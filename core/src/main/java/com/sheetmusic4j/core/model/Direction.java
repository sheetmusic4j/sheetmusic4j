package com.sheetmusic4j.core.model;

/**
 * A non-time-consuming musical marking positioned above or below the staff:
 * a {@link DirectionType.Words words} direction (e.g. "Andantino"), a
 * {@link DirectionType.Metronome metronome} mark, or a
 * {@link DirectionType.Dynamic dynamic}. Corresponds to the MusicXML
 * {@code <direction>} element.
 *
 * <p>Directions are appended to {@link Measure#elements()} in document order
 * and carry {@link Duration#ZERO}, so they never affect measure timing or
 * MIDI export.
 *
 * @param type      the concrete marking payload
 * @param placement above/below staff hint; {@link Placement#DEFAULT} lets the
 *                  engraver pick a sensible side
 */
public record Direction(DirectionType type, Placement placement) implements MusicElement {

    public Direction {
        if (type == null) {
            throw new IllegalArgumentException("Direction requires a type");
        }
        if (placement == null) {
            placement = Placement.DEFAULT;
        }
    }

    @Override
    public Duration duration() {
        return Duration.ZERO;
    }
}
