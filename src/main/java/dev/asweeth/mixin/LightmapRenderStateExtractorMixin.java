package dev.asweeth.mixin;

import dev.asweeth.config.ClientSettings;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright, applied where 26.3 actually computes world lighting.
 *
 * <p>The gamma slider cannot do this any more. {@code Options.gamma()} is validated by
 * {@code OptionInstance.UnitDouble}, so it refuses anything outside 0 - 1, and the value only feeds
 * {@code state.brightness}, which the shader applies as a curve on top of light that is already there -
 * a pitch black cave stays black.</p>
 *
 * <p>What the shader really mixes (see {@code lightmap.fsh}) is</p>
 *
 * <pre>{@code
 * vec3 color = max(AmbientColor, NightVisionColor * NightVisionFactor); // the floor
 * color += SkyLightColor * skyBrightness + BlockLightColor * blockBrightness;
 * color = mix(color, notGamma(color), BrightnessFactor);                // the curve
 * }</pre>
 *
 * <p>so two uniforms are enough to reproduce a fullbright:</p>
 * <ul>
 *   <li>{@code nightVisionEffectIntensity = 1} raises the floor of the map to what a night vision potion
 *       gives, lighting unlit caves;</li>
 *   <li>{@code brightness = 1} pushes the result through {@code notGamma} at full strength, which lifts
 *       that mid grey to almost white - night vision on its own stays noticeably dim.</li>
 * </ul>
 *
 * <p>Nothing else is touched: fog, the darkness effect, boss darkening and the dimension's own night
 * vision tint keep working, so the toggle brightens the world without repainting it. The injection sits
 * at {@code TAIL} rather than at every return so the early exit taken when the lightmap does not need an
 * update - i.e. when the renderer will not upload this state - is left alone.</p>
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    /** Fully blended night vision: the value vanilla reaches while the potion is running. */
    private static final float FULL_BLEND = 1.0F;
    /** Vanilla's "Bright" gamma, i.e. the far end of the {@code notGamma} curve. */
    private static final float FULL_BRIGHTNESS = 1.0F;

    @Inject(method = "extract", at = @At("TAIL"))
    private void asweeth$applyFullbright(LightmapRenderState state, float partialTick, CallbackInfo callback) {
        if (!ClientSettings.get().fullbright) {
            return;
        }
        state.nightVisionEffectIntensity = FULL_BLEND;
        state.brightness = FULL_BRIGHTNESS;
    }
}
