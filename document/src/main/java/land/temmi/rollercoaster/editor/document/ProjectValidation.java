package land.temmi.rollercoaster.editor.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-checks every cross-reference the interactive Place/Update commands already enforce one at a
 * time, in bulk, over an already-loaded project. {@link ProjectFile#load} deliberately skips
 * cross-map references (a transition's target map, an event's CHANGE_MAP action) since maps can
 * reference each other regardless of file order - nothing else re-checks them once loading
 * finishes, so a hand-edited or corrupted project.json can carry a dangling reference past load
 * time undetected. Collects every problem instead of stopping at the first one, unlike the
 * commands this reuses.
 */
public final class ProjectValidation {
    private ProjectValidation() {
    }

    public static List<String> findProblems(ProjectDocument document) {
        List<String> problems = new ArrayList<>();
        for (MapAsset map : document.getMaps()) {
            for (MapTransitionAsset transition : map.getTransitions()) {
                if (document.findMap(transition.targetMapId) == null) {
                    problems.add("Karte '" + map.id + "': Übergang '" + transition.instanceId
                        + "' verweist auf unbekannte Karte '" + transition.targetMapId + "'");
                }
            }
            for (GameEventAsset event : map.getEvents()) {
                try {
                    EventValidation.requireReferences(document, map, event);
                } catch (IllegalArgumentException e) {
                    problems.add("Karte '" + map.id + "': " + e.getMessage());
                }
            }
            for (MapEntityAsset entity : map.getEntities()) {
                try {
                    EntityPropertyValidation.requireValid(document, map, entity);
                } catch (IllegalArgumentException e) {
                    problems.add("Karte '" + map.id + "': " + e.getMessage());
                }
            }
        }
        return problems;
    }
}
