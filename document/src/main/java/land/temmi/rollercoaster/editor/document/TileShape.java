package land.temmi.rollercoaster.editor.document;

/**
 * Mirrors the engine's {@code land.temmi.rollercoaster.world.TileShape}: a ramp rises towards the
 * named direction and spans exactly one level across the tile. Kept as a document-local copy since
 * {@code document} has no compile-time dependency on the engine.
 */
public enum TileShape {
    FLAT,
    RAMP_NORTH,
    RAMP_EAST,
    RAMP_SOUTH,
    RAMP_WEST;

    public boolean isRamp() {
        return this != FLAT;
    }

    /** Matches the lowercase spelling used on disk by the engine's own map documents. */
    public String toId() {
        return name().toLowerCase();
    }

    /** Mirrors {@code TileShape.parse}: case-insensitive, accepts the short n/e/s/w aliases. */
    public static TileShape fromId(String id) {
        if (id == null || id.isEmpty()) return FLAT;
        for (TileShape shape : values()) {
            if (shape.name().equalsIgnoreCase(id)) return shape;
        }
        if ("ramp_n".equalsIgnoreCase(id)) return RAMP_NORTH;
        if ("ramp_e".equalsIgnoreCase(id)) return RAMP_EAST;
        if ("ramp_s".equalsIgnoreCase(id)) return RAMP_SOUTH;
        if ("ramp_w".equalsIgnoreCase(id)) return RAMP_WEST;
        throw new IllegalArgumentException("Unknown tile shape: " + id);
    }
}
