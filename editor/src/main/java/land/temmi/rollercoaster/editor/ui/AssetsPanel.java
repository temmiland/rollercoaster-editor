package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.TextureAsset;
import land.temmi.rollercoaster.editor.document.TileEntry;
import land.temmi.rollercoaster.editor.document.TilesetAsset;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.ListCellRenderer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/** Texture import and tileset authoring - the editor's asset catalog for the open project. */
final class AssetsPanel extends JPanel {
    private final ProjectController projectController;
    private final DefaultListModel<TextureAsset> textureListModel = new DefaultListModel<>();
    private final JList<TextureAsset> textureList = new JList<>(textureListModel);
    private final DefaultListModel<TilesetAsset> tilesetListModel = new DefaultListModel<>();
    private final JList<TilesetAsset> tilesetList = new JList<>(tilesetListModel);
    private final DefaultListModel<TileEntry> tileListModel = new DefaultListModel<>();
    private final JList<TileEntry> tileList = new JList<>(tileListModel);

    AssetsPanel(ProjectController projectController) {
        super(new BorderLayout());
        this.projectController = projectController;
        setBorder(BorderFactory.createTitledBorder("Assets"));

        textureList.setCellRenderer(labelRenderer(t -> t.id + "  (" + t.fileName + ")"));
        tilesetList.setCellRenderer(labelRenderer(t -> t.id + "  (" + t.getTiles().size() + " Tiles)"));
        tileList.setCellRenderer(labelRenderer(t -> t.id + " -> " + t.textureId
            + (t.sideTextureId != null ? " / Seite: " + t.sideTextureId : "")
            + (t.walkable ? "" : "  (nicht begehbar)")));
        tilesetList.addListSelectionListener(e -> refreshTiles());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Texturen", buildTexturesTab());
        tabs.addTab("Tilesets", buildTilesetsTab());
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

    void refresh() {
        boolean open = projectController.isOpen();
        textureList.setEnabled(open);
        tilesetList.setEnabled(open);
        tileList.setEnabled(open);

        TilesetAsset selectedTileset = tilesetList.getSelectedValue();

        textureListModel.clear();
        if (open) projectController.getTextures().forEach(textureListModel::addElement);

        tilesetListModel.clear();
        if (open) projectController.getTilesets().forEach(tilesetListModel::addElement);

        if (selectedTileset != null) {
            for (int i = 0; i < tilesetListModel.size(); i++) {
                if (tilesetListModel.get(i).id.equals(selectedTileset.id)) {
                    tilesetList.setSelectedIndex(i);
                    break;
                }
            }
        }
        refreshTiles();
    }

    private void refreshTiles() {
        tileListModel.clear();
        TilesetAsset selected = tilesetList.getSelectedValue();
        if (selected != null) selected.getTiles().forEach(tileListModel::addElement);
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

    private void showError(String title, Exception cause) {
        JOptionPane.showMessageDialog(this, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
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
}
