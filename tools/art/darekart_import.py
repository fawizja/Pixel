#!/usr/bin/env python3
"""Grafiki od wlasciciela (2026-11-12, licencja nieznana, dodane na jego odpowiedzialnosc; docs/CREDITS.md):
kamienie z arkuszy "Aseprites" (07f940a2-Aseprites.rar, 4 arkusze 256x192) i zloty smok (4c56d725-...webp, 256x192).

    python3 -I tools/art/darekart_import.py ASEPRITES_DIR DRAGON_WEBP [katalog assets]

ASEPRITES_DIR: katalog z 1.png..4.png (1 szary gladki, 2 mchem porosniety, 3 ciemny popekany, 4 sniezny).
Zrodla leza poza repo; do assets/ trafiaja tylko wyciete kawalki:
- tiles/rock*.png (warianty `rock`: 12 malych kamieni 32x32 z arkuszy 1-3, bez skalowania) oraz native/rock_v13..v16.png
  (duze glazy 64x64, rysowane 2x2 pola jak krzaki) - zastepuja kamienie OTSP;
- tiles/snow_rock*.png (4 male z arkusza 4) i native/snow_rock_v5.png (duzy glaz);
- monsters/dune_drake*.png: zloty smok (cztery kierunki x trzy klatki; srodkowy wiersz = postoj) w oryginalnym 64x64;
- monsters/{emerald,hoarfrost,coal,pearl}_dragon*.png: cztery pozostale kolory rodu smokow, przebarwione z klatek zlotego
  (`recolour_dragons`: przesuniecie barwy/nasycenia, osobno oczy i jasne akcenty); ikony lusek i tarcz z lupu
  (`items/*_scale.png`, `items/*_shield.png` itd.) to przebarwione ikony wiwerny i smoczej tarczy.
  Samo przebarwienie: ``python3 tools/art/darekart_import.py --recolour [assets]`` (bez arkuszy zrodlowych).
Nieuzyte: chmury (Nubes: rozmyte, nie pikselart), kamienie jak arkusz 4 do `sandstone_rock`/`obsidian_rock` (brak piaskowych).
"""
import colorsys
import os
import sys

from PIL import Image

# male kamienie w arkuszu (x0, y0, x1, y1)
SMALL = [(161, 32, 192, 65), (193, 32, 225, 65), (161, 66, 193, 98), (193, 66, 225, 98)]
# duze glazy (x0, y0, x1, y1) - rog prawy dolny plotna 64x64
BIG = [(36, 28, 97, 98), (42, 98, 98, 161), (101, 40, 161, 98)]


def crop(sheet, box):
    c = sheet.crop(box)
    bb = c.getbbox()
    return c.crop(bb) if bb else c


def center32(sprite):
    w, h = sprite.size
    if h == 33:   # arkusz 4: jeden wiersz ponad 32 px - zdejmujemy ten z mniejsza iloscia pikseli (okruchy)
        top = sum(1 for x in range(w) if sprite.getpixel((x, 0))[3])
        bot = sum(1 for x in range(w) if sprite.getpixel((x, 32))[3])
        sprite = sprite.crop((0, 1, w, 33)) if top <= bot else sprite.crop((0, 0, w, 32))
        h = 32
    assert w <= 32 and h <= 32, sprite.size
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    out.paste(sprite, ((32 - w) // 2, (32 - h) // 2 + 1), sprite)
    return out


def big64(sprite):
    w, h = sprite.size
    if h == 65:   # arkusz 4: jeden wiersz za duzo - zdejmujemy gorny albo dolny (mniej pikseli)
        top = sum(1 for x in range(w) if sprite.getpixel((x, 0))[3])
        bot = sum(1 for x in range(w) if sprite.getpixel((x, 64))[3])
        sprite = sprite.crop((0, 1, w, 65)) if top <= bot else sprite.crop((0, 0, w, 64))
        h = 64
    assert w <= 64 and h <= 64, sprite.size
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.paste(sprite, (64 - w, 64 - h), sprite)
    return out


def main():
    ase, dragon = sys.argv[1], sys.argv[2]
    assets = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets")
    art = os.path.join(assets, "art")
    sheets = {n: Image.open(os.path.join(ase, f"{n}.png")).convert("RGBA") for n in (1, 2, 3, 4)}

    def name(base, k):
        return base + ("" if k == 1 else f"_v{k}")

    # rock: arkusze 1 (szary), 3 (ciemny popekany), 2 (mech) po cztery male; potem trzy duze glazy (warianty 13-15)
    small = [center32(crop(sheets[n], b)) for n in (1, 3, 2) for b in SMALL]
    for i, im in enumerate(small):
        im.save(os.path.join(art, "tiles", name("rock", i + 1) + ".png"))
    for i, (n, b) in enumerate([(1, BIG[0]), (3, BIG[1]), (2, BIG[2])]):
        k = 13 + i
        small[0].save(os.path.join(art, "tiles", name("rock", k) + ".png"))   # zapas, gdy brak natywnej grafiki
        big64(crop(sheets[n], b)).save(os.path.join(art, "native", name("rock", k) + ".png"))
    # snow_rock
    for i, b in enumerate(SMALL):
        center32(crop(sheets[4], b)).save(os.path.join(art, "tiles", name("snow_rock", i + 1) + ".png"))
    center32(crop(sheets[4], SMALL[0])).save(os.path.join(art, "tiles", "snow_rock_v5.png"))
    big64(crop(sheets[4], BIG[0])).save(os.path.join(art, "native", "snow_rock_v5.png"))

    # zloty smok: kolumny gora, prawo, dol, lewo; wiersze: krok1, postoj, krok2; komorka 64x64
    sh = Image.open(dragon).convert("RGBA")
    cells = {}
    boxes = []
    for r in range(3):
        for c in range(4):
            cell = sh.crop((c * 64, r * 64, c * 64 + 64, r * 64 + 64))
            cells[(r, c)] = cell
            boxes.append(cell.getbbox())
    # natywny rozmiar jak smok w Tibii: cala komorka 64x64 bez skalowania (renderer rysuje go na 2x2 pola od
    # prawego dolnego rogu pola potwora - TileArt.bigMonster)
    side_names = {0: "_up", 1: "_right", 2: "", 3: "_left"}
    frames = {0: "_walk1", 1: "", 2: "_walk2"}
    for (r, c), cell in cells.items():
        sm = cell.copy()
        # kolejnosc nazw jak w grze: dune_drake_up_walk1.png, dune_drake_walk1.png
        fn = "dune_drake" + side_names[c] + frames[r] + ".png"
        sm.save(os.path.join(art, "monsters", fn))
    recolour_dragons(assets)


# ----------------------------------------------------------------------------------------------- rod smokow
SIDES = ("", "_walk1", "_walk2", "_up", "_up_walk1", "_up_walk2", "_left", "_left_walk1", "_left_walk2",
         "_right", "_right_walk1", "_right_walk2")


def _eye(h, s, v):
    """Zielone oczy zlotego smoka (barwa 70-170 st.) - przy przebarwieniu dostaja wlasny kolor."""
    return 70 <= h <= 170 and s > 0.35 and v > 0.3


def _recolour_px(variant, r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h *= 360
    eye = _eye(h, s, v)
    if variant == "emerald":
        if eye:
            return colorsys.hsv_to_rgb(48 / 360, 0.95, min(1.0, v * 1.15))      # zolte slepia
        return colorsys.hsv_to_rgb(((h - 30) * 0.5 + 125) / 360 % 1, min(1.0, s * 0.78), v * 0.82)
    if variant == "hoarfrost":
        if eye:
            return colorsys.hsv_to_rgb(185 / 360, 0.25, 1.0)                    # jasne, lodowe slepia
        return colorsys.hsv_to_rgb(((h - 30) * 0.25 + 206) / 360 % 1, min(1.0, 0.1 + s * 0.75), min(1.0, 0.1 + v * 0.9))
    if variant == "coal":
        if eye:
            return colorsys.hsv_to_rgb(8 / 360, 0.95, 1.0)                      # rozzarzone slepia
        if s > 0.55 and v > 0.84:
            return colorsys.hsv_to_rgb(358 / 360, 0.85, min(1.0, v * 0.95))     # czerwone akcenty (grzbiet, krawedzie skrzydel)
        return colorsys.hsv_to_rgb(0.03, min(0.22, s * 0.25), v * 0.5)
    if variant == "pearl":
        if eye:
            return colorsys.hsv_to_rgb(355 / 360, 0.85, 0.95)                   # czerwone slepia na bialej luesce
        return colorsys.hsv_to_rgb(262 / 360, min(0.12, s * 0.12), min(1.0, 0.3 + v * 0.72))
    raise ValueError(variant)


def recolour_image(img, variant):
    out = img.convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            nr, ng, nb = _recolour_px(variant, r, g, b)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def tint_icon(img, hue, sat, val):
    """Ikona przedmiotu w barwie smoka: barwa i nasycenie zadane, jasnosc z oryginalu (x val)."""
    out = img.convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _h, _s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue / 360, sat, min(1.0, v * val))
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


DRAGONS = {"emerald": "emerald_dragon", "hoarfrost": "hoarfrost_dragon", "coal": "coal_dragon", "pearl": "pearl_dragon"}
# lupy: (plik zrodlowy ikony, plik docelowy, barwa, nasycenie, jasnosc)
DRAGON_ICONS = [
    ("wyvern_scale", "emerald_scale", 120, 0.7, 1.0), ("wyvern_scale", "frost_scale", 200, 0.45, 1.2),
    ("wyvern_scale", "coal_scale", 0, 0.1, 0.55), ("wyvern_scale", "gilded_scale", 44, 0.85, 1.25),
    ("wyvern_scale", "pearl_scale", 262, 0.1, 1.5),
    ("dragon_shield", "emeraldscale_shield", 120, 0.7, 1.0), ("dragon_shield", "hoarscale_shield", 200, 0.45, 1.2),
    ("dragon_shield", "pearl_aegis", 262, 0.1, 1.5), ("dragon_armor", "coalscale_armor", 0, 0.1, 0.55),
    ("dragon_helmet", "gilded_helmet", 44, 0.85, 1.25),
]


def recolour_dragons(assets):
    """Cztery kolory smoka z klatek zlotego (dune_drake*.png) i ikony ich lupu; zloty zostaje jak jest."""
    art = os.path.join(assets, "art")
    for variant, key in DRAGONS.items():
        for side in SIDES:
            src = Image.open(os.path.join(art, "monsters", "dune_drake" + side + ".png"))
            recolour_image(src, variant).save(os.path.join(art, "monsters", key + side + ".png"))
    for src, dst, hue, sat, val in DRAGON_ICONS:
        tint_icon(Image.open(os.path.join(art, "items", src + ".png")), hue, sat, val).save(os.path.join(art, "items", dst + ".png"))


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--recolour":
        recolour_dragons(sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets"))
    else:
        main()
