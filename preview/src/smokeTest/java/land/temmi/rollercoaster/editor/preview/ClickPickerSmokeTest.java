package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import land.temmi.rollercoaster.world.TerrainSurface;
import land.temmi.rollercoaster.world.TileMap;
import land.temmi.rollercoaster.world.TileShape;
import land.temmi.rollercoaster.world.TileSurface;

import java.lang.reflect.Proxy;

/**
 * Checks ClickPicker's ray-march terrain picking against a real TileMap/TerrainSurface - pure
 * math, so no GL context or window is needed, unlike the picker's actual InputAdapter role.
 * Camera.update() still calls into libgdx's native Matrix4/Frustum code, and Camera.unproject()
 * reads Gdx.graphics.getHeight() to flip the Y axis regardless of which overload is called - both
 * normally set up as a side effect of starting an LWJGL backend, so this test does so itself.
 */
public final class ClickPickerSmokeTest {
    public static void main(String[] args) {
        GdxNativesLoader.load();
        Gdx.graphics = fakeGraphics(800, 600);
        verifyPicksRealRampHeight();
        verifyFallsBackToGroundPlaneWithoutTerrain();
        System.out.println("PASS: ray-march picking hits the real ramp midpoint on a TerrainSurface, "
            + "and falls back to the Y=0 ground plane when no terrain is set");
    }

    private static void verifyPicksRealRampHeight() {
        // A single column: flat at 0, a ramp climbing to 1, flat at 1 - the same shape the engine's
        // own testfield uses to bridge levels.
        TileMap map = new TileMap(1, 3);
        TileSurface surface = new TileSurface("grass");
        map.set(0, 0, surface, 0f, TileShape.FLAT, false);
        map.set(0, 1, surface, 0.5f, TileShape.RAMP_SOUTH, false);
        map.set(0, 2, surface, 1f, TileShape.FLAT, false);
        TerrainSurface terrain = new TerrainSurface(map);

        // Tile (0, 1) covers world Z in [0, 1], centred at Z=0.5; its stored height (0.5) is
        // exactly the surface height there, since surfaceOffset is zero at the tile centre.
        float targetX = -0.5f;
        float targetZ = 0.5f;
        Vector3[] captured = new Vector3[1];
        ClickPicker picker = new ClickPicker(lookingDownAt(targetX, targetZ), hit -> captured[0] = new Vector3(hit));
        picker.setTerrainSurface(terrain);
        picker.touchDown(400, 300, 0, 0);
        picker.touchUp(400, 300, 0, 0);

        if (captured[0] == null) throw new AssertionError("Expected a terrain pick, got none");
        if (Math.abs(captured[0].y - 0.5f) > 0.02f) {
            throw new AssertionError("Expected the ramp's midpoint height 0.5, got " + captured[0].y);
        }
        if (Math.abs(captured[0].x - targetX) > 0.02f || Math.abs(captured[0].z - targetZ) > 0.02f) {
            throw new AssertionError("Pick landed at the wrong X/Z: " + captured[0]);
        }
    }

    private static void verifyFallsBackToGroundPlaneWithoutTerrain() {
        Vector3[] captured = new Vector3[1];
        ClickPicker picker = new ClickPicker(lookingDownAt(0f, 0f), hit -> captured[0] = new Vector3(hit));
        picker.touchDown(400, 300, 0, 0);
        picker.touchUp(400, 300, 0, 0);

        if (captured[0] == null) throw new AssertionError("Expected a ground-plane pick, got none");
        if (Math.abs(captured[0].y) > 0.001f) {
            throw new AssertionError("Expected the Y=0 ground plane, got " + captured[0]);
        }
    }

    private static Graphics fakeGraphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class},
            (proxy, method, methodArgs) -> switch (method.getName()) {
                case "getWidth" -> width;
                case "getHeight" -> height;
                default -> defaultValueFor(method.getReturnType());
            });
    }

    private static Object defaultValueFor(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        return 0;
    }

    private static PerspectiveCamera lookingDownAt(float x, float z) {
        PerspectiveCamera camera = new PerspectiveCamera(60f, 800, 600);
        camera.position.set(x, 10f, z);
        camera.lookAt(x, 0f, z);
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();
        return camera;
    }
}
