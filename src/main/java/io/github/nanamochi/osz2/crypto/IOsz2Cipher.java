package io.github.nanamochi.osz2.crypto;

public interface IOsz2Cipher {
    void encrypt(byte[] buffer, int offset, int length);

    void decrypt(byte[] buffer, int offset, int length);
}
