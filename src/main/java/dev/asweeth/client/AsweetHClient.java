package dev.asweeth.client;

import com.mojang.logging.LogUtils;
import dev.asweeth.AsweetH;
import dev.asweeth.client.screen.AsweetHScreen;
import dev.asweeth.config.ClientSettings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Client side wiring for ASweetH that belongs on the {@code MOD} bus.
 *
 * <p>Only startup lifecycle events live here, and the settings are read once so the first frame already
 * draws in the right theme. Key bindings and the HUD overlay layer are <em>not</em> mod bus events in
 * 26.3 - {@code RegisterKeyMappingsEvent} and {@code AddGuiOverlayLayersEvent} are fired on the game
 * bus, so they are handled by {@link ClientEvents}.</p>
 *
 * <p>The class is annotated with {@code value = Dist.CLIENT}, so Forge never loads it on a dedicated
 * server even though the mod jar ships both sides.</p>
 */
@Mod.EventBusSubscriber(modid = AsweetH.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AsweetHClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AsweetHClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientSettings settings = ClientSettings.get();
        LOGGER.info("{} {} ready - theme {}, preset {}, zoom {}x, fullbright {}",
                AsweetH.CLIENT_NAME, AsweetH.VERSION, settings.theme.displayName(), settings.preset.name(),
                settings.zoomFactor, settings.fullbright ? "on" : "off");
        // Forge skips a Fabric jar without saying so, so point the player at the real reason.
        FabricModCheck.warnIfPresent();
    }

    /**
     * Hands the settings screen to Forge so the mods list gets a "Config" button for ASweetH.
     *
     * <p>Extension points have to be registered while the mod is still being constructed, which is why
     * this is called from the {@link AsweetH} constructor instead of from {@link #onClientSetup}. The
     * factory receives the vanilla parent screen, so Done in ASweetH returns to the mod list rather
     * than dropping the player back into the game.</p>
     */
    public static void registerConfigScreen(FMLJavaModLoadingContext context) {
        context.getContainer().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new AsweetHScreen(parent)));
        LOGGER.info("{} settings screen registered as the mods list config entry", AsweetH.CLIENT_NAME);
    }
}
