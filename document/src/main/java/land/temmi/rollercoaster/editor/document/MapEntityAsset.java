package land.temmi.rollercoaster.editor.document;

/** A stable, tile-anchored runtime entity such as a player start or an NPC. */
public final class MapEntityAsset {
    public final String instanceId;
    public final String type;
    public final String spriteId;
    public final int x;
    public final int z;

    public MapEntityAsset(String instanceId, String type, String spriteId, int x, int z) {
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
    }
}
