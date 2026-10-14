# Balans gry

Wszystkie liczby balansu sa w jednym miejscu: `shared/.../balance/Balance.java` (krzywe),
`shared/.../vocation/Vocation.java` (profesje), `data/items.csv` (przedmioty) i
`data/starting.properties` (start). Korekty robimy w danych i krzywych, nie wyjatkami w kodzie.

## Start postaci (konfigurowalny)

| Ustawienie | Wartosc | Klucz w `starting.properties` |
|---|---|---|
| Poziom startowy | 8 | `start.level` |
| Zloto w plecaku | 10 000 gp (wydane jako 1 Crystal Coin) | `start.gold.backpack` |
| Zloto w banku | 0 gp (10 000 gp **nie** trafia do banku) | `start.gold.bank` |
| Miasto startowe | Varden (swiatynia, strefa bezpieczna) | `start.town` |
| Maksymalny poziom | 1000 | `level.max` |

Kwota startowa jest dodawana w najwyzszych nominalach (`Money.add`), wiec 10 000 gp zajmuje jeden slot.

## Doswiadczenie

`exp(L) = 50/3 * (L^3 - 6L^2 + 17L - 12)` - krzywa szescienna, koszt kolejnego poziomu rosnie kwadratowo.
Wartosci sa liczone w `long` z kontrola przepelnienia (`Math.*Exact`), dodawanie doswiadczenia jest
nasycane do maksimum poziomu 1000 (`Balance.addExperience`).

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
  ale realnie ~0 przy wielkiej roznicy). Smierc: -5% exp, nigdy ponizej poziomu startowego.

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
| Poswiata | wszystkie | 8 | 20 | 2 s | swiatlo 4 kafelki na 5 min |

Grupy: atak 2 s, leczenie 1 s, wsparcie 2 s, mikstury 1 s (osobne - mozna leczyc sie i atakowac).

| Mikstura / jedzenie | Efekt | Wymagania |
|---|---|---|
| Health Potion | 60-90 HP | brak |
| Mana Potion | 50-80 many | brak |
| Great Health Potion | 220-300 HP | poziom 50, Paladin / Monk |
| Great Mana Potion | 140-200 many | poziom 50, Druid / Sorcerer / Paladin |
| Chleb / mieso / ryba | sytosc 5 / 3 / 4 min (podwojna regeneracja, maks. 20 min) | brak |

Symulacje z botem (`SpellBotSimulationTest`): kazda profesja na poziomie 8 przez 10 minut przy
wilkach pod Varden - zero smierci, co najmniej 5 zabitych; poziom 8 ze 100 miksturami przeciw
Grimtoothowi - zawsze przegrywa (boss zadaje do 160 obrazen, wiecej niz mikstury nadrabiaja).

## Do zrobienia w kolejnych etapach

Krzywe umiejetnosci i fishingu (etap 8), ceny NPC (etap 12), PvP (etap 13).
