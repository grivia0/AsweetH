package dev.asweeth.client.perf;

import dev.asweeth.config.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The runtime half of the Optimisation tab: a small set of client side tweaks that vanilla exposes
 * through {@link Options}, plus a render distance governor.
 *
 * <p>Everything here is re-applied lazily. {@link #tick(Minecraft)} builds a cheap signature from the
 * switches it cares about (and the current render distance, because the chunk streaming aid depends on
 * it) and only writes options when that signature changes, so the normal case is a few integer
 * comparisons per tick.</p>
 *
 * <p><b>Chunk streaming aid.</b> Vanilla re-bakes a freshly loaded chunk section for the fade-in
 * animation and blends biomes across up to seven blocks at build time. At 32 chunks both of those turn
 * into visible stutter, which is why the aid zeroes the fade and caps biome blending once the render
 * distance reaches {@value #HIGH_DISTANCE}. Combined with the {@code Max view} preset it keeps chunk
 * loading effective instead of making the client drop frames as the radius grows.</p>
 *
 * <p><b>Adaptive render distance.</b> Samples the framerate once per second and walks the render
 * distance down when the target is missed for two samples in a row, and back up (never above the
 * distance the player had when enabling it) after six comfortable samples. The asymmetry is
 * deliberate: dropping a chunk is cheap to notice, raising one costs a chunk build spike.</p>
 */
public final class Optimisations {
    /** Vanilla's own maximum for the render distance option. */
    public static final int MAX_RENDER_DISTANCE = 32;
    /** Below this the governor stops stepping down. */
    private static final int MIN_RENDER_DISTANCE = 4;
    /** From this render distance on, the streaming aid starts clamping build-time costs. */
    private static final int HIGH_DISTANCE = 16;
    /** Ticks between two framerate samples (one second). */
    private static final int SAMPLE_TICKS = 20;

    private static int lastSignature = Integer.MIN_VALUE;
    private static int sampleTicks;
    private static int lowStreak;
    private static int highStreak;
    /** Render distance the governor may climb back to, -1 while adaptive mode is off. */
    private static int ceiling = -1;

    private Optimisations() {
    }

    /** Forces the next {@link #tick(Minecraft)} to write the options again. */
    public static void invalidate() {
        lastSignature = Integer.MIN_VALUE;
    }

    /** Called once per client tick from the game bus. */
    public static void tick(Minecraft minecraft) {
        ClientSettings settings = ClientSettings.get();
        Options options = minecraft.options;
        if (options == null) {
            return;
        }

        applySwitches(minecraft, settings, options);

        if (!settings.adaptiveRenderDistance) {
            ceiling = -1;
            lowStreak = 0;
            highStreak = 0;
            return;
        }
        // Never govern while a menu is open or the window is minimised: both cap the framerate hard.
        if (minecraft.level == null || minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        if (++sampleTicks < SAMPLE_TICKS) {
            return;
        }
        sampleTicks = 0;
        govern(minecraft, settings, options);
    }

    private static void applySwitches(Minecraft minecraft, ClientSettings settings, Options options) {
        int distance = options.renderDistance().get();
        int signature = (settings.smartCulling ? 1 : 0)
                | (settings.fastMenus ? 2 : 0)
                | (settings.chunkStreamAid ? 4 : 0);
        signature = signature * 64 + distance;
        if (signature == lastSignature) {
            return;
        }
        lastSignature = signature;

        boolean changed = false;
        if (settings.smartCulling && !minecraft.smartCull) {
            // Vanilla only exposes this as a field; it skips entity/AABB work for invisible things.
            minecraft.smartCull = true;
            changed = true;
        }
        if (settings.fastMenus && options.menuBackgroundBlurriness().get() != 0) {
            options.menuBackgroundBlurriness().set(0);
            changed = true;
        }
        if (settings.chunkStreamAid && distance >= HIGH_DISTANCE) {
            // At high render distances the fade animation and biome blending both force extra chunk
            // rebuilds, which is exactly where the client cannot afford them.
            if (options.chunkSectionFadeInTime().get() != 0.0D) {
                options.chunkSectionFadeInTime().set(0.0D);
                changed = true;
            }
            if (options.biomeBlendRadius().get() > 1) {
                options.biomeBlendRadius().set(1);
                changed = true;
            }
        }
        if (changed) {
            options.save();
        }
    }


    private static void govern(Minecraft minecraft, ClientSettings settings, Options options) {
        int fps = minecraft.getFps();
        if (fps <= 0) {
            return;
        }
        int distance = options.renderDistance().get();
        if (ceiling < 0) {
            ceiling = Math.min(MAX_RENDER_DISTANCE, Math.max(distance, MIN_RENDER_DISTANCE * 2));
        }
        int target = settings.targetFps;

        if (fps < target * 0.85F && distance > MIN_RENDER_DISTANCE) {
            highStreak = 0;
            if (++lowStreak >= 2) {
                lowStreak = 0;
                step(options, distance - 1);
            }
        } else if (fps > target * 1.15F && distance < ceiling) {
            lowStreak = 0;
            if (++highStreak >= 6) {
                highStreak = 0;
                step(options, distance + 1);
            }
        } else {
            lowStreak = 0;
            highStreak = 0;
        }
    }

    private static void step(Options options, int distance) {
        int clamped = Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, distance));
        if (clamped == options.renderDistance().get()) {
            return;
        }
        options.renderDistance().set(clamped);
        options.save();
        // The streaming aid depends on the distance, so let it re-evaluate next tick.
        invalidate();
    }
}

