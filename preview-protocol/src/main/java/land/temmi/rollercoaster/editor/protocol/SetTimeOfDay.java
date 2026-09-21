package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor sets the preview's day/night clock directly, in hours [0, 24) - the document has no
 * per-map time-of-day field (the engine picks a lighting preset from a fixed daily cycle, not
 * from map data), so this is the only way to pin down a specific lighting situation for a
 * reproducible test. Fire-and-forget, like SetCameraMode.
 */
public final class SetTimeOfDay {
    public float hours;

    public SetTimeOfDay() {
    }

    public SetTimeOfDay(float hours) {
        this.hours = hours;
    }
}
