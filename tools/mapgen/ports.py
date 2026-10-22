"""Etap 7 rozbudowy w stylu Tibii: przystanie, mola i lodzie na wyspach, mosty na wyspy przy brzegu.

Wywolywany z generate_world.py po world_geo.geography (wyspy juz istnieja). Kazda przystan to drewniany
pomost na brzegu, molo w morze i zacumowana lodz; kapitan stoi na pomoscie w strefie ochronnej.
Trasy rejsow sa w maps/world/ships.csv (pisane tutaj), NPC kapitanow w npcs.csv (edytowane recznie).
"""

# kierunek: (dx, dy)
N, S, E, W = (0, -1), (0, 1), (1, 0), (-1, 0)

# lodzie (kadlub 'Б', poklad 'b', beczka 'ч'); otwarta strona styka sie z molem
BOATS = {
    S: ['БbbБ', 'БbчБ', 'БbbБ', ' ББ '],
    N: [' ББ ', 'БbbБ', 'БbчБ', 'БbbБ'],
    E: ['БББ ', 'bbчБ', 'bbbБ', 'БББ '],
    W: [' БББ', 'Бчbb', 'Бbbb', ' БББ'],
}

# przystanie: klucz kapitana, nazwa miejsca, punkt startu skanu (na ladzie), kierunek do morza
DOCKS = [
    ('east_captain', 'Przystan Wschodnia', (258, 150), E),
    ('coral_captain', 'Wyspa Koralowa', (120, 268), N),
    ('pirate_captain', 'Wyspa Piratow', (48, 282), N),
    ('mist_captain', 'Wyspa Mgiel', (250, 285), N),
    ('ice_captain', 'Lodowa Wyspa', (310, 38), W),
    ('volcano_captain', 'Wyspa Wulkaniczna', (314, 190), W),
]

# rejsy (w obie strony); Selvaran - port glowny, Przystan Wschodnia - wyspy wschodu
ROUTES = [
    ('selv_captain', 'qas_captain'),
    ('selv_captain', 'east_captain'),
    ('selv_captain', 'coral_captain'),
    ('selv_captain', 'pirate_captain'),
    ('selv_captain', 'mist_captain'),
    ('qas_captain', 'pirate_captain'),
    ('qas_captain', 'coral_captain'),
    ('east_captain', 'mist_captain'),
    ('east_captain', 'ice_captain'),
    ('east_captain', 'volcano_captain'),
]

# mosty na wyspy tuz przy brzegu kontynentu: (x, y) na ladzie, kierunek
BRIDGES = [
    ('Most Latarni', (255, 112), E),
    ('Most Mewi', (190, 236), S),
]

PIER = 6


def _put(g, x, y, ch):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = ch


def _to_water(g, start, d):
    """Ostatnie pole ladu przed morzem idac od start w kierunku d."""
    x, y = start
    while g[y + d[1]][x + d[0]] != '~':
        x, y = x + d[0], y + d[1]
    return x, y


def dock(g, start, d):
    """Pomost 3x2 na brzegu, molo 2 pola szerokie w morze i lodz na koncu. Zwraca (pole kapitana, prostokat pz)."""
    rx, ry = _to_water(g, start, d)
    px, py = (abs(d[1]), abs(d[0]))                      # prostopadly
    tiles = []
    # pomost na ladzie (pole brzegu i jedno w glab), 3 szerokie
    for back in (0, 1):
        for side in (-1, 0, 1):
            tiles.append((rx - d[0] * back + px * side, ry - d[1] * back + py * side))
    # molo w morze, 2 szerokie
    for k in range(1, PIER + 1):
        for side in (0, 1):
            tiles.append((rx + d[0] * k + px * side, ry + d[1] * k + py * side))
    for (x, y) in tiles:
        _put(g, x, y, 'b')
    # lodz za koncem mola
    ex, ey = rx + d[0] * (PIER + 1), ry + d[1] * (PIER + 1)
    rows = BOATS[d]
    if d in (N, S):
        bx = ex - 1
        by = ey if d == S else ey - 3
    else:
        by = ey - 1
        bx = ex if d == E else ex - 3
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ' ' and g[by + j][bx + i] == '~':
                g[by + j][bx + i] = ch
    captain = (rx - px, ry - py)
    xs = [x for (x, _) in tiles] + [bx, bx + 3]
    ys = [y for (_, y) in tiles] + [by, by + 3]
    return captain, (min(xs) - 1, min(ys) - 1, max(xs) + 1, max(ys) + 1)


def bridge(g, start, d):
    """Most 2 pola szeroki od brzegu do najblizszego ladu w kierunku d. Zwraca liste pol mostu."""
    rx, ry = _to_water(g, start, d)
    px, py = (abs(d[1]), abs(d[0]))
    out = []
    x, y = rx + d[0], ry + d[1]
    while g[y][x] == '~':
        for side in (0, 1):
            out.append((x + px * side, y + py * side))
        x, y = x + d[0], y + d[1]
    # brzeg po drugiej stronie, gdyby drugi pas trafil na wode dluzej
    for (bx, by) in out:
        _put(g, bx, by, 'b')
    for side in (0, 1):
        tx, ty = x + px * side, y + py * side
        if g[ty][tx] == '~':
            _put(g, tx, ty, 'b')
    return out


def qasirah_port(g):
    """Brama poludniowa Qasirah, droga przez plaze i przystan w zatoce."""
    for x in (79, 80):
        _put(g, x, 220, 'X')
        for y in range(221, 230):
            _put(g, x, y, 'X')
    captain, pz = dock(g, (79, 229), S)
    return captain, pz


def selvaran_captain():
    """Kapitan na nabrzezu przy srodkowym molo portu Selvaran (pz portu z cities.harbor)."""
    return (127, 168)


def ports(floors):
    g = floors[0]
    spots = {'selv_captain': ('Selvaran', selvaran_captain())}
    pz = []
    cap, rect = qasirah_port(g)
    spots['qas_captain'] = ('Qasirah', cap)
    pz.append(rect)
    for key, name, start, d in DOCKS:
        cap, rect = dock(g, start, d)
        spots[key] = (name, cap)
        pz.append(rect)
    bridges = []
    for name, start, d in BRIDGES:
        bridges.append((name, bridge(g, start, d)))
    return {
        'captains': spots,
        'pz': [(x0, y0, x1, y1, 0) for (x0, y0, x1, y1) in pz],
        'bridges': bridges,
        'routes': ROUTES,
    }
