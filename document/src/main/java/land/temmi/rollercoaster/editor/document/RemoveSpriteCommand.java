package land.temmi.rollercoaster.editor.document;

/** Removes a sprite registration while retaining it for undo. */
public final class RemoveSpriteCommand implements Command {
    private final SpriteAsset sprite;

    public RemoveSpriteCommand(SpriteAsset sprite) {
        this.sprite = sprite;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeSprite(sprite.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addSprite(sprite);
    }
}
