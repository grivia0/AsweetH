package dev.asweeth;

import com.mojang.logging.LogUtils;
import dev.asweeth.client.AsweetHClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Common entry point for ASweetH.
 *
 * <p>ASweetH is a client-side layer: everything user visible lives in {@code dev.asweeth.client}
 * and is only loaded through {@link net.minecraftforge.api.distmarker.Dist#CLIENT} events, so a
 * dedicated server never touches HUD or theme code.</p>
 */
@Mod(AsweetH.MOD_ID)
public final class AsweetH {
    /** Registry namespace and config file name for everything this mod owns. */
    public static final String MOD_ID = "asweeth";
    /** Human readable name, used for the window title and the watermark. */
    public static final String CLIENT_NAME = "ASweetH";
    /** Client version shown in the HUD watermark and the settings screen footer. */
    public static final String VERSION = "0.1.1";

    private static final Logger LOGGER = LogUtils.getLogger();

    public AsweetH(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBus).addListener(this::commonSetup);
        // Extension points can only be registered during construction. The distance check keeps the
        // client screen classes off a dedicated server's class loader.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AsweetHClient.registerConfigScreen(context);
        }
        LOGGER.info("{} {} loading for Minecraft 26.3", CLIENT_NAME, VERSION);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("ASweetH common setup complete");
    }
}
