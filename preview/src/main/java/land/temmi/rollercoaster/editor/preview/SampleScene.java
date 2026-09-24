package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import land.temmi.rollercoaster.asset.ModelCatalog;
import land.temmi.rollercoaster.world.TileSurface;
import land.temmi.rollercoaster.world.Tileset;
import land.temmi.rollercoaster.world.WorldScene;
import land.temmi.rollercoaster.world.WorldSceneLoader;

/**
 * The same testfeld map example-game uses, until the editor has its own map/tileset authoring
 * (Phase 2/3) to build a document-backed scene instead.
 */
final class SampleScene {
    private static final float SIDE_TINT = 0.72f;

    private SampleScene() {
    }

    /** The model catalog outlives WorldScene.dispose() - props only reference it - so callers
     * must dispose it themselves once they are done with the scene. */
    record Loaded(WorldScene scene, ModelCatalog catalog) {
    }

    static Loaded load() {
        ModelCatalog catalog = ModelCatalog.load(Gdx.files.classpath("models/models.json"));
        WorldScene scene = new WorldSceneLoader().load(
            Gdx.files.classpath("maps/testfield.json"), createTileset(), new Material(), catalog);
        return new Loaded(scene, catalog);
    }

    private static Tileset createTileset() {
        return new Tileset()
            .add(tile("grass", new Color(0.34f, 0.58f, 0.25f, 1f)))
            .add(tile("lightGrass", new Color(0.38f, 0.63f, 0.28f, 1f)))
            .add(tile("path", new Color(0.76f, 0.65f, 0.43f, 1f)))
            .add(tile("plateau", new Color(0.47f, 0.64f, 0.30f, 1f)))
            .add(tile("ramp", new Color(0.70f, 0.60f, 0.40f, 1f)))
            .add(tile("rampBlocked", new Color(0.46f, 0.44f, 0.42f, 1f)).setWalkable(false));
    }

    private static TileSurface tile(String id, Color color) {
        return new TileSurface(id).setColor(color, SIDE_TINT);
    }
}
