package land.temmi.rollercoaster.editor.document;

/**
 * The caller supplies the asset (from {@link ProjectDocument#findTileset}) so undo can restore it
 * with all its tiles intact - removal only unlists the object, it never clears its tiles.
 */
public final class RemoveTilesetCommand implements Command {
    private final TilesetAsset tileset;

    public RemoveTilesetCommand(TilesetAsset tileset) {
        this.tileset = tileset;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeTileset(tileset.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addTileset(tileset);
    }
}
