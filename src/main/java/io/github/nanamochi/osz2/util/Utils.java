package io.github.nanamochi.osz2.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Utils {
    public static byte[] int32LE(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    public static byte[] int64LE(long value) {
        return new byte[] {
            (byte) value,
            (byte) (value >> 8),
            (byte) (value >> 16),
            (byte) (value >> 24),
            (byte) (value >> 32),
            (byte) (value >> 40),
            (byte) (value >> 48),
            (byte) (value >> 56)
        };
    }

    public static int[] bytesToUint32Array(byte[] data) {
        var bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        var result = new int[data.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = bb.getInt();
        }
        return result;
    }

    public static byte[] uint32ToByteArray(int[] words) {
        var bb = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (var w : words) {
            bb.putInt(w);
        }
        return bb.array();
    }

    public static int bytesToIntLE(byte[] buffer, int offset) {
        return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt(offset);
    }

    public static void intsToBytesLE(int value, byte[] buffer, int offset) {
        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }

    public static int readULEB128(ByteBuffer buffer) {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = buffer.get() & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    public static int readULEB128(InputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("Unexpected end of stream in ULEB128");
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    public static byte[] writeULEB128(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(5);

        while (true) {
            int b = value & 0x7F;
            value >>>= 7;

            if (value != 0) {
                buffer.put((byte) (b | 0x80));
            } else {
                buffer.put((byte) b);
                break;
            }
        }

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    public static void writeULEB128(OutputStream out, int value) throws IOException {
        do {
            int b = value & 0x7F;
            value >>>= 7;
            if (value != 0) b |= 0x80;
            out.write(b);
        } while (value != 0);
    }

    public static String readString(ByteBuffer buffer) {
        int length = readULEB128(buffer);
        if (length == 0) return "";

        byte[] data = new byte[length];
        buffer.get(data);

        return new String(data, StandardCharsets.UTF_8);
    }

    public static String readString(InputStream in) throws IOException {
        int length = readULEB128(in);
        if (length == 0) return "";

        byte[] data = new byte[length];
        int total = 0;
        while (total < length) {
            int n = in.read(data, total, length - total);
            if (n < 0) throw new IOException("Unexpected end of stream");
            total += n;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public static byte[] writeString(String string) {
        byte[] encoded = string.getBytes(StandardCharsets.UTF_8);
        byte[] header = writeULEB128(encoded.length);
        byte[] result = new byte[header.length + encoded.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(encoded, 0, result, header.length, encoded.length);
        return result;
    }

    public static byte[] computeOszHash(byte[] buffer, int position, int swap) {
        byte[] buf = buffer.clone();
        byte[] hashBytes;

        if (position >= 0 && position < buf.length) {
            buf[position] ^= (byte) (swap & 0xFF);
        }
        hashBytes = md5(buf);

        // Swap bytes as in C# implementation
        for (int i = 0; i < 8; i++) {
            byte tmp = hashBytes[i];
            hashBytes[i] = hashBytes[i + 8];
            hashBytes[i + 8] = tmp;
        }

        hashBytes[5] ^= 0x2D;
        return hashBytes;
    }

    public static byte[] computeBodyHash(byte[] data, Integer videoOffset, Integer videoLength) {
        byte[] toHash = data;
        var position = data.length / 2;

        if (videoOffset != null && videoLength != null) {
            // Exclude video data from the hash calculation
            var before = Arrays.copyOfRange(data, 0, videoOffset);
            var after = Arrays.copyOfRange(data, videoOffset + videoLength, data.length);
            toHash = new byte[before.length + after.length];
            System.arraycopy(before, 0, toHash, 0, before.length);
            System.arraycopy(after, 0, toHash, before.length, after.length);
            position = toHash.length / 2;
        }

        return computeOszHash(toHash, position, 0x9F);
    }

    public static LocalDateTime dateTimeFromBinary(long binary) {
        long ticks = binary & 0x3FFFFFFFFFFFFFFFL;
        long seconds = ticks / 10_000_000L;
        long nanos = (ticks % 10_000_000L) * 100;
        LocalDateTime epoch = LocalDateTime.of(1, 1, 1, 0, 0, 0);
        return epoch.plus(Duration.ofSeconds(seconds, nanos));
    }

    public static long dateTimeToBinary(LocalDateTime dateTime) {
        LocalDateTime epoch = LocalDateTime.of(1, 1, 1, 0, 0, 0);
        Duration duration = Duration.between(epoch, dateTime);
        long ticks = duration.getSeconds() * 10_000_000L + duration.getNano() / 100;
        return ticks & 0x3FFFFFFFFFFFFFFFL;
    }

    public static byte[] md5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    public static byte[] md5(String input) {
        return md5(input.getBytes(StandardCharsets.US_ASCII));
    }
}
