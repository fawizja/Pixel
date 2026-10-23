#!/usr/bin/env python3
"""Generator mapy startowej Varden (pietra +5..-5).

Uruchom z katalogu repozytorium:  python3 tools/mapgen/generate_varden.py
Wynik trafia do shared/src/main/resources/maps/varden/. Generator jest deterministyczny
(stale ziarno), wiec ponowne uruchomienie daje identyczne pliki. Mozna tez edytowac
pliki floor_*.txt recznie - wtedy nie uruchamiaj ponownie generatora.
Znaczenie symboli: legend.csv.
"""
import math
import os
import random
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import town as tk  # noqa: E402

W, H = 64, 48
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'shared', 'src', 'main', 'resources', 'maps', 'varden')
WALKABLE = set('.,f=:b_-RDdlLwcjk<>()}HO')

rnd = random.Random(20261014)


def grid(fill=' '):
    return [[fill] * W for _ in range(H)]


def rect(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            g[y][x] = ch


def box(g, x0, y0, x1, y1, wall, floor):
    rect(g, x0, y0, x1, y1, wall)
    rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, floor)


BASE_LEGEND = [
    ('space', '-', '-', '-', '-'),
    ('.', 'grass', '-', '-', '-'), (',', 'grass', 'tall_grass', '-', '-'), ('f', 'grass', 'flowers', '-', '-'),
    ('T', 'grass', '-', 'tree', '-'), ('Y', 'grass', '-', 'bush', '-'), ('r', 'grass', '-', 'rock', '-'),
    ('=', 'cobblestone', '-', '-', '-'), (':', 'dirt', '-', '-', '-'), ('~', 'water', '-', '-', '-'),
    ('b', 'bridge', '-', '-', '-'), ('#', 'cobblestone', '-', '-', 'stone_wall'),
    ('%', 'wood_floor', '-', '-', 'wood_wall'), ('_', 'wood_floor', '-', '-', '-'),
    ('-', 'stone_floor', '-', '-', '-'), ('R', 'stone_floor', 'rug', '-', '-'),
    ('D', 'wood_floor', '-', 'door_open', '-'), ('d', 'stone_floor', '-', 'door_open', '-'),
    ('a', 'stone_floor', '-', 'altar', '-'), ('u', 'cobblestone', '-', 'fountain', '-'),
    ('t', 'cobblestone', '-', 'street_lamp', '-'), ('l', 'stone_floor', '-', 'wall_torch', '-'),
    ('L', 'wood_floor', '-', 'wall_torch', '-'), ('B', 'wood_floor', '-', 'counter', '-'),
    ('x', 'wood_floor', '-', 'crate', '-'), ('F', 'grass', '-', 'fence', '-'), ('w', 'field', 'wheat', '-', '-'),
    ('c', 'cave_floor', '-', '-', '-'), ('C', 'cave_floor', '-', '-', 'cave_wall'),
    ('g', 'cave_floor', '-', 'crystal', '-'), ('m', 'cave_floor', '-', 'rubble', '-'),
    ('j', 'cave_floor', '-', 'wall_torch', '-'), ('k', 'dirt', '-', 'campfire', '-'), ('q', 'dirt', '-', 'rock', '-'),
    ('<', 'stone_floor', '-', 'stairs_up', '-'), ('>', 'stone_floor', '-', 'stairs_down', '-'),
    ('(', 'cave_floor', '-', 'stairs_up', '-'), (')', 'cave_floor', '-', 'stairs_down', '-'),
    ('}', 'dirt', '-', 'stairs_down', '-'), ('H', 'cave_floor', '-', 'ladder', '-'), ('O', 'cave_floor', '-', 'hole', '-'),
]
LEGEND = tk.Legend(BASE_LEGEND)

# pola uzywane przez testy i spawny: zawsze wolna trawa (bez drzew lasu i zagajnikow)
KEEP_CLEAR = {(x, y) for x in range(8, 18) for y in range(10, 14)} | {
    (10, 8), (14, 30), (42, 6), (45, 6), (48, 6), (24, 42), (58, 12), (58, 32), (10, 40), (16, 22), (30, 30)} | {
    (x, y) for x in range(30, 51) for y in range(4, 9)}
SPAWN_SPOTS = [(10, 8), (14, 30), (42, 6), (24, 42), (58, 12), (58, 32)]


def smooth_noise(r, waves=5):
    """Gladki szum z kilku fal (deterministyczny, bez bibliotek)."""
    ws = [(r.uniform(0.18, 0.55), r.uniform(0.18, 0.55), r.uniform(0, 6.3), r.uniform(0, 6.3), r.uniform(0.5, 1.0))
          for _ in range(waves)]
    total = sum(w[4] for w in ws)

    def f(x, y):
        return sum(a * math.sin(kx * x + px) * math.sin(ky * y + py) for kx, ky, px, py, a in ws) / total * 2
    return f


def river_span(y):
    """Kolumny rzeki w wierszu y: meandry, ale na krawedziach mapy i przy moscie (y 22-23) x 52..54."""
    if y <= 1 or y >= 46:
        return 52, 54
    s = math.sin(2 * math.pi * y / 45)
    c = int(round(53 + 2.2 * s))
    x0, x1 = c - 1, c + 1
    if s > 0.7:
        x1 += 1          # zewnetrzny brzeg zakola jest szerszy
    elif s < -0.7:
        x0 -= 1
    return x0, x1


def old_terrain():
    """Stare losowanie trawy i lasu (ta sama sekwencja rnd, wiec podziemia generuja sie bez zmian)."""
    g = grid('.')
    for y in range(H):
        for x in range(W):
            r = rnd.random()
            if r < 0.12:
                g[y][x] = ','
            elif r < 0.15:
                g[y][x] = 'f'
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d < 2:
                g[y][x] = 'T'
            elif d < 5:
                r = rnd.random()
                if r < 0.35:
                    g[y][x] = 'T'
                elif r < 0.40:
                    g[y][x] = 'Y'
    return g


def forest(c, r):
    """Nieregularny skraj lasu: glebokosc 2..7 pol wg szumu, poszarpana krawedz, kepy krzakow."""
    n = smooth_noise(r)
    for y in range(H):
        for x in range(W):
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d < 2 or d >= 9 or (x, y) in KEEP_CLEAR:
                continue
            depth = 1.6 + 2.2 * (n(x, y) + 1)
            cell = c.at(x, y)
            if cell[2] in ('tree', 'bush'):
                c.put(x, y, ('grass', '-', '-', '-'))
            if d < depth:
                v = r.random()
                c.put(x, y, ('grass', '-', 'tree' if v < 0.62 else 'bush' if v < 0.74 else '-', '-'))
            elif d < depth + 1.6 and r.random() < 0.18:
                c.put(x, y, ('grass', '-', 'tree' if r.random() < 0.7 else 'bush', '-'))
    # zagajniki na lakach (nie na sciezkach spawnow)
    for gx, gy, rad in [(6, 16, 2.2), (39, 3, 2.0), (59, 6, 2.0), (60, 40, 2.6), (20, 44, 2.0), (4, 33, 2.5),
                        (14, 46, 2.0), (36, 44, 1.6)]:
        for y in range(gy - 3, gy + 4):
            for x in range(gx - 3, gx + 4):
                if not c.inside(x, y) or (x, y) in KEEP_CLEAR:
                    continue
                if math.hypot(x - gx, (y - gy) * 1.2) <= rad + r.uniform(-0.6, 0.6) and c.at(x, y)[0] == 'grass':
                    v = r.random()
                    c.put(x, y, ('grass', '-', 'tree' if v < 0.6 else 'bush' if v < 0.8 else '-',  '-'))
    for (sx, sy) in SPAWN_SPOTS:        # srodek spawnu i jego sasiedzi wolni
        for y in range(sy - 1, sy + 2):
            for x in range(sx - 1, sx + 2):
                if c.inside(x, y) and c.at(x, y)[2] in ('tree', 'bush'):
                    c.put(x, y, ('grass', '-', '-', '-'))


def water(c, r):
    n = smooth_noise(r, 4)
    for y in range(H):
        x0, x1 = river_span(y)
        for x in range(x0, x1 + 1):
            c.put(x, y, ('water', '-', '-', '-'))
    # jezioro: nieregularna plama na poludniowym wschodzie, laczy sie z rzeka rozlewiskiem
    for y in range(35, H):
        for x in range(34, 54):
            v = ((x - 44.5) / 5.6) ** 2 + ((y - 41.6) / 3.3) ** 2 + 0.32 * n(x, y)
            if v <= 1.0:
                c.put(x, y, ('water', '-', '-', '-'))
    c.put(43, 41, ('grass', '-', 'tree', '-'))       # wysepka z drzewem
    c.put(44, 41, ('grass', 'flowers', '-', '-'))
    # brzegi: piasek tam, gdzie woda styka sie z trawa (plaze przy jeziorze, nie przy miescie)
    for y in range(36, H):
        for x in range(34, W):
            if c.at(x, y)[0] == 'grass' and c.at(x, y)[2] == '-' and any(
                    c.inside(x + dx, y + dy) and c.at(x + dx, y + dy)[0] == 'water'
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))) and r.random() < 0.55:
                c.put(x, y, ('sand', '-', '-', '-'))


def path(c, cells, ground='dirt'):
    for (x, y) in cells:
        c.put(x, y, (ground, '-', '-', '-'))


def span(x0, x1, y0, y1):
    return [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]


def roads(c, r):
    # polnocna droga (x 31-32 na krawedzi mapy, do Frostholm) skreca do bramy polnocnej (25-26, 7)
    path(c, span(31, 32, 0, 2) + span(29, 32, 3, 3) + span(25, 30, 4, 4) + span(25, 26, 5, 6))
    # poludniowa droga (do Qasirah) z lekkim zakretem
    path(c, span(31, 32, 37, 39) + span(31, 33, 40, 40) + span(32, 33, 41, 43) + span(31, 33, 44, 44) + span(31, 32, 45, 47))
    path(c, span(16, 30, 40, 40) + span(21, 26, 39, 39))           # sciezka do pol (z poboczem)
    # zachodnia droga do kopalni
    path(c, span(11, 17, 22, 23) + [(13, 21), (14, 21), (15, 24)])
    # wschodnia droga za mostem (do Selvaran) z lukiem
    x0, x1 = river_span(22)
    path(c, span(x1 + 1, 57, 22, 23) + span(57, 60, 21, 22) + span(60, 63, 22, 23))
    for y in (22, 23):
        for x in range(x0 - 1, x1 + 1):
            if c.at(x, y)[0] == 'water':
                c.put(x, y, ('bridge', '-', '-', '-'))


def mine_and_fields(c, r):
    # polana przy wejsciu do kopalni: nieregularny krag kamieni, ognisko, drewno
    for y in range(18, 28):
        for x in range(3, 13):
            v = math.hypot((x - 8) / 3.6, (y - 22.5) / 3.9)
            if v <= 1.0:
                c.put(x, y, ('dirt', '-', '-', '-'))
            elif v <= 1.25 and r.random() < 0.55 and (x, y) not in KEEP_CLEAR:
                c.put(x, y, ('dirt' if r.random() < 0.5 else 'grass', '-', 'rock', '-'))
    path(c, span(11, 17, 22, 23))
    c.put(8, 22, ('dirt', '-', 'stairs_down', '-'))
    c.put(10, 25, ('dirt', '-', 'campfire', '-'))
    c.put(6, 24, ('dirt', '-', 'firewood', '-'))
    c.put(6, 20, ('dirt', '-', 'crate', '-'))
    c.put(7, 20, ('dirt', '-', 'barrel', '-'))
    c.put(10, 19, ('dirt', '-', 'signpost', '-'))
    # pola uprawne z plotem, stogami i stracha brak - bele siana przy furtce
    for y in range(36, 44):
        for x in range(6, 16):
            edge = x in (6, 15) or y in (36, 43)
            c.put(x, y, ('grass', '-', 'fence', '-') if edge else ('field', 'wheat', '-', '-'))
    c.put(15, 40, ('dirt', '-', '-', '-'))
    for (x, y) in ((17, 37), (17, 38), (18, 37), (16, 44)):
        c.put(x, y, ('grass', '-', 'hay_bale', '-'))
    c.put(17, 41, ('dirt', '-', 'signpost', '-'))


# mury miasta: lamana od narożnika NW zgodnie z ruchem wskazowek; wschodni bok zamyka rzeka
WALL_NORTH = [(18, 10), (22, 10), (22, 7), (39, 7), (39, 9), (46, 9), (46, 12)]
WALL_SOUTH = [(18, 10), (18, 34), (26, 34), (26, 36), (43, 36), (43, 37)]
GATES = [(25, 7), (26, 7), (18, 22), (18, 23), (31, 36), (32, 36)]
TOWERS = [(16, 8), (22, 5), (27, 5), (46, 7), (16, 19), (16, 24), (16, 34), (24, 35), (28, 36), (33, 36)]


def to_water(c, x, y, dx):
    """Przedluza mur w strone rzeki az do wody."""
    while c.inside(x, y) and c.at(x, y)[0] not in ('water', 'bridge'):
        c.put(x, y, ('cobblestone', '-', '-', 'stone_wall'))
        x += dx


def town(c, r):
    c.wall_line(WALL_NORTH, 'stone_wall', 'cobblestone')
    c.wall_line(WALL_SOUTH, 'stone_wall', 'cobblestone')
    to_water(c, 47, 12, 1)
    to_water(c, 44, 37, 1)
    for (x, y) in GATES:
        c.put(x, y, ('cobblestone', '-', '-', '-'))
    # wnetrze murow (do strefy ochronnej): zalanie od swiatyni, bramy i woda sa granica
    seen = {(31, 16)}
    stack = [(31, 16)]
    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if (nx, ny) in seen or not c.inside(nx, ny) or (nx, ny) in GATES:
                continue
            cell = c.at(nx, ny)
            if cell[3] != '-' or cell[0] in ('water', 'bridge'):
                continue
            seen.add((nx, ny))
            stack.append((nx, ny))
    assert len(seen) < 1300, f'mury nieszczelne: {len(seen)} pol'
    inside = seen
    for (x, y) in inside:                     # ogrody: trawa z kwiatami, bez lasu
        v = r.random()
        c.put(x, y, ('grass', 'flowers' if v < 0.06 else 'tall_grass' if v < 0.14 else '-', '-', '-'))

    # --- ulice (bruk), place (plyty), rynek (cegla), zaulki ---
    cob = 'cobblestone'
    for x0, y0, x1, y1 in [
        (23, 8, 38, 8), (23, 9, 28, 9), (35, 9, 38, 9), (38, 10, 38, 11),   # ulica za swiatynia od bramy polnocnej
        (19, 11, 26, 11), (26, 12, 26, 21), (37, 9, 37, 21),               # zaulki przy banku i przy sklepie
        (24, 18, 25, 21), (19, 21, 25, 23), (19, 24, 19, 33),              # ulica zachodnia, droga wzdluz muru
        (37, 19, 51, 21), (38, 22, 51, 23), (47, 13, 47, 18),              # ulica wschodnia do mostu
        (31, 27, 32, 35), (33, 24, 43, 26), (43, 27, 44, 29),              # ulica poludniowa, droga do wiezy i kuzni
        (36, 35, 48, 35), (44, 36, 48, 36), (19, 33, 25, 33), (27, 34, 30, 35),
    ]:
        c.pave(x0, y0, x1, y1, cob)
    # plac swiatynny: plyty z nieregularnym obrysem
    for y in range(20, 27):
        for x in range(25, 39):
            corner = (y in (20, 26) and x in (25, 38)) or (y == 26 and x in (26, 37))
            if not corner:
                c.pave(x, y, x, y, 'paving')
    c.pave(26, 27, 30, 33, 'brick_paving')                            # rynek
    c.pave(24, 24, 25, 26, 'brick_paving')
    # nabrzeze: deski przy rzece wzdluz calego miasta
    for y in range(12, 38):
        xs = [x for x in range(40, W) if c.at(x, y)[0] in ('water', 'bridge')]
        if not xs:
            continue
        bank = xs[0]
        for x in range(bank - 2, bank):
            if (x, y) in inside and c.at(x, y)[3] == '-':
                c.pave(x, y, x, y, 'bridge' if x == bank - 1 else cob)

    # --- budynki ---
    temple = c.building([(27, 12, 36, 19), (29, 9, 34, 12)], 'stone_wall', 'marble_floor')
    for x in (31, 32):
        c.door(x, 19)
        for y in range(11, 19):
            c.set(x, y, deco='rug')
        c.obj(x, 10, 'altar')
    for (x, y) in ((28, 13), (35, 13), (28, 18), (35, 18), (30, 11), (33, 11)):
        c.obj(x, y, 'wall_torch')
    c.obj(30, 10, 'banner')
    c.obj(33, 10, 'banner')
    c.obj(35, 16, 'potted_plant')
    c.obj(28, 16, 'potted_plant')

    bank = c.building([(20, 12, 25, 17), (20, 17, 23, 21)], 'plaster_wall', 'stone_floor')
    c.door(24, 17)
    for x in range(21, 25):
        c.obj(x, 14, 'counter')
    c.obj(21, 13, 'bookcase')
    c.obj(24, 13, 'wall_torch')
    c.obj(21, 20, 'chest')
    c.obj(22, 20, 'chest')
    c.obj(21, 18, 'wall_torch')
    c.obj(23, 16, 'potted_plant')

    shop = c.building([(38, 12, 43, 18), (43, 13, 46, 18)], 'timber_wall', 'wood_floor')
    c.wall_line([(43, 13), (43, 18)], 'timber_wall', 'wood_floor')
    c.door(43, 15)
    c.door(40, 18)
    for x in range(39, 43):
        c.obj(x, 14, 'counter')
    for (x, y, o) in ((42, 13, 'crate'), (39, 13, 'wall_torch'), (39, 17, 'showcase'), (42, 17, 'barrel'),
                      (44, 14, 'crate'), (45, 14, 'crate'), (45, 16, 'barrel'), (45, 17, 'barrel'), (44, 17, 'crate')):
        c.obj(x, y, o)

    fisher = c.building([(48, 13, 51, 17)], 'timber_wall', 'wood_floor')
    c.door(49, 17)
    for (x, y, o) in ((49, 14, 'bed'), (50, 14, 'wardrobe'), (50, 16, 'table'), (50, 15, 'chair')):
        c.obj(x, y, o)

    smithy = c.building([(45, 24, 50, 28)], 'stone_wall', 'stone_floor')
    c.door(45, 27)
    for (x, y, o) in ((49, 25, 'forge'), (47, 25, 'anvil'), (49, 27, 'firewood'), (46, 25, 'bucket'),
                      (48, 27, 'barrel'), (46, 26, 'wall_torch')):
        c.obj(x, y, o)

    tower = c.building([(38, 27, 42, 31)], 'stone_wall', 'stone_floor')
    c.door(40, 27)
    c.obj(41, 30, 'stairs_up')
    c.obj(39, 30, 'wall_torch')

    tavern = c.building([(43, 30, 48, 34)], 'timber_wall', 'wood_floor')
    c.door(44, 30)
    c.door(46, 34)
    for (x, y, o) in ((44, 31, 'wall_torch'), (47, 31, 'counter'), (47, 32, 'counter'), (47, 33, 'barrel'),
                      (45, 32, 'table'), (44, 32, 'chair'), (46, 32, 'chair')):
        c.obj(x, y, o)

    house = c.building([(33, 27, 37, 31), (34, 31, 37, 33)], 'whitewash_wall', 'wood_floor')
    c.door(33, 29)
    for (x, y, o) in ((36, 28, 'bed'), (34, 28, 'wardrobe'), (36, 32, 'table'), (35, 32, 'chair'),
                      (34, 30, 'potted_plant'), (36, 30, 'wall_torch')):
        c.obj(x, y, o)

    magic = c.building([(20, 27, 25, 32), (20, 24, 23, 27)], 'plaster_wall', 'wood_floor')
    c.door(25, 29)
    for x in range(21, 24):
        c.obj(x, 30, 'counter')
    for (x, y, o) in ((21, 25, 'bookcase'), (22, 25, 'bookcase'), (24, 28, 'showcase'), (24, 31, 'wall_torch'),
                      (21, 28, 'potted_plant'), (21, 26, 'lectern')):
        c.obj(x, y, o)

    # --- wyposazenie ulic ---
    c.obj(31, 23, 'fountain')
    for (x, y) in ((29, 20), (34, 20), (25, 24), (38, 24), (20, 23), (44, 21), (27, 9), (36, 8), (50, 23)):
        c.obj(x, y, 'street_lamp')
    for (x, y) in ((28, 23), (35, 23)):
        c.obj(x, y, 'bench')
    for (x, y) in ((27, 21), (36, 21), (27, 25), (36, 25)):
        c.put(x, y, ('grass', 'flowers', '-', '-'))
    c.obj(26, 25, 'potted_plant')
    c.obj(37, 25, 'potted_plant')
    for (x, y) in ((27, 28), (27, 31), (29, 30)):
        c.obj(x, y, 'market_stall')
    for (x, y, o) in ((28, 33, 'well'), (30, 28, 'crate'), (26, 31, 'barrel'), (30, 32, 'signpost'),
                      (46, 21, 'signpost'), (25, 19, 'flower_box'), (39, 21, 'flower_box'), (45, 29, 'anvil'),
                      (44, 28, 'hay_bale'), (48, 19, 'fishing_net'), (49, 21, 'crate'), (48, 35, 'barrel'),
                      (47, 36, 'crate')):
        c.obj(x, y, o)
    # ogrody za sklepem i za swiatynia
    for (x, y, o) in ((40, 10, 'tree'), (44, 10, 'tree'), (42, 11, 'bush')):
        if (x, y) in inside:
            c.obj(x, y, o)

    # --- baszty na murach (zamkniete, kryte dachem) ---
    for (tx, ty) in TOWERS:
        c.building([(tx, ty, tx + 2, ty + 2)], 'stone_wall', 'stone_floor')
    # lodki przy nabrzezu
    for (x, y) in ((54, 17), (50, 33)):
        if c.at(x, y)[0] == 'water':
            c.obj(x, y, 'rowboat')
    return inside


def surface():
    old = old_terrain()
    c = tk.Canvas(W, H)
    for y in range(H):
        for x in range(W):
            c.put(x, y, LEGEND.cell(old[y][x]))
    r = random.Random(20261023)
    forest(c, r)
    water(c, r)
    roads(c, r)
    mine_and_fields(c, r)
    inside = town(c, r)
    return c, inside


def tower_floor(z):
    g = grid(' ')
    if z == 5:
        box(g, 37, 26, 43, 32, '#', '-')
        g[28][39] = '>'
        g[27][38] = 'l'
        g[31][42] = 'l'
    else:
        box(g, 38, 27, 42, 31, '#', '-')
        g[28][39] = '>'
        g[30][41] = '<'
    return g


# kluczowe punkty kazdego pietra podziemi (musza byc polaczone)
CAVE_POINTS = {
    -1: [(8, 23), (8, 22), (14, 28), (14, 29)],
    -2: [(14, 27), (14, 28), (4, 30), (4, 29)],
    -3: [(5, 30), (4, 30), (15, 16), (15, 15)],
    -4: [(15, 17), (15, 16), (6, 18), (6, 17)],
    -5: [(7, 18), (6, 18), (12, 27)],
}
CAVE_MARKS = {
    -1: {(8, 22): '(', (14, 28): ')'},
    -2: {(14, 28): '(', (4, 30): 'O'},
    -3: {(4, 30): 'H', (15, 16): ')'},
    -4: {(15, 16): '(', (6, 18): 'O'},
    -5: {(6, 18): 'H'},
}
X0, Y0, X1, Y1 = 2, 14, 17, 32


def carve(g, x, y):
    if X0 < x < X1 and Y0 < y < Y1:
        g[y][x] = 'c'


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


def cave_floor(z):
    g = grid(' ')
    rect(g, X0, Y0, X1, Y1, 'C')
    pts = CAVE_POINTS[z]
    for (px, py) in pts:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                carve(g, px + dx, py + dy)
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        x, y = ax, ay
        while x != bx:
            carve(g, x, y); carve(g, x, y + 1)
            x += 1 if bx > x else -1
        while y != by:
            carve(g, x, y); carve(g, x + 1, y)
            y += 1 if by > y else -1
    # dodatkowe komory (expowiska na pozniejsze etapy)
    for _ in range(3 + (-z)):
        cx, cy = rnd.randint(X0 + 2, X1 - 3), rnd.randint(Y0 + 2, Y1 - 3)
        rw, rh = rnd.randint(1, 2), rnd.randint(1, 2)
        for y in range(cy - rh, cy + rh + 1):
            for x in range(cx - rw, cx + rw + 1):
                carve(g, x, y)
        x, y = cx, cy
        tx, ty = pts[0]
        while x != tx:
            carve(g, x, y)
            x += 1 if tx > x else -1
        while y != ty:
            carve(g, x, y)
            y += 1 if ty > y else -1
    if z == -5:  # komora bossa
        for y in range(24, 31):
            for x in range(9, 16):
                carve(g, x, y)
    for (mx, my), ch in CAVE_MARKS[z].items():
        g[my][mx] = ch
    # dekoracje: pochodnie plytko, krysztaly gleboko, gruz wszedzie (bez psucia przejsc)
    protected = set(pts)
    cells = [(x, y) for y in range(H) for x in range(W) if g[y][x] == 'c' and (x, y) not in protected]
    rnd.shuffle(cells)
    want = {'j': 3 if z >= -2 else 1, 'g': 0 if z >= -2 else 2 + (-z - 3), 'm': 3}
    walk_count = len(reachable(g, pts[0]))
    for ch, n in want.items():
        for (x, y) in cells:
            if n <= 0:
                break
            if g[y][x] != 'c':
                continue
            g[y][x] = ch
            if ch in WALKABLE:
                n -= 1
                continue
            # przeszkoda nie moze odciac zadnej czesci jaskini (tylko sam kafelek wypada z zasiegu)
            if len(reachable(g, pts[0])) == walk_count - 1:
                walk_count -= 1
                n -= 1
            else:
                g[y][x] = 'c'
    reach = reachable(g, pts[0])
    missing = [p for p in pts if p not in reach]
    assert not missing, f'floor {z}: unreachable {missing}'
    return g


def write(z, g):
    name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        for row in g:
            f.write(''.join(row).rstrip() + '\n')


# miejsca, do ktorych musi dac sie dojsc ze swiatyni (NPC, drzwi, bramy, drogi na krawedziach mapy, schody)
MUST_REACH = [(22, 15), (40, 15), (22, 28), (34, 15), (29, 16), (37, 22), (40, 30), (8, 21), (53, 22), (31, 0), (31, 47),
              (63, 22), (10, 40), (44, 15), (49, 15), (47, 26), (45, 31), (35, 29), (21, 19), (24, 26), (16, 22), (48, 6)]


def write_legend():
    lines = ['// symbol;podloze;dekoracja;obiekt;sciana  ("-" = brak, "space" = pusty kafelek)']
    lines += [';'.join(e) for e in LEGEND.base]
    if LEGEND.extra:
        lines.append('// --- miasto Varden (generate_varden.py, town.py): nawierzchnie, mury, wyposazenie ---')
        lines += [';'.join(e) for e in LEGEND.extra]
    with open(os.path.join(OUT, 'legend.csv'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def main():
    os.makedirs(OUT, exist_ok=True)
    canvas, inside = surface()
    reach = tk.reachable(canvas, (31, 16))
    missing = [p for p in MUST_REACH if p not in reach]
    assert not missing, f'Varden: nieosiagalne {missing}'
    write(0, canvas.to_rows(LEGEND))
    for z in range(1, 6):
        write(z, tower_floor(z))
    for z in range(-1, -6, -1):
        write(z, cave_floor(z))
    tr = ['# x;y;z;typ;docelowe x;y;z',
          '# wieza: schody w gore (41,30) i w dol (39,28)']
    for z in range(0, 5):
        tr.append(f'41;30;{z};STAIRS;40;30;{z + 1}')
    for z in range(1, 6):
        tr.append(f'39;28;{z};STAIRS;40;28;{z - 1}')
    tr += ['# kopalnia pod Varden',
           '8;22;0;STAIRS;8;23;-1',
           '8;22;-1;STAIRS;8;21;0',
           '14;28;-1;STAIRS;14;27;-2',
           '14;28;-2;STAIRS;14;29;-1',
           '4;30;-2;HOLE;5;30;-3',
           '4;30;-3;LADDER;4;29;-2',
           '15;16;-3;STAIRS;15;17;-4',
           '15;16;-4;STAIRS;15;15;-3',
           '6;18;-4;HOLE;7;18;-5',
           '6;18;-5;LADDER;6;17;-4']
    with open(os.path.join(OUT, 'transitions.csv'), 'w') as f:
        f.write('\n'.join(tr) + '\n')
    write_legend()
    pz = ';'.join(f'{x0},{y0},{x1},{y1},0' for x0, y0, x1, y1 in tk.rects_of(inside))
    with open(os.path.join(OUT, 'map.properties'), 'w') as f:
        f.write('# Region startowy: miasto Varden (klimat umiarkowany)\n'
                'name=Varden\n'
                f'width={W}\nheight={H}\n'
                'floors=-5,-4,-3,-2,-1,0,1,2,3,4,5\n'
                '# swiatynia w Varden - bezpieczne miejsce startu\n'
                'spawn=31,16,0\n'
                '# strefy ochronne (x0,y0,x1,y1,z): wnetrze murow miasta i wieza\n'
                'pz=' + pz + ';37,26,43,32,1;37,26,43,32,2;37,26,43,32,3;37,26,43,32,4;37,26,43,32,5\n')


if __name__ == '__main__':
    main()
