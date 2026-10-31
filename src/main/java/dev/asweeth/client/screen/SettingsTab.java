package dev.asweeth.client.screen;

import net.minecraft.network.chat.Component;

/**
 * The pages of the ASweetH settings screen.
 *
 * <p>Adding a page means adding a constant here plus a {@code case} in
 * {@code AsweetHScreen#populate}, which is the whole reason the screen is tab driven instead of one
 * long scrolling list.</p>
 */
public enum SettingsTab {
    GENERAL("asweeth.tab.general", "asweeth.tab.general.desc"),
    HUD("asweeth.tab.hud", "asweeth.tab.hud.desc"),
    KEYS("asweeth.tab.keys", "asweeth.tab.keys.desc"),
    THEME("asweeth.tab.theme", "asweeth.tab.theme.desc"),
    PERFORMANCE("asweeth.tab.performance", "asweeth.tab.performance.desc"),
    OPTIMISATION("asweeth.tab.optimisation", "asweeth.tab.optimisation.desc"),
    ABOUT("asweeth.tab.about", "asweeth.tab.about.desc");

    private final String labelKey;
    private final String descriptionKey;

    SettingsTab(String labelKey, String descriptionKey) {
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
    }

    public Component label() {
        return Component.translatable(labelKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }
}
