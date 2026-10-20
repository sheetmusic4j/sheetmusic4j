package com.sheetmusic4j.engraving.page;

import java.util.ArrayList;
import java.util.List;

import com.sheetmusic4j.core.model.Creator;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.PartGroup;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.glyph.MarkingCategory;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.TextPlacement;
import com.sheetmusic4j.engraving.analysis.PartInfo;
import com.sheetmusic4j.engraving.analysis.ScoreAnalysis;
import com.sheetmusic4j.engraving.staff.StaffGeometry;

/**
 * Page-level text outside the staves: the title block, section titles,
 * words after the tune, and the instrument labels left of each system.
 */
public final class TextBlockLayout {

    /**
     * Multiplier applied to the staff-line gap to derive the part-label font
     * size. Matches the lyric font size so both share a visual weight class.
     */
    public static final double PART_LABEL_FONT_SIZE_GAPS = 1.4;

    /**
     * Small trailing gap (in staff-line gaps) added after the longest label
     * so labels never abut the system left barline.
     */
    private static final double PART_LABEL_TRAILING_PAD_GAPS = 1.2;

    private TextBlockLayout() {
    }

    /**
     * Emit the score-level title / subtitle text placements at the top of the
     * page and return the total vertical space they consume (0 when the
     * score carries no metadata).
     *
     * <p>Titles are centered horizontally on the system. The block sits above
     * the first system's staves and pushes them down by the returned height.
     */
    public static double layoutTitleBlock(Score score, LayoutOptions options, List<TextPlacement> texts) {
        if (!options.showTitleTexts()) {
            return 0.0;
        }
        double gap = options.staffLineGap();
        double centerX = options.systemWidth() / 2.0;
        double y = options.topMargin();
        double consumed = 0.0;

        String workTitle = score.workTitle().orElse(null);
        if (workTitle != null && !workTitle.isBlank() && options.isVisible(MarkingCategory.TITLE)) {
            double fontSize = gap * 2.4;
            texts.add(new TextPlacement(workTitle, centerX, y + fontSize,
                    fontSize, TextPlacement.Align.CENTER, MarkingCategory.TITLE));
            double advance = fontSize * 1.2;
            y += advance;
            consumed += advance;
        }
        String movement = score.movementTitle().orElse(null);
        if (movement != null && !movement.isBlank() && !movement.equals(workTitle)
                && options.isVisible(MarkingCategory.SUBTITLE)) {
            double fontSize = gap * 1.6;
            texts.add(new TextPlacement(movement, centerX, y + fontSize,
                    fontSize, TextPlacement.Align.CENTER, MarkingCategory.SUBTITLE));
            double advance = fontSize * 1.4;
            y += advance;
            consumed += advance;
        }

        // Creator rows: composer/arranger/transcriber to the right, lyricist/
        // poet/translator to the left; any other role stacks left below.
        List<Creator> creators = score.creators();
        if (!creators.isEmpty() && options.isVisible(MarkingCategory.CREATOR)) {
            List<Creator> rightColumn = new ArrayList<>();
            List<Creator> leftColumn = new ArrayList<>();
            for (Creator creator : creators) {
                switch (creator.role()) {
                    case "composer", "arranger", "transcriber" -> rightColumn.add(creator);
                    case "lyricist", "poet", "translator" -> leftColumn.add(creator);
                    default -> leftColumn.add(creator);
                }
            }
            double fontSize = gap * 1.1;
            double leftX = options.leftMargin();
            double rightX = options.systemWidth() - options.rightMargin();
            int rowCount = Math.max(rightColumn.size(), leftColumn.size());
            for (int i = 0; i < rowCount; i++) {
                double baselineY = y + fontSize;
                if (i < rightColumn.size()) {
                    texts.add(new TextPlacement(rightColumn.get(i).name(), rightX, baselineY,
                            fontSize, TextPlacement.Align.RIGHT, MarkingCategory.CREATOR));
                }
                if (i < leftColumn.size()) {
                    texts.add(new TextPlacement(leftColumn.get(i).name(), leftX, baselineY,
                            fontSize, TextPlacement.Align.LEFT, MarkingCategory.CREATOR));
                }
                double advance = fontSize * 1.4;
                y += advance;
                consumed += advance;
            }
        }

        if (consumed > 0) {
            // A little breathing space between the title block and the first
            // staff; more when the score carries marks drawn above the staff
            // (bowings, ornaments) or chord symbols, so they don't collide
            // with the title text. Chord symbols sit further from the staff
            // than the reserve added for them at row layout time accounts
            // for (see AnnotationLayout.HARMONY_OFFSET_GAPS vs.
            // Engraver.HARMONY_ABOVE_RESERVE_GAPS), so the first row needs
            // its own, larger, clearance. Even the baseline (no special
            // markings) case needs more than a single gap: an ordinary high
            // note's stem - more so with multiple flags (sixteenth/thirty-
            // second/...) - can reach close enough to the staff top to
            // collide with the title text otherwise.
            double breathing = gap * 2.2;
            if (ScoreAnalysis.hasAboveStaffArticulation(score)) {
                breathing = Math.max(breathing, gap * 2.5);
            }
            if (ScoreAnalysis.hasHarmony(score)) {
                breathing = Math.max(breathing, gap * 2.7);
            }
            if (ScoreAnalysis.hasGraceNotes(score)) {
                breathing = Math.max(breathing, gap * 3.2);
            }
            if (ScoreAnalysis.hasTuplets(score)) {
                breathing = Math.max(breathing, gap * 3.0);
            }
            consumed += breathing;
        }
        return consumed;
    }

    /**
     * Emit the section title (ABC mid-tune {@code T:}) that starts at the
     * row's first measure, if any, and return the staff top pushed down
     * below it.
     */
    public static double layoutSectionTitle(List<PartInfo> parts, int rowStart, double staffTop,
                                     LayoutOptions options, List<TextPlacement> texts) {
        String sectionTitle = sectionTitleAt(parts, rowStart);
        if (sectionTitle == null || !options.showTitleTexts() || !options.isVisible(MarkingCategory.SUBTITLE)) {
            return staffTop;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * 1.8;
        texts.add(new TextPlacement(sectionTitle, options.systemWidth() / 2.0, staffTop + fontSize,
                fontSize, TextPlacement.Align.CENTER, MarkingCategory.SUBTITLE));
        // Clear not just the text itself but a typical beamed run's
        // stem height above the staff below it (the row's actual
        // note positions aren't known yet at this point).
        return staffTop + (fontSize + gap * 4.5);
    }

    /**
     * The section title carried by any part's measure at index {@code i},
     * the row's first measure - or {@code null} if none of them start a new
     * section there.
     */
    private static String sectionTitleAt(List<PartInfo> parts, int i) {
        for (PartInfo p : parts) {
            List<Measure> measures = p.measures();
            if (i < measures.size()) {
                String title = measures.get(i).sectionTitle().orElse(null);
                if (title != null) {
                    return title;
                }
            }
        }
        return null;
    }

    /**
     * Emit ABC {@code W:} "words after tune" text lines below the last
     * system. Returns the y coordinate reached after emitting the block
     * (equal to {@code startY} when no part carries post-tune text).
     *
     * <p>Lines are drawn left-aligned to the same margin used by the
     * title block so a reader sees the verses laid out like typeset
     * lyrics under the score, not squeezed into the last measure.
     */
    public static double layoutPostTuneText(Score score, LayoutOptions options,
                                     double startY, List<TextPlacement> texts) {
        if (!options.showTitleTexts() || !options.isVisible(MarkingCategory.LYRIC)) {
            return startY;
        }
        boolean any = false;
        for (Part part : score.parts()) {
            if (!part.postTuneText().isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return startY;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * 1.4;
        double lineHeight = fontSize * 1.3;
        double x = options.leftMargin();
        double y = startY + gap * 1.5;
        for (Part part : score.parts()) {
            for (String line : part.postTuneText()) {
                y += lineHeight;
                texts.add(new TextPlacement(line == null ? "" : line, x, y,
                        fontSize, TextPlacement.Align.LEFT, MarkingCategory.LYRIC));
            }
        }
        return y + gap;
    }

    /**
     * Compute the horizontal space to reserve to the left of every system
     * for part-name / part-abbreviation labels and the bracket column.
     * Returns {@code 0} when no part carries a non-blank name or
     * abbreviation and there are no {@link PartGroup part groups}, so
     * single-part unlabeled scores keep their pre-existing layout.
     *
     * <p>Labels are measured with {@link LayoutOptions#textMetrics()} -
     * the same metrics the painter aligns text with - plus a small padding
     * so labels never abut the system barline. Group-name /
     * abbreviation lengths participate in the same longest-label search.
     * When any group is declared we additionally reserve room for the
     * bracket columns themselves, sized so the outermost column sits
     * {@code (maxGroupNestingDepth + 1) * gap * BRACKET_COLUMN_STEP_GAPS}
     * to the left of {@code contentLeft}.
     *
     * @param score                  the score to inspect
     * @param options                layout options; used for staff-line gap only
     * @param maxGroupNestingDepth   max container count across all groups
     *                               ({@code -1} when the score has none)
     * @return non-negative reserve in layout units
     */
    public static double computeLabelReserve(Score score, LayoutOptions options,
                                      int maxGroupNestingDepth) {
        double gap = options.staffLineGap();
        double fontSize = gap * PART_LABEL_FONT_SIZE_GAPS;
        double widest = 0.0;
        for (Part part : score.parts()) {
            widest = Math.max(widest, labelWidth(part.name(), fontSize, options));
            widest = Math.max(widest, labelWidth(part.abbreviation(), fontSize, options));
        }
        for (PartGroup group : score.partGroups()) {
            widest = Math.max(widest, labelWidth(group.name(), fontSize, options));
            widest = Math.max(widest, labelWidth(group.abbreviation(), fontSize, options));
        }
        double labelReserve = 0.0;
        if (widest > 0 && options.isVisible(MarkingCategory.PART_LABEL)) {
            labelReserve = widest + gap * PART_LABEL_TRAILING_PAD_GAPS;
        }
        double bracketReserve = 0.0;
        if (maxGroupNestingDepth >= 0) {
            bracketReserve = (maxGroupNestingDepth + 1) * gap * PartGroupLayout.BRACKET_COLUMN_STEP_GAPS;
        }
        return labelReserve + bracketReserve;
    }

    /**
     * Width of a label as measured by the layout's
     * {@link LayoutOptions#textMetrics()}; {@code 0} for {@code null} or
     * blank labels.
     */
    private static double labelWidth(String label, double fontSize, LayoutOptions options) {
        return label == null || label.isBlank() ? 0.0 : options.textMetrics().width(label, fontSize);
    }

    /**
     * Emit the instrument-label {@link TextPlacement} for a part at the
     * given row. Skipped silently when the part carries neither a name nor
     * an abbreviation. On the first row of the layout the full
     * {@code <part-name>} is used; on subsequent rows we prefer
     * {@code <part-abbreviation>} and fall back to the full name when the
     * source did not supply one.
     *
     * @param texts             collector for page-level text placements
     * @param part              the part being labeled
     * @param rowIdx            0-based row index within the layout
     * @param firstStaffOfPart  first staff belonging to the part in this row
     * @param lastStaffOfPart   last staff belonging to the part in this row
     * @param options           layout options; used for font sizing only
     */
    public static void emitPartLabel(List<TextPlacement> texts, Part part, int rowIdx,
                              StaffLayout firstStaffOfPart, StaffLayout lastStaffOfPart,
                              LayoutOptions options) {
        String labelText;
        if (rowIdx == 0) {
            labelText = part.name();
        } else {
            labelText = part.abbreviation() != null ? part.abbreviation() : part.name();
        }
        if (labelText == null || labelText.isBlank() || !options.isVisible(MarkingCategory.PART_LABEL)) {
            return;
        }
        double gap = options.staffLineGap();
        double fontSize = gap * PART_LABEL_FONT_SIZE_GAPS;
        double top = firstStaffOfPart.lineY(0);
        double bottom = lastStaffOfPart.lineY(StaffGeometry.STAFF_LINES - 1);
        double labelY = (top + bottom) / 2.0 + fontSize / 2.0;
        texts.add(new TextPlacement(labelText, options.leftMargin(), labelY,
                fontSize, TextPlacement.Align.LEFT, MarkingCategory.PART_LABEL));
    }
}
