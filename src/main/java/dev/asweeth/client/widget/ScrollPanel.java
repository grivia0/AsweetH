package dev.asweeth.client.widget;

import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Easing;
import dev.asweeth.util.Render2D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The scrollable content area of the settings screen.
 *
 * <p>Rows are added with their position <em>inside</em> the content, not on the screen: the panel owns
 * the final Y of every widget and moves it whenever the scroll offset changes, so hit testing, hover
 * glow and drag handling all keep working without a translated pose matrix. Content is clipped with
 * {@link GuiGraphicsExtractor#enableScissor} around the children, which is what keeps a half scrolled
 * row from bleeding over the sheet border.</p>
 *
 * <p>Scrolling itself comes from {@link AbstractScrollArea}: it already clamps the offset to the
 * content height, so a tab that grows or shrinks after a rebuild can never leave the view past its
 * end. The offset is then damped towards the target before it is applied, which turns a wheel notch
 * into the same glide the rest of the mod uses.</p>
 *
 * <p>The scrollbar is drawn here rather than by vanilla: the panel asks for
 * {@link AbstractScrollArea.ScrollbarSettings#NO_SCROLL} because the stock sprite is not part of the
 * ASweetH palette, and paints a two pixel accent thumb instead.</p>
 */
public class ScrollPanel extends AbstractContainerWidget {
    /** Width reserved on the right for the scrollbar, so rows never shift when it appears. */
    public static final int GUTTER = 7;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_INSET = 2;
    private static final int MIN_THUMB = 14;
    /** Pixels of content per wheel notch. */
    private static final double SCROLL_RATE = 14.0D;
    /** Below this the animated offset snaps, so the thumb does not crawl for a pixel. */
    private static final float SNAP = 0.05F;

    /** Everything that draws, ordered the way it was added. */
    private final List<Row> rows = new ArrayList<>();
    /** The subset of {@link #rows} the panel forwards input to. */
    private final List<AbstractWidget> children = new ArrayList<>();
    /** Bottom edge of the content, in content coordinates. */
    private int contentBottom;
    /** Animated scroll offset; {@link #scrollAmount()} is the target. */
    private float shown;

    private record Row(Renderable renderable, int contentY) {
    }

    public ScrollPanel(int x, int y, int width, int height, Component title) {
        super(x, y, width, height, title, AbstractScrollArea.ScrollbarSettings.NO_SCROLL);
    }

    /** Adds an interactive widget at {@code contentY}, taking the row width from the panel. */
    public void add(AbstractWidget widget, int contentY) {
        widget.setX(getX());
        widget.setWidth(getWidth() - GUTTER);
        widget.setY(getY() + contentY - Math.round(shown));
        rows.add(new Row(widget, contentY));
        children.add(widget);
    }

    /**
     * Adds free form content (a preview, a divider, a paragraph) at {@code contentY}.
     *
     * <p>Blocks that happen to be widgets are positioned exactly like rows, because a {@code HintBlock}
     * starts life at (0, 0): without this it would draw at the left edge of the screen and the clip
     * would cut its beginning off rather than the panel's border.</p>
     */
    public void addBlock(Renderable block, int contentY) {
        if (block instanceof AbstractWidget widget) {
            widget.setX(getX());
            widget.setWidth(getWidth() - GUTTER);
            widget.setY(getY() + contentY - Math.round(shown));
        }
        rows.add(new Row(block, contentY));
    }

    /** Declares how tall the content is; anything past the panel height becomes scrollable. */
    public void setContentHeight(int height) {
        this.contentBottom = height;
        // A rebuild can shorten a tab (a preset with fewer rows), so re-clamp the current offset.
        refreshScrollAmount();
    }

    /**
     * Restores an offset without animating it.
     *
     * <p>Used when a tab is rebuilt: the rows are re-created at the top, so letting the damped offset
     * travel from zero would show a one frame jump followed by a glide back to where the player was.</p>
     */
    public void jumpTo(double amount) {
        setScrollAmount(amount);
        shown = (float) scrollAmount();
        applyScroll();
    }

    /** Width a row may use, i.e. the panel minus the scrollbar gutter. */
    public int rowWidth() {
        return getWidth() - GUTTER;
    }

    /** Vertical offset the content is currently drawn at. */
    public int visibleOffset() {
        return Math.round(shown);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    protected int contentHeight() {
        return Math.max(contentBottom, getHeight());
    }

    @Override
    protected double scrollRate() {
        return SCROLL_RATE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The container forwards clicks to children without checking its own bounds, so a row that is
        // scrolled out of sight could otherwise steal a click from the Done button below the panel.
        if (!this.visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Vanilla's implementation scrolls whenever the widget is visible; only the panel under the
        // cursor should react, or wheeling over the tab rail would move an invisible list.
        if (!this.visible || !isMouseOver(mouseX, mouseY) || !scrollable()) {
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                            float partialTick) {
        boolean animated = ClientSettings.get().animations;
        float seconds = 1.0F / Math.max(1, Minecraft.getInstance().getFps());
        shown = Easing.damp(shown, (float) scrollAmount(), animated ? 24.0F : 4096.0F, seconds);
        if (Math.abs(shown - (float) scrollAmount()) < SNAP) {
            shown = (float) scrollAmount();
        }
        applyScroll();

        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        for (Row row : rows) {
            row.renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();

        drawScrollbar(graphics, Theme.current());
    }

    /** Moves every widget to the row it belongs to, offset by the animated scroll. */
    private void applyScroll() {
        int offset = Math.round(shown);
        for (Row row : rows) {
            if (row.renderable instanceof AbstractWidget widget) {
                int y = getY() + row.contentY - offset;
                if (widget.getY() != y) {
                    widget.setY(y);
                }
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, ThemeMode theme) {
        int max = maxScrollAmount();
        if (max <= 0) {
            return;
        }
        int x = getRight() - BAR_WIDTH - BAR_INSET;
        int trackTop = getY() + BAR_INSET;
        int trackHeight = Math.max(1, getHeight() - BAR_INSET * 2);
        int thumbHeight = Math.max(MIN_THUMB,
                Math.round(trackHeight * (getHeight() / (float) contentHeight())));
        int travel = Math.max(0, trackHeight - thumbHeight);
        int thumbTop = trackTop + Math.round(travel * Math.min(1.0F, shown / max));

        int trackColor = Colors.multiplyAlpha(theme.outline(), 0.55F);
        Render2D.roundedRect(graphics, x, trackTop, BAR_WIDTH, trackHeight, 1, trackColor, trackColor);
        Render2D.roundedRect(graphics, x, thumbTop, BAR_WIDTH, thumbHeight, 1,
                theme.accent(), theme.accentDeep());
    }
}
