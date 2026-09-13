package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.utils.JsonWriter;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a packed tileset as texture PNG + manifest JSON, matching TilesetManifest's schema exactly. */
public final class TilesetExport {
    public static final int FORMAT_VERSION = 1;

    private TilesetExport() {
    }

    public static void write(TilePacker.PackedTileset packed, String tilesetId, String textureFileName,
                             Path catalogDirectory) throws IOException {
        Files.createDirectories(catalogDirectory);
        ImageIO.write(packed.atlas, "PNG", catalogDirectory.resolve(textureFileName).toFile());

        Path manifestFile = catalogDirectory.resolve(tilesetId + ".json");
        Path temp = catalogDirectory.resolve(tilesetId + ".json.tmp");
        Files.writeString(temp, toJson(packed, tilesetId, textureFileName), StandardCharsets.UTF_8);
        Files.move(temp, manifestFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String toJson(TilePacker.PackedTileset packed, String tilesetId, String textureFileName)
        throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("version", FORMAT_VERSION);
        writer.set("id", tilesetId);
        writer.set("texture", textureFileName);
        writer.array("tileSize");
        writer.value(packed.tileWidth);
        writer.value(packed.tileHeight);
        writer.pop();
        writer.array("tiles");
        for (TilePacker.PackedTile tile : packed.tiles) {
            writer.object();
            writer.set("id", tile.id);
            writer.array("region");
            writer.value(tile.x);
            writer.value(tile.y);
            writer.value(tile.width);
            writer.value(tile.height);
            writer.pop();
            writer.set("walkable", tile.walkable);
            writer.pop();
        }
        writer.pop();
        writer.pop();
        return buffer.toString();
    }
}
