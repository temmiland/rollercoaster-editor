package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.files.FileHandle;
import land.temmi.rollercoaster.asset.ModelDefinition;
import land.temmi.rollercoaster.asset.ModelManifest;
import land.temmi.rollercoaster.asset.SpriteDefinition;
import land.temmi.rollercoaster.asset.SpriteManifest;
import land.temmi.rollercoaster.world.LoadedMap;
import land.temmi.rollercoaster.world.MapLoader;
import land.temmi.rollercoaster.world.TileDefinition;
import land.temmi.rollercoaster.world.TileShape;
import land.temmi.rollercoaster.world.TileSurface;
import land.temmi.rollercoaster.world.Tileset;
import land.temmi.rollercoaster.world.TilesetManifest;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Checks tile packing and the tileset export against the real, reflection-free TilesetManifest
 * parser - no GL context involved, FileHandle is built directly from a java.io.File. */
public final class AssetPipelineSmokeTest {
    public static void main(String[] args) throws IOException {
        verifyPackAndExportRoundtrip();
        verifyExplicitSideRegionRoundtrip();
        verifyRejectsMismatchedTileSize();
        verifyRejectsMismatchedSideSize();
        verifyRejectsDuplicateIds();
        verifyRejectsOversizedTileset();
        verifyModelManifestExportRoundtrip();
        verifySpriteAtlasAndManifestRoundtrip();
        verifyMapExportRoundtrip();
        verifyMapExportWithPropsRoundtrip();
        verifyRejectsUnpaintedMap();
        System.out.println("PASS: tile packing and tileset export read back correctly "
            + "by the real TilesetManifest parser, model manifest export read back by the real "
            + "ModelManifest parser, sprite export read back by the real SpriteManifest parser, map export read back by the real MapLoader parser, "
            + "with clear errors for bad input");
    }

    private static void verifyMapExportRoundtrip() throws IOException {
        int width = 3;
        int depth = 2;
        String[] tiles = {"grass", "grass", "grass", "grass", "grass", "grass"};
        float[] heights = {0f, 0.5f, 0f, 0f, 0f, 0f};
        String[] shapes = {"flat", "ramp_east", "flat", "flat", "flat", "flat"};
        boolean[] collision = {false, false, false, false, false, true};

        Path directory = Files.createTempDirectory("trackside-editor-map");
        MapExport.write("valley", width, depth, "overworld", tiles, heights, shapes, collision,
            List.of(), List.of(), List.of(), directory);

        Path mapFile = directory.resolve("valley.json");
        if (!Files.exists(mapFile)) throw new AssertionError("Map JSON was not written");

        Tileset tileset = new Tileset().add(new TileSurface("grass"));
        LoadedMap loaded = new MapLoader().load(new FileHandle(mapFile.toFile()), tileset);
        if (!"valley".equals(loaded.name)) throw new AssertionError("Map name did not round-trip");
        if (loaded.tiles.getWidth() != width || loaded.tiles.getDepth() != depth) {
            throw new AssertionError("Map size did not round-trip");
        }
        if (loaded.tiles.getHeight(1, 0) != 0.5f || loaded.tiles.getShape(1, 0) != TileShape.RAMP_EAST) {
            throw new AssertionError("Terrain shape/height did not round-trip");
        }
        if (!loaded.tiles.isBlocked(2, 1)) throw new AssertionError("Collision layer did not round-trip");
        if (loaded.tiles.isBlocked(0, 0)) throw new AssertionError("Unblocked cells should round-trip as unblocked");
        if (loaded.props.size != 0 || loaded.entities.size != 0) {
            throw new AssertionError("A freshly exported map should have no props or entities yet");
        }
    }

    private static void verifySpriteAtlasAndManifestRoundtrip() throws IOException {
        List<SpriteFrameSource> frames = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            frames.add(new SpriteFrameSource("player-frame-" + index,
                solidColor(16, 24, index % 2 == 0 ? Color.GREEN : Color.ORANGE)));
        }
        SpritePacker.PackedSpriteAtlas packed = SpritePacker.pack(frames);
        Map<String, SpriteManifestExport.Direction> directions = new LinkedHashMap<>();
        String[] names = {"north", "east", "south", "west"};
        for (int index = 0; index < names.length; index++) {
            int firstFrame = index * 3;
            directions.put(names[index], new SpriteManifestExport.Direction("player-frame-" + firstFrame,
                List.of("player-frame-" + (firstFrame + 1), "player-frame-" + (firstFrame + 2))));
        }
        Path directory = Files.createTempDirectory("trackside-editor-sprites");
        SpriteAtlasExport.write(packed, "sprites.atlas", "sprites.png", directory);
        SpriteManifestExport.write(List.of(new SpriteManifestExport.Entry("player", 1.5f, 0.12f, 0.1f, directions)),
            "sprites.atlas", directory);
        if (!Files.exists(directory.resolve("sprites.png")) || !Files.exists(directory.resolve("sprites.atlas"))) {
            throw new AssertionError("Sprite atlas PNG or descriptor was not written");
        }
        SpriteManifest manifest = SpriteManifest.load(new FileHandle(directory.resolve("sprites.json").toFile()));
        SpriteDefinition player = manifest.sprite("player");
        if (!"sprites.atlas".equals(manifest.atlas) || player.worldHeight != 1.5f
            || player.frameDuration != 0.12f || player.footOffset != 0.1f
            || !"player-frame-0".equals(player.idleRegion(land.temmi.rollercoaster.actor.Facing.NORTH))
            || player.walkRegions(land.temmi.rollercoaster.actor.Facing.WEST).length != 2) {
            throw new AssertionError("Sprite manifest did not round-trip");
        }
    }

    private static void verifyMapExportWithPropsRoundtrip() throws IOException {
        String[] tiles = {"grass"};
        float[] heights = {0f};
        String[] shapes = {"flat"};
        boolean[] collision = {false};
        List<MapExport.Prop> props = List.of(new MapExport.Prop("house", 8f, 10f, 0.25f, 90f));

        Path directory = Files.createTempDirectory("trackside-editor-map-props");
        List<MapExport.Light> lights = List.of(
            new MapExport.Light("lamp-1", 8f, 1.5f, 10f, 1f, 0.9f, 0.7f, 1.2f, 5f, true),
            new MapExport.Light("spot-1", 3f, 3f, 3f, 1f, 1f, 1f, 1f, 6f, true, true, 0f, -1f, 0f, 20f, 35f));
        MapExport.write("withProps", 1, 1, "overworld", tiles, heights, shapes, collision, props,
            List.of(new MapExport.Entity("player-start", "player", "player", 0, 0)), lights, directory);

        Tileset tileset = new Tileset().add(new TileSurface("grass"));
        LoadedMap loaded = new MapLoader().load(
            new FileHandle(directory.resolve("withProps.json").toFile()), tileset);
        if (loaded.props.size != 1) throw new AssertionError("Expected 1 prop, got " + loaded.props.size);
        land.temmi.rollercoaster.world.MapProp prop = loaded.props.first();
        if (!"house".equals(prop.model) || prop.x != 8f || prop.z != 10f
            || prop.elevation != 0.25f || prop.rotation != 90f) {
            throw new AssertionError("Prop did not round-trip: " + prop.model + " " + prop.x + "," + prop.z);
        }
        if (loaded.entities.size != 1 || !"player-start".equals(loaded.entities.first().id)
            || !"player".equals(loaded.entities.first().sprite)) {
            throw new AssertionError("Entity did not round-trip through the runtime map loader");
        }
        if (loaded.lights.size != 2) throw new AssertionError("Expected 2 lights, got " + loaded.lights.size);
        land.temmi.rollercoaster.world.MapLight point = loaded.lights.get(0);
        if (!"lamp-1".equals(point.id) || point.x != 8f || point.y != 1.5f || point.z != 10f
            || point.colorR != 1f || point.colorG != 0.9f || point.colorB != 0.7f
            || point.intensity != 1.2f || point.range != 5f || !point.enabled || point.spot) {
            throw new AssertionError("Point light did not round-trip");
        }
        land.temmi.rollercoaster.world.MapLight spot = loaded.lights.get(1);
        if (!spot.spot || spot.innerAngle != 20f || spot.outerAngle != 35f || spot.directionY != -1f) {
            throw new AssertionError("Spot light did not round-trip");
        }
    }

    private static void verifyRejectsUnpaintedMap() throws IOException {
        String[] tiles = new String[4];
        float[] heights = new float[4];
        String[] shapes = {"flat", "flat", "flat", "flat"};
        boolean[] collision = new boolean[4];
        Path directory = Files.createTempDirectory("trackside-editor-map-empty");
        try {
            MapExport.write("empty", 2, 2, "overworld", tiles, heights, shapes, collision,
                List.of(), List.of(), List.of(), directory);
            throw new AssertionError("Exporting a map with unpainted cells should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifyModelManifestExportRoundtrip() throws IOException {
        ModelManifestExport.Entry house = new ModelManifestExport.Entry("house", "gltf:models/house.gltf",
            -0.5f, 0f, -0.5f, 1f, 4f,
            -1.8f, 0f, -1.3f, 2.8f, 4f, 2.3f,
            -2, 2, -2, 1, false, false, 0f);

        Path directory = Files.createTempDirectory("trackside-editor-models");
        ModelManifestExport.write(List.of(house), directory);

        Path manifestFile = directory.resolve(ModelManifestExport.FILE_NAME);
        if (!Files.exists(manifestFile)) throw new AssertionError("models.json was not written");

        com.badlogic.gdx.utils.Array<ModelDefinition> definitions =
            ModelManifest.load(new FileHandle(manifestFile.toFile()));
        if (definitions.size != 1) throw new AssertionError("Expected 1 model in the manifest");
        ModelDefinition definition = definitions.first();
        if (!"house".equals(definition.id) || !"gltf:models/house.gltf".equals(definition.source)) {
            throw new AssertionError("Model id/source did not round-trip");
        }
        if (definition.boundsMaxX != 2.8f || definition.collisionMinX != -2 || definition.collisionMaxZ != 1) {
            throw new AssertionError("Model bounds/collision did not round-trip");
        }
        if (definition.height != 4f) throw new AssertionError("Model height did not round-trip");
    }

    private static void verifyPackAndExportRoundtrip() throws IOException {
        List<TileSource> sources = List.of(
            new TileSource("grass", solidColor(16, 16, Color.GREEN), true),
            new TileSource("path", solidColor(16, 16, Color.ORANGE), true),
            new TileSource("wall", solidColor(16, 16, Color.GRAY), false));

        TilePacker.PackedTileset packed = TilePacker.pack(sources);
        if (packed.tileWidth != 16 || packed.tileHeight != 16) throw new AssertionError("Wrong tile size recorded");
        if (packed.tiles.size() != 3) throw new AssertionError("Expected 3 packed tiles");

        Path directory = Files.createTempDirectory("trackside-editor-tileset");
        TilesetExport.write(packed, "overworld", "overworld.png", directory);

        Path textureFile = directory.resolve("overworld.png");
        if (!Files.exists(textureFile)) throw new AssertionError("Texture PNG was not written");
        BufferedImage written = javax.imageio.ImageIO.read(textureFile.toFile());
        if (written.getWidth() != packed.atlas.getWidth() || written.getHeight() != packed.atlas.getHeight()) {
            throw new AssertionError("Written PNG size does not match the packed atlas");
        }

        FileHandle manifestFile = new FileHandle(directory.resolve("overworld.json").toFile());
        TilesetManifest manifest = TilesetManifest.load(manifestFile);
        if (manifest.tiles.size != 3) throw new AssertionError("Manifest lost a tile: " + manifest.tiles.size);
        if (!"overworld.png".equals(manifest.texture)) throw new AssertionError("Wrong texture filename in manifest");
        if (manifest.tileWidth != 16 || manifest.tileHeight != 16) throw new AssertionError("Wrong tileSize in manifest");

        for (TilePacker.PackedTile expected : packed.tiles) {
            TileDefinition actual = find(manifest, expected.id);
            if (actual.atlasX != expected.top.x || actual.atlasY != expected.top.y
                || actual.atlasWidth != expected.top.width || actual.atlasHeight != expected.top.height) {
                throw new AssertionError("Region mismatch for '" + expected.id + "'");
            }
            if (actual.walkable != expected.walkable) throw new AssertionError("Walkable flag mismatch for '" + expected.id + "'");
            // No side image was given, so TilesetManifest must fall back to the top region.
            if (actual.sideX != actual.atlasX || actual.sideY != actual.atlasY
                || actual.sideWidth != actual.atlasWidth || actual.sideHeight != actual.atlasHeight) {
                throw new AssertionError("Side region should default to the top region for '" + expected.id + "'");
            }
        }
    }

    private static void verifyExplicitSideRegionRoundtrip() throws IOException {
        List<TileSource> sources = List.of(
            new TileSource("cliff", solidColor(16, 16, Color.GREEN), solidColor(16, 16, Color.DARK_GRAY), true));

        TilePacker.PackedTileset packed = TilePacker.pack(sources);
        TilePacker.PackedTile tile = packed.tiles.get(0);
        if (tile.side == null) throw new AssertionError("Side region was not packed");
        if (tile.side.x == tile.top.x && tile.side.y == tile.top.y) {
            throw new AssertionError("Side region should be packed at a different atlas position than top");
        }

        Path directory = Files.createTempDirectory("trackside-editor-tileset-side");
        TilesetExport.write(packed, "cliffside", "cliffside.png", directory);
        TilesetManifest manifest = TilesetManifest.load(new FileHandle(directory.resolve("cliffside.json").toFile()));
        TileDefinition definition = find(manifest, "cliff");
        if (definition.sideX != tile.side.x || definition.sideY != tile.side.y
            || definition.sideWidth != tile.side.width || definition.sideHeight != tile.side.height) {
            throw new AssertionError("Side region did not round-trip through the manifest");
        }
        if (definition.sideX == definition.atlasX && definition.sideY == definition.atlasY) {
            throw new AssertionError("Side region collapsed to the top region in the manifest");
        }
    }

    private static void verifyRejectsMismatchedTileSize() {
        List<TileSource> sources = List.of(
            new TileSource("a", solidColor(16, 16, Color.GREEN), true),
            new TileSource("b", solidColor(32, 32, Color.RED), true));
        try {
            TilePacker.pack(sources);
            throw new AssertionError("Packing mismatched tile sizes should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifyRejectsMismatchedSideSize() {
        List<TileSource> sources = List.of(
            new TileSource("cliff", solidColor(16, 16, Color.GREEN), solidColor(16, 32, Color.DARK_GRAY), true));
        try {
            TilePacker.pack(sources);
            throw new AssertionError("Packing a side image sized differently than the top should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifyRejectsDuplicateIds() {
        List<TileSource> sources = List.of(
            new TileSource("grass", solidColor(16, 16, Color.GREEN), true),
            new TileSource("grass", solidColor(16, 16, Color.GREEN), true));
        try {
            TilePacker.pack(sources);
            throw new AssertionError("Packing duplicate tile ids should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void verifyRejectsOversizedTileset() {
        // 512px tiles: 4 columns fit a 2048px page, so 5 tiles need a second row - and a single
        // 512px-tall row already leaves no room for one, since 4*513-1 columns still needs
        // ceil(20/4)=5 rows of 513px each to hold 20 tiles, well past the 2048px page limit.
        List<TileSource> sources = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            sources.add(new TileSource("tile" + i, solidColor(512, 512, Color.BLUE), true));
        }
        try {
            TilePacker.pack(sources);
            throw new AssertionError("Packing a tileset past the page limit should fail");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static TileDefinition find(TilesetManifest manifest, String id) {
        for (TileDefinition definition : manifest.tiles) {
            if (definition.id.equals(id)) return definition;
        }
        throw new AssertionError("Manifest has no tile '" + id + "'");
    }

    private static BufferedImage solidColor(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
