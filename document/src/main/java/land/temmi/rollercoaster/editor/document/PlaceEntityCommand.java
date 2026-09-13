package land.temmi.rollercoaster.editor.document;

/** Adds one tile-anchored entity to a map. */
public final class PlaceEntityCommand implements Command {
    private final String mapId;
    private final MapEntityAsset entity;

    public PlaceEntityCommand(String mapId, MapEntityAsset entity) {
        this.mapId = mapId;
        this.entity = entity;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        if (entity.spriteId != null && document.findSprite(entity.spriteId) == null) {
            throw new IllegalArgumentException("Entity '" + entity.instanceId + "' references unknown sprite '" + entity.spriteId + "'");
        }
        map.requireEntityPosition(entity);
        map.addEntity(entity);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).removeEntity(entity.instanceId);
    }
}
