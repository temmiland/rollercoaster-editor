package land.temmi.rollercoaster.editor.document;

/** Adds one point or spot light to a map. */
public final class PlaceLightCommand implements Command {
    private final String mapId;
    private final MapLightAsset light;

    public PlaceLightCommand(String mapId, MapLightAsset light) {
        this.mapId = mapId;
        this.light = light;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        map.requireLightPosition(light);
        map.addLight(light);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).removeLight(light.instanceId);
    }
}
