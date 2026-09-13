package land.temmi.rollercoaster.editor.document;

/** Registers a dialogue tree. */
public final class RegisterDialogueCommand implements Command {
    private final DialogueAsset dialogue;

    public RegisterDialogueCommand(DialogueAsset dialogue) {
        this.dialogue = dialogue;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addDialogue(dialogue);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeDialogue(dialogue.id);
    }
}
