package dev.asweeth.client.hud;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * Where each HUD element landed on the last frame, in GUI pixels.
 *
 * <p>The HUD layers already know their own size after scaling; the editor does not, and recomputing
 * seven widths from the font and the settings a second time would be both duplicated code and a
 * source of drift. So the layers publish their bounds here as they draw and the editor hit tests
 * against exactly what the player sees.</p>
 *
 * <p>Only touched from the render thread, which is the same thread that runs the editor, so no
 * synchronisation is needed. The map is cleared when the HUD hides itself, which keeps a stale
 * rectangle from an old window size out of the hit test.</p>
 */
public final class HudBounds {
    private static final Map<HudElement, ScreenRectangle> BOUNDS = new EnumMap<>(HudElement.class);

    private HudBounds() {
    }

    /** Records the on screen rectangle of {@code element}. */
    public static void put(HudElement element, int x, int y, int width, int height) {
        BOUNDS.put(element, new ScreenRectangle(x, y, Math.max(1, width), Math.max(1, height)));
    }

    /** The rectangle {@code element} occupied on the last frame, or {@code null} if it was not drawn. */
    public static ScreenRectangle get(HudElement element) {
        return BOUNDS.get(element);
    }

    public static void clear() {
        BOUNDS.clear();
    }
}
