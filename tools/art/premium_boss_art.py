#!/usr/bin/env python3
"""Grafiki 21 nowych bossow linii premium (tools/mapgen/premium_data.py BOSSES): kopie grafik bazowych potworow (OTSP) z odcieniem
koloru bossa, wszystkie kierunki i klatki marszu. Uzycie: python3 tools/art/premium_boss_art.py"""
import os
import re
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
sys.path.insert(0, os.path.join(ROOT, 'tools', 'mapgen'))
import premium_data as D  # noqa: E402

DIR = os.path.join(ROOT, 'assets', 'art', 'monsters')


def tint(im, color):
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            pr, pg, pb, pa = px[x, y]
            if pa == 0:
                continue
            lum = (0.3 * pr + 0.59 * pg + 0.11 * pb) / 255.0
            tr, tg, tb = (min(255, c * lum * 1.7) for c in (r, g, b))
            px[x, y] = (int(0.5 * pr + 0.5 * tr), int(0.5 * pg + 0.5 * tg), int(0.5 * pb + 0.5 * tb), pa)
    return im


def main():
    files = os.listdir(DIR)
    n = 0
    for key, (_nm, _lv, base, color) in D.BOSSES.items():
        pat = re.compile('^' + re.escape(base) + r'((?:_(?:up|left|right|ne|nw|se|sw))?(?:_walk[12])?)\.png$')
        found = [(f, pat.match(f).group(1)) for f in files if pat.match(f)]
        assert found, base
        for f, suffix in found:
            im = Image.open(os.path.join(DIR, f)).convert('RGBA')
            tint(im, color).save(os.path.join(DIR, key + suffix + '.png'))
            n += 1
    print('zapisano', n, 'plikow dla', len(D.BOSSES), 'bossow')


if __name__ == '__main__':
    main()
