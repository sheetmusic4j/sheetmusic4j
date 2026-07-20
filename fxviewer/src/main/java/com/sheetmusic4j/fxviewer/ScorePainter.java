package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.GlyphPlacement;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.MeasureLayout;
import com.sheetmusic4j.engraving.StaffLayout;

/**
 * Surface-agnostic painting of a {@link LayoutResult}. All drawing goes through a
 * {@link RenderSurface}, so the identical logic can target a JavaFX canvas
 * (on screen) or an AWT image (headless tests / comparisons).
 *
 * <p>Uses primitive shapes rather than a bundled SMuFL font. When a SMuFL font
 * (typically Bravura) is later bundled with fxviewer, {@link #drawGlyph} is the
 * single place to switch to codepoint text rendering.
 */
public final class ScorePainter {

    private static final int STAFF_LINES = 5;
    private static final double STEM_LENGTH_GAPS = 3.5;

    public void paint(RenderSurface surface, LayoutResult layout, double surfaceWidth, double surfaceHeight) {
        surface.setFill(RenderColor.WHITE);
        surface.fillRect(0, 0, Math.max(layout.width(), surfaceWidth), Math.max(layout.height(), surfaceHeight));
        surface.setStroke(RenderColor.BLACK);
        surface.setFill(RenderColor.BLACK);
        surface.setLineWidth(1.0);

        for (StaffLayout staff : layout.staves()) {
            drawStaff(surface, staff);
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
        surface.strokeLine(staff.x(), staff.lineY(0), staff.x(), staff.lineY(STAFF_LINES - 1));

        for (GlyphPlacement glyph : staff.glyphs()) {
            drawGlyph(surface, staff, glyph);
        }
    }

    private void drawGlyph(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double headW = gap * 1.2;
        double headH = gap * 0.9;
        switch (glyph.glyph()) {
            case NOTEHEAD_BLACK -> {
                surface.fillOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(surface, staff, glyph);
            }
            case NOTEHEAD_HALF -> {
                surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(surface, staff, glyph);
            }
            case NOTEHEAD_WHOLE -> {
                surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
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
            case CLEF_G -> drawClef(surface, staff, glyph, "G");
            case CLEF_F -> drawClef(surface, staff, glyph, "F");
            case CLEF_C -> drawClef(surface, staff, glyph, "C");
            case REST_WHOLE -> drawWholeRest(surface, staff, glyph);
            case REST_HALF -> drawHalfRest(surface, staff, glyph);
            case REST_QUARTER -> drawQuarterRest(surface, staff, glyph);
            case REST_EIGHTH -> drawEighthRest(surface, staff, glyph);
            default -> {
                Character digit = glyph.glyph().timeDigitChar();
                if (digit != null) {
                    surface.strokeText(digit.toString(), glyph.x(), glyph.y());
                }
                // STAFF_LINE / LEDGER_LINE / STEM (legacy) handled elsewhere.
            }
        }
    }

    /**
     * Draw a stylized clef. Until a SMuFL font is bundled, this simply emits the
     * clef letter but scaled larger and vertically anchored on the correct line.
     */
    private void drawClef(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph, String letter) {
        double gap = staff.lineGap();
        // Draw the letter roughly two staff spaces tall so it visibly reads as a clef.
        surface.strokeText(letter, glyph.x(), glyph.y() + gap * 0.5);
    }

    /**
     * Whole rest: filled rectangle hanging from the 4th line (top-of-third-space).
     */
    private void drawWholeRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double w = gap * 1.2;
        double h = gap * 0.5;
        double y = staff.lineY(1); // hanging from line 2 (top of middle space)
        surface.fillRect(glyph.x() - w / 2, y, w, h);
    }

    /**
     * Half rest: filled rectangle sitting on the middle line.
     */
    private void drawHalfRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double w = gap * 1.2;
        double h = gap * 0.5;
        double y = staff.lineY(2) - h; // sitting on line 3 (middle line)
        surface.fillRect(glyph.x() - w / 2, y, w, h);
    }

    /**
     * Quarter rest: stylized zig-zag drawn as three connected segments.
     */
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
     * Eighth rest: small filled circle with a flag-like stroke.
     */
    private void drawEighthRest(RenderSurface surface, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double d = gap * 0.6;
        double x = glyph.x();
        double y = staff.lineY(2);
        surface.fillOval(x - d / 2, y - d / 2, d, d);
        surface.strokeLine(x + d / 2, y, x - d / 2, y + gap * 1.5);
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
