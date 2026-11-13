"""Bagienni lupiezcy (bog_reaver, poziom 85, grafika Darka): kilka spawnow bog_wraith (poziom 80) na bagnach zamienione
na bagiennych lupiezcow. Spawny sprawdzone pojedynczo (route_levels, GameWorldTest); idempotentna, bez losu."""
SPOTS = {(739, 531, -2), (628, 662, -2), (790, 552, -3)}


def apply_lines(lines):
    n = 0
    for i, ln in enumerate(lines):
        f = ln.split(';')
        if ln and not ln.startswith('#') and f[0] == 'bog_wraith' and (int(f[1]), int(f[2]), int(f[3])) in SPOTS:
            f[0] = 'bog_reaver'
            lines[i] = ';'.join(f)
            n += 1
    return n


def apply(ctx):
    return apply_lines(ctx['spawns'])
