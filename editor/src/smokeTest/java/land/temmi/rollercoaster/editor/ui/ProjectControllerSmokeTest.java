package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/** Checks ProjectController's new/open/save/saveAs and autosave-restore logic; no GUI involved. */
public final class ProjectControllerSmokeTest {
    public static void main(String[] args) throws IOException {
        verifyNewRenameSaveUndoRedo();
        verifyAutosaveDetectionAndRestore();
        verifySaveAsLeavesTheOriginalUntouched();
        verifyOpenProjectReadsWhatWasSaved();
        verifyOperationsRequireAnOpenProject();
        System.out.println("PASS: ProjectController new/rename/save/undo/redo, "
            + "autosave detection and restore, and saveAs isolation");
    }

    private static void verifyNewRenameSaveUndoRedo() throws IOException {
        Path directory = Files.createTempDirectory("trackside-editor-controller");
        ProjectController controller = new ProjectController(() -> { });

        controller.newProject(directory, "Erste Welt");
        if (!controller.isOpen() || controller.isDirty()) throw new AssertionError("A freshly created project should be open and clean");
        if (!"Erste Welt".equals(controller.getName())) throw new AssertionError("newProject did not set the name");

        controller.rename("Zweite Welt");
        if (!controller.isDirty() || !controller.canUndo()) throw new AssertionError("Rename should be a dirty, undoable edit");

        controller.save();
        if (controller.isDirty()) throw new AssertionError("Should not be dirty right after saving");
        if (!"Zweite Welt".equals(ProjectFile.load(directory).getName())) throw new AssertionError("save() did not persist the rename");

        controller.undo();
        if (!"Erste Welt".equals(controller.getName()) || !controller.isDirty()) {
            throw new AssertionError("Undo past the saved point should restore the name and be dirty");
        }
        controller.redo();
        if (!"Zweite Welt".equals(controller.getName()) || controller.isDirty()) {
            throw new AssertionError("Redo back to the saved point should not be dirty");
        }
    }

    private static void verifyAutosaveDetectionAndRestore() throws IOException {
        Path directory = Files.createTempDirectory("trackside-editor-controller-autosave");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(directory, "Original");
        controller.save();

        if (ProjectController.hasNewerAutosave(directory)) throw new AssertionError("No autosave exists yet");

        Path autosaveFile = ProjectFile.fileIn(directory.resolve(".editor"));
        ProjectFile.save(new ProjectDocument("Wiederhergestellt"), autosaveFile.getParent());
        // Filesystem mtime resolution can be coarser than this test's runtime, so force the
        // autosave to look newer instead of racing the clock.
        Files.setLastModifiedTime(autosaveFile, FileTime.from(Instant.now().plusSeconds(5)));

        if (!ProjectController.hasNewerAutosave(directory)) throw new AssertionError("Newer autosave was not detected");

        controller.restoreAutosave(directory);
        if (!"Wiederhergestellt".equals(controller.getName())) throw new AssertionError("restoreAutosave did not load the autosave");
        if (!controller.isDirty()) throw new AssertionError("A restored autosave differs from project.json, so it must be dirty");

        controller.save();
        if (!"Wiederhergestellt".equals(ProjectFile.load(directory).getName())) {
            throw new AssertionError("Saving after a restore did not persist to project.json");
        }
    }

    private static void verifySaveAsLeavesTheOriginalUntouched() throws IOException {
        Path original = Files.createTempDirectory("trackside-editor-controller-original");
        Path copy = Files.createTempDirectory("trackside-editor-controller-copy");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(original, "Original");
        controller.save();

        controller.rename("Kopie");
        controller.saveAs(copy);
        if (!copy.equals(controller.getProjectDirectory())) throw new AssertionError("saveAs did not switch the active directory");
        if (!"Kopie".equals(ProjectFile.load(copy).getName())) throw new AssertionError("saveAs did not write to the new directory");
        if (!"Original".equals(ProjectFile.load(original).getName())) {
            throw new AssertionError("saveAs must not modify the directory the project was saved from");
        }
    }

    private static void verifyOpenProjectReadsWhatWasSaved() throws IOException {
        Path directory = Files.createTempDirectory("trackside-editor-controller-open");
        ProjectFile.save(new ProjectDocument("Von der Platte geladen"), directory);

        ProjectController controller = new ProjectController(() -> { });
        controller.openProject(directory);
        if (!"Von der Platte geladen".equals(controller.getName())) throw new AssertionError("openProject did not read project.json");
        if (controller.isDirty()) throw new AssertionError("A freshly opened project should not be dirty");
    }

    private static void verifyOperationsRequireAnOpenProject() {
        ProjectController controller = new ProjectController(() -> { });
        try {
            controller.save();
            throw new AssertionError("Saving with no project open should fail");
        } catch (IllegalStateException expected) {
            // Expected: requireOpen() guards every mutating operation.
        } catch (IOException e) {
            throw new AssertionError("Expected IllegalStateException, not IOException", e);
        }
    }
}
