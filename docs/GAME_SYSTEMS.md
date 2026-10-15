# Systemy gry

Stan: **Z** = zaimplementowane i przetestowane, **C** = czesciowo, **P** = zaplanowane.

## Architektura

- `shared` - logika bez libGDX (testowalna na JVM, docelowo uzywana przez serwer).
- `core` - klient: renderowanie, UI, wejscie. Nie zawiera regul gry.
- `GameSession` (shared) - sesja laczaca postac, swiat i systemy. Offline dziala w kliencie; online
  serwer prowadzi po jednej sesji na gracza (zrodlo prawdy o HP, manie, przedmiotach i walucie),
  a klient trzyma replike bez symulacji (`copyStateFrom`, `applyRemotePosition`, `MonsterManager.syncRemote`).

## PvP (etap 13)

Zasady w `pvp/PvpRules` (wspolne dla serwera i testow):
- Postacie ponizej poziomu 30 nie atakuja graczy i nie moga byc atakowane (start na poziomie 8 = okres ochronny).
- W strefie ochronnej (PZ) nie ma walki - ani atakujacy, ani cel nie moga w niej stac.
- Nie mozna atakowac postaci o poziomie mniejszym niz polowa wlasnego, chyba ze ma czaszke.
- Atak na gracza bez czaszki = biala czaszka na 15 min. Atak na gracza z czaszka jest usprawiedliwiony.
- 3 nieusprawiedliwione zabojstwa w 24 h = czerwona czaszka na 6 h; smierc z nia = caly plecak zostaje na ziemi.
- Smierc z reki gracza: ta sama kara exp co od potwora, odrodzenie w swiatyni miasta. Zabojca nie dostaje exp.
- Obrazenia zadawane graczom x0,5 (walki trwaja dluzej niz z potworami - czas na ucieczke lub leczenie).
- Stan czaszek trzyma serwer po imieniu postaci i zapisuje na dysku (`pvp_<imie>`, czasy jako zegar scienny),
  wiec czaszki i licznik zabojstw przetrwaja wylogowanie i restart serwera.
- Blokada strefy ochronnej: kazdy atak PvP zamyka PZ dla atakujacego i ofiary na 60 s (wyjsc mozna, wejsc nie -
  takze schodami prowadzacymi do PZ). Blokada przetrwa ponowne zalogowanie.
- Czary i runy ofensywne trafiaja gracza, gdy jest celem PvP, a postac nie ma celu-potwora
  (`SpellSystem.PlayerTargeting`, na serwerze `PvpRules.spellHit`): te same zasady, czaszki, blokada PZ i x0,5.
  Czary obszarowe trafiaja tylko wybranego gracza (nie przypadkowych przechodniow). Odmowa zasad = bez kosztu many/runy.

## Siec (etap 14)

- TLS (certyfikat serwera tworzony przy pierwszym starcie, klient przypina odcisk SHA-256 przy pierwszym
  polaczeniu - zmiana odcisku = odmowa bez wysylania hasla). Opcja serwera `--no-tls` tylko do testow.
- TCP, linie tekstowe rozdzielone tabulatorem (`net.Protocol`, wersja 3, port 7171).
- Klient -> serwer: `HELLO 3 imie PROFESJA haslo` (konto zakladane przy pierwszym logowaniu, haslo
  tylko jako skrot PBKDF2 z sola, 5 blednych prob = blokada 60 s; polaczenie TLS), `MOVE kierunek|-`, `ACT akcja` (te same nazwy co
  `game.Actions`), `TARGET id` (potwor), `ATTACK id|-` (gracz, PvP), `CHAT tekst`, `PASSWD stare nowe`,
  `DELETE haslo` (usuwa konto, postac i stan PvP), `BYE`. W czacie klienta: `/haslo stare nowe`, `/usun-konto haslo`
  (`net.ChatCommands` - hasla nigdy nie ida na czat).
- Nowe konta: najwyzej 5 na dobe z jednego adresu IP.
- Serwer -> klient: `WELCOME id`, `ERR`, `SELF zapis` (gdy sie zmienil, max 4/s), `LOG`, `CHAT`,
  `EV` (zdarzenie walki), co 100 ms klatka `FRAME czas` + `ME` + `TG` + `PL`/`MO`/`GI` w promieniu 12 + `END`.
  Gdy klatka jest identyczna jak poprzednia, serwer wysyla tylko `KEEP czas` (klient powtarza ostatni stan).
- Klient przewiduje zwykle kroki (`MovePredictor`, max 2 kroki przed serwerem, 800 ms na potwierdzenie);
  schody i przejscia czekaja na serwer. Inna pozycja z serwera = korekta.
- Ochrona serwera: osobny watek wysylki na klienta (400 zaleglych linii = rozlaczenie), 300 polaczen,
  8 na adres, 40 wiadomosci/s (zapas 80), linia do 2048 znakow, 15 s na HELLO, czat 5 wiadomosci / 10 s.
- Serwer: 20 krokow/s, potwory aktualizowane raz na krok dla wszystkich graczy, zapis co 60 s i przy wyjsciu.
- Budzet wydajnosci (`ServerLoadBudgetTest`): 200 graczy przy spawnach calej mapy 256x232, pelna populacja
  potworow - srednio ok. 0,5 ms na tik AI lokalnie (budzet testu 25 ms z 50 ms tiku).

## Postac (Z)

- Tworzenie: poziom 8, 10 000 gp w plecaku, 0 gp w banku, zestaw startowy profesji, start w swiatyni Varden.
- Poziomy 1..1000, doswiadczenie w `long` z nasyceniem; HP i mana rosna przy awansie.
- Obrazenia, leczenie, mana, regeneracja co 6 s czasu gry.
- Umiejetnosci (`Skills`): piesc, miecz, dystans, tarcza, magia, lowienie; rosna z uzyciem.
- Walidacja imienia (3-20 liter, spacje, apostrof).

## Profesje (C)

Paladin, Druid, Sorcerer, Monk - rozne przyrosty, regeneracja, wspolczynniki walki, obrona,
odstep atakow i zestaw startowy. Czary i zasady PvP/PvM: etapy 7 i 13.

## Przedmioty, plecak, ekwipunek (Z)

- Rejestr z `data/items.csv` (kategoria, stos, wartosc, slot, atak/obrona/pancerz, profesje, swiatlo).
- Plecak 20 slotow; dodawanie (laczenie stosow), usuwanie, przenoszenie, dzielenie, zamiana, porzadkowanie.
- Kazda operacja jest atomowa - pelny plecak niczego nie zmienia (brak utraty i duplikacji).
- Ekwipunek: helm, zbroja, nogi, buty, bron, tarcza, amulet, pierscien, amunicja; wymagania profesji i poziomu;
  zamiana z plecakiem.
- Wyrzucanie na ziemie i podnoszenie (`GroundItems`), lista przedmiotow ignorowanych przez autoloot.
- UI: okno plecaka z ikonami, statystykami zaznaczonego przedmiotu, ZALOZ / WYRZUC, przenoszenie dotykiem.

## Waluty i bank (Z, UI banku - C)

Gold / Platinum / Crystal (100:1). Wplata, wyplata, przelew, wymiana w gore i w dol, placenie z reszta.
Wszystko transakcyjne, z ochrona przed przepelnieniem. Bankier w kazdym miescie (jedno konto).

## NPC, handel, zadania (Z logika, C UI)

- `npc/`: `Npc` (rola MERCHANT / BANKER / PRIEST / GUIDE), `NpcRegistry` (maps/world/npcs.csv, generowany
  z mapa; NPC tylko w strefie ochronnej, nie na przejsciach), `ShopRegistry` (data/shops.csv) z kontrola
  arbitrazu przy wczytywaniu, `TradeSystem` (kupno/sprzedaz atomowo na kopii plecaka).
- Rozmowa w zasiegu 2 kafelkow, przez lade, ale nie przez sciane. NPC blokuja ruch.
- Kaplan: pelne leczenie, zamieszkanie w miescie (`homeTown` - odrodzenie w jego swiatyni).
- `quest/`: `QuestType` (KILL / COLLECT), `QuestRegistry` (data/quests.csv), `QuestLog` w postaci (zapisywany),
  `QuestSystem` (przyjecie, licznik zabic przez `CombatSystem.setKillListener`, oddanie z nagroda).
- Klient: przycisk ROZMOWA (lub N / dotkniecie NPC), okno z kupnem (ilosc 1/5/25/100, strony), skupem,
  bankiem, uslugami kaplana i lista zadan ze stanem.
- Lancuchy zadan (`data/quest_chains.csv`): kolejny etap jest widoczny i dostepny dopiero po ukonczeniu
  poprzedniego (ten sam zleceniodawca, bez petli); nagrody przedmiotami dodawane atomowo razem ze zlotem
  (pelny plecak = zadanie czeka). Teraz: przemytnicy Varden (3 etapy, helm gornika), wyprawa Selvaran
  (2 etapy, nagolenniki z krokodyla), zamiec we Frostholm po yeti (mikstury).
- Rozmowa slowami kluczowymi (`data/dialogues.csv`, `npc/DialogueRegistry`): wiersze "Zapytaj: ..." w oknie
  NPC albo czat obok NPC (offline i online). Bez wielkosci liter i polskich znakow, z tolerancja odmiany
  (praca / prace); linia NPC wygrywa z linia roli i wspolna. Odpowiedz idzie do dziennika jako "Imie: tekst";
  online liczy ja serwer (akcja `npc:ask:<tekst>`).

## Oboz i kuznia endgame (Z logika, C UI)

- NPC moga stac poza miastami (pole "miasto" = nazwa obozu), ale kaplan tylko w miescie ze swiatynia.
- `camp_shop`: mikstury (w tym Supreme), skup materialow po 1/2 i sprzetu endgame po 1/4 wartosci.
- Receptury endgame w `recipes.csv` (crafting z ksiegi, bez stanowiska); poziom receptury = poziom przedmiotu.
- Ksiega: receptury do poziomu postaci + 100, po kilka na strone ("Nastepna strona receptur").

## Mapa, kolizje, pietra (Z)

- Warstwy: podloze, dekoracje, obiekty (przeszkody), sciany, swiatlo, przejscia.
- Pietra -5..+5 o wspolnym ukladzie wspolrzednych, mapy z plikow tekstowych + legenda.
- Kolizje z cache (`boolean[]`), ruch w 8 kierunkach bez scinania rogow.
- Schody, dziury, tunele, drabiny (UZYJ), miejsca na line.
- Przejscia strzezone poziomem: `gates=x0,y0,x1,y1,z,poziom` w map.properties, `WorldMap.requiredLevelAt`;
  `MovementSystem.step` zwraca `LEVEL_TOO_LOW`, sesja wypisuje ostrzezenie straznika (co 3 s), a klient online
  nie przewiduje kroku na taki kafelek.

## Ruch (Z)

Krok po kafelkach z czasem zaleznym od szybkosci (skos x1.41, bieganie -15%), plynna interpolacja.
Joystick ze strefa martwa, klawiatura na PC.

## Swiatlo, dzien i noc (C)

- `WorldClock`: doba gry = 60 min, fazy swit/dzien/zmierzch/noc, plynne przejscia, noc min. 38% jasnosci.
- Podziemia: jasnosc maleje z glebokoscia, min. 16%; postac zawsze ma poswiate 2,5 kafelka.
- Pochodnia z plecaka (POCH) - promien 5; pali sie 30 min czasu gry, potem znika i zapala sie nastepna
  (komunikat, gdy brak kolejnej). Zgaszona nie traci paliwa. Czas widac na karcie postaci.
- Czar Poswiata (LIGHT) daje wiekszy promien na czas; sytosc, swiatlo z czaru i pochodnia sa zapisywane.
- Zrodla swiatla na mapie: latarnie, pochodnie scienne, ogniska, krysztaly (takze lodowe), swiecacy mech,
  magma, swiatlo z otworu nad miejscem na line.
- Renderowanie: FBO o obnizonej rozdzielczosci, mnozenie przez scene; lista zrodel buforowana per pietro.
- Zegar swiata jest w `GameSession` (czas sesji 0 = 09:00); klient bierze z niego jasnosc i godzine.
  `setClock` pozwoli podac czas serwera. Noc = 21:00-05:00.
- Nocne drapiezniki (`activity=NOCTURNAL`: wilki, hieny, bandyci, szkielety, upiory, mumie, faraon) maja
  w nocy na powierzchni aggro +3 i ataki co 80% czasu. Pod ziemia noc nie ma znaczenia.
- Nocne spawny (8. kolumna `noc` w spawns.csv, tylko powierzchnia): o zmroku dostaja pelna obsade
  (potwory pojawiaja sie poza zasiegiem 5 kafelkow od gracza), za dnia znikaja, gdy nie walcza i zaden gracz
  nie jest blizej niz 9 kafelkow; zabite w nocy nie odradzaja sie za dnia. Teraz: wilki i bandyci przy Varden,
  sniezne wilki, hieny, piaskowe upiory i upiorni rycerze na wschodzie.

## Ochrona zasobow 90% / 75% (Z logika, C platforma)

- Stany NORMAL / LIMITED / RECOVERING, progi konfigurowalne (domyslnie 90% wejscie, 75% wznowienie).
- Potwierdzenie przekroczenia (0,75 s) chroni przed krotkimi skokami; stabilizacja 3 s przed wznowieniem;
  stopniowe przywracanie FPS i jakosci efektow w 4 s; brak przelaczania w kazdej klatce.
- LIMITED: limit 30 FPS, swiatlo w nizszej rozdzielczosci, wstrzymanie prac odkladalnych, rzadsza diagnostyka.
  Nigdy nie blokuje sterowania, zapisu ani transakcji.
- Pomiary: sterta aplikacji (desktop i Android), pamiec systemu i stan termiczny (Android API 29+),
  czas klatki (wskaznik posredni). CPU jest oznaczone jako niedostepne - Android nie udostepnia go aplikacjom.
- Panel diagnostyki (`i` / F3): FPS, pamiec, CPU, temperatura, bateria, tryb i przyczyna.
- Pod panelem ustawienia (`GameSettings`, zapis w Preferences `pixel-settings`): limit FPS 30/60/90/120,
  oszczedzanie baterii (maks. 30 FPS, jakosc swiatla 0,5), jakosc swiatla, liczby obrazen.
  Ostateczny limit = min(ustawienie, ochrona zasobow); jakosc swiatla = min(ustawienie, ochrona zasobow).

## Zapis (Z)

Tekstowy format z wersja i CRC32, migracja v1 -> v2, odrzucanie uszkodzonych plikow,
zapis atomowy (tmp + fsync + kopia .bak), odczyt z kopii przy uszkodzeniu. Autozapis co 60 s,
przy pauzie, utracie fokusu i zamknieciu.

## Potwory i AI (Z logika, C grafika)

- `MonsterRegistry` (monsters.csv + loot.csv), `SpawnTable` (spawns.csv), `MonsterManager`.
- Stany: IDLE (wedrowka w promieniu spawnu, regeneracja 5%), CHASE (BFS, max 400 wezlow), FLEE,
  RETURN (smycz: promien spawnu + 10 lub inne pietro; po powrocie pelne HP).
- Zachowania: MELEE, TANK, SKIRMISHER (ucieka ponizej progu HP; osaczony walczy do konca),
  RANGED / MAGIC (trzymaja dystans >= 2, strzelaja z linii wzroku), HEALER (leczy najslabszego
  sojusznika < 70% w promieniu 4), boss (leczenie raz ponizej 30%, atak 30% szybszy ponizej 50%).
- Potwory nie wchodza na strefy ochronne, przejscia, gracza ani siebie nawzajem; nie pojawiaja sie
  blizej niz 5 kafelkow od gracza.
- Wydajnosc: `SpatialGrid` (komorki 8x8); symulowane sa tylko potwory w promieniu 20 kafelkow.
  Serwer (etap 14) bedzie uzywal tej samej klasy dla wielu graczy (`Target`).

## Walka (Z)

- `CombatSystem`: atak co `attackIntervalMs` profesji (Monk 1,4 s, reszta 2 s), zasieg broni, linia wzroku.
- Cel: CEL (najblizszy, kolejne nacisniecia przechodza dalej), dotkniecie potwora, automatyczna
  odpowiedz na atak; gdy cel ucieknie poza zasieg, cel przechodzi na atakujacego potwora w zasiegu.
- Smierc potwora: exp (z redukcja), trening umiejetnosci, lup do plecaka (autoloot w promieniu 8,
  lista ignorowanych) lub na ziemie, odrodzenie po czasie ze spawns.csv.
- Smierc gracza: -5% exp (nie ponizej poziomu startowego), pelne HP/MP, swiatynia Varden.
- Zdarzenia walki (`CombatEvent`) -> liczby obrazen, leczenia, awansu w kliencie.

## Czary, runy, mikstury, crafting (Z logika, C UI)

- `magic/SpellRegistry` (spells.csv), `ConsumableRegistry` (consumables.csv), `RecipeRegistry` (recipes.csv).
- `SpellSystem`: kazda akcja jest najpierw sprawdzana (profesja, poziom, magia, mana, odnowienie, cel,
  zasieg, linia wzroku, strefa ochronna, przedmioty, miejsce), a dopiero potem pobiera koszt -
  nieudana proba nic nie kosztuje. Ataki przechodza przez `CombatSystem.damageMonster`
  (odpornosci, smierc, lup, exp).
- Runy: przedmiot RUNE o kluczu takim jak efekt w spells.csv (`RUNE_TARGET`, `RUNE_AREA`, `RUNE_HEAL`).
- `CraftingSystem` i zaklinanie run dzialaja na kopii plecaka (atomowo).
- Sytosc i swiatlo z czaru to stan ulotny (nie zapisywany) - po wczytaniu gry trzeba zjesc ponownie.
- UI: pasek wybierany automatycznie (najlepsze leczenie, atak, obszar, swiatlo), ksiega z wszystkimi
  czarami profesji (szare = jeszcze niedostepne) i recepturami (szare = brak skladnikow).

## Lowienie i umiejetnosci (Z logika, C UI)

- `gathering/FishingSystem`: najblizszy kafelek wody w zasiegu 3 z linia wzroku; proba co 1,5 s;
  szansa `min(70%, 10% + 0,6% * umiejetnosc)`; robak zuzywany przy zlowieniu; pelny plecak = nic nie
  znika; kafelek po zlowieniu odpoczywa 2 min (stan ulotny); perla 3% od umiejetnosci 30.
- Ryby biomow (`data/fishing.csv`): Mrozne Pustkowia - lodowy dorsz (35%, od umiejetnosci 15),
  Dzungla Selvaran - wegorz bagienny (35%, od 20; lowiskiem sa tam bagna), Pustynia Qasirah - karp z oazy
  (30%, od 25). Pozostale zlowienia to zwykla ryba. Ryby biomow dluzej syca i maja wyzszy skup w lokalnym sklepie.
- Trening: proba +1, zlowienie +2. Walka: brak treningu na potworach ponizej progu
  `poziom > 4 * poziom potwora + 20` (`CombatSystem.isTrivial`).
- Umiejetnosci: start 10 (magia 0), koszt kolejnego poziomu `50 * 1,1^(poziom-10)` prob, maks. 200.

## Swiat, miasta i regiony (Z)

- Jedna mapa `maps/world` 256 x 232 x 11 pieter (wschodni kontynent x 168..255 = endgame) (`WorldLayout.WORLD_MAP`), generator `tools/mapgen/generate_world.py`.
- `Town` (maks. 4, swiatynia musi byc w strefie ochronnej i przechodnia) i `Region` (prostokat, zakres pieter,
  zalecany poziom) wczytywane z `towns=` i `regions=` w map.properties.
- `WorldMap.templeOf(miasto)` - odrodzenie po smierci; `regionAt(pozycja)`; `GameSession.checkRegion` wypisuje
  nazwe regionu i ostrzezenie przy wejsciu.
- Zapis w wersji 3 (`map=world`); starsze zapisy z mapy Varden sa przenoszone o (+48, +64).

## Planowane

 fishing, NPC, zadania, handel, bank w UI, PvP,
serwer i synchronizacja - patrz [TODO.md](../TODO.md).
