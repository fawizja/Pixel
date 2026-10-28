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
    "grass": 106, "field": 116, "jungle_grass": 124, "blighted_grass": 101, "dirt": 136, "sand": 126,
    "dark_sand": 130, "cobblestone": 154, "stone_floor": 153, "wood_floor": 173, "bridge": 182,
    "cave_floor": 138, "abyss_floor": 144, "ash": 143, "water": 163, "swamp": 141, "snow": 145, "ice": 155,
    "frozen_floor": 175, "sandstone_floor": 166, "tomb_floor": 165,
    "stone_wall": 170, "wood_wall": 172, "sandstone_wall": 192,
    "lava": 2298,   # 2026-10-23: piasek 126 (pasuje do obrzezy 315), lawa z paczki
    "ship_deck": 173, "rope_bridge": 182, "chasm": 992,
}
# Obrzeza podloza jak w Tibii (borders/klucz_krawedz.png): 12 kawalkow z paczki w kolejnosci RME
# n, e, s, w, cnw, cne, csw, cse (male rogi po skosie), dnw, dne, dsw, dse (duze rogi przy dwoch krawedziach).
BORDER_EDGES = ("n", "e", "s", "w", "cnw", "cne", "csw", "cse", "dnw", "dne", "dsw", "dse")
BORDERS = {
    "grass": 227, "field": 251, "jungle_grass": 291, "blighted_grass": 215, "dirt": 387, "sand": 315,
    "dark_sand": 339, "swamp": 375,
}
# Obiekty na kafelku: przycinane do zawartosci (drzewa 64x64 tez mieszcza sie w jednym polu).
OBJECTS = {
    "tree": 1505, "pine": 1580, "dead_tree": 1501, "bush": 1483, "rock": 1625, "snow_rock": 1651,
    "sandstone_rock": 1627, "ice_crystal": 1650, "crystal": 1277, "obsidian_rock": 1633, "rubble": 1613,
    "flowers": 1400, "fern": 1414, "crate": 1139, "campfire": 1121, "hole": 996, "rope_spot": 1000, "sewer_grate": 999,
    "stairs_up": 1005, "stairs_down": 1008, "ladder": 1009, "counter": 1189, "altar": 1193, "wall_torch": 1254,
    # etap 5: wyposazenie miast
    "bookcase": 1209, "barrel": 1110, "table": 1191, "statue": 1155, "sand_statue": 1161,
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
ROOFS = {"roof_red": 184, "roof_slate": 188, "roof_wood": 182, "roof_brown": 190,
         # 2026-10-24: kalenice (_top) i okapy (_bottom) z paczki, pomaranczowa okragla dachowka, niebieska palacu
         "roof_red_top": 183, "roof_slate_top": 187, "roof_brown_top": 189,
         "roof_orange": 180, "roof_orange_top": 178, "roof_orange_bottom": 181,
         "roof_royal": 188, "roof_royal_top": 187}
# dachy przebarwione: nazwa -> (nasycenie, jasnosc) - niebieska dachowka z paczki jako szary lupek
ROOF_TONE = {"roof_slate": (0.18, 0.9), "roof_slate_top": (0.18, 0.9)}
# Sciany skalne (bryly): kamien laczacy sie w jednolita skale zamiast cegiel - jaskinie i gory jak w Tibii.
# nazwa: (podloze z paczki, mnoznik jasnosci, kolor domieszki, udzial domieszki)
ROCK_WALLS = {
    # wierzch gor: plaskie podloza "mountain" z paczki (990 piaskowy, 991 skalny), krawedzie to odcinki MOUNTAINS
    "cave_wall": (991, 0.62, None, 0),
    "mountain": (990, 0.95, (150, 140, 90), 0.15),
    "snow_mountain": (991, 1.0, (235, 242, 250), 0.55),
    "sand_cliff": (994, 1.0, None, 0),
    "pyramid": (166, 1.0, (225, 185, 115), 0.2),   # bloki piramidy Qasirah (etap 5)
}
# Gory i jaskinie jak w Tibii (2026-10-23 wieczor): odcinki gor z paczki rysowane jak mury (2x2 pola), kolejno
# pionowy, poziomy, slupek, naroznik; kazdy ksztalt to lista przedmiotow, a przedmiot z wzorem (px x py) daje
# kilka wariantow (walls/klucz_ksztalt.png i walls/klucz_ksztalt_N.png).
MOUNTAINS = {
    "mountain": ((923,), (922,), (921,), (930, 931)),
    "snow_mountain": ((939,), (938,), (937,), (943, 944, 945)),
    "sand_cliff": ((919,), (920,), (921,), (934, 926)),
    "cave_wall": ((957,), (956,), (958,), (961,)),
}
# Dopracowanie miast (2026-10-23): wyposazenie wnetrz i ulic, nowe nawierzchnie, mury z oknami, warianty roslin.
TOWN_OBJECTS = {
    "chair": 1094, "wardrobe": 1104, "potted_plant": 1088, "well": 1154, "firewood": 1141, "banner": 1179,
    "lectern": 1181, "showcase": 1211, "bucket": 1185, "bench": 1197,
    # 2026-10-24: balwany w miastach sniegu, dzbany i worki we wnetrzach
    "snowman": 1217, "clay_pot": 2700, "sack": 2673,
}
TOWN_GROUND = {"paving": 155, "brick_paving": 147, "marble_floor": 156}
TOWN_WALLS = {
    "plaster_wall": (603, 604, 605, 606), "whitewash_wall": (617, 618, 619, 620), "timber_wall": (627, 628, 629, 630),
}
# Okna zamkniete i otwarte (2026-10-24, Darek: "okna otwierane po nacisnieciu"): (pionowe zamkniete, pionowe otwarte,
# poziome zamkniete, poziome otwarte); para (mur, okno) naklada okno z paczki (1078/1079) na otwor w murze.
WINDOW_STATES = {
    "stone_wall": (539, 538, 541, 540), "plaster_wall": (612, 611, 614, 613),
    "whitewash_wall": ((621, 1078), 621, (622, 1079), 622), "timber_wall": ((631, 1078), 631, (632, 1079), 632),
    "sandstone_wall": ((556, 1078), 556, (557, 1079), 557),
}
# Drzwi w murach jak w Tibii (pionowe zamkniete, pionowe otwarte, poziome zamkniete, poziome otwarte);
# mury bez wlasnych drzwi w paczce dostaja drzwi kamienne (walls/klucz_door_v.png, _door_vopen, _door_h, _door_hopen).
DOORS = {
    "stone_wall": (1013, 1014, 1016, 1017), "timber_wall": (1027, 1028, 1030, 1031),
    "whitewash_wall": (1041, 1042, 1044, 1045), "plaster_wall": (1053, 1054, 1056, 1057),
    "wood_wall": (1065, 1066, 1068, 1069), "sandstone_wall": (1071, 1072, 1074, 1075),
}
DOOR_SHAPES = ("door_v", "door_vopen", "door_h", "door_hopen")
# Odmiany murow budynkow (2026-10-24, Darek: "urozmaic mury z grafiki ktorych nie wykorzystales"): renderer wybiera
# odmiane dla calego budynku (spojny fragment muru), wiec sasiednie domy roznia sie murem, a jeden dom ma jeden.
# mur -> lista odmian (pionowy, poziomy, slupek, naroznik, okno pionowe, okno poziome; None = bez okna),
# pliki walls/klucz_altN_ksztalt.png; okno otwarte powstaje z zamknietego (szyba zastapiona ciemnym wnetrzem).
ALT_WALLS = {
    "stone_wall": [(542, 543, 544, 545, 550, 551), (558, 559, 560, 561, None, None), (569, 570, 571, 572, None, None)],
    "timber_wall": [(600, 601, 592, 602, 598, 599)],
    "whitewash_wall": [(654, 655, 656, 657, 663, 664)],
    "plaster_wall": [(713, 714, 715, 716, 717, 718)],
    "sandstone_wall": [(713, 714, 715, 716, 717, 718)],
}
ALT_SHAPES = ("v", "h", "pole", "corner", "vwin", "hwin", "vwin_open", "hwin_open")
# Warianty roslin i kamieni (tiles/klucz_vN.png): renderer wybiera wariant wg pozycji pola, las nie jest jednym drzewem.
VARIANTS = {
    # 2026-10-23 wieczor (Darek: "dodaj wszystkie grafiki"): wiecej drzew, krzewow, kamieni i roslin z paczki
    "tree": (1503, 1504, 1506, 1507, 1509, 1531, 1534, 1537, 1511, 1512, 1514, 1519, 1521, 1523, 1529, 1532),
    "pine": (1508, 1565, 1566, 1579),
    "dead_tree": (1500, 1502, 1576),
    "bush": (1498, 1538, 1541, 1544, 1550, 1539, 1540, 1542, 1546, 1548, 1553, 1556, 1443, 1456, 1482),
    "flowers": (1387, 1394, 1400, 1422, 1424, 1448, 1449, 1451, 1452, 1454, 1480, 1405, 1406),
    "fern": (1408, 1409, 1411, 1415, 1416, 1417, 1490, 1499),
    "tall_grass": (1362, 1365, 1366, 1368, 1370, 1375, 1378, 1382),
    "rock": (1622, 1624, 1626, 1628, 1634, 1635, 1636, 1637, 1638, 1639, 1640, 1598, 1599),
    "snow_rock": (1606, 1607, 1609),
    "sandstone_rock": (1632, 1603, 1618),
    "rubble": (1614, 1615, 1616, 1594, 1595, 1597),
    "crystal": (1646, 1647, 2262, 2263),
    # 2026-10-24 (Darek: "inne grafiki z paczki tez"): odmiany mebli i ozdob, pajeczyny, balwany
    "chair": (1093, 1095, 1096, 1097, 1090, 1091, 1098, 1099),
    "crate": (1115, 1117),
    "statue": (1156, 1159, 1160),
    "flower_box": (1246, 1247, 1248),
    "candle": (1243, 1244),
    "banner": (1180,),
    "lumber": (1142, 1145),
    "cobweb": (1273, 1274),
    "snowman": (1218,),
    # 2026-10-24 (Darek: "wiecej mebli, precyzyjne"): komody, beczki i baryłki na boku
    "wardrobe": (1105, 1107, 1108),
    "barrel": (1185, 1187),
}
# Roslinnosc wg klimatu i slady napadu na wioski (2026-10-28, Darek: "lasy, dzungla, lodowe wyspy dopasowane do
# klimatu, krzaczki"; "opuszczone wioski napadniete, zniszczone, spalone"). nazwa -> (numery, przerobka):
# None = bez zmian, ("mix", barwa, udzial) = domieszka barwy (szron, susza), ("dark", mnoznik) = zweglone,
# ("tint", barwa) = przebarwienie. Pierwszy numer to tiles/klucz.png, kolejne to warianty _vN.
CLIMATE = {
    "jungle_tree": ((1562, 1564, 1568, 1570, 1571, 1572, 1573, 1574, 1577, 1578, 1581, 1582, 1583, 1584), None),
    "jungle_bush": ((1420, 1488, 1489, 1490, 1491, 1492, 1493, 1494, 1495, 1496, 1497, 1499, 1455, 1478, 1479), None),
    "jungle_plant": ((1407, 1412, 1414, 1415, 1416, 1417, 1418, 1419, 1446, 1447), None),
    "mushrooms": ((1401, 1402, 1403, 1404, 1405, 1406), None),
    "reeds": ((1409, 1410, 1411, 1412, 1413, 1414), None),
    "frost_pine": ((1508, 1565, 1566, 1579), ("mix", (238, 244, 250), 0.42)),
    "frost_bush": ((1483, 1484, 1485, 1482, 1486), ("mix", (238, 244, 250), 0.5)),
    "dry_bush": ((1486, 1467, 1469, 1471, 1473, 1483), ("mix", (196, 168, 100), 0.4)),
    "dry_shrub": ((1440, 1441, 1442, 1421, 1422), None),
    "autumn_tree": ((1567, 1575, 1517, 1527, 1528), None),
    "burnt_tree": ((1500, 1501, 1502, 1576), ("dark", 0.32)),
    "burnt_beam": ((1588, 1589, 1590, 1591), ("dark", 0.3)),
    "debris": ((509, 510, 511, 512, 513, 514, 515, 516, 517), ("dark", 0.75)),
    "corpse": ((2227, 2246, 2247, 2248), None),
    "blood": ((2221, 2222, 2224, 2226), ("tint", (175, 25, 25))),
}


def rework(img, mode):
    if mode is None:
        return img
    if mode[0] == "tint":
        return tint(img, mode[1])
    img = img.copy()
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if not a:
                continue
            if mode[0] == "dark":
                k = mode[1]
                px[x, y] = (int(r * k + 8), int(g * k + 5), int(b * k + 4), a)
            else:
                rgb, part = mode[1], mode[2]
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                p = part * (0.4 + 0.6 * lum)   # szron na jasnych miejscach, cienie zostaja
                px[x, y] = tuple(int(v * (1 - p) + m * p) for v, m in zip((r, g, b), rgb)) + (a,)
    return img


# Meble skladane z kilku pol paczki (2026-10-24, audyt precyzji): lozko z paczki to dwie polowki (wezglowie i nogi),
# a stol i oltarz to rogi stolu 2x2 - pojedynczy wycinek wygladal na ucieta grafike.
# COMPOSITES: wiersze numerow, kazdy na swoim polu (grafika w naturalnym rozmiarze, native/klucz.png).
COMPOSITES = {"bed": [[1199], [1200]], "bed_v2": [[1201, 1202]]}
# STITCHED: stol 2x2 zszyty w jedno pole bez skalowania (lewa i gorna polowa blatu + prawy dolny rog z nogami).
STITCHED = {"table": [[1189, 1190], [1191, 1192]], "altar": [[1193, 1194], [1195, 1196]]}
# Obrazy, gobeliny i tkaniny na murach wnetrz (renderer wiesza je na scianach wg pozycji, bez zmian mapy):
# poziome na murach polnocnych, pionowe na zachodnich (native/wall_deco_h_N.png, wall_deco_v_N.png).
WALL_DECO_H = (1225, 1229, 1230, 1221, 1233, 1235, 1237, 1256, 1260, 1264, 1268)
WALL_DECO_V = (1227, 1231, 1232, 1223, 1234, 1236, 1238, 1255, 1259, 1263, 1267)
# 2026-10-23 (Darek: "wszystkie grafiki maja byc z otclienta"): zamiast wlasnych rysunkow odpowiedniki z paczki.
# nazwa -> numer albo (numer, barwa przebarwienia)
OTSP_PROPS = {
    "throne": 1102, "candle": 1242, "carpet": 176, "rug": 176, "broken_wall": 840, "flower_box": 1245,
    "hay_bale": 1118, "ship_mast": 1131, "signpost": 1175, "anvil": 2694, "forge": 1121,
    "fishing_net": (1271, (150, 120, 80)), "fountain": 1122, "street_lamp": 1239, "fence": 964, "door_open": 1022,
    "tall_grass": 1420, "wheat": 1001, "snow_tuft": (1426, (240, 245, 250)), "dry_grass": (1426, (200, 175, 95)),
    "glow_moss": (1446, (120, 240, 200)), "magma": 2298, "bones": 1183, "shells": (1593, (240, 225, 210)),
    "palm": 2584, "cactus": 2583, "cobweb": 1272,
}
OTSP_OBJECTS = {"chest_closed": 1111, "training_dummy": 1275}
# Mury bez odpowiednika: przebarwiona drewniana sciana (burta statku).
TINTED_WALLS = {"ship_hull": ((680, 681, 682, 683), (120, 80, 50))}
TINTED_ROOFS = {"roof_sand": (184, (215, 180, 115)), "roof_sand_top": (183, (215, 180, 115)),
                # osniezona dachowka miasta sniegu
                "roof_snow": (184, (240, 246, 252)), "roof_snow_top": (183, (240, 246, 252))}
# Przedmioty bez wlasnego obrazka: najblizszy przedmiot z paczki, w razie potrzeby przebarwiony.
MORE_ITEMS = {
    # 2026-10-28: produkty nowych potworow i przedmioty tylko z nowych bossow
    "black_feather": (2167, (70, 62, 85)), "worm_slime": 2169, "chitin_plate": 2171, "ectoplasm": (2223, (170, 235, 200)),
    "gargoyle_shard": 2200, "crow_feather_hood": (1941, (80, 65, 110)), "mirewood_rod": 1901,
    # 2026-10-24: luki i kusze paladyna
    "short_bow": 1873, "hunting_bow": 1882, "crossbow": 1884, "elven_bow": 1883, "serpent_bow": 1871,
    "composite_bow": 1877, "heavy_crossbow": 1885, "magma_bow": 1887, "tidal_bow": (1878, (90, 170, 210)),
    "frost_bow": 1889, "glacier_crossbow": 1888, "shadow_bow": (1886, (120, 95, 170)), "dawn_bow": (1879, (255, 225, 120)),
    # 2026-10-23 wieczor: bronie rycerza
    "hand_axe": 1833, "battle_axe": 1826, "mace": 1807, "desert_scimitar": 1723, "barbarian_axe": 1852, "knight_sword": 1762,
    "war_hammer": 1813, "crusader_axe": 1856, "magma_cleaver": 1736, "obsidian_axe": 1845, "coral_warhammer": 2604, "glacier_axe": 1861,
    "frost_longsword": 1746, "umbral_blade": 1749, "skull_axe": 2613, "abyssal_greatsword": 2590, "abyss_hammer": 2608,
    "healing_herb": 2695, "fishing_rod": 1171, "saddle": 2673, "scorpion_tail": 2173, "worm": 2172,
    "boar_tusk": 2174, "hyena_fang": (2174, (235, 225, 200)), "jungle_idol": 2237, "cloth_wraps": 2685,
    "colossus_core": 2163, "croc_leather": (2162, (110, 140, 70)), "frost_pelt": (2162, (200, 225, 255)),
    "yeti_fur": (2162, (245, 245, 250)), "troll_hide": (2162, (120, 110, 70)), "snake_skin": (2162, (150, 170, 80)),
    "mummy_wrap": (2162, (225, 215, 185)), "frozen_heart": (2187, (140, 190, 255)), "glacial_core": 2209,
    "goblin_ear": (2178, (120, 160, 70)), "shadow_essence": (2161, (150, 90, 220)),
    "abyssal_ember": (2164, (170, 90, 255)),
    "blank_rune": (2698, (190, 190, 190)), "healing_rune": 2696, "ultimate_healing_rune": (2696, (120, 255, 160)),
    "fireburst_rune": 2697, "great_fireball_rune": (2697, (255, 150, 40)), "avalanche_rune": 2698,
    "boulder_rune": (2696, (190, 140, 90)), "sudden_death_rune": 2699,
    "challenge_scroll": 1219, "return_scroll": 1220, "exp_scroll": (1219, (255, 215, 90)),
    "strong_health_potion": 2133, "supreme_health_potion": 2134, "ultimate_health_potion": (2129, (200, 40, 90)),
    "strong_mana_potion": (2131, (110, 150, 255)), "supreme_mana_potion": (2131, (90, 90, 230)),
    "ultimate_mana_potion": (2131, (150, 90, 255)), "great_spirit_potion": 2128,
    "ultimate_spirit_potion": (2128, (230, 120, 255)),
    "fists_of_the_abyss": 2709, "frostbound_fists": 2710, "umbral_grips": 2711, "molten_knuckles": (2159, (255, 120, 50)),
}
# Efekty czarow i pociskow (Darek, 23.10: "skile i spele nie maja grafik, paladyn rzuca kulka").
# Efekt: klatki animacji effects/nazwa_N.png; pocisk: 8 kierunkow missiles/nazwa_XY.png (X, Y = 0..2 jak w Tibii:
# 0 = lewo/gora, 1 = srodek, 2 = prawo/dol). nazwa -> numer albo (numer, barwa przebarwienia)
EFFECTS = {
    "blood": 1, "energy": 2, "poof": 3, "sparks": 4, "fire": 6, "water": 7, "earth": 9, "dust": 10,
    "flame": 11, "explosion": 13, "ice": 15, "holy": (2, (255, 230, 120)), "death": (12, (170, 80, 220)),
    "heal": (2, (120, 255, 150)), "haste": (7, (150, 210, 255)),
    # 2026-10-24: reszta efektow paczki - zlote iskry, niebieski wybuch magii, kamienie wyrastajace z ziemi
    "sparkle": 5, "magic": 8, "stones": 14,
}
MISSILES = {
    "arrow": 2, "spear": 1, "fire": 4, "ice": 13, "earth": 5, "energy": (10, (120, 175, 255)), "stone": 12, "star": 6, "knife": 9,
    "holy": (10, (255, 225, 110)), "death": (10, (170, 80, 220)),
    # 2026-10-24: reszta pociskow paczki - czerwona i ognista strzala, belt, mala gwiazdka, trucizna, ciemna gwiazdka
    "arrow_red": 3, "bolt": 7, "star_small": 8, "fire_arrow": 11, "poison": 15, "shadow_star": 19,
}
# Kaluze krwi i ciala (przedmioty paczki zapisane jako jednoklatkowe efekty effects/<nazwa>_0.png);
# plyny w paczce sa niebieskie (kolor nadaje gra), wiec krew i sluz sa przebarwione.
BLOOD, SLIME = (175, 25, 25), (90, 160, 40)
DECALS = {
    "blood_pool": (2221, BLOOD), "blood_small": (2222, BLOOD), "blood_splash": (2224, BLOOD),
    "blood_drops": (2226, BLOOD), "slime_pool": (2221, SLIME), "slime_splash": (2224, SLIME),
    "corpse_human": 2227, "corpse_human_old": 2246, "corpse_bones": 2248, "corpse_goblin": 2234,
    "corpse_beast": 2228, "corpse_centipede": 2240, "corpse_wasp": 2243, "corpse_beetle": 2238,
}
# Pupile i potwory bez stroju w paczce: przebarwiony podobny stroj.
MORE_TINTED = {"cursed_treant": (12, (100, 115, 55))}
PET_TINTED = {"pet_dog": (1, (160, 115, 70)), "pet_fennec": (11, (235, 175, 95))}
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
    "crystal_shard": 2207, "pearl": 2197, "scarab_gem": 2219, "obsidian_shard": 2205, "iron_ore": 2201,
    "bone": 2166, "spider_silk": 2170, "wolf_pelt": 2162,
    "gold_coin": 2192, "platinum_coin": 2191, "crystal_coin": 2218,
    # etap 10: zestaw koralowy (poziom 350)
    "tidal_harpoon": 1828, "wand_of_the_deep": 1801, "rod_of_tides": 1798, "reef_gauntlets": 2153,
    "coral_helmet": 1947, "coral_armor": 2055, "coral_legs": 2078, "coral_shield": 1919,
    # etap 14: dodatkowy plecak
    "backpack": 2099,
    # etap 15: produkty potworow wysp
    "wyvern_scale": 2179, "crab_pincer": 2175, "pirate_hook": 2141, "powder_horn": 2173, "drowned_ring": 2151,
    "mist_essence": 2161, "drake_horn": 2174,
}
# Stroje: (numer, kolory glowa/tulow/nogi/stopy dla strojow z maska kolorow albo None).
MONSTERS = {
    "boar": (6, None), "goblin": (7, None), "forest_spider": (8, None), "scorpion": (13, None),
    "wraith_knight": (39, None), "sand_wraith": (40, None), "shadow_stalker": (37, None),
    "frost_witch": (35, None), "void_priest": (34, None), "fire_elemental": (36, None),
    "abyssal_demon": (46, None), "cave_troll": (12, None), "jungle_serpent": (29, None),
    "crocodile": (25, None), "storm_harpy": (14, None), "temple_guardian": (22, None), "pharaoh": (23, None),
    "goblin_shaman": (10, None),
    "bandit": (19, None), "jungle_hunter": (16, ((150, 90, 50), (70, 120, 50), (90, 70, 40), (60, 40, 30))),
    "mummy": (17, ((230, 220, 190), (220, 210, 180), (210, 200, 170), (200, 190, 160))),
    # 2026-10-24: wlasne stroje z paczki zamiast przebarwien (duch, krab, blady upior, skrzydlaty demon)
    "mist_wraith": (5, None), "coral_crab": (31, None), "morvena": (41, None), "morgath": (2, None),
    # 2026-10-28: nowe potwory na stroje paczki, ktorych gra jeszcze nie uzywala (latarnik, akolita, czerwie,
    # wirujace ostrze, iskra, granatowy gargulec, zielona i czerwona zjawa) oraz ludzie ze strojow z maska kolorow
    "barrow_lantern": (4, None), "sand_devourer": (24, None), "blade_warden": (27, None), "spark_wisp": (28, None),
    "rime_crawler": (30, None), "dusk_gargoyle": (38, None), "bog_wraith": (42, None), "crimson_shade": (43, None),
    "striped_prowler": (11, None),
    "candle_acolyte": (20, ((110, 70, 150), (70, 45, 100), (110, 40, 50), (40, 35, 40))),
    "tunnel_fence": (21, ((190, 50, 45), (60, 85, 150), (80, 80, 85), (60, 45, 35))),
    "snare_setter": (44, ((150, 110, 60), (85, 135, 60), (115, 90, 55), (75, 55, 35))),
    "quarry_brute": (45, ((175, 175, 170), (200, 125, 60), (125, 105, 85), (85, 65, 45))),
    "dune_zealot": (18, ((245, 235, 205), (250, 245, 225), (230, 195, 100), (160, 125, 70))),
}
# Potwory bez wlasnego stroju w paczce: podobny stroj przebarwiony (barwa mnozona przez jasnosc piksela).
TINTED = {
    "skeleton": (3, (225, 215, 190)),
    # 2026-10-27: szczury z kanalow pod miastami (paczka nie ma szczura - przebarwiony kot)
    "sewer_rat": (1, (125, 105, 85)), "giant_rat": (1, (95, 80, 65)),
    "wolf": (1, (150, 140, 130)), "snow_wolf": (1, (235, 240, 255)), "hyena": (11, (200, 160, 110)),
    "yeti": (12, (240, 245, 255)), "crystal_golem": (12, (150, 220, 240)), "ice_golem": (12, (190, 220, 255)),
    "lava_golem": (12, (255, 120, 50)), "ruin_colossus": (12, (190, 180, 160)), "sand_titan": (12, (230, 200, 130)),
    "grimtooth": (7, (200, 90, 70)), "frost_queen": (35, (200, 235, 255)), "frost_giant": (46, (150, 190, 255)),
    "ice_dragon": (25, (150, 210, 255)), "bone_dragon": (25, (235, 225, 200)), "ignaroth": (25, (255, 110, 60)),
    "hrimgar": (39, (190, 230, 255)), "khazuul": (37, (210, 170, 90)), "abyss_lord": (46, (190, 120, 255)),
    # etap 8: expowiska na wyspach i Smoczym Grzbiecie
    "wyvern": (25, (130, 180, 90)), "drowned_sailor": (3, (130, 190, 200)),
    "pirate_cutthroat": (19, (210, 100, 80)), "pirate_gunner": (19, (110, 110, 140)),
    "magma_drake": (25, (235, 80, 25)),
    # etap 10: bossowie nowych lochow
    "wyvern_matriarch": (25, (80, 140, 50)), "bloodbeard": (19, (170, 50, 50)), "skaldra": (35, (160, 210, 255)),
    "pyrrax": (46, (255, 120, 30)),
    # 2026-10-23: orki z zajetych wiosek (paczka nie ma orkow - przebarwiony goblin, okultysta i jaszczuroczlek)
    "orc_warrior": (7, (120, 150, 70)), "orc_shaman": (10, (110, 160, 90)), "orc_berserker": (7, (180, 110, 70)),
    "orc_warlord": (46, (100, 130, 60)),
    # 2026-10-28: odmiany strojow paczki dla nowych potworow (barwa wyraznie inna niz u potworow na tym samym stroju)
    "burrow_larva": (29, (150, 120, 80)), "carrion_crow": (9, (95, 85, 105)), "cave_centipede": (13, (125, 110, 150)),
    "frost_boar": (6, (230, 238, 250)), "swamp_hornet": (14, (170, 205, 70)), "gilded_beetle": (8, (235, 195, 80)),
    "frost_centipede": (13, (170, 225, 255)), "stone_sentinel": (22, (165, 165, 155)), "sand_gargoyle": (39, (215, 185, 125)),
    "ember_beetle": (8, (240, 105, 40)), "ash_magus": (15, (150, 145, 150)), "dune_drake": (25, (215, 175, 110)),
    "storm_wisp": (28, (190, 215, 255)), "rime_gargoyle": (39, (195, 230, 255)), "shadow_centipede": (13, (95, 75, 125)),
    "abyss_crawler": (31, (120, 55, 150)), "winged_spawn": (2, (135, 105, 185)), "obsidian_sentinel": (22, (70, 60, 90)),
    # bossowie
    "wroniec": (9, (60, 45, 85)), "szlamiara": (42, (70, 120, 55)), "velmira": (43, (165, 25, 45)),
    "suweren": (27, (230, 190, 90)),
}
CHARACTERS = {
    "paladin": (19, None),
    "druid": (32, None),
    "sorcerer": (15, None),
    "monk": (26, None),  # brazowa szata z kapturem (wczesniej stroj 16 z czerwona czapka)
    "knight": (3, None),  # rycerz w zbroi (2026-10-23 wieczor; szkielet dostal przebarwienie koloru kosci)
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
    # 2026-10-23: krol Aurelian i straznicy bram (klucze guard_N)
    "king": (17, ((230, 190, 60), (120, 40, 140), (100, 30, 110), (210, 170, 60))),
    "guard": (19, ((150, 155, 165), (70, 90, 140), (120, 125, 135), (60, 50, 40))),
}
_NPC_CSV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                        "shared/src/main/resources/maps/world/npcs.csv")
NPCS = {}
for _line in open(_NPC_CSV, encoding="utf-8"):
    _key = _line.split(";")[0]
    _cols = _line.split(";")
    if not _key or _key.startswith("#"):
        continue
    if _key.split("_")[-1] in _ROLE:
        NPCS[_key] = _ROLE[_key.split("_")[-1]]
    elif _key.startswith("guard_"):
        NPCS[_key] = _ROLE["guard"]
    elif len(_cols) > 2 and _cols[2].lower() in _ROLE:   # np. gull_ferryman (CAPTAIN), isle_guide
        NPCS[_key] = _ROLE[_cols[2].lower()]
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


# Grafiki w naturalnym rozmiarze jak w Tibii (2026-10-24, Darek: "meble precyzyjne"): obiekty wieksze niz pole
# (64x32, 32x64, 64x64) nie sa sciskane do 32 px, tylko rysowane w calosci od prawego dolnego rogu pola
# (native/klucz.png i native/klucz_vN.png, plotno 64x64). Drzewa, wiatrak i manekin zostaja po staremu.
NATIVE_SKIP = {"tree", "pine", "palm", "dead_tree", "jungle_tree", "frost_pine", "autumn_tree", "burnt_tree", "ship_mast", "windmill", "training_dummy", "door_open",
               "carpet", "rug", "magma", "hole", "rope_spot", "sewer_grate", "stairs_up", "stairs_down", "ladder", "campfire"}


def grid(spr, things, rows):
    """Kilka przedmiotow paczki na sasiednich polach (kazdy od prawego dolnego rogu swojego pola)."""
    h, w = len(rows), max(len(r) for r in rows)
    out = Image.new("RGBA", (32 * w, 32 * h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, i in enumerate(row):
            img = ot.compose(spr, things["item"][i]).convert("RGBA")
            # pole przedmiotu to prawy dolny kwadrat jego grafiki
            out.alpha_composite(img.crop((max(0, img.width - 32), max(0, img.height - 32), img.width, img.height)),
                                (32 * x, 32 * y))
    return out


def stitch(img):
    """Mebel 2x2 zszyty w jedno pole: lewa/gorna polowa i prawa/dolna polowa, piksele bez skalowania."""
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for dx, sx in ((0, 0), (16, img.width - 16)):
        for dy, sy in ((0, 0), (16, img.height - 16)):
            out.alpha_composite(img.crop((sx, sy, sx + 16, sy + 16)), (dx, dy))
    return out


def native_canvas(img):
    """Plotno 64x64 z obrazkiem przy prawym dolnym rogu (None, gdy obiekt miesci sie w polu albo jest za duzy)."""
    if img.width <= 32 and img.height <= 32:
        return None
    if img.width > 64 or img.height > 64:
        return None
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.alpha_composite(img.convert("RGBA"), (64 - img.width, 64 - img.height))
    return out


def open_window(img):
    """Okno otwarte z zamknietego: szyba (jasnoniebieskie piksele) zamieniona na ciemne wnetrze."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and b > 120 and b > r + 40 and g > r:
                d = 30 + (y % 3) * 4
                px[x, y] = (d + 10, d, d - 8, 255)
    return out


def open_chest(closed):
    """Otwarta skrzynia z zamknietej (1111): wieko odchylone do tylu, w srodku zloto (plotno 64x64)."""
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    lid = closed.crop((16, 1, 53, 22))
    body = closed.copy()
    px = body.load()
    for y in range(4, 21):
        for x in range(20, 48):
            if px[x, y][3] == 0:
                continue
            d = (y - 4) / 16
            px[x, y] = (int(40 + 30 * d), int(20 + 14 * d), int(10 + 6 * d), 255)
    # monety w rzedach: jasny gorny lewy piksel, ciemny prawy dolny
    for row, y in enumerate(range(13, 21, 2)):
        for x in range(21 + (row % 2) * 2, 46, 4):
            for dx, dy, c in ((0, 0, (255, 240, 150)), (1, 0, (246, 206, 80)), (2, 0, (232, 180, 48)),
                              (0, 1, (232, 180, 48)), (1, 1, (214, 156, 36)), (2, 1, (170, 110, 20))):
                if px[x + dx, y + dy][3]:
                    px[x + dx, y + dy] = c + (255,)
    out.alpha_composite(body, (0, 32))
    flat = lid.resize((lid.width, 9), Image.NEAREST)
    lp = flat.load()
    for y in range(flat.height):
        for x in range(flat.width):
            r, g, b, a = lp[x, y]
            if a:
                lp[x, y] = (int(r * 0.8), int(g * 0.75), int(b * 0.75), a)
    out.alpha_composite(flat, (16, 25))
    return out


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
        if img.width > 32:   # podloza gor w paczce sa 2x2 z jednym wypelnionym polem
            b = img.getbbox()
            img = img.crop((b[0], b[1], b[0] + 32, b[1] + 32))
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
    for name, i in TOWN_OBJECTS.items():
        save(trim_square(ot.compose(spr, things["item"][i])), f"{art}/tiles/{name}.png"); n += 1
    for name, i in TOWN_GROUND.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/tiles/{name}.png"); n += 1
    for name, shapes in MOUNTAINS.items():
        for shape, ids in zip(WALL_SHAPES, shapes):
            imgs = []
            for i in ids:
                t = things["item"][i]
                imgs += [ot.compose(spr, t, x=px, y=py).convert("RGBA") for py in range(t["py"]) for px in range(t["px"])]
            for k, img in enumerate(imgs):
                sq = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
                sq.alpha_composite(img, (64 - img.width, 64 - img.height))
                save(sq, f"{art}/walls/{name}_{shape}{'' if k == 0 else '_' + str(k)}.png"); n += 1
    for name, ids in TOWN_WALLS.items():
        for shape, i in zip(WALL_SHAPES, ids):
            save(ot.compose(spr, things["item"][i]), f"{art}/walls/{name}_{shape}.png"); n += 1
    def wall_with(spec):
        base, over = spec if isinstance(spec, tuple) else (spec, None)
        img = ot.compose(spr, things["item"][base]).convert("RGBA")
        if over is not None:
            o = ot.compose(spr, things["item"][over]).convert("RGBA")
            img.alpha_composite(o, (img.width - o.width, img.height - o.height))
        return img
    for name, specs in WINDOW_STATES.items():
        for shape, spec in zip(("vwin", "vwin_open", "hwin", "hwin_open"), specs):
            save(wall_with(spec), f"{art}/walls/{name}_{shape}.png"); n += 1
    for name, sets in ALT_WALLS.items():
        for k, ids in enumerate(sets, 1):
            imgs = [ot.compose(spr, things["item"][i]).convert("RGBA") for i in ids[:4]]
            imgs += [ot.compose(spr, things["item"][i]).convert("RGBA") if i else imgs[j] for j, i in zip((0, 1), ids[4:])]
            imgs += [open_window(imgs[4]) if ids[4] else imgs[0], open_window(imgs[5]) if ids[5] else imgs[1]]
            for shape, img in zip(ALT_SHAPES, imgs):
                save(img, f"{art}/walls/{name}_alt{k}_{shape}.png"); n += 1
    for name in set(WALLS) | set(TOWN_WALLS):
        for shape, i in zip(DOOR_SHAPES, DOORS.get(name, DOORS["stone_wall"])):
            save(ot.compose(spr, things["item"][i]), f"{art}/walls/{name}_{shape}.png"); n += 1
    for name, ids in VARIANTS.items():
        for k, i in enumerate(ids, 2):
            save(trim_square(ot.compose(spr, things["item"][i])), f"{art}/tiles/{name}_v{k}.png"); n += 1
    for name, (ids, mode) in CLIMATE.items():
        for k, i in enumerate(ids, 1):
            img = trim_square(rework(ot.compose(spr, things["item"][i]).convert("RGBA"), mode))
            save(img, f"{art}/tiles/{name}{'' if k == 1 else '_v' + str(k)}.png"); n += 1
    for name, i in EXTRA_OBJECTS.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/objects/{name}.png"); n += 1
    for name, i in ROOFS.items():
        img = ot.compose(spr, things["item"][i]).convert("RGBA")
        if name in ROOF_TONE:
            sat, light = ROOF_TONE[name]
            px = img.load()
            for y in range(img.height):
                for x in range(img.width):
                    r, g, b, a = px[x, y]
                    grey = 0.3 * r + 0.59 * g + 0.11 * b
                    c = [min(255, int((grey + (v - grey) * sat) * light)) for v in (r, g, b)]
                    px[x, y] = (c[0], c[1], c[2], a)
        save(img, f"{art}/objects/{name}.png"); n += 1
    for name, i in ITEMS.items():
        save(ot.compose(spr, things["item"][i]), f"{art}/items/{name}.png"); n += 1
    for name, first in BORDERS.items():
        for k, edge in enumerate(BORDER_EDGES):
            save(ot.compose(spr, things["item"][first + k]), f"{art}/borders/{name}_{edge}.png"); n += 1
    for name, spec in OTSP_PROPS.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        img = trim_square(ot.compose(spr, things["item"][i]).convert("RGBA"))
        save(tint(img, rgb) if rgb else img, f"{art}/tiles/{name}.png"); n += 1
    for name, i in OTSP_OBJECTS.items():
        save(trim_square(ot.compose(spr, things["item"][i]).convert("RGBA")), f"{art}/objects/{name}.png"); n += 1
    for name, (ids, rgb) in TINTED_WALLS.items():
        for shape, i in zip(WALL_SHAPES, ids):
            save(tint(ot.compose(spr, things["item"][i]).convert("RGBA"), rgb), f"{art}/walls/{name}_{shape}.png"); n += 1
    for name, (i, rgb) in TINTED_ROOFS.items():
        save(tint(ot.compose(spr, things["item"][i]).convert("RGBA"), rgb), f"{art}/objects/{name}.png"); n += 1
    for name, spec in MORE_ITEMS.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        img = ot.compose(spr, things["item"][i]).convert("RGBA")
        if img.width > 32:   # przedmiot 64x64 (np. figurka): przyciety i zmniejszony do pola plecaka
            img = trim_square(img)
            img = img.resize((32, 32), Image.NEAREST) if img.width > 32 else img
        save(tint(img, rgb) if rgb else img, f"{art}/items/{name}.png"); n += 1
    for name, spec in EFFECTS.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        t = things["effect"][i]
        for f in range(t["frames"]):
            img = ot.compose(spr, t, frame=f).convert("RGBA")
            save(tint(img, rgb) if rgb else img, f"{art}/effects/{name}_{f}.png"); n += 1
    for name, spec in DECALS.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        img = ot.compose(spr, things["item"][i]).convert("RGBA")
        if img.width != img.height:   # 32x64: kwadrat 64x64, pole w prawym dolnym rogu jak efekty 2x2
            sq = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            sq.alpha_composite(img, (64 - img.width, 64 - img.height))
            img = sq
        save(tint(img, rgb) if rgb else img, f"{art}/effects/{name}_0.png"); n += 1
    for name, spec in MISSILES.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        t = things["missile"][i]
        for py in range(3):
            for px in range(3):
                if px == 1 and py == 1:
                    continue
                img = ot.compose(spr, t, x=px, y=py).convert("RGBA")
                save(tint(img, rgb) if rgb else img, f"{art}/missiles/{name}_{px}{py}.png"); n += 1
    south = ot.DIRS.index("down")
    for name, (i, rgb) in MORE_TINTED.items():
        n += save_outfit(spr, things["outfit"][i], south, None, f"{art}/monsters/{name}", rgb)
    for name, (i, rgb) in PET_TINTED.items():
        n += save_outfit(spr, things["outfit"][i], south, None, f"{art}/pets/{name}", rgb)
    for folder, table in (("monsters", MONSTERS), ("npcs", NPCS), ("pets", PETS)):
        for name, (i, colors) in table.items():
            n += save_outfit(spr, things["outfit"][i], south, colors, f"{art}/{folder}/{name}")
    for name, (i, rgb) in TINTED.items():
        n += save_outfit(spr, things["outfit"][i], south, None, f"{art}/monsters/{name}", rgb)
    for voc, (i, colors) in CHARACTERS.items():
        for d, dname in enumerate(ot.DIRS):
            n += save_outfit(spr, things["outfit"][i], d, colors, f"{art}/characters/{voc}_{dname}")
    for name in ("pet_cat", "pet_raven", "pet_dog", "pet_fennec"):   # ikona pupila w plecaku = jego postac
        src = Image.open(f"{art}/pets/{name}.png").convert("RGBA")
        save(src.resize((32, 32), Image.NEAREST) if src.width != 32 else src, f"{art}/items/{name}.png"); n += 1
    # obiekty w naturalnym rozmiarze
    def native(name, i, rgb=None, suffix=""):
        if name in NATIVE_SKIP:
            return 0
        img = ot.compose(spr, things["item"][i]).convert("RGBA")
        c = native_canvas(tint(img, rgb) if rgb else img)
        if c is None:
            return 0
        save(c, f"{art}/native/{name}{suffix}.png")
        return 1
    os.makedirs(f"{art}/native", exist_ok=True)
    for name, i in OBJECTS.items():
        n += native(name, i)
    for name, rows in COMPOSITES.items():
        img = grid(spr, things, rows)
        save(trim_square(img), f"{art}/tiles/{name}.png")
        save(native_canvas(img), f"{art}/native/{name}.png"); n += 2
    for name, rows in STITCHED.items():
        save(stitch(grid(spr, things, rows)), f"{art}/tiles/{name}.png"); n += 1
    for prefix, ids in (("wall_deco_h", WALL_DECO_H), ("wall_deco_v", WALL_DECO_V)):
        for k, i in enumerate(ids):
            save(native_canvas(ot.compose(spr, things["item"][i]).convert("RGBA")), f"{art}/native/{prefix}_{k}.png"); n += 1
    for name, i in TOWN_OBJECTS.items():
        n += native(name, i)
    for name, spec in OTSP_PROPS.items():
        i, rgb = spec if isinstance(spec, tuple) else (spec, None)
        n += native(name, i, rgb)
    for name, ids in VARIANTS.items():
        for k, i in enumerate(ids, 2):
            n += native(name, i, None, f"_v{k}")
    for name, (ids, mode) in CLIMATE.items():
        if name in NATIVE_SKIP:
            continue
        for k, i in enumerate(ids, 1):
            c = native_canvas(rework(ot.compose(spr, things["item"][i]).convert("RGBA"), mode))
            if c is not None:
                save(c, f"{art}/native/{name}{'' if k == 1 else '_v' + str(k)}.png"); n += 1
    closed = ot.compose(spr, things["item"][OTSP_OBJECTS["chest_closed"]]).convert("RGBA")
    save(native_canvas(closed), f"{art}/native/chest_closed.png")
    save(open_chest(closed), f"{art}/native/chest_open.png"); n += 2
    print("Zapisano", n, "grafik do", art)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else
         os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "art"))
