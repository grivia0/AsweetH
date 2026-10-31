"""Regenerates docs/banner.png and docs/themes.png for the README.

The colours are taken verbatim from dev.asweeth.theme.ThemeMode, so the art cannot drift from the mod.
Run it from anywhere:  python3 docs/make_assets.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

BOLD = "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"
REG = "/usr/share/fonts/liberation/LiberationSans-Regular.ttf"

ACCENT = (0xFF, 0x6F, 0xAE)
ACCENT_DEEP = (0xC2, 0x18, 0x5B)
TEXT = (0xF7, 0xEC, 0xF3)
BG_TOP = (11, 7, 16)
BG_BOTTOM = (27, 16, 34)

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)


def font(path, size):
    return ImageFont.truetype(path, size)


def vgradient(w, h, top, bottom):
    img = Image.new("RGB", (1, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return img.resize((w, h))


def glow(img, cx, cy, radius, colour, peak=70):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 90
    for i in range(steps, 0, -1):
        r = max(1, int(radius * i / steps))
        a = int(peak * (1 - i / steps) ** 2.0)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour + (a,))
    img.alpha_composite(layer)


def tracked(draw, xy, text, f, fill, spacing=0):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill)
        x += draw.textlength(ch, font=f) + spacing
    return x


def tracked_width(draw, text, f, spacing=0):
    return sum(draw.textlength(c, font=f) for c in text) + spacing * max(0, len(text) - 1)



def banner():
    w, h = 1600, 500
    img = vgradient(w, h, BG_TOP, BG_BOTTOM).convert("RGBA")
    glow(img, 1330, 470, 560, ACCENT, 80)
    glow(img, 120, 40, 420, ACCENT_DEEP, 45)

    # Everything translucent is drawn on its own layer and composited once: ImageDraw writes raw
    # pixels, so a half transparent fill painted straight onto the canvas punches a hole instead of
    # blending with it.
    glass = Image.new("RGBA", img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glass)

    card = [1150, 82, 1520, 418]
    g.rounded_rectangle(card, 30, fill=(255, 255, 255, 13), outline=(255, 111, 174, 70), width=2)
    for i in range(3):
        y = card[1] + 52 + i * 40
        g.rounded_rectangle([card[0] + 34, y, card[0] + 190 + (i % 2) * 40, y + 10], 5,
                            fill=(255, 236, 246, 40))
    ty = card[3] - 92
    g.rounded_rectangle([card[0] + 34, ty, card[0] + 132, ty + 46], 23, fill=ACCENT + (235,))
    g.ellipse([card[0] + 94, ty + 6, card[0] + 126, ty + 40], fill=(255, 255, 255, 255))
    sy = card[3] - 34
    g.rounded_rectangle([card[0] + 34, sy, card[0] + 300, sy + 12], 6, fill=(255, 255, 255, 46))
    g.rounded_rectangle([card[0] + 34, sy, card[0] + 214, sy + 12], 6, fill=ACCENT + (220,))
    g.ellipse([card[0] + 200, sy - 6, card[0] + 228, sy + 22], fill=TEXT + (255,))

    left = 108
    cf = font(BOLD, 19)
    chips = []
    cx = left + 2
    for label in ["MINECRAFT 26.3", "FORGE 66.1.2", "JAVA 25", "MIT"]:
        cw = tracked_width(g, label, cf, 2.2) + 36
        g.rounded_rectangle([cx, 438, cx + cw, 478], 20, fill=(255, 255, 255, 18),
                            outline=(255, 111, 174, 105), width=2)
        chips.append((cx + 18, label))
        cx += cw + 12

    img.alpha_composite(glass)
    d = ImageDraw.Draw(img)

    tracked(d, (left + 2, 132), "MINECRAFT FORGE CLIENT", font(BOLD, 24),
            (255, 156, 200, 235), 7)

    word = font(BOLD, 170)
    x = left
    d.text((x, 168), "ASweet", font=word, fill=TEXT + (255,))
    x += d.textlength("ASweet", font=word)
    d.text((x, 168), "H", font=word, fill=ACCENT + (255,))
    width = int(d.textlength("ASweetH", font=word))

    bar = Image.new("RGBA", (width, 7), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bar)
    for i in range(width):
        t = i / max(1, width - 1)
        bd.line([(i, 0), (i, 6)],
                fill=tuple(round(ACCENT[k] + (ACCENT_DEEP[k] - ACCENT[k]) * t) for k in range(3)) + (255,))
    img.alpha_composite(bar, (left + 4, 356))

    d.text((left + 2, 384), "Glass-black, rose accent, zero chrome.",
           font=font(REG, 34), fill=(233, 217, 228, 235))

    for x, label in chips:
        tracked(d, (x, 449), label, cf, (243, 231, 239, 245), 2.2)

    img.convert("RGB").save(os.path.join(OUT, "banner.png"))



THEMES = [
    ("Rose Glass", (0xFF, 0x6F, 0xAE), (0xC2, 0x18, 0x5B), (0x1B, 0x10, 0x22), (0xF7, 0xEC, 0xF3)),
    ("Obsidian", (0xFF, 0x8F, 0xC0), (0x7A, 0x1F, 0x45), (0x0E, 0x0E, 0x12), (0xED, 0xED, 0xF2)),
    ("Sakura Milk", (0xE0, 0x44, 0x7F), (0xA8, 0x1F, 0x55), (0xFD, 0xF3, 0xF8), (0x2A, 0x16, 0x20)),
    ("Opaque", (0xFF, 0x6F, 0xAE), (0xC2, 0x18, 0x5B), (0x15, 0x10, 0x1A), (0xFF, 0xFF, 0xFF)),
]


def themes():
    w, h = 1600, 420
    img = vgradient(w, h, BG_TOP, (19, 11, 24)).convert("RGBA")
    glow(img, 1440, 20, 420, ACCENT, 40)
    d = ImageDraw.Draw(img)

    pad, gap = 104, 24
    card_w = (w - 2 * pad - 3 * gap) // 4
    top, bottom = 74, 346
    name_f, hex_f, cap_f = font(BOLD, 29), font(BOLD, 21), font(REG, 18)

    d.text((pad, 34), "FOUR PALETTES, ONE SYSTEM", font=font(BOLD, 21), fill=(255, 156, 200, 230))

    for i, (name, accent, deep, panel, text) in enumerate(THEMES):
        x = pad + i * (card_w + gap)
        d.rounded_rectangle([x, top, x + card_w, bottom], 22,
                            fill=panel + (246,), outline=accent + (140,), width=2)
        d.text((x + 30, top + 30), name, font=name_f, fill=text + (255,))
        d.rounded_rectangle([x + 30, top + 74, x + 92, top + 80], 3, fill=accent + (255,))

        for j, swatch in enumerate([accent, deep, panel, text]):
            sx = x + 30 + j * 62
            d.ellipse([sx, top + 104, sx + 48, top + 152], fill=swatch + (255,),
                      outline=(120, 120, 130, 90), width=1)

        d.text((x + 30, top + 178), "#%02X%02X%02X" % accent, font=hex_f, fill=accent + (255,))
        d.text((x + 30, top + 208), "accent", font=cap_f, fill=text + (170,))

    img.convert("RGB").save(os.path.join(OUT, "themes.png"))


banner()
themes()
print("wrote", sorted(os.listdir(OUT)))
