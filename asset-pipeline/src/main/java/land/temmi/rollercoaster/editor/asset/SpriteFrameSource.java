package land.temmi.rollercoaster.editor.asset;

import java.awt.image.BufferedImage;

/** One named frame to put on the single runtime sprite-atlas page. */
public final class SpriteFrameSource {
    public final String regionId;
    public final BufferedImage image;

    public SpriteFrameSource(String regionId, BufferedImage image) {
        if (regionId == null || regionId.trim().isEmpty()) throw new IllegalArgumentException("Sprite region id is required");
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("Sprite image is required: " + regionId);
        }
        this.regionId = regionId;
        this.image = image;
    }
}
