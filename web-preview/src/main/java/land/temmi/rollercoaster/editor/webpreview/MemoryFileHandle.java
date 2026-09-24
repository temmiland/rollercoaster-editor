package land.temmi.rollercoaster.editor.webpreview;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/** Read-only file handle over {@link MemoryFiles}, so engine loaders resolve siblings as on disk. */
final class MemoryFileHandle extends FileHandle {
    private final MemoryFiles files;
    private final String path;

    MemoryFileHandle(MemoryFiles files, String path) {
        this.files = files;
        this.path = path;
        this.type = FileType.Internal;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String name() {
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    @Override
    public String extension() {
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    @Override
    public String nameWithoutExtension() {
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override
    public String pathWithoutExtension() {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? path : path.substring(0, dot);
    }

    @Override
    public FileType type() {
        return type;
    }

    @Override
    public File file() {
        throw new GdxRuntimeException("Memory files have no java.io.File: " + path);
    }

    @Override
    public InputStream read() {
        return new ByteArrayInputStream(bytes());
    }

    @Override
    public byte[] readBytes() {
        return bytes().clone();
    }

    @Override
    public boolean exists() {
        return files.content(path) != null || files.isDirectory(path);
    }

    @Override
    public boolean isDirectory() {
        return files.content(path) == null && files.isDirectory(path);
    }

    @Override
    public long length() {
        byte[] content = files.content(path);
        return content == null ? 0 : content.length;
    }

    @Override
    public FileHandle child(String name) {
        return files.get(path.isEmpty() ? name : path + "/" + name);
    }

    @Override
    public FileHandle sibling(String name) {
        return parent().child(name);
    }

    @Override
    public FileHandle parent() {
        int index = path.lastIndexOf('/');
        return files.get(index < 0 ? "" : path.substring(0, index));
    }

    @Override
    public String toString() {
        return path;
    }

    private byte[] bytes() {
        byte[] content = files.content(path);
        if (content == null) throw new GdxRuntimeException("File not found: " + path);
        return content;
    }
}
