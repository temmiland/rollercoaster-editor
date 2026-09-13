package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.CommandHistory;
import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;
import land.temmi.rollercoaster.editor.document.RenameProjectCommand;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the currently open project: its document, undo history, disk location and autosave.
 * Autosave writes a copy to {@code <project>/.editor/project.json}, separate from the file the
 * user explicitly saved, so a crash can be recovered without silently overwriting their last save.
 */
public final class ProjectController {
    public interface Listener {
        void onProjectChanged();
    }

    private static final int AUTOSAVE_INTERVAL_MS = 30_000;
    private static final String AUTOSAVE_DIRECTORY_NAME = ".editor";

    private final Listener listener;
    private final Timer autosaveTimer;

    private CommandHistory history;
    private Path projectDirectory;

    public ProjectController(Listener listener) {
        this.listener = listener;
        autosaveTimer = new Timer(AUTOSAVE_INTERVAL_MS, e -> autosave());
    }

    public boolean isOpen() {
        return history != null;
    }

    public boolean isDirty() {
        return history != null && history.isDirty();
    }

    public boolean canUndo() {
        return history != null && history.canUndo();
    }

    public boolean canRedo() {
        return history != null && history.canRedo();
    }

    public String getName() {
        return history == null ? null : history.getDocument().getName();
    }

    public Path getProjectDirectory() {
        return projectDirectory;
    }

    public static boolean hasNewerAutosave(Path directory) {
        Path saved = ProjectFile.fileIn(directory);
        Path autosave = ProjectFile.fileIn(autosaveDirectory(directory));
        if (!Files.exists(autosave)) return false;
        if (!Files.exists(saved)) return true;
        try {
            return Files.getLastModifiedTime(autosave).compareTo(Files.getLastModifiedTime(saved)) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public void newProject(Path directory, String name) throws IOException {
        ProjectDocument document = new ProjectDocument(name);
        ProjectFile.save(document, directory);
        open(directory, new CommandHistory(document), true);
    }

    public void openProject(Path directory) throws IOException {
        open(directory, new CommandHistory(ProjectFile.load(directory)), true);
    }

    public void restoreAutosave(Path directory) throws IOException {
        ProjectDocument document = ProjectFile.load(autosaveDirectory(directory));
        // Deliberately not markSaved(): the restored state differs from the file on disk
        // until the user explicitly saves it.
        open(directory, new CommandHistory(document), false);
    }

    private void open(Path directory, CommandHistory newHistory, boolean markSaved) {
        autosaveTimer.stop();
        history = newHistory;
        projectDirectory = directory;
        if (markSaved) history.markSaved(); else history.markDirty();
        autosaveTimer.start();
        listener.onProjectChanged();
    }

    public void save() throws IOException {
        requireOpen();
        ProjectFile.save(history.getDocument(), projectDirectory);
        history.markSaved();
        listener.onProjectChanged();
    }

    public void saveAs(Path newDirectory) throws IOException {
        requireOpen();
        projectDirectory = newDirectory;
        save();
    }

    public void rename(String newName) {
        requireOpen();
        history.perform(new RenameProjectCommand(history.getDocument().getName(), newName));
        listener.onProjectChanged();
    }

    public void undo() {
        requireOpen();
        history.undo();
        listener.onProjectChanged();
    }

    public void redo() {
        requireOpen();
        history.redo();
        listener.onProjectChanged();
    }

    private void autosave() {
        if (history == null || !history.isDirty()) return;
        try {
            ProjectFile.save(history.getDocument(), autosaveDirectory(projectDirectory));
        } catch (IOException ignored) {
            // Best-effort; the explicit Speichern path is what surfaces real errors to the user.
        }
    }

    private static Path autosaveDirectory(Path projectDirectory) {
        return projectDirectory.resolve(AUTOSAVE_DIRECTORY_NAME);
    }

    private void requireOpen() {
        if (history == null) throw new IllegalStateException("No project is open");
    }
}
