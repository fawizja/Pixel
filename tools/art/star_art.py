"""Ikony przedmiotow Pieczeci Gasnacej Gwiazdy (2026-11-12): przebarwione ikony, ktore gra juz ma (assets/art/items),
w barwach gwiazdy i sekcji cytadeli. Uzycie: python3 tools/art/star_art.py [katalog assets/art]"""
import os
import sys

from PIL import Image

STAR = (255, 232, 150)
# nowa ikona: (istniejaca ikona bazowa, barwa przebarwienia)
ICONS = {
    "star_blade": ("frost_blade", STAR), "star_axe": ("glacier_axe", STAR), "star_maul": ("coral_warhammer", STAR),
    "star_bow": ("dawn_bow", (235, 235, 255)), "star_wand": ("starfall_wand", (255, 245, 190)),
    "star_rod": ("rod_of_twilight", (200, 230, 255)), "star_fists": ("umbral_grips", STAR),
    "star_plate": ("glacial_plate", STAR), "star_aegis": ("shadow_aegis", STAR),
    "star_spellbook": ("angel_spellbook", (255, 240, 170)), "star_amulet": ("angel_amulet", (255, 240, 170)),
    "penitent_cowl": ("shadow_hood", (150, 135, 125)), "rimeheart_legs": ("glacial_legs", (150, 215, 255)),
    "slagforged_boots": ("leather_boots", (225, 110, 50)), "thornmother_amulet": ("emerald_amulet", (110, 200, 80)),
    "mirror_crown": ("frost_tiara", (215, 230, 255)), "maw_greaves": ("shadow_legs", (190, 60, 80)),
    "dimmed_crown": ("crown_of_the_abyss", (150, 120, 230)), "dimmed_mantle": ("shadowweave_armor", (150, 120, 230)),
    "ash_relic": ("bone", (150, 140, 130)), "rime_sliver": ("crystal_shard", (160, 215, 245)),
    "slag_ingot": ("iron_ore", (215, 110, 60)), "thorn_seed": ("healing_herb", (110, 170, 70)),
    "mirror_shard": ("crystal_shard", (225, 235, 255)), "maw_fang": ("hyena_fang", (215, 195, 175)),
}


def tint(img, rgb):
    img = img.convert("RGBA")
    px = img.load()
    lums = sorted(0.3 * px[x, y][0] + 0.59 * px[x, y][1] + 0.11 * px[x, y][2]
                  for y in range(img.height) for x in range(img.width) if px[x, y][3])
    top = max(lums[int(len(lums) * 0.9)] if lums else 255, 40)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a:
                lum = min(255, int(255 * ((0.3 * r + 0.59 * g + 0.11 * b) / top) ** 0.8))
                px[x, y] = (rgb[0] * lum // 255, rgb[1] * lum // 255, rgb[2] * lum // 255, a)
    return img


def main(art):
    for name, (base, rgb) in ICONS.items():
        tint(Image.open(os.path.join(art, "items", base + ".png")), rgb).save(os.path.join(art, "items", name + ".png"), optimize=True)
    print(len(ICONS), "icons")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
