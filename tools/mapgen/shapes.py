"""Regula wlasciciela (2026-11-12): "nic regularnego, kwadratowego ani prostokatnego" - budynki, sale i lochy maja
nieregularny obrys na wszystkich pietrach (-7..+7).

Przebieg po zlozeniu calego swiata (tuz przed zapisem): ``reshape`` szuka na kazdym pietrze zamknietych pokoi w ksztalcie
idealnego prostokata wiekszych niz 4x4 (wnetrze w calosci podloga bez muru, otoczone murem z co najwyzej trzema
pojedynczymi przerwami na drzwi - ta sama definicja co ``NoRectangularRoomsTest``) i zmienia ich obrys:
  * schodkowe sciecie dwoch przeciwleglych rogow (skosne, schodkowe sciany; w duzych salach wyrwany blok narozny - ksztalt L),
  * uskoki (wglebienia) w prostych odcinkach scian, co kilka pol,
  * mur wokol scietych rogow znika (pole wraca do terenu na powierzchni albo do pustki pod ziemia i na pietrach), wiec
    zmienia sie takze obrys na zewnatrz (dach nad nowym ksztaltem liczy klient z murow).
Nie rusza pol, do ktorych odwoluje sie mapa: obiekty (meble, schody, pochodnie), przejscia, NPC, spawny, skrzynie, drzwi i
bramy (z sasiedztwem), domy na sprzedaz (drzwi i wnetrze z obiektami), wspolrzedne zadan z oltarzami i bossow. Wnetrze zostaje
spojne (BFS). Deterministyczne: wybor ksztaltu z hasha polozenia pokoju.
"""
import re
from collections import deque

import numpy as np

NONE = '-'
DECOR = {'barrel', 'crate', 'table', 'chair', 'bench', 'potted_plant', 'bucket', 'sack', 'clay_pot', 'firewood', 'candle',
         'bookcase', 'wardrobe', 'hay_bale', 'rug', 'cobweb', 'bones', 'rubble', 'debris', 'statue', 'banner', 'fishing_net', 'campfire'}
MIN = 5            # wnetrze co najmniej 5x5 (wieksze niz 4x4) jest "prostokatnym pokojem"
MAX = 60
NATURAL_OUTSIDE = {'grass', 'snow', 'sand', 'jungle_grass', 'dark_sand', 'blighted_grass', 'ash', 'dirt', 'field'}


def _arrays(g):
    h, w = len(g), len(g[0])
    inner = np.zeros((h, w), bool)
    wall = np.zeros((h, w), bool)
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c is None:
                continue
            if c[3] != NONE:
                wall[y, x] = True
            elif c[0] != NONE:
                inner[y, x] = True
    return inner, wall


def find_rects(g):
    """Zamkniete prostokatne pokoje [(x, y, w, h)] (wnetrze) na jednym pietrze."""
    inner, wall = _arrays(g)
    h, w = inner.shape
    PI = np.zeros((h + 1, w + 1), np.int32)
    PI[1:, 1:] = inner.cumsum(0).cumsum(1)
    nw = ~wall
    PW = np.zeros((h + 1, w + 1), np.int32)
    PW[1:, 1:] = nw.cumsum(0).cumsum(1)

    def rect(P, x0, y0, x1, y1):               # sumy dla x0..x1, y0..y1 (wlacznie)
        return int(P[y1 + 1, x1 + 1] - P[y0, x1 + 1] - P[y1 + 1, x0] + P[y0, x0])

    def gaps_ok(x0, y0, x1, y1):               # przerwy: pojedyncze pola, w sumie najwyzej trzy
        n = rect(PW, x0, y0, x1, y1)
        if n == 0:
            return True
        if n > 3:
            return False
        run = 0
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if nw[y, x]:
                    run += 1
                    if run > 1:
                        return False
                else:
                    run = 0
        return True
    out = []
    cand = np.argwhere(inner[1:-1, 1:-1] & wall[1:-1, :-2] & wall[:-2, 1:-1]) + 1
    for (y, x) in cand:
        y, x = int(y), int(x)
        maxw = 0
        while x + maxw < w - 1 and inner[y, x + maxw] and maxw < MAX:
            maxw += 1
        maxh = 0
        while y + maxh < h - 1 and inner[y + maxh, x] and maxh < MAX:
            maxh += 1
        if maxw < MIN or maxh < MIN:
            continue
        for rw in range(MIN, maxw + 1):
            if rect(PW, x, y - 1, x + rw - 1, y - 1) > 3:
                break
            for rh in range(MIN, maxh + 1):
                if rect(PW, x - 1, y, x - 1, y + rh - 1) > 3:
                    break
                if rect(PI, x, y, x + rw - 1, y + rh - 1) != rw * rh:
                    break
                if (gaps_ok(x, y - 1, x + rw - 1, y - 1) and gaps_ok(x, y + rh, x + rw - 1, y + rh)
                        and gaps_ok(x - 1, y, x - 1, y + rh - 1) and gaps_ok(x + rw, y, x + rw, y + rh - 1)):
                    out.append((x, y, rw, rh))
    return out


# ---------------------------------------------------------------------------------------------------- chronione pola
def protected_cells(ctx):
    """(x, y, z) pol, do ktorych odwoluje sie mapa; przy nich nie ciac."""
    P = set()

    def add(x, y, z):
        P.add((int(x), int(y), int(z)))
    for t in ctx['transitions']:
        add(t[0], t[1], t[2])
        add(t[4], t[5], t[6])
    for n in ctx['npcs']:
        add(n[4], n[5], n[6])
    for ln in ctx['spawns']:
        if ln.startswith('#') or not ln.strip():
            continue
        f = ln.split(';')
        add(f[1], f[2], f[3])
    for c in ctx.get('loot_chests', []):
        add(c[0], c[1], c[2])
    for c in ctx.get('star_choices', []):
        add(c[1], c[2], c[3])
    for c in ctx.get('star_gates', []):
        add(c[0], c[1], c[2])
    for c in ctx.get('level_doors', []):
        add(c[0], c[1], c[2])
    for r in ctx['gates'] if isinstance(ctx.get('gates'), list) else []:
        pass
    for r in ctx['houses']:
        if len(r) > 9:
            try:
                add(r[8], r[9], r[7])
            except (ValueError, TypeError):
                pass
    for d in ctx.get('displays', []):
        add(d[0], d[1], d[2])
    for key in ('altar_quests', 'boss_quests'):
        for ln in ctx.get(key, []):
            for m in re.finditer(r'(-?\d+),(-?\d+),(-?\d+)', ln):
                add(*m.groups())
    return P


def _components_ok(cells):
    cells = set(cells)
    if not cells:
        return False
    start = next(iter(cells))
    seen = {start}
    dq = deque([start])
    while dq:
        x, y = dq.popleft()
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + d[0], y + d[1])
            if n in cells and n not in seen:
                seen.add(n)
                dq.append(n)
    return len(seen) == len(cells)


def _hash(x, y, z, w, h):
    return ((x * 73856093) ^ (y * 19349663) ^ ((z + 9) * 83492791) ^ (w * 2654435761) ^ (h * 40503)) & 0xFFFFFFFF


def _plan_cuts(g, z, rect, prot):
    x0, y0, rw, rh = rect
    x1, y1 = x0 + rw - 1, y0 + rh - 1
    hs = _hash(x0, y0, z, rw, rh)
    cells = {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}
    # chronione wnetrze: obiekty, odwolania mapy i sasiedztwo przerw w murze (drzwi)
    keep = set()
    for (x, y) in cells:
        c = g[y][x]
        if (c[2] != NONE and c[2] not in DECOR) or (x, y, z) in prot:
            keep.add((x, y))
    gaps = [(x, y) for x in range(x0, x1 + 1) for y in (y0 - 1, y1 + 1) if g[y][x][3] == NONE] \
        + [(x, y) for y in range(y0, y1 + 1) for x in (x0 - 1, x1 + 1) if g[y][x][3] == NONE]
    for (gx, gy) in gaps:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                keep.add((gx + dx, gy + dy))
    lit = [(x, y) for x in range(x0 - 1, x1 + 2) for y in (y0 - 1, y1 + 1) if g[y][x] is not None and g[y][x][3] != NONE and g[y][x][2] != NONE] \
        + [(x, y) for y in range(y0, y1 + 1) for x in (x0 - 1, x1 + 1) if g[y][x] is not None and g[y][x][3] != NONE and g[y][x][2] != NONE]
    for (lx, ly) in lit:                       # pochodnie wbudowane w mur: pole przed nia (blask) zostaje
        for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            keep.add((lx + dx, ly + dy))
    corners = [(x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)]
    order = sorted(range(4), key=lambda i: (hs >> (3 * i)) & 7)
    cut = set()
    kmax = 2 if min(rw, rh) < 7 else 3 if min(rw, rh) < 9 else 4
    big = rw >= 9 and rh >= 7

    def corner_cells(i, k, block):
        cx, cy, dx, dy = corners[i]
        if block:
            a, b = max(2, rw // 3), max(2, rh // 3)
            return {(cx + dx * u, cy + dy * v) for u in range(a) for v in range(b)}
        return {(cx + dx * u, cy + dy * v) for u in range(k) for v in range(k) if u + v < k}

    done = 0
    for rank, i in enumerate(order[:3]):
        opts = []
        if big and rank == 0:
            opts.append(corner_cells(i, 0, True))
        for k in range(kmax, 0, -1):
            opts.append(corner_cells(i, k, False))
        for cs in opts:
            if cs & keep or cs & cut:
                continue
            if _components_ok(cells - cut - cs):
                cut |= cs
                done += 1
                break
    # uskok w prostym odcinku sciany (dluzszym niz 6): dwa pola, glebokosc 1
    sides = []
    if rw >= 7:
        sides += [('N', y0, 1), ('S', y1, -1)]
    if rh >= 7:
        sides += [('W', x0, 1), ('E', x1, -1)]
    if sides:
        side, line, d = sides[(hs >> 11) % len(sides)]
        n = 2 + ((hs >> 15) & 1)
        if side in 'NS':
            lo = x0 + 2
            hi = x1 - 2 - n
            if hi >= lo:
                sx = lo + (hs >> 17) % (hi - lo + 1)
                cs = {(sx + i, line) for i in range(n)}
                if not (cs & keep or cs & cut) and _components_ok(cells - cut - cs):
                    cut |= cs
                    done += 1
        else:
            lo = y0 + 2
            hi = y1 - 2 - n
            if hi >= lo:
                sy = lo + (hs >> 17) % (hi - lo + 1)
                cs = {(line, sy + i) for i in range(n)}
                if not (cs & keep or cs & cut) and _components_ok(cells - cut - cs):
                    cut |= cs
                    done += 1
    return cut, cells


def _outside_cell(g, z, rect, c):
    x0, y0, rw, rh = rect
    x1, y1 = x0 + rw - 1, y0 + rh - 1
    x, y = c
    d = {'W': x - (x0 - 1), 'E': (x1 + 1) - x, 'N': y - (y0 - 1), 'S': (y1 + 1) - y}
    side = min(d, key=d.get)
    if side == 'W':
        return (x0 - 2, y)
    if side == 'E':
        return (x1 + 2, y)
    if side == 'N':
        return (x, y0 - 2)
    return (x, y1 + 2)


def _apply(g, z, rect, cut, cells, prot):
    x0, y0, rw, rh = rect
    x1, y1 = x0 + rw - 1, y0 + rh - 1
    h, w = len(g), len(g[0])
    inner_new = cells - cut
    ring_old = {(x, y) for y in range(y0 - 1, y1 + 2) for x in range(x0 - 1, x1 + 2) if (x, y) not in cells}
    ring_new = {(x + dx, y + dy) for (x, y) in inner_new for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - inner_new
    wall_tile = None
    for (x, y) in ring_old:
        c = g[y][x]
        if c is not None and c[3] != NONE:
            wall_tile = c[3]
            break
    if wall_tile is None:
        return False
    for c in cut:                                    # dawne wnetrze: albo nowy mur, albo poza budynkiem
        x, y = c
        old = g[y][x]
        if c in ring_new:
            g[y][x] = (old[0], NONE, NONE, wall_tile)
    for c in list(cut) + list(ring_old):
        if c in ring_new or c in inner_new:
            continue
        x, y = c
        old = g[y][x]
        if old is None:
            continue
        if c in ring_old and (old[2] != NONE or old[3] == NONE or (x, y, z) in prot):
            continue                                 # przerwa w murze / obiekt na murze zostaje
        ox, oy = _outside_cell(g, z, rect, c)
        if z == 0 and 0 <= ox < w and 0 <= oy < h and g[oy][ox] is not None and g[oy][ox][0] in NATURAL_OUTSIDE:
            g[y][x] = (g[oy][ox][0], NONE, NONE, NONE)
        elif z == 0:
            g[y][x] = ('grass', NONE, NONE, NONE)
        else:
            g[y][x] = None
    return True


def reshape(ctx, floors, vrect=None, rounds=4):
    """Przekszalca wszystkie prostokatne pokoje; zwraca (zmienione, pozostale [(z, x, y, w, h)])."""
    world = ctx['world']
    prot = protected_cells(ctx)
    core = set(ctx.get('varden_core', ()))     # kopia mapy Varden 1:1 (test GameWorldTest) - bez zmian
    changed = 0
    left = []
    for rnd in range(rounds):
        left = []
        for z in floors:
            g = world.floors.get(z)
            if g is None:
                continue
            for rect in find_rects(g):
                if z != 0 and vrect and vrect[0] <= rect[0] <= vrect[2] and vrect[1] <= rect[1] <= vrect[3]:
                    continue                         # pietra kopii maps/varden (GameWorldTest) - bez zmian
                if core and any((x, y) in core for x in (rect[0], rect[0] + rect[2] - 1) for y in (rect[1], rect[1] + rect[3] - 1)):
                    continue
                x0, y0, rw, rh = rect
                if any(g[y][x] is None or g[y][x][3] != NONE or g[y][x][0] == NONE
                       for y in range(y0, y0 + rh) for x in range(x0, x0 + rw)):
                    continue                         # pokoj zmieniony wczesniejsza poprawka (zajmie go kolejna runda)
                cut, cells = _plan_cuts(g, z, rect, prot)
                if cut and _apply(g, z, rect, cut, cells, prot):
                    changed += 1
                else:
                    left.append((z, *rect))
        if not left:
            break
    return changed, left
