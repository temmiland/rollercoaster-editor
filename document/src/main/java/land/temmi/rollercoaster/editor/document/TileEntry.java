package land.temmi.rollercoaster.editor.document;

/**
 * One tile in a {@link TilesetAsset}: an id, which imported texture it shows on top, and
 * optionally a separate texture for its exposed cliff/ramp sides (null falls back to the top
 * texture, matching the engine's own default).
 */
public final class TileEntry {
    public final String id;
    public final String textureId;
    public final String sideTextureId;
    public final boolean walkable;

    public TileEntry(String id, String textureId, String sideTextureId, boolean walkable) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Tile id is required");
        if (textureId == null || textureId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tile texture id is required");
        }
        this.id = id;
        this.textureId = textureId;
        this.sideTextureId = sideTextureId;
        this.walkable = walkable;
    }

    public TileEntry(String id, String textureId, boolean walkable) {
        this(id, textureId, null, walkable);
    }
}
