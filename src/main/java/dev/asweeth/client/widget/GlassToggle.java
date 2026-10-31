package dev.asweeth.client.widget;

import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Easing;
import dev.asweeth.util.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/**
 * Label on the left, sliding glass switch on the right. The whole row is clickable, which is far
 * friendlier than a 26 pixel hitbox.
 */
public class GlassToggle extends GlassWidget {
    private static final int TRACK_WIDTH = 26;
    private static final int TRACK_HEIGHT = 12;

    private final java.util.function.BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    /** Animated knob position: 0 = off, 1 = on. */
    private float knob;

    public GlassToggle(int x, int y, int width, int height, Component label,
                       java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active) {
            return;
        }
        flashPress();
        playDownSound(Minecraft.getInstance().getSoundManager());
        setter.accept(!getter.getAsBoolean());
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();
        var font = Minecraft.getInstance().font;
        boolean on = getter.getAsBoolean();
        knob = Easing.damp(knob, on ? 1.0F : 0.0F, animSpeed(18.0F), frameSeconds());

        int textColor = this.active
                ? Colors.lerp(theme.text(), theme.accent(), hoverProgress() * 0.6F)
                : theme.textDim();
        Render2D.text(graphics, font, getMessage(), getX() + 2, getY() + (getHeight() - font.lineHeight) / 2 + 1,
                textColor);

        int trackX = getRight() - TRACK_WIDTH - 2;
        int trackY = getY() + (getHeight() - TRACK_HEIGHT) / 2;
        int offTrack = Colors.lerp(theme.panelBottom(), theme.outline(), 0.55F);
        int trackLeft = Colors.lerp(offTrack, theme.accent(), knob);
        int trackRight = Colors.lerp(offTrack, theme.accentDeep(), knob);
        Render2D.roundedRect(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2, trackLeft,
                trackRight);
        if (knob < 0.5F) {
            Render2D.roundedRect(graphics, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2,
                    theme.outline(), theme.outline());
            Render2D.roundedRect(graphics, trackX + 1, trackY + 1, TRACK_WIDTH - 2, TRACK_HEIGHT - 2,
                    (TRACK_HEIGHT - 2) / 2, offTrack, offTrack);
        }

        int knobSize = TRACK_HEIGHT - 4;
        int travel = TRACK_WIDTH - knobSize - 4;
        int knobX = trackX + 2 + Math.round(travel * knob);
        int knobColor = Colors.lerp(theme.textDim(), 0xFFFFFFFF, knob);
        Render2D.roundedRect(graphics, knobX, trackY + 2, knobSize, knobSize, 3, knobColor, knobColor);

        if (hoverProgress() > 0.02F) {
            Render2D.roundedRect(graphics, getX(), getBottom() - 1, getWidth(), 1, 0,
                    Colors.multiplyAlpha(theme.accent(), 0.35F * hoverProgress()),
                    Colors.multiplyAlpha(theme.accent(), 0.35F * hoverProgress()));
        }
    }
}
