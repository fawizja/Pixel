"""Kontynent swiata: ksztalt ladu, gory, biomy, rzeki, jeziora i wyspy. Wynik to pola terenu (krotki kafli town.py)
i mapy pomocnicze dla miast i podziemi.

2026-10-27 (Darek: "zaprojektuj mape jak na zdjeciu, 1000/1000"): teren pochodzi z obrazka mapy Darka, ktory
picture_terrain.py zamienil na klasy pol (data/world1k.txt.gz): woda, pustynia, snieg, lawa, czarne szczyty,
skaly, trawa i gesty las. Morze to woda polaczona z brzegiem mapy, reszta wody to rzeki i jeziora. Gory powstaja
tam, gdzie obrazek ma skupiska skal. Wszystko deterministyczne (stale ziarna).
"""
import gzip
import math
import os
import random
from collections import deque

import numpy as np

S = 1000                    # rozmiar swiata (kwadrat)
CELL = S // 64
HERE = os.path.dirname(os.path.abspath(__file__))
# Teren z obrazka mapy Darka (2026-10-27): klasy pol 1000x1000 z picture_terrain.py
PICTURE = os.path.join(HERE, 'data', 'world1k.txt.gz')

# --- miejsca (wspolrzedne swiata 1000 x 1000) ---
VOLCANO = (367, 70)                 # krater wulkanu (Czarne Szczyty, polnocny zachod)
VOLCANO_BOX = (215, 15, 470, 300)   # Czarne Szczyty: lad tu jest wulkaniczny
SNOW_TOP = 175                      # Lodowe Szczyty: snieg na polnoc od tej linii (i ich skaly)
ROCKY_LANDS = (60, 560, 430, 800)   # Skaliste Ziemie (poludniowy zachod)
JUNGLE = (555, 400, 840, 780)       # Dzungla Zielony Mrok
LAKES = []
RIVERS = []
ISLANDS = []


def picture_classes():
    with gzip.open(PICTURE, 'rt', encoding='ascii') as f:
        rows = f.read().split()
    assert len(rows) == S and all(len(r) == S for r in rows), 'world1k.txt.gz: zly rozmiar'
    return np.array([list(r) for r in rows])


def fbm(seed, octaves=4, base=24.0):
    """Szum fraktalny S x S w [-1, 1] (wartosci losowe w siatce, interpolacja dwuliniowa)."""
    rs = np.random.RandomState(seed)
    out = np.zeros((S, S))
    amp, total, scale = 1.0, 0.0, base
    for _ in range(octaves):
        n = int(S / scale) + 3
        grid = rs.uniform(-1, 1, (n, n))
        ys, xs = np.mgrid[0:S, 0:S] / scale
        x0, y0 = xs.astype(int), ys.astype(int)
        fx, fy = xs - x0, ys - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0, x0] * (1 - fx) + grid[y0, x0 + 1] * fx
        b = grid[y0 + 1, x0] * (1 - fx) + grid[y0 + 1, x0 + 1] * fx
        out += amp * (a * (1 - fy) + b * fy)
        total += amp
        amp *= 0.5
        scale /= 2.0
    return out / total


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = np.clip(((px - ax) * dx + (py - ay) * dy) / max(1e-9, dx * dx + dy * dy), 0, 1)
    return np.hypot(px - (ax + t * dx), py - (ay + t * dy))


def polyline_dist(pts, px, py):
    d = np.full(px.shape, 1e9)
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        d = np.minimum(d, seg_dist(px, py, ax, ay, bx, by))
    return d


class Terrain:
    def __init__(self, force_land=None, force_water=None):
        """force_land / force_water: maski S x S (miasta i wyspy kopiowane ze starego swiata)."""
        ys, xs = np.mgrid[0:S, 0:S].astype(float)
        self.xs, self.ys = xs, ys
        cls = picture_classes()
        self.cls = cls
        # rog z legenda obrazka (prostokat byl woda): ukosny brzeg pustyni od (200,150) do (40,300)
        warp = 14 * fbm(122, 3, 16)
        corner = (xs < 210) & (ys < 310)
        inside = xs + warp >= 200 - (ys - 150) * 1.07
        cls[corner & (xs < 134) & (ys < 292) & inside] = 'S'
        cls[corner & ~inside] = 'W'
        # napisy obrazka: "Lodowe Szczyty" (kopia terenu dala prostokatne jezioro) - snieg; "Wyspy Szmaragdowe"
        # (prosta krawedz wysp) - morze
        lab = (xs >= 600) & (xs < 708) & (ys >= 56) & (ys < 100)
        cls[lab & (cls == 'W')] = 'N'
        cls[(xs >= 826) & (xs < 962) & (ys >= 512) & (ys < 552)] = 'W'
        water = cls == 'W'
        water[:3, :] = water[-3:, :] = True
        water[:, :3] = water[:, -3:] = True
        # morze: woda polaczona z brzegiem mapy po odcieciu waskich rzek (otwarcie promieniem 4); reszta wody
        # na ladzie to rzeki i jeziora (mosty, brzegi bez plazy)
        open_ = self._erode(water, 4)
        sea_core = self._flood(open_, (0, 0))
        sea = self._dilate(sea_core, 5) & water
        self.land = ~sea
        self._drop_specks()
        if force_land is not None:
            self.land |= force_land
        if force_water is not None:
            self.land &= ~force_water
        self.coast_dist = self._distance(~self.land)
        # gory: gestosc skal (R) i czarnych szczytow (V) z obrazka, poszarpana szumem - przejscia zostaja
        rocky = ((cls == 'R') | (cls == 'V')).astype(float)
        dens = rocky.copy()
        for _ in range(3):
            dens = (dens + np.roll(dens, 1, 0) + np.roll(dens, -1, 0) + np.roll(dens, 1, 1) + np.roll(dens, -1, 1)) / 5
        self.ridge = dens * (0.62 + 0.3 * fbm(105, 3, 10)) + 0.08 * fbm(104, 3, 14)
        self.elev = self.ridge + np.minimum(self.coast_dist / 30.0, 1.0) * 0.35
        self.lava = (cls == 'L') & self.land
        self.biome = self._biomes()
        inland = water & self.land
        self.lake = inland & (self.biome == 'snow')
        self.river = inland & ~self.lake
        self.water = ~self.land | self.river | self.lake
        self.rivers = []

    @staticmethod
    def _erode(m, r):
        out = m.copy()
        for _ in range(r):
            out = out & np.roll(out, 1, 0) & np.roll(out, -1, 0) & np.roll(out, 1, 1) & np.roll(out, -1, 1)
        return out

    @staticmethod
    def _dilate(m, r):
        out = m.copy()
        for _ in range(r):
            out = out | np.roll(out, 1, 0) | np.roll(out, -1, 0) | np.roll(out, 1, 1) | np.roll(out, -1, 1)
        return out

    @staticmethod
    def _flood(mask, start):
        seen = np.zeros(mask.shape, bool)
        stack = [start]
        seen[start[1], start[0]] = True
        while stack:
            x, y = stack.pop()
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((nx, ny))
        return seen

    # ---------------------------------------------------------------- pomocnicze
    def _drop_specks(self):
        seen = np.zeros((S, S), bool)
        for y in range(S):
            for x in range(S):
                if self.land[y, x] and not seen[y, x]:
                    comp = self._component(x, y, seen)
                    if len(comp) < 60:
                        for (cx, cy) in comp:
                            self.land[cy, cx] = False

    def _component(self, x, y, seen):
        stack = [(x, y)]
        seen[y, x] = True
        out = []
        while stack:
            cx, cy = stack.pop()
            out.append((cx, cy))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = cx + dx, cy + dy
                if 0 <= nx < S and 0 <= ny < S and self.land[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((nx, ny))
        return out

    @staticmethod
    def _distance(src):
        """Odleglosc (w polach, metryka 8-sasiedztwa) od pol src."""
        d = np.where(src, 0.0, 1e9)
        for _ in range(2):
            for y in range(1, S):
                d[y] = np.minimum(d[y], d[y - 1] + 1)
            for y in range(S - 2, -1, -1):
                d[y] = np.minimum(d[y], d[y + 1] + 1)
            d = d.T.copy()
            for y in range(1, S):
                d[y] = np.minimum(d[y], d[y - 1] + 1)
            for y in range(S - 2, -1, -1):
                d[y] = np.minimum(d[y], d[y + 1] + 1)
            d = d.T.copy()
        return d

    def _biomes(self):
        """Biomy z klas obrazka: pustynia, snieg, wulkan, dzungla, skaly (wg krainy), trawa i lasy."""
        cls = self.cls
        # krainy bez prostych krawedzi: wspolrzedne przesuniete szumem (granica faluje o kilkadziesiat pol)
        xs = self.xs + 120 * fbm(120, 3, 60)
        ys = self.ys + 120 * fbm(121, 3, 60)

        def box(b):
            return (xs >= b[0]) & (xs < b[2]) & (ys >= b[1]) & (ys < b[3])
        dense = (cls == 'J').astype(float)
        for _ in range(4):
            dense = (dense + np.roll(dense, 2, 0) + np.roll(dense, -2, 0) + np.roll(dense, 2, 1) + np.roll(dense, -2, 1)) / 5
        sand = (cls == 'S').astype(float)
        for _ in range(6):
            sand = (sand + np.roll(sand, 3, 0) + np.roll(sand, -3, 0) + np.roll(sand, 3, 1) + np.roll(sand, -3, 1)) / 5
        out = np.empty((S, S), dtype=object)
        out[:] = 'temperate'
        out[dense > 0.3] = 'forest'
        jungle = box(JUNGLE)
        out[jungle & (dense > 0.2)] = 'jungle'
        out[jungle & (dense > 0.2) & (fbm(110, 3, 30) > 0.35)] = 'swamp'
        rock = (cls == 'R') | (cls == 'V')
        out[rock] = 'hills'
        out[sand > 0.35] = 'savanna'
        out[(cls == 'S') & (sand > 0.25)] = 'desert'
        rocky = box(ROCKY_LANDS)
        out[rocky & rock] = 'shadow'
        out[rocky & ~rock & (fbm(111, 3, 26) > 0.1) & (sand < 0.3)] = 'cursed'
        snow = (cls == 'N') | ((ys < SNOW_TOP) & (xs > 470) & rock)
        out[snow] = 'snow'
        out[(ys < SNOW_TOP + 25) & (xs > 470) & ~snow & (out != 'jungle')] = 'taiga'
        out[box(VOLCANO_BOX) & ((cls == 'V') | (cls == 'L') | (cls == 'G') | (cls == 'R'))] = 'volcanic'
        return out

    # ---------------------------------------------------------------- woda
    def meander(self, pts, rng, amp=3.0):
        """Rozbija lamana na krete odcinki (przesuniecie srodkow, kilka poziomow)."""
        out = [tuple(map(float, p)) for p in pts]
        for level in range(4):
            nxt = [out[0]]
            for (ax, ay), (bx, by) in zip(out, out[1:]):
                mx, my = (ax + bx) / 2, (ay + by) / 2
                ln = math.hypot(bx - ax, by - ay)
                if ln > 3:
                    nx, ny = -(by - ay) / ln, (bx - ax) / ln
                    off = rng.uniform(-1, 1) * min(amp, ln * 0.35)
                    mx, my = mx + nx * off, my + ny * off
                nxt += [(mx, my), (bx, by)]
            out = nxt
            amp *= 0.6
        return out

    def paint_line(self, pts, width_fn, mark):
        n = len(pts)
        for i, ((ax, ay), (bx, by)) in enumerate(zip(pts, pts[1:])):
            steps = int(max(abs(bx - ax), abs(by - ay)) * 2) + 1
            for k in range(steps + 1):
                t = k / steps
                x, y = ax + (bx - ax) * t, ay + (by - ay) * t
                w = width_fn((i + t) / max(1, n - 1))
                r = int(math.ceil(w))
                for yy in range(int(y) - r, int(y) + r + 1):
                    for xx in range(int(x) - r, int(x) + r + 1):
                        if 0 <= xx < S and 0 <= yy < S and (xx - x) ** 2 + (yy - y) ** 2 <= w * w + 0.3:
                            mark[yy, xx] = True

    def water_features(self, protect):
        rng = random.Random(20261030)
        for (cx, cy, rx, ry) in LAKES:
            n = fbm(108 + cx, 2, 6)
            m = ((self.xs - cx) / rx) ** 2 + ((self.ys - cy) / ry) ** 2 + 0.5 * n <= 1.0
            self.lake |= m & self.land & ~protect
        for name, pts, end in RIVERS:
            line = self.meander(pts, rng, 4.0)
            lo, hi = (0.6, 1.6) if end != 'join' else (0.5, 1.0)
            mark = np.zeros((S, S), bool)
            self.paint_line(line, lambda t: lo + (hi - lo) * t, mark)
            if end == 'delta':
                # ujscie: 3 rozchodzace sie odnogi z wysepkami miedzy nimi
                (ax, ay), (bx, by) = line[-8], line[-1]
                ln = math.hypot(bx - ax, by - ay) or 1
                ux, uy = (bx - ax) / ln, (by - ay) / ln
                for side in (-1, 1):
                    branch = [(ax, ay), (ax + ux * 6 - uy * side * 5, ay + uy * 6 + ux * side * 5),
                              (bx + ux * 4 - uy * side * 11, by + uy * 4 + ux * side * 11)]
                    self.paint_line(self.meander(branch, rng, 2.0), lambda t: 0.6 + 0.5 * t, mark)
            self.river |= mark & ~protect
            self.rivers.append((name, line))
        self.water = ~self.land | self.river | self.lake

    def connect_water(self, groups, blocked):
        """Woda klockow (port, rzeka miasta) musi laczyc sie z morzem albo rzeka: kreta struga najkrotsza droga
        przez lad. groups: listy pol (x, y) wody klocka; blocked: pola, przez ktore struga nie moze przejsc."""
        rng = random.Random(20261033)
        for cells in groups:
            src = set(cells)
            prev = {p: None for p in src}
            q = deque(src)
            end = None
            while q:
                x, y = q.popleft()
                if (x, y) not in src and self.water[y, x]:
                    end = (x, y)
                    break
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and (nx, ny) not in prev and not blocked[ny, nx]:
                        prev[(nx, ny)] = (x, y)
                        q.append((nx, ny))
            path = []
            p = end
            while p is not None and p not in src:
                path.append(p)
                p = prev[p]
            if not path:
                continue
            path.reverse()
            pts = path[::6] + ([path[-1]] if (len(path) - 1) % 6 else [])
            mark = np.zeros((S, S), bool)
            line = self.meander(pts, rng, 2.0) if len(pts) > 1 else [tuple(map(float, pts[0]))] * 2
            self.paint_line(line, lambda t: 1.1, mark)
            for (x, y) in path:
                mark[y, x] = True
            mark &= ~blocked
            self.river |= mark
            self.land &= ~mark
        self.water = ~self.land | self.river | self.lake
        self.coast_dist = self._distance(~self.land)

    # ---------------------------------------------------------------- wyspy
    def islands(self):
        out = []
        for i, ((cx, cy), (rx, ry), kind) in enumerate(ISLANDS):
            n = fbm(200 + i, 3, 8)
            ang = (i * 0.7) % math.pi
            dx, dy = self.xs - cx, self.ys - cy
            u = dx * math.cos(ang) + dy * math.sin(ang)
            v = -dx * math.sin(ang) + dy * math.cos(ang)
            m = (u / rx) ** 2 + (v / ry) ** 2 + 0.45 * n <= 1.0
            m &= ~self.land
            self.land |= m
            self.water &= ~m
            out.append((kind, m, (cx, cy)))
        self.coast_dist = self._distance(~self.land)
        return out


# ---------------------------------------------------------------------- kafle powierzchni
MOUNTAIN_LEVEL = 0.42
WALL_OF = {'snow': ('snow', 'snow_mountain'), 'taiga': ('snow', 'snow_mountain'), 'desert': ('sand', 'sand_cliff'),
           'savanna': ('sand', 'sand_cliff'), 'volcanic': ('ash', 'mountain'), 'jungle': ('jungle_grass', 'mountain'),
           'swamp': ('jungle_grass', 'mountain'), 'cursed': ('blighted_grass', 'mountain'),
           'shadow': ('dark_sand', 'sand_cliff')}


def ground_cell(biome, r, wood, n2):
    """Kafel ladu wg biomu; wood = gestosc lasu w tym miejscu (0..1), n2 = drobny szum.
    Roslinnosc dopasowana do klimatu (2026-10-28): oszronione swierki i krzaki na lodach, drzewa i paprocie dzungli,
    suche krzewy na pustyni, jesienne drzewa i grzyby w lasach, zweglone drzewa na ziemiach wulkanu."""
    v = r.random()
    if biome == 'snow':
        if v < 0.05 + 0.25 * wood:
            return ('snow', '-', 'frost_pine' if r.random() < 0.85 else 'pine', '-')
        if v < 0.27 * wood + 0.07:
            o = r.random()
            return ('snow', '-', 'snow_rock' if o < 0.15 else 'frost_bush' if o < 0.3 else 'ice_crystal' if o < 0.33 else '-', '-')
        return ('snow', 'snow_tuft' if r.random() < 0.08 else '-', '-', '-')
    if biome == 'taiga':
        g = 'snow' if n2 > 0.1 else 'grass'
        if v < 0.08 + 0.4 * wood:
            o = r.random()
            if g == 'snow':
                return (g, '-', 'frost_pine' if o < 0.7 else 'pine' if o < 0.9 else 'frost_bush', '-')
            return (g, '-', 'pine' if o < 0.75 else 'tree' if o < 0.9 else 'bush', '-')
        return (g, 'snow_tuft' if g == 'snow' and r.random() < 0.1 else ('tall_grass' if r.random() < 0.08 else '-'), '-', '-')
    if biome in ('temperate', 'hills', 'forest'):
        dens = {'temperate': 0.04 + 0.45 * wood, 'hills': 0.03 + 0.2 * wood, 'forest': 0.12 + 0.62 * wood}[biome]
        if v < dens:
            o = r.random()
            autumn = 0.1 if biome == 'forest' else 0.05
            return ('grass', '-', 'autumn_tree' if o < autumn else 'tree' if o < 0.82 else 'bush', '-')
        if biome == 'hills' and r.random() < 0.02:
            return ('grass', '-', 'rock', '-')
        w = r.random()
        if biome == 'forest' or wood > 0.55:
            return ('grass', 'mushrooms' if w < 0.025 else 'flowers' if w < 0.05 else 'tall_grass' if w < 0.2 else '-', '-', '-')
        return ('grass', 'flowers' if w < 0.05 else 'tall_grass' if w < 0.17 else '-', '-', '-')
    if biome == 'desert':
        if v < 0.012:
            return ('sand', '-', 'cactus', '-')
        if v < 0.02:
            return ('sand', '-', 'sandstone_rock', '-')
        if v < 0.028:
            return ('sand', '-', 'dry_bush', '-')
        w = r.random()
        return ('dark_sand' if n2 > 0.35 else 'sand', 'dry_grass' if w < 0.03 else 'dry_shrub' if w < 0.045 else '-', '-', '-')
    if biome == 'savanna':
        g = 'grass' if n2 > 0.15 else 'sand'
        if v < 0.03 + 0.08 * wood:
            return (g, '-', 'palm' if g == 'sand' else ('dry_bush' if r.random() < 0.6 else 'bush'), '-')
        return (g, 'dry_grass' if r.random() < 0.3 else '-', '-', '-')
    if biome == 'jungle':
        if v < 0.1 + 0.55 * wood:
            o = r.random()
            return ('jungle_grass', '-', 'palm' if o < 0.15 else 'jungle_tree' if o < 0.72 else 'jungle_bush', '-')
        w = r.random()
        return ('jungle_grass', 'fern' if w < 0.12 else 'jungle_plant' if w < 0.2 else '-', '-', '-')
    if biome == 'swamp':
        if n2 > 0.25 and v < 0.6:
            return ('swamp', '-', '-', '-')
        if v < 0.15:
            o = r.random()
            return ('jungle_grass', '-', 'dead_tree' if o < 0.4 else 'jungle_tree' if o < 0.8 else 'jungle_bush', '-')
        w = r.random()
        return ('jungle_grass', 'reeds' if w < 0.12 else 'fern' if w < 0.22 else '-', '-', '-')
    if biome == 'cursed':
        if v < 0.06 + 0.4 * wood:
            return ('blighted_grass', '-', 'dead_tree' if r.random() < 0.6 else 'tree', '-')
        return ('blighted_grass', 'tall_grass' if r.random() < 0.12 else ('bones' if r.random() < 0.01 else '-'), '-', '-')
    if biome == 'shadow':
        if v < 0.02:
            return ('dark_sand', '-', 'sandstone_rock', '-')
        if v < 0.035:
            return ('dark_sand', '-', 'dead_tree' if r.random() < 0.5 else 'burnt_tree', '-')
        return ('dark_sand' if n2 > -0.3 else 'ash', 'bones' if r.random() < 0.02 else '-', '-', '-')
    if biome == 'volcanic':
        if v < 0.03:
            return ('ash', '-', 'obsidian_rock', '-')
        if v < 0.05:
            return ('ash', '-', 'burnt_tree', '-')
        return ('ash' if n2 > -0.2 else 'dark_sand', 'debris' if r.random() < 0.02 else '-', '-', '-')
    return ('grass', '-', '-', '-')


def paint(T, canvas, skip):
    """Kafle powierzchni z terenu. skip(x, y) - pola zostawione (kopia Varden)."""
    r = random.Random(20261031)
    wood = (fbm(301, 4, 18) + 1) / 2
    n2 = fbm(302, 3, 9)
    jitter = random.Random(20261032)
    for y in range(S):
        for x in range(S):
            if skip(x, y):
                continue
            jx, jy = x + jitter.randint(-2, 2), y + jitter.randint(-2, 2)
            jx, jy = min(S - 1, max(0, jx)), min(S - 1, max(0, jy))
            biome = T.biome[jy, jx]
            if T.water[y, x]:
                frozen = T.lake[y, x] and biome == 'snow'
                canvas.put(x, y, ('ice' if frozen else 'water', '-', '-', '-'))
                continue
            if T.lava[y, x]:
                canvas.put(x, y, ('lava', '-', '-', '-'))
                continue
            if T.ridge[y, x] + 0.12 * n2[y, x] > MOUNTAIN_LEVEL:
                g, wall = WALL_OF.get(biome, ('grass', 'mountain'))
                canvas.put(x, y, (g, '-', '-', wall))
                continue
            sea_near = T.coast_dist[y, x] <= 1.5 + 0.8 * n2[y, x]
            if sea_near and biome not in ('snow', 'taiga', 'volcanic'):
                canvas.put(x, y, ('sand', 'shells' if r.random() < 0.05 else '-', 'palm' if biome == 'jungle' and r.random() < 0.08 else '-', '-'))
                continue
            canvas.put(x, y, ground_cell(biome, r, wood[y, x], n2[y, x]))
