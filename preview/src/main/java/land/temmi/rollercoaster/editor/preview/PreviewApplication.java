package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import land.temmi.rollercoaster.asset.GltfModelFactory;
import land.temmi.rollercoaster.asset.ModelCatalog;
import land.temmi.rollercoaster.asset.ModelManifest;
import land.temmi.rollercoaster.asset.SpriteAtlas;
import land.temmi.rollercoaster.asset.SpriteDefinition;
import land.temmi.rollercoaster.asset.SpriteManifest;
import land.temmi.rollercoaster.editor.protocol.CameraMode;
import land.temmi.rollercoaster.editor.protocol.ComputeModelBounds;
import land.temmi.rollercoaster.editor.protocol.PickResult;
import land.temmi.rollercoaster.editor.protocol.SetCameraMode;
import land.temmi.rollercoaster.editor.protocol.ShowGenericScene;
import land.temmi.rollercoaster.editor.protocol.ShowMap;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;
import land.temmi.rollercoaster.editor.protocol.ShowSampleLevel;
import land.temmi.rollercoaster.render.DayNightCycle;
import land.temmi.rollercoaster.render.LightingEnvironment;
import land.temmi.rollercoaster.render.BillboardQuad;
import land.temmi.rollercoaster.render.BillboardRenderer;
import land.temmi.rollercoaster.render.LowResTarget;
import land.temmi.rollercoaster.render.PixelCamera;
import land.temmi.rollercoaster.render.PointLightSource;
import land.temmi.rollercoaster.render.WorldShaderProvider;
import land.temmi.rollercoaster.world.MapEntity;
import land.temmi.rollercoaster.world.MapLight;
import land.temmi.rollercoaster.world.TerrainSurface;
import land.temmi.rollercoaster.world.TileMap;
import land.temmi.rollercoaster.world.TileSurface;
import land.temmi.rollercoaster.world.Tileset;
import land.temmi.rollercoaster.world.TextureTileset;
import land.temmi.rollercoaster.world.WorldScene;
import land.temmi.rollercoaster.world.WorldSceneLoader;

import java.io.IOException;

/**
 * The preview window. Renders through the same ModelBatch/WorldShaderProvider/LightingEnvironment
 * pipeline as the game. Shows a neutral generic scene until the editor reports that a project is
 * open, then switches to example-game's testfeld map, until the editor asks it to show a specific
 * exported document map instead (ShowMap) - which takes over as the active scene until the next
 * scene-switch message.
 */
public final class PreviewApplication extends ApplicationAdapter {
    private static final float GENERIC_CAMERA_FAR = 200f;
    private static final float LEVEL_CAMERA_FAR = 150f;
    private static final float DOCUMENT_MAP_CAMERA_FAR = 200f;
    /** Mirrors example-game's own constant: the game camera frames one terrain level at this pixel height. */
    private static final float TERRAIN_LEVEL_PIXEL_HEIGHT = 48f;

    private enum SceneMode { GENERIC, SAMPLE_LEVEL, DOCUMENT_MAP }

    private final PreviewConnection connection;

    private PerspectiveCamera camera;
    private CameraInputController cameraController;
    private ClickPicker clickPicker;
    private ModelBatch modelBatch;
    private LightingEnvironment lighting;
    private DayNightCycle dayNightCycle;

    /** The same fixed-pitch camera and low-res pixel-art target the game itself renders through. */
    private LowResTarget lowRes;
    private PixelCamera pixelCamera;
    private SpriteBatch blitBatch;
    private CameraMode cameraMode = CameraMode.FREE;
    private final Vector3 followTarget = new Vector3();

    private Model genericModel;
    private final Array<ModelInstance> genericInstances = new Array<>();

    private WorldScene levelScene;
    private ModelCatalog levelModelCatalog;
    private final Array<ModelInstance> visibleInstances = new Array<>();

    private WorldScene documentScene;
    private ModelCatalog documentModelCatalog;
    private TextureTileset documentTextureTileset;
    private SpriteAtlas documentSpriteAtlas;
    private BillboardQuad billboardQuad;
    private final Array<BillboardRenderer> documentSprites = new Array<>();
    private final Vector3 spriteRight = new Vector3();

    private SceneMode sceneMode = SceneMode.GENERIC;

    public PreviewApplication(PreviewConnection connection) {
        this.connection = connection;
    }

    @Override
    public void create() {
        camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cameraController = new CameraInputController(camera);
        clickPicker = new ClickPicker(camera, this::onPick);
        InputMultiplexer inputMultiplexer = new InputMultiplexer(clickPicker, cameraController);
        Gdx.input.setInputProcessor(inputMultiplexer);

        lighting = new LightingEnvironment();
        dayNightCycle = new DayNightCycle(lighting).setSecondsPerDay(90f);
        modelBatch = new ModelBatch(new WorldShaderProvider(lighting));
        billboardQuad = new BillboardQuad();

        lowRes = new LowResTarget();
        lowRes.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        pixelCamera = new PixelCamera();
        pixelCamera.resize(lowRes.getWidth(), lowRes.getHeight());
        blitBatch = new SpriteBatch();

        buildGenericModel();
        showGenericScene();

        connection.watch(this::onMessage, () -> Gdx.app.postRunnable(() -> Gdx.app.exit()));
    }

    private void onPick(Vector3 worldHit) {
        // ClickPicker always raycasts through the free camera; in GAME mode that camera sits
        // frozen wherever it was left (cameraController.update() doesn't run), so a hit against
        // it would silently disagree with what's actually on screen.
        if (cameraMode != CameraMode.FREE) return;
        connection.send(new PickResult(worldHit.x, worldHit.y, worldHit.z));
    }

    private void onMessage(Object message) {
        if (message instanceof ShowSampleLevel) {
            Gdx.app.postRunnable(this::showLevelScene);
        } else if (message instanceof ShowGenericScene) {
            Gdx.app.postRunnable(this::showGenericScene);
        } else if (message instanceof ComputeModelBounds) {
            String path = ((ComputeModelBounds) message).modelFilePath;
            Gdx.app.postRunnable(() -> connection.send(ModelBoundsService.compute(path)));
        } else if (message instanceof ShowMap) {
            ShowMap request = (ShowMap) message;
            Gdx.app.postRunnable(() -> connection.send(showDocumentMap(request)));
        } else if (message instanceof SetCameraMode) {
            CameraMode mode = ((SetCameraMode) message).mode;
            Gdx.app.postRunnable(() -> cameraMode = mode);
        }
    }

    private void buildGenericModel() {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        BoxShapeBuilder.build(builder.part("ground", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(new Color(0.36f, 0.52f, 0.3f, 1f)))),
            0f, -0.25f, 0f, 10f, 0.5f, 10f);
        BoxShapeBuilder.build(builder.part("marker", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(new Color(0.75f, 0.4f, 0.2f, 1f)))),
            0f, 0.5f, 0f, 1f, 1f, 1f);
        genericModel = builder.end();
        genericInstances.add(new ModelInstance(genericModel));
    }

    private void showGenericScene() {
        sceneMode = SceneMode.GENERIC;
        clickPicker.setTerrainSurface(null);
        lighting.clearPointLights();
        camera.position.set(8f, 6f, 8f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.1f;
        camera.far = GENERIC_CAMERA_FAR;
        camera.update();
        cameraController.target.set(0f, 0f, 0f);
        followTarget.set(0f, 0f, 0f);
    }

    private void showLevelScene() {
        if (levelScene == null) {
            SampleScene.Loaded loaded = SampleScene.load();
            levelScene = loaded.scene();
            levelModelCatalog = loaded.catalog();
        }
        sceneMode = SceneMode.SAMPLE_LEVEL;
        lighting.clearPointLights();
        clickPicker.setTerrainSurface(new TerrainSurface(levelScene.getMap().tiles));
        camera.position.set(34f, 24f, 34f);
        camera.lookAt(12f, 1f, 12f);
        camera.near = 0.1f;
        camera.far = LEVEL_CAMERA_FAR;
        camera.update();
        cameraController.target.set(12f, 1f, 12f);
        followTarget.set(findFollowTarget(levelScene.getMap().entities,
            new TerrainSurface(levelScene.getMap().tiles), 12f, 12f));
    }

    /** Builds a real WorldScene from an exported map file - the tool that closes Phase 3's "does
     * this ramp actually let a player reach the plateau" question, not just a colored 2D grid. */
    private ShowMapResult showDocumentMap(ShowMap request) {
        TextureTileset nextTileset = null;
        ModelCatalog nextCatalog = null;
        WorldScene nextScene = null;
        SpriteAtlas nextSpriteAtlas = null;
        Array<BillboardRenderer> nextSprites = new Array<>();
        try {
            if (request.tilesetManifestFilePath == null) {
                throw new IllegalArgumentException("Map preview requires an exported tileset manifest");
            }
            nextTileset = new TextureTileset(new FileHandle(request.tilesetManifestFilePath));
            nextCatalog = loadModelCatalog(request.modelManifestFilePath);
            nextScene = new WorldSceneLoader().load(new FileHandle(request.mapFilePath), nextTileset.getTileset(),
                nextTileset.createMaterial(), nextCatalog);
            if (nextScene.getMap().lights.size > LightingEnvironment.MAX_POINT_LIGHTS) {
                throw new IllegalArgumentException("Map has " + nextScene.getMap().lights.size
                    + " lights, more than the engine's shared point/spot budget of "
                    + LightingEnvironment.MAX_POINT_LIGHTS);
            }
            if (request.spriteManifestFilePath != null) {
                FileHandle manifestFile = new FileHandle(request.spriteManifestFilePath);
                SpriteManifest manifest = SpriteManifest.load(manifestFile);
                nextSpriteAtlas = new SpriteAtlas(manifestFile.parent().child(manifest.atlas));
                TerrainSurface surface = new TerrainSurface(nextScene.getMap().tiles);
                for (land.temmi.rollercoaster.world.MapEntity entity : nextScene.getMap().entities) {
                    if (entity.sprite == null) continue;
                    SpriteDefinition definition = manifest.sprite(entity.sprite);
                    BillboardRenderer sprite = new BillboardRenderer(billboardQuad, nextSpriteAtlas.getTexture(),
                        nextSpriteAtlas.region(definition.idleRegion(land.temmi.rollercoaster.actor.Facing.SOUTH)),
                        definition.worldHeight);
                    sprite.setBottomPadding(definition.footOffset);
                    sprite.setPosition(entity.x - 0.5f, surface.heightAt(entity.x - 0.5f, entity.z - 0.5f), entity.z - 0.5f);
                    nextSprites.add(sprite);
                }
            } else {
                for (land.temmi.rollercoaster.world.MapEntity entity : nextScene.getMap().entities) {
                    if (entity.sprite != null) throw new IllegalArgumentException("Map entity '" + entity.id
                        + "' needs an exported sprite manifest");
                }
            }

            disposeDocumentAssets();
            documentScene = nextScene;
            documentModelCatalog = nextCatalog;
            documentTextureTileset = nextTileset;
            documentSpriteAtlas = nextSpriteAtlas;
            documentSprites.addAll(nextSprites);
            applyLights(nextScene.getMap().lights);

            sceneMode = SceneMode.DOCUMENT_MAP;
            clickPicker.setTerrainSurface(new TerrainSurface(nextScene.getMap().tiles));
            float cx = request.width / 2f;
            float cz = request.depth / 2f;
            float distance = Math.max(request.width, request.depth) * 1.2f + 6f;
            camera.position.set(cx + distance, distance * 0.8f, cz + distance);
            camera.lookAt(cx, 1f, cz);
            camera.near = 0.1f;
            camera.far = DOCUMENT_MAP_CAMERA_FAR;
            camera.update();
            cameraController.target.set(cx, 1f, cz);
            followTarget.set(findFollowTarget(nextScene.getMap().entities,
                new TerrainSurface(nextScene.getMap().tiles), cx, cz));
            return ShowMapResult.ok();
        } catch (RuntimeException e) {
            for (BillboardRenderer sprite : nextSprites) sprite.dispose();
            if (nextScene != null) nextScene.dispose();
            if (nextCatalog != null) nextCatalog.dispose();
            if (nextTileset != null) nextTileset.dispose();
            if (nextSpriteAtlas != null) nextSpriteAtlas.dispose();
            return ShowMapResult.ofError(e.getMessage());
        }
    }

    /** Replaces the shared LightingEnvironment's point/spot lights with the map's own - called
     * only after everything else about the new scene has already loaded successfully, so a
     * rejected ShowMap never leaves the currently visible scene's lighting half-changed. */
    private void applyLights(Array<MapLight> lights) {
        lighting.clearPointLights();
        for (MapLight light : lights) {
            PointLightSource source = new PointLightSource(light.x, light.y, light.z,
                new Color(light.colorR, light.colorG, light.colorB, 1f), light.intensity, light.range);
            source.enabled = light.enabled;
            if (light.spot) {
                source.setSpot(new Vector3(light.directionX, light.directionY, light.directionZ),
                    light.innerAngle, light.outerAngle);
            }
            lighting.addPointLight(source);
        }
    }

    /**
     * The game camera frames a "player" entity's foot position; the preview has no live player
     * movement (that's test mode's job), so it just frames wherever one is authored - by the same
     * {@code type == "player"} convention example-game itself looks up. Falls back to the free
     * camera's own look-at point when a map has no such entity yet.
     */
    private static Vector3 findFollowTarget(Array<MapEntity> entities, TerrainSurface surface,
                                            float fallbackX, float fallbackZ) {
        for (MapEntity entity : entities) {
            if ("player".equals(entity.type)) {
                float x = entity.x - 0.5f;
                float z = entity.z - 0.5f;
                return new Vector3(x, surface.heightAt(x, z), z);
            }
        }
        return new Vector3(fallbackX, surface.heightAt(fallbackX, fallbackZ), fallbackZ);
    }

    private static ModelCatalog loadModelCatalog(String modelManifestFilePath) {
        ModelCatalog catalog = new ModelCatalog();
        if (modelManifestFilePath == null) return catalog;
        FileHandle manifestFile = new FileHandle(modelManifestFilePath);
        for (land.temmi.rollercoaster.asset.ModelDefinition definition : ModelManifest.load(manifestFile)) {
            int separator = definition.source.indexOf(':');
            String relativePath = definition.source.substring(separator + 1);
            catalog.register(definition, new GltfModelFactory(definition, manifestFile.parent().child(relativePath)));
        }
        return catalog;
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        dayNightCycle.update(delta);

        if (cameraMode == CameraMode.GAME) {
            renderGameCamera();
        } else {
            renderFreeCamera();
        }
    }

    private void renderFreeCamera() {
        cameraController.update();

        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        renderSceneInstances(camera);
        modelBatch.end();
    }

    /** Same follow/pixel-snap/low-res pipeline as example-game's own render loop, minus live
     * player movement - the preview has no player controller, so this frames a static position. */
    private void renderGameCamera() {
        pixelCamera.follow(followTarget, TileMap.LEVEL_HEIGHT, TERRAIN_LEVEL_PIXEL_HEIGHT);
        pixelCamera.snapToPixelGrid(lowRes.getWidth(), lowRes.getHeight());

        lowRes.begin();
        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(pixelCamera.camera);
        renderSceneInstances(pixelCamera.camera);
        modelBatch.end();
        lowRes.end();

        lowRes.blitToScreen(blitBatch);
    }

    private void renderSceneInstances(PerspectiveCamera activeCamera) {
        switch (sceneMode) {
            case SAMPLE_LEVEL -> modelBatch.render(levelScene.getVisibleInstances(activeCamera, visibleInstances));
            case DOCUMENT_MAP -> {
                modelBatch.render(documentScene.getVisibleInstances(activeCamera, visibleInstances));
                spriteRight.set(activeCamera.direction).crs(activeCamera.up).nor();
                for (BillboardRenderer sprite : documentSprites) {
                    sprite.setBasis(spriteRight, activeCamera.up);
                    modelBatch.render(sprite);
                }
            }
            default -> modelBatch.render(genericInstances);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        lowRes.resize(width, height);
        pixelCamera.resize(lowRes.getWidth(), lowRes.getHeight());
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        genericModel.dispose();
        if (levelScene != null) {
            levelScene.dispose();
            levelModelCatalog.dispose();
        }
        if (documentScene != null) {
            disposeDocumentAssets();
        }
        billboardQuad.dispose();
        lowRes.dispose();
        blitBatch.dispose();
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }

    private void disposeDocumentAssets() {
        for (BillboardRenderer sprite : documentSprites) sprite.dispose();
        documentSprites.clear();
        if (documentScene != null) documentScene.dispose();
        if (documentModelCatalog != null) documentModelCatalog.dispose();
        if (documentTextureTileset != null) documentTextureTileset.dispose();
        if (documentSpriteAtlas != null) documentSpriteAtlas.dispose();
        documentScene = null;
        documentModelCatalog = null;
        documentTextureTileset = null;
        documentSpriteAtlas = null;
    }
}
