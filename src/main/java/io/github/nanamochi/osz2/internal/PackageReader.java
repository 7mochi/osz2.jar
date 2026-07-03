package io.github.nanamochi.osz2.internal;

import io.github.nanamochi.osz2.Package;
import io.github.nanamochi.osz2.PackageFile;
import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.crypto.impl.XTEA;
import io.github.nanamochi.osz2.crypto.impl.XXTEA;
import io.github.nanamochi.osz2.crypto.io.XXTEAReader;
import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;
import io.github.nanamochi.osz2.util.Constants;
import io.github.nanamochi.osz2.util.Utils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.*;

public class PackageReader {
    public Package read(byte[] data, boolean metadataOnly, KeyType keyType) throws IOException {
        var buffer = ByteBuffer.wrap(data);
        return read(buffer, metadataOnly, keyType);
    }

    private Package read(ByteBuffer buffer, boolean metadataOnly, KeyType keyType) throws IOException {
        var metadata = new HashMap<MetadataType, String>();
        var beatmapIds = new HashMap<String, Integer>();
        var files = new ArrayList<PackageFile>();
        var iv = new byte[16];
        var metadataHash = new byte[16];
        var fileInfoHash = new byte[16];
        var fullBodyHash = new byte[16];

        int version = readHeader(buffer, metadata, beatmapIds, iv, metadataHash, fileInfoHash, fullBodyHash);
        var key = keyType.generateKey(metadata);

        if (!metadataOnly) {
            readFiles(buffer, fileInfoHash, files, key);
        }

        return new Package(
                metadata,
                beatmapIds,
                files,
                keyType,
                key,
                version,
                iv,
                metadataHash,
                fileInfoHash,
                fullBodyHash,
                metadataOnly);
    }

    private int readHeader(
            ByteBuffer buffer,
            Map<MetadataType, String> metadata,
            Map<String, Integer> beatmapIds,
            byte[] iv,
            byte[] metadataHash,
            byte[] fileInfoHash,
            byte[] fullBodyHash)
            throws IOException {
        // 1. Magic bytes
        byte m0 = buffer.get();
        byte m1 = buffer.get();
        byte m2 = buffer.get();

        if (m0 != (byte) 0xEC || m1 != 0x48 || m2 != 0x4F) {
            throw new IOException("Not a valid .osz2 package (bad magic)");
        }

        // 2. Version
        int version = buffer.get() & 0xFF;

        // 3. IV (16 bytes)
        buffer.get(iv);

        // 4. Hashes (3 × 16 bytes)
        buffer.get(metadataHash);
        buffer.get(fileInfoHash);
        buffer.get(fullBodyHash);

        // 5. Metadata
        readMetadata(buffer, metadata, metadataHash);

        // 6. File names
        readFileNames(buffer, beatmapIds);

        return version;
    }

    private void readMetadata(ByteBuffer buffer, Map<MetadataType, String> metadata, byte[] metadataHash)
            throws IOException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int startPosition = buffer.position();
        int count = buffer.getInt();

        for (int i = 0; i < count; i++) {
            int metaTypeValue = buffer.getShort() & 0xFFFF;
            String metaValue = Utils.readString(buffer);
            metadata.put(MetadataType.fromValue(metaTypeValue), metaValue);
        }

        int endPosition = buffer.position();
        byte[] hashBufBytes = new byte[endPosition - startPosition];

        buffer.position(startPosition);
        buffer.get(hashBufBytes);
        buffer.position(endPosition);

        byte[] computed = Utils.computeOszHash(hashBufBytes, count * 3, 0xA7);
        if (!Arrays.equals(computed, metadataHash)) {
            throw new IOException("Metadata hash mismatch: expected "
                    + HexFormat.of().formatHex(metadataHash)
                    + ", got "
                    + HexFormat.of().formatHex(computed));
        }
    }

    private void readFileNames(ByteBuffer buffer, Map<String, Integer> beatmapIds) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int count = buffer.getInt();
        for (int i = 0; i < count; i++) {
            String filename = Utils.readString(buffer);
            int beatmapId = buffer.getInt();
            beatmapIds.put(filename, beatmapId);
        }
    }

    private void readFiles(ByteBuffer buffer, byte[] fileInfoHash, List<PackageFile> files, byte[] key)
            throws IOException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Read and decrypt magic known-plain (64 bytes) using XTEA
        byte[] encryptedMagic = new byte[64];
        buffer.get(encryptedMagic);

        IOsz2Cipher xtea = new XTEA(key);
        xtea.decrypt(encryptedMagic, 0, 64);

        if (!Arrays.equals(encryptedMagic, Constants.KNOWN_PLAIN)) {
            throw new IOException("Invalid encryption key");
        }

        // Read encrypted file info length (int32 LE)
        int length = buffer.getInt();

        // Decode length by subtracting file info hash bytes
        for (int i = 0; i < 16; i += 2) {
            length -= (fileInfoHash[i] & 0xFF) | ((fileInfoHash[i + 1] & 0xFF) << 17);
        }

        // Read encrypted file info block
        byte[] fileInfoBlock = new byte[length];
        buffer.get(fileInfoBlock);

        // Read remaining file data
        byte[] fileDataBlock = new byte[buffer.remaining()];
        buffer.get(fileDataBlock);

        // Decrypt and parse file info via XXTEA streaming reader
        IOsz2Cipher xxteaInfo = new XXTEA(key);
        var fileInfoReader = new XXTEAReader(new ByteArrayInputStream(fileInfoBlock), xxteaInfo);

        // Read file count (int32 LE)
        byte[] countBuf = new byte[4];
        fileInfoReader.readFully(countBuf, 0, 4);
        int fileCount = ByteBuffer.wrap(countBuf).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // Verify file info hash on the encrypted bytes (NOT decrypted)
        byte[] computedFileInfoHash = Utils.computeOszHash(fileInfoBlock, fileCount * 4, 0xD1);
        if (!Arrays.equals(computedFileInfoHash, fileInfoHash)) {
            throw new IOException("File info hash mismatch: expected "
                    + HexFormat.of().formatHex(fileInfoHash)
                    + ", got "
                    + HexFormat.of().formatHex(computedFileInfoHash));
        }

        // Read current offset (int32 LE)
        byte[] offsetBuf = new byte[4];
        fileInfoReader.readFully(offsetBuf, 0, 4);
        int currentOffset =
                ByteBuffer.wrap(offsetBuf).order(ByteOrder.LITTLE_ENDIAN).getInt();

        for (int i = 0; i < fileCount; i++) {
            // Read string (ULEB128 prefixed UTF-8)
            String fileName = fileInfoReader.readString();

            // Read file hash (16 bytes)
            byte[] fileHash = new byte[16];
            fileInfoReader.readFully(fileHash, 0, 16);

            // Read timestamps (int64 LE each)
            byte[] dateCreatedBuf = new byte[8];
            fileInfoReader.readFully(dateCreatedBuf, 0, 8);
            long dateCreatedBinary = ByteBuffer.wrap(dateCreatedBuf)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getLong();

            byte[] dateModifiedBuf = new byte[8];
            fileInfoReader.readFully(dateModifiedBuf, 0, 8);
            long dateModifiedBinary = ByteBuffer.wrap(dateModifiedBuf)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getLong();

            LocalDateTime dateCreated = Utils.dateTimeFromBinary(dateCreatedBinary);
            LocalDateTime dateModified = Utils.dateTimeFromBinary(dateModifiedBinary);

            // Read next offset (int32 LE) or compute for last file
            int nextOffset;
            if (i + 1 < fileCount) {
                byte[] nextOffsetBuf = new byte[4];
                fileInfoReader.readFully(nextOffsetBuf, 0, 4);
                nextOffset = ByteBuffer.wrap(nextOffsetBuf)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt();
            } else {
                nextOffset = fileDataBlock.length;
            }

            int fileLength = nextOffset - currentOffset;

            files.add(new PackageFile(
                    fileName, currentOffset, fileLength, fileHash, dateCreated, dateModified, new byte[0]));
            currentOffset = nextOffset;
        }

        // Decrypt and parse file contents via XXTEAReader
        IOsz2Cipher xxteaData = new XXTEA(key);
        var fileDataReader = new XXTEAReader(new ByteArrayInputStream(fileDataBlock), xxteaData);

        for (PackageFile file : files) {
            // Read encrypted content length (int32 LE)
            byte[] lenBuf2 = new byte[4];
            fileDataReader.readFully(lenBuf2, 0, 4);
            int contentLength =
                    ByteBuffer.wrap(lenBuf2).order(ByteOrder.LITTLE_ENDIAN).getInt();

            // Read content
            byte[] content = new byte[contentLength];
            fileDataReader.readFully(content, 0, contentLength);

            file.setContent(content);
            file.setSize(contentLength);
        }
    }
}
