"""Durnkar, miasto krasnoludow pod gorami, i kopalnie krasnoludow (Darek 2026-11-11, etap 3 duzej listy).

Durnkar (w stylu Kazordoon z Tibii) lezy w wielkiej grocie na pietrze -5 pod gorami Szarych Turni. Wejscie nie jest
oczywiste: waski, kreta wawoz miedzy skalami (bez drogi) konczy sie pieczara ze schodami, dalej kretymi sztolniami
-1..-4 w dol do przedsionka z brama i straznikiem. W grocie: rzeka lawy z mostami, swiatynia z kaplanem (miasto ze
swiatynia - mozna tu zamieszkac, ale nie zaczac gry), bank z depozytem, sala tronowa krola, karczma, kuznia, platnerz,
runiarz, narzedziownik, kupiec, zaklinacz, opiekun zwierzat i kilkanascie domow na sprzedaz.

Trzy kopalnie krasnoludow (sztolnie w zboczach gory, kazda do -7): z kazdym pietrem silniejsze i liczniejsze potwory
(-1 czerwie i krasnoludy ... -7 behemoty), pietra coraz wieksze, z bocznymi slepymi komorami. Kopalnia Zelaznej Brody
na pietrze -5 laczy sie korytarzem ze wschodnia brama Durnkaru (drzwi poziomu). Uklad sprawdza Plan.check.
"""
import heapq
import random

import numpy as np

import features as F
import structures as St

NONE = '-'
SEED = 20261112
CITY = 'Durnkar'
CW, CH = 76, 56                  # grota miasta (z obramowaniem skaly)
GATE_LEVEL = 25                  # drzwi miedzy kopalnia a miastem (nikt slabszy nie przejdzie z miasta w glebie)
CITY_LEVELS = (30, 60)           # region miasta (odleglosc startowa w route_levels)
STONE, MARBLE, WOOD = 'stone_floor', 'marble_floor', 'wood_floor'
SWALL, ROCK = 'stone_wall', 'cave_wall'
STREET = 'cobblestone'
MOUNTAIN = ('mountain', 'snow_mountain')

F.CAVE_STYLES.setdefault('deep_mine', ('cave_floor', 'cave_wall', ['glow_moss'], ['rock', 'rubble', 'crystal', 'lumber'],
                                       'wall_torch'))
F.CAVE_STYLES.setdefault('magma_mine', ('cave_floor', 'cave_wall', [], ['obsidian_rock', 'rock', 'magma'], 'magma'))
F.CAVE_STYLES.setdefault('dwarf_tunnel', ('cave_floor', 'cave_wall', ['glow_moss'], ['rock', 'rubble'], 'glow_moss'))

# potwory pieter kopalni (Tibia: krasnoludy w gornych sztolniach, czerwie, golemy i zywiolaki nizej, behemoty na dnie);
# (potwory, liczba w stadzie) - glebiej silniejsze i liczniejsze
FLOOR_MONSTERS = {
    -1: (['mine_grub', 'dwarf', 'mine_grub', 'dwarf'], (3, 3)),
    -2: (['dwarf', 'dwarf_miner', 'mine_grub', 'dwarf_miner'], (3, 4)),
    -3: (['dwarf_miner', 'dwarf_soldier', 'deep_worm', 'dwarf_soldier'], (3, 4)),
    -4: (['dwarf_soldier', 'dwarf_warden', 'deep_worm', 'dwarf_warden'], (4, 4)),
    -5: (['dwarf_warden', 'dwarf_earthcaller', 'rock_golem', 'dwarf_earthcaller'], (5, 6)),
    -6: (['earth_spirit', 'crystal_golem', 'earth_spirit', 'earth_spirit'], (5, 6)),
    -7: (['deep_behemot', 'deep_behemot', 'stone_sentinel', 'deep_behemot'], (5, 5)),
}
# poziomy (monsters.csv) - komory od schodow: najpierw slabsze, dalej silniejsze (droga do slabych nie prowadzi
# obok silnych, jak wymaga route_levels)
LEVEL = {'mine_grub': 8, 'dwarf': 10, 'dwarf_miner': 12, 'dwarf_soldier': 16, 'deep_worm': 20, 'dwarf_warden': 26,
         'rock_golem': 32, 'dwarf_earthcaller': 34, 'crystal_golem': 45, 'earth_spirit': 55, 'deep_behemot': 120,
         'stone_sentinel': 140}
FLOOR_STYLE = {-1: 'mine', -2: 'mine', -3: 'mine', -4: 'mine', -5: 'deep_mine', -6: 'deep_mine', -7: 'magma_mine'}
MINES = [
    # nazwa, kierunek sztolni od srodka gory (dx, dy), przesuniecie pieter pod ziemia od wejscia, polaczenie z miastem
    ('Kopalnia Żelaznej Brody', (1, 0), (24, 0), True),
    ('Kopalnia Głębokiego Kilofa', (-1, 0), (-22, 4), False),
    ('Stara Sztolnia Granitowa', (0, 1), (2, 22), False),
]
MINE_LEVELS = (8, 150)
FLOOR_LEVELS = {-1: (8, 15), -2: (8, 20), -3: (12, 30), -4: (16, 40), -5: (26, 50), -6: (32, 80), -7: (55, 160)}


def floor_regions(plan, name, cells_by_z, levels=None):
    """Region na kazde pietro (prostokat pol tego pietra) - nie obejmuje cudzych lochow obok."""
    for z in sorted(cells_by_z, reverse=True):
        cs = cells_by_z[z]
        if not cs:
            continue
        lo, hi = (levels or FLOOR_LEVELS)[z] if isinstance(levels or FLOOR_LEVELS, dict) else levels
        plan.regions.append((name, min(c[0] for c in cs), min(c[1] for c in cs), max(c[0] for c in cs),
                             max(c[1] for c in cs), z, z, lo, hi))
CITY_HOUSE_PRICE = 1000          # gp za pole wnetrza (jak w miastach, homes.TOWN_PRICE)


# ====================================================================== miejsce pod gora
def mountain_core(world, near, radius=70):
    """Pole gory najdalej od jej brzegu (srodek masywu) w promieniu od near; zwraca (x, y, odleglosc do brzegu)."""
    x0, y0 = near
    xs, ys = x0 - radius, y0 - radius
    n = 2 * radius + 1
    m = np.zeros((n, n), bool)
    for y in range(n):
        for x in range(n):
            c = world.get(xs + x, ys + y)
            m[y, x] = c is not None and c[3] in MOUNTAIN
    d = np.where(m, 10 ** 6, 0)
    for _ in range(2):
        for y in range(n):
            for x in range(n):
                if d[y, x]:
                    a = min(d[y - 1, x] if y else 0, d[y, x - 1] if x else 0) + 1
                    d[y, x] = min(d[y, x], a)
        for y in range(n - 1, -1, -1):
            for x in range(n - 1, -1, -1):
                if d[y, x]:
                    a = min(d[y + 1, x] if y < n - 1 else 0, d[y, x + 1] if x < n - 1 else 0) + 1
                    d[y, x] = min(d[y, x], a)
    yy, xx = np.unravel_index(int(np.argmax(d)), d.shape)
    return xs + int(xx), ys + int(yy), int(d[yy, xx])


def edge_cell(world, core, direction, reach):
    """Od srodka gory w kierunku direction: ostatnie pole gory tuz przed pierwszym osiagalnym polem terenu
    (miedzy nimi nic). Zwraca (pole gory na brzegu, pole terenu przed sztolnia)."""
    x, y = core
    dx, dy = direction
    last = None
    for _ in range(200):
        x, y = x + dx, y + dy
        c = world.get(x, y)
        if c is None:
            break
        if c[3] in MOUNTAIN:
            last = (x, y)
            continue
        if last is not None and c[3] == NONE and c[0] not in ('water', 'deep_water', 'bridge', 'swamp', 'lava') \
                and c[2] not in F.STAIRS and any(reach[y + b, x + a] for a, b in ((0, 0),) + F.DIRS4):
            return last, (x, y)
        last = None
    raise AssertionError(f'no mountain edge from {core} towards {direction}')


def ravine(world, core, rng, reach, cost, avoid=()):
    """Waski kreta wawoz z lasu w glab gory (ukryte wejscie). Zwraca (pola wawozu od wejscia, pole pieczary)."""
    S = len(world.floors[0])
    best = None
    cx, cy = core
    # kandydaci: pola gory na brzegu masywu (sasiad z osiagalnym terenem, z dala od drog i od sztolni kopalni)
    for y in range(cy - 60, cy + 61):
        for x in range(cx - 60, cx + 61):
            c = world.get(x, y)
            if c is None or c[3] not in MOUNTAIN:
                continue
            out = None
            for ddx, ddy in F.DIRS4:
                o = world.get(x + ddx, y + ddy)
                if o is not None and F.walk(o) and o[2] == NONE and reach[y + ddy, x + ddx] and o[0] not in F.ROAD_GROUND \
                        and o[0] not in ('water', 'swamp'):
                    out = (x + ddx, y + ddy)
            if out is None:
                continue
            if any(abs(x - a) + abs(y - b) < 30 for (a, b) in avoid):
                continue
            road = cost[max(0, y - 9):min(S, y + 10), max(0, x - 9):min(S, x + 10)]
            if (road <= 0.35).any():
                continue                                         # z drogi wawozu nie widac
            d = abs(x - cx) + abs(y - cy)
            score = d + rng.random() * 4
            if best is None or score < best[0]:
                best = (score, (x, y), out)
    assert best is not None, 'no hidden entrance spot'
    start, out = best[1], best[2]
    # sciezka do pola 9 krokow od brzegu w strone srodka: Dijkstra z losowym kosztem (kreci)
    noise = {}

    def w(p):
        if p not in noise:
            noise[p] = 1 + rng.random() * 6
        return noise[p]
    goal_d = 16
    dist = {start: 0}
    prev = {start: None}
    pq = [(0, start)]
    end = None
    while pq:
        dcur, p = heapq.heappop(pq)
        if dcur > dist[p]:
            continue
        c = world.get(*p)
        depth = min(abs(p[0] - start[0]) + abs(p[1] - start[1]), 99)
        if depth >= goal_d and all(world.get(p[0] + a, p[1] + b)[3] in MOUNTAIN for a in range(-2, 3) for b in range(-2, 3)):
            end = p
            break
        for ddx, ddy in F.DIRS4:
            n = (p[0] + ddx, p[1] + ddy)
            cn = world.get(*n)
            if cn is None or cn[3] not in MOUNTAIN:
                continue
            # wawoz nie wychodzi z powrotem na brzeg gory (2 pola skaly z kazdej strony)
            if n != start and any(world.get(n[0] + a, n[1] + b)[3] not in MOUNTAIN for a in (-1, 0, 1) for b in (-1, 0, 1)) \
                    and abs(n[0] - start[0]) + abs(n[1] - start[1]) > 3:
                continue
            nd = dcur + w(n) + (0 if abs(n[0] - cx) + abs(n[1] - cy) < abs(p[0] - cx) + abs(p[1] - cy) else 2)
            if nd < dist.get(n, 1e9):
                dist[n] = nd
                prev[n] = p
                heapq.heappush(pq, (nd, n))
        _ = c
    assert end is not None, 'ravine did not reach the core'
    path = []
    p = end
    while p is not None:
        path.append(p)
        p = prev[p]
    path.reverse()
    return out, path


# ====================================================================== miasto na -5
def _keep_free(reserved, *pts):
    for (x, y) in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                reserved.add((x + dx, y + dy))


def _torch(p, x, y, z):
    c = p.get(x, y, z)
    if c is None or c[3] == NONE:
        return
    for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
        d = p.get(x + dx, y + dy, z)
        if d is not None and St.walk(d) and d[2] == NONE:
            p.put(x + dx, y + dy, z, (d[0], d[1], 'wall_torch', NONE))
            return


def _house(p, z, x, y, w, h, door_side, floor, rng):
    """Dom na sprzedaz: mury, drzwi, 1-3 lozka i meble przy scianach. Zwraca (wnetrze x0, y0, x1, y1, drzwi)."""
    door = {'S': (x + w // 2, y + h - 1), 'N': (x + w // 2, y), 'E': (x + w - 1, y + h // 2), 'W': (x, y + h // 2)}[door_side]
    inner = p.box(z, x, y, w, h, floor, SWALL, doors=[door])
    ix0, iy0, ix1, iy1 = x + 1, y + 1, x + w - 2, y + h - 2
    far = sorted(inner, key=lambda c: -(abs(c[0] - door[0]) + abs(c[1] - door[1])))
    beds = 1 if len(inner) <= 12 else 2 if len(inner) <= 24 else 3
    placed = []
    for (bx, by) in far:
        if len(placed) >= beds:
            break
        wall_next = bx in (ix0, ix1) or by in (iy0, iy1)
        if not wall_next or any(abs(bx - a) <= 1 and abs(by - b) <= 1 for (a, b) in placed):
            continue
        p.obj(bx, by, z, 'bed')
        placed.append((bx, by))
    for (bx, by), o in zip([c for c in far[len(far) // 3:] if (c[0] in (ix0, ix1) or c[1] in (iy0, iy1))
                             and abs(c[0] - door[0]) + abs(c[1] - door[1]) > 2 and c not in placed][::3][:2],
                            ('table', rng.choice(('wardrobe', 'barrel', 'bookcase')))):
        p.obj(bx, by, z, o)
    return (ix0, iy0, ix1, iy1, door)


def city_plan(p, ox, oy, rng):
    """Grota Durnkaru w planie p na pietrze -5 z lewym gornym rogiem (ox, oy). Zwraca slownik miejsc."""
    z = -5
    reserved = set()
    for y in range(CH):
        for x in range(CW):
            p.put(ox + x, oy + y, z, ('cave_floor', NONE, NONE, ROCK))
    # grota: posadzka z bruku, kilka skalnych filarow
    for y in range(2, CH - 2):
        for x in range(2, CW - 2):
            p.put(ox + x, oy + y, z, (STREET, NONE, NONE, NONE))
    # nierowne sciany groty (zaokraglone rogi)
    for (cx, cy) in ((2, 2), (CW - 3, 2), (2, CH - 3), (CW - 3, CH - 3)):
        for dx in range(-1, 3):
            for dy in range(-1, 3):
                X = cx + (dx if cx < 10 else -dx)
                Y = cy + (dy if cy < 10 else -dy)
                if abs(X - cx) + abs(Y - cy) <= 2:
                    p.put(ox + X, oy + Y, z, ('cave_floor', NONE, NONE, ROCK))
    # rzeka lawy przez srodek z mostami (glowna aleja i poludniowy most)
    for y in range(2, CH - 2):
        for x in (36, 37, 38, 39):
            p.put(ox + x, oy + y, z, ('lava', NONE, NONE, NONE))
    for (y0, y1) in ((25, 29), (44, 45), (14, 15)):
        for y in range(y0, y1 + 1):
            for x in (36, 37, 38, 39):
                p.put(ox + x, oy + y, z, ('bridge', NONE, NONE, NONE))
    for y in range(13, 31):
        _keep_free(reserved, (ox + 35, oy + y), (ox + 40, oy + y))
    # glowna aleja: plyty
    for y in range(25, 30):
        for x in range(2, CW - 2):
            if p.get(ox + x, oy + y, z)[0] != 'bridge':
                p.put(ox + x, oy + y, z, (STONE, NONE, NONE, NONE))
    for x in range(2, CW - 2):
        _keep_free(reserved, (ox + x, oy + 27))
    spots = {}
    npcs = []
    color = '#a0703a'
    # ---------------------------------------------------------------- polnoc: swiatynia, bank, sala tronowa, karczma
    p.box(z, ox + 4, oy + 3, 16, 11, MARBLE, SWALL, doors=[(ox + 11, oy + 13)])
    _keep_free(reserved, (ox + 11, oy + 14), (ox + 11, oy + 12))
    p.obj(ox + 11, oy + 4, z, 'altar')
    for (x, y) in ((ox + 8, oy + 4), (ox + 14, oy + 4)):
        p.obj(x, y, z, 'candle')
    for (x, y) in ((ox + 6, oy + 7), (ox + 6, oy + 9), (ox + 16, oy + 7), (ox + 16, oy + 9), (ox + 7, oy + 7), (ox + 7, oy + 9),
                   (ox + 15, oy + 7), (ox + 15, oy + 9)):
        p.obj(x, y, z, 'bench')
    for y in range(5, 13):
        p.deco(ox + 11, oy + y, z, 'carpet')
    for (x, y) in ((ox + 5, oy + 4), (ox + 18, oy + 4)):
        p.obj(x, y, z, 'statue')
    spots['temple'] = (ox + 11, oy + 9)
    npcs.append(['durn_priest', 'Kapłan Thorvin', 'PRIEST', CITY, ox + 13, oy + 5, z, '-', '#f0f0f0',
                 'Witaj w świątyni Durnkaru, synu i córko kamienia. Mogę cię uleczyć albo przyjąć do miasta.'])
    # bank i depozyt
    p.box(z, ox + 22, oy + 3, 13, 10, STONE, SWALL, doors=[(ox + 28, oy + 12)])
    _keep_free(reserved, (ox + 28, oy + 13), (ox + 28, oy + 11))
    for x in range(24, 33, 2):
        p.obj(ox + x, oy + 4, z, 'depot_locker')
    for x in (23, 33):
        p.obj(ox + x, oy + 8, z, 'chest')
    p.obj(ox + 31, oy + 9, z, 'counter')
    npcs.append(['durn_banker', 'Bankier Grimbald', 'BANKER', CITY, ox + 25, oy + 8, z, '-', '#d8c070',
                 'Złoto w skarbcu Durnkaru leży pod tysiącem sążni skały. Bank prowadzi konta we wszystkich miastach.'])
    npcs.append(['durn_housing', 'Zarządca Durgan', 'HOUSING', CITY, ox + 31, oy + 10, z, '-', '#6a5a8a',
                 'Domy w grocie Durnkaru, ciepłe od lawy. Płacisz z konta w banku.'])
    # sala tronowa
    p.box(z, ox + 41, oy + 3, 16, 11, MARBLE, SWALL, doors=[(ox + 48, oy + 13)])
    _keep_free(reserved, (ox + 48, oy + 14), (ox + 48, oy + 12))
    p.obj(ox + 48, oy + 4, z, 'throne')
    for y in range(5, 13):
        p.deco(ox + 48, oy + y, z, 'carpet')
    for (x, y) in ((ox + 44, oy + 4), (ox + 52, oy + 4)):
        p.obj(x, y, z, 'banner')
    for (x, y) in ((ox + 43, oy + 7), (ox + 53, oy + 7), (ox + 43, oy + 10), (ox + 53, oy + 10)):
        p.obj(x, y, z, 'statue')
    for (x, y) in ((ox + 42, oy + 4), (ox + 55, oy + 4)):
        p.obj(x, y, z, 'campfire')
    npcs.append(['durn_ruler', 'Król Thorgrim', 'KING', CITY, ox + 48, oy + 6, z, '-', '#c8a040',
                 'Durnkar pamięta czasy, gdy nad górami nie było jeszcze miast. Konto premium i promocję dam ci tak jak '
                 'król w Varden: napisz premium albo promocja.'])
    # karczma
    p.box(z, ox + 59, oy + 3, 14, 10, WOOD, SWALL, doors=[(ox + 65, oy + 12)])
    _keep_free(reserved, (ox + 65, oy + 13), (ox + 65, oy + 11))
    for (x, y, o) in ((61, 6, 'table'), (61, 9, 'table'), (68, 6, 'table'), (68, 9, 'table'), (62, 6, 'chair'), (62, 9, 'chair'),
                      (69, 6, 'chair'), (69, 9, 'chair'), (71, 4, 'barrel'), (71, 5, 'barrel'), (60, 4, 'barrel'), (64, 4, 'counter'),
                      (66, 4, 'counter'), (71, 10, 'firewood')):
        p.obj(ox + x, oy + y, z, o)
    npcs.append(['durn_innkeeper', 'Karczmarz Ulfrik', 'MERCHANT', CITY, ox + 65, oy + 5, z, 'durn_supplies', '#a07040',
                 'Piwo z grzybów jaskiniowych, chleb, mięso i lina na drogę. Siadaj, przybyszu.'])
    # ---------------------------------------------------------------- domy przy alei (rzad polnocny)
    houses = []
    names = iter([f'Kowadłowa {i}' for i in range(1, 9)] + [f'Głębinowa {i}' for i in range(1, 9)])
    for (x, y, side) in ((4, 16, 'S'), (12, 16, 'S'), (58, 16, 'S'), (66, 16, 'S')):
        h = _house(p, z, ox + x, oy + y, 7, 7, side, STONE, rng)
        houses.append((next(names), h))
        _keep_free(reserved, (h[4][0], h[4][1] + 1))
    # place: paleniska (ogien w kamiennym kregu), posag, filary
    for (x, y) in ((24, 18), (31, 18), (44, 18), (52, 18), (24, 22), (31, 22), (44, 22), (52, 22)):
        p.obj(ox + x, oy + y, z, 'campfire')
    p.obj(ox + 27, oy + 20, z, 'statue')
    p.obj(ox + 48, oy + 20, z, 'well')
    for (x, y) in ((20, 19), (33, 21), (56, 19), (42, 21)):
        for dx in (0, 1):
            for dy in (0, 1):
                p.put(ox + x + dx, oy + y + dy, z, ('cave_floor', NONE, NONE, ROCK))
    # ---------------------------------------------------------------- poludnie: sklepy
    shops = [  # x, szer., klucz, imie, sklep, powitanie, wnetrze
        (4, 12, 'durn_smith', 'Kowal Brokk', 'durn_arms',
         'Topory, młoty i kusze z kuźni Durnkaru. Skupuję też broń i rudę.', 'forge'),
        (17, 10, 'durn_armorer', 'Płatnerz Hagar', 'durn_armor',
         'Zbroje kute w żarze lawy: hełmy, kolczugi i tarcze dla każdego wojownika.', 'armour'),
        (28, 7, 'durn_imbuer', 'Zaklinacz Runnar', None,
         'Runy wyryte w kamieniu trzymają moc nasyceń. Kradzież życia, many, krytyk, żywioły i ochrony.', 'runes'),
        (41, 11, 'durn_mage', 'Runiarz Odvar', 'durn_magic',
         'Mikstury, runy i różdżki. Krasnoludzkie runy nie pękają nawet w bitwie.', 'magic'),
        (53, 10, 'durn_tools', 'Narzędziownik Skagi', 'durn_tools',
         'Liny, łopaty, pochodnie i hełmy górnicze - wszystko, czego trzeba w kopalni.', 'tools'),
        (64, 9, 'durn_merchant', 'Kupiec Gunnar', 'durn_shop',
         'Kupię łupy z kopalń i sprzedam to, czego brakuje w drodze.', 'general'),
    ]
    for (x, w, key, name, shop, greet, kind) in shops:
        door = (ox + x + w // 2, oy + 31)
        p.box(z, ox + x, oy + 31, w, 9, STONE if kind != 'general' else WOOD, SWALL, doors=[door])
        _keep_free(reserved, (door[0], door[1] - 1), (door[0], door[1] + 1))
        ix0, ix1 = ox + x + 1, ox + x + w - 2
        if kind == 'forge':
            for (a, o) in ((ix0, 'forge'), (ix0 + 2, 'anvil'), (ix1, 'forge'), (ix1 - 2, 'anvil')):
                p.obj(a, oy + 38, z, o)
            p.obj(ix0, oy + 33, z, 'lumber')
            p.obj(ix1, oy + 33, z, 'barrel')
        elif kind == 'armour':
            for a in range(ix0, ix1 + 1, 2):
                p.obj(a, oy + 38, z, 'showcase')
        elif kind == 'runes':
            p.obj(ix0, oy + 38, z, 'bookcase')
            p.obj(ix1, oy + 38, z, 'candle')
            p.obj(ix0 + 2, oy + 38, z, 'altar')
        elif kind == 'magic':
            for a in range(ix0, ix1 + 1, 2):
                p.obj(a, oy + 38, z, 'bookcase' if a % 4 else 'clay_pot')
        elif kind == 'tools':
            for (a, o) in ((ix0, 'crate'), (ix0 + 1, 'lumber'), (ix1, 'barrel'), (ix1 - 2, 'sack'), (ix0 + 3, 'crate')):
                p.obj(a, oy + 38, z, o)
        else:
            for a in range(ix0, ix1 + 1, 2):
                p.obj(a, oy + 38, z, 'crate' if a % 4 else 'sack')
        cx = ox + x + w // 2
        p.obj(cx - 1 if cx - 1 >= ix0 else cx + 1, oy + 35, z, 'counter')
        role = 'IMBUER' if key == 'durn_imbuer' else 'MERCHANT'
        npcs.append([key, name, role, CITY, cx, oy + 36, z, shop or '-', '#7050b0' if role == 'IMBUER' else color, greet])
    # opiekun zwierzat w zagrodzie przy karczmie (bez lady), przewodnik przy placu
    npcs.append(['durn_petkeeper', 'Opiekun zwierząt Bodvar', 'MERCHANT', CITY, ox + 70, oy + 20, z, 'durn_pets', '#6a8a3a',
                 'Kruki z górskich turni lecą za krasnoludem i w głąb kopalni. Pupil u boku daje +5% doświadczenia z potworów.'])
    for (x, y) in ((68, 19), (72, 19), (68, 22), (72, 22)):
        p.obj(ox + x, oy + y, z, 'fence')
    p.obj(ox + 72, oy + 21, z, 'hay_bale')
    npcs.append(['durn_guide', 'Przewodnik Dvalin', 'GUIDE', CITY, ox + 27, oy + 23, z, '-', '#8a6a3a',
                 'Kopalnie wokół Durnkaru roją się od odszczepieńców i potworów z głębin. Każde piętro niżej jest groźniejsze.'])
    # ---------------------------------------------------------------- domy na poludniu
    for (x, side) in ((4, 'N'), (12, 'N'), (20, 'N'), (28, 'N'), (42, 'N'), (50, 'N'), (58, 'N'), (66, 'N')):
        h = _house(p, z, ox + x, oy + 46, 7, 7, side, STONE if x < 36 else WOOD, rng)
        houses.append((next(names), h))
        _keep_free(reserved, (h[4][0], h[4][1] - 1))
    # ---------------------------------------------------------------- swiatla: pochodnie na murach budynkow, braziery
    for (x, y) in ((4, 8), (19, 8), (22, 7), (34, 7), (41, 8), (56, 8), (59, 7), (72, 7), (6, 31), (15, 31), (22, 31),
                   (45, 31), (57, 31), (68, 31), (7, 46), (23, 46), (45, 46), (61, 46)):
        _torch(p, ox + x, oy + y, z)
    for (x, y) in ((8, 24), (20, 24), (30, 24), (46, 24), (58, 24), (70, 24), (8, 30), (30, 30), (46, 30), (70, 30),
                   (16, 43), (30, 43), (46, 43), (62, 43)):
        c = p.get(ox + x, oy + y, z)
        if c is not None and St.walk(c) and c[2] == NONE and (ox + x, oy + y) not in reserved:
            p.obj(ox + x, oy + y, z, 'campfire')
    # ---------------------------------------------------------------- bramy: zachodnia (przedsionek) i wschodnia
    for y in (26, 27, 28):
        for x in (0, 1):
            p.put(ox + x, oy + y, z, (STONE, NONE, NONE, NONE))
            p.put(ox + CW - 1 - x, oy + y, z, (STONE, NONE, NONE, NONE))
    # przedsionek zachodni: hala ze schodami z gory (sztolnie -4)
    hall = (ox - 12, oy + 22, ox - 1, oy + 32)
    for y in range(hall[1], hall[3] + 1):
        for x in range(hall[0], hall[2] + 1):
            edge = x in (hall[0], hall[2]) or y in (hall[1], hall[3])
            p.put(x, y, z, (STONE, NONE, NONE, ROCK) if edge else (STONE, NONE, NONE, NONE))
    for y in (26, 27, 28):
        p.put(hall[2], oy + y, z, (STONE, NONE, NONE, NONE))
    for (x, y) in ((hall[0] + 2, hall[1] + 2), (hall[2] - 2, hall[1] + 2), (hall[0] + 2, hall[3] - 2), (hall[2] - 2, hall[3] - 2)):
        p.obj(x, y, z, 'statue')
    for (x, y) in ((hall[0] + 5, hall[1]), (hall[0] + 5, hall[3])):
        _torch(p, x, y, z)
    spots['west_stairs'] = (hall[0] + 3, oy + 27)
    # wozkarz: wagoniki podziemnymi torami do innych miast (miasto bez wozow na powierzchni)
    npcs.append(['durn_carrier', 'Wózkarz Brodrik', 'CARRIER', CITY, hall[0] + 7, hall[1] + 2, z, '-', '#8a6a4a',
                 'Wagonikiem starymi torami pod górami do innych miast. Płacisz monetami, trzymaj brodę w środku.'])
    for (x, y, o) in ((hall[0] + 6, hall[1] + 1, 'crate'), (hall[0] + 8, hall[1] + 1, 'barrel'), (hall[0] + 9, hall[1] + 1, 'crate')):
        p.obj(x, y, z, o)
    npcs.append(['durn_guard_west', 'Strażnik bramy Hroald', 'GUARD', CITY, ox + 3, oy + 25, z, '-', '#8a8a9a',
                 'Stój! Mało kto trafia do Durnkaru przez wąwóz. Wejdź, skoro już tu jesteś, i nie zgub drogi powrotnej.'])
    npcs.append(['durn_guard_east', 'Strażnik bramy Sigvald', 'GUARD', CITY, ox + CW - 4, oy + 25, z, '-', '#8a8a9a',
                 'Za tą bramą Kopalnia Żelaznej Brody. Drzwi przepuszczą tylko tych, którzy przeżyją w głębinach.'])
    spots['east_gate'] = (ox + CW, oy + 27)
    p.pz.append((hall[0], hall[1], ox + CW - 1, oy + CH - 1, z))
    p.pz.append((ox, oy, ox + CW - 1, oy + CH - 1, z))
    return spots, npcs, houses


# ====================================================================== kopalnie
def mine_floors(p, name, adit, base, rng, connect=None, forbid=None, keep_cell=None):
    """Pietra -1..-7 kopalni: schody z adit (pole sztolni na powierzchni), pietra wokol base. connect = pole bramy
    miasta na -5 (korytarz z drzwiami poziomu). forbid(x, y, z) - pola, na ktorych pietro nie moze lezec.
    Zwraca prostokat podziemi (x0, y0, x1, y1) i liste punktow komor wg pietra."""
    up = adit
    rect = None
    out = {}
    for k in range(1, 8):
        z = -k
        w, h = 30 + 3 * k, 24 + 2 * k
        ang = rng.random() * 6.283
        x0 = base[0] - w // 2 + int(round(4 * np.cos(ang)))
        y0 = base[1] - h // 2 + int(round(3 * np.sin(ang)))
        # wejscie (schody z gory) musi lezec w prostokacie pietra
        x0 = min(max(x0, up[0] - w + 4), up[0] - 3)
        y0 = min(max(y0, up[1] - h + 4), up[1] - 3)
        n = 6 + k
        keep = (lambda x, y, zz=z: keep_cell(x, y, zz)) if keep_cell else None
        style = FLOOR_STYLE[z]
        lava = 0.03 if z == -7 else 0.0
        for attempt in range(30):
            snapshot = dict(p.f.get(z, {}))
            pts = []
            tries = 0
            while len(pts) < n and tries < 4000:
                tries += 1
                q = (rng.randint(x0 + 3, x0 + w - 4), rng.randint(y0 + 3, y0 + h - 4))
                if any(abs(q[0] - a) + abs(q[1] - b) < 8 for (a, b) in pts + [up]):
                    continue
                if forbid and (forbid(q[0], q[1], z) or forbid(q[0], q[1], z - 1)):
                    continue
                pts.append(q)
            assert len(pts) >= 4, f'{name}: no room on floor {z}'
            # schody w dol w komorze najdalej od wejscia, kolejnosc: najblizszy sasiad od wejscia
            down = max(pts, key=lambda q: abs(q[0] - up[0]) + abs(q[1] - up[1])) if k < 7 else None
            rest = [q for q in pts if q != down]
            chain = [up]
            while rest:
                nxt = min(rest, key=lambda q: abs(q[0] - chain[-1][0]) + abs(q[1] - chain[-1][1]))
                rest.remove(nxt)
                chain.append(nxt)
            if down:
                chain.append(down)
            main_len = 3 + k // 2
            trunk = chain[:main_len] + ([down] if down else [])
            trunk = [q for i, q in enumerate(trunk) if q not in trunk[:i]]
            p.cave(z, x0, y0, w, h, trunk, style, rng, room=(2, 4), lava=lava, keep=keep)
            # pozostale komory: odnogi od najblizszej komory pnia (slepe korytarze i boczne komory)
            side = [q for q in chain if q not in trunk]
            for q in side:
                anchor = min(trunk, key=lambda t: abs(t[0] - q[0]) + abs(t[1] - q[1]))
                p.cave(z, x0, y0, w, h, [anchor, q], style, rng, room=(2, 3), lava=lava, keep=keep)
            for q in [up] + ([down] if down else []):
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        c = p.get(q[0] + dx, q[1] + dy, z)
                        if c is None or not St.walk(c) or c[0] == 'lava':
                            p.put(q[0] + dx, q[1] + dy, z, (F.CAVE_STYLES[style][0], NONE, NONE, NONE))
            # wszystkie komory polaczone na tym pietrze (zachowane pola obok mogly przeciac korytarz) - inaczej od nowa
            seen = {up}
            todo = [up]
            while todo:
                a, b = todo.pop()
                for dx, dy in F.DIRS4:
                    nb = (a + dx, b + dy)
                    if nb not in seen and nb in p.f[z] and St.walk(p.get(nb[0], nb[1], z)):
                        seen.add(nb)
                        todo.append(nb)
            if all(q in seen for q in chain):
                break
            p.f[z] = snapshot
        else:
            raise AssertionError(f'{name}: floor {z} chambers not connected')
        if k == 1:
            p.stairs(-1, up, up)
        else:
            p.stairs(z, up, up)
        # potwory: w kazdej komorze poza schodami, glebiej wiecej i silniej
        mons, counts = FLOOR_MONSTERS[z]
        targets = [q for q in chain if q != up and q != down]
        order = sorted((mons[i % len(mons)] for i in range(len(targets))), key=lambda m: LEVEL[m])
        for i, q in enumerate(targets):
            m = order[i]
            cnt = counts[i % 2]
            if m in ('deep_behemot', 'stone_sentinel'):
                cnt = max(2, cnt - 1)
            p.spawn(m, q[0], q[1], z, radius=3 if k < 5 else 4, count=cnt, respawn=200 if k < 5 else 240)
        out[z] = chain
        r = (x0, y0, x0 + w - 1, y0 + h - 1)
        rect = r if rect is None else (min(rect[0], r[0]), min(rect[1], r[1]), max(rect[2], r[2]), max(rect[3], r[3]))
        if down:
            up = down
    if connect is not None:
        # korytarz z komory -5 najblizszej bramie miasta do bramy; ostatni odcinek waski, z drzwiami poziomu
        gx, gy = connect
        corridor_end = (gx + 7, gy)
        cells = [(x, gy) for x in range(gx, corridor_end[0] + 1)]
        p.corridor(-5, cells, STONE, ROCK)
        door = (gx + 3, gy)
        p.level_door(-5, door, GATE_LEVEL, floor=STONE)
        anchor = min(out[-5], key=lambda q: abs(q[0] - corridor_end[0]) + abs(q[1] - corridor_end[1]))
        ax0, ay0 = min(anchor[0], corridor_end[0]) - 3, min(anchor[1], corridor_end[1]) - 4
        ax1, ay1 = max(anchor[0], corridor_end[0]) + 3, max(anchor[1], corridor_end[1]) + 4
        mid = ((anchor[0] + corridor_end[0]) // 2, corridor_end[1] + rng.choice((-2, 2)))
        p.cave(-5, ax0, ay0, ax1 - ax0 + 1, ay1 - ay0 + 1, [corridor_end, mid, anchor], 'mine', rng, room=(1, 2),
               keep=lambda x, y: (x <= corridor_end[0] - 1 and abs(y - gy) <= 1)
               or (p.get(x, y, -5) is not None and p.get(x, y, -5)[2] in F.STAIRS))
        for (x, y) in cells:
            c = p.get(x, y, -5)
            assert c is None or c[2] not in F.STAIRS, f'{name}: gate corridor over stairs at {(x, y)}'
            if (x, y) != door:
                p.put(x, y, -5, (STONE, NONE, NONE, NONE))
        for x in range(gx, corridor_end[0] + 1):
            for y in (gy - 1, gy + 1):
                c = p.get(x, y, -5)
                if c is None or c[3] == NONE:
                    p.put(x, y, -5, (STONE, NONE, NONE, ROCK))
        # kopalnia na -5 nadal spojna bez przechodzenia przez drzwi poziomu (schody gora -> komory -> schody w dol)
        chain5 = out[-5]
        seen = {chain5[0]}
        todo = [chain5[0]]
        while todo:
            a, b = todo.pop()
            for dx, dy in F.DIRS4:
                nb = (a + dx, b + dy)
                if nb not in seen and nb != door and nb in p.f[-5] and St.walk(p.get(nb[0], nb[1], -5)):
                    seen.add(nb)
                    todo.append(nb)
        missing = [q for q in chain5 if q not in seen]
        assert not missing, f'{name}: gate corridor cut floor -5, unreachable {missing}'
        rect = (min(rect[0], gx), min(rect[1], ay0), max(rect[2], ax1), max(rect[3], ay1))
    return rect, out


def adit_cells(p, world, ox, oy, mountain_cells, outside, name):
    """Sztolnia w zboczu: pola gory od outside w glab (plan wzgledem ox, oy), schody w dol na koncu, belki przy wejsciu."""
    ground = 'dirt'
    for i, (x, y) in enumerate(mountain_cells):
        p.put(x - ox, y - oy, 0, (ground, NONE, NONE, NONE))
    ex, ey = mountain_cells[-1]
    # wejscie: belki (lumber) po bokach u wylotu, tablica
    (ax, ay) = mountain_cells[0]
    dx, dy = ax - outside[0], ay - outside[1]
    for s in (-1, 1):
        sx, sy = ax + s * dy, ay + s * dx
        c = world.get(sx, sy)
        if c is not None and c[3] in MOUNTAIN:
            pass
    o = world.get(*outside)
    p.put(outside[0] - ox, outside[1] - oy, 0, ('dirt', NONE, NONE, NONE))
    return (ex - ox, ey - oy), (outside[0] - ox, outside[1] - oy)


# ====================================================================== wejscie z generate_continent.build
def build(ctx, cost, helpers):
    world = ctx['world']
    rng = random.Random(SEED)
    reach = helpers['reach']
    core = mountain_core(world, (500, 296))
    T = core[:2]
    out = dict(core=core)
    # ---------------------------------------------------------------- sztolnie kopalni w zboczach (wschod, zachod, poludnie)
    adits = []
    for (name, d, shift, conn) in MINES:
        last, outside = edge_cell(world, T, d, reach)
        # 4 pola sztolni w glab gory od brzegu
        cells = [(last[0] - d[0] * i, last[1] - d[1] * i) for i in range(0, 4)]
        assert all(world.get(*c)[3] in MOUNTAIN for c in cells), f'{name}: adit not in the mountain {cells}'
        adits.append((name, d, shift, conn, cells, outside))
    # ---------------------------------------------------------------- ukryte wejscie: wawoz z lasu
    out_cell, path = ravine(world, T, rng, reach, cost, avoid=[a[5] for a in adits])
    H = path[-1]
    plan = St.Plan(CITY)
    ox, oy = H
    # wawoz: pola od wejscia z lasu do pieczary, poszerzony na zakretach; kamienie i kosci
    rav = set()
    for i, (x, y) in enumerate(path):
        rav.add((x, y))
        if i % 3 == 1 and 2 < i < len(path) - 3:
            nx, ny = path[i + 1]
            px, py = path[i - 1]
            if nx - px and ny - py:                           # zakret: dodatkowe pole
                rav.add((px if (px, y) not in path else x, ny))
    for (x, y) in rav:
        deco = rng.choice((NONE, NONE, NONE, 'debris', 'bones'))
        plan.put(x - ox, y - oy, 0, ('dirt', deco, NONE, NONE))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            c = world.get(H[0] + dx, H[1] + dy)
            if c[3] in MOUNTAIN:
                plan.put(dx, dy, 0, ('cave_floor', NONE, NONE, NONE))
    ent = (out_cell[0] - ox, out_cell[1] - oy)
    o = world.get(*out_cell)
    plan.put(ent[0], ent[1], 0, o)
    plan.entries.append(ent)
    # ---------------------------------------------------------------- miasto pod srodkiem gory
    cox, coy = T[0] - CW // 2 - ox, T[1] - CH // 2 - oy
    spots, npcs, houses = city_plan(plan, cox, coy, rng)
    plan.npcs += npcs
    west = spots['west_stairs']
    # ---------------------------------------------------------------- sztolnie -1..-4 od pieczary do przedsionka
    P = [(0, 0)]
    for k in range(1, 4):
        t = k / 4
        bx = int(round(west[0] * t)) + rng.randint(-6, 6)
        by = int(round(west[1] * t)) + rng.randint(-6, 6)
        P.append((bx, by))
    P.append(west)
    route_cells = {}
    for k in range(1, 5):
        z = -k
        a, b = P[k - 1], P[k]
        # kilka zakretow miedzy a i b, slepa odnoga
        mids = []
        for j in range(1, 3):
            t = j / 3
            mx = int(round(a[0] + (b[0] - a[0]) * t))
            my = int(round(a[1] + (b[1] - a[1]) * t))
            perp = (-(b[1] - a[1]), b[0] - a[0])
            ln = max(1, abs(perp[0]) + abs(perp[1]))
            off = rng.choice((-7, -5, 5, 7))
            mids.append((mx + perp[0] * off // ln, my + perp[1] * off // ln))
        chain = [a] + mids + [b]
        xs = [q[0] for q in chain]
        ys = [q[1] for q in chain]
        x0, y0, x1, y1 = min(xs) - 8, min(ys) - 8, max(xs) + 8, max(ys) + 8
        plan.cave(z, x0, y0, x1 - x0 + 1, y1 - y0 + 1, chain, 'dwarf_tunnel', rng, room=(1, 2))
        dead = (mids[0][0] + rng.choice((-6, 6)), mids[0][1] + rng.choice((-5, 5)))
        plan.cave(z, x0 - 6, y0 - 6, x1 - x0 + 13, y1 - y0 + 13, [(mids[0][0], mids[0][1]), dead], 'dwarf_tunnel', rng,
                  room=(1, 2))
        for q in (a, b):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    c = plan.get(q[0] + dx, q[1] + dy, z)
                    if c is None or not St.walk(c):
                        plan.put(q[0] + dx, q[1] + dy, z, ('cave_floor', NONE, NONE, NONE))
        route_cells[z] = {xy for xy, c in plan.f[z].items() if St.walk(c)}
    for k in range(1, 6):
        plan.stairs(-k, P[k - 1], P[k - 1])
    # ---------------------------------------------------------------- kopalnia polaczona z miastem (ten sam plan)
    name, d, shift, conn, cells, outside = adits[0]
    a_end, a_out = adit_cells(plan, world, ox, oy, cells, outside, name)
    plan.entries.append(a_out)
    city_rect = (cox - 13, coy - 1, cox + CW, coy + CH)

    gate = spots['east_gate']

    def forbid(x, y, z):
        if z == -5 and city_rect[0] - 8 <= x <= city_rect[2] + 2 and city_rect[1] - 6 <= y <= city_rect[3] + 6:
            return True
        if z == -5 and gate[0] - 2 <= x <= gate[0] + 14 and abs(y - gate[1]) <= 6:
            return True                                   # korytarz bramy z drzwiami poziomu
        cells_z = route_cells.get(z, ())
        return any((x + dx, y + dy) in cells_z for dx in range(-14, 15, 2) for dy in range(-14, 15, 2))
    near_route = {}
    for z, cs in route_cells.items():
        near_route[z] = {(x + dx, y + dy) for (x, y) in cs for dx in range(-4, 5) for dy in range(-4, 5)}

    def keep_cell(x, y, z):
        if z == -5:
            if gate[0] - 1 <= x <= gate[0] + 8 and abs(y - gate[1]) <= 1:
                return True                               # miejsce na korytarz bramy - kopalnia go nie przecina
            return city_rect[0] - 1 <= x <= city_rect[2] and city_rect[1] - 1 <= y <= city_rect[3] + 1
        return (x, y) in near_route.get(z, ())
    gate = spots['east_gate']
    base = (a_end[0] + shift[0], a_end[1] + shift[1])
    before = {z: set(plan.f.get(z, {})) for z in range(-7, 0)}
    mrect, _chains = mine_floors(plan, name, a_end, base, rng, connect=gate, forbid=forbid, keep_cell=keep_cell)
    floor_regions(plan, name, {z: set(plan.f.get(z, {})) - before[z] for z in range(-7, 0)})
    # regiony: przejscie, miasto (ostatnie = pierwsze na liscie swiata - wazniejsze od prostokata kopalni)
    floor_regions(plan, 'Sztolnie do Durnkaru', {z: {xy for xy in plan.f[z] if (xy in route_cells[z] or
                                                  any((xy[0] + a, xy[1] + b) in route_cells[z] for a, b in F.DIRS4))}
                                              for z in range(-4, 0)}, CITY_LEVELS)
    plan.regions.append((CITY, cox - 13, coy, cox + CW - 1, coy + CH - 1, -5, -5) + CITY_LEVELS)
    plan.check()
    W = St.stamp(ctx, plan, (ox, oy), helpers['mark'], per_floor=True)
    temple = W(*spots['temple'], -5) + (-5,)
    ctx['towns'].append((CITY, temple))
    ctx.setdefault('residence_only', []).append(CITY)
    for (hname, (x0, y0, x1, y1, door)) in houses:
        a, b, dd = W(x0, y0, -5), W(x1, y1, -5), W(door[0], door[1], -5)
        n = len(ctx['houses'])
        size = (x1 - x0 + 1) * (y1 - y0 + 1)
        ctx['houses'].append([f'durn_{n}', hname, CITY, a[0], a[1], b[0], b[1], -5, dd[0], dd[1], size * CITY_HOUSE_PRICE])
    out['city'] = dict(temple=temple, hidden=(ox, oy), ravine=out_cell, west=W(*west, -5) + (-5,),
                       east_gate=W(*gate, -5) + (-5,), city=W(cox, coy, -5) + (-5,))
    out['mines'] = [dict(name=name, adit=(a_out[0] + ox, a_out[1] + oy), city=True)]
    # ---------------------------------------------------------------- dwie pozostale kopalnie (osobne plany)
    for (name, d, shift, conn, cells, outside) in adits[1:]:
        mp = St.Plan(name)
        mx, my = cells[0]
        a_end, a_out = adit_cells(mp, world, mx, my, cells, outside, name)
        mp.entries.append(a_out)
        base = (a_end[0] + shift[0], a_end[1] + shift[1])
        mrect, _chains = mine_floors(mp, name, a_end, base, rng)
        floor_regions(mp, name, {z: set(mp.f.get(z, {})) for z in range(-7, 0)})
        mp.check()
        St.stamp(ctx, mp, (mx, my), helpers['mark'], per_floor=True)
        out['mines'].append(dict(name=name, adit=(a_out[0] + mx, a_out[1] + my), city=False))
    # sciezka od wylotu kazdej sztolni do najblizszej drogi (wawoz ukrytego wejscia zostaje bez drogi)
    for m in out['mines']:
        ax, ay = m['adit']
        road = helpers['nearest_road'](world, cost, (ax, ay), (ax, ay, ax, ay), limit=80)
        if road is not None:
            saved = cost[ay, ax]
            cost[ay, ax] = 1.0
            F.connect(world, ctx['T'], cost, (ax, ay), road, width=1)
            cost[ay, ax] = saved
        m['road'] = road
    ctx['dwarves'] = out
    return out
