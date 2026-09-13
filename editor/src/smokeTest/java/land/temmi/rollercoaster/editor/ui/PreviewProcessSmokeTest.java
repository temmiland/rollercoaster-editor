package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.asset.MapExport;
import land.temmi.rollercoaster.editor.asset.ModelManifestExport;
import land.temmi.rollercoaster.editor.asset.TilePacker;
import land.temmi.rollercoaster.editor.asset.TileSource;
import land.temmi.rollercoaster.editor.asset.TilesetExport;
import land.temmi.rollercoaster.editor.asset.SpriteAtlasExport;
import land.temmi.rollercoaster.editor.asset.SpriteFrameSource;
import land.temmi.rollercoaster.editor.asset.SpriteManifestExport;
import land.temmi.rollercoaster.editor.asset.SpritePacker;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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
            Path sourceGltf = Path.of(sampleGltfPath);
            Path sourceBin = sourceGltf.resolveSibling("house.bin");
            Path modelDirectory = mapDirectory.resolve("models");
            Files.createDirectories(modelDirectory);
            Files.copy(sourceGltf, modelDirectory.resolve(sourceGltf.getFileName()));
            Files.copy(sourceBin, modelDirectory.resolve(sourceBin.getFileName()));
            ModelManifestExport.write(List.of(new ModelManifestExport.Entry("house",
                "gltf:models/" + sourceGltf.getFileName(), 0f, 0f, 0f, 1f, result.maxY - result.minY,
                result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ,
                0, 0, 0, 0, false, false, 0f)), mapDirectory);
            MapExport.write("valley", 2, 2, "overworld",
                new String[] {"grass", "grass", "grass", "grass"}, new float[] {0f, 0f, 0f, 0f},
                new String[] {"flat", "flat", "flat", "flat"}, new boolean[] {false, false, false, false},
                java.util.List.of(new MapExport.Prop("house", 1f, 1f, 0f, 0f)),
                java.util.List.of(new MapExport.Entity("npc-1", "npc", "npc", 0, 0)),
                java.util.List.of(new MapExport.Light("lamp-1", 1f, 1.5f, 1f, 1f, 0.9f, 0.7f, 1f, 4f, true)),
                mapDirectory);
            BufferedImage tile = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics graphics = tile.getGraphics();
            try {
                graphics.setColor(Color.GREEN);
                graphics.fillRect(0, 0, 16, 16);
            } finally {
                graphics.dispose();
            }
            TilesetExport.write(TilePacker.pack(java.util.List.of(new TileSource("grass", tile, true))),
                "overworld", "overworld.png", mapDirectory);
            SpritePacker.PackedSpriteAtlas spriteAtlas = SpritePacker.pack(
                List.of(new SpriteFrameSource("npc-frame-0", tile)));
            SpriteAtlasExport.write(spriteAtlas, "sprites.atlas", "sprites.png", mapDirectory);
            Map<String, SpriteManifestExport.Direction> spriteDirections = new LinkedHashMap<>();
            for (String direction : List.of("north", "east", "south", "west")) {
                spriteDirections.put(direction, new SpriteManifestExport.Direction("npc-frame-0", List.of("npc-frame-0")));
            }
            SpriteManifestExport.write(List.of(new SpriteManifestExport.Entry("npc", 1f, 0.1f, 0f, spriteDirections)),
                "sprites.atlas", mapDirectory);
            String mapFilePath = mapDirectory.resolve("valley.json").toAbsolutePath().toString();

            CompletableFuture<ShowMapResult> showMapFuture = process.showMap(mapFilePath, 2, 2,
                mapDirectory.resolve("overworld.json").toAbsolutePath().toString(),
                mapDirectory.resolve("models.json").toAbsolutePath().toString(),
                mapDirectory.resolve("sprites.json").toAbsolutePath().toString());
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
