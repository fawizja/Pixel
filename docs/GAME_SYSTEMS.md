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
- Nagrody za glowy (0.121.0, `pvp/Bounties`): `/nagroda imie, kwota` (min. 1 000 gp, z konta) dokłada do nagrody,
  `/nagrody` pokazuje 10 najwyzszych. Kto pokona postac w PvP (nie pojedynek, nie ta sama druzyna ani gildia),
  dostaje calosc na konto; serwer oglasza wplaty i odbior. Zapis `<dane>/bounties.txt`.
- Szukanie druzyny (0.146.0): `/szukam` oglasza gotowosc do gry w druzynie - widac to w `/gracze`, a gracze bez
  druzyny o podobnym poziomie (od 2/3 do 3/2 poziomu) dostaja wiadomosc z poleceniem `/zaproś`. Ponowne `/szukam` wylacza.
- Zamowienia kupna (0.138.0, `market/BuyOrders`): u bankiera `/kupię 20 wilcze futro 2000` blokuje zloto z konta
  (max 5 zamowien na gracza). `/kupię` pokazuje zamowienia, `/sprzedaj numer` realizuje cudze (sprzedajacy oddaje
  przedmioty z plecaka i dostaje calosc bez prowizji, przedmioty ida poczta do depozytu kupujacego),
  `/kupię anuluj numer` zwraca zloto. Po 7 dniach zamowienie wygasa i zloto wraca (offline - przy wejsciu). Zapis `<dane>/buyorders.txt`.

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
- Amunicja (0.118.0): wlocznie w gniezdzie amunicji dodaja 1/4 swojego ataku do fizycznej broni dystansowej;
  kazdy strzal zuzywa sztuke, pusty stos uzupelnia sie tym samym rodzajem z plecaka. Bez amunicji bron dziala jak dotad.
- Wyrzucanie na ziemie i podnoszenie (`GroundItems`), lista przedmiotow ignorowanych przez autoloot.
- UI: okno plecaka z ikonami, statystykami zaznaczonego przedmiotu, ZALOZ / WYRZUC, przenoszenie dotykiem.
- Wiele plecakow (etap 14): przedmiot `backpack` (20 gp u zaopatrzeniowcow) uzyty z plecaka staje sie kolejna
  strona po 20 miejsc (najwyzej 3 dodatkowe = 80). Logika widzi jeden pojemnik (`Container.resize`), wiec lup,
  sklepy, handel i mikstury dzialaja na wszystkich stronach. Ostatni plecak mozna odpiac (ODEPNIJ), gdy jest pusty.
  Zapis: `bags=<liczba>` przed slotami `bp.<n>`. Postac startujaca na 250+ ma od razu drugi plecak.
- Przeciaganie (etap 14): przytrzymanie i przesuniecie przedmiotu przenosi go na inne pole, na zakladke innego
  plecaka (pierwsze wolne pole tej strony) albo na panel ekwipunku (zalozenie). Krotkie dotkniecie dziala jak dotad.

## Waluty i bank (Z, UI banku - C)

Gold / Platinum / Crystal (100:1). Wplata, wyplata, przelew, wymiana w gore i w dol, placenie z reszta.
Uzycie monet jak w Tibii (etap 14): stos 100 sztuk zamienia sie na monete wyzszego nominalu (100 gp = 1 platinum,
100 platinum = 1 crystal), pojedyncza platynowa lub krysztalowa rozmienia sie na 100 nizszych. SORTUJ wymienia
pelne setki automatycznie.
Wszystko transakcyjne, z ochrona przed przepelnieniem. Bankier w kazdym miescie (jedno konto).
Depozyt przedmiotow (0.27.0): `PlayerCharacter.depot()` - 60 miejsc, wspolny dla miast, tylko przy bankierze
(`GameSession.depotStore/depotTake`, przenosi wszystkie sztuki ile sie zmiesci, monety odrzuca). Zapis: `dp.<slot>`.
Szafki depozytu (`depot_locker`) w budynkach depozytu kazdego miasta otwieraja ten sam depozyt.
Domy: `maps/world/houses.csv` + `house/HouseRegistry`; kupno u agenta domow (rola HOUSING) za zloto z banku,
jeden dom na postac, obcy nie wchodza do srodka, wlasciciele w `save/houses.txt` (serwer).
Goscie (0.208.0): `HouseOwners.guests/addGuest/removeGuest/transfer/mayEnter`; `HouseOwnersMap` zapisuje linie
`guests:<id>=Imie,Imie` (najwyzej `MAX_GUESTS` = 10). `GameSession.mayEnter` wpuszcza wlasciciela i gosci.
Czat: `/dom` (HOUSE INFO), `/dom wpusc|wyrzuc Imie` (GUEST/UNGUEST), `/dom sprzedaj Imie, cena` (SELL, oferta
5 minut), `/dom kup` (ACCEPT: kupujacy placi z banku, sprzedajacy dostaje zloto na konto albo w zaleglych
wplywach rynku, goscie traca wstep), `/dom anuluj`. Sprzedaz miastu u zarzadcy: zwrot
`HOUSE_SELLBACK_PERCENT` = 50% ceny. Domy maja kształt L: 12. pole houses.csv to skrzydla `x0,y0,x1,y1/...`.
Skrzynie (0.208.0): `world/Crates` (WorldMap.crates()). Skrzynie, beczki, worki i kufry (`MOVABLE`) pcha sie,
wchodzac w nie (tylko prosto, pole za nimi wolne, bez schodow i kratek, najdalej `MAX_DRIFT` = 6 pol od miejsca
z mapy, meble domu nie wyjezdzaja z domu). `STORAGE` (+ szafy) otwiera sie dotknieciem albo UZYJ: 8 miejsc,
okno jak u bankiera (`Npc.CRATE`), w cudzym domu zamkniete. Offline stan w zapisie postaci (klucz `crates`),
online wspolny dla wszystkich (`<dane>/crates.txt`, komunikaty CRATEMV i CRATE do klientow, stan przy wejsciu).
Lady, gabloty, polki i szafki depozytu stoja w miejscu.

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

## Przystan Wschodnia - wyspy 300+ (etap 15, dane)

- Admiral Wenda (`isle_guide`, GUIDE): 15 zlecen od poziomu 300 na wyspach i w lochach (wiwerny, kraby, piraci,
  topielcy, upiory mgly, magmowe smoki) oraz na pieciu bossow lochow. Cztery lancuchy (`quest_chains.csv`) koncza
  sie koralowym ekwipunkiem (helm, zbroja, nagolenniki) albo 20 krysztalowymi monetami.
- Kwatermistrz Olaf (`isle_merchant`, sklep `isle_shop`): mikstury 250+, plecaki, liny i skup produktow z wysp
  (luska wiwerny, szczypce kraba, hak pirata, rog z prochem, pierscien topielca, esencja mgly, rog smoka) po
  wartosci przedmiotu, a takze koralowego ekwipunku i materialow endgame.
- Produkty wypadaja ze zwyklych potworow wysp (15-25%) i licznie z bossow. Rozmowy kapitanow (@CAPTAIN) o wyspach i piratach.

## Oboz i kuznia endgame (Z logika, C UI)

- NPC moga stac poza miastami (pole "miasto" = nazwa obozu), ale kaplan tylko w miescie ze swiatynia.
- `camp_shop`: mikstury (w tym Supreme), skup materialow po 1/2 i sprzetu endgame po 1/4 wartosci.
- Receptury endgame w `recipes.csv` (crafting z ksiegi, bez stanowiska); poziom receptury = poziom przedmiotu.
- Receptury srednich poziomow (0.158.0): iron_knuckles (20), fur_armor (40), sandstone_shield (50), croc_legs (55),
  serpent_spear i desert_knuckles (60), scarab_helmet (70) - z materialow krain (kly, futra, skory, ogony, bandaze).
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

Krok po kafelkach z czasem zaleznym od szybkosci (skos gracza x3 jak wyczerpanie w Tibii, potwory x1.41; bieganie -15%), plynna interpolacja.
Kolizje (etap 12): poza strefa ochronna gracze blokuja sobie pola (na serwerze, `GameSession.setOtherPlayers`),
w strefie ochronnej mozna przez siebie przejsc jak w Tibii; potwory nie wchodza na pole zadnego gracza (nie tylko celu).
Przyspieszenie: czary utani hur / utani gran hur (`SpellKind.HASTE`, w spells.csv baza = procent, zasieg = sekundy),
ikona stanu w HUD i pole na automatycznym pasku czarow (zamiast poswiaty, gdy postac zna przyspieszenie).
Joystick ze strefa martwa, klawiatura na PC.
Sterowanie dotykiem (etap 17, `GameScreen` + `Hud.floatJoystick`): dotkniecie mapy rozstrzyga sie przy puszczeniu
palca. Krotkie dotkniecie = pole (cel, marsz, rozmowa, lup), przytrzymanie 450 ms = podglad pola
(`GameSession.lookAt`: potwor z poziomem i zdrowiem, NPC z rola, cialo, przedmioty, przejscia, strefa ochronna,
odleglosc; na PC prawy przycisk myszy), przeciagniecie po lewej czesci widoku gry = joystick pod kciukiem
(podstawa tam, gdzie palec dotknal; po puszczeniu wraca w rog). Ustawienia: joystick pod kciukiem / tylko w rogu,
rozmiar 80-150%. Na Androidzie ekran nie gasnie w trakcie gry (`useWakelock`).

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

## Ciala potworow i przedmioty na ziemi (Z logika, C UI)

- Zabity potwor zostawia cialo (`GroundItems.Corpse`) z lupem, ktorego nie zabral autoloot (wylaczony,
  filtr albo pelny plecak). Cialo znika po 3 min razem z zawartoscia.
- Przeszukanie z kafelka ciala albo obok: UZYJ, PODNIES (G) albo dotkniecie ciala; do plecaka trafia to,
  co sie miesci, reszta zostaje. Pusty trup jest ciemniejszy.
- Przedmioty na ziemi (wyrzucone, plecak po smierci w PvP) znikaja po 30 min od ostatniego polozenia na kafelku.
- Online: linia `CO x y z potwor 0|1` w klatce; przeszukanie to akcja `loot`.

## Pasek akcji (Z logika, C UI)

- 10 pol 5x2 na dole prawego panelu. Pole trzyma akcje: `cast:<czar>`, `item:<mikstura|runa>`, `potion_hp`/`potion_mp`
  (najsilniejsza dozwolona mikstura) albo `none` (celowo puste). Akcja jest wysylana jak przycisk (`Actions.apply`,
  online `ACT`); `item:` przyjmuje tylko mikstury i runy.
- Automat (pole bez wpisu): leczenie, atak na cel, atak obszarowy (lub drugi atak), poswiata, mikstura HP, mikstura MP,
  pierwsza runa z plecaka. Czar nieznany postaci wraca do automatu.
- Krotkie dotkniecie uzywa pola (po puszczeniu), przytrzymanie 450 ms otwiera wybor: czar leczacy / atakujacy / inny,
  mikstura, runa, automat, wyczysc. Odnowienie czarow, mikstur (grupa POTION) i run jako zaslona opadajaca z gory.
- `settings/SpellBar`: zapis "cast:mend,-,item:...,none,..." w Preferences `pixel-bar` osobno dla kazdej postaci;
  stary zapis 4 kluczy czarow jest czytany jako czary. Klawisze 1-9 i 0.

## Uklad ekranu (C UI)

- Jedna bryla jak klasyczny klient Tibii: `Hud.resize` liczy prostokat widoku gry (`Hud.gameView`, piksele),
  `GameScreen.resize` dobiera skale (min. 11x8 pol w ramce) i przesuniecie kamery do srodka ramki; swiat rysuje sie
  na calym ekranie, panele go zaslaniaja.
- Prawy panel: minimapa z kolumna (i, BIEG, TARCZA, POCH), ekwipunek (3x4 albo 5x2), Umiejetnosci (wysoki ekran),
  lista walki, pasek akcji. U gory paski HP/MP/doswiadczenie i ikony stanu, na dole rzad przyciskow i konsola.
- Pad w lewym dolnym rogu ekranu (nachodzi na widok gry i dolny pasek); konsola i przyciski zaczynaja sie za nim.
  Prawy panel ma 20% szerokosci (170-260 jednostek UI).
- Dotyk poza widokiem gry nie trafia na mape. Na szerokim ekranie okna (ksiega, NPC, mapa) stoja nad widokiem gry.

- Etap 16: konsola ma zakladki jak w OTClient - Ogolne (wszystko, znika po 30 s, z zadaniami), Czat (gracze,
  prywatne, gildia, druzyna, odpowiedzi NPC; `EventLog.Kind.CHAT`), Lup, Serwer (informacje, ostrzezenia,
  komunikaty systemu). Zakladki inne niz Ogolne pokazuja historie kanalu bez wygaszania.
- Ksiega czarow pokazuje wszystkie czary profesji (i zapowiedzi na +100 poziomow) stronami: domyslnie strona
  konczy sie najwyzszym poznanym czarem, wiersz "Kolejne czary" przewija dalej.

## Efekty graficzne czarow (Z zdarzenia, C rysowanie)

- `CombatEvent` ma rodzaje PROJECTILE (z `from` do `position`) i AREA (promien w `amount`), `effect` = rodzaj
  obrazen albo HEAL. Emituja je czary na cel i runy (pocisk), czary i runy obszarowe (fala), leczenie (iskry),
  ataki dystansowe gracza i potworow (pocisk).
- Klient rysuje je addytywnie po swietle: pocisk z warkoczem (260 ms), pierscien biegnacy po kafelkach (450 ms),
  kolory wg zywiolu. Przy niskiej jakosci (bateria, ochrona zasobow) bez warkocza i iskier.
- Online: `EV` z 10 polami (skad x,y,z i efekt); stare klienty ignoruja nieznane rodzaje.
- Podglad do zrzutow: `-Dpixel.fx=true`.

## Ochrona zasobow 90% / 75% (Z logika, C platforma)

- Stany NORMAL / LIMITED / RECOVERING, progi konfigurowalne (domyslnie 90% wejscie, 75% wznowienie).
- Potwierdzenie przekroczenia (0,75 s) chroni przed krotkimi skokami; stabilizacja 3 s przed wznowieniem;
  stopniowe przywracanie FPS i jakosci efektow w 4 s; brak przelaczania w kazdej klatce.
- LIMITED: limit 30 FPS, swiatlo w nizszej rozdzielczosci, wstrzymanie prac odkladalnych, rzadsza diagnostyka.
  Nigdy nie blokuje sterowania, zapisu ani transakcji.
- Pomiary: sterta aplikacji (desktop i Android), pamiec systemu i stan termiczny (Android API 29+),
  czas klatki (wskaznik posredni). CPU jest oznaczone jako niedostepne - Android nie udostepnia go aplikacjom.
- Panel diagnostyki (`i` / F3): FPS, pamiec, CPU, temperatura, bateria, tryb i przyczyna.
- Pod panelem ustawienia (`GameSettings`, zapis w Preferences `pixel-settings`): glosnosc 0/30/60/100%, limit FPS 30/60/90/120,
  oszczedzanie baterii (maks. 30 FPS, jakosc swiatla 0,5), jakosc swiatla, liczby obrazen.
  Od 0.52.0 takze pogoda (pokazuj/ukryj) i automatyczna mikstura zdrowia (wylaczona / ponizej 30, 50, 70% HP;
  klient wysyla te sama akcje `potion_hp` co przycisk, najwyzej raz na sekunde).
  Ostateczny limit = min(ustawienie, ochrona zasobow); jakosc swiatla = min(ustawienie, ochrona zasobow).
- Etap 18: panel diagnostyki pokazuje liczbe wywolan rysowania i wiazan tekstur na klatke
  (libGDX `GLProfiler`, wlaczony tylko przy otwartym panelu). `-Dpixel.profile=true` na PC co 300 klatek wypisuje
  srednie (rysowanie ogolem i swiat, tekstury, wierzcholki, czas CPU klatki).
  Podloze, obrzeza i dekoracje rysowane trzema przebiegami (kazdy z jednego atlasu) - noc w Varden z deszczem:
  251 -> 109 wywolan rysowania na klatke (swiat 201 -> 59). Pasek akcji przeliczany co 150 ms zamiast co klatke,
  rodzaje potworow ze spawnow liczone raz (HUD pytal o nie co klatke).

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
- Cios wrecz niesie pozycje atakujacego (`from`, `isMeleeStrike`): klient rysuje wypad atakujacego w strone celu
  (180 ms) i krotkie ciecie na celu. Dziala tez online (EV z 10 polami).

## Druzyny (Z logika, S serwer)

- `party/PartyManager`: zaproszenie przez lidera (wazne 60 s), przyjecie, wyjscie; maks. 8 osob; po wyjsciu lidera
  przywodztwo przechodzi na nastepnego, przy jednej osobie druzyna sie rozpada. Wylogowanie = wyjscie z druzyny.
- `party/PartyRules`: doswiadczenie dzielone, gdy >= 2 zywych czlonkow jest na pietrze zabojcy w promieniu 30
  i najnizszy poziom >= 2/3 najwyzszego. Udzial = solo(wlasny poziom) * (1 + 0,2 * (n - 1)) / n.
  Bez spelnienia warunkow zabojca dostaje doswiadczenie sam. Zabicie liczy sie do zadan tylko zabojcy.
- Klient: panel druzyny (imiona, HP% gdy widoczni, "daleko" poza zasiegiem), imiona niebieskie z [druzyna].
- `CombatSystem.setExperienceSharer` - punkt podpiecia serwera. PvP miedzy czlonkami jest zablokowane.
- Protokol: `PARTY INVITE|ACCEPT imie`, `PARTY LEAVE`, `PCHAT tekst`; serwer odsyla `PARTY imie1,imie2` albo `PARTY -`.

## Handel miedzy graczami (Z logika, S serwer)

- `trade/PlayerTrade`: oferty obu stron (do 8 pozycji + zloto), sprawdzane z plecakiem przy dodawaniu; kazda zmiana
  kasuje obie akceptacje; `execute` dziala na kopiach plecakow (CHANGED gdy przedmiotow juz nie ma, NO_SPACE gdy
  brak miejsca) i zatwierdza obie naraz. Przedmioty po kluczu, nazwie albo jednoznacznym poczatku nazwy.
- Serwer: prosba `/handel` wazna 60 s, wymiana otwiera sie, gdy obie strony poprosza; odleglosc maks. 2 pola na tym
  samym pietrze, limit 3 min bez zmian; po wymianie zapis obu postaci i wpis w logu serwera.
- Protokol: `TRADE REQUEST imie | OFFER tekst | ACCEPT | CANCEL`, odpowiedzi jako wpisy LOG oraz stan okna
  `TS partner mojaOferta jegoOferta 0|1 0|1` (`TS -` zamyka okno; `trade/TradeView`).
- Klient: okno handlu w HUD (oferty, AKCEPTUJ/ANULUJ, +zloto, przedmioty z plecaka), X = anulowanie.

## Gildie (Z logika, S serwer)

- `guild/GuildRegistry`: nazwa 3-20 liter i spacji, unikalna bez wzgledu na wielkosc liter; jedna gildia na gracza;
  do 50 czlonkow; zaproszenie lidera wazne 2 min; lider = najdluzej obecny czlonek; pusta gildia znika.
  Zapis tekstowy "Nazwa;Lider,Czlonek,..." (bledne linie pomijane).
- Serwer: oplata 50 000 gp pobierana z plecaka przed zalozeniem; `guilds.txt` zapisywany atomowo po kazdej zmianie;
  usuniecie konta usuwa gracza z gildii. Nazwa gildii w klatce stanu (10. pole PL).
- Protokol: `GUILD CREATE|INVITE|ACCEPT|KICK x`, `GUILD LEAVE`, `GUILD INFO`, `GUILD DEPOSIT|WITHDRAW kwota`, `GCHAT tekst`.
- Skarbiec (0.123.0): `/gildia wpłać 5k` przelewa z konta do skarbca (kazdy czlonek), `/gildia wypłać 5k` tylko lider;
  kazda operacja ogloszona w kanale gildii, stan w `/gildia`. Zapis jako pole `bank=zloto` przed wiadomoscia dnia
  (stare linie bez tego pola dzialaja).
- Lista czlonkow (0.162.0): `/gildia członkowie` (GUILD MEMBERS) - najpierw w grze, potem wg poziomu, lider oznaczony,
  przy nieobecnych czas od ostatniego zapisu postaci ("3 h temu"); po 6 osob na linie dziennika.
- Sztandar gildii (0.152.0): `/gildia sztandar` (lider) placi 100 000 gp ze skarbca - czlonkowie dostaja +10% exp za
  potwory przez godzine (kolejny sztandar przedluza). Sumuje sie z wydarzeniem /bonusexp. Tylko w pamieci serwera.

## Skrzynie skarbow (Z logika, C rysowanie)

`treasure/TreasureChests.generate(world)`: BFS z miejsca startu (4 kierunki + kazde przejscie), kandydaci poza PZ
i bramami poziomu, >= 8 pol od przejsc, losowanie z ziarnem 20261015 + pietro, odstep 20 pol (mniej na malych
pietrach). Lup rosnie x3 na pietro w dol. Otwieranie: `GameSession.openChest` (UZYJ obok), raz na postac
(`openedChests`, zapis `chests`).

## Minimapa (Z kolory, C rysowanie)

`world/MinimapPalette.color/paint`: piksel RGBA8888 na kafelek - przejscie zolte, sciana ciemna, podloze w kolorze
kafelka x0.8, blokujacy obiekt x0.5, puste pole przezroczyste. `client/ui/Minimap` buduje teksture pietra raz (Nearest)
i rysuje wycinek 60x60 pol wokol gracza albo cale pietro (`Hud.toggleMap`, klawisz J) z nazwami miast.
Dotkniecie duzej mapy -> akcja `map:goto:x,y` -> `GameScreen.walkTo`: `Pathfinder.path` (BFS na tablicach, bez przejsc
na inne pietra) i co klatke kierunek do nastepnego pola, podawany jak z joysticka (dziala tez online).
Dotkniecie swiata (`GameScreen.tapTile`): gracz online -> menu gracza, NPC blisko -> rozmowa, NPC daleko -> marsz na
pole obok (`pathNextTo`) i rozmowa po dojsciu, potwor -> cel, cialo obok -> lup, wolne pole -> marsz bez komunikatu.

## Podsumowanie smierci (Z logika)

`combat/DeathRecap`: `record(zrodlo, obrazenia, czas)` z `CombatSystem.monsterAttacks` i `PvpRules.hit`
(zrodlo "Imie (gracz)"); przy smierci `build` sumuje trafienia z ostatnich 30 s wg zrodla (malejaco) i czysci liste.
Protokol: klient `DEATH`, serwer `DEATHLOG` + wiersze `etykieta;opis` (pusta lista = brak smierci).

## Woznice (Z logika)

`npc/Travel.price(a, b)` = max(100, 3 gp x odleglosc Manhattan miedzy woznicami, zaokraglone do 10).
`GameSession.travel(miasto)`: woznica obok, trasa do innego miasta, brak blokady PZ, wymagany poziom pola
docelowego, platnosc z plecaka (kopia + commit), `teleport` na pierwsze wolne pole obok woznicy docelowego.

## Pogoda (Z logika)

`world/Weather.at(epochMs, region, z)`: okno `WINDOW_MS` = 20 min, los SplitMix64 z (numer okna, nazwa krainy),
`CHANCE` 30% opadow; rodzaj z nazwy krainy (mroz/lod -> SNOW, pustynia/Qasirah -> SANDSTORM, inne -> RAIN);
z < 0 -> CLEAR. `intensity` narasta i zanika przez 90 s na brzegach okna, `ambientFactor` przyciemnia swiatlo.
Klient: `WeatherRenderer` liczy pozycje kropli z numeru i czasu (bez stanu). Serwer nie bierze w tym udzialu.

## Manekiny treningowe (Z logika)

`training/TrainingDummies.generate(world, npcSpots)`: BFS od swiatyni kazdego miasta po polach strefy ochronnej,
pierwsze 2 pola z 8 przechodnimi sasiadami, bez obiektow i przejsc, >= 3 pola od swiatyni, od siebie i od NPC.
`GameSession.useNearby` -> `toggleTraining` (manekin obok); `updateTraining`: co `TRAIN_MS` (3 s) 1 proba
umiejetnosci z `CombatFormulas.attackProfile`, co druga takze `SHIELDING`; zmiana pola albo smierc konczy.
Rownowaga: walka daje probe co atak (ok. 2 s) plus doswiadczenie, wiec polowanie zostaje szybsze.

## Analiza polowania (Z logika)

`stats/HuntAnalyzer`: `reset` zapamietuje doswiadczenie, `bestiary().total()` i `Money.totalValue(plecak)`; `report`
podaje roznice, wartosci na godzine (po pierwszej minucie) i czas do poziomu przy obecnym exp/h. Klient
(`GameScreen.updateHunt`) startuje licznik z pierwsza klatka i odswieza otwarte okno co sekunde.

## Zwoj powrotu (Z logika)

`GameSession.useSlot` -> `startRecall` (odmowa w blokadzie PZ po walce z graczem). `updateRecall` w kazdym kroku:
zmiana pola, spadek zdrowia, smierc albo blokada PZ przerywa; po `RECALL_MS` zwoj jest zabierany z plecaka
i `teleport` do `templeOf(homeTown)`.

## Nagroda za logowanie (Z logika)

`daily/LoginStreak.claim(dzien)`: 1..7 (kolejny dzien +1, po 7 od nowa, przerwa = 1), 0 gdy juz odebrano.
`GameSession.checkLoginReward` (pierwszy krok sesji i co 60 s) dopisuje `rewardGold(dzien, poziom)` do konta;
pelne konto cofa odbior. Na serwerze dzien liczy sesja serwera (klient online nie wywoluje update).

## Pojedynki (Z zasady, S serwer)

`PvpRules.startDuel` zapisuje przeciwnika w `Status.duelPartner` (bez zapisu na dysk). `check` pomija ochrone poziomu
i roznice poziomow dla pary pojedynku (strefa ochronna i pietro dalej obowiazuja), `hit` przekazuje do `duelHit`:
bez czaszek i blokady PZ, cios smiertelny ucinany do 1 HP -> `Outcome.DUEL_WON`. Serwer: DUEL REQUEST imie (wyzwanie
wazne 60 s; wzajemne = start), DUEL SURRENDER, walkower przy wyjsciu, remis po `DUEL_MS` (5 min) lub smierci od potwora.

## Poczta (Z logika, S serwer)

`mail/Mailbox`: przesylki (odbiorca, nadawca, przedmiot, ilosc) czekajace na serwerze w `mail.txt`. MAIL SEND imie tekst
(`ServerWorld.mailCommand`, przy bankierze) zabiera przedmioty z plecaka; `deliverMail` wklada je do depozytu odbiorcy
od razu (gdy jest w grze) albo przy wejsciu; co sie nie miesci, czeka dalej.

## Wierzchowiec (Z logika, C rysowanie)

Siodlo (`saddle`) uzyte z plecaka (`GameSession.useSlot` -> `useSaddle`) daje konia na stale (`hasMount`).
`toggleMount` (akcja `mount`): +`PlayerCharacter.MOUNT_SPEED` (60) do szybkosci; pod ziemia (z < 0) zakaz i automatyczne
zsiadanie w `update`. Online: pole `M` w PL, klient rysuje `TileArt.horse` pod postacia.

Bossowie lochow 300+ (etap 10): Matka Wiwern (360, Gniazdo Wiwern -2), Kapitan Krwawa Broda (420, Grota
Przemytnikow -3), Skaldra (540, Lodowe Groty -3), Morvena (640, Krypta Mgiel -3), Pyrrax (780, Serce Wulkanu -4);
respawn 1 h, czesty loot z zestawu koralowego (poziom 350: harpun, rozdzka, laska, rekawice, helm, zbroja,
nagolenniki, tarcza) albo wyzszych zestawow. Zestaw koralowy wypada tez rzadko z potworow wysp.

## Zadanie dnia (Z logika, C UI)

`daily/DailyTask`: raz na dzien kalendarzowy (`GameSession.today()`, w testach `setToday`) potwor ze stalych spawnow
(`MonsterManager.spawnedTypes`), nie boss, poziom od polowy do 120% (+5) poziomu postaci; 20-40 sztuk. Losowanie
powtarzalne (imie + dzien). Nagroda: 2x exp potworow, poziom x ilosc x 3 gp na konto. Akcja `npc:daily` u przewodnika.

## Inwazje (Z logika, S serwer)

`event/Invasion`: plany dla 4 miast (potwor, liczba, herszt), miejsce zbiorki 14-24 pola od swiatyni poza PZ,
liczenie zabic (`CombatSystem.setDeathListener`), nagrody 100 gp / wrog, 1 000 gp za herszta. Potwory z
`MonsterManager.spawnEvent` nie odradzaja sie, `clearEvent` usuwa resztki. Harmonogram w `ServerWorld.updateInvasion`.

## Bestiariusz i osiagniecia (Z logika, C okno)

`achievement/Bestiary` liczy zabicia (killListener w GameSession), `Achievement` (15 celow liczonych ze stanu postaci),
`Achievements` (zdobyte, raz na zawsze). `GameSession.checkAchievements` przy zabiciu i co sekunde w `update`
(na serwerze dla graczy online; klient online nie liczy - dostaje stan w SELF). Zapis `kill.<klucz>` = n albo n/B, `achievements`.

## Rynek graczy (Z logika, S serwer, C okno)

`market/Market`: oferty (id, sprzedawca, przedmiot, ilosc, cena za calosc), do 10 na gracza, 2000 na serwer, prowizja 2%.
Zaplata dla sprzedawcy offline czeka w `takeEarnings` i trafia na konto przy wejsciu. Serwer: MARKET LIST [szukaj] |
SELL tekst | BUY id | CANCEL id (`ServerWorld.marketCommand`), wystawianie, kupno i wycofanie tylko przy bankierze.
Kupujacy placi z konta (`BankAccount.debit`), potem z plecaka. Zapis `market.txt` (atomowo) i zapis postaci po kazdej operacji.

## Znajomi (Z logika, S serwer, C okno)

`friends/FriendList` (do 30 imion, bez powtorzen, zapis `fr.<i>`), `friends/FriendsView` (komunikat FRIENDS).
Serwer: FRIEND LIST|ADD|REMOVE (`ServerWorld.friendCommand`), dodac mozna tylko postac online albo z rankingu.
Wejscie i wyjscie gracza: wiadomosc SYSTEM do znajomych online (`tellFriends`).

## Dzwiek (Z synteza i dobor, C odtwarzanie)

- `audio/SoundSynth`: 13 efektow syntezowanych w kodzie (fale, szum, obwiednie), deterministycznie, WAV PCM 16 bit
  22 050 Hz. Bez plikow z zewnatrz. Klient zapisuje je raz (`~/.pixel/sfx-v1/` na PC, pamiec aplikacji na Androidzie).
- `audio/SoundCues`: zdarzenie walki -> efekt (trafienie, rana, blok, leczenie, zywiol czaru/pocisku, awans, smierc).
  Kazde zdarzenie gra raz, tylko na pietrze gracza i do 9 kafelkow (ciszej z daleka), ten sam efekt nie czesciej niz
  co 70 ms, najwyzej 3 dzwieki na klatke.
- Glosnosc w ustawieniach: 0 (wylaczony) / 30 / 60 / 100%. Brak urzadzenia audio wylacza dzwiek bez bledu.

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
- Inkantacje (etap 13, `data/incantations.csv`): kazdy czar (bez efektow run) ma slowa w stylu Tibii, np.
  `exura vita`, `exori max flam`, `exevo gran mas vis`, `utani gran hur`, `adori gran mort`. Wpisanie slow
  w czacie rzuca czar (wielkosc liter i odstepy bez znaczenia); online inni gracze widza slowa w czacie.
  Ksiega pokazuje slowa przy kazdym czarze. `SpellRegistry.byWords` / `words`.
- Czary 120-300 (etap 13): po trzy ataki na profesje (cel 150, obszar lub cel 200 i 300) i silne leczenie.
  Najsilniejsze maja dlugie odnowienie (8-30 s), bo i tak ograniczaja je wspolne odnowienie grupy ataku.
  Leczenie 120-150 jest tylko troche silniejsze od poprzedniego - inaczej postac z progu nizej
  przeskakiwala prog (EndgameSimulationTest).
- Runy etapu 13: wielka kula ognia i lawina (obszar 2, poz. 30), nagla smierc (cel, smierc, poz. 45, magia 15),
  ostateczne leczenie (poz. 24, magia 8); zaklinane z pustej runy i sprzedawane u magow kazdego miasta i w obozie.
- Mikstury etapu 13: silna i ostateczna mikstura ducha (paladyn, zdrowie i mana naraz, poz. 80 / 130),
  ostateczna mikstura many (magowie, poz. 250), ostateczna mikstura zdrowia (paladyn i mnich, poz. 250).
  Przyciski HP/MP biora najsilniejsza dozwolona miksture z plecaka (`GameSession.HP_TIERS` / `MP_TIERS`).

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

- Okno gry (0.209.0): 15 x 11 pol (`GameScreen.VIEW_TILES_W/H`), postac w srodku; zasieg dystansowy i czarow 7,
  wzrok potworow 7-9 (`MonsterManager.MIN_SIGHT/MAX_SIGHT`). Kazda wyspa ma dojscie (kladka, most, tunel pod
  morzem albo przewoznik); Lochy Zapomnianych pod najwieksza wyspa bez miasta (-1..-4, poziomy 350-960).

- Jedna mapa `maps/world` 256 x 232 x 11 pieter (wschodni kontynent x 168..255 = endgame) (`WorldLayout.WORLD_MAP`), generator `tools/mapgen/generate_world.py`.
- `Town` (maks. 4, swiatynia musi byc w strefie ochronnej i przechodnia) i `Region` (prostokat, zakres pieter,
  zalecany poziom) wczytywane z `towns=` i `regions=` w map.properties.
- `WorldMap.templeOf(miasto)` - odrodzenie po smierci; `regionAt(pozycja)`; `GameSession.checkRegion` wypisuje
  nazwe regionu i ostrzezenie przy wejsciu.
- Zapis w wersji 3 (`map=world`); starsze zapisy z mapy Varden sa przenoszone o (+48, +64).

## Planowane

 fishing, NPC, zadania, handel, bank w UI, PvP,
serwer i synchronizacja - patrz [TODO.md](../TODO.md).
