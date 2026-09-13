package land.temmi.rollercoaster.editor.document;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** An editable directional sprite sheet and its animation assignment. */
public final class SpriteAsset {
    public final String id;
    public final String fileName;
    public final int columns;
    public final int rows;
    public final float worldHeight;
    public final float frameDuration;
    /** Fraction of the image height below the feet, consumed by BillboardRenderer as bottom padding. */
    public final float footOffset;
    private final Map<SpriteDirection, SpriteAnimationAsset> directions;

    public SpriteAsset(String id, String fileName, int columns, int rows, float worldHeight,
                       float frameDuration, float footOffset,
                       Map<SpriteDirection, SpriteAnimationAsset> directions) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Sprite id is required");
        if (fileName == null || fileName.trim().isEmpty() || !isFileName(fileName)) {
            throw new IllegalArgumentException("Sprite file name must be a single file name: " + id);
        }
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("Sprite grid must be positive: " + id);
        if (worldHeight <= 0f || frameDuration <= 0f || footOffset < 0f || footOffset >= 1f) {
            throw new IllegalArgumentException("Invalid sprite dimensions or timing: " + id);
        }
        if (directions == null) throw new IllegalArgumentException("Sprite directions are required: " + id);
        EnumMap<SpriteDirection, SpriteAnimationAsset> checked = new EnumMap<>(SpriteDirection.class);
        int frameCount = columns * rows;
        for (SpriteDirection direction : SpriteDirection.values()) {
            SpriteAnimationAsset animation = directions.get(direction);
            if (animation == null) throw new IllegalArgumentException("Missing sprite direction: " + direction.toId());
            validateFrame(animation.idleFrame, frameCount, id);
            for (int frame : animation.getWalkFrames()) validateFrame(frame, frameCount, id);
            checked.put(direction, animation);
        }
        this.id = id;
        this.fileName = fileName;
        this.columns = columns;
        this.rows = rows;
        this.worldHeight = worldHeight;
        this.frameDuration = frameDuration;
        this.footOffset = footOffset;
        this.directions = Collections.unmodifiableMap(checked);
    }

    public SpriteAnimationAsset direction(SpriteDirection direction) {
        if (direction == null) throw new IllegalArgumentException("Sprite direction is required");
        return directions.get(direction);
    }

    public Map<SpriteDirection, SpriteAnimationAsset> getDirections() {
        return directions;
    }

    private static void validateFrame(int frame, int frameCount, String id) {
        if (frame < 0 || frame >= frameCount) {
            throw new IllegalArgumentException("Sprite frame " + frame + " is outside grid for " + id);
        }
    }

    private static boolean isFileName(String value) {
        return value.indexOf('/') < 0 && value.indexOf('\\') < 0 && !".".equals(value) && !"..".equals(value);
    }
}
