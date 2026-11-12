#!/usr/bin/env python3
"""Rod pieciu smokow (2026-11-12): zielony, lodowy, czarny, zloty i (tylko losowo, w grze) bialy.

Smoki: ``emerald_dragon`` (zielony, najslabszy), ``hoarfrost_dragon`` (lodowy, polnoc), ``coal_dragon`` (czarny),
``dune_drake`` (zloty), ``pearl_dragon`` (bialy - bez stalych spawnow: pojawia sie rzadko na smoczych lowiskach,
MonsterManager.rollRare). Smocze wzgorza (hills.py) maja wlasne listy; tu dopasowanie reszty swiata:

* co trzeci spawn ``ice_golem`` bez stada (lodowe jaskinie i szczyty polnocy) staje sie ``hoarfrost_dragon`` - szronowym
  smokiem na polnocy (``ice_dragon`` zostaje jak dotad);
* co drugi spawn ``magma_drake`` na pietrach 0..-2 (wulkan, wyspa i wzgorza) staje sie ``coal_dragon`` (czarny smok z zarem);
* spawny zlotego (``dune_drake``) zostaja, jak byly (rowniki i pustynie, glebokie pietra wzgorz).
Zamiana jest deterministyczna (numer wystapienia), bez losowania - mapa wychodzi taka sama. ``apply`` wola
generate_continent.finish przed route_levels, ktore i tak obnizy kazdy spawn ponad pasem poziomu drogi.
"""

MAGMA_MAX_DEPTH = -2   # pietra 0, -1, -2 (glebiej i w stozku wulkanu zostaja magmowe smoki)
MAGMA_STEP = 2


GOLEM_STEP = 3          # co trzeci spawn ice_golem (poza stadami) w lodowych grotach staje sie szronowym smokiem


def apply(ctx):
    """Zamienia czesc spawnow ice_golem / magma_drake na szronowe i weglowe smoki oraz podmienia smoki na Smoczych
    Wzgorzach (HILLS_REPLACE); zwraca (lodowe, czarne, wzgorza). Spawny ze stadem (``stado``: lowiska dla druzyn, testy
    wymagaja tam poziomu >= 260-300) zostaja bez zmian; `fits` pilnuje progu regionu jak GameWorldTest."""
    regions = list(ctx['regions']) + list(ctx.get('regions_new', []))
    levels = _monster_levels()

    def fits(key, x, y, z):
        for r in regions:
            if len(r) > 8 and r[1] <= x <= r[3] and r[2] <= y <= r[4] and r[5] <= z <= r[6] and levels[key] < 0.8 * r[7]:
                return False
        return True
    hb = [r for r in regions if r[0] in (HILLS_NAME, 'Smocze Pieczary')]
    box = (min(r[1] for r in hb), min(r[2] for r in hb), max(r[3] for r in hb), max(r[4] for r in hb)) if hb else None
    ice = magma = hills = 0
    n_golem = n_magma = 0
    counts = {}
    out = []
    for line in ctx['spawns']:
        if line.startswith('#') or not line.strip():
            out.append(line)
            continue
        f = line.split(';')
        x, y, z = int(f[1]), int(f[2]), int(f[3])
        pack = 'stado' in f
        if f[0] == 'ice_golem' and not pack:
            n_golem += 1
            if n_golem % GOLEM_STEP == 0 and fits('hoarfrost_dragon', x, y, z):
                f[0] = 'hoarfrost_dragon'
                ice += 1
        elif f[0] == 'magma_drake' and MAGMA_MAX_DEPTH <= z <= 0 and not pack:
            n_magma += 1
            if n_magma % MAGMA_STEP == 0 and fits('coal_dragon', x, y, z):
                f[0] = 'coal_dragon'
                magma += 1
        elif box and box[0] <= x <= box[2] and box[1] <= y <= box[3] and z in HILLS_REPLACE:
            rule = HILLS_REPLACE[z].get(f[0])
            if rule:
                k = (z, f[0])
                counts[k] = counts.get(k, 0) + 1
                if counts[k] % rule[1] == 0 and fits(rule[0], x, y, z):
                    f[0] = rule[0]
                    hills += 1
        out.append(';'.join(f))
    ctx['spawns'] = out
    return ice, magma, hills


def _monster_levels():
    import os
    levels = {}
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'shared', 'src', 'main', 'resources', 'data',
                           'monsters.csv'), encoding='utf-8') as f:
        for line in f:
            p = line.split(';')
            if len(p) > 3 and not line.startswith('#'):
                levels[p[0]] = int(p[2])
    return levels


# ---------------------------------------------------------------------------------- smocze wzgorza (gotowy swiat)
HILLS_NAME = 'Wzgórza Smoków'
HILLS_REPLACE = {   # pietro -> {stary potwor: (nowy potwor, co ktory spawn zamieniamy)}
    # zielony smok (poziom 205) tylko na powierzchni i na -3: pietro -1 ma potwory do 200, a droga do -2 prowadzi przez piskleta
    0: {'scale_dragon': ('emerald_dragon', 1)},
    -2: {'crag_dragon': ('coal_dragon', 3)},
    -3: {'scale_dragon': ('emerald_dragon', 1), 'crag_dragon': ('coal_dragon', 2)},
    -4: {'crag_dragon': ('coal_dragon', 2)},
}


def hills_box(world_dir):
    """Prostokat Wzgorz Smokow (x0, y0, x1, y1) z regionow (obejmuje pieczary pod nimi)."""
    import os
    boxes = []
    with open(os.path.join(world_dir, 'map.properties'), encoding='utf-8') as f:
        for line in f:
            if line.startswith('regions='):
                for r in line[len('regions='):].strip().split('|'):
                    p = r.split(';')
                    if p[0] in (HILLS_NAME, 'Smocze Pieczary'):
                        boxes.append(tuple(int(v) for v in p[1:5]))
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


# gotowy swiat: spawny sprawdzone pojedynczo przez route_levels (0 naruszen); (x, y, z) srodka spawnu
ICE_SPOTS = {(771, 123, -3), (762, 104, -4), (697, 231, -2), (810, 108, 0)}       # ice_golem (120) -> szronowy smok (240)
COAL_SPOTS = {(71, 89, 0), (88, 103, 0), (61, 103, -1), (78, 112, -1), (452, 115, -2), (475, 230, -2)}  # magma_drake -> czarny


def patch_world(world_dir):
    """Dla gotowego swiata (spawns.csv): szronowe smoki w lodowych grotach, czarne w wulkanie i na wzgorzach, zielone
    i czarne na Smoczych Wzgorzach. Zmienia tylko wymienione spawny (bez losu); idempotentna."""
    import os
    path = os.path.join(world_dir, 'spawns.csv')
    with open(path, encoding='utf-8') as f:
        lines = f.read().split('\n')
    x0, y0, x1, y1 = hills_box(world_dir)
    levels, regions = _levels_and_regions(world_dir)

    def fits(key, spot):
        # test GameWorldTest: poziom potwora nie nizszy niz 0,8 x dolny prog regionu, w ktorym stoi spawn
        for r in regions:
            if r[0] <= spot[0] <= r[2] and r[1] <= spot[1] <= r[3] and r[4] <= spot[2] <= r[5] and levels[key] < 0.8 * r[6]:
                return False
        return True
    counts = {}
    changed = []
    ice = coal = hills = 0
    for i, line in enumerate(lines):
        if not line or line.startswith('#'):
            continue
        f = line.split(';')
        spot = (int(f[1]), int(f[2]), int(f[3]))
        if f[0] == 'ice_golem' and spot in ICE_SPOTS and fits('hoarfrost_dragon', spot):
            f[0] = 'hoarfrost_dragon'
            f = [v for v in f if v != 'stado']
            ice += 1
        elif f[0] == 'magma_drake' and spot in COAL_SPOTS and fits('coal_dragon', spot):
            f[0] = 'coal_dragon'
            coal += 1
        elif x0 <= spot[0] <= x1 and y0 <= spot[1] <= y1 and spot[2] in HILLS_REPLACE:
            rule = HILLS_REPLACE[spot[2]].get(f[0])
            if rule:
                k = (spot[2], f[0])
                counts[k] = counts.get(k, 0) + 1
                if counts[k] % rule[1] == 0 and fits(rule[0], spot):
                    f[0] = rule[0]
                    hills += 1
        if f[0] != line.split(';')[0]:
            changed.append((f[0], spot))
        lines[i] = ';'.join(f)
    with open(path, 'w', encoding='utf-8') as fo:
        fo.write('\n'.join(lines))
    widen_regions(world_dir, changed)
    return ice, coal, hills


def _levels_and_regions(world_dir):
    import os
    levels = {}
    with open(os.path.join(world_dir, '..', '..', 'data', 'monsters.csv'), encoding='utf-8') as f:
        for line in f:
            p = line.split(';')
            if len(p) > 3 and not line.startswith('#'):
                levels[p[0]] = int(p[2])
    regions = []
    with open(os.path.join(world_dir, 'map.properties'), encoding='utf-8') as f:
        for line in f:
            if line.startswith('regions='):
                for r in line[len('regions='):].strip().split('|'):
                    p = r.split(';')
                    regions.append(tuple(int(v) for v in p[1:9]))
    return levels, regions


def widen_regions(world_dir, changed):
    """Zalecany poziom regionu obejmuje najsilniejszy z nowych smokow (jak krok 4 route_levels: regiony wg spawnow)."""
    import os
    levels = {}
    with open(os.path.join(world_dir, '..', '..', 'data', 'monsters.csv'), encoding='utf-8') as f:
        for line in f:
            p = line.split(';')
            if len(p) > 3 and not line.startswith('#'):
                levels[p[0]] = int(p[2])
    path = os.path.join(world_dir, 'map.properties')
    with open(path, encoding='utf-8') as f:
        props = f.read().split('\n')
    for i, line in enumerate(props):
        if not line.startswith('regions='):
            continue
        regs = [r.split(';') for r in line[len('regions='):].split('|')]
        for r in regs:
            x0, y0, x1, y1, z0, z1 = (int(v) for v in r[1:7])
            for key, (x, y, z) in changed:
                if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1 and levels[key] > int(r[8]):
                    r[8] = str(levels[key])
        props[i] = 'regions=' + '|'.join(';'.join(r) for r in regs)
    with open(path, 'w', encoding='utf-8') as fo:
        fo.write('\n'.join(props))


if __name__ == '__main__':
    import os
    import sys
    d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'shared', 'src', 'main', 'resources', 'maps', 'world')
    print('szronowe, weglowe, na wzgorzach:', patch_world(d))
