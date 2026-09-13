package land.temmi.rollercoaster.editor.document;

/**
 * A model placed on a map. The engine's own map format has no id for a prop - it is purely
 * positional at runtime - but the editor needs a stable one to select, move and delete a specific
 * instance across edits, so {@code instanceId} stays editor-side and is never exported.
 */
public final class MapProp {
    public final String instanceId;
    public final String modelId;
    public final float x;
    public final float z;
    public final float elevation;
    public final float rotation;

    public MapProp(String instanceId, String modelId, float x, float z, float elevation, float rotation) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Prop instance id is required");
        }
        if (modelId == null || modelId.trim().isEmpty()) throw new IllegalArgumentException("Prop model id is required");
        this.instanceId = instanceId;
        this.modelId = modelId;
        this.x = x;
        this.z = z;
        this.elevation = elevation;
        this.rotation = rotation;
    }
}
