package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to render an exported map, replacing whatever scene is currently shown.
 * The tileset catalog travels alongside the map file path so the preview can load the exact atlas
 * and regions authored in the editor, rather than approximating tiles with generated colors.
 * {@code modelManifestFilePath} is optional for prop-free maps. When present, its relative model
 * sources are resolved from the manifest's directory, just as they will be in an exported package.
 * {@code dialogueManifestFilePath} is optional too - only needed to test-run START_DIALOGUE actions.
 *
 * <p>{@code dirtyCellXs}/{@code dirtyCellZs} are optional too, and only ever an optimization hint:
 * when set, and this request's {@code mapFilePath} names the map already being shown, the preview
 * may remesh just the terrain chunks those cells (and their immediate neighbours) could affect,
 * instead of the whole map - the same result a full rebuild would produce, just cheaper for a
 * small edit on a large map. Every other field is unaffected and still describes the map exactly
 * as a full rebuild would need it; a preview that ignores these two fields and always rebuilds
 * fully is still completely correct, just not as fast.
 */
public final class ShowMap {
    public String mapFilePath;
    public int width;
    public int depth;
    public String tilesetManifestFilePath;
    public String modelManifestFilePath;
    public String spriteManifestFilePath;
    public String dialogueManifestFilePath;
    public int[] dirtyCellXs;
    public int[] dirtyCellZs;

    public ShowMap() {
    }

    public ShowMap(String mapFilePath, int width, int depth, String tilesetManifestFilePath,
                   String modelManifestFilePath, String spriteManifestFilePath, String dialogueManifestFilePath) {
        this(mapFilePath, width, depth, tilesetManifestFilePath, modelManifestFilePath, spriteManifestFilePath,
            dialogueManifestFilePath, null, null);
    }

    public ShowMap(String mapFilePath, int width, int depth, String tilesetManifestFilePath,
                   String modelManifestFilePath, String spriteManifestFilePath, String dialogueManifestFilePath,
                   int[] dirtyCellXs, int[] dirtyCellZs) {
        this.mapFilePath = mapFilePath;
        this.width = width;
        this.depth = depth;
        this.tilesetManifestFilePath = tilesetManifestFilePath;
        this.modelManifestFilePath = modelManifestFilePath;
        this.spriteManifestFilePath = spriteManifestFilePath;
        this.dialogueManifestFilePath = dialogueManifestFilePath;
        this.dirtyCellXs = dirtyCellXs;
        this.dirtyCellZs = dirtyCellZs;
    }
}
