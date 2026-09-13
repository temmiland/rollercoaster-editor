package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
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
import land.temmi.rollercoaster.editor.protocol.ComputeModelBounds;
import land.temmi.rollercoaster.editor.protocol.PickResult;
import land.temmi.rollercoaster.editor.protocol.ShowGenericScene;
import land.temmi.rollercoaster.editor.protocol.ShowMap;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;
import land.temmi.rollercoaster.editor.protocol.ShowSampleLevel;
import land.temmi.rollercoaster.render.DayNightCycle;
import land.temmi.rollercoaster.render.LightingEnvironment;
import land.temmi.rollercoaster.render.WorldShaderProvider;
import land.temmi.rollercoaster.world.TerrainSurface;
import land.temmi.rollercoaster.world.TileSurface;
import land.temmi.rollercoaster.world.Tileset;
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
    private static final float DOCUMENT_TILE_SIDE_TINT = 0.72f;

    private enum SceneMode { GENERIC, SAMPLE_LEVEL, DOCUMENT_MAP }

    private final PreviewConnection connection;

    private PerspectiveCamera camera;
    private CameraInputController cameraController;
    private ClickPicker clickPicker;
    private ModelBatch modelBatch;
    private LightingEnvironment lighting;
    private DayNightCycle dayNightCycle;

    private Model genericModel;
    private final Array<ModelInstance> genericInstances = new Array<>();

    private WorldScene levelScene;
    private ModelCatalog levelModelCatalog;
    private final Array<ModelInstance> visibleInstances = new Array<>();

    private WorldScene documentScene;
    private ModelCatalog documentModelCatalog;

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

        buildGenericModel();
        showGenericScene();

        connection.watch(this::onMessage, () -> Gdx.app.postRunnable(() -> Gdx.app.exit()));
    }

    private void onPick(Vector3 worldHit) {
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
        camera.position.set(8f, 6f, 8f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.1f;
        camera.far = GENERIC_CAMERA_FAR;
        camera.update();
        cameraController.target.set(0f, 0f, 0f);
    }

    private void showLevelScene() {
        if (levelScene == null) {
            SampleScene.Loaded loaded = SampleScene.load();
            levelScene = loaded.scene();
            levelModelCatalog = loaded.catalog();
        }
        sceneMode = SceneMode.SAMPLE_LEVEL;
        clickPicker.setTerrainSurface(new TerrainSurface(levelScene.getMap().tiles));
        camera.position.set(34f, 24f, 34f);
        camera.lookAt(12f, 1f, 12f);
        camera.near = 0.1f;
        camera.far = LEVEL_CAMERA_FAR;
        camera.update();
        cameraController.target.set(12f, 1f, 12f);
    }

    /** Builds a real WorldScene from an exported map file - the tool that closes Phase 3's "does
     * this ramp actually let a player reach the plateau" question, not just a colored 2D grid. */
    private ShowMapResult showDocumentMap(ShowMap request) {
        WorldScene previousScene = documentScene;
        ModelCatalog previousCatalog = documentModelCatalog;
        try {
            Tileset tileset = new Tileset();
            for (int i = 0; i < request.tileIds.length; i++) {
                tileset.add(new TileSurface(request.tileIds[i])
                    .setColor(colorFor(request.tileIds[i]), DOCUMENT_TILE_SIDE_TINT)
                    .setWalkable(request.tileWalkable[i]));
            }
            ModelCatalog catalog = loadModelCatalog(request.modelManifestFilePath);
            WorldScene scene = new WorldSceneLoader().load(
                new FileHandle(request.mapFilePath), tileset, new Material(), catalog);

            documentScene = scene;
            documentModelCatalog = catalog;
            if (previousScene != null) {
                previousScene.dispose();
                previousCatalog.dispose();
            }

            sceneMode = SceneMode.DOCUMENT_MAP;
            clickPicker.setTerrainSurface(new TerrainSurface(scene.getMap().tiles));
            float cx = request.width / 2f;
            float cz = request.depth / 2f;
            float distance = Math.max(request.width, request.depth) * 1.2f + 6f;
            camera.position.set(cx + distance, distance * 0.8f, cz + distance);
            camera.lookAt(cx, 1f, cz);
            camera.near = 0.1f;
            camera.far = DOCUMENT_MAP_CAMERA_FAR;
            camera.update();
            cameraController.target.set(cx, 1f, cz);
            return ShowMapResult.ok();
        } catch (RuntimeException e) {
            return ShowMapResult.ofError(e.getMessage());
        }
    }

    /** Matches the editor's own MapCanvas hash-to-color function, so a tile looks the same in the
     * 2D map view and the 3D preview even without a real texture atlas. */
    private static Color colorFor(String tileId) {
        int hue = Math.floorMod(tileId.hashCode(), 360);
        return new Color(0f, 0f, 0f, 1f).fromHsv(hue, 0.45f, 0.75f);
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
        cameraController.update();

        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        switch (sceneMode) {
            case SAMPLE_LEVEL -> modelBatch.render(levelScene.getVisibleInstances(camera, visibleInstances));
            case DOCUMENT_MAP -> modelBatch.render(documentScene.getVisibleInstances(camera, visibleInstances));
            default -> modelBatch.render(genericInstances);
        }
        modelBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
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
            documentScene.dispose();
            documentModelCatalog.dispose();
        }
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }
}
