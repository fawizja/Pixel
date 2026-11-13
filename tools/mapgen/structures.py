"""Klocki do budowli wielopietrowych (2026-11-11, twierdza orkow i wioska elfow; potem miasto krasnoludow, wulkan...).

Plan to lokalny szkic budowli na wielu pietrach: pola (krotki ground, deco, obj, wall jak w town.py), przejscia,
spawny, skrzynie, drzwi poziomu, strefy ochronne, NPC i regiony - wszystko we wspolrzednych planu. ``stamp`` kladzie
plan na swiecie: powierzchnia (0) i pietra nad nia dokladnie w miejscu planu, podziemia (-1..-5) najblizej pod nim,
gdzie jest wolne miejsce (jak lochy z find_free), a wspolrzedne przejsc, spawnow itd. przelicza wg pietra.

Klocki (wszystkie dzialaja na planie):
    box          - budynek: mury na obwodzie, posadzka, drzwi i przejscia w murze
    platform     - podloga bez murow (pomost, taras, korona muru), opcjonalnie z barierka z obiektow
    deck         - prosty most (deski albo most linowy) z punktu do punktu, dowolnej szerokosci
    stairs       - schody miedzy pietrem z i z+1 (schody w gore na nizszym, w dol na wyzszym; miejsca ladowania
                   wyliczane na koniec, obok schodow)
    ladder       - drabina z nizszego pietra i dziura (zejscie) na wyzszym w tym samym polu
    level_door   - drzwi tylko dla postaci od danego poziomu (level_doors.csv)
    cave         - jaskinia / sale lochu z features.carve_cave / carve_crypt wpisane w pietro planu
    corridor     - korytarz z murami dookola (laczy jaskinie z wykutymi salami)
Na koniec ``check`` szuka drogi (krok w 4 kierunkach + przejscia) od wejsc do kazdego spawnu, schodow, NPC i skrzyni
oraz sprawdza, ze za drzwiami poziomu nie da sie wejsc inna droga.
"""
from collections import deque

import numpy as np

import features as F
import town as tk

NONE = '-'
# potwory stawiane tylko recznie w budowlach (twierdza orkow, osada elfow): generator nie wybiera ich sam do lochow
# dziczy (wilds.pool) ani jako zamiennikow przy obnizaniu poziomow wzdluz drog (route_levels._candidates)
HAND_PLACED = {'orc', 'orc_spearman', 'orc_rider', 'orc_marauder', 'orc_leader', 'orc_cult_priest', 'orc_cult_fanatic',
               'urgoth', 'elf', 'elf_scout', 'elf_arcanist',
               # 2026-11-11: kopalnie krasnoludow pod Durnkarem (dwarves.py)
               'dwarf', 'dwarf_miner', 'dwarf_soldier', 'dwarf_warden', 'dwarf_earthcaller', 'mine_grub', 'deep_worm',
               'rock_golem', 'earth_spirit', 'deep_behemot',
               # 2026-11-11: oboz lowczyn i Cyklopolis (isles.py)
               'grove_huntress', 'grove_shieldmaiden', 'grove_crone', 'grove_matriarch',
               'oneeye_brute', 'oneeye_hauler', 'oneeye_smith',
               # 2026-11-11: Warownia Gorvalda i podziemne chodniki (castle.py)
               'horn_raider', 'horn_bowman', 'horn_guard', 'horn_mystic', 'keep_hound', 'keep_crossbow', 'keep_sentry',
               'keep_chaplain', 'castellan_gorvald',
               # 2026-11-12: Wzgorza Goblinow, Hydr i Smokow (hills.py)
               'goblin_scrounger', 'goblin_knifer', 'goblin_warchief', 'marsh_lizard', 'lizard_hexer', 'bog_hydra',
               'bog_naga', 'venom_hydra', 'stone_gorgon', 'dragon_whelp', 'scale_dragon', 'crag_dragon',
               # 2026-11-12: Miasto Rogatych (minotaur.py)
               'horn_scout', 'horn_sapper', 'horn_gladiator', 'horn_oracle', 'horn_champion', 'horn_overlord',
               # 2026-11-12: Wyspa Gasnacej Gwiazdy (citadel.py)
               'ash_penitent', 'cinder_revenant', 'rimebound_monk', 'hoarfrost_warden', 'slag_forgehand', 'molten_cultist',
               'thorn_maiden', 'spore_stalker', 'mirror_acolyte', 'glass_phantom', 'maw_crawler', 'gore_harbinger',
               'dimmed_seraph', 'vorgrim', 'ysmera', 'kharzul', 'vessara', 'ilmora', 'ugrath', 'noctharion'}
DIRS4 = ((0, 1), (1, 0), (-1, 0), (0, -1))


def walk(c):
    return c is not None and tk.walkable(c)


class Plan:
    def __init__(self, name):
        self.name = name
        self.f = {}                 # z -> {(x, y): krotka}
        self.trans = []             # (x, y, z, typ, tx, ty, tz)
        self._stairs = []           # schody do rozwiazania w finish(): (z, gora, dol, ladowanie w gore, w dol)
        self._ladders = []
        self.spawns = []            # (potwor, x, y, z, promien, liczba, respawn)
        self.chests = []            # (x, y, z, zloto, przedmiot, ilosc)
        self.doors = []             # (x, y, z, poziom)
        self.pz = []                # (x0, y0, x1, y1, z)
        self.npcs = []              # [klucz, imie, rola, miasto, x, y, z, sklep, kolor, powitanie]
        self.regions = []           # (nazwa, x0, y0, x1, y1, z od, z do, poziom od, do) - prostokat w planie
        self.entries = []           # pola wejsc na powierzchni (start sprawdzania drogi)
        self.links = []             # dodatkowe polaczenia do check (np. wejscie z jaskini poza planem)
        self.behind = []            # pola (x, y, z) osiagalne tylko przez drzwi poziomu
        self._cuts = {}             # (x, y, z) -> pole przed sciecie rogu (box organic_shape)

    # ------------------------------------------------------------------ pola
    def get(self, x, y, z):
        return self.f.get(z, {}).get((x, y))

    def put(self, x, y, z, cell):
        self.f.setdefault(z, {})[(x, y)] = tuple(cell)

    def floor_of(self, x, y, z, default='stone_floor'):
        c = self.get(x, y, z)
        return c[0] if c is not None and c[0] != NONE else default

    def obj(self, x, y, z, obj, deco=None):
        c = self.get(x, y, z)
        g = c[0] if c is not None else 'stone_floor'
        self.put(x, y, z, (g, c[1] if (c is not None and deco is None) else (deco or NONE), obj, NONE))

    def deco(self, x, y, z, deco):
        c = self.get(x, y, z)
        if c is not None and c[3] == NONE:
            self.put(x, y, z, (c[0], deco, c[2], NONE))

    def fill(self, z, x0, y0, x1, y1, cell):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, z, cell)

    def bounds(self, z):
        pts = self.f.get(z, {})
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        return min(xs), min(ys), max(xs), max(ys)

    # ------------------------------------------------------------------ klocki
    def box(self, z, x0, y0, w, h, floor, wall, doors=(), gaps=(), door_obj='door_open', organic_shape=True):
        """Budynek w x h (z murami). doors - pola drzwi w murze, gaps - przejscia bez drzwi. Zwraca wnetrze."""
        inner = []
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                self.put(x, y, z, (floor, NONE, NONE, wall) if edge else (floor, NONE, NONE, NONE))
                if not edge:
                    inner.append((x, y))
        for (x, y) in doors:
            self.put(x, y, z, (floor, NONE, door_obj, NONE))
        for (x, y) in gaps:
            self.put(x, y, z, (floor, NONE, NONE, NONE))
        if organic_shape:
            inner = self._organic_corners(z, x0, y0, w, h, wall, inner, list(doors) + list(gaps))
        return inner

    def _organic_corners(self, z, x0, y0, w, h, wall, inner, keep):
        """Regula "zadnych idealnych prostokatow": sciete rogi wnetrza (2x2 / schodek) a w duzych budynkach wyrwany
        blok narozny (ksztalt L). Deterministyczne wg polozenia (nie rusza generatora losowego). Pola przy drzwiach i
        przejsciach zostaja bez zmian."""
        if w < 7 or h < 6:
            return inner
        hsh = (x0 * 73856093) ^ (y0 * 19349663) ^ (z * 83492791) ^ (w * 2654435761) ^ (h * 40503)
        corners = [(x0 + 1, y0 + 1, 1, 1), (x0 + w - 2, y0 + 1, -1, 1), (x0 + 1, y0 + h - 2, 1, -1), (x0 + w - 2, y0 + h - 2, -1, -1)]
        order = sorted(range(4), key=lambda i: (hsh >> (3 * i)) & 7)
        big = w >= 11 and h >= 9
        cut = set()
        for rank, i in enumerate(order[:3]):
            cx, cy, dx, dy = corners[i]
            a, b = (3, 3) if (big and rank == 0) else (2, 1 + ((hsh >> (7 + i)) & 1))
            for u in range(a):
                for v in range(b if u == 0 or a == 3 else 1):
                    cut.add((cx + dx * u, cy + dy * v))
        near = {(kx + i, ky + j) for (kx, ky) in keep for i in (-2, -1, 0, 1, 2) for j in (-2, -1, 0, 1, 2)}
        out = []
        for c in inner:
            cell = self.get(c[0], c[1], z)
            if c in cut and c not in near and cell is not None and cell[2] == NONE:
                self._cuts[(c[0], c[1], z)] = cell
                self.put(c[0], c[1], z, (cell[0], NONE, NONE, wall))
            else:
                out.append(c)
        return out

    def platform(self, z, x0, y0, x1, y1, floor, rail=None, rail_gaps=()):
        """Podloga bez murow; rail - obiekt barierki na brzegu (np. 'fence'), rail_gaps - przerwy w barierce."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                edge = x in (x0, x1) or y in (y0, y1)
                obj = rail if (rail and edge and (x, y) not in rail_gaps) else NONE
                self.put(x, y, z, (floor, NONE, obj, NONE))

    def deck(self, z, a, b, width=1, ground='bridge'):
        """Prosty most z a do b (pionowo albo poziomo), width pol szerokosci w prawo / w dol od linii."""
        (ax, ay), (bx, by) = a, b
        assert ax == bx or ay == by, 'deck must be straight'
        for x in range(min(ax, bx), max(ax, bx) + 1):
            for y in range(min(ay, by), max(ay, by) + 1):
                for k in range(width):
                    p = (x + k, y) if ax == bx else (x, y + k)
                    self.put(p[0], p[1], z, (ground, NONE, NONE, NONE))

    def stairs(self, z, up, down, land_up=None, land_down=None):
        """Schody: na pietrze z w polu up schody w gore, na z+1 w polu down schody w dol.
        Ladowanie: obok schodow docelowych (wolne pole), chyba ze podane."""
        self.obj(up[0], up[1], z, 'stairs_up')
        self.obj(down[0], down[1], z + 1, 'stairs_down')
        self._stairs.append((z, up, down, land_up, land_down))

    def stairs_down(self, z, down, up, land_down=None, land_up=None):
        """Schody w dol z pietra z (pole down) na z-1 (pole up ze schodami w gore)."""
        self.stairs(z - 1, up, down, land_up=land_down, land_down=land_up)

    def ladder(self, z, at, top=None, hole='hole'):
        """Drabina na pietrze z (uzycie - w gore), na z+1 w polu top (domyslnie to samo) dziura w dol."""
        top = top or at
        self.obj(at[0], at[1], z, 'ladder')
        self.obj(top[0], top[1], z + 1, hole)
        self._ladders.append((z, at, top))

    def level_door(self, z, at, level, floor=None):
        self.put(at[0], at[1], z, (floor or self.floor_of(at[0], at[1], z), NONE, 'door_level', NONE))
        self.doors.append((at[0], at[1], z, level))

    def corridor(self, z, cells, floor, wall):
        cells = set(cells)
        for (x, y) in cells:
            c = self.get(x, y, z)
            if c is None or c[3] != NONE or not walk(c):
                self.put(x, y, z, (floor, NONE, NONE, NONE))
        for (x, y) in cells:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if self.get(x + dx, y + dy, z) is None:
                        self.put(x + dx, y + dy, z, (floor, NONE, NONE, wall))

    def cave(self, z, x0, y0, w, h, points, style, rng, crypt=False, room=(3, 5), keep=None, lava=0.0):
        """Jaskinia (albo prostokatne sale - crypt) w prostokacie planu; points we wspolrzednych planu.
        keep(x, y) - pola, ktorych jaskinia nie nadpisuje (np. wykute wczesniej sale)."""
        pts = [(x - x0, y - y0) for (x, y) in points]
        if crypt:
            g = F.carve_crypt(w, h, pts, style, rng)
        else:
            g = F.carve_cave(w, h, pts, style, rng, room=room, lava=lava)
        F.fix_lava(g, pts)
        for y in range(h):
            for x in range(w):
                c = g[y][x]
                if c is None:
                    continue
                X, Y = x0 + x, y0 + y
                if keep is not None and keep(X, Y):
                    continue
                old = self.get(X, Y, z)
                if old is not None and walk(old) and c[3] != NONE:
                    continue                                  # mur jaskini nie zamyka wczesniejszego przejscia
                self.put(X, Y, z, c)

    def spawn(self, monster, x, y, z, radius=3, count=2, respawn=180):
        self.spawns.append((monster, x, y, z, radius, count, respawn))

    # ------------------------------------------------------------------ zakonczenie
    def _land(self, x, y, z, prefer=None):
        order = ([prefer] if prefer else []) + [(x, y + 1), (x + 1, y), (x - 1, y), (x, y - 1),
                                                (x + 1, y + 1), (x - 1, y + 1), (x + 1, y - 1), (x - 1, y - 1)]
        for p in order:
            c = self.get(p[0], p[1], z)
            if walk(c) and c[2] in (NONE, 'chair') and p != (x, y):
                return p
        raise AssertionError(f'{self.name}: no landing next to {(x, y, z)}')

    def finish(self):
        # sciete rogi wracaja na podloge przy schodach, drabinach, skrzyniach, NPC i spawnach (zawsze wolne sasiedztwo)
        pts = []
        for (z, up, down, *_r) in self._stairs:
            pts += [(up[0], up[1], z), (down[0], down[1], z + 1)]
        for (z, at, top) in self._ladders:
            pts += [(at[0], at[1], z), (top[0], top[1], z + 1)]
        pts += [(c[0], c[1], c[2]) for c in self.chests] + [(n[4], n[5], n[6]) for n in self.npcs]
        pts += [(sp[1], sp[2], sp[3]) for sp in self.spawns] + [(d[0], d[1], d[2]) for d in self.doors]
        for (px, py, pz) in pts:
            for dx in (-2, -1, 0, 1, 2):
                for dy in (-2, -1, 0, 1, 2):
                    orig = self._cuts.pop((px + dx, py + dy, pz), None)
                    cur = self.get(px + dx, py + dy, pz)
                    if orig is not None and cur is not None and cur[3] != NONE:
                        self.put(px + dx, py + dy, pz, orig)
        for (cx, cy, cz) in list(self._cuts):
            if any((self.get(cx + dx, cy + dy, cz) or (0, 0, NONE, 0))[2] != NONE for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                cur = self.get(cx, cy, cz)
                if cur is not None and cur[3] != NONE:
                    self.put(cx, cy, cz, self._cuts[(cx, cy, cz)])
        # skrzynie i NPC postawione po scietych rogach (box) wracaja na podloge: stoja zawsze na wolnym polu
        for (x, y, z, *_r) in self.chests:
            c = self.get(x, y, z)
            if c is not None and c[3] != NONE:
                self.put(x, y, z, (c[0], c[1], NONE, NONE))
        for n in self.npcs:
            c = self.get(n[4], n[5], n[6])
            if c is not None and c[3] != NONE:
                self.put(n[4], n[5], n[6], (c[0], c[1], NONE, NONE))
        for (z, up, down, lu, ld) in self._stairs:
            lu = lu or self._land(down[0], down[1], z + 1)
            ld = ld or self._land(up[0], up[1], z)
            self.trans.append((up[0], up[1], z, 'STAIRS', lu[0], lu[1], z + 1))
            self.trans.append((down[0], down[1], z + 1, 'STAIRS', ld[0], ld[1], z))
        for (z, at, top) in self._ladders:
            lu = self._land(top[0], top[1], z + 1)
            ld = self._land(at[0], at[1], z)
            self.trans.append((at[0], at[1], z, 'LADDER', lu[0], lu[1], z + 1))
            self.trans.append((top[0], top[1], z + 1, 'HOLE', ld[0], ld[1], z))
        self._stairs, self._ladders = [], []

    def reach(self, starts, through_doors=True):
        """Pola osiagalne z starts (x, y, z): 4 kierunki i przejscia; drzwi poziomu - wg through_doors."""
        tr = {(t[0], t[1], t[2]): (t[4], t[5], t[6]) for t in self.trans}
        for (a, b) in self.links:
            tr.setdefault(a, b)
        doors = {(d[0], d[1], d[2]) for d in self.doors}
        seen = set(starts)
        q = deque(starts)
        while q:
            x, y, z = q.popleft()
            nxt = [(x + dx, y + dy, z) for dx, dy in DIRS4]
            if (x, y, z) in tr:
                nxt.append(tr[(x, y, z)])
            for n in nxt:
                if n in seen:
                    continue
                if not through_doors and n in doors:
                    continue
                c = self.get(*n)
                if walk(c) or n in tr:
                    seen.add(n)
                    q.append(n)
        return seen

    def trail(self, z, target, reached):
        """Sciezka bez drzew i krzakow od target do najblizszego osiagalnego pola (lesne sciezki do spawnow)."""
        prev = {target: None}
        q = deque([target])
        end = None
        while q:
            p = q.popleft()
            if p in reached:
                end = p
                break
            for dx, dy in DIRS4:
                n = (p[0] + dx, p[1] + dy)
                c = self.get(n[0], n[1], z)
                if n in prev or c is None or c[3] != NONE or c[0] in ('water', 'chasm', 'lava', 'swamp'):
                    continue
                prev[n] = p
                q.append(n)
        assert end is not None, f'{self.name}: no trail to {target}'
        p = end
        while p is not None:
            c = self.get(p[0], p[1], z)
            if not walk(c):
                self.put(p[0], p[1], z, (c[0], c[1], NONE, NONE))
            p = prev[p]

    def snap(self):
        """Spawny na najblizsze wolne pole (bez obiektu) tego samego pietra."""
        out = []
        for (m, x, y, z, r, n, resp) in self.spawns:
            c = self.get(x, y, z)
            if not (walk(c) and c[2] == NONE):
                cands = [(abs(a - x) + abs(b - y), a, b) for (a, b), d in self.f[z].items()
                         if walk(d) and d[2] == NONE and abs(a - x) <= 8 and abs(b - y) <= 8]
                assert cands, f'{self.name}: no room for spawn {m} at {(x, y, z)}'
                _d, x, y = min(cands)
            out.append((m, x, y, z, r, n, resp))
        self.spawns = out

    def check(self):
        """Wszystko osiagalne od wejsc; za drzwiami poziomu tylko przez drzwi. Zwraca liczbe osiagalnych pol."""
        self.finish()
        self.snap()
        starts = [(x, y, 0) for (x, y) in self.entries]
        seen = self.reach(starts)
        missing = []
        for s in self.spawns:
            if (s[1], s[2], s[3]) not in seen:
                missing.append(('spawn', s))
        for t in self.trans:
            if (t[0], t[1], t[2]) not in seen:
                missing.append(('transition', t))
            c = self.get(t[0], t[1], t[2])
            if c is None or c[2] not in F.STAIRS:
                missing.append(('transition without stairs (overwritten?)', t, c))
        for c in self.chests:
            if not any((c[0] + dx, c[1] + dy, c[2]) in seen for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                missing.append(('chest', c))
        for n in self.npcs:
            if not any((n[4] + dx, n[5] + dy, n[6]) in seen for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                missing.append(('npc', n[0]))
        assert not missing, f'{self.name}: unreachable {missing[:6]}'
        if self.behind:
            closed = self.reach(starts, through_doors=False)
            for p in self.behind:
                assert p in seen, f'{self.name}: {p} behind the level door unreachable'
                assert p not in closed, f'{self.name}: {p} reachable without the level door'
        return len(seen)


# ---------------------------------------------------------------------- kladzenie na swiecie
def free_under(world, masks, x0, y0, S, step=2, limit=200, avoid=(), per_floor=False):
    """Najblizszy (x0, y0) lewy gorny rog, w ktorym maski pieter podziemi trafiaja w puste i niezajete pola,
    a w calym prostokacie (region podziemi) nie ma cudzych spawnow ``avoid`` [(x, y, z)]. per_floor: cudze spawny
    sprawdzane tylko w prostokacie zajetym na danym pietrze (plan z osobnym regionem na kazde pietro)."""
    h, w = next(iter(masks.values())).shape
    boxes = {}
    if per_floor:
        for z, m in masks.items():
            ys, xs = np.nonzero(m)
            boxes[z] = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))
    for r in range(0, limit):
        ring = [(dx, dy) for dx in range(-r, r + 1) for dy in range(-r, r + 1) if max(abs(dx), abs(dy)) == r]
        ring.sort(key=lambda d: d[0] * d[0] + d[1] * d[1])
        for (ddx, ddy) in ring:
            nx, ny = x0 + ddx * step, y0 + ddy * step
            if nx < 3 or ny < 3 or nx + w > S - 3 or ny + h > S - 3:
                continue
            if per_floor:
                ok = not any(z in boxes and nx + boxes[z][0] - 2 <= x <= nx + boxes[z][2] + 2
                             and ny + boxes[z][1] - 2 <= y <= ny + boxes[z][3] + 2 for (x, y, z) in avoid)
            else:
                ok = not any(z in masks and nx - 2 <= x < nx + w + 2 and ny - 2 <= y < ny + h + 2 for (x, y, z) in avoid)
            for z, m in masks.items():
                if not ok:
                    break
                if (world.occ[z][ny:ny + h, nx:nx + w] & m).any():
                    ok = False
                    break
                ys, xs = np.nonzero(m)
                g = world.floors[z]
                if any(g[ny + yy][nx + xx] is not None for yy, xx in zip(ys[::7], xs[::7])):
                    ok = False
                    break
            if ok:
                return nx, ny
    raise AssertionError('no free room underground')


def stamp(ctx, plan, origin, mark, per_floor=False):
    """Kladzie plan: (0, 0) planu -> origin na powierzchni i wyzej; podziemia najblizej pod spodem.
    mark(world, masks, x, y) - jak generate_continent.mark. Zwraca funkcje plan -> swiat dla kazdego pietra."""
    world = ctx['world']
    S = len(world.floors[0])
    plan.finish()
    ox, oy = origin
    under = [z for z in plan.f if z < 0]
    ux, uy = ox, oy
    if under:
        bx0 = min(plan.bounds(z)[0] for z in under)
        by0 = min(plan.bounds(z)[1] for z in under)
        bx1 = max(plan.bounds(z)[2] for z in under)
        by1 = max(plan.bounds(z)[3] for z in under)
        w, h = bx1 - bx0 + 1, by1 - by0 + 1
        masks = {}
        for z in under:
            m = np.zeros((h, w), bool)
            for (x, y) in plan.f[z]:
                m[y - by0, x - bx0] = True
            masks[z] = m
        avoid = []
        for ln in ctx['spawns']:
            if not ln.startswith('#'):
                f = ln.split(';')
                avoid.append((int(f[1]), int(f[2]), int(f[3])))
        # przypiete podziemia (ctx['under_pin'][nazwa planu] = swiat pola (0, 0) planu pod ziemia): szukanie zaczyna
        # sie tam - budowla zostaje na swoim miejscu, gdy zmiany gdzie indziej zwolnia jej dawne sasiedztwo
        pin = ctx.get('under_pin', {}).get(plan.name)
        sx, sy = (pin[0] + bx0, pin[1] + by0) if pin else (ox + bx0, oy + by0)
        nx, ny = free_under(world, masks, sx, sy, S, avoid=avoid, per_floor=per_floor)
        mark(world, masks, nx, ny)
        ux, uy = nx - bx0, ny - by0
    up = [z for z in plan.f if z > 0]
    for z in up:
        for (x, y) in plan.f[z]:
            assert world.get(ox + x, oy + y, z) is None, f'{plan.name}: floor {z} taken at {(ox + x, oy + y)}'
    if up:
        bx0 = min(plan.bounds(z)[0] for z in up)
        by0 = min(plan.bounds(z)[1] for z in up)
        bx1 = max(plan.bounds(z)[2] for z in up)
        by1 = max(plan.bounds(z)[3] for z in up)
        masks = {}
        for z in up:
            m = np.zeros((by1 - by0 + 1, bx1 - bx0 + 1), bool)
            for (x, y) in plan.f[z]:
                m[y - by0, x - bx0] = True
            masks[z] = m
        mark(world, masks, ox + bx0, oy + by0)

    def W(x, y, z):
        return (x + ux, y + uy) if z < 0 else (x + ox, y + oy)
    for z, cells in plan.f.items():
        for (x, y), c in cells.items():
            world.put(*W(x, y, z), z, c)
    for (x, y, z, t, tx, ty, tz) in plan.trans:
        a, b = W(x, y, z), W(tx, ty, tz)
        ctx['transitions'].append((a[0], a[1], z, t, b[0], b[1], tz))
    for (m, x, y, z, r, n, resp) in plan.spawns:
        a = W(x, y, z)
        ctx['spawns'].append(f'{m};{a[0]};{a[1]};{z};{r};{n};{resp}')
    for (x, y, z, gold, item, n) in plan.chests:
        a = W(x, y, z)
        ctx.setdefault('loot_chests', []).append((a[0], a[1], z, gold, item, n))
    for (x, y, z, lv) in plan.doors:
        a = W(x, y, z)
        ctx.setdefault('level_doors', []).append((a[0], a[1], z, lv))
    for (x0, y0, x1, y1, z) in plan.pz:
        a, b = W(x0, y0, z), W(x1, y1, z)
        ctx['pz'].append((a[0], a[1], b[0], b[1], z))
    for n in plan.npcs:
        a = W(n[4], n[5], n[6])
        ctx['npcs'].append([n[0], n[1], n[2], n[3], a[0], a[1], n[6], *n[7:10]])
    for (name, x0, y0, x1, y1, z0, z1, lo, hi) in plan.regions:
        a, b = W(x0, y0, z0), W(x1, y1, z0)
        ctx.setdefault('regions_new', []).insert(0, (name, a[0], a[1], b[0], b[1], z0, z1, lo, hi))
        if z0 < 0:
            ctx.setdefault('dungeons', []).append((name, a[0], a[1], b[0], b[1], z0, z1))
    return W


def clear_surface(ctx, x0, y0, x1, y1):
    """Usuwa spawny powierzchni w prostokacie (nowa budowla zajmuje to miejsce)."""
    keep = []
    for ln in ctx['spawns']:
        if ln.startswith('#'):
            keep.append(ln)
            continue
        p = ln.split(';')
        if p[3] == '0' and x0 <= int(p[1]) <= x1 and y0 <= int(p[2]) <= y1:
            continue
        keep.append(ln)
    ctx['spawns'] = keep


def find_site(ctx, helpers, cost, near, w, h, core=None, radius=110, biomes=None, max_water=0.05, why=None, min_temple=70, bounds=None):
    """Lewy gorny rog miejsca w x h (z pasem dookola) najblizej near: dziki lad bez budynkow, przejsc, miast i wiosek,
    malo wody i gor, osiagalny pieszo z miast. core = (dx0, dy0, dx1, dy1) - srodek, w ktorym nie moze byc drogi
    (pas dookola moze ja przecinac - zostaje przechodni). biomes - pozadane biomy (T.biome)."""
    world, T = ctx['world'], ctx['T']
    S = len(world.floors[0])
    natural = helpers['natural']
    trans = {(t[0], t[1]) for t in ctx['transitions'] if t[2] == 0}
    towns = list(helpers['town_rects'].values()) + [v['rect'] for v in ctx.get('villages', [])]
    pz = [(a, b, c, d) for (a, b, c, d, z) in ctx['pz'] if z == 0]
    houses = [(int(r[3]), int(r[4]), int(r[5]), int(r[6])) for r in ctx['houses'] if len(r) > 7 and str(r[7]) == '0']
    reach = helpers['reach']
    core = core or (0, 0, w - 1, h - 1)
    why = why if why is not None else {}
    best = None
    for r in range(0, radius, 3):
        for dy in range(-r, r + 1, 3):
            for dx in range(-r, r + 1, 3):
                if max(abs(dx), abs(dy)) != r and r:
                    continue
                X0, Y0 = near[0] + dx - w // 2, near[1] + dy - h // 2
                X1, Y1 = X0 + w - 1, Y0 + h - 1
                if X0 < 8 or Y0 < 8 or X1 > S - 8 or Y1 > S - 8 or (
                        bounds and (X0 < bounds[0] or Y0 < bounds[1] or X1 > bounds[2] or Y1 > bounds[3])):
                    why['edge'] = why.get('edge', 0) + 1
                    continue
                if any(max(abs(t[0] - (X0 + X1) // 2) - w // 2, abs(t[1] - (Y0 + Y1) // 2) - h // 2) < min_temple
                       for _n, t in ctx['towns']):
                    why['temple'] = why.get('temple', 0) + 1
                    continue
                if any(not (X1 + 4 < a or c < X0 - 4 or Y1 + 4 < b or d < Y0 - 4) for (a, b, c, d) in towns + pz + houses):
                    why['town'] = why.get('town', 0) + 1
                    continue
                cx0, cy0, cx1, cy1 = X0 + core[0], Y0 + core[1], X0 + core[2], Y0 + core[3]
                if (cost[cy0:cy1 + 1, cx0:cx1 + 1] <= 0.35).any():
                    why['road'] = why.get('road', 0) + 1
                    continue
                bad = water = 0
                n = 0
                ok = True
                for y in range(Y0, Y1 + 1):
                    for x in range(X0, X1 + 1):
                        if (x, y) in trans:
                            ok = False
                            break
                        if (x + y) % 2:
                            continue
                        n += 1
                        c = world.get(x, y)
                        if c[0] in ('water', 'deep_water', 'swamp', 'lava'):
                            water += 1
                        elif not natural(c):
                            if c[3] in ('mountain', 'snow_mountain', 'sand_cliff'):
                                bad += 1
                            elif c[3] == NONE and c[0] in ('dirt', 'field', 'bridge', 'cobblestone') and c[2] in (NONE, 'tree', 'bush', 'rock'):
                                bad += 0.3
                            else:
                                ok = False
                                break
                        elif biomes and str(T.biome[y, x]) not in biomes:
                            bad += 1
                    if not ok:
                        break
                if not ok:
                    why['built'] = why.get('built', 0) + 1
                    continue
                if water > n * max_water or bad > n * 0.15:
                    why['terrain'] = why.get('terrain', 0) + 1
                    continue
                for z in [k for k in world.floors if k > 0]:
                    g = world.floors[z]
                    if any(g[y][x] is not None for y in range(Y0, Y1 + 1, 2) for x in range(X0, X1 + 1, 2)):
                        ok = False
                        break
                if not ok:
                    why['upper'] = why.get('upper', 0) + 1
                    continue
                if not reach[Y0:Y1 + 1, X0:X1 + 1].any():
                    why['reach'] = why.get('reach', 0) + 1
                    continue
                score = water * 3 + bad + r * 0.6
                if best is None or score < best[0]:
                    best = (score, X0, Y0)
        if best is not None and best[0] < r * 0.6 + 25:
            break
    assert best is not None, f'no site near {near}: {why}'
    return best[1], best[2]
