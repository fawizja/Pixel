# Changelog

## [Unreleased]

### Dodano
- Plecak w plecaku jak w Tibii: każdy plecak (20/24/36 pól) można włożyć w pole innego plecaka i otworzyć, bez limitu głębokości; ograniczają
  tylko pola i udźwig (cap) postaci (`ItemWeights`, waga zawartości liczy się rekurencyjnie, `TOO_HEAVY`). Komputer: okno plecaka obok głównego
  z przyciskiem `<`; telefon: plecak otwiera się w miejscu panelu plecaka (nazwa w tytule, `< Wstecz`, "Wyjmij", przenoszenie dotknięciem,
  użycie mikstur/run/jedzenia wprost z zagnieżdżonego plecaka). Upuszczenie przedmiotu na plecak wkłada go do środka.
- Przepełnienie lupu: gdy główny plecak jest pełny, przedmioty (lup z ciała i autoloot, nagrody, kupno, zbieranie, podnoszenie) trafiają do
  pierwszego plecaka z miejscem (w głąb); stosy łączą się najpierw z istniejącymi.
- Zapis postaci v10 niesie zawartość plecaków (`{rozmiar|pole=stos|...}`), ten sam zapis idzie online (SELF); zapisy v1-v9 wczytują się bez zmian.
- Akcje: `bag_move`, `bag_use`, `bag_drop`, `bag_equip`, `bag_out`, `slot_wear`.
- Czary smoków: nowe obszary `WAVE9` (40 pól), `WIDE_BEAM8` (24), `CROSS_NOVA` (41); każdy smok ma własny zestaw (zielony, lodowy, czarny,
  złoty, biały, lodu, kości, magmy, Skaldra, Ignaroth), silniejsze rzucają wielkie fale, kule 3x4/5x5 i gwiazdy; obrażenia na atak bez zmian (+-10%).
- Sceny regresji GUI `nested_bag_*`, `dragon_wave9`, `dragon_star`; testy `NestedBackpackTest`, `DragonSpellAreasTest`.

### Zmieniono
- Użycie plecaka z plecaka otwiera go; dawne zastąpienie głównego plecaka to akcja "Załóż" (`slot_wear`).
- Efekt dużych obszarów czarów na kliencie świeci naraz na całym obszarze (krótsze opóźnienie na pole).
### Zmieniono
- 8 kierunków jak w Tibii (skos kosztuje 3 zwykłe kroki): joystick (stały i pod kciukiem; wariant „Joystick (8 kierunków)”), krzyżyk i przytrzymanie
  na mapie dają 8 kierunków od razu (8 równych sektorów po 45°, martwa strefa i histereza bez zmian, kierunek nie migocze na granicy sektora);
  „Pływający, 4 kierunki” zostaje do wyboru w ustawieniach. Marsz po dotknięciu mapy nadal idzie prosto, a skosem tylko przy przeciskaniu się.
- Potwory wędrują też po skosie (krok po skosie trwa 3x), ucieczka i trzymanie dystansu używają 8 kierunków.

### Dodano
- Klawiatura: dwa klawisze naraz = skos, blok numeryczny 1/3/7/9 = skosy (`KeyboardDirection`).
- Opcjonalna grafika 8 kierunków potworów: `klucz_ne|nw|se|sw` + `_walk1/_walk2`; bez niej skos patrzy w lewo/prawo jak w Tibii (docs/GRAPHICS.md).
- Testy 8 sektorów joysticka, klawiatury, spaceru potworów po skosie, czasu kroku x3 i wyboru grafiki; sceny regresji `eight_*`.

- Pięć nowych wysp (generator `tools/mapgen/islands5.py`, dane `islands5_data.py`, podpięte w `generate_continent.py`): darmowa
  **Wyspa Szeptów** (okolice 894-926, 94-155; poz. 8-60; powierzchnia plus 3 piętra w dół i dwa lochy: Kopiec Sroki i Wieża Szeptów)
  oraz cztery wyspy premium (poz. ok. 60-400, piętra od -5 do +2, kilka łowisk): lodowa **Biała Zamieć** (122-177, 73-134),
  pustynna **Złote Wydmy** (886-940, 901-974), tropikalna **Szmaragdowa Mgła** (49-119, 879-936) i umiarkowana **Wyspa Starych Dębów**
  (182-231, 822-902). Żadna nie jest osiągalna pieszo z Varden (sprawdza `IslandsFiveTest`).
- 17 nowych potworów (stałe w `monsters.csv`, łupy, bestiariusz, nazwy EN/ES, grafika OTSP 4 kierunki): m.in. Pan Kopca, Król Sroczy,
  Jarl Szronu, Regent Piasku, Cesarzowa Pnączy i Suwerenny Dąb. Stawiane tylko ręcznie (`HAND_PLACED`).
- Rejs na nowe wyspy wymaga znacznika postaci (`data/ship_access.csv`, `npc/ShipAccess`): kapitan odmawia z podpowiedzią, dopóki quest
  dostępowy nie ustawi znacznika; wyspy premium wymagają też konta premium lub promocji. Menu kapitana blokuje wiersze (`ship.row_locked`).
- Przewoźnik Oswin na nabrzeżu Cichej Przystani (845, 283) na wschodnim wybrzeżu, osiągalny pieszo z Varden: czteroetapowy quest
  ze srebrną broszką z Nory Sroki (mała strzeżona lochowa z drzwiami wymagającymi klucza i dźwigni) otwiera darmowy rejs.
- 25 linii questów (64 misje: 9 darmowych i 16 premium) z NPC o angielskich imionach, powiązanych między sobą nowym plikiem
  `data/quest_requires.csv` (wymagania między liniami; `QuestRegistry.withRequires`), z drzwiami `party:N`, poziomem, przedmiotami,
  zabójstwami i krokami VISIT; dziennik ukrywa linie z zablokowanym pierwszym krokiem.
- Obozy barbarzyńców: duży obóz (**Obóz Wodza Zamieci**, piętra 0, +1 i +2) na wyspie lodowej: nieregularna palisada z kłód, trzy bramy,
  chaty, wieże strażnicze (platformy na drabinach) z drugim poziomem na dwóch z nich, skrzynie z tabliczkami przy chacie wodza i
  boss **Wódz Zamieci** (poz. 330); mały darmowy **Obóz Hetmana Skarna** na lodowych skałach kontynentu (poz. 30-58, bez premium).
  8 nowych potworów (`frostfur_raider`, `snowclub_brute`, `skarn_hetman`, `icefang_hunter`, `bonewrap_tamer`, `ironjaw_splitter`,
  `rimeblood_walker`, `blizzard_warchief`) z nazwami PL/EN/ES, łupem, bestiariuszem i grafiką OTSP.
- Zasada „żadnych prostokątów”: wieże, sale bossów, skarbiec i chata Nory Sroki mają teraz nieregularne obrysy (`islands5.noisy`).
- Grafika: `tools/art/islands_art.py` (srebrna broszka), nowe wpisy w `otsp_import.py`; test `IslandsFiveTest`.

## [0.264.0] - 2026-11-13

### Dodano
- Bagienny łupieżca (`bog_reaver`, poziom 85, grafika Darka z Goblin3D, `docs/CREDITS.md`): potwór bagien wg Tibia Wiki (1300 HP, 800 exp, pancerz 20, odporności jak bagienne zjawy: ogień 85%, ziemia 30%; cios z zatruciem 0-180, wyssanie życia 90-140, bąbel 100-175, stalagmit 96-110, paraliż, leczenie ok. 100). Trzy spawny w Leżu Krokodyli i jaskiniach bagien (zamiast części bagiennych zjaw), 12 klatek grafiki (`tools/art/bog_raider_import.py`), spawny `tools/mapgen/bog_reaver_spawns.py`, scena regresji `bog_reaver`.
- Silne potwory rzucają różne czary (analiza: `analizy/potwory-ataki.md`, sekcja 6): każdy potwór od poziomu 100 i każdy boss (poza
  smokami) ma uderzenie w jeden cel i czar obszarowy dobrane do żywiołu i klasy (demony: kule ognia i wyssanie many, mumie i upiory:
  uderzenie śmierci i fala klątwy, piraci: strzały i bomby, golemy: rzut kamieniem/lawą i kula, harpie: energia i promień), a od
  poziomu 300 co najmniej trzy czary ze stanem (podpalenie, zatrucie, zamrożenie, porażenie, klątwa, krwawienie) i większymi obszarami
  (`WAVE7`, `BEAM6`, `CIRCLE3X3`, `CIRCLE5X5`). Zmieniono 68 potworów (`data/monster_spells.csv`, 18 przeszło z `MELEE` na `MIXED`);
  oczekiwane obrażenia czarów pozostały w widełkach poprzednich potworów. Test `StrongMonsterSpellsTest`.
- `-Dpixel.mobDemo=cast:potwor:czar[:klatki]` pokazuje dowolny czar dowolnego potwora; sceny regresji `mob_strong_bolt/ball/beam`.

## [0.263.0] - 2026-11-12

### Dodano
- Style ataku potworów jak w Tibii (analiza: `analizy/potwory-ataki.md`): każdy potwór ma profil w `data/monster_behaviour.csv`
  (archetyp: wręcz, dystans, czarodziej w cel, czarodziej obszarowy, przywoływacz, mieszany, wsparcie; dystans; pocisk; ucieczka)
  i czary w `data/monster_spells.csv`: uderzenie w cel (`BOLT`), fala/kula/wybuch (`WAVE`/`BALL`/`SELF`), leczenie siebie (`HEAL`),
  przyspieszenie (`HASTE`), przywołanie pomocników (`SUMMON`), paraliż (`SLOW`), wyssanie życia i many.
- Strzelcy i czarodzieje trzymają 3-4 pola od gracza i cofają się, gdy podejdzie; ranni uciekają (Canary `runonhealth`), leczą się
  i wracają do walki; ucieczka nie przerywa strzelania ani czarów.
- Widoczne pociski potworów (włócznie, strzały, bełty, kamienie, noże, kule żywiołów) z nazwą grafiki w zdarzeniu (`PHYSICAL/spear`).
- Przywoływacze (np. Akolita Czarnej Świecy, Orczy szaman, Ysmera) przywołują pomocników z limitem naraz; pomocnicy znikają ze
  śmiercią pana, nie dają łupu ani doświadczenia.
- Online: czar obszarowy potwora trafia każdego gracza w obszarze (nie tylko cel), a pociski i efekty widzą wszyscy gracze w pobliżu.
- Nowe sceny regresji GUI `mob_*` (`-Dpixel.mobDemo=spear|caster|area|summon|flee`).

### Zmieniono
- Strzelec nie stoi dokładnie w zasięgu, lecz 3-4 pola od celu (test `MonsterChaseTest`).
- Tarcza trenuje się od ataków podstawowych wręcz i fizycznych pocisków (czary nie, jak dotąd); kapłan pustki wysysa manę czarem
  `mana_drain_strike`.

## [0.262.0] - 2026-11-12

### Dodano
- Tabliczki przy skrzyniach questowych (`treasure/ChestSigns`): przy każdej skrzyni z wyborem nagrody i skrzyni questowej stoi nieblokująca
  tabliczka; użycie jej pokazuje (PL/EN/ES), co jest w środku dla Twojej profesji albo jakie zadanie trzeba ukończyć.
- 13 nowych questów w stylu Tibii z istniejących potworów i przedmiotów (bez nowych potworów, przedmiotów i grafik): 4 łatwe (poziom 8-22)
  i 3 średnie (35-55) za darmo na kontynencie głównym, 3 średnio-trudne (70-110), 2 trudne (150, 210) i 1 ekstremalny (270) premium na
  ziemiach dostępnych statkiem. Każdy ma NPC z angielskim imieniem, dialog, trzy misje (dojdź, zabij, zabij), skrzynie z wyborem nagrody
  i drzwi poziomu; generator `tools/mapgen/story_quests.py`.
- Nowy typ misji `VISIT` (dojdź do miejsca) w `quests.csv`.
- Dziennik questów jak w Tibii: dwukolumnowe okno z listą linii questów (strony), krokami, wskazówką i nagrodą (`QuestLines`, `QuestJournal`).
- Wymaganie bramy `party:N` (minimalna liczba graczy online w drużynie), wiersz `gate;x;y;z;party:N`; użyte na wejściu questu ekstremalnego (3 graczy).
- Wymaganie nagrody `reward;grupa;wymaganie` w `star_quest.csv`; właściwość `-Dpixel.quests=klucz:done,klucz:active:N` do zrzutów.
- Analiza `analizy/questy-nagrody.md`, testy `StoryQuestsTest`, `QuestRewardAuditTest`, sceny gui-regression dla dziennika, tabliczki i NPC.
- Ród pięciu smoków (`monsters.csv`, `monster_spells.csv`): zielony **Szmaragdowy smok** (`emerald_dragon`, poz. 205), lodowy
  **Szronowy smok** (`hoarfrost_dragon`, 240), czarny **Węglowy smok** (`coal_dragon`, 320), złoty **Złocisty smok** (dawny
  `dune_drake`, 410; klucz i grafika bez zmian) oraz bardzo rzadki biały **Perłowy smok** (`pearl_dragon`, 600). Od najsłabszego:
  zielony, lodowy, czarny, złoty, biały. Grafika: przebarwione klatki złotego smoka 64x64 (`darekart_import.py --recolour`).
- Czary potworów jak w Canary (`MonsterSpell`, `MonsterSpells`, `CombatSystem.monsterAttacks`): smok przy każdym ataku próbuje czarów
  (każdy z własną szansą), inaczej gryzie z sąsiedniego pola. Fala (`WAVE4`, `WAVE7`, `SQUAREWAVE5`, promień `BEAM6/8`) obraca się
  do celu, kula (`CIRCLE1X1`, wielka kula ognia `CIRCLE3X3`) leci w cel, wybuch (`CIRCLE5X5`) wokół smoka; trafienie podpala
  (`BURNING`), zamraża (`FREEZING`), razi (`ELECTRIFIED`) albo spowalnia jak paraliż (`PlayerCharacter.addSlow`). Efekty
  graficzne to zdarzenia kształtu z `CombatEvent.shaped`, więc działają offline i online. Zielony i czarny: ogień; lodowy: lód i
  spowolnienie; złoty: ogień i piorun; biały: ogień, lód i piorun.
- Rzadki biały smok (`RareSpawns`, `MonsterManager.rollRare`): przy odrodzeniu smoka na łowisku, na którym stoi gracz, z szansą 1,5%
  zamiast niego pojawia się Perłowy smok; przerwa godzina, najwyżej jeden żywy, cicha wiadomość dla graczy w pobliżu. To samo w grze
  offline (`GameSession`) i na serwerze (`ServerWorld`); `setRareRandom` pozwala testować z ziarnem.
- Łup smoków: łuski (`emerald_scale`, `frost_scale`, `coal_scale`, `gilded_scale`, `pearl_scale`, skup w `camp_shop` i `isle_shop`),
  tarcze i zbroje z łusek (`emeraldscale_shield`, `hoarscale_shield`, `coalscale_armor`, `gilded_helmet`, `pearl_aegis`), nazwy EN/ES.
- Spawny: zielone i czarne smoki na Smoczych Wzgórzach, czarne także w wulkanie, szronowe w lodowych grotach
  (`tools/mapgen/dragon_spawns.py`; `route_levels.py` bez naruszeń). Bestiariusz: wpisy dla czterech nowych smoków.
- Gui-regression: `dragons_five`, `dragon_fire_wave`, `dragon_ice_breath` (`-Dpixel.dragonDemo=five|fire|gfb|ice|white`).
- Wskaźnik pingu pod ramką widoku, obok FPS: kropka (zielona poniżej 100 ms, pomarańczowa 100-250, czerwona powyżej 250 albo brak odpowiedzi)
  i wartość w ms. Prawdziwy czas obiegu: klient wysyła `PING` co 2 s, serwer odpowiada `PONG` natychmiast poza kolejką świata, pokazywana
  jest mediana 5 pomiarów (`net/PingMeter`, `NetClient.ping`). Gra lokalna kropki nie pokazuje; do zrzutów `-Dpixel.fakePing=MS`.
- Depozyt jak w Tibii: dotknięcie skrzyni depozytu (`depot_locker`, w każdym mieście) otwiera okno depozytu obok plecaka (100 miejsc,
  przewijane), przenoszenie przeciąganiem albo dotknięciem w obie strony, przycisk "Odłóż łup" (`GameSession.depotTransfer`, akcja
  `depot_move`). Online i offline, zawartość w zapisie postaci i wspólna dla miast. Na telefonie depozyt zostaje listą.
- Trening offline przy posągach treningowych: w świątyni każdego miasta stoi posąg (`training_statue`, generator `tools/mapgen/trainers.py`).
  Dotknięcie: wybór Miecz / Topór / Maczuga / Pięści / Dystans / Poziom magii i wylogowanie (tylko konto premium, jak w Canary). Po powrocie
  (min. 10 min offline, maks. 12 godz. na pasku) umiejętność i tarcza dostają próby wg wzorów Canary, raport w dzienniku. Pasek
  "Trening offline" w panelu umiejętności odnawia się podczas gry. Opis w `docs/GAME_SYSTEMS.md`.
- Nowe kontenery: Plecak podróżnika (24 pola) i Plecak badacza (36 pól) jako przyszłe nagrody za zadania (ikony z odcieniem plecaka).
- Analiza modułów Canary i OTClient względem Pixela z listą priorytetów: `analizy/canary-moduly.md`.
- Sceny gui-regression: `bag_big`, `depot_window`, `training_statue`, `skills_offline`, `ping_green`, `ping_red` (wzorce dopiero po scaleniu).

### Zmieniono
- Przebalansowane nagrody 20 zadań obozów i wysp (bossy do 0,5 kosztu poziomu, zabicia 0,10-0,12) oraz ścięte nagrody `varden_smug1-3` do 1,2 poziomu.
- Pasek życia, ikony i imię dużych potworów (smoki 2x2) leżą nad grafiką, nie w połowie sprajta (`OverheadLayout`, `TileArt.bigMonsterHeight`).
- Pochodnia świeci wyraźnie dalej niż minimalna poświata: promień pochodni 7 (było 5), poświata postaci 2 (było 2,5), pochodnia
  co najmniej `Lighting.TORCH_MIN_GAIN` = 3 razy dalej.
- Złoty smok (`dune_drake`) walczy teraz wręcz i czarami (fala ognia, piorun, wielka kula ognia) zamiast trującego ciosu.
- Plecak: 20 pól na jednej stronie, bez zakładek i stron. Większe kontenery przewijają się w pionie (kółko myszy, pasek przewijania na
  komputerze, przyciski `<` `>` na telefonie) zamiast przechodzić na kolejną stronę. Użycie większego plecaka zastępuje obecny, a poprzedni
  wraca do torby. Usunięto przycisk ODEPNIJ i akcję `bag_detach`; weteran (poziom 250+) startuje z plecakiem na 24 pola.
- Zapis postaci: nowe pola `bagslots` i `offtrain`; stary zapis z `bags=<n>` (strony) wczytuje się jako jeden przewijany plecak
  `20 * (1 + n)` pól, nic nie ginie. Mapa świata przegenerowana (posągi w świątyniach); wiersz Skills ma o jeden wiersz więcej.

## [0.261.0] - 2026-11-12

### Dodano
- Poziomy dostępu jak w Canary: gracz, tutor, game master i GOD (`admin/AccessLevel`). Istniejący administratorzy z `admins.txt` są game masterami
  (moderacja jak dotąd); wpis `Imię:tutor` albo `Imię:god` nadaje inny poziom. Nowy plik `gods.txt` obok `admins.txt` (imię w linii) daje GOD-a.
  Nic nie jest wpisane na stałe w kodzie: żadnego konta, hasła ani tokenu - właściciel rejestruje się zwykłą postacią, a operator nadaje GOD-a po
  imieniu (plik albo konsola serwera: `god Imię`, `ungod Imię`, `reload` bez restartu). Poziom serwer liczy sam z nazwy postaci, klient go nie wysyła.
- Polecenia GOD (`admin/GodCommands`, wspólne dla gry lokalnej i serwera): `/i` (przedmiot), `/m` (potwór), `/a N`, `/up`, `/down`, `/goto`,
  `/c`, `/t`, `/ghost`, `/godmode`, `/level`, `/addexp`, `/addskill`, `/sethp`, `/setmana`, `/heal`, `/money`, `/premium`, `/promote`, `/bless`,
  `/flag` (znaczniki zadań, np. `star_sealed`), `/clean`, `/kill`, `/speed`, `/info`, `/pos`, plus aliasy `/broadcast`, `/raid`, `/save`, `/shutdown`.
  `/komendy` pokazuje w oknie polecenia Twojego poziomu. Każde polecenie sprawdza poziom na serwerze, a wynik (także odmowa) trafia do
  `admin.log` obok `admins.txt`.
- Duch (`/ghost`) i tryb boga (`/godmode`): bez obrażeń, duch niewidoczny dla graczy i potworów (tylko GOD). Nad postacią personelu widać znacznik
  `[GOD]`/`[GM]`/`[TUTOR]` (nowy komunikat `ACCESS` i pole wiersza `PL`).
- Gra lokalna jako GOD: `-Dpixel.god=true`, zmienna `PIXEL_GOD=1` albo pusty plik `god.enabled` w katalogu zapisów; domyślnie wyłączone,
  nigdy nie działa online ani nie trafia do zapisu postaci.
- Przewodnik dla właściciela: `analizy/komendy-god.md`. Testy: `GodCommandsTest`, `GodNetworkTest`, rozbudowany `ChatCommandsTest`; sceny
  gui-regression `god_komendy` i `god_tag` (wzorce dopiero po scaleniu).

## [0.260.0] - 2026-11-12

### Dodano
- Szlif mapy (`tools/mapgen/wall_lights.py`, `tools/mapgen/stairs.py`, renderer): analiza wad w `analizy/mapa-szlif.md`.
  - Ciemne wnętrza: pokój (zwarte wnętrze budynku, od 14 pól) bez żadnej pochodni dostaje ją na ścianie widocznej z wnętrza,
    jedna na ok. 36 pól (do 6), z odstępem co najmniej 3 pól od innych świateł i nigdy na drzwiach i oknach (`light_rooms`).
  - Schody w górę i drabiny w lochach opierają się o ścianę (`stairs.back_stairs`): na polu na północ od nich staje mur (i dwa pola
    obok), tylko tam, gdzie nie rozspaja to siatki pieszej, nie ma obiektów i celów przejść; wyjście zostaje od południa.
  - Schody w dół rysowane w ramie z ciemnym otworem wpuszczonym w podłogę (`TileArt.stairPit`), nie na gołej ziemi.
  - Duże potwory (plik postoju od 41 px: smoki, demony, hydry, bossowie) rysowane w naturalnym rozmiarze 64x64 na 2x2 polach od prawego
    dolnego rogu ich pola, jak smok w Tibii (`TileArt.bigMonster`, `GameScreen`). Złoty smok (`dune_drake`) ma teraz oryginalne klatki
    64x64 bez skalowania (`tools/art/darekart_import.py`).
  - Testy `MapPolishTest` (pochodnie: mur i przechodnie pole przed nimi, nie na drzwiach i oknach; obiekty nie wiszą w murach; pokoje
    bez ciemnych; schody oparte o ścianę), `BigMonsterArtTest`, `LightingTest` (loch ciemny, ale postać ma minimalne światło).
  - Sceny gui-regression: `cave_dark`, `cave_torch`, `stairs_down_pit`, `gold_dragon`, `shop_torch_night`, `brick_grass_edge`
    (wzorce do wygenerowania po scaleniu); `-Dpixel.gear=torch` zapala założoną pochodnię.

### Zmieniono
- Jaskinie i lochy są prawie ciemne poza światłem (jasność otoczenia -1: 0,10, niżej do 0,04), więc pochodnie i światło postaci
  mają znaczenie jak w Tibii; postać zawsze ma minimalną poświatę (`Lighting`).
- Obrzeża: kostka z cegły (`brick_paving`) i bruk (`paving`) mają priorytet obrzeży, więc trawa nachodzi na nie poszarpaną krawędzią
  zamiast twardego kwadratu (Nerys, Valmir, miasta z ceglanym placem).
- Mur budynku stoi na podłożu z zewnątrz (trawa, ziemia), nie na podłodze wnętrza: koniec z paskiem parkietu w rogu domów z bali
  (Elandor, `WorldRenderer.wallBase`).

## [0.259.0] - 2026-11-12

### Dodano
- Grafiki od właściciela (licencja nieznana, dodane na jego odpowiedzialność 2026-11-12; wpisy w `docs/CREDITS.md`, narzędzie
  `tools/art/darekart_import.py`): nowe kamienie obiektu `rock` (szare, ciemne popękane i porośnięte mchem; trzy duże głazy rysowane
  2x2 pola jak krzaki) i `snow_rock` (śnieżne), oraz złoty smok jako grafika potwora Wydmowy smok (`dune_drake`).
- Kamienie z OTSP zastąpione tylko tam, gdzie nowe są wyraźnie lepsze. Nie użyto: chmur, run-kamieni z JPG, schodów i dachów (w paczkach
  nie było pasujących), niczego z Tibii ani innych gier.
- Test `StarQuestTest.theVardenHeraldStandsApartFromEveryOtherNpcAndIsReachable`, sceny gui-regression `rocks_grass` i `herald_talk`.

### Naprawiono
- Przycisk "Mów" w świątyni Varden otwierał kapłana zamiast Heralda Matthiasa (stali po przekątnej). Herold stoi teraz na kobiercu przed
  ołtarzem (498,494), co najmniej 4 pola od każdego innego NPC; świat wygenerowany ponownie (zmienił się tylko `npcs.csv`).

## [0.258.0] - 2026-11-12

### Dodano
- Pieczęć Gasnącej Gwiazdy: bardzo długi quest endgame (poziom 300+) na nowej wyspie premium "Wyspa Gasnącej Gwiazdy" (dojazd tylko statkiem,
  przewoźnik Captain Brannoc, trasy z Varden, Qasirah i Valmir; pieszo z Varden nie ma drogi). Generator `tools/mapgen/citadel.py`:
  dziedziniec z przystanią i Warden Thessaly (zleceniodawca), Świątynia Pieczęci za drzwiami poziomu 300, siedem portali do sekcji,
  siedem sekcji pod morzem po trzy piętra (-1..-3): Cytadela Popiołu, Klasztor Szronu, Kuźnia Żużlu, Ogrody Cierni, Sale Zwierciadeł,
  Paszcza Głodu i Komnata Pożeracza Światła. Każda sekcja kończy się bossem (Vorgrim 360, Ysmera 420, Kharzul 470, Vessara 520,
  Ilmora 575, Ugrath 640 i końcowy Noctharion 700); ok. 360 punktów spawnu.
- Siedem misji w łańcuchu (`star_m1`..`star_m6` na potworach sekcji, `star_final` na Noctharionie), zapisywane w postaci (`QuestLog`).
  Bramy: budka bramna w każdej sekcji otwiera się po misji (`quest:star_mK`), drzwi za bossem po jego pokonaniu (`boss:<klucz>`), portal do
  sekcji k po bossie sekcji k-1, sala nagród (pod świątynią, na -1) po `star_final`. Wszystkie drzwi bram są też drzwiami poziomu 300 (`QuestGate`, `star_quest.csv`).
- Znacznik wyprawy `star_sealed` w postaci (`CharacterCodec`, pole `qflags`); ustawia go oddanie ostatniej misji.
- Sala nagród: jedenaście skrzyń z jednym elementem ekwipunku każda (miecz, topór, młot, łuk, różdżka, laska, rękawice, zbroja, tarcza,
  księga, amulet; poziom 300); postać wybiera dokładnie jeden, przedmiot cudzej profesji nie zużywa wyboru, wybór zostaje po zapisie.
- Herold Matthias (nowa rola NPC `HERALD`) w świątyni Varden sprzedaje wszystkie brakujące błogosławieństwa naraz (pięć zwykłych i Zrządzenie
  losu) za jedną cenę (80% sumy cen u mnichów, w setkach gp), ale tylko z `star_sealed`; bez niego odmawia i tłumaczy, jak je zdobyć.
  Sześciu mnichów z kaplic bez zmian. Działa tak samo offline i na serwerze (sesja gracza).
- Trzynaście nowych potworów z grafiką OTSP (cztery kierunki, klatki marszu), siedmiu bossów, nazwy EN/ES, wpisy bestiariusza i łupy;
  25 nowych przedmiotów (11 nagród, 8 przedmiotów tylko z bossów, 6 materiałów sekcji; `tools/art/star_art.py`).
- Testy `StarQuestTest` (misje, bramy bossów, wybór jednej nagrody, kodek, Herold z flagą i bez, dostęp od przystani i brak drogi z Varden),
  sceny gui-regression `star_*`.

### Zmieniono
- Mapa świata wygenerowana ponownie (nowa wyspa; numeracja legendy przetasowana, stąd duży diff plików pięter).
- `MovementSystem` zwraca `GATE_SEALED` na zapieczętowanym przejściu; `WorldRenderer` rysuje fioletową tabliczkę bramy zadania.

## [0.257.0] - 2026-11-12

### Dodano
- Sześciu mnichów błogosławieństw w małych kaplicach przy miastach (`tools/mapgen/chapels.py`): Varden (494,458, Duchowa Tarcza),
  Frostholm (701,235, Objęcia Świata), Qasirah (258,520, Ogień Słońc) dla darmowych kont oraz Valmir (451,718, Iskra Feniksa),
  Selvaran (711,666, Mądrość Samotności), Nerys (865,696, Zrządzenie losu) tylko dla premium. Rozmowa słowami z ceną zależną od poziomu.
- Śmierć jak w Tibii (Canary): ten sam ułamek odbiera exp, umiejętności i poziom magiczny; plecak i noszone przedmioty wypadają do ciała
  gracza z szansą zależną od liczby błogosławieństw (pięć = nic), Amulet Straty chroni ekwipunek, czerwona i czarna czaszka tracą wszystko.
- Przedmiot Amulet Straty (sklepy magiczne), grafiki mnichów z OTSP, `DeathLoss`, testy `DeathLossTest`, `BlessingChapelsTest`.

### Zmieniono
- Błogosławieństwa: pięć zwykłych po -8% straty (razem -40%) i Zrządzenie losu w PvP; usunięto Serce/Krew Góry i hurtowy zakup.
- `/blogoslawienstwa` jest oknem informacyjnym (co masz i gdzie kupić); kapłani w miastach ich nie sprzedają.
- Mapa świata wygenerowana ponownie (nowe kaplice; numeracja legendy przetasowana, stąd duży diff).

### Usunięto
- Kupno błogosławieństw ze sklepu (`Store`).

## [0.256.0] - 2026-11-12

### Zmieniono
- Imiona NPC są zawsze po angielsku, także w polskiej i hiszpańskiej wersji gry (np. "Merchant Tomas", "Pet Keeper Maciej",
  "Queen Świętosława"). Dialogi zostają w wybranym języku.

## [0.255.0] - 2026-11-12

### Dodano
- Miasto Rogatych (`tools/mapgen/minotaur.py`): wielkie nieregularne miasto na -7 pod wolnym kontynentem (x 477-567,
  y 333-445), z krętymi ulicami, nieregularnymi kamiennymi budynkami, ślepymi zaułkami, bocznymi jaskiniami, areną
  w centrum i salą tronową. Potwory na poziomy 20-100: słabsi przy zejściach, silniejsi w centrum i głębi, boss
  Grozdar, Pan Labiryntu (poziom 100) przy tronie (521,382,-7) i skrzynia zadania obok (526,381,-7).
- Dwa kręte zejścia z powierzchni (Labirynt Rogatych, piętra -1..-6): wejścia w górach (503,407) i (533,344).
- Sześć nowych potworów rogatych (łowca, saper, gladiator, wyrocznia, mistrz areny, Grozdar) z łupem, bestiariuszem,
  nazwami EN/ES i grafiką (4 kierunki, kroki); dotychczasowi rogaci (zwiadowca/rozbójnik/łucznik itd.) wracają w mieście.
- Testy: `CampsTest`, `MinotaurCityTest`; sceny regresji GUI obozów i miasta (wzorce do wygenerowania osobno).

### Zmieniono
- Obozy potworów w dziczy (orkowie, gobliny, rabusie, trolle itd.) nie są już kwadratowe: nieregularny obrys
  (palisada, kamienny pierścień lub ruiny), rozrzucone namioty, chaty i skrzynie (`tools/mapgen/camps.py`).
  Środki obozów są od siebie co najmniej 50 pól, obozy tego samego rodzaju co najmniej 70.
- Zamek, wzgórza, twierdze i miasta zostają na swoich miejscach (przypięte pozycje w generatorze). Lochy dziczy
  dostają pełną liczbę 66 miejsc (wcześniej pętla kończyła się za wcześnie przy zablokowanych polach).
- Wzgórza Goblinów: poziom maks. regionu 23 -> 35 (adaptacja `route_levels`), Durnkar przesunięty o kilka pól.

## [0.254.0] - 2026-11-12

### Dodano
- Trzy krainy wzgórz, każda z powierzchnią i podziemiami o nieregularnym, organicznym kształcie (generator
  `tools/mapgen/hills.py`): grzbiety górskie z krętymi dolinami, jaskinie z wąskimi korytarzami, ślepymi komorami i
  skrzyniami na każdym piętrze, spawny rozrzucone po całym terenie.
  - Wzgórza Goblinów (kontynent główny, bez premium, x 578-627, y 411-484): goblin, szabrownik, szaman, nożownik i herszt
    od najsłabszego do najsilniejszego; podziemia Nory Goblinów -1..-3 (herszt na dnie).
  - Wzgórza Smoków (pustynia przy Qasirah, x 84-151, y 252-326): pisklęta, smoki łuskowe i skalne, potem smoki
    piaskowe, magmowe i kostne; Smocze Pieczary -1..-4, najsilniejsze smoki najgłębiej.
  - Wzgórza Hydr (duża wyspa wschodnia, tylko statkiem, x 891-960, y 381-450): jaszczury bagienne, hydry, wężyce i
    gorgony; Legowiska Hydr -1..-5. Dojście z kontynentu po skosie przez las zasypują głazy, tunel pod morzem kończy się
    na sąsiednim kawałku lądu.
- 12 nowych potworów z grafiką (OTSP), łupem, wpisami w bestiariuszu i nazwami EN/ES: goblin szabrownik, nożownik,
  herszt, jaszczur bagienny, jaszczur guślarz, hydra bagienna, wężyca bagienna, hydra jadowita, gorgona, smocze pisklę,
  smok łuskowy, smok skalny.
- Nazwy miejsc w angielskim i hiszpańskim (`Region.displayName()`, rodzaj `place` w `data_en.csv` i `data_es.csv`):
  komunikat wejścia do regionu, tabliczki, bestiariusz i status bossów.
- `HillsTest` (osiągalność, piętra, kształty, spawny, brak kolizji z miastami i zamkiem, tłumaczenia) i sześć scen
  gui-regression `hills_*`.

## [0.253.0] - 2026-11-12

### Dodano
- Potwory chodzą i obracają się jak w Tibii: każdy z 136 potworów ma grafiki na cztery strony (N, E, S, W) z paczki OTSP,
  po trzy klatki (postój i dwa kroki). W marszu pokazuje klatki kroku (lewa i prawa noga na przemian) i patrzy w stronę
  ruchu, a stojąc - klatkę postoju. Przy ataku (zwarcie i dystans) odwraca się twarzą do celu, ruch po skosie patrzy na
  wschód albo zachód. Działa offline i online: klient wylicza kierunek z przesunięć pozycji z klatek serwera, a stojący
  potwór obok gracza patrzy na niego. Grafiki generuje `tools/art/otsp_import.py` (pliki `potwor_up|left|right[_walk1|2].png`).
- Scena podglądu `-Dpixel.facingDemo=dirs|walk|attack` i dwie sceny gui-regression (`monster_facing_dirs`, `monster_facing_attack`).

## [0.252.0] - 2026-11-11

### Dodano
- Warownia Gorvalda: zamek dla poziomów 20-70 na północy kontynentu głównego. Z zewnątrz nie da się wejść: fosa, mur
  bez bram. Wejście jest tylko przez podziemia - jaskinię w skale, trzy piętra tuneli z orkami, rogaczami i trollami
  jaskiniowymi oraz piwnicę pod twierdzą. W zamku (4 piętra) mieszkają Rogaci i żołnierze Warowni, a na szczycie za
  drzwiami poziomu 55 czeka Gorvald, Kasztelan Warowni, ze skrzynią zadania. Nowe potwory z grafiką i bestiariuszem.

### Zmieniono
- Przy tworzeniu postaci można wybrać tylko 3 miasta kontynentu głównego: Varden, Frostholm i Qasirah. Valmir, Selvaran
  i Nerys to ziemie premium - odcięte kanałami (nie ma do nich mostów ani dróg z kontynentu głównego), dopłynie się
  tam statkiem (konto premium albo promocja). Postacie, które tam mieszkają, zachowują dom. Wozacy jeżdżą tylko po
  jednym lądzie. Durnkar zostaje na kontynencie głównym (tylko zamieszkanie).
- Durnkar to teraz trzypiętrowe, organiczne miasto (Wysokie Hale, Rynek, Głębiny) z krętymi ulicami zamiast kwadratu,
  16 domów, schodami między piętrami i drzwiami poziomu do kopalni. Sztolnie do miasta mają 3 piętra.

## [0.251.0] - 2026-11-11

### Dodano
- Trzy języki: angielski (domyślny dla nowych instalacji), polski i hiszpański. Wybór jest na pierwszym ekranie
  (menu główne), przy logowaniu, przy tworzeniu postaci i w ustawieniach; zapisuje się w ustawieniach i działa od razu.
- Wspólna klasa `Messages` i paczki `messages_en/pl/es.properties` (klient i serwer): cały interfejs (HUD, okna, menu,
  ustawienia, komunikaty systemowe i czatu, podpowiedzi, opisy), ponad 2800 tekstów w trzech językach.
- Nazwy danych po angielsku i hiszpańsku (`data_en.csv`, `data_es.csv`): przedmioty, potwory, czary, zadania, imiona i
  powitania NPC. Zaklęcia (słowa) zostają po tibijsku, nazwy miast i miejsc to nazwy własne.
- Online: klient wysyła język w `HELLO` (siódme, opcjonalne pole), a serwer formatuje komunikaty w języku każdego
  gracza. Stary klient bez pola dostaje polski.
- Polecenia czatu `/idź` (rola NPC) mają angielskie i hiszpańskie nazwy ról; polecenia czatu poza tym zostają polskie.
- Testy: komplet kluczy w trzech językach, nazwy danych, trwałość języka, brak polskich tekstów w interfejsie klienta,
  glify czcionki dla ñ á é í ó ú ü ¿ ¡ i polskich liter.

### Zmieniono
- Regresja GUI domyślnie uruchamia sceny po polsku (`-Dpixel.lang=pl`), bo wzorce są polskie; nowe sceny
  `lang_*` pokazują angielski i hiszpański.

## [0.250.0] - 2026-11-11

### Zmieniono
- NPC i strażnicy wracają do dawnych strojów OpenTibia Sprite Pack; postacie LPC zostały wycofane (assets/art/lpc,
  tools/art/lpc_compose.py, flaga -Dpixel.lpcNpcs, autorzy LPC w /autorzy i docs/CREDITS.md). Brakujące obrazki
  (strażnicy guard_15..24, przewoźnicy isle*_ferryman, gull/lighthouse_ferryman, kapitan Pożogi) wygenerowano z tych
  samych strojów; 16 krasnoludów z Durnkaru ma stroje ról z rudą i siwą brodą, ściśnięte w pionie jak potwory dwarf*
  (`tools/art/otsp_import.py`, tabela `DWARF_NPCS`; obrazki odtwarza `python3 tools/art/otsp_import.py <client_files>`).
  Atlas grafik 32 px: 1335 / 2048.
- Błogosławieństwa jak w Tibii zamiast jednego jednorazowego: pięć zwykłych (Mądrość Samotności, Iskra Feniksa, Ogień
  Słońc, Duchowa Tarcza, Objęcia Świata) i dwa górskie (Serce Góry, Krew Góry) zmniejszają stratę doświadczenia po
  8% każde (razem -56%); poniżej poziomu 24 pięć zwykłych daje -50%. Znikają przy śmierci. Ceny jak w Tibii: do
  poziomu 30 po 2000 gp, potem 200 gp x (poziom - 20), od poziomu 120 20 000 + 75 gp za poziom; górskie drożej;
  wszystkie pięć naraz u kapłana +10%. Zapisy ze starym błogosławieństwem dostają pięć zwykłych.

### Dodano
- Zrządzenie losu (PvP): przy śmierci z ręki gracza bez czerwonej czaszki znika tylko ono, a zwykłe błogosławieństwa
  zostają; wymaga najpierw innego błogosławieństwa. Do poziomu 30 chroni darmowe Błogosławieństwo Poszukiwacza.
- Górskie błogosławieństwa sprzedaje tylko kapłan w Durnkarze.
- Okno kapłana z listą błogosławieństw i cenami, okno stanu `/blogoslawienstwa`, wiersz „Błogosławieństwa” w oknie
  postaci, komunikat o śmierci z liczbą błogosławieństw i zmniejszeniem straty.

## [0.249.0] - 2026-11-11

### Zmieniono
- Obszary czarów i run są jak w Tibii: wielka kula ognia, lawina, burza i deszcz kamieni trafiają okrąg (37 pól),
  a nie kwadrat 7x7; eksplozja - krzyż; czary ostateczne (exevo gran mas flam/frigo/vis/tera) - duże okręgi;
  fale (exevo flam hur, exevo gran flam hur, exevo vis hur, exevo tera hur, exevo gran frigo hur) to stożki przed
  postacią; promienie energii to proste linie (5 i 8 pól); ściana energii ma 7 pól, a ściany rzucone po skosie leżą
  ukośnie. Efekt na ekranie i podgląd celownika pokazują dokładnie trafione pola.
- Odnowienia jak w Tibii: czary skupienia blokują inne ataki na 4 s, utito tempo / utamo tempo i czary skupienia mają
  wspólną grupę, cnoty mnicha zajmują też grupę wsparcia. Runy i mikstury dzielą 1 s użycia przedmiotu. Czar ataku
  i czar leczenia nadal można rzucić w tej samej chwili, a combo na pasku rzuca jeden czar na jedno naciśnięcie.
- Skrzynie dają nagrody zależne od trudności miejsca: skrzynie zadań (za drzwiami poziomu, przy bossie, w skarbcach
  zadań) - dużo złota, wybrany dobry przedmiot dla profesji i mikstury; skrzynie w ślepych komorach - mniej złota,
  mikstury i czasem przedmiot. Wiadomość podaje całą nagrodę.

## [0.248.0] - 2026-11-11

### Dodano
- Wyspa Bursztynowa (wyspa najsilniejszych potworów) jest dużo większa. Na nowym lądzie stoi Bursztynowa Skała:
  wieża i urwiska na 3 piętra w górę oraz Bursztynowe Czeluście na 6 pięter w dół - grota morska, zalane jaskinie,
  krypty, kryształy, grobowce w piasku i rdzeń skały. Na każdym piętrze bardzo dużo silnych stad, w ślepych komorach
  skrzynie. Prom pływa jak dawniej.
- Obóz Łowczyń w dżungli na wschód od Varden: palisada z bramą dla poziomu 20+, chaty, chata Matrony i piwnice
  z grotą, w której Matrona Gaju pilnuje małej skrzyni zadania. Mieszkają tam Łowczynie Gaju, Tarczowniczki Gaju
  i Guślarki Gaju (poziomy ok. 18-40, łup i bestiariusz).
- Cyklopolis: ruiny miasta olbrzymów na górzystej wyspie, z Wielką Kuźnią, kuźnią kowala i kamiennymi ruinami.
  Pod miastem Kuźnie Cyklopolis, 6 pięter w dół. Mieszkają tam Jednookie olbrzymy, tragarze i kowale oraz słabsze
  zwierzęta jaskiń. Dopływa tam prom.

### Zmieniono
- Lochy w dziczy są większe i głębsze (2-7 pięter), mają ślepe komory, pętle i różne style pięter (także kryształowe
  groty, zalane jaskinie i ruiny). Część łowisk w dziczy ma nowe nazwy i położenie.

## [0.247.0] - 2026-11-11

### Dodano
- Srebrna Rzeka spod Varden jest teraz szeroka i przy ujściu rozlewa się w deltę: cztery koryta do morza, wyspy,
  łachy piasku, trzciny i pomosty rybackie. W delcie łowi się węgorze. Drogi przechodzą przez odnogi po mostach.
- Wulkan Pożogi: wulkaniczny cypel za deltą, odcięty od lądu pierścieniem lawy - pieszo się tam nie dojdzie.
  Statek Kapitana Ignacego Smolarza pływa na wulkan z Varden, Valmir, Selvaran, Qasirah, Frostholm, Nerys
  i Przystani Wschodniej.
- Pod wulkanem Trzewia Pożogi: 7 pięter w dół, coraz większych, ze ślepymi komorami i skrzyniami, pełnych silnych
  potworów ognia - im głębiej, tym silniejsze i liczniejsze stada. Stożek ma 7 pięter w górę z mieszanymi
  potworami i kraterem lawy na szczycie. To łowisko dla drużyn, nie dla samotnych postaci.
- Nowe potwory: Popielna salamandra, Ogar Pożogi i Magmowy kolos (łup i bestiariusz).

### Zmieniono
- Na rejsy statkami wystarczy teraz konto premium albo promocja (wcześniej tylko premium). Kapitan bez jednego
  i drugiego odmawia i mówi, u kogo je kupić.
- Świat ma piętra od -7 do +7.

## [0.246.0] - 2026-11-11

### Dodano
- Durnkar, krasnoludzkie miasto pod górami na północ od Varden (piętro -5). Wchodzi się ukrytym, krętym wąwozem
  między szczytami i tunelami w dół. W mieście są świątynia z kapłanem, bank z depozytem, karczma, kowal, płatnerz,
  runiarz, narzędziownik, kupiec, zaklinacz, sala tronowa króla, wózkarz do innych miast i 12 domów na sprzedaż.
  Mieszkańcy to krępi, brodaci krasnoludowie.
- W Durnkarze można zamieszkać u kapłana, ale nowa postać nie może w nim zacząć gry.
- Trzy kopalnie krasnoludów, każda do piętra -7: Kopalnia Żelaznej Brody, Kopalnia Głębokiego Kilofa i Stara Sztolnia
  Granitowa. Z każdym piętrem w dół potwory są silniejsze i jest ich więcej. Kopalnia Żelaznej Brody łączy się z
  miastem przez drzwi od poziomu 25.
- Nowe potwory: Czerw gnilny, Krasnolud, Krasnolud górnik, Krasnolud żołnierz, Czerw padlinożerny, Krasnolud strażnik,
  Kamienny golem, Krasnolud geomanta, Żywiołak ziemi i Behemot. Każdy ma łup i wpis w bestiariuszu.

## [0.245.0] - 2026-11-11

### Dodano
- Runy na pasku akcji mają tryb użycia jak w Tibii: na cel, na siebie albo z celownikiem. Tryb zmieniasz w menu pola
  (przytrzymaj pole paska). Runa obszarowa domyślnie leci w cel. Mały znaczek w rogu pola pokazuje wybrany tryb.
- Celownik: naciśnij pole z runą, a potem dotknij albo kliknij pole na mapie, i runa poleci właśnie tam. Wielka kula
  ognia może trafić puste pole obok stada, a cel walki się nie zmienia. Pola w zasięgu runy są lekko podświetlone.
  Na komputerze kursor pokazuje zielone pole (w zasięgu) albo czerwone, a przy runie obszarowej także jej obszar.
  Celownik anulujesz klawiszem Esc, przyciskiem Wstecz, prawym przyciskiem myszy albo tym samym polem paska.
  Zasięg runy to 7 pól, a pole musi być w zasięgu wzroku, jak w Tibii.
- Runa w plecaku ma "Użyj z...": po UŻYJ plecak się zamyka i włącza się celownik.
- Combo: jedno pole paska może trzymać do 4 czarów, np. atak i leczenie. Każde naciśnięcie rzuca pierwszy czar,
  którego można teraz użyć. Jedno naciśnięcie to zawsze najwyżej jeden czar, więc czasy odnowienia i grupy z Tibii
  działają jak przy osobnych klawiszach. Pole combo pokazuje czar, który poleci teraz, i żółte kropki (ile czarów
  ma combo).

### Zmieniono
- Układ paska akcji zapisuje się w postaci (zapis w wersji 9), a nie tylko na urządzeniu. W grze online trafia na
  serwer, więc wraca na każdym urządzeniu. Dawny układ z urządzenia przenosi się sam przy pierwszym wejściu do gry.
- Runa na stworzenie (np. nagła śmierć) użyta celownikiem na puste pole mówi "Tej runy możesz użyć tylko na
  stworzeniu.", a runa lecząca na cudze pole: "Tej runy możesz użyć tylko na sobie." Nieudana próba nic nie zużywa.
## [0.244.0] - 2026-11-11

### Dodano
- Twierdza Krwawego Kła na wschód od Varden: rozpadlina z mostami, mury z wieżami, brama, cytadela, koszary,
  kuźnia, zagroda wargów i obóz orków. Trzy piętra w górę (mury, wieże, wieża strażnicza) i trzy w dół
  (jaskinie, krypty i najgłębsze sale). Im głębiej, tym silniejsze i liczniejsze orki.
- Na najniższym piętrze twierdzy drzwi od poziomu 50 prowadzą do sali Urgotha, Pana Twierdzy (boss), i skarbca
  ze skrzynią: Hełm Pana Twierdzy i 4000 zł, raz dla każdej postaci.
- Nowe orki: Ork, Ork włócznik, Ork jeździec, Ork maruder, Ork przywódca, Kapłan orczego kultu i Fanatyk orczego
  kultu.
- Elandor, osada elfów w lesie na południowy zachód od Varden: Wielkie Drzewo, świątynia, depozyt, łuczarz,
  zielarka i gospoda. Pomosty i mosty linowe w koronach drzew (trzy piętra w górę), krypta, piwnice i grota ze
  źródłem pod ziemią. W osadzie jest bezpiecznie, a w lesie dookoła krążą elfy-wygnańcy: Elf, Zwiadowca elfów
  i Elfi arkanista.
## [0.243.0] - 2026-11-11

### Dodano
- Czary jak w Tibii dla każdej profesji: słowa, poziomy, mana i czasy odnowienia z Tibii. Druid ma 64 czary, czarodziej
  57, mnich 27, paladyn 24, rycerz 23, a 38 jest wspólnych. Są wśród nich fale, promienie, leczenie innego gracza
  (exura sio "imię"), tarcza many, niewidzialność, iluzja, wyzwanie, regeneracja i magiczna lina.
- Przywoływanie stworzeń: utevo res "wilk" i inne (najwyżej 2 sługi). Runa przekonania zamienia stworzenie w sługę,
  a runa ożywienia zwłok robi szkieleta z ciała. Słudzy chodzą za tobą i atakują twój cel.
- Stany jak w Tibii: zatrucie, podpalenie, porażenie, krwawienie, zamrożenie, oślepienie, klątwa i tonięcie.
  Zadają obrażenia co kilka sekund i mają ikonę przy pasku. W opisie ikony jest czar, który leczy stan.
- Leczenie stanów zależy od profesji: exana pox może rzucić każdy (jest też runa odtrutki). Druid leczy porażenie
  (exana vis) i podpalenie (exana flam). Druid i rycerz tamują krwawienie (exana kor). Paladyn zdejmuje klątwę
  (exana mort).
- 61 potworów nakłada stany: pająki, skorpiony i węże zatruwają, ogniste stwory podpalają, nieumarli przeklinają,
  a wilki i krokodyle ranią do krwi.
- Runy jak w Tibii, do kupienia u magów w każdym mieście i w obozie, albo do zrobienia czarem:
  - pola: ognia, trucizny i energii, z czasem trwania jak w Tibii
  - ściany i bomby
  - lawina i wielka kula ognia
  - sopel, stalagmit, burza, deszcz kamieni i ogień duszy
  - magiczna ściana, dziki wzrost i niszczenie pól
  - paraliż
- Pola leżą na ziemi i podpalają, trują albo rażą każdego, kto w nie wejdzie, także potwory.
- Exani hur up i exani hur down działają tylko tam, gdzie w Tibii: w górę spod dziury albo otwartego nieba nad tobą,
  w dół nad dziurą albo krawędzią przed tobą.

### Zmieniono
- Kratki ściekowe w miastach: do kanału schodzisz, dotykając kratki. Samo wejście na kratkę nic nie robi.
- Czary Koła Przeznaczenia odnoszą się do nowych czarów z Tibii.

## [0.242.0] - 2026-11-11

### Dodano
- Okrągła minimapa w nowym wyglądzie: mapa w okrągłej ramce z pakietu Zidane42, na komputerze i telefonie.
  Przyciski + i − na ramce przybliżają i oddalają mapę (6 stopni, zapamiętane w ustawieniach). Dotknięcie mapy
  dalej otwiera dużą mapę. Wygląd klasyczny ma dawną prostokątną minimapę.
- Zakładki w Sklepie: Usługi, Premium i Outfity.
- Zakładka Premium: kupisz w niej konto premium i promocję (te same ceny co u władcy miasta). Widać też, co daje
  premium: rejsy statkami, premię staminy, więcej miejsc na charmy, promocję i Koło Przeznaczenia.
- Zakładka Outfity: darmowe kolory stroju do założenia jednym dotknięciem i lista strojów, które pojawią się wkrótce.

### Zmieniono
- Na telefonie lista walki jest w nowym wyglądzie o 15 dp niższa, bo okrągła minimapa jest wyższa.
## [0.241.0] - 2026-11-10

### Dodano
- 137 nowych przedmiotów narysowanych przez Hinasa (OTLand), z podaniem autora w /autorzy: 11 zestawów od poziomu 20
  do 850 (Kogucie, Cukrowe, Zarazy, Zamieci, Smocze, Białego Słońca, Feniksa, Odmętów, Księżycowe, Anielskie,
  Piekielne) i 8 pojedynczych. Każdy zestaw ma hełm, zbroję, nagolenniki, buty, tarczę, broń rycerza, łuk,
  różdżkę, laskę, księgę czarów i amulet, chroni przed swoim żywiołem, a część ma słabość. Wypadają z potworów
  i bossów, zestaw Kogucie i kilka przedmiotów kupisz u kowala i maga.
- Księgi czarów jako tarcze dla czarodziejów i druidów.

### Poprawiono
- Atlas grafik 32 px był pełny i część obrazków po cichu znikała. Teraz ma 2048 miejsc, a brakujące są liczone w logu.

## [0.240.0] - 2026-11-10

### Dodano
- Nowy wygląd interfejsu z pakietu „Pixel RPG UI Kit” (Zidane42): paski życia, many i doświadczenia w ramkach,
  okna z ramką i czerwonym przyciskiem zamknięcia, pola plecaka, ekwipunku i paska skrótów, zielone, żółte
  i czerwone przyciski, przełączniki wł./wył. w ustawieniach. Działa na komputerze i telefonie.
- Ustawienie „Wygląd interfejsu: nowy / klasyczny” (domyślnie nowy).
- Ramki rzadkości przedmiotów: zwykły, niezwykły, rzadki, epicki, legendarny, mityczny (przedmioty tylko
  z bossów o stopień wyżej).

## [0.239.0] - 2026-11-07

### Zmieniono
- Wszyscy NPC (137) mają nowe postacie złożone z warstw Universal LPC Character Generator (CC BY-SA 3.0).
  Strój zależy od roli: strażnicy w zbrojach z tabardem w kolorze miasta i hełmem miasta, władcy w koronach,
  kapłani w białych szatach, zaklinacze i magowie w kapeluszach czarodzieja, kowal w fartuchu z młotem,
  kapitanowie w trójgraniastych kapeluszach, przewoźnicy jak marynarze. Twarze, włosy, karnacja i kolory
  różnią się między NPC i są stałe. Gracze i potwory zostają w grafice OTSP.
- NPC mają własny atlas grafik, bo atlas 32 px był już pełny (1024/1024).

### Dodano
- Autorzy warstw LPC w oknie /autorzy (sekcja „Postacie NPC”), w assets/art/lpc/CREDITS-LPC.csv
  i assets/art/lpc/LICENSE.txt.

## [0.238.0] - 2026-11-06

### Dodano
- Pupile dają +5% doświadczenia z potworów, gdy chodzą za postacią (też w drużynie i online). Widać to w opisie
  pupila, w komunikacie po przywołaniu i przy poziomie w umiejętnościach („pupil +5%”).
- W każdym z 6 miast nowi NPC:
  - opiekun zwierząt sprzedający pupila tego miasta (pupile zniknęły ze zwykłych sklepów). Nowe pupile: Mewka
    w Valmirze i Papużka w Nerys;
  - władca (król, królowa albo lord) sprzedający premium i promocję tak samo jak król Aurelian w Varden;
  - zaklinacz z imbuementami: kradzież życia (Vampirism), many (Void), krytyk (Strike), obrażenia od żywiołów
    i ochrony. Kowale dalej też je mają.
- /idź znajduje nowych NPC (np. /idź pupil, /idź władca, /idź zaklinacz).

## [0.237.0] - 2026-11-06

### Dodano
- Przycisk „Koło” na górze ekranu: na komputerze zakładka w prawym górnym rogu widoku gry, na telefonie
  dziewiąta ikona na górnym pasku (między Zadaniami a Ustawieniami). Widoczny dla każdej postaci.
- Koło Przeznaczenia jako duże koło w ramce, jak w Tibii:
  - 4 kolorowe ćwiartki i 36 pól jako pierścienie (środek 50, potem 75, 100, 150, 200), wypełnienie każdego pola
    widoczne na kole, znaczki tego, co daje pole (czar, umiejętność, kradzież, naczynie na klejnot);
  - postęp objawień na obręczy i odznaki objawień w rogach;
  - dotknięcie pola pokazuje obok jego kartę z opisem i przyciskami +1, +10, Do pełna, -1, Opróżnij;
  - przyciski Klejnoty, Zestawy, Premie, Wyczyść;
  - postać bez poziomu 51, promocji albo premium widzi zablokowane koło z listą brakujących wymagań.

### Poprawiono
- Układ pól na kole: każde pole leży obok pól, które je odblokowują (wcześniej górne pola czerwonej i fioletowej
  ćwiartki były po złej stronie).

## [0.236.0] - 2026-11-06

### Dodano
- Każde pole Koła Przeznaczenia ma nazwę i dokładny opis działania, jak w Tibii. Lista ćwiartki pokazuje krótki
  efekt (np. „+3 HP/pkt; pełne: +1 walka wręcz”). Dotknięcie pola otwiera kartę z pełnym opisem:
  - co daje każdy punkt oddania, ile daje teraz i ile da pełne pole, oraz co da kolejny punkt;
  - ulepszenie czaru z polską i tibijską nazwą oraz liczbami obu stopni;
  - premie pasywne i naczynia na klejnoty.
  Objawienia mają osobną kartę z etapami 1–3. Wszystkie opisy są liczone z tych samych danych, którymi liczy gra.
- Klejnoty Koła jak w Tibii:
  - mniejsze, zwykłe i większe; wypadają z potworów pod wpływem, diabolicznych i silnych bossów;
  - Pracownia klejnotów w oknie koła: odkrycie modów, blokada, zmiana domeny, rozbicie na okruchy;
  - naczynia w kole aktywują mody klejnotu zależnie od liczby pełnych pól naczyń w domenie (1, 2 lub 3);
  - warsztat okruchów ze stopniami I–IV, a stopień IV daje +1 punkt promocji;
  - mody podstawowe (HP, mana, udźwig, odporności, kradzież...) i najwyższe dla profesji (ulepszenia czarów,
    unik, objawienie);
  - ceny w złocie z Tibii podzielone przez 4, jak reszta ekonomii;
  - klejnoty wkłada się i wyjmuje w świątyni.

## [0.235.0] - 2026-11-06

### Dodano
- Harmonia mnicha widoczna nad postacią jak w Tibii: 5 małych kwadratów pod paskami życia i many (złote = punkty
  harmonii) oraz niebieski romb, gdy mnich jest w stanie Serene. Widać to u własnej postaci i u innych mnichów
  w grze online. Kule harmonii w HUD zostają (tam widać Cnotę i tam się ją wybiera).

### Zmieniono
- Czaszka PvP nad mnichem przesunięta pod kwadraty harmonii.

## [0.234.0] - 2026-11-06

### Dodano
- Koło Przeznaczenia (Wheel of Destiny) jak w Tibii dla wszystkich 5 profesji:
  - od poziomu 51, z promocją i premium; 1 punkt za każdy poziom od 51;
  - 36 pól w 4 ćwiartkach, pojemności, sąsiedztwo i progi z Tibii; punkty przestawia się w świątyni
    (obok kapłana; zapisanie zestawu działa wszędzie), czyszczenie koła za darmo; 3 zestawy;
  - oddanie (HP, mana, udźwig, ochrona za każdy punkt), przekonanie (umiejętność, kradzież życia i many,
    wzmocnienia czarów profesji, pasywne premie);
  - objawienia w każdej ćwiartce (250/500/1000 punktów): Dar Życia, awatar profesji na 15 s
    (Stali, Światła, Natury, Burzy, Równowagi) i czary z Tibii, m.in. Divine Grenade, Great Death Beam,
    Divine Empowerment, Terra i Ice Burst, Combat Mastery, Drain Body;
  - okno na komputerze z rysowanym kołem i na telefonie jako 4 kafelki ćwiartek. Otwiera się klawiszem U,
    poleceniem /koło, z menu Opcje albo u kapłana.
- Kamienie (Gem Atelier) jeszcze nie działają, będą w kolejnej wersji.

## [0.233.0] - 2026-11-06

### Dodano
- Kuźnia Wyniesienia jak w Tibii, u każdego z 6 kowali (nowa strona „Kuźnia” w oknie kowala):
  - klasy przedmiotów 1–4 i tiery (do T10 dla klasy 4), widoczne w nazwie, na slocie i w opisie przedmiotu;
  - fuzja (50% szansy, 65% z rdzeniem; przy porażce drugi przedmiot traci tier, rdzeń ochrony zmniejsza ryzyko),
    transfer tieru, wymiana 60 pyłu na 3 odłamki i 50 odłamków na 1 rdzeń, podnoszenie limitu pyłu (100 do 225);
  - koszt w złocie liczony z wartości przedmiotu (dopasowany do naszej ekonomii).
- Efekty tierów jak w Tibii: Onslaught (broń, +60% obrażeń), Ruse (zbroja, unik ciosu), Momentum (hełm, szybsze
  odnawianie czarów), Transcendence (nogawice, krótki awatar z pewnymi krytykami), Amplification (buty, wzmacnia
  pozostałe). 5 nowych par butów, m.in. otchłanne trzewiki od Mor'gatha.
- Potwory pod wpływem (etapy 1–5, fioletowa kula z kropkami, około 3% potworów) i diaboliczne (korona, 4 naraz,
  każdy na 1 godzinę, widoczne na minimapie) z mnożnikami z Tibii. Zastępują dawne elitarne potwory.
  Dają pył kuźni, a diaboliczne także odłamki.
- Imbuementy u kowala: 14 rodzajów (np. Vampirism, Void, Strike, żywiołowe obrażenia, ochrony) na 3 poziomach
  z wartościami z Tibii. Działają 20 godzin czasu walki.

### Zmieniono
- Zakładka kowala pokazuje się już tylko u kowali, a nie u każdego kupca. Ulepszenia i zaklęcia też wymagają kowala.
- Zapis postaci w wersji 8 (tiery przedmiotów, pył). Rynek zachowuje tier i ulepszenie.

## [0.232.0] - 2026-11-06

### Dodano
- Bestiary jak w Tibii: 20 klas potworów, gwiazdki trudności i progi zabójstw z Tibii (np. średnie 50/500/1000).
  Cztery etapy odkrywania: podstawy, potem zdrowie, doświadczenie i pospolity łup, potem odporności, miejsca
  i rzadszy łup, a na koniec cały łup i punkty charmów. Łup jest opisany kategoriami rzadkości jak w Tibii
  zamiast procentów.
- Charmy jak w obecnej Tibii: wszystkie 25 charmów, 14 Major za punkty charmów i 11 Minor za echa, poziomy I/II/III.
  Na stworzeniu może być 1 Major i 1 Minor; sloty: 2 bez premium, 6 z premium. Major wymaga ukończonego wpisu.
  Działają w walce: żywiołowe (Rana, Podpalenie, Trucizna, Zamrożenie, Porażenie, Klątwa, Boski gniew), Niski
  i Dziki cios (krytyki), kradzież życia i many, Unik, Riposta, Okaleczenie, Zastrzyk adrenaliny, Błogosławieństwo
  (mniej straty doświadczenia) i inne. Zadziałanie charmu widać jako fioletową liczbę z nazwą.
- Zdjęcie charmu kosztuje złoto (ceny z Tibii podzielone przez 4, jak reszta ekonomii).

## [0.231.0] - 2026-11-05

### Zmieniono
- Odporności potworów jak w Tibii: każdy potwór ma odporności, słabości i odporności całkowite swojego odpowiednika
  z Tibii (94 potwory zmienione) oraz klasę z Bestiary. Nieumarli są odporni na śmierć, utonięcie i wysysanie życia
  i słabi na święte. Żywiołaki są odporne na własny żywioł. Lodowe potwory nie są już z automatu słabe na ogień.
- Krytyki jak w obecnej Tibii: każdy ma 5% szansy na +10% obrażeń. Przedmioty mogą dawać szansę i siłę krytyka.
  Zaklęcie kowala „krytyk” działa jak Powerful Strike (+10% szansy, +50% obrażeń). Krytyki działają też w PvP.
  Krytyk widać jako większą pomarańczową liczbę z wykrzyknikiem.
- Kradzież życia i many wzorem z Tibii (przy wielu celach mniej na cel).

### Dodano
- Ochrona przed żywiołami na przedmiotach, jak w Tibii (np. obsydianowy zestaw: ogień +, lód −; koralowy hełm:
  utonięcie 100%). Działa na obrażenia od potworów i graczy. Widać ją w opisie przedmiotu i w oknie umiejętności
  (wiersze Krytyk, Kradzież, Ochrona).
- Nowe rodzaje obrażeń: utonięcie, wysysanie życia (omija pancerz) i wysysanie many (niebieska liczba).
  21 potworów ma dodatkowy atak wysysający życie albo manę.

## [0.230.0] - 2026-11-05

### Dodano
- Harmonia mnicha jak w Tibii (liczby z serwera Canary): 0–5 punktów harmonii widocznych w HUD jako kule.
  Czary budujące dają +1 punkt, czary kończące zużywają wszystkie i biją mocniej (do x2,28 przy 5 punktach,
  x3,56 z Cnotą Harmonii w stanie Serene). Każde zbudowanie albo zużycie leczy mnicha lub najbardziej rannego
  członka drużyny.
- Stan Serene (niebieska poświata): mnich traci go tylko w drużynie, gdy otacza go 6 lub więcej potworów.
- Trzy Cnoty od poziomu 20 (jedna naraz): Harmonii (silniejsze czary kończące i zwrot punktu), Sprawiedliwości
  (pięść +15%, +30% w Serene) i Wytrwałości (leczenie x1,35, x1,70 w Serene). Wybór dotknięciem kul harmonii.
- Czary Focus Serenity (utamo tio, poz. 150) i Focus Harmony (utevo nia, poz. 275): pełna harmonia.

### Zmieniono
- Czary mnicha przebudowane na role z Tibii (budujące, kończące, leczenie) z poziomami, maną i czasem odnowienia
  z Tibii. Stare czary mnicha zastąpione odpowiednikami z Tibii. Na niskich poziomach mnich ma dodatkową premię
  do obrażeń (zanika do poziomu 40), żeby polował tak szybko jak rycerz.
- Harmonia i Cnota zapisują się z postacią; śmierć zeruje harmonię.

## [0.229.0] - 2026-11-05

### Usunięto
- Zbędne rozmowy z NPC: zniknęły ogólne pogawędki („cześć”, „imię”, „żegnaj”, „noc”) u wszystkich NPC oraz tematy,
  które powtarzały to, co już pokazują przyciski okna (handel, bank, leczenie, błogosławieństwo, podróże, rejsy,
  zadania, ceny u króla). Zostało 7 tematów z informacjami potrzebnymi do gry (np. kopalnia w Varden, Lodowe Jaskinie,
  most na poziom 200, Otchłań).

### Zmieniono
- Okno NPC: od razu przyciski funkcji pod jednym zdaniem powitania. Inne słowo niż temat = NPC odpowiada powitaniem.
- Strażnik nie otwiera już pustego okna: dotknięcie pokazuje tylko jego powitanie na czacie.
- Wymóg poziomu 20 do promocji jest teraz widoczny na przycisku u króla.

## [0.228.0] - 2026-11-05

### Dodano
- Mgła na mapie jak w Tibii: nowa postać widzi całą mapę (minimapę i dużą mapę) zakrytą, a teren odsłania się
  wokół niej w miarę chodzenia (widok 17x13 pól, osobno na każdym piętrze i pod ziemią). Odkryta mapa zapisuje się
  osobno dla każdej postaci, także w grze online (serwer wysyła ją po zalogowaniu).
- Znaczniki na mapie widać tylko na odkrytych polach; dotknięcie nieodkrytego miejsca na dużej mapie pokazuje
  „Nie znasz tej okolicy.”.

### Zmieniono
- Zapis postaci w wersji 7 (pole z odkrytą mapą). Starsze postacie dostają odkrytą okolicę swojego miasta.

## [0.227.0] - 2026-11-05

### Zmieniono
- Przycisk Tarcza zastąpiony 3 trybami walki jak w starej Tibii (wartości z serwera TFS), przełączanymi dotknięciem:
  Ofensywa (miecz: +20% obrażeń bronią, słabsza obrona tarczą), Balans (miecz z tarczą, domyślny), Obrona (tarcza:
  obrażenia bronią o połowę mniejsze niż w ofensywie, pełna obrona tarczą). Czary i różdżki bez zmian, jak w Tibii.

### Dodano
- Telefon: dwa nowe miejsca na miksturę albo czar w prawym dolnym zestawie – trzecie kółko obok dwóch mikstur
  i jedno w rogu na środku łuków. Ustawia się je przytrzymaniem, jak pozostałe.

## [0.226.0] - 2026-11-05

### Dodano
- 4 warianty sterowania do wyboru w ustawieniach („Sterowanie”) i suwak „Czułość” 1–5:
  - Krzyżyk (domyślny): stały krzyżyk ze strzałkami i skosami w rogach, trzymanie = chód pole po polu;
  - Pływający joystick (4 kierunki): gałka pojawia się pod kciukiem, skos dopiero po przytrzymaniu w ukosie,
    kierunek zablokowany na całe pole;
  - Przytrzymanie na mapie: trzymasz palec na ekranie, postać idzie w jego stronę (krótkie dotknięcie działa jak dotąd);
  - Klasyczny joystick: dotychczasowa gałka.

### Zmieniono
- Dotknięcie mapy pokazuje delikatną białą kropkę, która rośnie i znika (zamiast X); w czasie marszu mała kropka
  w miejscu docelowym; gdy nie da się dojść – krótka czerwonawa kropka.

## [0.225.0] - 2026-11-05

### Zmieniono
- Przy staminie 0:00 potwory nie dają łupu ani expa (jak w Tibii): ciało jest puste, w dzienniku „brak staminy - bez
  expa i łupu”. W wyprawie na bossa uczestnik z pustą staminą zalicza zadanie, ale nie dostaje łupu.
- Stamina odnawia się o 1/3 szybciej niż w Tibii: 1 minuta co 2 min 15 s offline (powyżej 39:00 co 4 min 30 s).
  Pełne odnowienie 0:00 -> 42:00 trwa ok. 101 h zamiast 135 h. Sen w łóżku działa tak samo.

## [0.224.0] - 2026-11-05

### Dodano
- Wybór miasta startowego przy tworzeniu postaci, jak w Tibii: Varden, Frostholm, Selvaran, Qasirah, Valmir albo Nerys,
  każde z opisem i podglądem mapy. Nowa postać zaczyna w świątyni wybranego miasta i ma je jako miasto domowe.
  W grze online wybór miasta przy zakładaniu nowego konta.
- Zmiana miasta jak dotąd u kapłana w świątyni („Zamieszkaj w ...”).

## [0.223.0] - 2026-11-05

### Zmieniono
- Trening na manekinie ok. 2/3 tempa expienia: cios co 2,5 s, 15 prób broni i 20 prób tarczy na cios (z przelicznikiem
  serwera). Knight miecz 10 -> 60 w ok. 16 minut zamiast 4,8 godziny. Komunikat pokazuje tempo w próbach na minutę.
- Trening zaczyna i kończy się dotknięciem manekina (bez przycisku Użyj); postać obraca się do manekina.
- Manekin ma nowy wygląd: większy, słomiany na drewnianej podstawie, kołysze się po ciosie.

## [0.222.0] - 2026-11-05

### Zmieniono
- Skille z expienia rosną jak na serwerze z przelicznikami RubinOT (tabela przeliczników bez zmian): knight na
  poziomie 100 ma ok. 82/77 (broń/tarcza), paladin dystans ok. 83, sorcerer i druid magic level ok. 72.
  Każde uderzenie, strzał i punkt many liczą się jak 30 prób, a tarcza jak 90 prób za cios potwora.
- Tarcza ćwiczy się od każdego ciosu wręcz (także żywiołowego) i od pocisków fizycznych; czary potworów się nie liczą.
- Postacie tworzone od razu na wysokim poziomie (start offline na 300) mają skille pasujące do tego tempa, np. knight
  300: broń 106, tarcza 105; sorcerer 300: magic level 114.

## [0.221.0] - 2026-11-04

### Zmieniono
- Potwory zbalansowane według Tibii (TibiaWiki): poziom, życie, obrażenia i exp pasują do poziomu postaci, np.
  orkowie i krokodyle dla poziomu 15–30 zamiast 55–150; złoto z łupu przeliczone do nowego poziomu.
- Poziom potworów rośnie stopniowo z odległością od miast; przy miastach tylko słabe potwory (do poziomu 15).
- Dojścia do słabych expowisk bez silnych potworów, a drogi do silnych mają po drodze coraz mocniejsze potwory.
- Exp za questy przeliczany tak jak za potwory (np. x50 na poziomie 1–8).
- Świat wygenerowany od nowa: układ jaskiń i pięter częściowo się zmienił.

## [0.220.0] - 2026-11-04

### Zmieniono
- Skille i magic level liczone jak w Tibii osobno dla każdej profesji: knight szybko skilluje broń i tarczę, paladin
  dystans, sorcerer i druid magic level; mag z mieczem skilluje bardzo wolno, knight z magią także.
- Przeliczniki etapowe jak na RubinOT:
  - exp za potwory od x50 (poziom 1–8) i x80 (9–50), malejący do x3 przy poziomie 901–1000;
  - skille x10 do 80, x7 do 100, x4 do 120, potem x2;
  - magic x10 do 80, x7 do 100, x4 do 120, x3 do 130, potem x2.
- Okno umiejętności pokazuje aktualny przelicznik, np. „Poziom 8 (exp x50)”, a przy skillach „x10”.
- Tarczę skilluje się tylko z tarczą w ręce (mnich bez tarczy). Jedna mana wydana na czar to jedna próba magii.
- Zadania dzienne, łowieckie i wyzwania dają exp przeliczony tak jak za potwory.

## [0.219.0] - 2026-11-03

### Dodano
- 66 nowych wejść do podziemi w całym świecie (31 jaskiń, 19 kopalni, 16 dziur): w sumie 131. Z każdej drogi
  najbliższe wejście jest zwykle w odległości około 23 kratek, najdalej 73. Wejścia widać z daleka (skalny występ,
  drewniana rama kopalni z pochodnią, dziura w ruinach).
- W środku 1–3 piętra po 5–8 sal, potwory dobrane do regionu i silniejsze głębiej, na końcu skrzynie z łupem.
- Na powierzchni prawie dwa razy więcej potworów w lasach i na łąkach (351 miejsc z potworami poziomu 1–15,
  232 z poziomu 16–40), z dala od miast i dróg.
- Skały i góry jako tarasy 3D: wyższe, schodkowe, z zaokrąglonymi brzegami, ścianami i cieniem.
- Mosty z poręczami, słupkami, palami w wodzie i rampą na brzeg; mosty linowe z linami.
- Morze za krawędzią świata przechodzi w głęboką wodę zamiast czarnych kwadratów; pod ziemią pustka znika w mgle.
  Przepaście mają skalny brzeg.

### Zmieniono
- Dojścia do questów dla 5 profesji dużo dłuższe i trudniejsze: Grobowiec Pięciu Profesji 3 piętra po 13–21 sal
  (ok. 1500 kratek zamiast 227, 59 grup potworów zamiast 15), leża bossów po 17 sal z potworami w każdej
  (ok. 400–460 kratek zamiast ok. 100).

## [0.218.0] - 2026-11-03

### Dodano
- Łóżka w domach na sprzedaż (1–4 w domu). Dotknięcie łóżka we własnym domu (albo jako gość) kładzie postać spać
  i wylogowuje; po powrocie postać jest wypoczęta (2% HP i many na minutę snu). Obcy: „To nie twoje łóżko.”
- Tabliczka z monetą nad drzwiami każdego wolnego domu i napis „Na sprzedaż: cena” z bliska.
- 71 domów na sprzedaż: w miastach, przy bramach, po 2 w czterech wioskach i 6 chat w dziczy.

### Zmieniono
- Kupno domu działa: płaci się najpierw z konta w banku, resztę monetami z plecaka. Okno domu pokazuje liczbę łóżek
  i ile masz złota.
- Niższe ceny domów: 1000 gp za kratkę w mieście, 2000 w dzielnicy królewskiej Varden, 800 przy bramach i w dziczy,
  600 we wsiach (czynsz tygodniowy 2% ceny).

### Naprawiono
- Willa 10 w Nerys nie miała drzwi (zamalowała je boczna uliczka).

## [0.217.0] - 2026-10-29

### Dodano
- Przycisk „Podążaj” w dolnym rzędzie za Tarczą: gdy włączony, postać idzie za zaznaczonym potworem.
- Ikona „Sklep” u góry obok Wyloguj: premium, promocja, błogosławieństwo, wzmocnienia polowania, koń i zwierzaki
  kupowane złotem z banku, bez chodzenia do NPC.
- Dotknięcie powiększonej mapy prowadzi postać w to miejsce, jeśli da się tam dojść.
- Umiejętności Topór i Maczuga osobno od Miecza (każda broń ćwiczy swoją umiejętność).
- Stamina jak w Tibii: 42 h, premia za doświadczenie przy 39–42 h dla premium, połowa doświadczenia poniżej 14 h,
  odnawia się po wylogowaniu.
- Krzaki z jagodami i malinami: kilka dotknięć daje krótkie nasycenie, potem krzak odrasta po godzinie.

### Zmieniono
- Nick nad paskiem życia wyżej, nie nachodzi na pasek.
- Drzwi otwierają się tylko po dotknięciu, nie po wejściu w nie. Dotknięcie przedmiotu go używa (np. drabina).
- Drzwi poziomu zamykają się za postacią, więc niższy poziom nie przejdzie za kimś.
- Potwory idą do gracza, gdy go widzą, także skosem i dookoła przeszkód; potwory dystansowe trzymają odstęp.
- Wędka wystarczy w ekwipunku, robaki w plecaku (1 robak na zarzut). Pole na robaki usunięte.
- Szersza minimapa i lista Walka pod nią, większe wiersze łatwiejsze do dotknięcia.
- Dach domu znika, gdy podchodzi się do drzwi lub ściany domu.
- Czasy nasycenia jedzenia zbliżone do Tibii.

## [0.216.0] - 2026-10-29

### Zmieniono
- Monety same się łączą jak w Tibii: 100 złotych = 1 platynowa, 100 platynowych = 1 kryształowa (łup, zakupy, reszta,
  bank, depozyt, stare zapisy). Stosy monet rysowane jako kupka.
- Pochodnie: kupuje się je w sklepie albo wypadają z goblinów, bandytów, szkieletów i stonóg. „Załóż” wkłada pochodnię
  do nowego pola obok butów; dotknięcie pola zapala ją lub gasi. Pali się 20 minut, a zgaszona się nie wypala.
- Chodzenie po dotknięciu mapy w 4 kierunkach; skosem tylko przy przeciskaniu się między drzewami, krzakami
  i skałami (jak w Tibii). Mur i woda w rogu nadal blokują skos.
- Joystick mniej czuły: większa martwa strefa, skos tylko przy wyraźnym wychyleniu, bez skakania kierunków.
- Nad paskami własnej postaci jej nick, pod paskami czaszka PvP (także u innych graczy).

## [0.215.0] - 2026-10-29

### Dodano
- Nowy układ ekranu na telefonach i tabletach (wariant B według makiety): obraz gry na całym ekranie, u góry jedna ramka
  z poziomem, paskami HP i many oraz ikonami Plecak, Ekwipunek, Umiejętności, VIP, Zadania, Ustawienia, Wyloguj.
  Ikona otwiera okno na środku ekranu z krzyżykiem. Pod minimapą lista Walka. W prawym dolnym rogu 5 czarów na dużym
  łuku i 2 mikstury na małym; dłuższe przytrzymanie przycisku otwiera wybór czaru, runy lub mikstury profesji.
  Obok w rzędzie Atak, Tarcza, PvP. Czat schowany pod zakładką „Czat”. Bieg i Pochodnia są w oknie Ustawienia.
  Wylogowanie pyta o potwierdzenie. Na komputerze układ bez zmian (podgląd: -Dpixel.mobileHud=1).

## [0.214.0] - 2026-10-29

### Zmieniono
- Ekran na telefonach: przyciski, pasek czarów i pola ekwipunku 36-40 dp, napisy co najmniej 7 dp, krótsze podpisy
  (Mów, Weź, Łów, Poch.), okno Walka mieści 5-6 pozycji, komunikaty systemowe jako 1-2 znikające linie
  (pełny tekst w zakładce Serwer), umiejętności rozwinięte domyślnie, VIP zajmuje tyle miejsca, ile potrzebuje.
  Na wąskich telefonach Wyjdź i Pochodnia są w menu Opcje. Nicki nad postaciami większe, z obwódką.
- Naprawione rozjeżdżające się litery przy ułamkowej skali czcionki.

## [0.213.0] - 2026-10-29

### Zmieniono
- Nowy układ ekranu jak w Tibii: po lewej minimapa, lista VIP i rozwijane okno umiejętności; u góry paski HP i many
  z poziomem i paskiem doświadczenia; po prawej ekwipunek z polem wzmocnień i czaszki obok butów, kolumny przycisków
  (Bieg, Tarcza, Zadania, Pochodnia | Wyjdź, PvP, Opcje), większe okno Walka i większy pasek akcji.
  Paski HP nad postaciami i potworami, pod własnym HP pasek many. Mniejszy, ledwo widoczny joystick.
- Okienko gracza po stuknięciu: profesja, poziom, gildia, wiadomość, znajomi, zaproszenie do drużyny, handel, pojedynek.
- Doba: 3 godziny dnia i 3 godziny nocy.
- PvP i drużyny według zasad Tibii: przełącznik PvP (bezpieczny tryb), czaszki biała, żółta, pomarańczowa,
  czerwona (3/5/10 zabójstw w dobę/tydzień/miesiąc, 30 dni) i czarna (6/10/20, 45 dni), blokada PZ 1 min
  po ataku i 15 min po zabójstwie, blokada wylogowania, obrażenia między graczami 50%.
  Wspólne doświadczenie: premia 20/30/60/100% za 1/2/3/4+ profesje, poziomy w zakresie 2/3, do 30 pól od lidera,
  aktywność w ostatnich 2 min.

### Dodano
- Stada potworów po 3-6 sztuk na lepszych łowiskach i w głębokich lochach (około 3800 potworów zamiast 2300).
- Lista znajomych (VIP) i informacje o graczach z serwera.

## [0.212.0] - 2026-10-28

### Dodano
- 36 nowych potworów (32 zwykłe i 4 bossy) od poziomu 9 do 930, z własnymi nazwami i opisami. Statystyki
  wzorowane na proporcjach z serwera Canary, grafiki wyłącznie z OpenTibia Sprite Pack (CC BY 4.0).
  Nowi bossowie: Wroniec, Kruczy Kniaź (32), Szlamiara, Matka Topieli (95), Velmira, Szkarłatna Pani (290)
  i Suweren Ostrzy (640). Rozstawione w strefach klimatu, na wyspach, w tunelach, kopalniach, jaskiniach i lochach.
- Nowe materiały z potworów (czarne pióro, śluz czerwia, płytka chitynu, ektoplazma, odłamek gargulca)
  i dwa przedmioty tylko z bossów (kaptur z kruczych piór, różdżka z bagiennego drewna).

## [0.211.0] - 2026-10-28

### Dodano
- Grobowiec Pięciu Profesji pod pustynią Qasirah (od poziomu 20): trzy piętra w dół, na -3 sala z 5 ołtarzami.
  Właściwa profesja na kwadracie przed ołtarzem zapala pochodnię na ścianie; przy 5 pochodniach dźwignia na prawo
  od kwadratu knighta przenosi drużynę do skarbca, gdzie każdy otwiera skrzynię swojej profesji (20 000 gp, raz na postać).
- Drzwi poziomu ze złotą tabliczką: po stuknięciu mówią, od którego poziomu można wejść, i nie puszczają niższych.
- Cztery wyprawy na bossa w 5 postaci: Leże Grimtootha (30+), Lodowy Tron Ysolde (100+), Krypta Neferakha (200+),
  Kuźnia Ignarotha (400+). Dźwignia działa przy 5 postaciach na kwadratach, arena z bossem, osobny losowy łup dla
  każdego uczestnika, każda wyprawa raz na postać.

### Naprawiono
- Niestabilny test wspólnych potworów na serwerze i różnice w zrzutach GUI zależne od szybkości komputera.

## [0.210.0] - 2026-10-28

### Dodano
- Pochodnie i latarenki wiszą na ścianach (724 światła przeniesione z podłogi). Stuknięcie z sąsiedniej kratki
  zapala lub gasi światło, nic nie trafia do plecaka; stan zapisuje się w postaci i na serwerze.
- Zakładki czatu jak w Tibii: Lokalny, Serwer, Handel, Gildia, Drużyna i prywatne rozmowy; nieprzeczytane na czerwono,
  wpisany tekst idzie na kanał zakładki. Przyciski ATAK, UŻYJ, KSIĘGA i reszta stoją obok ekwipunku.
- Okno domu przy drzwiach: stan, cena, czynsz tygodniowy, przycisk Kup. Okno właściciela od środka: zaproś z pełnym
  dostępem albo tylko wejściem, lista gości, opuść dom. Czynsz (2% ceny tygodniowo) schodzi z banku, po 2 zaległościach
  dom wraca do miasta, a rzeczy idą do depo.
- Łowienie wymaga wędki i robaków w gnieździe amunicji; każde zarzucenie zużywa robaka (1 gp u kupców z plecakami).
- Reguły łupu: złoto i wartość przedmiotów według poziomu potwora, rzadsze drogie rzeczy, 35 przedmiotów tylko z bossów
  (1-5%), osobny rzut łupu bossa dla każdego gracza.

### Naprawiono
- Od 0.209.0 obraz w nocy i pod ziemią był prawie biały (przycinanie widoku przy rysowaniu świateł).

## [0.209.0] - 2026-10-28

### Zmieniono
- Okno gry jak w Tibii: 15 x 11 kratek, postać zawsze w środku (7 kratek w bok, 5 w górę i w dół).
  Reszta ramki widoku jest czarna.
- Zasięg czarów na cel, run, łuków, kusz, różdżek i broni rzucanej: 7 kratek, czyli do krawędzi ekranu.
- Potwory zauważają gracza z 7-9 kratek (za plecami o 2 mniej), więc w pionie także spoza ekranu.

### Dodano
- Dojście na każdą wyspę: kładki i mosty nad wąskimi cieśninami, tunele pod morzem ze schodami na obu
  brzegach i przewoźnicy z łodziami (rejs do najbliższych portów, konto premium).
- Potwory na wyspach według odległości od Varden: od dzików i bandytów (10-40) przez krokodyle i orki,
  mumie i strażników świątyń, aż po topielców, piratów i kraby koralowe (280-420). W tunelach też żyją potwory.
- Lochy Zapomnianych pod wielką wyspą na wschodzie: cztery piętra kamiennych sal i korytarzy z kośćmi
  i pajęczynami, upiorni rycerze i łowcy cieni, kapłani pustki, kościane smoki i władcy otchłani (poziomy 350-960).

## [0.208.0] - 2026-10-28

### Dodano
- Skrzynie, beczki, worki i kufry można przesuwać: wejdź w nie, a przesuną się o pole dalej (do 6 pól od
  swojego miejsca). Dotknięcie albo UŻYJ otwiera je, drugie dotknięcie zamyka. W środku jest 8 miejsc na
  rzeczy z plecaka. Online skrzynie są wspólne dla wszystkich graczy i zostają po restarcie serwera.
  Skrzynie w cudzym domu są zamknięte.
- Goście w domach: `/dom wpuść Imię` wpuszcza gracza do domu (wchodzi do środka i korzysta ze skrzyń),
  `/dom wyrzuć Imię` odbiera wstęp, `/dom` pokazuje dom i gości.
- Sprzedaż domu innemu graczowi: `/dom sprzedaj Imię, 100000`, kupujący wpisuje `/dom kup` i płaci z banku.
  Zarządca domów odkupuje dom za połowę ceny.

### Zmieniono
- Miasta mają nieregularny kształt: falisty mur, kręte ulice, budynki różnej wielkości ustawione wzdłuż ulic
  (także domy w kształcie litery L), sklepy rozrzucone po mieście. Przebudowane: Valmir, Nerys, Qasirah,
  Frostholm, Selvaran i dzielnica królewska Varden (pałac z tronem). Stare Varden zostało bez zmian.
- Sklepy mają ladę przez całą szerokość: kupiec stoi za nią, towary leżą na ladzie i na półkach.
- Kratki ściekowe w 7-9 miejscach w każdym mieście, rozrzucone po ulicach; kanały ciągną się pod większą
  częścią miasta.

## [0.207.0] - 2026-10-28

### Dodano
- Towary na ladach sklepów: przed kupcami, kowalami, magami i karczmarzami leżą na ladach i w gablotach
  przedmioty, które sprzedają. Są tylko do oglądania, nie da się ich podnieść ani wejść na ladę.
- Stawy i jeziorka w losowych miejscach (170): z trzciną przy brzegu, zamarznięte na lodach, oazy z palmami
  na pustyni.
- Roślinność dopasowana do klimatu z paczki OTSP: drzewa, krzewy i paprocie dżungli, oszronione świerki
  i krzaki na lodach, suche krzewy na pustyni, jesienne drzewa i grzyby w lasach, zwęglone drzewa na
  ziemiach wulkanu.
- Ozdoby w miastach: donice, skrzynki z kwiatami, beczki, ławki i dzbany pod domami, kwiaty na trawnikach.

### Zmieniono
- Drogi kręcą się po terenie zamiast biec prostymi odcinkami.
- Opuszczone i zajęte wioski wyglądają na napadnięte: spalona ziemia, zwęglone belki i drzewa, gruz,
  krew i ciała, domy z dziurami w murach.
- Podłogi w całym świecie z paczki WsT: kamień, marmur, cegła, piaskowiec, lód, posadzka domów, grobowce,
  jaskinie i popiół.

## [0.206.0] - 2026-10-27

### Dodano
- Nowy świat 1000x1000 pól według mapy Darka: kontynent z pustynią na zachodzie, dżunglą na południowym
  wschodzie, lodowymi szczytami na północy, wulkanem w północno-zachodnich Czarnych Szczytach i wyspami.
- Dwa nowe miasta: port Valmir na południu i wyspiarskie Nerys na Wyspach Szmaragdowych, każde ze świątynią,
  bankiem, kupcami, kowalem, magiem, karczmą, domami do wynajęcia, strażą i kapitanem statku.
- Kanały pod wszystkimi sześcioma miastami: kratki ściekowe na ulicach prowadzą w dół, drabina pod kratką
  wyprowadza z powrotem. W kanałach żyją szczury kanałowe i wielkie szczury (poziomy 3-20).
- Kopalnie pod lub obok każdego miasta (Stara Kopalnia, Kopalnia Lodu, Soli, Szmaragdów, Miedzi, Pereł).
- Otchłań Wulkanu: pięć pięter pod wulkanem z demonami i najsilniejszymi potworami (poziomy 320-960).
- Więcej expowisk i jaskiń (15 jaskiń, 19 wiosek), nowe trasy statków według tabeli z mapy i przystanie.

### Zmieniono
- Zapisane postacie ze starego świata pojawiają się w świątyni swojego miasta. Zadania, przedmioty i domy
  zostają.
- Na murach w podziemiach nie wiszą już obrazy ani gobeliny.

## [0.205.0] - 2026-10-27

### Poprawiono
- Mury budynków tworzą zamknięte bryły: w rogu północno-wschodnim nie wystaje już kawałek muru ponad linię
  ściany i nie brakuje ostatniego odcinka górnej ściany. Drzwi w ścianie poziomej przy murze wewnętrznym
  stoją w dobrą stronę.

## [0.204.0] - 2026-10-24

### Dodano
- Drzwi i okna w domach otwiera się dotknięciem pola obok, a drugie dotknięcie je zamyka, z krótką animacją.
  Wszystkie drzwi są na początku zamknięte, postać otwiera je sama, gdy w nie wchodzi, a potwory nie
  przechodzą przez zamknięte drzwi. Nie da się zamknąć drzwi, w których ktoś stoi.
- Skrzynie skarbów: nie można na nie wejść. Otwiera się je dotknięciem z sąsiedniego pola (z daleka postać
  najpierw podchodzi), z animacją otwarcia wieka i złotem w środku.
- Więcej mebli z paczki OTSP: nowe krzesła i taborety, komody, beczki i baryłki na boku, a na ścianach wnętrz
  obrazy, gobeliny i tkaniny.
- Sześć nowych odmian murów z paczki (zamkowy z blankami, gładki i łupkowy kamień, ciemny i biały mur
  szachulcowy, beżowe panele). Odmiana jest wybierana dla całego budynku, więc sąsiednie domy się różnią.
- Panele okien z kupionej paczki WsT dobrane do miejsca: plecak, ekwipunek i rozmowa z NPC drewniane, lista
  walki żelazna, umiejętności i czat klasyczne, księga czarów magiczna, handel i targ złote, osiągnięcia
  królewskie, a mapa i pozostałe okna kamienne.

### Zmieniono
- Konsola na dole pokazuje tylko czat. Pozostałe zdarzenia (łup, osiągnięcia, ostrzeżenia) pojawiają się
  na kilka sekund jako napis na dole widoku gry, jak w Tibii.
- Meble i ozdoby większe niż jedno pole są rysowane w naturalnym rozmiarze zamiast ściśniętych do 32 px.
  Łóżko jest całe (wcześniej było widać tylko połowę), a stół i ołtarz to pełne meble zamiast rogu
  większego stołu.

## [0.203.0] - 2026-10-24

### Zmieniono
- Ekwipunek stoi równo z lewą krawędzią minimapy, a przyciski są niżej, przy prawej krawędzi panelu.
- Przyciski mają piktogramy zamiast napisów: księga (diagnostyka), buty (bieg), tarcza (postawa obronna)
  i pochodnia.

## [0.202.0] - 2026-10-24

### Dodano
- Ikony z kupionej paczki WsT "Alchemy & Potion Icons": wszystkie mikstury zdrowia, many i ducha, esencje,
  róg z prochem, ziele lecznicze i zwój nauki.
- Cztery mikstury wzmacniające w sklepach magicznych (10 minut polowania na łyk, do 30 minut zapasu):
  siły (+15% obrażeń), kamiennej skóry (-15% otrzymywanych obrażeń), szybkości (krótszy krok) i szczęścia
  (+30% szans na łup).
- Ramki pól z kupionej paczki WsT "Icon Frames & UI Slots" w plecaku, ekwipunku, pasku akcji i przy
  przyciskach. Kolor ramki przedmiotu pokazuje jego rzadkość (od szarej po pomarańczową legendarną).

## [0.201.0] - 2026-10-24

### Zmieniono
- Przyciski I, Bieg, Tarcza i Pochodnia stoją teraz obok ekwipunku, a prawy panel jest wąski jak minimapa,
  więc widok gry jest szerszy.
- Bieg: gdy jest włączony, postać sama idzie do zaznaczonego potwora i podąża za nim, aż stanie obok.
  Joystick przerywa pogoń za tym celem.
- Tarcza: postawa obronna z założoną tarczą blokuje dodatkowo część ciosów tarczą i mocniej zmniejsza
  obrażenia (60% zamiast 75%).

## [0.200.0] - 2026-10-24

### Zmieniono
- Sterowanie: joystick woli kierunki proste (skos tylko w wąskim klinie), więc przypadkowy, trzy razy wolniejszy
  krok po skosie zdarza się rzadziej. Skos w mur zamienia się w krok wzdłuż muru. Krótkie stuknięcie strzałki
  lub WASD w trakcie kroku nie ginie: krok wykona się zaraz po bieżącym.

## [0.199.0] - 2026-10-24

### Dodano
- Łuki i kusze paladyna z OpenTibia Sprite Pack (13, od krótkiego łuku po łuk świtu na poziomie 850): w sklepach
  z bronią i u bossów. Strzelają bez amunicji, włócznie z gniazda amunicji do nich nie pasują.
- Kalenice i okapy dachów z paczki, dachy śnieżne w mieście śniegu, okrągła pomarańczowa dachówka i niebieski dach pałacu.
- Pozostałe pociski i efekty paczki: bełt z kuszy, ognista strzała z magmowego łuku, czerwone strzały łowców,
  trucizna węży i pająków, ciemna gwiazdka czarów śmierci, kamienie z ziemi przy fizycznych czarach obszarowych,
  złote iskry przyspieszenia i niebieski wybuch magii energii.

### Zmieniono
- Granice trawy, śniegu, piasku i popiołu są wygładzone: bez prostokątów, schodków i pojedynczych wypustek.

## [0.198.0] - 2026-10-24

### Dodano
- Kolejne grafiki z OpenTibia Sprite Pack: pajęczyny w zakamarkach jaskiń, bałwany na placach miasta śniegu,
  gliniane dzbany i worki we wnętrzach (część skrzyń i beczek).
- Odmiany krzeseł, skrzyń, posągów, donic z kwiatami, świec, sztandarów i drewna (każde pole może mieć inną).

### Zmieniono
- Upiór mgły, krab koralowy, Morvena i Mor'gath mają własne stroje z paczki zamiast przebarwionych.

## [0.197.0] - 2026-10-23

### Dodano
- Profesja Rycerz (Knight, po awansie Elite Knight): miecze, topory i młoty (17 nowych broni z OpenTibia Sprite Pack),
  tarcze, 11 czarów (m.in. brutal strike, berserk, wound cleansing, annihilation, groundshaker).
- Krew i plamy po trafieniach (30 s), zwłoki ludzi, goblinów i szkieletów.
- Góry i jaskinie z grafik OTSP, więcej odmian drzew, skał i roślin; drzewa 64 px bez rozmycia.

### Zmieniono
- Kamera bliżej postaci: widok 11x8 pól.
- Donice, kwiaty, ławki i strażnicy przy bramach stoją przy murze obok ulicy, nie blokują przejścia.

## [0.196.0] - 2026-10-22

### Dodano
- Plecak w ramce ekwipunku jak w Tibii (prawy górny róg lalki, w układzie 5x2 ostatnie pole). Dotknięcie
  otwiera i zamyka plecak; otwarty plecak podświetla gniazdo. Grafika z OpenTibia Sprite Pack.

### Zmieniono
- Krok po skosie trwa jak trzy zwykłe kroki (wyczerpanie po skosie jak w Tibii); wcześniej √2. Potwory bez zmian.

## [0.195.0] - 2026-10-22

### Zmieniono
- Układ ekranu: pad w lewym dolnym rogu ekranu (także nad dolnym paskiem), konsola i rząd przycisków zaczynają się
  za padem, prawy panel węższy (20% szerokości zamiast 30%), więc widok gry jest szerszy.
- Mury budynków jak w Tibii: cienkie ściany na krawędzi pola z wysokością rysowaną w górę i w lewo (kamień,
  drewno, piaskowiec, mech, bale; kształt z sąsiadów: pionowy, poziomy, narożnik, słupek). Dachy i wyższe piętra
  przesunięte o pole w górę i w lewo, leżą na murach. Mury jaskiń zostają bryłą.

### Dodano
- Animacja chodu: postacie graczy (4 kierunki) i potwory przebierają nogami w trakcie kroku (dwie klatki kroku
  z OpenTibia Sprite Pack, pliki `*_walk1.png` / `*_walk2.png`).

## [0.194.0] - 2026-10-22

### Zmieniono
- Gra offline: nowa postać startuje od razu na poziomie 300 (`start.level.offline` w starting.properties).
  HP i mana wynikają z poziomu i profesji (np. czarodziej 1645 HP / 8850 many) i są pełne; umiejętności dobrane do
  poziomu (magowie magia 70, paladyn dystans i mnich pięści ~91, obrona ~82). Serwer nadal tworzy postacie od poziomu 8.
  `-Dpixel.startLevel=N` nadpisuje poziom (regresja GUI używa 8).
- Napisy panelu nie przebijają przez okna Ekwipunek i Umiejętności.

## [0.193.3] - 2026-10-21

### Zmieniono
- Dotknięcie założonego przedmiotu (panel i okno Ekwipunek) najpierw opisuje go w konsoli (atak, obrona,
  pancerz, ulepszenie); dopiero drugie dotknięcie w ciągu 2 s zdejmuje go do plecaka. Koniec przypadkowego zdejmowania.

## [0.193.2] - 2026-10-21

### Zmieniono
- Pola paska z miksturą albo runą pokazują odnowienie tak jak czary: ciemna zasłona opada z góry ikony.

## [0.193.1] - 2026-10-21

### Zmieniono
- Przytrzymanie pola paska akcji pokazuje rosnącą żółtą ramkę, aż otworzy się wybór akcji (widać, ile trzymać).

## [0.193.0] - 2026-10-21

### Dodano
- Okno „Umiejętności” w prawym panelu na wysokim ekranie (jak w Tibii): poziom, doświadczenie i wszystkie
  umiejętności z paskami postępu, pod ekwipunkiem; lista walki zostaje pod nim.

## [0.192.2] - 2026-10-21

### Zmieniono
- Telefon: górny pasek pokazuje poziom i złoto obok HP/MP (pełny opis z godziną i piętrem na szerszym ekranie),
  przyciski dolnego rzędu są węższe.

## [0.192.1] - 2026-10-21

### Zmieniono
- Na szerokim ekranie okna (księga, NPC, rynek, mapa) otwierają się nad widokiem gry, a prawy panel
  (minimapa, ekwipunek, lista walki, pasek akcji) zostaje widoczny. Na telefonie okna dalej zajmują cały ekran.

## [0.192.0] - 2026-10-21

### Zmieniono
- Układ jak klasyczny klient Tibii, wszystko w jednej bryle: świat w ramce (co najmniej 15x11 pól, postać
  na środku ramki), po prawej stały panel z minimapą i przyciskami (i, BIEG, TARCZA, POCH), ekwipunkiem,
  listą walki i paskiem akcji 5x2, u góry paski HP i MP z doświadczeniem, poziomem, złotem i ikonami stanu,
  na dole rząd przycisków (ATAK, UŻYJ, PLECAK, KSIĘGA...) i konsola. Na mapie leży tylko pad i pasek celu.
- Lista walki przeniesiona z zakładki konsoli do własnego okna w panelu.
- Dotyk na panelach nie przechodzi już na mapę; czerwona ramka niskiego HP obejmuje widok gry.

## [0.191.0] - 2026-10-21

### Zmieniono
- Pasek akcji jak w Tibii na telefonie: 10 mniejszych pól 5x2 w prawym dolnym rogu, obok UŻYJ i ATAK.
  Każde pole może trzymać czar, miksturę albo runę (duża ikona i liczba sztuk z plecaka).
- Długie przytrzymanie pola (0,45 s) otwiera wybór: czar leczący, czar atakujący, mikstura, runa, inny czar,
  automat albo wyczyszczenie pola. Krótkie dotknięcie używa akcji, puste pole „+” od razu otwiera wybór.
- Układ zapisuje się osobno dla każdej postaci; stary zapis 4 czarów jest wczytywany. Klawisze 1-9 i 0 na komputerze.
- Napisy pasków i przycisków nie przebijają już przez otwarte okno.

### Dodano
- Akcja `item:<klucz>` (także na serwerze): użycie mikstury albo runy z plecaka z pola paska.

## [0.190.1] - 2026-10-21

### Dodano
- Zdobyte doświadczenie unosi się nad postacią jako biała liczba („123 exp”), jak w Tibii (nowe zdarzenie walki EXP,
  przesyłane też z serwera).
## [0.190.0] - 2026-10-21

### Dodano
- Ikony stanu pod paskami jak w Tibii: strefa ochronna, walka (blokada strefy), najedzony / głodny, błogosławieństwo,
  bieg, światło i zaklęcie broni. Dotknięcie ikon wypisuje ich opis w konsoli.
## [0.189.1] - 2026-10-21

### Zmieniono
- Pola czarów w pasku akcji mają kolor żywiołu (ogień, lód, ziemia, energia, święty, śmierć) i znak rodzaju czaru:
  krzyż – leczenie, pocisk – atak na cel, wybuch – obszar, słońce – światło. Nazwa jest małym napisem u dołu.
## [0.189.0] - 2026-10-21

### Dodano
- Lista walki jak w Tibii: zakładka „Walka (n)” w konsoli pokazuje potwory w pobliżu (do 7 pól, na tym samym piętrze)
  z ikoną i paskiem życia, najbliższe na górze. Dotknięcie potwora ustawia go jako cel (także online), obecny cel ma czerwoną ramkę.
  Nowa scena regresji GUI `battle`.
## [0.188.0] - 2026-10-21

### Zmieniono
- Układ jak w Tibii:
  - pasek akcji z kwadratowymi polami 4x2 w prawym dolnym rogu: czary 1-4 (odnowienie opada z góry jak w Tibii),
    mikstury HP/MP z ikoną i liczbą, UŻYJ i ATAK (czerwona ramka, gdy jest cel);
  - BIEG, TARCZA i POCH jako kwadratowe przyciski obok okna ekwipunku (jak przyciski walki w Tibii);
  - CZAT w dolnym pasku obok PLECAK i KSIĘGA.
- Pad ruchu to sama kulka, w 90% przezroczysta, bez strzałek i bez tła.
- Naprawiona półprzezroczystość HUD po rysowaniu ramek z obrazków (wcześniej część kształtów była nieprzezroczysta).
## [0.187.0] - 2026-10-21

### Zmieniono
- Prawy górny róg jak w Tibii: na stałe minimapa, a pod nią okno ekwipunku (dotknięcie przedmiotu zdejmuje go).
  Na niskich ekranach ekwipunek jest zwartym paskiem 5x2. Paski HP/MP i konsola są po lewej.
- `-Dpixel.uiScale=2` pokazuje na komputerze układ jak na telefonie; nowa scena regresji GUI `phone`.
## [0.186.0] - 2026-10-21

### Dodano
- Okna jak w OTClient (grafiki OTClientV8, MIT):
  - **Ekwipunek**: lalka z 9 gniazdami (amulet, hełm, broń, zbroja, tarcza, pierścień, nogi, amunicja, buty);
    dotknięcie założonego przedmiotu zdejmuje go do plecaka (nowa akcja `unequip:GNIAZDO`, działa też online).
  - **Umiejętności**: poziom, doświadczenie, zdrowie, mana, szybkość, udźwig, atak, sytość i umiejętności z paskiem postępu.
    Oba okna otwiera dotknięcie pasków HP/MP (P), zamyka krzyżyk.
  - **Plecak**: małe okno z nagłówkiem i polami przedmiotów z OTClientV8.
  - **Konsola**: stałe okno czatu i zdarzeń pod panelem postaci (zadania, wyzwanie, łupy, ostrzeżenia, wiadomości);
    dotknięcie otwiera pisanie, przycisk „–” zwija okno.
- Ramka panelu z minimapą i paskami oraz dolnego paska z OTClientV8 (panel_map.png).

### Zmieniono
- Przycisk CZAT stoi przy joysticku na dole; podpowiedź o walce wskazuje przycisk ATAK.
## [0.185.1] - 2026-10-21

### Zmieniono
- Paski HP, MP, doświadczenia, celu i umiejętności mają cieniowanie i ramkę z OTClientV8 (progressbar.png, MIT).
## [0.185.0] - 2026-10-21

### Zmieniono
- Grafiki interfejsu z OTClientV8 (licencja MIT): ramki okien z nagłówkiem (księga, sklep, plecak, mapa,
  karta postaci), przyciski dolnego paska i pad joysticka ze strzałkami. Autorzy w `/autorzy`
  i docs/CREDITS.md, licencja w `assets/ui/otc/LICENSE-OTCV8.txt`.
## [0.184.2] - 2026-10-21

### Zmieniono
- Okna (księga, sklep, plecak, mapa, karta postaci) mają ramkę i pasek tytułu w stylu klienta mobilnego.
## [0.184.1] - 2026-10-21

### Dodano
- Pusty przycisk akcji na łuku pokazuje „+”. Dotknięcie otwiera księgę czarów w trybie przypisywania:
  wybrany czar trafia na ten przycisk (zapisywane jak dotychczasowy własny pasek).
## [0.184.0] - 2026-10-21

### Zmieniono
- Nowy układ HUD w stylu mobilnego klienta (wzorowany na układzie OTClientV8 mobile, własne rysowanie):
  joystick w obramowaniu po lewej z przyciskiem czatu, duży przycisk ATAK w prawym dolnym rogu,
  wokół niego 8 akcji na dwóch łukach (czary, mikstury HP/MP z ikoną i liczbą, tarcza, użyj),
  nad nim bieg i pochodnia, na dole pasek: plecak, księga, rozmowa, podnieś, łów, koń.
- Minimapa przeniesiona do lewego górnego rogu, obok niej paski HP/MP/EXP i stan postaci w jednym panelu.
- Ikony przedmiotów na przyciskach (miecz, tarcza, pochodnia, mikstury, wędka).
## [0.183.1] - 2026-10-21

### Zmieniono
- Domy z piaskowca w Qasirah mają piaskowy dach zamiast niebieskiego.
## [0.183.0] - 2026-10-21

### Dodano
- Widok jak w Tibii: budynki mają dachy (dachówka nad drewnianymi domami, niebieski dach nad kamiennymi),
  które znikają, kiedy wejdziesz do środka. Wyższe piętra (tarasy na murach, piętra domów) są widoczne z dołu.
- Ściany mają wysokość: wierzch muru jest podniesiony, a pod nim widać ciemniejsze lico, więc mur zasłania to,
  co stoi tuż za nim. Drzewa i palmy są wyższe niż kafelek.
## [0.182.1] - 2026-10-21

### Zmieniono
- Manekiny treningowe nie stają już w świątyniach, tylko na placu lub ulicy obok.
- Klify Selvaran: skały tylko na krawędziach półek, w dole widać dżunglę.
## [0.182.0] - 2026-10-21

### Dodano
- Nowe Selvaran: miasto w dżungli przebudowane od zera. Mury z czterema wieżami, po których można chodzić
  (taras na piętrze +1), świątynia z kolumnami i dzwonnicą, depozyt ze skarbcem na piętrze, plac z fontanną,
  trzy sklepy (kupiec, płatnerz z bronią i zbrojami z całego świata, zielarka z miksturami i różdżkami) oraz domy
  z piętrem.
- Nowe expowiska wokół Selvaran: Skalny Wąwóz (skały, krzaki, łowcy i węże), Klify na piętrach +1 i +2,
  Kanały pod miastem (-1, wejście włazem z placu) i Kryształowa Głębia (-2, kryształowe golemy).
## [0.181.0] - 2026-10-21

### Dodano
- Nowa grafika: 180 obrazków z OpenTibia Sprite Pack (darmowe grafiki społeczności OpenTibia, licencja CC BY 4.0):
  trawa, piasek, bruk, woda i inne podłoża, drzewa i kamienie, gobliny, dziki, szkielety i 19 innych potworów,
  wszyscy NPC, postacie czterech profesji w 4 kierunkach, broń, zbroje, mikstury i monety.
- Grafiki 32×32 są wyświetlane w pełnej rozdzielczości (osobny atlas), więc nie tracą szczegółów.
- /autorzy: lista autorów grafik w grze (pełna lista też w docs/CREDITS.md).
## [0.180.0] - 2026-10-21

### Dodano
- Grafiki od grafików: plik PNG w `assets/art/` (np. `monsters/wolf.png`, `characters/paladin_down.png`) zastępuje
  obrazek rysowany przez kod, a brakujące zostają takie jak dotąd. Pełna lista 278 nazw: docs/GRAPHICS.md.
## [0.179.0] - 2026-10-21

### Dodano
- Rzut kostką: /kostka (k6) albo /kostka 20. Wynik losuje serwer, więc nikt go nie podrobi, a widzą go wszyscy
  w dymku nad postacią. Kostka jest też w oknie „Emotki”.
## [0.178.0] - 2026-10-21

### Dodano
- Osiągnięcie „Zielarz” za zebranie 50 ziół leczniczych (daje też tytuł). Liczbę zebranych ziół widać w /statystyki.
## [0.177.2] - 2026-10-21

### Dodano
- Pułapki z kolcami widać na minimapie i dużej mapie jako pomarańczowe punkty.
## [0.177.1] - 2026-10-21

### Zmieniono
- Marsz po dotknięciu mapy, znacznika albo łowiska omija pułapki z kolcami, jeśli jest inna droga.
## [0.177.0] - 2026-10-21

### Dodano
- Pułapki z kolcami w głębszych lochach (piętra -2 do -5): metalowe płyty w korytarzach. Wejście na płytę zabiera
  5% zdrowia na każde piętro głębokości (na -5 to 25%), ale pułapka nigdy nie zabija. Widać je na mapie, więc
  można je omijać.
## [0.176.0] - 2026-10-21

### Dodano
- Zbieranie ziół: UŻYJ obok krzaka (wokół Varden jest ich 18) daje 1–2 ziela lecznicze, a krzak odrasta po
  10 minutach. Z 4 ziół i 10 gp wytworzysz w księdze miksturę zdrowia; ziele skupuje też kupiec w Varden.
## [0.175.0] - 2026-10-21

### Dodano
- Okno „Emotki” (/emotki albo z listy /pomoc): dotknięcie emotki od razu ją wykonuje, bez pisania na telefonie.
## [0.174.1] - 2026-10-21

### Dodano
- Emotki poruszają postacią: przy /taniec i /hura podskakuje, przy /machaj, /brawo i /śmiech lekko się kołysze,
  a przy /ukłon, /siad i /zzz przykuca. Ruch widać też u innych graczy, dopóki wisi dymek.
## [0.174.0] - 2026-10-21

### Dodano
- Emotki na czacie: /taniec, /machaj, /ukłon, /śmiech, /brawo, /hura, /płacz, /siad, /zzz oraz /ja tekst.
  Nad postacią pojawia się niebieski dymek, np. „* tańczy *”, widoczny dla innych graczy online.
## [0.173.0] - 2026-10-21

### Dodano
- Przycisk ZACHOWAJ pod zaznaczonym przedmiotem w plecaku. Zachowany rodzaj przedmiotu ma złoty róg slotu i nie
  zostanie sprzedany ani odłożony do depozytu jednym dotknięciem („cały łup”) ani wyrzucony przypadkiem.
  ODBLOKUJ zdejmuje oznaczenie. Oznaczenia zapisują się z postacią, także na serwerze.
## [0.172.0] - 2026-10-21

### Dodano
- Przycisk SORTUJ w plecaku: łączy rozrzucone stosy (np. mikstury) i układa przedmioty według rodzaju
  (monety, broń, tarcze, zbroje, amunicja, mikstury...), lepiej ulepszone najpierw. Działa też w grze online.
## [0.171.0] - 2026-10-21

### Dodano
- Czary na poziom 100, po jednym dla profesji: Meteor (czarodziej), Śnieżyca (druid), Ostrze światła (paladyn)
  i Pięść feniksa (mnich). Dotąd najsilniejsze czary kończyły się na poziomie 60.
## [0.170.1] - 2026-10-21

### Dodano
- Dotknięcie potwora w oknie "Gdzie polować" prowadzi postać do jego najbliższego legowiska (albo mówi, w której
  krainie go szukać, gdy jest daleko).
## [0.170.0] - 2026-10-21

### Dodano
- "Gdzie polować?" u przewodnika i `/łowiska`: lista potworów dobranych do poziomu postaci z doświadczeniem
  i krainami, w których żyją (potwór dnia oznaczony).
## [0.169.0] - 2026-10-21

### Dodano
- Okno Wydarzenia pokazuje miejsce w rankingu tygodnia, zdobyte doświadczenie i ile zostało do nagród.
## [0.168.0] - 2026-10-20

### Dodano
- Nagrody rankingu tygodnia: w poniedziałek trzy osoby z największym przyrostem doświadczenia dostają 100 000,
  50 000 i 25 000 gp na konto (nieobecni po wejściu do gry), a serwer ogłasza podium wszystkim graczom.
## [0.167.0] - 2026-10-20

### Dodano
- Ranking tygodnia: `/ranking tydzień` pokazuje, kto zdobył najwięcej doświadczenia od poniedziałku. W każdym tygodniu
  wszyscy zaczynają od zera, więc nowi gracze też mają szansę na podium.
## [0.166.2] - 2026-10-20

### Naprawiono
- Sklep: włócznie do rzucania pokazywały "atak +20" (porównanie z pustym miejscem na amunicję). Teraz: "amunicja, atak 20".
## [0.166.1] - 2026-10-20

### Testy
- Nowa scena regresji GUI `shopgear`: strona sprzętu u kupca z porównaniem do założonego ("atak -11, obrona +8", "nie dla ciebie").
## [0.166.0] - 2026-10-20

### Dodano
- Administracja: `reports` w konsoli albo `/zgłoszenia` w grze pokazuje 5 ostatnich zgłoszeń graczy.
## [0.165.0] - 2026-10-20

### Dodano
- `/zgłoś Imię, powód`: zgłoszenie gracza do administracji. Administratorzy w grze widzą je od razu, a serwer zapisuje
  wszystkie w `reports.txt`. Jedno zgłoszenie na minutę.
## [0.164.0] - 2026-10-20

### Dodano
- Potężne mikstury zdrowia (420-560) i many (240-330) od poziomu 100, między silnymi a najwyższymi: w sklepach
  Mroźnych Pustkowi, Selvaran, Qasirah i obozu, czasem z Strażnika świątyni i Mumii. Przyciski HP/MP biorą je same.
## [0.163.0] - 2026-10-16

### Dodano
- Oglądanie innego gracza: `/obejrzyj Imię` albo "Obejrzyj" po dotknięciu postaci pokazuje profesję, poziom, gildię,
  tytuł i założony sprzęt z ulepszeniami. Oglądany dostaje o tym informację.
## [0.162.0] - 2026-10-16

### Dodano
- `/gildia członkowie`: lista członków gildii z poziomami - najpierw obecni w grze, przy pozostałych kiedy ostatnio grali.
## [0.161.0] - 2026-10-16

### Dodano
- Znajomi: przy osobach poza grą widać, kiedy ostatnio grały ("3 h temu", "5 dni temu").
## [0.160.0] - 2026-10-16

### Dodano
- Serwer: polecenie konsoli `shutdown 5` wyłącza serwer po odliczaniu z ogłoszeniami na czacie (30 min ... 10 s),
  zapisuje wszystkie postacie i kończy proces, więc aktualizacja na VPS nie przerywa nikomu walki bez ostrzeżenia.
  `shutdown -` odwołuje.
- Test pilnujący, by klient nie używał `Stream.toList()`, `String.formatted()` ani `Files.readString/writeString`
  (brak na Androidzie 11).
## [0.159.0] - 2026-10-16

### Dodano
- Sklep: przy broni i zbroi widać, co zmieni się względem założonego przedmiotu (np. "atak +5, pancerz -1"),
  czy profesja może go nosić i od którego poziomu.
## [0.158.0] - 2026-10-16

### Dodano
- 7 receptur dla poziomów 20-70 z materiałów krain: kastety, futrzana zbroja, tarcza z piaskowca, nogawice
  z krokodyla, wężowa włócznia, pustynne kastety i hełm skarabeusza. Dotąd rzemiosło miało dziurę między
  poziomem 15 a 250.
## [0.157.0] - 2026-10-16

### Dodano
- Android: przycisk Wstecz zamyka otwarte okno (mapa, rozmowa, plecak, karta postaci, osiągnięcia...).
  Gdy nic nie jest otwarte, dwukrotne Wstecz w ciągu 2 sekund wychodzi z gry zamiast zamykać ją od razu.

### Naprawiono
- Esc przy otwartym oknie osiągnięć, rankingu albo rynku otwierał księgę czarów zamiast zamknąć okno.
## [0.156.0] - 2026-10-16

### Zmieniono
- Rozmowa z przewodnikiem jest czytelniejsza: najpierw zadania do oddania, w toku i dostępne, z przyszłych
  tylko najbliższe, a ukończone zebrane w jeden wiersz. Wróciły ukryte wcześniej tematy rozmowy.
## [0.155.0] - 2026-10-16

### Dodano
- Znacznik „Oddaj zadanie” na mapie: gdy zadanie dnia albo zlecenie łowcy jest wykonane, mapa wskazuje
  najbliższego przewodnika.
## [0.154.0] - 2026-10-16

### Dodano
- Znacznik „Łowisko” na minimapie i dużej mapie: najbliższe miejsce potworów z zadania dnia albo zlecenia łowcy.

### Naprawiono
- Dziennik zdarzeń nie zasłania już tytułu dużej mapy.
## [0.153.0] - 2026-10-16

### Dodano
- Okno /wydarzenia pokazuje w wierszu „Premia exp” także sztandar Twojej gildii i jego pozostały czas.
## [0.152.0] - 2026-10-16

### Dodano
- Sztandar gildii: lider za 100 000 gp ze skarbca daje wszystkim członkom +10% doświadczenia za potwory
  na godzinę (`/gildia sztandar`; kolejny przedłuża czas).
## [0.151.0] - 2026-10-16

### Dodano
- Wiadomość prywatna (`/w Imię, tekst`) do gracza, którego nie ma w grze, czeka jako list i pokazuje się
  po jego wejściu (do 20 listów na gracza).
## [0.150.0] - 2026-10-16

### Dodano
- Osiągnięcia „Kronikarz” (40 różnych potworów) i „Legenda krainy” (40 zadań). Razem 29 osiągnięć.
## [0.149.0] - 2026-10-16

### Dodano
- `/cena` w grze sieciowej podaje też ceny z rynku graczy: ostatnią sprzedaż, najtańszą ofertę i ile płacą
  kupujący w zamówieniach (za sztukę).
## [0.148.0] - 2026-10-16

### Dodano
- Zmiana zadania dnia u przewodnika: raz dziennie, za 50 gp za poziom postaci, losuje innego potwora
  (postęp od zera).
## [0.147.0] - 2026-10-16

### Zmieniono
- Okno /pomoc podpowiada `/szukam` (drużyna) i `/kupię` (zamówienia kupna u bankiera).
## [0.146.0] - 2026-10-16

### Dodano
- `/szukam` (gra sieciowa): ogłoszenie, że szukasz drużyny. Gracze o podobnym poziomie bez drużyny dostają
  wiadomość z poleceniem `/zaproś`, a lista `/gracze` pokazuje „szuka drużyny”.
## [0.145.0] - 2026-10-16

### Dodano
- Nagłówek bestiariusza pokazuje, ile gatunków potworów już poznano spośród wszystkich w grze.
## [0.144.0] - 2026-10-16

### Dodano
- Wibracje na telefonie przy alarmie niskiego zdrowia (ustawienia, strona 2; domyślnie włączone).
## [0.143.0] - 2026-10-16

### Dodano
- Licznik rzadkich łupów (w /statystyki) i dwa osiągnięcia: „Szczęściarz” (pierwszy rzadki łup)
  i „Ulubieniec losu” (10 rzadkich łupów). Razem 27 osiągnięć.
## [0.142.1] - 2026-10-16

### Naprawiono
- Niestabilny test sieciowy nagród za głowy (CI 0.140.0): cel regenerował się między chybionymi strzałami.
## [0.142.0] - 2026-10-16

### Zmieniono
- Zamówienia kupna wygasają po 7 dniach, a zablokowane złoto wraca na konto (graczom offline przy następnym wejściu).
## [0.141.0] - 2026-10-16

### Dodano
- Licznik wygranych pojedynków (w /statystyki) i osiągnięcie „Mistrz pojedynków” za 10 zwycięstw (razem 25).
## [0.140.0] - 2026-10-16

### Dodano
- 7 nowych zadań na potwory, które ich dotąd nie miały: dziki, pająki leśne i kryształowe golemy w Varden
  (poziomy 10-40) oraz żywiołaki ognia, harpie burzy, piaskowe tytany i kościane smoki w obozie (300-870).
## [0.139.0] - 2026-10-16

### Dodano
- U bankiera wiersz „Zamówienia kupna” otwiera listę zamówień jednym dotknięciem.
## [0.138.0] - 2026-10-16

### Dodano
- Zamówienia kupna (gra sieciowa): u bankiera `/kupię 20 wilcze futro 2000` blokuje złoto z konta i ogłasza
  zamówienie. `/kupię` otwiera listę, `/sprzedaj 3` realizuje cudze zamówienie (złoto bez prowizji od razu na konto,
  przedmioty pocztą do depozytu kupującego), `/kupię anuluj 3` zwraca złoto.
## [0.137.0] - 2026-10-16

### Dodano
- Osiągnięcie „Wypoczęty łowca”: pokonaj 100 potworów z premią wypoczęcia (razem 24 osiągnięcia).
## [0.136.0] - 2026-10-16

### Dodano
- Lista /gracze pokazuje nagrodę za głowę przy postaciach, za które ktoś ją wyznaczył.
## [0.135.0] - 2026-10-16

### Dodano
- Podpowiedź o odpoczynku: po pierwszych walkach, gdy postać wróci do strefy ochronnej, dziennik wyjaśnia
  szybszą regenerację i premię za wypoczęcie.
## [0.134.0] - 2026-10-16

### Dodano
- Wypoczęcie: czas odpoczynku w strefie ochronnej (do 30 minut) zamienia się w premię +50% doświadczenia
  za kolejne pokonane potwory (20 sekund odpoczynku na walkę, 30 minut = 90 walk). Karta postaci pokazuje,
  na ile walk wystarczy wypoczęcia, a wpis w dzienniku dopisuje „wypoczęty”.
## [0.133.1] - 2026-10-16

### Naprawiono
- Gra na Androidzie zamykała się przy starcie od 0.131.0 (wczytywanie okna „Co nowego” używało metody Javy,
  której Android 11 nie ma). Nowy test pilnuje takich wywołań w kodzie klienta.
## [0.133.0] - 2026-10-16

### Dodano
- Odpoczynek: w strefie ochronnej (świątynia, miasto) zdrowie i mana odnawiają się trzy razy szybciej, a po
  jedzeniu cztery razy. Nie działa w czasie blokady strefy po walce. Karta postaci pokazuje, kiedy odpoczywasz.
## [0.132.0] - 2026-10-16

### Zmieniono
- Okno `/pomoc` ma wiersz `/nowości` i wspomina o `/idź łowisko`, `/nagroda` i wpłatach do gildii; krótsze opisy
  nie wychodzą już poza okno.
## [0.131.0] - 2026-10-16

### Dodano
- Okno „Co nowego w grze” (`/nowości` albo `/zmiany`) z punktami pięciu ostatnich wersji. Po aktualizacji
  gry otwiera się samo raz; dziennik zmian jest dołączony do gry.
## [0.130.0] - 2026-10-16

### Dodano
- 9 nowych czarów na poziomy 40-60: Płomienny grot i Burza energii (czarodziej), Lodowy kolec i Gniew ziemi
  (druid), Boska włócznia i Boski gniew (paladyn), Pięść smoka, Burza ciosów i Głęboka medytacja (mnich).
  Symulacje endgame nadal przechodzą.

### Naprawiono
- Księga czarów nie wychodzi poza okno: przy wielu czarach pokazuje te najbliższe poziomowi postaci,
  a lista receptur ma stronę dopasowaną do wysokości okna.
## [0.129.0] - 2026-10-16

### Dodano
- Dziennik zadań pokazuje zlecenie łowcy z postępem; dotknięcie go (albo zadania dnia w toku) prowadzi na łowisko.

### Naprawiono
- Dziennik zdarzeń nie zasłania już nagłówka otwartego okna (np. dziennika zadań).
## [0.128.0] - 2026-10-16

### Dodano
- `/idź łowisko` prowadzi do najbliższego legowiska potwora ze zlecenia łowcy (a bez zlecenia z zadania dnia)
  na tym piętrze; zlecenie przypomina o tym poleceniu.
## [0.127.0] - 2026-10-16

### Dodano
- Zadanie dnia i zlecenie łowcy mówią, gdzie szukać potwora, np. „pokonaj 50x Wilk (Krainy Varden)”.
## [0.126.0] - 2026-10-16

### Dodano
- Karta potwora w bestiariuszu podaje, gdzie go szukać: krainy z jego legowiskami (z dopiskiem „nocą”, gdy
  pojawia się tylko po zmroku).
## [0.125.0] - 2026-10-16

### Dodano
- Okno `/wydarzenia` w grze online pokazuje trwającą premię doświadczenia i ile minut jej zostało.
## [0.124.0] - 2026-10-16

### Dodano
- Wydarzenie premii doświadczenia: administrator włącza `/bonusexp 50 60` (albo `expevent 50 60` w konsoli),
  a wszyscy dostają +50% doświadczenia za potwory przez 60 minut. Start i koniec są ogłaszane, a wchodzący
  gracze widzą, ile zostało.
## [0.123.0] - 2026-10-16

### Dodano
- Skarbiec gildii (online): `/gildia wpłać kwota` przelewa złoto z konta do wspólnego skarbca, `/gildia wypłać kwota`
  może tylko lider. Każda wpłata i wypłata jest ogłaszana członkom, stan skarbca widać w `/gildia`.
## [0.122.0] - 2026-10-16

### Dodano
- Karta postaci pokazuje maksymalny atak z umiejętnością, a u broni dystansowej założoną amunicję i jej liczbę.

### Naprawiono
- Dziennik zdarzeń i śledzenie zadań nie nachodzą już na otwartą kartę postaci; karta jest szersza
  i mieści cały tekst.
## [0.121.0] - 2026-10-16

### Dodano
- Nagrody za głowy (online): `/nagroda imię, kwota` dokłada złoto z konta do nagrody za postać (min. 1 000 gp),
  `/nagrody` pokazuje listę. Kto pokona tę postać w walce PvP, dostaje całość na konto (nie działa w pojedynku
  ani w tej samej drużynie lub gildii). Serwer ogłasza wpłaty i odbiór, nagrody przetrwają restart.
## [0.120.0] - 2026-10-16

### Dodano
- Nowa podpowiedź dla paladynów: gdy gniazdo amunicji jest puste, a w plecaku są włócznie, gra podpowiada,
  żeby je założyć (od 0.118.0 zwiększają obrażenia).
## [0.119.0] - 2026-10-16

### Dodano
- Ranking umiejętności: `/ranking magia`, `miecz`, `dystans`, `pięści`, `tarcza`, `wędkarstwo` pokazuje
  najlepszych w danej umiejętności (także graczy offline z zapisów) i Twoje miejsce.
## [0.118.0] - 2026-10-16

### Dodano
- Amunicja ma znaczenie: włócznie w gnieździe amunicji zwiększają obrażenia włóczni paladyna o 1/4 swojego
  ataku, każdy strzał zużywa jedną, a gdy stos się skończy, uzupełnia się z plecaka (z komunikatem).
  Bez amunicji broń dystansowa działa jak dotąd.
## [0.117.0] - 2026-10-16

### Dodano
- Wiadomość dnia serwera: administrator ustawia ją `/motd tekst` w grze albo `MOTD tekst` w konsoli
  (`/motd -` usuwa). Każdy wchodzący gracz widzi ją w dzienniku; zapis w `motd.txt` przetrwa restart.
## [0.116.0] - 2026-10-16

### Dodano
- Online: serwer wysyła stan wyzwania (komunikat CHAL), więc fala i pozostały czas są widoczne na ekranie
  także w grze sieciowej, nie tylko offline.
## [0.115.0] - 2026-10-16

### Dodano
- Nad wybranym celem widać imię potwora z oceną trudności względem poziomu postaci (bardzo łatwy, łatwy,
  wyrównany, trudny, śmiertelny) w kolorze od szarego do czerwonego.

### Zmieniono
- Krótsza pierwsza podpowiedź, mieści się w szerokości ekranu telefonu.
## [0.114.0] - 2026-10-16

### Dodano
- `/podpowiedzi` wyłącza podpowiedzi dla nowych postaci (działa też online); pierwsza podpowiedź o tym mówi.
## [0.113.0] - 2026-10-16

### Dodano
- Podpowiedzi dla nowych postaci (do poziomu 29): sterowanie, walka z pierwszym potworem, łupy z ciała,
  niskie zdrowie, przewodnik z zadaniami i pełny plecak. Każda pokazuje się raz w dzienniku, gdy pasuje
  do sytuacji; pokazane zapisują się w postaci (online liczy je serwer). Wszystkie sceny GUI odświeżone.
## [0.112.0] - 2026-10-16

### Dodano
- `/idź kupiec` (także bank, kapłan, przewodnik, woźnica albo fragment imienia): postać idzie do najbliższego
  takiego NPC na piętrze, a po dojściu otwiera się rozmowa. Podpowiedź w oknie `/pomoc`.
## [0.111.0] - 2026-10-16

### Dodano
- Licznik ukończonych wyzwań w statystykach postaci (zapisywany; starsze zapisy zaczynają od zera)
  i osiągnięcie „Pogromca fal” za 5 wyzwań; razem 23 osiągnięcia.
## [0.110.0] - 2026-10-16

### Dodano
- Trwające wyzwanie jest widoczne na ekranie nad śledzeniem zadań: numer fali, potwór i pozostały czas.
## [0.109.0] - 2026-10-16

### Dodano
- Zwój wyzwania (1 000 gp u kupców ogólnych w 4 miastach): odczytany poza miastem przywołuje 3 fale potworów
  dobranych do poziomu (3, 4 i 5 sztuk). Kolejna fala rusza po pokonaniu poprzedniej; po ostatniej złoto na konto
  i premia doświadczenia. Wyzwanie przepada po 5 minutach, po śmierci albo po odejściu dalej niż 12 pól.
## [0.108.0] - 2026-10-16

### Dodano
- `/cena nazwa`: wartość przedmiotu i lista kupców NPC ze wszystkich miast, od najwyższej ceny skupu,
  z ceną sprzedaży (np. `/cena wilcze futro`).

### Naprawiono
- Wyścig w teście sieciowym drużyny (sprawdzenie postępu zlecenia przed jego zapisaniem).
## [0.107.0] - 2026-10-16

### Dodano
- Śledzenie zadań na ekranie pokazuje też postęp zlecenia łowcy, gdy jest wolny wiersz.
## [0.106.0] - 2026-10-16

### Zmieniono
- Online w drużynie: potwór pokonany przez drużynę obok (w zasięgu podziału doświadczenia) liczy się każdemu
  członkowi do zadania dnia, zlecenia łowcy i zadań na potwory, nie tylko temu, kto zadał ostatni cios.
## [0.105.0] - 2026-10-16

### Dodano
- Osiągnięcie „Zawodowy łowca” (tytuł) za 10 oddanych zleceń łowcy; razem 22 osiągnięcia.
- Okno `/statystyki` pokazuje liczbę oddanych zleceń łowcy.
## [0.104.0] - 2026-10-16

### Dodano
- Zlecenia łowcy u przewodnika: pokonaj 50 potworów jednego rodzaju dobranego do poziomu, nagroda w złocie
  na konto i doświadczeniu. Bez limitu dziennego (jedno zlecenie naraz), można porzucić i wziąć nowe.
  Postęp zapisuje się w postaci i widać go w oknie `/wydarzenia`.
## [0.103.0] - 2026-10-16

### Zmieniono
- „Uzupełnij zapasy” kupuje najsilniejsze mikstury zdrowia i many, których postać może używać i które sprzedaje
  ten kupiec (np. druid od poziomu 50 dostaje silne mikstury many, ale nie silne mikstury zdrowia).
## [0.102.0] - 2026-10-16

### Zmieniono
- Przyciski HP i MP (oraz klawisze i automatyczna mikstura) piją najsilniejszą miksturę, którą postać ma w plecaku
  i może użyć (najwyższa, silna, zwykła), a licznik na przycisku pokazuje liczbę właśnie tych mikstur.
## [0.101.0] - 2026-10-16

### Dodano
- Ignorowanie graczy: `/ignoruj Imię` ukrywa (albo przywraca) wiadomości gracza w czacie ogólnym, prywatnym, gildii
  i drużynie; `/ignoruj` pokazuje listę. Lista jest zapisana na urządzeniu dla każdej postaci, najwyżej 50 imion.
  Ogłoszeń administratora nie da się ukryć.
## [0.100.0] - 2026-10-16

### Dodano
- Online: ostrzeżenie w dzienniku, gdy w zasięgu wzroku pojawi się gracz z czerwoną czaszką
  (raz na każde pojawienie się; po zniknięciu z widoku i powrocie ostrzega znowu).
## [0.99.0] - 2026-10-16

### Dodano
- Kolor stroju: `/strój` otwiera okno z 8 kolorami (kolor profesji, czerwony, niebieski, zielony, czarny, biały,
  fioletowy, złoty), `/strój złoty` zakłada od razu. Kolor jest zapisywany w postaci i online widzą go inni gracze.
- `/strój` w oknie poleceń (zamiast `/rynek`, który działa dalej u bankiera); nowa scena GUI outfit (35 scen).
## [0.98.0] - 2026-10-16

### Dodano
- Polecenia serwera `status` (czas działania, gracze, potwory, inwazja, pamięć) i `save` (zapis wszystkich postaci)
  w konsoli oraz dla administratorów w czacie: `/serwer`, `/zapisz`. Opis w docs/SERVER.md.
## [0.97.0] - 2026-10-16

### Dodano
- Komunikat w dzienniku, gdy boss się odrodzi („Boss Grimtooth odrodził się (…)”); online liczy go serwer
  dla każdego gracza. Bossowie żywi przy wejściu do gry nie są ogłaszani.
## [0.96.0] - 2026-10-16

### Zmieniono
- Okno `/znaczniki` pokazuje także bossa tygodnia i miejsce ostatniej śmierci; dotknięcie wiersza prowadzi postać na miejsce.
## [0.95.0] - 2026-10-16

### Dodano
- Miejsce ostatniej śmierci jest zaznaczone na minimapie i dużej mapie („Miejsce śmierci”), także po śmierci z ręki gracza.
## [0.94.0] - 2026-10-16

### Zmieniono
- „Uzupełnij zapasy” u kupca dokupuje teraz także runy niesione w plecaku (do 20) i amunicję
  (z plecaka i z gniazda amunicji, do 100), a nie tylko mikstury.
- Jaśniejszy komunikat, gdy zapasy są pełne albo brakuje monet.
## [0.93.0] - 2026-10-16

### Dodano
- Miejsce bossa tygodnia jest zaznaczone na minimapie i dużej mapie (żółta kropka z podpisem „Boss tygodnia: …”
  na mapie piętra, na którym się odradza).
- Rozszerzony test bossa tygodnia.

## [0.92.0] - 2026-10-16

### Dodano
- 2 nowe osiągnięcia (razem 21): „Łowca tygodnia” za pokonanie bossa tygodnia i „Stały bywalec” za 10 godzin gry
  (czas z okna `/statystyki`). Oba dają tytuł nad imieniem.
- 1 nowy test. Sceny `achievements` i `stats` odświeżone.

## [0.91.0] - 2026-10-16

### Dodano
- W depozycie u bankiera wiersz „Odłóż cały łup”: jednym dotknięciem przenosi wszystkie materiały z plecaka
  do depozytu, bez przedmiotów potrzebnych do aktywnych zadań i bez zapasów (akcja `npc:storeloot`, działa też online).
- 1 nowy test; łącznie 343. Scena `depot` odświeżona.

## [0.90.0] - 2026-10-16

### Dodano
- Analiza polowania liczy zużyte zapasy (mikstury, runy, amunicja: sztuki i wartość) i pokazuje „Bilans polowania”:
  złoto i łup minus koszt zapasów, także na godzinę.

### Zmieniono
- „Wartość przedmiotów” w analizie nie liczy już zapasów, więc wypite mikstury nie zaniżają łupu.
- 1 nowy test; łącznie 342.

## [0.89.0] - 2026-10-16

### Dodano
- 5 nowych zadań strażnika obozu na wschodzie, wypełniających luki poziomów 230-900:
  „Upiorna straż” (270, upiorni rycerze), „Mroźni olbrzymi” (420), „Polowanie w cieniu” (570, łowcy cieni),
  „Kapłani pustki” (700) i „Demony u bram” (780). Test `everyQuestTargetCanBeFoundInTheWorld` potwierdza, że cele występują w świecie.

## [0.88.0] - 2026-10-16

### Dodano
- Karta postaci pokazuje postęp do następnego poziomu w procentach: przy doświadczeniu („Exp: … (42%)”)
  i przy każdej umiejętności („Miecz 12 (35%)”).
- 1 nowy test; łącznie 341. Scena `character` odświeżona.

## [0.87.0] - 2026-10-16

### Dodano
- Ustawienie „Cel sam” (druga strona panelu): gdy postać nie ma celu i jest poza strefą ochronną, sama wybiera
  potwora stojącego najwyżej 3 pola obok (działa też online, przez to samo polecenie co przycisk CEL).
- 1 nowy test i rozszerzony test ustawień; łącznie 340.

## [0.86.0] - 2026-10-16

### Dodano
- Śledzenie zadań na ekranie: pod stanem postaci widać postęp aktywnego zadania („Zadanie: Plaga wilków 3/10”)
  i zadania dnia, a gdy cel jest osiągnięty, przypomnienie o odbiorze nagrody. Najwyżej 2 wiersze.
- 1 nowy test; łącznie 339. Scena `journal` odświeżona.

## [0.85.0] - 2026-10-16

### Dodano
- Ustawienie „Jedzenie samo”: gdy sytości zostaje mniej niż minuta, postać zjada pierwsze jedzenie z plecaka
  (sytość podwaja regenerację).
- Panel ustawień ma strony: ostatni wiersz „Więcej ustawień (strona 1 z 2)” przełącza stronę.

### Zmieniono
- Krótszy opis ustawienia leczenia („Leczenie samo”), żeby mieścił się w wierszu.
- 1 nowy test; łącznie 338.

## [0.84.0] - 2026-10-16

### Dodano
- Alarm niskiego zdrowia: gdy życie spadnie poniżej 25%, krawędzie ekranu pulsują na czerwono, a co 4 s gra
  krótki sygnał (nowy dźwięk ALARM, głośność z ustawień). Znika, gdy zdrowie wróci powyżej progu.
- 1 nowy test; łącznie 337.

## [0.83.0] - 2026-10-16

### Dodano
- Historia czatu: strzałki w górę i w dół w polu czatu (desktop) przywracają 20 ostatnich wiadomości i poleceń.
  Na Androidzie okno czatu podpowiada ostatnią wiadomość („Ostatnio: …”).
- 1 nowy test; łącznie 336.

## [0.82.0] - 2026-10-16

### Dodano
- Wyszukiwanie przedmiotów `/szukaj nazwa`: okno z liczbą sztuk w plecaku, w depozycie i informacją, czy przedmiot
  jest założony. Działa bez polskich znaków i wielkości liter, także po angielskim kluczu (np. `/szukaj futro`).

### Zmieniono
- Wiersz `/statystyki` w `/pomoc` podpowiada `/szukaj`.
- 1 nowy test; łącznie 335.

## [0.81.0] - 2026-10-16

### Dodano
- U kupca wiersz „Uzupełnij mikstury”: jednym dotknięciem dokupuje mikstury zdrowia i many do 20 sztuk każdej,
  tyle, na ile starczy monet w plecaku (akcja `npc:refill`, działa też online).
- 1 nowy test; łącznie 334. Scena `shop` odświeżona.

## [0.80.0] - 2026-10-16

### Dodano
- Boss tygodnia: co 7 dni jeden boss wspólny dla wszystkich graczy. Pierwsze pokonanie w tygodniu daje postaci
  złoto na konto (2× doświadczenie bossa, co najmniej 5 000 gp); liczy się też jego elitarna odmiana.
- Wiersz „Boss tygodnia” w oknie `/wydarzenia` (nagroda albo „nagroda odebrana, nowy za N dni”).
- Zapis odebranej nagrody (`wboss`). 2 nowe testy; łącznie 333.

## [0.79.0] - 2026-10-16

### Dodano
- Ostrzeżenia o końcu zapasów na polowaniu: gdy mikstur, run albo amunicji w plecaku spadnie do 5 sztuk
  („Zostało tylko 5: Mikstura zdrowia.”) i gdy skończą się całkiem. W strefie ochronnej (zakupy, depozyt) bez komunikatów.
- 1 nowy test; łącznie 331.

## [0.78.0] - 2026-10-16

### Dodano
- Okno „Ostatnie łupy” (`/łupy`, `/łup`): 30 ostatnich pokonanych potworów i co z nich wypadło (także to, co zostało w ciele),
  z czasem („3 min temu”) i oznaczeniem rzadkiego łupu. Działa lokalnie i online (żądanie `LOOTLOG`).

### Zmieniono
- Wiersz `/analiza` w oknie `/pomoc` wskazuje też `/łupy`.
- 1 nowy test i rozszerzony test rzadkiego łupu; łącznie 330.

## [0.77.0] - 2026-10-16

### Zmieniono
- Ustawienie automatycznego leczenia najpierw rzuca najsilniejszy dostępny czar leczący (Opatrzenie ran, Uzdrowienie),
  a miksturę zdrowia pije dopiero, gdy brak many albo czar się odnawia. Wiersz ustawień: „Leczenie samo (czar, potem mikstura)”.
- 1 nowy test; łącznie 329.

## [0.76.0] - 2026-10-16

### Dodano
- Okno „Statystyki” (`/statystyki`, `/staty`): czas gry, poziom (z najwyższym osiągniętym), pokonane potwory i gatunki,
  elity i bossowie, śmierci, ukończone zadania, otwarte skrzynie, osiągnięcia i złoto w banku.
- Postać liczy czas gry, śmierci i najwyższy poziom (zapis `stats`, przenoszony też do klienta online).
- Scena regresji GUI `stats` (34 sceny).

### Zmieniono
- W oknie `/pomoc` wiersz `/zadania` zastąpiony wierszem `/statystyki` (dziennik zadań nadal pod `/zadania`, klawiszem Z i w KSIĘDZE).
- 2 nowe testy; łącznie 328.

## [0.75.0] - 2026-10-16

### Dodano
- Ustawienie „Mikstura many sama” (wyłączona, poniżej 30%, 50% albo 70% MP), niezależne od mikstury zdrowia.
  Zdrowie ma pierwszeństwo: mikstura many nie pije się w tej samej chwili co mikstura zdrowia.

### Zmieniono
- Panel ustawień przesunięty wyżej, żeby dziewiąty wiersz nie zachodził na przycisk KSIĘGA.
- 1 nowy test; łącznie 326.

## [0.74.0] - 2026-10-16

### Dodano
- Okno „Wydarzenia” (`/wydarzenia` albo `/dziś`): następna albo trwająca inwazja (z serwera), potwór dnia, postęp
  zadania dnia, seria logowania, najbliższa nagroda za poziom i aktywne zaklęcia z pozostałym czasem.
- Scena GUI `events` (33 sceny). W oknie `/pomoc` wiersz `/wydarzenia` zastąpił `/potwór` (polecenie dalej działa).
- 2 nowe testy (w tym sieciowy); łącznie 325.

## [0.73.0] - 2026-10-16

### Dodano
- „Sprzedaj cały łup” u kupca: jedno dotknięcie sprzedaje wszystkie materiały z potworów, które kupiec skupuje.
  Przedmioty potrzebne do aktywnych zadań zbierania zostają w plecaku; wyposażenie, mikstury i narzędzia też.
- 1 nowy test; łącznie 323.

## [0.72.0] - 2026-10-16

### Dodano
- Ranking gildii: `/ranking gildie` pokazuje 10 najsilniejszych gildii według sumy poziomów członków (także
  offline) i miejsce twojej gildii.
- 1 nowy test i rozszerzony test sieciowy gildii; łącznie 322.

## [0.71.0] - 2026-10-16

### Dodano
- Własne znaczniki na mapie: `/znacznik nazwa` zapisuje miejsce, w którym stoisz (żółta kropka na minimapie,
  podpis na mapie piętra), `/znaczniki` pokazuje listę z odległością, a dotknięcie prowadzi postać na miejsce.
  `/znacznik usuń nazwa` kasuje. Do 20 znaczników na postać, zapisywane na urządzeniu.
- Scena GUI `markers` (32 sceny). Wiersz `/znaczniki` w oknie `/pomoc`.
- 1 nowy test; łącznie 321.

## [0.70.0] - 2026-10-16

### Dodano
- Nagrody za progi poziomu 10, 20, 30, 50, 75, 100, 150, 200 i 250: złoto na konto (od 1 000 gp do 625 000 gp)
  i od poziomu 20 zwoje nauki (do plecaka, a gdy brak miejsca do depozytu). Każdy próg raz na postać, także po
  śmierci i ponownym awansie. Istniejące postacie dostają nagrody od swojego obecnego poziomu w górę.
- 2 nowe testy; łącznie 320.

## [0.69.0] - 2026-10-16

### Dodano
- Wiadomość dnia gildii: lider ustawia ją poleceniem `/gildia wiadomość tekst` (pusty tekst usuwa, do 120 znaków).
  Członkowie dostają ją od razu, przy każdym wejściu do gry i w `/gildia`. Zapisywana w guilds.txt
  (stare pliki bez wiadomości wczytują się bez zmian).
- 1 nowy test i rozszerzony test sieciowy gildii; łącznie 318.

## [0.68.0] - 2026-10-16

### Dodano
- Zwój nauki (u kupców w czterech miastach, 2000 gp): UŻYJ w plecaku daje +30% doświadczenia za potwory
  przez godzinę polowania (do 3 godzin zapasu; czas płynie tylko poza strefą ochronną). Działa też w drużynie
  i łączy się z potworem dnia. Pozostały czas widać w oknie analizy polowania.
- 1 nowy test; łącznie 317.

## [0.67.0] - 2026-10-16

### Dodano
- Przelewy złota u bankiera: `/przelew Imię, 5000` (także `5 000`, `5k`, `2kk`). Złoto schodzi z konta nadawcy
  i trafia na konto odbiorcy; gracz offline dostaje je przy następnym wejściu.
- Aktywne zaklęcia z pozostałym czasem w oknie analizy polowania (klawisz H).
- 2 nowe testy (w tym sieciowy); łącznie 316.

## [0.66.0] - 2026-10-16

### Dodano
- Zaklęcia u kowala (okno ulepszania u każdego kupca): kradzież życia (6% zadanych obrażeń leczy),
  kradzież many (3% wraca jako mana) i cios krytyczny (10% szans na +50% obrażeń, także czarami).
  Jeden zakup to godzina polowania, zapas do 3 godzin; czas płynie tylko poza strefą ochronną.
  Cena rośnie z poziomem postaci. Zaklęcia są zapisywane i działają online.
- 2 nowe testy; łącznie 314.

## [0.65.0] - 2026-10-16

### Dodano
- Selvaran: „Skóry węży” (15 wężowych skór, poziom 85) i „Strażnicy świątyni” (10 strażników świątyni, poziom 150,
  po „Wyprawie badaczy”, nagroda 20 wielkich mikstur zdrowia). Frostholm: „Serce mrozu” (3 zamarznięte serca,
  poziom 110, po „Zamieci”, nagroda 15 wielkich mikstur many). Zamyka luki poziomów 70-190 i 80-140.
- Nowe tematy rozmowy u przewodników: świątynia, golemy.

## [0.64.0] - 2026-10-16

### Dodano
- Łańcuch zadań w Qasirah dla poziomów 45-170 (dotąd luka między 55 a 180): „Napadnięta karawana” (12 kłów hien),
  „Szepty w piasku” (10 piaskowych upiorów, nagroda 10 silnych mikstur many) i „Złodziej klejnotów”
  (3 klejnoty skarabeuszy, nagroda Tarcza z piaskowca). Nowy temat rozmowy u przewodnika: karawana.
- Test: cel każdego zadania da się zdobyć w świecie (potwór ma spawn albo przedmiot wypada z takiego potwora).
- 1 nowy test; łącznie 312.

## [0.63.0] - 2026-10-16

### Dodano
- Okno „Polecenia” (`/pomoc`, także po nieznanym poleceniu): polecenia bez argumentów wykonują się po dotknięciu
  (bez pisania na telefonie), a te z argumentami mają przykład i opis. Akcja wiersza `cmd:/polecenie`.
- Scena GUI `help` (31 scen).
- 1 nowy test; łącznie 311.

## [0.62.0] - 2026-10-16

### Dodano
- Kowalstwo u kupców („Ulepsz wyposażenie”): założoną broń, tarczę i zbroję można ulepszyć do +5. Broń zyskuje
  6% ataku na poziom (co najmniej +1), zbroja i tarcza +1 pancerza i obrony. Szansa 100/90/75/55/35%, cena
  max(200 gp × poziom², 2 × wartość × poziom); nieudana próba zabiera tylko złoto.
- Poziom ulepszenia jest częścią przedmiotu (`ItemStack.upgrade`), zapis `idxilość+poziom`. Plecak pokazuje „+N”,
  a porównanie przedmiotów liczy ulepszenia.
- Ulepszonych przedmiotów nie można sprzedać hurtem u kupca, oddać w handlu, wystawić na rynku ani wysłać pocztą
  (liczą się tylko zwykłe sztuki). Depozyt przenosi je bez utraty poziomu. Przy zdejmowaniu przedmiotów
  (płatności, zadania) najpierw idą zwykłe sztuki.
- Scena GUI `smith` (30 scen), opcja `-Dpixel.open=smith`.
- 3 nowe testy; łącznie 310.

## [0.61.0] - 2026-10-16

### Dodano
- `/gracze` (albo `/online`): okno „W grze” z listą graczy online (profesja, poziom, gildia, tytuł),
  od najwyższego poziomu. Bez położenia graczy. Żądanie `WHO` dla każdego (dotąd tylko administrator: `/kto`).
- 1 nowy test sieciowy; łącznie 307.

## [0.60.0] - 2026-10-16

### Dodano
- Karta potwora w bestiariuszu: dotknięcie potwora pokazuje poziom, zdrowie, doświadczenie i atak; od 10
  pokonanych także odporności i pancerz, a od 25 listę łupów z szansami. Działa też online (dane z klienta).
- 2 nowe testy; łącznie 306.

## [0.59.0] - 2026-10-16

### Dodano
- Tytuły: zdobyte osiągnięcie w oknie osiągnięć (O) można dotknąć, aby nosić jego nazwę jako tytuł (ponowne
  dotknięcie go zdejmuje). Tytuł widać na karcie postaci, a online inni gracze widzą go złotym napisem nad
  imieniem (komunikat PL, pole 13; żądanie `TITLE klucz|-`). Zapis postaci (`title`).
- 2 nowe testy (w tym sieciowy: pupil i tytuł widoczne dla innego gracza); łącznie 304.

## [0.58.0] - 2026-10-16

### Dodano
- Pupile: Szczenię (Varden), Kocię (Selvaran), Kruk (Frostholm) i Fenek (Qasirah) u kupców po 5 000 gp.
  UŻYJ w plecaku przywołuje pupila, który chodzi pole za postacią (ponowne UŻYJ go odsyła). Przedmiot się
  nie zużywa; gdy zniknie z plecaka (sprzedaż, handel, wyrzucenie), pupil odchodzi. Zapis postaci (`pet`)
  i online: inni gracze widzą pupila (komunikat PL, pole 12).
- Opcja deweloperska `-Dpixel.open=pet` (`-Dpixel.pet=pet_cat` itd.), scena GUI `pet` (29 scen).
- 2 nowe testy; łącznie 302.

## [0.57.0] - 2026-10-16

### Dodano
- Rzadki łup: przedmiot z szansą bazową najwyżej 1% (dla elit liczona przed podwojeniem) daje wpis
  „Rzadki łup: …!” w dzienniku, a na serwerze pozostali gracze widzą „Imię zdobywa rzadki łup: przedmiot (potwór)!”.
- Potwór dnia ma złoty romb nad głową w świecie (także online).
- 2 nowe testy; łącznie 300.

## [0.56.0] - 2026-10-16

### Dodano
- Potwór dnia: każdego dnia jeden rodzaj potwora (nie boss, ze stałych spawnów) daje +50% doświadczenia
  i +50% szansy na każdy łup, także w odmianie elitarnej i przy podziale w drużynie. Wybór zależy tylko
  od daty, więc klient i serwer liczą go tak samo. Ogłoszenie w dzienniku po wejściu i o północy, wiersz
  w analizie polowania (H) i polecenie `/potwór` (albo `/bonus`).
- Opcja deweloperska `-Dpixel.day=N` ustala dzień gry; przy `-Dpixel.hour` dzień jest stały (regresja GUI).
- 3 nowe testy; łącznie 298.

## [0.55.0] - 2026-10-16

### Dodano
- 4 nowe osiągnięcia (łącznie 19): Czempion (pokonaj potwora elitarnego), Łowca elity (25 elitarnych),
  Mistrz broni (40 w umiejętności walki), Poszukiwacz skarbów (10 skrzyń). Postać liczy pokonane
  potwory elitarne (zapis `elites`).
- Okno osiągnięć ma strony, gdy lista nie mieści się w dwóch kolumnach (mały ekran).
- 1 nowy test; łącznie 295.

## [0.54.0] - 2026-10-16

### Dodano
- Potwory elitarne: około 4% zwykłych potworów pojawia się jako „(elita)”: większe i złociste, 2x zdrowie,
  1,3x obrażenia, 3x doświadczenie i podwójna szansa na każdy łup. Bossowie nie mają odmiany elitarnej.
  Los zależy od numeru potwora (bez zużywania generatora, więc spawny zostają powtarzalne). Online serwer
  oznacza elitę w klatce stanu (`MO ... E`, starsze klienty ignorują to pole).
- `MonsterType.eliteVariant/isElite/eliteRoll`. 3 nowe testy; łącznie 294.

### Poprawiono
- Polskie litery składane w czcionce (ą, ę, ś, ż i inne) nie miały ciemnego obrysu jak reszta liter, więc
  znikały na jasnym tle (śnieg, piasek). Teraz mają obrys jak pozostałe litery. Wzorce regresji GUI odświeżone.

## [0.53.0] - 2026-10-16

### Dodano
- Podsumowanie śmierci: po śmierci samo otwiera się okno „Ostatnia śmierć” z zabójcą, stratą doświadczenia
  i obrażeniami z ostatnich 30 s według źródła (potwory i gracze, suma i liczba ciosów). Ponownie: `/śmierć`.
  Online serwer odsyła podsumowanie na komunikat `DEATH` (odpowiedź `DEATHLOG` z wierszami „etykieta;opis”),
  a klient prosi o nie sam po komunikacie o śmierci.
- `combat/DeathRecap`, `CombatSystem.recordDamage/lastDeath`, trafienia PvP też trafiają do podsumowania,
  podgląd `-Dpixel.open=death`. 3 nowe testy (w tym sieciowy) i nowe sprawdzenia w teście śmierci; łącznie 291.

## [0.52.0] - 2026-10-16

### Dodano
- Ustawienie „Mikstura zdrowia sama”: wyłączona, poniżej 30%, 50% albo 70% HP. Klient sam używa mikstury
  zdrowia z plecaka (ta sama akcja co przycisk HP, najwyżej raz na sekundę, więc online decyduje serwer).
  Domyślnie wyłączone.
- Ustawienie „Pogoda: pokazuj/ukryj” (słabsze telefony, nagrania).
- 1 nowy test; łącznie 288.

### Poprawiono
- Test pojedynku online czasem nie przechodził pod obciążeniem. Prawdopodobna przyczyna (wniosek, nie
  potwierdzona): potwór z okolicy dobijał przegranego przy 1 HP. Test usuwa teraz potwory z okolicy pola walki.

## [0.51.0] - 2026-10-16

### Dodano
- Błogosławieństwo u kapłana: za monety z plecaka (200 gp za poziom, co najmniej 1000 gp) najbliższa śmierć
  zabiera 2,5% zamiast 5% doświadczenia. Działa raz, także przy śmierci z ręki gracza (plecak przy czerwonej
  czaszce i tak zostaje na ziemi). Zapisywane w postaci (`bless`), widoczne na karcie postaci i w rozmowie.
- Karta postaci pokazuje miasto zamieszkania. Nazwy umiejętności z `Skill.label()`.
- 2 nowe testy; łącznie 287.

### Poprawiono
- Niestabilny test sieciowy znajomych: lista była pobierana, zanim serwer obsłużył wyjście znajomego.

## [0.50.0] - 2026-10-16

### Dodano
- Woźnice: w każdym z 4 miast przy fontannie przed świątynią stoi woźnica (nowa rola NPC `CARRIER`).
  W rozmowie wybierasz miasto, płacisz monetami z plecaka (3 gp za pole odległości, co najmniej 100 gp;
  z Varden: Frostholm 160, Selvaran 260, Qasirah 340 gp) i stajesz obok woźnicy w mieście docelowym.
  Po walce z graczem trzeba odczekać blokadę. Działa lokalnie i online (akcja `npc:travel:Miasto`).
- `npc/Travel` (ceny i trasy), rozmowa o podróży, strój woźnicy (kapelusz, bat), woźnice w generatorze mapy.
  3 nowe testy; łącznie 285.

## [0.49.0] - 2026-10-16

### Dodano
- Pogoda na powierzchni: w około 30% dwudziestominutowych okien pada. Rodzaj zależy od krainy: deszcz
  (Varden, dżungla), śnieg (mroźne krainy), burza piaskowa (pustynia Qasirah). Opady narastają i zanikają
  na brzegach okna, lekko przyciemniają świat, a zmiana pogody pojawia się w dzienniku. Pod ziemią zawsze
  pogodnie. Pogoda wynika z zegara i nazwy krainy, więc gracze online widzą to samo bez nowych komunikatów.
  Przy oszczędzaniu zasobów rysowanych jest mniej kropli.
- Analiza polowania liczy też wartość zebranych przedmiotów (cena z items.csv) i jej tempo na godzinę.
- `world/Weather`, `client/render/WeatherRenderer` (bez stanu, bez alokacji), opcja `-Dpixel.weather=rain|snow|sandstorm|clear`,
  scena `rain` w regresji GUI (28 scen). Manekin ma ciemny obrys (widoczny na piasku). 3 nowe testy; łącznie 282.

## [0.48.0] - 2026-10-16

### Dodano
- Manekiny treningowe: po 2 w strefie ochronnej każdego miasta (w Varden przy fontannie przed świątynią).
  UŻYJ obok manekina (albo dotknięcie go) zaczyna trening: co 3 s próba umiejętności broni (pięści, miecz,
  dystans albo magia, zależnie od broni) i co 6 s tarczy, bez doświadczenia i bez kosztów. Ruch, śmierć albo
  ponowne UŻYJ kończy trening; awans umiejętności pojawia się w dzienniku. Dotknięcie dalekiego manekina
  prowadzi do niego. Działa lokalnie i online (logika w sesji, serwer liczy te same miejsca z mapy).
- `training/TrainingDummies` (miejsca liczone powtarzalnie z mapy i pozycji NPC), `Skill.label()`, grafika
  `TileArt.dummy`. 2 nowe testy; łącznie 279.

## [0.47.0] - 2026-10-16

### Dodano
- Analiza polowania: okno z czasem polowania, zdobytym doświadczeniem, liczbą zabitych potworów i bilansem złota
  w plecaku, każde także na godzinę, oraz czasem do następnego poziomu przy obecnym tempie. Otwiera je klawisz H,
  `/analiza` (albo `/polowanie`) lub wiersz w księdze; okno odświeża się co sekundę, „Zacznij od nowa” zeruje liczniki.
  Liczy z danych postaci, więc działa lokalnie i online.
- `stats/HuntAnalyzer`, `Hud.infoOpen/updateInfo`, wiersze okna informacyjnego mogą mieć akcję. 3 nowe testy; łącznie 277.

## [0.46.0] - 2026-10-16

### Dodano
- Zwój powrotu (`return_scroll`, 300 gp u kupców we wszystkich 4 miastach): użyty z plecaka po 3 s skupienia
  przenosi do świątyni miasta postaci. Ruch albo otrzymane obrażenia przerywają skupienie (zwój zostaje),
  nie działa w czasie blokady po walce z graczem. Zwój znika dopiero po udanym przeniesieniu. Działa lokalnie
  i online (logika w sesji, serwer wykonuje ją przy ACT slot_use).
- Test zwoju (przeniesienie, przerwanie ruchem); łącznie 274.

## [0.45.0] - 2026-10-16

### Dodano
- Opis przedmiotu w plecaku: po dotknięciu przedmiotu nad plecakiem pojawia się ramka ze statystykami i różnicą
  względem tego, co postać ma założone w tym miejscu (np. „atak 24 (+4)”), oraz z wymaganiami: poziom
  („za niski”), profesje („nie dla ciebie”) i wartość.
- `item/ItemCompare`, scena `bagitem` w regresji GUI (27 scen). 2 nowe testy; łącznie 273.

## [0.44.0] - 2026-10-16

### Dodano
- Licznik bossów: okno „Bossowie” (wiersz u każdego przewodnika albo `/bossowie`) z listą bossów świata, miejscem
  (kraina i piętro) i stanem: „żyje”, „odrodzi się za X min” (albo h) lub „odradza się lada chwila”.
  Online dane przychodzą z serwera (komunikat BOSS), lokalnie z własnego świata.
- Okno informacyjne w HUD (`Hud.openInfo`) do takich list. Scena `bosses` w regresji GUI (26 scen).
- 3 nowe testy (raport i czas odrodzenia, komunikat serwera, komenda czatu); łącznie 271.

## [0.43.0] - 2026-10-15

### Dodano
- Automatyczne łupy (domyślnie włączone, przełącznik „Łupy z ciał” w panelu ustawień pod „i”): ciało z łupem
  na polu postaci albo obok jest przeszukiwane samo, co 0,8 s, lokalnie i online. Przy pełnym plecaku najwyżej
  dwie próby na ciało, bez zalewania dziennika ostrzeżeniami.
- Ustawienie zapisywane (`loot` w Preferences). Sprawdzone w grze: walka z goblinami w kopalni, 78 gp z ciał
  trafiło do plecaka bez dotykania. Test ustawień; łącznie 269.

## [0.42.0] - 2026-10-15

### Dodano
- Dziennik zadań (klawisz Z, `/zadania`, wiersz „Dziennik zadań” w księdze): zadanie dnia, zadania w toku
  z postępem i zleceniodawcą (ODDAJ, gdy gotowe) oraz nowe zadania dostępne na obecnym poziomie.
- Dotknięcie zadania prowadzi postać do zleceniodawcy (zadanie dnia: do najbliższego przewodnika) i otwiera
  rozmowę po dojściu. Sprawdzone w grze: spod bramy Varden do Strażnika Orena.
- Opcja deweloperska `-Dpixel.button=akcja`, scena `journal` w regresji GUI (25 scen). Testy 268/268.

## [0.41.0] - 2026-10-15

### Dodano
- Dymki czatu: wiadomość z czatu ogólnego pojawia się nad głową mówiącego gracza (także nad własną postacią)
  na 4-9 s, z ciemnym tłem, zawijana do kilku linii i wygaszana na końcu. Czat prywatny, drużyny i gildii
  zostaje tylko w dzienniku.
- Scena `chat` w regresji GUI (24 sceny), opcja `-Dpixel.open=chat`. Testy 268/268.

## [0.40.0] - 2026-10-15

### Dodano
- Nagroda za codzienne wejście do gry: seria 7 dni (300, 500, 800, 1 000, 1 500, 2 000 i 5 000 gp, razy poziom/10
  od poziomu 20), złoto na konto, raz dziennie, z komunikatem w dzienniku. Przerwa zeruje serię, po 7. dniu
  zaczyna się nowy tydzień. Działa lokalnie i online (serwer liczy dzień), zapis `login`.
- 3 nowe testy (seria i zapis, nagroda w sesji); testy sieciowe sald uwzględniają nagrodę. Łącznie 268.
  Odświeżone wzorce GUI (komunikat w dzienniku).

## [0.39.0] - 2026-10-15

### Dodano
- Pojedynki (online): `/pojedynek Imię` wyzywa gracza stojącego najwyżej 8 pól dalej, ta sama komenda drugiej
  strony przyjmuje wyzwanie (ważne minutę); także wiersz „Pojedynek” w menu gracza. Walka do pierwszej krwi:
  cios, który by zabił, kończy pojedynek przy 1 HP przegranego. Bez czaszek, blokady strefy ochronnej i ochrony
  poziomu 30 (pojedynkować się mogą też nowe postacie i członkowie drużyny), obrażenia jak w PvP (x0,5).
- Koniec: zwycięstwo, `/pojedynek poddaj`, wyjście z gry (walkower), śmierć od potwora lub remis po 5 minutach.
  Obie strony przestają się atakować, gracze w pobliżu widzą wynik.
- Test sieciowy pojedynku (poziom 8, 1 HP, brak czaszki, potem zwykłe zasady) i test komend; łącznie 265.

## [0.38.0] - 2026-10-15

### Dodano
- Dotknij, aby iść: dotknięcie wolnego pola na ekranie prowadzi tam postać tą samą trasą co marsz z mapy
  (potwór dalej oznacza cel, gracz online otwiera menu gracza).
- Dotknięcie dalekiego NPC prowadzi na pole obok niego i samo otwiera rozmowę po dojściu; dotknięcie dalekiego
  ciała z łupem prowadzi obok niego.
- Opcja deweloperska `-Dpixel.tap=x,y` (dotknięcie pola). Sprawdzone w grze: marsz spod bramy do Strażnika Orena
  kończy się otwartą rozmową. Regresja GUI bez zmian (23/23), testy 264/264.

## [0.37.0] - 2026-10-15

### Dodano
- Marsz do celu: dotknięcie miejsca na dużej mapie wyznacza najkrótszą trasę po piętrze (z ominięciem schodów
  i drabin) i postać sama tam idzie, lokalnie i online. Cel widać na minimapie i dużej mapie (niebieski znacznik).
  Joystick albo klawisze przerywają marsz; zepchnięta z trasy postać wyznacza ją od nowa.
- `Pathfinder.path` - pełna trasa BFS na tablicach (całe piętro w jednym wywołaniu); opcja deweloperska
  `-Dpixel.walkto=x,y`. 2 nowe testy (ciągłość trasy, cele nieosiągalne); łącznie 264.

## [0.36.0] - 2026-10-15

### Dodano
- Minimapa w prawym górnym rogu (60 x 60 pól wokół postaci): ściany, woda, przeszkody, schody i drabiny (żółte),
  czerwony znacznik gracza. Działa na każdym piętrze; na niskim ekranie maleje albo znika, by nie zasłaniać przycisków.
- Duża mapa całego piętra z nazwami miast: dotknięcie minimapy albo klawisz J (zamyka dotknięcie, J lub Esc).
- Nowa scena `map` w regresji GUI (23 sceny), odświeżone wzorce scen z minimapą; 2 nowe testy kolorów; łącznie 262.

## [0.35.0] - 2026-10-15

### Dodano
- Skrzynie skarbów: po 4 na każdym piętrze lochów (-1..-5) i po 2 na wyżynach (+1..+3), z dala od schodów i przejść,
  zawsze osiągalne z miasta. Każda postać otwiera każdą skrzynię raz (UŻYJ obok skrzyni): złoto od 500 gp na -1
  do 40 500 gp na -5 i mikstury coraz lepsze z głębokością. Pełny plecak - skrzynia czeka.
- Skrzynie rysowane na mapie (zamknięta / otwarta dla tej postaci), działają lokalnie i online; zapis `chests`.
- Nowa scena `chest` w regresji GUI (22 sceny); 2 nowe testy (rozmieszczenie, osiągalność, otwieranie raz,
  zapis); łącznie 260.

## [0.34.0] - 2026-10-15

### Dodano
- Poczta (online, u bankiera): `/wyślij Imię, 5 wilcze futro` wysyła przedmioty z plecaka do depozytu innej postaci.
  Odbiorca w grze dostaje je od razu (z wiadomością), a nieobecny - przy następnym wejściu. Gdy depozyt jest pełny,
  przesyłka czeka. Do 50 czekających przesyłek na odbiorcę, zapis w `mail.txt` na serwerze (przetrwa restart),
  usunięcie konta kasuje jego przesyłki. Złota nie wysyła się pocztą.
- 2 nowe testy (skrzynka i zapis, sieciowy do gracza w grze i offline); łącznie 258.

## [0.33.0] - 2026-10-15

### Dodano
- Wierzchowiec: siodło (50 000 gp u kupców w Varden, Frostholm, Qasirah i Selvaran) użyte z plecaka oswaja konia
  na stałe. Przycisk KOŃ (klawisz M) - wsiadanie i zsiadanie, +60 szybkości (na poziomie 8 o ok. 25% szybciej).
  Pod ziemią nie można jechać - przy zejściu postać zostawia konia.
- Koń rysowany pod postacią (własny sprite w atlasie), widoczny też u innych graczy (pole `M` w komunikacie PL).
- Zapis: flagi `H` (ma konia) i `M` (jedzie).
- Nowa scena `mount` w regresji GUI (21 scen); 2 nowe testy (sklep, siodło, szybkość, zapis, podziemia; sieciowy:
  inni widzą jeźdźca); łącznie 256.

## [0.32.0] - 2026-10-15

### Dodano
- Zadanie dnia u przewodnika w każdym mieście: raz dziennie (data kalendarzowa) pokonaj 20-40 potworów
  jednego rodzaju dobranego do poziomu (nie boss, między połową a 120% poziomu postaci). Nagroda: podwójne
  doświadczenie za te potwory i złoto na konto. Postęp w oknie przewodnika ("Zadanie dnia: Wilk 12 / 31"),
  wpis w dzienniku po wykonaniu. Działa lokalnie i online; zapis w postaci (`daily`).
- Nowa scena `guide` w regresji GUI (20 scen).
- 2 nowe testy (dobór potwora dla poziomów 8-1000, cały przebieg dnia z nagrodą i nowym dniem); łącznie 254.

### Poprawiono
- "ukończone" z polską literą w liście zadań przewodnika.

## [0.31.0] - 2026-10-15

### Dodano
- Inwazje na miasta (serwer): co 120 minut (`-Dpixel.invasionMinutes`) grupa potworów z hersztem pojawia się
  za murami kolejnego miasta - Varden (bandyci, troll), Frostholm (śnieżne wilki, yeti), Qasirah (hieny, skorpion),
  Selvaran (krokodyle, wąż). Ogłoszenie dla wszystkich z kierunkiem ataku, komunikaty o hersztcie i połowie wrogów.
- Każdy pokonany najeźdźca liczy się graczowi; po obronie (albo po 20 minutach) 100 gp za wroga i 1 000 gp
  za herszta na konto, ogłoszenie najlepszego obrońcy. Najeźdźcy się nie odradzają, po czasie odchodzą.
- Administrator: `/inwazja [miasto]` (konsola: `invasion`) uruchamia inwazję od razu.
- Wchodzący do gry w czasie inwazji dostaje informację, ile wrogów zostało.
- 4 nowe testy (liczenie i nagrody, miejsca zbiórki we wszystkich miastach, potwory bez odradzania, sieciowy
  od ogłoszenia do nagrody); łącznie 252.

## [0.30.0] - 2026-10-15

### Dodano
- Bestiariusz: postać liczy pokonane potwory każdego rodzaju (i bossów); zapis `kill.<potwór>`.
- 15 osiągnięć (poziom 20/50/100/300, 100/1 000/10 000 potworów, 10/30 rodzajów, pierwszy boss i 5 bossów,
  5/20 zadań, 1 000 000 gp na koncie, 5 znajomych). Zdobyte raz zostają; wpis "Osiągnięcie zdobyte: ..." w dzienniku.
  Działa w grze lokalnej i online (liczy serwer).
- Okno osiągnięć z postępem (klawisz O, `/osiągnięcia`) przełączane na bestiariusz (`/bestiariusz`). Karta postaci
  pokazuje liczbę pokonanych potworów i osiągnięć.
- `-Dpixel.open=achievements|bestiary`; 2 nowe sceny w regresji GUI (19 scen), wzorzec `character` odświeżony.
- 3 nowe testy osiągnięć i bestiariusza, liczenie w teście zadań; łącznie 248.

## [0.29.0] - 2026-10-15

### Dodano
- Rynek graczy (online, u bankiera w każdym mieście): `/rynek wystaw 5 wilcze futro 300` wystawia przedmioty
  z plecaka za cenę za całość, `/rynek` pokazuje okno (cudze oferty po lewej - dotknięcie kupuje,
  własne po prawej - dotknięcie wycofuje), `/rynek nazwa` szuka, `/rynek kup 12`, `/rynek wycofaj 12`.
  U bankiera nowy wiersz "Rynek graczy".
- Kupujący płaci z konta, a gdy na nim brakuje - monetami z plecaka. Sprzedawca dostaje cenę minus 2% prowizji
  na konto od razu, a gdy jest offline - przy następnym wejściu do gry (z wiadomością).
- Do 10 ofert na gracza; oferty i zaległe wypłaty w `market.txt` na serwerze (zapis atomowy), przetrwają restart.
  Po każdej transakcji zapis postaci (bez podwajania przedmiotów po awarii). Usunięcie konta kasuje jego oferty.
- `-Dpixel.open=market` - podgląd; nowa scena `market` w regresji GUI (17 scen).
- 4 nowe testy (rynek, zapis, komunikat, sieciowy z zakupem od gracza offline); łącznie 245.

## [0.28.0] - 2026-10-15

### Dodano
- Lista znajomych (do 30 postaci): `/znajomi` otwiera okno (w grze po lewej, poza grą po prawej, z profesją
  i poziomem), `/znajomi dodaj Imię`, `/znajomi usuń Imię`. W menu gracza (dotknięcie postaci) nowy wiersz
  "Dodaj do znajomych". Dotknięcie znajomego w grze otwiera czat z gotowym `/w Imię, `.
- Powiadomienia: "Znajomy X jest teraz w grze." / "wyszedł z gry.", a po wejściu lista znajomych online.
- Serwer przyjmuje tylko istniejące postacie (online albo z rankingu); imię zapisuje tak, jak ma je postać.
  Lista zapisuje się z postacią (`fr.<i>`).
- `-Dpixel.open=friends` - podgląd; nowa scena `friends` w regresji GUI (16 scen), wzorzec `player` odświeżony.
- 4 nowe testy (lista, zapis, komunikat, sieciowy); łącznie 241.

## [0.27.0] - 2026-10-15

### Dodano
- Depozyt przedmiotow w banku: 60 miejsc, wspolny dla wszystkich miast (jak konto). U bankiera wiersz
  "Depozyt przedmiotów" przelacza okno na widok depozytu: po lewej "Wyjmij Nx Nazwa", po prawej "Odłóż Nx Nazwa"
  z plecaka (wszystkie sztuki, ile sie zmiesci). Monety nie trafiaja do depozytu - od tego jest konto.
- Depozyt zapisuje sie razem z postacia (`dp.<slot>` w zapisie) i dziala online (akcje `npc:store:<id>`,
  `npc:take:<id>` wykonuje serwer, stan wraca w SELF).
- `-Dpixel.open=depot` - podglad; nowa scena `depot` w regresji GUI (15 scen).
- Test `depotStoresItemsOnlyAtABankerKeepsCoinsOutAndSurvivesSaving`; lacznie 237 testow.

## [0.26.0] - 2026-10-15

### Dodano
- Ranking postaci na serwerze: `/ranking` (cały serwer) albo `/ranking druid|paladin|mag|mnich` otwiera okno
  z 10 pierwszymi miejscami (imie, profesja, poziom) i miejscem gracza. Kolejnosc wg doswiadczenia.
- Ranking obejmuje tez postacie offline: serwer buduje go przy starcie z zapisow, odswieza przy kazdym zapisie,
  usuwa konto po jego skasowaniu.
- `-Dpixel.open=ranking` - podglad okna; nowa scena `ranking` w regresji GUI (14 scen).
- 5 nowych testow, w tym sieciowy (ranking po restarcie serwera, filtr profesji) - lacznie 236.

## [0.25.0] - 2026-10-15

### Zmieniono
- Polskie nazwy wszystkich 108 przedmiotow i 42 potworow (np. Mikstura zdrowia, Wilcze futro, Wilk,
  Grimtooth, Król Kopalni). Klucze danych (`health_potion`, `wolf`) bez zmian, zapisy postaci zgodne.
- Handel i `/oferuj` przyjmuja polska nazwe (z polskimi znakami albo bez) i angielski klucz:
  `/oferuj 5 mikstura zdrowia` = `/oferuj 5 health potion`.
- Waluty: Zlota, Platynowa i Krysztalowa moneta.

## [0.24.0] - 2026-10-15

### Dodano
- Polskie litery w grze: czcionka domyslna libGDX uzupelniona w pamieci o ą ć ę ł ń ś ź ż (i wielkie), skladane
  z liter bazowych, rysowanej kreski / kropki i ogonka z odbitej cedylli (`client/ui/PolishFont`, bez nowych plikow).
  Czat z polskimi znakami wreszcie sie wyswietla.
- Polecenia czatu dzialaja z polskimi znakami i bez (`/zaproś` = `/zapros`, `/gildia załóż` = `/gildia zaloz`),
  handel przyjmuje `złoto`.
- Test `PolishFontTest` (kazda litera istnieje i ma znak diakrytyczny); lacznie 232 testy.

### Zmieniono
- Napisy interfejsu, komunikaty serwera, opisy przedmiotow, potworow, czarow, zadan, NPC i rozmowy z pelnymi polskimi
  znakami (np. UŻYJ, KSIĘGA, ŁÓW, "Zginąłeś", "Wędkarstwo"). Tematy rozmow z NPC wyswietlane z polskimi znakami.
- Wzorce regresji GUI odswiezone (13 scen).

## [0.23.0] - 2026-10-15

### Dodano
- Prywatne wiadomosci: `/w Imie, tekst` (z limitem spamu).
- Moderacja serwera: administratorzy z `admins.txt`, polecenia czatu `/ogloszenie`, `/kick`, `/ban Imie [godziny]`,
  `/unban`, `/wycisz Imie [minuty]`, `/kto` oraz te same polecenia w konsoli serwera (stdin, `docker attach`).
  Bany i wyciszenia zapisywane w `moderation.txt`, przetrwaja restart; zbanowany nie zaloguje sie do konca bana,
  wyciszony nie pisze na zadnym czacie.
- 2 nowe testy, w tym sieciowy (bany po restarcie i wygasanie) (lacznie 231).

## [0.22.0] - 2026-10-15

### Dodano
- Obraz Docker serwera (`server/Dockerfile`, uzytkownik bez uprawnien root, dane w wolumenie `/data`) i instrukcja
  `docs/SERVER.md`: Docker, systemd, kopie zapasowe, bezpieczenstwo, aktualizacja.
- Zadanie CI `server-image`: budowa obrazu, start kontenera, sprawdzenie TLS i restartu z zachowaniem certyfikatu.

## [0.21.2] - 2026-10-15

### Dodano
- Panel druzyny po lewej stronie ekranu: czlonkowie z HP w procentach (zielone / zolte / czerwone), "daleko" gdy
  poza zasiegiem wzroku - leczacy od razu widzi, komu pomoc.
- `-Dpixel.open=party` - podglad panelu; nowa scena `party` w regresji GUI (13 scen).

## [0.21.1] - 2026-10-15

### Zmieniono
- Dotkniecie innego gracza otwiera menu gracza: Handel, Zapros do druzyny, Zapros do gildii, Atakuj / Przerwij atak.
  Atak wymaga swiadomego wyboru (wczesniej dotkniecie od razu atakowalo - latwo bylo przypadkiem dostac czaszke).
  Czlonka wlasnej druzyny nie mozna wybrac do ataku.

### Dodano
- `-Dpixel.open=player` - podglad menu gracza; nowa scena `player` w regresji GUI (12 scen).

## [0.21.0] - 2026-10-15

### Dodano
- Gildie (trwale, zapisywane na serwerze w `guilds.txt`, objete kopiami zapasowymi): `/gildia zaloz Nazwa`
  (50 000 gp z plecaka), `/gildia zapros Imie`, `/gildia przyjmij Nazwa`, `/gildia wyrzuc Imie`, `/gildia opusc`,
  `/gildia` (informacje), czat gildii `/g tekst`. Do 50 czlonkow; po wyjsciu lidera przywodztwo przechodzi na
  najdluzej obecnego czlonka, pusta gildia znika. Nazwa gildii widoczna przy imieniu gracza.
- 4 nowe testy, w tym sieciowy z restartem serwera (lacznie 229).

## [0.20.1] - 2026-10-15

### Dodano
- Okno handlu w interfejsie (wygodne na telefonie): oferty obu stron ze stanem akceptacji, AKCEPTUJ, ANULUJ,
  dodawanie zlota (+100 / +1 000 / +10 000 gp) i przedmiotow z plecaka jednym dotknieciem. Otwiera sie samo po
  rozpoczeciu wymiany (`/handel Imie`), zamkniecie X anuluje wymiane. Serwer wysyla stan wymiany (`TS`).
- `-Dpixel.open=trade` - podglad okna handlu; nowa scena `trade` w regresji GUI (11 scen).
- 1 nowy test, sieciowy test handlu sprawdza tez stan okna (lacznie 225).

## [0.20.0] - 2026-10-15

### Dodano
- Handel miedzy graczami online, twarza w twarz (maks. 2 pola): `/handel Imie` (druga strona odpowiada tym samym),
  `/oferuj 5 health potion`, `/oferuj 1000 gp`, `/akceptuj`, `/anuluj`. Kazda zmiana oferty kasuje akceptacje,
  wymiana dzieje sie po akceptacji obu stron atomowo (albo cala, albo wcale), a potem oba zapisy postaci sa zapisywane.
  Wymiane przerywa oddalenie, wylogowanie albo 3 minuty bez zmian.
- 4 nowe testy, w tym sieciowy (lacznie 224).

## [0.19.0] - 2026-10-15

### Dodano
- Druzyny online (do 8 osob): `/zapros Imie`, `/przyjmij Imie` (zaproszenie wazne 60 s), `/opusc`, czat druzyny `/d tekst`.
- Wspolne doswiadczenie: gdy co najmniej dwoch czlonkow jest na tym samym pietrze w promieniu 30 kafelkow i poziomy sa
  zblizone (najnizszy >= 2/3 najwyzszego), kazdy dostaje (1 + 20% za kazdego kolejnego) / liczba osob doswiadczenia,
  jakie zdobylby sam: 2 osoby po 60%, 4 po 40%. Wysoki poziom nie "wyholuje" nowej postaci.
- Czlonkowie druzyny nie moga sie atakowac; ich imiona sa niebieskie z dopiskiem [druzyna].
- 5 nowych testow, w tym sieciowy (lacznie 220).

## [0.18.1] - 2026-10-15

### Dodano
- Pomiar plynnosci: klient co 30 s zapisuje w logu FPS, mediane, 95. percentyl i najdluzsza klatke
  (na Androidzie logcat, tag `Pixel`).
- Test APK na emulatorze Androida w CI (zadanie `android-smoke`): instalacja, start gry z pominieciem menu,
  75 s gry, zrzut ekranu i logcat jako artefakt. Ten sam skrypt `tools/android-smoke/run.sh` dziala z telefonem przez adb.
- APK debug przyjmuje `--es autostart Imie:PROFESJA` (tylko wersja debug).
- 1 nowy test (lacznie 215).

## [0.18.0] - 2026-10-15

### Dodano
- Dzwieki: 13 efektow syntezowanych w kodzie (trafienie, rana, blok, strzal, leczenie, szesc zywiolow czarow,
  awans, smierc). Graja przy zdarzeniach walki, ciszej z daleka, takze online.
- Ustawienie glosnosci w panelu ustawien: wylaczony / 30 / 60 / 100%.
- Animacja ataku wrecz: atakujacy (gracz, potwor, inny gracz) robi krotki wypad w strone celu, na celu widac ciecie.
- 5 nowych testow (lacznie 214).

## [0.17.1] - 2026-10-15

### Dodano
- Wyzyny poza wieza Varden: Mrozne Turnie (pietra +1..+3, poziomy 40-90, wilki sniezne, yeti, lodowe wiedzmy)
  i Plaskowyz Qasirah (pietra +1..+2, poziomy 45-130, skorpiony, hieny, piaskowe upiory). Wejscia po
  schodach z powierzchni, generowane deterministycznie.
- Generator mapy zapisuje teraz takze nocne spawny (osme pole "noc"), wiec `spawns.csv` nie wymaga recznych zmian.

### Poprawiono
- Niestabilny test sieciowy: losowe haslo magazynu TLS zaczynajace sie od "-J" bylo czytane przez keytool jako
  opcja JVM (czerwone CI dla 2cdebc8). Haslo ma teraz staly prefiks.

## [0.17.0] - 2026-10-15

### Dodano
- Ciala potworow: lup, ktorego nie zabral autoloot, czeka w ciele do przeszukania (UZYJ, PODNIES albo dotkniecie
  obok). Cialo znika po 3 minutach. Widoczne takze online.
- Przedmioty na ziemi znikaja po 30 minutach (serwer nie gromadzi ich bez konca).
- 4 nowe testy, w tym sieciowy (lacznie 209).

## [0.16.2] - 2026-10-15

### Dodano
- Wlasny uklad paska czarow: w ksiedze "Ustaw pasek czarow", potem dotkniecie czaru; zapisywany osobno dla
  kazdej postaci. Puste przyciski dzialaja jak dotad automatycznie.
- 2 nowe testy (lacznie 206).

### Poprawiono
- Regresja GUI izoluje katalog domowy Javy (`-Duser.home`), wiec zapamietany login i ustawienia z komputera
  nie wplywaja na zrzuty. Wzorce odswiezone.

## [0.16.1] - 2026-10-15

### Dodano
- Efekty graficzne: pociski czarow, run i atakow dystansowych (takze potworow), fala czarow obszarowych,
  iskry leczenia. Kolory wg zywiolu, uproszczone w trybie oszczedzania baterii. Dzialaja takze online.
- `-Dpixel.fx=true` - podglad efektow na zrzucie ekranu.
- Testy czarow sprawdzaja emitowane efekty (lacznie 204).

## [0.16.0] - 2026-10-15

### Dodano
- Zadania wieloetapowe z nagrodami w przedmiotach (`data/quest_chains.csv`): przemytnicy Varden (3 etapy),
  zaginiona wyprawa w Selvaran (2 etapy), zamiec we Frostholm. Nastepny etap pojawia sie po ukonczeniu
  poprzedniego; przy pelnym plecaku nagroda czeka.
- Rozmowa z NPC slowami kluczowymi (`data/dialogues.csv`): tematy w oknie rozmowy i czat obok NPC
  (takze w grze lokalnej), z tolerancja polskich znakow i odmiany. Wskazowki o miastach, lochach, rybach i nocy.
- Wzorce regresji GUI zaktualizowane (przycisk CZAT obok NPC, tematy w oknie kupca).
- 6 nowych testow (lacznie 204).

## [0.15.3] - 2026-10-15

### Dodano
- Nocne spawny: o zmroku na powierzchni pojawia sie 6 dodatkowych grup nocnych drapieznikow (wilki i bandyci
  przy Varden, sniezne wilki, hieny, piaskowe upiory, upiorni rycerze). Za dnia znikaja, ale nigdy na oczach
  gracza ani w trakcie walki. Spawny przy Varden nie przekraczaja poziomu 15.
- 2 nowe testy (lacznie 198).

## [0.15.2] - 2026-10-15

### Dodano
- Ryby zalezne od biomu (`data/fishing.csv`): lodowy dorsz we Mroznych Pustkowiach, wegorz bagienny w dzungli
  Selvaran (lowi sie z bagien), karp z oazy w Qasirah. Wymagaja progu umiejetnosci, dluzej syca,
  skupuje je lokalny kupiec. Ryby maja w plecaku wlasna ikone.
- Test ryb biomow na prawdziwej mapie (lacznie 196 testow).

## [0.15.1] - 2026-10-15

### Dodano
- Automatyczna regresja GUI: `tools/gui-regression/run.sh` robi zrzuty 10 kluczowych scen (menu, logowanie,
  Varden w dzien i w nocy, plecak, ksiega czarow, karta postaci, sklep, kopalnia, oboz na wschodzie)
  i porownuje je z wzorcami (polowa rozdzielczosci, tolerancja koloru, prog 3% pikseli). Nowe zadanie CI.
- `-Dpixel.seed` (stale ziarno potworow) i `-Dpixel.open=diag` w launcherze desktopowym.
- 2 nowe testy porownywarki (lacznie 195).

## [0.15.0] - 2026-10-15

### Dodano
- Ustawienia gracza pod panelem diagnostyki (`i` / F3), zmieniane dotknieciem: limit FPS 30/60/90/120,
  oszczedzanie baterii (30 FPS i swiatlo w polowie jakosci), jakosc swiatla, liczby obrazen.
- Ustawienia zapisywane w Preferences `pixel-settings`, bledne wartosci wracaja do domyslnych.
  Ochrona zasobow 90%/75% moze je tylko dodatkowo obnizyc.
- 2 nowe testy (lacznie 193).

## [0.14.1] - 2026-10-15

### Dodano
- Zmiana hasla (`/haslo stare nowe`) i usuniecie konta wraz z postacia (`/usun-konto haslo`) z okna czatu;
  hasla nie trafiaja na czat. Te same blokady po blednych probach co przy logowaniu.
- Limit 5 nowych kont na dobe z jednego adresu IP.
- 3 nowe testy (lacznie 191).

## [0.14.0] - 2026-10-15

### Dodano
- PvP czarami i runami: ofensywne czary i runy trafiaja gracza wybranego jako cel PvP (gdy nie ma celu-potwora),
  z tymi samymi zasadami co atak (ochrona do poziomu 30, PZ, roznica poziomow, czaszki, blokada PZ, obrazenia x0,5).
  Gdy zasady odmawiaja, mana i runa nie sa zuzywane.
- Test PvP czarami (lacznie 188 testow).

## [0.13.2] - 2026-10-15

### Dodano
- PvP: po kazdym ataku na gracza strefa ochronna jest zamknieta dla obu stron przez 60 s (takze schodami).
- Czaszki i nieusprawiedliwione zabojstwa zapisywane na dysku serwera - przetrwaja restart.
- Protokol 3: niezmieniona klatka stanu wysylana jako krotkie `KEEP` (mniej transferu na telefonie).
- Test budzetu wydajnosci serwera: 200 graczy na calej mapie, ok. 0,5 ms na tik AI.
- 4 nowe testy (lacznie 187).

## [0.13.1] - 2026-10-15

### Dodano
- Oboz Zwiadowcow za mostem: kwatermistrz (mikstury, skup materialow i sprzetu endgame), skarbniczka
  (wspolne konto bankowe) i zwiadowczyni z 9 zadaniami dla poziomow 230-900 (w tym na 4 bossow).
  Kaplana nie ma - oboz nie jest miastem, odrodzenie zostaje w swiatyni.
- Najsilniejsze mikstury: Supreme Health Potion (800-1100 HP, paladyn i mnich) i Supreme Mana Potion
  (400-600 many), od poziomu 200.
- 28 receptur kuzni endgame: kazdy element sprzetu progow 250/450/700/850 (bez korony Otchlani) z materialow
  swojego progu i poprzedniego oraz zlota. Wykucie i sprzedaz zawsze sie nie oplaca.
- Ksiega rzemiosla pokazuje receptury do poziomu postaci + 100 i ma strony.

### Naprawiono
- Test sieciowy walki teleportuje postac obok zywego wilka (wczesniej czasem czekal na wilka, ktory odszedl).

## [0.13.0] - 2026-10-15

### Dodano
- Endgame dla poziomow 230-1000: wschodni kontynent za ciesnina (mapa 256 x 232), most ze straznikiem
  wpuszczajacym dopiero od poziomu 200 i oboz (strefa ochronna) za mostem.
- 5 krain: Przeklete Knieje (230-300), Spalone Wyzyny (300-400, krater Ignarotha), Lodowe Szczyty
  (400-560, wnetrze gor +1..+2 z Hrimgarem), Pustkowia Cienia (550-750, ruiny Kha'zuula) i Otchlan
  (pietra -1..-5, 750-1000, Mor'gath na -5).
- 17 potworow (w tym 4 bossow dla druzyn), 33 przedmioty: 4 komplety broni (poziom 250/450/700/850),
  3 komplety zbroi, korona Otchlani, 4 materialy; lupy dla wszystkich nowych potworow.
- Nowe kafelki: przekleta trawa, popiol, lawa, ciemny piasek, kosci, martwe drzewa, obsydian, posadzka Otchlani.
- Przejscia strzezone poziomem (`gates=` w map.properties), sprawdzane takze przy przewidywaniu ruchu online.
- Symulacje endgame z leczeniem (`EndgameSimulationTest`), testy straznika mostu (lacznie 181 testow).

### Zmieniono
- Generator mapy: zachodni kontynent losowany ta sama sekwencja co wczesniej (identyczny poza przedluzeniem
  wschodniego traktu na most).

## [0.12.1] - 2026-10-14

### Dodano
- Odpornosc serwera: wysylka do kazdego klienta w osobnym watku (wolny telefon nie spowalnia swiata),
  limit 300 polaczen i 8 z jednego adresu, 40 wiadomosci/s (zapas 80), linia do 2048 znakow,
  15 s na zalogowanie, czat najwyzej 5 wiadomosci na 10 s.
- Kopie zapasowe katalogu serwera (zip w `backups/`) przy starcie i co 6 h, 7 najnowszych.
- 3 nowe testy (lacznie 174).

## [0.12.0] - 2026-10-14

### Dodano
- Szyfrowanie TLS miedzy klientem a serwerem. Serwer przy pierwszym starcie tworzy wlasny certyfikat
  (w katalogu zapisow, poza repozytorium) i wypisuje jego odcisk.
- Klient zapamietuje odcisk certyfikatu przy pierwszym polaczeniu; gdy sie zmieni, odmawia polaczenia
  przed wyslaniem hasla i proponuje "Zapomnij certyfikat" (na wypadek przeinstalowania serwera).
- Opcja serwera `--no-tls` (tylko testy).
- 1 nowy test TLS (lacznie 171); recznie sprawdzone pierwsze polaczenie i podmieniony certyfikat.

## [0.11.1] - 2026-10-14

### Dodano
- Przewidywanie ruchu w kliencie online (`game.MovePredictor`): postac rusza od razu, serwer tylko
  potwierdza kroki; przy blokadzie, teleporcie lub smierci obowiazuje pozycja serwera.
  Licznik poprawek w panelu diagnostyki (F3 / przycisk i).
- 2 testy symulujace opoznienie sieci (lacznie 170).

### Naprawiono
- Serwer zapisuje postac przed usunieciem gracza z listy (niestabilny test sieciowy).
- Test wspolnych potworow nie zalezy juz od tego, czy obie klatki pochodza z tego samego kroku.

## [0.11.0] - 2026-10-14 - etap 14: konta, logowanie, czat

### Dodano
- Menu startowe: "Graj lokalnie" (zapis na urzadzeniu) albo "Graj online".
- Ekran logowania (adres serwera, imie, haslo, profesja nowego konta) - dziala tez na Androidzie,
  wiec APK moze grac online; adres i imie sa zapamietywane, haslo nigdy.
- Konta na serwerze: pierwsze logowanie zaklada konto, haslo przechowywane tylko jako skrot
  PBKDF2-HMAC-SHA256 z losowa sola; 5 blednych prob = blokada na minute. Protokol w wersji 2.
- Czat: przycisk CZAT (Android - okno wpisywania), Enter na komputerze.
- Po utracie polaczenia klient wraca do menu z komunikatem.
- Uprawnienie INTERNET w APK.
- 1 nowy test kont przez TCP (lacznie 168); logowanie i czat sprawdzone recznie wpisywaniem z klawiatury.

### Jeszcze nie
- Polaczenie nie jest szyfrowane (TLS) - serwera nie nalezy wystawiac publicznie.

## [0.10.0] - 2026-10-14 - etap 13: PvP

### Dodano
- Walka gracz kontra gracz na serwerze: dotkniecie innego gracza rozpoczyna atak, drugie dotkniecie go konczy.
- Ochrona: do poziomu 30 nie mozna atakowac graczy ani byc atakowanym; w strefie ochronnej nie ma walki;
  nie mozna atakowac postaci o ponad polowe slabszej (chyba ze ma czaszke).
- Czaszki: biala za atak na niewinnego (15 min), czerwona za 3 nieusprawiedliwione zabojstwa w ciagu 24 h (6 h).
  Smierc z czerwona czaszka zostawia caly plecak na ziemi. Zabijanie graczy nie daje doswiadczenia.
- Imiona graczy z czaszka sa biale lub czerwone, atakowany gracz ma czerwona ramke.
- 6 nowych testow (lacznie 167), w tym symulacja pojedynkow wszystkich par profesji.

## [0.9.0] - 2026-10-14 - etap 14: serwer i tryb online

### Dodano (czesc 2: klient online)
- Klient libGDX laczy sie z serwerem (`-Dpixel.server=host:port`): ruch i akcje ida na serwer,
  ekran pokazuje stan z serwera - postac, potwory, innych graczy (z imionami i % zdrowia),
  przedmioty na ziemi, liczby obrazen, dziennik i czat. Bez serwera gra przechodzi w tryb lokalny.
- Zegar gry zgrany z czasem serwera; zapis postaci online robi tylko serwer.
- Gracze logujacy sie w to samo miejsce dostaja wolne pole obok (nie stoja jeden na drugim).
- 4 nowe testy (lacznie 161): replika klienta (stan postaci, pozycja, potwory) i przedmioty/zdarzenia walki przez TCP.

### Dodano (czesc 1: serwer)

### Dodano
- Modul `server`: autorytatywny serwer gry (TCP, protokol tekstowy `net.Protocol`), jeden wspolny swiat:
  potwory i przedmioty na ziemi wspolne dla wszystkich graczy, petla 20 krokow/s, stan 10 razy/s.
- Logowanie imieniem i profesja, zapis postaci na serwerze (co minute i przy wyjsciu), odrzucanie
  podwojnych logowan i zlej wersji protokolu, czat.
- `NetClient` (bez libGDX) i `Actions` - te same akcje co w HUD wykonywane na serwerze.
- Potwory poluja na najblizszego z wielu graczy.
- 4 nowe testy, w tym 3 przez prawdziwe polaczenia TCP (lacznie 157).

### Jeszcze nie
- Brak kont z haslem, szyfrowania i PvP; na Androidzie nie ma jeszcze ekranu wpisania adresu serwera
  (APK gra lokalnie), brak okna czatu do pisania (czat jest odbierany i wyswietlany).

## [0.8.0] - 2026-10-14

### Dodano
- NPC w czterech miastach: kupcy, bankierzy, kaplani i przewodnicy z zadaniami.
- Handel: kupno w ilosciach 1/5/25/100, sprzedaz lupow, ceny w data/shops.csv.
- Bank w grze (wplaty i wyplaty), leczenie u kaplana i wybor miasta odrodzenia.
- 13 zadan (zabij potwory / przynies lupy) z nagrodami exp i zlota; postep jest zapisywany.
- Okno rozmowy z NPC, imiona nad NPC, przycisk ROZMOWA.
- 6 nowych testow (lacznie 153), w tym kontrola, ze handlem, rzemioslem i zadaniami nie da sie "drukowac" zlota.

## [0.7.0] - 2026-10-14

### Dodano
- Noc wplywa na potwory: wilki, hieny, bandyci i nieumarli w nocy widza dalej i atakuja szybciej.
- Komunikaty o zapadajacej nocy i wschodzie dnia.
- Opcja deweloperska `-Dpixel.hour` (godzina gry na starcie).
- 5 nowych testow (lacznie 147).

### Zmieniono
- Zegar swiata jest czescia logiki gry (przygotowanie pod czas z serwera).
- Ostrzezenie o niebezpiecznym pietrze bierze pod uwage tylko okolice, a nie caly swiat.

## [0.6.0] - 2026-10-14

### Dodano
- Lochy biomow siegaja pietra -5: dziury, drabiny i miejsca, z ktorych wychodzi sie tylko z lina.
- Trzej bossowie: Frost Queen Ysolde (150), Ruin Colossus (200), Pharaoh Neferakh (230) i ich lupy.
- Nowe zrodla swiatla: swiecacy mech, magma, swiatlo nad miejscem na line.
- Pochodnie sie wypalaja (30 minut), czas widac na karcie postaci.
- 6 nowych testow (lacznie 142).

### Zmieniono
- Sytosc, swiatlo z czaru i zapalona pochodnia sa zapisywane razem z postacia.

## [0.5.0] - 2026-10-14

### Dodano
- Swiat z czterema miastami: Frostholm (snieg), Varden (start), Selvaran (dzungla) i Qasirah (pustynia),
  polaczonymi drogami; mapa 160 x 232 na 11 pietrach.
- Lochy: Lodowe Jaskinie, Zatopione Ruiny, Grobowce Qasirah.
- 12 potworow biomow (poziomy 40-200), nowe materialy, bron i zbroje na poziom 40-100.
- Regiony z zalecanym poziomem i ostrzezeniem przy wejsciu na zbyt niebezpieczny teren.
- Nowe kafelki: lod, bagno, piaskowiec, sosny, palmy, kaktusy, sciany lodowe i z piaskowca.
- 7 nowych testow swiata (lacznie 136).

### Zmieniono
- Zapis postaci w wersji 3; stare zapisy sa przenoszone na nowa mape automatycznie.
- Odrodzenie po smierci w swiatyni miasta postaci.
- Generator map nie stawia dekoracji, ktore odcinalyby fragment mapy (naprawiony niedostepny spawn szkieletow).

## [0.4.0] - 2026-10-14

### Dodano
- Lowienie ryb: wedka, robaki, szansa rosnaca z umiejetnoscia, odpoczynek lowiska, rzadkie perly.
- Karta postaci z umiejetnosciami i postepem, sytoscia i liczba zabitych potworow.
- 7 nowych testow (lacznie 129).

### Zmieniono
- Umiejetnosci walki i tarczy nie rosna na zbyt slabych potworach.

## [0.3.0] - 2026-10-14

### Dodano
- Czary dla 4 profesji: leczenie (Druid najsilniejsze), ataki na cel (ogien, energia, lod, ziemia,
  swiete, fizyczne), ataki obszarowe, poswiata, przywolanie wloczni, zaklinanie run.
- Runy glazu, wybuchu ognia i leczenia; mikstury zdrowia i many (takze silne od poziomu 50);
  jedzenie (chleb, mieso, ryba) podwajajace regeneracje.
- Crafting: 8 receptur z materialow z lupow (zbroje, miecz, tarcza, wlocznie, puste runy, pochodnie).
- Trening poziomu magii, czasy odnowienia czarow i grup, komunikaty o bledach (mana, poziom, zasieg...).
- Klient: pasek czarow z odnowieniem, przyciski HP/MP z licznikiem, ksiega czarow i rzemiosla,
  przycisk UZYJ w plecaku, skroty 1-4, Q, F, K.
- 16 nowych testow (lacznie 122), w tym symulacje z botem leczacym sie czarami i miksturami.

### Zmieniono
- Grimtooth uderza mocniej (40-160): bez tego postac na poziomie 8 mogla go pokonac zapasem mikstur.
- Potwory trafione z daleka (czar, runa, dystans) od razu ruszaja na gracza.

## [0.2.0] - 2026-10-14

### Dodano
- Potwory: wilk, dzik, lesny pajak, bandyta, goblin, goblin szaman, szkielet, troll jaskiniowy,
  krysztalowy golem i boss Grimtooth (dane w `monsters.csv`, lupy w `loot.csv`, spawny w `spawns.csv`).
- AI potworow: aggro, poscig, ucieczka (osaczony walczy dalej), utrzymywanie dystansu, leczenie
  sojusznikow, powrot na spawn, regeneracja poza walka, mechaniki bossa.
- Walka: wybor celu, atak automatyczny, wrecz / dystans / magia, pancerz, tarcza, odpornosci,
  postawa obronna, automatyczne przelaczenie na atakujacego, gdy cel ucieknie poza zasieg.
- Smierc gracza z kara 5% exp i odrodzeniem w swiatyni; ostrzezenie przy wejsciu na zbyt trudne pietro.
- Lupy z autolootem; gdy plecak pelny, lup zostaje na ziemi.
- Klient: grafika potworow, paski zycia, obwodka i panel celu, liczby obrazen, przyciski CEL i TARCZA.
- 30 nowych testow (lacznie 106): AI, walka, symulacje profesji, bezpieczenstwo poczatkujacych.

### Zmieniono
- Obrazenia magiczne rosna szybciej z poziomem magii (dzielnik 25 zamiast 40) - wynik symulacji.
- Stado wilkow pod Varden zmniejszone z 3 do 2 (nowa postac stojaca w miejscu ginela po ~80 s).

## [0.1.0] - 2026-10-14

Pierwsza wersja projektu (repozytorium bylo puste).

### Dodano
- Projekt Gradle: moduly `shared` (logika), `core` (klient libGDX 1.13.5), `desktop` (LWJGL3), `android`.
- Postac: start na poziomie 8 z 10 000 gp w plecaku, krzywe doswiadczenia i statystyk do poziomu 1000.
- Profesje Paladin, Druid, Sorcerer i Monk z roznymi profilami i zestawami startowymi.
- Przedmioty z pliku danych, plecak 20 slotow, ekwipunek, wyrzucanie i podnoszenie, autoloot (lista ignorowanych).
- Waluty Gold / Platinum / Crystal, wymiana, platnosci z reszta, bank.
- Mapa kafelkowa z warstwami, kolizje, pietra -5..+5 (wieza i kopalnia w Varden), schody, dziury, drabiny.
- Miasto startowe Varden z okolica (generator `tools/mapgen/generate_varden.py`).
- Ruch w 8 kierunkach, joystick, HUD (HP, MP, exp, zloto, pora dnia, dziennik), okno plecaka.
- Zegar swiata (dzien i noc), oswietlenie podziemi, pochodnie, latarnie i ogniska.
- Ochrona zasobow 90% / 75% (NORMAL / LIMITED / RECOVERING) i panel diagnostyki.
- Bezpieczny zapis postaci (CRC32, wersje, migracja, kopia zapasowa), autozapis.
- 76 testow JUnit, CI w GitHub Actions z budowa APK.
