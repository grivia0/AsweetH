package dev.asweeth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.asweeth.AsweetH;
import dev.asweeth.client.Zoom;
import dev.asweeth.client.hud.HudAnchor;
import dev.asweeth.client.hud.HudElement;
import dev.asweeth.client.hud.HudLayout;
import dev.asweeth.client.hud.KeystrokeTint;
import dev.asweeth.client.perf.PerformancePreset;
import dev.asweeth.theme.ThemeMode;
import java.io.BufferedReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
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
    /**
     * Per element position, scale and opacity, keyed by {@link HudElement}.
     *
     * <p>One map instead of a dozen floats: the HUD gained a per element editor, and seven elements
     * times three settings would otherwise mean twenty one near identical fields, twenty one clamps and
     * twenty one places to forget when an element is added. Declared as {@code Map} rather than
     * {@code EnumMap} because Gson instantiates interfaces through its own map type; the file is
     * re-keyed into an {@link EnumMap} by {@link #sanitizeLayouts()}.</p>
     */
    public Map<HudElement, HudLayout> hudLayouts = new EnumMap<>(HudElement.class);

    /** Draws the WASD / space / mouse readout. Position and size live in {@link #hudLayouts}. */
    public boolean keystrokesEnabled = true;
    /** Adds the left and right mouse buttons to that readout. */
    public boolean keystrokesMouse = true;
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
                // Read as a tree first so the migration below can still see the keys the single block
                // layout of v0.1.0 used; Gson drops keys that no longer exist when it binds the object.
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    ClientSettings loaded = GSON.fromJson(root, ClientSettings.class);
                    if (loaded != null) {
                        loaded.migrate(root);
                        loaded.sanitize();
                        return loaded;
                    }
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
        sanitizeLayouts();
        zoomFactor = clampZoom(zoomFactor);
        targetFps = Math.max(30, Math.min(360, targetFps));
    }

    /**
     * Layout of {@code element}, creating the default the first time it is asked for.
     *
     * <p>The HUD layers call this every frame for every element, so it must not allocate once the map
     * is populated; a default is only built for an element that a config file did not mention.</p>
     */
    public HudLayout layout(HudElement element) {
        Map<HudElement, HudLayout> map = layouts();
        HudLayout layout = map.get(element);
        if (layout == null) {
            layout = element.defaultLayout();
            map.put(element, layout);
        }
        return layout;
    }

    /** Restores one element to its default position, scale and opacity. */
    public void resetLayout(HudElement element) {
        layouts().put(element, element.defaultLayout());
    }

    /** Restores every element, behind the editor's "Reset all" button. */
    public void resetLayouts() {
        for (HudElement element : HudElement.values()) {
            resetLayout(element);
        }
    }

    /** The layout map, created if the field is null (a config written before the editor existed). */
    private Map<HudElement, HudLayout> layouts() {
        Map<HudElement, HudLayout> map = hudLayouts;
        if (map == null) {
            map = new EnumMap<>(HudElement.class);
            hudLayouts = map;
        }
        return map;
    }

    /**
     * Normalises the layout map: fills in missing elements, clamps the values and re-keys the map into
     * an {@link EnumMap}.
     *
     * <p>Gson binds a {@code Map} field to a {@code LinkedTreeMap}, whose enum keys work but whose
     * lookups compare strings instead of switching on the ordinal. Re-keying also drops an entry whose
     * key is no longer a valid element - a layout written by a build that had an extra readout - so one
     * stale key cannot fail the whole config load.</p>
     */
    private void sanitizeLayouts() {
        Map<HudElement, HudLayout> existing = hudLayouts;
        if (existing != null && !(existing instanceof EnumMap)) {
            Map<HudElement, HudLayout> rekeyed = new EnumMap<>(HudElement.class);
            for (Map.Entry<HudElement, HudLayout> entry : existing.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    rekeyed.put(entry.getKey(), entry.getValue());
                }
            }
            hudLayouts = rekeyed;
        }
        for (HudElement element : HudElement.values()) {
            layout(element).sanitize(element.defaultLayout());
        }
    }

    /**
     * Carries a v0.1.0 config over to per element layouts.
     *
     * <p>Before the editor the HUD had one anchor, scale and opacity for the watermark block and a
     * second trio for the keystrokes. Those fields no longer exist, so their values are read from the
     * raw tree: copying them onto every element keeps an upgraded player's HUD where it was instead of
     * snapping everything back to the top left at full size.</p>
     */
    private void migrate(JsonObject root) {
        if (root.has("hudLayouts")) {
            return;
        }
        HudAnchor hudAnchor = anchor(root, "hudAnchor", HudAnchor.TOP_LEFT);
        HudAnchor keyAnchor = anchor(root, "keystrokeAnchor", HudAnchor.BOTTOM_LEFT);
        float hudScale = number(root, "hudScale", 1.0F);
        float hudOpacity = number(root, "hudOpacity", 1.0F);
        float keyScale = number(root, "keystrokeScale", 1.0F);
        float keyOpacity = number(root, "keystrokeOpacity", 0.9F);
        for (HudElement element : HudElement.values()) {
            boolean keys = element == HudElement.KEYSTROKES;
            HudLayout layout = element.defaultLayout();
            layout.anchor = keys ? keyAnchor : hudAnchor;
            layout.scale = keys ? keyScale : hudScale;
            layout.opacity = keys ? keyOpacity : hudOpacity;
            layouts().put(element, layout);
        }
    }

    /** Reads an anchor name, falling back on a hand edited or renamed value. */
    private static HudAnchor anchor(JsonObject root, String key, HudAnchor fallback) {
        try {
            return root.has(key) ? HudAnchor.valueOf(root.get(key).getAsString()) : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    /** Reads a number, falling back on a hand edited value. */
    private static float number(JsonObject root, String key, float fallback) {
        try {
            return root.has(key) ? root.get(key).getAsFloat() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
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
}
