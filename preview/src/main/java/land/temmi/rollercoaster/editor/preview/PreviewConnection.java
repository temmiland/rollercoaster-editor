package land.temmi.rollercoaster.editor.preview;

import land.temmi.rollercoaster.editor.protocol.Hello;
import land.temmi.rollercoaster.editor.protocol.HelloAck;
import land.temmi.rollercoaster.editor.protocol.MessageChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/** Connects to the editor, performs the version handshake, and watches for disconnection. */
public final class PreviewConnection implements AutoCloseable {
    public interface DisconnectListener {
        void onDisconnected();
    }

    private final MessageChannel channel;
    private volatile boolean connected = true;

    private PreviewConnection(MessageChannel channel) {
        this.channel = channel;
    }

    public static PreviewConnection connect(int port) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        MessageChannel channel = new MessageChannel(socket);
        channel.send(new Hello(MessageChannel.PROTOCOL_VERSION));

        Object reply = channel.receive();
        if (!(reply instanceof HelloAck)) {
            channel.close();
            throw new IOException("Expected a HelloAck, got " + reply);
        }
        HelloAck ack = (HelloAck) reply;
        if (!ack.accepted) {
            channel.close();
            throw new IOException("Editor rejected the handshake: " + ack.reason);
        }
        return new PreviewConnection(channel);
    }

    /** Starts a background reader thread and calls the listener once the editor disconnects. */
    public void watch(DisconnectListener listener) {
        Thread reader = new Thread(() -> {
            try {
                while (channel.receive() != null) {
                    // No further message types expected yet.
                }
            } catch (IOException ignored) {
                // Falls through to the disconnect handling below.
            }
            connected = false;
            listener.onDisconnected();
        }, "preview-connection-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
