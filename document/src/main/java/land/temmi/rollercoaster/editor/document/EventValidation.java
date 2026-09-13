package land.temmi.rollercoaster.editor.document;

/**
 * Cross-reference checks shared by {@link PlaceEventCommand} and {@link UpdateEventCommand}. An
 * event's trigger and actions can each point at a different registry (entities and lights on the
 * same map, dialogues and other maps at the project level), so this is more than the one-field
 * bounds check the other placement commands inline directly.
 */
final class EventValidation {
    private EventValidation() { }

    static void requireReferences(ProjectDocument document, MapAsset map, GameEventAsset event) {
        EventTriggerAsset trigger = event.trigger;
        if (trigger.type == EventTriggerAsset.Type.INTERACTION && map.findEntity(trigger.entityId) == null) {
            throw new IllegalArgumentException(
                "Event '" + event.instanceId + "' trigger references unknown entity '" + trigger.entityId + "'");
        }
        for (EventActionAsset action : event.getActions()) {
            switch (action.type) {
                case START_DIALOGUE:
                    if (document.findDialogue(action.targetId) == null) {
                        throw new IllegalArgumentException(
                            "Event '" + event.instanceId + "' action references unknown dialogue '" + action.targetId + "'");
                    }
                    break;
                case MOVE_NPC:
                case OPEN_DOOR:
                    if (map.findEntity(action.targetId) == null) {
                        throw new IllegalArgumentException(
                            "Event '" + event.instanceId + "' action references unknown entity '" + action.targetId + "'");
                    }
                    break;
                case TOGGLE_LIGHT:
                    if (map.findLight(action.targetId) == null) {
                        throw new IllegalArgumentException(
                            "Event '" + event.instanceId + "' action references unknown light '" + action.targetId + "'");
                    }
                    break;
                case CHANGE_MAP:
                    if (document.findMap(action.targetMap) == null) {
                        throw new IllegalArgumentException(
                            "Event '" + event.instanceId + "' action references unknown map '" + action.targetMap + "'");
                    }
                    break;
                case SET_FLAG:
                    break;
            }
        }
    }
}
