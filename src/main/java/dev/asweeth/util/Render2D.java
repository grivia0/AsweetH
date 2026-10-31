package dev.asweeth.util;

import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Immediate-mode drawing helpers shared by the HUD and every ASweetH screen.
 *
 * <p>Minecraft 26.3 renders GUIs through {@link GuiGraphicsExtractor}: code no longer draws straight
 * into a framebuffer, it submits quads and text into a {@code GuiRenderState}. Everything here works
 * on that extractor, which is why the helpers take {@code GuiGraphicsExtractor} instead of the old
 * {@code GuiGraphics}.</p>
 *
 * <p>Rounded rectangles are emulated with one {@code fill} per pixel row: the GUI already batches
 * thousands of quads per frame, so this stays cheap and keeps ASweetH free of custom render
 * pipelines and textures.</p>
 */
public final class Render2D {
    /** Distance between two stacked HUD text rows, in GUI pixels. */
    public static final int LINE_STEP = 11;

    /** Corner tables are cached up to this radius; anything bigger is computed on the fly. */
    private static final int MAX_CACHED_RADIUS = 12;
    private static final int[][] CORNER_INSETS = new int[MAX_CACHED_RADIUS + 1][];

    private Render2D() {
    }

    /**
     * Draws a filled rectangle with rounded corners and a vertical colour gradient.
     *
     * <p>The straight middle of the panel is submitted as a single gradient quad
     * ({@code fillGradient}); only the two corner bands still need one quad per row because their
     * horizontal insets differ. A 200x40 panel drops from 40 quads to 1 + 2 * radius, which is where
     * most of the HUD's fill submission cost used to go. Turning "Batched panels" off in the
     * Optimisation tab falls back to the old per-row loop.</p>
     *
     * @param radius corner radius in pixels, automatically clamped to half the smaller side
     */
    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int radius, int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0 || height <= r * 2) {
            stripedRect(graphics, x, y, width, height, topColor, bottomColor);
            return;
        }
        float lastRow = Math.max(1, height - 1);
        int[] insets = cornerInsets(r);
        for (int row = 0; row < r; row++) {
            int inset = insets[row];
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1,
                    Colors.lerp(topColor, bottomColor, row / lastRow));
        }
        int bodyTop = y + r;
        int bodyBottom = y + height - r;
        if (bodyBottom > bodyTop) {
            if (ClientSettings.get().fastPanels) {
                graphics.fillGradient(x, bodyTop, x + width, bodyBottom,
                        Colors.lerp(topColor, bottomColor, r / lastRow),
                        Colors.lerp(topColor, bottomColor, (height - r - 1) / lastRow));
            } else {
                for (int row = r; row < height - r; row++) {
                    graphics.fill(x, y + row, x + width, y + row + 1,
                            Colors.lerp(topColor, bottomColor, row / lastRow));
                }
            }
        }
        for (int row = height - r; row < height; row++) {
            int inset = insets[height - 1 - row];
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1,
                    Colors.lerp(topColor, bottomColor, row / lastRow));
        }
    }

    /** The unreduced path: one horizontal quad per pixel row. Used for bars and for opt-out. */
    private static void stripedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    int topColor, int bottomColor) {
        float lastRow = Math.max(1, height - 1);
        for (int row = 0; row < height; row++) {
            graphics.fill(x, y + row, x + width, y + row + 1, Colors.lerp(topColor, bottomColor, row / lastRow));
        }
    }

    /** Glass panel using the panel colours of {@code theme}. */
    public static void glassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  int radius, ThemeMode theme) {
        glassPanel(graphics, x, y, width, height, radius, theme, theme.panelTop(), theme.panelBottom());
    }

    /**
     * Glass panel with a hairline outline and an explicit gradient, used by widgets that light up on
     * hover.
     */
    public static void glassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  int radius, ThemeMode theme, int topColor, int bottomColor) {
        glassPanel(graphics, x, y, width, height, radius, theme.outline(), topColor, bottomColor, 1.0F);
    }

    /**
     * The same panel with every channel's alpha scaled by {@code opacity} (0..1).
     *
     * <p>Fading only the fills would leave the outline and the specular highlight at full strength,
     * which reads as a ghost of a border around a half transparent panel - hence the separate
     * overload. At full opacity this delegates, so the translucency of the theme is untouched.</p>
     */
    public static void glassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                  int radius, ThemeMode theme, float opacity) {
        if (opacity >= 1.0F) {
            glassPanel(graphics, x, y, width, height, radius, theme);
            return;
        }
        glassPanel(graphics, x, y, width, height, radius,
                Colors.multiplyAlpha(theme.outline(), opacity),
                Colors.multiplyAlpha(theme.panelTop(), opacity),
                Colors.multiplyAlpha(theme.panelBottom(), opacity), opacity);
    }

    /** Shared body of every {@code glassPanel} overload. */
    private static void glassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int radius, int outline, int topColor, int bottomColor, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        roundedRect(graphics, x, y, width, height, radius, outline, outline);
        if (width > 2 && height > 2) {
            roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1),
                    topColor, bottomColor);
        }
        // One pixel of specular light along the top edge sells the "glass" read.
        int innerRadius = Math.max(0, radius - 1);
        if (width > (innerRadius + 1) * 2) {
            int specular = Colors.withAlpha(Colors.lighten(topColor, 0.55f), 28);
            graphics.fill(x + innerRadius + 1, y + 1, x + width - innerRadius - 1, y + 2,
                    opacity >= 1.0F ? specular : Colors.multiplyAlpha(specular, opacity));
        }
    }

    /** Horizontal accent bar (left to right fade from the theme accent into its deep variant). */
    public static void accentBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                 ThemeMode theme) {
        accentBar(graphics, x, y, width, height, theme, 1.0F);
    }

    /** The same bar with the alpha of every column scaled by {@code opacity}. */
    public static void accentBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                 ThemeMode theme, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float lastColumn = Math.max(1, width - 1);
        boolean fade = opacity < 1.0F;
        for (int column = 0; column < width; column++) {
            int color = Colors.lerp(theme.accent(), theme.accentDeep(), column / lastColumn);
            graphics.fill(x + column, y, x + column + 1, y + height,
                    fade ? Colors.multiplyAlpha(color, opacity) : color);
        }
    }

    /** One pixel tall separator. */
    public static void hairline(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        if (width > 0) {
            graphics.fill(x, y, x + width, y + 1, color);
        }
    }

    /**
     * Draws text using the palette's own shadow decision.
     *
     * <p>Vanilla's {@code text} overloads always add a drop shadow; on a light palette that shadow
     * smears into the glyph, so the flag comes from {@link ThemeMode#textShadow()} instead of being
     * hardcoded.</p>
     */
    public static void text(GuiGraphicsExtractor graphics, Font font, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, Theme.current().textShadow());
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, Component value, int x, int y, int color) {
        graphics.text(font, value, x, y, color, Theme.current().textShadow());
    }

    /** Draws {@code value} so that its last pixel column lands on {@code right}. */
    public static void textRight(GuiGraphicsExtractor graphics, Font font, String value, int right, int y,
                                 int color) {
        text(graphics, font, value, right - font.width(value), y, color);
    }

    public static void textRight(GuiGraphicsExtractor graphics, Font font, Component value, int right, int y,
                                 int color) {
        text(graphics, font, value, right - font.width(value), y, color);
    }

    /** Draws {@code value} centred on {@code centerX}. */
    public static void textCentered(GuiGraphicsExtractor graphics, Font font, Component value, int centerX,
                                    int y, int color) {
        // Vanilla's centeredText has no shadow flag and always shadows, so centre it here instead.
        text(graphics, font, value, centerX - font.width(value) / 2, y, color);
    }

    /**
     * Truncates {@code text} with an ellipsis so it never draws past {@code maxWidth} pixels.
     *
     * <p>Widths are measured per character instead of per candidate string: a slider label or a
     * translated hint is a handful of glyphs, and {@code Font#width} on the growing prefix keeps this
     * allocation free compared to rebuilding a substring for every character.</p>
     */
    public static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int budget = maxWidth - font.width(ellipsis);
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            int glyph = font.width(String.valueOf(text.charAt(i)));
            if (width + glyph > budget) {
                break;
            }
            width += glyph;
            builder.append(text.charAt(i));
        }
        return builder + ellipsis;
    }

    /**
     * Corner inset table for every row of a corner arc of the given radius.
     *
     * <p>Row 0 is inset by roughly the full radius and the inset falls to zero once the corner arc is
     * behind us, which is the standard way to fake rounded corners with axis aligned quads. The arc
     * only depends on the radius, so it is computed once per radius and reused for every panel of
     * that shape - worth it because {@code Math.sqrt} per row per panel used to dominate large
     * panels.</p>
     */
    private static int[] cornerInsets(int radius) {
        if (radius <= MAX_CACHED_RADIUS) {
            int[] cached = CORNER_INSETS[radius];
            if (cached == null) {
                cached = buildInsets(radius);
                CORNER_INSETS[radius] = cached;
            }
            return cached;
        }
        return buildInsets(radius);
    }

    private static int[] buildInsets(int radius) {
        int[] insets = new int[radius];
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5D;
            double remaining = (double) radius * radius - dy * dy;
            double arc = remaining <= 0D ? 0D : Math.sqrt(remaining);
            insets[row] = Math.max(0, (int) Math.round(radius - arc));
        }
        return insets;
    }
}
