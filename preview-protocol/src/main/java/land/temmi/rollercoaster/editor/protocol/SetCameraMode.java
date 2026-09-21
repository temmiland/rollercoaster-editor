package land.temmi.rollercoaster.editor.protocol;

/** Editor tells the preview which camera to use. Fire-and-forget, like the scene-switch messages. */
public final class SetCameraMode {
    public CameraMode mode;

    public SetCameraMode() {
    }

    public SetCameraMode(CameraMode mode) {
        this.mode = mode;
    }
}
