package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.utils.Array;
import land.temmi.rollercoaster.asset.ModelCatalog;
import land.temmi.rollercoaster.render.DayNightCycle;
import land.temmi.rollercoaster.render.LightingEnvironment;
import land.temmi.rollercoaster.render.WorldShaderProvider;
import land.temmi.rollercoaster.world.WorldScene;

import java.io.IOException;

/**
 * The preview window. Renders through the same ModelBatch/WorldShaderProvider/LightingEnvironment
 * pipeline as the game, showing example-game's testfeld map until the editor has its own
 * document-backed scene to send instead.
 */
public final class PreviewApplication extends ApplicationAdapter {
    private final PreviewConnection connection;

    private PerspectiveCamera camera;
    private CameraInputController cameraController;
    private ModelBatch modelBatch;
    private LightingEnvironment lighting;
    private DayNightCycle dayNightCycle;
    private WorldScene scene;
    private ModelCatalog modelCatalog;
    private final Array<ModelInstance> visibleInstances = new Array<>();

    public PreviewApplication(PreviewConnection connection) {
        this.connection = connection;
    }

    @Override
    public void create() {
        camera = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(34f, 24f, 34f);
        camera.lookAt(12f, 1f, 12f);
        camera.near = 0.1f;
        camera.far = 150f;
        camera.update();

        cameraController = new CameraInputController(camera);
        cameraController.target.set(12f, 1f, 12f);
        Gdx.input.setInputProcessor(cameraController);

        lighting = new LightingEnvironment();
        dayNightCycle = new DayNightCycle(lighting).setSecondsPerDay(90f);
        modelBatch = new ModelBatch(new WorldShaderProvider(lighting));

        SampleScene.Loaded loaded = SampleScene.load();
        scene = loaded.scene();
        modelCatalog = loaded.catalog();

        connection.watch(() -> Gdx.app.postRunnable(() -> Gdx.app.exit()));
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        dayNightCycle.update(delta);
        cameraController.update();

        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(scene.getVisibleInstances(camera, visibleInstances));
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
        scene.dispose();
        modelCatalog.dispose();
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }
}
