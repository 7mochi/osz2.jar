package io.github.nanamochi.osz2.crypto.impl;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.util.Utils;

/**
 * Implements the Extended Tiny Encryption Algorithm.
 */
public class XTEA implements IOsz2Cipher {
    private static final int TEA_DELTA = 0x9E3779B9;
    private static final int TEA_ROUNDS = 32;

    private final int[] key;
    private final SimpleCryptor cryptor;

    public XTEA(byte[] key) {
        this.key = new int[4];
        for (int i = 0; i < 4; i++) {
            this.key[i] = Utils.bytesToIntLE(key, i * 4);
        }
        this.cryptor = new SimpleCryptor(key);
    }

    /**
     * Encrypts the provided buffer range in-place using XTEA block processing.
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
     * Decrypts the provided buffer range in-place using XTEA block processing.
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
     * Processes encryption or decryption across 8-byte boundaries.
     */
    private void encryptDecrypt(byte[] buffer, int bufferStart, int count, boolean encrypt) {
        var fullWordCount = count / 8;
        var leftOver = count % 8;

        // Process full 8-byte words
        for (int i = 0; i < fullWordCount; i++) {
            var offset = bufferStart + i * 8;

            if (encrypt) {
                encryptBlock(buffer, offset);
            } else {
                decryptBlock(buffer, offset);
            }
        }

        // Handle leftover bytes
        if (leftOver > 0) {
            var leftoverStart = bufferStart + fullWordCount * 8;

            if (encrypt) cryptor.encrypt(buffer, leftoverStart, leftOver);
            else cryptor.decrypt(buffer, leftoverStart, leftOver);
        }
    }

    /**
     * Encrypts a single 64-bit word block (two 32-bit values) in-place.
     */
    private void encryptBlock(byte[] buffer, int offset) {
        var v0 = Utils.bytesToIntLE(buffer, offset);
        var v1 = Utils.bytesToIntLE(buffer, offset + 4);
        var sum = 0;

        for (int i = 0; i < TEA_ROUNDS; i++) {
            v0 += (((v1 << 4) ^ (v1 >>> 5)) + v1) ^ (sum + key[sum & 3]);
            sum += TEA_DELTA;
            v1 += (((v0 << 4) ^ (v0 >>> 5)) + v0) ^ (sum + key[(sum >>> 11) & 3]);
        }

        Utils.intsToBytesLE(v0, buffer, offset);
        Utils.intsToBytesLE(v1, buffer, offset + 4);
    }

    /**
     * Decrypts a single 64-bit word block (two 32-bit values) in-place.
     */
    @SuppressWarnings("NumericOverflow")
    private void decryptBlock(byte[] buffer, int offset) {
        var v0 = Utils.bytesToIntLE(buffer, offset);
        var v1 = Utils.bytesToIntLE(buffer, offset + 4);

        var sum = TEA_DELTA * TEA_ROUNDS;

        for (int i = 0; i < TEA_ROUNDS; i++) {
            v1 -= (((v0 << 4) ^ (v0 >>> 5)) + v0) ^ (sum + key[(sum >>> 11) & 3]);
            sum -= TEA_DELTA;
            v0 -= (((v1 << 4) ^ (v1 >>> 5)) + v1) ^ (sum + key[sum & 3]);
        }

        Utils.intsToBytesLE(v0, buffer, offset);
        Utils.intsToBytesLE(v1, buffer, offset + 4);
    }
}
