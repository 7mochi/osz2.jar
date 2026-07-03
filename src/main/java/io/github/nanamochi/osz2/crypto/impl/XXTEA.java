package io.github.nanamochi.osz2.crypto.impl;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.util.Utils;

/**
 * Implements the Corrected Block TEA (XXTEA) algorithm.
 */
public class XXTEA implements IOsz2Cipher {
    private static final int MAX_WORDS = 16;
    private static final int MAX_BYTES = MAX_WORDS * 4;
    private static final int TEA_DELTA = 0x9E3779B9;

    private final int[] key;
    private final SimpleCryptor cryptor;

    public XXTEA(byte[] key) {
        if (key.length != 16) throw new IllegalArgumentException("XTEA requires a 16-byte key");
        this.key = new int[4];
        for (int i = 0; i < 4; i++) {
            this.key[i] = Utils.bytesToIntLE(key, i * 4);
        }
        this.cryptor = new SimpleCryptor(key);
    }

    /**
     * Encrypts the provided buffer range in-place using XXTEA block processing.
     *
     * @param buffer the data buffer to encrypt.
     * @param offset the starting offset in the buffer.
     * @param length the number of bytes to encrypt.
     */
    @Override
    public void encrypt(byte[] buffer, int offset, int length) {
        encryptDecrypt(buffer, offset, length, true);
    }

    /**
     * Decrypts the provided buffer range in-place using XXTEA block processing.
     *
     * @param buffer the data buffer to decrypt.
     * @param offset the starting offset in the buffer.
     * @param length the number of bytes to decrypt.
     */
    @Override
    public void decrypt(byte[] buffer, int offset, int length) {
        encryptDecrypt(buffer, offset, length, false);
    }

    /**
     * Processes encryption or decryption splitting data into fixed blocks and residues.
     */
    private void encryptDecrypt(byte[] buffer, int bufferStart, int count, boolean encryption) {
        int fullWordCount = count / MAX_BYTES;
        int leftOver = count % MAX_BYTES;

        for (int i = 0; i < fullWordCount; i++) {
            int offset = bufferStart + i * MAX_BYTES;
            if (encryption) {
                encryptBlock(buffer, offset, MAX_WORDS);
            } else {
                decryptBlock(buffer, offset, MAX_WORDS);
            }
        }

        if (leftOver == 0) return;

        // Handle leftover bytes
        int leftoverStart = bufferStart + fullWordCount * MAX_BYTES;
        int n = leftOver / 4;

        if (n > 1) {
            if (encryption) {
                encryptBlock(buffer, leftoverStart, n);
            } else {
                decryptBlock(buffer, leftoverStart, n);
            }

            leftOver -= n * 4;
            leftoverStart += n * 4;
        }

        // Handle remaining bytes with simple cryptor
        if (leftOver > 0) {
            if (encryption) cryptor.encrypt(buffer, leftoverStart, leftOver);
            else cryptor.decrypt(buffer, leftoverStart, leftOver);
        }
    }

    /**
     * Encrypts a dynamic block of words in-place.
     */
    private void encryptBlock(byte[] buffer, int offset, int n) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = Utils.bytesToIntLE(buffer, offset + i * 4);
        }

        int rounds = 6 + 52 / n;
        int sum = 0;
        int z = v[n - 1];

        for (int r = 0; r < rounds; r++) {
            sum += TEA_DELTA;
            int e = (sum >>> 2) & 3;
            int p;
            for (p = 0; p < n - 1; p++) {
                int y = v[p + 1];
                v[p] += mx(y, z, sum, key[(p & 3) ^ e]);
                z = v[p];
            }
            int y = v[0];
            v[n - 1] += mx(y, z, sum, key[(p & 3) ^ e]);
            z = v[n - 1];
        }

        for (int i = 0; i < n; i++) {
            Utils.intsToBytesLE(v[i], buffer, offset + i * 4);
        }
    }

    /**
     * Decrypts a dynamic block of words in-place.
     */
    private void decryptBlock(byte[] buffer, int offset, int n) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = Utils.bytesToIntLE(buffer, offset + i * 4);
        }

        int rounds = 6 + 52 / n;
        int sum = rounds * TEA_DELTA;
        int y = v[0];

        while (sum != 0) {
            int e = (sum >>> 2) & 3;
            int p;
            for (p = n - 1; p > 0; p--) {
                int z = v[p - 1];
                v[p] -= mx(y, z, sum, key[(p & 3) ^ e]);
                y = v[p];
            }
            int z = v[n - 1];
            v[0] -= mx(y, z, sum, key[(p & 3) ^ e]);
            y = v[0];
            sum -= TEA_DELTA;
        }

        for (int i = 0; i < n; i++) {
            Utils.intsToBytesLE(v[i], buffer, offset + i * 4);
        }
    }

    /**
     * Mixes intermediate vector states using the XXTEA equation parameters.
     */
    private static int mx(int y, int z, int sum, int k) {
        return (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4))) ^ ((sum ^ y) + (k ^ z));
    }
}
