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

## Do zrobienia w kolejnych etapach

Krzywe obrazen, pancerza i odpornosci, potworow i bossow, lupow, kosztow many i cooldownow, czarow
i run - razem z symulacjami walk porownujacymi profesje na niskich, srednich i wysokich poziomach.
