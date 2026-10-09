package com.sheetmusic4j.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sheetmusic4j.core.model.Score;

/**
 * Reads a {@link Score} from one file format.
 */
public interface ScoreReader {

    /**
     * Read a score from a stream.
     *
     * @param in the stream to read (fully consumed, not closed)
     * @return the score
     * @throws ScoreFormatException when the content cannot be read or parsed
     */
    Score read(InputStream in);

    /**
     * Read a score from a file.
     *
     * @param path the file to read
     * @return the score
     * @throws ScoreFormatException when the file cannot be read or parsed
     */
    default Score read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new ScoreFormatException("Could not read " + path, e);
        }
    }
}
