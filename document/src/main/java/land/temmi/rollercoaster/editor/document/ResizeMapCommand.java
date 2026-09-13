package land.temmi.rollercoaster.editor.document;

/** Resizes one map while keeping its overlapping north-west terrain cells intact. */
public final class ResizeMapCommand implements Command {
    private final String mapId;
    private final int width;
    private final int depth;
    private MapAsset.State previous;

    public ResizeMapCommand(String mapId, int width, int depth) {
        this.mapId = mapId;
        this.width = width;
        this.depth = depth;
    }

    @Override
    public void execute(ProjectDocument document) {
        previous = document.requireMap(mapId).resize(width, depth);
    }

    @Override
    public void undo(ProjectDocument document) {
        if (previous == null) throw new IllegalStateException("Map resize was not executed");
        document.requireMap(mapId).restore(previous);
    }
}
