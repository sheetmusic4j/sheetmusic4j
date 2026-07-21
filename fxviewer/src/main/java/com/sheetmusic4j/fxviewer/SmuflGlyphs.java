package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.Glyph;

/**
 * Renderer-agnostic mapping from Sheet4j's {@link Glyph} enum to
 * <a href="https://www.smufl.org/">SMuFL</a> codepoints (as displayed by
 * <a href="https://github.com/steinbergmedia/bravura">Bravura</a>).
 *
 * <p>Codepoints from the SMuFL 1.4 Private Use Area range. Sources:
 * <ul>
 *   <li>{@code gClef} = {@code U+E050}</li>
 *   <li>{@code fClef} = {@code U+E062}</li>
 *   <li>{@code cClef} = {@code U+E05C}</li>
 *   <li>{@code timeSig0..9} = {@code U+E080..U+E089}</li>
 *   <li>{@code noteheadWhole} = {@code U+E0A2}</li>
 *   <li>{@code noteheadHalf} = {@code U+E0A3}</li>
 *   <li>{@code noteheadBlack} = {@code U+E0A4}</li>
 *   <li>{@code restWhole} = {@code U+E4E3}</li>
 *   <li>{@code restHalf} = {@code U+E4E4}</li>
 *   <li>{@code restQuarter} = {@code U+E4E5}</li>
 *   <li>{@code rest8th} = {@code U+E4E6}</li>
 * </ul>
 * All codepoints are in the Basic Multilingual Plane so they fit in a
 * single {@code char}.
 */
public final class SmuflGlyphs {

    private SmuflGlyphs() {
    }

    /**
     * Return the SMuFL codepoint (as a one-char string) for the given glyph,
     * or {@code null} if it has no font representation (staff lines, ledger
     * lines, stems - all drawn as primitives).
     */
    public static String codepoint(Glyph glyph) {
        return switch (glyph) {
            case CLEF_G -> "\uE050";
            case CLEF_F -> "\uE062";
            case CLEF_C -> "\uE05C";
            case NOTEHEAD_WHOLE -> "\uE0A2";
            case NOTEHEAD_HALF -> "\uE0A3";
            case NOTEHEAD_BLACK -> "\uE0A4";
            case REST_WHOLE -> "\uE4E3";
            case REST_HALF -> "\uE4E4";
            case REST_QUARTER -> "\uE4E5";
            case REST_EIGHTH -> "\uE4E6";
            case TIME_DIGIT_0 -> "\uE080";
            case TIME_DIGIT_1 -> "\uE081";
            case TIME_DIGIT_2 -> "\uE082";
            case TIME_DIGIT_3 -> "\uE083";
            case TIME_DIGIT_4 -> "\uE084";
            case TIME_DIGIT_5 -> "\uE085";
            case TIME_DIGIT_6 -> "\uE086";
            case TIME_DIGIT_7 -> "\uE087";
            case TIME_DIGIT_8 -> "\uE088";
            case TIME_DIGIT_9 -> "\uE089";
            default -> null;
        };
    }

    /**
     * Classpath location where the Bravura OTF is expected. When the file is
     * present, {@link RenderSurface#drawSmuflGlyph(String, double, double, double)}
     * implementations register it and switch to SMuFL codepoint rendering.
     * When absent, {@link ScorePainter} silently falls back to primitive
     * shapes.
     */
    public static final String BRAVURA_RESOURCE = "/fonts/Bravura.otf";

    /**
     * Nominal font-family name for the loaded Bravura font. Matches the value
     * exposed by both {@link javafx.scene.text.Font#getFamily()} and
     * {@link java.awt.Font#getFamily()} after registration.
     */
    public static final String BRAVURA_FAMILY = "Bravura";
}
