package land.temmi.rollercoaster.editor.document;

import java.util.List;

/** One brush stroke's worth of manual collision changes, applied and undone together. */
public final class PaintCollisionCommand implements Command {
    public static final class Edit {
        public final int x;
        public final int z;
        public final boolean previousBlocked;
        public final boolean newBlocked;

        public Edit(int x, int z, boolean previousBlocked, boolean newBlocked) {
            this.x = x;
            this.z = z;
            this.previousBlocked = previousBlocked;
            this.newBlocked = newBlocked;
        }
    }

    private final String mapId;
    private final List<Edit> edits;

    public PaintCollisionCommand(String mapId, List<Edit> edits) {
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
        }
        for (Edit edit : edits) map.setBlocked(edit.x, edit.z, edit.newBlocked);
    }

    @Override
    public void undo(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        for (Edit edit : edits) map.setBlocked(edit.x, edit.z, edit.previousBlocked);
    }
}
