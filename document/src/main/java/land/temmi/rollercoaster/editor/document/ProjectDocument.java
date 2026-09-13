package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Editable project state: a name plus the imported textures and tilesets built from them. */
public final class ProjectDocument {
    public static final int FORMAT_VERSION = 1;

    private String name;
    private final List<TextureAsset> textures = new ArrayList<>();
    private final List<TilesetAsset> tilesets = new ArrayList<>();
    private final List<ModelAsset> models = new ArrayList<>();
    private final List<SpriteAsset> sprites = new ArrayList<>();
    private final List<MapAsset> maps = new ArrayList<>();

    public ProjectDocument(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public List<TextureAsset> getTextures() {
        return Collections.unmodifiableList(textures);
    }

    public List<TilesetAsset> getTilesets() {
        return Collections.unmodifiableList(tilesets);
    }

    public TextureAsset findTexture(String id) {
        for (TextureAsset texture : textures) {
            if (texture.id.equals(id)) return texture;
        }
        return null;
    }

    public TilesetAsset findTileset(String id) {
        for (TilesetAsset tileset : tilesets) {
            if (tileset.id.equals(id)) return tileset;
        }
        return null;
    }

    public List<ModelAsset> getModels() {
        return Collections.unmodifiableList(models);
    }

    public ModelAsset findModel(String id) {
        for (ModelAsset model : models) {
            if (model.id.equals(id)) return model;
        }
        return null;
    }

    /** Package-private: mutation goes through a {@link Command} so undo/redo stays consistent. */
    void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Project name is required");
        this.name = name;
    }

    void addTexture(TextureAsset asset) {
        if (findTexture(asset.id) != null) throw new IllegalArgumentException("Duplicate texture id: " + asset.id);
        textures.add(asset);
    }

    void removeTexture(String id) {
        for (TilesetAsset tileset : tilesets) {
            for (TileEntry tile : tileset.getTiles()) {
                if (tile.textureId.equals(id) || id.equals(tile.sideTextureId)) {
                    throw new IllegalArgumentException(
                        "Texture '" + id + "' is still used by tile '" + tile.id + "' in tileset '" + tileset.id + "'");
                }
            }
        }
        if (!textures.removeIf(texture -> texture.id.equals(id))) {
            throw new IllegalArgumentException("No such texture: " + id);
        }
    }

    void addTileset(TilesetAsset tileset) {
        if (findTileset(tileset.id) != null) throw new IllegalArgumentException("Duplicate tileset id: " + tileset.id);
        tilesets.add(tileset);
    }

    void removeTileset(String id) {
        for (MapAsset map : maps) {
            if (map.tilesetId.equals(id)) {
                throw new IllegalArgumentException("Tileset '" + id + "' is still used by map '" + map.id + "'");
            }
        }
        if (!tilesets.removeIf(tileset -> tileset.id.equals(id))) {
            throw new IllegalArgumentException("No such tileset: " + id);
        }
    }

    TilesetAsset requireTileset(String id) {
        TilesetAsset tileset = findTileset(id);
        if (tileset == null) throw new IllegalArgumentException("No such tileset: " + id);
        return tileset;
    }

    void addModel(ModelAsset model) {
        if (findModel(model.id) != null) throw new IllegalArgumentException("Duplicate model id: " + model.id);
        models.add(model);
    }

    void removeModel(String id) {
        List<String> placements = new ArrayList<>();
        for (MapAsset map : maps) {
            for (MapProp prop : map.getProps()) {
                if (prop.modelId.equals(id)) {
                    placements.add("'" + prop.instanceId + "' on map '" + map.id + "'");
                }
            }
        }
        if (!placements.isEmpty()) {
            throw new IllegalArgumentException("Model '" + id + "' is still placed as " + String.join(", ", placements));
        }
        if (!models.removeIf(model -> model.id.equals(id))) {
            throw new IllegalArgumentException("No such model: " + id);
        }
    }

    void replaceModel(String id, ModelAsset replacement) {
        if (replacement == null || !id.equals(replacement.id)) {
            throw new IllegalArgumentException("Replacement model must keep ID '" + id + "'");
        }
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id.equals(id)) {
                models.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such model: " + id);
    }

    public List<SpriteAsset> getSprites() {
        return Collections.unmodifiableList(sprites);
    }

    public SpriteAsset findSprite(String id) {
        for (SpriteAsset sprite : sprites) {
            if (sprite.id.equals(id)) return sprite;
        }
        return null;
    }

    void addSprite(SpriteAsset sprite) {
        if (findSprite(sprite.id) != null) throw new IllegalArgumentException("Duplicate sprite id: " + sprite.id);
        sprites.add(sprite);
    }

    void removeSprite(String id) {
        List<String> placements = new ArrayList<>();
        for (MapAsset map : maps) {
            for (MapEntityAsset entity : map.getEntities()) {
                if (id.equals(entity.spriteId)) placements.add("'" + entity.instanceId + "' on map '" + map.id + "'");
            }
        }
        if (!placements.isEmpty()) {
            throw new IllegalArgumentException("Sprite '" + id + "' is still used by " + String.join(", ", placements));
        }
        if (!sprites.removeIf(sprite -> sprite.id.equals(id))) {
            throw new IllegalArgumentException("No such sprite: " + id);
        }
    }

    void replaceSprite(String id, SpriteAsset replacement) {
        if (replacement == null || !id.equals(replacement.id)) {
            throw new IllegalArgumentException("Replacement sprite must keep ID '" + id + "'");
        }
        for (int i = 0; i < sprites.size(); i++) {
            if (sprites.get(i).id.equals(id)) {
                sprites.set(i, replacement);
                return;
            }
        }
        throw new IllegalArgumentException("No such sprite: " + id);
    }

    public List<MapAsset> getMaps() {
        return Collections.unmodifiableList(maps);
    }

    public MapAsset findMap(String id) {
        for (MapAsset map : maps) {
            if (map.id.equals(id)) return map;
        }
        return null;
    }

    void addMap(MapAsset map) {
        if (findMap(map.id) != null) throw new IllegalArgumentException("Duplicate map id: " + map.id);
        for (MapEntityAsset entity : map.getEntities()) {
            if (entity.spriteId != null && findSprite(entity.spriteId) == null) {
                throw new IllegalArgumentException("Entity '" + entity.instanceId
                    + "' references unknown sprite '" + entity.spriteId + "'");
            }
        }
        maps.add(map);
    }

    void removeMap(String id) {
        for (MapAsset map : maps) {
            for (MapTransitionAsset transition : map.getTransitions()) {
                if (transition.targetMapId.equals(id)) {
                    throw new IllegalArgumentException("Map '" + id + "' is still the target of transition '"
                        + transition.instanceId + "' on map '" + map.id + "'");
                }
            }
        }
        if (!maps.removeIf(map -> map.id.equals(id))) {
            throw new IllegalArgumentException("No such map: " + id);
        }
    }

    MapAsset requireMap(String id) {
        MapAsset map = findMap(id);
        if (map == null) throw new IllegalArgumentException("No such map: " + id);
        return map;
    }
}
