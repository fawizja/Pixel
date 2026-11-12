"""Obozy potworow w dziczy (Darek 2026-11-12): "obozy orkow i lowiska nie maja byc kwadratowe i jedne obok drugich,
daj je daleko od siebie".

Dawniej kazdy oboz (wioska ``occupied`` w generate_continent.VILLAGES) to prostokat 24 x 18 z czterema domami wokol
placu, a Kamienny Brod, Wilczy Jar i wejscie do Orczej Kopalni stalo w odleglosci 15-35 pol. Teraz:
  * oboz to nieregularna plama (elipsa z falujacym brzegiem i czasem dodatkowym wybrzuszeniem), obrzeze to palisada z
    belek, kamienny pierscien albo ruiny muru (wg rodzaju obozu), z 2-3 bramami; w srodku ognisko, namioty, male chaty i
    skrzynie rozrzucone w nieladzie, spawny co najmniej 8 pol od siebie;
  * miejsca wybierane tak, by srodki obozow byly od siebie co najmniej ``MIN_ANY`` pol, obozy tego samego rodzaju
    (orkowie, gobliny, rabusie...) ``MIN_FAMILY`` pol, a od znanych wejsc do lochow tego rodzaju ``MIN_FIXED`` pol;
  * obozy omijaja drogi, zabudowe, wode, gory, zamek (Warownia Gorvalda), trzy krainy wzgorz, twierdze i obozy wysp.
Sprawdza CampsTest (odstepy i nieprostokatnosc obrysu).
"""
import math
import random
from collections import deque

import numpy as np

import features as F

NONE = '-'
MIN_ANY = 50
MIN_FAMILY = 70
MIN_FIXED = 55
SEARCH = 180

# miejsca, ktorych obozy nie moga zajac (zamek, wzgorza, twierdze, obozy wysp) - prostokaty z zapasem
HOLD = [(738, 324, 825, 401),           # Warownia Gorvalda
        (550, 385, 655, 510),           # Wzgorza Goblinow (z zapasem na margines blokady hills.py)
        (58, 226, 177, 352),            # Wzgorza Smokow
        (635, 465, 735, 555),           # Twierdza Krwawego Kla (strongholds.RESERVED)
        (335, 490, 435, 580),           # Elandor
        (709, 568, 805, 656),           # Oboz Lowczyn (isles.CAMP_RESERVED)
        (535, 855, 620, 960)]           # Cyklopolis
# znane wejscia do lochow tego samego rodzaju (stare Orcza Kopalnia itp.): obozy trzymaja od nich odstep
FIXED = {'orc': [(594, 289), (684, 533)], 'goblin': [(602, 447)], 'troll': [(470, 330)]}

# nazwa obozu -> rodzaj obrysu i rodzina potworow
SPECS = {
    'Kamienny Bród': dict(style='palisade', family='orc'),
    'Wilczy Jar': dict(style='palisade', family='orc'),
    'Czerwony Kurhan': dict(style='palisade', family='orc'),
    'Zielony Gaj': dict(style='palisade', family='goblin'),
    'Kozi Przysiółek': dict(style='palisade', family='goblin'),
    'Rozstaje': dict(style='palisade', family='bandit'),
    'Gniazdo Rabusiów': dict(style='palisade', family='bandit'),
    'Martwa Wieś': dict(style='ruins', family='undead'),
    'Trollowe Leże': dict(style='rocks', family='troll'),
    'Bagienna Osada': dict(style='palisade', family='lizard'),
    'Lodowy Kurhan': dict(style='rocks', family='yeti'),
    'Piaskowy Obóz': dict(style='rocks', family='hyena'),
}
TREES = {'tree', 'pine', 'palm', 'bush', 'jungle_tree', 'jungle_bush', 'frost_pine', 'frost_bush', 'dry_bush', 'autumn_tree',
         'dead_tree', 'burnt_tree', 'cactus', 'rock', 'snow_rock', 'sandstone_rock', 'obsidian_rock', 'ice_crystal'}


def blob(rng, rx, ry):
    """Nieregularna plama: elipsa z falujacym brzegiem (dwa-piec fal) i 1-2 wybrzuszeniami; najwieksza skladowa, bez dziur."""
    rot = rng.uniform(0, math.pi)
    ph = [rng.uniform(0, 6.28) for _ in range(3)]
    amp = (rng.uniform(0.10, 0.2), rng.uniform(0.08, 0.16), rng.uniform(0.04, 0.09))
    lobes = [(rng.uniform(0, 6.28), rng.uniform(0.75, 0.95) * max(rx, ry), rng.uniform(0.38, 0.55) * min(rx, ry))
             for _ in range(rng.choice((1, 2)))]
    R = int(max(rx, ry) * 1.7) + 2
    cs, sn = math.cos(rot), math.sin(rot)
    cells = set()
    for dy in range(-R, R + 1):
        for dx in range(-R, R + 1):
            x, y = dx * cs + dy * sn, -dx * sn + dy * cs
            a = math.atan2(y, x)
            t = 1 + amp[0] * math.sin(2 * a + ph[0]) + amp[1] * math.sin(3 * a + ph[1]) + amp[2] * math.sin(5 * a + ph[2])
            ok = math.hypot(x / rx, y / ry) < t
            if not ok:
                for (la, ld, lr) in lobes:
                    ex, ey = dx - math.cos(la) * ld, dy - math.sin(la) * ld
                    if math.hypot(ex, ey) < lr + 1.1 * math.sin(3 * math.atan2(ey, ex) + la):
                        ok = True
            if ok:
                cells.add((dx, dy))
    # najwieksza skladowa
    seen, best = set(), []
    for c in sorted(cells):
        if c in seen:
            continue
        comp, q = [c], deque([c])
        seen.add(c)
        while q:
            x, y = q.popleft()
            for dx, dy in F.DIRS4:
                n = (x + dx, y + dy)
                if n in cells and n not in seen:
                    seen.add(n)
                    comp.append(n)
                    q.append(n)
        if len(comp) > len(best):
            best = comp
    cells = set(best)
    # dziury zasypane (pole poza plama, do ktorego nie da sie dojsc z brzegu prostokata)
    lo, hi = -R - 1, R + 1
    out, q = {(lo, lo)}, deque([(lo, lo)])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if lo <= n[0] <= hi and lo <= n[1] <= hi and n not in cells and n not in out:
                out.add(n)
                q.append(n)
    return {(x, y) for x in range(lo, hi + 1) for y in range(lo, hi + 1) if (x, y) not in out}


def ring_of(cells):
    return {(x, y) for (x, y) in cells if any((x + dx, y + dy) not in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1))}


def dilate(cells, r):
    return {(x + dx, y + dy) for (x, y) in cells for dx in range(-r, r + 1) for dy in range(-r, r + 1)}


def find_spot(world, T, footprint, near, cells, family, placed, extra_avoid=(), land=None):
    """Srodek plamy najblizej near: bez wody, drog, zabudowy, gor i miejsc HOLD; z odstepami MIN_*."""
    g = world.floors[0]
    S = len(g)
    big = dilate(cells, 2)
    inner = dilate(cells, 1)
    xs = [c[0] for c in big]
    ys = [c[1] for c in big]
    ext = max(max(abs(v) for v in xs), max(abs(v) for v in ys))
    cx0, cy0 = near
    fixed = FIXED.get(family, [])
    why = {}
    for r in range(0, SEARCH + 1, 3):
        best = None
        pts = [(dx, dy) for dy in range(-r, r + 1, 3) for dx in range(-r, r + 1, 3) if max(abs(dx), abs(dy)) == r]
        for (dx, dy) in pts:
            cx, cy = cx0 + dx, cy0 + dy
            if cx < ext + 8 or cy < ext + 8 or cx > S - ext - 8 or cy > S - ext - 8:
                continue
            if any(a - ext <= cx <= c + ext and b - ext <= cy <= d + ext for (a, b, c, d) in HOLD):
                why['hold'] = why.get('hold', 0) + 1
                continue
            if land is not None and not land[cy, cx]:
                why['land'] = why.get('land', 0) + 1
                continue
            if any(math.hypot(cx - px, cy - py) < (MIN_FAMILY if fam == family else MIN_ANY) for (px, py, fam) in placed):
                why['spacing'] = why.get('spacing', 0) + 1
                continue
            if any(math.hypot(cx - fx, cy - fy) < MIN_FIXED for (fx, fy) in fixed):
                why['fixed'] = why.get('fixed', 0) + 1
                continue
            if any(math.hypot(cx - fx, cy - fy) < 30 for (fx, fy) in extra_avoid):
                continue
            bad = False
            for (ox, oy) in big:
                x, y = cx + ox, cy + oy
                c = g[y][x]
                if footprint[y, x] or T.water[y, x] or c[0] in ('water', 'lava', 'swamp', 'bridge', 'ice', 'deep_water') \
                        or c[2] in F.STAIRS or c[0] in F.ROAD_GROUND:
                    bad = True
                    break
                if (ox, oy) in inner and c[3] != NONE:
                    bad = True
                    break
            if bad:
                why['terrain'] = why.get('terrain', 0) + 1
                continue
            score = math.hypot(dx, dy)
            if best is None or score < best[0]:
                best = (score, cx, cy)
        if best is not None:
            return best[1], best[2]
    print('  camp: no site', near, why)
    return None


def build(world, T, footprint, near, name, rng, placed, cost, extra_avoid=(), land=None):
    """Buduje oboz name w poblizu near (patrz moduł). Zwraca slownik zgodny z features.village (rect, centre, free, houses,
    biome) oraz: cells, ring, gates (pola przed bramami), points (miejsca spawnow), style."""
    spec = SPECS[name]
    style, family = spec['style'], spec['family']
    g = world.floors[0]
    rx, ry = rng.uniform(10.5, 13.0), rng.uniform(8.0, 10.5)
    cells = blob(rng, rx, ry)
    spot = find_spot(world, T, footprint, near, cells, family, placed, extra_avoid, land)
    assert spot is not None, f'camp {name}: no site near {near}'
    cx, cy = spot
    biome = T.biome[cy, cx]
    ground, wall, floor, tree = F.BIOME_STYLE.get(biome, F.BIOME_STYLE['temperate'])
    abs_cells = {(cx + x, cy + y) for (x, y) in cells}
    ring = ring_of(abs_cells)
    inner = abs_cells - ring
    # ---- teren: polana (drzewa w pasie 2 pola dookola znikaja), ubita ziemia wokol ogniska i na srodku
    for (x, y) in dilate(abs_cells, 2):
        c = g[y][x]
        if c[2] in TREES and (x, y) not in ring:
            g[y][x] = (c[0], c[1], NONE, c[3])
    for (x, y) in abs_cells:
        d = math.hypot(x - cx, y - cy)
        dirt = d < 6.5 or rng.random() < 0.25
        deco = NONE
        r = rng.random()
        if r < 0.05:
            deco = 'bones'
        elif r < 0.07:
            deco = 'blood'
        elif r < 0.09:
            deco = 'debris'
        g[y][x] = ('dirt' if dirt else ground, deco if (dirt or ground != 'sand') else NONE, NONE, NONE)
    # ---- bramy: 2-3 przerwy w obrzezu, tam gdzie za nimi jest wolny teren
    n_gates = rng.choice((2, 3))
    base = rng.uniform(0, 2 * math.pi)
    gates, gate_out = [], []
    cand = sorted(ring)

    def ang_gap(q, a):
        d = abs(math.atan2(q[1] - cy, q[0] - cx) - a) % (2 * math.pi)
        return min(d, 2 * math.pi - d)
    order = [base + i * 2 * math.pi / n_gates + rng.uniform(-0.3, 0.3) for i in range(n_gates)] + [base + 0.5 + i * 0.9 for i in range(7)]
    for a in order:
        if len(gates) >= n_gates:
            break
        anchor = min(cand, key=lambda q: ang_gap(q, a))
        d = math.hypot(anchor[0] - cx, anchor[1] - cy)
        ux, uy = (anchor[0] - cx) / d, (anchor[1] - cy) / d
        out_pt = (int(round(anchor[0] + ux * 3)), int(round(anchor[1] + uy * 3)))
        oc = g[out_pt[1]][out_pt[0]]
        if oc[0] in ('water', 'lava', 'swamp', 'deep_water') or oc[3] != NONE \
                or any(math.hypot(anchor[0] - q[0], anchor[1] - q[1]) < 8 for q in gates):
            continue
        gates.append(anchor)
        gate_out.append(out_pt)
    assert len(gates) >= 2, f'camp {name}: no gates'
    gap = {q for q in ring if any(math.hypot(q[0] - a[0], q[1] - a[1]) <= 2.3 for a in gates)}
    for (ox, oy) in gate_out:
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                c = g[oy + dy][ox + dx]
                if c[2] in TREES and (ox + dx, oy + dy) not in ring:
                    g[oy + dy][ox + dx] = (c[0], c[1], NONE, NONE)
    rock = 'sandstone_rock' if ground in ('sand', 'dark_sand') else ('snow_rock' if ground == 'snow' else 'rock')
    for (x, y) in ring:
        if (x, y) in gap:
            g[y][x] = ('dirt', NONE, NONE, NONE)
        elif style == 'palisade':
            g[y][x] = ('dirt', NONE, NONE, 'log_wall' if biome in ('snow', 'taiga', 'forest') or rng.random() < 0.55 else 'wood_wall')
        elif style == 'rocks':
            g[y][x] = (g[y][x][0], NONE, rock if rng.random() < 0.9 else NONE, NONE)
        else:
            g[y][x] = ('dirt', NONE, rng.choice(('broken_wall',) * 6 + ('rubble', 'rubble', NONE, NONE)), NONE)
    # sztandary po bokach bram, rozrzucone glazy i belki przed obrzezem
    for (ax, ay) in gates:
        d = math.hypot(ax - cx, ay - cy)
        ux, uy = (ax - cx) / d, (ay - cy) / d
        for s in (-1, 1):
            bx, by = int(round(ax + ux * 1.5 - uy * 3 * s)), int(round(ay + uy * 1.5 + ux * 3 * s))
            c = g[by][bx]
            if F.walk(c) and c[2] == NONE and (bx, by) not in abs_cells and style != 'ruins':
                g[by][bx] = (c[0], c[1], 'banner', NONE)
    outside = [q for q in dilate(abs_cells, 3) if q not in abs_cells]
    rng.shuffle(outside)
    scatter = {'palisade': ['rock', 'firewood', 'rock'], 'rocks': [rock, rock, rock], 'ruins': ['burnt_beam', 'rubble', 'burnt_tree']}[style]
    for (x, y) in outside[:14]:
        c = g[y][x]
        if F.walk(c) and c[2] == NONE and c[0] not in F.ROAD_GROUND and not footprint[y, x]:
            g[y][x] = (c[0], c[1], rng.choice(scatter), NONE)
    # ---- ognisko, chaty, namioty, skrzynie (tylko w srodku, bez zamykania przejsc)
    centre = (cx, cy)
    g[cy][cx] = ('dirt', NONE, 'campfire', NONE)
    houses = []
    deep = {q for q in inner if all((q[0] + dx, q[1] + dy) in inner for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2))}
    n_huts = rng.choice((1, 2)) if style != 'rocks' else 0
    huts = []
    for _ in range(60):
        if len(huts) >= n_huts:
            break
        w, h = rng.choice(((5, 4), (4, 4), (5, 5)))
        hx, hy = cx + rng.randint(-9, 9) - w // 2, cy + rng.randint(-7, 7) - h // 2
        rect = {(x, y) for x in range(hx - 1, hx + w + 1) for y in range(hy - 1, hy + h + 1)}
        if not rect <= deep or math.hypot(hx + w / 2 - cx, hy + h / 2 - cy) < 5 or any(rect & r for r in huts):
            continue
        side = 'S' if hy + h / 2 < cy else 'N'
        if abs(hx + w / 2 - cx) > abs(hy + h / 2 - cy) * 1.4:
            side = 'S' if rng.random() < 0.5 else 'N'
        if style == 'ruins':
            res = F.house(world, hx, hy, w, h, wall, floor, side, rng, furniture=False, ruined=0.55, burnt=True)
        else:
            res = F.house(world, hx, hy, w, h, wall, floor, side, rng, furniture=False, ruined=0.1)
        houses.append(res)
        huts.append(rect)
    taken_cells = set().union(*huts) if huts else set()
    props = {'palisade': ['orc_tent', 'orc_tent', 'orc_tent', 'crate', 'barrel', 'firewood', 'campfire', 'hay_bale'],
             'rocks': ['orc_tent', rock, 'firewood', 'orc_tent', 'hay_bale'],
             'ruins': ['rubble', 'burnt_beam', 'crate', 'barrel', 'broken_wall', 'dead_tree']}[style]
    spots = sorted(q for q in deep if q not in taken_cells and math.hypot(q[0] - cx, q[1] - cy) >= 3)
    rng.shuffle(spots)
    placed_props = []
    k = 0
    for q in spots:
        if k >= rng.randint(11, 15):
            break
        if any(abs(q[0] - a) + abs(q[1] - b) < 4 for a, b in placed_props):
            continue
        c = g[q[1]][q[0]]
        if not F.walk(c) or c[2] != NONE:
            continue
        g[q[1]][q[0]] = (c[0], 'bones' if rng.random() < 0.25 else c[1], props[k % len(props)], NONE)
        placed_props.append(q)
        k += 1
    # ---- wolne pola i miejsca spawnow (ksiezycowo: najdalsze od siebie, co najmniej 8 pol)
    free = [(x, y) for (x, y) in sorted(inner) if F.walk(g[y][x]) and g[y][x][2] == NONE
            and all(F.walk(g[y + b][x + a]) and g[y + b][x + a][2] == NONE for a in (-1, 0, 1) for b in (-1, 0, 1))
            and math.hypot(x - cx, y - cy) >= 3]
    rng.shuffle(free)
    points = []
    if free:
        points.append(min(free, key=lambda q: math.hypot(q[0] - cx, q[1] - cy)))
        while len(points) < 3:
            q = max(free, key=lambda q: min(math.hypot(q[0] - a, q[1] - b) for a, b in points))
            if min(math.hypot(q[0] - a, q[1] - b) for a, b in points) < 8:
                break
            points.append(q)
    xs = [q[0] for q in abs_cells]
    ys = [q[1] for q in abs_cells]
    rect = (min(xs), min(ys), max(xs), max(ys))
    out = dict(rect=rect, centre=(cx, cy + 1), free=free, houses=houses, biome=biome, cells=abs_cells, ring=ring - gap,
               gates=gate_out, points=points, style=style, family=family, name=name, centre_xy=centre)
    placed.append((cx, cy, family))
    return out
