package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes the directional-sprite schema consumed by the engine's {@code SpriteManifest}. */
public final class SpriteManifestExport {
    public static final String FILE_NAME = "sprites.json";
    private static final String[] DIRECTIONS = {"north", "east", "south", "west"};

    public static final class Direction {
        public final String idleRegion;
        public final List<String> walkRegions;

        public Direction(String idleRegion, List<String> walkRegions) {
            if (idleRegion == null || idleRegion.trim().isEmpty() || walkRegions == null || walkRegions.isEmpty()) {
                throw new IllegalArgumentException("Sprite idle and walk regions are required");
            }
            this.idleRegion = idleRegion;
            this.walkRegions = Collections.unmodifiableList(new ArrayList<>(walkRegions));
        }
    }

    public static final class Entry {
        public final String id;
        public final float height;
        public final float frameDuration;
        public final float footOffset;
        private final Map<String, Direction> directions;

        public Entry(String id, float height, float frameDuration, float footOffset, Map<String, Direction> directions) {
            if (id == null || id.trim().isEmpty() || height <= 0f || frameDuration <= 0f
                || footOffset < 0f || footOffset >= 1f) {
                throw new IllegalArgumentException("Invalid sprite manifest entry: " + id);
            }
            if (directions == null) throw new IllegalArgumentException("Sprite directions are required: " + id);
            Map<String, Direction> checked = new LinkedHashMap<>();
            for (String direction : DIRECTIONS) {
                Direction value = directions.get(direction);
                if (value == null) throw new IllegalArgumentException("Missing sprite direction: " + direction);
                checked.put(direction, value);
            }
            this.id = id;
            this.height = height;
            this.frameDuration = frameDuration;
            this.footOffset = footOffset;
            this.directions = Collections.unmodifiableMap(checked);
        }

        public Direction direction(String name) {
            return directions.get(name);
        }
    }

    private SpriteManifestExport() {
    }

    public static Path write(List<Entry> sprites, String atlasFileName, Path catalogDirectory) throws IOException {
        if (sprites == null || sprites.isEmpty()) throw new IllegalArgumentException("At least one sprite is required");
        if (atlasFileName == null || atlasFileName.trim().isEmpty()) throw new IllegalArgumentException("Sprite atlas is required");
        Files.createDirectories(catalogDirectory);
        Path target = catalogDirectory.resolve(FILE_NAME);
        Path temp = catalogDirectory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, toJson(sprites, atlasFileName), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static String toJson(List<Entry> sprites, String atlasFileName) throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("version", 1);
        writer.set("atlas", atlasFileName);
        writer.array("sprites");
        for (Entry sprite : sprites) {
            writer.object();
            writer.set("id", sprite.id);
            writer.set("height", sprite.height);
            writer.set("frameDuration", sprite.frameDuration);
            writer.set("footOffset", sprite.footOffset);
            writer.object("directions");
            for (String directionName : DIRECTIONS) {
                Direction direction = sprite.direction(directionName);
                writer.object(directionName);
                writer.set("idle", direction.idleRegion);
                writer.array("walk");
                for (String region : direction.walkRegions) writer.value(region);
                writer.pop();
                writer.pop();
            }
            writer.pop();
            writer.pop();
        }
        writer.pop();
        writer.pop();
        return buffer.toString();
    }
}
