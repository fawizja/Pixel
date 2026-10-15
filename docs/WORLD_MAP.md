# Mapa swiata

Swiat ma **cztery glowne miasta** ulozone wzdluz osi klimatycznej polnoc-poludnie. Biomy przechodza
w siebie stopniowo (tundra -> lasy umiarkowane -> dzungla -> pustynia), a regiony lacza drogi,
mosty, przelecze, porty, tunele i jaskinie.

Swiat jest jedna mapa `maps/world` (256 x 232 kafelkow, pietra -5..+5), generowana
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

Za ciesnina (x 160..167, sama woda) lezy **wschodni kontynent** (x 168..255) - endgame 230-1000:

```
 y=0    Lodowe Szczyty 400-560   schody na +1 (214,30) -> +2: Hrimgar (242,8,+2)
 y=64   Przeklete Knieje 230-300 <- most (160..167, y 86-87, straznik: poziom 200) <- trakt z Varden
                                    oboz za mostem (172..186 x 80..93, strefa ochronna, bez swiatyni):
                                    kwatermistrz Bren, skarbniczka Wena, zwiadowczyni Ilka (zadania)
 y=112  Spalone Wyzyny 300-400   krater z lawa, Ignaroth (212,142)
 y=172  Pustkowia Cienia 550-750 ruiny Kha'zuula (222..238 x 198..212), zejscie do Otchlani (196,216)
        Otchlan -1..-5 750-1000  Mor'gath (240,170,-5)
```

Zachodni kontynent jest losowany ta sama sekwencja co przed rozszerzeniem (wschod ma osobny generator
liczb), wiec stare zapisy i lochy sa nietkniete.

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
| Przeklete Knieje | 168..255 x 64..111 | 0..+5 | 230-300 | przeklete drzewce, upiorni rycerze |
| Spalone Wyzyny | 168..255 x 112..171 | 0..+5 | 300-400 | ogniste zywiolaki, golemy lawy, Ignaroth |
| Lodowe Szczyty | 168..255 x 0..63 | 0..+5 | 400-560 | lodowi olbrzymi, harpie burzy, lodowe smoki, Hrimgar |
| Pustkowia Cienia | 168..255 x 172..231 | 0..+5 | 550-750 | cieniolowcy, tytani piasku, kaplani pustki, Kha'zuul |
| Otchlan | 168..255 x 0..231 | -5..-1 | 750-1000 | demony, kosciane smoki, panowie Otchlani, Mor'gath |

Potwory powyzej poziomu 15 nie stoja blizej niz 14 kafelkow od swiatyni, a poziom kazdego spawnu
miesci sie w zalecanym zakresie regionu (test `spawnsAreValidAwayFromTownsAndMatchRegionLevels`).
Nowa postac (poziom 8) idaca na polnoc lub poludnie trafia najpierw na ostrzezenie i potwory
40+, ktore ja szybko pokonaja - nie da sie "przypadkiem" wejsc w endgame. Na wschodni kontynent
prowadzi jeden most, a straznik (`gates=160,86,167,87,0,200`) zawraca postacie ponizej poziomu 200
z komunikatem; test `theBridgeGuardKeepsNewCharactersOutOfTheEndgame` sprawdza, ze poziomy 8, 120 i 199
nie dojda na wschod zadna droga, a 200 przejdzie.

## Glebokie lochy biomow (etap 10)

Kazdy loch schodzi do pietra -5 roznymi przejsciami:

| Loch | Droga w dol | Boss na -5 |
|---|---|---|
| Lodowe Jaskinie (wejscie 20,30) | schody -1, -2; dziura (8,54) na -3 (drabina w gore); dziura (36,46) na -4 (lina w gore); schody (16,58) na -5 | Frost Queen Ysolde (150) |
| Zatopione Ruiny (wejscie 60,150) | schody -1, -2; dziura (42,174) na -3 (drabina); dziura (74,156) na -4 (lina); schody (40,170) na -5 | Ruin Colossus (200) |
| Grobowce Qasirah (wejscie 124,206) | schody -1..-3; dziura (150,206) na -4 (lina); schody (108,220) na -5 | Pharaoh Neferakh (230) |

Bez liny w plecaku nie da sie wrocic z pieter -4 i -5 (test `withoutARopeTheDeepFloorsAreAOneWayTrip`);
lina jest w zestawie startowym. Kazde z pieter -3..-5 kazdego lochu ma co najmniej 3 zrodla swiatla
(lodowe krysztaly, swiecacy mech i magma, pochodnie scienne).

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
