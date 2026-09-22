package land.temmi.rollercoaster.editor.ui;

import java.awt.Rectangle;
import java.util.prefs.Preferences;

/** Window bounds and split/tab layout, persisted per OS user account so a small monitor doesn't
 * need re-arranging on every launch. */
final class EditorWindowState {
    private static final String WINDOW_SET_KEY = "window.set";

    private final Preferences preferences;

    EditorWindowState() {
        this(Preferences.userNodeForPackage(EditorWindowState.class));
    }

    EditorWindowState(Preferences preferences) {
        this.preferences = preferences;
    }

    /** Null when nothing was saved yet - the caller picks its own first-run default and centers it. */
    Rectangle getWindowBounds() {
        if (!preferences.getBoolean(WINDOW_SET_KEY, false)) return null;
        return new Rectangle(
            preferences.getInt("window.x", 0), preferences.getInt("window.y", 0),
            preferences.getInt("window.width", 1100), preferences.getInt("window.height", 720));
    }

    void putWindowBounds(Rectangle bounds) {
        preferences.putBoolean(WINDOW_SET_KEY, true);
        preferences.putInt("window.x", bounds.x);
        preferences.putInt("window.y", bounds.y);
        preferences.putInt("window.width", bounds.width);
        preferences.putInt("window.height", bounds.height);
    }

    int getExtendedState() {
        return preferences.getInt("window.extendedState", 0);
    }

    void putExtendedState(int extendedState) {
        preferences.putInt("window.extendedState", extendedState);
    }

    int getDividerLocation(String key, int fallback) {
        return preferences.getInt("divider." + key, fallback);
    }

    void putDividerLocation(String key, int location) {
        preferences.putInt("divider." + key, location);
    }

    int getSelectedTab(String key, int fallback) {
        return preferences.getInt("tab." + key, fallback);
    }

    void putSelectedTab(String key, int index) {
        preferences.putInt("tab." + key, index);
    }

    boolean getFlag(String key, boolean fallback) {
        return preferences.getBoolean("flag." + key, fallback);
    }

    void putFlag(String key, boolean value) {
        preferences.putBoolean("flag." + key, value);
    }
}
