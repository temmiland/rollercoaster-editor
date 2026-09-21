package land.temmi.rollercoaster.editor.protocol;

/**
 * Preview reports back what a triggered event or dialogue actually did - the editor has no other
 * way to observe actions that only affect preview-internal state (a moved NPC, a toggled light, a
 * dialogue node visited). Fire-and-forget, like PickResult.
 */
public final class EventLogEntry {
    public String message;

    public EventLogEntry() {
    }

    public EventLogEntry(String message) {
        this.message = message;
    }
}
