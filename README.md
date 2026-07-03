# osz2.jar

[![Java Version](https://img.shields.io/badge/java-25%2B-blue.svg)](https://www.java.com/)
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/io.github.7mochi/osz2/badge.svg)](https://central.sonatype.com/artifact/io.github.7mochi/osz2)
[![License](https://img.shields.io/badge/license-mit%20license-brightgreen.svg)](https://github.com/7mochi/osz2.jar/blob/master/LICENSE)

osz2.jar is a Java library for reading and writing osz2 files. It's a direct port of the existing [Osz2Decryptor](https://github.com/xxCherry/Osz2Decryptor) project by [xxCherry](https://github.com/xxCherry), [osz2-go](https://github.com/Lekuruu/osz2-go) and [osz2.py](https://github.com/Lekuruu/osz2.py).

This project *won't* provide beatmap parsing support. You will have to implement that by yourself, if you decide to use this library for implementing the beatmap submission system.

## Examples

Here is an example of how to use osz2.jar as a library:

```java
import io.github.nanamochi.osz2.Package;
import io.github.nanamochi.osz2.model.MetadataType;
import java.nio.file.Files;
import java.nio.file.Path;

// Parse package from file
Package pkg = Package.fromFile("beatmap.osz2");

// Access metadata
System.out.println("Title: " + pkg.getMetadata(MetadataType.Title));
System.out.println("Artist: " + pkg.getMetadata(MetadataType.Artist));
System.out.println("Creator: " + pkg.getMetadata(MetadataType.Creator));
System.out.println("Difficulty: " + pkg.getMetadata(MetadataType.Difficulty));

// Access files
for (var file : pkg.getFiles()) {
    System.out.printf("File: %s, Size: %d bytes%n", file.getFilename(), file.getSize());
}

// Extract specific files
for (var file : pkg.getFiles()) {
    if (!file.getFilename().endsWith(".osu")) continue;
    Files.write(Path.of(file.getFilename()), file.getContent());
}

// Create a regular .osz package
Files.write(Path.of("beatmap.osz"), pkg.createOszPackage(false));
```

### Metadata-only Mode

If you only need to read metadata without extracting files, you can use the `metadataOnly` parameter:

```java
// Only parse metadata
Package pkg = Package.fromFile("beatmap.osz2", true);

// Access metadata
System.out.println("Title: " + pkg.getMetadata(MetadataType.Title));
System.out.println("BeatmapSet ID: " + pkg.getMetadata(MetadataType.BeatmapSetID));
```

### Alternative Constructors

```java
// From file path
Package pkg = Package.fromFile("beatmap.osz2");

// From bytes
byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("beatmap.osz2"));
Package pkg = Package.fromBytes(data);

// With explicit key type
Package pkg = Package.fromFile("beatmap.osf2", KeyType.OSF2);
```

### Exporting an osz2 package

You can initialize and export osz2 packages from a directory:

```java
import io.github.nanamochi.osz2.Package;
import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;
import java.nio.file.Files;
import java.nio.file.Path;

// Initialize package from a directory containing beatmap files
Package pkg = Package.fromDirectory("./my_beatmap_folder", KeyType.OSZ2);

// Export to osz2 format
byte[] osz2Data = pkg.export();
Files.write(Path.of("output.osz2"), osz2Data);

// Or save directly to a file
pkg.save("output.osz2");
```

### Managing Files

You can add, remove, and modify files within a package:

```java
import io.github.nanamochi.osz2.Package;
import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;

// Create a new package
Package pkg = new Package(KeyType.OSZ2);

// Add metadata (required for export)
pkg.addMetadata(MetadataType.Title, "My Beatmap");
pkg.addMetadata(MetadataType.Artist, "Artist Name");
pkg.addMetadata(MetadataType.Creator, "Mapper Name");
pkg.addMetadata(MetadataType.BeatmapSetID, "123456");

// Add a file from memory
byte[] beatmapContent = "osu file format v14\n...".getBytes();
pkg.addFile("my_beatmap.osu", beatmapContent, null, null);

// Add a file from disk
pkg.addFileFromDisk("audio.mp3", "./path/to/audio.mp3");

// Add an entire directory (non-recursive)
pkg.addDirectory("./beatmap_files", false);

// Add an entire directory (recursive, preserves folder structure)
pkg.addDirectory("./beatmap_folder", true);

// Remove a file
pkg.removeFile("old_file.osu");

// Find a file by name
pkg.findFileByName("audio.mp3").ifPresent(file ->
    System.out.printf("Found: %s, size: %d bytes%n", file.getFilename(), file.getSize())
);

// Set beatmap IDs
pkg.setBeatmapID("my_beatmap.osu", 789012);

// Export the package
pkg.save("my_beatmap.osz2");
```

### Managing Metadata

Metadata can be added, retrieved, and removed:

```java
import io.github.nanamochi.osz2.Package;
import io.github.nanamochi.osz2.model.MetadataType;

Package pkg = Package.fromFile("beatmap.osz2");

// Add or update metadata
pkg.addMetadata(MetadataType.Title, "New Title");
pkg.addMetadata(MetadataType.Artist, "New Artist");

// Get metadata
String title = pkg.getMetadata(MetadataType.Title);
System.out.println("Title: " + title);

// Remove metadata
pkg.removeMetadata(MetadataType.Difficulty);

// Convenience method for setting beatmapset ID
pkg.setBeatmapSetID(999999);

// Save changes
pkg.save("modified.osz2");
```

### Using osu!stream .osf2 files

You can read osu!stream packages by passing in `KeyType.OSF2` when initializing the package:

```java
Package pkg = Package.fromFile("beatmap.osf2", KeyType.OSF2);
```

### Applying a patch

When developing an implementation of the beatmap submission system, this could come in handy:

```java
import io.github.nanamochi.osz2.util.PatchUtil;

// Assuming you have a source osz2 file and a patch file
byte[] osz2File = Files.readAllBytes(Path.of("source.osz2"));
byte[] patchFile = Files.readAllBytes(Path.of("patch.bin"));

byte[] updatedOsz2 = PatchUtil.applyBsdiffPatch(osz2File, patchFile);
Package pkg = Package.fromBytes(updatedOsz2);
```

## Installing osz2.jar

osz2.jar is available on the Sonatype Central Repository. These can be used without having to compile the project yourself.

Maven:
```xml
<dependency>
    <groupId>io.github.7mochi</groupId>
    <artifactId>osz2</artifactId>
    <version>0.0.3</version>
</dependency>
```

Gradle:
```groovy
implementation 'io.github.7mochi:osz2:0.0.3'
```

## Thanks to
- [Levi](https://github.com/Lekuruu) for creating and maintaining osz2.py and osz2-go.
