package com.sheetmusic4j.fxdemo;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Compares two raster images and yields a similarity score in {@code [0, 1]}.
 *
 * <p>Both images are scaled to a common grid and reduced to grayscale, then the
 * mean absolute per-pixel difference is turned into a similarity ratio
 * ({@code 1.0} = identical, {@code 0.0} = maximally different). This makes the
 * comparison robust to differing source resolutions, which is what we need when
 * comparing our engraving against a reference PDF/PNG.
 */
public final class ImageSimilarity {

    private static final int GRID = 256;

    private ImageSimilarity() {
    }

    /**
     * Structural similarity of two images as a value in {@code [0, 1]}.
     *
     * @param a first image
     * @param b second image
     * @return similarity score where {@code 1.0} means identical
     */
    public static double similarity(BufferedImage a, BufferedImage b) {
        int[] ga = grayGrid(a);
        int[] gb = grayGrid(b);
        long sum = 0;
        for (int i = 0; i < ga.length; i++) {
            sum += Math.abs(ga[i] - gb[i]);
        }
        double mean = (double) sum / ga.length;
        return 1.0 - mean / 255.0;
    }

    /**
     * Fraction of non-white ("ink") pixels, useful to assert that a rendering is
     * not blank.
     *
     * @param image image to analyze
     * @return ratio of non-white pixels in the normalized image grid
     */
    public static double inkRatio(BufferedImage image) {
        int[] gray = grayGrid(image);
        int ink = 0;
        for (int value : gray) {
            if (value < 250) {
                ink++;
            }
        }
        return (double) ink / gray.length;
    }

    private static int[] grayGrid(BufferedImage image) {
        BufferedImage scaled = new BufferedImage(GRID, GRID, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, GRID, GRID);
        g.drawImage(image, 0, 0, GRID, GRID, null);
        g.dispose();

        int[] result = new int[GRID * GRID];
        for (int y = 0; y < GRID; y++) {
            for (int x = 0; x < GRID; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int bl = rgb & 0xFF;
                result[y * GRID + x] = (int) Math.round(0.299 * r + 0.587 * gr + 0.114 * bl);
            }
        }
        return result;
    }
}
