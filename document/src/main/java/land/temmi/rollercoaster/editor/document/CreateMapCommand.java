package land.temmi.rollercoaster.editor.document;

public final class CreateMapCommand implements Command {
    private final String mapId;
    private final int width;
    private final int depth;
    private final String tilesetId;

    public CreateMapCommand(String mapId, int width, int depth, String tilesetId) {
        this.mapId = mapId;
        this.width = width;
        this.depth = depth;
        this.tilesetId = tilesetId;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireTileset(tilesetId);
        document.addMap(new MapAsset(mapId, width, depth, tilesetId));
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeMap(mapId);
    }
}
