package land.temmi.rollercoaster.editor.asset;

import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Writes the dialogue schema consumed by the engine's {@code DialogueManifest}. */
public final class DialogueManifestExport {
    public static final String FILE_NAME = "dialogues.json";

    /** A condition gating a response, matching {@code MapExport.Condition}'s lowercase enum names. */
    public static final class Condition {
        public final String type;
        public final String key;
        public final String comparison;
        public final String value;

        public Condition(String type, String key, String comparison, String value) {
            this.type = type;
            this.key = key;
            this.comparison = comparison;
            this.value = value;
        }
    }

    public static final class Response {
        public final String textId;
        public final String targetNode;
        public final List<Condition> conditions;

        public Response(String textId, String targetNode, List<Condition> conditions) {
            this.textId = textId;
            this.targetNode = targetNode;
            this.conditions = conditions;
        }
    }

    public static final class Node {
        public final String id;
        public final String speaker;
        public final String textId;
        public final String portrait;
        public final List<Response> responses;

        public Node(String id, String speaker, String textId, String portrait, List<Response> responses) {
            this.id = id;
            this.speaker = speaker;
            this.textId = textId;
            this.portrait = portrait;
            this.responses = responses;
        }
    }

    public static final class Entry {
        public final String id;
        public final String startNode;
        public final List<Node> nodes;

        public Entry(String id, String startNode, List<Node> nodes) {
            this.id = id;
            this.startNode = startNode;
            this.nodes = nodes;
        }
    }

    private DialogueManifestExport() {
    }

    public static Path write(List<Entry> dialogues, Path catalogDirectory) throws IOException {
        if (dialogues == null) throw new IllegalArgumentException("Dialogues are required");
        Files.createDirectories(catalogDirectory);
        Path target = catalogDirectory.resolve(FILE_NAME);
        Path temp = catalogDirectory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, toJson(dialogues), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static String toJson(List<Entry> dialogues) throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("version", 1);
        writer.array("dialogues");
        for (Entry dialogue : dialogues) {
            writer.object();
            writer.set("id", dialogue.id);
            writer.set("startNode", dialogue.startNode);
            writer.array("nodes");
            for (Node node : dialogue.nodes) {
                writer.object();
                writer.set("id", node.id);
                writer.set("speaker", node.speaker);
                writer.set("textId", node.textId);
                if (node.portrait != null) writer.set("portrait", node.portrait);
                writer.array("responses");
                for (Response response : node.responses) {
                    writer.object();
                    writer.set("textId", response.textId);
                    if (response.targetNode != null) writer.set("targetNode", response.targetNode);
                    writer.array("conditions");
                    for (Condition condition : response.conditions) {
                        writer.object();
                        writer.set("type", condition.type);
                        writer.set("key", condition.key);
                        writer.set("comparison", condition.comparison);
                        writer.set("value", condition.value);
                        writer.pop();
                    }
                    writer.pop();
                    writer.pop();
                }
                writer.pop();
                writer.pop();
            }
            writer.pop();
            writer.pop();
        }
        writer.pop();
        writer.pop();
        return buffer.toString();
    }
}
