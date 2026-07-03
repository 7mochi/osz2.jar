package io.github.nanamochi.osz2;

import io.github.nanamochi.osz2.internal.PackageReader;
import io.github.nanamochi.osz2.internal.PackageWriter;
import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;
import io.github.nanamochi.osz2.util.Utils;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Package represents an osz2 package
 */
@Getter
@AllArgsConstructor
public class Package {
    private final Map<MetadataType, String> metadata;
    private final Map<String, Integer> beatmapIds;
    private final List<PackageFile> files;
    private final KeyType keyType;
    private byte[] key;
    private int version;

    private byte[] iv;

    private byte[] metadataHash;
    private byte[] fileInfoHash;
    private byte[] fullBodyHash;

    private boolean metadataOnly;

    public Package(KeyType keyType) {
        this.metadata = new HashMap<>();
        this.beatmapIds = new HashMap<>();
        this.files = new ArrayList<>();
        this.keyType = keyType;
        this.key = null;
        this.version = 0;

        this.iv = new byte[16];
        new SecureRandom().nextBytes(this.iv);

        this.metadataHash = new byte[16];
        this.fileInfoHash = new byte[16];
        this.fullBodyHash = new byte[16];

        this.metadataOnly = false;
    }

    /**
     * Reads an osz2 package from the specified file path.
     *
     * @param path         the path to the osz2 file.
     * @param metadataOnly if true, only the package headers and metadata are parsed.
     * @param keyType      the key derivation strategy used to decrypt the stream.
     * @return the parsed and decrypted Package instance.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public static Package fromFile(String path, boolean metadataOnly, KeyType keyType) throws IOException {
        try (var stream = new FileInputStream(path)) {
            return new PackageReader().read(stream.readAllBytes(), metadataOnly, keyType);
        }
    }

    /**
     * Reads an osz2 package using the default {@link KeyType#OSZ2} key type.
     */
    public static Package fromFile(String path) throws IOException {
        return fromFile(path, false, KeyType.OSZ2);
    }

    /**
     * Reads an osz2 package using the default {@link KeyType#OSZ2} key type,
     * optionally parsing only its metadata.
     */
    public static Package fromFile(String path, boolean metadataOnly) throws IOException {
        return fromFile(path, metadataOnly, KeyType.OSZ2);
    }

    /**
     * Reads a full osz2 package using a specific key type.
     */
    public static Package fromFile(String path, KeyType keyType) throws IOException {
        return fromFile(path, false, keyType);
    }

    /**
     * Reads an osz2 package from raw bytes.
     *
     * @param data         the raw byte array containing the package data.
     * @param metadataOnly if true, only the package headers and metadata are parsed.
     * @param keyType      the key derivation strategy used to decrypt the content.
     * @return the parsed and decrypted Package instance.
     * @throws IOException if a parsing or cryptographic validation error occurs.
     */
    public static Package fromBytes(byte[] data, boolean metadataOnly, KeyType keyType) throws IOException {
        return new PackageReader().read(data, metadataOnly, keyType);
    }

    /**
     * Reads an osz2 package from raw bytes using the default {@link KeyType#OSZ2} key type.
     */
    public static Package fromBytes(byte[] data) throws IOException {
        return fromBytes(data, false, KeyType.OSZ2);
    }

    /**
     * Reads an osz2 package from raw bytes using the default {@link KeyType#OSZ2} key type,
     * optionally parsing only its metadata.
     */
    public static Package fromBytes(byte[] data, boolean metadataOnly) throws IOException {
        return fromBytes(data, metadataOnly, KeyType.OSZ2);
    }

    /**
     * Reads an osz2 package from raw bytes using a specific key type.
     */
    public static Package fromBytes(byte[] data, KeyType keyType) throws IOException {
        return fromBytes(data, false, keyType);
    }

    /**
     * Creates an osz2 package instance from a directory's contents,
     * useful for packing and exporting files.
     *
     * @param directory the path to the source directory to read.
     * @param keyType   the key derivation strategy to associate with the package.
     * @return a newly constructed Package instance populated with the directory's files.
     * @throws IOException if an I/O error occurs while reading the directory structure.
     */
    public static Package fromDirectory(String directory, KeyType keyType) throws IOException {
        var pkg = new Package(keyType);
        pkg.addDirectory(directory, true);
        return pkg;
    }

    /**
     * Finds a package file by its exact filename.
     *
     * @param name the name of the file to search for.
     * @return an {@link Optional} containing the matching file, or empty if not found.
     */
    public Optional<PackageFile> findFileByName(String name) {
        return files.stream().filter(f -> f.getFilename().equals(name)).findFirst();
    }

    /**
     * Finds a package file associated with a specific beatmap ID.
     *
     * @param beatmapID the unique ID of the beatmap.
     * @return an {@link Optional} containing the matching file, or empty if no file
     * corresponds to the given ID.
     */
    public Optional<PackageFile> findFileByBeatmapID(int beatmapID) {
        return beatmapIds.entrySet().stream()
                .filter(e -> e.getValue() == beatmapID)
                .map(Map.Entry::getKey)
                .flatMap(name -> files.stream().filter(f -> f.getFilename().equals(name)))
                .findFirst();
    }

    /**
     * Adds or replaces a file within this package.
     * <p>
     * If a file with the same name already exists, it will be removed before
     * adding the new one. If either creation or modification dates are null,
     * they will default to the current system time.
     *
     * @param filename     the destination name of the file inside the package.
     * @param content      the raw byte content of the file.
     * @param dateCreated  the optional creation timestamp.
     * @param dateModified the optional modification timestamp.
     */
    public void addFile(String filename, byte[] content, LocalDateTime dateCreated, LocalDateTime dateModified) {
        findFileByName(filename).ifPresent(files::remove);

        var hash = Utils.md5(content);
        var pkgFile = new PackageFile(
                filename,
                0,
                content.length,
                hash,
                dateCreated != null ? dateCreated : LocalDateTime.now(),
                dateModified != null ? dateModified : LocalDateTime.now(),
                content);
        files.add(pkgFile);

        if (pkgFile.isBeatmap() && !beatmapIds.containsKey(filename)) {
            beatmapIds.put(filename, -1);
        }
    }

    /**
     * Adds a file from the local file system into this package.
     *
     * @param filename the destination name of the file inside the package.
     * @param path     the path to the source file on the system.
     * @throws IOException if an I/O error occurs while reading the file content.
     */
    public void addFileFromDisk(String filename, String path) throws IOException {
        var content = Files.readAllBytes(Paths.get(path));
        var file = new File(path);
        var modified = file.lastModified();
        var modTime =
                LocalDateTime.ofEpochSecond(modified / 1000, (int) ((modified % 1000) * 1_000_000), ZoneOffset.UTC);
        addFile(filename, content, modTime, modTime);
    }

    /**
     * Adds all files from a directory to this package.
     *
     * @param path      the path to the source directory.
     * @param recursive if true, subdirectories will be traversed and added as well.
     * @throws IOException if the path does not point to a valid directory or if
     * an I/O error occurs during discovery.
     */
    public void addDirectory(String path, boolean recursive) throws IOException {
        var dir = new File(path);
        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + path);
        }

        var entries = dir.listFiles();
        if (entries == null) return;

        for (var entry : entries) {
            if (entry.isDirectory()) {
                if (recursive) {
                    addDirectory(entry.getAbsolutePath(), true);
                }
            } else if (entry.isFile()) {
                var relativePath =
                        entry.getAbsolutePath().substring(dir.getAbsolutePath().length() + 1);
                relativePath = relativePath.replace(File.separatorChar, '/');
                addFileFromDisk(relativePath, entry.getAbsolutePath());
            }
        }
    }

    /**
     * Removes a file from this package by its filename.
     *
     * @param filename the name of the file to remove.
     * @return true if the file existed and was successfully removed, false otherwise.
     */
    public boolean removeFile(String filename) {
        var found = findFileByName(filename);
        found.ifPresent(files::remove);
        beatmapIds.remove(filename);
        return found.isPresent();
    }

    /**
     * Adds or replaces a metadata item in this package.
     *
     * @param type  the {@link MetadataType} to associate the value with.
     * @param value the string value of the metadata entry.
     */
    public void addMetadata(MetadataType type, String value) {
        metadata.put(type, value);
    }

    /**
     * Removes the specified metadata item from this package.
     *
     * @param type the {@link MetadataType} to be removed.
     * @return true if the metadata item was present and removed, false otherwise.
     */
    public boolean removeMetadata(MetadataType type) {
        return metadata.remove(type) != null;
    }

    /**
     * Retrieves the value of the specified metadata type.
     *
     * @param type the {@link MetadataType} to look up.
     * @return the string value associated with the type, or null if it does not exist.
     */
    public String getMetadata(MetadataType type) {
        return metadata.get(type);
    }

    /**
     * Sets the beatmap ID for a specific beatmap file within the package.
     *
     * @param filename  the name of the target file.
     * @param beatmapID the unique ID to assign to the beatmap.
     * @throws IOException if the file does not exist or if it is not a valid beatmap.
     */
    public void setBeatmapID(String filename, int beatmapID) throws IOException {
        var pf = findFileByName(filename).orElseThrow(() -> new IOException("File not found: " + filename));
        if (!pf.isBeatmap()) throw new IOException("File is not a beatmap: " + filename);
        beatmapIds.put(filename, beatmapID);
    }

    /**
     * Sets the BeatmapSetID metadata entry for this package.
     *
     * @param beatmapSetID the unique dataset ID of the beatmap set.
     */
    public void setBeatmapSetID(int beatmapSetID) {
        addMetadata(MetadataType.BeatmapSetID, String.valueOf(beatmapSetID));
    }

    /**
     * Saves the current package state to the specified file path.
     *
     * @param path the destination path on the system where the package will be written.
     * @throws IOException if an I/O error occurs during the export or write operation.
     */
    public void save(String path) throws IOException {
        Files.write(Paths.get(path), export());
    }

    /**
     * Exports the current package state into a valid osz2 binary format.
     *
     * @return a byte array containing the fully structured and encrypted osz2 package data.
     * @throws IOException if an error occurs during data serialization or cryptographic processing.
     */
    public byte[] export() throws IOException {
        var result = new PackageWriter().write(metadata, beatmapIds, files, iv, version, keyType, key);

        // Update internal state to match written data
        key = keyType.generateKey(metadata);
        this.metadataHash = result.metadataHash();
        this.fileInfoHash = result.fileInfoHash();
        this.fullBodyHash = result.bodyHash();

        return result.data();
    }

    /**
     * Creates a regular .osz (ZIP-compressed) package from the current files in memory.
     *
     * @param excludeDisallowedFiles if true, files with restricted or non-standard
     * extensions will be skipped during packaging.
     * @return a byte array representing the compiled and compressed .osz package data.
     * @throws IOException if an error occurs while writing to the ZIP output stream.
     */
    public byte[] createOszPackage(boolean excludeDisallowedFiles) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var stream = new ZipOutputStream(buffer)) {
            for (var file : files) {
                if (file.getContent() == null) continue;
                if (excludeDisallowedFiles && !file.isAllowedExtension()) continue;

                var entry = new ZipEntry(file.getFilenameSanitized());
                if (file.getDateModified() != null) {
                    var zonedDateTime = file.getDateModified().atZone(ZoneOffset.UTC);
                    // Check if date_time is valid for zip format
                    if (zonedDateTime.getYear() >= 1980) {
                        entry.setTimeLocal(zonedDateTime.toLocalDateTime());
                    } else {
                        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0, 0));
                    }
                }

                stream.putNextEntry(entry);
                stream.write(file.getContent());
                stream.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
