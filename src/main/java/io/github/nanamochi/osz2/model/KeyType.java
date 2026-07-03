package io.github.nanamochi.osz2.model;

import io.github.nanamochi.osz2.util.Utils;
import java.util.Map;

/**
 * Determines how encryption keys are generated for package operations.
 */
public enum KeyType {
    /**
     * Regular .osz2 files, mainly used for beatmap submission.
     * Requires: Creator &amp; BeatmapSetID metadata fields.
     */
    OSZ2 {
        @Override
        public byte[] generateKey(Map<MetadataType, String> metadata) {
            String creator = metadata.get(MetadataType.Creator);
            String beatmapSetId = metadata.get(MetadataType.BeatmapSetID);

            if (creator == null) throw new IllegalArgumentException("Missing Creator");
            if (beatmapSetId == null) throw new IllegalArgumentException("Missing BeatmapSetID");

            return Utils.md5(creator + "yhxyfjo5" + beatmapSetId);
        }
    },
    /**
     * .osf2 files, used for beatmap packages inside osu!stream.
     * Requires: Title &amp; Artist metadata fields.
     */
    OSF2 {
        @Override
        public byte[] generateKey(Map<MetadataType, String> metadata) {
            String title = metadata.get(MetadataType.Title);
            String artist = metadata.get(MetadataType.Artist);

            if (title == null) throw new IllegalArgumentException("Missing Title");
            if (artist == null) throw new IllegalArgumentException("Missing Artist");

            return Utils.md5("\u0008" + title + "4390gn8931i" + artist);
        }
    };

    /**
     * Generates an encryption key from package metadata using the key type's
     * derivation algorithm.
     *
     * @param metadata the package metadata map.
     * @return a 16-byte MD5-derived encryption key.
     */
    public abstract byte[] generateKey(Map<MetadataType, String> metadata);
}
