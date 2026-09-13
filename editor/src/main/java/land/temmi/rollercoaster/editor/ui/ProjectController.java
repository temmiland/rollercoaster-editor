package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.TilePacker;
import land.temmi.rollercoaster.editor.asset.TileSource;
import land.temmi.rollercoaster.editor.asset.TilesetExport;
import land.temmi.rollercoaster.editor.document.AddTileCommand;
import land.temmi.rollercoaster.editor.document.CommandHistory;
import land.temmi.rollercoaster.editor.document.CreateTilesetCommand;
import land.temmi.rollercoaster.editor.document.ImportTextureCommand;
import land.temmi.rollercoaster.editor.document.ProjectDocument;
import land.temmi.rollercoaster.editor.document.ProjectFile;
import land.temmi.rollercoaster.editor.document.RemoveTextureCommand;
import land.temmi.rollercoaster.editor.document.RemoveTilesetCommand;
import land.temmi.rollercoaster.editor.document.RemoveTileCommand;
import land.temmi.rollercoaster.editor.document.RenameProjectCommand;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TilesetAsset;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private static final String CATALOGS_DIRECTORY_NAME = "catalogs";

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

    public void addTile(String tilesetId, String tileId, String textureId, boolean walkable) {
        requireOpen();
        history.perform(new AddTileCommand(tilesetId, new TileEntry(tileId, textureId, walkable)));
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
            TextureAsset texture = history.getDocument().findTexture(tile.textureId);
            if (texture == null) {
                throw new IOException("Tile '" + tile.id + "' references unknown texture '" + tile.textureId + "'");
            }
            sources.add(TileSource.load(tile.id, texturesDirectory().resolve(texture.fileName), tile.walkable));
        }
        TilePacker.PackedTileset packed = TilePacker.pack(sources);
        TilesetExport.write(packed, tilesetId, tilesetId + ".png", catalogsDirectory());
    }

    private Path texturesDirectory() {
        return projectDirectory.resolve(TEXTURES_DIRECTORY_NAME);
    }

    private Path catalogsDirectory() {
        return projectDirectory.resolve(CATALOGS_DIRECTORY_NAME);
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
