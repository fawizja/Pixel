"""Nowe miasta swiata 1000x1000 (2026-10-27, mapa Darka): port Valmir na poludniu i wyspiarskie Nerys.

Miasto to prostokat w murach z czterema bramami: dwie glowne ulice na krzyz z placem i fontanna, ulica wzdluz
murow i cztery kwartaly. Polnocno-zachodni kwartal to swiatynia, w pozostalych stoja bank z depozytem, sklepy,
kuznia, stajnia woznicy, przewodnik, zarzadca domow i domy do wynajecia. Brama od strony morza prowadzi na
nabrzeze z duzym statkiem kapitana. Funkcja build zwraca wszystko, czego potrzebuje generate_continent
(swiatynia, NPC, domy, strefa ochronna, punkty za bramami).
"""
import features as F

NONE = '-'

# nazwa: srodek, szerokosc, wysokosc, strona portu, styl (mur, podloga, ulica, podloze, zielen)
TOWNS = {
    'Valmir': dict(centre=(491, 765), w=48, h=38, port='S', key='valmir', levels=(25, 80),
                   wall='stone_wall', floor='wood_floor', hall='stone_floor', street='cobblestone',
                   ground='grass', trees=['tree', 'bush'], decos=['flowers', 'tall_grass']),
    'Nerys': dict(centre=(866, 738), w=44, h=36, port='S', key='nerys', levels=(60, 200),
                  wall='whitewash_wall', floor='wood_floor', hall='marble_floor', street='brick_paving',
                  ground='grass', trees=['palm', 'bush'], decos=['flowers', 'fern']),
}

# NPC miast: (rola w kluczu, imie, rola, sklep, kolor, powitanie); {T} = nazwa miasta
NPCS = {
    'Valmir': [
        ('priest', 'Kapłanka Mirosława', 'PRIEST', '-', '#f0f0f0', 'Świątynia Valmiru wita żeglarzy i wędrowców.'),
        ('banker', 'Bankier Ludomir', 'BANKER', '-', '#d8c070', 'Bank Valmiru prowadzi konta we wszystkich miastach.'),
        ('housing', 'Zarządczyni Grażyna', 'HOUSING', '-', '#6a5a8a', 'Domy przy porcie. Płacisz z konta w banku.'),
        ('innkeeper', 'Karczmarz Bogdan', 'MERCHANT', 'valmir_supplies', '#a07040', 'Ryby, prowiant i liny dla każdego, kto schodzi do kanałów.'),
        ('smith', 'Kowalka Dobrawa', 'MERCHANT', 'valmir_arms', '#909090', 'Broń i zbroje kute w porcie.'),
        ('mage', 'Czarodziej Wiesław', 'MERCHANT', 'valmir_magic', '#5040c0', 'Runy i mikstury prosto z akademii.'),
        ('merchant', 'Kupiec Zbyszko', 'MERCHANT', 'valmir_shop', '#c0a060', 'Kupuję łupy z kanałów i okolic.'),
        ('carrier', 'Woźnica Valmiru', 'CARRIER', '-', '#8a6a4a', 'Wóz do innych miast czeka. Płacisz monetami.'),
        ('guide', 'Przewodniczka Valmiru', 'GUIDE', '-', '#6080a0', 'Pod Valmirem są kanały pełne szczurów. Dobre na pierwsze polowanie.'),
    ],
    'Nerys': [
        ('priest', 'Kapłan Nerysa', 'PRIEST', '-', '#f0f0f0', 'Świątynia Szmaragdowych Wysp wita podróżnych.'),
        ('banker', 'Bankierka Selma', 'BANKER', '-', '#d8c070', 'Bank Nerys prowadzi konta we wszystkich miastach.'),
        ('housing', 'Zarządca Ilian', 'HOUSING', '-', '#6a5a8a', 'Wille wśród palm. Płacisz z konta w banku.'),
        ('innkeeper', 'Karczmarka Lirena', 'MERCHANT', 'nerys_supplies', '#a07040', 'Owoce, prowiant i pochodnie na wyspy.'),
        ('smith', 'Płatnerz Corvin', 'MERCHANT', 'nerys_arms', '#909090', 'Broń i zbroje z całego świata.'),
        ('mage', 'Czarodziejka Ysolde', 'MERCHANT', 'nerys_magic', '#5040c0', 'Runy i mikstury dla odkrywców wysp.'),
        ('merchant', 'Kupiec Nerys', 'MERCHANT', 'nerys_shop', '#c0a060', 'Skup łupów z wysp i dżungli.'),
        ('carrier', 'Woźnica Nerys', 'CARRIER', '-', '#8a6a4a', 'Promem i wozem do innych miast. Płacisz monetami.'),
        ('guide', 'Przewodnik Nerys', 'GUIDE', '-', '#6080a0', 'Wyspy Szmaragdowe kryją wiele tajemnic. Pomożesz?'),
    ],
}
CAPTAINS = {'Valmir': ('valmir_captain', 'Kapitan Radomir'), 'Nerys': ('nerys_captain', 'Kapitanka Maren')}

# meble budynkow wg roli (przy scianach)
FURNITURE = {
    'banker': ['depot_locker', 'depot_locker', 'counter', 'chest', 'potted_plant', 'bookcase'],
    'housing': ['bookcase', 'table', 'chair', 'lectern', 'potted_plant'],
    'innkeeper': ['barrel', 'table', 'chair', 'table', 'chair', 'barrel', 'counter'],
    'smith': ['anvil', 'forge', 'barrel', 'showcase', 'crate'],
    'mage': ['bookcase', 'bookcase', 'lectern', 'showcase', 'potted_plant'],
    'merchant': ['counter', 'crate', 'barrel', 'sack', 'showcase'],
    'carrier': ['hay_bale', 'hay_bale', 'barrel', 'crate'],
    'guide': ['table', 'chair', 'bookcase', 'banner'],
}


def build(world, name, rng):
    spec = TOWNS[name]
    W, H = spec['w'], spec['h']
    cx0, cy0 = spec['centre']
    x0, y0 = cx0 - W // 2, cy0 - H // 2
    cx, cy = W // 2, H // 2
    wall, street, ground = spec['wall'], spec['street'], spec['ground']

    def put(x, y, cell):
        world.put(x0 + x, y0 + y, 0, cell)

    # plac budowy: podloze, ulice, mury
    for y in range(H):
        for x in range(W):
            put(x, y, (ground, NONE, NONE, NONE))
    for y in range(H):
        for x in range(W):
            ring = x in (1, 2, W - 3, W - 2) or y in (1, 2, H - 3, H - 2)
            main = cx - 1 <= x <= cx + 1 or cy - 1 <= y <= cy + 1
            plaza = abs(x - cx) <= 3 and abs(y - cy) <= 3
            if (ring or main or plaza) and 0 < x < W - 1 and 0 < y < H - 1:
                put(x, y, (street, NONE, NONE, NONE))
            if x in (0, W - 1) or y in (0, H - 1):
                put(x, y, (ground, NONE, NONE, wall))
    gates = {}
    for side, cells, out in (('N', [(cx + d, 0) for d in (-1, 0, 1)], (cx, -1)),
                             ('S', [(cx + d, H - 1) for d in (-1, 0, 1)], (cx, H)),
                             ('W', [(0, cy + d) for d in (-1, 0, 1)], (-1, cy)),
                             ('E', [(W - 1, cy + d) for d in (-1, 0, 1)], (W, cy))):
        for (x, y) in cells:
            put(x, y, (street, NONE, NONE, NONE))
        gates[side] = (x0 + out[0], y0 + out[1])
        # droga tuz za brama (2 pola), zeby generator drog mial gdzie dolaczyc
        for k in (1, 2):
            ox, oy = out[0] + (out[0] - cells[1][0]) * (k - 1), out[1] + (out[1] - cells[1][1]) * (k - 1)
            for d in (-1, 0, 1):
                if side in 'NS':
                    world.put(x0 + ox + d, y0 + oy, 0, ('dirt', NONE, NONE, NONE))
                else:
                    world.put(x0 + ox, y0 + oy + d, 0, ('dirt', NONE, NONE, NONE))
    # fontanna na placu i latarnie przy niej
    put(cx, cy, (street, NONE, 'fountain', NONE))
    for (dx, dy) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        put(cx + dx, cy + dy, (street, NONE, 'street_lamp', NONE))

    # kwartaly: (x od, x do, y od, y do) wnetrza kwartalu w ukladzie lokalnym
    qx = [(3, cx - 4), (cx + 4, W - 4)]
    qy = [(3, cy - 4), (cy + 4, H - 4)]
    npcs, houses = [], []
    roles = [r[0] for r in NPCS[name]]
    lots = []
    for j, (ya, yb) in enumerate(qy):
        for i, (xa, xb) in enumerate(qx):
            if i == 0 and j == 0:
                continue                          # swiatynia
            mid = (xa + xb) // 2
            assert yb - ya >= 10, f'{name}: quarter too low'
            rows = [(ya, ya + 5), (max(ya + 6, yb - 5), yb)]
            for r, (ra, rb) in enumerate(rows):
                for (ca, cb) in ((xa, mid), (mid + 2, xb)):
                    side = 'N' if r == 0 else 'S'
                    lots.append((ca, ra, cb, rb, side))
    # swiatynia: caly kwartal polnocno-zachodni, drzwi na poludnie (do glownej ulicy)
    (ta, tb), (tc, td) = qx[0], qy[0]
    hall = spec['hall']
    inner, door = F.house(world, x0 + ta, y0 + tc, tb - ta + 1, td - tc + 1, wall, hall, 'S', rng, furniture=False)
    altar = (door[0], y0 + tc + 2)
    world.put(altar[0], altar[1], 0, (hall, 'carpet', 'altar', NONE))
    for y in range(altar[1] + 1, door[1]):
        world.put(altar[0], y, 0, (hall, 'carpet', NONE, NONE))
    for (dx, dy) in ((-3, 0), (3, 0), (-3, 3), (3, 3), (-6, 5), (6, 5)):
        c = world.get(altar[0] + dx, altar[1] + dy)
        if c is not None and c[3] == NONE:
            world.put(altar[0] + dx, altar[1] + dy, 0, (hall, NONE, 'candle', NONE))
    for (dx, dy) in ((-2, 6), (2, 6), (-2, 8), (2, 8)):
        world.put(altar[0] + dx, altar[1] + dy, 0, (hall, NONE, 'bench', NONE))
    temple = (altar[0], altar[1] + 1)
    key = spec['key']
    priest = NPCS[name][0]
    npcs.append([f'{key}_priest', priest[1], priest[2], name, altar[0] + 1, altar[1] + 1, 0, priest[3], priest[4], priest[5]])

    # pozostale budynki: NPC po kolei, reszta to domy do wynajecia
    others = [r for r in NPCS[name][1:]]
    lots.sort(key=lambda lot: (lot[4], lot[1], lot[0]))
    for k, (ca, ra, cb, rb, side) in enumerate(lots):
        bw, bh = cb - ca + 1, rb - ra + 1
        if k < len(others):
            role = others[k]
            floor = spec['floor'] if role[0] not in ('smith', 'carrier') else 'stone_floor'
            inner, door = F.house(world, x0 + ca, y0 + ra, bw, bh, wall, floor, side, rng, furniture=False)
            furnish(world, inner, door, FURNITURE.get(role[0], []), floor)
            spot = npc_spot(world, inner, door)
            npcs.append([f'{key}_{role[0]}', role[1], role[2], name, spot[0], spot[1], 0, role[3], role[4], role[5]])
        else:
            inner, door = F.house(world, x0 + ca, y0 + ra, bw, bh, wall, spec['floor'], side, rng, furniture=True)
            xs = [p[0] for p in inner]
            ys = [p[1] for p in inner]
            n = len(houses) + 1
            area = len(inner)
            houses.append([f'{key}_{n}', f'{"Port" if name == "Valmir" else "Willa"} {n}', name,
                           min(xs), min(ys), max(xs), max(ys), 0, door[0], door[1], area * 5000])
        # drozka od drzwi do ulicy
        dx_, dy_ = door
        step = -1 if side == 'N' else 1
        y = dy_ + step
        while 0 < y - y0 < H - 1:
            c = world.get(dx_, y)
            if c[0] == street:
                break
            world.put(dx_, y, 0, (street, NONE, NONE, NONE))
            y += step
    assert len(lots) >= len(others), f'{name}: too few lots'
    # zielen na podworkach: drzewa z wolnymi sasiadami, kwiaty
    for y in range(1, H - 1):
        for x in range(1, W - 1):
            c = world.get(x0 + x, y0 + y)
            if c[0] != ground or c[2] != NONE or c[3] != NONE:
                continue
            r = rng.random()
            free = all(F.walk(world.get(x0 + x + a, y0 + y + b)) and world.get(x0 + x + a, y0 + y + b)[2] == NONE
                       for a in (-1, 0, 1) for b in (-1, 0, 1))
            if r < 0.06 and free:
                put(x, y, (ground, NONE, rng.choice(spec['trees']), NONE))
            elif r < 0.2:
                put(x, y, (ground, rng.choice(spec['decos']), NONE, NONE))
    rect = (x0, y0, x0 + W - 1, y0 + H - 1)
    return {'name': name, 'rect': rect, 'temple': temple, 'npcs': npcs, 'houses': houses, 'gates': gates,
            'pz': (x0 + 1, y0 + 1, x0 + W - 2, y0 + H - 2)}


def furnish(world, inner, door, items, floor):
    spots = [p for p in sorted(inner) if abs(p[0] - door[0]) + abs(p[1] - door[1]) > 2
             and any(world.get(p[0] + a, p[1] + b)[3] != NONE for a, b in F.DIRS4)]
    step = max(1, len(spots) // max(1, len(items)))
    for i, item in enumerate(items):
        if i * step >= len(spots):
            break
        x, y = spots[i * step]
        world.put(x, y, 0, (floor, NONE, item, NONE))


def npc_spot(world, inner, door):
    """Wolne pole wnetrza z dala od drzwi, ktore nie zamyka przejscia (ma co najmniej 2 wolnych sasiadow)."""
    best = None
    for (x, y) in inner:
        c = world.get(x, y)
        if c[2] != NONE:
            continue
        free = sum(1 for a, b in F.DIRS4 if F.walk(world.get(x + a, y + b)) and world.get(x + a, y + b)[2] == NONE)
        if free < 3:
            continue
        d = abs(x - door[0]) + abs(y - door[1])
        if best is None or d > best[0]:
            best = (d, x, y)
    assert best is not None
    return best[1], best[2]
