"""Kanaly pod miastami (2026-10-27, Darek: "kratki sciekowe z podziemiami i slabymi niskolewelowymi potworami").

Pod ulicami miasta (pietro -1) biegna kanaly: kamienne chodniki z rynsztokiem posrodku szerszych ulic i kladkami
nad nim. Na ulicach stoja kratki sciekowe - wejscie na kratke sprowadza do kanalu, drabina pod kratka wyprowadza
na ulice. W kanalach mieszkaja szczury i inne slabe potwory (poziomy 3-20).
"""
from collections import deque

NONE = '-'
FLOOR, WALL = 'stone_floor', 'stone_wall'
DIRS4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def sewers(world, rect, streets, avoid, rng, walk, z=-1, min_part=24, min_mask=40):
    """Kanaly pod ulicami w prostokacie rect. streets: podloza ulic; avoid(x, y): pola, pod ktorymi kanalu byc
    nie moze (stare podziemia, rdzen Varden). Zwraca (pola chodnikow, kratki [(x, y)] ) albo None."""
    x0, y0, x1, y1 = rect
    occ = world.occ[z]

    def free_below(x, y):
        if avoid(x, y):
            return False
        for b in (-1, 0, 1):
            for a in (-1, 0, 1):
                if world.get(x + a, y + b, z) is not None:
                    return False
        return True
    mask = set()
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            c = world.get(x, y, 0)
            if c is not None and c[0] in streets and c[3] == NONE and free_below(x, y):
                mask.add((x, y))
    if len(mask) < min_mask:
        return None
    # rynsztok: pola z pelnym sasiedztwem (srodek szerszych ulic); co kilka pol kladka
    channel = {p for p in mask if all((p[0] + a, p[1] + b) in mask for a in (-1, 0, 1) for b in (-1, 0, 1))}
    def run(x, y, dx, dy):
        n = 1
        for s in (1, -1):
            k = 1
            while (x + s * k * dx, y + s * k * dy) in channel:
                n += 1
                k += 1
        return n
    cells = {}
    for (x, y) in mask:
        if (x, y) in channel:
            # kladka w poprzek rynsztoku co 10 pol (rynsztok poziomy - kladka pionowa i odwrotnie)
            bridge = x % 10 == 0 if run(x, y, 1, 0) >= run(x, y, 0, 1) else y % 10 == 0
            cells[(x, y)] = ('bridge', NONE, NONE, NONE) if bridge else ('water', NONE, NONE, NONE)
        else:
            r = rng.random()
            deco = 'cobweb' if r < 0.02 else NONE
            obj = 'barrel' if r > 0.985 else (('crate', 'sack', 'rubble')[int(r * 1000) % 3] if r > 0.975 else NONE)
            cells[(x, y)] = (FLOOR, deco, obj, NONE)
    # najwieksza spojna czesc chodnikow
    walkable = {p for p, c in cells.items() if walk(c)}
    best = set()
    comps = []
    seen = set()
    for p in sorted(walkable):
        if p in seen:
            continue
        comp = {p}
        q = deque([p])
        seen.add(p)
        while q:
            x, y = q.popleft()
            for a, b in DIRS4:
                n = (x + a, y + b)
                if n in walkable and n not in seen:
                    seen.add(n)
                    comp.add(n)
                    q.append(n)
        if len(comp) > len(best):
            best = comp
        comps.append(comp)
    # kazdy wiekszy kawalek chodnikow to osobny odcinek kanalu z wlasna kratka (kratki w roznych czesciach miasta)
    best = set(best)
    for comp in comps:
        if len(comp) >= min_part:
            best |= comp
    keep = {p for p in cells if p in best or (cells[p][0] == 'water' and any((p[0] + a, p[1] + b) in best for a, b in DIRS4))}
    for (x, y) in keep:
        world.put(x, y, z, cells[(x, y)])
    # odciete kawalki chodnikow w obrebie kanalu zalewa woda (zamiast zamurowanych wysepek)
    for (x, y) in cells:
        if (x, y) not in keep and any((x + a, y + b) in keep for a in (-1, 0, 1) for b in (-1, 0, 1)):
            world.put(x, y, z, ('water', NONE, NONE, NONE))
    ring = {(x + a, y + b) for (x, y) in keep for b in (-1, 0, 1) for a in (-1, 0, 1)
            if world.get(x + a, y + b, z) is None}
    for (x, y) in sorted(ring):
        # dziura otoczona sama woda (pod pomnikiem, studnia na ulicy) - woda, inaczej mur
        near_walk = any((x + a, y + b) in best for a in (-1, 0, 1) for b in (-1, 0, 1))
        world.put(x, y, z, (FLOOR, NONE, NONE, WALL) if near_walk else ('water', NONE, NONE, NONE))
    # zamkniete dziury wewnatrz kanalu (pola bez niczego otoczone woda) tez zalewa woda
    bx0, by0 = min(p[0] for p in keep) - 2, min(p[1] for p in keep) - 2
    bx1, by1 = max(p[0] for p in keep) + 2, max(p[1] for p in keep) + 2
    outside = set()
    q = deque((x, y) for x in range(bx0, bx1 + 1) for y in (by0, by1)
              if world.get(x, y, z) is None)
    q.extend((x, y) for y in range(by0, by1 + 1) for x in (bx0, bx1) if world.get(x, y, z) is None)
    outside.update(q)
    while q:
        x, y = q.popleft()
        for a, b in DIRS4:
            n = (x + a, y + b)
            if bx0 <= n[0] <= bx1 and by0 <= n[1] <= by1 and n not in outside and world.get(*n, z) is None:
                outside.add(n)
                q.append(n)
    holes = set()
    for y in range(by0, by1 + 1):
        for x in range(bx0, bx1 + 1):
            if world.get(x, y, z) is None and (x, y) not in outside and (x, y) not in holes:
                comp, q = {(x, y)}, deque([(x, y)])
                while q:
                    cx, cy = q.popleft()
                    for a, b in DIRS4:
                        n = (cx + a, cy + b)
                        if n not in comp and world.get(*n, z) is None:
                            comp.add(n)
                            q.append(n)
                holes |= comp
                if len(comp) <= 16:  # male dziury; duze zamkniete obszary pod kwartalami zostaja puste
                    for (hx, hy) in comp:
                        world.put(hx, hy, z, ('water', NONE, NONE, NONE))
    for (x, y) in keep:
        for b in range(-3, 4):
            for a in range(-3, 4):
                if 0 <= y + b < occ.shape[0] and 0 <= x + a < occ.shape[1]:
                    occ[y + b, x + a] = True
    # pochodnie na chodnikach przy murach co kilkanascie pol (swiatlo w kanale)
    lit = []
    for (x, y) in sorted(best):
        c = world.get(x, y, z)
        if c[2] != NONE or not any(world.get(x + a, y + b, z)[3] != NONE for a, b in DIRS4):
            continue
        if all(abs(x - lx) + abs(y - ly) >= 9 for lx, ly in lit):
            world.put(x, y, z, (c[0], c[1], 'wall_torch', NONE))
            lit.append((x, y))
    walk_cells = sorted(p for p in best if world.get(*p, z)[2] == NONE)
    return walk_cells


def grates(world, walk_cells, streets, taken, rng, count, walk, spacing=14, z=-1):
    """Kratki na ulicach nad chodnikami kanalu: pole ulicy bez obiektu z wolnymi sasiadami, co najmniej spacing pol
    od innych kratek. Zwraca liste (kratka, pole drabiny pod nia, wyjscie na ulice, zejscie w kanale)."""
    out = []
    cand = list(walk_cells)
    rng.shuffle(cand)
    for (x, y) in cand:
        if len(out) >= count:
            break
        c = world.get(x, y, 0)
        if c is None or c[0] not in streets or c[2] != NONE or c[1] != NONE or (x, y) in taken:
            continue
        if any(abs(x - gx) + abs(y - gy) < spacing for (gx, gy), *_ in out):
            continue
        up = [(x + a, y + b) for a, b in DIRS4 if walk(world.get(x + a, y + b, 0))
              and world.get(x + a, y + b, 0)[2] == NONE and (x + a, y + b) not in taken]
        down = [(x + a, y + b) for a, b in DIRS4 if walk(world.get(x + a, y + b, z))
                and world.get(x + a, y + b, z)[2] == NONE]
        # kratka nie moze zamykac przejscia: wszystkie 8 sasiednich pol wolne (obejscie dookola bez scinania rogow)
        around = [world.get(x + a, y + b, 0) for a in (-1, 0, 1) for b in (-1, 0, 1) if a or b]
        if len(up) < 4 or not down or not all(walk(c) and c[2] == NONE for c in around):
            continue
        out.append(((x, y), (x, y), up[0], down[0]))
    for (g, ladder, up, down) in out:
        c = world.get(*g, 0)
        world.put(g[0], g[1], 0, (c[0], NONE, 'sewer_grate', NONE))
        world.put(ladder[0], ladder[1], z, (FLOOR, NONE, 'ladder', NONE))
    return out
