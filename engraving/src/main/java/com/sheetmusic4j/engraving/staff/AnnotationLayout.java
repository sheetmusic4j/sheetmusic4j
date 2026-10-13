package com.sheetmusic4j.engraving.staff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sheetmusic4j.core.model.Direction;
import com.sheetmusic4j.core.model.DirectionType;
import com.sheetmusic4j.core.model.Harmony;
import com.sheetmusic4j.core.model.Lyric;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Placement;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.engraving.placement.HairpinPlacement;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;
import com.sheetmusic4j.engraving.glyph.GlyphMapper;
import com.sheetmusic4j.engraving.text.LyricText;

/**
 * Markings attached to a staff rather than drawn as part of a note:
 * directions (words, tempo, dynamics, rehearsal marks, hairpins), chord
 * symbols and lyrics. One instance per staff row, since an open hairpin
 * can span measures.
 */
public final class AnnotationLayout {

    /**
     * Distance (in staff-line gaps) from the top staff line to the baseline
     * of a chord-symbol label. Chord symbols conventionally sit closer to the
     * staff than tempo/words directions so performers can read them alongside
     * the notes - smaller than {@link #DIRECTION_OFFSET_GAPS}, not larger.
     */
    private static final double HARMONY_OFFSET_GAPS = 1.2;

    /**
     * Multiplier applied to the staff-line gap to derive the chord-symbol
     * font size.
     */
    private static final double HARMONY_FONT_SIZE_GAPS = 1.6;

    /**
     * Distance (in staff-line gaps) from a staff to a direction's baseline
     * anchor. Words/metronome sit above; dynamics sit below.
     */
    private static final double DIRECTION_OFFSET_GAPS = 1.5;

    /**
     * Half the maximum opening height (in staff-line gaps) of a crescendo/
     * diminuendo hairpin.
     */
    private static final double HAIRPIN_HALF_HEIGHT_GAPS = 1.2;

    /**
     * Multiplier applied to the staff-line gap to derive the direction text
     * font size (words / metronome).
     */
    private static final double DIRECTION_FONT_SIZE_GAPS = 1.6;

    /**
     * Multiplier applied to the staff-line gap to derive the rehearsal-mark
     * font size. Slightly larger than words to stand out visually alongside
     * the box outline.
     */
    private static final double REHEARSAL_FONT_SIZE_GAPS = 1.8;

    /**
     * Distance (in staff-line gaps) from the bottom staff line to the baseline
     * of the verse-1 lyric row.
     */
    private static final double LYRIC_BASELINE_OFFSET_GAPS = 2.2;

    private final LayoutOptions options;
    private final double staffY;
    private final List<TextPlacement> texts;
    private final List<GlyphPlacement> glyphs;
    private final List<HairpinPlacement> hairpins = new ArrayList<>();
    private final Map<Integer, WedgeStart> wedgeCandidates = new HashMap<>();

    /**
     * @param options layout options
     * @param staffY  y of the staff's top line
     * @param texts   page-level text collector
     * @param glyphs  the staff's glyph collector (dynamics are glyphs)
     */
    public AnnotationLayout(LayoutOptions options, double staffY,
                     List<TextPlacement> texts, List<GlyphPlacement> glyphs) {
        this.options = options;
        this.staffY = staffY;
        this.texts = texts;
        this.glyphs = glyphs;
    }

    public List<HairpinPlacement> hairpins() {
        return hairpins;
    }

    /**
     * Emit direction placements at the given anchor x. Words / metronome go
     * above the staff (unless the placement attribute forces them below);
     * dynamics go below the staff (unless forced above). {@link
     * Placement#DEFAULT} follows the type-specific convention.
     *
     * @param measureTopY highest point any stem/beam reaches in this measure
     *                    (see {@link StemPlan#topY})
     */
    public void placeDirection(Direction direction, double x, double measureTopY) {
        if (!options.isVisible(ScoreAnalysis.categoryOf(direction.type()))) {
            return;
        }
        double gap = options.staffLineGap();
        Placement side = ScoreAnalysis.resolvedPlacement(direction);
        DirectionType type = direction.type();
        // Clearance above the highest stem/beam/tuplet number this measure
        // reaches, so an ABOVE-placed direction never sits under a beam that
        // extends well past the staff (e.g. for notes on several ledger
        // lines) - a fixed offset from the staff alone can't anticipate that.
        // Only kicks in when something actually reaches above the staff;
        // otherwise it must not perturb the normal fixed-offset placement.
        double aboveFloor = measureTopY < staffY
                ? measureTopY - gap * (SpanTracker.TUPLET_ABOVE_GAP + 1.5)
                : Double.POSITIVE_INFINITY;
        if (type instanceof DirectionType.Words words) {
            double fontSize = gap * DIRECTION_FONT_SIZE_GAPS;
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : Math.min(staffY - gap * DIRECTION_OFFSET_GAPS, aboveFloor);
            texts.add(new TextPlacement(words.text(), x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.DIRECTION));
        } else if (type instanceof DirectionType.Metronome metronome) {
            double fontSize = gap * DIRECTION_FONT_SIZE_GAPS;
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : Math.min(staffY - gap * DIRECTION_OFFSET_GAPS, aboveFloor);
            String text = metronomeText(metronome);
            texts.add(new TextPlacement(text, x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.TEMPO));
        } else if (type instanceof DirectionType.Dynamic dynamic) {
            Glyph glyph = GlyphMapper.dynamicGlyph(dynamic.mark());
            double y = side == Placement.ABOVE
                    ? Math.min(staffY - gap * DIRECTION_OFFSET_GAPS - gap, aboveFloor - gap)
                    : staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + gap;
            // Use a staff step well outside the staff so ledger-line hinting
            // never engages for the dynamic glyph.
            int staffStep = side == Placement.ABOVE ? -6 : 12;
            glyphs.add(new GlyphPlacement(x, y, glyph, staffStep, MarkingCategory.DYNAMIC));
        } else if (type instanceof DirectionType.Rehearsal rehearsal) {
            double fontSize = gap * REHEARSAL_FONT_SIZE_GAPS;
            // Rehearsal marks conventionally sit above the staff. If the
            // source explicitly forces BELOW we still honour that, but the
            // default from {@code resolvedPlacement} yields ABOVE.
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + fontSize
                    : Math.min(staffY - gap * DIRECTION_OFFSET_GAPS, aboveFloor) - fontSize;
            texts.add(new TextPlacement(rehearsal.label(), x, y, fontSize,
                    TextPlacement.Align.LEFT, MarkingCategory.REHEARSAL, true));
        } else if (type instanceof DirectionType.Wedge wedge) {
            double y = side == Placement.BELOW
                    ? staffY + options.staffHeight() + gap * DIRECTION_OFFSET_GAPS + gap
                    : staffY - gap * DIRECTION_OFFSET_GAPS - gap;
            if (wedge.type() == DirectionType.WedgeType.STOP) {
                WedgeStart start = wedgeCandidates.remove(wedge.number());
                if (start != null) {
                    hairpins.add(new HairpinPlacement(start.x(), x, start.y(),
                            gap * HAIRPIN_HALF_HEIGHT_GAPS, start.crescendo()));
                }
            } else {
                boolean crescendo = wedge.type() == DirectionType.WedgeType.CRESCENDO;
                wedgeCandidates.put(wedge.number(), new WedgeStart(x, y, crescendo));
            }
        }
    }

    /**
     * Emit the chord-symbol {@link TextPlacement} for a {@link Harmony} at
     * the given anchor x. Chord symbols sit above the staff, higher than
     * words/tempo directions so the two categories don't overlap when both
     * appear on the same measure.
     */
    public void placeHarmony(Harmony harmony, double x) {
        if (!options.isVisible(MarkingCategory.CHORD_SYMBOL)) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * HARMONY_FONT_SIZE_GAPS;
        double y = staffY - gap * HARMONY_OFFSET_GAPS;
        texts.add(new TextPlacement(harmony.displayLabel(), x, y, fontSize,
                TextPlacement.Align.LEFT, MarkingCategory.CHORD_SYMBOL, false));
    }

    /**
     * Emit lyric {@link TextPlacement}s for the given measure element. Only
     * verse-1 lyrics are rendered in the current MVP scope; verses &gt;= 2 are
     * captured in the model but not drawn.
     */
    public void placeLyrics(MusicElement element, double noteX) {
        Note note = ScoreAnalysis.lyricCarrier(element);
        if (note == null || note.lyrics().isEmpty() || !options.isVisible(MarkingCategory.LYRIC)) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * LyricText.FONT_SIZE_GAPS;
        double baselineY = staffY + options.staffHeight()
                + gap * LYRIC_BASELINE_OFFSET_GAPS + fontSize;
        for (Lyric lyric : note.lyrics()) {
            if (lyric.verse() != 1) {
                continue;
            }
            String text = lyric.text();
            if (text.isEmpty()) {
                continue;
            }
            String rendered = LyricText.of(lyric);
            texts.add(new TextPlacement(rendered, noteX, baselineY, fontSize,
                    TextPlacement.Align.CENTER, MarkingCategory.LYRIC));
        }
    }

    /**
     * Render a metronome mark as a plain unicode string, e.g. {@code ♩ = 60}.
     * A proper engraved metronome (SMuFL note glyph + digit glyphs) is a
     * follow-up.
     */
    private static String metronomeText(DirectionType.Metronome metronome) {
        String beatChar = switch (metronome.beatUnit()) {
            case WHOLE -> "𝅝";
            case HALF -> "♩";
            case QUARTER -> "♩";
            case EIGHTH -> "♪";
            case SIXTEENTH -> "♬";
            default -> "♩";
        };
        String dotSuffix = metronome.dotted() ? "." : "";
        return beatChar + dotSuffix + " = " + metronome.perMinute();
    }

    private record WedgeStart(double x, double y, boolean crescendo) {
    }
}
