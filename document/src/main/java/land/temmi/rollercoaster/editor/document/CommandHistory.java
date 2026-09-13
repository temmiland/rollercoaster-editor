package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.List;

/** Tracks undo/redo for one document and whether it differs from its last save. */
public final class CommandHistory {
    private static final int UNREACHABLE = -1;

    private final ProjectDocument document;
    private final List<Command> undoStack = new ArrayList<>();
    private final List<Command> redoStack = new ArrayList<>();
    private int savedDepth = 0;

    public CommandHistory(ProjectDocument document) {
        this.document = document;
    }

    public ProjectDocument getDocument() {
        return document;
    }

    public void perform(Command command) {
        command.execute(document);
        if (!redoStack.isEmpty() && savedDepth > undoStack.size()) {
            // The saved state lived in the redo branch we're about to discard, so it can no
            // longer be reached by undo/redo - stay dirty until the next explicit save.
            savedDepth = UNREACHABLE;
        }
        redoStack.clear();
        undoStack.add(command);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!canUndo()) throw new IllegalStateException("Nothing to undo");
        Command command = undoStack.remove(undoStack.size() - 1);
        command.undo(document);
        redoStack.add(command);
    }

    public void redo() {
        if (!canRedo()) throw new IllegalStateException("Nothing to redo");
        Command command = redoStack.remove(redoStack.size() - 1);
        command.execute(document);
        undoStack.add(command);
    }

    public boolean isDirty() {
        return undoStack.size() != savedDepth;
    }

    public void markSaved() {
        savedDepth = undoStack.size();
    }
}
