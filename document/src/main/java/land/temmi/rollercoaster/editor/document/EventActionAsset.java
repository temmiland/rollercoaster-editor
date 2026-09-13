package land.temmi.rollercoaster.editor.document;

/** A single effect a placed event can cause, mirroring the engine's {@code Action} field-for-field. */
public final class EventActionAsset {
    public enum Type { START_DIALOGUE, MOVE_NPC, OPEN_DOOR, CHANGE_MAP, SET_FLAG, TOGGLE_LIGHT }

    public final Type type;
    public final String targetId;
    public final String value;
    public final int x;
    public final int z;
    public final String targetMap;

    public static EventActionAsset startDialogue(String dialogueId) {
        return new EventActionAsset(Type.START_DIALOGUE, dialogueId, null, 0, 0, null);
    }

    public static EventActionAsset moveNpc(String entityId, int x, int z) {
        return new EventActionAsset(Type.MOVE_NPC, entityId, null, x, z, null);
    }

    public static EventActionAsset openDoor(String entityId, boolean open) {
        return new EventActionAsset(Type.OPEN_DOOR, entityId, Boolean.toString(open), 0, 0, null);
    }

    public static EventActionAsset changeMap(String targetMap, int x, int z) {
        return new EventActionAsset(Type.CHANGE_MAP, null, null, x, z, targetMap);
    }

    public static EventActionAsset setFlag(String key, String value) {
        return new EventActionAsset(Type.SET_FLAG, key, value, 0, 0, null);
    }

    public static EventActionAsset toggleLight(String lightId, boolean enabled) {
        return new EventActionAsset(Type.TOGGLE_LIGHT, lightId, Boolean.toString(enabled), 0, 0, null);
    }

    public EventActionAsset(Type type, String targetId, String value, int x, int z, String targetMap) {
        if (type == null) throw new IllegalArgumentException("Action type is required");
        if (type == Type.CHANGE_MAP) {
            if (targetMap == null || targetMap.trim().isEmpty()) {
                throw new IllegalArgumentException("CHANGE_MAP action requires a target map");
            }
        } else if (targetId == null || targetId.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " action requires a target id");
        }
        this.type = type;
        this.targetId = targetId;
        this.value = value;
        this.x = x;
        this.z = z;
        this.targetMap = targetMap;
    }
}
