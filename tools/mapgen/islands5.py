"""Piec nowych wysp (Darek 2026-11-12): Wyspa Szeptow (darmowa) i cztery wyspy premium - lodowa, pustynna, tropikalna i
umiarkowana, oraz Nora Sroki na kontynencie (maly loch z broszka) i przystan promowego Oswina.

  * Wyspa Szeptow lezy na otwartym morzu na wschod od wybrzeza przy przystani promowego (``quay_captain``, statek przy
    nabrzezu poza miastem, osiagalny pieszo z Varden). Statek plynie bez premium, ale dopiero po queście ``ferry``
    (srebrna broszka z Nory Sroki): flaga ``ship.whisper`` (``data/ship_access.csv``).
  * Cztery wyspy premium (plynie sie statkiem premium albo promocja + flaga ``ship.<wyspa>`` z questu dostepu) maja
    organiczny lad z pagorkami (``hills.Surface``), dwa osobne lochy (trzy i piec pieter -1..-5, krecace sie siatki komor),
    wieze +1/+2, kilka lowisk na powierzchni, przystan z kapitanem i zleceniodawcami oraz sanktuarium bossa za drzwiami
    poziomu i bramą zadania (quest: albo party:N).
Plany ida przez ``structures.stamp`` (podziemia pod morzem tam, gdzie jest wolne miejsce). Dane gry: ``islands5_data.py``.
"""
import math
import random

import numpy as np

import features as F
import hills as H
import isles as I
import structures as St
import islands5_data as D

NONE = '-'
SEED = 20261301


# ---------------------------------------------------------------------- nieregularne kształty (zasada: żadnych prostokątów)
def noisy(rng, rx, ry, amp=0.2):
    """Elipsa z falującym brzegiem (2, 3 i 5 fal): względne pola (dx, dy); największy wypust do 1 + amp promienia."""
    ph = [rng.uniform(0, 6.28) for _ in range(3)]
    a = (amp * 0.5, amp * 0.3, amp * 0.2)
    R = int(max(rx, ry) * (1 + amp)) + 2
    out = set()
    for dy in range(-R, R + 1):
        for dx in range(-R, R + 1):
            th = math.atan2(dy / ry, dx / rx)
            t = 1 + a[0] * math.sin(2 * th + ph[0]) + a[1] * math.sin(3 * th + ph[1]) + a[2] * math.sin(5 * th + ph[2])
            if math.hypot(dx / rx, dy / ry) < t:
                out.add((dx, dy))
    return out


def biggest(cells):
    """Największa spójna (4 kierunki) część zbioru pól."""
    left, best = set(cells), set()
    while left:
        st = [next(iter(left))]
        comp = set()
        while st:
            c = st.pop()
            if c in comp or c not in left:
                continue
            comp.add(c)
            st += [(c[0] + dx, c[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        left -= comp
        if len(comp) > len(best):
            best = comp
    return best


def irregular(rng, k):
    """Platforma wieży obozu: falujący blob o różnych rozmiarach (k = numer wieży), z wypustem (pomost) i wgryzionym stopniem."""
    rx, ry = [(3.9, 2.7), (2.8, 3.7), (3.4, 3.1)][k % 3]
    cells = noisy(rng, rx, ry, amp=0.3)
    sx, sy = rng.choice((-1, 1)), rng.choice((-1, 1))
    lobe = shift(noisy(rng, 1.5, 1.4, amp=0.3), int(sx * rx * 0.65), int(sy * ry * 0.4))
    cells |= lobe
    bx, by = -sx * int(rx * 0.7), sy * int(ry * 0.5)
    cells = {c for c in cells if not (abs(c[0] - bx) <= 1 and (c[1] - by) * sy >= 0 and (c[1] - by) * sy <= 1 and abs(c[0]) > rx * 0.5)}
    return biggest(cells)


def shift(cells, ox, oy):
    return {(x + ox, y + oy) for (x, y) in cells}


def ring4(cells):
    return {(x, y) for (x, y) in cells if any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def erode(cells, n=1):
    for _ in range(n):
        cells = cells - ring4(cells)
    return cells


def pick(cells, target, exclude=()):
    c = [q for q in cells if q not in exclude]
    return min(c, key=lambda q: (q[0] - target[0]) ** 2 + (q[1] - target[1]) ** 2)


def room(p, z, cells, floor, wall):
    """Pokój o nieregularnym obrysie: ściana na brzegu, podłoga w środku. Zwraca (wnętrze, brzeg)."""
    ring = ring4(cells)
    for (x, y) in cells:
        p.put(x, y, z, (floor, NONE, NONE, wall if (x, y) in ring else NONE))
    return cells - ring, ring


def gap(p, z, ring, toward, floor):
    """Przejście w ścianie pokoju: pole brzegu najbliższe punktowi toward."""
    q = pick(ring, toward)
    p.put(q[0], q[1], z, (floor, NONE, NONE, NONE))
    return q


# ---------------------------------------------------------------------- style pieter (ruiny, groty, mgla)
F.CAVE_STYLES.setdefault('whisper_grot', ('cave_floor', 'mossy_wall', ['glow_moss', 'shells'], ['rock', 'rubble', 'crystal'], 'glow_moss'))
F.CAVE_STYLES.setdefault('dune_tomb', ('sandstone_floor', 'sandstone_wall', ['bones', 'cobweb', 'debris'], ['rubble', 'sandstone_rock', 'sand_statue'], 'wall_torch'))
F.CAVE_STYLES.setdefault('mist_ruin', ('stone_floor', 'mossy_wall', ['glow_moss', 'bones', 'reeds'], ['rubble', 'broken_wall', 'jungle_statue'], 'glow_moss'))
F.CAVE_STYLES.setdefault('barrow', ('tomb_floor', 'stone_wall', ['bones', 'cobweb', 'blood'], ['rubble', 'broken_wall', 'barrel'], 'wall_torch'))
F.WATER_STYLES.update({'whisper_grot', 'mist_ruin'})

THEMES = {
    'whisper': dict(ground=('grass',), patches=[(0.62, 'dirt'), (-0.2, 'sand')], mount_ground=('grass', 'dirt'), wall='mountain',
                    pond='water', deco=[(0.2, 'tall_grass'), (0.05, 'flowers'), (0.03, 'fern'), (0.01, 'bones')], density=0.04,
                    objects=['tree', 'tree', 'pine', 'bush', 'bush', 'rock']),
    'ice': dict(ground=('snow',), patches=[(0.62, 'ice'), (-0.2, 'frozen_floor')], mount_ground=('snow', 'ice'), wall='snow_mountain',
                pond='water', deco=[(0.16, 'snow_tuft'), (0.01, 'bones')], density=0.05,
                objects=['frost_pine', 'frost_pine', 'frost_bush', 'snow_rock', 'ice_crystal', 'snowman']),
    'desert': dict(ground=('sand',), patches=[(0.6, 'dirt'), (-0.2, 'dark_sand')], mount_ground=('sand', 'dirt'), wall='sand_cliff',
                   pond='water', deco=[(0.1, 'dry_grass'), (0.05, 'dry_shrub'), (0.03, 'bones')], density=0.035,
                   objects=['cactus', 'sandstone_rock', 'sandstone_rock', 'dry_bush', 'dead_tree']),
    'tropic': dict(ground=('jungle_grass',), patches=[(0.6, 'blighted_grass'), (-0.18, 'dark_sand')], mount_ground=('jungle_grass', 'dirt'),
                   wall='mountain', pond='water', deco=[(0.16, 'jungle_plant'), (0.1, 'fern'), (0.05, 'reeds'), (0.02, 'mushrooms')],
                   density=0.07, objects=['jungle_tree', 'jungle_tree', 'jungle_tree', 'jungle_bush', 'palm', 'rock']),
    'oak': dict(ground=('grass',), patches=[(0.62, 'dirt'), (-0.2, 'field')], mount_ground=('grass', 'dirt'), wall='mountain',
                pond='water', deco=[(0.2, 'tall_grass'), (0.06, 'flowers'), (0.03, 'fern')], density=0.06,
                objects=['tree', 'tree', 'tree', 'autumn_tree', 'bush', 'rock']),
}

# dungeons: (klucz, nazwa, pietra [(z, styl, potwory, liczby, (poziom od, do), nazwa pietra)], sanktuarium (nazwa, boss, brama, poziom drzwi))
CFG = {
    'whisper': dict(
        L=34, R=15, n=4, mount=0.2, sep=13, entrances=2, surface_n=10, surface_levels=(8, 40), prefer=(905, 281), tower=None,
        surface=['shore_scuttler', 'wolf', 'boar', 'forest_spider', 'snare_setter', 'tunnel_fence', 'shore_scuttler', 'wolf',
                 'carrion_crow', 'boar'],
        zones=['Plaże Szeptów', 'Wzgórza Szeptów', 'Klify Szeptów'],
        givers=['whisper_hermit', 'whisper_keeper', 'whisper_miner'],
        dungeons=[
            dict(key='A', name='Kopalnia Cichych Ech', floors=[
                (-1, 'whisper_grot', ['forest_spider', 'whisper_shade', 'mine_grub', 'goblin'], (3, 4), (10, 35), 'Groty Szeptów'),
                (-2, 'mine', ['goblin', 'goblin_shaman', 'cave_centipede', 'goblin_shaman', 'whisper_shade'], (3, 4), (16, 48), 'Kopalnia Cichych Ech'),
            ], sanctum=None),
            dict(key='B', name='Kopiec Pana Cieni', floors=[
                (-1, 'barrow', ['skeleton', 'cave_centipede', 'whisper_shade', 'skeleton'], (3, 4), (22, 50), 'Kopiec Pana Cieni'),
                (-2, 'barrow', ['skeleton', 'whisper_shade', 'cairn_wight', 'cairn_wight'], (3, 4), (32, 58), 'Kopiec Pana Cieni'),
                (-3, 'crypt', ['cairn_wight', 'whisper_shade', 'cairn_wight', 'skeleton'], (3, 4), (40, 66), 'Kopiec Pana Cieni'),
            ], sanctum=('Komnata Pana Kopca', 'cairn_lord', None, 40)),
        ],
        chests=[('health_potion', 4), ('mana_potion', 4), ('strong_health_potion', 2), ('strong_mana_potion', 2)]),
    'ice': dict(
        L=50, R=22, n=5, mount=0.3, entrances=3, surface_n=16, surface_levels=(60, 200), prefer=(935, 90),
        surface=['frost_boar', 'yeti', 'frost_witch', 'rimecloak_pilgrim', 'yeti', 'rime_crawler', 'frost_lurker', 'frost_centipede',
                 'rimecloak_pilgrim', 'frost_lurker', 'frost_centipede', 'ice_golem'],
        zones=['Zamarznięte Pola', 'Lodowy Grzbiet', 'Szczyty Wichru'],
        givers=['ice_skuld', 'ice_ragna'],
        camp=dict(boss='blizzard_warchief', mons=['icefang_hunter', 'bonewrap_tamer', 'ironjaw_splitter', 'rimeblood_walker', 'bonewrap_tamer', 'ironjaw_splitter'], levels=(150, 360)),
        tower=dict(name='Wieża Zamieci', mons=['rimecloak_pilgrim', 'frost_lurker', 'ice_golem'], levels=(110, 250)),
        dungeons=[
            dict(key='A', name='Lodowe Pieczary', floors=[
                (-1, 'ice', ['frost_boar', 'yeti', 'frost_witch', 'rime_crawler'], (3, 4), (60, 130), 'Lodowe Pieczary'),
                (-2, 'ice', ['rime_crawler', 'frost_centipede', 'rimecloak_pilgrim', 'ice_golem'], (3, 4), (95, 190), 'Lodowe Pieczary'),
                (-3, 'crystal', ['ice_golem', 'frost_lurker', 'frost_centipede', 'hoarfrost_dragon'], (2, 3), (140, 270), 'Kryształowe Sale'),
            ], sanctum=None),
            dict(key='B', name='Czeluście Zamieci', floors=[
                (-1, 'ice', ['yeti', 'frost_witch', 'rimecloak_pilgrim', 'frost_lurker'], (3, 4), (80, 170), 'Czeluście Zamieci'),
                (-2, 'ice', ['frost_lurker', 'ice_golem', 'frost_centipede', 'rimecloak_pilgrim'], (3, 4), (120, 230), 'Czeluście Zamieci'),
                (-3, 'crystal', ['hoarfrost_dragon', 'ice_golem', 'frost_lurker', 'rimecloak_pilgrim'], (2, 3), (170, 300), 'Czeluście Zamieci'),
                (-4, 'ice', ['hoarfrost_dragon', 'frost_giant', 'ice_golem', 'storm_wisp'], (2, 3), (230, 400), 'Głębia Mrozu'),
                (-5, 'crystal', ['frost_giant', 'ice_dragon', 'hoarfrost_dragon', 'frost_giant'], (2, 3), (320, 520), 'Głębia Mrozu'),
            ], sanctum=('Tron Jarla Szronu', 'rime_jarl', 'quest:ice_m3', 180)),
        ],
        chests=[('great_health_potion', 4), ('great_mana_potion', 4), ('ultimate_health_potion', 3), ('ultimate_mana_potion', 3)]),
    'desert': dict(
        L=46, R=19, n=5, mount=0.3, entrances=3, surface_n=16, surface_levels=(90, 260), prefer=(930, 935),
        surface=['sand_devourer', 'gilded_beetle', 'dune_zealot', 'dune_skulker', 'sand_wraith', 'dune_skulker', 'sand_gargoyle',
                 'dune_zealot', 'scale_dragon', 'sand_gargoyle', 'scale_dragon', 'obelisk_priest'],
        zones=['Złote Wydmy', 'Pole Obelisków', 'Smocze Urwiska'],
        givers=['desert_nadira', 'desert_omar'],
        tower=dict(name='Piramida Wichru', mons=['sand_gargoyle', 'dune_zealot', 'obelisk_priest'], levels=(150, 300)),
        dungeons=[
            dict(key='A', name='Pieczary Skorpionów', floors=[
                (-1, 'dune_tomb', ['sand_devourer', 'gilded_beetle', 'dune_zealot'], (3, 4), (70, 150), 'Pieczary Skorpionów'),
                (-2, 'dune_tomb', ['dune_zealot', 'sand_wraith', 'dune_skulker', 'gilded_beetle'], (3, 4), (110, 220), 'Pieczary Skorpionów'),
                (-3, 'crypt', ['sand_gargoyle', 'dune_skulker', 'mummy', 'sand_wraith'], (2, 3), (160, 290), 'Zapomniany Skarbiec'),
            ], sanctum=None),
            dict(key='B', name='Grobowiec Regenta', floors=[
                (-1, 'dune_tomb', ['dune_skulker', 'dune_zealot', 'sand_wraith'], (3, 4), (110, 210), 'Grobowiec Regenta'),
                (-2, 'crypt', ['mummy', 'sand_gargoyle', 'dune_zealot', 'sand_wraith'], (3, 4), (170, 270), 'Grobowiec Regenta'),
                (-3, 'dune_tomb', ['obelisk_priest', 'mummy', 'sand_gargoyle', 'scale_dragon'], (2, 3), (220, 340), 'Sale Obelisków'),
                (-4, 'dragon_den', ['obelisk_priest', 'crag_dragon', 'coal_dragon', 'mummy'], (2, 3), (280, 420), 'Smocza Krypta'),
                (-5, 'dragon_hoard', ['coal_dragon', 'crag_dragon', 'obelisk_priest', 'dune_drake'], (2, 3), (340, 520), 'Smocza Krypta'),
            ], sanctum=('Pałac Regenta', 'sand_regent', 'party:2', 250)),
        ],
        chests=[('great_health_potion', 4), ('great_mana_potion', 4), ('ultimate_health_potion', 3), ('ultimate_spirit_potion', 2)]),
    'tropic': dict(
        L=46, R=19, n=5, mount=0.3, entrances=3, surface_n=16, surface_levels=(120, 300), prefer=(80, 905),
        surface=['jungle_hunter', 'striped_prowler', 'swamp_hornet', 'jungle_serpent', 'canopy_lurker', 'marsh_lizard', 'crocodile',
                 'lizard_hexer', 'canopy_lurker', 'orchid_mimic', 'marsh_lizard', 'bog_hydra'],
        zones=['Brzeg Mgły', 'Dżungla Orchidei', 'Wzgórza Ruin'],
        givers=['tropic_tupa', 'tropic_inez'],
        tower=dict(name='Świątynia w Koronach', mons=['temple_guardian', 'canopy_lurker', 'blade_warden'], levels=(170, 280)),
        dungeons=[
            dict(key='A', name='Orchidowe Groty', floors=[
                (-1, 'flooded', ['swamp_hornet', 'bog_wraith', 'canopy_lurker', 'marsh_lizard'], (3, 4), (100, 190), 'Orchidowe Groty'),
                (-2, 'moss_grot', ['lizard_hexer', 'orchid_mimic', 'bog_wraith', 'jungle_serpent'], (3, 4), (150, 250), 'Orchidowe Groty'),
                (-3, 'venom_pit', ['orchid_mimic', 'bog_hydra', 'lizard_hexer', 'bog_hydra'], (2, 3), (200, 330), 'Jadowite Komory'),
            ], sanctum=None),
            dict(key='B', name='Ruiny Mgły', floors=[
                (-1, 'mist_ruin', ['marsh_lizard', 'canopy_lurker', 'bog_wraith'], (3, 4), (110, 200), 'Ruiny Mgły'),
                (-2, 'mist_ruin', ['lizard_hexer', 'temple_guardian', 'orchid_mimic', 'canopy_lurker'], (3, 4), (160, 270), 'Ruiny Mgły'),
                (-3, 'moss_grot', ['bog_hydra', 'temple_guardian', 'blade_warden', 'orchid_mimic'], (2, 3), (220, 340), 'Zatopione Sale'),
                (-4, 'venom_pit', ['bog_naga', 'blade_warden', 'venom_hydra', 'bog_hydra'], (2, 3), (280, 410), 'Gniazdo Węży'),
                (-5, 'serpent_lair', ['venom_hydra', 'stone_gorgon', 'bog_naga', 'ember_beetle'], (2, 3), (340, 540), 'Gniazdo Węży'),
            ], sanctum=('Tron Cesarzowej', 'vine_empress', 'quest:tropic_m3', 300)),
        ],
        chests=[('great_health_potion', 4), ('great_mana_potion', 4), ('ultimate_health_potion', 3), ('ultimate_mana_potion', 3)]),
    'oak': dict(
        L=48, R=20, n=5, mount=0.28, entrances=3, surface_n=18, surface_levels=(100, 330), prefer=(60, 60),
        surface=['briar_boar', 'moor_crone', 'briar_boar', 'moor_crone', 'urgoth', 'crimson_shade', 'cursed_treant', 'moor_crone',
                 'crimson_shade', 'wyvern', 'cursed_treant', 'wraith_knight'],
        zones=['Dębina', 'Wrzosowiska', 'Wiekowe Gaje'],
        givers=['oak_hollis', 'oak_maeve'],
        tower=dict(name='Wieża Dębów', mons=['crimson_shade', 'wyvern', 'wraith_knight'], levels=(230, 330)),
        dungeons=[
            dict(key='A', name='Kurhan Wrzosowisk', floors=[
                (-1, 'barrow', ['briar_boar', 'moor_crone', 'moor_crone'], (3, 4), (100, 200), 'Kurhan Wrzosowisk'),
                (-2, 'barrow', ['moor_crone', 'crimson_shade', 'cursed_treant'], (3, 4), (170, 290), 'Kurhan Wrzosowisk'),
                (-3, 'crypt', ['crimson_shade', 'wraith_knight', 'cursed_treant'], (2, 3), (230, 350), 'Zapomniana Krypta'),
            ], sanctum=None),
            dict(key='B', name='Dębowe Katakumby', floors=[
                (-1, 'ruin', ['moor_crone', 'crimson_shade', 'cursed_treant'], (3, 4), (150, 270), 'Dębowe Katakumby'),
                (-2, 'ruin', ['cursed_treant', 'wraith_knight', 'wyvern'], (3, 4), (210, 330), 'Dębowe Katakumby'),
                (-3, 'moss', ['wraith_knight', 'wyvern', 'crimson_shade'], (2, 3), (260, 390), 'Korzenie Dębów'),
                (-4, 'moss', ['wyvern', 'wraith_knight', 'cursed_treant', 'wraith_knight'], (2, 3), (300, 430), 'Korzenie Dębów'),
                (-5, 'crypt', ['wraith_knight', 'wyvern', 'cursed_treant', 'velmira'], (2, 3), (340, 470), 'Serce Dębów'),
            ], sanctum=('Sala Władcy Dębów', 'oak_sovereign', 'party:3', 280)),
        ],
        chests=[('ultimate_health_potion', 4), ('ultimate_mana_potion', 4), ('supreme_health_potion', 2), ('supreme_mana_potion', 2)]),
}
ORDER = ['whisper', 'ice', 'desert', 'tropic', 'oak']


# ====================================================================== morze
def sea_mask(world):
    S = len(world.floors[0])
    m = np.zeros((S, S), bool)
    g = world.floors[0]
    for y in range(S):
        row = g[y]
        for x in range(S):
            c = row[x]
            m[y, x] = c is not None and c[0] in ('water', 'deep_water') and c[2] == NONE and c[3] == NONE
    return m


def find_sea(mask, taken, h, w, prefer, margin=8):
    """Lewy gorny rog okna h x w (z pasem margin dookola calkiem na wodzie), najblizej prefer; taken - zajete prostokaty."""
    S = mask.shape[0]
    land = ~mask
    for (a, b, c, d) in taken:
        land[max(0, b - margin):d + margin + 1, max(0, a - margin):c + margin + 1] = True
    ii = np.zeros((S + 1, S + 1), np.int32)
    ii[1:, 1:] = land.cumsum(0).cumsum(1)
    hh, ww = h + 2 * margin, w + 2 * margin
    ys = np.arange(4, S - hh - 4)
    xs = np.arange(4, S - ww - 4)
    tot = (ii[ys[:, None] + hh, xs[None, :] + ww] - ii[ys[:, None], xs[None, :] + ww]
           - ii[ys[:, None] + hh, xs[None, :]] + ii[ys[:, None], xs[None, :]])
    ok = np.argwhere(tot == 0)
    if not len(ok):
        return None
    px, py = prefer
    d = (ok[:, 1] + ww // 2 - px) ** 2 + (ok[:, 0] + hh // 2 - py) ** 2
    i = int(np.argmin(d))
    return int(xs[ok[i, 1]]) + margin, int(ys[ok[i, 0]]) + margin


# ====================================================================== budowa wyspy
def _fit(p, name):
    return p


def _tower(p, sf, th, cfg, rng, level):
    """Wieża +1/+2 w dolinie o nieregularnym obrysie (w całości na przechodnim terenie). Zwraca pola w układzie planu."""
    walk = sf.walk_mask()
    base = noisy(rng, 5.8, 4.3)
    big = sorted(shift(base, 0, 0) | {(x + dx, y + dy) for (x, y) in base for dx in (-1, 0, 1) for dy in (-1, 0, 1)})
    offs = np.array(big)
    ys, xs = np.nonzero(walk)
    cand = list(zip(xs.tolist(), ys.tolist()))
    rng.shuffle(cand)
    for (x0, y0) in cand[:4000]:
        X, Y = offs[:, 0] + x0, offs[:, 1] + y0
        if X.min() < 1 or Y.min() < 1 or X.max() >= sf.W - 1 or Y.max() >= sf.H - 1:
            continue
        if not walk[Y, X].all():
            continue
        if sf.reserved[Y - 1, X].any() or sf.reserved[Y + 1, X].any():
            continue
        if math.hypot(x0 - sf.cx, y0 - sf.cy) < 6:
            continue
        px, py = sf.plan(x0, y0)
        sf.taken[Y, X] = True
        return shift(base, px, py), (px, py)
    raise AssertionError('no tower site')


def _build_tower(p, spot, cfg, rng, level):
    cells0, (cx, cy) = spot
    t = cfg['tower']
    cells = {0: cells0, 1: cells0, 2: erode(cells0, 1)}
    inner = {}
    for z in (0, 1, 2):
        inner[z], ring = room(p, z, cells[z], 'stone_floor' if z == 0 else 'wood_floor', 'stone_wall')
        if z == 0:
            ring0 = ring
    door = max((q for q in ring0 if abs(q[0] - cx) <= 3), key=lambda q: q[1])
    p.put(door[0], door[1], 0, ('stone_floor', NONE, NONE, NONE))
    deep1 = erode(cells[1], 1)
    deep2 = erode(cells[2], 1)
    s0 = pick(deep1, (cx - 3, cy - 1))
    s1 = pick(deep2, (cx + 3, cy - 1), exclude={s0})
    p.stairs(0, s0, s0)
    p.stairs(1, s1, s1)
    torches = set()
    for z in (0, 1, 2):
        ri = ring4(cells[z])
        near_wall = {q for q in inner[z] if any((q[0] + dx, q[1] + dy) in ri for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
        for tgt in ((cx - 4, cy + 2), (cx + 4, cy + 2), (cx, cy - 4)):
            q = pick(near_wall, tgt, exclude={s0, s1})
            c = p.get(q[0], q[1], z)
            if c is not None and c[2] == NONE:
                p.put(q[0], q[1], z, (c[0], c[1], 'wall_torch', NONE))
                torches.add(q)
    lo, hi = t['levels']
    used = {s0, s1} | torches
    for z, pts in ((0, [(cx - 3, cy + 2), (cx + 3, cy + 2)]), (1, [(cx - 3, cy + 1), (cx, cy + 1), (cx + 3, cy + 2)]),
                   (2, [(cx - 2, cy + 1), (cx + 2, cy + 1), (cx, cy)])):
        for i, tgt in enumerate(pts):
            q = pick(erode(cells[z], 1), tgt, exclude=used | {door})
            p.spawn(t['mons'][(i + z) % len(t['mons'])], q[0], q[1], z, radius=2, count=2 + (i % 2), respawn=200 + 40 * z)
    xs = [c[0] for c in cells0]
    ys = [c[1] for c in cells0]
    p.regions.append((t['name'], min(xs), min(ys), max(xs), max(ys), 0, 2, lo, hi))
    return door, s0, s1


# ---------------------------------------------------------------------- obóz barbarzyńców (wyspa lodowa)
CAMP_NAME = 'Obóz Wodza Zamieci'


def _camp_site(sf, rng):
    """Miejsce na duży obóz: nieregularna plama na lądzie wyspy, z otoczeniem; zwraca pola w układzie sf i środek."""
    import camps as CB
    walk = sf.walk_mask()
    best = None
    why = {}
    for attempt in range(8):
        cells = CB.blob(rng, rng.uniform(11.0, 12.5), rng.uniform(8.5, 10.0))
        big = np.array(sorted(CB.dilate(cells, 2)))
        cl = np.array(sorted(cells))
        ring3 = np.array(sorted(CB.dilate(cells, 3) - cells))
        ins = np.array(sorted(CB.dilate(cells, 1)))
        for _ in range(3000):
            x0, y0 = rng.randrange(sf.W), rng.randrange(sf.H)
            X, Y = big[:, 0] + x0, big[:, 1] + y0
            if X.min() < 1 or Y.min() < 1 or X.max() >= sf.W - 1 or Y.max() >= sf.H - 1:
                continue
            if not sf.inside[ins[:, 1] + y0, ins[:, 0] + x0].all():
                why['inside'] = why.get('inside', 0) + 1
                continue
            if sf.reserved[Y, X].any():
                why['res'] = why.get('res', 0) + 1
                continue
            if math.hypot(x0 - sf.gates[0][0], y0 - sf.gates[0][1]) < 16:
                why['gate'] = why.get('gate', 0) + 1
                continue
            wi = walk[cl[:, 1] + y0, cl[:, 0] + x0].mean()
            wo = walk[ring3[:, 1] + y0, ring3[:, 0] + x0].mean()
            if wi < 0.4 or wo < 0.4:
                why['walk'] = why.get('walk', 0) + 1
                continue
            sc = wi + wo
            if best is None or sc > best[0]:
                best = (sc, cells, x0, y0, big)
        if best is not None:
            break
    assert best is not None, f'no camp site {why} {sf.inside.shape}'
    _sc, cells, x0, y0, big = best
    sf.taken[big[:, 1] + y0, big[:, 0] + x0] = True
    sf.reserved[big[:, 1] + y0, big[:, 0] + x0] = True
    return shift(cells, x0, y0), (x0, y0)


def _build_camp(p, sf, th, cfg, rng, site):
    """Obóz z palisady (kłody), chatami, wieżami +1/+2 na drabinach i wodzem; wszystko o nieregularnym obrysie."""
    cc = cfg['camp']
    cells_sf, (cx0, cy0) = site
    cells = {sf.plan(*q) for q in cells_sf}
    cx, cy = sf.plan(cx0, cy0)
    ring = ring4(cells)
    inner = cells - ring
    walk = sf.walk_mask()
    ground = th['ground'][0]

    def outside_ok(q):
        x, y = q[0] + sf.cx, q[1] + sf.cy
        return 0 <= x < sf.W and 0 <= y < sf.H and walk[y, x]
    # teren: ubita ziemia przy ognisku, śnieg dalej, kości i krew
    for (x, y) in cells:
        dirt = math.hypot(x - cx, y - cy) < 6.5 or rng.random() < 0.25
        r = rng.random()
        deco = 'bones' if r < 0.05 else 'blood' if r < 0.07 else 'debris' if r < 0.09 else NONE
        p.put(x, y, 0, ('dirt' if dirt else ground, deco, NONE, NONE))
    # bramy: 3 przerwy w palisadzie tam, gdzie za nią jest przechodni teren
    gates, gate_out = [], []
    base = rng.uniform(0, 6.28)
    for i in range(12):
        a = base + (i % 3) * 2.094 + (i // 3) * 0.35
        q = min(ring, key=lambda c: abs(((math.atan2(c[1] - cy, c[0] - cx) - a) + math.pi) % (2 * math.pi) - math.pi))
        d = math.hypot(q[0] - cx, q[1] - cy)
        ux, uy = (q[0] - cx) / d, (q[1] - cy) / d
        o = (int(round(q[0] + ux * 3)), int(round(q[1] + uy * 3)))
        if not outside_ok(o) or any(math.hypot(q[0] - g[0], q[1] - g[1]) < 9 for g in gates):
            continue
        gates.append(q)
        gate_out.append(o)
        if len(gates) == 3:
            break
    assert len(gates) >= 2, 'camp: no gates'
    gapc = {q for q in ring if any(math.hypot(q[0] - g[0], q[1] - g[1]) <= 2.3 for g in gates)}
    for q in ring:
        if q in gapc:
            p.put(q[0], q[1], 0, ('dirt', NONE, NONE, NONE))
        else:
            p.put(q[0], q[1], 0, ('dirt', NONE, NONE, 'log_wall'))
    for (ox, oy) in gate_out:
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                c = p.get(ox + dx, oy + dy, 0)
                if c is not None and (ox + dx, oy + dy) not in ring and c[2] != NONE and c[2] not in F.STAIRS:
                    p.put(ox + dx, oy + dy, 0, (c[0], c[1], NONE, NONE))
    # ognisko i chaty (nieregularne), chata wodza po przeciwnej stronie niż bramy
    used = {(cx, cy)}
    p.obj(cx, cy, 0, 'campfire')
    gx = sum(g[0] for g in gates) / len(gates)
    gy = sum(g[1] for g in gates) / len(gates)
    deep = erode(cells, 3)
    far = (cx - (gx - cx) * 0.55, cy - (gy - cy) * 0.55)
    ch = pick(deep, far)
    hall = shift(noisy(rng, 4.6, 3.8, amp=0.16), ch[0], ch[1])
    hall &= erode(cells, 2)
    assert len(hall) > 40, 'camp: chief hut too small'
    hin, hring = room(p, 0, hall, 'wood_floor', 'log_wall')
    hd = gap(p, 0, hring, (cx, cy), 'wood_floor')
    chest_pts = []
    for tgt in ((ch[0] - 3, ch[1] - 2), (ch[0] + 3, ch[1] - 2), (ch[0] - 3, ch[1] + 2), (ch[0] + 3, ch[1] + 2), (ch[0], ch[1] - 3)):
        q = pick(erode(hall, 1), tgt, exclude=used | {hd})
        used.add(q)
        chest_pts.append(q)
    boss = pick(erode(hall, 1), (ch[0], ch[1] + 1), exclude=used)
    used.add(boss)
    hut_cells = set(hall)
    huts = []
    for _ in range(200):
        if len(huts) >= 2:
            break
        q = rng.choice(sorted(deep))
        if math.hypot(q[0] - cx, q[1] - cy) < 5 or math.hypot(q[0] - ch[0], q[1] - ch[1]) < 9:
            continue
        hc = shift(noisy(rng, 2.5, 2.2, amp=0.18), q[0], q[1])
        if not hc <= erode(cells, 1) or hc & hut_cells or any(math.hypot(q[0] - h[0], q[1] - h[1]) < 8 for h in huts):
            continue
        hi2, hr2 = room(p, 0, hc, 'wood_floor', 'log_wall')
        gap(p, 0, hr2, (cx, cy), 'wood_floor')
        hut_cells |= hc
        huts.append(q)
    assert len(huts) >= 1, 'camp: no huts'
    # wieże strażnicze: platformy +1 (dwie z nich z poziomem +2) na drabinach, przy palisadzie między bramami
    towers = []
    mid_angles = [math.atan2(gates[i][1] - cy, gates[i][0] - cx) for i in range(len(gates))]
    mid_angles.sort()
    cand_a = [(mid_angles[i] + mid_angles[(i + 1) % len(mid_angles)] + (2 * math.pi if i + 1 == len(mid_angles) else 0)) / 2
              for i in range(len(mid_angles))]
    cand_a += [a + 0.0 for a in mid_angles] + [base + 0.5 * i for i in range(13)]
    for a in cand_a:
        if len(towers) >= 3:
            break
        tx_, ty_ = cx + math.cos(a) * 9.0, cy + math.sin(a) * 6.8
        q = pick(erode(cells, 3), (tx_, ty_))
        if q in hut_cells or any(math.hypot(q[0] - t[0], q[1] - t[1]) < 8 for t in towers) or math.hypot(q[0] - cx, q[1] - cy) < 5:
            continue
        for _ in range(3):
            rng.uniform(0, 6.28)          # jak dawne noisy(): reszta obozu zostaje taka sama
        pc = shift(irregular(random.Random(7919 * len(towers) + q[0] * 31 + q[1]), len(towers)), q[0], q[1])
        if pc & hut_cells or not pc <= erode(cells, 1) or any(g in pc for g in gates):
            continue
        towers.append(q)
        ri = ring4(pc)
        for (x, y) in pc:
            p.put(x, y, 1, ('wood_floor', NONE, 'fence' if (x, y) in ri else NONE, NONE))
        pin = pc - ri
        at0 = pick(pin, (cx, cy))
        p.put(at0[0], at0[1], 1, ('wood_floor', NONE, NONE, NONE))
        p.ladder(0, at0)
        # poziom +2 na dwóch wieżach
        if len(towers) <= 2:
            p2 = biggest({c for c in erode(pc, 1) if not (c[0] >= q[0] + 2 and c[1] <= q[1] - 1)})
            if len(p2 - ring4(p2)) < 6:
                p2 = biggest(erode(pc, 1))
            if len(p2 - ring4(p2)) < 6:
                p2 = set(pc)
            r2 = ring4(p2)
            for (x, y) in p2:
                p.put(x, y, 2, ('wood_floor', NONE, 'fence' if (x, y) in r2 else NONE, NONE))
            assert len(p2 - r2) >= 5, 'camp: platform +2 too small'
            at1 = pick(p2 - r2, q, exclude={at0})
            p.put(at1[0], at1[1], 1, ('wood_floor', NONE, NONE, NONE))
            p.ladder(1, at1)
            top = pick(p2 - r2, (q[0] + 1, q[1] + 1), exclude={at1})
            p.spawn('icefang_hunter', top[0], top[1], 2, radius=1, count=2, respawn=260)
        p1 = pick(pin, q, exclude={at0})
        p.spawn('icefang_hunter', p1[0], p1[1], 1, radius=1, count=2, respawn=240)
        for (x, y) in pc:
            used.add((x, y))
    assert len(towers) >= 2, 'camp: watchtowers'
    # namioty, skrzynie, sztandary, drewno
    props = ['orc_tent', 'orc_tent', 'orc_tent', 'crate', 'barrel', 'firewood', 'hay_bale', 'campfire']
    spots = sorted(q for q in deep if q not in used and q not in hut_cells and math.hypot(q[0] - cx, q[1] - cy) >= 3)
    rng.shuffle(spots)
    placed, k = [], 0
    for q in spots:
        if k >= 16:
            break
        if any(abs(q[0] - a) + abs(q[1] - b) < 4 for a, b in placed) or any(abs(q[0] - g[0]) + abs(q[1] - g[1]) < 5 for g in gates):
            continue
        c = p.get(q[0], q[1], 0)
        if c is None or c[2] != NONE or not St.walk(c):
            continue
        p.put(q[0], q[1], 0, (c[0], 'bones' if rng.random() < 0.25 else c[1], props[k % len(props)], NONE))
        placed.append(q)
        k += 1
    for g in gates:
        d = math.hypot(g[0] - cx, g[1] - cy)
        ux, uy = (g[0] - cx) / d, (g[1] - cy) / d
        for sgn in (-1, 1):
            bq = (int(round(g[0] + ux * 1.5 - uy * 3 * sgn)), int(round(g[1] + uy * 1.5 + ux * 3 * sgn)))
            c = p.get(bq[0], bq[1], 0)
            if c is not None and St.walk(c) and c[2] == NONE and bq in inner:
                p.put(bq[0], bq[1], 0, (c[0], c[1], 'banner', NONE))
    # potwory: wódz w chacie, grupy po obozie (co najmniej 8 pól od siebie)
    choices = list(zip(chest_pts, ('blizzard_sword', 'blizzard_bow', 'blizzard_wand', 'blizzard_rod', 'blizzard_shield')))
    p.spawn(cc['boss'], boss[0], boss[1], 0, radius=1, count=1, respawn=3600)
    free = [q for q in sorted(inner) if q not in hut_cells and q not in used and St.walk(p.get(q[0], q[1], 0))
            and p.get(q[0], q[1], 0)[2] == NONE and all(St.walk(p.get(q[0] + a, q[1] + b, 0) or ('x', NONE, 'x', 'x')) for a in (-1, 0, 1) for b in (-1, 0, 1))
            and math.hypot(q[0] - cx, q[1] - cy) >= 3]
    assert len(free) > 20, 'camp: no room for monsters'
    pts = [min(free, key=lambda q: math.hypot(q[0] - cx, q[1] - cy))]
    while len(pts) < len(cc['mons']):
        q = max(free, key=lambda q: min(math.hypot(q[0] - a, q[1] - b) for a, b in pts))
        pts.append(q)
    for i, q in enumerate(pts):
        p.spawn(cc['mons'][i], q[0], q[1], 0, radius=3, count=3 if i % 3 != 2 else 2, respawn=200)
    xs = [q[0] for q in cells]
    ys = [q[1] for q in cells]
    lo, hi = cc['levels']
    p.regions.append((CAMP_NAME, min(xs) - 2, min(ys) - 2, max(xs) + 2, max(ys) + 2, 0, 2, lo, hi))
    return dict(rect=(min(xs), min(ys), max(xs), max(ys)), centre=(cx, cy), gates=gates, towers=towers, boss=boss, choices=choices)



def _sanctum_site(rect, inside_fn, arrive):
    """Miejsce sali bossa (X, Y, w, h) w polowie wyspy, daleko od wejscia (rezerwowane przed budowa pietra)."""
    w, h = 15, 11
    x0, y0, rw, rh = rect
    best = None
    for X in range(x0 + 4, x0 + rw - w - 4, 2):
        for Y in range(y0 + 4, y0 + rh - h - 4, 2):
            pts = [(X - 4, Y - 4), (X + w + 4, Y - 4), (X - 4, Y + h + 4), (X + w + 4, Y + h + 4), (X + w // 2, Y + h // 2)]
            if not all(inside_fn(q) for q in pts):
                continue
            d = min(abs(X + w // 2 - a[0]) + abs(Y + h // 2 - a[1]) for a in arrive)
            if best is None or d > best[0]:
                best = (d, X, Y)
    assert best is not None, 'no room for the sanctum'
    return best[1], best[2], w, h


def _sanctum(p, z, site, res, floor_cfg, sanct, gates, level_of):
    """Sala bossa na dnie lochu B: pudlo za drzwiami poziomu (i brama zadania), korytarz do najblizszej komory."""
    name, boss, gate, door_level = sanct
    X, Y, w, h = site
    style = floor_cfg[1]
    floor_s, wall_s = F.CAVE_STYLES[style][0], F.CAVE_STYLES[style][1]
    yd = Y + h // 2
    rooms = [q for q in res['rooms'] if q not in res['downs']]
    best = None
    for q in rooms:
        if q[0] < X - 3 or q[0] > X + w + 2:
            d = abs(q[0] - (X if q[0] < X else X + w)) + abs(q[1] - yd)
            if best is None or d < best[0]:
                best = (d, q)
    assert best is not None, 'sanctum: no room beside the box'
    leaf = best[1]
    left = leaf[0] < X
    rr = random.Random(X * 131 + Y * 7 + z)
    base = noisy(rr, (w - 1) / 2 / 1.14, (h - 1) / 2 / 1.14, amp=0.12)
    cells = shift(base, X + w // 2, Y + h // 2)
    inner, ring = room(p, z, cells, 'marble_floor', 'stone_wall')
    rowc = [q for q in ring if q[1] == yd]
    door = min(rowc) if left else max(rowc)
    p.level_door(z, door, door_level, floor='marble_floor')
    sx = door[0] - 1 if left else door[0] + 1
    path = []
    a = sx
    step = -1 if leaf[0] < sx else 1
    while a != leaf[0]:
        path.append((a, yd))
        a += step
    b = yd
    step = -1 if leaf[1] < yd else 1
    while b != leaf[1]:
        path.append((leaf[0], b))
        b += step
    path.append(leaf)
    p.corridor(z, path, floor_s, wall_s)
    deep = erode(cells, 1)
    near_wall = {q for q in inner if any((q[0] + dx, q[1] + dy) in ring for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    used = set()
    for tgt in ((X + 2, Y + 1), (X + w - 3, Y + 1), (X + 2, Y + h - 2), (X + w - 3, Y + h - 2)):
        q = pick(near_wall, tgt, exclude=used)
        used.add(q)
        p.put(q[0], q[1], z, ('marble_floor', NONE, 'wall_torch', NONE))
    bx, by = pick(deep, (X + w // 2 + (2 if left else -2), Y + h // 2), exclude=used)
    used.add((bx, by))
    p.spawn(boss, bx, by, z, radius=2, count=1, respawn=3600)
    lo = level_of[boss]
    for tgt in ((X + 3, Y + 3), (X + w - 4, Y + 3), (X + 3, Y + h - 4), (X + w - 4, Y + h - 4)):
        q = pick(deep, tgt, exclude=used)
        used.add(q)
        p.spawn(floor_cfg[2][-1], q[0], q[1], z, radius=2, count=2, respawn=300)
    for i, (item, n) in enumerate(floor_cfg[6]):
        q = pick(deep, (X + 4 + 3 * i, Y + 2), exclude=used | {door})
        used.add(q)
        p.chests.append((q[0], q[1], z, min(4500, 2500 + 500 * i), item, n))
    p.regions.append((name, X, Y, X + w - 1, Y + h - 1, z, z, max(1, lo - 90), lo + 220))
    p.behind.append((bx, by, z))
    if gate:
        gates.append((door[0], door[1], z, gate))
    return dict(door=door, boss=(bx, by), box=(X, Y, w, h), leaf=leaf, z=z)


def build_island(ctx, helpers, key, mask, taken, level, mark):
    cfg = CFG[key]
    th = THEMES[key]
    world = ctx['world']
    last = None
    reasons = {}
    for attempt in range(40):
        seed = SEED + D.ORDER_IDX[key] * 1009 + attempt * 31
        rng = random.Random(seed)
        inside, cx, cy = H.make_shape(seed, cfg)
        hh, ww = inside.shape
        pos = find_sea(mask, taken, hh, ww, cfg['prefer'], margin=9)
        if pos is None:
            last = 'no sea'
            continue
        wx, wy = pos
        try:
            sf = H.Surface(cfg, key, seed, inside, cx, cy, rng)
            sf.ridges()
            sf.open_gates(2)
            if len(sf.gates) < 1:
                raise AssertionError('no gates')
            sf.ponds(4, th['pond'])
            name = D.ISLANDS[key]['name'][0]
            p = St.Plan(name)
            sf.paint(p, th)
            for (x, y) in sf.extra:
                px, py = sf.plan(x, y)
                c = p.get(px, py, 0)
                if c is None:
                    p.put(px, py, 0, (th['ground'][0], NONE, NONE, NONE))
                elif c[2] != NONE:
                    p.put(px, py, 0, (c[0], NONE, NONE, NONE))
            for (x, y) in sf.gates:
                px, py = sf.plan(x, y)
                c = p.get(px, py, 0) or (th['ground'][0], NONE, NONE, NONE)
                p.put(px, py, 0, (c[0], NONE, NONE, NONE))
                p.entries.append((px, py))
            ents = sf.pick_entrances(p, cfg['entrances'])
            dxs = max(e[0] for e in ents) - min(e[0] for e in ents)
            dys = max(e[1] for e in ents) - min(e[1] for e in ents)
            axis = 0 if dxs >= dys else 1
            ents = sorted(ents, key=lambda e: e[axis])
            if ents[1][axis] - ents[0][axis] < cfg.get('sep', 16):
                raise AssertionError('entrances too close')
            xs = (ents[0][axis] + ents[1][axis]) // 2
            tower = None
            csite = _camp_site(sf, rng) if cfg.get('camp') else None
            if cfg['tower']:
                tower = _tower(p, sf, th, cfg, rng, level)
            info = _fill(ctx, p, sf, th, cfg, key, ents, xs, axis, tower, rng, level, name)
            if csite:
                info['camp'] = _build_camp(p, sf, th, cfg, rng, csite)
            info.update(wx=wx, wy=wy, cx=cx, cy=cy, hh=hh, ww=ww)
            break
        except AssertionError as e:
            last = str(e)
            reasons[last[:60]] = reasons.get(last[:60], 0) + 1
            continue
    else:
        raise AssertionError(f'{key}: island not built ({last}) {reasons}')
    origin = (wx + cx, wy + cy)
    W = St.stamp(ctx, p, origin, mark, per_floor=True)
    taken.append((wx, wy, wx + ww - 1, wy + hh - 1))
    if 'camp' in info:
        c = info['camp']
        a, b = W(c['rect'][0], c['rect'][1], 0), W(c['rect'][2], c['rect'][3], 0)
        info['camp_world'] = dict(rect=(a[0], a[1], b[0], b[1]), centre=tuple(W(c['centre'][0], c['centre'][1], 0)[:2]),
                                  boss=tuple(W(c['boss'][0], c['boss'][1], 0)[:2]),
                                  choices=[(W(q[0], q[1], 0)[0], W(q[0], q[1], 0)[1], it) for q, it in c['choices']], towers=[tuple(W(t[0], t[1], 1)[:2]) for t in c['towers']])
    if 'camp_world' in info:
        for (x, y, it) in info['camp_world']['choices']:
            ctx.setdefault('star_choices', []).append(('warchief', x, y, 0, it))
        ctx.setdefault('reward_reqs', []).append(('warchief', 'boss:blizzard_warchief'))
    # sanktuaria i bramy
    for g in info['gates']:
        a = W(g[0], g[1], g[2])
        ctx.setdefault('star_gates', []).append((a[0], a[1], g[2], g[3]))
    bosses = set()
    for ln in open(helpers['monsters_csv'], encoding='utf-8'):
        f = ln.split(';')
        if not ln.startswith('#') and len(f) > 17 and f[17].strip() == '1':
            bosses.add(f[0])
    I.tag_packs(ctx, W, p, only=lambda z: z <= -2 or z >= 1, level={m: (0 if m in bosses else v) for m, v in level.items()})
    return p, W, info, origin


def _fill(ctx, p, sf, th, cfg, key, ents, xs, axis, tower, rng, level, name):
    gates_out = []
    under = H.dilate(sf.inside, 4)
    ys_, xs_ = np.nonzero(sf.inside)
    bx0, bx1, by0, by1 = int(xs_.min()) - sf.cx, int(xs_.max()) - sf.cx, int(ys_.min()) - sf.cy, int(ys_.max()) - sf.cy
    area = int(sf.inside.sum())
    # wieza: budowa po ustawieniu terenu
    tinfo = _build_tower(p, tower, cfg, rng, level) if tower else None
    # ---------------------------------------------------------------- powierzchnia
    dock = sf.plan(*sf.gates[0])
    idx_ents = list(sf.entrances)
    pts = sf.spread(cfg['surface_n'], 11, idx_ents + sf.gates)
    pts = [q for q in pts if math.hypot(q[0] - sf.gates[0][0], q[1] - sf.gates[0][1]) > 16]
    assert len(pts) >= cfg['surface_n'] - 4, f'surface spawns {len(pts)}'
    order = sorted(cfg['surface'], key=lambda m: level[m])
    dsurf = H.route_dist(p, 0, {dock: 0})
    pts = sorted(pts, key=lambda q: dsurf.get(sf.plan(*q), 1e9))
    surf_pts = []
    for i, (x, y) in enumerate(pts):
        px, py = sf.plan(x, y)
        c = p.get(px, py, 0)
        if c is None or not St.walk(c):
            continue
        m = order[min(len(order) - 1, i * len(order) // len(pts))]
        p.spawn(m, px, py, 0, radius=4, count=(3, 4)[i % 2], respawn=200)
        surf_pts.append((px, py, m))
    # regiony powierzchni: trzy lowiska wg poziomu potworow (pasy wzdluz drogi od przystani)
    third = max(1, len(surf_pts) // 3)
    lo_all, hi_all = cfg['surface_levels']
    p.regions.append((name, bx0 - 6, by0 - 6, bx1 + 6, by1 + 6, 0, 2 if tower else 0, lo_all, hi_all))
    for k in range(3):
        grp = surf_pts[k * third:(k + 1) * third if k < 2 else len(surf_pts)]
        if not grp:
            continue
        lvls = [level[m] for (_x, _y, m) in grp]
        p.regions.append((cfg['zones'][k], min(g[0] for g in grp) - 7, min(g[1] for g in grp) - 7, max(g[0] for g in grp) + 7,
                          max(g[1] for g in grp) + 7, 0, 0, max(1, min(lvls) - 10), max(lvls) + 20))
    # ---------------------------------------------------------------- podziemia: dwa lochy po obu stronach xs
    rect = (bx0 - 5, by0 - 5, bx1 - bx0 + 11, by1 - by0 + 11)
    sanct_info = None
    chest_i = 0
    for di, dun in enumerate(cfg['dungeons']):
        side_a = di == 0
        if side_a:
            arrive = [e for e in ents if e[axis] < xs]
            arrive = arrive[:1]
        else:
            arrive = [e for e in ents if e[axis] >= xs]
        assert arrive
        if side_a:
            fn0 = lambda q, _xs=xs: q[axis] < _xs - 6 and _inside(under, sf, q)
        else:
            fn0 = lambda q, _xs=xs: q[axis] > _xs + 6 and _inside(under, sf, q)
        cells_area = sum(1 for yy in range(sf.H) for xx in range(0, sf.W, 2) if under[yy, xx] and fn0((xx - sf.cx, yy - sf.cy))) * 2
        dprev = H.route_dist(p, 0, {a: 0 for a in arrive})
        nfl = len(dun['floors'])
        for k, (z, style, mons, counts, lv, fname) in enumerate(dun['floors'], 1):
            before = set(p.f.get(z, {}))
            n = max(9, int(cells_area / (190 + 12 * k)))
            lastf = k == nfl
            fn = fn0
            site = None
            if lastf and dun['sanctum']:
                site = _sanctum_site(rect, fn0, arrive)
                sx0, sy0, sw, sh = site
                fn = lambda q, _f=fn0, _s=site: _f(q) and not (_s[0] - 4 <= q[0] <= _s[0] + _s[2] + 3 and _s[1] - 4 <= q[1] <= _s[1] + _s[3] + 3)
            res = H.floor_net(p, z, rect, arrive, n, style, rng, fn, n_down=0 if lastf else 2, gap=10, room=(3, 5))
            for a in arrive:
                p.stairs(z, a, a)
            rooms = [q for q in res['rooms'] if q not in res['downs']]
            dfl = H.route_dist(p, z, {a: dprev.get(a, 0) + 3 for a in arrive})
            ranked = sorted(rooms, key=lambda q: dfl.get(q, 1e9))
            order_m = sorted(set(mons), key=lambda m: level[m])
            for i, q in enumerate(ranked):
                idx = min(len(order_m) - 1, int(i / max(1, len(ranked)) * len(order_m) + rng.random() * 0.35))
                p.spawn(order_m[idx], q[0], q[1], z, radius=4, count=counts[i % 2], respawn=200 + 10 * k)
            leaves = sorted(res['leaves'], key=lambda q: -res['depth'].get(q, 0))
            for j, q in enumerate(leaves[:1 + k // 2]):
                spot = I.chest_spot(p, z, q)
                if spot is not None:
                    item, cnt = cfg['chests'][(chest_i + j) % len(cfg['chests'])]
                    p.chests.append((spot[0], spot[1], z, min(4900, 100 * k * max(1, lv[0] // 30) + 60 * j), item, cnt))
            chest_i += 1
            if lastf and dun['sanctum']:
                sc = dun['sanctum']
                fcfg = (z, style, mons, counts, lv, fname, cfg['chests'][:3])
                sanct_info = _sanctum(p, z, site, res, fcfg, sc, gates_out, level)
            new = set(p.f[z]) - before
            xs_n = [c[0] for c in new]
            ys_n = [c[1] for c in new]
            p.regions.append((fname, min(xs_n), min(ys_n), max(xs_n), max(ys_n), z, z, lv[0], lv[1]))
            arrive = list(res['downs'])
            dprev = dfl
        p.regions.append((dun['name'], bx0 - 5, by0 - 5, bx1 + 5, by1 + 5, -nfl, -1, cfg['dungeons'][di]['floors'][0][4][0],
                          cfg['dungeons'][di]['floors'][-1][4][1])) if False else None
    n_cells = p.check()
    for (mk, *_r) in p.spawns:
        assert mk in level, f'unknown monster {mk}'
    return dict(gates=gates_out, sanctum=sanct_info, plan_cells=n_cells, dock=dock, ents=ents, tower=tinfo, bbox=(bx0, by0, bx1, by1),
                area=area)


def _inside(under, sf, q):
    x, y = q[0] + sf.cx, q[1] + sf.cy
    return 0 <= x < sf.W and 0 <= y < sf.H and under[y, x]


# ====================================================================== przystanie, NPC
def walkable_free(world, x, y):
    c = world.get(x, y)
    return c is not None and F.walk(c) and c[2] == NONE and c[3] == NONE and c[0] not in ('bridge', 'ship_deck', 'water')


def spot_near(ctx, near, rad=12, min_npc=4, avoid=(), prefer_ground=None):
    world = ctx['world']
    npcs = [(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0] + list(avoid)
    trans = {(t[0], t[1]) for t in ctx['transitions'] if t[2] == 0}
    best = None
    for dy in range(-rad, rad + 1):
        for dx in range(-rad, rad + 1):
            x, y = near[0] + dx, near[1] + dy
            if not walkable_free(world, x, y) or (x, y) in trans:
                continue
            if not all(walkable_free(world, x + a, y + b) for a, b in F.DIRS4):
                continue
            if min([max(abs(x - a), abs(y - b)) for (a, b) in npcs] or [99]) < min_npc:
                continue
            d = abs(dx) + abs(dy)
            if best is None or d < best[0]:
                best = (d, (x, y))
    assert best is not None, f'no spot near {near}'
    return best[1]


def add_dock(ctx, info, W, key, level):
    world = ctx['world']
    v = D.ISLANDS[key]
    gx, gy = W(info['dock'][0], info['dock'][1], 0)
    # kierunek na zewnatrz: od srodka wyspy (0, 0 planu)
    ox, oy = W(0, 0, 0)
    best = None
    for dd in F.DIRS4:
        r0 = F.to_water(world, (gx, gy), dd, limit=40)
        if r0 is None:
            continue
        steps = abs(r0[0] - gx) + abs(r0[1] - gy)
        outward = (gx - ox) * dd[0] + (gy - oy) * dd[1]
        key_ = (steps, -outward)
        if best is None or key_ < best[0]:
            best = (key_, dd)
    d = best[1]
    r = F.small_dock(world, (gx, gy), d)
    assert r is not None, f'{key}: no dock'
    spot, rect = r
    ctx['pz'].append((rect[0] - 4, rect[1] - 4, rect[2] + 4, rect[3] + 4, 0))
    ctx['npcs'].append([v['captain'], v['cname'], 'CAPTAIN', v['name'][0], spot[0], spot[1], 0, '-', '#4a6a8a',
                        'Pływam na kontynent. ' + ('Rejsy tylko dla kont premium.' if v['premium'] else 'Płynę za darmo, jeśli wiesz, po co.')])
    for hub in v['hubs']:
        ctx['routes'].append((v['captain'], hub))
    pos = [spot]
    taken = [spot]
    for gkey in CFG[key]['givers']:
        n = D.NPCS[gkey]
        s = spot_near(ctx, spot, rad=11, min_npc=4, avoid=taken)
        taken.append(s)
        ctx['npcs'].append([gkey, n[0], 'GUIDE', v['name'][0], s[0], s[1], 0, '-', n[1], n[2][0]])
        pos.append(s)
    xs = [q[0] for q in pos] + [rect[0], rect[2]]
    ys = [q[1] for q in pos] + [rect[1], rect[3]]
    ctx['pz'].append((min(xs) - 3, min(ys) - 3, max(xs) + 3, max(ys) + 3, 0))
    return dict(captain=spot, givers=pos[1:], dir=d, rect=rect)


# ====================================================================== kontynent: przystan Oswina
def quay(ctx):
    world = ctx['world']
    start, d = (839, 284), F.E
    captain, rect = F.harbour_with_ship(world, start, d)
    ctx['pz'].append((*rect, 0))
    # poklad: pola ship_deck w prostokacie
    deck = [(x, y) for y in range(rect[1], rect[3] + 1) for x in range(rect[0], rect[2] + 1)
            if world.get(x, y) is not None and world.get(x, y)[0] == 'ship_deck' and world.get(x, y)[2] == NONE]
    far = [q for q in deck if max(abs(q[0] - captain[0]), abs(q[1] - captain[1])) >= 5]
    assert far, 'quay: deck too small'
    oswin = far[len(far) // 2]
    ctx['npcs'].append(['quay_captain', 'Helmsman Brenn', 'CAPTAIN', 'Cicha Przystań', captain[0], captain[1], 0, '-', '#4a6a8a',
                        'Płyniemy na Wyspę Szeptów, ale tylko z listem przewoźnika Oswina. Pytaj go o broszkę.'])
    n = D.NPCS['ferry_oswin']
    ctx['npcs'].append(['ferry_oswin', n[0], 'GUIDE', 'Cicha Przystań', oswin[0], oswin[1], 0, '-', n[1], n[2][0]])
    St.clear_surface(ctx, rect[0] - 10, rect[1] - 10, rect[2] + 10, rect[3] + 10)
    return dict(captain=captain, oswin=oswin, rect=rect)


# ====================================================================== kontynent: Nora Sroki
def magpie_plan(seed):
    style, wall_style = 'mine', 'stone_wall'
    floor_s, wall_s = F.CAVE_STYLES['mine'][0], F.CAVE_STYLES['mine'][1]
    for attempt in range(60):
        r = random.Random(seed + attempt * 313)
        p = St.Plan('Nora Sroki')
        try:
            plat = shift(noisy(r, 4.2, 4.0, amp=0.16), 4, 7)
            for (x, y) in plat:
                p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
            p.pz.append((0, 3, 9, 9, 0))
            hut = shift(noisy(r, 4.1, 3.7, amp=0.14), 14, 6)
            hin, hring = room(p, 0, hut, 'wood_floor', 'wood_wall')
            hd = gap(p, 0, hring, (9, 6), 'wood_floor')
            for x in range(max(q[0] for q in plat if q[1] == 6) + 1, hd[0]):
                p.put(x, 6, 0, ('cobblestone', NONE, NONE, NONE))
            down0 = (16, 6)
            p.stairs_down(0, down0, down0)
            R1 = I.rooms_floor(p, -1, (0, 0, 52, 36), down0, 8, 'mine', r, down=True, down_ok=lambda t: 10 < t[0] < 36, gap=8)
            dn = R1['down']
            p.stairs(-2, dn, dn)
            # -2: korytarz do sali króla (brama zadania ferry_m2), sala, skarbiec za bossem (brama boss:magpie_king)
            L = I.rooms_floor(p, -2, (0, 0, 37, 36), dn, 7, 'mine', r, extra=[(30, 18)], down=False, gap=8)
            hall = shift(noisy(r, 10.3, 9.6, amp=0.14), 49, 18)
            hin2, hring2 = room(p, -2, hall, 'stone_floor', 'stone_wall')
            west = min(q for q in hring2 if q[1] == 18)
            p.put(west[0], 18, -2, ('stone_floor', NONE, NONE, NONE))
            p.corridor(-2, [(x, 18) for x in range(30, west[0] + 1)], floor_s, wall_s)
            vault = shift(noisy(r, 4.2, 4.0, amp=0.14), 65, 18)
            vin, vring = room(p, -2, vault, 'marble_floor', 'stone_wall')
            vd = min(q for q in vring if q[1] == 18)
            east = max(q for q in hring2 if q[1] == 18)
            p.put(east[0], 18, -2, ('stone_floor', NONE, NONE, NONE))
            for x in range(east[0] + 1, vd[0]):
                p.put(x, 18, -2, ('stone_floor', NONE, NONE, 'stone_wall'))
                p.put(x, 17, -2, ('stone_floor', NONE, NONE, 'stone_wall'))
                p.put(x, 19, -2, ('stone_floor', NONE, NONE, 'stone_wall'))
                p.put(x, 18, -2, ('stone_floor', NONE, NONE, NONE))
            p.level_door(-2, vd, 25, floor='marble_floor')
            vdeep = erode(vault, 1)
            c1 = pick(vdeep, (67, 18))
            c2 = pick(vdeep, (67, 16), exclude={c1})
            p.chests.append((c1[0], c1[1], -2, 900, 'silver_brooch', 1))
            p.chests.append((c2[0], c2[1], -2, 600, 'strong_health_potion', 3))
            near_wall = {q for q in hin2 if any((q[0] + dx, q[1] + dy) in hring2 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
            for tgt in ((40, 10), (58, 10), (40, 27), (58, 27)):
                q = pick(near_wall, tgt)
                p.put(q[0], q[1], -2, ('stone_floor', NONE, 'wall_torch', NONE))
            p.finish()
        except AssertionError:
            continue
        for q in R1['chain'][1:]:
            near = [(abs(c[0] - q[0]) + abs(c[1] - q[1]), c) for c, v in p.f[-1].items()
                    if St.walk(v) and v[2] == NONE and abs(c[0] - q[0]) <= 3 and abs(c[1] - q[1]) <= 3]
            if near and abs(q[0] - dn[0]) + abs(q[1] - dn[1]) > 6:
                p.spawn(('bandit', 'snare_setter', 'bandit', 'tunnel_fence')[len(p.spawns) % 4], *min(near)[1], -1, radius=3, count=3, respawn=150)
        for q in L['chain'][1:]:
            near = [(abs(c[0] - q[0]) + abs(c[1] - q[1]), c) for c, v in p.f[-2].items()
                    if St.walk(v) and v[2] == NONE and abs(c[0] - q[0]) <= 3 and abs(c[1] - q[1]) <= 3]
            if near and abs(q[0] - dn[0]) + abs(q[1] - dn[1]) > 6:
                p.spawn(('bandit', 'carrion_crow', 'bandit', 'tunnel_fence')[len(p.spawns) % 4], *min(near)[1], -2, radius=3, count=3, respawn=170)
        hdeep = erode(hall, 1)
        for tgt in ((45, 12), (55, 12), (45, 24), (55, 24)):
            q = pick(hdeep, tgt)
            p.spawn('bandit', q[0], q[1], -2, radius=2, count=2, respawn=240)
        p.spawn('magpie_king', 52, 18, -2, radius=2, count=1, respawn=3600)
        I.floor_region(p, 'Skarbiec Sroki (głębiny)', -1, (10, 35))
        I.floor_region(p, 'Skarbiec Sroki (głębiny)', -2, (15, 40))
        p.regions.append(('Nora Sroki', 0, 3, 19, 11, 0, 0, 8, 40))
        p.entries.append((4, 6))
        p.snap()
        try:
            p.check()
        except AssertionError:
            continue
        return p
    raise AssertionError('Nora Sroki not built')


def magpie(ctx, cost, helpers):
    world = ctx['world']
    mark = helpers['mark']
    plan = magpie_plan(SEED + 77)
    hh = dict(helpers)
    tx, ty = St.find_site(ctx, hh, cost, (545, 525), 26, 18, radius=170, min_temple=60, max_water=0.1)
    origin = (tx + 3, ty + 3)
    St.clear_surface(ctx, tx - 2, ty - 2, tx + 27, ty + 19)
    W = St.stamp(ctx, plan, origin, mark, per_floor=True)
    g1 = W(38, 18, -2)
    g2 = W(60, 18, -2)
    ctx.setdefault('star_gates', []).append((g1[0], g1[1], -2, 'quest:ferry_m2'))
    ctx['star_gates'].append((g2[0], g2[1], -2, 'boss:magpie_king'))
    return dict(origin=origin, entrance=W(10, 6, 0), gate_hall=g1, gate_vault=g2, brooch=W(66, 18, -2), king=W(52, 18, -2))


# ====================================================================== zleceniodawcy dostepu w miastach
def access_givers(ctx):
    out = {}
    hubs = {'ice_halvar': ('frost_captain', 'Punkt Halvara'), 'desert_idris': ('qas_captain', 'Namiot Idrisa'),
            'tropic_ilvane': ('selv_captain', 'Szklarnia Ilvane'), 'oak_corwin': ('varden_captain', 'Posterunek Corwina')}
    cap = {n[0]: (int(n[4]), int(n[5]), n[3]) for n in ctx['npcs']}
    for gkey, (hub, place) in hubs.items():
        cx, cy, town = cap[hub]
        s = spot_near(ctx, (cx, cy - 3), rad=14, min_npc=5)
        n = D.NPCS[gkey]
        ctx['npcs'].append([gkey, n[0], 'GUIDE', town, s[0], s[1], 0, '-', n[1], n[2][0]])
        ctx['regions_new'].insert(0, (place, s[0] - 4, s[1] - 4, s[0] + 4, s[1] + 4, 0, 0, 1, 1000))
        ctx['pz'].append((s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, 0))
        out[gkey] = s
    return out


# ====================================================================== wejscie
def build(ctx, cost, helpers):
    world = ctx['world']
    S = len(world.floors[0])
    D.ORDER_IDX = {k: i for i, k in enumerate(ORDER)}
    level = I._levels(helpers['monsters_csv'])
    mask = sea_mask(world)
    out = {}
    ctx['avoid_regions'] = True
    n_spawns0 = len(ctx['spawns'])
    out['quay'] = quay(ctx)
    out['magpie'] = magpie(ctx, cost, helpers)
    taken = []
    # kwatera promowego i wyspa szeptow zajmuja morze przy wschodnim wybrzezu
    taken.append((out['quay']['rect'][0], out['quay']['rect'][1], out['quay']['rect'][2] + 14, out['quay']['rect'][3]))
    for key in ORDER:
        p, W, info, origin = build_island(ctx, helpers, key, mask, taken, level, helpers['mark'])
        dock = add_dock(ctx, info, W, key, level)
        info.update(dock=dock, origin=origin, W=W, plan=p)
        out[key] = info
    out['givers'] = access_givers(ctx)
    ctx['avoid_regions'] = False
    ctx['islands5_spawns'] = [tuple(int(v) for v in ln.split(';')[1:4]) for ln in ctx['spawns'][n_spawns0:] if not ln.startswith('#')]
    verify(ctx, out, S)
    ctx['islands5'] = {k: v for k, v in out.items()}
    return summary(out)


def verify(ctx, out, S):
    import realms
    from citadel import _reach
    g = realms.Graph(ctx, S)
    temple = dict(ctx['towns'])['Varden']
    start = g.idx(temple[0], temple[1], 0)
    gate_cells = {(x, y, z) for (x, y, z, _r) in ctx.get('star_gates', [])}
    for key in ORDER:
        info = out[key]
        W = info['W']
        cap = info['dock']['captain']
        assert g.path(start, {g.idx(cap[0], cap[1], 0)}) is None, f'{key}: island reachable on foot from Varden'
        reach = _reach(ctx, [(cap[0], cap[1], 0)], set())
        for sp in info['plan'].spawns:
            a = W(sp[1], sp[2], sp[3])
            assert (a[0], a[1], sp[3]) in reach, f'{key}: spawn {sp} unreachable from the dock'
        for c in info['plan'].chests:
            a = W(c[0], c[1], c[2])
            assert any((a[0] + dx, a[1] + dy, c[2]) in reach for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f'{key}: chest {c}'
        if info['sanctum']:
            sa = info['sanctum']
            sz = sa['z']
            b = W(sa['boss'][0], sa['boss'][1], sz)
            doors = {(W(d[0], d[1], d[2])[0], W(d[0], d[1], d[2])[1], d[2]) for d in info['plan'].doors}
            closed = _reach(ctx, [(cap[0], cap[1], 0)], gate_cells | doors)
            assert (b[0], b[1], sz) not in closed, f'{key}: sanctum open without the door'
    cap = out['quay']['captain']
    assert g.path(start, {g.idx(cap[0], cap[1], 0)}) is not None, 'quay not reachable on foot from Varden'
    m = out['magpie']
    assert g.path(start, {g.idx(m['entrance'][0], m['entrance'][1], 0)}) is not None, 'Nora Sroki not reachable on foot from Varden'


def summary(out):
    res = {}
    for k in ORDER:
        i = out[k]
        res[k] = dict(origin=i['origin'], bbox=(i['origin'][0] + i['bbox'][0], i['origin'][1] + i['bbox'][1],
                                                i['origin'][0] + i['bbox'][2], i['origin'][1] + i['bbox'][3]),
                      captain=i['dock']['captain'], givers=i['dock']['givers'], cells=i['plan_cells'],
                      spawns=len(i['plan'].spawns), camp=i.get('camp_world'))
    res['quay'] = out['quay']
    res['magpie'] = out['magpie']
    res['givers'] = out['givers']
    return res
