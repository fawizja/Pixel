"""Kontynent swiata (2026-10-23, wg szkicu Darka): ksztalt ladu, wysokosci, biomy, gory z przeleczami, rzeki
z delta, jeziora i wyspy. Wynik to pola terenu (krotki kafli town.py) i mapy pomocnicze dla miast i podziemi.

Ksztalt ladu: zgrubna maska 64x64 (COARSE, kazde pole = 6x6 kafli) rozmyta i poszarpana szumem - podobny
zarys do szkicu, wlasna linia brzegowa. Wszystko deterministyczne (stale ziarna).
"""
import math
import random
from collections import deque

import numpy as np

S = 448                     # rozmiar swiata (kwadrat)
CELL = S // 64

COARSE = """\
................................................................
................................................................
................................................................
................................................................
................................................................
................................................................
............................###.................................
..........................#######...............................
........................##########.###..........................
...........#############################........................
.........###############################........................
........################################.##.....................
.......####################################.....................
.......######################################...................
.......###############################################..........
.......################################################.........
.......#################################################........
.......#################################################........
........################################################........
........################################################........
........################################################........
........################################################........
.........###############################################........
..........##############################################........
............####..######################################........
.................#######################################........
.................#######################################........
..................######################################........
...............##.#####################################.........
................#######################################.........
..............##########################################........
..............##########################################........
..............##########################################........
.............###########################################........
.............###########################################........
.......################################################.........
.........#############################################..........
............##..###################################.............
.................##############################.................
.................##############################.................
.................##############################.................
.................##############################.................
.................#############################..................
.................#############################..................
..................############################..................
...................###########################..................
...................#########################....................
...................##################.##........................
...................##################...........................
.................####################...........................
...............#.####################...........................
.......#...#.##..####################...........................
........#..#.#######################............................
........############################............................
.........##.########################............................
..........###########################...........................
.............#############.##..#####............................
................................................................
................................................................
................................................................
................................................................
................................................................
................................................................
................................................................
"""

# --- miejsca (wspolrzedne swiata 448 x 448) ---
VOLCANO = (222, 72)                 # krater wulkanu (polnoc)
QAS_HILLS = (217, 250)              # wzgorza stolicy (srodek)

# lancuchy gor: lamane (x, y) i szerokosc; przelecze to miejsca, gdzie gora sie urywa
RANGES = [
    ([(175, 82), (198, 70), (222, 72), (247, 75), (275, 89)], 8.2, 'north'),
    ([(275, 93), (268, 121), (278, 152), (292, 187), (306, 212)], 6.4, 'spine'),
    ([(170, 149), (182, 175), (175, 203), (165, 222)], 5.8, 'west'),
    ([(60, 80), (86, 76), (112, 80), (138, 72)], 6.0, 'snow'),
    ([(196, 318), (226, 312), (250, 324)], 4.0, 'south'),
]
PASSES = [(271, 107), (285, 170), (175, 163), (172, 212), (100, 78), (226, 312), (205, 71)]

# biomy: punkty wzorcowe (Voronoi w zakrzywionych wspolrzednych - granice nieregularne)
BIOME_SEEDS = [
    ('snow', 82, 93), ('snow', 128, 76), ('snow', 70, 130), ('snow', 140, 110), ('taiga', 163, 82),
    ('taiga', 170, 131), ('taiga', 120, 160), ('temperate', 140, 190), ('temperate', 210, 187),
    ('temperate', 236, 226), ('hills', 217, 250), ('hills', 196, 262), ('temperate', 128, 240),
    ('forest', 163, 292), ('forest', 222, 315), ('forest', 150, 330), ('cursed', 90, 372), ('cursed', 120, 395),
    ('temperate', 196, 360), ('forest', 240, 380), ('desert', 287, 287), ('desert', 270, 270),
    ('savanna', 262, 240), ('jungle', 315, 128), ('jungle', 350, 120), ('jungle', 360, 190), ('jungle', 300, 196),
    ('jungle', 372, 232), ('swamp', 336, 228), ('shadow', 312, 262), ('shadow', 300, 300),
    ('temperate', 245, 140), ('volcanic', 222, 72), ('volcanic', 200, 92), ('volcanic', 248, 88),
    ('taiga', 198, 112),
]

# rzeki: punkty kontrolne od zrodla do ujscia (meandry i delta dodawane przy rysowaniu)
RIVERS = [
    ('Wielka Rzeka', [(206, 104), (196, 131), (201, 163), (186, 201), (172, 240), (176, 280), (190, 312),
                      (192, 338)], 'join'),
    ('Rzeka Dzungli', [(282, 104), (294, 136), (306, 160), (318, 170)], 'join'),
    ('Lodowy Potok', [(52, 142), (70, 152), (88, 160), (102, 176)], 'mouth'),
    ('Rzeka Wzgorz', [(252, 236), (262, 268), (266, 300), (276, 318), (296, 334)], 'delta'),
    ('Strumien Zachodni', [(131, 212), (152, 236), (172, 240)], 'join'),
    ('Rzeka Bagien', [(334, 214), (350, 240), (366, 262), (384, 278)], 'delta'),
    ('Ujscie Varden', [(193, 388), (194, 394), (196, 400)], 'mouth'),
]
LAKES = [(198, 208, 6, 5), (140, 275, 6, 4), (360, 70, 5, 4), (150, 142, 5, 3)]
ISLANDS = [  # srodek, promienie, rodzaj (male wyspy z przystaniami; duze wyspy to kopie ze starego swiata)
    ((26, 300), (8, 12), 'temperate'), ((150, 30), (12, 6), 'snow'), ((428, 120), (8, 15), 'jungle'),
    ((420, 360), (7, 7), 'beach'), ((120, 432), (9, 6), 'beach'),
]


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


def coarse_field():
    rows = COARSE.strip('\n').split('\n')
    g = np.array([[1.0 if ch == '#' else 0.0 for ch in r] for r in rows])
    # dwuliniowe powiekszenie do S x S + rozmycie (gladkie brzegi zamiast schodkow 6x6)
    ys, xs = np.mgrid[0:S, 0:S]
    gx, gy = (xs + 0.5) / CELL - 0.5, (ys + 0.5) / CELL - 0.5
    x0, y0 = np.clip(gx.astype(int), 0, 62), np.clip(gy.astype(int), 0, 62)
    fx, fy = np.clip(gx - x0, 0, 1), np.clip(gy - y0, 0, 1)
    f = (g[y0, x0] * (1 - fx) * (1 - fy) + g[y0, x0 + 1] * fx * (1 - fy) + g[y0 + 1, x0] * (1 - fx) * fy
         + g[y0 + 1, x0 + 1] * fx * fy)
    for _ in range(3):
        f = (f + np.roll(f, 1, 0) + np.roll(f, -1, 0) + np.roll(f, 1, 1) + np.roll(f, -1, 1)) / 5
    return f


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
        n1, n2 = fbm(101, 5, 32), fbm(102, 4, 20)
        self.land = (coarse_field() + 0.22 * n1 + 0.08 * n2) > 0.5
        self.land[:2, :] = self.land[-2:, :] = False
        self.land[:, :2] = self.land[:, -2:] = False
        self._drop_specks()
        if force_land is not None:
            self.land |= force_land
        if force_water is not None:
            self.land &= ~force_water
        self.coast_dist = self._distance(~self.land)
        # wysokosc: odleglosc od morza + pasma gor + wzgorza stolicy + wulkan + szum
        e = np.minimum(self.coast_dist / 30.0, 1.0) * 0.35 + 0.18 * fbm(103, 4, 28)
        self.ridge = np.zeros((S, S))
        wob = fbm(104, 3, 14)
        for pts, width, _name in RANGES:
            d = polyline_dist(pts, xs + 6 * wob, ys - 6 * wob)
            self.ridge = np.maximum(self.ridge, np.exp(-(d / width) ** 2) * (0.75 + 0.35 * fbm(105, 3, 10)))
        for (px, py) in PASSES:
            self.ridge *= 1 - np.exp(-((xs - px) ** 2 + (ys - py) ** 2) / 30.0)
        hills = np.exp(-((xs - QAS_HILLS[0]) ** 2 / 900 + (ys - QAS_HILLS[1]) ** 2 / 700))
        cone = np.exp(-((xs - VOLCANO[0]) ** 2 + (ys - VOLCANO[1]) ** 2) / 120.0)
        self.elev = e + self.ridge * 0.9 + hills * 0.25 + cone * 0.6
        self.biome = self._biomes()
        self.water = ~self.land
        self.river = np.zeros((S, S), bool)
        self.lake = np.zeros((S, S), bool)
        self.rivers = []

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
        wx = self.xs + 14 * fbm(106, 3, 26)
        wy = self.ys + 14 * fbm(107, 3, 26)
        best = np.full((S, S), 1e9)
        out = np.empty((S, S), dtype=object)
        out[:] = 'temperate'
        for name, bx, by in BIOME_SEEDS:
            d = np.hypot(wx - bx, wy - by)
            if name == 'desert':
                d = d * 1.15
            mask = d < best
            out[mask] = name
            best = np.minimum(best, d)
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
    """Kafel ladu wg biomu; wood = gestosc lasu w tym miejscu (0..1), n2 = drobny szum."""
    v = r.random()
    if biome == 'snow':
        if v < 0.05 + 0.25 * wood:
            return ('snow', '-', 'pine', '-')
        if v < 0.27 * wood + 0.07:
            return ('snow', '-', 'snow_rock' if r.random() < 0.15 else '-', '-')
        return ('snow', 'snow_tuft' if r.random() < 0.08 else '-', '-', '-')
    if biome == 'taiga':
        g = 'snow' if n2 > 0.1 else 'grass'
        if v < 0.08 + 0.4 * wood:
            return (g, '-', 'pine' if r.random() < 0.8 else 'tree', '-')
        return (g, 'snow_tuft' if g == 'snow' and r.random() < 0.1 else ('tall_grass' if r.random() < 0.08 else '-'), '-', '-')
    if biome in ('temperate', 'hills', 'forest'):
        dens = {'temperate': 0.04 + 0.45 * wood, 'hills': 0.03 + 0.2 * wood, 'forest': 0.12 + 0.62 * wood}[biome]
        if v < dens:
            return ('grass', '-', 'tree' if r.random() < 0.85 else 'bush', '-')
        if biome == 'hills' and r.random() < 0.02:
            return ('grass', '-', 'rock', '-')
        w = r.random()
        return ('grass', 'flowers' if w < 0.04 else 'tall_grass' if w < 0.16 else '-', '-', '-')
    if biome == 'desert':
        if v < 0.012:
            return ('sand', '-', 'cactus', '-')
        if v < 0.02:
            return ('sand', '-', 'sandstone_rock', '-')
        return ('dark_sand' if n2 > 0.35 else 'sand', 'dry_grass' if r.random() < 0.03 else '-', '-', '-')
    if biome == 'savanna':
        g = 'grass' if n2 > 0.15 else 'sand'
        if v < 0.03 + 0.08 * wood:
            return (g, '-', 'palm' if g == 'sand' else 'bush', '-')
        return (g, 'dry_grass' if r.random() < 0.3 else '-', '-', '-')
    if biome == 'jungle':
        if v < 0.1 + 0.55 * wood:
            o = r.random()
            return ('jungle_grass', '-', 'palm' if o < 0.3 else 'tree' if o < 0.85 else 'bush', '-')
        return ('jungle_grass', 'fern' if r.random() < 0.18 else '-', '-', '-')
    if biome == 'swamp':
        if n2 > 0.25 and v < 0.6:
            return ('swamp', '-', '-', '-')
        if v < 0.15:
            return ('jungle_grass', '-', 'dead_tree' if r.random() < 0.5 else 'tree', '-')
        return ('jungle_grass', 'fern' if r.random() < 0.2 else '-', '-', '-')
    if biome == 'cursed':
        if v < 0.06 + 0.4 * wood:
            return ('blighted_grass', '-', 'dead_tree' if r.random() < 0.6 else 'tree', '-')
        return ('blighted_grass', 'tall_grass' if r.random() < 0.12 else ('bones' if r.random() < 0.01 else '-'), '-', '-')
    if biome == 'shadow':
        if v < 0.02:
            return ('dark_sand', '-', 'sandstone_rock', '-')
        if v < 0.035:
            return ('dark_sand', '-', 'dead_tree', '-')
        return ('dark_sand' if n2 > -0.3 else 'ash', 'bones' if r.random() < 0.02 else '-', '-', '-')
    if biome == 'volcanic':
        if v < 0.03:
            return ('ash', '-', 'obsidian_rock', '-')
        if v < 0.05:
            return ('ash', '-', 'dead_tree', '-')
        return ('ash' if n2 > -0.2 else 'dark_sand', '-', '-', '-')
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
            if T.ridge[y, x] + 0.12 * n2[y, x] > MOUNTAIN_LEVEL:
                g, wall = WALL_OF.get(biome, ('grass', 'mountain'))
                canvas.put(x, y, (g, '-', '-', wall))
                continue
            sea_near = T.coast_dist[y, x] <= 1.5 + 0.8 * n2[y, x]
            if sea_near and biome not in ('snow', 'taiga', 'volcanic'):
                canvas.put(x, y, ('sand', 'shells' if r.random() < 0.05 else '-', 'palm' if biome == 'jungle' and r.random() < 0.08 else '-', '-'))
                continue
            canvas.put(x, y, ground_cell(biome, r, wood[y, x], n2[y, x]))
