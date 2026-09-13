package land.temmi.rollercoaster.editor.document;

import java.util.Locale;

/** The four directional animation slots expected by the runtime sprite manifest. */
public enum SpriteDirection {
    NORTH, EAST, SOUTH, WEST;

    public String toId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SpriteDirection fromId(String id) {
        if (id == null) throw new IllegalArgumentException("Sprite direction is required");
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sprite direction: " + id, e);
        }
    }
}
