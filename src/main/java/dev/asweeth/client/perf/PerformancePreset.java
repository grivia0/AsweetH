package dev.asweeth.client.perf;

import dev.asweeth.config.ClientSettings;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

/**
 * The three performance profiles in the Performance tab.
 *
 * <p>Vanilla's own graphics preset only covers a handful of switches, so ASweetH writes the individual
 * options instead: render/simulation distance, entity distance scaling, particle density, clouds,
 * mipmaps, biome blend, chunk fade-in and view bobbing all move together. Writing them one by one also
 * keeps the changes honest - the vanilla video settings screen shows exactly what was touched.</p>
 */
public enum PerformancePreset {
    /** Everything the player had before, at vanilla "fancy" quality. */
    STANDARD("asweeth.preset.standard", "asweeth.preset.standard.desc"),
    /** The lite profile: visibly cheaper, still pretty. */
    LITE("asweeth.preset.lite", "asweeth.preset.lite.desc"),
    /** Last resort for integrated GPUs and 4 chunk render distances. */
    POTATO("asweeth.preset.potato", "asweeth.preset.potato.desc"),
    /** Vanilla's maximum radius, kept playable by the ASweetH streaming aid and cheaper effects. */
    MAX_VIEW("asweeth.preset.max", "asweeth.preset.max.desc");

    private final String labelKey;
    private final String descriptionKey;

    PerformancePreset(String labelKey, String descriptionKey) {
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
    }

    public Component label() {
        return Component.translatable(labelKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    public PerformancePreset next() {
        PerformancePreset[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Writes this profile into {@link Options} and flushes them to {@code options.txt}.
     *
     * <p>Every {@code OptionInstance#set} updates the live option immediately, so the change is visible
     * the next frame without a resource reload.</p>
     */
    public void apply(Minecraft minecraft) {
        Options options = minecraft.options;
        switch (this) {
            case STANDARD -> {
                options.renderDistance().set(12);
                options.simulationDistance().set(10);
                options.entityDistanceScaling().set(1.0D);
                options.particles().set(ParticleStatus.ALL);
                options.cloudStatus().set(CloudStatus.FANCY);
                options.mipmapLevels().set(4);
                options.biomeBlendRadius().set(2);
                options.entityShadows().set(true);
                options.ambientOcclusion().set(true);
                options.chunkSectionFadeInTime().set(0.5D);
                options.bobView().set(true);
                options.menuBackgroundBlurriness().set(5);
                options.vignette().set(true);
                options.improvedTransparency().set(true);
                options.cutoutLeaves().set(false);
            }
            case LITE -> {
                options.renderDistance().set(8);
                options.simulationDistance().set(6);
                options.entityDistanceScaling().set(0.75D);
                options.particles().set(ParticleStatus.DECREASED);
                options.cloudStatus().set(CloudStatus.FAST);
                options.mipmapLevels().set(2);
                options.biomeBlendRadius().set(1);
                options.entityShadows().set(false);
                options.ambientOcclusion().set(true);
                options.chunkSectionFadeInTime().set(0.0D);
                options.bobView().set(true);
                options.menuBackgroundBlurriness().set(3);
                options.vignette().set(true);
                options.improvedTransparency().set(false);
                options.cutoutLeaves().set(true);
            }
            case POTATO -> {
                options.renderDistance().set(5);
                options.simulationDistance().set(4);
                options.entityDistanceScaling().set(0.5D);
                options.particles().set(ParticleStatus.MINIMAL);
                options.cloudStatus().set(CloudStatus.OFF);
                options.mipmapLevels().set(0);
                options.biomeBlendRadius().set(0);
                options.entityShadows().set(false);
                options.ambientOcclusion().set(false);
                options.chunkSectionFadeInTime().set(0.0D);
                options.bobView().set(false);
                options.menuBackgroundBlurriness().set(0);
                options.vignette().set(false);
                options.improvedTransparency().set(false);
                options.cutoutLeaves().set(true);
            }
            case MAX_VIEW -> {
                // Maximum radius, with everything that makes chunk *building* expensive turned down so
                // the client actually streams those 32 chunks instead of stuttering on the way in.
                options.renderDistance().set(Optimisations.MAX_RENDER_DISTANCE);
                options.simulationDistance().set(8);
                options.entityDistanceScaling().set(0.7D);
                options.particles().set(ParticleStatus.DECREASED);
                options.cloudStatus().set(CloudStatus.OFF);
                options.mipmapLevels().set(2);
                options.biomeBlendRadius().set(0);
                options.entityShadows().set(false);
                options.ambientOcclusion().set(true);
                options.chunkSectionFadeInTime().set(0.0D);
                options.bobView().set(true);
                options.menuBackgroundBlurriness().set(0);
                options.vignette().set(false);
                options.improvedTransparency().set(false);
                options.cutoutLeaves().set(true);
            }
            default -> throw new IllegalStateException("Unhandled preset " + this);
        }
        options.save();
        // The streaming aid caches its last applied state; a preset may have changed the distance.
        Optimisations.invalidate();
        // Keep the HUD badge in sync with whatever the player just applied.
        ClientSettings settings = ClientSettings.get();
        settings.preset = this;
        // Standard restores the full quality menu blur; the cheaper presets ask for fast menus so the
        // Optimisation switch does not immediately stomp the value this preset just wrote.
        settings.fastMenus = this != STANDARD;
        settings.save();
    }
}
