package com.sheetmusic4j.fxdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSiblingTest {

    @Test
    void replacesExtensionWithPdf() {
        Path pdf = PdfSibling.pathFor(Path.of("/music/song.musicxml")).orElseThrow();
        assertEquals(Path.of("/music/song.pdf"), pdf);
    }

    @Test
    void handlesFileWithoutExtension() {
        Path pdf = PdfSibling.pathFor(Path.of("/music/song")).orElseThrow();
        assertEquals(Path.of("/music/song.pdf"), pdf);
    }

    @Test
    void ignoresPdfInput() {
        assertTrue(PdfSibling.pathFor(Path.of("/music/song.pdf")).isEmpty());
    }

    @Test
    void existingPathResolvesOnlyWhenFilePresent(@TempDir Path dir) throws IOException {
        Path score = dir.resolve("piece.musicxml");
        Files.writeString(score, "<score/>");

        assertTrue(PdfSibling.existingPathFor(score).isEmpty(), "no pdf yet");

        Path pdf = dir.resolve("piece.pdf");
        Files.writeString(pdf, "%PDF-1.4");

        Optional<Path> resolved = PdfSibling.existingPathFor(score);
        assertTrue(resolved.isPresent());
        assertEquals(pdf, resolved.get());
    }

    @Test
    void nullInputYieldsEmpty() {
        assertFalse(PdfSibling.pathFor(null).isPresent());
    }
}
