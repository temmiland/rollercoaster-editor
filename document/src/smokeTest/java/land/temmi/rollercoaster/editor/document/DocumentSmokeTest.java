package land.temmi.rollercoaster.editor.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

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
        verifySpriteCommandsAndRoundtrip();
        verifyMapCommandsAndTerrainGrid();
        verifyMapRoundtrip();
        verifyPropCommandsAndRoundtrip();
        verifyLightCommandsAndRoundtrip();
        verifyTransitionCommandsAndRoundtrip();
        verifyDialogueCommandsAndRoundtrip();
        verifyEventCommandsAndRoundtrip();
        System.out.println("PASS: undo/redo, dirty tracking after a branching edit, an atomic project.json "
            + "roundtrip across a moved directory, texture/tileset commands with referential integrity, "
            + "model import/export, map terrain painting, prop placement, map lighting, map transitions, "
            + "dialogue trees, and map events");
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

        ModelAsset house = ModelAsset.imported("house", "house.gltf", false,
            -1.8f, 0f, -1.3f, 2.8f, 4f, 2.3f, List.of("house.bin"));
        if (house.getHeight() != 4f) throw new AssertionError("Height should derive from bounds and scale");
        if (!"gltf:models/house.gltf".equals(house.getSource())) throw new AssertionError("Wrong source string");

        history.perform(new ImportModelCommand(house));
        if (document.findModel("house") == null) throw new AssertionError("Model import did not apply");

        history.perform(new RemoveModelCommand(house));
        if (document.findModel("house") != null) throw new AssertionError("Model removal did not apply");
        history.undo();
        if (document.findModel("house") == null) throw new AssertionError("Undo did not restore the model");

        ModelAsset configuredHouse = house.withPlacement(0.25f, 1f, -0.5f, 1.5f,
            -1, 2, -2, 3, true, true, 2f);
        history.perform(new UpdateModelCommand(house, configuredHouse));
        ModelAsset updated = document.findModel("house");
        if (updated.scale != 1.5f || updated.offsetY != 1f || updated.collisionMaxZ != 3
            || !updated.alignToSlope || !updated.walkable || updated.walkHeight != 2f) {
            throw new AssertionError("Model metadata update did not apply");
        }
        history.undo();
        if (document.findModel("house").scale != 1f) throw new AssertionError("Undo did not restore model metadata");

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
        if (!reloaded.getDependencyFileNames().equals(List.of("house.bin"))) {
            throw new AssertionError("Model dependencies did not round-trip");
        }
    }

    private static void verifySpriteCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Spritewelt");
        CommandHistory history = new CommandHistory(document);
        SpriteAsset player = sprite("player", "player.png", 3, 4, 1.5f, 0.12f, 0.1f);
        history.perform(new ImportSpriteCommand(player));
        if (document.findSprite("player") == null) throw new AssertionError("Sprite import did not apply");
        SpriteAsset updated = sprite("player", "player.png", 3, 4, 2f, 0.2f, 0f);
        history.perform(new UpdateSpriteCommand(player, updated));
        if (document.findSprite("player").worldHeight != 2f) throw new AssertionError("Sprite update did not apply");
        history.undo();
        if (document.findSprite("player").footOffset != 0.1f) throw new AssertionError("Undo did not restore sprite settings");

        Path directory = Files.createTempDirectory("trackside-editor-project-sprites");
        ProjectFile.save(document, directory);
        SpriteAsset reloaded = ProjectFile.load(directory).findSprite("player");
        if (reloaded == null || reloaded.columns != 3 || reloaded.rows != 4 || reloaded.worldHeight != 1.5f
            || reloaded.direction(SpriteDirection.WEST).getWalkFrames().size() != 2) {
            throw new AssertionError("Sprite did not round-trip");
        }
    }

    private static SpriteAsset sprite(String id, String file, int columns, int rows, float height,
                                      float duration, float footOffset) {
        EnumMap<SpriteDirection, SpriteAnimationAsset> directions = new EnumMap<>(SpriteDirection.class);
        for (SpriteDirection direction : SpriteDirection.values()) {
            int firstFrame = direction.ordinal() * 3;
            directions.put(direction, new SpriteAnimationAsset(firstFrame, List.of(firstFrame + 1, firstFrame + 2)));
        }
        return new SpriteAsset(id, file, columns, rows, height, duration, footOffset, directions);
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

        history.perform(new ResizeMapCommand("valley", 5, 4));
        if (map.width != 5 || map.depth != 4 || !"grass".equals(map.getTile(1, 1))) {
            throw new AssertionError("Map resize did not preserve overlapping terrain cells");
        }
        if (map.getTile(4, 3) != null || map.getHeight(4, 3) != 0f || map.getShape(4, 3) != TileShape.FLAT) {
            throw new AssertionError("New map cells should start as empty, flat terrain");
        }
        history.undo();
        if (map.width != 4 || map.depth != 3 || !"grass".equals(map.getTile(1, 1))) {
            throw new AssertionError("Undo did not restore the original map size");
        }

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

    private static void verifyPropCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Requisitenwelt");
        CommandHistory history = new CommandHistory(document);
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);
        document.addModel(ModelAsset.imported("house", "house.gltf", false,
            -1.8f, 0f, -1.3f, 2.8f, 4f, 2.3f, List.of("house.bin")));
        history.perform(new CreateMapCommand("valley", 4, 3, "overworld"));
        MapEntityAsset playerStart = new MapEntityAsset("player-start", "player", null, 1, 0);
        history.perform(new PlaceEntityCommand("valley", playerStart));
        if (document.findMap("valley").findEntity("player-start") == null) {
            throw new AssertionError("Entity placement did not apply");
        }
        history.perform(new UpdateEntityCommand("valley", playerStart,
            new MapEntityAsset("player-start", "npc", null, 1, 1)));
        if (!"npc".equals(document.findMap("valley").findEntity("player-start").type)) {
            throw new AssertionError("Entity update did not apply");
        }
        history.undo();
        if (!"player".equals(document.findMap("valley").findEntity("player-start").type)) {
            throw new AssertionError("Undo did not restore entity metadata");
        }

        try {
            history.perform(new PlacePropCommand("valley",
                new MapProp("house-1", "unknown-model", 1f, 1f, 0f, 0f)));
            throw new AssertionError("Placing a prop with an unknown model should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-model" is not a registered model.
        }

        try {
            new MapProp("invalid", "house", Float.NaN, 1f, 0f, 0f);
            throw new AssertionError("A prop transform with NaN should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: every saved transform component is finite.
        }

        try {
            history.perform(new PlacePropCommand("valley",
                new MapProp("outside", "house", 4f, 1f, 0f, 0f)));
            throw new AssertionError("Placing a prop outside its map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the anchor has to remain within the map grid.
        }

        MapProp house = new MapProp("house-1", "house", 2f, 1f, 0f, 0f);
        history.perform(new PlacePropCommand("valley", house));
        MapAsset map = document.findMap("valley");
        if (map.findProp("house-1") == null) throw new AssertionError("Prop placement did not apply");

        try {
            history.perform(new ResizeMapCommand("valley", 2, 3));
            throw new AssertionError("Shrinking a map past a prop anchor should fail");
        } catch (IllegalArgumentException expected) {
            if (map.width != 4 || map.depth != 3 || map.findProp("house-1") == null) {
                throw new AssertionError("A rejected resize must leave the map and props untouched");
            }
        }

        history.perform(new TransformPropCommand("valley", "house-1", "house", 2f, 1f, 0f, 0f, 3f, 2f, 0.5f, 90f));
        MapProp moved = map.findProp("house-1");
        if (moved.x != 3f || moved.z != 2f || moved.elevation != 0.5f || moved.rotation != 90f) {
            throw new AssertionError("Transform did not apply");
        }
        history.undo();
        MapProp restored = map.findProp("house-1");
        if (restored.x != 2f || restored.z != 1f || restored.rotation != 0f) {
            throw new AssertionError("Undo did not restore the previous transform");
        }

        try {
            history.perform(new TransformPropCommand("valley", "house-1", "house",
                2f, 1f, 0f, 0f, 4f, 1f, 0f, 0f));
            throw new AssertionError("Moving a prop outside its map should fail");
        } catch (IllegalArgumentException expected) {
            MapProp unchanged = map.findProp("house-1");
            if (unchanged == null || unchanged.x != 2f || unchanged.z != 1f) {
                throw new AssertionError("A rejected transform must leave the prop untouched");
            }
        }

        history.perform(new PlacePropCommand("valley", new MapProp("house-2", "house", 1f, 2f, 0f, 0f)));
        try {
            document.removeModel("house");
            throw new AssertionError("Removing a model still placed on a map should fail");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("house-1") || !expected.getMessage().contains("house-2")) {
                throw new AssertionError("The blocked removal should list every affected prop", expected);
            }
        }

        history.perform(new RemovePropCommand("valley", house));
        if (map.findProp("house-1") != null) throw new AssertionError("Prop removal did not apply");
        history.undo();
        if (map.findProp("house-1") == null) throw new AssertionError("Undo did not restore the removed prop");

        Path directory = Files.createTempDirectory("trackside-editor-project-props");
        ProjectFile.save(document, directory);
        MapAsset reloadedMap = ProjectFile.load(directory).findMap("valley");
        MapProp reloadedProp = reloadedMap.findProp("house-1");
        if (reloadedProp == null || !"house".equals(reloadedProp.modelId) || reloadedProp.x != 2f
            || reloadedProp.z != 1f || reloadedProp.elevation != 0f || reloadedProp.rotation != 0f) {
            throw new AssertionError("Prop did not round-trip");
        }
        MapEntityAsset reloadedStart = reloadedMap.findEntity("player-start");
        if (reloadedStart == null || !"player".equals(reloadedStart.type)
            || reloadedStart.spriteId != null || reloadedStart.x != 1 || reloadedStart.z != 0) {
            throw new AssertionError("Entity did not round-trip");
        }
        ModelAsset reloadedModel = ProjectFile.load(directory).findModel("house");
        if (!reloadedModel.getDependencyFileNames().equals(List.of("house.bin"))) {
            throw new AssertionError("Model dependencies did not round-trip");
        }
    }

    private static void verifyLightCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Beleuchtete Welt");
        CommandHistory history = new CommandHistory(document);
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);
        history.perform(new CreateMapCommand("valley", 4, 3, "overworld"));
        MapAsset map = document.findMap("valley");

        MapLightAsset lamp = new MapLightAsset("lamp-1", 2f, 1.5f, 1f, 1f, 0.9f, 0.7f, 1f, 4f, true);
        history.perform(new PlaceLightCommand("valley", lamp));
        if (map.findLight("lamp-1") == null) throw new AssertionError("Light placement did not apply");

        try {
            history.perform(new PlaceLightCommand("valley",
                new MapLightAsset("outside", 4f, 1.5f, 1f, 1f, 1f, 1f, 1f, 4f, true)));
            throw new AssertionError("Placing a light outside its map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the position has to remain within the map grid.
        }

        try {
            new MapLightAsset("bad-spot", 1f, 1f, 1f, 1f, 1f, 1f, 1f, 4f, true, true, 0f, -1f, 0f, 30f, 20f);
            throw new AssertionError("A spot light with outer <= inner angle should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the cone must widen from inner to outer.
        }

        history.perform(new UpdateLightCommand("valley", lamp,
            new MapLightAsset("lamp-1", 2f, 2f, 1f, 1f, 1f, 1f, 2f, 5f, false)));
        MapLightAsset updated = map.findLight("lamp-1");
        if (updated.y != 2f || updated.intensity != 2f || updated.range != 5f || updated.enabled) {
            throw new AssertionError("Light update did not apply");
        }
        history.undo();
        MapLightAsset restored = map.findLight("lamp-1");
        if (restored.y != 1.5f || restored.intensity != 1f || !restored.enabled) {
            throw new AssertionError("Undo did not restore the previous light");
        }

        MapLightAsset spot = new MapLightAsset("spot-1", 1f, 3f, 1f, 1f, 1f, 1f, 1f, 6f, true,
            true, 0f, -1f, 0f, 20f, 35f);
        history.perform(new PlaceLightCommand("valley", spot));

        for (int i = 0; i < MapAsset.MAX_LIGHTS - 2; i++) {
            history.perform(new PlaceLightCommand("valley",
                new MapLightAsset("filler-" + i, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, true)));
        }
        if (map.getLights().size() != MapAsset.MAX_LIGHTS) {
            throw new AssertionError("Expected exactly " + MapAsset.MAX_LIGHTS + " lights, got " + map.getLights().size());
        }
        try {
            history.perform(new PlaceLightCommand("valley",
                new MapLightAsset("one-too-many", 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, true)));
            throw new AssertionError("Exceeding the engine's shared point/spot light budget should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: LightingEnvironment.MAX_POINT_LIGHTS is 8, shared by point and spot lights.
        }

        history.perform(new RemoveLightCommand("valley", spot));
        if (map.findLight("spot-1") != null) throw new AssertionError("Light removal did not apply");
        history.undo();
        if (map.findLight("spot-1") == null) throw new AssertionError("Undo did not restore the removed light");

        Path directory = Files.createTempDirectory("trackside-editor-project-lights");
        ProjectFile.save(document, directory);
        MapAsset reloadedMap = ProjectFile.load(directory).findMap("valley");
        MapLightAsset reloadedLamp = reloadedMap.findLight("lamp-1");
        if (reloadedLamp == null || reloadedLamp.x != 2f || reloadedLamp.y != 1.5f || reloadedLamp.z != 1f
            || reloadedLamp.colorR != 1f || reloadedLamp.colorG != 0.9f || reloadedLamp.colorB != 0.7f
            || reloadedLamp.intensity != 1f || reloadedLamp.range != 4f || !reloadedLamp.enabled || reloadedLamp.spot) {
            throw new AssertionError("Point light did not round-trip");
        }
        MapLightAsset reloadedSpot = reloadedMap.findLight("spot-1");
        if (reloadedSpot == null || !reloadedSpot.spot || reloadedSpot.innerAngle != 20f
            || reloadedSpot.outerAngle != 35f || reloadedSpot.directionY != -1f) {
            throw new AssertionError("Spot light did not round-trip");
        }
    }

    private static void verifyTransitionCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Verbundene Welt");
        CommandHistory history = new CommandHistory(document);
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);
        history.perform(new CreateMapCommand("valley", 4, 3, "overworld"));
        history.perform(new CreateMapCommand("cave", 3, 3, "overworld"));
        MapAsset valley = document.findMap("valley");

        try {
            history.perform(new PlaceTransitionCommand("valley",
                new MapTransitionAsset("to-nowhere", 1, 1, "unknown-map", 0, 0)));
            throw new AssertionError("A transition targeting an unknown map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-map" is not a registered map.
        }

        try {
            history.perform(new PlaceTransitionCommand("valley",
                new MapTransitionAsset("outside", 9, 1, "cave", 0, 0)));
            throw new AssertionError("Placing a transition outside its map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the tile has to remain within the map grid.
        }

        MapTransitionAsset toCave = new MapTransitionAsset("to-cave", 1, 1, "cave", 0, 0);
        history.perform(new PlaceTransitionCommand("valley", toCave));
        if (valley.findTransition("to-cave") == null) throw new AssertionError("Transition placement did not apply");

        history.perform(new UpdateTransitionCommand("valley", toCave,
            new MapTransitionAsset("to-cave", 1, 1, "cave", 2, 2)));
        MapTransitionAsset updated = valley.findTransition("to-cave");
        if (updated.targetX != 2 || updated.targetZ != 2) throw new AssertionError("Transition update did not apply");
        history.undo();
        MapTransitionAsset restored = valley.findTransition("to-cave");
        if (restored.targetX != 0 || restored.targetZ != 0) {
            throw new AssertionError("Undo did not restore the previous transition");
        }

        try {
            history.perform(new RemoveMapCommand(document.findMap("cave")));
            throw new AssertionError("Removing a map still targeted by a transition should fail");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("to-cave")) {
                throw new AssertionError("The blocked removal should name the offending transition", expected);
            }
        }

        history.perform(new RemoveTransitionCommand("valley", toCave));
        if (valley.findTransition("to-cave") != null) throw new AssertionError("Transition removal did not apply");
        history.undo();
        if (valley.findTransition("to-cave") == null) throw new AssertionError("Undo did not restore the removed transition");

        Path directory = Files.createTempDirectory("trackside-editor-project-transitions");
        ProjectFile.save(document, directory);
        MapAsset reloadedValley = ProjectFile.load(directory).findMap("valley");
        MapTransitionAsset reloaded = reloadedValley.findTransition("to-cave");
        if (reloaded == null || reloaded.x != 1 || reloaded.z != 1 || !"cave".equals(reloaded.targetMapId)
            || reloaded.targetX != 0 || reloaded.targetZ != 0) {
            throw new AssertionError("Transition did not round-trip");
        }
    }

    private static void verifyDialogueCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Gesprächige Welt");
        CommandHistory history = new CommandHistory(document);

        try {
            new DialogueAsset("npc-1-intro", "missing-node",
                List.of(new DialogueNodeAsset("greet", "npc-1", "dialogue.greet", null, List.of())));
            throw new AssertionError("A dialogue whose start node is missing should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: startNode must name one of the dialogue's own nodes.
        }

        try {
            new DialogueAsset("npc-1-intro", "greet", List.of(new DialogueNodeAsset("greet", "npc-1",
                "dialogue.greet", null, List.of(new DialogueResponseAsset("dialogue.greet.yes", "nowhere", List.of())))));
            throw new AssertionError("A response targeting an unknown node should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: a response's targetNode must name a node in the same dialogue.
        }

        DialogueNodeAsset explain = new DialogueNodeAsset("explain", "npc-1", "dialogue.explain", "npc1_face", List.of());
        DialogueNodeAsset greet = new DialogueNodeAsset("greet", "npc-1", "dialogue.greet", "npc1_face", List.of(
            new DialogueResponseAsset("dialogue.greet.yes", "explain",
                List.of(new ConditionAsset(ConditionAsset.Type.FLAG, "met-npc-1",
                    ConditionAsset.Comparison.NOT_EQUALS, "true"))),
            new DialogueResponseAsset("dialogue.greet.no", null, List.of())));
        DialogueAsset intro = new DialogueAsset("npc-1-intro", "greet", List.of(greet, explain));
        history.perform(new RegisterDialogueCommand(intro));
        if (document.findDialogue("npc-1-intro") == null) throw new AssertionError("Dialogue registration did not apply");

        DialogueAsset renamed = new DialogueAsset("npc-1-intro", "explain", List.of(greet, explain));
        history.perform(new UpdateDialogueCommand(intro, renamed));
        if (!"explain".equals(document.findDialogue("npc-1-intro").startNodeId)) {
            throw new AssertionError("Dialogue update did not apply");
        }
        history.undo();
        if (!"greet".equals(document.findDialogue("npc-1-intro").startNodeId)) {
            throw new AssertionError("Undo did not restore the previous dialogue");
        }

        history.perform(new RemoveDialogueCommand(intro));
        if (document.findDialogue("npc-1-intro") != null) throw new AssertionError("Dialogue removal did not apply");
        history.undo();
        if (document.findDialogue("npc-1-intro") == null) throw new AssertionError("Undo did not restore the removed dialogue");

        Path directory = Files.createTempDirectory("trackside-editor-project-dialogues");
        ProjectFile.save(document, directory);
        DialogueAsset reloaded = ProjectFile.load(directory).findDialogue("npc-1-intro");
        if (reloaded == null || !"greet".equals(reloaded.startNodeId) || reloaded.getNodes().size() != 2) {
            throw new AssertionError("Dialogue did not round-trip");
        }
        DialogueNodeAsset reloadedGreet = reloaded.findNode("greet");
        if (reloadedGreet == null || reloadedGreet.getResponses().size() != 2) {
            throw new AssertionError("Dialogue node did not round-trip");
        }
        DialogueResponseAsset reloadedYes = reloadedGreet.getResponses().get(0);
        if (!"explain".equals(reloadedYes.targetNodeId) || reloadedYes.getConditions().size() != 1
            || reloadedYes.getConditions().get(0).comparison != ConditionAsset.Comparison.NOT_EQUALS) {
            throw new AssertionError("Dialogue response and its condition did not round-trip");
        }
        if (reloadedGreet.getResponses().get(1).targetNodeId != null) {
            throw new AssertionError("A response with no target should end the dialogue");
        }
    }

    private static void verifyEventCommandsAndRoundtrip() throws IOException {
        ProjectDocument document = new ProjectDocument("Ereignisreiche Welt");
        CommandHistory history = new CommandHistory(document);
        document.addTexture(new TextureAsset("grass", "grass.png"));
        TilesetAsset tileset = new TilesetAsset("overworld");
        tileset.addTile(new TileEntry("grass", "grass", true));
        document.addTileset(tileset);
        history.perform(new CreateMapCommand("valley", 4, 3, "overworld"));
        history.perform(new CreateMapCommand("cave", 3, 3, "overworld"));
        MapAsset valley = document.findMap("valley");
        history.perform(new PlaceEntityCommand("valley", new MapEntityAsset("npc-1", "npc", null, 1, 1)));
        history.perform(new PlaceLightCommand("valley", new MapLightAsset("lamp-1", 1f, 1.5f, 1f, 1f, 1f, 1f, 1f, 4f, true)));
        history.perform(new RegisterDialogueCommand(new DialogueAsset("npc-1-intro", "greet",
            List.of(new DialogueNodeAsset("greet", "npc-1", "dialogue.greet", null, List.of())))));

        try {
            history.perform(new PlaceEventCommand("valley", new GameEventAsset("bad-trigger",
                new EventTriggerAsset(EventTriggerAsset.Type.INTERACTION, "unknown-npc", 0, 0, null),
                List.of(), List.of(EventActionAsset.setFlag("met-npc-1", "true")))));
            throw new AssertionError("A trigger referencing an unknown entity should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-npc" is not an entity on this map.
        }

        try {
            history.perform(new PlaceEventCommand("valley", new GameEventAsset("bad-dialogue",
                EventTriggerAsset.interaction("npc-1"), List.of(),
                List.of(EventActionAsset.startDialogue("unknown-dialogue")))));
            throw new AssertionError("An action referencing an unknown dialogue should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-dialogue" is not a registered dialogue.
        }

        try {
            history.perform(new PlaceEventCommand("valley", new GameEventAsset("bad-map",
                new EventTriggerAsset(EventTriggerAsset.Type.MAP_START, null, 0, 0, null), List.of(),
                List.of(EventActionAsset.changeMap("unknown-map", 0, 0)))));
            throw new AssertionError("An action referencing an unknown map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: "unknown-map" is not a registered map.
        }

        try {
            history.perform(new PlaceEventCommand("valley", new GameEventAsset("bad-area",
                new EventTriggerAsset(EventTriggerAsset.Type.ENTER_AREA, null, 9, 0, null), List.of(),
                List.of(EventActionAsset.setFlag("stepped", "true")))));
            throw new AssertionError("An ENTER_AREA trigger outside the map should fail");
        } catch (IllegalArgumentException expected) {
            // Expected: the trigger tile has to remain within the map grid.
        }

        GameEventAsset greetEvent = new GameEventAsset("npc-1-greet", EventTriggerAsset.interaction("npc-1"),
            List.of(new ConditionAsset(ConditionAsset.Type.FLAG, "met-npc-1", ConditionAsset.Comparison.NOT_EQUALS, "true")),
            List.of(EventActionAsset.startDialogue("npc-1-intro"), EventActionAsset.setFlag("met-npc-1", "true")));
        history.perform(new PlaceEventCommand("valley", greetEvent));
        if (valley.findEvent("npc-1-greet") == null) throw new AssertionError("Event placement did not apply");

        GameEventAsset toggled = new GameEventAsset("npc-1-greet", EventTriggerAsset.interaction("npc-1"),
            List.of(), List.of(EventActionAsset.toggleLight("lamp-1", false)));
        history.perform(new UpdateEventCommand("valley", greetEvent, toggled));
        GameEventAsset updated = valley.findEvent("npc-1-greet");
        if (updated.getActions().size() != 1 || updated.getActions().get(0).type != EventActionAsset.Type.TOGGLE_LIGHT) {
            throw new AssertionError("Event update did not apply");
        }
        history.undo();
        GameEventAsset restored = valley.findEvent("npc-1-greet");
        if (restored.getActions().size() != 2) throw new AssertionError("Undo did not restore the previous event");

        try {
            document.removeDialogue("npc-1-intro");
            throw new AssertionError("Removing a dialogue still started by an event should fail");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("npc-1-greet")) {
                throw new AssertionError("The blocked removal should name the offending event", expected);
            }
        }

        try {
            history.perform(new RemoveMapCommand(document.findMap("cave")));
        } catch (IllegalArgumentException unexpected) {
            throw new AssertionError("Removing an unrelated map should not be blocked by this map's events", unexpected);
        }

        history.perform(new RemoveEventCommand("valley", greetEvent));
        if (valley.findEvent("npc-1-greet") != null) throw new AssertionError("Event removal did not apply");
        history.undo();
        if (valley.findEvent("npc-1-greet") == null) throw new AssertionError("Undo did not restore the removed event");

        Path directory = Files.createTempDirectory("trackside-editor-project-events");
        ProjectFile.save(document, directory);
        MapAsset reloadedValley = ProjectFile.load(directory).findMap("valley");
        GameEventAsset reloaded = reloadedValley.findEvent("npc-1-greet");
        if (reloaded == null || reloaded.trigger.type != EventTriggerAsset.Type.INTERACTION
            || !"npc-1".equals(reloaded.trigger.entityId) || reloaded.getConditions().size() != 1
            || reloaded.getActions().size() != 2) {
            throw new AssertionError("Event did not round-trip");
        }
        if (reloaded.getActions().get(0).type != EventActionAsset.Type.START_DIALOGUE
            || !"npc-1-intro".equals(reloaded.getActions().get(0).targetId)) {
            throw new AssertionError("Event action did not round-trip");
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
