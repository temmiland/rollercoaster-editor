package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Writes models.json matching ModelManifest's schema exactly - one manifest for every registered model. */
public final class ModelManifestExport {
    public static final int FORMAT_VERSION = 1;
    public static final String FILE_NAME = "models.json";

    private ModelManifestExport() {
    }

    public static final class Entry {
        public final String id;
        public final String source;
        public final float offsetX;
        public final float offsetY;
        public final float offsetZ;
        public final float scale;
        public final float height;
        public final float boundsMinX;
        public final float boundsMinY;
        public final float boundsMinZ;
        public final float boundsMaxX;
        public final float boundsMaxY;
        public final float boundsMaxZ;
        public final int collisionMinX;
        public final int collisionMaxX;
        public final int collisionMinZ;
        public final int collisionMaxZ;
        public final boolean alignToSlope;
        public final boolean walkable;
        public final float walkHeight;

        public Entry(String id, String source, float offsetX, float offsetY, float offsetZ, float scale,
                    float height, float boundsMinX, float boundsMinY, float boundsMinZ,
                    float boundsMaxX, float boundsMaxY, float boundsMaxZ,
                    int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                    boolean alignToSlope, boolean walkable, float walkHeight) {
            this.id = id;
            this.source = source;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.scale = scale;
            this.height = height;
            this.boundsMinX = boundsMinX;
            this.boundsMinY = boundsMinY;
            this.boundsMinZ = boundsMinZ;
            this.boundsMaxX = boundsMaxX;
            this.boundsMaxY = boundsMaxY;
            this.boundsMaxZ = boundsMaxZ;
            this.collisionMinX = collisionMinX;
            this.collisionMaxX = collisionMaxX;
            this.collisionMinZ = collisionMinZ;
            this.collisionMaxZ = collisionMaxZ;
            this.alignToSlope = alignToSlope;
            this.walkable = walkable;
            this.walkHeight = walkHeight;
        }
    }

    public static void write(List<Entry> entries, Path catalogDirectory) throws IOException {
        Files.createDirectories(catalogDirectory);
        Path target = catalogDirectory.resolve(FILE_NAME);
        Path temp = catalogDirectory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, toJson(entries), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String toJson(List<Entry> entries) throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("version", FORMAT_VERSION);
        writer.array("models");
        for (Entry entry : entries) {
            writer.object();
            writer.set("id", entry.id);
            writer.set("source", entry.source);
            writeFloatArray(writer, "anchor", entry.offsetX, entry.offsetY, entry.offsetZ);
            writer.set("scale", entry.scale);
            writer.set("height", entry.height);
            writer.object("bounds");
            writeFloatArray(writer, "min", entry.boundsMinX, entry.boundsMinY, entry.boundsMinZ);
            writeFloatArray(writer, "max", entry.boundsMaxX, entry.boundsMaxY, entry.boundsMaxZ);
            writer.pop();
            writer.object("collision");
            writer.array("min");
            writer.value(entry.collisionMinX);
            writer.value(entry.collisionMinZ);
            writer.pop();
            writer.array("max");
            writer.value(entry.collisionMaxX);
            writer.value(entry.collisionMaxZ);
            writer.pop();
            writer.pop();
            writer.set("alignToSlope", entry.alignToSlope);
            writer.set("walkable", entry.walkable);
            writer.set("walkHeight", entry.walkHeight);
            writer.pop();
        }
        writer.pop();
        writer.pop();
        return buffer.toString();
    }

    private static void writeFloatArray(JsonWriter writer, String name, float x, float y, float z)
        throws IOException {
        writer.array(name);
        writer.value(x);
        writer.value(y);
        writer.value(z);
        writer.pop();
    }
}
