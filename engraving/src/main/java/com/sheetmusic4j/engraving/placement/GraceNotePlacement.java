package com.sheetmusic4j.engraving.placement;

import java.util.List;

/**
 * One or more small ornamental grace notes immediately preceding a real
 * note, drawn at reduced size and connected to it with a curved line.
 * Consecutive grace notes share a single flat beam (with an acciaccatura
 * slash) instead of individual flags.
 *
 * @param noteX      x of each grace notehead, oldest (furthest from the
 *                   main note) first
 * @param noteY      y of each grace notehead, same order as {@link #noteX()}
 * @param beamY      y of the shared stem-top / beam line
 * @param mainNoteX  x of the main note the grace notes lead into
 * @param mainNoteY  y of the main note the grace notes lead into
 */
public record GraceNotePlacement(List<Double> noteX, List<Double> noteY, double beamY,
                                 double mainNoteX, double mainNoteY) {

    public GraceNotePlacement {
        noteX = List.copyOf(noteX);
        noteY = List.copyOf(noteY);
    }
}
