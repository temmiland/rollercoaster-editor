package land.temmi.rollercoaster.editor.document;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Reads and writes project.json. Saves are atomic: write a sibling temp file, then rename over the target. */
public final class ProjectFile {
    public static final String FILE_NAME = "project.json";

    private ProjectFile() {
    }

    public static Path fileIn(Path projectDirectory) {
        return projectDirectory.resolve(FILE_NAME);
    }

    public static ProjectDocument load(Path projectDirectory) throws IOException {
        String content = Files.readString(fileIn(projectDirectory), StandardCharsets.UTF_8);
        JsonValue root = new JsonReader().parse(content);
        int version = root.getInt("formatVersion", -1);
        if (version != ProjectDocument.FORMAT_VERSION) {
            throw new IOException("Unsupported project format version: " + version);
        }
        JsonValue nameValue = root.get("name");
        if (nameValue == null || !nameValue.isString() || nameValue.asString().trim().isEmpty()) {
            throw new IOException("project.json is missing a nonempty 'name'");
        }
        ProjectDocument document = new ProjectDocument(nameValue.asString());

        JsonValue textures = root.get("textures");
        if (textures != null) {
            for (JsonValue texture = textures.child; texture != null; texture = texture.next) {
                document.addTexture(new TextureAsset(requireString(texture, "id"), requireString(texture, "file")));
            }
        }

        JsonValue tilesets = root.get("tilesets");
        if (tilesets != null) {
            for (JsonValue tileset = tilesets.child; tileset != null; tileset = tileset.next) {
                TilesetAsset asset = new TilesetAsset(requireString(tileset, "id"));
                JsonValue tiles = tileset.get("tiles");
                if (tiles != null) {
                    for (JsonValue tile = tiles.child; tile != null; tile = tile.next) {
                        asset.addTile(new TileEntry(requireString(tile, "id"), requireString(tile, "texture"),
                            tile.getString("sideTexture", null), tile.getBoolean("walkable", true)));
                    }
                }
                document.addTileset(asset);
            }
        }

        JsonValue models = root.get("models");
        if (models != null) {
            for (JsonValue model = models.child; model != null; model = model.next) {
                JsonValue offset = requiredArray(model, "offset", 3);
                JsonValue bounds = required(model, "bounds");
                JsonValue boundsMin = requiredArray(bounds, "min", 3);
                JsonValue boundsMax = requiredArray(bounds, "max", 3);
                JsonValue collision = required(model, "collision");
                JsonValue collisionMin = requiredArray(collision, "min", 2);
                JsonValue collisionMax = requiredArray(collision, "max", 2);
                List<String> dependencies = new ArrayList<>();
                JsonValue dependencyValues = model.get("dependencies");
                if (dependencyValues != null) {
                    if (!dependencyValues.isArray()) throw new IOException("project.json model dependencies must be an array");
                    for (JsonValue dependency = dependencyValues.child; dependency != null; dependency = dependency.next) {
                        if (!dependency.isString() || dependency.asString().trim().isEmpty()) {
                            throw new IOException("project.json model dependency must be a nonempty string");
                        }
                        dependencies.add(dependency.asString());
                    }
                }
                document.addModel(new ModelAsset(requireString(model, "id"), requireString(model, "file"),
                    model.getBoolean("binary", false),
                    offset.getFloat(0), offset.getFloat(1), offset.getFloat(2),
                    model.getFloat("scale", 1f),
                    boundsMin.getFloat(0), boundsMin.getFloat(1), boundsMin.getFloat(2),
                    boundsMax.getFloat(0), boundsMax.getFloat(1), boundsMax.getFloat(2),
                    collisionMin.getInt(0), collisionMax.getInt(0), collisionMin.getInt(1), collisionMax.getInt(1),
                    model.getBoolean("alignToSlope", false), model.getBoolean("walkable", false),
                    model.getFloat("walkHeight", 0f), dependencies));
            }
        }

        JsonValue sprites = root.get("sprites");
        if (sprites != null) {
            for (JsonValue sprite = sprites.child; sprite != null; sprite = sprite.next) {
                int columns = sprite.getInt("columns");
                int rows = sprite.getInt("rows");
                JsonValue directionValues = required(sprite, "directions");
                if (!directionValues.isObject()) throw new IOException("project.json sprite directions must be an object");
                EnumMap<SpriteDirection, SpriteAnimationAsset> directions = new EnumMap<>(SpriteDirection.class);
                for (SpriteDirection direction : SpriteDirection.values()) {
                    JsonValue animation = required(directionValues, direction.toId());
                    if (!animation.isObject()) {
                        throw new IOException("project.json sprite direction must be an object: " + direction.toId());
                    }
                    JsonValue walkValues = required(animation, "walk");
                    if (!walkValues.isArray()) {
                        throw new IOException("project.json sprite walk frames must be an array: " + direction.toId());
                    }
                    List<Integer> walkFrames = new ArrayList<>();
                    for (JsonValue walkFrame = walkValues.child; walkFrame != null; walkFrame = walkFrame.next) {
                        walkFrames.add(walkFrame.asInt());
                    }
                    directions.put(direction, new SpriteAnimationAsset(required(animation, "idle").asInt(), walkFrames));
                }
                document.addSprite(new SpriteAsset(requireString(sprite, "id"), requireString(sprite, "file"),
                    columns, rows, sprite.getFloat("height"), sprite.getFloat("frameDuration"),
                    sprite.getFloat("footOffset", 0f), directions));
            }
        }

        JsonValue maps = root.get("maps");
        if (maps != null) {
            for (JsonValue mapValue = maps.child; mapValue != null; mapValue = mapValue.next) {
                int width = mapValue.getInt("width");
                int depth = mapValue.getInt("depth");
                MapAsset map = new MapAsset(requireString(mapValue, "id"), width, depth,
                    requireString(mapValue, "tileset"));
                JsonValue tileRows = required(mapValue, "tiles");
                JsonValue heightRows = mapValue.get("heights");
                JsonValue shapeRows = mapValue.get("shapes");
                JsonValue collisionRows = mapValue.get("collision");
                JsonValue tileRow = tileRows.child;
                JsonValue heightRow = heightRows == null ? null : heightRows.child;
                JsonValue shapeRow = shapeRows == null ? null : shapeRows.child;
                JsonValue collisionRow = collisionRows == null ? null : collisionRows.child;
                for (int z = 0; z < depth; z++) {
                    JsonValue tileCell = tileRow.child;
                    JsonValue heightCell = heightRow == null ? null : heightRow.child;
                    JsonValue shapeCell = shapeRow == null ? null : shapeRow.child;
                    JsonValue collisionCell = collisionRow == null ? null : collisionRow.child;
                    for (int x = 0; x < width; x++) {
                        String tileId = tileCell.isNull() ? null : tileCell.asString();
                        if (tileId != null) map.setTile(x, z, tileId);
                        float height = heightCell == null ? 0f : heightCell.asFloat();
                        TileShape shape = shapeCell == null ? TileShape.FLAT : TileShape.fromId(shapeCell.asString());
                        if (height != 0f || shape != TileShape.FLAT) map.setTerrain(x, z, height, shape);
                        if (collisionCell != null && collisionCell.asInt() != 0) map.setBlocked(x, z, true);
                        tileCell = tileCell.next;
                        if (heightCell != null) heightCell = heightCell.next;
                        if (shapeCell != null) shapeCell = shapeCell.next;
                        if (collisionCell != null) collisionCell = collisionCell.next;
                    }
                    tileRow = tileRow.next;
                    if (heightRow != null) heightRow = heightRow.next;
                    if (shapeRow != null) shapeRow = shapeRow.next;
                    if (collisionRow != null) collisionRow = collisionRow.next;
                }
                JsonValue props = mapValue.get("props");
                if (props != null) {
                    for (JsonValue prop = props.child; prop != null; prop = prop.next) {
                        map.addProp(new MapProp(requireString(prop, "instanceId"), requireString(prop, "model"),
                            prop.getFloat("x", 0f), prop.getFloat("z", 0f),
                            prop.getFloat("elevation", 0f), prop.getFloat("rotation", 0f)));
                    }
                }
                JsonValue entities = mapValue.get("entities");
                if (entities != null) {
                    for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
                        MapEntityAsset mapEntity = new MapEntityAsset(requireString(entity, "instanceId"),
                            requireString(entity, "type"), entity.getString("sprite", null),
                            entity.getInt("x"), entity.getInt("z"));
                        map.requireEntityPosition(mapEntity);
                        map.addEntity(mapEntity);
                    }
                }
                document.addMap(map);
            }
        }
        return document;
    }

    private static JsonValue required(JsonValue parent, String field) throws IOException {
        JsonValue value = parent.get(field);
        if (value == null) throw new IOException("project.json entry is missing '" + field + "'");
        return value;
    }

    private static JsonValue requiredArray(JsonValue parent, String field, int size) throws IOException {
        JsonValue value = required(parent, field);
        if (!value.isArray() || value.size < size) {
            throw new IOException("project.json field '" + field + "' must contain " + size + " values");
        }
        return value;
    }

    private static String requireString(JsonValue parent, String field) throws IOException {
        JsonValue value = parent.get(field);
        if (value == null || !value.isString() || value.asString().trim().isEmpty()) {
            throw new IOException("project.json entry is missing a nonempty '" + field + "'");
        }
        return value.asString();
    }

    public static void save(ProjectDocument document, Path projectDirectory) throws IOException {
        Files.createDirectories(projectDirectory);
        Path target = fileIn(projectDirectory);
        Path temp = projectDirectory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, toJson(document), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String toJson(ProjectDocument document) throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("formatVersion", ProjectDocument.FORMAT_VERSION);
        writer.set("name", document.getName());

        writer.array("textures");
        for (TextureAsset texture : document.getTextures()) {
            writer.object();
            writer.set("id", texture.id);
            writer.set("file", texture.fileName);
            writer.pop();
        }
        writer.pop();

        writer.array("tilesets");
        for (TilesetAsset tileset : document.getTilesets()) {
            writer.object();
            writer.set("id", tileset.id);
            writer.array("tiles");
            for (TileEntry tile : tileset.getTiles()) {
                writer.object();
                writer.set("id", tile.id);
                writer.set("texture", tile.textureId);
                if (tile.sideTextureId != null) writer.set("sideTexture", tile.sideTextureId);
                writer.set("walkable", tile.walkable);
                writer.pop();
            }
            writer.pop();
            writer.pop();
        }
        writer.pop();

        writer.array("models");
        for (ModelAsset model : document.getModels()) {
            writer.object();
            writer.set("id", model.id);
            writer.set("file", model.fileName);
            writer.array("dependencies");
            for (String dependencyFileName : model.getDependencyFileNames()) writer.value(dependencyFileName);
            writer.pop();
            writer.set("binary", model.binary);
            writeFloatArray(writer, "offset", model.offsetX, model.offsetY, model.offsetZ);
            writer.set("scale", model.scale);
            writer.object("bounds");
            writeFloatArray(writer, "min", model.boundsMinX, model.boundsMinY, model.boundsMinZ);
            writeFloatArray(writer, "max", model.boundsMaxX, model.boundsMaxY, model.boundsMaxZ);
            writer.pop();
            writer.object("collision");
            writer.array("min");
            writer.value(model.collisionMinX);
            writer.value(model.collisionMinZ);
            writer.pop();
            writer.array("max");
            writer.value(model.collisionMaxX);
            writer.value(model.collisionMaxZ);
            writer.pop();
            writer.pop();
            writer.set("alignToSlope", model.alignToSlope);
            writer.set("walkable", model.walkable);
            writer.set("walkHeight", model.walkHeight);
            writer.pop();
        }
        writer.pop();

        writer.array("sprites");
        for (SpriteAsset sprite : document.getSprites()) {
            writer.object();
            writer.set("id", sprite.id);
            writer.set("file", sprite.fileName);
            writer.set("columns", sprite.columns);
            writer.set("rows", sprite.rows);
            writer.set("height", sprite.worldHeight);
            writer.set("frameDuration", sprite.frameDuration);
            writer.set("footOffset", sprite.footOffset);
            writer.object("directions");
            for (SpriteDirection direction : SpriteDirection.values()) {
                SpriteAnimationAsset animation = sprite.direction(direction);
                writer.object(direction.toId());
                writer.set("idle", animation.idleFrame);
                writer.array("walk");
                for (int frame : animation.getWalkFrames()) writer.value(frame);
                writer.pop();
                writer.pop();
            }
            writer.pop();
            writer.pop();
        }
        writer.pop();

        writer.array("maps");
        for (MapAsset map : document.getMaps()) {
            writer.object();
            writer.set("id", map.id);
            writer.set("width", map.width);
            writer.set("depth", map.depth);
            writer.set("tileset", map.tilesetId);
            writer.array("tiles");
            for (int z = 0; z < map.depth; z++) {
                writer.array();
                for (int x = 0; x < map.width; x++) writer.value(map.getTile(x, z));
                writer.pop();
            }
            writer.pop();
            writer.array("heights");
            for (int z = 0; z < map.depth; z++) {
                writer.array();
                for (int x = 0; x < map.width; x++) writer.value(map.getHeight(x, z));
                writer.pop();
            }
            writer.pop();
            writer.array("shapes");
            for (int z = 0; z < map.depth; z++) {
                writer.array();
                for (int x = 0; x < map.width; x++) writer.value(map.getShape(x, z).toId());
                writer.pop();
            }
            writer.pop();
            writer.array("collision");
            for (int z = 0; z < map.depth; z++) {
                writer.array();
                for (int x = 0; x < map.width; x++) writer.value(map.isBlocked(x, z) ? 1 : 0);
                writer.pop();
            }
            writer.pop();
            writer.array("props");
            for (MapProp prop : map.getProps()) {
                writer.object();
                writer.set("instanceId", prop.instanceId);
                writer.set("model", prop.modelId);
                writer.set("x", prop.x);
                writer.set("z", prop.z);
                writer.set("elevation", prop.elevation);
                writer.set("rotation", prop.rotation);
                writer.pop();
            }
            writer.pop();
            writer.array("entities");
            for (MapEntityAsset entity : map.getEntities()) {
                writer.object();
                writer.set("instanceId", entity.instanceId);
                writer.set("type", entity.type);
                if (entity.spriteId != null) writer.set("sprite", entity.spriteId);
                writer.set("x", entity.x);
                writer.set("z", entity.z);
                writer.pop();
            }
            writer.pop();
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
