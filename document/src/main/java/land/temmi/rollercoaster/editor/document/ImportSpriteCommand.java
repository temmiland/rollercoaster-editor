package land.temmi.rollercoaster.editor.document;

/** Registers a copied sprite sheet. */
public final class ImportSpriteCommand implements Command {
    private final SpriteAsset sprite;

    public ImportSpriteCommand(SpriteAsset sprite) {
        this.sprite = sprite;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addSprite(sprite);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeSprite(sprite.id);
    }
}
