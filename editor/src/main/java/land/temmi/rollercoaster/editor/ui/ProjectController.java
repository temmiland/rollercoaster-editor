package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.DialogueManifestExport;
import land.temmi.rollercoaster.editor.asset.MapExport;
import land.temmi.rollercoaster.editor.asset.ModelManifestExport;
import land.temmi.rollercoaster.editor.asset.SpriteAtlasExport;
import land.temmi.rollercoaster.editor.asset.SpriteFrameSource;
import land.temmi.rollercoaster.editor.asset.SpriteManifestExport;
import land.temmi.rollercoaster.editor.asset.SpritePacker;
import land.temmi.rollercoaster.editor.asset.TilePacker;
import land.temmi.rollercoaster.editor.asset.TileSource;
import land.temmi.rollercoaster.editor.asset.TilesetExport;
import land.temmi.rollercoaster.editor.document.AddTileCommand;
import land.temmi.rollercoaster.editor.document.CommandHistory;
import land.temmi.rollercoaster.editor.document.ConditionAsset;
import land.temmi.rollercoaster.editor.document.CreateMapCommand;
import land.temmi.rollercoaster.editor.document.CreateTilesetCommand;
import land.temmi.rollercoaster.editor.document.DialogueAsset;
import land.temmi.rollercoaster.editor.document.DialogueNodeAsset;
import land.temmi.rollercoaster.editor.document.DialogueResponseAsset;
import land.temmi.rollercoaster.editor.document.EntityTypeAsset;
import land.temmi.rollercoaster.editor.document.EventActionAsset;
import land.temmi.rollercoaster.editor.document.EventTriggerAsset;
import land.temmi.rollercoaster.editor.document.GameEventAsset;
import land.temmi.rollercoaster.editor.document.ImportModelCommand;
import land.temmi.rollercoaster.editor.document.ImportTextureCommand;
import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapEntityAsset;
import land.temmi.rollercoaster.editor.document.MapLightAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.MapTransitionAsset;
import land.temmi.rollercoaster.editor.document.ModelAsset;
import land.temmi.rollercoaster.editor.document.PlaceEventCommand;
import land.temmi.rollercoaster.editor.document.RegisterDialogueCommand;
import land.temmi.rollercoaster.editor.document.RegisterEntityTypeCommand;
import land.temmi.rollercoaster.editor.document.RemoveDialogueCommand;
import land.temmi.rollercoaster.editor.document.RemoveEntityTypeCommand;
import land.temmi.rollercoaster.editor.document.RemoveEventCommand;
import land.temmi.rollercoaster.editor.document.UpdateDialogueCommand;
import land.temmi.rollercoaster.editor.document.UpdateEntityTypeCommand;
import land.temmi.rollercoaster.editor.document.UpdateEventCommand;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.PlaceLightCommand;
import land.temmi.rollercoaster.editor.document.PlacePropCommand;
import land.temmi.rollercoaster.editor.document.PlaceEntityCommand;
import land.temmi.rollercoaster.editor.document.PlaceTransitionCommand;
import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;
import land.temmi.rollercoaster.editor.document.ProjectValidation;
import land.temmi.rollercoaster.editor.document.RemoveMapCommand;
import land.temmi.rollercoaster.editor.document.RemoveModelCommand;
import land.temmi.rollercoaster.editor.document.RemoveLightCommand;
import land.temmi.rollercoaster.editor.document.RemovePropCommand;
import land.temmi.rollercoaster.editor.document.RemoveEntityCommand;
import land.temmi.rollercoaster.editor.document.RemoveTransitionCommand;
import land.temmi.rollercoaster.editor.document.RemoveTextureCommand;
import land.temmi.rollercoaster.editor.document.RemoveTilesetCommand;
import land.temmi.rollercoaster.editor.document.RemoveTileCommand;
import land.temmi.rollercoaster.editor.document.RenameProjectCommand;
import land.temmi.rollercoaster.editor.document.ResizeMapCommand;
import land.temmi.rollercoaster.editor.document.ImportSpriteCommand;
import land.temmi.rollercoaster.editor.document.RemoveSpriteCommand;
import land.temmi.rollercoaster.editor.document.SpriteAnimationAsset;
import land.temmi.rollercoaster.editor.document.SpriteAsset;
import land.temmi.rollercoaster.editor.document.SpriteDirection;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TilesetAsset;
import land.temmi.rollercoaster.editor.document.TransformPropCommand;
import land.temmi.rollercoaster.editor.document.UpdateModelCommand;
import land.temmi.rollercoaster.editor.document.UpdateSpriteCommand;
import land.temmi.rollercoaster.editor.document.UpdateEntityCommand;
import land.temmi.rollercoaster.editor.document.UpdateLightCommand;
import land.temmi.rollercoaster.editor.document.UpdateTransitionCommand;

import javax.swing.Timer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
    private static final String SPRITES_DIRECTORY_NAME = "sources/sprites";
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
    public Path exportTileset(String tilesetId) throws IOException {
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
        return catalogsDirectory().resolve(tilesetId + ".json");
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

    public List<SpriteAsset> getSprites() {
        requireOpen();
        return history.getDocument().getSprites();
    }

    /** Copies one directional sprite sheet into sources/sprites/ and registers its grid metadata. */
    public SpriteAsset importSprite(Path sourceImageFile, SpriteAsset asset) throws IOException {
        requireOpen();
        if (!sourceImageFile.getFileName().toString().equals(asset.fileName)) {
            throw new IllegalArgumentException("Sprite file name must match its selected source file");
        }
        validateSpriteSheet(sourceImageFile, asset);
        Files.createDirectories(spritesDirectory());
        Path destination = spritesDirectory().resolve(asset.fileName);
        if (Files.exists(destination)) {
            throw new IOException("A sprite file named '" + asset.fileName + "' is already in this project");
        }
        Files.copy(sourceImageFile, destination);
        history.perform(new ImportSpriteCommand(asset));
        listener.onProjectChanged();
        return asset;
    }

    public void removeSprite(SpriteAsset asset) {
        requireOpen();
        history.perform(new RemoveSpriteCommand(asset));
        listener.onProjectChanged();
    }

    public void updateSprite(SpriteAsset previous, SpriteAsset replacement) throws IOException {
        requireOpen();
        if (!previous.id.equals(replacement.id)) throw new IllegalArgumentException("Sprite ID cannot be changed");
        validateSpriteSheet(spritesDirectory().resolve(replacement.fileName), replacement);
        history.perform(new UpdateSpriteCommand(previous, replacement));
        listener.onProjectChanged();
    }

    /** Packs all registered sheet frames into one texture-atlas page and writes sprites.json. */
    public Path exportSprites() throws IOException {
        requireOpen();
        List<SpriteAsset> sprites = history.getDocument().getSprites();
        if (sprites.isEmpty()) throw new IOException("No sprites to export");
        List<SpriteFrameSource> frames = new ArrayList<>();
        List<SpriteManifestExport.Entry> entries = new ArrayList<>();
        for (SpriteAsset sprite : sprites) {
            BufferedImage sheet = loadSpriteSheet(sprite);
            int frameWidth = sheet.getWidth() / sprite.columns;
            int frameHeight = sheet.getHeight() / sprite.rows;
            int frameCount = sprite.columns * sprite.rows;
            for (int index = 0; index < frameCount; index++) {
                int x = index % sprite.columns * frameWidth;
                int y = index / sprite.columns * frameHeight;
                frames.add(new SpriteFrameSource(regionId(sprite, index), sheet.getSubimage(x, y, frameWidth, frameHeight)));
            }
            java.util.Map<String, SpriteManifestExport.Direction> directions = new java.util.LinkedHashMap<>();
            for (SpriteDirection direction : SpriteDirection.values()) {
                SpriteAnimationAsset animation = sprite.direction(direction);
                List<String> walkRegions = new ArrayList<>();
                for (int frame : animation.getWalkFrames()) walkRegions.add(regionId(sprite, frame));
                directions.put(direction.toId(), new SpriteManifestExport.Direction(regionId(sprite, animation.idleFrame), walkRegions));
            }
            entries.add(new SpriteManifestExport.Entry(sprite.id, sprite.worldHeight, sprite.frameDuration,
                sprite.footOffset, directions));
        }
        SpritePacker.PackedSpriteAtlas packed = SpritePacker.pack(frames);
        SpriteAtlasExport.write(packed, "sprites.atlas", "sprites.png", catalogsDirectory());
        return SpriteManifestExport.write(entries, "sprites.atlas", catalogsDirectory());
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
            entities.add(new MapExport.Entity(entity.instanceId, entity.type, entity.spriteId, entity.x, entity.z,
                entity.getProperties()));
        }
        List<MapExport.Light> lights = new ArrayList<>();
        for (MapLightAsset light : map.getLights()) {
            lights.add(new MapExport.Light(light.instanceId, light.x, light.y, light.z,
                light.colorR, light.colorG, light.colorB, light.intensity, light.range, light.enabled,
                light.spot, light.directionX, light.directionY, light.directionZ, light.innerAngle, light.outerAngle));
        }
        List<MapExport.Transition> transitions = new ArrayList<>();
        for (MapTransitionAsset transition : map.getTransitions()) {
            transitions.add(new MapExport.Transition(transition.instanceId, transition.x, transition.z,
                transition.targetMapId, transition.targetX, transition.targetZ));
        }
        List<MapExport.Event> events = new ArrayList<>();
        for (GameEventAsset event : map.getEvents()) {
            events.add(new MapExport.Event(event.instanceId, toExportTrigger(event.trigger),
                toExportConditions(event.getConditions()), toExportActions(event.getActions())));
        }
        MapExport.write(map.id, map.width, map.depth, map.tilesetId, tiles, heights, shapes, collision,
            props, entities, lights, transitions, events, mapsDirectory());
        return mapsDirectory().resolve(map.id + ".json");
    }

    private static MapExport.EventTrigger toExportTrigger(EventTriggerAsset trigger) {
        return new MapExport.EventTrigger(trigger.type.name().toLowerCase(Locale.ROOT), trigger.entityId,
            trigger.x, trigger.z, trigger.timeOfDay);
    }

    private static List<MapExport.Action> toExportActions(List<EventActionAsset> actions) {
        List<MapExport.Action> exported = new ArrayList<>();
        for (EventActionAsset action : actions) {
            exported.add(new MapExport.Action(action.type.name().toLowerCase(Locale.ROOT), action.targetId,
                action.value, action.x, action.z, action.targetMap));
        }
        return exported;
    }

    private static List<MapExport.Condition> toExportConditions(List<ConditionAsset> conditions) {
        List<MapExport.Condition> exported = new ArrayList<>();
        for (ConditionAsset condition : conditions) {
            exported.add(new MapExport.Condition(condition.type.name().toLowerCase(Locale.ROOT), condition.key,
                condition.comparison.name().toLowerCase(Locale.ROOT), condition.value));
        }
        return exported;
    }

    /** Writes dialogues.json for every registered dialogue tree. */
    public Path exportDialogues() throws IOException {
        requireOpen();
        List<DialogueAsset> dialogues = history.getDocument().getDialogues();
        if (dialogues.isEmpty()) throw new IOException("No dialogues to export");
        List<DialogueManifestExport.Entry> entries = new ArrayList<>();
        for (DialogueAsset dialogue : dialogues) {
            List<DialogueManifestExport.Node> nodes = new ArrayList<>();
            for (DialogueNodeAsset node : dialogue.getNodes()) {
                List<DialogueManifestExport.Response> responses = new ArrayList<>();
                for (DialogueResponseAsset response : node.getResponses()) {
                    responses.add(new DialogueManifestExport.Response(response.textId, response.targetNodeId,
                        toDialogueConditions(response.getConditions())));
                }
                nodes.add(new DialogueManifestExport.Node(node.id, node.speakerId, node.textId, node.portrait, responses));
            }
            entries.add(new DialogueManifestExport.Entry(dialogue.id, dialogue.startNodeId, nodes));
        }
        DialogueManifestExport.write(entries, catalogsDirectory());
        return catalogsDirectory().resolve(DialogueManifestExport.FILE_NAME);
    }

    /** Re-checks every cross-reference the interactive Place/Update commands already enforce one
     * at a time, in bulk (see {@link ProjectValidation}), plus every registered source file's
     * presence on disk - the two ways a project can go stale without the editor's own placement
     * commands ever seeing it happen: a hand-edited or corrupted project.json, or a source file
     * deleted outside the editor. Returns every problem found; empty means the project is ready
     * to export. */
    public List<String> validateProject() {
        requireOpen();
        List<String> problems = new ArrayList<>(ProjectValidation.findProblems(history.getDocument()));
        for (TextureAsset texture : getTextures()) {
            if (!Files.isRegularFile(texturesDirectory().resolve(texture.fileName))) {
                problems.add("Textur '" + texture.id + "': Datei fehlt (" + texture.fileName + ")");
            }
        }
        for (ModelAsset model : getModels()) {
            if (!Files.isRegularFile(modelsDirectory().resolve(model.fileName))) {
                problems.add("Modell '" + model.id + "': Datei fehlt (" + model.fileName + ")");
            }
            for (String dependencyFileName : model.getDependencyFileNames()) {
                if (!Files.isRegularFile(modelsDirectory().resolve(dependencyFileName))) {
                    problems.add("Modell '" + model.id + "': abhängige Datei fehlt (" + dependencyFileName + ")");
                }
            }
        }
        for (SpriteAsset sprite : getSprites()) {
            if (!Files.isRegularFile(spritesDirectory().resolve(sprite.fileName))) {
                problems.add("Sprite '" + sprite.id + "': Datei fehlt (" + sprite.fileName + ")");
            }
        }
        return problems;
    }

    private static List<DialogueManifestExport.Condition> toDialogueConditions(List<ConditionAsset> conditions) {
        List<DialogueManifestExport.Condition> exported = new ArrayList<>();
        for (ConditionAsset condition : conditions) {
            exported.add(new DialogueManifestExport.Condition(condition.type.name().toLowerCase(Locale.ROOT),
                condition.key, condition.comparison.name().toLowerCase(Locale.ROOT), condition.value));
        }
        return exported;
    }

    public List<DialogueAsset> getDialogues() {
        requireOpen();
        return history.getDocument().getDialogues();
    }

    public void registerDialogue(DialogueAsset dialogue) {
        requireOpen();
        history.perform(new RegisterDialogueCommand(dialogue));
        listener.onProjectChanged();
    }

    public void removeDialogue(DialogueAsset dialogue) {
        requireOpen();
        history.perform(new RemoveDialogueCommand(dialogue));
        listener.onProjectChanged();
    }

    public void updateDialogue(DialogueAsset previous, DialogueAsset replacement) {
        requireOpen();
        history.perform(new UpdateDialogueCommand(previous, replacement));
        listener.onProjectChanged();
    }

    public List<EntityTypeAsset> getEntityTypes() {
        requireOpen();
        return history.getDocument().getEntityTypes();
    }

    public void registerEntityType(EntityTypeAsset entityType) {
        requireOpen();
        history.perform(new RegisterEntityTypeCommand(entityType));
        listener.onProjectChanged();
    }

    public void removeEntityType(EntityTypeAsset entityType) {
        requireOpen();
        history.perform(new RemoveEntityTypeCommand(entityType));
        listener.onProjectChanged();
    }

    public void updateEntityType(EntityTypeAsset previous, EntityTypeAsset replacement) {
        requireOpen();
        history.perform(new UpdateEntityTypeCommand(previous, replacement));
        listener.onProjectChanged();
    }

    public void placeEvent(String mapId, GameEventAsset event) {
        requireOpen();
        history.perform(new PlaceEventCommand(mapId, event));
        listener.onProjectChanged();
    }

    public void removeEvent(String mapId, GameEventAsset event) {
        requireOpen();
        history.perform(new RemoveEventCommand(mapId, event));
        listener.onProjectChanged();
    }

    public void updateEvent(String mapId, GameEventAsset previous, GameEventAsset replacement) {
        requireOpen();
        history.perform(new UpdateEventCommand(mapId, previous, replacement));
        listener.onProjectChanged();
    }

    public void placeLight(String mapId, MapLightAsset light) {
        requireOpen();
        history.perform(new PlaceLightCommand(mapId, light));
        listener.onProjectChanged();
    }

    public void removeLight(String mapId, MapLightAsset light) {
        requireOpen();
        history.perform(new RemoveLightCommand(mapId, light));
        listener.onProjectChanged();
    }

    public void updateLight(String mapId, MapLightAsset previous, MapLightAsset replacement) {
        requireOpen();
        history.perform(new UpdateLightCommand(mapId, previous, replacement));
        listener.onProjectChanged();
    }

    public void placeTransition(String mapId, MapTransitionAsset transition) {
        requireOpen();
        history.perform(new PlaceTransitionCommand(mapId, transition));
        listener.onProjectChanged();
    }

    public void removeTransition(String mapId, MapTransitionAsset transition) {
        requireOpen();
        history.perform(new RemoveTransitionCommand(mapId, transition));
        listener.onProjectChanged();
    }

    public void updateTransition(String mapId, MapTransitionAsset previous, MapTransitionAsset replacement) {
        requireOpen();
        history.perform(new UpdateTransitionCommand(mapId, previous, replacement));
        listener.onProjectChanged();
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

    public void updateEntity(String mapId, MapEntityAsset previous, String type, String spriteId, int x, int z,
                             java.util.Map<String, String> properties) {
        requireOpen();
        history.perform(new UpdateEntityCommand(mapId, previous,
            new MapEntityAsset(previous.instanceId, type, spriteId, x, z, properties)));
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

    private BufferedImage loadSpriteSheet(SpriteAsset sprite) throws IOException {
        Path file = spritesDirectory().resolve(sprite.fileName);
        validateSpriteSheet(file, sprite);
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Not a readable sprite image: " + sprite.fileName);
        return image;
    }

    private static void validateSpriteSheet(Path file, SpriteAsset sprite) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Not a readable sprite image: " + file);
        if (image.getWidth() % sprite.columns != 0 || image.getHeight() % sprite.rows != 0) {
            throw new IOException("Sprite sheet '" + sprite.fileName + "' is " + image.getWidth() + "x" + image.getHeight()
                + "px and cannot be split into " + sprite.columns + "x" + sprite.rows + " frames");
        }
    }

    private static String regionId(SpriteAsset sprite, int frameIndex) {
        return sprite.id + "-frame-" + frameIndex;
    }

    private Path modelsDirectory() {
        return projectDirectory.resolve(MODELS_DIRECTORY_NAME);
    }

    private Path spritesDirectory() {
        return projectDirectory.resolve(SPRITES_DIRECTORY_NAME);
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
