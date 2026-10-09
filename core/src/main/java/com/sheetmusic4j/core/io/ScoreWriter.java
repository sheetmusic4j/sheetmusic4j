package com.sheetmusic4j.core.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sheetmusic4j.core.model.Score;

/**
 * Writes a {@link Score} in one file format.
 */
public interface ScoreWriter {

    /**
     * Write a score to a stream.
     *
     * @param score the score to write
     * @param out   the stream to write to
     * @throws ScoreFormatException when the score cannot be written
     */
    void write(Score score, OutputStream out);

    /**
     * Write a score to a file, replacing it if it exists.
     *
     * @param score the score to write
     * @param path  the file to write
     * @throws ScoreFormatException when the file cannot be written
     */
    default void write(Score score, Path path) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            write(score, out);
        } catch (IOException e) {
            throw new ScoreFormatException("Could not write " + path, e);
        }
    }
}
