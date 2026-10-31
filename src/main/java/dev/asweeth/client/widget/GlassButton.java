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
 * Flat glass button. {@code primary} buttons (Apply, Done) are filled with the accent gradient, the
 * rest stay translucent so they do not compete with the content.
 */
public class GlassButton extends GlassWidget {
    private final Runnable onPress;
    private final boolean primary;

    public GlassButton(int x, int y, int width, int height, Component message, boolean primary,
                       Runnable onPress) {
        super(x, y, width, height, message);
        this.primary = primary;
        this.onPress = onPress;
    }

    public GlassButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        this(x, y, width, height, message, false, onPress);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) {
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
        float glow = hoverProgress() * 0.55F + pressProgress() * 0.45F;
        int radius = 4;

        if (primary) {
            int top = Colors.lerp(theme.accent(), Colors.lighten(theme.accent(), 0.35F), glow);
            int bottom = Colors.lerp(theme.accentDeep(), theme.accent(), glow);
            Render2D.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), radius, top, bottom);
        } else {
            int base = Colors.lerp(theme.panelTop(), theme.panelBottom(), 0.4F);
            int top = Colors.lerp(base, theme.accent(), 0.22F * glow);
            int bottom = Colors.lerp(base, Colors.darken(theme.accent(), 0.25F), 0.22F * glow);
            Render2D.glassPanel(graphics, getX(), getY(), getWidth(), getHeight(), radius, theme, top, bottom);
            if (glow > 0.01F) {
                Render2D.roundedRect(graphics, getX(), getY() + getHeight() - 2, getWidth(), 1, 0,
                        Colors.multiplyAlpha(theme.accent(), 0.5F + 0.5F * glow),
                        Colors.multiplyAlpha(theme.accent(), 0.2F + 0.5F * glow));
            }
        }

        int textColor = primary
                ? (Colors.brightness(theme.accent()) > 0.6F ? 0xFF14101A : 0xFFFFFFFF)
                : (this.active ? theme.text() : theme.textDim());
        var font = Minecraft.getInstance().font;
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        Render2D.textCentered(graphics, font, getMessage(), getX() + getWidth() / 2, textY, textColor);
    }
}
