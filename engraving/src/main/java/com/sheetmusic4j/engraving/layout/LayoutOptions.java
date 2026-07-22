package com.sheetmusic4j.engraving.layout;

/**
 * Tunable parameters controlling how a score is laid out. All measurements are
 * in abstract pixel-like units.
 *
 * <p>Historically defined as a Java record; migrated to a class + {@link
 * Builder} to allow additional layout knobs to be added without breaking the
 * positional constructor of every downstream caller. The eight original
 * fields are still reachable via the legacy constructor
 * {@link #LayoutOptions(double, double, double, double, double, double,
 * double, double)} and their accessor methods retain the record-style
 * (no-{@code get}) naming.
 */
public final class LayoutOptions {

    private final double staffLineGap;
    private final double staffSpacing;
    private final double systemWidth;
    private final double leftMargin;
    private final double rightMargin;
    private final double topMargin;
    private final double measureMinWidth;
    private final double fontSize;
    private final LayoutMode layoutMode;
    private final boolean showClef;
    private final boolean showKeySignature;
    private final boolean showTimeSignature;
    private final boolean showTitleTexts;

    /**
     * Legacy positional constructor. Populates the record-era fields; all
     * new flags default to a "page mode, everything visible" preset so
     * behaviour is unchanged.
     *
     * @param staffLineGap    vertical gap between two adjacent staff lines
     * @param staffSpacing    vertical gap between the bottom of one part's
     *                        staff and the top of the next
     * @param systemWidth     total available width for a system (ignored in
     *                        {@link LayoutMode#STRIP} mode)
     * @param leftMargin      left margin before the first measure
     * @param rightMargin     right margin after the last measure
     * @param topMargin       top margin above the first staff
     * @param measureMinWidth minimum width for a single measure
     * @param fontSize        default font size hint for glyphs
     */
    public LayoutOptions(double staffLineGap, double staffSpacing, double systemWidth,
                         double leftMargin, double rightMargin, double topMargin,
                         double measureMinWidth, double fontSize) {
        this(staffLineGap, staffSpacing, systemWidth, leftMargin, rightMargin, topMargin,
                measureMinWidth, fontSize,
                LayoutMode.PAGE, true, true, true, true);
    }

    private LayoutOptions(double staffLineGap, double staffSpacing, double systemWidth,
                          double leftMargin, double rightMargin, double topMargin,
                          double measureMinWidth, double fontSize,
                          LayoutMode layoutMode, boolean showClef, boolean showKeySignature,
                          boolean showTimeSignature, boolean showTitleTexts) {
        this.staffLineGap = staffLineGap;
        this.staffSpacing = staffSpacing;
        this.systemWidth = systemWidth;
        this.leftMargin = leftMargin;
        this.rightMargin = rightMargin;
        this.topMargin = topMargin;
        this.measureMinWidth = measureMinWidth;
        this.fontSize = fontSize;
        this.layoutMode = layoutMode == null ? LayoutMode.PAGE : layoutMode;
        this.showClef = showClef;
        this.showKeySignature = showKeySignature;
        this.showTimeSignature = showTimeSignature;
        this.showTitleTexts = showTitleTexts;
    }

    /** Returns a fresh options instance populated with sensible defaults. */
    public static LayoutOptions defaults() {
        return new LayoutOptions(10.0, 60.0, 1000.0, 40.0, 20.0, 40.0, 120.0, 32.0);
    }

    /** Start a new builder pre-populated with {@link #defaults()}. */
    public static Builder builder() {
        return new Builder(defaults());
    }

    /** Return a builder pre-populated with this options instance's values. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public double staffLineGap() {
        return staffLineGap;
    }

    public double staffSpacing() {
        return staffSpacing;
    }

    public double systemWidth() {
        return systemWidth;
    }

    public double leftMargin() {
        return leftMargin;
    }

    public double rightMargin() {
        return rightMargin;
    }

    public double topMargin() {
        return topMargin;
    }

    public double measureMinWidth() {
        return measureMinWidth;
    }

    public double fontSize() {
        return fontSize;
    }

    /** How the engraver should distribute the score into systems. */
    public LayoutMode layoutMode() {
        return layoutMode;
    }

    /** Whether to emit the clef glyph at the start of each system. */
    public boolean showClef() {
        return showClef;
    }

    /** Whether to emit the key-signature accidentals. */
    public boolean showKeySignature() {
        return showKeySignature;
    }

    /** Whether to emit the time-signature digits. */
    public boolean showTimeSignature() {
        return showTimeSignature;
    }

    /**
     * Whether to emit the page-level title / subtitle / composer text
     * placements. Turn off for widget use where only the notation itself
     * should be drawn.
     */
    public boolean showTitleTexts() {
        return showTitleTexts;
    }

    /**
     * Height of a five-line staff (four gaps).
     */
    public double staffHeight() {
        return staffLineGap * 4;
    }

    /**
     * Fluent builder for {@link LayoutOptions}. New fields should be added
     * here (and given a sensible default in {@link LayoutOptions#defaults()})
     * rather than in the legacy positional constructor.
     */
    public static final class Builder {
        private double staffLineGap;
        private double staffSpacing;
        private double systemWidth;
        private double leftMargin;
        private double rightMargin;
        private double topMargin;
        private double measureMinWidth;
        private double fontSize;
        private LayoutMode layoutMode;
        private boolean showClef;
        private boolean showKeySignature;
        private boolean showTimeSignature;
        private boolean showTitleTexts;

        private Builder(LayoutOptions base) {
            this.staffLineGap = base.staffLineGap;
            this.staffSpacing = base.staffSpacing;
            this.systemWidth = base.systemWidth;
            this.leftMargin = base.leftMargin;
            this.rightMargin = base.rightMargin;
            this.topMargin = base.topMargin;
            this.measureMinWidth = base.measureMinWidth;
            this.fontSize = base.fontSize;
            this.layoutMode = base.layoutMode;
            this.showClef = base.showClef;
            this.showKeySignature = base.showKeySignature;
            this.showTimeSignature = base.showTimeSignature;
            this.showTitleTexts = base.showTitleTexts;
        }

        public Builder staffLineGap(double v) {
            this.staffLineGap = v;
            return this;
        }

        public Builder staffSpacing(double v) {
            this.staffSpacing = v;
            return this;
        }

        public Builder systemWidth(double v) {
            this.systemWidth = v;
            return this;
        }

        public Builder leftMargin(double v) {
            this.leftMargin = v;
            return this;
        }

        public Builder rightMargin(double v) {
            this.rightMargin = v;
            return this;
        }

        public Builder topMargin(double v) {
            this.topMargin = v;
            return this;
        }

        public Builder measureMinWidth(double v) {
            this.measureMinWidth = v;
            return this;
        }

        public Builder fontSize(double v) {
            this.fontSize = v;
            return this;
        }

        public Builder layoutMode(LayoutMode mode) {
            this.layoutMode = mode == null ? LayoutMode.PAGE : mode;
            return this;
        }

        public Builder showClef(boolean v) {
            this.showClef = v;
            return this;
        }

        public Builder showKeySignature(boolean v) {
            this.showKeySignature = v;
            return this;
        }

        public Builder showTimeSignature(boolean v) {
            this.showTimeSignature = v;
            return this;
        }

        public Builder showTitleTexts(boolean v) {
            this.showTitleTexts = v;
            return this;
        }

        public LayoutOptions build() {
            return new LayoutOptions(staffLineGap, staffSpacing, systemWidth,
                    leftMargin, rightMargin, topMargin, measureMinWidth, fontSize,
                    layoutMode, showClef, showKeySignature, showTimeSignature, showTitleTexts);
        }
    }
}
