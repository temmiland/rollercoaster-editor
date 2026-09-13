package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Writes a map matching MapLoader's schema exactly - one file per map, named "<id>.json". */
public final class MapExport {
    public static final int FORMAT_VERSION = 1;

    private MapExport() {
    }

    /** A placed model. The runtime format has no id for a prop - it's purely positional. */
    public static final class Prop {
        public final String model;
        public final float x;
        public final float z;
        public final float elevation;
        public final float rotation;

        public Prop(String model, float x, float z, float elevation, float rotation) {
            this.model = model;
            this.x = x;
            this.z = z;
            this.elevation = elevation;
            this.rotation = rotation;
        }
    }

    /**
     * @param tiles per-cell tile type id, row-major (z outer, x inner); every cell must be filled
     * @param heights per-cell surface height at the tile centre, same layout as {@code tiles}
     * @param shapes per-cell shape id (e.g. "flat", "ramp_north"), same layout as {@code tiles}
     * @param collision per-cell manual block flag, same layout as {@code tiles}
     */
    public static void write(String id, int width, int depth, String tilesetId,
                             String[] tiles, float[] heights, String[] shapes, boolean[] collision,
                             List<Prop> props, Path mapsDirectory) throws IOException {
        int cells = width * depth;
        if (tiles.length != cells || heights.length != cells || shapes.length != cells || collision.length != cells) {
            throw new IllegalArgumentException("Map layer arrays must have " + cells + " cells: " + id);
        }
        for (int i = 0; i < cells; i++) {
            if (tiles[i] == null) {
                throw new IllegalArgumentException("Map '" + id + "' has an unpainted cell at index " + i);
            }
        }

        Files.createDirectories(mapsDirectory);
        Path target = mapsDirectory.resolve(id + ".json");
        Path temp = mapsDirectory.resolve(id + ".json.tmp");
        Files.writeString(temp, toJson(id, width, depth, tilesetId, tiles, heights, shapes, collision, props),
            StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String toJson(String id, int width, int depth, String tilesetId,
                                 String[] tiles, float[] heights, String[] shapes, boolean[] collision,
                                 List<Prop> props)
        throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("version", FORMAT_VERSION);
        writer.set("name", id);
        writer.array("size");
        writer.value(width);
        writer.value(depth);
        writer.pop();
        writer.set("tileset", tilesetId);
        writer.object("layers");
        writeGrid(writer, "tile", width, depth, (x, z) -> writer.value(tiles[z * width + x]));
        writeGrid(writer, "height", width, depth, (x, z) -> writer.value(heights[z * width + x]));
        writeGrid(writer, "shape", width, depth, (x, z) -> writer.value(shapes[z * width + x]));
        writeGrid(writer, "collision", width, depth, (x, z) -> writer.value(collision[z * width + x] ? 1 : 0));
        writer.pop();
        writer.array("props");
        for (Prop prop : props) {
            writer.object();
            writer.set("model", prop.model);
            writer.set("x", prop.x);
            writer.set("y", prop.z);
            writer.set("elevation", prop.elevation);
            writer.set("rot", prop.rotation);
            writer.pop();
        }
        writer.pop();
        writer.array("entities");
        writer.pop();
        writer.pop();
        return buffer.toString();
    }

    private interface CellWriter {
        void write(int x, int z) throws IOException;
    }

    private static void writeGrid(JsonWriter writer, String name, int width, int depth, CellWriter cell)
        throws IOException {
        writer.array(name);
        for (int z = 0; z < depth; z++) {
            writer.array();
            for (int x = 0; x < width; x++) cell.write(x, z);
            writer.pop();
        }
        writer.pop();
    }
}
