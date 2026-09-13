package land.temmi.rollercoaster.editor.document;

import java.util.List;

/** A registered conversation tree: a start node and every node it can reach. Branch targets are
 * validated against this same node set at construction time, same as the engine's {@code Dialogue}. */
public final class DialogueAsset {
    public final String id;
    public final String startNodeId;
    private final List<DialogueNodeAsset> nodes;

    public DialogueAsset(String id, String startNodeId, List<DialogueNodeAsset> nodes) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Dialogue id is required");
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Start node id is required: " + id);
        }
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("Dialogue must have at least one node: " + id);
        boolean foundStart = false;
        for (DialogueNodeAsset node : nodes) {
            if (node.id.equals(startNodeId)) foundStart = true;
            for (DialogueResponseAsset response : node.getResponses()) {
                if (response.targetNodeId != null && !containsNode(nodes, response.targetNodeId)) {
                    throw new IllegalArgumentException(
                        "Dialogue '" + id + "' response targets unknown node: " + response.targetNodeId);
                }
            }
        }
        if (!foundStart) throw new IllegalArgumentException("Dialogue '" + id + "' start node not found: " + startNodeId);
        this.id = id;
        this.startNodeId = startNodeId;
        this.nodes = List.copyOf(nodes);
    }

    public List<DialogueNodeAsset> getNodes() {
        return nodes;
    }

    public DialogueNodeAsset findNode(String nodeId) {
        for (DialogueNodeAsset node : nodes) if (node.id.equals(nodeId)) return node;
        return null;
    }

    private static boolean containsNode(List<DialogueNodeAsset> nodes, String nodeId) {
        for (DialogueNodeAsset node : nodes) if (node.id.equals(nodeId)) return true;
        return false;
    }
}
