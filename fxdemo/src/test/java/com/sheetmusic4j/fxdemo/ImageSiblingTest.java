package com.sheetmusic4j.fxdemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSiblingTest {

    @Test
    void candidatesReplaceExtensionForEachKnownFormat() {
        List<Path> candidates = ImageSibling.candidatesFor(Path.of("/music/song.abc"));
        assertEquals(ImageSibling.EXTENSIONS.size(), candidates.size());
        assertEquals(Path.of("/music/song.png"), candidates.get(0));
        assertEquals(Path.of("/music/song.jpg"), candidates.get(1));
        assertEquals(Path.of("/music/song.jpeg"), candidates.get(2));
        assertEquals(Path.of("/music/song.gif"), candidates.get(3));
        assertEquals(Path.of("/music/song.bmp"), candidates.get(4));
    }

    @Test
    void candidatesHandleFileWithoutExtension() {
        List<Path> candidates = ImageSibling.candidatesFor(Path.of("/music/song"));
        assertEquals(Path.of("/music/song.png"), candidates.get(0));
    }

    @Test
    void ignoresImageInput() {
        assertTrue(ImageSibling.candidatesFor(Path.of("/music/song.png")).isEmpty());
        assertTrue(ImageSibling.candidatesFor(Path.of("/music/song.JPG")).isEmpty());
    }

    @Test
    void existingPathPrefersPngOverJpg(@TempDir Path dir) throws IOException {
        Path score = dir.resolve("piece.abc");
        Files.writeString(score, "X:1\nT:Piece\nK:C\n");

        assertTrue(ImageSibling.existingPathFor(score).isEmpty(), "no image yet");

        Path jpg = dir.resolve("piece.jpg");
        Files.writeString(jpg, "fake jpg");
        Optional<Path> resolvedJpg = ImageSibling.existingPathFor(score);
        assertTrue(resolvedJpg.isPresent());
        assertEquals(jpg, resolvedJpg.get());

        Path png = dir.resolve("piece.png");
        Files.writeString(png, "fake png");
        Optional<Path> resolvedPng = ImageSibling.existingPathFor(score);
        assertTrue(resolvedPng.isPresent());
        assertEquals(png, resolvedPng.get(), "png must win over jpg when both exist");
    }

    @Test
    void nullInputYieldsEmpty() {
        assertFalse(ImageSibling.existingPathFor(null).isPresent());
        assertTrue(ImageSibling.candidatesFor(null).isEmpty());
    }
}
