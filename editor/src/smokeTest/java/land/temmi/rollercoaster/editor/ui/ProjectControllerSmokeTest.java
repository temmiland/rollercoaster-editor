package land.temmi.rollercoaster.editor.ui;

import com.badlogic.gdx.files.FileHandle;
import land.temmi.rollercoaster.asset.ModelDefinition;
import land.temmi.rollercoaster.asset.ModelManifest;
import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.ModelAsset;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileShape;
import land.temmi.rollercoaster.world.LoadedMap;
import land.temmi.rollercoaster.world.MapLoader;
import land.temmi.rollercoaster.world.TileDefinition;
import land.temmi.rollercoaster.world.TileSurface;
import land.temmi.rollercoaster.world.Tileset;
import land.temmi.rollercoaster.world.TilesetManifest;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Checks ProjectController's new/open/save/saveAs and autosave-restore logic; no GUI involved. */
public final class ProjectControllerSmokeTest {
    public static void main(String[] args) throws IOException {
        verifyNewRenameSaveUndoRedo();
        verifyAutosaveDetectionAndRestore();
        verifySaveAsLeavesTheOriginalUntouched();
        verifyOpenProjectReadsWhatWasSaved();
        verifyOperationsRequireAnOpenProject();
        verifyTextureImportAndTilesetExport();
        verifyExportWithSideTexture();
        verifyModelImportAndExport();
        verifyMapCreationPaintingAndExport();
        System.out.println("PASS: ProjectController new/rename/save/undo/redo, autosave detection and restore, "
            + "saveAs isolation, a texture-import-to-tileset-export roundtrip read back by the real parser, "
            + "a separately packed side texture, a model import/export roundtrip, "
            + "and a map creation/paint/export roundtrip read back by the real MapLoader parser");
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

    private static void verifyTextureImportAndTilesetExport() throws IOException {
        Path projectDirectory = Files.createTempDirectory("trackside-editor-controller-assets");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Textured World");

        Path grassPng = solidColorPng("grass", 16, 16, Color.GREEN);
        TextureAsset grass = controller.importTexture(grassPng, "grass");
        if (!Files.exists(projectDirectory.resolve("sources/textures/grass.png"))) {
            throw new AssertionError("importTexture did not copy the file into the project");
        }
        if (controller.getTextures().size() != 1) throw new AssertionError("Texture was not added to the document");

        controller.createTileset("overworld");
        controller.addTile("overworld", "grass", grass.id, null, true);
        if (controller.getTilesets().get(0).getTiles().size() != 1) throw new AssertionError("Tile was not added");

        try {
            controller.removeTexture(grass);
            throw new AssertionError("Removing a texture still used by a tile should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }

        controller.exportTileset("overworld");
        Path manifestFile = projectDirectory.resolve("catalogs/overworld.json");
        Path textureFile = projectDirectory.resolve("catalogs/overworld.png");
        if (!Files.exists(manifestFile) || !Files.exists(textureFile)) {
            throw new AssertionError("exportTileset did not write both files");
        }

        TilesetManifest manifest = TilesetManifest.load(new FileHandle(manifestFile.toFile()));
        if (manifest.tiles.size != 1 || !"grass".equals(manifest.tiles.first().id)) {
            throw new AssertionError("Exported manifest does not match the tileset");
        }
        if (!manifest.tiles.first().walkable) throw new AssertionError("Walkable flag lost on export");

        controller.undo(); // undoes addTile
        controller.undo(); // undoes createTileset
        controller.undo(); // undoes importTexture
        if (!controller.getTextures().isEmpty() || !controller.getTilesets().isEmpty()) {
            throw new AssertionError("Undo did not fully unwind the texture/tileset edits");
        }
    }

    private static void verifyExportWithSideTexture() throws IOException {
        Path projectDirectory = Files.createTempDirectory("trackside-editor-controller-side-texture");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Cliff World");

        TextureAsset grass = controller.importTexture(solidColorPng("grass", 16, 16, Color.GREEN), "grass");
        TextureAsset cliff = controller.importTexture(solidColorPng("cliff", 16, 16, Color.DARK_GRAY), "cliff");
        controller.createTileset("overworld");
        controller.addTile("overworld", "plateau", grass.id, cliff.id, true);
        controller.exportTileset("overworld");

        TilesetManifest manifest = TilesetManifest.load(
            new FileHandle(projectDirectory.resolve("catalogs/overworld.json").toFile()));
        TileDefinition definition = manifest.tiles.first();
        if (definition.sideX == definition.atlasX && definition.sideY == definition.atlasY) {
            throw new AssertionError("Side texture should pack to a different atlas region than the top");
        }
    }

    private static void verifyModelImportAndExport() throws IOException {
        Path projectDirectory = Files.createTempDirectory("trackside-editor-controller-models");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Model World");

        Path sourceDirectory = Files.createTempDirectory("trackside-editor-model-source");
        Path gltfFile = sourceDirectory.resolve("house.gltf");
        Path binFile = sourceDirectory.resolve("house.bin");
        Files.writeString(gltfFile, "{}"); // content is irrelevant here - only the copy/manifest logic is under test
        Files.writeString(binFile, "binary-placeholder");

        ModelAsset house = controller.importModel(gltfFile, List.of(binFile), "house",
            -1.8f, 0f, -1.3f, 2.8f, 4f, 2.3f);
        if (!Files.exists(projectDirectory.resolve("sources/models/house.gltf"))
            || !Files.exists(projectDirectory.resolve("sources/models/house.bin"))) {
            throw new AssertionError("importModel did not copy the primary file and its dependency");
        }
        if (house.getHeight() != 4f) throw new AssertionError("Height was not derived from bounds and scale");
        if (controller.getModels().size() != 1) throw new AssertionError("Model was not added to the document");

        controller.exportModels();
        Path exportedGltf = projectDirectory.resolve("catalogs/models/house.gltf");
        Path exportedBin = projectDirectory.resolve("catalogs/models/house.bin");
        Path manifestFile = projectDirectory.resolve("catalogs/models.json");
        if (!Files.exists(exportedGltf) || !Files.exists(exportedBin) || !Files.exists(manifestFile)) {
            throw new AssertionError("exportModels did not write the model, its dependency, and the manifest");
        }

        com.badlogic.gdx.utils.Array<ModelDefinition> definitions =
            ModelManifest.load(new FileHandle(manifestFile.toFile()));
        if (definitions.size != 1) throw new AssertionError("Expected 1 model in the exported manifest");
        ModelDefinition definition = definitions.first();
        if (!"house".equals(definition.id) || !"gltf:models/house.gltf".equals(definition.source)) {
            throw new AssertionError("Exported model id/source is wrong");
        }
        if (definition.height != 4f || definition.boundsMaxX != 2.8f) {
            throw new AssertionError("Exported model bounds/height are wrong");
        }

        controller.updateModel(house, 0.5f, 1f, -0.25f, 1.5f,
            -2, 3, -1, 2, true, true, 2.5f);
        Path cabinFile = sourceDirectory.resolve("cabin.gltf");
        Path cabinBinFile = sourceDirectory.resolve("cabin.bin");
        Files.writeString(cabinFile, "updated-model");
        Files.writeString(cabinBinFile, "updated-binary-placeholder");
        ModelAsset configuredHouse = controller.getModels().get(0);
        controller.reimportModel(configuredHouse, cabinFile, List.of(cabinBinFile),
            -2f, -0.5f, -2f, 3f, 6f, 4f);
        ModelAsset reimported = controller.getModels().get(0);
        if (!"house".equals(reimported.id) || !"cabin.gltf".equals(reimported.fileName)
            || !List.of("cabin.bin").equals(reimported.getDependencyFileNames())) {
            throw new AssertionError("Reimport must preserve the ID and replace every source file");
        }
        if (reimported.scale != 1.5f || reimported.offsetX != 0.5f || !reimported.walkable
            || reimported.boundsMaxY != 6f) {
            throw new AssertionError("Reimport lost placement settings or did not update model bounds");
        }
        controller.undo();
        if (!"house.gltf".equals(controller.getModels().get(0).fileName)) {
            throw new AssertionError("Undoing a reimport must restore its previous source metadata");
        }
        controller.redo();
        controller.exportModels();
        ModelDefinition reimportedDefinition = ModelManifest.load(new FileHandle(manifestFile.toFile())).first();
        if (!"gltf:models/cabin.gltf".equals(reimportedDefinition.source)
            || reimportedDefinition.boundsMaxY != 6f
            || !Files.exists(projectDirectory.resolve("catalogs/models/cabin.bin"))) {
            throw new AssertionError("Export did not use the reimported source and bounds");
        }

        controller.removeModel(house);
        if (!controller.getModels().isEmpty()) throw new AssertionError("removeModel did not apply");
        controller.undo();
        if (controller.getModels().isEmpty()) throw new AssertionError("Undo did not restore the model");
    }

    private static Path solidColorPng(String name, int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        Path file = Files.createTempDirectory("trackside-editor-texture-source").resolve(name + ".png");
        javax.imageio.ImageIO.write(image, "PNG", file.toFile());
        return file;
    }

    private static void verifyMapCreationPaintingAndExport() throws IOException {
        Path projectDirectory = Files.createTempDirectory("trackside-editor-controller-map");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Valley World");

        TextureAsset grass = controller.importTexture(solidColorPng("grass", 16, 16, Color.GREEN), "grass");
        controller.createTileset("overworld");
        controller.addTile("overworld", "grass", grass.id, null, true);

        controller.createMap("valley", 3, 2, "overworld");
        if (controller.getMaps().size() != 1) throw new AssertionError("Map was not added to the document");
        MapAsset map = controller.getMaps().get(0);

        controller.paintTiles("valley", List.of(
            new PaintTilesCommand.Edit(0, 0, null, "grass"),
            new PaintTilesCommand.Edit(1, 0, null, "grass"),
            new PaintTilesCommand.Edit(2, 0, null, "grass"),
            new PaintTilesCommand.Edit(0, 1, null, "grass"),
            new PaintTilesCommand.Edit(1, 1, null, "grass"),
            new PaintTilesCommand.Edit(2, 1, null, "grass")));
        controller.paintTerrain("valley", Collections.singletonList(
            new PaintTerrainCommand.Edit(1, 0, 0f, TileShape.FLAT, 0.5f, TileShape.RAMP_EAST)));
        controller.paintCollision("valley", Collections.singletonList(
            new PaintCollisionCommand.Edit(2, 1, false, true)));

        try {
            controller.exportMap("valley");
        } catch (IOException unexpected) {
            throw new AssertionError("A fully painted map should export cleanly", unexpected);
        }

        Path mapFile = projectDirectory.resolve("maps/valley.json");
        if (!Files.exists(mapFile)) throw new AssertionError("exportMap did not write the map file");

        Tileset tileset = new Tileset().add(new TileSurface("grass"));
        LoadedMap loaded = new MapLoader().load(new FileHandle(mapFile.toFile()), tileset);
        if (loaded.tiles.getWidth() != 3 || loaded.tiles.getDepth() != 2) {
            throw new AssertionError("Exported map size is wrong");
        }
        if (loaded.tiles.getHeight(1, 0) != 0.5f) throw new AssertionError("Exported ramp height is wrong");
        if (!loaded.tiles.isBlocked(2, 1)) throw new AssertionError("Exported collision flag is wrong");

        controller.removeMap(map);
        if (!controller.getMaps().isEmpty()) throw new AssertionError("removeMap did not apply");
        controller.undo();
        if (controller.getMaps().isEmpty()) throw new AssertionError("Undo did not restore the map");
        if (!"grass".equals(controller.getMaps().get(0).getTile(0, 0))) {
            throw new AssertionError("Restoring a removed map must keep its painted cells");
        }
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
