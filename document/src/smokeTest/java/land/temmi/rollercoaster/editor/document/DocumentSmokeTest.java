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
        verifyTextureAndTilesetCommands();
        verifyTextureAndTilesetRoundtrip();
        System.out.println("PASS: undo/redo, dirty tracking after a branching edit, an atomic project.json "
            + "roundtrip across a moved directory, and texture/tileset commands with referential integrity");
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

    private static void verifyTextureAndTilesetCommands() {
        ProjectDocument document = new ProjectDocument("Welt");
        CommandHistory history = new CommandHistory(document);

        TextureAsset grass = new TextureAsset("grass", "grass.png");
        history.perform(new ImportTextureCommand(grass));
        if (document.findTexture("grass") == null) throw new AssertionError("Texture import did not apply");

        history.perform(new CreateTilesetCommand("overworld"));
        TilesetAsset tileset = document.findTileset("overworld");
        if (tileset == null) throw new AssertionError("Tileset creation did not apply");

        TileEntry grassTile = new TileEntry("grass", "grass", true);
        history.perform(new AddTileCommand("overworld", grassTile));
        if (tileset.findTile("grass") == null) throw new AssertionError("Adding a tile did not apply");

        try {
            document.removeTexture("grass");
            throw new AssertionError("Removing a texture still used by a tile should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the tile still references it.
        }

        history.perform(new RemoveTileCommand("overworld", grassTile));
        if (tileset.findTile("grass") != null) throw new AssertionError("Removing a tile did not apply");

        history.perform(new RemoveTextureCommand(grass));
        if (document.findTexture("grass") != null) throw new AssertionError("Texture removal did not apply");

        history.undo(); // restore texture
        history.undo(); // restore tile
        if (tileset.findTile("grass") == null) throw new AssertionError("Undo did not restore the tile");
        if (document.findTexture("grass") == null) throw new AssertionError("Undo did not restore the texture");

        history.perform(new RemoveTilesetCommand(tileset));
        if (document.findTileset("overworld") != null) throw new AssertionError("Tileset removal did not apply");
        history.undo();
        if (document.findTileset("overworld") == null) throw new AssertionError("Undo did not restore the tileset");
        if (document.findTileset("overworld").findTile("grass") == null) {
            throw new AssertionError("Restoring a removed tileset must keep its tiles");
        }
    }

    private static void verifyTextureAndTilesetRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Welt mit Tileset");
        document.addTexture(new TextureAsset("grass", "grass.png"));
        document.addTexture(new TextureAsset("path", "path.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        tileset.addTile(new TileEntry("path", "path", true));
        document.addTileset(tileset);

        Path directory = Files.createTempDirectory("trackside-editor-project-tileset");
        ProjectFile.save(document, directory);
        ProjectDocument reloaded = ProjectFile.load(directory);

        if (reloaded.getTextures().size() != 2) throw new AssertionError("Texture list did not round-trip");
        if (reloaded.findTexture("path").fileName == null || !"path.png".equals(reloaded.findTexture("path").fileName)) {
            throw new AssertionError("Texture file name did not round-trip");
        }
        TilesetAsset reloadedTileset = reloaded.findTileset("overworld");
        if (reloadedTileset == null || reloadedTileset.getTiles().size() != 2) {
            throw new AssertionError("Tileset did not round-trip");
        }
        TileEntry reloadedTile = reloadedTileset.findTile("grass");
        if (reloadedTile == null || !"grass".equals(reloadedTile.textureId) || !reloadedTile.walkable) {
            throw new AssertionError("Tile entry did not round-trip");
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
