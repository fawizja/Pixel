"""Uslugi w kazdym miescie (2026-11-06, Darek: "w kazdym miescie npc odpowiedni za kupno danego pupila, premium u krola,
krolowej albo lorda, kradziez zycia i krytyk u NPC w miastach").

Po zbudowaniu miast (generate_continent.finish, przed lupami na ladach) kazde miasto z map.properties dostaje:
  - opiekuna zwierzat (MERCHANT, klucz <miasto>_petkeeper, sklep <miasto>_pets z pupilem miasta),
  - wladce (KING: krol, krolowa albo lord; Varden ma juz krola Aurelian w zamku),
  - zaklinacza (IMBUER, klucz <miasto>_imbuer): nasycenia jak u kowala.
NPC staja wewnatrz budynkow miasta (nie w domach na sprzedaz), na wolnym polu podlogi przy scianie, poza drogami,
z dala od drzwi i schodow, nie zamykajac przejscia, osiagalni ze swiatyni i co najmniej MIN_GAP pol od innych NPC
(rozmowa wybiera najblizszego NPC w zasiegu 2 pol). Najpierw szukaja miejsca w budynku "kotwicy" (zaklinacz u kowala,
opiekun u kupca, wladca w urzedzie zarzadcy domow), potem w najblizszym innym budynku miasta.
Kolejnosc i dane sa stale - generator daje te same wspolrzedne przy kazdym uruchomieniu.
"""
from collections import deque

import features as F
import sidewalk

NONE = '-'
INTERIOR = {'stone_floor', 'sandstone_floor', 'marble_floor', 'wood_floor'}
MIN_GAP = 5          # Chebyshev do innych NPC: gracz przy innym NPC (<= 2 pola) ma nowego NPC dalej niz 2
REACH = 100          # promien poszukiwan od swiatyni (Varden: stare miasto i dzielnica krolewska)
MAX_ROOM = 400       # wieksze "wnetrza" to ulice z plyt (Qasirah), nie budynki

# miasto: prefiks kluczy, pupil, (opiekun), (wladca albo None), (zaklinacz); kotwice: klucze NPC w kolejnosci
TOWNS = [
    ('Varden', 'varden', 'pet_dog',
     ('Opiekun zwierząt Maciej', 'Szczenięta z Varden: wierne i wesołe. Pupil u boku daje +5% doświadczenia z potworów.'),
     None,
     ('Zaklinacz Teodor', 'Nasycam broń i zbroje: kradzież życia, many, krytyk, żywioły i ochrony. Kowal robi to samo.')),
    ('Frostholm', 'frost', 'pet_raven',
     ('Opiekunka zwierząt Halszka', 'Kruki z gór nie boją się mrozu. Pupil u boku daje +5% doświadczenia z potworów.'),
     ('Królowa Świętosława', 'Witaj we Frostholm. Konto premium i promocję dam ci tak jak król w Varden: napisz premium albo promocja.'),
     ('Zaklinaczka Ludwika', 'Lód trzyma moc nasyceń. Kradzież życia, many, krytyk, żywioły i ochrony - wybierz przedmiot.')),
    ('Selvaran', 'selv', 'pet_cat',
     ('Opiekun zwierząt Florian', 'Rude kocięta z portu łapią szczury i szczęście. Pupil u boku daje +5% doświadczenia z potworów.'),
     ('Lord Radosław', 'Selvaran wita gości. Konto premium i promocję dam ci tak jak król w Varden: napisz premium albo promocja.'),
     ('Zaklinacz Ignacy', 'Soki dżungli wzmacniają nasycenia. Kradzież życia, many, krytyk, żywioły i ochrony.')),
    ('Qasirah', 'qas', 'pet_fennec',
     ('Opiekun zwierząt Kasper', 'Fenki z pustyni słyszą wroga z daleka. Pupil u boku daje +5% doświadczenia z potworów.'),
     ('Król Mieszko', 'Pustynia należy do Qasirah. Konto premium i promocję dam ci tak jak król w Varden: napisz premium albo promocja.'),
     ('Zaklinaczka Zofia', 'Piasek i słońce utrwalają nasycenia. Kradzież życia, many, krytyk, żywioły i ochrony.')),
    ('Valmir', 'valmir', 'pet_gull',
     ('Opiekunka zwierząt Marianna', 'Mewy z portu znajdą każdą rybę. Pupil u boku daje +5% doświadczenia z potworów.'),
     ('Lord Bogumir', 'Port Valmir wita żeglarzy. Konto premium i promocję dam ci tak jak król w Varden: napisz premium albo promocja.'),
     ('Zaklinacz Hieronim', 'Sól morska nie szkodzi moim nasyceniom. Kradzież życia, many, krytyk, żywioły i ochrony.')),
    ('Nerys', 'nerys', 'pet_parrot',
     ('Opiekunka zwierząt Lilia', 'Papużki z wysp powtarzają każde słowo. Pupil u boku daje +5% doświadczenia z potworów.'),
     ('Królowa Jadwiga', 'Witaj na Szmaragdowych Wyspach. Konto premium i promocję dam ci tak jak król w Varden: napisz premium albo promocja.'),
     ('Zaklinaczka Aniela', 'Perły wysp niosą moc nasyceń. Kradzież życia, many, krytyk, żywioły i ochrony.')),
]
ANCHORS = {
    'petkeeper': ('merchant', 'innkeeper', 'fishmonger', 'general'),
    'ruler': ('housing', 'banker', 'priest'),
    'imbuer': ('smith', 'mage', 'archmage'),
}
COLORS = {'petkeeper': '#6a8a3a', 'ruler': '#c8a040', 'imbuer': '#7050b0'}


def _house_cells(houses):
    cells = set()
    for h in houses:
        rects = [tuple(int(v) for v in h[3:7])]
        if len(h) > 11 and h[11]:
            rects += [tuple(int(v) for v in r.split(',')) for r in str(h[11]).split('/')]
        for (x0, y0, x1, y1) in rects:
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    cells.add((x, y))
    return cells


def _room(world, start):
    """Wnetrze budynku: podloga wewnatrz (bez murow i drzwi) polaczona z polem start. Pusty zbior, gdy obszar nie jest
    zamkniety murami (dotyka terenu albo ulicy z plyt) - wtedy to nie budynek."""
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            c = world.get(*n)
            if n in seen or c is None or c[3] != NONE or c[2].startswith('door'):
                continue
            if c[0] not in INTERIOR:
                if F.walk(c) or c[0] in F.ROAD_GROUND or c[0] in ('grass', 'sand', 'snow', 'dirt', 'jungle_grass'):
                    return set(), seen
                continue
            seen.add(n)
            if len(seen) > MAX_ROOM:
                return set(), seen
            q.append(n)
    return seen, seen


def _reachable(world, start, taken):
    seen = {start}
    q = deque([start])
    sx, sy = start
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n in seen or n in taken or abs(n[0] - sx) > REACH or abs(n[1] - sy) > REACH:
                continue
            if not F.walk(world.get(*n)):
                continue
            seen.add(n)
            q.append(n)
    return seen


def _near_door_or_stairs(world, x, y):
    for dx, dy in sidewalk.RING + ((0, 0),):
        c = world.get(x + dx, y + dy)
        if c is None:
            continue
        if c[2].startswith('door') or c[3].startswith('door') or c[2] in F.STAIRS:
            return True
    return False


def place(ctx):
    world = ctx['world']
    towns = dict(ctx['towns'])
    houses = _house_cells(ctx['houses'])
    added = []

    def npc_tiles():
        return {(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0}

    for town, prefix, pet, keeper, ruler, imbuer in TOWNS:
        temple = towns[town][:2]
        for kind, spec in (('petkeeper', keeper), ('ruler', ruler), ('imbuer', imbuer)):
            if spec is None:
                continue
            key = f'{prefix}_{kind}'
            if any(n[0] == key for n in ctx['npcs']):
                continue
            taken = npc_tiles()
            reach = _reachable(world, temple, taken)
            anchors = [n for a in ANCHORS[kind] for n in ctx['npcs'] if n[0] == f'{prefix}_{a}' and int(n[6]) == 0]
            others = [(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0]

            def ok(x, y, gap):
                c = world.get(x, y)
                if c is None or (x, y) in taken or (x, y) in houses or (x, y) not in reach:
                    return False
                if c[0] not in INTERIOR or c[2] != NONE or c[3] != NONE or not F.walk(c):
                    return False
                if _near_door_or_stairs(world, x, y):
                    return False
                if any(max(abs(x - ox), abs(y - oy)) < gap for (ox, oy) in others):
                    return False
                get = lambda a, b: None if (a, b) in taken else world.get(a, b)  # noqa: E731
                if not sidewalk.keeps_passage(get, x, y):
                    return False
                # przy scianie, nie na srodku sali; obok co najmniej trzy wolne pola do podejscia
                walls = sum(1 for dx, dy in F.DIRS4 if (world.get(x + dx, y + dy) or (NONE,) * 4)[3] != NONE)
                free = sum(1 for dx, dy in F.DIRS4 if F.walk(get(x + dx, y + dy)))
                return walls >= 1 and free >= 2

            spot = None
            for gap in (MIN_GAP, MIN_GAP - 1):
                # 1) budynek kotwicy, 2) inne budynki miasta (bez domow), najblizej kotwicy albo swiatyni
                for a in anchors:
                    room = _room(world, (int(a[4]), int(a[5])))[0]
                    if room & houses:
                        continue
                    cand = sorted(p for p in room if ok(*p, gap))
                    if cand:
                        ax, ay = int(a[4]), int(a[5])
                        spot = min(cand, key=lambda p: (max(abs(p[0] - ax), abs(p[1] - ay)), p[1], p[0]))
                        break
                if spot:
                    break
                centre = (int(anchors[0][4]), int(anchors[0][5])) if anchors else tuple(temple)
                rooms_seen = set()
                best = None
                for p in sorted(reach, key=lambda p: (max(abs(p[0] - centre[0]), abs(p[1] - centre[1])), p[1], p[0])):
                    c = world.get(*p)
                    if p in rooms_seen or c is None or c[0] not in INTERIOR or p in houses:
                        continue
                    room, visited = _room(world, p)
                    rooms_seen |= visited | {p}
                    if room & houses:
                        continue
                    cand = sorted(q for q in room if ok(*q, gap))
                    if cand:
                        best = min(cand, key=lambda q: (max(abs(q[0] - centre[0]), abs(q[1] - centre[1])), q[1], q[0]))
                        break
                spot = best
                if spot:
                    break
            assert spot is not None, f'{town}: no place for {key}'
            name, greet = spec
            role = {'petkeeper': 'MERCHANT', 'ruler': 'KING', 'imbuer': 'IMBUER'}[kind]
            shop = f'{prefix}_pets' if kind == 'petkeeper' else '-'
            row = [key, name, role, town, spot[0], spot[1], 0, shop, COLORS[kind], greet]
            ctx['npcs'].append(row)
            added.append(row)
    return added
