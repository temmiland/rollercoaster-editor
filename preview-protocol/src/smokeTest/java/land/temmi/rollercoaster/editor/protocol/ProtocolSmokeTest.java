package land.temmi.rollercoaster.editor.protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Checks the handshake codec over a real loopback socket; no GL context involved. */
public final class ProtocolSmokeTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            Thread server = new Thread(() -> runServer(serverSocket));
            server.start();

            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION));

                Object reply = client.receive();
                if (!(reply instanceof HelloAck)) throw new AssertionError("Expected a HelloAck, got " + reply);
                HelloAck ack = (HelloAck) reply;
                if (!ack.accepted) throw new AssertionError("Server rejected a matching protocol version");
                if (ack.reason != null) throw new AssertionError("Accepted handshake carried a reason: " + ack.reason);

                if (client.receive() != null) throw new AssertionError("Expected end of stream after the server closed");
            }
            server.join();
        }

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            Thread server = new Thread(() -> runServer(serverSocket));
            server.start();

            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION + 1));

                HelloAck ack = (HelloAck) client.receive();
                if (ack.accepted) throw new AssertionError("Server accepted a mismatched protocol version");
                if (ack.reason == null) throw new AssertionError("Rejected handshake carried no reason");
            }
            server.join();
        }

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     MessageChannel channel = new MessageChannel(socket)) {
                    channel.receive(); // Hello
                    channel.send(new HelloAck(true, null));
                    channel.send(new ShowGenericScene());
                    channel.send(new ShowSampleLevel());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();

            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION));
                client.receive(); // HelloAck
                if (!(client.receive() instanceof ShowGenericScene)) throw new AssertionError("Expected ShowGenericScene");
                if (!(client.receive() instanceof ShowSampleLevel)) throw new AssertionError("Expected ShowSampleLevel");
            }
            server.join();
        }

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            PickResult[] received = new PickResult[1];
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     MessageChannel channel = new MessageChannel(socket)) {
                    channel.receive(); // Hello
                    channel.send(new HelloAck(true, null));
                    received[0] = (PickResult) channel.receive();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();

            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION));
                client.receive(); // HelloAck
                client.send(new PickResult(1.5f, 0f, -2.25f));
            }
            server.join();

            if (received[0] == null) throw new AssertionError("Server never received the PickResult");
            if (received[0].worldX != 1.5f || received[0].worldY != 0f || received[0].worldZ != -2.25f) {
                throw new AssertionError("PickResult lost a coordinate in transit: " + received[0].worldX
                    + "," + received[0].worldY + "," + received[0].worldZ);
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            ComputeModelBounds[] received = new ComputeModelBounds[1];
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     MessageChannel channel = new MessageChannel(socket)) {
                    channel.receive(); // Hello
                    channel.send(new HelloAck(true, null));
                    received[0] = (ComputeModelBounds) channel.receive();
                    channel.send(ModelBoundsResult.ofBounds(-1f, 0f, -1f, 1f, 2f, 1f));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();

            ModelBoundsResult result;
            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION));
                client.receive(); // HelloAck
                client.send(new ComputeModelBounds("/tmp/house.gltf"));
                result = (ModelBoundsResult) client.receive();
            }
            server.join();

            if (received[0] == null || !"/tmp/house.gltf".equals(received[0].modelFilePath)) {
                throw new AssertionError("Server never received the ComputeModelBounds request");
            }
            if (!result.success || result.minY != 0f || result.maxY != 2f) {
                throw new AssertionError("ModelBoundsResult lost data in transit");
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            ShowMap[] received = new ShowMap[1];
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept();
                     MessageChannel channel = new MessageChannel(socket)) {
                    channel.receive(); // Hello
                    channel.send(new HelloAck(true, null));
                    received[0] = (ShowMap) channel.receive();
                    channel.send(ShowMapResult.ok());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            server.start();

            ShowMapResult result;
            try (Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), port);
                 MessageChannel client = new MessageChannel(clientSocket)) {
                client.send(new Hello(MessageChannel.PROTOCOL_VERSION));
                client.receive(); // HelloAck
                client.send(new ShowMap("/tmp/valley.json", 3, 2, new String[] {"grass", "cliff"},
                    new boolean[] {true, false}, "/tmp/catalogs/models.json"));
                result = (ShowMapResult) client.receive();
            }
            server.join();

            if (received[0] == null || !"/tmp/valley.json".equals(received[0].mapFilePath)
                || received[0].width != 3 || received[0].depth != 2 || received[0].tileIds.length != 2
                || !received[0].tileIds[1].equals("cliff") || received[0].tileWalkable[1]
                || !"/tmp/catalogs/models.json".equals(received[0].modelManifestFilePath)) {
                throw new AssertionError("ShowMap lost data in transit");
            }
            if (!result.success) throw new AssertionError("ShowMapResult lost data in transit");
        }

        System.out.println("PASS: Hello/HelloAck roundtrip over a loopback socket, version mismatch rejected with a "
            + "reason, scene-switch messages roundtrip after the handshake, a preview-to-editor PickResult, "
            + "a ComputeModelBounds/ModelBoundsResult roundtrip, and a ShowMap/ShowMapResult roundtrip");
    }

    /** Mirrors the editor's half of the handshake: one connection, one Hello, one reply. */
    private static void runServer(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             MessageChannel channel = new MessageChannel(socket)) {
            Hello hello = (Hello) channel.receive();
            boolean accepted = hello.protocolVersion == MessageChannel.PROTOCOL_VERSION;
            channel.send(accepted
                ? new HelloAck(true, null)
                : new HelloAck(false, "protocol version mismatch: editor=" + MessageChannel.PROTOCOL_VERSION
                    + " preview=" + hello.protocolVersion));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
