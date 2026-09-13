package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.math.Vector3;
import land.temmi.rollercoaster.world.TerrainSurface;

import java.util.function.Consumer;

/**
 * Reports a pick whenever the user clicks without dragging the camera: against the real terrain
 * surface when a document map or level is active, or a flat Y=0 plane for the generic scene that
 * has no terrain at all. The free preview camera never snaps to a pixel grid, so - unlike the
 * game's PixelCamera - this needs no correction to invProjectionView after the fact.
 */
final class ClickPicker extends InputAdapter {
    private static final float CLICK_DRAG_TOLERANCE_PX = 4f;
    private static final float MARCH_STEP = 0.25f;
    private static final float MARCH_MAX_DISTANCE = 500f;
    private static final int REFINE_ITERATIONS = 8;

    private final Camera camera;
    private final Consumer<Vector3> onPick;
    private final Plane groundPlane = new Plane(new Vector3(0f, 1f, 0f), 0f);
    private final Vector2 downPosition = new Vector2();
    private final Vector3 hit = new Vector3();
    private final Vector3 marchPoint = new Vector3();

    private TerrainSurface terrainSurface;

    ClickPicker(Camera camera, Consumer<Vector3> onPick) {
        this.camera = camera;
        this.onPick = onPick;
    }

    /** Null means no real terrain is shown right now (the generic placeholder scene). */
    void setTerrainSurface(TerrainSurface terrainSurface) {
        this.terrainSurface = terrainSurface;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        downPosition.set(screenX, screenY);
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (downPosition.dst(screenX, screenY) <= CLICK_DRAG_TOLERANCE_PX) {
            // The 2-arg overload reads Gdx.graphics instead, which PreviewApplication.resize()
            // always keeps equal to the camera's own viewport anyway - using that explicitly
            // keeps this class free of implicit global state and testable without a live app.
            Ray ray = camera.getPickRay(screenX, screenY, 0, 0, camera.viewportWidth, camera.viewportHeight);
            boolean picked = terrainSurface != null
                ? marchTerrain(ray, terrainSurface, hit)
                : Intersector.intersectRayPlane(ray, groundPlane, hit);
            if (picked) onPick.accept(hit);
        }
        return false;
    }

    /** Ray-marches along the pick ray until it crosses the terrain height field, then bisects to
     * refine the crossing - cheap and accurate enough for editor picking without a full mesh raycast. */
    private boolean marchTerrain(Ray ray, TerrainSurface surface, Vector3 out) {
        float previousDistance = 0f;
        float previousDiff = 0f;
        boolean havePrevious = false;
        for (float distance = 0f; distance <= MARCH_MAX_DISTANCE; distance += MARCH_STEP) {
            ray.getEndPoint(marchPoint, distance);
            float diff = marchPoint.y - surface.heightAt(marchPoint.x, marchPoint.z);
            if (havePrevious && Math.signum(diff) != Math.signum(previousDiff)) {
                float lo = previousDistance;
                float hi = distance;
                for (int i = 0; i < REFINE_ITERATIONS; i++) {
                    float mid = (lo + hi) * 0.5f;
                    ray.getEndPoint(marchPoint, mid);
                    float midDiff = marchPoint.y - surface.heightAt(marchPoint.x, marchPoint.z);
                    if (Math.signum(midDiff) == Math.signum(previousDiff)) lo = mid; else hi = mid;
                }
                ray.getEndPoint(marchPoint, (lo + hi) * 0.5f);
                out.set(marchPoint.x, surface.heightAt(marchPoint.x, marchPoint.z), marchPoint.z);
                return true;
            }
            previousDistance = distance;
            previousDiff = diff;
            havePrevious = true;
        }
        return false;
    }
}
