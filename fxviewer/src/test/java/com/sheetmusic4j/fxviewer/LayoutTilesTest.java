package com.sheetmusic4j.fxviewer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** Which tiles {@link LayoutTiles} keeps for a visible range. */
class LayoutTilesTest {

    @Test
    void coversTheTilesOverlappingTheRange() {
        assertArrayEquals(new int[] {1, 2}, LayoutTiles.range(1500, 2500, 1000, 10_000, 0));
        assertArrayEquals(new int[] {2, 2}, LayoutTiles.range(2000, 2999, 1000, 10_000, 0));
    }

    @Test
    void extraTilesWidenTheRange() {
        assertArrayEquals(new int[] {0, 3}, LayoutTiles.range(1500, 2500, 1000, 10_000, 1));
    }

    @Test
    void clampsToTheLayout() {
        assertArrayEquals(new int[] {0, 0}, LayoutTiles.range(-5000, 100, 1000, 10_000, 0));
        assertArrayEquals(new int[] {8, 9}, LayoutTiles.range(8500, 50_000, 1000, 10_000, 0));
        assertArrayEquals(new int[] {0, 2}, LayoutTiles.range(0, 1e9, 1000, 2500, 0), "last partial tile counts");
    }

    @Test
    void rangeOutsideTheLayoutIsEmpty() {
        int[] range = LayoutTiles.range(20_000, 30_000, 1000, 10_000, 0);
        assertArrayEquals(new int[] {20, 9}, range);
        assertArrayEquals(new int[] {0, -1}, LayoutTiles.range(0, 100, 1000, 0, 0));
    }
}
