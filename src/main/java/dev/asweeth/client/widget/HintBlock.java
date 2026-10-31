package dev.asweeth.client.widget;

import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Render2D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A block of dimmed help text that belongs to the scrollable content of a tab.
 *
 * <p>It is a real widget so the scroll panel can position it like a control, but it never reacts to
 * the mouse: the height is fixed at construction from the number of lines, and each line is clipped to
 * the row width with {@link Render2D#fit} so a long translation cannot spill out of the sheet.</p>
 */
public final class HintBlock extends GlassWidget {
    /** Gap between two hint lines; matches the two pixel lead used elsewhere in the screen. */
    private static final int LINE_GAP = 2;

    private final List<Component> lines = new ArrayList<>();
    private final boolean accentFirst;

    /** Height a block with {@code lineCount} lines occupies, in GUI pixels. */
    public static int height(int lineCount) {
        return Math.max(1, lineCount) * (Render2D.LINE_STEP + LINE_GAP);
    }

    public HintBlock(int x, int y, int width, boolean accentFirst, Component... lines) {
        super(x, y, width, height(lines.length),
                lines.length == 0 ? Component.empty() : lines[0]);
        this.lines.addAll(List.of(lines));
        this.accentFirst = accentFirst;
    }

    @Override
    protected void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        ThemeMode theme = Theme.current();
        int lineY = getY();
        for (int i = 0; i < lines.size(); i++) {
            boolean accent = accentFirst && i == 0;
            Render2D.text(graphics, font, Render2D.fit(font, lines.get(i).getString(), getWidth()),
                    getX(), lineY, accent ? theme.accent() : theme.textDim());
            lineY += Render2D.LINE_STEP + LINE_GAP;
        }
    }
}
