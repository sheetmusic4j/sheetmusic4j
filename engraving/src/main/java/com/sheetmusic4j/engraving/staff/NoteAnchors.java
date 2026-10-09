package com.sheetmusic4j.engraving.staff;

import java.util.List;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Rest;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.layout.SystemLayout;
import com.sheetmusic4j.engraving.placement.BeamPlacement;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.engraving.placement.StemPlacement;

import static com.sheetmusic4j.engraving.staff.StaffGeometry.restAnchorStaffStep;
import static com.sheetmusic4j.engraving.staff.StaffGeometry.staffStep;

/**
 * Hit-test / playback anchors for placed elements, and the content extent
 * they (together with the drawn glyphs) cover.
 */
public final class NoteAnchors {

    private NoteAnchors() {
    }

    /**
     * Where an anchor sits in the score: part, staff within the system,
     * measure number and musical timing.
     */
    public record Position(int partIndex, int systemStaffIndex, int measureNumber,
                    double onsetQuarters, double durationQuarters) {
    }

    /**
     * Emit one or more {@link NoteAnchor}s for a placed measure element.
     * A {@link Note} or {@link Rest} produces exactly one anchor with the
     * element itself as {@code elementRef}; a {@link Chord} produces one
     * anchor per member note (each with the note as {@code elementRef} at
     * the same x/onset), plus one anchor for the chord itself so callers
     * may highlight the whole chord as a unit.
     */
    public static void emit(List<NoteAnchor> anchors, MusicElement element,
                     double noteX, double staffY, Clef clef, LayoutOptions options,
                     double stemTipY, boolean hasStem, Position at) {
        int partIndex = at.partIndex();
        int systemStaffIndex = at.systemStaffIndex();
        int measureNumber = at.measureNumber();
        double onsetQuarters = at.onsetQuarters();
        double durationQuarters = at.durationQuarters();
        double gap = options.staffLineGap();
        double approxWidth = gap * 2.5;
        if (element instanceof Note note) {
            int step = staffStep(note.pitch(), clef);
            double y = staffY + step * (gap / 2.0);
            double height = hasStem ? Math.abs(stemTipY - y) + gap : gap * 2;
            anchors.add(new NoteAnchor(note, partIndex, systemStaffIndex, measureNumber,
                    onsetQuarters, durationQuarters, noteX, y, approxWidth, height));
        } else if (element instanceof Chord chord) {
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (Note n : chord.notes()) {
                int step = staffStep(n.pitch(), clef);
                double y = staffY + step * (gap / 2.0);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                double memberHeight = hasStem ? Math.abs(stemTipY - y) + gap : gap * 2;
                anchors.add(new NoteAnchor(n, partIndex, systemStaffIndex, measureNumber,
                        onsetQuarters, durationQuarters, noteX, y, approxWidth, memberHeight));
            }
            double chordCenter = (minY + maxY) / 2.0;
            double chordHeight = (maxY - minY) + gap * 2;
            if (hasStem) {
                chordHeight = Math.max(chordHeight, Math.abs(stemTipY - minY) + gap);
                chordHeight = Math.max(chordHeight, Math.abs(stemTipY - maxY) + gap);
            }
            anchors.add(new NoteAnchor(chord, partIndex, systemStaffIndex, measureNumber,
                    onsetQuarters, durationQuarters, noteX, chordCenter, approxWidth, chordHeight));
        } else if (element instanceof Rest rest) {
            int restStep = restAnchorStaffStep(rest.type());
            double y = staffY + restStep * (gap / 2.0);
            anchors.add(new NoteAnchor(rest, partIndex, systemStaffIndex, measureNumber,
                    onsetQuarters, durationQuarters, noteX, y, approxWidth, gap * 2));
        }
    }

    /**
     * Return the maximum y coordinate reached by any glyph, stem, beam or
     * note anchor in the given systems, i.e. the true bottom of the drawn
     * content. Used to expand {@link LayoutResult#height()} so surface-based
     * renderers reserve enough space for ledger lines that sit below the
     * last staff line.
     */
    public static double contentBottom(List<SystemLayout> systems, List<NoteAnchor> anchors) {
        double bottom = 0.0;
        for (SystemLayout system : systems) {
            for (StaffLayout staff : system.staves()) {
                double gap = staff.lineGap();
                for (GlyphPlacement gp : staff.glyphs()) {
                    // Each glyph is drawn roughly a staff-line-gap tall,
                    // centred on its y anchor - add one gap of headroom to
                    // cover the notehead radius / rest body / clef ornament.
                    bottom = Math.max(bottom, gp.y() + gap);
                }
                for (StemPlacement sp : staff.stems()) {
                    bottom = Math.max(bottom, Math.max(sp.y1(), sp.y2()));
                }
                for (BeamPlacement bp : staff.beams()) {
                    bottom = Math.max(bottom, Math.max(bp.y1(), bp.y2()));
                }
            }
        }
        for (NoteAnchor a : anchors) {
            // Anchor.height is the bounding-box height including stems;
            // add half-extent below the notehead centre as a conservative
            // lower edge.
            bottom = Math.max(bottom, a.y() + a.height() / 2.0);
        }
        return bottom;
    }
}
