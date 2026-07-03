package io.github.nanamochi.osz2.crypto.io;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.util.Utils;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.NonNull;

/**
 * InputStream that decrypts data on-the-fly using XXTEA.
 */
public class XXTEAReader extends FilterInputStream {
    private final IOsz2Cipher cipher;

    public XXTEAReader(InputStream in, IOsz2Cipher cipher) {
        super(in);
        this.cipher = cipher;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        if (n <= 0) return -1;
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
        int bytesRead = in.read(b, off, len);
        if (bytesRead <= 0) return bytesRead;
        cipher.decrypt(b, off, bytesRead);
        return bytesRead;
    }

    @Override
    public int read(byte @NonNull [] b) throws IOException {
        return read(b, 0, b.length);
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = read(b, off + total, len - total);
            if (n < 0) throw new IOException("Unexpected end of stream");
            total += n;
        }
    }

    public String readString() throws IOException {
        return Utils.readString(this);
    }
}
