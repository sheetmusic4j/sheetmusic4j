package com.sheetmusic4j.fxviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sheetmusic4j.core.model.Attributes;
import com.sheetmusic4j.core.model.Clef;
import com.sheetmusic4j.core.model.Duration;
import com.sheetmusic4j.core.model.KeySignature;
import com.sheetmusic4j.core.model.Measure;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.NoteType;
import com.sheetmusic4j.core.model.Part;
import com.sheetmusic4j.core.model.Pitch;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.core.model.Step;
import com.sheetmusic4j.core.model.TimeSignature;

import javafx.application.Platform;
import javafx.scene.Group;

/**
 * Builds the retained scene graph for a one-note score and checks that the note
 * gets its own group. Skipped when no JavaFX toolkit can start (headless CI).
 */
class NodeSheetViewTest {

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        } catch (Throwable unavailable) {
            Assumptions.abort("JavaFX toolkit unavailable: " + unavailable);
        }
        Assumptions.assumeTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
    }

    private static <T> T onFxThread(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(10, TimeUnit.SECONDS);
    }

    @Test
    void eachNoteHasItsOwnGroupAndSelectionAddsEffect() throws Exception {
        Note note = Note.builder().pitch(new Pitch(Step.C, 4))
                .duration(new Duration(1, 1)).type(NoteType.QUARTER).build();
        Measure.Builder m = Measure.builder(1).attributes(Attributes.builder()
                .divisions(1).keySignature(KeySignature.cMajor())
                .timeSignature(TimeSignature.fourFour()).clef(Clef.treble()).build());
        m.addElement(note);
        Score score = Score.builder().addPart(Part.builder("P1").addMeasure(m.build()).build()).build();

        onFxThread(() -> {
            NodeSheetView view = new NodeSheetView();
            view.setScore(score);

            Group group = view.nodeFor(note);
            assertNotNull(group);
            assertSame(note, group.getUserData());
            assertFalse(group.getChildren().isEmpty());
            assertEquals(null, group.getEffect());

            view.selectedElementProperty().set(note);
            assertNotNull(group.getEffect());
            view.selectedElementProperty().set(null);
            assertEquals(null, group.getEffect());
            assertTrue(view.prefWidth(-1) > 1);
            return null;
        });
    }
}
