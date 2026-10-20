package com.sheetmusic4j.core.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Size-capped reads for compressed containers ({@code .mxl}, {@code .gp}),
 * so a malicious or corrupt archive that inflates to gigabytes fails fast
 * instead of exhausting the heap.
 */
public final class BoundedRead {

    /**
     * Largest single archive entry that will be inflated. Real scores are a
     * few MB at most.
     */
    public static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;

    /**
     * Largest total inflated size across all entries of one archive.
     */
    public static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private BoundedRead() {
    }

    /**
     * Read {@code in} fully, failing once more than {@code maxBytes} have
     * been read.
     *
     * @param in       the stream to read
     * @param maxBytes the maximum number of bytes allowed
     * @param name     what is being read, for the error message
     * @return the bytes read
     * @throws IOException when reading fails or the limit is exceeded
     */
    public static byte[] readAtMost(InputStream in, long maxBytes, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException(name + " exceeds the maximum size of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
