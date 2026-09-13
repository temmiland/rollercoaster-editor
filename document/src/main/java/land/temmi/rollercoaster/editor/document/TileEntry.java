package land.temmi.rollercoaster.editor.document;

/** One tile in a {@link TilesetAsset}: an id plus which imported texture it shows. */
public final class TileEntry {
    public final String id;
    public final String textureId;
    public final boolean walkable;

    public TileEntry(String id, String textureId, boolean walkable) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Tile id is required");
        if (textureId == null || textureId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tile texture id is required");
        }
        this.id = id;
        this.textureId = textureId;
        this.walkable = walkable;
    }
}
