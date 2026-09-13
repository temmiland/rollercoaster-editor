package land.temmi.rollercoaster.editor.document;

public final class ImportModelCommand implements Command {
    private final ModelAsset asset;

    public ImportModelCommand(ModelAsset asset) {
        this.asset = asset;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addModel(asset);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeModel(asset.id);
    }
}
