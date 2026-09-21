package land.temmi.rollercoaster.editor.document;

/** One property a registered entity type carries, and how the editor should validate its value. */
public final class EntityPropertyDefinition {
    public enum Type { TEXT, NUMBER, BOOLEAN, MAP_REFERENCE, DIALOGUE_REFERENCE, LIGHT_REFERENCE, ENTITY_REFERENCE }

    public final String key;
    public final Type type;
    public final boolean required;

    public EntityPropertyDefinition(String key, Type type, boolean required) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Property key is required");
        if (type == null) throw new IllegalArgumentException("Property type is required: " + key);
        this.key = key;
        this.type = type;
        this.required = required;
    }
}
