package land.temmi.rollercoaster.editor.asset;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs tile top/side textures into one atlas as a stable row-major grid, not a general
 * bin-packer: every packed image - top or side - must share one size, so a shelf/bin packer
 * would add complexity without adding packing density. No rotation, for the same reason - it
 * only helps when packing differently shaped rectangles.
 */
public final class TilePacker {
    public static final int PADDING_PX = 1;
    public static final int MAX_PAGE_SIZE_PX = 2048;

    private TilePacker() {
    }

    public static final class Region {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        Region(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class PackedTile {
        public final String id;
        public final Region top;
        /** Null means "fall back to the top region", matching TilesetManifest's own default. */
        public final Region side;
        public final boolean walkable;

        PackedTile(String id, Region top, Region side, boolean walkable) {
            this.id = id;
            this.top = top;
            this.side = side;
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

    private static final class Cell {
        final String tileId;
        final boolean isSide;
        final BufferedImage image;

        Cell(String tileId, boolean isSide, BufferedImage image) {
            this.tileId = tileId;
            this.isSide = isSide;
            this.image = image;
        }
    }

    public static PackedTileset pack(List<TileSource> sources) {
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("At least one tile is required");
        checkNoDuplicateIds(sources);

        List<Cell> cells = new ArrayList<>();
        for (TileSource source : sources) {
            cells.add(new Cell(source.id, false, source.topImage));
            if (source.sideImage != null) cells.add(new Cell(source.id, true, source.sideImage));
        }

        int tileWidth = cells.get(0).image.getWidth();
        int tileHeight = cells.get(0).image.getHeight();
        if (tileWidth > MAX_PAGE_SIZE_PX || tileHeight > MAX_PAGE_SIZE_PX) {
            throw new IllegalArgumentException("Tile '" + sources.get(0).id + "' is " + tileWidth + "x" + tileHeight
                + "px, larger than the " + MAX_PAGE_SIZE_PX + "x" + MAX_PAGE_SIZE_PX + "px page limit");
        }
        for (Cell cell : cells) {
            if (cell.image.getWidth() != tileWidth || cell.image.getHeight() != tileHeight) {
                throw new IllegalArgumentException("Every top and side image in one tileset must share the same "
                    + "size: '" + cell.tileId + (cell.isSide ? "' side" : "'") + " is " + cell.image.getWidth() + "x"
                    + cell.image.getHeight() + "px, expected " + tileWidth + "x" + tileHeight + "px");
            }
        }

        int cellWidth = tileWidth + PADDING_PX;
        int cellHeight = tileHeight + PADDING_PX;
        int columns = Math.max(1, (MAX_PAGE_SIZE_PX + PADDING_PX) / cellWidth);
        int rows = (cells.size() + columns - 1) / columns;
        int atlasWidth = columns * cellWidth - PADDING_PX;
        int atlasHeight = rows * cellHeight - PADDING_PX;
        if (atlasHeight > MAX_PAGE_SIZE_PX) {
            throw new IllegalArgumentException("Tileset needs " + cells.size() + " regions of " + tileWidth + "x"
                + tileHeight + "px, which does not fit a single " + MAX_PAGE_SIZE_PX + "x" + MAX_PAGE_SIZE_PX
                + "px page. Multiple pages are not supported yet.");
        }

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Map<String, Region> topRegions = new LinkedHashMap<>();
        Map<String, Region> sideRegions = new LinkedHashMap<>();
        Graphics graphics = atlas.getGraphics();
        try {
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                int column = i % columns;
                int row = i / columns;
                int x = column * cellWidth;
                int y = row * cellHeight;
                graphics.drawImage(cell.image, x, y, null);
                Region region = new Region(x, y, tileWidth, tileHeight);
                (cell.isSide ? sideRegions : topRegions).put(cell.tileId, region);
            }
        } finally {
            graphics.dispose();
        }

        List<PackedTile> tiles = new ArrayList<>();
        for (TileSource source : sources) {
            tiles.add(new PackedTile(source.id, topRegions.get(source.id), sideRegions.get(source.id), source.walkable));
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
