package land.temmi.rollercoaster.editor.document;

/** A tile-anchored warp to a spawn point on another map, with a stable instance id. */
public final class MapTransitionAsset {
    public final String instanceId;
    public final int x;
    public final int z;
    public final String targetMapId;
    public final int targetX;
    public final int targetZ;

    public MapTransitionAsset(String instanceId, int x, int z, String targetMapId, int targetX, int targetZ) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transition instance id is required");
        }
        if (targetMapId == null || targetMapId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transition target map id is required: " + instanceId);
        }
        this.instanceId = instanceId;
        this.x = x;
        this.z = z;
        this.targetMapId = targetMapId;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }
}
