package land.temmi.rollercoaster.editor.document;

/** A placed point or spot light, with a stable instance id for editor selection and undo. */
public final class MapLightAsset {
    public final String instanceId;
    public final float x;
    public final float y;
    public final float z;
    public final float colorR;
    public final float colorG;
    public final float colorB;
    public final float intensity;
    public final float range;
    public final boolean enabled;
    public final boolean spot;
    public final float directionX;
    public final float directionY;
    public final float directionZ;
    public final float innerAngle;
    public final float outerAngle;

    /** A plain point light, upright and pointing nowhere in particular. */
    public MapLightAsset(String instanceId, float x, float y, float z, float colorR, float colorG, float colorB,
                         float intensity, float range, boolean enabled) {
        this(instanceId, x, y, z, colorR, colorG, colorB, intensity, range, enabled, false, 0f, -1f, 0f, 0f, 0f);
    }

    public MapLightAsset(String instanceId, float x, float y, float z, float colorR, float colorG, float colorB,
                         float intensity, float range, boolean enabled, boolean spot,
                         float directionX, float directionY, float directionZ, float innerAngle, float outerAngle) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Light instance id is required");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Light position must be finite: " + instanceId);
        }
        if (intensity < 0f) throw new IllegalArgumentException("Light intensity must be nonnegative: " + instanceId);
        if (range <= 0f) throw new IllegalArgumentException("Light range must be positive: " + instanceId);
        if (spot && (innerAngle < 0f || outerAngle <= innerAngle || outerAngle > 180f)) {
            throw new IllegalArgumentException("Invalid spot light cone: " + instanceId);
        }
        this.instanceId = instanceId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.intensity = intensity;
        this.range = range;
        this.enabled = enabled;
        this.spot = spot;
        this.directionX = directionX;
        this.directionY = directionY;
        this.directionZ = directionZ;
        this.innerAngle = innerAngle;
        this.outerAngle = outerAngle;
    }
}
