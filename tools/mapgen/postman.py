"""Zadanie Listonosza (darmowe, 10 misji) i skrzynki pocztowe (Darek 2026-11-13).

Zleceniodawca: Postmaster Kevan w Varden. Obiekt ``mailbox`` stoi w szesciu glownych miastach (TOWN) i na jedenastu polowiskach
(HUNT: dziedzince wypraw, skarbcu kurhanu, szczycie wiezy, czterech miejscach premium). Uzycie skrzynki zalicza misje VISIT
o jej nazwie (patrz ``GameSession.useMailbox``); kto ukonczy ostatnia misje, dostaje range listonosza (znacznik ``postman_rank``)
i moze ze skrzynki wysylac paczki poleceniem /wyslij (system poczty z banku). Wiersze skrzynek: ``mailboxes.csv``.
Dane gry pisze ``python3 postman.py data``.
"""
import os
import sys

import features as F

NONE = '-'
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
BEGIN, END = '# BEGIN postman (tools/mapgen/postman.py data)', '# END postman'

GIVER = ('postmaster_kevan', 'Postmaster Kevan', '#3a62a8')
GREET = ('Poczta nie śpi, a listy same się nie doniosą. Chcesz zostać Listonoszem Varden? Zapytaj mnie o pocztę.',
         'The post never sleeps and letters do not deliver themselves. Want to become a Varden Postman? Ask me about the post.',
         'El correo nunca duerme y las cartas no se reparten solas. ¿Quieres ser cartero de Varden? Pregúntame por el correo.')

# miasta: (klucz, miasto, nazwa pl/en/es)
TOWN_BOXES = [('mb_varden', 'Varden'), ('mb_frostholm', 'Frostholm'), ('mb_qasirah', 'Qasirah'),
              ('mb_selvaran', 'Selvaran'), ('mb_valmir', 'Valmir'), ('mb_nerys', 'Nerys')]
# polowiska: (klucz, zrodlo, klucz zrodla, rodzaj miejsca); pl/en/es nazwy brane z miejsc wypraw
HUNT_BOXES = [('mb_mill', 'story', 'mill', 'yard'), ('mb_crypt', 'story', 'crypt', 'yard'), ('mb_poacher', 'story', 'poacher', 'yard'),
              ('mb_oasis', 'story', 'oasis', 'yard'), ('mb_barrow', 'story', 'barrow', 'vault'), ('mb_watch', 'story', 'watch', 'top'),
              ('mb_zig', 'story', 'zig', 'yard'), ('mb_drown', 'premium', 'drown', 'yard'), ('mb_oakgrove', 'premium', 'oakgrove', 'yard'),
              ('mb_ember', 'premium', 'ember', 'yard'), ('mb_storm', 'premium', 'storm', 'yard')]
SUFFIX = {'vault': ('skarbiec', 'vault', 'cámara'), 'top': ('szczyt', 'summit', 'cima')}

PREFIX = ('Skrzynka', 'Mailbox', 'Buzón')


def box_name(label3, kind=''):
    return tuple(f"{PREFIX[i]}: {label3[i]}" + (f" ({SUFFIX[kind][i]})" if kind in SUFFIX else '') for i in range(3))


def town_names(town):
    return box_name((town, town, town))


def hunt_names(src, key, kind):
    if src == 'story':
        import story_quests as SQ
        q = next(q for q in SQ.QUESTS if q['key'] == key)
    else:
        import premium_quests as PQ
        q = next(q for q in PQ.all_lines() if q['key'] == key)
    return box_name(q['place'], kind)


# ------------------------------------------------------------------------------------------------ swiat
def _open(world, x, y, z):
    c = world.get(x, y, z)
    return c is not None and F.walk(c) and c[2] == NONE and c[3] == NONE and c[0] not in ('bridge', 'ship_deck', 'water')


def find_spot(ctx, near, z, rad, taken, min_gap=2, need8=True):
    """Najblizsze wolne pole wokol near (x, y): otwarte pole z wolnymi sasiadami, z dala od NPC, przejsc i skrzyn."""
    world = ctx['world']
    trans = {(t[0], t[1], t[2]) for t in ctx['transitions']}
    npcs = [(int(n[4]), int(n[5]), int(n[6])) for n in ctx['npcs']]
    chests = [(c[0], c[1], c[2]) for c in ctx.get('loot_chests', [])] + [(c[2], c[3], c[4]) for c in ctx.get('star_choices', [])]
    best = None
    dirs = F.DIRS4 + (((1, 1), (1, -1), (-1, 1), (-1, -1)) if need8 else ())
    for dy in range(-rad, rad + 1):
        for dx in range(-rad, rad + 1):
            x, y = near[0] + dx, near[1] + dy
            if not _open(world, x, y, z) or (x, y, z) in trans:
                continue
            if not all(_open(world, x + a, y + b, z) and (x + a, y + b, z) not in trans for a, b in dirs):
                continue
            if any(abs(x - a) < min_gap and abs(y - b) < min_gap and z == c for (a, b, c) in npcs):
                continue
            if any(abs(x - a) < 2 and abs(y - b) < 2 and z == c for (a, b, c) in chests):
                continue
            if any(abs(x - a) < min_gap and abs(y - b) < min_gap and z == c for (a, b, c) in taken):
                continue
            d = abs(dx) + abs(dy)
            if best is None or d < best[0]:
                best = (d, (x, y))
    assert best is not None, f'postman: no spot near {near} z={z}'
    return best[1]


def put_box(ctx, x, y, z):
    world = ctx['world']
    g, deco, _o, w = world.get(x, y, z)
    world.put(x, y, z, (g, deco, 'mailbox', w))


def build(ctx, cost, helpers):
    world = ctx['world']
    towns = dict(ctx['towns'])
    rows, taken = [], []
    # Poczmistrz i skrzynka w Varden, obok siebie
    temple = towns['Varden'][:2]
    taken.append((temple[0], temple[1], 0))          # swiatynia to miejsce odrodzenia: Kevan stoi co najmniej 3 pola dalej
    kv = find_spot(ctx, temple, 0, 16, taken, min_gap=3)
    taken.pop()
    ctx['npcs'].append([GIVER[0], GIVER[1], 'GUIDE', 'Varden', kv[0], kv[1], 0, '-', GIVER[2], GREET[0]])
    taken.append((kv[0], kv[1], 0))
    for key, town in TOWN_BOXES:
        near = kv if town == 'Varden' else towns[town][:2]
        sp = find_spot(ctx, near, 0, 24, taken, min_gap=3 if town == 'Varden' else 2)
        put_box(ctx, sp[0], sp[1], 0)
        taken.append((sp[0], sp[1], 0))
        rows.append(f"{key};{sp[0]};{sp[1]};0;TOWN;{town_names(town)[0]}")
    sq = {r['key']: r for r in ctx.get('story_quests', [])}
    ps = {r['key']: r for r in ctx.get('premium_sites', [])}
    for key, src, qk, kind in HUNT_BOXES:
        rec = (sq if src == 'story' else ps)[qk]
        if kind == 'yard':
            at, z = rec['npc'], 0
        else:
            c = rec['chests'][0]
            at, z = (c[0], c[1]), c[2]
        sp = find_spot(ctx, at, z, 10, taken, min_gap=3, need8=(kind == 'yard'))
        put_box(ctx, sp[0], sp[1], z)
        taken.append((sp[0], sp[1], z))
        rows.append(f"{key};{sp[0]};{sp[1]};{z};HUNT;{hunt_names(src, qk, kind if kind != 'yard' else '')[0]}")
    ctx['mailboxes'] = rows
    return rows


# ------------------------------------------------------------------------------------------------ dane gry
def level_cost(level):
    def tot(L):
        return 50 * (L ** 3 - 6 * L ** 2 + 17 * L - 12) // 3
    return tot(level + 1) - tot(level)


def missions():
    """10 misji: (klucz, poziom, rodzaj, cel, ilosc, frakcja_exp, nazwa pl/en/es, opis pl/en/es)."""
    mill, crypt, poach = hunt_names('story', 'mill', ''), hunt_names('story', 'crypt', ''), hunt_names('story', 'poacher', '')
    barrow = hunt_names('story', 'barrow', 'vault')
    varden, frost, qas = town_names('Varden'), town_names('Frostholm'), town_names('Qasirah')
    T = lambda pl, en, es: (pl, en, es)
    M = []

    def add(level, kind, target, amount, share, names, descs):
        M.append(dict(level=level, kind=kind, target=target, amount=amount, share=share, names=names, descs=descs))

    add(5, 'VISIT', varden[0], 1, 0.15,
        T('Pierwszy list', 'The First Letter', 'La primera carta'),
        T(f'Kevan wręcza ci pierwszy list. Wrzuć go do skrzynki w Varden ({varden[0]}): stań obok i dotknij jej.',
          f'Kevan hands you your first letter. Post it in the Varden mailbox ({varden[1]}): stand next to it and tap it.',
          f'Kevan te da tu primera carta. Échala en el buzón de Varden ({varden[2]}): colócate al lado y tócalo.'))
    add(8, 'KILL', 'wolf', 8, 0.30,
        T('Wilki na trakcie', 'Wolves on the Road', 'Lobos en el camino'),
        T('Wilki rozszarpały już dwie torby kurierów. Pokonaj 8, żeby trakt do młyna był bezpieczny.',
          'Wolves have already shredded two courier satchels. Defeat 8 to make the road to the mill safe.',
          'Los lobos ya destrozaron dos bolsas de mensajeros. Derrota a 8 para que el camino al molino sea seguro.'))
    add(8, 'VISIT', mill[0], 1, 0.15,
        T('Pomiar trasy: młyn', 'Measuring the Route: the Mill', 'Medir la ruta: el molino'),
        T(f'Kevan rysuje mapę tras i potrzebuje czasu przejścia. Dotknij skrzynki przy młynie ({mill[0]}).',
          f'Kevan is drawing a route map and needs walking times. Tap the mailbox at the mill ({mill[1]}).',
          f'Kevan dibuja un mapa de rutas y necesita los tiempos. Toca el buzón del molino ({mill[2]}).'))
    add(12, 'VISIT', crypt[0], 1, 0.15,
        T('Pomiar trasy: krypta', 'Measuring the Route: the Crypt', 'Medir la ruta: la cripta'),
        T(f'Druga odnoga trasy biegnie do kaplicy z kryptą. Dotknij skrzynki przy niej ({crypt[0]}).',
          f'The second branch of the route runs to the chapel with the crypt. Tap the mailbox there ({crypt[1]}).',
          f'El segundo tramo de la ruta llega a la capilla con la cripta. Toca el buzón de allí ({crypt[2]}).'))
    add(14, 'COLLECT', 'lost_mailbag', 1, 0.35,
        T('Zgubiona torba', 'The Lost Mailbag', 'La bolsa perdida'),
        T('Kurier zgubił torbę z listami, a bandyci z traktu zabrali ją jako łup. Odbierz od bandytów zgubioną torbę pocztową.',
          'A courier lost his bag of letters and the road bandits took it as loot. Take the lost mailbag back from the bandits.',
          'Un mensajero perdió su bolsa de cartas y los bandidos del camino se la llevaron. Recupera la bolsa perdida de los bandidos.'))
    add(15, 'VISIT', frost[0], 1, 0.20,
        T('Przesyłka do Frostholm', 'Delivery to Frostholm', 'Entrega a Frostholm'),
        T(f'Odzyskane listy mają adres w Frostholm. Wrzuć je do skrzynki w tamtejszym mieście ({frost[0]}).',
          f'The recovered letters are addressed to Frostholm. Post them in the mailbox in that town ({frost[1]}).',
          f'Las cartas recuperadas van a Frostholm. Échalas en el buzón de esa ciudad ({frost[2]}).'))
    add(16, 'VISIT', poach[0], 1, 0.15,
        T('Pomiar trasy: kłusownicy', 'Measuring the Route: the Poachers', 'Medir la ruta: los cazadores furtivos'),
        T(f'Ostatni odcinek trasy prowadzi przez śniegi do jamy kłusownika. Dotknij skrzynki przy niej ({poach[0]}).',
          f'The last stretch of the route leads through the snow to the poacher den. Tap the mailbox there ({poach[1]}).',
          f'El último tramo de la ruta cruza la nieve hasta la guarida del cazador furtivo. Toca el buzón de allí ({poach[2]}).'))
    add(20, 'COLLECT', 'sodden_letters', 3, 0.45,
        T('Rozmokła poczta', 'Sodden Post', 'Correo empapado'),
        T('Śnieżne wilki rozgrzebały zasypaną skrzynkę i rozwlekły listy po lodzie. Zbierz 3 rozmokłe listy z ich łupu.',
          'Snow wolves dug up a buried mailbox and dragged the letters across the ice. Gather 3 sodden letters from their spoils.',
          'Los lobos de nieve desenterraron un buzón y arrastraron las cartas por el hielo. Reúne 3 cartas empapadas de su botín.'))
    add(22, 'VISIT', qas[0], 1, 0.25,
        T('Przesyłka do Qasirah', 'Delivery to Qasirah', 'Entrega a Qasirah'),
        T(f'Wysuszone listy mają trafić do pustynnego miasta. Wrzuć je do skrzynki w Qasirah ({qas[0]}).',
          f'The dried letters are bound for the desert town. Post them in the Qasirah mailbox ({qas[1]}).',
          f'Las cartas secas van a la ciudad del desierto. Échalas en el buzón de Qasirah ({qas[2]}).'))
    add(35, 'VISIT', barrow[0], 1, 1.0,
        T('Paczka z głębin', 'Parcel from the Depths', 'Paquete de las profundidades'),
        T(f'Ostatnia przesyłka dla zmarłych kupców leży w skarbcu Kurhanu Dworu Świec. Przedrzyj się przez straże i dotknij skrzynki w skarbcu ({barrow[0]}). Poziom drzwi: 35.',
          f'The last parcel for the dead merchants goes to the vault of the Barrow of the Candle Court. Fight past the guards and tap the vault mailbox ({barrow[1]}). Door level: 35.',
          f'El último paquete para los mercaderes muertos va a la cámara del Túmulo de la Corte de Velas. Abriéndote paso entre los guardias, toca el buzón de la cámara ({barrow[2]}). Nivel de la puerta: 35.'))
    return M


ITEMS = [
    '14;postman_cap;Czapka listonosza;ARMOR;1;400;HEAD;0;0;2;0;*;0;0;PHYSICAL;-;-;#3662a8;Granatowa czapka z rogiem poczty (nagroda Zadania Listonosza).',
    '15;lost_mailbag;Zgubiona torba pocztowa;MATERIAL;1;50;NONE;0;0;0;0;*;0;0;PHYSICAL;-;-;#7e522a;Torba kuriera z listami, zabrana przez bandytów.',
    '16;sodden_letters;Rozmokły list;MATERIAL;20;10;NONE;0;0;0;0;*;0;0;PHYSICAL;-;-;#9aa4b4;List rozmoknięty od śniegu i wilczej śliny. Kevan zdoła go wysuszyć.',
]
ITEM_NAMES = {'postman_cap': ('Postman Cap', 'Gorra de cartero'), 'lost_mailbag': ('Lost Mailbag', 'Bolsa de correo perdida'),
              'sodden_letters': ('Sodden Letter', 'Carta empapada')}


def splice(path, lines):
    text = open(path, encoding='utf-8').read()
    if BEGIN in text:
        a = text.index(BEGIN)
        b = text.index(END) + len(END)
        text = text[:a].rstrip('\n') + '\n' + text[b:].lstrip('\n')
    text = text.rstrip('\n') + '\n' + BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    open(path, 'w', encoding='utf-8').write(text)


def write_data():
    ms = missions()
    quests, chains, en, es = [], [], [], []
    for i, m in enumerate(ms, start=1):
        key = f'postman_m{i}'
        exp = m['share'] * level_cost(m['level'])
        exp = int(round(exp, -2))
        gold = max(100, int(round(exp * 0.05, -2)))
        quests.append(f"{key};{m['names'][0]};{GIVER[0]};{m['kind']};{m['target']};{m['amount']};{exp};{gold};{m['level']};{m['descs'][0]}")
        for lang, acc in ((1, en), (2, es)):
            acc.append(f"quest;{key};name;{m['names'][lang]}")
            acc.append(f"quest;{key};desc;{m['descs'][lang]}")
    # nagrody przedmiotowe (za ukonczenie misji): plecak za dostawe do Qasirah, czapka za paczke z glebin
    for i in range(2, len(ms) + 1):
        chains.append(f"postman_m{i};postman_m{i - 1};{ {9: 'backpack_traveler:1', 10: 'postman_cap:1'}.get(i, '-') }")
    for lang, acc in ((1, en), (2, es)):
        acc.append(f"quest;postman_m1;line;{('Zadanie Listonosza', 'Postman Quest', 'Misión del cartero')[lang]}")
        acc.append(f"quest;postman_m1;where;{('Varden, okolice świątyni', 'Varden, near the temple', 'Varden, cerca del templo')[lang]}")
        acc.append(f"npc;{GIVER[0]};name;{GIVER[1]}")
        acc.append(f"npc;{GIVER[0]};greeting;{GREET[lang]}")
        for nm, tr in ITEM_NAMES.items():
            acc.append(f"item;{nm};name;{tr[lang - 1]}")
    lines_csv = ["postman_m1;easy;0;Zadanie Listonosza;Varden, okolice świątyni"]
    # skrzynki: nazwy 3 jezyki
    seen = set()
    for key, town in TOWN_BOXES:
        names = town_names(town)
        for lang, acc in ((1, en), (2, es)):
            acc.append(f"mailbox;{key};name;{names[lang]}")
            if names[0] not in seen:
                acc.append(f"place;{names[0]};name;{names[lang]}")
        seen.add(names[0])
    for key, src, qk, kind in HUNT_BOXES:
        names = hunt_names(src, qk, kind if kind != 'yard' else '')
        for lang, acc in ((1, en), (2, es)):
            acc.append(f"mailbox;{key};name;{names[lang]}")
            if names[0] not in seen:
                acc.append(f"place;{names[0]};name;{names[lang]}")
        seen.add(names[0])
    dlg = [
        f"{GIVER[0]};poczta,listonosz,ranga,skrzynka,skrzynki,trasa,miasta,pomiar;Skrzynki pocztowe stoją w Varden, Frostholm, Qasirah, Selvaran, Valmir i Nerys oraz przy młynie, krypcie, jamie kłusownika, oazie, kurhanie, wieży strażniczej, ziguracie i czterech wyprawach premium. Dotknij skrzynki, żeby jej użyć. Kto ukończy moje zadanie, zostaje Listonoszem: ze skrzynki wyśle paczkę poleceniem /wyślij Imię, 5 wilcze futro, bez wizyty w banku.",
        f"{GIVER[0]};zadanie,praca,list,listy,przesyłka,torba,bandyci,rozmokła,kurhan,paczka;Mam dla ciebie dziesięć zleceń: listy do miast, pomiar tras, zgubiona torba, rozmokła poczta i jedna paczka, po którą trzeba zejść w głąb Kurhanu Dworu Świec (drzwi od poziomu 35). Torbę kuriera zabrali bandyci z traktu, a rozmokłe listy zbierzesz z łupu śnieżnych wilków. Powiedz „zadanie”, a dam ci pierwszy list.",
    ]
    loot = ['bandit;lost_mailbag;120;1;1', 'snow_wolf;sodden_letters;350;1;2']
    splice(os.path.join(RES, 'data', 'items.csv'), ITEMS)
    splice(os.path.join(RES, 'data', 'quests.csv'), quests)
    splice(os.path.join(RES, 'data', 'quest_chains.csv'), chains)
    splice(os.path.join(RES, 'data', 'quest_lines.csv'), lines_csv)
    splice(os.path.join(RES, 'data', 'dialogues.csv'), dlg)
    splice(os.path.join(RES, 'data', 'loot.csv'), loot)
    splice(os.path.join(RES, 'i18n', 'data_en.csv'), en)
    splice(os.path.join(RES, 'i18n', 'data_es.csv'), es)
    print('postman data written:', len(quests), 'missions,', len(TOWN_BOXES) + len(HUNT_BOXES), 'mailboxes')


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'data':
        write_data()
