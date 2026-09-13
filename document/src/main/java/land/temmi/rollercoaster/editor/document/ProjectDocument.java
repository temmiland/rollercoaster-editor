package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Editable project state: a name plus the imported textures and tilesets built from them. */
public final class ProjectDocument {
    public static final int FORMAT_VERSION = 1;

    private String name;
    private final List<TextureAsset> textures = new ArrayList<>();
    private final List<TilesetAsset> tilesets = new ArrayList<>();

    public ProjectDocument(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public List<TextureAsset> getTextures() {
        return Collections.unmodifiableList(textures);
    }

    public List<TilesetAsset> getTilesets() {
        return Collections.unmodifiableList(tilesets);
    }

    public TextureAsset findTexture(String id) {
        for (TextureAsset texture : textures) {
            if (texture.id.equals(id)) return texture;
        }
        return null;
    }

    public TilesetAsset findTileset(String id) {
        for (TilesetAsset tileset : tilesets) {
            if (tileset.id.equals(id)) return tileset;
        }
        return null;
    }

    /** Package-private: mutation goes through a {@link Command} so undo/redo stays consistent. */
    void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Project name is required");
        this.name = name;
    }

    void addTexture(TextureAsset asset) {
        if (findTexture(asset.id) != null) throw new IllegalArgumentException("Duplicate texture id: " + asset.id);
        textures.add(asset);
    }

    void removeTexture(String id) {
        for (TilesetAsset tileset : tilesets) {
            for (TileEntry tile : tileset.getTiles()) {
                if (tile.textureId.equals(id) || id.equals(tile.sideTextureId)) {
                    throw new IllegalArgumentException(
                        "Texture '" + id + "' is still used by tile '" + tile.id + "' in tileset '" + tileset.id + "'");
                }
            }
        }
        if (!textures.removeIf(texture -> texture.id.equals(id))) {
            throw new IllegalArgumentException("No such texture: " + id);
        }
    }

    void addTileset(TilesetAsset tileset) {
        if (findTileset(tileset.id) != null) throw new IllegalArgumentException("Duplicate tileset id: " + tileset.id);
        tilesets.add(tileset);
    }

    void removeTileset(String id) {
        if (!tilesets.removeIf(tileset -> tileset.id.equals(id))) {
            throw new IllegalArgumentException("No such tileset: " + id);
        }
    }

    TilesetAsset requireTileset(String id) {
        TilesetAsset tileset = findTileset(id);
        if (tileset == null) throw new IllegalArgumentException("No such tileset: " + id);
        return tileset;
    }
}
