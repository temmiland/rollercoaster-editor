package land.temmi.rollercoaster.editor.document;

/** Updates a dialogue tree while retaining an event-visible dialogue ID. */
public final class UpdateDialogueCommand implements Command {
    private final DialogueAsset previous;
    private final DialogueAsset replacement;

    public UpdateDialogueCommand(DialogueAsset previous, DialogueAsset replacement) {
        if (previous == null || replacement == null || !previous.id.equals(replacement.id)) {
            throw new IllegalArgumentException("Dialogue updates need matching IDs");
        }
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.replaceDialogue(previous.id, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.replaceDialogue(replacement.id, previous);
    }
}
