#!/usr/bin/env python3
"""Pochodnie i latarnie na murach (zapalone i zgaszone) z OpenTibia Sprite Pack (CC BY 4.0).

Zapisuje assets/art/native/wall_light_<torch|lantern>_<h|v>_<lit|off>.png: plotno 2x2 pola jak mury (prawy dolny
kwadrat to pole muru). h = mur poziomy (sciana widoczna od poludnia), v = mur pionowy (od wschodu).
Pochodnia: plomien z paczki (1253 / 1254) na drewnianym trzonku w zelaznym uchwycie; zgaszona - zweglona glowka.
Latarnia: lampa scienna z paczki (1240 / 1239); zgaszona - ciemne szybki. Paczka rysuje lampe na polu przed murem,
wiec renderer przesuwa latarnie o pole w strone podlogi (WorldRenderer.drawWallLight).
Uzycie: python3 tools/art/wall_lights_import.py <katalog client_files paczki> [katalog assets/art]
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import otsp_extract as ot  # noqa: E402

FLAME = {"h": 1253, "v": 1254}
LANTERN = {"h": 1240, "v": 1239}
WOOD, WOOD_DARK, WOOD_LIGHT = (122, 78, 40, 255), (78, 48, 24, 255), (160, 108, 58, 255)
OUTLINE = (30, 20, 12, 255)
IRON, IRON_DARK = (120, 120, 128, 255), (60, 60, 66, 255)
CHAR, EMBER = (40, 32, 28, 255), (70, 50, 40, 255)


def canvas(spr, things, i):
    im = ot.compose(spr, things["item"][i]).convert("RGBA")
    c = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    c.alpha_composite(im, (64 - im.width, 64 - im.height))
    return c


def torch(spr, things, side, lit):
    flame = canvas(spr, things, FLAME[side])
    x0, y0, x1, y1 = flame.getbbox()
    bx, by = x1 - 3, y1 - 3          # podstawa plomienia (prawy dolny koniec, plomien idzie w gore i w lewo)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = out.load()

    def dot(x, y, col):
        if 0 <= x < 64 and 0 <= y < 64:
            px[x, y] = col

    # trzonek wzdluz osi plomienia (skos w dol i w prawo), 3 px grubosci z ciemnym brzegiem
    for k in range(0, 11):
        x, y = bx + k, by + k
        dot(x - 1, y, OUTLINE)
        dot(x, y - 1, OUTLINE)
        dot(x, y, WOOD_LIGHT)
        dot(x + 1, y, WOOD)
        dot(x, y + 1, WOOD)
        dot(x + 1, y + 1, WOOD_DARK)
        dot(x + 2, y + 1, OUTLINE)
        dot(x + 1, y + 2, OUTLINE)
    # zelazna obrecz w poprzek trzonka i plytka przy murze
    for t in range(-3, 4):
        cx, cy = bx + 6 + t, by + 6 - t
        dot(cx, cy, IRON if t < 1 else IRON_DARK)
        dot(cx + 1, cy, IRON_DARK)
    for t in range(-2, 3):
        dot(bx + 9 + t, by + 9 - t, IRON_DARK)
    # owinieta glowka pochodni (pod plomieniem albo zweglona)
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 3:
                if lit:
                    col = (150, 96, 40, 255) if (dx + dy) % 2 else (110, 70, 30, 255)
                else:
                    col = CHAR if (dx * 3 + dy) % 3 else EMBER
                dot(bx + dx, by + dy, col)
    if lit:
        out.alpha_composite(flame)
    else:
        dot(bx - 1, by - 1, (90, 40, 20, 255))   # ostatni zar
    return out


def lantern(spr, things, side, lit):
    img = canvas(spr, things, LANTERN[side])
    if lit:
        return img
    px = img.load()
    for y in range(64):
        for x in range(64):
            r, g, b, a = px[x, y]
            if a and r > 120 and g > 70 and r - b > 50:   # zolte szybki i plomyk
                lum = (r + g + b) // 3
                v = 40 + lum // 5
                px[x, y] = (v, v, v + 12, a)
    return img


def main(client, art):
    things = ot.load_dat(os.path.join(client, "otsp.dat"))
    spr = ot.Spr(os.path.join(client, "otsp.spr"))
    out = os.path.join(art, "native")
    os.makedirs(out, exist_ok=True)
    n = 0
    for side in ("h", "v"):
        for lit in (True, False):
            state = "lit" if lit else "off"
            torch(spr, things, side, lit).save(os.path.join(out, f"wall_light_torch_{side}_{state}.png"))
            lantern(spr, things, side, lit).save(os.path.join(out, f"wall_light_lantern_{side}_{state}.png"))
            n += 2
    print(n, "grafik pochodni i latarni na murach")


if __name__ == "__main__":
    here = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "assets", "art"))
