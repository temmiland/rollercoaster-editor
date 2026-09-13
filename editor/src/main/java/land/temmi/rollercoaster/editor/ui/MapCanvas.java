package land.temmi.rollercoaster.editor.ui;

import land.temmi.rollercoaster.editor.document.MapAsset;
import land.temmi.rollercoaster.editor.document.MapEntityAsset;
import land.temmi.rollercoaster.editor.document.MapProp;
import land.temmi.rollercoaster.editor.document.PaintCollisionCommand;
import land.temmi.rollercoaster.editor.document.PaintTerrainCommand;
import land.temmi.rollercoaster.editor.document.PaintTilesCommand;
import land.temmi.rollercoaster.editor.document.TileShape;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    interface EntityListener {
        void onPlaceEntity(String mapId, int x, int z);
    }

    interface TileListener {
        void onPickTile(String tileId);
    }

    enum Tool { TILE, ERASE_TILE, PICK_TILE, FILL_TILE, RECT_TILE, COPY_TILE, PASTE_TILE, COLLISION, TERRAIN, PROPS, ENTITIES }

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
    private final EntityListener entityListener;
    private final TileListener tileListener;
    private final Map<Long, PaintTilesCommand.Edit> pendingTileEdits = new LinkedHashMap<>();
    private final Map<Long, PaintCollisionCommand.Edit> pendingCollisionEdits = new LinkedHashMap<>();
    private final Map<Long, PaintTerrainCommand.Edit> pendingTerrainEdits = new LinkedHashMap<>();

    private MapAsset map;
    private Tool tool = Tool.TILE;
    private String paintTileId;
    private float terrainTargetHeight;
    private TileShape terrainTargetShape = TileShape.FLAT;
    private Map<String, Boolean> tileWalkability = Map.of();
    private Map<String, Image> tileImages = Map.of();
    private String selectedPropInstanceId;
    private String selectedEntityInstanceId;
    private boolean showTerrain = true;
    private boolean showGrid = true;
    private boolean showWalkability;
    private boolean showEdges;
    private boolean showManualCollision = true;
    private Boolean collisionStrokeValue;
    private boolean propPlacedThisPress;
    private boolean entityPlacedThisPress;
    private int lastPaintedX = -1;
    private int lastPaintedZ = -1;
    private int rectangleStartX = -1;
    private int rectangleStartZ = -1;
    private int rectangleEndX = -1;
    private int rectangleEndZ = -1;
    private String[][] tileClipboard;

    MapCanvas(StrokeListener strokeListener, HoverListener hoverListener, PropListener propListener,
              EntityListener entityListener, TileListener tileListener) {
        this.strokeListener = strokeListener;
        this.hoverListener = hoverListener;
        this.propListener = propListener;
        this.entityListener = entityListener;
        this.tileListener = tileListener;
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
        clearRectangle();
        repaint();
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

    void setSelectedEntityInstanceId(String instanceId) {
        selectedEntityInstanceId = instanceId;
        repaint();
    }

    void setTileWalkability(Map<String, Boolean> tileWalkability) {
        this.tileWalkability = new LinkedHashMap<>(tileWalkability);
        repaint();
    }

    /** Tile-id keyed source previews; missing or unreadable images deliberately fall back to color. */
    void setTileImages(Map<String, Image> tileImages) {
        this.tileImages = new LinkedHashMap<>(tileImages);
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
        entityPlacedThisPress = false;
        int x = cellX(e.getX());
        int z = cellZ(e.getY());
        if (!map.contains(x, z)) return;
        switch (tool) {
            case PICK_TILE -> tileListener.onPickTile(map.getTile(x, z));
            case FILL_TILE -> fillTiles(x, z);
            case RECT_TILE, COPY_TILE -> setRectangle(x, z, x, z);
            case PASTE_TILE -> pasteTiles(x, z);
            default -> paintAt(e.getX(), e.getY());
        }
    }

    private void continueStroke(MouseEvent e) {
        if (tool == Tool.RECT_TILE || tool == Tool.COPY_TILE) {
            int x = cellX(e.getX());
            int z = cellZ(e.getY());
            if (map.contains(x, z)) setRectangle(rectangleStartX, rectangleStartZ, x, z);
        } else if (tool != Tool.PICK_TILE && tool != Tool.FILL_TILE && tool != Tool.PASTE_TILE) {
            paintAt(e.getX(), e.getY());
        }
    }

    private void endStroke() {
        if (tool == Tool.RECT_TILE) paintRectangle();
        else if (tool == Tool.COPY_TILE) copyRectangle();
        clearRectangle();
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
        int x = cellX(e.getX());
        int z = cellZ(e.getY());
        if (x < 0 || x >= map.width || z < 0 || z >= map.depth) return;
        hoverListener.onHover(map, x, z);
    }

    private void paintAt(int pixelX, int pixelY) {
        if (map == null) return;
        int x = cellX(pixelX);
        int z = cellZ(pixelY);
        if (x < 0 || x >= map.width || z < 0 || z >= map.depth) return;
        if (x == lastPaintedX && z == lastPaintedZ) return;
        lastPaintedX = x;
        lastPaintedZ = z;

        long key = key(x, z);
        switch (tool) {
            case TILE -> {
                if (paintTileId != null) queueTileEdit(x, z, paintTileId);
            }
            case ERASE_TILE -> queueTileEdit(x, z, null);
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
            case ENTITIES -> {
                if (!entityPlacedThisPress) {
                    entityPlacedThisPress = true;
                    entityListener.onPlaceEntity(map.id, x, z);
                }
            }
            default -> {
                // One-click tools are handled in beginStroke().
            }
        }
        repaint();
    }

    private void fillTiles(int startX, int startZ) {
        if (paintTileId == null) return;
        String replacedTileId = map.getTile(startX, startZ);
        if (Objects.equals(replacedTileId, paintTileId)) return;
        boolean[][] visited = new boolean[map.depth][map.width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {startX, startZ});
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            int x = cell[0];
            int z = cell[1];
            if (!map.contains(x, z) || visited[z][x] || !Objects.equals(map.getTile(x, z), replacedTileId)) continue;
            visited[z][x] = true;
            queueTileEdit(x, z, paintTileId);
            queue.addLast(new int[] {x - 1, z});
            queue.addLast(new int[] {x + 1, z});
            queue.addLast(new int[] {x, z - 1});
            queue.addLast(new int[] {x, z + 1});
        }
    }

    private void paintRectangle() {
        if (paintTileId == null || rectangleStartX < 0) return;
        forEachRectangleCell((x, z) -> queueTileEdit(x, z, paintTileId));
    }

    private void copyRectangle() {
        if (rectangleStartX < 0) return;
        int minX = Math.min(rectangleStartX, rectangleEndX);
        int maxX = Math.max(rectangleStartX, rectangleEndX);
        int minZ = Math.min(rectangleStartZ, rectangleEndZ);
        int maxZ = Math.max(rectangleStartZ, rectangleEndZ);
        tileClipboard = new String[maxZ - minZ + 1][maxX - minX + 1];
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) tileClipboard[z - minZ][x - minX] = map.getTile(x, z);
        }
    }

    private void pasteTiles(int startX, int startZ) {
        if (tileClipboard == null) return;
        for (int clipboardZ = 0; clipboardZ < tileClipboard.length; clipboardZ++) {
            for (int clipboardX = 0; clipboardX < tileClipboard[clipboardZ].length; clipboardX++) {
                int x = startX + clipboardX;
                int z = startZ + clipboardZ;
                if (map.contains(x, z)) queueTileEdit(x, z, tileClipboard[clipboardZ][clipboardX]);
            }
        }
    }

    private void queueTileEdit(int x, int z, String tileId) {
        if (Objects.equals(map.getTile(x, z), tileId)) return;
        pendingTileEdits.putIfAbsent(key(x, z), new PaintTilesCommand.Edit(x, z, map.getTile(x, z), tileId));
    }

    private void setRectangle(int startX, int startZ, int endX, int endZ) {
        rectangleStartX = startX;
        rectangleStartZ = startZ;
        rectangleEndX = endX;
        rectangleEndZ = endZ;
        repaint();
    }

    private void clearRectangle() {
        rectangleStartX = -1;
        rectangleStartZ = -1;
        rectangleEndX = -1;
        rectangleEndZ = -1;
    }

    private void forEachRectangleCell(CellConsumer consumer) {
        int minX = Math.min(rectangleStartX, rectangleEndX);
        int maxX = Math.max(rectangleStartX, rectangleEndX);
        int minZ = Math.min(rectangleStartZ, rectangleEndZ);
        int maxZ = Math.max(rectangleStartZ, rectangleEndZ);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) consumer.accept(x, z);
        }
    }

    private static int cellX(int pixelX) {
        return Math.floorDiv(pixelX, CELL_SIZE);
    }

    private static int cellZ(int pixelY) {
        return Math.floorDiv(pixelY, CELL_SIZE);
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
                Image image = tileId == null ? null : tileImages.get(tileId);
                if (image == null) {
                    g.setColor(tileId == null ? EMPTY_COLOR : colorFor(tileId));
                    g.fillRect(px, py, CELL_SIZE, CELL_SIZE);
                } else {
                    g.drawImage(image, px, py, CELL_SIZE, CELL_SIZE, this);
                }
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
        for (MapEntityAsset entity : map.getEntities()) {
            drawEntity(g, entity, entity.instanceId.equals(selectedEntityInstanceId));
        }
        drawRectangle(g);
    }

    private void drawRectangle(Graphics g) {
        if (rectangleStartX < 0) return;
        int minX = Math.min(rectangleStartX, rectangleEndX);
        int maxX = Math.max(rectangleStartX, rectangleEndX);
        int minZ = Math.min(rectangleStartZ, rectangleEndZ);
        int maxZ = Math.max(rectangleStartZ, rectangleEndZ);
        g.setColor(Color.WHITE);
        g.drawRect(minX * CELL_SIZE, minZ * CELL_SIZE,
            (maxX - minX + 1) * CELL_SIZE, (maxZ - minZ + 1) * CELL_SIZE);
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

    private static void drawEntity(Graphics g, MapEntityAsset entity, boolean selected) {
        int px = entity.x * CELL_SIZE;
        int py = entity.z * CELL_SIZE;
        int inset = CELL_SIZE / 4;
        g.setColor(new Color(70, 220, 155));
        g.fillRect(px + inset, py + inset, CELL_SIZE - inset * 2, CELL_SIZE - inset * 2);
        g.setColor(selected ? SELECTED_PROP_OUTLINE : PROP_OUTLINE);
        g.drawRect(px + inset, py + inset, CELL_SIZE - inset * 2, CELL_SIZE - inset * 2);
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

    private interface CellConsumer {
        void accept(int x, int z);
    }
}
