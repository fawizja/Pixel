"""Kaplice blogoslawienstw (Darek 2026-11-12): "blesy kupuje sie u 6 npc obok miast w malych swiatyniach; 3 na facc,
nastepne 3 na pacc".

Szesc malych kaplic z jednym mnichem w kazdej - jak oddalone swiatynie z Tibii, w ktorych monk sprzedaje pojedyncze
blogoslawienstwo (``Blessing.sellerKey`` w grze, klucze ``<miasto>_blesser``):

  kontynent glowny (konto darmowe)  Varden    - Duchowa Tarcza
                                    Frostholm - Objecia Swiata
                                    Qasirah   - Ogien Slonc
  ziemie premium (konto premium)    Valmir    - Iskra Feniksa
                                    Selvaran  - Madrosc Samotnosci
                                    Nerys     - Zrzadzenie losu

Kaplica stoi 18-45 pol od swiatyni miasta na tym samym ladzie (osiagalna pieszo ze swiatyni, wiec ziemie premium nadal
sa odciete od kontynentu glownego - nic tu nie buduje mostow ani przejsc). Miejsce: nieregularna polana (plama z
falujacym brzegiem) na terenie bez wody, drog, zabudowy, domow na sprzedaz i stref ochronnych, najchetniej przy drodze,
w innym kierunku od miasta dla kazdej kaplicy. Na polanie: murowana kaplica z apsyda (oltarz, swiece, lawki, dywan, sztandary
i pulpity), drzwiami skierowanymi ku drodze albo miastu, brukowany dziedziniec ze zwieszonymi latarniami, sciezka do
najblizszej drogi, krzewy i kwiaty rozrzucone w nieladzie. Mnich stoi przed oltarzem, a kaplica jest strefa ochronna.
Spawny potworow, ktorych srodek wypada na polanie albo tuz obok, sa usuwane. Kolejnosc i dane sa stale - generator daje te
same wspolrzedne przy kazdym uruchomieniu.
"""
import math
import random
from collections import deque

import camps
import features as F

NONE = '-'
NATURAL_GROUND = {'grass', 'sand', 'snow', 'jungle_grass', 'dark_sand'}
OBSTACLES = {'tree', 'pine', 'palm', 'bush', 'jungle_tree', 'jungle_bush', 'frost_pine', 'frost_bush', 'dry_bush', 'autumn_tree',
             'dead_tree', 'cactus', 'rock', 'snow_rock', 'sandstone_rock', 'tall_grass'}
REACH = 90               # promien poszukiwan od swiatyni (kroki BFS po polach przechodnich)
MIN_TEMPLE = 18          # Chebyshev od swiatyni miasta
MAX_TEMPLE = 46
GAP_NPC = 12             # od innych NPC i budynkow (Chebyshev)

# miasto: (klucz, imie mnicha, powitanie, kierunek w stopniach (0 = wschod, 90 = poludnie), odleglosc docelowa, mur, podloga, biom)
SPECS = [
    ('Varden', 'varden_blesser', 'Brat Anzelm',
     'Pokój z tobą, wędrowcze. Jestem Brat Anzelm z kaplicy pod Varden. Dam ci Duchową Tarczę. Powiedz: błogosławieństwo.',
     200, 26, 'whitewash_wall', 'marble_floor'),
    ('Frostholm', 'frost_blesser', 'Siostra Brygida',
     'Mróz nie ma władzy w tej kaplicy, wędrowcze. Jestem Siostra Brygida i dam ci Objęcia Świata. Powiedz: błogosławieństwo.',
     300, 27, 'stone_wall', 'stone_floor'),
    ('Qasirah', 'qas_blesser', 'Brat Hamid',
     'Niech piasek cię oszczędzi, wędrowcze. Jestem Brat Hamid i dam ci Ogień Słońc. Powiedz: błogosławieństwo.',
     60, 28, 'sandstone_wall', 'sandstone_floor'),
    ('Valmir', 'valmir_blesser', 'Siostra Marta',
     'Morze i ogień chronią tych, którzy wierzą. Jestem Siostra Marta i dam ci Iskrę Feniksa (konto premium). Powiedz: błogosławieństwo.',
     130, 26, 'whitewash_wall', 'marble_floor'),
    ('Selvaran', 'selv_blesser', 'Brat Cyprian',
     'Cisza dżungli uczy mądrości. Jestem Brat Cyprian i dam ci Mądrość Samotności (konto premium). Powiedz: błogosławieństwo.',
     20, 40, 'plaster_wall', 'stone_floor'),
    ('Nerys', 'nerys_blesser', 'Siostra Fortunata',
     'Los bywa łaskawy dla ostrożnych. Jestem Siostra Fortunata i dam ci Zrządzenie losu (konto premium). Powiedz: błogosławieństwo.',
     250, 26, 'whitewash_wall', 'marble_floor'),
]
COLOR = '#8a6a4a'

# szablon kaplicy zwroconej drzwiami na poludnie (x 0..6, y 0..7), znaki: W mur, f podloga, a oltarz, c swieca, b lawka,
# n sztandar, l pulpit, D drzwi, . puste (poza budynkiem)
TEMPLATE = [
    "..WWW..",
    "..WaW..",
    "WWWfWWW",
    "WlffflW",
    "WbfffbW",
    "WcfffcW",
    "WnfffnW",
    "WWWDWWW",
]
NPC_AT = (3, 3)          # mnich: przed oltarzem, na dywanie
TALK_AT = (3, 4)         # tu staje rozmowca


def _rot(x, y, k, w=7, h=8):
    """Obrot punktu szablonu o k * 90 stopni zgodnie z ruchem wskazowek zegara; zwraca (x, y) w nowym prostokacie."""
    for _ in range(k % 4):
        x, y, w, h = h - 1 - y, x, h, w
    return x, y


def _dims(k):
    return (8, 7) if k % 2 else (7, 8)


def _reachable(world, start):
    seen = {start}
    q = deque([(start, 0)])
    while q:
        (x, y), d = q.popleft()
        if d >= REACH:
            continue
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n in seen or not F.walk(world.get(*n)):
                continue
            seen.add(n)
            q.append((n, d + 1))
    return seen


def _cells_of(houses):
    out = set()
    for h in houses:
        rects = [tuple(int(v) for v in h[3:7])]
        if len(h) > 11 and h[11]:
            rects += [tuple(int(v) for v in r.split(',')) for r in str(h[11]).split('/')]
        for (x0, y0, x1, y1) in rects:
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    out.add((x, y))
    return out


def _road_distance(world, centre, radius):
    """Odleglosc (kroki, do 14) od najblizszej drogi (dirt/bruk) w oknie wokol centre."""
    g = world.floors[0]
    cx, cy = centre
    dist = {}
    q = deque()
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            c = g[y][x]
            if c is not None and c[0] in ('dirt', 'cobblestone', 'paving', 'brick_paving') and c[3] == NONE and c[2] == NONE:
                dist[(x, y)] = 0
                q.append((x, y))
    while q:
        x, y = q.popleft()
        d = dist[(x, y)]
        if d >= 14:
            continue
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n not in dist and cx - radius <= n[0] <= cx + radius and cy - radius <= n[1] <= cy + radius:
                dist[n] = d + 1
                q.append(n)
    return dist


def _find_site(world, ctx, temple, bearing, target, rng, avoid):
    """Srodek polany: najlepszy wg odleglosci, kierunku i bliskosci drogi. Zwraca (cx, cy, cells) albo None."""
    g = world.floors[0]
    S = len(g)
    reach = _reachable(world, temple)
    houses = _cells_of(ctx['houses'])
    pz = [r for r in ctx['pz'] if r[4] == 0]
    roads = _road_distance(world, temple, 70)
    shape = camps.blob(rng, 5.6, 4.8)
    inner = camps.dilate(shape, 1)
    big = camps.dilate(shape, 3)
    sample = sorted(shape)[::5]
    ext = max(max(abs(x) for x, _ in big), max(abs(y) for _, y in big))
    best = None
    tx, ty = temple
    for cy in range(max(ext + 2, ty - MAX_TEMPLE), min(S - ext - 2, ty + MAX_TEMPLE) + 1, 2):
        for cx in range(max(ext + 2, tx - MAX_TEMPLE), min(S - ext - 2, tx + MAX_TEMPLE) + 1, 2):
            if (cx, cy) not in reach:
                continue
            d = max(abs(cx - tx), abs(cy - ty))
            if d < MIN_TEMPLE or d > MAX_TEMPLE:
                continue
            if any(max(abs(cx - ax), abs(cy - ay)) < GAP_NPC for (ax, ay) in avoid):
                continue
            ok = True
            for (ox, oy) in big:
                x, y = cx + ox, cy + oy
                c = g[y][x]
                if c is None or c[0] not in NATURAL_GROUND or c[3] != NONE or (c[2] != NONE and c[2] not in OBSTACLES) \
                        or (x, y) in houses \
                        or any(a - 3 <= x <= b + 3 and e - 3 <= y <= f + 3 for (a, e, b, f, _) in pz):
                    ok = False
                    break
                if (ox, oy) in inner and c[0] in ('dirt',):
                    ok = False
                    break
            if not ok:
                continue
            ang = math.degrees(math.atan2(cy - ty, cx - tx)) % 360
            dang = abs((ang - bearing + 180) % 360 - 180)
            rd = min(roads.get((cx + ox, cy + oy), 14) for (ox, oy) in sample)
            score = abs(d - target) * 1.0 + dang * 0.12 + rd * 0.35
            if best is None or score < best[0]:
                best = (score, cx, cy)
    if best is None:
        return None
    return best[1], best[2], {(best[1] + ox, best[2] + oy) for (ox, oy) in shape}


def _path(world, start, goal_set, blocked, limit=40):
    """Najkrotsza sciezka po wolnych polach natury od start do pola z goal_set (BFS); lista pol albo []."""
    prev = {start: None}
    q = deque([start])
    while q:
        cur = q.popleft()
        if cur in goal_set and cur != start:
            out = []
            while cur is not None:
                out.append(cur)
                cur = prev[cur]
            return out[::-1]
        for dx, dy in F.DIRS4:
            n = (cur[0] + dx, cur[1] + dy)
            if n in prev or n in blocked:
                continue
            if abs(n[0] - start[0]) > limit or abs(n[1] - start[1]) > limit:
                continue
            c = world.get(*n)
            if c is None or not F.walk(c) or c[0] not in NATURAL_GROUND | {'dirt', 'cobblestone', 'paving', 'brick_paving'}:
                continue
            if c[2] != NONE and c[2] not in OBSTACLES:
                continue
            prev[n] = cur
            q.append(n)
    return []


def _build(world, cx, cy, cells, spec, temple, rng):
    """Stawia polane i kaplice; zwraca (rect budynku, pole mnicha, pole rozmowcy, drzwi)."""
    g = world.floors[0]
    key, wall, floor = spec[1], spec[6], spec[7]
    ground = g[cy][cx][0]
    # drzwi ku miastu: obrot szablonu tak, by drzwi (poludnie) patrzyly w strone swiatyni
    ang = math.degrees(math.atan2(temple[1] - cy, temple[0] - cx)) % 360   # 90 = poludnie
    k = int(round(((ang - 90) % 360) / 90.0)) % 4
    w, h = _dims(k)
    bx, by = cx - w // 2, cy - h // 2
    cellmap = {}
    for ty, row in enumerate(TEMPLATE):
        for tx, ch in enumerate(row):
            if ch == '.':
                continue
            rx, ry = _rot(tx, ty, k)
            cellmap[(bx + rx, by + ry)] = ch
    # polana: drzewa i krzewy znikaja w pasie 2 pol dookola, trawa z kwiatami
    for (x, y) in camps.dilate(cells, 2):
        c = g[y][x]
        if c[2] in OBSTACLES and c[2] not in ('rock',):
            g[y][x] = (c[0], c[1], NONE, c[3])
    for (x, y) in cells:
        c = g[y][x]
        deco = 'flowers' if rng.random() < 0.10 else (c[1] if c[1] in ('tall_grass',) and rng.random() < 0.5 else NONE)
        g[y][x] = (c[0], deco, NONE, NONE)
    door = None
    for (x, y), ch in cellmap.items():
        if ch == 'W':
            g[y][x] = (floor, NONE, NONE, wall)
        elif ch == 'D':
            g[y][x] = (floor, NONE, 'door_open', NONE)
            door = (x, y)
        else:
            obj = {'a': 'altar', 'c': 'candle', 'b': 'bench', 'n': 'banner', 'l': 'lectern', 'f': NONE}[ch]
            g[y][x] = (floor, NONE, obj, NONE)
    # dywan od drzwi do oltarza (deco carpet na srodkowej kolumnie szablonu)
    for ty in range(2, 8):
        rx, ry = _rot(3, ty, k)
        c = g[by + ry][bx + rx]
        if c[2] in (NONE,) and c[3] == NONE:
            g[by + ry][bx + rx] = (floor, 'carpet', NONE, NONE)
    npc = (bx + _rot(*NPC_AT, k)[0], by + _rot(*NPC_AT, k)[1])
    talk = (bx + _rot(*TALK_AT, k)[0], by + _rot(*TALK_AT, k)[1])
    rect = (bx, by, bx + w - 1, by + h - 1)
    # dziedziniec przed drzwiami: bruk 5 x 3 i dwie latarnie
    dx, dy = {0: (0, 1), 1: (-1, 0), 2: (0, -1), 3: (1, 0)}[k]
    px, py = -dy, dx
    court = set()
    for a in range(1, 4):
        for b in range(-2, 3):
            q = (door[0] + dx * a + px * b, door[1] + dy * a + py * b)
            if q in cells and g[q[1]][q[0]][2] == NONE:
                court.add(q)
                g[q[1]][q[0]] = ('paving' if ground != 'sand' else 'sandstone_floor', NONE, NONE, NONE)
    for b in (-2, 2):
        q = (door[0] + dx + px * b, door[1] + dy + py * b)
        if q in court:
            g[q[1]][q[0]] = (g[q[1]][q[0]][0], NONE, 'street_lamp', NONE)
    return rect, npc, talk, door, court, (dx, dy)


def _decorate(world, cells, rect, court, door, out_dir, rng):
    """Krzewy na brzegu polany, kwiaty, lawka przy wejsciu i sciezka z dziedzinca w strone drogi."""
    g = world.floors[0]
    x0, y0, x1, y1 = rect
    ring = sorted(camps.ring_of(cells))
    rng.shuffle(ring)
    bush = {'sand': 'dry_bush', 'dark_sand': 'dry_bush', 'snow': 'frost_bush', 'jungle_grass': 'jungle_bush'}
    n = 0
    for (x, y) in ring:
        c = g[y][x]
        if n >= 9 or c[2] != NONE or c[3] != NONE or (x0 - 2 <= x <= x1 + 2 and y0 - 2 <= y <= y1 + 2) or (x, y) in court:
            continue
        if any(abs(x - px) + abs(y - py) < 3 for (px, py) in (door,)):
            continue
        g[y][x] = (c[0], c[1], bush.get(c[0], 'bush') if rng.random() < 0.8 else 'rock', NONE)
        n += 1
    # lawka z boku dziedzinca
    dx, dy = out_dir
    px, py = -dy, dx
    q = (door[0] + dx * 2 + px * 4, door[1] + dy * 2 + py * 4)
    if q in cells and g[q[1]][q[0]][2] == NONE and g[q[1]][q[0]][3] == NONE:
        g[q[1]][q[0]] = (g[q[1]][q[0]][0], NONE, 'bench', NONE)


def place(ctx):
    """Dodaje szesc kaplic (budynki, polany, mnisi w ctx['npcs'], strefy ochronne). Zwraca wiersze NPC."""
    world = ctx['world']
    towns = dict(ctx['towns'])
    added = []
    avoid = [(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0]
    for i, spec in enumerate(SPECS):
        town, key, name, greet, bearing, target = spec[:6]
        if any(n[0] == key for n in ctx['npcs']):
            continue
        temple = tuple(towns[town][:2])
        rng = random.Random(20261112 + i * 101)
        site = _find_site(world, ctx, temple, bearing, target, rng, avoid)
        assert site is not None, f'chapel {town}: no site'
        cx, cy, cells = site
        rect, npc, talk, door, court, out_dir = _build(world, cx, cy, cells, spec, temple, rng)
        _decorate(world, cells, rect, court, door, out_dir, rng)
        # sciezka z dziedzinca do najblizszej drogi (tylko dla wygladu: kaplica i tak jest osiagalna z miasta)
        roads = {(x, y) for y in range(cy - 24, cy + 25) for x in range(cx - 24, cx + 25)
                 if world.get(x, y) is not None and world.get(x, y)[0] in ('dirt', 'cobblestone', 'paving', 'brick_paving')
                 and (x, y) not in cells and world.get(x, y)[2] == NONE and world.get(x, y)[3] == NONE}
        start = min(court, key=lambda q: abs(q[0] - door[0]) + abs(q[1] - door[1])) if court else None
        if start:
            blocked = {(x, y) for (x, y) in cells if world.get(x, y)[3] != NONE or world.get(x, y)[2] not in (NONE,)}
            path = _path(world, start, roads, blocked - {start}, 30)
            for (x, y) in path[1:-1]:
                c = world.get(x, y)
                if c[0] in NATURAL_GROUND and c[2] == NONE and c[3] == NONE:
                    world.floors[0][y][x] = ('dirt', NONE, NONE, NONE)
        # strefa ochronna wokol kaplicy; spawny, ktorych srodek wypada na polanie, znikaja
        ctx['pz'].append((rect[0] - 2, rect[1] - 2, rect[2] + 2, rect[3] + 2, 0))
        xs = [q[0] for q in cells]
        ys = [q[1] for q in cells]
        box = (min(xs) - 3, min(ys) - 3, max(xs) + 3, max(ys) + 3)
        before = len(ctx['spawns'])
        ctx['spawns'][:] = [s for s in ctx['spawns'] if not _spawn_in(s, box)]
        row = [key, name, 'BLESSER', town, npc[0], npc[1], 0, NONE, COLOR, greet]
        ctx['npcs'].append(row)
        avoid.append(npc)
        added.append(row + [rect, talk, before - len(ctx['spawns'])])
    return added


def _spawn_in(line, box):
    p = line.split(';')
    try:
        x, y, z = int(p[1]), int(p[2]), int(p[3])
        r = int(float(p[4]))
    except (IndexError, ValueError):
        return False
    return z == 0 and box[0] - r // 2 <= x <= box[2] + r // 2 and box[1] - r // 2 <= y <= box[3] + r // 2
