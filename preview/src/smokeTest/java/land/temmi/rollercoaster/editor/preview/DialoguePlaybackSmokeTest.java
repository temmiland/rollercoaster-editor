package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.utils.Array;
import land.temmi.rollercoaster.dialogue.Dialogue;
import land.temmi.rollercoaster.dialogue.DialogueNode;
import land.temmi.rollercoaster.dialogue.DialogueResponse;
import land.temmi.rollercoaster.event.Condition;
import land.temmi.rollercoaster.event.GameState;

import java.util.List;

/** Checks DialoguePlayback's branch-following against a real Dialogue tree - pure logic, no GL
 * context needed. */
public final class DialoguePlaybackSmokeTest {
    public static void main(String[] args) {
        verifyFollowsFirstEligibleResponse();
        verifyStopsAtADeadEnd();
        System.out.println("PASS: DialoguePlayback follows the first response whose conditions pass, "
            + "re-routes when a flag changes which response is eligible, and stops at a dead end");
    }

    private static void verifyFollowsFirstEligibleResponse() {
        Dialogue dialogue = threeNodeDialogue();

        GameState withoutKey = new GameState();
        List<DialogueNode> visitedWithoutKey = DialoguePlayback.play(dialogue, withoutKey);
        assertIds(visitedWithoutKey, "start", "no-key-end");

        GameState withKey = new GameState();
        withKey.setFlag("has-key", "true");
        List<DialogueNode> visitedWithKey = DialoguePlayback.play(dialogue, withKey);
        assertIds(visitedWithKey, "start", "has-key-end");
    }

    private static void verifyStopsAtADeadEnd() {
        Array<DialogueNode> nodes = new Array<>();
        nodes.add(new DialogueNode("only", "npc", "text-only", null, new Array<>()));
        Dialogue dialogue = new Dialogue("dead-end", "only", nodes);

        List<DialogueNode> visited = DialoguePlayback.play(dialogue, new GameState());
        assertIds(visited, "only");
    }

    private static Dialogue threeNodeDialogue() {
        Array<Condition> requiresKey = new Array<>();
        requiresKey.add(new Condition(Condition.Type.FLAG, "has-key", "true"));

        Array<DialogueResponse> startResponses = new Array<>();
        startResponses.add(new DialogueResponse("r-key", "has-key-end", requiresKey));
        startResponses.add(new DialogueResponse("r-no-key", "no-key-end", new Array<>()));

        Array<DialogueNode> nodes = new Array<>();
        nodes.add(new DialogueNode("start", "npc", "text-start", null, startResponses));
        nodes.add(new DialogueNode("has-key-end", "npc", "text-has-key", null, new Array<>()));
        nodes.add(new DialogueNode("no-key-end", "npc", "text-no-key", null, new Array<>()));
        return new Dialogue("d1", "start", nodes);
    }

    private static void assertIds(List<DialogueNode> visited, String... expectedIds) {
        if (visited.size() != expectedIds.length) {
            throw new AssertionError("Expected " + expectedIds.length + " nodes, got " + visited.size() + ": " + visited);
        }
        for (int i = 0; i < expectedIds.length; i++) {
            if (!visited.get(i).id.equals(expectedIds[i])) {
                throw new AssertionError("Expected node " + i + " to be '" + expectedIds[i]
                    + "', got '" + visited.get(i).id + "'");
            }
        }
    }
}
