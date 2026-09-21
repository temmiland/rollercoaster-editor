package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.ConditionAsset;
import land.temmi.rollercoaster.editor.document.DialogueAsset;
import land.temmi.rollercoaster.editor.document.DialogueNodeAsset;
import land.temmi.rollercoaster.editor.document.DialogueResponseAsset;
import land.temmi.rollercoaster.editor.document.EntityPropertyDefinition;
import land.temmi.rollercoaster.editor.document.EntityTypeAsset;
import land.temmi.rollercoaster.editor.document.ModelAsset;
import land.temmi.rollercoaster.editor.document.SpriteAnimationAsset;
import land.temmi.rollercoaster.editor.document.SpriteAsset;
import land.temmi.rollercoaster.editor.document.SpriteDirection;
import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TilesetAsset;
import land.temmi.rollercoaster.editor.protocol.ModelBoundsResult;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Texture import and tileset authoring - the editor's asset catalog for the open project. */
final class AssetsPanel extends JPanel {
    private final ProjectController projectController;
    private final Function<String, CompletableFuture<ModelBoundsResult>> modelBoundsComputer;
    private final DefaultListModel<TextureAsset> textureListModel = new DefaultListModel<>();
    private final JList<TextureAsset> textureList = new JList<>(textureListModel);
    private final DefaultListModel<TilesetAsset> tilesetListModel = new DefaultListModel<>();
    private final JList<TilesetAsset> tilesetList = new JList<>(tilesetListModel);
    private final DefaultListModel<TileEntry> tileListModel = new DefaultListModel<>();
    private final JList<TileEntry> tileList = new JList<>(tileListModel);
    private final DefaultListModel<ModelAsset> modelListModel = new DefaultListModel<>();
    private final JList<ModelAsset> modelList = new JList<>(modelListModel);
    private final DefaultListModel<SpriteAsset> spriteListModel = new DefaultListModel<>();
    private final JList<SpriteAsset> spriteList = new JList<>(spriteListModel);
    private final DefaultListModel<DialogueAsset> dialogueListModel = new DefaultListModel<>();
    private final JList<DialogueAsset> dialogueList = new JList<>(dialogueListModel);
    private final DefaultListModel<DialogueNodeAsset> dialogueNodeListModel = new DefaultListModel<>();
    private final JList<DialogueNodeAsset> dialogueNodeList = new JList<>(dialogueNodeListModel);
    private final DefaultListModel<EntityTypeAsset> entityTypeListModel = new DefaultListModel<>();
    private final JList<EntityTypeAsset> entityTypeList = new JList<>(entityTypeListModel);
    private final DefaultListModel<EntityPropertyDefinition> entityPropertyListModel = new DefaultListModel<>();
    private final JList<EntityPropertyDefinition> entityPropertyList = new JList<>(entityPropertyListModel);
    private final Map<String, ImageIcon> textureThumbnails = new HashMap<>();
    private final JTabbedPane tabs = new JTabbedPane();

    AssetsPanel(ProjectController projectController,
               Function<String, CompletableFuture<ModelBoundsResult>> modelBoundsComputer) {
        super(new BorderLayout());
        this.projectController = projectController;
        this.modelBoundsComputer = modelBoundsComputer;
        setBorder(BorderFactory.createTitledBorder("Assets"));

        textureList.setCellRenderer(textureRenderer());
        tilesetList.setCellRenderer(labelRenderer(t -> t.id + "  (" + t.getTiles().size() + " Tiles)"));
        tileList.setCellRenderer(labelRenderer(t -> t.id + " -> " + t.textureId
            + (t.sideTextureId != null ? " / Seite: " + t.sideTextureId : "")
            + (t.walkable ? "" : "  (nicht begehbar)")));
        modelList.setCellRenderer(labelRenderer(m -> m.id + "  (" + m.fileName
            + String.format(Locale.ROOT, ", H=%.2f)", m.getHeight())));
        spriteList.setCellRenderer(labelRenderer(sprite -> sprite.id + "  (" + sprite.fileName + ", "
            + sprite.columns + "x" + sprite.rows + ", H=" + sprite.worldHeight + ")"));
        tilesetList.addListSelectionListener(e -> refreshTiles());
        dialogueList.setCellRenderer(labelRenderer(d -> d.id + "  (Start: " + d.startNodeId
            + ", " + d.getNodes().size() + " Knoten)"));
        dialogueNodeList.setCellRenderer(labelRenderer(n -> n.id + "  [" + n.speakerId + "] " + n.textId
            + (n.getResponses().isEmpty() ? "" : "  (" + n.getResponses().size() + " Antwort(en))")));
        dialogueList.addListSelectionListener(e -> refreshDialogueNodes());
        entityTypeList.setCellRenderer(labelRenderer(t -> t.id + "  (" + t.getProperties().size() + " Eigenschaft(en))"));
        entityPropertyList.setCellRenderer(labelRenderer(p -> p.key + "  (" + p.type.name().toLowerCase(Locale.ROOT)
            + (p.required ? ", erforderlich" : "") + ")"));
        entityTypeList.addListSelectionListener(e -> refreshEntityProperties());

        tabs.addTab("Texturen", buildTexturesTab());
        tabs.addTab("Tilesets", buildTilesetsTab());
        tabs.addTab("Modelle", buildModelsTab());
        tabs.addTab("Sprites", buildSpritesTab());
        tabs.addTab("Dialoge", buildDialoguesTab());
        tabs.addTab("Entity-Typen", buildEntityTypesTab());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildTexturesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(textureList), BorderLayout.CENTER);

        JButton importButton = new JButton("Importieren…");
        importButton.addActionListener(e -> onImportTexture());
        JButton removeButton = new JButton("Entfernen");
        removeButton.addActionListener(e -> onRemoveTexture());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(importButton);
        buttons.add(removeButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildTilesetsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(tilesetList), new JScrollPane(tileList));
        split.setResizeWeight(0.5);
        panel.add(split, BorderLayout.CENTER);

        JButton newTileset = new JButton("Neu…");
        newTileset.addActionListener(e -> onCreateTileset());
        JButton removeTileset = new JButton("Löschen");
        removeTileset.addActionListener(e -> onRemoveTileset());
        JButton addTile = new JButton("Tile hinzufügen…");
        addTile.addActionListener(e -> onAddTile());
        JButton removeTile = new JButton("Tile entfernen");
        removeTile.addActionListener(e -> onRemoveTile());
        JButton export = new JButton("Exportieren");
        export.addActionListener(e -> onExportTileset());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(newTileset);
        buttons.add(removeTileset);
        buttons.add(addTile);
        buttons.add(removeTile);
        buttons.add(export);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildModelsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(modelList), BorderLayout.CENTER);

        JButton importButton = new JButton("Importieren…");
        importButton.addActionListener(e -> onImportModel());
        JButton reimportButton = new JButton("Neu importieren…");
        reimportButton.addActionListener(e -> onReimportModel());
        JButton removeButton = new JButton("Entfernen");
        removeButton.addActionListener(e -> onRemoveModel());
        JButton propertiesButton = new JButton("Eigenschaften…");
        propertiesButton.addActionListener(e -> onEditModel());
        JButton export = new JButton("Exportieren");
        export.addActionListener(e -> onExportModels());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(importButton);
        buttons.add(reimportButton);
        buttons.add(removeButton);
        buttons.add(propertiesButton);
        buttons.add(export);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSpritesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(spriteList), BorderLayout.CENTER);
        JButton importButton = new JButton("Spritesheet importieren…");
        importButton.addActionListener(e -> onImportSprite());
        JButton propertiesButton = new JButton("Eigenschaften…");
        propertiesButton.addActionListener(e -> onEditSprite());
        JButton removeButton = new JButton("Entfernen");
        removeButton.addActionListener(e -> onRemoveSprite());
        JButton exportButton = new JButton("Exportieren");
        exportButton.addActionListener(e -> onExportSprites());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(importButton);
        buttons.add(propertiesButton);
        buttons.add(removeButton);
        buttons.add(exportButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDialoguesTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel dialoguesPanel = new JPanel(new BorderLayout());
        dialoguesPanel.add(new JScrollPane(dialogueList), BorderLayout.CENTER);
        JButton newDialogue = new JButton("Neu…");
        newDialogue.addActionListener(e -> onCreateDialogue());
        JButton removeDialogue = new JButton("Löschen");
        removeDialogue.addActionListener(e -> onRemoveDialogue());
        JButton exportDialogues = new JButton("Exportieren");
        exportDialogues.addActionListener(e -> onExportDialogues());
        JPanel dialogueButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dialogueButtons.add(newDialogue);
        dialogueButtons.add(removeDialogue);
        dialogueButtons.add(exportDialogues);
        dialoguesPanel.add(dialogueButtons, BorderLayout.SOUTH);

        JPanel nodesPanel = new JPanel(new BorderLayout());
        nodesPanel.setBorder(BorderFactory.createTitledBorder("Knoten"));
        nodesPanel.add(new JScrollPane(dialogueNodeList), BorderLayout.CENTER);
        JButton addNode = new JButton("Hinzufügen…");
        addNode.addActionListener(e -> onAddDialogueNode());
        JButton editNode = new JButton("Bearbeiten…");
        editNode.addActionListener(e -> onEditDialogueNode());
        JButton removeNode = new JButton("Entfernen");
        removeNode.addActionListener(e -> onRemoveDialogueNode());
        JButton setStartNode = new JButton("Als Start setzen");
        setStartNode.addActionListener(e -> onSetDialogueStartNode());
        JPanel nodeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nodeButtons.add(addNode);
        nodeButtons.add(editNode);
        nodeButtons.add(removeNode);
        nodeButtons.add(setStartNode);
        nodesPanel.add(nodeButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dialoguesPanel, nodesPanel);
        split.setResizeWeight(0.4);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEntityTypesTab() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel typesPanel = new JPanel(new BorderLayout());
        typesPanel.add(new JScrollPane(entityTypeList), BorderLayout.CENTER);
        JButton newType = new JButton("Neu…");
        newType.addActionListener(e -> onCreateEntityType());
        JButton removeType = new JButton("Löschen");
        removeType.addActionListener(e -> onRemoveEntityType());
        JPanel typeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typeButtons.add(newType);
        typeButtons.add(removeType);
        typesPanel.add(typeButtons, BorderLayout.SOUTH);

        JPanel propertiesPanel = new JPanel(new BorderLayout());
        propertiesPanel.setBorder(BorderFactory.createTitledBorder("Eigenschaften"));
        propertiesPanel.add(new JScrollPane(entityPropertyList), BorderLayout.CENTER);
        JButton addProperty = new JButton("Hinzufügen…");
        addProperty.addActionListener(e -> onAddEntityProperty());
        JButton editProperty = new JButton("Bearbeiten…");
        editProperty.addActionListener(e -> onEditEntityProperty());
        JButton removeProperty = new JButton("Entfernen");
        removeProperty.addActionListener(e -> onRemoveEntityProperty());
        JPanel propertyButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        propertyButtons.add(addProperty);
        propertyButtons.add(editProperty);
        propertyButtons.add(removeProperty);
        propertiesPanel.add(propertyButtons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, typesPanel, propertiesPanel);
        split.setResizeWeight(0.4);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    /** Jumps to a project-level asset by id, switching tabs if needed - the Assets side of a
     * clickable diagnostic line (MapPanel.trySelectPlacement covers per-map placements instead). */
    boolean trySelect(String id) {
        for (int i = 0; i < textureListModel.size(); i++) {
            if (textureListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(0);
                textureList.setSelectedIndex(i);
                return true;
            }
        }
        for (int i = 0; i < tilesetListModel.size(); i++) {
            if (tilesetListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(1);
                tilesetList.setSelectedIndex(i);
                return true;
            }
        }
        for (int i = 0; i < modelListModel.size(); i++) {
            if (modelListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(2);
                modelList.setSelectedIndex(i);
                return true;
            }
        }
        for (int i = 0; i < spriteListModel.size(); i++) {
            if (spriteListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(3);
                spriteList.setSelectedIndex(i);
                return true;
            }
        }
        for (int i = 0; i < dialogueListModel.size(); i++) {
            if (dialogueListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(4);
                dialogueList.setSelectedIndex(i);
                return true;
            }
        }
        for (int i = 0; i < entityTypeListModel.size(); i++) {
            if (entityTypeListModel.get(i).id.equals(id)) {
                tabs.setSelectedIndex(5);
                entityTypeList.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    void refresh() {
        boolean open = projectController.isOpen();
        textureList.setEnabled(open);
        tilesetList.setEnabled(open);
        tileList.setEnabled(open);
        modelList.setEnabled(open);
        spriteList.setEnabled(open);
        dialogueList.setEnabled(open);
        dialogueNodeList.setEnabled(open);
        entityTypeList.setEnabled(open);
        entityPropertyList.setEnabled(open);

        TilesetAsset selectedTileset = tilesetList.getSelectedValue();
        ModelAsset selectedModel = modelList.getSelectedValue();
        SpriteAsset selectedSprite = spriteList.getSelectedValue();
        DialogueAsset selectedDialogue = dialogueList.getSelectedValue();
        EntityTypeAsset selectedEntityType = entityTypeList.getSelectedValue();

        textureListModel.clear();
        if (open) projectController.getTextures().stream().sorted(Comparator.comparing(t -> t.id))
            .forEach(textureListModel::addElement);

        tilesetListModel.clear();
        if (open) projectController.getTilesets().stream().sorted(Comparator.comparing(t -> t.id))
            .forEach(tilesetListModel::addElement);

        modelListModel.clear();
        if (open) projectController.getModels().stream().sorted(Comparator.comparing(m -> m.id))
            .forEach(modelListModel::addElement);

        spriteListModel.clear();
        if (open) projectController.getSprites().stream().sorted(Comparator.comparing(sprite -> sprite.id))
            .forEach(spriteListModel::addElement);

        dialogueListModel.clear();
        if (open) projectController.getDialogues().stream().sorted(Comparator.comparing(d -> d.id))
            .forEach(dialogueListModel::addElement);

        entityTypeListModel.clear();
        if (open) projectController.getEntityTypes().stream().sorted(Comparator.comparing(t -> t.id))
            .forEach(entityTypeListModel::addElement);

        if (selectedTileset != null) {
            for (int i = 0; i < tilesetListModel.size(); i++) {
                if (tilesetListModel.get(i).id.equals(selectedTileset.id)) {
                    tilesetList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (selectedModel != null) {
            for (int i = 0; i < modelListModel.size(); i++) {
                if (modelListModel.get(i).id.equals(selectedModel.id)) {
                    modelList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (selectedSprite != null) {
            for (int i = 0; i < spriteListModel.size(); i++) {
                if (spriteListModel.get(i).id.equals(selectedSprite.id)) {
                    spriteList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (selectedDialogue != null) {
            for (int i = 0; i < dialogueListModel.size(); i++) {
                if (dialogueListModel.get(i).id.equals(selectedDialogue.id)) {
                    dialogueList.setSelectedIndex(i);
                    break;
                }
            }
        }
        if (selectedEntityType != null) {
            for (int i = 0; i < entityTypeListModel.size(); i++) {
                if (entityTypeListModel.get(i).id.equals(selectedEntityType.id)) {
                    entityTypeList.setSelectedIndex(i);
                    break;
                }
            }
        }
        refreshTiles();
        refreshDialogueNodes();
        refreshEntityProperties();
    }

    private void refreshTiles() {
        tileListModel.clear();
        TilesetAsset selected = tilesetList.getSelectedValue();
        if (selected != null) selected.getTiles().stream().sorted(Comparator.comparing(t -> t.id))
            .forEach(tileListModel::addElement);
    }

    private void refreshDialogueNodes() {
        dialogueNodeListModel.clear();
        DialogueAsset selected = dialogueList.getSelectedValue();
        if (selected != null) selected.getNodes().forEach(dialogueNodeListModel::addElement);
    }

    private void refreshEntityProperties() {
        entityPropertyListModel.clear();
        EntityTypeAsset selected = entityTypeList.getSelectedValue();
        if (selected != null) selected.getProperties().forEach(entityPropertyListModel::addElement);
    }

    private void onImportTexture() {
        if (!projectController.isOpen()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Textur importieren");
        chooser.setFileFilter(new FileNameExtensionFilter("Bilder", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        String id = JOptionPane.showInputDialog(this, "Textur-ID:", stripExtension(file.getName()));
        if (id == null || id.trim().isEmpty()) return;
        try {
            projectController.importTexture(file.toPath(), id.trim());
            refresh();
        } catch (IOException e) {
            showError("Textur konnte nicht importiert werden", e);
        }
    }

    private void onRemoveTexture() {
        TextureAsset selected = textureList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.removeTexture(selected);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Textur kann nicht entfernt werden", e);
        }
    }

    private void onCreateTileset() {
        if (!projectController.isOpen()) return;
        String id = JOptionPane.showInputDialog(this, "Tileset-ID:", "Neues Tileset", JOptionPane.PLAIN_MESSAGE);
        if (id == null || id.trim().isEmpty()) return;
        try {
            projectController.createTileset(id.trim());
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Tileset konnte nicht angelegt werden", e);
        }
    }

    private void onRemoveTileset() {
        TilesetAsset selected = tilesetList.getSelectedValue();
        if (selected == null) return;
        projectController.removeTileset(selected);
        refresh();
    }

    private void onAddTile() {
        TilesetAsset tileset = tilesetList.getSelectedValue();
        if (tileset == null) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst ein Tileset auswählen.");
            return;
        }
        List<TextureAsset> textures = projectController.getTextures();
        if (textures.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst eine Textur importieren.");
            return;
        }
        TextureAsset texture = (TextureAsset) JOptionPane.showInputDialog(this, "Textur (Oberseite):", "Tile hinzufügen",
            JOptionPane.PLAIN_MESSAGE, null, textures.toArray(), textures.get(0));
        if (texture == null) return;
        String id = JOptionPane.showInputDialog(this, "Tile-ID:", texture.id);
        if (id == null || id.trim().isEmpty()) return;

        String sideTextureId = null;
        boolean useOwnSide = JOptionPane.showConfirmDialog(this, "Eigene Seitentextur verwenden?", "Tile hinzufügen",
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        if (useOwnSide) {
            TextureAsset sideTexture = (TextureAsset) JOptionPane.showInputDialog(this, "Textur (Seite):",
                "Tile hinzufügen", JOptionPane.PLAIN_MESSAGE, null, textures.toArray(), texture);
            if (sideTexture == null) return;
            sideTextureId = sideTexture.id;
        }

        boolean walkable = JOptionPane.showConfirmDialog(this, "Begehbar?", "Tile hinzufügen",
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        try {
            projectController.addTile(tileset.id, id.trim(), texture.id, sideTextureId, walkable);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Tile konnte nicht hinzugefügt werden", e);
        }
    }

    private void onRemoveTile() {
        TilesetAsset tileset = tilesetList.getSelectedValue();
        TileEntry tile = tileList.getSelectedValue();
        if (tileset == null || tile == null) return;
        projectController.removeTile(tileset.id, tile);
        refresh();
    }

    private void onExportTileset() {
        TilesetAsset tileset = tilesetList.getSelectedValue();
        if (tileset == null) return;
        try {
            projectController.exportTileset(tileset.id);
            JOptionPane.showMessageDialog(this, "Tileset '" + tileset.id + "' exportiert.");
        } catch (IOException e) {
            showError("Tileset konnte nicht exportiert werden", e);
        }
    }

    private void onImportModel() {
        if (!projectController.isOpen()) return;
        ModelFiles files = chooseModelFiles("Modell importieren (Hauptdatei + Abhängigkeiten wie .bin zusammen auswählen)");
        if (files == null) return;

        String id = JOptionPane.showInputDialog(this, "Modell-ID:", stripExtension(files.primary.getName()));
        if (id == null || id.trim().isEmpty()) return;

        String finalId = id.trim();
        modelBoundsComputer.apply(files.primary.getAbsolutePath()).whenComplete((result, error) ->
            SwingUtilities.invokeLater(() -> onModelBoundsComputed(files, finalId, result, error)));
    }

    private void onReimportModel() {
        ModelAsset model = modelList.getSelectedValue();
        if (model == null) {
            JOptionPane.showMessageDialog(this, "Bitte zuerst ein Modell auswählen.");
            return;
        }
        ModelFiles files = chooseModelFiles("Modell neu importieren (Hauptdatei + Abhängigkeiten zusammen auswählen)");
        if (files == null) return;
        modelBoundsComputer.apply(files.primary.getAbsolutePath()).whenComplete((result, error) ->
            SwingUtilities.invokeLater(() -> onModelReimportBoundsComputed(model, files, result, error)));
    }

    private ModelFiles chooseModelFiles(String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("3D-Modelle und Abhängigkeiten", "gltf", "glb", "bin"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;

        File[] selected = chooser.getSelectedFiles();
        File primary = null;
        List<Path> dependencies = new ArrayList<>();
        for (File file : selected) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gltf") || lower.endsWith(".glb")) {
                if (primary != null) {
                    JOptionPane.showMessageDialog(this, "Bitte nur eine .gltf- oder .glb-Hauptdatei auswählen.");
                    return null;
                }
                primary = file;
            } else {
                dependencies.add(file.toPath());
            }
        }
        if (primary == null) {
            JOptionPane.showMessageDialog(this, "Bitte eine .gltf- oder .glb-Datei auswählen.");
            return null;
        }
        return new ModelFiles(primary, dependencies);
    }

    private void onModelBoundsComputed(ModelFiles files, String id,
                                       ModelBoundsResult result, Throwable error) {
        if (error != null) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Modell konnte nicht geladen werden",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!result.success) {
            JOptionPane.showMessageDialog(this, result.errorMessage, "Modell konnte nicht geladen werden",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            projectController.importModel(files.primary.toPath(), files.dependencies, id,
                result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ);
            refresh();
        } catch (IOException e) {
            showError("Modell konnte nicht importiert werden", e);
        }
    }

    private void onModelReimportBoundsComputed(ModelAsset previous, ModelFiles files,
                                                ModelBoundsResult result, Throwable error) {
        if (error != null) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Modell konnte nicht geladen werden",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!result.success) {
            JOptionPane.showMessageDialog(this, result.errorMessage, "Modell konnte nicht geladen werden",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            projectController.reimportModel(previous, files.primary.toPath(), files.dependencies,
                result.minX, result.minY, result.minZ, result.maxX, result.maxY, result.maxZ);
            refresh();
        } catch (IOException | IllegalArgumentException e) {
            showError("Modell konnte nicht neu importiert werden", e);
        }
    }

    private void onRemoveModel() {
        ModelAsset selected = modelList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.removeModel(selected);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Modell kann nicht entfernt werden", e);
        }
    }

    private void onEditModel() {
        ModelAsset model = modelList.getSelectedValue();
        if (model == null) return;
        ModelSettings settings = askModelSettings(model);
        if (settings == null) return;
        try {
            projectController.updateModel(model, settings.offsetX, settings.offsetY, settings.offsetZ, settings.scale,
                settings.collisionMinX, settings.collisionMaxX, settings.collisionMinZ, settings.collisionMaxZ,
                settings.alignToSlope, settings.walkable, settings.walkHeight);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Modelleigenschaften konnten nicht geändert werden", e);
        }
    }

    private ModelSettings askModelSettings(ModelAsset model) {
        JSpinner offsetX = decimalSpinner(model.offsetX, 0.1d);
        JSpinner offsetY = decimalSpinner(model.offsetY, 0.1d);
        JSpinner offsetZ = decimalSpinner(model.offsetZ, 0.1d);
        JSpinner scale = decimalSpinner(model.scale, 0.1d);
        JSpinner collisionMinX = integerSpinner(model.collisionMinX);
        JSpinner collisionMaxX = integerSpinner(model.collisionMaxX);
        JSpinner collisionMinZ = integerSpinner(model.collisionMinZ);
        JSpinner collisionMaxZ = integerSpinner(model.collisionMaxZ);
        JCheckBox alignToSlope = new JCheckBox("Am Hang ausrichten", model.alignToSlope);
        JCheckBox walkable = new JCheckBox("Begehbare Oberfläche", model.walkable);
        JSpinner walkHeight = decimalSpinner(model.walkHeight, 0.1d);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(form, "Bounds:", String.format(Locale.ROOT, "X %.2f…%.2f, Y %.2f…%.2f, Z %.2f…%.2f",
            model.boundsMinX, model.boundsMaxX, model.boundsMinY, model.boundsMaxY, model.boundsMinZ, model.boundsMaxZ));
        addRow(form, "Höhe:", String.format(Locale.ROOT, "%.2f", model.getHeight()));
        addRow(form, "Anker X:", offsetX);
        addRow(form, "Anker Y:", offsetY);
        addRow(form, "Anker Z:", offsetZ);
        addRow(form, "Skalierung:", scale);
        addRow(form, "Kollision min. X:", collisionMinX);
        addRow(form, "Kollision max. X:", collisionMaxX);
        addRow(form, "Kollision min. Z:", collisionMinZ);
        addRow(form, "Kollision max. Z:", collisionMaxZ);
        addRow(form, "Hangausrichtung:", alignToSlope);
        addRow(form, "Begehbar:", walkable);
        addRow(form, "Laufhöhe:", walkHeight);

        if (JOptionPane.showConfirmDialog(this, form, "Modell: " + model.id,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        return new ModelSettings(number(offsetX), number(offsetY), number(offsetZ), number(scale),
            integer(collisionMinX), integer(collisionMaxX), integer(collisionMinZ), integer(collisionMaxZ),
            alignToSlope.isSelected(), walkable.isSelected(), number(walkHeight));
    }

    private static void addRow(JPanel form, String label, Object component) {
        form.add(new JLabel(label));
        if (component instanceof java.awt.Component) form.add((java.awt.Component) component);
        else form.add(new JLabel(String.valueOf(component)));
    }

    private static JSpinner decimalSpinner(float value, double step) {
        return new JSpinner(new SpinnerNumberModel((double) value, -10_000d, 10_000d, step));
    }

    private static JSpinner integerSpinner(int value) {
        return new JSpinner(new SpinnerNumberModel(value, -1_024, 1_024, 1));
    }

    private static float number(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }

    private static int integer(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private record ModelSettings(float offsetX, float offsetY, float offsetZ, float scale,
                                 int collisionMinX, int collisionMaxX, int collisionMinZ, int collisionMaxZ,
                                 boolean alignToSlope, boolean walkable, float walkHeight) {
    }

    private record SpriteSettings(int columns, int rows, float worldHeight, float frameDuration,
                                  float footOffset, Map<SpriteDirection, SpriteAnimationAsset> directions) {
        private SpriteAsset toAsset(String id, String fileName) {
            return new SpriteAsset(id, fileName, columns, rows, worldHeight, frameDuration, footOffset, directions);
        }
    }

    private void onExportModels() {
        try {
            projectController.exportModels();
            JOptionPane.showMessageDialog(this, "Modelle exportiert.");
        } catch (IOException e) {
            showError("Modelle konnten nicht exportiert werden", e);
        }
    }

    private void onImportSprite() {
        if (!projectController.isOpen()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Spritesheet importieren");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG-Bilder", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        String id = JOptionPane.showInputDialog(this, "Sprite-ID:", stripExtension(file.getName()));
        if (id == null || id.trim().isEmpty()) return;
        SpriteSettings settings = askSpriteSettings("Spritesheet einrichten", null);
        if (settings == null) return;
        try {
            SpriteAsset asset = settings.toAsset(id.trim(), file.getName());
            projectController.importSprite(file.toPath(), asset);
            refresh();
        } catch (IOException | IllegalArgumentException e) {
            showError("Spritesheet konnte nicht importiert werden", e);
        }
    }

    private void onEditSprite() {
        SpriteAsset previous = spriteList.getSelectedValue();
        if (previous == null) return;
        SpriteSettings settings = askSpriteSettings("Sprite-Eigenschaften", previous);
        if (settings == null) return;
        try {
            projectController.updateSprite(previous, settings.toAsset(previous.id, previous.fileName));
            refresh();
        } catch (IOException | IllegalArgumentException e) {
            showError("Sprite konnte nicht geändert werden", e);
        }
    }

    private void onRemoveSprite() {
        SpriteAsset selected = spriteList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.removeSprite(selected);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Sprite kann nicht entfernt werden", e);
        }
    }

    private void onExportSprites() {
        try {
            projectController.exportSprites();
            JOptionPane.showMessageDialog(this, "Sprites exportiert.");
        } catch (IOException | IllegalArgumentException e) {
            showError("Sprites konnten nicht exportiert werden", e);
        }
    }

    private void onCreateDialogue() {
        if (!projectController.isOpen()) return;
        String id = JOptionPane.showInputDialog(this, "Dialog-ID:", "Neuer Dialog", JOptionPane.PLAIN_MESSAGE);
        if (id == null || id.trim().isEmpty()) return;
        DialogueNodeAsset firstNode = askDialogueNodeSettings("Ersten Knoten anlegen", null, List.of());
        if (firstNode == null) return;
        try {
            DialogueAsset dialogue = new DialogueAsset(id.trim(), firstNode.id, List.of(firstNode));
            projectController.registerDialogue(dialogue);
            refresh();
            selectDialogue(dialogue.id);
        } catch (IllegalArgumentException e) {
            showError("Dialog konnte nicht angelegt werden", e);
        }
    }

    private void onRemoveDialogue() {
        DialogueAsset selected = dialogueList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.removeDialogue(selected);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Dialog kann nicht entfernt werden", e);
        }
    }

    private void onExportDialogues() {
        try {
            projectController.exportDialogues();
            JOptionPane.showMessageDialog(this, "Dialoge exportiert.");
        } catch (IOException e) {
            showError("Dialoge konnten nicht exportiert werden", e);
        }
    }

    private void onAddDialogueNode() {
        DialogueAsset dialogue = dialogueList.getSelectedValue();
        if (dialogue == null) return;
        List<String> existingIds = dialogue.getNodes().stream().map(n -> n.id).toList();
        DialogueNodeAsset node = askDialogueNodeSettings("Knoten hinzufügen", null, existingIds);
        if (node == null) return;
        List<DialogueNodeAsset> nodes = new ArrayList<>(dialogue.getNodes());
        nodes.add(node);
        try {
            DialogueAsset replacement = new DialogueAsset(dialogue.id, dialogue.startNodeId, nodes);
            projectController.updateDialogue(dialogue, replacement);
            refresh();
            selectDialogue(dialogue.id);
        } catch (IllegalArgumentException e) {
            showError("Knoten konnte nicht hinzugefügt werden", e);
        }
    }

    private void onEditDialogueNode() {
        DialogueAsset dialogue = dialogueList.getSelectedValue();
        DialogueNodeAsset node = dialogueNodeList.getSelectedValue();
        if (dialogue == null || node == null) return;
        List<String> otherIds = dialogue.getNodes().stream().map(n -> n.id)
            .filter(id -> !id.equals(node.id)).toList();
        DialogueNodeAsset edited = askDialogueNodeSettings("Knoten bearbeiten", node, otherIds);
        if (edited == null) return;
        List<DialogueNodeAsset> nodes = new ArrayList<>();
        for (DialogueNodeAsset existing : dialogue.getNodes()) {
            nodes.add(existing.id.equals(node.id) ? edited : existing);
        }
        try {
            DialogueAsset replacement = new DialogueAsset(dialogue.id, dialogue.startNodeId, nodes);
            projectController.updateDialogue(dialogue, replacement);
            refresh();
            selectDialogue(dialogue.id);
        } catch (IllegalArgumentException e) {
            showError("Knoten konnte nicht bearbeitet werden", e);
        }
    }

    private void onRemoveDialogueNode() {
        DialogueAsset dialogue = dialogueList.getSelectedValue();
        DialogueNodeAsset node = dialogueNodeList.getSelectedValue();
        if (dialogue == null || node == null) return;
        List<DialogueNodeAsset> nodes = new ArrayList<>(dialogue.getNodes());
        nodes.removeIf(n -> n.id.equals(node.id));
        try {
            DialogueAsset replacement = new DialogueAsset(dialogue.id, dialogue.startNodeId, nodes);
            projectController.updateDialogue(dialogue, replacement);
            refresh();
            selectDialogue(dialogue.id);
        } catch (IllegalArgumentException e) {
            showError("Knoten kann nicht entfernt werden", e);
        }
    }

    private void onSetDialogueStartNode() {
        DialogueAsset dialogue = dialogueList.getSelectedValue();
        DialogueNodeAsset node = dialogueNodeList.getSelectedValue();
        if (dialogue == null || node == null) return;
        try {
            DialogueAsset replacement = new DialogueAsset(dialogue.id, node.id, dialogue.getNodes());
            projectController.updateDialogue(dialogue, replacement);
            refresh();
            selectDialogue(dialogue.id);
        } catch (IllegalArgumentException e) {
            showError("Startknoten konnte nicht gesetzt werden", e);
        }
    }

    private void selectDialogue(String id) {
        for (int i = 0; i < dialogueListModel.size(); i++) {
            if (dialogueListModel.get(i).id.equals(id)) {
                dialogueList.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * A response may target this node itself (a loop) as well as any of its siblings, so the ID
     * field - fixed for an edit, freshly typed for a new node - is folded into the target list too.
     */
    private DialogueNodeAsset askDialogueNodeSettings(String title, DialogueNodeAsset existing, List<String> otherNodeIds) {
        JTextField idField = new JTextField(existing != null ? existing.id : "", 16);
        idField.setEditable(existing == null);
        JTextField speakerField = new JTextField(existing != null ? existing.speakerId : "", 16);
        JTextField textIdField = new JTextField(existing != null ? existing.textId : "", 20);
        JTextField portraitField = new JTextField(
            existing != null && existing.portrait != null ? existing.portrait : "", 16);

        JPanel fieldsForm = new JPanel(new GridLayout(0, 2, 6, 6));
        fieldsForm.add(new JLabel("Knoten-ID:"));
        fieldsForm.add(idField);
        fieldsForm.add(new JLabel("Sprecher-ID:"));
        fieldsForm.add(speakerField);
        fieldsForm.add(new JLabel("Text-ID:"));
        fieldsForm.add(textIdField);
        fieldsForm.add(new JLabel("Porträt (optional):"));
        fieldsForm.add(portraitField);

        DefaultListModel<DialogueResponseAsset> responsesModel = new DefaultListModel<>();
        if (existing != null) existing.getResponses().forEach(responsesModel::addElement);
        JList<DialogueResponseAsset> responsesList = new JList<>(responsesModel);
        responsesList.setCellRenderer(labelRenderer(
            r -> r.textId + (r.targetNodeId == null ? "  (Ende)" : "  -> " + r.targetNodeId)));
        JButton addResponse = new JButton("Hinzufügen…");
        addResponse.addActionListener(e -> {
            DialogueResponseAsset response = askDialogueResponseSettings("Antwort hinzufügen", null,
                availableTargets(idField, otherNodeIds));
            if (response != null) responsesModel.addElement(response);
        });
        JButton editResponse = new JButton("Bearbeiten…");
        editResponse.addActionListener(e -> {
            int index = responsesList.getSelectedIndex();
            if (index < 0) return;
            DialogueResponseAsset response = askDialogueResponseSettings("Antwort bearbeiten",
                responsesModel.get(index), availableTargets(idField, otherNodeIds));
            if (response != null) responsesModel.set(index, response);
        });
        JButton removeResponse = new JButton("Löschen");
        removeResponse.addActionListener(e -> {
            int index = responsesList.getSelectedIndex();
            if (index >= 0) responsesModel.remove(index);
        });
        JPanel responseButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responseButtons.add(addResponse);
        responseButtons.add(editResponse);
        responseButtons.add(removeResponse);
        JPanel responsesPanel = new JPanel(new BorderLayout());
        responsesPanel.setBorder(BorderFactory.createTitledBorder("Antworten (leer = Text endet automatisch)"));
        responsesPanel.add(new JScrollPane(responsesList), BorderLayout.CENTER);
        responsesPanel.add(responseButtons, BorderLayout.SOUTH);
        responsesPanel.setPreferredSize(new java.awt.Dimension(360, 120));

        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.add(fieldsForm);
        form.add(responsesPanel);

        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            String id = idField.getText().trim();
            String speaker = speakerField.getText().trim();
            String textId = textIdField.getText().trim();
            String portrait = portraitField.getText().trim();
            List<DialogueResponseAsset> responses = java.util.Collections.list(responsesModel.elements());
            try {
                return new DialogueNodeAsset(id, speaker, textId, portrait.isEmpty() ? null : portrait, responses);
            } catch (IllegalArgumentException e) {
                showError("Knoten ist ungültig", e);
            }
        }
    }

    private static List<String> availableTargets(JTextField idField, List<String> otherNodeIds) {
        List<String> targets = new ArrayList<>(otherNodeIds);
        String currentId = idField.getText().trim();
        if (!currentId.isEmpty() && !targets.contains(currentId)) targets.add(currentId);
        return targets;
    }

    private DialogueResponseAsset askDialogueResponseSettings(String title, DialogueResponseAsset existing,
                                                               List<String> availableNodeIds) {
        JTextField textIdField = new JTextField(existing != null ? existing.textId : "", 20);
        String[] targets = new String[availableNodeIds.size() + 1];
        targets[0] = "(Ende)";
        for (int i = 0; i < availableNodeIds.size(); i++) targets[i + 1] = availableNodeIds.get(i);
        JComboBox<String> targetCombo = new JComboBox<>(targets);
        if (existing != null && existing.targetNodeId != null) targetCombo.setSelectedItem(existing.targetNodeId);

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
        JButton removeCondition = new JButton("Löschen");
        removeCondition.addActionListener(e -> {
            int index = conditionsList.getSelectedIndex();
            if (index >= 0) conditionsModel.remove(index);
        });
        JPanel conditionButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        conditionButtons.add(addCondition);
        conditionButtons.add(removeCondition);
        JPanel conditionsPanel = new JPanel(new BorderLayout());
        conditionsPanel.setBorder(BorderFactory.createTitledBorder("Bedingungen"));
        conditionsPanel.add(new JScrollPane(conditionsList), BorderLayout.CENTER);
        conditionsPanel.add(conditionButtons, BorderLayout.SOUTH);
        conditionsPanel.setPreferredSize(new java.awt.Dimension(320, 90));

        JPanel fieldsForm = new JPanel(new GridLayout(0, 2, 6, 6));
        fieldsForm.add(new JLabel("Text-ID:"));
        fieldsForm.add(textIdField);
        fieldsForm.add(new JLabel("Ziel-Knoten:"));
        fieldsForm.add(targetCombo);

        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.add(fieldsForm);
        form.add(conditionsPanel);

        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            String textId = textIdField.getText().trim();
            String target = (String) targetCombo.getSelectedItem();
            List<ConditionAsset> conditions = java.util.Collections.list(conditionsModel.elements());
            try {
                return new DialogueResponseAsset(textId, "(Ende)".equals(target) ? null : target, conditions);
            } catch (IllegalArgumentException e) {
                showError("Antwort ist ungültig", e);
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

    private void onCreateEntityType() {
        if (!projectController.isOpen()) return;
        String id = JOptionPane.showInputDialog(this, "Entity-Typ-ID:", "Neuer Entity-Typ", JOptionPane.PLAIN_MESSAGE);
        if (id == null || id.trim().isEmpty()) return;
        try {
            EntityTypeAsset entityType = new EntityTypeAsset(id.trim(), List.of());
            projectController.registerEntityType(entityType);
            refresh();
            selectEntityType(entityType.id);
        } catch (IllegalArgumentException e) {
            showError("Entity-Typ konnte nicht angelegt werden", e);
        }
    }

    private void onRemoveEntityType() {
        EntityTypeAsset selected = entityTypeList.getSelectedValue();
        if (selected == null) return;
        try {
            projectController.removeEntityType(selected);
            refresh();
        } catch (IllegalArgumentException e) {
            showError("Entity-Typ kann nicht entfernt werden", e);
        }
    }

    private void onAddEntityProperty() {
        EntityTypeAsset entityType = entityTypeList.getSelectedValue();
        if (entityType == null) return;
        EntityPropertyDefinition property = askEntityPropertySettings("Eigenschaft hinzufügen", null);
        if (property == null) return;
        List<EntityPropertyDefinition> properties = new ArrayList<>(entityType.getProperties());
        properties.add(property);
        try {
            EntityTypeAsset replacement = new EntityTypeAsset(entityType.id, properties);
            projectController.updateEntityType(entityType, replacement);
            refresh();
            selectEntityType(entityType.id);
        } catch (IllegalArgumentException e) {
            showError("Eigenschaft konnte nicht hinzugefügt werden", e);
        }
    }

    private void onEditEntityProperty() {
        EntityTypeAsset entityType = entityTypeList.getSelectedValue();
        EntityPropertyDefinition property = entityPropertyList.getSelectedValue();
        if (entityType == null || property == null) return;
        EntityPropertyDefinition edited = askEntityPropertySettings("Eigenschaft bearbeiten", property);
        if (edited == null) return;
        List<EntityPropertyDefinition> properties = new ArrayList<>();
        for (EntityPropertyDefinition existing : entityType.getProperties()) {
            properties.add(existing.key.equals(property.key) ? edited : existing);
        }
        try {
            EntityTypeAsset replacement = new EntityTypeAsset(entityType.id, properties);
            projectController.updateEntityType(entityType, replacement);
            refresh();
            selectEntityType(entityType.id);
        } catch (IllegalArgumentException e) {
            showError("Eigenschaft konnte nicht bearbeitet werden", e);
        }
    }

    private void onRemoveEntityProperty() {
        EntityTypeAsset entityType = entityTypeList.getSelectedValue();
        EntityPropertyDefinition property = entityPropertyList.getSelectedValue();
        if (entityType == null || property == null) return;
        List<EntityPropertyDefinition> properties = new ArrayList<>(entityType.getProperties());
        properties.removeIf(existing -> existing.key.equals(property.key));
        try {
            EntityTypeAsset replacement = new EntityTypeAsset(entityType.id, properties);
            projectController.updateEntityType(entityType, replacement);
            refresh();
            selectEntityType(entityType.id);
        } catch (IllegalArgumentException e) {
            showError("Eigenschaft kann nicht entfernt werden", e);
        }
    }

    private void selectEntityType(String id) {
        for (int i = 0; i < entityTypeListModel.size(); i++) {
            if (entityTypeListModel.get(i).id.equals(id)) {
                entityTypeList.setSelectedIndex(i);
                return;
            }
        }
    }

    private EntityPropertyDefinition askEntityPropertySettings(String title, EntityPropertyDefinition existing) {
        JTextField key = new JTextField(existing != null ? existing.key : "", 16);
        key.setEditable(existing == null);
        JComboBox<EntityPropertyDefinition.Type> type = new JComboBox<>(EntityPropertyDefinition.Type.values());
        if (existing != null) type.setSelectedItem(existing.type);
        JCheckBox required = new JCheckBox("Erforderlich", existing != null && existing.required);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Schlüssel:"));
        form.add(key);
        form.add(new JLabel("Typ:"));
        form.add(type);
        form.add(new JLabel());
        form.add(required);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
            try {
                return new EntityPropertyDefinition(key.getText().trim(),
                    (EntityPropertyDefinition.Type) type.getSelectedItem(), required.isSelected());
            } catch (IllegalArgumentException e) {
                showError("Eigenschaft ist ungültig", e);
            }
        }
    }

    private SpriteSettings askSpriteSettings(String title, SpriteAsset existing) {
        JSpinner columns = new JSpinner(new SpinnerNumberModel(existing == null ? 1 : existing.columns, 1, 1_024, 1));
        JSpinner rows = new JSpinner(new SpinnerNumberModel(existing == null ? 1 : existing.rows, 1, 1_024, 1));
        JSpinner height = decimalSpinner(existing == null ? 1f : existing.worldHeight, 0.1d);
        JSpinner duration = decimalSpinner(existing == null ? 0.15f : existing.frameDuration, 0.01d);
        JSpinner footOffset = new JSpinner(new SpinnerNumberModel(
            (double) (existing == null ? 0f : existing.footOffset), 0d, 0.99d, 0.01d));
        Map<SpriteDirection, JTextField> idleFields = new EnumMap<>(SpriteDirection.class);
        Map<SpriteDirection, JTextField> walkFields = new EnumMap<>(SpriteDirection.class);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(form, "Spalten:", columns);
        addRow(form, "Zeilen:", rows);
        addRow(form, "Welt-Höhe:", height);
        addRow(form, "Frame-Dauer (s):", duration);
        addRow(form, "Fußversatz (0–<1):", footOffset);
        for (SpriteDirection direction : SpriteDirection.values()) {
            SpriteAnimationAsset animation = existing == null ? defaultAnimation() : existing.direction(direction);
            JTextField idle = new JTextField(String.valueOf(animation.idleFrame));
            JTextField walk = new JTextField(joinFrames(animation.getWalkFrames()));
            idleFields.put(direction, idle);
            walkFields.put(direction, walk);
            addRow(form, direction.toId() + " idle (Index):", idle);
            addRow(form, direction.toId() + " walk (Indizes):", walk);
        }
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        try {
            EnumMap<SpriteDirection, SpriteAnimationAsset> directions = new EnumMap<>(SpriteDirection.class);
            for (SpriteDirection direction : SpriteDirection.values()) {
                int idle = Integer.parseInt(idleFields.get(direction).getText().trim());
                directions.put(direction, new SpriteAnimationAsset(idle, parseFrameList(walkFields.get(direction).getText())));
            }
            return new SpriteSettings(integer(columns), integer(rows), number(height), number(duration),
                number(footOffset), directions);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ungültige Sprite-Einstellungen", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private static SpriteAnimationAsset defaultAnimation() {
        return new SpriteAnimationAsset(0, List.of(0));
    }

    private static List<Integer> parseFrameList(String value) {
        List<Integer> frames = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            frames.add(Integer.parseInt(trimmed));
        }
        if (frames.isEmpty()) throw new IllegalArgumentException("Mindestens ein Lauf-Frame ist erforderlich.");
        return frames;
    }

    private static String joinFrames(List<Integer> frames) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) value.append(", ");
            value.append(frames.get(i));
        }
        return value.toString();
    }

    private void showError(String title, Exception cause) {
        JOptionPane.showMessageDialog(this, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static final class ModelFiles {
        private final File primary;
        private final List<Path> dependencies;

        private ModelFiles(File primary, List<Path> dependencies) {
            this.primary = primary;
            this.dependencies = new ArrayList<>(dependencies);
        }
    }

    private static <T> ListCellRenderer<T> labelRenderer(Function<T, String> text) {
        return (list, value, index, isSelected, cellHasFocus) -> {
            javax.swing.JLabel label = new javax.swing.JLabel(text.apply(value));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        };
    }

    private ListCellRenderer<TextureAsset> textureRenderer() {
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.id + "  (" + value.fileName + ")", thumbnailFor(value), JLabel.LEFT);
            label.setIconTextGap(8);
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        };
    }

    private ImageIcon thumbnailFor(TextureAsset asset) {
        ImageIcon existing = textureThumbnails.get(asset.id);
        if (existing != null) return existing;
        Path projectDirectory = projectController.getProjectDirectory();
        if (projectDirectory == null) return null;
        try {
            BufferedImage source = javax.imageio.ImageIO.read(
                projectDirectory.resolve("sources/textures").resolve(asset.fileName).toFile());
            if (source == null) return null;
            Image scaled = source.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
            ImageIcon thumbnail = new ImageIcon(scaled);
            textureThumbnails.put(asset.id, thumbnail);
            return thumbnail;
        } catch (IOException ignored) {
            return null;
        }
    }
}
