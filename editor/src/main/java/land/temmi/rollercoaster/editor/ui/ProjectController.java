package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.MapExport;
import land.temmi.rollercoaster.editor.asset.ModelManifestExport;
import land.temmi.rollercoaster.editor.asset.TilePacker;
import land.temmi.rollercoaster.editor.asset.TileSource;
import land.temmi.rollercoaster.editor.asset.TilesetExport;
import land.temmi.rollercoaster.editor.document.AddTileCommand;
import land.temmi.rollercoaster.editor.document.CommandHistory;
import land.temmi.rollercoaster.editor.document.CreateMapCommand;
import land.temmi.rollercoaster.editor.document.CreateTilesetCommand;
import land.temmi.rollercoaster.editor.document.ImportModelCommand;
import land.temmi.rollercoaster.editor.document.ImportTextureCommand;
import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapEntityAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.ModelAsset;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.PlacePropCommand;
import land.temmi.rollercoaster.editor.document.PlaceEntityCommand;
import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;
import land.temmi.rollercoaster.editor.document.RemoveMapCommand;
import land.temmi.rollercoaster.editor.document.RemoveModelCommand;
import land.temmi.rollercoaster.editor.document.RemovePropCommand;
import land.temmi.rollercoaster.editor.document.RemoveEntityCommand;
import land.temmi.rollercoaster.editor.document.RemoveTextureCommand;
import land.temmi.rollercoaster.editor.document.RemoveTilesetCommand;
import land.temmi.rollercoaster.editor.document.RemoveTileCommand;
import land.temmi.rollercoaster.editor.document.RenameProjectCommand;
import land.temmi.rollercoaster.editor.document.ResizeMapCommand;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TilesetAsset;
import land.temmi.rollercoaster.editor.document.TransformPropCommand;
import land.temmi.rollercoaster.editor.document.UpdateModelCommand;
import land.temmi.rollercoaster.editor.document.UpdateEntityCommand;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private static final String TEXTURES_DIRECTORY_NAME = "sources/textures";
    private static final String MODELS_DIRECTORY_NAME = "sources/models";
    private static final String CATALOGS_DIRECTORY_NAME = "catalogs";
    private static final String MAPS_DIRECTORY_NAME = "maps";

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

    public List<TextureAsset> getTextures() {
        requireOpen();
        return history.getDocument().getTextures();
    }

    public List<TilesetAsset> getTilesets() {
        requireOpen();
        return history.getDocument().getTilesets();
    }

    /** Copies the source file into the project's sources/textures/ folder, then imports it. */
    public TextureAsset importTexture(Path sourceImageFile, String id) throws IOException {
        requireOpen();
        String fileName = sourceImageFile.getFileName().toString();
        Path destination = texturesDirectory().resolve(fileName);
        if (Files.exists(destination)) {
            throw new IOException("A texture file named '" + fileName + "' is already in this project");
        }
        Files.createDirectories(texturesDirectory());
        Files.copy(sourceImageFile, destination);

        TextureAsset asset = new TextureAsset(id, fileName);
        history.perform(new ImportTextureCommand(asset));
        listener.onProjectChanged();
        return asset;
    }

    public void removeTexture(TextureAsset asset) {
        requireOpen();
        history.perform(new RemoveTextureCommand(asset));
        listener.onProjectChanged();
    }

    public void createTileset(String id) {
        requireOpen();
        history.perform(new CreateTilesetCommand(id));
        listener.onProjectChanged();
    }

    public void removeTileset(TilesetAsset tileset) {
        requireOpen();
        history.perform(new RemoveTilesetCommand(tileset));
        listener.onProjectChanged();
    }

    public void addTile(String tilesetId, String tileId, String textureId, String sideTextureId, boolean walkable) {
        requireOpen();
        history.perform(new AddTileCommand(tilesetId, new TileEntry(tileId, textureId, sideTextureId, walkable)));
        listener.onProjectChanged();
    }

    public void removeTile(String tilesetId, TileEntry entry) {
        requireOpen();
        history.perform(new RemoveTileCommand(tilesetId, entry));
        listener.onProjectChanged();
    }

    /** Packs a tileset's tiles into an atlas and writes texture + manifest to catalogs/. */
    public void exportTileset(String tilesetId) throws IOException {
        requireOpen();
        TilesetAsset tileset = history.getDocument().findTileset(tilesetId);
        if (tileset == null) throw new IOException("No such tileset: " + tilesetId);
        if (tileset.getTiles().isEmpty()) throw new IOException("Tileset '" + tilesetId + "' has no tiles");

        List<TileSource> sources = new ArrayList<>();
        for (TileEntry tile : tileset.getTiles()) {
            Path topFile = requireTextureFile(tile.id, tile.textureId);
            Path sideFile = tile.sideTextureId == null ? null : requireTextureFile(tile.id, tile.sideTextureId);
            sources.add(TileSource.load(tile.id, topFile, sideFile, tile.walkable));
        }
        TilePacker.PackedTileset packed = TilePacker.pack(sources);
        TilesetExport.write(packed, tilesetId, tilesetId + ".png", catalogsDirectory());
    }

    public List<ModelAsset> getModels() {
        requireOpen();
        return history.getDocument().getModels();
    }

    /**
     * Copies the model file and any dependency files (e.g. a GLTF's .bin buffer) into the
     * project's sources/models/ folder, then imports it. Bounds must already be known - the
     * editor's Swing process has no GL context to compute them itself; the caller gets them from
     * PreviewProcess.computeModelBounds(), which runs in the process that does.
     */
    public ModelAsset importModel(Path primaryFile, List<Path> dependencyFiles, String id,
                                  float boundsMinX, float boundsMinY, float boundsMinZ,
                                  float boundsMaxX, float boundsMaxY, float boundsMaxZ) throws IOException {
        requireOpen();
        Files.createDirectories(modelsDirectory());
        String fileName = primaryFile.getFileName().toString();
        List<Path> files = new ArrayList<>();
        files.add(primaryFile);
        files.addAll(dependencyFiles);
        Set<String> importedFileNames = new LinkedHashSet<>();
        List<String> dependencyFileNames = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String importedFileName = file.getFileName().toString();
            if (!importedFileNames.add(importedFileName)) {
                throw new IOException("Model import contains '" + importedFileName + "' more than once");
            }
            if (Files.exists(modelsDirectory().resolve(importedFileName))) {
                throw new IOException("A model file named '" + importedFileName + "' is already in this project");
            }
            if (i > 0) dependencyFileNames.add(importedFileName);
        }
        Files.copy(primaryFile, modelsDirectory().resolve(fileName));
        for (Path dependency : dependencyFiles) {
            Files.copy(dependency, modelsDirectory().resolve(dependency.getFileName().toString()));
        }

        boolean binary = fileName.toLowerCase(Locale.ROOT).endsWith(".glb");
        ModelAsset asset = ModelAsset.imported(id, fileName, binary,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ, dependencyFileNames);
        history.perform(new ImportModelCommand(asset));
        listener.onProjectChanged();
        return asset;
    }

    public void removeModel(ModelAsset asset) {
        requireOpen();
        history.perform(new RemoveModelCommand(asset));
        listener.onProjectChanged();
    }

    public void updateModel(ModelAsset previous, float offsetX, float offsetY, float offsetZ, float scale,
                            int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                            boolean alignToSlope, boolean walkable, float walkHeight) {
        requireOpen();
        ModelAsset replacement = previous.withPlacement(offsetX, offsetY, offsetZ, scale,
            collisionMinX, collisionMaxX, collisionMinZ, collisionMaxZ, alignToSlope, walkable, walkHeight);
        history.perform(new UpdateModelCommand(previous, replacement));
        listener.onProjectChanged();
    }

    /**
     * Replaces a model's copied source files and geometry bounds while retaining its stable ID and
     * placement settings. Existing map props therefore keep referring to the updated model.
     * Old source files are intentionally retained: undoing this document command must still be
     * able to preview and export the former revision.
     */
    public void reimportModel(ModelAsset previous, Path primaryFile, List<Path> dependencyFiles,
                              float boundsMinX, float boundsMinY, float boundsMinZ,
                              float boundsMaxX, float boundsMaxY, float boundsMaxZ) throws IOException {
        requireOpen();
        if (history.getDocument().findModel(previous.id) == null) {
            throw new IllegalArgumentException("No such model: " + previous.id);
        }

        List<Path> files = new ArrayList<>();
        files.add(primaryFile);
        files.addAll(dependencyFiles);
        Set<String> incomingNames = new LinkedHashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!incomingNames.add(name)) {
                throw new IOException("Model import contains '" + name + "' more than once");
            }
            for (ModelAsset other : history.getDocument().getModels()) {
                if (!other.id.equals(previous.id)
                    && (other.fileName.equals(name) || other.getDependencyFileNames().contains(name))) {
                    throw new IOException("A model file named '" + name + "' is already used by model '" + other.id + "'");
                }
            }
        }

        Files.createDirectories(modelsDirectory());
        for (Path file : files) {
            Files.copy(file, modelsDirectory().resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }

        String fileName = primaryFile.getFileName().toString();
        List<String> dependencyFileNames = new ArrayList<>();
        for (Path dependencyFile : dependencyFiles) {
            dependencyFileNames.add(dependencyFile.getFileName().toString());
        }
        boolean binary = fileName.toLowerCase(Locale.ROOT).endsWith(".glb");
        ModelAsset replacement = new ModelAsset(previous.id, fileName, binary,
            previous.offsetX, previous.offsetY, previous.offsetZ, previous.scale,
            boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ,
            previous.collisionMinX, previous.collisionMaxX, previous.collisionMinZ, previous.collisionMaxZ,
            previous.alignToSlope, previous.walkable, previous.walkHeight, dependencyFileNames);
        history.perform(new UpdateModelCommand(previous, replacement));
        listener.onProjectChanged();
    }

    /** Copies every registered model's files into catalogs/models/ and writes one models.json. */
    public Path exportModels() throws IOException {
        requireOpen();
        List<ModelAsset> models = history.getDocument().getModels();
        if (models.isEmpty()) throw new IOException("No models to export");

        Path outputDirectory = catalogsDirectory().resolve("models");
        Files.createDirectories(outputDirectory);
        List<ModelManifestExport.Entry> entries = new ArrayList<>();
        for (ModelAsset model : models) {
            copyModelFile(model.fileName, outputDirectory);
            for (String dependencyFileName : model.getDependencyFileNames()) {
                copyModelFile(dependencyFileName, outputDirectory);
            }
            entries.add(new ModelManifestExport.Entry(model.id, model.getSource(),
                model.offsetX, model.offsetY, model.offsetZ, model.scale, model.getHeight(),
                model.boundsMinX, model.boundsMinY, model.boundsMinZ,
                model.boundsMaxX, model.boundsMaxY, model.boundsMaxZ,
                model.collisionMinX, model.collisionMaxX, model.collisionMinZ, model.collisionMaxZ,
                model.alignToSlope, model.walkable, model.walkHeight));
        }
        ModelManifestExport.write(entries, catalogsDirectory());
        return catalogsDirectory().resolve(ModelManifestExport.FILE_NAME);
    }

    public List<MapAsset> getMaps() {
        requireOpen();
        return history.getDocument().getMaps();
    }

    public void createMap(String id, int width, int depth, String tilesetId) {
        requireOpen();
        history.perform(new CreateMapCommand(id, width, depth, tilesetId));
        listener.onProjectChanged();
    }

    public void removeMap(MapAsset map) {
        requireOpen();
        history.perform(new RemoveMapCommand(map));
        listener.onProjectChanged();
    }

    public void resizeMap(String mapId, int width, int depth) {
        requireOpen();
        history.perform(new ResizeMapCommand(mapId, width, depth));
        listener.onProjectChanged();
    }

    public void paintTiles(String mapId, List<PaintTilesCommand.Edit> edits) {
        requireOpen();
        history.perform(new PaintTilesCommand(mapId, edits));
        listener.onProjectChanged();
    }

    public void paintTerrain(String mapId, List<PaintTerrainCommand.Edit> edits) {
        requireOpen();
        history.perform(new PaintTerrainCommand(mapId, edits));
        listener.onProjectChanged();
    }

    public void paintCollision(String mapId, List<PaintCollisionCommand.Edit> edits) {
        requireOpen();
        history.perform(new PaintCollisionCommand(mapId, edits));
        listener.onProjectChanged();
    }

    /** Writes maps/&lt;id&gt;.json matching MapLoader's schema exactly; returns the file it wrote. */
    public Path exportMap(String mapId) throws IOException {
        requireOpen();
        MapAsset map = history.getDocument().findMap(mapId);
        if (map == null) throw new IOException("No such map: " + mapId);

        int cells = map.width * map.depth;
        String[] tiles = new String[cells];
        float[] heights = new float[cells];
        String[] shapes = new String[cells];
        boolean[] collision = new boolean[cells];
        for (int z = 0; z < map.depth; z++) {
            for (int x = 0; x < map.width; x++) {
                int index = z * map.width + x;
                tiles[index] = map.getTile(x, z);
                heights[index] = map.getHeight(x, z);
                shapes[index] = map.getShape(x, z).toId();
                collision[index] = map.isBlocked(x, z);
            }
        }
        List<MapExport.Prop> props = new ArrayList<>();
        for (MapProp prop : map.getProps()) {
            props.add(new MapExport.Prop(prop.modelId, prop.x, prop.z, prop.elevation, prop.rotation));
        }
        List<MapExport.Entity> entities = new ArrayList<>();
        for (MapEntityAsset entity : map.getEntities()) {
            entities.add(new MapExport.Entity(entity.instanceId, entity.type, entity.spriteId, entity.x, entity.z));
        }
        MapExport.write(map.id, map.width, map.depth, map.tilesetId, tiles, heights, shapes, collision,
            props, entities, mapsDirectory());
        return mapsDirectory().resolve(map.id + ".json");
    }

    public void placeProp(String mapId, MapProp prop) {
        requireOpen();
        history.perform(new PlacePropCommand(mapId, prop));
        listener.onProjectChanged();
    }

    public void placeEntity(String mapId, MapEntityAsset entity) {
        requireOpen();
        history.perform(new PlaceEntityCommand(mapId, entity));
        listener.onProjectChanged();
    }

    public void removeEntity(String mapId, MapEntityAsset entity) {
        requireOpen();
        history.perform(new RemoveEntityCommand(mapId, entity));
        listener.onProjectChanged();
    }

    public void updateEntity(String mapId, MapEntityAsset previous, String type, String spriteId, int x, int z) {
        requireOpen();
        history.perform(new UpdateEntityCommand(mapId, previous,
            new MapEntityAsset(previous.instanceId, type, spriteId, x, z)));
        listener.onProjectChanged();
    }

    public void removeProp(String mapId, MapProp prop) {
        requireOpen();
        history.perform(new RemovePropCommand(mapId, prop));
        listener.onProjectChanged();
    }

    public void transformProp(String mapId, MapProp previous, float newX, float newZ, float newElevation,
                              float newRotation) {
        requireOpen();
        history.perform(new TransformPropCommand(mapId, previous.instanceId, previous.modelId,
            previous.x, previous.z, previous.elevation, previous.rotation, newX, newZ, newElevation, newRotation));
        listener.onProjectChanged();
    }

    private Path modelsDirectory() {
        return projectDirectory.resolve(MODELS_DIRECTORY_NAME);
    }

    private void copyModelFile(String fileName, Path outputDirectory) throws IOException {
        Path source = modelsDirectory().resolve(fileName);
        if (!Files.isRegularFile(source)) throw new IOException("Missing imported model file: " + fileName);
        Files.copy(source, outputDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path requireTextureFile(String tileId, String textureId) throws IOException {
        TextureAsset texture = history.getDocument().findTexture(textureId);
        if (texture == null) {
            throw new IOException("Tile '" + tileId + "' references unknown texture '" + textureId + "'");
        }
        return texturesDirectory().resolve(texture.fileName);
    }

    private Path texturesDirectory() {
        return projectDirectory.resolve(TEXTURES_DIRECTORY_NAME);
    }

    private Path catalogsDirectory() {
        return projectDirectory.resolve(CATALOGS_DIRECTORY_NAME);
    }

    private Path mapsDirectory() {
        return projectDirectory.resolve(MAPS_DIRECTORY_NAME);
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
