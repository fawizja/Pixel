"""Cztery rozne miasta (etap 5): kazde ma wlasny charakter, uklad i wyposazenie budynkow.

    Frostholm - kamienne miasto gorskie: dolne miasto, tarasy na pietrze +1, przepasc z mostem, kopalnia (-1)
    Qasirah   - egzotyczne miasto pustyni i dzungli: ogrody z kanalem, palac, oaza, bazar, ruiny, piramida
    Selvaran  - miasto portowe: nabrzeze, molo, statki, magazyny, stocznia, kanal z mostem (dzielnica portu)
    Varden    - miasto krolewskie: rdzen z maps/varden + dzielnica krolewska (zamek, rynek, biblioteka, gildia magow)

Kazda funkcja rysuje miasto na siatkach pieter (listy wierszy znakow legendy) i zwraca slownik:
temple, protect (prostokaty, ktorych geografia nie rusza), pz, transitions, regions.
Wszystkie wspolrzedne sa wspolrzednymi swiata. Losowosc tylko z wlasnych ziaren (rnd generatora bez zmian).
"""
import random

# symbole legendy (podloze;dekoracja;obiekt;sciana) dla wyposazenia miast
LEGEND = [
    ('\u0431', 'wood_floor', '-', 'bed', '-'),            # б
    ('\u0432', 'wood_floor', '-', 'bookcase', '-'),       # в
    ('\u0433', 'wood_floor', '-', 'barrel', '-'),         # г
    ('\u0434', 'wood_floor', '-', 'table', '-'),          # д
    ('\u0435', 'stone_floor', '-', 'statue', '-'),        # е
    ('\u0436', 'sandstone_floor', '-', 'sand_statue', '-'),   # ж
    ('\u0437', 'jungle_grass', '-', 'jungle_statue', '-'),    # з
    ('\u0438', 'bridge', '-', 'cannon', '-'),             # и
    ('\u0439', 'wood_floor', '-', 'chest', '-'),          # й
    ('\u043a', 'dirt', '-', 'lumber', '-'),               # к
    ('\u043b', 'stone_floor', '-', 'counter', '-'),       # л
    ('\u043c', 'bridge', '-', 'crate', '-'),              # м
    ('\u043d', 'cobblestone', '-', 'crate', '-'),         # н
    ('\u043e', 'cobblestone', '-', 'barrel', '-'),        # о
    ('\u043f', 'sandstone_floor', '-', 'counter', '-'),   # п
    ('\u0440', 'stone_floor', '-', 'bookcase', '-'),      # р
    ('\u0441', 'stone_floor', '-', 'table', '-'),         # с
    ('\u0442', 'stone_floor', '-', 'chest', '-'),         # т
    ('\u0443', 'cobblestone', '-', 'statue', '-'),        # у
    ('\u0444', 'sandstone_floor', '-', 'palm', '-'),      # ф
    ('\u0445', 'jungle_grass', 'flowers', '-', '-'),      # х
    ('\u0446', 'sandstone_floor', '-', 'door_open', '-'), # ц
    ('\u0447', 'bridge', '-', 'barrel', '-'),             # ч
    ('\u0448', 'stone_floor', '-', 'campfire', '-'),      # ш (palenisko kuzni)
    ('\u0449', 'sandstone_floor', 'rug', '-', '-'),       # щ
    ('\u044a', 'sandstone_floor', '-', 'wall_torch', '-'),    # ъ
    ('\u044b', 'sandstone_floor', '-', 'street_lamp', '-'),   # ы
    ('\u044c', 'sandstone_floor', '-', 'fountain', '-'),  # ь
    ('\u044d', 'wood_floor', 'rug', '-', '-'),            # э
    ('\u044e', 'sandstone_floor', '-', 'altar', '-'),     # ю
    ('\u044f', 'sandstone_floor', '-', 'table', '-'),     # я
    ('\u0411', 'bridge', '-', '-', 'wood_wall'),          # Б burta statku
    ('\u0412', 'bridge', '-', 'street_lamp', '-'),        # В latarnia na maszcie
    ('\u0413', 'cobblestone', '-', 'cannon', '-'),        # Г
    ('\u0414', 'sand', '-', 'crate', '-'),                # Д
    ('\u0415', 'sand', '-', 'barrel', '-'),               # Е
    ('\u0416', 'sandstone_floor', '-', 'bookcase', '-'),  # Ж
    ('\u0417', 'sandstone_floor', '-', 'chest', '-'),     # З
    ('\u0418', 'grass', '-', 'statue', '-'),              # И
    ('\u0419', 'stone_floor', '-', 'barrel', '-'),        # Й
    ('\u041a', 'stone_floor', '-', 'crate', '-'),         # К
    ('\u041b', 'sandstone_floor', '-', 'barrel', '-'),    # Л
    ('\u041c', 'sandstone_floor', '-', 'crate', '-'),     # М
    ('\u041d', 'stone_floor', '-', 'bed', '-'),           # Н
    ('\u041f', 'sand', '-', 'rubble', '-'),               # П gruz ruin
    ('\u0420', 'dirt', '-', 'crate', '-'),                # Р
    ('\u0421', 'stone_floor', 'rug', 'statue', '-'),      # С
    ('\u0422', 'sandstone_floor', '-', 'stairs_down', '-'),   # Т
    ('\u0423', 'cobblestone', '-', 'stairs_up', '-'),     # У
    ('\u0424', 'cobblestone', '-', 'stairs_down', '-'),   # Ф
    ('\u0425', 'grass', '-', 'fence', '-'),               # Х
    ('\u042a', 'sand', '-', '-', 'pyramid'),             # Ъ bloki piramidy
]
WALKABLE = set('\u0445\u0446\u0449\u044a\u044d\u0422\u0423\u0424')

FROST_TOWN = (60, 4, 100, 47)
QAS_TOWN = (56, 178, 108, 220)
HARBOR = (104, 160, 162, 182)
ROYAL = (114, 66, 156, 104)


def put(g, x, y, ch):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = ch


def rect(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(g, x, y, ch)


def box(g, x0, y0, x1, y1, wall, floor):
    rect(g, x0, y0, x1, y1, wall)
    rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, floor)


def stamp(g, x0, y0, rows):
    """Wzor z napisow; spacja zostawia kafelek bez zmian."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != ' ':
                put(g, x0 + dx, y0 + dy, ch)


def stairs_up(transitions, x, y, z):
    """Schody w gore na (x,y,z) i powrotne obok (x+1,y-1,z+1) -> (x+1,y,z), jak w wiezy Varden."""
    transitions.append((x, y, z, 'STAIRS', x, y - 1, z + 1))
    transitions.append((x + 1, y - 1, z + 1, 'STAIRS', x + 1, y, z))


# ------------------------------------------------------------------ Frostholm: kamienne miasto gorskie

def frostholm(floors, transitions, dungeon):
    g, up, mine = floors[0], floors[1], floors[-1]
    rng = random.Random(5001)
    X0, Y0, X1, Y1 = FROST_TOWN
    # masyw gorski: dolne miasto u stop skaly, tarasy na pietrze +1 nad skala
    rect(g, X0 - 2, Y0, X1 + 2, 23, '\u03c9')
    for x in range(X0 - 2, X1 + 3):                       # poszarpany grzbiet od polnocy
        for y in range(Y0, Y0 + rng.randint(0, 2)):
            put(g, x, y, 's')
    rect(g, X0, 24, X1, 45, '\u03c9')
    rect(g, X0 + 3, 24, X1 - 3, 44, '=')
    rect(g, X0, 45, X1, 45, '#')                           # mur poludniowy z brama
    rect(g, 79, 45, 80, 45, '=')
    rect(g, X0 - 2, 30, X0 + 2, 33, '=')                   # brama zachodnia (szlak do jaskin lodu)
    rect(g, 80, Y0 + 3, 81, 23, '~')                       # potok w wawozie pod mostem

    # swiatynia (kamienna, posagi, oltarz)
    box(g, 74, 25, 86, 32, '#', '-')
    stamp(g, 75, 26, ['l\u0435  aa   \u0435l',
                      '    RR     ',
                      ' \u0441  RR   \u0441 ',
                      '    RR     ',
                      ' \u0441  RR   \u0441 ',
                      'l   RR    l'])
    rect(g, 79, 32, 80, 32, 'd')
    temple = (79, 29)
    # bank (lada, skarbiec) i depozyt (skrzynie)
    box(g, 63, 25, 72, 32, '#', '-')
    stamp(g, 64, 26, ['\u0442\u0440     \u0440\u0442',
                      '        ',
                      '\u043b\u043b\u043b\u043b\u043b\u043b\u043b\u043b',
                      '        ',
                      'l      l'])
    put(g, 68, 32, 'd')
    box(g, 63, 34, 72, 38, '#', '-')
    stamp(g, 64, 37, ['\u0442\u0442\u0442  \u0442\u0442\u0442'])
    put(g, 64, 35, 'l'); put(g, 71, 35, 'l')
    put(g, 68, 34, 'd')
    # sklep (drewno), tawerna, domy
    box(g, 88, 25, 97, 32, '%', '_')
    stamp(g, 89, 26, ['x      x',
                      '        ',
                      'BBBBBBBB',
                      '        ',
                      '        ',
                      '\u0433L    L\u0433'])
    put(g, 92, 32, 'D')
    box(g, 87, 34, 97, 39, '%', '_')
    stamp(g, 88, 35, ['L    BBBB',
                      '         ',
                      '\u0434 \u0434 \u0434  \u0433\u0433',
                      '        \u0433'])
    put(g, 87, 36, 'D')
    for hx, door in ((63, 65), (70, 72), (84, 86), (91, 93)):
        box(g, hx, 40, hx + 5, 44, '%', '_')
        stamp(g, hx + 1, 41, ['\u0433  \u0432', '    ', '\u0431 \u0434\u0439'])
        put(g, door, 40, 'D')
    # plac z fontanna, latarnie, posagi przy bramie
    rect(g, 79, 35, 80, 35, 'u')
    for lx, ly in ((76, 34), (83, 34), (76, 37), (83, 37), (77, 43), (82, 43)):
        put(g, lx, ly, 't')
    put(g, 78, 44, '\u0443'); put(g, 81, 44, '\u0443')
    for bx, by in ((75, 38), (84, 38)):
        put(g, bx, by, '\u043e')
    put(g, 84, 33, '\u043d')

    # schody na tarasy (zachodnie i wschodnie)
    put(g, 66, 24, '\u0423'); stairs_up(transitions, 66, 24, 0)
    put(g, 93, 24, '\u0423')
    transitions.append((93, 24, 0, 'STAIRS', 93, 23, 1))
    transitions.append((92, 23, 1, 'STAIRS', 92, 24, 0))

    # pietro +1: tarasy z ratuszem jarla, kuznia, biblioteka i domami; przepasc z mostem
    rect(up, X0 + 1, Y0 + 3, X1 - 1, 23, '=')
    rect(up, X0 + 1, Y0 + 3, X1 - 1, Y0 + 3, '\u03c9')
    rect(up, X0 + 1, Y0 + 3, X0 + 1, 23, '\u03c9')
    rect(up, X1 - 1, Y0 + 3, X1 - 1, 23, '\u03c9')
    rect(up, 80, Y0 + 3, 81, 23, ' ')                      # przepasc nad potokiem
    rect(up, 80, 17, 81, 18, 'b')                          # most nad przepascia
    # ratusz jarla (sala tronowa)
    box(up, 63, 9, 77, 16, '#', '-')
    stamp(up, 64, 10, ['l\u0442  \u0435 a \u0435  \u0442l',
                       '      R      ',
                       '\u0441     R     \u0441',
                       '\u0441     R     \u0441',
                       '      R      ',
                       'l\u0440    R    \u0440l'])
    put(up, 70, 16, 'd')
    # kuznia (palenisko, beczki, skrzynie)
    box(up, 70, 18, 78, 22, '#', '-')
    stamp(up, 71, 19, ['\u0448\u0448 \u041a\u041a\u0419\u0419',
                       '       ',
                       'l      '])
    put(up, 74, 22, 'd')
    box(up, 62, 18, 68, 22, '%', '_')
    stamp(up, 63, 19, ['\u0433   \u0432', '     ', '\u0431  \u0434\u0439'])
    put(up, 65, 22, 'D')
    # wschodni taras: biblioteka i dom kupca, punkt widokowy
    box(up, 84, 9, 97, 15, '#', '-')
    stamp(up, 85, 10, ['\u0440\u0440\u0440\u0440 \u0440\u0440\u0440\u0440 \u0440\u0440',
                       '            ',
                       '\u0441\u0441  l  \u0441\u0441  l',
                       '            ',
                       '\u0440\u0440\u0440\u0440    \u0440\u0440\u0440\u0440'])
    put(up, 90, 15, 'd')
    for hx, door in ((84, 86), (91, 93)):
        box(up, hx, 17, hx + 5, 21, '%', '_')
        stamp(up, hx + 1, 18, ['\u0433  \u0432', '    ', '\u0431 \u0434\u0439'])
        put(up, door, 21, 'D')
    for sx, sy in ((83, 10), (83, 13), (78, 9)):
        put(up, sx, sy, '\u0443')
    put(up, 67, 23, '\u0424')                               # zejscie z tarasu zachodniego
    put(up, 92, 23, '\u0424')

    # kopalnia: korytarz w skale -> schody w dol -> chodniki z krysztalami (-1)
    rect(g, 70, 18, 71, 23, 'c')
    put(g, 70, 18, ')')
    put(g, 71, 21, 'j')
    dungeon(mine, (60, 4, 100, 26), [(70, 19), (84, 8), (96, 18), (62, 10), (90, 24)], 'C', 'c', 'gggmmjj', 5,
            {(71, 18): '('}, random.Random(5002))
    rect(mine, 69, 18, 72, 22, 'c')
    put(mine, 71, 18, '(')
    transitions += [(70, 18, 0, 'STAIRS', 70, 19, -1), (71, 18, -1, 'STAIRS', 71, 19, 0)]
    return {
        'temple': temple,
        'protect': [(X0 - 3, Y0, X1 + 3, 46)],
        'pz': [(X0 + 3, 24, X1 - 3, 44, 0), (X0 + 1, Y0 + 3, X1 - 1, 23, 1)],
        'regions': ['Kopalnia Frostholm;60;4;100;26;-1;-1;40;90'],
    }


# ------------------------------------------------------------------ Qasirah: pustynia i dzungla

def qasirah(floors, transitions):
    g = floors[0]
    rng = random.Random(5101)
    X0, Y0, X1, Y1 = QAS_TOWN
    box(g, X0, Y0, X1, Y1, 'Z', 'y')
    for y in range(Y0 + 1, Y1):
        for x in range(X0 + 1, X1):
            if rng.random() < 0.06:
                g[y][x] = 'o'
    # aleje z piaskowca i bramy (polnoc: droga z Varden, wschod: szlak do piramidy)
    rect(g, 79, Y0, 80, Y1 - 1, 'X')
    rect(g, 81, 198, X1, 199, 'X')
    rect(g, X0 + 1, 205, 78, 206, 'X')
    for y in range(Y0 + 3, Y1, 6):
        put(g, 78, y, '\u044b'); put(g, 81, y, '\u044b')

    # ogrody (polnocny zachod): trawa dzungli, palmy, kwiaty, kanal z mostami, kapliczka
    rect(g, X0 + 1, Y0 + 1, 77, 190, 'J')
    for y in range(Y0 + 1, 191):
        for x in range(X0 + 1, 78):
            r = rng.random()
            g[y][x] = 'P' if r < 0.08 else ('\u0445' if r < 0.20 else ('h' if r < 0.28 else 'J'))
    rect(g, X0 + 1, 186, 77, 187, '~')
    rect(g, 64, 186, 65, 187, 'b'); rect(g, 73, 186, 74, 187, 'b')
    rect(g, 60, 180, 68, 184, 'X')
    stamp(g, 60, 180, ['\u0437  \u044e\u044e\u044e  \u0437',
                       '         ',
                       '\u0437       \u0437'])
    rect(g, 62, 188, 75, 190, 'J')
    for x in range(63, 75, 3):
        put(g, x, 189, 'P')

    # palac (polnocny wschod)
    box(g, 84, 180, 105, 190, 'Z', 'X')
    rect(g, 94, 182, 95, 189, '\u0449')                     # dywan do tronu
    for x, y, ch in ((85, 181, '\u0436'), (104, 181, '\u0436'), (86, 181, '\u0417'), (103, 181, '\u0417'),
                     (85, 183, '\u0416'), (85, 184, '\u0416'), (104, 183, '\u0416'), (104, 184, '\u0416'),
                     (88, 184, '\u044f'), (101, 184, '\u044f'), (88, 186, '\u044f'), (101, 186, '\u044f'),
                     (85, 186, '\u044a'), (104, 186, '\u044a'), (91, 181, '\u044a'), (98, 181, '\u044a'),
                     (85, 188, '\u041b'), (86, 188, '\u041b'), (103, 188, '\u041c'), (104, 188, '\u041c'),
                     (85, 189, '\u0436'), (104, 189, '\u0436')):
        put(g, x, y, ch)
    rect(g, 94, 181, 95, 181, '\u044e')                     # tron
    rect(g, 94, 190, 95, 190, '\u0446')
    for x in (83, 106):
        for y in (181, 185, 189):
            put(g, x, y, 'p')

    # oaza z palmami
    rect(g, 84, 192, 104, 196, 'y')
    for y in range(192, 197):
        for x in range(84, 105):
            if ((x - 94) / 9.5) ** 2 + ((y - 194) / 2.6) ** 2 <= 1.0:
                g[y][x] = '~'
            elif ((x - 94) / 10.5) ** 2 + ((y - 194) / 3.4) ** 2 <= 1.0 and rng.random() < 0.35:
                g[y][x] = 'p'

    # swiatynia Slonca (zachod, drzwi na aleje)
    box(g, 58, 192, 75, 203, 'Z', 'X')
    stamp(g, 59, 193, ['\u044a\u0436            \u0436\u044a',
                       '                ',
                       '  \u044f  \u044f    \u044f  \u044f  ',
                       '                ',
                       '\u044e\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449',
                       '\u044e\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449\u0449',
                       '                ',
                       '  \u044f  \u044f    \u044f  \u044f  ',
                       '                ',
                       '\u044a\u0436            \u0436\u044a'])
    rect(g, 75, 197, 75, 198, '\u0446')
    rect(g, 76, 197, 78, 198, 'X')
    temple = (67, 197)

    # bazar (stragany z lada, towarem i sprzedawcami)
    rect(g, 83, 201, 106, 210, 'X')
    for sx in (84, 91, 98):
        stamp(g, sx, 202, ['\u041c\u041b\u041c\u041b\u041c', '     ', '\u043f\u043f\u043f\u043f\u043f'])
        stamp(g, sx, 207, ['\u043f\u043f\u043f\u043f\u043f', '     ', '\u041b\u041c\u041b\u041c\u041b'])
    for x in (83, 89, 90, 96, 97, 104):
        put(g, x, 201, '\u044b' if x in (83, 104) else 'X')
    put(g, 94, 205, '\u044c')

    # bank i depozyt (poludniowy zachod), domy
    box(g, 58, 208, 67, 213, 'Z', 'X')
    stamp(g, 59, 209, ['        ', '\u043f\u043f\u043f\u043f\u043f\u043f\u043f\u043f', '        ', '\u0417\u044a    \u044a\u0417'])
    put(g, 62, 208, '\u0446')
    box(g, 69, 208, 77, 213, 'Z', 'X')
    stamp(g, 70, 209, ['\u044a     \u044a', '       ', '       ', '\u0417\u0417\u0417 \u0417\u0417\u0417'])
    put(g, 73, 208, '\u0446')
    for hx, door in ((58, 60), (65, 67), (72, 74)):
        box(g, hx, 215, hx + 5, 219, 'Z', 'X')
        stamp(g, hx + 1, 216, ['\u041b  \u0416', '    ', '\u041d \u044f\u0417'.replace('\u041d', '\u0431')])
        put(g, door, 215, '\u0446')
    # ruiny (poludniowy wschod): rozbite mury, posagi, gruz
    rect(g, 84, 212, 106, 219, 'y')
    stamp(g, 85, 212, ['ZZZ ZZ   Z  ZZZZ  Z   ',
                       'Z     \u041f    \u0436   Z  \u041f   ',
                       'Z  \u0436      Z     Z     ',
                       '   ZZ  \u041f   Z  Q       ',
                       'Z     Z   ZZZ   \u0436  Z ',
                       'Z  Q        \u041f      Z  ',
                       'ZZ  ZZZ  Z    ZZ ZZZ  '])
    # karawana przy bramie wschodniej
    stamp(g, 101, 196, ['\u0414\u0415', '  '])

    # piramida nad wejsciem do grobowcow (124,206): skala z piaskowca, korytarz, wyzsze stopnie +1..+3
    rect(g, 114, 199, 135, 215, '\u042a')
    rect(g, 124, 199, 125, 206, 'X')
    put(g, 124, 206, '^')
    put(g, 125, 201, '\u044a'); put(g, 124, 204, '\u044a')
    put(g, 123, 198, '\u0436'); put(g, 126, 198, '\u0436')               # posagi straznikow przy wejsciu
    for z, m in ((1, 2), (2, 5), (3, 8)):
        rect(floors[z], 114 + m, 199 + m, 135 - m, 215 - m, '\u042a')
    return {
        'temple': temple,
        'protect': [(X0 - 1, Y0 - 1, X1 + 1, Y1 + 1), (112, 197, 137, 217)],
        'pz': [(X0 + 1, Y0 + 1, X1 - 1, Y1 - 1, 0)],
        'regions': [],
    }


# ------------------------------------------------------------------ Selvaran: dzielnica portowa

def ship(g, x0, y0, rng, gangway=None):
    """Statek zacumowany dziobem na poludnie: burty, poklad, armaty, ladunek, latarnia na maszcie."""
    stamp(g, x0, y0, ['\u0411\u0411\u0411\u0411\u0411\u0411\u0411',
                      '\u0411\u043cbbb\u0447\u0411',
                      '\u0411bb\u0412bb\u0411',
                      '\u0411\u0438bbb\u0438\u0411',
                      '\u0411bbbbb\u0411',
                      '\u0411\u0438bbb\u0438\u0411',
                      ' \u0411b\u043cb\u0411 ',
                      '  \u0411\u0411\u0411  '])
    if gangway:
        gx, gy = gangway
        put(g, gx, gy, 'b')


def harbor(floors, transitions):
    g = floors[0]
    rng = random.Random(5201)
    X0, Y0, X1, Y1 = HARBOR
    rect(g, X0, Y0, 158, 168, 'J')
    rect(g, 110, 169, X1, Y1 - 1, '~')                      # basen portowy polaczony z ciesnina
    # nabrzeze z latarniami, beczkami i skrzyniami
    rect(g, X0 + 2, 166, 158, 168, '=')
    for x in range(X0 + 4, 158, 7):
        put(g, x, 166, 't')
    for x, ch in ((109, '\u043e'), (110, '\u043d'), (123, '\u043d'), (135, '\u043e'), (147, '\u043d'), (148, '\u043e')):
        put(g, x, 168, ch)
    # ulica od bramy poludniowej Selvaran
    rect(g, 125, 160, 126, 165, '=')
    # magazyny (drewno), kapitanat (kamien), targ rybny
    for wx0, wx1, door in ((106, 115, 110), (117, 123, 120)):
        box(g, wx0, 160, wx1, 165, '%', '_')
        for y in range(161, 165):
            for x in range(wx0 + 1, wx1):
                if (x + y) % 3 == 0 and y != 164:
                    g[y][x] = 'x' if (x * 7 + y) % 2 else '\u0433'
        put(g, door, 165, 'D')
    box(g, 128, 160, 135, 165, '#', '-')
    stamp(g, 129, 161, ['\u0440\u0440 \u0442\u0442 ', '      ', '\u043b\u043b\u043b\u043b\u043b\u043b', 'l    l'])
    put(g, 131, 165, 'd')
    # kanal z bagna do basenu, most na nabrzezu
    rect(g, 137, 160, 138, 168, '~')
    rect(g, 137, 166, 138, 168, 'b')
    rect(g, 140, 160, 147, 165, '=')
    stamp(g, 141, 161, ['\u043d\u043e \u043e\u043d ', '      ', '\u043b\u043b  \u043b\u043b'])
    # stocznia: plac z drewnem i kadlub w budowie
    rect(g, 149, 158, 158, 168, ':')
    stamp(g, 150, 159, ['\u043a\u043a  \u043a \u043a\u043a ',
                        '         ',
                        ' \u0411\u0411\u0411\u0411\u0411  \u0420',
                        ' \u0411:::\u0411   ',
                        ' \u0411:\u043a:\u0411 \u043a ',
                        ' \u0411:::\u0411   ',
                        '  \u0411:\u0411  \u0420 '])
    # molo i statki
    for px in (112, 128, 144):
        rect(g, px, 169, px + 1, 176, 'b')
    ship(g, 114, 170, rng, (114, 173))
    ship(g, 130, 171, rng, (130, 174))
    stamp(g, 146, 171, ['\u0411\u0411\u0411\u0411', '\u0411b\u0447\u0411', '\u0411bb\u0411', ' \u0411\u0411 '])
    put(g, 146, 172, 'b')
    ship(g, 150, 173, rng)                                  # statek na redzie (bez trapu)
    return {
        'protect': [(X0, 157, X1, Y1 + 1)],
        'pz': [(X0, Y0, X1, Y1 - 1, 0)],
        'regions': [],
    }


# ------------------------------------------------------------------ Varden: dzielnica krolewska

def royal_district(floors, transitions):
    g = floors[0]
    rng = random.Random(5301)
    X0, Y0, X1, Y1 = ROYAL
    rect(g, X0, Y0, X1, Y1, '.')
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            if rng.random() < 0.10:
                g[y][x] = ','
    # mury z bramami (zachod i wschod na glownej drodze, polnoc na szlak jeziora)
    for x in range(X0, X1 + 1):
        put(g, x, Y0, '#'); put(g, x, Y1, '#')
    for y in range(Y0, Y1 + 1):
        put(g, X0, y, '#'); put(g, X1, y, '#')
    rect(g, X0, 86, X1, 87, '=')
    rect(g, 146, Y0, 147, 85, '=')
    rect(g, 134, 88, 135, Y1 - 1, '=')
    # zamek: mury zewnetrzne, dziedziniec z fontanna, sala tronowa, skarbiec
    box(g, 119, 69, 139, 83, '#', '=')
    box(g, 121, 71, 137, 78, '#', '-')
    stamp(g, 122, 72, ['\u0442l \u0440\u0440 \u0435a\u0435 \u0440\u0440 l\u0442',
                       '\u0442     RRR     \u0442',
                       '  \u0441   RRR   \u0441  ',
                       '  \u0441   RRR   \u0441  ',
                       '      RRR      ',
                       'l\u0435    RRR    \u0435l'])
    rect(g, 128, 78, 130, 78, 'd')
    rect(g, 128, 80, 130, 80, 'u')
    for x in (123, 135):
        put(g, x, 80, 't')
    stamp(g, 120, 79, ['\u043e\u043e', '\u043d '])
    stamp(g, 137, 79, ['\u043d\u043e', ' \u043e'])
    rect(g, 128, 83, 130, 83, '=')
    rect(g, 128, 84, 130, 85, '=')
    put(g, 127, 84, '\u0443'); put(g, 131, 84, '\u0443')
    # ogrod krolewski miedzy zamkiem a ulica polnocna
    for y in range(70, 84):
        for x in range(140, 145):
            r = rng.random()
            g[y][x] = 'f' if r < 0.25 else ('Y' if r < 0.35 else ('T' if r < 0.42 else '.'))
    put(g, 142, 76, '\u0418')

    # biblioteka i gildia magow (wschod ulicy polnocnej)
    box(g, 149, 71, 155, 79, '#', '-')
    stamp(g, 150, 72, ['\u0440\u0440\u0440\u0440\u0440',
                       '     ',
                       '\u0440 \u0441 \u0440',
                       '\u0440 \u0441 \u0440',
                       '     ',
                       '\u0440\u0440 \u0440\u0440',
                       'l   l'])
    put(g, 149, 76, 'd')
    box(g, 149, 80, 155, 85, '#', '-')
    stamp(g, 150, 81, ['\u0435 g \u0435'.replace('g', '\u0442'), '     ', 'l\u0440 \u0440l'])
    put(g, 149, 82, 'd')

    # rynek z fontanna, posagami i straganami
    rect(g, X0 + 1, 89, 133, 97, '=')
    rect(g, 124, 92, 125, 93, 'u')
    for lx, ly in ((121, 90), (128, 90), (121, 95), (128, 95)):
        put(g, lx, ly, 't')
    for sx in (116, 129):
        stamp(g, sx, 90, ['\u043d\u043e\u043d', '\u043b\u043b\u043b', '   ', '\u043b\u043b\u043b', '\u043e\u043d\u043e'])
    put(g, 124, 97, '\u0443')
    # kuznia i tawerna (poludnie rynku)
    box(g, 119, 99, 126, 103, '#', '-')
    stamp(g, 120, 100, ['\u0448\u0448 \u0419\u041a ', '      ', 'l \u041a\u041a  '])
    put(g, 122, 99, 'd')
    box(g, 127, 99, 133, 103, '%', '_')
    stamp(g, 128, 100, ['BB \u0433\u0433', '     ', '\u0434 \u0434 L'])
    put(g, 130, 99, 'D')

    # bogata dzielnica: domy z kamienia z ogrodami
    for hx, hy in ((137, 90), (145, 90), (137, 97), (145, 97)):
        box(g, hx, hy, hx + 6, hy + 5, '#', '-')
        stamp(g, hx + 1, hy + 1, ['\u041dR \u0440\u0442', ' R   ', ' R \u0441 ', 'l   l'])
        put(g, hx + 2, hy, 'd')
        put(g, hx - 1, hy + 6, 'f'); put(g, hx + 7, hy + 6, 'Y')
    for x in range(136, 152):
        if g[96][x] == '.':
            g[96][x] = 'f'
    rect(g, 136, 89, 152, 89, '=')
    # wieze straznicze w naroznikach (z pochodniami) i armaty przy bramach
    for tx, ty in ((X0, Y0), (X1 - 4, Y0), (X0, Y1 - 4), (X1 - 4, Y1 - 4)):
        box(g, tx, ty, tx + 4, ty + 4, '#', '-')
        put(g, tx + 2, ty + 2, 'l')
        if ty == Y0:                                        # polnocne: drzwi do srodka dzielnicy z boku
            put(g, tx + 4 if tx == X0 else tx, ty + 2, 'd')
        else:
            put(g, tx + 2, ty, 'd')
    for cx, cy in ((X0 + 1, 84), (X0 + 1, 89), (X1 - 1, 84), (X1 - 1, 89), (144, Y0 + 1), (149, Y0 + 1)):
        put(g, cx, cy, '\u0413')
    for sx, sy in ((X0 + 2, 85), (X0 + 2, 88), (X1 - 2, 85), (X1 - 2, 88)):
        put(g, sx, sy, 't')

    return {
        'protect': [(X0 - 1, Y0 - 1, X1 + 1, Y1 + 1)],
        'pz': [(X0 + 1, Y0 + 1, X1 - 1, Y1 - 1, 0)],
        'regions': [],
    }
