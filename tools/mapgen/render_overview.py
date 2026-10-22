#!/usr/bin/env python3
"""Podglad calego pietra mapy (kolory z data/tiles.csv) do PNG - wizualizacja, nie zrzut z gry.

    python3 tools/mapgen/render_overview.py <pietro> <wyjscie.png> [skala] [mapa]
Miasta (map.properties towns=) sa podpisane. Wymaga Pillow.
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'shared', 'src', 'main', 'resources')


def hex_rgb(h):
    h = h.strip().lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def load_colors():
    colors = {}
    with open(os.path.join(ROOT, 'data', 'tiles.csv'), encoding='utf-8') as f:
        for line in f:
            if not line.strip() or line.startswith('#'):
                continue
            p = line.rstrip('\n').split(';')
            colors[p[1]] = hex_rgb(p[6])
    return colors


def load_legend(name):
    legend = {}
    with open(os.path.join(ROOT, 'maps', name, 'legend.csv'), encoding='utf-8') as f:
        for line in f:
            if not line.strip() or line.startswith('//') or line.startswith('#'):
                continue
            p = line.rstrip('\n').split(';')
            sym = ' ' if p[0] == 'space' else p[0]
            legend[sym] = p[1:5]
    return legend


def main():
    floor = sys.argv[1]
    out = sys.argv[2]
    scale = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    name = sys.argv[4] if len(sys.argv) > 4 else 'world'
    colors = load_colors()
    legend = load_legend(name)
    fname = 'floor_%s.txt' % (floor if floor.startswith('-') or floor == '0' else '+' + floor.lstrip('+'))
    with open(os.path.join(ROOT, 'maps', name, fname), encoding='utf-8') as f:
        rows = f.read().split('\n')
    w = max(len(r) for r in rows)
    h = len(rows)
    img = Image.new('RGB', (w * scale, h * scale), (12, 12, 16))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            layers = legend.get(ch)
            if not layers:
                continue
            col = None
            for i, key in enumerate((layers[3], layers[2], layers[1], layers[0])):  # sciana > obiekt > dekoracja > podloze
                if key and key != '-' and key in colors:
                    col = colors[key]
                    if i == 0:  # sciany i gory ciemniejsze - widac je na tle podloza
                        col = tuple(c * 3 // 5 for c in col)
                    break
            if col is None:
                continue
            for dy in range(scale):
                for dx in range(scale):
                    px[x * scale + dx, y * scale + dy] = col
    props = {}
    with open(os.path.join(ROOT, 'maps', name, 'map.properties'), encoding='utf-8') as f:
        for line in f:
            if '=' in line and not line.startswith('#'):
                k, v = line.rstrip('\n').split('=', 1)
                props[k] = v
    d = ImageDraw.Draw(img)
    for t in props.get('towns', '').split(';'):
        if ':' not in t:
            continue
        n, c = t.split(':')
        x, y, z = (int(v) for v in c.split(','))
        if str(z) == floor.lstrip('+'):
            d.rectangle([x * scale - 2, y * scale - 2, x * scale + 2, y * scale + 2], outline=(255, 255, 0))
            d.text((x * scale + 5, y * scale - 6), n, fill=(255, 255, 255))
    img.save(out)


if __name__ == '__main__':
    main()
