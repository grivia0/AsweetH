package dev.asweeth.client.hud;

/**
 * Position, size and opacity of one HUD element.
 *
 * <p>Every element carries its own layout, which is what the HUD editor drags around: the anchor
 * picks one of the nine grid spots, the offset then moves the element away from that spot in GUI
 * pixels, and the scale/opacity are applied at draw time. Keeping the anchor as the base means a
 * layout still follows the window when it is resized instead of staying at an absolute pixel that a
 * different GUI scale no longer reaches.</p>
 *
 * <p>Offsets are expressed relative to the anchor origin and are clamped on use, so a layout written
 * by a hand edited config - or a drag made in a larger window - can never park an element completely
 * off screen.</p>
 */
public final class HudLayout {
    /** Smallest and largest uniform scale the editor allows. */
    public static final float MIN_SCALE = 0.6F;
    public static final float MAX_SCALE = 1.8F;
    /** Smallest and largest alpha multiplier the editor allows. */
    public static final float MIN_OPACITY = 0.2F;
    public static final float MAX_OPACITY = 1.0F;
    /** Alpha multiplier used while the editor previews an element that is switched off. */
    public static final float DISABLED_ALPHA = 0.45F;
    /** Guard against absurd offsets in a hand edited config file. */
    private static final int OFFSET_LIMIT = 4096;

    /** Grid spot this element is pinned to. Never {@code null} once sanitised. */
    public HudAnchor anchor = HudAnchor.TOP_LEFT;
    /** Pixels to the right of the anchor origin. */
    public int offsetX;
    /** Pixels below the anchor origin. */
    public int offsetY;
    /** Uniform scale applied through the pose matrix, {@link #MIN_SCALE} - {@link #MAX_SCALE}. */
    public float scale = 1.0F;
    /** Alpha multiplier for the element and its text, {@link #MIN_OPACITY} - {@link #MAX_OPACITY}. */
    public float opacity = 1.0F;

    /** For Gson and the defaults; the five argument constructor documents the fields. */
    public HudLayout() {
    }

    public HudLayout(HudAnchor anchor, int offsetX, int offsetY, float scale, float opacity) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.scale = scale;
        this.opacity = opacity;
    }

    /** A detached copy, so the defaults can never be mutated by the editor. */
    public HudLayout copy() {
        return new HudLayout(anchor, offsetX, offsetY, scale, opacity);
    }

    /**
     * Repairs a layout read from disk.
     *
     * <p>Gson skips field initialisers, so a key missing from an older config file arrives as
     * {@code null} or {@code 0}; the fallback is the element's default rather than the minimum, which
     * would leave an upgraded player with an invisible or microscopic HUD.</p>
     */
    public void sanitize(HudLayout fallback) {
        if (anchor == null) {
            anchor = fallback.anchor;
        }
        scale = clampScale(scale, fallback.scale);
        opacity = clampOpacity(opacity, fallback.opacity);
        offsetX = clampOffset(offsetX);
        offsetY = clampOffset(offsetY);
    }

    /** The scale drawing actually uses, so the pose matrix and the reported bounds agree. */
    public float effectiveScale() {
        return clampScale(scale, 1.0F);
    }

    /** The opacity drawing actually uses, before the editor's disabled dimming. */
    public float effectiveOpacity() {
        return clampOpacity(opacity, 1.0F);
    }

    /** Alpha to draw with: the configured opacity, dimmed while previewing a disabled element. */
    public float alpha(boolean enabled) {
        float base = effectiveOpacity();
        return enabled ? base : base * DISABLED_ALPHA;
    }

    /** Width of an element {@code width} unscaled pixels wide, as it lands on screen. */
    public int scaledWidth(int width) {
        return Math.max(1, Math.round(width * effectiveScale()));
    }

    /** Height of an element {@code height} unscaled pixels tall, as it lands on screen. */
    public int scaledHeight(int height) {
        return Math.max(1, Math.round(height * effectiveScale()));
    }

    /**
     * Left edge on screen, in GUI pixels.
     *
     * @param elementWidth width of the element <em>after</em> scaling, because the anchor maths is in
     *                     screen space; passing the unscaled width would push a 1.8x element off the
     *                     right edge
     */
    public int x(int guiWidth, int elementWidth, int margin) {
        return clampX(anchor.originX(guiWidth, elementWidth, margin) + offsetX, guiWidth, elementWidth, margin);
    }

    /** Top edge on screen, in GUI pixels; {@code elementHeight} is the scaled height. */
    public int y(int guiHeight, int elementHeight, int margin) {
        return clampY(anchor.originY(guiHeight, elementHeight, margin) + offsetY, guiHeight, elementHeight, margin);
    }

    /**
     * Moves the element so its top left corner lands on ({@code left}, {@code top}).
     *
     * <p>The target is clamped to the screen <em>first</em> and the offset is derived from the clamped
     * value, so a drag can never build up an invisible store of offset that only shows up after the
     * player switches anchor or resizes the window.</p>
     */
    public void moveTo(int left, int top, int guiWidth, int guiHeight, int elementWidth, int elementHeight,
                       int margin) {
        offsetX = clampX(left, guiWidth, elementWidth, margin) - anchor.originX(guiWidth, elementWidth, margin);
        offsetY = clampY(top, guiHeight, elementHeight, margin) - anchor.originY(guiHeight, elementHeight, margin);
    }

    /** Adds to the offset, e.g. for the editor's arrow key nudging. */
    public void nudge(int dx, int dy) {
        offsetX = clampOffset(offsetX + dx);
        offsetY = clampOffset(offsetY + dy);
    }

    public static int clampX(int left, int guiWidth, int elementWidth, int margin) {
        int max = Math.max(margin, guiWidth - margin - elementWidth);
        return Math.max(margin, Math.min(max, left));
    }

    public static int clampY(int top, int guiHeight, int elementHeight, int margin) {
        int max = Math.max(margin, guiHeight - margin - elementHeight);
        return Math.max(margin, Math.min(max, top));
    }

    /** Clamps a scale, reading a zero (a key missing from an old config) as the fallback. */
    public static float clampScale(float value, float fallback) {
        if (!(value > 0.0F)) {
            return fallback;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    /** Clamps an opacity, reading a zero (a key missing from an old config) as the fallback. */
    public static float clampOpacity(float value, float fallback) {
        if (!(value > 0.0F)) {
            return fallback;
        }
        return Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, value));
    }

    private static int clampOffset(int value) {
        return Math.max(-OFFSET_LIMIT, Math.min(OFFSET_LIMIT, value));
    }
}
