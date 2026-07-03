package io.github.nanamochi.osz2.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents all known metadata fields that can appear in an osz2 package.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum MetadataType {
    Title(0),
    Artist(1),
    Creator(2),
    Version(3),
    Source(4),
    Tags(5),
    VideoDataOffset(6),
    VideoDataLength(7),
    VideoHash(8),
    BeatmapSetID(9),
    Genre(10),
    Language(11),
    TitleUnicode(12),
    ArtistUnicode(13),
    Protocol(14), // Unsure what this is, but some osf2 files set it to "http"
    Unknown(9999),
    Difficulty(10000),
    PreviewTime(10001),
    ArtistFullName(10002),
    ArtistTwitter(10003),
    SourceUnicode(10004),
    ArtistURL(10005),
    Revision(10006),
    PackID(10007);

    private final int value;

    public static MetadataType fromValue(int value) {
        for (MetadataType t : values()) {
            if (t.value == value) return t;
        }
        return Unknown;
    }
}
