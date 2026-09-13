package land.temmi.rollercoaster.editor.protocol;

/**
 * Preview reports a click as a ground-plane (Y=0) intersection. Picking against the actual
 * terrain surface is Phase 3 work, once there is a document-backed map to pick against.
 */
public final class PickResult {
    public float worldX;
    public float worldY;
    public float worldZ;

    public PickResult() {
    }

    public PickResult(float worldX, float worldY, float worldZ) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
    }
}
