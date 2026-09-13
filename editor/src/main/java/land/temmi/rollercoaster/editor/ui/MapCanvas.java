package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
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
    }

    interface HoverListener {
        void onHover(MapAsset map, int x, int z);
    }

    enum Tool { TILE, COLLISION }

    private static final int CELL_SIZE = 28;
    private static final Color EMPTY_COLOR = new Color(60, 60, 60);
    private static final Color BLOCKED_TINT = new Color(220, 30, 30, 130);
    private static final Color GRID_LINE = new Color(20, 20, 20);

    private final StrokeListener strokeListener;
    private final HoverListener hoverListener;
    private final Map<Long, PaintTilesCommand.Edit> pendingTileEdits = new LinkedHashMap<>();
    private final Map<Long, PaintCollisionCommand.Edit> pendingCollisionEdits = new LinkedHashMap<>();

    private MapAsset map;
    private Tool tool = Tool.TILE;
    private String paintTileId;
    private Boolean collisionStrokeValue;
    private int lastPaintedX = -1;
    private int lastPaintedZ = -1;

    MapCanvas(StrokeListener strokeListener, HoverListener hoverListener) {
        this.strokeListener = strokeListener;
        this.hoverListener = hoverListener;
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

    private void beginStroke(MouseEvent e) {
        if (map == null) return;
        lastPaintedX = -1;
        lastPaintedZ = -1;
        collisionStrokeValue = null;
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
        if (tool == Tool.TILE) {
            pendingTileEdits.putIfAbsent(key, new PaintTilesCommand.Edit(x, z, map.getTile(x, z), paintTileId));
        } else {
            if (collisionStrokeValue == null) collisionStrokeValue = !map.isBlocked(x, z);
            pendingCollisionEdits.putIfAbsent(key,
                new PaintCollisionCommand.Edit(x, z, map.isBlocked(x, z), collisionStrokeValue));
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

                int px = x * CELL_SIZE;
                int py = z * CELL_SIZE;
                g.setColor(tileId == null ? EMPTY_COLOR : colorFor(tileId));
                g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                if (blocked) {
                    g.setColor(BLOCKED_TINT);
                    g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                }
                TileShape shape = map.getShape(x, z);
                if (shape != TileShape.FLAT) drawRampIndicator(g, px, py, shape);
                g.setColor(GRID_LINE);
                g.drawRect(px, py, CELL_SIZE, CELL_SIZE);
            }
        }
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
