package land.temmi.rollercoaster.editor.ui;

import javax.swing.SwingUtilities;

public final class EditorApplication {
    private EditorApplication() {
    }

    public static void launch(String previewClasspath) {
        SwingUtilities.invokeLater(() -> {
            PreviewProcess[] previewProcess = new PreviewProcess[1];
            EditorFrame frame = new EditorFrame(() -> previewProcess[0].restart());
            previewProcess[0] = new PreviewProcess(previewClasspath, frame::onPreviewStatusChanged);
            frame.setVisible(true);
            previewProcess[0].start();
        });
    }
}
