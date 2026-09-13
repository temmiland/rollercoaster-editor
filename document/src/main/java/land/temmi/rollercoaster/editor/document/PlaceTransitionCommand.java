package land.temmi.rollercoaster.editor.document;

/** Adds one warp tile to a map. */
public final class PlaceTransitionCommand implements Command {
    private final String mapId;
    private final MapTransitionAsset transition;

    public PlaceTransitionCommand(String mapId, MapTransitionAsset transition) {
        this.mapId = mapId;
        this.transition = transition;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        if (document.findMap(transition.targetMapId) == null) {
            throw new IllegalArgumentException("Unknown target map '" + transition.targetMapId + "'");
        }
        map.requireTransitionPosition(transition);
        map.addTransition(transition);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).removeTransition(transition.instanceId);
    }
}
