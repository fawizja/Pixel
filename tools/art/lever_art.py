#!/usr/bin/env python3
"""Dzwignia (kafel lever) rysowana kodem - bez cudzych grafik.

Zapisuje assets/art/native/lever.png: plotno 64x64 jak inne obiekty native (prawy dolny kwadrat 32x32 to pole
dzwigni). Kamienny cokol z zelazna szczelina, drewniany drazek przechylony w lewo i czerwona galka.
Uzycie: python3 tools/art/lever_art.py [katalog assets/art]
"""
import os
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                                                         'assets', 'art')
OUTLINE = (24, 20, 18, 255)
STONE, STONE_LIGHT, STONE_DARK = (128, 122, 112, 255), (168, 160, 146, 255), (84, 78, 70, 255)
IRON, IRON_DARK = (70, 72, 78, 255), (40, 40, 46, 255)
WOOD, WOOD_LIGHT, WOOD_DARK = (126, 82, 42, 255), (170, 116, 62, 255), (82, 52, 26, 255)
KNOB, KNOB_LIGHT, KNOB_DARK = (184, 36, 30, 255), (236, 96, 80, 255), (110, 18, 16, 255)


def main():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # cien pod cokolem
    d.ellipse((34, 54, 62, 62), fill=(0, 0, 0, 70))
    # cokol: bryla z jasnym wierzchem
    d.rectangle((37, 44, 59, 58), fill=STONE, outline=OUTLINE)
    d.rectangle((38, 45, 58, 47), fill=STONE_LIGHT)
    d.rectangle((38, 55, 58, 57), fill=STONE_DARK)
    for (x, y) in ((41, 50), (52, 52), (47, 49)):
        d.point((x, y), fill=STONE_DARK)
    # szczelina
    d.rectangle((44, 45, 52, 47), fill=IRON_DARK)
    # drazek od szczeliny w gore i w lewo
    x0, y0, x1, y1 = 48, 46, 38, 26
    for t in range(0, 41):
        x = x0 + (x1 - x0) * t / 40
        y = y0 + (y1 - y0) * t / 40
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=WOOD_DARK)
    for t in range(0, 41):
        x = x0 + (x1 - x0) * t / 40
        y = y0 + (y1 - y0) * t / 40
        d.point((round(x), round(y)), fill=WOOD_LIGHT)
        d.point((round(x) + 1, round(y)), fill=WOOD)
    # zelazna obejma u podstawy
    d.rectangle((45, 42, 51, 45), fill=IRON, outline=OUTLINE)
    # galka
    d.ellipse((32, 20, 43, 31), fill=KNOB, outline=OUTLINE)
    d.ellipse((34, 22, 38, 26), fill=KNOB_LIGHT)
    d.arc((33, 21, 42, 30), 20, 120, fill=KNOB_DARK)
    path = os.path.join(OUT, 'native', 'lever.png')
    im.save(path)
    print('zapisano', path)


if __name__ == '__main__':
    main()
