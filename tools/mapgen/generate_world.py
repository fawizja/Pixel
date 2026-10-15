#!/usr/bin/env python3
"""Generator swiata gry: 4 miasta w pasach biomow (polnoc -> poludnie):

    Frostholm (snieg)  ->  Varden (umiarkowany)  ->  Selvaran (rownik)  ->  Qasirah (pustynia)

Uruchom z katalogu repozytorium PO generate_varden.py:
    python3 tools/mapgen/generate_world.py
Region Varden jest kopiowany 1:1 z maps/varden (przesuniety o VARDEN_OFFSET), wiec mapa Varden
uzywana w testach i ten fragment swiata sa identyczne. Wynik: shared/src/main/resources/maps/world/.
Generator jest deterministyczny (stale ziarno).
"""
import os
import random
from collections import deque

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'shared', 'src', 'main', 'resources', 'maps')
VARDEN = os.path.join(ROOT, 'varden')
OUT = os.path.join(ROOT, 'world')

W, H = 256, 232
OLD_W = 160              # zachodni kontynent (losowanie terenu jak przed rozszerzeniem mapy)
STRAIT_X0, STRAIT_X1 = 160, 167   # ciesnina miedzy kontynentami
EAST_X0 = 168
VX, VY = 48, 64          # przesuniecie regionu Varden (musi zgadzac sie z WorldLayout.VARDEN_OFFSET_*)
VW, VH = 64, 48
FLOORS = list(range(-5, 6))

# pasy biomow (y): Frostholm 0..63, Varden 64..111, Selvaran 112..171, Qasirah 172..231
NORTH_END, TEMPERATE_END, JUNGLE_END = 63, 111, 171

rnd = random.Random(20261015)
rnd_east = random.Random(20261016)   # osobna sekwencja: zachod pozostaje bez zmian

# symbole legendy swiata (legend.csv = legenda Varden + ponizsze)
EXTRA_LEGEND = [
    ('s', 'snow', '-', '-', '-'),
    ('v', 'snow', 'snow_tuft', '-', '-'),
    ('S', 'snow', '-', 'pine', '-'),
    ('n', 'snow', '-', 'snow_rock', '-'),
    ('i', 'ice', '-', '-', '-'),
    ('e', 'frozen_floor', '-', '-', '-'),
    ('E', 'frozen_floor', '-', '-', 'ice_wall'),
    ('G', 'frozen_floor', '-', 'ice_crystal', '-'),
    ('A', 'snow', '-', '-', 'log_wall'),
    ('J', 'jungle_grass', '-', '-', '-'),
    ('h', 'jungle_grass', 'fern', '-', '-'),
    ('P', 'jungle_grass', '-', 'palm', '-'),
    ('U', 'jungle_grass', '-', 'tree', '-'),
    ('V', 'swamp', '-', '-', '-'),
    ('M', 'stone_floor', '-', '-', 'mossy_wall'),
    ('N', 'cave_floor', '-', '-', 'mossy_wall'),
    ('y', 'sand', '-', '-', '-'),
    ('o', 'sand', 'dry_grass', '-', '-'),
    ('K', 'sand', '-', 'cactus', '-'),
    ('p', 'sand', '-', 'palm', '-'),
    ('Q', 'sand', '-', 'sandstone_rock', '-'),
    ('X', 'sandstone_floor', '-', '-', '-'),
    ('Z', 'sandstone_floor', '-', '-', 'sandstone_wall'),
    ('z', 'tomb_floor', '-', '-', '-'),
    ('I', 'tomb_floor', '-', '-', 'sandstone_wall'),
    ('[', 'stone_floor', '-', 'stairs_up', '-'),
    (']', 'stone_floor', '-', 'stairs_down', '-'),
    ('{', 'snow', '-', 'stairs_down', '-'),
    ('|', 'jungle_grass', '-', 'stairs_down', '-'),
    ('^', 'sand', '-', 'stairs_down', '-'),
    ('!', 'frozen_floor', '-', 'stairs_up', '-'),
    ('?', 'frozen_floor', '-', 'stairs_down', '-'),
    ('&', 'tomb_floor', '-', 'stairs_up', '-'),
    ('*', 'tomb_floor', '-', 'stairs_down', '-'),
    ('@', 'tomb_floor', '-', 'wall_torch', '-'),
    ('$', 'frozen_floor', '-', 'wall_torch', '-'),
    # etap 10: dziury, drabiny, miejsca na line i zrodla swiatla w glebokich lochach
    ('0', 'frozen_floor', '-', 'hole', '-'),
    ('1', 'frozen_floor', '-', 'ladder', '-'),
    ('2', 'frozen_floor', '-', 'rope_spot', '-'),
    ('4', 'cave_floor', '-', 'rope_spot', '-'),
    ('5', 'tomb_floor', '-', 'hole', '-'),
    ('7', 'tomb_floor', '-', 'rope_spot', '-'),
    ('8', 'cave_floor', '-', 'glow_moss', '-'),
    ('9', 'cave_floor', '-', 'magma', '-'),
    # wschodni kontynent (endgame 230-1000)
    ('\u03b1', 'blighted_grass', '-', '-', '-'),
    ('\u03c0', 'blighted_grass', 'tall_grass', '-', '-'),
    ('\u03b2', 'blighted_grass', '-', 'dead_tree', '-'),
    ('\u03b3', 'ash', '-', '-', '-'),
    ('\u03b4', 'ash', '-', 'obsidian_rock', '-'),
    ('\u03b5', 'lava', '-', '-', '-'),
    ('\u03b6', 'dark_sand', '-', '-', '-'),
    ('\u03b7', 'dark_sand', 'bones', '-', '-'),
    ('\u03c1', 'dark_sand', '-', 'sandstone_rock', '-'),
    ('\u03bd', 'dark_sand', '-', 'stairs_down', '-'),
    ('\u03b8', 'abyss_floor', '-', '-', '-'),
    ('\u03b9', 'abyss_floor', '-', '-', 'obsidian_wall'),
    ('\u03ba', 'abyss_floor', '-', 'magma', '-'),
    ('\u03bb', 'abyss_floor', '-', 'stairs_down', '-'),
    ('\u03bc', 'abyss_floor', '-', 'stairs_up', '-'),
    ('\u03be', 'abyss_floor', '-', 'wall_torch', '-'),
]
WALKABLE = set('.,f=:b_-RDdlLwcjk<>()}HO' + 'sviehJyoXz[]{|^!?&*@$' + '0124578'
               + '\u03b1\u03c0\u03b3\u03b6\u03b7\u03bd\u03b8\u03bb\u03bc\u03be')


def grid(fill=' '):
    return [[fill] * W for _ in range(H)]


def rect(g, x0, y0, x1, y1, ch):
    for y in range(max(0, y0), min(H - 1, y1) + 1):
        for x in range(max(0, x0), min(W - 1, x1) + 1):
            g[y][x] = ch


def box(g, x0, y0, x1, y1, wall, floor):
    rect(g, x0, y0, x1, y1, wall)
    rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, floor)


def reachable(g, start):
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and g[n[1]][n[0]] in WALKABLE:
                seen.add(n)
                q.append(n)
    return seen


# ------------------------------------------------------------------ teren powierzchni

def biome_at(y, r=None):
    """Pas biomu z poszarpana granica (szum)."""
    j = (r or rnd).randint(-2, 2)
    if y + j <= NORTH_END:
        return 'north'
    if y + j <= TEMPERATE_END:
        return 'temperate'
    if y + j <= JUNGLE_END:
        return 'jungle'
    return 'desert'


def east_terrain(biome, edge, rng):
    """Wschodni kontynent: te same pasy, ale ziemie zniszczone (endgame)."""
    r = rng.random()
    if biome == 'north':
        if edge or r < 0.07:
            return 'n'
        return 'S' if r < 0.13 else ('i' if r < 0.17 else ('v' if r < 0.27 else 's'))
    if biome == 'temperate':
        if edge or r < 0.11:
            return '\u03b2'
        return 'T' if r < 0.15 else ('\u03c0' if r < 0.30 else '\u03b1')
    if biome == 'jungle':
        if edge or r < 0.07:
            return '\u03b4'
        return '\u03b4' if r < 0.09 else '\u03b3'
    if edge or r < 0.04:
        return '\u03c1'
    return '\u03b7' if r < 0.10 else ('\u03c1' if r < 0.12 else '\u03b6')


def terrain(biome, edge):
    r = rnd.random()
    if biome == 'north':
        if edge or r < 0.10:
            return 'S'
        return 'v' if r < 0.22 else ('n' if r < 0.25 else 's')
    if biome == 'temperate':
        if edge or r < 0.12:
            return 'T'
        return ',' if r < 0.24 else ('f' if r < 0.27 else '.')
    if biome == 'jungle':
        if edge or r < 0.16:
            return 'U' if r < 0.5 else 'P'
        return 'h' if r < 0.32 else 'J'
    if edge or r < 0.04:
        return 'Q' if r < 0.5 else 'K'
    return 'o' if r < 0.12 else ('K' if r < 0.15 else 'y')


def surface():
    g = grid()
    for y in range(H):
        for x in range(OLD_W):
            edge = min(x, y, OLD_W - 1 - x, H - 1 - y) < 2
            g[y][x] = terrain(biome_at(y), edge)
        for x in range(OLD_W, W):
            if x <= STRAIT_X1:
                g[y][x] = '~'
                continue
            edge = min(y, W - 1 - x, H - 1 - y) < 2
            g[y][x] = east_terrain(biome_at(y, rnd_east), edge, rnd_east)
    # jeziora: zamarzniete na polnocy, bagna w dzungli, oaza na pustyni
    lake(g, 40, 50, 7, 4, 'i')
    lake(g, 122, 52, 5, 3, '~')
    lake(g, 60, 128, 8, 5, 'V')
    lake(g, 135, 160, 6, 4, 'V')
    lake(g, 110, 222, 4, 3, '~')
    return g


def lake(g, cx, cy, rx, ry, ch):
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if 0 <= x < W and 0 <= y < H and ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                g[y][x] = ch


def road(g, points, ch=':'):
    """Droga szerokosci 2 przez kolejne punkty (odcinki poziome/pionowe); mosty nad woda."""
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        x, y = ax, ay
        while (x, y) != (bx, by):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                px, py = x + dx, y + dy
                if 0 <= px < W and 0 <= py < H:
                    g[py][px] = 'b' if g[py][px] in '~V' else ch
            if x != bx:
                x += 1 if bx > x else -1
            else:
                y += 1 if by > y else -1


# ------------------------------------------------------------------ miasta

def town(g, x0, y0, x1, y1, wall, street, inner_wall, inner_floor, door_floor):
    """Mury, bramy na 4 strony, swiatynia, bank, sklep, domy. Zwraca pozycje swiatyni."""
    box(g, x0, y0, x1, y1, wall, street)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    for gx, gy in [(cx, y0), (cx + 1, y0), (cx, y1), (cx + 1, y1), (x0, cy), (x0, cy + 1), (x1, cy), (x1, cy + 1)]:
        g[gy][gx] = street
    # plac
    rect(g, cx, cy, cx + 1, cy + 1, 'u')
    for lx, ly in [(cx - 3, cy), (cx + 4, cy), (cx - 3, cy + 3), (cx + 4, cy + 3)]:
        g[ly][lx] = 't'
    # swiatynia (polnoc placu)
    tx0, ty0 = cx - 4, y0 + 2
    box(g, tx0, ty0, tx0 + 9, ty0 + 7, '#', '-')
    rect(g, cx, ty0 + 2, cx + 1, ty0 + 6, 'R')
    g[ty0 + 1][cx] = 'a'
    g[ty0 + 1][cx + 1] = 'a'
    g[ty0 + 1][tx0 + 1] = 'l'
    g[ty0 + 1][tx0 + 8] = 'l'
    g[ty0 + 7][cx] = 'd'
    g[ty0 + 7][cx + 1] = 'd'
    temple = (cx, ty0 + 4)
    # bank (zachod) i sklep (wschod)
    box(g, x0 + 2, y0 + 2, x0 + 7, y0 + 8, inner_wall, inner_floor)
    rect(g, x0 + 3, y0 + 4, x0 + 5, y0 + 4, 'B')
    g[y0 + 8][x0 + 5] = door_floor
    box(g, x1 - 7, y0 + 2, x1 - 2, y0 + 8, inner_wall, inner_floor)
    rect(g, x1 - 6, y0 + 4, x1 - 4, y0 + 4, 'B')
    g[y0 + 8][x1 - 4] = door_floor
    # domy (poludnie)
    for hx in (x0 + 2, x0 + 8, x1 - 13, x1 - 7):
        if hx + 5 < x1:
            box(g, hx, y1 - 7, hx + 5, y1 - 2, inner_wall, inner_floor)
            g[y1 - 7][hx + 2] = door_floor
    return temple


# ------------------------------------------------------------------ podziemia

def dungeon(g, area, points, wall, floor, decor, extra_rooms, seed_marks, rng=None):
    """Korytarze laczace punkty kluczowe w prostokacie area; sprawdza dostepnosc."""
    rnd = rng or globals()['rnd']
    X0, Y0, X1, Y1 = area
    rect(g, X0, Y0, X1, Y1, wall)

    def carve(x, y):
        if X0 < x < X1 and Y0 < y < Y1:
            g[y][x] = floor

    for (px, py) in points:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                carve(px + dx, py + dy)
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        x, y = ax, ay
        while x != bx:
            carve(x, y); carve(x, y + 1)
            x += 1 if bx > x else -1
        while y != by:
            carve(x, y); carve(x + 1, y)
            y += 1 if by > y else -1
    rooms = []
    for _ in range(extra_rooms):
        cx, cy = rnd.randint(X0 + 3, X1 - 4), rnd.randint(Y0 + 3, Y1 - 4)
        rw, rh = rnd.randint(2, 3), rnd.randint(1, 3)
        for y in range(cy - rh, cy + rh + 1):
            for x in range(cx - rw, cx + rw + 1):
                carve(x, y)
        rooms.append((cx, cy))
        x, y = cx, cy
        tx, ty = points[rnd.randrange(len(points))]
        while x != tx:
            carve(x, y)
            x += 1 if tx > x else -1
        while y != ty:
            carve(x, y)
            y += 1 if ty > y else -1
    for (mx, my), ch in seed_marks.items():
        g[my][mx] = ch
    near_points = {(px + dx, py + dy) for (px, py) in points for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    cells = [(x, y) for y in range(Y0, Y1 + 1) for x in range(X0, X1 + 1)
             if g[y][x] == floor and (x, y) not in near_points]
    rnd.shuffle(cells)
    walk_count = len(reachable(g, points[0]))
    for ch in decor:
        for (x, y) in cells[:4]:
            if g[y][x] == floor:
                g[y][x] = ch
                if ch not in WALKABLE:
                    # przeszkoda nie moze odciac zadnej czesci podziemi
                    if len(reachable(g, points[0])) == walk_count - 1:
                        walk_count -= 1
                    else:
                        g[y][x] = floor
        cells = cells[4:]
    reach = reachable(g, points[0])
    missing = [p for p in points if p not in reach]
    assert not missing, f'unreachable {missing}'
    return rooms


# ------------------------------------------------------------------ Varden (kopia)

def read_varden():
    floors = {}
    for z in FLOORS:
        name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
        with open(os.path.join(VARDEN, name)) as f:
            rows = [line.rstrip('\n') for line in f]
        floors[z] = [r.ljust(VW)[:VW] for r in rows[:VH]] + [' ' * VW] * max(0, VH - len(rows))
    return floors


def paste(g, rows, ox, oy, transparent_space):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ' ' and transparent_space:
                continue
            g[oy + y][ox + x] = ch


def read_lines(name):
    with open(os.path.join(VARDEN, name)) as f:
        return [l.rstrip('\n') for l in f if l.strip() and not l.startswith('#')]


def read_props():
    props = {}
    with open(os.path.join(VARDEN, 'map.properties')) as f:
        for l in f:
            if '=' in l and not l.startswith('#'):
                k, v = l.strip().split('=', 1)
                props[k] = v
    return props


# ------------------------------------------------------------------ wschodni kontynent

BRIDGE_Y = 86
CAMP = (172, 80, 186, 93)                       # palisada obozu za mostem (strefa ochronna)
GATES = [(STRAIT_X0, BRIDGE_Y, STRAIT_X1, BRIDGE_Y + 1, 0, 200)]   # straznik mostu: od poziomu 200
VOLCANO = (212, 142)                            # krater Ignarotha
RUINS = (222, 198, 238, 212)                    # ruiny Kha'zuula
SUMMIT = (214, 30)                              # wejscie na Lodowe Szczyty (+1, +2)
ABYSS = (196, 216)                              # zejscie do Otchlani (-1..-5)


def east_continent(floors, transitions):
    """Kontynent za ciesnina: 4 krainy w pasach biomow i Otchlan pod spodem. Zwraca spawny i wejscia."""
    g = floors[0]
    r = rnd_east
    # most nad ciesnina - przedluzenie wschodniego szlaku Varden
    road(g, [(146, BRIDGE_Y), (CAMP[0] + 4, BRIDGE_Y)])
    # oboz: plot, wejscia na 4 strony, ogniska
    x0, y0, x1, y1 = CAMP
    rect(g, x0, y0, x1, y1, 'F')
    rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, ':')
    cx = (x0 + x1) // 2
    for gx, gy in [(x0, BRIDGE_Y), (x0, BRIDGE_Y + 1), (x1, BRIDGE_Y), (x1, BRIDGE_Y + 1),
                   (cx, y0), (cx + 1, y0), (cx, y1), (cx + 1, y1)]:
        g[gy][gx] = ':'
    g[y0 + 3][x0 + 4] = 'k'
    g[y1 - 3][x1 - 4] = 'k'
    # szlaki z obozu do krain
    road(g, [(x1, BRIDGE_Y), (199, BRIDGE_Y), (199, 150)])          # na poludnie: Wyzyny
    road(g, [(cx, y0), (cx, 40), (SUMMIT[0] - 6, 40)])               # na polnoc: Szczyty
    road(g, [(199, 150), (199, ABYSS[1] - 6), (ABYSS[0], ABYSS[1] - 6)])  # dalej: Pustkowia

    # lawa na Wyzynach i krater wulkanu (wyspa z boss'em, jedno przejscie od poludnia)
    for (lx, ly, rx, ry) in [(184, 124, 4, 3), (240, 128, 5, 3), (232, 162, 4, 2), (188, 160, 3, 2)]:
        lake(g, lx, ly, rx, ry, '\u03b5')
    vx, vy = VOLCANO
    lake(g, vx, vy, 9, 6, '\u03b5')
    lake(g, vx, vy, 5, 3, '\u03b3')
    rect(g, vx, vy, vx + 1, vy + 7, '\u03b3')
    # zamarzniete jeziora na Szczytach, rozlewiska w Kniejach
    lake(g, 238, 44, 5, 3, 'i')
    lake(g, 226, 96, 5, 3, 'V')
    lake(g, 196, 70, 3, 2, 'V')
    # ruiny Kha'zuula: piaskowiec, wejscia od zachodu i polnocy
    rx0, ry0, rx1, ry1 = RUINS
    box(g, rx0, ry0, rx1, ry1, 'I', 'z')
    for gx, gy in [(rx0, (ry0 + ry1) // 2), (rx0, (ry0 + ry1) // 2 + 1), ((rx0 + rx1) // 2, ry0)]:
        g[gy][gx] = 'z'
    for tx, ty in [(rx0 + 2, ry0 + 2), (rx1 - 2, ry0 + 2), (rx0 + 2, ry1 - 2), (rx1 - 2, ry1 - 2)]:
        g[ty][tx] = '@'

    # Lodowe Szczyty: schody na +1 i +2 (lodowe wnetrze gor)
    sx, sy = SUMMIT
    rect(g, sx - 2, sy - 2, sx + 2, sy + 2, 's')
    g[sy][sx] = '!'
    dungeon(floors[1], (198, 10, 236, 44), [SUMMIT, (228, 16), (204, 40), (232, 38)], 'E', 'e', 'GG$$', 4,
            {SUMMIT: '?', (228, 16): '!'}, r)
    dungeon(floors[2], (214, 2, 248, 30), [(228, 16), (242, 8), (220, 26)], 'E', 'e', 'GG$', 3,
            {(228, 16): '?'}, r)
    transitions += [(sx, sy, 0, 'STAIRS', sx, sy - 1, 1), (sx, sy, 1, 'STAIRS', sx, sy + 1, 0),
                    (228, 16, 1, 'STAIRS', 228, 17, 2), (228, 16, 2, 'STAIRS', 229, 16, 1)]

    # Otchlan: piec pieter w dol, schody w obie strony
    ax, ay = ABYSS
    rect(g, ax - 2, ay - 2, ax + 2, ay + 2, '\u03b6')
    g[ay][ax] = '\u03bd'
    down, up = '\u03bb', '\u03bc'
    levels = [
        (-1, (170, 160, 252, 230), [ABYSS, (230, 190), (180, 180), (240, 224)]),
        (-2, (170, 156, 252, 230), [(240, 224), (210, 170), (176, 200), (200, 228)]),
        (-3, (170, 150, 252, 230), [(200, 228), (240, 160), (186, 160)]),
        (-4, (170, 150, 252, 230), [(186, 160), (226, 184), (246, 214), (176, 224)]),
        (-5, (170, 156, 252, 230), [(176, 224), (210, 200), (240, 170)]),
    ]
    for z, area, pts in levels:
        marks = {pts[0]: up}
        if z > -5:
            marks[pts[-1]] = down
        dungeon(floors[z], area, pts, '\u03b9', '\u03b8', '\u03be\u03ba\u03be\u03ba', 5, marks, r)
        (ex, ey) = pts[0]
        if z == -1:
            transitions += [(ex, ey, 0, 'STAIRS', ex, ey + 1, -1), (ex, ey, -1, 'STAIRS', ex + 1, ey, 0)]
        else:
            transitions += [(ex, ey, z + 1, 'STAIRS', ex, ey + 1, z), (ex, ey, z, 'STAIRS', ex + 1, ey, z + 1)]

    spawns = [
        '# Przeklete Knieje (230-300)',
        'cursed_treant;196;76;0;5;3;240', 'cursed_treant;232;80;0;5;3;240', 'cursed_treant;214;104;0;5;3;240',
        'cursed_treant;246;104;0;4;2;240', 'wraith_knight;186;104;0;5;3;240', 'wraith_knight;222;70;0;5;3;240',
        'wraith_knight;244;90;0;5;3;240', 'wraith_knight;200;96;0;4;2;240',
        '# Spalone Wyzyny (300-400)',
        'fire_elemental;184;132;0;5;3;240', 'fire_elemental;236;138;0;5;3;240', 'fire_elemental;190;166;0;5;2;240',
        'fire_elemental;246;156;0;4;2;240', 'lava_golem;196;120;0;5;2;300', 'lava_golem;226;122;0;5;2;300',
        'lava_golem;222;166;0;5;2;300',
        f'ignaroth;{VOLCANO[0]};{VOLCANO[1]};0;2;1;3600',
        '# Lodowe Szczyty (400-560)',
        'frost_giant;180;20;0;5;3;300', 'frost_giant;246;26;0;5;3;300', 'frost_giant;196;54;0;5;2;300',
        'storm_harpy;230;54;0;6;3;300', 'storm_harpy;188;8;0;6;3;300', 'storm_harpy;244;8;0;5;2;300',
        'ice_dragon;204;40;1;4;2;360', 'ice_dragon;232;38;1;4;2;360', 'frost_giant;214;20;1;4;2;300',
        'ice_dragon;222;56;0;5;1;360',
        'hrimgar;242;8;2;2;1;3600',
        '# Pustkowia Cienia (550-750)',
        'shadow_stalker;180;180;0;5;3;300', 'shadow_stalker;246;180;0;5;3;300', 'shadow_stalker;200;226;0;5;2;300',
        'sand_titan;220;186;0;5;2;360', 'sand_titan;184;206;0;5;2;360', 'sand_titan;248;222;0;4;2;360',
        'void_priest;212;204;0;4;2;360', 'void_priest;244;200;0;4;2;360', 'void_priest;230;226;0;4;2;360',
        f'khazuul;{(RUINS[0] + RUINS[2]) // 2};{(RUINS[1] + RUINS[3]) // 2};0;2;1;3600',
        '# Otchlan (750-1000)',
        'abyssal_demon;230;190;-1;5;3;360', 'abyssal_demon;180;180;-1;5;3;360', 'abyssal_demon;210;170;-2;5;3;360',
        'bone_dragon;176;200;-2;5;2;420', 'bone_dragon;240;160;-3;5;2;420', 'bone_dragon;200;200;-3;5;2;420',
        'abyss_lord;226;184;-4;5;2;480', 'abyss_lord;246;214;-4;4;2;480', 'abyss_lord;210;200;-5;4;2;480',
        'morgath;240;170;-5;2;1;7200',
    ]
    entrances = [('camp', (cx, BRIDGE_Y)), ('volcano', VOLCANO), ('ruins', ((RUINS[0] + RUINS[2]) // 2, (RUINS[1] + RUINS[3]) // 2)),
                 ('summit', SUMMIT), ('abyss', ABYSS)]
    return spawns, entrances


# ------------------------------------------------------------------ swiat

def main():
    os.makedirs(OUT, exist_ok=True)
    floors = {z: grid() for z in FLOORS}
    g = surface()
    floors[0] = g

    # drogi miedzy miastami (najpierw drogi, potem miasta i Varden na wierzchu)
    road(g, [(79, 64), (79, 44)])                                  # Varden -> Frostholm
    road(g, [(79, 111), (79, 186)])                                # Varden -> Qasirah
    road(g, [(79, 140), (99, 140)])                                # odnoga do Selvaran
    road(g, [(111, 86), (146, 86), (146, 60), (124, 60)])          # wschodni szlak do jeziora polnocy
    road(g, [(20, 30), (60, 30), (60, 31), (63, 31)])              # zachodni szlak do jaskin lodu
    road(g, [(79, 150), (61, 150)])                                # sciezka do ruin w dzungli
    road(g, [(96, 198), (124, 198), (124, 205)])                   # szlak do grobowcow

    # Frostholm (snieg)
    frost = town(g, 64, 20, 95, 43, 'A', '=', '%', '_', 'D')
    # Selvaran (rownik) - mury z omszalego kamienia
    selv = town(g, 100, 128, 127, 151, 'M', '=', '%', '_', 'D')
    # Qasirah (pustynia) - piaskowiec
    qas = town(g, 64, 187, 95, 210, 'Z', 'X', 'Z', 'X', 'd')

    # Varden: kopia 1:1 (powierzchnia nadpisuje teren)
    v = read_varden()
    paste(g, v[0], VX, VY, False)
    for z in FLOORS:
        if z != 0:
            paste(floors[z], v[z], VX, VY, True)

    transitions = []
    for line in read_lines('transitions.csv'):
        x, y, z, typ, tx, ty, tz = line.split(';')
        transitions.append((int(x) + VX, int(y) + VY, int(z), typ, int(tx) + VX, int(ty) + VY, int(tz)))

    # jaskinie lodu pod Frostholm: wejscie (20,30) -> -1 -> -2
    g[30][20] = '{'
    f1 = floors[-1]
    dungeon(f1, (6, 14, 40, 46), [(20, 30), (34, 18), (30, 42), (12, 40)], 'E', 'e', '$GG', 4,
            {(20, 30): '!', (12, 40): '?'})
    f2 = floors[-2]
    dungeon(f2, (4, 30, 36, 58), [(12, 40), (30, 34), (28, 54), (8, 54)], 'E', 'e', 'GGG$', 5,
            {(12, 40): '!', (8, 54): '0'})
    # -2 dziura -> -3 (drabina w gore) -> dziura -> -4 (lina w gore) -> schody -> -5 (krolowa lodu)
    dungeon(floors[-3], (2, 40, 40, 62), [(8, 54), (24, 58), (36, 46), (14, 44)], 'E', 'e', 'GG$', 4,
            {(8, 54): '1', (36, 46): '0'})
    dungeon(floors[-4], (10, 30, 50, 62), [(36, 46), (20, 36), (46, 58), (16, 58)], 'E', 'e', 'GGG', 4,
            {(36, 46): '2', (16, 58): '?'})
    dungeon(floors[-5], (4, 36, 44, 62), [(16, 58), (30, 44), (40, 56)], 'E', 'e', 'GGGG', 3, {(16, 58): '!'})
    transitions += [(20, 30, 0, 'STAIRS', 20, 31, -1), (20, 30, -1, 'STAIRS', 21, 30, 0),
                    (12, 40, -1, 'STAIRS', 12, 41, -2), (12, 40, -2, 'STAIRS', 12, 39, -1),
                    (8, 54, -2, 'HOLE', 9, 54, -3), (8, 54, -3, 'LADDER', 8, 53, -2),
                    (36, 46, -3, 'HOLE', 37, 46, -4), (36, 46, -4, 'ROPE_SPOT', 36, 45, -3),
                    (16, 58, -4, 'STAIRS', 16, 59, -5), (16, 58, -5, 'STAIRS', 16, 57, -4)]

    # ruiny swiatyni w dzungli: wejscie (60,150) -> -1 -> -2
    g[150][60] = '|'
    r1 = floors[-1]
    dungeon(r1, (40, 140, 80, 168), [(60, 150), (46, 146), (74, 162), (50, 164)], 'N', 'c', 'jj', 4,
            {(60, 150): '(', (50, 164): ')'})
    r2 = floors[-2]
    dungeon(r2, (36, 152, 76, 180), [(50, 164), (70, 158), (66, 176), (42, 174)], 'N', 'c', 'jm8', 5,
            {(50, 164): '(', (42, 174): 'O'})
    # -2 dziura -> -3 (drabina) -> dziura -> -4 (lina) -> schody -> -5 (kolos z magma)
    dungeon(floors[-3], (32, 150, 80, 184), [(42, 174), (60, 180), (74, 156), (48, 152)], 'N', 'c', '88j', 4,
            {(42, 174): 'H', (74, 156): 'O'})
    dungeon(floors[-4], (34, 140, 86, 176), [(74, 156), (56, 144), (40, 170), (80, 172)], 'N', 'c', '889', 4,
            {(74, 156): '4', (40, 170): ')'})
    dungeon(floors[-5], (32, 146, 84, 184), [(40, 170), (60, 150), (78, 178)], 'N', 'c', '9998', 3, {(40, 170): '('})
    transitions += [(60, 150, 0, 'STAIRS', 60, 151, -1), (60, 150, -1, 'STAIRS', 61, 150, 0),
                    (50, 164, -1, 'STAIRS', 50, 165, -2), (50, 164, -2, 'STAIRS', 50, 163, -1),
                    (42, 174, -2, 'HOLE', 43, 174, -3), (42, 174, -3, 'LADDER', 42, 173, -2),
                    (74, 156, -3, 'HOLE', 75, 156, -4), (74, 156, -4, 'ROPE_SPOT', 74, 155, -3),
                    (40, 170, -4, 'STAIRS', 40, 171, -5), (40, 170, -5, 'STAIRS', 40, 169, -4)]

    # grobowce pod pustynia: wejscie (124,206) -> -1 -> -2 -> -3
    g[206][124] = '^'
    t1 = floors[-1]
    dungeon(t1, (104, 192, 150, 226), [(124, 206), (110, 198), (144, 220), (112, 222)], 'I', 'z', '@@', 4,
            {(124, 206): '&', (112, 222): '*'})
    t2 = floors[-2]
    dungeon(t2, (100, 196, 146, 230), [(112, 222), (140, 200), (132, 226)], 'I', 'z', '@', 5,
            {(112, 222): '&', (132, 226): '*'})
    t3 = floors[-3]
    dungeon(t3, (116, 200, 156, 230), [(132, 226), (150, 206), (122, 204)], 'I', 'z', '@', 4,
            {(132, 226): '&', (150, 206): '5'})
    # -3 dziura -> -4 (lina w gore) -> schody -> -5 (faraon)
    dungeon(floors[-4], (104, 190, 156, 230), [(150, 206), (130, 194), (108, 220), (140, 228)], 'I', 'z', '@@', 4,
            {(150, 206): '7', (108, 220): '*'})
    dungeon(floors[-5], (100, 192, 150, 230), [(108, 220), (126, 198), (144, 224)], 'I', 'z', '@@@', 3,
            {(108, 220): '&'})
    transitions += [(150, 206, -3, 'HOLE', 151, 206, -4), (150, 206, -4, 'ROPE_SPOT', 150, 205, -3),
                    (108, 220, -4, 'STAIRS', 108, 221, -5), (108, 220, -5, 'STAIRS', 108, 219, -4)]
    transitions += [(124, 206, 0, 'STAIRS', 124, 207, -1), (124, 206, -1, 'STAIRS', 124, 205, 0),
                    (112, 222, -1, 'STAIRS', 112, 221, -2), (112, 222, -2, 'STAIRS', 112, 223, -1),
                    (132, 226, -2, 'STAIRS', 132, 225, -3), (132, 226, -3, 'STAIRS', 132, 227, -2)]

    # ---------------------------------------------------------- wschodni kontynent (endgame)
    east_spawns, east_entrances = east_continent(floors, transitions)

    # NPC: Varden (wspolrzedne mapy varden + przesuniecie) i uklad town() dla 3 miast
    npcs = [
        ('varden_banker', 'Bankier Aldric', 'BANKER', 'Varden', 22 + VX, 13 + VY, '-', '#d8c070',
         'Witaj w banku Varden. Twoje zloto jest tu bezpieczne.'),
        ('varden_merchant', 'Kupiec Tomas', 'MERCHANT', 'Varden', 40 + VX, 13 + VY, 'varden_general', '#a07040',
         'Pochodnie, liny, prowiant i sprzet dla poczatkujacych. Skupuje tez lupy.'),
        ('varden_mage', 'Czarodziejka Mirela', 'MERCHANT', 'Varden', 22 + VX, 31 + VY, 'varden_magic', '#8060c0',
         'Mikstury, runy i rozdzki. Skupuje krysztaly i perly.'),
        ('varden_priest', 'Kaplan Benedykt', 'PRIEST', 'Varden', 34 + VX, 15 + VY, '-', '#f0f0f0',
         'Niech swiatlo cie prowadzi. Moge cie uleczyc.'),
        ('varden_guide', 'Straznik Oren', 'GUIDE', 'Varden', 29 + VX, 16 + VY, '-', '#607080',
         'Okolica potrzebuje pomocy. Mam dla ciebie zadania.'),
    ]
    for key, name, (x0, y0, x1, y1), temple, shop, colors in [
        ('frost', 'Frostholm', (64, 20, 95, 43), frost, 'frost_shop', ('#c0d8e8', '#6080a0')),
        ('selv', 'Selvaran', (100, 128, 127, 151), selv, 'selv_shop', ('#60a060', '#80a040')),
        ('qas', 'Qasirah', (64, 187, 95, 210), qas, 'qas_shop', ('#e0c080', '#c09050')),
    ]:
        cx, ty0 = (x0 + x1) // 2, y0 + 2
        npcs += [
            (key + '_banker', 'Bankier ' + name, 'BANKER', name, x0 + 4, y0 + 3, '-', '#d8c070',
             'Bank ' + name + ' prowadzi konta we wszystkich miastach.'),
            (key + '_merchant', 'Kupiec ' + name, 'MERCHANT', name, x1 - 5, y0 + 3, shop, colors[0],
             'Towary z ' + name + ' i skup lupow z okolicy.'),
            (key + '_priest', 'Kaplan ' + name, 'PRIEST', name, cx + 3, ty0 + 2, '-', '#f0f0f0',
             'Swiatynia ' + name + ' wita wedrowca.'),
            (key + '_guide', 'Przewodnik ' + name, 'GUIDE', name, cx - 2, ty0 + 4, '-', colors[1],
             'Okolice ' + name + ' sa niebezpieczne. Pomozesz?'),
        ]
    for (_k, _n, _r, _t, x, y, *_rest) in npcs:
        assert g[y][x] in WALKABLE, f'npc {_k} on {g[y][x]!r} at {x},{y}'

    # kontrola: wszystkie miasta osiagalne z Varden po powierzchni
    vt = (31 + VX, 16 + VY)
    reach = reachable(g, vt)
    for name, t in (('Frostholm', frost), ('Selvaran', selv), ('Qasirah', qas)):
        assert t in reach, f'{name} temple unreachable from Varden'
    for (x, y, z, *_r) in transitions:
        if z == 0:
            assert (x, y) in reach, f'entrance {x},{y} unreachable'
    for name, pos in east_entrances:
        assert pos in reach, f'{name} unreachable from Varden'

    # zapis pieter
    for z in FLOORS:
        name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
        with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
            for row in floors[z]:
                f.write(''.join(row).rstrip() + '\n')

    # legenda = Varden + biomy
    with open(os.path.join(VARDEN, 'legend.csv')) as f:
        legend = f.read().rstrip('\n').split('\n')
    legend.append('// --- biomy swiata (generate_world.py) ---')
    for sym, ground, dec, obj, wall in EXTRA_LEGEND:
        legend.append(f'{sym};{ground};{dec};{obj};{wall}')
    with open(os.path.join(OUT, 'legend.csv'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(legend) + '\n')

    with open(os.path.join(OUT, 'transitions.csv'), 'w', encoding='utf-8') as f:
        f.write('# x;y;z;typ;docelowe x;y;z\n')
        for t in transitions:
            f.write(';'.join(str(v) for v in t) + '\n')

    # strefy ochronne: Varden (przesuniety) + wnetrza murow 3 miast
    props = read_props()
    pz = []
    for r in props['pz'].split(';'):
        x0, y0, x1, y1, z = (int(v) for v in r.split(','))
        pz.append(f'{x0 + VX},{y0 + VY},{x1 + VX},{y1 + VY},{z}')
    pz += ['65,21,94,42,0', '101,129,126,150,0', '65,188,94,209,0']
    pz += [f'{CAMP[0] + 1},{CAMP[1] + 1},{CAMP[2] - 1},{CAMP[3] - 1},0']   # oboz za mostem (bez swiatyni)

    towns = [('Varden', vt), ('Frostholm', frost), ('Selvaran', selv), ('Qasirah', qas)]
    regions = [
        'Mrozne Pustkowia;0;0;159;63;0;5;40;90',
        'Lodowe Jaskinie;0;0;159;63;-1;-1;70;100',
        'Glebokie Lodowe Jaskinie;0;0;159;63;-5;-2;100;150',
        'Krainy Varden;0;64;159;111;-5;5;8;60',
        'Dzungla Selvaran;0;112;159;171;0;5;55;80',
        'Zatopione Ruiny;30;130;90;185;-1;-1;65;90',
        'Serce Ruin;30;130;90;185;-5;-2;150;200',
        'Pustynia Qasirah;0;172;159;231;0;5;45;130',
        'Grobowce Qasirah;100;185;159;231;-1;-1;60;130',
        'Krypty Faraonow;100;185;159;231;-5;-2;180;230',
        'Przeklete Knieje;168;64;255;111;0;5;230;300',
        'Spalone Wyzyny;168;112;255;171;0;5;300;400',
        'Lodowe Szczyty;168;0;255;63;0;5;400;560',
        'Pustkowia Cienia;168;172;255;231;0;5;550;750',
        'Otchlan;168;0;255;231;-5;-1;750;1000',
    ]
    with open(os.path.join(OUT, 'map.properties'), 'w', encoding='utf-8') as f:
        f.write('# Swiat gry: 4 miasta w pasach biomow (generate_world.py)\n'
                'name=Swiat Pixel\n'
                f'width={W}\nheight={H}\n'
                'floors=' + ','.join(str(z) for z in FLOORS) + '\n'
                f'spawn={vt[0]},{vt[1]},0\n'
                'pz=' + ';'.join(pz) + '\n'
                '# przejscia strzezone: x0,y0,x1,y1,z,minimalny poziom\n'
                'gates=' + ';'.join(','.join(str(v) for v in gt) for gt in GATES) + '\n'
                '# miasta: nazwa:x,y,z swiatyni (maks. 4)\n'
                'towns=' + ';'.join(f'{n}:{t[0]},{t[1]},0' for n, t in towns) + '\n'
                '# regiony: nazwa;x0;y0;x1;y1;z od;z do;zalecany poziom od;do (oddzielone |)\n'
                'regions=' + '|'.join(regions) + '\n')

    # spawny: Varden (przesuniete) + biomy
    spawns = ['# potwor;x;y;z;promien;liczba;respawn (s)']
    for line in read_lines('spawns.csv'):
        k, x, y, z, rr, c, s = line.split(';')
        spawns.append(f'{k};{int(x) + VX};{int(y) + VY};{z};{rr};{c};{s}')
    spawns += [
        '# Mrozne Pustkowia',
        'snow_wolf;50;12;0;5;3;90', 'snow_wolf;110;12;0;5;3;90', 'snow_wolf;30;56;0;4;3;90',
        'yeti;20;10;0;4;2;150', 'yeti;140;20;0;4;2;150', 'frost_witch;130;44;0;3;2;150',
        '# Lodowe Jaskinie',
        'yeti;30;20;-1;4;2;150', 'frost_witch;30;40;-1;4;2;150', 'ice_golem;20;50;-2;4;2;240', 'ice_golem;30;36;-2;4;2;240',
        '# Dzungla Selvaran',
        'crocodile;60;122;0;6;3;120', 'crocodile;135;168;0;5;2;120', 'jungle_serpent;30;130;0;5;3;120',
        'jungle_serpent;145;120;0;5;3;120', 'jungle_hunter;40;160;0;4;2;120', 'jungle_hunter;150;145;0;4;2;120',
        '# Zatopione Ruiny',
        'jungle_serpent;70;158;-1;4;2;120', 'temple_guardian;68;170;-2;4;2;300', 'temple_guardian;44;172;-2;3;1;300',
        '# Pustynia Qasirah',
        'hyena;40;190;0;6;3;90', 'hyena;120;180;0;6;3;90', 'scorpion;30;215;0;5;3;120', 'scorpion;140;190;0;5;3;120',
        'sand_wraith;20;226;0;4;2;180',
        '# Grobowce Qasirah',
        'scorpion;140;220;-1;4;3;120', 'sand_wraith;118;200;-1;4;2;180', 'mummy;140;202;-2;4;2;300',
        'mummy;148;210;-3;4;2;300',
        '# Glebokie lochy (etap 10)',
        'ice_golem;24;58;-3;4;2;240', 'frost_witch;20;40;-3;4;2;150', 'ice_golem;30;40;-4;5;3;240',
        'frost_queen;30;44;-5;2;1;1800',
        'temple_guardian;60;178;-3;4;2;300', 'temple_guardian;56;146;-4;4;2;300', 'temple_guardian;80;170;-4;3;2;300',
        'ruin_colossus;60;150;-5;2;1;1800',
        'mummy;130;196;-4;4;3;300', 'mummy;140;226;-4;3;2;300', 'mummy;120;214;-4;4;2;300',
        'pharaoh;126;198;-5;2;1;1800',
    ] + east_spawns
    pz_rects = [tuple(int(v) for v in r.split(',')) for r in pz]

    def in_pz(x, y, z):
        return any(a <= x <= c and b <= y <= d and z == zz for a, b, c, d, zz in pz_rects)

    def snap(x, y, z):
        """Najblizszy kafelek do chodzenia poza strefa ochronna i przejsciami."""
        trans = {(t[0], t[1], t[2]) for t in transitions}
        fl = floors[z]
        for rad in range(0, 15):
            for dy in range(-rad, rad + 1):
                for dx in range(-rad, rad + 1):
                    if max(abs(dx), abs(dy)) != rad:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and fl[ny][nx] in WALKABLE and not in_pz(nx, ny, z) \
                            and (nx, ny, z) not in trans and fl[ny][nx] not in '<>(){}|^!?&*[]HO\u03bb\u03bc\u03bd' \
                            and (z != 0 or (nx, ny) in reach):
                        return nx, ny
        raise AssertionError(f'no walkable tile near {x},{y},{z}')

    fixed = []
    for line in spawns:
        if line.startswith('#'):
            fixed.append(line)
            continue
        k, x, y, z, rr, c, s = line.split(';')
        nx, ny = snap(int(x), int(y), int(z))
        fixed.append(f'{k};{nx};{ny};{z};{rr};{c};{s}')
    with open(os.path.join(OUT, 'spawns.csv'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(fixed) + '\n')
    with open(os.path.join(OUT, 'npcs.csv'), 'w', encoding='utf-8') as f:
        f.write('# klucz;imie;rola;miasto;x;y;z;sklep;kolor;powitanie\n')
        for (k, n, r, t, x, y, shop, color, greet) in npcs:
            f.write(f'{k};{n};{r};{t};{x};{y};0;{shop};{color};{greet}\n')
    print('world generated', W, 'x', H, 'towns', towns)


if __name__ == '__main__':
    main()
