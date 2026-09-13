package land.temmi.rollercoaster.editor.preview;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.io.IOException;

/** Launched by the editor as a separate process; connects back to it over a loopback socket. */
public final class PreviewMain {
    private PreviewMain() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: PreviewMain <editor-port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        PreviewConnection connection;
        try {
            connection = PreviewConnection.connect(port);
        } catch (IOException e) {
            System.err.println("Could not connect to the editor: " + e.getMessage());
            System.exit(1);
            return;
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rollercoaster Editor — Preview");
        config.useVsync(true);
        config.setWindowedMode(1280, 720);
        new Lwjgl3Application(new PreviewApplication(connection), config);
    }
}
