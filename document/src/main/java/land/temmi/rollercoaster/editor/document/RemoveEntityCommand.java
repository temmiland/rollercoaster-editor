package land.temmi.rollercoaster.editor.document;

/** Removes one entity while retaining it for undo. */
public final class RemoveEntityCommand implements Command {
    private final String mapId;
    private final MapEntityAsset entity;

    public RemoveEntityCommand(String mapId, MapEntityAsset entity) {
        this.mapId = mapId;
        this.entity = entity;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireMap(mapId).removeEntity(entity.instanceId);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).addEntity(entity);
    }
}
