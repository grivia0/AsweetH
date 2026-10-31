package dev.asweeth.theme;

/**
 * The four looks ASweetH ships with. Every theme is a complete token set, so the whole client can be
 * re-skinned by swapping one enum constant: nothing in the render code hardcodes a colour.
 *
 * <p>All values are packed ARGB ints, which is what Minecraft's GUI renderer expects.</p>
 */
public enum ThemeMode {
    /** Default: smoked black glass with a rose accent. */
    ROSE_GLASS(
            "Rose Glass",
            0xE6100A12,
            0xCC1B1022,
            0xB30D0812,
            0xFFFF6FAE,
            0xFFC2185B,
            0xFFF7ECF3,
            0xFFB6A4B4,
            0x59FF9CC8
    ),
    /** Pure black glass, pink used only for the active control. */
    OBSIDIAN(
            "Obsidian",
            0xE6050507,
            0xCC0E0E12,
            0xB3060609,
            0xFFFF8FC0,
            0xFF7A1F45,
            0xFFEDEDF2,
            0xFF9C9CAA,
            0x40FF8FC0
    ),
    /** A lighter, milkier glass for daylight play; still translucent, still pink. */
    SAKURA_MILK(
            "Sakura Milk",
            0xD9F7E9F0,
            0xCCFDF3F8,
            0xB3F2DDE8,
            0xFFE0447F,
            0xFFA81F55,
            0xFF2A1620,
            0xFF6E5060,
            0x73E0447F
    ),
    /** Accessibility theme: no translucency at all, for screenshots and low-contrast displays. */
    OPAQUE(
            "Opaque",
            0xFF0B070E,
            0xFF15101A,
            0xFF120C16,
            0xFFFF6FAE,
            0xFFC2185B,
            0xFFFFFFFF,
            0xFFC9BFCA,
            0xFFFFC0DC
    );

    private final String displayName;
    private final int screenTint;
    private final int panelTop;
    private final int panelBottom;
    private final int accent;
    private final int accentDeep;
    private final int text;
    private final int textDim;
    private final int outline;

    ThemeMode(String displayName, int screenTint, int panelTop, int panelBottom, int accent, int accentDeep,
              int text, int textDim, int outline) {
        this.displayName = displayName;
        this.screenTint = screenTint;
        this.panelTop = panelTop;
        this.panelBottom = panelBottom;
        this.accent = accent;
        this.accentDeep = accentDeep;
        this.text = text;
        this.textDim = textDim;
        this.outline = outline;
    }

    public String displayName() {
        return displayName;
    }

    /** Backdrop painted over the world behind every ASweetH screen. */
    public int screenTint() {
        return screenTint;
    }

    /** Top stop of the vertical glass gradient. */
    public int panelTop() {
        return panelTop;
    }

    /** Bottom stop of the vertical glass gradient. */
    public int panelBottom() {
        return panelBottom;
    }

    /** Primary pink used for highlights, sliders and the watermark underline. */
    public int accent() {
        return accent;
    }

    /** Darker pink used for pressed states and gradient ends. */
    public int accentDeep() {
        return accentDeep;
    }

    public int text() {
        return text;
    }

    public int textDim() {
        return textDim;
    }

    /** Hairline border drawn around glass panels. */
    public int outline() {
        return outline;
    }

    /**
     * Whether glyphs on this palette should be drawn with the one pixel drop shadow.
     *
     * <p>The shadow is a darker copy of the glyph offset by a pixel. On the dark glass themes that reads
     * as depth; on a light panel such as Sakura Milk the dark text and its dark shadow overlap into a
     * smeared blob, so light palettes draw flat text instead. The decision is derived from the panel
     * luminance rather than hardcoded per theme, so a future light palette inherits it.</p>
     */
    public boolean textShadow() {
        return luminance(panelTop) < 0.55F;
    }

    /** Perceived brightness of an ARGB colour, 0 (black) to 1 (white). */
    private static float luminance(int argb) {
        float red = ((argb >> 16) & 0xFF) / 255.0F;
        float green = ((argb >> 8) & 0xFF) / 255.0F;
        float blue = (argb & 0xFF) / 255.0F;
        return 0.2126F * red + 0.7152F * green + 0.0722F * blue;
    }

    public ThemeMode next() {
        ThemeMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static ThemeMode byName(String name) {
        for (ThemeMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name) || mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return ROSE_GLASS;
    }
}
