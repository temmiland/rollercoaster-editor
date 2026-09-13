package land.temmi.rollercoaster.editor.document;

/** The caller supplies the event (from {@link MapAsset#findEvent}) so undo restores it exactly. */
public final class RemoveEventCommand implements Command {
    private final String mapId;
    private final GameEventAsset event;

    public RemoveEventCommand(String mapId, GameEventAsset event) {
        this.mapId = mapId;
        this.event = event;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireMap(mapId).removeEvent(event.instanceId);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).addEvent(event);
    }
}
