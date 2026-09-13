package land.temmi.rollercoaster.editor.document;

/** The caller supplies the light (from {@link MapAsset#findLight}) so undo can restore it exactly. */
public final class RemoveLightCommand implements Command {
    private final String mapId;
    private final MapLightAsset light;

    public RemoveLightCommand(String mapId, MapLightAsset light) {
        this.mapId = mapId;
        this.light = light;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireMap(mapId).removeLight(light.instanceId);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).addLight(light);
    }
}
