package dev.asweeth.client.hud;

import net.minecraft.network.chat.Component;

/**
 * Colour schemes for the keystroke overlay.
 *
 * <p>All three reuse the active {@code ThemeMode}, so the corner never clashes with the rest of the
 * client: {@code THEME} is plain glass, {@code ACCENT} tints the caps with the palette accent and
 * {@code MONO} drops the palette entirely for players who want a neutral, high contrast readout.</p>
 */
public enum KeystrokeTint {
    THEME("asweeth.tint.theme"),
    ACCENT("asweeth.tint.accent"),
    MONO("asweeth.tint.mono");

    private final String labelKey;

    KeystrokeTint(String labelKey) {
        this.labelKey = labelKey;
    }

    public Component label() {
        return Component.translatable(labelKey);
    }

    public KeystrokeTint next() {
        KeystrokeTint[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
