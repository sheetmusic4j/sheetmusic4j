package com.sheetmusic4j.fxviewer;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javafx.scene.Group;
import javafx.scene.Node;

/**
 * Keeps a wide layout rasterised as fixed-width vertical tiles, but only the
 * tiles near what is visible. GPUs cap texture size (a single canvas or image
 * wider than about 16k pixels draws nothing) and a whole long score in pixels
 * costs gigabytes, so views that show a window of a long strip ask for the
 * tiles covering {@link #show(double, double) the visible range}: missing
 * ones are painted, tiles more than one tile width away are dropped.
 *
 * <p>How a tile is drawn is up to the {@link TilePainter} - a
 * {@code Canvas} for {@link StripSheetView}, a baked image for
 * {@link FlyOverSheetView} - which lets both views share the bookkeeping.
 * Tile nodes live in {@link #group()} and must be positioned by the painter.
 * FX thread only.
 */
final class LayoutTiles {

    /** Paints the tile covering {@code [x0, x0 + width)} in layout units. */
    @FunctionalInterface
    interface TilePainter {
        Node paint(double x0, double width);
    }

    private final Group group = new Group();
    private final TilePainter painter;
    private final TreeMap<Integer, Node> live = new TreeMap<>();
    private double tileWidth = 1;
    private double totalWidth;
    private double shownX0 = Double.NaN;
    private double shownX1 = Double.NaN;

    LayoutTiles(TilePainter painter) {
        this.painter = painter;
    }

    /** The node holding the live tiles. */
    Group group() {
        return group;
    }

    /**
     * Start over for a new layout (or a new scale): drops every tile. Nothing
     * is painted until the next {@link #show}.
     *
     * @param totalWidth width of the layout, in layout units
     * @param tileWidth  width of one tile, in layout units
     */
    void reset(double totalWidth, double tileWidth) {
        live.clear();
        group.getChildren().clear();
        this.totalWidth = Math.max(totalWidth, 0);
        this.tileWidth = Math.max(tileWidth, 1);
        shownX0 = Double.NaN;
        shownX1 = Double.NaN;
    }

    /**
     * Make sure the tiles overlapping {@code [x0, x1]} exist and drop the ones
     * further than one tile away. Cheap when nothing changes, so it can run
     * every frame.
     */
    void show(double x0, double x1) {
        shownX0 = x0;
        shownX1 = x1;
        int[] need = range(x0, x1, tileWidth, totalWidth, 0);
        int[] keep = range(x0, x1, tileWidth, totalWidth, 1);
        for (Iterator<Map.Entry<Integer, Node>> it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Node> tile = it.next();
            if (tile.getKey() < keep[0] || tile.getKey() > keep[1]) {
                group.getChildren().remove(tile.getValue());
                it.remove();
            }
        }
        for (int i = need[0]; i <= need[1]; i++) {
            if (!live.containsKey(i)) {
                Node node = paintTile(i);
                live.put(i, node);
                group.getChildren().add(node);
            }
        }
    }

    /** Repaint the live tiles, e.g. after highlights changed. */
    void repaint() {
        live.clear();
        group.getChildren().clear();
        if (!Double.isNaN(shownX0)) {
            show(shownX0, shownX1);
        }
    }

    /** Number of tiles currently painted. */
    int liveCount() {
        return live.size();
    }

    private Node paintTile(int index) {
        double x0 = index * tileWidth;
        // One unit of overlap hides the seam linear filtering leaves at tile edges.
        double width = Math.min(tileWidth + 1, totalWidth - x0);
        return painter.paint(x0, width);
    }

    /**
     * The first and last tile index overlapping {@code [x0, x1]}, widened by
     * {@code extra} tiles on each side and clamped to the layout; an empty
     * range ({@code first > last}) when the layout is empty.
     */
    static int[] range(double x0, double x1, double tileWidth, double totalWidth, int extra) {
        int count = (int) Math.ceil(totalWidth / tileWidth);
        if (count <= 0) {
            return new int[] {0, -1};
        }
        double lo = Math.min(x0, x1);
        double hi = Math.max(x0, x1);
        int first = (int) Math.floor(lo / tileWidth) - extra;
        int last = (int) Math.floor(hi / tileWidth) + extra;
        return new int[] {Math.max(first, 0), Math.min(last, count - 1)};
    }
}
