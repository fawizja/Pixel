"""Wejscia na schody jak w Tibii (2026-11-12): schody w gore i drabiny stoja przy scianie, nie posrodku pustej jaskini.

Ostatni przebieg przed swiatlami na murach (generate_continent.finish). Dla kazdego pola schodow w gore (stairs_up) albo
drabiny (ladder) w lochu (z < 0), ktore nie ma muru od polnocy, na polu na polnoc od schodow staje kawalek muru (i
opcjonalnie dwa pola obok), zeby schody opieraly sie o sciane. Wyjscie z schodow jest od poludnia (cel przejscia
partnera), wiec sciana od polnocy nikomu nie zagradza drogi. Mur wchodzi tylko tam, gdzie nie rozspaja pieszej
siatki (test punktu prostego dla 4-spojnosci), nie ma obiektow ani celow przejsc, a w poblizu jest mur jaskini
(jego rodzaj dostaje nowy mur). Schody w dol (stairs_down) rysuje renderer w ramie z ciemnym otworem
(TileArt.stairPit); tu nic sie z nimi nie dzieje.
"""
import wall_lights

NONE = '-'
BACKED = ('stairs_up', 'ladder')
RING = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]   # N NE E SE S SW W NW


def _removable(walk, x, y):
    """Czy zamurowanie pola (x, y) nie rozdziela pieszych sasiadow (4-spojnosc): sasiedzi z boku lacza sie przez rog."""
    side = [walk(x + RING[i][0], y + RING[i][1]) for i in (0, 2, 4, 6)]
    diag = [walk(x + RING[i][0], y + RING[i][1]) for i in (1, 3, 5, 7)]   # NE SE SW NW: miedzy N-E, E-S, S-W, W-N
    n = sum(side)
    if n == 0:
        return True
    comps = n
    for i in range(4):
        if side[i] and side[(i + 1) % 4] and diag[i]:
            comps -= 1
    if n == 4 and comps == 0:   # petla dookola pola: wszyscy polaczeni
        comps = 1
    return comps <= 1


def back_stairs(ctx, size, floors):
    world = ctx['world']
    protected = set()
    for t in ctx['transitions']:
        protected.add((int(t[0]), int(t[1]), int(t[2])))
        protected.add((int(t[4]), int(t[5]), int(t[6])))
    for n in ctx['npcs']:
        protected.add((int(n[4]), int(n[5]), int(n[6])))
    stats = {'backed': 0, 'sides': 0, 'skipped': 0}
    for z in floors:
        if z >= 0:
            continue
        m = wall_lights.Mounter(world, size, z)
        g = world.floors[z]
        stairs = [(x, y) for y in range(size) for x in range(size)
                  if g[y][x] is not None and g[y][x][2] in BACKED]
        for (x, y) in stairs:
            if m.wall(x, y - 1):
                continue   # juz oparte o sciane od polnocy
            style = _nearest_wall(m, x, y)
            if style is None:
                stats['skipped'] += 1
                continue
            if not _put(m, g, protected, x, y - 1, z, style):
                stats['skipped'] += 1
                continue
            stats['backed'] += 1
            for dx in (-1, 1):
                if _put(m, g, protected, x + dx, y - 1, z, style):
                    stats['sides'] += 1
    return stats


def _nearest_wall(m, x, y):
    best = None
    for r in range(1, 8):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if max(abs(dx), abs(dy)) != r:
                    continue
                c = m.cell(x + dx, y + dy)
                if c is not None and c[3] != NONE:
                    return c[3]
    return best


def _put(m, g, protected, x, y, z, style):
    c = m.cell(x, y)
    if c is None or c[3] != NONE or c[2] != NONE or c[0] == NONE or (x, y, z) in protected:
        return False
    if not m.walkable(x, y):
        return False
    if not _removable(m.walkable, x, y):
        return False
    g[y][x] = (c[0], NONE, NONE, style)
    return True
