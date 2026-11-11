"""Bursztynowa Wyspa powiekszona, oboz lowczyn i Cyklopolis (Darek 2026-11-11, etap 5 duzej listy).

"Powieksz jedna wyspe z silnymi potworkami i zrob -6 roznorodne pomieszczenia, bardzo duzo potworkow, i +3 bardzo
duzo silnych potworkow; zrob gdzies Amazon camp podobny jak w real Tibii i Cyclopolis gdzies na jakiejs wyspie, tez
na wzor Tibii, z duza iloscia srednich i slabych potworkow i duzo pieter minus."

* Wyspa Bursztynowa (najsilniejsze potwory wysp, tylko przewoznikiem - premium albo promocja): dosypany lad na
  poludnie (grow_island, przed drogami i przystaniami), w srodku nowego ladu Bursztynowa Skala - masyw z ruina wiezy
  u podnoza. Wieza prowadzi na szczyt masywu (+1 polki skalne, +2 ruiny wiezy, +3 szczyt), wneka w zboczu do
  Bursztynowych Czelusci (-1 grota morska, -2 zalane sale, -3 krypty, -4 krysztaly, -5 grobowce w piaskowcu,
  -6 otchlan). Kazde pietro: pien komor, slepe komory (skrzynie) i petla; w kazdej komorze stado silnych potworow.
* Oboz Lowczyn (jak oboz amazonek z Tibii): polana w dzungli za palisada, chaty, chata matrony z tronem, ognisko
  i totemy; pod chata piwnice (-1) i grota (-2) ze skrzynia-nagroda w najdalszej komorze. Poziomy ~20-50.
* Cyklopolis (jak miasto cyklopow z Tibii) na gorzystej wyspie z przewoznikiem: ruiny kamiennych budowli miedzy
  skalami, Wielka Kuznia i kuznia kowala z paleniskami i kowadlami, plac z posagiem; pod miastem -1..-6 (hale kuzni,
  kopalnie, groty) z jednookimi olbrzymami i slabsza fauna jaskin. Przewoznik plywa do portu "Cyklopolis".
Wszystko z wlasnym ziarnem; uklad sprawdza Plan.check (droga od wejsc do kazdego spawnu, schodow i skrzyni).
"""
import heapq
import math
import random

import numpy as np

import features as F
import structures as St

NONE = '-'
SEED = 20261114
F.CAVE_STYLES.setdefault('cliff_top', ('grass', 'mountain', ['tall_grass', 'flowers'], ['rock', 'broken_wall', 'bush'],
                                       'wall_torch'))
F.CAVE_STYLES.setdefault('sea_grotto', ('dark_sand', 'cave_wall', ['shells', 'reeds'], ['rock', 'rubble'], 'wall_torch'))
F.CAVE_STYLES.setdefault('oneeye_hall', ('stone_floor', 'stone_wall', ['debris', 'bones'], ['forge', 'anvil', 'barrel',
                                                                                          'rubble'], 'wall_torch'))
F.CAVE_STYLES.setdefault('grove_cellar', ('dirt', 'cave_wall', ['bones', 'debris'], ['barrel', 'crate', 'rubble'],
                                          'wall_torch'))
F.CAVE_STYLES.setdefault('deep_mine', ('cave_floor', 'cave_wall', ['glow_moss'], ['rock', 'rubble', 'crystal', 'lumber'],
                                       'glow_moss'))
F.CAVE_STYLES.setdefault('sandtomb', ('sandstone_floor', 'sandstone_wall', ['bones', 'cobweb', 'debris'],
                                      ['rubble', 'sand_statue'], 'wall_torch'))

# ====================================================================== Wyspa Bursztynowa
ISLE = 'Wyspa Bursztynowa'
ROCK = 'Bursztynowa Skała'
DEEP = 'Bursztynowe Czeluście'
ISLE_SEED = (790, 880)               # pole starej wyspy
GROW_C = (788, 936)                  # srodek dosypanego ladu
GROW_R = (44, 31)
GROW_GAP = 17                        # tyle wody zostaje do innych wysp (bez kladek i mostow)
ISLE_LEVELS = (280, 420)
DEEP_FLOORS = {
    # styl, potwory (od slabszych), liczba w stadzie, poziomy regionu
    -1: ('sea_grotto', ['coral_crab', 'drowned_sailor', 'pirate_cutthroat', 'crimson_shade', 'pirate_gunner'], (5, 6), (300, 450)),
    -2: ('flooded', ['drowned_sailor', 'pirate_gunner', 'wraith_knight', 'mist_wraith'], (5, 6), (330, 560)),
    -3: ('crypt', ['wraith_knight', 'crimson_shade', 'mist_wraith', 'shadow_stalker'], (5, 6), (400, 620)),
    -4: ('crystal', ['frost_giant', 'rime_gargoyle', 'ice_dragon', 'shadow_stalker'], (5, 6), (450, 680)),
    -5: ('sandtomb', ['sand_titan', 'dusk_gargoyle', 'shadow_centipede', 'void_priest'], (6, 6), (600, 800)),
    -6: ('core', ['abyss_crawler', 'abyssal_demon', 'bone_dragon', 'winged_spawn', 'obsidian_sentinel'], (6, 6), (750, 1000)),
}
UP_FLOORS = {
    1: ('cliff_top', ['wyvern', 'storm_harpy', 'dune_drake', 'storm_wisp', 'rime_gargoyle'], (5, 6), (300, 560)),
    2: ('ruin', ['rime_gargoyle', 'mist_wraith', 'dusk_gargoyle', 'shadow_stalker'], (5, 6), (500, 700)),
    3: ('cliff_top', ['dusk_gargoyle', 'magma_drake', 'bone_dragon', 'winged_spawn'], (6, 6), (650, 950)),
}
ISLE_SURFACE = ['wraith_knight', 'drowned_sailor', 'pirate_cutthroat', 'wyvern', 'coral_crab', 'crimson_shade',
                'pirate_gunner', 'wraith_knight', 'storm_harpy', 'mist_wraith']
ISLE_CHESTS = [('great_health_potion', 3), ('great_mana_potion', 3), ('ultimate_health_potion', 2),
               ('great_spirit_potion', 2), ('strong_health_potion', 4)]

# ====================================================================== oboz lowczyn
CAMP = 'Obóz Łowczyń'
CELLAR = 'Piwnice Obozu Łowczyń'
CAMP_NEAR = (757, 612)
PW, PH, PM = 40, 32, 5
CAMP_LEVELS = (18, 45)
CELLAR_LEVELS = (22, 55)
CAMP_DOOR_LEVEL = 20   # brama obozu: od poziomu 20 (lowczynie 18-40)
CAMP_REWARD = (2500, 'elven_bow', 1)
CAMP_RESERVED = (CAMP_NEAR[0] - 48, CAMP_NEAR[1] - 44, CAMP_NEAR[0] + 48, CAMP_NEAR[1] + 44)

# ====================================================================== Cyklopolis
CYC = 'Cyklopolis'
CYC_DEEP = 'Kuźnie Cyklopolis'
CYC_SEED = (580, 905)
CW, CH = 30, 34
CYC_LEVELS = (20, 60)
# budowle miasta (x0, y0, szer., wys.) i ulice (x0, y0, x1, y1) we wspolrzednych obrysu CW x CH
CYC_BOXES = [(1, 1, 17, 11), (20, 1, 9, 8), (1, 15, 8, 7), (1, 25, 9, 8), (21, 13, 8, 7), (20, 24, 9, 9)]
CYC_STREETS = [(0, 12, CW - 1, 13), (0, 22, CW - 1, 23), (10, 12, 19, CH - 1), (18, 0, 19, 12)]
CYC_FLOORS = {
    -1: ('oneeye_hall', ['giant_rat', 'cave_centipede', 'oneeye_brute', 'oneeye_brute', 'oneeye_hauler'], (3, 4), (15, 45)),
    -2: ('rock', ['burrow_larva', 'skeleton', 'oneeye_brute', 'oneeye_hauler', 'cave_troll'], (3, 4), (18, 50)),
    -3: ('oneeye_hall', ['oneeye_brute', 'oneeye_hauler', 'oneeye_smith', 'oneeye_hauler', 'oneeye_smith'], (3, 4), (25, 60)),
    -4: ('mine', ['cave_centipede', 'quarry_brute', 'oneeye_hauler', 'oneeye_smith', 'cave_troll'], (3, 4), (25, 65)),
    -5: ('flooded', ['cave_troll', 'oneeye_hauler', 'oneeye_smith', 'crystal_golem'], (3, 4), (30, 70)),
    -6: ('crystal', ['oneeye_smith', 'crystal_golem', 'oneeye_smith', 'earth_spirit'], (3, 4), (35, 80)),
}
CYC_SURFACE = ['oneeye_brute', 'oneeye_brute', 'oneeye_hauler', 'oneeye_brute', 'oneeye_smith', 'oneeye_hauler',
               'oneeye_brute', 'oneeye_smith']
CYC_WILD = ['boar', 'wolf', 'carrion_crow', 'boar', 'wolf']

# podziemia zarezerwowane przed lochami dziczy (generate_continent: wilds nie zajmuje tych prostokatow na zadnym
# pietrze pod ziemia) - inaczej glebokie lochy dziczy odsuwaja podziemia budowli stawianych pozniej daleko od wejsc
UNDER_RESERVED = [
    (700, 820, 880, 995),             # Bursztynowe Czeluscie
    (520, 840, 640, 975),             # kuznie Cyklopolis
    CAMP_RESERVED,                    # piwnice obozu
]


def _levels(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.split(';')
            out[p[0]] = int(p[2])
    return out


def _disc(r):
    return [(dx, dy) for dy in range(-r, r + 1) for dx in range(-r, r + 1) if dx * dx + dy * dy <= r * r]


def _land(c):
    return c is not None and c[0] not in ('water', 'deep_water')


def component(world, seed, limit=40000):
    """Pola ladu polaczone z seed (4 kierunki, cala powierzchnia ladu niezaleznie od przechodnosci)."""
    if not _land(world.get(*seed)):
        best = None
        for r in range(1, 20):
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    if _land(world.get(seed[0] + dx, seed[1] + dy)):
                        best = (seed[0] + dx, seed[1] + dy)
                        break
                if best:
                    break
            if best:
                break
        seed = best
    seen = {seed}
    st = [seed]
    while st and len(seen) < limit:
        x, y = st.pop()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n not in seen and _land(world.get(*n)):
                seen.add(n)
                st.append(n)
    return seen


# ====================================================================== wspolne: pietra komor
def dig(p, z, x0, y0, w, h, pts, style, rng, room=(3, 4)):
    """Komory w punktach pts (wspolrzedne planu) wpisane w pietro z; nie zamyka ani nie blokuje wczesniejszych przejsc."""
    rel = [(x - x0, y - y0) for (x, y) in pts]
    g = F.carve_rooms(w, h, rel, style, rng, room=room)
    F.fix_lava(g, rel)
    for y in range(h):
        for x in range(w):
            c = g[y][x]
            if c is None:
                continue
            X, Y = x0 + x, y0 + y
            old = p.get(X, Y, z)
            if old is not None and (St.walk(old) or old[2] in F.STAIRS) and not St.walk(c):
                continue
            if old is not None and old[2] in F.STAIRS:
                continue
            p.put(X, Y, z, c)


def _clear(p, z, q, style):
    floor = F.CAVE_STYLES[style][0]
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            c = p.get(q[0] + dx, q[1] + dy, z)
            if c is None or not St.walk(c) or c[0] in ('lava', 'water'):
                if c is not None and c[2] in F.STAIRS:
                    continue
                p.put(q[0] + dx, q[1] + dy, z, (floor, NONE, NONE, NONE))


def _connected(p, z, a, pts):
    seen = {a}
    todo = [a]
    while todo:
        x, y = todo.pop()
        for dx, dy in F.DIRS4:
            nb = (x + dx, y + dy)
            if nb not in seen and St.walk(p.get(nb[0], nb[1], z)):
                seen.add(nb)
                todo.append(nb)
    return all(q in seen for q in pts)


def rooms_floor(p, z, rect, arrive, n, style, rng, extra=(), down=True, inside=None, trim=None, gap=9,
                trunk_frac=0.55, room=(3, 4), wall=None, down_ok=None):
    """Pietro komor w prostokacie rect = (x0, y0, w, h): pien od arrive (i dodatkowych zejsc extra) do najdalszej komory
    (schody nizej), boczne slepe komory podpiete do pnia i petla. inside(q) - gdzie moga stac komory, trim(x, y) - pola
    pietra (reszta znika, brzeg staje sie murem wall). down_ok(q) - gdzie moga stac schody dalej.
    Zwraca dict(chain, trunk, side, down)."""
    x0, y0, w, h = rect
    fixed = [arrive] + list(extra)
    for _attempt in range(60):
        snap = dict(p.f.get(z, {}))
        pts = []
        tries = 0
        while len(pts) < n and tries < 6000:
            tries += 1
            q = (rng.randint(x0 + 4, x0 + w - 5), rng.randint(y0 + 4, y0 + h - 5))
            if inside is not None and not inside(q):
                continue
            if any(abs(q[0] - a) + abs(q[1] - b) < gap for (a, b) in pts + fixed):
                continue
            pts.append(q)
        if len(pts) < max(4, n * 2 // 3):
            continue
        dn = None
        if down:
            cands = [q for q in pts if down_ok is None or down_ok(q)]
            if not cands:
                continue
            dn = max(cands, key=lambda q: abs(q[0] - arrive[0]) + abs(q[1] - arrive[1]))
        rest = [q for q in pts + list(extra) if q != dn]
        chain = [arrive]
        while rest:
            nx = min(rest, key=lambda q: abs(q[0] - chain[-1][0]) + abs(q[1] - chain[-1][1]))
            rest.remove(nx)
            chain.append(nx)
        if dn:
            chain.append(dn)
        k = max(3, int(len(chain) * trunk_frac))
        trunk = chain[:k] + ([dn] if dn else []) + [e for e in extra if e not in chain[:k]]
        trunk = [q for i, q in enumerate(trunk) if q not in trunk[:i]]
        dig(p, z, x0, y0, w, h, trunk, style, rng, room=room)
        side = [q for q in chain if q not in trunk]
        for q in side:
            anchor = min((t for t in trunk if t != dn) or trunk, key=lambda t: abs(t[0] - q[0]) + abs(t[1] - q[1]))
            dig(p, z, x0, y0, w, h, [anchor, q], style, rng, room=(2, 3))
        if len(trunk) >= 5 and rng.random() < 0.7:
            dig(p, z, x0, y0, w, h, [trunk[1], trunk[4]], style, rng, room=(2, 3))
        if trim is not None:
            for (x, y) in list(p.f.get(z, {})):
                if not trim(x, y):
                    del p.f[z][(x, y)]
            for (x, y), c in list(p.f[z].items()):
                if (c[3] == NONE and c[2] not in F.STAIRS
                        and any((x + a, y + b) not in p.f[z] for a in (-1, 0, 1) for b in (-1, 0, 1))):
                    p.put(x, y, z, (c[0] if c[0] not in ('water', 'lava') else F.CAVE_STYLES[style][0], NONE, NONE,
                                    wall or F.CAVE_STYLES[style][1]))
        for q in fixed + ([dn] if dn else []):
            _clear(p, z, q, style)
        if _connected(p, z, arrive, chain):
            keep = {(q[0] + a, q[1] + b) for q in fixed + ([dn] if dn else []) for a in (-2, -1, 0, 1, 2)
                    for b in (-2, -1, 0, 1, 2)}
            F.ensure_lights(lambda x, y: p.get(x, y, z), lambda x, y, c: p.put(x, y, z, c),
                            [q for q, c in p.f[z].items() if St.walk(c) and q not in keep], F.CAVE_STYLES[style][4], rng)
            return dict(chain=chain, trunk=trunk, side=side, down=dn)
        p.f[z] = snap
    raise AssertionError(f'{p.name}: floor {z} not connected')


def populate(p, z, targets, mons, counts, level, radius=4, respawn=240):
    order = sorted((mons[i % len(mons)] for i in range(len(targets))), key=lambda m: level[m])
    for i, q in enumerate(targets):
        p.spawn(order[i], q[0], q[1], z, radius=radius, count=counts[i % 2], respawn=respawn)


def chest_spot(p, z, q):
    """Pole przy scianie komory, z ktorego zostaja co najmniej dwa wolne przejscia."""
    best = None
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            x, y = q[0] + dx, q[1] + dy
            c = p.get(x, y, z)
            if c is None or not St.walk(c) or c[2] != NONE:
                continue
            walls = sum(1 for a, b in F.DIRS4 if p.get(x + a, y + b, z) is not None and p.get(x + a, y + b, z)[3] != NONE)
            free = sum(1 for a, b in F.DIRS4 if St.walk(p.get(x + a, y + b, z)))
            if walls >= 1 and free >= 2:
                d = abs(dx) + abs(dy)
                if best is None or d < best[0]:
                    best = (d, (x, y))
    return best[1] if best else None


def floor_region(p, name, z, levels):
    cs = list(p.f[z])
    p.regions.append((name, min(c[0] for c in cs), min(c[1] for c in cs), max(c[0] for c in cs),
                      max(c[1] for c in cs), z, z) + tuple(levels))


def tag_packs(ctx, W, plan, only=None, level=None):
    """Spawny planu oznaczone jako stada (party_packs zostawia je bez zmian).

    Nigdy potwory do poziomu 15 ani punkty z mniej niz 3 potworami."""
    keys = {(W(x, y, z)[0], W(x, y, z)[1], z) for (m, x, y, z, _r, c, _s) in plan.spawns
            if c >= 3 and (only is None or only(z)) and (level is None or level[m] > 15)}
    out = []
    for ln in ctx['spawns']:
        f = ln.split(';')
        if not ln.startswith('#') and len(f) == 7 and (int(f[1]), int(f[2]), int(f[3])) in keys:
            ln += ';stado'
        out.append(ln)
    ctx['spawns'] = out


def dijkstra_path(cells_cost, start, goals):
    """Najtansza droga po polach (koszt pola z cells_cost, None = nie do przejscia) od start do ktoregos z goals."""
    goals = set(goals)
    dist = {start: 0}
    prev = {start: None}
    heap = [(0, start)]
    end = None
    while heap:
        d, p = heapq.heappop(heap)
        if p in goals:
            end = p
            break
        if d > dist.get(p, 1e18):
            continue
        for dx, dy in F.DIRS4:
            n = (p[0] + dx, p[1] + dy)
            c = cells_cost(n)
            if c is None:
                continue
            nd = d + c
            if nd < dist.get(n, 1e18):
                dist[n] = nd
                prev[n] = p
                heapq.heappush(heap, (nd, n))
    assert end is not None, f'no path from {start}'
    out = []
    while end is not None:
        out.append(end)
        end = prev[end]
    return out[::-1]


def world_trail(world, start, goal_set, allow_mountain=True):
    """Sciezka na powierzchni swiata od start do ktoregos z pol goal_set: przechodnie pola 1, drzewa/krzaki 3,
    skaly gor 8; pola sciezki staja sie przechodnie (bez obiektow, gory -> ziemia)."""
    def cost(n):
        c = world.get(*n)
        if not _land(c) or c[0] in ('lava', 'swamp', 'chasm'):
            return None
        if c[2] in F.STAIRS:
            return None
        if St.walk(c):
            return 1
        if c[3] == NONE and c[2] in ('tree', 'bush', 'rock', 'jungle_tree', 'palm', 'jungle_bush', 'pine', 'dead_tree',
                                     'autumn_tree', 'rubble', 'snow_rock', 'sandstone_rock', 'burnt_tree', 'frost_bush'):
            return 3
        if allow_mountain and c[3] in ('mountain', 'snow_mountain', 'sand_cliff'):
            return 8
        return None
    path = dijkstra_path(cost, start, goal_set)
    for q in path:
        c = world.get(*q)
        if not St.walk(c):
            g = c[0] if c[3] == NONE else 'dirt'
            world.put(q[0], q[1], 0, (g, NONE, NONE, NONE))
    return path


def world_reach(world, start, limit=60000):
    seen = {start}
    st = [start]
    while st and len(seen) < limit:
        x, y = st.pop()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n not in seen and St.walk(world.get(*n)):
                seen.add(n)
                st.append(n)
    return seen


# ====================================================================== 1. dosypany lad Wyspy Bursztynowej
def _grow(ctx, seed_cell, GROW_C, GROW_R, GROW_GAP, rng, mountain=False, max_x=None):
    """Dosypuje lad (elipsa GROW_C, GROW_R z falujacym brzegiem) do wyspy z polem seed_cell. Nowy lad nie zbliza sie
    do innych ladow na mniej niz GROW_GAP pol wody (zostaje przewoznik, bez kladek i mostow). mountain: srodek
    nowego ladu to skaly (masyw gorski), na brzegu piasek i trawa. Zwraca (stary lad, nowy lad)."""
    world, T = ctx['world'], ctx['T']
    S = len(world.floors[0])
    old = component(world, seed_cell)
    x0, y0 = GROW_C[0] - GROW_R[0] - GROW_GAP - 4, GROW_C[1] - GROW_R[1] - GROW_GAP - 4
    x1, y1 = GROW_C[0] + GROW_R[0] + GROW_GAP + 4, min(S - 1, GROW_C[1] + GROW_R[1] + GROW_GAP + 4)
    w, h = x1 - x0 + 1, y1 - y0 + 1
    other = np.zeros((h, w), bool)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = world.get(x, y)
            if _land(c) and (x, y) not in old:
                other[y - y0, x - x0] = True
    # odleglosc od cudzego ladu (Czebyszew)
    near = other.copy()
    for _ in range(GROW_GAP):
        n2 = near.copy()
        n2[1:, :] |= near[:-1, :]
        n2[:-1, :] |= near[1:, :]
        n2[:, 1:] |= near[:, :-1]
        n2[:, :-1] |= near[:, 1:]
        near = n2
    noise = np.random.RandomState(SEED).uniform(-1, 1, 16)
    new = set()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x, y) in old or y > S - 12 or x > S - 12 or (max_x is not None and x > max_x):
                continue
            if near[y - y0, x - x0]:
                continue
            a = math.atan2(y - GROW_C[1], x - GROW_C[0])
            k = (a + math.pi) / (2 * math.pi) * 16
            i = int(k) % 16
            t = k - int(k)
            wob = 1 + 0.12 * (noise[i] * (1 - t) + noise[(i + 1) % 16] * t)
            if ((x - GROW_C[0]) / (GROW_R[0] * wob)) ** 2 + ((y - GROW_C[1]) / (GROW_R[1] * wob)) ** 2 <= 1:
                new.add((x, y))
    # zatoki zamkniete przez nowy lad tez zasypane (bez jeziorek morskiej wody w srodku)
    land = old | new
    border = [(x, y) for x in range(x0, x1 + 1) for y in (y0, y1)] + [(x, y) for y in range(y0, y1 + 1) for x in (x0, x1)]
    sea = {q for q in border if q not in land and not _land(world.get(*q))}
    st = list(sea)
    while st:
        x, y = st.pop()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if x0 <= n[0] <= x1 and y0 <= n[1] <= y1 and n not in sea and n not in land and not _land(world.get(*n)):
                sea.add(n)
                st.append(n)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            inner = ((x - GROW_C[0]) / (GROW_R[0] * 1.3)) ** 2 + ((y - GROW_C[1]) / (GROW_R[1] * 1.3)) ** 2 <= 1
            if inner and (max_x is None or x <= max_x) and (x, y) not in sea and (x, y) not in land and not _land(world.get(x, y)) \
                    and not other[y - y0, x - x0] and not near[y - y0, x - x0]:
                new.add((x, y))
    land = old | new
    biome = str(T.biome[seed_cell[1], seed_cell[0]])
    ground = 'jungle_grass' if biome == 'jungle' else 'grass'
    for (x, y) in new:
        coast = any((x + dx, y + dy) not in land for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        v = rng.random()
        if coast:
            world.put(x, y, 0, ('sand', 'shells' if v < 0.03 else NONE, NONE, NONE))
        elif mountain and all((x + dx, y + dy) in land for dx in range(-3, 4) for dy in range(-3, 4)):
            world.put(x, y, 0, ('dirt', NONE, NONE, 'mountain'))
        else:
            obj = 'tree' if v < 0.11 else ('bush' if v < 0.15 else ('rock' if v < 0.16 else NONE))
            deco = rng.choice(('tall_grass', 'tall_grass', 'flowers', NONE, NONE, NONE, NONE, NONE, NONE, NONE)) \
                if obj == NONE else NONE
            world.put(x, y, 0, (ground, deco, obj, NONE))
        T.land[y, x] = True
        T.river[y, x] = False
        T.lake[y, x] = False
        T.biome[y, x] = biome
    # kilka skalnych wychodni w glebi nowego ladu (nie przy brzegu)
    inner = [q for q in sorted(new) if all((q[0] + dx, q[1] + dy) in land for dx in range(-6, 7, 3) for dy in range(-6, 7, 3))]
    rng.shuffle(inner)
    rocks = []
    for (x, y) in inner:
        if len(rocks) >= 5:
            break
        if any(abs(x - a) + abs(y - b) < 18 for (a, b) in rocks) or math.hypot(x - GROW_C[0], y - GROW_C[1]) < 26:
            continue
        rocks.append((x, y))
        r = rng.choice((1, 2))
        for (dx, dy) in _disc(r):
            if rng.random() < 0.85:
                world.put(x + dx, y + dy, 0, (ground, NONE, NONE, 'mountain'))
    # dawny brzeg (piasek) w srodku nowego ladu zarasta trawa
    for (x, y) in old:
        c = world.get(x, y)
        if c[0] == 'sand' and c[3] == NONE and all((x + dx, y + dy) in land for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)) \
                and any((x + dx, y + dy) in new for dx in (-3, 0, 3) for dy in (-3, 0, 3)):
            world.put(x, y, 0, (ground, NONE, c[2] if c[2] in ('tree', 'bush', 'rock') else NONE, NONE))
    T.water = ~T.land | T.river | T.lake
    return old, new


def _walk_size(world, seed):
    """Rozmiar przechodniego kawalka ladu przy seed (jak land_parts w generate_continent)."""
    start = None
    for r in range(15):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if start is None and F.walk(world.get(seed[0] + dx, seed[1] + dy)) and _land(world.get(seed[0] + dx, seed[1] + dy)):
                    start = (seed[0] + dx, seed[1] + dy)
    seen = {start}
    st = [start]
    while st:
        x, y = st.pop()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n not in seen and F.walk(world.get(*n)) and _land(world.get(*n)):
                seen.add(n)
                st.append(n)
    return len(seen)


def grow_island(ctx):
    """Wyspa Bursztynowa powiekszona na poludnie (przed przystaniami, drogami i dostepem do wysp).
    ctx['isle_old_cells']: dawny rozmiar wyspy (kolejnosc przewoznikow i ich nazwy bez zmian)."""
    rng = random.Random(SEED)
    walk_before = {ISLE_SEED: _walk_size(ctx['world'], ISLE_SEED)}
    old, new = _grow(ctx, ISLE_SEED, GROW_C, GROW_R, GROW_GAP, rng)
    ctx['isle_new_land'] = new
    ctx['isle_old_cells'] = dict(walk_before)
    return dict(stary_lad=len(old), nowy_lad=len(new))


# ====================================================================== 2. Bursztynowa Skala i Czelusci
def amber_rock(ctx, cost, helpers):
    world = ctx['world']
    rng = random.Random(SEED + 1)
    level = _levels(helpers['monsters_csv'])
    cells = component(world, ISLE_SEED)
    ferries = [n for n in ctx['npcs'] if n[2] == 'CAPTAIN' and int(n[6]) == 0 and (int(n[4]), int(n[5])) in cells]
    assert ferries, f'{ISLE}: no ferry'
    trans0 = [(t[0], t[1]) for t in ctx['transitions'] if t[2] == 0 and (t[0], t[1]) in cells]
    # srodek masywu: najdalej od wody (w nowym ladzie), z dala od przystani i zejsc
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    bx0, by0 = min(xs) - 2, min(ys) - 2
    m = np.zeros((max(ys) - by0 + 3, max(xs) - bx0 + 3), bool)
    for (x, y) in cells:
        m[y - by0, x - bx0] = True
    d = np.where(m, 1e9, 0.0)
    for _ in range(2):
        for flip in (False, True):
            a = d.T.copy() if flip else d
            for y in range(1, a.shape[0]):
                a[y] = np.minimum(a[y], a[y - 1] + 1)
            for y in range(a.shape[0] - 2, -1, -1):
                a[y] = np.minimum(a[y], a[y + 1] + 1)
            d = a.T.copy() if flip else a
    best = None
    for (x, y) in cells:
        dd = d[y - by0, x - bx0]
        if dd < 20:
            continue
        if any(abs(x - int(f[4])) + abs(y - int(f[5])) < 34 for f in ferries):
            continue
        if any(abs(x - a) + abs(y - b) < 30 for (a, b) in trans0):
            continue
        score = dd * 2 - math.hypot(x - GROW_C[0], y - GROW_C[1]) * 0.3
        if best is None or score > best[0]:
            best = (score, x, y, dd)
    assert best is not None, f'{ISLE}: no room for the rock'
    _s, OX, OY, dd = best
    R = int(min(19, dd - 6))
    assert R >= 14, f'{ISLE}: rock too small {R}'
    p = St.Plan(ROCK)
    # ---------------------------------------------------------------- powierzchnia: masyw, wieza u podnoza, wneka
    massif = set()
    wob = np.random.RandomState(SEED + 5).uniform(0, 1, 12)
    for (dx, dy) in _disc(R + 3):
        a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 12
        i = int(a) % 12
        t = a - int(a)
        if math.hypot(dx, dy) > R + 3 * (wob[i] * (1 - t) + wob[(i + 1) % 12] * t):
            continue
        c = world.get(OX + dx, OY + dy)
        massif.add((dx, dy))
        p.put(dx, dy, 0, (c[0] if c[0] not in ('sand', 'water') else 'grass', NONE, NONE, 'mountain'))
    tower = (-4, R - 4, 9, 9)
    p.box(0, *tower, 'stone_floor', 'stone_wall', doors=[(0, R + 4)])
    for q in ((-2, R - 2), (2, R + 1), (3, R - 3)):
        p.obj(q[0], q[1], 0, 'rubble')
    for q in ((-3, R + 4), (3, R + 4)):
        p.put(q[0], q[1], 0, ('stone_floor', NONE, 'broken_wall', NONE))
    p.obj(-3, R - 1, 0, 'wall_torch')
    # wneka w zachodnim zboczu z zejsciem
    for k in range(R - 5, R + 3):
        p.put(-k, 0, 0, ('cave_floor', NONE, NONE, NONE))
        p.put(-k, 1, 0, ('cave_floor', 'bones' if k % 3 == 0 else NONE, NONE, NONE))
    down_at = (-(R - 5), 0)
    out_tower = (0, R + 5)
    out_cave = (-(R + 3), 0)
    for q in (out_tower, out_cave, (out_tower[0], out_tower[1] + 1), (out_cave[0] - 1, out_cave[1])):
        c = world.get(OX + q[0], OY + q[1])
        p.put(q[0], q[1], 0, (c[0] if _land(c) else 'grass', NONE, NONE, NONE))
    p.entries += [out_tower, out_cave]
    # ---------------------------------------------------------------- +1..+3: polki skalne, ruiny wiezy, szczyt
    up_R = {1: R, 2: R - 4, 3: R - 8}
    arrive = (0, R - 6)
    upstairs = []
    ups = {}
    for z in (1, 2, 3):
        style, mons, counts, lv = UP_FLOORS[z]
        r = up_R[z]
        nxt = up_R.get(z + 1)
        lim = r - 2
        if z == 1:
            # szczyt wiezy na +1 (nad wieza u podnoza), otwarty na polke skalna
            for y in range(R - 4, R + 5):
                for x in range(-4, 5):
                    edge = x in (-4, 4) or y in (R - 4, R + 4)
                    p.put(x, y, 1, ('stone_floor', NONE, NONE, 'stone_wall') if edge else ('stone_floor', NONE, NONE, NONE))
            for y in (R - 4, R - 5):
                p.put(0, y, 1, ('stone_floor', NONE, NONE, NONE))
                p.put(1, y, 1, ('stone_floor', NONE, NONE, NONE))
            p.put(-2, R + 2, 1, ('stone_floor', NONE, 'broken_wall', NONE))

        def in_tower(x, y, z=z):
            return z == 1 and -4 <= x <= 4 and R - 5 <= y <= R + 4
        res = rooms_floor(p, z, (-r - 1, -r - 1, 2 * r + 3, 2 * r + 3), arrive, 7 + 2 * (3 - z) + 2, style, rng,
                          down=nxt is not None, gap=7, room=(2, 4),
                          inside=lambda q, lim=lim, it=in_tower: math.hypot(*q) <= lim - 1 and not (
                              -7 <= q[0] <= 7 and q[1] >= R - 9 and it(0, R)),
                          trim=lambda x, y, r=r, it=in_tower: math.hypot(x, y) <= r + 0.5 or it(x, y), wall='mountain',
                          down_ok=(lambda q, nl=nxt: math.hypot(*q) <= nl - 4) if nxt else None)
        ups[z] = res
        if res['down']:
            upstairs.append((z, res['down']))
            arrive = res['down']
    p.stairs(0, (0, R - 2), (0, R - 2))
    for (z, q) in upstairs:
        p.stairs(z, q, q)
    for z, res in ups.items():
        style, mons, counts, lv = UP_FLOORS[z]
        targets = [q for q in res['chain'] if q not in (res['chain'][0], res['down'])]
        populate(p, z, targets, mons, counts, level, radius=3)
        floor_region(p, ROCK, z, lv)
    # ---------------------------------------------------------------- -1..-6: Czelusci
    arrive = down_at
    deep = {}
    for k in range(1, 7):
        z = -k
        style, mons, counts, lv = DEEP_FLOORS[z]
        w, h = 52 + 4 * k, 40 + 3 * k
        ang = rng.random() * 6.283
        rx0 = arrive[0] - w // 2 + int(10 * math.cos(ang))
        ry0 = arrive[1] - h // 2 + int(8 * math.sin(ang))
        rx0 = min(max(rx0, arrive[0] - w + 6), arrive[0] - 5)
        ry0 = min(max(ry0, arrive[1] - h + 6), arrive[1] - 5)
        res = rooms_floor(p, z, (rx0, ry0, w, h), arrive, 11 + k, style, rng, down=k < 6, room=(3, 5))
        deep[z] = res
        p.stairs(z, arrive, arrive)
        targets = [q for q in res['chain'] if q not in (arrive, res['down'])]
        populate(p, z, targets, mons, counts, level)
        dead = sorted(res['side'], key=lambda q: -min(abs(q[0] - t[0]) + abs(q[1] - t[1]) for t in res['trunk']))
        for j, q in enumerate(dead[:1 + k // 3]):
            spot = chest_spot(p, z, q)
            if spot is not None:
                item, cnt = ISLE_CHESTS[(k + j) % len(ISLE_CHESTS)]
                p.chests.append((spot[0], spot[1], z, 600 + 500 * k + 200 * j, item, cnt))
        floor_region(p, DEEP, z, lv)
        if res['down']:
            arrive = res['down']
    # regiony powierzchni masywu
    p.regions.append((ROCK, -R - 1, -R - 1, R + 1, R + 6, 0, 0) + ISLE_LEVELS)
    n = p.check()
    for (mk, *_r) in p.spawns:
        assert mk in level, f'{ROCK}: unknown monster {mk}'
    St.clear_surface(ctx, OX - R - 2, OY - R - 2, OX + R + 2, OY + R + 6)
    W = St.stamp(ctx, p, (OX, OY), helpers['mark'], per_floor=True)
    tag_packs(ctx, W, p, level=level)
    # sciezki od przystani do wiezy i do wneki
    stand = (int(ferries[0][4]), int(ferries[0][5]))
    for q in (out_tower, out_cave):
        goal = (OX + q[0], OY + q[1])
        if goal not in world_reach(world, stand):
            start = next((stand[0] + a, stand[1] + b) for a, b in ((0, 0),) + F.DIRS4 if St.walk(world.get(stand[0] + a, stand[1] + b)))
            world_trail(world, start, {goal})
    # wiecej silnych potworow na calym ladzie wyspy (nowy lad pelny stad)
    free = sorted(q for q in ctx.get('isle_new_land', ()) if St.walk(world.get(*q)) and world.get(*q)[2] == NONE
                  and math.hypot(q[0] - OX, q[1] - OY) > R + 9)
    rng.shuffle(free)
    placed = []
    for (x, y) in free:
        if len(placed) >= len(ISLE_SURFACE):
            break
        if any(abs(x - a) + abs(y - b) < 12 for (a, b) in placed):
            continue
        if any(abs(x - int(f[4])) + abs(y - int(f[5])) < 12 for f in ferries):
            continue
        ctx['spawns'].append(f'{ISLE_SURFACE[len(placed)]};{x};{y};0;4;{4 + len(placed) % 2};240;stado')
        placed.append((x, y))
    floors = {}
    for z in range(-6, 4):
        floors[z] = sum(c for (_m, _x, _y, zz, _r, c, _s) in p.spawns if zz == z)
    floors[0] += sum(4 + i % 2 for i in range(len(placed)))
    info = dict(centre=(OX, OY), R=R, tower=(OX, OY + R + 5), cave=W(*out_cave, 0), stairs_down=W(*down_at, 0),
                deep_first=W(*down_at, -1) + (-1,), ferry=stand, plan_cells=n, potwory_wg_pietra=floors,
                up1=W(0, R - 2, 1) + (1,))
    for z in (-3, -6):
        q = deep[z]['chain'][1]
        info[f'p{z}'] = W(q[0], q[1], z) + (z,)
    q = ups[3]['chain'][1]
    info['p3'] = W(q[0], q[1], 3) + (3,)
    ctx['amber'] = info
    return info


# ====================================================================== 3. Oboz Lowczyn
HUTS = [(3, 3, 'S'), (3, 12, 'E'), (3, 21, 'E'), (30, 3, 'S'), (30, 12, 'W'), (30, 21, 'W')]


def camp_plan(world, origin, rng, level):
    p = St.Plan(CAMP)
    ox, oy = origin
    # pas dookola palisady: dzungla przerzedzona, wydeptana ziemia przy palisadzie
    for y in range(-PM, PH + PM):
        for x in range(-PM, PW + PM):
            if 0 <= x < PW and 0 <= y < PH:
                continue
            c = world.get(ox + x, oy + y)
            if c[0] in ('water', 'deep_water', 'swamp', 'lava', 'bridge'):
                p.put(x, y, 0, c)
                continue
            dd = max(-x - 1 if x < 0 else (x - PW if x >= PW else -1), -y - 1 if y < 0 else (y - PH if y >= PH else -1))
            g = c[0] if c[3] == NONE else 'jungle_grass'
            obj = c[2] if (c[3] == NONE and dd >= 3) else NONE
            if dd <= 1:
                g = 'dirt'
            p.put(x, y, 0, (g, c[1] if dd >= 2 and c[3] == NONE else NONE, obj, NONE))
    # srodek: ziemia i trawa dzungli
    for y in range(PH):
        for x in range(PW):
            edge = x in (0, PW - 1) or y in (0, PH - 1)
            g = 'dirt' if rng.random() < 0.55 else 'jungle_grass'
            p.put(x, y, 0, (g, NONE, NONE, 'log_wall' if edge else NONE))
    # jedyne wejscie: brama z drzwiami poziomu w palisadzie (oboz nie jest przejsciem; route_levels liczy go jako
    # lowisko za brama i nie obniza lowczyn do zwierzyny z okolicznej dzungli)
    p.level_door(0, (PW // 2, PH - 1), CAMP_DOOR_LEVEL, floor='dirt')
    # droga od bramy na poludnie
    for y in range(PH, PH + PM):
        for x in range(PW // 2 - 1, PW // 2 + 2):
            p.put(x, y, 0, ('dirt', NONE, NONE, NONE))
    p.entries += [(PW // 2, PH + PM - 1)]
    # chaty lowczyn
    hut_spots = []
    for (hx, hy, side) in HUTS:
        door = {'S': (hx + 3, hy + 5), 'E': (hx + 6, hy + 2), 'W': (hx, hy + 2)}[side]
        inner = p.box(0, hx, hy, 7, 6, 'wood_floor', 'log_wall', doors=[door])
        p.obj(hx + 1, hy + 1, 0, 'bed')
        p.obj(hx + 5, hy + 1, 0, 'barrel')
        p.obj(hx + 5, hy + 4, 0, 'clay_pot') if side != 'E' else p.obj(hx + 1, hy + 4, 0, 'clay_pot')
        hut_spots.append(inner[len(inner) // 2])
    # chata matrony (wodza): tron, stol, sztandary, zejscie do piwnic
    cx0, cy0, cw, ch = 14, 2, 12, 9
    p.box(0, cx0, cy0, cw, ch, 'wood_floor', 'log_wall', doors=[(cx0 + 6, cy0 + ch - 1)])
    p.obj(cx0 + 6, cy0 + 1, 0, 'throne')
    for q in ((cx0 + 2, cy0 + 1), (cx0 + 9, cy0 + 1)):
        p.obj(q[0], q[1], 0, 'banner')
    p.obj(cx0 + 3, cy0 + 4, 0, 'table')
    for x in range(cx0 + 5, cx0 + 8):
        for y in range(cy0 + 2, cy0 + 5):
            c = p.get(x, y, 0)
            if c[2] == NONE:
                p.put(x, y, 0, (c[0], 'rug', NONE, NONE))
    p.obj(cx0 + 1, cy0 + 7, 0, 'wall_torch')
    cellar_at = (cx0 + 9, cy0 + 6)
    # plac: ognisko, totemy, drewno, zagroda
    for (q, o) in (((20, 18), 'campfire'), ((16, 16), 'jungle_statue'), ((24, 16), 'jungle_statue'),
                   ((18, 22), 'firewood'), ((23, 22), 'firewood'), ((13, 28), 'well'), ((27, 28), 'clay_pot'),
                   ((16, 13), 'campfire'), ((26, 26), 'barrel')):
        p.obj(q[0], q[1], 0, o)
    for x in range(30, 37):
        p.obj(x, 28, 0, 'fence')
    for (x, y) in ((-3, 6), (-3, 25), (PW + 2, 8), (PW + 2, 26), (8, -3), (31, -3)):
        if p.get(x, y, 0) is not None and St.walk(p.get(x, y, 0)):
            p.obj(x, y, 0, 'campfire' if (x + y) % 2 else 'firewood')
    # spawny powierzchni
    # chaty matrony pilnuja tarczowniczki; sama Matrona siedzi w grocie pod obozem (przy skrzyni)
    p.spawn('grove_shieldmaiden', cx0 + 5, cy0 + 5, 0, radius=2, count=2, respawn=180)
    surf = [('grove_huntress', 10, 16), ('grove_huntress', 29, 18), ('grove_huntress', 20, 26), ('grove_huntress', 8, 28),
            ('grove_shieldmaiden', 13, 9), ('grove_shieldmaiden', 27, 9), ('grove_shieldmaiden', 20, 14),
            ('grove_crone', 14, 24), ('grove_crone', 26, 23)]
    for (m, x, y) in surf:
        p.spawn(m, x, y, 0, radius=3, count=3, respawn=180)
    for i, q in enumerate(hut_spots):
        p.spawn(('grove_huntress', 'grove_crone')[i % 2], q[0], q[1], 0, radius=1, count=1, respawn=180)
    for (m, x, y) in (('grove_huntress', -3, 15), ('grove_huntress', PW + 2, 18), ('grove_shieldmaiden', 20, -3)):
        p.spawn(m, x, y, 0, radius=3, count=2, respawn=180)
    # ---------------------------------------------------------------- piwnice (-1) i grota (-2)
    arrive = cellar_at
    r1 = rooms_floor(p, -1, (arrive[0] - 24, arrive[1] - 4, 46, 34), arrive, 8, 'grove_cellar', rng, gap=8, room=(2, 4))
    p.stairs(-1, arrive, arrive)
    t1 = [q for q in r1['chain'] if q not in (arrive, r1['down'])]
    # w piwnicach tylko lowczynie: slabsza zwierzyna bylaby celem drog przez caly oboz (route_levels obnizylby lowczynie)
    populate(p, -1, t1, ['grove_huntress', 'grove_crone', 'grove_shieldmaiden', 'grove_crone', 'grove_huntress'], (3, 3), level,
             radius=3, respawn=180)
    a2 = r1['down']
    r2 = rooms_floor(p, -2, (a2[0] - 20, a2[1] - 15, 40, 30), a2, 7, 'moss', rng, down=False, gap=8, room=(2, 4))
    p.stairs(-2, a2, a2)
    t2 = [q for q in r2['chain'] if q != a2]
    far = max(r2['side'] or t2, key=lambda q: abs(q[0] - a2[0]) + abs(q[1] - a2[1]))
    t2 = [q for q in t2 if q != far]
    populate(p, -2, t2, ['grove_crone', 'grove_shieldmaiden', 'grove_huntress', 'grove_shieldmaiden'], (3, 4), level,
             radius=3, respawn=180)
    p.spawn('grove_matriarch', far[0], far[1], -2, radius=2, count=2, respawn=600)
    spot = chest_spot(p, -2, far)
    assert spot is not None, f'{CAMP}: no chest spot'
    p.chests.append((spot[0], spot[1], -2) + CAMP_REWARD)
    # chest w slepej komorze piwnicy
    if r1['side']:
        s1 = chest_spot(p, -1, r1['side'][-1])
        if s1 is not None:
            p.chests.append((s1[0], s1[1], -1, 400, 'health_potion', 3))
    p.regions.append((CAMP, -PM, -PM, PW + PM - 1, PH + PM - 1, 0, 0) + CAMP_LEVELS)
    floor_region(p, CELLAR, -1, CELLAR_LEVELS)
    floor_region(p, CELLAR, -2, CELLAR_LEVELS)
    return p, dict(cellar=cellar_at, quest=spot)


def amazon_camp(ctx, cost, helpers):
    import strongholds as SH
    world = ctx['world']
    rng = random.Random(SEED + 2)
    level = _levels(helpers['monsters_csv'])
    tw, th = PW + 2 * PM, PH + 2 * PM
    why = {}
    tx, ty = St.find_site(ctx, helpers, cost, CAMP_NEAR, tw, th, core=(PM - 1, PM - 1, PM + PW, PM + PH), radius=45,
                          biomes={'jungle', 'forest', 'swamp'}, max_water=0.08, why=why, min_temple=55)
    origin = (tx + PM, ty + PM)
    plan, spots = camp_plan(world, origin, rng, level)
    plan.check()
    St.clear_surface(ctx, tx - 2, ty - 2, tx + tw + 1, ty + th + 1)
    W = St.stamp(ctx, plan, origin, helpers['mark'], per_floor=True)
    tag_packs(ctx, W, plan, only=lambda z: z < 0, level=level)
    # dawne spawny dziczy (sidlarze, bandyci, dziki) nie stoja w obozie - za palisada mieszkaja tylko lowczynie
    keep = []
    for ln in ctx['spawns']:
        f = ln.split(';')
        if (not ln.startswith('#') and len(f) >= 7 and f[3] == '0' and not f[0].startswith('grove_')
                and tx <= int(f[1]) < tx + tw and ty <= int(f[2]) < ty + th):
            continue
        keep.append(ln)
    ctx['spawns'] = keep
    start = (origin[0] + PW // 2, origin[1] + PH + PM)
    try:
        road = SH._connect(ctx, cost, helpers, start, (tx, ty, tx + tw - 1, ty + th - 1))
    except AssertionError:
        # droga do najblizszego traktu nie przechodzi (woda, gory) - sciezka do osiagalnego pola traktu
        reach = helpers['reach']
        goals = {(x, y) for y in range(start[1] - 70, start[1] + 71) for x in range(start[0] - 70, start[0] + 71)
                 if 0 <= x < len(world.floors[0]) and 0 <= y < len(world.floors[0]) and reach[y, x]
                 and world.get(x, y)[0] in F.ROAD_GROUND and not (tx - 1 <= x <= tx + tw and ty - 1 <= y <= ty + th)}
        path = world_trail(world, start, goals)
        road = path[-1]
    info = dict(origin=origin, gate=(origin[0] + PW // 2, origin[1] + PH), road=road,
                cellar=W(*spots['cellar'], 0) + (0,), cellar_down=W(*spots['cellar'], -1) + (-1,),
                quest_chest=W(*spots['quest'], -2) + (-2,))
    ctx['amazon_camp'] = info
    return info


# ====================================================================== 4. Cyklopolis
def cyclopolis(ctx, cost, helpers):
    world = ctx['world']
    rng = random.Random(SEED + 3)
    level = _levels(helpers['monsters_csv'])
    cells = component(world, CYC_SEED)
    ferries = [n for n in ctx['npcs'] if n[2] == 'CAPTAIN' and int(n[6]) == 0 and (int(n[4]), int(n[5])) in cells]
    assert ferries, f'{CYC}: no ferry'
    ferry = ferries[0]
    ferry[3] = CYC
    stand = (int(ferry[4]), int(ferry[5]))
    trans0 = [(t[0], t[1]) for t in ctx['transitions'] if t[2] == 0 and (t[0], t[1]) in cells]
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    bbox = (min(xs), min(ys), max(xs), max(ys))
    # miejsce miasta: prostokat CW x CH najpelniej na ladzie, z dala od zejsc i przystani
    best = None
    for y0 in range(bbox[1], bbox[3] - CH + 2):
        for x0 in range(bbox[0], bbox[2] - CW + 2):
            if any(x0 - 4 <= a <= x0 + CW + 3 and y0 - 4 <= b <= y0 + CH + 3 for (a, b) in trans0 + [stand]):
                continue
            n = sum(1 for y in range(y0, y0 + CH, 2) for x in range(x0, x0 + CW, 2) if (x, y) in cells)
            full = (CW // 2) * (CH // 2)
            if n < full * 0.78:
                continue
            score = n - abs(x0 + CW // 2 - stand[0]) * 0.05
            if best is None or score > best[0]:
                best = (score, x0, y0)
    assert best is not None, f'{CYC}: no room for the city'
    _s, CX0, CY0 = best
    OX, OY = CX0, CY0
    # plaskowyz miasta: budowle (z pasem), ulice i plac; reszta obrysu zostaje skala, trawa albo woda wyspy
    need = set()
    for (x0, y0, w, h) in CYC_BOXES:
        for y in range(y0 - 1, y0 + h + 1):
            for x in range(x0 - 1, x0 + w + 1):
                need.add((x, y))
    for (x0, y0, x1, y1) in CYC_STREETS:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                need.add((x, y))
    filled = set()
    for (x, y) in need:
        if (x + OX, y + OY) not in cells:
            filled.add((x + OX, y + OY))
    for (x, y) in filled:
        world.put(x, y, 0, ('dirt', NONE, NONE, NONE))
        cells.add((x, y))
    for (x, y) in filled:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in cells and not _land(world.get(*q)):
                    world.put(q[0], q[1], 0, ('sand', NONE, NONE, NONE))
                    cells.add(q)
    p = St.Plan(CYC)
    keep = {(a + dx, b + dy) for (a, b) in trans0 for dx in range(-2, 3) for dy in range(-2, 3)}
    for (x, y) in cells:
        p.put(x - OX, y - OY, 0, world.get(x, y))
    # ---------------------------------------------------------------- miasto: ruiny na skalnym plaskowyzu
    for (x, y) in sorted(need):
        if (x + OX, y + OY) in keep:
            continue
        v = rng.random()
        g = 'stone_floor' if v < 0.35 else 'dirt'
        p.put(x, y, 0, (g, rng.choice(('debris', NONE, NONE, NONE, 'bones')) if v > 0.9 else NONE, NONE, NONE))
    # skaly na krawedzi plaskowyzu: pola obok miasta, ktore byly trawa, czasem staja sie skala (poszarpany brzeg)
    for (x, y) in sorted(need):
        for dx, dy in F.DIRS4:
            q = (x + dx, y + dy)
            c = p.get(q[0], q[1], 0)
            if q in need or c is None or c[0] in ('water', 'deep_water', 'sand') or (q[0] + OX, q[1] + OY) in keep:
                continue
            if c[3] == NONE and rng.random() < 0.35:
                p.put(q[0], q[1], 0, ('dirt', NONE, NONE, 'mountain'))

    def ruin(x0, y0, w, h, doors, broken=0.18):
        inner = p.box(0, x0, y0, w, h, 'stone_floor', 'stone_wall', doors=doors)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                corner = x in (x0, x0 + w - 1) and y in (y0, y0 + h - 1)
                if edge and not corner and (x, y) not in doors and rng.random() < broken:
                    p.put(x, y, 0, ('stone_floor', NONE, 'rubble' if rng.random() < 0.5 else NONE, NONE))
        return inner
    # Wielka Kuznia: paleniska i kowadla, zejscie do hal pod miastem
    ruin(1, 1, 17, 11, doors=[(9, 11), (17, 6)], broken=0.06)
    for x in (3, 7, 11, 15):
        p.obj(x, 2, 0, 'forge')
        p.obj(x, 4, 0, 'anvil')
    p.obj(4, 9, 0, 'barrel')
    p.obj(2, 6, 0, 'wall_torch')
    big_down = (14, 8)
    # kuznia kowala
    ruin(20, 1, 9, 8, doors=[(24, 8)], broken=0.06)
    p.obj(22, 2, 0, 'forge')
    p.obj(25, 3, 0, 'anvil')
    p.obj(27, 2, 0, 'barrel')
    small_down = (26, 6)
    # zrujnowane domy olbrzymow
    for (x0, y0, w, h, door) in ((1, 15, 8, 7, (8, 18)), (1, 25, 9, 8, (9, 28)), (21, 13, 8, 7, (21, 16)),
                                 (20, 24, 9, 9, (20, 28))):
        inner = ruin(x0, y0, w, h, doors=[door], broken=0.25)
        q = inner[len(inner) // 3]
        p.obj(q[0], q[1], 0, rng.choice(('barrel', 'rubble', 'crate')))
    # plac: posag olbrzyma, studnia, gruz
    for (q, o) in (((14, 21), 'statue'), ((14, 27), 'well'), ((11, 17), 'rubble'), ((17, 31), 'rubble'),
                   ((12, 31), 'broken_wall'), ((18, 14), 'broken_wall')):
        p.obj(q[0], q[1], 0, o)
    # ---------------------------------------------------------------- droga od przystani do miasta przez skaly
    city = {q for q in need if St.walk(p.get(q[0], q[1], 0))}
    start = (stand[0] - OX, stand[1] - OY)
    st0 = next(((start[0] + a, start[1] + b) for a, b in ((0, 1), (1, 0), (-1, 0), (0, -1))
                if St.walk(p.get(start[0] + a, start[1] + b, 0))), start)

    def pcost(n):
        c = p.get(n[0], n[1], 0)
        if c is None or c[0] in ('water', 'deep_water', 'lava', 'swamp') or (n[0] + OX, n[1] + OY) in keep \
                or c[2] in F.STAIRS:
            return None
        if St.walk(c):
            return 1
        if c[3] == NONE:
            return 3
        return 7
    path = dijkstra_path(pcost, st0, city)
    for q in path:
        c = p.get(q[0], q[1], 0)
        if not St.walk(c) or c[0] not in ('stone_floor', 'wood_floor'):
            p.put(q[0], q[1], 0, ('dirt', NONE, NONE, NONE))
    p.entries.append(st0)
    # ---------------------------------------------------------------- pod miastem -1..-6
    arrive = big_down
    extra = [small_down]
    deep = {}
    for k in range(1, 7):
        z = -k
        style, mons, counts, lv = CYC_FLOORS[z]
        w, h = 48 + 4 * k, 38 + 3 * k
        ang = rng.random() * 6.283
        rx0 = arrive[0] - w // 2 + int(8 * math.cos(ang))
        ry0 = arrive[1] - h // 2 + int(6 * math.sin(ang))
        need = [arrive] + extra
        rx0 = min(max(rx0, max(q[0] for q in need) - w + 6), min(q[0] for q in need) - 5)
        ry0 = min(max(ry0, max(q[1] for q in need) - h + 6), min(q[1] for q in need) - 5)
        res = rooms_floor(p, z, (rx0, ry0, w, h), arrive, 10 + k, style, rng, extra=extra, down=k < 6, room=(3, 5))
        deep[z] = res
        p.stairs(z, arrive, arrive)
        for e in extra:
            p.stairs(z, e, e)
        targets = [q for q in res['chain'] if q not in [arrive, res['down']] + extra]
        populate(p, z, targets, mons, counts, level, radius=4, respawn=180)
        dead = sorted(res['side'], key=lambda q: -min(abs(q[0] - t[0]) + abs(q[1] - t[1]) for t in res['trunk']))
        for j, q in enumerate(dead[:1 + k // 3]):
            spot = chest_spot(p, z, q)
            if spot is not None:
                p.chests.append((spot[0], spot[1], z, 150 + 120 * k, ('health_potion', 'mana_potion',
                                                                      'strong_health_potion')[(k + j) % 3], 2))
        floor_region(p, CYC_DEEP, z, lv)
        extra = []
        if res['down']:
            arrive = res['down']
    # ---------------------------------------------------------------- spawny powierzchni
    surf = [(5, 6), (13, 7), (24, 4), (4, 18), (5, 29), (25, 16), (24, 28), (14, 24), (10, 31), (19, 19)]
    for i, (x, y) in enumerate(surf[:len(CYC_SURFACE)]):
        p.spawn(CYC_SURFACE[i], x, y, 0, radius=3, count=3, respawn=180)
    p.finish()
    seen0 = {(x, y) for (x, y, z) in p.reach([(st0[0], st0[1], 0)]) if z == 0}
    outside = sorted(q for q, c in p.f[0].items() if St.walk(c) and c[2] == NONE and q in seen0
                     and not (-3 <= q[0] <= CW + 2 and -3 <= q[1] <= CH + 2)
                     and abs(q[0] - start[0]) + abs(q[1] - start[1]) > 10)
    rng.shuffle(outside)
    wild = []
    for q in outside:
        if len(wild) >= len(CYC_WILD):
            break
        if any(abs(q[0] - a) + abs(q[1] - b) < 12 for (a, b) in wild):
            continue
        wild.append(q)
    for i, q in enumerate(wild):
        p.spawn(CYC_WILD[i], q[0], q[1], 0, radius=4, count=2, respawn=150)
    p.regions.append((CYC, bbox[0] - OX - 1, bbox[1] - OY - 1, bbox[2] - OX + 1, bbox[3] - OY + 1, 0, 0) + CYC_LEVELS)
    n = p.check()
    for (mk, *_r) in p.spawns:
        assert mk in level, f'{CYC}: unknown monster {mk}'
    # obce spawny powierzchni wyspy (silne potwory wysp) ustepuja miastu olbrzymow
    St.clear_surface(ctx, bbox[0], bbox[1], bbox[2], bbox[3])
    W = St.stamp(ctx, p, (OX, OY), helpers['mark'], per_floor=True)
    floors = {z: sum(c for (_m, _x, _y, zz, _r, c, _s) in p.spawns if zz == z) for z in range(-6, 1)}
    info = dict(city=(OX, OY), ferry=stand, forge=W(9, 6, 0), forge_down=W(*big_down, 0), smithy_down=W(*small_down, 0),
                deep_first=W(*big_down, -1) + (-1,), plan_cells=n, potwory_wg_pietra=floors)
    for z in (-3, -5):
        q = deep[z]['chain'][1]
        info[f'p{z}'] = W(q[0], q[1], z) + (z,)
    ctx['cyclopolis'] = info
    return info
