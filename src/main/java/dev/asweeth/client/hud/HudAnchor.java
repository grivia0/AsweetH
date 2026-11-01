package dev.asweeth.client.hud;

import net.minecraft.network.chat.Component;

/**
 * The nine spots a HUD block can be pinned to.
 *
 * <p>A grid of anchors instead of two free offset sliders: the block then always stays on screen no
 * matter which GUI scale or window size the player uses, and the settings screen needs one cycle
 * button instead of two fiddly number fields. The column also decides how the panels of a block line
 * up against each other - see {@link #innerX(int, int)} - so a right anchored HUD right aligns its
 * narrower panel instead of leaving it hanging off the left edge.</p>
 *
 * <p>Every method takes the block size in <em>scaled</em> GUI pixels, because the pose matrix is
 * scaled after the origin is chosen: offsetting by unscaled sizes would push a 1.8x HUD off screen.</p>
 */
public enum HudAnchor {
    TOP_LEFT(0, 0, "asweeth.anchor.top_left"),
    TOP_CENTER(1, 0, "asweeth.anchor.top_center"),
    TOP_RIGHT(2, 0, "asweeth.anchor.top_right"),
    MIDDLE_LEFT(0, 1, "asweeth.anchor.middle_left"),
    MIDDLE_CENTER(1, 1, "asweeth.anchor.middle_center"),
    MIDDLE_RIGHT(2, 1, "asweeth.anchor.middle_right"),
    BOTTOM_LEFT(0, 2, "asweeth.anchor.bottom_left"),
    BOTTOM_CENTER(1, 2, "asweeth.anchor.bottom_center"),
    BOTTOM_RIGHT(2, 2, "asweeth.anchor.bottom_right");

    /** Column and row of the 3x3 grid: 0 left/top, 1 centre/middle, 2 right/bottom. */
    private final int column;
    private final int row;
    private final String labelKey;
    /** Built lazily: enum constants are created while the config is still loading. */
    private Component label;

    HudAnchor(int column, int row, String labelKey) {
        this.column = column;
        this.row = row;
        this.labelKey = labelKey;
    }

    /**
     * The grid spot at {@code column} / {@code row}, both clamped to 0-2.
     *
     * <p>The editor re-pins an element to the spot nearest to where it was dropped, so the stored
     * offset stays a handful of pixels measured from the closest corner or edge. That is what keeps a
     * dragged layout in place when the window or the GUI scale changes: a large offset banked against
     * a far away anchor would drift instead.</p>
     */
    public static HudAnchor at(int column, int row) {
        int wantedColumn = Math.max(0, Math.min(2, column));
        int wantedRow = Math.max(0, Math.min(2, row));
        for (HudAnchor anchor : values()) {
            if (anchor.column == wantedColumn && anchor.row == wantedRow) {
                return anchor;
            }
        }
        return TOP_LEFT;
    }

    /** Left edge of a block {@code blockWidth} wide on a screen {@code guiWidth} wide. */
    public int originX(int guiWidth, int blockWidth, int margin) {
        return switch (column) {
            case 1 -> (guiWidth - blockWidth) / 2;
            case 2 -> guiWidth - margin - blockWidth;
            default -> margin;
        };
    }

    /** Top edge of a block {@code blockHeight} tall on a screen {@code guiHeight} tall. */
    public int originY(int guiHeight, int blockHeight, int margin) {
        return switch (row) {
            case 1 -> (guiHeight - blockHeight) / 2;
            case 2 -> guiHeight - margin - blockHeight;
            default -> margin;
        };
    }

    /**
     * Left edge of a panel {@code width} wide inside a block {@code blockWidth} wide.
     *
     * <p>This is what keeps a right anchored HUD looking deliberate when the info panel is narrower
     * than the watermark above it.</p>
     */
    public int innerX(int blockWidth, int width) {
        return switch (column) {
            case 1 -> (blockWidth - width) / 2;
            case 2 -> blockWidth - width;
            default -> 0;
        };
    }

    /** True for the three right hand anchors, used to mirror directional decoration. */
    public boolean isRight() {
        return column == 2;
    }

    /** True for the three bottom anchors. */
    public boolean isBottom() {
        return row == 2;
    }

    public Component label() {
        Component local = label;
        if (local == null) {
            local = Component.translatable(labelKey);
            label = local;
        }
        return local;
    }
}
