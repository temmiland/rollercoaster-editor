package land.temmi.rollercoaster.editor.document;

/** Adds one event to a map, checking every cross-reference its trigger and actions carry. */
public final class PlaceEventCommand implements Command {
    private final String mapId;
    private final GameEventAsset event;

    public PlaceEventCommand(String mapId, GameEventAsset event) {
        this.mapId = mapId;
        this.event = event;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        EventValidation.requireReferences(document, map, event);
        map.requireEventPosition(event);
        map.addEvent(event);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).removeEvent(event.instanceId);
    }
}
