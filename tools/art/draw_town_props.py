#!/usr/bin/env python3
"""Rysuje wlasne grafiki rekwizytow miejskich, ktorych brakuje w OpenTibia Sprite Pack (licencja projektu).

    python3 tools/art/draw_town_props.py [katalog assets/art]

Styl jak w paczce OTSP: 32x32, swiatlo z lewej gory, ciemny kontur, przedmiot stoi przy dolnej krawedzi pola.
Kowadlo, palenisko kuzni, stragan, drogowskaz, bela siana, lodz, sieci rybackie, skrzynka z kwiatami.
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


def anvil():
    img = canvas()
    stump = (122, 82, 48)
    ellipse(img, 16, 26, 7, 3, shade(stump, 0.8))
    rect(img, 10, 20, 22, 26, stump)
    for x in (12, 15, 19):
        rect(img, x, 21, x, 26, shade(stump, 0.7))
    ellipse(img, 16, 20, 6, 2, shade(stump, 1.3))
    iron = (92, 96, 104)
    rect(img, 13, 15, 19, 19, shade(iron, 0.85))
    rect(img, 7, 11, 24, 14, iron)
    rect(img, 7, 11, 24, 11, shade(iron, 1.5))
    for x in range(3, 7):
        rect(img, x, 12, x, 13 - (6 - x) // 3, iron)
    px(img, 3, 12, shade(iron, 1.4))
    rect(img, 22, 9, 24, 10, shade(iron, 1.2))
    rect(img, 15, 12, 17, 13, shade(iron, 1.2))
    return outline(img)


def forge():
    img = canvas()
    stone = (118, 112, 106)
    rect(img, 4, 12, 27, 29, stone)
    for y in range(13, 29, 4):
        for x in range(4 + (y // 4 % 2) * 3, 28, 6):
            rect(img, x, y, x, y + 3, shade(stone, 0.7))
        rect(img, 4, y + 3, 27, y + 3, shade(stone, 0.75))
    rect(img, 4, 10, 27, 12, shade(stone, 1.3))
    rect(img, 8, 6, 23, 11, (40, 30, 26))
    for x in range(9, 23):
        h = 2 + (x * 7) % 4
        rect(img, x, 11 - h, x, 10, (236, 120, 40))
        px(img, x, 11 - h, (255, 214, 96))
    for x in (11, 15, 19):
        px(img, x, 5 - (x % 3), (255, 240, 160))
    rect(img, 11, 19, 20, 25, (40, 26, 20))
    rect(img, 12, 21, 19, 25, (210, 90, 30))
    rect(img, 13, 23, 18, 25, (255, 190, 70))
    return outline(img)


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


def signpost():
    img = canvas()
    wood = (132, 90, 52)
    rect(img, 15, 8, 16, 29, wood)
    rect(img, 15, 8, 15, 29, shade(wood, 1.25))
    ellipse(img, 16, 29, 4, 1.5, (90, 76, 52))
    board = (170, 122, 72)
    rect(img, 6, 9, 24, 12, board)
    for k in range(3):
        rect(img, 25 + k, 9 + k, 25 + k, 12 - k, board)
    rect(img, 6, 9, 24, 9, shade(board, 1.25))
    rect(img, 9, 15, 25, 18, shade(board, 0.9))
    for k in range(3):
        rect(img, 8 - k, 15 + k, 8 - k, 18 - k, shade(board, 0.9))
    for x in range(9, 23, 3):
        px(img, x, 11, (70, 48, 30))
        px(img, x + 2, 17, (70, 48, 30))
    return outline(img)


def hay_bale():
    img = canvas()
    hay = (214, 182, 84)
    ellipse(img, 16, 21, 12, 8, hay)
    ellipse(img, 13, 18, 7, 4, shade(hay, 1.18))
    for k in range(-10, 11, 3):
        for y in range(15, 28):
            if ((k + 0.5) / 12) ** 2 + ((y - 21) / 8) ** 2 <= 1 and (y + k) % 3 == 0:
                px(img, 16 + k, y, shade(hay, 0.78))
    rect(img, 10, 13, 11, 29, (150, 104, 52))
    rect(img, 21, 13, 22, 29, (150, 104, 52))
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


def fishing_net():
    img = canvas()
    pole = (120, 82, 48)
    rect(img, 4, 6, 5, 29, pole)
    rect(img, 26, 6, 27, 29, pole)
    rect(img, 4, 6, 27, 7, shade(pole, 1.2))
    for y in range(8, 26):
        sag = int(3 * (1 - abs(y - 17) / 9))
        for x in range(6, 26):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                px(img, x, y + (sag if 10 < x < 22 else 0) // 2, (196, 186, 150))
    for x in (9, 15, 21):
        ellipse(img, x, 27, 1.5, 1.2, (230, 220, 200))
    return outline(img)


def flower_box():
    img = canvas()
    wood = (134, 88, 50)
    wood_box(img, 3, 21, 28, 28, wood)
    rect(img, 4, 19, 27, 21, (88, 60, 36))
    blooms = [(222, 60, 80), (246, 210, 70), (240, 240, 240), (170, 90, 210), (222, 60, 80), (246, 150, 60)]
    for k, c in enumerate(blooms):
        x = 6 + k * 4
        rect(img, x, 14, x, 19, (70, 130, 50))
        px(img, x + 1, 16, (90, 160, 60))
        ellipse(img, x + 0.5, 13, 1.8, 1.6, c)
        px(img, x, 12, shade(c, 1.3))
    return outline(img)


PROPS = {
    "anvil": anvil, "forge": forge, "market_stall": market_stall, "signpost": signpost, "hay_bale": hay_bale,
    "rowboat": rowboat, "fishing_net": fishing_net, "flower_box": flower_box,
}


def main(art):
    os.makedirs(os.path.join(art, "tiles"), exist_ok=True)
    for name, fn in PROPS.items():
        fn().save(os.path.join(art, "tiles", name + ".png"), optimize=True)
    print("Zapisano", len(PROPS), "rekwizytow do", art)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else
         os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
