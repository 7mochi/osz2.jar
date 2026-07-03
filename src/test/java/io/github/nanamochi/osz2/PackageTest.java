package io.github.nanamochi.osz2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Package")
class PackageTest {
    record PackageCase(Path path, KeyType keyType, List<MetadataType> requiredFields) {}

    static Stream<PackageCase> packages() throws IOException {
        try (var files = Files.list(Paths.get("src/test/resources"))) {
            return files
                    .sorted()
                    .filter(p -> p.toString().endsWith(".osz2") || p.toString().endsWith(".osf2"))
                    .map(p -> {
                        boolean osf2 = p.toString().endsWith(".osf2");
                        return new PackageCase(
                                p,
                                osf2 ? KeyType.OSF2 : KeyType.OSZ2,
                                osf2
                                        ? List.of(MetadataType.Title, MetadataType.Artist)
                                        : List.of(MetadataType.Creator, MetadataType.BeatmapSetID));
                    })
                    .toList()
                    .stream();
        }
    }

    @Nested
    @DisplayName("Reading")
    class ReadTests {
        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should parse from file")
        void fromFile(PackageCase f) throws IOException {
            var pkg = Package.fromFile(f.path.toString(), false, f.keyType);

            assertThat(pkg).isNotNull();
            assertThat(pkg.getMetadata()).isNotEmpty();
            assertThat(pkg.getFiles()).isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should parse from byte array")
        void fromBytes(PackageCase f) throws IOException {
            var pkg = Package.fromBytes(Files.readAllBytes(f.path), false, f.keyType);

            assertThat(pkg).isNotNull();
            assertThat(pkg.getMetadata()).isNotEmpty();
            assertThat(pkg.getFiles()).isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should read metadata-only without loading files")
        void metadataOnly(PackageCase f) throws IOException {
            var pkg = Package.fromBytes(Files.readAllBytes(f.path), true, f.keyType);

            assertThat(pkg).isNotNull();
            assertThat(pkg.getMetadata()).isNotEmpty();
            assertThat(pkg.getFiles()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should contain required fields for its type")
        void containsRequiredFields(PackageCase f) throws IOException {
            var pkg = Package.fromBytes(Files.readAllBytes(f.path), true, f.keyType);

            for (var field : f.requiredFields) {
                assertThat(pkg.getMetadata())
                        .as("Missing %s in %s", field, f.path.getFileName())
                        .containsKey(field);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should contain valid .osu beatmap content")
        void beatmapContentIsValid(PackageCase f) throws IOException {
            if (f.keyType == KeyType.OSF2) return;

            var pkg = Package.fromBytes(Files.readAllBytes(f.path), false, f.keyType);
            for (var pf : pkg.getFiles()) {
                if (!pf.isBeatmap()) continue;
                var content = new String(pf.getContent(), StandardCharsets.UTF_8);
                assertThat(content)
                        .as("Beatmap %s in %s is not a valid .osu file", pf.getFilename(), f.path.getFileName())
                        .contains("osu file format");
            }
        }

        @Test
        @DisplayName("should throw on invalid data")
        void invalidFileThrows() {
            assertThatThrownBy(() -> Package.fromBytes(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}, false, KeyType.OSZ2))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("Writing")
    class WriteTests {
        @Test
        @DisplayName("should add file and find it by name")
        void addAndFindFile() {
            // Given
            var pkg = new Package(KeyType.OSZ2);
            pkg.addFile("test.osu", "osu file format v14\n".getBytes(), null, null);

            // When
            var found = pkg.findFileByName("test.osu");

            // Then
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("should remove an added file")
        void addThenRemoveFile() {
            // Given
            var pkg = new Package(KeyType.OSZ2);
            pkg.addFile("test.osu", "osu file format v14\n".getBytes(), null, null);

            // When
            var removed = pkg.removeFile("test.osu");

            // Then
            assertThat(removed).isTrue();
            assertThat(pkg.findFileByName("test.osu")).isNotPresent();
        }

        @Test
        @DisplayName("should replace existing file with same name")
        void addReplaceFile() {
            // Given
            var pkg = new Package(KeyType.OSZ2);
            pkg.addFile("test.osu", "original".getBytes(), null, null);

            // When
            pkg.addFile("test.osu", "replacement".getBytes(), null, null);

            // Then
            assertThat(pkg.getFiles()).hasSize(1);
            assertThat(pkg.findFileByName("test.osu").orElseThrow().getContent())
                    .isEqualTo("replacement".getBytes());
        }

        @Test
        @DisplayName("should roundtrip metadata through add/remove/get")
        void metadataRoundtrip() {
            // Given
            var pkg = new Package(KeyType.OSZ2);

            // When
            pkg.addMetadata(MetadataType.Title, "Test Title");
            pkg.addMetadata(MetadataType.Artist, "Test Artist");

            // Then
            assertThat(pkg.getMetadata(MetadataType.Title)).isEqualTo("Test Title");
            assertThat(pkg.getMetadata(MetadataType.Artist)).isEqualTo("Test Artist");

            // When
            var removed = pkg.removeMetadata(MetadataType.Title);

            // Then
            assertThat(removed).isTrue();
            assertThat(pkg.getMetadata(MetadataType.Title)).isNull();
        }

        @Test
        @DisplayName("should set BeatmapSetID metadata")
        void setBeatmapSetID() {
            // Given
            var pkg = new Package(KeyType.OSZ2);

            // When
            pkg.setBeatmapSetID(12345);

            // Then
            assertThat(pkg.getMetadata(MetadataType.BeatmapSetID)).isEqualTo("12345");
        }

        @Test
        @DisplayName("should find a file by its beatmap ID")
        void findFileByBeatmapID() throws IOException {
            // Given
            var pkg = new Package(KeyType.OSZ2);
            pkg.addFile("test.osu", "osu file format v14\n".getBytes(), null, null);
            pkg.setBeatmapID("test.osu", 999);

            // When
            var found = pkg.findFileByBeatmapID(999);

            // Then
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("should classify beatmap and video files correctly")
        void beatmapAndVideoFiles() {
            // Given
            var pkg = new Package(KeyType.OSZ2);
            pkg.addFile("song.mp3", "audio".getBytes(), null, null);
            pkg.addFile("beatmap.osu", "osu file format v14\n".getBytes(), null, null);
            pkg.addFile("video.mp4", "video".getBytes(), null, null);

            // When
            var videoCount =
                    pkg.getFiles().stream().filter(PackageFile::isVideo).count();
            var beatmapCount =
                    pkg.getFiles().stream().filter(PackageFile::isBeatmap).count();

            // Then
            assertThat(videoCount).isOne();
            assertThat(beatmapCount).isOne();
        }

        @Test
        @DisplayName("should throw when exporting empty package")
        void exportEmptyPackageThrows() {
            // Given
            var pkg = new Package(KeyType.OSZ2);

            // Then
            assertThatThrownBy(pkg::export).isInstanceOf(IOException.class).hasMessageContaining("empty");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should export and reimport with same metadata and file count")
        void exportRoundtrip(PackageCase f) throws Exception {
            // Given
            var original = Package.fromFile(f.path.toString(), false, f.keyType);
            assertThat(original.getFiles()).isNotEmpty();

            // When
            var exported = original.export();
            var reimported = Package.fromBytes(exported, false, f.keyType);

            // Then
            assertThat(reimported.getFiles()).hasSize(original.getFiles().size());
            assertThat(reimported.getMetadata()).hasSameSizeAs(original.getMetadata());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should export with valid magic bytes")
        void exportAndVerifyMagic(PackageCase f) throws Exception {
            // Given
            var original = Package.fromFile(f.path.toString(), false, f.keyType);

            // When
            var exported = original.export();

            // Then
            assertThat(exported[0]).isEqualTo((byte) 0xEC);
            assertThat(exported[1]).isEqualTo((byte) 0x48);
            assertThat(exported[2]).isEqualTo((byte) 0x4F);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should create valid ZIP .osz package")
        void createOszPackage(PackageCase f) throws Exception {
            // Given
            var original = Package.fromFile(f.path.toString(), false, f.keyType);

            // When
            var osz = original.createOszPackage(false);

            // Then (PK zip signature)
            assertThat(osz).isNotNull();
            assertThat(osz[0] & 0xFF).isEqualTo(0x50);
            assertThat(osz[1] & 0xFF).isEqualTo(0x4B);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.nanamochi.osz2.PackageTest#packages")
        @DisplayName("should create .osz with disallowed files excluded")
        void createOszPackageExcludeDisallowed(PackageCase f) throws Exception {
            // Given
            var original = Package.fromFile(f.path.toString(), false, f.keyType);

            // When
            var osz = original.createOszPackage(true);

            // Then
            assertThat(osz).isNotNull();
            assertThat(osz).hasSizeGreaterThan(22);
        }
    }
}
