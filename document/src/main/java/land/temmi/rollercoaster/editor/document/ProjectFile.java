package land.temmi.rollercoaster.editor.document;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes project.json. Saves are atomic: write a sibling temp file, then rename over the target. */
public final class ProjectFile {
    public static final String FILE_NAME = "project.json";

    private ProjectFile() {
    }

    public static Path fileIn(Path projectDirectory) {
        return projectDirectory.resolve(FILE_NAME);
    }

    public static ProjectDocument load(Path projectDirectory) throws IOException {
        String content = Files.readString(fileIn(projectDirectory), StandardCharsets.UTF_8);
        JsonValue root = new JsonReader().parse(content);
        int version = root.getInt("formatVersion", -1);
        if (version != ProjectDocument.FORMAT_VERSION) {
            throw new IOException("Unsupported project format version: " + version);
        }
        JsonValue nameValue = root.get("name");
        if (nameValue == null || !nameValue.isString() || nameValue.asString().trim().isEmpty()) {
            throw new IOException("project.json is missing a nonempty 'name'");
        }
        return new ProjectDocument(nameValue.asString());
    }

    public static void save(ProjectDocument document, Path projectDirectory) throws IOException {
        Files.createDirectories(projectDirectory);
        Path target = fileIn(projectDirectory);
        Path temp = projectDirectory.resolve(FILE_NAME + ".tmp");
        Files.writeString(temp, toJson(document), StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String toJson(ProjectDocument document) throws IOException {
        StringWriter buffer = new StringWriter();
        JsonWriter writer = new JsonWriter(buffer);
        writer.object();
        writer.set("formatVersion", ProjectDocument.FORMAT_VERSION);
        writer.set("name", document.getName());
        writer.pop();
        return buffer.toString();
    }
}
