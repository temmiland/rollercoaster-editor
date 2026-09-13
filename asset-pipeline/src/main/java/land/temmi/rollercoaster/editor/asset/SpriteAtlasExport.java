package land.temmi.rollercoaster.editor.asset;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a PNG plus libGDX TextureAtlas descriptor from a packed single-page sprite atlas. */
public final class SpriteAtlasExport {
    private SpriteAtlasExport() {
    }

    public static void write(SpritePacker.PackedSpriteAtlas packed, String atlasFileName, String textureFileName,
                             Path catalogDirectory) throws IOException {
        Files.createDirectories(catalogDirectory);
        ImageIO.write(packed.image, "PNG", catalogDirectory.resolve(textureFileName).toFile());
        Path target = catalogDirectory.resolve(atlasFileName);
        Path temp = catalogDirectory.resolve(atlasFileName + ".tmp");
        StringBuilder descriptor = new StringBuilder();
        descriptor.append(textureFileName).append('\n');
        descriptor.append("format: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n");
        descriptor.append("size: ").append(packed.image.getWidth()).append(',').append(packed.image.getHeight()).append('\n');
        for (java.util.Map.Entry<String, SpritePacker.Region> entry : packed.getRegions().entrySet()) {
            SpritePacker.Region region = entry.getValue();
            descriptor.append(entry.getKey()).append('\n');
            descriptor.append("  rotate: false\n");
            descriptor.append("  xy: ").append(region.x).append(',').append(region.y).append('\n');
            descriptor.append("  size: ").append(region.width).append(',').append(region.height).append('\n');
            descriptor.append("  orig: ").append(region.width).append(',').append(region.height).append('\n');
            descriptor.append("  offset: 0,0\n  index: -1\n");
        }
        Files.writeString(temp, descriptor.toString(), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
