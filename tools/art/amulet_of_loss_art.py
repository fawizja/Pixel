"""Ikona Amuletu Straty (items/amulet_of_loss.png), rysowana proceduralnie (bez cudzej grafiki): zloty lancuszek,
czerwony kamien w oprawie i jasny krzyz. 32 x 32, ten sam rozmiar co inne amulety.
Uzycie: python3 tools/art/amulet_of_loss_art.py [katalog assets/art]"""
import math
import os
import sys

from PIL import Image

art = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'assets', 'art')
im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
px = im.load()
GOLD, GOLD_D, OUT = (232, 196, 70, 255), (168, 128, 40, 255), (40, 24, 16, 255)


def put(x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        px[x, y] = c


# lancuszek: luk od lewego do prawego ramienia
for i in range(0, 41):
    t = i / 40.0
    x = 4 + t * 24
    y = 3 + 14 * math.sin(math.pi * t) ** 1.6
    for c, dy in ((OUT, 1), (GOLD if i % 3 else GOLD_D, 0)):
        put(int(round(x)), int(round(y)) + dy, c)
# oprawa i kamien (kolo o srodku (16,21), promien 7)
for y in range(32):
    for x in range(32):
        d = math.hypot(x - 15.5, y - 21.5)
        if d <= 8.2:
            put(x, y, OUT)
        if d <= 7.2:
            put(x, y, GOLD_D if d > 6.0 else GOLD)
        if d <= 5.6:
            shade = 0.75 + 0.35 * (1 - d / 5.6) - 0.08 * ((x - 15.5) + (y - 21.5)) / 5.6
            put(x, y, (min(255, int(190 * shade)), int(38 * shade), int(30 * shade), 255))
# jasny krzyz na kamieniu
for k in range(-3, 4):
    put(16, 21 + k, (250, 232, 200, 255))
for k in range(-2, 3):
    put(16 + k, 20, (250, 232, 200, 255))
# polysk
put(13, 18, (255, 150, 130, 255))
put(14, 17, (255, 190, 170, 255))
os.makedirs(os.path.join(art, 'items'), exist_ok=True)
im.save(os.path.join(art, 'items', 'amulet_of_loss.png'))
print('amulet_of_loss.png')
