package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.MapExport;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Spawns the real preview subprocess and asks it to compute bounds for a real sample GLTF file -
 * the one piece of the model-import pipeline that needs a live GL context (to build a Model/Mesh)
 * and so can't be checked by a headless smoke test. Run with :editor:previewProcessSmokeTest.
 */
public final class PreviewProcessSmokeTest {
    public static void main(String[] args) throws Exception {
        String previewClasspath = System.getProperty("trackside.editor.previewClasspath");
        if (previewClasspath == null) {
            throw new IllegalStateException("Run via the previewProcessSmokeTest Gradle task");
        }
        if (args.length != 1) throw new IllegalStateException("Usage: PreviewProcessSmokeTest <path-to-sample.gltf>");
        String sampleGltfPath = args[0];

        CountDownLatch connected = new CountDownLatch(1);
        PreviewProcess.StatusListener statusListener = (status, detail) -> {
            System.out.println(status + (detail != null ? ": " + detail : ""));
            if (status == PreviewProcess.Status.CONNECTED) connected.countDown();
        };
        PreviewProcess.PickListener pickListener = (x, y, z) -> { };

        PreviewProcess process = new PreviewProcess(previewClasspath, statusListener, pickListener);
        process.start();
        try {
            if (!connected.await(20, TimeUnit.SECONDS)) throw new AssertionError("Preview never connected");

            CompletableFuture<ModelBoundsResult> future = process.computeModelBounds(sampleGltfPath);
            ModelBoundsResult result = future.get(20, TimeUnit.SECONDS);

            if (!result.success) throw new AssertionError("Bounds computation failed: " + result.errorMessage);
            System.out.println("Bounds: min=(" + result.minX + "," + result.minY + "," + result.minZ
                + ") max=(" + result.maxX + "," + result.maxY + "," + result.maxZ + ")");
            if (result.maxY <= result.minY) throw new AssertionError("Expected a positive height above the anchor");
            if (result.maxX <= result.minX || result.maxZ <= result.minZ) {
                throw new AssertionError("Expected nonzero horizontal extent");
            }

            Path mapDirectory = Files.createTempDirectory("preview-process-smoke-test-map");
            MapExport.write("valley", 2, 2, "overworld",
                new String[] {"grass", "grass", "grass", "grass"}, new float[] {0f, 0f, 0f, 0f},
                new String[] {"flat", "flat", "flat", "flat"}, new boolean[] {false, false, false, false},
                mapDirectory);
            String mapFilePath = mapDirectory.resolve("valley.json").toAbsolutePath().toString();

            CompletableFuture<ShowMapResult> showMapFuture = process.showMap(mapFilePath, 2, 2,
                new String[] {"grass"}, new boolean[] {true});
            ShowMapResult showMapResult = showMapFuture.get(20, TimeUnit.SECONDS);
            if (!showMapResult.success) throw new AssertionError("ShowMap failed: " + showMapResult.errorMessage);
            System.out.println("ShowMap succeeded for a real exported map, built through a real WorldSceneLoader");
        } finally {
            process.stop();
        }

        System.out.println("PASS: preview computed real bounds for a sample GLTF file, and rendered a real "
            + "exported map through WorldSceneLoader/ChunkMesher, both over the live protocol");
        System.exit(0);
    }
}
