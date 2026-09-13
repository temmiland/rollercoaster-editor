package land.temmi.rollercoaster.editor.document;

/** The caller supplies the prop (from {@link MapAsset#findProp}) so undo can restore it exactly. */
public final class RemovePropCommand implements Command {
    private final String mapId;
    private final MapProp prop;

    public RemovePropCommand(String mapId, MapProp prop) {
        this.mapId = mapId;
        this.prop = prop;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.requireMap(mapId).removeProp(prop.instanceId);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.requireMap(mapId).addProp(prop);
    }
}
