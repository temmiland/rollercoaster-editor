package land.temmi.rollercoaster.editor.document;

/** Replaces a transition's position or target while holding its stable instance ID. */
public final class UpdateTransitionCommand implements Command {
    private final String mapId;
    private final MapTransitionAsset previous;
    private final MapTransitionAsset replacement;

    public UpdateTransitionCommand(String mapId, MapTransitionAsset previous, MapTransitionAsset replacement) {
        if (previous == null || replacement == null || !previous.instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Transition updates need matching instance IDs");
        }
        this.mapId = mapId;
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        if (document.findMap(replacement.targetMapId) == null) {
            throw new IllegalArgumentException("Unknown target map '" + replacement.targetMapId + "'");
        }
        map.requireTransitionPosition(replacement);
        map.replaceTransition(previous.instanceId, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).replaceTransition(replacement.instanceId, previous);
    }
}
