package land.temmi.rollercoaster.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Most-recently-opened project directories, most recent first. Persisted per OS user account. */
public final class RecentProjects {
    private static final int MAX_ENTRIES = 8;
    private static final String KEY_PREFIX = "recentProject.";

    private final Preferences preferences;

    public RecentProjects() {
        this(Preferences.userNodeForPackage(RecentProjects.class));
    }

    RecentProjects(Preferences preferences) {
        this.preferences = preferences;
    }

    public List<String> get() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < MAX_ENTRIES; i++) {
            String path = preferences.get(KEY_PREFIX + i, null);
            if (path != null) paths.add(path);
        }
        return paths;
    }

    public void add(String projectDirectory) {
        List<String> paths = get();
        paths.remove(projectDirectory);
        paths.add(0, projectDirectory);
        while (paths.size() > MAX_ENTRIES) paths.remove(paths.size() - 1);
        for (int i = 0; i < MAX_ENTRIES; i++) {
            if (i < paths.size()) preferences.put(KEY_PREFIX + i, paths.get(i));
            else preferences.remove(KEY_PREFIX + i);
        }
    }
}
