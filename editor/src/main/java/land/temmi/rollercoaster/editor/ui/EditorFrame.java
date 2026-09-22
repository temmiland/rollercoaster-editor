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
import javax.swing.JTabbedPane;
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
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
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
    private static final int MAP_TAB_INDEX = 0;
    private static final int ASSETS_TAB_INDEX = 1;
    private static final Rectangle DEFAULT_WINDOW_BOUNDS = new Rectangle(0, 0, 1100, 720);

    private final ProjectController projectController;
    private final RecentProjects recentProjects;
    private final EditorWindowState windowState;

    private final JLabel statusLabel = new JLabel("Vorschau: getrennt");
    private final JTextArea diagnostics = new JTextArea();
    private final JTextField projectNameField = new JTextField();
    private final JMenu recentMenu = new JMenu("Zuletzt geöffnet");
    private final JMenuItem saveMenuItem = new JMenuItem("Speichern");
    private final JMenuItem saveAsMenuItem = new JMenuItem("Speichern unter…");
    private final JMenuItem validateMenuItem = new JMenuItem("Projekt validieren…");
    private final JMenuItem undoMenuItem = new JMenuItem("Rückgängig");
    private final JMenuItem redoMenuItem = new JMenuItem("Wiederholen");
    private final JCheckBoxMenuItem diagnosticsVisibleItem = new JCheckBoxMenuItem("Diagnosen anzeigen", true);
    private final AssetsPanel assetsPanel;
    private final MapPanel mapPanel;
    private JTabbedPane centerTabs;
    private JSplitPane contentSplit;
    private JPanel diagnosticsPanel;
    private int diagnosticsDividerSize;
    private int lastDiagnosticsDividerLocation = -1;

    public EditorFrame(Runnable onRestartPreviewRequested, ProjectController projectController,
                       RecentProjects recentProjects, EditorWindowState windowState,
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
        this.windowState = windowState;
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
                    saveWindowState();
                    dispose();
                    System.exit(0);
                }
            }
        });

        setLayout(new BorderLayout());
        setJMenuBar(buildMenuBar(onRestartPreviewRequested, onCameraModeChanged, onTestModeChanged));
        add(buildContent(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        restoreWindowState();
    }

    /** Restores everything that can be applied before the frame is shown (bounds, extended
     * state, selected tab, diagnostics visibility); the diagnostics divider needs the frame's
     * real size, so that part is deferred until after the pending setVisible(true) runs. */
    private void restoreWindowState() {
        Rectangle savedBounds = windowState.getWindowBounds();
        if (savedBounds != null) {
            setBounds(clampToScreen(savedBounds));
        } else {
            setSize(DEFAULT_WINDOW_BOUNDS.width, DEFAULT_WINDOW_BOUNDS.height);
            setLocationRelativeTo(null);
        }
        setExtendedState(windowState.getExtendedState());

        centerTabs.setSelectedIndex(
            Math.min(windowState.getSelectedTab("center", MAP_TAB_INDEX), centerTabs.getTabCount() - 1));

        boolean diagnosticsVisible = windowState.getFlag("diagnosticsVisible", true);
        diagnosticsVisibleItem.setSelected(diagnosticsVisible);
        if (!diagnosticsVisible) setDiagnosticsVisible(false);

        SwingUtilities.invokeLater(() -> {
            if (!diagnosticsVisibleItem.isSelected()) return;
            int location = windowState.getDividerLocation("diagnostics", -1);
            if (location > 0) contentSplit.setDividerLocation(location);
        });
    }

    private void saveWindowState() {
        int extendedState = getExtendedState();
        windowState.putExtendedState(extendedState);
        if ((extendedState & MAXIMIZED_BOTH) == 0) windowState.putWindowBounds(getBounds());

        windowState.putSelectedTab("center", centerTabs.getSelectedIndex());
        windowState.putFlag("diagnosticsVisible", diagnosticsVisibleItem.isSelected());
        int dividerLocation = diagnosticsVisibleItem.isSelected()
            ? contentSplit.getDividerLocation() : lastDiagnosticsDividerLocation;
        if (dividerLocation > 0) windowState.putDividerLocation("diagnostics", dividerLocation);
    }

    /** A window position saved on a bigger or differently arranged screen must not reopen
     * partly or fully off-screen - the exact failure mode a small-monitor user would hit after
     * using the editor on a larger display first. */
    private static Rectangle clampToScreen(Rectangle bounds) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(bounds.width, screen.width);
        int height = Math.min(bounds.height, screen.height);
        int x = Math.max(screen.x, Math.min(bounds.x, screen.x + screen.width - width));
        int y = Math.max(screen.y, Math.min(bounds.y, screen.y + screen.height - height));
        return new Rectangle(x, y, width, height);
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

        JMenu viewMenu = new JMenu("Ansicht");
        diagnosticsVisibleItem.addActionListener(e -> setDiagnosticsVisible(diagnosticsVisibleItem.isSelected()));
        viewMenu.add(diagnosticsVisibleItem);

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
        menuBar.add(viewMenu);
        menuBar.add(previewMenu);
        return menuBar;
    }

    private JSplitPane buildContent() {
        centerTabs = new JTabbedPane();
        centerTabs.addTab("Karte", mapPanel);
        centerTabs.addTab("Assets", assetsPanel);

        diagnosticsPanel = buildDiagnosticsPanel();

        contentSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerTabs, diagnosticsPanel);
        contentSplit.setResizeWeight(1.0);
        contentSplit.setOneTouchExpandable(true);
        diagnosticsDividerSize = contentSplit.getDividerSize();
        return contentSplit;
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

    /** Collapses the diagnostics log to zero height instead of leaving it a thin, useless strip -
     * most sessions don't need it open, and on a small monitor its permanent share of the window
     * is exactly the kind of space this panel used to waste. */
    private void setDiagnosticsVisible(boolean visible) {
        if (visible) {
            contentSplit.setBottomComponent(diagnosticsPanel);
            contentSplit.setDividerSize(diagnosticsDividerSize);
            contentSplit.setDividerLocation(lastDiagnosticsDividerLocation > 0
                ? lastDiagnosticsDividerLocation : (int) (getHeight() * 0.7));
        } else {
            lastDiagnosticsDividerLocation = contentSplit.getDividerLocation();
            contentSplit.setBottomComponent(null);
            contentSplit.setDividerSize(0);
        }
        contentSplit.revalidate();
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
                centerTabs.setSelectedIndex(MAP_TAB_INDEX);
                return;
            }

            Matcher matcher = DIAGNOSTIC_ID_PATTERN.matcher(lineText);
            if (!matcher.find()) return;
            String id = matcher.group(1);
            if (mapPanel.trySelectPlacement(id)) {
                centerTabs.setSelectedIndex(MAP_TAB_INDEX);
            } else if (assetsPanel.trySelect(id)) {
                centerTabs.setSelectedIndex(ASSETS_TAB_INDEX);
            }
        } catch (BadLocationException ignored) {
            // The click landed past the current text (e.g. a trailing blank line) - nothing to resolve.
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Projekt:"));
        projectNameField.setColumns(18);
        projectNameField.addActionListener(e -> commitNameEdit());
        projectNameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitNameEdit();
            }
        });
        bar.add(projectNameField);
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
