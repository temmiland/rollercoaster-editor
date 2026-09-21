package land.temmi.rollercoaster.editor.preview;

import land.temmi.rollercoaster.dialogue.Dialogue;
import land.temmi.rollercoaster.dialogue.DialogueNode;
import land.temmi.rollercoaster.dialogue.DialogueResponse;
import land.temmi.rollercoaster.event.Condition;
import land.temmi.rollercoaster.event.ConditionEvaluator;
import land.temmi.rollercoaster.event.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-plays a dialogue tree for testing: starting at the dialogue's start node, always follows
 * the first response whose conditions pass, until a node has no eligible response left. There is
 * no in-preview dialogue box to click through - the project has no translation catalog yet either
 * (see docs/plan.md), so text wouldn't render as anything but IDs regardless. This instead lets a
 * tester walk a branch deterministically by setting flags before triggering the dialogue.
 */
public final class DialoguePlayback {
    private DialoguePlayback() {
    }

    public static List<DialogueNode> play(Dialogue dialogue, GameState state) {
        List<DialogueNode> visited = new ArrayList<>();
        String currentNodeId = dialogue.startNodeId;
        int guard = 0;
        while (currentNodeId != null) {
            if (guard++ > dialogue.nodes.size) {
                throw new IllegalStateException("Dialogue '" + dialogue.id + "' looped without terminating");
            }
            DialogueNode node = dialogue.node(currentNodeId);
            visited.add(node);
            currentNodeId = firstEligibleTarget(node, state);
        }
        return visited;
    }

    private static String firstEligibleTarget(DialogueNode node, GameState state) {
        for (DialogueResponse response : node.responses) {
            boolean eligible = true;
            for (Condition condition : response.conditions) {
                if (!ConditionEvaluator.evaluate(condition, state)) {
                    eligible = false;
                    break;
                }
            }
            if (eligible) return response.targetNodeId;
        }
        return null;
    }
}
