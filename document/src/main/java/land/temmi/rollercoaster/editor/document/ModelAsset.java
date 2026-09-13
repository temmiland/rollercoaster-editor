package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

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
    private final List<String> dependencyFileNames;

    public ModelAsset(String id, String fileName, boolean binary, float offsetX, float offsetY, float offsetZ,
                      float scale, float boundsMinX, float boundsMinY, float boundsMinZ,
                      float boundsMaxX, float boundsMaxY, float boundsMaxZ,
                      int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                      boolean alignToSlope, boolean walkable, float walkHeight) {
        this(id, fileName, binary, offsetX, offsetY, offsetZ, scale,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ,
            collisionMinX, collisionMaxX, collisionMinZ, collisionMaxZ,
            alignToSlope, walkable, walkHeight, Collections.emptyList());
    }

    public ModelAsset(String id, String fileName, boolean binary, float offsetX, float offsetY, float offsetZ,
                      float scale, float boundsMinX, float boundsMinY, float boundsMinZ,
                      float boundsMaxX, float boundsMaxY, float boundsMaxZ,
                      int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                      boolean alignToSlope, boolean walkable, float walkHeight, List<String> dependencyFileNames) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Model id is required");
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model file name is required");
        }
        if (!isFileName(fileName)) throw new IllegalArgumentException("Model file name must not include a path: " + id);
        if (scale <= 0f) throw new IllegalArgumentException("Model scale must be positive: " + id);
        if (boundsMinX > boundsMaxX || boundsMinY > boundsMaxY || boundsMinZ > boundsMaxZ) {
            throw new IllegalArgumentException("Invalid model bounds: " + id);
        }
        if (collisionMinX > collisionMaxX || collisionMinZ > collisionMaxZ) {
            throw new IllegalArgumentException("Invalid model collision bounds: " + id);
        }
        if (walkable && walkHeight < 0f) throw new IllegalArgumentException("Invalid walk height: " + id);
        if (dependencyFileNames == null) throw new IllegalArgumentException("Model dependencies are required: " + id);
        LinkedHashSet<String> dependencyNames = new LinkedHashSet<>();
        for (String dependencyFileName : dependencyFileNames) {
            if (dependencyFileName == null || dependencyFileName.trim().isEmpty()) {
                throw new IllegalArgumentException("Model dependency file name is required: " + id);
            }
            if (!isFileName(dependencyFileName)) {
                throw new IllegalArgumentException("Model dependency file name must not include a path: " + id);
            }
            if (fileName.equals(dependencyFileName)) {
                throw new IllegalArgumentException("Model dependency duplicates its primary file: " + id);
            }
            if (!dependencyNames.add(dependencyFileName)) {
                throw new IllegalArgumentException("Duplicate model dependency: " + dependencyFileName);
            }
        }
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
        this.dependencyFileNames = Collections.unmodifiableList(new ArrayList<>(dependencyNames));
    }

    /** A freshly imported model: identity offset, scale 1, a single-cell collision footprint at
     * the anchor - a safe starting point the user then widens once they see the model placed. */
    public static ModelAsset imported(String id, String fileName, boolean binary,
                                      float boundsMinX, float boundsMinY, float boundsMinZ,
                                      float boundsMaxX, float boundsMaxY, float boundsMaxZ) {
        return imported(id, fileName, binary, boundsMinX, boundsMinY, boundsMinZ,
            boundsMaxX, boundsMaxY, boundsMaxZ, Collections.emptyList());
    }

    public static ModelAsset imported(String id, String fileName, boolean binary,
                                      float boundsMinX, float boundsMinY, float boundsMinZ,
                                      float boundsMaxX, float boundsMaxY, float boundsMaxZ,
                                      List<String> dependencyFileNames) {
        return new ModelAsset(id, fileName, binary, 0f, 0f, 0f, 1f,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ,
            0, 0, 0, 0, false, false, 0f, dependencyFileNames);
    }

    public ModelAsset withPlacement(float offsetX, float offsetY, float offsetZ, float scale,
                                    int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                                    boolean alignToSlope, boolean walkable, float walkHeight) {
        return new ModelAsset(id, fileName, binary, offsetX, offsetY, offsetZ, scale,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ,
            collisionMinX, collisionMaxX, collisionMinZ, collisionMaxZ,
            alignToSlope, walkable, walkHeight, dependencyFileNames);
    }

    public List<String> getDependencyFileNames() {
        return dependencyFileNames;
    }

    public float getHeight() {
        return (boundsMaxY - boundsMinY) * scale;
    }

    public String getSource() {
        return (binary ? "glb:" : "gltf:") + "models/" + fileName;
    }

    private static boolean isFileName(String value) {
        return value.indexOf('/') < 0 && value.indexOf('\\') < 0 && !".".equals(value) && !"..".equals(value);
    }
}
