"""Budowa miast z pelnych kafli (podloze, dekoracja, obiekt, sciana) zamiast pojedynczych symboli.

Plotno trzyma dla kazdego pola krotke (ground, deco, obj, wall) z kluczami z data/tiles.csv ('-' = brak).
Na koniec Legend zamienia krotki na symbole legendy: znane kombinacje dostaja stare symbole, nowe - kolejne
litery z puli (Latin-1 i Latin Extended-A, nieuzywane przez generate_world.py ani cities.py).
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'shared', 'src', 'main', 'resources')
NONE = '-'
POOL = ''.join(chr(c) for c in range(0xC0, 0x180) if chr(c) not in '×÷')


def load_tiles():
    """klucz -> (warstwa, przechodni)."""
    tiles = {}
    with open(os.path.join(ROOT, 'data', 'tiles.csv'), encoding='utf-8') as f:
        for line in f:
            if not line.strip() or line.startswith('#'):
                continue
            p = line.rstrip('\n').split(';')
            tiles[p[1]] = (p[2], p[3] == '1')
    return tiles


TILES = load_tiles()


def walkable(cell):
    ground, deco, obj, wall = cell
    if ground == NONE or wall != NONE or not TILES[ground][1]:
        return False
    return obj == NONE or TILES[obj][1]


class Legend:
    """Symbol <-> kafel; bazowe wpisy zachowuja swoje symbole, nowe kombinacje dostaja litery z puli."""

    def __init__(self, base, pool=POOL, used=''):
        self.base = list(base)
        self.by_cell = {}
        self.by_sym = {}
        for sym, *cell in self.base:
            self.by_sym[sym] = tuple(cell)
            self.by_cell.setdefault(tuple(cell), sym)
        self.extra = []
        self.pool = [c for c in pool if c not in self.by_sym and c not in used]

    def cell(self, sym):
        return self.by_sym[sym]

    def sym(self, cell):
        cell = tuple(cell)
        for k in cell:
            assert k == NONE or k in TILES, f'unknown tile {k} in {cell}'
        s = self.by_cell.get(cell)
        if s is None:
            s = self.pool.pop(0)
            self.by_cell[cell] = s
            self.by_sym[s] = cell
            self.extra.append((s, *cell))
        return s

    def walkable_symbols(self):
        return {s for s, c in self.by_sym.items() if s != 'space' and walkable(c)}


class Canvas:
    def __init__(self, w, h, fill=('grass', NONE, NONE, NONE)):
        self.w, self.h = w, h
        self.g = [[tuple(fill)] * w for _ in range(h)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def at(self, x, y):
        return self.g[y][x]

    def set(self, x, y, ground=None, deco=None, obj=None, wall=None):
        if not self.inside(x, y):
            return
        c = list(self.g[y][x])
        for i, v in enumerate((ground, deco, obj, wall)):
            if v is not None:
                c[i] = v
        self.g[y][x] = tuple(c)

    def put(self, x, y, cell):
        if self.inside(x, y):
            self.g[y][x] = tuple(cell)

    def fill(self, x0, y0, x1, y1, ground, deco=NONE, obj=NONE, wall=NONE):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, (ground, deco, obj, wall))

    def pave(self, x0, y0, x1, y1, ground):
        """Nowa nawierzchnia bez zmiany scian; zdejmuje drzewa i krzaki."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, (ground, NONE, NONE, NONE))

    def ground(self, x, y):
        return self.g[y][x][0]

    def is_wall(self, x, y):
        return self.inside(x, y) and self.g[y][x][3] != NONE

    def building(self, rects, wall, floor):
        """Budynek z sumy prostokatow (skrzydla, przybudowki): sciany na obrysie, podloga wewnatrz.
        Zwraca zbior pol wnetrza."""
        cells = set()
        for x0, y0, x1, y1 in rects:
            cells |= {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}
        inner = set()
        for (x, y) in cells:
            edge = any((x + dx, y + dy) not in cells for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            self.put(x, y, (floor, NONE, NONE, wall if edge else NONE))
            if not edge:
                inner.add((x, y))
        return inner

    def wall_line(self, pts, wall, ground):
        """Mur po lamanej z odcinkow poziomych i pionowych."""
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            assert ax == bx or ay == by, f'odcinek muru nie jest prosty: {(ax, ay)} -> {(bx, by)}'
            n = max(abs(bx - ax), abs(by - ay))
            for i in range(n + 1):
                x = ax + (bx > ax) * i - (bx < ax) * i
                y = ay + (by > ay) * i - (by < ay) * i
                self.put(x, y, (ground, NONE, NONE, wall))

    def door(self, x, y, floor=None):
        """Drzwi w scianie: podloze jak wnetrze obok (dach budynku obejmuje drzwi)."""
        if floor is None:
            floor = self.g[y][x][0]
        self.put(x, y, (floor, NONE, 'door_open', NONE))

    def obj(self, x, y, obj, deco=None):
        self.set(x, y, obj=obj, deco=deco)

    def to_rows(self, legend):
        return [[legend.sym(c) for c in row] for row in self.g]


def reachable(canvas, start, extra=()):
    """Pola osiagalne krokami N/S/E/W po przechodnich kaflach (extra = dodatkowe przechodnie pola)."""
    from collections import deque
    extra = set(extra)
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in seen and canvas.inside(*n) and (walkable(canvas.at(*n)) or n in extra):
                seen.add(n)
                q.append(n)
    return seen


def rects_of(cells):
    """Rozklad zbioru pol na prostokaty (wiersze laczone pionowo) - do stref ochronnych w map.properties."""
    rows = {}
    for (x, y) in sorted(cells, key=lambda c: (c[1], c[0])):
        runs = rows.setdefault(y, [])
        if runs and runs[-1][1] == x - 1:
            runs[-1][1] = x
        else:
            runs.append([x, x])
    out = []
    open_ = {}
    for y in sorted(rows):
        nxt = {}
        for x0, x1 in rows[y]:
            key = (x0, x1)
            if key in open_ and open_[key][3] == y - 1:
                r = open_[key]
                r[3] = y
            else:
                r = [x0, y, x1, y]
                out.append(r)
            nxt[key] = r
        open_ = nxt
    return [tuple(r) for r in out]
