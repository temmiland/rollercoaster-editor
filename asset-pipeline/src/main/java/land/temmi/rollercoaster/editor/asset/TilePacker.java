package land.temmi.rollercoaster.editor.asset;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Packs same-sized tile textures into one atlas as a stable row-major grid, not a general
 * bin-packer: the tileset manifest already assumes one shared tileWidth/tileHeight for the whole
 * set, so a shelf/bin packer would add complexity without adding packing density. No rotation,
 * for the same reason - it only helps when packing differently shaped rectangles.
 */
public final class TilePacker {
    public static final int PADDING_PX = 1;
    public static final int MAX_PAGE_SIZE_PX = 2048;

    private TilePacker() {
    }

    public static final class PackedTile {
        public final String id;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final boolean walkable;

        PackedTile(String id, int x, int y, int width, int height, boolean walkable) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.walkable = walkable;
        }
    }

    public static final class PackedTileset {
        public final BufferedImage atlas;
        public final int tileWidth;
        public final int tileHeight;
        public final List<PackedTile> tiles;

        PackedTileset(BufferedImage atlas, int tileWidth, int tileHeight, List<PackedTile> tiles) {
            this.atlas = atlas;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.tiles = tiles;
        }
    }

    public static PackedTileset pack(List<TileSource> sources) {
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("At least one tile is required");
        checkNoDuplicateIds(sources);

        int tileWidth = sources.get(0).image.getWidth();
        int tileHeight = sources.get(0).image.getHeight();
        if (tileWidth > MAX_PAGE_SIZE_PX || tileHeight > MAX_PAGE_SIZE_PX) {
            throw new IllegalArgumentException("Tile '" + sources.get(0).id + "' is " + tileWidth + "x" + tileHeight
                + "px, larger than the " + MAX_PAGE_SIZE_PX + "x" + MAX_PAGE_SIZE_PX + "px page limit");
        }
        for (TileSource source : sources) {
            if (source.image.getWidth() != tileWidth || source.image.getHeight() != tileHeight) {
                throw new IllegalArgumentException("All tiles in one tileset must share the same size: '"
                    + source.id + "' is " + source.image.getWidth() + "x" + source.image.getHeight()
                    + "px, expected " + tileWidth + "x" + tileHeight + "px");
            }
        }

        int cellWidth = tileWidth + PADDING_PX;
        int cellHeight = tileHeight + PADDING_PX;
        int columns = Math.max(1, (MAX_PAGE_SIZE_PX + PADDING_PX) / cellWidth);
        int rows = (sources.size() + columns - 1) / columns;
        int atlasWidth = columns * cellWidth - PADDING_PX;
        int atlasHeight = rows * cellHeight - PADDING_PX;
        if (atlasHeight > MAX_PAGE_SIZE_PX) {
            throw new IllegalArgumentException("Tileset needs " + sources.size() + " tiles of " + tileWidth + "x"
                + tileHeight + "px, which does not fit a single " + MAX_PAGE_SIZE_PX + "x" + MAX_PAGE_SIZE_PX
                + "px page. Multiple pages are not supported yet.");
        }

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = atlas.getGraphics();
        List<PackedTile> tiles = new ArrayList<>();
        try {
            for (int i = 0; i < sources.size(); i++) {
                TileSource source = sources.get(i);
                int column = i % columns;
                int row = i / columns;
                int x = column * cellWidth;
                int y = row * cellHeight;
                graphics.drawImage(source.image, x, y, null);
                tiles.add(new PackedTile(source.id, x, y, tileWidth, tileHeight, source.walkable));
            }
        } finally {
            graphics.dispose();
        }
        return new PackedTileset(atlas, tileWidth, tileHeight, tiles);
    }

    private static void checkNoDuplicateIds(List<TileSource> sources) {
        for (int i = 0; i < sources.size(); i++) {
            for (int j = i + 1; j < sources.size(); j++) {
                if (sources.get(i).id.equals(sources.get(j).id)) {
                    throw new IllegalArgumentException("Duplicate tile id: " + sources.get(i).id);
                }
            }
        }
    }
}
