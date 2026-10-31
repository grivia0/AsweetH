package dev.asweeth.util;

import dev.asweeth.config.ClientSettings;

/**
 * Small easing library for the UI animations (tab indicator, hover glows, HUD fades).
 *
 * <p>Every method maps {@code t} in 0..1 to an eased value in 0..1 and clamps its input, so callers can
 * feed raw {@code partialTick} style fractions without defensive checks.</p>
 */
public final class Easing {
    private Easing() {
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    public static float linear(float t) {
        return clamp01(t);
    }

    public static float easeOutCubic(float t) {
        float x = 1f - clamp01(t);
        return 1f - x * x * x;
    }

    public static float easeInOutCubic(float t) {
        float x = clamp01(t);
        if (x < 0.5f) {
            return 4f * x * x * x;
        }
        float f = -2f * x + 2f;
        return 1f - f * f * f / 2f;
    }

    public static float easeOutQuint(float t) {
        float x = 1f - clamp01(t);
        return 1f - x * x * x * x * x;
    }

    public static float easeOutBack(float t) {
        float x = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float f = x - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    }

    /**
     * Frame-rate independent approach of {@code current} towards {@code target}.
     *
     * <p>Uses the lookup table in {@link FastMath} instead of {@code Math.exp} so the per-widget
     * hover animations stay off the libm path; the Optimisation tab can switch back to the exact
     * call when the player prefers it.</p>
     */
    public static float damp(float current, float target, float speed, float deltaSeconds) {
        if (current == target) {
            return target;
        }
        float decay = Math.max(0f, speed) * Math.max(0f, deltaSeconds);
        float factor;
        if (ClientSettings.get().fastMath) {
            factor = 1f - FastMath.expNeg(decay);
        } else {
            factor = 1f - (float) Math.exp(-decay);
        }
        return current + (target - current) * factor;
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }
}
