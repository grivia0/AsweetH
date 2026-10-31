package dev.asweeth.client;

import dev.asweeth.AsweetH;
import dev.asweeth.config.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hold-to-zoom lens.
 *
 * <p>The field of view is written in {@link ViewportEvent.ComputeFov}, which Forge fires once per frame
 * right before the projection matrix is built, so the lens moves at the framerate instead of at the
 * 20 Hz of the client tick. The factor glides towards its target with a time based exponential
 * approach: the step comes from {@link Minecraft#getFrameTimeNs()}, so a 300 fps client and a 30 fps
 * client spend the same wall clock time zooming in.</p>
 *
 * <p>The vanilla value is only ever overridden while the lens is actually moving or zoomed. Once the
 * factor is back at {@code 1.0} this class stops calling {@code setFOV} entirely, so the sprint and
 * effect FOV modifiers - and any other mod that writes the event - keep full control of the field.</p>
 *
 * <p>The factor itself is a setting rather than a hardcoded constant: the Keys tab exposes it as a
 * slider, because how far a player wants to zoom is a matter of taste and screen size.</p>
 */
@Mod.EventBusSubscriber(modid = AsweetH.MOD_ID, value = Dist.CLIENT)
public final class Zoom {
    /** Smallest and largest factor the slider and the config accept. */
    public static final float MIN_FACTOR = 1.5F;
    public static final float MAX_FACTOR = 10.0F;
    /** Factor used when the config file predates the setting. */
    public static final float DEFAULT_FACTOR = 4.0F;

    /** Seconds for the lens to travel most of the way in, and back out. */
    private static final float TAU_IN = 0.055F;
    private static final float TAU_OUT = 0.080F;
    /** A stall must not teleport the lens, so the frame time is capped. */
    private static final float MAX_FRAME_SECONDS = 0.1F;
    /** Below this the lens counts as fully open, and vanilla keeps the FOV untouched. */
    private static final float EPSILON = 0.01F;

    /** Current lens factor, 1.0 when open. Only touched on the render and tick threads. */
    private static float factor = 1.0F;
    /** True while the key is held, the player is in a world and no screen is open. */
    private static boolean held;

    private Zoom() {
    }

    /** Samples the key once per client tick, so the per-frame path never reads input. */
    static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            // Leaving the world resets the lens; the next world must not start zoomed in.
            factor = 1.0F;
            held = false;
            return;
        }
        held = minecraft.gui.screen() == null && KeyBinds.ZOOM.isDown();
    }

    /**
     * Divides the FOV the game asked for by the current lens factor.
     *
     * <p>Dividing instead of setting an absolute FOV keeps sprinting, the underwater effect and the
     * death screen working while zoomed: they still get to pick the base value.</p>
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        float target = held ? configuredFactor() : 1.0F;
        float seconds = Math.min(MAX_FRAME_SECONDS,
                Math.max(1.0E-4F, minecraft.getFrameTimeNs() / 1.0E9F));
        factor = approach(factor, target, seconds, target > factor ? TAU_IN : TAU_OUT);
        if (factor <= 1.0F + EPSILON) {
            factor = 1.0F;
            return;
        }
        event.setFOV(event.getFOV() / factor);
    }

    /** The configured factor, clamped to the slider range and defended against a hand edited NaN. */
    public static float configuredFactor() {
        float configured = ClientSettings.get().zoomFactor;
        if (Float.isNaN(configured)) {
            return DEFAULT_FACTOR;
        }
        return Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, configured));
    }

    /** Exponential approach towards {@code goal}: frame rate independent and never overshoots. */
    private static float approach(float current, float goal, float seconds, float tau) {
        float step = 1.0F - (float) Math.exp(-seconds / tau);
        return current + (goal - current) * step;
    }
}
