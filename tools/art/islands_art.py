"""Ikony przedmiotow piatki nowych wysp (2026-11-12): przebarwione ikony, ktore gra juz ma (assets/art/items).
Uzycie: python3 tools/art/islands_art.py [katalog assets/art]"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from star_art import tint  # noqa: E402

ICONS = {"silver_brooch": ("pearl", (215, 220, 235))}


def main(art):
    for name, (base, rgb) in ICONS.items():
        tint(Image.open(os.path.join(art, "items", base + ".png")), rgb).save(os.path.join(art, "items", name + ".png"), optimize=True)
    print(len(ICONS), "icons")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
