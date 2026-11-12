"""Szeroka rzeka z delta pod Varden i Wulkan Pozogi za pierscieniem lawy (Darek 2026-11-11, etap 4 duzej listy).

"Obok miasta Garden [Varden] niech plynie szeroka rzeka z delta ujscia do morza; miejsce polowania wulkan odgrodz
lawa, zeby nie mozna bylo przejsc, i daj tam statek, zeby mozna bylo plynac z innych miast; plywac moga tylko premium
i promocja. Pod wulkanem az -7 pieter i bardzo duzo silnych potworow i +7 bardzo duzo potworow mieszanych - exp dla
party, nie solo."

Rzeka: Srebrna Rzeka wyplywa z jeziora pod Varden na poludniowy wschod. Generator ja poszerza (kilka pol z kazdej
strony) i rozgalezia przy ujsciu w delte: glowne koryto i dwie odnogi do zatoki, z lachami piasku, trzcinami,
piaszczystymi brzegami i pomostami rybackimi. Drogi przechodza po mostach (zwykla siec drog z features.connect).

Wulkan Pozogi: wulkaniczny cypel na poludnie od delty, odciety od ladu pierscieniem lawy (nie da sie przejsc ani
zbudowac drogi), z przystania i kapitanem (rejsy z portow miast tylko z premium albo promocja). Stozek: +1..+6
lawowe korytarze we wnetrzu gory, +7 krater z jeziorem lawy. Pod spodem Trzewia Pozogi -1..-7: coraz wieksze pietra
z bocznymi slepymi komorami (skrzynie), z kazdym pietrem silniejsze i liczniejsze stada (stado = lowisko dla druzyn).

Wejscia z generate_continent: delta_terrain (teren, przed malowaniem kafli), volcano_surface (na poczatku build,
przed drogami - koszt drog w strefie nieskonczony), volcano_depths i delta_docks (na koncu build).
"""
import math
import random
from collections import deque

import numpy as np

import features as F
import structures as St

NONE = '-'
SEED = 20261113
# ---------------------------------------------------------------- rzeka i delta
RIVER_NAME = 'Srebrna Rzeka'
DELTA_REGION = 'Delta Srebrnej Rzeki'
RIVER_BOX = (538, 560, 660, 820)      # tu rzeka jest poszerzana (od jeziora pod Varden do zatoki)
RIVER_SEED = (612, 722)               # pole glownego koryta (szukane najblizsze pole rzeki)
WIDEN = 3                             # o tyle pol z kazdej strony szersze koryto
APEX = (601, 698)                     # tu rzeka rozdziela sie na odnogi
ARMS = [
    # odnogi od rozwidlenia: srodkowa (do zatoki pod Selvaran), poludniowa (wzdluz cypla wulkanu) i zachodnia (do
    # laguny za piaszczysta mierzeja) - wachlarz delty z wyspami miedzy odnogami
    [APEX, (606, 720), (614, 744), (626, 761), (642, 772)],
    [(APEX[0] - 2, APEX[1] + 3), (596, 726), (600, 752), (613, 773), (631, 787), (650, 797)],
    [(APEX[0] - 4, APEX[1] + 4), (587, 722), (581, 746), (585, 770), (583, 792), (574, 814)],
]
DELTA_BOX = (560, 680, 662, 818)      # region delty (lowisko z wegorzami, fishing.csv)
# ---------------------------------------------------------------- wulkan
NAME = 'Wulkan Pożogi'
CONE_NAME = 'Stożek Pożogi'
DEEP_NAME = 'Trzewia Pożogi'
VX, VY = 652, 850                     # srodek krateru
R_OUT, R_IN = 42, 36                  # pierscien lawy na ladzie miedzy R_IN i R_OUT
R_LAND = 33                           # lad wulkanicznej wyspy (dosypany, gdzie byla woda)
CONE = 24                             # podstawa stozka (gora) na powierzchni
UP_R = {1: 23, 2: 21, 3: 19, 4: 17, 5: 15, 6: 13, 7: 11}
CAPTAIN = ('pozoga_captain', 'Kapitan Ignacy Smolarz')
GREET_CAPTAIN = 'Wiozę na Wulkan Pożogi konta premium i postacie z promocją. Roi się tam od potworów - płyńcie drużyną.'
# porty, z ktorych plynie statek na wulkan (i z powrotem)
PORTS = ['varden_captain', 'valmir_captain', 'selv_captain', 'qas_captain', 'frost_captain', 'nerys_captain',
         'east_captain']
F.CAVE_STYLES.setdefault('magma_tube', ('ash', 'obsidian_wall', [], ['magma', 'obsidian_rock'], 'magma'))
F.CAVE_STYLES.setdefault('magma_deep', ('abyss_floor', 'obsidian_wall', [], ['magma', 'obsidian_rock'], 'magma'))

# Trzewia (-1..-7): bardzo duzo silnych potworow ognia; (potwory w kolejnosci od schodow, liczba w stadzie)
DEEP = {
    -1: (['fire_elemental', 'ember_beetle', 'ash_magus', 'coal_dragon', 'cinder_salamander'], (5, 6)),
    -2: (['ash_magus', 'lava_golem', 'cinder_salamander', 'lava_golem', 'coal_dragon'], (5, 6)),
    -3: (['lava_golem', 'cinder_salamander', 'ember_hound', 'coal_dragon', 'ember_hound'], (5, 6)),
    -4: (['ember_hound', 'magma_colossus', 'ember_hound', 'magma_colossus', 'ember_hound'], (5, 6)),
    -5: (['magma_colossus', 'magma_drake', 'magma_colossus', 'magma_drake', 'abyss_crawler'], (6, 6)),
    -6: (['magma_drake', 'abyss_crawler', 'abyssal_demon', 'abyss_crawler', 'magma_drake'], (6, 6)),
    -7: (['abyssal_demon', 'winged_spawn', 'abyssal_demon', 'winged_spawn', 'abyss_crawler'], (6, 6)),
}
DEEP_STYLE = {-1: 'fire', -2: 'fire', -3: 'fire', -4: 'magma_deep', -5: 'magma_deep', -6: 'core', -7: 'core'}
DEEP_LAVA = {-1: 0.03, -2: 0.04, -3: 0.05, -4: 0.05, -5: 0.06, -6: 0.07, -7: 0.08}
# Stozek (+1..+7): bardzo duzo potworow mieszanych (ogien, powietrze, mroz, cienie) - wyzej silniejsze
CONE_MONSTERS = {
    1: ['fire_elemental', 'wyvern', 'cinder_salamander', 'ash_magus', 'coal_dragon'],
    2: ['wyvern', 'storm_wisp', 'cinder_salamander', 'dune_drake', 'lava_golem'],
    3: ['storm_harpy', 'lava_golem', 'frost_giant', 'ember_hound', 'dune_drake'],
    4: ['rime_gargoyle', 'mist_wraith', 'ember_hound', 'storm_harpy', 'frost_giant'],
    5: ['ice_dragon', 'shadow_stalker', 'magma_colossus', 'dusk_gargoyle', 'rime_gargoyle'],
    6: ['sand_titan', 'shadow_centipede', 'magma_drake', 'void_priest', 'magma_colossus'],
    7: ['magma_drake', 'abyssal_demon', 'winged_spawn', 'bone_dragon', 'magma_drake'],
}
CONE_COUNT = {1: (4, 5), 2: (4, 5), 3: (5, 5), 4: (5, 5), 5: (5, 6), 6: (5, 6), 7: (5, 6)}
SURFACE_MONSTERS = ['fire_elemental', 'cinder_salamander', 'ember_beetle', 'fire_elemental', 'ash_magus',
                    'cinder_salamander', 'ember_beetle', 'fire_elemental']
DEEP_LEVELS = {-1: (320, 460), -2: (340, 500), -3: (380, 560), -4: (450, 650), -5: (520, 760), -6: (650, 900),
               -7: (760, 1000)}
CONE_LEVELS = {1: (300, 450), 2: (320, 500), 3: (380, 560), 4: (450, 650), 5: (520, 760), 6: (600, 860),
               7: (700, 1000)}
SURFACE_LEVELS = (300, 450)
CHEST_LOOT = [('great_health_potion', 3), ('great_mana_potion', 3), ('ultimate_health_potion', 2),
              ('great_spirit_potion', 2), ('abyssal_ember', 1), ('obsidian_shard', 5)]


def _disc(r):
    return [(dx, dy) for dy in range(-r, r + 1) for dx in range(-r, r + 1) if dx * dx + dy * dy <= r * r]


def _noise(seed):
    rs = np.random.RandomState(seed)
    return rs.uniform(-1, 1, 64)


def vdist(x, y, n=None):
    """Odleglosc od srodka wulkanu z falujacym brzegiem (pierscien nie jest idealnym kolem)."""
    d = math.hypot(x - VX, y - VY)
    if n is None:
        n = _NOISE
    a = math.atan2(y - VY, x - VX)
    k = (a + math.pi) / (2 * math.pi) * 16
    i = int(k) % 16
    t = k - int(k)
    w = n[i] * (1 - t) + n[(i + 1) % 16] * t
    return d + 1.6 * w


_NOISE = _noise(SEED)


def in_zone(x, y, margin=0):
    return vdist(x, y) <= R_OUT + margin


# ====================================================================== teren (przed malowaniem kafli)
def _flood_local(mask, start):
    h, w = mask.shape
    seen = np.zeros(mask.shape, bool)
    if not mask[start[1], start[0]]:
        return seen
    st = [start]
    seen[start[1], start[0]] = True
    while st:
        x, y = st.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and mask[ny, nx] and not seen[ny, nx]:
                seen[ny, nx] = True
                st.append((nx, ny))
    return seen


def _dist_local(src):
    """Odleglosc 8-sasiedztwa od pol src (lokalna tablica)."""
    d = np.where(src, 0.0, 1e9)
    for _ in range(2):
        for axis_flip in (False, True):
            a = d.T.copy() if axis_flip else d
            for y in range(1, a.shape[0]):
                a[y] = np.minimum(a[y], a[y - 1] + 1)
            for y in range(a.shape[0] - 2, -1, -1):
                a[y] = np.minimum(a[y], a[y + 1] + 1)
            d = a.T.copy() if axis_flip else a
    return d


def delta_terrain(T, protect, world=None):
    """Poszerza rzeke spod Varden do zatoki, dokleja odnogi delty i lachy piasku; ksztaltuje cypel wulkanu
    (lad, pierscien lawy, biom wulkaniczny). world - gotowa powierzchnia: nowa woda malowana od razu (cypel maluje
    volcano_surface). Zwraca opis do raportu."""
    S = T.land.shape[0]
    water_before = T.water.copy()
    rng = random.Random(SEED)
    x0, y0, x1, y1 = RIVER_BOX
    sl = (slice(y0, y1 + 1), slice(x0, x1 + 1))
    zone = np.zeros((S, S), bool)
    zx0, zy0, zx1, zy1 = VX - R_OUT - 3, VY - R_OUT - 3, VX + R_OUT + 3, VY + R_OUT + 3
    vd = np.full((S, S), 1e9)
    for y in range(zy0, zy1 + 1):
        for x in range(zx0, zx1 + 1):
            vd[y, x] = vdist(x, y)
    zone = vd <= R_OUT
    # ---- glowne koryto: pola rzeki polaczone z RIVER_SEED w prostokacie, poszerzone o WIDEN
    river = T.river[sl].copy()
    ys, xs = np.nonzero(river)
    k = int(np.argmin((xs + x0 - RIVER_SEED[0]) ** 2 + (ys + y0 - RIVER_SEED[1]) ** 2))
    comp = _flood_local(river, (int(xs[k]), int(ys[k])))
    wide = comp.copy()
    for (dx, dy) in _disc(WIDEN):
        sh = np.zeros_like(comp)
        h, w = comp.shape
        sh[max(0, dy):h + min(0, dy), max(0, dx):w + min(0, dx)] = comp[max(0, -dy):h - max(0, dy), max(0, -dx):w - max(0, dx)]
        wide |= sh
    yy = np.arange(y0, y1 + 1)[:, None] + np.zeros((1, x1 - x0 + 1))
    allowed = T.land[sl] & ~protect[sl] & ~T.lake[sl] & ~zone[sl] & (yy >= 566)
    added = wide & allowed & ~T.river[sl]
    T.river[sl] |= added
    # ---- odnogi delty: kretye koryta od rozwidlenia do zatoki, coraz szersze
    mark = np.zeros((S, S), bool)
    lines = []
    for pts in ARMS:
        line = T.meander(pts, rng, 3.0)
        T.paint_line(line, lambda t: 1.7 + 1.5 * t, mark)
        lines.append(line)
    arms = mark & T.land & ~protect & ~zone
    T.river |= arms
    # ---- lachy piasku w szerokim korycie (male wysepki), zapamietane do ubrania w piasek
    sub = T.river[sl] & T.land[sl]
    dist = _dist_local(~sub)
    cand = [(int(x) + x0, int(y) + y0) for y, x in zip(*np.nonzero(dist >= 4.5)) if y + y0 >= 640]
    rng.shuffle(cand)
    bars = []
    for (x, y) in cand:
        if len(bars) >= 9:
            break
        if any(abs(x - a) + abs(y - b) < 14 for (a, b, _r) in bars):
            continue
        bars.append((x, y, rng.choice((1, 1, 2))))
    cells = set()
    for (x, y, r) in bars:
        for dx in range(-r - 1, r + 2):
            for dy in range(-r, r + 1):
                if (dx / (r + 1.3)) ** 2 + (dy / (r + 0.4)) ** 2 <= 1.0 and T.river[y + dy, x + dx]:
                    cells.add((x + dx, y + dy))
    for (x, y) in cells:
        T.river[y, x] = False
    T.delta_sandbars = sorted(cells)
    # ---- cypel wulkanu: lad do R_LAND, pierscien lawy na ladzie, biom wulkaniczny, bez gor z obrazka
    T.land |= vd <= R_LAND
    T.river &= ~zone
    T.lake &= ~zone
    T.lava |= zone & T.land & (vd >= R_IN)
    T.lava &= ~(zone & (vd < R_IN))
    T.biome[zone] = 'volcanic'
    T.ridge[zone] = 0.0
    T.water = ~T.land | T.river | T.lake
    T.delta_lines = lines
    T.volcano_zone = zone
    if world is not None:
        ys, xs = np.nonzero(T.water & ~water_before & ~zone)
        for y, x in zip(ys.tolist(), xs.tolist()):
            world.put(x, y, 0, ('water', NONE, NONE, NONE))
    return {'poszerzenie': int(added.sum()), 'odnogi': int(arms.sum()), 'lachy': len(bars)}


# ====================================================================== powierzchnia wulkanu (przed drogami)
def volcano_surface(ctx, cost=None):
    """Stozek (gora) w srodku cypla, popiol z obsydianem i zweglonymi drzewami, strugi i oczka lawy, pierscien
    lawy; przystan z duzym statkiem i kapitanem; trasy statkow z portow. Strefa wylaczona z drog i zabudowy."""
    world = ctx['world']
    S = len(world.floors[0])
    rng = random.Random(SEED + 1)
    zone = []
    for y in range(VY - R_OUT - 3, VY + R_OUT + 4):
        for x in range(VX - R_OUT - 3, VX + R_OUT + 4):
            if 0 <= x < S and 0 <= y < S and in_zone(x, y):
                zone.append((x, y))
    streams = [math.radians(a) for a in (20, 75, 140, 200, 250, 320)]
    T = ctx['T']
    for (x, y) in zone:
        c = world.get(x, y)
        d = vdist(x, y)
        if not T.land[y, x]:
            world.put(x, y, 0, ('water', NONE, NONE, NONE))
            continue
        if d >= R_LAND - 2:          # lawa splywa do morza: brzeg cypla i pierscien od strony ladu
            world.put(x, y, 0, ('lava', NONE, NONE, NONE))
        elif math.hypot(x - VX, y - VY) <= CONE:
            world.put(x, y, 0, ('ash', NONE, NONE, 'mountain'))
        else:
            a = math.atan2(y - VY, x - VX)
            r = math.hypot(x - VX, y - VY)
            in_stream = r < CONE + 4 and any(abs((a - s + math.pi) % (2 * math.pi) - math.pi) * r < 0.9 for s in streams)
            if in_stream:
                world.put(x, y, 0, ('lava', NONE, NONE, NONE))
            elif c[3] != NONE or c[0] not in ('ash', 'dark_sand') or (c[2] != NONE and c[2] not in ('obsidian_rock', 'burnt_tree')):
                v = rng.random()
                world.put(x, y, 0, ('ash' if v < 0.8 else 'dark_sand', NONE,
                                    'obsidian_rock' if v < 0.03 else ('burnt_tree' if v < 0.05 else NONE), NONE))
    # oczka lawy na popiele (nie odcinaja przejsc - sprawdzane nizej)
    ring = [(x, y) for (x, y) in zone if CONE + 3 <= math.hypot(x - VX, y - VY) <= R_LAND - 5
            and F.walk(world.get(x, y))]
    rng.shuffle(ring)
    pools = []
    for (x, y) in ring:
        if len(pools) >= 7:
            break
        if any(abs(x - a) + abs(y - b) < 12 for (a, b) in pools):
            continue
        pools.append((x, y))
        for (dx, dy) in _disc(1):
            c = world.get(x + dx, y + dy)
            if c is not None and F.walk(c):
                world.put(x + dx, y + dy, 0, ('lava', NONE, NONE, NONE))
    # przystan na wschodnim brzegu (statek na morzu): pas piasku od podnoza stozka do brzegu - brzeg lezy wewnatrz
    # pierscienia lawy (inaczej pas przecialby pierscien)
    start = (VX + CONE + 2, VY + 2)
    x = start[0]
    path = []
    while world.get(x + 1, start[1])[0] not in ('water', 'deep_water'):
        x += 1
        path.append((x, start[1]))
        assert x < VX + R_OUT + 8, f'{NAME}: no sea east of the cone'
    assert all(vdist(px, py) < R_IN - 1 for (px, py) in path), f'{NAME}: harbour path crosses the lava ring {path[-3:]}'
    for (px, py) in [start] + path:
        for dy in (-1, 0, 1):
            c = world.get(px, py + dy)
            if vdist(px, py + dy) < R_IN - 1 and c[0] not in ('water', 'deep_water') and c[3] == NONE:
                world.put(px, py + dy, 0, ('dark_sand', NONE, NONE, NONE))
    captain, rect = F.harbour_with_ship(world, start, F.E)
    ctx['pz'].append((*rect, 0))
    ctx['npcs'].append([CAPTAIN[0], CAPTAIN[1], 'CAPTAIN', NAME, captain[0], captain[1], 0, '-', '#8a4a2a', GREET_CAPTAIN])
    ctx['routes'] += [(p, CAPTAIN[0]) for p in PORTS]
    # spojnosc: caly popiol strefy osiagalny od kapitana (oczka i strugi lawy nie odcinaja kawalkow)
    _fix_zone_reach(world, zone, captain)
    footprint = ctx['footprint']
    zx = [p[0] for p in zone] + [rect[0], rect[2]]
    zy = [p[1] for p in zone] + [rect[1], rect[3]]
    box = (min(zx) - 2, min(zy) - 2, max(zx) + 2, max(zy) + 2)
    for (x, y) in zone:
        footprint[y, x] = True
    for y in range(rect[1], rect[3] + 1):
        for x in range(rect[0], rect[2] + 1):
            footprint[y, x] = True          # koszt drog liczony pozniej z footprint: tu nieskonczony
    ctx['volcano'] = dict(zone=set(zone), captain=captain, harbour=rect, box=box, pools=pools)
    return dict(kapitan=captain, przystan=rect, oczka=len(pools))


def _fix_zone_reach(world, zone, captain):
    zs = set(zone)
    for _ in range(4):
        seen = {captain}
        q = deque([captain])
        while q:
            x, y = q.popleft()
            for dx, dy in F.DIRS4:
                n = (x + dx, y + dy)
                if n not in seen and F.walk(world.get(*n)) and (n in zs or abs(n[0] - captain[0]) + abs(n[1] - captain[1]) < 30):
                    seen.add(n)
                    q.append(n)
        lost = [p for p in zs if F.walk(world.get(*p)) and p not in seen]
        if not lost:
            return
        # odciete kawalki: najpierw lawa strug i oczek miedzy nimi a reszta zamienia sie w popiol, potem kamienie
        for (x, y) in zs:
            c = world.get(x, y)
            if c[0] == 'lava' and vdist(x, y) < R_LAND - 2 and math.hypot(x - VX, y - VY) > CONE:
                world.put(x, y, 0, ('ash', NONE, NONE, NONE))
            elif _ > 0 and c[2] != NONE and c[3] == NONE and c[0] not in ('water', 'deep_water', 'lava'):
                world.put(x, y, 0, (c[0], c[1], NONE, NONE))
    raise AssertionError(f'volcano surface split: {lost[:5]}')


# ====================================================================== pietra wulkanu (na koncu build)
def _levels(path):
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.split(';')
            out[p[0]] = int(p[2])
    return out


def _clear(p, z, q, style, r=1):
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            c = p.get(q[0] + dx, q[1] + dy, z)
            if c is None or not St.walk(c) or c[0] == 'lava':
                p.put(q[0] + dx, q[1] + dy, z, (F.CAVE_STYLES[style][0], NONE, NONE, NONE))


def _connected(p, z, a, pts):
    seen = {a}
    todo = [a]
    while todo:
        x, y = todo.pop()
        for dx, dy in F.DIRS4:
            nb = (x + dx, y + dy)
            if nb not in seen and nb in p.f[z] and St.walk(p.get(nb[0], nb[1], z)):
                seen.add(nb)
                todo.append(nb)
    return all(q in seen for q in pts), seen


def deep_floors(p, up, rng, level):
    """Trzewia -1..-7: kazde pietro wieksze, pien komor od schodow do schodow w dol i boczne slepe komory
    (czesc ze skrzyniami); stada w komorach - blizej schodow slabsze. Zwraca {z: komory}."""
    out = {}
    for k in range(1, 8):
        z = -k
        w, h = 44 + 4 * k, 34 + 3 * k
        ang = rng.random() * 6.283
        x0 = -w // 2 + int(round(5 * math.cos(ang)))
        y0 = -h // 2 + int(round(4 * math.sin(ang)))
        x0 = min(max(x0, up[0] - w + 5), up[0] - 4)
        y0 = min(max(y0, up[1] - h + 5), up[1] - 4)
        n = 9 + k
        style = DEEP_STYLE[z]
        for attempt in range(40):
            snap = dict(p.f.get(z, {}))
            pts = []
            tries = 0
            while len(pts) < n and tries < 5000:
                tries += 1
                q = (rng.randint(x0 + 4, x0 + w - 5), rng.randint(y0 + 4, y0 + h - 5))
                if any(abs(q[0] - a) + abs(q[1] - b) < 9 for (a, b) in pts + [up]):
                    continue
                pts.append(q)
            assert len(pts) >= 6, f'{DEEP_NAME}: no room on floor {z}'
            down = max(pts, key=lambda q: abs(q[0] - up[0]) + abs(q[1] - up[1])) if k < 7 else None
            rest = [q for q in pts if q != down]
            chain = [up]
            while rest:
                nxt = min(rest, key=lambda q: abs(q[0] - chain[-1][0]) + abs(q[1] - chain[-1][1]))
                rest.remove(nxt)
                chain.append(nxt)
            if down:
                chain.append(down)
            trunk_len = 4 + k // 2
            trunk = chain[:trunk_len] + ([down] if down else [])
            trunk = [q for i, q in enumerate(trunk) if q not in trunk[:i]]
            p.cave(z, x0, y0, w, h, trunk, style, rng, room=(3, 5), lava=DEEP_LAVA[z])
            side = [q for q in chain if q not in trunk]
            for q in side:
                anchor = min(trunk, key=lambda t: abs(t[0] - q[0]) + abs(t[1] - q[1]))
                p.cave(z, x0, y0, w, h, [anchor, q], style, rng, room=(2, 4), lava=DEEP_LAVA[z] / 2)
            for q in [up] + ([down] if down else []):
                _clear(p, z, q, style)
            ok, _seen = _connected(p, z, up, chain)
            if ok:
                break
            p.f[z] = snap
        else:
            raise AssertionError(f'{DEEP_NAME}: floor {z} chambers not connected')
        _lights(p, z, chain, rng)
        p.stairs(z, up, up)
        mons, counts = DEEP[z]
        targets = [q for q in chain if q != up and q != down]
        order = sorted((mons[i % len(mons)] for i in range(len(targets))), key=lambda m: level[m])
        for i, q in enumerate(targets):
            p.spawn(order[i], q[0], q[1], z, radius=4, count=counts[i % 2], respawn=240)
        # slepe komory na koncu odnog: skrzynia z lupem (najdalsze od pnia)
        dead = sorted(side, key=lambda q: -min(abs(q[0] - t[0]) + abs(q[1] - t[1]) for t in trunk))[:1 + k // 3]
        for j, q in enumerate(dead):
            item, cnt = CHEST_LOOT[(k + j) % len(CHEST_LOOT)]
            spot = _chest_spot(p, z, q)
            if spot is not None:
                p.chests.append((spot[0], spot[1], z, 500 + 500 * k + 200 * j, item, cnt))
        out[z] = chain
        if down:
            up = down
    return out


def _lights(p, z, chain, rng):
    """Swiatlo w kazdej komorze: magma (blokuje pole, ale tylko tam, gdzie 8 sasiadow jest wolnych - nie odcina
    przejscia) obok srodka komory."""
    n = 0
    for q in chain:
        for (dx, dy) in sorted(((a, b) for a in range(-3, 4) for b in range(-3, 4) if 2 <= abs(a) + abs(b) <= 4),
                               key=lambda d: (abs(d[0]) + abs(d[1]), rng.random())):
            x, y = q[0] + dx, q[1] + dy
            c = p.get(x, y, z)
            if c is None or not St.walk(c) or c[2] != NONE or c[0] == 'lava' or (x, y) in chain:
                continue
            if all(St.walk(p.get(x + a, y + b, z)) and p.get(x + a, y + b, z)[2] == NONE
                   for a in (-1, 0, 1) for b in (-1, 0, 1) if (a, b) != (0, 0)):
                p.put(x, y, z, (c[0], c[1], 'magma', NONE))
                n += 1
                break
    return n


def _chest_spot(p, z, q):
    """Pole przy scianie komory (8 sasiadow: sciana obok, przejscie wokol zachowane)."""
    best = None
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            x, y = q[0] + dx, q[1] + dy
            c = p.get(x, y, z)
            if c is None or not St.walk(c) or c[2] != NONE or c[0] == 'lava':
                continue
            walls = sum(1 for a, b in F.DIRS4 if p.get(x + a, y + b, z) is not None and p.get(x + a, y + b, z)[3] != NONE)
            free = sum(1 for a, b in F.DIRS4 if St.walk(p.get(x + a, y + b, z)))
            if walls >= 1 and free >= 2:
                d = abs(dx) + abs(dy)
                if best is None or d < best[0]:
                    best = (d, (x, y))
    return best[1] if best else None


def cone_floors(p, up0, rng, level):
    """Stozek +1..+7: lawowe korytarze we wnetrzu gory (+1..+6, w kole coraz mniejszym), na +7 krater z jeziorem lawy
    i krawedzia. up0 - pole schodow z powierzchni (na +1 schody w dol). Zwraca {z: komory}."""
    out = {}
    stairs = []
    arrive = up0
    for z in range(1, 8):
        r = UP_R[z]
        if z == 7:
            cells = []
            for (dx, dy) in _disc(r + 1):
                d = math.hypot(dx, dy)
                if d > r:
                    p.put(dx, dy, z, ('ash', NONE, NONE, 'mountain'))
                elif d < 4.2:
                    p.put(dx, dy, z, ('lava', NONE, NONE, NONE))
                else:
                    v = rng.random()
                    p.put(dx, dy, z, ('ash', NONE, 'magma' if v < 0.04 and d > 6 else ('obsidian_rock' if v < 0.06 and d > 6 else NONE), NONE))
                    cells.append((dx, dy))
            _clear(p, z, arrive, 'magma_tube')
            # stada dookola krateru
            pts = []
            for i in range(7):
                a = 2 * math.pi * i / 7 + 0.4
                q = (int(round(math.cos(a) * 7.5)), int(round(math.sin(a) * 7.5)))
                if abs(q[0] - arrive[0]) + abs(q[1] - arrive[1]) >= 5:
                    pts.append(q)
            out[z] = [arrive] + pts
            break
        lim = r - 6
        nxt_lim = UP_R[z + 1] - 6 if z + 1 < 7 else 5
        for attempt in range(40):
            snap = dict(p.f.get(z, {}))
            n = 6 + (7 - z)
            pts = []
            tries = 0
            while len(pts) < n and tries < 4000:
                tries += 1
                a = rng.random() * 2 * math.pi
                rr = math.sqrt(rng.random()) * lim
                q = (int(round(math.cos(a) * rr)), int(round(math.sin(a) * rr)))
                if any(abs(q[0] - u) + abs(q[1] - v) < 7 for (u, v) in pts + [arrive]):
                    continue
                pts.append(q)
            # schody w gore: pole w kole nastepnego pietra (krater na +7: na krawedzi)
            if z + 1 == 7:
                cands = [(int(round(math.cos(a) * 7.5)), int(round(math.sin(a) * 7.5))) for a in
                         [i * math.pi / 8 for i in range(16)]]
            else:
                cands = [q for q in pts if math.hypot(*q) <= nxt_lim]
                if not cands:
                    a = rng.random() * 2 * math.pi
                    rr = rng.random() * nxt_lim
                    cands = [(int(round(math.cos(a) * rr)), int(round(math.sin(a) * rr)))]
            up = max(cands, key=lambda q: abs(q[0] - arrive[0]) + abs(q[1] - arrive[1]))
            if up not in pts:
                pts.append(up)
            rest = [q for q in pts if q != up]
            chain = [arrive]
            while rest:
                nx = min(rest, key=lambda q: abs(q[0] - chain[-1][0]) + abs(q[1] - chain[-1][1]))
                rest.remove(nx)
                chain.append(nx)
            chain.append(up)
            box = r + 1
            trunk = chain[:3 + (7 - z) // 2] + [up]
            trunk = [q for i, q in enumerate(trunk) if q not in trunk[:i]]
            p.cave(z, -box, -box, 2 * box + 1, 2 * box + 1, trunk, 'magma_tube', rng, room=(3, 4), lava=0.03)
            side = [q for q in chain if q not in trunk]
            for q in side:
                anchor = min(trunk, key=lambda t: abs(t[0] - q[0]) + abs(t[1] - q[1]))
                p.cave(z, -box, -box, 2 * box + 1, 2 * box + 1, [anchor, q], 'magma_tube', rng, room=(3, 4), lava=0.02)
            # w kole stozka: pola dalej niz r + 1 od srodka znikaja (sciana zostaje na brzegu)
            for (x, y) in list(p.f.get(z, {})):
                if math.hypot(x, y) > r + 0.5:
                    del p.f[z][(x, y)]
            for (x, y), c in list(p.f[z].items()):
                if St.walk(c) and any((x + a, y + b) not in p.f[z] for a in (-1, 0, 1) for b in (-1, 0, 1)):
                    p.put(x, y, z, (c[0], NONE, NONE, 'obsidian_wall'))
            for q in (arrive, up):
                _clear(p, z, q, 'magma_tube')
            ok, _seen = _connected(p, z, arrive, chain)
            if ok:
                _lights(p, z, chain, rng)
                break
            p.f[z] = snap
        else:
            raise AssertionError(f'{CONE_NAME}: floor +{z} not connected')
        out[z] = chain
        stairs.append((z, up))
        arrive = up
    # stada: w kazdej komorze poza schodami, slabsze blizej wejscia
    for z, chain in out.items():
        mons = CONE_MONSTERS[z]
        targets = [q for q in chain[1:] if q != chain[-1] or z == 7]
        order = sorted((mons[i % len(mons)] for i in range(len(targets))), key=lambda m: level[m])
        cnt = CONE_COUNT[z]
        for i, q in enumerate(targets):
            p.spawn(order[i], q[0], q[1], z, radius=3 if z >= 6 else 4, count=cnt[i % 2], respawn=240)
    return out, stairs


def volcano_depths(ctx, cost, helpers):
    """Plan wulkanu: powierzchnia strefy (kopia), wejscia w stozku, Trzewia -1..-7 i Stozek +1..+7; spawny,
    regiony, skrzynie; sprawdzenie drogi od kapitana do kazdego spawnu i schodow; klad na swiecie."""
    world = ctx['world']
    info = ctx['volcano']
    rng = random.Random(SEED + 2)
    level = _levels(helpers['monsters_csv'])
    zone = info['zone']
    # obce spawny powierzchni i przejscia w strefie: strefa jest tylko dla wulkanu
    foreign = [t for t in ctx['transitions'] if (t[2] == 0 and (t[0], t[1]) in zone) or (t[6] == 0 and (t[4], t[5]) in zone)]
    assert not foreign, f'{NAME}: foreign transitions in the zone {foreign[:3]}'
    ctx['spawns'] = [ln for ln in ctx['spawns'] if ln.startswith('#') or ln.split(';')[3] != '0'
                     or (int(ln.split(';')[1]), int(ln.split(';')[2])) not in zone]
    p = St.Plan(NAME)
    bx0, by0, bx1, by1 = info['box']
    for y in range(by0, by1 + 1):
        for x in range(bx0, bx1 + 1):
            c = world.get(x, y)
            if c is not None and ((x, y) in zone or (info['harbour'][0] <= x <= info['harbour'][2]
                                                     and info['harbour'][1] <= y <= info['harbour'][3])):
                p.put(x - VX, y - VY, 0, c)
    cx, cy = info['captain']
    p.entries.append((cx - VX, cy - VY))
    # wejscie na stozek (poludnie) i do Trzewi (zachod): wneki w zboczu
    for k in range(CONE - 3, CONE + 1):
        p.put(0, k, 0, ('ash', NONE, NONE, NONE))
        p.put(-k, 0, 0, ('ash', NONE, NONE, NONE))
    for k in range(CONE + 1, CONE + 4):
        for q in ((0, k), (-k, 0)):
            c = p.get(q[0], q[1], 0)
            if c is not None and not St.walk(c):
                p.put(q[0], q[1], 0, ('ash', NONE, NONE, NONE))
    for q in ((-1, CONE), (1, CONE), (-CONE, -1), (-CONE, 1)):
        p.put(q[0], q[1], 0, ('ash', NONE, NONE, 'mountain'))
    up_at = (0, CONE - 3)
    down_at = (-CONE + 3, 0)
    # stozek: na +1 schody w dol tuz nad wejsciem (w kole +1); schody po wykuciu wszystkich pieter
    _cone, cone_stairs = cone_floors(p, (0, CONE - 5), rng, level)
    p.stairs(0, up_at, (0, CONE - 5), land_down=(0, CONE - 2))
    for (z, q) in cone_stairs:
        p.stairs(z, q, q)
    # Trzewia: na -1 schody w gore w tym samym polu planu co zejscie na powierzchni (deep_floors: p.stairs(-1, ...))
    deep = deep_floors(p, down_at, rng, level)
    # stada na powierzchni (popiol miedzy stozkiem a pierscieniem) - z dala od przystani
    hx0, hy0, hx1, hy1 = info['harbour']
    spots = []
    cand = sorted((x, y) for (x, y), c in p.f[0].items() if St.walk(c) and c[2] == NONE and c[0] in ('ash', 'dark_sand')
                  and CONE + 3 <= math.hypot(x, y) <= R_LAND - 4)
    rng.shuffle(cand)
    for (x, y) in cand:
        if len(spots) >= len(SURFACE_MONSTERS):
            break
        wx, wy = x + VX, y + VY
        if hx0 - 8 <= wx <= hx1 + 8 and hy0 - 8 <= wy <= hy1 + 8:
            continue
        if any(abs(x - a) + abs(y - b) < 11 for (a, b) in spots):
            continue
        if abs(x - up_at[0]) + abs(y - CONE) < 6 or abs(x + CONE) + abs(y) < 6:
            continue
        spots.append((x, y))
    for i, (x, y) in enumerate(spots):
        p.spawn(SURFACE_MONSTERS[i], x, y, 0, radius=4, count=4 + i % 2, respawn=240)
    # regiony: powierzchnia (ostatni na liscie planu = pierwszy w swiecie), pietra stozka i Trzewi
    for z in range(-7, 0):
        cs = [xy for xy, c in p.f[z].items()]
        lo, hi = DEEP_LEVELS[z]
        p.regions.append((DEEP_NAME, min(c[0] for c in cs), min(c[1] for c in cs), max(c[0] for c in cs),
                          max(c[1] for c in cs), z, z, lo, hi))
    for z in range(7, 0, -1):
        cs = list(p.f[z])
        lo, hi = CONE_LEVELS[z]
        p.regions.append((CONE_NAME, min(c[0] for c in cs), min(c[1] for c in cs), max(c[0] for c in cs),
                          max(c[1] for c in cs), z, z, lo, hi))
    # powierzchnia: pasy po 3 wiersze w obrysie wnetrza pierscienia (prostokaty nie siegaja ladu za lawa)
    for y0 in range(-R_IN - 2, R_IN + 3, 3):
        xs = [x - VX for (x, y) in zone if y0 <= y - VY < y0 + 3 and vdist(x, y) <= R_IN]
        if xs:
            p.regions.append((NAME, min(xs), y0, max(xs), y0 + 2, 0, 0) + SURFACE_LEVELS)
    hx0, hy0, hx1, hy1 = info['harbour']
    p.regions.append((NAME, hx0 - VX, hy0 - VY, hx1 - VX, hy1 - VY, 0, 0) + SURFACE_LEVELS)
    n = p.check()
    for (m, x, y, z, r, cnt, resp) in p.spawns:
        assert m in level, f'{NAME}: unknown monster {m}'
    W = St.stamp(ctx, p, (VX, VY), helpers['mark'], per_floor=True)
    # spawny wulkanu: stada dla druzyn (party_packs zostawia linie z dodatkowym polem)
    tagged = []
    keys = {(W(x, y, z)[0], W(x, y, z)[1], z) for (_m, x, y, z, _r, _c, _s) in p.spawns}
    for ln in ctx['spawns']:
        f = ln.split(';')
        if not ln.startswith('#') and len(f) == 7 and (int(f[1]), int(f[2]), int(f[3])) in keys:
            ln += ';stado'
        tagged.append(ln)
    ctx['spawns'] = tagged
    floors = {}
    for z in range(-7, 8):
        floors[z] = sum(c for (_m, _x, _y, zz, _r, c, _s) in p.spawns if zz == z)
    entry_up = W(*up_at, 0)
    entry_down = W(*down_at, 0)
    deep_up = W(*deep[-1][0], -1)
    info.update(entry_up=entry_up, entry_down=entry_down, deep_first=deep_up, plan_cells=n, floors=floors)
    return dict(kapitan=info['captain'], wejscie_stozek=entry_up, wejscie_trzewia=entry_down, trzewia_minus1=deep_up,
                potwory_wg_pietra=floors)


# ====================================================================== pomosty rybackie w delcie (na koncu build)
def delta_dress(ctx):
    """Piaszczyste brzegi, trzciny i lachy piasku w delcie (przed drogami - drogi i mosty kladzione pozniej)."""
    world = ctx['world']
    T = ctx['T']
    rng = random.Random(SEED + 3)
    x0, y0, x1, y1 = DELTA_BOX
    n_sand = n_reed = 0
    for (x, y) in getattr(T, 'delta_sandbars', []):
        world.put(x, y, 0, ('sand', 'shells' if rng.random() < 0.15 else NONE, NONE, NONE))
        n_sand += 1
    rx0, ry0, rx1, ry1 = RIVER_BOX
    for y in range(ry0 + 60, ry1 + 1):
        for x in range(rx0, rx1 + 1):
            if in_zone(x, y, 2) or ctx['footprint'][y, x]:
                continue
            c = world.get(x, y)
            if c is None or c[0] in ('water', 'deep_water', 'lava') or c[3] != NONE:
                continue
            near = min((abs(a) + abs(b) for a in range(-3, 4) for b in range(-3, 4)
                        if T.river[y + b, x + a] and world.get(x + a, y + b)[0] == 'water'), default=99)
            in_delta = x0 <= x <= x1 and y0 <= y <= y1
            if near == 1 and (in_delta or rng.random() < 0.5):
                world.put(x, y, 0, ('sand', 'shells' if rng.random() < 0.04 else NONE, NONE, NONE))
                n_sand += 1
            elif near <= 3 and c[0] in ('grass', 'jungle_grass') and rng.random() < (0.45 if in_delta else 0.2):
                world.put(x, y, 0, (c[0], 'reeds', NONE if rng.random() < 0.85 else c[2], NONE))
                n_reed += 1
    return dict(piasek=n_sand, trzciny=n_reed)


def delta_docks(ctx, reach):
    """Trzy pomosty rybackie z sieciami na brzegach delty osiagalnych pieszo; region delty (lowisko)."""
    world = ctx['world']
    T = ctx['T']
    x0, y0, x1, y1 = DELTA_BOX
    rng = random.Random(SEED + 4)
    done = []
    cands = []
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if in_zone(x, y, 4) or not reach[y, x]:
                continue
            c = world.get(x, y)
            if c is None or not F.walk(c) or c[2] != NONE or c[0] in F.ROAD_GROUND:
                continue
            for d in (F.N, F.S_, F.E, F.W):
                run = [world.get(x + d[0] * k, y + d[1] * k) for k in range(1, 7)]
                side = [world.get(x + d[0] * k + abs(d[1]), y + d[1] * k + abs(d[0])) for k in range(1, 5)]
                if all(r is not None and r[0] == 'water' and r[2] == NONE for r in run + side) \
                        and T.river[y + d[1], x + d[0]]:
                    back = world.get(x - d[0], y - d[1])
                    if back is not None and F.walk(back) and reach[y - d[1], x - d[0]]:
                        cands.append((x, y, d))
    rng.shuffle(cands)
    for (x, y, d) in cands:
        if len(done) >= 3:
            break
        if any(abs(x - a) + abs(y - b) < 22 for (a, b, _d) in done):
            continue
        r = F.small_dock(world, (x - d[0], y - d[1]), d, pier=3, boat=len(done) == 1)
        if r is None:
            continue
        spot, _rect = r
        # siec rybacka i beczka na brzegu obok pomostu
        for (a, b) in ((x - d[0] * 2 + abs(d[1]) * 2, y - d[1] * 2 + abs(d[0]) * 2),
                       (x - d[0] * 2 - abs(d[1]) * 2, y - d[1] * 2 - abs(d[0]) * 2)):
            c = world.get(a, b)
            if c is not None and F.walk(c) and c[2] == NONE and c[0] not in F.ROAD_GROUND and reach[b, a]:
                world.put(a, b, 0, (c[0], NONE, 'fishing_net' if not done or len(done) == 2 else 'barrel', NONE))
                break
        done.append((x, y, d))
    ctx.setdefault('regions_new', []).insert(0, (DELTA_REGION, x0, y0, x1, y1, 0, 0, 10, 60))
    return dict(pomosty=[(x, y) for (x, y, _d) in done])
