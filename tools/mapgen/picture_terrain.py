#!/usr/bin/env python3
"""Mapa terenu swiata 1000x1000 z obrazka mapy Darka (2026-10-27, "zaprojektuj mape jak na zdjeciu").

    python3 tools/mapgen/picture_terrain.py <obrazek mapy.png>

Kazde pole swiata dostaje klase terenu wg koloru obrazka (rozmytego, zeby pojedyncze drzewka i szczyty nie
dawaly szumu): W woda, S piasek pustyni, N snieg, L lawa, V czarne szczyty wulkanu, R skaly i gory, G trawa,
J gesty las (dzungla). Legenda, tabela rejsow, roza wiatrow, skala i napisy regionow sa zakryte i wypelnione
klasami z sasiedztwa. Wynik: tools/mapgen/data/world1k.txt.gz (1000 wierszy po 1000 znakow) - generator swiata
czyta tylko ten plik, sam obrazek nie jest potrzebny.
"""
import gzip
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'data', 'world1k.txt.gz')
S = 1000
# zakryte fragmenty obrazka (x0, y0, x1, y1 w pikselach obrazka 1536x1024): ramki legendy i tabel, napisy
OVERLAYS = [(0, 0, 1536, 16), (0, 1006, 1536, 1024), (0, 0, 16, 1024), (1500, 0, 1536, 1024),
            (8, 8, 204, 298), (6, 778, 514, 1018), (1130, 948, 1530, 1020), (1392, 8, 1522, 152)]
LABELS = [(488, 134, 634, 182), (928, 64, 1078, 98), (804, 186, 934, 230), (191, 341, 339, 389),
          (300, 484, 376, 518), (738, 503, 828, 535), (1094, 290, 1171, 322), (1001, 533, 1124, 577),
          (1279, 533, 1464, 561), (358, 664, 504, 696), (718, 765, 794, 797), (1288, 738, 1362, 770)]
VOLCANO_BOX = (330, 20, 720, 300)
SNOW_BOX = (740, 0, 1400, 210)


def classify(path):
    im = Image.open(path).convert('RGB')
    assert im.size == (1536, 1024), im.size
    blur = im.filter(ImageFilter.BoxBlur(5))
    a = np.asarray(blur).astype(float) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    sat, val = (mx - mn) / (mx + 1e-6), mx
    hue = np.asarray(blur.convert('HSV')).astype(float)[..., 0] / 255 * 360
    h, w = r.shape
    ys, xs = np.mgrid[0:h, 0:w]

    def box(bx):
        return (xs >= bx[0]) & (xs < bx[2]) & (ys >= bx[1]) & (ys < bx[3])
    cls = np.full((h, w), 'G')
    water = (b > r + 0.12) & (b > g - 0.02) & (b > 0.3)
    snow = (val > 0.68) & (sat < 0.25) & (b >= r - 0.02)
    sand = (hue > 25) & (hue < 55) & (sat > 0.35) & (val > 0.55)
    red = (r > g + 0.25) & (r > 0.45)
    dark = (val < 0.32) & (sat < 0.5) & ~water
    rock = (sat < 0.3) & (val >= 0.32) & (val < 0.68) & ~water
    green = (g > r) & (g > b - 0.05) & ~water
    cls[green & (val < 0.42)] = 'J'
    cls[rock] = 'R'
    cls[dark] = 'V'
    cls[sand] = 'S'
    cls[snow] = 'N'
    cls[red] = 'L'
    cls[water] = 'W'
    volcano = box(VOLCANO_BOX)
    cls[(cls == 'L') & ~volcano] = 'R'          # rude skaly pustyni to skaly, nie lawa
    cls[(cls == 'V') & ~volcano] = 'R'
    cls[water & (r > 0.42) & box(SNOW_BOX)] = 'N'   # niebieskie cienie na sniegu
    for bx in OVERLAYS:
        cls[box(bx)] = 'W'
    for (x0, y0, x1, y1) in LABELS:
        # napis: na jego miejsce kopia terenu tuz nad nim (pod nim, gdy u gory brak miejsca) - faktura zostaje
        x0, y0, x1, y1 = x0 - 6, y0 - 6, x1 + 6, y1 + 6
        hh = y1 - y0
        sy = y0 - hh - 4 if y0 - hh - 4 >= 0 else y1 + 4
        cls[y0:y1, x0:x1] = cls[sy:sy + hh, x0:x1]
    cls = majority(cls, 2)
    # do siatki swiata (obrazek 3:2 rozciagniety na kwadrat)
    gy = (np.arange(S) + 0.5) * h / S
    gx = (np.arange(S) + 0.5) * w / S
    out = cls[gy.astype(int)[:, None], gx.astype(int)[None, :]]
    out = majority(out, 1)
    out = drop_small(out)
    return out


def majority(cls, radius):
    """Kazde pole bierze najczestsza klase z kwadratu (2r+1)^2 - bez pojedynczych kropek."""
    keys = np.unique(cls)
    counts = []
    for k in keys:
        m = (cls == k).astype(np.int32)
        c = np.zeros_like(m)
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                c += np.roll(np.roll(m, dy, 0), dx, 1)
        counts.append(c)
    return keys[np.argmax(np.stack(counts), 0)]


def components(mask):
    h, w = mask.shape
    lab = np.full((h, w), -1, np.int32)
    sizes = []
    for y in range(h):
        for x in range(w):
            if mask[y, x] and lab[y, x] < 0:
                n = len(sizes)
                stack = [(y, x)]
                lab[y, x] = n
                k = 0
                while stack:
                    cy, cx = stack.pop()
                    k += 1
                    for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and lab[ny, nx] < 0:
                            lab[ny, nx] = n
                            stack.append((ny, nx))
                sizes.append(k)
    return lab, sizes


def drop_small(cls):
    """Drogi z obrazka (piaskowe kreski na trawie), stateczki i przerywane szlaki morskie znikaja: male plamy
    piasku i sniegu staja sie trawa, male laty ladu na morzu woda, male oczka wody na ladzie trawa."""
    cls = cls.copy()
    for k, limit, into in (('S', 900, 'G'), ('N', 300, 'R'), ('W', 40, 'G'), ('L', 12, 'V')):
        lab, sizes = components(cls == k)
        small = np.array([s < limit for s in sizes] + [False])
        cls[small[lab]] = into
    land = cls != 'W'
    lab, sizes = components(land)
    small = np.array([s < 60 for s in sizes] + [False])
    cls[small[lab]] = 'W'
    return cls


def main():
    cls = classify(sys.argv[1])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, 'wt', encoding='ascii') as f:
        for row in cls:
            f.write(''.join(row) + '\n')
    keys, counts = np.unique(cls, return_counts=True)
    print('zapisano', OUT, dict(zip(keys, counts)))


if __name__ == '__main__':
    main()
