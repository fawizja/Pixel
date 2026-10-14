#!/usr/bin/env python3
"""Generator mapy startowej Varden (pietra +5..-5).

Uruchom z katalogu repozytorium:  python3 tools/mapgen/generate_varden.py
Wynik trafia do shared/src/main/resources/maps/varden/. Generator jest deterministyczny
(stale ziarno), wiec ponowne uruchomienie daje identyczne pliki. Mozna tez edytowac
pliki floor_*.txt recznie - wtedy nie uruchamiaj ponownie generatora.
Znaczenie symboli: legend.csv.
"""
import os
import random
from collections import deque

W, H = 64, 48
OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'shared', 'src', 'main', 'resources', 'maps', 'varden')
WALKABLE = set('.,f=:b_-RDdlLwcjk<>()}HO')

rnd = random.Random(20261014)


def grid(fill=' '):
    return [[fill] * W for _ in range(H)]


def rect(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            g[y][x] = ch


def box(g, x0, y0, x1, y1, wall, floor):
    rect(g, x0, y0, x1, y1, wall)
    rect(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, floor)


def surface():
    g = grid('.')
    for y in range(H):
        for x in range(W):
            r = rnd.random()
            if r < 0.12:
                g[y][x] = ','
            elif r < 0.15:
                g[y][x] = 'f'
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d < 2:
                g[y][x] = 'T'
            elif d < 5:
                r = rnd.random()
                if r < 0.35:
                    g[y][x] = 'T'
                elif r < 0.40:
                    g[y][x] = 'Y'
    # rzeka na wschodzie
    rect(g, 52, 0, 54, H - 1, '~')
    # jezioro na poludniowym wschodzie
    for y in range(H):
        for x in range(W):
            if ((x - 45) / 5.0) ** 2 + ((y - 41) / 3.0) ** 2 <= 1.0:
                g[y][x] = '~'
    # drogi poza miastem
    rect(g, 31, 0, 32, 10, ':')          # polnocna droga (do Frostholm)
    rect(g, 31, 34, 32, H - 1, ':')      # poludniowa droga (do Qasirah)
    rect(g, 45, 22, 63, 23, ':')         # wschodnia droga (do Selvaran)
    rect(g, 52, 22, 54, 23, 'b')         # most
    rect(g, 8, 22, 17, 23, ':')          # zachodnia droga do kopalni
    rect(g, 16, 40, 30, 40, ':')         # sciezka do pol
    # mury miasta
    box(g, 18, 10, 44, 34, '#', '=')
    for gx, gy in [(31, 10), (32, 10), (31, 34), (32, 34), (18, 22), (18, 23), (44, 22), (44, 23)]:
        g[gy][gx] = '='
    # plac z fontanna i latarnie
    rect(g, 31, 22, 32, 23, 'u')
    for lx, ly in [(29, 20), (34, 20), (29, 25), (34, 25), (20, 21), (42, 24), (29, 11), (34, 33)]:
        g[ly][lx] = 't'
    # swiatynia (miejsce startu)
    box(g, 27, 12, 36, 19, '#', '-')
    rect(g, 31, 14, 32, 18, 'R')
    g[13][31] = 'a'
    g[13][32] = 'a'
    for tx, ty in [(28, 13), (35, 13), (28, 18), (35, 18)]:
        g[ty][tx] = 'l'
    g[19][31] = 'd'
    g[19][32] = 'd'
    # bank
    box(g, 20, 12, 25, 18, '%', '_')
    rect(g, 21, 14, 23, 14, 'B')
    g[13][24] = 'L'
    g[18][23] = 'D'
    # sklep ogolny
    box(g, 38, 12, 43, 18, '%', '_')
    rect(g, 39, 14, 41, 14, 'B')
    g[13][42] = 'x'
    g[15][42] = 'x'
    g[18][40] = 'D'
    # sklep magiczny
    box(g, 20, 27, 25, 32, '%', '_')
    rect(g, 21, 30, 23, 30, 'B')
    g[31][24] = 'L'
    g[27][23] = 'D'
    # domy mieszkancow
    box(g, 27, 28, 30, 32, '%', '_')
    g[28][28] = 'D'
    box(g, 33, 28, 36, 32, '%', '_')
    g[28][35] = 'D'
    # wieza straznicza (schody w gore do +5)
    box(g, 38, 27, 42, 31, '#', '-')
    g[27][40] = 'd'
    g[30][41] = '<'
    g[30][39] = 'l'
    # polana przy wejsciu do kopalni
    rect(g, 5, 19, 11, 26, ':')
    for x in range(5, 12):
        g[19][x] = 'q'
        g[26][x] = 'q'
    for y in range(19, 27):
        g[y][5] = 'q'
    g[22][8] = '}'
    g[25][10] = 'k'
    g[23][5] = ':'
    # pola uprawne
    box(g, 6, 36, 15, 43, 'F', 'w')
    g[40][15] = ':'
    return g


def tower_floor(z):
    g = grid(' ')
    if z == 5:
        box(g, 37, 26, 43, 32, '#', '-')
        g[28][39] = '>'
        g[27][38] = 'l'
        g[31][42] = 'l'
    else:
        box(g, 38, 27, 42, 31, '#', '-')
        g[28][39] = '>'
        g[30][41] = '<'
    return g


# kluczowe punkty kazdego pietra podziemi (musza byc polaczone)
CAVE_POINTS = {
    -1: [(8, 23), (8, 22), (14, 28), (14, 29)],
    -2: [(14, 27), (14, 28), (4, 30), (4, 29)],
    -3: [(5, 30), (4, 30), (15, 16), (15, 15)],
    -4: [(15, 17), (15, 16), (6, 18), (6, 17)],
    -5: [(7, 18), (6, 18), (12, 27)],
}
CAVE_MARKS = {
    -1: {(8, 22): '(', (14, 28): ')'},
    -2: {(14, 28): '(', (4, 30): 'O'},
    -3: {(4, 30): 'H', (15, 16): ')'},
    -4: {(15, 16): '(', (6, 18): 'O'},
    -5: {(6, 18): 'H'},
}
X0, Y0, X1, Y1 = 2, 14, 17, 32


def carve(g, x, y):
    if X0 < x < X1 and Y0 < y < Y1:
        g[y][x] = 'c'


def reachable(g, start):
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < W and 0 <= n[1] < H and n not in seen and g[n[1]][n[0]] in WALKABLE:
                seen.add(n)
                q.append(n)
    return seen


def cave_floor(z):
    g = grid(' ')
    rect(g, X0, Y0, X1, Y1, 'C')
    pts = CAVE_POINTS[z]
    for (px, py) in pts:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                carve(g, px + dx, py + dy)
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        x, y = ax, ay
        while x != bx:
            carve(g, x, y); carve(g, x, y + 1)
            x += 1 if bx > x else -1
        while y != by:
            carve(g, x, y); carve(g, x + 1, y)
            y += 1 if by > y else -1
    # dodatkowe komory (expowiska na pozniejsze etapy)
    for _ in range(3 + (-z)):
        cx, cy = rnd.randint(X0 + 2, X1 - 3), rnd.randint(Y0 + 2, Y1 - 3)
        rw, rh = rnd.randint(1, 2), rnd.randint(1, 2)
        for y in range(cy - rh, cy + rh + 1):
            for x in range(cx - rw, cx + rw + 1):
                carve(g, x, y)
        x, y = cx, cy
        tx, ty = pts[0]
        while x != tx:
            carve(g, x, y)
            x += 1 if tx > x else -1
        while y != ty:
            carve(g, x, y)
            y += 1 if ty > y else -1
    if z == -5:  # komora bossa
        for y in range(24, 31):
            for x in range(9, 16):
                carve(g, x, y)
    for (mx, my), ch in CAVE_MARKS[z].items():
        g[my][mx] = ch
    # dekoracje: pochodnie plytko, krysztaly gleboko, gruz wszedzie (bez psucia przejsc)
    protected = set(pts)
    cells = [(x, y) for y in range(H) for x in range(W) if g[y][x] == 'c' and (x, y) not in protected]
    rnd.shuffle(cells)
    want = {'j': 3 if z >= -2 else 1, 'g': 0 if z >= -2 else 2 + (-z - 3), 'm': 3}
    for ch, n in want.items():
        for (x, y) in cells:
            if n <= 0:
                break
            if g[y][x] != 'c':
                continue
            g[y][x] = ch
            if ch in WALKABLE:
                n -= 1
                continue
            base = reachable(g, pts[0])
            if all(p in base for p in pts):
                n -= 1
            else:
                g[y][x] = 'c'
    reach = reachable(g, pts[0])
    missing = [p for p in pts if p not in reach]
    assert not missing, f'floor {z}: unreachable {missing}'
    return g


def write(z, g):
    name = f'floor_{"+" + str(z) if z > 0 else z}.txt'
    with open(os.path.join(OUT, name), 'w') as f:
        for row in g:
            f.write(''.join(row).rstrip() + '\n')


def main():
    os.makedirs(OUT, exist_ok=True)
    write(0, surface())
    for z in range(1, 6):
        write(z, tower_floor(z))
    for z in range(-1, -6, -1):
        write(z, cave_floor(z))
    tr = ['# x;y;z;typ;docelowe x;y;z',
          '# wieza: schody w gore (41,30) i w dol (39,28)']
    for z in range(0, 5):
        tr.append(f'41;30;{z};STAIRS;40;30;{z + 1}')
    for z in range(1, 6):
        tr.append(f'39;28;{z};STAIRS;40;28;{z - 1}')
    tr += ['# kopalnia pod Varden',
           '8;22;0;STAIRS;8;23;-1',
           '8;22;-1;STAIRS;8;21;0',
           '14;28;-1;STAIRS;14;27;-2',
           '14;28;-2;STAIRS;14;29;-1',
           '4;30;-2;HOLE;5;30;-3',
           '4;30;-3;LADDER;4;29;-2',
           '15;16;-3;STAIRS;15;17;-4',
           '15;16;-4;STAIRS;15;15;-3',
           '6;18;-4;HOLE;7;18;-5',
           '6;18;-5;LADDER;6;17;-4']
    with open(os.path.join(OUT, 'transitions.csv'), 'w') as f:
        f.write('\n'.join(tr) + '\n')
    with open(os.path.join(OUT, 'map.properties'), 'w') as f:
        f.write('# Region startowy: miasto Varden (klimat umiarkowany)\n'
                'name=Varden\n'
                f'width={W}\nheight={H}\n'
                'floors=-5,-4,-3,-2,-1,0,1,2,3,4,5\n'
                '# swiatynia w Varden - bezpieczne miejsce startu\n'
                'spawn=31,16,0\n'
                '# strefy ochronne (x0,y0,x1,y1,z): wnetrze murow miasta i wieza\n'
                'pz=19,11,43,33,0;37,26,43,32,1;37,26,43,32,2;37,26,43,32,3;37,26,43,32,4;37,26,43,32,5\n')


if __name__ == '__main__':
    main()
