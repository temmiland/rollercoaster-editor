package land.temmi.rollercoaster.editor.document;

/** Moves and/or rotates an existing prop in place, keeping its instance id stable. */
public final class TransformPropCommand implements Command {
    private final String mapId;
    private final String instanceId;
    private final String modelId;
    private final float previousX;
    private final float previousZ;
    private final float previousElevation;
    private final float previousRotation;
    private final float newX;
    private final float newZ;
    private final float newElevation;
    private final float newRotation;

    public TransformPropCommand(String mapId, String instanceId, String modelId,
                                float previousX, float previousZ, float previousElevation, float previousRotation,
                                float newX, float newZ, float newElevation, float newRotation) {
        this.mapId = mapId;
        this.instanceId = instanceId;
        this.modelId = modelId;
        this.previousX = previousX;
        this.previousZ = previousZ;
        this.previousElevation = previousElevation;
        this.previousRotation = previousRotation;
        this.newX = newX;
        this.newZ = newZ;
        this.newElevation = newElevation;
        this.newRotation = newRotation;
    }

    @Override
    public void execute(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        map.removeProp(instanceId);
        map.addProp(new MapProp(instanceId, modelId, newX, newZ, newElevation, newRotation));
    }

    @Override
    public void undo(ProjectDocument document) {
        MapAsset map = document.requireMap(mapId);
        map.removeProp(instanceId);
        map.addProp(new MapProp(instanceId, modelId, previousX, previousZ, previousElevation, previousRotation));
    }
}
