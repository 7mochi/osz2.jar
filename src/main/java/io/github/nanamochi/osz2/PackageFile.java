package io.github.nanamochi.osz2;

import io.github.nanamochi.osz2.util.Constants;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents metadata and binary content of a specific file inside an osz2 package.
 */
@Getter
@Setter
@AllArgsConstructor
@ToString(of = {"filename", "size"})
public class PackageFile {
    @NonNull private String filename;

    private int offset;
    private int size;
    private byte[] hash;
    private LocalDateTime dateCreated;
    private LocalDateTime dateModified;
    private byte[] content;

    /**
     * Extracts the lowercase file extension from the filename without the leading dot.
     *
     * @return the file extension in lowercase, or an empty string if no extension is present.
     */
    public String getFileExtension() {
        String name = filename.toLowerCase().strip();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : "";
    }

    /**
     * Sanitizes the filename by removing or replacing invalid characters to ensure
     * cross-platform path safety.
     *
     * @return the sanitized, path-safe filename string.
     */
    public String getFilenameSanitized() {
        return filename.replace("<", "")
                .replace(">", "")
                .replace(":", "")
                .replace("\"", "")
                .replace("|", "")
                .replace("?", "")
                .replace("*", "")
                .replace("\\", "/");
    }

    /**
     * Determines whether the file is a standard osu! beatmap (.osu) file.
     *
     * @return true if the file extension is "osu", false otherwise.
     */
    public boolean isBeatmap() {
        return "osu".equals(getFileExtension());
    }

    /**
     * Determines whether the file is an osu! stream combined beatmap (.osc) file.
     * <p>
     * Reference format implementation can be found at:
     * <a href="https://github.com/ppy/osu-stream/blob/master/BeatmapCombinator/Program.cs#L31">osu!stream BeatmapCombinator</a>
     *
     * @return true if the file extension is "osc", false otherwise.
     */
    public boolean isCombinedBeatmap() {
        return "osc".equals(getFileExtension());
    }

    /**
     * Checks whether the file is classified as a video file based on its extension.
     *
     * @return true if the file extension is registered as a valid video type, false otherwise.
     */
    public boolean isVideo() {
        return Constants.VIDEO_FILE_EXTENSIONS.contains(getFileExtension());
    }

    /**
     * Checks whether the file extension is explicitly permitted inside standard .osz packages.
     *
     * @return true if the file extension is allowed, false if it should be restricted.
     */
    public boolean isAllowedExtension() {
        return Constants.ALLOWED_FILE_EXTENSIONS.contains(getFileExtension());
    }
}
