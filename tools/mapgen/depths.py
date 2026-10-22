"""Etap 9 rozbudowy w stylu Tibii: wielopoziomowe lochy 300+ pod wyspami i pod Dolina Wiwern.

Kazdy loch to kilka pieter w dol polaczonych schodami, a miedzy glebszymi pietrami takze dziura i drabina.
Pietra rysuje generate_world.dungeon (przekazywana jako argument, zeby nie bylo importu w kolo);
kazdy loch ma wlasne ziarno, wiec reszta swiata sie nie zmienia.
"""

import random

# schody w dol na powierzchni wysp i doliny (popiol, przeklete trawy, trawa)
LEGEND = [
    ('Ю', 'ash', '-', 'stairs_down', '-'),
    ('Я', 'blighted_grass', '-', 'stairs_down', '-'),
    ('Ш', 'grass', '-', 'stairs_down', '-'),
]
WALKABLE = set('ЮЯШ')

# style pieter: sciana, podloga, ozdoby, schody w gore, schody w dol
CAVE = ('N', 'c', 'j8j8', '(', ')')
LAVA = ('N', 'c', '9j99', '(', ')')
ICE = ('E', 'e', 'GG$', '!', '?')
ABYSS = ('ι', 'θ', 'ξκξ', 'μ', 'λ')

# loch: nazwa, symbol wejscia na powierzchni, styl, pietra [(z, obszar, punkty)], dziura miedzy pietrami (z), spawny
DUNGEONS = [
    ('Grota Przemytnikow', '^', CAVE, [
        (-1, (32, 272, 64, 295), [(50, 284), (36, 276), (60, 292), (40, 292)]),
        (-2, (32, 270, 64, 296), [(40, 292), (58, 274), (34, 282)]),
        (-3, (32, 272, 62, 294), [(34, 282), (50, 278), (56, 290)]),
    ], -2, [
        'pirate_cutthroat;40;278;-1;4;3;240', 'pirate_cutthroat;58;288;-1;4;2;240', 'pirate_gunner;44;292;-1;3;2;300',
        'drowned_sailor;54;276;-2;4;3;240', 'drowned_sailor;38;286;-2;3;2;240', 'pirate_gunner;60;282;-2;3;2;300',
        'pirate_cutthroat;50;280;-3;3;3;240', 'pirate_gunner;56;288;-3;3;2;300', 'bloodbeard;54;290;-3;2;1;3600',
    ], 501),
    ('Serce Wulkanu', 'Ю', LAVA, [
        (-1, (296, 176, 332, 206), [(316, 192), (300, 180), (328, 200), (304, 204)]),
        (-2, (296, 174, 332, 206), [(304, 204), (324, 178), (312, 190)]),
        (-3, (298, 176, 330, 204), [(312, 190), (300, 200), (326, 196)]),
        (-4, (300, 178, 330, 204), [(326, 196), (306, 182), (316, 198)]),
    ], -3, [
        'magma_drake;300;184;-1;4;2;360', 'magma_drake;324;200;-1;4;2;360',
        'magma_drake;322;180;-2;4;2;360', 'abyssal_demon;308;196;-2;3;1;420',
        'abyssal_demon;302;198;-3;3;2;420', 'magma_drake;322;194;-3;3;2;360',
        'magma_drake;308;184;-4;4;2;360', 'abyssal_demon;318;196;-4;3;2;420', 'pyrrax;314;198;-4;2;1;3600',
    ], 502),
    ('Lodowe Groty', '{', ICE, [
        (-1, (292, 26, 330, 52), [(312, 40), (296, 30), (326, 48)]),
        (-2, (292, 24, 330, 52), [(326, 48), (300, 46), (320, 28)]),
        (-3, (294, 26, 328, 50), [(320, 28), (304, 36), (316, 46)]),
    ], -2, [
        'frost_giant;298;32;-1;4;2;300', 'frost_giant;322;44;-1;4;2;300',
        'ice_dragon;302;44;-2;4;2;360', 'frost_giant;318;32;-2;3;2;300',
        'ice_dragon;306;36;-3;4;2;360', 'ice_dragon;314;44;-3;3;1;360', 'skaldra;316;47;-3;2;1;3600',
    ], 503),
    ('Krypta Mgiel', 'Я', ABYSS, [
        (-1, (230, 272, 272, 298), [(252, 288), (236, 278), (266, 294), (240, 296)]),
        (-2, (230, 272, 272, 298), [(240, 296), (262, 278), (248, 286)]),
        (-3, (232, 274, 270, 298), [(248, 286), (236, 292), (264, 290)]),
    ], -2, [
        'mist_wraith;238;280;-1;4;3;300', 'mist_wraith;264;292;-1;4;2;300',
        'shadow_stalker;260;280;-2;4;2;300', 'mist_wraith;246;292;-2;4;2;300',
        'mist_wraith;238;290;-3;4;2;300', 'sand_titan;262;288;-3;3;1;360', 'morvena;265;291;-3;2;1;3600',
    ], 504),
    ('Gniazdo Wiwern', 'Ш', CAVE, [
        (-1, (8, 100, 40, 116), [(28, 106), (12, 104), (36, 114), (16, 114)]),
        (-2, (8, 100, 40, 118), [(16, 114), (34, 104), (24, 110)]),
    ], None, [
        'wyvern;14;106;-1;3;2;240', 'wyvern;34;112;-1;3;2;240', 'wyvern;30;106;-2;3;3;240', 'wyvern;18;112;-2;3;2;240', 'wyvern_matriarch;24;111;-2;2;1;3600',
    ], 505),
]

# region podziemi Doliny Wiwern (wyspy maja regiony na wszystkich pietrach z world_geo)
REGIONS = ['Gniazdo Wiwern;8;100;40;118;-2;-1;300;360']

# grunt wokol wejscia (pola obok schodow musza byc wolne - tam staje postac wracajaca z lochu)
GROUND = {'^': 'y', '\u042e': '\u03b3', '{': 's', '\u042f': '\u03b1', '\u0428': '.'}


def depths(floors, transitions, dungeon):
    """Rysuje lochy, dopisuje przejscia; zwraca (spawny, wejscia na powierzchni)."""
    spawns, entrances = [], []
    for name, entry, (wall, floor, decor, up, down), levels, hole_z, monsters, seed in DUNGEONS:
        rng = random.Random(seed)
        last = levels[-1][0]
        for z, area, pts in levels:
            marks = {pts[0]: up}
            if z > last:
                marks[pts[-1]] = down
            # dziura w dol zamiast schodow, drabina z powrotem
            if hole_z is not None and z == hole_z:
                marks[pts[-1]] = 'O'
            if hole_z is not None and z == hole_z - 1:
                marks[pts[0]] = 'H'
            dungeon(floors[z], area, pts, wall, floor, decor, 3, marks, rng)
            ex, ey = pts[0]
            if z == -1:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        floors[0][ey + dy][ex + dx] = GROUND[entry]
                floors[0][ey][ex] = entry
                entrances.append((name, (ex, ey)))
                transitions += [(ex, ey, 0, 'STAIRS', ex, ey + 1, -1), (ex, ey, -1, 'STAIRS', ex + 1, ey, 0)]
            elif hole_z is not None and z == hole_z - 1:
                transitions += [(ex, ey, z + 1, 'HOLE', ex + 1, ey, z), (ex, ey, z, 'LADDER', ex, ey - 1, z + 1)]
            else:
                transitions += [(ex, ey, z + 1, 'STAIRS', ex, ey + 1, z), (ex, ey, z, 'STAIRS', ex + 1, ey, z + 1)]
        spawns.append(f'# {name}')
        spawns += monsters
    return spawns, entrances
