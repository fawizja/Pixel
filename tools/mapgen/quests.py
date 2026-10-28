"""Zadania z oltarzami profesji i drzwi poziomu (Darek 2026-10-28).

Grobowiec Pieciu Profesji pod Pustynia Qasirah: na pustyni kamienna kapliczka z drzwiami poziomu 20 i schodami w dol,
trzy pietra grobowca (-1..-3) z potworami na druzyne pieciu postaci od poziomu 20, na -3 za drzwiami sala z piecioma
oltarzami (na kazdym przedmiot profesji, przed kazdym pole, nad kazdym pochodnia na murze, domyslnie zgaszona)
i dzwignia na prawo od pola rycerza. Dzwignia przenosi piec postaci do skarbca (osobna sala bez wejscia) z piecioma
skrzyniami po 20 000 gp; ze skarbca schody prowadza na -2.

Wynik w ctx: level_doors (x, y, z, poziom), altar_quests (wiersze altar_quests.csv), quest_displays (x, y, z, przedmiot),
transitions, spawns, pz, regions_new, dungeons. Logika zadania jest w Javie (world/AltarQuest, GameSession).
"""
from collections import deque

import features as F

NONE = '-'

# grobowiec pod piaskiem: piaskowiec, kosci, pajeczyny
F.CAVE_STYLES.setdefault('sandtomb', ('sandstone_floor', 'sandstone_wall', ['bones', 'cobweb', 'debris'],
                                      ['rubble', 'sandstone_rock', 'broken_wall'], 'wall_torch'))

QUEST_ID = 'desert'
QUEST_NAME = 'Grobowiec Pięciu Profesji'
MIN_LEVEL = 20
GOLD = 20000
# oltarze od lewej: dzwignia stoi na prawo od pola rycerza (miedzy rycerzem a druidem)
ORDER = [('PALADIN', 'hunting_bow'), ('SORCERER', 'wand_of_embers'), ('KNIGHT', 'knight_sword'),
         ('DRUID', 'thornroot_rod'), ('MONK', 'iron_knuckles')]
W, H = 76, 50
# punkty pieter w ukladzie lokalnym (pierwszy: schody w gore, ostatni: schody w dol = pierwszy punkt nizej)
PTS1 = [(8, 8), (26, 8), (46, 10), (64, 14), (62, 32), (40, 28), (20, 40)]
HALL = (51, 5)          # lewy gorny rog sali oltarzy (z murem), 17 x 8
VAULT = (51, 24)        # lewy gorny rog skarbca (z murem), 17 x 7
VAULT_EXIT = (VAULT[0] + 8, VAULT[1] + 5)
PTS2 = [(20, 40), (8, 24), (26, 12), (48, 8), (66, 16), VAULT_EXIT, (40, 42), (14, 44)]
PTS3 = [(14, 44), (32, 40), (12, 26), (28, 16)]
# potwory na druzyne pieciu postaci od poziomu 20: (potwor, promien, liczba, punkt)
SPAWNS = {
    -1: [('skeleton', 4, 3, PTS1[1]), ('hyena', 4, 2, PTS1[2]), ('skeleton', 4, 3, PTS1[3]),
         ('hyena', 3, 2, PTS1[4]), ('cave_troll', 3, 1, PTS1[5])],
    -2: [('cave_troll', 4, 3, PTS2[1]), ('hyena', 4, 3, PTS2[2]), ('crystal_golem', 3, 2, PTS2[3]),
         ('cave_troll', 4, 2, PTS2[4]), ('skeleton', 4, 3, PTS2[6])],
    -3: [('scorpion', 4, 2, PTS3[1]), ('crystal_golem', 4, 2, PTS3[2]), ('scorpion', 3, 2, PTS3[3]),
         ('cave_troll', 3, 2, (44, 17))],
}
REGION_LEVELS = (20, 60)


def _hall(grid, rng):
    """Sala oltarzy w siatce pietra -3 (uklad lokalny). Zwraca opis pol."""
    floor, wall = 'marble_floor', 'sandstone_wall'
    x0, y0 = HALL
    for y in range(8):
        for x in range(17):
            edge = x in (0, 16) or y in (0, 7)
            grid[y0 + y][x0 + x] = (floor, NONE, NONE, wall) if edge else (floor, NONE, NONE, NONE)
    slots = []
    for i, (voc, item) in enumerate(ORDER):
        ax = x0 + 2 + 3 * i
        grid[y0][ax + 1] = (floor, NONE, 'wall_torch', wall)        # pochodnia na murze obok oltarza
        grid[y0 + 1][ax] = (floor, NONE, 'altar', NONE)
        grid[y0 + 2][ax] = (floor, 'carpet', NONE, NONE)             # pole dla profesji
        slots.append(dict(voc=voc, item=item, torch=(ax + 1, y0), altar=(ax, y0 + 1), stand=(ax, y0 + 2)))
    knight = next(s for s in slots if s['voc'] == 'KNIGHT')
    lever = (knight['stand'][0] + 1, knight['stand'][1])
    grid[lever[1]][lever[0]] = (floor, NONE, 'lever', NONE)
    for (cx, cy) in ((x0 + 1, y0 + 6), (x0 + 15, y0 + 6)):
        grid[cy][cx] = (floor, NONE, 'candle', NONE)
    door = (x0 + 8, y0 + 7)
    grid[door[1]][door[0]] = (floor, NONE, 'door_open', NONE)
    return slots, lever, door


def _vault(grid, slots):
    floor, wall = 'marble_floor', 'sandstone_wall'
    x0, y0 = VAULT
    for y in range(7):
        for x in range(17):
            edge = x in (0, 16) or y in (0, 6)
            grid[y0 + y][x0 + x] = (floor, NONE, NONE, wall) if edge else (floor, NONE, NONE, NONE)
    for i, s in enumerate(slots):
        cx = x0 + 2 + 3 * i
        s['chest'] = (cx, y0 + 1)                                     # skrzynia (rysowana z TreasureChests)
        s['case'] = (cx + 1, y0 + 1)                                  # gablota z przedmiotem profesji obok
        grid[y0 + 1][cx + 1] = (floor, NONE, 'showcase', NONE)
        s['target'] = (cx, y0 + 2)
    for tx in (x0 + 4, x0 + 7, x0 + 10, x0 + 13):
        grid[y0][tx] = (floor, NONE, 'wall_torch', wall)
    grid[VAULT_EXIT[1]][VAULT_EXIT[0]] = (floor, NONE, 'stairs_up', NONE)


def _corridor(grid, door):
    """Korytarz (2 pola) od drzwi sali w dol i na zachod do ostatniej komory pietra -3."""
    floor, wall = 'sandstone_floor', 'sandstone_wall'
    cells = set()
    dx, dy = door
    for y in range(dy + 1, 19):
        cells |= {(dx, y), (dx + 1, y)}
    for x in range(PTS3[-1][0], dx + 2):
        cells |= {(x, 17), (x, 18)}
    for (x, y) in cells:
        grid[y][x] = (floor, NONE, NONE, NONE)
    for (x, y) in cells:
        for ax in (-1, 0, 1):
            for ay in (-1, 0, 1):
                nx, ny = x + ax, y + ay
                if grid[ny][nx] is None:
                    grid[ny][nx] = (floor, NONE, NONE, wall)


def _local_reach(grid, start):
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for ax, ay in F.DIRS4:
            n = (x + ax, y + ay)
            if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and F.walk(grid[n[1]][n[0]]):
                seen.add(n)
                q.append(n)
    return seen


def _site(world, helpers, near):
    """Miejsce na kapliczke 5x5 na pustyni: piasek, wolne pola wokol, osiagalne pieszo z Varden."""
    S = len(world.floors[0])
    reach = helpers['reach']
    best = None
    for r in range(0, 80):
        for y in range(near[1] - r, near[1] + r + 1):
            for x in range(near[0] - r, near[0] + r + 1):
                if max(abs(x - near[0]), abs(y - near[1])) != r:
                    continue
                if not (8 < x < S - 8 and 8 < y < S - 8):
                    continue
                ok = True
                for yy in range(y - 1, y + 7):
                    for xx in range(x - 1, x + 6):
                        c = world.get(xx, yy)
                        if not helpers['standable'](world, xx, yy) or c[0] != 'sand' or helpers['in_pz'](xx, yy) \
                                or helpers['footprint'][yy, xx]:
                            ok = False
                            break
                    if not ok:
                        break
                if ok and reach[y + 6, x + 2]:
                    best = (x, y)
                    break
            if best:
                break
        if best:
            break
    assert best is not None, 'no desert site for the quest'
    return best


def tomb_of_five(ctx, rng, helpers):
    """Grobowiec Pieciu Profesji. helpers: standable, in_pz, reach (np. bool SxS), footprint, find_free, mark."""
    world = ctx['world']
    levels = [(-1, PTS1), (-2, PTS2), (-3, PTS3)]
    grids = {}
    for z, pts in levels:
        g = F.carve_crypt(W, H, pts, 'sandtomb', rng)
        F.fix_lava(g, pts)
        grids[z] = g
    g3 = grids[-3]
    slots, lever, door = _hall(g3, rng)
    _corridor(g3, door)
    _vault(g3, slots)
    seen = _local_reach(g3, (PTS3[0][0], PTS3[0][1] + 1))
    assert (door[0], door[1] - 1) in seen and all(s['stand'] in seen for s in slots), 'altar hall not reachable'
    assert not any(s['target'] in seen for s in slots), 'vault must not be reachable on foot'
    assert VAULT_EXIT in PTS2 and F.walk(grids[-2][VAULT_EXIT[1]][VAULT_EXIT[0]])
    # powierzchnia: kapliczka z drzwiami poziomu
    sx, sy = _site(world, helpers, (178, 412))
    # spawny powierzchni nie moga trafic do kapliczki
    ctx['spawns'] = [ln for ln in ctx['spawns'] if ln.startswith('#') or not (
        ln.split(';')[3] == '0' and sx - 1 <= int(ln.split(';')[1]) <= sx + 5 and sy - 1 <= int(ln.split(';')[2]) <= sy + 6)]
    stairs = (sx + 2, sy + 2)
    masks = {}
    import numpy as np
    for z, g in grids.items():
        m = np.zeros((H, W), bool)
        for y in range(H):
            for x in range(W):
                m[y, x] = g[y][x] is not None
        masks[z] = m
    tx, ty = helpers['find_free'](world, masks, stairs[0] - PTS1[0][0], stairs[1] - PTS1[0][1])
    helpers['mark'](world, masks, tx, ty)
    for z, g in grids.items():
        for y in range(H):
            for x in range(W):
                if g[y][x] is not None:
                    world.put(tx + x, ty + y, z, g[y][x])
    for y in range(5):
        for x in range(5):
            edge = x in (0, 4) or y in (0, 4)
            world.put(sx + x, sy + y, 0, ('sandstone_floor', NONE, NONE, 'sandstone_wall') if edge
                      else ('sandstone_floor', NONE, NONE, NONE))
    ldoor = (sx + 2, sy + 4)
    world.put(*ldoor, 0, ('sandstone_floor', NONE, 'door_level', NONE))
    world.put(*stairs, 0, ('sandstone_floor', NONE, 'stairs_down', NONE))
    for (ox, oy) in ((sx - 1, sy + 4), (sx + 5, sy + 4)):
        world.put(ox, oy, 0, ('sand', NONE, 'sandstone_rock', NONE))   # glazy przy rogach kapliczki
    world.put(sx + 2, sy + 5, 0, ('sandstone_floor', NONE, NONE, NONE))
    t = ctx['transitions']

    def G(p):
        return (tx + p[0], ty + p[1])
    # schody: powierzchnia <-> -1 <-> -2 <-> -3
    up1 = G(PTS1[0])
    world.put(*up1, -1, ('sandstone_floor', NONE, 'stairs_up', NONE))
    t.append((stairs[0], stairs[1], 0, 'STAIRS', up1[0], up1[1] + 1, -1))
    t.append((up1[0], up1[1], -1, 'STAIRS', stairs[0], stairs[1] + 1, 0))
    for z, pts, nxt in ((-1, PTS1, PTS2), (-2, PTS2, PTS3)):
        d = G(pts[-1])
        assert pts[-1] == nxt[0]
        world.put(*d, z, ('sandstone_floor', NONE, 'stairs_down', NONE))
        world.put(*d, z - 1, ('sandstone_floor', NONE, 'stairs_up', NONE))
        t.append((d[0], d[1], z, 'STAIRS', d[0], d[1] + 1, z - 1))
        t.append((d[0], d[1], z - 1, 'STAIRS', d[0] + 1, d[1], z))
    ex = G(VAULT_EXIT)
    t.append((ex[0], ex[1], -3, 'STAIRS', ex[0], ex[1], -2))
    # potwory w komorach z dala od schodow
    for z, pts in levels:
        g = grids[z]
        cells = [(x, y) for y in range(H) for x in range(W)
                 if g[y][x] is not None and F.walk(g[y][x]) and g[y][x][2] == NONE
                 and all(abs(x - px) + abs(y - py) > 5 for (px, py) in (pts[0], pts[-1]))
                 and not (z == -3 and HALL[0] <= x <= HALL[0] + 16 and HALL[1] <= y <= HALL[1] + 7)
                 and not (z == -3 and VAULT[0] <= x <= VAULT[0] + 16 and VAULT[1] <= y <= VAULT[1] + 6)
                 and abs(x - VAULT_EXIT[0]) + abs(y - VAULT_EXIT[1]) > 5]
        for (monster, radius, count, want) in SPAWNS[z]:
            cx, cy = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]))
            ctx['spawns'].append(f'{monster};{tx + cx};{ty + cy};{z};{radius};{count};180')
    # strefy ochronne: sala oltarzy i skarbiec (bez potworow)
    hall = (tx + HALL[0] + 1, ty + HALL[1] + 1, tx + HALL[0] + 15, ty + HALL[1] + 6, -3)
    vault = (tx + VAULT[0] + 1, ty + VAULT[1] + 1, tx + VAULT[0] + 15, ty + VAULT[1] + 5, -3)
    ctx['pz'] += [hall, vault]
    ctx.setdefault('regions_new', []).insert(0, (QUEST_NAME, tx, ty, tx + W - 1, ty + H - 1, -3, -1, *REGION_LEVELS))
    ctx.setdefault('dungeons', []).append((QUEST_NAME, tx, ty, tx + W - 1, ty + H - 1, -3, -1))
    ctx.setdefault('level_doors', []).append((ldoor[0], ldoor[1], 0, MIN_LEVEL))

    def P(p, z=-3):
        q = G(p)
        return f'{q[0]},{q[1]},{z}'
    lines = [f'quest;{QUEST_ID};{QUEST_NAME};{GOLD};{P(lever)};{",".join(str(v) for v in hall)};'
             f'{",".join(str(v) for v in vault)}']
    disp = ctx.setdefault('quest_displays', [])
    for s in slots:
        lines.append(f'altar;{QUEST_ID};{s["voc"]};{P(s["altar"])};{P(s["stand"])};{P(s["torch"])};{s["item"]};'
                     f'{P(s["target"])};{P(s["chest"])}')
        disp.append((*G(s['altar']), -3, s['item']))
        disp.append((*G(s['case']), -3, s['item']))
    ctx.setdefault('altar_quests', []).extend(lines)
    info = dict(entrance=stairs, level_door=ldoor, origin=(tx, ty), lever=G(lever),
                stands={s['voc']: G(s['stand']) for s in slots}, vault=vault, hall=hall, vault_exit=ex)
    ctx['tomb_of_five'] = info
    return info
