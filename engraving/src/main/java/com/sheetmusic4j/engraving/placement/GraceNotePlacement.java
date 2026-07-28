package com.sheetmusic4j.engraving.placement;

import java.util.List;

/**
 * One or more small ornamental grace notes immediately preceding a real
 * note, drawn at reduced size and connected to it with a curved line.
 * Consecutive grace notes share a single beam (with an acciaccatura slash)
 * instead of individual flags, raked (sloped) to follow the run's pitch
 * contour rather than sitting flat.
 *
 * @param noteX      x of each grace notehead, oldest (furthest from the
 *                   main note) first
 * @param noteY      y of each grace notehead, same order as {@link #noteX()}
 * @param stemTopY   y of each grace note's own stem-top / beam-line point,
 *                   same order as {@link #noteX()} - linearly interpolated
 *                   between the first and last note's own clearance, so the
 *                   beam follows the run's contour instead of being flat
 * @param mainNoteX  x of the main note the grace notes lead into
 * @param mainNoteY  y of the main note the grace notes lead into
 */
public record GraceNotePlacement(List<Double> noteX, List<Double> noteY, List<Double> stemTopY,
                                 double mainNoteX, double mainNoteY) {

    public GraceNotePlacement {
        noteX = List.copyOf(noteX);
        noteY = List.copyOf(noteY);
        stemTopY = List.copyOf(stemTopY);
    }
}
