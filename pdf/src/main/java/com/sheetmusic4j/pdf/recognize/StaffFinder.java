package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.HLine;
import com.sheetmusic4j.pdf.extract.PageContent.VLine;

/**
 * Finds staves as runs of exactly five equally spaced long horizontal lines, and groups them into systems.
 * Longer runs (tablature) and shorter ones (percussion) are not staves of this reader.
 */
public final class StaffFinder {

    private static final double MIN_LINE_LENGTH = 60;

    private StaffFinder() {
    }

    /**
     * All staves of the page, top to bottom.
     */
    public static List<Staff> find(PageContent page) {
        List<HLine> lines = new ArrayList<>();
        page.hlines().stream()
                .filter(l -> l.x1() - l.x0() >= MIN_LINE_LENGTH && l.thickness() < 2)
                .sorted(Comparator.comparingDouble(HLine::y))
                .forEach(l -> {
                    HLine last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
                    boolean duplicate = last != null && Math.abs(last.y() - l.y()) < 0.25
                            && Math.abs(last.x0() - l.x0()) < 3 && Math.abs(last.x1() - l.x1()) < 3;
                    if (!duplicate) {
                        lines.add(l);
                    }
                });
        List<Staff> staves = new ArrayList<>();
        int i = 0;
        while (i + 4 < lines.size()) {
            int run = runLength(lines, i);
            if (run == 5) {
                double x0 = Double.MAX_VALUE;
                double x1 = 0;
                for (int k = i; k < i + 5; k++) {
                    x0 = Math.min(x0, lines.get(k).x0());
                    x1 = Math.max(x1, lines.get(k).x1());
                }
                staves.add(new Staff(x0, x1, lines.get(i).y(), lines.get(i + 4).y()));
                i += 5;
            } else {
                i += Math.max(run, 1);
            }
        }
        return staves;
    }

    private static int runLength(List<HLine> lines, int start) {
        HLine first = lines.get(start);
        double space = lines.get(start + 1).y() - first.y();
        if (space < 2 || space > 12) {
            return 1;
        }
        int run = 1;
        while (start + run < lines.size()) {
            HLine prev = lines.get(start + run - 1);
            HLine next = lines.get(start + run);
            boolean sameExtent = Math.abs(next.x0() - first.x0()) < 3 && Math.abs(next.x1() - first.x1()) < 3;
            if (!sameExtent || Math.abs(next.y() - prev.y() - space) > Math.max(0.35, 0.1 * space)) {
                break;
            }
            run++;
        }
        return run;
    }

    /**
     * Groups staves into systems. Staves are one system when a vertical line at their left edge spans from one to
     * the next (the system start line), or when they share the same barline positions (several instrument groups
     * of one orchestral system).
     */
    public static List<List<Staff>> systems(List<Staff> staves, PageContent page) {
        List<List<Staff>> systems = new ArrayList<>();
        for (Staff staff : staves) {
            if (!systems.isEmpty() && joined(systems.get(systems.size() - 1), staff, page)) {
                systems.get(systems.size() - 1).add(staff);
            } else {
                List<Staff> system = new ArrayList<>();
                system.add(staff);
                systems.add(system);
            }
        }
        return systems;
    }

    private static boolean joined(List<Staff> system, Staff next, PageContent page) {
        Staff prev = system.get(system.size() - 1);
        double tol = prev.space() * 0.3;
        for (VLine v : page.vlines()) {
            if (Math.abs(v.x() - prev.x0()) <= 3 + v.thickness() && v.y0() <= prev.top() + tol
                    && v.y1() >= next.bottom() - tol) {
                return true;
            }
        }
        List<Double> a = fullHeightX(prev, page);
        List<Double> b = fullHeightX(next, page);
        if (a.size() < 2 || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i) - b.get(i)) > 0.8) {
                return false;
            }
        }
        return true;
    }

    private static List<Double> fullHeightX(Staff staff, PageContent page) {
        double tol = staff.space() * 0.3;
        return page.vlines().stream()
                .filter(v -> v.y0() <= staff.top() + tol && v.y1() >= staff.bottom() - tol
                        && v.x() > staff.x0() + staff.space())
                .map(VLine::x)
                .sorted()
                .distinct()
                .toList();
    }
}
