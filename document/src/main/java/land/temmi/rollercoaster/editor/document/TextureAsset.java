package land.temmi.rollercoaster.editor.document;

/** An imported texture, copied into the project's sources/textures/ folder. */
public final class TextureAsset {
    public final String id;
    public final String fileName;

    public TextureAsset(String id, String fileName) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Texture id is required");
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Texture file name is required");
        }
        this.id = id;
        this.fileName = fileName;
    }
}
