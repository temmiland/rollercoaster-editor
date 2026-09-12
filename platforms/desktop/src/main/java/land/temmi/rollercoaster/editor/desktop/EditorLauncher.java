package land.temmi.rollercoaster.editor.desktop;

import land.temmi.rollercoaster.editor.ui.EditorApplication;

public final class EditorLauncher {
    private EditorLauncher() {
    }

    public static void main(String[] args) {
        String previewClasspath = System.getProperty("trackside.editor.previewClasspath");
        if (previewClasspath == null) {
            throw new IllegalStateException(
                "trackside.editor.previewClasspath is not set; run via ./gradlew :platforms:desktop:run");
        }
        EditorApplication.launch(previewClasspath);
    }
}
