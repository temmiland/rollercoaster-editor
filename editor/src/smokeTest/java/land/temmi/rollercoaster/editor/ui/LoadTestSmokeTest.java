package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapEntityAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.ModelAsset;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;
import land.temmi.rollercoaster.world.ChunkMesher;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Measures full-map export/reload latency at a reference project size, over the real live-preview
 * protocol - this is the "vor Phase 6" measurement docs/plan.md asks for before deciding how much
 * incremental-rebuild work bullet 1 actually needs. Run with :editor:loadTestSmokeTest.
 *
 * <p>Reference size: 128x128 tiles (8x8 ChunkMesher.CHUNK_SIZE-16 chunks) with a prop every 8
 * tiles (256 props) - well beyond anything built by hand in this project so far, but still a
 * single ordinary overworld map, not a stress-test extreme.
 */
public final class LoadTestSmokeTest {
    private static final int MAP_SIZE = 128;
    private static final int PROP_SPACING = 8;
    private static final int LIVE_PREVIEW_TICKS = 20;

    public static void main(String[] args) throws Exception {
        String previewClasspath = System.getProperty("trackside.editor.previewClasspath");
        if (previewClasspath == null) {
            throw new IllegalStateException("Run via the loadTestSmokeTest Gradle task");
        }
        if (args.length != 1) throw new IllegalStateException("Usage: LoadTestSmokeTest <path-to-sample.gltf>");
        String sampleGltfPath = args[0];

        Path projectDirectory = Files.createTempDirectory("trackside-editor-load-test");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Load Test World");

        TextureAsset grass = controller.importTexture(solidColorPng("grass", 16, 16, Color.GREEN), "grass");
        controller.createTileset("overworld");
        controller.addTile("overworld", "grass", grass.id, null, true);

        CountDownLatch connected = new CountDownLatch(1);
        PreviewProcess.StatusListener statusListener = (status, detail) -> {
            if (status == PreviewProcess.Status.CONNECTED) connected.countDown();
        };
        PreviewProcess process = new PreviewProcess(previewClasspath, statusListener, (x, y, z) -> { }, entry -> { });
        process.start();
        try {
            if (!connected.await(20, TimeUnit.SECONDS)) throw new AssertionError("Preview never connected");

            ModelBoundsResult bounds = process.computeModelBounds(sampleGltfPath).get(20, TimeUnit.SECONDS);
            if (!bounds.success) throw new AssertionError("Bounds computation failed: " + bounds.errorMessage);

            Path sourceGltf = Path.of(sampleGltfPath);
            Path sourceBin = sourceGltf.resolveSibling("house.bin");
            ModelAsset house = controller.importModel(sourceGltf, List.of(sourceBin), "house",
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);

            controller.createMap("valley", MAP_SIZE, MAP_SIZE, "overworld");
            List<PaintTilesCommand.Edit> edits = new ArrayList<>();
            for (int z = 0; z < MAP_SIZE; z++) {
                for (int x = 0; x < MAP_SIZE; x++) edits.add(new PaintTilesCommand.Edit(x, z, null, "grass"));
            }
            controller.paintTiles("valley", edits);

            int propCount = 0;
            for (int z = PROP_SPACING / 2; z < MAP_SIZE; z += PROP_SPACING) {
                for (int x = PROP_SPACING / 2; x < MAP_SIZE; x += PROP_SPACING) {
                    controller.placeProp("valley",
                        new MapProp("house-" + propCount, house.id, x + 0.5f, z + 0.5f, 0f, 0f));
                    propCount++;
                }
            }
            controller.placeEntity("valley", new MapEntityAsset("player-start", "player", null, 0, 0));

            System.out.println("Reference map: " + MAP_SIZE + "x" + MAP_SIZE + " tiles ("
                + (MAP_SIZE / ChunkMesher.CHUNK_SIZE) + "x" + (MAP_SIZE / ChunkMesher.CHUNK_SIZE) + " chunks), "
                + propCount + " props");

            long exportStart = System.nanoTime();
            Path mapFile = controller.exportMap("valley");
            Path tilesetFile = controller.exportTileset("overworld");
            Path modelsFile = controller.exportModels();
            long exportMs = (System.nanoTime() - exportStart) / 1_000_000;
            System.out.println("Full export (map+tileset+models): " + exportMs + " ms");

            long coldStart = System.nanoTime();
            ShowMapResult cold = process.showMap(mapFile.toAbsolutePath().toString(), MAP_SIZE, MAP_SIZE,
                tilesetFile.toAbsolutePath().toString(), modelsFile.toAbsolutePath().toString(), null, null)
                .get(60, TimeUnit.SECONDS);
            long coldMs = (System.nanoTime() - coldStart) / 1_000_000;
            if (!cold.success) throw new AssertionError("Cold ShowMap failed: " + cold.errorMessage);
            System.out.println("Cold ShowMap (full WorldSceneLoader/ChunkMesher build): " + coldMs + " ms");

            // Today's live-preview debounce (MapPanel.startQueuedPreview) re-exports and reloads
            // the WHOLE map on every settled brush stroke, however small. This loop measures
            // exactly that: the per-stroke latency a painting session on the reference map
            // actually experiences right now, with no chunk-level diffing anywhere in the path.
            long[] tickMs = new long[LIVE_PREVIEW_TICKS];
            for (int i = 0; i < LIVE_PREVIEW_TICKS; i++) {
                int x = i % MAP_SIZE;
                int z = (i / MAP_SIZE) % MAP_SIZE;
                controller.paintTiles("valley", List.of(new PaintTilesCommand.Edit(x, z, "grass", "grass")));

                long tickStart = System.nanoTime();
                Path tickMapFile = controller.exportMap("valley");
                ShowMapResult tick = process.showMap(tickMapFile.toAbsolutePath().toString(), MAP_SIZE, MAP_SIZE,
                    tilesetFile.toAbsolutePath().toString(), modelsFile.toAbsolutePath().toString(), null, null)
                    .get(60, TimeUnit.SECONDS);
                tickMs[i] = (System.nanoTime() - tickStart) / 1_000_000;
                if (!tick.success) throw new AssertionError("Tick " + i + " ShowMap failed: " + tick.errorMessage);
            }
            long total = 0, max = 0, min = Long.MAX_VALUE;
            for (long ms : tickMs) {
                total += ms;
                max = Math.max(max, ms);
                min = Math.min(min, ms);
            }
            System.out.println("Single-tile-edit live-preview ticks over " + LIVE_PREVIEW_TICKS + " repeats: min="
                + min + "ms avg=" + (total / LIVE_PREVIEW_TICKS) + "ms max=" + max + "ms (each is a full "
                + "re-export + full WorldSceneLoader/ChunkMesher rebuild of the whole map)");

            // Repeated switching between very differently sized scenes is where a resource-
            // cleanup ordering bug would most likely surface: shrinking after a large scene, then
            // growing again, exercises disposeDocumentAssets()'s build-new-then-dispose-old path
            // from both directions instead of just repeating the same size.
            controller.createMap("outpost", 4, 4, "overworld");
            List<PaintTilesCommand.Edit> smallEdits = new ArrayList<>();
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) smallEdits.add(new PaintTilesCommand.Edit(x, z, null, "grass"));
            }
            controller.paintTiles("outpost", smallEdits);
            Path smallMapFile = controller.exportMap("outpost");

            for (int i = 0; i < 4; i++) {
                ShowMapResult big = process.showMap(mapFile.toAbsolutePath().toString(), MAP_SIZE, MAP_SIZE,
                    tilesetFile.toAbsolutePath().toString(), modelsFile.toAbsolutePath().toString(), null, null)
                    .get(60, TimeUnit.SECONDS);
                if (!big.success) throw new AssertionError("Switch-back-to-big ShowMap failed: " + big.errorMessage);
                ShowMapResult small = process.showMap(smallMapFile.toAbsolutePath().toString(), 4, 4,
                    tilesetFile.toAbsolutePath().toString(), null, null, null).get(60, TimeUnit.SECONDS);
                if (!small.success) throw new AssertionError("Switch-to-small ShowMap failed: " + small.errorMessage);
            }
            System.out.println("Repeated large/small map switching completed without a failed ShowMap "
                + "or a dropped connection");
        } finally {
            process.stop();
        }

        System.out.println("PASS: load test measured full-map export/reload latency at " + MAP_SIZE + "x" + MAP_SIZE
            + " tiles with a scattered prop field, and repeated large/small map switching, over the live "
            + "preview protocol");
        System.exit(0);
    }

    private static Path solidColorPng(String name, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        Path file = Files.createTempDirectory("trackside-editor-load-test-texture").resolve(name + ".png");
        javax.imageio.ImageIO.write(image, "PNG", file.toFile());
        return file;
    }
}
