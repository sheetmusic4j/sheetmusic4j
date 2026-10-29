package com.sheetmusic4j.pdf;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sheetmusic4j.core.io.ScoreReader;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.pdf.extract.PageExtractor;
import com.sheetmusic4j.pdf.recognize.ScoreRecognizer;

/**
 * Reads an <em>engraved</em> PDF: one that notation software (Finale, LilyPond, ...) exported with the music as
 * font glyphs and vector lines, so symbols can be read straight from the page instead of recognised from pixels.
 * Scanned PDFs hold only images and are rejected with a {@link PdfException}, as are PDFs set in a music font
 * this reader has no table for.
 *
 * <p>Supported fonts: Maestro (Finale) and Emmentaler (LilyPond). What is read: staves, clefs, key and time
 * signatures, notes, chords, rests, dots, accidentals, stems/flags/beams for the duration, barlines and system
 * breaks. Not yet: tuplets, ties, slurs, articulations, dynamics, lyrics and text.
 */
public final class PdfScoreReader implements ScoreReader {

    private static final Logger LOG = LoggerFactory.getLogger(PdfScoreReader.class);

    @Override
    public Score read(InputStream in) {
        try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            ScoreRecognizer recognizer = new ScoreRecognizer();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                recognizer.addPage(PageExtractor.extract(document.getPage(i), i));
            }
            if (recognizer.staffCount() == 0) {
                throw new PdfException("No engraved staves found in this PDF. It is probably a scan, which would "
                        + "need optical music recognition.");
            }
            if (!recognizer.foundMusic()) {
                throw new PdfException("The PDF has staves but its music font is not supported "
                        + "(supported: Maestro, Emmentaler).");
            }
            if (recognizer.staffCountVaries()) {
                LOG.warn("The number of staves differs between systems; staves are matched by position, so parts "
                        + "of this score may be mixed up");
            }
            Score.Builder score = Score.builder();
            recognizer.parts().forEach(score::addPart);
            return score.build();
        } catch (IOException e) {
            throw new PdfException("Could not read the PDF: " + e.getMessage(), e);
        }
    }
}
