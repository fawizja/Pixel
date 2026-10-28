"""Pochodnie i latarnie na murach (Darek 2026-10-28: "pochodnie maja wisiec na scianach ... latarenki, ktore sa
na ziemi, maja wisiec na scianach").

Ostatni przebieg generatora: kazda pochodnia (wall_torch) i latarnia (street_lamp) stojaca na podlodze przenosi sie
na najblizszy mur, ktorego sciana jest widoczna z tego samego pomieszczenia albo ulicy: sciana poludniowa muru
poziomego (podloga na poludnie, mur po obu stronach na wschod i zachod) albo wschodnia muru pionowego (podloga na
wschod, mur na polnoc i poludnie). Latarnie zmieniaja sie w wall_lantern. Gdy w poblizu nie ma takiego muru
(latarnia na moscie, ognisko na lace), swiatlo znika. Regula sciany jest ta sama co w WallLights.face (Java).
"""
import os
from collections import deque

NONE = '-'
TORCH, LANTERN = 'wall_torch', 'wall_lantern'
SOURCES = {'wall_torch': TORCH, 'street_lamp': LANTERN, 'wall_lantern': LANTERN}
RADIUS = {TORCH: 4, LANTERN: 5}
SPACING = {TORCH: 3, LANTERN: 4}

HERE = os.path.dirname(os.path.abspath(__file__))
TILES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources', 'data', 'tiles.csv')


def walkable_tiles():
    out = {}
    with open(TILES, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.strip().split(';')
            out[p[1]] = p[3] == '1'
    return out


class Mounter:
    def __init__(self, world, size, z):
        self.w, self.S, self.z = world, size, z
        self.walk = walkable_tiles()

    def cell(self, x, y):
        if 0 <= x < self.S and 0 <= y < self.S:
            return self.w.get(x, y, self.z)
        return None

    def wall(self, x, y):
        c = self.cell(x, y)
        return c is not None and c[3] != NONE

    def open(self, x, y):
        """Jak WallLights.open: podloze bez muru."""
        c = self.cell(x, y)
        return c is not None and c[0] != NONE and c[3] == NONE

    def walkable(self, x, y):
        c = self.cell(x, y)
        if c is None or c[0] == NONE or c[3] != NONE or not self.walk.get(c[0], False):
            return False
        if c[2] != NONE and not self.walk.get(c[2], False):
            return False
        return c[1] == NONE or self.walk.get(c[1], True)

    def face(self, x, y):
        """Pole przed sciana, na ktorej zawisnie swiatlo w murze (x, y), albo None."""
        c = self.cell(x, y)
        if c is None or c[3] == NONE or c[2] != NONE:
            return None
        if self.z >= 0 and (x + y) % 3 == 1:
            return None   # tu bywa okno (Openings.hasWindow)
        if not self.clear(x + 1, y + 1):
            return None   # obiekt z pola po skosie (beczka, drewno) wystaje w gore i w lewo i zaslonilby swiatlo
        if self.open(x, y + 1):
            if self.wall(x - 1, y) and self.wall(x + 1, y) and self.walkable(x, y + 1) and self.clear(x, y + 1) \
                    and not self.door(x, y + 1):
                return (x, y + 1)
            return None   # sciana poludniowa jest odslonieta, wiec swiatlo i tak wisialoby na niej
        if self.open(x + 1, y) and self.wall(x, y - 1) and self.wall(x, y + 1) and self.walkable(x + 1, y) \
                and self.clear(x + 1, y) and not self.door(x + 1, y):
            return (x + 1, y)
        return None

    def clear(self, x, y):
        c = self.cell(x, y)
        return c is None or c[2] == NONE or self.walk.get(c[2], False)

    def door(self, x, y):
        c = self.cell(x, y)
        return c is not None and c[2].startswith('door')

    def reach(self, x, y, radius):
        """Pola osiagalne pieszo ze zrodla (to samo pomieszczenie / ulica)."""
        seen = {(x, y): 0}
        q = deque([(x, y)])
        while q:
            cx, cy = q.popleft()
            d = seen[(cx, cy)]
            if d >= radius:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (cx + dx, cy + dy)
                if n not in seen and self.walkable(*n):
                    seen[n] = d + 1
                    q.append(n)
        return seen


def mount(world, size, floors):
    stats = {'moved': 0, 'dropped': 0}
    for z in floors:
        m = Mounter(world, size, z)
        sources = []
        g = world.floors[z]
        for y in range(size):
            row = g[y]
            for x in range(size):
                c = row[x]
                if c is not None and c[2] in SOURCES and c[3] == NONE:
                    sources.append((x, y, SOURCES[c[2]]))
                    row[x] = (c[0], c[1], NONE, c[3])
        placed = []
        for (x, y, kind) in sources:
            r = RADIUS[kind]
            reach = m.reach(x, y, r + 2)
            best = None
            for wy in range(y - r - 1, y + r + 2):
                for wx in range(x - r - 1, x + r + 2):
                    f = m.face(wx, wy)
                    if f is None or f not in reach:
                        continue
                    if any(max(abs(wx - px), abs(wy - py)) < SPACING[kind] for (px, py) in placed):
                        continue
                    d = (reach[f], abs(wx - x) + abs(wy - y), wy, wx)
                    if best is None or d < best[0]:
                        best = (d, wx, wy)
            if best is None:
                stats['dropped'] += 1
                continue
            _, wx, wy = best
            c = g[wy][wx]
            g[wy][wx] = (c[0], c[1], kind, c[3])
            placed.append((wx, wy))
            stats['moved'] += 1
        for y in range(size):
            for x in range(size):
                c = g[y][x]
                assert c is None or c[2] not in SOURCES or c[3] != NONE, f'light on the floor at {x},{y},{z}'
    return stats
