# Mapa swiata

Swiat ma **cztery glowne miasta** ulozone wzdluz osi klimatycznej polnoc-poludnie. Biomy przechodza
w siebie stopniowo (tundra -> lasy umiarkowane -> dzungla -> pustynia), a regiony lacza drogi,
mosty, przelecze, porty, tunele i jaskinie.

Swiat jest jedna mapa `maps/world` (160 x 232 kafelkow, pietra -5..+5), generowana
deterministycznie przez `tools/mapgen/generate_world.py`. Region Varden jest wklejony bez zmian z
`maps/varden` z przesunieciem (+48, +64) - test `GameWorldTest` pilnuje, ze jest identyczny.

```
 y=0    [ FROSTHOLM ] (79,26)  Mrozne Pustkowia 40-90, Lodowe Jaskinie -1..-5 (wejscie 20,30)
          | trakt polnocny
 y=64   [ VARDEN ]   (79,80)   start, Krainy Varden 8-60, Kopalnia Varden -1..-5
          | trakt poludniowy, rzeka
 y=112                     [ SELVARAN ] (113,134)  Dzungla 55-80, Zatopione Ruiny -1..-5 (wejscie 60,150)
          | trakt przez sawanne
 y=172  [ QASIRAH ]  (79,193)  Pustynia 45-130, Grobowce -1..-3 (wejscie 124,206)
 y=231
```

## Miasta (maks. 4, `towns=` w map.properties)

| Miasto | Swiatynia | Biom | Charakter |
|---|---|---|---|
| **Varden** | 79,80,0 | umiarkowany | miasto startowe: swiatynia, bank, sklepy, wieza 0..+5 |
| **Frostholm** | 79,26,0 | snieg i lod | mury z lodu, drewniane domy, zamarzniete posadzki |
| **Selvaran** | 113,134,0 | rownik (dzungla, bagna) | mury porosniete mchem, palmy, bagna wokol |
| **Qasirah** | 79,193,0 | pustynia | piaskowiec, oaza, kaktusy |

Kazde miasto ma swiatynie w strefie ochronnej (miejsce odrodzenia po smierci - `homeTown`), plac z
fontanna i latarniami oraz domy. NPC i uslugi przyjda w etapie 12.

## Regiony (`regions=`) i zalecane poziomy

Wejscie do regionu wypisuje jego nazwe i zalecany poziom; gdy minimum regionu jest o ponad 15
poziomow wyzsze od poziomu postaci, pojawia sie ostrzezenie. To informacja, nie blokada.

| Region | Obszar | Pietra | Poziomy | Potwory |
|---|---|---|---|---|
| Krainy Varden | y 64..111 | -5..+5 | 8-60 | wilki, dziki, pajaki, bandyci, kopalnia (gobliny..Grimtooth) |
| Mrozne Pustkowia | y 0..63 | 0..+5 | 40-90 | wilki sniezne, yeti, lodowe wiedzmy |
| Lodowe Jaskinie | y 0..63 | -1 | 70-100 | yeti, lodowe wiedzmy |
| Glebokie Lodowe Jaskinie | y 0..63 | -5..-2 | 100-150 | lodowe golemy |
| Dzungla Selvaran | y 112..171 | 0..+5 | 55-80 | krokodyle, weze, mysliwi |
| Zatopione Ruiny | 30..90 x 130..185 | -1 | 65-90 | weze dzungli |
| Serce Ruin | 30..90 x 130..185 | -5..-2 | 150-200 | straznicy swiatyni |
| Pustynia Qasirah | y 172..231 | 0..+5 | 45-130 | hieny, skorpiony, piaskowe upiory |
| Grobowce Qasirah | 100..159 x 185..231 | -1 | 60-130 | skorpiony, piaskowe upiory |
| Krypty Faraonow | 100..159 x 185..231 | -5..-2 | 180-230 | mumie |

Potwory powyzej poziomu 15 nie stoja blizej niz 14 kafelkow od swiatyni, a poziom kazdego spawnu
miesci sie w zalecanym zakresie regionu (test `spawnsAreValidAwayFromTownsAndMatchRegionLevels`).
Nowa postac (poziom 8) idaca na polnoc lub poludnie trafia najpierw na ostrzezenie i potwory
40+, ktore ja szybko pokonaja - nie da sie "przypadkiem" wejsc w endgame.

Poziomy 230..1000 (glebie swiata, bossowie endgame) zostana dodane w kolejnych etapach.

## Region Varden (`maps/varden`, 64 x 48; w swiecie przesuniety o +48, +64)

Wspolrzedne ponizej sa wzgledem `maps/varden` (w swiecie dodaj 48 do x i 64 do y).

- **Miasto** (mury 18..44 x 10..34), cztery bramy (N, S, E, W).
  - Swiatynia (start, `spawn=31,16,0`, strefa bezpieczna), oltarz, pochodnie.
  - Bank (lada), sklep ogolny, sklep magiczny, dwa domy mieszkancow.
  - Plac z fontanna i latarniami (zrodla swiatla w nocy).
  - Wieza straznicza (38..42 x 27..31) - **schody od 0 do +5**, na +5 taras widokowy.
- **Okolice**: las na obrzezach, rzeka Ashwater na wschodzie (most na trakcie wschodnim),
  jezioro na poludniowym wschodzie (miejsce na fishing), pola uprawne z plotem na poludniowym zachodzie.
- **Kopalnia Varden** (polana z ogniskiem na zachodzie, wejscie po schodach w (8,22)):
  - -1: chodniki z pochodniami, schody w dol (14,28)
  - -2: dziura w (4,30) - zejscie na -3
  - -3: drabina w gore (4,30), schody w dol (15,16)
  - -4: dziura w (6,18) - zejscie na -5
  - -5: najglebsza komora (miejsce na bossa), krysztaly jako zrodla swiatla
  Glebsze pietra sa ciemniejsze; z kazdego pietra jest droga powrotna (test `WorldMapTest`).

## Pietra

| Poziom | Przeznaczenie |
|---|---|
| +1 .. +5 | wzgorza, gory, wieze, przelecze, szczyty |
| 0 | powierzchnia |
| -1 .. -5 | jaskinie, kopalnie, krypty, lochy, glebokie podziemia |

Przejscia: `STAIRS` (wejscie na kafelek), `HOLE` (zejscie przez dziure), `TUNNEL`,
`LADDER` (trzeba uzyc), `ROPE_SPOT` (wymaga liny). Ladowanie mapy odrzuca przejscia prowadzace
w sciane albo na inne przejscie (petla).

## Spawny Varden (`maps/varden/spawns.csv`)

| Miejsce | Potwory | Pietro |
|---|---|---|
| Pola na zachod od miasta (10,8) | 2x wilk | 0 |
| Las poludniowo-zachodni (14,30) | 2x wilk | 0 |
| Pola polnocno-wschodnie (42,6) | 3x dzik | 0 |
| Las poludniowy (24,42) | 3x lesny pajak | 0 |
| Obozy bandytow (58,12), (58,32) | 2x bandyta | 0 |
| Kopalnia, poziom 1-2 | gobliny, goblin szaman | -1, -2 |
| Krypta kopalni | szkielety | -3 |
| Glebokie chodniki | trolle jaskiniowe | -4 |
| Krysztalowa grota i komnata krola | golem, boss Grimtooth (odrodzenie 20 min) | -5 |

Miasto (mury i budynki, wszystkie pietra wiezy) jest strefa ochronna: brak walki, potwory nie wchodza.
Wejscie na pietro z potworami o poziomie > 2 x poziom postaci + 10 wyswietla ostrzezenie.
