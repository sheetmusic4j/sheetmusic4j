package com.sheetmusic4j.pdf.recognize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.core.model.TimeSignature;
import com.sheetmusic4j.pdf.extract.PageContent;
import com.sheetmusic4j.pdf.extract.PageContent.Glyph;
import com.sheetmusic4j.pdf.symbol.MusicFonts;

/**
 * Turns the pages of an engraved PDF, in order, into parts. Each staff of a system is the same part as the staff at
 * the same position in the other systems. When systems have different numbers of staves (orchestral scores that drop
 * resting instruments) the parts are not told apart by instrument yet, so they can be mixed up; the missing staves
 * are filled with invisible rests to keep every part the same length.
 */
public final class ScoreRecognizer {

    private static final int MAX_NAME_LENGTH = 40;

    private final List<PartAcc> parts = new ArrayList<>();
    private int systems;
    private int recognizedMarks;
    private boolean varyingStaffCount;

    private static final class PartAcc {
        final PartState state = new PartState();
        final List<Measure> measures = new ArrayList<>();
        String name = "";
    }

    /**
     * Read the staves of one page.
     */
    public void addPage(PageContent page) {
        List<Staff> staves = StaffFinder.find(page);
        if (staves.isEmpty()) {
            return;
        }
        Map<Staff, List<Mark>> marks = assign(staves, page);
        for (List<Staff> system : StaffFinder.systems(staves, page)) {
            addSystem(system, page, marks);
        }
    }

    public int staffCount() {
        return parts.size();
    }

    /**
     * Whether any music symbol of a supported font was found.
     */
    public boolean foundMusic() {
        return recognizedMarks > 0;
    }

    public boolean staffCountVaries() {
        return varyingStaffCount;
    }

    public List<Part> parts() {
        List<Part> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            PartAcc acc = parts.get(i);
            Part.Builder part = Part.builder("P" + (i + 1))
                    .name(acc.name.isBlank() ? "Part " + (i + 1) : acc.name);
            acc.measures.forEach(part::addMeasure);
            out.add(part.build());
        }
        return out;
    }

    private Map<Staff, List<Mark>> assign(List<Staff> staves, PageContent page) {
        Map<Staff, List<Mark>> marks = new HashMap<>();
        for (Glyph glyph : page.glyphs()) {
            MusicFonts.classify(glyph).ifPresent(symbol -> {
                Mark mark = new Mark(symbol, glyph);
                Staff nearest = null;
                for (Staff staff : staves) {
                    if (mark.cx() >= staff.x0() - staff.space() && mark.cx() <= staff.x1() + staff.space()
                            && (nearest == null || Math.abs(mark.cy() - staff.midY()) < Math.abs(mark.cy() - nearest.midY()))) {
                        nearest = staff;
                    }
                }
                if (nearest != null && nearest.distanceTo(mark.cy()) <= 7 * nearest.space()) {
                    marks.computeIfAbsent(nearest, k -> new ArrayList<>()).add(mark);
                    recognizedMarks++;
                }
            });
        }
        return marks;
    }

    private void addSystem(List<Staff> system, PageContent page, Map<Staff, List<Mark>> marks) {
        if (systems > 0 && system.size() != parts.size()) {
            varyingStaffCount = true;
        }
        int before = parts.isEmpty() ? 0 : parts.get(0).measures.size();
        while (parts.size() < system.size()) {
            PartAcc added = new PartAcc();
            parts.add(added);
            if (before > 0) {
                pad(added, before, parts.get(0).state.time);
            }
        }
        int count = -1;
        TimeSignature time = null;
        for (int i = 0; i < system.size(); i++) {
            Staff staff = system.get(i);
            PartAcc acc = parts.get(i);
            List<Mark> staffMarks = marks.getOrDefault(staff, List.of());
            StaffReader reader = new StaffReader(staff, page, acc.state);
            List<Measure> measures = reader.read(staffMarks, acc.measures.size() + 1, systems > 0);
            acc.measures.addAll(measures);
            if (acc.name.isBlank()) {
                acc.name = label(staff, page);
            }
            if (count < 0) {
                count = measures.size();
                time = acc.state.time;
            }
        }
        for (int i = system.size(); i < parts.size(); i++) {
            pad(parts.get(i), count, time);
        }
        systems++;
    }

    private static void pad(PartAcc part, int measures, TimeSignature time) {
        TimeSignature ts = time == null ? TimeSignature.fourFour() : time;
        int ticks = ts.beats() * StaffReader.DIVISIONS * 4 / ts.beatType();
        for (int i = 0; i < measures; i++) {
            part.measures.add(Measure.builder(part.measures.size() + 1)
                    .addElement(Rest.builder().duration(new Duration(ticks, StaffReader.DIVISIONS))
                            .type(NoteType.WHOLE).staff(1).voice(1).visible(false).build())
                    .build());
        }
    }

    /** Text standing left of the staff, such as an instrument name. */
    private static String label(Staff staff, PageContent page) {
        StringBuilder text = new StringBuilder();
        page.glyphs().stream()
                .filter(g -> g.text() != null && !MusicFonts.isMusicFont(g.font()))
                .filter(g -> g.box().right() < staff.x0() - 0.5 && g.y() > staff.top() - staff.space()
                        && g.y() < staff.bottom() + staff.space())
                .sorted(Comparator.comparingDouble(Glyph::x))
                .forEach(g -> text.append(g.text()));
        String label = text.toString().strip();
        return label.length() > MAX_NAME_LENGTH ? "" : label;
    }
}
