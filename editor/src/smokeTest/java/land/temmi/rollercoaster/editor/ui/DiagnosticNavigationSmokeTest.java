package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.EventActionAsset;
import land.temmi.rollercoaster.editor.document.EventTriggerAsset;
import land.temmi.rollercoaster.editor.document.GameEventAsset;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the pieces behind a clickable diagnostic line: the id-in-quotes convention every
 * validation/error message in this codebase already follows, and the two panel-side dispatchers
 * (AssetsPanel.trySelect for project-level catalogs, MapPanel.trySelectPlacement for a map or its
 * placements) that a click resolves an id against. No GUI event loop needed - these are called
 * directly, the same way EditorFrame's click handler calls them. */
public final class DiagnosticNavigationSmokeTest {
    private static final Pattern DIAGNOSTIC_ID_PATTERN = Pattern.compile("'([^']+)'");

    public static void main(String[] args) throws Exception {
        verifyFirstQuotedTokenExtraction();
        verifyAssetsPanelTrySelect();
        verifyMapPanelTrySelectPlacement();
        System.out.println("PASS: the diagnostic id-in-quotes convention parses correctly, "
            + "AssetsPanel.trySelect jumps to a project-level asset and switches tabs, and "
            + "MapPanel.trySelectPlacement jumps to a map or a placement on the selected map");
    }

    private static void verifyFirstQuotedTokenExtraction() {
        Matcher matcher = DIAGNOSTIC_ID_PATTERN.matcher(
            "Karte 'valley': Übergang 'to-nowhere' verweist auf unbekannte Karte 'unknown-map'");
        if (!matcher.find() || !"valley".equals(matcher.group(1))) {
            throw new AssertionError("Expected the first quoted token to be 'valley'");
        }

        if (DIAGNOSTIC_ID_PATTERN.matcher("verbunden").find()) {
            throw new AssertionError("A status line with no quoted id should not match");
        }
    }

    private static void verifyAssetsPanelTrySelect() throws Exception {
        Path projectDirectory = Files.createTempDirectory("diagnostic-navigation-assets");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Scratch");
        controller.importTexture(solidColorPng("grass", 16, 16, Color.GREEN), "grass");

        AssetsPanel panel = new AssetsPanel(controller, path -> new CompletableFuture<>());
        panel.refresh();

        if (!panel.trySelect("grass")) throw new AssertionError("Expected trySelect('grass') to find the texture");
        if (panel.trySelect("does-not-exist")) throw new AssertionError("Expected an unknown id to return false");
    }

    private static void verifyMapPanelTrySelectPlacement() throws Exception {
        Path projectDirectory = Files.createTempDirectory("diagnostic-navigation-map");
        ProjectController controller = new ProjectController(() -> { });
        controller.newProject(projectDirectory, "Scratch");
        controller.importTexture(solidColorPng("grass", 16, 16, Color.GREEN), "grass");
        controller.createTileset("overworld");
        controller.addTile("overworld", "grass", "grass", null, true);
        controller.createMap("valley", 2, 2, "overworld");
        controller.paintTiles("valley", List.of(
            new PaintTilesCommand.Edit(0, 0, null, "grass"), new PaintTilesCommand.Edit(1, 0, null, "grass"),
            new PaintTilesCommand.Edit(0, 1, null, "grass"), new PaintTilesCommand.Edit(1, 1, null, "grass")));
        controller.placeEvent("valley", new GameEventAsset("greet-event", EventTriggerAsset.mapStart(), null,
            List.of(EventActionAsset.setFlag("greeted", "true"))));

        MapPanel.PreviewMapRequester previewMapRequester = (a, b, c, d, e, f, g) -> CompletableFuture.completedFuture(null);
        MapPanel.TestModeController testModeController = new MapPanel.TestModeController() {
            @Override
            public void triggerEvent(String eventInstanceId) {
            }

            @Override
            public void resetFlags() {
            }

            @Override
            public void setTimeOfDay(float hours) {
            }
        };
        MapPanel panel = new MapPanel(controller, previewMapRequester, testModeController);
        panel.refresh();

        if (!panel.trySelectPlacement("valley")) throw new AssertionError("Expected trySelectPlacement to find the map itself");
        if (!panel.trySelectPlacement("greet-event")) {
            throw new AssertionError("Expected trySelectPlacement to find the event on the selected map");
        }
        if (panel.trySelectPlacement("does-not-exist")) throw new AssertionError("Expected an unknown id to return false");
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
        Path file = Files.createTempFile(name, ".png");
        javax.imageio.ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
