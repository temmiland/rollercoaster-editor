package land.temmi.rollercoaster.editor.webpreview;

import com.badlogic.gdx.files.FileHandle;
import java.util.HashMap;
import java.util.Map;

/** Project files handed over by the editor, addressed by project-relative paths with '/' separators. */
final class MemoryFiles {
    private final Map<String, byte[]> files = new HashMap<>();

    void put(String path, byte[] content) {
        files.put(normalize(path), content);
    }

    FileHandle get(String path) {
        return new MemoryFileHandle(this, normalize(path));
    }

    byte[] content(String path) {
        return files.get(path);
    }

    boolean isDirectory(String path) {
        String prefix = path.isEmpty() ? "" : path + "/";
        for (String file : files.keySet()) {
            if (file.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Resolves "." and ".." segments so relative asset references land on stored paths. */
    static String normalize(String path) {
        String[] segments = path.replace('\\', '/').split("/");
        StringBuilder result = new StringBuilder();
        int[] ends = new int[segments.length];
        int depth = 0;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (depth == 0) throw new IllegalArgumentException("Path leaves the project: " + path);
                depth--;
                result.setLength(depth == 0 ? 0 : ends[depth - 1]);
                continue;
            }
            if (result.length() > 0) result.append('/');
            result.append(segment);
            ends[depth++] = result.length();
        }
        return result.toString();
    }
}
