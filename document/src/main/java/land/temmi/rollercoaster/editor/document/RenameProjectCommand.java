package land.temmi.rollercoaster.editor.document;

public final class RenameProjectCommand implements Command {
    private final String previousName;
    private final String newName;

    public RenameProjectCommand(String previousName, String newName) {
        this.previousName = previousName;
        this.newName = newName;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.setName(newName);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.setName(previousName);
    }
}
