"""Trzy krainy wzgorz z podziemiami (Darek 2026-11-12): Wzgorza Goblinow, Wzgorza Hydr i Wzgorza Smokow.

"Zrob trzy hills jak w Tibii: powierzchnia z pagorkami i pod nia podziemia." Kazda kraina to organiczny, nieregularny
skrawek ladu zlozony z kilku zachodzacych na siebie pagorkow (lancuch kol z falujacym brzegiem), a nie prostokat:
  * powierzchnia (0): grzbiety gor (``mountain``/``sand_cliff``) przecinane krecacymi dolinami, laczone ze soba i z
    trzema szczelinami w obrzezu (tam wchodzi sie z zewnatrz), stawy, drzewa, oboz goblinow albo ogniska smokow;
    spawny rozlozone w dolinach (odstep >= 10 pol), wejscia do jaskin u podnoza grzbietow;
  * podziemia: kazde pietro to siec komor polaczonych krecacymi tunelami (drzewo rozpinajace + petle), slepe komory ze
    skrzyniami, kilka zejsc na nizsze pietro; potwory rosna z glebokoscia i dlugoscia drogi od wejscia.
  Wzgorza Goblinow   - kontynent glowny (darmowy lad), -1..-3, gobliny od slabych do herszta.
  Wzgorza Hydr       - duza wyspa na wschodzie (tylko statkiem: premium albo promocja), -1..-5, hydry, jaszczury, wezyce.
  Wzgorza Smokow     - pustynia kontynentu glownego, dalej na polnocny zachod od Qasirah, -1..-4, od piskleci po smoki
                       skalne, a na dnie najsilniejsze smoki.
Wszystko z wlasnym ziarnem, po kanalach (realms.split) i po zamku; sprawdza Plan.check (droga od wejsc do kazdego spawnu,
schodow i skrzyni) oraz test HillsTest.
"""
import heapq
import math
import random
from collections import deque

import numpy as np

import features as F
import isles as I
import structures as St

NONE = '-'
SEED = 20261121
PAD = 6
GRAVE = ('water', 'deep_water', 'swamp', 'lava', 'chasm')
MOUNT = ('mountain', 'snow_mountain', 'sand_cliff')

# ---------------------------------------------------------------------- style pieter
F.CAVE_STYLES.setdefault('goblin_den', ('dirt', 'cave_wall', ['bones', 'debris', 'mushrooms'],
                                        ['barrel', 'crate', 'rubble', 'firewood'], 'wall_torch'))
F.CAVE_STYLES.setdefault('goblin_mine', ('cave_floor', 'cave_wall', ['debris'], ['rock', 'rubble', 'lumber', 'crate'],
                                         'wall_torch'))
F.CAVE_STYLES.setdefault('goblin_hold', ('stone_floor', 'stone_wall', ['bones', 'debris', 'blood'],
                                         ['barrel', 'crate', 'rubble', 'sack'], 'wall_torch'))
F.CAVE_STYLES.setdefault('fen_cave', ('dark_sand', 'mossy_wall', ['reeds', 'shells', 'mushrooms'], ['rock', 'rubble'],
                                      'glow_moss'))
F.CAVE_STYLES.setdefault('moss_grot', ('cave_floor', 'mossy_wall', ['glow_moss', 'mushrooms'], ['rock', 'rubble', 'crystal'],
                                       'glow_moss'))
F.CAVE_STYLES.setdefault('venom_pit', ('blighted_grass', 'mossy_wall', ['bones', 'mushrooms', 'cobweb'],
                                       ['rock', 'rubble', 'jungle_statue'], 'glow_moss'))
F.CAVE_STYLES.setdefault('serpent_lair', ('tomb_floor', 'mossy_wall', ['bones', 'cobweb', 'blood'],
                                          ['rubble', 'jungle_statue', 'broken_wall'], 'wall_torch'))
F.CAVE_STYLES.setdefault('dragon_den', ('sandstone_floor', 'sandstone_wall', ['bones', 'cobweb', 'debris'],
                                        ['rubble', 'sandstone_rock'], 'wall_torch'))
F.CAVE_STYLES.setdefault('ember_den', ('ash', 'obsidian_wall', ['bones', 'debris', 'blood'], ['rubble', 'obsidian_rock'],
                                       'wall_torch'))
F.CAVE_STYLES.setdefault('dragon_hoard', ('abyss_floor', 'obsidian_wall', ['bones', 'blood', 'debris'],
                                          ['rubble', 'obsidian_rock', 'statue'], 'wall_torch'))
F.WATER_STYLES.update({'fen_cave', 'moss_grot', 'venom_pit'})
F.LAVA_STYLES.update({'ember_den', 'dragon_hoard'})

# ---------------------------------------------------------------------- konfiguracja krain
GOBLIN, HYDRA, DRAGON = 'goblin', 'hydra', 'dragon'
HILLS = {
    GOBLIN: dict(
        name='Wzgórza Goblinów', under='Nory Goblinów', near=(608, 446), pin=(608, 446), radius=45, L=46, R=20, n=5, mount=0.36,
        entrances=3, surface_n=9, surface_levels=(8, 40), min_temple=70, island=False,
        surface=['goblin', 'goblin', 'goblin', 'goblin_shaman', 'goblin_scrounger', 'goblin_scrounger', 'goblin_knifer',
                 'goblin_shaman', 'goblin_knifer'],
        floors={
            -1: ('goblin_den', ['goblin', 'goblin_scrounger', 'goblin_scrounger', 'goblin_shaman', 'goblin_scrounger'], (3, 4), (8, 30)),
            -2: ('goblin_mine', ['goblin_scrounger', 'goblin_shaman', 'goblin_knifer', 'goblin_knifer', 'cave_troll'], (3, 4),
                 (14, 45)),
            -3: ('goblin_hold', ['goblin_knifer', 'goblin_shaman', 'goblin_warchief', 'cave_troll', 'goblin_warchief'], (3, 4),
                 (24, 65)),
        },
        chests=[('health_potion', 3), ('mana_potion', 3), ('strong_health_potion', 2), ('strong_mana_potion', 2)],
        stand_in=False),
    HYDRA: dict(
        name='Wzgórza Hydr', under='Legowiska Hydr', near=(922, 390), pin=(922, 411), radius=60, L=52, R=21, n=6, mount=0.32,
        entrances=4, surface_n=13, surface_levels=(80, 230), min_temple=60, island=True,
        surface=['crocodile', 'jungle_serpent', 'marsh_lizard', 'marsh_lizard', 'jungle_serpent', 'marsh_lizard',
                 'lizard_hexer', 'marsh_lizard', 'lizard_hexer', 'bog_hydra', 'marsh_lizard', 'lizard_hexer', 'bog_hydra'],
        floors={
            -1: ('fen_cave', ['marsh_lizard', 'jungle_serpent', 'lizard_hexer', 'bog_hydra', 'bog_wraith', 'lizard_hexer'], (3, 5),
                 (85, 220)),
            -2: ('flooded', ['marsh_lizard', 'lizard_hexer', 'bog_hydra', 'swamp_hornet', 'bog_hydra'], (3, 5), (120, 300)),
            -3: ('moss_grot', ['lizard_hexer', 'bog_hydra', 'bog_naga', 'bog_hydra', 'bog_naga'], (3, 5), (190, 380)),
            -4: ('venom_pit', ['bog_naga', 'venom_hydra', 'bog_hydra', 'venom_hydra', 'stone_gorgon'], (4, 5), (250, 470)),
            -5: ('serpent_lair', ['venom_hydra', 'bog_naga', 'stone_gorgon', 'venom_hydra', 'stone_gorgon'], (4, 6),
                 (320, 560)),
        },
        chests=[('great_health_potion', 3), ('great_mana_potion', 3), ('great_spirit_potion', 2),
                ('ultimate_health_potion', 2), ('ultimate_mana_potion', 2)],
        stand_in=True),
    DRAGON: dict(
        name='Wzgórza Smoków', under='Smocze Pieczary', near=(130, 288), pin=(119, 283), radius=80, L=52, R=22, n=5, mount=0.34,
        entrances=3, surface_n=11, surface_levels=(40, 190), min_temple=110, island=False,
        surface=['dragon_whelp', 'dragon_whelp', 'dragon_whelp', 'dragon_whelp', 'scale_dragon', 'scale_dragon', 'scale_dragon',
                 'scale_dragon', 'scale_dragon', 'scale_dragon', 'scale_dragon'],
        floors={
            -1: ('dragon_den', ['dragon_whelp', 'dragon_whelp', 'scale_dragon', 'scale_dragon'], (2, 3), (50, 170)),
            -2: ('dragon_den', ['dragon_whelp', 'scale_dragon', 'scale_dragon', 'crag_dragon', 'crag_dragon'], (2, 3), (120, 280)),
            -3: ('ember_den', ['scale_dragon', 'crag_dragon', 'crag_dragon', 'dune_drake', 'dune_drake'], (2, 3), (230, 450)),
            -4: ('dragon_hoard', ['crag_dragon', 'dune_drake', 'magma_drake', 'magma_drake', 'bone_dragon', 'bone_dragon'],
                 (2, 3), (400, 900)),
        },
        chests=[('strong_health_potion', 3), ('great_health_potion', 3), ('great_mana_potion', 2),
                ('ultimate_health_potion', 2)],
        stand_in=False),
}

# podziemia zarezerwowane przed lochami dziczy (generate_continent): wokol miejsc, w ktorych budowane sa krainy
UNDER_RESERVED = [(HILLS[k]['near'][0] - 95, HILLS[k]['near'][1] - 85, HILLS[k]['near'][0] + 95, HILLS[k]['near'][1] + 85)
                  for k in (GOBLIN, HYDRA, DRAGON)]


# ====================================================================== szum, kropla, geometria
def vnoise(h, w, scale, seed):
    """Gladki szum wartosci 0..1 o rozmiarze h x w (bilinearny, wygladzony)."""
    r = np.random.RandomState(seed)
    g = r.uniform(0, 1, (h // scale + 3, w // scale + 3))
    ys, xs = np.arange(h) / scale, np.arange(w) / scale
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a, b = g[y0][:, x0], g[y0][:, x0 + 1]
    c, d = g[y0 + 1][:, x0], g[y0 + 1][:, x0 + 1]
    top = a * (1 - fx[None, :]) + b * fx[None, :]
    bot = c * (1 - fx[None, :]) + d * fx[None, :]
    return top * (1 - fy[:, None]) + bot * fy[:, None]


def dilate(m, r):
    out = m.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r + 1:
                continue
            s = np.zeros_like(m)
            ys0, ys1 = max(0, dy), m.shape[0] + min(0, dy)
            xs0, xs1 = max(0, dx), m.shape[1] + min(0, dx)
            s[ys0:ys1, xs0:xs1] = m[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
            out |= s
    return out


def components(mask):
    """Skladowe 4-spojne maski: lista list pol (x, y), od najwiekszej."""
    h, w = mask.shape
    seen = np.zeros_like(mask)
    out = []
    for y in range(h):
        for x in range(w):
            if mask[y, x] and not seen[y, x]:
                comp = [(x, y)]
                seen[y, x] = True
                q = deque([(x, y)])
                while q:
                    a, b = q.popleft()
                    for dx, dy in F.DIRS4:
                        n = (a + dx, b + dy)
                        if 0 <= n[0] < w and 0 <= n[1] < h and mask[n[1], n[0]] and not seen[n[1], n[0]]:
                            seen[n[1], n[0]] = True
                            comp.append(n)
                            q.append(n)
                out.append(comp)
    out.sort(key=len, reverse=True)
    return out


def make_shape(seed, cfg):
    """Nieregularny lad z kilku pagorkow: maska ``inside`` (H, W) i indeks pola (0, 0) planu (cx, cy)."""
    r = random.Random(seed)
    L, R, n = cfg['L'], cfg['R'], cfg['n']
    ang = r.uniform(0, math.pi)
    circles = []
    for i in range(n):
        t = (i / (n - 1)) * 2 - 1
        px = t * L / 2
        py = R * 0.55 * math.sin(t * math.pi * r.uniform(0.8, 1.4) + r.uniform(0, 6))
        rad = R * (0.72 + 0.3 * r.random()) * (1 - 0.22 * abs(t))
        circles.append((px * math.cos(ang) - py * math.sin(ang), px * math.sin(ang) + py * math.cos(ang), rad))
    for _ in range(3):
        b = r.choice(circles[:n])
        a = r.uniform(0, 2 * math.pi)
        d = b[2] * 0.95
        circles.append((b[0] + d * math.cos(a), b[1] + d * math.sin(a), b[2] * r.uniform(0.4, 0.62)))
    x0 = min(c[0] - c[2] for c in circles) - 5
    x1 = max(c[0] + c[2] for c in circles) + 5
    y0 = min(c[1] - c[2] for c in circles) - 5
    y1 = max(c[1] + c[2] for c in circles) + 5
    W, H = int(x1 - x0) + 2 * PAD, int(y1 - y0) + 2 * PAD
    cx, cy = int(-x0) + PAD, int(-y0) + PAD
    yy, xx = np.mgrid[0:H, 0:W]
    px, py = xx - cx, yy - cy
    m = np.full((H, W), 1e9)
    for (x, y, rad) in circles:
        m = np.minimum(m, np.hypot(px - x, py - y) - rad)
    noise = vnoise(H, W, 7, seed) * 2 - 1
    noise2 = vnoise(H, W, 3, seed + 1) * 2 - 1
    inside = m < 3.2 * noise + 1.3 * noise2
    comps = components(inside)
    inside = np.zeros_like(inside)
    for (x, y) in comps[0]:
        inside[y, x] = True
    # dziury (jeziorka w srodku) zasypane
    holes = components(~inside)
    for comp in holes[1:]:
        for (x, y) in comp:
            inside[y, x] = True
    return inside, cx, cy


# ====================================================================== wybor miejsca
def _surface_masks(ctx, helpers):
    world = ctx['world']
    nat = helpers['natural']
    S = len(world.floors[0])
    built = np.zeros((S, S), bool)
    water = np.zeros((S, S), bool)
    mount = np.zeros((S, S), bool)
    g = world.floors[0]
    for y in range(S):
        row = g[y]
        for x in range(S):
            c = row[x]
            if c is None or c[0] in GRAVE:
                water[y, x] = True
            elif nat(c):
                continue
            elif c[3] in MOUNT and c[2] == NONE:
                mount[y, x] = True
            else:
                built[y, x] = True
    blocked = np.zeros((S, S), bool)

    def block(a, b, c, d, m=8):
        blocked[max(0, b - m):min(S, d + m + 1), max(0, a - m):min(S, c + m + 1)] = True
    for (a, b, c, d) in list(helpers['town_rects'].values()) + [v['rect'] for v in ctx.get('villages', [])]:
        block(a, b, c, d)
    for (a, b, c, d, z) in ctx['pz']:
        if z == 0:
            block(a, b, c, d)
    for r in ctx['houses']:
        if len(r) > 7 and str(r[7]) == '0':
            block(int(r[3]), int(r[4]), int(r[5]), int(r[6]), 4)
    import strongholds
    for rect in list(strongholds.RESERVED) + [I.CAMP_RESERVED]:
        block(*rect, m=0)
    for (name, a, b, c, d, z0, z1, lo, hi) in ctx.get('regions_new', []):
        if z0 == 0 and name in ('Warownia Gorvalda', 'Twierdza Krwawego Kła', 'Obóz Łowczyń', 'Cyklopolis'):
            block(a, b, c, d, 10)
    for (x, y, z, *_r) in ctx['transitions']:
        if z == 0:
            blocked[max(0, y - 5):y + 6, max(0, x - 5):x + 6] = True
    upper = np.zeros((S, S), bool)
    for z in world.floors:
        if z > 0:
            for y in range(S):
                row = world.floors[z][y]
                for x in range(S):
                    if row[x] is not None:
                        upper[y, x] = True
    for (x, y, *_r) in ctx.get('level_doors', []):
        blocked[max(0, y - 6):y + 7, max(0, x - 6):x + 7] = True
    return built, water, mount, blocked | dilate(upper, 4)


def find_origin(ctx, helpers, cfg, inside, cx, cy, reach, masks, taken, why):
    """Najlepsze polozenie ksztaltu (swiat pola (cx, cy) planu) najblizej cfg['near']: bez zabudowy, drog, wody i gor
    poza kilkoma procentami, z dala od swiatyn i innych krain, z obrzezem osiagalnym z reach."""
    built, water, mount, blocked = masks
    world = ctx['world']
    S = len(world.floors[0])
    H, W = inside.shape
    dil = dilate(inside, 3)
    area = int(inside.sum())
    ring = dil & ~inside
    temples = [p for _n, p in ctx['towns']]
    nx, ny = cfg['near']
    best = None
    rad = cfg['radius']
    # podziemia innych budowli (np. Chodniki pod Warownia) nie moga sasiadowac z krainami: groty nie mogly by sie zlaczyc
    under = [r[1:5] for r in list(ctx['regions']) + list(ctx.get('regions_new', [])) if len(r) > 8 and r[0] == 'Chodniki pod Warownią']
    for dy in range(-rad, rad + 1, 3):
        for dx in range(-rad, rad + 1, 3):
            ox, oy = nx + dx, ny + dy
            x0, y0 = ox - cx, oy - cy
            if x0 < 6 or y0 < 6 or x0 + W > S - 6 or y0 + H > S - 6:
                why['edge'] = why.get('edge', 0) + 1
                continue
            if any(max(abs(t[0] - ox), abs(t[1] - oy)) < cfg['min_temple'] + 20 and t[2] == 0 for t in temples):
                why['temple'] = why.get('temple', 0) + 1
                continue
            if any(x0 - 1 <= u[2] and u[0] <= x0 + W + 1 and y0 - 1 <= u[3] and u[1] <= y0 + H + 1 for u in under):
                why['under'] = why.get('under', 0) + 1
                continue
            sl = (slice(y0, y0 + H), slice(x0, x0 + W))
            if (built[sl] & dil).any() or (blocked[sl] & dil).any():
                why['built'] = why.get('built', 0) + 1
                continue
            wat = int((water[sl] & dil).sum())
            if wat > area * 0.06:
                why['water'] = why.get('water', 0) + 1
                continue
            mt = int((mount[sl] & inside).sum())
            if mt > area * 0.45:
                why['mount'] = why.get('mount', 0) + 1
                continue
            if any(abs(ox - tx) < 70 and abs(oy - ty) < 70 for (tx, ty) in taken):
                why['other'] = why.get('other', 0) + 1
                continue
            if not (reach[sl] & ring).any():
                why['reach'] = why.get('reach', 0) + 1
                continue
            score = math.hypot(dx, dy) + wat * 0.4 + mt / area * 40
            if best is None or score < best[0]:
                best = (score, ox, oy)
    return best


def _reach_mask(world, S, start_cells):
    seen = np.zeros((S, S), bool)
    q = deque()
    for p in start_cells:
        seen[p[1], p[0]] = True
        q.append(p)
    g = world.floors[0]
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            a, b = x + dx, y + dy
            if 0 <= a < S and 0 <= b < S and not seen[b, a] and F.walk(g[b][a]):
                seen[b, a] = True
                q.append((a, b))
    return seen


# ====================================================================== powierzchnia
class Surface:
    """Teren jednej krainy w ukladzie planu: maski (H, W) indeksowane [y, x], pole planu = (x - cx, y - cy)."""

    def __init__(self, cfg, key, seed, inside, cx, cy, rng):
        self.cfg, self.key, self.seed, self.inside, self.cx, self.cy, self.rng = cfg, key, seed, inside, cx, cy, rng
        self.H, self.W = inside.shape
        self.mount = np.zeros_like(inside)
        self.extra = {}          # pola poza kropla (szczeliny wejsc): (x, y) indeksowe -> True
        self.gates = []          # pola poza obrzezem (indeksy)
        self.entrances = []
        self.objs = {}           # (x, y) indeksowe -> (deco, obj)
        self.pool = np.zeros_like(inside)

    def plan(self, x, y):
        return int(x) - self.cx, int(y) - self.cy

    def walk_mask(self):
        return (self.inside | self._extra_mask()) & ~self.mount & ~self.pool

    def _extra_mask(self):
        m = np.zeros_like(self.inside)
        for (x, y) in self.extra:
            m[y, x] = True
        return m

    # ---------------------------------------------------------------- grzbiety i doliny
    def ridges(self):
        H, W, seed = self.H, self.W, self.seed
        field = 0.7 * vnoise(H, W, 9, seed + 2) + 0.3 * vnoise(H, W, 4, seed + 3)
        vals = np.sort(field[self.inside])
        thr = vals[int(len(vals) * (1 - self.cfg['mount']))]
        self.mount = self.inside & (field >= thr)
        # srodek krainy jest doliną (tam ruszaja drogi): wokol (0, 0) zawsze wolno
        yy, xx = np.mgrid[0:H, 0:W]
        self.mount &= np.hypot(xx - self.cx, yy - self.cy) > 4
        self.fix_connectivity()

    def _dijkstra(self, sources, targets, allow, cost_mount=4, limit=None):
        """Najtansza droga po polach (inside + dozwolone) od sources do targets; zwraca liste pol."""
        H, W = self.H, self.W
        targets = set(targets)
        dist = {s: 0 for s in sources}
        prev = {s: None for s in sources}
        heap = [(0, s) for s in sources]
        heapq.heapify(heap)
        end = None
        while heap:
            d, p = heapq.heappop(heap)
            if p in targets:
                end = p
                break
            if d > dist.get(p, 1e18):
                continue
            for dx, dy in F.DIRS4:
                n = (p[0] + dx, p[1] + dy)
                if not (1 <= n[0] < W - 1 and 1 <= n[1] < H - 1) or not allow(n):
                    continue
                nd = d + (cost_mount if self.mount[n[1], n[0]] else 1) + (self.rng.random() * 0.8)
                if nd < dist.get(n, 1e18):
                    dist[n] = nd
                    prev[n] = p
                    heapq.heappush(heap, (nd, n))
        if end is None:
            return None
        out = []
        while end is not None:
            out.append(end)
            end = prev[end]
        return out[::-1]

    def _carve(self, path, wide=True):
        for (x, y) in path:
            self.mount[y, x] = False
            self.pool[y, x] = False
            if wide:
                dx, dy = self.rng.choice(F.DIRS4)
                a, b = x + dx, y + dy
                if self.inside[b, a] and self.rng.random() < 0.7:
                    self.mount[b, a] = False

    def fix_connectivity(self):
        """Doliny tworza jedna spojna siec: male kieszenie zamieniane w grzbiet, duze laczone krecacymi przejsciami."""
        for _ in range(12):
            comps = components(self.inside & ~self.mount)
            if len(comps) <= 1:
                return
            main = set(comps[0])
            for comp in comps[1:]:
                if len(comp) < 30:
                    for (x, y) in comp:
                        self.mount[y, x] = True
                else:
                    path = self._dijkstra(comp, main, lambda n: self.inside[n[1], n[0]])
                    if path:
                        self._carve(path)
                        main |= set(path) | set(comp)
        comps = components(self.inside & ~self.mount)
        for comp in comps[1:]:
            for (x, y) in comp:
                self.mount[y, x] = True

    # ---------------------------------------------------------------- szczeliny w obrzezu (wejscia z zewnatrz)
    def open_gates(self, count):
        H, W = self.H, self.W
        base = self.rng.uniform(0, 2 * math.pi)
        main = set(components(self.inside & ~self.mount)[0])
        for k in range(count):
            a = base + 2 * math.pi * k / count + self.rng.uniform(-0.45, 0.45)
            ex = ey = None
            for step in range(2, 200):
                x, y = int(round(self.cx + math.cos(a) * step)), int(round(self.cy + math.sin(a) * step))
                if not (0 <= x < W and 0 <= y < H) or not self.inside[y, x]:
                    break
                ex, ey = x, y
            if ex is None:
                continue
            tx, ty = int(round(ex + math.cos(a) * 4)), int(round(ey + math.sin(a) * 4))
            tx, ty = min(max(tx, 2), W - 3), min(max(ty, 2), H - 3)
            path = self._dijkstra([(tx, ty)], main, lambda n: True)
            if not path:
                continue
            for (x, y) in path:
                if not self.inside[y, x]:
                    self.extra[(x, y)] = True
                    dx, dy = self.rng.choice(F.DIRS4)
                    if not self.inside[y + dy, x + dx] and self.rng.random() < 0.7:
                        self.extra[(x + dx, y + dy)] = True
            self._carve([p for p in path if self.inside[p[1], p[0]]])
            self.gates.append((tx, ty))
        return self.gates

    # ---------------------------------------------------------------- stawy i ozdoby
    def ponds(self, count, ground):
        """Stawy (nieprzechodnie) tam, gdzie nie ucinaja doliny; ground - pole stawu."""
        walk = self.walk_mask()
        cells = [(x, y) for (x, y) in zip(*np.nonzero(walk)[::-1])]
        self.rng.shuffle(cells)
        made = 0
        for (x, y) in cells:
            if made >= count:
                break
            if math.hypot(x - self.cx, y - self.cy) < 9:
                continue
            r = self.rng.choice((2, 2, 3))
            if any(not (0 <= x + dx < self.W and 0 <= y + dy < self.H) or not walk[y + dy, x + dx]
                   for dx in range(-r - 2, r + 3) for dy in range(-r - 2, r + 3) if dx * dx + dy * dy <= (r + 2) ** 2):
                continue
            if any(abs(x - gx) + abs(y - gy) < 14 for (gx, gy) in self.gates):
                continue
            keep = self.pool.copy()
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    if dx * dx + dy * dy <= r * r + self.rng.random() * 2:
                        self.pool[y + dy, x + dx] = True
            if len(components(self.walk_mask())) > 1:
                self.pool = keep
                continue
            made += 1
        self.pond_ground = ground
        return made

    def paint(self, p, th):
        """Wpisuje powierzchnie do planu z0 wg motywu th (podloze, ozdoby, obiekty, sciana grzbietow)."""
        rng = self.rng
        H, W, seed = self.H, self.W, self.seed
        n3 = vnoise(H, W, 6, seed + 11)
        n4 = vnoise(H, W, 5, seed + 12)
        walk = self.walk_mask()
        reserved = np.zeros_like(walk)
        for (x, y) in self.gates + self.entrances:
            reserved[max(0, y - 3):y + 4, max(0, x - 3):x + 4] = True
        for (x, y) in zip(*np.nonzero(self.inside | self._extra_mask())[::-1]):
            px, py = self.plan(x, y)
            if self.mount[y, x]:
                g = th['mount_ground'][0] if n3[y, x] < 0.55 else th['mount_ground'][1]
                p.put(px, py, 0, (g, NONE, NONE, th['wall']))
                continue
            if self.pool[y, x]:
                p.put(px, py, 0, (th['pond'], NONE, NONE, NONE))
                continue
            v = n3[y, x]
            g = th['ground'][0]
            for lim, gg in th['patches']:
                if (lim > 0 and v > lim) or (lim < 0 and v < -lim):
                    g = gg
                    break
            deco = NONE
            r = rng.random()
            acc = 0.0
            for chance, d in th['deco']:
                acc += chance
                if r < acc:
                    deco = d
                    break
            p.put(px, py, 0, (g, deco, NONE, NONE))
        # obiekty blokujace tylko na polach z osmioma wolnymi sasiadami (nie zamykaja przejsc)
        taken = np.zeros_like(walk)
        cand = [(x, y) for (x, y) in zip(*np.nonzero(walk)[::-1])]
        rng.shuffle(cand)
        for (x, y) in cand:
            if reserved[y, x] or taken[y - 1:y + 2, x - 1:x + 2].any():
                continue
            if not walk[y - 1:y + 2, x - 1:x + 2].all():
                continue
            dens = th['density'] * (0.4 + 1.6 * n4[y, x])
            if rng.random() >= dens:
                continue
            obj = rng.choice(th['objects'])
            px, py = self.plan(x, y)
            c = p.get(px, py, 0)
            p.put(px, py, 0, (c[0], NONE, obj, NONE))
            taken[y, x] = True
        self.taken = taken
        self.reserved = reserved

    # ---------------------------------------------------------------- wejscia do jaskin i spawny
    def pick_entrances(self, p, count):
        """Zejscia do jaskin u podnoza grzbietow: pole doliny z grzbietem od polnocy, daleko od siebie."""
        walk = self.walk_mask()
        cand = []
        for (x, y) in zip(*np.nonzero(walk)[::-1]):
            if not (self.mount[y - 1, x] and walk[y + 1, x] and walk[y, x - 1] and walk[y, x + 1]):
                continue
            if self.taken[y - 1:y + 2, x - 1:x + 2].any() or self.reserved[y, x]:
                continue
            if any(abs(x - gx) + abs(y - gy) < 12 for (gx, gy) in self.gates):
                continue
            px, py = self.plan(x, y)
            c = p.get(px, py, 0)
            if c is None or c[2] != NONE:
                continue
            cand.append((x, y))
        assert len(cand) >= count * 3, f'{self.key}: too few cave mouths {len(cand)}'
        first = min(cand, key=lambda q: math.hypot(q[0] - self.cx, q[1] - self.cy))
        out = [first]
        while len(out) < count:
            out.append(max(cand, key=lambda q: min(abs(q[0] - a) + abs(q[1] - b) for a, b in out) + self.rng.random() * 6))
        self.entrances = out
        for (x, y) in out:
            px, py = self.plan(x, y)
            c = p.get(px, py, 0)
            p.put(px, py, 0, (c[0], NONE, NONE, NONE))
            self.taken[y - 1:y + 2, x - 1:x + 2] = True
        return [self.plan(x, y) for (x, y) in out]

    def spread(self, count, apart, avoid):
        """count pol doliny rozmieszczonych szeroko (odstep >= apart), z dala od avoid (indeksowe)."""
        walk = self.walk_mask()
        cand = [(x, y) for (x, y) in zip(*np.nonzero(walk)[::-1])
                if not self.reserved[y, x] and not self.taken[y - 1:y + 2, x - 1:x + 2].any()
                and all(abs(x - a) + abs(y - b) >= 8 for (a, b) in avoid)]
        self.rng.shuffle(cand)
        out = []
        for q in cand:
            if len(out) >= count:
                break
            if all(abs(q[0] - a) + abs(q[1] - b) >= apart for (a, b) in out):
                out.append(q)
        return out


THEMES = {
    GOBLIN: dict(
        ground=('grass',), patches=[(0.64, 'dirt')], mount_ground=('grass', 'dirt'), wall='mountain', pond='swamp',
        deco=[(0.22, 'tall_grass'), (0.06, 'flowers'), (0.03, 'fern'), (0.01, 'bones'), (0.01, 'debris')],
        density=0.045, objects=['tree', 'tree', 'tree', 'pine', 'bush', 'bush', 'rock']),
    HYDRA: dict(
        ground=('jungle_grass',), patches=[(0.60, 'blighted_grass'), (-0.18, 'dark_sand')], mount_ground=('jungle_grass', 'dirt'),
        wall='mountain', pond='swamp',
        deco=[(0.16, 'jungle_plant'), (0.10, 'fern'), (0.05, 'reeds'), (0.02, 'bones'), (0.02, 'mushrooms')],
        density=0.06, objects=['jungle_tree', 'jungle_tree', 'jungle_tree', 'jungle_bush', 'jungle_bush', 'palm', 'rock']),
    DRAGON: dict(
        ground=('sand',), patches=[(0.62, 'dirt'), (-0.2, 'ash')], mount_ground=('sand', 'dirt'), wall='sand_cliff', pond='lava',
        deco=[(0.10, 'dry_grass'), (0.05, 'dry_shrub'), (0.035, 'bones'), (0.01, 'debris')],
        density=0.035, objects=['cactus', 'sandstone_rock', 'sandstone_rock', 'dry_bush', 'dry_bush', 'dead_tree']),
}


# ====================================================================== podziemia
def floor_net(p, z, rect, entrances, n, style, rng, inside, n_down=2, gap=10, room=(3, 5)):
    """Pietro jako siec komor: komory w losowych punktach (z odstepem gap), drzewo rozpinajace plus petle, krecace tunely
    z punktami posrednimi, slepe komory (liscie drzewa), n_down zejsc na nizsze pietro w najdalszych komorach.
    Zwraca dict(nodes, edges, depth, leaves, downs, rooms)."""
    x0, y0, w, h = rect
    entrances = list(entrances)
    for _attempt in range(80):
        snap = dict(p.f.get(z, {}))
        pts = []
        tries = 0
        while len(pts) < n and tries < 9000:
            tries += 1
            q = (rng.randint(x0 + 5, x0 + w - 6), rng.randint(y0 + 5, y0 + h - 6))
            if not inside(q) or any(abs(q[0] - a) + abs(q[1] - b) < gap for (a, b) in pts + entrances):
                continue
            pts.append(q)
        if len(pts) < max(6, n * 3 // 4):
            continue
        downs = []
        for _ in range(n_down):
            cands = [q for q in pts if q not in downs]
            downs.append(max(cands, key=lambda q: min(abs(q[0] - a) + abs(q[1] - b) for a, b in entrances + downs)
                             + rng.random() * 5))
        nodes = entrances + pts
        # drzewo rozpinajace (Prim) od wejsc
        intree = set(range(len(entrances)))
        edges = []
        while len(intree) < len(nodes):
            best = None
            for i in intree:
                for j in range(len(nodes)):
                    if j in intree:
                        continue
                    d = math.hypot(nodes[i][0] - nodes[j][0], nodes[i][1] - nodes[j][1])
                    if best is None or d < best[0]:
                        best = (d, i, j)
            _d, i, j = best
            intree.add(j)
            edges.append((i, j))
        # wejscia polaczone ze soba (jedno pietro, kilka zejsc z powierzchni)
        for i in range(1, len(entrances)):
            if not any(i in e and j in e for e in edges for j in range(len(entrances)) if j != i):
                edges.append((0, i))
        # petle: krotkie krawedzie spoza drzewa
        have = {frozenset(e) for e in edges}
        extra = sorted(((math.hypot(nodes[i][0] - nodes[j][0], nodes[i][1] - nodes[j][1]), i, j)
                        for i in range(len(nodes)) for j in range(i + 1, len(nodes)) if frozenset((i, j)) not in have))
        loops = 0
        for (d, i, j) in extra:
            if loops >= max(2, len(pts) // 8):
                break
            if 12 < d < 30 and rng.random() < 0.45:
                edges.append((i, j))
                loops += 1
        for q in nodes:
            big = room if q not in entrances and q not in downs else (2, 3)
            I.dig(p, z, x0, y0, w, h, [q], style, rng, room=big)
        for (i, j) in edges:
            a, b = nodes[i], nodes[j]
            way = [a]
            d = math.hypot(b[0] - a[0], b[1] - a[1])
            k = int(d // 9)
            for s in range(1, k + 1):
                t = s / (k + 1)
                nx, ny = -(b[1] - a[1]) / max(d, 1), (b[0] - a[0]) / max(d, 1)
                off = rng.uniform(-1, 1) * min(8, d * 0.3)
                wx = min(max(int(a[0] + (b[0] - a[0]) * t + nx * off), x0 + 3), x0 + w - 4)
                wy = min(max(int(a[1] + (b[1] - a[1]) * t + ny * off), y0 + 3), y0 + h - 4)
                way.append((wx, wy))
            way.append(b)
            I.dig(p, z, x0, y0, w, h, way, style, rng, room=(1, 2))
        for q in entrances + downs:
            I._clear(p, z, q, style)
        if I._connected(p, z, entrances[0], nodes):
            keep = {(q[0] + a, q[1] + b) for q in entrances + downs for a in (-2, -1, 0, 1, 2) for b in (-2, -1, 0, 1, 2)}
            F.ensure_lights(lambda x, y: p.get(x, y, z), lambda x, y, c: p.put(x, y, z, c),
                            [q for q, c in p.f[z].items() if St.walk(c) and q not in keep], F.CAVE_STYLES[style][4], rng)
            # glebokosc w drzewie od wejsc (liczba krawedzi)
            adj = {i: [] for i in range(len(nodes))}
            for (i, j) in edges:
                adj[i].append(j)
                adj[j].append(i)
            depth = {i: 0 for i in range(len(entrances))}
            dq = deque(depth)
            while dq:
                i = dq.popleft()
                for j in adj[i]:
                    if j not in depth:
                        depth[j] = depth[i] + 1
                        dq.append(j)
            leaves = [nodes[i] for i in range(len(entrances), len(nodes)) if len(adj[i]) == 1 and nodes[i] not in downs]
            return dict(nodes=nodes, edges=edges, depth={nodes[i]: d for i, d in depth.items()}, leaves=leaves,
                        downs=downs, rooms=pts)
        p.f[z] = snap
    raise AssertionError(f'{p.name}: floor {z} not connected')


# ====================================================================== budowa krainy
def route_dist(p, z, sources):
    """Odleglosc po polach przechodnich pietra z planu od zrodel {pole: odleglosc poczatkowa} (Dijkstra).
    Przyblizenie drogi, ktora liczy route_levels od swiatyn: poziomy potworow maja rosnac wzdluz niej."""
    dist = dict(sources)
    heap = [(d, q) for q, d in sources.items()]
    heapq.heapify(heap)
    while heap:
        d, q = heapq.heappop(heap)
        if d > dist.get(q, 1e18):
            continue
        for dx, dy in F.DIRS4:
            n = (q[0] + dx, q[1] + dy)
            c = p.get(n[0], n[1], z)
            if c is None or not St.walk(c) and c[2] not in F.STAIRS:
                continue
            if d + 1 < dist.get(n, 1e18):
                dist[n] = d + 1
                heapq.heappush(heap, (d + 1, n))
    return dist


def build_one(ctx, cost, helpers, key, masks, reach, stand, taken, level, refs):
    cfg = HILLS[key]
    th = THEMES[key]
    world = ctx['world']
    S = len(world.floors[0])
    seed = SEED + {GOBLIN: 1, HYDRA: 2, DRAGON: 3}[key] * 101
    rng = random.Random(seed)
    why = {}
    found = None
    for attempt in range(8):
        inside, cx, cy = make_shape(seed + attempt * 7, cfg)
        found = None
        if cfg.get('pin'):      # 2026-11-12: dawne polozenie (zmiany obozow i dziczy nie przesuwaja krain), potem zwykle szukanie
            found = find_origin(ctx, helpers, dict(cfg, near=cfg['pin'], radius=0), inside, cx, cy, reach, masks, taken, why)
        if found is None:
            found = find_origin(ctx, helpers, cfg, inside, cx, cy, reach, masks, taken, why)
        if found is not None:
            break
    assert found is not None, f'{cfg["name"]}: no site near {cfg["near"]}: {why}'
    _score, ox, oy = found
    sf = Surface(cfg, key, seed + attempt * 7, inside, cx, cy, rng)
    sf.ridges()
    sf.open_gates(3)
    assert len(sf.gates) >= 2, f'{cfg["name"]}: gates {sf.gates}'
    sf.ponds(5 if key != DRAGON else 3, th['pond'])
    p = St.Plan(cfg['name'])
    sf.paint(p, th)
    # trasy wejsc: pola poza obrzezem (szczeliny) maja podloze motywu
    for (x, y) in sf.extra:
        px, py = sf.plan(x, y)
        c = p.get(px, py, 0)
        if c is None:
            p.put(px, py, 0, (th['ground'][0], NONE, NONE, NONE))
        elif c[2] != NONE:
            p.put(px, py, 0, (c[0], NONE, NONE, NONE))
    for (x, y) in sf.gates:
        px, py = sf.plan(x, y)
        c = p.get(px, py, 0) or (th['ground'][0], NONE, NONE, NONE)
        p.put(px, py, 0, (c[0], NONE, NONE, NONE))
        p.entries.append((px, py))
    ents = sf.pick_entrances(p, cfg['entrances'])
    ys, xs = np.nonzero(sf.inside)
    bx0, bx1, by0, by1 = int(xs.min()) - sf.cx, int(xs.max()) - sf.cx, int(ys.min()) - sf.cy, int(ys.max()) - sf.cy
    area = int(sf.inside.sum())
    # ---------------------------------------------------------------- spawny powierzchni, oboz i ozdoby
    idx_ents = list(sf.entrances)
    pts = sf.spread(cfg['surface_n'], 11, idx_ents + sf.gates)
    assert len(pts) >= cfg['surface_n'] - 2, f'{cfg["name"]}: surface spawns {len(pts)}'
    # najslabsze potwory przy szczelinach wejsc, najsilniejsze w glebi (poziomy rosna wzdluz drogi)
    # (kolejnosc wg przyblizonej drogi od swiatyni: odleglosc od swiatyni do szczeliny + droga w dolinach)
    order = sorted(cfg['surface'], key=lambda m: level[m])
    src = {}
    for (gx, gy) in sf.gates:
        wx, wy = ox + gx - sf.cx, oy + gy - sf.cy
        src[sf.plan(gx, gy)] = min(math.hypot(wx - r[0], wy - r[1]) for r in refs) * 1.25
    dsurf = route_dist(p, 0, src)
    pts = sorted(pts, key=lambda q: dsurf.get(sf.plan(*q), 1e9))
    for i, (x, y) in enumerate(pts):
        px, py = sf.plan(x, y)
        p.spawn(order[min(len(order) - 1, i * len(order) // len(pts))], px, py, 0, radius=4, count=(3, 4)[i % 2], respawn=200)
    surf_pts = [sf.plan(x, y) for (x, y) in pts]
    _props(p, sf, th, key, rng, surf_pts)
    sf.pick_entrances_done = True
    # ---------------------------------------------------------------- podziemia
    rect = (bx0 - 5, by0 - 5, bx1 - bx0 + 11, by1 - by0 + 11)
    under = dilate(sf.inside, 4)

    def inside_q(q):
        x, y = q[0] + sf.cx, q[1] + sf.cy
        return 0 <= x < sf.W and 0 <= y < sf.H and under[y, x]
    arrive = list(ents)
    dprev = dsurf
    floors = {}
    chest_i = 0
    depth_total = len(cfg['floors'])
    for k in range(1, depth_total + 1):
        z = -k
        style, mons, counts, lv = cfg['floors'][z]
        n = max(12, int(area / (150 + 8 * k)))
        last = k == depth_total
        res = floor_net(p, z, rect, arrive, n, style, rng, inside_q, n_down=0 if last else 2,
                        gap=10 if key != GOBLIN else 9, room=(2, 4) if key == GOBLIN else (3, 5))
        floors[z] = res
        for a in arrive:
            p.stairs(z, a, a)
        rooms = [q for q in res['rooms'] if q not in res['downs']]
        dfl = route_dist(p, z, {a: dprev.get(a, 0) + 3 for a in arrive})
        ranked = sorted(rooms, key=lambda q: dfl.get(q, 1e9))
        for i, q in enumerate(ranked):
            idx = min(len(mons) - 1, int(i / max(1, len(ranked)) * len(mons) + rng.random() * 0.35))
            order_m = sorted(mons, key=lambda m: level[m])
            p.spawn(order_m[idx], q[0], q[1], z, radius=4, count=counts[i % 2], respawn=200 + 10 * k)
        # slepe komory ze skrzyniami
        leaves = sorted(res['leaves'], key=lambda q: -res['depth'].get(q, 0))
        for j, q in enumerate(leaves[:1 + k // 2]):
            spot = I.chest_spot(p, z, q)
            if spot is not None:
                item, cnt = cfg['chests'][(chest_i + j) % len(cfg['chests'])]
                p.chests.append((spot[0], spot[1], z, 120 * k * (2 if key != GOBLIN else 1) + 90 * j, item, cnt))
        chest_i += 1
        I.floor_region(p, cfg['under'], z, lv)
        arrive = list(res['downs'])
        dprev = dfl
    p.regions.append((cfg['name'], bx0 - 5, by0 - 5, bx1 + 5, by1 + 5, 0, 0) + cfg['surface_levels'])
    n_cells = p.check()
    for (mk, *_r) in p.spawns:
        assert mk in level, f'{cfg["name"]}: unknown monster {mk}'
    # ---------------------------------------------------------------- na swiat
    origin = (ox, oy)
    St.clear_surface(ctx, ox + bx0 - 6, oy + by0 - 6, ox + bx1 + 6, oy + by1 + 6)
    W = St.stamp(ctx, p, origin, helpers['mark'], per_floor=True)
    deep_only = (lambda z: z <= -2) if key != GOBLIN else (lambda z: z <= -3)
    I.tag_packs(ctx, W, p, only=deep_only, level=level)
    # kraina osiagalna pieszo (albo przystan wyspy) - w razie potrzeby sciezka od szczeliny do reszty swiata
    gate_w = [(ox + x - sf.cx, oy + y - sf.cy) for (x, y) in sf.gates]
    road = _link(ctx, cost, helpers, gate_w, stand, key, cfg)
    info = dict(origin=origin, bbox=(ox + bx0, oy + by0, ox + bx1, oy + by1), gates=gate_w,
                entrances=[W(q[0], q[1], 0) for q in ents], under={z: W(0, 0, z) for z in floors},
                area=area, plan_cells=n_cells, road=road,
                spawns={z: sum(c for (_m, _x, _y, zz, _r, c, _s) in p.spawns if zz == z) for z in range(-depth_total, 1)})
    taken.append(origin)
    return info


def _props(p, sf, th, key, rng, surf_pts):
    """Oboz goblinow, ruiny hydr albo gniazda smokow przy czesci spawnow (obiekty tylko na wolnych polach)."""
    prop = {GOBLIN: ['orc_tent', 'campfire', 'crate', 'barrel', 'firewood'],
            HYDRA: ['jungle_statue', 'broken_wall', 'rubble', 'rock'],
            DRAGON: ['sand_statue', 'sandstone_rock', 'rubble', 'dead_tree']}[key]
    free_deco = {GOBLIN: 'bones', HYDRA: 'bones', DRAGON: 'bones'}[key]
    camps = rng.sample(surf_pts, min(5, len(surf_pts)))
    for (cx, cy) in camps:
        for k in range(rng.randint(3, 5)):
            for _ in range(10):
                x, y = cx + rng.randint(-4, 4), cy + rng.randint(-4, 4)
                if abs(x - cx) + abs(y - cy) < 2:
                    continue
                ok = True
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        c = p.get(x + dx, y + dy, 0)
                        if c is None or not St.walk(c) or c[2] != NONE:
                            ok = False
                if ok:
                    c = p.get(x, y, 0)
                    p.put(x, y, 0, (c[0], free_deco if rng.random() < 0.3 else c[1], prop[k % len(prop)], NONE))
                    break
    if key == DRAGON:
        # ogniska smokow: ciemne place popiolu wokol punktow spawnu
        for (cx, cy) in camps[:3]:
            for dx in range(-3, 4):
                for dy in range(-3, 4):
                    c = p.get(cx + dx, cy + dy, 0)
                    if c is not None and St.walk(c) and c[2] == NONE and dx * dx + dy * dy <= 8 and rng.random() < 0.8:
                        p.put(cx + dx, cy + dy, 0, ('ash', rng.choice((NONE, NONE, 'bones', 'debris')), NONE, NONE))


def _link(ctx, cost, helpers, gates, stand, key, cfg):
    """Zapewnia sciezke od szczeliny krainy do swiata: kontynent - do najblizszej drogi / lad osiagalny z miasta, wyspa -
    do przystani przewoznika."""
    world = ctx['world']
    S = len(world.floors[0])
    start = min(gates, key=lambda g: math.hypot(g[0] - cfg['near'][0], g[1] - cfg['near'][1]))
    if cfg['island']:
        reach = I.world_reach(world, stand, limit=400000)
        if any(g in reach or (g[0] + dx, g[1] + dy) in reach for g in gates for dx, dy in F.DIRS4 + ((0, 0),)):
            return None
        s0 = next((stand[0] + a, stand[1] + b) for a, b in ((0, 0),) + F.DIRS4 if St.walk(world.get(stand[0] + a, stand[1] + b)))
        path = I.world_trail(world, start, {s0})
        return path[-1]
    temple = dict(ctx['towns'])['Varden'][:2]
    mask = _reach_mask(world, S, [temple])
    if any(mask[g[1], g[0]] for g in gates):
        return None
    import strongholds as SH
    ring = (start[0] - 3, start[1] - 3, start[0] + 3, start[1] + 3)
    road = SH._connect(ctx, cost, helpers, start, ring)
    mask = _reach_mask(world, S, [temple])
    if not any(mask[g[1], g[0]] for g in gates):
        goals = {(x, y) for y in range(max(0, start[1] - 90), min(S, start[1] + 91))
                 for x in range(max(0, start[0] - 90), min(S, start[0] + 91)) if mask[y, x]
                 and world.get(x, y)[0] in F.ROAD_GROUND}
        path = I.world_trail(world, start, goals)
        road = path[-1]
    return road


def build(ctx, cost, helpers):
    """Buduje trzy krainy (kolejnosc: Goblinow, Hydr, Smokow); zwraca opis do wypisania."""
    world = ctx['world']
    S = len(world.floors[0])
    level = I._levels(helpers['monsters_csv'])
    masks = _surface_masks(ctx, helpers)
    out = {}
    taken = []
    temple = dict(ctx['towns'])['Varden'][:2]
    main_reach = _reach_mask(world, S, [temple])
    for key in (GOBLIN, DRAGON, HYDRA):
        cfg = HILLS[key]
        refs = [t[:2] for _n, t in ctx['towns'] if t[2] == 0]
        if cfg['island']:
            isle = I.component(world, ISLAND_SEED, limit=60000)
            ferries = [n for n in ctx['npcs'] if n[2] == 'CAPTAIN' and int(n[6]) == 0 and (int(n[4]), int(n[5])) in isle]
            assert ferries, f'{cfg["name"]}: no ferry on the island'
            stand = (int(ferries[0][4]), int(ferries[0][5]))
            reach = _reach_mask(world, S, [stand])
            refs = [stand]
        else:
            stand = None
            reach = main_reach
        info = build_one(ctx, cost, helpers, key, masks, reach, stand, taken, level, refs)
        out[key] = info
        # kolejna krain nie wchodzi w juz zbudowana: odswiez maski zabudowy na pietrze 0
        masks = _surface_masks(ctx, helpers)
        print(f'  {cfg["name"]}: {info["bbox"]} pola {info["area"]} wejscia {info["entrances"]} potwory {info["spawns"]}')
    sever_diagonals(ctx, S)
    ctx['hills'] = out
    return out


def _walk8(world, start, limit=200000):
    """Pole -> poprzednik, ruch w 8 kierunkach (jak w grze: po skosie miedzy drzewami tez sie przechodzi)."""
    par = {start: None}
    q = deque([start])
    while q and len(par) < limit:
        p = q.popleft()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (p[0] + dx, p[1] + dy)
                if n not in par and St.walk(world.get(*n)):
                    par[n] = p
                    q.append(n)
    return par


def _dist8(world, start, limit=200000):
    dist = {start: 0}
    q = deque([start])
    while q and len(dist) < limit:
        p = q.popleft()
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (p[0] + dx, p[1] + dy)
                if n not in dist and St.walk(world.get(*n)):
                    dist[n] = dist[p] + 1
                    q.append(n)
    return dist


def sever_diagonals(ctx, S):
    """Wzgorza Hydr leza na wyspie tylko dla statkow. Tunel pod morzem (generate_continent.tunnel) konczy sie na
    sasiednim kawalku ladu, ktory z wyspa laczy tylko krzyzujacy sie po skosie las. Taka szczelina (4 kierunki jej
    nie widza, gra pozwala przejsc) jest zasypywana glazami na polu, bez ktorego sciezka sie urywa."""
    world = ctx['world']
    isle = ISLAND_SEED
    landings = [(tx, ty) for (x, y, z, _t, tx, ty, tz) in ctx['transitions']
                if tz == 0 and z < 0 and abs(tx - isle[0]) < 140 and abs(ty - isle[1]) < 140]
    cut = 0
    own = I.world_reach(world, isle, limit=60000)
    for land in landings:
        if land in own or not St.walk(world.get(*land)):
            continue
        for _ in range(30):
            dl = _dist8(world, land)
            if isle not in dl:
                break
            di = _dist8(world, isle)
            total = dl[isle]
            # warstwa BFS od ladowania o najmniejszej liczbie pol lezacych na (prawie) najkrotszych drogach do wyspy:
            # kazda droga przechodzi przez kazda warstwe, wiec zasypanie tej warstwy ja przecina
            layers = {}
            for c_, d_ in dl.items():
                if c_ in di and d_ + di[c_] <= total + 4 and 6 <= d_ <= total - 6:
                    layers.setdefault(d_, []).append(c_)
            d_best = min(layers, key=lambda k: len(layers[k]))
            for (qx, qy) in layers[d_best]:
                g, d, o, w = world.get(qx, qy)
                world.put(qx, qy, 0, (g, NONE, 'rock', w))
                cut += 1
        else:
            raise AssertionError(f'Wzgorza Hydr: nie da sie odciac wyspy od tunelu przy {land}')
    print('  Wzgórza Hydr: glazy w szczelinach po skosie:', cut)


ISLAND_SEED = (933, 441)      # pole wyspy wschodniej (Lochy Zapomnianych), z ktorej wybierany jest przewoznik
