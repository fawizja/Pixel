#!/usr/bin/env python3
"""Stopniowanie poziomu potworow wzdluz drog od swiatyn (Darek, 2026-11-04).

"Podejscia do slabych potworow nie moga byc strzezone przez silne; po drodze do silnych potworow poziom ma rosnac
stopniowo." Przebieg (``apply`` - ostatni krok generate_continent.finish, po stadach i przesunieciu spawnow):

1. Odleglosc pieszo od swiatyn do kazdego pola wszystkich pieter (4 kierunki; schody, drabiny, dziury i liny jako
   krawedzie) i drzewo najkrotszych drog (rodzic pola). Swiatynia miasta startuje z odlegloscia, ktorej pas
   odpowiada najnizszemu poziomowi regionu miasta (Varden 0, Frostholm ~ pas poziomu 40 itd.).
2. Dla kazdego spawnu dopuszczalny poziom potwora:
     - pas wg odleglosci (``band_cap``): co ~40 pol drogi ok. x1.6 poziomu; za drzwiami/bramami poziomu G co najmniej
       G * GATE_FACTOR; na powierzchni do 16 pol od swiatyni najwyzej 15 (potwory dla poczatkujacych),
     - droga do slabszego celu: spawn najwyzej ``ROUTE_NEAR`` pol od drogi ze swiatyni do innego spawnu nie moze
       przekraczac poziomu tego celu o wiecej niz ``route_slack``.
   Pojedynczy slaby spawn w srodku silnego lowiska (wiekszosc sasiadow 3x silniejsza) nie jest celem - dostaje
   potwora sasiadow (o ile pozwala jego pas).
3. Spawn ponad limitem: potwor z tego samego klimatu (biome w monsters.csv; pod ziemia takze "underground"),
   najsilniejszy mieszczacy sie w limicie (nocny - tylko nocny potwor); gdy brak - spawn usuniety.
   Powtarzane do skutku (obnizony spawn sam staje sie slabszym celem).
4. Zalecane poziomy regionow dopasowane do spawnow, ktore w nich zostaly.

Wyspy osiagalne tylko statkiem nie sa zmieniane (osobne lowiska od przystani).
``python3 tools/mapgen/route_levels.py [katalog swiata]`` - raport dla gotowego swiata (naruszenia, potwory wg pasa).
"""
import math
import os
import sys
from array import array
from collections import Counter, deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import town as tk  # noqa: E402

RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
FLOORS = list(range(-5, 6))
INF = 1 << 30

# pas: (droga od swiatyni w polach, maks. poziom potwora); miedzy punktami liniowo po logarytmie poziomu
BANDS = [(0, 12), (40, 20), (80, 32), (120, 52), (160, 85), (200, 135), (240, 215), (280, 340), (320, 520),
         (360, 760), (400, 1000)]
# pod murami: na powierzchni w promieniu TOWN_RADIUS od swiatyni tylko potwory do poziomu TOWN_MAX
TOWN_RADIUS = 16
TOWN_MAX = 15
# za drzwiami/brama poziomu G (nie wejdzie nikt slabszy) wolno postawic potwory do G * GATE_FACTOR
GATE_FACTOR = 1.4
# droga do slabszego celu
ROUTE_NEAR = 8
ROUTE_SLACK_PCT = 25
ROUTE_SLACK_MIN = 10
# pojedynczy slaby spawn w silnym lowisku (promien, krotnosc, min. sasiadow)
OUTLIER_RADIUS = 24
OUTLIER_FACTOR = 3
OUTLIER_MIN = 3
# potwory morza/powietrza, ktorych nie dajemy pod ziemie
NOT_UNDERGROUND = {'coral_crab', 'drowned_sailor', 'pirate_cutthroat', 'pirate_gunner', 'storm_harpy', 'storm_wisp',
                   'wyvern', 'carrion_crow', 'snare_setter', 'dune_drake'}
PACK_MIN_LEVEL = 15
LEVEL_BANDS = [(1, 15), (16, 30), (31, 60), (61, 100), (101, 200), (201, 400), (401, 700), (701, 1000)]


def route_slack(level):
    return max(ROUTE_SLACK_MIN, level * ROUTE_SLACK_PCT // 100)


def band_cap(d):
    """Maksymalny poziom potwora na polu odleglym o d krokow (odleglosc z przesunieciem miasta)."""
    if d <= 0:
        return BANDS[0][1]
    for (d0, l0), (d1, l1) in zip(BANDS, BANDS[1:]):
        if d0 <= d < d1:
            t = (d - d0) / (d1 - d0)
            return int(round(math.exp(math.log(l0) + t * (math.log(l1) - math.log(l0)))))
    return BANDS[-1][1]


def band_distance(level):
    """Najmniejsza odleglosc, na ktorej pas dopuszcza dany poziom (przesuniecie startu miasta)."""
    d = 0
    while band_cap(d) < level and d < BANDS[-1][0]:
        d += 1
    return d


def band_name(level):
    for lo, hi in LEVEL_BANDS:
        if lo <= level <= hi:
            return f'{lo}-{hi}'
    return str(level)


# ------------------------------------------------------------------ siatka i BFS

class Grid:
    """Pola przechodnie wszystkich pieter (bytearray na pietro) i przejscia (pole -> cel)."""

    def __init__(self, S, cell_at, transitions):
        self.S = S
        self.walk = {}
        for z in FLOORS:
            a = bytearray(S * S)
            for y in range(S):
                for x in range(S):
                    c = cell_at(x, y, z)
                    if c is not None and tk.walkable(c):
                        a[y * S + x] = 1
            self.walk[z] = a
        self.trans = {}
        for (x, y, z, _typ, tx, ty, tz) in transitions:
            self.trans[(z, y * S + x)] = (tz, ty * S + tx)

    def idx(self, x, y, z):
        return (z + 5) * self.S * self.S + y * self.S + x

    def xyz(self, i):
        z, r = divmod(i, self.S * self.S)
        y, x = divmod(r, self.S)
        return x, y, z - 5


def bfs(grid, starts, blocked=None):
    """Odleglosc pieszo od najblizszego startu (z przesunieciem d0) i rodzic na drodze. starts: [(x, y, z, d0)].
    BFS warstwami: start z d0 wchodzi do kolejki, gdy front dojdzie do odleglosci d0."""
    S = grid.S
    S2 = S * S
    n = S2 * len(FLOORS)
    dist = array('i', [INF]) * n
    parent = array('i', [-1]) * n
    pending = sorted(((d0, grid.idx(x, y, z)) for (x, y, z, d0) in starts), reverse=True)
    trans, walk = grid.trans, grid.walk
    frontier, d = [], 0
    while frontier or pending:
        while pending and pending[-1][0] <= d:
            _d0, i = pending.pop()
            if dist[i] > d:
                dist[i] = d
                frontier.append(i)
        nxt = []
        nd = d + 1
        for i in frontier:
            if dist[i] != d:
                continue
            zi, r = divmod(i, S2)
            z = zi - 5
            y, x = divmod(r, S)
            wz = walk[z]
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if not (0 <= nx < S and 0 <= ny < S):
                    continue
                nr = ny * S + nx
                t = trans.get((z, nr))
                if t is not None:
                    j = (t[0] + 5) * S2 + t[1]
                elif wz[nr]:
                    j = zi * S2 + nr
                else:
                    continue
                if blocked is not None and j in blocked:
                    continue
                if dist[j] > nd:
                    dist[j] = nd
                    parent[j] = i
                    nxt.append(j)
        frontier = nxt
        d = nd
        if not frontier and pending:
            d = max(d, pending[-1][0])
    return dist, parent


# ------------------------------------------------------------------ dane

def load_monsters(path=None):
    path = path or os.path.join(RES, 'data', 'monsters.csv')
    out = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            p = line.rstrip('\n').split(';')
            out[p[0]] = dict(level=int(p[2]), boss=p[17].strip() == '1', biome=p[20].strip(),
                             night=p[21].strip() == 'NOCTURNAL')
    return out


def parse_spawn(line):
    f = line.split(';')
    return dict(key=f[0], x=int(f[1]), y=int(f[2]), z=int(f[3]), f=f)


class Model:
    """Odleglosci, drogi i limity poziomu dla listy spawnow."""

    def __init__(self, S, cell_at, transitions, towns, town_levels, npcs, gates, doors, monsters):
        self.monsters = monsters
        self.grid = Grid(S, cell_at, transitions)
        self.towns = towns
        starts = []
        for name, (x, y, z) in towns:
            starts.append((x, y, z, band_distance(town_levels.get(name, BANDS[0][1]))))
        self.dist, self.parent = bfs(self.grid, starts)
        # pola z drzwiami/bramami poziomu (wejscie dopiero od tego poziomu)
        self.gate = {}
        for (x0, y0, x1, y1, z, lvl) in gates:
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    self.gate[self.grid.idx(x, y, z)] = lvl
        for (x, y, z, lvl) in doors:
            self.gate[self.grid.idx(x, y, z)] = max(lvl, self.gate.get(self.grid.idx(x, y, z), 0))
        # drogi do celow omijaja drzwi i bramy poziomu (slabsza postac tamtedy nie przejdzie); cele za nimi - zwykle
        _d, self.parent_open = bfs(self.grid, starts, blocked=set(self.gate))
        self.dist_open = _d
        # osiagalnosc w ogole: od swiatyn i od kazdego NPC (wyspy - od przystani i przewoznikow)
        npc_starts = [(int(n[4]), int(n[5]), int(n[6]), 0) for n in npcs] + [(*p, 0) for _n, p in towns]
        self.dist_any, _p = bfs(self.grid, npc_starts)

    def reachable(self, s):
        return self.dist[self.grid.idx(s['x'], s['y'], s['z'])] < INF

    def gate_level(self, s):
        """Najwyzszy poziom drzwi/bram na drodze ze swiatyni do spawnu."""
        i = self.grid.idx(s['x'], s['y'], s['z'])
        best = 0
        steps = 0
        while i >= 0 and steps < 5000:
            g = self.gate.get(i)
            if g:
                best = max(best, g)
            i = self.parent[i]
            steps += 1
        return best

    def band(self, s):
        d = self.dist[self.grid.idx(s['x'], s['y'], s['z'])]
        cap = band_cap(d)
        g = self.gate_level(s)
        if g:
            cap = max(cap, int(round(g * GATE_FACTOR)))
        if s['z'] == 0 and any(max(abs(s['x'] - p[0]), abs(s['y'] - p[1])) <= TOWN_RADIUS and p[2] == 0
                               for _n, p in self.towns):
            cap = min(cap, TOWN_MAX)
        return cap

    def anywhere(self, s):
        """Czy da sie dojsc do spawnu skadkolwiek (swiatynia, przystan, przewoznik)."""
        return self.dist_any[self.grid.idx(s['x'], s['y'], s['z'])] < INF

    def route_marks(self, spawns, levels, active):
        """Pole drogi -> najslabszy cel, ktorego droga przez nie przechodzi (bez odcinka tuz przy celu).
        Drogi omijajace drzwi/bramy poziomu i zwykle drogi to dwa drzewa - osobne oznaczenia, bo na wspolnym
        odcinku przerwanie petli "juz oznaczone slabszym" dziala tylko w obrebie jednego drzewa."""
        best = {}
        for parent, keep in ((self.parent_open, True), (self.parent, False)):
            mark = {}
            for k in sorted(active, key=lambda k: levels[k]):
                s = spawns[k]
                i = self.grid.idx(s['x'], s['y'], s['z'])
                if (self.dist_open[i] < INF) != keep:
                    continue
                lv = levels[k]
                for _ in range(ROUTE_NEAR + 2):
                    if i < 0:
                        break
                    i = parent[i]
                while i >= 0:
                    if mark.get(i, INF) <= lv:
                        break
                    mark[i] = lv
                    i = parent[i]
            for i, lv in mark.items():
                if best.get(i, INF) > lv:
                    best[i] = lv
            if not keep:
                self.marks_gated = mark
        return best

    def near_min(self, best, s, near=ROUTE_NEAR):
        S = self.grid.S
        # spawn za brama/drzwiami poziomu: drogi slabszych celow tuz za murem (poza brama) go nie dotycza
        if self.dist_open[self.grid.idx(s['x'], s['y'], s['z'])] >= INF:
            best = getattr(self, 'marks_gated', {})
        base = (s['z'] + 5) * S * S
        m = INF
        x, y = s['x'], s['y']
        for yy in range(max(0, y - near), min(S, y + near + 1)):
            row = base + yy * S
            for xx in range(max(0, x - near), min(S, x + near + 1)):
                v = best.get(row + xx)
                if v is not None and v < m:
                    m = v
        return m


# ------------------------------------------------------------------ przebieg

def _candidates(monsters, orig, z, night, cap):
    m0 = monsters[orig]
    biomes = {m0['biome']} | ({'underground'} if z < 0 else set())
    out = [k for k, m in monsters.items() if not m['boss'] and m['level'] <= cap and m['biome'] in biomes
           and not k.endswith('_elite') and (z >= 0 or k not in NOT_UNDERGROUND) and (not night or m['night'])]
    if not out:
        biomes = {'underground'} if z < 0 else {'temperate'}
        out = [k for k, m in monsters.items() if not m['boss'] and m['level'] <= cap and m['biome'] in biomes
               and (z >= 0 or k not in NOT_UNDERGROUND) and (not night or m['night'])]
    return out


def _pick(monsters, cands, s):
    """Najsilniejszy z kandydatow; wsrod 3 najblizszych limitowi wybor stały wg pozycji (rozmaitosc)."""
    cands = sorted(cands, key=lambda k: (-monsters[k]['level'], k))
    top = [k for k in cands if monsters[k]['level'] >= monsters[cands[0]]['level'] * 0.7][:3]
    return top[(s['x'] * 7 + s['y'] * 13 + s['z'] * 3) % len(top)]


def run(model, lines, log=print):
    """Zwraca nowe linie spawnow i statystyki. lines: wiersze spawns.csv (z komentarzami)."""
    monsters = model.monsters
    spawns = [parse_spawn(ln) if not ln.startswith('#') else None for ln in lines]
    idx = [k for k, s in enumerate(spawns) if s is not None]
    stats = Counter()
    removed = set()
    for k in idx:
        if not model.anywhere(spawns[k]):
            removed.add(k)            # nie da sie tam dojsc (ani z przystani wyspy)
            stats['nieosiagalne'] += 1
    active = [k for k in idx if k not in removed and model.reachable(spawns[k]) and not monsters[spawns[k]['key']]['boss']]
    bands = {k: model.band(spawns[k]) for k in active}
    # pojedyncze slabe spawny w srodku silnych lowisk: dostaja potwora sasiadow (gdy pozwala pas)
    for k in active:
        s = spawns[k]
        lv = monsters[s['key']]['level']
        nb = [spawns[j] for j in active if j != k and spawns[j]['z'] == s['z']
              and abs(spawns[j]['x'] - s['x']) <= OUTLIER_RADIUS and abs(spawns[j]['y'] - s['y']) <= OUTLIER_RADIUS]
        if len(nb) < OUTLIER_MIN:
            continue
        lvls = sorted(monsters[n['key']]['level'] for n in nb)
        med = lvls[len(lvls) // 2]
        if med >= OUTLIER_FACTOR * max(lv, 5) and sum(1 for v in lvls if v >= OUTLIER_FACTOR * lv) * 3 >= len(lvls) * 2:
            night = len(s['f']) > 7 and s['f'][7] == 'noc'
            near = sorted(nb, key=lambda n: abs(n['x'] - s['x']) + abs(n['y'] - s['y']))
            for n in near:
                m = monsters[n['key']]
                if m['level'] <= bands[k] and (not night or m['night']) and (s['z'] >= 0 or n['key'] not in NOT_UNDERGROUND):
                    s['f'][0] = s['key'] = n['key']
                    stats['podniesione'] += 1
                    break
    for rnd in range(8):
        levels = {k: monsters[spawns[k]['key']]['level'] for k in active if k not in removed}
        keys = list(levels)
        best = model.route_marks(spawns, levels, keys)
        changed = 0
        for k in keys:
            s = spawns[k]
            lv = levels[k]
            r = model.near_min(best, s)
            cap = min(bands[k], r + route_slack(r) if r < INF else INF)
            if lv <= cap:
                continue
            night = len(s['f']) > 7 and s['f'][7] == 'noc'
            cands = _candidates(monsters, s['key'], s['z'], night, cap)
            if not cands:
                removed.add(k)
                stats['usuniete'] += 1
            else:
                new = _pick(monsters, cands, s)
                s['f'][0] = s['key'] = new
                stats['obnizone'] += 1
                if monsters[new]['level'] <= PACK_MIN_LEVEL and s['f'][-1] == 'stado':
                    s['f'].pop()
                    s['f'][5] = str(min(int(s['f'][5]), 3))
            changed += 1
        log(f'poziomy drog: runda {rnd + 1}, zmienione {changed}')
        if not changed:
            break
    out = []
    for k, ln in enumerate(lines):
        if spawns[k] is None:
            out.append(ln)
        elif k not in removed:
            out.append(';'.join(spawns[k]['f']))
    return out, stats


def violations(model, lines):
    """Spawny ponad pasem albo ponad najslabszym celem drogi obok (bez bossow i wysp)."""
    monsters = model.monsters
    spawns = [parse_spawn(ln) for ln in lines if not ln.startswith('#')]
    active = [k for k, s in enumerate(spawns) if model.reachable(s) and not monsters[s['key']]['boss']]
    levels = {k: monsters[spawns[k]['key']]['level'] for k in active}
    best = model.route_marks(spawns, levels, active)
    out = []
    for k in active:
        s = spawns[k]
        b = model.band(s)
        r = model.near_min(best, s)
        rc = r + route_slack(r) if r < INF else INF
        if levels[k] > b or levels[k] > rc:
            out.append((s, levels[k], model.dist[model.grid.idx(s['x'], s['y'], s['z'])], b, rc))
    return out


def fit_regions(regions, lines, monsters):
    """Zalecane poziomy regionu wg spawnow, ktore w nim sa (pierwszy pasujacy region, jak WorldMap.regionAt)."""
    found = {}
    for ln in lines:
        if ln.startswith('#'):
            continue
        s = parse_spawn(ln)
        m = monsters[s['key']]
        for i, r in enumerate(regions):
            if r[1] <= s['x'] <= r[3] and r[2] <= s['y'] <= r[4] and r[5] <= s['z'] <= r[6]:
                found.setdefault(i, []).append(m['level'])
                break
    out = []
    for i, r in enumerate(regions):
        r = list(r)
        lv = found.get(i)
        if lv:
            mn, mx = min(lv), max(lv)
            lo, hi = r[7], r[8]
            if mn < lo * 0.8:
                lo = mn
            if mx > hi:
                hi = mx
            if mx < lo:          # caly region oslabiony: zakres z tego, co zostalo
                lo, hi = mn, mx
            hi = max(lo, min(hi, int(round(mx * 1.25)) + 5))
            r[7], r[8] = lo, hi
        out.append(tuple(r))
    return out


def report(lines, monsters):
    by_band = Counter()
    for ln in lines:
        if ln.startswith('#'):
            continue
        s = parse_spawn(ln)
        by_band[band_name(monsters[s['key']]['level'])] += int(s['f'][5])
    return dict(sorted(by_band.items(), key=lambda kv: int(kv[0].split('-')[0])))


# ------------------------------------------------------------------ generator i gotowy swiat

def town_levels_of(regions, towns):
    out = {}
    for name, (x, y, z) in towns:
        for r in regions:
            if r[0] == name and r[1] <= x <= r[3] and r[2] <= y <= r[4] and r[5] <= z <= r[6]:
                out[name] = int(r[7])
                break
    return out


def apply(ctx, regions, monsters_csv=None):
    """Krok generatora: poprawia ctx['spawns'] i zwraca dopasowane regiony (lista jak all_regions)."""
    monsters = load_monsters(monsters_csv)
    world = ctx['world']
    S = len(world.floors[0])
    model = Model(S, world.get, ctx['transitions'], ctx['towns'], town_levels_of(regions, ctx['towns']), ctx['npcs'],
                  ctx['gates'], ctx.get('level_doors', []), monsters)
    before = report(ctx['spawns'], monsters)
    ctx['spawns'], stats = run(model, ctx['spawns'])
    bad = violations(model, ctx['spawns'])
    assert not bad, f'potwory ponad pasem drogi: {[(s["key"], lv, s["x"], s["y"], s["z"], d, b, rc) for s, lv, d, b, rc in bad[:10]]}'
    print('poziomy drog:', dict(stats), 'potwory wg pasa przed', before, 'po', report(ctx['spawns'], monsters))
    return fit_regions(regions, ctx['spawns'], monsters)


def load_world(d=None):
    d = d or os.path.join(RES, 'maps', 'world')
    props = {}
    with open(os.path.join(d, 'map.properties'), encoding='utf-8') as f:
        for line in f:
            if '=' in line and not line.startswith('#'):
                k, v = line.rstrip('\n').split('=', 1)
                props[k] = v
    legend = {}
    with open(os.path.join(d, 'legend.csv'), encoding='utf-8') as f:
        for line in f:
            if not line.strip() or line.startswith('//') or line.startswith('#'):
                continue
            p = line.rstrip('\n').split(';')
            sym = ' ' if p[0] == 'space' else p[0]
            legend[sym] = None if sym == ' ' else tuple(p[1:5])
    S = int(props['width'])
    floors = {}
    for z in FLOORS:
        name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
        floors[z] = open(os.path.join(d, name), encoding='utf-8').read().split('\n')
    transitions = []
    for line in open(os.path.join(d, 'transitions.csv'), encoding='utf-8'):
        if line.startswith('#') or not line.strip():
            continue
        p = line.strip().split(';')
        transitions.append((int(p[0]), int(p[1]), int(p[2]), p[3], int(p[4]), int(p[5]), int(p[6])))
    spawns = [ln for ln in open(os.path.join(d, 'spawns.csv'), encoding='utf-8').read().split('\n') if ln]
    npcs = [ln.rstrip('\n').split(';') for ln in open(os.path.join(d, 'npcs.csv'), encoding='utf-8')
            if ln.strip() and not ln.startswith('#')]
    towns = []
    for t in props['towns'].split(';'):
        n, p = t.split(':')
        towns.append((n, tuple(int(v) for v in p.split(','))))
    regions = []
    for r in props['regions'].split('|'):
        p = r.split(';')
        regions.append((p[0], *(int(v) for v in p[1:])))
    gates = [tuple(int(v) for v in g.split(',')) for g in props.get('gates', '').split(';') if g]
    doors = [tuple(int(v) for v in ln.strip().split(';')) for ln in open(os.path.join(d, 'level_doors.csv'), encoding='utf-8')
             if ln.strip() and not ln.startswith('#')]

    def cell_at(x, y, z):
        row = floors[z][y] if y < len(floors[z]) else ''
        ch = row[x] if x < len(row) else ' '
        return legend.get(ch)
    return dict(S=S, cell_at=cell_at, transitions=transitions, spawns=spawns, npcs=npcs, towns=towns, regions=regions,
                gates=gates, doors=doors, props=props)


def model_of(w, monsters=None):
    monsters = monsters or load_monsters()
    return Model(w['S'], w['cell_at'], w['transitions'], w['towns'], town_levels_of(w['regions'], w['towns']), w['npcs'],
                 w['gates'], w['doors'], monsters)


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    w = load_world(args[0] if args else None)
    mon = load_monsters()
    mdl = model_of(w, mon)
    v = violations(mdl, w['spawns'])
    act = [ln for ln in w['spawns'] if not ln.startswith('#')]
    print('spawny:', len(act), 'naruszenia:', len(v), '(pas:', sum(1 for x in v if x[1] > x[3]), ' droga:',
          sum(1 for x in v if x[1] > x[4]), ')')
    for s, lv, d, b, rc in sorted(v, key=lambda t: -t[1])[:25]:
        print(f"  {s['key']:18s} lv{lv:4d} @{s['x']},{s['y']},{s['z']} d={d} pas<={b} droga<={rc if rc < INF else '-'}")
    print('potwory wg pasa poziomu:', report(w['spawns'], mon))
    if '--simulate' in sys.argv:
        out, st = run(mdl, w['spawns'], log=lambda *_: None)
        print('po przebiegu:', dict(st), report(out, mon), 'naruszenia', len(violations(mdl, out)))
