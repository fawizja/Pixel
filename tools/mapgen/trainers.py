"""Posagi treningowe offline w swiatyniach (2026-11-12, Darek: "pomniki do skolowania w kazdym miescie w swiatyni;
po nacisnieciu wylogowuje postac jak w prawdziwej Tibii").

Po zbudowaniu miast (generate_continent.finish, przed swiatlami na murach) kazda swiatynia z map.properties dostaje jeden
posag treningowy (obiekt training_statue). Posag stoi w sali swiatyni na wolnym polu podlogi przy scianie, poza dywanem,
z dala od drzwi, oltarza, innych obiektow i NPC, a po jego postawieniu cala sala zostaje spojna (nic nie jest odcieta).
Kolejnosc i dane sa stale - generator daje te same wspolrzedne przy kazdym uruchomieniu.
Gra: GameSession#startOfflineTraining, training/OfflineTraining (wzory z Canary).
"""
from collections import deque

import features as F

NONE = '-'
INTERIOR = {'stone_floor', 'sandstone_floor', 'marble_floor', 'wood_floor', 'frozen_floor'}
STATUE = 'training_statue'
RADIUS = 14          # promien sali swiatyni od jej punktu
MAX_ROOM = 500
BLOCKING = {'altar', 'door', 'door_open', 'depot_locker', 'stairs_up', 'stairs_down', 'ladder', 'throne', STATUE}


def _room(world, start, z):
    """Pola podlogi sali (bez przechodzenia przez drzwi), od start."""
    sx, sy = start
    seen = {start}
    q = deque([start])
    while q and len(seen) < MAX_ROOM:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            c = world.get(n[0], n[1], z)
            if n in seen or c is None or abs(n[0] - sx) > RADIUS or abs(n[1] - sy) > RADIUS:
                continue
            if c[0] in INTERIOR and F.walk(c) and c[2] not in ('door', 'door_open'):
                seen.add(n)
                q.append(n)
    return seen


def _connected(cells):
    cells = set(cells)
    if not cells:
        return True
    start = next(iter(cells))
    seen = {start}
    q = deque([start])
    while q:
        x, y = q.popleft()
        for dx, dy in F.DIRS4:
            n = (x + dx, y + dy)
            if n in cells and n not in seen:
                seen.add(n)
                q.append(n)
    return len(seen) == len(cells)


def place(ctx):
    """Stawia posag w swiatyni kazdego miasta; zwraca liste (miasto, x, y, z)."""
    world = ctx['world']
    npcs = [(int(n[4]), int(n[5]), int(n[6])) for n in ctx['npcs']]
    out = []
    for name, (tx, ty, tz) in ctx['towns']:
        start = (tx, ty)
        c0 = world.get(tx, ty, tz)
        if c0 is None or not F.walk(c0):
            raise AssertionError(f'trainers: temple point of {name} is not walkable')
        room = _room(world, start, tz)
        altars = [(x, y) for (x, y) in room for dx, dy in F.DIRS4
                  if (world.get(x + dx, y + dy, tz) or ('~', NONE, NONE, NONE))[2] == 'altar']
        ax, ay = min(altars, key=lambda p: abs(p[0] - tx) + abs(p[1] - ty)) if altars else start
        best = None
        why = {'room': len(room), 'occupied': 0, 'nowall': 0, 'blocking': 0, 'npc': 0, 'cut': 0}
        # od najluzniejszego wymagania: odstep od obiektow blokujacych i NPC (Chebyshev), potem coraz mniejszy
        for clear, npc_gap in ((2, 2), (1, 2), (1, 1)):
            for (x, y) in sorted(room):
                c = world.get(x, y, tz)
                if c[2] != NONE or c[1] == 'carpet' or c[1] == 'rug':
                    why['occupied'] += 1
                    continue
                if not any((world.get(x + dx, y + dy, tz) or ('~', NONE, NONE, NONE))[3] != NONE for dx, dy in F.DIRS4):
                    why['nowall'] += 1
                    continue                                    # tylko przy scianie
                near = [(world.get(x + dx, y + dy, tz) or ('~', NONE, NONE, NONE))
                        for dx in range(-clear, clear + 1) for dy in range(-clear, clear + 1)]
                if any(n[2] in BLOCKING for n in near):
                    why['blocking'] += 1
                    continue                                    # z dala od drzwi, oltarza, schodow
                if any(max(abs(nx - x), abs(ny - y)) <= npc_gap and nz == tz for nx, ny, nz in npcs):
                    why['npc'] += 1
                    continue
                rest = room - {(x, y)}
                if not _connected(rest):
                    why['cut'] += 1
                    continue
                # najlepiej 4-6 pol od oltarza (widac go z wejscia, nie stoi w przejsciu)
                score = (abs(max(abs(x - ax), abs(y - ay)) - 5), x, y)
                if best is None or score < best[0]:
                    best = (score, x, y)
            if best is not None:
                break
        if best is None:
            raise AssertionError(f'trainers: no place for a training statue in the temple of {name} {why}')
        _s, x, y = best
        c = world.get(x, y, tz)
        world.put(x, y, tz, (c[0], c[1], STATUE, c[3]))
        out.append((name, x, y, tz))
    return out
