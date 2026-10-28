#!/usr/bin/env python3
"""Ikony czaszek PvP rysowane kodem (bez cudzych grafik).

Zapisuje do assets/ui/pvp:
  skull_white.png, skull_red.png - 16x16, czaszka przy imieniu gracza i we wlasnym HUD,
  pvp_off.png, pvp_on.png        - 32x32, przelacznik trybu PvP obok ekwipunku (biala / czerwona czaszka
                                   na skrzyzowanych mieczach).
Uzycie: python3 tools/art/skull_icons.py [katalog wyjsciowy]
"""
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..',
                                                         'assets', 'ui', 'pvp')
# 16x16: '#' kontur, 'o' kosc, 's' cien kosci, '.' oczodol/nos, ' ' przezroczyste
SKULL = [
    "                ",
    "     ######     ",
    "   ##oooooo##   ",
    "  #oooooooooo#  ",
    "  #oooooooooo#  ",
    " #oooooooooooo# ",
    " #oo...oo...os# ",
    " #oo...oo...os# ",
    " #ooo.oooo.oos# ",
    "  #oooo..ooos#  ",
    "  #soooooooss#  ",
    "   ##o#o#o#s#   ",
    "    #o#o#o#s#   ",
    "    #########   ",
    "                ",
    "                ",
]
OUTLINE = (20, 14, 12, 255)
HOLE = (28, 18, 16, 255)
PALETTES = {
    'white': ((240, 238, 228, 255), (176, 170, 158, 255)),
    'red': ((222, 44, 36, 255), (140, 20, 18, 255)),
}
BLADE, BLADE_DARK, HILT = (196, 204, 214, 255), (110, 118, 130, 255), (134, 90, 44, 255)


def skull(bone, shade):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SKULL):
        for x, c in enumerate(row):
            col = {'#': OUTLINE, 'o': bone, 's': shade, '.': HOLE}.get(c)
            if col:
                im.putpixel((x, y), col)
    return im


def toggle(bone, shade):
    """32x32: skrzyzowane miecze i czaszka na srodku."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for t in range(3, 27):
        for x, y in ((t, t), (31 - t, t)):
            im.putpixel((x, y), BLADE)
            im.putpixel((x, y + 1), BLADE_DARK)
    # jelce i rekojesci w dolnych rogach
    for d in range(-2, 3):
        im.putpixel((26 + d, 26 - d), HILT)
        im.putpixel((5 + d, 26 + d), HILT)
    for t in range(27, 30):
        im.putpixel((t, t), HILT)
        im.putpixel((31 - t, t), HILT)
    s = skull(bone, shade).resize((24, 24), Image.NEAREST)
    im.alpha_composite(s, (4, 2))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (bone, shade) in PALETTES.items():
        skull(bone, shade).save(os.path.join(OUT, 'skull_' + name + '.png'))
    toggle(*PALETTES['white']).save(os.path.join(OUT, 'pvp_off.png'))
    toggle(*PALETTES['red']).save(os.path.join(OUT, 'pvp_on.png'))
    print('zapisano', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
