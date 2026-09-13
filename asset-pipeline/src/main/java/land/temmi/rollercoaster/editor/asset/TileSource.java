package land.temmi.rollercoaster.editor.asset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * One tile's source images plus the metadata carried into the packed tileset. The side image is
 * optional - a tile without one falls back to its top image on exposed cliff and ramp sides,
 * matching TilesetManifest's own fallback.
 */
public final class TileSource {
    public final String id;
    public final BufferedImage topImage;
    public final BufferedImage sideImage;
    public final boolean walkable;

    public TileSource(String id, BufferedImage topImage, BufferedImage sideImage, boolean walkable) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Tile id is required");
        if (topImage == null) throw new IllegalArgumentException("Tile top image is required");
        this.id = id;
        this.topImage = topImage;
        this.sideImage = sideImage;
        this.walkable = walkable;
    }

    public TileSource(String id, BufferedImage topImage, boolean walkable) {
        this(id, topImage, null, walkable);
    }

    public static TileSource load(String id, Path topImageFile, Path sideImageFile, boolean walkable)
        throws IOException {
        BufferedImage top = readImage(topImageFile);
        BufferedImage side = sideImageFile == null ? null : readImage(sideImageFile);
        return new TileSource(id, top, side, walkable);
    }

    public static TileSource load(String id, Path topImageFile, boolean walkable) throws IOException {
        return load(id, topImageFile, null, walkable);
    }

    private static BufferedImage readImage(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Not a readable image: " + file);
        return image;
    }
}
