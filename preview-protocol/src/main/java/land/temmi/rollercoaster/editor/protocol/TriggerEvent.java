package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to run one placed event's conditions and actions right now, regardless
 * of its authored trigger - a manual test hook, not a simulation of the trigger actually
 * occurring. Fire-and-forget; the preview reports what happened via EventLogEntry messages.
 */
public final class TriggerEvent {
    public String eventInstanceId;

    public TriggerEvent() {
    }

    public TriggerEvent(String eventInstanceId) {
        this.eventInstanceId = eventInstanceId;
    }
}
