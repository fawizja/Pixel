"""Plan Durnkaru na trzech pietrach (Darek 2026-11-11: "miasto krasnoludow moze miec poziomy i nie byc takie kwadratowe").

Zamiast jednej prostokatnej groty: trzy nieregularne jaskinie nad soba, polaczone schodami.
  -4  Wysokie Hale   - brama zachodnia (przybycie sztolniami), swiatynia, bank z depozytem, sala tronowa, domy
  -5  Rynek          - kreta aleja z placem, karczma, kupiec, narzedziownik, runiarz, opiekun zwierzat, domy;
                       brama wschodnia do Kopalni Zelaznej Brody (drzwi poziomu)
  -6  Kuznie         - kuzn, platnerz, zaklinacz nad rzeka lawy z mostami, domy kowali
Jaskinie powstaja z krzywych ulic (spline Catmull-Rom) o zmiennej szerokosci, okraglych placow, zatok, filarow
skalnych i budynkow wstawianych wzdluz ulic (drzwi zawsze od strony ulicy). Skala wokol to zwykla sciana jaskini.
"""
import math

import numpy as np

import structures as St

NONE = '-'
CITY = 'Durnkar'
STONE, MARBLE, WOOD = 'stone_floor', 'marble_floor', 'wood_floor'
SWALL, ROCK = 'stone_wall', 'cave_wall'
STREET = 'cobblestone'
CAVE = 'cave_floor'
CW, CH = 92, 66
ZU, ZM, ZL = -4, -5, -6          # pietro gorne, srodkowe (rynek), dolne (kuznie)


def noise_field(rng, cell=6):
    g = [[rng.random() for _ in range(CW * 2 // cell + 6)] for _ in range(CH * 2 // cell + 6)]

    def at(x, y):
        fx, fy = x / cell, y / cell
        x0, y0 = int(fx), int(fy)
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = g[y0][x0] * (1 - tx) + g[y0][x0 + 1] * tx
        b = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    return at


def spline(pts, step=1.0):
    """Gesta lamana przez punkty kontrolne (Catmull-Rom)."""
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        n = max(2, int(math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / step))
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    out.append(tuple(pts[-1]))
    return out


class Level:
    """Jedno pietro miasta: zbior pol jaskini, ulice i budynki (wspolrzedne lokalne 0..CW, 0..CH)."""

    def __init__(self, z, rng, noise):
        self.z, self.rng, self.nz = z, rng, noise
        self.cave = set()        # pola do wykucia
        self.street = set()      # pola ulic (srodek)
        self.lava = set()
        self.bridge = set()
        self.boxes = []          # (x, y, w, h)
        self.pillars = set()

    def disc(self, cx, cy, r, into):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if 1 <= x < CW - 1 and 1 <= y < CH - 1 and (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    into.add((x, y))

    def road(self, pts, rad=2.6, wob=1.2, plates=False):
        line = spline(pts)
        for (x, y) in line:
            r = rad + wob * (self.nz(x * 1.3, y * 1.3) - 0.5) * 2
            self.disc(x, y, max(1.6, r), self.cave)
            self.disc(x, y, max(1.0, rad - 1.2), self.street)
        return line

    def blob(self, cx, cy, rx, ry, street=False):
        for y in range(int(cy - ry) - 2, int(cy + ry) + 3):
            for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
                if not (1 <= x < CW - 1 and 1 <= y < CH - 1):
                    continue
                d = math.hypot((x - cx) / rx, (y - cy) / ry)
                d += 0.28 * (self.nz(x * 1.7 + 11, y * 1.7) - 0.5)
                if d <= 1.0:
                    self.cave.add((x, y))
                    if street and d <= 0.82:
                        self.street.add((x, y))

    def free_rect(self, x, y, w, h, margin=1):
        for (a, b, c, d) in self.boxes:
            if not (x + w + margin <= a or a + c + margin <= x or y + h + margin <= b or b + d + margin <= y):
                return False
        if x < 2 or y < 2 or x + w > CW - 2 or y + h > CH - 2:
            return False
        for yy in range(y - 1, y + h + 1):
            for xx in range(x - 1, x + w + 1):
                if (xx, yy) in self.lava or (xx, yy) in self.bridge:
                    return False
        return True

    def place(self, x, y, w, h, door_side, margin=1):
        """Rejestruje budynek (jaskinia obejmuje jego obrys); zwraca (x, y, w, h, drzwi, pole przed drzwiami)."""
        self.boxes.append((x, y, w, h))
        for yy in range(y - 1, y + h + 1):
            for xx in range(x - 1, x + w + 1):
                self.cave.add((xx, yy))
        door = {'S': (x + w // 2, y + h - 1), 'N': (x + w // 2, y), 'E': (x + w - 1, y + h // 2), 'W': (x, y + h // 2)}[door_side]
        out = {'S': (door[0], door[1] + 1), 'N': (door[0], door[1] - 1), 'E': (door[0] + 1, door[1]),
               'W': (door[0] - 1, door[1])}[door_side]
        return (x, y, w, h, door, out)

    def along(self, line, w, h, side, at, offset=1):
        """Prostokat w x h po stronie side (+1 / -1) ulicy line w punkcie at (indeks); drzwi od ulicy. None gdy zajete."""
        i = min(max(at, 1), len(line) - 2)
        (px, py), (qx, qy) = line[i - 1], line[i + 1]
        tx, ty = qx - px, qy - py
        n = math.hypot(tx, ty) or 1
        nx, ny = -ty / n * side, tx / n * side
        gap = 3 + offset
        x, y = int(round(line[i][0])), int(round(line[i][1]))
        if abs(nx) >= abs(ny):                 # budynek z boku (E / W od ulicy), drzwi od ulicy
            bx = x + gap if nx > 0 else x - gap - w + 1
            by = y - h // 2
            door = 'W' if nx > 0 else 'E'
        else:
            by = y + gap if ny > 0 else y - gap - h + 1
            bx = x - w // 2
            door = 'N' if ny > 0 else 'S'
        # nie na ulicy
        for yy in range(by, by + h):
            for xx in range(bx, bx + w):
                if (xx, yy) in self.street:
                    return None
        if not self.free_rect(bx, by, w, h):
            return None
        b = self.place(bx, by, w, h, door)
        if True:                                # chodnik od drzwi do ulicy (zawsze: waski mur potrafi odciac budynek)
            ox_, oy_ = b[5]
            sx_, sy_ = x, y
            n = max(abs(sx_ - ox_), abs(sy_ - oy_))
            for k in range(n + 1):
                t = k / max(1, n)
                cx_, cy_ = int(round(ox_ + (sx_ - ox_) * t)), int(round(oy_ + (sy_ - oy_) * t))
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        if abs(dx) + abs(dy) <= 1:
                            self.cave.add((cx_ + dx, cy_ + dy))
                self.street.add((cx_, cy_))
        return b

    def join(self, a, b, rad=1.8):
        line = spline([a, ((a[0] + b[0]) / 2 + self.rng.randint(-3, 3), (a[1] + b[1]) / 2 + self.rng.randint(-3, 3)), b])
        for (x, y) in line:
            self.disc(x, y, rad, self.cave)
            self.disc(x, y, rad - 0.6, self.street)

    def pillars_in(self, n, avoid=()):
        tries = 0
        placed = 0
        cells = sorted(self.street)
        while placed < n and tries < 400 and cells:
            tries += 1
            x, y = self.rng.choice(cells)
            if any(abs(x - a) + abs(y - b) < 5 for (a, b) in avoid):
                continue
            if any((x + dx, y + dy) in self.pillars for dx in range(-4, 5) for dy in range(-4, 5)):
                continue
            ring = [(x + dx, y + dy) for dx in range(-3, 4) for dy in range(-3, 4)]
            if not all(c in self.street for c in ring):
                continue
            for c in [(x, y), (x + 1, y), (x, y + 1)] + ([(x + 1, y + 1)] if self.rng.random() < 0.5 else []):
                self.pillars.add(c)
            placed += 1

    def commit(self, p, ox, oy, ground):
        """Wpisuje jaskinie do planu: podloga, sciany skaly dookola, ulice, lawa, mosty, filary."""
        z = self.z
        for (x, y) in self.cave:
            g = STREET if (x, y) in self.street else ground
            p.put(ox + x, oy + y, z, (g, NONE, NONE, NONE))
        for (x, y) in self.lava:
            p.put(ox + x, oy + y, z, ('lava', NONE, NONE, NONE))
        for (x, y) in self.bridge:
            p.put(ox + x, oy + y, z, ('bridge', NONE, NONE, NONE))
        for (x, y) in self.pillars:
            p.put(ox + x, oy + y, z, (CAVE, NONE, NONE, ROCK))
        inside = set(self.cave) | self.lava | self.bridge
        for (x, y) in list(inside):
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    n = (x + dx, y + dy)
                    if n not in inside and p.get(ox + n[0], oy + n[1], z) is None:
                        p.put(ox + n[0], oy + n[1], z, (CAVE, NONE, NONE, ROCK))


def torch(p, x, y, z):
    c = p.get(x, y, z)
    if c is None or c[3] == NONE:
        return
    for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
        d = p.get(x + dx, y + dy, z)
        if d is not None and St.walk(d) and d[2] == NONE:
            p.put(x + dx, y + dy, z, (d[0], d[1], 'wall_torch', NONE))
            return


def _door_side(b):
    (x, y, w, h, door, out) = b
    if door[1] == y + h - 1 and door[0] not in (x, x + w - 1):
        return 'S'
    if door[1] == y:
        return 'N'
    return 'E' if door[0] == x + w - 1 else 'W'


def build(p, ox, oy, rng, house_fn, keep_free, add_npc_text):
    """Buduje miasto w planie p (lewy gorny rog planu miasta = (ox, oy)). Zwraca (spots, npcs, houses)."""
    nz = noise_field(rng)
    spots, npcs, houses = {}, [], []
    color = '#a0703a'
    U, M, L = Level(ZU, rng, nz), Level(ZM, rng, nz), Level(ZL, rng, nz)

    # ================================================================= ulice
    ua = U.road([(0, 31), (12, 33), (22, 33), (34, 29), (48, 31), (66, 31), (80, 27), (86, 24)], rad=3.0, wob=1.4)
    ma = M.road([(0, 31), (12, 30), (22, 33), (34, 37), (46, 34), (58, 30), (66, 31), (78, 34), (CW - 2, 31)], rad=3.0, wob=1.4)
    la = L.road([(6, 40), (22, 38), (34, 38), (46, 42), (60, 40), (72, 36), (84, 38)], rad=3.0, wob=1.4)
    # ulice boczne (gorne: polnoc i poludnie; rynek: odnogi; kuznie: petla)
    ub1 = U.road([(22, 33), (18, 44), (26, 54), (40, 56)], rad=2.2, wob=1.0)
    ub2 = U.road([(66, 31), (70, 42), (62, 52), (50, 56)], rad=2.2, wob=1.0)
    ub3 = U.road([(34, 29), (30, 20), (24, 12)], rad=2.2, wob=1.0)
    mb1 = M.road([(34, 37), (28, 46), (34, 56), (48, 58)], rad=2.2, wob=1.0)
    mb2 = M.road([(46, 34), (50, 22), (44, 12)], rad=2.2, wob=1.0)
    mb3 = M.road([(66, 31), (72, 44), (64, 54)], rad=2.2, wob=1.0)
    mb4 = M.road([(22, 33), (14, 22), (20, 10)], rad=2.2, wob=1.0)
    lb1 = L.road([(34, 38), (28, 28), (36, 16), (50, 12)], rad=2.2, wob=1.0)
    lb2 = L.road([(60, 40), (66, 52), (54, 58)], rad=2.2, wob=1.0)
    # place i zatoki
    M.blob(46, 34, 12, 8, street=True)                     # rynek
    M.blob(10, 31, 7, 6, street=True)                      # plac przed bramami
    M.blob(78, 33, 8, 7, street=True)
    U.blob(14, 30, 8, 6, street=True)                      # hala zachodnia
    U.blob(80, 27, 9, 8, street=True)                      # przed sala tronowa
    U.blob(40, 30, 7, 5, street=True)
    L.blob(44, 40, 10, 7, street=True)
    L.blob(72, 36, 8, 6, street=True)
    L.blob(20, 38, 8, 6, street=True)

    stair_spots = {(ZU, ZM): [(24, 36), (68, 33)], (ZM, ZL): [(36, 40), (62, 36)]}
    for (za, zb), pts_ in stair_spots.items():
        for (sx, sy) in pts_:
            for lv in (U, M, L):
                if lv.z in (za, zb):
                    lv.boxes.append((sx - 3, sy - 3, 7, 7))

    # ================================================================= pietro -4: Wysokie Hale
    # swiatynia, bank, sala tronowa wzdluz alei gornej, domy przy bocznych ulicach
    def need(level, line, w, h, side, at, tries=14):
        for off in (1, 3, 5):
            for k in range(tries * 4):
                shift = ((k + 1) // 2) * 3 * (1 if k % 2 else -1)
                for s in (side, -side):
                    b = level.along(line, w, h, s, at + shift, offset=off)
                    if b is not None:
                        return b
        raise AssertionError(f'no room for {w}x{h} near {at} on floor {level.z}')

    # swiatynia w polnocno-zachodniej czesci gornej alei
    t = need(U, ua, 18, 12, -1, 16)
    U_temple = t
    throne = need(U, ua, 18, 12, -1, 58)
    bank = need(U, ua, 13, 10, 1, 40)
    # domy: gora (6)
    u_houses = []
    for line, at, side in ((ub3, 6, 1), (ub3, 6, -1), (ub1, 10, 1), (ub1, 10, -1), (ub2, 8, 1), (ub2, 8, -1),
                           (ub1, 26, 1), (ub2, 28, -1)):
        if len(u_houses) >= 6:
            break
        try:
            u_houses.append(need(U, line, 7, 7, side, at, tries=10))
        except AssertionError:
            continue
    assert len(u_houses) >= 5, f'upper houses {len(u_houses)}'
    # ================================================================= pietro -5: Rynek
    tav = need(M, ma, 14, 10, -1, 36, tries=16)
    mer = need(M, ma, 11, 8, 1, 30)
    tools = need(M, ma, 10, 8, 1, 62)
    mage = need(M, ma, 11, 8, 1, 82)
    m_houses = []
    for line, at, side in ((mb4, 6, 1), (mb4, 6, -1), (mb2, 8, 1), (mb2, 8, -1), (mb1, 8, 1), (mb1, 8, -1), (mb3, 10, 1),
                           (mb3, 10, -1), (mb1, 24, 1), (mb3, 24, -1)):
        if len(m_houses) >= 6:
            break
        try:
            m_houses.append(need(M, line, 7, 7, side, at, tries=10))
        except AssertionError:
            continue
    assert len(m_houses) >= 5, f'market houses {len(m_houses)}'
    # ================================================================= pietro -6: Kuznie
    smith = need(L, la, 13, 9, -1, 28)
    armor = need(L, la, 11, 9, -1, 56)
    imb = need(L, la, 9, 8, 1, 38)
    l_houses = []
    for line, at, side in ((lb1, 8, 1), (lb1, 8, -1), (lb2, 8, 1), (lb2, 8, -1), (lb1, 24, 1), (lb2, 22, -1)):
        if len(l_houses) >= 4:
            break
        try:
            l_houses.append(need(L, line, 7, 7, side, at, tries=10))
        except AssertionError:
            continue
    assert len(l_houses) >= 3, f'forge houses {len(l_houses)}'

    # ================================================================= schody miedzy pietrami (ten sam xy na obu pietrach)
    for (za, zb), spots_ in stair_spots.items():
        for (sx, sy) in spots_:
            for lv in (U, M, L):
                if lv.z in (za, zb):
                    lv.disc(sx, sy, 3.2, lv.cave)
                    lv.disc(sx, sy, 2.6, lv.street)
                    # rozjazd do najblizszej ulicy tego pietra
                    near = min(lv.street - set(c for c in lv.street if (abs(c[0] - sx) < 3 and abs(c[1] - sy) < 3)),
                               key=lambda c: abs(c[0] - sx) + abs(c[1] - sy), default=None)
                    if near is not None and abs(near[0] - sx) + abs(near[1] - sy) > 3:
                        lv.join((sx, sy), near, rad=2.0)
    # lawa w kuzniach: rzeka z mostami nad poludniowa czescia, jeziorko na polnocy
    lava_line = spline([(2, 56), (16, 50), (30, 52), (44, 52), (58, 56), (74, 50), (CW - 3, 54)])
    for (x, y) in lava_line:
        for dy in range(-2, 3):
            for dx in range(-1, 2):
                c = (int(x) + dx, int(y) + dy)
                if (c not in L.street) and all(not (b[0] - 1 <= c[0] <= b[0] + b[2] and b[1] - 1 <= c[1] <= b[1] + b[3]) for b in L.boxes) \
                        and 2 <= c[0] < CW - 2 and 2 <= c[1] < CH - 2:
                    L.lava.add(c)
                    L.cave.add(c)
    for (x, y) in lb2:
        c = (int(x), int(y))
        if c in L.lava:
            L.bridge.add(c)
    for (x, y) in lb2:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c = (int(x) + dx, int(y) + dy)
                if c in L.lava and abs(dx) + abs(dy) <= 1 and (int(x), int(y)) in L.bridge:
                    L.bridge.add(c)
    L.lava -= L.bridge
    # filary skalne na ulicach rynku i kuzni
    for lv in (U, M, L):
        lv.pillars_in(10, avoid=[(sx, sy) for sp in stair_spots.values() for (sx, sy) in sp])

    # ================================================================= wpis jaskin
    for lv, ground in ((U, CAVE), (M, CAVE), (L, CAVE)):
        lv.commit(p, ox, oy, ground)
    # hala zachodnia -4 (przybycie sztolniami) poza miastem i otwarty tunel do alei, brama wschodnia -5
    z = ZU
    hall = (ox - 13, oy + 25, ox - 1, oy + 37)
    for y in range(hall[1], hall[3] + 1):
        for x in range(hall[0], hall[2] + 1):
            edge = x in (hall[0], hall[2]) or y in (hall[1], hall[3])
            p.put(x, y, z, (STONE, NONE, NONE, ROCK) if edge else (STONE, NONE, NONE, NONE))
    for y in (30, 31, 32):
        p.put(hall[2], oy + y, z, (STONE, NONE, NONE, NONE))
        for x in range(0, 3):
            c = p.get(ox + x, oy + y, z)
            if c is None or c[3] != NONE:
                p.put(ox + x, oy + y, z, (STONE, NONE, NONE, NONE))
    spots['west_stairs'] = (hall[0] + 3, oy + 31)
    for (x, y) in ((hall[0] + 2, hall[1] + 2), (hall[2] - 2, hall[1] + 2), (hall[0] + 2, hall[3] - 2), (hall[2] - 2, hall[3] - 2)):
        p.obj(x, y, z, 'statue')
    torch(p, hall[0] + 5, hall[1], z)
    torch(p, hall[0] + 5, hall[3], z)
    npcs.append(['durn_carrier', 'Wózkarz Brodrik', 'CARRIER', CITY, hall[0] + 7, hall[1] + 2, z, '-', '#8a6a4a',
                 'Wagonikiem starymi torami pod górami do innych miast kontynentu. Płacisz monetami, trzymaj brodę w środku.'])
    for (x, y, o) in ((hall[0] + 6, hall[1] + 1, 'crate'), (hall[0] + 8, hall[1] + 1, 'barrel'), (hall[0] + 9, hall[1] + 1, 'crate')):
        p.obj(x, y, z, o)
    npcs.append(['durn_guard_west', 'Strażnik bramy Hroald', 'GUARD', CITY, ox + 4, oy + 32, z, '-', '#8a8a9a',
                 'Stój! Mało kto trafia do Durnkaru przez wąwóz. Wejdź, skoro już tu jesteś, i nie zgub drogi powrotnej.'])
    # brama wschodnia -5: korytarz z hali na wschodnim koncu rynku
    z = ZM
    ey = 31
    for y in (ey - 1, ey, ey + 1):
        for x in range(CW - 6, CW):
            p.put(ox + x, oy + y, z, (STONE, NONE, NONE, NONE))
    for x in range(CW - 6, CW):
        for y in (ey - 2, ey + 2):
            c = p.get(ox + x, oy + y, z)
            if c is None or c[3] == NONE and not St.walk(c):
                p.put(ox + x, oy + y, z, (STONE, NONE, NONE, ROCK))
    spots['east_gate'] = (ox + CW, oy + ey)
    npcs.append(['durn_guard_east', 'Strażnik bramy Sigvald', 'GUARD', CITY, ox + CW - 5, oy + ey - 1, z, '-', '#8a8a9a',
                 'Za tą bramą Kopalnia Żelaznej Brody. Drzwi przepuszczą tylko tych, którzy przeżyją w głębinach.'])

    # ================================================================= budynki
    reserved = set()
    names = iter([f'Kowadłowa {i}' for i in range(1, 10)] + [f'Głębinowa {i}' for i in range(1, 10)]
                 + [f'Wyżynna {i}' for i in range(1, 10)])

    def box_at(b, floor, zz):
        x, y, w, h, door, out = b
        inner = p.box(zz, ox + x, oy + y, w, h, floor, SWALL, doors=[(ox + door[0], oy + door[1])])
        reserved.add((ox + out[0], oy + out[1]))
        return (ox + x, oy + y, w, h), (ox + door[0], oy + door[1]), inner

    def furnish(zz, rect, door, inner, props):
        """Rekwizyty przy scianach najdalej od drzwi (nie blokuja wejscia); zwraca zajete pola."""
        x0, y0, w, h = rect
        ring = [c for c in inner if c[0] in (x0 + 1, x0 + w - 2) or c[1] in (y0 + 1, y0 + h - 2)]
        far = sorted(ring, key=lambda c: -(abs(c[0] - door[0]) + abs(c[1] - door[1])))
        used = []
        for o in props:
            for c in far:
                if c in used or abs(c[0] - door[0]) + abs(c[1] - door[1]) < 4:
                    continue
                p.obj(c[0], c[1], zz, o)
                used.append(c)
                break
        return used

    def spot(rect, door, inner, used, far_from_door=3):
        x0, y0, w, h = rect
        cx, cy = x0 + w // 2, y0 + h // 2
        cands = [c for c in inner if c not in used and abs(c[0] - door[0]) + abs(c[1] - door[1]) >= far_from_door]
        return min(cands, key=lambda c: abs(c[0] - cx) + abs(c[1] - cy))

    def house_box(b, zz, floor):
        x, y, w, h, door, out = b
        hh = house_fn(p, zz, ox + x, oy + y, w, h, _door_side(b), floor, rng)
        reserved.add((ox + out[0], oy + out[1]))
        houses.append((next(names), hh, zz))

    def service(b, zz, floor, props, key, name, role, shop, greet, col=color, carpet=False):
        rect, door, inner = box_at(b, floor, zz)
        used = furnish(zz, rect, door, inner, props)
        sx, sy = spot(rect, door, inner, used)
        npcs.append([key, name, role, CITY, sx, sy, zz, shop or '-', col, greet])
        return rect, door, inner, used

    # ---- pietro -4: swiatynia, bank z depozytem, sala tronowa
    z = ZU
    rect, door, inner = box_at(U_temple, MARBLE, z)
    tx, ty, tw, th = rect
    cx = tx + tw // 2
    # ołtarz na scianie przeciwleglej do drzwi (odwrocone o strone drzwi): zawsze w glebi sali
    far = max(inner, key=lambda c: abs(c[0] - door[0]) + abs(c[1] - door[1]))
    p.obj(far[0], far[1], z, 'altar')
    used = [far]
    benches = furnish(z, rect, door, inner, ['bench'] * 6 + ['statue', 'statue', 'candle', 'candle'])
    sp = spot(rect, door, inner, used + benches, 4)
    spots['temple'] = (sp[0], sp[1])
    near = [c for c in inner if c not in used + benches + [tuple(sp)] and 1 <= abs(c[0] - sp[0]) + abs(c[1] - sp[1]) <= 3]
    pr = min(near, key=lambda c: abs(c[0] - sp[0]) + abs(c[1] - sp[1])) if near else sp   # kaplan obok swiatyni, nie na niej
    npcs.append(['durn_priest', 'Kapłan Thorvin', 'PRIEST', CITY, pr[0], pr[1], z, '-', '#f0f0f0',
                 'Witaj w świątyni Durnkaru, synu i córko kamienia. Mogę cię uleczyć albo przyjąć do miasta.'])
    rect, door, inner = box_at(bank, STONE, z)
    used = furnish(z, rect, door, inner, ['depot_locker'] * 5 + ['chest', 'chest', 'counter'])
    bs = spot(rect, door, inner, used, 3)
    npcs.append(['durn_banker', 'Bankier Grimbald', 'BANKER', CITY, bs[0], bs[1], z, '-', '#d8c070',
                 'Złoto w skarbcu Durnkaru leży pod tysiącem sążni skały. Bank prowadzi konta we wszystkich miastach.'])
    hs = spot(rect, door, inner, used + [bs], 3) if len(inner) > 8 else bs
    cands = [c for c in inner if c not in used + [bs] and abs(c[0] - door[0]) + abs(c[1] - door[1]) >= 3]
    hs = max(cands, key=lambda c: abs(c[0] - bs[0]) + abs(c[1] - bs[1]))
    npcs.append(['durn_housing', 'Zarządca Durgan', 'HOUSING', CITY, hs[0], hs[1], z, '-', '#6a5a8a',
                 'Domy w grotach Durnkaru, ciepłe od lawy. Płacisz z konta w banku.'])
    rect, door, inner = box_at(throne, MARBLE, z)
    far = max(inner, key=lambda c: abs(c[0] - door[0]) + abs(c[1] - door[1]))
    p.obj(far[0], far[1], z, 'throne')
    used = [far]
    props = furnish(z, rect, door, inner, ['banner', 'banner', 'statue', 'statue', 'statue', 'statue', 'campfire', 'campfire'])
    ks = spot(rect, door, inner, used + props, 4)
    npcs.append(['durn_ruler', 'Król Thorgrim', 'KING', CITY, ks[0], ks[1], z, '-', '#c8a040',
                 'Durnkar pamięta czasy, gdy nad górami nie było jeszcze miast. Konto premium i promocję dam ci tak jak '
                 'król w Varden: napisz premium albo promocja.'])
    for b in u_houses:
        house_box(b, ZU, STONE)
    # ---- pietro -5: karczma, kupiec, narzedziownik, runiarz, zagroda, przewodnik
    z = ZM
    service(tav, z, WOOD, ['barrel', 'barrel', 'table', 'table', 'table', 'chair', 'chair', 'firewood', 'counter'],
            'durn_innkeeper', 'Karczmarz Ulfrik', 'MERCHANT', 'durn_supplies',
            'Piwo z grzybów jaskiniowych, chleb, mięso i lina na drogę. Siadaj, przybyszu.')
    service(mer, z, WOOD, ['crate', 'sack', 'crate', 'sack', 'counter'], 'durn_merchant', 'Kupiec Gunnar', 'MERCHANT', 'durn_shop',
            'Kupię łupy z kopalń i sprzedam to, czego brakuje w drodze.')
    service(tools, z, STONE, ['crate', 'lumber', 'barrel', 'sack', 'counter'], 'durn_tools', 'Narzędziownik Skagi', 'MERCHANT',
            'durn_tools', 'Liny, łopaty, pochodnie i hełmy górnicze - wszystko, czego trzeba w kopalni.')
    service(mage, z, STONE, ['bookcase', 'clay_pot', 'bookcase', 'clay_pot', 'counter'], 'durn_mage', 'Runiarz Odvar', 'MERCHANT',
            'durn_magic', 'Mikstury, runy i różdżki. Krasnoludzkie runy nie pękają nawet w bitwie.')
    for b in m_houses:
        house_box(b, ZM, STONE)
    pen = (46, 40)
    for yy in range(pen[1], pen[1] + 6):
        for xx in range(pen[0], pen[0] + 8):
            if xx in (pen[0], pen[0] + 7) or yy in (pen[1], pen[1] + 5):
                if (xx, yy) not in ((pen[0] + 3, pen[1]), (pen[0] + 4, pen[1]), (pen[0] + 3, pen[1] + 5)):
                    c = p.get(ox + xx, oy + yy, z)
                    if c is not None and c[3] == NONE and c[2] == NONE and St.walk(c) and (xx, yy) in M.street:
                        p.obj(ox + xx, oy + yy, z, 'fence')
    pc = (ox + pen[0] + 4, oy + pen[1] + 2)
    c0 = p.get(pc[0], pc[1], z)
    if c0 is None or not St.walk(c0) or c0[2] != NONE or (pen[0] + 4, pen[1] + 2) not in M.cave:
        pc = min(((ox + x, oy + y) for (x, y) in M.street if (x, y) not in M.pillars and abs(x - 46) + abs(y - 42) < 12),
                 key=lambda c: abs(c[0] - ox - 46) + abs(c[1] - oy - 42))
    npcs.append(['durn_petkeeper', 'Opiekun zwierząt Bodvar', 'MERCHANT', CITY, pc[0], pc[1], z, 'durn_pets', '#6a8a3a',
                 'Kruki z górskich turni lecą za krasnoludem i w głąb kopalni. Pupil u boku daje +5% doświadczenia z potworów.'])
    gcell = min(((ox + x, oy + y) for (x, y) in M.street if (x, y) not in M.pillars and abs(x - 42) + abs(y - 33) < 8
                 and (ox + x, oy + y) != pc), key=lambda c: abs(c[0] - ox - 42) + abs(c[1] - oy - 33))
    npcs.append(['durn_guide', 'Przewodnik Dvalin', 'GUIDE', CITY, gcell[0], gcell[1], z, '-', '#8a6a3a',
                 'Kopalnie wokół Durnkaru roją się od odszczepieńców i potworów z głębin. Każde piętro niżej jest groźniejsze.'])
    wx = (ox + 47, oy + 34)
    for (c, o) in ((wx, 'well'), ((ox + 51, oy + 30), 'statue'), ((ox + 44, oy + 30), 'campfire'), ((ox + 49, oy + 38), 'campfire')):
        cc = p.get(c[0], c[1], z)
        if cc is not None and St.walk(cc) and cc[2] == NONE and c not in ((pc), gcell):
            p.obj(c[0], c[1], z, o)
    # ---- pietro -6: kuznia, platnerz, zazaklinacz
    z = ZL
    service(smith, z, STONE, ['forge', 'anvil', 'forge', 'anvil', 'lumber', 'barrel', 'counter'], 'durn_smith', 'Kowal Brokk',
            'MERCHANT', 'durn_arms', 'Topory, młoty i kusze z kuźni Durnkaru. Skupuję też broń i rudę.')
    service(armor, z, STONE, ['showcase'] * 4 + ['counter'], 'durn_armorer', 'Płatnerz Hagar', 'MERCHANT', 'durn_armor',
            'Zbroje kute w żarze lawy: hełmy, kolczugi i tarcze dla każdego wojownika.')
    service(imb, z, STONE, ['bookcase', 'candle', 'altar'], 'durn_imbuer', 'Zaklinacz Runnar', 'IMBUER', None,
            'Runy wyryte w kamieniu trzymają moc nasyceń. Kradzież życia, many, krytyk, żywioły i ochrony.', col='#7050b0')
    for b in l_houses:
        house_box(b, ZL, STONE)
    for (x, y) in ((30, 30), (48, 36), (66, 34), (40, 46), (58, 44), (22, 44)):
        c = p.get(ox + x, oy + y, z)
        if c is not None and St.walk(c) and c[2] == NONE and (ox + x, oy + y) not in reserved and (x, y) in L.street:
            p.obj(ox + x, oy + y, z, 'campfire')

    # ================================================================= schody
    for (za, zb), pts in stair_spots.items():
        for (sx, sy) in pts:
            up = (ox + sx, oy + sy)
            for zz in (za, zb):
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        c = p.get(up[0] + dx, up[1] + dy, zz)
                        if c is None or not St.walk(c) or c[2] != NONE:
                            p.put(up[0] + dx, up[1] + dy, zz, (STREET, NONE, NONE, NONE))
            p.stairs(zb, up, up)                      # na -5 schody w gore do -4; na -4 schody w dol (zb nizsze)
    spots['stairs'] = stair_spots

    # ================================================================= swiatla
    for (zz, lv) in ((ZU, U), (ZM, M), (ZL, L)):
        walls = [(ox + x, oy + y) for (x, y) in sorted(lv.cave) if (x + 2 * y) % 11 == 0]
        for (x, y) in walls[:80]:
            c = p.get(x, y, zz)
            if c is not None and c[2] == NONE and St.walk(c) and (x, y) not in reserved:
                for dx, dy in ((0, -1), (0, 1), (1, 0), (-1, 0)):
                    d = p.get(x + dx, y + dy, zz)
                    if d is not None and d[3] == ROCK:
                        p.obj(x, y, zz, 'campfire') if (x + y) % 3 == 0 else None
                        break
    for lv_z, lvl in ((ZU, U), (ZM, M), (ZL, L)):
        p.pz.append((ox - 13 if lv_z == ZU else ox, oy, ox + CW - 1, oy + CH - 1, lv_z))
    spots['floors'] = {ZU: U, ZM: M, ZL: L}
    return spots, npcs, houses
