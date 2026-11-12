"""Wyspa Gasnacej Gwiazdy: bardzo dluga wyprawa endgame (Darek 2026-11-12: "na wyspie premium bardzo dlugi quest z
trudnymi potworkami, kilka sekcji i bossow jak w Inquisition, poziom 300+; po nim w Varden kupno wszystkich blogoslawienstw").

Wyspa lezy na otwartym morzu i jest osiagalna tylko statkiem (przewoznik star_captain, konta premium). Na wyspie:
  * dziedziniec z przystania, Straznik Pieczeci (star_warden, zadania) i Swiatynia Pieczeci za drzwiami poziomu 300;
  * w swiatyni siedem portali do sekcji (portal k otwiera pokonanie bossa sekcji k-1) i schody do sali nagrod pod nia (-1)
    za bramą zadania ``star_final`` (jedenascie skrzyn, z grupy wolno otworzyc jedna - wybor jednego elementu ekwipunku);
  * siedem sekcji pod morzem, kazda na trzech pietrach (-1..-3): F1 komora wejsciowa z portalem powrotnym, jaskinie
    i budka bramna (drzwi poziomu 300 + brama zadania star_mK), schody w dol; F2 jaskinie; F3 jaskinie, sala bossa,
    drzwi za bossem (brama bossa) i alkowa z portalem do nastepnej sekcji. Sekcja 7 (Komnata Pozeracza Swiatla)
    nie ma zadania bramnego - boss Noctharion zamyka Pieczec, portal z alkowy wraca do swiatyni.
Bramy (QuestGate, star_quest.csv) nie sa widoczne dla siatki dostepu: wszystko jest osiagalne od przystani, a testy
Javy otwieraja bramy flagami postaci. Pieszo z Varden na wyspe nie ma drogi (wyspa na morzu).
"""
import math
import random

import features as F
import isles as I
import structures as St

NONE = '-'
SEED = 20261201
ISLAND = 'Wyspa Gasnącej Gwiazdy'
TEMPLE = 'Świątynia Pieczęci'
REWARD_ROOM = 'Komnata Nagród Gwiazdy'
LEVEL = 300
SURFACE_ORIGIN = (80, 770)
IW, IH = 92, 46
SW, SH = 72, 58
GROUP = 'star_reward'
FINAL_MISSION = 'star_final'

# nazwa, klucz, styl jaskin, potwory (pierwszy = cel zadania), boss, zadanie bramne, poziomy regionu, miejsce (lewy gorny rog planu)
SECTIONS = [
    dict(key='ash', name='Cytadela Popiołu', style='fire', mons=['ash_penitent', 'cinder_revenant'], boss='vorgrim',
         mission='star_m1', levels=(310, 380), at=(12, 850)),
    dict(key='rime', name='Klasztor Szronu', style='ice', mons=['rimebound_monk', 'hoarfrost_warden'], boss='ysmera',
         mission='star_m2', levels=(350, 440), at=(90, 850)),
    dict(key='slag', name='Kuźnia Żużlu', style='ruin', mons=['slag_forgehand', 'molten_cultist'], boss='kharzul',
         mission='star_m3', levels=(395, 490), at=(168, 850)),
    dict(key='thorn', name='Ogrody Cierni', style='moss', mons=['thorn_maiden', 'spore_stalker'], boss='vessara',
         mission='star_m4', levels=(440, 540), at=(246, 850)),
    dict(key='mirror', name='Sale Zwierciadeł', style='crystal', mons=['mirror_acolyte', 'glass_phantom'], boss='ilmora',
         mission='star_m5', levels=(490, 600), at=(12, 920)),
    dict(key='maw', name='Paszcza Głodu', style='core', mons=['maw_crawler', 'gore_harbinger'], boss='ugrath',
         mission='star_m6', levels=(540, 660), at=(90, 920)),
    dict(key='void', name='Komnata Pożeracza Światła', style='crypt', mons=['dimmed_seraph', 'gore_harbinger'],
         boss='noctharion', mission=None, levels=(575, 720), at=(168, 920)),
]
REWARDS = ['star_blade', 'star_axe', 'star_maul', 'star_bow', 'star_wand', 'star_rod', 'star_fists', 'star_plate',
           'star_aegis', 'star_spellbook', 'star_amulet']
PORTAL_XS = [33 + 4 * i for i in range(7)]       # portale w swiatyni (lokalnie, rzad y = 8)
PORTAL_Y = 8
ARRIVE = (6, 29)                                  # portal powrotny na F1 sekcji
GATE_DOOR = (41, 29)                              # drzwi budki bramnej na F1
BOSS_DOOR = (62, 29)                              # drzwi za bossem na F3
ALCOVE_PORTAL = (67, 29)
BOSS_AT = (51, 29)


def _noise(a, seed):
    return (1 + 0.07 * math.sin(3 * a + seed) + 0.05 * math.sin(5 * a + 2 * seed) + 0.03 * math.sin(9 * a + seed))


# ====================================================================== powierzchnia: dziedziniec, swiatynia, sala nagrod
def island_plan():
    p = St.Plan(ISLAND)
    rng = random.Random(SEED)
    cx, cy, rx, ry = IW // 2, IH // 2, IW // 2 - 2, IH // 2 - 1
    land = set()
    for y in range(IH):
        for x in range(IW):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            a = math.atan2(dy, dx)
            r = math.hypot(dx, dy)
            if r <= _noise(a, 1.3) * 0.98:
                land.add((x, y))
                edge = any((x + a_, y + b_) not in land and not (0 <= x + a_ < IW and 0 <= y + b_ < IH)
                           for a_, b_ in F.DIRS4)
                ground = 'sand' if r > _noise(a, 1.3) * 0.88 else 'grass'
                p.put(x, y, 0, (ground, NONE, NONE, NONE))
    # sciezki: od przystani (zachod) i od swiatyni na poludnie
    for x in range(6, 47):
        for y in (27, 28):
            if (x, y) in land:
                p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    for y in range(23, 38):
        for x in (45, 46, 47):
            if (x, y) in land:
                p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    # swiatynia: portale na polnocy, brama zadania do sali nagrod na wschodzie, wejscie (drzwi poziomu) na poludniu
    tx0, ty0, tw, th = 30, 6, 33, 17
    p.box(0, tx0, ty0, tw, th, 'marble_floor', 'stone_wall', doors=[], gaps=[])
    door = (46, ty0 + th - 1)
    p.level_door(0, door, LEVEL, floor='marble_floor')
    for x in PORTAL_XS:
        p.obj(x, PORTAL_Y, 0, 'boss_portal')
    for (x, y) in ((31, 7), (61, 7), (31, 21), (61, 21), (40, 15), (52, 15)):
        p.obj(x, y, 0, 'crystal')
    # sala nagrod pod swiatynia (-1): schody w dol przy wschodniej scianie, przedsionek i brama zadania do sali (sciana x = 62)
    p.stairs_down(0, (60, 14), (58, 14))
    p.box(-1, 54, 9, 9, 11, 'marble_floor', 'stone_wall')
    rx0, ry0, rw, rh = 62, 9, 19, 11
    p.box(-1, rx0, ry0, rw, rh, 'marble_floor', 'stone_wall')
    gate = (62, 14)
    p.level_door(-1, gate, LEVEL, floor='marble_floor')
    spots = [(64 + 2 * i, 10) for i in range(8)] + [(66, 18), (70, 18), (74, 18)]
    for (x, y), item in zip(spots, REWARDS):
        p.chests.append((x, y, -1, 1000, item, 1))
    for (x, y) in ((78, 14), (65, 14), (56, 10), (56, 18)):
        p.obj(x, y, -1, 'crystal')
    p.regions.append((REWARD_ROOM, 54, 9, 80, 19, -1, -1, LEVEL, 760))
    # dekoracje dziedzinca
    keep = {(x, y) for x in range(28, 66) for y in range(4, 24)} | {(46, y) for y in range(22, 38)}
    for (x, y) in sorted(land):
        c = p.get(x, y, 0)
        if c is None or c[2] != NONE or c[0] not in ('grass', 'sand') or (x, y) in keep:
            continue
        if abs(x - 46) <= 3 and y >= 22 and y <= 36:
            continue
        rr = rng.random()
        if c[0] == 'grass' and rr < 0.07:
            p.obj(x, y, 0, 'tree')
        elif c[0] == 'grass' and rr < 0.10:
            p.obj(x, y, 0, 'bush')
        elif rr < 0.115:
            p.obj(x, y, 0, 'rock')
    p.npcs.append(['star_warden', 'Warden Thessaly', 'GUIDE', ISLAND, 49, 26, 0, '-', '#c0c8e0',
                   'Gwiazda nad nami gaśnie. Strzegę Pieczęci i mam zadania dla tych od poziomu 300.'])
    p.pz.append((0, 0, IW - 1, IH - 1, 0))
    p.regions.append((ISLAND, 0, 0, IW - 1, IH - 1, 0, 0, LEVEL, 760))
    return p


# ====================================================================== sekcje
def _spawn_cells(p, z, pts, avoid, mons, count, respawn, radius=3, rng=None):
    n = 0
    for i, q in enumerate(pts):
        if any(abs(q[0] - a) + abs(q[1] - b) < 8 for (a, b) in avoid):
            continue
        cells = [(abs(c[0] - q[0]) + abs(c[1] - q[1]), c) for c, v in p.f[z].items()
                 if St.walk(v) and v[2] == NONE and abs(c[0] - q[0]) <= 3 and abs(c[1] - q[1]) <= 3]
        if not cells:
            continue
        _d, c = min(cells)
        p.spawn(mons[i % len(mons)], c[0], c[1], z, radius=radius, count=count + (i % 2), respawn=respawn)
        n += 1
    return n


def section_plan(k, sec, seed):
    style = sec['style']
    floor_s, wall_s = F.CAVE_STYLES[style][0], F.CAVE_STYLES[style][1]
    gatehouse = sec['mission'] is not None
    for attempt in range(40):
        rng = random.Random(seed + attempt * 977)
        p = St.Plan(sec['name'])
        try:
            if gatehouse:
                L = I.rooms_floor(p, -1, (0, 0, 37, SH), ARRIVE, 9, style, rng, extra=[(30, 29)], down=False, gap=8)
                R = I.rooms_floor(p, -1, (41, 0, 31, SH), (46, 29), 7, style, rng, down=True, gap=8)
                p.box(-1, 37, 24, 5, 11, 'stone_floor', 'stone_wall', gaps=[(37, 29)])
                p.corridor(-1, [(x, 29) for x in range(30, 38)], floor_s, wall_s)
                p.corridor(-1, [(x, 29) for x in range(41, 47)], floor_s, wall_s)
                p.level_door(-1, GATE_DOOR, LEVEL, floor='stone_floor')
                dn1 = R['down']
                chain1 = L['chain'] + R['chain']
                f1_front = L['chain']
            else:
                R = I.rooms_floor(p, -1, (0, 0, SW, SH), ARRIVE, 11, style, rng, down=True, gap=8)
                dn1 = R['down']
                chain1 = R['chain']
                f1_front = R['chain']
            p.obj(ARRIVE[0], ARRIVE[1], -1, 'boss_portal')
            B = I.rooms_floor(p, -2, (0, 0, SW, SH), dn1, 11, style, rng, down=True, down_ok=lambda q: q[0] < 36, gap=9)
            dn2 = B['down']
            C = I.rooms_floor(p, -3, (0, 0, 40, SH), dn2, 7, style, rng, extra=[(34, 29)], down=False, gap=8)
            p.box(-3, 41, 19, 22, 21, 'marble_floor', 'stone_wall', gaps=[(41, 29)])
            p.corridor(-3, [(x, 29) for x in range(34, 41)], floor_s, wall_s)
            p.box(-3, 62, 25, 9, 9, 'marble_floor', 'stone_wall')
            p.level_door(-3, BOSS_DOOR, LEVEL, floor='marble_floor')
            p.obj(ALCOVE_PORTAL[0], ALCOVE_PORTAL[1], -3, 'boss_portal')
            for (x, y) in ((45, 23), (45, 35), (58, 23), (58, 35)):
                p.obj(x, y, -3, 'crystal')
            p.stairs(-2, dn1, dn1)
            p.stairs(-3, dn2, dn2)
            p.finish()
        except AssertionError:
            continue
        mons = sec['mons']
        avoid1 = [ARRIVE, dn1, GATE_DOOR]
        # F1: przed bramą wyłącznie cel zadania (mons[0]) - 50-70 sztuk do ubicia bez przekraczania bramy
        _spawn_cells(p, -1, f1_front[1:], avoid1, [mons[0]], 3, 90)
        if gatehouse:
            _spawn_cells(p, -1, R['chain'][1:], avoid1, mons, 3, 110)
        _spawn_cells(p, -2, B['chain'][1:], [dn1, dn2], mons[::-1] + mons, 3, 120)
        _spawn_cells(p, -3, C['chain'][1:], [dn2, (34, 29)], mons, 3, 130)
        for q in ((46, 22), (46, 36), (57, 22), (57, 36)):
            p.spawn(mons[(q[0] + q[1]) % 2], q[0], q[1], -3, radius=2, count=2, respawn=300)
        p.spawn(sec['boss'], BOSS_AT[0], BOSS_AT[1], -3, radius=2, count=1, respawn=3600)
        # skrzynie w bocznych komorach F2 i F3 (po jednej)
        for z, info, item, cnt in ((-2, B, 'ultimate_health_potion', 4), (-3, C, 'ultimate_mana_potion', 4)):
            for q in info['side']:
                spot = I.chest_spot(p, z, q)
                if spot is not None:
                    p.chests.append((spot[0], spot[1], z, 1500 + 300 * k, item, cnt))
                    break
        for z in (-1, -2, -3):
            I.floor_region(p, sec['name'], z, sec['levels'])
        p.snap()
        # dostep: wszystko osiagalne od portalu powrotnego, a za bramami tylko przez bramy
        start = [(ARRIVE[0] + 1, ARRIVE[1], -1)]
        seen = p.reach(start, True)
        closed = p.reach(start, False)
        need = [(s[1], s[2], s[3]) for s in p.spawns] + [(ALCOVE_PORTAL[0], ALCOVE_PORTAL[1], -3), (dn1[0], dn1[1], -1)]
        if any(q not in seen for q in need):
            continue
        if any(not any((c[0] + dx, c[1] + dy, c[2]) in seen for dx in (-1, 0, 1) for dy in (-1, 0, 1)) for c in p.chests):
            continue
        if (ALCOVE_PORTAL[0], ALCOVE_PORTAL[1], -3) in closed or (gatehouse and (BOSS_AT[0], BOSS_AT[1], -3) in closed):
            continue
        if gatehouse and ((dn1[0], dn1[1], -1) in closed or (46, 29, -1) in closed):
            continue
        return p, dict(dn1=dn1, dn2=dn2)
    raise AssertionError(f'{sec["name"]}: section not built')


# ====================================================================== swiat
def build(ctx, cost, helpers):
    import realms
    world = ctx['world']
    S = len(world.floors[0])
    mark = helpers['mark']
    ox, oy = SURFACE_ORIGIN
    for y in range(oy - 6, oy + IH + 6):
        for x in range(ox - 12, ox + IW + 12):
            c = world.get(x, y)
            assert c is not None and c[0] == 'water' and c[2] == NONE, f'citadel: surface not free at {(x, y)} {c}'
    St.clear_surface(ctx, ox - 10, oy - 10, ox + IW + 10, oy + IH + 10)
    hall = island_plan()
    Wh = St.stamp(ctx, hall, SURFACE_ORIGIN, mark)
    # przystan i przewoznik (zachodni brzeg)
    dock = F.small_dock(world, Wh(12, 27, 0), F.W)
    assert dock is not None, 'citadel: no dock'
    spot, rect = dock
    ctx['pz'].append((rect[0], rect[1], rect[2], rect[3], 0))
    ctx['npcs'].append(['star_captain', 'Captain Brannoc', 'CAPTAIN', ISLAND, spot[0], spot[1], 0, '-', '#4a6a8a',
                        'Pływam na kontynent. Tylko konta premium.'])
    for partner in ('varden_captain', 'qas_captain', 'valmir_captain'):
        ctx['routes'].append(('star_captain', partner))
    # sekcje
    secs = []
    gates = []
    for k, sec in enumerate(SECTIONS):
        plan, info = section_plan(k, sec, SEED + 101 * (k + 1))
        W = St.stamp(ctx, plan, sec['at'], mark, per_floor=True)
        secs.append(dict(sec=sec, plan=plan, W=W, **info))
    transitions = ctx['transitions']
    hall_land = []
    for i, x in enumerate(PORTAL_XS):
        a = Wh(x, PORTAL_Y + 1, 0)
        hall_land.append(a)
    for k, s in enumerate(secs):
        W, sec = s['W'], s['sec']
        pa = W(ARRIVE[0], ARRIVE[1], -1)
        land = W(ARRIVE[0] + 1, ARRIVE[1], -1)
        hp = Wh(PORTAL_XS[k], PORTAL_Y, 0)
        transitions.append((hp[0], hp[1], 0, 'TUNNEL', land[0], land[1], -1))
        transitions.append((pa[0], pa[1], -1, 'TUNNEL', hall_land[k][0], hall_land[k][1], 0))
        if k > 0:
            gates.append((hp[0], hp[1], 0, 'boss:' + SECTIONS[k - 1]['boss']))
        ap = W(ALCOVE_PORTAL[0], ALCOVE_PORTAL[1], -3)
        if k + 1 < len(secs):
            nw = secs[k + 1]['W']
            nl = nw(ARRIVE[0] + 1, ARRIVE[1], -1)
            transitions.append((ap[0], ap[1], -3, 'TUNNEL', nl[0], nl[1], -1))
        else:
            transitions.append((ap[0], ap[1], -3, 'TUNNEL', hall_land[3][0], hall_land[3][1], 0))
        if sec['mission']:
            gd = W(GATE_DOOR[0], GATE_DOOR[1], -1)
            gates.append((gd[0], gd[1], -1, 'quest:' + sec['mission']))
        bd = W(BOSS_DOOR[0], BOSS_DOOR[1], -3)
        gates.append((bd[0], bd[1], -3, 'boss:' + sec['boss']))
    rg = Wh(62, 14, -1)
    gates.append((rg[0], rg[1], -1, 'quest:' + FINAL_MISSION))
    ctx['star_gates'] = gates
    ctx['star_choices'] = []
    for (x, y, z, gold, item, n) in hall.chests:
        a = Wh(x, y, z)
        ctx['star_choices'].append((GROUP, a[0], a[1], z, item))
    # Herold w swiatyni Varden
    herald = None
    taken = {(int(n[4]), int(n[5])) for n in ctx['npcs'] if int(n[6]) == 0}
    # Przycisk "Mow" otwiera najblizszego NPC, wiec herold stoi >= 4 pola (Chebyshev) od kazdego innego NPC z z=0:
    # na kobiercu w apsydzie za oltarzem (kaplan stoi na (501,498), przewodnik na (496,499)).
    for (x, y) in [(498, 494), (499, 494), (498, 495), (499, 495)]:
        c = world.get(x, y)
        if min([max(abs(x - tx), abs(y - ty)) for (tx, ty) in taken] or [99]) < 4:
            continue
        if F.walk(c) and c[2] == NONE and (x, y) not in taken and not any(
                t[0] == x and t[1] == y and t[2] == 0 for t in ctx['transitions']):
            herald = (x, y)
            break
    assert herald is not None, 'citadel: no place for the herald in the Varden temple'
    ctx['npcs'].append(['varden_herald', 'Herald Matthias', 'HERALD', 'Varden', herald[0], herald[1], 0, '-', '#d8c060',
                        'Witaj w świątyni. Zapytaj mnie o błogosławieństwa.'])
    # ---------------------------------------------------------------- weryfikacja swiata: od przystani wszystko, od Varden nic
    reach_open = _reach(ctx, [(spot[0], spot[1], 0)], set())
    gate_cells = {(g[0], g[1], g[2]) for g in gates}
    dl = {(d[0], d[1], d[2]) for d in ctx.get('level_doors', [])}
    for s in secs:
        W = s['W']
        for sp in s['plan'].spawns:
            a = W(sp[1], sp[2], sp[3])
            assert (a[0], a[1], sp[3]) in reach_open, f'citadel: spawn {sp} unreachable from the dock'
        for c in s['plan'].chests:
            a = W(c[0], c[1], c[2])
            assert any((a[0] + dx, a[1] + dy, c[2]) in reach_open for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f'chest {c}'
    for (grp, x, y, z, item) in ctx['star_choices']:
        assert any((x + dx, y + dy, z) in reach_open for dx in (-1, 0, 1) for dy in (-1, 0, 1)), f'reward chest {x},{y}'
    # bramy zamkniete: ani sekcja 2, ani sala nagrod nie sa osiagalne
    reach_closed = _reach(ctx, [(spot[0], spot[1], 0)], gate_cells)
    s2 = secs[1]['W'](ARRIVE[0], ARRIVE[1], -1)
    assert (s2[0], s2[1], -1) not in reach_closed, 'citadel: section 2 reachable without defeating the guardian'
    assert (rg[0] + 3, rg[1], -1) not in reach_closed, 'citadel: reward room reachable without the quest'
    # pieszo z Varden nie ma drogi na wyspe
    g = realms.Graph(ctx, S)
    temple = dict(ctx['towns'])['Varden']
    start = g.idx(temple[0], temple[1], 0)
    assert g.path(start, {g.idx(spot[0], spot[1], 0)}) is None, 'citadel: the island is reachable on foot from Varden'
    out = dict(origin=SURFACE_ORIGIN, dock=spot, warden=Wh(49, 26, 0), herald=herald, temple_door=Wh(46, 22, 0),
               reward_gate=rg, reward_room=Wh(70, 14, -1), vault_stairs=Wh(60, 14, 0),
               sections=[dict(name=s['sec']['name'], boss=s['sec']['boss'], start=s['W'](ARRIVE[0], ARRIVE[1], -1),
                              boss_at=s['W'](BOSS_AT[0], BOSS_AT[1], -3), gate=s['W'](GATE_DOOR[0], GATE_DOOR[1], -1),
                              boss_door=s['W'](BOSS_DOOR[0], BOSS_DOOR[1], -3),
                              stairs1=s['W'](s['dn1'][0], s['dn1'][1], -1), stairs2=s['W'](s['dn2'][0], s['dn2'][1], -2))
                         for s in secs],
               portals=[Wh(x, PORTAL_Y, 0) for x in PORTAL_XS], gates=len(gates),
               spawns=sum(len(s['plan'].spawns) for s in secs))
    ctx['citadel'] = out
    return out


def _reach(ctx, starts, blocked):
    """Pola osiagalne (4 kierunki i przejscia wszystkich typow); blocked - pola bram (nieprzechodnie)."""
    from collections import deque
    import features as F_
    world = ctx['world']
    tr = {(t[0], t[1], t[2]): (t[4], t[5], t[6]) for t in ctx['transitions']}
    seen = set(starts)
    q = deque(starts)
    while q:
        x, y, z = q.popleft()
        nxt = [(x + dx, y + dy, z) for dx, dy in F_.DIRS4]
        if (x, y, z) in tr:
            nxt.append(tr[(x, y, z)])
        for n in nxt:
            if n in seen or n in blocked:
                continue
            if F_.walk(world.get(*n)):
                seen.add(n)
                q.append(n)
    return seen
