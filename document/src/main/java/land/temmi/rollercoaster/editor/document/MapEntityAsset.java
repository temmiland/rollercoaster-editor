package land.temmi.rollercoaster.editor.document;

import java.util.Collections;
import java.util.Map;

/** A stable, tile-anchored runtime entity such as a player start or an NPC. */
public final class MapEntityAsset {
    public final String instanceId;
    public final String type;
    public final String spriteId;
    public final int x;
    public final int z;
    private final Map<String, String> properties;

    public MapEntityAsset(String instanceId, String type, String spriteId, int x, int z) {
        this(instanceId, type, spriteId, x, z, Collections.emptyMap());
    }

    public MapEntityAsset(String instanceId, String type, String spriteId, int x, int z,
                          Map<String, String> properties) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity instance id is required");
        }
        if (type == null || type.trim().isEmpty()) throw new IllegalArgumentException("Entity type is required");
        if (spriteId != null && spriteId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity sprite id must be nonempty when present");
        }
        this.instanceId = instanceId;
        this.type = type;
        this.spriteId = spriteId;
        this.x = x;
        this.z = z;
        this.properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}
