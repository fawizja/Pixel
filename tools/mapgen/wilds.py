"""Lowiska w dziczy (Darek 2026-11-03): "wiecej jaskin, kopalni, dziur i losowych lowisk - teraz chodze po mapie i ciezko
trafic na lowisko; na powierzchni w lasach i na lakach wiecej potworow niskiego i sredniego poziomu".

Dwie czesci, obie z wlasnym ziarnem (reszta mapy i kolejnosc losowan generatora bez zmian):

* wild_dungeons - wejscia do podziemi rozsiane po calym ladzie: najpierw tam, gdzie od drogi najdalej do lowiska
  (kazdy punkt drogi ma lowisko w zasiegu ~45 pol), potem w najbardziej pustych miejscach. Trzy rodzaje:
    - jaskinia: skalny wystep z ciemnym wylotem i schodami w dol, naturalne komory (mech, skala, lod, popiol),
    - kopalnia: wejscie w drewnianej ramie z pochodnia, plac z drewnem, skrzyniami i kupka rudy; w srodku
      chodniki z drewnianymi stemplami przy scianach, glazy rudy i krysztaly,
    - zapadlisko: dziura w ziemi wsrod ruin, w dole stare kanaly z kamienia (drabina z powrotem).
  Kazdy loch ma 1-3 pietra po 5-8 komor, potwory wg poziomu okolicy (kraina i odleglosc od miast), w komorach
  ciala poleglych, a w ostatniej komorze skrzynie z drobnym lupem (chests.csv, world/WorldMap.LootChest).
* surface_spawns - dodatkowe spawny niskiego (1-15) i sredniego (16-40) poziomu w lasach i na lakach, obok drog
  (nigdy na drodze) i w glebi lasow, wg odleglosci od miast: blisko miast slabe, dalej srednie.
"""
import math
import random
from collections import deque

import features as F
import town as tk
from structures import HAND_PLACED

NONE = '-'
SEED_DUNGEONS = 20261103
SEED_SPAWNS = 20261104

F.CAVE_STYLES.setdefault('sewer', ('stone_floor', 'stone_wall', ['cobweb', 'blood', 'bones', 'debris'],
                                   ['barrel', 'rubble', 'crate'], 'wall_torch'))

# punkty testow (GameWorldTest, PathTest...) i strzezone przejscia - zostaja z dala od nowych wejsc
ANCHORS = [(149, 348), (131, 348), (151, 348), (149, 346), (149, 351), (489, 497), (511, 498), (510, 498),
           (512, 498), (513, 503), (514, 503), (309, 216), (309, 217), (310, 217)]

CAVE_NAMES = ['Jaskinia', 'Grota', 'Pieczara', 'Nora']
MINE_NAMES = ['Kopalnia', 'Sztolnia', 'Szyb']
HOLE_NAMES = ['Zapadlisko', 'Stary Kanał', 'Rozpadlina', 'Dziura']
OF = ['Szeptów', 'Kości', 'Mroku', 'Nietoperzy', 'Ech', 'Wilków', 'Pajęczyn', 'Kamieni', 'Zbójców', 'Mgieł',
      'Korzeni', 'Rdzy', 'Popiołu', 'Kryształów', 'Wiatru', 'Cieni', 'Szczurów', 'Sów', 'Jeleni', 'Zimnej Wody',
      'Starego Bartosza', 'Czarnego Lisa', 'Zagubionych', 'Węży', 'Grzybów', 'Iskier', 'Soli', 'Gliny', 'Miedzi',
      'Cyny', 'Srebra', 'Żelaza', 'Węgla', 'Bursztynu', 'Topielców', 'Gnatów', 'Krzyków', 'Kretów', 'Borsuka',
      'Trzech Braci', 'Siwego Gór', 'Kruków', 'Żmij', 'Dzików', 'Pustelnika', 'Ślepców', 'Kapiącej Wody', 'Łupieżców',
      'Strzyg', 'Leszego', 'Głazów', 'Smolarzy', 'Węglarzy', 'Kowala', 'Wisielców', 'Upiorów', 'Gromów', 'Mrozów',
      'Piasków', 'Skorpionów', 'Ciem', 'Starej Wiedźmy', 'Dziewięciu Grobów', 'Krasnoludów', 'Goblinów', 'Ropuch',
      'Żuków', 'Kurhanu', 'Starego Młyna', 'Złamanego Kilofa', 'Rudej Skały', 'Czerwonej Gliny', 'Białych Kości']

# potwory, ktorych w lochach w glebi ladu nie chcemy (morze, powietrze, bossowie wysp)
NOT_UNDERGROUND = {'coral_crab', 'drowned_sailor', 'pirate_cutthroat', 'pirate_gunner', 'storm_harpy', 'storm_wisp',
                   'wyvern', 'carrion_crow', 'snare_setter', 'dune_drake'}
BIOME_MONSTERS = {'snow': {'north', 'underground'}, 'taiga': {'north', 'underground'},
                  'desert': {'desert', 'underground'}, 'savanna': {'desert', 'underground'},
                  'jungle': {'equatorial', 'underground'}, 'swamp': {'equatorial', 'underground'},
                  'volcanic': {'equatorial', 'underground'}, 'shadow': {'desert', 'underground'},
                  'cursed': {'temperate', 'underground'}}

# dodatkowe spawny na powierzchni: slabe (1-15) i srednie (16-40) wg biomu
LOW = {'temperate': ['wolf', 'boar', 'carrion_crow', 'forest_spider', 'goblin', 'snare_setter', 'bandit'],
       'forest': ['wolf', 'boar', 'forest_spider', 'forest_spider', 'goblin', 'snare_setter', 'carrion_crow'],
       'hills': ['goblin', 'bandit', 'carrion_crow', 'boar', 'snare_setter'],
       'snow': ['snow_wolf', 'wolf', 'carrion_crow', 'snare_setter'],
       'taiga': ['snow_wolf', 'boar', 'snare_setter', 'forest_spider'],
       'desert': ['hyena', 'scorpion', 'bandit', 'burrow_larva'],
       'savanna': ['hyena', 'carrion_crow', 'bandit', 'scorpion'],
       'jungle': ['forest_spider', 'burrow_larva', 'boar', 'carrion_crow'],
       'swamp': ['forest_spider', 'burrow_larva', 'giant_rat', 'carrion_crow']}
# 2026-11-04: szkielet, hiena, skorpion i snieżny wilk maja poziomy z Tibii (slabe) - w srednich ich miejsce zajmuja orki
MID = {'temperate': ['goblin_shaman', 'orc_warrior', 'quarry_brute', 'spark_wisp', 'barrow_lantern', 'cave_troll'],
       'forest': ['orc_shaman', 'orc_warrior', 'barrow_lantern', 'spark_wisp', 'candle_acolyte', 'tunnel_fence'],
       'hills': ['quarry_brute', 'cave_troll', 'spark_wisp', 'orc_berserker', 'tunnel_fence'],
       'snow': ['frost_boar', 'orc_warrior', 'barrow_lantern', 'quarry_brute', 'frost_boar'],
       'taiga': ['frost_boar', 'orc_shaman', 'quarry_brute', 'barrow_lantern', 'orc_warrior'],
       'desert': ['orc_berserker', 'quarry_brute', 'tunnel_fence', 'candle_acolyte', 'crocodile'],
       'savanna': ['orc_warrior', 'quarry_brute', 'tunnel_fence', 'goblin_shaman', 'crocodile'],
       'jungle': ['cave_centipede', 'spark_wisp', 'crocodile', 'candle_acolyte'],
       'swamp': ['cave_centipede', 'spark_wisp', 'crocodile', 'barrow_lantern']}


def _monsters(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.rstrip('\n').split(';')
            out[p[0]] = dict(level=int(p[2]), boss=p[17].strip() == '1', biome=p[20].strip())
    return out


def _dist_field(S, sources, passable=None):
    """Odleglosc w polach (8 kierunkow, metryka Czebyszewa) od zrodel - na siatce co 2 pola wystarczy."""
    import numpy as np
    g = 2
    n = S // g
    d = np.full((n, n), 10 ** 6, dtype=np.int32)
    q = deque()
    for (x, y) in sources:
        cx, cy = min(n - 1, x // g), min(n - 1, y // g)
        if d[cy, cx] > 0:
            d[cy, cx] = 0
            q.append((cx, cy))
    while q:
        x, y = q.popleft()
        for a in (-1, 0, 1):
            for b in (-1, 0, 1):
                nx, ny = x + a, y + b
                if 0 <= nx < n and 0 <= ny < n and d[ny, nx] > d[y, x] + 1:
                    d[ny, nx] = d[y, x] + 1
                    q.append((nx, ny))
    return d * g


class Wilds:
    def __init__(self, ctx, cost, helpers):
        self.ctx = ctx
        self.world = ctx['world']
        self.T = ctx['T']
        self.S = len(self.world.floors[0])
        self.cost = cost
        self.h = helpers
        self.mon = _monsters(helpers['monsters_csv'])
        self.temples = [p[:2] for _n, p in ctx['towns']]
        self.villages = [v['rect'] for v in ctx.get('villages', [])]
        self.towns = list(helpers['town_rects'].values())
        self.reach = helpers['reach']
        self.trans = {(t[0], t[1]) for t in ctx['transitions'] if t[2] == 0}

    # ------------------------------------------------------------------ pomocnicze
    def near_temple(self, x, y):
        return min(math.hypot(x - a, y - b) for a, b in self.temples)

    def blocked_area(self, x, y, margin):
        if any(a - margin <= x <= c + margin and b - margin <= y <= d + margin for (a, b, c, d) in self.towns):
            return True
        if any(a - margin <= x <= c + margin and b - margin <= y <= d + margin for (a, b, c, d) in self.villages):
            return True
        if any(a - margin <= x <= c + margin and b - margin <= y <= d + margin for (a, b, c, d, z) in self.ctx['pz'] if z == 0):
            return True
        # miejsca zarezerwowane dla budowli stawianych pozniej (twierdza orkow, osada elfow - strongholds.RESERVED)
        if any(a - margin <= x <= c + margin and b - margin <= y <= d + margin for (a, b, c, d) in self.ctx.get('reserved', ())):
            return True
        return any(abs(x - a) < 14 and abs(y - b) < 14 for a, b in ANCHORS)

    def zone(self, x, y):
        return self.ctx['zones'].get((x // self.h['zone_cell'], y // self.h['zone_cell']))

    def area_level(self, x, y):
        zone = self.zone(x, y)
        lo, hi = self.h['zone_levels'].get(zone, (8, 60))
        frac = max(0.0, min(1.0, (self.near_temple(x, y) - 40) / 220))
        return max(5, int(lo + (hi - lo) * (0.15 + 0.6 * frac)))

    def site_ok(self, x, y):
        """Plac 9 x 9 dzikiego ladu (bez drog, domow, wody i gor), w promieniu 7 bez drog i zabudowan."""
        S, world = self.S, self.world
        if not (12 < x < S - 12 and 12 < y < S - 12) or self.blocked_area(x, y, 8):
            return False
        if self.h['footprint'][y - 7:y + 8, x - 7:x + 8].any():
            return False
        natural = self.h['natural']
        reach_any = False
        for b in range(-4, 5):
            for a in range(-4, 5):
                c = world.get(x + a, y + b)
                if not natural(c) or (x + a, y + b) in self.trans:
                    return False
                if self.reach[y + b, x + a]:
                    reach_any = True
        if not reach_any or not self.reach[y + 3, x]:
            return False
        for b in range(-7, 8):
            for a in range(-7, 8):
                if max(abs(a), abs(b)) <= 4:
                    continue
                c = world.get(x + a, y + b)
                if c is None or (x + a, y + b) in self.trans:
                    return False
                if not (natural(c) or c[0] in ('water', 'deep_water', 'swamp') or c[3] in ('mountain', 'snow_mountain', 'sand_cliff')):
                    return False
        return True

    # ------------------------------------------------------------------ uklad lochu
    def layout(self, rng, floors):
        W, H = rng.randint(44, 62), rng.randint(32, 44)
        out = []
        start = (rng.randint(5, 10), rng.randint(5, 10))
        for i in range(floors):
            n = rng.randint(5, 8)
            pts = [start]
            tries = 0
            while len(pts) < n and tries < 400:
                tries += 1
                p = (rng.randint(5, W - 6), rng.randint(5, H - 6))
                if all(abs(p[0] - q[0]) + abs(p[1] - q[1]) >= 12 for q in pts):
                    pts.append(p)
            # kolejnosc: najblizszy sasiad od wejscia (kretyny ciag komor), ostatni = schody nizej
            chain, left = [pts[0]], pts[1:]
            while left:
                last = chain[-1]
                nxt = min(left, key=lambda q: abs(q[0] - last[0]) + abs(q[1] - last[1]))
                chain.append(nxt)
                left.remove(nxt)
            out.append(chain)
            start = chain[-1]
        return W, H, out

    def pool(self, level, biome):
        allowed = BIOME_MONSTERS.get(biome, {'temperate', 'underground'})
        cand = [(k, m['level']) for k, m in self.mon.items()
                if not m['boss'] and k not in NOT_UNDERGROUND and k not in HAND_PLACED and m['biome'] in allowed
                and level * 0.55 <= m['level'] <= level * 1.6 + 4]
        if len(cand) < 3:
            ranked = sorted(((k, m['level']) for k, m in self.mon.items()
                             if not m['boss'] and k not in NOT_UNDERGROUND and k not in HAND_PLACED),
                            key=lambda km: abs(km[1] - level))
            cand = ranked[:4]
        return [k for k, _l in cand]

    def mine_dressing(self, g, rng, pts):
        """Drewniane stemple przy scianach chodnikow i glazy rudy w komorach."""
        h, w = len(g), len(g[0])
        for y in range(h):
            for x in range(w):
                c = g[y][x]
                if c is None or c[3] == NONE:
                    continue
                touches = any(0 <= x + a < w and 0 <= y + b < h and g[y + b][x + a] is not None and g[y + b][x + a][3] == NONE
                              for a, b in F.DIRS4)
                if touches and (x * 7 + y * 3) % 5 == 0:
                    g[y][x] = (c[0], NONE, NONE, 'timber_wall')
        for y in range(1, h - 1):
            for x in range(1, w - 1):
                c = g[y][x]
                if c is None or c[3] != NONE or c[2] != NONE or any(abs(x - px) + abs(y - py) < 4 for px, py in pts):
                    continue
                open8 = all(g[y + b][x + a] is not None and g[y + b][x + a][3] == NONE and g[y + b][x + a][2] == NONE
                            for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b)
                if open8 and rng.random() < 0.035:
                    g[y][x] = (c[0], NONE, rng.choice(('rock', 'rock', 'crystal', 'rubble')), NONE)

    # ------------------------------------------------------------------ jeden loch
    def dungeon(self, kind, name, site, rng):
        world, ctx = self.world, self.ctx
        ex, ey = site
        level = self.area_level(ex, ey)
        biome = str(self.T.biome[ey, ex])
        floors = 1 + (rng.random() < 0.75) + (level >= 60 and rng.random() < 0.5)
        W, H, chains = self.layout(rng, floors)
        if kind == 'cave':
            style = {'snow': 'ice', 'taiga': 'ice', 'volcanic': 'fire', 'jungle': 'moss', 'swamp': 'moss',
                     'forest': 'moss'}.get(biome, 'rock')
        else:
            style = 'mine' if kind == 'mine' else 'sewer'
        grids = {}
        for i, pts in enumerate(chains):
            z = -1 - i
            if kind == 'hole':
                g = F.carve_crypt(W, H, pts, style, rng)
            else:
                g = F.carve_cave(W, H, pts, style, rng, room=(3, 5) if kind == 'cave' else (2, 4))
            F.fix_lava(g, pts)
            if kind == 'mine':
                self.mine_dressing(g, rng, pts)
            assert all(p in F.reachable_local(g, pts[0]) for p in pts), name
            grids[z] = g
        import numpy as np
        masks = {}
        for z, g in grids.items():
            m = np.zeros((H, W), bool)
            for y in range(H):
                for x in range(W):
                    m[y, x] = g[y][x] is not None
            masks[z] = m
        p0 = chains[0][0]
        tx, ty = self.h['find_free'](world, masks, ex - p0[0], ey - p0[1])
        self.h['mark'](world, masks, tx, ty)
        for z, g in grids.items():
            for y in range(H):
                for x in range(W):
                    if g[y][x] is not None:
                        world.put(tx + x, ty + y, z, g[y][x])
        floor_of = {z: F.CAVE_STYLES[style][0] for z in grids}
        t = ctx['transitions']
        front = self.entrance(kind, site, rng)
        up = (tx + p0[0], ty + p0[1])
        if kind == 'hole':
            world.put(*up, -1, (floor_of[-1], NONE, 'ladder', NONE))
            land = (up[0] + 1, up[1]) if F.walk(world.get(up[0] + 1, up[1], -1)) else (up[0], up[1] + 1)
            t.append((ex, ey, 0, 'HOLE', land[0], land[1], -1))
            t.append((up[0], up[1], -1, 'LADDER', front[0], front[1], 0))
        else:
            world.put(*up, -1, (floor_of[-1], NONE, 'stairs_up', NONE))
            t.append((ex, ey, 0, 'STAIRS', up[0], up[1] + 1, -1))
            t.append((up[0], up[1], -1, 'STAIRS', front[0], front[1], 0))
        for i in range(len(chains) - 1):
            z = -1 - i
            d = (tx + chains[i][-1][0], ty + chains[i][-1][1])
            world.put(*d, z, (floor_of[z], NONE, 'stairs_down', NONE))
            world.put(*d, z - 1, (floor_of[z - 1], NONE, 'stairs_up', NONE))
            t.append((d[0], d[1], z, 'STAIRS', d[0], d[1] + 1, z - 1))
            t.append((d[0], d[1], z - 1, 'STAIRS', d[0] + 1, d[1], z))
        # potwory w komorach (bez komor ze schodami), ciala poleglych, skrzynie w ostatniej komorze
        spawns, chests, levels = 0, [], []
        for i, pts in enumerate(chains):
            z = -1 - i
            g = grids[z]
            lv = int(level * (1 + 0.35 * i))
            pool = self.pool(lv, biome)
            stairs = [pts[0]] + ([pts[-1]] if i + 1 < len(chains) else [])
            cells = [(x, y) for y in range(H) for x in range(W)
                     if g[y][x] is not None and F.walk(g[y][x]) and g[y][x][2] == NONE
                     and all(abs(x - px) + abs(y - py) > 5 for (px, py) in stairs)]
            rooms = [p for p in pts if p not in stairs]
            for j, want in enumerate(rooms):
                sx, sy = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]))
                key = pool[(j + rng.randint(0, len(pool) - 1)) % len(pool)]
                count = rng.choice((2, 3)) if lv < 40 else rng.choice((3, 3, 4))
                ctx['spawns'].append(f'{key};{tx + sx};{ty + sy};{z};{3 if j % 2 else 4};{count};180')
                levels.append(self.mon[key]['level'])
                spawns += 1
                # cialo poleglego poszukiwacza przygod obok komory
                for _ in range(6):
                    a, b = want[0] + rng.randint(-3, 3), want[1] + rng.randint(-3, 3)
                    if 0 <= a < W and 0 <= b < H and g[b][a] is not None and F.walk(g[b][a]) and g[b][a][2] == NONE \
                            and g[b][a][1] == NONE:
                        world.put(tx + a, ty + b, z, (g[b][a][0], rng.choice(('corpse', 'bones', 'blood')), NONE, NONE))
                        break
            if i == len(chains) - 1:
                last = pts[-1]
                spots = sorted((c for c in cells if world.get(tx + c[0], ty + c[1], z)[1] == NONE),
                               key=lambda c: abs(c[0] - last[0]) + abs(c[1] - last[1]) + (0 if c[1] != last[1] else 1))
                picked = []
                for c in spots:
                    if len(picked) >= (2 if len(chains) > 1 else 1):
                        break
                    if all(max(abs(c[0] - q[0]), abs(c[1] - q[1])) >= 3 for q in picked):
                        picked.append(c)
                # skrzynie tylko tam, dokad dojdzie sie pieszo z Varden (TreasureChests.reachable nie zna statkow)
                if self.h['reach_varden'][ey, ex]:
                    for c in picked:
                        chests.append((tx + c[0], ty + c[1], z) + loot(lv, rng))
        ctx.setdefault('loot_chests', []).extend(chests)
        lo = max(1, min(level, min(levels)))
        hi = max(levels) + 5
        ctx['regions_new'].insert(0, (name, tx, ty, tx + W - 1, ty + H - 1, -len(chains), -1, lo, hi))
        ctx.setdefault('dungeons', []).append((name, tx, ty, tx + W - 1, ty + H - 1, -len(chains), -1))
        self.trans.add((ex, ey))
        return dict(name=name, kind=kind, entrance=site, level=level, floors=len(chains), spawns=spawns,
                    chests=len(chests), front=front)

    def entrance(self, kind, site, rng):
        """Rysuje wejscie na powierzchni; zwraca pole przed wejsciem (tam wraca postac)."""
        world = self.world
        ex, ey = site
        ground = world.get(ex, ey)[0]
        biome = str(self.T.biome[ey, ex])
        rock = 'snow_mountain' if ground == 'snow' else ('sand_cliff' if ground in ('sand', 'dark_sand') else 'mountain')
        # polana: drzewa i krzaki w promieniu 4 znikaja, wejscie widac z daleka
        for b in range(-4, 5):
            for a in range(-4, 5):
                c = world.get(ex + a, ey + b)
                world.put(ex + a, ey + b, 0, (c[0], c[1] if max(abs(a), abs(b)) > 1 else NONE, NONE, NONE))
        if kind == 'cave':
            shape = [(a, -3) for a in (-1, 0, 1)] + [(a, -2) for a in range(-2, 3)] + [(a, -1) for a in range(-3, 4)] \
                + [(a, 0) for a in (-3, -2, -1, 1, 2, 3)]
            shape += [p for p in ((-2, -3), (2, -3), (-3, -2), (3, -2), (-4, -1), (4, -1), (-4, 0), (4, 0), (0, -4))
                      if rng.random() < 0.5]
            for (a, b) in shape:
                c = world.get(ex + a, ey + b)
                world.put(ex + a, ey + b, 0, (c[0], NONE, NONE, rock))
            world.put(ex, ey, 0, ('cave_floor', NONE, 'stairs_down', NONE))
            for a, deco in ((-1, 'debris'), (1, 'bones')):
                c = world.get(ex + a, ey + 1)
                world.put(ex + a, ey + 1, 0, (c[0], deco, NONE, NONE))
            c = world.get(ex, ey + 1)
            world.put(ex, ey + 1, 0, (c[0], NONE, NONE, NONE))
        elif kind == 'mine':
            for b in (-2, -1):
                for a in range(-2, 3):
                    c = world.get(ex + a, ey + b)
                    world.put(ex + a, ey + b, 0, (c[0], NONE, NONE, rock))
            for a in (-3, 3):
                if rng.random() < 0.6:
                    c = world.get(ex + a, ey - 1)
                    world.put(ex + a, ey - 1, 0, (c[0], NONE, NONE, rock))
            # rama z belek: stemple po bokach (z pochodnia) i belka nad wejsciem
            world.put(ex - 1, ey, 0, ('dirt', NONE, 'wall_torch', 'timber_wall'))
            world.put(ex + 1, ey, 0, ('dirt', NONE, NONE, 'timber_wall'))
            world.put(ex, ey - 1, 0, ('dirt', NONE, NONE, 'timber_wall'))
            world.put(ex, ey, 0, ('dirt', NONE, 'stairs_down', NONE))
            for b in (1, 2):
                for a in range(-2, 3):
                    world.put(ex + a, ey + b, 0, ('dirt', NONE, NONE, NONE))
            props = [((-2, 1), 'lumber'), ((2, 1), 'crate'), ((2, 2), 'barrel'), ((-2, 2), 'rock')]
            for (a, b), obj in props:
                world.put(ex + a, ey + b, 0, ('dirt', NONE, obj, NONE))
            world.put(ex - 1, ey + 2, 0, ('dirt', 'debris', NONE, NONE))
        else:
            ruin = [(-2, -2), (-1, -2), (1, -2), (2, -2), (-2, -1), (2, -1), (-2, 1), (2, 1)]
            for (a, b) in ruin:
                if rng.random() < 0.8:
                    c = world.get(ex + a, ey + b)
                    world.put(ex + a, ey + b, 0, (c[0], NONE, rng.choice(('broken_wall', 'rubble', 'broken_wall')), NONE))
            for (a, b) in ((-1, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (1, 1), (0, -1)):
                c = world.get(ex + a, ey + b)
                world.put(ex + a, ey + b, 0, ('dirt', rng.choice(('debris', 'bones', NONE, 'debris')), NONE, NONE))
            world.put(ex, ey, 0, ('dirt', NONE, 'hole', NONE))
            c = world.get(ex, ey + 1)
            world.put(ex, ey + 1, 0, ('dirt', NONE, NONE, NONE))
            c = world.get(ex, ey + 2)
            world.put(ex, ey + 2, 0, (c[0], 'corpse', NONE, NONE))
        _ = biome
        return (ex, ey + 1)


def loot(level, rng):
    """Skromny lup skrzyni w lochu dziczy: zloto wg poziomu i kilka mikstur."""
    gold = min(2500, int((40 + level * 5) * rng.uniform(0.8, 1.25)))
    if level < 30:
        item, n = rng.choice((('health_potion', 2), ('mana_potion', 2), ('health_potion', 3)))
    elif level < 120:
        item, n = rng.choice((('health_potion', 4), ('mana_potion', 4), ('strong_health_potion', 1)))
    else:
        item, n = rng.choice((('great_health_potion', 2), ('great_mana_potion', 3), ('strong_health_potion', 2)))
    return (gold, item, n)


def existing_entrances(ctx):
    world = ctx['world']
    out = []
    for (x, y, z, _t, tx, ty, tz) in ctx['transitions']:
        if z == 0 and tz < 0:
            c = world.get(x, y)
            if c is not None and c[2] == 'sewer_grate':
                continue
            out.append((x, y))
    return out


def wild_dungeons(ctx, cost, helpers, target=66, cover=44):
    w = Wilds(ctx, cost, helpers)
    rng = random.Random(SEED_DUNGEONS)
    S = w.S
    world = w.world
    ents = existing_entrances(ctx)
    roads = []
    for y in range(0, S, 2):
        for x in range(0, S, 2):
            c = world.get(x, y)
            if c is not None and c[0] == 'dirt' and c[3] == NONE and w.reach[y, x] and w.near_temple(x, y) > 40 \
                    and not w.blocked_area(x, y, 0):
                roads.append((x, y))
    land = []
    for y in range(10, S - 10, 6):
        for x in range(10, S - 10, 6):
            if w.reach[y, x] and helpers['natural'](world.get(x, y)) and w.near_temple(x, y) > 45:
                land.append((x, y))
    names = set()
    kinds = ['cave', 'mine', 'cave', 'hole', 'mine', 'cave', 'hole']
    out = []
    failed = set()

    def try_near(p, rmin, rmax):
        cands = []
        for r in range(rmin, rmax + 1, 2):
            for k in range(max(8, r)):
                ang = 2 * math.pi * k / max(8, r)
                cands.append((int(p[0] + r * math.cos(ang)), int(p[1] + r * math.sin(ang))))
        for (x, y) in cands:
            if any(abs(x - a) + abs(y - b) < 28 for a, b in ents):
                continue
            if w.near_temple(x, y) < 38:
                continue
            if w.site_ok(x, y):
                return (x, y)
        return None

    def place(site):
        kind = kinds[len(out) % len(kinds)]
        biome = str(w.T.biome[site[1], site[0]])
        if kind == 'hole' and biome in ('snow', 'volcanic'):
            kind = 'cave'
        base = {'cave': CAVE_NAMES, 'mine': MINE_NAMES, 'hole': HOLE_NAMES}[kind]
        for _ in range(200):
            name = f'{rng.choice(base)} {rng.choice(OF)}'
            if name not in names:
                break
        names.add(name)
        info = w.dungeon(kind, name, site, rng)
        ents.append(site)
        out.append(info)
        # sciezka do drogi (gdy blisko), zeby wejscie bylo widac z traktu
        road = helpers['nearest_road'](world, cost, info['front'], (site[0] - 4, site[1] - 4, site[0] + 4, site[1] + 4), 40)
        if road is not None:
            saved = cost[info['front'][1], info['front'][0]]
            cost[info['front'][1], info['front'][0]] = 1.0
            F.connect(world, w.T, cost, (info['front'][0], info['front'][1] + 1), road, width=1)
            cost[info['front'][1], info['front'][0]] = saved

    import numpy as np
    R = np.array(roads)
    while len(out) < target:
        E = np.array(ents)
        d = np.sqrt(((R[:, None, :] - E[None, :, :]) ** 2).sum(-1)).min(1)
        order = np.argsort(-d)
        site = None
        for i in order[:40]:
            if d[i] <= cover:
                break
            p = tuple(int(v) for v in R[i])
            if p in failed:
                continue
            site = try_near(p, 8, 26)
            if site:
                break
            failed.add(p)
        if site is None:
            break
        place(site)
    covered = len(out)
    L = np.array(land)
    while len(out) < target:
        E = np.array(ents)
        d = np.sqrt(((L[:, None, :] - E[None, :, :]) ** 2).sum(-1)).min(1)
        order = np.argsort(-d)
        site = None
        for i in order[:60]:
            if d[i] < 34:
                break
            p = tuple(int(v) for v in L[i])
            if p in failed:
                continue
            site = try_near(p, 0, 14)
            if site:
                break
            failed.add(p)
        if site is None:
            break
        place(site)
    ctx['wild_dungeons'] = out
    kinds_n = {k: sum(1 for o in out if o['kind'] == k) for k in ('cave', 'mine', 'hole')}
    return dict(new=len(out), for_roads=covered, kinds=kinds_n, spawns=sum(o['spawns'] for o in out),
                chests=sum(o['chests'] for o in out))


def surface_spawns(ctx, helpers):
    """Dodatkowe spawny niskiego i sredniego poziomu w lasach i na lakach (obok drog i w glebi lasow)."""
    world = ctx['world']
    T = ctx['T']
    S = len(world.floors[0])
    rng = random.Random(SEED_SPAWNS)
    w = Wilds(ctx, None, helpers)
    natural = helpers['natural']
    taken = []
    for line in ctx['spawns']:
        if not line.startswith('#'):
            f = line.split(';')
            if f[3] == '0':
                taken.append((int(f[1]), int(f[2])))
    import numpy as np
    occ = np.zeros((S // 4 + 1, S // 4 + 1), bool)

    def free(x, y, gap):
        for (a, b) in taken:
            if abs(a - x) < gap and abs(b - y) < gap:
                return False
        return True
    _ = occ
    roadmask = np.zeros((S, S), bool)
    for y in range(S):
        row = world.floors[0][y]
        for x in range(S):
            c = row[x]
            if c is not None and c[0] in F.ROAD_GROUND:
                roadmask[y, x] = True

    def ok(x, y):
        if not (6 < x < S - 6 and 6 < y < S - 6) or not w.reach[y, x] or helpers['footprint'][y, x]:
            return False
        c = world.get(x, y)
        if not natural(c) or c[2] != NONE:
            return False
        if roadmask[y - 2:y + 3, x - 2:x + 3].any():
            return False                 # obok drogi, ale nie na niej
        if w.blocked_area(x, y, 5) or (x, y) in w.trans:
            return False
        biome = str(T.biome[y, x])
        if biome not in LOW:
            return False
        zl = region_levels(x, y)
        return zl is not None and zl[0] <= 60
    regions = [r for r in helpers['regions']() if r[5] <= 0 <= r[6]]

    def region_levels(x, y):
        # pierwszy pasujacy region wygrywa (jak WorldMap.regionAt): wioski, wyspy, krainy
        for r in regions:
            if r[1] <= x <= r[3] and r[2] <= y <= r[4]:
                return r[7], r[8]
        return None
    out = ['# --- dodatkowe spawny w lasach i na lakach (wilds.py) ---']
    added = {'low': 0, 'mid': 0}

    def add(x, y):
        d = w.near_temple(x, y)
        if d < 32:
            return
        biome = str(T.biome[y, x])
        p_mid = 0.0 if d < 70 else (0.3 if d < 140 else 0.5)
        tier = 'mid' if rng.random() < p_mid else 'low'
        # potwor musi pasowac do poziomu krainy (region na mapie): od 0.8 dolnej granicy do gornej
        lo, hi = region_levels(x, y)
        fits = lambda ks: [k for k in ks if lo * 0.8 <= w.mon[k]['level'] <= hi]  # noqa: E731
        keys = fits((MID if tier == 'mid' else LOW)[biome])
        if not keys:
            tier = 'low' if tier == 'mid' else 'mid'
            keys = fits((MID if tier == 'mid' else LOW)[biome])
        if not keys:
            return
        key = rng.choice(keys)
        count = rng.choice((2, 3)) if tier == 'low' else rng.choice((2, 2, 3))
        out.append(f'{key};{x};{y};0;4;{count};{rng.choice((90, 120, 150))}')
        taken.append((x, y))
        added[tier] += 1
    # obok drog: co ~12 pol drogi spawn 4-9 pol w bok
    roads = [(x, y) for y in range(0, S, 3) for x in range(0, S, 3) if roadmask[y, x] and world.get(x, y)[0] == 'dirt'
             and w.reach[y, x]]
    rng.shuffle(roads)
    anchors = []
    for (x, y) in roads:
        if any(abs(x - a) < 12 and abs(y - b) < 12 for a, b in anchors):
            continue
        anchors.append((x, y))
        for _ in range(10):
            r = rng.randint(4, 9)
            ang = rng.random() * 2 * math.pi
            sx, sy = int(x + r * math.cos(ang)), int(y + r * math.sin(ang))
            if ok(sx, sy) and free(sx, sy, 9):
                add(sx, sy)
                break
    road_added = dict(added)
    # w glebi lasow i lak: siatka co 15 pol
    for gy in range(10, S - 10, 15):
        for gx in range(10, S - 10, 15):
            x, y = gx + rng.randint(-5, 5), gy + rng.randint(-5, 5)
            if ok(x, y) and free(x, y, 11):
                add(x, y)
    ctx['spawns'] += out
    return dict(roads=road_added, total=added)
