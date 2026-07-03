package io.github.nanamochi.osz2.crypto.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.util.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XXTEA")
class XXTEATest {
    @Test
    @DisplayName("should roundtrip encrypt and decrypt 64 bytes")
    void encryptDecryptRoundtrip() {
        // Given
        IOsz2Cipher xxtea = new XXTEA(TestUtils.randomBytes(16));
        var original = TestUtils.randomBytes(64);
        var data = original.clone();

        // When
        xxtea.encrypt(data, 0, data.length);
        var encrypted = data.clone();
        xxtea.decrypt(data, 0, data.length);

        // Then
        assertThat(encrypted).isNotEqualTo(original);
        assertThat(data).isEqualTo(original);
    }

    @Test
    @DisplayName("should roundtrip encrypt and decrypt large data (1024 bytes)")
    void encryptDecryptLargeData() {
        // Given
        IOsz2Cipher xxtea = new XXTEA(TestUtils.randomBytes(16));

        var original = TestUtils.randomBytes(1024);
        var data = original.clone();

        // When
        xxtea.encrypt(data, 0, data.length);
        xxtea.decrypt(data, 0, data.length);

        // Then
        assertThat(data).isEqualTo(original);
    }

    @Test
    @DisplayName("should only modify the encrypted portion of a buffer")
    void encryptPartialBuffer() {
        // Given
        IOsz2Cipher xxtea = new XXTEA(TestUtils.randomBytes(16));
        var original = TestUtils.randomBytes(84);
        var data = original.clone();

        // When
        xxtea.encrypt(data, 10, 64);

        // Then: start and end portions should be unchanged
        assertThat(java.util.Arrays.copyOfRange(data, 0, 10))
                .containsExactly(java.util.Arrays.copyOfRange(original, 0, 10));
        assertThat(java.util.Arrays.copyOfRange(data, 74, 84))
                .containsExactly(java.util.Arrays.copyOfRange(original, 74, 84));
        assertThat(data).isNotEqualTo(original);
    }

    @Test
    @DisplayName("should handle single-byte data with SimpleCryptor fallback")
    void encryptDecryptSingleByte() {
        // Given
        IOsz2Cipher xxtea = new XXTEA(TestUtils.randomBytes(16));
        var original = new byte[] {0x42};
        var data = original.clone();

        // When
        xxtea.encrypt(data, 0, data.length);
        xxtea.decrypt(data, 0, data.length);

        // Then
        assertThat(data).isEqualTo(original);
    }

    @Test
    @DisplayName("should handle empty data without error")
    void emptyData() {
        // Given
        IOsz2Cipher xxtea = new XXTEA(TestUtils.randomBytes(16));
        var data = new byte[0];

        // When
        xxtea.encrypt(data, 0, 0);
        xxtea.decrypt(data, 0, 0);

        // Then
        assertThat(data).isEmpty();
    }
}
