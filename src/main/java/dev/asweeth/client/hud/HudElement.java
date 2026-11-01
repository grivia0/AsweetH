package dev.asweeth.client.hud;

import dev.asweeth.config.ClientSettings;
import net.minecraft.network.chat.Component;

/**
 * One drawable piece of the ASweetH HUD.
 *
 * <p>The enum exists so the layout map in {@link ClientSettings}, the watermark/readout layer and the
 * HUD editor all talk about the same seven things: the editor can then iterate {@link #values()} and
 * find the screen bounds of each element without a hand written switch in three places. Each constant
 * knows how to read its own visibility flag, which is what lets the editor's "shown" row work for the
 * keystrokes block, whose switch lives in a different section of the config than the stat readouts.</p>
 */
public enum HudElement {
    WATERMARK("asweeth.element.watermark"),
    FPS("asweeth.element.fps"),
    PING("asweeth.element.ping"),
    COORDINATES("asweeth.element.coordinates"),
    CHUNKS("asweeth.element.chunks"),
    MEMORY("asweeth.element.memory"),
    KEYSTROKES("asweeth.element.keystrokes");

    private final String labelKey;
    /** Built lazily: enum constants are created while the config is still loading. */
    private Component label;

    HudElement(String labelKey) {
        this.labelKey = labelKey;
    }

    public Component label() {
        Component local = label;
        if (local == null) {
            local = Component.translatable(labelKey);
            label = local;
        }
        return local;
    }

    /** Whether this element's own switch in the settings is on. */
    public boolean enabled(ClientSettings settings) {
        return switch (this) {
            case WATERMARK -> settings.showWatermark;
            case FPS -> settings.showFps;
            case PING -> settings.showPing;
            case COORDINATES -> settings.showCoordinates;
            case CHUNKS -> settings.showChunks;
            case MEMORY -> settings.showMemory;
            case KEYSTROKES -> settings.keystrokesEnabled;
        };
    }

    /** Flips this element's visibility flag, used by the editor's "shown" toggle. */
    public void setEnabled(ClientSettings settings, boolean value) {
        switch (this) {
            case WATERMARK -> settings.showWatermark = value;
            case FPS -> settings.showFps = value;
            case PING -> settings.showPing = value;
            case COORDINATES -> settings.showCoordinates = value;
            case CHUNKS -> settings.showChunks = value;
            case MEMORY -> settings.showMemory = value;
            case KEYSTROKES -> settings.keystrokesEnabled = value;
        }
    }

    /**
     * Where this element sits on a fresh install.
     *
     * <p>The five stat pills stack under the watermark in fixed 15 pixel steps (a 13 pixel pill plus a
     * 2 pixel gap) so the column lines up out of the box, and the keystroke block keeps its usual
     * bottom left home at 90% opacity. Disabling one pill leaves its slot empty rather than sliding the
     * others up, which keeps a layout stable while the player toggles rows in the editor.</p>
     */
    public HudLayout defaultLayout() {
        return switch (this) {
            case WATERMARK -> new HudLayout(HudAnchor.TOP_LEFT, 0, 0, 1.0F, 1.0F);
            case FPS -> new HudLayout(HudAnchor.TOP_LEFT, 0, 21, 1.0F, 1.0F);
            case PING -> new HudLayout(HudAnchor.TOP_LEFT, 0, 36, 1.0F, 1.0F);
            case COORDINATES -> new HudLayout(HudAnchor.TOP_LEFT, 0, 51, 1.0F, 1.0F);
            case CHUNKS -> new HudLayout(HudAnchor.TOP_LEFT, 0, 66, 1.0F, 1.0F);
            case MEMORY -> new HudLayout(HudAnchor.TOP_LEFT, 0, 81, 1.0F, 1.0F);
            case KEYSTROKES -> new HudLayout(HudAnchor.BOTTOM_LEFT, 0, 0, 1.0F, 0.9F);
        };
    }
}

