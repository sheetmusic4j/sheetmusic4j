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
 *   <li>{@code flag8thUp} = {@code U+E240}, {@code flag8thDown} = {@code U+E241}</li>
 *   <li>{@code flag16thUp} = {@code U+E242}, {@code flag16thDown} = {@code U+E243}</li>
 *   <li>{@code accidentalFlat} = {@code U+E260}, {@code accidentalNatural} = {@code U+E261}</li>
 *   <li>{@code accidentalSharp} = {@code U+E262}, {@code accidentalDoubleSharp} = {@code U+E263}</li>
 *   <li>{@code accidentalDoubleFlat} = {@code U+E264}</li>
 *   <li>{@code augmentationDot} = {@code U+E1E7}</li>
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
     *
     * @param glyph glyph to map
     * @return SMuFL codepoint string, or {@code null} if the glyph is not
     *         backed by a SMuFL character
     */
    public static String codepoint(Glyph glyph) {
        return switch (glyph) {
            case CLEF_G -> "\uE050";
            case CLEF_F -> "\uE062";
            case CLEF_C -> "\uE05C";
            case NOTEHEAD_WHOLE -> "\uE0A2";
            case NOTEHEAD_HALF -> "\uE0A3";
            case NOTEHEAD_BLACK -> "\uE0A4";
            case FLAG_8TH_UP -> "\uE240";
            case FLAG_8TH_DOWN -> "\uE241";
            case FLAG_16TH_UP -> "\uE242";
            case FLAG_16TH_DOWN -> "\uE243";
            case ACCIDENTAL_FLAT -> "\uE260";
            case ACCIDENTAL_NATURAL -> "\uE261";
            case ACCIDENTAL_SHARP -> "\uE262";
            case ACCIDENTAL_DOUBLE_SHARP -> "\uE263";
            case ACCIDENTAL_DOUBLE_FLAT -> "\uE264";
            case AUG_DOT -> "\uE1E7";
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
            case DYNAMIC_P -> "\uE520";
            case DYNAMIC_F -> "\uE522";
            case DYNAMIC_NIENTE -> "\uE526";
            case DYNAMIC_PPP -> "\uE52A";
            case DYNAMIC_PP -> "\uE52B";
            case DYNAMIC_MP -> "\uE52C";
            case DYNAMIC_MF -> "\uE52D";
            case DYNAMIC_FF -> "\uE52F";
            case DYNAMIC_FFF -> "\uE530";
            case DYNAMIC_FP -> "\uE534";
            case DYNAMIC_FZ -> "\uE535";
            case DYNAMIC_SF -> "\uE536";
            case DYNAMIC_SFZ -> "\uE539";
            case DYNAMIC_RF -> "\uE53C";
            case DYNAMIC_RFZ -> "\uE53D";
            case BRACE -> "\uE000";
            case BRACKET_TOP -> "\uE003";
            case BRACKET_BOTTOM -> "\uE004";
            default -> null;
            };
            }

    /**
     * Half of the SMuFL advance width for the given glyph, expressed in
     * layout units at the given {@code sizeHint} font size.
     *
     * <p>Advance widths taken from Bravura's {@code glyphAdvanceWidths.json}
     * (in staff spaces; 1 em = 4 staff spaces). Returned in the same units
     * as the layout so callers can shift a "center-anchored" glyph by
     * subtracting this from the target x.
     *
     * @param glyph    SMuFL-backed glyph
     * @param sizeHint font em-size the glyph will be drawn at
     * @return half advance width in layout units, or 0 for non-SMuFL glyphs
     */
    public static double halfAdvanceWidth(Glyph glyph, double sizeHint) {
        double staffSpaces = switch (glyph) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF -> 1.18;
            case NOTEHEAD_WHOLE -> 1.5;
            case TIME_DIGIT_0, TIME_DIGIT_1, TIME_DIGIT_2, TIME_DIGIT_3, TIME_DIGIT_4,
                    TIME_DIGIT_5, TIME_DIGIT_6, TIME_DIGIT_7, TIME_DIGIT_8, TIME_DIGIT_9 -> 1.4;
            case ACCIDENTAL_FLAT -> 0.9;
            case ACCIDENTAL_NATURAL -> 0.65;
            case ACCIDENTAL_SHARP -> 1.0;
            case ACCIDENTAL_DOUBLE_SHARP -> 1.05;
            case ACCIDENTAL_DOUBLE_FLAT -> 1.75;
            case AUG_DOT -> 0.4;
            case DYNAMIC_P, DYNAMIC_F, DYNAMIC_NIENTE -> 1.5;
            case DYNAMIC_PP, DYNAMIC_MP, DYNAMIC_MF, DYNAMIC_FF, DYNAMIC_FZ,
                    DYNAMIC_FP, DYNAMIC_SF, DYNAMIC_RF -> 2.4;
            case DYNAMIC_PPP, DYNAMIC_FFF, DYNAMIC_SFZ, DYNAMIC_RFZ -> 3.2;
            case BRACE -> 0.6;
            case BRACKET_TOP, BRACKET_BOTTOM -> 0.7;
            default -> 0.0;
            };
        // 1 em = 4 staff spaces in SMuFL, so staffSpaces / 4 = fraction of the em/sizeHint.
        return staffSpaces * 0.5 * sizeHint / 4.0;
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
