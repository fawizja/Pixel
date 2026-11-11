"""Ikony run z 0.240 (Darek: "runy jak w Tibii"): przebarwione kopie run OTSP, ktore juz sa w repo.

Paczka OTSP (.spr) nie jest potrzebna: bazowe runy OTSP 2696-2699 leza w assets/art/items jako healing_rune (2696),
fireburst_rune (2697), avalanche_rune (2698) i sudden_death_rune (2699) - bez przebarwienia (zob. otsp_import.py,
MORE_ITEMS). Ten skrypt przebarwia je ta sama funkcja tint() co import OTSP. Bez grafik CipSoft.
Uzycie: python3 tools/art/rune_tints.py
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from otsp_import import tint  # noqa: E402

ART = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "art", "items")
BASE = {2696: "healing_rune", 2697: "fireburst_rune", 2698: "avalanche_rune", 2699: "sudden_death_rune"}

# klucz przedmiotu -> (runa OTSP, barwa)
RUNES = {
    "light_magic_missile_rune": (2696, (255, 170, 220)), "heavy_magic_missile_rune": (2696, (220, 60, 140)),
    "icicle_rune": (2698, (150, 220, 255)), "holy_missile_rune": (2697, (255, 240, 150)),
    "thunderstorm_rune": (2698, (200, 120, 255)), "stone_shower_rune": (2698, (170, 140, 100)),
    "explosion_rune": (2697, (255, 90, 60)), "antidote_rune": (2696, (120, 220, 90)),
    "poison_field_rune": (2697, (90, 200, 60)), "fire_field_rune": (2697, (255, 120, 30)),
    "energy_field_rune": (2697, (170, 100, 255)), "poison_bomb_rune": (2698, (90, 200, 60)),
    "fire_bomb_rune": (2698, (255, 120, 30)), "energy_bomb_rune": (2698, (170, 100, 255)),
    "poison_wall_rune": (2699, (90, 200, 60)), "fire_wall_rune": (2699, (255, 120, 30)),
    "energy_wall_rune": (2699, (170, 100, 255)), "magic_wall_rune": (2699, (120, 180, 255)),
    "wild_growth_rune": (2696, (60, 150, 50)), "destroy_field_rune": (2696, (200, 200, 140)),
    "soulfire_rune": (2699, (255, 80, 40)), "paralyse_rune": (2696, (240, 240, 255)),
    "convince_creature_rune": (2697, (230, 190, 120)), "animate_dead_rune": (2699, (150, 150, 170)),
}


def main():
    n = 0
    for key, (otsp, rgb) in RUNES.items():
        src = Image.open(os.path.join(ART, BASE[otsp] + ".png")).convert("RGBA")
        tint(src, rgb).save(os.path.join(ART, key + ".png"))
        n += 1
    print("runy:", n)


if __name__ == "__main__":
    main()
