package io.github.nanamochi.osz2.internal;

import io.github.nanamochi.osz2.PackageFile;
import io.github.nanamochi.osz2.crypto.IOsz2Cipher;
import io.github.nanamochi.osz2.crypto.impl.XTEA;
import io.github.nanamochi.osz2.crypto.impl.XXTEA;
import io.github.nanamochi.osz2.crypto.io.XXTEAWriter;
import io.github.nanamochi.osz2.model.KeyType;
import io.github.nanamochi.osz2.model.MetadataType;
import io.github.nanamochi.osz2.util.Constants;
import io.github.nanamochi.osz2.util.Utils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class PackageWriter {
    public record WriteResult(byte[] data, byte[] metadataHash, byte[] fileInfoHash, byte[] bodyHash) {}

    public WriteResult write(
            Map<MetadataType, String> metadata,
            Map<String, Integer> beatmapIds,
            List<PackageFile> files,
            byte[] iv,
            int version,
            KeyType keyType,
            byte[] key)
            throws IOException {
        if (files.isEmpty()) {
            throw new IOException("Cannot export an empty package");
        }

        // Validate required metadata for the given key type
        validateMetadata(metadata, keyType);

        // Generate encryption key from metadata
        if (key == null) {
            key = keyType.generateKey(metadata);
        }
        int[] keyArray = Utils.bytesToUint32Array(key);

        var exportFiles = prepareExportFiles(files);
        // Sort files using the same logic as C# FileComparer
        // https://github.com/ppy/osu-stream/blob/master/osu!stream/Helpers/osu!common/MapPackage.cs#L1478
        exportFiles.sort((a, b) -> {
            boolean aVideo = a.isVideo();
            boolean bVideo = b.isVideo();
            if (aVideo != bVideo) return aVideo ? 1 : -1;
            // Videos go last, other files go first sorted by filename
            return a.getFilename().compareTo(b.getFilename());
        });

        processVideoMetadata(exportFiles, metadata);
        updateOffsets(exportFiles);

        // Encrypt file data & file info
        var encryptedFileData = writeAndEncryptFileData(exportFiles, keyArray);
        var encryptedFileInfo = writeAndEncryptFileInfo(exportFiles, keyArray);

        // Calculate hashes
        var fileInfoHash = Utils.computeOszHash(encryptedFileInfo, exportFiles.size() * 4, 0xD1);
        var hasVideo = metadata.containsKey(MetadataType.VideoDataOffset)
                && metadata.containsKey(MetadataType.VideoDataLength);
        Integer videoOffset = hasVideo ? Integer.parseInt(metadata.get(MetadataType.VideoDataOffset)) : null;
        Integer videoLength = hasVideo ? Integer.parseInt(metadata.get(MetadataType.VideoDataLength)) : null;
        var bodyHash = Utils.computeBodyHash(encryptedFileData, videoOffset, videoLength);

        var metaDataBytes = writeMetadata(metadata);
        var metaHash = Utils.computeOszHash(metaDataBytes, metadata.size() * 3, 0xA7);

        // Encode IV by XORing with body hash
        byte[] encodedIV = new byte[16];
        for (int i = 0; i < 16; i++) {
            encodedIV[i] = (byte) (iv[i] ^ bodyHash[i]);
        }

        // Write osz2 header
        var output = new ByteArrayOutputStream();

        // Magic bytes + version
        output.write(new byte[] {(byte) 0xEC, 0x48, 0x4F});
        output.write(version);
        output.write(encodedIV);
        output.write(metaHash);
        output.write(fileInfoHash);
        output.write(bodyHash);
        output.write(metaDataBytes);

        // Write beatmap ID mappings
        var beatmapFiles = exportFiles.stream().filter(PackageFile::isBeatmap).toList();
        output.write(Utils.int32LE(beatmapFiles.size()));
        for (var f : beatmapFiles) {
            output.write(Utils.writeString(f.getFilename()));
            Integer id = beatmapIds.get(f.getFilename());
            output.write(Utils.int32LE(id != null ? id : -1));
        }

        // Encrypted known-plain marker (XTEA-encrypted, 64 bytes)
        byte[] knownPlain = Constants.KNOWN_PLAIN.clone();
        IOsz2Cipher xtea = new XTEA(key);
        xtea.encrypt(knownPlain, 0, 64);
        output.write(knownPlain);

        // Obfuscate file info length using file info hash bytes
        int encodedLength = encryptedFileInfo.length;
        for (int i = 0; i < 16; i += 2) {
            encodedLength += (fileInfoHash[i] & 0xFF) | ((fileInfoHash[i + 1] & 0xFF) << 17);
        }
        output.write(Utils.int32LE(encodedLength));

        output.write(encryptedFileInfo);
        output.write(encryptedFileData);

        return new WriteResult(output.toByteArray(), metaHash, fileInfoHash, bodyHash);
    }

    private void validateMetadata(Map<MetadataType, String> metadata, KeyType keyType) throws IOException {
        if (keyType == KeyType.OSZ2) {
            if (!metadata.containsKey(MetadataType.Creator))
                throw new IOException("Missing required metadata: Creator");
            if (!metadata.containsKey(MetadataType.BeatmapSetID))
                throw new IOException("Missing required metadata: BeatmapSetID");
        } else if (keyType == KeyType.OSF2) {
            if (!metadata.containsKey(MetadataType.Title)) throw new IOException("Missing required metadata: Title");
            if (!metadata.containsKey(MetadataType.Artist)) throw new IOException("Missing required metadata: Artist");
        }
    }

    private List<PackageFile> prepareExportFiles(List<PackageFile> files) {
        var result = new ArrayList<PackageFile>();
        var now = LocalDateTime.now(ZoneOffset.UTC);

        for (var file : files) {
            if (file.getContent() == null || file.getContent().length == 0) continue;

            result.add(new PackageFile(
                    file.getFilename(),
                    0,
                    file.getContent().length,
                    Utils.md5(file.getContent()),
                    file.getDateCreated() != null ? file.getDateCreated() : now,
                    file.getDateModified() != null ? file.getDateModified() : now,
                    file.getContent()));
        }

        return result;
    }

    private void processVideoMetadata(List<PackageFile> files, Map<MetadataType, String> metadata) {
        int offset = 0;
        for (var file : files) {
            if (!file.isVideo()) {
                offset += 4 + file.getContent().length;
                continue;
            }

            // Calculate video hash from middle section of the file
            byte[] content = file.getContent();
            if (content.length < 1024) break;

            int dataLength = content.length;
            int footStart = (dataLength / 2) - ((dataLength / 2) % 16) - 512 + 16;
            byte[] footData = Arrays.copyOfRange(content, footStart, footStart + 1024);

            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(footData);

                metadata.put(MetadataType.VideoDataOffset, String.valueOf(offset));
                metadata.put(MetadataType.VideoDataLength, String.valueOf(dataLength));
                metadata.put(
                        MetadataType.VideoHash, HexFormat.of().formatHex(hash).toUpperCase());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            break;
        }
    }

    private void updateOffsets(List<PackageFile> files) {
        int offset = 0;
        for (var file : files) {
            file.setOffset(offset);
            file.setSize(file.getContent().length + 4);
            file.setHash(Utils.md5(file.getContent()));
            offset += 4 + file.getContent().length;
        }
    }

    private byte[] writeAndEncryptFileData(List<PackageFile> files, int[] keyArray) throws IOException {
        try (var writer = new XXTEAWriter(new XXTEA(Utils.uint32ToByteArray(keyArray)))) {
            for (var f : files) {
                byte[] content = f.getContent();
                writer.write(Utils.int32LE(content.length));
                writer.write(content);
            }

            return writer.toByteArray();
        }
    }

    private byte[] writeAndEncryptFileInfo(List<PackageFile> files, int[] keyArray) throws IOException {
        try (var writer = new XXTEAWriter(new XXTEA(Utils.uint32ToByteArray(keyArray)))) {
            // Write file count
            writer.write(Utils.int32LE(files.size()));

            if (!files.isEmpty()) {
                // Write first offset (always 0)
                writer.write(Utils.int32LE(files.getFirst().getOffset()));
            }

            for (int i = 0; i < files.size(); i++) {
                var f = files.get(i);

                byte[] fnEncoded = f.getFilename().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Utils.writeULEB128(writer, fnEncoded.length);
                writer.write(fnEncoded);

                // Write file hash (16 bytes)
                writer.write(f.getHash() != null ? f.getHash() : new byte[16]);

                // Write timestamps (.NET binary format, int64 LE each)
                long createdTicks = Utils.dateTimeToBinary(f.getDateCreated());
                long modifiedTicks = Utils.dateTimeToBinary(f.getDateModified());
                writer.write(Utils.int64LE(createdTicks));
                writer.write(Utils.int64LE(modifiedTicks));

                // Write next offset (int32 LE), except for last file
                if (i + 1 < files.size()) {
                    writer.write(Utils.int32LE(files.get(i + 1).getOffset()));
                }
            }

            return writer.toByteArray();
        }
    }

    private byte[] writeMetadata(Map<MetadataType, String> metadata) {
        var buf = new ByteArrayOutputStream();

        buf.writeBytes(Utils.int32LE(metadata.size()));

        var entries = new ArrayList<>(metadata.entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparingInt(MetadataType::getValue)));

        for (var entry : entries) {
            short typeValue = (short) entry.getKey().getValue();
            buf.writeBytes(new byte[] {(byte) typeValue, (byte) (typeValue >> 8)});
            buf.writeBytes(Utils.writeString(entry.getValue()));
        }

        return buf.toByteArray();
    }
}
