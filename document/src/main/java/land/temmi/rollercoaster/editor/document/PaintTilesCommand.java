package land.temmi.rollercoaster.editor.document;

import java.util.List;

/** One brush stroke's worth of tile-type changes, applied and undone together. */
public final class PaintTilesCommand implements Command {
    public static final class Edit {
        public final int x;
        public final int z;
        public final String previousTileId;
        public final String newTileId;

        public Edit(int x, int z, String previousTileId, String newTileId) {
            this.x = x;
            this.z = z;
            this.previousTileId = previousTileId;
            this.newTileId = newTileId;
        }
    }

    private final String mapId;
    private final List<Edit> edits;

    public PaintTilesCommand(String mapId, List<Edit> edits) {
        if (edits == null || edits.isEmpty()) throw new IllegalArgumentException("A paint stroke needs at least one cell");
        this.mapId = mapId;
        this.edits = edits;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        TilesetAsset tileset = document.requireTileset(map.tilesetId);
        for (Edit edit : edits) {
            if (edit.newTileId != null && tileset.findTile(edit.newTileId) == null) {
                throw new IllegalArgumentException("Unknown tile '" + edit.newTileId + "' in tileset '" + map.tilesetId + "'");
            }
            map.setTile(edit.x, edit.z, edit.newTileId);
        }
    }

    @Override
    public void undo(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        for (Edit edit : edits) map.setTile(edit.x, edit.z, edit.previousTileId);
    }
}
