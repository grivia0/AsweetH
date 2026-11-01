package dev.asweeth.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.asweeth.AsweetH;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/**
 * ASweetH's key bindings, all grouped under their own controls category so they do not pollute the
 * vanilla sections of the controls screen.
 */
public final class KeyBinds {
    /** "ASweetH" section in Options -> Controls -> Key Binds. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AsweetH.MOD_ID, "controls"));

    /** Opens the tabbed settings screen. Right shift is the classic client-menu key. */
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.asweeth.open_menu",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY);

    /** Shows or hides the ASweetH HUD without opening the menu. */
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.asweeth.toggle_hud",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY);

    /** Shows or hides the WASD / mouse readout, which sits in the corner picked in the Keys tab. */
    public static final KeyMapping TOGGLE_KEYSTROKES = new KeyMapping("key.asweeth.toggle_keystrokes",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY);

    /** Cycles the active performance preset (Standard -> Lite -> Potato -> ...). */
    public static final KeyMapping CYCLE_PRESET = new KeyMapping("key.asweeth.cycle_preset",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_L, CATEGORY);

    /** Held down for the zoom lens; the factor is set in the Keys tab. */
    public static final KeyMapping ZOOM = new KeyMapping("key.asweeth.zoom",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY);

    /** Puts the block coordinates on the clipboard, ready to paste into chat or Discord. */
    public static final KeyMapping COPY_COORDS = new KeyMapping("key.asweeth.copy_coords",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY);

    /**
     * Opens the drag editor for the HUD. F6 keeps it clear of the menu key and of the WASD side of the
     * keyboard, since the editor is used while looking at the corners the HUD lives in.
     */
    public static final KeyMapping OPEN_HUD_EDITOR = new KeyMapping("key.asweeth.open_hud_editor",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, CATEGORY);

    private KeyBinds() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(TOGGLE_HUD);
        event.register(TOGGLE_KEYSTROKES);
        event.register(CYCLE_PRESET);
        event.register(ZOOM);
        event.register(COPY_COORDS);
        event.register(OPEN_HUD_EDITOR);
    }
}
