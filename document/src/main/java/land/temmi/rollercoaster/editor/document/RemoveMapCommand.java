package land.temmi.rollercoaster.editor.document;

/**
 * The caller supplies the asset (from {@link ProjectDocument#findMap}) so undo can restore it with
 * all its painted terrain intact - removal only unlists the object, it never clears its cells.
 */
public final class RemoveMapCommand implements Command {
    private final MapAsset map;

    public RemoveMapCommand(MapAsset map) {
        this.map = map;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeMap(map.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addMap(map);
    }
}
