package land.temmi.rollercoaster.editor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checks undo/redo, dirty tracking and the project.json roundtrip; no GL context involved. */
public final class DocumentSmokeTest {
    public static void main(String[] args) throws IOException {
        verifyUndoRedoAndDirtyTracking();
        verifySaveLoadRoundtrip();
        verifyRejectsUnknownVersion();
        System.out.println("PASS: undo/redo, dirty tracking after a branching edit, "
            + "and an atomic project.json roundtrip across a moved directory");
    }

    private static void verifyUndoRedoAndDirtyTracking() {
        ProjectDocument document = new ProjectDocument("Erste Welt");
        CommandHistory history = new CommandHistory(document);
        if (history.isDirty()) throw new AssertionError("A freshly created document should not be dirty");

        history.perform(new RenameProjectCommand("Erste Welt", "Zweite Welt"));
        if (!"Zweite Welt".equals(document.getName())) throw new AssertionError("Rename did not apply");
        if (!history.isDirty()) throw new AssertionError("Document should be dirty after an unsaved change");

        history.markSaved();
        if (history.isDirty()) throw new AssertionError("Document should not be dirty right after saving");

        history.undo();
        if (!"Erste Welt".equals(document.getName())) throw new AssertionError("Undo did not restore the name");
        if (!history.isDirty()) throw new AssertionError("Undoing past the saved point should be dirty");

        history.redo();
        if (!"Zweite Welt".equals(document.getName())) throw new AssertionError("Redo did not reapply the rename");
        if (history.isDirty()) throw new AssertionError("Redoing back to the saved point should not be dirty");

        history.undo();
        history.perform(new RenameProjectCommand("Erste Welt", "Dritte Welt"));
        if (!history.isDirty()) {
            throw new AssertionError("A new edit that discards the saved state's redo branch must stay dirty");
        }
        if (history.canRedo()) throw new AssertionError("Performing a command should clear the redo stack");

        CommandHistory freshHistory = new CommandHistory(new ProjectDocument("Frisch geladen"));
        if (freshHistory.isDirty()) throw new AssertionError("A freshly built history is clean by default");
        freshHistory.markDirty();
        if (!freshHistory.isDirty()) throw new AssertionError("markDirty() must force a dirty read");
        freshHistory.markSaved();
        if (freshHistory.isDirty()) throw new AssertionError("markSaved() must clear a forced-dirty state");
    }

    private static void verifySaveLoadRoundtrip() throws IOException {
        Path original = Files.createTempDirectory("trackside-editor-project");
        ProjectDocument document = new ProjectDocument("Rollercoaster Valley");
        ProjectFile.save(document, original);
        if (!Files.exists(ProjectFile.fileIn(original))) throw new AssertionError("save() did not create project.json");
        if (Files.list(original).anyMatch(p -> p.toString().endsWith(".tmp"))) {
            throw new AssertionError("A temp file survived the atomic rename");
        }

        ProjectDocument reloaded = ProjectFile.load(original);
        if (!"Rollercoaster Valley".equals(reloaded.getName())) throw new AssertionError("Roundtrip lost the name");

        // Simulates the user moving the whole project folder before reopening it.
        Path moved = original.resolveSibling(original.getFileName() + "-moved");
        Files.move(original, moved);
        ProjectDocument reloadedAfterMove = ProjectFile.load(moved);
        if (!"Rollercoaster Valley".equals(reloadedAfterMove.getName())) {
            throw new AssertionError("Reopening after a move lost the name");
        }

        // Saving again must overwrite cleanly rather than append or duplicate the temp file.
        ProjectFile.save(new ProjectDocument("Renamed After Move"), moved);
        if (!"Renamed After Move".equals(ProjectFile.load(moved).getName())) {
            throw new AssertionError("Re-saving after a move did not overwrite project.json");
        }
    }

    private static void verifyRejectsUnknownVersion() throws IOException {
        Path directory = Files.createTempDirectory("trackside-editor-project-bad-version");
        Files.writeString(ProjectFile.fileIn(directory), "{\"formatVersion\": 999, \"name\": \"X\"}");
        try {
            ProjectFile.load(directory);
            throw new AssertionError("Loading an unknown format version should fail");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("999")) {
                throw new AssertionError("Version mismatch error should name the offending version: " + expected.getMessage());
            }
        }
    }
}
