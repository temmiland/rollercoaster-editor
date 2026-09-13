package land.temmi.rollercoaster.editor.document;

public final class AddTileCommand implements Command {
    private final String tilesetId;
    private final TileEntry entry;

    public AddTileCommand(String tilesetId, TileEntry entry) {
        this.tilesetId = tilesetId;
        this.entry = entry;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireTileset(tilesetId).addTile(entry);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireTileset(tilesetId).removeTile(entry.id);
    }
}
