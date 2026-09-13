package land.temmi.rollercoaster.editor.asset;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic single-page shelf packer for directional-sprite frames. */
public final class SpritePacker {
    public static final int PADDING_PX = 1;
    public static final int MAX_PAGE_SIZE_PX = 2048;

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

    public static final class PackedSpriteAtlas {
        public final BufferedImage image;
        private final Map<String, Region> regions;

        PackedSpriteAtlas(BufferedImage image, Map<String, Region> regions) {
            this.image = image;
            this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
        }

        public Region region(String id) {
            Region region = regions.get(id);
            if (region == null) throw new IllegalArgumentException("Unknown packed sprite region: " + id);
            return region;
        }

        public Map<String, Region> getRegions() {
            return regions;
        }
    }

    private SpritePacker() {
    }

    public static PackedSpriteAtlas pack(List<SpriteFrameSource> frames) {
        if (frames == null || frames.isEmpty()) throw new IllegalArgumentException("At least one sprite frame is required");
        Map<String, Region> regions = new LinkedHashMap<>();
        List<Placement> placements = new ArrayList<>();
        int cursorX = 0;
        int cursorY = 0;
        int rowHeight = 0;
        int atlasWidth = 0;
        for (SpriteFrameSource frame : frames) {
            if (regions.containsKey(frame.regionId)) throw new IllegalArgumentException("Duplicate sprite region: " + frame.regionId);
            int width = frame.image.getWidth();
            int height = frame.image.getHeight();
            if (width > MAX_PAGE_SIZE_PX || height > MAX_PAGE_SIZE_PX) {
                throw new IllegalArgumentException("Sprite frame '" + frame.regionId + "' exceeds the page limit");
            }
            if (cursorX > 0 && cursorX + width > MAX_PAGE_SIZE_PX) {
                cursorX = 0;
                cursorY += rowHeight + PADDING_PX;
                rowHeight = 0;
            }
            if (cursorY + height > MAX_PAGE_SIZE_PX) {
                throw new IllegalArgumentException("Sprite frames do not fit a single " + MAX_PAGE_SIZE_PX + "px page");
            }
            Region region = new Region(cursorX, cursorY, width, height);
            regions.put(frame.regionId, region);
            placements.add(new Placement(frame.image, region));
            cursorX += width + PADDING_PX;
            rowHeight = Math.max(rowHeight, height);
            atlasWidth = Math.max(atlasWidth, region.x + width);
        }
        int atlasHeight = cursorY + rowHeight;
        BufferedImage image = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        try {
            for (Placement placement : placements) {
                graphics.drawImage(placement.image, placement.region.x, placement.region.y, null);
            }
        } finally {
            graphics.dispose();
        }
        return new PackedSpriteAtlas(image, regions);
    }

    private static final class Placement {
        private final BufferedImage image;
        private final Region region;

        private Placement(BufferedImage image, Region region) {
            this.image = image;
            this.region = region;
        }
    }
}
