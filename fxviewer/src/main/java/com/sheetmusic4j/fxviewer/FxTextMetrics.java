package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.engraving.layout.TextMetrics;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Measures text with the JavaFX font {@link FxRenderSurface} draws it in,
 * so layout reserves exactly the room lyrics and labels take on screen.
 * Must be used on the JavaFX application thread (or after the toolkit has
 * started).
 */
public final class FxTextMetrics implements TextMetrics {

    private final String family = Font.getDefault().getFamily();
    private final Text probe = new Text();

    @Override
    public double width(String text, double fontSize) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        probe.setFont(Font.font(family, fontSize));
        probe.setText(text);
        return probe.getLayoutBounds().getWidth();
    }
}
