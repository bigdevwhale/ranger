"""Builds the Google Play graphics in docs/google-play/graphics from the README screenshots.

python tools/play.py

Takes docs/screenshots/<lang>/<name>.jpg (1080x2341 renders of the demo, see tools/shots.py) and writes the
512 px icon, the 1024x500 feature graphics, the 1600x900 launch posts and eight 1080x1920 captioned
screenshots per language. Play wants screenshots no longer than twice their width, so each screen runs off
the bottom edge under its caption. Captions are in Cormorant, the app's own headings; the banner is the
painted outpost at its highest rank, by night. Needs `pip install pillow`.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/google-play/graphics"
SHOTS = ROOT / "docs/screenshots"
RES = ROOT / "app/src/main/res"
HEAD = RES / "font/cormorant_bold.ttf"
HEAD_I = RES / "font/cormorant_italic.ttf"
COVER = ROOT / "app/src/main/assets/outpost/night9.webp"

# The app's colours (ui/theme/Theme.kt): the parchment, its ink, the wax seal, the gold, the night, the fire.
BG = (0xEF, 0xE3, 0xC8)
DEEP = (0xE5, 0xD5, 0xB2)
LINE = (0xCD, 0xB8, 0x8E)
INK = (0x2B, 0x21, 0x18)
SEAL = (0x9E, 0x3B, 0x26)
SEAL_DARK = (0x6E, 0x26, 0x18)
GOLD = (0xA8, 0x81, 0x2A)
NIGHT = (0x16, 0x1B, 0x2B)
ON_DARK = (0xF1, 0xE6, 0xCC)
FIRE = (0xF2, 0xA5, 0x3A)
PAPER = (0xF8, 0xE9, 0xD3)  # the launcher icon's ground

NAME = {"en": "Ranger", "ru": "Следопыт"}
LINES = {
    "en": ("Every run is a patrol", "on the edge of the kingdom"),
    "ru": ("Каждая пробежка — дозор", "на границе королевства"),
}

# The order they appear in the store and the caption: a line in ink and one in the seal's red.
PAGES = [
    ("1-outpost", ("Every run is a patrol,", "and the outpost grows"), ("Каждая пробежка — дозор,", "а застава растёт")),
    ("3-bestiary", ("57 creatures to meet", "on your runs"), ("57 существ встречаются", "на пробежках")),
    ("6-creature", ("Rain, fog, a full moon:", "the run decides who comes"), ("Дождь, туман, полнолуние:", "пробежка решает, кто придёт")),
    ("5-patrol", ("Route, pace, pulse", "and the weather of each run"), ("Маршрут, темп, пульс", "и погода каждой пробежки")),
    ("2-map", ("A map of only the land", "you have run through"), ("Карта только тех земель,", "что вы уже пробежали")),
    ("9-add", ("Health Connect, your watch", "or a Strava archive"), ("Health Connect, часы", "или архив Strava")),
    ("11-summary", ("Load, condition", "and your best times"), ("Нагрузка, состояние", "и лучшие времена")),
    ("10-share", ("A painted card", "of any run to share"), ("Открытка с любой", "пробежкой для друзей")),
]


def font(size, italic=False):
    return ImageFont.truetype(str(HEAD_I if italic else HEAD), size)


def fit(text, size, width, italic=False):
    while size > 20 and font(size, italic).getlength(text) > width:
        size -= 2
    return font(size, italic)


def rounded(im, r):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def lay(g, card, x, y, r=40, outline=INK):
    """A screen on the parchment: rounded corners, a soft warm shadow and a thin ink edge."""
    sh = Image.new("RGBA", (card.width + 120, card.height + 120), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((60, 76, 60 + card.width, 76 + card.height), r, fill=(70, 45, 20, 120))
    g.alpha_composite(sh.filter(ImageFilter.GaussianBlur(22)), (x - 60, y - 60))
    g.alpha_composite(rounded(card, r), (x, y))
    ImageDraw.Draw(g).rounded_rectangle((x - 2, y - 2, x + card.width + 1, y + card.height + 1), r, outline=outline, width=3)


def board(W, H):
    """Parchment with a soft darker edge, like a page of the journal."""
    g = Image.new("RGBA", (W, H), BG + (255,))
    edge = Image.new("L", (W, H), 0)
    ImageDraw.Draw(edge).rectangle((0, 0, W - 1, H - 1), outline=110, width=60)
    g.paste(Image.new("RGBA", (W, H), DEEP + (255,)), (0, 0), edge.filter(ImageFilter.GaussianBlur(50)))
    return g


def rule(d, cx, y, half):
    """The journal's divider: a gold line with a small diamond in the middle."""
    d.line((cx - half, y, cx - 16, y), fill=GOLD, width=3)
    d.line((cx + 16, y, cx + half, y), fill=GOLD, width=3)
    d.polygon([(cx, y - 9), (cx + 9, y), (cx, y + 9), (cx - 9, y)], fill=GOLD)


def screenshot(src, caption):
    W, H = 1080, 1920
    g = board(W, H)
    d = ImageDraw.Draw(g)
    f = min((fit(c, 84, W - 120) for c in caption), key=lambda x: x.size)
    d.text((W / 2, 112), caption[0], font=f, fill=INK, anchor="mm")
    d.text((W / 2, 206), caption[1], font=f, fill=SEAL, anchor="mm")
    rule(d, W // 2, 284, 150)
    im = Image.open(src).convert("RGB")
    w = 880
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    top = 340
    # only the top corners show: the screen runs on past the bottom edge
    lay(g, im.crop((0, 0, w, H - top + 60)), (W - w) // 2, top)
    return g.convert("RGB")


def night_cover(W, H, reach):
    """The painted outpost by night across the picture, darkened to the left under the words."""
    cover = Image.open(COVER).convert("RGB")
    k = max(W / cover.width, H / cover.height)
    cover = cover.resize((round(cover.width * k), round(cover.height * k)), Image.LANCZOS)
    x0 = cover.width - W
    g = cover.crop((x0, (cover.height - H) // 2, x0 + W, (cover.height - H) // 2 + H)).convert("RGBA")
    shade = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(shade)
    for x in range(W):
        sd.line([(x, 0), (x, H)], fill=max(0, min(242, int(420 * (1 - x / (W * reach))))))
    g.paste(Image.new("RGBA", (W, H), NIGHT + (255,)), (0, 0), shade)
    return g


def logo(size):
    return Image.open(ROOT / "docs/logo.png").convert("RGBA").resize((size, size), Image.LANCZOS)


def shadowed(d, xy, text, font, fill, off=3):
    d.text((xy[0] + off, xy[1] + off), text, font=font, fill=(8, 10, 18))
    d.text(xy, text, font=font, fill=fill)


def feature(lang):
    W, H = 1024, 500
    g = night_cover(W, H, .8)
    g.alpha_composite(logo(116), (54, 54))
    d = ImageDraw.Draw(g)
    name = NAME[lang]
    shadowed(d, (190, 64), name, fit(name, 96, 360), ON_DARK)
    lines = LINES[lang]
    f = min((fit(l, 46, 470, True) for l in lines), key=lambda x: x.size)
    d.line((60, 236, 250, 236), fill=SEAL, width=5)
    for i, l in enumerate(lines):
        shadowed(d, (58, 262 + i * 56), l, f, ON_DARK if i == 0 else FIRE, 2)
    g.convert("RGB").save(OUT / f"feature-graphic-{lang}.png", optimize=True)


def post(lang):
    """The launch post: the banner's look at 1600x900, with a «now on Google Play» plaque."""
    W, H = 1600, 900
    g = night_cover(W, H, .75)
    g.alpha_composite(logo(180), (90, 100))
    d = ImageDraw.Draw(g)
    name = NAME[lang]
    shadowed(d, (88, 300), name, fit(name, 150, 760), ON_DARK, 5)
    d.line((94, 498, 400, 498), fill=SEAL, width=7)
    lines = LINES[lang]
    f = min((fit(l, 66, 740, True) for l in lines), key=lambda x: x.size)
    for i, l in enumerate(lines):
        shadowed(d, (92, 528 + i * 78), l, f, ON_DARK if i == 0 else FIRE)
    # the plaque: the app's red button, rounded, with a darker lip below
    plaque = "Now on Google Play" if lang == "en" else "Теперь в Google Play"
    pf = font(52)
    tw = int(pf.getlength(plaque))
    px, py, ph = 94, 730, 88
    d.rounded_rectangle((px, py + 8, px + tw + 80, py + ph + 8), 22, fill=SEAL_DARK)
    d.rounded_rectangle((px, py, px + tw + 80, py + ph), 22, fill=SEAL)
    d.text((px + 40, py + ph // 2), plaque, font=pf, fill=ON_DARK, anchor="lm")
    g.convert("RGB").save(OUT / f"post-{lang}.png", optimize=True)


def icon():
    """The adaptive icon flattened: the painted medallion on its paper ground, inset as on the phone (10 of 108 dp).
    Play rounds the corners itself, so the square stays full."""
    art = Image.open(RES / "drawable-nodpi/ic_launcher_art.webp").convert("RGB")
    side = round(512 * 88 / 108)
    sq = Image.new("RGB", (512, 512), PAPER)
    sq.paste(art.resize((side, side), Image.LANCZOS), ((512 - side) // 2, (512 - side) // 2))
    sq.save(OUT / "icon-512.png", optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    icon()
    for lang in ("en", "ru"):
        feature(lang)
        post(lang)
        folder = OUT / f"screenshots-{lang}"
        folder.mkdir(exist_ok=True)
        for i, (name, en, ru) in enumerate(PAGES, 1):
            screenshot(SHOTS / lang / f"{name}.jpg", en if lang == "en" else ru).save(folder / f"{i}.png", optimize=True)
    print("done:", OUT)


if __name__ == "__main__":
    main()
