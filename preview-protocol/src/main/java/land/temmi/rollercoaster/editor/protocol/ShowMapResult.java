package land.temmi.rollercoaster.editor.protocol;

/** Reply to {@link ShowMap}. A failed load carries a human-readable reason. */
public final class ShowMapResult {
    public boolean success;
    public String errorMessage;

    public ShowMapResult() {
    }

    public static ShowMapResult ok() {
        ShowMapResult result = new ShowMapResult();
        result.success = true;
        return result;
    }

    public static ShowMapResult ofError(String errorMessage) {
        ShowMapResult result = new ShowMapResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
