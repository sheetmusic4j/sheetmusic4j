package com.sheetmusic4j.pdf;

import com.sheetmusic4j.core.io.ScoreFormatException;

/**
 * Thrown when a PDF cannot be read as engraved music: damaged, scanned, or set in an unsupported music font.
 */
public class PdfException extends ScoreFormatException {
    public PdfException(String message) {
        super(message);
    }

    public PdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
