package dev.asweeth.util;

/**
 * Cheap approximations for the few transcendental calls that sit on ASweetH's hot UI path.
 *
 * <p>{@link #expNeg(float)} replaces {@code Math.exp} inside {@link Easing#damp}, which runs once per
 * animated widget and per keystroke cap every frame. A 256 entry lookup table with linear
 * interpolation is accurate to roughly 0.4 % over the whole useful range, which is far below what an
 * animation can show, and it costs a multiply, a cast and a lerp instead of a libm call.</p>
 *
 * <p>Everything here is stateless and safe to call from the render thread; the only static state is
 * the immutable table built in the static initialiser.</p>
 */
public final class FastMath {
    /** Table resolution. 256 entries over 16 units keeps the error under half a percent. */
    private static final int EXP_STEPS = 256;
    /** Inputs above this decay to zero anyway (e^-16 ~ 1.1e-7), so the table stops here. */
    private static final float EXP_RANGE = 16.0F;
    private static final float EXP_SCALE = EXP_STEPS / EXP_RANGE;
    /** {@code e^-x} sampled at {@code i / EXP_SCALE}; one extra slot so interpolation never overflows. */
    private static final float[] EXP_NEG = new float[EXP_STEPS + 2];

    static {
        for (int i = 0; i < EXP_NEG.length; i++) {
            EXP_NEG[i] = (float) Math.exp(-(i / EXP_SCALE));
        }
    }

    private FastMath() {
    }

    /**
     * {@code e^-x} for {@code x >= 0}.
     *
     * @param x decay amount; negative values are treated as zero
     * @return a value in 0..1, monotonically decreasing
     */
    public static float expNeg(float x) {
        if (x <= 0.0F) {
            return 1.0F;
        }
        if (x >= EXP_RANGE) {
            return 0.0F;
        }
        float scaled = x * EXP_SCALE;
        int index = (int) scaled;
        float fraction = scaled - index;
        float from = EXP_NEG[index];
        return from + (EXP_NEG[index + 1] - from) * fraction;
    }
}
