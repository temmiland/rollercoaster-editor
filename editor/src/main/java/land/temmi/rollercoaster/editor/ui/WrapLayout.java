package land.temmi.rollercoaster.editor.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A FlowLayout whose reported preferred/minimum size accounts for the rows components actually
 * wrap into at the container's current width, instead of FlowLayout's own single-row size. Plain
 * FlowLayout under-reports its height once a narrow window forces a toolbar onto several rows,
 * which clips the last row when that toolbar sits in a BorderLayout.NORTH strip - that strip is
 * sized from the preferred size alone, not from the wrapped layout it then performs.
 */
final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return wrappedSize(target);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        return wrappedSize(target);
    }

    private Dimension wrappedSize(Container target) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) targetWidth = target.getParent().getWidth();
            if (targetWidth <= 0) return super.preferredLayoutSize(target);

            Insets insets = target.getInsets();
            int hgap = getHgap();
            int vgap = getVgap();
            int maxRowWidth = targetWidth - insets.left - insets.right - hgap * 2;

            int width = 0;
            int height = insets.top + insets.bottom;
            int rowWidth = 0;
            int rowHeight = 0;
            boolean rowStarted = false;

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = component.getPreferredSize();
                if (rowStarted && rowWidth + hgap + size.width > maxRowWidth) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                    rowStarted = false;
                }
                rowWidth += (rowStarted ? hgap : 0) + size.width;
                rowHeight = Math.max(rowHeight, size.height);
                rowStarted = true;
            }
            width = Math.max(width, rowWidth);
            height += rowHeight + vgap;

            return new Dimension(width + insets.left + insets.right + hgap * 2, height);
        }
    }
}
