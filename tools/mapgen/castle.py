"""Warownia Gorvalda: zamek dla poziomow 20-70 z wejsciem tylko przez podziemia (Darek 2026-11-11).

Zamek stoi na kontynencie glownym z dala od miast, otoczony fosa i zamknietym murem bez bram. Z zewnatrz nie da sie
wejsc: jedyna droga to jaskinia w skalnym wystepie przed fosa, a dalej trzy pietra tuneli (-1 posterunek orkow,
-2 orcze chodniki i rogacze, -3 glebokie tunele rogaczy pod calym zamkiem), z powrotem w gore krotkim chodnikiem (-2)
do piwnicy pod twierdza (-1) i schodami do sali glownej zamku.
  z 0   dziedziniec z koszarami, psiarnia, kuznia, kaplica i stajnia; twierdza (sala glowna, zbrojownia, kuchnia,
        schody z piwnicy); cztery wieze narozne i dwie wieze srodkowe
  z +1  korona murow z wiezami, pietro twierdzy (sala rady i biblioteka)
  z +2  komnaty kasztelana, przedsionek z drzwiami poziomu
  z +3  sala tronowa na szczycie: boss Gorvald i skrzynia zadania (raz na postac)
Potwory na poziomy 20-70: orki, rogacze (horn_*), trolle jaskiniowe, a w zamku straz (keep_*) i kasztelan.
Wszystko osiagalne wedlug Plan.check (od wejscia w jaskini); test sprawdza, ze z zewnatrz nie ma innego wejscia.
"""
import random

import features as F
import structures as St

NONE = '-'
SEED = 20261113
W, H = 64, 54                 # zamek (mury 2 pola); fosa 3 pola dookola
ML, MT, MR, MB = 16, 8, 8, 14  # marginesy miejsca: z lewej i u dolu miejsce na jaskinie wejsciowa
CASTLE = 'Warownia Gorvalda'
UNDER = 'Chodniki pod Warownią'
QUEST_LEVEL = 55
REWARD = (6000, 'croc_legs', 1)
STONE, SWALL, WOOD, WWALL = 'stone_floor', 'stone_wall', 'wood_floor', 'wood_wall'
COBBLE = 'cobblestone'
PIN = (750, 336)              # dawny lewy gorny rog planu zamku na powierzchni
ENTRANCE = (-10, 61)          # pole schodow w dol na powierzchni (w planie)

F.CAVE_STYLES.setdefault('warren', ('cave_floor', 'cave_wall', [], ['rubble', 'crate', 'barrel'], 'wall_torch'))
F.CAVE_STYLES.setdefault('keep_cellar', ('stone_floor', 'stone_wall', ['cobweb'], ['barrel', 'crate', 'rubble'], 'wall_torch'))


def _torch(p, x, y, z):
    c = p.get(x, y, z)
    if c is None or c[3] == NONE:
        return
    for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
        d = p.get(x + dx, y + dy, z)
        if d is not None and St.walk(d) and d[2] == NONE:
            p.put(x + dx, y + dy, z, (d[0], d[1], 'wall_torch', NONE))
            return


def _spawns(p, z, pts, monsters, counts, respawn):
    """Spawn w kazdej komorze (bez pierwszej) na najblizszym wolnym polu."""
    cells = [xy for xy, c in p.f[z].items() if St.walk(c) and c[2] == NONE]
    for i, want in enumerate(pts[1:]):
        x, y = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]))
        p.spawn(monsters[i % len(monsters)], x, y, z, radius=4, count=counts[i % 2], respawn=respawn)


def _clear_stair_cells(p, z, cells):
    for (x, y) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c = p.get(x + dx, y + dy, z)
                if c is None or c[3] != NONE or not St.walk(c):
                    p.put(x + dx, y + dy, z, ('cave_floor', NONE, NONE, NONE))


def castle_plan(world, origin, rng):
    p = St.Plan(CASTLE)
    late = []
    ox, oy = origin

    # ---------------------------------------------------------------- powierzchnia: fosa, mur, dziedziniec
    for y in range(-6, H + 6):
        for x in range(-6, W + 6):
            if 0 <= x < W and 0 <= y < H:
                continue
            c = world.get(ox + x, oy + y)
            d = max(-x - 1 if x < 0 else (x - W if x >= W else -1), -y - 1 if y < 0 else (y - H if y >= H else -1))
            if c is None:
                continue
            if d <= 2:
                p.put(x, y, 0, ('water', NONE, NONE, NONE))
            elif d <= 4:
                p.put(x, y, 0, ('dirt', NONE, NONE, NONE))
            elif c[2] in ('tree', 'pine', 'bush', 'autumn_tree', 'dead_tree', 'jungle_tree') and rng.random() < 0.6:
                p.put(x, y, 0, (c[0], c[1], NONE, NONE))
    for y in range(H):
        for x in range(W):
            ring = min(x, y, W - 1 - x, H - 1 - y)
            if ring < 2:
                p.put(x, y, 0, (STONE, NONE, NONE, SWALL))
            else:
                grass = rng.random() < 0.3 and 8 < x < W - 9 and (y < 8 or y > 34)
                p.put(x, y, 0, ('grass' if grass else COBBLE, rng.choice((NONE,) * 8 + ('tall_grass',)) if grass else NONE, NONE, NONE))

    # ---------------------------------------------------------------- wieze (z0): narozne 9x9, srodkowe 10x8
    corners = {'NW': (0, 0, (8, 5)), 'NE': (W - 9, 0, (W - 9, 5)), 'SW': (0, H - 9, (8, H - 6)), 'SE': (W - 9, H - 9, (W - 9, H - 6))}
    for key, (tx, ty, door) in corners.items():
        p.box(0, tx, ty, 9, 9, STONE, SWALL, doors=[door])
    midN, midS = (27, 0), (27, H - 8)
    p.box(0, midN[0], midN[1], 10, 8, STONE, SWALL, doors=[(32, 7)])
    p.box(0, midS[0], midS[1], 10, 8, STONE, SWALL, doors=[(32, H - 8)])
    for (x, y, o) in ((4, 4, 'barrel'), (W - 5, 4, 'crate'), (4, H - 5, 'crate'), (W - 5, H - 5, 'barrel')):
        p.obj(x, y, 0, o)
    for (x, y) in ((31, 2), (34, 2), (31, H - 3), (34, H - 3)):
        p.obj(x, y, 0, 'banner')

    # ---------------------------------------------------------------- twierdza (z0)
    KX, KY, KW, KH = 16, 10, 32, 22
    p.box(0, KX, KY, KW, KH, STONE, SWALL, doors=[(31, KY + KH - 1)])
    for y in range(11, 21):                       # zbrojownia (NW) i kuchnia (NE)
        p.put(26, y, 0, (STONE, NONE, NONE, SWALL))
        p.put(37, y, 0, (STONE, NONE, NONE, SWALL))
    for x in list(range(17, 27)) + list(range(37, 47)):
        p.put(x, 20, 0, (STONE, NONE, NONE, SWALL))
    for y in range(22, 31):                       # pokoj schodow z piwnicy (SW)
        p.put(26, y, 0, (STONE, NONE, NONE, SWALL))
    for x in range(17, 27):
        p.put(x, 21, 0, (STONE, NONE, NONE, SWALL))
    for (x, y) in ((26, 15), (37, 15), (26, 26)):
        p.put(x, y, 0, (STONE, NONE, 'door_open', NONE))
    for (x, y, o) in ((18, 12, 'barrel'), (18, 18, 'crate'), (24, 12, 'statue'), (24, 18, 'bookcase')):   # zbrojownia
        p.obj(x, y, 0, o)
    for (x, y, o) in ((39, 12, 'table'), (41, 12, 'table'), (44, 12, 'barrel'), (45, 18, 'firewood'), (40, 18, 'counter'),
                      (43, 18, 'sack')):                                                               # kuchnia
        p.obj(x, y, 0, o)
    for (x, y, o) in ((18, 23, 'barrel'), (18, 29, 'crate'), (24, 29, 'barrel'), (24, 23, 'sack')):    # skladzik schodow
        p.obj(x, y, 0, o)
    for y in range(12, 31):                       # dywan w sali glownej
        p.deco(31, y, 0, 'carpet')
    for (x, y) in ((29, 13), (34, 13), (29, 28), (34, 28)):
        p.obj(x, y, 0, 'statue')
    for (x, y) in ((28, 12), (35, 12), (28, 26), (35, 26)):
        p.obj(x, y, 0, 'campfire')
    for (x, y) in ((27, 16), (36, 16), (27, 24), (36, 24)):
        p.obj(x, y, 0, 'banner')
    for (x, y) in ((31, KY), (22, KY), (42, KY), (KX, 18), (KX + KW - 1, 18), (22, KY + KH - 1), (42, KY + KH - 1)):
        _torch(p, x, y, 0)
    late.append((0, (30, 13), (30, 13)))          # na +1

    # ---------------------------------------------------------------- dziedziniec: koszary, psiarnia, kuznia, kaplica, stajnia
    p.box(0, 4, 37, 14, 9, WOOD, WWALL, doors=[(10, 37)])
    for x in range(6, 17, 2):
        p.obj(x, 44, 0, 'bed')
    for (x, y, o) in ((6, 39, 'table'), (9, 39, 'table'), (7, 39, 'chair'), (10, 39, 'chair'), (16, 41, 'barrel')):
        p.obj(x, y, 0, o)
    p.box(0, 22, 42, 12, 8, STONE, SWALL, doors=[(27, 42)])
    for (x, y, o) in ((24, 48, 'forge'), (26, 48, 'anvil'), (31, 48, 'anvil'), (32, 44, 'barrel'), (24, 44, 'lumber')):
        p.obj(x, y, 0, o)
    p.box(0, 50, 12, 12, 10, STONE, SWALL, doors=[(50, 16)])
    for (x, y, o) in ((60, 14, 'altar'), (57, 14, 'candle'), (53, 14, 'candle'), (53, 17, 'bench'), (53, 19, 'bench'),
                      (57, 17, 'bench'), (57, 19, 'bench')):
        p.obj(x, y, 0, o)
    for y in range(13, 21):
        p.deco(60, y, 0, 'carpet')
    p.box(0, 4, 12, 10, 9, WOOD, WWALL, doors=[(13, 16)])
    for (x, y, o) in ((6, 14, 'hay_bale'), (7, 14, 'hay_bale'), (6, 18, 'hay_bale'), (11, 14, 'barrel'), (11, 18, 'crate')):
        p.obj(x, y, 0, o)
    # psiarnia: plot z przerwa od zachodu
    for x in range(40, 57):
        for y in range(36, 47):
            if x in (40, 56) or y in (36, 46):
                if not (x == 40 and y in (40, 41, 42)):
                    p.put(x, y, 0, ('dirt', NONE, 'fence', NONE))
            else:
                p.put(x, y, 0, ('dirt', rng.choice((NONE, NONE, 'bones', 'dry_grass')), NONE, NONE))
    p.obj(42, 38, 0, 'hay_bale')
    p.obj(54, 44, 0, 'hay_bale')
    # studnia, ogniska, beczki na dziedzincu
    p.obj(31, 36, 0, 'well')
    for (x, y, o) in ((20, 34, 'campfire'), (43, 34, 'campfire'), (18, 52 - 2, 'barrel'), (46, 50, 'crate'), (37, 40, 'barrel'),
                      (14, 34, 'crate'), (50, 30, 'barrel')):
        c = p.get(x, y, 0)
        if c is not None and St.walk(c) and c[2] == NONE:
            p.obj(x, y, 0, o)
    for (x, y) in ((6, 37), (15, 37), (24, 42), (32, 42), (52, 12), (60, 12)):
        _torch(p, x, y, 0)

    # ---------------------------------------------------------------- +1: korona murow, wieze, pietro twierdzy
    for y in range(H):
        for x in range(W):
            ring = min(x, y, W - 1 - x, H - 1 - y)
            if ring < 2 and not any(tx <= x < tx + 9 and ty <= y < ty + 9 for (tx, ty, _d) in corners.values()) \
                    and not (27 <= x < 37 and (y < 8 or y >= H - 8)):
                p.put(x, y, 1, (STONE, NONE, 'fence' if ring == 0 else NONE, NONE))
    for (key, (tx, ty, _door)) in corners.items():
        gaps = [((tx + 8) if tx == 0 else tx, 1 if ty == 0 else H - 2), ((1 if tx == 0 else W - 2), (ty + 8) if ty == 0 else ty)]
        p.box(1, tx, ty, 9, 9, STONE, SWALL, gaps=gaps)
    p.box(1, midN[0], midN[1], 10, 8, STONE, SWALL, gaps=[(27, 1), (36, 1)])
    p.box(1, midS[0], midS[1], 10, 8, STONE, SWALL, gaps=[(27, H - 2), (36, H - 2)])
    # schody w wiezach narozne (z0 -> +1)
    p.stairs(0, (3, 3), (4, 3))
    p.stairs(0, (W - 4, 3), (W - 5, 3))
    p.stairs(0, (3, H - 4), (4, H - 4))
    p.stairs(0, (W - 4, H - 4), (W - 5, H - 4))
    for (x, y, o) in ((4, 5, 'banner'), (W - 5, 5, 'banner'), (4, H - 6, 'banner'), (W - 5, H - 6, 'banner')):
        p.obj(x, y, 1, o)
    # pietro twierdzy +1: sala rady (zachod) i biblioteka (wschod)
    p.box(1, KX, KY, KW, KH, STONE, SWALL)
    for y in range(11, 31):
        p.put(31, y, 1, (STONE, NONE, NONE, SWALL))
    for y in (16, 24):
        p.put(31, y, 1, (STONE, NONE, 'door_open', NONE))
    for (x, y, o) in ((20, 14, 'table'), (21, 14, 'table'), (24, 14, 'table'), (25, 14, 'table'), (20, 15, 'chair'), (25, 15, 'chair'),
                      (18, 12, 'banner'), (28, 12, 'banner'), (22, 28, 'barrel'), (26, 28, 'crate')):
        p.obj(x, y, 1, o)
    for (x, y, o) in ((34, 12, 'bookcase'), (36, 12, 'bookcase'), (38, 12, 'bookcase'), (40, 12, 'bookcase'), (42, 12, 'bookcase'),
                      (45, 14, 'bookcase'), (45, 18, 'bookcase'), (35, 20, 'table'), (36, 20, 'table'), (40, 26, 'candle')):
        p.obj(x, y, 1, o)
    late.append((1, (44, 14), (43, 14)))          # na +2
    for (x, y) in ((22, KY), (42, KY), (KX, 18), (KX + KW - 1, 18), (22, KY + KH - 1), (42, KY + KH - 1)):
        _torch(p, x, y, 1)

    # ---------------------------------------------------------------- +2: komnaty kasztelana, przedsionek z drzwiami poziomu
    p.box(2, 18, 12, 28, 18, STONE, SWALL)
    for (x, y, o) in ((20, 14, 'bed'), (21, 14, 'bed'), (24, 14, 'bookcase'), (26, 14, 'table'), (27, 14, 'chair'),
                      (41, 14, 'statue'), (41, 27, 'statue'), (20, 27, 'barrel'), (22, 27, 'crate'), (38, 27, 'campfire')):
        p.obj(x, y, 2, o)
    p.box(2, 26, 18, 9, 7, STONE, SWALL, doors=[(30, 24)])
    p.level_door(2, (30, 24), QUEST_LEVEL, floor=STONE)
    for (x, y, o) in ((27, 19, 'banner'), (33, 19, 'banner'), (27, 22, 'statue'), (33, 22, 'statue')):
        p.obj(x, y, 2, o)
    for y in range(19, 24):
        p.deco(30, y, 2, 'carpet')
    late.append((2, (30, 20), (31, 20)))          # na +3 (za drzwiami poziomu)
    for (x, y) in ((22, 12), (42, 12), (18, 20), (45, 20)):
        _torch(p, x, y, 2)

    # ---------------------------------------------------------------- +3: sala tronowa, boss, skrzynia
    p.box(3, 22, 12, 20, 12, STONE, SWALL)
    for (x, y) in ((24, 14), (39, 14), (24, 21), (39, 21)):
        p.obj(x, y, 3, 'statue')
    for (x, y) in ((27, 13), (36, 13)):
        p.obj(x, y, 3, 'banner')
    for (x, y) in ((26, 17), (37, 17)):
        p.obj(x, y, 3, 'campfire')
    p.obj(31, 13, 3, 'throne')
    for y in range(14, 23):
        p.deco(32, y, 3, 'carpet')
    gold, item, n = REWARD
    p.chests.append((33, 13, 3, gold, item, n))
    p.behind += [(31, 16, 3), (33, 13, 3)]
    p.spawn('castellan_gorvald', 31, 16, 3, radius=2, count=1, respawn=1800)
    p.spawn('keep_sentry', 26, 20, 3, radius=2, count=2, respawn=300)
    p.spawn('keep_sentry', 37, 20, 3, radius=2, count=2, respawn=300)
    for (x, y) in ((22, 17), (41, 17), (31, 12)):
        _torch(p, x, y, 3)

    # ---------------------------------------------------------------- zamkowe spawny
    S0 = [('keep_sentry', 12, 33, 3, 3), ('keep_crossbow', 30, 34, 4, 3), ('keep_hound', 48, 41, 4, 3), ('keep_sentry', 50, 33, 3, 3),
          ('keep_sentry', 31, 24, 3, 3), ('keep_hound', 29, 14, 2, 2), ('keep_hound', 42, 15, 2, 2), ('keep_crossbow', 20, 25, 2, 2),
          ('keep_sentry', 11, 41, 3, 2), ('keep_crossbow', 55, 16, 2, 2), ('keep_sentry', 28, 47, 3, 2), ('keep_hound', 10, 15, 2, 2)]
    S1 = [('keep_crossbow', 20, 1, 2, 2), ('keep_crossbow', 44, 1, 2, 2), ('keep_crossbow', 20, H - 2, 2, 2),
          ('keep_crossbow', 44, H - 2, 2, 2), ('keep_crossbow', 1, 24, 2, 2), ('keep_crossbow', W - 2, 24, 2, 2),
          ('keep_sentry', 24, 16, 3, 3), ('keep_chaplain', 38, 16, 3, 2), ('keep_sentry', 38, 24, 3, 2), ('keep_chaplain', 24, 26, 3, 2),
          ('keep_sentry', 5, 5, 2, 1), ('keep_sentry', W - 6, 5, 2, 1)]
    S2 = [('keep_sentry', 24, 22, 3, 3), ('keep_chaplain', 38, 22, 3, 2), ('keep_sentry', 40, 16, 3, 3), ('keep_chaplain', 24, 16, 3, 2)]
    for z, lst in ((0, S0), (1, S1), (2, S2)):
        for (m, x, y, r, c) in lst:
            p.spawn(m, x, y, z, radius=r, count=c, respawn=200)

    # ---------------------------------------------------------------- wejscie: jaskinia w skalnym wystepie przed fosa
    sx, sy = ENTRANCE
    for (a, b) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-2, 0), (2, 0), (0, -2), (-1, -2), (1, -2), (-2, -1), (2, -1)):
        c = world.get(ox + sx + a, oy + sy + b)
        p.put(sx + a, sy + b, 0, ((c[0] if c and c[0] not in ('water',) else 'grass'), NONE, NONE, 'mountain'))
    p.put(sx, sy, 0, ('cave_floor', NONE, NONE, NONE))
    p.put(sx, sy + 1, 0, ('dirt', 'bones', NONE, NONE))
    p.entries.append((sx, sy + 1))

    # ---------------------------------------------------------------- podziemia
    def cave(z, x0, y0, w, h, pts, style, **kw):
        p.cave(z, x0, y0, w, h, pts, style, rng, **kw)
    # -1 posterunek orkow
    a1 = [(sx, sy - 1), (-5, 58), (0, 60), (10, 62), (20, 58), (24, 56)]
    cave(-1, -24, 46, 66, 24, a1, 'orc', room=(3, 4))
    # -2 orcze chodniki
    a2 = [(24, 56), (32, 50), (40, 56), (48, 50), (54, 42)]
    cave(-2, 12, 36, 56, 28, a2, 'warren', room=(3, 5))
    # -3 glebokie tunele rogaczy: petla na wschod i polnoc, pod calym zamkiem
    a3 = [(54, 42), (58, 30), (54, 18), (44, 8), (34, 4), (24, 6), (12, 12), (10, 24)]
    cave(-3, 2, 0, 64, 52, a3, 'warren', room=(3, 5))
    # -2 B: krotki chodnik w gore pod twierdza
    b2 = [(10, 24), (14, 18), (22, 14)]
    cave(-2, 2, 4, 36, 30, b2, 'orc', room=(2, 4))
    # -1 piwnica pod twierdza (prostokatne sale z kamienia)
    b1 = [(22, 14), (30, 16), (38, 14), (44, 22), (34, 27), (21, 25)]
    cave(-1, 14, 8, 38, 26, b1, 'keep_cellar', crypt=True)
    # schody (kazda para: stairs(z, gora na z, dol na z+1))
    pairs = [(-1, (sx, sy - 1), (sx, sy)),            # z jaskini na -1
             (-2, (24, 56), (24, 56)),                # -1 -> -2
             (-3, (54, 42), (54, 42)),                # -2 -> -3
             (-3, (10, 24), (10, 24)),                # -3 -> -2 (B)
             (-2, (22, 14), (22, 14)),                # -2 (B) -> -1 (piwnica)
             (-1, (21, 25), (21, 25))]                # piwnica -> sala schodow zamku (z0)
    for (z, up, down) in pairs:
        _clear_stair_cells(p, z, [up])
        _clear_stair_cells(p, z + 1, [down]) if z + 1 < 0 else None
    for (z, up, down) in pairs + late:
        p.stairs(z, up, down)
    # twarde pola wokol schodow z0 (pokoj schodow zamku)
    # swiatlo w grotach
    for z, pts in ((-1, a1), (-2, a2), (-3, a3), (-2, b2), (-1, b1)):
        for (x, y) in pts:
            for r in range(2, 8):
                done = False
                for (dx, dy) in ((0, -r), (r, 0), (-r, 0), (0, r)):
                    c = p.get(x + dx, y + dy, z)
                    if c is not None and c[3] != NONE and c[2] == NONE:
                        q = p.get(x + dx - (dx > 0) + (dx < 0), y + dy - (dy > 0) + (dy < 0), z)
                        if q is not None and St.walk(q):
                            _torch(p, x + dx, y + dy, z)
                            done = True
                            break
                if done:
                    break
    # potwory pod ziemia (poziomy rosna wzdluz drogi: orki i rogacze, w piwnicy straz zamku)
    _spawns(p, -1, a1, ['orc_warrior', 'horn_raider', 'orc_shaman', 'horn_bowman', 'orc_warrior'], (3, 4), 200)
    _spawns(p, -2, a2, ['orc_berserker', 'horn_bowman', 'horn_raider', 'cave_troll'], (3, 4), 210)
    _spawns(p, -3, a3, ['horn_guard', 'horn_mystic', 'cave_troll', 'orc_marauder', 'horn_guard', 'horn_mystic', 'orc_leader'],
            (3, 4), 230)
    _spawns(p, -2, b2, ['orc_leader', 'horn_mystic'], (3, 3), 230)
    _spawns(p, -1, b1, ['keep_hound', 'keep_sentry', 'keep_crossbow', 'keep_hound', 'keep_sentry'], (3, 3), 230)

    # ---------------------------------------------------------------- regiony (poziomy 20-70)
    p.regions.append((CASTLE, -6, -6, W + 5, H + 5, 0, 3, 20, 70))
    ux0 = min(p.bounds(z)[0] for z in (-1, -2, -3))
    uy0 = min(p.bounds(z)[1] for z in (-1, -2, -3))
    ux1 = max(p.bounds(z)[2] for z in (-1, -2, -3))
    uy1 = max(p.bounds(z)[3] for z in (-1, -2, -3))
    p.regions.append((UNDER, ux0, uy0, ux1, uy1, -3, -1, 18, 60))
    return p


def build(ctx, cost, helpers):
    rng = random.Random(SEED)
    world = ctx['world']
    tw, th = W + ML + MR, H + MT + MB
    # 2026-11-12: miejsce przypiete (dawny wynik, plan od (750, 336)): obozy i dzicz nie przesuwaja zamku; brak miejsca = szukanie
    try:
        tx, ty = St.find_site(ctx, helpers, cost, (PIN[0] - ML + tw // 2, PIN[1] - MT + th // 2), tw, th,
                              core=(ML - 1, MT - 1, ML + W, MT + H), radius=1, min_temple=45, bounds=(8, 8, 1100, 540), max_water=0.2)
    except AssertionError:
        tx, ty = St.find_site(ctx, helpers, cost, (730, 345), tw, th, core=(ML - 1, MT - 1, ML + W, MT + H), radius=300,
                              min_temple=45, bounds=(8, 8, 1100, 540), max_water=0.2)
    origin = (tx + ML, ty + MT)
    plan = castle_plan(world, origin, rng)
    plan.check()
    St.clear_surface(ctx, tx - 2, ty - 2, tx + tw + 1, ty + th + 1)
    Wf = St.stamp(ctx, plan, origin, helpers['mark'])
    import strongholds
    road = strongholds._connect(ctx, cost, helpers, (origin[0] + ENTRANCE[0], origin[1] + ENTRANCE[1] + 2),
                                (origin[0] + ENTRANCE[0] - 4, origin[1] + ENTRANCE[1] - 4, origin[0] + ENTRANCE[0] + 4,
                                 origin[1] + ENTRANCE[1] + 1))
    out = dict(origin=origin, size=(W, H), entrance=(origin[0] + ENTRANCE[0], origin[1] + ENTRANCE[1]), road=road,
               keep=(origin[0] + 31, origin[1] + 20), boss=Wf(31, 16, 3) + (3,), chest=Wf(33, 13, 3) + (3,),
               door=Wf(30, 24, 2) + (2,), cellar_stairs=Wf(21, 25, -1) + (-1,), under=Wf(0, 0, -1))
    ctx['castle'] = out
    return out
