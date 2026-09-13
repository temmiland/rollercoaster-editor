package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.TileShape;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map's terrain grid, north-up (world -Z at the top, +X to the right). A brush stroke only
 * previews locally while the mouse is down - it becomes a single undoable edit on release, via
 * {@link StrokeListener}, so the document is never mutated outside a Command.
 */
final class MapCanvas extends JPanel {
    interface StrokeListener {
        void onTileStroke(String mapId, List<PaintTilesCommand.Edit> edits);

        void onCollisionStroke(String mapId, List<PaintCollisionCommand.Edit> edits);

        void onTerrainStroke(String mapId, List<PaintTerrainCommand.Edit> edits);
    }

    interface HoverListener {
        void onHover(MapAsset map, int x, int z);
    }

    interface PropListener {
        void onPlaceProp(String mapId, int x, int z);
    }

    enum Tool { TILE, COLLISION, TERRAIN, PROPS }

    private static final int CELL_SIZE = 28;
    private static final Color EMPTY_COLOR = new Color(60, 60, 60);
    private static final Color BLOCKED_TINT = new Color(220, 30, 30, 130);
    private static final Color UNWALKABLE_TINT = new Color(40, 80, 220, 100);
    private static final Color GRID_LINE = new Color(20, 20, 20);
    private static final Color EDGE_LINE = new Color(255, 180, 30);
    private static final Color PROP_OUTLINE = Color.WHITE;
    private static final Color SELECTED_PROP_OUTLINE = new Color(255, 220, 40);

    private final StrokeListener strokeListener;
    private final HoverListener hoverListener;
    private final PropListener propListener;
    private final Map<Long, PaintTilesCommand.Edit> pendingTileEdits = new LinkedHashMap<>();
    private final Map<Long, PaintCollisionCommand.Edit> pendingCollisionEdits = new LinkedHashMap<>();
    private final Map<Long, PaintTerrainCommand.Edit> pendingTerrainEdits = new LinkedHashMap<>();

    private MapAsset map;
    private Tool tool = Tool.TILE;
    private String paintTileId;
    private float terrainTargetHeight;
    private TileShape terrainTargetShape = TileShape.FLAT;
    private Map<String, Boolean> tileWalkability = Map.of();
    private String selectedPropInstanceId;
    private boolean showTerrain = true;
    private boolean showGrid = true;
    private boolean showWalkability;
    private boolean showEdges;
    private boolean showManualCollision = true;
    private Boolean collisionStrokeValue;
    private boolean propPlacedThisPress;
    private int lastPaintedX = -1;
    private int lastPaintedZ = -1;

    MapCanvas(StrokeListener strokeListener, HoverListener hoverListener, PropListener propListener) {
        this.strokeListener = strokeListener;
        this.hoverListener = hoverListener;
        this.propListener = propListener;
        setBackground(Color.DARK_GRAY);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                beginStroke(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                continueStroke(e);
                reportHover(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                reportHover(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                endStroke();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setMap(MapAsset map) {
        this.map = map;
        pendingTileEdits.clear();
        pendingCollisionEdits.clear();
        pendingTerrainEdits.clear();
        setPreferredSize(map == null ? new Dimension(0, 0)
            : new Dimension(map.width * CELL_SIZE, map.depth * CELL_SIZE));
        revalidate();
        repaint();
    }

    void setTool(Tool tool) {
        this.tool = tool;
    }

    void setPaintTileId(String tileId) {
        this.paintTileId = tileId;
    }

    void setTerrainTarget(float height, TileShape shape) {
        this.terrainTargetHeight = height;
        this.terrainTargetShape = shape;
    }

    void setSelectedPropInstanceId(String instanceId) {
        selectedPropInstanceId = instanceId;
        repaint();
    }

    void setTileWalkability(Map<String, Boolean> tileWalkability) {
        this.tileWalkability = new LinkedHashMap<>(tileWalkability);
        repaint();
    }

    void setOverlays(boolean terrain, boolean grid, boolean walkability, boolean edges, boolean manualCollision) {
        showTerrain = terrain;
        showGrid = grid;
        showWalkability = walkability;
        showEdges = edges;
        showManualCollision = manualCollision;
        repaint();
    }

    private void beginStroke(MouseEvent e) {
        if (map == null) return;
        lastPaintedX = -1;
        lastPaintedZ = -1;
        collisionStrokeValue = null;
        propPlacedThisPress = false;
        paintAt(e.getX(), e.getY());
    }

    private void continueStroke(MouseEvent e) {
        paintAt(e.getX(), e.getY());
    }

    private void endStroke() {
        if (!pendingTileEdits.isEmpty()) {
            strokeListener.onTileStroke(map.id, new ArrayList<>(pendingTileEdits.values()));
            pendingTileEdits.clear();
        }
        if (!pendingCollisionEdits.isEmpty()) {
            strokeListener.onCollisionStroke(map.id, new ArrayList<>(pendingCollisionEdits.values()));
            pendingCollisionEdits.clear();
        }
        if (!pendingTerrainEdits.isEmpty()) {
            strokeListener.onTerrainStroke(map.id, new ArrayList<>(pendingTerrainEdits.values()));
            pendingTerrainEdits.clear();
        }
        repaint();
    }

    private void reportHover(MouseEvent e) {
        if (map == null) return;
        int x = e.getX() / CELL_SIZE;
        int z = e.getY() / CELL_SIZE;
        if (x < 0 || x >= map.width || z < 0 || z >= map.depth) return;
        hoverListener.onHover(map, x, z);
    }

    private void paintAt(int pixelX, int pixelY) {
        if (map == null) return;
        int x = pixelX / CELL_SIZE;
        int z = pixelY / CELL_SIZE;
        if (x < 0 || x >= map.width || z < 0 || z >= map.depth) return;
        if (x == lastPaintedX && z == lastPaintedZ) return;
        lastPaintedX = x;
        lastPaintedZ = z;

        long key = key(x, z);
        switch (tool) {
            case TILE -> pendingTileEdits.putIfAbsent(key,
                new PaintTilesCommand.Edit(x, z, map.getTile(x, z), paintTileId));
            case COLLISION -> {
                if (collisionStrokeValue == null) collisionStrokeValue = !map.isBlocked(x, z);
                pendingCollisionEdits.putIfAbsent(key,
                    new PaintCollisionCommand.Edit(x, z, map.isBlocked(x, z), collisionStrokeValue));
            }
            case TERRAIN -> pendingTerrainEdits.putIfAbsent(key, new PaintTerrainCommand.Edit(
                x, z, map.getHeight(x, z), map.getShape(x, z), terrainTargetHeight, terrainTargetShape));
            case PROPS -> {
                if (!propPlacedThisPress) {
                    propPlacedThisPress = true;
                    propListener.onPlaceProp(map.id, x, z);
                }
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (map == null) return;
        for (int z = 0; z < map.depth; z++) {
            for (int x = 0; x < map.width; x++) {
                long key = key(x, z);
                PaintTilesCommand.Edit tileEdit = pendingTileEdits.get(key);
                String tileId = tileEdit != null ? tileEdit.newTileId : map.getTile(x, z);
                PaintCollisionCommand.Edit collisionEdit = pendingCollisionEdits.get(key);
                boolean blocked = collisionEdit != null ? collisionEdit.newBlocked : map.isBlocked(x, z);
                PaintTerrainCommand.Edit terrainEdit = pendingTerrainEdits.get(key);
                float height = terrainEdit != null ? terrainEdit.newHeight : map.getHeight(x, z);
                TileShape shape = terrainEdit != null ? terrainEdit.newShape : map.getShape(x, z);

                int px = x * CELL_SIZE;
                int py = z * CELL_SIZE;
                g.setColor(tileId == null ? EMPTY_COLOR : colorFor(tileId));
                g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                if (showManualCollision && blocked) {
                    g.setColor(BLOCKED_TINT);
                    g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                }
                if (showWalkability && tileId != null && !tileWalkability.getOrDefault(tileId, true)) {
                    g.setColor(UNWALKABLE_TINT);
                    g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                }
                if (showTerrain) drawTerrain(g, px, py, height, shape);
                if (showGrid) {
                    g.setColor(GRID_LINE);
                    g.drawRect(px, py, CELL_SIZE, CELL_SIZE);
                }
            }
        }
        if (showEdges) drawEdges(g);
        for (MapProp prop : map.getProps()) drawProp(g, prop, prop.instanceId.equals(selectedPropInstanceId));
    }

    private static void drawTerrain(Graphics g, int px, int py, float height, TileShape shape) {
        if (shape != TileShape.FLAT) drawRampIndicator(g, px, py, shape);
        g.setColor(Color.WHITE);
        g.drawString(String.format("%.1f", height), px + 3, py + CELL_SIZE - 4);
    }

    private void drawEdges(Graphics g) {
        g.setColor(EDGE_LINE);
        for (int z = 0; z < map.depth; z++) {
            for (int x = 0; x < map.width; x++) {
                if (x == 0 && isHeightEdge(x, z, -1, 0)) {
                    g.drawLine(x * CELL_SIZE, z * CELL_SIZE, x * CELL_SIZE, (z + 1) * CELL_SIZE);
                }
                if (z == 0 && isHeightEdge(x, z, 0, -1)) {
                    g.drawLine(x * CELL_SIZE, z * CELL_SIZE, (x + 1) * CELL_SIZE, z * CELL_SIZE);
                }
                if (isHeightEdge(x, z, 1, 0)) {
                    int edgeX = (x + 1) * CELL_SIZE;
                    g.drawLine(edgeX, z * CELL_SIZE, edgeX, (z + 1) * CELL_SIZE);
                }
                if (isHeightEdge(x, z, 0, 1)) {
                    int edgeZ = (z + 1) * CELL_SIZE;
                    g.drawLine(x * CELL_SIZE, edgeZ, (x + 1) * CELL_SIZE, edgeZ);
                }
            }
        }
    }

    private boolean isHeightEdge(int x, int z, int dx, int dz) {
        float current = edgeHeight(map.getHeight(x, z), map.getShape(x, z), dx, dz);
        int neighborX = x + dx;
        int neighborZ = z + dz;
        float neighbor = map.contains(neighborX, neighborZ)
            ? edgeHeight(map.getHeight(neighborX, neighborZ), map.getShape(neighborX, neighborZ), -dx, -dz)
            : 0f;
        return Math.abs(current - neighbor) > 0.001f;
    }

    private static float edgeHeight(float height, TileShape shape, int dx, int dz) {
        return switch (shape) {
            case RAMP_NORTH -> height - dz * 0.5f;
            case RAMP_EAST -> height + dx * 0.5f;
            case RAMP_SOUTH -> height + dz * 0.5f;
            case RAMP_WEST -> height - dx * 0.5f;
            case FLAT -> height;
        };
    }

    private static void drawProp(Graphics g, MapProp prop, boolean selected) {
        // Grid coordinate (x, z) is the centre of its matching canvas cell.
        int cx = Math.round((prop.x + 0.5f) * CELL_SIZE);
        int cz = Math.round((prop.z + 0.5f) * CELL_SIZE);
        int r = CELL_SIZE / 3;
        g.setColor(colorFor(prop.modelId));
        g.fillOval(cx - r, cz - r, r * 2, r * 2);
        g.setColor(selected ? SELECTED_PROP_OUTLINE : PROP_OUTLINE);
        g.drawOval(cx - r, cz - r, r * 2, r * 2);
    }

    private static void drawRampIndicator(Graphics g, int px, int py, TileShape shape) {
        int cx = px + CELL_SIZE / 2;
        int cy = py + CELL_SIZE / 2;
        int r = CELL_SIZE / 4;
        Polygon triangle = new Polygon();
        switch (shape) {
            case RAMP_NORTH -> {
                triangle.addPoint(cx, cy - r);
                triangle.addPoint(cx - r, cy + r);
                triangle.addPoint(cx + r, cy + r);
            }
            case RAMP_SOUTH -> {
                triangle.addPoint(cx, cy + r);
                triangle.addPoint(cx - r, cy - r);
                triangle.addPoint(cx + r, cy - r);
            }
            case RAMP_EAST -> {
                triangle.addPoint(cx + r, cy);
                triangle.addPoint(cx - r, cy - r);
                triangle.addPoint(cx - r, cy + r);
            }
            case RAMP_WEST -> {
                triangle.addPoint(cx - r, cy);
                triangle.addPoint(cx + r, cy - r);
                triangle.addPoint(cx + r, cy + r);
            }
            default -> {
                return;
            }
        }
        g.setColor(Color.WHITE);
        g.fillPolygon(triangle);
    }

    private static Color colorFor(String tileId) {
        int hue = Math.floorMod(tileId.hashCode(), 360);
        return Color.getHSBColor(hue / 360f, 0.45f, 0.75f);
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xffffffffL);
    }
}
