package land.temmi.rollercoaster.editor.document;

/** The caller supplies the transition (from {@link MapAsset#findTransition}) so undo restores it exactly. */
public final class RemoveTransitionCommand implements Command {
    private final String mapId;
    private final MapTransitionAsset transition;

    public RemoveTransitionCommand(String mapId, MapTransitionAsset transition) {
        this.mapId = mapId;
        this.transition = transition;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireMap(mapId).removeTransition(transition.instanceId);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).addTransition(transition);
    }
}
