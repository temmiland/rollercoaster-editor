package land.temmi.rollercoaster.editor.protocol;

/**
 * Test-only hook: asks the preview to write its current frame to a PNG at the given path. Exists
 * so a smoke test can verify a rendering change by comparing real screenshots pixel-for-pixel,
 * instead of trusting the geometry code by reasoning alone - the editor's real UI has no use for
 * this and never sends it.
 */
public final class CaptureScreenshot {
    public String outputPath;

    public CaptureScreenshot() {
    }

    public CaptureScreenshot(String outputPath) {
        this.outputPath = outputPath;
    }
}
