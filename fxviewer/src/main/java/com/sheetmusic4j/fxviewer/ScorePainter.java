package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.GlyphPlacement;
import com.sheetmusic4j.engraving.LayoutResult;
import com.sheetmusic4j.engraving.MeasureLayout;
import com.sheetmusic4j.engraving.StaffLayout;

/**
 * Surface-agnostic painting of a {@link LayoutResult}. All drawing goes through a
 * {@link RenderSurface}, so the identical logic can target a JavaFX canvas
 * (on screen) or an AWT image (headless tests / comparisons).
 */
public final class ScorePainter {

    private static final int STAFF_LINES = 5;

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
                surface.strokeLine(glyph.x() + headW / 2, glyph.y(), glyph.x() + headW / 2, glyph.y() - gap * 3.5);
            }
            case NOTEHEAD_HALF -> {
                surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(surface, staff, glyph);
                surface.strokeLine(glyph.x() + headW / 2, glyph.y(), glyph.x() + headW / 2, glyph.y() - gap * 3.5);
            }
            case NOTEHEAD_WHOLE -> {
                surface.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(surface, staff, glyph);
            }
            case CLEF_G -> surface.strokeText("G", glyph.x(), glyph.y());
            case CLEF_F -> surface.strokeText("F", glyph.x(), glyph.y());
            case CLEF_C -> surface.strokeText("C", glyph.x(), glyph.y());
            case TIME_DIGIT -> surface.strokeText("4/4", glyph.x(), glyph.y());
            case REST_WHOLE, REST_HALF, REST_QUARTER, REST_EIGHTH ->
                    surface.strokeText("\u00A6", glyph.x(), glyph.y());
            default -> {
                // STAFF_LINE / LEDGER_LINE / STEM handled elsewhere
            }
        }
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
