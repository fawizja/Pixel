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
    ('varden', 'full', (46, 64, 157, 111), (137, 340)),
    ('frostholm', 'full', (60, 4, 100, 47), (100, 96)),
    ('selvaran', 'full', [(98, 112, 159, 176), (111, 177, 159, 181)], (318, 94)),
    ('qasirah', 'full', (54, 177, 110, 222), (189, 228)),
    ('pyramid', 'full', (113, 198, 136, 216), (276, 280)),
    ('ruins', 'full', (219, 195, 241, 215), (300, 270)),
    ('camp', 'full', (170, 78, 188, 95), (282, 238)),
    ('wyvern', 'full', (12, 101, 32, 111), (158, 184)),
    ('coral', 'island', (95, 244, 146, 283), (232, 404)),
    ('gull', 'island', (184, 242, 197, 256), (48, 44)),
    ('lighthouse', 'island', (276, 103, 298, 119), (62, 212)),
    ('pirate', 'island', (26, 255, 70, 300), (8, 396)),
    ('mist', 'island', (222, 255, 280, 304), (334, 330)),
    ('ice', 'island', (274, 18, 336, 58), (316, 6)),
    ('volcano_isle', 'island', (279, 168, 337, 212), (388, 208)),
    ('ice_caves', 'entrance', (20, 30, 20, 30), (124, 152)),
    ('jungle_ruins', 'entrance', (60, 150, 60, 150), (300, 214)),
    ('abyss', 'entrance', (196, 216, 196, 216), (318, 300)),
    ('turnie', 'entrance', (36, 10, 36, 10), (86, 74)),
    ('szczyty', 'entrance', (214, 30, 214, 30), (64, 120)),
    ('mesa', 'entrance', (30, 200, 30, 200), (250, 206)),
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
NATURAL_DECO = {NONE, 'tall_grass', 'flowers', 'snow_tuft', 'fern', 'dry_grass', 'shells', 'bones'}
NATURAL_OBJ = {NONE, 'tree', 'pine', 'palm', 'bush', 'rock', 'cactus', 'snow_rock', 'sandstone_rock', 'dead_tree',
               'obsidian_rock'}
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
    ctx = dict(legacy=legacy, blocks=blocks, T=T, world=world, remap=remap, transitions=transitions, npcs=npcs,
               unplaced=unplaced, spawns=spawns, dropped=dropped, houses=houses, pz=pz, gates=gates, towns=towns,
               regions=regions, routes=routes, footprint=footprint)
    return ctx


# nowe przystanie z duzymi statkami: klucz kapitana, imie, miasto, punkt na ladzie, kierunek do wody
HARBOURS = [
    ('varden_captain', 'Kapitan Edmund', 'Varden', (170, 389), F.S_),
    ('qas_captain', None, 'Qasirah', (150, 214), F.W),
    ('east_captain', None, 'Przystań Wschodnia', (380, 252), F.E),
]
NEW_ROUTES = [('varden_captain', 'selv_captain'), ('varden_captain', 'qas_captain'),
              ('varden_captain', 'pirate_captain'), ('varden_captain', 'coral_captain'),
              ('varden_captain', 'east_captain')]
# drogi: (skad, dokad) - pola tuz za bramami miast i przy celach; kolejne drogi korzystaja z poprzednich
ROADS = [
    ((170, 339), (214, 274)),        # Varden (polnoc) -> Qasirah (poludnie)
    ((215, 227), (119, 140)),        # Qasirah (polnoc) -> Frostholm (poludnie)
    ((246, 249), (317, 122)),        # Qasirah (wschod) -> Selvaran (zachod), przelecz w grzbiecie
    ((237, 339), (282, 279)),        # Varden (dzielnica krolewska) -> piramida
    ((282, 279), (290, 256)),        # piramida -> oboz
    ((99, 123), (66, 122)),          # Frostholm (zachod) -> Lodowe Szczyty
    ((119, 140), (124, 154)),        # Frostholm -> Lodowe Jaskinie
    ((99, 123), (86, 77)),           # Frostholm -> Mrozne Turnie
    ((215, 227), (250, 208)),        # Qasirah -> Plaskowyz
    ((170, 388), (170, 392)),        # Varden (poludnie) -> przystan
    ((215, 227), (151, 214)),        # Qasirah -> przystan Qasirah
    ((379, 252), (317, 122)),        # Przystan Wschodnia -> Selvaran
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
    ctx['routes'] += NEW_ROUTES
    ferries(ctx)
    bridges = []
    for a, b in ROADS:
        br, _path = F.connect(world, T, cost, a, b)
        bridges += br
    ctx['bridges'] = bridges
    ctx['regions_new'] = []
    rng = random.Random(20261035)
    foot = volcano(ctx, cost, rng)
    F.connect(world, T, cost, foot, nearest_road(world, cost, foot, (foot[0], foot[1], foot[0], foot[1]), limit=120))
    villages(ctx, cost)
    caves(ctx, rng)
    for (yr, xr, kind) in (((130, 160), (258, 300), 'rock'), ((228, 256), (330, 372), 'river'),
                           ((290, 320), (160, 200), 'river')):
        cr = find_crossing(world, T, yr[0], yr[1], xr[0], xr[1], kind)
        if cr is not None:
            sky_bridge(ctx, cr[1], cr[2], chasm=(kind == 'rock'), deck='rope_bridge' if kind == 'rock' else 'bridge')
            ctx.setdefault('sky_bridges', []).append((cr[1], cr[2], kind))
    small_docks(ctx)
    # kazde wejscie na powierzchni osiagalne pieszo z Varden (inaczej sciezka do najblizszej drogi)
    temple = dict(ctx['towns'])['Varden']
    island_blocks = [b for b in ctx['blocks'] if b.kind == 'island']
    for _ in range(3):
        reach = reach_surface(world, temple[:2])
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
    restore_varden_core(ctx)
    street_sides(ctx)
    zones = zone_grid(T)
    ctx['zones'] = zones
    climate_spawns(ctx, zones, rng)


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
    ('Wierzbowo', 'living', (150, 300), []),
    ('Mroźna Osada', 'living', (168, 112), []),
    ('Popielisko', 'deserted', (138, 178), []),
    ('Suche Studnie', 'deserted', (262, 304), []),
    ('Kamienny Bród', 'occupied', (120, 250), [('orc_warrior', 4, 3), ('orc_shaman', 3, 2)]),
    ('Wilczy Jar', 'occupied', (238, 168), [('orc_berserker', 4, 3), ('orc_warrior', 4, 2)]),
    ('Czerwony Kurhan', 'occupied', (226, 300), [('orc_warrior', 4, 3), ('orc_warlord', 2, 1)]),
    ('Zielony Gaj', 'occupied', (150, 262), [('goblin', 4, 4), ('goblin_shaman', 3, 2)]),
    ('Rozstaje', 'occupied', (140, 206), [('bandit', 4, 4)]),
    ('Martwa Wieś', 'occupied', (92, 366), [('skeleton', 4, 4), ('wraith_knight', 3, 1)]),
    ('Trollowe Leże', 'occupied', (196, 142), [('cave_troll', 4, 3)]),
    ('Bagienna Osada', 'occupied', (346, 216), [('jungle_hunter', 4, 3), ('crocodile', 4, 2)]),
]
VILLAGE_NPCS = {
    'Wierzbowo': ('wierzbowo_merchant', 'Sołtys Bogumił', 'Witaj w Wierzbowie. Chleb, mięso i pochodnie dla wędrowców.'),
    'Mroźna Osada': ('osada_merchant', 'Handlarka Jagna', 'Ciepła strawa i liny na drogę w góry.'),
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
        g = F.carve_cave(W, H, pts, style, rng, lava=lava)
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
        for j, (monster, radius, count) in enumerate(spawns):
            pts_world = [(tx + px, ty + py) for (px, py) in pts[1:-1]] or [(tx + pts[-1][0], ty + pts[-1][1])]
            want = pts_world[j % len(pts_world)]
            sx, sy = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]) + (j * 13 % 5))
            ctx['spawns'].append(f'{monster};{sx};{sy};{z};{radius};{count};180')
    ctx.setdefault('dungeons', []).append((name, tx, ty, tx + W - 1, ty + H - 1, -len(levels), -1))
    return tx, ty


def volcano(ctx, cost, rng):
    """Wulkan: stozek z lawowymi strugami, polka (+1) na zboczu i krater z jeziorem lawy na szczycie (+2),
    wnetrze wulkanu (-1..-3) z ogniowymi potworami. Zwraca punkt u stop (do drogi)."""
    world = ctx['world']
    vx, vy = C.VOLCANO
    n = C.fbm(401, 2, 5)
    streams = [math.radians(a) for a in (35, 150, 260)]
    for y in range(vy - 22, vy + 23):
        for x in range(vx - 22, vx + 23):
            d = math.hypot(x - vx, y - vy) + 1.2 * n[y, x]
            if d > 21:
                continue
            ang = math.atan2(y - vy, x - vx)
            if d < 7:
                cell = ('lava', NONE, NONE, NONE)
            elif d <= 11:
                cell = ('ash', NONE, NONE, 'mountain')
            else:
                in_stream = any(abs((ang - a + math.pi) % (2 * math.pi) - math.pi) * d < 0.9 for a in streams) and d < 18
                if in_stream:
                    cell = ('lava', NONE, NONE, NONE)
                else:
                    v = rng.random()
                    cell = ('ash', NONE, 'obsidian_rock' if v < 0.05 else ('dead_tree' if v < 0.07 else NONE), NONE)
            world.put(x, y, 0, cell)
            cost[y, x] = 30.0 if cell[3] != NONE or cell[0] == 'lava' else 1.0
            if cell[0] == 'lava':
                cost[y, x] = np.inf
    # polka (+1) i krawedz krateru (+2)
    for y in range(vy - 14, vy + 15):
        for x in range(vx - 14, vx + 15):
            d = math.hypot(x - vx, y - vy)
            if 9 <= d <= 12.4:
                world.put(x, y, 1, ('ash', NONE, 'obsidian_rock' if rng.random() < 0.04 and d > 10 else NONE, NONE))
            if 6 <= d <= 9.4:
                world.put(x, y, 2, ('ash', NONE, 'magma' if rng.random() < 0.05 and d > 8.6 else NONE, NONE))
            elif d < 6:
                world.put(x, y, 2, ('lava', NONE, NONE, NONE))
    # schody: zbocze (poludnie) -> polka -> krawedz krateru (polnoc)
    t = ctx['transitions']
    world.put(vx, vy + 12, 0, ('ash', NONE, 'stairs_up', NONE))
    world.put(vx, vy + 13, 0, ('ash', NONE, NONE, NONE))
    world.put(vx, vy + 12, 1, ('ash', NONE, 'stairs_down', NONE))
    world.put(vx, vy + 11, 1, ('ash', NONE, NONE, NONE))
    t += [(vx, vy + 12, 0, 'STAIRS', vx, vy + 11, 1), (vx, vy + 12, 1, 'STAIRS', vx, vy + 13, 0)]
    world.put(vx, vy - 10, 1, ('ash', NONE, 'stairs_up', NONE))
    world.put(vx, vy - 11, 1, ('ash', NONE, NONE, NONE))
    world.put(vx, vy - 9, 2, ('ash', NONE, 'stairs_down', NONE))
    world.put(vx, vy - 8, 2, ('ash', NONE, NONE, NONE))
    t += [(vx, vy - 10, 1, 'STAIRS', vx, vy - 8, 2), (vx, vy - 9, 2, 'STAIRS', vx, vy - 11, 1)]
    for yy in range(vy + 13, vy + 22):
        for xx in (vx - 1, vx, vx + 1):
            c = world.get(xx, yy)
            if c[0] == 'lava' or c[3] != NONE or c[2] != NONE:
                world.put(xx, yy, 0, ('ash', NONE, NONE, NONE))
            cost[yy, xx] = 1.0
    sp = ctx['spawns']
    sp += ['# Wulkan: zbocza, polka i krater (Ignaroth na szczycie)',
           f'fire_elemental;{vx - 15};{vy + 4};0;3;2;240', f'lava_golem;{vx + 15};{vy - 3};0;3;2;300',
           f'fire_elemental;{vx + 11};{vy + 3};1;2;2;240', f'fire_elemental;{vx - 11};{vy - 3};1;2;2;240',
           f'ignaroth;{vx + 7};{vy};2;1;1;3600']
    # wnetrze wulkanu: wejscie u wschodniej stopy
    entry = (vx + 16, vy + 8)
    world.put(entry[0], entry[1], 0, ('ash', NONE, NONE, NONE))
    dungeon(ctx, 'Wnetrze Wulkanu', entry, [
        (44, 34, [(4, 4), (16, 10), (30, 6), (38, 20), (24, 26)], 'fire', 0.06,
         [('fire_elemental', 4, 3), ('fire_elemental', 4, 2), ('lava_golem', 3, 2)]),
        (44, 34, [(24, 26), (10, 22), (8, 8), (26, 14), (40, 6)], 'fire', 0.08,
         [('lava_golem', 4, 3), ('fire_elemental', 4, 3), ('magma_drake', 3, 1)]),
        (44, 34, [(40, 6), (36, 22), (20, 28), (8, 16)], 'core', 0.1,
         [('magma_drake', 4, 2), ('lava_golem', 4, 2), ('magma_drake', 3, 2)]),
    ], rng, surface_ground='ash')
    ctx['regions_new'] = [('Szczyt Wulkanu', vx - 14, vy - 14, vx + 14, vy + 14, 1, 2, 340, 420)] + ctx['regions_new']
    _n, x0, y0, x1, y1, zl, zh = ctx['dungeons'][-1]
    lv = [MONSTER_LEVEL[m] for m in ('fire_elemental', 'lava_golem', 'magma_drake')]
    ctx['regions_new'].insert(0, ('Wnetrze Wulkanu', x0, y0, x1, y1, zl, zh, min(lv), max(lv) + 10))
    return (vx, vy + 21)


# ------------------------------------------------------------------ krainy (regiony) i spawny wg klimatu
# kraina: (poziom od, do, potwory dzienne [(klucz, waga)], potwory nocne)
ZONES = {
    'Krainy Varden': (8, 60, [('wolf', 3), ('boar', 3), ('forest_spider', 2), ('bandit', 2), ('goblin', 2),
                              ('goblin_shaman', 1), ('skeleton', 1)], ['wolf']),
    'Wzgorza Qasirah': (20, 70, [('bandit', 2), ('goblin_shaman', 2), ('skeleton', 2), ('cave_troll', 2)], ['wolf']),
    'Mrozne Pustkowia': (40, 90, [('snow_wolf', 3), ('yeti', 2), ('frost_witch', 1)], ['snow_wolf']),
    'Lodowe Szczyty': (400, 560, [('frost_giant', 3), ('storm_harpy', 2), ('ice_dragon', 1)], []),
    'Spalone Wyzyny': (300, 400, [('fire_elemental', 3), ('lava_golem', 2)], []),
    'Dzungla Selvaran': (55, 80, [('crocodile', 2), ('jungle_hunter', 3), ('jungle_serpent', 2)], []),
    'Pustynia Qasirah': (45, 130, [('hyena', 3), ('scorpion', 3), ('sand_wraith', 1)], ['hyena', 'sand_wraith']),
    'Pustkowia Cienia': (550, 750, [('shadow_stalker', 3), ('sand_titan', 2), ('void_priest', 2)], ['shadow_stalker']),
    'Przeklete Knieje': (230, 300, [('cursed_treant', 3), ('wraith_knight', 2)], ['wraith_knight']),
}
BIOME_ZONE = {'snow': 'Mrozne Pustkowia', 'taiga': 'Mrozne Pustkowia', 'temperate': 'Krainy Varden',
              'forest': 'Krainy Varden', 'hills': 'Wzgorza Qasirah', 'cursed': 'Przeklete Knieje',
              'volcanic': 'Spalone Wyzyny', 'jungle': 'Dzungla Selvaran', 'swamp': 'Dzungla Selvaran',
              'desert': 'Pustynia Qasirah', 'savanna': 'Pustynia Qasirah', 'shadow': 'Pustkowia Cienia'}
ZONE_CELL = 16


def zone_grid(T):
    """Kraina kazdego pola 16 x 16 wg przewazajacego biomu ladu (zachodni snieg to Lodowe Szczyty)."""
    n = S // ZONE_CELL
    out = {}
    for cy in range(n):
        for cx in range(n):
            votes = {}
            for y in range(cy * ZONE_CELL, (cy + 1) * ZONE_CELL, 2):
                for x in range(cx * ZONE_CELL, (cx + 1) * ZONE_CELL, 2):
                    if T.land[y, x]:
                        z = BIOME_ZONE.get(T.biome[y, x], 'Krainy Varden')
                        if z == 'Mrozne Pustkowia' and x < 100 and y < 168:
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
    reach = reach_surface(world, temples[0][:2])
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
            if not (0 <= x < S and 0 <= y < S) or not reach[y, x] or ctx['footprint'][y, x]:
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
            count = 3 if rng.random() < 0.5 else 2
            out.append(f'{key};{x};{y};0;{rng.choice((4, 5))};{count};{rng.choice((90, 120, 150))}')
            taken.append((x, y))
            if night and night_left.get(zone, 0) < 3 and rng.random() < 0.35:
                night_left[zone] = night_left.get(zone, 0) + 1
                out.append(f'{rng.choice(night)};{x};{y};0;5;2;120;noc')
    ctx['spawns'] += out


# ------------------------------------------------------------------ jaskinie w gorach, mosty wiszace, przystanie

CAVES = [
    # nazwa, okolica wejscia, poziomy (szer, wys, punkty, styl, lawa, spawny), poziomy regionu
    ('Grota Pajakow', (226, 318), [
        (34, 26, [(4, 4), (14, 12), (28, 6), (26, 20)], 'moss', 0, [('forest_spider', 4, 3), ('forest_spider', 3, 2)]),
        (34, 26, [(26, 20), (10, 18), (6, 6), (24, 8)], 'moss', 0, [('forest_spider', 4, 3), ('skeleton', 3, 2)]),
    ], (10, 40)),
    ('Jaskinia Trolli', (176, 156), [
        (36, 28, [(4, 4), (16, 14), (30, 6), (30, 22)], 'rock', 0, [('cave_troll', 4, 3), ('cave_troll', 4, 2)]),
        (36, 28, [(30, 22), (12, 22), (6, 8), (22, 10)], 'rock', 0, [('cave_troll', 4, 3), ('crystal_golem', 3, 2)]),
    ], (28, 60)),
    ('Orcza Kopalnia', (270, 126), [
        (40, 30, [(4, 4), (18, 8), (34, 6), (30, 20), (14, 24)], 'orc', 0,
         [('orc_warrior', 4, 3), ('orc_shaman', 4, 2), ('orc_warrior', 3, 2)]),
        (40, 30, [(14, 24), (6, 12), (20, 6), (34, 14), (30, 26)], 'orc', 0,
         [('orc_berserker', 4, 3), ('orc_shaman', 4, 2), ('orc_warlord', 2, 1)]),
    ], (100, 230)),
    ('Lodowa Grota', (128, 80), [
        (34, 26, [(4, 4), (16, 12), (28, 6), (26, 20)], 'ice', 0, [('yeti', 4, 3), ('frost_witch', 3, 2)]),
        (34, 26, [(26, 20), (10, 20), (6, 6), (22, 10)], 'ice', 0, [('ice_golem', 4, 2), ('frost_witch', 3, 2)]),
    ], (60, 130)),
    ('Grota Przemytnikow', (252, 322), [
        (30, 22, [(4, 4), (14, 10), (24, 16), (8, 16)], 'rock', 0, [('bandit', 4, 3), ('skeleton', 3, 2)]),
    ], (12, 40)),
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
        ex, ey = mountain_entrance(world, ctx['footprint'], near)
        ground = world.get(ex, ey + 1)[0]
        tx, ty = dungeon(ctx, name, (ex, ey), levels, rng, surface_ground=ground)
        nm, x0, y0, x1, y1, zl, zh = ctx['dungeons'][-1]
        ctx['regions_new'].insert(0, (name, x0, y0, x1, y1, zl, zh, lo, hi))
        ctx.setdefault('entrances', []).append((name, (ex, ey + 1)))


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
    spots = [((96, 236), F.W), ((70, 344), F.S_), ((330, 300), F.E), ((150, 30), F.S_), ((428, 120), F.W),
             ((420, 360), F.W), ((120, 432), F.N), ((26, 300), F.E), ((300, 336), F.S_)]
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
               'Dobromir', 'Jarogniew', 'Ziemowit', 'Bogusław', 'Przemysław', 'Siemowit', 'Wszebor', 'Czcibor']
GUARD_RANKS = ['Strażnik', 'Strażniczka', 'Halabardnik', 'Kusznik']
GUARD_FEMALE = ['Dobrosława', 'Wiesława', 'Jaromira', 'Bogna', 'Świętosława', 'Zdzisława']
# bramy miast: punkt tuz za brama (z ROADS) i miasto
GATES = [((170, 339), 'Varden'), ((237, 339), 'Varden'), ((170, 388), 'Varden'), ((215, 227), 'Qasirah'),
         ((246, 249), 'Qasirah'), ((99, 123), 'Frostholm'), ((119, 140), 'Frostholm'), ((317, 122), 'Selvaran')]
TOWN_BLOCKS = {'Varden': 'varden', 'Frostholm': 'frostholm', 'Qasirah': 'qasirah', 'Selvaran': 'selvaran'}
TOWN_GREEN = {'Varden': (['tree', 'bush'], ['flowers', 'tall_grass']),
              'Frostholm': (['pine', 'snow_rock'], ['snow_tuft']),
              'Qasirah': (['palm', 'cactus', 'bush'], ['dry_grass', 'flowers']),
              'Selvaran': (['tree', 'palm', 'bush'], ['fern', 'flowers'])}


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


def towns_dressing(ctx):
    world = ctx['world']
    rng = random.Random(20261036)
    stats = {}
    towns = dict(ctx['towns'])
    # swiatynie poza Varden (Varden ma swiece z generate_varden.py, a jego rdzen zostaje 1:1)
    for name in ('Frostholm', 'Qasirah', 'Selvaran'):
        tx, ty, _ = towns[name]
        altars = [(x, y) for y in range(ty - 12, ty + 13) for x in range(tx - 12, tx + 13)
                  if (world.get(x, y) or WATER)[2] == 'altar']
        assert altars, f'no altar in the temple of {name}'
        altars.sort(key=lambda p: abs(p[0] - tx) + abs(p[1] - ty))
        stats[name] = dress_temple(world, altars[0], rng)
    # sala tronowa krola Aureliana (dzielnica krolewska Varden): oltarz -> tron
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
    for (gx, gy), town in GATES:
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
            [(200, 222), (118, 318)]:
        p = open_spot(world, ctx, (near[0] + 8, near[1] - 4), radius=24)
        if p is not None:
            c = world.get(*p)
            world.put(p[0], p[1], 0, (c[0], NONE, 'windmill', c[3]))
            mills.append(p)
    ctx['windmills'] = mills
    # zielen w miastach (poza rdzeniem Varden): drzewa tylko na polach z wolnymi 8 sasiadami (nigdy obok siebie)
    green = 0
    for town, bname in TOWN_BLOCKS.items():
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


def all_regions(ctx):
    """Kolejnosc ma znaczenie (pierwszy pasujacy region wygrywa): wioski i nowe lochy, regiony klockow
    przeniesione ze starego swiata, krainy kontynentu."""
    out = []
    for r in ctx.get('regions_new', []):
        if len(r) == 5:
            continue
        out.append(r)
    out += ctx['regions']
    out += zone_regions(ctx['zones']) if 'zones' in ctx else []
    return out


def finish(ctx):
    temple = dict(ctx['towns'])['Varden']
    props = [
        ('spawn', f'{temple[0]},{temple[1]},0'),
        ('pz', ';'.join(','.join(str(v) for v in r) for r in ctx['pz'])),
        ('# przejscia strzezone: x0,y0,x1,y1,z,minimalny poziom', None),
        ('gates', ';'.join(','.join(str(v) for v in g) for g in ctx['gates'])),
        ('# miasta: nazwa:x,y,z swiatyni (maks. 4)', None),
        ('towns', ';'.join(f'{n}:{p[0]},{p[1]},{p[2]}' for n, p in ctx['towns'])),
        ('# regiony: nazwa;x0;y0;x1;y1;z od;z do;zalecany poziom od;do (oddzielone |)', None),
        ('regions', '|'.join(';'.join(str(v) for v in r) for r in all_regions(ctx))),
    ]
    write_world(ctx['world'], ctx['legacy'], props, ctx['transitions'], ctx['npcs'], ctx['spawns'], ctx['houses'],
                ctx['routes'])


if __name__ == '__main__':
    c = main()
    build(c)
    print('unplaced npcs:', sorted(c['unplaced']))
    print('dropped spawns:', len(c['dropped']))
    finish(c)
