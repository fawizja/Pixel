#!/usr/bin/env python3
"""Rysuje wlasne grafiki rekwizytow miejskich, ktorych brakuje w OpenTibia Sprite Pack (licencja projektu).

    python3 tools/art/draw_town_props.py [katalog assets/art]

Styl jak w paczce OTSP: 32x32, swiatlo z lewej gory, ciemny kontur, przedmiot stoi przy dolnej krawedzi pola.
Zostaly tylko rzeczy, ktorych paczka nie ma: stragan, lodz, ster statku, namiot orkow, wiatrak.
Reszta (kowadlo, tron, maszt, swiecznik...) jest od 2026-10-23 brana z paczki: tools/art/otsp_import.py.
"""
import os
import sys

from PIL import Image

OUTLINE = (34, 24, 18, 255)


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def px(img, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def ellipse(img, cx, cy, rx, ry, c):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                px(img, x, y, c)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def outline(img):
    """Kontur 1 px wokol nieprzezroczystych pikseli (jak w paczce OTSP)."""
    src = img.copy()
    for y in range(32):
        for x in range(32):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 32 and 0 <= ny < 32 and src.getpixel((nx, ny))[3] and src.getpixel((nx, ny)) != OUTLINE:
                    img.putpixel((x, y), OUTLINE)
                    break
    return img


def wood_box(img, x0, y0, x1, y1, base):
    """Skrzynia z desek: jasniejszy wierzch, ciemniejszy przod, slojowanie."""
    top = shade(base, 1.25)
    rect(img, x0, y0, x1, y1, base)
    rect(img, x0, y0, x1, y0 + 2, top)
    for x in range(x0 + 3, x1, 5):
        for y in range(y0 + 3, y1 + 1):
            px(img, x, y, shade(base, 0.75))


def market_stall():
    img = canvas()
    wood = (140, 96, 56)
    rect(img, 4, 9, 5, 29, shade(wood, 0.8))
    rect(img, 26, 9, 27, 29, shade(wood, 0.8))
    wood_box(img, 3, 20, 28, 28, wood)
    goods = [(214, 58, 48), (232, 176, 52), (110, 170, 60), (214, 58, 48), (180, 110, 200), (232, 176, 52)]
    for k, c in enumerate(goods):
        ellipse(img, 7 + k * 3.6, 19, 1.6, 1.4, c)
        px(img, int(6 + k * 3.6), 18, shade(c, 1.4))
    for x in range(2, 30):
        stripe = (x // 4) % 2
        c = (200, 52, 44) if stripe else (240, 232, 214)
        rect(img, x, 3, x, 9, c)
        px(img, x, 3, shade(c, 1.2))
        if x % 4 == 1:
            px(img, x, 10, c)
    rect(img, 2, 9, 29, 9, (150, 40, 34))
    return outline(img)


def rowboat():
    img = canvas()
    wood = (128, 84, 46)
    for y in range(8, 27):
        t = (y - 8) / 18
        half = int(5 + 7 * (1 - abs(t - 0.5) * 2) ** 0.6)
        rect(img, 16 - half, y, 15 + half, y, wood)
        px(img, 16 - half, y, shade(wood, 1.3))
        px(img, 15 + half, y, shade(wood, 0.7))
    for y in range(10, 25):
        t = (y - 8) / 18
        half = int(3 + 6 * (1 - abs(t - 0.5) * 2) ** 0.6)
        rect(img, 17 - half, y, 14 + half, y, (88, 56, 32))
    for y in (13, 20):
        rect(img, 8, y, 23, y + 1, shade(wood, 1.15))
    rect(img, 5, 16, 26, 16, (200, 170, 120))
    return outline(img)


PROPS = {
    "market_stall": market_stall, "rowboat": rowboat,
}


# ------------------------------------------------------------------ 2026-10-23: kontynent, swiatynie, statki, orki

def ship_wheel():
    img = canvas()
    wood = (122, 80, 44)
    for k in range(8):
        import math
        a = k * math.pi / 4
        for r in range(2, 13):
            px(img, int(16 + math.cos(a) * r), int(15 + math.sin(a) * r), wood)
        ellipse(img, 16 + math.cos(a) * 13, 15 + math.sin(a) * 13, 1.3, 1.3, shade(wood, 1.2))
    for k in range(64):
        a = k * math.pi / 32
        px(img, int(16.5 + math.cos(a) * 9), int(15.5 + math.sin(a) * 9), shade(wood, 1.15))
    ellipse(img, 16, 15, 2.5, 2.5, (190, 150, 70))
    rect(img, 14, 27, 18, 30, shade(wood, 0.8))
    return outline(img)


def orc_tent():
    """Namiot orkow z wyprawionych skor, z kosciami na szczycie."""
    img = canvas()
    hide = (138, 112, 66)
    for y in range(6, 29):
        half = int((y - 6) * 0.62) + 1
        rect(img, 16 - half, y, 15 + half, y, shade(hide, 0.9 + 0.2 * ((y // 4) % 2)))
    for y in range(16, 29):
        half = int((y - 16) * 0.35)
        rect(img, 16 - half, y, 15 + half, y, (40, 28, 20))
    for y in range(6, 29, 5):
        px(img, 16 - int((y - 6) * 0.62) - 1, y, (90, 60, 40))
        px(img, 16 + int((y - 6) * 0.62), y, (90, 60, 40))
    rect(img, 15, 2, 16, 6, (110, 80, 50))
    rect(img, 12, 3, 19, 3, (230, 222, 200))
    px(img, 12, 2, (230, 222, 200))
    px(img, 19, 2, (230, 222, 200))
    return outline(img)


def windmill():
    """Wiatrak 64x64 (rysowany wiekszy niz pole): kamienna wieza, dach i cztery skrzydla."""
    import math
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    stone = (214, 204, 182)
    for y in range(26, 64):
        half = 9 + (y - 26) // 6
        for x in range(32 - half, 32 + half):
            f = 1.15 if x < 32 - half + 3 else 0.8 if x > 32 + half - 4 else 1.0
            if (y % 6 == 0) or ((x + (y // 6) * 3) % 8 == 0):
                f *= 0.85
            img.putpixel((x, y), shade(stone, f))
    for y in range(52, 64):
        for x in range(29, 35):
            img.putpixel((x, y), (70, 48, 30, 255))
    for y in range(36, 41):
        for x in range(36, 40):
            img.putpixel((x, y), (60, 70, 90, 255))
    roof = (150, 60, 40)
    for y in range(14, 27):
        half = (y - 14) + 2
        for x in range(32 - half, 32 + half):
            img.putpixel((x, y), shade(roof, 1.15 if x < 32 else 0.85))
    hub = (32, 22)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        for r in range(2, 27):
            cx, cy = hub[0] + math.cos(a) * r, hub[1] + math.sin(a) * r
            for w in (-1, 0, 1):
                x = int(cx - math.sin(a) * w)
                y = int(cy + math.cos(a) * w)
                if 0 <= x < 64 and 0 <= y < 64:
                    img.putpixel((x, y), (92, 64, 38, 255))
            if r > 6:
                for w in range(2, 6):
                    x = int(cx - math.sin(a) * w)
                    y = int(cy + math.cos(a) * w)
                    if 0 <= x < 64 and 0 <= y < 64:
                        img.putpixel((x, y), (232, 226, 210, 255) if (r + w) % 3 else (180, 160, 120, 255))
    for y in range(20, 25):
        for x in range(30, 35):
            img.putpixel((x, y), (60, 40, 24, 255))
    return img


PROPS.update({"ship_wheel": ship_wheel, "orc_tent": orc_tent, "windmill": windmill})


def main(art):
    os.makedirs(os.path.join(art, "tiles"), exist_ok=True)
    for name, fn in PROPS.items():
        fn().save(os.path.join(art, "tiles", name + ".png"), optimize=True)
    print("Zapisano", len(PROPS), "rekwizytow do", art)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else
         os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
