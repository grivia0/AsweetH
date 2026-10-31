package dev.asweeth.client.screen;

import dev.asweeth.AsweetH;
import dev.asweeth.client.Zoom;
import dev.asweeth.client.hud.AsweetHHud;
import dev.asweeth.client.hud.HudAnchor;
import dev.asweeth.client.hud.KeystrokeTint;
import dev.asweeth.client.perf.Optimisations;
import dev.asweeth.client.perf.PerformancePreset;
import dev.asweeth.client.widget.GlassButton;
import dev.asweeth.client.widget.GlassCycleButton;
import dev.asweeth.client.widget.GlassSlider;
import dev.asweeth.client.widget.GlassToggle;
import dev.asweeth.client.widget.HintBlock;
import dev.asweeth.client.widget.ScrollPanel;
import dev.asweeth.client.widget.TabButton;
import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Render2D;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The ASweetH settings menu: a glass sheet with a vertical tab rail, a content area whose controls are
 * rebuilt per tab, and a footer with the version and a Done button.
 *
 * <p>Only the chrome is painted by hand - individual controls are real {@code AbstractWidget}s, so
 * keyboard navigation, narration, focus handling and click routing come from vanilla. Layout is
 * recomputed in {@link #init()}, which also runs whenever the player switches tabs.</p>
 */
public final class AsweetHScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 420;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int RAIL_WIDTH = 96;
    private static final int GUTTER = 10;
    private static final int HEADER = 30;
    private static final int FOOTER = 26;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int PANEL_RADIUS = 8;

    private final Screen parent;
    private SettingsTab active = SettingsTab.GENERAL;
    /** Scrollable holder for the rows of the active tab; rebuilt by {@link #init()}. */
    private ScrollPanel content;
    /** Offset each tab was left at, so switching back and forth does not lose the player's place. */
    private final Map<SettingsTab, Double> scrollMemory = new EnumMap<>(SettingsTab.class);
    /** Tab the current {@link #content} belongs to, used to record its offset before a rebuild. */
    private SettingsTab builtTab;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    public AsweetHScreen(Screen parent) {
        super(Component.translatable("asweeth.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientSettings settings = ClientSettings.get();
        // Remember where the tab that is being replaced was scrolled to. A tab switch and a plain
        // rebuild (applying a preset) both land here, so the offset is recorded per tab: the player
        // returns to their place in a long tab instead of being thrown back to the top.
        if (content != null && builtTab != null) {
            scrollMemory.put(builtTab, content.scrollAmount());
        }
        double previousScroll = scrollMemory.getOrDefault(active, 0.0D);

        panelWidth = Math.min(this.width - 20, MAX_PANEL_WIDTH);
        panelHeight = Math.min(this.height - 20, MAX_PANEL_HEIGHT);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        contentX = panelX + RAIL_WIDTH + GUTTER;
        contentWidth = panelWidth - RAIL_WIDTH - GUTTER - 10;
        contentY = panelY + HEADER;
        contentHeight = panelHeight - HEADER - FOOTER;

        // The tab title and its description stay pinned in the header, so the scroll area starts below
        // them and ends just above the footer rule.
        content = new ScrollPanel(contentX + 2, contentY + 26, contentWidth - 4,
                Math.max(24, contentHeight - 26 - 8), active.label());
        addRenderableWidget(content);

        int tabY = panelY + HEADER + 2;
        // Seven tabs have to fit between the header and the footer even on a short window, so the rail
        // shrinks its rows instead of spilling over the Done button.
        SettingsTab[] tabs = SettingsTab.values();
        int railSpace = Math.max(0, panelHeight - HEADER - FOOTER - 4);
        int tabHeight = Math.max(12, Math.min(18, (railSpace - (tabs.length - 1) * 3) / tabs.length));
        for (SettingsTab tab : tabs) {
            SettingsTab target = tab;
            addRenderableWidget(new TabButton(panelX + 6, tabY, RAIL_WIDTH - 12, tabHeight, tab.label(),
                    tab == active, () -> {
                        active = target;
                        rebuildWidgets();
                    }));
            tabY += tabHeight + 3;
        }

        populate(settings);
        content.jumpTo(previousScroll);
        builtTab = active;

        addRenderableWidget(new GlassButton(panelX + panelWidth - 10 - 64, panelY + panelHeight - FOOTER + 4,
                64, 18, Component.translatable("gui.done"), true, this::onClose));
    }

    /**
     * Adds the controls of the active tab into the scroll area, stacked from its top.
     *
     * <p>Rows are placed in content coordinates: the panel decides the real Y once it knows the scroll
     * offset. Whatever does not fit becomes scrollable instead of spilling out of the sheet, which is
     * what makes the screen usable on a short window.</p>
     */
    private void populate(ClientSettings settings) {
        int y = 0;

        switch (active) {
            case GENERAL -> {
                content.add(toggle("asweeth.option.hud",
                        () -> settings.hudEnabled, value -> settings.hudEnabled = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.watermark",
                        () -> settings.showWatermark, value -> settings.showWatermark = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.coordinates",
                        () -> settings.showCoordinates, value -> settings.showCoordinates = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                // Layout of the whole block: where it sits, how solid it is, how big it gets.
                content.add(new GlassCycleButton<HudAnchor>(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.hud_position"), HudAnchor.values(),
                        () -> settings.hudAnchor,
                        value -> {
                            settings.hudAnchor = value;
                            settings.save();
                        },
                        HudAnchor::label, 92), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.hud_opacity"), 0.2D, 1.0D,
                        () -> settings.hudOpacity,
                        value -> {
                            settings.hudOpacity = (float) value;
                            settings.save();
                        },
                        value -> Math.round(value * 100.0D) + "%"), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.hud_scale"), 0.6D, 1.8D,
                        () -> settings.hudScale,
                        value -> {
                            settings.hudScale = (float) value;
                            settings.save();
                        },
                        value -> String.format(Locale.ROOT, "%.2fx", value)), y);
                y += 24 + ROW_GAP;
                // Not a HUD switch, but it is the one visual setting that changes the world rather than
                // the overlay, so it belongs next to the other "how does my game look" rows.
                content.add(toggle("asweeth.option.fullbright",
                        () -> settings.fullbright, value -> settings.fullbright = value), y);
                y += ROW_HEIGHT;
            }
            case HUD -> {
                content.add(toggle("asweeth.option.fps",
                        () -> settings.showFps, value -> settings.showFps = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.ping",
                        () -> settings.showPing, value -> settings.showPing = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.memory",
                        () -> settings.showMemory, value -> settings.showMemory = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.chunks",
                        () -> settings.showChunks, value -> settings.showChunks = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.lite_badge",
                        () -> settings.showLiteBadge, value -> settings.showLiteBadge = value), y);
                y += ROW_HEIGHT;
            }
            case KEYS -> {
                content.add(toggle("asweeth.option.keystrokes",
                        () -> settings.keystrokesEnabled, value -> settings.keystrokesEnabled = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassCycleButton<HudAnchor>(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.keystroke_position"), HudAnchor.values(),
                        () -> settings.keystrokeAnchor,
                        value -> {
                            settings.keystrokeAnchor = value;
                            settings.save();
                        },
                        HudAnchor::label, 92), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.keystrokes_mouse",
                        () -> settings.keystrokesMouse, value -> settings.keystrokesMouse = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.keystrokes_size"), 0.6D, 1.8D,
                        () -> settings.keystrokeScale,
                        value -> {
                            settings.keystrokeScale = (float) value;
                            settings.save();
                        },
                        value -> String.format(Locale.ROOT, "%.2fx", value)), y);
                y += 24 + ROW_GAP;
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.keystrokes_opacity"), 0.2D, 1.0D,
                        () -> settings.keystrokeOpacity,
                        value -> {
                            settings.keystrokeOpacity = (float) value;
                            settings.save();
                        },
                        value -> Math.round(value * 100.0D) + "%"), y);
                y += 24 + ROW_GAP;
                content.add(new GlassCycleButton<KeystrokeTint>(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.keystrokes_colors"), KeystrokeTint.values(),
                        () -> settings.keystrokeTint,
                        value -> {
                            settings.keystrokeTint = value;
                            settings.save();
                        },
                        KeystrokeTint::label, 92), y);
                y += ROW_HEIGHT + ROW_GAP;
                // The zoom lens lives here rather than on the HUD tab: it is a key binding, not a HUD
                // readout, and this is where a player looks after pressing the zoom key for the first time.
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.zoom_factor"),
                        Zoom.MIN_FACTOR, Zoom.MAX_FACTOR,
                        () -> settings.zoomFactor,
                        value -> {
                            settings.zoomFactor = (float) value;
                            settings.save();
                        },
                        value -> String.format(Locale.ROOT, "%.1fx", value)), y);
                y += 24;
            }
            case THEME -> {
                content.add(new GlassCycleButton<ThemeMode>(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.theme"), ThemeMode.values(),
                        () -> settings.theme,
                        value -> {
                            settings.theme = value;
                            settings.save();
                        },
                        mode -> Component.literal(mode.displayName()), 92), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.animations",
                        () -> settings.animations, value -> settings.animations = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.vanilla_theme",
                        () -> settings.themeVanillaScreens, value -> settings.themeVanillaScreens = value), y);
                y += ROW_HEIGHT;
            }
            case PERFORMANCE -> {
                content.add(new GlassCycleButton<PerformancePreset>(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.preset"), PerformancePreset.values(),
                        () -> settings.preset, value -> settings.preset = value,
                        PerformancePreset::label, 92), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassButton(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.apply_preset"), true, () -> {
                            ClientSettings.get().preset.apply(this.minecraft);
                            rebuildWidgets();
                        }), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.smart_culling",
                        () -> settings.smartCulling, value -> settings.smartCulling = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.fast_menus",
                        () -> settings.fastMenus, value -> settings.fastMenus = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.chunk_stream",
                        () -> settings.chunkStreamAid, value -> settings.chunkStreamAid = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.adaptive_distance",
                        () -> settings.adaptiveRenderDistance, value -> settings.adaptiveRenderDistance = value), y);
                y += ROW_HEIGHT;
            }
            case OPTIMISATION -> {
                content.add(toggle("asweeth.option.fast_math",
                        () -> settings.fastMath, value -> settings.fastMath = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.fast_panels",
                        () -> settings.fastPanels, value -> settings.fastPanels = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(toggle("asweeth.option.cache_readouts",
                        () -> settings.cacheReadouts, value -> settings.cacheReadouts = value), y);
                y += ROW_HEIGHT + ROW_GAP;
                content.add(new GlassSlider(0, 0, 0, 24,
                        Component.translatable("asweeth.option.target_fps"), 30.0D, 360.0D,
                        () -> settings.targetFps,
                        value -> {
                            settings.targetFps = (int) Math.round(value);
                            settings.save();
                        },
                        value -> Integer.toString((int) Math.round(value))), y);
                y += 24 + ROW_GAP;
                content.add(new GlassButton(0, 0, 0, ROW_HEIGHT,
                        Component.translatable("asweeth.option.reapply"), false, () -> {
                            Optimisations.invalidate();
                            rebuildWidgets();
                        }), y);
                y += ROW_HEIGHT;
            }
            case ABOUT -> {
                // Text only: the block below the rows carries everything.
            }
        }

        y = addTabExtras(y);
        content.setContentHeight(y + 4);
    }

    /**
     * The per-tab previews and help text, added below the controls so they scroll with them.
     *
     * <p>Everything here used to be painted at a fixed offset by the screen and simply vanished when the
     * window was too short; as content rows they keep their place in the flow instead.</p>
     */
    private int addTabExtras(int y) {
        int width = content.rowWidth();
        switch (active) {
            case GENERAL -> {
                return addHints(y + 6, false, "asweeth.hud.hint",
                        "asweeth.fullbright.hint", "asweeth.fullbright.hint2");
            }
            case KEYS -> {
                return addHints(y + 6, false, "asweeth.keys.hint", "asweeth.keys.hint2");
            }
            case THEME -> {
                int swatchTop = y + 8;
                int swatchHeight = 30;
                content.addBlock((graphics, mouseX, mouseY, partialTick) ->
                        drawSwatches(graphics, swatchTop, swatchHeight, width), swatchTop);
                return addHints(swatchTop + swatchHeight + 6, false, "asweeth.theme.hint");
            }
            case PERFORMANCE -> {
                PerformancePreset preset = ClientSettings.get().preset;
                content.addBlock(new HintBlock(0, 0, width, true,
                        preset.description(), Component.translatable("asweeth.preset.hint")), y + 6);
                return y + 6 + HintBlock.height(2);
            }
            case OPTIMISATION -> {
                int distance = this.minecraft.options.renderDistance().get();
                String stats = "FPS " + this.minecraft.getFps()
                        + "  RD " + distance + "/" + Optimisations.MAX_RENDER_DISTANCE
                        + "  " + (ClientSettings.get().adaptiveRenderDistance
                        ? "auto " + ClientSettings.get().targetFps : "fixed");
                content.addBlock(new HintBlock(0, 0, width, true,
                        Component.literal(stats),
                        Component.translatable("asweeth.optimisation.hint"),
                        Component.translatable("asweeth.optimisation.hint2")), y + 6);
                return y + 6 + HintBlock.height(3);
            }
            case ABOUT -> {
                int top = y + 6;
                content.addBlock((graphics, mouseX, mouseY, partialTick) ->
                        drawAbout(graphics, top, width), top);
                return top + aboutHeight();
            }
            default -> {
                return y;
            }
        }
    }

    /** Adds one help block and returns the pixel below it. */
    private int addHints(int y, boolean accentFirst, String... keys) {
        Component[] lines = new Component[keys.length];
        for (int i = 0; i < keys.length; i++) {
            lines[i] = Component.translatable(keys[i]);
        }
        content.addBlock(new HintBlock(0, 0, content.rowWidth(), accentFirst, lines), y);
        return y + HintBlock.height(lines.length);
    }
    /** Live palette preview: one swatch per theme, outlined with the active one's accent. */
    private void drawSwatches(GuiGraphicsExtractor graphics, int contentY, int height, int width) {
        ThemeMode theme = Theme.current();
        ThemeMode[] modes = ThemeMode.values();
        int gap = 6;
        int swatchWidth = (width - gap * (modes.length - 1)) / modes.length;
        int top = content.getY() + contentY - content.visibleOffset();
        int left = content.getX();
        for (int i = 0; i < modes.length; i++) {
            ThemeMode mode = modes[i];
            int x = left + i * (swatchWidth + gap);
            boolean chosen = mode == ClientSettings.get().theme;
            int outline = chosen ? theme.accent() : mode.outline();
            Render2D.roundedRect(graphics, x, top, swatchWidth, height, 4, outline, outline);
            Render2D.roundedRect(graphics, x + 1, top + 1, swatchWidth - 2, height - 2, 3,
                    mode.panelTop(), mode.panelBottom());
            Render2D.accentBar(graphics, x + 4, top + height - 6, swatchWidth - 8, 2, mode);
            String name = fit(mode.displayName(), swatchWidth - 6);
            Render2D.textCentered(graphics, this.font, Component.literal(name), x + swatchWidth / 2,
                    top + 6, mode.text());
        }
    }

    /** The credits block, drawn as one scrollable row. */
    private void drawAbout(GuiGraphicsExtractor graphics, int contentY, int width) {
        ThemeMode theme = Theme.current();
        String[] lines = {
                AsweetH.CLIENT_NAME + " " + AsweetH.VERSION,
                "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name(),
                "Forge 66.1.2",
                "Theme: " + theme.displayName()
        };
        int y = content.getY() + contentY - content.visibleOffset();
        for (String line : lines) {
            Render2D.text(graphics, this.font, Render2D.fit(this.font, line, width),
                    content.getX(), y, theme.text());
            y += Render2D.LINE_STEP + 4;
        }
        y += 6;
        Render2D.accentBar(graphics, content.getX(), y, width, 1, theme);
        y += 8;
        Render2D.text(graphics, this.font,
                Render2D.fit(this.font, Component.translatable("asweeth.about.hint").getString(), width),
                content.getX(), y, theme.textDim());
    }

    /** Height of {@link #drawAbout}, so the scroll range matches what is painted. */
    private static int aboutHeight() {
        return 4 * (Render2D.LINE_STEP + 4) + 6 + 1 + 8 + Render2D.LINE_STEP + 6;
    }

    /**
     * Builds a switch row; the scroll panel assigns its real x, width and y when it is added.
     */
    private GlassToggle toggle(String key, java.util.function.BooleanSupplier getter,
                               java.util.function.Consumer<Boolean> setter) {
        ClientSettings settings = ClientSettings.get();
        return new GlassToggle(0, 0, 0, ROW_HEIGHT, Component.translatable(key), getter, value -> {
            setter.accept(value);
            settings.save();
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, Theme.current().screenTint());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();
        drawPanel(graphics, theme);
        // Widgets are painted after the chrome so controls always sit on top of the glass.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Paints the sheet, the header and the footer. */
    private void drawPanel(GuiGraphicsExtractor graphics, ThemeMode theme) {
        Render2D.glassPanel(graphics, panelX, panelY, panelWidth, panelHeight, PANEL_RADIUS, theme);
        Render2D.accentBar(graphics, panelX + PANEL_RADIUS, panelY + 1, panelWidth - PANEL_RADIUS * 2, 2, theme);

        // Brand block, top left of the rail.
        String brand = "ASweet";
        Render2D.text(graphics, this.font, brand, panelX + 10, panelY + 9, theme.text());
        int brandWidth = this.font.width(brand);
        Render2D.text(graphics, this.font, "H", panelX + 10 + brandWidth, panelY + 9, theme.accent());
        Render2D.text(graphics, this.font, "v" + AsweetH.VERSION, panelX + 10 + brandWidth
                + this.font.width("H") + 3, panelY + 9, theme.textDim());

        // Header separator spans the whole sheet; the rail gets its own vertical divider.
        Render2D.hairline(graphics, panelX + 8, panelY + HEADER - 4, panelWidth - 16, theme.outline());
        int railDividerX = panelX + RAIL_WIDTH + GUTTER / 2;
        Render2D.hairline(graphics, railDividerX, panelY + HEADER, 1, Colors.multiplyAlpha(theme.outline(), 0.8F));

        // Content header: tab name plus its one line explanation.
        Render2D.text(graphics, this.font, active.label(), contentX + 2, contentY + 2, theme.accent());
        Render2D.text(graphics, this.font, fit(active.description().getString(), contentWidth - 4),
                contentX + 2, contentY + 13, theme.textDim());

        // Footer.
        Render2D.hairline(graphics, panelX + 8, panelY + panelHeight - FOOTER, panelWidth - 16, theme.outline());
        Render2D.text(graphics, this.font, AsweetH.CLIENT_NAME + " " + AsweetH.VERSION, panelX + 10,
                panelY + panelHeight - FOOTER + 10, theme.textDim());
    }

    /** Truncates {@code text} with an ellipsis so nothing ever spills out of the sheet. */
    private String fit(String text, int maxWidth) {
        return Render2D.fit(this.font, text, maxWidth);
    }

    @Override
    public void onClose() {
        ClientSettings.get().save();
        // 26.3 routes screen changes through the Gui: a null screen hands control back to the game.
        this.minecraft.gui.setScreen(parent);
    }
}
