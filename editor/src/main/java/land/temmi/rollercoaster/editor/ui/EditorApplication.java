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

            frame[0] = new EditorFrame(() -> previewProcess[0].restart(), projectController[0], recentProjects,
                path -> previewProcess[0].computeModelBounds(path),
                (mapFilePath, width, depth, tilesetManifestFilePath, modelManifestFilePath, spriteManifestFilePath,
                 dialogueManifestFilePath) ->
                    previewProcess[0].showMap(mapFilePath, width, depth, tilesetManifestFilePath,
                        modelManifestFilePath, spriteManifestFilePath, dialogueManifestFilePath),
                mode -> previewProcess[0].setCameraMode(mode),
                enabled -> previewProcess[0].setTestMode(enabled),
                eventInstanceId -> previewProcess[0].triggerEvent(eventInstanceId),
                () -> previewProcess[0].resetFlags(),
                hours -> previewProcess[0].setTimeOfDay(hours));
            previewProcess[0] = new PreviewProcess(previewClasspath, frame[0]::onPreviewStatusChanged, frame[0]::onPick,
                frame[0]::onEventLog);

            frame[0].setVisible(true);
            frame[0].refreshProjectUi();
            previewProcess[0].start();
        });
    }
}
