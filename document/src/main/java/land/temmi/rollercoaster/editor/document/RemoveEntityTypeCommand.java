package land.temmi.rollercoaster.editor.document;

/** Removes an entity type registration while retaining it for undo. */
public final class RemoveEntityTypeCommand implements Command {
    private final EntityTypeAsset entityType;

    public RemoveEntityTypeCommand(EntityTypeAsset entityType) {
        this.entityType = entityType;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.removeEntityType(entityType.id);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.addEntityType(entityType);
    }
}
