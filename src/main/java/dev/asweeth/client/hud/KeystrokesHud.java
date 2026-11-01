package dev.asweeth.client.hud;

import dev.asweeth.AsweetH;
import dev.asweeth.client.screen.HudEditorScreen;
import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Easing;
import dev.asweeth.util.Render2D;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.gui.overlay.ForgeLayer;

/**
 * WASD / space / mouse readout in the corner picked in the Keys tab.
 *
 * <p>The caps are driven by the vanilla {@code KeyMapping}s of the player, so a rebound walk key moves
 * the highlight with it and mouse buttons work without touching GLFW. The layout is deliberately
 * allocation free: seven floats of animation state, a handful of colours resolved once per frame, and
 * {@link Render2D} quads.</p>
 *
 * <p>Position, scale, opacity and the colour scheme all come from {@link ClientSettings}: the block
 * takes its box from the {@code KEYSTROKES} {@link HudLayout}, is laid out in unscaled GUI pixels and
 * pushed through the pose matrix once, which is why changing the size never blurs the labels. The
 * anchor snaps the block to any of the nine grid spots, so the caps can share a corner with the main
 * HUD or move out of the way of a minimap, and the offset underneath it is what the HUD editor drags.
 * The on screen rectangle is published to {@link HudBounds} so the editor can hit test it.</p>
 */
public final class KeystrokesHud implements ForgeLayer {
    /** Layer id, registered above {@link AsweetHHud}. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AsweetH.MOD_ID, "keystrokes");

    private static final int KEY_WIDTH = 17;
    private static final int KEY_HEIGHT = 15;
    private static final int GAP = 2;
    private static final int ROW_STEP = KEY_HEIGHT + GAP;
    private static final int BLOCK_WIDTH = KEY_WIDTH * 3 + GAP * 2;
    private static final int HALF_WIDTH = (BLOCK_WIDTH - GAP) / 2;
    private static final int RADIUS = 3;
    private static final int MARGIN = 4;

    private static final int W = 0;
    private static final int A = 1;
    private static final int S = 2;
    private static final int D = 3;
    private static final int SPACE = 4;
    private static final int LMB = 5;
    private static final int RMB = 6;
    private static final String[] LABELS = {"W", "A", "S", "D", "SPACE", "LMB", "RMB"};

    /** Smoothed press amount per cap: 0 released, 1 held. */
    private final float[] press = new float[LABELS.length];

    // Palette resolved once per frame so the per-cap loop stays branch and lookup free.
    private int idleTop;
    private int idleBottom;
    private int idleText;
    private int idleBorder;
    private int heldTop;
    private int heldBottom;
    private int heldText;
    private int heldBorder;
    private float opacity;

    @Override
    public void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ClientSettings settings = ClientSettings.get();
        boolean editing = HudEditorScreen.isEditing();
        // The editor keeps the caps on screen - dimmed when the block is switched off - so there is
        // always something to drag; outside it the block stays out of menus and screens as before.
        if (!editing && (!settings.keystrokesEnabled || minecraft.gui.hud.isHidden()
                || minecraft.gui.screen() != null)) {
            return;
        }

        Options options = minecraft.options;
        float seconds = 1.0F / Math.max(1, minecraft.getFps());
        float speed = settings.animations ? 22.0F : 4096.0F;
        press[W] = Easing.damp(press[W], options.keyUp.isDown() ? 1.0F : 0.0F, speed, seconds);
        press[A] = Easing.damp(press[A], options.keyLeft.isDown() ? 1.0F : 0.0F, speed, seconds);
        press[S] = Easing.damp(press[S], options.keyDown.isDown() ? 1.0F : 0.0F, speed, seconds);
        press[D] = Easing.damp(press[D], options.keyRight.isDown() ? 1.0F : 0.0F, speed, seconds);
        press[SPACE] = Easing.damp(press[SPACE], options.keyJump.isDown() ? 1.0F : 0.0F, speed, seconds);
        if (settings.keystrokesMouse) {
            press[LMB] = Easing.damp(press[LMB], options.keyAttack.isDown() ? 1.0F : 0.0F, speed, seconds);
            press[RMB] = Easing.damp(press[RMB], options.keyUse.isDown() ? 1.0F : 0.0F, speed, seconds);
        }

        preparePalette(Theme.current(), settings.keystrokeTint,
                settings.layout(HudElement.KEYSTROKES).alpha(settings.keystrokesEnabled));

        int rows = settings.keystrokesMouse ? 4 : 3;
        int blockHeight = rows * ROW_STEP - GAP;
        HudLayout layout = settings.layout(HudElement.KEYSTROKES);
        int scaledWidth = layout.scaledWidth(BLOCK_WIDTH);
        int scaledHeight = layout.scaledHeight(blockHeight);
        int originX = layout.x(minecraft.getWindow().getGuiScaledWidth(), scaledWidth, MARGIN);
        int originY = layout.y(minecraft.getWindow().getGuiScaledHeight(), scaledHeight, MARGIN);

        Font font = minecraft.font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(layout.effectiveScale(), layout.effectiveScale());

        drawKey(graphics, font, KEY_WIDTH + GAP, 0, KEY_WIDTH, LABELS[W], press[W]);
        int row = ROW_STEP;
        drawKey(graphics, font, 0, row, KEY_WIDTH, LABELS[A], press[A]);
        drawKey(graphics, font, KEY_WIDTH + GAP, row, KEY_WIDTH, LABELS[S], press[S]);
        drawKey(graphics, font, (KEY_WIDTH + GAP) * 2, row, KEY_WIDTH, LABELS[D], press[D]);
        row += ROW_STEP;
        drawKey(graphics, font, 0, row, BLOCK_WIDTH, LABELS[SPACE], press[SPACE]);
        if (settings.keystrokesMouse) {
            row += ROW_STEP;
            drawKey(graphics, font, 0, row, HALF_WIDTH, LABELS[LMB], press[LMB]);
            drawKey(graphics, font, BLOCK_WIDTH - HALF_WIDTH, row, HALF_WIDTH, LABELS[RMB], press[RMB]);
        }

        graphics.pose().popMatrix();

        HudBounds.put(HudElement.KEYSTROKES, originX, originY, scaledWidth, scaledHeight);
    }


    /** Paints one cap, blending between the idle and the held palette by {@code amount}. */
    private void drawKey(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, String label,
                         float amount) {
        int border = Colors.multiplyAlpha(Colors.lerp(idleBorder, heldBorder, amount), opacity);
        int top = Colors.multiplyAlpha(Colors.lerp(idleTop, heldTop, amount), opacity);
        int bottom = Colors.multiplyAlpha(Colors.lerp(idleBottom, heldBottom, amount), opacity);
        Render2D.roundedRect(graphics, x, y, width, KEY_HEIGHT, RADIUS, border, border);
        Render2D.roundedRect(graphics, x + 1, y + 1, width - 2, KEY_HEIGHT - 2, RADIUS - 1, top, bottom);
        int color = Colors.lerp(idleText, heldText, amount);
        // Render2D centres without assuming a shadow, which matters on the light Sakura Milk caps.
        Render2D.textCentered(graphics, font, Component.literal(label), x + width / 2,
                y + (KEY_HEIGHT - font.lineHeight) / 2 + 1, color);
    }

    /**
     * Resolves the caps' colours for this frame. {@code MONO} ignores the palette completely, which
     * is why it is the one scheme that does not read any {@link ThemeMode} token.
     */
    private void preparePalette(ThemeMode theme, KeystrokeTint tint, float opacitySetting) {
        // Floor of 0.05 rather than the layout's 0.2: the editor previews a switched off block dimmed,
        // and clamping that up to the minimum would make the preview look identical to being enabled.
        opacity = Math.max(0.05F, Math.min(1.0F, opacitySetting));
        switch (tint) {
            case ACCENT -> {
                idleTop = Colors.lerp(theme.panelTop(), theme.accent(), 0.22F);
                idleBottom = Colors.lerp(theme.panelBottom(), theme.accentDeep(), 0.18F);
                idleText = theme.accent();
                idleBorder = Colors.multiplyAlpha(theme.accent(), 0.55F);
            }
            case MONO -> {
                idleTop = 0xFF17171C;
                idleBottom = 0xFF0B0B0F;
                idleText = 0xFFE8E8EE;
                idleBorder = 0xFF34343C;
            }
            default -> {
                idleTop = theme.panelTop();
                idleBottom = theme.panelBottom();
                idleText = theme.textDim();
                idleBorder = theme.outline();
            }
        }
        boolean mono = tint == KeystrokeTint.MONO;
        heldTop = mono ? 0xFFF4F4F8 : theme.accent();
        heldBottom = mono ? 0xFF9C9CA8 : theme.accentDeep();
        heldBorder = mono ? 0xFFFFFFFF : theme.accent();
        heldText = mono || Colors.brightness(heldTop) > 0.6F ? 0xFF14101A : 0xFFFFFFFF;
    }
}
