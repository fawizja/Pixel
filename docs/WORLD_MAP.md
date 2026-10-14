# Mapa swiata

Swiat ma **cztery glowne miasta** ulozone wzdluz osi klimatycznej polnoc-poludnie. Biomy przechodza
w siebie stopniowo (tundra -> lasy umiarkowane -> dzungla -> pustynia), a regiony lacza drogi,
mosty, przelecze, porty, tunele i jaskinie.

```
                     [ FROSTHOLM ]  (polnoc: snieg i lod)
                          |  Przelecz Szronu (+2..+4), lodowe jaskinie (-1..-5)
                          |
   Kopalnia Varden  -- [ VARDEN ] -- most na rzece Ashwater -- trakt wschodni
   (-1..-5)              (start, klimat umiarkowany)                  |
                          |                                           |
                          |  Trakt poludniowy, wzgorza                port rzeczny
                          |                                           |
                     [ QASIRAH ] <---- kaniony, oazy ---- [ SELVARAN ] (rownik: dzungla, bagna)
                     (poludnie: pustynia)
```

## Miasta

| Miasto | Biom | Charakter | Status |
|---|---|---|---|
| **Varden** | umiarkowany | klasyczne miasto startowe: kamienne mury, swiatynia, bank, sklepy, wieza straznicza | **zbudowane (etap 9 rozpoczety)** |
| Frostholm | snieg i lod | twierdza z drewna i kamienia, kuznia, port na lodowym jeziorze | zaplanowane |
| Selvaran | dzungla, bagna | miasto na palach i pomostach, port rzeczny, alchemicy | zaplanowane |
| Qasirah | pustynia | miasto z piaskowca wokol oazy, bazar, wejscia do grobowcow | zaplanowane |

## Region Varden (zbudowany, `maps/varden`, 64 x 48 kafelkow)

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

## Planowane regiony i expowiska

| Region | Biom | Pietra | Poziomy | Potwory |
|---|---|---|---|---|
| Laki i lasy Varden | umiarkowany | 0 | 8-20 | wilki, dziki, pajaki |
| Kopalnia Varden | podziemia | -1..-5 | 15-60 | gobliny, szkielety, trolle jaskiniowe |
| Ruiny i krypty polnocne | umiarkowany | 0, -1..-3 | 20-60 | bandyci, szkielety, nieumarli |
| Przelecz Szronu | snieg | +2..+4 | 60-150 | wilki sniezne, niedzwiedzie |
| Lodowe jaskinie Frostholm | lod | -1..-5 | 150-300 | lodowe pajaki, trolle mrozu, zywiolaki lodu |
| Bagna Selvaran | rownik | 0 | 60-150 | krokodyle, weze, wielkie owady |
| Zaginione swiatynie | rownik | -1..-4 | 300-500 | roslinne potwory, straznicy swiatyn |
| Kaniony Qasirah | pustynia | 0, +1..+3 | 150-300 | skorpiony, hieny, piaskowe potwory |
| Grobowce faraonow | pustynia | -1..-5 | 500-750 | mumie, straznicy ruin |
| Glebie swiata | wszystkie | -5 | 750-1000 | bossowie endgame |
