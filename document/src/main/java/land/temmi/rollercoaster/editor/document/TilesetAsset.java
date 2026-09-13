package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A named group of tiles, exported together as one atlas by the asset pipeline. */
public final class TilesetAsset {
    public final String id;
    private final List<TileEntry> tiles = new ArrayList<>();

    public TilesetAsset(String id) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Tileset id is required");
        this.id = id;
    }

    public List<TileEntry> getTiles() {
        return Collections.unmodifiableList(tiles);
    }

    public TileEntry findTile(String tileId) {
        for (TileEntry tile : tiles) {
            if (tile.id.equals(tileId)) return tile;
        }
        return null;
    }

    /** Package-private: mutation goes through a {@link Command} so undo/redo stays consistent. */
    void addTile(TileEntry entry) {
        if (findTile(entry.id) != null) throw new IllegalArgumentException("Duplicate tile id: " + entry.id);
        tiles.add(entry);
    }

    void removeTile(String tileId) {
        if (!tiles.removeIf(tile -> tile.id.equals(tileId))) {
            throw new IllegalArgumentException("No such tile: " + tileId);
        }
    }
}
