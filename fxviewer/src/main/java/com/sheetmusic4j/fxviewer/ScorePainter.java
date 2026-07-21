package com.sheetmusic4j.fxviewer;

import java.util.EnumSet;
import java.util.Set;

import com.sheetmusic4j.engraving.BeamPlacement;
import com.sheetmusic4j.engraving.BracketPlacement;
import com.sheetmusic4j.engraving.Glyph;
import com.sheetmusic4j.engraving.GlyphPlacement;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.MarkingCategory;
import com.sheetmusic4j.engraving.MeasureLayout;
import com.sheetmusic4j.engraving.StaffLayout;
import com.sheetmusic4j.engraving.SystemBarline;
import com.sheetmusic4j.engraving.SystemLayout;
import com.sheetmusic4j.engraving.TextPlacement;
import com.sheetmusic4j.engraving.TiePlacement;

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

    /** Creates a painter for rendering a layout onto any {@link RenderSurface}. */
    public ScorePainter() {
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

    /** Currently hidden categories (a defensive copy). */
    public Set<MarkingCategory> getHiddenCategories() {
        return hiddenCategories.isEmpty()
                ? EnumSet.noneOf(MarkingCategory.class)
                : EnumSet.copyOf(hiddenCategories);
    }

    /**
     * Paints the given layout onto the provided surface.
     *
     * @param surface surface abstraction to draw on
     * @param layout engraved score layout to paint
     * @param surfaceWidth available surface width
     * @param surfaceHeight available surface height
     */
    public void paint(RenderSurface surface, LayoutResult layout, double surfaceWidth, double surfaceHeight) {
        surface.setFill(RenderColor.WHITE);
        surface.fillRect(0, 0, Math.max(layout.width(), surfaceWidth), Math.max(layout.height(), surfaceHeight));
        surface.setStroke(RenderColor.BLACK);
        surface.setFill(RenderColor.BLACK);
        surface.setLineWidth(1.0);

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
            for (BracketPlacement bracket : system.brackets()) {
                drawBracket(surface, bracket);
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
            double top = staff.lineY(0);
            double bottom = staff.lineY(STAFF_LINES - 1);
            surface.strokeLine(measure.right(), top, measure.right(), bottom);
        }

        for (GlyphPlacement glyph : staff.glyphs()) {
            if (hiddenCategories.contains(glyph.category())) {
                continue;
            }
            drawGlyph(surface, staff, glyph);
        }

        for (BeamPlacement beam : staff.beams()) {
            drawBeam(surface, staff, beam);
        }
        for (TiePlacement tie : staff.ties()) {
            drawTie(surface, staff, tie);
        }
    }

    private void drawGlyph(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double headW = gap * 1.2;
        double headH = gap * 0.9;
        double sizeHint = gap * 4;
        Glyph g = glyph.glyph();
        switch (g) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF, NOTEHEAD_WHOLE -> {
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
            case FLAG_8TH_UP, FLAG_8TH_DOWN, FLAG_16TH_UP, FLAG_16TH_DOWN -> {
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
                    drawEighthRest(surface, staff, glyph);
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
     * Draw a tie as a shallow curve approximated with two lines meeting at
     * the peak. Callers that need a real curve should override this via a
     * dedicated surface primitive; the two-segment approximation is enough
     * for the diagnostic comparator's window-level similarity check.
     */
    private void drawTie(RenderSurface surface, StaffLayout staff, TiePlacement tie) {
        double gap = staff.lineGap();
        double bend = gap * 0.6 * (tie.curveUp() ? -1 : 1);
        double midX = (tie.x1() + tie.x2()) / 2.0;
        double midY = ((tie.y1() + tie.y2()) / 2.0) + bend;
        surface.strokeLine(tie.x1(), tie.y1(), midX, midY);
        surface.strokeLine(midX, midY, tie.x2(), tie.y2());
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
            case NOTEHEAD_BLACK ->
                    surface.fillOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
            case NOTEHEAD_HALF, NOTEHEAD_WHOLE ->
                    surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
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

    private static String accidentalFallback(Glyph glyph) {
        return switch (glyph) {
            case ACCIDENTAL_SHARP, ACCIDENTAL_DOUBLE_SHARP -> "#";
            case ACCIDENTAL_FLAT, ACCIDENTAL_DOUBLE_FLAT -> "b";
            case ACCIDENTAL_NATURAL -> "n";
            default -> "";
        };
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

    private void drawEighthRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double d = gap * 0.6;
        double x = glyph.x();
        double y = staff.lineY(2);
        surface.fillOval(x - d / 2, y - d / 2, d, d);
        surface.strokeLine(x + d / 2, y, x - d / 2, y + gap * 1.5);
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
     * Draw a grouping mark (brace / bracket / line) at the left edge of a
     * system. {@link BracketPlacement.BracketShape#BRACE} is rendered via
     * the SMuFL {@code brace} glyph when a font is available, with a
     * primitive vertical line + serif fallback otherwise.
     * {@link BracketPlacement.BracketShape#BRACKET} and
     * {@link BracketPlacement.BracketShape#LINE} are reserved for a
     * follow-up task and currently render as a plain vertical line so the
     * switch is exhaustive.
     */
    private void drawBracket(RenderSurface surface, BracketPlacement bracket) {
        double span = bracket.bottomY() - bracket.topY();
        double midY = (bracket.topY() + bracket.bottomY()) / 2.0;
        switch (bracket.shape()) {
            case BRACE -> {
                // SMuFL's brace (E000) is a single-size glyph; approximate
                // stretching by drawing it at the span size, anchored so the
                // rough vertical centre sits near the group midpoint.
                boolean drawn = surface.drawSmuflGlyph("\uE000",
                        bracket.x(), midY + span * 0.25, span);
                if (!drawn) {
                    drawBraceFallback(surface, bracket, span);
                }
            }
            case BRACKET, LINE -> drawBracketLineFallback(surface, bracket);
        }
    }

    /**
     * Primitive brace fallback: a vertical line joined by two short
     * horizontal serifs at each end. Uglier than a real brace but
     * unambiguously signals "these staves are grouped".
     */
    private void drawBraceFallback(RenderSurface surface, BracketPlacement bracket, double span) {
        double x = bracket.x();
        surface.strokeLine(x, bracket.topY(), x, bracket.bottomY());
        // Serif width: 2/3 of the average staff-line gap. We don't have
        // direct access to the gap here; approximate from the span so the
        // serif scales sensibly with the brace height.
        double serif = Math.max(4.0, span * 0.05);
        surface.strokeLine(x, bracket.topY(), x + serif, bracket.topY());
        surface.strokeLine(x, bracket.bottomY(), x + serif, bracket.bottomY());
    }

    /**
     * Fallback for the not-yet-emitted {@link BracketPlacement.BracketShape#BRACKET}
     * and {@link BracketPlacement.BracketShape#LINE} shapes. Renders a
     * plain vertical line so downstream painters remain exhaustive.
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
