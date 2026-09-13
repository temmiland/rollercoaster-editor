package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.files.FileHandle;
import land.temmi.rollercoaster.world.TileDefinition;
import land.temmi.rollercoaster.world.TilesetManifest;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        System.out.println("PASS: tile packing and tileset export read back correctly "
            + "by the real TilesetManifest parser, with clear errors for bad input");
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
