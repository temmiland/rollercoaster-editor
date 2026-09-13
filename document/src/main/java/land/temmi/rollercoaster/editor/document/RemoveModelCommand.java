package land.temmi.rollercoaster.editor.document;

/** The caller supplies the asset (from {@link ProjectDocument#findModel}) so undo can restore it. */
public final class RemoveModelCommand implements Command {
    private final ModelAsset asset;

    public RemoveModelCommand(ModelAsset asset) {
        this.asset = asset;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeModel(asset.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addModel(asset);
    }
}
