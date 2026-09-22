package land.temmi.rollercoaster.editor.protocol;

/** Response to {@link CaptureScreenshot}. */
public final class CaptureScreenshotResult {
    public boolean success;
    public String errorMessage;

    public CaptureScreenshotResult() {
    }

    private CaptureScreenshotResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static CaptureScreenshotResult ok() {
        return new CaptureScreenshotResult(true, null);
    }

    public static CaptureScreenshotResult ofError(String message) {
        return new CaptureScreenshotResult(false, message);
    }
}
