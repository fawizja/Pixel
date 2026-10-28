"""Nowe miasta swiata 1000x1000 (2026-10-27, mapa Darka): port Valmir na poludniu i wyspiarskie Nerys.

Miasto to prostokat w murach z czterema bramami: dwie glowne ulice na krzyz z placem i fontanna, ulica wzdluz
murow i cztery kwartaly. Polnocno-zachodni kwartal to swiatynia, w pozostalych stoja bank z depozytem, sklepy,
kuznia, stajnia woznicy, przewodnik, zarzadca domow i domy do wynajecia. Brama od strony morza prowadzi na
nabrzeze z duzym statkiem kapitana. Funkcja build zwraca wszystko, czego potrzebuje generate_continent
(swiatynia, NPC, domy, strefa ochronna, punkty za bramami).
"""
import features as F
import organic

NONE = '-'

# nazwa: srodek, szerokosc, wysokosc, strona portu, styl (mur, podloga, ulica, podloze, zielen)
TOWNS = {
    'Valmir': dict(centre=(491, 765), w=48, h=38, port='S', key='valmir', levels=(25, 80),
                   wall='stone_wall', floor='wood_floor', hall='stone_floor', street='cobblestone',
                   ground='grass', trees=['tree', 'bush', 'autumn_tree'], decos=['flowers', 'tall_grass']),
    'Nerys': dict(centre=(866, 738), w=44, h=36, port='S', key='nerys', levels=(60, 200),
                  wall='whitewash_wall', floor='wood_floor', hall='marble_floor', street='brick_paving',
                  ground='grass', trees=['palm', 'jungle_bush', 'jungle_tree'], decos=['flowers', 'fern', 'jungle_plant']),
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
    """Miasto o nieregularnym ksztalcie (organic.py): mur po falujacym obrysie, krete ulice, budynki przy ulicach."""
    spec = dict(TOWNS[name])
    spec['name'] = name
    spec['rx'], spec['ry'] = spec['w'] * 0.72, spec['h'] * 0.72
    spec['gates'] = 'NSWE'
    spec['house_label'] = 'Port' if name == 'Valmir' else 'Willa'
    roles = [dict(key=f"{spec['key']}_{r[0]}", name=r[1], role=r[2], shop=r[3], color=r[4], greet=r[5], kind=r[0],
                  central=r[0] in ('banker', 'innkeeper')) for r in NPCS[name]]
    return organic.build(world, spec, rng, roles, houses=12)


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
