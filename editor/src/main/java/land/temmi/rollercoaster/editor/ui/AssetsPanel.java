package land.temmi.rollercoaster.editor.ui;

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
    private final Map<String, ImageIcon> textureThumbnails = new HashMap<>();

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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Texturen", buildTexturesTab());
        tabs.addTab("Tilesets", buildTilesetsTab());
        tabs.addTab("Modelle", buildModelsTab());
        tabs.addTab("Sprites", buildSpritesTab());
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

    void refresh() {
        boolean open = projectController.isOpen();
        textureList.setEnabled(open);
        tilesetList.setEnabled(open);
        tileList.setEnabled(open);
        modelList.setEnabled(open);
        spriteList.setEnabled(open);

        TilesetAsset selectedTileset = tilesetList.getSelectedValue();
        ModelAsset selectedModel = modelList.getSelectedValue();
        SpriteAsset selectedSprite = spriteList.getSelectedValue();

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
        refreshTiles();
    }

    private void refreshTiles() {
        tileListModel.clear();
        TilesetAsset selected = tilesetList.getSelectedValue();
        if (selected != null) selected.getTiles().stream().sorted(Comparator.comparing(t -> t.id))
            .forEach(tileListModel::addElement);
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
