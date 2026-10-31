package dev.asweeth.client.widget;

import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Easing;
import dev.asweeth.util.Render2D;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Label row with a thin glass track underneath. Clicking anywhere on the track jumps the value and
 * holding the button scrubs it, which is how players expect client sliders to behave.
 */
public class GlassSlider extends GlassWidget {
    private static final int TRACK_HEIGHT = 3;
    private static final int HANDLE = 8;

    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> format;
    /** Animated handle position, follows the value so tooltips/programmatic changes glide. */
    private float shown;

    public GlassSlider(int x, int y, int width, int height, Component label, double min, double max,
                       DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> format) {
        super(x, y, width, height, label);
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
        this.format = format;
    }

    private int trackX() {
        return getX() + 1;
    }

    private int trackWidth() {
        return Math.max(1, getWidth() - 2);
    }

    private int trackY() {
        return getBottom() - 6;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) {
            return;
        }
        apply(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        if (this.active) {
            apply(event.x());
        }
    }

    private void apply(double mouseX) {
        double fraction = (mouseX - trackX()) / (double) trackWidth();
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        setter.accept(min + (max - min) * fraction);
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();
        var font = Minecraft.getInstance().font;
        double value = getter.getAsDouble();
        float fraction = (float) ((value - min) / (max - min));
        shown = Easing.damp(shown, fraction, animSpeed(20.0F), frameSeconds());
        shown = Math.max(0.0F, Math.min(1.0F, shown));

        int textColor = this.active
                ? Colors.lerp(theme.text(), theme.accent(), hoverProgress() * 0.6F)
                : theme.textDim();
        Render2D.text(graphics, font, getMessage(), getX() + 2, getY() + 2, textColor);
        Render2D.textRight(graphics, font, format.apply(value), getRight() - 2, getY() + 2,
                Colors.lerp(theme.textDim(), theme.accent(), 0.35F + 0.65F * hoverProgress()));

        int x = trackX();
        int width = trackWidth();
        int y = trackY();
        Render2D.roundedRect(graphics, x, y, width, TRACK_HEIGHT, TRACK_HEIGHT / 2, theme.outline(),
                theme.outline());

        int filled = Math.round(width * shown);
        if (filled > 0) {
            Render2D.roundedRect(graphics, x, y, filled, TRACK_HEIGHT, TRACK_HEIGHT / 2,
                    Colors.lerp(theme.accentDeep(), theme.accent(), shown), theme.accent());
        }

        int handleX = x + filled - HANDLE / 2;
        handleX = Math.max(x, Math.min(x + width - HANDLE, handleX));
        int handleColor = Colors.lerp(0xFFFFFFFF, theme.accent(), hoverProgress() * 0.5F);
        Render2D.roundedRect(graphics, handleX, y + TRACK_HEIGHT / 2 - HANDLE / 2, HANDLE, HANDLE,
                HANDLE / 2, handleColor, Colors.multiplyAlpha(handleColor, 0.85F));
    }
}
