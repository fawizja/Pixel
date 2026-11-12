#!/usr/bin/env python3
"""Grafiki od wlasciciela (2026-11-12, licencja nieznana, dodane na jego odpowiedzialnosc; docs/CREDITS.md):
kamienie z arkuszy "Aseprites" (07f940a2-Aseprites.rar, 4 arkusze 256x192) i zloty smok (4c56d725-...webp, 256x192).

    python3 -I tools/art/darekart_import.py ASEPRITES_DIR DRAGON_WEBP [katalog assets]

ASEPRITES_DIR: katalog z 1.png..4.png (1 szary gladki, 2 mchem porosniety, 3 ciemny popekany, 4 sniezny).
Zrodla leza poza repo; do assets/ trafiaja tylko wyciete kawalki:
- tiles/rock*.png (warianty `rock`: 12 malych kamieni 32x32 z arkuszy 1-3, bez skalowania) oraz native/rock_v13..v16.png
  (duze glazy 64x64, rysowane 2x2 pola jak krzaki) - zastepuja kamienie OTSP;
- tiles/snow_rock*.png (4 male z arkusza 4) i native/snow_rock_v5.png (duzy glaz);
- monsters/dune_drake*.png: zloty smok (cztery kierunki x trzy klatki; srodkowy wiersz = postoj) w 41x41.
Nieuzyte: chmury (Nubes: rozmyte, nie pikselart), kamienie jak arkusz 4 do `sandstone_rock`/`obsidian_rock` (brak piaskowych).
"""
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
    x0 = min(b[0] for b in boxes); y0 = min(b[1] for b in boxes)
    x1 = max(b[2] for b in boxes); y1 = max(b[3] for b in boxes)
    side = max(x1 - x0, y1 - y0)
    side_names = {0: "_up", 1: "_right", 2: "", 3: "_left"}
    frames = {0: "_walk1", 1: "", 2: "_walk2"}
    for (r, c), cell in cells.items():
        sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        sq.paste(cell.crop((x0, y0, x1, y1)), ((side - (x1 - x0)) // 2, (side - (y1 - y0)) // 2))
        # skalowanie z usrednianiem, potem twarde krawedzie alfa (bez rozmytego obrysu)
        sm = sq.resize((41, 41), Image.LANCZOS)
        px = sm.load()
        for y in range(41):
            for x in range(41):
                p = px[x, y]
                px[x, y] = (p[0], p[1], p[2], 255) if p[3] >= 128 else (0, 0, 0, 0)
        # kolejnosc nazw jak w grze: dune_drake_up_walk1.png, dune_drake_walk1.png
        fn = "dune_drake" + side_names[c] + frames[r] + ".png"
        sm.save(os.path.join(art, "monsters", fn))


if __name__ == "__main__":
    main()
