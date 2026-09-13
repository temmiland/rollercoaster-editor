package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to load a GLTF/GLB file and report its bounding box. Computing this
 * needs a real Model/Mesh, which needs a live GL context - the editor's Swing process never has
 * one, so this has to be a round trip to the preview process instead of a local computation.
 */
public final class ComputeModelBounds {
    public String modelFilePath;

    public ComputeModelBounds() {
    }

    public ComputeModelBounds(String modelFilePath) {
        this.modelFilePath = modelFilePath;
    }
}
