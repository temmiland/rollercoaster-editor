package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A map's terrain grid: which tile type, height and shape each cell has, plus a manual collision
 * layer. Mirrors {@code land.temmi.rollercoaster.world.TileMap} field-for-field, including its
 * grid-height validation, so a map built here always loads on the engine's own terms.
 */
public final class MapAsset {
    public static final float LEVEL_HEIGHT = 1f;
    private static final float HEIGHT_EPSILON = 0.0001f;

    public final String id;
    public int width;
    public int depth;
    public final String tilesetId;

    private String[] tiles;
    private float[] heights;
    private TileShape[] shapes;
    private boolean[] collision;
    private final List<MapProp> props = new ArrayList<>();

    public MapAsset(String id, int width, int depth, String tilesetId) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Map id is required");
        if (width <= 0 || depth <= 0) throw new IllegalArgumentException("Map size must be positive: " + id);
        if (tilesetId == null || tilesetId.trim().isEmpty()) {
            throw new IllegalArgumentException("Map tileset id is required");
        }
        this.id = id;
        this.width = width;
        this.depth = depth;
        this.tilesetId = tilesetId;
        int cells = width * depth;
        this.tiles = new String[cells];
        this.heights = new float[cells];
        this.shapes = new TileShape[cells];
        Arrays.fill(shapes, TileShape.FLAT);
        this.collision = new boolean[cells];
    }

    public String getTile(int x, int z) {
        return tiles[indexOf(x, z)];
    }

    public float getHeight(int x, int z) {
        return heights[indexOf(x, z)];
    }

    public TileShape getShape(int x, int z) {
        return shapes[indexOf(x, z)];
    }

    public boolean isBlocked(int x, int z) {
        return collision[indexOf(x, z)];
    }

    public boolean contains(int x, int z) {
        return x >= 0 && x < width && z >= 0 && z < depth;
    }

    public List<MapProp> getProps() {
        return Collections.unmodifiableList(props);
    }

    public MapProp findProp(String instanceId) {
        for (MapProp prop : props) {
            if (prop.instanceId.equals(instanceId)) return prop;
        }
        return null;
    }

    /** Package-private: mutation goes through a {@link Command} so undo/redo stays consistent. */
    void addProp(MapProp prop) {
        if (findProp(prop.instanceId) != null) throw new IllegalArgumentException("Duplicate prop instance id: " + prop.instanceId);
        props.add(prop);
    }

    void removeProp(String instanceId) {
        if (!props.removeIf(prop -> prop.instanceId.equals(instanceId))) {
            throw new IllegalArgumentException("No such prop: " + instanceId);
        }
    }

    /** Restorable grid contents used by {@link ResizeMapCommand}. */
    static final class State {
        private final int width;
        private final int depth;
        private final String[] tiles;
        private final float[] heights;
        private final TileShape[] shapes;
        private final boolean[] collision;

        private State(MapAsset map) {
            width = map.width;
            depth = map.depth;
            tiles = map.tiles.clone();
            heights = map.heights.clone();
            shapes = map.shapes.clone();
            collision = map.collision.clone();
        }
    }

    /** Resizes the terrain grid, preserving the north-west cells shared by both dimensions. */
    State resize(int newWidth, int newDepth) {
        if (newWidth <= 0 || newDepth <= 0) throw new IllegalArgumentException("Map size must be positive: " + id);
        for (MapProp prop : props) {
            if (prop.x >= newWidth || prop.z >= newDepth) {
                throw new IllegalArgumentException("Resizing map '" + id + "' would remove prop '" + prop.instanceId + "'");
            }
        }
        State previous = new State(this);
        String[] newTiles = new String[newWidth * newDepth];
        float[] newHeights = new float[newWidth * newDepth];
        TileShape[] newShapes = new TileShape[newWidth * newDepth];
        Arrays.fill(newShapes, TileShape.FLAT);
        boolean[] newCollision = new boolean[newWidth * newDepth];
        int copiedWidth = Math.min(width, newWidth);
        int copiedDepth = Math.min(depth, newDepth);
        for (int z = 0; z < copiedDepth; z++) {
            int oldOffset = z * width;
            int newOffset = z * newWidth;
            System.arraycopy(tiles, oldOffset, newTiles, newOffset, copiedWidth);
            System.arraycopy(heights, oldOffset, newHeights, newOffset, copiedWidth);
            System.arraycopy(shapes, oldOffset, newShapes, newOffset, copiedWidth);
            System.arraycopy(collision, oldOffset, newCollision, newOffset, copiedWidth);
        }
        width = newWidth;
        depth = newDepth;
        tiles = newTiles;
        heights = newHeights;
        shapes = newShapes;
        collision = newCollision;
        return previous;
    }

    void restore(State state) {
        width = state.width;
        depth = state.depth;
        tiles = state.tiles.clone();
        heights = state.heights.clone();
        shapes = state.shapes.clone();
        collision = state.collision.clone();
    }

    /** Props use grid coordinates; their anchor must remain on this map. */
    void requirePropPosition(MapProp prop) {
        if (prop.x < 0f || prop.x >= width || prop.z < 0f || prop.z >= depth) {
            throw new IllegalArgumentException("Prop '" + prop.instanceId + "' is outside map '" + id + "'");
        }
    }

    /** Lets a batch command pre-validate every cell before mutating any of them. */
    public static boolean fitsGrid(float height, TileShape shape) {
        if (Float.isNaN(height) || Float.isInfinite(height)) return false;
        float midpoint = shape.isRamp() ? LEVEL_HEIGHT * 0.5f : 0f;
        float level = (height - midpoint) / LEVEL_HEIGHT;
        return Math.abs(level - Math.round(level)) <= HEIGHT_EPSILON;
    }

    void setTile(int x, int z, String tileId) {
        tiles[indexOf(x, z)] = tileId;
    }

    /** Changes shape when the current height already fits it; otherwise use {@link #setTerrain}. */
    void setShape(int x, int z, TileShape shape) {
        if (shape == null) throw new IllegalArgumentException("Tile shape is required");
        int index = indexOf(x, z);
        requireGridHeight(heights[index], shape);
        shapes[index] = shape;
    }

    /** Changes height when it already fits the current shape; otherwise use {@link #setTerrain}. */
    void setHeight(int x, int z, float height) {
        int index = indexOf(x, z);
        requireGridHeight(height, shapes[index]);
        heights[index] = height;
    }

    /** Changes height and shape together so a tile always remains on the terrain grid. */
    void setTerrain(int x, int z, float height, TileShape shape) {
        if (shape == null) throw new IllegalArgumentException("Tile shape is required");
        int index = indexOf(x, z);
        requireGridHeight(height, shape);
        heights[index] = height;
        shapes[index] = shape;
    }

    void setBlocked(int x, int z, boolean blocked) {
        collision[indexOf(x, z)] = blocked;
    }

    private int indexOf(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= depth) {
            throw new IndexOutOfBoundsException("Cell (" + x + "," + z + ") is outside map '" + id + "'");
        }
        return z * width + x;
    }

    /** Mirrors {@code TileMap.requireGridHeight}: flat tiles sit at whole levels, ramps at half levels. */
    private static void requireGridHeight(float height, TileShape shape) {
        if (fitsGrid(height, shape)) return;
        if (Float.isNaN(height) || Float.isInfinite(height)) {
            throw new IllegalArgumentException("Terrain height must be finite");
        }
        throw new IllegalArgumentException("Height does not fit the terrain grid: " + height);
    }
}
