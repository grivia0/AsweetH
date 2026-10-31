package dev.asweeth.client.widget;

import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Render2D;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Label on the left, current value in a pill on the right. Left click advances, right click goes back
 * so a two option setting never needs a second trip to the menu.
 */
public class GlassCycleButton<T> extends GlassWidget {
    private final T[] values;
    private final java.util.function.Supplier<T> getter;
    private final Consumer<T> setter;
    private final java.util.function.Function<T, Component> labeler;
    private final int pillWidth;

    public GlassCycleButton(int x, int y, int width, int height, Component label, T[] values,
                            java.util.function.Supplier<T> getter, Consumer<T> setter,
                            java.util.function.Function<T, Component> labeler, int pillWidth) {
        super(x, y, width, height, label);
        this.values = values;
        this.getter = getter;
        this.setter = setter;
        this.labeler = labeler;
        this.pillWidth = pillWidth;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) {
            return;
        }
        flashPress();
        playDownSound(Minecraft.getInstance().getSoundManager());
        T current = getter.get();
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
                break;
            }
        }
        int step = event.button() == 1 ? -1 : 1;
        int next = Math.floorMod(index + step, values.length);
        setter.accept(values[next]);
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();
        var font = Minecraft.getInstance().font;
        int textColor = this.active
                ? Colors.lerp(theme.text(), theme.accent(), hoverProgress() * 0.6F)
                : theme.textDim();
        Render2D.text(graphics, font, getMessage(), getX() + 2, getY() + (getHeight() - font.lineHeight) / 2 + 1,
                textColor);

        int pillHeight = getHeight() - 4;
        int pillX = getRight() - pillWidth - 2;
        int pillY = getY() + 2;
        int base = Colors.lerp(theme.panelBottom(), theme.panelTop(), 0.5F);
        int top = Colors.lerp(base, theme.accent(), 0.18F + 0.25F * hoverProgress());
        int bottom = Colors.lerp(base, theme.accentDeep(), 0.12F + 0.25F * hoverProgress());
        Render2D.glassPanel(graphics, pillX, pillY, pillWidth, pillHeight, 4, theme, top, bottom);

        T value = getter.get();
        Component shown = value == null ? Component.empty() : labeler.apply(value);
        Render2D.textCentered(graphics, font, shown, pillX + pillWidth / 2, pillY + (pillHeight - font.lineHeight) / 2 + 1,
                theme.accent());
    }
}
