package land.temmi.rollercoaster.editor.ui;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Main editor window. Asset list, map view and properties are empty until Phase 2/3 fill them in. */
public final class EditorFrame extends JFrame {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JLabel statusLabel = new JLabel("Vorschau: getrennt");
    private final JTextArea diagnostics = new JTextArea();

    public EditorFrame(Runnable onRestartPreviewRequested) {
        super("Trackside Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        setJMenuBar(buildMenuBar(onRestartPreviewRequested));
        add(buildContent(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setSize(1280, 800);
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenuBar(Runnable onRestartPreviewRequested) {
        JMenuBar menuBar = new JMenuBar();
        JMenu previewMenu = new JMenu("Vorschau");
        JMenuItem restart = new JMenuItem("Neu verbinden");
        restart.addActionListener(e -> onRestartPreviewRequested.run());
        previewMenu.add(restart);
        menuBar.add(previewMenu);
        return menuBar;
    }

    private JSplitPane buildContent() {
        JPanel assets = placeholderPanel("Assets");
        JPanel map = placeholderPanel("Karte");
        JPanel properties = placeholderPanel("Eigenschaften");
        JPanel diagnosticsPanel = buildDiagnosticsPanel();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, properties, diagnosticsPanel);
        rightSplit.setResizeWeight(0.6);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assets, map);
        centerSplit.setResizeWeight(0.2);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightSplit);
        mainSplit.setResizeWeight(0.7);
        return mainSplit;
    }

    private JPanel placeholderPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setPreferredSize(new Dimension(200, 200));
        return panel;
    }

    private JPanel buildDiagnosticsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Diagnosen"));
        diagnostics.setEditable(false);
        diagnostics.setLineWrap(true);
        panel.add(new JScrollPane(diagnostics), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(statusLabel);
        return bar;
    }

    public void onPreviewStatusChanged(PreviewProcess.Status status, String detail) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Vorschau: " + describe(status));
            diagnostics.append(LocalTime.now().format(TIMESTAMP) + "  " + describe(status)
                + (detail != null ? " - " + detail : "") + "\n");
        });
    }

    private static String describe(PreviewProcess.Status status) {
        switch (status) {
            case STARTING: return "startet";
            case CONNECTED: return "verbunden";
            case DISCONNECTED: return "getrennt";
            case FAILED: return "fehlgeschlagen";
            default: throw new IllegalArgumentException("Unknown status: " + status);
        }
    }
}
