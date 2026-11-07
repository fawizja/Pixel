#!/usr/bin/env python3
"""PROBA (galaz lpctrial): postacie NPC z Universal LPC Spritesheet Character Generator w formacie strojow gry.

Generator: https://github.com/LiberatedPixelCup/Universal-LPC-Spritesheet-Character-Generator (fork:
https://taarunshetty.github.io/Character-Generator/). Kazda warstwa ma wlasne licencje i autorow (CREDITS.csv
generatora); narzedzie zapisuje je obok grafik (assets/art/lpc_trial/CREDITS-LPC.csv).

Uzycie:
  python3 -I tools/art/lpc_compose.py <katalog z CREDITS.csv i spritesheets/ generatora> [--sheet plik.png]

Arkusz LPC walk.png: 9 klatek 64x64 w wierszu (0 = postoj, 1-8 = cykl kroku), wiersze: gora, lewo, dol, prawo.
Stroje gry (tools/art/otsp_import.py): postoj + 2 klatki kroku (_walk1, _walk2), obrazek przyciety i dopelniony do
kwadratu, w grze sciskany do jednego pola 32x32 (TileArt.override). NPC sa rysowane tylko przodem (kierunek "dol").

Dwa warianty skali (w grze: -Dpixel.lpcTrial=a albo =b, domyslnie wylaczone):
  a - cala klatka 64x64 zmniejszona do pola 32x32 (skala 1:2), postac ok. 24 px wysokosci;
  b - postac przeskalowana do wysokosci postaci OTSP w grze (mediana NPC z Varden, ok. 31 px), obrazek 32x32.
"""
import csv
import glob
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "assets", "art", "lpc_trial")
FRAME = 64
ROWS = {"up": 0, "left": 1, "down": 2, "right": 3}
DIRS = ("down", "up", "left", "right")   # kolejnosc jak TileArt.DIRS
WALK = (0, 3, 7)                          # postoj i dwie klatki z nogami najdalej (lewa, prawa)

# Kolejnosc rysowania (od spodu) jak w generatorze: tlo peleryny/wlosow, cialo, glowa, nogi, tulow, okrycia, wlosy, czapki.
Z = ("cape/solid/bg", "hair/princess/adult/bg", "body/", "head/", "feet/", "legs/", "torso/", "dress/",
     "cape/solid/fg", "beards/", "hair/", "hat/")

# NPC: klucz z npcs.csv -> warstwy (sciezka wzgledem spritesheets/, barwa RGB albo None = kolor oryginalny).
NPCS = {
    "petkeeper": [
        ("body/bodies/male/walk.png", None), ("head/heads/human/male/walk.png", None),
        ("feet/shoes/basic/male/walk.png", (90, 60, 40)), ("legs/pants/male/walk.png", (120, 90, 55)),
        ("torso/clothes/longsleeve/longsleeve/male/walk.png", (106, 138, 58)),
        ("beards/beard/basic/walk.png", (110, 70, 40)), ("hair/plain/adult/walk.png", (110, 70, 40)),
    ],
    "ruler": [   # krol (Varden: Krol Aurelian)
        ("cape/solid/bg/walk.png", (170, 30, 40)),
        ("body/bodies/male/walk.png", None), ("head/heads/human/male/walk.png", None),
        ("feet/shoes/basic/male/walk.png", (60, 45, 35)), ("legs/pants/male/walk.png", (60, 50, 90)),
        ("torso/clothes/longsleeve/longsleeve/male/walk.png", (200, 160, 64)),
        ("cape/solid/fg/walk.png", (170, 30, 40)),
        ("beards/beard/basic/walk.png", (225, 215, 190)), ("hair/plain/adult/walk.png", (225, 215, 190)),
        ("hat/formal/crown/adult/walk.png", None),
    ],
    "queen": [   # krolowa (Frostholm: Krolowa Swietoslawa)
        ("cape/solid/bg/walk.png", (70, 90, 160)), ("hair/princess/adult/bg/walk.png", (235, 205, 120)),
        ("body/bodies/female/walk.png", None), ("head/heads/human/female/walk.png", None),
        ("feet/shoes/basic/thin/walk.png", (60, 45, 35)),
        ("dress/slit/female/walk/maroon.png", None),
        ("cape/solid/fg/walk.png", (70, 90, 160)),
        ("hair/princess/adult/fg/walk.png", (235, 205, 120)), ("hat/formal/tiara/adult/walk.png", None),
    ],
    "imbuer": [
        ("body/bodies/male/walk.png", None), ("head/heads/human/male/walk.png", None),
        ("feet/shoes/basic/male/walk.png", (50, 40, 50)), ("legs/pants/male/walk.png", (70, 50, 110)),
        ("torso/clothes/longsleeve/longsleeve/male/walk.png", (112, 80, 176)),
        ("beards/beard/basic/walk.png", (190, 190, 195)), ("hair/plain/adult/walk.png", (190, 190, 195)),
        ("hat/magic/wizard/base/adult/walk.png", (112, 80, 176)), ("hat/magic/wizard/belt/adult/walk.png", None),
    ],
    "guard": [
        ("body/bodies/male/walk.png", None), ("head/heads/human/male/walk.png", None),
        ("feet/armour/plate/male/walk.png", None), ("legs/armour/plate/male/walk.png", None),
        ("torso/armour/plate/male/walk.png", None), ("hat/helmet/kettle/adult/walk.png", None),
    ],
}
# Ktorzy NPC w grze dostaja postac LPC (tylko z -Dpixel.lpcTrial).
APPLY = {"varden_petkeeper": "petkeeper", "varden_king": "ruler", "frost_ruler": "queen", "varden_imbuer": "imbuer",
         "guard_21": "guard", "guard_22": "guard", "guard_23": "guard", "guard_24": "guard"}


def zkey(path):
    for i, prefix in enumerate(Z):
        if path.startswith(prefix):
            return i
    return len(Z)


def tint(img, rgb):
    """Przebarwienie warstwy (biale/szare bazy LPC): barwa * jasnosc piksela, jak tint() w otsp_import.py."""
    a = np.asarray(img.convert("RGBA")).astype(np.float32)
    lum = 0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]
    vis = a[..., 3] > 0
    top = max(np.percentile(lum[vis], 90) if vis.any() else 255, 40)
    k = np.clip(lum / top, 0, 1) ** 0.8
    out = a.copy()
    for c in range(3):
        out[..., c] = rgb[c] * k
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def compose(src, layers):
    """Pelny arkusz walk (576x256) z warstw w kolejnosci Z."""
    sheet = None
    for path, rgb in sorted(layers, key=lambda l: zkey(l[0])):
        img = Image.open(os.path.join(src, "spritesheets", path)).convert("RGBA")
        if rgb:
            img = tint(img, rgb)
        sheet = img if sheet is None else Image.alpha_composite(sheet, img)
    return sheet


def frame(sheet, d, f):
    r = ROWS[d]
    return sheet.crop((f * FRAME, r * FRAME, (f + 1) * FRAME, (r + 1) * FRAME))


def shrink(img, w, h):
    """Zmniejszenie grafiki pikselowej: srednia z pola (alfa premnozona), prog alfy 50% i kolory przyciagniete do
    palety oryginalu - bez polprzezroczystych krawedzi i nowych odcieni."""
    a = np.asarray(img).astype(np.float32) / 255.0
    pre = a.copy()
    pre[..., :3] *= pre[..., 3:4]
    small = np.asarray(Image.fromarray((pre * 255).astype(np.uint8), "RGBA").resize((w, h), Image.BOX)).astype(np.float32) / 255.0
    alpha = small[..., 3]
    rgb = np.where(alpha[..., None] > 0, small[..., :3] / np.maximum(alpha[..., None], 1e-6), 0)
    pal = np.unique((a[a[..., 3] > 0.5][:, :3] * 255).round().astype(np.int32), axis=0)
    out = np.zeros((h, w, 4), np.uint8)
    vis = alpha >= 0.5
    if pal.size and vis.any():
        px = (rgb[vis] * 255)
        dist = ((px[:, None, :] - pal[None, :, :]) ** 2).sum(-1)
        out[vis, :3] = pal[dist.argmin(1)]
        out[vis, 3] = 255
    return Image.fromarray(out, "RGBA")


def otsp_height():
    """Wysokosc postaci OTSP w grze (px na polu 32): mediana NPC z Varden po scisnieciu kwadratu do 32 px."""
    hs = []
    for f in glob.glob(os.path.join(ROOT, "assets", "art", "npcs", "varden_*.png")):
        if "_walk" in f:
            continue
        im = Image.open(f).convert("RGBA")
        box = im.getbbox()
        if box:
            hs.append((box[3] - box[1]) * 32 / im.height)
    return int(round(sorted(hs)[len(hs) // 2])) if hs else 31


def variant_a(frames):
    """Cala klatka 64x64 -> pole 32x32 (skala 1:2), stopy zostaja tam, gdzie w klatce LPC."""
    return [shrink(f, 32, 32) for f in frames]


def variant_b(frames, height):
    """Postac (wspolny prostokat klatek) przeskalowana do wysokosci OTSP, stopy przy dolnej krawedzi pola."""
    boxes = [f.getbbox() for f in frames]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    s = height / (box[3] - box[1])
    w = max(1, round((box[2] - box[0]) * s))
    out = []
    for f in frames:
        small = shrink(f.crop(box), w, height)
        tile = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        tile.alpha_composite(small, ((32 - w) // 2, 32 - height))
        out.append(tile)
    return out


def outfit_frames(sheet, d):
    return [frame(sheet, d, f) for f in WALK]


def save_set(imgs, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    imgs[0].save(path + ".png", optimize=True)
    for k, img in enumerate(imgs[1:], 1):
        img.save(f"{path}_walk{k}.png", optimize=True)


def credits(src, used):
    rows = {}
    with open(os.path.join(src, "CREDITS.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f, skipinitialspace=True):
            rows[r["filename"].strip()] = {k: (v or "").strip() for k, v in r.items()}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "CREDITS-LPC.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["layer", "used_by", "licenses", "authors", "urls"])
        for path in sorted(used):
            # warianty kolorow (walk/maroon.png) sa w CREDITS.csv pod nazwa arkusza (walk.png)
            r = rows.get(path) or rows.get(path.rsplit("/walk/", 1)[0] + "/walk.png")
            if r is None:
                raise SystemExit(f"Brak warstwy {path} w CREDITS.csv - nie mozna jej uzyc bez autorow i licencji")
            w.writerow([path, " ".join(sorted(used[path])), r["licenses"], r["authors"], r["urls"]])


def sheet_png(sheets, height, path):
    """Arkusz porownawczy: wiersz OTSP (rycerz, 4 kierunki x postoj/krok1/krok2), wiersze LPC a/b dla kazdej postaci
    i na koncu NPC OTSP przodem obok tych samych NPC w LPC, na trawie z paczki OTSP, wszystko jak w grze (pole 32 px) powiekszone x4."""
    from PIL import ImageDraw
    grass = os.path.join(ROOT, "assets", "art", "tiles", "grass.png")
    bg = Image.open(grass).convert("RGBA").resize((32, 32), Image.NEAREST) if os.path.exists(grass) else None
    rows = []
    otsp = []
    for d in DIRS:
        base = os.path.join(ROOT, "assets", "art", "characters", f"knight_{d}")
        for suf in ("", "_walk1", "_walk2"):
            im = Image.open(base + suf + ".png").convert("RGBA")
            otsp.append(im.resize((32, 32), Image.NEAREST))   # jak TileArt: kwadrat sciskany do pola
    rows.append(("OTSP knight (gracz)", otsp))
    for name in ("guard", "ruler", "queen", "imbuer", "petkeeper"):
        a, b = [], []
        for d in DIRS:
            fr = outfit_frames(sheets[name], d)
            a += variant_a(fr)
            b += variant_b(fr, height)
        rows.append((f"LPC {name} - a (klatka 1:2)", a))
        rows.append((f"LPC {name} - b (wys. OTSP {height}px)", b))
    # NPC OTSP (w grze tylko przodem) obok tych samych NPC w LPC a/b
    keys = list(APPLY)[:5]
    # straznicy z Varden (guard_21..24) nie maja jeszcze grafiki OTSP - do porownania straznik guard_1 z Valmir
    npc = [Image.open(os.path.join(ROOT, "assets", "art", "npcs", (k if k != "guard_21" else "guard_1") + ".png"))
           .convert("RGBA").resize((32, 32), Image.NEAREST) for k in keys]
    rows.append(("OTSP NPC (przod, te same role)", npc))
    rows.append(("LPC te same - a", [variant_a(outfit_frames(sheets[APPLY[k]], "down"))[0] for k in keys]))
    rows.append(("LPC te same - b", [variant_b(outfit_frames(sheets[APPLY[k]], "down"), height)[0] for k in keys]))
    z, label = 4, 230
    W, H = label + 12 * 32 * z + 11 * 4, len(rows) * (32 * z + 8) + 40
    out = Image.new("RGBA", (W, H), (40, 40, 46, 255))
    dr = ImageDraw.Draw(out)
    for i, d in enumerate(DIRS):
        dr.text((label + i * 3 * (32 * z + 4) + 8, 12), f"{d}: postoj / krok1 / krok2", fill=(230, 230, 230, 255))
    for r, (title, imgs) in enumerate(rows):
        y = 40 + r * (32 * z + 8)
        dr.text((8, y + 56), title, fill=(230, 230, 230, 255))
        for i, im in enumerate(imgs):
            x = label + i * (32 * z + 4)
            cell = bg.copy() if bg else Image.new("RGBA", (32, 32), (90, 120, 70, 255))
            cell.alpha_composite(im)
            out.alpha_composite(cell.resize((32 * z, 32 * z), Image.NEAREST), (x, y))
    out.save(path)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 1
    src = argv[1]
    sheet_out = argv[argv.index("--sheet") + 1] if "--sheet" in argv else None
    sheets = {name: compose(src, layers) for name, layers in NPCS.items()}
    used = {}
    for name, layers in NPCS.items():
        for path, _ in layers:
            used.setdefault(path, set()).add(name)
    credits(src, used)
    height = otsp_height()
    n = 0
    for key, name in APPLY.items():
        fr = outfit_frames(sheets[name], "down")
        save_set(variant_a(fr), os.path.join(OUT, "a", "npcs", key))
        save_set(variant_b(fr, height), os.path.join(OUT, "b", "npcs", key))
        n += 6
    if sheet_out:
        sheet_png(sheets, height, sheet_out)
    print(f"LPC: {n} obrazkow w {OUT}, wysokosc postaci OTSP {height}px")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
