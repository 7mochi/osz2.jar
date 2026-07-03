package io.github.nanamochi.osz2.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanamochi.osz2.util.Utils;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KeyType")
class KeyTypeTest {
    @Nested
    @DisplayName("OSZ2 key derivation")
    class Osz2Tests {

        @Test
        @DisplayName("should generate key from Creator + salt + BeatmapSetID")
        void generatesKey() {
            // Given
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.Creator, "TestCreator");
            meta.put(MetadataType.BeatmapSetID, "12345");

            // When
            var key = KeyType.OSZ2.generateKey(meta);

            // Then
            var expected = Utils.md5("TestCreatoryhxyfjo512345");
            assertThat(key).containsExactly(expected);
        }

        @Test
        @DisplayName("should throw when Creator is missing")
        void missingCreator() {
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.BeatmapSetID, "12345");

            assertThatThrownBy(() -> KeyType.OSZ2.generateKey(meta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Creator");
        }

        @Test
        @DisplayName("should throw when BeatmapSetID is missing")
        void missingBeatmapSetId() {
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.Creator, "TestCreator");

            assertThatThrownBy(() -> KeyType.OSZ2.generateKey(meta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("BeatmapSetID");
        }
    }

    @Nested
    @DisplayName("OSF2 key derivation")
    class Osf2Tests {

        @Test
        @DisplayName("should generate key from 0x08 + Title + salt + Artist")
        void generatesKey() {
            // Given
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.Title, "TestTitle");
            meta.put(MetadataType.Artist, "TestArtist");

            // When
            var key = KeyType.OSF2.generateKey(meta);

            // Then
            var expected = Utils.md5("\u0008TestTitle4390gn8931iTestArtist");
            assertThat(key).containsExactly(expected);
        }

        @Test
        @DisplayName("should throw when Title is missing")
        void missingTitle() {
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.Artist, "TestArtist");

            assertThatThrownBy(() -> KeyType.OSF2.generateKey(meta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Title");
        }

        @Test
        @DisplayName("should throw when Artist is missing")
        void missingArtist() {
            var meta = new HashMap<MetadataType, String>();
            meta.put(MetadataType.Title, "TestTitle");

            assertThatThrownBy(() -> KeyType.OSF2.generateKey(meta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Artist");
        }
    }
}
