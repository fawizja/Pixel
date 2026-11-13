"""Domy zadan w miastach (Darek 2026-11-12: "w miescie ma byc quest w stylu Tibii: PZ, wiele skrzynek, co najmniej pietro
do gory, moze tez w dol; z 20 skrzynek w miescie, randomowo w roznych miastach, nie wszystko kwadratowe").

Kazde z szesciu glownych miast (Varden, Frostholm, Qasirah, Selvaran, Valmir, Nerys) dostaje tuz za murami Dom Zadan:
nieregularna budowla (hol w ksztalcie L/T/krzyza, sale o scietych rogach i wglebieniach), dziedziniec ze strefa ochronna
(PZ) i dozorca, a w srodku DWADZIESCIA skrzyn zadan rozlozonych na pietrach: parter, pietro +1 i (zaleznie od miasta)
poddasze +2 albo piwnica -1 (jaskinia). Uklad, wielkosc i odbicie lustrzane losuje ziarno miasta.
  * hol parteru lezy w PZ; sale: otwarta, za drzwiami poziomu, ze straznikami (potwory poza PZ);
  * pietro +1: sala otwarta, sala za pieczecia zadania (quest:<klucz>), skarbiec za nia z trzema skrzyniami do wyboru
    (jedna na postac), sala ze straznikami; poddasze albo piwnica: trzy skrzynie (piwnica ze straza);
  * skrzynie to przedmioty polki poziomow miasta (items.csv: minLevel); zloto i mikstury dodaje ChestRewards wg regionu;
  * wszystkie skrzynie stoja przy polnocnej scianie pomieszczenia, wiec tabliczki (ChestSigns) leza po jednej stronie.
Dane gry (NPC, nazwy EN/ES) pisze ``python3 quest_houses.py data``. Wszystko deterministyczne.
"""
import math
import os
import random
import sys

import organic
import structures as St

NONE = '-'
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
SEED = 20261213

CW, CH = 36, 28              # plansza w polach
HALL_X0 = 9                  # hol zaczyna sie nie wczesniej niz tu (na zachod dziedziniec)
CHEST_TOTAL = 20

HOUSES = [
    dict(town='Varden', key='varden', floors=('stone_floor', 'wood_floor', 'stone_wall'), lv=(8, 30), door=10,
         quest='varden_smug2', guards=['wolf', 'forest_spider', 'goblin'], extra='cellar', items=(0, 20), cave='rock',
         place=('Dom Starego Skarbnika', "Old Treasurer's House", 'Casa del viejo tesorero'),
         npc=('Dozorca Walenty', 'Warden Valentin'),
         greet=('Skarbnik zostawił po sobie skrzynie w każdym kącie. Zajrzyj do sal, ale uważaj na straże.',
                'The treasurer left chests in every corner. Look through the rooms, but beware the guards.',
                'El tesorero dejó cofres en cada rincón. Mira las salas, pero cuidado con los guardias.')),
    dict(town='Frostholm', key='frost', floors=('wood_floor', 'wood_floor', 'timber_wall'), lv=(15, 40), door=18,
         quest='poacher_m3', guards=['snow_wolf', 'frost_boar', 'bandit'], extra='attic', items=(10, 30), cave='ice',
         place=('Dwór Skarbnika Zimy', "Winter Treasurer's Manor", 'Mansión del tesorero del invierno'),
         npc=('Dozorczyni Gunhild', 'Warden Gunhild'),
         greet=('Mróz tu nie sięga, ale skrzynie owszem. Hol jest bezpieczny, w salach czekają strażnicy.',
                'The frost does not reach inside, but the chests do. The hall is safe; guards wait in the rooms.',
                'La escarcha no llega adentro, pero los cofres sí. El vestíbulo es seguro; en las salas esperan guardias.')),
    dict(town='Qasirah', key='qas', floors=('sandstone_floor', 'sandstone_floor', 'sandstone_wall'), lv=(15, 40), door=18,
         quest='oasis_m3', guards=['hyena', 'scorpion', 'bandit'], extra='cellar', items=(10, 30), cave='rock',
         place=('Dom Piaskowego Kupca', "Sand Merchant's House", 'Casa del mercader de arena'),
         npc=('Dozorca Hakim', 'Warden Hakim'),
         greet=('Kupiec ukrył towary pod piaskiem i na piętrze. W holu nikt cię nie tknie.',
                'The merchant hid his goods under the sand and upstairs. No one will touch you in the hall.',
                'El mercader escondió sus bienes bajo la arena y arriba. En el vestíbulo nadie te tocará.')),
    dict(town='Selvaran', key='selv', floors=('wood_floor', 'wood_floor', 'wood_wall'), lv=(25, 45), door=30,
         quest='selv_exp1', guards=['crocodile', 'jungle_hunter', 'bandit'], extra='attic', items=(20, 35), cave='moss',
         place=('Dom Portowego Celnika', 'Harbour Customs House', 'Casa del aduanero del puerto'),
         npc=('Dozorca Teodoro', 'Warden Theodore'),
         greet=('Celnik trzymał w tym domu skonfiskowane skrzynie. Część jest dla każdego, część za pieczęcią.',
                'The customs officer kept confiscated chests here. Some are free for all, some sealed.',
                'El aduanero guardaba aquí cofres confiscados. Algunos son para todos, otros están sellados.')),
    dict(town='Valmir', key='valmir', floors=('stone_floor', 'wood_floor', 'plaster_wall'), lv=(30, 50), door=35,
         quest='bog_m2', guards=['marsh_lizard', 'bog_wraith', 'bandit'], extra='attic', items=(25, 40), cave='flooded',
         place=('Dom Zatopionego Kapitana', "Drowned Captain's House", 'Casa del capitán ahogado'),
         npc=('Dozorca Ambroży', 'Warden Ambrose'),
         greet=('Kapitan utonął, a jego skrzynie zostały. W holu możesz odpocząć, dalej bądź czujny.',
                'The captain drowned, his chests remain. You can rest in the hall, stay alert beyond it.',
                'El capitán se ahogó, pero sus cofres quedaron. Puedes descansar en el vestíbulo; más allá, alerta.')),
    dict(town='Nerys', key='nerys', floors=('marble_floor', 'wood_floor', 'whitewash_wall'), lv=(30, 50), door=35,
         quest='lantern_m2', guards=['lizard_hexer', 'marsh_lizard', 'bandit'], extra='cellar', items=(25, 40), cave='crystal',
         place=('Dom Perłowego Poszukiwacza', "Pearl Seeker's House", 'Casa del buscador de perlas'),
         npc=('Dozorczyni Marina', 'Warden Marina'),
         greet=('Poszukiwacz pereł zbudował dom z piwnicą pełną skrzyń. Hol należy do wszystkich, sale do odważnych.',
                'The pearl seeker built a house with a cellar full of chests. The hall belongs to all, the rooms to the brave.',
                'El buscador de perlas construyó una casa con sótano lleno de cofres. El vestíbulo es de todos, las salas de los valientes.')),
]


# ------------------------------------------------------------------------------------------------ ksztalty organiczne
def rect_cells(x0, y0, w, h):
    return {(x, y) for y in range(y0, y0 + h) for x in range(x0, x0 + w)}


def connected(cells):
    """Najwieksza spojna (4 kierunki) czesc."""
    left = set(cells)
    best = set()
    while left:
        start = next(iter(left))
        comp = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + d[0], y + d[1])
                if n in left and n not in comp:
                    comp.add(n)
                    stack.append(n)
        left -= comp
        if len(comp) > len(best):
            best = comp
    return best


def chip(cells, rng, p=0.7):
    """Sciete rogi i wglebienia na krawedziach: sala przestaje byc prostokatem."""
    cells = set(cells)
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for (cx, dx) in ((x0, 1), (x1, -1)):
        for (cy, dy) in ((y0, 1), (y1, -1)):
            if rng.random() < p:
                a, b = rng.randint(1, 2), rng.randint(1, 2)
                for i in range(a):
                    for j in range(b):
                        cells.discard((cx + dx * i, cy + dy * j))
    if x1 - x0 >= 5 and y1 - y0 >= 3 and rng.random() < 0.6:       # wglebienie w jednej scianie
        side = rng.choice('NSEW')
        n = rng.randint(2, 3)
        if side in 'NS':
            sx = rng.randint(x0 + 2, max(x0 + 2, x1 - 1 - n))
            y = y0 if side == 'N' else y1
            for i in range(n):
                cells.discard((sx + i, y))
        else:
            sy = rng.randint(y0 + 1, max(y0 + 1, y1 - 1 - n))
            x = x0 if side == 'W' else x1
            for i in range(n):
                cells.discard((x, sy + i))
    return connected(cells)


def n8(c):
    return [(c[0] + dx, c[1] + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (dx, dy) != (0, 0)]


def cheb(a, b):
    return max(abs(a[0] - b[0]), abs(a[1] - b[1]))


def hall_shape(rng, S, west):
    """Hol: kilka prostokatow (L, T, krzyz) zawierajacych punkt S; west - ramie do zachodniego wejscia."""
    sx, sy = S
    cells = rect_cells(sx - rng.randint(2, 3), sy - rng.randint(1, 2), rng.randint(5, 6), rng.randint(4, 5))
    cells |= rect_cells(sx - rng.randint(0, 2), sy - rng.randint(3, 4), 3, rng.randint(7, 9))
    if west:
        ey = sy + rng.randint(-2, 2)
        cells |= rect_cells(HALL_X0, ey - 1, sx - HALL_X0 + 1, 3)
    elif rng.random() < 0.7:
        cells |= rect_cells(sx - rng.randint(4, 6), sy + rng.randint(-1, 1), rng.randint(5, 7), 3)
    cells = {(x, y) for (x, y) in cells if HALL_X0 <= x <= CW - 3 and 3 <= y <= CH - 4}
    out = chip(cells, rng, 0.5)
    return out if S in out else out | {S}


def walls_of(sets):
    """Pola murow: sasiedzi (8 kierunkow) wnetrz, same nie bedace wnetrzem."""
    inner = set().union(*sets) if sets else set()
    return {n for c in inner for n in n8(c) if n not in inner}


class Layout:
    """Hol i sale jednego pietra: wnetrza, drzwi (pole w murze miedzy dwiema salami) i drzewo rodzicow."""

    def __init__(self, rng, hall):
        self.rng = rng
        self.rooms = {'hall': hall}
        self.parent = {}
        self.doors = {}

    def add_room(self, name, parent, size, tries=900):
        rng = self.rng
        P = self.rooms[parent]
        px = [c[0] for c in P]
        py = [c[1] for c in P]
        for _ in range(tries):
            w, h = rng.randint(*size[0]), rng.randint(*size[1])
            x0 = rng.randint(min(px) - w - 2, max(px) + 2)
            y0 = rng.randint(min(py) - h - 2, max(py) + 2)
            cells = rect_cells(x0, y0, w, h)
            if min(c[0] for c in cells) < HALL_X0 or max(c[0] for c in cells) > CW - 3:
                continue
            if min(c[1] for c in cells) < 3 or max(c[1] for c in cells) > CH - 4:
                continue
            cells = chip(cells, rng, 0.8)
            if len(cells) < w * h * 0.6 or len(cells) == w * h:
                continue
            ok = True
            for n, s in self.rooms.items():
                if any(cheb(c, d) < 2 for c in cells for d in s if abs(c[0] - d[0]) < 3 and abs(c[1] - d[1]) < 3):
                    ok = False
                    break
            if not ok:
                continue
            door = self._door(cells, P)
            if door is None:
                continue
            self.rooms[name] = cells
            self.parent[name] = parent
            self.doors[name] = door
            return cells
        return None

    def _door(self, cells, P):
        inner = set().union(*self.rooms.values()) | cells
        cands = []
        for (x, y) in cells:
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                m = (x + d[0], y + d[1])
                f = (x + 2 * d[0], y + 2 * d[1])
                if m not in inner and f in P:
                    cands.append(m)
        return self.rng.choice(sorted(cands)) if cands else None


# -------------------------------------------------------------------------------------------------------- budowa planu
def chest_spots(cells, wallset, n, rng, avoid):
    """n pol przy polnocnej scianie (nad polem mur, pod nim wolne wnetrze), rozstawionych co najmniej o 3."""
    cand = sorted(c for c in cells if (c[0], c[1] - 1) in wallset and (c[0], c[1] + 1) in cells
                  and all(cheb(c, a) >= 2 for a in avoid))
    rng.shuffle(cand)
    out = []
    for c in cand:
        if len(out) >= n:
            break
        if all(cheb(c, o) >= 2 for o in out):
            out.append(c)
    return out


def cave_chambers(rng, n, box):
    x0, y0, x1, y1 = box
    pts = []
    for _ in range(400):
        p = (rng.randint(x0, x1), rng.randint(y0, y1))
        if all(abs(p[0] - q[0]) + abs(p[1] - q[1]) >= 7 for q in pts):
            pts.append(p)
        if len(pts) == n:
            break
    return pts


def build_house(h, k, attempt):
    rng = random.Random(SEED + 211 * k + 7919 * attempt)
    town = h['town']
    fl_hall, fl_room, wall = h['floors']
    p = St.Plan(h['place'][0])
    S = (rng.randint(19, 23), rng.randint(12, 15))          # schody parter -> +1
    layouts = {}

    hall0 = hall_shape(rng, S, west=True)
    L0 = Layout(rng, hall0)
    for name in ('open', 'level', 'guard'):
        assert L0.add_room(name, 'hall', ((5, 7), (4, 5))), f'{town}: no room {name} on floor 0'
    layouts[0] = L0
    L1 = Layout(rng, hall_shape(rng, S, west=False))
    for name, parent, size in (('open', 'hall', ((5, 7), (4, 5))), ('qroom', 'hall', ((5, 6), (4, 5))),
                               ('vault', 'qroom', ((5, 6), (4, 4))), ('guard', 'hall', ((5, 7), (4, 5)))):
        assert L1.add_room(name, parent, size), f'{town}: no room {name} on floor 1'
    layouts[1] = L1
    S2 = S3 = None
    if h['extra'] == 'attic':
        hall1 = L1.rooms['hall']
        far = [c for c in hall1 if cheb(c, S) >= 4 and c[1] > 4 and (c[0], c[1] + 1) in hall1 and (c[0], c[1] - 1) in hall1]
        assert far, f'{town}: no stairs spot to the attic'
        S2 = rng.choice(sorted(far))
        layouts[2] = Layout(rng, hall_shape(rng, S2, west=False))
    else:
        c0 = [c for c in hall0 if cheb(c, S) >= 4 and (c[0], c[1] + 1) in hall0 and (c[0], c[1] - 1) in hall0
              and c[0] > HALL_X0 + 2]
        assert c0, f'{town}: no stairs spot to the cellar'
        S3 = rng.choice(sorted(c0))
    stairs_xy = [s for s in (S, S2, S3) if s]

    for z, L in layouts.items():
        sets = list(L.rooms.values())
        L.walls = walls_of(sets)
        for name, cells in L.rooms.items():
            fl = fl_hall if (z == 0 and name == 'hall') else fl_room
            for c in cells:
                p.put(c[0], c[1], z, (fl, NONE, NONE, NONE))
        for c in L.walls:
            p.put(c[0], c[1], z, (fl_room, NONE, NONE, wall))
    hx0 = min(c[0] for c in hall0)
    rows = sorted(c[1] for c in hall0 if c[0] == hx0)
    ey = rows[len(rows) // 2]
    ent = (hx0 - 1, ey)
    p.put(ent[0], ent[1], 0, (fl_hall, NONE, 'door_open', NONE))
    gates = []
    for z, L in layouts.items():
        for name, door in L.doors.items():
            if z == 0 and name == 'level':
                p.level_door(0, door, h['door'], floor=fl_room)
            elif z == 1 and name == 'qroom':
                p.level_door(1, door, h['door'], floor=fl_room)
                gates.append((door, 1, 'quest:' + h['quest']))
            else:
                p.put(door[0], door[1], z, (fl_room, NONE, 'door_open', NONE))
    p.stairs(0, S, S)
    if S2:
        p.stairs(1, S2, S2)

    quota = {0: {'open': 3, 'level': 3, 'guard': 2}, 1: {'open': 2, 'qroom': 2, 'guard': 2}}
    chosen = []
    for z, qs in quota.items():
        L = layouts[z]
        avoid = list(stairs_xy) + list(L.doors.values()) + ([ent] if z == 0 else [])
        for name, n in qs.items():
            spots = chest_spots(L.rooms[name], L.walls, n, rng, avoid + [c[:2] for c in chosen if c[2] == z])
            assert len(spots) == n, f'{town}: floor {z} room {name}: {len(spots)} of {n} chest spots'
            chosen += [(x, y, z, name) for (x, y) in spots]
    avoid1 = list(stairs_xy) + list(L1.doors.values()) + [c[:2] for c in chosen if c[2] == 1]
    vault = chest_spots(L1.rooms['vault'], L1.walls, 3, rng, avoid1)
    assert len(vault) == 3, f'{town}: vault chest spots {len(vault)}'
    if S2:
        L2 = layouts[2]
        spots = chest_spots(L2.rooms['hall'], L2.walls, 3, rng, [S2])
        assert len(spots) == 3, f'{town}: attic spots {len(spots)}'
        chosen += [(x, y, 2, 'attic') for (x, y) in spots]
    guards_cells = []
    if S3:
        cx0, cy0 = 9, 4
        pts = [(rng.randint(13, 16), rng.randint(8, 20))] + cave_chambers(rng, 3, (18, 6, 31, 23))
        assert len(pts) == 4, f'{town}: cave chambers'
        p.cave(-1, cx0, cy0, CW - cx0, CH - cy0, pts, h['cave'], rng, room=(2, 3))
        p.stairs(-1, pts[0], S3)
        cave = {c for c, v in p.f[-1].items() if St.walk(v) and v[2] == NONE}
        wl = {c for c, v in p.f[-1].items() if v[3] != NONE}
        for q in pts[1:]:
            near = sorted((cheb(c, q), c) for c in cave if cheb(c, q) <= 3)
            guards_cells.append(near[0][1] if near else q)
        spots = chest_spots(cave, wl, 3, rng, [pts[0]])
        assert len(spots) == 3, f'{town}: cave chest spots {len(spots)}'
        chosen += [(x, y, -1, 'cellar') for (x, y) in spots]
    items = pick_items(h, rng)
    for (x, y, z, _r), item in zip(chosen, items):
        p.chests.append((x, y, z, 200, item, 1))
    for (x, y), item in zip(vault, choice_items(h)):
        p.chests.append((x, y, 1, 0, item, 1))
    assert len(p.chests) == CHEST_TOTAL, f'{town}: {len(p.chests)} chests'

    taken = [(c[0], c[1]) for c in p.chests]
    for z, L in layouts.items():
        for name, cells in L.rooms.items():
            cand = sorted(c for c in cells if (c[0], c[1] - 1) in L.walls and p.get(c[0], c[1], z)[2] == NONE)
            rng.shuffle(cand)
            put = 0
            for c in cand:
                if put >= 2:
                    break
                if all(cheb(c, t) >= 3 for t in taken) and all(cheb(c, s) >= 3 for s in stairs_xy) \
                        and all(cheb(c, d) >= 2 for d in list(L.doors.values()) + [ent]):
                    p.obj(c[0], c[1], z, 'wall_torch')
                    taken.append(c)
                    put += 1

    yard = set()
    cx, cy = ent[0] - 3, ey
    for y in range(cy - 6, cy + 7):
        for x in range(1, ent[0]):
            d = ((x - cx) / 3.6) ** 2 + ((y - cy) / 4.2) ** 2
            if d <= 1.0 + 0.35 * (rng.random() - 0.5) and (x, y) not in hall0:
                yard.add((x, y))
    for x in range(cx, ent[0]):
        yard.add((x, ey))
    yard = connected(yard)
    assert (ent[0] - 1, ey) in yard
    for (x, y) in yard:
        p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    npc_at = (ent[0] - 2, ey + 1) if (ent[0] - 2, ey + 1) in yard else (ent[0] - 2, ey)
    p.npcs.append([f"house_{h['key']}_warden", h['npc'][0], 'GUIDE', h['place'][0], npc_at[0], npc_at[1], 0, '-', '#b89050', h['greet'][0]])
    for r in organic.rect_cover(yard | hall0 | {ent}):
        p.pz.append((*r, 0))
    p.entries.append((ent[0] - 2, ey))

    g = h['guards']
    for z, L in layouts.items():
        if 'guard' in L.rooms:
            cells = sorted(L.rooms['guard'])
            for i in range(2):
                c = cells[(len(cells) * (2 * i + 1)) // 4]
                p.spawn(g[(i + z) % len(g)], c[0], c[1], z, radius=2, count=2, respawn=160)
    for i, c in enumerate(guards_cells):
        p.spawn(g[i % len(g)], c[0], c[1], -1, radius=2, count=2, respawn=170)

    lo, hi = h['lv']
    for z, cells in sorted(p.f.items()):
        if z >= 0:                              # region po obrysie (rect_cover), nie po calej planszy
            for (a, b, c, d) in organic.rect_cover(set(cells)):
                p.regions.append((h['place'][0], a, b, c, d, z, z, lo, hi))
        else:
            xs = [c[0] for c in cells]
            ys = [c[1] for c in cells]
            p.regions.append((cellar_name(h, 0), min(xs), min(ys), max(xs), max(ys), z, z, lo, hi))
    p.check()
    meta = dict(gates=gates, quest=h['quest'], choice_group=f"house_{h['key']}", vault=vault, ent=ent,
                top=max(layouts), cellar=bool(S3))
    return p, meta


def cellar_name(h, lang):
    return h['place'][lang] + (' (piwnica)', ' (cellar)', ' (sótano)')[lang]


_ITEMS = None


def items_table():
    global _ITEMS
    if _ITEMS is None:
        rows = []
        for ln in open(os.path.join(RES, 'data', 'items.csv'), encoding='utf-8'):
            f = ln.rstrip('\n').split(';')
            if ln.startswith('#') or len(f) < 12:
                continue
            rows.append(dict(key=f[1], cat=f[3], slot=f[6], lv=int(f[10] or 0), voc=f[11]))
        _ITEMS = rows
    return _ITEMS


def pool(h):
    lo, hi = h['items']
    out = [r for r in items_table() if r['cat'] in ('WEAPON', 'ARMOR', 'SHIELD') and r['slot'] not in ('NONE', 'AMMO')
           and lo <= r['lv'] <= hi and 'grimtooth' not in r['key'] and r['key'] != 'fishing_rod']
    return sorted(out, key=lambda r: r['key'])


def pick_items(h, rng):
    pl = [r['key'] for r in pool(h)]
    rng.shuffle(pl)
    out = pl[:17]
    while len(out) < 17:
        out.append(rng.choice(pl))
    return out


def choice_items(h):
    """Trzy przedmioty do wyboru: najlepszy z polki dla wojownika, lucznika i maga."""
    pl = pool(h)
    out = []
    for voc in (('KNIGHT',), ('PALADIN',), ('SORCERER', 'DRUID')):
        c = [r for r in pl if any(v in r['voc'] for v in voc) and r['voc'] != '*']
        c.sort(key=lambda r: (-r['lv'], r['key']))
        out.append(c[0]['key'] if c else pl[0]['key'])
    return out


# ---------------------------------------------------------------------------------------- lustro i umieszczenie
def mirror(p, fx, fy):
    def T(x, y):
        return (CW - 1 - x if fx else x, CH - 1 - y if fy else y)
    q = St.Plan(p.name)
    for z, cells in p.f.items():
        for (x, y), c in cells.items():
            q.put(*T(x, y), z, c)
    q.trans = [(*T(t[0], t[1]), t[2], t[3], *T(t[4], t[5]), t[6]) for t in p.trans]
    q.spawns = [(m, *T(x, y), z, r, n, rs) for (m, x, y, z, r, n, rs) in p.spawns]
    q.chests = [(*T(c[0], c[1]), *c[2:]) for c in p.chests]
    q.doors = [(*T(d[0], d[1]), *d[2:]) for d in p.doors]
    for (x0, y0, x1, y1, z) in p.pz:
        a, b = T(x0, y0), T(x1, y1)
        q.pz.append((min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1]), z))
    for n in p.npcs:
        a = T(n[4], n[5])
        q.npcs.append([*n[:4], a[0], a[1], *n[6:]])
    for r in p.regions:
        a, b = T(r[1], r[2]), T(r[3], r[4])
        q.regions.append((r[0], min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1]), *r[5:]))
    q.entries = [T(*e) for e in p.entries]
    return q, T


TREES = {'tree', 'pine', 'palm', 'bush', 'rock', 'cactus', 'snow_rock', 'sandstone_rock', 'dead_tree', 'jungle_tree', 'jungle_bush',
         'frost_pine', 'frost_bush', 'dry_bush', 'autumn_tree', 'burnt_tree', 'sandstone_rock', 'obsidian_rock', 'ice_crystal'}


def town_site(ctx, helpers, plan0, temple, rng, k, radius=100):
    """Miejsce w miescie albo na jego skraju: najblizsze swiatyni, gdzie caly obrys planu (z 1 polem zapasu) lezy na wolnym
    terenie (trawa, drzewa i krzewy do wyciecia), z dala od budynkow, dróg, stref ochronnych, NPC i przejsc. Probuje
    czterech odbic lustrzanych. Zwraca (plan, T, origin, (fx, fy)) albo None."""
    import numpy as np
    world = ctx['world']
    natural = helpers['natural']
    tx, ty = temple
    x0, y0 = max(5, tx - radius - 30), max(5, ty - radius - 30)
    x1, y1 = min(len(world.floors[0][0]) - 6, tx + radius + 30), min(len(world.floors[0]) - 6, ty + radius + 30)
    W, H = x1 - x0 + 1, y1 - y0 + 1
    free = np.zeros((H, W), bool)
    g = world.floors[0]
    for y in range(y0, y1 + 1):
        row = g[y]
        for x in range(x0, x1 + 1):
            if natural(row[x]):
                free[y - y0, x - x0] = True

    def block(ax0, ay0, ax1, ay1, m):
        free[max(0, ay0 - m - y0):max(0, ay1 + m + 1 - y0), max(0, ax0 - m - x0):max(0, ax1 + m + 1 - x0)] = False
    for (a, b, c, d, z) in ctx['pz']:
        if z == 0:
            block(a, b, c, d, 3)
    for r in ctx['houses']:
        if len(r) > 7 and str(r[7]) == '0':
            block(int(r[3]), int(r[4]), int(r[5]), int(r[6]), 4)
    for r in ctx.get('regions_new', []):               # miejsca specjalne (wzgorza, twierdze, obozy): bez domu zadan
        if r[5] <= 0 <= r[6]:
            block(r[1], r[2], r[3], r[4], 4)
    for r in ctx.get('reserved', []):
        block(r[0], r[1], r[2], r[3], 2)
    for t in ctx['transitions']:
        if t[2] == 0:
            block(t[0], t[1], t[0], t[1], 3)
            block(t[4], t[5], t[4], t[5], 3)
    for n in ctx['npcs']:
        if int(n[6]) == 0:
            block(int(n[4]), int(n[5]), int(n[4]), int(n[5]), 3)
    for ln in ctx['spawns']:
        f = ln.split(';')
        if not ln.startswith('#') and len(f) > 3 and f[3] == '0':
            block(int(f[1]), int(f[2]), int(f[1]), int(f[2]), 2)
    for zz in world.floors:
        if zz > 0:
            for y in range(y0, y1 + 1, 2):
                for x in range(x0, x1 + 1, 2):
                    if world.floors[zz][y][x] is not None:
                        block(x, y, x, y, 2)
    flips = [(a, b) for a in (False, True) for b in (False, True)]
    rng2 = random.Random(SEED + 77 * k)
    rng2.shuffle(flips)
    best = None
    offs = sorted(((dx, dy) for dx in range(-radius, radius + 1, 2) for dy in range(-radius, radius + 1, 2)),
                  key=lambda d: d[0] * d[0] + d[1] * d[1])
    for (fx, fy) in flips:
        plan, T = mirror(plan0, fx, fy)
        cells = np.array(sorted(plan.f[0].keys()))
        cx, cy = int(cells[:, 0].mean()), int(cells[:, 1].mean())
        # zapas 1 pola dookola
        mask = {(x + dx, y + dy) for (x, y) in plan.f[0] for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
        mxy = np.array(sorted(mask))
        for (dx, dy) in offs:
            ox, oy = tx + dx - cx, ty + dy - cy
            if best is not None and dx * dx + dy * dy >= best[0]:
                break
            xs, ys = mxy[:, 0] + ox - x0, mxy[:, 1] + oy - y0
            if xs.min() < 0 or ys.min() < 0 or xs.max() >= W or ys.max() >= H:
                continue
            if free[ys, xs].all():
                best = (dx * dx + dy * dy, plan, T, (ox, oy), (fx, fy))
                break
    if best is None:
        return None
    return best[1], best[2], best[3], best[4]


def link_to_town(ctx, W, T, meta):
    """Sciezka od dziedzinca do najblizszej drogi (drzewa i krzewy na trasie wycinane)."""
    import features as F
    world = ctx['world']
    ent = W(*T(*meta['ent']), 0)
    ex, ey = ent
    # dziedziniec leży przed wejściem: start w sasiednim polu poza murem budynku
    starts = [(ex + dx, ey + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
    start = None
    for s_ in starts:
        c = world.get(s_[0], s_[1])
        if c is not None and F.walk(c):
            start = s_
            break
    if start is None:
        return 0
    from collections import deque
    prev = {start: None}
    dq = deque([start])
    end = None
    while dq:
        p = dq.popleft()
        c = world.get(p[0], p[1])
        if p != start and c[0] in F.ROAD_GROUND and F.walk(c) and abs(p[0] - start[0]) + abs(p[1] - start[1]) > 6:
            end = p
            break
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (p[0] + dx, p[1] + dy)
            if n in prev or abs(n[0] - start[0]) > 70 or abs(n[1] - start[1]) > 70:
                continue
            cn = world.get(n[0], n[1])
            if cn is None or cn[3] != NONE or cn[0] in ('water', 'deep_water', 'lava', 'swamp', 'chasm'):
                continue
            if not (F.walk(cn) or cn[2] in TREES):
                continue
            prev[n] = p
            dq.append(n)
    if end is None:
        return 0
    n = 0
    p = end
    while p is not None:
        c = world.get(p[0], p[1])
        if c[2] in TREES:
            world.put(p[0], p[1], 0, (c[0], c[1], NONE, NONE))
            n += 1
        p = prev[p]
    return n


def build(ctx, cost, helpers):
    import story_quests as SQ
    world = ctx['world']
    mark = helpers['mark']
    towns = dict(ctx['towns'])
    out = []
    ctx.setdefault('reward_reqs', [])
    ctx.setdefault('star_choices', [])
    ctx.setdefault('star_gates', [])
    ctx.setdefault('quest_houses', [])
    for k, h in enumerate(HOUSES):
        plan = meta = last = None
        for attempt in range(600):
            try:
                plan, meta = build_house(h, k, attempt)
                break
            except AssertionError as e:
                last = e
        assert plan is not None, f'{h["town"]}: quest house not built: {last}'
        rng = random.Random(SEED + 31 * k)
        fx, fy = rng.random() < 0.5, rng.random() < 0.5
        plan0 = plan
        temple = towns[h['town']][:2]
        site = town_site(ctx, helpers, plan0, temple, rng, k)
        if site is None:
            plan, T = mirror(plan0, fx, fy)
            gate_cells = [(T(*g[0]), g[1], g[2]) for g in meta['gates']]
            vault = [T(*v) for v in meta['vault']]
        else:
            plan, T, origin, (fx, fy) = site
            gate_cells = [(T(*g[0]), g[1], g[2]) for g in meta['gates']]
            vault = [T(*v) for v in meta['vault']]
            tx, ty = origin[0] - 3, origin[1] - 3
            w, hgt = CW + 6, CH + 6
        if site is None:
            hh = dict(helpers, reach=SQ._reach(world, temple))
            w, hgt = CW + 6, CH + 6
            best = None
            for i in range(8):                      # zapas: najblizsze wolne miejsce wsrod osmiu kierunkow
                ang = (rng.random() + i) * 6.283 / 8
                near = (int(temple[0] + 62 * math.cos(ang)), int(temple[1] + 62 * math.sin(ang)))
                try:
                    cand = St.find_site(ctx, hh, cost, near, w, hgt, radius=200, min_temple=34, max_water=0.12)
                except AssertionError:
                    continue
                d = math.hypot(cand[0] + w / 2 - temple[0], cand[1] + hgt / 2 - temple[1])
                if best is None or d < best[0]:
                    best = (d, cand)
            assert best is not None, f'{h["town"]}: no site for the quest house'
            tx, ty = best[1]
            origin = (tx + 3, ty + 3)
        St.clear_surface(ctx, tx - 2, ty - 2, tx + w + 1, ty + hgt + 1)
        W = St.stamp(ctx, plan, origin, mark)
        linked = link_to_town(ctx, W, T, meta) if site is not None else -1
        for (xy, z, req) in gate_cells:
            a = W(xy[0], xy[1], z)
            ctx['star_gates'].append((a[0], a[1], z, req))
        grp = meta['choice_group']
        loot = {(c[0], c[1], c[2]): c for c in ctx['loot_chests']}
        for (x, y) in vault:
            a = W(x, y, 1)
            c = loot[(a[0], a[1], 1)]
            ctx['loot_chests'].remove(c)          # skrzynia wyboru nie jest skrzynia lupu
            ctx['star_choices'].append((grp, a[0], a[1], 1, c[4]))
        ctx['reward_reqs'].append((grp, 'quest:' + meta['quest']))
        ent = W(*T(*meta['ent']), 0)
        rec = dict(key=h['key'], town=h['town'], origin=origin, ent=ent, chests=CHEST_TOTAL, floors=meta['top'],
                   cellar=meta['cellar'], flip=(fx, fy), site=(tx, ty), in_town=site is not None, cleared=linked,
                   dist=round(math.hypot(ent[0] - temple[0], ent[1] - temple[1])))
        out.append(rec)
        ctx['quest_houses'].append(rec)
    return out


# ------------------------------------------------------------------------------------------------------- dane gry
BEGIN, END = '# BEGIN quest_houses (tools/mapgen/quest_houses.py data)', '# END quest_houses'


def _splice(path, lines):
    text = open(path, encoding='utf-8').read()
    if BEGIN in text:
        a = text.index(BEGIN)
        b = text.index(END) + len(END)
        text = text[:a].rstrip('\n') + '\n' + text[b:].lstrip('\n')
    text = text.rstrip('\n') + '\n' + BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    open(path, 'w', encoding='utf-8').write(text)


def write_data():
    en, es, dlg = [], [], []
    for h in HOUSES:
        key = f"house_{h['key']}_warden"
        for idx, rows in ((1, en), (2, es)):
            rows.append(f"place;{h['place'][0]};name;{h['place'][idx]}")
            rows.append(f"place;{cellar_name(h, 0)};name;{cellar_name(h, idx)}")
            rows.append(f"npc;{key};name;{h['npc'][1]}")
            rows.append(f"npc;{key};greeting;{h['greet'][idx]}")
        dlg.append(f"{key};dom,skrzynie,skrzynia,nagroda,pietro,piwnica,straznicy;"
                   f"Skrzynie stoją przy ścianach, każda ma tabliczkę z opisem. Hol jest strefą ochronną. Część sal otwiera "
                   f"poziom {h['door']}, a sala na piętrze zadanie. W salach ze strażnikami nie ma ochrony.")
    _splice(os.path.join(RES, 'i18n', 'data_en.csv'), en)
    _splice(os.path.join(RES, 'i18n', 'data_es.csv'), es)
    _splice(os.path.join(RES, 'data', 'dialogues.csv'), dlg)
    print('quest house data written:', len(HOUSES))


if __name__ == '__main__':
    if sys.argv[1:] == ['data']:
        write_data()
