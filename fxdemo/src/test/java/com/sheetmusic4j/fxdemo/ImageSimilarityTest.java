package com.sheetmusic4j.fxdemo;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSimilarityTest {

    private BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 100, 80);
        g.dispose();
        return image;
    }

    @Test
    void identicalImagesAreFullySimilar() {
        BufferedImage image = solid(Color.WHITE);
        assertEquals(1.0, ImageSimilarity.similarity(image, image), 1e-9);
    }

    @Test
    void blackVersusWhiteIsMaximallyDifferent() {
        double similarity = ImageSimilarity.similarity(solid(Color.BLACK), solid(Color.WHITE));
        assertTrue(similarity < 0.01, "expected near-zero similarity but was " + similarity);
    }

    @Test
    void differentResolutionsStillCompare() {
        BufferedImage small = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage large = new BufferedImage(400, 320, BufferedImage.TYPE_INT_RGB);
        for (BufferedImage img : new BufferedImage[]{small, large}) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.dispose();
        }
        assertEquals(1.0, ImageSimilarity.similarity(small, large), 1e-9);
    }

    @Test
    void inkRatioDetectsContent() {
        assertEquals(0.0, ImageSimilarity.inkRatio(solid(Color.WHITE)), 1e-9);
        assertTrue(ImageSimilarity.inkRatio(solid(Color.BLACK)) > 0.99);
    }
}
