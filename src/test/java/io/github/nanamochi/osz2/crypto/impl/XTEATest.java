package io.github.nanamochi.osz2.crypto.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.util.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XTEA")
class XTEATest {
    @Test
    @DisplayName("should roundtrip encrypt and decrypt 64 bytes")
    void encryptDecryptRoundtrip() {
        // Given
        IOsz2Cipher xtea = new XTEA(TestUtils.randomBytes(16));
        var original = TestUtils.randomBytes(64);
        var data = original.clone();

        // When
        xtea.encrypt(data, 0, data.length);
        var encrypted = data.clone();
        xtea.decrypt(data, 0, data.length);

        // Then
        assertThat(encrypted).isNotEqualTo(original);
        assertThat(data).isEqualTo(original);
    }

    @Test
    @DisplayName("should handle non-8-byte bound data via SimpleCryptor fallback")
    void encryptDecryptPartialBlock() {
        // Given
        IOsz2Cipher xtea = new XTEA(TestUtils.randomBytes(16));
        var original = TestUtils.randomBytes(10); // 8 + 2 leftover
        var data = original.clone();

        // When
        xtea.encrypt(data, 0, data.length);
        xtea.decrypt(data, 0, data.length);

        // Then
        assertThat(data).isEqualTo(original);
    }
}
