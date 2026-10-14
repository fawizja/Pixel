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
- Ceny przedmiotow (`value` w `items.csv`) sa punktem odniesienia dla sklepow NPC (etap 12).

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

## Do zrobienia w kolejnych etapach

Koszty many i cooldowny czarow, runy, mikstury - z ponownym uruchomieniem symulacji.
