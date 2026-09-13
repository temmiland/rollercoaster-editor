package land.temmi.rollercoaster.editor.document;

/** The caller supplies the asset (from {@link ProjectDocument#findTexture}) so undo can restore it. */
public final class RemoveTextureCommand implements Command {
    private final TextureAsset asset;

    public RemoveTextureCommand(TextureAsset asset) {
        this.asset = asset;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeTexture(asset.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addTexture(asset);
    }
}
