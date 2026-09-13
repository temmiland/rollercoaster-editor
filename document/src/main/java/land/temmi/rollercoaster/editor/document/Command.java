package land.temmi.rollercoaster.editor.document;

/** One undoable change to a {@link ProjectDocument}. */
public interface Command {
    void execute(ProjectDocument document);

    void undo(ProjectDocument document);
}
