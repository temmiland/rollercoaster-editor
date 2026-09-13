package land.temmi.rollercoaster.editor.document;

import java.util.List;

/**
 * One brush stroke's worth of height/shape changes, applied and undone together. Height and shape
 * always change as a pair here so a tile never sits in a state {@link MapAsset} would reject
 * (flat tiles at whole levels, ramps at half levels) between the two.
 */
public final class PaintTerrainCommand implements Command {
    public static final class Edit {
        public final int x;
        public final int z;
        public final float previousHeight;
        public final TileShape previousShape;
        public final float newHeight;
        public final TileShape newShape;

        public Edit(int x, int z, float previousHeight, TileShape previousShape, float newHeight, TileShape newShape) {
            this.x = x;
            this.z = z;
            this.previousHeight = previousHeight;
            this.previousShape = previousShape;
            this.newHeight = newHeight;
            this.newShape = newShape;
        }
    }

    private final String mapId;
    private final List<Edit> edits;

    public PaintTerrainCommand(String mapId, List<Edit> edits) {
        if (edits == null || edits.isEmpty()) throw new IllegalArgumentException("A paint stroke needs at least one cell");
        this.mapId = mapId;
        this.edits = edits;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        // Validate the whole stroke before touching the map, so a bad cell can't leave earlier
        // cells in this same stroke mutated without a matching undo entry.
        for (Edit edit : edits) {
            if (!map.contains(edit.x, edit.z)) {
                throw new IndexOutOfBoundsException("Cell (" + edit.x + "," + edit.z + ") is outside map '" + mapId + "'");
            }
            if (edit.newShape == null || !MapAsset.fitsGrid(edit.newHeight, edit.newShape)) {
                throw new IllegalArgumentException("Height " + edit.newHeight + " does not fit shape " + edit.newShape);
            }
        }
        for (Edit edit : edits) map.setTerrain(edit.x, edit.z, edit.newHeight, edit.newShape);
    }

    @Override
    public void undo(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        for (Edit edit : edits) map.setTerrain(edit.x, edit.z, edit.previousHeight, edit.previousShape);
    }
}
