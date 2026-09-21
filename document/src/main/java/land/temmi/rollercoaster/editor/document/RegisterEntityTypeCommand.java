package land.temmi.rollercoaster.editor.document;

/** Registers an entity type's property schema. */
public final class RegisterEntityTypeCommand implements Command {
    private final EntityTypeAsset entityType;

    public RegisterEntityTypeCommand(EntityTypeAsset entityType) {
        this.entityType = entityType;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.addEntityType(entityType);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.removeEntityType(entityType.id);
    }
}
