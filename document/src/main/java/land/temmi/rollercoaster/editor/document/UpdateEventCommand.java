package land.temmi.rollercoaster.editor.document;

/** Replaces an event's trigger, conditions or actions while holding its stable instance ID. */
public final class UpdateEventCommand implements Command {
    private final String mapId;
    private final GameEventAsset previous;
    private final GameEventAsset replacement;

    public UpdateEventCommand(String mapId, GameEventAsset previous, GameEventAsset replacement) {
        if (previous == null || replacement == null || !previous.instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Event updates need matching instance IDs");
        }
        this.mapId = mapId;
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        EventValidation.requireReferences(document, map, replacement);
        map.requireEventPosition(replacement);
        map.replaceEvent(previous.instanceId, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).replaceEvent(replacement.instanceId, previous);
    }
}
