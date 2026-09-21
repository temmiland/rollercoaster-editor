package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to render an exported map, replacing whatever scene is currently shown.
 * The tileset catalog travels alongside the map file path so the preview can load the exact atlas
 * and regions authored in the editor, rather than approximating tiles with generated colors.
 * {@code modelManifestFilePath} is optional for prop-free maps. When present, its relative model
 * sources are resolved from the manifest's directory, just as they will be in an exported package.
 * {@code dialogueManifestFilePath} is optional too - only needed to test-run START_DIALOGUE actions.
 */
public final class ShowMap {
    public String mapFilePath;
    public int width;
    public int depth;
    public String tilesetManifestFilePath;
    public String modelManifestFilePath;
    public String spriteManifestFilePath;
    public String dialogueManifestFilePath;

    public ShowMap() {
    }

    public ShowMap(String mapFilePath, int width, int depth, String tilesetManifestFilePath,
                   String modelManifestFilePath, String spriteManifestFilePath, String dialogueManifestFilePath) {
        this.mapFilePath = mapFilePath;
        this.width = width;
        this.depth = depth;
        this.tilesetManifestFilePath = tilesetManifestFilePath;
        this.modelManifestFilePath = modelManifestFilePath;
        this.spriteManifestFilePath = spriteManifestFilePath;
        this.dialogueManifestFilePath = dialogueManifestFilePath;
    }
}
