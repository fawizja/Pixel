#!/usr/bin/env python3
"""Mapa swiata z punktami spawnow pokolorowanymi wg poziomu potwora (kontrola stopniowania poziomow, route_levels.py).

    python3 tools/mapgen/render_spawn_levels.py <wyjscie.png> [skala] [katalog swiata] [monsters.csv]
Powierzchnia przyciemniona, spawny ze wszystkich pieter: kolko = powierzchnia, kwadrat = podziemia/pietra.
Kolory: pasy poziomu z route_levels.LEVEL_BANDS (zielony = slabe ... fioletowy = 701-1000), legenda w rogu.
Wymaga Pillow.
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import render_overview as ro  # noqa: E402
import route_levels as rl  # noqa: E402

COLORS = [(80, 220, 80), (170, 230, 60), (240, 220, 50), (250, 160, 40), (240, 90, 40), (220, 40, 60), (200, 40, 160),
          (140, 60, 230)]


def color_of(level):
    for (lo, hi), c in zip(rl.LEVEL_BANDS, COLORS):
        if lo <= level <= hi:
            return c
    return COLORS[-1]


def main():
    out = sys.argv[1]
    scale = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    d = sys.argv[3] if len(sys.argv) > 3 else os.path.join(ro.ROOT, 'maps', 'world')
    mon = rl.load_monsters(sys.argv[4] if len(sys.argv) > 4 else None)
    colors = ro.load_colors()
    legend = {}
    for line in open(os.path.join(d, 'legend.csv'), encoding='utf-8'):
        if not line.strip() or line.startswith('//') or line.startswith('#'):
            continue
        p = line.rstrip('\n').split(';')
        legend[' ' if p[0] == 'space' else p[0]] = p[1:5]
    rows = open(os.path.join(d, 'floor_0.txt'), encoding='utf-8').read().split('\n')
    w, h = max(len(r) for r in rows), len(rows)
    img = Image.new('RGB', (w * scale, h * scale), (10, 10, 14))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            layers = legend.get(ch)
            if not layers:
                continue
            col = None
            for key in (layers[3], layers[2], layers[1], layers[0]):
                if key and key != '-' and key in colors:
                    col = colors[key]
                    break
            if col is None:
                continue
            col = tuple(c * 2 // 5 for c in col)
            for dy in range(scale):
                for dx in range(scale):
                    px[x * scale + dx, y * scale + dy] = col
    dr = ImageDraw.Draw(img)
    r = max(2, scale * 2)
    for ln in open(os.path.join(d, 'spawns.csv'), encoding='utf-8'):
        if ln.startswith('#') or not ln.strip():
            continue
        f = ln.strip().split(';')
        m = mon.get(f[0])
        if m is None:
            continue
        x, y, z = int(f[1]) * scale, int(f[2]) * scale, int(f[3])
        c = color_of(m['level'])
        if z == 0:
            dr.ellipse([x - r, y - r, x + r, y + r], fill=c)
        else:
            dr.rectangle([x - r, y - r, x + r, y + r], outline=c, width=max(1, scale))
    props = {}
    for line in open(os.path.join(d, 'map.properties'), encoding='utf-8'):
        if '=' in line and not line.startswith('#'):
            k, v = line.rstrip('\n').split('=', 1)
            props[k] = v
    for t in props.get('towns', '').split(';'):
        n, c = t.split(':')
        x, y, _z = (int(v) for v in c.split(','))
        dr.rectangle([x * scale - 5, y * scale - 5, x * scale + 5, y * scale + 5], outline=(255, 255, 255), width=2)
        dr.text((x * scale + 8, y * scale - 6), n, fill=(255, 255, 255))
    # legenda
    y0 = 8
    for (lo, hi), c in zip(rl.LEVEL_BANDS, COLORS):
        dr.rectangle([8, y0, 22, y0 + 12], fill=c)
        dr.text((28, y0), f'poziom {lo}-{hi}', fill=(255, 255, 255))
        y0 += 16
    dr.text((8, y0 + 4), 'kolko = powierzchnia, kwadrat = podziemia', fill=(200, 200, 200))
    img.save(out)


if __name__ == '__main__':
    main()
