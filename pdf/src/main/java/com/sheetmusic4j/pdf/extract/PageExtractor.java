package com.sheetmusic4j.pdf.extract;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDVectorFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import com.sheetmusic4j.pdf.PdfException;
import com.sheetmusic4j.pdf.extract.PageContent.Box;
import com.sheetmusic4j.pdf.extract.PageContent.Glyph;
import com.sheetmusic4j.pdf.extract.PageContent.HLine;
import com.sheetmusic4j.pdf.extract.PageContent.Segment;
import com.sheetmusic4j.pdf.extract.PageContent.VLine;

/**
 * Runs a page's content stream and records glyphs and vector shapes as {@link PageContent}. Shapes are classified
 * by geometry only (thin and tall, thin and wide, curved, other); deciding what they mean is left to recognition.
 */
public final class PageExtractor extends PDFGraphicsStreamEngine {

    private static final Box NO_BOX = new Box(0, 0, 0, 0);
    private static final double EPS = 0.05;

    private final double height;
    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<HLine> hlines = new ArrayList<>();
    private final List<VLine> vlines = new ArrayList<>();
    private final List<Segment> diagonals = new ArrayList<>();
    private final List<Box> beams = new ArrayList<>();
    private final List<Box> curves = new ArrayList<>();
    /** Outline bounds per font object and character code; subsets of one font reuse codes for different glyphs. */
    private final Map<PDFont, Map<Integer, Rectangle2D>> outlineBounds = new IdentityHashMap<>();

    private final List<Sub> path = new ArrayList<>();
    private Sub current;

    private PageExtractor(PDPage page) {
        super(page);
        this.height = page.getMediaBox().getHeight();
    }

    /**
     * Extract one page.
     *
     * @throws PdfException when the content stream cannot be interpreted
     */
    public static PageContent extract(PDPage page, int index) {
        PageExtractor extractor = new PageExtractor(page);
        try {
            extractor.processPage(page);
        } catch (IOException | RuntimeException e) {
            throw new PdfException("Could not read page " + (index + 1), e);
        }
        return new PageContent(index, page.getMediaBox().getWidth(), extractor.height, extractor.glyphs,
                extractor.hlines, extractor.vlines, extractor.diagonals, extractor.beams, extractor.curves);
    }

    private static final class Sub {
        final List<double[]> points = new ArrayList<>();
        boolean curved;
    }

    @Override
    protected void showGlyph(Matrix rendering, PDFont font, int code, Vector displacement) {
        String fontName = font.getName() == null ? "" : font.getName().replaceFirst("^[A-Z]{6}\\+", "");
        String name = null;
        if (font instanceof PDSimpleFont simple && simple.getEncoding() != null) {
            name = simple.getEncoding().getName(code);
        }
        String text = null;
        try {
            text = font.toUnicode(code);
        } catch (RuntimeException ignored) {
            // fonts without a usable mapping are identified by glyph name only
        }
        AffineTransform at = rendering.createAffineTransform();
        at.concatenate(font.getFontMatrix().createAffineTransform());
        Rectangle2D bounds = outlineBounds.computeIfAbsent(font, f -> new HashMap<>()).computeIfAbsent(code, c -> outline(font, c));
        Rectangle2D device = at.createTransformedShape(bounds).getBounds2D();
        Box box = bounds == null || bounds.isEmpty() ? NO_BOX
                : new Box(device.getMinX(), height - device.getMaxY(), device.getMaxX(), height - device.getMinY());
        glyphs.add(new Glyph(fontName, name, text, rendering.getTranslateX(), height - rendering.getTranslateY(), box));
    }

    private static Rectangle2D outline(PDFont font, int code) {
        try {
            if (font instanceof PDVectorFont vector) {
                return vector.getNormalizedPath(code).getBounds2D();
            }
        } catch (IOException | RuntimeException ignored) {
            // no outline: the glyph is placed by its origin only
        }
        return new Rectangle2D.Double();
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        moveTo((float) p0.getX(), (float) p0.getY());
        lineTo((float) p1.getX(), (float) p1.getY());
        lineTo((float) p2.getX(), (float) p2.getY());
        lineTo((float) p3.getX(), (float) p3.getY());
        closePath();
    }

    @Override
    public void moveTo(float x, float y) {
        current = new Sub();
        current.points.add(new double[]{x, height - y});
        path.add(current);
    }

    @Override
    public void lineTo(float x, float y) {
        if (current == null) {
            moveTo(x, y);
        } else {
            current.points.add(new double[]{x, height - y});
        }
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (current == null) {
            moveTo(x1, y1);
        }
        current.curved = true;
        current.points.add(new double[]{x3, height - y3});
        current.points.add(new double[]{x1, height - y1});
        current.points.add(new double[]{x2, height - y2});
    }

    @Override
    public Point2D getCurrentPoint() {
        if (current == null || current.points.isEmpty()) {
            return new Point2D.Float();
        }
        double[] p = current.points.get(0);
        return new Point2D.Double(p[0], height - p[1]);
    }

    @Override
    public void closePath() {
        if (current != null && current.points.size() > 1) {
            current.points.add(current.points.get(0));
        }
    }

    @Override
    public void clip(int windingRule) {
        reset();
    }

    @Override
    public void endPath() {
        reset();
    }

    @Override
    public void strokePath() {
        paint(false, true);
    }

    @Override
    public void fillPath(int windingRule) {
        paint(true, false);
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        paint(true, true);
    }

    @Override
    public void shadingFill(COSName shadingName) {
        reset();
    }

    @Override
    public void drawImage(PDImage image) {
        // scanned content is not engraved music
    }

    private void reset() {
        path.clear();
        current = null;
    }

    private void paint(boolean fill, boolean stroke) {
        double lineWidth = stroke
                ? getGraphicsState().getLineWidth() * getGraphicsState().getCurrentTransformationMatrix().getScalingFactorX()
                : 0;
        for (Sub sub : path) {
            paintSub(sub, fill, stroke, lineWidth);
        }
        reset();
    }

    private void paintSub(Sub sub, boolean fill, boolean stroke, double lineWidth) {
        if (sub.points.size() < 2) {
            return;
        }
        Box box = bounds(sub);
        if (sub.curved) {
            curves.add(box);
            return;
        }
        if (stroke) {
            strokeSegments(sub, lineWidth);
        }
        if (fill && sub.points.size() >= 3) {
            fillShape(box);
        }
    }

    private void strokeSegments(Sub sub, double lineWidth) {
        for (int i = 1; i < sub.points.size(); i++) {
            double[] a = sub.points.get(i - 1);
            double[] b = sub.points.get(i);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            if (Math.abs(dy) < EPS && Math.abs(dx) > EPS) {
                hlines.add(new HLine(Math.min(a[0], b[0]), Math.max(a[0], b[0]), a[1], lineWidth));
            } else if (Math.abs(dx) < EPS && Math.abs(dy) > EPS) {
                vlines.add(new VLine(a[0], Math.min(a[1], b[1]), Math.max(a[1], b[1]), lineWidth));
            } else if (Math.abs(dx) > EPS && Math.abs(dy) > EPS) {
                diagonals.add(a[0] <= b[0] ? new Segment(a[0], a[1], b[0], b[1], lineWidth)
                        : new Segment(b[0], b[1], a[0], a[1], lineWidth));
            }
        }
    }

    private void fillShape(Box box) {
        double w = box.width();
        double h = box.height();
        if (w < 4 && h >= 4 * w && h > 2) {
            vlines.add(new VLine(box.centerX(), box.top(), box.bottom(), w));
        } else if (h < 1.6 && w >= 4 * h && w > 4) {
            hlines.add(new HLine(box.left(), box.right(), box.centerY(), h));
        } else if (w > 1 && h > 0.5) {
            beams.add(box);
        }
    }

    private static Box bounds(Sub sub) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : sub.points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        return new Box(minX, minY, maxX, maxY);
    }
}
