package land.temmi.rollercoaster.editor.document;

/** Replaces a light's transform, color or parameters while holding its stable instance ID. */
public final class UpdateLightCommand implements Command {
    private final String mapId;
    private final MapLightAsset previous;
    private final MapLightAsset replacement;

    public UpdateLightCommand(String mapId, MapLightAsset previous, MapLightAsset replacement) {
        if (previous == null || replacement == null || !previous.instanceId.equals(replacement.instanceId)) {
            throw new IllegalArgumentException("Light updates need matching instance IDs");
        }
        this.mapId = mapId;
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        map.requireLightPosition(replacement);
        map.replaceLight(previous.instanceId, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).replaceLight(replacement.instanceId, previous);
    }
}
