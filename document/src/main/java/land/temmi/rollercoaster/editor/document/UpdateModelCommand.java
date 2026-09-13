package land.temmi.rollercoaster.editor.document;

/** Replaces a model's placement metadata while preserving its stable ID and imported source files. */
public final class UpdateModelCommand implements Command {
    private final ModelAsset previous;
    private final ModelAsset replacement;

    public UpdateModelCommand(ModelAsset previous, ModelAsset replacement) {
        if (previous == null || replacement == null || !previous.id.equals(replacement.id)) {
            throw new IllegalArgumentException("Model updates need matching previous and replacement IDs");
        }
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.replaceModel(previous.id, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.replaceModel(replacement.id, previous);
    }
}
