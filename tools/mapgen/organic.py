"""Miasta o nieregularnym ksztalcie (2026-10-28, Darek: "miasta nie takie same kwadratowe, losowe rozne ksztalty,
ulozenie sklepow i budynkow").

Miasto powstaje tak:
1. Obrys: owal z falujacym brzegiem (kilka sinusow o losowych fazach) - mur miejski idzie po tym brzegu, bramy
   tam, gdzie wychodza drogi.
2. Plac w poblizu srodka (tez nieregularny) z fontanna i latarniami.
3. Ulice glowne kreta sciezka (A* z szumem) od placu do kazdej bramy, szerokosci 3; kilka ulic bocznych
   (szerokosci 2) od ulic glownych w glab kwartalow.
4. Budynki rosnace przy ulicach: losowy rozmiar, losowe przesuniecie wzdluz ulicy, drzwi zawsze od ulicy,
   czesc domow w ksztalcie litery L. Miedzy budynkami zostaja zaulki. Najpierw swiatynia (przy placu), potem
   budynki NPC, a reszta to domy na sprzedaz.
5. Sklep ma prawdziwa lade: kupiec stoi za rzedem lad przez cala szerokosc sali (z przejsciem przy scianie),
   na polkach za nim towary; gracz rozmawia przez lade.
Wszystko zalezy tylko od ziarna, wiec mapa jest powtarzalna.
"""
import heapq
import math
from collections import deque

import features as F

NONE = '-'
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
DIRS8 = DIRS4 + ((1, 1), (1, -1), (-1, 1), (-1, -1))
SIDES = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}
HOME_FURNITURE = ['bed', 'table', 'chair', 'chest', 'wardrobe', 'barrel', 'bookcase', 'potted_plant', 'crate']
# role, ktore stoja za lada (sprzedaja i kupuja)
COUNTER_ROLES = {'MERCHANT', 'BANKER'}
# polki za lada wg rodzaju sklepu (klucz roli w kluczu NPC)
SHELVES = {
    'smith': ['anvil', 'forge', 'showcase'],
    'mage': ['bookcase', 'showcase', 'bookcase'],
    'innkeeper': ['barrel', 'barrel', 'crate'],
    'banker': ['depot_locker', 'depot_locker', 'chest'],
}
SHOP_EXTRA = {
    'innkeeper': ['table', 'chair', 'table', 'chair'],
    'smith': ['barrel', 'crate'],
    'mage': ['potted_plant'],
    'merchant': ['crate', 'sack', 'barrel'],
    'banker': ['potted_plant', 'depot_locker'],
}
OFFICE = {
    'housing': ['bookcase', 'table', 'chair', 'lectern', 'potted_plant'],
    'guide': ['table', 'chair', 'bookcase', 'banner'],
    'carrier': ['hay_bale', 'hay_bale', 'barrel', 'crate'],
}


class Noise:
    """Gladki szum 0..1 na siatce co `cell` pol (interpolacja z wygladzeniem)."""

    def __init__(self, rng, x0, y0, w, h, cell=6):
        self.x0, self.y0, self.cell = x0, y0, cell
        self.g = [[rng.random() for _ in range(w // cell + 3)] for _ in range(h // cell + 3)]

    def __call__(self, x, y):
        fx, fy = (x - self.x0) / self.cell, (y - self.y0) / self.cell
        ix, iy = max(0, int(fx)), max(0, int(fy))
        ix, iy = min(ix, len(self.g[0]) - 2), min(iy, len(self.g) - 2)
        tx, ty = min(1.0, max(0.0, fx - ix)), min(1.0, max(0.0, fy - iy))
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        g = self.g
        a = g[iy][ix] + (g[iy][ix + 1] - g[iy][ix]) * tx
        b = g[iy + 1][ix] + (g[iy + 1][ix + 1] - g[iy + 1][ix]) * tx
        return a + (b - a) * ty


def outline(cx, cy, rx, ry, rng, bounds=None):
    """Pola miasta: owal o falujacym brzegu (promien 1 +- ~0.25). bounds (x0, y0, x1, y1) przycina."""
    waves = [(rng.choice((2, 3)), 0.15), (rng.choice((4, 5)), 0.08), (rng.choice((6, 7)), 0.04)]
    phase = [rng.uniform(0, 2 * math.pi) for _ in waves]
    cells = set()
    for y in range(cy - int(ry * 1.25) - 1, cy + int(ry * 1.25) + 2):
        for x in range(cx - int(rx * 1.25) - 1, cx + int(rx * 1.25) + 2):
            if bounds and not (bounds[0] <= x <= bounds[2] and bounds[1] <= y <= bounds[3]):
                continue
            dx, dy = (x - cx) / rx, (y - cy) / ry
            th = math.atan2(dy, dx)
            r = 1 + sum(a * math.sin(k * th + p) for (k, a), p in zip(waves, phase))
            if math.hypot(dx, dy) <= r:
                cells.add((x, y))
    return cells


def astar(passable, cost, start, goals):
    """Najtanszaa sciezka 4-kierunkowa od start do dowolnego pola z goals (zbior)."""
    gx = [g[0] for g in goals]
    gy = [g[1] for g in goals]
    single = next(iter(goals)) if len(goals) == 1 else None

    def h(p):
        if single:
            return abs(p[0] - single[0]) + abs(p[1] - single[1])
        return 0 if not gx else max(0, min(abs(p[0] - x) + abs(p[1] - y) for x, y in zip(gx[:40], gy[:40])) - 40)
    best = {start: 0.0}
    came = {}
    q = [(h(start), 0.0, start)]
    while q:
        _f, g, p = heapq.heappop(q)
        if p in goals:
            path = [p]
            while p in came:
                p = came[p]
                path.append(p)
            return path[::-1]
        if g > best.get(p, 1e18):
            continue
        for a, b in DIRS4:
            n = (p[0] + a, p[1] + b)
            if not passable(n):
                continue
            ng = g + cost(n)
            if ng < best.get(n, 1e18):
                best[n] = ng
                came[n] = p
                heapq.heappush(q, (ng + h(n), ng, n))
    return None


class Town:
    """Plan miasta w trakcie budowy (wszystko na pietrze 0)."""

    def __init__(self, world, spec, rng, reserved=()):
        self.world, self.spec, self.rng = world, spec, rng
        cx, cy = spec['centre']
        self.cells = outline(cx, cy, spec['rx'], spec['ry'], rng, spec.get('bounds'))
        for (x, y) in reserved:        # zachowane zejscia zawsze w obrebie murow
            self.cells |= {(x + a, y + b) for a in range(-3, 4) for b in range(-3, 4)}
        self.ring = thin_ring(self.cells)
        self.inside = self.cells - self.ring
        self.core = {p for p in self.inside if all((p[0] + a, p[1] + b) in self.inside for a, b in DIRS8)}
        self.reserved = set(reserved)
        self.street = set()
        self.main = set()
        self.taken = set()          # pola budynkow
        self.buildings = []
        xs = [p[0] for p in self.cells]
        ys = [p[1] for p in self.cells]
        self.rect = (min(xs), min(ys), max(xs), max(ys))
        self.noise = Noise(rng, self.rect[0], self.rect[1], self.rect[2] - self.rect[0] + 1,
                           self.rect[3] - self.rect[1] + 1, cell=5)
        self.gates = {}

    # ------------------------------------------------------------------ teren, mur, bramy
    def lay_ground(self):
        s = self.spec
        for (x, y) in self.cells:
            if (x, y) in self.reserved:
                continue
            if (x, y) in self.ring:
                self.world.put(x, y, 0, (s['ground'], NONE, NONE, s['wall']))
            else:
                self.world.put(x, y, 0, (s['ground'], NONE, NONE, NONE))

    def gate(self, side):
        """Brama na boku side: 3 pola muru zamienione w ulice, droga 2 pola za mur. Zwraca pole wewnatrz."""
        dx, dy = SIDES[side]
        cx, cy = self.spec['centre']
        # brama na wysokosci srodka (wzdluz boku lekko przesunieta)
        span = self.spec['rx'] if dx == 0 else self.spec['ry']
        shift = self.rng.randint(-int(span * 0.3), int(span * 0.3))
        px, py = (cx + shift, cy) if dx == 0 else (cx, cy + shift)
        while (px + dx, py + dy) in self.inside:
            px, py = px + dx, py + dy
        inner = (px, py)
        ox, oy = -dy, dx   # w poprzek bramy
        last = None
        for k in (-1, 0, 1):
            for step in range(8):
                x, y = px + ox * k + dx * step, py + oy * k + dy * step
                if (x, y) not in self.cells:
                    if k == 0:
                        last = (x, y)
                    break
                self.street.add((x, y))
                self.main.add((x, y))
        out = last
        for step in (0, 1):
            for k in (-1, 0, 1):
                x, y = out[0] + dx * step + ox * k, out[1] + dy * step + oy * k
                c = self.world.get(x, y, 0)
                if c is not None and c[0] != 'water':
                    self.world.put(x, y, 0, ('dirt', NONE, NONE, NONE))
        self.gates[side] = out
        return inner

    # ------------------------------------------------------------------ ulice
    def plaza(self):
        cx, cy = self.spec['centre']
        r = self.rng
        px, py = cx + r.randint(-2, 2), cy + r.randint(-2, 2)
        rad = self.spec.get('plaza', 4.5)
        cells = {p for p in self.core if math.hypot(p[0] - px, p[1] - py) + 1.6 * self.noise(*p) < rad + 0.8}
        self.street |= cells
        self.main |= cells
        self.plaza_cells = cells
        self.plaza_centre = (px, py)
        return (px, py)

    def passable(self, p):
        return p in self.inside and p not in self.reserved and p not in self.taken

    def road(self, start, goals, width):
        def cost(p):
            if p in self.street:
                return 0.4
            return 1.0 + 5.0 * self.noise(*p)
        path = astar(self.passable, cost, start, goals)
        assert path is not None, f'{self.spec["name"]}: no street from {start}'
        for (x, y) in path:
            if width == 3:
                brush = [(a, b) for a in (-1, 0, 1) for b in (-1, 0, 1)]
            elif width == 2:
                brush = [(0, 0), (1, 0), (0, 1), (1, 1)]
            else:
                brush = [(0, 0)]
            for a, b in brush:
                q = (x + a, y + b)
                if q in self.inside and q not in self.reserved:
                    self.street.add(q)
                    if width == 3:
                        self.main.add(q)
        return path

    def streets(self, gate_inner, side_count):
        for g in gate_inner:
            # przez punkt posredni odsuniety w bok, zeby ulica glowna kluczyla zamiast biec prosto
            px, py = self.plaza_centre
            mx, my = (g[0] + px) // 2, (g[1] + py) // 2
            span = max(3, int(0.22 * (abs(g[0] - px) + abs(g[1] - py))))
            if abs(g[0] - px) > abs(g[1] - py):
                my += self.rng.choice((-1, 1)) * self.rng.randint(span // 2, span)
            else:
                mx += self.rng.choice((-1, 1)) * self.rng.randint(span // 2, span)
            way = min(self.core, key=lambda p: abs(p[0] - mx) + abs(p[1] - my))
            self.road(g, {way}, 3)
            self.road(way, set(self.plaza_cells), 3)
        # ulice boczne: od pol daleko od ulic do najblizszej ulicy (najpierw najdalsze zakatki, potem losowe)
        for i in range(side_count):
            dist = self.distance(self.street)
            far = [p for p in sorted(self.core) if dist.get(p, 0) >= 6 and p not in self.reserved]
            if i % 2 == 0 and far:
                top = max(dist[p] for p in far)
                far = [p for p in far if dist[p] >= top - 2]
            if not far:
                break
            start = self.rng.choice(far)
            self.road(start, set(self.street), 2)

    def side_street(self):
        """Uliczka (szer. 2) od najdalszego od ulic wolnego miejsca do najblizszej ulicy. False, gdy brak miejsca."""
        dist = self.distance(self.street | self.taken)
        far = [p for p in sorted(self.core) if dist.get(p, 0) >= 5 and p not in self.reserved and p not in self.taken]
        if not far:
            return False
        top = max(dist[p] for p in far)
        start = self.rng.choice([p for p in far if dist[p] >= top - 1])
        self.road(start, set(self.street), 2)
        return True

    def lot_free_for(self, role):
        """Czy przy obecnych ulicach zmiesci sie jeszcze budynek (szybki test na 30 kandydatach)."""
        for (s, d) in self.frontage()[::7][:30]:
            door = (s[0] + d[0], s[1] + d[1])
            perp = (-d[1], d[0])
            shape = {(door[0] + perp[0] * (u - 2) + d[0] * v, door[1] + perp[1] * (u - 2) + d[1] * v)
                     for u in range(7) for v in range(6)}
            if self.fits(shape):
                return True
        return False

    def distance(self, sources):
        d = {p: 0 for p in sources}
        q = deque(sources)
        while q:
            x, y = q.popleft()
            for a, b in DIRS4:
                n = (x + a, y + b)
                if n in self.inside and n not in d:
                    d[n] = d[(x, y)] + 1
                    q.append(n)
        return d

    def link(self, cell):
        """Waska sciezka (ulica) od pola zarezerwowanego (np. zejscie do lochow) do najblizszej ulicy."""
        starts = [(cell[0] + a, cell[1] + b) for a, b in DIRS4 if (cell[0] + a, cell[1] + b) in self.inside
                  and (cell[0] + a, cell[1] + b) not in self.reserved]
        for s in starts:
            if s in self.street:
                return
        for s in starts:
            path = astar(self.passable, lambda p: 0.3 if p in self.street else 1.0 + 3 * self.noise(*p), s,
                         set(self.street))
            if path:
                for p in path:
                    self.street.add(p)
                return

    # ------------------------------------------------------------------ budynki
    def frontage(self):
        """Pary (pole ulicy, kierunek od ulicy) z wolnym polem budowy za ulica."""
        out = []
        for (x, y) in sorted(self.street):
            for a, b in DIRS4:
                n = (x + a, y + b)
                if n in self.core and n not in self.street and n not in self.taken and n not in self.reserved:
                    out.append(((x, y), (a, b)))
        return out

    def fits(self, shape):
        for p in shape:
            if p not in self.core or p in self.street or p in self.taken or p in self.reserved:
                return False
            for a, b in DIRS8:
                n = (p[0] + a, p[1] + b)
                if n in self.taken or n in self.reserved:
                    return False
        return True

    def lot(self, sizes, prefer=None, wing=0.0, tries=None, setback=True):
        """Budynek przy ulicy: (pola, drzwi, kierunek w glab, wymiary). Rozmiary z listy sizes (w, h)."""
        cands = self.frontage()
        self.rng.shuffle(cands)
        if prefer is not None:
            cands.sort(key=lambda c: abs(c[0][0] - prefer[0]) + abs(c[0][1] - prefer[1]))
        for (s, d) in cands[:tries or len(cands)]:
            # czesc budynkow cofnieta od ulicy (sciezka do drzwi), zeby pierzeje nie byly rowne
            back = self.rng.choice((0, 0, 0, 1, 2, 3)) if setback else 0
            path = [(s[0] + d[0] * k, s[1] + d[1] * k) for k in range(1, back + 1)]
            if any(p not in self.inside or p in self.taken or p in self.reserved or p in self.street for p in path):
                continue
            door = (s[0] + d[0] * (back + 1), s[1] + d[1] * (back + 1))
            perp = (-d[1], d[0])
            for (w, h) in self.rng.sample(sizes, len(sizes)):
                off = self.rng.randint(1, w - 2)

                def at(u, v):
                    return (door[0] + perp[0] * (u - off) + d[0] * v, door[1] + perp[1] * (u - off) + d[1] * v)
                shape = {at(u, v) for u in range(w) for v in range(h)}
                if wing and self.rng.random() < wing:
                    ww = self.rng.randint(3, 5)
                    us = range(w - 1, w + ww - 1) if self.rng.random() < 0.5 else range(-ww + 1, 1)
                    shape |= {at(u, v) for u in us for v in range(h // 2, h)}
                if self.fits(shape):
                    self.taken |= shape
                    self.street.update(path)
                    return shape, door, d, (w, h, off, at)
        return None

    def raise_walls(self, shape, door, floor):
        s = self.spec
        wall = self.rng.choice(s.get('bwalls') or [s['wall']])
        inner = []
        for (x, y) in shape:
            if any((x + a, y + b) not in shape for a, b in DIRS8):
                self.world.put(x, y, 0, (floor, NONE, NONE, wall))
            else:
                self.world.put(x, y, 0, (floor, NONE, NONE, NONE))
                inner.append((x, y))
        self.world.put(door[0], door[1], 0, (floor, NONE, 'door_open', NONE))
        return sorted(inner)

    def free(self, p):
        c = self.world.get(p[0], p[1], 0)
        return c is not None and c[3] == NONE and c[2] == NONE

    def connected(self, inner, door, extra_block=()):
        """Wszystkie wolne pola wnetrza osiagalne od drzwi."""
        walk = {p for p in inner if self.free(p) and p not in extra_block}
        start = None
        for a, b in DIRS4:
            n = (door[0] + a, door[1] + b)
            if n in walk:
                start = n
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

    def furnish(self, inner, door, items, floor, keep=()):
        """Meble przy scianach (nie przy drzwiach), tylko tam, gdzie nie zamykaja przejscia."""
        spots = [p for p in inner if abs(p[0] - door[0]) + abs(p[1] - door[1]) > 2 and p not in keep
                 and any(self.world.get(p[0] + a, p[1] + b, 0)[3] != NONE for a, b in DIRS4)]
        self.rng.shuffle(spots)
        k = 0
        for p in spots:
            if k >= len(items):
                break
            if not self.free(p):
                continue
            self.world.put(p[0], p[1], 0, (floor, NONE, items[k], NONE))
            if self.connected(inner, door, keep):
                k += 1
            else:
                self.world.put(p[0], p[1], 0, (floor, NONE, NONE, NONE))
        return k

    # ------------------------------------------------------------------ rodzaje budynkow
    def temple(self, priest, throne=False):
        """Swiatynia z oltarzem i lawkami albo (throne) palac z tronem: krol stoi przed tronem."""
        s = self.spec
        hall = s['hall']
        got = None
        for sizes in ([(13, 11), (13, 10), (11, 11)], [(11, 9), (9, 9)], [(9, 8), (7, 8)]):
            got = self.lot(sizes, prefer=self.plaza_centre, tries=400)
            if got:
                break
        assert got, f'{s["name"]}: no room for the temple'
        shape, door, d, (w, h, off, at) = got
        inner = self.raise_walls(shape, door, hall)
        altar = at(off, h - 2)
        self.world.put(altar[0], altar[1], 0, (hall, 'carpet', 'throne' if throne else 'altar', NONE))
        for v in range(1, h - 2):
            p = at(off, v)
            self.world.put(p[0], p[1], 0, (hall, 'carpet', NONE, NONE))
        for (u, v, item) in ((off - 2, h - 2, 'candle'), (off + 2, h - 2, 'candle'), (1, 1, 'potted_plant'),
                             (w - 2, 1, 'potted_plant'), (1, h - 2, 'candle'), (w - 2, h - 2, 'candle')):
            p = at(u, v)
            if p in inner and self.free(p) and p != at(off + 1, h - 2) and abs(u - off) >= 2:
                self.world.put(p[0], p[1], 0, (hall, NONE, item, NONE))
                if not self.connected(inner, door):
                    self.world.put(p[0], p[1], 0, (hall, NONE, NONE, NONE))
        if throne:
            # sala tronowa: sztandary pod scianami, krol przed tronem
            for v in range(2, h - 2, 3):
                for u in (1, w - 2):
                    p = at(u, v)
                    if self.free(p) and abs(u - off) >= 2:
                        self.world.put(p[0], p[1], 0, (hall, NONE, 'banner', NONE))
                        if not self.connected(inner, door):
                            self.world.put(p[0], p[1], 0, (hall, NONE, NONE, NONE))
            self.npc(priest, at(off, h - 3))
            self.palace_spot = at(off, h - 3)
            self.buildings.append(('palace', shape, door))
            return
        for v in range(3, h - 3, 2):
            for u in range(1, w - 1):
                if abs(u - off) >= 2 and 1 <= u <= w - 2 and self.rng.random() < 0.8:
                    p = at(u, v)
                    if self.free(p) and u not in (1, w - 2):
                        self.world.put(p[0], p[1], 0, (hall, NONE, 'bench', NONE))
        spot = at(off + 1, h - 2)
        if not self.free(spot):
            spot = at(off - 1, h - 2)
        self.npc(priest, spot)
        self.temple_spot = at(off, h - 3)
        self.buildings.append(('temple', shape, door))

    def shop(self, role):
        key = role['kind']
        s = self.spec
        floor = 'stone_floor' if key in ('smith', 'carrier') else s['floor']
        counter = role['role'] in COUNTER_ROLES
        sizes = [(w, h) for w in (7, 8, 9, 10) for h in (6, 7, 8)] if counter else \
            [(w, h) for w in (5, 6, 7, 8) for h in (5, 6, 7)]
        if key == 'innkeeper':
            sizes = [(w, h) for w in (9, 10, 11) for h in (8, 9)]
        got = self.lot(sizes, prefer=self.plaza_centre if role.get('central') else None, tries=600) \
            or self.lot([(7, 6), (6, 6), (5, 6)] if counter else [(5, 5), (6, 5)], tries=None)
        assert got, f'{s["name"]}: no room for {role["key"]}'
        shape, door, d, (w, h, off, at) = got
        inner = self.raise_walls(shape, door, floor)
        back = h - 2
        if counter:
            npc_u = self.rng.randint(2, w - 3) if w > 5 else w // 2
            gap = 1 if npc_u > w // 2 else w - 2           # przejscie za lade przy scianie, z dala od kupca
            for u in range(1, w - 1):
                if u != gap:
                    p = at(u, back - 1)
                    self.world.put(p[0], p[1], 0, (floor, NONE, 'counter', NONE))
            shelves = SHELVES.get(key, ['showcase', 'bookcase', 'barrel'])
            k = 0
            for u in range(1, w - 1):
                if u in (npc_u, gap):
                    continue
                p = at(u, back)
                self.world.put(p[0], p[1], 0, (floor, NONE, shelves[k % len(shelves)], NONE))
                k += 1
            keep = {at(u, back) for u in range(1, w - 1)} | {at(u, back - 1) for u in range(1, w - 1)} | \
                {at(u, back - 2) for u in (npc_u - 1, npc_u, npc_u + 1)}
            self.furnish(inner, door, SHOP_EXTRA.get(key, []), floor, keep=keep)
            self.npc(role, at(npc_u, back))
        else:
            spot = at(off, back)
            items = OFFICE.get(key, ['table', 'chair'])
            if key == 'housing' and s.get('lockers'):
                items = ['depot_locker', 'depot_locker'] + items     # depozyt w dzielnicy bez banku
            self.furnish(inner, door, items, floor, keep={spot})
            self.npc(role, spot)
        self.buildings.append((key, shape, door))

    def house(self, n, label, price_per_cell=5000):
        s = self.spec
        sizes = [(w, h) for w in (5, 6, 7, 8, 9) for h in (5, 6, 7)]
        got = self.lot(sizes, wing=0.4, tries=900)
        if not got:
            return None
        shape, door, d, dims = got
        inner = self.raise_walls(shape, door, s['floor'])
        items = list(HOME_FURNITURE)
        self.rng.shuffle(items)
        self.furnish(inner, door, items[:max(2, len(inner) // 5)], s['floor'])
        self.buildings.append(('house', shape, door))
        rects = rect_cover(set(inner))
        (x0, y0, x1, y1) = rects[0]
        extra = '/'.join(f'{a},{b},{c},{e}' for (a, b, c, e) in rects[1:])
        return [f'{s["key"]}_{n}', f'{label} {n}', s['name'], x0, y0, x1, y1, 0, door[0], door[1],
                len(inner) * price_per_cell] + ([extra] if extra else [])

    def npc(self, role, spot):
        self.npcs.append([role['key'], role['name'], role['role'], self.spec['name'], spot[0], spot[1], 0,
                          role['shop'], role['color'], role['greet']])

    # ------------------------------------------------------------------ wystroj
    def dress(self):
        s, w, rng = self.spec, self.world, self.rng
        for (x, y) in sorted(self.street):
            if (x, y) in self.reserved:
                continue
            c = w.get(x, y, 0)
            w.put(x, y, 0, (s['street'], NONE, NONE, NONE) if c[3] == NONE or (x, y) in self.street else c)
        px, py = self.plaza_centre
        w.put(px, py, 0, (s['street'], NONE, 'fountain', NONE))
        doors = [b[2] for b in self.buildings]
        for (a, b) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            p = (px + a, py + b)
            if p in self.plaza_cells and not near(p, doors, 1):
                w.put(p[0], p[1], 0, (s['street'], NONE, 'street_lamp', NONE))
        # latarnie na skraju ulic glownych co kilkanascie pol
        lamps = [(px, py)]
        for p in sorted(self.main - self.plaza_cells):
            if any((p[0] + a, p[1] + b) not in self.street for a, b in DIRS4) and not near(p, doors, 2) \
                    and all(abs(p[0] - q[0]) + abs(p[1] - q[1]) >= 11 for q in lamps) and self.edge_ok(p):
                w.put(p[0], p[1], 0, (s['street'], NONE, 'street_lamp', NONE))
                lamps.append(p)
        # zielen na podworkach i w zaulkach
        for (x, y) in sorted(self.inside):
            c = w.get(x, y, 0)
            if (x, y) in self.street or (x, y) in self.taken or (x, y) in self.reserved or c[0] != s['ground'] \
                    or c[2] != NONE or c[3] != NONE:
                continue
            r = rng.random()
            free = all(F.walk(w.get(x + a, y + b, 0)) and w.get(x + a, y + b, 0)[2] == NONE
                       and (x + a, y + b) not in self.street for a, b in DIRS8)
            if r < 0.07 and free and not near((x, y), doors, 2):
                w.put(x, y, 0, (s['ground'], NONE, rng.choice(s['trees']), NONE))
            elif r < 0.22:
                w.put(x, y, 0, (s['ground'], rng.choice(s['decos']), NONE, NONE))

    def check(self):
        """Kazda ulica, brama i drzwi osiagalne od swiatyni."""
        area = self.cells | set(self.gates.values())
        seen = _reach(self.world, self.temple_spot, area)
        lost = [p for p in sorted(self.street) if p not in seen and F.walk(self.world.get(p[0], p[1], 0))]
        lost += [b[2] for b in self.buildings if b[2] not in seen]
        assert not lost, f'{self.spec["name"]}: unreachable {lost[:8]} ({len(lost)})'

    def edge_ok(self, p):
        """Latarnia przy krawedzi ulicy nie zweza ulicy do zera: pole po drugiej stronie wolne."""
        for a, b in DIRS4:
            n, o = (p[0] + a, p[1] + b), (p[0] - a, p[1] - b)
            if n not in self.street and o in self.street and (o[0] - a, o[1] - b) in self.street:
                return True
        return False


def _reach(world, start, area):
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for a, b in DIRS4:
            n = (x + a, y + b)
            if n in area and n not in seen and F.walk(world.get(n[0], n[1], 0)):
                seen.add(n)
                q.append(n)
    return seen


def thin_ring(cells):
    """Mur grubosci jednego pola: pola z sasiadem (4) poza miastem, plus pole w rogu kazdego skosnego schodka,
    zeby mur byl ciagly (bez przejscia po skosie i bez przerw w rysunku)."""
    ring = {p for p in cells if any((p[0] + a, p[1] + b) not in cells for a, b in DIRS4)}
    for (x, y) in sorted(ring):
        for a, b in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            if (x + a, y + b) in ring and (x + a, y) not in ring and (x, y + b) not in ring:
                for q in ((x + a, y), (x, y + b)):
                    if q in cells:
                        ring.add(q)
                        break
    return ring


def near(p, points, r):
    return any(abs(p[0] - q[0]) <= r and abs(p[1] - q[1]) <= r for q in points)


def rect_cover(cells):
    """Pokrycie zbioru pol prostokatami (zachlannie, najwieksze najpierw). Pierwszy jest najwiekszy."""
    left = set(cells)
    out = []
    while left:
        best = None
        for (x, y) in sorted(left):
            # najszerszy prostokat zaczynajacy sie w (x, y)
            w = 0
            while (x + w, y) in left:
                w += 1
            ww, h = w, 0
            while True:
                row = 0
                while row < ww and (x + row, y + h) in left:
                    row += 1
                if row == 0:
                    break
                ww = min(ww, row)
                h += 1
                area = ww * h
                if best is None or area > best[0]:
                    best = (area, x, y, x + ww - 1, y + h - 1)
        _a, x0, y0, x1, y1 = best
        out.append((x0, y0, x1, y1))
        left -= {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}
    return out


def build(world, spec, rng, roles, houses, reserved=(), links=()):
    """Buduje miasto. roles: slowniki NPC (key, name, role, shop, color, greet, kind); pierwszy to kaplan.
    houses: ile domow na sprzedaz (tyle, ile sie zmiesci). reserved: pola, ktorych nie ruszac (stare zejscia),
    links: pola zarezerwowane, do ktorych prowadzi sciezka z ulicy. Zwraca opis jak newtowns.build."""
    t = Town(world, spec, rng, reserved)
    t.npcs = []
    t.lay_ground()
    t.plaza()
    inner = [t.gate(side) for side in spec['gates']]
    t.streets(inner, spec.get('side_streets', 1))
    for cell in links:
        t.link(cell)
    t.temple(roles[0], throne=bool(spec.get('palace')))
    if spec.get('palace'):
        t.temple_spot = (t.palace_spot[0], t.palace_spot[1] + 0)
    for role in sorted(roles[1:], key=lambda r: not r.get('central')):
        if not t.lot_free_for(role):
            t.side_street()
        t.shop(role)
    # domy: przy istniejacych ulicach, a gdy brak miejsca - nowa uliczka w glab wolnego kwartalu
    out_houses = []
    n = 1
    while n <= houses:
        h = t.house(n, spec['house_label'], spec.get('price_per_cell', 5000))
        if h is None:
            if not t.side_street():
                break
            continue
        out_houses.append(h)
        n += 1
    t.dress()
    t.check()
    x0, y0, x1, y1 = t.rect
    return {'name': spec['name'], 'rect': t.rect, 'temple': t.temple_spot, 'npcs': t.npcs, 'houses': out_houses,
            'gates': t.gates, 'pz': (x0 + 1, y0 + 1, x1 - 1, y1 - 1), 'pz_rects': rect_cover(t.cells),
            'cells': t.cells, 'streets': t.street,
            'buildings': t.buildings}
