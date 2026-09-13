package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.collision.BoundingBox;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

import java.util.Locale;

/**
 * Loads a GLTF/GLB file and reports its bounding box, mirroring GltfModelFactory's own loading
 * logic exactly (same loader classes, same calculateBoundingBox call) so the bounds the editor
 * writes into a model manifest are the same ones the engine will later validate against.
 */
final class ModelBoundsService {
    private ModelBoundsService() {
    }

    static ModelBoundsResult compute(String modelFilePath) {
        String lower = modelFilePath.toLowerCase(Locale.ROOT);
        boolean binary = lower.endsWith(".glb");
        if (!binary && !lower.endsWith(".gltf")) {
            return ModelBoundsResult.ofError("Not a .gltf or .glb file: " + modelFilePath);
        }

        FileHandle file = new FileHandle(modelFilePath);
        SceneAsset asset;
        try {
            asset = binary ? new GLBLoader().load(file) : new GLTFLoader().load(file);
        } catch (RuntimeException e) {
            return ModelBoundsResult.ofError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        try {
            if (asset.scene == null || asset.scene.model == null) {
                return ModelBoundsResult.ofError("Model has no default scene: " + modelFilePath);
            }
            BoundingBox bounds = asset.scene.model.calculateBoundingBox(new BoundingBox());
            return ModelBoundsResult.ofBounds(bounds.min.x, bounds.min.y, bounds.min.z,
                bounds.max.x, bounds.max.y, bounds.max.z);
        } finally {
            asset.dispose();
        }
    }
}
