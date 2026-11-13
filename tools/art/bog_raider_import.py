#!/usr/bin/env python3
"""Bagienny lupiezca (bog_reaver): grafika Darka (Goblin3D), 128x128 -> ~40 px, 4 kierunki x (postoj, walk1, walk2).
Uzycie: python3 tools/art/bog_raider_import.py <katalog z idle.png i walk.png> assets/art/monsters"""
import sys
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
KEY = "bog_reaver"
idle = Image.open(f"{src}/idle.png").convert("RGBA")
walk = Image.open(f"{src}/walk.png").convert("RGBA")
ROWS = [("", 0), ("_left", 1), ("_right", 2), ("_up", 3)]  # kolejnosc w arkuszu: down, left, right, up
SCALE = 40 / 111  # postac ma ok. 111 px wysokosci

def prep(img):
    # alfa premultiplikowana: brak jasnej obwodki przy skalowaniu
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r * a // 255, g * a // 255, b * a // 255, a)
    return img

def unprep(img):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a < 70:
                px[x, y] = (0, 0, 0, 0)
            elif a < 255:
                k = 255 / a
                px[x, y] = (min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255)
    return img

def shrink(f):
    f = prep(f.copy())
    f = f.resize((round(128 * SCALE), round(128 * SCALE)), Image.LANCZOS)
    f = unprep(f)
    return f  # bez wyostrzania: UnsharpMask wzmacnial jasne pikselki obrysu

for suffix, row in ROWS:
    frames = [shrink(idle.crop((0, row * 128, 128, row * 128 + 128))),
              shrink(walk.crop((1 * 128, row * 128, 2 * 128, row * 128 + 128))),
              shrink(walk.crop((5 * 128, row * 128, 6 * 128, row * 128 + 128)))]
    bx = [f.getbbox() for f in frames]
    box = (min(b[0] for b in bx), min(b[1] for b in bx), max(b[2] for b in bx), max(b[3] for b in bx))
    s = max(box[2] - box[0], box[3] - box[1], 32)
    for k, f in enumerate(frames):
        c = f.crop(box)
        o = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        o.alpha_composite(c, ((s - c.width) // 2, s - c.height))
        name = f"{KEY}{suffix}" + ("" if k == 0 else f"_walk{k}")
        o.save(f"{out}/{name}.png", optimize=True)
        print(name, o.size)
