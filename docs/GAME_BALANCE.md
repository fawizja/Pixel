# Balans gry

Wszystkie liczby balansu sa w jednym miejscu: `shared/.../balance/Balance.java` (krzywe),
`shared/.../vocation/Vocation.java` (profesje), `data/items.csv` (przedmioty) i
`data/starting.properties` (start). Korekty robimy w danych i krzywych, nie wyjatkami w kodzie.

Nazwy przedmiotow i potworow w tym dokumencie sa robocze (angielskie, jak klucze danych); w grze od 0.25.0
wyswietlane sa polskie nazwy z `items.csv` / `monsters.csv`.

## Start postaci (konfigurowalny)

| Ustawienie | Wartosc | Klucz w `starting.properties` |
|---|---|---|
| Poziom startowy (serwer) | 8 | `start.level` |
| Poziom startowy gry offline | 300 (umiejetnosci: bron ~91, magia magow 70, obrona ~82; HP/mana z poziomu) | `start.level.offline`, `-Dpixel.startLevel` |
| Zloto w plecaku | 10 000 gp (wydane jako 1 Crystal Coin) | `start.gold.backpack` |
| Zloto w banku | 0 gp (10 000 gp **nie** trafia do banku) | `start.gold.bank` |
| Miasto startowe | Varden (swiatynia, strefa bezpieczna) | `start.town` |
| Maksymalny poziom | 1000 | `level.max` |

Kwota startowa jest dodawana w najwyzszych nominalach (`Money.add`), wiec 10 000 gp zajmuje jeden slot.

## Doswiadczenie

`exp(L) = 50/3 * (L^3 - 6L^2 + 17L - 12)` - krzywa szescienna, koszt kolejnego poziomu rosnie kwadratowo.
Wartosci sa liczone w `long` z kontrola przepelnienia (`Math.*Exact`), dodawanie doswiadczenia jest
nasycane do maksimum poziomu 1000 (`Balance.addExperience`).

### Gildia (0.21.0)
Zalozenie kosztuje 50 000 gp - ok. 5x startowe 10 000 gp, wiec nowa postac nie zalozy gildii od razu,
a dla poziomu 60+ to rozsadny wydatek (zloto znika z gry - ujscie monet).

### Druzyna (0.19.0)
Udzial czlonka = doswiadczenie solo wg jego poziomu * (1 + 0,2 * (n - 1)) / n: 2 osoby po 60%, 4 po 40%, 8 po 30%.
Druzyna zdobywa lacznie wiecej (premia za wspolprace), ale kazdy osobno mniej niz solo na tym samym potworze -
oplaca sie na bossach i potworach za mocnych dla jednej osoby. Wymagane poziomy w stosunku 2/3, zasieg 30 kafelkow.

## Statystyki wg poziomu

HP i mana na poziomie 8 sa wspolne (185 / 90), potem rosna o przyrost profesji.
Szybkosc: `220 + 25 * sqrt(L - 7)`, przeliczana na kafelki/s (`speed / 80`) z gornym limitem 8 kafelkow/s
(czytelnosc na telefonie; powyzej ok. poziomu 300 dalszy wzrost szybkosci dadza czary i przedmioty).

| Poziom | Exp calkowity | Exp do nast. | Paladin HP/MP | Druid HP/MP | Sorcerer HP/MP | Monk HP/MP | Szybkosc (kaf./s) |
|---|---|---|---|---|---|---|---|
| 8 | 4 200 | 2 200 | 185 / 90 | 185 / 90 | 185 / 90 | 185 / 90 | 245 (3.1) |
| 20 | 98 800 | 17 200 | 305 / 270 | 245 / 450 | 245 / 450 | 341 / 162 | 310 (3.9) |
| 60 | 3 256 800 | 171 200 | 705 / 870 | 445 / 1650 | 445 / 1650 | 861 / 402 | 402 (5.0) |
| 100 | 15 694 800 | 485 200 | 1105 / 1470 | 645 / 2850 | 645 / 2850 | 1381 / 642 | 461 (5.8) |
| 150 | 54 042 300 | 1 102 700 | 1605 / 2220 | 895 / 4350 | 895 / 4350 | 2031 / 942 | 519 (6.5) |
| 300 | 441 084 800 | 4 455 200 | 3105 / 4470 | 1645 / 8850 | 1645 / 8850 | 3981 / 1842 | 648 (8.0) |
| 500 | 2 058 474 800 | 12 425 200 | 5105 / 7470 | 2645 / 14850 | 2645 / 14850 | 6581 / 3042 | 775 (8.0) |
| 750 | 6 975 212 300 | 28 012 700 | 7605 / 11220 | 3895 / 22350 | 3895 / 22350 | 9831 / 4542 | 901 (8.0) |
| 1000 | 16 566 949 800 | - | 10105 / 14970 | 5145 / 29850 | 5145 / 29850 | 13081 / 6042 | 1008 (8.0) |

## Profesje

| | Paladin | Druid | Sorcerer | Monk |
|---|---|---|---|---|
| HP / poziom | 10 | 5 | 5 | 13 |
| Mana / poziom | 15 | 30 | 30 | 6 |
| Udzwig / poziom | 20 | 10 | 10 | 22 |
| Regeneracja HP / MP (baza na tik 6 s) | 2 / 3 | 1 / 4 | 1 / 4 | 3 / 2 |
| Walka wrecz | 0.85 | 0.50 | 0.50 | **1.25** |
| Dystans | **1.25** | 0.50 | 0.50 | 0.40 |
| Magia ofensywna | 0.60 | 1.00 | **1.25** | 0.50 |
| Leczenie | 0.85 | **1.30** | 0.70 | 0.90 |
| Obrona | 1.00 | 0.80 | 0.75 | **1.15** |
| Odstep atakow | 2.0 s | 2.0 s | 2.0 s | **1.4 s** |
| Styl | dystans + swieta magia | leczenie, lod, ziemia | ogien, energia, smierc | piesci, kombinacje, samoleczenie |

Regeneracja rosnie lekko z poziomem: `baza + poziom * przyrost / 400`.
Wspolczynniki obrazen zostana uzyte przez system walki (etap 6). Testy pilnuja, ze kazda profesja
jest najlepsza w swojej roli i ze profile sa rozne (`VocationTest`).

## Przedzialy rozwoju (orientacyjne, nie blokady)

| Poziomy | Przedzial |
|---|---|
| 8-20 | Poczatkujacy |
| 20-60 | Podstawowe expowiska |
| 60-150 | Srednio zaawansowane regiony |
| 150-300 | Trudniejsze obszary |
| 300-500 | Zaawansowane expowiska |
| 500-750 | Bardzo trudne regiony |
| 750-1000 | Endgame i bossowie |

## Ekonomia

- 100 Gold Coin = 1 Platinum Coin, 100 Platinum Coin = 1 Crystal Coin (10 000 gp).
- Monety maja stos do 100 sztuk. Platnosci wydaja reszte optymalnie; brak miejsca na reszte anuluje transakcje.
- Ceny przedmiotow (`value` w `items.csv`) sa punktem odniesienia dla sklepow NPC.
- Sklepy (`data/shops.csv`): zapasy (pochodnia 8, lina 60, mikstury 50/55, silne 200/130, pusta runa 15),
  sprzet startowy, a w miastach biomow bron i zbroje na poziom 40-70 za ok. 2x `value`.
- Skup: lupy za `value`, sprzet za 1/4..1/2 `value`. Skup zawsze ponizej najnizszej ceny sprzedazy w swiecie;
  runy, mikstury, amunicja i monety nie sa skupowane (runy powstaja z many - inaczej mana = zloto).
- Zadania: nagrody 2 000 .. 2 500 000 exp i 300 .. 60 000 gp; zadania na przynoszenie dotycza tylko lupow,
  ktorych nie da sie kupic. Kazde zadanie mozna wykonac raz.

## Noc (etap 11)

Nocne drapiezniki na powierzchni w nocy: zasieg aggro +3 (wilk 6 -> 9) i ataki co 80% czasu (wilk co 1,6 s
zamiast 2 s). Pozostale potwory bez zmian. Symulacje i test "godzina na wilkach" obejmuja pelna dobe gry.

## Walka (etap 6, `CombatFormulas`)

- Maksymalne obrazenia gracza (`L` = poziom, `A` = atak broni, piesci = 7):
  - wrecz: `A * (umiejetnosc + 10) / 20 * meleeFactor + L/5` (Monk: FIST, reszta: SWORD),
  - dystans: `A * (distance + 10) / 20 * distanceFactor + L/5`,
  - magia (rozdzki, rozdzki druida): `A * (1 + magic/25) * magicFactor + L/5`.
- Rzut: 40-100% maksimum. Postawa obronna: 80% zadawanych, 75% otrzymywanych obrazen.
- Potwor: fizyczne minus `armor/2..armor`, potem odpornosc w % (ujemna = slabosc).
- Gracz: fizyczne minus `0..pancerz/2` i `0..obrona/3` (tarcza * defenseFactor * shielding); zywioly ignoruja pancerz.
- Exp za potwora: pelny do poziomu `4 * poziom_potwora + 20`, potem proporcjonalnie mniej (min. 10%,
  ale realnie ~0 przy wielkiej roznicy). Potwory elitarne (ok. 4% zwyklych): 2x HP, 1,3x obrazenia, 3x exp, 2x szansa lupow.
  Smierc: -5% exp (-2,5% z blogoslawienstwem kaplana: 200 gp za poziom, min. 1000 gp, jednorazowe), nigdy ponizej poziomu startowego.

## Potwory Varden

| Potwor | Poz. | HP | Exp | Atak | Typ | Zasieg | Zachowanie | Pietro |
|---|---|---|---|---|---|---|---|---|
| Wolf | 8 | 55 | 20 | 2-10 | fiz. | 1 | SKIRMISHER (ucieka <) | 0 |
| Boar | 10 | 90 | 32 | 3-14 | fiz. | 1 | TANK | 0 |
| Forest Spider | 12 | 70 | 35 | 2-12 | ziemia | 1 | MELEE | 0 |
| Bandit | 15 | 140 | 65 | 5-22 | fiz. | 1 | SKIRMISHER | 0 |
| Goblin | 12 | 60 | 30 | 2-12 | fiz. | 4 | RANGED | -1, -2 |
| Goblin Shaman | 18 | 90 | 70 | 6-20 | ogien | 4 | HEALER | -2 |
| Skeleton | 22 | 160 | 90 | 6-26 | fiz. | 1 | MELEE (odporny na smierc, slaby na swiete) | -3 |
| Cave Troll | 32 | 320 | 220 | 10-40 | fiz. | 1 | TANK | -4 |
| Crystal Golem | 45 | 620 | 450 | 15-55 | energia | 2 | MAGIC | -5 |
| Grimtooth (boss) | 60 | 3200 | 4000 | 25-90 | fiz. | 1 | TANK, leczenie 1x, szal <50% | -5 |

## Potwory biomow (etap 9)

| Potwor | Poz. | HP | Exp | Atak | Typ | Zachowanie | Biom |
|---|---|---|---|---|---|---|---|
| Snow Wolf | 40 | 420 | 330 | 15-50 | fizyczny | skirmisher | polnoc |
| Hyena | 45 | 480 | 360 | 15-55 | fizyczny | skirmisher | pustynia |
| Crocodile | 55 | 650 | 520 | 20-70 | fizyczny | tank | rownik |
| Scorpion | 60 | 700 | 600 | 20-75 | ziemia | melee | pustynia |
| Jungle Hunter | 65 | 600 | 650 | 25-75 (zasieg 4) | fizyczny | ranged | rownik |
| Yeti | 70 | 1100 | 950 | 30-95 | fizyczny | tank | polnoc |
| Jungle Serpent | 75 | 760 | 800 | 20-80 | ziemia | skirmisher | rownik |
| Frost Witch | 85 | 800 | 1000 | 25-85 (zasieg 4) | lod | magic | polnoc |
| Ice Golem | 120 | 2600 | 2600 | 50-150 | lod | magic | polnoc |
| Sand Wraith | 130 | 1800 | 2300 | 40-130 (zasieg 3) | smierc | magic | pustynia |
| Temple Guardian | 180 | 4800 | 5200 | 70-210 | fizyczny | tank | rownik |
| Mummy | 200 | 5200 | 6200 | 70-230 | smierc | tank | pustynia |

| **Frost Queen Ysolde** (boss) | 150 | 9000 | 12000 | 60-180 (zasieg 4) | lod | magic, leczenie 1200 | polnoc -5 |
| **Ruin Colossus** (boss) | 200 | 14000 | 19000 | 90-260 | fizyczny | tank, leczenie 2000 | rownik -5 |
| **Pharaoh Neferakh** (boss) | 230 | 16000 | 22000 | 80-250 (zasieg 3) | smierc | magic, leczenie 2500 | pustynia -5 |

Bossowie odradzaja sie co 30 minut i daja unikalne lupy (Frost Tiara, Colossus Core, Pharaoh Scepter).
Postac na poziomie 8, ktora tam dotrze, ginie od pierwszych ciosow (sprawdzone w grze).

Zasady (test `newMonstersAreSensiblyScaled`): exp/HP miedzy 0.3 a 1.4, HP >= 5 x poziom.
Odpornosci tworza slabosci biomow: polnoc slaba na ogien, pustynne nieumarle na swiete i ogien,
dzungla na lod. Nowe lupy: futra, kly, kly krokodyla, luski, zadla, bandaze, skarabeusze, rdzenie lodu
oraz bron i zbroje na poziom 40-100 (Frost Blade, Fur Armor, Serpent Spear, Wand of Storms,
Rod of Vines, Desert Knuckles, Sandstone Shield, Scarab Helmet, Croc Legs).

## Symulacje walk (`CombatSimulationTest`, 300 pojedynkow, bez mikstur i ucieczki)

| Sytuacja | Wynik |
|---|---|
| Kazda profesja, poziom 8, zestaw startowy vs wilk | 100% wygranych, 4-6 s |
| Poziom 8 vs bandyta | 100%, 12-16 s |
| Poziom 8 vs troll / golem / boss | 0-0,7% (za wczesnie) |
| Poziom 30 (sredni sprzet) vs szkielet, troll | 100% |
| Poziom 30 solo vs Grimtooth | 0% (boss na grupe lub wyzszy poziom) |
| Obrazenia na atak, poziom 8 (max / min profesji) | <= 1,6x |
| Obrazenia na atak, poziom 30 rozdzka vs bron | <= 2,0x (czary w etapie 7 wyrownaja magow) |
| Godzina zabijania wilkow od poziomu 8 | <= poziom 20 |
| Poziom 300, godzina na wilkach | < 1% poziomu |

Wnioski: Druid jest najslabszy w obrazeniach (rola wsparcia - leczenie w etapie 7); Sorcerer
cierpi na krysztalowym golemie (80% odpornosci na energie) - zamierzone, trzeba dobrac zywiol.

## Endgame 230-1000 (wschodni kontynent, 0.13.0)

Za mostem ze straznikiem (poziom 200). Kazda kraina ma potwory "w progu", jednego trudniejszego
"elitarnego" (lava_golem, ice_dragon, void_priest, abyss_lord) i bossa dla druzyny.

| Potwor | Poziom | HP | Exp | Atak | Typ, zasieg | Odpornosci | Uwagi |
|---|---|---|---|---|---|---|---|
| Cursed Treant | 240 | 5600 | 6400 | 105-330 co 2400 ms | EARTH, 1 | EARTH:70,FIRE:-30 | TANK |
| Wraith Knight | 280 | 5200 | 7000 | 135-390 co 2000 ms | DEATH, 1 | DEATH:90,HOLY:-35,PHYSICAL:20 | MELEE, nocny |
| Fire Elemental | 320 | 5600 | 7500 | 150-450 co 2300 ms | FIRE, 3 | FIRE:100,ICE:-30,PHYSICAL:30 | MAGIC |
| Lava Golem | 370 | 8600 | 10500 | 155-440 co 2400 ms | FIRE, 1 | FIRE:100,PHYSICAL:30,ICE:-25,ENERGY:-10 | TANK |
| Ignaroth the Molten | 400 | 45000 | 60000 | 440-1230 co 1800 ms | FIRE, 3 | FIRE:100,ICE:-20 | **boss**, leczy sie raz o 4000 |
| Frost Giant | 430 | 10500 | 12500 | 250-720 co 2400 ms | PHYSICAL, 1 | ICE:80,FIRE:-25 | TANK |
| Storm Harpy | 470 | 8200 | 11000 | 235-755 co 2000 ms | ENERGY, 3 | ENERGY:90,EARTH:-20 | SKIRMISHER |
| Ice Dragon | 520 | 13800 | 17000 | 430-1270 co 2300 ms | ICE, 3 | ICE:100,FIRE:-30 | MAGIC |
| Hrimgar the Glacier King | 560 | 70000 | 95000 | 650-1900 co 1800 ms | ICE, 1 | ICE:100,FIRE:-20,PHYSICAL:20 | **boss**, leczy sie raz o 6000 |
| Shadow Stalker | 590 | 11500 | 15500 | 340-990 co 1800 ms | DEATH, 1 | DEATH:90,HOLY:-40 | SKIRMISHER, nocny |
| Sand Titan | 650 | 17000 | 19500 | 340-985 co 2500 ms | PHYSICAL, 1 | PHYSICAL:35,EARTH:60,ICE:-20 | TANK |
| Void Priest | 700 | 17600 | 23600 | 1050-1425 co 2300 ms | DEATH, 4 | DEATH:100,HOLY:-40,EARTH:25 | HEALER |
| Kha'zuul the Hollow | 750 | 95000 | 130000 | 800-2250 co 1800 ms | DEATH, 3 | DEATH:100,HOLY:-30 | **boss**, leczy sie raz o 8000, nocny |
| Abyssal Demon | 800 | 18000 | 25000 | 480-1400 co 2000 ms | FIRE, 1 | FIRE:90,HOLY:-30,ICE:-10 | MELEE |
| Bone Dragon | 880 | 20000 | 28000 | 520-1520 co 2200 ms | DEATH, 3 | DEATH:100,HOLY:-35,EARTH:30 | MAGIC |
| Abyss Lord | 950 | 28100 | 33500 | 1080-2000 co 2300 ms | PHYSICAL, 1 | PHYSICAL:15,FIRE:50,DEATH:50,HOLY:-25 | TANK |
| Mor'gath, Heart of the Abyss | 1000 | 160000 | 250000 | 1040-2990 co 1700 ms | DEATH, 3 | DEATH:100,FIRE:50,HOLY:-25 | **boss**, leczy sie raz o 12000 |

Sprzet (lupy, rzadkie; bossowie daja komplet swojego progu z szansa 25-30%):

| Prog | Poziom | Paladyn | Druid | Czarodziej | Mnich | Zbroja (pancerz) | Tarcza |
|---|---|---|---|---|---|---|---|
| Obsydian | 250 | Magma Spear 64 | Rod of Hoarfrost 62 ICE | Obsidian Wand 62 ENERGY | Molten Knuckles 62 | 12/8/7 | 30 |
| Lodowy | 450 | Frostfang Spear 82 | Rod of Stoneroot 80 EARTH | Wand of Inferno 80 FIRE | Frostbound Fists 80 | 15/10/9 | 36 |
| Cien | 700 | Shadowpiercer 100 | Rod of Twilight 98 ICE | Stormcaller Wand 98 ENERGY | Umbral Grips 98 | 18/12/11 | 42 |
| Otchlan | 850 | Dawnspear 118 | Rod of Eternal Winter 136 ICE | Starfall Wand 116 ENERGY | Fists of the Abyss 116 | + Korona Otchlani 16 (poz. 900) | - |

Zywioly broni sa dobrane tak, by nie trafialy w odpornosci potworow swojego progu (np. rozdzka
ognia nie jest bronia na Spalone Wyzyny).

### Symulacje endgame (`EndgameSimulationTest`, 200 walk, atak + najlepszy czar leczacy ponizej 55% HP)

Postac modelowa progu: Knieje/Wyzyny poz. 320 (umiejetnosc 95, magia 75), Szczyty 500 (110/90),
Pustkowia 680 (120/100), Otchlan 900 (128/112), sprzet swojego progu, tylko poczatkowa mana (bez mikstur).

| Wymaganie testu | Wynik |
|---|---|
| Kazda profesja wygrywa >= 95% walk z kazdym potworem progu, srednio <= 90 s, mana <= 60% | spelnione; najwolniej druid i paladyn na elitach (83-90 s) |
| Boss w pojedynke: <= 10% zwyciestw | 0% dla wszystkich profesji i bossow (gina w 6-126 s) |
| Postac z nizszego progu przeciw najtrudniejszemu potworowi nastepnego: <= 50% zwyciestw albo > 120 s | ice_dragon 0%; void_priest 0-8% (druid 143 s); abyss_lord 0-3% (druid 126 s) |
| Postac poziomu 8 przeciw kazdemu potworowi endgame | 0% |

Slabsze potwory nastepnego progu (np. frost_giant, abyssal_demon) sa do pokonania z progu nizej -
to zamierzone przejscie miedzy krainami, ktorych zakresy poziomow na siebie zachodza.

## Czary, runy i mikstury (etap 7)

Wartosc czaru = `baza + poziom * lf + magia * mf + umiejetnosc * sf`, rzut 85-115%.
Leczenie mnozone przez `healingFactor` profesji (Druid 1,3, Monk 0,9, Paladin 0,85,
Sorcerer 0,7); efekty run przez `magicFactor`. Umiejetnosc: Paladin dystans, Monk piesci.
Trening magii: proby = mana * min(1, magicFactor) (Druid/Sorcerer 1,0, Paladin 0,6, Monk 0,5).

| Czar | Profesje | Poz. | Mana | Odnowienie | Efekt (baza / lf / mf / sf) |
|---|---|---|---|---|---|
| Opatrzenie ran | wszystkie | 8 | 20 | 1 s | leczenie 20 / 0,4 / 2,0 |
| Uzdrowienie | D, S, P | 20 | 60 | 1 s | leczenie 50 / 0,8 / 4,0 |
| Przyplyw zycia | Druid | 35 | 120 | 1 s | leczenie 130 / 1,4 / 7,0 |
| Wewnetrzny spokoj | Monk | 12 | 25 | 1 s | leczenie 30 / 0,6 / - / 0,8 |
| Zar / Iskra | Sorcerer | 8 / 14 | 20 / 30 | 2 s | ogien 15 / energia 20, zasieg 4 |
| Krag ognia | Sorcerer | 25 | 80 | 4 s | ogien, promien 2 |
| Lodowy pocisk / Wybuch cierni | Druid | 8 / 14 | 20 / 30 | 2 s | lod / ziemia, zasieg 4 |
| Mrozna fala | Druid | 30 | 90 | 4 s | lod, promien 2 |
| Swiety pocisk | Paladin | 8 | 20 | 2 s | swiete, zasieg 4 |
| Przeszywajacy rzut | Paladin | 20 | 40 | 3 s | fizyczne, sf 1,5 |
| Swieta fala | Paladin | 30 | 80 | 4 s | swiete, promien 2 |
| Cios tygrysa | Monk | 8 | 15 | 2 s | fizyczne wrecz, sf 1,2 |
| Wirujace kopniecie | Monk | 20 | 40 | 4 s | fizyczne, promien 1 |
| Gleboka medytacja | Monk | 40 | 45 | 1 s | leczenie 50 / 0,7 / - / 1,0 |
| Plomienny grot | Sorcerer | 45 | 120 | 2 s | ogien 60 / 0,5 / 3,5, zasieg 4 |
| Burza energii | Sorcerer | 60 | 220 | 4 s | energia 70 / 0,55 / 3,6, promien 3 |
| Lodowy kolec | Druid | 45 | 110 | 2 s | lod 55 / 0,48 / 3,2, zasieg 4 |
| Gniew ziemi | Druid | 60 | 210 | 4 s | ziemia 65 / 0,5 / 3,3, promien 3 |
| Boska wlocznia | Paladin | 45 | 90 | 3 s | swiete 50 / 0,5 / 1,8 / 1,2, zasieg 4 |
| Boski gniew | Paladin | 60 | 180 | 4 s | swiete 60 / 0,5 / 1,8 / 1,0, promien 3 |
| Piesc smoka | Monk | 45 | 60 | 2 s | fizyczne wrecz 35 / 0,4 / - / 1,4 |
| Burza ciosow | Monk | 60 | 120 | 4 s | fizyczne 40 / 0,45 / - / 1,5, promien 1 |
| Poswiata | wszystkie | 8 | 20 | 2 s | swiatlo 4 kafelki na 5 min |

Czary od poziomu 40-60 (0.130.0) dodane z kontrola symulacji endgame: Gleboka medytacja ma niski koszt many,
bo mnich ma maly zapas many (test `manaShare <= 0,6`).

Grupy: atak 2 s, leczenie 1 s, wsparcie 2 s, mikstury 1 s (osobne - mozna leczyc sie i atakowac).

| Mikstura / jedzenie | Efekt | Wymagania |
|---|---|---|
| Health Potion | 60-90 HP | brak |
| Mana Potion | 50-80 many | brak |
| Great Health Potion | 220-300 HP | poziom 50, Paladin / Monk |
| Great Mana Potion | 140-200 many | poziom 50, Druid / Sorcerer / Paladin |
| Chleb / mieso / ryba | sytosc 5 / 3 / 4 min (podwojna regeneracja, maks. 20 min) | brak |

Odpoczynek (0.133.0): w strefie ochronnej bez blokady po walce regeneracja HP i many jest x3 (x4 z sytoscia).

Wypoczecie (0.134.0): kazda sekunda odpoczynku dodaje sekunde wypoczecia (max 30 min). Pokonanie potwora poza podzialem
druzyny zuzywa 20 s i daje +50% exp (stosowane po premiach zwoju, potwora dnia i wydarzenia). Pelne 30 min = 90 walk.

Symulacje z botem (`SpellBotSimulationTest`): kazda profesja na poziomie 8 przez 10 minut przy
wilkach pod Varden - zero smierci, co najmniej 5 zabitych; poziom 8 ze 100 miksturami przeciw
Grimtoothowi - zawsze przegrywa (boss zadaje do 160 obrazen, wiecej niz mikstury nadrabiaja).

## Do zrobienia w kolejnych etapach

Krzywe umiejetnosci i fishingu (etap 8), ceny NPC (etap 12), PvP (etap 13).

## PvP (etap 13)

Symulacja `PvpRulesTest.equalLevelDuelsAreNeitherInstantNorEndless`: poziom 50, ekwipunek startowy, sam atak
podstawowy (bez czarow i mikstur), srednia z 5 walk. Czas zabicia (atakujacy -> cel):

| atakujacy \ cel | Paladin | Druid | Sorcerer | Monk |
|---|---|---|---|---|
| Paladin  | 110 s | 60 s | 60 s | 116 s |
| Druid    | 106 s | 70 s | 70 s | 128 s |
| Sorcerer | 88 s  | 56 s | 56 s | 106 s |
| Monk     | 92 s  | 48 s | 48 s | 95 s  |

Wymog testu: nikt nie ginie szybciej niz w 10 s. Magowie maja mniej zycia (krocej zyja), a ich sila w PvP
przyjdzie z czarami na graczy (jeszcze niezaimplementowane) - do ponownego zbalansowania razem z nimi.
