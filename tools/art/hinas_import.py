#!/usr/bin/env python3
"""Przedmioty Hinasa (OTLand, grafiki udostepnione publicznie; plik Credits.txt mowi tylko "Hinas", bez licencji -
wlasciciel projektu przyjal je z podaniem autora, na zyczenie autora zostana usuniete; docs/CREDITS.md).

    python3 tools/art/hinas_import.py [katalog assets]

Zrodla (tools/art/hinas/, poza repo): luzne PNG 32x32 w loose/ i rozpakowane zestawy "set N"/"New Piskel (1)"
(numerowane PNG 32x32, Photo.png to tylko podglad). Do gry trafiaja tylko uzywane obrazki: assets/art/items/<klucz>.png,
32x32 bez zmian (bez skalowania i rozmywania); jedyna poprawka: czarne tlo jednego obrazka (set 13/11) staje sie
przezroczyste (wypelnienie od krawedzi). Statystyki i nazwy: shared/src/main/resources/data/items.csv (id 256..).

Nieuzyte (przeglad 2026-11-10): loose/06 i 07 (zlote nagolenniki i helm - zbyt bliskie Golden Legs / Golden Helmet
z Tibii), plecaki loose/1-3, 5-9 (komplet kolorowych plecakow Tibii z plecakiem moro - przerysowania przedmiotow
CipSoft), loose/4 (skrzynia, brak przedmiotu), set 7/08 i 09 (prawie kopie set 3/104 i 103, nie pasuja do zestawu).
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "hinas")
# przedmiot w grze -> (katalog w tools/art/hinas, plik)
MAP = {
    "rooster_helmet": ("set 13", "01.png"),
    "rooster_armor": ("set 13", "04.png"),
    "rooster_legs": ("set 13", "07.png"),
    "rooster_boots": ("set 13", "10.png"),
    "rooster_shield": ("set 13", "02.png"),
    "rooster_sword": ("set 13", "00.png"),
    "rooster_club": ("set 13", "09.png"),
    "rooster_bow": ("set 13", "06.png"),
    "rooster_wand": ("set 13", "03.png"),
    "rooster_rod": ("set 13", "11.png"),
    "rooster_spellbook": ("set 13", "05.png"),
    "rooster_amulet": ("set 13", "08.png"),
    "candy_helmet": ("set 7", "01.png"),
    "candy_armor": ("set 7", "04.png"),
    "candy_legs": ("set 7", "07.png"),
    "candy_boots": ("set 7", "10.png"),
    "candy_shield": ("set 7", "02.png"),
    "candy_sword": ("set 7", "00.png"),
    "candy_bow": ("set 7", "06.png"),
    "candy_wand": ("set 7", "03.png"),
    "candy_spellbook": ("set 7", "05.png"),
    "candy_amulet": ("set 7", "11.png"),
    "plague_helmet": ("set 3", "set 100.png"),
    "plague_mask": ("set 3", "set 104.png"),
    "plague_armor": ("set 3", "set 108.png"),
    "plague_legs": ("set 3", "set 105.png"),
    "plague_boots": ("set 3", "set 102.png"),
    "plague_shield": ("set 3", "set 111.png"),
    "plague_sword": ("set 3", "set 110.png"),
    "plague_axe": ("set 3", "set 103.png"),
    "plague_bow": ("set 3", "set 106.png"),
    "plague_wand": ("set 3", "set 109.png"),
    "plague_rod": ("set 3", "set 101.png"),
    "plague_spellbook": ("set 3", "set 107.png"),
    "blizzard_helmet": ("set 5", "01.png"),
    "blizzard_armor": ("set 5", "04.png"),
    "blizzard_legs": ("set 5", "07.png"),
    "blizzard_boots": ("set 5", "10.png"),
    "blizzard_shield": ("set 5", "02.png"),
    "blizzard_sword": ("set 5", "00.png"),
    "blizzard_axe": ("set 5", "09.png"),
    "blizzard_bow": ("set 5", "06.png"),
    "blizzard_wand": ("set 5", "03.png"),
    "blizzard_rod": ("set 5", "11.png"),
    "blizzard_spellbook": ("set 5", "05.png"),
    "blizzard_amulet": ("set 5", "08.png"),
    "dragon_helmet": ("set 9", "01.png"),
    "dragon_armor": ("set 9", "04.png"),
    "dragon_legs": ("set 9", "07.png"),
    "dragon_boots": ("set 9", "10.png"),
    "dragon_shield": ("set 9", "02.png"),
    "dragon_sword": ("set 9", "00.png"),
    "dragon_axe": ("set 9", "09.png"),
    "dragon_bow": ("set 9", "06.png"),
    "dragon_wand": ("set 9", "11.png"),
    "dragon_rod": ("set 9", "03.png"),
    "dragon_spellbook": ("set 9", "05.png"),
    "dragon_amulet": ("set 9", "08.png"),
    "sun_helmet": ("set 11", "01.png"),
    "sun_armor": ("set 11", "04.png"),
    "sun_legs": ("set 11", "07.png"),
    "sun_boots": ("set 11", "10.png"),
    "sun_shield": ("set 11", "02.png"),
    "sun_sword": ("set 11", "00.png"),
    "sun_axe": ("set 11", "09.png"),
    "sun_bow": ("set 11", "06.png"),
    "sun_wand": ("set 11", "03.png"),
    "sun_rod": ("set 11", "11.png"),
    "sun_spellbook": ("set 11", "05.png"),
    "sun_amulet": ("set 11", "08.png"),
    "phoenix_helmet": ("set 6", "01.png"),
    "phoenix_armor": ("set 6", "04.png"),
    "phoenix_legs": ("set 6", "07.png"),
    "phoenix_boots": ("set 6", "10.png"),
    "phoenix_shield": ("set 6", "02.png"),
    "phoenix_sword": ("set 6", "00.png"),
    "phoenix_axe": ("set 6", "09.png"),
    "phoenix_mace": ("set 6", "11.png"),
    "phoenix_bow": ("set 6", "06.png"),
    "phoenix_wand": ("set 6", "03.png"),
    "phoenix_spellbook": ("set 6", "05.png"),
    "phoenix_amulet": ("set 6", "08.png"),
    "deep_helmet": ("set 12", "01.png"),
    "deep_armor": ("set 12", "04.png"),
    "deep_legs": ("set 12", "07.png"),
    "deep_boots": ("set 12", "10.png"),
    "deep_shield": ("set 12", "02.png"),
    "deep_trident": ("set 12", "00.png"),
    "deep_hammer": ("set 12", "09.png"),
    "deep_bow": ("set 12", "06.png"),
    "deep_wand": ("set 12", "03.png"),
    "deep_rod": ("set 12", "11.png"),
    "deep_spellbook": ("set 12", "05.png"),
    "deep_amulet": ("set 12", "08.png"),
    "moon_helmet": ("set 8", "01.png"),
    "moon_armor": ("set 8", "04.png"),
    "moon_legs": ("set 8", "07.png"),
    "moon_boots": ("set 8", "10.png"),
    "moon_shield": ("set 8", "02.png"),
    "moon_sword": ("set 8", "00.png"),
    "moon_axe": ("set 8", "09.png"),
    "moon_bow": ("set 8", "06.png"),
    "moon_wand": ("set 8", "03.png"),
    "moon_spellbook": ("set 8", "05.png"),
    "moon_amulet": ("set 8", "08.png"),
    "angel_helmet": ("New Piskel (1)", "01.png"),
    "angel_armor": ("New Piskel (1)", "04.png"),
    "angel_legs": ("New Piskel (1)", "07.png"),
    "angel_boots": ("New Piskel (1)", "10.png"),
    "angel_shield": ("New Piskel (1)", "02.png"),
    "angel_sword": ("New Piskel (1)", "00.png"),
    "angel_axe": ("New Piskel (1)", "09.png"),
    "angel_bow": ("New Piskel (1)", "06.png"),
    "angel_wand": ("New Piskel (1)", "03.png"),
    "angel_rod": ("New Piskel (1)", "11.png"),
    "angel_spellbook": ("New Piskel (1)", "05.png"),
    "angel_amulet": ("New Piskel (1)", "08.png"),
    "infernal_helmet": ("set 4", "01.png"),
    "infernal_armor": ("set 4", "04.png"),
    "infernal_legs": ("set 4", "07.png"),
    "infernal_boots": ("set 4", "10.png"),
    "infernal_shield": ("set 4", "02.png"),
    "infernal_sword": ("set 4", "00.png"),
    "infernal_axe": ("set 4", "09.png"),
    "infernal_bow": ("set 4", "06.png"),
    "infernal_wand": ("set 4", "03.png"),
    "infernal_rod": ("set 4", "11.png"),
    "infernal_spellbook": ("set 4", "05.png"),
    "infernal_amulet": ("set 4", "08.png"),
    "forest_hood": ("loose", "04.png"),
    "traveler_boots": ("loose", "02.png"),
    "wolf_spellbook": ("loose", "03.png"),
    "horned_helmet": ("loose", "01.png"),
    "gem_shield": ("loose", "08.png"),
    "crystal_shortsword": ("loose", "09.png"),
    "crystal_sword": ("loose", "10.png"),
    "emerald_amulet": ("loose", "11.png"),
}
# obrazki z nieprzezroczystym czarnym tlem
BLACK_BACKGROUND = {("set 13", "11.png")}


def clear_background(img):
    """Czarne piksele polaczone z krawedzia -> przezroczyste (kontur w srodku zostaje)."""
    w, h = img.size
    px = img.load()
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    seen = set()
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
            continue
        seen.add((x, y))
        r, g, b, a = px[x, y]
        if a == 0 or max(r, g, b) > 12:
            continue
        px[x, y] = (0, 0, 0, 0)
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return img


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "assets")
    if not os.path.isdir(SRC):
        sys.exit("Brak zrodel w tools/art/hinas/ (rozpakuj paczke Hinasa: loose/ i katalogi zestawow)")
    out = os.path.join(assets, "art", "items")
    for key, (folder, name) in MAP.items():
        img = Image.open(os.path.join(SRC, folder, name)).convert("RGBA")
        if img.size != (32, 32):
            img = img.resize((32, 32), Image.NEAREST)
        if (folder, name) in BLACK_BACKGROUND:
            img = clear_background(img)
        img.save(os.path.join(out, key + ".png"))
    print(f"Hinas: {len(MAP)} przedmiotow -> {out}")


if __name__ == "__main__":
    main()
