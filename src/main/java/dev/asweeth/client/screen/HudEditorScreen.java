package dev.asweeth.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.asweeth.client.hud.HudBounds;
import dev.asweeth.client.hud.HudAnchor;
import dev.asweeth.client.hud.HudElement;
import dev.asweeth.client.hud.HudLayout;
import dev.asweeth.client.widget.GlassButton;
import dev.asweeth.client.widget.GlassCycleButton;
import dev.asweeth.client.widget.GlassSlider;
import dev.asweeth.client.widget.GlassToggle;
import dev.asweeth.config.ClientSettings;
import dev.asweeth.theme.Theme;
import dev.asweeth.theme.ThemeMode;
import dev.asweeth.util.Colors;
import dev.asweeth.util.Render2D;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Live layout editor for the ASweetH HUD: drag any element where you want it, then size and fade it
 * from the inspector on the right.
 *
 * <p>This screen draws no background of its own - the whole point is to see the readouts over the
 * world they will live on - and it is not a pause screen, so animations, FPS and ping keep updating
 * while the player arranges things. The HUD layers keep drawing switched off elements dimmed while
 * {@link #isEditing()} is true, which is how an element that is currently hidden can still be found
 * and dragged back into place.</p>
 *
 * <p>Hit testing goes through {@link HudBounds}: the HUD publishes the rectangle it actually painted
 * last frame, so a drag grabs exactly what the player sees without the editor measuring seven
 * different elements a second time. Positions are written with {@link HudLayout#moveTo}, which clamps
 * to the screen before deriving the offset, so a drag can never hide an element past an edge. The
 * config file is written once, when the editor closes, instead of on every pixel of a drag.</p>
 */
public final class HudEditorScreen extends Screen {
    /** Gap between an element and the screen edge; matches both HUD layers so clamping agrees. */
    private static final int MARGIN = 4;
    /** Grid a Ctrl-drag snaps to, so elements can be lined up by hand. */
    private static final int SNAP = 4;
    /** Arrow key nudge: plain, with Shift, with Ctrl. */
    private static final int NUDGE = 1;
    private static final int NUDGE_SHIFT = 5;
    private static final int NUDGE_CTRL = 10;

    private static final int PANEL_WIDTH = 168;
    private static final int PANEL_PAD = 8;
    private static final int PANEL_RADIUS = 8;
    private static final int PANEL_MARGIN = 6;
    private static final int HEADER = 26;
    private static final int FOOTER = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int SLIDER_HEIGHT = 24;
    private static final int DONE_HEIGHT = 18;
    /** Width of the value pill on the element picker. */
    private static final int PILL_WIDTH = 92;

    /** True while this screen is the active one; the HUD layers read it to preview hidden elements. */
    private static boolean editing;

    /** Element the inspector acts on, and the one drawn with the accent frame. */
    private HudElement selected = HudElement.WATERMARK;
    /** Element under the cursor while it is being dragged, {@code null} otherwise. */
    private HudElement dragged;
    /** Offset from the cursor to the element's top left at the moment it was grabbed. */
    private int grabX;
    private int grabY;
    /** "Reset everything" needs a second click; this remembers that the first one happened. */
    private boolean confirmResetAll;
    private GlassButton resetAllButton;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    /** Screen to return to on close, {@code null} when the editor was opened straight from the game. */
    private final Screen parent;

    public HudEditorScreen() {
        this(null);
    }

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("asweeth.editor.title"));
        this.parent = parent;
    }

    /** Whether an editor is open right now. Static because HUD layers are not given the screen. */
    public static boolean isEditing() {
        return editing;
    }

    @Override
    protected void init() {
        ClientSettings settings = ClientSettings.get();
        panelWidth = Math.min(PANEL_WIDTH, Math.max(PILL_WIDTH + 48, this.width - PANEL_MARGIN * 2));
        panelX = Math.max(PANEL_MARGIN, this.width - panelWidth - PANEL_MARGIN);
        panelY = PANEL_MARGIN;
        int rows = ROW_HEIGHT + ROW_GAP + ROW_HEIGHT + ROW_GAP + SLIDER_HEIGHT + ROW_GAP + SLIDER_HEIGHT
                + ROW_GAP + ROW_HEIGHT + ROW_GAP + ROW_HEIGHT + ROW_GAP + DONE_HEIGHT;
        panelHeight = Math.min(HEADER + rows + FOOTER, this.height - PANEL_MARGIN * 2);

        int x = panelX + PANEL_PAD;
        int width = panelWidth - PANEL_PAD * 2;
        int y = panelY + HEADER;

        // Every getter below reads the selected field live, so switching the picker retargets the whole
        // inspector without rebuilding a single widget.
        addRenderableWidget(new GlassCycleButton<HudElement>(x, y, width, ROW_HEIGHT,
                Component.translatable("asweeth.editor.element"), HudElement.values(),
                () -> selected,
                value -> selected = value,
                HudElement::label, PILL_WIDTH));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new GlassToggle(x, y, width, ROW_HEIGHT,
                Component.translatable("asweeth.editor.shown"),
                () -> selected.enabled(settings), value -> {
                    selected.setEnabled(settings, value);
                    settings.save();
                }));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new GlassSlider(x, y, width, SLIDER_HEIGHT,
                Component.translatable("asweeth.editor.size"), HudLayout.MIN_SCALE, HudLayout.MAX_SCALE,
                () -> (double) settings.layout(selected).scale,
                value -> settings.layout(selected).scale = (float) value,
                value -> String.format(Locale.ROOT, "%.2fx", value)));
        y += SLIDER_HEIGHT + ROW_GAP;

        addRenderableWidget(new GlassSlider(x, y, width, SLIDER_HEIGHT,
                Component.translatable("asweeth.editor.opacity"), HudLayout.MIN_OPACITY, HudLayout.MAX_OPACITY,
                () -> (double) settings.layout(selected).opacity,
                value -> settings.layout(selected).opacity = (float) value,
                value -> Math.round(value * 100.0D) + "%"));
        y += SLIDER_HEIGHT + ROW_GAP;

        addRenderableWidget(new GlassButton(x, y, width, ROW_HEIGHT,
                Component.translatable("asweeth.editor.reset"), false, () -> {
                    ClientSettings.get().resetLayout(selected);
                    ClientSettings.get().save();
                }));
        y += ROW_HEIGHT + ROW_GAP;

        resetAllButton = new GlassButton(x, y, width, ROW_HEIGHT, resetAllLabel(), false, this::resetAll);
        addRenderableWidget(resetAllButton);
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new GlassButton(x, y, width, DONE_HEIGHT,
                Component.translatable("gui.done"), true, this::onClose));
    }

    private Component resetAllLabel() {
        return Component.translatable(confirmResetAll
                ? "asweeth.editor.reset_all_confirm" : "asweeth.editor.reset_all");
    }

    /**
     * Restores every element. Two clicks, because there is no undo: the first one only relabels the
     * button, and clicking anywhere else disarms it again.
     */
    private void resetAll() {
        if (!confirmResetAll) {
            setConfirmResetAll(true);
            return;
        }
        setConfirmResetAll(false);
        ClientSettings settings = ClientSettings.get();
        settings.resetLayouts();
        settings.save();
    }

    private void setConfirmResetAll(boolean value) {
        if (confirmResetAll == value) {
            return;
        }
        confirmResetAll = value;
        // The label lives on the widget itself, so the rows are rebuilt to show the armed state.
        rebuildWidgets();
    }

    /**
     * Draws nothing on purpose: the editor has to be transparent so the HUD can be judged against the
     * world behind it, which is also why this screen is not a pause screen.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeMode theme = Theme.current();

        // Faint frames tell the player what is draggable before they hover anything; only elements that
        // were drawn last frame have bounds, so a switched off element shows up in the same pass.
        int faint = Colors.multiplyAlpha(theme.accent(), 0.22F);
        for (HudElement element : HudElement.values()) {
            ScreenRectangle bounds = HudBounds.get(element);
            if (bounds != null && element != selected) {
                outline(graphics, bounds, faint);
            }
        }

        Hit hovered = hitTest(mouseX, mouseY);
        if (hovered != null && hovered.element() != selected) {
            outline(graphics, hovered.bounds(), Colors.multiplyAlpha(theme.accent(), 0.6F));
        }

        ScreenRectangle selectedBounds = HudBounds.get(selected);
        if (selectedBounds != null) {
            outline(graphics, selectedBounds, theme.accent());
            drawTag(graphics, selectedBounds, selected);
        }

        drawPanelChrome(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawHint(graphics);
    }

    private void drawPanelChrome(GuiGraphicsExtractor graphics) {
        ThemeMode theme = Theme.current();
        Render2D.glassPanel(graphics, panelX, panelY, panelWidth, panelHeight, PANEL_RADIUS, theme, 0.96F);
        Render2D.text(graphics, this.font, this.title, panelX + PANEL_PAD, panelY + 7, theme.text());
        HudLayout layout = ClientSettings.get().layout(selected);
        Render2D.textRight(graphics, this.font, layout.offsetX + "," + layout.offsetY,
                panelX + panelWidth - PANEL_PAD, panelY + 7, theme.textDim());
        Render2D.accentBar(graphics, panelX + PANEL_PAD, panelY + HEADER - 8,
                panelWidth - PANEL_PAD * 2, 1, theme);
    }

    /** One line reminder of the controls, centred at the bottom where it cannot cover the panel. */
    private void drawHint(GuiGraphicsExtractor graphics) {
        ThemeMode theme = Theme.current();
        Component hint = Component.translatable("asweeth.editor.hint");
        int chipWidth = Math.min(this.width - 8, this.font.width(hint) + 12);
        int x = (this.width - chipWidth) / 2;
        int y = this.height - 15;
        Render2D.glassPanel(graphics, x, y, chipWidth, 11, 4, theme, 0.9F);
        Render2D.textCentered(graphics, this.font,
                Component.literal(Render2D.fit(this.font, hint.getString(), chipWidth - 12)),
                this.width / 2, y + 1, theme.textDim());
    }

    /** Name of the selected element, pinned just outside its frame. */
    private void drawTag(GuiGraphicsExtractor graphics, ScreenRectangle bounds, HudElement element) {
        ThemeMode theme = Theme.current();
        Component label = element.label();
        int width = this.font.width(label) + 8;
        int x = Math.max(2, Math.min(this.width - width - 2, bounds.left()));
        // Above the frame unless there is no room, then below it.
        int y = bounds.top() - 11;
        if (y < 2) {
            y = Math.min(this.height - 11, bounds.bottom() + 1);
        }
        Render2D.roundedRect(graphics, x, y, width, 10, 3, theme.accent(), theme.accentDeep());
        int ink = Colors.brightness(theme.accent()) > 0.6F ? 0xFF14101A : 0xFFFFFFFF;
        Render2D.textCentered(graphics, this.font, label, x + width / 2, y + 1, ink);
    }

    /** One pixel frame around a rectangle. */
    private static void outline(GuiGraphicsExtractor graphics, ScreenRectangle bounds, int color) {
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean onWidget = super.mouseClicked(event, doubleClick);
        if (confirmResetAll && (resetAllButton == null || !resetAllButton.isMouseOver(event.x(), event.y()))) {
            // Any other click means the player changed their mind about wiping the layout.
            setConfirmResetAll(false);
        }
        if (onWidget || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return onWidget;
        }

        Hit hit = hitTest(event.x(), event.y());
        if (hit == null) {
            return false;
        }
        selected = hit.element();
        dragged = hit.element();
        grabX = (int) Math.floor(event.x()) - hit.bounds().left();
        grabY = (int) Math.floor(event.y()) - hit.bounds().top();
        // No widget owns this gesture, so mark the dragging state here; the release below clears it.
        setDragging(true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragged == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        ScreenRectangle bounds = HudBounds.get(dragged);
        if (bounds == null) {
            return true;
        }
        int left = (int) Math.floor(event.x()) - grabX;
        int top = (int) Math.floor(event.y()) - grabY;
        if (event.hasControlDown()) {
            // Snapping is the only way to line two elements up on a pixel grid by hand.
            left = Math.round(left / (float) SNAP) * SNAP;
            top = Math.round(top / (float) SNAP) * SNAP;
        }
        place(dragged, left, top, bounds);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragged == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(event);
        }
        dragged = null;
        setDragging(false);
        // Written once per drag instead of per pixel: a drag spans hundreds of frames.
        ClientSettings.get().save();
        return true;
    }

    /**
     * Arrow keys nudge the selected element, Shift moves five pixels at a time and Ctrl ten, Tab walks
     * the selection. Handling the arrows before {@code super} also keeps vanilla from moving the widget
     * focus around while the player is lining an element up.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        int step = event.hasControlDown() ? NUDGE_CTRL : event.hasShiftDown() ? NUDGE_SHIFT : NUDGE;
        if (event.isLeft()) {
            nudge(-step, 0);
            return true;
        }
        if (event.isRight()) {
            nudge(step, 0);
            return true;
        }
        if (event.isUp()) {
            nudge(0, -step);
            return true;
        }
        if (event.isDown()) {
            nudge(0, step);
            return true;
        }
        if (event.isCycleFocus()) {
            cycleSelection(event.hasShiftDown() ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    /** Moves the selection by a delta in screen pixels, clamped to the screen like a drag. */
    private void nudge(int dx, int dy) {
        ScreenRectangle bounds = HudBounds.get(selected);
        if (bounds != null) {
            place(selected, bounds.left() + dx, bounds.top() + dy, bounds);
        }
    }

    /**
     * Puts {@code element}'s top left corner on ({@code left}, {@code top}).
     *
     * <p>{@link HudLayout#moveTo} clamps first and derives the offset from the clamped position, so
     * dragging an element far past an edge does not bank invisible offset for later. The anchor is
     * re-picked from where the element lands before the offset is derived: dragging a chip into the
     * bottom right really pins it to the bottom right, so it stays there when the window changes size
     * instead of hanging off a large offset measured from the top left.</p>
     */
    private void place(HudElement element, int left, int top, ScreenRectangle bounds) {
        HudLayout layout = ClientSettings.get().layout(element);
        int width = bounds.width();
        int height = bounds.height();
        int x = HudLayout.clampX(left, this.width, width, MARGIN);
        int y = HudLayout.clampY(top, this.height, height, MARGIN);
        layout.anchor = HudAnchor.at(third(x + width / 2, this.width), third(y + height / 2, this.height));
        layout.moveTo(x, y, this.width, this.height, width, height, MARGIN);
    }

    /** Which third of the screen {@code position} falls in: 0 the first, 2 the last. */
    private static int third(int position, int extent) {
        if (extent <= 0) {
            return 0;
        }
        return position * 3 < extent ? 0 : position * 3 < extent * 2 ? 1 : 2;
    }

    private void cycleSelection(int step) {
        HudElement[] elements = HudElement.values();
        selected = elements[Math.floorMod(selected.ordinal() + step, elements.length)];
    }

    /**
     * Element under the cursor, or {@code null}.
     *
     * <p>The panel floats above the HUD, so a click that lands on it - including in the gaps between its
     * rows - belongs to the widgets and never grabs an element behind it. Within the HUD itself the last
     * element drawn wins, which is why the scan runs backwards.</p>
     */
    private Hit hitTest(double mouseX, double mouseY) {
        if (mouseX >= panelX && mouseX < panelX + panelWidth
                && mouseY >= panelY && mouseY < panelY + panelHeight) {
            return null;
        }
        int x = (int) Math.floor(mouseX);
        int y = (int) Math.floor(mouseY);
        HudElement[] elements = HudElement.values();
        for (int i = elements.length - 1; i >= 0; i--) {
            ScreenRectangle bounds = HudBounds.get(elements[i]);
            if (bounds != null && bounds.containsPoint(x, y)) {
                return new Hit(elements[i], bounds);
            }
        }
        return null;
    }

    @Override
    public void added() {
        editing = true;
        super.added();
    }

    @Override
    public void removed() {
        editing = false;
        super.removed();
    }

    @Override
    public void onClose() {
        // Every drag, nudge and slider already landed in memory; one write on the way out keeps the file
        // out of the per-frame path.
        ClientSettings.get().save();
        // Same convention as the settings screen: a null parent hands control back to the game.
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        // Single player must keep ticking: FPS, ping and the keystroke animation are what is framed here.
        return false;
    }

    /** A hit tested element together with the rectangle it occupied. */
    private record Hit(HudElement element, ScreenRectangle bounds) {
    }
}
