#!/usr/bin/env python3
"""Importuje wybrane grafiki z OpenTibia Sprite Pack (CC BY 4.0) do assets/art/ pod nazwami z docs/GRAPHICS.md.

Paczka: https://github.com/peonso/opentibia_sprite_pack (autorzy: docs/CREDITS.md).
Uzycie: python3 tools/art/otsp_import.py <katalog client_files paczki> [katalog assets/art]
Mapowanie ponizej: nazwa w grze -> numer przedmiotu (item) albo stroju (outfit) w paczce.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import otsp_extract as ot  # noqa: E402

# Kafelki podloza: caly kwadrat 32x32, bez przycinania.
GROUND = {
    "grass": 106, "field": 116, "jungle_grass": 124, "blighted_grass": 101, "dirt": 136, "sand": 128,
    "dark_sand": 130, "cobblestone": 154, "stone_floor": 153, "wood_floor": 173, "bridge": 182,
    "cave_floor": 138, "abyss_floor": 144, "ash": 143, "water": 163, "swamp": 141, "snow": 145, "ice": 155,
    "frozen_floor": 175, "sandstone_floor": 166, "tomb_floor": 165,
    "stone_wall": 170, "wood_wall": 172, "sandstone_wall": 192,
}
# Obiekty na kafelku: przycinane do zawartosci (drzewa 64x64 tez mieszcza sie w jednym polu).
OBJECTS = {
    "tree": 1505, "pine": 1580, "dead_tree": 1501, "bush": 1483, "rock": 1625, "snow_rock": 1651,
    "sandstone_rock": 1627, "ice_crystal": 1650, "crystal": 1277, "obsidian_rock": 1633, "rubble": 1613,
    "flowers": 1400, "fern": 1414, "crate": 1139, "campfire": 1121, "hole": 996, "rope_spot": 1000,
    "stairs_up": 1005, "stairs_down": 1008, "ladder": 1009, "counter": 1189, "altar": 1193, "wall_torch": 1254,
    # etap 5: wyposazenie miast
    "bed": 1201, "bookcase": 1209, "barrel": 1110, "table": 1191, "statue": 1155, "sand_statue": 1161,
    "jungle_statue": 1157, "cannon": 1163, "chest": 1111, "lumber": 1140,
    "depot_locker": 1113,   # etap 6: skrzynia depozytu jak w Tibii
}
# Mury jak w Tibii: cienka sciana na krawedzi pola z wysokoscia rysowana w gore i w lewo (obrazek 64x64,
# prawy dolny kwadrat to pole muru). Kolejno: pionowy, poziomy, slupek, naroznik.
WALLS = {
    "stone_wall": (534, 535, 536, 537), "wood_wall": (680, 681, 682, 683), "sandstone_wall": (552, 553, 554, 555),
    "mossy_wall": (518, 519, 520, 521), "log_wall": (878, 879, 880, 881),
    "ice_wall": (719, 720, 721, 722), "obsidian_wall": (576, 577, 583, 578),
}
WALL_SHAPES = ("v", "h", "pole", "corner")
# Dachy budynkow (widok jak w Tibii): obiekty rysowane nad budynkami.
ROOFS = {"roof_red": 183, "roof_slate": 187}
# Sciany skalne (bryly): kamien laczacy sie w jednolita skale zamiast cegiel - jaskinie i gory jak w Tibii.
# nazwa: (podloze z paczki, mnoznik jasnosci, kolor domieszki, udzial domieszki)
ROCK_WALLS = {
    "cave_wall": (159, 0.7, None, 0),
    "mountain": (159, 0.95, (120, 110, 90), 0.2),
    "snow_mountain": (159, 1.0, (235, 242, 250), 0.55),
    "sand_cliff": (159, 1.0, (200, 160, 100), 0.5),
    "pyramid": (166, 1.0, (225, 185, 115), 0.2),   # bloki piramidy Qasirah (etap 5)
}
# Plecak w gniezdzie ekwipunku (jak w Tibii: dotkniecie otwiera plecak).
EXTRA_OBJECTS = {"backpack": 2099}
ITEMS = {
    "iron_sword": 1683, "training_sword": 1702, "frost_blade": 1680, "shadowpiercer": 1734, "spear": 1893,
    "hunting_spear": 1880, "steel_spear": 1821, "dawnspear": 1909, "serpent_spear": 1830, "magma_spear": 1842,
    "frostfang_spear": 1858, "wand_of_sparks": 1900, "wand_of_embers": 1903, "wand_of_inferno": 1808,
    "wand_of_storms": 1898, "starfall_wand": 1906, "stormcaller_wand": 1907, "obsidian_wand": 1896,
    "pharaoh_scepter": 1908, "rod_of_vines": 1809, "rod_of_stoneroot": 1810, "thornroot_rod": 1897,
    "rod_of_frost": 1816, "rod_of_hoarfrost": 1817, "rod_of_eternal_winter": 1818, "rod_of_twilight": 1902,
    "iron_knuckles": 2155, "desert_knuckles": 2159,
    "wooden_shield": 1915, "bone_shield": 1938, "sandstone_shield": 1913, "obsidian_shield": 1927,
    "glacial_shield": 1924, "shadow_aegis": 1937,
    "leather_helmet": 1943, "miner_helmet": 1945, "obsidian_helmet": 1974, "glacial_helm": 1979,
    "scarab_helmet": 1961, "shadow_hood": 1948, "grimtooth_crown": 1977, "frost_tiara": 1946,
    "crown_of_the_abyss": 1953,
    "leather_armor": 1995, "studded_armor": 2024, "fur_armor": 2052, "obsidian_armor": 2028,
    "glacial_plate": 2021, "shadowweave_armor": 2003,
    "leather_legs": 2079, "croc_legs": 2084, "glacial_legs": 2086, "shadow_legs": 2087, "obsidian_legs": 2088,
    "leather_boots": 2091,
    "shovel": 2109, "rope": 2137, "torch": 2103,
    "health_potion": 2130, "great_health_potion": 2129, "mana_potion": 2128, "great_mana_potion": 2131,
    "meat": 2181, "bread": 2183,
    "crystal_shard": 2207, "pearl": 2191, "scarab_gem": 2219, "obsidian_shard": 2205, "iron_ore": 2201,
    "bone": 2166, "spider_silk": 2170, "wolf_pelt": 2162,
    "gold_coin": 2192, "platinum_coin": 2191, "crystal_coin": 2218,
}
# Stroje: (numer, kolory glowa/tulow/nogi/stopy dla strojow z maska kolorow albo None).
MONSTERS = {
    "boar": (6, None), "goblin": (7, None), "forest_spider": (8, None), "scorpion": (13, None),
    "skeleton": (3, None), "wraith_knight": (39, None), "sand_wraith": (40, None), "shadow_stalker": (37, None),
    "frost_witch": (35, None), "void_priest": (34, None), "fire_elemental": (36, None),
    "abyssal_demon": (46, None), "cave_troll": (12, None), "jungle_serpent": (29, None),
    "crocodile": (25, None), "storm_harpy": (14, None), "temple_guardian": (22, None), "pharaoh": (23, None),
    "goblin_shaman": (10, None),
    "bandit": (19, None), "jungle_hunter": (16, ((150, 90, 50), (70, 120, 50), (90, 70, 40), (60, 40, 30))),
    "mummy": (17, ((230, 220, 190), (220, 210, 180), (210, 200, 170), (200, 190, 160))),
}
# Potwory bez wlasnego stroju w paczce: podobny stroj przebarwiony (barwa mnozona przez jasnosc piksela).
TINTED = {
    "wolf": (1, (150, 140, 130)), "snow_wolf": (1, (235, 240, 255)), "hyena": (11, (200, 160, 110)),
    "yeti": (12, (240, 245, 255)), "crystal_golem": (12, (150, 220, 240)), "ice_golem": (12, (190, 220, 255)),
    "lava_golem": (12, (255, 120, 50)), "ruin_colossus": (12, (190, 180, 160)), "sand_titan": (12, (230, 200, 130)),
    "grimtooth": (7, (200, 90, 70)), "frost_queen": (35, (200, 235, 255)), "frost_giant": (46, (150, 190, 255)),
    "ice_dragon": (25, (150, 210, 255)), "bone_dragon": (25, (235, 225, 200)), "ignaroth": (25, (255, 110, 60)),
    "hrimgar": (39, (190, 230, 255)), "khazuul": (37, (210, 170, 90)), "abyss_lord": (46, (190, 120, 255)),
    "morgath": (38, (255, 80, 80)),
    # etap 8: expowiska na wyspach i Smoczym Grzbiecie
    "wyvern": (25, (130, 180, 90)), "coral_crab": (13, (255, 130, 100)), "drowned_sailor": (3, (130, 190, 200)),
    "pirate_cutthroat": (19, (210, 100, 80)), "pirate_gunner": (19, (110, 110, 140)),
    "mist_wraith": (40, (205, 220, 230)), "magma_drake": (25, (235, 80, 25)),
}
CHARACTERS = {
    "paladin": (19, None),
    "druid": (32, None),
    "sorcerer": (15, None),
    "monk": (26, None),  # brazowa szata z kapturem (wczesniej stroj 16 z czerwona czapka)
}
_ROLE = {
    "banker": (21, ((90, 70, 50), (60, 60, 110), (50, 50, 60), (40, 30, 30))),
    "carrier": (44, ((150, 110, 70), (140, 90, 50), (90, 80, 60), (60, 40, 30))),
    "guide": (45, ((120, 90, 60), (90, 110, 60), (80, 70, 50), (60, 40, 30))),
    "merchant": (16, ((150, 100, 60), (170, 120, 40), (90, 70, 50), (70, 50, 30))),
    "priest": (18, ((230, 230, 230), (240, 240, 250), (220, 220, 230), (150, 130, 100))),
    "mage": (33, None),
    "smith": (45, ((110, 80, 60), (150, 150, 160), (90, 90, 100), (60, 50, 40))),
    # etap 6: karczmarze, zarzadcy domow, arcymag, rybaczka
    "innkeeper": (16, ((120, 70, 40), (200, 190, 170), (110, 80, 50), (60, 40, 30))),
    "housing": (21, ((70, 60, 110), (110, 90, 150), (60, 50, 70), (40, 30, 30))),
    "archmage": (33, None),
    "fishmonger": (44, ((70, 110, 140), (60, 120, 150), (80, 80, 70), (50, 40, 30))),
    # etap 7: kapitanowie statkow
    "captain": (44, ((40, 50, 90), (40, 60, 120), (200, 190, 170), (40, 30, 30))),
}
_NPC_CSV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                        "shared/src/main/resources/maps/world/npcs.csv")
NPCS = {}
for _line in open(_NPC_CSV, encoding="utf-8"):
    _key = _line.split(";")[0]
    if _key and not _key.startswith("#") and _key.split("_")[-1] in _ROLE:
        NPCS[_key] = _ROLE[_key.split("_")[-1]]
PETS = {"pet_cat": (1, None), "pet_raven": (9, None)}

MASK = {(255, 255, 0): 0, (255, 0, 0): 1, (0, 255, 0): 2, (0, 0, 255): 3}


def outfit(spr, t, d, colors, frame=0):
    base = ot.compose(spr, t, x=d, frame=frame)
    if colors and t["layers"] > 1:
        mask = ot.compose(spr, t, layer=1, x=d, frame=frame)
        b, m = base.load(), mask.load()
        for y in range(base.height):
            for x in range(base.width):
                part = MASK.get(m[x, y][:3]) if m[x, y][3] else None
                if part is not None and b[x, y][3]:
                    c = colors[part]
                    r, g, bl, a = b[x, y]
                    b[x, y] = (r * c[0] // 255, g * c[1] // 255, bl * c[2] // 255, a)
    return base


def trim_square(img, box=None):
    """Przycina do zawartosci i dopelnia do kwadratu (stopy przy dolnej krawedzi, srodek w poziomie)."""
    box = box or img.getbbox()
    if not box:
        return img
    img = img.crop(box)
    s = max(img.width, img.height, 32)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.alpha_composite(img, ((s - img.width) // 2, s - img.height))
    return out


def tint(img, rgb):
    """Przebarwienie: barwa * jasnosc piksela (kontury i cienie zostaja ciemne)."""
    img = img.copy()
    px = img.load()
    lums = sorted(0.3 * px[x, y][0] + 0.59 * px[x, y][1] + 0.11 * px[x, y][2]
                  for y in range(img.height) for x in range(img.width) if px[x, y][3])
    # ciemne stroje (czarny wilk) rozciagniete: 90. percentyl jasnosci = pelna barwa
    top = max(lums[int(len(lums) * 0.9)] if lums else 255, 40)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a:
                lum = min(255, int(255 * ((0.3 * r + 0.59 * g + 0.11 * b) / top) ** 0.8))
                px[x, y] = (rgb[0] * lum // 255, rgb[1] * lum // 255, rgb[2] * lum // 255, a)
    return img


def walk_frames(t):
    """Klatki stroju: 0 = postoj, dwie klatki kroku (w paczce 3 klatki: postoj i dwa kroki)."""
    n = t["frames"]
    return [0, 1, 2] if n == 3 else [0, max(1, n // 4), max(1, 3 * n // 4)] if n > 1 else [0]


def save_outfit(spr, t, d, colors, path, rgb=None):
    """Postoj jako path.png i kroki jako path_walk1.png / path_walk2.png, przyciete wspolnym prostokatem (bez drgan)."""
    imgs = [outfit(spr, t, d, colors, f) for f in walk_frames(t)]
    if rgb:
        imgs = [tint(i, rgb) for i in imgs]
    boxes = [i.getbbox() for i in imgs if i.getbbox()]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    save(trim_square(imgs[0], box), path + ".png")
    for k, img in enumerate(imgs[1:], 1):
        save(trim_square(img, box), f"{path}_walk{k}.png")
    return len(imgs)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def main(client, art):
    things = ot.load_dat(os.path.join(client, "otsp.dat"))
    spr = ot.Spr(os.path.join(client, "otsp.spr"))
    n = 0
    for name, i in GROUND.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/tiles/{name}.png"); n += 1
    for name, i in OBJECTS.items():
        save(trim_square(ot.compose(spr, things["item"][i])), f"{art}/tiles/{name}.png"); n += 1
    for name, (ground, mult, mix, part) in ROCK_WALLS.items():
        img = ot.compose(spr, things["item"][ground]).convert("RGBA")
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                c = [min(255, int(v * mult)) for v in (r, g, b)]
                if mix:
                    c = [int(v * (1 - part) + m * part) for v, m in zip(c, mix)]
                px[x, y] = (c[0], c[1], c[2], a)
        save(img, f"{art}/tiles/{name}.png"); n += 1
    for name, ids in WALLS.items():
        for shape, i in zip(WALL_SHAPES, ids):
            save(ot.compose(spr, things["item"][i]), f"{art}/walls/{name}_{shape}.png"); n += 1
    for name, i in EXTRA_OBJECTS.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/objects/{name}.png"); n += 1
    for name, i in ROOFS.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/objects/{name}.png"); n += 1
    for name, i in ITEMS.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/items/{name}.png"); n += 1
    south = ot.DIRS.index("down")
    for folder, table in (("monsters", MONSTERS), ("npcs", NPCS), ("pets", PETS)):
        for name, (i, colors) in table.items():
            n += save_outfit(spr, things["outfit"][i], south, colors, f"{art}/{folder}/{name}")
    for name, (i, rgb) in TINTED.items():
        n += save_outfit(spr, things["outfit"][i], south, None, f"{art}/monsters/{name}", rgb)
    for voc, (i, colors) in CHARACTERS.items():
        for d, dname in enumerate(ot.DIRS):
            n += save_outfit(spr, things["outfit"][i], d, colors, f"{art}/characters/{voc}_{dname}")
    print("Zapisano", n, "grafik do", art)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else
         os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
