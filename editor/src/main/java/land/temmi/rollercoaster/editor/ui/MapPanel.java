package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.ModelAsset;
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
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Map list plus the 2D terrain view: create/remove/export a map, paint its tiles and collision. */
final class MapPanel extends JPanel {
    /** Fire-and-forget from the UI's side; the returned future carries success/failure back. */
    interface PreviewMapRequester {
        CompletableFuture<ShowMapResult> showMap(String mapFilePath, int width, int depth,
                                                 String[] tileIds, boolean[] tileWalkable,
                                                 String modelManifestFilePath);
    }

    private final ProjectController projectController;
    private final PreviewMapRequester previewMapRequester;
    private final DefaultListModel<MapAsset> mapListModel = new DefaultListModel<>();
    private final JList<MapAsset> mapList = new JList<>(mapListModel);
    private final DefaultListModel<TileEntry> paletteListModel = new DefaultListModel<>();
    private final JList<TileEntry> paletteList = new JList<>(paletteListModel);
    private final DefaultListModel<ModelAsset> modelPaletteListModel = new DefaultListModel<>();
    private final JList<ModelAsset> modelPaletteList = new JList<>(modelPaletteListModel);
    private final DefaultListModel<MapProp> placedPropsListModel = new DefaultListModel<>();
    private final JList<MapProp> placedPropsList = new JList<>(placedPropsListModel);
    private final MapCanvas canvas;
    private final JToggleButton tileTool = new JToggleButton("Kacheln malen", true);
    private final JToggleButton collisionTool = new JToggleButton("Sperren malen");
    private final JToggleButton terrainTool = new JToggleButton("Terrain formen");
    private final JToggleButton propsTool = new JToggleButton("Props platzieren");
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
        modelPaletteList.setCellRenderer(labelRenderer(m -> m.id));
        placedPropsList.setCellRenderer(labelRenderer(p -> p.modelId + "  (" + p.x + ", " + p.z + ")"));
        placedPropsList.addListSelectionListener(e -> canvas.setSelectedPropInstanceId(
            placedPropsList.getSelectedValue() == null ? null : placedPropsList.getSelectedValue().instanceId));

        ButtonGroup tools = new ButtonGroup();
        tools.add(tileTool);
        tools.add(collisionTool);
        tools.add(terrainTool);
        tools.add(propsTool);
        tileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TILE));
        collisionTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.COLLISION));
        terrainTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TERRAIN));
        propsTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.PROPS));
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
        MapCanvas.PropListener propListener = this::onPlaceProp;
        return new MapCanvas(strokeListener, hoverListener, propListener);
    }

    private void onPlaceProp(String mapId, int x, int z) {
        ModelAsset model = modelPaletteList.getSelectedValue();
        if (model == null) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst ein Modell auswählen.");
            return;
        }
        MapProp prop = new MapProp(UUID.randomUUID().toString(), model.id, x, z, 0f, 0f);
        try {
            projectController.placeProp(mapId, prop);
            refresh();
            selectProp(prop.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Prop konnte nicht platziert werden", e);
        }
    }

    private JPanel buildMapListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(200, 0));

        JPanel mapListPanel = new JPanel(new BorderLayout());
        mapListPanel.add(new JScrollPane(mapList), BorderLayout.CENTER);

        JButton newMap = new JButton("Neu…");
        newMap.addActionListener(e -> onCreateMap());
        JButton removeMap = new JButton("Löschen");
        removeMap.addActionListener(e -> onRemoveMap());
        JButton resizeMap = new JButton("Größe ändern…");
        resizeMap.addActionListener(e -> onResizeMap());
        JButton exportMap = new JButton("Exportieren");
        exportMap.addActionListener(e -> onExportMap());
        JButton previewMap = new JButton("In Vorschau zeigen");
        previewMap.addActionListener(e -> onPreviewMap());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(newMap);
        buttons.add(removeMap);
        buttons.add(resizeMap);
        buttons.add(exportMap);
        buttons.add(previewMap);
        mapListPanel.add(buttons, BorderLayout.SOUTH);

        JPanel propsPanel = new JPanel(new BorderLayout());
        propsPanel.setBorder(BorderFactory.createTitledBorder("Platzierte Props"));
        propsPanel.add(new JScrollPane(placedPropsList), BorderLayout.CENTER);
        JButton removeProp = new JButton("Löschen");
        removeProp.addActionListener(e -> onRemoveProp());
        JButton editProp = new JButton("Bearbeiten…");
        editProp.addActionListener(e -> onEditProp());
        JButton duplicateProp = new JButton("Duplizieren…");
        duplicateProp.addActionListener(e -> onDuplicateProp());
        JPanel propsButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        propsButtons.add(editProp);
        propsButtons.add(duplicateProp);
        propsButtons.add(removeProp);
        propsPanel.add(propsButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapListPanel, propsPanel);
        split.setResizeWeight(0.6);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(tileTool);
        toolbar.add(collisionTool);
        toolbar.add(terrainTool);
        toolbar.add(propsTool);
        toolbar.add(new JLabel("Level:"));
        toolbar.add(levelSpinner);
        toolbar.add(shapeCombo);
        panel.add(toolbar, BorderLayout.NORTH);

        JTabbedPane palettePanel = new JTabbedPane();
        palettePanel.setPreferredSize(new Dimension(140, 0));
        palettePanel.addTab("Tiles", new JScrollPane(paletteList));
        palettePanel.addTab("Modelle", new JScrollPane(modelPaletteList));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, palettePanel, new JScrollPane(canvas));
        split.setResizeWeight(0);
        panel.add(split, BorderLayout.CENTER);
        panel.add(hoverLabel, BorderLayout.SOUTH);
        return panel;
    }

    void refresh() {
        boolean open = projectController.isOpen();
        mapList.setEnabled(open);

        ModelAsset selectedModel = modelPaletteList.getSelectedValue();
        modelPaletteListModel.clear();
        if (open) projectController.getModels().forEach(modelPaletteListModel::addElement);
        if (selectedModel != null) {
            for (int i = 0; i < modelPaletteListModel.size(); i++) {
                if (modelPaletteListModel.get(i).id.equals(selectedModel.id)) {
                    modelPaletteList.setSelectedIndex(i);
                    break;
                }
            }
        }

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

        MapProp selectedProp = placedPropsList.getSelectedValue();
        placedPropsListModel.clear();
        if (selected != null) {
            selected.getProps().forEach(placedPropsListModel::addElement);
            if (selectedProp != null) {
                for (int i = 0; i < placedPropsListModel.size(); i++) {
                    if (placedPropsListModel.get(i).instanceId.equals(selectedProp.instanceId)) {
                        placedPropsList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    private void onRemoveProp() {
        MapAsset selected = mapList.getSelectedValue();
        MapProp prop = placedPropsList.getSelectedValue();
        if (selected == null || prop == null) return;
        projectController.removeProp(selected.id, prop);
        refresh();
    }

    private void onEditProp() {
        MapAsset map = mapList.getSelectedValue();
        MapProp prop = placedPropsList.getSelectedValue();
        if (map == null || prop == null) return;
        PropTransform transform = askPropTransform("Prop bearbeiten", prop);
        if (transform == null) return;
        try {
            projectController.transformProp(map.id, prop, transform.x, transform.z,
                transform.elevation, transform.rotation);
            refresh();
            selectProp(prop.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Prop konnte nicht bearbeitet werden", e);
        }
    }

    private void onDuplicateProp() {
        MapAsset map = mapList.getSelectedValue();
        MapProp prop = placedPropsList.getSelectedValue();
        if (map == null || prop == null) return;
        float suggestedX = prop.x + 1f < map.width ? prop.x + 1f : prop.x - 1f >= 0f ? prop.x - 1f : prop.x;
        PropTransform transform = askPropTransform("Prop duplizieren",
            new MapProp(prop.instanceId, prop.modelId, suggestedX, prop.z, prop.elevation, prop.rotation));
        if (transform == null) return;
        MapProp duplicate = new MapProp(UUID.randomUUID().toString(), prop.modelId, transform.x, transform.z,
            transform.elevation, transform.rotation);
        try {
            projectController.placeProp(map.id, duplicate);
            refresh();
            selectProp(duplicate.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Prop konnte nicht dupliziert werden", e);
        }
    }

    private PropTransform askPropTransform(String title, MapProp prop) {
        JSpinner x = decimalSpinner(prop.x, 0.25d);
        JSpinner z = decimalSpinner(prop.z, 0.25d);
        JSpinner elevation = decimalSpinner(prop.elevation, 0.25d);
        JSpinner rotation = decimalSpinner(prop.rotation, 15d);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("X:"));
        form.add(x);
        form.add(new JLabel("Z:"));
        form.add(z);
        form.add(new JLabel("Höhenversatz:"));
        form.add(elevation);
        form.add(new JLabel("Drehung:"));
        form.add(rotation);
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        return new PropTransform(number(x), number(z), number(elevation), number(rotation));
    }

    private static JSpinner decimalSpinner(float value, double step) {
        return new JSpinner(new SpinnerNumberModel((double) value, -1_000d, 1_000d, step));
    }

    private static float number(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }

    private void selectProp(String instanceId) {
        for (int i = 0; i < placedPropsListModel.size(); i++) {
            if (placedPropsListModel.get(i).instanceId.equals(instanceId)) {
                placedPropsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private record PropTransform(float x, float z, float elevation, float rotation) {
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
        Integer width = askDimension("Breite (Tiles):", 16);
        if (width == null) return;
        Integer depth = askDimension("Tiefe (Tiles):", 16);
        if (depth == null) return;
        try {
            projectController.createMap(id.trim(), width, depth, tileset.id);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Karte konnte nicht angelegt werden", e);
        }
    }

    private Integer askDimension(String prompt, int currentValue) {
        String text = JOptionPane.showInputDialog(this, prompt, String.valueOf(currentValue));
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

    private void onResizeMap() {
        MapAsset selected = mapList.getSelectedValue();
        if (selected == null) return;
        Integer width = askDimension("Breite (Tiles):", selected.width);
        if (width == null) return;
        Integer depth = askDimension("Tiefe (Tiles):", selected.depth);
        if (depth == null) return;
        try {
            projectController.resizeMap(selected.id, width, depth);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Kartengröße konnte nicht geändert werden", e);
        }
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
        Path modelManifestFile = null;
        try {
            mapFile = projectController.exportMap(selected.id);
            if (!selected.getProps().isEmpty()) modelManifestFile = projectController.exportModels();
        } catch (IOException e) {
            showError("Karte oder Modelle konnten nicht exportiert werden", e);
            return;
        }

        List<TileEntry> tiles = tileset.getTiles();
        String[] tileIds = new String[tiles.size()];
        boolean[] tileWalkable = new boolean[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            tileIds[i] = tiles.get(i).id;
            tileWalkable[i] = tiles.get(i).walkable;
        }
        previewMapRequester.showMap(mapFile.toAbsolutePath().toString(), selected.width, selected.depth,
            tileIds, tileWalkable, modelManifestFile == null ? null : modelManifestFile.toAbsolutePath().toString())
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
