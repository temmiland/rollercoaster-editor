package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.math.Vector3;

import java.util.function.Consumer;

/**
 * Reports a ground-plane (Y=0) pick whenever the user clicks without dragging the camera.
 * Returns false from every callback so it never steals input from the camera controller it
 * shares the input multiplexer with.
 */
final class ClickPicker extends InputAdapter {
    private static final float CLICK_DRAG_TOLERANCE_PX = 4f;

    private final Camera camera;
    private final Consumer<Vector3> onPick;
    private final Plane groundPlane = new Plane(new Vector3(0f, 1f, 0f), 0f);
    private final Vector2 downPosition = new Vector2();
    private final Vector3 hit = new Vector3();

    ClickPicker(Camera camera, Consumer<Vector3> onPick) {
        this.camera = camera;
        this.onPick = onPick;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        downPosition.set(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (downPosition.dst(screenX, screenY) <= CLICK_DRAG_TOLERANCE_PX) {
            Ray ray = camera.getPickRay(screenX, screenY);
            if (Intersector.intersectRayPlane(ray, groundPlane, hit)) {
                onPick.accept(hit);
            }
        }
        return false;
    }
}
