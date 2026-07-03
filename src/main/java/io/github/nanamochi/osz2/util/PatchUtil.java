package io.github.nanamochi.osz2.util;

import io.sigpipe.jbsdiff.Patch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.compress.compressors.CompressorException;

/**
 * Utility methods for applying bsdiff patches to osz2 packages.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PatchUtil {

    /**
     * Applies a bsdiff patch to an osz2 package and returns the
     * resulting patched package bytes.
     *
     * @param sourceOsz2 the original osz2 file bytes.
     * @param patchBytes the bsdiff patch bytes.
     * @return the patched osz2 bytes.
     * @throws IOException if an I/O or decompression error occurs during patching.
     */
    public static byte[] applyBsdiffPatch(byte[] sourceOsz2, byte[] patchBytes) throws IOException {
        try {
            var out = new ByteArrayOutputStream();
            Patch.patch(sourceOsz2, patchBytes, out);
            return out.toByteArray();
        } catch (CompressorException | io.sigpipe.jbsdiff.InvalidHeaderException e) {
            throw new IOException("Failed to decompress bsdiff patch", e);
        }
    }
}
