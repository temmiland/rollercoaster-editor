package land.temmi.rollercoaster.editor.document;

/** Replaces an entity's type, sprite or tile while holding its stable instance ID. */
public final class UpdateEntityCommand implements Command {
    private final String mapId;
    private final MapEntityAsset previous;
    private final MapEntityAsset replacement;

    public UpdateEntityCommand(String mapId, MapEntityAsset previous, MapEntityAsset replacement) {
        if (previous == null || replacement == null || !previous.instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Entity updates need matching instance IDs");
        }
        this.mapId = mapId;
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        if (replacement.spriteId != null && document.findSprite(replacement.spriteId) == null) {
            throw new IllegalArgumentException("Entity '" + replacement.instanceId
                + "' references unknown sprite '" + replacement.spriteId + "'");
        }
        map.requireEntityPosition(replacement);
        map.replaceEntity(previous.instanceId, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).replaceEntity(replacement.instanceId, previous);
    }
}
