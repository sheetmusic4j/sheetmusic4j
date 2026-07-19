package com.sheet4j.fxviewer;

import com.sheet4j.engraving.GlyphPlacement;
import com.sheet4j.engraving.LayoutResult;
import com.sheet4j.engraving.MeasureLayout;
import com.sheet4j.engraving.StaffLayout;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Draws a {@link LayoutResult} onto a JavaFX {@link GraphicsContext} using simple
 * vector shapes (lines and ellipses). SMuFL font rendering is a future enhancement.
 */
public final class ScoreRenderer {

    private static final int STAFF_LINES = 5;

    public void render(GraphicsContext gc, LayoutResult layout) {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, Math.max(layout.width(), gc.getCanvas().getWidth()),
                Math.max(layout.height(), gc.getCanvas().getHeight()));
        gc.setStroke(Color.BLACK);
        gc.setFill(Color.BLACK);
        gc.setLineWidth(1.0);

        for (StaffLayout staff : layout.staves()) {
            drawStaff(gc, staff);
        }
    }

    private void drawStaff(GraphicsContext gc, StaffLayout staff) {
        for (int line = 0; line < STAFF_LINES; line++) {
            double y = staff.lineY(line);
            gc.strokeLine(staff.x(), y, staff.x() + staff.width(), y);
        }

        // Barlines
        for (MeasureLayout measure : staff.measures()) {
            double top = staff.lineY(0);
            double bottom = staff.lineY(STAFF_LINES - 1);
            gc.strokeLine(measure.right(), top, measure.right(), bottom);
        }
        double leftTop = staff.lineY(0);
        double leftBottom = staff.lineY(STAFF_LINES - 1);
        gc.strokeLine(staff.x(), leftTop, staff.x(), leftBottom);

        for (GlyphPlacement glyph : staff.glyphs()) {
            drawGlyph(gc, staff, glyph);
        }
    }

    private void drawGlyph(GraphicsContext gc, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        double headW = gap * 1.2;
        double headH = gap * 0.9;
        switch (glyph.glyph()) {
            case NOTEHEAD_BLACK -> {
                gc.fillOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(gc, staff, glyph);
                gc.strokeLine(glyph.x() + headW / 2, glyph.y(), glyph.x() + headW / 2, glyph.y() - gap * 3.5);
            }
            case NOTEHEAD_HALF -> {
                gc.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(gc, staff, glyph);
                gc.strokeLine(glyph.x() + headW / 2, glyph.y(), glyph.x() + headW / 2, glyph.y() - gap * 3.5);
            }
            case NOTEHEAD_WHOLE -> {
                gc.strokeOval(glyph.x() - headW / 2, glyph.y() - headH / 2, headW, headH);
                drawLedgerLines(gc, staff, glyph);
            }
            case CLEF_G -> gc.strokeText("G", glyph.x(), glyph.y());
            case CLEF_F -> gc.strokeText("F", glyph.x(), glyph.y());
            case CLEF_C -> gc.strokeText("C", glyph.x(), glyph.y());
            case TIME_DIGIT -> gc.strokeText("4/4", glyph.x(), glyph.y());
            case REST_WHOLE, REST_HALF, REST_QUARTER, REST_EIGHTH ->
                    gc.strokeText("\u00A6", glyph.x(), glyph.y());
            default -> {
                // STAFF_LINE / LEDGER_LINE / STEM handled elsewhere
            }
        }
    }

    private void drawLedgerLines(GraphicsContext gc, StaffLayout staff, GlyphPlacement glyph) {
        double gap = staff.lineGap();
        int staffStep = glyph.staffStep();
        double ledgerHalfWidth = gap * 0.9;
        // Above the top line (staffStep < 0), at even half-steps.
        for (int s = -2; s >= staffStep; s -= 2) {
            double y = staff.y() + s * (gap / 2.0);
            gc.strokeLine(glyph.x() - ledgerHalfWidth, y, glyph.x() + ledgerHalfWidth, y);
        }
        // Below the bottom line (staffStep > 8).
        for (int s = 10; s <= staffStep; s += 2) {
            double y = staff.y() + s * (gap / 2.0);
            gc.strokeLine(glyph.x() - ledgerHalfWidth, y, glyph.x() + ledgerHalfWidth, y);
        }
    }
}
