"""Twierdza orkow i wioska elfow (Darek 2026-11-11, etap 2 duzej listy).

Twierdza Krwawego Kla (w stylu twierdz orkow z Tibii): fosa-rozpadlina dookola, mury z czterema wiezami narozymi,
brama z dwiema basztami i mostem nad fosa, furta wschodnia z mostem linowym. W srodku rozpadlina dzieli dziedziniec
zewnetrzny (koszary z pietrem, jadalnia, kuznia, zagroda wargow, oboz z namiotami) od gornego zamku (twierdza wodza
z sala tronowa, chata szamanow, magazyn) - dwa drewniane mosty. Pietra +1..+3: korona murow obiegajaca cala twierdze,
wieze, pietro koszar, komnaty wodza, taras z mostami linowymi do wiez i wieza strazy na szczycie. Pod ziemia -1..-3:
orcze nory, wykute hale i najglebsze groty z najsilniejszymi orkami; za drzwiami poziomu 50 sala Urgotha (boss)
i skarbiec z nagroda (skrzynia otwierana raz na postac). Zejscia: schody w twierdzy wodza, schody w jadalni, dziura
w zagrodzie wargow, a z zewnatrz tajne wejscie do jaskini za fosa.

Elandor, osada elfow (w stylu miast elfow z Tibii): polana w gestym lesie z pierscieniem drzew, swiatynia, depozyt,
lucznik, zielarka, gospoda i domy na ziemi; na +1 pomosty na dachach polaczone mostami linowymi (pierscien wokol
placu), na +2 korona Wielkiego Drzewa i gniazdo zwiadowcow, na +3 szczyt drzewa ze Starszym. Pod ziemia piwnice
polaczone tunelami (-1) i grota ze zrodlem (-2). Cala osada to strefa ochronna; elfy-wygnancy krecace sie w lesie.
Wszystko z wlasnym ziarnem; uklad sprawdza Plan.check (droga do kazdego spawnu, schodow, NPC i skrzyni).
"""
import random

import numpy as np

import features as F
import structures as St

NONE = '-'
SEED = 20261111

# ====================================================================== twierdza orkow
FW, FH = 66, 58          # twierdza z fosa
FM = 6                   # pas wokol (ziemia orkow)
PIN_ORC = (652, 505)     # lewy gorny rog planu twierdzy orkow w swiecie (wynik z 0.253)
PIN_ELF = (352, 507)     # i osady elfow
CX = 33
QUEST_LEVEL = 50
ORC_REWARD = (4000, 'warlord_helmet', 1)

STONE, SWALL = 'stone_floor', 'stone_wall'
# wykute hale orkow (-2): kamienne sale i korytarze, kosci i krew, beczki i skrzynie
F.CAVE_STYLES.setdefault('orc_hall', ('stone_floor', 'stone_wall', ['bones', 'debris', 'blood'],
                                      ['barrel', 'crate', 'rubble'], 'wall_torch'))
WOOD, WWALL = 'wood_floor', 'wood_wall'


# srodki miejsc twierdzy i osady (find_site szuka najblizej nich): loch dziczy (wilds) nie zajmuje okolicy - inaczej
# losowe zejscie do lochu potrafi zablokowac cala okolice osady po zmianach terenu gdzie indziej (2026-11-11)
RESERVED = [(685 - 50, 510 - 45, 685 + 50, 510 + 45), (385 - 50, 535 - 45, 385 + 50, 535 + 45)]

def _safe_props(plan, z, cells, objs, rng, chance, reserved):
    """Blokujace ozdoby tylko na polach, ktorych 8 sasiadow jest wolnych (nie zamykaja przejsc)."""
    n = 0
    for (x, y) in cells:
        if (x, y) in reserved or rng.random() >= chance:
            continue
        c = plan.get(x, y, z)
        if c is None or not St.walk(c) or c[2] != NONE:
            continue
        ok = True
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if dx or dy:
                    d = plan.get(x + dx, y + dy, z)
                    if d is None or not St.walk(d) or d[2] != NONE or (x + dx, y + dy) in reserved:
                        ok = False
        if ok:
            plan.put(x, y, z, (c[0], c[1], rng.choice(objs), NONE))
            n += 1
    return n


def _torch(plan, x, y, z):
    """Pochodnia przy murze (x, y): stawiana na wolnym polu obok, wall_lights.mount zawiesza ja na widocznej scianie."""
    c = plan.get(x, y, z)
    if c is None or c[3] == NONE:
        return
    for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
        d = plan.get(x + dx, y + dy, z)
        if d is not None and St.walk(d) and d[2] == NONE:
            plan.put(x + dx, y + dy, z, (d[0], d[1], 'wall_torch', NONE))
            return


def orc_plan(world, origin, rng):
    p = St.Plan('Twierdza Krwawego Kla')
    ox, oy = origin
    reserved = set()

    def keep_free(*pts):
        for (x, y) in pts:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    reserved.add((x + dx, y + dy))

    # ---------------------------------------------------------------- pas wokol fosy: wydeptana ziemia orkow
    for y in range(-FM, FH + FM):
        for x in range(-FM, FW + FM):
            if 0 <= x < FW and 0 <= y < FH:
                continue
            c = world.get(ox + x, oy + y)
            d = max(-x - 1 if x < 0 else (x - FW if x >= FW else -1), -y - 1 if y < 0 else (y - FH if y >= FH else -1))
            if c[0] in ('water', 'deep_water', 'swamp', 'lava', 'bridge'):
                p.put(x, y, 0, c)                            # rzeki i jeziora zostaja
                continue
            ground = c[0]
            if d <= 1:
                ground = 'dirt'
            elif rng.random() < 0.45 and ground in ('grass', 'jungle_grass'):
                ground = 'dirt'
            deco = rng.choice((NONE, NONE, NONE, 'bones', 'debris', 'blood')) if d <= 2 else (
                c[1] if c[1] in ('tall_grass', 'flowers', 'fern', 'dry_grass', NONE) else NONE)
            obj = NONE
            if d >= 3 and c[2] in ('tree', 'pine', 'bush', 'autumn_tree', 'dead_tree', 'jungle_tree') and rng.random() < 0.5:
                obj = 'dead_tree' if rng.random() < 0.3 else c[2]
            p.put(x, y, 0, (ground, deco, obj, NONE))
    # droga od mostu bramy na poludnie i od furty na wschod
    for y in range(FH, FH + FM):
        for x in range(CX - 1, CX + 2):
            p.put(x, y, 0, ('dirt', NONE, NONE, NONE))
    p.entries += [(CX, FH + FM - 1)]
    # oboz przed brama: namioty i ogniska po bokach drogi
    for (x, y, o) in ((CX - 6, FH + 2, 'orc_tent'), (CX + 6, FH + 2, 'orc_tent'), (CX - 4, FH + 4, 'campfire'),
                      (CX + 9, FH + 4, 'orc_tent'), (CX - 10, FH + 3, 'firewood'), (FW + 3, 34, 'orc_tent'),
                      (FW + 3, 43, 'campfire'), (-3, 12, 'orc_tent'), (-3, 46, 'orc_tent'), (20, -3, 'orc_tent'),
                      (46, -3, 'campfire')):
        c = p.get(x, y, 0)
        p.put(x, y, 0, ('dirt', NONE, o, NONE))
        _ = c
    # tajne wejscie: skalny wystep za fosa po zachodniej stronie, schody w dol do nor
    sx, sy = -4, 30
    for (a, b) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-2, 0), (2, 0), (0, -2), (-1, -2), (1, -2), (-2, -1), (2, -1)):
        p.put(sx + a, sy + b, 0, (p.get(sx + a, sy + b, 0)[0], NONE, NONE, 'mountain'))
    p.put(sx, sy, 0, ('cave_floor', NONE, NONE, NONE))
    p.put(sx, sy + 1, 0, ('dirt', 'bones', NONE, NONE))
    p.entries.append((sx, sy + 1))

    # ---------------------------------------------------------------- fosa, mury, wewnetrzna rozpadlina
    for y in range(FH):
        for x in range(FW):
            if x < 3 or x >= FW - 3 or y < 3 or y >= FH - 3:
                p.put(x, y, 0, ('chasm', NONE, NONE, NONE))
            elif x in (3, FW - 4) or y in (3, FH - 4):
                p.put(x, y, 0, (STONE, NONE, NONE, SWALL))
            else:
                p.put(x, y, 0, ('dirt', rng.choice((NONE,) * 14 + ('debris', 'bones', 'blood')), NONE, NONE))
    for x in range(4, FW - 4):
        for y in (21, 22, 23):
            p.put(x, y, 0, ('chasm', NONE, NONE, NONE))
    for bx in (14, 50):
        p.deck(0, (bx, 21), (bx, 23), width=2, ground='bridge')
        keep_free((bx, 20), (bx + 1, 20), (bx, 24), (bx + 1, 24))
    # brama: przejscie 3 pola miedzy basztami, most nad fosa
    for y in range(49, FH - 3):
        for x in range(CX - 1, CX + 2):
            p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    p.deck(0, (CX - 1, FH - 3), (CX - 1, FH - 1), width=3, ground='bridge')
    keep_free(*[(x, y) for x in range(CX - 1, CX + 2) for y in range(44, 49)])

    # ---------------------------------------------------------------- wieze narozne i baszty bramy (z0)
    towers = {'NW': (3, 3, (6, 9)), 'NE': (FW - 10, 3, (FW - 7, 9)), 'SW': (3, FH - 10, (6, FH - 10)),
              'SE': (FW - 10, FH - 10, (FW - 7, FH - 10))}
    for key, (tx, ty, door) in towers.items():
        p.box(0, tx, ty, 7, 7, STONE, SWALL, doors=[door])
        keep_free(door, (door[0], door[1] + (1 if ty < 20 else -1)))
        _torch(p, tx + 2, ty + (6 if ty < 20 else 0), 0)
    gtw, gte = (CX - 7, 49), (CX + 2, 49)
    p.box(0, gtw[0], gtw[1], 6, 6, STONE, SWALL, doors=[(CX - 2, 51)])
    p.box(0, gte[0], gte[1], 6, 6, STONE, SWALL, doors=[(CX + 2, 51)])
    for y in range(49, FH - 3):
        for x in range(CX - 1, CX + 2):
            p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    for (x, y) in ((CX - 2, 50), (CX + 2, 50), (CX - 2, 53), (CX + 2, 53)):
        _torch(p, x, y, 0)

    # ---------------------------------------------------------------- gorny zamek: twierdza wodza, chata, magazyn
    p.box(0, 22, 5, 23, 14, STONE, SWALL, doors=[(CX, 18)])
    keep_free((CX, 19), (CX, 18))
    for y in range(6, 18):
        p.put(28, y, 0, (STONE, NONE, NONE, SWALL))
        p.put(38, y, 0, (STONE, NONE, NONE, SWALL))
    for x in list(range(23, 28)) + list(range(39, 44)):
        p.put(x, 11, 0, (STONE, NONE, NONE, SWALL))
    for d in ((28, 8), (28, 14), (38, 8), (38, 14), (25, 11), (41, 11)):
        p.put(d[0], d[1], 0, (STONE, NONE, 'door_open', NONE))
        keep_free(d)
    p.obj(CX, 6, 0, 'throne')
    for y in range(8, 18):
        p.deco(CX, y, 0, 'carpet')
    for (x, y) in ((31, 6), (35, 6)):
        p.obj(x, y, 0, 'banner')
    for (x, y) in ((30, 9), (36, 9), (30, 14), (36, 14)):
        p.obj(x, y, 0, 'statue')
    for (x, y) in ((29, 6), (37, 6), (29, 17), (37, 17)):
        p.obj(x, y, 0, 'campfire')
    for (x, y) in ((23, 13), (23, 15), (23, 17)):
        p.obj(x, y, 0, 'bed')
    p.obj(26, 15, 0, 'table')
    p.obj(43, 7, 0, 'chest')
    p.obj(43, 9, 0, 'crate')
    p.obj(40, 7, 0, 'sack')
    for (x, y) in ((22, 8), (44, 8), (22, 15), (44, 15), (CX - 3, 5), (CX + 3, 5)):
        _torch(p, x, y, 0)
    # chata szamanow
    p.box(0, 10, 11, 8, 7, WOOD, WWALL, doors=[(13, 17)])
    keep_free((13, 18))
    for (x, y, o) in ((11, 12, 'candle'), (16, 12, 'bookcase'), (11, 16, 'clay_pot'), (16, 16, 'candle'), (14, 12, 'altar')):
        p.obj(x, y, 0, o)
    # magazyn
    p.box(0, 49, 11, 8, 7, WOOD, WWALL, doors=[(52, 17)])
    keep_free((52, 18))
    for (x, y, o) in ((50, 12, 'crate'), (51, 12, 'crate'), (55, 12, 'barrel'), (55, 13, 'barrel'), (50, 16, 'sack'),
                      (55, 16, 'crate')):
        p.obj(x, y, 0, o)

    # ---------------------------------------------------------------- dziedziniec zewnetrzny
    p.box(0, 6, 27, 14, 9, WOOD, WWALL, doors=[(12, 35), (19, 31)])
    keep_free((12, 36), (20, 31))
    for x in range(8, 18, 2):
        p.obj(x, 28, 0, 'bed')
    for (x, y, o) in ((10, 31, 'table'), (14, 31, 'table'), (11, 31, 'chair'), (15, 31, 'chair'), (18, 34, 'barrel')):
        p.obj(x, y, 0, o)
    p.box(0, 6, 40, 12, 7, WOOD, WWALL, doors=[(17, 43)])
    keep_free((18, 43))
    for (x, y, o) in ((9, 42, 'table'), (12, 42, 'table'), (9, 44, 'table'), (12, 44, 'table'), (16, 41, 'barrel'),
                      (16, 45, 'barrel'), (10, 42, 'chair'), (13, 44, 'chair')):
        p.obj(x, y, 0, o)
    p.box(0, 46, 27, 12, 8, STONE, SWALL, doors=[(51, 34)])
    keep_free((51, 35))
    for (x, y, o) in ((48, 28, 'forge'), (50, 28, 'anvil'), (53, 28, 'anvil'), (56, 28, 'lumber'), (56, 31, 'firewood'),
                      (47, 32, 'barrel')):
        p.obj(x, y, 0, o)
    # zagroda wargow (plot z przerwa od zachodu), w rogu dziura do nor
    for x in range(44, 57):
        for y in range(38, 47):
            if x in (44, 56) or y in (38, 46):
                if not (x == 44 and y in (41, 42, 43)):
                    p.put(x, y, 0, ('dirt', NONE, 'fence', NONE))
            else:
                p.put(x, y, 0, ('dirt', rng.choice((NONE, NONE, 'bones', 'dry_grass')), NONE, NONE))
    p.obj(46, 39, 0, 'hay_bale')
    p.obj(46, 45, 0, 'hay_bale')
    keep_free((43, 42), (45, 42))
    # oboz na srodku: namioty, ogniska, beczki
    for (x, y, o) in ((24, 27, 'orc_tent'), (28, 27, 'orc_tent'), (38, 27, 'orc_tent'), (42, 27, 'orc_tent'),
                      (24, 44, 'orc_tent'), (42, 44, 'orc_tent'), (30, 36, 'campfire'), (36, 36, 'campfire'),
                      (CX, 40, 'well')):
        p.obj(x, y, 0, o)
    yard = [(x, y) for y in range(24, FH - 4) for x in range(4, FW - 4)]
    _safe_props(p, 0, yard, ['barrel', 'crate', 'firewood', 'orc_tent', 'rubble'], rng, 0.025, reserved)
    north = [(x, y) for y in range(4, 21) for x in range(4, FW - 4)]
    _safe_props(p, 0, north, ['barrel', 'crate', 'orc_tent', 'firewood'], rng, 0.03, reserved)

    # ---------------------------------------------------------------- pietro +1: korona murow, wieze, koszary, wodz
    for y in range(3, FH - 3):
        for x in range(3, FW - 3):
            if x in (3, 4, FW - 5, FW - 4) or y in (3, 4, FH - 5, FH - 4):
                p.put(x, y, 1, (STONE, NONE, NONE, NONE))
    for (x, y) in ((3, 15), (3, 40), (FW - 4, 15), (FW - 4, 28), (20, 3), (46, 3), (14, FH - 4), (52, FH - 4)):
        p.obj(x, y, 1, 'banner')
    tw1 = {'NW': [(4, 9), (9, 4)], 'NE': [(FW - 5, 9), (FW - 10, 4)], 'SW': [(4, FH - 10), (9, FH - 5)],
           'SE': [(FW - 5, FH - 10), (FW - 10, FH - 5)]}
    for key, (tx, ty, door) in towers.items():
        p.box(1, tx, ty, 7, 7, STONE, SWALL, gaps=tw1[key])
    p.stairs(0, (4, 4), (5, 4))
    p.stairs(0, (FW - 5, 4), (FW - 6, 4))
    p.stairs(0, (4, FH - 5), (5, FH - 5))
    p.stairs(0, (FW - 5, FH - 5), (FW - 6, FH - 5))
    p.box(1, gtw[0], gtw[1], 6, 6, STONE, SWALL, gaps=[(gtw[0], FH - 5), (gtw[0] + 5, FH - 5)])
    p.box(1, gte[0], gte[1], 6, 6, STONE, SWALL, gaps=[(gte[0], FH - 5), (gte[0] + 5, FH - 5)])
    for y in range(49, FH - 3):
        for x in range(CX - 1, CX + 2):
            p.put(x, y, 1, (STONE, NONE, NONE, NONE))
    p.stairs(0, (gtw[0] + 1, 50), (gtw[0] + 2, 50))
    p.stairs(0, (gte[0] + 4, 50), (gte[0] + 3, 50))
    # koszary +1
    p.box(1, 6, 27, 14, 9, WOOD, WWALL)
    for x in range(9, 18, 2):
        p.obj(x, 34, 1, 'bed')
    p.obj(17, 29, 1, 'chest')
    p.stairs(0, (7, 34), (7, 33))
    # komnaty wodza +1
    p.box(1, 22, 5, 23, 14, STONE, SWALL)
    for (x, y, o) in ((30, 10, 'table'), (31, 10, 'table'), (35, 10, 'table'), (36, 10, 'table'), (CX, 6, 'bookcase'),
                      (29, 6, 'banner'), (37, 6, 'banner'), (23, 17, 'barrel'), (43, 17, 'crate'), (28, 14, 'statue'),
                      (38, 14, 'statue')):
        p.obj(x, y, 1, o)
    p.stairs(0, (23, 6), (24, 6))
    for (x, y) in ((22, 10), (44, 10), (CX, 18)):
        _torch(p, x, y, 1)

    # ---------------------------------------------------------------- +2: wieze polnocne, taras wodza, mosty linowe
    p.box(2, 3, 3, 7, 7, STONE, SWALL, gaps=[(9, 7)])
    p.box(2, FW - 10, 3, 7, 7, STONE, SWALL, gaps=[(FW - 10, 7)])
    p.stairs(1, (8, 8), (7, 8))
    p.stairs(1, (FW - 9, 8), (FW - 8, 8))
    p.box(2, 22, 5, 23, 14, STONE, SWALL, gaps=[(22, 7), (44, 7)])
    p.deck(2, (10, 7), (21, 7), width=1, ground='rope_bridge')
    p.deck(2, (45, 7), (FW - 11, 7), width=1, ground='rope_bridge')
    p.stairs(1, (43, 6), (42, 6))
    for (x, y, o) in ((24, 16, 'campfire'), (42, 16, 'campfire'), (25, 9, 'barrel'), (41, 9, 'barrel')):
        p.obj(x, y, 2, o)
    # +3: wieza strazy na szczycie
    p.box(3, 29, 8, 9, 7, STONE, SWALL)
    p.stairs(2, (31, 10), (32, 10))
    p.obj(36, 9, 3, 'banner')
    p.obj(30, 13, 3, 'campfire')

    # ---------------------------------------------------------------- podziemia
    p.stairs(-1, (42, 16), (42, 16))                     # schody w dol w twierdzy wodza (pokoj SE)
    p.stairs(-1, (8, 45), (7, 45))                       # schody w dol w jadalni
    p.ladder(-1, (54, 44))                               # dziura w zagrodzie wargow, drabina w norach
    p.stairs(-1, (sx, sy - 1), (sx, sy))                 # tajne wejscie (schody w gore z nor pod wystepem)
    # -1: orcze nory
    a1 = [(42, 17), (56, 12), (57, 30), (54, 43), (40, 50), (24, 50), (8, 46), (8, 28), (10, 10), (24, 12), (30, 30)]
    p.cave(-1, -8, -4, FW + 14, FH + 8, a1, 'orc', rng, room=(3, 5))
    p.cave(-1, -8, -4, FW + 14, FH + 8, [(8, 28), (0, 29), (sx, sy - 1)], 'orc', rng, room=(2, 3))
    for (x, y) in ((42, 16), (8, 45), (54, 44), (sx, sy - 1)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c = p.get(x + dx, y + dy, -1)
                if c is None or c[3] != NONE or not St.walk(c):
                    p.put(x + dx, y + dy, -1, ('dirt', NONE, NONE, NONE))
    for (x, y) in ((42, 16), (8, 45), (54, 44), (sx, sy - 1)):
        for dx in (-2, -1, 0, 1, 2):
            for dy in (-2, -1, 0, 1, 2):
                if p.get(x + dx, y + dy, -1) is None:
                    p.put(x + dx, y + dy, -1, ('dirt', NONE, NONE, 'cave_wall'))
    p.put(42, 16, -1, ('dirt', NONE, 'stairs_up', NONE))
    p.put(8, 45, -1, ('dirt', NONE, 'stairs_up', NONE))
    p.put(54, 44, -1, ('dirt', NONE, 'ladder', NONE))
    p.put(sx, sy - 1, -1, ('dirt', NONE, 'stairs_up', NONE))
    # -2: wykute hale (prostokatne sale i korytarze)
    a2 = [(30, 31), (16, 22), (8, 8), (28, 6), (46, 8), (58, 16), (58, 36), (46, 50), (26, 50), (10, 44), (40, 40)]
    p.cave(-2, -2, -2, FW + 4, FH + 4, a2, 'orc_hall', rng, crypt=True)
    p.stairs(-2, (30, 31), (30, 30))
    # -3: najglebsze groty i skrzydlo Urgotha za drzwiami poziomu
    wing = (4, 1, 28, 21)
    p.box(-3, 4, 1, 25, 21, STONE, SWALL)
    p.box(-3, 11, 1, 11, 8, STONE, SWALL, doors=[(16, 8)])
    for y in range(9, 21):
        for x in range(5, 28):
            p.put(x, y, -3, (STONE, rng.choice((NONE,) * 10 + ('bones', 'blood')), NONE, NONE))
    for (x, y) in ((6, 10), (26, 10), (6, 19), (26, 19), (11, 14), (21, 14)):
        p.obj(x, y, -3, 'campfire')
    for (x, y) in ((13, 3), (19, 3)):
        p.obj(x, y, -3, 'statue')
    for (x, y) in ((12, 7), (20, 7)):
        p.obj(x, y, -3, 'candle')
    for (x, y) in ((8, 1), (24, 1), (4, 12), (28, 12), (11, 4), (21, 4), (10, 21), (22, 21)):
        _torch(p, x, y, -3)
    p.level_door(-3, (16, 21), QUEST_LEVEL, floor=STONE)
    a3 = [(40, 41), (54, 48), (58, 30), (52, 14), (40, 8), (36, 26), (22, 34), (8, 44), (8, 30), (16, 28)]
    p.cave(-3, -2, -2, FW + 4, FH + 4, a3, 'orc', rng, room=(4, 6),
           keep=lambda x, y: wing[0] - 1 <= x <= wing[2] + 1 and wing[1] - 1 <= y <= wing[3])
    p.corridor(-3, [(16, y) for y in range(22, 29)] + [(17, y) for y in range(22, 29)], 'dirt', 'cave_wall')
    p.put(16, 21, -3, (STONE, NONE, 'door_level', NONE))
    p.stairs(-3, (40, 41), (40, 40))
    for (x, y) in ((40, 40), (30, 31)):
        pass
    # nagroda: skrzynia w skarbcu (raz na postac), boss na srodku sali
    gold, item, n = ORC_REWARD
    p.chests.append((16, 4, -3, gold, item, n))
    p.behind += [(16, 4, -3), (16, 14, -3)]
    p.spawn('urgoth', 16, 14, -3, radius=2, count=1, respawn=1800)
    p.spawn('orc_cult_fanatic', 8, 16, -3, radius=2, count=2, respawn=300)
    p.spawn('orc_cult_fanatic', 24, 16, -3, radius=2, count=2, respawn=300)

    # ---------------------------------------------------------------- spawny (glebiej - silniejsze i liczniejsze)
    # poziomy rosna wzdluz drog (route_levels): na zewnatrz i na dziedzincu najslabsze, w gornym zamku i w twierdzy
    # wodza mocniejsze, na pietrach wodza najsilniejsze; pod ziemia -1 (22-32), -2 (28-44), -3 (44-60)
    S0 = [('orc', CX, FH + 3, 3, 3), ('orc', CX - 8, FH + 4, 3, 2),
          ('orc_spearman', CX, 46, 3, 3), ('orc', 26, 33, 4, 4), ('orc_warrior', 39, 33, 4, 3),
          ('orc_spearman', 12, 31, 3, 3), ('orc_warrior', 11, 43, 3, 3), ('orc_warrior', 51, 31, 3, 2),
          ('orc_rider', 50, 42, 3, 3), ('orc_spearman', 20, 45, 3, 2),
          ('orc_shaman', 13, 14, 2, 2), ('orc_rider', 9, 19, 3, 2), ('orc_berserker', CX, 20, 3, 2),
          ('orc_rider', 57, 19, 2, 2), ('orc_shaman', 52, 14, 2, 2),
          ('orc_berserker', CX, 11, 3, 3), ('orc_berserker', 25, 15, 2, 2), ('orc_shaman', 41, 8, 2, 2)]
    S1 = [('orc_marauder', 4, 28, 3, 3), ('orc_marauder', FW - 5, 28, 3, 3), ('orc_marauder', 20, 4, 3, 2),
          ('orc_marauder', 46, 4, 3, 2), ('orc_marauder', 18, FH - 5, 3, 2), ('orc_marauder', 48, FH - 5, 3, 2),
          ('orc_spearman', CX, 51, 2, 2), ('orc_warrior', 12, 31, 3, 3),
          ('orc_leader', 27, 8, 2, 2), ('orc_leader', 40, 15, 2, 2), ('orc_marauder', CX, 12, 4, 3)]
    S2 = [('orc_marauder', CX, 14, 4, 3), ('orc_marauder', 6, 6, 2, 2), ('orc_marauder', FW - 7, 6, 2, 2),
          ('orc_leader', 40, 15, 2, 2)]
    S3 = [('orc_leader', 33, 11, 2, 2), ('orc_marauder', 31, 12, 2, 1)]
    for z, lst in ((0, S0), (1, S1), (2, S2), (3, S3)):
        for (m, x, y, r, n) in lst:
            p.spawn(m, x, y, z, radius=r, count=n, respawn=180)
    _cave_spawns(p, -1, a1, ['orc_shaman', 'orc_rider', 'orc_shaman', 'orc_rider', 'orc_berserker', 'orc_shaman',
                             'orc_shaman', 'orc_berserker', 'orc_berserker', 'orc_berserker'], (3, 4), 200)
    _cave_spawns(p, -2, a2, ['orc_rider', 'orc_berserker', 'orc_marauder', 'orc_berserker', 'orc_rider', 'orc_marauder',
                             'orc_berserker', 'orc_marauder', 'orc_berserker', 'orc_marauder'], (4, 5), 220)
    _cave_spawns(p, -3, a3, ['orc_leader', 'orc_leader', 'orc_leader', 'orc_warlord', 'orc_warlord', 'orc_cult_priest',
                             'orc_cult_priest', 'orc_cult_fanatic', 'orc_cult_fanatic'], (4, 5), 240)
    # swiatlo w grotach (pochodnie przy scianach w kazdej komorze)
    for z, pts in ((-1, a1), (-2, a2), (-3, a3)):
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
    p.regions.append(('Twierdza Krwawego Kła', -FM, -FM, FW + FM - 1, FH + FM - 1, 0, 3, 12, 60))
    ux0, uy0, ux1, uy1 = (min(p.bounds(z)[i] for z in (-1, -2, -3)) if i < 2 else max(p.bounds(z)[i] for z in (-1, -2, -3))
                          for i in range(4))
    p.regions.append(('Podziemia Twierdzy Krwawego Kła', ux0, uy0, ux1, uy1, -3, -1, 18, 75))
    return p


def _cave_spawns(p, z, pts, monsters, counts, respawn):
    """Spawn w kazdej komorze (bez pierwszej - schody) na najblizszym wolnym polu."""
    cells = [xy for xy, c in p.f[z].items() if St.walk(c) and c[2] == NONE]
    for i, want in enumerate(pts[1:]):
        x, y = min(cells, key=lambda c: abs(c[0] - want[0]) + abs(c[1] - want[1]))
        m = monsters[i % len(monsters)]
        n = 1 if m == 'orc_warlord' else counts[i % 2]
        p.spawn(m, x, y, z, radius=4, count=n, respawn=respawn)


# ====================================================================== osada elfow
EW, EH = 60, 50
EM = 9
VILLAGE = 'Elandor'
LOG, LWALL = 'wood_floor', 'log_wall'


def _platform(p, z, x0, y0, x1, y1, gaps, hut=None, rng=None):
    """Pomost na drzewie z barierka (plot) i przerwami na mosty; hut = (bok drzwi) - chatka w srodku z obejsciem."""
    p.platform(z, x0, y0, x1, y1, LOG, rail='fence', rail_gaps=set(gaps))
    if hut:
        hx0, hy0, hx1, hy1 = x0 + 2, y0 + 2, x1 - 2, y1 - 2
        w, h = hx1 - hx0 + 1, hy1 - hy0 + 1
        door = {'S': ((hx0 + hx1) // 2, hy1), 'N': ((hx0 + hx1) // 2, hy0), 'E': (hx1, (hy0 + hy1) // 2),
                'W': (hx0, (hy0 + hy1) // 2)}[hut]
        inner = p.box(z, hx0, hy0, w, h, LOG, LWALL, doors=[door])
        for (x, y) in inner[:: max(1, len(inner) // 3)][:3]:
            if abs(x - door[0]) + abs(y - door[1]) > 1:
                p.obj(x, y, z, 'potted_plant')
    for (x, y) in ((x0 + 1, y0 + 1), (x1 - 1, y1 - 1)):
        if p.get(x, y, z)[2] == NONE and (x, y) not in gaps:
            pass


def elf_plan(world, origin, rng):
    p = St.Plan('Elandor')
    ox, oy = origin
    reserved = set()

    def keep_free(*pts):
        for (x, y) in pts:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    reserved.add((x + dx, y + dy))

    # ---------------------------------------------------------------- las dookola (z mapy, przerzedzony przy osadzie)
    for y in range(-EM, EH + EM):
        for x in range(-EM, EW + EM):
            if 0 <= x < EW and 0 <= y < EH:
                continue
            c = world.get(ox + x, oy + y)
            if c[0] in ('water', 'deep_water', 'swamp', 'lava', 'bridge'):
                p.put(x, y, 0, c)                            # rzeki i jeziora zostaja
                continue
            ground = c[0]
            obj = c[2] if c[2] in ('tree', 'pine', 'bush', 'autumn_tree', 'dead_tree', 'jungle_tree', 'jungle_bush') else NONE
            if c[3] != NONE:
                obj = 'tree'
            deco = c[1] if c[1] in ('tall_grass', 'flowers', 'fern', 'mushrooms', NONE) else NONE
            if obj == NONE and ground not in F.ROAD_GROUND and rng.random() < 0.22:
                obj = rng.choice(('tree', 'tree', 'autumn_tree', 'bush'))
            p.put(x, y, 0, (ground, deco, obj, NONE))
    # ---------------------------------------------------------------- polana osady: trawa, kwiaty, pierscien drzew
    base = 'grass'
    for y in range(EH):
        for x in range(EW):
            edge = x in (0, 1, EW - 2, EW - 1) or y in (0, 1, EH - 2, EH - 1)
            deco = rng.choice((NONE,) * 8 + ('flowers', 'tall_grass', 'fern'))
            obj = rng.choice(('tree', 'tree', 'autumn_tree')) if edge and rng.random() < 0.85 else NONE
            p.put(x, y, 0, (base, deco, obj, NONE))
    # drozki: od czterech wejsc do placu
    paths = set()
    for y in range(0, EH):
        paths |= {(29, y), (30, y)}
    for x in range(3, EW):
        paths |= {(x, 20), (x, 21)}
    for (x, y) in paths:
        p.put(x, y, 0, ('dirt', NONE, NONE, NONE))
    # wejscia od polnocy, poludnia i wschodu (od zachodu gesty las - osada nie skraca drogi przez puszcze)
    p.entries += [(29, EH + EM - 1), (29, -EM), (EW + EM - 1, 20)]
    for (a, b) in ((29, EH), (29, -1), (-1, 20), (EW, 20)):
        pass
    for k in range(EM):
        for (x, y) in ((29, EH + k), (30, EH + k), (29, -1 - k), (30, -1 - k), (EW + k, 20), (EW + k, 21)):
            c = p.get(x, y, 0)
            p.put(x, y, 0, ('dirt', NONE, NONE, NONE))
            _ = c
    # plac: kamienna posadzka wokol fontanny i krag kwiatow
    for y in range(17, 25):
        for x in range(25, 35):
            p.put(x, y, 0, ('paving', NONE, NONE, NONE))
    for y in (19, 20, 21, 22):
        for x in (0, 1):
            p.put(x, y, 0, (base, NONE, 'tree', NONE))
    p.obj(29, 21, 0, 'fountain')                      # na skrzyzowaniu drozek, obok tarasu +1
    for (x, y) in ((25, 17), (34, 17), (25, 24), (34, 24)):
        p.obj(x, y, 0, 'potted_plant')
    keep_free(*[(x, y) for y in range(16, 26) for x in range(24, 36)])

    # ---------------------------------------------------------------- budynki na ziemi
    houses = []
    # Wielkie Drzewo (sala Starszego): 4 pietra
    p.box(0, 23, 3, 14, 11, LOG, LWALL, doors=[(29, 13), (30, 13)])
    keep_free((29, 14), (30, 14))
    for (x, y, o) in ((24, 4, 'bookcase'), (35, 4, 'bookcase'), (24, 8, 'potted_plant'), (35, 8, 'potted_plant'),
                      (29, 7, 'table'), (30, 7, 'table'), (28, 7, 'chair'), (31, 7, 'chair')):
        p.obj(x, y, 0, o)
    for y in range(5, 13):
        p.deco(29, y, 0, 'carpet')
        p.deco(30, y, 0, 'carpet')
    # swiatynia
    p.box(0, 4, 5, 12, 10, 'marble_floor', LWALL, doors=[(15, 9)])
    keep_free((16, 9))
    p.obj(6, 9, 0, 'altar')
    for (x, y, o) in ((5, 6, 'candle'), (5, 13, 'candle'), (8, 6, 'potted_plant'), (8, 13, 'potted_plant'),
                      (11, 7, 'bench'), (11, 11, 'bench'), (13, 7, 'bench'), (13, 11, 'bench')):
        p.obj(x, y, 0, o)
    # depozyt
    p.box(0, 45, 5, 11, 9, LOG, LWALL, doors=[(45, 9)])
    keep_free((44, 9))
    for (x, y) in ((54, 6), (54, 8), (54, 10)):
        p.obj(x, y, 0, 'depot_locker')
    for (x, y, o) in ((47, 6, 'potted_plant'), (51, 6, 'bookcase')):
        p.obj(x, y, 0, o)
    # lucznik i zielarka
    p.box(0, 5, 26, 11, 8, LOG, LWALL, doors=[(15, 29)])
    keep_free((16, 29))
    for (x, y, o) in ((6, 27, 'showcase'), (6, 29, 'showcase'), (9, 27, 'lumber'), (12, 32, 'table'), (6, 32, 'barrel')):
        p.obj(x, y, 0, o)
    p.box(0, 44, 26, 11, 8, LOG, LWALL, doors=[(44, 29)])
    keep_free((43, 29))
    for (x, y, o) in ((53, 27, 'bookcase'), (53, 29, 'clay_pot'), (50, 27, 'potted_plant'), (47, 32, 'table'),
                      (53, 32, 'clay_pot')):
        p.obj(x, y, 0, o)
    # gospoda i dwa domy
    p.box(0, 23, 35, 14, 10, LOG, LWALL, doors=[(29, 35), (30, 35)])
    keep_free((29, 34), (30, 34))
    for (x, y, o) in ((25, 38, 'table'), (25, 41, 'table'), (34, 38, 'table'), (34, 41, 'table'), (26, 38, 'chair'),
                      (26, 41, 'chair'), (33, 38, 'chair'), (33, 41, 'chair'), (24, 36, 'barrel'), (35, 43, 'barrel')):
        p.obj(x, y, 0, o)
    for (hx, door) in ((6, (10, 38)), (45, (49, 38))):
        p.box(0, hx, 38, 9, 7, LOG, LWALL, doors=[door])
        keep_free((door[0], door[1] - 1))
        for (x, y, o) in ((hx + 1, 39, 'bed'), (hx + 1, 42, 'wardrobe'), (hx + 7, 39, 'table'), (hx + 7, 43, 'chest')):
            p.obj(x, y, 0, o)
        houses.append(hx)
    # drzewa w osadzie miedzy budynkami (nie na drozkach, nie przy drzwiach)
    yard = [(x, y) for y in range(2, EH - 2) for x in range(2, EW - 2)]
    _safe_props(p, 0, yard, ['tree', 'tree', 'autumn_tree', 'bush'], rng, 0.10, reserved | paths)
    for (x, y) in yard:
        c = p.get(x, y, 0)
        if c[0] == 'grass' and c[2] == NONE and c[3] == NONE and rng.random() < 0.05 and (x, y) not in paths:
            p.put(x, y, 0, (c[0], 'flowers', NONE, NONE))

    # ---------------------------------------------------------------- +1: pomosty na dachach i mosty linowe
    p.box(1, 23, 3, 14, 11, LOG, LWALL, gaps=[(23, 9), (36, 9), (29, 13)])
    p.stairs(0, (24, 12), (25, 12))
    _platform(p, 1, 4, 5, 15, 14, gaps=[(15, 9), (10, 14)], hut='S')
    _platform(p, 1, 45, 5, 55, 13, gaps=[(45, 9), (50, 13)], hut='S')
    _platform(p, 1, 5, 26, 15, 33, gaps=[(10, 26), (11, 33)], hut='E')
    _platform(p, 1, 44, 26, 54, 33, gaps=[(50, 26), (48, 33)], hut='W')
    _platform(p, 1, 23, 35, 36, 44, gaps=[(23, 41), (36, 41)], hut=None)
    _platform(p, 1, 6, 38, 14, 44, gaps=[(11, 38), (14, 41)], hut=None)
    _platform(p, 1, 45, 38, 53, 44, gaps=[(48, 38), (45, 41)], hut=None)
    p.deck(1, (16, 9), (22, 9), ground='rope_bridge')
    p.deck(1, (37, 9), (44, 9), ground='rope_bridge')
    p.deck(1, (10, 15), (10, 25), ground='rope_bridge')
    p.deck(1, (50, 14), (50, 25), ground='rope_bridge')
    p.deck(1, (11, 34), (11, 37), ground='rope_bridge')
    p.deck(1, (48, 34), (48, 37), ground='rope_bridge')
    p.deck(1, (15, 41), (22, 41), ground='rope_bridge')
    p.deck(1, (37, 41), (44, 41), ground='rope_bridge')
    # przejscie z korony Wielkiego Drzewa na plac nad fontanna (taras widokowy)
    p.deck(1, (29, 14), (29, 15), width=2, ground=LOG)
    p.platform(1, 27, 16, 32, 19, LOG, rail='fence', rail_gaps={(29, 16), (30, 16)})
    p.stairs(0, (35, 43), (34, 43))                      # schody na pomost gospody
    for (x, y, o) in ((26, 39, 'bench'), (33, 39, 'bench'), (29, 38, 'potted_plant'), (31, 42, 'flower_box')):
        if p.get(x, y, 1)[2] == NONE:
            p.obj(x, y, 1, o)
    for (x, y, o) in ((25, 5, 'bookcase'), (34, 5, 'potted_plant'), (25, 10, 'bed'), (28, 6, 'table')):
        p.obj(x, y, 1, o)

    # ---------------------------------------------------------------- +2: korona Wielkiego Drzewa, gniazdo zwiadowcow
    p.box(2, 25, 4, 10, 9, LOG, LWALL, gaps=[(34, 8)])
    p.stairs(1, (33, 11), (32, 11))
    p.deck(2, (35, 8), (46, 8), ground='rope_bridge')
    p.platform(2, 47, 6, 54, 11, LOG, rail='fence', rail_gaps={(47, 8)})
    for (x, y, o) in ((49, 7, 'barrel'), (52, 10, 'crate'), (53, 7, 'banner')):
        p.obj(x, y, 2, o)
    for (x, y, o) in ((26, 5, 'potted_plant'), (33, 5, 'bookcase')):
        p.obj(x, y, 2, o)
    # +3: szczyt drzewa ze Starszym
    p.platform(3, 25, 4, 34, 12, LOG, rail='fence')
    p.stairs(2, (26, 11), (27, 11))
    for (x, y, o) in ((26, 5, 'potted_plant'), (33, 5, 'potted_plant'), (33, 11, 'statue')):
        p.obj(x, y, 3, o)

    # ---------------------------------------------------------------- -1: piwnice i tunele, -2: grota ze zrodlem
    p.box(-1, 4, 5, 12, 10, 'stone_floor', SWALL)        # krypta pod swiatynia
    p.box(-1, 45, 5, 11, 9, 'stone_floor', SWALL)        # skarbiec pod depozytem
    p.box(-1, 23, 35, 14, 10, 'stone_floor', SWALL)      # piwnica gospody
    for (x, y, o) in ((5, 6, 'candle'), (14, 6, 'candle'), (9, 6, 'statue'), (5, 13, 'clay_pot')):
        p.obj(x, y, -1, o)
    for (x, y, o) in ((46, 6, 'chest'), (47, 6, 'crate'), (54, 12, 'crate'), (46, 12, 'sack')):
        p.obj(x, y, -1, o)
    for (x, y, o) in ((24, 36, 'barrel'), (25, 36, 'barrel'), (35, 36, 'barrel'), (24, 43, 'crate'), (35, 43, 'crate'),
                      (28, 36, 'sack')):
        p.obj(x, y, -1, o)
    tun = [(x, y) for x in range(10, 52) for y in (17, 18)] + [(x, y) for x in (10, 11) for y in range(14, 19)] + \
          [(x, y) for x in (50, 51) for y in range(13, 19)] + [(x, y) for x in (29, 30) for y in range(17, 36)]
    p.corridor(-1, tun, 'stone_floor', SWALL)
    for (x, y) in ((10, 20), (51, 20), (29, 25), (30, 31)):
        pass
    for (x, y) in ((20, 16), (40, 16), (28, 26), (31, 30)):
        c = p.get(x, y, -1)
        if c is not None and c[3] != NONE:
            _torch(p, x, y, -1)
    p.stairs(-1, (52, 11), (52, 11))                     # depozyt -> skarbiec
    p.stairs(-1, (25, 42), (25, 42))                     # gospoda -> piwnica
    p.stairs(-1, (13, 12), (13, 12))                     # swiatynia -> krypta
    g2 = [(6, 7), (14, 20), (26, 26), (40, 22), (30, 36), (16, 34)]
    p.cave(-2, 1, 2, 52, 42, g2, 'moss', rng, room=(3, 5))
    p.stairs(-2, (6, 7), (6, 7))                         # krypta -> grota
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            c = p.get(6 + dx, 7 + dy, -1)
            if c is not None and c[2] in ('candle', 'statue', 'clay_pot'):
                p.put(6 + dx, 7 + dy, -1, (c[0], NONE, NONE, c[3]))
    # zrodlo w grocie (woda w srodku komory) i kapliczka
    for (x, y) in ((26, 25), (27, 25), (26, 26), (27, 26)):
        c = p.get(x, y, -2)
        if c is not None and c[3] == NONE:
            p.put(x, y, -2, ('water', NONE, NONE, NONE))
    p.obj(40, 20, -2, 'altar') if p.get(40, 20, -2) is not None and p.get(40, 20, -2)[3] == NONE else None
    # ---------------------------------------------------------------- NPC i strefy ochronne
    color = '#5aa04a'
    p.npcs += [
        ['elandor_priestess', 'Kapłanka Liriel', 'GUIDE', VILLAGE, 8, 9, 0, '-', color,
         'Niech Wielkie Drzewo cię chroni. Opowiem ci o lesie i o łowach na wygnańców.'],
        ['elandor_banker', 'Skarbnik Faelar', 'BANKER', VILLAGE, 50, 9, 0, '-', color,
         'Złoto i depozyt są tu bezpieczne jak korzenie drzew.'],
        ['elandor_bowyer', 'Łuczarz Thalion', 'MERCHANT', VILLAGE, 10, 29, 0, 'elandor_bows', color,
         'Łuki z cisu i jesionu, strzelają bez amunicji. Skupuję też drewno i skóry.'],
        ['elandor_herbalist', 'Zielarka Ilyana', 'MERCHANT', VILLAGE, 49, 29, 0, 'elandor_herbs', color,
         'Mikstury, zioła i różdżki dla wędrowców lasu.'],
        ['elandor_elder', 'Starszy Aelindor', 'GUIDE', VILLAGE, 29, 8, 3, '-', color,
         'Witaj na szczycie Wielkiego Drzewa. Widać stąd cały las... i wygnańców, którzy w nim krążą.'],
    ]
    for z in (-2, -1, 0, 1, 2, 3):
        p.pz.append((0, 0, EW - 1, EH - 1, z))
    # elfy-wygnancy w lesie dookola (poza strefa ochronna)
    ring = [('elf', -5, 8), ('elf', -5, 34), ('elf', 12, -5), ('elf', 46, -5), ('elf', EW + 4, 10),
            ('elf', EW + 4, 36), ('elf', 6, EH + 4), ('elf', 44, EH + 4), ('elf', -6, EH + 5),
            ('elf', EW + 5, -6)]
    for (m, x, y) in ring:
        best = min(((abs(a - x) + abs(b - y), a, b) for (a, b), c in p.f[0].items()
                    if St.walk(c) and c[2] == NONE and not (0 <= a < EW and 0 <= b < EH)), default=None)
        if best:
            p.spawn(m, best[1], best[2], 0, radius=4, count=3, respawn=180)
    # czatownie wygnancow: pomosty na drzewach w lesie (+1), drabina z ziemi, zwiadowcy i arkanisci na gorze
    stands = [(-8, 8, 'elf_scout'), (EW + 3, 28, 'elf_arcanist'), (40, -8, 'elf_scout'), (16, EH + 3, 'elf_arcanist'),
              (EW + 3, EH + 3, 'elf_scout')]
    for (x0, y0, m) in stands:
        cx, cy = x0 + 2, y0 + 2
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c = p.get(cx + dx, cy + dy, 0)
                if c[0] not in ('water', 'deep_water', 'swamp', 'lava'):
                    p.put(cx + dx, cy + dy, 0, (c[0] if c[0] != 'bridge' else 'grass', c[1], NONE, NONE))
        p.platform(1, x0, y0, x0 + 4, y0 + 4, LOG, rail='fence')
        p.ladder(0, (cx, cy))
        p.obj(x0 + 1, y0 + 1, 1, 'barrel')
        p.spawn(m, cx + 1, cy - 1, 1, radius=1, count=2, respawn=180)
    p.finish()
    reached = {(x, y) for (x, y, z) in p.reach([(x, y, 0) for (x, y) in p.entries]) if z == 0}
    for s in p.spawns:
        p.trail(0, (s[1], s[2]) if s[3] == 0 else next((t[0], t[1]) for t in p.trans if t[3] == 'LADDER' and
                                                         abs(t[0] - s[1]) <= 2 and abs(t[1] - s[2]) <= 2), reached)
    p.regions.append(('Elandor, osada elfów', -EM, -EM, EW + EM - 1, EH + EM - 1, 0, 3, 14, 35))
    ux0, uy0, ux1, uy1 = (min(p.bounds(z)[i] for z in (-1, -2)) if i < 2 else max(p.bounds(z)[i] for z in (-1, -2))
                          for i in range(4))
    p.regions.append(('Piwnice Elandoru', ux0, uy0, ux1, uy1, -2, -1, 14, 35))
    return p


# ====================================================================== wejscie z generate_continent.build
def _connect(ctx, cost, helpers, start, rect):
    """Droga od pola start (tuz za planem) do najblizszej drogi; plan nie jest przecinany."""
    world, T = ctx['world'], ctx['T']
    x0, y0, x1, y1 = rect
    saved = cost[y0:y1 + 1, x0:x1 + 1].copy()
    cost[y0:y1 + 1, x0:x1 + 1] = np.inf
    # najblizszy trakt moze lezec za woda (np. odnoga obozu po drugiej stronie rzeki): wtedy nastepny
    skipped = []
    road = None
    for _attempt in range(12):
        road = helpers['nearest_road'](world, cost, start, rect, limit=160)
        if road is None:
            break
        cost[start[1], start[0]] = 1.0
        try:
            F.connect(world, T, cost, start, road, width=2)
            break
        except AssertionError:
            skipped.append((road, cost[road[1], road[0]]))
            cost[road[1], road[0]] = 1.0
            road = None
    for (r, c) in skipped:
        cost[r[1], r[0]] = c
    cost[y0:y1 + 1, x0:x1 + 1] = saved
    return road


ELF_AREA_CAP = 45


def build(ctx, cost, helpers):
    out = {}
    rng = random.Random(SEED)
    world = ctx['world']
    # twierdza orkow: na wschod od Varden, w lasach miedzy Varden a Selvaran (Leze Grimtootha na polnoc stad)
    tw, th = FW + 2 * FM, FH + 2 * FM
    # 2026-11-12: miejsce przypiete (dawny wynik) - zmiany obozow i dziczy nie przesuwaja twierdzy; brak miejsca = stare szukanie
    try:
        tx, ty = St.find_site(ctx, helpers, cost, (PIN_ORC[0] - FM + tw // 2, PIN_ORC[1] - FM + th // 2), tw, th,
                              core=(FM - 1, FM - 1, FM + FW, FM + FH), radius=1)
    except AssertionError:
        tx, ty = St.find_site(ctx, helpers, cost, (685, 510), tw, th, core=(FM - 1, FM - 1, FM + FW, FM + FH), radius=60)
    origin = (tx + FM, ty + FM)
    plan = orc_plan(world, origin, rng)
    plan.check()
    St.clear_surface(ctx, tx - 2, ty - 2, tx + tw + 1, ty + th + 1)
    W = St.stamp(ctx, plan, origin, helpers['mark'])
    road = _connect(ctx, cost, helpers, (origin[0] + CX, origin[1] + FH + FM), (tx, ty, tx + tw - 1, ty + th - 1))
    out['orc'] = dict(origin=origin, gate=(origin[0] + CX, origin[1] + FH), road=road,
                      door=W(16, 21, -3) + (-3,), chest=W(16, 4, -3) + (-3,), boss=W(16, 14, -3) + (-3,),
                      under=W(0, 0, -1))
    # wioska elfow: w lesie na poludniowy zachod od Varden (pas lasu zageszczony wokol polany)
    vw, vh = EW + 2 * EM, EH + 2 * EM
    try:
        vx, vy = St.find_site(ctx, helpers, cost, (PIN_ELF[0] - EM + vw // 2, PIN_ELF[1] - EM + vh // 2), vw, vh,
                              core=(EM - 1, EM - 1, EM + EW, EM + EH), radius=1, max_water=0.06)
    except AssertionError:
        vx, vy = St.find_site(ctx, helpers, cost, (385, 535), vw, vh, core=(EM - 1, EM - 1, EM + EW, EM + EH), radius=60,
                              max_water=0.06)
    origin = (vx + EM, vy + EM)
    plan = elf_plan(world, origin, rng)
    plan.check()
    St.clear_surface(ctx, vx - 2, vy - 2, vx + vw + 1, vy + vh + 1)
    W = St.stamp(ctx, plan, origin, helpers['mark'])
    road = _connect(ctx, cost, helpers, (origin[0] + 29, origin[1] + EH + EM), (vx, vy, vx + vw - 1, vy + vh - 1))
    out['elf'] = dict(origin=origin, road=road, under=W(0, 0, -1))
    # las wokol osady: lowisko na poziom osady (+ zapas drogi), bez silnych potworow pustyni tuz za drzewami
    ctx.setdefault('area_caps', []).append((vx - 30, vy - 30, vx + vw + 30, vy + vh + 30, 0, 0, ELF_AREA_CAP))
    ctx['strongholds'] = out
    return out
