package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Segment;

/**
 * A crescendo or diminuendo wedge: two thin slanted lines that meet in a point at one end and are about a space apart
 * at the other. {@code start} and {@code end} are the left and right ends in x, {@code y} the height of the point.
 */
record Hairpin(double start, double end, boolean crescendo, double y) {

    private static final double MIN_LENGTH = 1.0;
    private static final double MAX_SLOPE = 0.5;
    private static final double JOIN = 0.7;

    /**
     * Find the wedges of a page and hand each to the staff it hangs from. A wedge that runs on to the next system is
     * drawn as two lines that do not meet and is not found.
     */
    static Map<Staff, List<Hairpin>> find(PageContent page, List<Staff> staves) {
        Map<Staff, List<Hairpin>> found = new HashMap<>();
        for (Staff staff : staves) {
            List<Hairpin> hairpins = pairUp(candidates(page, staves, staff), staff.space());
            if (!hairpins.isEmpty()) {
                found.put(staff, hairpins);
            }
        }
        return found;
    }

    /** Thin, long, nearly flat lines that hang from this staff and from no other. */
    private static List<Segment> candidates(PageContent page, List<Staff> staves, Staff staff) {
        double space = staff.space();
        return page.diagonals().stream()
                .filter(s -> s.thickness() < 1.5 && s.x1() - s.x0() >= MIN_LENGTH * space
                        && Math.abs(s.y1() - s.y0()) <= MAX_SLOPE * (s.x1() - s.x0())
                        && nearest(staves, (s.y0() + s.y1()) / 2) == staff
                        && staff.distanceTo((s.y0() + s.y1()) / 2) <= 6 * space
                        && s.x0() >= staff.x0() - space && s.x1() <= staff.x1() + space)
                .toList();
    }

    private static List<Hairpin> pairUp(List<Segment> candidates, double space) {
        List<Hairpin> hairpins = new ArrayList<>();
        boolean[] used = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size() && !used[i]; j++) {
                Hairpin pair = used[j] ? null : pair(candidates.get(i), candidates.get(j), space);
                if (pair != null) {
                    used[i] = true;
                    used[j] = true;
                    addUnlessDuplicate(hairpins, pair);
                }
            }
        }
        return hairpins;
    }

    /** A filled and stroked outline draws every line twice. */
    private static void addUnlessDuplicate(List<Hairpin> hairpins, Hairpin pair) {
        boolean duplicate = hairpins.stream().anyMatch(h -> Math.abs(h.start - pair.start) < 1
                && Math.abs(h.end - pair.end) < 1 && h.crescendo == pair.crescendo);
        if (!duplicate) {
            hairpins.add(pair);
        }
    }

    private static Staff nearest(List<Staff> staves, double y) {
        Staff best = null;
        for (Staff s : staves) {
            if (best == null || Math.abs(y - s.midY()) < Math.abs(y - best.midY())) {
                best = s;
            }
        }
        return best;
    }

    private static Hairpin pair(Segment a, Segment b, double space) {
        // point on the left, opening to the right: crescendo
        if (close(a.x0(), b.x0()) && close(a.y0(), b.y0()) && (a.y1() - a.y0()) * (b.y1() - b.y0()) < 0
                && Math.abs(a.x1() - b.x1()) < 1.2 && opening(Math.abs(a.y1() - b.y1()), space)) {
            return new Hairpin(a.x0(), Math.max(a.x1(), b.x1()), true, a.y0());
        }
        // point on the right, open on the left: diminuendo
        if (close(a.x1(), b.x1()) && close(a.y1(), b.y1()) && (a.y0() - a.y1()) * (b.y0() - b.y1()) < 0
                && Math.abs(a.x0() - b.x0()) < 1.2 && opening(Math.abs(a.y0() - b.y0()), space)) {
            return new Hairpin(Math.min(a.x0(), b.x0()), a.x1(), false, a.y1());
        }
        return null;
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < JOIN;
    }

    private static boolean opening(double width, double space) {
        return width >= 0.4 * space && width <= 2.4 * space;
    }
}
