package land.temmi.rollercoaster.editor.document;

import java.util.Collections;
import java.util.List;

/** One answer a player can pick. A null target ends the dialogue; otherwise it jumps to another
 * node in the same dialogue. */
public final class DialogueResponseAsset {
    public final String textId;
    public final String targetNodeId;
    private final List<ConditionAsset> conditions;

    public DialogueResponseAsset(String textId, String targetNodeId, List<ConditionAsset> conditions) {
        if (textId == null || textId.trim().isEmpty()) throw new IllegalArgumentException("Response text ID is required");
        this.textId = textId;
        this.targetNodeId = targetNodeId;
        this.conditions = conditions == null ? Collections.emptyList() : List.copyOf(conditions);
    }

    public List<ConditionAsset> getConditions() {
        return conditions;
    }
}
