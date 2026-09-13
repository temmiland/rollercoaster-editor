package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.protocol.ComputeModelBounds;
import land.temmi.rollercoaster.editor.protocol.Hello;
import land.temmi.rollercoaster.editor.protocol.HelloAck;
import land.temmi.rollercoaster.editor.protocol.MessageChannel;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import land.temmi.rollercoaster.editor.protocol.PickResult;
import land.temmi.rollercoaster.editor.protocol.ShowGenericScene;
import land.temmi.rollercoaster.editor.protocol.ShowMap;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;
import land.temmi.rollercoaster.editor.protocol.ShowSampleLevel;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Starts, monitors and restarts the preview subprocess; runs the editor's half of the handshake. */
public final class PreviewProcess {
    public enum Status { STARTING, CONNECTED, DISCONNECTED, FAILED }

    public interface StatusListener {
        void onPreviewStatusChanged(Status status, String detail);
    }

    public interface PickListener {
        void onPick(float worldX, float worldY, float worldZ);
    }

    private final String previewClasspath;
    private final StatusListener listener;
    private final PickListener pickListener;

    private Process process;
    private ServerSocket serverSocket;
    private volatile MessageChannel channel;
    private volatile boolean levelOpen;
    private volatile CompletableFuture<ModelBoundsResult> pendingBoundsRequest;
    private volatile CompletableFuture<ShowMapResult> pendingShowMapRequest;
    private volatile ShowMap latestShowMap;

    public PreviewProcess(String previewClasspath, StatusListener listener, PickListener pickListener) {
        this.previewClasspath = previewClasspath;
        this.listener = listener;
        this.pickListener = pickListener;
    }

    /** Tells the preview whether to show the open project or its generic default scene. */
    public synchronized void setLevelOpen(boolean levelOpen) {
        if (this.levelOpen == levelOpen) return;
        this.levelOpen = levelOpen;
        if (!levelOpen) {
            latestShowMap = null;
            CompletableFuture<ShowMapResult> future = pendingShowMapRequest;
            pendingShowMapRequest = null;
            if (future != null) future.completeExceptionally(new IOException("Project closed"));
        }
        sendLevelState();
    }

    /**
     * Asks the preview to load a GLTF/GLB file and report its bounds - the editor's Swing process
     * has no GL context, so it cannot build the Model/Mesh needed to compute this itself. Only one
     * request is in flight at a time, which matches the one-model-at-a-time import dialog flow.
     */
    public CompletableFuture<ModelBoundsResult> computeModelBounds(String modelFilePath) {
        CompletableFuture<ModelBoundsResult> future = new CompletableFuture<>();
        MessageChannel current = channel;
        if (current == null) {
            future.completeExceptionally(new IOException("Preview is not connected"));
            return future;
        }
        pendingBoundsRequest = future;
        try {
            current.send(new ComputeModelBounds(modelFilePath));
        } catch (IOException e) {
            pendingBoundsRequest = null;
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Asks the preview to render an already-exported map file, replacing whatever it currently shows. */
    public CompletableFuture<ShowMapResult> showMap(String mapFilePath, int width, int depth,
                                                    String tilesetManifestFilePath, String modelManifestFilePath,
                                                    String spriteManifestFilePath) {
        CompletableFuture<ShowMapResult> future = new CompletableFuture<>();
        ShowMap request = new ShowMap(mapFilePath, width, depth, tilesetManifestFilePath,
            modelManifestFilePath, spriteManifestFilePath);
        latestShowMap = request;
        MessageChannel current = channel;
        if (current == null) {
            CompletableFuture<ShowMapResult> previous = pendingShowMapRequest;
            pendingShowMapRequest = future;
            if (previous != null && !previous.isDone()) {
                previous.completeExceptionally(new IOException("Preview request superseded"));
            }
            return future;
        }
        pendingShowMapRequest = future;
        try {
            current.send(request);
        } catch (IOException e) {
            pendingShowMapRequest = null;
            future.completeExceptionally(e);
        }
        return future;
    }

    private void sendLevelState() {
        MessageChannel current = channel;
        if (current == null) return;
        try {
            current.send(levelOpen ? new ShowSampleLevel() : new ShowGenericScene());
        } catch (IOException e) {
            listener.onPreviewStatusChanged(Status.DISCONNECTED, e.getMessage());
        }
    }

    /** Replays the newest document map after the preview process reconnects. */
    private void sendLatestMap() {
        MessageChannel current = channel;
        ShowMap request = latestShowMap;
        if (current == null || request == null || !levelOpen) return;
        try {
            current.send(request);
        } catch (IOException e) {
            listener.onPreviewStatusChanged(Status.DISCONNECTED, e.getMessage());
        }
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
             MessageChannel established = new MessageChannel(socket)) {
            Object message = established.receive();
            if (!(message instanceof Hello)) {
                established.send(new HelloAck(false, "Expected Hello, got " + message));
                listener.onPreviewStatusChanged(Status.FAILED, "Unexpected first message: " + message);
                return;
            }
            Hello hello = (Hello) message;
            if (hello.protocolVersion != MessageChannel.PROTOCOL_VERSION) {
                String reason = "protocol version mismatch: editor=" + MessageChannel.PROTOCOL_VERSION
                    + " preview=" + hello.protocolVersion;
                established.send(new HelloAck(false, reason));
                listener.onPreviewStatusChanged(Status.FAILED, reason);
                return;
            }
            established.send(new HelloAck(true, null));
            channel = established;
            sendLevelState();
            sendLatestMap();
            listener.onPreviewStatusChanged(Status.CONNECTED, null);

            Object incoming;
            while ((incoming = established.receive()) != null) {
                if (incoming instanceof PickResult) {
                    PickResult pick = (PickResult) incoming;
                    pickListener.onPick(pick.worldX, pick.worldY, pick.worldZ);
                } else if (incoming instanceof ModelBoundsResult) {
                    CompletableFuture<ModelBoundsResult> future = pendingBoundsRequest;
                    pendingBoundsRequest = null;
                    if (future != null) future.complete((ModelBoundsResult) incoming);
                } else if (incoming instanceof ShowMapResult) {
                    CompletableFuture<ShowMapResult> future = pendingShowMapRequest;
                    pendingShowMapRequest = null;
                    if (future != null) future.complete((ShowMapResult) incoming);
                }
            }
            listener.onPreviewStatusChanged(Status.DISCONNECTED, "Preview closed the connection");
        } catch (IOException e) {
            listener.onPreviewStatusChanged(Status.DISCONNECTED, e.getMessage());
        } finally {
            channel = null;
            CompletableFuture<ModelBoundsResult> boundsFuture = pendingBoundsRequest;
            pendingBoundsRequest = null;
            if (boundsFuture != null) boundsFuture.completeExceptionally(new IOException("Preview disconnected"));
            CompletableFuture<ShowMapResult> showMapFuture = pendingShowMapRequest;
            pendingShowMapRequest = null;
            if (showMapFuture != null) showMapFuture.completeExceptionally(new IOException("Preview disconnected"));
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
