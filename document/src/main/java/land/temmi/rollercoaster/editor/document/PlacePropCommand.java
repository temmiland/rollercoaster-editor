package land.temmi.rollercoaster.editor.document;

public final class PlacePropCommand implements Command {
    private final String mapId;
    private final MapProp prop;

    public PlacePropCommand(String mapId, MapProp prop) {
        this.mapId = mapId;
        this.prop = prop;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        if (document.findModel(prop.modelId) == null) {
            throw new IllegalArgumentException("Unknown model '" + prop.modelId + "'");
        }
        map.requirePropPosition(prop);
        map.addProp(prop);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).removeProp(prop.instanceId);
    }
}
