package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.protocol.Hello;
import land.temmi.rollercoaster.editor.protocol.HelloAck;
import land.temmi.rollercoaster.editor.protocol.MessageChannel;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Starts, monitors and restarts the preview subprocess; runs the editor's half of the handshake. */
public final class PreviewProcess {
    public enum Status { STARTING, CONNECTED, DISCONNECTED, FAILED }

    public interface StatusListener {
        void onPreviewStatusChanged(Status status, String detail);
    }

    private final String previewClasspath;
    private final StatusListener listener;

    private Process process;
    private ServerSocket serverSocket;

    public PreviewProcess(String previewClasspath, StatusListener listener) {
        this.previewClasspath = previewClasspath;
        this.listener = listener;
    }

    public synchronized void start() {
        stop();
        listener.onPreviewStatusChanged(Status.STARTING, null);
        try {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            int port = serverSocket.getLocalPort();

            Thread acceptThread = new Thread(() -> acceptAndHandshake(serverSocket), "preview-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            process = new ProcessBuilder(launchCommand(port))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

            Thread exitWatcher = new Thread(this::watchExit, "preview-exit-watch");
            exitWatcher.setDaemon(true);
            exitWatcher.start();
        } catch (IOException e) {
            listener.onPreviewStatusChanged(Status.FAILED, e.getMessage());
        }
    }

    public synchronized void restart() {
        start();
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }

    private List<String> launchCommand(int port) {
        String javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            // The preview is its own LWJGL3 process, so it - not the Swing editor - needs this flag.
            command.add("-XstartOnFirstThread");
        }
        command.add("-cp");
        command.add(previewClasspath);
        command.add("land.temmi.rollercoaster.editor.preview.PreviewMain");
        command.add(String.valueOf(port));
        return command;
    }

    private void acceptAndHandshake(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             MessageChannel channel = new MessageChannel(socket)) {
            Object message = channel.receive();
            if (!(message instanceof Hello)) {
                channel.send(new HelloAck(false, "Expected Hello, got " + message));
                listener.onPreviewStatusChanged(Status.FAILED, "Unexpected first message: " + message);
                return;
            }
            Hello hello = (Hello) message;
            if (hello.protocolVersion != MessageChannel.PROTOCOL_VERSION) {
                String reason = "protocol version mismatch: editor=" + MessageChannel.PROTOCOL_VERSION
                    + " preview=" + hello.protocolVersion;
                channel.send(new HelloAck(false, reason));
                listener.onPreviewStatusChanged(Status.FAILED, reason);
                return;
            }
            channel.send(new HelloAck(true, null));
            listener.onPreviewStatusChanged(Status.CONNECTED, null);

            while (channel.receive() != null) {
                // No further message types expected yet.
            }
            listener.onPreviewStatusChanged(Status.DISCONNECTED, "Preview closed the connection");
        } catch (IOException e) {
            listener.onPreviewStatusChanged(Status.DISCONNECTED, e.getMessage());
        }
    }

    private void watchExit() {
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                listener.onPreviewStatusChanged(Status.FAILED, "Preview process exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
