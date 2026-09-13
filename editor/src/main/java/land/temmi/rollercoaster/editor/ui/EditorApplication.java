package land.temmi.rollercoaster.editor.ui;

import javax.swing.SwingUtilities;

public final class EditorApplication {
    private EditorApplication() {
    }

    public static void launch(String previewClasspath) {
        SwingUtilities.invokeLater(() -> {
            PreviewProcess[] previewProcess = new PreviewProcess[1];
            ProjectController[] projectController = new ProjectController[1];
            EditorFrame[] frame = new EditorFrame[1];
            RecentProjects recentProjects = new RecentProjects();

            projectController[0] = new ProjectController(() -> {
                frame[0].refreshProjectUi();
                previewProcess[0].setLevelOpen(projectController[0].isOpen());
            });

            frame[0] = new EditorFrame(() -> previewProcess[0].restart(), projectController[0], recentProjects);
            previewProcess[0] = new PreviewProcess(previewClasspath, frame[0]::onPreviewStatusChanged);

            frame[0].setVisible(true);
            frame[0].refreshProjectUi();
            previewProcess[0].start();
        });
    }
}
