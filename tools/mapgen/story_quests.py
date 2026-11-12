"""Zadania fabularne w stylu Tibii (Darek 2026-11-12: "dodaj questy tak jak w tibi z nagrodami: slabe, srednie za darmo,
srednio trudne, trudne i ekstremalne premium"): 13 wypraw z wlasnymi nazwami i historiami.

  darmowe (kontynent glowny): 4 latwe (poz. 8-30), 3 srednie (30-60);
  premium (statkiem: Valmir, Selvaran, Nerys): 3 srednio trudne (60-120), 2 trudne (120-250), 1 ekstremalna (250+).

Kazda wyprawa to jedno miejsce: dziedziniec ze strefa ochronna i zleceniodawca (NPC z angielskim imieniem), potem wieza
(pietra w gore) albo wejscie do lochu (pietra w dol), drzwi poziomu na wejsciu (poza latwymi), potwory, na szczycie
albo na dnie sala skarbow z piecioma-osmioma skrzyniami nagrod do wyboru (jedna na postac), kazda z tabliczka.
Misje (3 na wyprawe, lancuch ``quest_chains.csv``): 1 zajrzyj do srodka (VISIT: region wnetrza), 2 pokonaj N potworow,
3 pokonaj N potworow-strazy. Skrzynie otwiera dopiero oddanie misji 3 (wymog ``quest:<klucz>`` w ``star_quest.csv``).
Dane gry (quests.csv, quest_chains.csv, quest_lines.csv, dialogues.csv, tlumaczenia) pisze ``python3 story_quests.py data``.
"""
import math
import os
import random
import sys
from collections import deque

import numpy as np

import features as F
import isles as I
import structures as St

NONE = '-'
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'shared', 'src', 'main', 'resources')
SEED = 20261212


def level_cost(level):
    """Doswiadczenie z poziomu na nastepny (krzywa Balance.exp)."""
    def tot(L):
        return 50 * (L ** 3 - 6 * L ** 2 + 17 * L - 12) // 3
    return tot(level + 1) - tot(level)


# --------------------------------------------------------------------------------------------------------- dane wypraw
# tier: easy | medium | mediumhard | hard | extreme; site: tower | delve; town: miasto, w ktorego okolicy leza (reach);
# m2/m3: (potwor, ilosc, nazwa pl/en/es, opis pl/en/es); rewards: skrzynie do wyboru (klucze przedmiotow)
QUESTS = [
    dict(key='mill', tier='easy', level=8, door=0, premium=False, site='tower', floors=2, town='Varden', near=(460, 545),
         place=('Opuszczony Młyn', 'Abandoned Mill', 'Molino abandonado'),
         line=('Młyn Cichych Pająków', 'Mill of Quiet Spiders', 'Molino de las arañas silenciosas'),
         npc=('mill_bram', 'Miller Bram', '#c8a060'),
         greet=('Mój młyn zarósł pajęczynami. Pomożesz mi odzyskać spichlerz?',
                'Cobwebs have swallowed my mill. Will you help me win back the granary?',
                'Las telarañas se tragaron mi molino. ¿Me ayudas a recuperar el granero?'),
         story='Młyn stoi pusty od jesieni: pająki zasnuły żarna, a wilki zadomowiły się w spichlerzu na piętrze.',
         m2=('forest_spider', 8, ('Pajęczy rój', 'Spider Swarm', 'Enjambre de arañas'),
             ('Pająki zasiedliły młyn. Pokonaj 8, żeby dało się znów zmielić ziarno.',
              'Spiders have taken over the mill. Defeat 8 so the grain can be ground again.',
              'Las arañas se apoderaron del molino. Derrota a 8 para poder moler grano de nuevo.')),
         m3=('wolf', 6, ('Wilki w spichlerzu', 'Wolves in the Granary', 'Lobos en el granero'),
             ('Wilki leżą w spichlerzu na górze. Pokonaj 6, a skrzynie na szczycie młyna są twoje.',
              'Wolves lie in the granary upstairs. Defeat 6 and the chests at the top of the mill are yours.',
              'Los lobos duermen en el granero de arriba. Derrota a 6 y los cofres de lo alto del molino son tuyos.')),
         rewards=['hand_axe', 'hunting_bow', 'wand_of_embers', 'thornroot_rod', 'cloth_wraps', 'studded_armor']),
    dict(key='crypt', tier='easy', level=12, door=0, premium=False, site='delve', floors=2, town='Varden', near=(440, 440),
         place=('Krypta Cichego Dzwonu', 'Crypt of the Silent Bell', 'Cripta de la campana muda'),
         line=('Cichy Dzwon', 'The Silent Bell', 'La campana muda'),
         npc=('crypt_aldric', 'Sexton Aldric', '#8a8a9a'),
         greet=('Dzwon w krypcie zamilkł, a w nocy słychać kroki. Ktoś musi tam zejść.',
                'The bell in the crypt has gone silent and footsteps echo at night. Someone must go down.',
                'La campana de la cripta enmudeció y de noche se oyen pasos. Alguien debe bajar.'),
         story='Stary dzwonnik pochowany pod kaplicą nie zaznał spokoju. Jego szkielety krążą po krypcie, a stonogi gnieżdżą się w ścianach.',
         m2=('skeleton', 8, ('Niespokojne kości', 'Restless Bones', 'Huesos inquietos'),
             ('Szkielety wędrują po krypcie. Pokonaj 8.', 'Skeletons roam the crypt. Defeat 8.',
              'Los esqueletos vagan por la cripta. Derrota a 8.')),
         m3=('cave_centipede', 6, ('Gniazdo stonóg', 'Centipede Nest', 'Nido de ciempiés'),
             ('Stonogi opanowały dolne komnaty. Pokonaj 6, a odblokujesz skarbiec dzwonnika.',
              'Centipedes hold the lower chambers. Defeat 6 and the bellringer\'s hoard opens.',
              'Los ciempiés dominan las cámaras bajas. Derrota a 6 y se abre el tesoro del campanero.')),
         rewards=['forest_hood', 'iron_sword', 'bone_shield', 'wolf_spellbook', 'rooster_bow', 'miner_helmet']),
    dict(key='poacher', tier='easy', level=16, door=0, premium=False, site='delve', floors=2, town='Frostholm', near=(792, 250),
         place=('Jama Kłusownika', "The Poacher's Den", 'La guarida del cazador furtivo'),
         line=('Jama Kłusownika', "The Poacher's Den", 'La guarida del cazador furtivo'),
         npc=('poacher_hild', 'Trapper Hild', '#7a9ab0'),
         greet=('Ktoś zastawia sidła na moich wilkach. Znajdź jaskinię, z której wychodzą kłusownicy.',
                'Someone is snaring my wolves. Find the cave the poachers come out of.',
                'Alguien tiende lazos a mis lobos. Encuentra la cueva de la que salen los furtivos.'),
         story='Banda kłusowników zakopała się w lodowej jamie i obdziera śnieżne wilki ze skór, a rannych zwierząt nie ma kto chronić.',
         m2=('snow_wolf', 10, ('Wilki w sidłach', 'Wolves in the Snares', 'Lobos en los lazos'),
             ('Śnieżne wilki miotają się w sidłach. Pokonaj 10, zanim ruszą na karawany.',
              'Snow wolves thrash in the snares. Defeat 10 before they turn on the caravans.',
              'Los lobos de nieve se debaten en los lazos. Derrota a 10 antes de que ataquen a las caravanas.')),
         m3=('bandit', 6, ('Banda kłusowników', 'The Poacher Gang', 'La banda de furtivos'),
             ('Kłusownicy bronią skarbca w głębi jamy. Pokonaj 6 i zabierz łup.',
              'The poachers guard a hoard deep in the den. Defeat 6 and take the loot.',
              'Los furtivos guardan un botín al fondo de la guarida. Derrota a 6 y llévate el botín.')),
         rewards=['rooster_armor', 'rooster_legs', 'iron_knuckles', 'wolf_spellbook', 'iron_sword', 'hunting_bow']),
    dict(key='oasis', tier='easy', level=22, door=0, premium=False, site='delve', floors=2, town='Qasirah', near=(196, 524),
         place=('Studnia Zakopanej Oazy', 'Well of the Buried Oasis', 'Pozo del oasis enterrado'),
         line=('Zakopana Oaza', 'The Buried Oasis', 'El oasis enterrado'),
         npc=('oasis_zahir', 'Warden Zahir', '#d8b868'),
         greet=('Piasek pogrzebał oazę, a w jej studni zagnieździły się bestie. Bez wody karawany giną.',
                'Sand has buried the oasis and beasts nest in its well. Without water the caravans die.',
                'La arena enterró el oasis y unas bestias anidan en su pozo. Sin agua las caravanas mueren.'),
         story='Strażnik studni uciekł z oazy przed hienami i skorpionami. W zasypanych komnatach pod piaskiem ukryto zapasy karawan.',
         m2=('hyena', 10, ('Hieny przy wodzie', 'Hyenas at the Water', 'Hienas junto al agua'),
             ('Hieny pilnują wody. Pokonaj 10.', 'Hyenas guard the water. Defeat 10.', 'Las hienas vigilan el agua. Derrota a 10.')),
         m3=('scorpion', 12, ('Skorpiony w studni', 'Scorpions in the Well', 'Escorpiones en el pozo'),
             ('Skorpiony zalęgły się w studni. Pokonaj 12, a dotrzesz do skrzyń karawan.',
              'Scorpions have bred in the well. Defeat 12 and reach the caravan chests.',
              'Los escorpiones criaron en el pozo. Derrota a 12 y llega a los cofres de las caravanas.')),
         rewards=['steel_spear', 'crossbow', 'wand_of_sparks', 'rod_of_frost', 'crystal_shortsword', 'gem_shield', 'traveler_boots']),
    dict(key='barrow', tier='medium', level=35, door=35, premium=False, site='delve', floors=2, town='Varden', near=(548, 418),
         place=('Kurhan Dworu Świec', 'Barrow of the Candle Court', 'Túmulo de la Corte de las Velas'),
         line=('Dwór Świec', 'The Candle Court', 'La Corte de las Velas'),
         npc=('barrow_edda', 'Lamplighter Edda', '#e0c070'),
         greet=('Pod kurhanem płoną świece, których nikt nie zapalał. Dwór zmarłych chce, by o nim pamiętano.',
                'Candles burn under the barrow that no one lit. The dead court wants to be remembered.',
                'Bajo el túmulo arden velas que nadie encendió. La corte de los muertos quiere ser recordada.'),
         story='Dwór zmarłych kupców płonie świecami w kurhanie. Akolici trzymają straż, a ich latarnie wabią w mrok wędrowców.',
         m2=('candle_acolyte', 10, ('Akolici świec', 'Candle Acolytes', 'Acólitos de las velas'),
             ('Akolici pilnują wejścia do dworu. Pokonaj 10.', 'Acolytes guard the court entrance. Defeat 10.',
              'Los acólitos guardan la entrada de la corte. Derrota a 10.')),
         m3=('barrow_lantern', 6, ('Latarnie w mroku', 'Lanterns in the Dark', 'Linternas en la oscuridad'),
             ('Latarnie krążą po komnacie tronowej. Pokonaj 6, a skarb dworu będzie twój.',
              'Lanterns drift through the throne chamber. Defeat 6 and the court\'s treasure is yours.',
              'Las linternas flotan por la sala del trono. Derrota a 6 y el tesoro de la corte es tuyo.')),
         rewards=['candy_armor', 'candy_sword', 'candy_bow', 'candy_wand', 'candy_shield', 'emerald_amulet', 'fur_armor']),
    dict(key='watch', tier='medium', level=50, door=50, premium=False, site='tower', floors=3, town='Frostholm', near=(790, 150),
         place=('Wieża Strażnicza Hrimvard', 'Hrimvard Watchtower', 'Atalaya de Hrimvard'),
         line=('Straż Hrimvard', 'The Hrimvard Watch', 'La Guardia de Hrimvard'),
         npc=('watch_rorik', 'Sentinel Rorik', '#9ab8d0'),
         greet=('Wieża Hrimvard milczy od zimy. Ostatni strażnik zamarzł na szczycie z latarnią w ręku.',
                'The Hrimvard tower has been silent since winter. The last sentinel froze at the top holding his lantern.',
                'La torre de Hrimvard calla desde el invierno. El último centinela se congeló arriba con su linterna.'),
         story='Strażnicy z Hrimvard osłaniali przełęcz do Frostholm. Teraz po wieży krążą zamarznięte dziki i yeti, a skarbiec garnizonu leży na szczycie.',
         m2=('frost_boar', 8, ('Zamrożone dziki', 'Frozen Boars', 'Jabalíes helados'),
             ('Zamrożone dziki ryją pod wieżą. Pokonaj 8.', 'Frozen boars root beneath the tower. Defeat 8.',
              'Jabalíes helados hozan bajo la torre. Derrota a 8.')),
         m3=('yeti', 6, ('Yeti na szczycie', 'Yetis at the Summit', 'Yetis en la cumbre'),
             ('Yeti zajęły górne piętra. Pokonaj 6, a otworzysz skarbiec garnizonu.',
              'Yetis hold the upper floors. Defeat 6 and the garrison vault opens.',
              'Los yetis ocupan los pisos altos. Derrota a 6 y se abre la cámara de la guarnición.')),
         rewards=['sandstone_shield', 'crystal_sword', 'elven_bow', 'wand_of_storms', 'rod_of_vines', 'fur_armor', 'candy_boots']),
    dict(key='zig', tier='medium', level=55, door=55, premium=False, site='delve', floors=2, town='Qasirah', near=(190, 430),
         place=('Zigurat Spalonego Słońca', 'Ziggurat of the Scorched Sun', 'Zigurat del Sol Abrasado'),
         line=('Spalone Słońce', 'The Scorched Sun', 'El Sol Abrasado'),
         npc=('zig_nadira', 'Seeker Nadira', '#e0a050'),
         greet=('Zigurat pod wydmą wypuszcza żar nawet nocą. Szukam kogoś, kto zajrzy do jego komnat.',
                'The ziggurat under the dune gives off heat even at night. I seek someone to look into its chambers.',
                'El zigurat bajo la duna despide calor incluso de noche. Busco a alguien que mire en sus cámaras.'),
         story='Kapłani spalonego słońca zamknęli się w ziguracie i oddali go żarłokom piasku. W głębi pilnują go pręgowani łowcy.',
         m2=('sand_devourer', 10, ('Piaskowe żarłoki', 'Sand Devourers', 'Devoradores de arena'),
             ('Żarłoki podkopują zigurat. Pokonaj 10.', 'Devourers undermine the ziggurat. Defeat 10.',
              'Los devoradores socavan el zigurat. Derrota a 10.')),
         m3=('striped_prowler', 6, ('Pręgowani łowcy', 'Striped Prowlers', 'Merodeadores rayados'),
             ('Pręgowani łowcy bronią skarbca kapłanów. Pokonaj 6.', 'Striped prowlers guard the priests\' vault. Defeat 6.',
              'Los merodeadores rayados guardan la cámara de los sacerdotes. Derrota a 6.')),
         rewards=['croc_legs', 'desert_scimitar', 'serpent_spear', 'desert_knuckles', 'wand_of_storms', 'rod_of_vines', 'sandstone_shield']),
    dict(key='bog', tier='mediumhard', level=70, door=70, premium=True, site='delve', floors=2, town='Valmir', near=(436, 792),
         place=('Kaplica Bagiennej Wody', 'Bogwater Chapel', 'Capilla de Aguas Cenagosas'),
         line=('Bagienna Kaplica', 'The Bogwater Chapel', 'La Capilla Cenagosa'),
         npc=('bog_alaric', 'Reverend Alaric', '#7a9a6a'),
         greet=('Kaplica utonęła w bagnie, ale dzwony wciąż biją pod wodą. Potrzebuję kogoś z silnym ramieniem.',
                'The chapel sank into the bog, yet its bells still ring under water. I need someone with a strong arm.',
                'La capilla se hundió en el pantano, pero sus campanas suenan bajo el agua. Necesito un brazo fuerte.'),
         story='Wierni utonęli w bagnie razem z kaplicą. Ich upiory wędrują po nawie, a jaszczury wypełniły podziemia.',
         m2=('bog_wraith', 12, ('Upiory z bagien', 'Bog Wraiths', 'Espectros del pantano'),
             ('Upiory bagienne krążą po kaplicy. Pokonaj 12.', 'Bog wraiths drift through the chapel. Defeat 12.',
              'Los espectros del pantano vagan por la capilla. Derrota a 12.')),
         m3=('marsh_lizard', 8, ('Jaszczury w nawie', 'Lizards in the Nave', 'Lagartos en la nave'),
             ('Jaszczury zajęły nawę główną. Pokonaj 8, a skarbiec kaplicy się otworzy.',
              'Lizards have taken the main nave. Defeat 8 and the chapel vault opens.',
              'Los lagartos ocupan la nave principal. Derrota a 8 y se abre la cámara de la capilla.')),
         rewards=['plague_armor', 'plague_sword', 'plague_bow', 'plague_wand', 'plague_rod', 'plague_shield', 'scarab_helmet']),
    dict(key='coil', tier='mediumhard', level=90, door=90, premium=True, site='delve', floors=2, town='Selvaran', near=(722, 722),
         place=('Sanktuarium Złotej Spirali', 'Shrine of the Gilded Coil', 'Santuario de la Espiral Dorada'),
         line=('Złota Spirala', 'The Gilded Coil', 'La Espiral Dorada'),
         npc=('coil_itzal', 'Priest Itzal', '#c8a030'),
         greet=('Sanktuarium Złotej Spirali przestało odpowiadać na modlitwy. Węże wyległy z posągów.',
                'The Shrine of the Gilded Coil no longer answers prayers. Snakes crawled out of the statues.',
                'El Santuario de la Espiral Dorada ya no responde a las plegarias. Las serpientes salieron de las estatuas.'),
         story='Dżunglowi kapłani czcili złotego węża, aż złoto obudziło coś głodnego. Teraz węże i dawni łowcy skarbów pilnują sanktuarium.',
         m2=('jungle_serpent', 12, ('Węże w korytarzach', 'Serpents in the Halls', 'Serpientes en los pasillos'),
             ('Węże wypełniły korytarze sanktuarium. Pokonaj 12.', 'Serpents fill the shrine halls. Defeat 12.',
              'Las serpientes llenan los pasillos del santuario. Derrota a 12.')),
         m3=('jungle_hunter', 8, ('Łowcy skarbów', 'Treasure Hunters', 'Cazadores de tesoros'),
             ('Dawni łowcy skarbów strzegą ołtarza. Pokonaj 8, a skarb spirali jest twój.',
              'Former treasure hunters guard the altar. Defeat 8 and the coil\'s treasure is yours.',
              'Antiguos cazadores de tesoros guardan el altar. Derrota a 8 y el tesoro de la espiral es tuyo.')),
         rewards=['knight_sword', 'war_hammer', 'composite_bow', 'blizzard_wand', 'blizzard_rod', 'blizzard_shield', 'blizzard_boots']),
    dict(key='lantern', tier='mediumhard', level=110, door=110, premium=True, site='tower', floors=3, town='Nerys', near=(903, 772),
         place=('Latarnia Zielonych Mgieł', 'Lighthouse of the Green Mists', 'Faro de las Brumas Verdes'),
         line=('Zielone Mgły', 'The Green Mists', 'Las Brumas Verdes'),
         npc=('lantern_orsa', 'Lightkeeper Orsa', '#80c0a0'),
         greet=('Światło latarni zgasło, a z mgły wychodzą istoty, które nie powinny istnieć. Wejdź na szczyt.',
                'The lighthouse beam has gone out and things that should not exist walk out of the mist. Climb to the top.',
                'El haz del faro se apagó y de la niebla salen seres que no deberían existir. Sube a la cima.'),
         story='Latarnik wzywał statki do portu, aż mgła przyniosła zaklinaczy jaszczurów i kamiennych wartowników. Latarnia czeka na kogoś, kto ją znów zapali.',
         m2=('lizard_hexer', 10, ('Zaklinacze z mgły', 'Hexers of the Mist', 'Hechiceros de la niebla'),
             ('Zaklinacze jaszczurów zajęli dolne piętra. Pokonaj 10.', 'Lizard hexers hold the lower floors. Defeat 10.',
              'Los hechiceros lagarto ocupan los pisos bajos. Derrota a 10.')),
         m3=('stone_sentinel', 5, ('Kamienni wartownicy', 'Stone Sentinels', 'Centinelas de piedra'),
             ('Kamienni wartownicy strzegą lampy. Pokonaj 5, a zapalisz latarnię i otworzysz skarbiec.',
              'Stone sentinels guard the lamp. Defeat 5 to light the beacon and open the vault.',
              'Los centinelas de piedra guardan la lámpara. Derrota a 5 para encender el faro y abrir la cámara.')),
         rewards=['blizzard_armor', 'blizzard_helmet', 'blizzard_legs', 'blizzard_sword', 'blizzard_bow', 'blizzard_spellbook', 'blizzard_shield', 'blizzard_amulet']),
    dict(key='stormgate', tier='hard', level=150, door=150, premium=True, site='delve', floors=3, town='Selvaran', near=(640, 648),
         place=('Cytadela Zburzonej Bramy Burz', 'Citadel of the Shattered Stormgate', 'Ciudadela de la Puerta de Tormentas Rota'),
         line=('Brama Burz', 'The Stormgate', 'La Puerta de Tormentas'),
         npc=('storm_teodrin', 'Marshal Teodrin', '#a0a8c8'),
         greet=('Cytadela Burz padła w jedną noc. Jej brama wciąż pęka od piorunów, a garnizon nie odpoczywa.',
                'The Stormgate citadel fell in a single night. Its gate still cracks with lightning and the garrison never rests.',
                'La ciudadela de la Tormenta cayó en una noche. Su puerta aún cruje con rayos y la guarnición no descansa.'),
         story='Marszałek stracił armię, gdy gargulce burzy zrzuciły mury. Straż bramy z kamienia pilnuje skarbca generałów.',
         m2=('sand_gargoyle', 12, ('Gargulce burzy', 'Storm Gargoyles', 'Gárgolas de la tormenta'),
             ('Gargulce krążą po murach cytadeli. Pokonaj 12.', 'Gargoyles circle the citadel walls. Defeat 12.',
              'Las gárgolas rondan las murallas. Derrota a 12.')),
         m3=('temple_guardian', 8, ('Straż bramy', 'The Gate Guard', 'La guardia de la puerta'),
             ('Straż bramy broni skarbca generałów. Pokonaj 8.', 'The gate guard defends the generals\' vault. Defeat 8.',
              'La guardia de la puerta defiende la cámara de los generales. Derrota a 8.')),
         rewards=['dragon_armor', 'dragon_helmet', 'dragon_legs', 'dragon_sword', 'dragon_bow', 'dragon_wand', 'dragon_rod', 'dragon_shield']),
    dict(key='wyrm', tier='hard', level=210, door=210, premium=True, site='delve', floors=3, town='Nerys', near=(842, 690),
         place=('Smocze Moczary', 'Wyrmfen Hollow', 'Hondonada del Pantano del Wyrm'),
         line=('Smocze Moczary', 'Wyrmfen Hollow', 'Pantano del Wyrm'),
         npc=('wyrm_kaldor', 'Drakehunter Kaldor', '#c07040'),
         greet=('W moczarach ukryto gniazdo, o którym milczą łowcy. Kto wróci, zbierze nagrodę lepszą od zbroi smoka.',
                'A nest lies hidden in the fens that hunters will not speak of. Whoever returns earns a prize beyond dragon armour.',
                'En el pantano hay un nido del que los cazadores no hablan. Quien vuelva gana un premio mejor que una armadura de dragón.'),
         story='Łowca smoków odkrył, że nagi i smoki skalne dzielą jedno legowisko. Nikt, kto tam wszedł, nie wyszedł bez blizn.',
         m2=('bog_naga', 10, ('Nagi z moczarów', 'Nagas of the Fen', 'Nagas del pantano'),
             ('Nagi strzegą przejść do gniazda. Pokonaj 10.', 'Nagas guard the passages to the nest. Defeat 10.',
              'Las nagas guardan los pasos al nido. Derrota a 10.')),
         m3=('crag_dragon', 6, ('Smoki skalne', 'Crag Dragons', 'Dragones de risco'),
             ('Smoki skalne strzegą skarbu w gnieździe. Pokonaj 6.', 'Crag dragons guard the treasure in the nest. Defeat 6.',
              'Los dragones de risco guardan el tesoro del nido. Derrota a 6.')),
         rewards=['sun_armor', 'sun_helmet', 'sun_legs', 'sun_sword', 'sun_bow', 'sun_wand', 'sun_rod', 'sun_shield']),
    dict(key='crown', tier='extreme', level=270, door=270, gate='party:3', premium=True, site='delve', floors=3, town='Valmir', near=(552, 800),
         place=('Skarbiec Pustej Korony', 'Vault of the Hollow Crown', 'Cámara de la Corona Hueca'),
         line=('Pusta Korona', 'The Hollow Crown', 'La Corona Hueca'),
         npc=('crown_isolde', 'Crownwarden Isolde', '#b8a0d8'),
         greet=('Korona, która nie ma głowy, wciąż woła o władzę. Jej strażnicy nie pozwolą nikomu wyjść z pustymi rękami... ani żywym.',
                'The crown that has no head still calls for power. Its guards let no one leave empty-handed... or alive.',
                'La corona sin cabeza aún reclama poder. Sus guardianes no dejan salir a nadie con las manos vacías... ni vivo.'),
         story='Ostatni król oddał duszę koronie, a jego gwardia stała się upiorami. Magowie popiołu pilnują sali, w której złożono skarbiec królestwa.',
         m2=('wraith_knight', 20, ('Upiorna gwardia', 'The Wraith Guard', 'La guardia espectral'),
             ('Upiorni rycerze stoją na każdym piętrze. Pokonaj 20.', 'Wraith knights stand on every floor. Defeat 20.',
              'Caballeros espectrales vigilan cada piso. Derrota a 20.')),
         m3=('ash_magus', 10, ('Magowie popiołu', 'Ash Magi', 'Magos de ceniza'),
             ('Magowie popiołu strzegą korony. Pokonaj 10, a skarbiec królestwa się otworzy.',
              'Ash magi guard the crown. Defeat 10 and the kingdom\'s vault opens.',
              'Los magos de ceniza guardan la corona. Derrota a 10 y se abre la cámara del reino.')),
         rewards=['phoenix_armor', 'phoenix_helmet', 'phoenix_legs', 'phoenix_sword', 'phoenix_bow', 'phoenix_wand', 'phoenix_spellbook', 'phoenix_shield']),
]
BY_KEY = {q['key']: q for q in QUESTS}

TIER_EXP = {'easy': 1.0, 'medium': 1.2, 'mediumhard': 1.2, 'hard': 1.2, 'extreme': 2.0}
SPLIT = (0.15, 0.30, 0.55)        # udzial misji 1-3 w nagrodzie wyprawy
GOLD_PER_EXP = 0.05
INNER = {'tower': ('wnętrze', 'interior', 'interior'), 'delve': ('głębiny', 'depths', 'profundidades')}


def inner_name(q, lang=0):
    return f"{q['place'][lang]} ({INNER[q['site']][lang]})"


def rewards_of(q):
    """(exp, gold) misji 1-3: wyprawa daje lacznie TIER_EXP poziomow doswiadczenia i 5% tego w zlocie."""
    total = TIER_EXP[q['tier']] * level_cost(q['level'])
    out = []
    for f in SPLIT:
        exp = int(round(total * f, -2 if total * f < 1e6 else -3))
        gold = max(100, int(round(exp * GOLD_PER_EXP, -2)))
        out.append((exp, gold))
    return out


# ------------------------------------------------------------------------------------------------------- budowa planow
YARD = (0, 3, 9, 9)               # dziedziniec (x0, y0, x1, y1) w planie; strefa ochronna
NPC_AT = (4, 6)
DOOR = (10, 6)                    # wejscie w zachodniej scianie budynku
FLOOR_STONE, WALL_STONE = 'stone_floor', 'stone_wall'


def _spread(cells, k, rng, avoid, gap=6):
    cells = [c for c in cells if all(abs(c[0] - a) + abs(c[1] - b) >= 3 for (a, b) in avoid)]
    rng.shuffle(cells)
    out = []
    for c in cells:
        if len(out) >= k:
            break
        if all(abs(c[0] - o[0]) + abs(c[1] - o[1]) >= gap for o in out):
            out.append(c)
    return out


def _free(p, z):
    return [xy for xy, c in p.f[z].items() if St.walk(c) and c[2] == NONE and c[3] == NONE]


def _yard(p, q):
    x0, y0, x1, y1 = YARD
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p.put(x, y, 0, ('cobblestone', NONE, NONE, NONE))
    p.pz.append((x0, y0, x1, y1, 0))
    p.npcs.append([q['npc'][0], q['npc'][1], 'GUIDE', q['place'][0], NPC_AT[0], NPC_AT[1], 0, '-', q['npc'][2], q['greet'][0]])


def _entrance(p, q, z=0):
    """Wejscie w zachodniej scianie budynku: drzwi poziomu (wedlug door) albo zwykla przerwa."""
    if q['door']:
        p.level_door(z, DOOR, q['door'], floor=FLOOR_STONE)
    else:
        p.put(DOOR[0], DOOR[1], z, (FLOOR_STONE, NONE, NONE, NONE))


def _reward_row(p, q, z, x0, x1, y):
    """Skrzynie nagrod w rzedzie y od x0 co 2 pola (tabliczki dostawia ChestSigns pod nimi)."""
    n = len(q['rewards'])
    xs = [x0 + 2 * i for i in range(n)]
    assert xs[-1] <= x1, f"{q['key']}: reward row too long"
    for x, item in zip(xs, q['rewards']):
        p.chests.append((x, y, z, 100, item, 1))


def tower_plan(q, rng):
    p = St.Plan(q['place'][0])
    _yard(p, q)
    bx, by, bw, bh = 10, 0, 19, 13
    floors = q['floors']
    stair = [(22, 3), (12, 9), (22, 9)]
    for z in range(floors):
        w, h = (bw, bh) if z < floors - 1 else (bw, bh)
        p.box(z, bx, by, w, h, FLOOR_STONE if z == 0 else 'wood_floor', WALL_STONE)
    _entrance(p, q)
    for z in range(floors - 1):
        up = stair[z % len(stair)]
        down = (up[0] + 0, up[1] + 0)
        p.stairs(z, up, down)
    top = floors - 1
    # skarbiec na szczycie: skrzynie przy polnocnej scianie
    _reward_row(p, q, top, 12, 26, 2)
    # pochodnie przy scianach
    for z in range(floors):
        for (x, y) in ((13, 1), (25, 1), (13, 11), (25, 11)):
            c = p.get(x, y, z)
            if c is not None and c[2] == NONE and (z, x, y) not in [(0, *DOOR)]:
                p.put(x, y, z, (c[0], c[1], 'wall_torch', NONE))
    p.entries.append(NPC_AT)
    # potwory: misja 2 na dolnych pietrach, misja 3 (straz) na szczycie
    m2, m3 = q['m2'][0], q['m3'][0]
    stairs_xy = [(s[0], s[1]) for s in stair[:floors]]
    for z in range(floors):
        mon = m3 if z == top else m2
        cnt = 2 if z == top else 3
        pts = _spread(_free(p, z), 6, rng, stairs_xy + [(11, 6)], gap=4)
        # na szczycie nie stawiamy pod rzedem skrzyn
        pts = [c for c in pts if not (z == top and c[1] <= 4)] or pts
        for c in pts:
            p.spawn(mon, c[0], c[1], z, radius=3, count=cnt, respawn=150 + 30 * z)
    if floors == 3:
        for c in _spread(_free(p, 1), 3, rng, stairs_xy + [(11, 6)], gap=5):
            p.spawn(m2, c[0], c[1], 1, radius=3, count=3, respawn=170)
    p.regions.append((q['place'][0], 0, 0, bw + 9, bh, 0, 0, max(1, q['level'] - 5), q['level'] + 25))
    p.regions.append((inner_name(q), bx, by, bx + bw - 1, by + bh - 1, 0, floors - 1, max(1, q['level'] - 5), q['level'] + 25))
    return p, dict(top=top)


def delve_plan(q, rng, seed):
    style = q.get('style', 'crypt' if q['tier'] in ('easy', 'medium') else 'ruin')
    style = {'crypt': 'crypt', 'ruin': 'ruin'}.get(style, style)
    floor_s, wall_s = F.CAVE_STYLES[style][0], F.CAVE_STYLES[style][1]
    nf = q['floors']
    for attempt in range(60):
        r = random.Random(seed + 131 * attempt)
        p = St.Plan(q['place'][0])
        try:
            _yard(p, q)
            p.box(0, 10, 2, 9, 9, FLOOR_STONE, WALL_STONE)
            _entrance(p, q)
            down0 = (16, 6)
            p.stairs_down(0, down0, down0)
            R = 52
            arrive = down0
            infos = []
            dn = None
            for i in range(nf):
                z = -(i + 1)
                last = i == nf - 1
                if not last:
                    f = I.rooms_floor(p, z, (0, 0, R, 36), arrive, 7, style, r, down=True, down_ok=lambda t: 8 < t[0] < 34 and 6 < t[1] < 30, gap=8)
                    dn = f['down']
                    p.stairs(z - 1, dn, dn)
                    arrive = dn
                else:
                    f = I.rooms_floor(p, z, (0, 0, 40, 36), arrive, 6, style, r, extra=[(33, 18)], down=False, gap=8)
                    p.box(z, 40, 10, 13, 15, 'marble_floor', 'stone_wall', gaps=[(40, 18)])
                    p.corridor(z, [(x, 18) for x in range(33, 41)], floor_s, wall_s)
                infos.append(f)
            top = -nf
            # skarbiec: skrzynie wzdluz polnocnej sciany sali, straz w srodku
            n = len(q['rewards'])
            xs = [42 + 2 * i for i in range(n)]
            assert xs[-1] <= 61, 'vault too narrow'
            p.box(top, 40, 10, 24, 15, 'marble_floor', 'stone_wall', gaps=[(40, 18)])
            for x, item in zip(xs, q['rewards']):
                p.chests.append((x, 11, top, 100, item, 1))
            for (x, y) in ((42, 23), (61, 23)):
                p.obj(x, y, top, 'wall_torch')
            p.finish()
        except AssertionError:
            continue
        m2, m3 = q['m2'][0], q['m3'][0]
        avoid = [arrive]
        for i, f in enumerate(infos):
            z = -(i + 1)
            last = i == nf - 1
            pts = f['chain'][1:]
            cells = []
            for pt in pts:
                near = [(abs(c[0] - pt[0]) + abs(c[1] - pt[1]), c) for c in _free(p, z) if abs(c[0] - pt[0]) <= 3 and abs(c[1] - pt[1]) <= 3]
                if near:
                    cells.append(min(near)[1])
            cells = [c for c in cells if all(abs(c[0] - a) + abs(c[1] - b) >= 6 for (a, b) in avoid + ([dn] if dn else []))]
            for j, c in enumerate(cells):
                mon = m3 if (last and j % 2 == 0) else m2
                if not last and i == 0:
                    mon = m2
                p.spawn(mon, c[0], c[1], z, radius=3, count=3 + (j % 2), respawn=140 + 20 * i)
        # straz skarbca
        for (x, y) in ((46, 17), (55, 17), (50, 22)):
            p.spawn(m3, x, y, top, radius=2, count=3, respawn=240)
        for i in range(nf):
            I.floor_region(p, inner_name(q), -(i + 1), (max(1, q['level'] - 5), q['level'] + 25))
        p.regions.append((q['place'][0], 0, 0, 19, 12, 0, 0, max(1, q['level'] - 5), q['level'] + 25))
        p.entries.append(NPC_AT)
        p.snap()
        try:
            p.check()
        except AssertionError:
            continue
        return p, dict(top=top)
    raise AssertionError(f"{q['key']}: dungeon not built")


# ---------------------------------------------------------------------------------------------------- umieszczenie
def _reach(world, start):
    S = len(world.floors[0])
    g = world.floors[0]
    seen = np.zeros((S, S), bool)
    dq = deque([start])
    seen[start[1], start[0]] = True
    while dq:
        x, y = dq.popleft()
        for dx, dy in F.DIRS4:
            nx, ny = x + dx, y + dy
            if 0 <= nx < S and 0 <= ny < S and not seen[ny, nx] and F.walk(g[ny][nx]):
                seen[ny, nx] = True
                dq.append((nx, ny))
    return seen


def build(ctx, cost, helpers):
    world = ctx['world']
    mark = helpers['mark']
    towns = dict(ctx['towns'])
    reach_cache = {}
    ctx.setdefault('reward_reqs', [])
    ctx.setdefault('star_choices', [])
    out = []
    for k, q in enumerate(QUESTS):
        rng = random.Random(SEED + 17 * k)
        if q['site'] == 'tower':
            plan, info = tower_plan(q, rng)
            w, h = 29 + 6, 13 + 6
        else:
            plan, info = delve_plan(q, rng, SEED + 977 * k)
            w, h = 20 + 6, 13 + 6
        if q['town'] not in reach_cache:
            reach_cache[q['town']] = _reach(world, towns[q['town']][:2])
        hh = dict(helpers, reach=reach_cache[q['town']])
        tx, ty = St.find_site(ctx, hh, cost, q['near'], w, h, radius=160, min_temple=34 if q['tier'] == 'easy' else 70, max_water=0.1)
        origin = (tx + 3, ty + 3)
        St.clear_surface(ctx, tx - 2, ty - 2, tx + w + 1, ty + h + 1)
        W = St.stamp(ctx, plan, origin, mark)
        npc = W(NPC_AT[0], NPC_AT[1], 0)
        door = W(DOOR[0], DOOR[1], 0)
        top = info['top']
        chests = []
        for (x, y, z, gold, item, n) in plan.chests:
            a = W(x, y, z)
            chests.append((a[0], a[1], z, item))
            ctx['star_choices'].append((q['key'], a[0], a[1], z, item))
        ctx['reward_reqs'].append((q['key'], 'quest:' + q['key'] + '_m3'))
        if q.get('gate'):
            # wymog przejscia na wejsciu: quest:<klucz> | party:<N> | flag:<nazwa> (patrz world/QuestGate)
            ctx.setdefault('star_gates', []).append((door[0], door[1], 0, q['gate']))
        rec = dict(key=q['key'], tier=q['tier'], level=q['level'], premium=q['premium'], site=q['site'], npc=npc,
                   door=door, chests=chests, top=top, origin=origin, town=q['town'])
        out.append(rec)
        ctx.setdefault('story_quests', []).append(rec)
    return out


# ------------------------------------------------------------------------------------------------------- dane gry
BEGIN, END = '# BEGIN story_quests (tools/mapgen/story_quests.py data)', '# END story_quests'


def _splice(path, lines, header=None):
    text = open(path, encoding='utf-8').read() if os.path.exists(path) else (header or '')
    if BEGIN in text:
        a = text.index(BEGIN)
        b = text.index(END) + len(END)
        text = text[:a].rstrip('\n') + '\n' + text[b:].lstrip('\n')
    text = text.rstrip('\n') + '\n' + BEGIN + '\n' + '\n'.join(lines) + '\n' + END + '\n'
    open(path, 'w', encoding='utf-8').write(text)


def write_data():
    quests, chains, lines_csv, dialogues = [], [], [], []
    en, es = [], []
    for q in QUESTS:
        (e1, g1), (e2, g2), (e3, g3) = rewards_of(q)
        npc = q['npc'][0]
        k1, k2, k3 = q['key'] + '_m1', q['key'] + '_m2', q['key'] + '_m3'
        lvl = q['level']
        place_in = inner_name(q)
        n1 = (f"Do środka: {q['place'][0]}", f"Inside: {q['place'][1]}", f"Dentro: {q['place'][2]}")
        d1 = (f"{q['story']} Wejdź do środka ({place_in}) i rozejrzyj się.",
              f"Step inside ({inner_name(q, 1)}) and have a look around.",
              f"Entra ({inner_name(q, 2)}) y echa un vistazo.")
        quests.append(f"{k1};{n1[0]};{npc};VISIT;{place_in};1;{e1};{g1};{lvl};{d1[0]}")
        quests.append(f"{k2};{q['m2'][2][0]};{npc};KILL;{q['m2'][0]};{q['m2'][1]};{e2};{g2};{lvl};{q['m2'][3][0]}")
        quests.append(f"{k3};{q['m3'][2][0]};{npc};KILL;{q['m3'][0]};{q['m3'][1]};{e3};{g3};{lvl};{q['m3'][3][0]}")
        chains.append(f"{k2};{k1};-")
        chains.append(f"{k3};{k2};-")
        prem = 1 if q['premium'] else 0
        where = (f"{q['place'][0]}, okolice miasta {q['town']}", f"{q['place'][1]}, near {q['town']}", f"{q['place'][2]}, cerca de {q['town']}")
        lines_csv.append(f"{k1};{q['tier']};{prem};{q['line'][0]};{where[0]}")
        for lang, rows, idx in ((1, en, 1), (2, es, 2)):
            rows.append(f"quest;{k1};name;{n1[idx]}")
            rows.append(f"quest;{k1};desc;{d1[idx]}")
            rows.append(f"quest;{k2};name;{q['m2'][2][idx]}")
            rows.append(f"quest;{k2};desc;{q['m2'][3][idx]}")
            rows.append(f"quest;{k3};name;{q['m3'][2][idx]}")
            rows.append(f"quest;{k3};desc;{q['m3'][3][idx]}")
            rows.append(f"quest;{k1};line;{q['line'][idx]}")
            rows.append(f"quest;{k1};where;{where[idx]}")
            rows.append(f"place;{q['place'][0]};name;{q['place'][idx]}")
            rows.append(f"place;{place_in};name;{inner_name(q, idx)}")
            rows.append(f"npc;{npc};name;{q['npc'][1]}")
            rows.append(f"npc;{npc};greeting;{q['greet'][idx]}")
        dialogues.append(f"{npc};historia,{q['place'][0].split()[0].lower()},{q['key']};{q['story']}")
        door_txt = (f"Drzwi do wnętrza wpuszczą tylko od poziomu {q['door']}." if q['door']
                    else 'Wejście jest otwarte dla każdego.')
        dialogues.append(f"{npc};wejście,drzwi,poziom,nagroda,skrzynia,skarb;{door_txt} Na końcu czeka sala ze skrzyniami: otworzysz tylko jedną, po oddaniu mojego ostatniego zadania. Każda ma tabliczkę z opisem.")
    _splice(os.path.join(RES, 'data', 'quests.csv'), quests)
    _splice(os.path.join(RES, 'data', 'quest_chains.csv'), chains)
    write_lines(lines_csv)
    _splice(os.path.join(RES, 'data', 'dialogues.csv'), dialogues)
    _splice(os.path.join(RES, 'i18n', 'data_en.csv'), en)
    _splice(os.path.join(RES, 'i18n', 'data_es.csv'), es)
    print('data written:', len(quests), 'missions')


def rebalance(apply=False):
    """Wyrownanie nagrod starych zadan do jednej skali (patrz docs/GAME_BALANCE.md, "Zadania"):
    doswiadczenie wzgledem kosztu poziomu (level_cost): boss 0,5 poziomu, N <= 25 zabic 0,10, N > 25 albo zbieranie 0,12;
    nagroda wzrasta do tej wartosci (nigdy nie maleje poza lancuchem przemytnikow, ktory byl 1,4-1,7 poziomu na misje i dostaje
    limit 1,2 poziomu); zloto rosnie w tej samej proporcji co doswiadczenie (jesli zadanie w ogole daje zloto). Misje star_* i fabularne nie sa ruszane."""
    path = os.path.join(RES, 'data', 'quests.csv')
    story = set()
    for q in QUESTS:
        story |= {q['key'] + s for s in ('_m1', '_m2', '_m3')}
    out, report = [], []
    inside = False
    for ln in open(path, encoding='utf-8').read().split('\n'):
        if ln == BEGIN:
            inside = True
        if inside or ln.startswith('#') or not ln.strip():
            out.append(ln)
            if ln == END:
                inside = False
            continue
        f = ln.split(';', 9)
        k, kind, n, exp, gp, lvl = f[0], f[3], int(f[5]), int(f[6]), int(f[7]), int(f[8])
        if k.startswith('star_') or k in story:
            out.append(ln)
            continue
        cost = level_cost(lvl)
        factor = 0.5 if (kind == 'KILL' and n == 1) else 0.10 if n <= 25 else 0.12
        new_exp = exp
        if exp < 0.9 * factor * cost:
            new_exp = int(round(factor * cost, -3 if factor * cost >= 1e5 else -2))
        if exp > 1.2 * cost and k.startswith('varden_smug'):
            new_exp = int(round(1.2 * cost, -2))
        new_gp = gp
        if gp > 0 and new_exp > exp:
            new_gp = int(round(gp * new_exp / exp, -3 if new_exp >= 1e6 else -2))
        if (new_exp, new_gp) != (exp, gp):
            report.append((k, lvl, exp, new_exp, gp, new_gp, round(exp / cost, 2), round(new_exp / cost, 2)))
            f[6], f[7] = str(new_exp), str(new_gp)
        out.append(';'.join(f))
    if apply:
        open(path, 'w', encoding='utf-8').write('\n'.join(out))
    return report


def tier_of(level):
    return 'easy' if level < 30 else 'medium' if level < 60 else 'mediumhard' if level < 120 else 'hard' if level < 250 else 'extreme'


PREMIUM_TOWNS = {'Valmir', 'Selvaran', 'Nerys', 'Przystań Wschodnia', 'Wyspa Gasnącej Gwiazdy'}


def write_lines(story_lines):
    """data/quest_lines.csv: kazda linia zadan (pierwsza misja lancucha): tier, premium, nazwa linii, miejsce (puste = domyslne)."""
    npc_town = {}
    for ln in open(os.path.join(RES, 'maps', 'world', 'npcs.csv'), encoding='utf-8'):
        f = ln.rstrip('\n').split(';')
        if len(f) > 4 and not ln.startswith('#'):
            npc_town[f[0]] = f[3]
    chained = set()
    for ln in open(os.path.join(RES, 'data', 'quest_chains.csv'), encoding='utf-8'):
        f = ln.strip().split(';')
        if len(f) == 3 and not ln.startswith('#') and f[1] != '-':
            chained.add(f[0])
    story = {s.split(';')[0] for s in story_lines}
    rows = []
    for ln in open(os.path.join(RES, 'data', 'quests.csv'), encoding='utf-8'):
        f = ln.rstrip('\n').split(';')
        if ln.startswith('#') or len(f) < 10 or f[0] in chained or f[0] in story:
            continue
        giver_town = npc_town.get(f[2], '')
        giver_town = 'Wyspa Gasnącej Gwiazdy' if f[2] == 'star_warden' else giver_town
        rows.append(f"{f[0]};{tier_of(int(f[8]))};{1 if giver_town in PREMIUM_TOWNS else 0};;")
    path = os.path.join(RES, 'data', 'quest_lines.csv')
    with open(path, 'w', encoding='utf-8') as fh:
        fh.write('# Linie zadan (dziennik zadan): pierwsza misja lancucha;poziom trudnosci (easy/medium/mediumhard/hard/extreme);'
                 'premium (0/1);nazwa linii (puste = nazwa misji);miejsce (puste = zleceniodawca)\n'
                 '# Generowane przez tools/mapgen/story_quests.py data\n')
        fh.write('\n'.join(rows + story_lines) + '\n')


if __name__ == '__main__':
    if sys.argv[1:] == ['data']:
        write_data()
    elif sys.argv[1:2] == ['rebalance']:
        for r in rebalance(apply=sys.argv[2:] == ['apply']):
            print(*r)
