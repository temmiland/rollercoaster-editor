package land.temmi.rollercoaster.editor.document;

/** The caller supplies the entry (from {@link TilesetAsset#findTile}) so undo can restore it. */
public final class RemoveTileCommand implements Command {
    private final String tilesetId;
    private final TileEntry entry;

    public RemoveTileCommand(String tilesetId, TileEntry entry) {
        this.tilesetId = tilesetId;
        this.entry = entry;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireTileset(tilesetId).removeTile(entry.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireTileset(tilesetId).addTile(entry);
    }
}
