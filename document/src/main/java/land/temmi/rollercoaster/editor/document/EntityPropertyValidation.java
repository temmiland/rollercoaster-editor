package land.temmi.rollercoaster.editor.document;

/**
 * Property checks shared by {@link PlaceEntityCommand} and {@link UpdateEntityCommand}. Only runs
 * when the entity's {@code type} matches a registered {@link EntityTypeAsset} - an unregistered
 * type carries no schema, so its properties (if any) are never checked, same as before schemas
 * existed at all.
 */
final class EntityPropertyValidation {
    private EntityPropertyValidation() { }

    static void requireValid(ProjectDocument document, MapAsset map, MapEntityAsset entity) {
        EntityTypeAsset schema = document.findEntityType(entity.type);
        if (schema == null) return;
        for (EntityPropertyDefinition definition : schema.getProperties()) {
            String value = entity.getProperties().get(definition.key);
            if (value == null || value.trim().isEmpty()) {
                if (definition.required) {
                    throw new IllegalArgumentException("Entity '" + entity.instanceId
                        + "' is missing required property '" + definition.key + "'");
                }
                continue;
            }
            switch (definition.type) {
                case NUMBER:
                    try {
                        Float.parseFloat(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' must be a number: " + value);
                    }
                    break;
                case BOOLEAN:
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' must be true or false: " + value);
                    }
                    break;
                case MAP_REFERENCE:
                    if (document.findMap(value) == null) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' references unknown map '" + value + "'");
                    }
                    break;
                case DIALOGUE_REFERENCE:
                    if (document.findDialogue(value) == null) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' references unknown dialogue '" + value + "'");
                    }
                    break;
                case LIGHT_REFERENCE:
                    if (map.findLight(value) == null) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' references unknown light '" + value + "'");
                    }
                    break;
                case ENTITY_REFERENCE:
                    if (map.findEntity(value) == null) {
                        throw new IllegalArgumentException("Entity '" + entity.instanceId + "' property '"
                            + definition.key + "' references unknown entity '" + value + "'");
                    }
                    break;
                case TEXT:
                    break;
            }
        }
    }
}
