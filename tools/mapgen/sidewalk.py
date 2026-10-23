"""Rzeczy stojace na srodku ulicy (donice, kwiaty, lawki) i straznicy przesuwani na pobocze, pod mur.

Pracuje na dowolnej siatce przez get(x, y) -> krotka kafla (ground, deco, obj, wall) albo None i put(x, y, kafel).
Pole na poboczu: przechodnie, puste, przylega bokiem do muru, nie stoi przed drzwiami i nie rozcina przejscia
(przechodni sasiedzi wokol zostaja polaczeni), a ulica obok ma co najmniej 2 pola szerokosci.
"""
import town as tk

NONE = '-'
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))
RING = ((-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0))
PROPS = ('flower_box', 'potted_plant', 'bench')


def _walk(c):
    return c is not None and tk.walkable(c)


def _door(c):
    return c is not None and (c[2].startswith('door') or c[3].startswith('door'))


def free_standing(get, x, y):
    """Na srodku ulicy: nie przylega bokiem do muru."""
    return all((get(x + dx, y + dy) or (NONE,) * 4)[3] == NONE for dx, dy in DIRS4)


def keeps_passage(get, x, y, taken=()):
    """Po zastawieniu (x, y) przechodnie pola wokol nadal lacza sie ze soba po pierscieniu 8 sasiadow."""
    ring = [(_walk(get(x + dx, y + dy)) and (x + dx, y + dy) not in taken) for dx, dy in RING]
    if not any(ring):
        return False
    # liczba osobnych odcinkow przechodnich na pierscieniu (rogi lacza sie tylko przez boki)
    runs = 0
    for i in range(8):
        if ring[i] and not ring[i - 1]:
            runs += 1
    if all(ring):
        runs = 1
    return runs == 1


def roadside(get, x, y, ground_ok, taken=()):
    c = get(x, y)
    if c is None or (x, y) in taken or not _walk(c) or c[2] != NONE or c[1] != NONE or c[0] not in ground_ok:
        return False
    wall_sides = [(dx, dy) for dx, dy in DIRS4 if (get(x + dx, y + dy) or (NONE,) * 4)[3] != NONE]
    if len(wall_sides) != 1:   # bez murow obok albo w waskim korytarzu / rogu
        return False
    if any(_door(get(x + dx, y + dy)) for dx, dy in RING):
        return False
    # ulica przy murze ma przynajmniej 2 pola szerokosci (pole naprzeciw muru i nastepne sa przechodnie)
    dx, dy = wall_sides[0]
    if not (_walk(get(x - dx, y - dy)) and _walk(get(x - 2 * dx, y - 2 * dy))):
        return False
    return keeps_passage(get, x, y, taken)


def nearest_roadside(get, x, y, ground_ok, radius=5, taken=(), avoid=()):
    for r in range(1, radius + 1):
        best = None
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if max(abs(dx), abs(dy)) != r:
                    continue
                p = (x + dx, y + dy)
                if p in avoid or not roadside(get, *p, ground_ok, taken):
                    continue
                d = dx * dx + dy * dy
                if best is None or d < best[0]:
                    best = (d, p)
        if best:
            return best[1]
    return None


def move_props(get, put, cells, kinds=PROPS, radius=5, avoid=()):
    """Rzeczy z kinds stojace na srodku ulicy idą pod najblizszy mur; gdy takiego miejsca nie ma, znikaja.
    Zwraca (przesuniete, usuniete)."""
    moved = removed = 0
    for (x, y) in cells:
        c = get(x, y)
        if c is None or c[2] not in kinds or not free_standing(get, x, y):
            continue
        put(x, y, (c[0], c[1], NONE, c[3]))
        ground_ok = {c[0]}
        spot = nearest_roadside(get, x, y, ground_ok, radius, avoid=avoid)
        if spot is None:
            removed += 1
            continue
        t = get(*spot)
        put(spot[0], spot[1], (t[0], t[1], c[2], t[3]))
        moved += 1
    return moved, removed
