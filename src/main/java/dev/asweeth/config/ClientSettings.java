package dev.asweeth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.asweeth.AsweetH;
import dev.asweeth.client.Zoom;
import dev.asweeth.client.hud.HudAnchor;
import dev.asweeth.client.hud.KeystrokeTint;
import dev.asweeth.client.perf.PerformancePreset;
import dev.asweeth.theme.ThemeMode;
import java.io.BufferedReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything ASweetH remembers between launches, stored as one small JSON file in {@code config/}.
 *
 * <p>The file is written through {@link FMLPaths#CONFIGDIR} rather than the game directory so that a
 * pack or launcher that relocates the instance keeps the settings next to the other mods' configs.</p>
 */
public final class ClientSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsweetH.CLIENT_NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = AsweetH.MOD_ID + ".json";

    private static volatile ClientSettings instance;

    /** Active palette. Never {@code null} once {@link #get()} has run. */
    public ThemeMode theme = ThemeMode.ROSE_GLASS;
    /** Last preset the player applied from the Performance tab. */
    public PerformancePreset preset = PerformancePreset.STANDARD;

    public boolean hudEnabled = true;
    public boolean showWatermark = true;
    public boolean showCoordinates = true;
    public boolean showFps = true;
    public boolean showPing = true;
    public boolean showMemory = false;
    public boolean showChunks = true;
    public boolean showLiteBadge = true;
    /** Uniform scale applied to the whole HUD, 0.6 - 1.8. */
    public float hudScale = 1.0F;
    /** Corner the HUD block is pinned to. */
    public HudAnchor hudAnchor = HudAnchor.TOP_LEFT;
    /** Alpha multiplier for the HUD panels and their text, 0.2 - 1.0. */
    public float hudOpacity = 1.0F;

    /** Draws the WASD / space / mouse readout in the bottom left corner. */
    public boolean keystrokesEnabled = true;
    /** Adds the left and right mouse buttons to that readout. */
    public boolean keystrokesMouse = true;
    /** Uniform scale of the keystroke block, 0.6 - 1.8. */
    public float keystrokeScale = 1.0F;
    /** Corner the keystroke block is pinned to. */
    public HudAnchor keystrokeAnchor = HudAnchor.BOTTOM_LEFT;
    /** Alpha multiplier for the key caps, 0.2 - 1.0. */
    public float keystrokeOpacity = 0.9F;
    /** Which colour scheme the key caps use. */
    public KeystrokeTint keystrokeTint = KeystrokeTint.THEME;

    /** Drives hover glides and the tab indicator. */
    public boolean animations = true;

    /** Zoom lens factor while {@link dev.asweeth.client.KeyBinds#ZOOM} is held, 1.5 - 10.0. */
    public float zoomFactor = 4.0F;

    // --- World rendering --------------------------------------------------------------------
    /**
     * Lifts the lightmap so unlit caves are visible, driven by {@code LightmapRenderStateExtractorMixin}.
     *
     * <p>Off by default: it is a changed world, not a fixed bug, so a player who never opens the menu
     * keeps vanilla lighting. The mixin reads this flag every frame, so toggling takes effect within a
     * tick without touching the render pipeline.</p>
     */
    public boolean fullbright = false;

    // --- Built-in optimisations -------------------------------------------------------------
    /** Lookup-table exponentials and fixed point colour maths instead of libm per call. */
    public boolean fastMath = true;
    /** Collapses the straight part of every panel into one gradient quad. */
    public boolean fastPanels = true;
    /** Recomputes the FPS/ping/memory strings a few times per second instead of every frame. */
    public boolean cacheReadouts = true;
    /** Keeps vanilla's entity/AABB smart culling enabled. */
    public boolean smartCulling = true;
    /** Drops the menu background blur, which costs several milliseconds per frame while a screen is open. */
    public boolean fastMenus = true;
    /** Removes chunk fade-in churn and caps biome blending at high render distances. */
    public boolean chunkStreamAid = true;
    /** Steps the render distance down (and back up) to hold the target framerate. */
    public boolean adaptiveRenderDistance = false;
    /** Framerate the adaptive governor tries to hold, 30 - 360. */
    public int targetFps = 120;
    /** Paints the palette gradient over vanilla screens. */
    public boolean themeVanillaScreens = true;

    private ClientSettings() {
    }

    /**
     * Returns the single settings instance, loading it on first use.
     *
     * <p>Double checked with a {@code volatile} field: the render thread reads this several times per
     * frame (theme, animation flags, optimisation switches), so it must not take a monitor.</p>
     */
    public static ClientSettings get() {
        ClientSettings local = instance;
        if (local == null) {
            synchronized (ClientSettings.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Applies the persisted values again, e.g. after an external edit of the JSON file. */
    public static synchronized void reload() {
        instance = load();
    }

    public void save() {
        Path file = file();
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not write ASweetH settings to {}", file, exception);
        }
    }

    private static ClientSettings load() {
        Path file = file();
        if (file != null && Files.isRegularFile(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ClientSettings loaded = GSON.fromJson(reader, ClientSettings.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            } catch (Exception exception) {
                LOGGER.warn("Could not read ASweetH settings from {} - using defaults", file, exception);
            }
        }
        return new ClientSettings();
    }

    private static Path file() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        } catch (RuntimeException exception) {
            LOGGER.warn("Config directory is not available yet", exception);
            return null;
        }
    }

    /** Defensive fix-ups for hand edited or older config files. */
    private void sanitize() {
        if (theme == null) {
            theme = ThemeMode.ROSE_GLASS;
        }
        if (preset == null) {
            preset = PerformancePreset.STANDARD;
        }
        if (keystrokeTint == null) {
            keystrokeTint = KeystrokeTint.THEME;
        }
        if (hudAnchor == null) {
            hudAnchor = HudAnchor.TOP_LEFT;
        }
        if (keystrokeAnchor == null) {
            keystrokeAnchor = HudAnchor.BOTTOM_LEFT;
        }
        hudScale = clamp(hudScale, 0.6F, 1.8F, 1.0F);
        hudOpacity = clampOpacity(hudOpacity, 1.0F);
        keystrokeScale = clamp(keystrokeScale, 0.6F, 1.8F, 1.0F);
        keystrokeOpacity = clamp(keystrokeOpacity, 0.2F, 1.0F, 0.9F);
        zoomFactor = clampZoom(zoomFactor);
        targetFps = Math.max(30, Math.min(360, targetFps));
    }

    /**
     * Clamps the zoom factor, reading a zero as "the file predates this setting" for the same reason
     * {@link #clampOpacity} does: Gson skips field initialisers, so a missing key arrives as {@code 0}
     * and clamping that up to the minimum would leave an upgraded player with a 1.5x lens.
     */
    private static float clampZoom(float value) {
        if (!(value > 0.0F)) {
            return Zoom.DEFAULT_FACTOR;
        }
        return Math.max(Zoom.MIN_FACTOR, Math.min(Zoom.MAX_FACTOR, value));
    }

    /**
     * Clamps an opacity to 0.2 - 1.0, but reads a zero as "the file predates this setting".
     *
     * <p>Gson builds instances without running the field initialisers, so any key missing from an
     * older config file leaves the field at {@code 0}. Clamping that to the minimum would make the
     * HUD almost invisible the first time a player updates, so zero (and NaN) falls back instead.</p>
     */
    private static float clampOpacity(float value, float fallback) {
        if (!(value > 0.0F)) {
            return fallback;
        }
        return Math.max(0.2F, Math.min(1.0F, value));
    }

    private static float clamp(float value, float min, float max, float fallback) {
        // NaN survives Math.min/max, and Gson happily writes NaN for a hand edited "null" number.
        if (Float.isNaN(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
