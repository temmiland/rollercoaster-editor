package land.temmi.rollercoaster.editor.document;

import java.util.Collections;
import java.util.List;

/** A placed event, with a stable instance id for editor selection and undo. */
public final class GameEventAsset {
    public final String instanceId;
    public final EventTriggerAsset trigger;
    private final List<ConditionAsset> conditions;
    private final List<EventActionAsset> actions;

    public GameEventAsset(String instanceId, EventTriggerAsset trigger, List<ConditionAsset> conditions,
                          List<EventActionAsset> actions) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Event instance id is required");
        }
        if (trigger == null) throw new IllegalArgumentException("Event trigger is required: " + instanceId);
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("Event must have at least one action: " + instanceId);
        }
        this.instanceId = instanceId;
        this.trigger = trigger;
        this.conditions = conditions == null ? Collections.emptyList() : List.copyOf(conditions);
        this.actions = List.copyOf(actions);
    }

    public List<ConditionAsset> getConditions() {
        return conditions;
    }

    public List<EventActionAsset> getActions() {
        return actions;
    }
}
