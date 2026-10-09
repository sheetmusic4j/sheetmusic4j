package com.sheetmusic4j.fxviewer;

import com.sheetmusic4j.core.model.Chord;
import com.sheetmusic4j.core.model.MusicElement;
import com.sheetmusic4j.core.model.Note;
import com.sheetmusic4j.core.model.Score;
import com.sheetmusic4j.engraving.Engraver;
import com.sheetmusic4j.engraving.glyph.Glyph;
import com.sheetmusic4j.engraving.layout.LayoutMode;
import com.sheetmusic4j.engraving.layout.LayoutOptions;
import com.sheetmusic4j.engraving.layout.LayoutResult;
import com.sheetmusic4j.engraving.layout.NoteAnchor;
import com.sheetmusic4j.engraving.layout.StaffLayout;
import com.sheetmusic4j.engraving.placement.GlyphPlacement;
import com.sheetmusic4j.render.RenderColor;
import com.sheetmusic4j.render.ScorePainter;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Play-along "fly-over" view: the {@link LayoutMode#STRIP} engraving of a
 * score lies on a lit sheet of paper in a 3D scene, a low perspective camera
 * glides along it following {@link #cursorTimeProperty() cursorTime}, and
 * every played notehead lights up in its staff's colour and floats above the
 * paper while it sounds.
 *
 * <p>The ink is the regular {@link ScorePainter} output, rendered once into
 * transparent image tiles (GPUs limit texture size, so a long strip is split
 * into {@value #TILE_WIDTH}-unit wide tiles) laid over a coloured paper plane
 * with a moving pool of light. The floating noteheads are copies drawn with
 * {@link ScorePainter#paintNotehead}; the engraved notehead stays on the
 * paper as the floating one's shadow, so beams and stems are never torn
 * apart. The float itself is a pure function of time ({@link JumpCurve}), so
 * the cursor can be scrubbed.
 *
 * <p>Every colour and size of the look is a property. Sizes are relative to
 * the score so one setting suits a single staff and a piano score alike:
 * camera and light distances are in multiples of the staff block height (top
 * line of the first staff to bottom line of the last, at least
 * {@value #MIN_STAFF_BLOCK} units), note sizes in staff line gaps.
 *
 * <p>Like {@link StripSheetView}, advancing the cursor never re-engraves or
 * repaints: it only moves the camera, the light and the noteheads in the air.
 * Requires {@link ConditionalFeature#SCENE3D}; see {@link #is3DSupported()}.
 *
 * <p>The scene uses layout units: the paper lies in the x/y plane at
 * {@code z = 0} with y pointing down the page, and "up" off the paper is
 * negative z.
 */
public final class FlyOverSheetView extends Region {

    /** Width of one ink tile, in layout units. */
    private static final double TILE_WIDTH = 1024;

    /** Size of a floating-notehead sprite, in staff line gaps (room for its glow). */
    private static final double SPRITE_GAPS = 4.0;

    /** Sprites are seen up close, so render them sharper than the paper. */
    private static final double SPRITE_OVERSAMPLE = 2.0;

    /** Smallest staff block height the camera and light distances scale with, in layout units. */
    private static final double MIN_STAFF_BLOCK = 120;

    /** How far the paper extends around the music, in staff block heights. */
    private static final double PAPER_MARGIN_BLOCKS = 20;

    private final Group world = new Group();
    private final Rectangle paper = new Rectangle();
    private final ImageView light = new ImageView();
    private final Group ink = new Group();
    private final Rectangle cursor = new Rectangle();
    private final Group poolLayer = new Group();
    private final Group noteLayer = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    /** 2D overlay fading the far distance (and a little of the foreground) into the background. */
    private final Rectangle fade = new Rectangle();

    // Camera rig: look-at target on the paper, orbit by yaw (around the paper
    // normal) and pitch (tilt away from straight down), then back off.
    private final Translate cameraTarget = new Translate();
    private final Rotate cameraYawRotate = new Rotate(0, Rotate.Z_AXIS);
    private final Rotate cameraPitchRotate = new Rotate(0, Rotate.X_AXIS);
    private final Translate cameraBack = new Translate();

    private final Engraver engraver = new Engraver();
    private final ScoreRenderer renderer = new ScoreRenderer();

    private final DoubleProperty cursorTime = new SimpleDoubleProperty(this, "cursorTime", 0.0);

    private final DoubleProperty cameraPitch = new SimpleDoubleProperty(this, "cameraPitch", 70.0);
    private final DoubleProperty cameraYaw = new SimpleDoubleProperty(this, "cameraYaw", 72.0);
    private final DoubleProperty cameraDistance = new SimpleDoubleProperty(this, "cameraDistance", 2.0);
    private final DoubleProperty lookAhead = new SimpleDoubleProperty(this, "lookAhead", 0.3);

    private final ObjectProperty<Color> backgroundColor =
            new SimpleObjectProperty<>(this, "backgroundColor", Color.web("#05040f"));
    private final ObjectProperty<Color> paperColor =
            new SimpleObjectProperty<>(this, "paperColor", Color.web("#2a22c8"));
    private final ObjectProperty<Color> inkColor =
            new SimpleObjectProperty<>(this, "inkColor", Color.web("#05030f"));
    private final ObjectProperty<Color> lightColor =
            new SimpleObjectProperty<>(this, "lightColor", Color.web("#ff3df0"));
    private final DoubleProperty lightRadius = new SimpleDoubleProperty(this, "lightRadius", 0.9);
    private final DoubleProperty lightAhead = new SimpleDoubleProperty(this, "lightAhead", 0.9);
    private final DoubleProperty distanceFade = new SimpleDoubleProperty(this, "distanceFade", 1.0);

    private final ObservableList<Color> noteColors = FXCollections.observableArrayList(
            Color.web("#ffb02e"), Color.web("#33e0ff"), Color.web("#f5f542"),
            Color.web("#ff6fae"), Color.web("#5cff8a"));
    private final DoubleProperty noteGlow = new SimpleDoubleProperty(this, "noteGlow", 0.5);
    private final DoubleProperty jumpHeight = new SimpleDoubleProperty(this, "jumpHeight", 1.5);

    private final ObjectProperty<Color> cursorColor =
            new SimpleObjectProperty<>(this, "cursorColor", Color.web("#ffffff", 0.5));
    private final BooleanProperty cursorVisible = new SimpleBooleanProperty(this, "cursorVisible", false);

    private final DoubleProperty stripSpacingFactor =
            new SimpleDoubleProperty(this, "stripSpacingFactor", LayoutOptions.DEFAULT_STRIP_SPACING_FACTOR);
    private final DoubleProperty textureScale = new SimpleDoubleProperty(this, "textureScale", 2.0);

    private final Map<SpriteKey, Image> sprites = new HashMap<>();
    private final Map<Color, Image> pools = new HashMap<>();
    private final List<Jumper> lifted = new ArrayList<>();

    private Score score;
    private LayoutResult layout;
    /** Jumpers sorted by onset, so a frame only visits the ones near the playhead. */
    private Jumper[] jumpers = new Jumper[0];
    /** Longest time any note stays off the paper, bounding the per-frame scan. */
    private double maxAirborne;
    private double staffCenterY;
    private double staffBlockHeight = MIN_STAFF_BLOCK;

    /** See {@link #setCursor}; NaN means "follow {@link LayoutResult#xAtTime}". */
    private double cursorLayoutX = Double.NaN;

    /**
     * Creates an empty fly-over view.
     */
    public FlyOverSheetView() {
        camera.setFieldOfView(40);
        camera.setNearClip(1);
        camera.setFarClip(100_000);
        camera.getTransforms().setAll(cameraTarget, cameraYawRotate, cameraPitchRotate, cameraBack);

        renderer.setPageColor(null);
        paper.fillProperty().bind(paperColor);
        cursor.fillProperty().bind(cursorColor);
        cursor.visibleProperty().bind(cursorVisible);
        fade.setMouseTransparent(true);

        // No depth buffer: everything is drawn in scene-graph order (paper,
        // light, ink, cursor, light pools, noteheads), which is exactly right
        // for things floating above a flat sheet and avoids z-fighting.
        world.getChildren().addAll(paper, light, ink, cursor, poolLayer, noteLayer, camera);
        subScene = new SubScene(world, 800, 450, false, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.fillProperty().bind(backgroundColor);
        getChildren().addAll(subScene, fade);

        cursorTime.addListener((obs, o, n) -> update());
        for (DoubleProperty p : List.of(cameraPitch, cameraYaw, cameraDistance, lookAhead,
                lightRadius, lightAhead, jumpHeight)) {
            p.addListener((obs, o, n) -> update());
        }
        lightColor.addListener((obs, o, n) -> updateLight());
        backgroundColor.addListener((obs, o, n) -> updateFade());
        distanceFade.addListener((obs, o, n) -> updateFade());
        noteColors.addListener((ListChangeListener<Color>) c -> repaintSprites());
        noteGlow.addListener((obs, o, n) -> repaintSprites());
        stripSpacingFactor.addListener((obs, o, n) -> rebuild());
        textureScale.addListener((obs, o, n) -> rebuild());
        inkColor.addListener((obs, o, n) -> rebuildInk());

        setMinSize(200, 150);
        setPrefSize(800, 450);
        updateLight();
        updateFade();
        update();
    }

    /**
     * @return whether this platform can render the 3D scene this view needs
     */
    public static boolean is3DSupported() {
        return Platform.isSupported(ConditionalFeature.SCENE3D);
    }

    /**
     * @return the currently displayed score, or {@code null}.
     */
    public Score getScore() {
        return score;
    }

    /**
     * Set the score to display. It is engraved in {@link LayoutMode#STRIP}
     * mode and rendered into the ink tiles; the cursor time is preserved.
     */
    public void setScore(Score score) {
        this.score = score;
        rebuild();
    }

    /**
     * The last engraved layout, or {@code null} if no score has been set.
     */
    public LayoutResult getLayout() {
        return layout;
    }

    /**
     * Total musical duration of the engraved score, in quarter notes.
     */
    public double totalDurationQuarters() {
        return layout == null ? 0.0 : layout.totalDurationQuarters();
    }

    /**
     * Musical time (in quarter notes) the camera follows and the noteheads
     * float to. Cheap to animate at 60 fps: only the camera, the light and the
     * noteheads in the air move.
     */
    public DoubleProperty cursorTimeProperty() {
        return cursorTime;
    }

    public double getCursorTime() {
        return cursorTime.get();
    }

    public void setCursorTime(double quarters) {
        cursorLayoutX = Double.NaN;
        cursorTime.set(quarters);
        update();
    }

    /**
     * Follow an explicit x position in layout units instead of
     * {@link LayoutResult#xAtTime} until the next {@link #setCursorTime}; the
     * noteheads still follow {@code quarters}. Same idea as
     * {@link StripSheetView#setCursorLayoutX}.
     *
     * @param layoutX  the x coordinate in layout units the camera follows
     * @param quarters the musical time the noteheads float to
     */
    public void setCursor(double layoutX, double quarters) {
        cursorLayoutX = layoutX;
        cursorTime.set(quarters);
        update();
    }

    /**
     * Camera tilt in degrees: {@code 0} looks straight down onto the paper,
     * {@code 90} skims along it.
     */
    public DoubleProperty cameraPitchProperty() {
        return cameraPitch;
    }

    /**
     * Camera swing around the paper normal in degrees: {@code 0} looks up the
     * page from below the staff, positive values move the camera back along
     * the music so the upcoming bars recede into the distance.
     */
    public DoubleProperty cameraYawProperty() {
        return cameraYaw;
    }

    /**
     * Distance from the camera to the point it looks at, in staff block
     * heights.
     */
    public DoubleProperty cameraDistanceProperty() {
        return cameraDistance;
    }

    /**
     * How far ahead of the cursor the camera looks, in staff block heights.
     */
    public DoubleProperty lookAheadProperty() {
        return lookAhead;
    }

    /**
     * Colour behind the paper, also used to fade the distance.
     */
    public ObjectProperty<Color> backgroundColorProperty() {
        return backgroundColor;
    }

    /**
     * Colour of the paper the ink lies on.
     */
    public ObjectProperty<Color> paperColorProperty() {
        return paperColor;
    }

    /**
     * Colour of the engraved ink (staff lines, stems, printed noteheads, ...).
     * Changing it re-renders the ink tiles.
     */
    public ObjectProperty<Color> inkColorProperty() {
        return inkColor;
    }

    /**
     * Colour of the pool of light that travels ahead of the cursor; fully
     * transparent switches it off.
     */
    public ObjectProperty<Color> lightColorProperty() {
        return lightColor;
    }

    /**
     * Radius of the pool of light, in staff block heights.
     */
    public DoubleProperty lightRadiusProperty() {
        return lightRadius;
    }

    /**
     * How far ahead of the cursor the pool of light sits, in staff block
     * heights.
     */
    public DoubleProperty lightAheadProperty() {
        return lightAhead;
    }

    /**
     * Strength of the fade into {@link #backgroundColorProperty()} at the far
     * (top) and near (bottom) edges of the view, from {@code 0} (off) to
     * {@code 1}. Also hides the shimmer of thin lines far away.
     */
    public DoubleProperty distanceFadeProperty() {
        return distanceFade;
    }

    /**
     * Colours of the floating noteheads, by staff: staff {@code i} of the
     * system uses entry {@code i % size}. Editing the list recolours the
     * noteheads; an empty list falls back to white.
     */
    public ObservableList<Color> noteColors() {
        return noteColors;
    }

    /**
     * Radius of the glow around a floating notehead, in staff line gaps;
     * {@code 0} switches the glow off.
     */
    public DoubleProperty noteGlowProperty() {
        return noteGlow;
    }

    /**
     * How high a sounding notehead floats above the paper, in staff line gaps.
     */
    public DoubleProperty jumpHeightProperty() {
        return jumpHeight;
    }

    /**
     * Colour of the cursor line drawn on the paper.
     */
    public ObjectProperty<Color> cursorColorProperty() {
        return cursorColor;
    }

    /**
     * Whether the cursor line is drawn (off by default: the floating notes
     * already show where the music is).
     */
    public BooleanProperty cursorVisibleProperty() {
        return cursorVisible;
    }

    /**
     * Horizontal spacing of the STRIP layout; see
     * {@link StripSheetView#stripSpacingFactorProperty()}. Changing it
     * re-engraves and re-renders the ink.
     */
    public DoubleProperty stripSpacingFactorProperty() {
        return stripSpacingFactor;
    }

    /**
     * Pixels per layout unit of the ink texture. Higher is sharper close to
     * the camera but costs memory; changing it re-renders the ink.
     */
    public DoubleProperty textureScaleProperty() {
        return textureScale;
    }

    @Override
    protected void layoutChildren() {
        subScene.setWidth(getWidth());
        subScene.setHeight(getHeight());
        fade.setWidth(getWidth());
        fade.setHeight(getHeight());
    }

    // ------------------------------------------------------------------
    // Building the scene
    // ------------------------------------------------------------------

    private void rebuild() {
        ink.getChildren().clear();
        poolLayer.getChildren().clear();
        noteLayer.getChildren().clear();
        lifted.clear();
        sprites.clear();
        pools.clear();
        jumpers = new Jumper[0];
        maxAirborne = 0;
        if (score == null) {
            layout = null;
            update();
            return;
        }
        LayoutOptions options = LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP)
                .showTitleTexts(false)
                .stripSpacingFactor(stripSpacingFactor.get())
                .build();
        layout = engraver.layout(score, options);
        measureStaffBlock(layout);
        buildPaper();
        buildInk();
        buildJumpers();
        updateLight();
        update();
    }

    private void measureStaffBlock(LayoutResult layout) {
        double top = Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (StaffLayout staff : layout.staves()) {
            top = Math.min(top, staff.y());
            bottom = Math.max(bottom, staff.y() + staff.lineGap() * 4);
        }
        if (top > bottom) {
            top = 0;
            bottom = layout.height();
        }
        staffCenterY = (top + bottom) / 2;
        staffBlockHeight = Math.max(bottom - top, MIN_STAFF_BLOCK);
    }

    /** A sheet that reaches well past the music in every direction, so its edges stay out of sight. */
    private void buildPaper() {
        double margin = staffBlockHeight * PAPER_MARGIN_BLOCKS;
        paper.setX(-margin);
        paper.setY(staffCenterY - margin);
        paper.setWidth(layout.width() + 2 * margin);
        paper.setHeight(2 * margin);
    }

    /**
     * Render the strip once into transparent image tiles laid side by side on
     * the paper. Each tile repaints the whole layout through a translated,
     * clipped canvas, which keeps {@link ScorePainter} the only drawing code.
     */
    private void rebuildInk() {
        if (layout != null) {
            ink.getChildren().clear();
            buildInk();
        }
    }

    private void buildInk() {
        double scale = Math.max(textureScale.get(), 0.25);
        double top = Math.min(0, layout.contentTop());
        double height = Math.max(layout.height(), layout.contentBottom()) - top;
        double width = layout.width();

        Canvas canvas = new Canvas();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        for (double x0 = 0; x0 < width; x0 += TILE_WIDTH) {
            // One unit of overlap hides the seam linear filtering leaves at tile edges.
            double tileWidth = Math.min(TILE_WIDTH + 1, width - x0);
            canvas.setWidth(Math.ceil(tileWidth * scale));
            canvas.setHeight(Math.ceil(height * scale));
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.save();
            gc.scale(scale, scale);
            gc.translate(-x0, -top);
            renderer.render(gc, layout, layout.width(), layout.height());
            gc.restore();
            // Recolour every inked pixel, keeping its coverage.
            gc.setGlobalBlendMode(BlendMode.SRC_ATOP);
            gc.setFill(inkColor.get());
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);

            ImageView tile = new ImageView(canvas.snapshot(params, null));
            tile.setSmooth(true);
            tile.setFitWidth(tileWidth);
            tile.setFitHeight(height);
            tile.setTranslateX(x0);
            tile.setTranslateY(top);
            ink.getChildren().add(tile);
        }

        cursor.setY(top);
        cursor.setHeight(height);
        cursor.setWidth(1.5);
    }

    /**
     * One floating copy per engraved notehead of a sounding note. Chord
     * members each get their own, timed by their own anchor.
     */
    private void buildJumpers() {
        Map<MusicElement, NoteAnchor> anchorByElement = new IdentityHashMap<>();
        for (NoteAnchor anchor : layout.noteAnchors()) {
            if (anchor.durationQuarters() > 0
                    && (anchor.elementRef() instanceof Note || anchor.elementRef() instanceof Chord)) {
                anchorByElement.putIfAbsent(anchor.elementRef(), anchor);
            }
        }
        List<Jumper> list = new ArrayList<>();
        for (StaffLayout staff : layout.staves()) {
            for (GlyphPlacement glyph : staff.glyphs()) {
                NoteAnchor anchor = glyph.elementRef() == null ? null : anchorByElement.get(glyph.elementRef());
                if (anchor == null || !isNotehead(glyph.glyph())) {
                    continue;
                }
                Jumper jumper = new Jumper(glyph, staff.lineGap(), anchor);
                paintJumper(jumper);
                list.add(jumper);
                maxAirborne = Math.max(maxAirborne, JumpCurve.airborne(jumper.duration));
                poolLayer.getChildren().add(jumper.pool);
                noteLayer.getChildren().add(jumper.view);
            }
        }
        list.sort(Comparator.comparingDouble(j -> j.onset));
        jumpers = list.toArray(new Jumper[0]);
    }

    private static boolean isNotehead(Glyph glyph) {
        return switch (glyph) {
            case NOTEHEAD_BLACK, NOTEHEAD_HALF, NOTEHEAD_WHOLE, NOTEHEAD_BREVE -> true;
            default -> false;
        };
    }

    private Color noteColor(int staffIndex) {
        if (noteColors.isEmpty()) {
            return Color.WHITE;
        }
        return noteColors.get(Math.floorMod(staffIndex, noteColors.size()));
    }

    private void paintJumper(Jumper jumper) {
        Color color = noteColor(jumper.staffIndex);
        jumper.view.setImage(sprites.computeIfAbsent(
                new SpriteKey(jumper.glyph, jumper.lineGap, color, noteGlow.get()), this::paintSprite));
        jumper.pool.setImage(pools.computeIfAbsent(color, c -> radialGlow(
                new Stop(0, c.deriveColor(0, 1, 1, 0.55)), new Stop(1, Color.TRANSPARENT))));
    }

    /** A glowing notehead, centred in a transparent square. */
    private Image paintSprite(SpriteKey key) {
        double size = key.lineGap() * SPRITE_GAPS;
        double px = Math.max(textureScale.get(), 0.25) * SPRITE_OVERSAMPLE;
        Canvas canvas = new Canvas(Math.ceil(size * px), Math.ceil(size * px));
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Color color = key.color();
        if (key.glow() > 0) {
            gc.setEffect(new DropShadow(BlurType.GAUSSIAN, color.deriveColor(0, 1, 1.2, 0.9),
                    Math.min(key.lineGap() * key.glow() * px, 127), 0.3, 0, 0));
        }
        gc.scale(px, px);
        FxRenderSurface surface = new FxRenderSurface(gc);
        RenderColor renderColor = NoteDecorations.toRenderColor(color).orElse(RenderColor.WHITE);
        surface.setFill(renderColor);
        surface.setStroke(renderColor);
        surface.setLineWidth(key.lineGap() * 0.15);
        ScorePainter.paintNotehead(surface, key.glyph(), size / 2, size / 2, key.lineGap());
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    /**
     * A radial gradient baked into an image: gradient-filled shapes don't
     * render reliably inside a 3D sub-scene, images do.
     */
    private static Image radialGlow(Stop... stops) {
        Canvas canvas = new Canvas(256, 256);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE, stops));
        gc.fillRect(0, 0, 256, 256);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private void repaintSprites() {
        sprites.clear();
        pools.clear();
        for (Jumper jumper : jumpers) {
            paintJumper(jumper);
        }
    }

    private void updateLight() {
        Color color = lightColor.get();
        light.setImage(radialGlow(
                new Stop(0, color.deriveColor(0, 1, 1, 0.95)),
                new Stop(0.35, color.deriveColor(0, 1, 1, 0.45)),
                new Stop(1, Color.TRANSPARENT)));
    }

    private void updateFade() {
        double strength = Math.clamp(distanceFade.get(), 0, 1);
        Color bg = backgroundColor.get();
        Paint fill = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, bg.deriveColor(0, 1, 1, strength)),
                new Stop(0.35, bg.deriveColor(0, 1, 1, 0)),
                new Stop(0.8, bg.deriveColor(0, 1, 1, 0)),
                new Stop(1, bg.deriveColor(0, 1, 1, 0.6 * strength)));
        fade.setFill(fill);
        fade.setVisible(strength > 0);
    }

    // ------------------------------------------------------------------
    // Per-frame update
    // ------------------------------------------------------------------

    private void update() {
        double time = cursorTime.get();
        double x = 0;
        if (layout != null) {
            x = Double.isNaN(cursorLayoutX) ? layout.xAtTime(time) : cursorLayoutX;
        }
        cursor.setX(x - cursor.getWidth() / 2);

        cameraTarget.setX(x + lookAhead.get() * staffBlockHeight);
        cameraTarget.setY(staffCenterY);
        cameraYawRotate.setAngle(cameraYaw.get());
        cameraPitchRotate.setAngle(cameraPitch.get());
        cameraBack.setZ(-cameraDistance.get() * staffBlockHeight);

        double radius = Math.max(lightRadius.get(), 0) * staffBlockHeight;
        light.setFitWidth(2 * radius);
        light.setFitHeight(2 * radius);
        light.setTranslateX(x + lightAhead.get() * staffBlockHeight - radius);
        light.setTranslateY(staffCenterY - staffBlockHeight * 0.3 - radius);

        updateJumpers(time);
    }

    private void updateJumpers(double time) {
        for (Jumper jumper : lifted) {
            jumper.land();
        }
        lifted.clear();
        double yaw = cameraYaw.get();
        double pitch = cameraPitch.get();
        for (int i = firstOnsetAtOrAfter(time - maxAirborne);
             i < jumpers.length && jumpers[i].onset <= time; i++) {
            Jumper jumper = jumpers[i];
            double lift = JumpCurve.lift(time, jumper.onset, jumper.duration);
            if (lift > 0) {
                jumper.lift(lift, jumpHeight.get(), yaw, pitch);
                lifted.add(jumper);
            }
        }
    }

    private int firstOnsetAtOrAfter(double time) {
        int lo = 0;
        int hi = jumpers.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (jumpers[mid].onset < time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private record SpriteKey(Glyph glyph, double lineGap, Color color, double glow) {
    }

    /**
     * A notehead copy that floats above the paper, plus the pool of its
     * light on the paper below it.
     */
    private static final class Jumper {

        final Glyph glyph;
        final double lineGap;
        final int staffIndex;
        final double onset;
        final double duration;
        final ImageView view = new ImageView();
        final ImageView pool = new ImageView();
        final Translate position;
        final Rotate yaw = new Rotate(0, Rotate.Z_AXIS);
        final Rotate pitch = new Rotate(0, Rotate.X_AXIS);

        Jumper(GlyphPlacement glyph, double lineGap, NoteAnchor anchor) {
            this.glyph = glyph.glyph();
            this.lineGap = lineGap;
            this.staffIndex = anchor.staffIndex();
            this.onset = anchor.onsetQuarters();
            this.duration = anchor.durationQuarters();
            double size = lineGap * SPRITE_GAPS;
            view.setSmooth(true);
            view.setFitWidth(size);
            view.setFitHeight(size);
            position = new Translate(glyph.x(), glyph.y(), 0);
            // Rise, then turn to face the camera, around the notehead centre.
            view.getTransforms().setAll(position, yaw, pitch, new Translate(-size / 2, -size / 2));
            pool.setFitWidth(lineGap * 3.2);
            pool.setFitHeight(lineGap * 2.4);
            pool.setTranslateX(glyph.x() - lineGap * 1.6);
            pool.setTranslateY(glyph.y() - lineGap * 1.2);
            land();
        }

        void lift(double lift, double heightGaps, double cameraYaw, double cameraPitch) {
            position.setZ(-lift * heightGaps * lineGap);
            yaw.setAngle(cameraYaw * lift);
            pitch.setAngle(cameraPitch * lift);
            view.setVisible(true);
            pool.setOpacity(lift);
            pool.setVisible(true);
        }

        void land() {
            view.setVisible(false);
            pool.setVisible(false);
        }
    }
}
