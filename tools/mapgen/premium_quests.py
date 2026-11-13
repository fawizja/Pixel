"""17 nowych linii zadan premium (Darek 2026-11-13: "30 darmowych i 50 premium zlozonych, powiazanych questow jak w Canary").

Szesnascie miejsc (zakon dwoch frakcji to jedno miejsce z dwiema liniami) na ladach premium wokol Valmir, Selvaran i Nerys:
dziedziniec ze strefa ochronna i zleceniodawca (NPC z angielskim imieniem), nieregularny budynek z drzwiami poziomu i schodami
w dol oraz podziemie z nieregularnych komor (ksztalt z falujacym brzegiem, zadnych prostokatow) polaczonych wasami
korytarzami. Bramy w korytarzach to pola z drzwiami poziomu i wymaganiem z ``star_quest.csv``: ``quest:``, ``boss:``, ``party:N``
i ``flag:`` (dzwignie ``lever;znacznik;x;y;z``), skladane znakiem ``+``. Szablony i mechanika: ``premium_data.py``.
Dane gry (quests, chains, lines, requires, dialogues, potwory, tlumaczenia) pisze ``python3 premium_quests.py data``.
"""
import math
import os
import random
import re
import sys
from collections import deque

import features as F
import islands5 as I5
import structures as St
import premium_data as D

NONE = '-'
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
SEED = 20261313

# bossowie stawiani tylko recznie (generator nie wybiera ich do lochow dziczy ani jako zamiennikow)
St.HAND_PLACED.update(D.BOSSES)

STYLE_OF = {'drown': 'flooded', 'pit': 'mine', 'oakgrove': 'moss', 'fleet': 'ruin', 'toll': 'flooded', 'pendulum': 'ruin',
            'cells': 'crypt', 'sunorder': 'moss', 'seals': 'flooded', 'sandrite': 'sand', 'throne': 'fire', 'quarters': 'crypt',
            'ember': 'fire', 'trials': 'fire', 'storm': 'ice', 'mirror': 'crystal'}
# dodatkowa brama drużynowa przed ostatnia sala (party:N laczone znakiem + z reszta wymogu); Canary: ostatnie pokoje wymagaja grupy
PARTY_GATES = {'trials': 3, 'quarters': 2, 'throne': 5, 'mirror': 2}
FLOOR_WALL = {'sand': ('sandstone_floor', 'sandstone_wall', ['bones', 'cobweb'])}


def style_cells(key):
    st = STYLE_OF.get(key, 'ruin')
    if st in FLOOR_WALL:
        return FLOOR_WALL[st]
    f, w, free, _blk, _light = F.CAVE_STYLES[st]
    return f, w, free


def level_cost(level):
    def tot(L):
        return 50 * (L ** 3 - 6 * L ** 2 + 17 * L - 12) // 3
    return tot(level + 1) - tot(level)


def tier_of(level):
    return 'easy' if level < 30 else 'medium' if level < 60 else 'mediumhard' if level < 120 else 'hard' if level < 250 else 'extreme'


TIER_EXP = {'easy': 1.0, 'medium': 1.2, 'mediumhard': 1.2, 'hard': 1.2, 'extreme': 2.0}


# ------------------------------------------------------------------------------------------------ misje linii
def all_lines():
    """Linie jako slowniki z kluczem, szablonem i lista kroków: (rodzaj, indeks tekstu, potwor/boss, ilosc)."""
    out = []
    for q in D.LINES:
        if q['tpl'] == 'faction':
            a = dict(q, fkey=q['key'], other=D.FACTION_B_KEY, side='a')
            b = dict(q, key=D.FACTION_B_KEY, fkey=D.FACTION_B_KEY, other=q['key'], side='b', line=q['line_b'], npc=q['npc_b'],
                     greet=q['greet_b'], story=q['story_b'], mins=q['mins_b'], bosses=q['bosses_b'])
            out += [a, b]
        else:
            out.append(dict(q))
    for q in out:
        q['steps'] = steps_of(q)
    return out


def steps_of(q):
    t, m, b = q['tpl'], q['mins'], q['bosses']
    g = lambda i: ('KILL', 'guard%d' % (i + 1), m[i][0], m[i][1])
    boss = lambda i: ('KILL', 'boss%d' % (i + 1), b[i], 1)
    visit = ('VISIT', 'visit', None, 1)
    if t in ('lever', 'team', 'ritual'):
        return [visit, g(0), g(1), boss(0)]
    if t == 'chain':
        return [visit, boss(0), boss(1), boss(2), ('KILL', 'guard1', m[2][0], m[2][1])]
    if t in ('wrath', 'quarters'):
        return [visit, g(0), g(1), g(2), g(3), boss(0)]
    if t == 'faction':
        return [visit, g(0), boss(0)]
    if t == 'arena':
        return [visit, g(0), g(1), g(2), boss(0)]
    if t == 'seals':
        return [visit, g(0), g(1), boss(0)]
    raise KeyError(t)


def mk(q, i):
    return f"{q['key']}_m{i}"


def inner_name(q, lang=0):
    return f"{q['place'][lang]} ({('głębiny', 'depths', 'profundidades')[lang]})"


# --------------------------------------------------------------------------------------------- budowa komor
class Ch:
    def __init__(self, name, center, cells, interior, ring):
        self.name, self.center, self.cells, self.interior, self.ring = name, center, cells, interior, ring


_TILE_KINDS = {}


def _tile_kinds():
    if not _TILE_KINDS:
        for ln in open(os.path.join(RES, 'data', 'tiles.csv'), encoding='utf-8'):
            f = ln.strip().split(';')
            if len(f) > 4 and not ln.startswith('#'):
                _TILE_KINDS[f[1]] = (f[2], f[3])
    return _TILE_KINDS


class Dungeon:
    """Podziemie z komor o nieregularnym obrysie (falujaca elipsa) polaczonych korytarzami; bramy to drzwi poziomu."""

    def __init__(self, p, rng, key, z=-1):
        self.p, self.rng, self.z = p, rng, z
        self.floor, self.wall, free = style_cells(key)
        self.free = list(free) or ['debris', 'bones']
        self.ch = {}
        self.gates = []          # (x, y, z, wymaganie)
        self.gate_cells = []
        self.reserved = set()
        self.levers = []         # (flag, x, y, z)

    def chamber(self, name, cx, cy, rx, ry, flat=False, amp=0.2):
        cells = I5.shift(I5.noisy(self.rng, rx, ry, amp), cx, cy)
        if flat:
            bottom = cy + ry - 3
            cells = {c for c in cells if c[1] <= bottom}
        for other in self.ch.values():
            near = {(x + dx, y + dy) for (x, y) in other.cells for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)}
            assert not (cells & near), f'{name} overlaps {other.name}'
        interior, ring = I5.room(self.p, self.z, cells, self.floor, self.wall)
        ch = Ch(name, (cx, cy), cells, interior, ring)
        self.ch[name] = ch
        return ch

    def _path(self, a, b, vertical_first=False):
        (ax, ay), (bx, by) = self.ch[a].center, self.ch[b].center
        path = []
        if vertical_first:
            path += [(ax, y) for y in self._rng(ay, by)]
            path += [(x, by) for x in self._rng(ax, bx)]
        else:
            path += [(x, ay) for x in self._rng(ax, bx)]
            path += [(bx, y) for y in self._rng(ay, by)]
        seen, out = set(), []
        for c in path:
            if c not in seen:
                seen.add(c)
                out.append(c)
        return out

    @staticmethod
    def _rng(a, b):
        return range(a, b + 1) if b >= a else range(a, b - 1, -1)

    def link(self, a, b, req=None, level=1, vertical_first=False):
        """Korytarz miedzy komorami; req - brama (drzwi poziomu + wymaganie) na prostym odcinku poza komorami."""
        path = self._path(a, b, vertical_first)
        for n_, other in self.ch.items():
            if n_ not in (a, b):
                assert not (set(path) & other.cells), f'corridor {a}-{b} crosses {n_}'
        self.p.corridor(self.z, path, self.floor, self.wall)
        if req:
            cell = self._gate_cell(path, a, b)
            self.p.level_door(self.z, cell, level, floor=self.floor)
            self.gates.append((cell[0], cell[1], self.z, req))
            self.gate_cells.append(cell)
            return cell
        return None

    def _gate_cell(self, path, a, b):
        occupied = self.ch[a].cells | self.ch[b].cells
        for other in self.ch.values():
            occupied |= other.cells
        idx = [i for i in range(1, len(path) - 1) if path[i] not in occupied]
        good = [i for i in idx if (path[i - 1][0] - path[i][0], path[i - 1][1] - path[i][1]) ==
                (path[i][0] - path[i + 1][0], path[i][1] - path[i + 1][1])
                and path[i - 1] not in occupied | self.reserved and path[i + 1] not in occupied | self.reserved]
        assert good, f'no gate cell between {a} and {b}'
        return path[good[len(good) // 2]]

    # ---- zawartosc
    def free_cells(self, name):
        p = self.p
        out = []
        for (x, y) in self.ch[name].interior:
            c = p.get(x, y, self.z)
            if c is None or not St.walk(c) or c[2] != NONE:
                continue
            if (x, y) in self.reserved or any(abs(x - g[0]) + abs(y - g[1]) < 4 for g in self.gate_cells):
                continue
            out.append((x, y))
        return out

    def spots(self, name, k, gap=5, avoid=()):
        cells = self.free_cells(name)
        cx, cy = self.ch[name].center
        cells = [c for c in cells if all(abs(c[0] - a[0]) + abs(c[1] - a[1]) >= 3 for a in avoid)
                 and sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (c[0] + dx, c[1] + dy) in self.ch[name].interior) >= 7]
        self.rng.shuffle(cells)
        out = []
        for c in cells:
            if len(out) >= k:
                break
            if all(abs(c[0] - o[0]) + abs(c[1] - o[1]) >= gap for o in out):
                out.append(c)
        return out

    def populate(self, mon, chambers, need, avoid=(), per=3, respawn=150):
        """Spawny potwora w komorach: laczna liczba >= need * 1.5 (misja zabijania wymaga ilosci wsrod spawnow)."""
        want = int(math.ceil(need * 1.5 / per))
        total = 0
        spots_by = {n: self.spots(n, 99, 5, avoid) for n in chambers}
        i = 0
        while want > 0 and any(spots_by.values()):
            n = chambers[i % len(chambers)]
            i += 1
            if not spots_by[n]:
                continue
            x, y = spots_by[n].pop(0)
            self.p.spawn(mon, x, y, self.z, radius=3, count=per, respawn=respawn)
            self.reserved |= {(x + dx, y + dy) for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)}
            total += per
            want -= 1
        assert total >= need, f'{mon}: only {total} spawns for {need}'
        return total

    def place_obj(self, name, obj, near=None, min_free=6):
        """Obiekt (dzwignia, oltarz) na polu wnetrza komory: z co najmniej min_free wolnymi sasiadami."""
        ch = self.ch[name]
        cx, cy = near or (ch.center[0], ch.center[1] - 3)
        best = None
        for (x, y) in ch.interior:
            c = self.p.get(x, y, self.z)
            if c is None or not St.walk(c) or c[2] != NONE or (x, y) in self.reserved:
                continue
            free = sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1) if (dx or dy) and (x + dx, y + dy) in ch.interior
                       and St.walk(self.p.get(x + dx, y + dy, self.z)) and self.p.get(x + dx, y + dy, self.z)[2] == NONE)
            if free < min_free or any(abs(x - g[0]) + abs(y - g[1]) < 3 for g in self.gate_cells):
                continue
            d = abs(x - cx) + abs(y - cy)
            if best is None or d < best[0]:
                best = (d, x, y)
        assert best, f'no spot for {obj} in {name}'
        self.p.obj(best[1], best[2], self.z, obj)
        self.reserved |= {(best[1] + dx, best[2] + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
        return best[1], best[2]

    def lever(self, name, flag, near=None):
        x, y = self.place_obj(name, 'lever', near)
        self.levers.append((flag, x, y, self.z))
        return x, y

    def vault(self, name, cx, cy, n, items):
        """Skarbiec: komora z prosta poludniowa sciana i rzedem skrzyn co 2 pola przy niej (tabliczki nad nimi, od strony
        wejscia nigdy nie stoi skrzynia: korytarz wchodzi od gory albo z boku na wysokosci srodka)."""
        for attempt in range(40):
            snap = dict(self.p.f.get(self.z, {}))
            saved = dict(self.ch)
            try:
                ch = self.chamber(name, cx, cy, n + 5, 7, flat=True, amp=0.12)
            except AssertionError:
                raise
            ybot = max(c[1] for c in ch.cells)
            y = ybot - 1
            xs = sorted(x for (x, yy) in ch.interior if yy == y)
            run = [x for x in xs if all((x + k, y) in ch.interior and (x + k, y - 1) in ch.interior for k in range(0, 2 * n - 1, 2))]
            if run:
                x0 = min(run, key=lambda v: abs(v + n - 1 - cx))
                for i, item in enumerate(items):
                    self.p.chests.append((x0 + 2 * i, y, self.z, 100, item, 1))
                    self.reserved |= {(x0 + 2 * i + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0)}
                return ch
            self.p.f[self.z] = snap
            self.ch = saved
        raise AssertionError(f'{name}: no chest row')

    def dress(self, names, per=5):
        for n in names:
            cells = [c for c in self.free_cells(n) if c not in self.reserved]
            self.rng.shuffle(cells)
            for (x, y) in cells[:per]:
                d = self.rng.choice(self.free)
                kind = _tile_kinds().get(d, ('DECORATION', '1'))
                if kind[0] == 'OBJECT' and kind[1] != '1':
                    continue
                if kind[0] == 'OBJECT':
                    self.p.obj(x, y, self.z, d)
                else:
                    self.p.deco(x, y, self.z, d)

    def verify_gates(self, start, behind):
        """Kazda brama: komory za nia (behind[i]) nie sa osiagalne od start po zablokowaniu tej bramy."""
        p, z = self.p, self.z

        def reach(block):
            seen = {start}
            dq = deque([start])
            while dq:
                x, y = dq.popleft()
                for dx, dy in St.DIRS4:
                    n = (x + dx, y + dy)
                    if n in seen or n == block:
                        continue
                    if St.walk(p.get(n[0], n[1], z)):
                        seen.add(n)
                        dq.append(n)
            return seen
        for cell, beyond in zip(self.gate_cells, behind):
            seen = reach(cell)
            for b in beyond:
                assert b not in seen, f'gate {cell} is not a chokepoint for {b}'


# --------------------------------------------------------------------------------------------- plany miejsc
YARD = (0, 3, 9, 9)
NPC_AT = (4, 6)
NPC_B_AT = (6, 7)


def surface(p, q, rng, level):
    """Dziedziniec z PZ i NPC, nieregularna wartownia z drzwiami poziomu i schodami w dol."""
    x0, y0, x1, y1 = YARD
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    p.pz.append((x0, y0, x1, y1, 0))
    p.npcs.append([q['npc'][0], q['npc'][1], 'GUIDE', q['town'], NPC_AT[0], NPC_AT[1], 0, '-', q['npc'][2], q['greet'][0]])
    if q['tpl'] == 'faction' and q['side'] == 'a':
        nb = q['npc_b']
        p.npcs.append([nb[0], nb[1], 'GUIDE', q['town'], NPC_B_AT[0], NPC_B_AT[1], 0, '-', nb[2], q['greet_b'][0]])
    cells = I5.shift(I5.noisy(rng, 5, 4, 0.18), 16, 6)
    interior, ring = I5.room(p, 0, cells, 'stone_floor', 'stone_wall')
    door = I5.pick(ring, (x1 + 1, 6))
    for x in range(x1 + 1, door[0]):
        p.put(x, door[1], 0, ('cobblestone', NONE, NONE, NONE))
    p.level_door(0, door, level, floor='stone_floor')
    center = (16, 6)
    assert center in interior
    p.entries.append(NPC_AT)
    return center, door


def region_levels(level):
    return max(1, int(level * 0.75)), int(level * 1.5) + 10


def party_req(q):
    n = PARTY_GATES.get(q['key'])
    return f'+party:{n}' if n else ''


def build_plan(q, seed):
    t = q['tpl']
    for attempt in range(80):
        rng = random.Random(seed + 131 * attempt)
        try:
            p = St.Plan(q['place'][0])
            lo, hi = region_levels(q['level'])
            stairs, door = surface(p, q, rng, q['level'])
            dg = Dungeon(p, rng, q.get('fkey', q['key']))
            info = LAYOUTS[t](q, p, dg, rng)
            hall = dg.ch['hall']
            p.stairs_down(0, stairs, hall.center)
            p.regions.append((q['place'][0], 0, 0, 27, 14, 0, 0, lo, hi))
            zs = [c for c in p.f[-1]]
            p.regions.append((inner_name(q), min(c[0] for c in zs), min(c[1] for c in zs), max(c[0] for c in zs),
                              max(c[1] for c in zs), -1, -1, lo, hi))
            p.behind.append((hall.center[0], hall.center[1], -1))
            dg.verify_gates(hall.center, info['behind'])
            p.check()
            return p, dg, info
        except AssertionError as e:
            last = e
            continue
    raise AssertionError(f"{q['key']}: site not built: {last}")


def L_lever(q, p, dg, rng):
    key = q['key']
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 24, 6, 5)
    dg.chamber('leverC', 14, 8, 6, 4)
    dg.chamber('hub', 34, 24, 6, 5)
    dg.chamber('leverA', 34, 8, 6, 4)
    dg.chamber('leverB', 34, 40, 6, 4)
    dg.chamber('boss', 58, 24, 9, 7)
    dg.vault('vault', 58, 46, n, rewards_of(q))
    dg.link('hall', 'leverC')
    dg.link('hall', 'hub')
    dg.link('hub', 'leverA')
    dg.link('hub', 'leverB')
    flags = [f'lv_{key}_{c}' for c in 'abc']
    g1 = dg.link('hub', 'boss', req='+'.join('flag:' + f for f in flags), level=q['level'])
    g2 = dg.link('boss', 'vault', req='boss:' + q['bosses'][0], level=q['level'], vertical_first=True)
    dg.lever('leverA', flags[0])
    dg.lever('leverB', flags[1])
    dg.lever('leverC', flags[2])
    (ma, na), (mb, nb) = q['mins']
    dg.populate(ma, ['leverA', 'leverC', 'hall'], na)
    dg.populate(mb, ['leverB', 'hub'], nb)
    bx, by = dg.ch['boss'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'hub', 'boss', 'leverA', 'leverB', 'leverC'])
    return dict(behind=[[dg.ch['boss'].center, dg.ch['vault'].center], [dg.ch['vault'].center]], vault_gate=g2,
                reward_req=f'quest:{mk(q, 4)}')


def L_team(q, p, dg, rng):
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 24, 6, 5)
    arena = dg.chamber('arena', 50, 24, 12, 9)
    dg.vault('vault', 50, 48, n, rewards_of(q))
    dg.link('hall', 'arena', req=f"party:{q['party']}", level=q['level'])
    dg.link('arena', 'vault', req='boss:' + q['bosses'][0], level=q['level'], vertical_first=True)
    ax, ay = arena.center
    for dy in (-3, -1, 1, 3):
        c = I5.pick(arena.interior, (ax - 6, ay + dy))
        p.deco(c[0], c[1], dg.z, 'boss_plate')
        dg.reserved.add(c)
    (ma, na), (mb, nb) = q['mins']
    dg.populate(ma, ['hall'], na, per=4)
    dg.populate(mb, ['arena'], nb, avoid=[(ax - 6, ay + d) for d in (-3, -1, 1, 3)], per=3)
    p.spawn(q['bosses'][0], ax + 4, ay, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'arena'])
    return dict(behind=[[arena.center, dg.ch['vault'].center], [dg.ch['vault'].center]], reward_req=f'quest:{mk(q, 4)}')


def L_ritual(q, p, dg, rng):
    key = q['key']
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 24, 6, 5)
    grove = dg.chamber('grove', 38, 24, 9, 7)
    dg.chamber('boss', 64, 24, 8, 7)
    dg.vault('vault', 38, 48, n, rewards_of(q))
    dg.link('hall', 'grove')
    flag = f'lv_{key}_rite'
    dg.link('grove', 'boss', req=f'quest:{mk(q, 3)}+flag:{flag}', level=q['level'])
    dg.link('boss', 'vault', req='boss:' + q['bosses'][0], level=q['level'], vertical_first=True)
    ax, ay = dg.place_obj('grove', 'altar', near=(grove.center[0] - 2, grove.center[1]))
    dg.lever('grove', flag, near=(ax + 2, ay))
    (ma, na), (mb, nb) = q['mins']
    dg.populate(ma, ['hall'], na)
    dg.populate(mb, ['grove'], nb)
    bx, by = dg.ch['boss'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'grove', 'boss'])
    return dict(behind=[[dg.ch['boss'].center, dg.ch['vault'].center], [dg.ch['vault'].center]], reward_req=f'quest:{mk(q, 4)}')


def L_chain(q, p, dg, rng):
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 12, 6, 5)
    dg.chamber('r1', 38, 12, 7, 5)
    dg.chamber('r2', 62, 12, 7, 5)
    dg.chamber('r3', 62, 34, 7, 5)
    dg.vault('vault', 36, 36, n, rewards_of(q))
    dg.link('hall', 'r1')
    b = q['bosses']
    dg.link('r1', 'r2', req='boss:' + b[0], level=q['level'])
    dg.link('r2', 'r3', req='boss:' + b[1], level=q['level'], vertical_first=True)
    dg.link('r3', 'vault', req='boss:' + b[2], level=q['level'])
    for i, rn in enumerate(('r1', 'r2', 'r3')):
        mon, cnt = q['mins'][i]
        dg.populate(mon, [rn], max(6, cnt // 2) if rn != 'r3' else 6)
        bx, by = dg.ch[rn].center
        p.spawn(b[i], bx + 1, by, -1, radius=2, count=1, respawn=900)
    mon, cnt = q['mins'][2]
    dg.populate(mon, ['vault'], cnt)
    dg.dress(['hall', 'r1', 'r2', 'r3'])
    rest = [dg.ch[c].center for c in ('r2', 'r3', 'vault')]
    return dict(behind=[rest, rest[1:], rest[2:]], reward_req=f'quest:{mk(q, 5)}')


def L_wrath(q, p, dg, rng):
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 10, 6, 4)
    dg.chamber('w1', 38, 10, 7, 5)
    dg.chamber('w2', 62, 10, 7, 5)
    dg.chamber('w3', 62, 32, 7, 5)
    dg.chamber('w4', 38, 32, 7, 5)
    dg.chamber('throne', 14, 32, 9, 6)
    dg.vault('vault', 14, 56, n, rewards_of(q))
    dg.link('hall', 'w1')
    dg.link('w1', 'w2', req=f'quest:{mk(q, 2)}', level=q['level'])
    dg.link('w2', 'w3', req=f'quest:{mk(q, 3)}', level=q['level'], vertical_first=True)
    dg.link('w3', 'w4', req=f'quest:{mk(q, 4)}', level=q['level'])
    dg.link('w4', 'throne', req=f'quest:{mk(q, 5)}' + party_req(q), level=q['level'])
    dg.link('throne', 'vault', req='boss:' + q['bosses'][0], level=q['level'], vertical_first=True)
    for i, wn in enumerate(('w1', 'w2', 'w3', 'w4')):
        mon, cnt = q['mins'][i]
        dg.populate(mon, [wn], cnt)
    bx, by = dg.ch['throne'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'w1', 'w2', 'w3', 'w4', 'throne'])
    c = lambda *ns: [dg.ch[n].center for n in ns]
    return dict(behind=[c('w2', 'w3', 'w4', 'throne', 'vault'), c('w3', 'w4', 'throne', 'vault'), c('w4', 'throne', 'vault'),
                        c('throne', 'vault'), c('vault')], reward_req=f'quest:{mk(q, 6)}')


def L_faction(q, p, dg, rng):
    a, b = q['key'], q['other']
    if q['side'] == 'b':
        return None
    n = len(rewards_of(q))
    dg.chamber('hall', 36, 30, 8, 6)
    dg.chamber('campA', 12, 30, 6, 5)
    dg.chamber('campB', 62, 30, 6, 5)
    dg.chamber('bossA', 12, 10, 6, 5)
    dg.chamber('bossB', 62, 10, 6, 5)
    dg.vault('vaultA', 14, 52, n, rewards_of(q))
    dg.vault('vaultB', 62, 52, n, rewards_of(dict(q, key=b, fkey=b)))
    dg.link('hall', 'campA')
    dg.link('hall', 'campB')
    dg.link('campA', 'bossA', req=f'quest:{b}_m2', level=q['level'], vertical_first=True)
    dg.link('campB', 'bossB', req=f'quest:{a}_m2', level=q['level'], vertical_first=True)
    dg.link('campA', 'vaultA', req=f'quest:{a}_m3', level=q['level'], vertical_first=True)
    dg.link('campB', 'vaultB', req=f'quest:{b}_m3', level=q['level'], vertical_first=True)
    # linia A (zakon Slonca) zabija potwory i bossa Liscia, ktore stoja w obozie B; linia B odwrotnie
    (ma, na), (m2a, n2a) = q['mins']
    (mb, nb), (m2b, n2b) = q['mins_b']
    dg.populate(ma, ['campB'], na)
    dg.populate(m2a, ['campB', 'bossB'], n2a)
    dg.populate(mb, ['campA'], nb)
    dg.populate(m2b, ['campA', 'bossA'], n2b)
    bx, by = dg.ch['bossB'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    bx, by = dg.ch['bossA'].center
    p.spawn(q['bosses_b'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'campA', 'campB', 'bossA', 'bossB'])
    c = lambda *ns: [dg.ch[n].center for n in ns]
    return dict(behind=[c('bossA'), c('bossB'), c('vaultA'), c('vaultB')],
                reward_req=f'quest:{mk(q, 3)}', reward_req_b=f'quest:{b}_m3')


def L_quarters(q, p, dg, rng):
    n = len(rewards_of(q))
    dg.chamber('hall', 38, 28, 9, 7)
    dg.chamber('q1', 12, 28, 7, 5)
    dg.chamber('q2', 38, 8, 7, 4)
    dg.chamber('q3', 64, 28, 7, 5)
    dg.chamber('q4', 38, 48, 7, 4)
    dg.chamber('boss', 66, 48, 8, 6)
    dg.vault('vault', 66, 8, n, rewards_of(q))
    dg.link('hall', 'q1')
    dg.link('hall', 'q2', req=f'quest:{mk(q, 2)}', level=q['level'], vertical_first=True)
    dg.link('hall', 'q3', req=f'quest:{mk(q, 3)}', level=q['level'])
    dg.link('hall', 'q4', req=f'quest:{mk(q, 4)}', level=q['level'], vertical_first=True)
    dg.link('q4', 'boss', req=f'quest:{mk(q, 5)}' + party_req(q), level=q['level'])
    dg.link('q2', 'vault', req='boss:' + q['bosses'][0], level=q['level'])
    for i, qn in enumerate(('q1', 'q2', 'q3', 'q4')):
        mon, cnt = q['mins'][i]
        dg.populate(mon, [qn], cnt)
    bx, by = dg.ch['boss'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'q1', 'q2', 'q3', 'q4', 'boss'])
    c = lambda *ns: [dg.ch[n].center for n in ns]
    return dict(behind=[c('q2', 'vault'), c('q3'), c('q4', 'boss'), c('boss'), c('vault')], reward_req=f'quest:{mk(q, 6)}')


def L_arena(q, p, dg, rng):
    n = len(rewards_of(q))
    dg.chamber('hall', 14, 12, 6, 5)
    dg.chamber('t1', 38, 12, 7, 5)
    dg.chamber('t2', 62, 12, 7, 5)
    dg.chamber('t3', 62, 34, 7, 5)
    dg.chamber('champ', 38, 34, 9, 6)
    dg.vault('vault', 12, 36, n, rewards_of(q))
    dg.link('hall', 't1')
    dg.link('t1', 't2', req=f'quest:{mk(q, 2)}', level=q['level'])
    dg.link('t2', 't3', req=f'quest:{mk(q, 3)}', level=q['level'], vertical_first=True)
    dg.link('t3', 'champ', req=f'quest:{mk(q, 4)}' + party_req(q), level=q['level'])
    dg.link('champ', 'vault', req='boss:' + q['bosses'][0], level=q['level'])
    for i, tn in enumerate(('t1', 't2', 't3')):
        mon, cnt = q['mins'][i]
        dg.populate(mon, [tn], cnt)
    bx, by = dg.ch['champ'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 't1', 't2', 't3', 'champ'])
    c = lambda *ns: [dg.ch[n].center for n in ns]
    return dict(behind=[c('t2', 't3', 'champ', 'vault'), c('t3', 'champ', 'vault'), c('champ', 'vault'), c('vault')],
                reward_req=f'quest:{mk(q, 5)}')


def L_seals(q, p, dg, rng):
    key = q['key']
    n = len(rewards_of(q))
    dg.chamber('hall', 12, 28, 6, 5)
    dg.chamber('hub', 34, 28, 7, 6)
    dg.chamber('boss', 56, 28, 8, 6)
    dg.vault('vault', 82, 28, n, rewards_of(q))
    seals = {'s1': (12, 8), 's2': (34, 8), 's3': (56, 8), 's4': (12, 48), 's5': (34, 48), 's6': (56, 48)}
    for name, (x, y) in seals.items():
        dg.chamber(name, x, y, 6, 4)
    dg.link('hall', 'hub')
    dg.link('hub', 's2', vertical_first=True)
    dg.link('hub', 's5', vertical_first=True)
    dg.link('s2', 's1')
    dg.link('s2', 's3')
    dg.link('s5', 's4')
    dg.link('s5', 's6')
    flags = [f'lv_{key}_{i}' for i in range(1, 7)]
    dg.link('hub', 'boss', req='+'.join('flag:' + f for f in flags) + party_req(q), level=q['level'])
    dg.link('boss', 'vault', req='boss:' + q['bosses'][0], level=q['level'])
    for i, name in enumerate(seals):
        dg.lever(name, flags[i])
    (ma, na), (mb, nb) = q['mins']
    dg.populate(ma, ['s1', 's2', 's3'], na)
    dg.populate(mb, ['s4', 's5', 's6', 'hub'], nb)
    bx, by = dg.ch['boss'].center
    p.spawn(q['bosses'][0], bx, by, -1, radius=2, count=1, respawn=900)
    dg.dress(['hall', 'hub', 'boss'] + list(seals))
    return dict(behind=[[dg.ch['boss'].center, dg.ch['vault'].center], [dg.ch['vault'].center]], reward_req=f'quest:{mk(q, 4)}')


LAYOUTS = dict(lever=L_lever, team=L_team, ritual=L_ritual, chain=L_chain, wrath=L_wrath, faction=L_faction,
               quarters=L_quarters, arena=L_arena, seals=L_seals)


# --------------------------------------------------------------------------------------------- nagrody
FAMILIES = ['desert', 'plague', 'blizzard', 'dragon', 'sun', 'obsidian', 'phoenix', 'deep', 'moon', 'angel']
FAMILY_MIN = dict(desert=50, plague=80, blizzard=120, dragon=170, sun=210, obsidian=250, phoenix=330, deep=420, moon=600, angel=720)
SLOTS = [('armor', ['armor']), ('helmet', ['helmet', 'mask']), ('legs', ['legs']), ('shield', ['shield', 'aegis']),
         ('K', ['sword', 'axe', 'cleaver', 'hammer', 'mace', 'scimitar']), ('P', ['bow', 'trident', 'spear']),
         ('S', ['wand']), ('D', ['rod'])]
DESERT = dict(armor='fur_armor', helmet='scarab_helmet', legs='croc_legs', shield='sandstone_shield', K='desert_scimitar',
              P='serpent_bow', S='wand_of_storms', D='rod_of_vines')
_ITEMS = {}
_BOSS_ONLY = set()


def _load_items():
    if _ITEMS:
        return
    for ln in open(os.path.join(RES, 'data', 'items.csv'), encoding='utf-8'):
        f = ln.rstrip('\n').split(';')
        if not ln.startswith('#') and len(f) > 11:
            _ITEMS[f[1]] = dict(level=int(f[10]), voc=f[11], cat=f[3])
    for ln in open(os.path.join(RES, 'data', 'boss_items.csv'), encoding='utf-8'):
        if not ln.startswith('#') and ln.strip():
            _BOSS_ONLY.add(ln.split(';')[0])


def family_item(fam, slot):
    _load_items()
    if fam == 'desert':
        return DESERT.get(slot[0])
    for suf in slot[1]:
        k = f'{fam}_{suf}'
        if k in _ITEMS and k not in _BOSS_ONLY:
            return k
    return None


def rewards_of(q):
    """Osiem skrzyn do wyboru: sloty (zbroja, helm, nogi, tarcza, bron KPSD) z dwoch rodzin; poziom <= 1,5 poziomu zadania + 5."""
    _load_items()
    L = q['level']
    ok = [f for f in FAMILIES if FAMILY_MIN[f] <= L * 1.5 + 5]
    main = ok[-1]
    alt = ok[-2] if len(ok) > 1 else ok[-1]
    idx = int(q.get('fkey', q['key']) in ('seals', 'mirror', 'oakgrove', 'sandrite', 'quarters', 'trials')) if len(ok) > 1 else 0
    out = []
    for i, slot in enumerate(SLOTS):
        order = [main, alt] if (i + idx) % 2 == 0 else [alt, main]
        if slot[0] in 'KPSD':
            order = [main, alt]
        item = None
        for fam in order + [f for f in reversed(ok) if f not in order]:
            item = family_item(fam, slot)
            if item and _ITEMS[item]['level'] <= L * 1.5 + 5:
                break
            item = None
        assert item, f"{q['key']}: no item for {slot[0]}"
        if item not in out:
            out.append(item)
    return out


# --------------------------------------------------------------------------------------------- umieszczenie
def carve_to_town(ctx, key, town, temple, npc, box):
    import heapq
    import story_quests as SQ
    world = ctx['world']
    q = {'key': key, 'town': town}
    town_seen = SQ._reach(world, temple)
    if not town_seen[npc[1], npc[0]]:
        # Dijkstra z pol dziedzinca (przechodnich, w ramie miejsca) do najblizszego pola osiagalnego z miasta
        S = len(world.floors[0])
        start = [npc]
        yard = set()
        dq = [npc]
        while dq:
            x, y = dq.pop()
            if (x, y) in yard:
                continue
            yard.add((x, y))
            for dx, dy in F.DIRS4:
                n = (x + dx, y + dy)
                if box[0] <= n[0] <= box[2] and box[1] <= n[1] <= box[3] and n not in yard and F.walk(world.get(n[0], n[1], 0)):
                    dq.append(n)
        pq = [(0, c, None) for c in yard]
        best = {c: (0, None) for c in yard}
        end = None
        heapq.heapify(pq)
        while pq:
            d, c, _ = heapq.heappop(pq)
            if best[c][0] < d:
                continue
            if town_seen[c[1], c[0]] and c not in yard:
                end = c
                break
            for dx, dy in F.DIRS4:
                n = (c[0] + dx, c[1] + dy)
                if not (0 < n[0] < S - 1 and 0 < n[1] < S - 1) or n in yard:
                    continue
                cell = world.get(n[0], n[1], 0)
                if cell is None or cell[0] in ('water', 'deep_water', 'lava', 'chasm') :
                    continue
                if box[0] <= n[0] <= box[2] and box[1] <= n[1] <= box[3]:
                    continue                       # nie przebijamy sie przez budynek miejsca
                nd = d + (1 if F.walk(cell) else 6)
                if n not in best or nd < best[n][0]:
                    best[n] = (nd, c)
                    heapq.heappush(pq, (nd, n, c))
        assert end is not None, f"premium {q['key']}: yard cannot be joined to {q['town']}"
        c = end
        while c is not None and c not in yard:
            g, deco, obj, wall = world.get(c[0], c[1], 0)
            if not F.walk(world.get(c[0], c[1], 0)):
                g = g if g not in ('mountain', 'rock', 'cliff') else 'dirt'
                world.put(c[0], c[1], 0, (g, deco, NONE, NONE))
                if not F.walk(world.get(c[0], c[1], 0)):
                    world.put(c[0], c[1], 0, ('dirt', NONE, NONE, NONE))
            c = best[c][1]
        town_seen = SQ._reach(world, temple)
        assert town_seen[npc[1], npc[0]], f"premium {q['key']}: yard still cut off"


def finalize(ctx):
    """Na koniec generowania (pozniejsze kroki dosadzaja drzewa): dziedzince premium nadal maja byc osiagalne pieszo z miasta."""
    towns = dict(ctx['towns'])
    for rec in ctx.get('premium_sites', []):
        carve_to_town(ctx, rec['key'], rec['town'], towns[rec['town']][:2], tuple(rec['npc'][:2]), rec['box'])


def connect_and_verify(ctx, q, plan, W, temple, box):
    """Po wstawieniu miejsca: dziedziniec ma byc osiagalny pieszo z miasta (inaczej wycina sciezke przez drzewa i skaly poza
    planem), a z dziedzinca przez schody wszystkie skrzynie i miejsca spawnow podziemia."""
    import heapq
    import citadel
    import story_quests as SQ
    world = ctx['world']
    npc = W(NPC_AT[0], NPC_AT[1], 0)
    carve_to_town(ctx, q['key'], q['town'], temple, npc, box)
    seen = citadel._reach(ctx, [(npc[0], npc[1], 0)], set())
    bad = []
    for (x, y, z, *_r) in plan.chests:
        a = W(x, y, z)
        if not any((a[0] + dx, a[1] + dy, z) in seen for dx, dy in ((0, 1), (0, -1), (1, 0), (-1, 0))):
            bad.append(('chest', a, z))
    for (m, x, y, z, r, n, resp) in plan.spawns:
        a = W(x, y, z)
        if (a[0], a[1], z) not in seen and not any((a[0] + dx, a[1] + dy, z) in seen for dx, dy in F.DIRS4):
            bad.append((m, a, z))
    if bad:
        ctx.setdefault('premium_fail', []).append(f"premium {q['key']}: unreachable from the yard: {bad[:8]} ({len(bad)})")
        print('UWAGA', ctx['premium_fail'][-1])


def build(ctx, cost, helpers):
    import story_quests as SQ
    world = ctx['world']
    mark = helpers['mark']
    towns = dict(ctx['towns'])
    reach_cache = {}
    ctx.setdefault('reward_reqs', [])
    ctx.setdefault('star_choices', [])
    ctx.setdefault('star_gates', [])
    ctx.setdefault('lever_flags', [])
    out = []
    done_sites = set()
    for k, q in enumerate(all_lines()):
        if q['tpl'] == 'faction' and q['side'] == 'b':
            continue
        qq = dict(q)
        plan, dg, info = build_plan(qq, SEED + 977 * k)
        if q['town'] not in reach_cache:
            reach_cache[q['town']] = SQ._reach(world, towns[q['town']][:2])
        hh = dict(helpers, reach=reach_cache[q['town']])
        w, h = 28 + 6, 16 + 6
        tx = ty = None
        for (mt, mw, rad) in ((70, 0.1, 220), (50, 0.2, 260), (36, 0.3, 320), (24, 0.4, 400)):
            try:
                tx, ty = St.find_site(ctx, hh, cost, q['near'], w, h, radius=rad, min_temple=mt, max_water=mw)
                break
            except AssertionError:
                continue
        assert tx is not None, f"premium: no site for {q['key']}"
        origin = (tx + 3, ty + 3)
        St.clear_surface(ctx, tx - 2, ty - 2, tx + w + 1, ty + h + 1)
        W = St.stamp(ctx, plan, origin, mark)
        for (x, y, z, req) in dg.gates:
            a = W(x, y, z)
            ctx['star_gates'].append((a[0], a[1], z, req))
        for (flag, x, y, z) in dg.levers:
            a = W(x, y, z)
            ctx['lever_flags'].append((flag, a[0], a[1], z))
        connect_and_verify(ctx, q, plan, W, towns[q['town']][:2], (tx, ty, tx + w, ty + h))
        groups = [(q['key'], info['reward_req'], 'vault' if q['tpl'] != 'faction' else 'vaultA')]
        if q['tpl'] == 'faction':
            groups = [(q['key'], info['reward_req'], 'A'), (q['other'], info['reward_req_b'], 'B')]
        chest_rows = [c for c in plan.chests]
        for gi, (grp, req, _v) in enumerate(groups):
            sub = chest_rows if len(groups) == 1 else chest_rows[gi * len(chest_rows) // 2:(gi + 1) * len(chest_rows) // 2]
            for (x, y, z, gold, item, n) in sub:
                a = W(x, y, z)
                ctx['star_choices'].append((grp, a[0], a[1], z, item))
            ctx['reward_reqs'].append((grp, req))
        npc = W(NPC_AT[0], NPC_AT[1], 0)
        door = W(plan.doors[0][0], plan.doors[0][1], 0)
        rec = dict(key=q['key'], town=q['town'], level=q['level'], tpl=q['tpl'], npc=npc, door=door, origin=origin,
                   box=(tx, ty, tx + w, ty + h))
        out.append(rec)
        ctx.setdefault('premium_sites', []).append(rec)
    assert not ctx.get('premium_fail'), ctx['premium_fail']
    return out


# --------------------------------------------------------------------------------------------- dane gry
def data_split_exp(q):
    """Doswiadczenie misji jako ulamek kosztu poziomu (StoryQuestsTest.rewardsOfOlderQuestsFollowOneScale: boss >= 0,45,
    do 25 zabic >= 0,09, wiecej >= 0,108, nigdy ponad 1,25); rosnie wzdluz lancucha, ostatni boss placi najwiecej."""
    cost = level_cost(q['level'])
    mult = 1.2 if tier_of(q['level']) == 'extreme' else 1.0
    out = []
    bosses = 0
    n = len(q['steps'])
    for i, (kind, tk, target, amount) in enumerate(q['steps']):
        if tk == 'visit':
            ratio = 0.10
        elif amount == 1:
            ratio = 0.9 if i == n - 1 else 0.55 + 0.05 * bosses
            bosses += 1
        elif amount > 25:
            ratio = 0.16 + 0.02 * i
        else:
            ratio = 0.12 + 0.02 * i
        exp = cost * ratio * mult
        exp = int(round(exp, -2 if exp < 1e6 else -3))
        out.append((exp, max(100, int(round(exp * 0.05, -2)))))
    return out


_NAMES = {}


def monster_names(key):
    if not _NAMES:
        for ln in open(os.path.join(RES, 'data', 'monsters.csv'), encoding='utf-8'):
            if ln.strip() and not ln.startswith('#'):
                f = ln.split(';')
                _NAMES[f[0]] = [f[1], None, None]
        for i, fn in ((1, 'data_en.csv'), (2, 'data_es.csv')):
            for ln in open(os.path.join(RES, 'i18n', fn), encoding='utf-8'):
                f = ln.rstrip('\n').split(';')
                if len(f) >= 4 and f[0] == 'monster' and f[2] == 'name' and f[1] in _NAMES:
                    _NAMES[f[1]][i] = f[3]
        for k, (nm, _lv, _base, _col) in D.BOSSES.items():
            _NAMES[k] = list(nm)
    return _NAMES[key]


def mission_rows(q):
    """Wiersze danych misji linii: lista slownikow (klucz, rodzaj, cel, ilosc, nazwa/opis 3 jezyki, exp, gold)."""
    t = q['tpl']
    rewards = data_split_exp(q)
    rows = []
    for i, (kind, tk, target, amount) in enumerate(q['steps'], start=1):
        e, g = rewards[i - 1]
        if tk == 'visit':
            names = tuple(f"{('Do środka', 'Inside', 'Dentro')[lang]}: {q['place'][lang]}" for lang in range(3))
            descs = tuple(f"{q['story'][lang]} " + ('Wejdź do środka ({}) i rozejrzyj się.', 'Step inside ({}) and have a look around.',
                                                    'Entra ({}) y echa un vistazo.')[lang].format(inner_name(q, lang)) for lang in range(3))
            tgt = inner_name(q)
        else:
            nm_t, ds_t = D.TEXT[t][tk]
            what = monster_names(target)
            fm = lambda s, lang: s.format(n=amount, mon=what[lang], boss=what[lang], place=q['place'][lang], party=q.get('party', 2))
            names = tuple(fm(nm_t[lang], lang) for lang in range(3))
            descs = tuple(fm(ds_t[lang], lang) for lang in range(3))
            tgt = target
        rows.append(dict(key=mk(q, i), kind=kind, target=tgt, amount=amount, names=names, descs=descs, exp=e, gold=g))
    return rows


def potion_for(level):
    return ('great_health_potion', 'great_mana_potion') if level < 120 else ('strong_health_potion', 'strong_mana_potion') if level < 200 \
        else ('supreme_health_potion', 'supreme_mana_potion') if level < 250 else ('ultimate_health_potion', 'ultimate_mana_potion')


def boss_loot(level):
    """Lup bossa: oczekiwane zloto co najmniej 9x targetGold (LootRules.BOSS_GOLD_MIN_FACTOR = 8): zloto, platyna, od ~poziomu 450 kryształowe monety."""
    hp, mp = potion_for(level)
    need = 9.0 * level ** 1.12
    base = (level * 3 + max(30, level * 9)) / 2.0
    plat_avg = min(50.0, max(3.0, (need - base) / 100.0))
    rem = need - base - plat_avg * 100
    out = [('gold_coin', 1000, max(10, level * 3), max(30, level * 9)),
           ('platinum_coin', 1000, max(3, int(plat_avg * 0.6)), max(6, int(plat_avg * 1.4 + 1)))]
    if rem > 0:
        avg = rem / 10000.0
        out.append(('crystal_coin', 1000, max(1, int(avg * 0.6)), max(2, int(avg * 1.4 + 1))))
    return out + [(hp, 700, 2, 5), (mp, 600, 2, 5), ('crystal_shard', 500, 1, 3)]


BEGIN, END = '# BEGIN premium_quests (tools/mapgen/premium_quests.py data)', '# END premium_quests'


def splice(path, lines, header=''):
    text = open(path, encoding='utf-8').read() if os.path.exists(path) else header
    if BEGIN in text:
        a = text.index(BEGIN)
        b = text.index(END) + len(END)
        text = text[:a].rstrip('\n') + '\n' + text[b:].lstrip('\n')
    text = text.rstrip('\n') + '\n' + BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    open(path, 'w', encoding='utf-8').write(text)


ELEMENTS = [('DEATH', 'death'), ('FIRE', 'fire'), ('ICE', 'ice'), ('EARTH', 'earth'), ('ENERGY', 'energy')]


def extra_spells(key, lvl, have):
    """Uzupelnienie czarow bossa (StrongMonsterSpellsTest): strzal w jeden cel, czar obszarowy, od poziomu 300 co najmniej trzy czary."""
    el, missile = ELEMENTS[sum(map(ord, key)) % len(ELEMENTS)]
    modes = [r[2] for r in have]
    out = []
    n = len(have)
    if 'BOLT' not in modes:
        out.append(f"{key};{key}_bolt;BOLT;POINT;{el};{int(lvl * 0.7)};{int(lvl * 1.8)};15;4;-;{missile}")
        n += 1
    if not any(m in ('WAVE', 'BALL', 'SELF') for m in modes):
        out.append(f"{key};{key}_blast;BALL;CIRCLE2X2;{el};{int(lvl * 0.5)};{int(lvl * 1.3)};12;4;-;-")
        n += 1
    el2, missile2 = ELEMENTS[(sum(map(ord, key)) + 2) % len(ELEMENTS)]
    if n < 3:
        out.append(f"{key};{key}_nova;SELF;CIRCLE3X3;PHYSICAL;{int(lvl * 0.45)};{int(lvl * 1.1)};9;2;-;-")
        n += 1
    if n < 3:
        out.append(f"{key};{key}_lash;BOLT;POINT;{el2};{int(lvl * 0.6)};{int(lvl * 1.5)};12;4;-;{missile2}")
    return out


def write_data():
    quests, chains, lines_csv, dialogues, requires = [], [], [], [], []
    en, es = [], []
    seen_npc, seen_place = set(), set()
    for q in all_lines():
        rows = mission_rows(q)
        npc = q['npc'][0]
        first = rows[0]['key']
        for i, r in enumerate(rows):
            desc = r['descs'][0]
            quests.append(f"{r['key']};{r['names'][0]};{npc};{r['kind']};{r['target']};{r['amount']};{r['exp']};{r['gold']};{q['level']};{desc}")
            item = '-'
            if q['tpl'] == 'arena' and 1 <= i <= 3:
                item = f"{potion_for(q['level'])[i % 2]}:{10 + 5 * i}"
            elif i == len(rows) - 1 and i > 0:
                item = f"{potion_for(q['level'])[0]}:{15}"
            if i > 0:
                chains.append(f"{r['key']};{rows[i - 1]['key']};{item}")
            for lang, acc in ((1, en), (2, es)):
                acc.append(f"quest;{r['key']};name;{r['names'][lang]}")
                acc.append(f"quest;{r['key']};desc;{r['descs'][lang]}")
        where = (f"{q['place'][0]}, okolice miasta {q['town']}", f"{q['place'][1]}, near {q['town']}", f"{q['place'][2]}, cerca de {q['town']}")
        lines_csv.append(f"{first};{tier_of(q['level'])};1;{q['line'][0]};{where[0]}")
        reqs = list(q['prereq'])
        if q['tpl'] == 'faction':
            reqs.append('!' + mk(dict(q, key=q['other']), 1))
        requires.append(f"{first};{','.join(reqs)}")
        for lang, acc in ((1, en), (2, es)):
            acc.append(f"quest;{first};line;{q['line'][lang]}")
            acc.append(f"quest;{first};where;{where[lang]}")
            for nm, trio in ((q['place'][0], q['place']), (inner_name(q), tuple(inner_name(q, i) for i in range(3)))):
                if nm not in seen_place:
                    acc.append(f"place;{nm};name;{trio[lang]}")
            acc.append(f"npc;{npc};name;{q['npc'][1]}")
            acc.append(f"npc;{npc};greeting;{q['greet'][lang]}")
        seen_place |= {q['place'][0], inner_name(q)}
        door = (f"Drzwi wpuszczą tylko od poziomu {q['level']}." )
        tw = q['place'][0].split()[0].lower()
        hk, hv = D.HINTS[q['tpl']]
        extra = f" Do zadania trzeba drużyny co najmniej {q['party']} graczy." if q['tpl'] == 'team' else ''
        if q['key'] in PARTY_GATES:
            extra = f" Przed ostatnią salą trzeba drużyny co najmniej {PARTY_GATES[q['key']]} graczy."

        dialogues.append(f"{npc};{hk};{hv}{extra}")
        taken_words = set(hk.split(','))
        words = [w for w in ('wejście', 'historia', 'poziom', 'nagroda', 'skrzynia', 'skarb', tw, q['key']) if w not in taken_words]
        dialogues.append(f"{npc};{','.join(words)};{q['story'][0]} {door} Na końcu czeka skarbiec: jedna skrzynia do wyboru na postać, po oddaniu ostatniej misji.")
    # tlumaczenia bossow i pliki potworow
    monsters, loot, behaviour, spells, conditions = [], [], [], [], []
    base_rows = {}
    for fn in ('monsters', 'monster_behaviour', 'monster_spells', 'monster_conditions'):
        base_rows[fn] = [ln.rstrip('\n') for ln in open(os.path.join(RES, 'data', fn + '.csv'), encoding='utf-8')
                         if ln.strip() and not ln.startswith('#')]
    for key, (nm, lvl, base, color) in D.BOSSES.items():
        row = next(r for r in base_rows['monsters'] if r.split(';')[0] == base).split(';')
        hp, exp = int(row[3]), int(row[4])
        row[0], row[1], row[2] = key, nm[0], str(lvl)
        row[3], row[4] = str(int(round(hp * 8, -2))), str(int(round(exp * 6, -1)))
        row[6], row[7] = str(int(int(row[6]) * 1.3)), str(int(int(row[7]) * 1.3))
        row[15], row[16], row[17] = '0', row[16], '1'
        row[18] = color
        row[24] = f"Strażnik zadania premium: {nm[0]}. Miejsce jego legowiska opisuje zleceniodawca w dzienniku zadań."
        monsters.append(';'.join(row))
        for r in base_rows['monster_behaviour']:
            if r.split(';')[0] == base:
                behaviour.append(';'.join([key] + r.split(';')[1:]))
        for r in base_rows['monster_spells']:
            if r.split(';')[0] == base:
                spells.append(';'.join([key] + r.split(';')[1:]))
        spells.extend(extra_spells(key, lvl, [x.split(';') for x in spells if x.startswith(key + ';')]))
        for r in base_rows['monster_conditions']:
            if r.split(';')[0] == base:
                conditions.append(';'.join([key] + r.split(';')[1:]))
        for (it, chance, lo, hi) in boss_loot(lvl):
            loot.append(f'{key};{it};{chance};{lo};{hi}')
        for lang, acc in ((1, en), (2, es)):
            acc.append(f"monster;{key};name;{nm[lang]}")
    casters = {r.split(';')[0] for r in spells}
    behaviour = [';'.join([f[0], 'MIXED'] + f[2:]) if f[1] == 'MELEE' and f[0] in casters else ';'.join(f)
                 for f in (r.split(';') for r in behaviour)]
    for fn, rows_ in (('monsters.csv', monsters), ('loot.csv', loot), ('monster_behaviour.csv', behaviour),
                      ('monster_spells.csv', spells), ('monster_conditions.csv', conditions)):
        splice(os.path.join(RES, 'data', fn), rows_)
    splice(os.path.join(RES, 'data', 'quests.csv'), quests)
    splice(os.path.join(RES, 'data', 'quest_chains.csv'), chains)
    splice(os.path.join(RES, 'data', 'quest_lines.csv'), lines_csv)
    splice(os.path.join(RES, 'data', 'quest_requires.csv'), requires)
    splice(os.path.join(RES, 'data', 'dialogues.csv'), dialogues)
    splice(os.path.join(RES, 'i18n', 'data_en.csv'), en)
    splice(os.path.join(RES, 'i18n', 'data_es.csv'), es)
    print('premium data written:', len(all_lines()), 'lines,', len(quests), 'missions,', len(D.BOSSES), 'bosses')


def selftest():
    """Buduje wszystkie plany bez swiata (kontrola ksztaltow, bram i nagrod)."""
    for k, q in enumerate(all_lines()):
        if q['tpl'] == 'faction' and q['side'] == 'b':
            continue
        plan, dg, info = build_plan(q, SEED + 977 * k)
        print(q['key'], q['tpl'], 'chests', len(plan.chests), 'spawns', len(plan.spawns), 'gates', len(dg.gates),
              'levers', len(dg.levers), rewards_of(q))


if __name__ == '__main__':
    if sys.argv[1:] == ['data']:
        write_data()
    elif sys.argv[1:] == ['selftest']:
        selftest()
