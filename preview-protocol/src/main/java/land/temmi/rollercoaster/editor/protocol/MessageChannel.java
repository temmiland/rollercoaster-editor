package land.temmi.rollercoaster.editor.protocol;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Newline-delimited JSON channel shared by the editor and preview process.
 * Message classes are tagged so the wire format survives Java class renames.
 */
public final class MessageChannel implements Closeable {
    public static final int PROTOCOL_VERSION = 1;

    private final Socket socket;
    private final Json json;
    private final BufferedReader in;
    private final Writer out;

    public MessageChannel(Socket socket) throws IOException {
        this.socket = socket;
        json = new Json(JsonWriter.OutputType.json);
        json.addClassTag("hello", Hello.class);
        json.addClassTag("helloAck", HelloAck.class);
        json.addClassTag("showGenericScene", ShowGenericScene.class);
        json.addClassTag("showSampleLevel", ShowSampleLevel.class);
        json.addClassTag("pickResult", PickResult.class);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    /** Writes one message as a single JSON line. The class tag is always included. */
    public synchronized void send(Object message) throws IOException {
        out.write(json.toJson(message, (Class) null));
        out.write('\n');
        out.flush();
    }

    /** Blocks for the next message. Returns null at a clean end of stream. */
    public Object receive() throws IOException {
        String line = in.readLine();
        return line == null ? null : json.fromJson(null, line);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
