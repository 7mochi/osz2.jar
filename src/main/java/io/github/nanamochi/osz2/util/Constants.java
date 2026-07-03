package io.github.nanamochi.osz2.util;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {
    /**
     * Constant derived from FastRandom(1990). Used to verify the encryption key via XTEA.
     * <a href="https://github.com/ppy/osu-stream/blob/master/osu!stream/Helpers/osu!common/MapPackage.cs#L64">Reference/</a>
     */
    public static final byte[] KNOWN_PLAIN = {
        0x55,
        (byte) 0xAA,
        0x74,
        0x10,
        0x2B,
        0x56,
        (byte) 0xB3,
        (byte) 0x9E,
        0x25,
        (byte) 0x9E,
        (byte) 0xFE,
        (byte) 0xB7,
        (byte) 0xBE,
        0x06,
        (byte) 0xFC,
        (byte) 0xF2,
        (byte) 0xB6,
        0x3C,
        0x6F,
        0x47,
        0x7E,
        0x38,
        0x69,
        0x43,
        (byte) 0x80,
        (byte) 0x89,
        0x25,
        0x00,
        (byte) 0xCC,
        (byte) 0xB6,
        (byte) 0xFE,
        0x12,
        (byte) 0xA9,
        (byte) 0xB2,
        0x4A,
        0x2C,
        (byte) 0x96,
        (byte) 0xD5,
        (byte) 0xEA,
        0x26,
        0x42,
        0x31,
        (byte) 0xAF,
        0x0A,
        0x0D,
        (byte) 0xAE,
        0x00,
        (byte) 0xED,
        (byte) 0xFE,
        (byte) 0x96,
        (byte) 0xA6,
        (byte) 0x94,
        (byte) 0x99,
        (byte) 0xA7,
        (byte) 0x90,
        (byte) 0xE4,
        0x68,
        (byte) 0xBF,
        (byte) 0xC6,
        (byte) 0x97,
        0x5B,
        0x1B,
        0x5E,
        0x7F
    };

    /**
     * A list of all allowed file extensions in an .osz package.
     */
    public static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
            "osu", "osz", "osb", "osk", "png", "mp3", "wav", "ogg", "jpg", "wmv", "flv", "flac", "avi", "ini", "m4v",
            "mpg", "mov", "webm", "ogv", "mpeg", "3gp", "mkv", "mp4", "jpeg");

    /**
     * osu! officially only uses ".avi", ".flv" and ".mpg" for video files.
     */
    public static final Set<String> VIDEO_FILE_EXTENSIONS =
            Set.of("wmv", "flv", "avi", "m4v", "mpg", "mov", "webm", "ogv", "mpeg", "3gp", "mkv", "mp4");
}
