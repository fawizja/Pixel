# PROJECT_PROGRESS

Plik wznowienia dla rozbudowy "w stylu Tibii" (master prompt Darka z 2026-10-22, etapy 1-20).
Po wznowieniu przeczytaj go razem z AI_WORK_STATE.md i TODO.md. Nie zawiera hasel ani tokenow.

## Cel

Rozbudowac istniejaca gre Pixel (libGDX, Java 17, Android) tak, by klimatem, interfejsem i mechanika
przypominala klasyczna Tibie (OTClient V8 / Canary), ale z autorskim swiatem: cztery duze, rozne miasta
(krolewskie, portowe, gorskie, egzotyczne), wiele expowisk, wielopoziomowe podziemia, statki i mosty.
Zalozenia serwera: kazda profesja startuje na poziomie 300 z 10 000 000 gp w banku.

## Repozytorium

- https://github.com/fawizja/Pixel, branch roboczy: `development` (main = dzialajaca wersja, APK `najnowsza`).
- Punkt wyjscia: main 0ba79e6 (0.196.0). Rownolegle inny watek pracuje na `main` (plecak w ramce ekwipunku,
  ruch po skosie) - `development` regularnie wciaga `main` (merge, bez przepisywania historii).

## Stan etapow

| Etap | Stan | Commit |
|------|------|--------|
| 1. Audyt i zabezpieczenie | ZAKONCZONY | 15af7c2 |
| 2. Grafika, assety, renderowanie | ZAKONCZONY | 55e7e49 |
| 3. System mapy i warstw | ZAKONCZONY | 57d39e5 |
| 4. Geografia swiata | ZAKONCZONY | e79368b |
| 5. Cztery miasta | ZAKONCZONY | 19757e8 |
| 6. Depozyty, banki, swiatynie, sklepy, domy | ZAKONCZONY | 435babc |
| 7. Mosty, statki, podroze | ZAKONCZONY | d9b2950 |
| 8. Expowiska na powierzchni | ZAKONCZONY | 7312299 |
| 9. Wielopoziomowe lochy | ZAKONCZONY | 35ddade |
| 10. Potwory, bossowie, loot | ZAKONCZONY | 0cd1b79 |
| 11. Profesje i poziomy | ZAKONCZONY | 71a19a3 |
| 12. Ruch i walka | ZAKONCZONY | 96e652b |
| 13. Czary, runy, mikstury | ZAKONCZONY | 6522097 |
| 14. Ekwipunek, plecaki, monety | ZAKONCZONY | ea984e9 |
| 15. NPC, questy, ekonomia | ZAKONCZONY | ddead90 |
| 16. UI w stylu OTClient V8 | ZAKONCZONY | c4f43a8 |
| 17. Sterowanie mobilne | ZAKONCZONY | 11f56db |
| 18. Optymalizacja | ZAKONCZONY | 9895785 |
| 19. Testy koncowe | ZAKONCZONY | 22a8d8a |
| 20. ZIP i debug APK | ZAKONCZONY | 5636854 |

Galeria zrzutow po etapach: https://claude.ai/artifact/NQhU2aj1gny6JeFq3Nzekz
APK z development: https://github.com/fawizja/Pixel/releases/tag/development-apk (CI po kazdym pushu)

## Etap 1 - wyniki audytu (2026-10-22)

### Budowa i testy
- `./gradlew test :desktop:installDist :server:installDist` - OK. Na starcie 421 testow (shared 378, server 40, desktop 3), 0 bledow.
- Po zmianie startu: 422 testy (nowy StartingConfigFileTest), 0 bledow.
- APK: Android SDK niedostepny w chmurze; APK buduje CI GitHub Actions (`.github/workflows/build.yml`).
- Gra uruchomiona pod Xvfb (desktop lwjgl3): zrzuty Varden, Frostholm, Selvaran, Qasirah, obozu.

### Architektura (dziala, zachowac)
- `shared` - cala logika (swiat, ruch, walka, potwory, czary, ekwipunek, ekonomia, NPC, zadania, zapis, protokol).
- `core` - klient libGDX (WorldRenderer, LightRenderer, Hud 2.8 tys. linii, GameScreen 2 tys. linii).
- `server` - autorytatywny serwer TCP (ServerWorld uzywa `shared`), konta, TLS, PvP.
- `desktop`/`android` - launchery. Mapy: pliki ASCII `maps/world/floor_<z>.txt` + legend.csv, generowane
  przez `tools/mapgen/generate_world.py` (deterministycznie). Pietra -5..+5, przejscia w transitions.csv.
- Dane: data/*.csv (120 przedmiotow, 42 potwory, 36 czarow/run, 46 zadan, 240 pozycji sklepow).

### Kompletne (zweryfikowane testami lub zrzutem)
- Ruch 8 kierunkow z kolizjami, pathfinding, pietra i schody, swiatlo i pora dnia, pogoda.
- Walka wrecz/dystans/magia, odpornosci, smierc i odrodzenie, exp, lupy z autolootem, ciala potworow.
- Plecak 20 slotow (atomowe operacje), ekwipunek z wymaganiami, monety gp/plat/crystal z wymiana i reszta.
- Bank (konto wspolne miast), depozyt (zapis w postaci), rynek, handel graczy, poczta, przelewy.
- Czary (36), runy z ladunkami, mikstury, jedzenie, crafting, lowienie, pupile, wierzchowiec, blogoslawienstwa.
- NPC (26) z dialogami, zadania i lancuchy zadan, osiagniecia, ranking, gildie, druzyny.
- Serwer TCP z kontami, PvP, czaszkami, kopiami zapasowymi.

### Braki wzgledem master promptu (do kolejnych etapow)
1. **Swiat (etapy 4-9)**: mapa 256x232 to cztery male miasta w poziomych pasach biomow z losowym szumem;
   brak wybrzezy, morza, wysp, gor/kanionow jako ksztaltu terenu. Frostholm i Qasirah to te same,
   male uklady co Varden (identyczna swiatynia) - wymog "kazde miasto inne" niespelniony.
   Brak miasta portowego i gorskiego (sa: Varden krolewskie, Frostholm sniezne, Selvaran dzungla, Qasirah pustynia).
2. **Statki (etap 7)**: brak portow i statkow; podroz miedzy miastami tylko "woznica" (teleport za monety).
3. **Domki (etap 6)**: brak systemu domow (sa tylko budynki bez wlasciciela).
4. **Start 300 (etap 10)**: zachodni kontynent (poziomy 8-230) jest dla postaci 300 za slaby; pierwsze
   expowiska trzeba przestawic na 300+.
5. **Czary (etap 13)**: brak czarow szybkosci (utani hur, utani gran hur) i inkantacji w stylu Tibii
   (nazwy sa tylko polskie). Szybkosc = poziom + wierzchowiec, bez buffow.
6. **Plecaki (etap 14)**: jeden plecak; brak wielu plecakow (pojemnikow w pojemnikach) i przeciagania
   miedzy slotami (w Hud tylko szczatkowe "drag").
7. **Grafika (etap 2)**: sprite postaci z OpenTibia Sprite Pack sa po imporcie pochylone/scięte
   (postacie "leza" po skosie) i stroj mnicha ma czapke mikolaja - do poprawy w tools/art/otsp_import.py.
   Brakujace grafiki (TODO.md): smoki, golemy, schody, sciany lodu i dzungli, kon, skrzynie.
8. **UI (etap 16)**: HUD w stylu OTClient jest, ale bez zakladek czatu i okna wielu plecakow.
9. **Wydajnosc (etap 18)**: brak pomiaru na prawdziwym telefonie (wymaga urzadzenia Darka).

### Wykonane w etapie 1
- Branch `development` utworzony z main 0.196.0 (main nietkniety = zabezpieczona dzialajaca wersja).
- `data/starting.properties`: start.level=300 (takze online), start.gold.bank=10000000 (kieszonkowe 10 000 gp w plecaku zostaje).
- `StartingConfig.loadDefault()` przyjmuje `-Dpixel.start.level`/`-Dpixel.start.gold.bank`; testy mechaniki
  dalej licza od poziomu 8 (build.gradle), a `StartingConfigFileTest` pilnuje 300 i 10 mln w dostarczonej konfiguracji.
- `tools/mapgen/render_overview.py` - podglad calego pietra mapy do PNG (wizualizacja do raportow).

## Etap 2 - grafika (2026-10-22)
- `core/.../render/GroundBorders.java`: obrzeza podloza jak w Tibii (priorytety: woda < lod/lawa < bagno < jaskinia
  < popiol < ziemia < ciemny piasek < piasek < bruk < pole < trawa < trawa dzungli < snieg); 8 kawalkow na podloze
  skladanych przy starcie z tekstury i maski; rysowane w WorldRenderer.drawBorders po podlozu, przed dekoracja.
- `tools/art/otsp_import.py`: TINTED - 19 potworow z przebarwionych strojow OTSP; mnich = stroj 26; schody, drabina,
  lada, oltarz, pochodnia; mury ice_wall i obsidian_wall; cave_wall = przyciemniony kamien (lita skala).
  Paczka OTSP: `git clone https://github.com/peonso/opentibia_sprite_pack` i
  `python3 -I tools/art/otsp_import.py <paczka>/client_files /tmp/art`, potem kopiowac tylko zmienione pliki.
- Poprawka: CharacterFactory.BASE_LEVEL = 8 - umiejetnosci "pod poziom" od 9 w gore (wczesniej porownanie z
  start.level, ktore po zmianie na 300 wylaczalo umiejetnosci postaci serwerowych).
- Uwaga: ukosna sylwetka postaci to styl samej paczki OTSP (perspektywa Tibii), nie blad importu.
- Testy 423 OK, regresja GUI zaktualizowana (east_camp, selvaran zmienione przez obrzeza).
- Brakuje nadal: palma, kaktus, fontanna, latarnia uliczna, plot, dywan, magma/lawa z OTSP (zostaja z kodu).

## Etap 3 - system mapy (2026-10-22)
- `FloorMap` w kawalkach 32x32 tworzonych leniwie (puste morze i podziemia bez pamieci; 6,4 MB -> 2,4 MB),
  `lightSources(x0,y0,x1,y1)` po kawalkach. Format plikow map bez zmian.
- `Reachability` (BFS po polach, przejsciach i opcjonalnych polaczeniach statkow) + `WorldConnectivityTest`:
  kazda swiatynia, NPC (w zasiegu rozmowy), spawn i przejscie osiagalne ze swiatyni startowej.

## Etap 4 - geografia (2026-10-22)
- Mapa 352x320; stare kontynenty zachowuja wspolrzedne. `tools/mapgen/world_geo.py`: poszarpane wybrzeza, plaze,
  morze na poludniu i wschodzie, 7 wysp, faliste granice biomow, masywy gorskie z przeleczami, lasy, 3 rzeki z mostami.
- Nowe kafelki: mountain, snow_mountain, sand_cliff (nieprzechodnie skaly), shells.
- Generator nie nadpisuje recznie poprawianego `npcs.csv` (tworzy go tylko, gdy brak pliku).

## Etap 5 - cztery miasta (2026-10-22)
- `tools/mapgen/cities.py` (wywolywany z generate_world.py; wlasne ziarna, wiec reszta swiata bez zmian):
  - Frostholm (gorskie): dolne miasto u stop skaly (swiatynia 79,29, bank z lada i skarbcem, depozyt, sklep,
    tawerna, 4 domy), tarasy na +1 (ratusz jarla, kuznia, biblioteka, domy) po schodach (66,24) i (93,24),
    przepasc z potokiem i mostem (80..81,17..18,+1), kopalnia (70,18) -> -1 z krysztalami (region Kopalnia Frostholm).
  - Qasirah (pustynia + dzungla): ogrody z kanalem, mostami i kapliczka dzungli, palac z tronem, oaza z palmami,
    swiatynia Slonca (67,197), bazar z 6 straganami, bank, depozyt, 3 domy, ruiny; piramida z blokow (+1..+3)
    nad wejsciem do grobowcow (124,206) z korytarzem i posagami.
  - Selvaran (portowe): port na poludnie od murow (y 160..181): nabrzeze z latarniami, 3 mola, 3 statki przy molach
    (burty, poklad, armaty, ladunek, latarnia), statek na redzie, 2 magazyny, kapitanat, targ rybny, stocznia
    z kadlubem w budowie, kanal z mostem; basen polaczony z ciesnina. Strefa ochronna obejmuje port i mola.
  - Varden (krolewskie): rdzen z maps/varden bez zmian + dzielnica krolewska (x 114..156, y 66..104): zamek
    (sala tronowa, skarbiec, dziedziniec z fontanna), rynek ze straganami, biblioteka, gildia magow, kuznia,
    tawerna, 4 bogate domy z ogrodami, 4 wieze straznicze, bramy z armatami.
- Nowe obiekty z OTSP: bed, bookcase, barrel, table, statue, sand_statue, jungle_statue, cannon, chest, lumber,
  pyramid (sciana-bryla). Symbole legendy: cyrylica w cities.LEGEND.
- NPC Frostholm i Qasirah przeniesione do nowych budynkow (npcs.csv, polskie znaki zachowane).
- Krokodyle z dawnego bagna przeniesione (port jest strefa ochronna).
- Testy 426 OK, regresja GUI: zmieniony tylko wzorzec chest (skrzynia skarbow trafila do nowej kopalni).
- Debug generatora: `MAPGEN_DUMP=plik python3 tools/mapgen/generate_world.py` zapisuje pietro 0 przed kontrola dostepnosci.

## Etap 6 - depozyty, banki, swiatynie, sklepy, domy (2026-10-22)
- Szafki depozytu (`depot_locker`, OTSP 1113) w kazdym miescie: Varden (dzielnica krolewska, 8 szafek),
  Frostholm (6), Qasirah (6), Selvaran (4). Szafka otwiera ten sam wspolny depozyt (60 miejsc) co bankier;
  `GameSession.npcNearby()` zwraca stalego syntetycznego NPC na pozycje szafki, nazwa miasta z najblizszego NPC.
- Banki i swiatynie: bankier i kaplan w kazdym z 4 miast (sprawdza `TownServicesTest`: kazda rola NPC w kazdym
  miescie + osiagalne miejsce rozmowy, rowniez na pietrach przez schody).
- Sklepy: nowi NPC (14) - kowale/platnerze, karczmarze, magowie, handlarz ryb i agenci domow; nowe sklepy
  `*_arms`, `*_magic`, `*_supplies` (te same ceny co istniejace sklepy, bez zmian w ekonomii).
- Domy: `maps/world/houses.csv` (16 domow, generowane przez cities.house), `house/HouseRegistry` (walidacja:
  miasto, drzwi, brak nakladania), wlasciciele `HouseOwnersMap` zapisywani przez serwer w `save/houses.txt`
  (zapis atomowy). Kupno u agenta domow w tym samym miescie (`npc:housebuy:<id>`), cena 5000 gp za pole
  (Varden x2), platne z banku; jeden dom na postac; `npc:houseleave` oddaje dom (bez zwrotu). Obcy nie wejda do
  srodka; po utracie domu gracz przenoszony pod drzwi. Pole `house` w zapisie postaci.
- Nowe testy: HouseSystemTest, DepotLockerTest. Testy 432 OK, regresja GUI bez zmian.
- Czynsz za domy nie jest pobierany (decyzja: brak okresowych oplat, zeby nie zmieniac ekonomii bez zgody).

## Etap 7 - mosty, statki i podroze (2026-10-22)
- Nowa rola NPC `CAPTAIN` (kapitan): rejsy w obie strony z `maps/world/ships.csv` (10 tras), cena jak u woznicy,
  monety z plecaka, bez rejsu w blokadzie po walce z graczem. Panel NPC: "Plyn: <cel>" z cena.
- `tools/mapgen/ports.py`: przystan w zatoce Qasirah (nowa brama poludniowa i droga), Przystan Wschodnia na
  Spalonych Wyzynach, przystanie na 6 wyspach (pomost, molo, lodz, strefa ochronna); kapitan w porcie Selvaran.
- 8 kapitanow (npcs.csv, grafika OTSP). Wyspy sa osiagalne tylko statkiem albo mostem.
- Mosty: Most Latarni (14 pol) na Wyspe Latarni i Most Mewi na Skaly Mewie.
- Kontrola swiata: `Travel.links()` daje `Reachability.Link` dla rejsow (WorldConnectivityTest), test uslug miast
  plywa statkami; generator sprawdza pomosty, mosty i polaczenie przystani z wnetrzem wysp.
- Nowy test ShipTravelTest (trasy w obie strony, bledne ships.csv, rejs na wyspe i powrot). Testy 435 OK,
  regresja GUI bez zmian.
- Wyspy nie maja jeszcze potworow - expowiska na wyspach w etapie 8.

## Etap 8 - expowiska na powierzchni (2026-10-22)
- 7 nowych potworow 300-690 (monsters.csv, loot.csv, grafika OTSP przebarwiona): wiwerna, krab koralowy,
  topielec (nocny), pirat rzezimieszek, pirat kanonier (dystans), upior mgly, smok magmy.
- Expowiska na wszystkich 7 wyspach (dojscie statkiem z etapu 7 lub mostem): Koralowa 300-380, Latarni i Skaly
  Mewie 300-340, Piratow 320-420, Lodowa 420-560, Mgiel 480-650, Wulkaniczna 600-800; nocne spawny topielcow
  i lowcow cieni.
- Zachodni kontynent: Dolina Wiwern wykuta w Smoczym Grzbiecie (region 300-360) z jednym wejsciem i straznikiem
  od poziomu 250 (gates), zeby postacie testowe poziomu 8 nie wchodzily w endgame; wrak statku u brzegu SW.
- Testy: GameWorldTest liczy dostepnosc razem z rejsami; nowy test expowisk 300+ (kazda wyspa i dolina ma
  potwory, straznik doliny). Testy 436 OK, regresja GUI 42/42.
- Do poprawy w etapie 10: grafika kraba (przebarwiony skorpion wyglada jak wij).

## Etap 9 - wielopoziomowe lochy (2026-10-22)
- `tools/mapgen/depths.py`: 5 nowych lochow 300+ (wlasne ziarna, reszta swiata bez zmian): Grota Przemytnikow
  (Wyspa Piratow, -1..-3), Serce Wulkanu (Wyspa Wulkaniczna, -1..-4, magma), Lodowe Groty (Lodowa Wyspa, -1..-3),
  Krypta Mgiel (Wyspa Mgiel, -1..-3), Gniazdo Wiwern (pod Dolina Wiwern, -1..-2, region 300-360).
- Pietra lacza schody, a w lochach wysp miedzy glebszymi pietrami dziura i drabina. Wejscia na powierzchni maja
  nowe symbole schodow (popiol, przeklete trawy, trawa).
- Lacznie swiat ma lochy: Kopalnia Varden, Lodowe Jaskinie, Zatopione Ruiny, Grobowce Qasirah i Otchlan (-1..-5),
  Kanaly Selvaran (-1..-2), Kopalnia Frostholm (-1) oraz 5 nowych.
- Nowy test: kazdy nowy loch ma osiagalne pietra do najglebszego i potwory na najglebszym; co najmniej 4 dziury
  w lochach 300+. Testy 437 OK, regresja GUI: zmieniony tylko wzorzec chest (inna pierwsza skrzynia skarbow).

## Etap 10 - potwory, bossowie, loot (2026-10-22)
- 5 bossow na dnie lochow z etapu 9 (monsters.csv, loot.csv, grafika OTSP przebarwiona): Matka Wiwern 360,
  Kapitan Krwawa Broda 420, Skaldra 540, Morvena 640, Pyrrax 780; respawn 3600 s, sami w spawnie.
- Nowy zestaw przedmiotow poziomu 350 (luka miedzy obsydianem 250 a lodem 450): bron dla kazdej profesji
  (harpun, rozdzka, laska, rekawice) i helm/zbroja/nagolenniki/tarcza; ikony z OTSP. Wypada z bossow (20-30%)
  i rzadko z potworow wysp.
- Grafika kraba koralowego poprawiona (stroj OTSP 24 zamiast skorpiona). docs/GRAPHICS.md uzupelnione o grafiki
  etapow 7, 8 i 10.
- Regresja GUI: wzorzec chest z etapu 9 byl skopiowany w zlym formacie (pelny rozmiar) - odtworzony przez
  run.sh --update. Testy 439 OK (nowy DungeonBossTest), regresja GUI 42/42.

## Etap 11 - profesje i poziomy (2026-10-22)
- Przeglad: HP/mana/udzwig na poziom zgodne z Tibia dla paladyna (10/15/20) i magow (5/30/10); monk ma wlasny
  profil (13/6/22, najszybsze ataki). Krzywa doswiadczenia jak w Tibii (szescienna), poziom maks. 1000.
  Umiejetnosci startowe na poziomie 300 dobierane do profesji (bron ~91, magia magow 70, obrona ~82) - bez zmian.
- Nowe: wyposazenie startowe zalezne od poziomu. Postac zaczynajaca na 300 dostaje zestaw obsydianowy (poziom 250)
  z bronia swojej profesji i mocniejsze mikstury/runy zamiast skory z poziomu 8. Start 300 / 10 000 000 gp w banku
  bez zmian.
- docs/GAME_BALANCE.md: poprawiona nieaktualna tabela startu (pokazywala poziom 8 i 0 gp w banku) + tabela zestawow.
- Braki przeniesione dalej: czary konczace sie na poziomie 100 i brak utani hur / utani gran hur (etapy 12-13),
  szybkosc 8 kafelkow/s na poziomie 300 do przegladu w etapie 12.
- Testy 441 OK (nowy VeteranStarterKitTest), regresja GUI 42/42.

## Etap 12 - ruch i walka (2026-10-22)
- Nowe czary przyspieszenia: utani hur ("Przyspieszenie", kazda profesja od poz. 14, 60 many, +30% szybkosci
  na 33 s) i utani gran hur ("Silne przyspieszenie", druid i sorcerer od poz. 20, 100 many, +70% na 22 s).
  Czas trwania odlicza sie z czasem gry, po wygasnieciu szybkosc wraca; nowy czar zastepuje poprzedni.
- Przeglad szybkosci 300+: wczesniej poziom 300 juz siedzial na limicie 8 pol/s, wiec przyspieszenie nic by nie
  dalo. Krzywa powyzej szybkosci 400 jest teraz o polowe lagodniejsza, limit 10 pol/s: poziom 300 = 6.6 pola/s,
  z utani hur 7.7, z utani gran hur 9.1. Niskie poziomy (do ok. 60) bez zmian; potwory (szybkosc <= 300) bez zmian.
- Kolizje: na serwerze gracze blokuja sobie pola poza strefa ochronna (w PZ mozna przejsc), potwory nie wchodza
  na pola zadnego gracza (wczesniej tylko celu). Klient online poprawia przewidziany krok wg serwera.
- HUD: ikona stanu przyspieszenia, kolor i znak czaru na pasku, automatyczny pasek podstawia przyspieszenie.
- Testy 447 OK (nowe HasteTest i PlayerCollisionTest), regresja GUI 42/42.

## Etap 13 - czary, runy, mikstury (2026-10-22)
- 15 nowych czarow na poziomy 120-300: sorcerer (Ostateczny grom 150, Piekielny rdzen 200, Gniew niebios 300),
  druid (Ostateczny cios ziemi 150, Wieczna zima 200, Gniew natury 300), paladyn (Boska kaldera 150, Silna
  eteryczna wlocznia 200, Boski sad 300), mnich (Piesc burzy 150, Fala chi 200, Tysiac dloni 300) oraz leczenie
  (Wielkie uzdrowienie magow 150, Wielkie swiete uzdrowienie paladyna 120, Harmonia ducha mnicha 150).
- Inkantacje w stylu Tibii dla wszystkich 54 czarow (data/incantations.csv): wpisanie np. "utani gran hur" albo
  "exevo gran mas flam" w czacie rzuca czar; ksiega pokazuje slowa.
- 4 nowe runy (wielka kula ognia, lawina, nagla smierc, ostateczne leczenie) z czarami zaklinania i 4 nowe
  mikstury (silna i ostateczna mikstura ducha, ostateczna mikstura many i zdrowia) w sklepach magow 4 miast i w obozie.
  Grafiki run i mikstur rysowane proceduralnie wg koloru (jak dotychczasowe runy), bez nowych plikow graficznych.
- Zestaw startowy postaci 300 zawiera nowe mikstury i runy; przyciski HP/MP biora najsilniejsza miksture.
- Wywazenie: leczenie 120-150 oslabione wzgledem pierwszego projektu, bo EndgameSimulationTest wykazal, ze
  postac z progu nizej przeskakiwala prog (paladyn, sorcerer, mnich vs void_priest).
- Znane ograniczenie (naprawione w etapie 16): ksiega miescila ok. 10 czarow.
- Testy 453 OK (nowy HighLevelMagicTest), regresja GUI 42/42.

## Etap 14 - ekwipunek, plecaki, monety (2026-10-22)
- Plecak glowny 20 pol bez zmian. Nowy przedmiot "Plecak" (20 gp u zaopatrzeniowcow 4 miast i w obozie): uzyty
  dolacza kolejna strone 20 pol, najwyzej 3 dodatkowe (80 pol). Strony przelacza sie zakladkami 1-4 obok pol.
  Ostatni plecak mozna odpiac (wraca jako przedmiot), gdy jest pusty. Postac startujaca na 300 ma 2 plecaki (40 pol).
- Przeciaganie przedmiotow: na inne pole, na zakladke innego plecaka albo na panel ekwipunku (zalozenie).
- Monety: uzycie 100 sztuk wymienia na wyzszy nominal (100 gp = 1 platinum, 100 platinum = 1 crystal), uzycie
  pojedynczej wyzszej monety rozmienia; SORTUJ wymienia pelne setki sam.
- Zapis i synchronizacja online: liczba plecakow w zapisie (`bags`), klient dostaje ja z serwera razem z postacia.
- Grafika: plecak z OTSP (2099). Poprawiony blad: perla miala te sama grafike co platynowa moneta (teraz OTSP 2197).
- Testy 458 OK (nowy MultiBackpackTest), regresja GUI 42/42. Przeciagania nie sprawdza zaden test automatyczny
  (wymaga gestu na ekranie) - sprawdzone tylko kompilacja i logika ruchu miedzy stronami.

## Etap 15 - NPC, questy, ekonomia (2026-10-22)
- Przeglad: 4 miasta maja bankierow, kaplanow, agentow domow, przewoznikow, kupcow, przewodnikow z zadaniami
  (lacznie 46 zadan do poziomu 900) i 8 kapitanow. Brakowalo tresci dla nowych wysp z etapow 7-10.
- Nowi NPC w Przystani Wschodniej: Admiral Wenda (15 zlecen 300+ na wyspy i 5 bossow lochow, 4 lancuchy z koralowym
  ekwipunkiem i krysztalowymi monetami) oraz Kwatermistrz Olaf (mikstury 250+, plecaki, skup lupow z wysp).
- Ekonomia: 7 nowych produktow potworow (luska wiwerny 400 gp ... rog magmowego smoka 1500 gp) skupowanych po
  wartosci; koralowy ekwipunek tez mozna sprzedac. Grafiki produktow z OTSP.
- Rozmowy kapitanow o rejsach, wyspach i piratach; Admiral i kwatermistrz odpowiadaja o bossach, koralu i skupie.
- Testy 460 OK (nowy IslandQuestsTest), regresja GUI 42/42.

## Etap 16 - UI w stylu OTClient V8 (2026-10-22)
- Przeglad: HUD od wczesniejszych wersji korzysta z grafik OTClientV8 (MIT: male okna, pola przedmiotow, ramki),
  ma panel ekwipunku, umiejetnosci, liste walki, minimape i pasek akcji. Brakowalo zakladek konsoli
  i okna wielu plecakow (zrobione w etapie 14) oraz ksiegi mieszczacej czary 300+.
- Konsola: zakladki Ogolne / Czat / Lup / Serwer. Nowy rodzaj wpisu CHAT dla rozmow graczy, wiadomosci prywatnych,
  gildii, druzyny, odpowiedzi NPC i inkantacji.
- Ksiega czarow: stronicowana, domyslnie z najwyzszymi poznanymi czarami, "Kolejne czary" przewija - na poziomie
  300 widac wszystkie 19-21 czarow profesji (usuniete ograniczenie z etapu 13).
- Wzorce regresji GUI odswiezone przez run.sh --update (pasek zakladek w kazdej scenie), 42/42.
- Testy 460 OK.

## Etap 17 - sterowanie mobilne (2026-10-22)
- Przeglad: byl joystick w rogu (8 kierunkow), przyciski dolnego paska, pasek akcji z dlugim dotknieciem
  (wybor czaru), dotkniecie pola (cel, marsz, NPC, lup), przeciaganie przedmiotow (etap 14), Wstecz zamyka okna.
- Gesty na mapie rozstrzygane przy puszczeniu palca: krotkie dotkniecie = pole, przytrzymanie = podglad pola
  ("Widzisz: Wilk (poziom 8, zdrowie 50%). Odleglosc: 4 pola."), przeciagniecie po lewej czesci mapy =
  joystick pod kciukiem. Prawy przycisk myszy na PC = podglad.
- Ustawienia: joystick pod kciukiem / tylko w rogu, rozmiar joysticka 80/100/125/150% (konsola przesuwa sie).
- Naprawiony wiersz "Wibracje" w ustawieniach (nie przelaczal sie - brakowalo go w obsludze przyciskow).
- Android: ekran nie gasnie w trakcie gry.
- Testy 463 OK (nowy LookAtTest, test ustawien joysticka), regresja GUI 42/42 bez zmian wzorcow. Gesty
  dotykowe nie maja testu automatycznego (brak emulacji dotyku w chmurze) - sprawdzone kompilacja, zrzut
  z -Dpixel.joy i -Dpixel.look oraz logika w testach.

## Etap 18 - optymalizacja (2026-10-22)
- Pomiar: JFR (profil CPU) i libGDX GLProfiler. Logika jest tania: 421 potworow ze 201 spawnow, GameSession.update
  ok. 0,01 ms. Waskim gardlem byl GPU: 251 wywolan rysowania i 245 wiazan tekstur na klatke (noc w Varden, deszcz),
  bo podloze, obrzeza i dekoracje przelaczaly atlasy na kazdym polu.
- Podloze, obrzeza i dekoracje rysowane osobnymi przebiegami: 251 -> 109 wywolan na klatke (swiat 201 -> 59),
  wiazania tekstur 245 -> 103. Obraz bez zmian (regresja GUI 42/42, roznice ponizej 0,7% to szum rasteryzacji).
- HUD: pasek akcji przeliczany co 150 ms zamiast co klatke; nazwy potworow zadan bez kopiowania listy spawnow co klatke.
- Narzedzia: liczniki rysowania w panelu diagnostyki, -Dpixel.profile=true wypisuje srednie co 300 klatek.
- Czas klatki CPU w chmurze (renderowanie programowe Mesa) pozostaje ok. 14 ms - tam ogranicza wypelnianie pikseli,
  nie liczba wywolan; na telefonie mniej wywolan rysowania przeklada sie bezposrednio na krotsza klatke.
- Testy 463 OK.

## Etap 19 - testy koncowe (2026-10-22)
- Nowy test odbiorczy briefu `acceptance/BriefAcceptanceTest` na prawdziwych danych: kazda profesja startuje
  na poziomie 300 z 10 000 000 gp w banku i dwoma plecakami (40 miejsc); "utani hur" rozpoznawane i rzucane przez
  kazda profesje, "utani gran hur" to strong_haste; 4 miasta, kazde z kaplanem w strefie ochronnej, bankiem, sklepami
  i transportem; co najmniej 4 kapitanow; co najmniej 3 pietra poza powierzchnia, ponad 100 spawnow na 3+ pietrach
  i co najmniej 5 bossow w swiecie.
- Regresja GUI: 2 nowe sceny (diag - panel diagnostyki z ustawieniami, look - podglad pola), lacznie 44/44.
- Poprawki znalezione w testach: panel diagnostyki nie przeswituje juz przez minimape, ekwipunek i przyciski
  kolumny (wczesniej nakladaly sie napisy), krotszy wiersz licznikow rysowania (nie wychodzi poza ekran),
  poprawna odmiana "Odleglosc: 3 pola" / "5 pol".
- Przeglad nocy: w miastach latarnie i pochodnie oswietlaja ulice (zamierzone), poza miastem noc jest ciemna.
- Testy 467 OK, CI zielone dla etapow 16-18.

## Etap 20 - ZIP i debug APK (2026-10-22)
- Opis wydania: docs/DEVELOPMENT_RELEASE.md (zawartosc etapow, pobranie, kompilacja, znane ograniczenia).
- ZIP z kodem: `git archive` commita tego etapu (bez build/, bez sekretow; .gitignore wyklucza local.properties).
- Debug APK: budowany przez CI na pushu i publikowany w wydaniu `development-apk`
  (https://github.com/fawizja/Pixel/releases/tag/development-apk); lokalnie brak Android SDK.
- main (0ba79e6) jest w pelni wciagniety do development.

## Rozbudowa mapy, miast i interfejsu (brief z 2026-10-23)
- Grafika (29fbba4): okna w scianach (kamien, piaskowiec, tynk, biel, mur pruski) rysowane co 3 pola na scianach
  budynkow; nowe mury tynk / bielona sciana / mur pruski; plyty, cegla, marmur; 9 gatunkow drzew, krzaki, kwiaty
  i kamienie losowane po pozycji, trawa i piasek odbijane; meble i rekwizyty OTSP (krzeslo, szafa, studnia, lawka...)
  oraz wlasne (kowadlo, palenisko, stragan, drogowskaz, bela siana, lodka, sieci, skrzynka z kwiatami).
- Varden od nowa (6012021, tools/mapgen/generate_varden.py + town.py): mury lamana linia z basztami i 3 bramami,
  wschodni bok zamyka meandrujaca rzeka z drewnianym nabrzezem i pomostem, jezioro z plaza i wysepka, plac
  swiatynny z plyt z jedna fontanna, rynek z cegly ze straganami i studnia, swiatynia z apsyda i marmurem, bank
  z skarbcem, sklep z magazynem, dom rybaka, kuznia, karczma, dom mieszkalny, sklep magiczny z biblioteka; ogrody
  zamiast pustego bruku; nieregularny las. Dachy wg muru: lupek (kamien), gont (mur pruski), braz (tynk).
- Pozostale miasta (d4acbcb, tools/mapgen/dressing.py): wnetrza wyposazone wg roli NPC (bank, sklep, swiatynia,
  przewodnik, woznica, kapitan) z kontrola, ze kazde pole wnetrza jest osiagalne od drzwi; drewniane budynki
  dostaja rozne mury; place: latarnie, lawki, skrzynki z kwiatami, drzewa w ogrodach; fontanny 2x2 -> 1.
- Teren (1e5df22): dlugie drogi wija sie (konce bez zmian), cieśnina ma poszarpane brzegi, region Varden
  bez lesnej ramki.
- Interfejs: imiona NPC i graczy nie sa pokazywane pod widocznym dachem i nie wchodza na gorny pasek; kolumna
  przyciskow przy minimapie min. 30 dp, dolny pasek 32-34 dp. Sprawdzone 800x600, 1280x720, 1600x720,
  720x1280 i uklad telefonu (-Dpixel.uiScale=1.8).
- Testy 467 OK; regresja GUI: 38 z 44 wzorcow odswiezonych (nowa mapa), 6 bez zmian.
- Znane ograniczenia: granice pasow biomow (snieg/trawa/piasek) i prostokatne ramy lasu wokol regionow
  pozostaja proste; Frostholm, Selvaran i Qasirah maja dalej prostokatne obrysy (dopracowane wnetrzami
  i wyposazeniem, nie przebudowane); brak animowanej wody i nowych animacji postaci.

## Kontynent ze szkicu Darka (brief z 2026-10-23, 06:53)
- Mapa (tools/mapgen/generate_continent.py, continent.py, features.py): swiat 448x448 o ksztalcie ze szkicu.
  Stary swiat 352x320 powstaje jako etap posredni (generate_world.py -> tools/mapgen/build/legacy) i jego
  miasta, wyspy i wejscia do lochow sa przenoszone w calosci (klocki BLOCKS); Varden pole w pole z maps/varden.
  Snieg i lod na polnocnym zachodzie (Frostholm), Qasirah na wzgorzach w srodku z pustynia, Selvaran w dzungli
  na polnocnym wschodzie, lasy i doliny na poludniu (Varden). Pasma gorskie z przeleczami, rzeki z delta,
  jeziora, 5 nowych wysp, drogi miedzy miastami, mosty i kladki nad rzekami, most wiszacy nad rozpadlina.
- Wulkan: stozek z potokami lawy, polka i krater z lawa (pietra +1/+2, boss Ignaroth), Wnetrze Wulkanu
  (3 pietra pod ziemia, zywiolaki ognia, golemy lawy, smoki magmy).
- 12 wiosek: 2 zywe (kupcy), 2 opuszczone, 8 zajetych przez potwory (orki x3 z wodzem Gruzhakiem, gobliny,
  bandyci, nieumarli, trolle, lowcy z krokodylami); 5 jaskin z potworami w gorach; 6 malych przystani.
- Porty: duze statki z kapitanem na pokladzie (Varden, Qasirah, Przystan Wschodnia); przewoznicy na Mewiej
  Skale i Latarni. Rejsy statkiem tylko z kontem premium.
- Krol Aurelian w sali tronowej Varden: konto premium (500 000 gp z banku) i promocja (1 000 000 gp, premium,
  od poziomu 20): nazwa profesji jak w Tibii, zlota gwiazda nad postacia i ikona stanu, regeneracja +50%.
  Zapis v4 przechowuje premium i promocje; zapisy v3 przenosza postac razem z miastem albo do swiatyni.
- Straznicy (7, losowe imiona) przy bramach miast; swiece i czerwony chodnik w swiatyniach, swieczniki przy
  tronie, 3 wiatraki (grafika 2x2 pola), wiecej drzew, krzewow i kwiatow w miastach.
- Klient: slady stop na sniegu (gracz, inni gracze, potwory; znikaja po 40 s), towary kupcow na ladach obok
  nich (zlota moneta = skupuje). Wlasne grafiki: swiecznik, chodnik, tron, ster, maszt, poklad, most wiszacy,
  rozpadlina, ruiny muru, namiot orkow, wiatrak; orki z przebarwionych strojow OTSP, krol i straznicy OTSP.
- Testy 472 OK (testy z wspolrzednymi starego swiata przepisane); regresja GUI: 50 wzorcow (6 nowych scen).
- Znane ograniczenia: Qasirah juz mial dywan w swiatyni (dodane tylko swiece); 3 zamiast 4 wiatrakow (czwarte
  miejsce kolidowalo z rdzeniem Varden); znak promocji widac nad wlasna postacia i w ikonach stanu, inni gracze
  online go nie widza (protokol nie przesyla flagi); orki to przebarwione stroje (paczka OTSP nie ma orkow).

## Mniej sztucznie: skaly, walka, pory dnia, efekty (brief z 2026-10-23, 17:04)
- Grafika: 97 grafik OTSP zamiast wlasnych (647122d), podloza z autokafelkow WsT (d35c92a). Skaly (gory,
  jaskinie, klify, piramidy) maja zaokraglone rogi i nierowne brzegi tam, gdzie nie stykaja sie z inna skala;
  zbocze ma ten sam ksztalt (TileArt.loadRockShapes, WorldRenderer.rockMask).
- Dzwiek domyslnie wylaczony (ustawienie zapisywane pod nowym kluczem "sound").
- Walka: postac atakuje tylko potwora zaznaczonego przez gracza; usunieto samo przelaczanie celu i samo
  odpowiadanie na atak (takze po trafieniu czarem obszarowym).
- Potwory: pole widzenia co najmniej 7 pol (wiecej w nocy), pelny zasieg przed soba i polowa za soba, zawsze
  zauwazaja gracza w promieniu 2 pol; same biegna do gracza, a gdy zniknie z oczu, ida przez 5 s tam, gdzie go
  widzialy; stojac rozgladaja sie.
- Czas: doba gry trwa 2 godziny: 1 godzina dnia i 1 godzina nocy (switt i zmierzch po 5 min). Klimat zmienia
  sie z kazdym dniem gry i regionem (slonecznie / zmiennie / deszczowo), komunikat "Pogoda na dzis".
- Efekty czarow i pociskow z paczki OTSP (assets/art/effects, assets/art/missiles): paladyn strzela strzalami
  (wlocznia przy wloczni), potwory rzucaja kamieniami albo nozami, czary maja animacje ognia, lodu, ziemi,
  energii, swietosci i smierci; czary obszarowe rozchodza sie fala od srodka.
- Testy 476 OK; regresja GUI: wzorce odswiezone.

## Klimat z paczki OTSP, rycerz, ulice (brief z 2026-10-23, 21:08)
- Ulice: donice, kwiaty i lawki stojace na srodku ulicy przeniesione pod mur (tools/mapgen/sidewalk.py, takze
  w generate_varden.py); straznicy przy bramach stoja na poboczu obok przejscia. Tam, gdzie nie bylo miejsca
  pod murem, rzecz usunieto.
- Kamera blizej: widok 11x8 pol, calkowita skala piksela (bez szwow).
- Krew i plamy (OTSP) po trafieniach na 30 s, zwloki ludzi, goblinow i szkieletow.
- Gory i jaskinie z grafik OTSP (szczyty, zbocza od poludnia i wschodu), skala laczy sie tylko z ta sama skala;
  wiecej odmian drzew, skal i roslin, drzewa 64 px bez rozmycia (atlas bigTiles).
- Nowa profesja Rycerz (Knight, awans Elite Knight): miecze, topory i mloty (17 nowych broni 161-177 z OTSP),
  tarcze, 11 czarow (m.in. brutal strike, berserk, wound cleansing, annihilation, groundshaker), sklepy, loot,
  receptury, ranking. Start jak inni: poziom 300, 10 mln w banku.
- Testy 479 OK (KnightTest); regresja GUI: wzorce odswiezone.

## Kolejne grafiki z paczki OTSP (pytanie Darka z 2026-10-24, 05:16)
- Nowe kafle 139-142: cobweb (dekoracja w rogach jaskin), snowman, clay_pot, sack; generate_continent.atmosphere()
  wybiera pola wg pozycji (bez losowania), rdzen i podziemia Varden bez zmian.
- Warianty z paczki: krzesla, skrzynie, posagi, donice, swiece, sztandary, drewno, pajeczyny, balwany.
- Wlasne stroje z paczki: mist_wraith (5), coral_crab (31), morvena (41), morgath (2).
- Testy 481 OK; regresja GUI: wzorce odswiezone.

## Wszystko z paczki, co ma sens (brief Darka z 2026-10-24, 05:35)
- Krawedzie terenow: generate_continent.soften_edges() (przesuniecie o gladki szum + 2 przejscia wygladzania,
  tylko miedzy przechodnimi podlozami naturalnymi, bez miast i Varden).
- Dachy: Roofs.SNOW/ORANGE/ROYAL, kalenica i okap (objects/roof_*_top/_bottom.png) w WorldRenderer.renderAbove.
- Luki i kusze 178-190 (PALADIN, zasieg 5, bez amunicji: CombatFormulas.usesNoAmmo), sklepy i loot bossow; BowTest.
- Pociski arrow_red, bolt, star_small, fire_arrow, poison, shadow_star i efekty sparkle, magic, stones
  (GameScreen.missileFor/impactFor). Paczka ma tylko ok. 15 roznych efektow i 20 pociskow - teraz uzyte prawie wszystkie.

- Sterowanie (05:56 "popraw mechanike sterowania"): Direction.fromStick (klin skosu +-12 st.), slizg po murze w
  GameSession.update (dziala tez na serwerze), kolejka jednego kroku z keyDown w GameScreen (450 ms); ControlFeelTest.

## Przyciski przy ekwipunku, Bieg i Tarcza (Darek, 2026-10-24, 06:26)
- Hud.resize: panel max(150, min(210, 17% szerokosci)), minimapa na cala szerokosc, kolumna diag/run/defensive/torch
  obok ekwipunku (niski ekran: rzad pod minimapa i zwarty ekwipunek). layoutEquipPanel(top, bottom, maxW).
- Bieg: ChaseMode.wants + GameScreen.chase (trasa na pole obok celu, odswiezana co >=200 ms przy ruchu celu,
  joystick zawiesza pogon do zmiany celu). Szybszy krok biegu zostaje.
- Tarcza: Equipment.shieldDefense, CombatFormulas.DEFENSIVE_INCOMING_SHIELD = 0.6 + dodatkowy blok tarcza; RunShieldTest.

## Kupione paczki WsT: ikony mikstur i ramki pol (Darek, 2026-10-24, 06:45)
- tools/art/wst_icons_import.py (po otsp_import.py i wst_import.py): 23 ikony 16->32 px do art/items, 9 ramek 22 px do
  assets/ui/wst/. Surowe paczki w tools/art/wst/alchemy, tools/art/wst/ui_slots (gitignore) i pixel-wst-zrodla/*.zip.
- Mikstury 191-194 (Enchantments.Kind STRENGTH/STONESKIN/SWIFTNESS/LUCK, POTION_MS 10 min), GameSession.drinkBuff;
  Rarity.tier wg ceny -> Hud.slotFrame; PotionBuffTest.

## Drzwi, okna, skrzynie, meble, mury, panele WsT, czat (Darek, 2026-10-24, 07:32)
- world/Openings (wspolne klient/serwer): drzwi i okna zamkniete na starcie, GameSession.useAt (Actions "open:x:y:z"),
  MovementSystem otwiera drzwi przy wejsciu, MonsterManager.canOccupy omija zamkniete. Grafiki drzwi i okien
  otwartych z paczki (otsp_import DOORS, WINDOW_STATES), WorldRenderer.openness = animacja 220 ms. OpeningsTest.
- Skrzynie skarbow blokuja krok (GameSession.chestAt), tap = useOpening (z daleka walkTo + walkUse),
  native/chest_closed/open + przejscie 350 ms w GameScreen.drawChests.
- Grafiki w naturalnym rozmiarze: assets/art/native (plotno 64x64, prawy dolny = pole), TileArt.nativeTile.
  COMPOSITES (lozko z dwoch polowek), STITCHED (stol i oltarz 2x2 zszyte w jedno pole), obrazy na murach
  WALL_DECO_H/V (Openings.wallDecor, co czwarty prosty mur wnetrza).
- ALT_WALLS: odmiany murow walls/klucz_altN_ksztalt.png, WorldRenderer.buildingStyle wg spojnego fragmentu muru.
- Konsola: tylko czat (Hud conTab=1), reszta zdarzen 5 s na widoku gry (drawStatus).
- Panele WsT: wst_icons_import PANELS -> assets/ui/wst/panel_*.png, Hud.Tiled9 (kafelkowany 9-slice x2),
  Hud.menuStyle wybiera styl wg okna.
## Swiat 1000x1000 wg mapy Darka (2026-10-27, 20:13, wersja 0.206.0)
- Teren z obrazka: tools/mapgen/picture_terrain.py -> tools/mapgen/data/world1k.txt.gz, czytany w continent.py.
- Nowe miasta tools/mapgen/newtowns.py (Valmir, Nerys), kanaly i kratki tools/mapgen/underground.py
  (sewer_grate = STAIRS w dol, LADDER w gore), kopalnie = lochy stylu 'mine'. Town.MAX_TOWNS=6.
- WorldLayout.VARDEN_OFFSET=(467,483); stare wspolrzedne Varden 448 + (328,143). CharacterCodec v5: stare
  zapisy -> pozycja (-1,-1,0) -> GameSession stawia w swiatyni miasta.
- Otchlan Wulkanu: 5 pieter pod wulkanem (Otchlan -2..-1, Dno -5..-3). Openings.wallDecor wylaczony pod ziemia.
- Testy maja heap 1536m (kazdy serwer testowy laduje swiat ~15 MB).
## Wyglad swiata (2026-10-28, 06:27, wersja 0.207.0)
- tools/mapgen/nature.py: ponds (stawy przed drogami, zajmuja footprint), ravage (napadniete wioski), town_ornaments
  (ozdoby pod murami, bez zamykania przejsc i przy NPC), shop_displays -> maps/world/displays.csv (x;y;z;przedmiot).
- features.winding_path: drogi przez odsuniete punkty posrednie, A* 8-kierunkowy po szumie kosztu, skosy dopelnione.
- WorldMap.displays / MapLoader.readDisplays; WorldRenderer.drawDisplay rysuje przedmiot na ladzie. WorldLookTest.
- Grafiki: otsp_import CLIMATE (jungle_tree, frost_pine, burnt_tree, blood, corpse...), wst_import FLOORS (posadzki).
## Miasta, skrzynie, domy, kratki (2026-10-28, 07:10, wersja 0.208.0)
- tools/mapgen/organic.py: Town (falisty obrys, cienki mur thin_ring, bramy, plac, ulice A* przez punkty posrednie,
  dzialki wzdluz ulic, domy L -> houses.csv pole 12 "wings"), sklepy z lada; newtowns (Valmir, Nerys) i
  generate_continent.REBUILD/rebuild_towns (Qasirah, Frostholm, Selvaran, Varden-East). Stare Varden 1:1.
- Kratki: underground.sewers trzyma wszystkie odcinki >= 24 pol, town_undergrounds daje kratki kazdemu odcinkowi
  (spacing 9, 7-9 na miasto, nie przy NPC, 8 wolnych sasiadow).
- world/Crates + GameSession (pushCrate, useCrate, cratePut/Take), serwer crates.txt + CRATEMV/CRATE. CratesTest.
- Domy: HouseOwners goscie/transfer, ServerWorld.houseCommand, ChatCommands /dom, zwrot 50% u zarzadcy.
## Nastepne kroki (dokladnie)
Wszystkie 20 etapow zakonczone. Dalsze prace tylko na nowe polecenie (np. scalenie development do main
- wymaga zgody Darka).

## Narzedzia
- JDK 21 (kompilacja --release 17), Gradle 8.14.3 wrapper; w chmurze lokalny init.d z lustrem Maven Central
  (poza repo) gdy Maven Central zwraca 429.
- Zrzuty: Xvfb :99 + `desktop/build/install/desktop/bin/desktop` z `-Dpixel.autostart=Imie:PROFESJA
  -Dpixel.teleport=x,y,z -Dpixel.screenshot=plik.png`; regresja GUI `tools/gui-regression/run.sh`.
