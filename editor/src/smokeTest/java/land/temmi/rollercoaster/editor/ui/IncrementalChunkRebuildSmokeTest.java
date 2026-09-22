package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.MapExport;
import land.temmi.rollercoaster.editor.asset.ModelManifestExport;
import land.temmi.rollercoaster.editor.asset.TilePacker;
import land.temmi.rollercoaster.editor.asset.TileSource;
import land.temmi.rollercoaster.editor.asset.TilesetExport;
import land.temmi.rollercoaster.editor.protocol.CaptureScreenshotResult;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;

import javax.imageio.ImageIO;
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
 * The one test in this project with real stakes if it is wrong rather than merely incomplete:
 * PreviewApplication.loadDocumentScene lets a live-preview tick reuse another WorldScene's chunk
 * models instead of remeshing them, which only works if it never reuses a Model something else
 * still needs (a live GPU resource double-freed is not a recoverable bug) and never renders a
 * chunk differently than a full rebuild would have. Run with :editor:incrementalChunkRebuildSmokeTest.
 *
 * <p>Verifies, all against a real preview subprocess:
 * <ol>
 *   <li>An incremental update at a chunk-boundary cell renders pixel-identical to a full rebuild
 *       of the exact same edited map - the chunk mesher's byte-for-byte-shared code path, checked
 *       through the real rendering pipeline rather than trusted by inspection alone.</li>
 *   <li>Twenty consecutive incremental edits in a row never fail or drop the connection - the
 *       stress case for a reuse/dispose ordering bug, which would otherwise only show up after
 *       several edits pile up chunk models from different scene generations.</li>
 *   <li>Switching to an unrelated map and back afterward still renders correctly - the case that
 *       would catch a stale chunk-origin cache entry surviving a map switch it should not.</li>
 *   <li>An incremental request that also (incorrectly) implies a model catalog reuse is safe -
 *       dirtyCellXs/Zs set alongside a newly-registered prop model the reused catalog does not
 *       have - still succeeds, because PreviewApplication retries once as a full rebuild instead
 *       of trusting the editor's dirtyCellXs/Zs as a guarantee. Without that retry this would
 *       throw "Unknown model ID" and the whole preview would report failure.</li>
 * </ol>
 */
public final class IncrementalChunkRebuildSmokeTest {
    private static final int MAP_SIZE = 32;
    /** Column 15 is the last column of chunk (0,0) - editing it must also remesh chunk (16,0),
     * since ChunkMesher.appendSide reads column 16 as column 15's neighbour and vice versa. */
    private static final int BOUNDARY_X = 15;
    private static final int BOUNDARY_Z = 10;

    public static void main(String[] args) throws Exception {
        String previewClasspath = System.getProperty("trackside.editor.previewClasspath");
        if (previewClasspath == null) {
            throw new IllegalStateException("Run via the incrementalChunkRebuildSmokeTest Gradle task");
        }
        if (args.length != 1) throw new IllegalStateException("Usage: IncrementalChunkRebuildSmokeTest <path-to-sample.gltf>");
        String sampleGltfPath = args[0];

        Path mapDirectory = Files.createTempDirectory("incremental-chunk-rebuild-smoke-test");
        writeTileset(mapDirectory);
        Path mapFile = mapDirectory.resolve("valley.json").toAbsolutePath();
        Path otherMapFile = mapDirectory.resolve("outpost.json").toAbsolutePath();
        writeFlatMap("outpost", 4, 4, mapDirectory);

        CountDownLatch connected = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        PreviewProcess.StatusListener statusListener = (status, detail) -> {
            if (status == PreviewProcess.Status.CONNECTED) connected.countDown();
            else if (status != PreviewProcess.Status.STARTING) disconnected.set(true);
        };
        PreviewProcess process = new PreviewProcess(previewClasspath, statusListener, (x, y, z) -> { }, entry -> { });
        process.start();
        try {
            if (!connected.await(20, TimeUnit.SECONDS)) throw new AssertionError("Preview never connected");
            disconnected.set(false);

            // 1) Baseline: flat map, shown in full.
            writeMap(mapFile.getParent(), false);
            requireShowMap(process, mapFile, null, null, "baseline");

            // 2) The map edited at the chunk-boundary cell, shown in full - this is ground truth.
            writeMap(mapFile.getParent(), true);
            requireShowMap(process, mapFile, null, null, "full rebuild of edited map");
            Path expectedPng = mapDirectory.resolve("expected.png");
            requireScreenshot(process, expectedPng);

            // 3) Back to the flat baseline, in full - what a live-preview session already showed
            // before the stroke that's about to be replayed incrementally.
            writeMap(mapFile.getParent(), false);
            requireShowMap(process, mapFile, null, null, "reset to baseline");

            // 4) The same edit as step 2, but as an incremental update - only chunk (0,0) and its
            // boundary neighbour chunk (16,0) should be remeshed; every other chunk and every prop
            // instance is expected to be the exact objects step 3's scene already built.
            writeMap(mapFile.getParent(), true);
            requireShowMap(process, mapFile, new int[] {BOUNDARY_X}, new int[] {BOUNDARY_Z}, "incremental update");
            Path actualPng = mapDirectory.resolve("actual.png");
            requireScreenshot(process, actualPng);

            requirePixelIdentical(expectedPng, actualPng);
            System.out.println("PASS: incremental chunk update renders pixel-identical to a full rebuild "
                + "of the same edited map");

            // 5) Stress case: twenty more incremental edits in a row, each at a different cell
            // (some chunk-interior, some chunk-boundary), must never fail or disconnect - the
            // scenario where a reuse/dispose bug would accumulate across scene generations.
            for (int i = 0; i < 20; i++) {
                int x = 1 + (i * 7) % (MAP_SIZE - 2);
                int z = 1 + (i * 11) % (MAP_SIZE - 2);
                writeMap(mapFile.getParent(), x, z, 1f + (i % 3));
                requireShowMap(process, mapFile, new int[] {x}, new int[] {z}, "stress edit " + i);
                if (disconnected.get()) throw new AssertionError("Preview disconnected after stress edit " + i);
            }
            System.out.println("PASS: twenty consecutive incremental updates completed without a failure "
                + "or a dropped connection");

            // 6) Switching to an unrelated map, then back, must still show the right thing - the
            // case that would catch a stale chunk-origin cache entry surviving a map switch.
            requireShowMap(process, otherMapFile, null, null, "switch to unrelated map");
            requireShowMap(process, mapFile, null, null, "switch back");
            if (disconnected.get()) throw new AssertionError("Preview disconnected while switching maps");
            System.out.println("PASS: switching away from and back to the incrementally-updated map "
                + "still rendered correctly");

            // 7) Safety net: an incremental request whose dirtyCellXs/Zs wrongly implies "only
            // terrain changed", sent alongside a newly-registered prop model the currently reused
            // catalog does not have. A naive incremental attempt would throw "Unknown model ID";
            // PreviewApplication must retry once as a full rebuild and still succeed.
            ModelBoundsResult bounds = process.computeModelBounds(sampleGltfPath).get(20, TimeUnit.SECONDS);
            if (!bounds.success) throw new AssertionError("Bounds computation failed: " + bounds.errorMessage);
            Path modelDirectory = mapDirectory.resolve("models");
            Files.createDirectories(modelDirectory);
            Path sourceGltf = Path.of(sampleGltfPath);
            Path sourceBin = sourceGltf.resolveSibling("house.bin");
            Files.copy(sourceGltf, modelDirectory.resolve(sourceGltf.getFileName()),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(sourceBin, modelDirectory.resolve(sourceBin.getFileName()),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ModelManifestExport.write(List.of(new ModelManifestExport.Entry("house",
                "gltf:models/" + sourceGltf.getFileName(), 0f, 0f, 0f, 1f, bounds.maxY - bounds.minY,
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ,
                0, 0, 0, 0, false, false, 0f)), mapDirectory);
            writeMapWithProp(mapFile.getParent());
            ShowMapResult withNewModel = process.showMap(mapFile.toString(), MAP_SIZE, MAP_SIZE,
                mapFile.getParent().resolve("overworld.json").toString(),
                mapDirectory.resolve("models.json").toString(), null, null,
                new int[] {1}, new int[] {1}).get(20, TimeUnit.SECONDS);
            if (!withNewModel.success) {
                throw new AssertionError("Expected the retry-as-full-rebuild fallback to recover from a "
                    + "wrongly-incremental request, got: " + withNewModel.errorMessage);
            }
            if (disconnected.get()) throw new AssertionError("Preview disconnected on the recovered request");
            System.out.println("PASS: an incremental request implying an unsafe catalog reuse still "
                + "succeeded, via the automatic full-rebuild retry");
        } finally {
            process.stop();
        }
    }

    private static void requireShowMap(PreviewProcess process, Path mapFile, int[] dirtyXs, int[] dirtyZs,
                                       String step) throws Exception {
        ShowMapResult result = process.showMap(mapFile.toString(), MAP_SIZE, MAP_SIZE,
            mapFile.getParent().resolve("overworld.json").toString(), null, null, null, dirtyXs, dirtyZs)
            .get(20, TimeUnit.SECONDS);
        if (!result.success) throw new AssertionError("ShowMap failed at '" + step + "': " + result.errorMessage);
    }

    private static void requireScreenshot(PreviewProcess process, Path outputPath) throws Exception {
        CaptureScreenshotResult result = process.captureScreenshot(outputPath.toString()).get(20, TimeUnit.SECONDS);
        if (!result.success) throw new AssertionError("Screenshot failed: " + result.errorMessage);
        if (!Files.exists(outputPath)) throw new AssertionError("Screenshot file was not written: " + outputPath);
    }

    private static void requirePixelIdentical(Path expectedPath, Path actualPath) throws Exception {
        BufferedImage expected = ImageIO.read(expectedPath.toFile());
        BufferedImage actual = ImageIO.read(actualPath.toFile());
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            throw new AssertionError("Screenshot size mismatch: expected " + expected.getWidth() + "x"
                + expected.getHeight() + ", got " + actual.getWidth() + "x" + actual.getHeight());
        }
        int mismatches = 0;
        for (int y = 0; y < expected.getHeight() && mismatches < 5; y++) {
            for (int x = 0; x < expected.getWidth() && mismatches < 5; x++) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                    mismatches++;
                    System.out.println("Mismatch at (" + x + "," + y + "): expected "
                        + Integer.toHexString(expected.getRGB(x, y)) + ", got " + Integer.toHexString(actual.getRGB(x, y)));
                }
            }
        }
        if (mismatches > 0) {
            throw new AssertionError("Incremental update rendered differently from a full rebuild "
                + "of the same map - see logged mismatches above");
        }
    }

    private static void writeTileset(Path mapDirectory) throws Exception {
        BufferedImage tile = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = tile.getGraphics();
        try {
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, 16, 16);
        } finally {
            graphics.dispose();
        }
        TilesetExport.write(TilePacker.pack(List.of(new TileSource("grass", tile, true))),
            "overworld", "overworld.png", mapDirectory);
    }

    private static void writeFlatMap(String id, int width, int depth, Path mapDirectory) throws Exception {
        int cells = width * depth;
        String[] tiles = new String[cells];
        float[] heights = new float[cells];
        String[] shapes = new String[cells];
        boolean[] collision = new boolean[cells];
        for (int i = 0; i < cells; i++) {
            tiles[i] = "grass";
            shapes[i] = "flat";
        }
        MapExport.write(id, width, depth, "overworld", tiles, heights, shapes, collision,
            List.of(), List.of(), List.of(), List.of(), List.of(), mapDirectory);
    }

    /** Flat MAP_SIZE x MAP_SIZE map, with BOUNDARY_X/Z raised to height 1 when edited=true. */
    private static void writeMap(Path mapDirectory, boolean edited) throws Exception {
        writeMap(mapDirectory, edited ? BOUNDARY_X : -1, BOUNDARY_Z, 1f);
    }

    /** Flat MAP_SIZE x MAP_SIZE map, with cell (raisedX, raisedZ) raised to raisedHeight; a
     * negative raisedX means every cell stays flat at height 0. */
    private static void writeMap(Path mapDirectory, int raisedX, int raisedZ, float raisedHeight) throws Exception {
        int cells = MAP_SIZE * MAP_SIZE;
        String[] tiles = new String[cells];
        float[] heights = new float[cells];
        String[] shapes = new String[cells];
        boolean[] collision = new boolean[cells];
        for (int z = 0; z < MAP_SIZE; z++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int index = z * MAP_SIZE + x;
                tiles[index] = "grass";
                shapes[index] = "flat";
                heights[index] = (x == raisedX && z == raisedZ) ? raisedHeight : 0f;
            }
        }
        MapExport.write("valley", MAP_SIZE, MAP_SIZE, "overworld", tiles, heights, shapes, collision,
            List.of(), List.of(), List.of(), List.of(), List.of(), mapDirectory);
    }

    /** Same flat map as writeMap(dir, false), plus one "house" prop - for the retry-on-failure check. */
    private static void writeMapWithProp(Path mapDirectory) throws Exception {
        int cells = MAP_SIZE * MAP_SIZE;
        String[] tiles = new String[cells];
        float[] heights = new float[cells];
        String[] shapes = new String[cells];
        boolean[] collision = new boolean[cells];
        for (int i = 0; i < cells; i++) {
            tiles[i] = "grass";
            shapes[i] = "flat";
        }
        MapExport.write("valley", MAP_SIZE, MAP_SIZE, "overworld", tiles, heights, shapes, collision,
            List.of(new MapExport.Prop("house", 5f, 5f, 0f, 0f)),
            List.of(), List.of(), List.of(), List.of(), mapDirectory);
    }
}
