"""Kontynent glowny i ziemie premium (Darek 2026-11-11): "przy tworzeniu postaci tylko miasta z glownego kontynentu,
czyli 3; reszta to opcja premium i nie moze byc do nich mostow bezposrednio z glownego ladu, tylko statkiem".

Glowny kontynent: Varden, Frostholm i Qasirah (oraz ukryte Durnkar - tylko zamieszkanie). Ziemie premium:
  1 - poludnie za Srebrna Rzeka: Valmir i Selvaran (jeden lad, kanaly ucinaja go od polnocy),
  2 - Szmaragdowe Wyspy z Nerys (wyspa od poczatku).
Kanaly kopie ``split``: nowe odnogi rzeki w najwezszych miejscach ladu (przeciecia wyznaczone offline minimalnym
przekrojem grafu ladu, bez ruszania twierdzy orkow), a potem petla naprawcza: dopoki z Varden da sie dojsc pieszo do
swiatyni miasta premium (mosty, kladki, schody i tunele pod kanalami), najkrotsza droga jest przecinana - most znika
w calosci, schody zostaja zamurowane, a korytarz podziemny dostaje mur. Statki (kapitanowie) i tak przewoza premium.
"""
import math
from array import array
from collections import deque

import numpy as np

import features as F

NONE = '-'
WATER = ('water', NONE, NONE, NONE)
MAIN = ['Varden', 'Frostholm', 'Qasirah']          # miasta startowe kontynentu glownego
PREMIUM = {'Valmir': 1, 'Selvaran': 1, 'Nerys': 2}  # miasto -> numer ladu premium
BRIDGE_GROUND = ('bridge', 'rope_bridge')

# srodkowe linie kanalow (x, y) i szerokosc w polach; dwie krotkie odnogi domykaja wasko rzeki przy Varden i Valmir
CHANNELS = [
    # Zielona Odnoga: od Srebrnej Rzeki na wschod przez dzungle do morza (pod twierdza orkow, nad Selvaran)
    ([(582, 622), (582, 609), (592, 609), (645, 610), (656, 615), (682, 615), (695, 617), (718, 618), (718, 633), (727, 638),
      (755, 638), (760, 641), (788, 643), (802, 643)], 8),
    # wasko przy mostach Varden (przesmyk Valmiru)
    ([(509, 565), (521, 566)], 6),
    ([(528, 572), (542, 572)], 6),
    # luki w rzece na zachod od Valmiru
    ([(430, 612), (431, 628)], 6),
    ([(430, 638), (431, 654)], 6),
    ([(424, 656), (429, 676)], 6),
]
SIDE_CAP = 60          # najwyzej tyle napraw petla naprawcza


def _is_land(c):
    return c is not None and c[0] not in ('water', 'deep_water', 'lava', 'chasm', 'bridge', 'rope_bridge', 'ship_deck')


def dig(ctx, S):
    """Kopie kanaly (woda na 0, brak pol na wyzszych pietrach, bez przejsc i spawnow w kanale). Zwraca liczbe pol."""
    world = ctx['world']
    mask = np.zeros((S, S), bool)
    for pts, width in CHANNELS:
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            n = max(abs(bx - ax), abs(by - ay)) * 2
            for i in range(n + 1):
                t = i / max(1, n)
                x, y = ax + (bx - ax) * t, ay + (by - ay) * t
                r = width / 2.0 + 0.9 * math.sin(x * 0.19 + y * 0.13) + 0.5 * math.sin(x * 0.07 - y * 0.23)
                r = max(2.4, r)
                x0, x1, y0, y1 = int(x - r - 1), int(x + r + 1), int(y - r - 1), int(y + r + 1)
                for yy in range(max(0, y0), min(S, y1 + 1)):
                    for xx in range(max(0, x0), min(S, x1 + 1)):
                        if (xx - x) ** 2 + (yy - y) ** 2 <= r * r:
                            mask[yy, xx] = True
    dug = 0
    guard = [(a, b, c, d) for (a, b, c, d, z) in ctx['pz'] if z == 0]
    for y, x in zip(*np.nonzero(mask)):
        x, y = int(x), int(y)
        # kanal nie wchodzi w miasta i wioski (strefy ochronne) ani w domy
        assert not any(a <= x <= c and b <= y <= d for (a, b, c, d) in guard if (c - a) > 6 and (d - b) > 6), \
            f'channel hits a protected area at {(x, y)}'
    for y, x in zip(*np.nonzero(mask)):
        x, y = int(x), int(y)
        world.floors[0][y][x] = WATER
        for z in world.floors:
            if z > 0 and world.floors[z][y][x] is not None:
                world.floors[z][y][x] = None
        dug += 1
    inside = lambda x, y: 0 <= x < S and 0 <= y < S and mask[y, x]
    ctx['transitions'] = [t for t in ctx['transitions'] if not (t[2] == 0 and inside(t[0], t[1]))]
    return dug, mask


class Graph:
    """Graf pol swiata (wszystkie pietra, schody z transitions) do szukania drogi pieszo."""

    def __init__(self, ctx, S):
        self.S = S
        self.world = ctx['world']
        self.zs = sorted(self.world.floors)
        self.zi = {z: i for i, z in enumerate(self.zs)}
        self.walk = {}
        for z in self.zs:
            g = self.world.floors[z]
            m = bytearray(S * S)
            for y in range(S):
                row = g[y]
                base = y * S
                for x in range(S):
                    c = row[x]
                    if c is not None and F.walk(c):
                        m[base + x] = 1
            self.walk[z] = m
        self.tr = {}
        for (x, y, z, _t, tx, ty, tz) in ctx['transitions']:
            self.tr[self.idx(x, y, z)] = self.idx(tx, ty, tz)

    def idx(self, x, y, z):
        return (self.zi[z] * self.S + y) * self.S + x

    def pos(self, i):
        S = self.S
        x = i % S
        y = (i // S) % S
        return x, y, self.zs[i // (S * S)]

    def ok(self, i):
        S = self.S
        return self.walk[self.zs[i // (S * S)]][i % (S * S)] == 1

    def path(self, start, goals, blocked=()):
        """Najkrotsza droga (lista indeksow) od start do najblizszego z goals albo None."""
        S = self.S
        SS = S * S
        n = len(self.zs) * SS
        par = array('i', [-2]) * n
        par[start] = -1
        q = deque([start])
        goals = set(goals)
        walks = [self.walk[z] for z in self.zs]
        tr = self.tr
        while q:
            i = q.popleft()
            if i in goals:
                out = []
                while i != -1:
                    out.append(i)
                    i = par[i]
                return out[::-1]
            zi, r = divmod(i, SS)
            y, x = divmod(r, S)
            w = walks[zi]
            nb = []
            if x + 1 < S:
                nb.append(i + 1)
            if x:
                nb.append(i - 1)
            if y + 1 < S:
                nb.append(i + S)
            if y:
                nb.append(i - S)
            t = tr.get(i)
            if t is not None:
                nb.append(t)
            for j in nb:
                if par[j] != -2 or j in blocked:
                    continue
                if walks[j // SS][j % SS] != 1 and j not in tr:
                    continue
                par[j] = i
                q.append(j)
        return None


def _premium_land(world, S, temples):
    """Pola ladu (powierzchnia) polaczone z swiatyniami miast premium - bez mostow."""
    seen = np.zeros((S, S), bool)
    q = deque()
    for (x, y) in temples:
        seen[y, x] = True
        q.append((x, y))
    g = world.floors[0]
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4 + ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            a, b = x + dx, y + dy
            if 0 <= a < S and 0 <= b < S and not seen[b, a] and _is_land(g[b][a]):
                seen[b, a] = True
                q.append((a, b))
    return seen


def _remove_bridge(world, x, y, S):
    """Usuwa caly most (pola bridge/rope_bridge polaczone z (x, y) na tym pietrze) - woda na 0, brak pola wyzej."""
    z = 0
    seen = {(x, y)}
    q = deque([(x, y)])
    cells = []
    while q:
        a, b = q.popleft()
        cells.append((a, b))
        for dx, dy in F.DIRS4 + ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            n = (a + dx, b + dy)
            if n in seen or not (0 <= n[0] < S and 0 <= n[1] < S) or abs(n[0] - x) > 40 or abs(n[1] - y) > 40:
                continue
            c = world.floors[z][n[1]][n[0]]
            if c is not None and c[0] in BRIDGE_GROUND:
                seen.add(n)
                q.append(n)
    for (a, b) in cells:
        world.floors[z][b][a] = WATER
    return len(cells)


def _clear_stairs(ctx, g, i):
    """Zamurowuje schody/drabine w polu i (indeks) razem z para po drugiej stronie; usuwa ich przejscia."""
    world = ctx['world']
    x, y, z = g.pos(i)
    tx, ty, tz = g.pos(g.tr[i])
    drop = set()
    for t in ctx['transitions']:
        a, b = (t[0], t[1], t[2]), (t[4], t[5], t[6])
        if a == (x, y, z):
            drop.add(t)
        # przejscie zwrotne: z pietra docelowego, ladujace obok tych schodow
        if a[2] == tz and b[2] == z and abs(b[0] - x) <= 1 and abs(b[1] - y) <= 1 and \
                abs(a[0] - tx) <= 2 and abs(a[1] - ty) <= 2:
            drop.add(t)
    ctx['transitions'] = [t for t in ctx['transitions'] if t not in drop]
    for t in drop:
        a, b, zz = t[0], t[1], t[2]
        c = world.floors[zz][b][a]
        if c is not None and c[2] in F.STAIRS:
            world.floors[zz][b][a] = (c[0], NONE, NONE, 'cave_wall' if zz < 0 else NONE)
    return len(drop)


def split(ctx, S):
    """Kanaly plus petla naprawcza; zwraca opis (do wypisania i do testow)."""
    world = ctx['world']
    towns = dict(ctx['towns'])
    prem = {n: towns[n] for n in PREMIUM}
    dug, mask = dig(ctx, S)
    g = Graph(ctx, S)
    start = g.idx(*towns['Varden'])
    goals = {g.idx(*p) for n, p in prem.items() if p[2] == 0 and n != 'Nerys'}
    land_p = _premium_land(world, S, [p[:2] for n, p in prem.items() if p[2] == 0 and n != 'Nerys'])
    vx, vy = towns['Varden'][:2]
    assert not land_p[vy, vx], 'the canals leave a land link between Varden and the premium south'
    report = {'dug': dug, 'bridges': 0, 'stairs': 0, 'walls': 0, 'land_patches': 0}
    for it in range(SIDE_CAP):
        path = g.path(start, goals)
        if path is None:
            break
        cells = [g.pos(i) for i in path]
        # pierwszy indeks na ladzie premium, ostatni wczesniejszy indeks na ladzie spoza premium (powierzchnia)
        j = next(k for k, (x, y, z) in enumerate(cells) if z == 0 and land_p[y, x])
        i = max((k for k in range(j) if cells[k][2] == 0 and not land_p[cells[k][1], cells[k][0]]
                 and _is_land(world.floors[0][cells[k][1]][cells[k][0]])), default=-1)
        seg = list(range(i + 1, j))
        mid = (i + j) // 2
        bridge = [k for k in seg if cells[k][2] == 0 and world.floors[0][cells[k][1]][cells[k][0]][0] in BRIDGE_GROUND]
        trans = [k for k in seg if path[k] in g.tr]
        if bridge:
            k = min(bridge, key=lambda k: abs(k - mid))
            x, y, _z = cells[k]
            n = _remove_bridge(world, x, y, S)
            report['bridges'] += 1
            print(f'  naprawa {it}: most usuniety w {(x, y)} ({n} pol)')
            g = Graph(ctx, S)
            continue
        if trans:
            k = min(trans, key=lambda k: abs(k - mid))
            x, y, z = cells[k]
            n = _clear_stairs(ctx, g, path[k])
            report['stairs'] += 1
            print(f'  naprawa {it}: przejscie zamurowane w {(x, y, z)} ({n})')
            g = Graph(ctx, S)
            continue
        under = [k for k in seg if cells[k][2] != 0]
        if under:
            # wasko korytarza: pole podziemia z najmniejsza liczba przechodnich sasiadow, najblizej srodka
            def width(k):
                x, y, z = cells[k]
                return sum(1 for dx, dy in F.DIRS4 if g.walk[z][(y + dy) * S + x + dx])
            k = min(under, key=lambda k: (width(k), abs(k - mid)))
            x, y, z = cells[k]
            c = world.floors[z][y][x]
            world.floors[z][y][x] = (c[0], NONE, NONE, 'cave_wall' if z < 0 else 'stone_wall')
            g.walk[z][y * S + x] = 0
            report['walls'] += 1
            print(f'  naprawa {it}: mur w korytarzu {(x, y, z)}')
            continue
        # sam lad bez mostow - kanal za waski: dodatkowy krag wody w srodku odcinka (zwykle pole ladu przy brzegu)
        k = mid if 0 <= mid < len(cells) else j - 1
        x, y, z = cells[max(0, k)]
        for yy in range(y - 3, y + 4):
            for xx in range(x - 3, x + 4):
                if (xx - x) ** 2 + (yy - y) ** 2 <= 12 and world.floors[0][yy][xx] is not None:
                    world.floors[0][yy][xx] = WATER
        report['land_patches'] += 1
        print(f'  naprawa {it}: dodatkowy kanal w {(x, y)}')
        g = Graph(ctx, S)
    else:
        raise AssertionError('premium lands still reachable on foot after the repair loop')
    # kontrola koncowa: zadne premium pieszo z Varden (takze Nerys)
    allg = {g.idx(*p) for p in prem.values()}
    assert g.path(start, allg) is None, 'a premium town is reachable on foot'
    report['premium'] = {n: PREMIUM[n] for n in PREMIUM}
    return report
