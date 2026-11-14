"""Paints the app's artwork with Codex's image tool: creature portraits, feat medals, the outpost, banners, icons, crests.

python tools/paint.py beasts              # every creature without a portrait yet
python tools/paint.py medals banners      # several kinds at once
python tools/paint.py beasts nixie lynx   # just these ones, replacing what's there
python tools/paint.py outpost             # the camp at each rank, night then day (each rank edits the one before)

Full-size PNGs stay in build/paint/<kind>; what the app ships goes to app/src/main/assets/<kind>.
"""
import json, os, re, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/app/ranger/domain"
RAW = ROOT / "build/paint"
ASSETS = ROOT / "app/src/main/assets"
CODEX = "codex.cmd" if os.name == "nt" else "codex"

PAPER = ("hand-painted page from a medieval bestiary kept by a forest ranger: fine iron-gall ink line work with soft watercolor "
         "washes on warm cream parchment, muted earthy palette (river blue, forest green, ochre, plum, small vermilion accents)")
NO_TEXT = "No text, no letters, no numbers, no runes, no signature, no watermark."
ROUND = ("The painting sits inside a soft circular vignette that fades into plain flat cream (#F6EDD8) toward the corners. "
         "No border, no frame. Square image.")


# ---- what to paint ------------------------------------------------------------------------------------------------

def strings(n):
    return r',\s*'.join([r'"((?:[^"\\]|\\.)*)"'] * n)


def beasts():
    s = (SRC / "Bestiary.kt").read_text(encoding="utf8")
    tier = {1: "", 2: " It is a rare visitor, slightly otherworldly.", 3: " It is a legend, majestic, with a faint golden glow."}
    for m in re.finditer(r'c\("(\w+)",\s*' + strings(6) + r',\s*(\d+),\s*(\d)', s, re.S):
        yield m[1], (f"Image: \"{m[3]}\", a creature of the Borderlands. {m[7]} It is met when a runner does this: {m[5]} "
                     f"Show that setting (weather, time of day, terrain).{tier[int(m[9])]}\n\nStyle: a {PAPER}. Composition: a single "
                     f"portrait of the creature, centred, clearly the subject and large (about 70% of the frame), a simple, uncluttered "
                     f"suggestion of its setting behind it so it still reads as a small thumbnail. {ROUND} {NO_TEXT}")


METAL = {
    "DISTANCE": "bronze with vermilion enamel", "ROAD": "brass and old gold", "RHYTHM": "silver with forest-green enamel",
    "SKY": "dark iron with midnight-blue enamel and silver stars", "WORLD": "silver with river-blue enamel", "LORE": "gold with plum enamel",
}


def medals():
    s = (SRC / "Feats.kt").read_text(encoding="utf8")
    for m in re.finditer(r'^\s+([A-Z0-9_]+)\(FeatGroup\.([A-Z]+),\s*Glyph\.([A-Z]+),\s*L\(' + strings(2) + r'\),\s*L\(' + strings(2) + r'\)', s, re.M):
        key, group, glyph, title, about = m[1], m[2], m[3], m[5], m[7]
        yield key.lower(), (f"Image: a round medal awarded to a forest ranger for the feat \"{title}\" ({about}). A struck {METAL[group]} "
                            f"medallion seen straight on, filling about 85% of the frame, with a raised relief scene inside that tells the "
                            f"feat at a glance (its emblem is a {glyph.lower()}), a beaded rim, soft highlights. The medal is the only "
                            f"object: no ribbon, no hands, no table.\n\nStyle: a {PAPER}, the metal painted in watercolor. {ROUND} {NO_TEXT}")


RANKS = [
    "a campfire, a hooded ranger sitting by it, a sleeping dog and two small canvas tents in a clearing",
    "and now a wooden stockade of sharpened logs around the camp",
    "and now a tall wooden watchtower with a lit window on the left inside the stockade",
    "and now a timber gate in the stockade with a red banner bearing a pine tree",
    "and now a small forge with a glowing hearth and an anvil on the right",
    "and now a long timber longhouse with smoke rising, behind the tents",
    "with the wooden stockade now replaced by a low wall of grey stone",
    "and now a square stone keep tower rising in the middle of the outpost",
    "and now a beacon fire burning on a far hill in the background",
    "and now a golden flag flying from the top of the stone keep",
]


def outpost_prompt(i, day):
    hour = ("on a clear summer day, soft daylight, blue sky with a few clouds, the campfire small and smoking" if day else
            "at night under a starry sky with a crescent moon, deep blue darkness, warm orange firelight glowing from the campfire, torches and windows")
    if i == 0:
        return (f"Image: a wide landscape painting of a small ranger's camp on the edge of a forest kingdom {hour}: {RANKS[0]}, pine forest "
                f"and rolling hills behind. Seen from a little above and some distance away, the camp in the middle of the frame, plenty "
                f"of sky above. Style: a {PAPER}, painted edge to edge (no vignette, no border). Landscape 3:2. {NO_TEXT}")
    return (f"Edit the attached painting: keep the same place, viewpoint, composition, style, lighting and every existing building, "
            f"tent, figure and tree exactly as they are, {RANKS[i]}. Landscape 3:2, painted edge to edge. {NO_TEXT}")


def day_prompt():
    return ("Edit the attached painting: keep exactly the same place, viewpoint, composition, buildings, tents, figures and style, "
            "but show it on a clear summer day: soft daylight, a blue sky with a few clouds, no moon or stars, the campfire small "
            f"and smoking, windows unlit. Landscape 3:2, painted edge to edge. {NO_TEXT}")


BANNERS = {
    "map": "a cartographer's table in a ranger's tent: a half-drawn parchment map of forests and rivers, a compass, a quill, a brass dividers, a candle",
    "bestiary": "an open leather-bound bestiary on a mossy stone, its pages showing small painted creatures, a feather and ferns around it, forest light",
    "feats": "a ranger's trophy wall of timber: medals on ribbons, a horn, crossed arrows and a small shield, lit by warm lamplight",
    "chronicle": "a scribe's desk at night: an open journal with lines of handwriting too small to read, an inkwell and quill, a candle, a window with stars",
    "summary": "a ranger's study table: an hourglass, a stack of journals, a pocket compass, a sprig of pine, soft morning light",
    "settings": "a ranger's workbench inside a tent: a lantern, rope, a whetstone, a pair of boots, folded cloak, tools laid out neatly",
    "board": "a weathered wooden notice board at a crossroads in the woods with several pinned parchment scrolls and a lantern hanging on it",
    "boots": "a row of worn leather running boots hung by their laces on a log wall of the outpost, autumn leaves, warm light",
    "band": "a small band of rangers and a dog running together along a forest trail at golden hour, seen from a distance",
    "skills": "a ranger's training ground at the edge of the forest: a straw target with arrows, a climbing rope on a tree, a stepping-stone path, morning mist",
    "add": "a raven arriving at the outpost carrying a rolled parchment in its beak, the camp's gate behind it",
    "steps": "a winding footpath through meadows and woods toward a distant village, a walking staff leaning on a milestone",
    "news": "a herald on horseback arriving at a forest outpost at dusk, unrolling a scroll, a banner on his lance",
    "rank": "a ranger's banner and heraldic shield hanging in a timber hall above a hearth, warm firelight",
}


def banners():
    for k, what in BANNERS.items():
        yield k, (f"Image: a wide banner illustration: {what}. The main subject sits in the middle band of the image (the top and bottom "
                  f"fifth may be cropped away), with calm painted space at both sides. Style: a {PAPER}, painted edge to edge "
                  f"(no vignette, no border). Landscape 3:2. {NO_TEXT}")


ICONS = {
    "tab_outpost": "a wooden watchtower on stilts with a little pointed roof and a flag",
    "tab_map": "a folded parchment map with a dotted trail and a small cross",
    "tab_bestiary": "an animal's paw print inside a round seal",
    "tab_chronicle": "an open book with a ribbon bookmark",
    "add": "a plus sign made of two crossed quill strokes",
    "settings": "a compass rose with eight points",
    "share": "a single swallow in flight, wings spread, heading up and to the right, as a bold simple silhouette",
    "stat_distance": "a winding road with a milestone",
    "stat_time": "an hourglass",
    "stat_climb": "a mountain peak with a small flag on top",
    "stat_pace": "a running boot with three speed lines behind it",
    "stat_pulse": "a heart",
    "stat_longest": "a long banner on a pole",
    "stat_patrols": "a lantern",
    "stat_zones": "a flame",
    "stat_form": "a shield with an upward chevron",
    "stat_steps": "a pair of footprints",
    "stat_rest": "a heart resting on a crescent moon",
    "stat_records": "a rolled scroll with a wax seal",
    "stat_years": "a wall calendar page",
    "stat_easy": "a feather",
}


def icons():
    for k, what in ICONS.items():
        yield k, (f"Image: a single small app icon: {what}. Solid black ink on a pure white background, bold confident hand-inked "
                  f"lines like a woodcut stamp, simple enough to read at 24 pixels, centred, filling about 75% of the frame, no shading "
                  f"gradients, no grey, no other objects. Square image. {NO_TEXT}")


CRESTS = [
    "a plain wooden shield with a single pine twig",
    "a leather-covered shield with a crossed spear and torch",
    "a green shield with a silver chevron and a pine",
    "a green shield with a silver chevron, a pine and two stars",
    "a vermilion shield with a gold chevron and an oak leaf",
    "a vermilion shield with a gold chevron, an oak leaf and a bordure of studs",
    "a blue shield with a silver tower and a gold chevron",
    "a blue shield with a silver keep, a gold chevron and three stars",
    "a deep crimson shield with a gold keep, a beacon flame and a laurel border",
    "a gold-edged midnight shield with a crowned keep, a golden flag and a laurel wreath",
]


def crests():
    for i, what in enumerate(CRESTS):
        yield f"rank{i}", (f"Image: a heraldic shield crest for a forest ranger's rank: {what}. Only the shield, seen straight on, "
                           f"centred, filling about 85% of the frame, on a pure white background with nothing else. Style: a {PAPER}, "
                           f"the shield painted in watercolor with crisp ink outlines. Square image. {NO_TEXT}")


def logo():
    yield "logo", (f"Image: an app icon: a wooden watchtower with a small campfire glowing at its foot, pines behind, a crescent moon, "
                   f"inside a round medallion of deep night blue with a warm cream rim. Bold and simple so it reads at small size. "
                   f"Style: a {PAPER}. The medallion fills about 80% of the frame on a plain flat cream (#F6EDD8) background. Square image. {NO_TEXT}")


KINDS = {"beasts": beasts, "medals": medals, "banners": banners, "icons": icons, "crests": crests, "logo": logo}


# ---- turning a painting into what the app ships ---------------------------------------------------------------------

def ship(kind, key, png):
    im = Image.open(png)
    out = ASSETS / kind
    out.mkdir(parents=True, exist_ok=True)
    if kind in ("beasts", "medals"):
        n = 640 if kind == "beasts" else 512
        im.convert("RGB").resize((n, n), Image.LANCZOS).save(out / f"{key}.webp", "WEBP", quality=80 if kind == "beasts" else 78, method=6)
    elif kind in ("banners", "outpost"):
        w = 1200 if kind == "banners" else 1280
        im = im.convert("RGB"); im.resize((w, round(im.height * w / im.width)), Image.LANCZOS).save(out / f"{key}.webp", "WEBP", quality=80 if kind == "banners" else 76, method=6)
    elif kind == "icons":
        # ink on white becomes an alpha mask, so the app can tint it
        g = ImageOps.autocontrast(im.convert("L").resize((192, 192), Image.LANCZOS), cutoff=1)
        a = g.point(lambda v: 0 if v > 225 else 255 if v < 60 else int((225 - v) * 255 / 165))
        Image.merge("RGBA", (Image.new("L", a.size, 255),) * 3 + (a,)).save(out / f"{key}.png", optimize=True)
    elif kind == "crests":
        # the white around the shield becomes transparent
        rgba = im.convert("RGBA").resize((320, 320), Image.LANCZOS)
        mask = Image.new("L", rgba.size, 0)
        flood = rgba.convert("RGB").copy()
        for corner in [(0, 0), (319, 0), (0, 319), (319, 319)]:
            ImageDraw.floodfill(flood, corner, (255, 0, 255), thresh=40)
        px, mp = flood.load(), mask.load()
        for y in range(320):
            for x in range(320):
                mp[x, y] = 0 if px[x, y] == (255, 0, 255) else 255
        rgba.putalpha(mask)
        rgba.crop(rgba.getbbox()).save(out / f"{key}.webp", "WEBP", quality=85, method=6)
    elif kind == "logo":
        # the launcher icon's foreground, and the README's logo with rounded corners
        out.rmdir()
        im = im.convert("RGB")
        im.resize((432, 432), Image.LANCZOS).save(ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_art.webp", "WEBP", quality=90)
        logo = im.convert("RGBA").resize((512, 512), Image.LANCZOS)
        m = Image.new("L", logo.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, 511, 511), radius=112, fill=255); logo.putalpha(m)
        logo.save(ROOT / "docs/logo.png", optimize=True)


# ---- talking to codex -------------------------------------------------------------------------------------------------

def codex(prompt, workdir, last, image=None):
    cmd = [CODEX, "exec", "--skip-git-repo-check", "--ephemeral", "-s", "read-only", "-o", str(last)]
    if image: cmd += ["-i", str(image)]
    full = ("Use your image generation tool to create exactly ONE image, then reply with only the absolute path of the generated "
            "PNG file. Do not run any shell commands.\n\n" + prompt)
    # the prompt goes in on stdin: a .cmd launcher would cut a multi-line argument at its first line
    subprocess.run(cmd + ["-"], cwd=workdir, input=full.encode("utf8"), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=1200)
    text = last.read_text(encoding="utf8") if last.exists() else ""
    found = re.findall(r'[A-Za-z]:[\\/][^\s`)\]"\'<>]+?\.png|/[^\s`)\]"\'<>]+?\.png', text)
    return found[-1] if found and os.path.exists(found[-1]) else None


def paint(kind, key, prompt, image=None):
    d = RAW / kind; d.mkdir(parents=True, exist_ok=True)
    for _ in range(3):
        got = codex(prompt, d, d / f"{key}.txt", image)
        if got:
            shutil.copy(got, d / f"{key}.png")
            ship(kind, key, d / f"{key}.png")
            return f"{kind}/{key} ok"
    return f"{kind}/{key} FAILED"


def outpost():
    """Night paintings in order, each built on the one before so the camp grows in place; then each one by day."""
    d = RAW / "outpost"; d.mkdir(parents=True, exist_ok=True)
    for i in range(len(RANKS)):
        if not (d / f"night{i}.png").exists():
            print(paint("outpost", f"night{i}", outpost_prompt(i, False), d / f"night{i - 1}.png" if i else None), flush=True)
    with ThreadPoolExecutor(5) as ex:
        for r in ex.map(lambda i: paint("outpost", f"day{i}", day_prompt(), d / f"night{i}.png"),
                        [i for i in range(len(RANKS)) if not (d / f"day{i}.png").exists()]):
            print(r, flush=True)


def painted(kind, key):
    """Whether this one already ships, so a plain run only paints what's missing."""
    if kind == "logo": return (ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_art.webp").exists()
    return any((ASSETS / kind).glob(f"{key}.*"))


def main():
    args = sys.argv[1:]
    kinds = [a for a in args if a in KINDS or a == "outpost"]
    only = set(args) - set(kinds)
    for k in kinds:
        if k == "outpost":
            outpost(); continue
        jobs = [(k, key, p) for key, p in KINDS[k]()
                if (key in only if only else not painted(k, key))]
        with ThreadPoolExecutor(6) as ex:
            for r in ex.map(lambda j: paint(*j), jobs):
                print(r, flush=True)


if __name__ == "__main__":
    main()
