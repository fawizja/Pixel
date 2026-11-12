"""Miasto Rogatych na -7 (Darek 2026-11-12): "dodaj jak w Tibii miasto minotaurow, nie kwadratowe tylko losowego ksztaltu,
na -7 gdzies na mapie, z potworami na poziomy 20-100".

Pod kontynentem glownym (wolny lad, z dala od miast, zamku i wzgorz) lezy podziemne miasto rogaczy:
  * dwa zejscia z powierzchni (jaskinie w skalnych wystepach); kazde to kreta jaskinia przez szesc pieter (-1..-6):
    na kazdym pietrze jeden wijacy sie chodnik z komorami, slepymi odnogami i (co drugie) skrzynia; ze schodow na -6
    wchodzi sie do miasta;
  * miasto (-7) o nieregularnym obrysie (kilka zachodzacych pagorkow jak w hills.py): arena w centrum (piasek, mur z
    bramami, trybuny z posagami), za nia sala tronowa (swiatynia) z Grozdarem, Panem Labiryntu, i skrzynia zadania;
    dookola pierscien i promieniste ulice (cobblestone) z kretymi przebiegami, place miedzy kamienicami, nieregularne
    kamienne budynki (L, T, sciete rogi, wciecia) z drzwiami na ulice, slepe zauleki i boczne jaskinie w zakatkach;
  * potwory: rogacze od slabych (zwiadowca, rozbojnik, lucznik) przy wejsciach, przez strazników, mistykow i saperow, do
    gladiatorow i wyroczni w sercu miasta oraz mistrzow areny w centrum i w glebokich jaskiniach; poziomy 20-100
    (boss 100). Kolejnosc wg drogi od wejsc, zeby route_levels niczego nie zmieniala.
Trzy plany (dwie jaskinie i miasto) kladzione osobno przez structures.stamp (kazdy znajduje wolne miejsce na pietrach),
a schody miedzy planami i na powierzchni laczy ``link``. Sprawdza MinotaurCityTest.
"""
import math
import random
from collections import deque

import numpy as np

import features as F
import hills as H
import isles as I
import structures as St

NONE = '-'
SEED = 20261131
CITY = 'Miasto Rogatych'
CAVE = 'Labirynt Rogatych'
CITY_LEVELS = (20, 100)
CAVE_LEVELS = (20, 32)
CITY_Z = -7
SHAPE = dict(L=62, R=30, n=5)
MOUTH_NEAR = (480, 390)       # preferowane miejsce pierwszego wejscia (powierzchnia)

F.CAVE_STYLES.setdefault('horn_cave', ('cave_floor', 'cave_wall', ['bones', 'debris'], ['rock', 'rubble', 'barrel'],
                                       'wall_torch'))

# rogacze wg poziomu: od najslabszego (obrzeza, wejscia) do najsilniejszego (centrum, glebiny)
LADDER = ['horn_scout', 'horn_raider', 'horn_bowman', 'horn_guard', 'horn_mystic', 'horn_sapper', 'horn_gladiator',
          'horn_oracle', 'horn_champion']
BOSS = 'horn_overlord'
# jaskinie zejsciowe: poziomy rosna z pietrem i nie przekraczaja najslabszych potworow miasta (route_levels)
CAVE_MONSTERS = {1: ['horn_scout', 'horn_scout', 'horn_raider'], 2: ['horn_scout', 'horn_raider', 'horn_raider'],
                 3: ['horn_raider', 'horn_scout', 'horn_bowman'], 4: ['horn_raider', 'horn_bowman', 'horn_raider'],
                 5: ['horn_bowman', 'horn_raider', 'horn_bowman'], 6: ['horn_bowman', 'horn_raider', 'horn_bowman']}
CAVE_CHESTS = {2: ('health_potion', 4), 4: ('strong_health_potion', 2), 6: ('strong_mana_potion', 2)}
CITY_CHESTS = [('great_health_potion', 2), ('great_mana_potion', 2), ('strong_health_potion', 3), ('guardian_gem', 1),
               ('great_spirit_potion', 2), ('sage_gem', 1)]
QUEST_CHEST = (4800, 'obsidian_armor', 1)
SOLID = ('housewall', 'building', 'arena', 'arenawall', 'seats', 'hall', 'hallwall', 'gate', 'door')

DIRS4 = F.DIRS4


class Retry(Exception):
    pass


# ====================================================================== geometria na siatce
def dist_field(mask, limit=40):
    """Odleglosc (przyblizona euklidesowa) od najblizszego pola maski (0 na masce); dwa przebiegi chamfer."""
    h, w = mask.shape
    d = np.where(mask, 0.0, 1e9)
    s2 = math.sqrt(2)
    for y in range(h):
        for x in range(w):
            v = d[y, x]
            if x and d[y, x - 1] + 1 < v:
                v = d[y, x - 1] + 1
            if y:
                if d[y - 1, x] + 1 < v:
                    v = d[y - 1, x] + 1
                if x and d[y - 1, x - 1] + s2 < v:
                    v = d[y - 1, x - 1] + s2
                if x + 1 < w and d[y - 1, x + 1] + s2 < v:
                    v = d[y - 1, x + 1] + s2
            d[y, x] = v
    for y in range(h - 1, -1, -1):
        for x in range(w - 1, -1, -1):
            v = d[y, x]
            if x + 1 < w and d[y, x + 1] + 1 < v:
                v = d[y, x + 1] + 1
            if y + 1 < h:
                if d[y + 1, x] + 1 < v:
                    v = d[y + 1, x] + 1
                if x + 1 < w and d[y + 1, x + 1] + s2 < v:
                    v = d[y + 1, x + 1] + s2
                if x and d[y + 1, x - 1] + s2 < v:
                    v = d[y + 1, x - 1] + s2
            d[y, x] = v
    return np.minimum(d, limit)


def polyline(pts, step=0.5):
    """Pola (x, y) wzdluz lamanej o wierzcholkach float."""
    out = []
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(bx - ax, by - ay) / step))
        for i in range(n + 1):
            t = i / n
            out.append((int(round(ax + (bx - ax) * t)), int(round(ay + (by - ay) * t))))
    return out


def brush(cells, width):
    """Pogrubia sciezke: 3 - kwadrat 3x3, 2 - kwadrat 2x2, 1 - same pola."""
    out = set()
    for (x, y) in cells:
        if width >= 3:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    out.add((x + dx, y + dy))
        elif width == 2:
            for dx in (0, 1):
                for dy in (0, 1):
                    out.add((x + dx, y + dy))
        else:
            out.add((x, y))
    return out


def wander(a, b, rng, n=3, amp=5.0, ok=None):
    """Kreta lamana od a do b: n punktow posrednich przesunietych w bok (tylko tam, gdzie ok(x, y))."""
    pts = [a]
    dx, dy = b[0] - a[0], b[1] - a[1]
    d = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / d, dx / d
    for i in range(1, n + 1):
        t = i / (n + 1)
        base = (a[0] + dx * t, a[1] + dy * t)
        for shrink in (1.0, 0.6, 0.3, 0.0):
            off = rng.uniform(-amp, amp) * shrink
            q = (base[0] + nx * off, base[1] + ny * off)
            if ok is None or ok(int(round(q[0])), int(round(q[1]))):
                pts.append(q)
                break
        else:
            pts.append(base)
    pts.append(b)
    return pts


def ang_diff(a, b):
    return abs((a - b + math.pi) % (2 * math.pi) - math.pi)


# ====================================================================== miasto (-7)
def _shape(rng):
    """Obrys kamienicy: zbior pol o nieregularnym ksztalcie (prostokat z wycieciem, sciete rogi, wciecie, dwa prostokaty)."""
    w, h = rng.randint(5, 8), rng.randint(5, 7)
    cells = {(x, y) for x in range(w) for y in range(h)}
    r = rng.random()
    if r < 0.30:                                     # L
        cw, ch = rng.randint(2, w - 3), rng.randint(2, h - 3)
        sx, sy = rng.choice((0, w - cw)), rng.choice((0, h - ch))
        cells -= {(x, y) for x in range(sx, sx + cw) for y in range(sy, sy + ch)}
    elif r < 0.55:                                   # sciete rogi
        for (cx, cy, ex, ey) in rng.sample([(0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)], 2):
            k = rng.randint(1, 2)
            for i in range(k + 1):
                for j in range(k + 1 - i):
                    cells.discard((cx + ex * i, cy + ey * j))
    elif r < 0.75:                                   # wciecie w boku
        cw, ch = rng.randint(2, 3), rng.randint(2, 3)
        mx, my = w // 2 - 1, h // 2 - 1
        side = rng.choice('NSEW')
        if side == 'N':
            cells -= {(x, y) for x in range(mx, mx + cw) for y in range(0, ch)}
        elif side == 'S':
            cells -= {(x, y) for x in range(mx, mx + cw) for y in range(h - ch, h)}
        elif side == 'W':
            cells -= {(x, y) for x in range(0, cw) for y in range(my, my + ch)}
        else:
            cells -= {(x, y) for x in range(w - cw, w) for y in range(my, my + ch)}
    else:                                            # dwa prostokaty (T)
        w2, h2 = rng.randint(4, 6), rng.randint(3, 5)
        ox = rng.randint(-2, 2) + (w - w2) // 2
        cells |= {(x + ox, y + h - 1) for x in range(w2) for y in range(h2)}
        mn = min(c[0] for c in cells)
        cells = {(x - mn, y) for (x, y) in cells}
    return cells


def _walls_of(cells):
    return {(x, y) for (x, y) in cells if any((x + dx, y + dy) not in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1))}


class City:
    """Plan miasta w indeksach maski (H, W). Kolejnosc: obrys, arena i sala tronowa, ulice, place, budynki, zauleki,
    jaskinie, wejscia, mury, swiatla, potwory."""

    def __init__(self, seed):
        self.seed = seed
        self.rng = random.Random(seed)
        self.p = St.Plan(CITY)
        self.cells = {}          # (x, y) -> (podloze, dekoracja, obiekt, mur): pola miasta (bez murow obrzeza jaskin)
        self.kind = {}           # (x, y) -> 'street' | 'yard' | 'arena' | 'building' | 'hall' | 'alley' | 'cave' | 'plaza'...
        self.buildings = []
        self.caves = []
        self.alley_ends = []
        self.ents = []
        self.radials = []

    # ---------------------------------------------------------------- obrys, srodek
    def shape(self):
        for attempt in range(60):
            inside, _cx, _cy = H.make_shape(self.seed + attempt * 13, SHAPE)
            h, w = inside.shape
            if w < 70 or h < 50:
                continue
            ed = dist_field(~inside)
            if ed.max() < 13:
                continue
            self.inside, self.ed, self.W, self.H = inside, ed, w, h
            self.core = ed >= 3.5
            ys, xs = np.nonzero(inside)
            mx, my = xs.mean(), ys.mean()
            cand = np.argwhere(ed >= ed.max() - 2.5)
            cy_, cx_ = min(cand, key=lambda q: (q[1] - mx) ** 2 + (q[0] - my) ** 2)
            self.C = (int(cx_), int(cy_))
            return
        raise Retry('shape')

    def in_core(self, x, y):
        return 0 <= x < self.W and 0 <= y < self.H and bool(self.core[y, x])

    def ray_len(self, ang, margin=0.0):
        cx, cy = self.C
        r = 0.0
        while r < 80:
            x, y = int(round(cx + math.cos(ang) * (r + 0.5))), int(round(cy + math.sin(ang) * (r + 0.5)))
            if not self.in_core(x, y):
                break
            r += 0.5
        return max(0.0, r - margin)

    # ---------------------------------------------------------------- arena i sala tronowa
    def arena(self):
        rng = self.rng
        cx, cy = self.C
        R = 7.4
        arena, rim, seats = set(), set(), set()
        for y in range(cy - 12, cy + 13):
            for x in range(cx - 12, cx + 13):
                d = math.hypot(x - cx, y - cy)
                if d <= R:
                    arena.add((x, y))
                elif d <= R + 1.2:
                    rim.add((x, y))
                elif d <= R + 2.6:
                    seats.add((x, y))
        self.arena_cells, self.rim, self.seats = arena, rim, seats
        # sala tronowa po tej stronie (kardynalnie), gdzie rdzen sieka najdalej
        best = max(((self.ray_len(math.atan2(dy, dx)), dx, dy) for (dx, dy) in ((0, -1), (1, 0), (0, 1), (-1, 0))))
        _room, dx, dy = best
        self.throne_dir = (dx, dy)
        depth, width, d0 = 7, 11, 10
        if dx == 0:
            x0, x1 = cx - width // 2, cx + width // 2
            y0, y1 = (cy - d0 - depth + 1, cy - d0) if dy < 0 else (cy + d0, cy + d0 + depth - 1)
        else:
            y0, y1 = cy - width // 2, cy + width // 2
            x0, x1 = (cx - d0 - depth + 1, cx - d0) if dx < 0 else (cx + d0, cx + d0 + depth - 1)
        self.hall = (x0, y0, x1, y1)
        hall_cells = {(x, y) for x in range(x0 - 1, x1 + 2) for y in range(y0 - 1, y1 + 2)}
        if not all(self.inside[y, x] if 0 <= x < self.W and 0 <= y < self.H else False for (x, y) in hall_cells):
            raise Retry('hall outside')
        gate = set()
        for k in range(0, d0 + 1):
            for s in (-1, 0, 1):
                gate.add((cx + dx * k + (s if dx == 0 else 0), cy + dy * k + (s if dy == 0 else 0)))
        self.hall_cells, self.gate_cells = hall_cells, gate
        self.keep_out = arena | rim | seats | hall_cells | gate
        for (x, y) in arena:
            self.cells[(x, y)] = ('sand', rng.choice((NONE,) * 7 + ('bones', 'blood')), NONE, NONE)
            self.kind[(x, y)] = 'arena'
        for (x, y) in rim:
            self.cells[(x, y)] = ('stone_floor', NONE, NONE, 'stone_wall')
            self.kind[(x, y)] = 'arenawall'
        for (x, y) in seats:
            self.cells[(x, y)] = ('stone_floor', NONE, NONE, NONE)
            self.kind[(x, y)] = 'seats'
        for y in range(y0 - 1, y1 + 2):
            for x in range(x0 - 1, x1 + 2):
                edge = x in (x0 - 1, x1 + 1) or y in (y0 - 1, y1 + 1)
                self.cells[(x, y)] = ('marble_floor', NONE, NONE, 'stone_wall' if edge else NONE)
                self.kind[(x, y)] = 'hallwall' if edge else 'hall'
        for (x, y) in gate:
            if (x, y) in rim or (x, y) in seats or (x, y) in hall_cells:
                self.cells[(x, y)] = ('marble_floor', NONE, NONE, NONE)
                self.kind[(x, y)] = 'gate'
        # dywan od areny do tronu, tron na koncu, posagi i sztandary pod scianami, skrzynia w rogu za dywanem
        if dx == 0:
            ty = y0 if dy < 0 else y1
            self.throne = (cx, ty)
            self.front = (cx, ty + (2 if dy < 0 else -2))
            axis = [(cx, y) for y in range(y0, y1 + 1)]
            side = [(x, y) for x in (x0, x1) for y in range(y0, y1 + 1, 2)]
            self.chest = (x1, ty + (1 if dy < 0 else -1))
        else:
            tx = x0 if dx < 0 else x1
            self.throne = (tx, cy)
            self.front = (tx + (2 if dx < 0 else -2), cy)
            axis = [(x, cy) for x in range(x0, x1 + 1)]
            side = [(x, y) for y in (y0, y1) for x in range(x0, x1 + 1, 2)]
            self.chest = (tx + (1 if dx < 0 else -1), y1)
        for (x, y) in axis:
            c = self.cells[(x, y)]
            if self.kind[(x, y)] in ('hall', 'gate'):
                self.cells[(x, y)] = (c[0], 'carpet', c[2], c[3])
        self.cells[self.throne] = ('marble_floor', 'carpet', 'throne', NONE)
        for (x, y) in side:
            if self.kind.get((x, y)) == 'hall' and (x, y) != self.chest:
                self.cells[(x, y)] = ('marble_floor', NONE, 'banner' if (x + y) % 4 == 0 else 'statue', NONE)
        # trybuny: posagi i brazery co kilka pol, srodek areny: podest z oltarzem i cztery kolumny
        k = 0
        for (x, y) in sorted(seats):
            ang = math.atan2(y - cy, x - cx)
            if abs(math.hypot(x - cx, y - cy) - (R + 1.9)) < 0.6 and int(round((ang % (2 * math.pi)) / (2 * math.pi) * 24)) % 3 == 0:
                if (x, y) not in gate and (x, y) not in hall_cells:
                    self.cells[(x, y)] = ('stone_floor', NONE, 'statue' if k % 2 == 0 else 'campfire', NONE)
                    k += 1
        for (ax, ay) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            self.cells[(cx + ax, cy + ay)] = ('sand', NONE, 'statue', NONE)
        for ax in (-1, 0, 1):
            for ay in (-1, 0, 1):
                self.cells[(cx + ax, cy + ay)] = ('marble_floor', NONE, NONE, NONE)
        self.cells[(cx, cy)] = ('marble_floor', NONE, 'altar', NONE)

    # ---------------------------------------------------------------- ulice
    def streets(self):
        rng = self.rng
        cx, cy = self.C
        tdx, tdy = self.throne_dir
        t_ang = math.atan2(tdy, tdx)
        base = rng.uniform(0, math.pi / 4)
        street = set()
        for i in range(8):
            a = base + i * math.pi / 4 + rng.uniform(-0.2, 0.2)
            if ang_diff(a, t_ang) < 0.65:
                continue
            L = self.ray_len(a, margin=2.5)
            if L < 18:
                continue
            p0 = (cx + math.cos(a) * 7.0, cy + math.sin(a) * 7.0)
            p1 = (cx + math.cos(a) * 11.5, cy + math.sin(a) * 11.5)
            end = (cx + math.cos(a) * L, cy + math.sin(a) * L)
            pts = wander(p1, end, rng, n=4, amp=5.5, ok=self.in_core)
            cells = polyline([p0] + pts)
            street |= brush(cells, 3)
            self.radials.append(dict(ang=a, end=(int(round(end[0])), int(round(end[1]))), len=L))
        # pierscien dookola areny (promien ~19, kretego ksztaltu, przerwany w sektorze sali tronowej)
        seg, segs = [], []
        for i in range(40):
            a = i / 40 * 2 * math.pi
            r = 19.5 + 2.4 * math.sin(a * 3 + 1.3) + rng.uniform(-1.0, 1.0)
            r = min(r, self.ray_len(a, margin=3.5))
            if ang_diff(a, t_ang) < 0.65 or r < 15:
                if seg:
                    segs.append(seg)
                seg = []
                continue
            seg.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        if seg:
            segs.append(seg)
        for s in segs:
            if len(s) >= 2:
                street |= brush(polyline(s), 3)
        # bramy w murze areny i trybunach tam, gdzie ulica dochodzi do areny
        for r_ in self.radials:
            a = r_['ang']
            gate = brush(polyline([(cx + math.cos(a) * 7.0, cy + math.sin(a) * 7.0),
                                   (cx + math.cos(a) * 11.5, cy + math.sin(a) * 11.5)]), 3)
            for q in gate & (self.rim | self.seats):
                self.cells[q] = ('cobblestone', NONE, NONE, NONE)
                self.kind[q] = 'street'
        street = {c for c in street if self.in_core(*c) and c not in self.keep_out}
        self.street = street
        for (x, y) in street:
            self.cells[(x, y)] = (rng.choice(('cobblestone',) * 5 + ('paving',)), NONE, NONE, NONE)
            self.kind[(x, y)] = 'street'
        if len(self.radials) < 4:
            raise Retry('few radials')

    # ---------------------------------------------------------------- place miedzy ulicami
    def yards(self):
        rng = self.rng
        mask = np.zeros((self.H, self.W), bool)
        for (x, y) in self.street:
            mask[y, x] = True
        ds = dist_field(mask, limit=24)
        n1 = H.vnoise(self.H, self.W, 5, self.seed + 3)
        n2 = H.vnoise(self.H, self.W, 3, self.seed + 4)
        reach = 6.0 + 9.0 * n1 + 2.0 * (n2 - 0.5)
        yard = (ds <= reach) & self.core
        keep = np.zeros_like(yard)
        for (x, y) in self.keep_out:
            if 0 <= x < self.W and 0 <= y < self.H:
                keep[y, x] = True
        yard &= ~H.dilate(keep, 1)
        self.yard = yard
        patch = H.vnoise(self.H, self.W, 6, self.seed + 5)
        for y, x in zip(*np.nonzero(yard)):
            x, y = int(x), int(y)
            if (x, y) in self.cells:
                continue
            r = rng.random()
            deco = 'bones' if r < 0.03 else ('debris' if r < 0.06 else ('blood' if r < 0.07 else NONE))
            self.cells[(x, y)] = ('dirt' if patch[y, x] > 0.58 else 'cave_floor', deco, NONE, NONE)
            self.kind[(x, y)] = 'yard'

    # ---------------------------------------------------------------- kamienice
    def build_houses(self, want=60):
        rng = self.rng
        occupied = set()
        fm = np.zeros((self.H, self.W), bool)
        for c, k in self.kind.items():
            if k == 'yard' and 0 <= c[0] < self.W and 0 <= c[1] < self.H:
                fm[c[1], c[0]] = True
        placed = fails = 0
        pre = None
        while placed < want and fails < 400:
            shape = _shape(rng)
            w_ = max(c[0] for c in shape) + 1
            h_ = max(c[1] for c in shape) + 1
            if pre is None:
                pre = np.pad(fm.astype(np.int32).cumsum(0).cumsum(1), ((1, 0), (1, 0)))
            tot = pre[h_:, w_:] - pre[:-h_, w_:] - pre[h_:, :-w_] + pre[:-h_, :-w_]
            ys, xs = np.nonzero(tot == w_ * h_)
            if len(xs) == 0:
                fails += 1
                continue
            i = rng.randrange(len(xs))
            px, py = int(xs[i]), int(ys[i])
            cells = {(x + px, y + py) for (x, y) in shape}
            if cells & occupied:
                fails += 1
                continue
            ring = {(x + dx, y + dy) for (x, y) in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
            walls = _walls_of(cells)
            inner = cells - walls
            if len(inner) < 6:
                fails += 1
                continue
            door, best = None, 1e9
            for (x, y) in walls:
                for (dx, dy) in DIRS4:
                    out_c, in_c = (x + dx, y + dy), (x - dx, y - dy)
                    if out_c in cells or in_c not in inner or self.kind.get(out_c) not in ('street', 'yard'):
                        continue
                    d = 0 if out_c in self.street else (1 if (x + dx * 2, y + dy * 2) in self.street else 2)
                    d += rng.random() * 1.5
                    if d < best:
                        best, door = d, ((x, y), out_c, in_c)
            if door is None:
                fails += 1
                continue
            (dxy, front, inner_c) = door
            for c in cells:
                if c in walls:
                    self.cells[c] = ('stone_floor', NONE, NONE, 'stone_wall')
                    self.kind[c] = 'housewall'
                else:
                    self.cells[c] = ('stone_floor', rng.choice((NONE,) * 12 + ('debris', 'bones')), NONE, NONE)
                    self.kind[c] = 'building'
            self.cells[dxy] = ('stone_floor', NONE, 'door_open', NONE)
            self.kind[dxy] = 'door'
            occupied |= ring
            for (x, y) in ring:
                if 0 <= x < self.W and 0 <= y < self.H:
                    fm[y, x] = False
            pre = None
            fails = 0
            self.buildings.append(dict(cells=cells, inner=inner, door=dxy, front=front, inner_c=inner_c))
            placed += 1
        if placed < 20:
            raise Retry('few houses')
        furniture = ['table', 'bed', 'barrel', 'crate', 'bookcase', 'anvil', 'clay_pot', 'sack', 'bench', 'table']
        for b in self.buildings:
            near_door = {c for c in b['inner'] if abs(c[0] - b['inner_c'][0]) + abs(c[1] - b['inner_c'][1]) <= 2}
            cand = sorted(c for c in b['inner'] if c not in near_door
                          and any((c[0] + dx, c[1] + dy) not in b['inner'] for dx, dy in DIRS4))
            rng.shuffle(cand)
            for c in cand[:rng.randint(2, 4)]:
                g = self.cells[c]
                self.cells[c] = (g[0], NONE, rng.choice(furniture), NONE)
                if not self._inner_connected(b):
                    self.cells[c] = g

    def _inner_connected(self, b):
        start = b['inner_c']
        seen = {start}
        q = deque([start])
        while q:
            x, y = q.popleft()
            for dx, dy in DIRS4:
                n = (x + dx, y + dy)
                if n in b['inner'] and n not in seen and self.cells[n][2] == NONE:
                    seen.add(n)
                    q.append(n)
        return {c for c in b['inner'] if self.cells[c][2] == NONE} <= seen

    # ---------------------------------------------------------------- zauleki i jaskinie
    def alleys(self, n=9):
        rng = self.rng
        pts = sorted(self.street)
        rng.shuffle(pts)
        used = []
        for (x, y) in pts:
            if len(self.alley_ends) >= n:
                break
            if any(abs(x - a) + abs(y - b) < 14 for a, b in used):
                continue
            ang = rng.uniform(0, 2 * math.pi)
            cur = (float(x), float(y))
            path = []
            L = rng.randint(10, 17)
            good = True
            for i in range(L):
                ang += rng.uniform(-0.4, 0.4)
                cur = (cur[0] + math.cos(ang), cur[1] + math.sin(ang))
                c = (int(round(cur[0])), int(round(cur[1])))
                cells2 = brush([c], 2)
                if not all(self.in_core(*q) for q in cells2):
                    good = False
                    break
                if i >= 3 and any(q in self.cells for q in cells2):
                    good = False
                    break
                path.append(c)
            if not good:
                continue
            end = path[-1]
            plaza = {(end[0] + dx, end[1] + dy) for dx in range(-2, 3) for dy in range(-2, 3) if dx * dx + dy * dy <= 5}
            if any(q in self.cells or not self.in_core(*q) for q in plaza):
                continue
            carve = brush(path, 2) | plaza
            if any(self.kind.get(q) in SOLID for q in carve):
                continue
            for q in carve:
                if q in self.cells:
                    continue
                self.cells[q] = ('dirt', rng.choice((NONE,) * 6 + ('bones', 'debris')), NONE, NONE)
                self.kind[q] = 'alley'
            for (ax, ay) in ((end[0] - 1, end[1] - 1), (end[0] + 1, end[1] + 1)):
                g = self.cells[(ax, ay)]
                self.cells[(ax, ay)] = (g[0], NONE, rng.choice(('barrel', 'crate', 'sack', 'clay_pot')), NONE)
            self.alley_ends.append(end)
            used.append((x, y))

    def side_caves(self, n=5):
        rng = self.rng
        cx, cy = self.C
        ys, xs = np.nonzero(self.inside)
        cands = sorted(((math.hypot(x - cx, y - cy), x, y) for y, x in zip(ys.tolist(), xs.tolist())
                        if 2.5 <= self.ed[y, x] <= 6.0 and math.hypot(x - cx, y - cy) > 24), reverse=True)
        chosen = []
        for (_d, x, y) in cands:
            if all(math.hypot(x - a, y - b) > 24 for a, b in chosen):
                chosen.append((x, y))
            if len(chosen) >= n + 2:
                break
        for (x, y) in chosen:
            if len(self.caves) >= n:
                break
            r = rng.uniform(4.6, 6.6)
            noise = [rng.uniform(-1, 1) for _ in range(12)]
            blob = set()
            for dy in range(-9, 10):
                for dx in range(-9, 10):
                    a = math.atan2(dy, dx)
                    rr = r + 1.5 * noise[int((a % (2 * math.pi)) / (2 * math.pi) * 12) % 12] + 0.8 * math.sin(a * 3)
                    if math.hypot(dx, dy) <= rr:
                        blob.add((x + dx, y + dy))
            if any(self.kind.get(q) in SOLID for q in blob):
                continue
            near = min((c for c, k in self.kind.items() if k in ('street', 'yard', 'alley')),
                       key=lambda c: (c[0] - x) ** 2 + (c[1] - y) ** 2)
            pts = wander((x, y), near, rng, n=3, amp=4.0, ok=lambda a, b: self.in_core(a, b) or (a, b) in blob)
            tunnel = brush(polyline(pts), 2)
            if any(self.kind.get(q) in SOLID for q in tunnel):
                continue
            for q in blob | tunnel:
                if q in self.cells:
                    continue
                self.cells[q] = ('dirt' if rng.random() < 0.3 else 'cave_floor',
                                 rng.choice((NONE,) * 5 + ('bones', 'blood', 'debris', 'bones')), NONE, NONE)
                self.kind[q] = 'cave' if q in blob else 'alley'
            for _ in range(rng.randint(3, 6)):
                q = rng.choice(sorted(blob))
                c = self.cells.get(q)
                if c is not None and F.walk(c) and c[2] == NONE and q not in tunnel and \
                        all(self.cells.get((q[0] + dx, q[1] + dy)) is not None and F.walk(self.cells[(q[0] + dx, q[1] + dy)])
                            for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    self.cells[q] = (c[0], c[1], rng.choice(('rock', 'rubble', 'barrel', 'crate')), NONE)
            self.caves.append((x, y))
        if len(self.caves) < 3:
            raise Retry('few caves')

    # ---------------------------------------------------------------- wejscia z jaskin
    def entrances(self):
        best = None
        rads = self.radials
        for i in range(len(rads)):
            for j in range(i + 1, len(rads)):
                d = ang_diff(rads[i]['ang'], rads[j]['ang'])
                if best is None or d > best[0]:
                    best = (d, i, j)
        if best is None or best[0] < 1.8:
            raise Retry('radials')
        for idx in (best[1], best[2]):
            ex, ey = rads[idx]['end']
            for dx in range(-3, 4):
                for dy in range(-3, 4):
                    q = (ex + dx, ey + dy)
                    if dx * dx + dy * dy <= 11 and self.in_core(*q) and q not in self.cells:
                        self.cells[q] = ('cave_floor', NONE, NONE, NONE)
                        self.kind[q] = 'plaza'
            for (dx, dy) in ((-2, 2), (2, 2), (-2, -2), (2, -2)):
                c = self.cells.get((ex + dx, ey + dy))
                if c is not None and F.walk(c) and c[2] == NONE:
                    self.cells[(ex + dx, ey + dy)] = (c[0], NONE, 'campfire' if dy > 0 else 'statue', NONE)
            self.cells[(ex, ey)] = ('cave_floor', NONE, 'stairs_up', NONE)
            self.kind[(ex, ey)] = 'stairs'
            self.ents.append((ex, ey))

    # ---------------------------------------------------------------- mury, swiatla
    def seal(self):
        p = self.p
        walls = set()
        for (x, y), c in self.cells.items():
            if c[3] != NONE:
                continue
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    n = (x + dx, y + dy)
                    if n not in self.cells:
                        walls.add(n)
        for (x, y), c in self.cells.items():
            p.put(x, y, CITY_Z, c)
        for (x, y) in walls:
            p.put(x, y, CITY_Z, ('cave_floor', NONE, NONE, 'cave_wall'))

    def lights(self):
        p, rng, z = self.p, self.rng, CITY_Z
        lamps = []
        for (x, y) in sorted(self.street):
            for (dx, dy) in DIRS4:
                n = (x + dx, y + dy)
                if self.kind.get(n) != 'yard' or p.get(n[0], n[1], z)[2] != NONE:
                    continue
                if all(F.walk(p.get(n[0] + a, n[1] + b, z)) and p.get(n[0] + a, n[1] + b, z)[2] == NONE
                       for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b) and all(abs(n[0] - a) + abs(n[1] - b) > 9 for a, b in lamps):
                    lamps.append(n)
                    c = p.get(n[0], n[1], z)
                    p.put(n[0], n[1], z, (c[0], c[1], 'street_lamp', NONE))
                    break
        keep = set()
        for (ex, ey) in self.ents:
            keep |= {(ex + a, ey + b) for a in range(-2, 3) for b in range(-2, 3)}
        walk_cells = [q for q, c in p.f[z].items() if St.walk(c) and q not in keep]
        F.ensure_lights(lambda x, y: p.get(x, y, z), lambda x, y, c: p.put(x, y, z, c), walk_cells, 'wall_torch', rng,
                        per=60, minimum=12)
        x0, y0, x1, y1 = self.hall
        for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            c = p.get(x, y, z)
            if c is not None and F.walk(c) and c[2] == NONE and (x, y) != self.chest:
                p.put(x, y, z, (c[0], c[1], 'candle', NONE))

    # ---------------------------------------------------------------- potwory
    def populate(self):
        p, rng, z = self.p, self.rng, CITY_Z
        cx, cy = self.C
        dist = H.route_dist(p, z, {e: 0 for e in self.ents})
        self.reach_cells = set(dist)
        cells = [q for q, c in p.f[z].items() if St.walk(c) and c[2] == NONE and q in dist]
        cells.sort()
        rng.shuffle(cells)
        kinds = {q: self.kind.get(q) for q in cells}
        picks = []

        def free(q, gap):
            return all(abs(q[0] - a) + abs(q[1] - b) >= gap for a, b, _k in picks)
        arena_pts = []
        for q in cells:
            if kinds[q] == 'arena' and math.hypot(q[0] - cx, q[1] - cy) > 4.5 and all(abs(q[0] - a) + abs(q[1] - b) >= 7
                                                                                       for a, b in arena_pts):
                arena_pts.append(q)
        for q in arena_pts[:4]:
            picks.append((q[0], q[1], 'arena'))
        for (x, y) in self.caves:
            q = min((c for c in cells if kinds[c] == 'cave'), key=lambda c: (c[0] - x) ** 2 + (c[1] - y) ** 2, default=None)
            if q:
                picks.append((q[0], q[1], 'cave'))
        for end in self.alley_ends:
            q = min((c for c in cells if kinds[c] == 'alley'), key=lambda c: (c[0] - end[0]) ** 2 + (c[1] - end[1]) ** 2,
                    default=None)
            if q and free(q, 8):
                picks.append((q[0], q[1], 'alley'))
        for b in self.buildings:
            if len(b['inner']) >= 16 and rng.random() < 0.7:
                mx = sum(i[0] for i in b['inner']) / len(b['inner'])
                my = sum(i[1] for i in b['inner']) / len(b['inner'])
                q = min((c for c in b['inner'] if c in dist and p.get(c[0], c[1], z)[2] == NONE),
                        key=lambda c: abs(c[0] - mx) + abs(c[1] - my), default=None)
                if q and free(q, 8):
                    picks.append((q[0], q[1], 'house'))
        for q in cells:
            if kinds[q] in ('street', 'yard', 'plaza') and free(q, 10) and min(abs(q[0] - e[0]) + abs(q[1] - e[1])
                                                                                for e in self.ents) > 8:
                picks.append((q[0], q[1], 'open'))
        dmax = max(dist[(x, y)] for (x, y, _k) in picks)
        dmin = min(dist[(x, y)] for (x, y, _k) in picks)
        n = len(LADDER)
        out = []
        for (x, y, k) in picks:
            frac = (dist[(x, y)] - dmin) / max(1, dmax - dmin)
            idx = int(frac * n * 0.98 + rng.uniform(-0.4, 0.4))
            r = math.hypot(x - cx, y - cy)
            if k == 'arena':
                idx = n - 1 if rng.random() < 0.6 else n - 2
            elif r < 22 and k in ('open', 'house'):
                idx = max(idx, 5 + (0 if r > 16 else 1))
            elif k == 'cave':
                idx = max(idx, n - 3 + (1 if frac > 0.75 else 0))
            out.append([x, y, max(0, min(n - 1, idx)), k])
        out.sort(key=lambda t: dist[(t[0], t[1])])
        for t in out[:4]:                       # przy wejsciach tylko najslabsi
            if t[3] != 'arena':
                t[2] = min(t[2], 2)
        for (x, y, idx, k) in out:
            cnt = 3 if idx <= 6 else 2
            if idx <= 3 and rng.random() < 0.5:
                cnt = 4
            p.spawn(LADDER[idx], x, y, z, radius=3 if k == 'house' else 4, count=cnt, respawn=220 + 12 * idx)
        self.spawn_count = len(out)
        fx, fy = self.front
        p.spawn(BOSS, fx, fy, z, radius=2, count=1, respawn=1800)
        p.chests.append((self.chest[0], self.chest[1], z, QUEST_CHEST[0], QUEST_CHEST[1], QUEST_CHEST[2]))
        spots = list(self.alley_ends) + list(self.caves)
        rng.shuffle(spots)
        ci = 0
        for q in spots:
            if ci >= 7:
                break
            spot = I.chest_spot(p, z, q)
            if spot is not None and self.kind.get(spot) in ('alley', 'cave') and spot in dist:
                item, cnt = CITY_CHESTS[ci % len(CITY_CHESTS)]
                p.chests.append((spot[0], spot[1], z, 1200 + 240 * ci, item, cnt))
                ci += 1

    def build(self):
        self.shape()
        self.arena()
        self.streets()
        self.yards()
        self.build_houses()
        self.alleys()
        self.side_caves()
        self.entrances()
        self.seal()
        self.lights()
        self.populate()
        I.floor_region(self.p, CITY, CITY_Z, CITY_LEVELS)
        self.p.finish()
        return self


def city_plan(seed=SEED):
    last = None
    for attempt in range(30):
        try:
            return City(seed + attempt * 101).build()
        except Retry as e:
            last = e
    raise AssertionError(f'{CITY}: no layout ({last})')


# ====================================================================== jaskinie zejsciowe (-1..-6)
def chain_plan(name, seed, w=48, h=36):
    """Wijaca sie jaskinia przez szesc pieter; na kazdym jeden chodnik od schodow w gore do schodow w dol, z komorami po
    drodze, slepymi odnogami i (co drugie pietro) skrzynia. Zwraca (plan, pole schodow w gore na -1, pole schodow w dol na -6)."""
    rng = random.Random(seed)
    ok_fn = lambda a, b: 4 <= a < w - 4 and 4 <= b < h - 4  # noqa: E731
    for attempt in range(40):
        p = St.Plan(name)
        arrive = (4, rng.randint(8, h - 9))
        info = {}
        good = True
        for k in range(1, 7):
            z = -k
            ex = w - 6 if arrive[0] < w // 2 else 5
            ey = rng.randint(6, h - 7)
            for _ in range(20):
                if abs(ey - arrive[1]) >= 8:
                    break
                ey = rng.randint(6, h - 7)
            end = (ex, ey)
            pts = [(int(round(a)), int(round(b))) for a, b in wander(arrive, end, rng, n=4, amp=9.0, ok=ok_fn)]
            I.dig(p, z, 0, 0, w, h, pts, 'horn_cave', rng, room=(2, 3))
            sides = []
            for i in rng.sample(range(1, len(pts) - 1), 2):
                a = pts[i]
                ang = rng.uniform(0, 2 * math.pi)
                L = rng.randint(8, 13)
                b = (min(max(int(a[0] + math.cos(ang) * L), 5), w - 6), min(max(int(a[1] + math.sin(ang) * L), 5), h - 6))
                if abs(b[0] - a[0]) + abs(b[1] - a[1]) < 6:
                    continue
                mid = [(int(round(u)), int(round(v))) for u, v in wander(a, b, rng, n=1, amp=3.0, ok=ok_fn)]
                I.dig(p, z, 0, 0, w, h, mid, 'horn_cave', rng, room=(2, 3))
                sides.append(b)
            I._clear(p, z, arrive, 'horn_cave')
            I._clear(p, z, end, 'horn_cave')
            if not I._connected(p, z, arrive, [end] + sides):
                good = False
                break
            keep = {(q[0] + a, q[1] + b) for q in (arrive, end) for a in range(-2, 3) for b in range(-2, 3)}
            F.ensure_lights(lambda x, y, z=z: p.get(x, y, z), lambda x, y, c, z=z: p.put(x, y, z, c),
                            [q for q, c in p.f[z].items() if St.walk(c) and q not in keep], 'wall_torch', rng,
                            per=40, minimum=8)
            info[z] = dict(route=pts, sides=sides, start=arrive, end=end)
            arrive = end
        if not good:
            continue
        for k in range(1, 6):
            e = info[-k]['end']
            p.stairs(-(k + 1), e, e)            # w gore na -(k+1) i w dol na -k w tym samym polu
        for k in range(1, 7):
            z = -k
            inf = info[z]
            mons = CAVE_MONSTERS[k]
            for i, q in enumerate(inf['route'][1:-1]):
                if i % 2 == 1:
                    continue
                cells = [(abs(c[0] - q[0]) + abs(c[1] - q[1]), c) for c, v in p.f[z].items()
                         if St.walk(v) and v[2] == NONE and abs(c[0] - q[0]) <= 3 and abs(c[1] - q[1]) <= 3]
                if not cells:
                    continue
                _d, c = min(cells)
                if min(abs(c[0] - inf[s][0]) + abs(c[1] - inf[s][1]) for s in ('start', 'end')) < 7:
                    continue
                p.spawn(mons[(i + k) % len(mons)], c[0], c[1], z, radius=3, count=2 + (i + k) % 2, respawn=200 + 8 * k)
            if k in CAVE_CHESTS and inf['sides']:
                spot = I.chest_spot(p, z, inf['sides'][0])
                if spot is not None:
                    item, cnt = CAVE_CHESTS[k]
                    p.chests.append((spot[0], spot[1], z, 150 * k, item, cnt))
            I.floor_region(p, CAVE, z, CAVE_LEVELS)
        s1, e6 = info[-1]['start'], info[-6]['end']
        p.obj(s1[0], s1[1], -1, 'stairs_up')
        p.obj(e6[0], e6[1], -6, 'stairs_down')
        p.finish()
        p.snap()
        return p, s1, e6
    raise AssertionError(f'{name}: chain not built')


# ====================================================================== laczenie planow i swiata
def no_packs_near_temples(ctx, rect, extra=((524, 328),)):
    """Stada nie moga stac w promieniu 60 pol od swiatyni miasta (test PartyPackSpawnsTest); Durnkar
    jest stawiany pozniej, wiec jego swiatynie podaje extra."""
    temples = [(t[1][0], t[1][1]) for t in ctx['towns']] + list(extra)
    out = []
    for ln in ctx['spawns']:
        f = ln.split(';')
        if (not ln.startswith('#') and len(f) == 8 and f[7] == 'stado' and rect[0] <= int(f[1]) <= rect[2]
                and rect[1] <= int(f[2]) <= rect[3] and any(max(abs(int(f[1]) - a), abs(int(f[2]) - b)) < 62 for a, b in temples)):
            ln = ';'.join(f[:7])
        out.append(ln)
    ctx['spawns'] = out


def link(ctx, lower, upper, land_lower=None, land_upper=None):
    """Schody miedzy polami swiata: lower (x, y, z) ze schodami w gore, upper (x, y, z+1) ze schodami w dol.
    Ladowanie: wolne pole obok schodow docelowych (chyba ze podane)."""
    world = ctx['world']

    def land(p):
        for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1)):
            c = world.get(p[0] + dx, p[1] + dy, p[2])
            if c is not None and F.walk(c) and c[2] == NONE:
                return (p[0] + dx, p[1] + dy)
        raise AssertionError(f'minotaur: no landing next to {p}')
    lu = land_upper or land(upper)
    ll = land_lower or land(lower)
    ctx['transitions'].append((lower[0], lower[1], lower[2], 'STAIRS', lu[0], lu[1], upper[2]))
    ctx['transitions'].append((upper[0], upper[1], upper[2], 'STAIRS', ll[0], ll[1], lower[2]))


def _bbox(plan):
    zs = list(plan.f)
    return (min(plan.bounds(z)[0] for z in zs), min(plan.bounds(z)[1] for z in zs),
            max(plan.bounds(z)[2] for z in zs), max(plan.bounds(z)[3] for z in zs))


def build(ctx, cost, helpers):
    import realms
    import wilds
    world = ctx['world']
    S = len(world.floors[0])
    rng = random.Random(SEED)
    temple = dict(ctx['towns'])['Varden'][:2]
    main = H._reach_mask(world, S, [temple])
    w = wilds.Wilds(ctx, cost, dict(helpers, reach=main))
    level = I._levels(helpers['monsters_csv'])
    for k in LADDER + [BOSS]:
        assert k in level, f'minotaur: unknown monster {k}'
    # ---------------------------------------------------------------- wejscia na powierzchni
    avoid = []
    for r in ctx.get('regions_new', []):
        if r[5] == 0 and r[0] in ('Wzgórza Goblinów', 'Wzgórza Smoków', 'Wzgórza Hydr', 'Warownia Gorvalda'):
            avoid.append((r[1] - 18, r[2] - 18, r[3] + 18, r[4] + 18))
        elif r[5] == 0 and r[0] in ('Twierdza Krwawego Kła', 'Obóz Łowczyń', 'Cyklopolis', 'Elandor'):
            avoid.append((r[1] - 14, r[2] - 14, r[3] + 14, r[4] + 14))
    ents = [tuple(e) for _n, e in ctx.get('entrances', [])] + [tuple(wd['front']) for wd in ctx.get('wild_dungeons', [])]

    def site(x, y):
        return (not any(a <= x <= c and b <= y <= d for (a, b, c, d) in avoid)
                and math.hypot(x - temple[0], y - temple[1]) >= 90
                and not any(abs(x - a) + abs(y - b) < 26 for a, b in ents) and w.site_ok(x, y))
    cands = [(x, y) for y in range(20, S - 20, 3) for x in range(20, S - 20, 3) if main[y, x] and site(x, y)]
    assert cands, 'minotaur: no site for entrances'
    a = min(cands, key=lambda q: math.hypot(q[0] - MOUTH_NEAR[0], q[1] - MOUTH_NEAR[1]))
    pair = [q for q in cands if 55 <= math.hypot(q[0] - a[0], q[1] - a[1]) <= 100]
    assert pair, 'minotaur: no second entrance'
    b = min(pair, key=lambda q: abs(math.hypot(q[0] - a[0], q[1] - a[1]) - 70))
    mouths = [a, b]
    # ---------------------------------------------------------------- plany i kontrola dostepu wewnatrz planow
    city = city_plan()
    cp = city.p
    chains = [chain_plan(f'{CAVE} {n}', SEED + 17 * (i + 1)) for i, n in enumerate('AB')]
    for e in city.ents:
        seen = cp.reach([(e[0], e[1], CITY_Z)])
        for s in cp.spawns:
            assert (s[1], s[2], s[3]) in seen, f'{CITY}: spawn {s} unreachable from {e}'
        for c in cp.chests:
            assert any((c[0] + dx, c[1] + dy, c[2]) in seen for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f'{CITY}: chest {c}'
    for (pl, s1, e6) in chains:
        seen = pl.reach([(s1[0], s1[1], -1)])
        for s in pl.spawns:
            assert (s[1], s[2], s[3]) in seen, f'{pl.name}: spawn {s} unreachable'
        for c in pl.chests:
            assert any((c[0] + dx, c[1] + dy, c[2]) in seen for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f'{pl.name}: chest {c}'
        assert (e6[0], e6[1], -6) in seen
    # ---------------------------------------------------------------- stawianie: miasto pod srodkiem wejsc, potem jaskinie
    mark = helpers['mark']
    mid = ((a[0] + b[0]) // 2, (a[1] + b[1]) // 2)
    bx0, by0, bx1, by1 = _bbox(cp)
    Wc = St.stamp(ctx, cp, (mid[0] - (bx0 + bx1) // 2, mid[1] - (by0 + by1) // 2), mark, per_floor=True)
    I.tag_packs(ctx, Wc, cp, only=lambda z: True, level=level)
    no_packs_near_temples(ctx, (mid[0] - 80, mid[1] - 80, mid[0] + 80, mid[1] + 80))
    infos = []
    for i, ((pl, s1, e6), m, ent) in enumerate(zip(chains, mouths, city.ents)):
        x0, y0, x1, y1 = _bbox(pl)
        Wp = St.stamp(ctx, pl, (m[0] - (x0 + x1) // 2, m[1] - (y0 + y1) // 2), mark, per_floor=True)
        I.tag_packs(ctx, Wp, pl, only=lambda z: z <= -4, level=level)
        no_packs_near_temples(ctx, (m[0] - 40, m[1] - 40, m[0] + 40, m[1] + 40))
        front = w.entrance('cave', m, rng)
        ctx.setdefault('entrances', []).append((f'{CAVE} {"AB"[i]}', front))
        s1w, e6w, entw = Wp(s1[0], s1[1], -1), Wp(e6[0], e6[1], -6), Wc(ent[0], ent[1], CITY_Z)
        link(ctx, (s1w[0], s1w[1], -1), (m[0], m[1], 0), land_upper=front)
        link(ctx, (entw[0], entw[1], CITY_Z), (e6w[0], e6w[1], -6))
        infos.append(dict(mouth=m, front=front, cave_start=(s1w[0], s1w[1], -1), cave_end=(e6w[0], e6w[1], -6),
                          city_entrance=(entw[0], entw[1], CITY_Z)))
    # ---------------------------------------------------------------- weryfikacja: Varden -> boss i skrzynia, brak drogi do premium
    g = realms.Graph(ctx, S)
    start = g.idx(temple[0], temple[1], 0)
    bw, cw = Wc(*city.front, CITY_Z), Wc(city.chest[0], city.chest[1], CITY_Z)
    path = g.path(start, {g.idx(bw[0], bw[1], CITY_Z)})
    assert path is not None, f'{CITY}: boss unreachable from Varden'
    assert g.path(start, {g.idx(cw[0], cw[1], CITY_Z)}) is not None, f'{CITY}: chest unreachable from Varden'
    for name in realms.PREMIUM:
        t = dict(ctx['towns'])[name]
        assert g.path(start, {g.idx(t[0], t[1], t[2])}) is None, f'walking route to premium {name} exists'
    out = dict(mouths=infos, boss=(bw[0], bw[1], CITY_Z), chest=(cw[0], cw[1], CITY_Z), centre=Wc(*city.C, CITY_Z),
               spawns=city.spawn_count, buildings=len(city.buildings), route_len=len(path), city_origin=Wc(0, 0, CITY_Z))
    ctx['minotaur'] = out
    return out
