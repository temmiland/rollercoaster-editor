package land.temmi.rollercoaster.editor.document;

/** What starts a placed event, mirroring the engine's {@code EventTrigger} field-for-field. */
public final class EventTriggerAsset {
    public enum Type { MAP_START, INTERACTION, ENTER_AREA, TIME_CHANGE }

    public final Type type;
    public final String entityId;
    public final int x;
    public final int z;
    public final String timeOfDay;

    public static EventTriggerAsset mapStart() {
        return new EventTriggerAsset(Type.MAP_START, null, 0, 0, null);
    }

    public static EventTriggerAsset interaction(String entityId) {
        return new EventTriggerAsset(Type.INTERACTION, entityId, 0, 0, null);
    }

    public static EventTriggerAsset enterArea(int x, int z) {
        return new EventTriggerAsset(Type.ENTER_AREA, null, x, z, null);
    }

    public static EventTriggerAsset timeChange(String timeOfDay) {
        return new EventTriggerAsset(Type.TIME_CHANGE, null, 0, 0, timeOfDay);
    }

    public EventTriggerAsset(Type type, String entityId, int x, int z, String timeOfDay) {
        if (type == null) throw new IllegalArgumentException("Trigger type is required");
        if (type == Type.INTERACTION && (entityId == null || entityId.trim().isEmpty())) {
            throw new IllegalArgumentException("INTERACTION trigger requires an entity id");
        }
        if (type == Type.TIME_CHANGE && (timeOfDay == null || timeOfDay.trim().isEmpty())) {
            throw new IllegalArgumentException("TIME_CHANGE trigger requires a time of day");
        }
        this.type = type;
        this.entityId = entityId;
        this.x = x;
        this.z = z;
        this.timeOfDay = timeOfDay;
    }
}
