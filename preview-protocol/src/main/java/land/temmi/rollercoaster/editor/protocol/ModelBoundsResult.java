package land.temmi.rollercoaster.editor.protocol;

/** Reply to {@link ComputeModelBounds}. A failed load carries a human-readable reason. */
public final class ModelBoundsResult {
    public boolean success;
    public String errorMessage;
    public float minX;
    public float minY;
    public float minZ;
    public float maxX;
    public float maxY;
    public float maxZ;

    public ModelBoundsResult() {
    }

    public static ModelBoundsResult ofBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        ModelBoundsResult result = new ModelBoundsResult();
        result.success = true;
        result.minX = minX;
        result.minY = minY;
        result.minZ = minZ;
        result.maxX = maxX;
        result.maxY = maxY;
        result.maxZ = maxZ;
        return result;
    }

    public static ModelBoundsResult ofError(String errorMessage) {
        ModelBoundsResult result = new ModelBoundsResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
