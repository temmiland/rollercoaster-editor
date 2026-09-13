package land.temmi.rollercoaster.editor.document;

/** Removes a dialogue registration while retaining it for undo. */
public final class RemoveDialogueCommand implements Command {
    private final DialogueAsset dialogue;

    public RemoveDialogueCommand(DialogueAsset dialogue) {
        this.dialogue = dialogue;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeDialogue(dialogue.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addDialogue(dialogue);
    }
}
