package land.temmi.rollercoaster.editor.document;

/** Updates an entity type's property schema while retaining an entity-visible type ID. */
public final class UpdateEntityTypeCommand implements Command {
    private final EntityTypeAsset previous;
    private final EntityTypeAsset replacement;

    public UpdateEntityTypeCommand(EntityTypeAsset previous, EntityTypeAsset replacement) {
        if (previous == null || replacement == null || !previous.id.equals(replacement.id)) {
            throw new IllegalArgumentException("Entity type updates need matching IDs");
        }
        this.previous = previous;
        this.replacement = replacement;
    }

    @Override
    public void execute(ProjectDocument document) {
        document.replaceEntityType(previous.id, replacement);
    }

    @Override
    public void undo(ProjectDocument document) {
        document.replaceEntityType(replacement.id, previous);
    }
}
