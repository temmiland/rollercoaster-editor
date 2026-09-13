package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TileShape;
import land.temmi.rollercoaster.editor.document.TilesetAsset;
import land.temmi.rollercoaster.editor.protocol.ShowMapResult;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Map list plus the 2D terrain view: create/remove/export a map, paint its tiles and collision. */
final class MapPanel extends JPanel {
    /** Fire-and-forget from the UI's side; the returned future carries success/failure back. */
    interface PreviewMapRequester {
        CompletableFuture<ShowMapResult> showMap(String mapFilePath, int width, int depth,
                                                 String[] tileIds, boolean[] tileWalkable, String[] modelIds);
    }

    private final ProjectController projectController;
    private final PreviewMapRequester previewMapRequester;
    private final DefaultListModel<MapAsset> mapListModel = new DefaultListModel<>();
    private final JList<MapAsset> mapList = new JList<>(mapListModel);
    private final DefaultListModel<TileEntry> paletteListModel = new DefaultListModel<>();
    private final JList<TileEntry> paletteList = new JList<>(paletteListModel);
    private final MapCanvas canvas;
    private final JToggleButton tileTool = new JToggleButton("Kacheln malen", true);
    private final JToggleButton collisionTool = new JToggleButton("Sperren malen");
    private final JToggleButton terrainTool = new JToggleButton("Terrain formen");
    private final JSpinner levelSpinner = new JSpinner(new SpinnerNumberModel(0, -20, 20, 1));
    private final JComboBox<TileShape> shapeCombo = new JComboBox<>(TileShape.values());
    private final JLabel hoverLabel = new JLabel(" ");

    MapPanel(ProjectController projectController, PreviewMapRequester previewMapRequester) {
        super(new BorderLayout());
        this.projectController = projectController;
        this.previewMapRequester = previewMapRequester;
        setBorder(BorderFactory.createTitledBorder("Karte"));

        canvas = buildCanvas();

        mapList.setCellRenderer(labelRenderer(m -> m.id + "  (" + m.width + "x" + m.depth + ")"));
        mapList.addListSelectionListener(e -> onMapSelected());
        paletteList.setCellRenderer(labelRenderer(t -> t.id));
        paletteList.addListSelectionListener(e -> canvas.setPaintTileId(
            paletteList.getSelectedValue() == null ? null : paletteList.getSelectedValue().id));

        ButtonGroup tools = new ButtonGroup();
        tools.add(tileTool);
        tools.add(collisionTool);
        tools.add(terrainTool);
        tileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TILE));
        collisionTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.COLLISION));
        terrainTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TERRAIN));
        levelSpinner.addChangeListener(e -> updateTerrainTarget());
        shapeCombo.addActionListener(e -> updateTerrainTarget());
        updateTerrainTarget();

        add(buildMapListPanel(), BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);
    }

    private MapCanvas buildCanvas() {
        MapCanvas.StrokeListener strokeListener = new MapCanvas.StrokeListener() {
            @Override
            public void onTileStroke(String mapId, List<PaintTilesCommand.Edit> edits) {
                projectController.paintTiles(mapId, edits);
            }

            @Override
            public void onCollisionStroke(String mapId, List<PaintCollisionCommand.Edit> edits) {
                projectController.paintCollision(mapId, edits);
            }

            @Override
            public void onTerrainStroke(String mapId, List<PaintTerrainCommand.Edit> edits) {
                projectController.paintTerrain(mapId, edits);
            }
        };
        MapCanvas.HoverListener hoverListener = (map, x, z) -> hoverLabel.setText(String.format(
            "(%d, %d)  Höhe %.2f  %s  %s%s", x, z, map.getHeight(x, z), map.getShape(x, z),
            map.getTile(x, z) == null ? "leer" : map.getTile(x, z), map.isBlocked(x, z) ? "  gesperrt" : ""));
        return new MapCanvas(strokeListener, hoverListener);
    }

    private JPanel buildMapListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(200, 0));
        panel.add(new JScrollPane(mapList), BorderLayout.CENTER);

        JButton newMap = new JButton("Neu…");
        newMap.addActionListener(e -> onCreateMap());
        JButton removeMap = new JButton("Löschen");
        removeMap.addActionListener(e -> onRemoveMap());
        JButton exportMap = new JButton("Exportieren");
        exportMap.addActionListener(e -> onExportMap());
        JButton previewMap = new JButton("In Vorschau zeigen");
        previewMap.addActionListener(e -> onPreviewMap());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(newMap);
        buttons.add(removeMap);
        buttons.add(exportMap);
        buttons.add(previewMap);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(tileTool);
        toolbar.add(collisionTool);
        toolbar.add(terrainTool);
        toolbar.add(new JLabel("Level:"));
        toolbar.add(levelSpinner);
        toolbar.add(shapeCombo);
        panel.add(toolbar, BorderLayout.NORTH);

        JPanel palettePanel = new JPanel(new BorderLayout());
        palettePanel.setBorder(BorderFactory.createTitledBorder("Tiles"));
        palettePanel.setPreferredSize(new Dimension(140, 0));
        palettePanel.add(new JScrollPane(paletteList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palettePanel, new JScrollPane(canvas));
        split.setResizeWeight(0);
        panel.add(split, BorderLayout.CENTER);
        panel.add(hoverLabel, BorderLayout.SOUTH);
        return panel;
    }

    void refresh() {
        boolean open = projectController.isOpen();
        mapList.setEnabled(open);

        MapAsset selected = mapList.getSelectedValue();
        mapListModel.clear();
        if (open) projectController.getMaps().forEach(mapListModel::addElement);
        if (selected != null) {
            for (int i = 0; i < mapListModel.size(); i++) {
                if (mapListModel.get(i).id.equals(selected.id)) {
                    mapList.setSelectedIndex(i);
                    break;
                }
            }
        }
        onMapSelected();
    }

    private void onMapSelected() {
        MapAsset selected = mapList.getSelectedValue();
        canvas.setMap(selected);
        hoverLabel.setText(" ");

        paletteListModel.clear();
        if (selected != null) {
            TilesetAsset tileset = findTileset(selected.tilesetId);
            if (tileset != null) tileset.getTiles().forEach(paletteListModel::addElement);
        }
    }

    /**
     * Level is the flat plateau a ramp bridges up to, so height derives from it and the shape
     * rather than being typed directly - that keeps every target the terrain tool can produce
     * already valid (whole levels for flat, half levels for a ramp), matching {@link MapAsset}.
     */
    private void updateTerrainTarget() {
        int level = (Integer) levelSpinner.getValue();
        TileShape shape = (TileShape) shapeCombo.getSelectedItem();
        float height = shape.isRamp() ? level - 0.5f : level;
        canvas.setTerrainTarget(height, shape);
    }

    private TilesetAsset findTileset(String id) {
        for (TilesetAsset tileset : projectController.getTilesets()) {
            if (tileset.id.equals(id)) return tileset;
        }
        return null;
    }

    private void onCreateMap() {
        if (!projectController.isOpen()) return;
        List<TilesetAsset> tilesets = projectController.getTilesets();
        if (tilesets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst ein Tileset anlegen.");
            return;
        }
        String id = JOptionPane.showInputDialog(this, "Karten-ID:", "Neue Karte", JOptionPane.PLAIN_MESSAGE);
        if (id == null || id.trim().isEmpty()) return;
        TilesetAsset tileset = (TilesetAsset) JOptionPane.showInputDialog(this, "Tileset:", "Neue Karte",
            JOptionPane.PLAIN_MESSAGE, null, tilesets.toArray(), tilesets.get(0));
        if (tileset == null) return;
        Integer width = askDimension("Breite (Tiles):");
        if (width == null) return;
        Integer depth = askDimension("Tiefe (Tiles):");
        if (depth == null) return;
        try {
            projectController.createMap(id.trim(), width, depth, tileset.id);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Karte konnte nicht angelegt werden", e);
        }
    }

    private Integer askDimension(String prompt) {
        String text = JOptionPane.showInputDialog(this, prompt, "16");
        if (text == null) return null;
        try {
            int value = Integer.parseInt(text.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Bitte eine positive Zahl eingeben.");
            return null;
        }
    }

    private void onRemoveMap() {
        MapAsset selected = mapList.getSelectedValue();
        if (selected == null) return;
        projectController.removeMap(selected);
        refresh();
    }

    private void onExportMap() {
        MapAsset selected = mapList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.exportMap(selected.id);
            JOptionPane.showMessageDialog(this, "Karte '" + selected.id + "' exportiert.");
        } catch (IOException e) {
            showError("Karte konnte nicht exportiert werden", e);
        }
    }

    private void onPreviewMap() {
        MapAsset selected = mapList.getSelectedValue();
        if (selected == null) return;
        TilesetAsset tileset = findTileset(selected.tilesetId);
        if (tileset == null) return;

        Path mapFile;
        try {
            mapFile = projectController.exportMap(selected.id);
        } catch (IOException e) {
            showError("Karte konnte nicht exportiert werden", e);
            return;
        }

        List<TileEntry> tiles = tileset.getTiles();
        String[] tileIds = new String[tiles.size()];
        boolean[] tileWalkable = new boolean[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            tileIds[i] = tiles.get(i).id;
            tileWalkable[i] = tiles.get(i).walkable;
        }
        java.util.Set<String> modelIds = new java.util.LinkedHashSet<>();
        for (MapProp prop : selected.getProps()) modelIds.add(prop.modelId);

        previewMapRequester.showMap(mapFile.toAbsolutePath().toString(), selected.width, selected.depth,
            tileIds, tileWalkable, modelIds.toArray(new String[0]))
            .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    JOptionPane.showMessageDialog(this, error.getMessage(), "Vorschau fehlgeschlagen",
                        JOptionPane.ERROR_MESSAGE);
                } else if (!result.success) {
                    JOptionPane.showMessageDialog(this, result.errorMessage, "Vorschau fehlgeschlagen",
                        JOptionPane.ERROR_MESSAGE);
                }
            }));
    }

    private void showError(String title, Exception cause) {
        JOptionPane.showMessageDialog(this, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static <T> ListCellRenderer<T> labelRenderer(Function<T, String> text) {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(text.apply(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        };
    }
}
