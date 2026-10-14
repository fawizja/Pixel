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

W, H = 160, 232
VX, VY = 48, 64          # przesuniecie regionu Varden (musi zgadzac sie z WorldLayout.VARDEN_OFFSET_*)
VW, VH = 64, 48
FLOORS = list(range(-5, 6))

# pasy biomow (y): Frostholm 0..63, Varden 64..111, Selvaran 112..171, Qasirah 172..231
NORTH_END, TEMPERATE_END, JUNGLE_END = 63, 111, 171

rnd = random.Random(20261015)

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
]
WALKABLE = set('.,f=:b_-RDdlLwcjk<>()}HO' + 'sviehJyoXz[]{|^!?&*@$' + '0124578')


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

def biome_at(y):
    """Pas biomu z poszarpana granica (szum)."""
    j = rnd.randint(-2, 2)
    if y + j <= NORTH_END:
        return 'north'
    if y + j <= TEMPERATE_END:
        return 'temperate'
    if y + j <= JUNGLE_END:
        return 'jungle'
    return 'desert'


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
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y) < 2
            g[y][x] = terrain(biome_at(y), edge)
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

def dungeon(g, area, points, wall, floor, decor, extra_rooms, seed_marks):
    """Korytarze laczace punkty kluczowe w prostokacie area; sprawdza dostepnosc."""
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

    # kontrola: wszystkie miasta osiagalne z Varden po powierzchni
    vt = (31 + VX, 16 + VY)
    reach = reachable(g, vt)
    for name, t in (('Frostholm', frost), ('Selvaran', selv), ('Qasirah', qas)):
        assert t in reach, f'{name} temple unreachable from Varden'
    for (x, y, z, *_r) in transitions:
        if z == 0:
            assert (x, y) in reach, f'entrance {x},{y} unreachable'

    # zapis pieter
    for z in FLOORS:
        name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
        with open(os.path.join(OUT, name), 'w') as f:
            for row in floors[z]:
                f.write(''.join(row).rstrip() + '\n')

    # legenda = Varden + biomy
    with open(os.path.join(VARDEN, 'legend.csv')) as f:
        legend = f.read().rstrip('\n').split('\n')
    legend.append('// --- biomy swiata (generate_world.py) ---')
    for sym, ground, dec, obj, wall in EXTRA_LEGEND:
        legend.append(f'{sym};{ground};{dec};{obj};{wall}')
    with open(os.path.join(OUT, 'legend.csv'), 'w') as f:
        f.write('\n'.join(legend) + '\n')

    with open(os.path.join(OUT, 'transitions.csv'), 'w') as f:
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
    ]
    with open(os.path.join(OUT, 'map.properties'), 'w') as f:
        f.write('# Swiat gry: 4 miasta w pasach biomow (generate_world.py)\n'
                'name=Swiat Pixel\n'
                f'width={W}\nheight={H}\n'
                'floors=' + ','.join(str(z) for z in FLOORS) + '\n'
                f'spawn={vt[0]},{vt[1]},0\n'
                'pz=' + ';'.join(pz) + '\n'
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
    ]
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
                            and (nx, ny, z) not in trans and fl[ny][nx] not in '<>(){}|^!?&*[]HO':
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
    with open(os.path.join(OUT, 'spawns.csv'), 'w') as f:
        f.write('\n'.join(fixed) + '\n')
    print('world generated', W, 'x', H, 'towns', towns)


if __name__ == '__main__':
    main()
