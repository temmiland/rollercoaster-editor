package land.temmi.rollercoaster.editor.document;

/** Updates sheet settings while retaining an entity-visible sprite ID. */
public final class UpdateSpriteCommand implements Command {
    private final SpriteAsset previous;
    private final SpriteAsset replacement;

    public UpdateSpriteCommand(SpriteAsset previous, SpriteAsset replacement) {
        if (previous == null || replacement == null || !previous.id.equals(replacement.id)) {
            throw new IllegalArgumentException("Sprite updates need matching IDs");
        }
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.replaceSprite(previous.id, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.replaceSprite(replacement.id, previous);
    }
}
