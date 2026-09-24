package land.temmi.rollercoaster.editor.webpreview;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * postMessage channel to the editor page that embeds the preview in an iframe.
 *
 * <p>Requests are plain objects with a {@code type}; {@code showMap} carries {@code files}, a
 * map from project-relative path to {@code Uint8Array}, plus the paths of the map, tileset,
 * model manifest and sprite manifest inside it. Every reply is posted to the parent window.
 */
final class EditorBridge {
    @JSFunctor
    interface Listener extends JSObject {
        void onMessage(JSObject data);
    }

    private EditorBridge() { }

    @JSBody(params = "listener", script = "window.addEventListener('message', function(e) {"
        + " if (e.data && typeof e.data.type === 'string') listener(e.data); });")
    static native void listen(Listener listener);

    @JSBody(params = {"type", "error"}, script = "var target = window.parent !== window ? window.parent : window;"
        + " target.postMessage({ source: 'rollercoaster-preview', type: type, error: error }, '*');")
    static native void reply(String type, String error);

    @JSBody(params = {"data", "key"}, script = "var value = data[key]; return value == null ? null : String(value);")
    static native String string(JSObject data, String key);

    /** Newline-separated so no JS array conversion is needed; project paths never contain one. */
    @JSBody(params = "data", script = "return data.files ? Object.keys(data.files).join('\\n') : '';")
    static native String filePaths(JSObject data);

    @JSBody(params = {"data", "path"}, script = "var bytes = data.files[path];"
        + " return new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);")
    static native Int8Array fileBytes(JSObject data, String path);

    static MemoryFiles files(JSObject data) {
        MemoryFiles files = new MemoryFiles();
        String paths = filePaths(data);
        if (paths.isEmpty()) return files;
        for (String path : paths.split("\n")) {
            files.put(path, fileBytes(data, path).copyToJavaArray());
        }
        return files;
    }
}
