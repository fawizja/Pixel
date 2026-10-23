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


# ------------------------------------------------------------------ 2026-10-23: kontynent, swiatynie, statki, orki

def candle():
    """Swiecznik z trzema swiecami (swiatynie, sala tronowa)."""
    img = canvas()
    brass = (196, 156, 60)
    ellipse(img, 16, 28, 6, 2, shade(brass, 0.8))
    rect(img, 15, 18, 16, 27, brass)
    rect(img, 9, 18, 22, 19, brass)
    for x in (9, 15, 21):
        rect(img, x, 10, x + 1, 17, (240, 232, 210))
        px(img, x, 10, (255, 255, 255))
        ellipse(img, x + 1, 7.5, 1.4, 2.4, (255, 190, 60))
        px(img, x, 7, (255, 245, 180))
    return outline(img)


def carpet():
    """Czerwony chodnik ze zlota lamowka - pelne pole, laczy sie z sasiednimi."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    red = (150, 30, 42)
    rect(img, 3, 0, 28, 31, red)
    rect(img, 3, 0, 4, 31, (210, 170, 70))
    rect(img, 27, 0, 28, 31, (210, 170, 70))
    for y in range(0, 32, 8):
        for d in range(3):
            px(img, 16 - d, y + 2 + d, (210, 170, 70))
            px(img, 15 + d, y + 2 + d, (210, 170, 70))
            px(img, 16 - d, y + 6 - d, (210, 170, 70))
            px(img, 15 + d, y + 6 - d, (210, 170, 70))
    for y in range(32):
        for x in range(5, 27):
            if (x * 7 + y * 3) % 11 == 0:
                px(img, x, y, shade(red, 0.85))
    return img


def throne():
    img = canvas()
    gold = (214, 170, 60)
    velvet = (140, 30, 50)
    rect(img, 8, 3, 23, 22, gold)
    rect(img, 10, 5, 21, 20, velvet)
    for x in (8, 15, 23):
        ellipse(img, x, 3, 1.6, 1.6, (250, 220, 110))
    rect(img, 6, 18, 25, 23, gold)
    rect(img, 8, 19, 23, 22, shade(velvet, 1.15))
    rect(img, 6, 24, 8, 29, shade(gold, 0.8))
    rect(img, 23, 24, 25, 29, shade(gold, 0.8))
    rect(img, 9, 24, 22, 25, shade(gold, 0.7))
    px(img, 15, 10, (90, 190, 230))
    return outline(img)


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


def ship_mast():
    """Maszt ze zwinietym zaglem i reja (64x64, rysowany wyzej jak drzewo)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    wood = (112, 74, 40)
    for y in range(4, 62):
        for x in range(30, 34):
            img.putpixel((x, y), shade(wood, 1.25 if x == 30 else 1.0 if x < 33 else 0.75))
    for y in (14, 30):
        for x in range(10, 54):
            img.putpixel((x, y), shade(wood, 1.1))
            img.putpixel((x, y + 1), shade(wood, 0.8))
        for x in range(12, 52):
            for yy in range(y + 2, y + 5):
                if (x + yy) % 5:
                    img.putpixel((x, yy), (226, 214, 186, 255))
                else:
                    img.putpixel((x, yy), (196, 182, 150, 255))
    for x in range(26, 38):
        img.putpixel((x, 4), (60, 40, 24, 255))
    for x in range(34, 44):
        for y in range(5, 10):
            img.putpixel((x, y), (170, 40, 40, 255) if y < 8 else (220, 200, 90, 255))
    return img


def ship_deck():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    base = (150, 104, 58)
    for y in range(32):
        board = y // 6
        c = shade(base, 0.92 + 0.08 * (board % 3))
        for x in range(32):
            img.putpixel((x, y), c)
        if y % 6 == 5:
            for x in range(32):
                img.putpixel((x, y), shade(base, 0.6))
    for board in range(6):
        x = (board * 11) % 32
        for y in range(board * 6, board * 6 + 5):
            if y < 32:
                img.putpixel((x, y), shade(base, 0.65))
    return img


def rope_bridge():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    plank = (140, 100, 56)
    for x in range(0, 32, 5):
        rect(img, x, 6, x + 3, 25, plank)
        px(img, x, 6, shade(plank, 1.3))
        rect(img, x + 3, 6, x + 3, 25, shade(plank, 0.7))
    rect(img, 0, 4, 31, 5, (196, 170, 112))
    rect(img, 0, 26, 31, 27, (196, 170, 112))
    for x in range(1, 32, 5):
        px(img, x, 3, (170, 140, 90))
        px(img, x, 28, (170, 140, 90))
    return img


def chasm():
    img = Image.new("RGBA", (32, 32), (16, 13, 12, 255))
    for y in range(32):
        for x in range(32):
            v = 14 + ((x * 13 + y * 7) % 9) - y // 6
            img.putpixel((x, y), (max(4, v), max(3, v - 2), max(3, v - 3), 255))
    for x in range(32):
        for y in range(0, 4 - (x % 3 == 0)):
            img.putpixel((x, y), (70, 64, 58, 255))
    return img


def broken_wall():
    img = canvas()
    stone = (118, 112, 104)
    heights = [14, 18, 9, 20, 12, 7, 16]
    for k, h in enumerate(heights):
        x0 = 2 + k * 4
        rect(img, x0, 28 - h, x0 + 3, 28, shade(stone, 1.0 + 0.08 * (k % 2)))
        px(img, x0, 28 - h, shade(stone, 1.3))
    for y in range(12, 29, 4):
        rect(img, 2, y, 29, y, shade(stone, 0.7))
    for (x, y) in ((5, 29), (13, 30), (22, 29), (27, 30)):
        ellipse(img, x, y, 2, 1.2, shade(stone, 0.9))
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


PROPS.update({
    "candle": candle, "carpet": carpet, "throne": throne, "ship_wheel": ship_wheel, "ship_mast": ship_mast,
    "ship_deck": ship_deck, "rope_bridge": rope_bridge, "chasm": chasm, "broken_wall": broken_wall,
    "orc_tent": orc_tent, "windmill": windmill,
})


def main(art):
    os.makedirs(os.path.join(art, "tiles"), exist_ok=True)
    for name, fn in PROPS.items():
        fn().save(os.path.join(art, "tiles", name + ".png"), optimize=True)
    print("Zapisano", len(PROPS), "rekwizytow do", art)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else
         os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
