package com.sheetmusic4j.fxviewer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
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

    @Test
    void rangedRepaintOnlyRedrawsLiveTilesInTheRange() {
        List<Double> painted = new ArrayList<>();
        LayoutTiles tiles = new LayoutTiles((x0, width) -> {
            painted.add(x0);
            return new Group();
        });
        tiles.reset(10_000, 1000);
        tiles.show(1500, 3500);
        assertEquals(3, tiles.liveCount());
        painted.clear();

        tiles.repaint(2100, 2200);
        assertEquals(List.of(2000.0), painted);
        assertEquals(3, tiles.liveCount());
        assertEquals(3, tiles.group().getChildren().size());

        painted.clear();
        tiles.repaint(7000, 8000);
        assertEquals(List.of(), painted, "tiles that are not live stay unpainted");
    }
}
