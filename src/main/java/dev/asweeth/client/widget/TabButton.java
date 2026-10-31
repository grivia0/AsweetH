package dev.asweeth.client.widget;

import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One entry of the vertical tab rail. The selected tab gets a pink edge marker instead of a full
 * highlight so the rail stays quiet next to the content area.
 */
public class TabButton extends GlassWidget {
    private final boolean selected;
    private final Runnable onPress;

    public TabButton(int x, int y, int width, int height, Component label, boolean selected, Runnable onPress) {
        super(x, y, width, height, label);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || this.selected) {
            return;
        }
        flashPress();
        playDownSound(Minecraft.getInstance().getSoundManager());
        if (onPress != null) {
            onPress.run();
        }
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();
        var font = Minecraft.getInstance().font;

        if (selected) {
            int base = Colors.lerp(theme.panelTop(), theme.accent(), 0.16F);
            Render2D.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 4,
                    Colors.withAlpha(base, 0xE6), Colors.withAlpha(Colors.darken(base, 0.25F), 0xD9));
            Render2D.accentBar(graphics, getX(), getY() + 3, 2, getHeight() - 6, theme);
        } else if (hoverProgress() > 0.02F) {
            int glow = Colors.multiplyAlpha(theme.accent(), Math.round(26 * hoverProgress()));
            Render2D.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 4, glow, glow);
        }

        int color = selected
                ? theme.accent()
                : Colors.lerp(theme.textDim(), theme.text(), hoverProgress());
        Render2D.text(graphics, font, getMessage(), getX() + 10, getY() + (getHeight() - font.lineHeight) / 2 + 1,
                color);
    }
}
