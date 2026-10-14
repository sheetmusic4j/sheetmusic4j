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
import com.sheetmusic4j.engraving.layout.PlaybackTimeline;
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
import javafx.scene.Node;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Play-along "fly-over" view: the {@link LayoutMode#STRIP} engraving of a
 * score lies on a lit sheet of paper in a 3D scene, a low perspective camera
 * glides along it following {@link #cursorTimeProperty() cursorTime}, and
 * a glowing ball per voice hops from note to note, landing on each one as it
 * starts; every played notehead lights up in its staff's colour while it
 * sounds.
 *
 * <p>The ink is the regular {@link ScorePainter} output, rendered into
 * transparent image {@link LayoutTiles tiles} of {@value #TILE_WIDTH} units
 * wide, laid over a coloured paper plane. Only the tiles within sight of the
 * camera exist (GPUs limit texture size and a whole long score in pixels
 * costs gigabytes); new ones are painted as the cursor moves. The texture
 * resolution is lowered when the tiles in sight would exceed
 * {@value #INK_PIXEL_BUDGET} pixels, which only happens for very tall scores.
 * A pool of light moves along the paper. The lit noteheads are glowing copies drawn
 * with {@link ScorePainter#paintNotehead} over the engraved ones. The hops and
 * the glow are pure functions of time ({@link JumpCurve}), so the cursor can
 * be scrubbed.
 *
 * <p>Every colour and size of the look is a property. Sizes are relative to
 * the score so one setting suits a single staff and a piano score alike:
 * camera and light distances are in multiples of the staff block height (top
 * line of the first staff to bottom line of the last, at least
 * {@value #MIN_STAFF_BLOCK} units), note sizes in staff line gaps.
 *
 * <p>Like {@link StripSheetView}, advancing the cursor never re-engraves: it
 * moves the camera, the light and the balls, and paints an ink tile when one
 * comes into sight.
 * Requires {@link ConditionalFeature#SCENE3D}; see {@link #is3DSupported()}.
 *
 * <p>The scene uses layout units: the paper lies in the x/y plane at
 * {@code z = 0} with y pointing down the page, and "up" off the paper is
 * negative z.
 */
public final class FlyOverSheetView extends Region {

    /** Width of one ink tile, in layout units. */
    private static final double TILE_WIDTH = 1024;

    /** Most ink pixels kept alive at once (4 bytes each, in the heap and on the GPU). */
    private static final double INK_PIXEL_BUDGET = 32_000_000;

    /** Tallest ink texture, in pixels, well below every GPU's texture limit. */
    private static final double MAX_INK_TEXTURE = 8192;

    /** Size of a lit-notehead sprite, in staff line gaps (room for its glow). */
    private static final double SPRITE_GAPS = 4.0;

    /** Sprites are seen up close, so render them sharper than the paper. */
    private static final double SPRITE_OVERSAMPLE = 2.0;

    /** Radius of a ball's solid core, as a fraction of its sprite size (the rest is glow). */
    private static final double BALL_CORE = 0.15;

    /** Smallest staff block height the camera and light distances scale with, in layout units. */
    private static final double MIN_STAFF_BLOCK = 120;

    /** How far the paper extends around the music, in staff block heights. */
    private static final double PAPER_MARGIN_BLOCKS = 20;

    private final Group world = new Group();
    private final Rectangle paper = new Rectangle();
    private final ImageView light = new ImageView();
    private final LayoutTiles ink = new LayoutTiles(this::paintInkTile);
    private final Canvas inkCanvas = new Canvas();
    private double inkTop;
    private double inkHeight;
    private double inkScale = 1;
    private final Rectangle cursor = new Rectangle();
    private final Group poolLayer = new Group();
    private final Group noteLayer = new Group();
    private final Group ballLayer = new Group();
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
    private final DoubleProperty jumpHeight = new SimpleDoubleProperty(this, "jumpHeight", 3.0);
    private final DoubleProperty ballSize = new SimpleDoubleProperty(this, "ballSize", 2.5);

    private final ObjectProperty<Color> cursorColor =
            new SimpleObjectProperty<>(this, "cursorColor", Color.web("#ffffff", 0.5));
    private final BooleanProperty cursorVisible = new SimpleBooleanProperty(this, "cursorVisible", false);

    private final DoubleProperty stripSpacingFactor =
            new SimpleDoubleProperty(this, "stripSpacingFactor", LayoutOptions.DEFAULT_STRIP_SPACING_FACTOR);
    private final DoubleProperty textureScale = new SimpleDoubleProperty(this, "textureScale", 2.0);

    private final Map<SpriteKey, Image> sprites = new HashMap<>();
    private final Map<Color, Image> pools = new HashMap<>();
    private final Map<Color, Image> balls = new HashMap<>();
    private final List<LitNote> lit = new ArrayList<>();

    private Score score;
    private LayoutResult layout;
    private PlaybackTimeline timeline;
    /** Lit notes sorted by onset, so a frame only visits the ones near the playhead. */
    private LitNote[] notes = new LitNote[0];
    /** Longest time any note keeps glowing, bounding the per-frame scan. */
    private double maxAfterglow;
    /** One bouncing ball per voice. */
    private Hopper[] hoppers = new Hopper[0];
    private double staffCenterY;
    private double staffBlockHeight = MIN_STAFF_BLOCK;

    /** See {@link #setCursor}; NaN means "follow {@link PlaybackTimeline#cursorX}". */
    private double cursorLayoutX = Double.NaN;

    /**
     * Creates an empty fly-over view.
     */
    public FlyOverSheetView() {
        camera.setFieldOfView(40);
        camera.setNearClip(1);
        camera.setFarClip(100_000);
        camera.getTransforms().setAll(cameraTarget, cameraYawRotate, cameraPitchRotate, cameraBack);

        renderer.painter().setPageColor(null);
        paper.fillProperty().bind(paperColor);
        cursor.fillProperty().bind(cursorColor);
        cursor.visibleProperty().bind(cursorVisible);
        fade.setMouseTransparent(true);

        // No depth buffer: everything is drawn in scene-graph order (paper,
        // light, ink, cursor, light pools, lit noteheads, balls), which is
        // exactly right for things above a flat sheet and avoids z-fighting.
        world.getChildren().addAll(paper, light, ink.group(), cursor, poolLayer, noteLayer, ballLayer, camera);
        subScene = new SubScene(world, 800, 450, false, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.fillProperty().bind(backgroundColor);
        getChildren().addAll(subScene, fade);

        cursorTime.addListener((obs, o, n) -> update());
        for (DoubleProperty p : List.of(cameraPitch, cameraYaw, cameraDistance, lookAhead,
                lightRadius, lightAhead, jumpHeight, ballSize)) {
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
     * Musical time (in quarter notes) the camera follows and the balls hop
     * to. Cheap to animate at 60 fps: only the camera, the light and the balls
     * move.
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
     * {@link PlaybackTimeline#cursorX} until the next {@link #setCursorTime}; the
     * balls still follow {@code quarters}. Same idea as
     * {@link StripSheetView#setCursorLayoutX}.
     *
     * @param layoutX  the x coordinate in layout units the camera follows
     * @param quarters the musical time the balls hop to
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
     * Colours of the balls and lit noteheads, by staff: staff {@code i} of the
     * system uses entry {@code i % size}. Editing the list recolours them; an
     * empty list falls back to white.
     */
    public ObservableList<Color> noteColors() {
        return noteColors;
    }

    /**
     * Radius of the glow around a lit notehead, in staff line gaps;
     * {@code 0} switches the glow off.
     */
    public DoubleProperty noteGlowProperty() {
        return noteGlow;
    }

    /**
     * How high a ball hops between two notes at least a quarter note apart,
     * in staff line gaps (shorter hops are lower).
     */
    public DoubleProperty jumpHeightProperty() {
        return jumpHeight;
    }

    /**
     * Size of a ball including its glow, in staff line gaps.
     */
    public DoubleProperty ballSizeProperty() {
        return ballSize;
    }

    /**
     * Colour of the cursor line drawn on the paper.
     */
    public ObjectProperty<Color> cursorColorProperty() {
        return cursorColor;
    }

    /**
     * Whether the cursor line is drawn (off by default: the balls
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
     * the camera but costs memory; changing it re-renders the ink. Lowered
     * automatically for very tall scores (see the class description).
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
        ink.reset(0, TILE_WIDTH);
        poolLayer.getChildren().clear();
        noteLayer.getChildren().clear();
        ballLayer.getChildren().clear();
        lit.clear();
        sprites.clear();
        pools.clear();
        balls.clear();
        notes = new LitNote[0];
        hoppers = new Hopper[0];
        maxAfterglow = 0;
        if (score == null) {
            layout = null;
            timeline = null;
            update();
            return;
        }
        LayoutOptions options = LayoutOptions.defaults().toBuilder()
                .layoutMode(LayoutMode.STRIP)
                .showTitleTexts(false)
                .stripSpacingFactor(stripSpacingFactor.get())
                .build();
        layout = engraver.layout(score, options);
        timeline = PlaybackTimeline.of(layout);
        measureStaffBlock(layout);
        buildPaper();
        buildInk();
        buildNotes();
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
     * Prepare the ink tiles for the current layout; they are painted on
     * demand by {@link #update()} as the camera moves.
     */
    private void rebuildInk() {
        if (layout != null) {
            buildInk();
            update();
        }
    }

    private void buildInk() {
        inkTop = Math.min(0, layout.contentTop());
        inkHeight = Math.max(layout.height(), layout.contentBottom()) - inkTop;
        double inSight = Math.min(2 * inkReach() + 3 * TILE_WIDTH, layout.width() + TILE_WIDTH);
        inkScale = Math.min(Math.max(textureScale.get(), 0.25),
                Math.min(MAX_INK_TEXTURE / inkHeight, Math.sqrt(INK_PIXEL_BUDGET / (inSight * inkHeight))));
        ink.reset(layout.width(), TILE_WIDTH);

        cursor.setY(inkTop);
        cursor.setHeight(inkHeight);
        cursor.setWidth(1.5);
    }

    /** How far from the cursor (in layout units) the camera can see ink; matches the paper's margin. */
    private double inkReach() {
        return staffBlockHeight * PAPER_MARGIN_BLOCKS;
    }

    /**
     * One ink tile: the painter's output for {@code [x0, x0 + width)}, its
     * ink recoloured, baked into an image (canvases don't render reliably in
     * the 3D sub-scene) and laid on the paper.
     */
    private Node paintInkTile(double x0, double width) {
        inkCanvas.setWidth(Math.ceil(width * inkScale));
        inkCanvas.setHeight(Math.ceil(inkHeight * inkScale));
        GraphicsContext gc = inkCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, inkCanvas.getWidth(), inkCanvas.getHeight());
        gc.save();
        gc.scale(inkScale, inkScale);
        gc.translate(-x0, -inkTop);
        renderer.renderRegion(gc, layout, x0, inkTop, x0 + width, inkTop + inkHeight);
        gc.restore();
        // Recolour every inked pixel, keeping its coverage.
        gc.setGlobalBlendMode(BlendMode.SRC_ATOP);
        gc.setFill(inkColor.get());
        gc.fillRect(0, 0, inkCanvas.getWidth(), inkCanvas.getHeight());
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        ImageView tile = new ImageView(inkCanvas.snapshot(params, null));
        tile.setSmooth(true);
        tile.setFitWidth(width);
        tile.setFitHeight(inkHeight);
        tile.setTranslateX(x0);
        tile.setTranslateY(inkTop);
        return tile;
    }

    /**
     * One glowing copy per engraved notehead of a sounding note (chord members
     * each get their own, timed by their own anchor), and one ball per voice
     * that lands on the top notehead at every onset of the voice.
     */
    private void buildNotes() {
        Map<MusicElement, NoteAnchor> anchorByElement = new IdentityHashMap<>();
        for (NoteAnchor anchor : timeline.soundingAnchors()) {
            anchorByElement.putIfAbsent(anchor.elementRef(), anchor);
        }
        List<LitNote> list = new ArrayList<>();
        Map<VoiceKey, Map<Double, Landing>> landingsByVoice = new LinkedHashMap<>();
        for (StaffLayout staff : layout.staves()) {
            for (GlyphPlacement glyph : staff.glyphs()) {
                NoteAnchor anchor = glyph.elementRef() == null ? null : anchorByElement.get(glyph.elementRef());
                if (anchor == null || !isNotehead(glyph.glyph())) {
                    continue;
                }
                LitNote note = new LitNote(glyph, staff.lineGap(), anchor);
                paintNote(note);
                list.add(note);
                maxAfterglow = Math.max(maxAfterglow, JumpCurve.afterglow(note.duration));
                poolLayer.getChildren().add(note.pool);
                noteLayer.getChildren().add(note.view);

                VoiceKey voice = new VoiceKey(anchor.partIndex(), anchor.staffIndex(), voiceOf(anchor.elementRef()));
                landingsByVoice.computeIfAbsent(voice, k -> new TreeMap<>())
                        .merge(anchor.onsetQuarters(),
                                new Landing(anchor.onsetQuarters(), anchor.durationQuarters(),
                                        glyph.x(), glyph.y(), staff.lineGap()),
                                Landing::higher);
            }
        }
        list.sort(Comparator.comparingDouble(n -> n.onset));
        notes = list.toArray(new LitNote[0]);

        List<Hopper> hopperList = new ArrayList<>();
        landingsByVoice.forEach((voice, landings) -> {
            Hopper hopper = new Hopper(voice.staffIndex(), List.copyOf(landings.values()));
            paintHopper(hopper);
            hopperList.add(hopper);
            poolLayer.getChildren().add(hopper.pool);
            ballLayer.getChildren().add(hopper.ball);
        });
        hoppers = hopperList.toArray(new Hopper[0]);
    }

    private static int voiceOf(MusicElement element) {
        if (element instanceof Note note) {
            return note.voice();
        }
        if (element instanceof Chord chord) {
            return chord.voice();
        }
        return 1;
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

    private void paintNote(LitNote note) {
        Color color = noteColor(note.staffIndex);
        note.view.setImage(sprites.computeIfAbsent(
                new SpriteKey(note.glyph, note.lineGap, color, noteGlow.get()), this::paintSprite));
        note.pool.setImage(pool(color));
    }

    private void paintHopper(Hopper hopper) {
        Color color = noteColor(hopper.staffIndex);
        // A white-hot core in the voice's colour, fading out into its glow.
        hopper.ball.setImage(balls.computeIfAbsent(color, c -> radialGlow(
                new Stop(0, Color.WHITE),
                new Stop(BALL_CORE * 2 * 0.6, c.deriveColor(0, 0.6, 1.4, 1)),
                new Stop(BALL_CORE * 2, c.deriveColor(0, 1, 1, 0.6)),
                new Stop(1, Color.TRANSPARENT))));
        hopper.pool.setImage(pool(color));
    }

    private Image pool(Color color) {
        return pools.computeIfAbsent(color, c -> radialGlow(
                new Stop(0, c.deriveColor(0, 1, 1, 0.55)), new Stop(1, Color.TRANSPARENT)));
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
        balls.clear();
        for (LitNote note : notes) {
            paintNote(note);
        }
        for (Hopper hopper : hoppers) {
            paintHopper(hopper);
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
        if (timeline != null) {
            x = Double.isNaN(cursorLayoutX) ? timeline.cursorX(time) : cursorLayoutX;
        }
        cursor.setX(x - cursor.getWidth() / 2);
        if (layout != null) {
            ink.show(x - inkReach(), x + inkReach());
        }

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

        updateNotes(time);
        double yaw = cameraYaw.get();
        double pitch = cameraPitch.get();
        for (Hopper hopper : hoppers) {
            hopper.update(time, jumpHeight.get(), ballSize.get(), yaw, pitch);
        }
    }

    private void updateNotes(double time) {
        for (LitNote note : lit) {
            note.dim();
        }
        lit.clear();
        for (int i = firstOnsetAtOrAfter(time - maxAfterglow);
             i < notes.length && notes[i].onset <= time; i++) {
            LitNote note = notes[i];
            double glow = JumpCurve.glow(time, note.onset, note.duration);
            if (glow > 0) {
                note.light(glow);
                lit.add(note);
            }
        }
    }

    private int firstOnsetAtOrAfter(double time) {
        int lo = 0;
        int hi = notes.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (notes[mid].onset < time) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private record SpriteKey(Glyph glyph, double lineGap, Color color, double glow) {
    }

    /** A ball's voice: each (part, staff, voice) is its own timeline. */
    private record VoiceKey(int partIndex, int staffIndex, int voice) {
    }

    /** Where and when a ball touches down: the top notehead of a voice at one onset. */
    private record Landing(double onset, double duration, double x, double y, double lineGap) {

        static Landing higher(Landing a, Landing b) {
            Landing top = b.y < a.y ? b : a;
            return new Landing(a.onset, Math.max(a.duration, b.duration), top.x, top.y, top.lineGap);
        }
    }

    /**
     * A glowing notehead copy lying on the engraved one, plus the pool of its
     * light on the paper.
     */
    private static final class LitNote {

        final Glyph glyph;
        final double lineGap;
        final int staffIndex;
        final double onset;
        final double duration;
        final ImageView view = new ImageView();
        final ImageView pool = new ImageView();

        LitNote(GlyphPlacement glyph, double lineGap, NoteAnchor anchor) {
            this.glyph = glyph.glyph();
            this.lineGap = lineGap;
            this.staffIndex = anchor.staffIndex();
            this.onset = anchor.onsetQuarters();
            this.duration = anchor.durationQuarters();
            double size = lineGap * SPRITE_GAPS;
            view.setSmooth(true);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setTranslateX(glyph.x() - size / 2);
            view.setTranslateY(glyph.y() - size / 2);
            pool.setFitWidth(lineGap * 3.2);
            pool.setFitHeight(lineGap * 2.4);
            pool.setTranslateX(glyph.x() - lineGap * 1.6);
            pool.setTranslateY(glyph.y() - lineGap * 1.2);
            dim();
        }

        void light(double glow) {
            view.setOpacity(glow);
            view.setVisible(true);
            pool.setOpacity(glow);
            pool.setVisible(true);
        }

        void dim() {
            view.setVisible(false);
            pool.setVisible(false);
        }
    }

    /**
     * The ball of one voice: it lands on every note of the voice at its onset
     * and hops along a parabola to the next, casting a pool of light on the
     * paper below it. It appears on the first note and disappears once the
     * last one has ended.
     */
    private static final class Hopper {

        final int staffIndex;
        final double[] onsets;
        final Landing[] landings;
        final double end;
        final ImageView ball = new ImageView();
        final ImageView pool = new ImageView();
        final Translate position = new Translate();
        final Rotate yaw = new Rotate(0, Rotate.Z_AXIS);
        final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
        final Translate centre = new Translate();

        Hopper(int staffIndex, List<Landing> landings) {
            this.staffIndex = staffIndex;
            this.landings = landings.toArray(new Landing[0]);
            this.onsets = landings.stream().mapToDouble(Landing::onset).toArray();
            Landing last = this.landings[this.landings.length - 1];
            this.end = last.onset() + last.duration();
            ball.setSmooth(true);
            // Turn to face the camera around the ball's centre.
            ball.getTransforms().setAll(position, yaw, pitch, centre);
            hide();
        }

        void update(double time, double heightGaps, double sizeGaps, double cameraYaw, double cameraPitch) {
            int i = Arrays.binarySearch(onsets, time);
            i = i >= 0 ? i : -i - 2;
            if (i < 0 || time > end) {
                hide();
                return;
            }
            Landing from = landings[i];
            Landing to = i + 1 < landings.length ? landings[i + 1] : from;
            double s = JumpCurve.progress(time, from.onset(), to.onset());
            double height = JumpCurve.height(time, from.onset(), to.onset());
            double x = from.x() + (to.x() - from.x()) * s;
            double y = from.y() + (to.y() - from.y()) * s;
            double lineGap = from.lineGap() + (to.lineGap() - from.lineGap()) * s;

            double size = Math.max(sizeGaps, 0) * lineGap;
            ball.setFitWidth(size);
            ball.setFitHeight(size);
            centre.setX(-size / 2);
            centre.setY(-size / 2);
            position.setX(x);
            position.setY(y);
            // Resting on the paper, the core touches it rather than sinking in.
            position.setZ(-size * BALL_CORE - height * heightGaps * lineGap);
            yaw.setAngle(cameraYaw);
            pitch.setAngle(cameraPitch);
            ball.setVisible(size > 0);

            pool.setFitWidth(lineGap * 3.2);
            pool.setFitHeight(lineGap * 2.4);
            pool.setTranslateX(x - lineGap * 1.6);
            pool.setTranslateY(y - lineGap * 1.2);
            pool.setOpacity(1 - 0.7 * height);
            pool.setVisible(true);
        }

        void hide() {
            ball.setVisible(false);
            pool.setVisible(false);
        }
    }
}
