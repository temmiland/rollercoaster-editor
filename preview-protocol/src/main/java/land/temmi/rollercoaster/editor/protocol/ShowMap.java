package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to render an exported map, replacing whatever scene is currently shown.
 * The tile catalog travels alongside the map file path since the preview has no other way to
 * resolve walkability for tiles it renders with hash-derived colors instead of a real atlas.
 * {@code modelManifestFilePath} is optional for prop-free maps. When present, its relative model
 * sources are resolved from the manifest's directory, just as they will be in an exported package.
 */
public final class ShowMap {
    public String mapFilePath;
    public int width;
    public int depth;
    public String[] tileIds;
    public boolean[] tileWalkable;
    public String modelManifestFilePath;

    public ShowMap() {
    }

    public ShowMap(String mapFilePath, int width, int depth, String[] tileIds, boolean[] tileWalkable,
                   String modelManifestFilePath) {
        this.mapFilePath = mapFilePath;
        this.width = width;
        this.depth = depth;
        this.tileIds = tileIds;
        this.tileWalkable = tileWalkable;
        this.modelManifestFilePath = modelManifestFilePath;
    }
}
