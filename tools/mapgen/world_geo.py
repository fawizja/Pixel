"""Geografia swiata (etap 4 rozbudowy w stylu Tibii): morze, wybrzeza, wyspy i lancuchy gor.

Stary swiat (zachodni kontynent 0..159, ciesnina, wschodni kontynent 168..255, wysokosc 0..231) zostaje
w tych samych wspolrzednych; mapa rosnie na wschod i poludnie. Zachodnie i polnocne krawedzie starego
kontynentu staja sie wybrzezem, na poludniu i wschodzie jest morze z wyspami. Gory to nieprzechodnie skaly
(sciany jak w Tibii), z przeleczami tam, gdzie biegna drogi.

Wszystko losowane wlasnym ziarnem (rnd_geo), wiec stary swiat losowany wczesniej nie zmienia sie.
"""
import math
import random

OLD_W, OLD_H = 160, 232     # zachodni kontynent
OLD_EAST_W_ALL = 256        # oba kontynenty (0..255)
rnd_geo = random.Random(20261022)

# symbole legendy (wolne litery greckie)
LEGEND = [
    ('ω', 'snow', '-', '-', 'snow_mountain'),      # omega: osniezona skala
    ('Ω', 'grass', '-', '-', 'mountain'),           # Omega: szara skala
    ('Ψ', 'sand', '-', '-', 'sand_cliff'),          # Psi: piaskowe urwisko
    ('Φ', 'ash', '-', '-', 'mountain'),             # Phi: skala wulkaniczna
    ('χ', 'jungle_grass', '-', '-', 'mountain'),    # chi: skala w dzungli
    ('υ', 'sand', 'shells', '-', '-'),              # upsilon: plaza z muszlami
]
MOUNTAIN = set('ωΩΨΦχ')

# naturalny teren (tylko on moze zostac nadpisany gorami i wybrzezem)
NATURAL = set('Ssvn' + 'T,.f' + 'JhUP' + 'yoKQ' + 'απβγδζηρστ' + 'pi')


def _noise1(seed, n, lo, hi, step=1):
    """Poszarpana linia: n wartosci w [lo, hi] zmieniajacych sie o +-step."""
    r = random.Random(seed)
    v = (lo + hi) // 2
    out = []
    for _ in range(n):
        v = max(lo, min(hi, v + r.randint(-step, step)))
        out.append(v)
    return out


def _biome_of(y, x):
    if x >= 168 or (x >= OLD_W and y >= OLD_H):
        return 'east' if y > 171 else ('east_north' if y <= 63 else ('east_temperate' if y <= 111 else 'east_jungle'))
    if y <= 63:
        return 'north'
    if y <= 111:
        return 'temperate'
    if y <= 171:
        return 'jungle'
    return 'desert'


def _shore(biome):
    """Pole tuz przy wodzie: plaza albo snieg."""
    return 's' if biome == 'north' else 'y'


def _protected(protect, x, y):
    return any(a <= x <= c and b <= y <= d for a, b, c, d in protect)


def _coastline(seed, n, lo, hi):
    """Gladka, poszarpana linia brzegu: suma fal o roznych dlugosciach + drobny szum (zatoki i cyple)."""
    r = random.Random(seed)
    waves = [(r.uniform(0, 6.28), r.uniform(0.015, 0.03), 0.5), (r.uniform(0, 6.28), r.uniform(0.05, 0.09), 0.3),
             (r.uniform(0, 6.28), r.uniform(0.15, 0.25), 0.2)]
    mid, amp = (lo + hi) / 2, (hi - lo) / 2
    out, jitter = [], 0
    for i in range(n):
        v = sum(a * math.sin(ph + f * i) for ph, f, a in waves)
        jitter = max(-1, min(1, jitter + r.choice((-1, 0, 0, 1))))
        out.append(int(round(mid + amp * v)) + jitter)
    return out


def _new_land(biome, r):
    """Teren nowego ladu za starym brzegiem (przedluzenie biomu)."""
    v = r.random()
    if biome == 'east_north':
        return 'S' if v < 0.06 else ('i' if v < 0.1 else ('v' if v < 0.2 else 's'))
    if biome == 'east_temperate':
        return '\u03b2' if v < 0.08 else ('T' if v < 0.11 else ('\u03c0' if v < 0.26 else '\u03b1'))
    if biome == 'east_jungle':
        return '\u03b4' if v < 0.07 else '\u03b3'
    if biome == 'east':
        return '\u03c1' if v < 0.05 else ('\u03b7' if v < 0.12 else '\u03b6')
    if biome == 'desert':
        return 'K' if v < 0.04 else ('Q' if v < 0.06 else ('o' if v < 0.16 else 'y'))
    if biome == 'jungle':
        return 'P' if v < 0.1 else ('h' if v < 0.3 else 'J')
    if biome == 'temperate':
        return 'T' if v < 0.1 else (',' if v < 0.25 else '.')
    return 'S' if v < 0.08 else ('v' if v < 0.2 else 's')


def _beachify(g, protect, W, H):
    """Pola naturalnego ladu przy wodzie staja sie plaza (snieg na polnocy), skaly i drzewa przy brzegu znikaja."""
    shore = []
    for y in range(H):
        for x in range(W):
            if g[y][x] not in NATURAL or _protected(protect, x, y):
                continue
            if any(0 <= x + dx < W and 0 <= y + dy < H and g[y + dy][x + dx] == '~'
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                shore.append((x, y))
    for (x, y) in shore:
        b = _biome_of(y, x)
        if b in ('north', 'east_north'):
            g[y][x] = 's'
        elif b.startswith('east'):
            g[y][x] = '\u03b6'
        else:
            g[y][x] = 'y' if rnd_geo.random() < 0.85 else '\u03c5'


def coasts(g, W, H, protect):
    """Brzegi z zatokami i cyplami: zachod i polnoc starego kontynentu, poludnie i wschod wchodza w nowe morze."""
    r = random.Random(14)
    for y in range(H):
        for x in range(W):
            if x >= OLD_EAST_W_ALL or y >= OLD_H:
                g[y][x] = '~'

    def set_land(x, y):
        if g[y][x] == '~':
            g[y][x] = _new_land(_biome_of(min(y, OLD_H - 1), x), r)

    def set_sea(x, y):
        if g[y][x] in NATURAL and not _protected(protect, x, y):
            g[y][x] = '~'

    west = _coastline(11, OLD_H, 1, 15)
    for y in range(OLD_H):
        for x in range(0, max(0, west[y])):
            set_sea(x, y)
    north = _coastline(12, OLD_W, 0, 9)
    for x in range(OLD_W):
        if 16 <= x <= 64:      # pod Mroznymi Turniami (+1..+5) zostaje skalny brzeg
            for y in range(0, 2):
                if g[y][x] in NATURAL:
                    g[y][x] = '\u03c9'
            continue
        for y in range(0, max(0, north[x])):
            set_sea(x, y)
    # poludnie obu kontynentow: lad wychodzi w morze albo morze wcina sie w zatoki
    south = _coastline(13, OLD_EAST_W_ALL, OLD_H - 10, OLD_H + 16)
    for x in range(OLD_EAST_W_ALL):
        if 160 <= x <= 167:
            continue  # ciesnina
        line = south[x]
        for y in range(min(line, OLD_H) + 1, OLD_H):
            set_sea(x, y)
        for y in range(OLD_H, min(H, line + 1)):
            set_land(x, y)
    # wschodni brzeg wschodniego kontynentu
    east = _coastline(15, OLD_H + 16, OLD_EAST_W_ALL - 9, OLD_EAST_W_ALL + 18)
    for y in range(min(H, OLD_H + 16)):
        line = east[y]
        for x in range(line + 1, OLD_EAST_W_ALL):
            if y < OLD_H:
                set_sea(x, y)
        for x in range(OLD_EAST_W_ALL, min(W, line + 1)):
            if y < OLD_H or y <= south[min(OLD_EAST_W_ALL - 1, x)]:
                set_land(x, y)
    # brzegi ciesniny: zatoczki po obu stronach
    strait = _coastline(16, OLD_H, -3, 4)
    for y in range(OLD_H):
        for x in range(160 - max(0, strait[y]), 160):
            set_sea(x, y)
        for x in range(168, 168 + max(0, -strait[y])):
            set_sea(x, y)
    # stary pas skal na krawedziach (las, skaly) - przerwy, zeby dalo sie wejsc na plaze
    for y in range(OLD_H):
        for x in range(OLD_EAST_W_ALL):
            if g[y][x] in 'QKUPTSn\u03c1\u03b2\u03b4' and not _protected(protect, x, y) \
                    and (x < 12 or y < 8 or y > OLD_H - 14 or x > OLD_EAST_W_ALL - 14 or 150 <= x <= 175) \
                    and rnd_geo.random() < 0.6:
                b = _biome_of(y, x)
                g[y][x] = {'north': 's', 'temperate': '.', 'jungle': 'J', 'east': '\u03b6', 'east_north': 's',
                           'east_temperate': '\u03b1', 'east_jungle': '\u03b3'}.get(b, 'y')
    _beachify(g, protect, W, H)


def warp_biomes(g, protect):
    """Granice pasow biomow (proste linie y=63/111/171) staja sie faliste: teren w pasie +-12 pol jest losowany
    od nowa wg przesunietej granicy. Zmieniany jest tylko naturalny teren zachodniego kontynentu."""
    r = random.Random(21)
    for bound, seed in ((63, 31), (111, 32), (171, 33)):
        line = _coastline(seed, OLD_EAST_W_ALL, bound - 11, bound + 11)
        for x in range(OLD_EAST_W_ALL):
            if 160 <= x <= 167:
                continue
            for y in range(bound - 13, bound + 14):
                if not (0 <= y < OLD_H) or g[y][x] not in NATURAL or _protected(protect, x, y):
                    continue
                want = _biome_of(y - (line[x] - bound), x)
                have = _biome_of(y, x)
                if want != have:
                    g[y][x] = _new_land(want, r)


def forest(g, cx, cy, rx, ry, protect, seed, tree):
    """Gesty las (kepy drzew z przejsciami)."""
    r = random.Random(seed)
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if not (0 <= y < len(g) and 0 <= x < len(g[0])) or g[y][x] not in NATURAL or _protected(protect, x, y):
                continue
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d <= 1 and r.random() < 0.55 * (1.2 - d):
                g[y][x] = tree


def river(g, points, protect, seed):
    """Rzeka szerokosci 2-3; drogi przecina mostem."""
    r = random.Random(seed)
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        steps = max(abs(bx - ax), abs(by - ay))
        wob = 0.0
        for i in range(steps + 1):
            t = i / max(1, steps)
            wob = max(-2.0, min(2.0, wob + r.uniform(-0.5, 0.5)))
            cx = ax + (bx - ax) * t + (wob if abs(by - ay) > abs(bx - ax) else 0)
            cy = ay + (by - ay) * t + (wob if abs(bx - ax) >= abs(by - ay) else 0)
            for dy in range(-1, 2):
                for dx in range(-1, 2):
                    x, y = int(round(cx)) + dx, int(round(cy)) + dy
                    if abs(dx) + abs(dy) > 1 or not (0 <= y < len(g) and 0 <= x < len(g[0])):
                        continue
                    if _protected(protect, x, y):
                        continue
                    if g[y][x] in ':=':
                        g[y][x] = 'b'
                    elif g[y][x] in NATURAL or g[y][x] in MOUNTAIN:
                        g[y][x] = '~'


def ridge(g, points, width, ch, protect, seed):
    """Lancuch gor wzdluz lamanej; nadpisuje tylko naturalny teren (drogi, miasta, woda zostaja = przelecze)."""
    r = random.Random(seed)
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        steps = max(abs(bx - ax), abs(by - ay))
        for i in range(steps + 1):
            t = i / max(1, steps)
            cx = ax + (bx - ax) * t + r.uniform(-1.2, 1.2)
            cy = ay + (by - ay) * t + r.uniform(-1.2, 1.2)
            rad = width / 2 + r.uniform(-0.6, 1.1)
            for y in range(int(cy - rad) - 1, int(cy + rad) + 2):
                for x in range(int(cx - rad) - 1, int(cx + rad) + 2):
                    if not (0 <= y < len(g) and 0 <= x < len(g[0])):
                        continue
                    if (x - cx) ** 2 + (y - cy) ** 2 > rad * rad:
                        continue
                    if g[y][x] in NATURAL and not _protected(protect, x, y):
                        g[y][x] = ch


def island(g, cx, cy, rx, ry, kind, seed):
    """Wyspa z kilku elips; wnetrze wg rodzaju, brzegi z plaza. Zwraca liste pol ladu."""
    r = random.Random(seed)
    blobs = [(cx, cy, rx, ry)]
    for _ in range(4):
        blobs.append((cx + r.randint(-rx // 2, rx // 2), cy + r.randint(-ry // 2, ry // 2),
                      max(3, rx // 2 + r.randint(-2, 3)), max(3, ry // 2 + r.randint(-2, 3))))
    land = []
    H, W = len(g), len(g[0])
    for y in range(cy - ry - rx, cy + ry + rx):
        for x in range(cx - rx - ry, cx + rx + ry):
            if not (0 <= x < W and 0 <= y < H) or g[y][x] != '~':
                continue
            inside = False
            for (bx, by, brx, bry) in blobs:
                wob = 1.0 + 0.12 * math.sin(x * 0.7 + seed) * math.cos(y * 0.6 + seed)
                if ((x - bx) / brx) ** 2 + ((y - by) / bry) ** 2 <= wob:
                    inside = True
                    break
            if inside:
                land.append((x, y))
    land_set = set(land)
    for (x, y) in land:
        coast = any((x + dx, y + dy) not in land_set for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        v = r.random()
        if coast:
            g[y][x] = {'ice': 's', 'volcano': 'γ'}.get(kind, 'y' if v < 0.85 else 'υ')
        elif kind == 'tropic':
            g[y][x] = 'P' if v < 0.10 else ('U' if v < 0.16 else ('h' if v < 0.36 else 'J'))
        elif kind == 'pirate':
            g[y][x] = 'p' if v < 0.06 else ('Q' if v < 0.09 else ('o' if v < 0.25 else 'y'))
        elif kind == 'ice':
            g[y][x] = 'S' if v < 0.08 else ('n' if v < 0.12 else ('v' if v < 0.3 else 's'))
        elif kind == 'volcano':
            g[y][x] = 'δ' if v < 0.08 else 'γ'
        elif kind == 'mist':
            g[y][x] = 'β' if v < 0.10 else ('π' if v < 0.30 else 'α')
        else:  # 'green' - lagodna wyspa
            g[y][x] = 'T' if v < 0.10 else (',' if v < 0.25 else ('f' if v < 0.28 else '.'))
    return land


# wyspy: nazwa, srodek, promienie, rodzaj, (poziom od, do)
ISLANDS = [
    ('Wyspa Koralowa', (120, 268), (22, 12), 'tropic', (300, 380)),
    ('Wyspa Piratow', (48, 282), (20, 14), 'pirate', (320, 420)),
    ('Wyspa Mgiel', (250, 285), (24, 16), 'mist', (480, 650)),
    ('Wyspa Latarni', (286, 112), (9, 7), 'green', (300, 340)),
    ('Lodowa Wyspa', (310, 38), (22, 16), 'ice', (420, 560)),
    ('Wyspa Wulkaniczna', (314, 190), (20, 18), 'volcano', (600, 800)),
    ('Skaly Mewie', (190, 250), (6, 4), 'green', (300, 340)),
]


def geography(floors, W, H, protect):
    """Wybrzeza, gory i wyspy na pietrze 0. Zwraca (regiony, opis wysp) do map.properties."""
    g = floors[0]
    warp_biomes(g, protect)
    coasts(g, W, H, protect)
    # gory polnocy (wokol Frostholm - przyszle kamienne miasto gorskie): masywy z przeleczami na drogach
    ridge(g, [(4, 42), (18, 46), (32, 44), (50, 40), (58, 30), (60, 20)], 5, '\u03c9', protect, 101)
    ridge(g, [(100, 4), (106, 18), (110, 30), (104, 50)], 6, '\u03c9', protect, 102)
    ridge(g, [(122, 6), (138, 8), (152, 14), (150, 36)], 5, '\u03c9', protect, 103)
    ridge(g, [(64, 48), (72, 54), (76, 52)], 4, '\u03c9', protect, 104)
    ridge(g, [(84, 52), (90, 56), (98, 48)], 4, '\u03c9', protect, 105)
    ridge(g, [(180, 6), (196, 12), (204, 4)], 5, '\u03c9', protect, 114)
    ridge(g, [(232, 20), (246, 34), (240, 52)], 5, '\u03c9', protect, 115)
    # Smoczy Grzbiet miedzy krainami Varden a dzungla
    ridge(g, [(6, 100), (20, 108), (34, 112), (44, 106)], 5, '\u03a9', protect, 106)
    ridge(g, [(114, 102), (126, 110), (140, 112), (154, 104)], 5, '\u03a9', protect, 107)
    # skaly w dzungli i kaniony pustyni
    ridge(g, [(8, 136), (20, 148), (14, 166)], 5, '\u03c7', protect, 108)
    ridge(g, [(84, 160), (92, 168)], 4, '\u03c7', protect, 116)
    ridge(g, [(98, 176), (110, 182), (124, 184), (148, 178)], 4, '\u03a8', protect, 109)
    ridge(g, [(4, 176), (8, 186), (6, 196)], 5, '\u03a8', protect, 110)
    ridge(g, [(100, 212), (104, 224)], 3, '\u03a8', protect, 111)
    ridge(g, [(36, 214), (50, 222)], 4, '\u03a8', protect, 117)
    # wschod: wulkaniczne pasma Spalonych Wyzyn
    ridge(g, [(182, 118), (196, 128), (206, 122)], 5, '\u03a6', protect, 112)
    ridge(g, [(232, 132), (246, 150), (240, 168)], 5, '\u03a6', protect, 113)
    # lasy: Puszcza Varden, Bory Polnocy, Gaszcz Selvaran
    forest(g, 22, 76, 14, 9, protect, 121, 'T')
    forest(g, 140, 92, 10, 7, protect, 122, 'T')
    forest(g, 30, 22, 10, 6, protect, 123, 'S')
    forest(g, 130, 48, 10, 6, protect, 124, 'S')
    forest(g, 40, 132, 14, 8, protect, 125, 'U')
    # rzeki: z gor polnocy przez dzungle do morza poludniowego, oraz na wschodzie
    river(g, [(16, 48), (14, 70), (8, 92), (2, 98)], protect, 131)
    river(g, [(96, 168), (92, 182), (90, 200), (92, 226), (94, 236)], protect, 132)
    river(g, [(200, 66), (214, 80), (232, 92), (256, 100)], protect, 133)
    regions = []
    for i, (name, (cx, cy), (rx, ry), kind, (lo, hi)) in enumerate(ISLANDS):
        land = island(g, cx, cy, rx, ry, kind, 200 + i)
        xs = [p[0] for p in land]
        ys = [p[1] for p in land]
        regions.append(f'{name};{min(xs)};{min(ys)};{max(xs)};{max(ys)};-5;5;{lo};{hi}')
    regions += [
        f'Morze Poludniowe;0;{OLD_H + 17};{W - 1};{H - 1};0;0;300;1000',
        f'Morze Wschodnie;{OLD_EAST_W_ALL + 19};0;{W - 1};{OLD_H - 1};0;0;300;1000',
    ]
    return regions
