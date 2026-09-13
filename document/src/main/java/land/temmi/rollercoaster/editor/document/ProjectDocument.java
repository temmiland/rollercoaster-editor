package land.temmi.rollercoaster.editor.document;

/** Minimal editable project state. Phase 2+ adds maps, catalogs and asset roots. */
public final class ProjectDocument {
    public static final int FORMAT_VERSION = 1;

    private String name;

    public ProjectDocument(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    /** Package-private: mutation goes through a {@link Command} so undo/redo stays consistent. */
    void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Project name is required");
        this.name = name;
    }
}
