package dev.asweeth.util;


/**
 * Packed ARGB helpers. Minecraft passes colours around as {@code int} 0xAARRGGBB, so all colour maths
 * in ASweetH stays in that representation instead of allocating {@code java.awt.Color} objects.
 */
public final class Colors {
    private Colors() {
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp8(alpha) << 24) | (clamp8(red) << 16) | (clamp8(green) << 8) | clamp8(blue);
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** Returns the same colour with a new alpha (0-255). */
    public static int withAlpha(int color, int alpha) {
        return (clamp8(alpha) << 24) | (color & 0x00FFFFFF);
    }

    /** Scales the existing alpha by {@code factor} (0..1), e.g. for hover fades. */
    public static int multiplyAlpha(int color, float factor) {
        float clamped = clamp01(factor);
        // Fixed point keeps this allocation free and avoids Math.round per channel.
        int alpha = ((color >>> 24) & 0xFF) * (int) (clamped * 256.0F) >>> 8;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Linear interpolation between two packed ARGB colours, including their alpha channels.
     *
     * <p>Channel maths is done in 8.8 fixed point: one multiply and one shift per channel instead of
     * a float widening, a rounding call and a clamp. This is the single hottest colour helper in the
     * client - every gradient row of every panel goes through it - so the integer path matters.</p>
     */
    public static int lerp(int from, int to, float delta) {
        if (from == to) {
            return from;
        }
        int t = (int) (clamp01(delta) * 256.0F + 0.5F);
        int inv = 256 - t;
        int a = (((from >>> 24) & 0xFF) * inv + ((to >>> 24) & 0xFF) * t) >>> 8;
        int r = (((from >>> 16) & 0xFF) * inv + ((to >>> 16) & 0xFF) * t) >>> 8;
        int g = (((from >>> 8) & 0xFF) * inv + ((to >>> 8) & 0xFF) * t) >>> 8;
        int b = ((from & 0xFF) * inv + (to & 0xFF) * t) >>> 8;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * A relative brightness in 0..1 using the sRGB luma weights. Used to decide whether a theme needs
     * dark or light text on top of it.
     */
    public static float brightness(int color) {
        return (0.2126f * red(color) + 0.7152f * green(color) + 0.0722f * blue(color)) / 255f;
    }

    /** Mixes the colour towards white; {@code amount} 0..1. */
    public static int lighten(int color, float amount) {
        return lerp(color, 0xFFFFFFFF, amount);
    }

    /** Mixes the colour towards black; {@code amount} 0..1. */
    public static int darken(int color, float amount) {
        return lerp(color, 0xFF000000, amount);
    }

    private static int clamp8(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
