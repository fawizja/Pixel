"""Domy na sprzedaz poza murami miast i lozka w domach (2026-10-29, Darek: "domy na sprzedaz maja miec w srodku
kilka lozek, dotkniecie lozka kladzie postac i wylogowuje (jak w Tibii); sporo domow do kupienia w miastach i poza
nimi, nawet na wioskach").

- add_beds: 1-4 lozka we wnetrzu domu (wieksze domy wiecej) przy scianach, z dala od drzwi i tak, zeby kazde
  wolne pole wnetrza zostalo osiagalne od drzwi. Osobne ziarno z id domu - reszta mapy sie nie przesuwa.
- village_houses: dwa domy w kazdej zywej wiosce (ze strefa ochronna) na sprzedaz.
- homesteads: pojedyncze domy przy drogach - po dwa przy bramach kazdego miasta ("Przedmiescie") i kilka chat
  w dziczy z dala od miast; dom z drzwiami do drogi, sciezka do niej i strefa ochronna nad domem.
- check_houses: kontrola na koniec generowania (drzwi, lozka, przejscie od drzwi do kazdego pola).
"""
import camps
import random
from collections import deque

import features as F

NONE = '-'
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
# cena za pole wnetrza (gp); dzielnica krolewska Varden 2x (generate_continent REBUILD)
TOWN_PRICE = 1000
VILLAGE_PRICE = 600
OUTSIDE_PRICE = 800
NOT_LAND = ('water', 'lava', 'swamp', 'bridge', 'ice', 'ship_deck', 'rope_bridge')


def beds_for(size):
    """Ile lozek w domie o tylu polach wnetrza (Darek: 1-4, wieksze domy wiecej)."""
    if size <= 12:
        return 1
    if size <= 20:
        return 2
    if size <= 30:
        return 3
    return 4


def _free(c):
    return c is not None and c[3] == NONE and c[2] == NONE and F.walk(c)


def _connected(world, inner, door, block=()):
    walk = {p for p in inner if _free(world.get(p[0], p[1])) and p not in block}
    if not walk:
        return False
    start = next(((door[0] + a, door[1] + b) for a, b in DIRS4 if (door[0] + a, door[1] + b) in walk), None)
    if start is None:
        return False
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for a, b in DIRS4:
            n = (x + a, y + b)
            if n in walk and n not in seen:
                seen.add(n)
                q.append(n)
    return len(seen) == len(walk)


def add_beds(world, inner, door, seed, target=None):
    """Dokłada lozka do wnetrza (lista pol) az bedzie ich target (domyslnie wg wielkosci). Gdy przy scianach brak
    miejsca, lozko zastepuje inny mebel. Zwraca liczbe lozek."""
    inner = sorted(inner)
    target = beds_for(len(inner)) if target is None else target
    rng = random.Random(seed)
    have = sum(1 for (x, y) in inner if world.get(x, y)[2] == 'bed')

    def by_wall(p):
        return any(world.get(p[0] + a, p[1] + b)[3] != NONE for a, b in DIRS4)
    spots = [p for p in inner if abs(p[0] - door[0]) + abs(p[1] - door[1]) > 2 and by_wall(p)]
    rng.shuffle(spots)
    def roomy(p):
        # grafika lozka siega pola nad nim (lozko pionowe) albo z lewej (poziome): tam bez mebli, i bez lozek obok
        if any(world.get(p[0] + a, p[1] + b)[2] == 'bed' for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b):
            return False
        return all(world.get(p[0] + a, p[1] + b)[2] == NONE for a, b in ((0, -1), (-1, 0)))
    # najpierw wolne pola z miejscem wokol, potem pola z innym meblem (zamiana mebla na lozko), na koniec ciasno
    free = [q for q in spots if _free(world.get(*q))]
    other = [q for q in spots if world.get(*q)[2] not in (NONE, 'bed')]
    for tight, p in [(False, q) for q in free + other] + [(True, q) for q in free + other]:
        if have >= target:
            break
        c = world.get(*p)
        if c[3] != NONE or c[2] == 'bed' or c[2] in F.STAIRS or (not tight and not roomy(p)):
            continue
        world.put(p[0], p[1], 0, (c[0], NONE, 'bed', NONE))
        if _connected(world, inner, door):
            have += 1
        else:
            world.put(p[0], p[1], 0, c)
    # bardzo male wnetrze: lozko tez blizej drzwi, byle nie zamknelo przejscia
    if have == 0:
        for p in inner:
            c = world.get(*p)
            if p == door or not _free(c) or abs(p[0] - door[0]) + abs(p[1] - door[1]) <= 1:
                continue
            world.put(p[0], p[1], 0, (c[0], NONE, 'bed', NONE))
            if _connected(world, inner, door):
                have += 1
                break
            world.put(p[0], p[1], 0, c)
    return have


def furnish(world, inner, door, items, seed):
    """Kilka mebli przy scianach (z dala od drzwi), tylko gdy nie zamykaja przejscia do zadnego pola."""
    rng = random.Random(str(seed) + ':meble')
    spots = [p for p in sorted(inner) if abs(p[0] - door[0]) + abs(p[1] - door[1]) > 2
             and any(world.get(p[0] + a, p[1] + b)[3] != NONE for a, b in DIRS4)]
    rng.shuffle(spots)
    k = 0
    for p in spots:
        if k >= min(len(items), max(1, len(inner) // 5)):
            break
        c = world.get(*p)
        if not _free(c):
            continue
        world.put(p[0], p[1], 0, (c[0], NONE, items[k], NONE))
        if _connected(world, inner, door):
            k += 1
        else:
            world.put(p[0], p[1], 0, c)
    return k


def rect_cover(cells):
    import organic
    return organic.rect_cover(cells)


def house_row(hid, name, town, inner, door, price_per_cell):
    rects = rect_cover(set(inner))
    (x0, y0, x1, y1) = rects[0]
    extra = '/'.join(f'{a},{b},{c},{e}' for (a, b, c, e) in rects[1:])
    return [hid, name, town, x0, y0, x1, y1, 0, door[0], door[1], len(inner) * price_per_cell] + ([extra] if extra else [])


def nearest_town(ctx, p):
    return min(ctx['towns'], key=lambda t: abs(t[1][0] - p[0]) + abs(t[1][1] - p[1]))[0]


SLUG = str.maketrans('ąćęłńóśźżĄĆĘŁŃÓŚŹŻ ', 'acelnoszzACELNOSZZ_')


def slug(name):
    return name.translate(SLUG).lower()


def village_houses(ctx, per_village=2):
    """W kazdej zywej wiosce (ma strefe ochronna i kupca) dwa domy na sprzedaz; zwraca liczbe domow."""
    n = 0
    for v in ctx.get('villages', []):
        if v.get('kind', 'living') != 'living' or not _in_pz(ctx, *v['centre']):
            continue
        k = 0
        for (inner, door) in v['houses']:
            if k >= per_village:
                break
            if ctx['world'].get(*door)[2] != 'door_open' or not _connected(ctx['world'], inner, door):
                continue
            k += 1
            hid = f'wies_{slug(v["name"])}_{k}'
            add_beds(ctx['world'], inner, door, hid)
            ctx['houses'].append(house_row(hid, f'{v["name"]} {k}', nearest_town(ctx, door), inner, door, VILLAGE_PRICE))
            n += 1
    return n


def _in_pz(ctx, x, y, z=0):
    return any(r[0] <= x <= r[2] and r[1] <= y <= r[3] and r[4] == z for r in ctx['pz'])


WILD_NAMES = ['Chata Drwala', 'Leśniczówka', 'Chata Pustelnika', 'Dom przy Trakcie', 'Zagroda nad Strumieniem',
              'Chata Myśliwego', 'Dom Wędrowca', 'Chata Zielarki']
SIDES = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}


def _spot_ok(ctx, cells, spawns, region_level):
    w = ctx['world']
    fp = ctx['footprint']
    for (x, y) in cells:
        if not (4 <= x < len(fp) - 4 and 4 <= y < len(fp) - 4) or fp[y, x]:
            return False
        c = w.get(x, y)
        if c is None or c[0] in NOT_LAND or c[0] in F.ROAD_GROUND or c[3] != NONE or c[2] in F.STAIRS \
                or c[2] in ('door_open', 'sewer_grate'):
            return False
        if _in_pz(ctx, x, y) or any(w.get(x, y, z) is not None for z in (1, -1)):
            return False
        if any(abs(x - sx) <= sr + 2 and abs(y - sy) <= sr + 2 for (sx, sy, sr) in spawns):
            return False
        if region_level(x, y) > 45:
            return False
    return True


def _try_house(ctx, road, side, w, h, spawns, region_level, npcs):
    """Dom w x h przy polu drogi road, po stronie side; drzwi w srodku boku do drogi. None, gdy sie nie miesci."""
    d = SIDES[side]
    front = (road[0] + d[0], road[1] + d[1])           # pole przed drzwiami (sciezka)
    door = (road[0] + 2 * d[0], road[1] + 2 * d[1])
    if d[1]:
        x0 = door[0] - w // 2
        y0 = door[1] if d[1] > 0 else door[1] - h + 1
    else:
        y0 = door[1] - h // 2
        x0 = door[0] if d[0] > 0 else door[0] - w + 1
    rect = [(x, y) for y in range(y0 - 1, y0 + h + 1) for x in range(x0 - 1, x0 + w + 1)]
    if not _spot_ok(ctx, rect + [front], spawns, region_level) or front in npcs:
        return None
    return x0, y0, door, front


def homesteads(ctx, cost, extra_gates=None, per_town=2, wild=6):
    """Domy przy drogach poza murami: per_town przy bramach kazdego miasta i wild chat w dziczy. Zwraca liczby."""
    world = ctx['world']
    import numpy as np
    regions = ctx['regions'] + ctx.get('regions_new', [])

    def region_level(x, y):
        for r in regions:
            if r[1] <= x <= r[3] and r[2] <= y <= r[4] and r[5] <= 0 <= r[6]:
                return int(r[7])
        return 0
    spawns = []
    for ln in ctx['spawns']:
        if ln.startswith('#'):
            continue
        f = ln.split(';')
        if int(f[3]) == 0:
            spawns.append((int(f[1]), int(f[2]), int(f[4])))
    npcs = {(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0}
    S = len(world.floors[0])
    road = np.zeros((S, S), bool)
    g = world.floors[0]
    for y in range(S):
        row = g[y]
        for x in range(S):
            if row[x][0] == 'dirt' and row[x][3] == NONE and cost[y, x] <= 0.35:
                road[y, x] = True
    placed = []

    def build(name, hid, town, near, rmin, rmax, rng):
        cands = []
        nx, ny = near
        for yy in range(max(0, ny - rmax), min(S, ny + rmax + 1)):
            for xx in range(max(0, nx - rmax), min(S, nx + rmax + 1)):
                dd = abs(xx - nx) + abs(yy - ny)
                if road[yy, xx] and rmin <= dd <= rmax:
                    cands.append((dd, xx, yy))
        cands.sort()
        rng.shuffle(cands)
        cands.sort(key=lambda c: c[0] // 6)
        for (_dd, xx, yy) in cands[:400]:
            if any(abs(xx - p[0]) + abs(yy - p[1]) < 14 for p in placed):
                continue
            for side in rng.sample('NSEW', 4):
                for (w, h) in rng.sample([(7, 6), (6, 6), (7, 5), (8, 6), (6, 5)], 5):
                    got = _try_house(ctx, (xx, yy), side, w, h, spawns, region_level, npcs)
                    if got is None:
                        continue
                    x0, y0, door, front = got
                    floor_ground = g[front[1]][front[0]][0]
                    biome = ctx['T'].biome[y0 + h // 2, x0 + w // 2]
                    _ground, wall, floor, _tree = F.BIOME_STYLE.get(biome, F.BIOME_STYLE['temperate'])
                    # podworko: obejscie domu bez drzew i krzakow
                    for y in range(y0 - 1, y0 + h + 1):
                        for x in range(x0 - 1, x0 + w + 1):
                            c = g[y][x]
                            if c[2] != NONE:
                                g[y][x] = (c[0], c[1], NONE, NONE)
                    inner, door2 = F.house(world, x0, y0, w, h, wall, floor, {(0, 1): 'N', (0, -1): 'S', (1, 0): 'W',
                                                                             (-1, 0): 'E'}[SIDES[side]], rng, furniture=False)
                    assert door2 == door, (door2, door)
                    g[front[1]][front[0]] = ('dirt' if floor_ground not in NOT_LAND else floor_ground, NONE, NONE, NONE)
                    add_beds(world, inner, door, hid)
                    furnish(world, inner, door, ['table', 'chair', 'chest', 'wardrobe'], hid)
                    for y in range(y0 - 1, y0 + h + 1):
                        for x in range(x0 - 1, x0 + w + 1):
                            ctx['footprint'][y, x] = True
                            cost[y, x] = np.inf if (x0 <= x < x0 + w and y0 <= y < y0 + h) else cost[y, x]
                    cost[front[1], front[0]] = 0.35
                    ctx['pz'].append((x0, y0, x0 + w - 1, y0 + h - 1, 0))
                    ctx['pz'].append((front[0], front[1], front[0], front[1], 0))
                    ctx['houses'].append(house_row(hid, name, town, inner, door, OUTSIDE_PRICE))
                    placed.append(door)
                    return True
        return False
    rng = random.Random(20261029)
    per = {}
    gates = {}
    for t in ctx['new_towns'] + list(ctx['rebuilt'].values()):
        gates.setdefault(t['name'], {}).update(t['gates'])
    for town, extra in (extra_gates or {}).items():
        gates.setdefault(town, {}).update(extra)
    for town, gs in sorted(gates.items()):
        k = 0
        prefix = next((h[0].rsplit('_', 1)[0] for h in ctx['houses'] if h[2] == town), slug(town))
        for side in sorted(gs):
            if k >= per_town:
                break
            if build(f'{town} - Przedmieście {k + 1}', f'{prefix}_p{k + 1}', town, gs[side], 6, 34, rng):
                k += 1
        per[town] = k
    temples = [p for _n, p in ctx['towns']]
    villages = [v['centre'] for v in ctx.get('villages', [])]
    k = 0
    # chaty w dziczy: pola drog daleko od miast i wiosek, rozsiane po mapie
    far = []
    for y in range(20, S - 20, 9):
        for x in range(20, S - 20, 9):
            if road[y, x] and all(abs(x - t[0]) + abs(y - t[1]) > 70 for t in temples) \
                    and all(abs(x - v[0]) + abs(y - v[1]) > 30 for v in villages) and region_level(x, y) <= 45 \
                    and not any(a <= x <= c and b <= y <= d for (a, b, c, d) in camps.HOLD):
                far.append((x, y))
    rng.shuffle(far)
    chosen = []
    for p in far:
        if all(abs(p[0] - q[0]) + abs(p[1] - q[1]) > 110 for q in chosen):
            chosen.append(p)
    for p in chosen:
        if k >= wild:
            break
        name = WILD_NAMES[k % len(WILD_NAMES)]
        if build(name, f'wild_{k + 1}', nearest_town(ctx, p), p, 0, 8, rng):
            k += 1
    return per, k


class _Floor:
    """Widok jednego pietra swiata z get(x, y) jak powierzchnia (check_houses, _connected)."""

    def __init__(self, world, z):
        self.world, self.z = world, z

    def get(self, x, y, z=None):
        return self.world.get(x, y, self.z)


def check_houses(ctx):
    """Kazdy dom na sprzedaz: drzwi w murze, 1-4 lozka, kazde wolne pole wnetrza osiagalne od drzwi."""
    W = ctx['world']
    bad = []
    stats = {}
    for h in ctx['houses']:
        hid, x0, y0, x1, y1 = h[0], int(h[3]), int(h[4]), int(h[5]), int(h[6])
        world = _Floor(W, int(h[7]))          # domy pod ziemia (Durnkar na -5)
        door = (int(h[8]), int(h[9]))
        cells = {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}
        if len(h) > 11:
            for part in h[11].split('/'):
                a, b, c, e = (int(v) for v in part.split(','))
                cells |= {(x, y) for y in range(b, e + 1) for x in range(a, c + 1)}
        beds = sum(1 for (x, y) in cells if world.get(x, y)[2] == 'bed')
        if world.get(*door)[2] != 'door_open':
            bad.append((hid, 'door', world.get(*door)))
        elif not 1 <= beds <= 4:
            bad.append((hid, 'beds', beds))
        elif not _connected(world, cells, door):
            for yy in range(y0 - 2, y1 + 3):
                print(' '.join(('D' if (xx, yy) == door else 'W' if world.get(xx, yy)[3] != NONE else
                                (world.get(xx, yy)[2][:3] if world.get(xx, yy)[2] != NONE else '.')).ljust(3) for xx in range(x0 - 2, x1 + 3)))
            bad.append((hid, 'blocked', h, door, sorted((p, world.get(*p)) for p in cells if not _free(world.get(*p)))))
        stats[h[2]] = stats.get(h[2], 0) + 1
    assert not bad, f'houses: {bad}'
    return stats
