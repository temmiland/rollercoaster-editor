package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One direction's still frame plus a non-empty walking loop, expressed as sheet-frame indices. */
public final class SpriteAnimationAsset {
    public final int idleFrame;
    private final List<Integer> walkFrames;

    public SpriteAnimationAsset(int idleFrame, List<Integer> walkFrames) {
        if (idleFrame < 0) throw new IllegalArgumentException("Sprite idle frame must not be negative");
        if (walkFrames == null || walkFrames.isEmpty()) {
            throw new IllegalArgumentException("Sprite walk frames are required");
        }
        List<Integer> checked = new ArrayList<>();
        for (Integer frame : walkFrames) {
            if (frame == null || frame < 0) throw new IllegalArgumentException("Sprite walk frame must not be negative");
            checked.add(frame);
        }
        this.idleFrame = idleFrame;
        this.walkFrames = Collections.unmodifiableList(checked);
    }

    public List<Integer> getWalkFrames() {
        return walkFrames;
    }
}
