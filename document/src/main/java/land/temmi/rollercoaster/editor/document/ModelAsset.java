package land.temmi.rollercoaster.editor.document;

/**
 * An imported GLTF/GLB model, copied into the project's sources/models/ folder. Bounds come from
 * the real model geometry (computed by the preview process, which has the GL context needed to
 * build it) - height is derived from bounds and scale rather than stored separately, so it can
 * never drift out of sync the way the engine's own ModelDefinition validates against.
 */
public final class ModelAsset {
    public final String id;
    public final String fileName;
    public final boolean binary;
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;
    public final float scale;
    public final float boundsMinX;
    public final float boundsMinY;
    public final float boundsMinZ;
    public final float boundsMaxX;
    public final float boundsMaxY;
    public final float boundsMaxZ;
    public final int collisionMinX;
    public final int collisionMaxX;
    public final int collisionMinZ;
    public final int collisionMaxZ;
    public final boolean alignToSlope;
    public final boolean walkable;
    public final float walkHeight;

    public ModelAsset(String id, String fileName, boolean binary, float offsetX, float offsetY, float offsetZ,
                      float scale, float boundsMinX, float boundsMinY, float boundsMinZ,
                      float boundsMaxX, float boundsMaxY, float boundsMaxZ,
                      int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                      boolean alignToSlope, boolean walkable, float walkHeight) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Model id is required");
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model file name is required");
        }
        if (scale <= 0f) throw new IllegalArgumentException("Model scale must be positive: " + id);
        if (boundsMinX > boundsMaxX || boundsMinY > boundsMaxY || boundsMinZ > boundsMaxZ) {
            throw new IllegalArgumentException("Invalid model bounds: " + id);
        }
        if (collisionMinX > collisionMaxX || collisionMinZ > collisionMaxZ) {
            throw new IllegalArgumentException("Invalid model collision bounds: " + id);
        }
        if (walkable && walkHeight < 0f) throw new IllegalArgumentException("Invalid walk height: " + id);
        this.id = id;
        this.fileName = fileName;
        this.binary = binary;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.boundsMinX = boundsMinX;
        this.boundsMinY = boundsMinY;
        this.boundsMinZ = boundsMinZ;
        this.boundsMaxX = boundsMaxX;
        this.boundsMaxY = boundsMaxY;
        this.boundsMaxZ = boundsMaxZ;
        this.collisionMinX = collisionMinX;
        this.collisionMaxX = collisionMaxX;
        this.collisionMinZ = collisionMinZ;
        this.collisionMaxZ = collisionMaxZ;
        this.alignToSlope = alignToSlope;
        this.walkable = walkable;
        this.walkHeight = walkHeight;
    }

    /** A freshly imported model: identity offset, scale 1, a single-cell collision footprint at
     * the anchor - a safe starting point the user then widens once they see the model placed. */
    public static ModelAsset imported(String id, String fileName, boolean binary,
                                      float boundsMinX, float boundsMinY, float boundsMinZ,
                                      float boundsMaxX, float boundsMaxY, float boundsMaxZ) {
        return new ModelAsset(id, fileName, binary, 0f, 0f, 0f, 1f,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ,
            0, 0, 0, 0, false, false, 0f);
    }

    public float getHeight() {
        return (boundsMaxY - boundsMinY) * scale;
    }

    public String getSource() {
        return (binary ? "glb:" : "gltf:") + "models/" + fileName;
    }
}
