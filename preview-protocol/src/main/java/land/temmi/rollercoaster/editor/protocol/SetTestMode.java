package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor tells the preview whether the "player" entity on the current document map should be
 * live, keyboard-controlled and collision-checked - the same {@code GridActor}/{@code TerrainRules}
 * movement the real game uses - instead of sitting at its authored spawn point. Fire-and-forget,
 * like the scene-switch messages.
 */
public final class SetTestMode {
    public boolean enabled;

    public SetTestMode() {
    }

    public SetTestMode(boolean enabled) {
        this.enabled = enabled;
    }
}
