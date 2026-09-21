package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.ConditionAsset;
import land.temmi.rollercoaster.editor.document.EntityPropertyDefinition;
import land.temmi.rollercoaster.editor.document.EntityTypeAsset;
import land.temmi.rollercoaster.editor.document.EventActionAsset;
import land.temmi.rollercoaster.editor.document.EventTriggerAsset;
import land.temmi.rollercoaster.editor.document.GameEventAsset;
import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapEntityAsset;
import land.temmi.rollercoaster.editor.document.MapLightAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.MapTransitionAsset;
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
import javax.swing.JCheckBox;
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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Map list plus the 2D terrain view: create/remove/export a map, paint its tiles and collision. */
final class MapPanel extends JPanel {
    /** Fire-and-forget from the UI's side; the returned future carries success/failure back. */
    interface PreviewMapRequester {
        CompletableFuture<ShowMapResult> showMap(String mapFilePath, int width, int depth,
                                                 String tilesetManifestFilePath, String modelManifestFilePath,
                                                 String spriteManifestFilePath, String dialogueManifestFilePath);
    }

    /** Manual test-mode hooks: fire-and-forget, like PreviewProcess's own camera/test-mode setters. */
    interface TestModeController {
        void triggerEvent(String eventInstanceId);

        void resetFlags();

        void setTimeOfDay(float hours);
    }

    private final ProjectController projectController;
    private final PreviewMapRequester previewMapRequester;
    private final TestModeController testModeController;
    private final DefaultListModel<MapAsset> mapListModel = new DefaultListModel<>();
    private final JList<MapAsset> mapList = new JList<>(mapListModel);
    private final DefaultListModel<TileEntry> paletteListModel = new DefaultListModel<>();
    private final JList<TileEntry> paletteList = new JList<>(paletteListModel);
    private final DefaultListModel<ModelAsset> modelPaletteListModel = new DefaultListModel<>();
    private final JList<ModelAsset> modelPaletteList = new JList<>(modelPaletteListModel);
    private final DefaultListModel<MapProp> placedPropsListModel = new DefaultListModel<>();
    private final JList<MapProp> placedPropsList = new JList<>(placedPropsListModel);
    private final DefaultListModel<MapEntityAsset> placedEntitiesListModel = new DefaultListModel<>();
    private final JList<MapEntityAsset> placedEntitiesList = new JList<>(placedEntitiesListModel);
    private final DefaultListModel<MapLightAsset> placedLightsListModel = new DefaultListModel<>();
    private final JList<MapLightAsset> placedLightsList = new JList<>(placedLightsListModel);
    private final DefaultListModel<MapTransitionAsset> placedTransitionsListModel = new DefaultListModel<>();
    private final JList<MapTransitionAsset> placedTransitionsList = new JList<>(placedTransitionsListModel);
    private final DefaultListModel<GameEventAsset> placedEventsListModel = new DefaultListModel<>();
    private final JList<GameEventAsset> placedEventsList = new JList<>(placedEventsListModel);
    private final MapCanvas canvas;
    private final JToggleButton tileTool = new JToggleButton("Kacheln malen", true);
    private final JToggleButton eraseTileTool = new JToggleButton("Kacheln löschen");
    private final JToggleButton pickTileTool = new JToggleButton("Pipette");
    private final JToggleButton fillTileTool = new JToggleButton("Füllen");
    private final JToggleButton rectangleTileTool = new JToggleButton("Rechteck");
    private final JToggleButton copyTileTool = new JToggleButton("Kopieren");
    private final JToggleButton pasteTileTool = new JToggleButton("Einfügen");
    private final JToggleButton collisionTool = new JToggleButton("Sperren malen");
    private final JToggleButton terrainTool = new JToggleButton("Terrain formen");
    private final JToggleButton propsTool = new JToggleButton("Props platzieren");
    private final JToggleButton entitiesTool = new JToggleButton("Entities platzieren");
    private final JToggleButton lightsTool = new JToggleButton("Lichter platzieren");
    private final JToggleButton transitionsTool = new JToggleButton("Übergänge platzieren");
    private final JSpinner levelSpinner = new JSpinner(new SpinnerNumberModel(0, -20, 20, 1));
    private final JComboBox<TileShape> shapeCombo = new JComboBox<>(TileShape.values());
    private final JCheckBox terrainOverlay = new JCheckBox("Terrain", true);
    private final JCheckBox gridOverlay = new JCheckBox("Gitter", true);
    private final JCheckBox walkabilityOverlay = new JCheckBox("Begehbarkeit");
    private final JCheckBox edgesOverlay = new JCheckBox("Kanten");
    private final JCheckBox manualCollisionOverlay = new JCheckBox("Manuelle Sperren", true);
    private final JCheckBox livePreview = new JCheckBox("Live-Vorschau", true);
    private final JLabel hoverLabel = new JLabel(" ");
    private final Timer livePreviewTimer;
    private boolean previewRequestInFlight;
    private String queuedPreviewMapId;
    private boolean queuedPreviewReportsErrors;

    MapPanel(ProjectController projectController, PreviewMapRequester previewMapRequester,
            TestModeController testModeController) {
        super(new BorderLayout());
        this.projectController = projectController;
        this.previewMapRequester = previewMapRequester;
        this.testModeController = testModeController;
        setBorder(BorderFactory.createTitledBorder("Karte"));

        canvas = buildCanvas();
        livePreviewTimer = new Timer(180, e -> startQueuedPreview());
        livePreviewTimer.setRepeats(false);

        mapList.setCellRenderer(labelRenderer(m -> m.id + "  (" + m.width + "x" + m.depth + ")"));
        mapList.addListSelectionListener(e -> onMapSelected());
        paletteList.setCellRenderer(labelRenderer(t -> t.id));
        paletteList.addListSelectionListener(e -> canvas.setPaintTileId(
            paletteList.getSelectedValue() == null ? null : paletteList.getSelectedValue().id));
        modelPaletteList.setCellRenderer(labelRenderer(m -> m.id));
        placedPropsList.setCellRenderer(labelRenderer(p -> p.modelId + "  (" + p.x + ", " + p.z + ")"));
        placedPropsList.addListSelectionListener(e -> canvas.setSelectedPropInstanceId(
            placedPropsList.getSelectedValue() == null ? null : placedPropsList.getSelectedValue().instanceId));
        placedEntitiesList.setCellRenderer(labelRenderer(entity -> entity.type
            + (entity.spriteId == null ? "" : " / " + entity.spriteId) + "  (" + entity.x + ", " + entity.z + ")"));
        placedEntitiesList.addListSelectionListener(e -> canvas.setSelectedEntityInstanceId(
            placedEntitiesList.getSelectedValue() == null ? null : placedEntitiesList.getSelectedValue().instanceId));
        placedLightsList.setCellRenderer(labelRenderer(light -> (light.spot ? "Spot " : "Punkt ") + light.instanceId
            + "  (" + light.x + ", " + light.z + ")" + (light.enabled ? "" : "  (aus)")));
        placedLightsList.addListSelectionListener(e -> canvas.setSelectedLightInstanceId(
            placedLightsList.getSelectedValue() == null ? null : placedLightsList.getSelectedValue().instanceId));
        placedTransitionsList.setCellRenderer(labelRenderer(transition -> transition.instanceId + "  ("
            + transition.x + ", " + transition.z + ") -> " + transition.targetMapId
            + " (" + transition.targetX + ", " + transition.targetZ + ")"));
        placedTransitionsList.addListSelectionListener(e -> canvas.setSelectedTransitionInstanceId(
            placedTransitionsList.getSelectedValue() == null ? null
                : placedTransitionsList.getSelectedValue().instanceId));
        placedEventsList.setCellRenderer(labelRenderer(event -> describeTrigger(event.trigger)
            + "  (" + event.getActions().size() + " Aktion(en))"));
        placedEventsList.addListSelectionListener(e -> canvas.setSelectedEventInstanceId(
            placedEventsList.getSelectedValue() == null ? null : placedEventsList.getSelectedValue().instanceId));

        ButtonGroup tools = new ButtonGroup();
        tools.add(tileTool);
        tools.add(eraseTileTool);
        tools.add(pickTileTool);
        tools.add(fillTileTool);
        tools.add(rectangleTileTool);
        tools.add(copyTileTool);
        tools.add(pasteTileTool);
        tools.add(collisionTool);
        tools.add(terrainTool);
        tools.add(propsTool);
        tools.add(entitiesTool);
        tools.add(lightsTool);
        tools.add(transitionsTool);
        tileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TILE));
        eraseTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.ERASE_TILE));
        pickTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.PICK_TILE));
        fillTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.FILL_TILE));
        rectangleTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.RECT_TILE));
        copyTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.COPY_TILE));
        pasteTileTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.PASTE_TILE));
        collisionTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.COLLISION));
        terrainTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TERRAIN));
        propsTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.PROPS));
        entitiesTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.ENTITIES));
        lightsTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.LIGHTS));
        transitionsTool.addActionListener(e -> canvas.setTool(MapCanvas.Tool.TRANSITIONS));
        levelSpinner.addChangeListener(e -> updateTerrainTarget());
        shapeCombo.addActionListener(e -> updateTerrainTarget());
        terrainOverlay.addActionListener(e -> updateOverlays());
        gridOverlay.addActionListener(e -> updateOverlays());
        walkabilityOverlay.addActionListener(e -> updateOverlays());
        edgesOverlay.addActionListener(e -> updateOverlays());
        manualCollisionOverlay.addActionListener(e -> updateOverlays());
        livePreview.addActionListener(e -> {
            if (livePreview.isSelected()) {
                scheduleLivePreview(mapList.getSelectedValue());
            } else if (!queuedPreviewReportsErrors) {
                livePreviewTimer.stop();
                queuedPreviewMapId = null;
            }
        });
        updateTerrainTarget();
        updateOverlays();

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
        MapCanvas.EntityListener entityListener = this::onPlaceEntity;
        MapCanvas.LightListener lightListener = this::onPlaceLight;
        MapCanvas.TransitionListener transitionListener = this::onPlaceTransition;
        MapCanvas.TileListener tileListener = this::onPickTile;
        return new MapCanvas(strokeListener, hoverListener, propListener, entityListener, lightListener,
            transitionListener, tileListener);
    }

    private void onPickTile(String tileId) {
        if (tileId == null) {
            paletteList.clearSelection();
            canvas.setPaintTileId(null);
        } else {
            for (int i = 0; i < paletteListModel.size(); i++) {
                if (tileId.equals(paletteListModel.get(i).id)) {
                    paletteList.setSelectedIndex(i);
                    break;
                }
            }
            canvas.setPaintTileId(tileId);
        }
        tileTool.setSelected(true);
        canvas.setTool(MapCanvas.Tool.TILE);
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

    private void onPlaceEntity(String mapId, int x, int z) {
        EntitySettings settings = askEntitySettings("Entity platzieren", "npc", null, x, z, Map.of());
        if (settings == null) return;
        MapEntityAsset entity = new MapEntityAsset(UUID.randomUUID().toString(), settings.type, settings.spriteId,
            settings.x, settings.z, settings.properties);
        try {
            projectController.placeEntity(mapId, entity);
            refresh();
            selectEntity(entity.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Entity konnte nicht platziert werden", e);
        }
    }

    private void onPlaceLight(String mapId, int x, int z) {
        MapAsset map = mapList.getSelectedValue();
        if (map != null && map.getLights().size() >= MapAsset.MAX_LIGHTS) {
            JOptionPane.showMessageDialog(this, "Diese Karte hat bereits " + MapAsset.MAX_LIGHTS
                + " Lichter - das gemeinsame Budget der Engine für Punkt- und Spotlichter.");
            return;
        }
        LightSettings settings = askLightSettings("Licht platzieren",
            new MapLightAsset(UUID.randomUUID().toString(), x, 1.5f, z, 1f, 1f, 1f, 1f, 4f, true));
        if (settings == null) return;
        MapLightAsset light = settings.toAsset(UUID.randomUUID().toString());
        try {
            projectController.placeLight(mapId, light);
            refresh();
            selectLight(light.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Licht konnte nicht platziert werden", e);
        }
    }

    private void onPlaceTransition(String mapId, int x, int z) {
        List<MapAsset> maps = projectController.getMaps();
        if (maps.isEmpty()) return;
        TransitionSettings settings = askTransitionSettings("Übergang platzieren", x, z, maps.get(0).id, 0, 0);
        if (settings == null) return;
        MapTransitionAsset transition = settings.toAsset(UUID.randomUUID().toString());
        try {
            projectController.placeTransition(mapId, transition);
            refresh();
            selectTransition(transition.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Übergang konnte nicht platziert werden", e);
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
        livePreview.setToolTipText("Aktualisiert die Vorschau nach jeder Änderung dieser Karte");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(newMap);
        buttons.add(removeMap);
        buttons.add(resizeMap);
        buttons.add(exportMap);
        buttons.add(previewMap);
        buttons.add(livePreview);
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

        JPanel entitiesPanel = new JPanel(new BorderLayout());
        entitiesPanel.setBorder(BorderFactory.createTitledBorder("Startpunkte & NPCs"));
        entitiesPanel.add(new JScrollPane(placedEntitiesList), BorderLayout.CENTER);
        JButton removeEntity = new JButton("Löschen");
        removeEntity.addActionListener(e -> onRemoveEntity());
        JButton editEntity = new JButton("Bearbeiten…");
        editEntity.addActionListener(e -> onEditEntity());
        JPanel entityButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        entityButtons.add(editEntity);
        entityButtons.add(removeEntity);
        entitiesPanel.add(entityButtons, BorderLayout.SOUTH);

        JPanel lightsPanel = new JPanel(new BorderLayout());
        lightsPanel.setBorder(BorderFactory.createTitledBorder("Lichter"));
        lightsPanel.add(new JScrollPane(placedLightsList), BorderLayout.CENTER);
        JButton removeLight = new JButton("Löschen");
        removeLight.addActionListener(e -> onRemoveLight());
        JButton editLight = new JButton("Bearbeiten…");
        editLight.addActionListener(e -> onEditLight());
        JPanel lightButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lightButtons.add(editLight);
        lightButtons.add(removeLight);
        lightsPanel.add(lightButtons, BorderLayout.SOUTH);

        JPanel transitionsPanel = new JPanel(new BorderLayout());
        transitionsPanel.setBorder(BorderFactory.createTitledBorder("Übergänge"));
        transitionsPanel.add(new JScrollPane(placedTransitionsList), BorderLayout.CENTER);
        JButton removeTransition = new JButton("Löschen");
        removeTransition.addActionListener(e -> onRemoveTransition());
        JButton editTransition = new JButton("Bearbeiten…");
        editTransition.addActionListener(e -> onEditTransition());
        JPanel transitionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        transitionButtons.add(editTransition);
        transitionButtons.add(removeTransition);
        transitionsPanel.add(transitionButtons, BorderLayout.SOUTH);

        JPanel eventsPanel = new JPanel(new BorderLayout());
        eventsPanel.setBorder(BorderFactory.createTitledBorder("Ereignisse"));
        eventsPanel.add(new JScrollPane(placedEventsList), BorderLayout.CENTER);
        JButton addEvent = new JButton("Hinzufügen…");
        addEvent.addActionListener(e -> onAddEvent());
        JButton removeEvent = new JButton("Löschen");
        removeEvent.addActionListener(e -> onRemoveEvent());
        JButton editEvent = new JButton("Bearbeiten…");
        editEvent.addActionListener(e -> onEditEvent());
        JButton triggerEvent = new JButton("Auslösen");
        triggerEvent.addActionListener(e -> onTriggerEvent());
        JPanel eventButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        eventButtons.add(addEvent);
        eventButtons.add(editEvent);
        eventButtons.add(removeEvent);
        eventButtons.add(triggerEvent);
        eventsPanel.add(eventButtons, BorderLayout.SOUTH);
        eventsPanel.add(buildTestModePanel(), BorderLayout.NORTH);

        JSplitPane transitionsAndEvents = new JSplitPane(JSplitPane.VERTICAL_SPLIT, transitionsPanel, eventsPanel);
        transitionsAndEvents.setResizeWeight(0.5);
        JSplitPane lightsAndTransitions = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lightsPanel, transitionsAndEvents);
        lightsAndTransitions.setResizeWeight(0.34);
        JSplitPane entityAndLights = new JSplitPane(JSplitPane.VERTICAL_SPLIT, entitiesPanel, lightsAndTransitions);
        entityAndLights.setResizeWeight(0.25);
        JSplitPane placements = new JSplitPane(JSplitPane.VERTICAL_SPLIT, propsPanel, entityAndLights);
        placements.setResizeWeight(0.2);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapListPanel, placements);
        split.setResizeWeight(0.35);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(tileTool);
        toolbar.add(eraseTileTool);
        toolbar.add(pickTileTool);
        toolbar.add(fillTileTool);
        toolbar.add(rectangleTileTool);
        toolbar.add(copyTileTool);
        toolbar.add(pasteTileTool);
        toolbar.add(collisionTool);
        toolbar.add(terrainTool);
        toolbar.add(propsTool);
        toolbar.add(entitiesTool);
        toolbar.add(lightsTool);
        toolbar.add(transitionsTool);
        toolbar.add(new JLabel("Level:"));
        toolbar.add(levelSpinner);
        toolbar.add(shapeCombo);
        toolbar.add(terrainOverlay);
        toolbar.add(gridOverlay);
        toolbar.add(walkabilityOverlay);
        toolbar.add(edgesOverlay);
        toolbar.add(manualCollisionOverlay);
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
        livePreview.setEnabled(open);

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
        Map<String, Boolean> tileWalkability = new LinkedHashMap<>();
        Map<String, Image> tileImages = new LinkedHashMap<>();
        if (selected != null) {
            TilesetAsset tileset = findTileset(selected.tilesetId);
            if (tileset != null) {
                for (TileEntry tile : tileset.getTiles()) {
                    paletteListModel.addElement(tile);
                    tileWalkability.put(tile.id, tile.walkable);
                    Image image = tileImage(tile);
                    if (image != null) tileImages.put(tile.id, image);
                }
            }
        }
        scheduleLivePreview(selected);
        canvas.setTileWalkability(tileWalkability);
        canvas.setTileImages(tileImages);

        Map<String, ModelAsset> modelsById = new LinkedHashMap<>();
        if (projectController.isOpen()) {
            for (ModelAsset model : projectController.getModels()) modelsById.put(model.id, model);
        }
        canvas.setModels(modelsById);

        MapProp selectedProp = placedPropsList.getSelectedValue();
        MapEntityAsset selectedEntity = placedEntitiesList.getSelectedValue();
        MapLightAsset selectedLight = placedLightsList.getSelectedValue();
        MapTransitionAsset selectedTransition = placedTransitionsList.getSelectedValue();
        GameEventAsset selectedEvent = placedEventsList.getSelectedValue();
        placedPropsListModel.clear();
        placedEntitiesListModel.clear();
        placedLightsListModel.clear();
        placedTransitionsListModel.clear();
        placedEventsListModel.clear();
        if (selected != null) {
            selected.getProps().forEach(placedPropsListModel::addElement);
            selected.getEntities().forEach(placedEntitiesListModel::addElement);
            selected.getLights().forEach(placedLightsListModel::addElement);
            selected.getTransitions().forEach(placedTransitionsListModel::addElement);
            selected.getEvents().forEach(placedEventsListModel::addElement);
            if (selectedProp != null) {
                for (int i = 0; i < placedPropsListModel.size(); i++) {
                    if (placedPropsListModel.get(i).instanceId.equals(selectedProp.instanceId)) {
                        placedPropsList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (selectedEntity != null) {
                for (int i = 0; i < placedEntitiesListModel.size(); i++) {
                    if (placedEntitiesListModel.get(i).instanceId.equals(selectedEntity.instanceId)) {
                        placedEntitiesList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (selectedLight != null) {
                for (int i = 0; i < placedLightsListModel.size(); i++) {
                    if (placedLightsListModel.get(i).instanceId.equals(selectedLight.instanceId)) {
                        placedLightsList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (selectedTransition != null) {
                for (int i = 0; i < placedTransitionsListModel.size(); i++) {
                    if (placedTransitionsListModel.get(i).instanceId.equals(selectedTransition.instanceId)) {
                        placedTransitionsList.setSelectedIndex(i);
                        break;
                    }
                }
            }
            if (selectedEvent != null) {
                for (int i = 0; i < placedEventsListModel.size(); i++) {
                    if (placedEventsListModel.get(i).instanceId.equals(selectedEvent.instanceId)) {
                        placedEventsList.setSelectedIndex(i);
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

    private void onRemoveEntity() {
        MapAsset selected = mapList.getSelectedValue();
        MapEntityAsset entity = placedEntitiesList.getSelectedValue();
        if (selected == null || entity == null) return;
        projectController.removeEntity(selected.id, entity);
        refresh();
    }

    private void onEditEntity() {
        MapAsset map = mapList.getSelectedValue();
        MapEntityAsset entity = placedEntitiesList.getSelectedValue();
        if (map == null || entity == null) return;
        EntitySettings settings = askEntitySettings("Entity bearbeiten", entity.type, entity.spriteId, entity.x, entity.z,
            entity.getProperties());
        if (settings == null) return;
        try {
            projectController.updateEntity(map.id, entity, settings.type, settings.spriteId, settings.x, settings.z,
                settings.properties);
            refresh();
            selectEntity(entity.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Entity konnte nicht bearbeitet werden", e);
        }
    }

    private void onRemoveLight() {
        MapAsset selected = mapList.getSelectedValue();
        MapLightAsset light = placedLightsList.getSelectedValue();
        if (selected == null || light == null) return;
        projectController.removeLight(selected.id, light);
        refresh();
    }

    private void onEditLight() {
        MapAsset map = mapList.getSelectedValue();
        MapLightAsset light = placedLightsList.getSelectedValue();
        if (map == null || light == null) return;
        LightSettings settings = askLightSettings("Licht bearbeiten", light);
        if (settings == null) return;
        try {
            projectController.updateLight(map.id, light, settings.toAsset(light.instanceId));
            refresh();
            selectLight(light.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Licht konnte nicht bearbeitet werden", e);
        }
    }

    private void onRemoveTransition() {
        MapAsset selected = mapList.getSelectedValue();
        MapTransitionAsset transition = placedTransitionsList.getSelectedValue();
        if (selected == null || transition == null) return;
        projectController.removeTransition(selected.id, transition);
        refresh();
    }

    private void onEditTransition() {
        MapAsset map = mapList.getSelectedValue();
        MapTransitionAsset transition = placedTransitionsList.getSelectedValue();
        if (map == null || transition == null) return;
        TransitionSettings settings = askTransitionSettings("Übergang bearbeiten", transition.x, transition.z,
            transition.targetMapId, transition.targetX, transition.targetZ);
        if (settings == null) return;
        try {
            projectController.updateTransition(map.id, transition, settings.toAsset(transition.instanceId));
            refresh();
            selectTransition(transition.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Übergang konnte nicht bearbeitet werden", e);
        }
    }

    private void onAddEvent() {
        MapAsset map = mapList.getSelectedValue();
        if (map == null) return;
        GameEventAsset event = askEventSettings("Ereignis anlegen", null);
        if (event == null) return;
        try {
            projectController.placeEvent(map.id, event);
            refresh();
            selectEvent(event.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Ereignis konnte nicht angelegt werden", e);
        }
    }

    private void onRemoveEvent() {
        MapAsset selected = mapList.getSelectedValue();
        GameEventAsset event = placedEventsList.getSelectedValue();
        if (selected == null || event == null) return;
        projectController.removeEvent(selected.id, event);
        refresh();
    }

    private void onEditEvent() {
        MapAsset map = mapList.getSelectedValue();
        GameEventAsset event = placedEventsList.getSelectedValue();
        if (map == null || event == null) return;
        GameEventAsset settings = askEventSettings("Ereignis bearbeiten", event);
        if (settings == null) return;
        try {
            projectController.updateEvent(map.id, event, settings);
            refresh();
            selectEvent(event.instanceId);
        } catch (IllegalArgumentException e) {
            showError("Ereignis konnte nicht bearbeitet werden", e);
        }
    }

    /** Manual test hook: runs the selected event's conditions/actions in the preview right now,
     * regardless of its authored trigger - independent of whether the preview is even connected. */
    private void onTriggerEvent() {
        GameEventAsset event = placedEventsList.getSelectedValue();
        if (event == null) return;
        testModeController.triggerEvent(event.instanceId);
    }

    private JPanel buildTestModePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton resetFlags = new JButton("Flags zurücksetzen");
        resetFlags.addActionListener(e -> testModeController.resetFlags());
        panel.add(resetFlags);

        panel.add(new JLabel("Tageszeit:"));
        JSpinner timeOfDay = new JSpinner(new SpinnerNumberModel(12.0, 0.0, 23.5, 0.5));
        panel.add(timeOfDay);
        JButton applyTime = new JButton("Übernehmen");
        applyTime.addActionListener(e -> testModeController.setTimeOfDay(((Number) timeOfDay.getValue()).floatValue()));
        panel.add(applyTime);
        return panel;
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

    /**
     * If the typed type matches a registered {@link EntityTypeAsset}, a property form for its
     * schema appears below and resizes the dialog live as the type field changes - an
     * unregistered type just keeps behaving as free text with no properties, as before schemas
     * existed at all.
     */
    private EntitySettings askEntitySettings(String title, String type, String spriteId, int x, int z,
                                             Map<String, String> existingProperties) {
        JTextField typeField = new JTextField(type, 18);
        JTextField spriteField = new JTextField(spriteId == null ? "" : spriteId, 18);
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(x, -1_000, 1_000, 1));
        JSpinner zSpinner = new JSpinner(new SpinnerNumberModel(z, -1_000, 1_000, 1));

        JPanel fixedForm = new JPanel(new GridLayout(0, 2, 6, 6));
        fixedForm.add(new JLabel("Typ:"));
        fixedForm.add(typeField);
        fixedForm.add(new JLabel("Sprite-ID (optional):"));
        fixedForm.add(spriteField);
        fixedForm.add(new JLabel("X:"));
        fixedForm.add(xSpinner);
        fixedForm.add(new JLabel("Z:"));
        fixedForm.add(zSpinner);

        JPanel propertiesPanel = new JPanel(new GridLayout(0, 2, 6, 6));
        propertiesPanel.setBorder(BorderFactory.createTitledBorder("Eigenschaften"));
        Map<String, JTextField> propertyFields = new LinkedHashMap<>();

        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.add(fixedForm, BorderLayout.NORTH);
        form.add(propertiesPanel, BorderLayout.CENTER);

        Runnable rebuildProperties = () -> {
            propertiesPanel.removeAll();
            propertyFields.clear();
            EntityTypeAsset schema = projectController.getEntityTypes().stream()
                .filter(candidate -> candidate.id.equals(typeField.getText().trim()))
                .findFirst().orElse(null);
            if (schema != null) {
                for (EntityPropertyDefinition property : schema.getProperties()) {
                    JTextField field = new JTextField(existingProperties.getOrDefault(property.key, ""), 16);
                    propertyFields.put(property.key, field);
                    propertiesPanel.add(new JLabel(property.key + " (" + property.type.name().toLowerCase(Locale.ROOT)
                        + (property.required ? ", erforderlich" : "") + "):"));
                    propertiesPanel.add(field);
                }
            }
            java.awt.Window window = SwingUtilities.getWindowAncestor(form);
            if (window != null) window.pack();
        };
        typeField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { rebuildProperties.run(); }

            @Override
            public void removeUpdate(DocumentEvent e) { rebuildProperties.run(); }

            @Override
            public void changedUpdate(DocumentEvent e) { rebuildProperties.run(); }
        });
        rebuildProperties.run();

        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        String selectedType = typeField.getText().trim();
        String selectedSprite = spriteField.getText().trim();
        if (selectedType.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ein Entity-Typ ist erforderlich.");
            return null;
        }
        Map<String, String> properties = new LinkedHashMap<>();
        for (Map.Entry<String, JTextField> field : propertyFields.entrySet()) {
            String value = field.getValue().getText().trim();
            if (!value.isEmpty()) properties.put(field.getKey(), value);
        }
        return new EntitySettings(selectedType, selectedSprite.isEmpty() ? null : selectedSprite,
            (Integer) xSpinner.getValue(), (Integer) zSpinner.getValue(), properties);
    }

    private LightSettings askLightSettings(String title, MapLightAsset light) {
        JSpinner x = decimalSpinner(light.x, 0.25d);
        JSpinner y = decimalSpinner(light.y, 0.25d);
        JSpinner z = decimalSpinner(light.z, 0.25d);
        JSpinner colorR = decimalSpinner(light.colorR, 0.05d);
        JSpinner colorG = decimalSpinner(light.colorG, 0.05d);
        JSpinner colorB = decimalSpinner(light.colorB, 0.05d);
        JSpinner intensity = decimalSpinner(light.intensity, 0.1d);
        JSpinner range = decimalSpinner(light.range, 0.5d);
        JCheckBox enabled = new JCheckBox("Aktiv", light.enabled);
        JCheckBox spot = new JCheckBox("Spotlicht", light.spot);
        JSpinner directionX = decimalSpinner(light.directionX, 0.1d);
        JSpinner directionY = decimalSpinner(light.directionY, 0.1d);
        JSpinner directionZ = decimalSpinner(light.directionZ, 0.1d);
        JSpinner innerAngle = decimalSpinner(light.innerAngle, 1d);
        JSpinner outerAngle = decimalSpinner(light.outerAngle, 1d);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("X:"));
        form.add(x);
        form.add(new JLabel("Y (Höhe):"));
        form.add(y);
        form.add(new JLabel("Z:"));
        form.add(z);
        form.add(new JLabel("Farbe R:"));
        form.add(colorR);
        form.add(new JLabel("Farbe G:"));
        form.add(colorG);
        form.add(new JLabel("Farbe B:"));
        form.add(colorB);
        form.add(new JLabel("Intensität:"));
        form.add(intensity);
        form.add(new JLabel("Reichweite:"));
        form.add(range);
        form.add(new JLabel());
        form.add(enabled);
        form.add(new JLabel());
        form.add(spot);
        form.add(new JLabel("Richtung X (Spot):"));
        form.add(directionX);
        form.add(new JLabel("Richtung Y (Spot):"));
        form.add(directionY);
        form.add(new JLabel("Richtung Z (Spot):"));
        form.add(directionZ);
        form.add(new JLabel("Innerer Winkel (Spot):"));
        form.add(innerAngle);
        form.add(new JLabel("Äußerer Winkel (Spot):"));
        form.add(outerAngle);
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        return new LightSettings(number(x), number(y), number(z), number(colorR), number(colorG), number(colorB),
            number(intensity), number(range), enabled.isSelected(), spot.isSelected(),
            number(directionX), number(directionY), number(directionZ), number(innerAngle), number(outerAngle));
    }

    private TransitionSettings askTransitionSettings(String title, int x, int z, String targetMapId,
                                                      int targetX, int targetZ) {
        List<MapAsset> maps = projectController.getMaps();
        String[] mapIds = maps.stream().map(m -> m.id).toArray(String[]::new);
        JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(x, -1_000, 1_000, 1));
        JSpinner zSpinner = new JSpinner(new SpinnerNumberModel(z, -1_000, 1_000, 1));
        JComboBox<String> targetMapCombo = new JComboBox<>(mapIds);
        targetMapCombo.setSelectedItem(targetMapId);
        JSpinner targetXSpinner = new JSpinner(new SpinnerNumberModel(targetX, -1_000, 1_000, 1));
        JSpinner targetZSpinner = new JSpinner(new SpinnerNumberModel(targetZ, -1_000, 1_000, 1));
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("X:"));
        form.add(xSpinner);
        form.add(new JLabel("Z:"));
        form.add(zSpinner);
        form.add(new JLabel("Zielkarte:"));
        form.add(targetMapCombo);
        form.add(new JLabel("Ziel-X:"));
        form.add(targetXSpinner);
        form.add(new JLabel("Ziel-Z:"));
        form.add(targetZSpinner);
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        String selectedTargetId = (String) targetMapCombo.getSelectedItem();
        if (selectedTargetId == null) {
            JOptionPane.showMessageDialog(this, "Bitte eine Zielkarte auswählen.");
            return null;
        }
        return new TransitionSettings((Integer) xSpinner.getValue(), (Integer) zSpinner.getValue(),
            selectedTargetId, (Integer) targetXSpinner.getValue(), (Integer) targetZSpinner.getValue());
    }

    /**
     * A trigger/action/condition can be edited into any state the engine will reject (e.g. an
     * INTERACTION trigger with no entity ID), so this loops on {@link IllegalArgumentException}
     * instead of pre-validating field-by-field in Swing - one source of truth for what's valid.
     */
    private GameEventAsset askEventSettings(String title, GameEventAsset existing) {
        JComboBox<EventTriggerAsset.Type> triggerType = new JComboBox<>(EventTriggerAsset.Type.values());
        if (existing != null) triggerType.setSelectedItem(existing.trigger.type);
        JTextField entityIdField = new JTextField(
            existing != null && existing.trigger.entityId != null ? existing.trigger.entityId : "", 14);
        JSpinner triggerX = new JSpinner(new SpinnerNumberModel(
            existing != null ? existing.trigger.x : 0, -1_000, 1_000, 1));
        JSpinner triggerZ = new JSpinner(new SpinnerNumberModel(
            existing != null ? existing.trigger.z : 0, -1_000, 1_000, 1));
        JTextField timeOfDayField = new JTextField(
            existing != null && existing.trigger.timeOfDay != null ? existing.trigger.timeOfDay : "", 14);

        JPanel triggerForm = new JPanel(new GridLayout(0, 2, 6, 6));
        triggerForm.add(new JLabel("Auslöser:"));
        triggerForm.add(triggerType);
        triggerForm.add(new JLabel("Entity-ID (Interaktion):"));
        triggerForm.add(entityIdField);
        triggerForm.add(new JLabel("X (Fläche betreten):"));
        triggerForm.add(triggerX);
        triggerForm.add(new JLabel("Z (Fläche betreten):"));
        triggerForm.add(triggerZ);
        triggerForm.add(new JLabel("Tageszeit (Zeitwechsel):"));
        triggerForm.add(timeOfDayField);

        DefaultListModel<ConditionAsset> conditionsModel = new DefaultListModel<>();
        if (existing != null) existing.getConditions().forEach(conditionsModel::addElement);
        JList<ConditionAsset> conditionsList = new JList<>(conditionsModel);
        conditionsList.setCellRenderer(labelRenderer(
            c -> c.type + " " + c.key + " " + c.comparison + " " + c.value));
        JButton addCondition = new JButton("Hinzufügen…");
        addCondition.addActionListener(e -> {
            ConditionAsset condition = askConditionSettings("Bedingung hinzufügen", null);
            if (condition != null) conditionsModel.addElement(condition);
        });
        JButton editCondition = new JButton("Bearbeiten…");
        editCondition.addActionListener(e -> {
            int index = conditionsList.getSelectedIndex();
            if (index < 0) return;
            ConditionAsset condition = askConditionSettings("Bedingung bearbeiten", conditionsModel.get(index));
            if (condition != null) conditionsModel.set(index, condition);
        });
        JButton removeCondition = new JButton("Löschen");
        removeCondition.addActionListener(e -> {
            int index = conditionsList.getSelectedIndex();
            if (index >= 0) conditionsModel.remove(index);
        });
        JPanel conditionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        conditionButtons.add(addCondition);
        conditionButtons.add(editCondition);
        conditionButtons.add(removeCondition);
        JPanel conditionsPanel = new JPanel(new BorderLayout());
        conditionsPanel.setBorder(BorderFactory.createTitledBorder("Bedingungen"));
        conditionsPanel.add(new JScrollPane(conditionsList), BorderLayout.CENTER);
        conditionsPanel.add(conditionButtons, BorderLayout.SOUTH);
        conditionsPanel.setPreferredSize(new Dimension(320, 110));

        DefaultListModel<EventActionAsset> actionsModel = new DefaultListModel<>();
        if (existing != null) existing.getActions().forEach(actionsModel::addElement);
        JList<EventActionAsset> actionsList = new JList<>(actionsModel);
        actionsList.setCellRenderer(labelRenderer(MapPanel::describeAction));
        JButton addAction = new JButton("Hinzufügen…");
        addAction.addActionListener(e -> {
            EventActionAsset action = askActionSettings("Aktion hinzufügen", null);
            if (action != null) actionsModel.addElement(action);
        });
        JButton editAction = new JButton("Bearbeiten…");
        editAction.addActionListener(e -> {
            int index = actionsList.getSelectedIndex();
            if (index < 0) return;
            EventActionAsset action = askActionSettings("Aktion bearbeiten", actionsModel.get(index));
            if (action != null) actionsModel.set(index, action);
        });
        JButton removeAction = new JButton("Löschen");
        removeAction.addActionListener(e -> {
            int index = actionsList.getSelectedIndex();
            if (index >= 0) actionsModel.remove(index);
        });
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionButtons.add(addAction);
        actionButtons.add(editAction);
        actionButtons.add(removeAction);
        JPanel actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Aktionen (mindestens eine)"));
        actionsPanel.add(new JScrollPane(actionsList), BorderLayout.CENTER);
        actionsPanel.add(actionButtons, BorderLayout.SOUTH);
        actionsPanel.setPreferredSize(new Dimension(320, 110));

        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.add(triggerForm);
        form.add(conditionsPanel);
        form.add(actionsPanel);

        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            EventTriggerAsset.Type selectedType = (EventTriggerAsset.Type) triggerType.getSelectedItem();
            String entityId = entityIdField.getText().trim();
            String timeOfDay = timeOfDayField.getText().trim();
            List<ConditionAsset> conditions = java.util.Collections.list(conditionsModel.elements());
            List<EventActionAsset> actions = java.util.Collections.list(actionsModel.elements());
            try {
                EventTriggerAsset trigger = new EventTriggerAsset(selectedType,
                    selectedType == EventTriggerAsset.Type.INTERACTION && !entityId.isEmpty() ? entityId : null,
                    (Integer) triggerX.getValue(), (Integer) triggerZ.getValue(),
                    selectedType == EventTriggerAsset.Type.TIME_CHANGE && !timeOfDay.isEmpty() ? timeOfDay : null);
                return new GameEventAsset(existing != null ? existing.instanceId : UUID.randomUUID().toString(),
                    trigger, conditions, actions);
            } catch (IllegalArgumentException e) {
                showError("Ereignis ist ungültig", e);
            }
        }
    }

    private ConditionAsset askConditionSettings(String title, ConditionAsset existing) {
        JComboBox<ConditionAsset.Type> type = new JComboBox<>(ConditionAsset.Type.values());
        if (existing != null) type.setSelectedItem(existing.type);
        JTextField key = new JTextField(existing != null ? existing.key : "", 16);
        JComboBox<ConditionAsset.Comparison> comparison = new JComboBox<>(ConditionAsset.Comparison.values());
        if (existing != null) comparison.setSelectedItem(existing.comparison);
        JTextField value = new JTextField(existing != null ? existing.value : "", 12);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Typ:"));
        form.add(type);
        form.add(new JLabel("Schlüssel:"));
        form.add(key);
        form.add(new JLabel("Vergleich:"));
        form.add(comparison);
        form.add(new JLabel("Wert:"));
        form.add(value);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            try {
                return new ConditionAsset((ConditionAsset.Type) type.getSelectedItem(), key.getText().trim(),
                    (ConditionAsset.Comparison) comparison.getSelectedItem(), value.getText().trim());
            } catch (IllegalArgumentException e) {
                showError("Bedingung ist ungültig", e);
            }
        }
    }

    private EventActionAsset askActionSettings(String title, EventActionAsset existing) {
        JComboBox<EventActionAsset.Type> type = new JComboBox<>(EventActionAsset.Type.values());
        if (existing != null) type.setSelectedItem(existing.type);
        JTextField targetId = new JTextField(
            existing != null && existing.targetId != null ? existing.targetId : "", 16);
        JTextField value = new JTextField(existing != null && existing.value != null ? existing.value : "", 12);
        JSpinner x = new JSpinner(new SpinnerNumberModel(existing != null ? existing.x : 0, -1_000, 1_000, 1));
        JSpinner z = new JSpinner(new SpinnerNumberModel(existing != null ? existing.z : 0, -1_000, 1_000, 1));
        List<MapAsset> maps = projectController.getMaps();
        String[] mapIds = maps.stream().map(m -> m.id).toArray(String[]::new);
        JComboBox<String> targetMap = new JComboBox<>(mapIds);
        if (existing != null && existing.targetMap != null) targetMap.setSelectedItem(existing.targetMap);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Typ:"));
        form.add(type);
        form.add(new JLabel("Ziel-ID (Entity/Dialog/Licht/Flag):"));
        form.add(targetId);
        form.add(new JLabel("Wert (Flag-Wert/an-aus):"));
        form.add(value);
        form.add(new JLabel("X (NPC bewegen/Kartenwechsel):"));
        form.add(x);
        form.add(new JLabel("Z (NPC bewegen/Kartenwechsel):"));
        form.add(z);
        form.add(new JLabel("Zielkarte (Kartenwechsel):"));
        form.add(targetMap);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            EventActionAsset.Type selectedType = (EventActionAsset.Type) type.getSelectedItem();
            String targetIdText = targetId.getText().trim();
            String valueText = value.getText().trim();
            String selectedTargetMap = selectedType == EventActionAsset.Type.CHANGE_MAP
                ? (String) targetMap.getSelectedItem() : null;
            try {
                return new EventActionAsset(selectedType, targetIdText.isEmpty() ? null : targetIdText,
                    valueText.isEmpty() ? null : valueText, (Integer) x.getValue(), (Integer) z.getValue(),
                    selectedTargetMap);
            } catch (IllegalArgumentException e) {
                showError("Aktion ist ungültig", e);
            }
        }
    }

    private static String describeTrigger(EventTriggerAsset trigger) {
        return switch (trigger.type) {
            case MAP_START -> "Kartenstart";
            case INTERACTION -> "Interaktion: " + trigger.entityId;
            case ENTER_AREA -> "Fläche betreten (" + trigger.x + ", " + trigger.z + ")";
            case TIME_CHANGE -> "Zeitwechsel: " + trigger.timeOfDay;
        };
    }

    private static String describeAction(EventActionAsset action) {
        return switch (action.type) {
            case START_DIALOGUE -> "Dialog starten: " + action.targetId;
            case MOVE_NPC -> "NPC bewegen: " + action.targetId + " -> (" + action.x + ", " + action.z + ")";
            case OPEN_DOOR -> "Tür: " + action.targetId + " = " + action.value;
            case CHANGE_MAP -> "Kartenwechsel: " + action.targetMap + " (" + action.x + ", " + action.z + ")";
            case SET_FLAG -> "Flag setzen: " + action.targetId + " = " + action.value;
            case TOGGLE_LIGHT -> "Licht schalten: " + action.targetId + " = " + action.value;
        };
    }

    private void selectEvent(String instanceId) {
        for (int i = 0; i < placedEventsListModel.size(); i++) {
            if (placedEventsListModel.get(i).instanceId.equals(instanceId)) {
                placedEventsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectProp(String instanceId) {
        for (int i = 0; i < placedPropsListModel.size(); i++) {
            if (placedPropsListModel.get(i).instanceId.equals(instanceId)) {
                placedPropsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectEntity(String instanceId) {
        for (int i = 0; i < placedEntitiesListModel.size(); i++) {
            if (placedEntitiesListModel.get(i).instanceId.equals(instanceId)) {
                placedEntitiesList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectLight(String instanceId) {
        for (int i = 0; i < placedLightsListModel.size(); i++) {
            if (placedLightsListModel.get(i).instanceId.equals(instanceId)) {
                placedLightsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectTransition(String instanceId) {
        for (int i = 0; i < placedTransitionsListModel.size(); i++) {
            if (placedTransitionsListModel.get(i).instanceId.equals(instanceId)) {
                placedTransitionsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private record PropTransform(float x, float z, float elevation, float rotation) {
    }

    private record EntitySettings(String type, String spriteId, int x, int z, Map<String, String> properties) {
    }

    private record LightSettings(float x, float y, float z, float colorR, float colorG, float colorB,
                                 float intensity, float range, boolean enabled, boolean spot,
                                 float directionX, float directionY, float directionZ,
                                 float innerAngle, float outerAngle) {
        MapLightAsset toAsset(String instanceId) {
            return new MapLightAsset(instanceId, x, y, z, colorR, colorG, colorB, intensity, range, enabled,
                spot, directionX, directionY, directionZ, innerAngle, outerAngle);
        }
    }

    private record TransitionSettings(int x, int z, String targetMapId, int targetX, int targetZ) {
        MapTransitionAsset toAsset(String instanceId) {
            return new MapTransitionAsset(instanceId, x, z, targetMapId, targetX, targetZ);
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

    private void updateOverlays() {
        canvas.setOverlays(terrainOverlay.isSelected(), gridOverlay.isSelected(), walkabilityOverlay.isSelected(),
            edgesOverlay.isSelected(), manualCollisionOverlay.isSelected());
    }

    private TilesetAsset findTileset(String id) {
        for (TilesetAsset tileset : projectController.getTilesets()) {
            if (tileset.id.equals(id)) return tileset;
        }
        return null;
    }

    private Image tileImage(TileEntry tile) {
        for (land.temmi.rollercoaster.editor.document.TextureAsset texture : projectController.getTextures()) {
            if (!texture.id.equals(tile.textureId)) continue;
            Path file = projectController.getProjectDirectory().resolve("sources/textures").resolve(texture.fileName);
            try {
                return ImageIO.read(file.toFile());
            } catch (IOException ignored) {
                return null;
            }
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
        queuePreview(selected.id, true);
        livePreviewTimer.stop();
        startQueuedPreview();
    }

    /** Debounces brush strokes so the preview receives only the most recent authored map state. */
    private void scheduleLivePreview(MapAsset selected) {
        if (selected == null) {
            if (!queuedPreviewReportsErrors) {
                livePreviewTimer.stop();
                queuedPreviewMapId = null;
            }
            return;
        }
        if (!livePreview.isSelected()) return;
        queuePreview(selected.id, false);
        if (!previewRequestInFlight) livePreviewTimer.restart();
    }

    private void queuePreview(String mapId, boolean reportErrors) {
        queuedPreviewMapId = mapId;
        queuedPreviewReportsErrors |= reportErrors;
    }

    private void startQueuedPreview() {
        if (previewRequestInFlight || queuedPreviewMapId == null) return;

        String mapId = queuedPreviewMapId;
        boolean reportErrors = queuedPreviewReportsErrors;
        queuedPreviewMapId = null;
        queuedPreviewReportsErrors = false;

        MapAsset selected = mapList.getSelectedValue();
        if (selected == null || !selected.id.equals(mapId)) return;
        TilesetAsset tileset = findTileset(selected.tilesetId);
        if (tileset == null) return;

        Path mapFile;
        Path modelManifestFile = null;
        Path spriteManifestFile = null;
        Path dialogueManifestFile = null;
        Path tilesetManifestFile;
        try {
            mapFile = projectController.exportMap(selected.id);
            tilesetManifestFile = projectController.exportTileset(selected.tilesetId);
            if (!selected.getProps().isEmpty()) modelManifestFile = projectController.exportModels();
            if (selected.getEntities().stream().anyMatch(entity -> entity.spriteId != null)) {
                spriteManifestFile = projectController.exportSprites();
            }
            if (!projectController.getDialogues().isEmpty()) dialogueManifestFile = projectController.exportDialogues();
        } catch (IOException e) {
            if (reportErrors) showError("Karten-Assets konnten nicht exportiert werden", e);
            startQueuedPreview();
            return;
        }
        previewRequestInFlight = true;
        previewMapRequester.showMap(mapFile.toAbsolutePath().toString(), selected.width, selected.depth,
            tilesetManifestFile.toAbsolutePath().toString(),
            modelManifestFile == null ? null : modelManifestFile.toAbsolutePath().toString(),
            spriteManifestFile == null ? null : spriteManifestFile.toAbsolutePath().toString(),
            dialogueManifestFile == null ? null : dialogueManifestFile.toAbsolutePath().toString())
            .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                previewRequestInFlight = false;
                if (reportErrors && error != null) {
                    JOptionPane.showMessageDialog(this, error.getMessage(), "Vorschau fehlgeschlagen",
                        JOptionPane.ERROR_MESSAGE);
                } else if (reportErrors && !result.success) {
                    JOptionPane.showMessageDialog(this, result.errorMessage, "Vorschau fehlgeschlagen",
                        JOptionPane.ERROR_MESSAGE);
                }
                startQueuedPreview();
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
