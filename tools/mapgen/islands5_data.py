"""Dane piatki nowych wysp (2026-11-12): Wyspa Szeptow (darmowa, po quescie promowego) i cztery wyspy premium
(lodowa, pustynna, tropikalna, umiarkowana), kazda odblokowana osobnym questem dostepu.

Ten modul to tylko dane: potwory, przedmioty, linie zadan, zaleznosci miedzy nimi i zasady rejsow. Swiat buduje
``islands5.py`` (wyspy, lochy, przystanie), a ``python3 islands5_data.py`` zapisuje CSV gry (monsters, loot, bestiary,
items, quests, quest_chains, quest_requires, quest_lines, ship_access, dialogues, tlumaczenia) miedzy znacznikami
``# BEGIN islands5``. Poziomy i nagrody: docs/GAME_BALANCE.md ("Nagrody questow").
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
BEGIN, END = '# BEGIN islands5 (tools/mapgen/islands5_data.py)', '# END islands5'


def level_cost(level):
    def tot(L):
        return 50 * (L ** 3 - 6 * L ** 2 + 17 * L - 12) // 3
    return tot(level + 1) - tot(level)


# ====================================================================================== potwory
# klucz: (pl, en, es, poziom, zachowanie, typ obrazen, zasieg, biom, klasa, kolor, ksztalt, odpornosci, opis, boss, lup)
# lup: [(przedmiot, promile, min, max)]
GOLD = lambda lv: ('gold_coin', 900, max(2, lv // 2), max(8, lv * 3))
MONSTERS = {
    'shore_scuttler': ('Krab przybrzeżny', 'Shore Scuttler', 'Cangrejo costero', 11, 'TANK', 'PHYSICAL', 1, 'temperate', 'Aquatic',
                       '#c07850', 'beast', 'ICE:-10,EARTH:10', 'Pancerny krab z szarej plaży. Szczypce tną sieci i palce.', 0,
                       [('pearl', 40, 1, 1), ('meat', 400, 1, 2)]),
    'whisper_shade': ('Szeptający Cień', 'Whispering Shade', 'Sombra susurrante', 26, 'MAGIC', 'DEATH', 3, 'underground', 'Undead',
                      '#8a90b0', 'humanoid', 'DEATH:100,LIFE_DRAIN:100,DROWN:100,HOLY:-30,PHYSICAL:10', 'Cień szepcze imiona zmarłych, aż słuchacz traci głowę.', 0,
                      [('bone', 400, 1, 2), ('health_potion', 150, 1, 2), ('mana_potion', 120, 1, 1)]),
    'cairn_wight': ('Upiór z Kopca', 'Cairn Wight', 'Aparecido del túmulo', 44, 'MELEE', 'DEATH', 1, 'underground', 'Undead',
                    '#6a7a6a', 'humanoid', 'DEATH:100,LIFE_DRAIN:100,DROWN:100,HOLY:-30,FIRE:-10', 'Wartownik starego kopca, który nie zauważył końca wojny.', 0,
                    [('bone', 500, 1, 3), ('strong_health_potion', 100, 1, 1), ('crystal_shard', 40, 1, 1)]),
    'cairn_lord': ('Pan Kopca', 'Lord of the Cairn', 'Señor del túmulo', 60, 'TANK', 'DEATH', 1, 'underground', 'Undead',
                   '#4a5a5a', 'humanoid', 'DEATH:100,LIFE_DRAIN:100,DROWN:100,HOLY:-30,PHYSICAL:15', 'Król bez królestwa. Strzeże kopca wysp i pamięta każdy ukłon.', 1,
                   [('strong_health_potion', 600, 2, 4), ('strong_mana_potion', 500, 2, 4), ('crystal_shard', 500, 2, 4)]),
    'magpie_king': ('Król Sroczy', 'Magpie King', 'Rey de las urracas', 32, 'SKIRMISHER', 'PHYSICAL', 1, 'temperate', 'Human',
                    '#2a2a3a', 'humanoid', 'PHYSICAL:10,EARTH:10', 'Herszt złodziei, który kradnie wszystko, co błyszczy, i chowa w norze.', 1,
                    [('silver_brooch', 1000, 1, 1), ('strong_health_potion', 500, 1, 3), ('health_potion', 800, 3, 6)]),
    'frost_lurker': ('Szronowy Czyhacz', 'Frost Lurker', 'Acechador escarchado', 170, 'SKIRMISHER', 'ICE', 1, 'north', 'Mammal',
                     '#bcd8f0', 'beast', 'ICE:80,FIRE:-25,PHYSICAL:10', 'Biały drapieżnik, niewidoczny w zamieci. Atakuje z zasypanej zaspy.', 0,
                     [('frost_pelt', 400, 1, 3), ('great_health_potion', 160, 1, 2), ('glacial_core', 20, 1, 1)]),
    'rimecloak_pilgrim': ('Pielgrzym w Szronie', 'Rimecloak Pilgrim', 'Peregrino de escarcha', 95, 'MAGIC', 'ICE', 4, 'north', 'Human',
                          '#a8c0e0', 'humanoid', 'ICE:70,FIRE:-20,HOLY:10', 'Wędrowiec, który szedł do Lodowego Tronu tak długo, że sam zamarzł.', 0,
                          [('frost_pelt', 300, 1, 2), ('great_mana_potion', 140, 1, 2), ('great_health_potion', 140, 1, 1)]),
    'rime_jarl': ('Jarl Szronu', 'Jarl of the Rime', 'Jarl de la escarcha', 300, 'TANK', 'ICE', 1, 'north', 'Giant',
                  '#7aa8d8', 'humanoid', 'ICE:100,FIRE:-25,PHYSICAL:25', 'Pan zamarzniętej fortecy. Jego topór nigdy się nie nagrzewa.', 1,
                  [('ultimate_health_potion', 600, 2, 5), ('ultimate_mana_potion', 500, 2, 4), ('glacial_core', 700, 2, 4)]),
    'dune_skulker': ('Wydmowy Łazik', 'Dune Skulker', 'Rondador de dunas', 150, 'SKIRMISHER', 'PHYSICAL', 1, 'desert', 'Reptile',
                     '#d8b868', 'beast', 'FIRE:30,EARTH:30,ICE:-15', 'Jaszczur piaskowy z kolcami na grzbiecie, pływa w sypkim piasku.', 0,
                     [('scorpion_tail', 300, 1, 3), ('great_health_potion', 150, 1, 2), ('gilded_scale', 8, 1, 1)]),
    'obelisk_priest': ('Kapłan Obelisku', 'Obelisk Priest', 'Sacerdote del obelisco', 230, 'MAGIC', 'FIRE', 4, 'desert', 'Human',
                       '#d09040', 'humanoid', 'FIRE:70,EARTH:20,ICE:-20,HOLY:10', 'Strażnik złotych obelisków. Pali tych, którzy stąpają po ich cieniu.', 0,
                       [('mummy_wrap', 300, 1, 3), ('ultimate_mana_potion', 120, 1, 2), ('scarab_gem', 40, 1, 1)]),
    'sand_regent': ('Regent Burzy Piaskowej', 'Regent of the Sandstorm', 'Regente de la tormenta de arena', 340, 'TANK', 'PHYSICAL', 1,
                    'desert', 'Giant', '#c8982c', 'humanoid', 'PHYSICAL:30,EARTH:60,FIRE:40,ICE:-20',
                    'Władca burzy, który zbudował pałac z poruszających się wydm.', 1,
                    [('ultimate_health_potion', 600, 2, 5), ('ultimate_spirit_potion', 400, 2, 4), ('gilded_scale', 600, 1, 3)]),
    'canopy_lurker': ('Czyhacz Korony', 'Canopy Lurker', 'Acechador del dosel', 115, 'SKIRMISHER', 'PHYSICAL', 1, 'equatorial', 'Mammal',
                      '#5a8a4a', 'beast', 'EARTH:40,ICE:-10,FIRE:-10', 'Wielki kot dżungli, który spada na ofiarę z koron drzew.', 0,
                      [('croc_leather', 300, 1, 3), ('great_health_potion', 140, 1, 2), ('jungle_idol', 20, 1, 1)]),
    'orchid_mimic': ('Storczykowa Mimikra', 'Orchid Mimic', 'Mímica de orquídea', 175, 'MAGIC', 'EARTH', 3, 'equatorial', 'Plant',
                     '#c070b0', 'humanoid', 'EARTH:80,FIRE:-30,ICE:-10', 'Kwiat tak piękny, że nikt nie zauważa, że zjada.', 0,
                     [('snake_skin', 300, 1, 3), ('great_mana_potion', 160, 1, 2), ('emerald_scale', 25, 1, 1)]),
    'vine_empress': ('Cesarzowa Pnączy', 'Empress of Vines', 'Emperatriz de las enredaderas', 360, 'MAGIC', 'EARTH', 3, 'equatorial', 'Plant',
                     '#3a9a4a', 'humanoid', 'EARTH:100,FIRE:-30,PHYSICAL:20', 'Dżungla jest jej tronem, a pnącza świtą. Leczy się z każdego liścia.', 1,
                     [('ultimate_health_potion', 600, 2, 5), ('ultimate_mana_potion', 500, 2, 4), ('emerald_scale', 700, 2, 4)]),
    'briar_boar': ('Cierniowy Odyniec', 'Briar Boar', 'Jabalí de zarzas', 75, 'TANK', 'PHYSICAL', 1, 'temperate', 'Mammal',
                   '#6a5a3a', 'beast', 'PHYSICAL:15,EARTH:30,FIRE:-15', 'Dzik obrośnięty cierniami. Szarżuje przez krzaki jak taran.', 0,
                   [('boar_tusk', 400, 1, 2), ('great_health_potion', 120, 1, 1)]),
    'moor_crone': ('Wiedźma Wrzosowisk', 'Moor Crone', 'Bruja del páramo', 135, 'MAGIC', 'DEATH', 4, 'temperate', 'Human',
                   '#7a5a8a', 'humanoid', 'DEATH:40,EARTH:20,HOLY:-20,FIRE:-15', 'Stara czarownica z bagiennych wrzosowisk. Klątwa jest jej ostatnią dłonią.', 0,
                   [('spider_silk', 300, 1, 3), ('great_mana_potion', 160, 1, 2), ('ectoplasm', 80, 1, 1)]),
    'oak_sovereign': ('Władca Dębów', 'Sovereign of the Oaks', 'Soberano de los robles', 300, 'TANK', 'EARTH', 1, 'temperate', 'Plant',
                      '#5a4a2a', 'humanoid', 'EARTH:100,PHYSICAL:30,FIRE:-30,ICE:10', 'Najstarszy dąb wyspy, który nauczył się chodzić i nie lubi drwali.', 1,
                      [('ultimate_health_potion', 600, 2, 5), ('ultimate_mana_potion', 500, 2, 4), ('ectoplasm', 600, 2, 4)]),
    # 2026-11-12: oboz barbarzyncow na wyspie lodowej (premium) i maly oboz darmowy na lodowych skalach kontynentu
    'frostfur_raider': ('Najeźdźca w Szronowym Futrze', 'Frostfur Raider', 'Asaltante de pelaje escarchado', 30, 'MELEE', 'PHYSICAL', 1, 'north', 'Human',
                        '#9a8a7a', 'humanoid', 'ICE:30,FIRE:-10', 'Półnagi wojownik w futrze z lodowego wilka. Rzuca się na każdego, kto zbliży się do palisady.', 0,
                        [('frost_pelt', 400, 1, 2), ('meat', 400, 1, 2), ('health_potion', 150, 1, 2)]),
    'snowclub_brute': ('Brutal ze Śnieżną Pałką', 'Snowclub Brute', 'Bruto de la maza nevada', 38, 'TANK', 'PHYSICAL', 1, 'north', 'Human',
                       '#8a7a6a', 'humanoid', 'ICE:40,FIRE:-10,PHYSICAL:10', 'Potężny barbarzyńca z okutą pałką. Ogień i mróz go nie ruszają, tylko dobry cios.', 0,
                       [('frost_pelt', 350, 1, 2), ('meat', 450, 1, 3), ('strong_health_potion', 120, 1, 1)]),
    'skarn_hetman': ('Hetman Skarn', 'Hetman Skarn', 'Hetman Skarn', 58, 'TANK', 'PHYSICAL', 1, 'north', 'Human',
                     '#7a6a5a', 'humanoid', 'ICE:50,FIRE:-10,PHYSICAL:15', 'Wódz małego obozu na lodowych skałach. Czaszki wrogów wiszą na jego pasie.', 1,
                     [('strong_health_potion', 600, 2, 4), ('strong_mana_potion', 500, 2, 3), ('crystal_shard', 400, 1, 3)]),
    'icefang_hunter': ('Łowca Lodowego Kła', 'Icefang Hunter', 'Cazador del colmillo helado', 150, 'SKIRMISHER', 'PHYSICAL', 4, 'north', 'Human',
                       '#a8a090', 'humanoid', 'ICE:50,FIRE:-15', 'Barbarzyński myśliwy, który zbiera kły i czaszki. Rzuca włócznie z palisady.', 0,
                       [('frost_pelt', 400, 1, 3), ('great_health_potion', 160, 1, 2), ('glacial_core', 20, 1, 1)]),
    'bonewrap_tamer': ('Poskramiacz w Kościanych Opaskach', 'Bonewrap Tamer', 'Domador de vendas de hueso', 175, 'MELEE', 'PHYSICAL', 1, 'north', 'Human',
                       '#9a9080', 'humanoid', 'ICE:50,FIRE:-15,PHYSICAL:5', 'Prowadzi mamuty na łańcuchu. Kości przodków ma wplecione w opaski na rękach.', 0,
                       [('frost_pelt', 350, 1, 3), ('great_health_potion', 150, 1, 2), ('great_mana_potion', 120, 1, 1)]),
    'ironjaw_splitter': ('Rozłupywacz Żelaznej Szczęki', 'Ironjaw Splitter', 'Partidor de mandíbula de hierro', 200, 'TANK', 'PHYSICAL', 1, 'north', 'Human',
                         '#8a8070', 'humanoid', 'ICE:55,FIRE:-15,PHYSICAL:15', 'Opancerzony olbrzym z toporem do łupania hełmów i bram. Nie cofa się nigdy.', 0,
                         [('frost_pelt', 300, 1, 3), ('great_health_potion', 160, 1, 2), ('glacial_core', 25, 1, 1)]),
    'rimeblood_walker': ('Szronokrwisty Wędrowiec', 'Rimeblood Walker', 'Caminante de sangre escarcha', 225, 'MAGIC', 'ICE', 3, 'north', 'Human',
                         '#90b0d0', 'humanoid', 'ICE:80,FIRE:-25,HOLY:10', 'Szaman, który pije lodowatą krew mamutów. Jego dłonie zostawiają szron na tarczach.', 0,
                         [('frost_pelt', 300, 1, 2), ('great_mana_potion', 150, 1, 2), ('glacial_core', 30, 1, 1)]),
    'blizzard_warchief': ('Wódz Zamieci', 'Blizzard Warchief', 'Jefe de la ventisca', 330, 'TANK', 'PHYSICAL', 1, 'north', 'Human',
                          '#6a5a4a', 'humanoid', 'ICE:70,FIRE:-20,PHYSICAL:25', 'Pan barbarzyńskiego obozu, który wygrał z zimą i uczynił ją swoją żoną. Ryk słychać na całej wyspie.', 1,
                          [('ultimate_health_potion', 600, 2, 5), ('ultimate_mana_potion', 500, 2, 4), ('glacial_core', 700, 2, 4)]),
}
BOSSES = {k for k, v in MONSTERS.items() if v[13]}
# strojow paczki OTSP: klucz -> (numer stroju, przebarwienie, skala wysokosci, skala szerokosci); skala 1,0 = TINTED
ART = {
    'shore_scuttler': (37, (200, 130, 90), 1.0, 1.0), 'whisper_shade': (2, (150, 160, 200), 1.0, 1.0),
    'cairn_wight': (3, (120, 140, 120), 1.0, 1.0), 'cairn_lord': (46, (80, 100, 100), 1.35, 1.35),
    'magpie_king': (19, (60, 60, 85), 1.15, 1.15),
    'frost_lurker': (38, (195, 225, 245), 1.0, 1.0), 'rimecloak_pilgrim': (26, (150, 185, 230), 1.0, 1.0),
    'rime_jarl': (12, (120, 170, 225), 1.25, 1.25),
    'dune_skulker': (37, (220, 185, 105), 1.0, 1.0), 'obelisk_priest': (33, (225, 150, 60), 1.0, 1.0),
    'sand_regent': (12, (215, 165, 50), 1.3, 1.3),
    'canopy_lurker': (38, (95, 150, 70), 1.0, 1.0), 'orchid_mimic': (32, (205, 110, 175), 1.0, 1.0),
    'vine_empress': (32, (60, 170, 80), 1.3, 1.3),
    'briar_boar': (38, (120, 95, 60), 1.0, 1.0), 'moor_crone': (33, (130, 95, 150), 1.0, 1.0),
    'oak_sovereign': (12, (100, 75, 40), 1.35, 1.35),
    'frostfur_raider': (19, (150, 130, 105), 1.0, 1.0), 'snowclub_brute': (12, (120, 105, 90), 1.0, 1.0),
    'skarn_hetman': (12, (105, 90, 75), 1.3, 1.3),
    'icefang_hunter': (19, (175, 170, 150), 1.0, 1.0), 'bonewrap_tamer': (26, (170, 160, 135), 1.0, 1.0),
    'ironjaw_splitter': (12, (140, 135, 125), 1.1, 1.1), 'rimeblood_walker': (31, (140, 175, 215), 1.0, 1.0),
    'blizzard_warchief': (46, (100, 85, 70), 1.4, 1.4),
}


def monster_row(key):
    pl, _en, _es, lv, beh, dt, rng, biome, cls, color, shape, res, desc, boss, _loot = MONSTERS[key]
    base = min(0.4 * lv * lv + 4 * lv, 23 * lv)
    hp = int(round(base * (7 if boss else 1), -1 if base * (7 if boss else 1) > 200 else 0))
    exp = int(round(hp * (0.35 + min(lv, 400) / 700.0) * (1.0 if not boss else 0.9), -1))
    mx = int(lv * (1.9 - lv / 900.0))
    if rng > 1 and not boss:
        mx = int(mx * 0.9)
    mn = max(1, int(mx * 0.3))
    interval = 2000 if beh != 'SKIRMISHER' else 1800
    speed = 200 if beh in ('TANK', 'MELEE') else 220
    aggro = 7 if boss else 6
    heal = int(hp * 0.05) if boss else 0
    armor = max(1, lv // 8)
    return ';'.join(str(x) for x in (key, pl, lv, hp, exp, speed, mn, mx, dt, rng, interval, armor, res, beh, aggro, 0, heal,
                                      1 if boss else 0, color, shape, biome, '-', cls, '-', desc))


# ====================================================================================== przedmioty
ITEMS = {
    # klucz: (pl, en, es, wartosc, kolor, opis, ikona bazowa, przebarwienie)
    'silver_brooch': ('Srebrna broszka', 'Silver Brooch', 'Broche de plata', 50, '#d8dce8',
                      'Broszka z herbem rodziny przewoźnika. Król Sroczy ukradł ją dawno temu.', 'pearl', (215, 220, 235)),
}


# ====================================================================================== wyspy i rejsy
# klucz wyspy: (kapitan wyspy, miasto/etykieta kapitana, port macierzysty (kapitan), premium, flaga dostepu, quest przyznajacy flage)
ISLANDS = {
    'whisper': dict(name=('Wyspa Szeptów', 'Isle of Whispers', 'Isla de los Susurros'), captain='whisper_captain',
                    cname='Boatwoman Brenna', hubs=['quay_captain'], premium=False, flag='ship.whisper', grant='ferry_m4',
                    hint=('Nie wpuszczę cię na pokład bez broszki. Pogadaj z Oswinem przy nabrzeżu i przynieś mu to, co ukradł Król Sroczy.',
                          'I will not take you aboard without the brooch. Talk to Oswin at the quay and bring him what the Magpie King stole.',
                          'No te llevo sin el broche. Habla con Oswin en el muelle y tráele lo que robó el Rey de las urracas.')),
    'ice': dict(name=('Wyspa Białej Zamieci', 'Isle of the White Blizzard', 'Isla de la Ventisca Blanca'), captain='ice5_captain',
                cname='Captain Skuld', hubs=['frost_captain'], premium=True, flag='ship.ice5', grant='ice_m4',
                hint=('Na Białą Zamieć płynę tylko z pozwoleniem Halvara z Frostholm. Zdobądź je, zanim zawiniemy.',
                      'I sail to the White Blizzard only with Halvar of Frostholm\'s leave. Earn it first.',
                      'Solo navego a la Ventisca Blanca con permiso de Halvar de Frostholm. Consíguelo antes.')),
    'desert': dict(name=('Wyspa Złotych Wydm', 'Isle of Golden Dunes', 'Isla de las Dunas Doradas'), captain='desert5_captain',
                   cname='Captain Zahir', hubs=['qas_captain'], premium=True, flag='ship.desert5', grant='desert_m4',
                   hint=('Złote Wydmy są zamknięte. Karawaniarz Idris w Qasirah wie, jak zdobyć zgodę.',
                         'The Golden Dunes are closed. Caravaneer Idris in Qasirah knows how to earn leave.',
                         'Las Dunas Doradas están cerradas. El caravanero Idris en Qasirah sabe cómo obtener permiso.')),
    'tropic': dict(name=('Wyspa Szmaragdowej Mgły', 'Isle of Emerald Mist', 'Isla de la Niebla Esmeralda'), captain='tropic5_captain',
                   cname='Captain Marisol', hubs=['selv_captain'], premium=True, flag='ship.tropic5', grant='tropic_m4',
                   hint=('Mgła jest gęsta, a rafy zdradliwe. Botanik Ilvane w Selvaran da ci przepustkę, jeśli na nią zasłużysz.',
                         'The mist is thick and the reefs treacherous. Botanist Ilvane in Selvaran will give you a pass if you earn it.',
                         'La niebla es espesa y los arrecifes traicioneros. La botánica Ilvane en Selvaran te dará un pase si lo mereces.')),
    'oak': dict(name=('Wyspa Starych Dębów', 'Isle of Ancient Oaks', 'Isla de los Robles Antiguos'), captain='oak5_captain',
                cname='Captain Edwin', hubs=['varden_captain', 'valmir_captain'], premium=True, flag='ship.oak5', grant='oak_m4',
                hint=('Stare Dęby wpuszczają tylko znanych gościom Straży Corwina z Varden. Zdobądź jego list.',
                      'The Ancient Oaks admit only guests known to Warden Corwin of Varden. Earn his letter.',
                      'Los Robles Antiguos solo admiten a quienes conoce el guardián Corwin de Varden. Consigue su carta.')),
}


def ship_access_rows():
    rows = ['# kapitan (cel rejsu);wymog wejscia (flaga postaci) albo -;konto: premium wymagane (1) albo rejs darmowy (0);quest ustawiajacy flage;podpowiedz kapitana (pl)']
    rows.append('quay_captain;-;0;-;')
    for k, v in ISLANDS.items():
        rows.append(f"{v['captain']};{v['flag']};{1 if v['premium'] else 0};{v['grant']};{v['hint'][0]}")
    return rows


# ====================================================================================== zadania
# misja: (sufiks, rodzaj, cel, ilosc, (nazwa pl, en, es), (opis pl, en, es))
# linia: dict(key, giver, level, tier, premium, line=(pl,en,es), where=(pl,en,es), missions=[...], rewards=[przedmiot:ilosc|None], requires=[klucze misji])
def M(suffix, kind, target, amount, name, desc):
    return (suffix, kind, target, amount, name, desc)


LINES = []


def line(key, giver, level, premium, line_name, where, missions, rewards=None, requires=(), tier=None, split=None):
    LINES.append(dict(key=key, giver=giver, level=level, premium=premium, line=line_name, where=where, missions=missions,
                      rewards=rewards or [None] * len(missions), requires=list(requires), tier=tier, split=split))


# ---------------------------------------------------------------- darmowy quest promowego (kontynent -> Wyspa Szeptow)
line('ferry', 'ferry_oswin', 8, False, ('Broszka Przewoźnika', "The Ferryman's Brooch", 'El broche del barquero'),
     ('Cicha Przystań na wschodnim wybrzeżu', 'Quiet Landing on the east coast', 'Embarcadero Tranquilo en la costa este'), [
         M('m1', 'VISIT', 'Nora Sroki', 1, ('Do nory złodziei', 'To the thieves\' burrow', 'A la madriguera de los ladrones'),
           ('Oswin nie pożyczy łodzi nikomu, kto nie zna drogi: odszukaj Norę Sroki na polach na wschód od Varden i wejdź do jej wnętrza. Wejście pilnują bandyci.',
            'Oswin will lend his boat only to someone who knows the way: find the Magpie Burrow on the fields east of Varden and step inside. Bandits guard the door.',
            'Oswin solo presta su barca a quien conoce el camino: busca la Madriguera de la Urraca en los campos al este de Varden y entra. Unos bandidos vigilan la puerta.')),
         M('m2', 'KILL', 'bandit', 12, ('Straż Sroki', 'The Magpie\'s Guard', 'La guardia de la urraca'),
           ('Bandyci pilnują norę. Pokonaj 12, a brama do komnaty króla ustąpi.',
            'Bandits guard the burrow. Defeat 12 and the gate to the king\'s hall will give way.',
            'Los bandidos guardan la madriguera. Derrota a 12 y se abrirá la puerta de la sala del rey.')),
         M('m3', 'KILL', 'magpie_king', 1, ('Król Sroczy', 'The Magpie King', 'El Rey de las urracas'),
           ('Król Sroczy trzyma broszkę w skarbcu za swoją komnatą. Pokonaj go, a klucz do skarbca spadnie z jego pasa.',
            'The Magpie King keeps the brooch in a vault behind his hall. Defeat him and the vault key falls from his belt.',
            'El Rey de las urracas guarda el broche en una cámara tras su sala. Véncelo y la llave caerá de su cinto.')),
         M('m4', 'COLLECT', 'silver_brooch', 1, ('Srebrna broszka', 'The Silver Brooch', 'El broche de plata'),
           ('Otwórz skrzynię w skarbcu i przynieś Oswinowi srebrną broszkę jego rodziny. Za to zabierze cię na Wyspę Szeptów.',
            'Open the chest in the vault and bring Oswin his family\'s silver brooch. In return he will take you to the Isle of Whispers.',
            'Abre el cofre de la cámara y llévale a Oswin el broche de plata de su familia. A cambio te llevará a la Isla de los Susurros.')),
     ], rewards=[None, 'health_potion:10', 'mana_potion:10', 'health_potion:15'], tier='easy')

# ---------------------------------------------------------------- Wyspa Szeptow: zadania poboczne (darmowe), 8 linii po 2 misje
WG = ['whisper_hermit', 'whisper_keeper', 'whisper_miner']
line('wh_crabs', WG[1], 8, False, ('Krabie Plaże', 'Crab Beaches', 'Playas de cangrejos'), ('plaże Wyspy Szeptów', 'beaches of the Isle of Whispers', 'playas de la Isla de los Susurros'), [
    M('m1', 'KILL', 'shore_scuttler', 10, ('Szczypce na plaży', 'Pincers on the Beach', 'Pinzas en la playa'),
      ('Kraby podcinają sieci rybaków. Pokonaj 10.', 'Crabs cut the fishermen\'s nets. Defeat 10.', 'Los cangrejos cortan las redes. Derrota a 10.')),
    M('m2', 'COLLECT', 'pearl', 3, ('Perły z krabów', 'Pearls from Crabs', 'Perlas de cangrejo'),
      ('W skorupach kryją się perły. Przynieś 3.', 'Pearls hide in the shells. Bring 3.', 'Hay perlas en los caparazones. Trae 3.'))], tier='easy')
line('wh_wolves', WG[0], 10, False, ('Wilki z Wzgórz', 'Wolves of the Hills', 'Lobos de las colinas'), ('wzgórza Wyspy Szeptów', 'hills of the Isle of Whispers', 'colinas de la Isla de los Susurros'), [
    M('m1', 'KILL', 'wolf', 10, ('Wilcza wataha', 'The Wolf Pack', 'La manada de lobos'),
      ('Wataha wyje pod chatą pustelniczki. Pokonaj 10 wilków.', 'A pack howls outside the hermit\'s hut. Defeat 10 wolves.', 'Una manada aúlla frente a la cabaña. Derrota a 10 lobos.')),
    M('m2', 'KILL', 'boar', 6, ('Dziki w uprawach', 'Boars in the Crops', 'Jabalíes en los cultivos'),
      ('Dziki rozkopują ogródek. Pokonaj 6.', 'Boars dig up the garden. Defeat 6.', 'Los jabalíes escarban el huerto. Derrota a 6.'))], tier='easy')
line('wh_spiders', WG[0], 14, False, ('Pajęczyna Pustelniczki', 'The Hermit\'s Webs', 'Las telarañas de la ermitaña'), ('jaskinie Wyspy Szeptów', 'caves of the Isle of Whispers', 'cuevas de la Isla de los Susurros'), [
    M('m1', 'KILL', 'forest_spider', 10, ('Pająki w grotach', 'Spiders in the Grottos', 'Arañas en las grutas'),
      ('Pająki zasnuły groty. Pokonaj 10.', 'Spiders have webbed the grottos. Defeat 10.', 'Las arañas cubrieron las grutas. Derrota a 10.')),
    M('m2', 'COLLECT', 'spider_silk', 8, ('Jedwab na szaty', 'Silk for Robes', 'Seda para túnicas'),
      ('Pustelniczka tka szaty. Przynieś 8 kłębków jedwabiu.', 'The hermit weaves robes. Bring 8 skeins of silk.', 'La ermitaña teje túnicas. Trae 8 madejas de seda.'))], tier='easy')
line('wh_goblins', WG[2], 20, False, ('Gobliny z Kopalni', 'Goblins of the Mine', 'Goblins de la mina'), ('Kopalnia Cichych Ech', 'Mine of Quiet Echoes', 'Mina de los Ecos Silenciosos'), [
    M('m1', 'KILL', 'goblin', 12, ('Najeźdźcy szybu', 'Raiders of the Shaft', 'Invasores del pozo'),
      ('Gobliny zajęły szyb. Pokonaj 12.', 'Goblins took the shaft. Defeat 12.', 'Los goblins ocuparon el pozo. Derrota a 12.')),
    M('m2', 'KILL', 'goblin_shaman', 6, ('Szamani szybu', 'Shaft Shamans', 'Chamanes del pozo'),
      ('Szamani podjudzają resztę. Pokonaj 6.', 'Shamans stir up the rest. Defeat 6.', 'Los chamanes incitan al resto. Derrota a 6.'))], tier='easy')
line('wh_shades', WG[1], 26, False, ('Szepty w Grotach', 'Whispers in the Grottos', 'Susurros en las grutas'), ('Groty Szeptów', 'Whispering Grottos', 'Grutas de los Susurros'), [
    M('m1', 'VISIT', 'Groty Szeptów', 1, ('Do grot', 'Into the Grottos', 'A las grutas'),
      ('Latarnik słyszy w nocy szepty z grot pod latarnią. Zejdź i sprawdź.', 'The lightkeeper hears whispers at night from the grottos below. Go down and see.', 'El farero oye susurros de noche en las grutas. Baja y mira.')),
    M('m2', 'KILL', 'whisper_shade', 10, ('Cienie szeptów', 'The Whispering Shades', 'Las sombras susurrantes'),
      ('Cienie szepczą imiona. Uciszyj 10.', 'The shades whisper names. Silence 10.', 'Las sombras susurran nombres. Silencia a 10.'))], tier='medium')
line('wh_bones', WG[2], 32, False, ('Kości Starej Sztolni', 'Bones of the Old Gallery', 'Huesos de la vieja galería'), ('Kopiec Pana Cieni', 'Mound of the Lord of Shadows', 'Túmulo del Señor de las Sombras'), [
    M('m1', 'KILL', 'skeleton', 12, ('Szkielety sztolni', 'Gallery Skeletons', 'Esqueletos de la galería'),
      ('Szkielety wędrują sztolniami. Pokonaj 12.', 'Skeletons roam the galleries. Defeat 12.', 'Los esqueletos vagan por las galerías. Derrota a 12.')),
    M('m2', 'KILL', 'cave_centipede', 10, ('Stonogi w kościach', 'Centipedes in the Bones', 'Ciempiés entre huesos'),
      ('Stonogi mieszkają w żebrach. Pokonaj 10.', 'Centipedes live in the ribs. Defeat 10.', 'Los ciempiés viven en las costillas. Derrota a 10.'))], tier='medium')
line('wh_bandits', WG[1], 38, False, ('Przemytnicy z Zatoki', 'Smugglers of the Cove', 'Contrabandistas de la cala'), ('wybrzeże Wyspy Szeptów', 'coast of the Isle of Whispers', 'costa de la Isla de los Susurros'), [
    M('m1', 'KILL', 'snare_setter', 10, ('Pułapki na plaży', 'Traps on the Shore', 'Trampas en la orilla'),
      ('Przemytnicy zastawiają sidła. Pokonaj 10.', 'Smugglers set snares. Defeat 10.', 'Los contrabandistas ponen trampas. Derrota a 10.')),
    M('m2', 'KILL', 'tunnel_fence', 8, ('Paserzy', 'The Fences', 'Los receptadores'),
      ('Paserzy skupują łup. Pokonaj 8.', 'Fences buy the loot. Defeat 8.', 'Los receptadores compran el botín. Derrota a 8.'))], tier='medium')
line('wh_cairn', WG[2], 44, False, ('Pan Kopca', 'Lord of the Cairn', 'El señor del túmulo'), ('Kopiec Pana Cieni', 'Mound of the Lord of Shadows', 'Túmulo del Señor de las Sombras'), [
    M('m1', 'KILL', 'cairn_wight', 12, ('Strażnicy kopca', 'Wardens of the Cairn', 'Guardianes del túmulo'),
      ('Upiory pilnują wejścia do komnaty króla. Pokonaj 12.', 'Wights guard the way to the king\'s chamber. Defeat 12.', 'Los aparecidos guardan la cámara del rey. Derrota a 12.')),
    M('m2', 'KILL', 'cairn_lord', 1, ('Koniec dynastii', 'End of a Dynasty', 'Fin de una dinastía'),
      ('Pan Kopca wciąż rządzi martwymi. Pokonaj go na dnie kopca.', 'The Lord of the Cairn still rules the dead. Defeat him at the bottom of the mound.', 'El Señor del túmulo aún manda a los muertos. Véncelo en el fondo.'))], tier='medium')

# ---------------------------------------------------------------- zadania dostepu premium (5 misji, krzyzowe wymagania)
# lodowa: darmowa Wyspa Szeptow (ferry + Pan Kopca) + yeti z Frostholm (poz. 65)
line('ice', 'ice_halvar', 60, True, ('Pozwolenie Halvara', "Halvar's Leave", 'El permiso de Halvar'),
     ('Frostholm, nabrzeże', 'Frostholm, the quay', 'Frostholm, el muelle'), [
         M('m1', 'VISIT', 'Punkt Halvara', 1, ('Latarnia Halvara', "Halvar's Lantern", 'El faro de Halvar'),
           ('Kartograf Halvar rysuje mapę mroźnej wyspy, ale potrzebuje pewnych rąk. Stań przy jego stole map na nabrzeżu.',
            'Cartographer Halvar charts a frozen isle but needs steady hands. Stand at his map table on the quay.',
            'El cartógrafo Halvar dibuja una isla helada y necesita manos firmes. Ponte junto a su mesa de mapas en el muelle.')),
         M('m2', 'COLLECT', 'yeti_fur', 8, ('Futra dla przewodników', 'Furs for the Guides', 'Pieles para los guías'),
           ('Przewodnicy wyprawy potrzebują ciepłych futer. Przynieś 8 futer yeti.', 'Expedition guides need warm furs. Bring 8 yeti furs.', 'Los guías necesitan pieles abrigadas. Trae 8 pieles de yeti.')),
         M('m3', 'KILL', 'ice_golem', 10, ('Golemy na szlaku', 'Golems on the Trail', 'Gólems en el sendero'),
           ('Lodowe golemy zagradzają szlak na północ. Pokonaj 10.', 'Ice golems block the northern trail. Defeat 10.', 'Los gólems de hielo bloquean el sendero norte. Derrota a 10.')),
         M('m4', 'KILL', 'cairn_lord', 1, ('Dowód ze świata', 'Proof from Afar', 'Prueba de lejos'),
           ('Halvar chce dowodu, że sprostasz wyspie: Pan Kopca z Wyspy Szeptów musi upaść. Gdy wrócisz, dostaniesz pozwolenie na rejs.',
            'Halvar wants proof you can handle an island: the Lord of the Cairn on the Isle of Whispers must fall. Then you earn the sailing leave.',
            'Halvar quiere pruebas de que puedes con una isla: el Señor del túmulo en la Isla de los Susurros debe caer. Luego tendrás el permiso.')),
         M('m5', 'VISIT', 'Wyspa Białej Zamieci', 1, ('Pierwsza zaspa', 'First Snowdrift', 'Primera ventisca'),
           ('Z pozwoleniem popłyń na Wyspę Białej Zamieci i postaw stopę na jej brzegu. Halvar odliczy resztę nagrody.',
            'With the leave, sail to the Isle of the White Blizzard and set foot on its shore. Halvar will count out the rest of your reward.',
            'Con el permiso, navega a la Isla de la Ventisca Blanca y pisa su orilla. Halvar te dará el resto de la recompensa.')),
     ], rewards=[None, 'great_health_potion:10', 'great_mana_potion:10', 'strong_health_potion:6', 'strong_mana_potion:6'],
     requires=['ferry_m4', 'frost_yeti'], tier='mediumhard')
line('desert', 'desert_idris', 90, True, ('Zgoda Karawaniarza', "The Caravaneer's Consent", 'El consentimiento del caravanero'),
     ('Qasirah, port', 'Qasirah, the harbour', 'Qasirah, el puerto'), [
         M('m1', 'VISIT', 'Namiot Idrisa', 1, ('Namiot Idrisa', "Idris's Tent", 'La tienda de Idris'),
           ('Karawaniarz Idris zbiera ludzi na wyprawę przez Złote Wydmy. Odwiedź jego namiot w porcie.', 'Caravaneer Idris is gathering a crew for the Golden Dunes. Visit his tent in the harbour.', 'El caravanero Idris reúne gente para las Dunas Doradas. Visita su tienda en el puerto.')),
         M('m2', 'COLLECT', 'scorpion_tail', 12, ('Jad na wyprawę', 'Venom for the Journey', 'Veneno para el viaje'),
           ('Medyk karawany potrzebuje jadu. Przynieś 12 żądeł skorpionów.', 'The caravan healer needs venom. Bring 12 scorpion tails.', 'El curandero necesita veneno. Trae 12 aguijones de escorpión.')),
         M('m3', 'KILL', 'sand_wraith', 12, ('Widma szlaku', 'Wraiths of the Trail', 'Espectros del camino'),
           ('Widma pustyni gubią karawany. Pokonaj 12.', 'Desert wraiths lose caravans. Defeat 12.', 'Los espectros pierden caravanas. Derrota a 12.')),
         M('m4', 'KILL', 'rime_jarl', 1, ('Jarl i piasek', 'The Jarl and the Sand', 'El jarl y la arena'),
           ('Idris sprzedaje szlak tylko tym, którzy pokonali Jarla Szronu na Wyspie Białej Zamieci (drzwi wymagają drużyny).',
            'Idris sells the way only to those who defeated the Jarl of the Rime on the Isle of the White Blizzard (the door needs a party).',
            'Idris vende el camino solo a quien venció al Jarl de la escarcha en la Isla de la Ventisca Blanca (la puerta exige grupo).')),
         M('m5', 'VISIT', 'Wyspa Złotych Wydm', 1, ('Pierwsza wydma', 'First Dune', 'Primera duna'),
           ('Popłyń na Wyspę Złotych Wydm i wejdź na jej piasek. Idris zapłaci resztę.', 'Sail to the Isle of Golden Dunes and step onto its sand. Idris will pay the rest.', 'Navega a la Isla de las Dunas Doradas y pisa su arena. Idris pagará el resto.')),
     ], rewards=[None, 'great_health_potion:15', 'great_mana_potion:15', 'strong_health_potion:8', 'strong_mana_potion:8'],
     requires=['ice_m5', 'qas_scorpions'], tier='mediumhard')
line('tropic', 'tropic_ilvane', 120, True, ('Przepustka Botaniczki', "The Botanist's Pass", 'El pase de la botánica'),
     ('Selvaran, przystań', 'Selvaran, the dock', 'Selvaran, el muelle'), [
         M('m1', 'VISIT', 'Szklarnia Ilvane', 1, ('Szklarnia Ilvane', "Ilvane's Glasshouse", 'El invernadero de Ilvane'),
           ('Botanik Ilvane bada kwiaty ze Szmaragdowej Mgły. Odwiedź jej szklarnię przy przystani.', 'Botanist Ilvane studies flowers of the Emerald Mist. Visit her glasshouse by the dock.', 'La botánica Ilvane estudia las flores de la Niebla Esmeralda. Visita su invernadero junto al muelle.')),
         M('m2', 'COLLECT', 'croc_leather', 10, ('Skóra na buty', 'Hide for Boots', 'Cuero para botas'),
           ('Wyprawa potrzebuje mocnych butów. Przynieś 10 skór krokodyla.', 'The expedition needs sturdy boots. Bring 10 crocodile hides.', 'La expedición necesita botas fuertes. Trae 10 cueros de cocodrilo.')),
         M('m3', 'KILL', 'jungle_serpent', 15, ('Węże na przesmyku', 'Serpents on the Isthmus', 'Serpientes en el istmo'),
           ('Węże roją się przy mglistym przesmyku. Pokonaj 15.', 'Serpents swarm the misty isthmus. Defeat 15.', 'Las serpientes pululan en el istmo. Derrota a 15.')),
         M('m4', 'KILL', 'sand_regent', 1, ('Regent z pustyni', 'The Regent of the Desert', 'El regente del desierto'),
           ('Ilvane zna szlak, ale chce ręczyć za ciebie: pokonaj Regenta Burzy Piaskowej na Złotych Wydmach.', 'Ilvane knows the route but wants a voucher: defeat the Regent of the Sandstorm on the Golden Dunes.', 'Ilvane conoce la ruta pero quiere un aval: vence al Regente de la tormenta de arena en las Dunas Doradas.')),
         M('m5', 'VISIT', 'Wyspa Szmaragdowej Mgły', 1, ('Pierwsza mgła', 'First Mist', 'Primera niebla'),
           ('Wejdź na Wyspę Szmaragdowej Mgły. Ilvane wypłaci resztę.', 'Step onto the Isle of Emerald Mist. Ilvane will pay the rest.', 'Pisa la Isla de la Niebla Esmeralda. Ilvane pagará el resto.')),
     ], rewards=[None, 'great_health_potion:15', 'great_mana_potion:15', 'strong_health_potion:10', 'ultimate_spirit_potion:10'],
     requires=['desert_m5', 'selv_crocs'], tier='hard')
line('oak', 'oak_corwin', 180, True, ('List Strażnika', "The Warden's Letter", 'La carta del guardián'),
     ('Varden, przystań', 'Varden, the harbour', 'Varden, el puerto'), [
         M('m1', 'VISIT', 'Posterunek Corwina', 1, ('Posterunek Corwina', "Corwin's Post", 'El puesto de Corwin'),
           ('Strażnik Corwin pilnuje listy gości Starych Dębów. Zgłoś się na jego posterunek w porcie.', 'Warden Corwin keeps the guest list of the Ancient Oaks. Report to his post in the harbour.', 'El guardián Corwin lleva la lista de invitados de los Robles Antiguos. Preséntate en su puesto del puerto.')),
         M('m2', 'COLLECT', 'wyvern_scale', 6, ('Łuski na pieczęć', 'Scales for the Seal', 'Escamas para el sello'),
           ('Pieczęć listu odlewa się z łusek. Przynieś 6 łusek wiwerny.', 'The letter seal is cast from scales. Bring 6 wyvern scales.', 'El sello se funde con escamas. Trae 6 escamas de wyvern.')),
         M('m3', 'KILL', 'wraith_knight', 12, ('Upiory szlaku', 'Wraiths of the Road', 'Espectros del camino'),
           ('Rycerze-upiory zagradzają drogę do portu. Pokonaj 12.', 'Wraith knights block the road to the port. Defeat 12.', 'Los caballeros espectro bloquean el camino. Derrota a 12.')),
         M('m4', 'KILL', 'vine_empress', 1, ('Cesarzowa i dąb', 'The Empress and the Oak', 'La emperatriz y el roble'),
           ('Corwin wpisze cię na listę, gdy pokonasz Cesarzową Pnączy na Szmaragdowej Mgle.', 'Corwin will add you to the list once you defeat the Empress of Vines in the Emerald Mist.', 'Corwin te añadirá a la lista cuando venzas a la Emperatriz de las enredaderas en la Niebla Esmeralda.')),
         M('m5', 'VISIT', 'Wyspa Starych Dębów', 1, ('Pierwszy dąb', 'First Oak', 'Primer roble'),
           ('Wejdź pod Stare Dęby. Corwin wypłaci resztę.', 'Walk beneath the Ancient Oaks. Corwin will pay the rest.', 'Camina bajo los Robles Antiguos. Corwin pagará el resto.')),
     ], rewards=[None, 'supreme_health_potion:8', 'supreme_mana_potion:8', 'supreme_health_potion:6', 'supreme_mana_potion:6'],
     requires=['tropic_m5', 'ice_m5'], tier='hard')

# ---------------------------------------------------------------- zadania poboczne na wyspach premium (po 3 na wyspe, po 2 misje)
PG = {'ice': ['ice_skuld', 'ice_ragna'], 'desert': ['desert_nadira', 'desert_omar'], 'tropic': ['tropic_tupa', 'tropic_inez'],
      'oak': ['oak_hollis', 'oak_maeve']}
SIDE = [
    ('ice', PG['ice'][0], 65, ('Kły Zimy', 'Fangs of Winter', 'Colmillos del invierno'), 'yeti', 12, 'frost_lurker', 10,
     ('Yeti na lodowisku', 'Yetis on the Ice', 'Yetis en el hielo'), ('Yeti niszczą obóz. Pokonaj 12.', 'Yetis wreck the camp. Defeat 12.', 'Los yetis destrozan el campamento. Derrota a 12.'),
     ('Czyhacze zasp', 'Drift Lurkers', 'Acechadores de ventisca'), ('Białe czyhacze ukryte w zaspach. Pokonaj 10.', 'White lurkers hide in the drifts. Defeat 10.', 'Los acechadores se ocultan en la nieve. Derrota a 10.')),
    ('ice', PG['ice'][1], 110, ('Szron w Jaskiniach', 'Rime in the Caves', 'Escarcha en las cuevas'), 'rimecloak_pilgrim', 12, 'ice_golem', 10,
     ('Zamarznięci pielgrzymi', 'Frozen Pilgrims', 'Peregrinos helados'), ('Pielgrzymi krążą po jaskiniach. Pokonaj 12.', 'Pilgrims wander the caves. Defeat 12.', 'Los peregrinos vagan por las cuevas. Derrota a 12.'),
     ('Golemy lodowych sal', 'Golems of the Ice Halls', 'Gólems de las salas heladas'), ('Golemy strzegą sal. Pokonaj 10.', 'Golems guard the halls. Defeat 10.', 'Los gólems guardan las salas. Derrota a 10.')),
    ('ice', PG['ice'][0], 200, ('Smoki Zamieci', 'Dragons of the Blizzard', 'Dragones de la ventisca'), 'hoarfrost_dragon', 8, 'frost_centipede', 10,
     ('Szronowe smoki', 'Rime Dragons', 'Dragones de escarcha'), ('Smoki gnieżdżą się w turni. Pokonaj 8.', 'Dragons nest in the spire. Defeat 8.', 'Los dragones anidan en la aguja. Derrota a 8.'),
     ('Stonogi lodowych sal', 'Centipedes of the Ice Halls', 'Ciempiés de las salas heladas'), ('Stonogi gryzą wędrowców. Pokonaj 10.', 'Centipedes bite travellers. Defeat 10.', 'Los ciempiés muerden a los viajeros. Derrota a 10.')),
    ('desert', PG['desert'][0], 95, ('Żądła Wydm', 'Stings of the Dunes', 'Aguijones de las dunas'), 'sand_devourer', 12, 'gilded_beetle', 10,
     ('Pożeracze piasku', 'Sand Devourers', 'Devoradores de arena'), ('Pożeracze podkopują szlak. Pokonaj 12.', 'Devourers undermine the trail. Defeat 12.', 'Los devoradores socavan el sendero. Derrota a 12.'),
     ('Złociste żuki', 'Gilded Beetles', 'Escarabajos dorados'), ('Żuki gryzą wozy. Pokonaj 10.', 'Beetles gnaw the wagons. Defeat 10.', 'Los escarabajos roen los carros. Derrota a 10.')),
    ('desert', PG['desert'][1], 150, ('Cień Obelisków', 'Shadow of the Obelisks', 'Sombra de los obeliscos'), 'dune_skulker', 12, 'dune_zealot', 10,
     ('Łaziki wydm', 'Dune Skulkers', 'Rondadores de dunas'), ('Łaziki napadają z piasku. Pokonaj 12.', 'Skulkers ambush from the sand. Defeat 12.', 'Los rondadores atacan desde la arena. Derrota a 12.'),
     ('Fanatycy wydm', 'Dune Zealots', 'Fanáticos de las dunas'), ('Fanatycy palą pielgrzymów. Pokonaj 10.', 'Zealots burn pilgrims. Defeat 10.', 'Los fanáticos queman peregrinos. Derrota a 10.')),
    ('desert', PG['desert'][0], 240, ('Smocze Gniazda', 'Dragon Nests', 'Nidos de dragón'), 'crag_dragon', 8, 'obelisk_priest', 10,
     ('Smoki skalne', 'Crag Dragons', 'Dragones de risco'), ('Smoki gnieżdżą się w skałach. Pokonaj 8.', 'Dragons nest in the crags. Defeat 8.', 'Los dragones anidan en los riscos. Derrota a 8.'),
     ('Kapłani obelisków', 'Obelisk Priests', 'Sacerdotes de obelisco'), ('Kapłani palą ścieżki. Pokonaj 10.', 'Priests burn the paths. Defeat 10.', 'Los sacerdotes queman los senderos. Derrota a 10.')),
    ('tropic', PG['tropic'][0], 120, ('Dżungla pod Mgłą', 'Jungle under Mist', 'Selva bajo la niebla'), 'marsh_lizard', 12, 'canopy_lurker', 10,
     ('Jaszczury bagien', 'Marsh Lizards', 'Lagartos del pantano'), ('Jaszczury zalegają w brodach. Pokonaj 12.', 'Lizards lurk in the fords. Defeat 12.', 'Los lagartos acechan en los vados. Derrota a 12.'),
     ('Czyhacze koron', 'Canopy Lurkers', 'Acechadores del dosel'), ('Koty spadają z drzew. Pokonaj 10.', 'Cats drop from the trees. Defeat 10.', 'Los felinos caen de los árboles. Derrota a 10.')),
    ('tropic', PG['tropic'][1], 200, ('Ogród Mimików', 'The Mimic Garden', 'El jardín de las mímicas'), 'orchid_mimic', 12, 'bog_hydra', 8,
     ('Storczyki zabójcy', 'Killer Orchids', 'Orquídeas asesinas'), ('Storczyki wabią zapachem. Pokonaj 12.', 'Orchids lure with scent. Defeat 12.', 'Las orquídeas atraen con su aroma. Derrota a 12.'),
     ('Hydry mokradeł', 'Hydras of the Fens', 'Hidras de los pantanos'), ('Hydry zagrażają zbieraczom. Pokonaj 8.', 'Hydras threaten gatherers. Defeat 8.', 'Las hidras amenazan a los recolectores. Derrota a 8.')),
    ('tropic', PG['tropic'][0], 300, ('Głębia Dżungli', 'Heart of the Jungle', 'Corazón de la selva'), 'venom_hydra', 8, 'stone_gorgon', 8,
     ('Jadowite hydry', 'Venom Hydras', 'Hidras venenosas'), ('Hydry zatruwają strumienie. Pokonaj 8.', 'Hydras poison the streams. Defeat 8.', 'Las hidras envenenan los arroyos. Derrota a 8.'),
     ('Gorgony ruin', 'Gorgons of the Ruins', 'Gorgonas de las ruinas'), ('Gorgony zamieniają w kamień. Pokonaj 8.', 'Gorgons turn men to stone. Defeat 8.', 'Las gorgonas petrifican. Derrota a 8.')),
    ('oak', PG['oak'][0], 130, ('Cierń i Kieł', 'Thorn and Tusk', 'Espina y colmillo'), 'briar_boar', 12, 'moor_crone', 10,
     ('Cierniowe odyńce', 'Briar Boars', 'Jabalíes de zarzas'), ('Odyńce niszczą pola. Pokonaj 12.', 'Boars ruin the fields. Defeat 12.', 'Los jabalíes arruinan los campos. Derrota a 12.'),
     ('Wiedźmy wrzosowisk', 'Moor Crones', 'Brujas del páramo'), ('Wiedźmy rzucają klątwy. Pokonaj 10.', 'Crones cast curses. Defeat 10.', 'Las brujas lanzan maldiciones. Derrota a 10.')),
    ('oak', PG['oak'][1], 200, ('Skrzydła nad Dębiną', 'Wings over the Oakwood', 'Alas sobre el robledal'), 'crimson_shade', 10, 'wyvern', 8,
     ('Szkarłatne cienie', 'Crimson Shades', 'Sombras carmesíes'), ('Cienie pojawiają się o zmroku. Pokonaj 10.', 'Shades appear at dusk. Defeat 10.', 'Las sombras aparecen al anochecer. Derrota a 10.'),
     ('Wywerny z klifów', 'Wyverns of the Cliffs', 'Wyverns de los acantilados'), ('Wywerny porywają bydło. Pokonaj 8.', 'Wyverns carry off cattle. Defeat 8.', 'Los wyverns se llevan el ganado. Derrota a 8.')),
    ('oak', PG['oak'][0], 320, ('Dęby Nocy', 'Oaks of the Night', 'Robles de la noche'), 'cursed_treant', 10, 'wraith_knight', 10,
     ('Przeklęte drzewce', 'Cursed Treants', 'Troncos malditos'), ('Drzewce duszą las. Pokonaj 10.', 'Treants strangle the forest. Defeat 10.', 'Los troncos ahogan el bosque. Derrota a 10.'),
     ('Upiorni rycerze', 'Wraith Knights', 'Caballeros espectro'), ('Rycerze patrolują ruiny. Pokonaj 10.', 'Knights patrol the ruins. Defeat 10.', 'Los caballeros patrullan las ruinas. Derrota a 10.')),
]
for (isl, giver, lv, ln, t1, n1, t2, n2, nm1, ds1, nm2, ds2) in SIDE:
    key = f'{isl}_s{lv}'
    line(key, giver, lv, True, ln, (ISLANDS[isl]['name'][0], ISLANDS[isl]['name'][1], ISLANDS[isl]['name'][2]),
         [M('m1', 'KILL', t1, n1, nm1, ds1), M('m2', 'KILL', t2, n2, nm2, ds2)], requires=[f'{isl}_m4' if isl != 'oak' else 'oak_m4'])

# NPC: klucz: (imie, kolor, powitanie pl/en/es, miejsce)
NPCS = {
    'quay_captain': ('Helmsman Brenn', '#4a6a8a', ('Płyniemy na Wyspę Szeptów, ale tylko z listem przewoźnika Oswina. Pytaj go o broszkę.',
                                                   'We sail to the Isle of Whispers, but only with the ferryman Oswin\'s letter. Ask him about the brooch.',
                                                   'Navegamos a la Isla de los Susurros, pero solo con la carta del barquero Oswin. Pregúntale por el broche.')),
    'ferry_oswin': ('Ferryman Oswin', '#4a6a8a', ('Statek jest mój, ale łódź na wyspę pożyczę tylko komuś, kto odzyska rodzinną broszkę od Króla Sroczego.',
                                                  'The ship is mine, but I lend the boat to the isle only to someone who gets my family brooch back from the Magpie King.',
                                                  'El barco es mío, pero solo presto la barca a quien recupere el broche de mi familia del Rey de las urracas.')),
    'whisper_hermit': ('Hermit Maren', '#7a8a6a', ('Wilki i pająki wyją mi pod chatą. Pomożesz starej pustelniczce?', 'Wolves and spiders howl outside my hut. Will you help an old hermit?', 'Lobos y arañas aúllan frente a mi cabaña. ¿Ayudas a una vieja ermitaña?')),
    'whisper_keeper': ('Lightkeeper Dunstan', '#8a8aa0', ('Latarnia świeci, ale z grot pod nią słychać szepty. Ktoś musi to zbadać.', 'The lighthouse shines, but whispers rise from the grottos beneath. Someone must look.', 'El faro brilla, pero de las grutas suben susurros. Alguien debe mirar.')),
    'whisper_miner': ('Prospector Hale', '#a08060', ('Kopalnia i kopiec to moje życie, a teraz roją się tam gobliny i kości.', 'The mine and the cairn are my life, and now goblins and bones infest them.', 'La mina y el túmulo son mi vida, y ahora los infestan goblins y huesos.')),
    'ice_halvar': ('Cartographer Halvar', '#9ab8d8', ('Mapa Białej Zamieci jest niedokończona. Pomóż, a dostaniesz pozwolenie na rejs.', 'My chart of the White Blizzard is unfinished. Help me and you will earn leave to sail.', 'Mi mapa de la Ventisca Blanca está inconclusa. Ayúdame y tendrás permiso para navegar.')),
    'desert_idris': ('Caravaneer Idris', '#d0a050', ('Złote Wydmy są piękne i zabójcze. Karawana ruszy z tobą, jeśli zasłużysz na zgodę.', 'The Golden Dunes are beautiful and deadly. My caravan will go with you if you earn consent.', 'Las Dunas Doradas son hermosas y mortales. Mi caravana irá contigo si ganas el consentimiento.')),
    'tropic_ilvane': ('Botanist Ilvane', '#60a070', ('Szmaragdowa Mgła kryje kwiaty, jakich nikt nie widział. Dam ci przepustkę, gdy się wykażesz.', 'The Emerald Mist hides flowers no one has seen. I will give you a pass once you prove yourself.', 'La Niebla Esmeralda oculta flores nunca vistas. Te daré un pase cuando te lo ganes.')),
    'oak_corwin': ('Warden Corwin', '#7a6a4a', ('Stare Dęby wpuszczają tylko tych, których znam. Zasłuż na mój list.', 'The Ancient Oaks admit only those I know. Earn my letter.', 'Los Robles Antiguos solo admiten a quienes conozco. Gánate mi carta.')),
    'ice_skuld': ('Outfitter Skuld', '#a8c8e8', ('Futra, haki i raki dla wyprawy. Mam też zlecenia na yeti i lodowe stwory.', 'Furs, hooks and crampons for the trip. I also have jobs about yetis and ice beasts.', 'Pieles, ganchos y crampones. También tengo encargos de yetis y bestias de hielo.')),
    'ice_ragna': ('Warden Ragna', '#88a8c8', ('Pielgrzymi i golemy chodzą po jaskiniach. Pomożesz?', 'Pilgrims and golems walk the caves. Will you help?', 'Peregrinos y gólems caminan por las cuevas. ¿Ayudas?')),
    'desert_nadira': ('Scout Nadira', '#d8b060', ('Wydmy się przesuwają, a pod nimi czają się żądła. Mam zlecenia.', 'The dunes shift and stings lurk beneath. I have jobs.', 'Las dunas se mueven y bajo ellas acechan aguijones. Tengo encargos.')),
    'desert_omar': ('Priest-Hunter Omar', '#c09040', ('Obeliski gniewają się na ludzi. Pomóż mi je uciszyć.', 'The obelisks are angry at men. Help me quiet them.', 'Los obeliscos están furiosos con los hombres. Ayúdame a calmarlos.')),
    'tropic_tupa': ('Guide Tupa', '#78a868', ('Dżungla zjada nieostrożnych. Znam ścieżki i zlecenia.', 'The jungle eats the careless. I know the trails and the jobs.', 'La selva se come a los descuidados. Conozco senderos y encargos.')),
    'tropic_inez': ('Herbalist Inez', '#b87898', ('Storczyki i hydry mi przeszkadzają w zbiorach. Pomożesz?', 'Orchids and hydras ruin my harvest. Will you help?', 'Orquídeas e hidras arruinan mi cosecha. ¿Ayudas?')),
    'oak_hollis': ('Ranger Hollis', '#6a8a4a', ('Odyńce i wiedźmy psują lasy Dębów. Pomóż straży.', 'Boars and crones spoil the Oak woods. Help the rangers.', 'Jabalíes y brujas arruinan el robledal. Ayuda a los guardas.')),
    'oak_maeve': ('Knight-Captain Maeve', '#8a7a8a', ('Orkowie i strażnicy ruin to problem dla stolicy. Mam zlecenia na nich.', 'Orcs and ruin sentries trouble the realm. I have jobs for them.', 'Orcos y centinelas dan problemas al reino. Tengo encargos para ellos.')),
}
# regiony VISIT na kontynencie (obok zleceniodawcy): nazwa -> nazwy tlumaczen
VISIT_PLACES = {
    'Nora Sroki': ('Magpie Burrow', 'Madriguera de la Urraca'),
    'Obóz Wodza Zamieci': ('Camp of the Blizzard Warchief', 'Campamento del Jefe de la Ventisca'),
    'Obóz Hetmana Skarna': ("Hetman Skarn's Camp", 'Campamento del hetman Skarn'),
    'Skarbiec Sroki (głębiny)': ('Magpie Hoard (depths)', 'Tesoro de la Urraca (profundidades)'),
    'Groty Szeptów': ('Whispering Grottos', 'Grutas de los Susurros'),
    'Punkt Halvara': ("Halvar's Table", 'Mesa de Halvar'),
    'Namiot Idrisa': ("Idris's Tent", 'Tienda de Idris'),
    'Szklarnia Ilvane': ("Ilvane's Glasshouse", 'Invernadero de Ilvane'),
    'Posterunek Corwina': ("Corwin's Post", 'Puesto de Corwin'),
}
for _k, _v in ISLANDS.items():
    VISIT_PLACES[_v['name'][0]] = (_v['name'][1], _v['name'][2])

TIER_EXP = {'easy': 1.0, 'medium': 1.0, 'mediumhard': 1.1, 'hard': 1.1}
SPLITS = {4: (0.10, 0.20, 0.30, 0.40), 5: (0.08, 0.14, 0.20, 0.28, 0.30), 2: (0.40, 0.60), 3: (0.15, 0.30, 0.55)}


def mission_rows():
    """(klucz, nazwa..., npc, rodzaj, cel, ilosc, exp, gold, poziom, opis) dla wszystkich misji."""
    out = []
    for ln in LINES:
        total = TIER_EXP.get(ln['tier'] or 'medium', 1.0) * level_cost(ln['level'])
        sp = SPLITS[len(ln['missions'])]
        for (suf, kind, target, amount, name, desc), f in zip(ln['missions'], sp):
            exp = int(round(total * f, -2 if total * f < 1e6 else -3))
            lo = 0.5 if (kind == 'KILL' and amount == 1) else 0.1  # progi StoryQuestsTest.rewardsOfOlderQuestsFollowOneScale
            exp = max(exp, int(round(level_cost(ln['level']) * lo, -2)))
            gold = max(100, int(round(exp * 0.05, -2)))
            out.append(dict(line=ln, key=f"{ln['key']}_{suf}", kind=kind, target=target, amount=amount, name=name, desc=desc,
                            exp=exp, gold=gold))
    return out


def splice(path, lines):
    text = open(path, encoding='utf-8').read() if os.path.exists(path) else ''
    if BEGIN in text:
        a, b = text.index(BEGIN), text.index(END) + len(END)
        text = text[:a].rstrip('\n') + '\n' + text[b:].lstrip('\n')
    text = text.rstrip('\n') + '\n' + BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    open(path, 'w', encoding='utf-8').write(text)


def write_data():
    res = lambda *p: os.path.join(RES, *p)
    # potwory, lup, bestiariusz
    splice(res('data', 'monsters.csv'), [monster_row(k) for k in MONSTERS])
    loot = []
    for k, v in MONSTERS.items():
        lv = v[3]
        if v[13]:
            t = lv ** 1.12
            loot.append(f'{k};gold_coin;1000;{int(6 * t)};{int(12 * t) + 1}')
        else:
            loot.append(f'{k};gold_coin;900;{max(2, lv // 2)};{max(8, lv * 3)}')
        for (it, pm, a, b) in v[14]:
            loot.append(f'{k};{it};{pm};{a};{b}')
    splice(res('data', 'loot.csv'), loot)
    best = [f'{k};{v[1]};{3 if v[3] > 100 else 2};{1 if v[3] > 150 else 0}' for k, v in MONSTERS.items() if not v[13]]
    splice(res('data', 'bestiary.csv'), best)
    # przedmioty
    path = res('data', 'items.csv')
    ids = [int(l.split(';')[0]) for l in open(path, encoding='utf-8') if l[:1].isdigit()]
    mine = {int(l.split(';')[0]) for l in open(path, encoding='utf-8') if l[:1].isdigit() and ';silver_brooch;' in l}
    ids = [i for i in ids if i not in mine]
    free = [i for i in range(1, 256) if i not in set(ids)]
    nid = free[0]
    rows = []
    for k, (pl, _en, _es, val, color, desc, _b, _t) in ITEMS.items():
        rows.append(f'{nid};{k};{pl};MATERIAL;20;{val};NONE;0;0;0;0;*;0;0;PHYSICAL;-;-;{color};{desc}')
        free.pop(0)
        nid = free[0]
    splice(path, rows)
    # zadania
    mr = mission_rows()
    qrows, chains, requires, lines_csv, dialogues = [], [], [], [], []
    en, es = [], []
    for m in mr:
        ln = m['line']
        qrows.append(f"{m['key']};{m['name'][0]};{ln['giver']};{m['kind']};{m['target']};{m['amount']};{m['exp']};{m['gold']};{ln['level']};{m['desc'][0]}")
    for ln in LINES:
        keys = [f"{ln['key']}_{s[0]}" for s in ln['missions']]
        for i, k in enumerate(keys):
            item = ln['rewards'][i] or '-'
            prev = keys[i - 1] if i else '-'
            chains.append(f'{k};{prev};{item}')
        if ln['requires']:
            requires.append(f"{keys[0]};{','.join(ln['requires'])}")
        tier = ln['tier'] or ('easy' if ln['level'] < 30 else 'medium' if ln['level'] < 60 else 'mediumhard' if ln['level'] < 120 else 'hard' if ln['level'] < 250 else 'extreme')
        lines_csv.append(f"{keys[0]};{tier};{1 if ln['premium'] else 0};{ln['line'][0]};{ln['where'][0]}")
    for m in mr:
        ln = m['line']
        for idx, rows in ((1, en), (2, es)):
            rows.append(f"quest;{m['key']};name;{m['name'][idx]}")
            rows.append(f"quest;{m['key']};desc;{m['desc'][idx]}")
    for ln in LINES:
        k1 = f"{ln['key']}_{ln['missions'][0][0]}"
        for idx, rows in ((1, en), (2, es)):
            rows.append(f"quest;{k1};line;{ln['line'][idx]}")
            rows.append(f"quest;{k1};where;{ln['where'][idx]}")
    for k, (name, _c, greet) in NPCS.items():
        for idx, rows in ((1, en), (2, es)):
            rows.append(f'npc;{k};name;{name}')
            rows.append(f'npc;{k};greeting;{greet[idx]}')
    for k, v in ISLANDS.items():
        for idx, rows in ((1, en), (2, es)):
            rows.append(f"npc;{v['captain']};name;{v['cname']}")
            rows.append(f"npc;{v['captain']};shiphint;{v['hint'][idx]}")
            gr = ('I sail to the mainland. Premium accounts only.', 'Navego al continente. Solo cuentas premium.') if v['premium'] else \
                 ('I sail to the mainland. Free, if you know why.', 'Navego al continente. Gratis, si sabes por qué.')
            rows.append(f"npc;{v['captain']};greeting;{gr[idx - 1]}")
            rows.append(f"place;{v['name'][0]};name;{v['name'][idx]}")
    for pl, (e, s) in VISIT_PLACES.items():
        en.append(f'place;{pl};name;{e}')
        es.append(f'place;{pl};name;{s}')
    for k, v in MONSTERS.items():
        en.append(f'monster;{k};name;{v[1]}')
        es.append(f'monster;{k};name;{v[2]}')
    for k, v in ITEMS.items():
        en.append(f'item;{k};name;{v[1]}')
        es.append(f'item;{k};name;{v[2]}')
    dialogues.append("ferry_oswin;broszka,wyspa,statek,plyn,sroka,krol;Moją srebrną broszkę ukradł Król Sroczy i schował w norze na polach na wschód od Varden. Przynieś ją, a Boatwoman Brenna zabierze cię na Wyspę Szeptów.")
    dialogues.append("ice_halvar;pozwolenie,wyspa,rejs,zamiec;Pozwolenie dostajesz po pokonaniu Pana Kopca z Wyspy Szeptów, ale najpierw musisz ukończyć quest Oswina i polowanie na yeti.")
    dialogues.append("desert_idris;zgoda,wyspa,rejs,wydmy;Szlak sprzedaję tym, którzy ukończyli quest Halvara i przynieśli jad skorpionów. Jarla Szronu pokonasz tylko w drużynie.")
    dialogues.append("tropic_ilvane;przepustka,wyspa,rejs,mgla;Przepustkę dostanie ten, kto ukończył quest Idrisa i pomógł Selvaran z krokodylami. Regenta z pustyni pokonaj sam albo z drużyną.")
    dialogues.append("oak_corwin;list,wyspa,rejs,debowa;List dostanie ten, kto ukończył quest Ilvane i Halvara. Cesarzowa Pnączy czeka na Mgle.")
    splice(res('data', 'quests.csv'), qrows)
    splice(res('data', 'quest_chains.csv'), chains)
    splice(res('data', 'quest_requires.csv'), requires)
    splice(res('data', 'quest_lines.csv'), lines_csv)
    splice(res('data', 'ship_access.csv'), ship_access_rows())
    splice(res('data', 'dialogues.csv'), dialogues)
    splice(res('i18n', 'data_en.csv'), en)
    splice(res('i18n', 'data_es.csv'), es)
    print('islands5 data:', len(MONSTERS), 'monsters,', len(LINES), 'quest lines,', len(mr), 'missions')


if __name__ == '__main__':
    write_data()
