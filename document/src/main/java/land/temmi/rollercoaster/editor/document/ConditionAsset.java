package land.temmi.rollercoaster.editor.document;

/** A check against game state, mirroring the engine's {@code Condition} field-for-field. */
public final class ConditionAsset {
    public enum Type { FLAG, VARIABLE, TIME_OF_DAY }

    public enum Comparison { EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL }

    public final Type type;
    public final String key;
    public final Comparison comparison;
    public final String value;

    public ConditionAsset(Type type, String key, Comparison comparison, String value) {
        if (type == null) throw new IllegalArgumentException("Condition type is required");
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Condition key is required");
        if (comparison == null) throw new IllegalArgumentException("Condition comparison is required: " + key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Condition value is required: " + key);
        this.type = type;
        this.key = key;
        this.comparison = comparison;
        this.value = value;
    }
}
