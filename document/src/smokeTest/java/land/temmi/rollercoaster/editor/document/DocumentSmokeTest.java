package land.temmi.rollercoaster.editor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Checks undo/redo, dirty tracking and the project.json roundtrip; no GL context involved. */
public final class DocumentSmokeTest {
    public static void main(String[] args) throws IOException {
        verifyUndoRedoAndDirtyTracking();
        verifySaveLoadRoundtrip();
        verifyRejectsUnknownVersion();
        verifyTextureAndTilesetCommands();
        verifyTextureAndTilesetRoundtrip();
        verifySideTextureRoundtrip();
        verifyRemoveTextureBlockedBySideReference();
        verifyModelCommandsAndRoundtrip();
        verifyMapCommandsAndTerrainGrid();
        verifyMapRoundtrip();
        System.out.println("PASS: undo/redo, dirty tracking after a branching edit, an atomic project.json "
            + "roundtrip across a moved directory, texture/tileset commands with referential integrity, "
            + "model import/export, and map terrain painting");
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
        if (reloadedTile.sideTextureId != null) {
            throw new AssertionError("A tile with no side texture should round-trip as null, not a placeholder");
        }
    }

    private static void verifyRemoveTextureBlockedBySideReference() {
        ProjectDocument document = new ProjectDocument("Welt");
        document.addTexture(new TextureAsset("grass", "grass.png"));
        document.addTexture(new TextureAsset("cliff", "cliff.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("plateau", "grass", "cliff", true));
        document.addTileset(tileset);

        try {
            document.removeTexture("cliff");
            throw new AssertionError("Removing a texture only used as a side texture should still fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifySideTextureRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Welt mit Seitentextur");
        document.addTexture(new TextureAsset("grass", "grass.png"));
        document.addTexture(new TextureAsset("cliff", "cliff.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("plateau", "grass", "cliff", true));
        document.addTileset(tileset);

        Path directory = Files.createTempDirectory("trackside-editor-project-side-texture");
        ProjectFile.save(document, directory);
        TileEntry reloaded = ProjectFile.load(directory).findTileset("overworld").findTile("plateau");
        if (!"cliff".equals(reloaded.sideTextureId)) throw new AssertionError("Side texture id did not round-trip");
    }

    private static void verifyModelCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Modellwelt");
        CommandHistory history = new CommandHistory(document);

        ModelAsset house = ModelAsset.imported("house", "house.gltf", false, -1.8f, 0f, -1.3f, 2.8f, 4f, 2.3f);
        if (house.getHeight() != 4f) throw new AssertionError("Height should derive from bounds and scale");
        if (!"gltf:models/house.gltf".equals(house.getSource())) throw new AssertionError("Wrong source string");

        history.perform(new ImportModelCommand(house));
        if (document.findModel("house") == null) throw new AssertionError("Model import did not apply");

        history.perform(new RemoveModelCommand(house));
        if (document.findModel("house") != null) throw new AssertionError("Model removal did not apply");
        history.undo();
        if (document.findModel("house") == null) throw new AssertionError("Undo did not restore the model");

        try {
            document.addModel(house);
            throw new AssertionError("Adding a duplicate model id should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }

        Path directory = Files.createTempDirectory("trackside-editor-project-models");
        ProjectFile.save(document, directory);
        ModelAsset reloaded = ProjectFile.load(directory).findModel("house");
        if (reloaded == null) throw new AssertionError("Model did not round-trip");
        if (reloaded.binary != house.binary || reloaded.getHeight() != house.getHeight()
            || reloaded.boundsMaxX != house.boundsMaxX || reloaded.collisionMaxX != house.collisionMaxX) {
            throw new AssertionError("Model fields did not round-trip");
        }
    }

    private static void verifyMapCommandsAndTerrainGrid() {
        ProjectDocument document = new ProjectDocument("Kartenwelt");
        CommandHistory history = new CommandHistory(document);
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);

        history.perform(new CreateMapCommand("valley", 4, 3, "overworld"));
        MapAsset map = document.findMap("valley");
        if (map == null) throw new AssertionError("Map creation did not apply");
        if (map.getShape(0, 0) != TileShape.FLAT || map.getHeight(0, 0) != 0f) {
            throw new AssertionError("A fresh map must start flat at height zero");
        }

        history.perform(new PaintTilesCommand("valley",
            Collections.singletonList(new PaintTilesCommand.Edit(1, 1, null, "grass"))));
        if (!"grass".equals(map.getTile(1, 1))) throw new AssertionError("Tile paint did not apply");
        history.undo();
        if (map.getTile(1, 1) != null) throw new AssertionError("Undo did not clear the painted tile");
        history.redo();

        try {
            history.perform(new PaintTilesCommand("valley", Collections.singletonList(
                new PaintTilesCommand.Edit(2, 1, null, "unknown-tile"))));
            throw new AssertionError("Painting an unknown tile id should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-tile" is not in the map's tileset.
        }

        // A stroke where the first cell is valid and the second is not must apply neither: a
        // partly-applied stroke would leave an undo-stack entry that doesn't match its own effect.
        try {
            history.perform(new PaintTilesCommand("valley", java.util.Arrays.asList(
                new PaintTilesCommand.Edit(3, 0, null, "grass"),
                new PaintTilesCommand.Edit(3, 1, null, "unknown-tile"))));
            throw new AssertionError("A stroke with any invalid cell should fail entirely");
        } catch (IllegalArgumentException expected) {
            if (map.getTile(3, 0) != null) {
                throw new AssertionError("A rejected stroke must not leave earlier cells painted");
            }
        }

        // A ramp climbing to level 1, matching the engine's own testfield: two ramp midpoints then a plateau.
        history.perform(new PaintTerrainCommand("valley", java.util.Arrays.asList(
            new PaintTerrainCommand.Edit(0, 0, 0f, TileShape.FLAT, 0.5f, TileShape.RAMP_SOUTH),
            new PaintTerrainCommand.Edit(0, 1, 0f, TileShape.FLAT, 1f, TileShape.FLAT))));
        if (map.getHeight(0, 0) != 0.5f || map.getShape(0, 0) != TileShape.RAMP_SOUTH) {
            throw new AssertionError("Ramp terrain paint did not apply");
        }
        history.undo();
        if (map.getHeight(0, 0) != 0f || map.getShape(0, 0) != TileShape.FLAT) {
            throw new AssertionError("Undo did not restore the previous terrain");
        }

        try {
            map.setShape(0, 0, TileShape.RAMP_NORTH);
            throw new AssertionError("A shape change that no longer fits the stored height should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: height 0 is not a valid ramp midpoint.
        }

        history.perform(new PaintCollisionCommand("valley",
            Collections.singletonList(new PaintCollisionCommand.Edit(3, 2, false, true))));
        if (!map.isBlocked(3, 2)) throw new AssertionError("Collision paint did not apply");
        history.undo();
        if (map.isBlocked(3, 2)) throw new AssertionError("Undo did not clear the collision flag");

        try {
            document.removeTileset("overworld");
            throw new AssertionError("Removing a tileset still used by a map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the map still references it.
        }

        history.perform(new RemoveMapCommand(map));
        if (document.findMap("valley") != null) throw new AssertionError("Map removal did not apply");
        history.undo();
        if (document.findMap("valley") == null) throw new AssertionError("Undo did not restore the map");
        if (!"grass".equals(document.findMap("valley").getTile(1, 1))) {
            throw new AssertionError("Restoring a removed map must keep its painted cells");
        }
    }

    private static void verifyMapRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Kartenexport");
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);

        MapAsset map = new MapAsset("valley", 3, 2, "overworld");
        map.setTile(0, 0, "grass");
        map.setTerrain(1, 0, 0.5f, TileShape.RAMP_EAST);
        map.setBlocked(2, 1, true);
        document.addMap(map);

        Path directory = Files.createTempDirectory("trackside-editor-project-map");
        ProjectFile.save(document, directory);
        MapAsset reloaded = ProjectFile.load(directory).findMap("valley");
        if (reloaded == null || reloaded.width != 3 || reloaded.depth != 2) {
            throw new AssertionError("Map dimensions did not round-trip");
        }
        if (!"overworld".equals(reloaded.tilesetId)) throw new AssertionError("Map tileset reference did not round-trip");
        if (!"grass".equals(reloaded.getTile(0, 0))) throw new AssertionError("Tile layer did not round-trip");
        if (reloaded.getTile(1, 1) != null) throw new AssertionError("An unpainted cell should round-trip as null");
        if (reloaded.getShape(1, 0) != TileShape.RAMP_EAST || reloaded.getHeight(1, 0) != 0.5f) {
            throw new AssertionError("Terrain shape/height did not round-trip");
        }
        if (!reloaded.isBlocked(2, 1)) throw new AssertionError("Collision layer did not round-trip");
        if (reloaded.isBlocked(0, 0)) throw new AssertionError("Unblocked cells should round-trip as unblocked");
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
