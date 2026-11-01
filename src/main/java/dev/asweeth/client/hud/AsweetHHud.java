package dev.asweeth.client.hud;

import dev.asweeth.AsweetH;
import dev.asweeth.client.perf.PerformancePreset;
import dev.asweeth.client.screen.HudEditorScreen;
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
 * The ASweetH watermark and stat readouts.
 *
 * <p>Minecraft 26.3 collects HUD layers through {@code AddGuiOverlayLayersEvent} and calls
 * {@link ForgeLayer#extract} while it builds the GUI render state, so this class only submits quads
 * and glyphs - no immediate-mode GL.</p>
 *
 * <p>Each element - the watermark pill and one pill per readout - owns a {@link HudLayout}, so the HUD
 * editor can drag, scale and fade them independently. This layer therefore never computes a shared
 * block origin: it measures one element, asks that element's layout where the box goes, pushes the pose
 * matrix for it and pops again. Anchors and scale are applied to the origin, never to the glyphs, so a
 * 1.8x HUD stays crisp.</p>
 *
 * <p>Every element also publishes its on screen rectangle to {@link HudBounds} as it draws, which is how
 * the editor hit tests exactly what the player sees instead of recomputing seven widths.</p>
 *
 * <p>The readouts are cached: FPS refreshes four times a second, coordinates ten times, ping and memory
 * once and the chunk counter twice, with glyph widths measured only when a string changes. Turning
 * "Cache readouts" off in the Optimisation tab restores the per-frame rebuild for comparison.</p>
 */
public final class AsweetHHud implements ForgeLayer {
    /** Layer id used when the HUD is inserted above the vanilla overlay stack. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AsweetH.MOD_ID, "hud");

    /** Gap between an element and the screen edge, in GUI pixels. */
    private static final int MARGIN = 4;
    private static final int PANEL_PAD = 6;
    /** Padding inside a stat pill. */
    private static final int CHIP_PAD = 4;
    private static final int LABEL_GAP = 5;
    private static final int PANEL_RADIUS = 5;
    private static final int CHIP_RADIUS = 4;
    private static final int BRAND_HEIGHT = 18;
    private static final int CHIP_HEIGHT = 13;

    private static final String BRAND_HEAD = "ASweet";
    private static final String BRAND_TAIL = "H";
    private static final String BRAND_VERSION = "v" + AsweetH.VERSION;

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
    private int brandHeadWidth;
    private int brandTailWidth;
    private int brandVersionWidth;
    /** Measured chip label widths, indexed by {@link HudElement#ordinal()}. */
    private final int[] labelWidths = new int[HudElement.values().length];

    /** True while the HUD editor is open; disabled elements are then previewed dimmed, not hidden. */
    private boolean editing;

    @Override
    public void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            HudBounds.clear();
            return;
        }
        ClientSettings settings = ClientSettings.get();
        editing = HudEditorScreen.isEditing();
        // The editor keeps drawing while the player has the HUD switched off or pressed F1 - otherwise
        // there would be nothing to drag, and no way to find an element that is currently hidden.
        if (!editing && (!settings.hudEnabled || minecraft.gui.hud.isHidden())) {
            // Nothing was painted, so last frame's rectangles would only mislead the editor.
            HudBounds.clear();
            return;
        }

        ThemeMode theme = Theme.current();
        Font font = minecraft.font;
        ensureMetrics(font);
        // Refreshed before measuring: the cached widths are only valid for the strings of this frame.
        refresh(minecraft, player, font, settings);

        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();

        drawBrand(graphics, font, theme, settings, guiWidth, guiHeight);
        drawChip(graphics, font, theme, settings, HudElement.FPS, fpsText, fpsWidth, guiWidth, guiHeight);
        drawChip(graphics, font, theme, settings, HudElement.PING, pingText, pingWidth, guiWidth, guiHeight);
        drawChip(graphics, font, theme, settings, HudElement.COORDINATES, positionText, positionWidth, guiWidth,
                guiHeight);
        drawChip(graphics, font, theme, settings, HudElement.CHUNKS, chunkText, chunkWidth, guiWidth, guiHeight);
        drawChip(graphics, font, theme, settings, HudElement.MEMORY, memoryText, memoryWidth, guiWidth, guiHeight);
    }

    /** Scales a colour's alpha by an element's opacity, so a panel and its text fade together. */
    private static int fade(int color, float opacity) {
        return opacity >= 1.0F ? color : Colors.multiplyAlpha(color, opacity);
    }

    /** True when the preset badge is drawn to the right of the version. */
    private static boolean badge(ClientSettings settings) {
        return settings.showLiteBadge && settings.preset != PerformancePreset.STANDARD;
    }

    /** Short label drawn on the left of an element's chip. */
    private static String chipLabel(HudElement element) {
        return switch (element) {
            case FPS -> "FPS";
            case PING -> "PING";
            case COORDINATES -> "XYZ";
            case CHUNKS -> "CHK";
            case MEMORY -> "MEM";
            case WATERMARK, KEYSTROKES -> "";
        };
    }

    /** Width of the watermark panel, in unscaled HUD pixels. */
    private int brandWidth(Font font, ClientSettings settings) {
        int width = PANEL_PAD * 2 + brandHeadWidth + brandTailWidth + 4 + brandVersionWidth;
        if (badge(settings)) {
            width += font.width(settings.preset.label()) + 8 + 6;
        }
        return width;
    }

    /**
     * Draws the "ASweetH v0.1.1" pill with the trailing H in the accent colour, plus the preset badge.
     *
     * <p>The badge and the version are placed from the panel's own width, so the pill grows with the
     * version string and never overlaps the accent underline.</p>
     */
    private void drawBrand(GuiGraphicsExtractor graphics, Font font, ThemeMode theme, ClientSettings settings,
                           int guiWidth, int guiHeight) {
        HudElement element = HudElement.WATERMARK;
        boolean enabled = element.enabled(settings);
        if (!enabled && !editing) {
            return;
        }
        HudLayout layout = settings.layout(element);
        int width = brandWidth(font, settings);
        int scaledWidth = layout.scaledWidth(width);
        int scaledHeight = layout.scaledHeight(BRAND_HEIGHT);
        int originX = layout.x(guiWidth, scaledWidth, MARGIN);
        int originY = layout.y(guiHeight, scaledHeight, MARGIN);
        float opacity = layout.alpha(enabled);

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(layout.effectiveScale(), layout.effectiveScale());

        Render2D.glassPanel(graphics, 0, 0, width, BRAND_HEIGHT, PANEL_RADIUS, theme, opacity);
        int textY = (BRAND_HEIGHT - font.lineHeight) / 2 + 1;
        Render2D.text(graphics, font, BRAND_HEAD, PANEL_PAD, textY, fade(theme.text(), opacity));
        Render2D.text(graphics, font, BRAND_TAIL, PANEL_PAD + brandHeadWidth, textY, fade(theme.accent(), opacity));
        Render2D.text(graphics, font, BRAND_VERSION, PANEL_PAD + brandHeadWidth + brandTailWidth + 4, textY,
                fade(theme.textDim(), opacity));

        if (badge(settings)) {
            int badgeBox = font.width(settings.preset.label()) + 8;
            int badgeX = width - PANEL_PAD - badgeBox;
            int badgeY = (BRAND_HEIGHT - 9) / 2;
            Render2D.roundedRect(graphics, badgeX, badgeY, badgeBox, 9, 4, fade(theme.accent(), opacity),
                    fade(theme.accentDeep(), opacity));
            int badgeInk = Colors.brightness(theme.accent()) > 0.6F ? 0xFF14101A : 0xFFFFFFFF;
            Render2D.textCentered(graphics, font, settings.preset.label(), badgeX + badgeBox / 2, badgeY + 1,
                    fade(badgeInk, opacity));
        }

        // Accent underline: the signature ASweetH detail.
        Render2D.accentBar(graphics, PANEL_PAD, BRAND_HEIGHT - 2, width - PANEL_PAD * 2, 1, theme, opacity);
        graphics.pose().popMatrix();

        HudBounds.put(element, originX, originY, scaledWidth, scaledHeight);
    }

    /**
     * Draws one readout as a small pill: dim label on the left, accent value on the right.
     *
     * @param valueWidth measured glyph width of {@code value}, cached by {@link #refresh}
     */
    private void drawChip(GuiGraphicsExtractor graphics, Font font, ThemeMode theme, ClientSettings settings,
                          HudElement element, String value, int valueWidth, int guiWidth, int guiHeight) {
        boolean enabled = element.enabled(settings);
        if (!enabled && !editing) {
            return;
        }
        HudLayout layout = settings.layout(element);
        int labelWidth = labelWidths[element.ordinal()];
        int width = CHIP_PAD * 2 + labelWidth + LABEL_GAP + valueWidth;
        int scaledWidth = layout.scaledWidth(width);
        int scaledHeight = layout.scaledHeight(CHIP_HEIGHT);
        int originX = layout.x(guiWidth, scaledWidth, MARGIN);
        int originY = layout.y(guiHeight, scaledHeight, MARGIN);
        float opacity = layout.alpha(enabled);

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(layout.effectiveScale(), layout.effectiveScale());

        Render2D.glassPanel(graphics, 0, 0, width, CHIP_HEIGHT, CHIP_RADIUS, theme, opacity);
        int textY = (CHIP_HEIGHT - font.lineHeight) / 2 + 1;
        Render2D.text(graphics, font, chipLabel(element), CHIP_PAD, textY, fade(theme.textDim(), opacity));
        Render2D.text(graphics, font, value, CHIP_PAD + labelWidth + LABEL_GAP, textY, fade(theme.accent(), opacity));

        graphics.pose().popMatrix();

        HudBounds.put(element, originX, originY, scaledWidth, scaledHeight);
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
        for (HudElement element : HudElement.values()) {
            labelWidths[element.ordinal()] = font.width(chipLabel(element));
        }
        // Force a value refresh so the cached widths are measured with the new font.
        fpsStamp = 0L;
        pingStamp = 0L;
        positionStamp = 0L;
        chunkStamp = 0L;
        memoryStamp = 0L;
    }

    /**
     * Updates the readout strings.
     *
     * <p>Only the elapsed period decides whether a value is rebuilt, so a 500 FPS client no longer
     * allocates five strings and five glyph-width lookups every single frame - the old behaviour.
     * Turning "Cache readouts" off forces a refresh every call, which is useful when debugging but
     * measurably slower.</p>
     *
     * <p>Every readout is refreshed even when its pill is switched off: the HUD editor previews the
     * hidden ones, and a pill whose text was last built a minute ago would read as a bug there.</p>
     */
    private void refresh(Minecraft minecraft, LocalPlayer player, Font font, ClientSettings settings) {
        long now = System.currentTimeMillis();
        boolean cache = settings.cacheReadouts;
        if (!cache || now - fpsStamp >= FPS_INTERVAL) {
            fpsStamp = now;
            fpsText = Integer.toString(minecraft.getFps());
            fpsWidth = font.width(fpsText);
        }
        if (!cache || now - pingStamp >= PING_INTERVAL) {
            pingStamp = now;
            pingText = ping(minecraft);
            pingWidth = font.width(pingText);
        }
        if (!cache || now - positionStamp >= POSITION_INTERVAL) {
            positionStamp = now;
            positionText = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
            positionWidth = font.width(positionText);
        }
        if (!cache || now - chunkStamp >= CHUNK_INTERVAL) {
            chunkStamp = now;
            chunkText = chunks(minecraft);
            chunkWidth = font.width(chunkText);
        }
        if (!cache || now - memoryStamp >= MEMORY_INTERVAL) {
            memoryStamp = now;
            memoryText = memory();
            memoryWidth = font.width(memoryText);
        }
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
