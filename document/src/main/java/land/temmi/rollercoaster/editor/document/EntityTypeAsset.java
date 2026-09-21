package land.temmi.rollercoaster.editor.document;

import java.util.List;

/**
 * A registered entity type's property schema. Registering a type is optional: an entity whose
 * {@code type} matches no registered {@link EntityTypeAsset} still places fine with no properties,
 * exactly as before this existed - only a matching registration turns the type's free-text field
 * into a validated property form.
 */
public final class EntityTypeAsset {
    public final String id;
    private final List<EntityPropertyDefinition> properties;

    public EntityTypeAsset(String id, List<EntityPropertyDefinition> properties) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Entity type id is required");
        if (properties == null) throw new IllegalArgumentException("Entity type properties are required: " + id);
        for (int i = 0; i < properties.size(); i++) {
            for (int j = i + 1; j < properties.size(); j++) {
                if (properties.get(i).key.equals(properties.get(j).key)) {
                    throw new IllegalArgumentException(
                        "Entity type '" + id + "' has a duplicate property key: " + properties.get(i).key);
                }
            }
        }
        this.id = id;
        this.properties = List.copyOf(properties);
    }

    public List<EntityPropertyDefinition> getProperties() {
        return properties;
    }

    public EntityPropertyDefinition findProperty(String key) {
        for (EntityPropertyDefinition property : properties) {
            if (property.key.equals(key)) return property;
        }
        return null;
    }
}
