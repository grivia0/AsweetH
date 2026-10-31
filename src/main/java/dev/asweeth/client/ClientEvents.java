package dev.asweeth.client;

import com.mojang.blaze3d.platform.ClipboardManager;
import dev.asweeth.AsweetH;
import dev.asweeth.client.hud.AsweetHHud;
import dev.asweeth.client.hud.KeystrokesHud;
import dev.asweeth.client.perf.Optimisations;
import dev.asweeth.client.screen.AsweetHScreen;
import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Game bus listeners: per-tick key handling plus the two registration events that create the client UI.
 *
 * <p>{@link net.minecraft.client.KeyMapping#consumeClick()} is drained in a {@code while} loop so a
 * press that arrives while the client is busy is never swallowed, and every branch checks that no
 * other screen is open before it acts.</p>
 *
 * <p>No {@code bus} parameter is given on purpose: {@code RegisterKeyMappingsEvent} and
 * {@code AddGuiOverlayLayersEvent} live on the default (game) bus group in 26.3, and asking for the
 * mod bus group makes Forge reject the whole subscriber class.</p>
 */
@Mod.EventBusSubscriber(modid = AsweetH.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsweetH.CLIENT_NAME);

    /** Single HUD instance: the layer itself is stateless, all state lives in {@link ClientSettings}. */
    private static final AsweetHHud HUD = new AsweetHHud();
    /** Bottom left key/mouse readout; kept as one instance so its press animation survives frames. */
    private static final KeystrokesHud KEYSTROKES = new KeystrokesHud();

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBinds.register(event);
    }

    /**
     * Inserts the ASweetH readout into the in-game HUD stack, as the last layer of that stack.
     *
     * <p>26.3 splits the vanilla overlay into named stacks: camera overlays, crosshair, hotbar, potion
     * icons and the boss bar live in {@code minecraft:pre_sleep}, while the scoreboard, chat, tab list
     * and titles live in {@code minecraft:post_sleep}. The three argument {@code addAbove} overload
     * only inspects the root stack it is called on and warns that the sibling is missing, so the
     * target stack has to be named explicitly:
     * {@code addAbove(stack, newLayer, existingLayer, layer)}.</p>
     */
    @SubscribeEvent
    public static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, AsweetHHud.ID,
                ForgeLayeredDraw.BOSS_OVERLAY, HUD);
        // Ordered against the HUD layer we just inserted, so the caps render on top of the readout.
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK, KeystrokesHud.ID,
                AsweetHHud.ID, KEYSTROKES);
        LOGGER.info("ASweetH HUD layer {} and keystroke layer {} registered above {}",
                AsweetHHud.ID, KeystrokesHud.ID, ForgeLayeredDraw.BOSS_OVERLAY);
    }

    /**
     * Paints the active palette over every vanilla screen background.
     *
     * <p>Forge fires this after the screen's own background (dirt, panorama or blurred world) and before
     * the widgets. The heavy lifting for vanilla menus happens at the resource level instead: ASweetH
     * ships replacements for the vanilla menu background tiles, the button/slider/text-field sprites and
     * the header/footer separators, so those are rose glass with a pink edge on every screen. This
     * handler only adds the vertical gradient and the hairline frame that ties the screen together, and
     * it stays subtle enough that the title screen panorama is still visible.</p>
     */
    @SubscribeEvent
    public static void onScreenBackground(ScreenEvent.BackgroundRendered event) {
        ClientSettings settings = ClientSettings.get();
        if (!settings.themeVanillaScreens) {
            return;
        }
        Screen screen = event.getScreen();
        if (screen instanceof AsweetHScreen || screen.width <= 0 || screen.height <= 0) {
            return;
        }
        ThemeMode theme = Theme.current();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        boolean inGame = screen.isInGameUi();
        int topAlpha = inGame ? 0x14 : 0x33;
        int bottomAlpha = inGame ? 0x2E : 0x73;
        graphics.fillGradient(0, 0, screen.width, screen.height,
                Colors.withAlpha(theme.panelTop(), topAlpha),
                Colors.withAlpha(theme.panelBottom(), bottomAlpha));
        if (!inGame) {
            // A flat hairline instead of a gradient bar: one quad instead of one per column.
            int top = Colors.withAlpha(theme.accent(), 0x8C);
            int bottom = Colors.withAlpha(theme.accentDeep(), 0x8C);
            graphics.fill(0, 0, screen.width, 1, top);
            graphics.fill(0, screen.height - 1, screen.width, screen.height, bottom);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        // Applies the optimisation switches and, when enabled, the adaptive render distance.
        Optimisations.tick(minecraft);
        // Samples the zoom key once per tick; the lens itself is stepped in the FOV event.
        Zoom.tick(minecraft);

        while (KeyBinds.OPEN_MENU.consumeClick()) {
            // 26.3 keeps the active screen on the Gui, not on the Minecraft instance itself.
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                minecraft.gui.setScreen(new AsweetHScreen(null));
            }
        }

        while (KeyBinds.TOGGLE_HUD.consumeClick()) {
            ClientSettings settings = ClientSettings.get();
            settings.hudEnabled = !settings.hudEnabled;
            settings.save();
        }

        while (KeyBinds.TOGGLE_KEYSTROKES.consumeClick()) {
            ClientSettings settings = ClientSettings.get();
            settings.keystrokesEnabled = !settings.keystrokesEnabled;
            settings.save();
        }

        while (KeyBinds.CYCLE_PRESET.consumeClick()) {
            ClientSettings settings = ClientSettings.get();
            settings.preset = settings.preset.next();
            settings.save();
        }

        while (KeyBinds.COPY_COORDS.consumeClick()) {
            copyCoordinates(minecraft);
        }
    }

    /**
     * Puts the player's block position on the system clipboard.
     *
     * <p>Block coordinates rather than the exact doubles: they are what a player types into chat or a
     * waypoint list, and the decimals are noise at that scale. The confirmation goes to the action bar
     * through vanilla's own chat listener, so it does not fill the chat log when the key is tapped
     * repeatedly.</p>
     */
    private static void copyCoordinates(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        String coordinates = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
        // The manager is a two line wrapper around SDL's clipboard, so building one per press is free
        // and avoids touching SDL during class initialisation.
        new ClipboardManager().setClipboard(coordinates);
        minecraft.gui.chatListener().handleOverlay(
                Component.translatable("asweeth.coords.copied", coordinates));
    }
}
