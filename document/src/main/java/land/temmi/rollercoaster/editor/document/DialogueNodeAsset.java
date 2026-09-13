package land.temmi.rollercoaster.editor.document;

import java.util.Collections;
import java.util.List;

/** One line (or branch point) of a dialogue. Text and portrait are translation/asset keys, not
 * the text or image itself. */
public final class DialogueNodeAsset {
    public final String id;
    public final String speakerId;
    public final String textId;
    public final String portrait;
    private final List<DialogueResponseAsset> responses;

    public DialogueNodeAsset(String id, String speakerId, String textId, String portrait,
                             List<DialogueResponseAsset> responses) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Node id is required");
        if (speakerId == null || speakerId.trim().isEmpty()) throw new IllegalArgumentException("Speaker id is required: " + id);
        if (textId == null || textId.trim().isEmpty()) throw new IllegalArgumentException("Text id is required: " + id);
        this.id = id;
        this.speakerId = speakerId;
        this.textId = textId;
        this.portrait = portrait;
        this.responses = responses == null ? Collections.emptyList() : List.copyOf(responses);
    }

    public List<DialogueResponseAsset> getResponses() {
        return responses;
    }
}
