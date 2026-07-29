package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Accidental;
import com.sheetmusic4j.core.model.Barline;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.*;
import com.sheetmusic4j.engraving.placement.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Surface-agnostic painting of a {@link LayoutResult}. All drawing goes through a
 * {@link RenderSurface}, so the identical logic can target a JavaFX canvas
 * (on screen) or an AWT image (headless tests / comparisons).
 *
 * <p>When a SMuFL font (Bravura) is available on the fxviewer classpath under
 * {@link SmuflGlyphs#BRAVURA_RESOURCE}, clef, notehead, rest and time-signature
 * glyphs are drawn via
 * {@link RenderSurface#drawSmuflGlyph(String, double, double, double)}. When
 * the font is missing, the painter falls back to primitive shapes so that
 * every downstream test still runs on a fresh checkout without any binary
 * asset.
 */
public final class ScorePainter {

    private static final int STAFF_LINES = 5;
    private static final double STEM_LENGTH_GAPS = 3.5;

    private final EnumSet<MarkingCategory> hiddenCategories =
            EnumSet.noneOf(MarkingCategory.class);

    private boolean bracketsVisible = true;

    /**
     * Optional per-element colour override. When set, the returned colour
     * (if any) is applied to every glyph/stem whose
     * {@link GlyphPlacement#elementRef()} equals the queried element,
     * covering notehead + stem + flag + accidental + augmentation dots as
     * one unit. {@code null} disables the mechanism.
     */
    private Function<MusicElement, Optional<RenderColor>> noteColorProvider;

    /**
     * Optional per-element background provider. When set, the returned
     * colour (if any) is drawn as a rounded, potentially semi-transparent
     * rectangle behind that element's notehead - covering the accidental
     * slot and augmentation dot column - before any staff or glyph content
     * is drawn. {@code null} disables the mechanism.
     */
    private Function<MusicElement, Optional<RenderColor>> noteBackgroundProvider;

    /**
     * Geometry of the note-background rectangle. Never {@code null}; defaults to
     * {@link NoteBackgroundStyle#defaults()}.
     */
    private NoteBackgroundStyle backgroundStyle = NoteBackgroundStyle.defaults();

    /**
     * Optional per-element accidental override provider. When set, the
     * returned {@link Accidental} (if any) is drawn to the left of that
     * element's notehead at the same {@code noteX - gap * 1.5} offset the
     * engraver uses for engraved accidentals. Any engraved accidental on
     * the same element is suppressed for as long as the override is
     * present. {@code null} disables the mechanism.
     */
    private Function<MusicElement, Optional<Accidental>> noteAccidentalProvider;

    /**
     * Creates a painter for rendering a layout onto any {@link RenderSurface}.
     */
    public ScorePainter() {
    }

    /**
     * Reference staff-line gap used to size layout-wide highlights. Uses
     * the first staff's gap when available; falls back to a sensible
     * constant when the layout carries no staves (e.g. an empty score).
     */
    private static double referenceLineGap(LayoutResult layout) {
        for (SystemLayout system : layout.systems()) {
            for (StaffLayout staff : system.staves()) {
                return staff.lineGap();
            }
        }
        return 10.0;
    }

    /**
     * SMuFL glyph draw centered on ({@code centerX}, {@code centerY}) by
     * shifting the left-edge origin by half the glyph's advance width.
     */
    private static boolean drawSmuflCentered(RenderSurface surface, Glyph glyph,
                                             double centerX, double centerY, double sizeHint) {
        String codepoint = SmuflGlyphs.codepoint(glyph);
        if (codepoint == null) {
            return false;
        }
        double halfW = SmuflGlyphs.halfAdvanceWidth(glyph, sizeHint);
        return surface.drawSmuflGlyph(codepoint, centerX - halfW, centerY, sizeHint);
    }

    private static boolean drawSmuflIfAvailable(RenderSurface surface, Glyph glyph, GlyphPlacement placement,
                                                double sizeHint) {
        String codepoint = SmuflGlyphs.codepoint(glyph);
        if (codepoint == null) {
            return false;
        }
        return surface.drawSmuflGlyph(codepoint, placement.x(), placement.y(), sizeHint);
    }

    private static void drawNoteheadPrimitive(RenderSurface surface, Glyph g, GlyphPlacement glyph,
                                              double headW, double headH) {
        switch (g) {
            case NOTEHEAD_BLACK -> surface.fillOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
            case NOTEHEAD_HALF, NOTEHEAD_WHOLE ->
                    surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
            case NOTEHEAD_BREVE -> {
                // A hollow rectangle with short vertical ticks at each side
                // (the "double whole note" convention), wider than a
                // regular whole note.
                double w = headW * 1.6;
                double tick = headH * 0.4;
                surface.strokeRect(glyph.x() - w / 2, glyph.y() - headH / 2, w, headH);
                surface.strokeLine(glyph.x() - w / 2, glyph.y() - headH / 2 - tick,
                        glyph.x() - w / 2, glyph.y() + headH / 2 + tick);
                surface.strokeLine(glyph.x() + w / 2, glyph.y() - headH / 2 - tick,
                        glyph.x() + w / 2, glyph.y() + headH / 2 + tick);
            }
            default -> {
            }
        }
    }

    private static String clefLetter(Glyph glyph) {
        return switch (glyph) {
            case CLEF_F -> "F";
            case CLEF_C -> "C";
            default -> "G";
        };
    }

    private static String dynamicFallback(Glyph glyph) {
        return switch (glyph) {
            case DYNAMIC_PPP -> "ppp";
            case DYNAMIC_PP -> "pp";
            case DYNAMIC_P -> "p";
            case DYNAMIC_MP -> "mp";
            case DYNAMIC_MF -> "mf";
            case DYNAMIC_F -> "f";
            case DYNAMIC_FF -> "ff";
            case DYNAMIC_FFF -> "fff";
            case DYNAMIC_SF -> "sf";
            case DYNAMIC_SFZ -> "sfz";
            case DYNAMIC_FZ -> "fz";
            case DYNAMIC_FP -> "fp";
            case DYNAMIC_RF -> "rf";
            case DYNAMIC_RFZ -> "rfz";
            case DYNAMIC_NIENTE -> "n";
            default -> "";
        };
    }

    private static Glyph accidentalToGlyph(Accidental accidental) {
        return switch (accidental) {
            case SHARP -> Glyph.ACCIDENTAL_SHARP;
            case FLAT -> Glyph.ACCIDENTAL_FLAT;
            case NATURAL -> Glyph.ACCIDENTAL_NATURAL;
            case DOUBLE_SHARP -> Glyph.ACCIDENTAL_DOUBLE_SHARP;
            case DOUBLE_FLAT -> Glyph.ACCIDENTAL_DOUBLE_FLAT;
        };
    }

    private static boolean isAccidentalGlyph(Glyph glyph) {
        return switch (glyph) {
            case ACCIDENTAL_SHARP, ACCIDENTAL_FLAT, ACCIDENTAL_NATURAL,
                 ACCIDENTAL_DOUBLE_SHARP, ACCIDENTAL_DOUBLE_FLAT -> true;
            default -> false;
        };
    }

    private static boolean isNoteheadGlyph(Glyph glyph) {
        return switch (glyph) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF, NOTEHEAD_WHOLE, NOTEHEAD_BREVE -> true;
            default -> false;
        };
    }

    private static String accidentalFallback(Glyph glyph) {
        return switch (glyph) {
            case ACCIDENTAL_SHARP, ACCIDENTAL_DOUBLE_SHARP -> "#";
            case ACCIDENTAL_FLAT, ACCIDENTAL_DOUBLE_FLAT -> "b";
            case ACCIDENTAL_NATURAL -> "n";
            default -> "";
        };
    }

    /**
     * Whether {@link BracketPlacement bracket placements} are drawn.
     */
    public boolean isBracketsVisible() {
        return bracketsVisible;
    }

    /**
     * Toggle drawing of all {@link BracketPlacement bracket placements}
     * (both implicit grand-staff braces and explicit {@code <part-group>}
     * brackets). When {@code false}, all brackets are skipped during
     * paint; the layout is unchanged so brackets snap back into place
     * whenever the flag is re-enabled.
     *
     * @param visible whether brackets should be drawn
     */
    public void setBracketsVisible(boolean visible) {
        this.bracketsVisible = visible;
    }

    /**
     * Install a per-element colour provider used to tint highlighted
     * notes. The provider is queried once per element that has a linked
     * {@link MusicElement} on its glyph placement; when it returns a
     * non-empty {@link Optional} the returned colour replaces the default
     * fill/stroke for that element's glyphs and stem.
     *
     * @param provider the provider, or {@code null} to disable highlighting
     */
    public void setNoteColorProvider(Function<MusicElement, Optional<RenderColor>> provider) {
        this.noteColorProvider = provider;
    }

    /**
     * Install a per-element background provider used to draw a rounded,
     * semi-transparent rectangle behind the target element's notehead
     * before any staff or glyph content is drawn. The provider is queried
     * once per {@link NoteAnchor} in the layout; when it returns a
     * non-empty {@link Optional} a rounded rectangle in that colour is
     * drawn behind the notehead (including the accidental slot).
     *
     * <p>Independent of {@link #setNoteColorProvider}: an element can
     * carry a tint, a background, both, or neither.
     *
     * @param provider the provider, or {@code null} to disable backgrounds
     */
    public void setNoteBackgroundProvider(Function<MusicElement, Optional<RenderColor>> provider) {
        this.noteBackgroundProvider = provider;
    }

    /**
     * @return the current note-background geometry (never {@code null}).
     */
    public NoteBackgroundStyle getNoteBackgroundStyle() {
        return backgroundStyle;
    }

    /**
     * Configure the geometry (paddings, corner arc, height cap) of the rounded
     * rectangle drawn behind highlighted notes. See {@link NoteBackgroundStyle}.
     *
     * @param style the new geometry, or {@code null} to restore the defaults
     */
    public void setNoteBackgroundStyle(NoteBackgroundStyle style) {
        this.backgroundStyle = style != null ? style : NoteBackgroundStyle.defaults();
    }

    /**
     * Install a per-element accidental override provider. When the
     * returned {@link Accidental} is present, its SMuFL glyph is drawn to
     * the immediate left of that element's notehead - at the same
     * {@code noteX - gap * 1.5} offset the engraver uses for engraved
     * accidentals - and any engraved accidental originally emitted for
     * that element is suppressed. {@code null} disables the mechanism.
     *
     * <p>Independent of {@link #setNoteColorProvider} and
     * {@link #setNoteBackgroundProvider}: an element can carry any
     * combination of tint, background, and accidental overlay. When a
     * tint is also active, the overlay accidental inherits it so the
     * overlay reads as part of the tinted note.
     *
     * @param provider the provider, or {@code null} to disable overlays
     */
    public void setNoteAccidentalProvider(Function<MusicElement, Optional<Accidental>> provider) {
        this.noteAccidentalProvider = provider;
    }

    /**
     * Look up the highlight colour for the given source element, if any.
     * Returns {@code null} when no provider is installed, no element is
     * associated, or the provider returned an empty optional.
     */
    private RenderColor colorFor(MusicElement element) {
        if (element == null || noteColorProvider == null) {
            return null;
        }
        Optional<RenderColor> c = noteColorProvider.apply(element);
        return c == null ? null : c.orElse(null);
    }

    /**
     * Look up the background colour for the given source element, if any.
     * Same semantics as {@link #colorFor}.
     */
    private RenderColor backgroundFor(MusicElement element) {
        if (element == null || noteBackgroundProvider == null) {
            return null;
        }
        Optional<RenderColor> c = noteBackgroundProvider.apply(element);
        return c == null ? null : c.orElse(null);
    }

    /**
     * Look up the accidental override for the given source element, if
     * any. Same semantics as {@link #colorFor}.
     */
    private Accidental accidentalOverrideFor(MusicElement element) {
        if (element == null || noteAccidentalProvider == null) {
            return null;
        }
        Optional<Accidental> a = noteAccidentalProvider.apply(element);
        return a == null ? null : a.orElse(null);
    }

    /**
     * Currently hidden categories (a defensive copy).
     */
    public Set<MarkingCategory> getHiddenCategories() {
        return hiddenCategories.isEmpty()
                ? EnumSet.noneOf(MarkingCategory.class)
                : EnumSet.copyOf(hiddenCategories);
    }

    /**
     * Replace the set of {@link MarkingCategory categories} that should be
     * skipped during painting. Hidden content still consumes vertical space
     * at the engraver — reclaiming that gap is a follow-up.
     *
     * @param categories categories to hide (never {@code null})
     */
    public void setHiddenCategories(Set<MarkingCategory> categories) {
        hiddenCategories.clear();
        if (categories != null && !categories.isEmpty()) {
            hiddenCategories.addAll(categories);
        }
    }

    /**
     * Draw a tie as a shallow curve approximated with two lines meeting at
     * the peak. Callers that need a real curve should override this via a
     * dedicated surface primitive; the two-segment approximation is enough
     * for the diagnostic comparator's window-level similarity check.
     */

    /**
     * Paints the given layout onto the provided surface.
     *
     * @param surface       surface abstraction to draw on
     * @param layout        engraved score layout to paint
     * @param surfaceWidth  available surface width
     * @param surfaceHeight available surface height
     */
    public void paint(RenderSurface surface, LayoutResult layout, double surfaceWidth, double surfaceHeight) {
        surface.setFill(RenderColor.WHITE);
        surface.fillRect(0, 0, Math.max(layout.width(), surfaceWidth), Math.max(layout.height(), surfaceHeight));
        surface.setStroke(RenderColor.BLACK);
        surface.setFill(RenderColor.BLACK);
        surface.setLineWidth(1.0);

        // Note-background highlights are drawn first so staff lines and
        // noteheads sit on top of them (they read as a highlight behind
        // the notehead, not a foreground overlay covering it).
        drawNoteBackgrounds(surface, layout);

        // Hidden text still consumes vertical space at the engraver —
        // reclaiming that gap is a follow-up task.
        for (TextPlacement text : layout.texts()) {
            if (hiddenCategories.contains(text.category())) {
                continue;
            }
            drawText(surface, text);
        }
        for (SystemLayout system : layout.systems()) {
            for (StaffLayout staff : system.staves()) {
                drawStaff(surface, staff);
            }
            for (SystemBarline barline : system.barlines()) {
                drawSystemBarline(surface, barline);
            }
            if (bracketsVisible) {
                double systemGap = system.staves().isEmpty() ? referenceLineGap(layout)
                        : system.staves().get(0).lineGap();
                for (BracketPlacement bracket : system.brackets()) {
                    drawBracket(surface, bracket, systemGap);
                }
            }
        }
    }

    /**
     * Whether the given category should be skipped by the current painter.
     * Package-private for tests.
     */
    boolean isHidden(MarkingCategory category) {
        return hiddenCategories.contains(category);
    }

    /**
     * Draw a rounded, semi-transparent rectangle behind every element that
     * carries a background colour. Iterates {@link LayoutResult#noteAnchors()}
     * once; anchors whose element resolves to {@code null} in the
     * background provider are skipped. No-op when no background provider
     * is installed.
     *
     * <p>The rectangle is sized to cover the notehead plus a wider slot on
     * the left (for the accidental) and a narrower one on the right (for
     * augmentation dots). Padding scales with the layout's staff-line gap
     * so backgrounds keep their proportion at any zoom level.
     */
    private void drawNoteBackgrounds(RenderSurface surface, LayoutResult layout) {
        if (noteBackgroundProvider == null) {
            return;
        }
        double gap = referenceLineGap(layout);
        double padLeft = gap * backgroundStyle.padLeftGaps();
        double padRight = gap * backgroundStyle.padRightGaps();
        double padTop = gap * backgroundStyle.padTopGaps();
        double padBottom = gap * backgroundStyle.padBottomGaps();
        double arc = gap * backgroundStyle.arcGaps();
        double offsetY = gap * backgroundStyle.offsetYGaps();
        // Cap the note-derived content height so a chord anchor whose bounding
        // box also includes its long stem doesn't produce a hugely tall rect.
        double maxContentHeight = gap * backgroundStyle.maxHeightGaps();
        for (NoteAnchor anchor : layout.noteAnchors()) {
            RenderColor bg = backgroundFor(anchor.elementRef());
            if (bg == null) {
                continue;
            }
            // The box is anchored to the note's rendered size and position and
            // grows outward by the configured padding on each side.
            double contentHeight = Math.min(anchor.height(), maxContentHeight);
            double rectWidth = anchor.width() + padLeft + padRight;
            double rectHeight = contentHeight + padTop + padBottom;
            double rectX = anchor.x() - anchor.width() / 2.0 - padLeft;
            double rectY = anchor.y() - contentHeight / 2.0 - padTop + offsetY;
            surface.fillRoundedRect(rectX, rectY, rectWidth, rectHeight, arc, arc, bg);
        }
    }

    /**
     * Draw a page-level {@link TextPlacement}. Alignment is approximated by
     * subtracting an estimated width (0.55 * fontSize per character) from the
     * anchor x. Backends that support real text metrics can override the
     * surface to do this more accurately.
     */
    private void drawText(RenderSurface surface, TextPlacement text) {
        double estimatedWidth = 0.55 * text.fontSize() * Math.max(1, text.text().length());
        double x = switch (text.align()) {
            case LEFT -> text.x();
            case CENTER -> text.x() - estimatedWidth / 2.0;
            case RIGHT -> text.x() - estimatedWidth;
        };
        surface.drawText(text.text(), x, text.y(), text.fontSize());
        if (text.boxed()) {
            double padding = text.fontSize() * 0.2;
            double boxHeight = text.fontSize() * 1.2;
            double boxX = x - padding;
            // Text is drawn with its baseline at text.y(); the visual bounding
            // box extends roughly from (baseline - fontSize) to baseline.
            double boxY = text.y() - text.fontSize() - padding;
            double boxW = estimatedWidth + 2 * padding;
            double boxH = boxHeight + 2 * padding;
            surface.strokeRect(boxX, boxY, boxW, boxH);
        }
    }

    private void drawStaff(RenderSurface surface, StaffLayout staff) {
        for (int line = 0; line < STAFF_LINES; line++) {
            double y = staff.lineY(line);
            surface.strokeLine(staff.x(), y, staff.x() + staff.width(), y);
        }

        for (MeasureLayout measure : staff.measures()) {
            if (measure.leadingRepeatStart()) {
                drawBarline(surface, staff, measure.x(), new Barline(Barline.Style.REGULAR, Barline.Repeat.FORWARD));
            }
            drawBarline(surface, staff, measure.right(), measure.barline());
        }
        drawEndingBrackets(surface, staff);

        for (GlyphPlacement glyph : staff.glyphs()) {
            if (hiddenCategories.contains(glyph.category())) {
                continue;
            }
            MusicElement element = glyph.elementRef();
            if (isAccidentalGlyph(glyph.glyph()) && accidentalOverrideFor(element) != null) {
                // An override is active for this element - suppress the
                // engraved accidental, the overlay drawn alongside the
                // notehead below replaces it.
                continue;
            }
            drawGlyph(surface, staff, glyph);
            if (isNoteheadGlyph(glyph.glyph())) {
                Accidental overlay = accidentalOverrideFor(element);
                if (overlay != null) {
                    drawOverlayAccidental(surface, staff, glyph, overlay);
                }
            }
        }

        for (BeamPlacement beam : staff.beams()) {
            drawBeam(surface, staff, beam);
        }
        for (TiePlacement tie : staff.ties()) {
            drawTie(surface, staff, tie);
        }
        for (SlurPlacement slur : staff.slurs()) {
            drawSlur(surface, staff, slur);
        }
        for (TupletPlacement tuplet : staff.tuplets()) {
            drawTuplet(surface, staff, tuplet);
        }
        for (HairpinPlacement hairpin : staff.hairpins()) {
            drawHairpin(surface, hairpin);
        }
        for (GraceNotePlacement grace : staff.graceNotes()) {
            drawGraceNotes(surface, staff, grace);
        }
        for (StemPlacement stem : staff.stems()) {
            drawStem(surface, stem);
        }
    }

    /**
     * Draw a note stem as a straight line between its precomputed
     * endpoints. Unlike a fixed-length primitive, the engraver has already
     * lengthened this as needed to reach a shared beam or clear the staff,
     * so no further adjustment happens here.
     */
    private void drawStem(RenderSurface surface, StemPlacement stem) {
        // Slightly thicker than a hairline so the join with a notehead the
        // stem merely passes through (not its own start point) reads as
        // solidly connected rather than a fragile single-point tangent.
        RenderColor override = colorFor(stem.elementRef());
        if (override != null) {
            surface.setStroke(override);
        }
        surface.setLineWidth(1.4);
        surface.strokeLine(stem.x(), stem.y1(), stem.x(), stem.y2());
        surface.setLineWidth(1.0);
        if (override != null) {
            surface.setStroke(RenderColor.BLACK);
        }
    }

    private void drawGlyph(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double headW = gap * 1.2;
        double headH = gap * 0.9;
        double sizeHint = gap * 4;
        Glyph g = glyph.glyph();
        RenderColor override = colorFor(glyph.elementRef());
        if (override != null) {
            surface.setStroke(override);
            surface.setFill(override);
        }
        try {
            drawGlyphInner(surface, staff, glyph, gap, headW, headH, sizeHint, g);
        } finally {
            if (override != null) {
                surface.setStroke(RenderColor.BLACK);
                surface.setFill(RenderColor.BLACK);
            }
        }
    }

    private void drawGlyphInner(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph,
                                double gap, double headW, double headH, double sizeHint, Glyph g) {
        switch (g) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF, NOTEHEAD_WHOLE, NOTEHEAD_BREVE -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    drawNoteheadPrimitive(surface, g, glyph, headW, headH);
                }
                drawLedgerLines(surface, staff, glyph);
            }
            case STEM_UP -> {
                double sx = glyph.x() + headW / 2;
                surface.strokeLine(sx, glyph.y(), sx, glyph.y() - gap * STEM_LENGTH_GAPS);
            }
            case STEM_DOWN -> {
                double sx = glyph.x() - headW / 2;
                surface.strokeLine(sx, glyph.y(), sx, glyph.y() + gap * STEM_LENGTH_GAPS);
            }
            case FLAG_8TH_UP, FLAG_8TH_DOWN, FLAG_16TH_UP, FLAG_16TH_DOWN,
                 FLAG_32ND_UP, FLAG_32ND_DOWN, FLAG_64TH_UP, FLAG_64TH_DOWN,
                 FLAG_128TH_UP, FLAG_128TH_DOWN -> {
                // Flags rely on the SMuFL font; when absent we draw nothing
                // (a missing flag is preferable to an incorrect primitive).
                drawSmuflIfAvailable(surface, g, glyph, sizeHint);
            }
            case ACCIDENTAL_SHARP, ACCIDENTAL_FLAT, ACCIDENTAL_NATURAL,
                 ACCIDENTAL_DOUBLE_SHARP, ACCIDENTAL_DOUBLE_FLAT -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    surface.strokeText(accidentalFallback(g), glyph.x(), glyph.y() + gap * 0.4);
                }
            }
            case AUG_DOT -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    double d = gap * 0.4;
                    surface.fillOval(glyph.x() - d / 2, glyph.y() - d / 2, d, d);
                }
            }
            case ARTICULATION_STACCATO -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    double d = gap * 0.35;
                    surface.fillOval(glyph.x() - d / 2, glyph.y() - d / 2, d, d);
                }
            }
            case ARTICULATION_ACCENT -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    surface.strokeText(">", glyph.x(), glyph.y() + gap * 0.4);
                }
            }
            case ARTICULATION_DOWN_BOW -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    surface.strokeText("⊓", glyph.x(), glyph.y() + gap * 0.4);
                }
            }
            case ARTICULATION_UP_BOW -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    surface.strokeText("V", glyph.x(), glyph.y() + gap * 0.4);
                }
            }
            case ARTICULATION_ROLL -> surface.strokeText("~", glyph.x(), glyph.y() + gap * 0.4);
            case DYNAMIC_PPP, DYNAMIC_PP, DYNAMIC_P, DYNAMIC_MP, DYNAMIC_MF,
                 DYNAMIC_F, DYNAMIC_FF, DYNAMIC_FFF, DYNAMIC_SF, DYNAMIC_SFZ,
                 DYNAMIC_FZ, DYNAMIC_FP, DYNAMIC_RF, DYNAMIC_RFZ, DYNAMIC_NIENTE -> {
                if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), sizeHint)) {
                    surface.strokeText(dynamicFallback(g), glyph.x(), glyph.y() + gap * 0.4);
                }
            }
            case CLEF_G, CLEF_F, CLEF_C -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawClefFallback(surface, staff, glyph, clefLetter(g));
                }
            }
            case REST_WHOLE -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawWholeRest(surface, staff, glyph);
                }
            }
            case REST_HALF -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawHalfRest(surface, staff, glyph);
                }
            }
            case REST_QUARTER -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawQuarterRest(surface, staff, glyph);
                }
            }
            case REST_EIGHTH -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawFlaggedRestFallback(surface, staff, glyph, 1);
                }
            }
            case REST_SIXTEENTH -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawFlaggedRestFallback(surface, staff, glyph, 2);
                }
            }
            case REST_THIRTY_SECOND -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawFlaggedRestFallback(surface, staff, glyph, 3);
                }
            }
            case REST_SIXTY_FOURTH -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawFlaggedRestFallback(surface, staff, glyph, 4);
                }
            }
            case REST_128TH -> {
                if (!drawSmuflIfAvailable(surface, g, glyph, sizeHint)) {
                    drawFlaggedRestFallback(surface, staff, glyph, 5);
                }
            }
            default -> {
                if (g.timeDigitChar() != null) {
                    if (!drawSmuflCentered(surface, g, glyph.x(), glyph.y(), gap * 4)) {
                        surface.strokeText(g.timeDigitChar().toString(), glyph.x(), glyph.y());
                    }
                }
                // STAFF_LINE / LEDGER_LINE / legacy STEM / BEAM handled elsewhere.
            }
        }
    }

    /**
     * Draw a beam segment as a thick rectangle (axis-aligned MVP; a full
     * implementation would use a rotated polygon). Multi-level beams are
     * stacked below (for stem-up groups) / above (stem-down groups) the
     * primary beam.
     */
    private void drawBeam(RenderSurface surface, StaffLayout staff, BeamPlacement beam) {
        double gap = staff.lineGap();
        double thickness = gap * 0.5;
        // Level 1 is the primary beam, aligned exactly at the stem tips.
        // Higher levels stack toward the notehead.
        double offset = (beam.level() - 1) * gap * 0.75;
        double dy = beam.stemUp() ? offset : -offset;
        double y = ((beam.y1() + beam.y2()) / 2.0) + dy;
        double x1 = Math.min(beam.x1(), beam.x2());
        double x2 = Math.max(beam.x1(), beam.x2());
        surface.fillRect(x1, y - thickness / 2, x2 - x1, thickness);
    }

    /**
     * Strokes a smooth rounded arc from ({@code x1},{@code y1}) to
     * ({@code x2},{@code y2}) whose apex reaches {@code peakY}, using a
     * cubic curve with two inset control points so the shape stays an
     * evenly rounded dome regardless of how deep the bend is relative to
     * the horizontal span. A single-control-point quadratic curve gets
     * visibly more "pointed" as that ratio grows (e.g. a slur clearing a
     * melodic peak far above its two endpoints) - two independent control
     * points avoid that.
     */
    private void strokeArc(RenderSurface surface, double x1, double y1, double x2, double y2, double peakY) {
        double avgY = (y1 + y2) / 2.0;
        // A cubic curve with both control points at the same y reaches 75%
        // of the way from the endpoint average to that y at its own apex
        // (t = 0.5); solve for the control y that lands the rendered apex
        // exactly at peakY.
        double controlY = (4 * peakY - avgY) / 3.0;
        double c1x = x1 + (x2 - x1) * 0.25;
        double c2x = x1 + (x2 - x1) * 0.75;
        surface.strokeCubicCurve(x1, y1, c1x, controlY, c2x, controlY, x2, y2);
    }

    private void drawTie(RenderSurface surface, StaffLayout staff, TiePlacement tie) {
        double gap = staff.lineGap();
        double bend = gap * 0.6 * (tie.curveUp() ? -1 : 1);
        double avgY = (tie.y1() + tie.y2()) / 2.0;
        strokeArc(surface, tie.x1(), tie.y1(), tie.x2(), tie.y2(), avgY + bend);
    }

    /**
     * Draw a slur as a rounded arc (a real cubic curve, not two straight
     * segments meeting at a point - that reads as an angular "^"/"V" rather
     * than a slur). Slurs typically span more horizontal distance than
     * ties, so the bend is proportional to the span rather than a flat
     * multiple of the staff-line gap.
     */
    private void drawSlur(RenderSurface surface, StaffLayout staff, SlurPlacement slur) {
        double span = Math.abs(slur.x2() - slur.x1());
        double gap = staff.lineGap();
        double avgY = (slur.y1() + slur.y2()) / 2.0;
        // A slur commonly arcs over/under several notes between its two
        // endpoints, not just the endpoints themselves - e.g. a phrase that
        // arches up to a peak and back down to roughly its starting pitch.
        // A bend scaled only to the horizontal span (as ties use) can be far
        // too shallow to clear that peak, so take whichever is more extreme
        // of the default shallow-arc bend and the clearance actually needed
        // to pass every notehead the slur spans (tracked in clearY).
        double clearance = gap * 0.8;
        double midY;
        if (slur.curveUp()) {
            // Anchored to the leading (start) note's own height, not the
            // average with the end note - a slur curving above shouldn't
            // have its default depth pulled around by wherever the phrase
            // happens to land, which can be a very different pitch.
            double defaultPeak = slur.y1() - Math.max(gap * 1.3, span * 0.12);
            midY = Math.min(defaultPeak, slur.clearY() - clearance);
        } else {
            double defaultPeak = avgY + Math.max(gap * 1.3, span * 0.12);
            midY = Math.max(defaultPeak, slur.clearY() + clearance);
        }
        strokeArc(surface, slur.x1(), slur.y1(), slur.x2(), slur.y2(), midY);
    }

    /**
     * Draw a tuplet indicator: the displayed count (composed from
     * {@link Glyph#timeDigit(int)} digit glyphs, matching how the time
     * signature renders its digits) centered over the run, plus a plain
     * bracket with a small downward tick at each end when
     * {@link TupletPlacement#bracket()} is set.
     */
    private void drawTuplet(RenderSurface surface, StaffLayout staff, TupletPlacement tuplet) {
        double gap = staff.lineGap();
        // Tuplet numbers are drawn much smaller than time-signature digits
        // (which use a full-staff-height sizeHint of gap*4) - roughly the
        // same scale as ordinary expression text.
        double sizeHint = gap * 1.6;
        double digitWidth = sizeHint * 0.4;
        String digits = Integer.toString(tuplet.number());
        double midX = (tuplet.x1() + tuplet.x2()) / 2.0;
        double textWidth = digits.length() * digitWidth;
        double startX = midX - textWidth / 2.0;
        if (tuplet.bracket()) {
            double tick = gap * 0.5;
            surface.strokeLine(tuplet.x1(), tuplet.y() + tick, tuplet.x1(), tuplet.y());
            surface.strokeLine(tuplet.x1(), tuplet.y(), startX - gap * 0.3, tuplet.y());
            surface.strokeLine(startX + textWidth + gap * 0.3, tuplet.y(), tuplet.x2(), tuplet.y());
            surface.strokeLine(tuplet.x2(), tuplet.y(), tuplet.x2(), tuplet.y() + tick);
        }
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            Glyph glyph = Glyph.timeDigit(digit);
            double x = startX + i * digitWidth;
            if (!drawSmuflIfAvailable(surface, glyph, new GlyphPlacement(x, tuplet.y(), glyph, 0), sizeHint)) {
                surface.strokeText(digits.substring(i, i + 1), x, tuplet.y());
            }
        }
    }

    /**
     * Draw a crescendo/diminuendo hairpin as two diverging (or converging)
     * lines meeting at the closed end.
     */
    private void drawHairpin(RenderSurface surface, HairpinPlacement hairpin) {
        double closedX = hairpin.crescendo() ? hairpin.x1() : hairpin.x2();
        double openX = hairpin.crescendo() ? hairpin.x2() : hairpin.x1();
        surface.strokeLine(closedX, hairpin.y(), openX, hairpin.y() - hairpin.halfHeight());
        surface.strokeLine(closedX, hairpin.y(), openX, hairpin.y() + hairpin.halfHeight());
    }

    /**
     * Draw a grace-note run: small noteheads with stems reaching a shared
     * beam line that is raked (sloped) to follow the run's pitch contour
     * (a lone grace note just gets its own short stem), an acciaccatura
     * slash across the beam (or the stem, when there is only one), and a
     * curved line connecting the last grace note to the main note it leads
     * into.
     */
    private void drawGraceNotes(RenderSurface surface, StaffLayout staff, GraceNotePlacement grace) {
        double gap = staff.lineGap();
        double headW = gap * 0.78;
        double headH = gap * 0.58;
        List<Double> xs = grace.noteX();
        List<Double> ys = grace.noteY();
        List<Double> stemTops = grace.stemTopY();
        int count = xs.size();
        for (int i = 0; i < count; i++) {
            double x = xs.get(i);
            double y = ys.get(i);
            surface.fillOval(x - headW / 2, y - headH / 2, headW, headH);
            surface.strokeLine(x + headW / 2, y, x + headW / 2, stemTops.get(i));
        }
        double slashHalf = gap * 0.5;
        double firstStemX = xs.get(0) + headW / 2;
        double firstStemTop = stemTops.get(0);
        if (count > 1) {
            surface.strokeLine(firstStemX, firstStemTop,
                    xs.get(count - 1) + headW / 2, stemTops.get(count - 1));
            surface.strokeLine(firstStemX - slashHalf * 0.5, firstStemTop + slashHalf,
                    firstStemX + slashHalf * 0.5, firstStemTop - slashHalf);
        } else {
            double slashY = (ys.get(0) + firstStemTop) / 2.0;
            surface.strokeLine(firstStemX - slashHalf * 0.6, slashY + slashHalf,
                    firstStemX + slashHalf * 0.6, slashY - slashHalf);
        }
        double lastX = xs.get(count - 1);
        double lastY = ys.get(count - 1);
        double curveMidX = (lastX + grace.mainNoteX()) / 2.0;
        double curveY = Math.min(lastY, grace.mainNoteY()) - gap * 1.2;
        surface.strokeQuadCurve(lastX, lastY, curveMidX, curveY, grace.mainNoteX(), grace.mainNoteY());
    }

    /**
     * Draw an overlay accidental for the given notehead placement. The
     * glyph is positioned at {@code (notehead.x() - gap * 1.5,
     * notehead.y())} - matching the engraver's own accidental placement
     * byte-for-byte - so callers get identical geometry whether the
     * accidental comes from the model or from the overlay map. When the
     * source note carries a colour tint, the overlay accidental is drawn
     * with the same tint since we go through the standard
     * {@link #drawGlyph} path.
     */
    private void drawOverlayAccidental(RenderSurface surface, StaffLayout staff,
                                       GlyphPlacement notehead, Accidental accidental) {
        double gap = staff.lineGap();
        double x = notehead.x() - gap * 1.5;
        double y = notehead.y();
        Glyph accGlyph = accidentalToGlyph(accidental);
        GlyphPlacement synthetic = new GlyphPlacement(
                x, y, accGlyph, notehead.staffStep(), MarkingCategory.NOTE, notehead.elementRef());
        drawGlyph(surface, staff, synthetic);
    }

    /**
     * Non-SMuFL clef fallback: just emit the clef letter, anchored on the
     * correct line. Deliberately simple - the plumbing above uses Bravura
     * whenever it is committed to the fxviewer classpath.
     */
    private void drawClefFallback(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph, String letter) {
        double gap = staff.lineGap();
        surface.strokeText(letter, glyph.x(), glyph.y() + gap * 0.5);
    }

    private void drawWholeRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double w = gap * 1.2;
        double h = gap * 0.5;
        double y = staff.lineY(1);
        surface.fillRect(glyph.x() - w / 2, y, w, h);
    }

    private void drawHalfRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double w = gap * 1.2;
        double h = gap * 0.5;
        double y = staff.lineY(2) - h;
        surface.fillRect(glyph.x() - w / 2, y, w, h);
    }

    private void drawQuarterRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double x = glyph.x();
        double top = staff.lineY(1);
        double bottom = staff.lineY(3);
        double half = gap * 0.5;
        surface.strokeLine(x - half, top, x + half, top + gap);
        surface.strokeLine(x + half, top + gap, x - half, top + 2 * gap);
        surface.strokeLine(x - half, top + 2 * gap, x + half, bottom);
    }

    /**
     * Primitive fallback for eighth-and-shorter rests: {@code flagCount}
     * stacked flag blobs (1 for an eighth rest, 2 for a sixteenth, ...)
     * joined by a diagonal stroke, approximating the real SMuFL glyph's
     * zigzag shape closely enough to be unambiguous when Bravura is
     * unavailable.
     */
    private void drawFlaggedRestFallback(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph,
                                         int flagCount) {
        double gap = staff.lineGap();
        double d = gap * 0.6;
        double x = glyph.x();
        double topY = staff.lineY(2) - (flagCount - 1) * gap * 0.7;
        for (int i = 0; i < flagCount; i++) {
            double y = topY + i * gap * 0.7;
            surface.fillOval(x - d / 2, y - d / 2, d, d);
            surface.strokeLine(x + d / 2, y, x - d / 2, y + gap * 1.5);
        }
    }

    /**
     * Draw a single per-measure barline at {@code x}: a plain thin line for
     * {@code null}/{@link Barline.Style#REGULAR}, two close thin lines for
     * {@link Barline.Style#DOUBLE}, thin+thick for {@link Barline.Style#FINAL},
     * and the conventional dotted variants when {@link Barline#repeat()} is
     * set (dots sit on the side the repeated section is on).
     */
    private void drawBarline(RenderSurface surface, StaffLayout staff, double x, Barline barline) {
        double gap = staff.lineGap();
        double top = staff.lineY(0);
        double bottom = staff.lineY(STAFF_LINES - 1);
        double lineSpacing = gap * 0.35;
        double dotOffset = lineSpacing + gap * 0.65;

        Barline.Repeat repeat = barline == null ? Barline.Repeat.NONE : barline.repeat();
        Barline.Style style = barline == null ? Barline.Style.REGULAR : barline.style();
        switch (repeat) {
            case BACKWARD -> {
                drawRepeatDots(surface, staff, x - dotOffset);
                surface.strokeLine(x - lineSpacing, top, x - lineSpacing, bottom);
                drawThickLine(surface, x, top, bottom);
            }
            case FORWARD -> {
                drawThickLine(surface, x, top, bottom);
                surface.strokeLine(x + lineSpacing, top, x + lineSpacing, bottom);
                drawRepeatDots(surface, staff, x + dotOffset);
            }
            case BOTH -> {
                drawRepeatDots(surface, staff, x - dotOffset);
                surface.strokeLine(x - lineSpacing, top, x - lineSpacing, bottom);
                surface.strokeLine(x + lineSpacing, top, x + lineSpacing, bottom);
                drawRepeatDots(surface, staff, x + dotOffset);
            }
            case NONE -> {
                switch (style) {
                    case DOUBLE -> {
                        surface.strokeLine(x - lineSpacing, top, x - lineSpacing, bottom);
                        surface.strokeLine(x, top, x, bottom);
                    }
                    case FINAL -> {
                        surface.strokeLine(x - lineSpacing, top, x - lineSpacing, bottom);
                        drawThickLine(surface, x, top, bottom);
                    }
                    case REGULAR -> surface.strokeLine(x, top, x, bottom);
                }
            }
        }
    }

    private void drawThickLine(RenderSurface surface, double x, double top, double bottom) {
        surface.setLineWidth(2.5);
        surface.strokeLine(x, top, x, bottom);
        surface.setLineWidth(1.0);
    }

    /** Two repeat dots, vertically centered in the staff's middle two spaces. */
    private void drawRepeatDots(RenderSurface surface, StaffLayout staff, double x) {
        double gap = staff.lineGap();
        double radius = gap * 0.16;
        double midY = (staff.lineY(0) + staff.lineY(STAFF_LINES - 1)) / 2.0;
        surface.fillOval(x - radius, midY - gap * 0.5 - radius, radius * 2, radius * 2);
        surface.fillOval(x - radius, midY + gap * 0.5 - radius, radius * 2, radius * 2);
    }

    /**
     * Draw a first/second-ending bracket over each contiguous run of
     * measures sharing the same non-null {@link MeasureLayout#ending()}
     * label: a horizontal line with a short downward tick at each end and
     * the label centered above it.
     */
    private void drawEndingBrackets(RenderSurface surface, StaffLayout staff) {
        double gap = staff.lineGap();
        double y = staff.lineY(0) - gap * 2.2;
        double tick = gap * 0.6;
        List<MeasureLayout> measures = staff.measures();
        int i = 0;
        while (i < measures.size()) {
            String label = measures.get(i).ending();
            if (label == null) {
                i++;
                continue;
            }
            int start = i;
            while (i < measures.size() && label.equals(measures.get(i).ending())) {
                i++;
            }
            double x1 = measures.get(start).x();
            double x2 = measures.get(i - 1).right();
            surface.strokeLine(x1, y, x1, y + tick);
            surface.strokeLine(x1, y, x2, y);
            surface.strokeLine(x2, y, x2, y + tick);
            surface.strokeText(label, x1 + gap * 0.5, y - gap * 0.3);
        }
    }

    /**
     * Draw a system-wide vertical barline at the given x, spanning the
     * top-line of the first staff to the bottom-line of the last staff of
     * the enclosing system. Style is honored by picking a heavier line
     * width for {@link SystemBarline.LineStyle#THICK}.
     */
    private void drawSystemBarline(RenderSurface surface, SystemBarline barline) {
        if (barline.style() == SystemBarline.LineStyle.THICK) {
            surface.setLineWidth(2.5);
            surface.strokeLine(barline.x(), barline.topY(), barline.x(), barline.bottomY());
            surface.setLineWidth(1.0);
        } else {
            surface.strokeLine(barline.x(), barline.topY(), barline.x(), barline.bottomY());
        }
    }

    /**
     * Draw a grouping mark (brace / bracket / square bracket / line) at
     * the left edge of a system.
     * <ul>
     *   <li>{@link BracketPlacement.BracketShape#BRACE} — SMuFL {@code brace}
     *       glyph when available, otherwise a primitive vertical line +
     *       serif fallback.</li>
     *   <li>{@link BracketPlacement.BracketShape#BRACKET} — thick vertical
     *       stroke plus SMuFL {@code bracketTop} / {@code bracketBottom}
     *       ornamental tips (falling back to short square serifs when
     *       Bravura is missing).</li>
     *   <li>{@link BracketPlacement.BracketShape#SQUARE} — thick vertical
     *       stroke with plain square serifs at each end (no ornamental
     *       tips).</li>
     *   <li>{@link BracketPlacement.BracketShape#LINE} — a single thin
     *       vertical line, no serifs.</li>
     * </ul>
     */
    private void drawBracket(RenderSurface surface, BracketPlacement bracket, double gap) {
        double span = bracket.bottomY() - bracket.topY();
        switch (bracket.shape()) {
            case BRACE -> {
                // SMuFL's brace (E000) is drawn with the AWT/FX text baseline
                // convention: at font size = span, Bravura's brace glyph ink
                // runs from the baseline (y offset 0, its bottom tip) up to
                // very nearly the full font size above it (its top tip) -
                // verified via GlyphVector#getVisualBounds() (top ~ -0.997 *
                // fontSize, bottom ~ 0). So anchoring the baseline at
                // bracket.bottomY() makes the glyph span almost exactly
                // [topY, bottomY] with no extra offset needed. Unlike the
                // bracket's ornamental tips below, the brace glyph is
                // *meant* to stretch across the whole span - that's the
                // one piece of this drawing that legitimately scales with
                // however many staves the group covers.
                boolean drawn = surface.drawSmuflGlyph("\uE000",
                        bracket.x() - 5, bracket.bottomY(), span);
                if (!drawn) {
                    drawBraceFallback(surface, bracket, gap);
                }
            }
            case BRACKET -> drawBracketWithOrnaments(surface, bracket, gap);
            case SQUARE -> drawSquareBracket(surface, bracket, gap);
            case LINE -> drawBracketLineFallback(surface, bracket);
        }
    }

    /**
     * Primitive brace fallback: a vertical line joined by two short
     * horizontal serifs at each end. Uglier than a real brace but
     * unambiguously signals "these staves are grouped".
     */
    private void drawBraceFallback(RenderSurface surface, BracketPlacement bracket, double gap) {
        double x = bracket.x();
        surface.strokeLine(x, bracket.topY(), x, bracket.bottomY());
        double serif = gap * 0.65;
        surface.strokeLine(x, bracket.topY(), x + serif, bracket.topY());
        surface.strokeLine(x, bracket.bottomY(), x + serif, bracket.bottomY());
    }

    /**
     * Draw a canonical orchestral bracket: a thick vertical line with a
     * small overshoot at each end plus the SMuFL {@code bracketTop} /
     * {@code bracketBottom} ornamental tips. When Bravura is unavailable,
     * falls back to short horizontal serifs so the shape still reads as a
     * bracket rather than a plain vertical line.
     */
    private void drawBracketWithOrnaments(RenderSurface surface, BracketPlacement bracket, double gap) {
        double x = bracket.x();
        // Overshoot each end by 0.4 of a staff-line gap so the bracket
        // visually "caps" the outermost staff lines.
        double overshoot = gap * 0.4;
        double thickness = gap * 0.4;
        double topExtended = bracket.topY() - overshoot;
        double bottomExtended = bracket.bottomY() + overshoot;
        surface.fillRect(x - thickness / 2.0, topExtended, thickness,
                bottomExtended - topExtended);
        // The ornamental tips are small fixed-size caps at each end, not a
        // single glyph stretched across the whole bracket like the brace
        // is (see drawBracket's BRACE case) - scale by the actual staff
        // gap, not the bracket's own span, or a bracket spanning many
        // staves draws a wildly oversized tip.
        double tipSize = gap * 4.0;
        boolean topDrawn = surface.drawSmuflGlyph("\uE003", x, bracket.topY(), tipSize);
        boolean bottomDrawn = surface.drawSmuflGlyph("\uE004", x, bracket.bottomY(), tipSize);
        if (!topDrawn || !bottomDrawn) {
            double serif = gap * 0.9;
            if (!topDrawn) {
                surface.strokeLine(x, topExtended, x + serif, topExtended);
            }
            if (!bottomDrawn) {
                surface.strokeLine(x, bottomExtended, x + serif, bottomExtended);
            }
        }
    }

    /**
     * Draw a plain rectangular "square" bracket: same thick vertical
     * stroke as {@link #drawBracketWithOrnaments} but with unadorned
     * horizontal serifs at each end instead of SMuFL ornamental tips.
     */
    private void drawSquareBracket(RenderSurface surface, BracketPlacement bracket, double gap) {
        double x = bracket.x();
        double thickness = gap * 0.4;
        double serif = gap * 0.9;
        surface.fillRect(x - thickness / 2.0, bracket.topY(), thickness,
                bracket.bottomY() - bracket.topY());
        surface.strokeLine(x, bracket.topY(), x + serif, bracket.topY());
        surface.strokeLine(x, bracket.bottomY(), x + serif, bracket.bottomY());
    }

    /**
     * Draw a thin single-line grouping stroke for
     * {@link BracketPlacement.BracketShape#LINE}.
     */
    private void drawBracketLineFallback(RenderSurface surface, BracketPlacement bracket) {
        surface.strokeLine(bracket.x(), bracket.topY(), bracket.x(), bracket.bottomY());
    }

    private void drawLedgerLines(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        int staffStep = glyph.staffStep();
        double ledgerHalfWidth = gap * 0.9;
        for (int s = -2; s >= staffStep; s -= 2) {
            double y = staff.y() + s * (gap / 2.0);
            surface.strokeLine(glyph.x() - ledgerHalfWidth, y, glyph.x() + ledgerHalfWidth, y);
        }
        for (int s = 10; s <= staffStep; s += 2) {
            double y = staff.y() + s * (gap / 2.0);
            surface.strokeLine(glyph.x() - ledgerHalfWidth, y, glyph.x() + ledgerHalfWidth, y);
        }
    }
}
