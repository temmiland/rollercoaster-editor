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
    /** Mirrors {@code LightingEnvironment.MAX_POINT_LIGHTS}; point and spot lights share the budget. */
    public static final int MAX_LIGHTS = 8;

    public final String id;
    public int width;
    public int depth;
    public final String tilesetId;

    private String[] tiles;
    private float[] heights;
    private TileShape[] shapes;
    private boolean[] collision;
    private final List<MapProp> props = new ArrayList<>();
    private final List<MapEntityAsset> entities = new ArrayList<>();
    private final List<MapLightAsset> lights = new ArrayList<>();
    private final List<MapTransitionAsset> transitions = new ArrayList<>();
    private final List<GameEventAsset> events = new ArrayList<>();

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

    public List<MapEntityAsset> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    public MapEntityAsset findEntity(String instanceId) {
        for (MapEntityAsset entity : entities) {
            if (entity.instanceId.equals(instanceId)) return entity;
        }
        return null;
    }

    public List<MapLightAsset> getLights() {
        return Collections.unmodifiableList(lights);
    }

    public MapLightAsset findLight(String instanceId) {
        for (MapLightAsset light : lights) {
            if (light.instanceId.equals(instanceId)) return light;
        }
        return null;
    }

    public List<MapTransitionAsset> getTransitions() {
        return Collections.unmodifiableList(transitions);
    }

    public MapTransitionAsset findTransition(String instanceId) {
        for (MapTransitionAsset transition : transitions) {
            if (transition.instanceId.equals(instanceId)) return transition;
        }
        return null;
    }

    public List<GameEventAsset> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public GameEventAsset findEvent(String instanceId) {
        for (GameEventAsset event : events) {
            if (event.instanceId.equals(instanceId)) return event;
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

    void addEntity(MapEntityAsset entity) {
        if (findEntity(entity.instanceId) != null) {
            throw new IllegalArgumentException("Duplicate entity instance id: " + entity.instanceId);
        }
        entities.add(entity);
    }

    void removeEntity(String instanceId) {
        if (!entities.removeIf(entity -> entity.instanceId.equals(instanceId))) {
            throw new IllegalArgumentException("No such entity: " + instanceId);
        }
    }

    void replaceEntity(String instanceId, MapEntityAsset replacement) {
        if (replacement == null || !instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Replacement entity must keep instance ID '" + instanceId + "'");
        }
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i).instanceId.equals(instanceId)) {
                entities.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such entity: " + instanceId);
    }

    void addLight(MapLightAsset light) {
        if (findLight(light.instanceId) != null) {
            throw new IllegalArgumentException("Duplicate light instance id: " + light.instanceId);
        }
        if (lights.size() >= MAX_LIGHTS) {
            throw new IllegalArgumentException(
                "Map '" + id + "' already has " + MAX_LIGHTS + " lights, the engine's shared point/spot budget");
        }
        lights.add(light);
    }

    void removeLight(String instanceId) {
        if (!lights.removeIf(light -> light.instanceId.equals(instanceId))) {
            throw new IllegalArgumentException("No such light: " + instanceId);
        }
    }

    void replaceLight(String instanceId, MapLightAsset replacement) {
        if (replacement == null || !instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Replacement light must keep instance ID '" + instanceId + "'");
        }
        for (int i = 0; i < lights.size(); i++) {
            if (lights.get(i).instanceId.equals(instanceId)) {
                lights.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such light: " + instanceId);
    }

    void addTransition(MapTransitionAsset transition) {
        if (findTransition(transition.instanceId) != null) {
            throw new IllegalArgumentException("Duplicate transition instance id: " + transition.instanceId);
        }
        transitions.add(transition);
    }

    void removeTransition(String instanceId) {
        if (!transitions.removeIf(transition -> transition.instanceId.equals(instanceId))) {
            throw new IllegalArgumentException("No such transition: " + instanceId);
        }
    }

    void replaceTransition(String instanceId, MapTransitionAsset replacement) {
        if (replacement == null || !instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Replacement transition must keep instance ID '" + instanceId + "'");
        }
        for (int i = 0; i < transitions.size(); i++) {
            if (transitions.get(i).instanceId.equals(instanceId)) {
                transitions.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such transition: " + instanceId);
    }

    void addEvent(GameEventAsset event) {
        if (findEvent(event.instanceId) != null) {
            throw new IllegalArgumentException("Duplicate event instance id: " + event.instanceId);
        }
        events.add(event);
    }

    void removeEvent(String instanceId) {
        if (!events.removeIf(event -> event.instanceId.equals(instanceId))) {
            throw new IllegalArgumentException("No such event: " + instanceId);
        }
    }

    void replaceEvent(String instanceId, GameEventAsset replacement) {
        if (replacement == null || !instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Replacement event must keep instance ID '" + instanceId + "'");
        }
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).instanceId.equals(instanceId)) {
                events.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such event: " + instanceId);
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
        for (MapEntityAsset entity : entities) {
            if (entity.x >= newWidth || entity.z >= newDepth) {
                throw new IllegalArgumentException("Resizing map '" + id + "' would remove entity '" + entity.instanceId + "'");
            }
        }
        for (MapLightAsset light : lights) {
            if (light.x >= newWidth || light.z >= newDepth) {
                throw new IllegalArgumentException("Resizing map '" + id + "' would remove light '" + light.instanceId + "'");
            }
        }
        for (MapTransitionAsset transition : transitions) {
            if (transition.x >= newWidth || transition.z >= newDepth) {
                throw new IllegalArgumentException(
                    "Resizing map '" + id + "' would remove transition '" + transition.instanceId + "'");
            }
        }
        for (GameEventAsset event : events) {
            EventTriggerAsset trigger = event.trigger;
            if (trigger.type == EventTriggerAsset.Type.ENTER_AREA
                && (trigger.x >= newWidth || trigger.z >= newDepth)) {
                throw new IllegalArgumentException(
                    "Resizing map '" + id + "' would remove event '" + event.instanceId + "'");
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

    /** Entities are placed on whole cells, unlike freely offsettable props. */
    void requireEntityPosition(MapEntityAsset entity) {
        if (!contains(entity.x, entity.z)) {
            throw new IllegalArgumentException("Entity '" + entity.instanceId + "' is outside map '" + id + "'");
        }
    }

    /** Lights use grid coordinates like props; height is unconstrained. */
    void requireLightPosition(MapLightAsset light) {
        if (light.x < 0f || light.x >= width || light.z < 0f || light.z >= depth) {
            throw new IllegalArgumentException("Light '" + light.instanceId + "' is outside map '" + id + "'");
        }
    }

    /** Transitions are placed on whole cells, like entities. */
    void requireTransitionPosition(MapTransitionAsset transition) {
        if (!contains(transition.x, transition.z)) {
            throw new IllegalArgumentException("Transition '" + transition.instanceId + "' is outside map '" + id + "'");
        }
    }

    /** Only an ENTER_AREA trigger has a map position; other trigger types fire independently of it. */
    void requireEventPosition(GameEventAsset event) {
        EventTriggerAsset trigger = event.trigger;
        if (trigger.type == EventTriggerAsset.Type.ENTER_AREA && !contains(trigger.x, trigger.z)) {
            throw new IllegalArgumentException("Event '" + event.instanceId + "' area is outside map '" + id + "'");
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
