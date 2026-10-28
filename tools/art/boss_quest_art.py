#!/usr/bin/env python3
"""Pola wyprawy na bossa (boss_plate) i portal wyjscia z areny (boss_portal) rysowane kodem - bez cudzych grafik.

Zapisuje assets/art/native/boss_plate.png i boss_portal.png: plotno 64x64 jak inne obiekty native (prawy dolny
kwadrat 32x32 to pole). Plyta: kamienny kwadrat ze zlota obwodka i swiecaca runa. Portal: fioletowy krag
z jasnym srodkiem i iskrami.
Uzycie: python3 tools/art/boss_quest_art.py [katalog assets/art]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                                                         'assets', 'art')
OUTLINE = (30, 24, 16, 255)
STONE, STONE_LIGHT, STONE_DARK = (120, 112, 100, 255), (160, 150, 132, 255), (78, 72, 64, 255)
GOLD, GOLD_LIGHT, GOLD_DARK = (214, 168, 52, 255), (250, 214, 110, 255), (130, 96, 26, 255)
RUNE, RUNE_GLOW = (255, 226, 120, 255), (255, 200, 80, 110)


def plate():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # plyta zajmuje prawie cale pole (32..63), lekko wpuszczona w podloge
    d.rectangle((34, 34, 61, 61), fill=STONE_DARK)
    d.rectangle((35, 35, 60, 60), fill=STONE, outline=GOLD_DARK)
    d.rectangle((36, 36, 59, 59), outline=GOLD)
    d.line((37, 37, 58, 37), fill=GOLD_LIGHT)
    d.line((37, 37, 37, 58), fill=GOLD_LIGHT)
    d.rectangle((38, 38, 57, 57), fill=STONE_LIGHT)
    d.rectangle((39, 39, 57, 57), fill=STONE)
    # zlote narozniki
    for (x, y) in ((36, 36), (57, 36), (36, 57), (57, 57)):
        d.rectangle((x, y, x + 2, y + 2), fill=GOLD_LIGHT, outline=GOLD_DARK)
    # runa: poswiata i znak (romb z kreska)
    cx, cy = 48, 48
    d.ellipse((cx - 8, cy - 8, cx + 8, cy + 8), fill=RUNE_GLOW)
    d.polygon([(cx, cy - 7), (cx + 6, cy), (cx, cy + 7), (cx - 6, cy)], outline=RUNE)
    d.polygon([(cx, cy - 6), (cx + 5, cy), (cx, cy + 6), (cx - 5, cy)], outline=GOLD)
    d.line((cx, cy - 4, cx, cy + 4), fill=RUNE)
    d.line((cx - 3, cy, cx + 3, cy), fill=RUNE)
    return im


def portal():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy = 48, 50
    # cien i krag kamieni
    d.ellipse((cx - 15, cy - 9, cx + 15, cy + 11), fill=(0, 0, 0, 70))
    d.ellipse((cx - 14, cy - 10, cx + 14, cy + 10), fill=(70, 40, 120, 255), outline=OUTLINE)
    for k in range(5, 0, -1):
        f = k / 5.0
        col = (int(150 + 100 * (1 - f)), int(90 + 150 * (1 - f)), 255, 255)
        d.ellipse((cx - 12 * f, cy - 8 * f, cx + 12 * f, cy + 8 * f), fill=col)
    # wir
    for t in range(0, 60):
        a = t * 0.35
        r = 1 + t * 0.18
        x, y = cx + r * math.cos(a), cy + r * 0.66 * math.sin(a)
        d.point((round(x), round(y)), fill=(255, 255, 255, 220))
    # iskry nad portalem
    for (x, y) in ((40, 34), (52, 30), (47, 26), (57, 37), (37, 41)):
        d.line((x - 1, y, x + 1, y), fill=(230, 210, 255, 255))
        d.line((x, y - 1, x, y + 1), fill=(230, 210, 255, 255))
    return im


def main():
    for name, im in (('boss_plate', plate()), ('boss_portal', portal())):
        path = os.path.join(OUT, 'native', name + '.png')
        im.save(path)
        print('zapisano', path)


if __name__ == '__main__':
    main()
