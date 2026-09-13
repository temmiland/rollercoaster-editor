package land.temmi.rollercoaster.editor.document;

public final class ImportTextureCommand implements Command {
    private final TextureAsset asset;

    public ImportTextureCommand(TextureAsset asset) {
        this.asset = asset;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addTexture(asset);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeTexture(asset.id);
    }
}
