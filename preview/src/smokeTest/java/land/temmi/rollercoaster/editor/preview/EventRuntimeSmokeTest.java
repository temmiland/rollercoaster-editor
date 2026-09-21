package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.utils.Array;
import land.temmi.rollercoaster.event.Action;
import land.temmi.rollercoaster.event.Condition;
import land.temmi.rollercoaster.event.EventActionHandler;
import land.temmi.rollercoaster.event.EventDispatcher;
import land.temmi.rollercoaster.event.EventTrigger;
import land.temmi.rollercoaster.event.GameEvent;
import land.temmi.rollercoaster.event.GameState;

import java.util.ArrayList;
import java.util.List;

/** Checks GameState/ConditionEvaluator/EventDispatcher against real engine event data - pure
 * logic, so no GL context is needed. */
public final class EventRuntimeSmokeTest {
    public static void main(String[] args) {
        verifyUnconditionalEventRunsAllActions();
        verifyFailingConditionBlocksActions();
        verifySetFlagUpdatesStateForLaterConditions();
        verifyNumericComparisons();
        System.out.println("PASS: EventDispatcher gates on real Condition/GameState checks, SET_FLAG "
            + "actions land in GameState immediately, and every action type reaches the handler");
    }

    private static void verifyUnconditionalEventRunsAllActions() {
        GameState state = new GameState();
        Array<Action> actions = new Array<>();
        actions.add(Action.startDialogue("d1"));
        actions.add(Action.moveNpc("npc-1", 3, 4));
        actions.add(Action.openDoor("door-1", true));
        actions.add(Action.changeMap("cave", 1, 1));
        actions.add(Action.setFlag("met-npc", "true"));
        actions.add(Action.toggleLight("lamp-1", false));
        GameEvent event = new GameEvent("e1", EventTrigger.mapStart(), null, actions);

        List<String> calls = new ArrayList<>();
        boolean fired = EventDispatcher.fire(event, state, recordingHandler(calls));

        if (!fired) throw new AssertionError("Expected an unconditional event to fire");
        List<String> expected = List.of(
            "dialogue:d1", "moveNpc:npc-1,3,4", "door:door-1,true", "changeMap:cave,1,1",
            "flag:met-npc,true", "light:lamp-1,false");
        if (!calls.equals(expected)) throw new AssertionError("Expected " + expected + ", got " + calls);
        if (!"true".equals(state.getFlag("met-npc"))) {
            throw new AssertionError("Expected SET_FLAG to land in GameState, got " + state.getFlag("met-npc"));
        }
    }

    private static void verifyFailingConditionBlocksActions() {
        GameState state = new GameState();
        Array<Condition> conditions = new Array<>();
        conditions.add(new Condition(Condition.Type.FLAG, "has-key", "true"));
        Array<Action> actions = new Array<>();
        actions.add(Action.openDoor("door-1", true));
        GameEvent event = new GameEvent("e2", EventTrigger.interaction("door-1"), conditions, actions);

        List<String> calls = new ArrayList<>();
        boolean fired = EventDispatcher.fire(event, state, recordingHandler(calls));

        if (fired) throw new AssertionError("Expected a missing flag to block the event");
        if (!calls.isEmpty()) throw new AssertionError("Expected no actions to run, got " + calls);
    }

    private static void verifySetFlagUpdatesStateForLaterConditions() {
        GameState state = new GameState();
        Array<Action> grantKey = new Array<>();
        grantKey.add(Action.setFlag("has-key", "true"));
        EventDispatcher.fire(new GameEvent("grant", EventTrigger.mapStart(), null, grantKey), state,
            new EventActionHandler() { });

        Array<Condition> requiresKey = new Array<>();
        requiresKey.add(new Condition(Condition.Type.FLAG, "has-key", "true"));
        Array<Action> openDoor = new Array<>();
        openDoor.add(Action.openDoor("door-1", true));
        GameEvent unlock = new GameEvent("unlock", EventTrigger.interaction("door-1"), requiresKey, openDoor);

        List<String> calls = new ArrayList<>();
        boolean fired = EventDispatcher.fire(unlock, state, recordingHandler(calls));
        if (!fired) throw new AssertionError("Expected the flag granted by the first event to unlock the second");
        if (!calls.equals(List.of("door:door-1,true"))) throw new AssertionError("Unexpected calls: " + calls);
    }

    private static void verifyNumericComparisons() {
        GameState state = new GameState();
        state.setVariable("score", "7");
        Array<Condition> conditions = new Array<>();
        conditions.add(new Condition(Condition.Type.VARIABLE, "score", Condition.Comparison.GREATER_OR_EQUAL, "5"));
        Array<Action> actions = new Array<>();
        actions.add(Action.setFlag("passed", "true"));
        GameEvent event = new GameEvent("e3", EventTrigger.mapStart(), conditions, actions);

        boolean fired = EventDispatcher.fire(event, state, new EventActionHandler() { });
        if (!fired) throw new AssertionError("Expected 7 >= 5 to pass");

        state.setVariable("score", "3");
        boolean firedAgain = EventDispatcher.fire(event, state, new EventActionHandler() { });
        if (firedAgain) throw new AssertionError("Expected 3 >= 5 to fail");
    }

    private static EventActionHandler recordingHandler(List<String> calls) {
        return new EventActionHandler() {
            @Override
            public void onStartDialogue(String dialogueId) {
                calls.add("dialogue:" + dialogueId);
            }

            @Override
            public void onMoveNpc(String entityId, int x, int z) {
                calls.add("moveNpc:" + entityId + "," + x + "," + z);
            }

            @Override
            public void onOpenDoor(String entityId, boolean open) {
                calls.add("door:" + entityId + "," + open);
            }

            @Override
            public void onChangeMap(String targetMap, int x, int z) {
                calls.add("changeMap:" + targetMap + "," + x + "," + z);
            }

            @Override
            public void onSetFlag(String key, String value) {
                calls.add("flag:" + key + "," + value);
            }

            @Override
            public void onToggleLight(String lightId, boolean enabled) {
                calls.add("light:" + lightId + "," + enabled);
            }
        };
    }
}
