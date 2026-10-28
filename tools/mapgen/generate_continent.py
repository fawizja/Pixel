#!/usr/bin/env python3
"""Swiat gry na kontynencie ze szkicu Darka (2026-10-23).

Uruchom z katalogu repozytorium PO generate_varden.py:
    python3 tools/mapgen/generate_continent.py
Wynik: shared/src/main/resources/maps/world/ (pietra, legenda, przejscia, NPC, spawny, domy, rejsy, map.properties).

Jak to dziala:
1. generate_world.py buduje stary uklad swiata (352 x 320) w katalogu roboczym build/legacy.
2. Z niego biore "klocki": miasta (Varden z dzielnica krolewska, Frostholm, Selvaran z portem, Qasirah),
   piramide, ruiny, oboz, doline wiwern, wyspy, wejscia do lochow i plaskowyze (+1..+3). Kazdy klocek ma nowe
   miejsce na kontynencie; podziemia (-1..-5) sa przenoszone osobno - kazdy kompleks lochow tam, gdzie jest wolne
   miejsce, najblizej swojego wejscia.
3. Teren (continent.py) powstaje wokol klockow: ksztalt ladu ze szkicu, gory z przeleczami, biomy, rzeki
   z delta, jeziora. Potem drogi, mosty, wioski, przystanie, wulkan, jaskinie i spawny wg klimatu.
Wszystkie wspolrzedne (przejscia, NPC, spawny, domy, strefy, regiony) sa przeliczane przez te same przesuniecia.
Generator jest deterministyczny.
"""
import heapq
import math
import os
import random
import sys
from collections import deque

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import continent as C  # noqa: E402
import town as tk  # noqa: E402
import features as F  # noqa: E402
import sidewalk  # noqa: E402
import nature  # noqa: E402
import newtowns  # noqa: E402
import organic  # noqa: E402
import underground as U  # noqa: E402
import wall_lights  # noqa: E402
import quests  # noqa: E402
import boss_quests  # noqa: E402

ROOT = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources', 'maps')
OUT = os.path.join(ROOT, 'world')
LEGACY = os.path.join(HERE, 'build', 'legacy')
S = C.S
FLOORS = list(range(-5, 6))
NONE = '-'
WATER = ('water', NONE, NONE, NONE)

# ------------------------------------------------------------------ klocki starego swiata
# nazwa, rodzaj, prostokat w starym swiecie (x0, y0, x1, y1), nowy lewy gorny rog
#   full     - cala powierzchnia prostokata (z woda) i pietra nad nia
#   island   - lad wyspy (bez morza dookola), przystan i lodz; pietra nad nia
#   entrance - tylko otoczenie wejsc na powierzchni (promien 2) i pietra nad nimi (plaskowyze)
BLOCKS = [
    ('varden', 'full', (46, 64, 157, 111), (465, 483)),
    ('frostholm', 'full', (60, 4, 100, 47), (722, 186)),
    ('selvaran', 'full', [(98, 112, 159, 176), (111, 177, 159, 181)], (657, 672)),
    ('qasirah', 'full', (54, 177, 110, 222), (187, 465)),
    ('pyramid', 'full', (113, 198, 136, 216), (110, 556)),
    ('ruins', 'full', (219, 195, 241, 215), (204, 674)),
    ('camp', 'full', (170, 78, 188, 95), (447, 265)),
    ('wyvern', 'full', (12, 101, 32, 111), (300, 215)),
    ('coral', 'island', (95, 244, 146, 283), (250, 790)),
    ('gull', 'island', (184, 242, 197, 256), (120, 40)),
    ('lighthouse', 'island', (276, 103, 298, 119), (60, 200)),
    ('pirate', 'island', (26, 255, 70, 300), (670, 910)),
    ('mist', 'island', (222, 255, 280, 304), (930, 515)),
    ('ice', 'island', (274, 18, 336, 58), (880, 20)),
    ('volcano_isle', 'island', (279, 168, 337, 212), (40, 80)),
    ('ice_caves', 'entrance', (20, 30, 20, 30), (760, 95)),
    ('jungle_ruins', 'entrance', (60, 150, 60, 150), (703, 586)),
    ('abyss', 'entrance', (196, 216, 196, 216), (160, 640)),
    ('turnie', 'entrance', (36, 10, 36, 10), (520, 240)),
    ('szczyty', 'entrance', (214, 30, 214, 30), (600, 60)),
    ('mesa', 'entrance', (30, 200, 30, 200), (250, 330)),
]


def floor_name(z):
    return f'floor_{"+" + str(z) if z > 0 else z}.txt'


class Legacy:
    """Stary swiat z build/legacy jako pelne kafle (None = pusty kafel)."""

    def __init__(self, d):
        self.dir = d
        self.legend_rows = []
        self.cell_of = {}
        with open(os.path.join(d, 'legend.csv'), encoding='utf-8') as f:
            for line in f:
                line = line.rstrip('\n')
                if not line or line.startswith('//'):
                    continue
                p = line.split(';')
                sym = ' ' if p[0] == 'space' else p[0]
                cell = None if sym == ' ' else tuple(p[1:5])
                self.cell_of[sym] = cell
                if sym != ' ':
                    self.legend_rows.append((sym, *p[1:5]))
        props = {}
        with open(os.path.join(d, 'map.properties'), encoding='utf-8') as f:
            for line in f:
                if '=' in line and not line.startswith('#'):
                    k, v = line.rstrip('\n').split('=', 1)
                    props[k] = v
        self.props = props
        self.w, self.h = int(props['width']), int(props['height'])
        self.floors = {}
        for z in FLOORS:
            with open(os.path.join(d, floor_name(z)), encoding='utf-8') as f:
                rows = f.read().split('\n')
            g = [[None] * self.w for _ in range(self.h)]
            for y, row in enumerate(rows[:self.h]):
                for x, ch in enumerate(row[:self.w]):
                    g[y][x] = self.cell_of[ch]
            self.floors[z] = g
        self.transitions = [tuple(int(v) if i != 3 else v for i, v in enumerate(line.split(';')))
                            for line in self.lines('transitions.csv')]
        self.spawns = [line for line in open(os.path.join(d, 'spawns.csv'), encoding='utf-8').read().split('\n') if line]
        self.npcs = [line.split(';') for line in self.lines('npcs.csv')]
        self.houses = [line.split(';') for line in self.lines('houses.csv')]

    def lines(self, name):
        with open(os.path.join(self.dir, name), encoding='utf-8') as f:
            return [line.rstrip('\n') for line in f if line.strip() and not line.startswith('#')]

    def at(self, x, y, z):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.floors[z][y][x]
        return None


def components(legacy, zs):
    """Kompleksy pieter zs: skladowe (8-sasiedztwo) polaczone przejsciami miedzy pietrami zs."""
    comp_of = {}
    comps = []
    for z in zs:
        g = legacy.floors[z]
        for y in range(legacy.h):
            for x in range(legacy.w):
                if g[y][x] is None or (z, x, y) in comp_of:
                    continue
                cid = len(comps)
                cells = []
                q = deque([(x, y)])
                comp_of[(z, x, y)] = cid
                while q:
                    cx, cy = q.popleft()
                    cells.append((z, cx, cy))
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            nx, ny = cx + dx, cy + dy
                            if legacy.at(nx, ny, z) is not None and (z, nx, ny) not in comp_of:
                                comp_of[(z, nx, ny)] = cid
                                q.append((nx, ny))
                comps.append(cells)
    # laczenie przejsciami (union-find)
    parent = list(range(len(comps)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for (x, y, z, _t, tx, ty, tz) in legacy.transitions:
        a, b = comp_of.get((z, x, y)), comp_of.get((tz, tx, ty))
        if a is not None and b is not None:
            parent[find(a)] = find(b)
    groups = {}
    for cid, cells in enumerate(comps):
        groups.setdefault(find(cid), []).extend(cells)
    out = []
    of = {}
    for cells in groups.values():
        gid = len(out)
        out.append(cells)
        for c in cells:
            of[c] = gid
    return out, of


class Block:
    def __init__(self, name, kind, rect, tl):
        self.rects = rect if isinstance(rect, list) else [rect]
        rect = (min(r[0] for r in self.rects), min(r[1] for r in self.rects),
                max(r[2] for r in self.rects), max(r[3] for r in self.rects))
        self.name, self.kind, self.rect, self.tl = name, kind, rect, tl
        self.dx, self.dy = tl[0] - rect[0], tl[1] - rect[1]
        self.surface = set()          # stare pola powierzchni kopiowane przez klocek
        self.upper = []               # kompleksy pieter +1..+5

    def new(self, x, y):
        return x + self.dx, y + self.dy


def build_blocks(legacy, upper_comps):
    blocks = [Block(*b) for b in BLOCKS]
    g0 = legacy.floors[0]
    for b in blocks:
        x0, y0, x1, y1 = b.rect
        if b.kind == 'full':
            b.surface = {(x, y) for (rx0, ry0, rx1, ry1) in b.rects for y in range(ry0, ry1 + 1) for x in range(rx0, rx1 + 1)}
        elif b.kind == 'island':
            # lad wyspy z przystania: pola nie-wody polaczone ze srodkiem prostokata (4-sasiedztwo)
            cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
            land = lambda x, y: x0 <= x <= x1 and y0 <= y <= y1 and g0[y][x] is not None and g0[y][x][0] != 'water'  # noqa: E731
            if not land(cx, cy):
                cands = [(abs(x - cx) + abs(y - cy), x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1) if land(x, y)]
                _d, cx, cy = min(cands)
            seen = {(cx, cy)}
            q = deque([(cx, cy)])
            while q:
                x, y = q.popleft()
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = (x + dx, y + dy)
                    if n not in seen and land(*n):
                        seen.add(n)
                        q.append(n)
            b.surface = seen
        else:
            b.surface = {(x, y) for y in range(y0 - 2, y1 + 3) for x in range(x0 - 2, x1 + 3)}
    # pietra nad powierzchnia: kompleks nalezy do klocka, ktory obejmuje jego wejscie (albo wiekszosc pol)
    for cells in upper_comps:
        votes = {}
        entry = set()
        for (x, y, z, _t, tx, ty, tz) in legacy.transitions:
            if z == 0 and tz > 0 and (tz, tx, ty) in cells_set(cells):
                entry.add((x, y))
        for b in blocks:
            if any(p in b.surface for p in entry):
                votes[b.name] = 1e9
            n = sum(1 for (_z, x, y) in cells if b.rect[0] - 30 <= x <= b.rect[2] + 30 and b.rect[1] - 30 <= y <= b.rect[3] + 30
                    and (b.kind != 'island' or (x, y) in b.surface))
            if b.kind == 'full' and all(b.rect[0] <= x <= b.rect[2] and b.rect[1] <= y <= b.rect[3] for (_z, x, y) in cells):
                n += 1e6
            if n:
                votes[b.name] = votes.get(b.name, 0) + n
        if not votes:
            raise AssertionError(f'upper complex without block at {cells[0]}')
        best = max(votes, key=votes.get)
        next(b for b in blocks if b.name == best).upper.append(cells)
    return blocks


_cs_cache = {}


def cells_set(cells):
    k = id(cells)
    if k not in _cs_cache:
        _cs_cache[k] = set(cells)
    return _cs_cache[k]


class World:
    def __init__(self):
        self.floors = {z: [[None] * S for _ in range(S)] for z in FLOORS}
        self.occ = {z: np.zeros((S, S), bool) for z in FLOORS if z != 0}

    def get(self, x, y, z=0):
        if 0 <= x < S and 0 <= y < S:
            return self.floors[z][y][x]
        return None

    def put(self, x, y, z, cell):
        if 0 <= x < S and 0 <= y < S:
            self.floors[z][y][x] = cell


def place_underground(legacy, comps, block_of_surface, world):
    """Kazdy kompleks lochow: najblizej przesuniecia jego wejscia, bez nakladania sie z innymi (margines 3)."""
    # najpierw lochy pod Varden (musza zostac 1:1 pod miastem - test zgodnosci z maps/varden), potem od najwiekszych
    def under_varden(i):
        cs = cells_set(comps[i])
        for (x, y, z, _t, tx, ty, tz) in legacy.transitions:
            if z == 0 and tz < 0 and (tz, tx, ty) in cs:
                b = block_of_surface(x, y)
                return b is not None and b.name == 'varden'
        return False
    order = sorted(range(len(comps)), key=lambda i: (not under_varden(i), -len(comps[i])))
    offsets = [None] * len(comps)
    for cid in order:
        cells = comps[cid]
        cs = cells_set(cells)
        want = None
        for (x, y, z, _t, tx, ty, tz) in legacy.transitions:
            if z == 0 and tz < 0 and (tz, tx, ty) in cs:
                b = block_of_surface(x, y)
                if b is not None:
                    want = (b.dx, b.dy)
                    break
        if want is None:
            raise AssertionError(f'underground complex without entrance {cells[0]}')
        xs = [c[1] for c in cells]
        ys = [c[2] for c in cells]
        bx0, by0, bx1, by1 = min(xs), min(ys), max(xs), max(ys)
        w, h = bx1 - bx0 + 1, by1 - by0 + 1
        masks = {}
        for (z, x, y) in cells:
            masks.setdefault(z, np.zeros((h, w), bool))[y - by0, x - bx0] = True
        offsets[cid] = find_free(world, masks, bx0 + want[0], by0 + want[1])
        ox, oy = offsets[cid][0] - bx0, offsets[cid][1] - by0
        offsets[cid] = (ox, oy)
        for (z, x, y) in cells:
            world.put(x + ox, y + oy, z, legacy.floors[z][y][x])
        mark(world, masks, bx0 + ox, by0 + oy)
    return offsets


def find_free(world, masks, x0, y0, step=3):
    """Najblizszy (x0, y0) lewy gorny rog, w ktorym maski pieter nie nachodza na zajete pola."""
    h, w = next(iter(masks.values())).shape
    for r in range(0, S):
        ring = [(dx, dy) for dx in range(-r, r + 1) for dy in range(-r, r + 1) if max(abs(dx), abs(dy)) == r]
        ring.sort(key=lambda d: d[0] * d[0] + d[1] * d[1])
        for (ddx, ddy) in ring:
            nx, ny = x0 + ddx * step, y0 + ddy * step
            if nx < 2 or ny < 2 or nx + w > S - 2 or ny + h > S - 2:
                continue
            if all(not (world.occ[z][ny:ny + h, nx:nx + w] & m).any() for z, m in masks.items()):
                return nx, ny
    raise AssertionError('no room underground')


def mark(world, masks, nx, ny, margin=3):
    for z, m in masks.items():
        h, w = m.shape
        big = np.zeros((h + 2 * margin, w + 2 * margin), bool)
        for dy in range(2 * margin + 1):
            for dx in range(2 * margin + 1):
                big[dy:dy + h, dx:dx + w] |= m
        y0, x0 = ny - margin, nx - margin
        ys0, xs0 = max(0, -y0), max(0, -x0)
        y1, x1 = min(S, y0 + h + 2 * margin), min(S, x0 + w + 2 * margin)
        world.occ[z][max(0, y0):y1, max(0, x0):x1] |= big[ys0:ys0 + y1 - max(0, y0), xs0:xs0 + x1 - max(0, x0)]


def run_legacy():
    import generate_world
    generate_world.OUT = LEGACY
    generate_world.main()


NATURAL_GROUND = {'grass', 'snow', 'sand', 'jungle_grass', 'dark_sand', 'blighted_grass', 'ash'}
NATURAL_DECO = {NONE, 'tall_grass', 'flowers', 'snow_tuft', 'fern', 'dry_grass', 'shells', 'bones', 'mushrooms',
                'jungle_plant', 'reeds', 'dry_shrub', 'debris'}
NATURAL_OBJ = {NONE, 'tree', 'pine', 'palm', 'bush', 'rock', 'cactus', 'snow_rock', 'sandstone_rock', 'dead_tree',
               'obsidian_rock', 'jungle_tree', 'jungle_bush', 'frost_pine', 'frost_bush', 'dry_bush', 'autumn_tree',
               'burnt_tree', 'ice_crystal'}
VARDEN_CORE = (48, 64, 111, 111)      # kopia maps/varden - zostaje 1:1 (test GameWorldTest)
BAND = 2


def natural(c):
    return c is not None and c[0] in NATURAL_GROUND and c[1] in NATURAL_DECO and c[2] in NATURAL_OBJ and c[3] == NONE


class Remap:
    """Przeliczanie wspolrzednych starego swiata na nowe (przez klocki i kompleksy lochow)."""

    def __init__(self, legacy, blocks, surf_index, upper_of, upper_owner, under_of, offsets, under_cells):
        self.legacy, self.blocks, self.surf_index = legacy, blocks, surf_index
        self.upper_of, self.upper_owner, self.under_of, self.offsets = upper_of, upper_owner, under_of, offsets
        self.under_cells = under_cells

    def offset(self, x, y, z):
        if z < 0:
            cid = self.under_of.get((z, x, y))
            if cid is None:
                return None
            return self.offsets[cid]
        if z > 0:
            cid = self.upper_of.get((z, x, y))
            if cid is not None:
                b = self.upper_owner[cid]
                return (b.dx, b.dy)
            b = self.surf_index.get((x, y))
            return (b.dx, b.dy) if b is not None else None
        b = self.surf_index.get((x, y))
        return (b.dx, b.dy) if b is not None else None

    def point(self, x, y, z):
        o = self.offset(x, y, z)
        return None if o is None else (x + o[0], y + o[1], z)

    def rect(self, x0, y0, x1, y1, z):
        """Prostokat przesuniety tak jak wiekszosc jego pol (strefy ochronne siegaja czasem poza klocek)."""
        votes = {}
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                o = self.offset(x, y, z)
                if o is not None:
                    votes[o] = votes.get(o, 0) + 1
        if not votes:
            return None
        a = max(votes, key=votes.get)
        return (x0 + a[0], y0 + a[1], x1 + a[0], y1 + a[1])

    def pieces(self):
        """Kawalki swiata do przeliczania regionow: (z od, z do, stary prostokat, dx, dy)."""
        out = []
        for b in self.blocks:
            if b.kind != 'entrance':
                out.append((0, 0, b.rect, b.dx, b.dy))
            for cells in b.upper:
                xs = [c[1] for c in cells]
                ys = [c[2] for c in cells]
                zs = [c[0] for c in cells]
                out.append((min(zs), max(zs), (min(xs), min(ys), max(xs), max(ys)), b.dx, b.dy))
        for cid, cells in enumerate(self.under_cells):
            xs = [c[1] for c in cells]
            ys = [c[2] for c in cells]
            zs = [c[0] for c in cells]
            ox, oy = self.offsets[cid]
            out.append((min(zs), max(zs), (min(xs), min(ys), max(xs), max(ys)), ox, oy))
        return out


def legacy_regions(legacy, remap):
    out = []
    pieces = remap.pieces()
    for entry in legacy.props['regions'].split('|'):
        f = entry.split(';')
        name = f[0]
        x0, y0, x1, y1, zf, zt, lf, lt = (int(v) for v in f[1:9])
        for (pz0, pz1, (px0, py0, px1, py1), dx, dy) in pieces:
            a, b = max(zf, pz0), min(zt, pz1)
            ix0, iy0, ix1, iy1 = max(x0, px0), max(y0, py0), min(x1, px1), min(y1, py1)
            if a > b or ix0 > ix1 or iy0 > iy1:
                continue
            # szerokie regiony powierzchni (biomy starego swiata) - tylko gdy obejmuja caly klocek;
            # wybrzeze i morze dostaja nowe regiony
            if a == 0 and b == 0 and name.startswith('Morze'):
                continue
            out.append((name, ix0 + dx, iy0 + dy, ix1 + dx, iy1 + dy, a, b, lf, lt))
    return out


def symbol_pool(used):
    out = []
    for c in range(0x100, 0xD7FF):
        ch = chr(c)
        if ch in used or ch.isspace() or 0x300 <= c <= 0x36F or ch in ';/#' or not ch.isprintable():
            continue
        if 0x0483 <= c <= 0x0489 or 0x0591 <= c <= 0x05C7 or 0x0610 <= c <= 0x061A or 0x064B <= c <= 0x065F:
            continue
        import unicodedata
        if unicodedata.category(ch) in ('Mn', 'Mc', 'Me', 'Cf', 'Co', 'Cn', 'Zs', 'Zl', 'Zp'):
            continue
        out.append(ch)
    return out


def write_world(world, legacy, extra_props, transitions, npcs, spawns, houses, routes):
    os.makedirs(OUT, exist_ok=True)
    base = list(legacy.legend_rows)
    used = {r[0] for r in base}
    legend = tk.Legend(base, pool=symbol_pool(used))
    for z in FLOORS:
        g = world.floors[z]
        with open(os.path.join(OUT, floor_name(z)), 'w', encoding='utf-8') as f:
            for row in g:
                f.write(''.join(' ' if c is None else legend.sym(c) for c in row).rstrip(' ') + '\n')
    with open(os.path.join(OUT, 'legend.csv'), 'w', encoding='utf-8') as f:
        f.write('// symbol;podloze;dekoracja;obiekt;sciana  ("-" = brak, "space" = pusty kafelek)\n')
        f.write('space;-;-;-;-\n')
        for row in base:
            f.write(';'.join(row) + '\n')
        if legend.extra:
            f.write('// --- kontynent (generate_continent.py) ---\n')
            for row in legend.extra:
                f.write(';'.join(row) + '\n')
    with open(os.path.join(OUT, 'transitions.csv'), 'w', encoding='utf-8') as f:
        f.write('# x;y;z;typ;docelowe x;y;z\n')
        for t in transitions:
            f.write(';'.join(str(v) for v in t) + '\n')
    with open(os.path.join(OUT, 'npcs.csv'), 'w', encoding='utf-8') as f:
        f.write('# klucz;imie;rola;miasto;x;y;z;sklep;kolor;powitanie\n')
        for n in npcs:
            f.write(';'.join(str(v) for v in n) + '\n')
    with open(os.path.join(OUT, 'spawns.csv'), 'w', encoding='utf-8') as f:
        f.write('# potwor;x;y;z;promien;liczba;respawn (s)[;noc - tylko w nocy, na powierzchni]\n')
        f.write('\n'.join(spawns) + '\n')
    with open(os.path.join(OUT, 'houses.csv'), 'w', encoding='utf-8') as f:
        f.write('# id;nazwa;miasto;wnetrze x0;y0;x1;y1;z;drzwi x;drzwi y;cena (gp, z konta)\n')
        for h in houses:
            f.write(';'.join(str(v) for v in h) + '\n')
    with open(os.path.join(OUT, 'ships.csv'), 'w', encoding='utf-8') as f:
        f.write('# rejsy statkiem w obie strony: kapitan;kapitan (kapitanowie w npcs.csv)\n')
        for a, b in routes:
            f.write(f'{a};{b}\n')
    with open(os.path.join(OUT, 'map.properties'), 'w', encoding='utf-8') as f:
        f.write('# Swiat gry: kontynent ze szkicu Darka (generate_continent.py)\n'
                'name=Swiat Pixel\n'
                f'width={S}\nheight={S}\n'
                'floors=' + ','.join(str(z) for z in FLOORS) + '\n')
        for k, v in extra_props:
            if k.startswith('#'):
                f.write(k + '\n')
            else:
                f.write(f'{k}={v}\n')


def main():
    run_legacy()
    legacy = Legacy(LEGACY)
    upper, upper_of = components(legacy, [z for z in FLOORS if z > 0])
    under, under_of = components(legacy, [z for z in FLOORS if z < 0])
    blocks = build_blocks(legacy, upper)
    surf_index = {}
    for b in blocks:
        for p in b.surface:
            assert p not in surf_index, f'{b.name} overlaps {surf_index[p].name} at {p}'
            surf_index[p] = b
    upper_owner = {}
    for b in blocks:
        for cells in b.upper:
            upper_owner[upper_of[cells[0]]] = b

    def block_of_surface(x, y):
        return surf_index.get((x, y))

    # maski ladu i wody klockow
    force_land = np.zeros((S, S), bool)
    force_water = np.zeros((S, S), bool)
    footprint = np.zeros((S, S), bool)
    for b in blocks:
        for (x, y) in b.surface:
            c = legacy.floors[0][y][x]
            nx, ny = b.new(x, y)
            assert 0 <= nx < S and 0 <= ny < S, f'{b.name} outside the world at {nx},{ny}'
            footprint[ny, nx] = True
            if c is None or c == WATER:
                force_water[ny, nx] = True
            else:
                force_land[ny, nx] = True
    protect = footprint.copy()
    for _ in range(3):
        protect = protect | np.roll(protect, 1, 0) | np.roll(protect, -1, 0) | np.roll(protect, 1, 1) | np.roll(protect, -1, 1)
    T = C.Terrain(force_land, force_water)
    T.water_features(protect)
    T.islands()
    # woda klockow siegajaca ich brzegu (port Selvaran, rzeka Varden) polaczona z morzem albo rzeka
    groups = []
    seen = set()
    edge_of = {}
    for b in blocks:
        x0, y0, x1, y1 = b.rect
        for (x, y) in b.surface:
            if x in (x0, x1) or y in (y0, y1):
                edge_of[b.new(x, y)] = True
    for y in range(S):
        for x in range(S):
            if force_water[y, x] and (x, y) not in seen:
                comp = []
                q = deque([(x, y)])
                seen.add((x, y))
                while q:
                    cx, cy = q.popleft()
                    comp.append((cx, cy))
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        n = (cx + dx, cy + dy)
                        if 0 <= n[0] < S and 0 <= n[1] < S and force_water[n[1], n[0]] and n not in seen:
                            seen.add(n)
                            q.append(n)
                if any(p in edge_of for p in comp):
                    groups.append(comp)
    T.connect_water(groups, footprint & ~force_water)
    canvas = tk.Canvas(S, S, fill=WATER)
    C.paint(T, canvas, lambda x, y: False)
    fresh = [row[:] for row in canvas.g]
    world = World()
    world.floors[0] = canvas.g
    # klocki: powierzchnia i pietra nad nia; na brzegu klocka (pas BAND) naturalny teren starego swiata
    # ustepuje nowemu (bez wody i gor, zeby nie zamknac wyjsc z miasta)
    for b in blocks:
        x0, y0, x1, y1 = b.rect
        for (x, y) in b.surface:
            nx, ny = b.new(x, y)
            c = legacy.floors[0][y][x] or WATER
            edge = min(x - x0, x1 - x, y - y0, y1 - y)
            in_core = b.name == 'varden' and VARDEN_CORE[0] <= x <= VARDEN_CORE[2] and VARDEN_CORE[1] <= y <= VARDEN_CORE[3]
            if b.kind == 'full' and edge < BAND and not in_core and natural(c):
                f = fresh[ny][nx]
                if f[0] != 'water' and f[3] == NONE:
                    c = f
            world.put(nx, ny, 0, c)
        for cells in b.upper:
            for (z, x, y) in cells:
                nx, ny = b.new(x, y)
                assert world.get(nx, ny, z) is None, f'upper floors of {b.name} overlap at {nx},{ny},{z}'
                world.put(nx, ny, z, legacy.floors[z][y][x])
    offsets = place_underground(legacy, under, block_of_surface, world)
    remap = Remap(legacy, blocks, surf_index, upper_of, upper_owner, under_of, offsets, under)

    # ---------------------------------------------------------------- przeliczenie danych starego swiata
    transitions = []
    for (x, y, z, typ, tx, ty, tz) in legacy.transitions:
        a, b = remap.point(x, y, z), remap.point(tx, ty, tz)
        assert a is not None and b is not None, f'transition {x},{y},{z} -> {tx},{ty},{tz} not mapped'
        transitions.append((*a[:2], z, typ, *b[:2], tz))
    npcs = []
    unplaced = {}
    for n in legacy.npcs:
        key, name, role, town, x, y, z, shop, color, greet = n[:10]
        p = remap.point(int(x), int(y), int(z))
        if p is None:
            unplaced[key] = n
            continue
        npcs.append([key, name, role, town, p[0], p[1], p[2], shop, color, greet])
    spawns, dropped = [], []
    for line in legacy.spawns:
        if line.startswith('#'):
            spawns.append(line)
            continue
        k, x, y, z, *rest = line.split(';')
        p = remap.point(int(x), int(y), int(z))
        if p is None:
            dropped.append(line)
            continue
        spawns.append(';'.join([k, str(p[0]), str(p[1]), z] + rest))
    houses = []
    for h in legacy.houses:
        hid, hname, htown, x0, y0, x1, y1, z, dx_, dy_, price = h[:11]
        z = int(z)
        r = remap.rect(int(x0), int(y0), int(x1), int(y1), z)
        d = remap.point(int(dx_), int(dy_), z)
        assert r is not None and d is not None, f'house {hid} not mapped'
        houses.append([hid, hname, htown, *r, z, d[0], d[1], price])
    pz = []
    for rr in legacy.props['pz'].split(';'):
        x0, y0, x1, y1, z = (int(v) for v in rr.split(','))
        r = remap.rect(x0, y0, x1, y1, z)
        if r is not None:
            pz.append((*r, z))
    gates = []
    for rr in legacy.props['gates'].split(';'):
        x0, y0, x1, y1, z, lvl = (int(v) for v in rr.split(','))
        r = remap.rect(x0, y0, x1, y1, z)
        if r is not None:
            gates.append((*r, z, lvl))
    towns = []
    for entry in legacy.props['towns'].split(';'):
        name, xyz = entry.split(':')
        x, y, z = (int(v) for v in xyz.split(','))
        towns.append((name, remap.point(x, y, z)))
    regions = legacy_regions(legacy, remap)
    routes = [tuple(line.split(';')) for line in legacy.lines('ships.csv')]
    # nowe miasta z mapy Darka (2026-10-27): port Valmir i wyspiarskie Nerys
    new_towns = []
    trng = random.Random(20261027)
    for name in newtowns.TOWNS:
        t = newtowns.build(world, name, trng)
        x0, y0, x1, y1 = t['rect']
        for (x, y) in t['cells']:
            footprint[y, x] = True
        npcs += t['npcs']
        houses += t['houses']
        pz += [(*r, 0) for r in t['pz_rects']]
        towns.append((name, (*t['temple'], 0)))
        lo, hi = newtowns.TOWNS[name]['levels']
        regions.insert(0, (name, x0, y0, x1, y1, 0, 2, lo, hi))
        new_towns.append(t)
    ctx = dict(legacy=legacy, blocks=blocks, T=T, world=world, remap=remap, transitions=transitions, npcs=npcs,
               unplaced=unplaced, spawns=spawns, dropped=dropped, houses=houses, pz=pz, gates=gates, towns=towns,
               regions=regions, routes=routes, footprint=footprint, new_towns=new_towns, fresh=fresh)
    rebuild_towns(ctx, random.Random(20261030))
    return ctx


# ------------------------------------------------------------------ przebudowa starych miast (2026-10-28)
# Darek: "miasta nie takie same kwadratowe, losowe rozne ksztalty". Stare miasta (poza Varden) powstaja od nowa
# z organic.py w miejscu dawnych klockow. Zostaja zejscia do starych lochow, port Selvaran i klucze NPC (zadania).
REBUILD = {
    'Qasirah': dict(key='qas', centre=(215, 487), rx=36, ry=28, gates='NSEW', wall='sandstone_wall',
                    bwalls=['sandstone_wall'], floor='sandstone_floor', hall='sandstone_floor', street='sandstone_floor',
                    ground='sand', trees=['palm', 'dry_bush', 'cactus'], decos=['dry_grass', 'dry_shrub', 'flowers'],
                    house_label='Dom', houses=10, keep_below=None),
    'Frostholm': dict(key='frost', centre=(742, 207), rx=31, ry=25, gates='NSEW', wall='stone_wall',
                      bwalls=['wood_wall', 'log_wall', 'stone_wall'], floor='wood_floor', hall='stone_floor',
                      street='cobblestone', ground='snow', trees=['frost_pine', 'frost_bush', 'snow_rock'],
                      decos=['snow_tuft'], house_label='Chata', houses=10, keep_below=None),
    'Selvaran': dict(key='selv', centre=(687, 699), rx=34, ry=25, gates='NSEW', wall='mossy_wall',
                     bwalls=['wood_wall', 'mossy_wall', 'timber_wall'], floor='wood_floor', hall='stone_floor',
                     street='cobblestone', ground='jungle_grass', trees=['jungle_tree', 'palm', 'jungle_bush'],
                     decos=['fern', 'flowers', 'jungle_plant'], house_label='Dom', houses=10,
                     bounds=(655, 668, 716, 725), keep_below=726),
    # dzielnica krolewska Varden (wschodnia czesc klocka; stare miasto Varden zostaje 1:1)
    'Varden-East': dict(town='Varden', key='varden', centre=(555, 506), rx=24, ry=23, gates='NSEW',
                        wall='stone_wall', bwalls=['stone_wall', 'timber_wall', 'plaster_wall'], floor='wood_floor',
                        hall='marble_floor', street='cobblestone', ground='grass', trees=['tree', 'bush', 'autumn_tree'],
                        decos=['flowers', 'tall_grass'], house_label='Dzielnica Krolewska', houses=8,
                        bounds=(534, 482, 576, 530), old=(533, 483, 576, 530), keep_below=None, palace=True,
                        lockers=True, price_per_cell=10000),
}
KING = dict(key='varden_king', name='Król Aurelian', role='KING', shop='-', color='#c8a040', kind='king',
            greet='Witaj w moim królestwie. Konta premium i promocje załatwisz tutaj: napisz premium albo promocja.')
BUILDING_ROLES = {'PRIEST', 'BANKER', 'MERCHANT', 'HOUSING', 'GUIDE', 'CARRIER'}


def npc_kind(key, role):
    tail = key.split('_', 1)[1] if '_' in key else key
    if tail in organic.SHELVES or tail in organic.SHOP_EXTRA or tail in organic.OFFICE:
        return tail
    return {'BANKER': 'banker', 'HOUSING': 'housing', 'GUIDE': 'guide', 'CARRIER': 'carrier', 'PRIEST': 'priest'}.get(
        role, 'merchant')


def rebuild_towns(ctx, rng):
    world, fresh = ctx['world'], ctx['fresh']
    ctx['rebuilt'] = {}
    for entry, spec0 in REBUILD.items():
        spec = dict(spec0)
        name = spec.pop('town', entry)
        spec['name'] = name
        b = next(bb for bb in ctx['blocks'] if bb.name == TOWN_BLOCKS[name])
        old = spec.pop('old', None) or (b.rect[0] + b.dx, b.rect[1] + b.dy, b.rect[2] + b.dx, b.rect[3] + b.dy)
        palace = spec.get('palace')
        keep_below = spec.pop('keep_below')

        def in_old(x, y):
            return old[0] <= x <= old[2] and old[1] <= y <= old[3] and (keep_below is None or y < keep_below)
        # zejscia do starych lochow zostaja (pole schodow i pole wyjscia); pietra nad miastem znikaja
        reserved, links = {}, []
        for (x, y, z, typ, tx, ty, tz) in ctx['transitions']:
            if z == 0 and tz < 0 and in_old(x, y):
                reserved[(x, y)] = world.get(x, y)
            if z < 0 and tz == 0 and in_old(tx, ty):
                reserved[(tx, ty)] = world.get(tx, ty)
                links.append((tx, ty))
        ctx['transitions'][:] = [t for t in ctx['transitions']
                                 if not ((t[2] > 0 and in_old(t[0], t[1])) or (t[6] > 0 and in_old(t[4], t[5])))]
        for z in range(1, 6):
            for y in range(old[1], old[3] + 1):
                for x in range(old[0], old[2] + 1):
                    if in_old(x, y) and world.get(x, y, z) is not None:
                        world.floors[z][y][x] = None
        # stare budynki poza nowym obrysem ustepuja naturalnemu terenowi
        for y in range(old[1], old[3] + 1):
            for x in range(old[0], old[2] + 1):
                if in_old(x, y) and (x, y) not in reserved and not natural(world.get(x, y)):
                    f = fresh[y][x]
                    world.put(x, y, 0, f if f[0] != 'water' else (spec['ground'], NONE, NONE, NONE))
        for (x, y), c in reserved.items():
            world.put(x, y, 0, (spec['street'], NONE, c[2] if c[2] in F.STAIRS else NONE, NONE))
        mine = [n for n in ctx['npcs'] if n[3] == name and n[2] in BUILDING_ROLES and int(n[6]) >= 0
                and old[0] - 2 <= int(n[4]) <= old[2] + 2 and old[1] - 2 <= int(n[5]) <= old[3] + 2]
        assert sum(1 for n in mine if n[2] == 'PRIEST') == (0 if palace else 1), f'{name}: priest'
        mine.sort(key=lambda n: n[2] != 'PRIEST')
        ctx['npcs'][:] = [n for n in ctx['npcs'] if n not in mine]
        roles = [dict(key=n[0], name=n[1], role=n[2], shop=n[7], color=n[8], greet=n[9], kind=npc_kind(n[0], n[2]),
                      central=n[2] == 'BANKER' or n[0].endswith('innkeeper')) for n in mine]
        if palace:
            roles.insert(0, dict(KING))

        def house_here(h):
            return h[2] == name and in_old(int(h[8]), int(h[9]))
        prefix = next((h[0].rsplit('_', 1)[0] for h in ctx['houses'] if house_here(h)), spec['key'])
        spec['key'] = prefix
        t = organic.build(world, spec, rng, roles, houses=spec['houses'], reserved=set(reserved), links=links)
        spec['key'] = spec0['key']
        for n in t['npcs']:
            n[0] = next(r['key'] for r in roles if r['name'] == n[1])
        ctx['npcs'] += t['npcs']
        ctx['houses'][:] = [h for h in ctx['houses'] if not house_here(h)] + t['houses']
        for y in range(old[1], old[3] + 1):
            for x in range(old[0], old[2] + 1):
                if in_old(x, y):
                    ctx['footprint'][y, x] = False      # poza murami znow zwykly teren (drogi, stawy)
        for (x, y) in t['cells']:
            ctx['footprint'][y, x] = True
        x0, y0, x1, y1 = t['rect']
        ctx['pz'][:] = [r for r in ctx['pz'] if not (r[4] >= 0 and in_old(r[0], r[1]) and in_old(r[2], r[3]))]
        ctx['pz'] += [(*r, 0) for r in t['pz_rects']]
        ox, oy, _oz = dict(ctx['towns'])[name]
        levels = next(((r[7], r[8]) for r in ctx['regions'] if r[1] <= ox <= r[3] and r[2] <= oy <= r[4] and r[5] <= 0 <= r[6]),
                      (40, 90))
        if not palace:
            ctx['towns'][:] = [(n, (*t['temple'], 0)) if n == name else (n, p) for n, p in ctx['towns']]
        ctx['regions'].insert(0, (name, x0, y0, x1, y1, 0, 2, *levels))
        ctx['spawns'][:] = [ln for ln in ctx['spawns'] if ln.startswith('#') or not (
            (int(ln.split(';')[3]) > 0 and in_old(int(ln.split(';')[1]), int(ln.split(';')[2])))
            or (int(ln.split(';')[3]) == 0 and (int(ln.split(';')[1]), int(ln.split(';')[2])) in t['cells']))]
        t['old'] = old
        ctx['rebuilt'][entry] = t
        print('przebudowa', name, 'obrys', t['rect'], 'domy', len(t['houses']), 'zejscia', len(reserved))


# nowe przystanie z duzymi statkami: klucz kapitana, imie, miasto, punkt na ladzie, kierunek do wody
HARBOURS = [
    ('varden_captain', 'Kapitan Edmund', 'Varden', (498, 533), F.S_),
    ('qas_captain', None, 'Qasirah', (70, 470), F.W),
    ('east_captain', None, 'Przystań Wschodnia', (785, 560), F.E),
    ('frost_captain', 'Kapitan Ulf', 'Frostholm', (800, 215), F.E),
]
# rejsy wg tabeli z mapy Darka (Sareth = Qasirah, Thalion = Varden, Arden = Frostholm) i stare trasy wysp
NEW_ROUTES = [('varden_captain', 'selv_captain'), ('varden_captain', 'qas_captain'),
              ('varden_captain', 'pirate_captain'), ('varden_captain', 'coral_captain'),
              ('varden_captain', 'east_captain'), ('varden_captain', 'frost_captain'),
              ('varden_captain', 'valmir_captain'), ('qas_captain', 'valmir_captain'),
              ('frost_captain', 'valmir_captain'), ('frost_captain', 'nerys_captain'),
              ('valmir_captain', 'nerys_captain'), ('east_captain', 'nerys_captain')]
# drogi: (skad, dokad) - pola tuz za bramami miast i przy celach; kolejne drogi korzystaja z poprzednich
ROADS = [
    ((498, 482), ('Frostholm', 'S')),  # Varden (polnoc) -> Frostholm (poludnie)
    ((498, 531), ('Valmir', 'N')),   # Varden (poludnie) -> Valmir (polnoc)
    ((498, 482), ('Qasirah', 'E')),  # Varden (polnoc) -> Qasirah (wschod)
    (('Varden-East', 'N'), (455, 283)),  # Varden (dzielnica krolewska) -> oboz w Szarych Turniach
    ((532, 505), ('Varden-East', 'W')),  # stare miasto Varden -> dzielnica krolewska
    (('Qasirah', 'N'), (250, 332)),  # Qasirah (polnoc) -> Plaskowyz
    (('Qasirah', 'S'), (116, 555)),  # Qasirah (poludnie) -> piramida
    (('Qasirah', 'W'), (70, 470)),   # Qasirah -> przystan Qasirah
    (('Frostholm', 'W'), (520, 243)),  # Frostholm (zachod) -> Szare Turnie
    (('Frostholm', 'N'), (602, 62)),   # Frostholm (polnoc) -> Lodowe Szczyty
    (('Frostholm', 'N'), (760, 97)),   # Frostholm -> Lodowe Jaskinie
    (('Frostholm', 'E'), (800, 215)),  # Frostholm -> przystan Frostholm
    (('Valmir', 'E'), ('Selvaran', 'W')),  # Valmir (wschod) -> Selvaran (zachod)
    ((785, 560), (703, 589)),        # Przystan Wschodnia -> ruiny w dzungli
    ((703, 589), ('Selvaran', 'N')),  # ruiny w dzungli -> Selvaran
    (('Valmir', 'W'), (160, 643)),   # Valmir (zachod) -> Skaliste Ziemie (Otchlan)
]


def varden_core_cells(ctx):
    b = next(bb for bb in ctx['blocks'] if bb.name == 'varden')
    x0, y0, x1, y1 = VARDEN_CORE
    return [(x + b.dx, y + b.dy) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]


def restore_varden_core(ctx):
    """Drogi spod bram i wystroj nie zmieniaja kopii maps/varden (GameWorldTest porownuje ja pole po polu)."""
    world = ctx['world']
    for (x, y), c in ctx['varden_core'].items():
        world.put(x, y, 0, c)


def build(ctx):
    world, T, footprint = ctx['world'], ctx['T'], ctx['footprint']
    ctx['varden_core'] = {p: world.get(*p) for p in varden_core_cells(ctx)}
    ctx['pond_avoid'] = [p for r in ROADS for p in r if not isinstance(p[0], str)] + [h[3] for h in HARBOURS]
    print('stawy i jeziorka:', nature.ponds(ctx))
    cost = F.road_cost(world, T, footprint)
    for b in ctx['blocks']:
        if b.kind == 'entrance':
            for (x, y) in b.surface:
                nx, ny = b.new(x, y)
                c = world.get(nx, ny)
                if F.walk(c) and c[2] not in F.STAIRS:
                    cost[ny, nx] = 1.0
    harbours = {}
    for key, name, town, start, d in HARBOURS:
        captain, rect = F.harbour_with_ship(world, start, d)
        harbours[key] = captain
        ctx['pz'].append((*rect, 0))
        for y in range(rect[1], rect[3] + 1):
            for x in range(rect[0], rect[2] + 1):
                if 0 <= x < S and 0 <= y < S:
                    cost[y, x] = min(cost[y, x], 0.35) if world.floors[0][y][x][0] in ('bridge', 'ship_deck') else cost[y, x]
        if key in ctx['unplaced']:
            n = ctx['unplaced'].pop(key)
            ctx['npcs'].append([n[0], n[1], n[2], n[3], captain[0], captain[1], 0, *n[7:10]])
        else:
            ctx['npcs'].append([key, name, 'CAPTAIN', town, captain[0], captain[1], 0, '-', '#4a6a8a',
                                'Mój statek pływa do portów całego świata. Rejsy tylko dla kont premium.'])
    # Admiral i kwatermistrz zostaja na nabrzezu Przystani Wschodniej
    ex, ey = harbours['east_captain']
    for key in ('isle_guide', 'isle_merchant'):
        n = ctx['unplaced'].pop(key)
        spot = free_spot(world, (ex - 6, ey), ctx['npcs'])
        ctx['npcs'].append([n[0], n[1], n[2], n[3], spot[0], spot[1], 0, *n[7:10]])
    for t in ctx['new_towns']:
        key, cname = newtowns.CAPTAINS[t['name']]
        start = t['gates']['S']
        quay = F.to_water(world, start, F.S_)
        assert quay is not None, f'no sea south of {t["name"]}'
        captain, rect = F.harbour_with_ship(world, quay, F.S_)
        ctx['pz'].append((*rect, 0))
        ctx['npcs'].append([key, cname, 'CAPTAIN', t['name'], captain[0], captain[1], 0, '-', '#4a6a8a',
                            'Mój statek pływa do portów całego świata. Rejsy tylko dla kont premium.'])
        for y in range(rect[1], rect[3] + 1):
            for x in range(rect[0], rect[2] + 1):
                if world.floors[0][y][x][0] in ('bridge', 'ship_deck'):
                    cost[y, x] = min(cost[y, x], 0.35)
        if quay != start:
            F.connect(world, T, cost, start, quay)
    ctx['routes'] += NEW_ROUTES
    ferries(ctx)
    for t in ctx['new_towns'] + list(ctx['rebuilt'].values()):
        for (gx, gy) in t['gates'].values():
            cost[gy, gx] = 1.0
    bridges = []
    gates = {t['name']: t['gates'] for t in ctx['new_towns']}
    gates.update({k: t['gates'] for k, t in ctx['rebuilt'].items()})

    def at(p):
        return gates[p[0]][p[1]] if isinstance(p[0], str) else p
    for a, b in ROADS:
        br, _path = F.connect(world, T, cost, at(a), at(b))
        bridges += br
    ctx['bridges'] = bridges
    ctx['regions_new'] = []
    rng = random.Random(20261035)
    foot = volcano(ctx, cost, rng)
    # droga spod wulkanu do Plaskowyzu (droga z Qasirah) i do gniazda wiwern
    F.connect(world, T, cost, foot, (250, 332))
    F.connect(world, T, cost, foot, (310, 228))
    villages(ctx, cost)
    caves(ctx, rng)
    town_undergrounds(ctx, rng)
    for (yr, xr, kind) in (((230, 280), (470, 560), 'rock'), ((150, 200), (380, 460), 'rock'),
                           ((300, 330), (560, 640), 'river'), ((600, 640), (540, 620), 'river'),
                           ((380, 420), (380, 470), 'river')):
        cr = find_crossing(world, T, yr[0], yr[1], xr[0], xr[1], kind)
        if cr is not None:
            sky_bridge(ctx, cr[1], cr[2], chasm=(kind == 'rock'), deck='rope_bridge' if kind == 'rock' else 'bridge')
            ctx.setdefault('sky_bridges', []).append((cr[1], cr[2], kind))
    small_docks(ctx)
    # kazde wejscie na powierzchni osiagalne pieszo z Varden (inaczej sciezka do najblizszej drogi)
    temple = dict(ctx['towns'])['Varden']
    others = [p[:2] for _n, p in ctx['towns']]
    island_blocks = [b for b in ctx['blocks'] if b.kind == 'island']
    for _ in range(3):
        reach = reach_surface(world, temple[:2], others)
        missing = []
        for (x, y, z, _t, tx, ty, tz) in ctx['transitions']:
            if z == 0 and not reach[y, x] and not any(b.rect[0] + b.dx - 2 <= x <= b.rect[2] + b.dx + 2
                                                       and b.rect[1] + b.dy - 2 <= y <= b.rect[3] + b.dy + 2 for b in island_blocks):
                ax, ay = (tx, ty) if tz == 0 else (x, y + 1)
                missing.append((x, y, ax, ay))
        if not missing:
            break
        done = set()
        for (x, y, ax, ay) in missing:
            if reach[ay, ax] or (x // 8, y // 8) in done:
                continue
            done.add((x // 8, y // 8))
            start = (ax, ay)
            if not F.walk(world.get(*start)):
                for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
                    if F.walk(world.get(x + dx, y + dy)) and world.get(x + dx, y + dy)[2] == NONE:
                        start = (x + dx, y + dy)
                        break
            road = nearest_road(world, cost, start, (start[0], start[1], start[0], start[1]), limit=140)
            if road is not None:
                saved = cost[start[1], start[0]]
                cost[start[1], start[0]] = 1.0
                F.connect(world, T, cost, start, road, width=1)
                cost[start[1], start[0]] = saved
    towns_dressing(ctx)
    core = set(ctx['varden_core'])
    print('ozdoby w miastach:', nature.town_ornaments(ctx, town_rects(ctx), random.Random(20261029),
                                                      protect=lambda x, y: (x, y) in core))
    restore_varden_core(ctx)
    street_sides(ctx)
    atmosphere(ctx)
    soften_edges(ctx)
    print('wyspy:', island_access(ctx, rng))
    zones = zone_grid(T)
    ctx['zones'] = zones
    climate_spawns(ctx, zones, rng)
    # Grobowiec Pieciu Profesji pod pustynia (drzwi poziomu 20, oltarze profesji, skarbiec)
    temple = dict(ctx['towns'])['Varden']
    helpers = dict(standable=standable, in_pz=lambda x, y: in_pz(ctx, x, y), footprint=ctx['footprint'],
                   reach=reach_surface(world, temple[:2], [p[:2] for _n, p in ctx['towns']]),
                   find_free=find_free, mark=mark)
    print('grobowiec pieciu profesji:', quests.tomb_of_five(ctx, random.Random(20261040), helpers))
    # wyprawy na bossa w piec postaci: drzwi poziomu, loch, przedsionek z dzwignia, zamknieta arena
    for info in boss_quests.boss_quests(ctx, random.Random(20261050), helpers):
        print('wyprawa na bossa:', info)


def monster_levels():
    out = {}
    with open(os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources', 'data', 'monsters.csv'), encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.split(';')
            out[p[0]] = int(p[2])
    return out


MONSTER_LEVEL = monster_levels()
# wioski: nazwa, rodzaj, okolica, potwory (klucz, promien, liczba) dla zajetych
VILLAGES = [
    ('Wierzbowo', 'living', (420, 440), []),
    ('Mroźna Osada', 'living', (680, 260), []),
    ('Rybacka Wola', 'living', (600, 815), []),
    ('Oaza Daktylowa', 'living', (140, 360), []),
    ('Popielisko', 'deserted', (300, 300), []),
    ('Suche Studnie', 'deserted', (120, 430), []),
    ('Stary Młyn', 'deserted', (560, 640), []),
    ('Kamienny Bród', 'occupied', (610, 330), [('orc_warrior', 4, 3), ('orc_shaman', 3, 2)]),
    ('Wilczy Jar', 'occupied', (580, 290), [('orc_berserker', 4, 3), ('orc_warrior', 4, 2)]),
    ('Czerwony Kurhan', 'occupied', (330, 640), [('orc_warrior', 4, 3), ('orc_warlord', 2, 1)]),
    ('Zielony Gaj', 'occupied', (410, 610), [('goblin', 4, 4), ('goblin_shaman', 3, 2)]),
    ('Rozstaje', 'occupied', (360, 470), [('bandit', 4, 4)]),
    ('Martwa Wieś', 'occupied', (250, 610), [('skeleton', 4, 4), ('wraith_knight', 3, 1)]),
    ('Trollowe Leże', 'occupied', (440, 340), [('cave_troll', 4, 3)]),
    ('Bagienna Osada', 'occupied', (630, 560), [('jungle_hunter', 4, 3), ('crocodile', 4, 2)]),
    ('Kozi Przysiółek', 'occupied', (380, 700), [('goblin', 4, 3), ('wolf', 4, 2)]),
    ('Lodowy Kurhan', 'occupied', (650, 150), [('yeti', 4, 3), ('frost_witch', 3, 1)]),
    ('Gniazdo Rabusiów', 'occupied', (740, 420), [('bandit', 4, 4), ('skeleton', 3, 2)]),
    ('Piaskowy Obóz', 'occupied', (90, 520), [('hyena', 4, 3), ('scorpion', 3, 2)]),
]
VILLAGE_NPCS = {
    'Wierzbowo': ('wierzbowo_merchant', 'Sołtys Bogumił', 'Witaj w Wierzbowie. Chleb, mięso i pochodnie dla wędrowców.'),
    'Mroźna Osada': ('osada_merchant', 'Handlarka Jagna', 'Ciepła strawa i liny na drogę w góry.'),
    'Rybacka Wola': ('wola_merchant', 'Rybaczka Halina', 'Świeże ryby, chleb i pochodnie dla wędrowców.'),
    'Oaza Daktylowa': ('oaza_merchant', 'Handlarz Karim', 'Woda, daktyle i liny na drogę przez pustynię.'),
}


def villages(ctx, cost):
    world, T = ctx['world'], ctx['T']
    taken = ctx['footprint'].copy()
    rng = random.Random(20261034)
    out = []
    for name, kind, near, monsters in VILLAGES:
        v = F.village(world, T, taken, near, kind, rng)
        x0, y0, x1, y1 = v['rect']
        taken[y0 - 6:y1 + 7, x0 - 6:x1 + 7] = True
        v['name'] = name
        out.append(v)
        for y in range(y0 - 2, y1 + 3):
            for x in range(x0 - 2, x1 + 3):
                c = world.get(x, y)
                cost[y, x] = 0.35 if c[0] in F.ROAD_GROUND else (1.0 if F.walk(c) else (np.inf if c[0] == 'water' else 30.0))
        free = list(v['free'])
        if kind == 'living':
            key, npc_name, greet = VILLAGE_NPCS[name]
            spot = min(free, key=lambda p: abs(p[0] - v['centre'][0]) + abs(p[1] - v['centre'][1]))
            ctx['npcs'].append([key, npc_name, 'MERCHANT', name, spot[0], spot[1], 0, 'village_shop', '#7a6a3a', greet])
            ctx['pz'].append((x0, y0, x1, y1, 0))
        for i, (monster, radius, count) in enumerate(monsters):
            sx, sy = free[(i * 7 + 3) % len(free)]
            ctx['spawns'].append(f'{monster};{sx};{sy};0;{radius};{count};150')
        if monsters:
            levels = [MONSTER_LEVEL[m] for m, _r, _c in monsters]
            ctx['regions_new'].append((name, x0 - 3, y0 - 3, x1 + 3, y1 + 3, 0, 0, min(levels), max(levels) + 10))
        # sciezka do najblizszej drogi
        cx, cy = v['centre']
        road = nearest_road(world, cost, (cx, cy), exclude=(x0, y0, x1, y1))
        if road is not None:
            F.connect(world, T, cost, (cx, y1 + 1), road, width=1)
        if kind != 'living':
            v['keep'] = [tuple(int(t) for t in sp.split(';')[1:3]) for sp in ctx['spawns'][-len(monsters):]] if monsters else []
            nature.ravage(world, v, rng, burnt=True)
    ctx['villages'] = out
    return out


def dungeon(ctx, name, entrance, levels, rng, surface_ground=None):
    """Loch pod wejsciem na powierzchni. levels: [(szer, wys, punkty, styl, lawa, spawny)], punkty w ukladzie
    lokalnym wspolnym dla wszystkich pieter; pierwszy punkt pietra -1 lezy pod wejsciem, ostatni punkt pietra
    to schody w dol, ktore sa pierwszym punktem nastepnego pietra. spawny: [(potwor, promien, liczba)]."""
    world = ctx['world']
    grids = {}
    W = max(lv[0] for lv in levels)
    H = max(lv[1] for lv in levels)
    for i, (w, h, pts, style, lava, _sp) in enumerate(levels):
        z = -1 - i
        carve = F.carve_crypt if style == 'crypt' else F.carve_cave
        g = carve(W, H, pts, style, rng, lava=lava)
        F.fix_lava(g, pts)
        grids[z] = (g, pts)
    masks = {}
    for z, (g, _p) in grids.items():
        m = np.zeros((H, W), bool)
        for y in range(H):
            for x in range(W):
                if g[y][x] is not None:
                    m[y, x] = True
        masks[z] = m
    ex, ey = entrance
    p0 = levels[0][2][0]
    tx, ty = find_free(world, masks, ex - p0[0], ey - p0[1])
    mark(world, masks, tx, ty)
    for z, (g, pts) in grids.items():
        for y in range(H):
            for x in range(W):
                if g[y][x] is not None:
                    world.put(tx + x, ty + y, z, g[y][x])
    # schody
    gc = world.get(ex, ey)
    ground = surface_ground or gc[0]
    if ground in ('water', 'lava', 'swamp', 'bridge', 'ice'):
        ground = 'dirt'
    world.put(ex, ey, 0, (ground, NONE, 'stairs_down', NONE))
    a = world.get(ex, ey + 1)
    if not F.walk(a) or a[2] != NONE:
        world.put(ex, ey + 1, 0, (ground, NONE, NONE, NONE))
    for dx in (-1, 1):
        c = world.get(ex + dx, ey)
        if c is not None and c[3] != NONE:
            world.put(ex + dx, ey, 0, (ground, NONE, NONE, NONE))
    t = ctx['transitions']
    for i, (w, h, pts, style, lava, spawns) in enumerate(levels):
        z = -1 - i
        g, _ = grids[z]
        floor = F.CAVE_STYLES[style][0]
        up = (tx + pts[0][0], ty + pts[0][1])
        world.put(up[0], up[1], z, (floor, NONE, 'stairs_up', NONE))
        if i == 0:
            t.append((ex, ey, 0, 'STAIRS', up[0], up[1] + 1, -1))
            t.append((up[0], up[1], -1, 'STAIRS', ex, ey + 1, 0))
        if i + 1 < len(levels):
            down = (tx + pts[-1][0], ty + pts[-1][1])
            world.put(down[0], down[1], z, (floor, NONE, 'stairs_down', NONE))
            nfloor = F.CAVE_STYLES[levels[i + 1][3]][0]
            t.append((down[0], down[1], z, 'STAIRS', down[0], down[1] + 1, z - 1))
            t.append((down[0], down[1], z - 1, 'STAIRS', down[0] + 1, down[1], z))
            _ = nfloor
        # spawny w komorach z dala od schodow
        cells = [(tx + x, ty + y) for y in range(H) for x in range(W)
                 if g[y][x] is not None and F.walk(g[y][x]) and g[y][x][2] == NONE
                 and all(abs(x - px) + abs(y - py) > 5 for (px, py) in (pts[0], pts[-1]))]
        cells.sort()
        for j, spawn in enumerate(spawns):
            monster, radius, count = spawn[:3]
            respawn = spawn[3] if len(spawn) > 3 else 180   # bossowie jaskin: 3600 s
            pts_world = [(tx + px, ty + py) for (px, py) in pts[1:-1]] or [(tx + pts[-1][0], ty + pts[-1][1])]
            want = pts_world[j % len(pts_world)]
            sx, sy = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]) + (j * 13 % 5))
            ctx['spawns'].append(f'{monster};{sx};{sy};{z};{radius};{count};{respawn}')
    ctx.setdefault('dungeons', []).append((name, tx, ty, tx + W - 1, ty + H - 1, -len(levels), -1))
    return tx, ty


def volcano(ctx, cost, rng):
    """Wulkan (Czarne Szczyty): szeroki stozek z lawowymi strugami, polka (+1) na zboczu i krater z jeziorem lawy
    na szczycie (+2). Pod nim Otchlan Wulkanu (-1..-5): od zywiolakow ognia przez smoki magmy do demonow i wladcow
    otchlani na samym dnie (2026-10-27, Darek: "pod wulkanem jaskinia z demonami i silnymi potworami").
    Zwraca punkt u stop (do drogi)."""
    world = ctx['world']
    vx, vy = C.VOLCANO
    R = 36                                   # promien stozka
    n = C.fbm(401, 2, 5)
    streams = [math.radians(a) for a in (35, 100, 150, 215, 260, 320)]
    for y in range(vy - R - 1, vy + R + 2):
        for x in range(vx - R - 1, vx + R + 2):
            if not (0 <= x < S and 0 <= y < S):
                continue
            d = math.hypot(x - vx, y - vy) + 1.6 * n[y, x]
            if d > R:
                continue
            ang = math.atan2(y - vy, x - vx)
            if d < 11:
                cell = ('lava', NONE, NONE, NONE)
            elif d <= 18.5:
                cell = ('ash', NONE, NONE, 'mountain')
            else:
                in_stream = any(abs((ang - a + math.pi) % (2 * math.pi) - math.pi) * d < 1.1 for a in streams) and d < 31
                if in_stream:
                    cell = ('lava', NONE, NONE, NONE)
                else:
                    v = rng.random()
                    cell = ('ash', NONE, 'obsidian_rock' if v < 0.04 else ('dead_tree' if v < 0.055 else NONE), NONE)
            world.put(x, y, 0, cell)
            cost[y, x] = 30.0 if cell[3] != NONE else 1.0
            if cell[0] == 'lava':
                cost[y, x] = np.inf
    # polka (+1) i krawedz krateru (+2)
    for y in range(vy - 22, vy + 23):
        for x in range(vx - 22, vx + 23):
            d = math.hypot(x - vx, y - vy)
            if 15.3 <= d <= 21:
                world.put(x, y, 1, ('ash', NONE, 'obsidian_rock' if rng.random() < 0.04 and d > 17 else NONE, NONE))
            if 10.2 <= d <= 16:
                world.put(x, y, 2, ('ash', NONE, 'magma' if rng.random() < 0.05 and d > 15 else NONE, NONE))
            elif d < 10.2:
                world.put(x, y, 2, ('lava', NONE, NONE, NONE))
    # schody: zbocze (poludnie) -> polka -> krawedz krateru (polnoc)
    t = ctx['transitions']
    world.put(vx, vy + 20, 0, ('ash', NONE, 'stairs_up', NONE))
    world.put(vx, vy + 21, 0, ('ash', NONE, NONE, NONE))
    world.put(vx, vy + 20, 1, ('ash', NONE, 'stairs_down', NONE))
    world.put(vx, vy + 19, 1, ('ash', NONE, NONE, NONE))
    t += [(vx, vy + 20, 0, 'STAIRS', vx, vy + 19, 1), (vx, vy + 20, 1, 'STAIRS', vx, vy + 21, 0)]
    world.put(vx, vy - 17, 1, ('ash', NONE, 'stairs_up', NONE))
    world.put(vx, vy - 18, 1, ('ash', NONE, NONE, NONE))
    world.put(vx, vy - 15, 2, ('ash', NONE, 'stairs_down', NONE))
    world.put(vx, vy - 14, 2, ('ash', NONE, NONE, NONE))
    t += [(vx, vy - 17, 1, 'STAIRS', vx, vy - 14, 2), (vx, vy - 15, 2, 'STAIRS', vx, vy - 18, 1)]
    for yy in range(vy + 21, vy + R + 2):
        for xx in (vx - 1, vx, vx + 1):
            c = world.get(xx, yy)
            if c[0] in ('lava', 'water') or c[3] != NONE or c[2] != NONE:
                world.put(xx, yy, 0, ('ash', NONE, NONE, NONE))
            cost[yy, xx] = 1.0
    sp = ctx['spawns']
    sp += ['# Wulkan: zbocza, polka i krater (Ignaroth na szczycie)',
           f'fire_elemental;{vx - 26};{vy + 6};0;4;3;240', f'lava_golem;{vx + 26};{vy - 5};0;4;2;300',
           f'fire_elemental;{vx - 8};{vy + 28};0;4;3;240', f'lava_golem;{vx + 22};{vy + 20};0;4;2;300',
           f'fire_elemental;{vx + 18};{vy + 5};1;2;2;240', f'fire_elemental;{vx - 18};{vy - 5};1;2;2;240',
           f'magma_drake;{vx};{vy + 18};1;2;1;600', f'ignaroth;{vx + 12};{vy};2;1;1;3600']
    # Otchlan Wulkanu: wejscie u wschodniej stopy, piec pieter w dol
    entry = (vx + 27, vy + 14)
    world.put(entry[0], entry[1], 0, ('ash', NONE, NONE, NONE))
    for yy in range(entry[1] + 1, entry[1] + 3):
        world.put(entry[0], yy, 0, ('ash', NONE, NONE, NONE))
    dungeon(ctx, 'Otchłań Wulkanu', entry, [
        (72, 56, [(6, 6), (24, 16), (50, 8), (64, 30), (40, 44), (18, 40)], 'fire', 0.06,
         [('fire_elemental', 4, 3), ('lava_golem', 4, 2), ('fire_elemental', 4, 3), ('lava_golem', 3, 2),
          ('ember_beetle', 3, 2)]),
        (72, 56, [(18, 40), (8, 24), (26, 8), (48, 20), (64, 44), (40, 50)], 'fire', 0.08,
         [('lava_golem', 4, 3), ('magma_drake', 3, 2), ('fire_elemental', 4, 3), ('magma_drake', 3, 1),
          ('ash_magus', 3, 2)]),
        (72, 56, [(40, 50), (60, 36), (64, 10), (36, 14), (12, 22), (10, 46)], 'core', 0.1,
         [('magma_drake', 4, 3), ('abyssal_demon', 3, 2), ('magma_drake', 4, 2), ('abyssal_demon', 3, 1),
          ('abyss_crawler', 3, 2)]),
        (72, 56, [(10, 46), (28, 34), (22, 10), (50, 6), (66, 26), (52, 48)], 'core', 0.1,
         [('abyssal_demon', 4, 3), ('bone_dragon', 3, 2), ('abyssal_demon', 4, 2), ('bone_dragon', 3, 1),
          ('winged_spawn', 3, 2)]),
        (72, 56, [(52, 48), (30, 46), (10, 30), (30, 14), (56, 18)], 'core', 0.12,
         [('abyss_lord', 4, 2), ('abyssal_demon', 4, 3), ('bone_dragon', 3, 2), ('abyss_lord', 3, 1),
          ('obsidian_sentinel', 3, 1)]),
    ], rng, surface_ground='ash')
    ctx['regions_new'] = [('Szczyt Wulkanu', vx - 22, vy - 22, vx + 22, vy + 22, 1, 2, 340, 700)] + ctx['regions_new']
    _n, x0, y0, x1, y1, zl, zh = ctx['dungeons'][-1]
    ctx['regions_new'].insert(0, ('Otchłań Wulkanu', x0, y0, x1, y1, -2, -1, 320, 700))
    ctx['regions_new'].insert(0, ('Dno Otchłani', x0, y0, x1, y1, -5, -3, 690, 960))
    return (vx, vy + R + 1)


# ------------------------------------------------------------------ krainy (regiony) i spawny wg klimatu
# kraina: (poziom od, do, potwory dzienne [(klucz, waga)], potwory nocne)
ZONES = {
    # 2026-10-28: nowe potwory (wrona, klusownik, lamacz kamieni, iskrzyk, latarnik, odyniec, czerwie, szerszen,
    # skradacz, zjawy, chrzaszcze, mag popiolu, ognik, gargulce, wije) w kazdej krainie
    'Krainy Varden': (8, 60, [('wolf', 3), ('boar', 3), ('forest_spider', 2), ('bandit', 2), ('goblin', 2),
                              ('goblin_shaman', 1), ('skeleton', 1), ('carrion_crow', 2), ('snare_setter', 2),
                              ('quarry_brute', 1)], ['wolf']),
    'Wzgorza Qasirah': (20, 70, [('crystal_golem', 2), ('goblin_shaman', 2), ('skeleton', 2), ('cave_troll', 2),
                                 ('spark_wisp', 2), ('quarry_brute', 1), ('barrow_lantern', 1)], ['hyena', 'barrow_lantern']),
    'Mrozne Pustkowia': (40, 100, [('snow_wolf', 3), ('yeti', 2), ('frost_witch', 1), ('frost_boar', 2), ('rime_crawler', 1)],
                         ['snow_wolf']),
    'Lodowe Szczyty': (400, 560, [('frost_giant', 3), ('storm_harpy', 2), ('ice_dragon', 1), ('storm_wisp', 2),
                                  ('rime_gargoyle', 2)], []),
    'Spalone Wyzyny': (300, 400, [('fire_elemental', 3), ('lava_golem', 2), ('ember_beetle', 2), ('ash_magus', 2)], []),
    'Dzungla Selvaran': (55, 80, [('crocodile', 2), ('jungle_hunter', 3), ('jungle_serpent', 2), ('swamp_hornet', 2),
                                  ('striped_prowler', 2), ('bog_wraith', 1)], ['bog_wraith']),
    'Pustynia Qasirah': (45, 130, [('hyena', 3), ('scorpion', 3), ('sand_wraith', 1), ('sand_devourer', 2),
                                   ('gilded_beetle', 1), ('dune_zealot', 1)], ['hyena', 'sand_wraith']),
    'Pustkowia Cienia': (550, 750, [('shadow_stalker', 3), ('sand_titan', 2), ('void_priest', 2), ('dusk_gargoyle', 2),
                                    ('shadow_centipede', 2)], ['shadow_stalker', 'dusk_gargoyle']),
    'Przeklete Knieje': (230, 300, [('cursed_treant', 3), ('wraith_knight', 2), ('crimson_shade', 2)],
                         ['wraith_knight', 'crimson_shade']),
}
BIOME_ZONE = {'snow': 'Mrozne Pustkowia', 'taiga': 'Mrozne Pustkowia', 'temperate': 'Krainy Varden',
              'forest': 'Krainy Varden', 'hills': 'Wzgorza Qasirah', 'cursed': 'Przeklete Knieje',
              'volcanic': 'Spalone Wyzyny', 'jungle': 'Dzungla Selvaran', 'swamp': 'Dzungla Selvaran',
              'desert': 'Pustynia Qasirah', 'savanna': 'Pustynia Qasirah', 'shadow': 'Pustkowia Cienia'}
ZONE_CELL = 16


def zone_grid(T):
    """Kraina kazdego pola 16 x 16 wg przewazajacego biomu ladu (polnocny snieg to Lodowe Szczyty)."""
    n = S // ZONE_CELL
    out = {}
    for cy in range(n):
        for cx in range(n):
            votes = {}
            for y in range(cy * ZONE_CELL, (cy + 1) * ZONE_CELL, 2):
                for x in range(cx * ZONE_CELL, (cx + 1) * ZONE_CELL, 2):
                    if T.land[y, x]:
                        z = BIOME_ZONE.get(T.biome[y, x], 'Krainy Varden')
                        if z == 'Mrozne Pustkowia' and y < 120:
                            z = 'Lodowe Szczyty'
                        votes[z] = votes.get(z, 0) + 1
            if votes:
                out[(cx, cy)] = max(votes, key=votes.get)
    return out


def zone_regions(zones):
    """Prostokaty krain z pol 16 x 16 (wiersze laczone w pionie, jak town.rects_of)."""
    out = []
    for name in ZONES:
        cells = {(cx, cy) for (cx, cy), z in zones.items() if z == name}
        for (x0, y0, x1, y1) in tk.rects_of(cells):
            lo, hi = ZONES[name][:2]
            out.append((name, x0 * ZONE_CELL, y0 * ZONE_CELL, (x1 + 1) * ZONE_CELL - 1, (y1 + 1) * ZONE_CELL - 1, 0, 5, lo, hi))
    return out


def reach_surface(world, start, extra_starts=()):
    g = world.floors[0]
    seen = np.zeros((S, S), bool)
    q = deque()
    for p in (start, *extra_starts):
        seen[p[1], p[0]] = True
        q.append(p)
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            nx, ny = x + dx, y + dy
            if 0 <= nx < S and 0 <= ny < S and not seen[ny, nx] and F.walk(g[ny][nx]):
                seen[ny, nx] = True
                q.append((nx, ny))
    return seen


def climate_spawns(ctx, zones, rng):
    world = ctx['world']
    temples = [p for _n, p in ctx['towns']]
    reach = reach_surface(world, temples[0][:2], [t[:2] for t in temples] + ctx.get('island_starts', []))
    island_cells = ctx.get('island_cells', set())
    pz = ctx['pz']
    avoid = [v['rect'] for v in ctx.get('villages', [])]
    taken = []
    for line in ctx['spawns']:
        if not line.startswith('#'):
            f = line.split(';')
            if f[3] == '0':
                taken.append((int(f[1]), int(f[2])))
    out = ['# --- spawny wg klimatu (generate_continent.py) ---']
    step = 21
    night_left = {}
    for gy in range(8, S - 8, step):
        for gx in range(8, S - 8, step):
            x, y = gx + rng.randint(-6, 6), gy + rng.randint(-6, 6)
            if not (0 <= x < S and 0 <= y < S) or not reach[y, x] or ctx['footprint'][y, x] or (x, y) in island_cells:
                continue
            c = world.get(x, y)
            if not F.walk(c) or c[0] in F.ROAD_GROUND or c[2] != NONE:
                continue
            zone = zones.get((x // ZONE_CELL, y // ZONE_CELL))
            if zone is None:
                continue
            lo, hi, pool, night = ZONES[zone]
            if any(abs(x - t[0]) + abs(y - t[1]) < 26 for t in temples):
                continue
            # okolica Varden (miasto startowe) spokojna: silne potwory dopiero za 40 pol od swiatyni
            if lo > 15 and max(abs(x - temples[0][0]), abs(y - temples[0][1])) < 40:
                continue
            if any(a - 2 <= x <= c_ + 2 and b - 2 <= y <= d + 2 for (a, b, c_, d, z) in pz if z == 0):
                continue
            if any(a - 6 <= x <= c_ + 6 and b - 6 <= y <= d + 6 for (a, b, c_, d) in avoid):
                continue
            if any(abs(x - tx) + abs(y - ty) < 12 for (tx, ty) in taken):
                continue
            total = sum(w for _k, w in pool)
            r = rng.random() * total
            for key, w in pool:
                r -= w
                if r <= 0:
                    break
            if MONSTER_LEVEL[key] > 15 and max(abs(x - temples[0][0]), abs(y - temples[0][1])) < 40:
                key = min(pool, key=lambda kw: MONSTER_LEVEL[kw[0]])[0]   # pod Varden tylko slabe potwory
            count = 3 if rng.random() < 0.5 else 2
            out.append(f'{key};{x};{y};0;{rng.choice((4, 5))};{count};{rng.choice((90, 120, 150))}')
            taken.append((x, y))
            if night and night_left.get(zone, 0) < 3 and rng.random() < 0.35:
                night_left[zone] = night_left.get(zone, 0) + 1
                nk = rng.choice(night)
                if MONSTER_LEVEL[nk] > 15 and max(abs(x - temples[0][0]), abs(y - temples[0][1])) < 40:
                    nk = min(night, key=lambda k: MONSTER_LEVEL[k])   # noca pod Varden tez tylko slabe
                out.append(f'{nk};{x};{y};0;5;2;120;noc')
    ctx['spawns'] += out


# ------------------------------------------------------------------ jaskinie w gorach, mosty wiszace, przystanie

# uklady pieter jaskin (szer, wys, punkty komor); ostatni punkt pietra = pierwszy punkt nastepnego
CAVE_LAYOUTS = [(36, 28, [(4, 4), (16, 14), (30, 6), (30, 22)]),
                (36, 28, [(30, 22), (12, 22), (6, 8), (22, 10)]),
                (36, 28, [(22, 10), (8, 18), (20, 24), (32, 14)])]


def cave_levels(style, *spawns, lava=0):
    """Pietra jaskini w stylu style; spawns: lista spawnow [(potwor, promien, liczba)] na kazde pietro."""
    return [(w, h, pts, style, lava, sp) for (w, h, pts), sp in zip(CAVE_LAYOUTS, spawns)]


CAVES = [
    # nazwa, okolica wejscia, pietra, poziomy regionu; spawn (potwor, promien, liczba[, odrodzenie s])
    # 2026-10-28: nowe potwory i trzej bossowie jaskin (Wroniec, Szlamiara, Velmira, Suweren Ostrzy)
    ('Grota Pająków', (430, 560), cave_levels('moss', [('forest_spider', 4, 3), ('forest_spider', 3, 2)],
                                             [('forest_spider', 4, 3), ('skeleton', 3, 2), ('cave_centipede', 3, 2)]), (10, 40)),
    ('Wilcza Nora', (330, 430), cave_levels('moss', [('wolf', 4, 3), ('boar', 3, 2)], [('forest_spider', 4, 3)]), (8, 25)),
    ('Grota Rozbójników', (420, 720), cave_levels('rock', [('bandit', 4, 3), ('skeleton', 3, 2), ('tunnel_fence', 3, 2)],
                                                 [('bandit', 4, 3), ('goblin_shaman', 3, 2), ('candle_acolyte', 3, 2),
                                                  ('wroniec', 2, 1, 3600)]), (12, 40)),
    ('Jaskinia Trolli', (470, 330), cave_levels('rock', [('cave_troll', 4, 3), ('cave_troll', 4, 2), ('spark_wisp', 3, 2)],
                                               [('cave_troll', 4, 3), ('crystal_golem', 3, 2), ('candle_acolyte', 3, 2)]), (28, 60)),
    ('Kryształowa Grota', (560, 240), cave_levels('moss', [('crystal_golem', 4, 3), ('cave_troll', 3, 2), ('barrow_lantern', 3, 2)],
                                                 [('crystal_golem', 4, 3), ('barrow_lantern', 3, 2)],
                                                 [('cave_troll', 4, 3), ('crystal_golem', 4, 2)]), (40, 80)),
    ('Orcza Kopalnia', (590, 280), cave_levels('orc', [('orc_warrior', 4, 3), ('orc_shaman', 4, 2)],
                                              [('orc_berserker', 4, 3), ('orc_shaman', 4, 2), ('orc_warlord', 2, 1)]), (100, 230)),
    ('Lodowa Grota', (680, 180), cave_levels('ice', [('yeti', 4, 3), ('frost_witch', 3, 2), ('rime_crawler', 3, 2)],
                                            [('ice_golem', 4, 2), ('frost_witch', 3, 2), ('frost_centipede', 3, 2)]), (60, 130)),
    ('Mroźna Otchłań', (560, 120), cave_levels('ice', [('frost_giant', 4, 3), ('storm_harpy', 3, 2)],
                                              [('frost_giant', 4, 3), ('ice_dragon', 3, 1), ('storm_wisp', 3, 2)],
                                              [('ice_dragon', 4, 2), ('storm_harpy', 3, 2), ('rime_gargoyle', 3, 2)]), (430, 560)),
    ('Leże Krokodyli', (720, 500), cave_levels('moss', [('crocodile', 4, 3), ('jungle_serpent', 3, 2), ('swamp_hornet', 3, 2)],
                                              [('jungle_serpent', 4, 3), ('crocodile', 3, 2), ('bog_wraith', 3, 2),
                                               ('szlamiara', 2, 1, 3600)]), (55, 100)),
    ('Zatopiona Świątynia', (640, 620), cave_levels('moss', [('jungle_hunter', 4, 3), ('temple_guardian', 3, 1), ('stone_sentinel', 3, 2)],
                                                   [('temple_guardian', 4, 2), ('jungle_serpent', 3, 2), ('blade_warden', 3, 2)]), (60, 220)),
    ('Grobowiec Wydm', (170, 400), cave_levels('rock', [('scorpion', 4, 3), ('sand_wraith', 3, 2), ('dune_zealot', 3, 2)],
                                              [('mummy', 4, 3), ('sand_wraith', 3, 2), ('sand_gargoyle', 3, 2)]), (60, 220)),
    ('Grota Upiorów', (300, 560), cave_levels('rock', [('wraith_knight', 4, 2), ('cursed_treant', 4, 2), ('crimson_shade', 3, 2)],
                                             [('wraith_knight', 4, 3), ('cursed_treant', 3, 1), ('crimson_shade', 3, 2),
                                              ('velmira', 2, 1, 3600)]), (240, 300)),
    ('Jaskinia Cienia', (200, 620), cave_levels('rock', [('shadow_stalker', 4, 3), ('void_priest', 3, 1), ('dusk_gargoyle', 3, 2)],
                                               [('sand_titan', 4, 2), ('void_priest', 3, 2), ('shadow_centipede', 3, 2)],
                                               [('shadow_stalker', 4, 3), ('sand_titan', 3, 2), ('suweren', 2, 1, 3600)]), (550, 750)),
    ('Grota Przemytników', (330, 700), cave_levels('rock', [('bandit', 4, 3), ('skeleton', 3, 2), ('tunnel_fence', 3, 2)]), (12, 40)),
    ('Wyspiarska Grota', (900, 690), cave_levels('moss', [('drowned_sailor', 4, 3), ('pirate_cutthroat', 3, 2)],
                                                [('pirate_gunner', 4, 2), ('drowned_sailor', 3, 2), ('dune_drake', 3, 1)]), (330, 420)),
]
MOUNTAIN_WALLS = {'mountain', 'snow_mountain', 'sand_cliff'}


def mountain_entrance(world, footprint, near, radius=30):
    """Pole gory z wolnym ladem od poludnia (wejscie do jaskini w zboczu), najblizej near."""
    best = None
    x0, y0 = near
    for y in range(y0 - radius, y0 + radius):
        for x in range(x0 - radius, x0 + radius):
            c = world.get(x, y)
            b = world.get(x, y + 1)
            if c is None or b is None or c[3] not in MOUNTAIN_WALLS or footprint[y, x] or footprint[y + 1, x]:
                continue
            if not F.walk(b) or b[2] != NONE or b[0] in ('bridge',):
                continue
            # zbocze: gora takze z bokow
            if world.get(x - 1, y)[3] not in MOUNTAIN_WALLS or world.get(x + 1, y)[3] not in MOUNTAIN_WALLS:
                continue
            d = abs(x - x0) + abs(y - y0)
            if best is None or d < best[0]:
                best = (d, (x, y))
    assert best is not None, f'no mountain near {near}'
    return best[1]


def caves(ctx, rng):
    world = ctx['world']
    for name, near, levels, (lo, hi) in CAVES:
        ex, ey = mountain_entrance(world, ctx['footprint'], near, radius=60)
        ground = world.get(ex, ey + 1)[0]
        tx, ty = dungeon(ctx, name, (ex, ey), levels, rng, surface_ground=ground)
        nm, x0, y0, x1, y1, zl, zh = ctx['dungeons'][-1]
        ctx['regions_new'].insert(0, (name, x0, y0, x1, y1, zl, zh, lo, hi))
        ctx.setdefault('entrances', []).append((name, (ex, ey + 1)))


# ------------------------------------------------------------------ kanaly i kopalnie pod miastami (2026-10-27)
SEWER_STREETS = {'cobblestone', 'paving', 'brick_paving', 'dirt', 'sandstone_floor'}
SEWER_MONSTERS = [('sewer_rat', 4, 3), ('giant_rat', 4, 2), ('burrow_larva', 3, 2), ('sewer_rat', 3, 3),
                  ('forest_spider', 3, 2), ('giant_rat', 4, 3), ('goblin', 3, 2)]
MINES = [
    # miasto, nazwa, okolica wejscia, pietra (spawny), poziomy regionu
    ('Varden', 'Stara Kopalnia', (445, 505), [[('goblin', 4, 3), ('goblin_shaman', 3, 2), ('cave_centipede', 3, 2)],
                                             [('skeleton', 4, 3), ('cave_troll', 3, 1), ('candle_acolyte', 3, 2)]], (12, 40)),
    ('Qasirah', 'Kopalnia Soli', (268, 470), [[('scorpion', 4, 3), ('hyena', 3, 2)],
                                             [('scorpion', 4, 3), ('sand_wraith', 3, 1), ('sand_devourer', 3, 2)]], (45, 140)),
    ('Frostholm', 'Kopalnia Lodu', (698, 222), [[('snow_wolf', 4, 3), ('yeti', 3, 1), ('frost_boar', 3, 2)],
                                               [('yeti', 4, 3), ('ice_golem', 3, 1)]], (40, 130)),
    ('Selvaran', 'Kopalnia Szmaragdów', (636, 692), [[('jungle_serpent', 4, 2), ('crocodile', 3, 2)],
                                                    [('jungle_hunter', 4, 3), ('jungle_serpent', 3, 2), ('swamp_hornet', 3, 2)]], (55, 90)),
    ('Valmir', 'Kopalnia Miedzi', (436, 772), [[('goblin', 4, 3), ('bandit', 3, 2), ('cave_centipede', 3, 2)],
                                              [('cave_troll', 4, 2), ('skeleton', 3, 2)]], (12, 45)),
    ('Nerys', 'Kopalnia Pereł', (815, 748), [[('cave_troll', 4, 3), ('crystal_golem', 3, 1)],
                                            [('crystal_golem', 4, 3), ('cave_troll', 3, 2)]], (32, 60)),
]


def town_rects(ctx):
    out = {}
    for town, bname in TOWN_BLOCKS.items():
        if town in ctx.get('rebuilt', {}):
            out[town] = ctx['rebuilt'][town]['rect']
            continue
        b = next(bb for bb in ctx['blocks'] if bb.name == bname)
        out[town] = (b.rect[0] + b.dx, b.rect[1] + b.dy, b.rect[2] + b.dx, b.rect[3] + b.dy)
    for t in ctx['new_towns']:
        out[t['name']] = t['rect']
    return out


def clearing(world, ctx, near, radius=30):
    """Polana 5x5 na naturalnym ladzie najblizej near (drzewa i krzaki znikaja) - gdy w gestym lesie nie ma
    wolnego miejsca. Zwraca srodek."""
    x0, y0 = near
    best = None
    for y in range(y0 - radius, y0 + radius + 1):
        for x in range(x0 - radius, x0 + radius + 1):
            area = [world.get(x + a, y + b) for a in range(-2, 3) for b in range(-2, 3)]
            if any(c is None or c[0] not in NATURAL_GROUND or c[3] != NONE or c[2] in F.STAIRS for c in area):
                continue
            if in_pz(ctx, x, y) or ctx['footprint'][y, x]:
                continue
            d = abs(x - x0) + abs(y - y0)
            if best is None or d < best[0]:
                best = (d, x, y)
    assert best is not None, f'no clearing near {near}'
    _d, x, y = best
    for a in range(-2, 3):
        for b in range(-2, 3):
            c = world.get(x + a, y + b)
            world.put(x + a, y + b, 0, (c[0], NONE, NONE, NONE))
    return (x, y)


def cell_parts(cells):
    """Spojne (4 kierunki) kawalki zbioru pol, od najwiekszego."""
    left, out = set(cells), []
    while left:
        start = left.pop()
        comp, q = {start}, [start]
        while q:
            x, y = q.pop()
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + a, y + b)
                if n in left:
                    left.discard(n)
                    comp.add(n)
                    q.append(n)
        out.append(comp)
    return sorted(out, key=len, reverse=True)


def town_undergrounds(ctx, rng):
    """Kanaly z kratkami pod ulicami kazdego miasta i kopalnia u bram (dwa pietra w dol)."""
    world = ctx['world']
    core = set(varden_core_cells(ctx))
    # kratka nie moze stanac tuz przy NPC (zejscie zabraloby miejsce do rozmowy)
    taken = {(int(n[4]) + a, int(n[5]) + b) for n in ctx['npcs'] if int(n[6]) == 0
             for a in range(-2, 3) for b in range(-2, 3)}
    ctx['sewers'] = {}
    for town, rect in town_rects(ctx).items():
        # pietro -1, a gdy pod miastem sa juz stare lochy - nizej (kratka prowadzi wtedy glebiej)
        # najpierw szukamy pietra, pod ktorym miesci sie wiekszosc ulic; male kanaly tylko w ostatecznosci
        cells = None
        for min_mask in (200, 40):
            for z in (-1, -2, -3):
                cells = U.sewers(world, rect, SEWER_STREETS, lambda x, y: (x, y) in core, rng, F.walk, z=z,
                                 min_mask=min_mask)
                if cells:
                    break
            if cells:
                break
        if not cells:
            print('kanaly: brak miejsca pod', town)
            continue
        # kratki w roznych czesciach miasta: kazdy odcinek kanalu dostaje swoje, mniej wiecej co 10 pol ulicy
        gr = []
        parts = cell_parts(cells)
        for part in parts:
            if len(gr) >= 9 and len(part) < 60:
                break
            count = max(1, min(4, len(part) // 70))
            gr += U.grates(world, part, SEWER_STREETS, taken | core | {g[0] for g in gr}, rng, count, F.walk,
                           spacing=9, z=z)
        reach = set()
        for part in parts:
            if any(g[3] in part or g[0] in part for g in gr):
                reach |= part
        cells = [c for c in cells if c in reach]
        t = ctx['transitions']
        for (g, ladder, up, down) in gr:
            t.append((g[0], g[1], 0, 'STAIRS', down[0], down[1], z))
            t.append((ladder[0], ladder[1], z, 'LADDER', up[0], up[1], 0))
        far = [c for c in cells if all(abs(c[0] - g[0][0]) + abs(c[1] - g[0][1]) > 5 for g in gr)] or cells
        n_sp = max(3, len(far) // 70)
        for i in range(n_sp):
            sx, sy = far[(i * 97 + 13) % len(far)]
            m, r, k = SEWER_MONSTERS[i % len(SEWER_MONSTERS)]
            ctx['spawns'].append(f'{m};{sx};{sy};{z};{r};{k};60')
        xs = [c[0] for c in cells]
        ys = [c[1] for c in cells]
        ctx['regions_new'].insert(0, (f'Kanały {town}', min(xs), min(ys), max(xs), max(ys), z, z, 3, 20))
        ctx['sewers'][town] = (len(cells), [g[0] for g in gr], n_sp)
        print('kanaly', town, 'pola', len(cells), 'kratki', len(gr), 'spawny', n_sp)
    for town, name, near, spawns, (lo, hi) in MINES:
        spot = open_spot(world, ctx, near, radius=24) or clearing(world, ctx, near)
        levels = cave_levels('mine', *spawns)
        dungeon(ctx, name, spot, levels, rng)
        _n, x0, y0, x1, y1, zl, zh = ctx['dungeons'][-1]
        ctx['regions_new'].insert(0, (name, x0, y0, x1, y1, zl, zh, lo, hi))
        ctx.setdefault('mines', []).append((town, name, spot))


def sky_bridge(ctx, a, b, chasm=True, deck='rope_bridge'):
    """Most na pietrze +1 miedzy punktami a i b w jednym wierszu: schody w gore przy obu koncach,
    pod mostem rozpadlina (chasm) - jak mosty wiszace miedzy skalami w Tibii."""
    world = ctx['world']
    (ax, ay), (bx, by) = a, b
    assert ay == by and ax < bx
    y = ay
    for x in range(ax, bx + 1):
        assert world.get(x, y, 1) is None, f'sky bridge over existing floor at {x},{y}'
        world.put(x, y, 1, (deck, NONE, NONE, NONE))
    t = ctx['transitions']
    for (x, out) in ((ax, -1), (bx, 1)):
        c = world.get(x, y)
        g = c[0] if c[0] not in ('water', 'chasm', 'bridge') else 'dirt'
        world.put(x, y, 0, (g, NONE, 'stairs_up', NONE))
        world.put(x + out, y, 0, (g, NONE, NONE, NONE))
        world.put(x, y, 1, ('stone_floor', NONE, 'stairs_down', NONE))
    t += [(ax, y, 0, 'STAIRS', ax + 1, y, 1), (ax, y, 1, 'STAIRS', ax - 1, y, 0),
          (bx, y, 0, 'STAIRS', bx - 1, y, 1), (bx, y, 1, 'STAIRS', bx + 1, y, 0)]
    if chasm:
        for x in range(ax + 2, bx - 1):
            for yy in (y - 1, y, y + 1):
                world.put(x, yy, 0, ('chasm', NONE, NONE, NONE))
            for yy in (y - 2, y + 2):
                c = world.get(x, yy)
                if c is not None and c[0] != 'water':
                    world.put(x, yy, 0, (c[0], NONE, NONE, 'mountain'))


def find_crossing(world, T, y_from, y_to, x_from, x_to, kind):
    """Wiersz, w ktorym miedzy dwoma polami ladu jest tylko woda rzeki (kind='river') albo gory (kind='rock')."""
    best = None
    for y in range(y_from, y_to):
        row = [world.get(x, y) for x in range(x_from, x_to)]
        for i in range(1, len(row) - 1):
            if not F.walk(row[i - 1]) or row[i - 1][2] != NONE:
                continue
            j = i
            while j < len(row) and ((kind == 'river' and row[j][0] == 'water' and T.river[y, x_from + j])
                                    or (kind == 'rock' and row[j][3] in MOUNTAIN_WALLS)):
                j += 1
            span = j - i
            if j < len(row) and span >= (3 if kind == 'river' else 4) and F.walk(row[j]) and row[j][2] == NONE:
                above = all(world.get(x, yy, 1) is None for x in range(x_from + i - 2, x_from + j + 2) for yy in (y,))
                score = abs(span - 7)
                if above and (best is None or score < best[0]):
                    best = (score, (x_from + i - 1, y), (x_from + j, y))
    return best


def small_docks(ctx):
    world = ctx['world']
    spots = [((60, 560), F.W), ((150, 690), F.S_), ((300, 735), F.S_), ((400, 760), F.W), ((600, 850), F.S_),
             ((790, 640), F.E), ((820, 250), F.E), ((330, 210), F.W), ((40, 420), F.W), ((560, 470), F.E),
             ((760, 330), F.E), ((900, 640), F.N)]
    for start, d in spots:
        c = world.get(*start)
        if c is None or c[0] == 'water':
            continue
        r = F.small_dock(world, start, d)
        if r is not None:
            ctx.setdefault('docks', []).append(r[0])


def nearest_road(world, cost, p, exclude, limit=90):
    x, y = p
    best = None
    for yy in range(max(0, y - limit), min(S, y + limit)):
        for xx in range(max(0, x - limit), min(S, x + limit)):
            if cost[yy, xx] <= 0.35 and not (exclude[0] - 2 <= xx <= exclude[2] + 2 and exclude[1] - 2 <= yy <= exclude[3] + 2):
                c = world.get(xx, yy)
                if c[0] == 'dirt':
                    d = abs(xx - x) + abs(yy - y)
                    if best is None or d < best[0]:
                        best = (d, (xx, yy))
    return best[1] if best else None


def free_spot(world, near, npcs, radius=8):
    taken = {(int(n[4]), int(n[5])) for n in npcs if int(n[6]) == 0}
    x0, y0 = near
    best = None
    for r in range(radius + 1):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if max(abs(dx), abs(dy)) != r:
                    continue
                x, y = x0 + dx, y0 + dy
                c = world.get(x, y)
                if c is not None and F.walk(c) and (x, y) not in taken and c[2] == NONE:
                    return (x, y)
    raise AssertionError(f'no free spot near {near}')


# ------------------------------------------------------------------ wystroj miast (2026-10-23)
INTERIOR = {'stone_floor', 'sandstone_floor', 'marble_floor', 'wood_floor'}
GUARD_NAMES = ['Bolesław', 'Radosław', 'Mścisław', 'Gniewomir', 'Sobiesław', 'Wojciech', 'Zbigniew', 'Lechosław',
               'Dobromir', 'Jarogniew', 'Ziemowit', 'Bogusław', 'Przemysław', 'Siemowit', 'Wszebor', 'Czcibor',
               'Bronisław', 'Dobiesław', 'Gościwit', 'Jaromir', 'Kazimierz', 'Mirosław', 'Racibor', 'Sędziwój',
               'Świętopełk', 'Unisław', 'Wielisław', 'Zawisza', 'Budzisław', 'Chwalibóg', 'Domarad', 'Mieszko']
GUARD_RANKS = ['Strażnik', 'Strażniczka', 'Halabardnik', 'Kusznik']
GUARD_FEMALE = ['Dobrosława', 'Wiesława', 'Jaromira', 'Bogna', 'Świętosława', 'Zdzisława', 'Mirosława', 'Radomira',
                'Bolesława', 'Wojsława', 'Gniewomira', 'Sławomira']
# bramy miast: punkt tuz za brama (z ROADS) i miasto
GATES = [((498, 482), 'Varden'), ((498, 531), 'Varden'), ((213, 464), 'Qasirah'),
         ((244, 486), 'Qasirah'), ((721, 213), 'Frostholm'), ((741, 230), 'Frostholm'), ((656, 700), 'Selvaran')]
TOWN_BLOCKS = {'Varden': 'varden', 'Frostholm': 'frostholm', 'Qasirah': 'qasirah', 'Selvaran': 'selvaran'}
TOWN_GREEN = {'Varden': (['tree', 'bush', 'autumn_tree'], ['flowers', 'tall_grass', 'flowers']),
              'Frostholm': (['frost_pine', 'frost_bush', 'snow_rock'], ['snow_tuft']),
              'Qasirah': (['palm', 'cactus', 'dry_bush'], ['dry_grass', 'flowers', 'dry_shrub']),
              'Selvaran': (['jungle_tree', 'palm', 'jungle_bush'], ['fern', 'flowers', 'jungle_plant'])}


def in_pz(ctx, x, y, z=0):
    return any(r[0] <= x <= r[2] and r[1] <= y <= r[3] and r[4] == z for r in ctx['pz'])


def isolated(world, x, y):
    """Pole i jego 8 sasiadow wolne do chodzenia - postawiony tu obiekt nie przetnie zadnej drogi."""
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            c = world.get(x + dx, y + dy)
            if not F.walk(c) or c[2] != NONE:
                return False
    return True


def room_of(world, start, radius=14):
    sx, sy = start
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            c = world.get(*n)
            if n in seen or c is None or abs(n[0] - sx) > radius or abs(n[1] - sy) > radius:
                continue
            if c[3] == NONE and c[0] in INTERIOR and c[2] not in ('door_open', 'door'):
                seen.add(n)
                q.append(n)
    return seen


def dress_temple(world, altar, rng):
    """Dywan od oltarza do drzwi i swiece pod scianami swiatyni."""
    ax, ay = altar
    front = None
    for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        c = world.get(ax + dx, ay + dy)
        if F.walk(c) and c[2] == NONE and c[0] in INTERIOR:
            front = (ax + dx, ay + dy)
            break
    if front is None:
        return 0, 0
    room = room_of(world, front)
    doors = [(x + dx, y + dy) for (x, y) in room for dx, dy in F.DIRS4
             if (world.get(x + dx, y + dy) or WATER)[2] in ('door_open', 'door')]
    path = []
    if doors:
        prev = {front: None}
        q = deque([front])
        goal = None
        while q and goal is None:
            p = q.popleft()
            for dx, dy in F.DIRS4:
                n = (p[0] + dx, p[1] + dy)
                if n in doors:
                    goal = p
                    break
                if n in room and n not in prev and world.get(*n)[2] == NONE:
                    prev[n] = p
                    q.append(n)
        while goal is not None:
            path.append(goal)
            goal = prev[goal]
    for (x, y) in path:
        c = world.get(x, y)
        world.put(x, y, 0, (c[0], 'carpet', c[2], c[3]))
    onpath = set(path)
    near_door = {(d[0] + dx, d[1] + dy) for d in doors for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    cands = []
    for (x, y) in room:
        c = world.get(x, y)
        if (x, y) in onpath or (x, y) in near_door or c[2] != NONE:
            continue
        if any((world.get(x + dx, y + dy) or WATER)[3] != NONE for dx, dy in F.DIRS4):
            cands.append((abs(x - ax) + abs(y - ay), x, y))
    cands.sort()
    placed = []
    for _d, x, y in cands:
        if len(placed) >= 6:
            break
        if all(abs(x - px) + abs(y - py) >= 3 for px, py in placed):
            c = world.get(x, y)
            world.put(x, y, 0, (c[0], c[1], 'candle', c[3]))
            placed.append((x, y))
    return len(path), len(placed)


def open_spot(world, ctx, near, radius=12, avoid_pz=True):
    x0, y0 = near
    for r in range(radius + 1):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if max(abs(dx), abs(dy)) != r:
                    continue
                x, y = x0 + dx, y0 + dy
                c = world.get(x, y)
                if c is None or c[0] in F.ROAD_GROUND or c[0] in ('field', 'bridge') or c[1] == 'wheat':
                    continue
                if avoid_pz and in_pz(ctx, x, y):
                    continue
                if any((x + ex, y + ey) in ctx['varden_core'] for ex in (-2, 0, 2) for ey in (-2, 0, 2)):
                    continue
                if all(isolated(world, x + ex, y + ey) for ex in (-1, 0, 1) for ey in (-1, 0, 1)):
                    return (x, y)
    return None


def street_sides(ctx):
    """Donice, kwiaty i lawki ze srodka ulic pod mury (Varden ma je juz z generate_varden.py)."""
    world = ctx['world']
    core = ctx['varden_core']
    npcs = {(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0}

    def get(x, y):
        return None if (x, y) in npcs else world.get(x, y)

    cells = [(x, y) for y in range(S) for x in range(S) if (x, y) not in core]
    moved, removed = sidewalk.move_props(get, lambda x, y, c: world.put(x, y, 0, c), cells, avoid=core)
    print('pobocza: przesuniete', moved, 'usuniete', removed)


INDOOR_FLOORS = {'wood_floor', 'stone_floor', 'sandstone_floor', 'frozen_floor', 'marble_floor', 'ship_deck'}
CAVE_FLOORS = {'cave_floor', 'dirt', 'frozen_floor', 'ash', 'stone_floor'}


def atmosphere(ctx):
    """Pajeczyny w zakamarkach jaskin (rogi miedzy dwoma murami), balwany na snieznych placach, a we wnetrzach
    czesc skrzyn i beczek zamieniona na worki i gliniane dzbany. Wybor wg pozycji pola (bez losowania), wiec reszta mapy sie nie przesuwa; rdzen Varden bez zmian."""
    world = ctx['world']
    vb = next(bb for bb in ctx['blocks'] if bb.name == 'varden')
    vx0, vy0, vx1, vy1 = vb.rect[0] + vb.dx, vb.rect[1] + vb.dy, vb.rect[2] + vb.dx, vb.rect[3] + vb.dy
    webs = snowmen = pots = 0
    for z, rows in world.floors.items():
        for y in range(1, S - 1):
            for x in range(1, S - 1):
                if vx0 - 1 <= x <= vx1 + 1 and vy0 - 1 <= y <= vy1 + 1:
                    continue
                c = rows[y][x]
                if c is None:
                    continue
                h = (x * 7349 + y * 1913 + z * 409) % 7
                if c[0] in INDOOR_FLOORS and c[3] == NONE and c[2] in ('crate', 'barrel') and h % 3 == 0:
                    rows[y][x] = (c[0], c[1], 'sack' if c[2] == 'crate' else 'clay_pot', NONE)
                    pots += 1
                elif z < 0 and c[0] in CAVE_FLOORS and c[1] == NONE and c[2] == NONE and c[3] == NONE and h == 0:
                    walls = [rows[y + dy][x + dx] is not None and rows[y + dy][x + dx][3] != NONE
                             for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))]
                    if any(walls[k] and walls[(k + 1) % 4] for k in range(4)):
                        rows[y][x] = (c[0], 'cobweb', NONE, NONE)
                        webs += 1
    # balwany na snieznych placach miast (strefy ochronne), tylko na polu z 8 wolnymi sasiadami
    placed = []
    for (x0, y0, x1, y1, z) in ctx['pz']:
        if z != 0:
            continue
        for y in range(max(1, y0), min(S - 1, y1 + 1)):
            for x in range(max(1, x0), min(S - 1, x1 + 1)):
                c = world.get(x, y)
                if c is None or c[0] != 'snow' or c[2] != NONE or c[3] != NONE or (x * 31 + y * 17) % 11:
                    continue
                if vx0 - 1 <= x <= vx1 + 1 and vy0 - 1 <= y <= vy1 + 1:
                    continue
                if any(abs(x - px) < 6 and abs(y - py) < 6 for (px, py) in placed):
                    continue
                around = [world.get(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy]
                if all(n is not None and F.walk(n) and n[2] == NONE for n in around):
                    world.put(x, y, 0, (c[0], c[1], 'snowman', NONE))
                    placed.append((x, y))
                    snowmen += 1
    print('klimat: pajeczyny', webs, 'balwany', snowmen, 'worki i dzbany', pots)


SOFT_GROUNDS = {'grass', 'snow', 'sand', 'dark_sand', 'ash', 'jungle_grass', 'blighted_grass'}
# rosliny pasujace do podloza: po zmianie podloza nie zostaje sosna na piasku ani palma na sniegu
COLD_ONLY = {'pine', 'snow_rock', 'snow_tuft', 'frost_pine', 'frost_bush', 'ice_crystal'}
WARM_ONLY = {'palm', 'cactus', 'sandstone_rock', 'dry_grass', 'shells', 'dry_bush', 'dry_shrub'}
COLD, WARM = {'snow'}, {'sand', 'dark_sand', 'ash'}


def smooth_noise(seed, base):
    """Gladki szum -1..1 na calej mapie: losowa siatka co base pol, interpolacja dwuliniowa."""
    rs = np.random.RandomState(seed)
    n = S // base + 2
    grid = rs.uniform(-1, 1, (n, n))
    ys, xs = np.mgrid[0:S, 0:S] / base
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = grid[y0, x0] * (1 - fx) + grid[y0, x0 + 1] * fx
    b = grid[y0 + 1, x0] * (1 - fx) + grid[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


def soften_edges(ctx):
    """Granice naturalnych podlozy (trawa, snieg, piasek, popiol...) bez schodkow i prostokatow: kazde pole bierze
    podloze z pola przesunietego o gladki szum (do 2-3 pol). Zmienia sie tylko podloze miedzy przechodnimi
    podlozami naturalnymi, wiec przejscia, drogi, woda, miasta i Varden zostaja bez zmian."""
    world = ctx['world']
    rows = world.floors[0]
    vb = next(bb for bb in ctx['blocks'] if bb.name == 'varden')
    vx0, vy0, vx1, vy1 = vb.rect[0] + vb.dx - 2, vb.rect[1] + vb.dy - 2, vb.rect[2] + vb.dx + 2, vb.rect[3] + vb.dy + 2
    pz = [r for r in ctx['pz'] if r[4] == 0]
    wx = smooth_noise(41, 6) * 2.2 + smooth_noise(42, 3) * 0.8
    wy = smooth_noise(43, 6) * 2.2 + smooth_noise(44, 3) * 0.8
    old = [[c[0] if c is not None else None for c in row] for row in rows]
    changed = 0
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            c = rows[y][x]
            if c is None or c[0] not in SOFT_GROUNDS or c[3] != NONE:
                continue
            if vx0 <= x <= vx1 and vy0 <= y <= vy1 or any(r[0] - 1 <= x <= r[2] + 1 and r[1] - 1 <= y <= r[3] + 1 for r in pz):
                continue
            sx, sy = x + int(round(wx[y, x])), y + int(round(wy[y, x]))
            src = old[sy][sx] if 0 <= sx < S and 0 <= sy < S else None
            if src is None or src == c[0] or src not in SOFT_GROUNDS:
                continue
            deco, obj = c[1], c[2]
            if src in WARM and deco in COLD_ONLY or src in COLD and deco in WARM_ONLY:
                deco = NONE
            if src in WARM and obj in COLD_ONLY or src in COLD and obj in WARM_ONLY:
                obj = NONE
            rows[y][x] = (src, deco, obj, NONE)
            changed += 1
    # dwa przejscia wygladzania: pojedyncze wypustki i wciecia znikaja (pole bierze podloze, ktore ma co najmniej
    # 6 z 8 sasiadow), wiec brzegi sa rowne jak w Tibii zamiast poszarpanych kresek szerokosci jednego pola
    for _ in range(2):
        snap = [[c[0] if c is not None else None for c in row] for row in rows]
        for y in range(2, S - 2):
            for x in range(2, S - 2):
                c = rows[y][x]
                if c is None or c[0] not in SOFT_GROUNDS or c[3] != NONE:
                    continue
                if vx0 <= x <= vx1 and vy0 <= y <= vy1 or any(r[0] - 1 <= x <= r[2] + 1 and r[1] - 1 <= y <= r[3] + 1 for r in pz):
                    continue
                count = {}
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        if dx or dy:
                            g = snap[y + dy][x + dx]
                            count[g] = count.get(g, 0) + 1
                g, k = max(count.items(), key=lambda kv: kv[1])
                if k >= 6 and g != c[0] and g in SOFT_GROUNDS:
                    deco, obj = c[1], c[2]
                    if g in WARM and deco in COLD_ONLY or g in COLD and deco in WARM_ONLY:
                        deco = NONE
                    if g in WARM and obj in COLD_ONLY or g in COLD and obj in WARM_ONLY:
                        obj = NONE
                    rows[y][x] = (g, deco, obj, NONE)
                    changed += 1
    print('krawedzie terenow: zmienione pola', changed)


def towns_dressing(ctx):
    world = ctx['world']
    rng = random.Random(20261036)
    stats = {}
    towns = dict(ctx['towns'])
    # swiatynie poza Varden (Varden ma swiece z generate_varden.py, a jego rdzen zostaje 1:1)
    for name in ('Frostholm', 'Qasirah', 'Selvaran'):
        if name in ctx['rebuilt']:
            continue                      # nowe swiatynie maja juz swiece i lawki (organic.py)
        tx, ty, _ = towns[name]
        altars = [(x, y) for y in range(ty - 12, ty + 13) for x in range(tx - 12, tx + 13)
                  if (world.get(x, y) or WATER)[2] == 'altar']
        assert altars, f'no altar in the temple of {name}'
        altars.sort(key=lambda p: abs(p[0] - tx) + abs(p[1] - ty))
        stats[name] = dress_temple(world, altars[0], rng)
    # sala tronowa krola Aureliana (dzielnica krolewska Varden): oltarz -> tron; po przebudowie dzielnicy palac
    # z tronem i krolem stawia organic.py
    if 'Varden-East' not in ctx['rebuilt']:
        kx, ky = ctx['remap'].point(129, 72, 0)[:2]
        c = world.get(kx, ky)
        assert c[2] == 'altar', f'throne room altar not found at {kx},{ky}: {c}'
        world.put(kx, ky, 0, (c[0], 'carpet', 'throne', c[3]))
        king = (kx, ky + 1)
        hall = room_of(world, king, radius=10)
        xs = [p[0] for p in hall]
        ys = [p[1] for p in hall]
        ctx['pz'].append((min(xs), min(ys), max(xs), max(ys), 0))
        y = ky + 2
        while (kx, y) in hall:
            cc = world.get(kx, y)
            world.put(kx, y, 0, (cc[0], 'carpet', cc[2], cc[3]))
            y += 1
        for (x, yy) in sorted(hall):
            cc = world.get(x, yy)
            if cc[2] == NONE and abs(x - kx) == 2 and yy in (ky, ky + 3):
                world.put(x, yy, 0, (cc[0], cc[1], 'candle', cc[3]))
        ctx['npcs'].append(['varden_king', 'Król Aurelian', 'KING', 'Varden', king[0], king[1], 0, '-', '#c8a040',
                            'Witaj w moim królestwie. Konta premium i promocje załatwisz tutaj: napisz premium albo promocja.'])
    # straznicy przy bramach (w strefie ochronnej, tuz za brama)
    used = set()
    guards = 0
    gates = [g for g in GATES if g[1] not in ctx['rebuilt']] + \
        [(p, t['name']) for t in ctx['new_towns'] + list(ctx['rebuilt'].values()) for p in t['gates'].values()]
    for (gx, gy), town in gates:
        spot = None
        for r in range(1, 9):
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    x, y = gx + dx, gy + dy
                    c = world.get(x, y)
                    if spot is None and max(abs(dx), abs(dy)) == r and c is not None and F.walk(c) and c[2] == NONE \
                            and in_pz(ctx, x, y) and c[0] in F.ROAD_GROUND | INTERIOR \
                            and not any(int(n[4]) == x and int(n[5]) == y and int(n[6]) == 0 for n in ctx['npcs']):
                        spot = (x, y)
            if spot:
                break
        if spot is None:
            continue
        # straznik stoi przy murze z boku drogi, nie na srodku przejscia
        taken = {(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0}
        outdoor = {k for k, (layer, ok) in tk.TILES.items() if layer == 'GROUND' and ok} - {'wood_floor', 'marble_floor'}
        side = sidewalk.nearest_roadside(lambda x, y: None if (x, y) in taken else world.get(x, y), gx, gy,
                                         outdoor, radius=7, avoid={(x, y) for y in range(gy - 8, gy + 9)
                                                                   for x in range(gx - 8, gx + 9) if not in_pz(ctx, x, y)})
        if side is None:
            # bez muru obok: dowolne pole, ktore nie zamyka przejscia
            get = lambda x, y: None if (x, y) in taken else world.get(x, y)  # noqa: E731
            cand = [(x, y) for y in range(gy - 8, gy + 9) for x in range(gx - 8, gx + 9)
                    if in_pz(ctx, x, y) and F.walk(get(x, y)) and get(x, y)[2] == NONE and get(x, y)[0] in outdoor
                    and sidewalk.keeps_passage(get, x, y)
                    and sum(1 for dx, dy in F.DIRS4 if F.walk(get(x + dx, y + dy))) >= 3]
            if cand:
                side = min(cand, key=lambda p: (p[0] - gx) ** 2 + (p[1] - gy) ** 2)
        if side is not None:
            spot = side
        if rng.random() < 0.3:
            title, first = rng.choice(['Strażniczka', 'Łuczniczka']), rng.choice([n for n in GUARD_FEMALE if n not in used])
        else:
            title, first = rng.choice(['Strażnik', 'Halabardnik', 'Kusznik']), rng.choice([n for n in GUARD_NAMES if n not in used])
        used.add(first)
        guards += 1
        ctx['npcs'].append([f'guard_{guards}', f'{title} {first}', 'GUARD', town, spot[0], spot[1], 0, '-', '#5a6a8a',
                            'Spokojnie, wędrowcze. Pod moją strażą w mieście nic ci nie grozi.'])
    stats['guards'] = guards
    # wiatraki: wioski zywe i pola za Qasirah i Varden
    mills = []
    for near in [v['centre'] for v in ctx.get('villages', []) if v['name'] in ('Wierzbowo', 'Mroźna Osada')] + \
            [(198, 459), (446, 461), (480, 720)]:
        p = open_spot(world, ctx, (near[0] + 8, near[1] - 4), radius=24)
        if p is not None:
            c = world.get(*p)
            world.put(p[0], p[1], 0, (c[0], NONE, 'windmill', c[3]))
            mills.append(p)
    ctx['windmills'] = mills
    # zielen w miastach (poza rdzeniem Varden): drzewa tylko na polach z wolnymi 8 sasiadami (nigdy obok siebie)
    green = 0
    for town, bname in TOWN_BLOCKS.items():
        if town in ctx.get('rebuilt', {}):
            continue                       # nowe miasta maja wlasna zielen (organic.py)
        b = next(bb for bb in ctx['blocks'] if bb.name == bname)
        objs, decos = TOWN_GREEN[town]
        for (x, y) in sorted(b.surface):
            if bname == 'varden' and VARDEN_CORE[0] <= x <= VARDEN_CORE[2] and VARDEN_CORE[1] <= y <= VARDEN_CORE[3]:
                continue
            nx, ny = b.new(x, y)
            c = world.get(nx, ny)
            if c is None or c[0] not in NATURAL_GROUND or c[2] != NONE or c[3] != NONE:
                continue
            r = rng.random()
            if r < 0.05 and isolated(world, nx, ny) and not in_pz(ctx, nx, ny):
                world.put(nx, ny, 0, (c[0], NONE, rng.choice(objs), NONE))
                green += 1
            elif r < 0.05 and isolated(world, nx, ny) and rng.random() < 0.5:
                world.put(nx, ny, 0, (c[0], NONE, 'bush' if town != 'Frostholm' else 'snow_rock', NONE))
                green += 1
            elif r < 0.16 and c[1] == NONE:
                world.put(nx, ny, 0, (c[0], rng.choice(decos), NONE, NONE))
                green += 1
    stats['green'] = green
    ctx['dressing'] = stats
    return stats


# przewoznicy na malych wyspach (Mewia Skala, Latarnia) - inaczej wyspy bylyby nieosiagalne
FERRIES = [('gull', 'gull_ferryman', 'Przewoźnik Bolko', 'Mewia Skała', ['qas_captain', 'varden_captain']),
           ('lighthouse', 'lighthouse_ferryman', 'Latarniczka Wanda', 'Latarnia', ['qas_captain', 'varden_captain'])]


def ferries(ctx):
    world = ctx['world']
    for bname, key, name, town, partners in FERRIES:
        b = next(bb for bb in ctx['blocks'] if bb.name == bname)
        cells = [b.new(x, y) for (x, y) in b.surface]
        cx = sum(c[0] for c in cells) / len(cells)
        cy = sum(c[1] for c in cells) / len(cells)
        best = None
        for (x, y) in cells:
            c = world.get(x, y)
            if not F.walk(c) or c[2] != NONE or c[0] == 'water':
                continue
            if not any((world.get(x + dx, y + dy) or WATER)[0] == 'water' for dx, dy in F.DIRS4):
                continue
            land = sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1) if F.walk(world.get(x + dx, y + dy))
                       and world.get(x + dx, y + dy)[0] != 'water')
            if land < 6:
                continue
            # brzeg zwrocony do kontynentu (blizej srodka mapy)
            d = abs(x - S // 2) + abs(y - S // 2)
            if best is None or d < best[0]:
                best = (d, x, y)
        assert best is not None, f'no shore for the ferry on {bname}'
        _d, x, y = best
        ctx['pz'].append((x - 1, y - 1, x + 1, y + 1, 0))
        ctx['npcs'].append([key, name, 'CAPTAIN', town, x, y, 0, '-', '#4a6a8a',
                            'Przewożę łodzią na stały ląd. Tylko posiadacze konta premium.'])
        for partner in partners:
            ctx['routes'].append((key, partner))

# ------------------------------------------------------------------ dojscie na wszystkie wyspy (2026-10-28)
# Darek: "dostep na wszystkie wyspy przez podziemia, statki, kladki, mosty; roznorodne potworki o roznym poziomie;
# pod jedna wyspa podziemia w stylu lochow, trudny poziom dla wysokopoziomowych graczy".
SEA = {'water', 'deep_water'}
NOT_LAND = SEA | {'lava', 'chasm'}
ISLAND_MIN = 60
ISLAND_TIERS = [
    # najdalej od Varden (pola), poziomy regionu, potwory [(klucz, waga)]
    (300, (10, 40), [('boar', 2), ('forest_spider', 2), ('bandit', 2), ('goblin', 2), ('skeleton', 1),
                      ('carrion_crow', 1), ('snare_setter', 1), ('tunnel_fence', 1)]),
    (380, (45, 130), [('crocodile', 2), ('jungle_serpent', 2), ('orc_warrior', 2), ('orc_shaman', 1), ('scorpion', 1),
                       ('swamp_hornet', 1), ('sand_devourer', 1), ('striped_prowler', 1)]),
    (450, (150, 260), [('orc_berserker', 2), ('mummy', 2), ('temple_guardian', 2), ('cursed_treant', 1),
                        ('stone_sentinel', 1), ('sand_gargoyle', 1), ('blade_warden', 2)]),
    (10 ** 6, (280, 420), [('drowned_sailor', 2), ('pirate_cutthroat', 2), ('coral_crab', 2), ('wraith_knight', 1),
                            ('dune_drake', 1), ('crimson_shade', 1), ('ember_beetle', 1)]),
]
ISLAND_NAMES = ['Wyspa Mewia', 'Wyspa Bursztynowa', 'Wyspa Szeptów', 'Wyspa Kormoranów', 'Wyspa Wraków', 'Wyspa Fok',
                'Wyspa Zachodu', 'Wyspa Mgieł Porannych', 'Wyspa Kamiennego Kręgu', 'Wyspa Rybaków', 'Wyspa Czapli',
                'Wyspa Sosnowa', 'Wyspa Ukwiałów', 'Wyspa Burz', 'Wyspa Lisów', 'Wyspa Starej Latarni',
                'Wyspa Wilcza', 'Wyspa Słonego Wiatru', 'Wyspa Bocianów', 'Wyspa Piaskowa', 'Wyspa Złotego Brzegu',
                'Wyspa Ciszy', 'Wyspa Krabów', 'Wyspa Przemytników', 'Wyspa Rafy', 'Wyspa Pustelnika',
                'Wyspa Białych Skał', 'Wyspa Kruków', 'Wyspa Delfinów', 'Wyspa Paproci', 'Wyspa Dzwonów',
                'Wyspa Sztormu', 'Wyspa Muszli', 'Wyspa Orlicy', 'Wyspa Zatoki', 'Wyspa Bezimienna',
                'Wyspa Topielców', 'Wyspa Wrzosów', 'Wyspa Kutra', 'Wyspa Zielona', 'Wyspa Szkwału', 'Wyspa Łabędzia',
                'Wyspa Zakotwiczona', 'Wyspa Dalekiego Brzegu', 'Wyspa Ostatnia']
FERRYMEN = ['Przewoźnik Gniewko', 'Przewoźniczka Jagna', 'Przewoźnik Wojsław', 'Przewoźniczka Dobrawa',
            'Przewoźnik Sambor', 'Przewoźniczka Mila', 'Przewoźnik Bratumił', 'Przewoźniczka Ludmiła',
            'Przewoźnik Zbysław', 'Przewoźniczka Wiesna', 'Przewoźnik Racibor', 'Przewoźniczka Bogumiła',
            'Przewoźnik Dobromir', 'Przewoźniczka Sława', 'Przewoźnik Ciechosław', 'Przewoźniczka Grzymisława',
            'Przewoźnik Mirogniew', 'Przewoźniczka Wanda', 'Przewoźnik Lubomir', 'Przewoźniczka Zbysława',
            'Przewoźnik Krzesimir', 'Przewoźniczka Jarosława', 'Przewoźnik Domasław', 'Przewoźniczka Unisława',
            'Przewoźnik Żelisław', 'Przewoźniczka Tomira', 'Przewoźnik Wszebor', 'Przewoźniczka Czesława',
            'Przewoźnik Bogdan', 'Przewoźniczka Radosława', 'Przewoźnik Sulisław', 'Przewoźniczka Milena']
TUNNEL_SPAWNS = [[('giant_rat', 3, 3), ('burrow_larva', 3, 2), ('forest_spider', 3, 2)],
                 [('jungle_serpent', 3, 2), ('swamp_hornet', 3, 2), ('crocodile', 3, 2)],
                 [('orc_berserker', 3, 2), ('blade_warden', 3, 2), ('mummy', 3, 2)],
                 [('drowned_sailor', 3, 3), ('dune_drake', 3, 1), ('pirate_cutthroat', 3, 2)]]
LOCHY = ('Lochy Zapomnianych', [
    (64, 48, [(6, 6), (26, 10), (52, 8), (54, 30), (32, 40), (8, 36)], 'crypt', 0,
     [('wraith_knight', 4, 3), ('shadow_stalker', 4, 2), ('dusk_gargoyle', 4, 2), ('shadow_stalker', 3, 2)]),
    (64, 48, [(8, 36), (10, 14), (34, 6), (56, 18), (50, 42), (28, 26)], 'crypt', 0,
     [('shadow_stalker', 4, 3), ('void_priest', 3, 2), ('shadow_centipede', 4, 3), ('void_priest', 3, 2)]),
    (64, 48, [(28, 26), (52, 40), (58, 12), (32, 6), (8, 18), (12, 42)], 'crypt', 0,
     [('void_priest', 4, 3), ('bone_dragon', 3, 1), ('abyss_crawler', 4, 2), ('bone_dragon', 3, 2)]),
    (64, 48, [(12, 42), (34, 40), (56, 30), (50, 8), (22, 8)], 'crypt', 0,
     [('bone_dragon', 4, 3), ('abyss_lord', 3, 1), ('winged_spawn', 4, 2), ('obsidian_sentinel', 3, 2)]),
], [(-1, -2, 350, 750), (-3, -4, 700, 960)])


def land_parts(world, by_ground=False):
    """Spojne kawalki ladu, po ktorym da sie chodzic (z mostami, bez gor i drzew): tablica numerow i lista [pola]."""
    lab = np.zeros((S, S), np.int32)
    parts = [None]
    g = world.floors[0]
    for y0 in range(S):
        for x0 in range(S):
            c = g[y0][x0]
            if lab[y0, x0] or c is None or c[0] in NOT_LAND or not (by_ground or F.walk(c)):
                continue
            k = len(parts)
            cells = [(x0, y0)]
            lab[y0, x0] = k
            i = 0
            while i < len(cells):
                x, y = cells[i]
                i += 1
                for dx, dy in F.DIRS4:
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and not lab[ny, nx]:
                        cc = g[ny][nx]
                        if cc is not None and cc[0] not in NOT_LAND and (by_ground or F.walk(cc)):
                            lab[ny, nx] = k
                            cells.append((nx, ny))
            parts.append(cells)
    return lab, parts


def standable(world, x, y):
    c = world.get(x, y)
    return c is not None and F.walk(c) and c[2] == NONE and c[0] not in NOT_LAND


def sea_paths(world, lab, linked, limit):
    """BFS po wodzie od brzegow polaczonego ladu (linked: numery kawalkow) do limit pol. Zwraca (odleglosc, rodzic)."""
    g = world.floors[0]
    dist = np.full((S, S), -1, np.int32)
    parent = {}
    q = deque()
    for y in range(1, S - 1):
        for x in range(1, S - 1):
            if lab[y, x] in linked and standable(world, x, y):
                for dx, dy in F.DIRS4:
                    nx, ny = x + dx, y + dy
                    c = g[ny][nx]
                    if c is not None and c[0] in SEA and c[2] == NONE and c[3] == NONE and dist[ny, nx] < 0:
                        dist[ny, nx] = 1
                        parent[(nx, ny)] = (x, y)
                        q.append((nx, ny))
    while q:
        x, y = q.popleft()
        if dist[y, x] >= limit:
            continue
        for dx, dy in F.DIRS4:
            nx, ny = x + dx, y + dy
            if not (0 < nx < S - 1 and 0 < ny < S - 1) or dist[ny, nx] >= 0:
                continue
            c = g[ny][nx]
            if c is not None and c[0] in SEA and c[2] == NONE and c[3] == NONE:
                dist[ny, nx] = dist[y, x] + 1
                parent[(nx, ny)] = (x, y)
                q.append((nx, ny))
    return dist, parent


def best_landing(world, lab, part_id, cells, dist):
    """Najblizszy wodzie polaczonego ladu brzeg wyspy: (odleglosc, pole ladu, pole wody)."""
    best = None
    for (x, y) in cells:
        if not standable(world, x, y):
            continue
        for dx, dy in F.DIRS4:
            nx, ny = x + dx, y + dy
            if 0 <= nx < S and 0 <= ny < S and dist[ny, nx] > 0:
                if best is None or dist[ny, nx] < best[0]:
                    best = (int(dist[ny, nx]), (x, y), (nx, ny))
    return best


def trace(parent, water):
    path = [water]
    while path[-1] in parent:
        path.append(parent[path[-1]])
    return path            # woda ... woda, pole ladu po drugiej stronie


def lay_bridge(world, path, wide):
    """Kladka (1 pole) albo most (2 pola) po wodnych polach sciezki."""
    water = path[:-1]
    for i, (x, y) in enumerate(water):
        world.put(x, y, 0, ('bridge', NONE, NONE, NONE))
        if wide:
            a = water[i - 1] if i else path[-1]
            b = water[i + 1] if i + 1 < len(water) else path[-1]
            vx, vy = (1, 0) if a[1] != b[1] or a[1] != y else (0, 1)
            for (px, py) in ((x + vx, y + vy),):
                c = world.get(px, py)
                if c is not None and c[0] in SEA and c[2] == NONE:
                    world.put(px, py, 0, ('bridge', NONE, NONE, NONE))


def tunnel(ctx, a, b, rng, tier):
    """Tunel pod morzem miedzy polami ladu a i b (na pietrze -1, -2 albo -3) ze schodami na obu brzegach."""
    world = ctx['world']
    x0, x1 = min(a[0], b[0]) - 8, max(a[0], b[0]) + 8
    y0, y1 = min(a[1], b[1]) - 8, max(a[1], b[1]) + 8
    for z in (-1, -2, -3):
        occ = world.occ[z]

        def free(x, y):
            if not (x0 <= x <= x1 and y0 <= y <= y1 and 2 <= x < S - 3 and 2 <= y < S - 3):
                return False
            return all(world.get(x + dx, y + dy, z) is None and not occ[y + dy, x + dx]
                       for dx in (-1, 0, 1, 2) for dy in (-1, 0, 1, 2))
        if not free(*a) or not free(*b):
            continue
        # A* (4 kierunki) z lekkim szumem: tunel kreci sie, ale nie meandruje
        start, goal = a, b
        openh = [(0, start)]
        came = {start: None}
        g_ = {start: 0}
        while openh:
            _f, cur = heapq.heappop(openh)
            if cur == goal:
                break
            for dx, dy in F.DIRS4:
                n = (cur[0] + dx, cur[1] + dy)
                if n in g_ or not free(*n):
                    continue
                g_[n] = g_[cur] + 1 + rng.random() * 0.6
                came[n] = cur
                heapq.heappush(openh, (g_[n] + abs(n[0] - goal[0]) + abs(n[1] - goal[1]), n))
        if goal not in came:
            continue
        path = [goal]
        while came[path[-1]] is not None:
            path.append(came[path[-1]])
        path.reverse()
        carved = set()
        for (x, y) in path:
            for dx in (0, 1):
                for dy in (0, 1):
                    carved.add((x + dx, y + dy))
        for (x, y) in carved:
            r = rng.random()
            obj = 'glow_moss' if r < 0.03 else NONE
            deco = 'cobweb' if r > 0.985 else NONE
            world.put(x, y, z, ('cave_floor', deco, obj, NONE))
        for (x, y) in carved:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if world.get(x + dx, y + dy, z) is None:
                        world.put(x + dx, y + dy, z, ('cave_floor', NONE, NONE, 'mossy_wall'))
        for (x, y) in carved:
            for dx in range(-3, 4):
                for dy in range(-3, 4):
                    if 0 <= x + dx < S and 0 <= y + dy < S:
                        occ[y + dy, x + dx] = True
        t = ctx['transitions']
        for end, nxt in ((path[0], path[2]), (path[-1], path[-3])):
            ex, ey = end
            out = next(((ex + dx, ey + dy) for dx, dy in F.DIRS4 if standable(world, ex + dx, ey + dy)), None)
            assert out is not None, f'no land next to tunnel stairs {end}'
            c = world.get(ex, ey)
            world.put(ex, ey, 0, (c[0] if c[0] not in ('bridge',) else 'dirt', NONE, 'stairs_down', NONE))
            world.put(ex, ey, z, ('cave_floor', NONE, 'stairs_up', NONE))
            t.append((ex, ey, 0, 'STAIRS', nxt[0], nxt[1], z))
            t.append((ex, ey, z, 'STAIRS', out[0], out[1], 0))
        pool = TUNNEL_SPAWNS[tier]
        n = max(1, min(4, len(path) // 18))
        for i in range(n):
            sx, sy = path[(i + 1) * len(path) // (n + 1)]
            m, r, k = pool[i % len(pool)]
            ctx['spawns'].append(f'{m};{sx};{sy};{z};{r};{k};120')
        xs = [p[0] for p in carved]
        ys = [p[1] for p in carved]
        lo, hi = ISLAND_TIERS[tier][1]
        ctx['regions_new'].insert(0, ('Tunel pod Morzem', min(xs), min(ys), max(xs), max(ys), z, z, lo, hi))
        return z, len(path)
    return None


def inland(world, lab, p):
    """Pole przy brzegu p (do 6 pol w glab ladu), na ktorym zmiesci sie zejscie: wolne, z wolnymi 8 sasiadami."""
    k = lab[p[1], p[0]]
    best = None
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            x, y = p[0] + dx, p[1] + dy
            if not (0 < x < S - 1 and 0 < y < S - 1) or lab[y, x] != k or not standable(world, x, y):
                continue
            # zejscie nie moze zamykac przejscia: wszystkie 8 pol dookola wolne
            n = sum(1 for a in (-1, 0, 1) for b in (-1, 0, 1) if (a or b) and standable(world, x + a, y + b))
            if n == 8 and (best is None or abs(dx) + abs(dy) < best[0]):
                best = (abs(dx) + abs(dy), (x, y))
    return best[1] if best else None


def island_tier(cells):
    tx, ty = dict(ctx_towns_cache)['Varden'][:2]
    cx = sum(c[0] for c in cells) / len(cells)
    cy = sum(c[1] for c in cells) / len(cells)
    d = math.hypot(cx - tx, cy - ty)
    return next(i for i, (lim, _lv, _p) in enumerate(ISLAND_TIERS) if d < lim)


ctx_towns_cache = []


def island_access(ctx, rng):
    """Kazda wyspa (ponad ISLAND_MIN pol) dostaje dojscie: kladka (do 8 pol wody), most (do 14), tunel pod morzem
    (do 60) albo przewoznika z lodzia; potwory wg odleglosci od Varden; pod najwieksza wyspa Lochy Zapomnianych."""
    world = ctx['world']
    ctx_towns_cache[:] = ctx['towns']
    lab, parts = land_parts(world)
    glab, _gparts = land_parts(world, by_ground=True)
    temple = dict(ctx['towns'])['Varden']
    mainland = int(glab[temple[1], temple[0]])
    # wyspy to kawalki ladu oddzielone od kontynentu woda (zamkniete gorami kotliny na ladzie zostaja jak sa)
    sea_island = [False] + [glab[p[0][1], p[0][0]] != mainland for p in parts[1:]]
    main = int(lab[temple[1], temple[0]])
    linked = {main} | {int(lab[p[1], p[0]]) for _n, p in ctx['towns']}
    for n in ctx['npcs']:
        if n[2] == 'CAPTAIN' and int(n[6]) == 0:
            linked.add(int(lab[int(n[5]), int(n[4])]))
    stats = {'kladki': 0, 'mosty': 0, 'tunele': 0, 'przewoznicy': 0}
    islands = []           # (numer, sposob)
    for rnd in range(2):
        dist, parent = sea_paths(world, lab, linked, 60)
        todo = []
        for k in range(1, len(parts)):
            if k in linked or len(parts[k]) < ISLAND_MIN or not sea_island[k]:
                continue
            bl = best_landing(world, lab, k, parts[k], dist)
            if bl is not None:
                todo.append((bl[0], k, bl))
        todo.sort()
        for d, k, (_d, land, water) in todo:
            path = trace(parent, water)
            other = path[-1]
            if d <= 14:
                lay_bridge(world, path, wide=d > 8)
                stats['kladki' if d <= 8 else 'mosty'] += 1
                islands.append((k, 'kładka' if d <= 8 else 'most'))
                linked.add(k)
            elif rnd == 1:
                tier = island_tier(parts[k])
                a, b = inland(world, lab, land), inland(world, lab, other)
                if a and b and tunnel(ctx, a, b, rng, tier):
                    stats['tunele'] += 1
                    islands.append((k, 'tunel'))
                    linked.add(k)
    # reszta: przewoznik z lodzia na brzegu zwroconym do ladu, rejs do dwoch najblizszych kapitanow
    # przewoznicy plywaja do portow miast (kapitanowie dawnych wysp maja wlasne, stale trasy)
    ports = {n for n, _p in ctx['towns']} | {'Przystań Wschodnia'}
    captains = [n for n in ctx['npcs'] if n[2] == 'CAPTAIN' and int(n[6]) == 0 and n[3] in ports]
    fi = 0
    ferry_names = {}
    # nazwy wysp nie moga powtarzac miejsc, do ktorych juz plywaja statki, ani starych regionow
    used = {n[3] for n in ctx['npcs']} | {r[0] for r in ctx['regions'] + ctx['regions_new']}
    ISLAND_NAMES[:] = [n for n in ISLAND_NAMES if n not in used]
    for k in sorted(range(1, len(parts)), key=lambda i: -len(parts[i])):
        if k in linked or len(parts[k]) < ISLAND_MIN or not sea_island[k]:
            continue
        cells = parts[k]
        best = None
        for need_land, reach_out in ((6, 8), (3, 5)):          # male wysepki: luzniejsze wymagania
            for (x, y) in cells:
                if not standable(world, x, y):
                    continue
                for d in (F.N, F.S_, F.E, F.W):
                    c = world.get(x + d[0], y + d[1])
                    if c is None or c[0] not in SEA:
                        continue
                    if sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1) if standable(world, x + dx, y + dy)) < need_land:
                        continue
                    far = [(x + d[0] * j + abs(d[1]) * side, y + d[1] * j + abs(d[0]) * side)
                           for j in range(1, reach_out) for side in (0, 1)]
                    if not all((world.get(*p) or WATER)[0] in SEA and (world.get(*p) or WATER)[2] == NONE for p in far):
                        continue
                    score = min(abs(x - int(c_[4])) + abs(y - int(c_[5])) for c_ in captains)
                    if best is None or score < best[0]:
                        best = (score, (x, y), d)
            if best is not None:
                break
        if best is None:
            print('wyspa bez brzegu na przystan:', len(cells), cells[0])
            continue
        _s, spot, d = best
        r = F.small_dock(world, spot, d, pier=3)
        if r is None:
            print('wyspa bez przystani:', len(cells), cells[0])
            continue
        stand, rect = r
        if not standable(world, *stand):
            stand = spot
        key = f'isle{fi}_ferryman'
        name = FERRYMEN[fi % len(FERRYMEN)]
        island = ISLAND_NAMES[fi % len(ISLAND_NAMES)]
        fi += 1
        ctx['pz'].append((*rect, 0))
        ctx['npcs'].append([key, name, 'CAPTAIN', island, stand[0], stand[1], 0, '-', '#4a6a8a',
                            'Przewożę łodzią na stały ląd i z powrotem. Tylko posiadacze konta premium.'])
        near = sorted(captains, key=lambda c_: abs(stand[0] - int(c_[4])) + abs(stand[1] - int(c_[5])))[:2]
        for c_ in near:
            ctx['routes'].append((key, c_[0]))
        stats['przewoznicy'] += 1
        ferry_names[k] = island
        islands.append((k, 'statek'))
        linked.add(k)
    # potwory i regiony wysp (wczesniej puste); klimatyczne spawny omijaja te pola
    island_cells = set()
    ni = 0
    starts = []
    for k, how in islands:
        cells = parts[k]
        island_cells.update(cells)
        tier = island_tier(cells)
        lim, (lo, hi), pool = ISLAND_TIERS[tier]
        xs = [p[0] for p in cells]
        ys = [p[1] for p in cells]
        box = (min(xs), min(ys), max(xs), max(ys))
        sub = lab[box[1]:box[3] + 1, box[0]:box[2] + 1]
        other = int(((sub != 0) & (sub != k)).sum())
        name = ferry_names.get(k) or ISLAND_NAMES[(ni + 13) % len(ISLAND_NAMES)]
        ni += 1
        # prostokat wyspy; gdy obejmuje cudzy lad - kilka prostokatow dokladnie po wyspie
        rects = [box] if other < 40 else [r for r in organic.rect_cover(set(cells))
                                          if (r[2] - r[0] + 1) * (r[3] - r[1] + 1) >= 6]
        for r in rects:
            ctx.setdefault('island_regions', []).append((name, *r, 0, 0, lo, hi))
        z0_rects = [r[1:5] for r in ctx['regions'] + ctx['regions_new'] if len(r) > 5 and r[5] <= 0 <= r[6]]
        free = [p for p in cells if standable(world, *p) and world.get(*p)[0] not in F.ROAD_GROUND
                and any(a <= p[0] <= c_ and b <= p[1] <= d_ for (a, b, c_, d_) in rects)
                and not any(a <= p[0] <= c_ and b <= p[1] <= d_ for (a, b, c_, d_) in z0_rects)]
        if not free:
            continue
        starts.append(free[0])
        rng.shuffle(free)
        want = max(1, min(8, len(cells) // 450))
        placed = []
        for (x, y) in free:
            if len(placed) >= want:
                break
            if any(abs(x - a) + abs(y - b) < 14 for (a, b) in placed):
                continue
            if any(a - 3 <= x <= c_ + 3 and b - 3 <= y <= d_ + 3 for (a, b, c_, d_, z) in ctx['pz'] if z == 0):
                continue
            total = sum(w for _m, w in pool)
            v = rng.random() * total
            for m, w in pool:
                v -= w
                if v <= 0:
                    break
            ctx['spawns'].append(f'{m};{x};{y};0;4;{rng.choice((2, 3))};120')
            placed.append((x, y))
    ctx['island_cells'] = island_cells
    ctx['island_starts'] = starts
    # Lochy Zapomnianych pod najwieksza wyspa bez miasta
    town_parts = {int(lab[p[1], p[0]]) for _n, p in ctx['towns']}
    big = max((k for k, _h in islands if k not in town_parts), key=lambda k: len(parts[k]))
    cells = parts[big]
    cx = sum(c[0] for c in cells) / len(cells)
    cy = sum(c[1] for c in cells) / len(cells)
    entry = min((p for p in cells if standable(world, *p) and standable(world, p[0], p[1] + 1)
                 and all(standable(world, p[0] + dx, p[1] + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))),
                key=lambda p: abs(p[0] - cx) + abs(p[1] - cy))
    name, levels, regs = LOCHY
    dungeon(ctx, name, entry, levels, rng)
    _n, x0, y0, x1, y1, _zl, _zh = ctx['dungeons'][-1]
    for (za, zb, lo, hi) in regs:
        ctx['regions_new'].insert(0, (name if za == -1 else 'Dno Lochów', x0, y0, x1, y1, zb, za, lo, hi))
    # ruiny nad wejsciem: kawalki murow, kosci i pochodnie
    ex, ey = entry
    for (dx, dy, cell) in ((-2, -1, 'broken_wall'), (2, -1, 'broken_wall'), (-2, 1, 'wall_torch'), (2, 1, 'wall_torch')):
        c = world.get(ex + dx, ey + dy)
        if standable(world, ex + dx, ey + dy):
            world.put(ex + dx, ey + dy, 0, (c[0], NONE, cell, NONE))
    ctx['lochy'] = entry
    stats['lochy'] = entry
    return stats



def all_regions(ctx):
    """Kolejnosc ma znaczenie (pierwszy pasujacy region wygrywa): wioski i nowe lochy, regiony klockow
    przeniesione ze starego swiata, krainy kontynentu."""
    out = []
    for r in ctx.get('regions_new', []):
        if len(r) == 5:
            continue
        out.append(r)
    out += ctx['regions']
    out += ctx.get('island_regions', [])
    out += zone_regions(ctx['zones']) if 'zones' in ctx else []
    return out


def snap_spawns(ctx):
    """Srodek spawnu na polu przechodnim: przesuniety na najblizsze wolne pole (do 6 pol), inaczej usuniety."""
    world = ctx['world']
    out, moved, dropped = [], 0, 0
    trans = {(t[0], t[1], t[2]) for t in ctx['transitions']}

    def open_cell(x, y, z):
        c = world.get(x, y, z)
        return F.walk(c) and c[2] not in F.STAIRS and (x, y, z) not in trans

    def good(x, y, z):
        # nie w kieszeni zamknietej schodami (wejscie na schody przenosi pietro nizej)
        return open_cell(x, y, z) and not in_pz(ctx, x, y, z) and \
            any(open_cell(x + a, y + b, z) for a, b in F.DIRS4)
    for line in ctx['spawns']:
        if line.startswith('#'):
            out.append(line)
            continue
        f = line.split(';')
        x, y, z = int(f[1]), int(f[2]), int(f[3])
        best = None
        for r in range(7):
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    if best is None and max(abs(dx), abs(dy)) == r:
                        if good(x + dx, y + dy, z):
                            best = (x + dx, y + dy)
            if best:
                break
        boxes = [t['rect'] for t in ctx['new_towns'] + list(ctx.get('rebuilt', {}).values())]
        if best is not None and z == 0 and any(r[0] <= best[0] <= r[2] and r[1] <= best[1] <= r[3] for r in boxes):
            best = None                    # pod murami nowych miast (region miasta) nie ma spawnow
        if best is None:
            dropped += 1
            continue
        if best != (x, y):
            moved += 1
        f[1], f[2] = str(best[0]), str(best[1])
        out.append(';'.join(f))
    ctx['spawns'] = out
    print('spawny: przesuniete', moved, 'usuniete', dropped)


def fix_transition_targets(ctx):
    """Cel przejscia zastawiony pozniej (lawka z pobocza, drzewo, straznik) - przejscie prowadzi na sasiednie wolne
    pole celu."""
    world = ctx['world']
    npcs = {(int(n[4]), int(n[5]), int(n[6])) for n in ctx['npcs']}
    out, fixed = [], 0
    for (x, y, z, typ, tx, ty, tz) in ctx['transitions']:
        c = world.get(tx, ty, tz)
        if not (F.walk(c) and c[2] not in F.STAIRS and c[2] != 'sewer_grate') or (tx, ty, tz) in npcs:
            for r in (1, 2):
                cand = [(tx + a, ty + b) for a in range(-r, r + 1) for b in range(-r, r + 1) if (a or b)]
                cand = [p for p in cand if F.walk(world.get(p[0], p[1], tz)) and world.get(p[0], p[1], tz)[2] not in F.STAIRS
                        and world.get(p[0], p[1], tz)[2] != 'sewer_grate' and (p[0], p[1], tz) not in npcs]
                if cand:
                    tx, ty = min(cand, key=lambda p: abs(p[0] - tx) + abs(p[1] - ty))
                    fixed += 1
                    break
        out.append((x, y, z, typ, tx, ty, tz))
    ctx['transitions'] = out
    print('przejscia: poprawione cele', fixed)


def finish(ctx):
    shops = {}
    with open(os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources', 'data', 'shops.csv'), encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.strip().split(';')
            if int(p[2]) > 0:
                shops.setdefault(p[0], []).append(p[1])
    vb = next(bb for bb in ctx['blocks'] if bb.name == 'varden')
    vr = (vb.rect[0] + vb.dx, vb.rect[1] + vb.dy, vb.rect[2] + vb.dx, vb.rect[3] + vb.dy)
    core = set(ctx['varden_core'])
    ctx['displays'] = nature.shop_displays(ctx, shops, protect=lambda x, y: (x, y) in core or (
        vr[0] <= x <= vr[2] and vr[1] <= y <= vr[3]))
    print('towary na ladach:', len(ctx['displays']))
    snap_spawns(ctx)
    fix_transition_targets(ctx)
    temple = dict(ctx['towns'])['Varden']
    props = [
        ('spawn', f'{temple[0]},{temple[1]},0'),
        ('pz', ';'.join(','.join(str(v) for v in r) for r in ctx['pz'])),
        ('# przejscia strzezone: x0,y0,x1,y1,z,minimalny poziom', None),
        ('gates', ';'.join(','.join(str(v) for v in g) for g in ctx['gates'])),
        ('# miasta: nazwa:x,y,z swiatyni (maks. 6)', None),
        ('towns', ';'.join(f'{n}:{p[0]},{p[1]},{p[2]}' for n, p in ctx['towns'])),
        ('# regiony: nazwa;x0;y0;x1;y1;z od;z do;zalecany poziom od;do (oddzielone |)', None),
        ('regions', '|'.join(';'.join(str(v) for v in r) for r in all_regions(ctx))),
    ]
    print('swiatla na murach:', wall_lights.mount(ctx['world'], S, FLOORS))
    write_world(ctx['world'], ctx['legacy'], props, ctx['transitions'], ctx['npcs'], ctx['spawns'], ctx['houses'],
                ctx['routes'])
    with open(os.path.join(OUT, 'displays.csv'), 'w', encoding='utf-8') as f:
        f.write('# towary wylozone na ladach sklepow (tylko do ogladania): x;y;z;przedmiot\n')
        for d in ctx['displays'] + ctx.get('quest_displays', []):
            f.write(';'.join(str(v) for v in d) + '\n')
    with open(os.path.join(OUT, 'level_doors.csv'), 'w', encoding='utf-8') as f:
        f.write('# drzwi poziomu: x;y;z;minimalny poziom (pole z drzwiami; dotkniecie pokazuje napis)\n')
        for d in ctx.get('level_doors', []):
            f.write(';'.join(str(v) for v in d) + '\n')
    with open(os.path.join(OUT, 'altar_quests.csv'), 'w', encoding='utf-8') as f:
        f.write('# zadania z oltarzami profesji (world/AltarQuest):\n'
                '# quest;id;nazwa;zloto;dzwignia x,y,z;sala x0,y0,x1,y1,z;skarbiec x0,y0,x1,y1,z\n'
                '# altar;id;PROFESJA;oltarz x,y,z;pole x,y,z;pochodnia x,y,z;przedmiot;cel x,y,z;skrzynia x,y,z\n')
        for line in ctx.get('altar_quests', []):
            f.write(line + '\n')
    with open(os.path.join(OUT, 'boss_quests.csv'), 'w', encoding='utf-8') as f:
        f.write('# wyprawy na bossa w piec postaci (world/BossQuest, game/BossQuestRuntime):\n'
                '# quest;id;nazwa;poziom;boss;dzwignia x,y,z;przedsionek x0,y0,x1,y1,z;arena x0,y0,x1,y1,z;'
                'boss x,y,z;portal wyjscia x,y,z;cel wyjscia x,y,z;drzwi poziomu x,y,z\n'
                '# square;id;pole x,y,z;cel teleportu na arenie x,y,z (piec pol na wyprawe)\n')
        for line in ctx.get('boss_quests', []):
            f.write(line + '\n')


if __name__ == '__main__':
    c = main()
    build(c)
    print('unplaced npcs:', sorted(c['unplaced']))
    print('dropped spawns:', len(c['dropped']))
    finish(c)
