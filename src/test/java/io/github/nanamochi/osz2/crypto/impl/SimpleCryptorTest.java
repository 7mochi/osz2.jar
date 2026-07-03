package io.github.nanamochi.osz2.crypto.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanamochi.osz2.util.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SimpleCryptor")
class SimpleCryptorTest {
    @Test
    @DisplayName("should roundtrip encrypt and decrypt data")
    void encryptDecryptRoundtrip() {
        // Given
        var cryptor = new SimpleCryptor(TestUtils.randomBytes(16));
        var original = "comeyuca".getBytes();
        var data = original.clone();

        // When
        cryptor.encrypt(data, 0, data.length);
        var encrypted = data.clone();
        cryptor.decrypt(data, 0, data.length);

        // Then
        assertThat(encrypted).isNotEqualTo(original);
        assertThat(data).isEqualTo(original);
    }

    @Test
    @DisplayName("should produce different output after encryption")
    void encryptModifiesData() {
        // Given
        var cryptor = new SimpleCryptor(TestUtils.randomBytes(16));
        var original = "hoshizada".getBytes();
        var data = original.clone();

        // When
        cryptor.encrypt(data, 0, data.length);

        // Then
        assertThat(data).isNotEqualTo(original);
    }

    @Test
    @DisplayName("should handle empty data without error")
    void emptyData() {
        // Given
        var cryptor = new SimpleCryptor(TestUtils.randomBytes(16));
        var data = new byte[0];

        // When
        cryptor.encrypt(data, 0, 0);
        cryptor.decrypt(data, 0, 0);

        // Then
        assertThat(data).isEmpty();
    }
}
