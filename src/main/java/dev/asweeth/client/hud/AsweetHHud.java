package dev.asweeth.client.hud;

import dev.asweeth.AsweetH;
import dev.asweeth.client.perf.PerformancePreset;
import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

/**
 * The ASweetH watermark and info readout.
 *
 * <p>Minecraft 26.3 collects HUD layers through {@code AddGuiOverlayLayersEvent} and calls
 * {@link ForgeLayer#extract} while it builds the GUI render state, so this class only submits quads
 * and glyphs - no immediate-mode GL.</p>
 *
 * <p>The readouts are cached: FPS refreshes four times a second, coordinates ten times, ping twice and
 * the chunk counter twice, and their glyph widths are measured only when the string changes. The old
 * implementation built a {@code List<String[]>} plus five {@code Font#width} calls every frame, which
 * is pure garbage on a high framerate client. Turning "Cache readouts" off in the Optimisation tab
 * restores the per-frame rebuild for comparison.</p>
 *
 * <p>Placement, size and opacity are the three HUD settings: the block measures itself first, asks
 * {@link HudAnchor} where that box goes, and only then pushes the pose matrix. Anchor and scale
 * are applied to the origin, never to the text, so a 1.8x HUD keeps crisp glyphs.</p>
 */
public final class AsweetHHud implements ForgeLayer {
    /** Layer id used when the HUD is inserted above the vanilla overlay stack. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AsweetH.MOD_ID, "hud");

    private static final int MARGIN = 4;
    private static final int PANEL_PAD = 6;
    private static final int LABEL_GAP = 6;
    private static final int PANEL_RADIUS = 5;
    private static final int BRAND_HEIGHT = 18;
    /** Vertical space between the brand panel and the info panel. */
    private static final int PANEL_GAP = 3;

    private static final String BRAND_HEAD = "ASweet";
    private static final String BRAND_TAIL = "H";
    private static final String BRAND_VERSION = "v" + AsweetH.VERSION;
    private static final String LABEL_FPS = "FPS";
    private static final String LABEL_PING = "PING";
    private static final String LABEL_POSITION = "XYZ";
    private static final String LABEL_CHUNKS = "CHK";
    private static final String LABEL_MEMORY = "MEM";

    /** Readout refresh periods. FPS and coordinates change constantly, ping and memory do not. */
    private static final long FPS_INTERVAL = 250L;
    private static final long PING_INTERVAL = 1000L;
    private static final long POSITION_INTERVAL = 100L;
    private static final long CHUNK_INTERVAL = 500L;
    private static final long MEMORY_INTERVAL = 1000L;

    // Zero, not Long.MIN_VALUE: "now - stamp" must not overflow, or the very first refresh never
    // happens and the readouts stay at their placeholders with a zero measured width.
    private long fpsStamp;
    private long pingStamp;
    private long positionStamp;
    private long chunkStamp;
    private long memoryStamp;

    private String fpsText = "0";
    private String pingText = "-";
    private String positionText = "-";
    private String chunkText = "-";
    private String memoryText = "-";
    private int fpsWidth;
    private int pingWidth;
    private int positionWidth;
    private int chunkWidth;
    private int memoryWidth;

    /** Cached metrics for the current font; a resource reload hands us a new one and invalidates. */
    private Font metricsFont;
    private int labelWidth;
    private int brandHeadWidth;
    private int brandTailWidth;
    private int brandVersionWidth;

    /** Alpha multiplier for this frame, resolved in {@link #extract} and used by {@link #fade}. */
    private float opacity = 1.0F;

    @Override
    public void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ClientSettings settings = ClientSettings.get();
        if (!settings.hudEnabled || minecraft.gui.hud.isHidden()) {
            return;
        }

        ThemeMode theme = Theme.current();
        Font font = minecraft.font;
        ensureMetrics(font);
        opacity = Math.max(0.2F, Math.min(1.0F, settings.hudOpacity));
        // Refreshed before measuring: the anchor needs the real panel sizes, and the cached widths are
        // only valid for the strings of this frame.
        refresh(minecraft, player, font, settings);

        int brandWidth = settings.showWatermark ? brandWidth(font, settings) : 0;
        int rows = infoRows(settings);
        int infoWidth = rows == 0 ? 0 : infoWidth();
        int infoHeight = rows == 0 ? 0 : PANEL_PAD * 2 + rows * Render2D.LINE_STEP - 1;
        int blockWidth = Math.max(brandWidth, infoWidth);
        int blockHeight = (settings.showWatermark ? BRAND_HEIGHT + PANEL_GAP : 0) + infoHeight;

        float scale = Math.max(0.6F, Math.min(1.8F, settings.hudScale));
        HudAnchor anchor = settings.hudAnchor == null ? HudAnchor.TOP_LEFT : settings.hudAnchor;
        int originX = anchor.originX(minecraft.getWindow().getGuiScaledWidth(),
                Math.round(blockWidth * scale), MARGIN);
        int originY = anchor.originY(minecraft.getWindow().getGuiScaledHeight(),
                Math.round(blockHeight * scale), MARGIN);

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(scale, scale);

        int y = 0;
        if (settings.showWatermark) {
            y = drawBrand(graphics, font, theme, settings, anchor.innerX(blockWidth, brandWidth), y);
        }
        if (rows > 0) {
            drawInfo(graphics, font, theme, settings, anchor.innerX(blockWidth, infoWidth), y, rows);
        }

        graphics.pose().popMatrix();
    }

    /** Scales a colour's alpha by the HUD opacity, so panels and their text fade together. */
    private int fade(int color) {
        return opacity >= 1.0F ? color : Colors.multiplyAlpha(color, opacity);
    }

    /** True when the preset badge is drawn to the right of the version. */
    private static boolean badge(ClientSettings settings) {
        return settings.showLiteBadge && settings.preset != PerformancePreset.STANDARD;
    }

    /** Width of the watermark panel, in unscaled HUD pixels. */
    private int brandWidth(Font font, ClientSettings settings) {
        int width = PANEL_PAD + brandHeadWidth + brandTailWidth + 4 + brandVersionWidth + PANEL_PAD;
        if (badge(settings)) {
            width += font.width(settings.preset.label()) + 8 + 6;
        }
        return width;
    }

    /** Number of enabled readout rows; zero means the info panel is skipped entirely. */
    private static int infoRows(ClientSettings settings) {
        int rows = 0;
        rows += settings.showFps ? 1 : 0;
        rows += settings.showPing ? 1 : 0;
        rows += settings.showCoordinates ? 1 : 0;
        rows += settings.showChunks ? 1 : 0;
        rows += settings.showMemory ? 1 : 0;
        return rows;
    }

    /** Width of the info panel from the measured label and value glyph widths. */
    private int infoWidth() {
        int valueWidth = Math.max(Math.max(fpsWidth, pingWidth), Math.max(positionWidth, chunkWidth));
        valueWidth = Math.max(valueWidth, memoryWidth);
        return PANEL_PAD * 2 + labelWidth + LABEL_GAP + valueWidth;
    }

    /**
     * Draws "ASweetH v0.1.0" with the trailing H in the accent colour, plus the lite-mode badge.
     *
     * @return the y coordinate of the next free row
     */
    private int drawBrand(GuiGraphicsExtractor graphics, Font font, ThemeMode theme, ClientSettings settings,
                          int x, int y) {
        int width = brandWidth(font, settings);
        boolean showBadge = badge(settings);

        Render2D.glassPanel(graphics, x, y, width, BRAND_HEIGHT, PANEL_RADIUS, theme, opacity);
        int textY = y + (BRAND_HEIGHT - font.lineHeight) / 2 + 1;
        Render2D.text(graphics, font, BRAND_HEAD, x + PANEL_PAD, textY, fade(theme.text()));
        Render2D.text(graphics, font, BRAND_TAIL, x + PANEL_PAD + brandHeadWidth, textY, fade(theme.accent()));
        Render2D.text(graphics, font, BRAND_VERSION, x + PANEL_PAD + brandHeadWidth + brandTailWidth + 4, textY,
                fade(theme.textDim()));

        if (showBadge) {
            int badgeWidth = font.width(settings.preset.label()) + 8;
            int badgeX = x + width - PANEL_PAD - badgeWidth;
            int badgeY = y + (BRAND_HEIGHT - 9) / 2;
            Render2D.roundedRect(graphics, badgeX, badgeY, badgeWidth, 9, 4, fade(theme.accent()),
                    fade(theme.accentDeep()));
            int badgeText = Colors.brightness(theme.accent()) > 0.6F ? 0xFF14101A : 0xFFFFFFFF;
            Render2D.textCentered(graphics, font, settings.preset.label(), badgeX + badgeWidth / 2, badgeY + 1,
                    fade(badgeText));
        }

        // Accent underline: the signature ASweetH detail.
        Render2D.accentBar(graphics, x + PANEL_PAD, y + BRAND_HEIGHT - 2, width - PANEL_PAD * 2, 1, theme, opacity);
        return y + BRAND_HEIGHT + PANEL_GAP;
    }

    /**
     * Draws the FPS / ping / coordinates / chunks / memory readout as one aligned block.
     *
     * @param rows number of enabled rows, already counted by {@link #infoRows}
     */
    private void drawInfo(GuiGraphicsExtractor graphics, Font font, ThemeMode theme, ClientSettings settings,
                          int x, int y, int rows) {
        int width = infoWidth();
        int height = PANEL_PAD * 2 + rows * Render2D.LINE_STEP - 1;

        Render2D.glassPanel(graphics, x, y, width, height, PANEL_RADIUS, theme, opacity);
        int rowY = y + PANEL_PAD;
        int labelX = x + PANEL_PAD;
        int valueLeft = labelX + labelWidth + LABEL_GAP;
        if (settings.showFps) {
            row(graphics, font, theme, LABEL_FPS, fpsText, labelX, valueLeft, rowY);
            rowY += Render2D.LINE_STEP;
        }
        if (settings.showPing) {
            row(graphics, font, theme, LABEL_PING, pingText, labelX, valueLeft, rowY);
            rowY += Render2D.LINE_STEP;
        }
        if (settings.showCoordinates) {
            row(graphics, font, theme, LABEL_POSITION, positionText, labelX, valueLeft, rowY);
            rowY += Render2D.LINE_STEP;
        }
        if (settings.showChunks) {
            row(graphics, font, theme, LABEL_CHUNKS, chunkText, labelX, valueLeft, rowY);
            rowY += Render2D.LINE_STEP;
        }
        if (settings.showMemory) {
            row(graphics, font, theme, LABEL_MEMORY, memoryText, labelX, valueLeft, rowY);
        }
    }

    private void row(GuiGraphicsExtractor graphics, Font font, ThemeMode theme, String label, String value,
                     int labelX, int valueLeft, int y) {
        Render2D.text(graphics, font, label, labelX, y, fade(theme.textDim()));
        Render2D.text(graphics, font, value, valueLeft, y, fade(theme.accent()));
    }

    /**
     * Updates the readout strings.
     *
     * <p>Only the elapsed period decides whether a value is rebuilt, so a 500 FPS client no longer
     * allocates three strings and five glyph-width lookups every single frame - the old behaviour.
     * Turning "Cache readouts" off forces a refresh every call, which is useful when debugging but
     * measurably slower.</p>
     */
    private void refresh(Minecraft minecraft, LocalPlayer player, Font font, ClientSettings settings) {
        long now = System.currentTimeMillis();
        boolean cache = settings.cacheReadouts;
        if (!cache || now - fpsStamp >= FPS_INTERVAL) {
            fpsStamp = now;
            fpsText = Integer.toString(minecraft.getFps());
            fpsWidth = font.width(fpsText);
        }
        if (settings.showPing && (!cache || now - pingStamp >= PING_INTERVAL)) {
            pingStamp = now;
            pingText = ping(minecraft);
            pingWidth = font.width(pingText);
        }
        if (settings.showCoordinates && (!cache || now - positionStamp >= POSITION_INTERVAL)) {
            positionStamp = now;
            positionText = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
            positionWidth = font.width(positionText);
        }
        if (settings.showChunks && (!cache || now - chunkStamp >= CHUNK_INTERVAL)) {
            chunkStamp = now;
            chunkText = chunks(minecraft);
            chunkWidth = font.width(chunkText);
        }
        if (settings.showMemory && (!cache || now - memoryStamp >= MEMORY_INTERVAL)) {
            memoryStamp = now;
            memoryText = memory();
            memoryWidth = font.width(memoryText);
        }
    }

    /** Recomputes the static metrics whenever the font instance changes. */
    private void ensureMetrics(Font font) {
        if (font == metricsFont) {
            return;
        }
        metricsFont = font;
        brandHeadWidth = font.width(BRAND_HEAD);
        brandTailWidth = font.width(BRAND_TAIL);
        brandVersionWidth = font.width(BRAND_VERSION);
        labelWidth = Math.max(Math.max(font.width(LABEL_FPS), font.width(LABEL_PING)),
                Math.max(font.width(LABEL_POSITION), Math.max(font.width(LABEL_CHUNKS), font.width(LABEL_MEMORY))));
        // Force a value refresh so the cached widths are measured with the new font.
        fpsStamp = 0L;
        pingStamp = 0L;
        positionStamp = 0L;
        chunkStamp = 0L;
        memoryStamp = 0L;
    }

    /** Latency of the local player as reported by the server list entry, or {@code "-"} when unknown. */
    private static String ping(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return "-";
        }
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        return info == null ? "-" : info.getLatency() + "ms";
    }

    /**
     * Loaded client chunks versus the theoretical view, e.g. {@code 1024/1369}.
     *
     * <p>This is the readout that makes the chunk streaming work visible: at a high render distance the
     * left number keeps climbing while the world streams in, and the right one is what the vanilla
     * radius implies. A loaded count below the view after the world has settled means the server has not
     * sent those chunks yet, not that the client is dropping them.</p>
     */
    private static String chunks(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return "-";
        }
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int distance = minecraft.options.renderDistance().get();
        int view = (distance * 2 + 1) * (distance * 2 + 1);
        return loaded + "/" + view;
    }

    private static String memory() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long max = runtime.maxMemory() / (1024L * 1024L);
        return used + "/" + max + "MB";
    }
}
