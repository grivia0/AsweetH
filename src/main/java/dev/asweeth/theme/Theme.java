package dev.asweeth.theme;

import dev.asweeth.config.ClientSettings;

/**
 * Single point of access for "which palette am I drawing with right now".
 *
 * <p>Render code never reads {@link ClientSettings} directly, so a theme change instantly applies to
 * the HUD, the tabbed screen and every widget without any invalidation step.</p>
 */
public final class Theme {
    private Theme() {
    }

    public static ThemeMode current() {
        ThemeMode selected = ClientSettings.get().theme;
        return selected == null ? ThemeMode.ROSE_GLASS : selected;
    }
}
