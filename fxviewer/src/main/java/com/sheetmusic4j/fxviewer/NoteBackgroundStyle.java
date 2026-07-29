package com.sheetmusic4j.fxviewer;

/**
 * Geometry of the rounded rectangle drawn behind a highlighted note by
 * {@link ScorePainter}'s note-background pass. All values are expressed in
 * staff-line gaps so the background keeps its proportion at any zoom level.
 *
 * <p>The rectangle is anchored to the note's rendered position and size (its
 * {@link com.sheetmusic4j.engraving.layout.NoteAnchor}); these paddings say how
 * far it extends beyond the note on each side. Larger paddings make a looser
 * box; smaller paddings hug the note more closely.
 *
 * @param padLeftGaps   padding left of the note (default leaves room for an
 *                      accidental slot)
 * @param padRightGaps  padding right of the note
 * @param padTopGaps    padding above the note
 * @param padBottomGaps padding below the note
 * @param arcGaps       corner arc radius
 * @param maxHeightGaps cap on the note-derived content height, so a chord
 *                      anchor whose bounding box includes a long stem does not
 *                      produce a hugely tall rectangle
 * @param offsetYGaps   vertical shift of the whole box; positive moves it down.
 *                      Zero centres it on the note; nudge it to sit over the
 *                      notehead rather than the stem, for example
 */
public record NoteBackgroundStyle(
        double padLeftGaps,
        double padRightGaps,
        double padTopGaps,
        double padBottomGaps,
        double arcGaps,
        double maxHeightGaps,
        double offsetYGaps) {

    public NoteBackgroundStyle {
        if (padLeftGaps < 0 || padRightGaps < 0 || padTopGaps < 0 || padBottomGaps < 0) {
            throw new IllegalArgumentException("paddings must be >= 0");
        }
        if (arcGaps < 0) {
            throw new IllegalArgumentException("arcGaps must be >= 0");
        }
        if (maxHeightGaps <= 0) {
            throw new IllegalArgumentException("maxHeightGaps must be > 0");
        }
    }

    /**
     * The default box: a wider left slot (to cover an accidental), a narrow
     * right slot and modest vertical padding, centred on the note. Matches the
     * historical hard-coded geometry.
     */
    public static NoteBackgroundStyle defaults() {
        return new NoteBackgroundStyle(2.2, 0.6, 0.4, 0.4, 1.0, 4.5, 0.0);
    }

    /**
     * A box that hugs the note closely on all sides - useful when the
     * background should read as "this exact note" rather than a broad slot.
     */
    public static NoteBackgroundStyle tight() {
        return new NoteBackgroundStyle(0.5, 0.5, 0.35, 0.35, 0.6, 4.5, 0.0);
    }

    /** A copy with equal left/right padding. */
    public NoteBackgroundStyle withHorizontalPadding(double gaps) {
        return new NoteBackgroundStyle(gaps, gaps, padTopGaps, padBottomGaps, arcGaps, maxHeightGaps, offsetYGaps);
    }

    /** A copy with equal top/bottom padding. */
    public NoteBackgroundStyle withVerticalPadding(double gaps) {
        return new NoteBackgroundStyle(padLeftGaps, padRightGaps, gaps, gaps, arcGaps, maxHeightGaps, offsetYGaps);
    }

    /** A copy with a different corner arc radius. */
    public NoteBackgroundStyle withArc(double gaps) {
        return new NoteBackgroundStyle(padLeftGaps, padRightGaps, padTopGaps, padBottomGaps, gaps, maxHeightGaps, offsetYGaps);
    }

    /** A copy shifted vertically; positive moves the box down. */
    public NoteBackgroundStyle withOffsetY(double gaps) {
        return new NoteBackgroundStyle(padLeftGaps, padRightGaps, padTopGaps, padBottomGaps, arcGaps, maxHeightGaps, gaps);
    }
}
