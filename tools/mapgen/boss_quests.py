"""Wyprawy na bossa w piec postaci (Darek 2026-10-28): cztery lochy w roznych krainach.

Kazda wyprawa: na powierzchni kapliczka z drzwiami poziomu (napis "Tylko dla poziomu N+") i schodami w dol, pod ziemia
loch z potworami pasujacymi do progu, na koncu przedsionek z piecioma oznaczonymi polami (boss_plate) i dzwignia,
obok zamknieta arena bez wejscia (piec pol docelowych teleportu, miejsce bossa, portal wyjscia w rogu).
Logika (dzwignia tylko przy pieciu postaciach, boss, lup, raz na postac) jest w Javie: world/BossQuest
i game/BossQuestRuntime. Wynik w ctx: boss_quests (wiersze boss_quests.csv), level_doors, transitions, spawns, pz,
regions_new, dungeons.
"""
from collections import deque

import features as F
import numpy as np

NONE = '-'

W, H = 72, 48
# lokalny uklad lochu: punkty komor (pierwszy: schody w gore), ostatni prowadzi do przedsionka
PTS = [(6, 6), (20, 10), (36, 7), (52, 12), (42, 22), (30, 26)]
LOBBY = (24, 32)        # lewy gorny rog przedsionka (z murem), 15 x 11
LOBBY_W, LOBBY_H = 15, 11
ARENA = (46, 28)        # lewy gorny rog areny (z murem), 21 x 17
ARENA_W, ARENA_H = 21, 17

# wyprawy: id, nazwa, poziom, boss, styl lochu, kraina (podloze, mur i podloga kapliczki), gdzie szukac miejsca,
# potwory w komorach (potwor, promien, liczba) - po jednej grupie na komore 1..4
QUESTS = [
    dict(id='grimtooth', name='Leże Grimtootha', level=30, boss='grimtooth', style='orc', z=-1,
         grounds={'grass'}, wall='wood_wall', floor='wood_floor', near=(610, 395), lava=0.0,
         levels=(25, 60),
         spawns=[('skeleton', 3, 3), ('cave_troll', 3, 2), ('skeleton', 3, 3), ('crystal_golem', 3, 2), ('cave_troll', 3, 3)]),
    dict(id='frost', name='Lodowy Tron Ysolde', level=100, boss='frost_queen', style='ice', z=-1,
         grounds={'snow'}, wall='log_wall', floor='wood_floor', near=(625, 95), lava=0.0,
         levels=(85, 160),
         spawns=[('yeti', 3, 3), ('frost_witch', 3, 3), ('yeti', 3, 3), ('ice_golem', 3, 3), ('frost_witch', 3, 2)]),
    dict(id='pharaoh', name='Krypta Neferakha', level=200, boss='pharaoh', style='crypt', z=-1,
         grounds={'sand', 'dark_sand'}, wall='sandstone_wall', floor='sandstone_floor', near=(105, 395), lava=0.0,
         levels=(160, 240),
         spawns=[('sand_wraith', 3, 3), ('mummy', 3, 3), ('mummy', 3, 3), ('sand_wraith', 3, 3), ('mummy', 3, 2)]),
    dict(id='ignaroth', name='Kuźnia Ignarotha', level=400, boss='ignaroth', style='fire', z=-1,
         grounds={'ash'}, wall='stone_wall', floor='stone_floor', near=(230, 150), lava=0.02,
         levels=(380, 480),
         spawns=[('fire_elemental', 3, 3), ('lava_golem', 3, 3), ('fire_elemental', 3, 3), ('lava_golem', 3, 3), ('lava_golem', 3, 2)]),
]

# podloga i mur przedsionka i areny wg stylu lochu
ROOMS = {
    'orc': ('dirt', 'cave_wall'),
    'ice': ('frozen_floor', 'ice_wall'),
    'crypt': ('tomb_floor', 'stone_wall'),
    'fire': ('stone_floor', 'obsidian_wall'),
}


def _box(grid, x0, y0, w, h, floor, wall):
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            grid[y0 + y][x0 + x] = (floor, NONE, NONE, wall) if edge else (floor, NONE, NONE, NONE)


def _lobby(grid, floor, wall):
    """Przedsionek: piec oznaczonych pol w rzedzie, dzwignia nad srodkowym, drzwi (przejscie) u gory."""
    x0, y0 = LOBBY
    _box(grid, x0, y0, LOBBY_W, LOBBY_H, floor, wall)
    plates = [(x0 + 3 + 2 * i, y0 + 6) for i in range(5)]
    for p in plates:
        grid[p[1]][p[0]] = (floor, 'boss_plate', NONE, NONE)
    lever = (plates[2][0], plates[2][1] - 1)
    grid[lever[1]][lever[0]] = (floor, NONE, 'lever', NONE)
    for (tx, ty) in ((x0 + 3, y0), (x0 + LOBBY_W - 4, y0)):
        grid[ty][tx] = (floor, NONE, 'wall_torch', wall)
    for (cx, cy) in ((x0 + 1, y0 + LOBBY_H - 2), (x0 + LOBBY_W - 2, y0 + LOBBY_H - 2)):
        grid[cy][cx] = (floor, NONE, 'candle', NONE)
    door = (x0 + 6, y0)
    grid[door[1]][door[0]] = (floor, NONE, NONE, NONE)       # przejscie w murze
    exit_target = (door[0], door[1] + 2)
    return plates, lever, door, exit_target


# swiatla areny wg stylu (blokujace stoja z dala od pol druzyny, bossa i portalu)
ARENA_LIGHT = {'orc': 'campfire', 'ice': 'ice_crystal', 'crypt': 'candle', 'fire': 'magma'}


def _arena(grid, floor, wall, style):
    """Zamknieta arena: cele teleportu przy dolnym murze, boss na srodku u gory, portal wyjscia w lewym dolnym rogu."""
    x0, y0 = ARENA
    _box(grid, x0, y0, ARENA_W, ARENA_H, floor, wall)
    targets = [(x0 + 6 + 2 * i, y0 + ARENA_H - 4) for i in range(5)]
    boss = (x0 + ARENA_W // 2, y0 + ARENA_H - 8)       # 4 pola nad druzyna - widac go od razu
    portal = (x0 + 2, y0 + ARENA_H - 3)
    grid[portal[1]][portal[0]] = (floor, NONE, 'boss_portal', NONE)
    for tx in (x0 + 4, x0 + 10, x0 + 16):
        grid[y0][tx] = (floor, NONE, 'wall_torch', wall)
    for (cx, cy) in ((x0 + 2, y0 + 2), (x0 + ARENA_W - 3, y0 + 2), (x0 + ARENA_W - 3, y0 + ARENA_H - 3)):
        grid[cy][cx] = (floor, NONE, 'candle', NONE)
    light = ARENA_LIGHT[style]
    for (cx, cy) in ((x0 + 4, y0 + 4), (x0 + ARENA_W - 5, y0 + 4), (x0 + 3, y0 + ARENA_H - 8),
                     (x0 + ARENA_W - 4, y0 + ARENA_H - 8), (x0 + ARENA_W // 2, y0 + 3)):
        grid[cy][cx] = (floor, NONE, light, NONE)
    return targets, boss, portal


def _corridor(grid, a, door, floor, wall):
    """Korytarz (2 pola) od ostatniej komory do przejscia w murze przedsionka."""
    cells = set()
    ax, ay = a
    dx, dy = door
    for x in range(min(ax, dx), max(ax, dx) + 2):
        cells |= {(x, ay), (x, ay + 1)}
    for y in range(min(ay, dy - 1), max(ay, dy - 1) + 1):
        cells |= {(dx, y), (dx + 1, y)}
    for (x, y) in cells:
        if grid[y][x] is None or grid[y][x][3] != NONE or not F.walk(grid[y][x]):
            grid[y][x] = (floor, NONE, NONE, NONE)
    for (x, y) in cells:
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                nx, ny = x + ox, y + oy
                if grid[ny][nx] is None:
                    grid[ny][nx] = (floor, NONE, NONE, wall)


def _reach(grid, start):
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for ax, ay in F.DIRS4:
            n = (x + ax, y + ay)
            if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and grid[n[1]][n[0]] is not None and F.walk(grid[n[1]][n[0]]):
                seen.add(n)
                q.append(n)
    return seen


def _in_rect(p, corner, w, h, margin=0):
    return corner[0] - margin <= p[0] < corner[0] + w + margin and corner[1] - margin <= p[1] < corner[1] + h + margin


def _site(world, helpers, near, grounds, taken):
    """Miejsce na kapliczke 5x5 na podlozu krainy: wolne pola wokol, osiagalne pieszo z miast."""
    S = len(world.floors[0])
    reach = helpers['reach']
    for r in range(0, 120):
        for y in range(near[1] - r, near[1] + r + 1):
            for x in range(near[0] - r, near[0] + r + 1):
                if max(abs(x - near[0]), abs(y - near[1])) != r:
                    continue
                if not (8 < x < S - 8 and 8 < y < S - 8):
                    continue
                if any(abs(x - tx) < 12 and abs(y - ty) < 12 for (tx, ty) in taken):
                    continue
                ok = True
                for yy in range(y - 1, y + 7):
                    for xx in range(x - 1, x + 6):
                        c = world.get(xx, yy)
                        # drzewa i krzaki w obrysie kapliczki znikaja; pole przed drzwiami musi byc wolne
                        front = xx == x + 2 and yy >= y + 5
                        if c is None or c[0] not in grounds or c[3] != NONE or c[2] in F.STAIRS \
                                or (front and not helpers['standable'](world, xx, yy)) \
                                or helpers['in_pz'](xx, yy) or helpers['footprint'][yy, xx]:
                            ok = False
                            break
                    if not ok:
                        break
                if ok and reach[y + 6, x + 2]:
                    return (x, y)
    raise AssertionError(f'no site for boss quest near {near}')


def _one(ctx, q, rng, helpers, taken):
    world = ctx['world']
    z = q['z']
    floor, wall = ROOMS[q['style']]
    if q['style'] == 'crypt':
        g = F.carve_crypt(W, H, PTS, q['style'], rng)
    else:
        g = F.carve_cave(W, H, PTS, q['style'], rng, room=(3, 4), tunnel=1, lava=q['lava'])
    F.fix_lava(g, PTS)
    plates, lever, door, exit_target = _lobby(g, floor, wall)
    targets, boss, portal = _arena(g, floor, wall, q['style'])
    _corridor(g, PTS[-1], door, floor, wall)
    seen = _reach(g, PTS[0])
    assert all(p in seen for p in PTS), q['id'] + ': cave points not connected'
    assert all(p in seen for p in plates) and (lever[0], lever[1] + 1) in seen, q['id'] + ': lobby not reachable'
    assert exit_target in seen
    assert not any(t in seen for t in targets + [boss, portal]), q['id'] + ': arena must be closed'
    assert all(F.walk(g[t[1]][t[0]]) for t in targets + [boss, portal])
    # powierzchnia: kapliczka z drzwiami poziomu
    sx, sy = _site(world, helpers, q['near'], q['grounds'], taken)
    taken.append((sx, sy))
    ctx['spawns'] = [ln for ln in ctx['spawns'] if ln.startswith('#') or not (
        ln.split(';')[3] == '0' and sx - 2 <= int(ln.split(';')[1]) <= sx + 6 and sy - 2 <= int(ln.split(';')[2]) <= sy + 7)]
    stairs = (sx + 2, sy + 2)
    m = np.zeros((H, W), bool)
    for y in range(H):
        for x in range(W):
            m[y, x] = g[y][x] is not None
    masks = {z: m}
    tx, ty = helpers['find_free'](world, masks, stairs[0] - PTS[0][0], stairs[1] - PTS[0][1])
    helpers['mark'](world, masks, tx, ty)
    for y in range(H):
        for x in range(W):
            if g[y][x] is not None:
                world.put(tx + x, ty + y, z, g[y][x])
    sfloor, swall = q['floor'], q['wall']
    for yy in range(sy - 1, sy + 6):
        for xx in range(sx - 1, sx + 6):
            c = world.get(xx, yy)
            world.put(xx, yy, 0, (c[0], NONE, NONE, NONE))
    for y in range(5):
        for x in range(5):
            edge = x in (0, 4) or y in (0, 4)
            world.put(sx + x, sy + y, 0, (sfloor, NONE, NONE, swall) if edge else (sfloor, NONE, NONE, NONE))
    ldoor = (sx + 2, sy + 4)
    world.put(*ldoor, 0, (sfloor, NONE, 'door_level', NONE))
    world.put(*stairs, 0, (sfloor, NONE, 'stairs_down', NONE))
    world.put(sx + 2, sy + 5, 0, (world.get(sx + 2, sy + 5)[0], NONE, NONE, NONE))

    def G(p):
        return (tx + p[0], ty + p[1])
    up = G(PTS[0])
    world.put(*up, z, (floor, NONE, 'stairs_up', NONE))
    t = ctx['transitions']
    t.append((stairs[0], stairs[1], 0, 'STAIRS', up[0], up[1] + 1, z))
    t.append((up[0], up[1], z, 'STAIRS', stairs[0], stairs[1] + 1, 0))
    # potwory w komorach 1..5 (bez schodow), z dala od przedsionka i areny
    cells = [(x, y) for y in range(H) for x in range(W)
             if g[y][x] is not None and F.walk(g[y][x]) and g[y][x][2] == NONE
             and abs(x - PTS[0][0]) + abs(y - PTS[0][1]) > 6
             and not _in_rect((x, y), LOBBY, LOBBY_W, LOBBY_H, 2) and not _in_rect((x, y), ARENA, ARENA_W, ARENA_H, 1)]
    for (monster, radius, count), want in zip(q['spawns'], PTS[1:]):
        cx, cy = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]))
        ctx['spawns'].append(f'{monster};{tx + cx};{ty + cy};{z};{radius};{count};240')
    lobby = (tx + LOBBY[0] + 1, ty + LOBBY[1] + 1, tx + LOBBY[0] + LOBBY_W - 2, ty + LOBBY[1] + LOBBY_H - 2, z)
    arena = (tx + ARENA[0] + 1, ty + ARENA[1] + 1, tx + ARENA[0] + ARENA_W - 2, ty + ARENA[1] + ARENA_H - 2, z)
    ctx['pz'].append(lobby)                       # przedsionek bez walki (arena nie jest strefa ochronna)
    ctx.setdefault('regions_new', []).insert(0, (q['name'], tx, ty, tx + W - 1, ty + H - 1, z, z, *q['levels']))
    ctx.setdefault('dungeons', []).append((q['name'], tx, ty, tx + W - 1, ty + H - 1, z, z))
    ctx.setdefault('level_doors', []).append((ldoor[0], ldoor[1], 0, q['level']))

    def P(p):
        a = G(p)
        return f'{a[0]},{a[1]},{z}'
    rect = lambda r: ','.join(str(v) for v in r)  # noqa: E731
    lines = [f'quest;{q["id"]};{q["name"]};{q["level"]};{q["boss"]};{P(lever)};{rect(lobby)};{rect(arena)};'
             f'{P(boss)};{P(portal)};{P(exit_target)};{ldoor[0]},{ldoor[1]},0']
    for p, tgt in zip(plates, targets):
        lines.append(f'square;{q["id"]};{P(p)};{P(tgt)}')
    ctx.setdefault('boss_quests', []).extend(lines)
    return dict(id=q['id'], level=q['level'], boss=q['boss'], level_door=ldoor, stairs=stairs, lever=G(lever),
                plates=[G(p) for p in plates], arena=arena, boss_at=G(boss), portal=G(portal), exit=G(exit_target))


def boss_quests(ctx, rng, helpers):
    """Cztery wyprawy na bossa. helpers jak w quests.tomb_of_five."""
    taken = []
    out = []
    for q in QUESTS:
        out.append(_one(ctx, q, rng, helpers, taken))
    ctx['boss_quest_info'] = out
    return out
