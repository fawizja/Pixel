"""Wyglad swiata (2026-10-28, Darek): losowe stawy i jeziorka, napadniete i spalone opuszczone wioski,
ozdoby w miastach i towary NPC wylozone na ladach sklepow.

Wszystko pracuje na pietrach World (krotki kafli: ground, deco, obj, wall; NONE = brak).
"""
import math
import random
from collections import deque

import features as F

NONE = '-'
# biom -> (podloze brzegu, ozdoba brzegu, roslina przy brzegu, zamarzniety)
POND_STYLE = {
    'temperate': ('grass', 'reeds', 'bush', False), 'forest': ('grass', 'reeds', 'tree', False),
    'hills': ('grass', 'reeds', 'bush', False), 'jungle': ('jungle_grass', 'reeds', 'jungle_bush', False),
    'swamp': ('jungle_grass', 'reeds', 'jungle_tree', False), 'cursed': ('blighted_grass', 'reeds', 'dead_tree', False),
    'taiga': ('snow', 'snow_tuft', 'frost_bush', True), 'snow': ('snow', 'snow_tuft', 'frost_pine', True),
    'desert': ('sand', 'dry_grass', 'palm', False), 'savanna': ('sand', 'dry_grass', 'palm', False),
}
POND_GROUND = {'grass', 'jungle_grass', 'blighted_grass', 'snow', 'sand', 'dark_sand'}
POND_OBJ = {NONE, 'tree', 'bush', 'pine', 'palm', 'jungle_tree', 'jungle_bush', 'frost_pine', 'frost_bush',
            'autumn_tree', 'dry_bush', 'dead_tree', 'cactus', 'rock', 'snow_rock', 'sandstone_rock'}


def ponds(ctx, count=170, seed=20261028):
    """Stawy i jeziorka w losowych miejscach ladu (bez drog, miast, wejsc i rzek w poblizu). Zamarzniete na
    sniegu (lod, mozna po nim chodzic), oaza z palmami na pustyni. Zwraca liczbe stawow."""
    world, T, foot = ctx['world'], ctx['T'], ctx['footprint']
    g = world.floors[0]
    S = len(g)
    rng = random.Random(seed)
    placed = []
    tries = 0
    while len(placed) < count and tries < count * 60:
        tries += 1
        cx, cy = rng.randint(20, S - 21), rng.randint(20, S - 21)
        biome = T.biome[cy, cx]
        if biome not in POND_STYLE or (biome in ('desert', 'savanna') and rng.random() > 0.25):
            continue
        if any(abs(cx - px) + abs(cy - py) < 34 for px, py in placed):
            continue
        r = rng.uniform(2.2, 6.5)
        m = int(r) + 5
        if foot[cy - m:cy + m + 1, cx - m:cx + m + 1].any() or T.water[cy - m:cy + m + 1, cx - m:cx + m + 1].any():
            continue
        ok = True
        for y in range(cy - m, cy + m + 1):
            for x in range(cx - m, cx + m + 1):
                c = g[y][x]
                if c is None or c[0] not in POND_GROUND or c[2] not in POND_OBJ or c[3] != NONE:
                    ok = False
                    break
            if not ok:
                break
        if not ok:
            continue
        shore, deco, plant, frozen = POND_STYLE[biome]
        phase = [rng.uniform(0, 6.28) for _ in range(3)]
        cells = set()
        for y in range(cy - m + 2, cy + m - 1):
            for x in range(cx - m + 2, cx + m - 1):
                a = math.atan2(y - cy, x - cx)
                rr = r * (1 + 0.22 * math.sin(2 * a + phase[0]) + 0.12 * math.sin(3 * a + phase[1])
                          + 0.08 * math.sin(5 * a + phase[2]))
                if (x - cx) ** 2 + ((y - cy) * 1.15) ** 2 <= rr * rr:
                    cells.add((x, y))
        if len(cells) < 8:
            continue
        for (x, y) in cells:
            g[y][x] = ('ice' if frozen else 'water', NONE, NONE, NONE)
        ring = {(x + dx, y + dy) for (x, y) in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - cells
        for (x, y) in ring:
            v = rng.random()
            g[y][x] = (shore, deco if v < 0.35 else NONE, NONE, NONE)
        outer = {(x + dx, y + dy) for (x, y) in ring for dx in (-1, 0, 1) for dy in (-1, 0, 1)} - ring - cells
        for (x, y) in outer:
            c = g[y][x]
            if c[2] == NONE and rng.random() < 0.18:
                g[y][x] = (c[0], c[1], plant, NONE)
        foot[cy - m:cy + m + 1, cx - m:cx + m + 1] = True   # nic wiecej tu nie stawiamy (wioski, wejscia)
        placed.append((cx, cy))
    ctx['ponds'] = placed
    return len(placed)


# ------------------------------------------------------------------ napadniete wioski

def ravage(world, v, rng, burnt=True):
    """Wioska napadnieta przez potwory: spalona ziemia, zweglone belki i drzewa, gruz, krew i ciala.
    Domy maja juz dziury w murach (features.house ruined); tu dochodzi pozoga i slady walki."""
    g = world.floors[0]
    x0, y0, x1, y1 = v['rect']
    W, H = x1 - x0 + 1, y1 - y0 + 1
    # plamy spalenizny wokol domow (kilka ognisk pozaru)
    fires = [(x0 + rng.randint(2, W - 3), y0 + rng.randint(2, H - 3)) for _ in range(4 if burnt else 2)]
    for (inner, door) in v['houses']:
        if inner:
            fires.append(inner[len(inner) // 2])
    changed = 0
    for y in range(y0 - 3, y1 + 4):
        for x in range(x0 - 3, x1 + 4):
            c = world.get(x, y)
            if c is None or c[3] != NONE or c[0] in ('water', 'lava', 'bridge'):
                continue
            d = min(math.hypot(x - fx, y - fy) for fx, fy in fires)
            heat = max(0.0, 1.0 - d / 4.2) + rng.uniform(-0.25, 0.25)
            ground, deco, obj = c[0], c[1], c[2]
            if heat > 0.55 and ground in ('grass', 'jungle_grass', 'dirt', 'field', 'snow', 'sand', 'blighted_grass',
                                           'wood_floor', 'stone_floor', 'sandstone_floor'):
                ground = 'ash' if ground not in ('wood_floor', 'stone_floor', 'sandstone_floor') or rng.random() < 0.6 else ground
                if deco in ('tall_grass', 'flowers', 'wheat', 'fern', 'snow_tuft', 'dry_grass', NONE):
                    deco = 'debris' if rng.random() < 0.35 else NONE
            if obj in ('tree', 'pine', 'palm', 'jungle_tree', 'autumn_tree', 'frost_pine', 'dead_tree') and heat > 0.2:
                obj = 'burnt_tree'
            elif obj in ('bush', 'jungle_bush', 'frost_bush', 'dry_bush', 'fence', 'hay_bale') and heat > 0.3:
                obj = 'burnt_beam' if rng.random() < 0.4 else NONE
            if (ground, deco, obj) != c[:3]:
                world.put(x, y, 0, (ground, deco, obj, c[3]))
                changed += 1
    # zweglone belki i gruz w domach i przy nich, krew i ciala na placu
    free = [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)
            if F.walk(world.get(x, y)) and world.get(x, y)[2] == NONE]
    rng.shuffle(free)
    keep = set(v.get('keep', ()))
    k = 0
    for (x, y) in free:
        if (x, y) in keep or k >= max(6, len(free) // 9):
            continue
        c = world.get(x, y)
        if not _safe(world, x, y):
            continue
        world.put(x, y, 0, (c[0], c[1], rng.choice(['burnt_beam', 'burnt_beam', 'rubble', 'broken_wall']), NONE))
        k += 1
    for (x, y) in free[-14:]:
        c = world.get(x, y)
        if c[2] == NONE and c[1] == NONE:
            world.put(x, y, 0, (c[0], rng.choice(['blood', 'blood', 'corpse', 'debris', 'bones']), NONE, NONE))
    return changed


def _safe(world, x, y):
    """Obiekt na polu nie zamknie przejscia: wszystkie 8 sasiadow do chodzenia i bez obiektow."""
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx == 0 and dy == 0:
                continue
            c = world.get(x + dx, y + dy)
            if c is None or not F.walk(c) or c[2] != NONE:
                return False
    return True


# ------------------------------------------------------------------ ozdoby w miastach

TOWN_ORNAMENTS = {
    # miasto: (obiekty przy murach domow, ozdoby na trawie)
    'Varden': (['flower_box', 'potted_plant', 'barrel', 'bench', 'crate'], ['flowers']),
    'Frostholm': (['barrel', 'firewood', 'potted_plant', 'snowman', 'crate'], ['snow_tuft']),
    'Qasirah': (['clay_pot', 'potted_plant', 'sack', 'barrel', 'flower_box'], ['dry_shrub']),
    'Selvaran': (['potted_plant', 'flower_box', 'clay_pot', 'barrel'], ['jungle_plant', 'flowers']),
    'Valmir': (['flower_box', 'barrel', 'potted_plant', 'crate', 'fishing_net'], ['flowers']),
    'Nerys': (['potted_plant', 'flower_box', 'clay_pot', 'barrel'], ['flowers', 'jungle_plant']),
}
STREETS = {'cobblestone', 'paving', 'brick_paving', 'sandstone_floor', 'dirt'}


def town_ornaments(ctx, rects, rng, protect=None):
    """Donice, skrzynki z kwiatami, beczki i lawki pod murami domow (od strony ulicy), kwiaty na trawnikach.
    Tylko tam, gdzie obiekt nie zamyka przejscia; protect(x, y) - pola nietykalne (kopia Varden)."""
    world = ctx['world']
    total = 0
    npcs = {(n[4] + dx, n[5] + dy) for n in ctx['npcs'] if n[6] == 0 for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    for name, (x0, y0, x1, y1) in rects.items():
        objs, decos = TOWN_ORNAMENTS.get(name, TOWN_ORNAMENTS['Varden'])
        placed = []
        for y in range(y0 + 1, y1):
            for x in range(x0 + 1, x1):
                if (protect is not None and protect(x, y)) or (x, y) in npcs:
                    continue
                c = world.get(x, y)
                if c is None or c[3] != NONE or c[2] != NONE:
                    continue
                if c[0] in ('grass', 'jungle_grass', 'snow', 'sand') and c[1] == NONE and rng.random() < 0.08:
                    world.put(x, y, 0, (c[0], rng.choice(decos), NONE, NONE))
                    continue
                if c[0] not in STREETS or c[1] != NONE:
                    continue
                # pod murem domu (mur na polnoc albo na poludnie / wschod / zachod), nie przy drzwiach
                walls = [world.get(x + dx, y + dy) for dx, dy in F.DIRS4]
                if not any(w is not None and w[3] != NONE for w in walls):
                    continue
                if any(w is not None and w[2] in ('door_open',) for w in walls):
                    continue
                if any(abs(x - px) + abs(y - py) < 5 for px, py in placed):
                    continue
                if rng.random() > 0.35 or not _side_ok(world, x, y):
                    continue
                world.put(x, y, 0, (c[0], c[1], rng.choice(objs), NONE))
                placed.append((x, y))
        total += len(placed)
    return total


def _side_ok(world, x, y):
    """Obiekt przy murze: pole po drugiej stronie i oba pola wzdluz muru wolne (ulica sie nie zweza do zera),
    zadnych schodow ani drabin obok, a wolne pola wokol zostaja ze soba polaczone (obejscie dookola)."""
    for dy in (-2, -1, 0, 1, 2):
        for dx in (-2, -1, 0, 1, 2):
            c = world.get(x + dx, y + dy)
            if c is not None and c[2] in F.STAIRS:
                return False
    ring = [(dx, dy) for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dx or dy)]
    open_ = {(dx, dy) for dx, dy in ring if world.get(x + dx, y + dy) is not None
             and F.walk(world.get(x + dx, y + dy)) and world.get(x + dx, y + dy)[2] == NONE}
    if open_:
        start = next(iter(sorted(open_)))
        seen, q = {start}, [start]
        while q:
            a, b = q.pop()
            for da, db in F.DIRS4:
                n = (a + da, b + db)
                if n in open_ and n not in seen:
                    seen.add(n)
                    q.append(n)
        if seen != open_:
            return False
    free = 0
    for dx, dy in F.DIRS4:
        c = world.get(x + dx, y + dy)
        if c is not None and F.walk(c) and c[2] == NONE:
            free += 1
    if free < 3:
        return False
    # rog: zadne dwa sasiednie pola po skosie nie moga byc zablokowane razem z murem (przejscie po skosie)
    return all(F.walk(world.get(x + dx, y + dy)) for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1))
               if world.get(x + dx, y + dy) is not None and world.get(x + dx, y + dy)[3] == NONE)


# ------------------------------------------------------------------ towary na ladach sklepow

COUNTERS = {'counter', 'showcase'}


def room_cells(world, start, z, radius=6):
    """Pola pomieszczenia NPC (bez przechodzenia przez mury i drzwi), do radius od NPC."""
    sx, sy = start
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n in seen or abs(n[0] - sx) > radius or abs(n[1] - sy) > radius:
                continue
            c = world.get(n[0], n[1], z)
            if c is None or c[3] != NONE or c[2] in ('door_open',):
                continue
            seen.add(n)
            q.append(n)
    return seen


def shop_displays(ctx, shops, protect=None):
    """Lady przed kupcami z towarami wylozonymi do ogladania (Darek 2026-10-28: "w sklepach na ladach,
    niedostepne dla gracza, tylko zeby widzial przedmioty, ktore sprzedaje NPC"). Brakujace lady stawia przy
    NPC (zostawia mu wolne pole do rozmowy). Zwraca liste (x, y, z, przedmiot)."""
    world = ctx['world']
    out = []
    taken = {(n[4], n[5], n[6]) for n in ctx['npcs']}
    for n in ctx['npcs']:
        key, shop, x, y, z = n[0], n[7], n[4], n[5], n[6]
        items = shops.get(shop)
        if not items:
            continue
        room = room_cells(world, (x, y), z)
        counters = sorted(p for p in room if world.get(p[0], p[1], z)[2] in COUNTERS
                          and abs(p[0] - x) <= 3 and abs(p[1] - y) <= 3)
        if len(counters) < 3:
            counters += place_counters(world, (x, y), z, room, taken, protect, 3 - len(counters))
        counters.sort(key=lambda p: (abs(p[0] - x) + abs(p[1] - y), p))
        for i, (cx, cy) in enumerate(counters[:4]):
            out.append((cx, cy, z, items[i % len(items)]))
    return out


def place_counters(world, npc, z, room, taken, protect, want):
    """Lada w rzedzie przed NPC (od strony wnetrza): pola obok NPC, ktore nie odcinaja go od drzwi i reszty sali."""
    x, y = npc
    placed = []
    # kierunek do srodka sali
    cx = sum(p[0] for p in room) / len(room)
    cy = sum(p[1] for p in room) / len(room)
    dx, dy = (1 if cx > x else -1, 0) if abs(cx - x) >= abs(cy - y) else (0, 1 if cy > y else -1)
    if dx:
        line = [(x + dx, y + k) for k in (0, -1, 1)]
    else:
        line = [(x + k, y + dy) for k in (0, -1, 1)]
    for (px, py) in line:
        if len(placed) >= want:
            break
        if (px, py) not in room or (px, py, z) in taken or (protect is not None and protect(px, py)):
            continue
        c = world.get(px, py, z)
        if c[2] != NONE or c[1] not in (NONE, 'carpet', 'rug'):
            continue
        world.put(px, py, z, (c[0], c[1], 'counter', NONE))
        if not _still_open(world, npc, z, room):
            world.put(px, py, z, c)
            continue
        placed.append((px, py))
    return placed


def _still_open(world, npc, z, room):
    """Kazde wolne pole sali dalej osiagalne (lada nie zamyka sali), a NPC ma choc jedno wolne pole obok."""
    walk = {p for p in room if F.walk(world.get(p[0], p[1], z)) and world.get(p[0], p[1], z)[2] in (NONE, 'door_open')}
    walk.discard(npc)
    if not walk:
        return False
    start = next(iter(sorted(walk)))
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n in walk and n not in seen:
                seen.add(n)
                q.append(n)
    near = [(npc[0] + dx, npc[1] + dy) for dx, dy in F.DIRS4]
    return seen == walk and any(p in walk for p in near)
