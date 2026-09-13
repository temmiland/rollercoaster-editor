package land.temmi.rollercoaster.editor.asset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** One tile's source image plus the metadata carried into the packed tileset. */
public final class TileSource {
    public final String id;
    public final BufferedImage image;
    public final boolean walkable;

    public TileSource(String id, BufferedImage image, boolean walkable) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Tile id is required");
        if (image == null) throw new IllegalArgumentException("Tile image is required");
        this.id = id;
        this.image = image;
        this.walkable = walkable;
    }

    public static TileSource load(String id, Path imageFile, boolean walkable) throws IOException {
        BufferedImage image = ImageIO.read(imageFile.toFile());
        if (image == null) throw new IOException("Not a readable image: " + imageFile);
        return new TileSource(id, image, walkable);
    }
}
