package com.sheetmusic4j.render;

import java.io.InputStream;

import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.glyph.GlyphMetrics;

/**
 * Renderer-agnostic mapping from Sheetmusic4J's {@link Glyph} enum to
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
 *   <li>{@code rest16th} = {@code U+E4E7}</li>
 *   <li>{@code rest32nd} = {@code U+E4E8}</li>
 *   <li>{@code rest64th} = {@code U+E4E9}</li>
 *   <li>{@code rest128th} = {@code U+E4EA}</li>
 * </ul>
 * All codepoints are in the Basic Multilingual Plane so they fit in a
 * single {@code char}.
 */
public final class SmuflGlyphs {

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
     * exposed by both {@code javafx.scene.text.Font#getFamily()} and
     * {@code java.awt.Font#getFamily()} after registration.
     */
    public static final String BRAVURA_FAMILY = "Bravura";

    private SmuflGlyphs() {
    }

    /**
     * Open the bundled Bravura font, or return {@code null} when it is not
     * on the classpath. Loading through this class keeps the font reachable
     * when this module runs on the module path, where its resources are
     * encapsulated.
     *
     * @return a new stream the caller must close, or {@code null}
     */
    public static InputStream openBravura() {
        return SmuflGlyphs.class.getResourceAsStream(BRAVURA_RESOURCE);
    }

    /**
     * Return the SMuFL codepoint (as a one-char string) for the given glyph,
     * or {@code null} if it has no font representation (staff lines, ledger
     * lines, stems - all drawn as primitives).
     *
     * @param glyph glyph to map
     * @return SMuFL codepoint string, or {@code null} if the glyph is not
     * backed by a SMuFL character
     */
    public static String codepoint(Glyph glyph) {
        return switch (glyph) {
            case CLEF_G -> "\uE050";
            case CLEF_F -> "\uE062";
            case CLEF_C -> "\uE05C";
            case CLEF_G_8VB -> "\uE052";
            case CLEF_G_8VA -> "\uE053";
            case CLEF_F_8VB -> "\uE064";
            case CLEF_C_8VB -> "\uE05D";
            case CLEF_PERCUSSION -> "\uE069";
            case NOTEHEAD_WHOLE -> "\uE0A2";
            case NOTEHEAD_HALF -> "\uE0A3";
            case NOTEHEAD_BLACK -> "\uE0A4";
            case NOTEHEAD_BREVE -> "\uE0A0";
            case FLAG_8TH_UP -> "\uE240";
            case FLAG_8TH_DOWN -> "\uE241";
            case FLAG_16TH_UP -> "\uE242";
            case FLAG_16TH_DOWN -> "\uE243";
            case FLAG_32ND_UP -> "\uE244";
            case FLAG_32ND_DOWN -> "\uE245";
            case FLAG_64TH_UP -> "\uE246";
            case FLAG_64TH_DOWN -> "\uE247";
            case FLAG_128TH_UP -> "\uE248";
            case FLAG_128TH_DOWN -> "\uE249";
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
            case REST_SIXTEENTH -> "\uE4E7";
            case REST_THIRTY_SECOND -> "\uE4E8";
            case REST_SIXTY_FOURTH -> "\uE4E9";
            case REST_128TH -> "\uE4EA";
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
            case ARTICULATION_ACCENT -> "\uE4A0";
            case ARTICULATION_STACCATO -> "\uE4A2";
            case ARTICULATION_DOWN_BOW -> "\uE610";
            case ARTICULATION_UP_BOW -> "\uE612";
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
        // 1 em = 4 staff spaces in SMuFL, so advance / 4 = fraction of the em/sizeHint.
        return GlyphMetrics.advanceWidth(glyph) * 0.5 * sizeHint / 4.0;
    }
}
