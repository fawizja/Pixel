"""Dopracowanie miast swiata (Frostholm, Selvaran, Qasirah, port, dzielnica krolewska, oboz) po wygenerowaniu:

- wnetrza budynkow wyposazone wg roli NPC (bank, sklep, swiatynia, przewodnik, woznica, kapitan) albo jak dom,
- drewniane budynki dostaja rozne mury (mur pruski, tynk, bielona sciana) - okna rysuje renderer,
- place i ulice w strefach ochronnych: latarnie, lawki, skrzynki z kwiatami, studnie, drzewa w ogrodach.

Kazdy przedmiot stawiany jest tylko tam, gdzie nie odcina przejscia: we wnetrzu po sprawdzeniu osiagalnosci od
drzwi, na zewnatrz tylko na polu, ktorego 8 sasiadow jest wolnych. Region Varden (kopia maps/varden) jest pomijany.
"""
import random
from collections import deque

import town as tk

INTERIOR = {'wood_floor', 'stone_floor', 'sandstone_floor', 'frozen_floor', 'marble_floor'}
OUTDOOR = {'cobblestone', 'paving', 'brick_paving', 'sand', 'snow', 'grass',
           'jungle_grass', 'dirt'}
FURNITURE = {
    'BANKER': ['bookcase', 'chest', 'potted_plant', 'banner', 'chest', 'bookcase'],
    'MERCHANT': ['crate', 'barrel', 'showcase', 'crate', 'barrel', 'potted_plant', 'crate'],
    'PRIEST': ['banner', 'potted_plant', 'lectern', 'banner', 'potted_plant'],
    'GUIDE': ['bookcase', 'table', 'chair', 'potted_plant', 'bookcase'],
    'CARRIER': ['hay_bale', 'barrel', 'crate', 'bucket'],
    'CAPTAIN': ['barrel', 'crate', 'fishing_net', 'barrel'],
    None: ['bed', 'wardrobe', 'table', 'chair', 'potted_plant', 'bookcase', 'chest'],
}
# drewniane budynki: mur wg miasta (x, y srodka budynku -> material); piaskowiec i kamien bez zmian
WOOD_WALLS = ['wood_wall', 'timber_wall', 'plaster_wall', 'whitewash_wall']
STREET_PROPS = ['street_lamp', 'bench', 'flower_box', 'street_lamp', 'potted_plant', 'bench']


class Grid:
    """Widok na pietro swiata (lista wierszy znakow) przez legende pelnych kafli."""

    def __init__(self, rows, legend):
        self.rows, self.legend = rows, legend
        self.h, self.w = len(rows), len(rows[0])

    def cell(self, x, y):
        return self.legend.cell(self.rows[y][x])

    def put(self, x, y, cell):
        self.rows[y][x] = self.legend.sym(cell)

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def walk(self, x, y):
        return self.inside(x, y) and tk.walkable(self.cell(x, y))


def buildings(gr, skip):
    """Zamkniete murami wnetrza (jak dachy w Roofs): lista (pola, drzwi)."""
    seen = set()
    out = []
    for y in range(gr.h):
        for x in range(gr.w):
            if (x, y) in seen or skip(x, y):
                continue
            c = gr.cell(x, y)
            if c[0] not in INTERIOR or c[3] != '-' or c[2] == 'door_open':
                continue
            cells, doors, enclosed = [], set(), True
            q = deque([(x, y)])
            seen.add((x, y))
            while q:
                cx, cy = q.popleft()
                cells.append((cx, cy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if not gr.inside(nx, ny):
                        enclosed = False
                        continue
                    n = gr.cell(nx, ny)
                    if n[3] != '-':
                        continue
                    if n[2] == 'door_open':
                        doors.add((nx, ny))
                        continue
                    if n[0] not in INTERIOR:
                        enclosed = False
                        continue
                    if (nx, ny) not in seen:
                        seen.add((nx, ny))
                        q.append((nx, ny))
            if enclosed and doors and len(cells) <= 400:
                out.append((cells, doors))
    return out


def reach_all(gr, cells, doors, must):
    """Czy od drzwi da sie dojsc do kazdego wolnego pola wnetrza i do NPC."""
    area = set(cells)
    free = {p for p in area if gr.walk(*p)}
    free |= set(doors)
    start = min(doors)
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n in free and n not in seen:
                seen.add(n)
                q.append(n)
    return free <= seen and all(m in seen for m in must)


def furnish(gr, cells, doors, role, rng, avoid):
    area = set(cells)
    objects = sum(1 for p in cells if gr.cell(*p)[2] != '-')
    want = max(0, len(cells) // 7 - objects)
    if want == 0:
        return 0
    near_door = {(x + dx, y + dy) for (x, y) in doors for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
    cand = [p for p in cells if gr.cell(*p)[2] == '-' and p not in near_door and p not in avoid
            and any(gr.cell(p[0] + dx, p[1] + dy)[3] != '-' for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    rng.shuffle(cand)
    items = FURNITURE.get(role, FURNITURE[None])
    must = [p for p in avoid if p in area]
    placed = 0
    for p in cand:
        if placed >= want:
            break
        old = gr.cell(*p)
        obj = items[placed % len(items)]
        gr.put(*p, (old[0], old[1], obj, '-'))
        if reach_all(gr, cells, doors, must):
            placed += 1
        else:
            gr.put(*p, old)
    return placed


def rewall(gr, cells, material):
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                c = gr.cell(nx, ny)
                if c[3] == 'wood_wall':
                    gr.put(nx, ny, (c[0], c[1], c[2], material))


def street_props(gr, rects, rng, avoid, skip):
    placed = []
    for (x0, y0, x1, y1) in rects:
        spots = [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)]
        rng.shuffle(spots)
        budget = max(1, (x1 - x0 + 1) * (y1 - y0 + 1) // 45)
        n = 0
        for (x, y) in spots:
            if n >= budget:
                break
            if skip(x, y) or not gr.inside(x, y):
                continue
            c = gr.cell(x, y)
            if c[0] not in OUTDOOR or c[2] != '-' or c[3] != '-' or c[0] in INTERIOR and c[0] != 'stone_floor':
                continue
            if any(abs(x - px) < 4 and abs(y - py) < 4 for (px, py) in placed):
                continue
            if any(abs(x - ax) <= 2 and abs(y - ay) <= 2 for (ax, ay) in avoid):
                continue
            ok = True
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    nb = gr.cell(x + dx, y + dy) if gr.inside(x + dx, y + dy) else None
                    if nb is None or not tk.walkable(nb) or nb[2] not in ('-',) or nb[0] in INTERIOR - {'stone_floor'}:
                        ok = False
            if not ok:
                continue
            if c[0] in ('grass', 'jungle_grass'):
                obj = 'tree' if rng.random() < 0.6 else 'bush'
            elif c[0] in ('snow',):
                obj = 'pine' if rng.random() < 0.5 else 'street_lamp'
            else:
                obj = STREET_PROPS[n % len(STREET_PROPS)]
            gr.put(x, y, (c[0], '-', obj, '-'))
            placed.append((x, y))
            n += 1
    return placed


def fountains(gr, skip):
    """Fontanna z 4 jednakowych kafli (2x2) wyglada jak 4 kregi: zostaje jedna, wokol plyty placu."""
    n = 0
    for y in range(gr.h - 1):
        for x in range(gr.w - 1):
            if skip(x, y) or any(gr.cell(x + dx, y + dy)[2] != 'fountain' for dx in (0, 1) for dy in (0, 1)):
                continue
            for dy in range(-1, 3):
                for dx in range(-1, 3):
                    c = gr.cell(x + dx, y + dy)
                    if c[3] == '-' and c[0] in ('cobblestone', 'sand', 'snow', 'dirt', 'sandstone_floor'):
                        obj = c[2] if c[2] not in ('fountain',) else '-'
                        gr.put(x + dx, y + dy, ('paving', c[1], obj, '-'))
            for dx, dy in ((1, 0), (0, 1), (1, 1)):
                c = gr.cell(x + dx, y + dy)
                gr.put(x + dx, y + dy, (c[0], c[1], '-', '-'))
            gr.put(x, y, ('paving', '-', 'fountain', '-'))
            n += 1
    return n


def dress(rows, legend, pz_rects, npcs, transitions, skip, houses, keep=(), seed=20261023):
    """rows: pietro 0 (lista list znakow, zmieniana w miejscu); npcs: [(x, y, rola)];
    houses: wnetrza domow na sprzedaz (zostaja puste); skip(x, y): pola do pominiecia (Varden)."""
    rng = random.Random(seed)
    gr = Grid(rows, legend)
    npc_at = {(x, y): role for (x, y, role) in npcs}
    avoid = set(npc_at) | {(t[0], t[1]) for t in transitions if t[2] == 0} | set(keep)
    stats = {'furniture': 0, 'rewalled': 0, 'street': 0, 'fountains': fountains(gr, skip)}
    for cells, doors in buildings(gr, skip):
        area = set(cells)
        if any(area & h for h in houses):
            continue
        roles = [npc_at[p] for p in cells if p in npc_at]
        role = roles[0] if roles else None
        stats['furniture'] += furnish(gr, cells, doors, role, rng, avoid)
        mx = sum(x for x, _ in cells) // len(cells)
        my = sum(y for _, y in cells) // len(cells)
        material = WOOD_WALLS[(mx * 7 + my * 13) % len(WOOD_WALLS)]
        if material != 'wood_wall':
            rewall(gr, cells, material)
            stats['rewalled'] += 1
    stats['street'] = len(street_props(gr, pz_rects, rng, avoid, skip))
    return stats


def house_cells(houses):
    out = []
    for h in houses:
        _id, _n, _t, x0, y0, x1, y1, z = h[:8]
        if int(z) == 0:
            out.append({(x, y) for y in range(int(y0), int(y1) + 1) for x in range(int(x0), int(x1) + 1)})
    return out
