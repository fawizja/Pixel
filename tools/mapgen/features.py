"""Nowy kontynent (generate_continent.py): drogi z mostami, przystanie, statki, wioski, jaskinie, wulkan.

Wszystko pracuje na pietrach World (listy krotek kafli town.py: ground, deco, obj, wall; None = pusty kafel).
"""
import heapq
import math
import random
from collections import deque

import numpy as np

import town as tk

NONE = '-'
WATER = ('water', NONE, NONE, NONE)
ROAD = ('dirt', NONE, NONE, NONE)
BRIDGE = ('bridge', NONE, NONE, NONE)
ROAD_GROUND = {'dirt', 'cobblestone', 'paving', 'brick_paving', 'sandstone_floor', 'stone_floor', 'bridge'}
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
STAIRS = {'stairs_up', 'stairs_down', 'ladder', 'hole', 'rope_spot'}


def walk(c):
    return c is not None and tk.walkable(c)


# ------------------------------------------------------------------ drogi

def road_cost(world, T, footprint):
    S = len(world.floors[0])
    g = world.floors[0]
    cost = np.ones((S, S))
    for y in range(S):
        row = g[y]
        for x in range(S):
            c = row[x]
            if footprint[y, x]:
                cost[y, x] = np.inf
            elif c[0] == 'water' or c[0] == 'ice':
                cost[y, x] = 14.0 if (T.river[y, x] or T.lake[y, x]) and c[0] == 'water' else (6.0 if c[0] == 'ice' else np.inf)
            elif c[3] != NONE:
                cost[y, x] = 30.0
            elif c[0] in ROAD_GROUND:
                cost[y, x] = 0.35
            elif c[0] == 'swamp':
                cost[y, x] = 5.0
            elif c[2] != NONE:
                cost[y, x] = 2.5
            elif c[0] == 'lava':
                cost[y, x] = np.inf
    cost[T.coast_dist <= 1] += 2.0
    return cost


DIRS8 = DIRS4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))


def astar(cost, start, goal, hw=0.8, diag=False):
    """Najtansza droga 4-sasiedztwem (heurystyka manhattan * hw); diag: tez po skosie (koszt * 1.41)."""
    S = cost.shape[0]
    sx, sy = start
    gx, gy = goal
    best = {start: 0.0}
    prev = {start: None}
    heap = [(0.0, 0.0, start)]
    while heap:
        _f, d, p = heapq.heappop(heap)
        if p == goal:
            break
        if d > best.get(p, 1e18):
            continue
        x, y = p
        for dx, dy in (DIRS8 if diag else DIRS4):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < S and 0 <= ny < S):
                continue
            c = cost[ny, nx]
            if (nx, ny) == goal:
                c = 1.0
            if c == np.inf:
                continue
            if dx and dy:
                # skos tylko gdy oba pola obok sa przejezdne (droga nie przecina rogu muru ani brzegu)
                if cost[y, nx] == np.inf or cost[ny, x] == np.inf:
                    continue
                c *= 1.41
            nd = d + c
            if nd < best.get((nx, ny), 1e18):
                best[(nx, ny)] = nd
                prev[(nx, ny)] = p
                h = (max(abs(gx - nx), abs(gy - ny)) + 0.41 * min(abs(gx - nx), abs(gy - ny)) if diag
                     else abs(gx - nx) + abs(gy - ny)) * hw
                heapq.heappush(heap, (nd + h, nd, (nx, ny)))
    if goal not in prev:
        return None
    out = []
    p = goal
    while p is not None:
        out.append(p)
        p = prev[p]
    out.reverse()
    return out


def paint_road(world, T, path, cost, width=2, ground='dirt', keep=None):
    """Droga po sciezce: 2 pola szeroka, przez rzeki most, przez gory przeciete przejscie."""
    g = world.floors[0]
    S = len(g)
    bridges = []
    cells = set()
    for i, (x, y) in enumerate(path):
        cells.add((x, y))
        if width > 1:
            # drugi pas po prawej stronie kierunku
            nx_, ny_ = path[min(i + 1, len(path) - 1)]
            px_, py_ = path[max(i - 1, 0)]
            dx, dy = nx_ - px_, ny_ - py_
            if abs(dx) >= abs(dy):
                cells.add((x, y + 1))
            else:
                cells.add((x + 1, y))
    for (x, y) in cells:
        if not (0 <= x < S and 0 <= y < S) or (keep is not None and keep[y, x]):
            continue
        c = g[y][x]
        if c[2] in STAIRS:
            continue
        if c[0] == 'water':
            if not (T.river[y, x] or T.lake[y, x]):
                continue
            g[y][x] = BRIDGE
            bridges.append((x, y))
        elif c[0] in ROAD_GROUND and c[3] == NONE:
            continue
        else:
            gr = ground
            if c[0] == 'ice':
                gr = 'ice'
            g[y][x] = (gr, NONE, NONE, NONE)
        cost[y, x] = min(cost[y, x], 0.35)
    return bridges


_WIGGLE = {}


def wiggle_field(shape):
    """Gladki szum 0..1 (dwie skale): mnoznik kosztu pol poza drogami, zeby drogi kluczyly zamiast isc prosto."""
    if shape not in _WIGGLE:
        rs = np.random.RandomState(20261028)
        out = np.zeros(shape)
        for base, amp in ((31, 0.65), (11, 0.35)):
            n = shape[0] // base + 2
            g = rs.uniform(0, 1, (n, n))
            ys, xs = np.mgrid[0:shape[0], 0:shape[1]] / base
            x0, y0 = xs.astype(int), ys.astype(int)
            fx, fy = xs - x0, ys - y0
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            top = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
            bot = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
            out += amp * (top * (1 - fy) + bot * fy)
        _WIGGLE[shape] = out
    return _WIGGLE[shape]


def winding_path(cost, a, b):
    """Droga z zakretami (2026-10-28, Darek: "drogi nie maja byc prostymi kreskami"): przez punkty posrednie
    odsuniete na przemian w bok od prostej a-b, po terenie z szumem kosztu (laczy sie z istniejacymi drogami)."""
    ax, ay = a
    bx, by = b
    length = abs(bx - ax) + abs(by - ay)
    rng = random.Random(ax * 7919 + ay * 104729 + bx * 31 + by)
    field = wiggle_field(cost.shape)
    noisy = np.where((cost >= 1.0) & np.isfinite(cost), cost * (1.0 + 2.2 * field), cost)
    points = [a]
    if length >= 36:
        n = max(1, length // 45)
        dx, dy = (bx - ax) / length, (by - ay) / length
        side = rng.choice((-1, 1))
        for i in range(1, n + 1):
            t = i / (n + 1) + rng.uniform(-0.08, 0.08)
            amp = side * min(22, 0.18 * length) * rng.uniform(0.5, 1.0)
            px, py = int(ax + (bx - ax) * t - dy * amp), int(ay + (by - ay) * t + dx * amp)
            best = None
            for r in range(0, 7):
                for qy in range(py - r, py + r + 1):
                    for qx in range(px - r, px + r + 1):
                        if 0 <= qx < cost.shape[1] and 0 <= qy < cost.shape[0] and cost[qy, qx] <= 3.0:
                            best = (qx, qy)
                            break
                    if best:
                        break
                if best:
                    break
            if best:
                points.append(best)
            side = -side
    points.append(b)
    path = [a]
    for p, q in zip(points, points[1:]):
        seg = astar(noisy, p, q, diag=True)
        if seg is None:
            seg = astar(cost, p, q)
        if seg is None:
            return astar(cost, a, b)
        path += seg[1:]
    # bez petli: przy powrocie na pole juz odwiedzone wycinamy kawalek miedzy wizytami
    seen, out = {}, []
    for p in path:
        if p in seen:
            out = out[:seen[p] + 1]
            seen = {q: i for i, q in enumerate(out)}
            continue
        seen[p] = len(out)
        out.append(p)
    # krok po skosie dopelniony polem obok (droga zostaje spojna dla chodzenia w czterech kierunkach)
    full = out[:1]
    for (px, py), (qx, qy) in zip(out, out[1:]):
        if px != qx and py != qy:
            full.append((qx, py) if np.isfinite(cost[py, qx]) else (px, qy))
        full.append((qx, qy))
    return full


def connect(world, T, cost, a, b, width=2, keep=None):
    path = winding_path(cost, a, b)
    assert path is not None, f'no road {a} -> {b}'
    return paint_road(world, T, path, cost, width, keep=keep), path


# ------------------------------------------------------------------ przystanie i statki

N, S_, E, W = (0, -1), (0, 1), (1, 0), (-1, 0)


def to_water(world, start, d, limit=60):
    """Ostatnie pole ladu przed woda idac od start w kierunku d."""
    g = world.floors[0]
    x, y = start
    for _ in range(limit):
        nx, ny = x + d[0], y + d[1]
        if g[ny][nx][0] == 'water':
            return x, y
        x, y = nx, ny
    return None


def small_dock(world, start, d, pier=5, boat=True):
    """Mala przystan: pomost 3x2 na brzegu, molo w morze i lodka. Zwraca (pole na pomoscie, prostokat)."""
    g = world.floors[0]
    r = to_water(world, start, d)
    if r is None:
        return None
    rx, ry = r
    px, py = abs(d[1]), abs(d[0])
    tiles = []
    for back in (0, 1):
        for side in (-1, 0, 1):
            tiles.append((rx - d[0] * back + px * side, ry - d[1] * back + py * side))
    for k in range(1, pier + 1):
        for side in (0, 1):
            tiles.append((rx + d[0] * k + px * side, ry + d[1] * k + py * side))
    for (x, y) in tiles:
        g[y][x] = BRIDGE
    if boat:
        ex, ey = rx + d[0] * pier + px * 2, ry + d[1] * pier + py * 2
        if g[ey][ex][0] == 'water':
            g[ey][ex] = ('water', NONE, 'rowboat', NONE)
    spot = (rx - px, ry - py)
    xs = [x for x, _ in tiles]
    ys = [y for _, y in tiles]
    return spot, (min(xs) - 1, min(ys) - 1, max(xs) + 1, max(ys) + 1)


# duzy statek (widok z gory, dziob w strone d): '#' burta, '.' poklad, 'm' maszt, 'c' armata, 'b' beczka,
# 'w' ster (kolo), 'h' luk (zejscie pod poklad - tu tylko ozdoba), ' ' woda
SHIP = [
    '   ###   ',
    '  #...#  ',
    ' #.....# ',
    ' #c...c# ',
    '#...m...#',
    '#.......#',
    '#c.....c#',
    '#...b...#',
    '#.......#',
    '#c..m..c#',
    '#.......#',
    '#b.....b#',
    '#...w...#',
    ' #######  ',
]


def rot_ship(d):
    rows = [r.ljust(9) for r in SHIP]
    if d == N:
        return rows
    if d == S_:
        return [r[::-1] for r in rows[::-1]]
    h, w = len(rows), len(rows[0])
    if d == E:      # dziob na wschod: obrot zgodnie z ruchem wskazowek
        return [''.join(rows[h - 1 - j][i] for j in range(h)) for i in range(w)]
    return [''.join(rows[j][w - 1 - i] for j in range(h)) for i in range(w)]   # W


def big_ship(world, x0, y0, d):
    """Duzy statek na wodzie (lewy gorny rog x0, y0), dziobem w kierunku d. Zwraca pola pokladu."""
    g = world.floors[0]
    rows = rot_ship(d)
    deck = []
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            x, y = x0 + i, y0 + j
            if ch == ' ':
                continue
            if ch == '#':
                g[y][x] = ('bridge', NONE, NONE, 'ship_hull')
            elif ch == 'm':
                g[y][x] = ('ship_deck', NONE, 'ship_mast', NONE)
            elif ch == 'c':
                g[y][x] = ('ship_deck', NONE, 'cannon', NONE)
            elif ch == 'b':
                g[y][x] = ('ship_deck', NONE, 'barrel', NONE)
            elif ch == 'w':
                g[y][x] = ('ship_deck', NONE, 'ship_wheel', NONE)
            else:
                g[y][x] = ('ship_deck', NONE, NONE, NONE)
                deck.append((x, y))
    return deck, rows


def harbour_with_ship(world, start, d, pier=4):
    """Nabrzeze, molo i duzy statek przycumowany burta do konca mola; kapitan stoi na pokladzie.
    Zwraca (pole kapitana, prostokat strefy ochronnej)."""
    g = world.floors[0]
    r = to_water(world, start, d)
    assert r is not None, f'no water from {start}'
    rx, ry = r
    px, py = abs(d[1]), abs(d[0])
    tiles = []
    for back in (0, 1, 2):
        for side in (-2, -1, 0, 1, 2):
            tiles.append((rx - d[0] * back + px * side, ry - d[1] * back + py * side))
    for k in range(1, pier + 1):
        for side in (0, 1):
            tiles.append((rx + d[0] * k + px * side, ry + d[1] * k + py * side))
    for (x, y) in tiles:
        g[y][x] = BRIDGE
    # statek bokiem do mola: dziob prostopadle do d
    ex, ey = rx + d[0] * (pier + 1), ry + d[1] * (pier + 1)
    sd = E if d in (N, S_) else S_
    rows = rot_ship(sd)
    h, w = len(rows), len(rows[0])
    if d == S_:
        x0, y0 = ex - w // 2, ey
    elif d == N:
        x0, y0 = ex - w // 2, ey - h + 1
    elif d == E:
        x0, y0 = ex, ey - h // 2
    else:
        x0, y0 = ex - w + 1, ey - h // 2
    deck, rows = big_ship(world, x0, y0, sd)
    # trap z mola na poklad: wyciecie burty naprzeciw konca mola
    gx, gy = ex, ey
    for _ in range(3):
        c = g[gy][gx]
        if c[3] == 'ship_hull':
            g[gy][gx] = ('ship_deck', NONE, NONE, NONE)
            if d in (N, S_):
                g[gy][gx + 1] = ('ship_deck', NONE, NONE, NONE) if g[gy][gx + 1][3] == 'ship_hull' else g[gy][gx + 1]
            else:
                g[gy + 1][gx] = ('ship_deck', NONE, NONE, NONE) if g[gy + 1][gx][3] == 'ship_hull' else g[gy + 1][gx]
            break
        if c[0] == 'water':
            g[gy][gx] = BRIDGE
            if d in (N, S_):
                g[gy][gx + 1] = BRIDGE
            else:
                g[gy + 1][gx] = BRIDGE
        gx, gy = gx + d[0], gy + d[1]
    # kapitan: pole pokladu najblizej trapu
    captain = min(deck, key=lambda p: abs(p[0] - gx) + abs(p[1] - gy) + (0 if p != (gx + d[0], gy + d[1]) else 5))
    xs = [x for x, _ in tiles] + [x0, x0 + w - 1]
    ys = [y for _, y in tiles] + [y0, y0 + h - 1]
    return captain, (min(xs) - 1, min(ys) - 1, max(xs) + 1, max(ys) + 1)


# ------------------------------------------------------------------ wioski

BIOME_STYLE = {
    # biom: (podloze, mur domow, podloga, drzewo)
    'snow': ('snow', 'log_wall', 'wood_floor', 'frost_pine'),
    'taiga': ('snow', 'log_wall', 'wood_floor', 'frost_pine'),
    'temperate': ('grass', 'timber_wall', 'wood_floor', 'tree'),
    'hills': ('grass', 'plaster_wall', 'wood_floor', 'tree'),
    'forest': ('grass', 'wood_wall', 'wood_floor', 'tree'),
    'cursed': ('blighted_grass', 'wood_wall', 'wood_floor', 'dead_tree'),
    'desert': ('sand', 'sandstone_wall', 'sandstone_floor', 'palm'),
    'savanna': ('sand', 'sandstone_wall', 'sandstone_floor', 'palm'),
    'shadow': ('dark_sand', 'sandstone_wall', 'sandstone_floor', 'dead_tree'),
    'jungle': ('jungle_grass', 'wood_wall', 'wood_floor', 'jungle_tree'),
    'swamp': ('jungle_grass', 'wood_wall', 'wood_floor', 'jungle_tree'),
    'volcanic': ('ash', 'stone_wall', 'stone_floor', 'dead_tree'),
}
HOME_FURNITURE = ['bed', 'table', 'chair', 'chest', 'wardrobe', 'barrel', 'bookcase', 'potted_plant']
CAMP_PROPS = ['orc_tent', 'campfire', 'barrel', 'crate', 'orc_tent', 'firewood']


def find_site(world, T, footprint, near, w, h, radius=30):
    """Lewy gorny rog prostokata w x h najblizej near: bez wody, klockow, schodow; malo gor."""
    g = world.floors[0]
    S = len(g)
    best = None
    cx, cy = near
    for r in range(0, radius + 1, 2):
        for dy in range(-r, r + 1, 2):
            for dx in range(-r, r + 1, 2):
                if max(abs(dx), abs(dy)) != r:
                    continue
                x0, y0 = cx + dx - w // 2, cy + dy - h // 2
                if x0 < 3 or y0 < 3 or x0 + w > S - 3 or y0 + h > S - 3:
                    continue
                if footprint[y0 - 2:y0 + h + 2, x0 - 2:x0 + w + 2].any():
                    continue
                if T.water[y0 - 1:y0 + h + 1, x0 - 1:x0 + w + 1].any():
                    continue
                bad = 0
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        c = g[y][x]
                        if c[0] in ('water', 'lava', 'swamp', 'bridge', 'ice') or c[2] in STAIRS:
                            bad += 1000
                        elif c[3] != NONE:
                            bad += 3
                        elif c[0] in ROAD_GROUND:
                            bad += 1
                score = bad + r * 0.5
                if bad < 1000 and (best is None or score < best[0]):
                    best = (score, x0, y0)
        if best is not None and best[0] < r * 0.5 + 40:
            break
    assert best is not None, f'no village site near {near}'
    return best[1], best[2]


def house(world, x0, y0, w, h, wall, floor, door_side, rng, furniture=True, ruined=0.0, burnt=False):
    """Dom: mury na obwodzie, podloga, drzwi w srodku boku door_side ('N','S','E','W'). Zwraca (wnetrze, drzwi)."""
    g = world.floors[0]
    inner = []
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if edge:
                g[y][x] = (floor, NONE, NONE, wall)
            else:
                g[y][x] = (floor, NONE, NONE, NONE)
                inner.append((x, y))
    if door_side == 'S':
        door = (x0 + w // 2, y0 + h - 1)
    elif door_side == 'N':
        door = (x0 + w // 2, y0)
    elif door_side == 'E':
        door = (x0 + w - 1, y0 + h // 2)
    else:
        door = (x0, y0 + h // 2)
    if ruined > 0:
        # zburzony dom: dziury w murach, gruz i spalone belki, bez drzwi
        g[door[1]][door[0]] = (floor, NONE, NONE, NONE)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = g[y][x]
                if c[3] != NONE and rng.random() < ruined:
                    g[y][x] = (floor, NONE, rng.choice(['broken_wall', 'rubble', NONE, NONE]), NONE)
        for (x, y) in inner:
            if rng.random() < 0.12:
                g[y][x] = (floor, 'bones' if burnt and rng.random() < 0.5 else NONE, rng.choice(['rubble', 'firewood', 'barrel']), NONE)
    else:
        g[door[1]][door[0]] = (floor, NONE, 'door_open', NONE)
        if furniture:
            # meble przy scianach, nie przy drzwiach
            spots = [(x, y) for (x, y) in inner if (x in (x0 + 1, x0 + w - 2) or y in (y0 + 1, y0 + h - 2))
                     and abs(x - door[0]) + abs(y - door[1]) > 2]
            rng.shuffle(spots)
            for i, (x, y) in enumerate(spots[:max(1, len(inner) // 5)]):
                g[y][x] = (floor, NONE, HOME_FURNITURE[i % len(HOME_FURNITURE)], NONE)
    return inner, door


def village(world, T, footprint, near, kind, rng, w=24, h=18):
    """Wioska: domy wokol placu, studnia albo ognisko, pola z plotem. kind: living / deserted / occupied.
    Zwraca slownik: prostokat, srodek, wolne pola placu, domy."""
    x0, y0 = find_site(world, T, footprint, near, w, h)
    biome = T.biome[y0 + h // 2, x0 + w // 2]
    ground, wall, floor, tree = BIOME_STYLE.get(biome, BIOME_STYLE['temperate'])
    g = world.floors[0]
    # polana
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = g[y][x]
            if c[0] in ROAD_GROUND:
                continue
            g[y][x] = (ground, 'tall_grass' if ground == 'grass' and rng.random() < 0.08 else NONE, NONE, NONE)
    cx, cy = x0 + w // 2, y0 + h // 2
    # plac i drozki
    for y in range(cy - 2, cy + 2):
        for x in range(cx - 3, cx + 3):
            g[y][x] = ('dirt', NONE, NONE, NONE)
    centre_obj = 'well' if kind != 'occupied' else 'campfire'
    if kind == 'deserted':
        centre_obj = 'well'
    g[cy - 1][cx - 1] = ('dirt', NONE, centre_obj, NONE)
    houses = []
    slots = [(x0 + 1, y0 + 1, 'S'), (x0 + 9, y0 + 1, 'S'), (x0 + w - 7, y0 + 1, 'S'),
             (x0 + 1, y0 + h - 6, 'N'), (x0 + w - 7, y0 + h - 6, 'N')]
    rng.shuffle(slots)
    n_houses = 4 if kind != 'deserted' else 3
    for (hx, hy, side) in slots[:n_houses]:
        hw, hh = rng.choice([(6, 5), (5, 5), (6, 4)])
        if kind == 'living':
            inner, door = house(world, hx, hy, hw, hh, wall, floor, side, rng)
        elif kind == 'deserted':
            inner, door = house(world, hx, hy, hw, hh, wall, floor, side, rng, furniture=False, ruined=0.45, burnt=True)
        else:
            inner, door = house(world, hx, hy, hw, hh, wall, floor, side, rng, furniture=False, ruined=0.5, burnt=True)
        houses.append((inner, door))
        # drozka od drzwi do placu
        dx = 0
        px, py = door
        step = 1 if side == 'S' else -1
        py += step
        while not (cy - 2 <= py <= cy + 1) and 0 < py < len(g) - 1:
            if g[py][px][3] == NONE and g[py][px][2] in (NONE,):
                g[py][px] = ('dirt', NONE, NONE, NONE)
            py += step
        while px != cx and abs(px - cx) > 3:
            px += 1 if cx > px else -1
            if g[py][px][3] == NONE and g[py][px][2] == NONE:
                g[py][px] = ('dirt', NONE, NONE, NONE)
        _ = dx
    # pole z plotem (wioski zywe i opuszczone - zarosniete)
    fx0, fy0 = x0 + 8, y0 + h - 6
    if all(g[y][x][3] == NONE and g[y][x][2] == NONE for y in range(fy0, fy0 + 4) for x in range(fx0, fx0 + 7)):
        for y in range(fy0, fy0 + 4):
            for x in range(fx0, fx0 + 7):
                edge = x in (fx0, fx0 + 6) or y in (fy0, fy0 + 3)
                if edge and not (y == fy0 and x == fx0 + 3):
                    g[y][x] = (ground, NONE, 'fence' if kind != 'occupied' or rng.random() < 0.5 else NONE, NONE)
                elif not edge:
                    if kind == 'living':
                        g[y][x] = ('field', 'wheat', NONE, NONE)
                    else:
                        g[y][x] = (ground, 'tall_grass' if ground == 'grass' else 'dry_grass' if ground == 'sand' else NONE, NONE, NONE)
    # obozowisko potworow albo zdziczenie
    free = []
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            c = g[y][x]
            if walk(c) and c[2] == NONE:
                free.append((x, y))
    rng.shuffle(free)
    if kind == 'occupied':
        k = 0
        for (x, y) in free[:10]:
            if abs(x - cx) + abs(y - cy) < 3:
                continue
            g[y][x] = (g[y][x][0], 'bones' if rng.random() < 0.3 else NONE, CAMP_PROPS[k % len(CAMP_PROPS)], NONE)
            k += 1
    elif kind == 'deserted':
        for (x, y) in free[:8]:
            g[y][x] = (g[y][x][0], 'tall_grass' if ground == 'grass' else NONE, rng.choice([tree, 'bush', 'rubble', NONE]), NONE)
    else:
        for (x, y) in free[:4]:
            if abs(x - cx) + abs(y - cy) > 4:
                g[y][x] = (g[y][x][0], NONE, rng.choice(['hay_bale', 'barrel', 'firewood', 'crate']), NONE)
    # drzewa dookola polany
    for y in range(y0 - 2, y0 + h + 2):
        for x in range(x0 - 2, x0 + w + 2):
            if (x0 <= x < x0 + w and y0 <= y < y0 + h) or not (0 <= y < len(g) and 0 <= x < len(g)):
                continue
            c = g[y][x]
            if c[0] == ground and c[2] == NONE and c[3] == NONE and rng.random() < 0.12:
                g[y][x] = (ground, NONE, tree, NONE)
    free = [(x, y) for (x, y) in free if walk(g[y][x]) and g[y][x][2] == NONE]
    return {'rect': (x0, y0, x0 + w - 1, y0 + h - 1), 'centre': (cx, cy + 1), 'free': free, 'houses': houses,
            'biome': biome}


# ------------------------------------------------------------------ jaskinie i lochy (pietra ujemne)

CAVE_STYLES = {
    # styl: (podloga, mur, ozdoby nieblokujace, ozdoby blokujace, swiatlo)
    'rock': ('cave_floor', 'cave_wall', ['glow_moss'], ['rock', 'rubble'], 'wall_torch'),
    'moss': ('cave_floor', 'mossy_wall', ['glow_moss'], ['rock', 'crystal'], 'glow_moss'),
    'ice': ('frozen_floor', 'ice_wall', [], ['ice_crystal', 'snow_rock'], 'wall_torch'),
    'fire': ('ash', 'obsidian_wall', [], ['magma', 'obsidian_rock'], 'magma'),
    'core': ('abyss_floor', 'obsidian_wall', [], ['magma', 'obsidian_rock'], 'magma'),
    'orc': ('dirt', 'cave_wall', [], ['barrel', 'crate', 'orc_tent', 'rubble'], 'wall_torch'),
    'mine': ('dirt', 'cave_wall', [], ['lumber', 'crate', 'rubble', 'barrel', 'rock'], 'wall_torch'),
}


def carve_cave(w, h, points, style, rng, room=(3, 5), tunnel=1, lava=0.0):
    """Jaskinia w lokalnym prostokacie w x h: komory w punktach, kretye korytarze miedzy nimi, mury dookola.
    Zwraca siatke krotek (None poza jaskinia)."""
    floor, wall, deco_free, deco_block, light = CAVE_STYLES[style]
    carved = set()

    def blob(cx, cy, r):
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + rng.random() * r and 1 <= x < w - 1 and 1 <= y < h - 1:
                    carved.add((x, y))
    for (px, py) in points:
        blob(px, py, rng.randint(*room))
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        x, y = ax, ay
        while (x, y) != (bx, by):
            if rng.random() < 0.6:
                x += (bx > x) - (bx < x)
            else:
                y += (by > y) - (by < y)
            if rng.random() < 0.15:
                x += rng.choice((-1, 1))
                x = max(2, min(w - 3, x))
            for dy in range(0, tunnel + 1):
                for dx in range(0, tunnel + 1):
                    if 1 <= x + dx < w - 1 and 1 <= y + dy < h - 1:
                        carved.add((x + dx, y + dy))
    grid = [[None] * w for _ in range(h)]
    for (x, y) in carved:
        grid[y][x] = (floor, NONE, NONE, NONE)
    for (x, y) in carved:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and grid[ny][nx] is None:
                    grid[ny][nx] = (floor, NONE, NONE, wall)
    # ozdoby: blokujace tylko tam, gdzie 8 sasiadow jest wolnych (nie odcinaja przejsc); swiatla przy murach
    cells = sorted(carved)
    rng.shuffle(cells)
    taken = set(points)
    for (x, y) in cells[:len(cells) // 9]:
        if any(abs(x - px) + abs(y - py) < 3 for (px, py) in points):
            continue
        near_wall = any(grid[y + dy][x + dx] is not None and grid[y + dy][x + dx][3] != NONE
                        for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        open8 = all(grid[y + dy][x + dx] is not None and grid[y + dy][x + dx][3] == NONE and grid[y + dy][x + dx][2] == NONE
                    for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (dx, dy) != (0, 0))
        if near_wall and rng.random() < 0.35 and (tk.TILES[light][1] or open8):
            grid[y][x] = (floor, NONE, light, NONE)
        elif deco_free and rng.random() < 0.3:
            grid[y][x] = (floor, NONE, rng.choice(deco_free), NONE)
        elif all(grid[y + dy][x + dx] is not None and grid[y + dy][x + dx][3] == NONE and grid[y + dy][x + dx][2] == NONE
                 for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (dx, dy) != (0, 0)):
            grid[y][x] = (floor, NONE, rng.choice(deco_block), NONE)
        taken.add((x, y))
    if lava > 0:
        # jeziorka lawy w wiekszych komorach (nieprzechodnie), z dala od punktow
        for (x, y) in cells:
            if rng.random() < lava and all(grid[y + dy][x + dx] is not None and grid[y + dy][x + dx][3] == NONE
                                           for dx in (-1, 0, 1) for dy in (-1, 0, 1)) \
                    and all(abs(x - px) + abs(y - py) > 4 for (px, py) in points):
                grid[y][x] = ('lava', NONE, NONE, NONE)
    return grid


def reachable_local(grid, start):
    h, w = len(grid), len(grid[0])
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in DIRS4:
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in seen and walk(grid[ny][nx]):
                seen.add((nx, ny))
                q.append((nx, ny))
    return seen


def fix_lava(grid, points):
    """Lawa i blokujace ozdoby nie moga odciac punktow (schodow) od siebie - w razie potrzeby znikaja."""
    floor = None
    for row in grid:
        for c in row:
            if c is not None and c[3] == NONE and c[0] != 'lava':
                floor = c[0]
                break
        if floor:
            break
    seen = reachable_local(grid, points[0])
    if all(p in seen for p in points):
        return
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c is not None and c[3] == NONE and (c[0] == 'lava' or not walk(c)):
                row[x] = (floor, NONE, NONE, NONE)
    seen = reachable_local(grid, points[0])
    assert all(p in seen for p in points), 'cave points not connected'
