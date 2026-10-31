package dev.asweeth.client.widget;

import dev.asweeth.config.ClientSettings;
import dev.asweeth.util.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Shared behaviour for every ASweetH control: it owns the hover/press animation and forwards the
 * actual painting to {@link #draw}.
 *
 * <p>Subclasses only draw; they never deal with animation timing, which keeps each control a few
 * dozen lines long. Animation speed falls back to "instant" when the player disabled animations in
 * the Theme tab.</p>
 */
public abstract class GlassWidget extends AbstractWidget {
    /** 0 while idle, 1 while the cursor is on the control. Animated. */
    private float hover;
    /** Temporary bump used for the press flash, decays back to 0. */
    private float press;

    protected GlassWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean animated = ClientSettings.get().animations;
        float seconds = 1.0F / Math.max(1, minecraft.getFps());
        hover = Easing.damp(hover, isHovered ? 1.0F : 0.0F, animated ? 16.0F : 4096.0F, seconds);
        press = Easing.damp(press, 0.0F, animated ? 10.0F : 4096.0F, seconds);

        draw(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** Paints the control. {@link #hoverProgress()} and {@link #pressProgress()} drive the glow. */
    protected abstract void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

    protected float hoverProgress() {
        return hover;
    }

    protected float pressProgress() {
        return press;
    }

    /** Triggers the press flash; called by subclasses when they accept a click. */
    protected void flashPress() {
        press = 1.0F;
    }

    /** Length of the frame that is currently being rendered, in seconds. */
    protected static float frameSeconds() {
        return 1.0F / Math.max(1, Minecraft.getInstance().getFps());
    }

    /**
     * Animation speed, or a huge value that makes {@link Easing#damp} snap to the target when the
     * player turned animations off.
     */
    protected static float animSpeed(float normalSpeed) {
        return ClientSettings.get().animations ? normalSpeed : 4096.0F;
    }
}
