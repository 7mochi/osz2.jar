package io.github.nanamochi.osz2.crypto.impl;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import lombok.AllArgsConstructor;

/**
 * Implements the simple encryption used in osz2
 */
@AllArgsConstructor
public class SimpleCryptor implements IOsz2Cipher {
    private final byte[] key;

    /**
     * Encrypts the provided buffer range in-place using simple stream encryption.
     *
     * @param buffer the data buffer to encrypt.
     * @param offset the starting offset in the buffer.
     * @param length the number of bytes to encrypt.
     */
    @Override
    public void encrypt(byte[] buffer, int offset, int length) {
        byte prevEncrypted = 0;

        for (int i = 0; i < length; i++) {
            int idx = offset + i;

            // Handle modulo properly for potentially negative values
            int sum = (buffer[idx] & 0xFF) + ((key[i & 15] & 0xFF) >>> 2);
            buffer[idx] = (byte) ((sum % 256 + 256) % 256);

            buffer[idx] ^= rotateLeft(key[15 - (i & 15)], ((prevEncrypted & 0xFF) + length - i) % 7);
            buffer[idx] = rotateRight(buffer[idx], ((~prevEncrypted) & 0xFF) % 7);

            prevEncrypted = buffer[idx];
        }
    }

    /**
     * Decrypts the provided buffer range in-place using simple stream decryption.
     *
     * @param buffer the data buffer to decrypt.
     * @param offset the starting offset in the buffer.
     * @param length the number of bytes to decrypt.
     */
    @Override
    public void decrypt(byte[] buffer, int offset, int length) {
        byte prevEncrypted = 0;

        for (int i = 0; i < length; i++) {
            int idx = offset + i;
            byte tmpE = buffer[idx];

            buffer[idx] = rotateLeft(buffer[idx], ((~prevEncrypted) & 0xFF) % 7);
            buffer[idx] ^= rotateLeft(key[15 - (i & 15)], ((prevEncrypted & 0xFF) + length - i) % 7);

            // Handle negative results properly - convert to uint before modulo
            int diff = (buffer[idx] & 0xFF) - ((key[i & 15] & 0xFF) >>> 2);
            buffer[idx] = (byte) ((diff % 256 + 256) % 256);

            prevEncrypted = tmpE;
        }
    }

    /**
     * Rotates the bits of a byte to the left by a specified number of positions.
     *
     * @param value  the byte value to rotate.
     * @param number the number of positions to shift.
     * @return the rotated byte.
     */
    private static byte rotateLeft(byte value, int number) {
        int bits = value & 0xFF;
        number &= 7;
        return (byte) ((bits << number) | (bits >>> (8 - number)));
    }

    /**
     * Rotates the bits of a byte to the right by a specified number of positions.
     *
     * @param value  the byte value to rotate.
     * @param number the number of positions to shift.
     * @return the rotated byte.
     */
    private static byte rotateRight(byte value, int number) {
        int bits = value & 0xFF;
        number &= 7;
        return (byte) ((bits >>> number) | (bits << (8 - number)));
    }
}
