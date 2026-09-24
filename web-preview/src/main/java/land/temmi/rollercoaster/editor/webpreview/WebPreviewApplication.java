package land.temmi.rollercoaster.editor.webpreview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import land.temmi.rollercoaster.actor.Facing;
import land.temmi.rollercoaster.asset.ModelCatalog;
import land.temmi.rollercoaster.asset.SpriteAtlas;
import land.temmi.rollercoaster.asset.SpriteDefinition;
import land.temmi.rollercoaster.asset.SpriteManifest;
import land.temmi.rollercoaster.render.BillboardQuad;
import land.temmi.rollercoaster.render.BillboardRenderer;
import land.temmi.rollercoaster.render.DayNightCycle;
import land.temmi.rollercoaster.render.LightingEnvironment;
import land.temmi.rollercoaster.render.PointLightSource;
import land.temmi.rollercoaster.render.WorldShaderProvider;
import land.temmi.rollercoaster.world.MapEntity;
import land.temmi.rollercoaster.world.MapLight;
import land.temmi.rollercoaster.world.TerrainSurface;
import land.temmi.rollercoaster.world.TextureTileset;
import land.temmi.rollercoaster.world.WorldScene;
import land.temmi.rollercoaster.world.WorldSceneLoader;
import org.teavm.jso.JSObject;

/** Renders whatever map the embedding editor sends, through the game's own loaders and shaders. */
final class WebPreviewApplication extends ApplicationAdapter {
    private PerspectiveCamera camera;
    private CameraInputController cameraController;
    private LightingEnvironment lighting;
    private DayNightCycle dayNightCycle;
    private ModelBatch modelBatch;
    private BillboardQuad billboardQuad;
    private final Array<ModelInstance> visibleInstances = new Array<>();
    private final Vector3 spriteRight = new Vector3();
    private Scene scene;

    /** Everything one shown map owns, so a replacement can be built before the old one is dropped. */
    private static final class Scene {
        TextureTileset tileset;
        ModelCatalog catalog;
        WorldScene world;
        SpriteAtlas spriteAtlas;
        final Array<BillboardRenderer> sprites = new Array<>();

        void dispose() {
            for (BillboardRenderer sprite : sprites) sprite.dispose();
            if (spriteAtlas != null) spriteAtlas.dispose();
            if (world != null) world.dispose();
            if (catalog != null) catalog.dispose();
            if (tileset != null) tileset.dispose();
        }
    }

    @Override
    public void create() {
        camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;
        cameraController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(cameraController);
        lighting = new LightingEnvironment();
        dayNightCycle = new DayNightCycle(lighting).setTimeOfDay(12f).setPaused(true).enterMap();
        modelBatch = new ModelBatch(new WorldShaderProvider(lighting));
        billboardQuad = new BillboardQuad();
        EditorBridge.listen(this::onMessage);
        EditorBridge.reply("ready", null);
    }

    private void onMessage(JSObject data) {
        String type = EditorBridge.string(data, "type");
        if ("showMap".equals(type)) {
            MemoryFiles files = EditorBridge.files(data);
            String map = EditorBridge.string(data, "map");
            String tileset = EditorBridge.string(data, "tileset");
            String models = EditorBridge.string(data, "models");
            String sprites = EditorBridge.string(data, "sprites");
            Gdx.app.postRunnable(() -> EditorBridge.reply("showMapResult", showMap(files, map, tileset, models, sprites)));
        } else if ("setTimeOfDay".equals(type)) {
            float hours = Float.parseFloat(EditorBridge.string(data, "hours"));
            Gdx.app.postRunnable(() -> dayNightCycle.setTimeOfDay(hours).enterMap());
        }
    }

    /** Returns null on success, otherwise the error; a failed map leaves the previous one shown. */
    private String showMap(MemoryFiles files, String map, String tileset, String models, String sprites) {
        Scene next = new Scene();
        try {
            if (map == null || tileset == null) throw new IllegalArgumentException("Map and tileset paths are required");
            next.tileset = new TextureTileset(files.get(tileset));
            next.catalog = loadModelCatalog(files, models);
            next.world = new WorldSceneLoader().load(files.get(map), next.tileset.getTileset(),
                next.tileset.createMaterial(), next.catalog);
            if (sprites != null) loadSprites(files.get(sprites), next);
        } catch (RuntimeException e) {
            next.dispose();
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
        boolean first = scene == null;
        if (scene != null) scene.dispose();
        scene = next;
        applyLights(next.world.getMap().lights);
        if (first) frame(next.world);
        return null;
    }

    private static ModelCatalog loadModelCatalog(MemoryFiles files, String models) {
        return models == null ? new ModelCatalog() : ModelCatalog.load(files.get(models));
    }

    private void loadSprites(FileHandle manifestFile, Scene next) {
        SpriteManifest manifest = SpriteManifest.load(manifestFile);
        next.spriteAtlas = SpriteAtlas.load(manifestFile, manifest);
        TerrainSurface surface = new TerrainSurface(next.world.getMap().tiles);
        for (MapEntity entity : next.world.getMap().entities) {
            if (entity.sprite == null) continue;
            SpriteDefinition definition = manifest.sprite(entity.sprite);
            BillboardRenderer sprite = new BillboardRenderer(billboardQuad, next.spriteAtlas.getTexture(),
                next.spriteAtlas.region(definition.idleRegion(Facing.SOUTH)), definition.worldHeight);
            sprite.setBottomPadding(definition.footOffset);
            sprite.setPosition(entity.x - 0.5f, surface.heightAt(entity.x - 0.5f, entity.z - 0.5f), entity.z - 0.5f);
            next.sprites.add(sprite);
        }
    }

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

    private void frame(WorldScene world) {
        float cx = world.getMap().tiles.getWidth() / 2f;
        float cz = world.getMap().tiles.getDepth() / 2f;
        float distance = Math.max(world.getMap().tiles.getWidth(), world.getMap().tiles.getDepth()) * 0.45f + 2f;
        camera.position.set(cx + distance, distance, cz + distance);
        camera.lookAt(cx, 0f, cz);
        camera.up.set(Vector3.Y);
        camera.update();
        cameraController.target.set(cx, 0f, cz);
    }

    @Override
    public void render() {
        dayNightCycle.update(Gdx.graphics.getDeltaTime());
        cameraController.update();
        Gdx.gl.glClearColor(0.1f, 0.12f, 0.16f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (scene == null) return;
        modelBatch.begin(camera);
        modelBatch.render(scene.world.getVisibleInstances(camera, visibleInstances));
        spriteRight.set(camera.direction).crs(camera.up).nor();
        for (BillboardRenderer sprite : scene.sprites) {
            sprite.setBasis(spriteRight, camera.up);
            modelBatch.render(sprite);
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
        if (scene != null) scene.dispose();
        modelBatch.dispose();
        billboardQuad.dispose();
    }
}
