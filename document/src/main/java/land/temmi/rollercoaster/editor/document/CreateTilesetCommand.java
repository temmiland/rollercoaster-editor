package land.temmi.rollercoaster.editor.document;

public final class CreateTilesetCommand implements Command {
    private final String tilesetId;

    public CreateTilesetCommand(String tilesetId) {
        this.tilesetId = tilesetId;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addTileset(new TilesetAsset(tilesetId));
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeTileset(tilesetId);
    }
}
