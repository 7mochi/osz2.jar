package io.github.nanamochi.osz2.crypto.io;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import lombok.Getter;
import lombok.NonNull;

/**
 * OutputStream that encrypts data on-the-fly using XXTEA.
 */
public class XXTEAWriter extends OutputStream {
    private final ByteArrayOutputStream buf;

    @Getter
    private final IOsz2Cipher cipher;

    public XXTEAWriter(IOsz2Cipher cipher) {
        this.buf = new ByteArrayOutputStream();
        this.cipher = cipher;
    }

    @Override
    public void write(int b) {
        byte[] single = new byte[] {(byte) b};
        write(single, 0, 1);
    }

    @Override
    public void write(byte @NonNull [] b, int off, int len) {
        if (len == 0) return;
        byte[] encrypted = new byte[len];
        System.arraycopy(b, off, encrypted, 0, len);
        cipher.encrypt(encrypted, 0, len);
        buf.write(encrypted, 0, len);
    }

    public byte[] toByteArray() {
        return buf.toByteArray();
    }
}
