#!/usr/bin/env python3
"""Skrzynka pocztowa (kafel mailbox) i czapka listonosza (przedmiot postman_cap) rysowane kodem - bez cudzych grafik.

OTSP nie ma skrzynki pocztowej, wiec obiekt jest rysowany tak jak lever_art.py: plotno 64x64 jak inne obiekty native
(prawy dolny kwadrat 32x32 to pole skrzynki): drewniany slupek, niebieska skrzynka z lukowym wiekiem, czerwona flaga
i biala koperta na froncie. Czapka to przedmiot 32x32: niebieski otok z daszkiem i zlotym rogiem poczty.
Uzycie: python3 tools/art/postman_art.py [katalog assets/art]
"""
import os
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                                                         'assets', 'art')
OL = (20, 22, 34, 255)
BLUE, BLUE_L, BLUE_D = (54, 98, 168, 255), (96, 142, 214, 255), (30, 56, 112, 255)
WOOD, WOOD_L, WOOD_D = (126, 82, 42, 255), (170, 116, 62, 255), (82, 52, 26, 255)
RED, RED_L = (200, 40, 34, 255), (240, 98, 84, 255)
PAPER, PAPER_D = (244, 238, 220, 255), (190, 180, 150, 255)
GOLD, GOLD_D = (240, 200, 60, 255), (170, 130, 30, 255)


def mailbox():
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((34, 55, 62, 63), fill=(0, 0, 0, 70))
    # slupek
    d.rectangle((46, 38, 51, 60), fill=WOOD, outline=OL)
    d.rectangle((47, 39, 48, 59), fill=WOOD_L)
    d.rectangle((42, 56, 55, 60), fill=WOOD_D, outline=OL)
    # skrzynka: prostokat z lukowym wiekiem
    d.rectangle((36, 28, 61, 42), fill=BLUE, outline=OL)
    d.pieslice((36, 20, 61, 36), 180, 360, fill=BLUE, outline=OL)
    d.rectangle((37, 28, 60, 29), fill=BLUE)
    d.arc((38, 22, 59, 35), 200, 320, fill=BLUE_L)
    d.line((38, 40, 59, 40), fill=BLUE_D)
    d.line((37, 41, 60, 41), fill=BLUE_D)
    # koperta na froncie
    d.rectangle((42, 31, 55, 38), fill=PAPER, outline=OL)
    d.line((42, 31, 48, 35), fill=PAPER_D)
    d.line((55, 31, 49, 35), fill=PAPER_D)
    # szczelina na listy
    d.rectangle((41, 28, 56, 29), fill=OL)
    # flaga
    d.rectangle((60, 14, 62, 33), fill=WOOD_D, outline=OL)
    d.rectangle((52, 14, 61, 20), fill=RED, outline=OL)
    d.rectangle((53, 15, 59, 16), fill=RED_L)
    # galka drzwiczek
    d.ellipse((57, 34, 59, 36), fill=GOLD, outline=OL)
    return im


def cap():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((5, 24, 27, 29), fill=(0, 0, 0, 60))
    # kopula
    d.pieslice((6, 8, 26, 26), 180, 360, fill=BLUE, outline=OL)
    d.rectangle((6, 17, 26, 22), fill=BLUE, outline=OL)
    d.rectangle((7, 18, 25, 18), fill=BLUE_L)
    # otok
    d.rectangle((5, 20, 27, 23), fill=BLUE_D, outline=OL)
    # daszek
    d.pieslice((4, 19, 28, 31), 0, 180, fill=BLUE_D, outline=OL)
    d.rectangle((6, 21, 26, 23), fill=BLUE_D)
    # zloty rog poczty
    d.ellipse((13, 11, 19, 17), fill=GOLD, outline=GOLD_D)
    d.rectangle((15, 13, 17, 15), fill=BLUE)
    return im


def bag():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((5, 25, 27, 30), fill=(0, 0, 0, 60))
    d.rounded_rectangle((6, 11, 26, 26), 3, fill=WOOD, outline=OL)
    d.rectangle((7, 12, 25, 14), fill=WOOD_L)
    d.rectangle((6, 18, 26, 19), fill=WOOD_D)
    d.rectangle((14, 16, 18, 22), fill=GOLD, outline=GOLD_D)
    d.arc((9, 4, 23, 16), 180, 360, fill=OL, width=2)
    d.polygon([(19, 7), (25, 7), (25, 12), (19, 12)], fill=PAPER, outline=OL)
    return im


def letters():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((4, 24, 28, 30), fill=(40, 70, 110, 90))
    for i, (x, y) in enumerate(((5, 12), (9, 8), (12, 14))):
        d.rectangle((x, y, x + 15, y + 10), fill=PAPER_D if i != 2 else PAPER, outline=OL)
        d.line((x, y, x + 7, y + 6), fill=OL)
        d.line((x + 15, y, x + 8, y + 6), fill=OL)
    d.ellipse((8, 18, 13, 22), fill=(70, 110, 160, 160))
    d.ellipse((20, 16, 25, 20), fill=(70, 110, 160, 160))
    return im


def main():
    for nm, fn in (('lost_mailbag', bag), ('sodden_letters', letters)):
        path = os.path.join(OUT, 'items', nm + '.png')
        fn().save(path)
        print('zapisano', path)
    path = os.path.join(OUT, 'native', 'mailbox.png')
    mailbox().save(path)
    print('zapisano', path)
    path = os.path.join(OUT, 'items', 'postman_cap.png')
    cap().save(path)
    print('zapisano', path)


if __name__ == '__main__':
    main()
