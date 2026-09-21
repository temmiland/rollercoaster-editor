package land.temmi.rollercoaster.editor.ui;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.BadLocationException;
import land.temmi.rollercoaster.editor.protocol.CameraMode;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Main editor window. Asset list and map view are empty until Phase 2/3 fill them in. */
public final class EditorFrame extends JFrame {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern DIAGNOSTIC_ID_PATTERN = Pattern.compile("'([^']+)'");
    private static final Pattern DIAGNOSTIC_PICK_PATTERN =
        Pattern.compile("Pick: \\(([-\\d.]+), ([-\\d.]+), ([-\\d.]+)\\)");

    private final ProjectController projectController;
    private final RecentProjects recentProjects;

    private final JLabel statusLabel = new JLabel("Vorschau: getrennt");
    private final JTextArea diagnostics = new JTextArea();
    private final JTextField projectNameField = new JTextField();
    private final JMenu recentMenu = new JMenu("Zuletzt geöffnet");
    private final JMenuItem saveMenuItem = new JMenuItem("Speichern");
    private final JMenuItem saveAsMenuItem = new JMenuItem("Speichern unter…");
    private final JMenuItem validateMenuItem = new JMenuItem("Projekt validieren…");
    private final JMenuItem undoMenuItem = new JMenuItem("Rückgängig");
    private final JMenuItem redoMenuItem = new JMenuItem("Wiederholen");
    private final AssetsPanel assetsPanel;
    private final MapPanel mapPanel;

    public EditorFrame(Runnable onRestartPreviewRequested, ProjectController projectController,
                       RecentProjects recentProjects,
                       Function<String, CompletableFuture<ModelBoundsResult>> modelBoundsComputer,
                       MapPanel.PreviewMapRequester previewMapRequester,
                       Consumer<CameraMode> onCameraModeChanged,
                       Consumer<Boolean> onTestModeChanged,
                       Consumer<String> onTriggerEvent,
                       Runnable onResetFlags,
                       Consumer<Float> onSetTimeOfDay) {
        super("Rollercoaster Editor");
        this.projectController = projectController;
        this.recentProjects = recentProjects;
        this.assetsPanel = new AssetsPanel(projectController, modelBoundsComputer);
        this.mapPanel = new MapPanel(projectController, previewMapRequester,
            new MapPanel.TestModeController() {
                @Override
                public void triggerEvent(String eventInstanceId) {
                    onTriggerEvent.accept(eventInstanceId);
                }

                @Override
                public void resetFlags() {
                    onResetFlags.run();
                }

                @Override
                public void setTimeOfDay(float hours) {
                    onSetTimeOfDay.accept(hours);
                }
            });

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (confirmDiscardIfDirty()) {
                    dispose();
                    System.exit(0);
                }
            }
        });

        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar(onRestartPreviewRequested, onCameraModeChanged, onTestModeChanged));
        add(buildContent(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setSize(1280, 800);
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenuBar(Runnable onRestartPreviewRequested, Consumer<CameraMode> onCameraModeChanged,
                                  Consumer<Boolean> onTestModeChanged) {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu fileMenu = new JMenu("Datei");
        JMenuItem newProject = new JMenuItem("Neues Projekt…");
        newProject.addActionListener(e -> onNewProject());
        JMenuItem openProject = new JMenuItem("Projekt öffnen…");
        openProject.addActionListener(e -> onOpenProject());
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutMask));
        saveMenuItem.addActionListener(e -> onSave());
        saveAsMenuItem.addActionListener(e -> onSaveAs());
        validateMenuItem.addActionListener(e -> onValidateProject());
        JMenuItem exit = new JMenuItem("Beenden");
        exit.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        fileMenu.add(newProject);
        fileMenu.add(openProject);
        fileMenu.addSeparator();
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(validateMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(recentMenu);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu editMenu = new JMenu("Bearbeiten");
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask));
        undoMenuItem.addActionListener(e -> onUndo());
        redoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask | KeyEvent.SHIFT_DOWN_MASK));
        redoMenuItem.addActionListener(e -> onRedo());
        editMenu.add(undoMenuItem);
        editMenu.add(redoMenuItem);

        JMenu previewMenu = new JMenu("Vorschau");
        JMenuItem restart = new JMenuItem("Neu verbinden");
        restart.addActionListener(e -> onRestartPreviewRequested.run());
        previewMenu.add(restart);
        previewMenu.addSeparator();
        JRadioButtonMenuItem freeCamera = new JRadioButtonMenuItem("Freie Kamera", true);
        freeCamera.addActionListener(e -> onCameraModeChanged.accept(CameraMode.FREE));
        JRadioButtonMenuItem gameCamera = new JRadioButtonMenuItem("Spielkamera");
        gameCamera.addActionListener(e -> onCameraModeChanged.accept(CameraMode.GAME));
        ButtonGroup cameraModeGroup = new ButtonGroup();
        cameraModeGroup.add(freeCamera);
        cameraModeGroup.add(gameCamera);
        previewMenu.add(freeCamera);
        previewMenu.add(gameCamera);
        previewMenu.addSeparator();
        JCheckBoxMenuItem testMode = new JCheckBoxMenuItem("Testmodus (Spielersteuerung)");
        testMode.addActionListener(e -> onTestModeChanged.accept(testMode.isSelected()));
        previewMenu.add(testMode);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(previewMenu);
        return menuBar;
    }

    private JSplitPane buildContent() {
        JPanel properties = buildPropertiesPanel();
        JPanel diagnosticsPanel = buildDiagnosticsPanel();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, properties, diagnosticsPanel);
        rightSplit.setResizeWeight(0.4);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assetsPanel, mapPanel);
        centerSplit.setResizeWeight(0.3);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightSplit);
        mainSplit.setResizeWeight(0.7);
        return mainSplit;
    }

    private JPanel buildPropertiesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Eigenschaften"));

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEADING));
        form.add(new JLabel("Projektname:"));
        projectNameField.setColumns(20);
        projectNameField.addActionListener(e -> commitNameEdit());
        projectNameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitNameEdit();
            }
        });
        form.add(projectNameField);
        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildDiagnosticsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Diagnosen"));
        diagnostics.setEditable(false);
        diagnostics.setLineWrap(true);
        diagnostics.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onDiagnosticLineClicked(e.getPoint());
            }
        });
        panel.add(new JScrollPane(diagnostics), BorderLayout.CENTER);
        return panel;
    }

    /** A Pick line carries a world position instead of an id - checked first since "Pick:" never
     * also contains a quoted id. Every other diagnostic line that names a specific asset writes
     * its id in single quotes, the same convention every validation/error message in this
     * codebase already uses - so that needs no per-source-type parsing, just the first quoted
     * token on the clicked line. Tries the map side (a map itself, or a placement on whichever
     * map is currently selected) before the project-level asset catalogs, and does nothing when
     * the line names neither. */
    private void onDiagnosticLineClicked(Point point) {
        int offset = diagnostics.viewToModel2D(point);
        if (offset < 0) return;
        try {
            int line = diagnostics.getLineOfOffset(offset);
            int start = diagnostics.getLineStartOffset(line);
            int end = diagnostics.getLineEndOffset(line);
            String lineText = diagnostics.getText(start, end - start);

            Matcher pick = DIAGNOSTIC_PICK_PATTERN.matcher(lineText);
            if (pick.find()) {
                float worldX = Float.parseFloat(pick.group(1));
                float worldZ = Float.parseFloat(pick.group(3));
                mapPanel.highlightWorldPosition(worldX, worldZ);
                return;
            }

            Matcher matcher = DIAGNOSTIC_ID_PATTERN.matcher(lineText);
            if (!matcher.find()) return;
            String id = matcher.group(1);
            if (!mapPanel.trySelectPlacement(id)) assetsPanel.trySelect(id);
        } catch (BadLocationException ignored) {
            // The click landed past the current text (e.g. a trailing blank line) - nothing to resolve.
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(statusLabel);
        return bar;
    }

    private void commitNameEdit() {
        if (!projectController.isOpen()) return;
        String newName = projectNameField.getText().trim();
        if (newName.isEmpty() || newName.equals(projectController.getName())) {
            projectNameField.setText(projectController.getName());
            return;
        }
        projectController.rename(newName);
    }

    private void onNewProject() {
        if (!confirmDiscardIfDirty()) return;
        JFileChooser chooser = directoryChooser("Projektordner wählen");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path directory = chooser.getSelectedFile().toPath();
        String name = JOptionPane.showInputDialog(this, "Projektname:", "Neues Projekt",
            JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        try {
            projectController.newProject(directory, name.trim());
            recentProjects.add(directory.toString());
        } catch (IOException e) {
            showError("Projekt konnte nicht angelegt werden", e);
        }
    }

    private void onOpenProject() {
        if (!confirmDiscardIfDirty()) return;
        JFileChooser chooser = directoryChooser("Projekt öffnen");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        openDirectory(chooser.getSelectedFile().toPath());
    }

    private void openDirectory(Path directory) {
        try {
            if (ProjectController.hasNewerAutosave(directory)) {
                int choice = JOptionPane.showConfirmDialog(this,
                    "Es gibt eine automatische Sicherung, die neuer als der letzte Speicherstand ist. Wiederherstellen?",
                    "Automatische Sicherung gefunden", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    projectController.restoreAutosave(directory);
                } else {
                    projectController.openProject(directory);
                }
            } else {
                projectController.openProject(directory);
            }
            recentProjects.add(directory.toString());
        } catch (IOException e) {
            showError("Projekt konnte nicht geöffnet werden", e);
        }
    }

    private void onSave() {
        try {
            projectController.save();
        } catch (IOException e) {
            showError("Projekt konnte nicht gespeichert werden", e);
        }
    }

    private void onSaveAs() {
        JFileChooser chooser = directoryChooser("Speichern unter");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path directory = chooser.getSelectedFile().toPath();
        try {
            projectController.saveAs(directory);
            recentProjects.add(directory.toString());
        } catch (IOException e) {
            showError("Projekt konnte nicht gespeichert werden", e);
        }
    }

    private void onValidateProject() {
        List<String> problems = projectController.validateProject();
        if (problems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Probleme gefunden.", "Projekt validieren",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea list = new JTextArea(String.join("\n", problems));
        list.setEditable(false);
        list.setRows(Math.min(problems.size(), 15));
        JOptionPane.showMessageDialog(this, new JScrollPane(list),
            problems.size() + " Problem(e) gefunden", JOptionPane.WARNING_MESSAGE);
    }

    private void onUndo() {
        projectController.undo();
    }

    private void onRedo() {
        projectController.redo();
    }

    private boolean confirmDiscardIfDirty() {
        if (!projectController.isDirty()) return true;
        int choice = JOptionPane.showConfirmDialog(this,
            "Das aktuelle Projekt wurde geändert. Änderungen speichern?",
            "Ungespeicherte Änderungen", JOptionPane.YES_NO_CANCEL_OPTION);
        if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return false;
        if (choice == JOptionPane.YES_OPTION) {
            try {
                projectController.save();
            } catch (IOException e) {
                showError("Projekt konnte nicht gespeichert werden", e);
                return false;
            }
        }
        return true;
    }

    private JFileChooser directoryChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser;
    }

    private void showError(String title, Exception cause) {
        JOptionPane.showMessageDialog(this, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    /** Called by ProjectController after every change; also called once at startup. */
    public void refreshProjectUi() {
        boolean open = projectController.isOpen();

        String title = "Rollercoaster Editor";
        if (open) {
            title += " - " + projectController.getName();
            if (projectController.isDirty()) title += " *";
        }
        setTitle(title);

        projectNameField.setEnabled(open);
        if (open && !projectNameField.getText().equals(projectController.getName())) {
            projectNameField.setText(projectController.getName());
        } else if (!open) {
            projectNameField.setText("");
        }

        saveMenuItem.setEnabled(open);
        saveAsMenuItem.setEnabled(open);
        validateMenuItem.setEnabled(open);
        undoMenuItem.setEnabled(projectController.canUndo());
        redoMenuItem.setEnabled(projectController.canRedo());

        refreshRecentMenu();
        assetsPanel.refresh();
        mapPanel.refresh();
    }

    private void refreshRecentMenu() {
        recentMenu.removeAll();
        List<String> paths = recentProjects.get();
        if (paths.isEmpty()) {
            JMenuItem empty = new JMenuItem("(keine)");
            empty.setEnabled(false);
            recentMenu.add(empty);
            return;
        }
        for (String path : paths) {
            JMenuItem item = new JMenuItem(path);
            item.addActionListener(e -> {
                if (!confirmDiscardIfDirty()) return;
                openDirectory(Paths.get(path));
            });
            recentMenu.add(item);
        }
    }

    public void onPick(float worldX, float worldY, float worldZ) {
        SwingUtilities.invokeLater(() -> diagnostics.append(LocalTime.now().format(TIMESTAMP)
            + String.format(Locale.ROOT, "  Pick: (%.2f, %.2f, %.2f)%n", worldX, worldY, worldZ)));
    }

    public void onEventLog(String message) {
        SwingUtilities.invokeLater(() -> diagnostics.append(LocalTime.now().format(TIMESTAMP)
            + "  " + message + "\n"));
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
