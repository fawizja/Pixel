# Systemy gry

Stan: **Z** = zaimplementowane i przetestowane, **C** = czesciowo, **P** = zaplanowane.

## Architektura

- `shared` - logika bez libGDX (testowalna na JVM, docelowo uzywana przez serwer).
- `core` - klient: renderowanie, UI, wejscie. Nie zawiera regul gry.
- `GameSession` (shared) - sesja laczaca postac, swiat i systemy. Offline dziala w kliencie; online
  serwer prowadzi po jednej sesji na gracza (zrodlo prawdy o HP, manie, przedmiotach i walucie),
  a klient trzyma replike bez symulacji (`copyStateFrom`, `applyRemotePosition`, `MonsterManager.syncRemote`).

## PvP (etap 13, zasady Tibii)

Zasady w `pvp/PvpRules` (wspolne dla serwera i testow) odtwarzaja swiat Open PvP Tibii. Zrodla (pobrane 28.10.2026):
[T1] https://www.tibia.com/gameguides/?subtopic=manual&section=combat (podrecznik: czaszki, blokady, 50% obrazen,
czarna czaszka), [T2] https://www.tibia.com/support/?subtopic=gethelp&entryid=100 (progi 3/5/10 i 6/10/20, blokada
PZ 15 min / 1 min), [W1] https://tibia.fandom.com/wiki/Skull_System, [W2] https://tibia.fandom.com/wiki/Combat_Controls
(tryb bezpieczny), [W3] https://tibia.fandom.com/wiki/Protection_Zone_Block, [Q1] https://www.tibiaqa.com/225/how-long-does-the-white-skull-last.

| Zasada | Tibia | Zrodlo | U nas |
|---|---|---|---|
| Tryb bezpieczny | nie atakujesz graczy bez czaszki; czary obszarowe ich nie omijaja | W2, T1 | przelacznik PvP wylaczony = tryb bezpieczny (`Verdict.PVP_OFF` dla ataku, czaru i runy celowanej); obszar rani kazdego (`SECURE_MODE_BLOCKS_AREA = false`) |
| Biala czaszka | atak/zabicie nieoznaczonego; po ataku tyle co blokada wylogowania (60 s od ostatniej agresji), po zabiciu 15 min | T1, W1, Q1 | `WHITE_SKULL_MS` = 60 s, po zabiciu `KILL_BLOCK_MS` = 15 min, kazda agresja odnawia |
| Zolta czaszka | kto (bez czaszki) atakuje oznaczonego, jest dla niego zolty; widzi tylko zaatakowany; zabicie zoltego jest usprawiedliwione | T1, W1 | `Status.yellow`, `skullSeenBy(...)` = YELLOW tylko dla ofiary, 60 s (`YELLOW_SKULL_MS`) |
| Pomaranczowa (zemsta) | ofiara nieusprawiedliwionego zabojstwa widzi zabojce na pomaranczowo 7 dni albo do zemsty; moze go bic bez bialej czaszki | T1, W1 | `Status.revenge`, `REVENGE_MS` = 7 dni, zabicie zabojcy zuzywa jedna zemste |
| Zielona (druzyna) | usunieta z Open PvP w 2013 r.; czlonkow druzyny nie mozna atakowac | W1 | brak zielonej czaszki; atak na czlonka druzyny odrzucony, obszar omija druzyne |
| Czerwona | 3 nieusprawiedliwione zabojstwa w 24 h, 5 w 7 dni albo 10 w 30 dniach; trwa 30 dni od zabojstwa ponad limit; smierc = utrata wszystkiego mimo blogoslawienstw | T1, T2, W1 | `RED_SKULL_KILLS {3,5,10}` w oknach `KILL_WINDOWS_MS {24 h, 7 d, 30 d}`, `RED_SKULL_MS` = 30 dni; smierc: plecak i ekwipunek na ziemi |
| Czarna | 6 w 24 h, 10 w 7 dni, 20 w 30 dniach; 45 dni; nie atakuje nieoznaczonych, bez czarow/run obszarowych, 100% obrazen od graczy, odrodzenie z 40 HP i 0 many, utrata wszystkiego | T1, T2, W1 | `BLACK_SKULL_KILLS {6,10,20}`, `BLACK_SKULL_MS` = 45 dni, `Verdict.BLACK_SKULL`, `CastResult.BLACK_SKULL`, `BLACK_SKULL_DAMAGE` = 1,0, `BLACK_SKULL_RESPAWN_HP` = 40 |
| Blokada PZ | atakujacy: 1 min od ostatniej agresji (takze atak na oznaczonego i obrona); zabojca nieoznaczonego: 15 min | T1, T2 | `PZ_BLOCK_MS` = 60 s przy kazdym trafieniu gracza; `KILL_BLOCK_MS` = 15 min po nieusprawiedliwionym zabojstwie; zabicie oznaczonego - zwykle 60 s |
| Blokada wylogowania | atakujacy i zaatakowany: 60 s od ostatniej agresji; zabojca 15 min; postac nie moze wyjsc | T1 | `LOGOUT_BLOCK_MS` = 60 s obu stronom, 15 min zabojcy; rozlaczony gracz zostaje w swiecie (`ServerWorld.disconnect`, `ServerPlayer.lingering`), ponowne logowanie odrzucone do konca blokady |
| Obrazenia | 50% miedzy graczami, 100% na czarna czaszke | T1 | `PVP_DAMAGE` = 0,5 |
| Punkty nieusprawiedliwione | kazde zabicie nieoznaczonego to pelny "frag"; w Retro: do 2 zabojcow (najwiecej obrazen + ostatni cios) | T2 | frag dostaje zadajacy ostatni cios (pomocnicy nie dostaja fragow) |

Pozostale zasady:
- Postacie ponizej poziomu 30 nie walcza z graczami (`PROTECTION_LEVEL`). W Tibii odpowiednikiem jest Rookgaard
  (wyspa bez PvP do poziomu 8); u nas nie ma osobnej wyspy, wiec zostaje ochrona poziomu.
- Usunieta (bo w Tibii jej nie ma): zasada "nie atakujesz postaci o ponad polowe slabszej".
- W strefie ochronnej (PZ) nie ma walki - ani atakujacy, ani cel nie moga w niej stac.
- Smierc z reki gracza: ta sama kara exp co od potwora, odrodzenie w swiatyni; smierc konczy blokady ofiary.
  Zabojca nie dostaje exp (w Tibii PvP daje doswiadczenie tylko na swiatach Hardcore/Retro - tu Open PvP).
- Stan (czaszki, zabojstwa, zemsta, blokady) trzyma serwer po imieniu i zapisuje (`pvp_<imie>`, zegar scienny):
  `biala;czerwona;zabojstwa;czarna;blokadaPz;blokadaWylogowania;imie@t1/t2+...`; stary zapis z 3 polami sie wczyta.
- Czary i runy celowane: te same zasady co atak (`PvpRules.spellHit`). Obszar: `PvpRules.areaHit` / `checkArea`
  (`pvp/PvpTargeting`) - trafia kazdego gracza w zasiegu poza druzyna, PZ i ochrona poziomu; nieoznaczony = biala
  czaszka. Odmowa zasad dla czaru celowanego = bez kosztu many/runy.

### Przelacznik PvP i czaszki

`PvpRules.Skull { NONE, WHITE, YELLOW, ORANGE, RED, BLACK }`. NONE/WHITE/RED/BLACK widza wszyscy (`Status.skull(now)`);
YELLOW i ORANGE tylko jeden gracz (`PvpRules.skullSeenBy(viewerStatus, imie, status, now)`, kolejnosc: BLACK, RED,
ORANGE, WHITE, YELLOW). "Oznaczony" = ma dla atakujacego jakakolwiek czaszke (`PvpRules.marked`).
- Tryb PvP: `PlayerCharacter.pvpMode()` - tylko w pamieci, po zalogowaniu wylaczony (= tryb bezpieczny Tibii).
- Postac z blokada nie wejdzie do PZ (komunikat "Walczysz z graczem - nie wejdziesz do strefy ochronnej jeszcze
  przez ..."); jesli juz w niej stoi, zostaje.
- Protokol: klient -> serwer `PVP ON|OFF|TOGGLE` albo `ACT pvp`. Serwer -> klient po kazdej zmianie
  `PVP 1|0 CZASZKA czaszkaDo blokadaPzDo blokadaWylogowaniaDo` (czasy serwera, 0 = brak; 6. pole nowe). Inni gracze:
  pole `czaszka` w `PL` to czaszka widziana przez odbiorce (moze byc YELLOW/ORANGE), na koncu `P|-` (tryb PvP).
- Testy: `TibiaSkullsTest` (progi, zolta, pomaranczowa, czarna, blokady 1/15 min, 50%/100%), `PvpRulesTest`,
  `PvpModeTest`; serwer: `NetworkSyncTest.pvpFollowsTheRulesOverTheNetwork` (blokada wylogowania, postac zostaje),
  `areaSpellsSkullsAndAggressorsWithThreePlayers` (obszar w trybie bezpiecznym, zolta czaszka).
- Ikony: `assets/ui/pvp/skull_white|red|yellow|orange|black.png` (16x16) i `pvp_off.png` / `pvp_on.png` (32x32)
  rysowane kodem w `tools/art/skull_icons.py`. Podglad: `-Dpixel.open=pvp|pzblock`.

API dla HUD (wszystko we wspolnym module):

| Wywolanie | Znaczenie |
|---|---|
| `session.pvpMode()` / `session.isPvpMode()` | czy tryb PvP jest wlaczony |
| `session.setPvpMode(boolean)` | ustawia tryb (bez komunikatu) |
| `session.togglePvpMode()` | przelacza, zwraca nowy stan (bez komunikatu) |
| `session.togglePvpMode(long now)` | przelacza z komunikatem w dzienniku (tak dziala przycisk i `/pvp` offline) |
| `Actions.apply(session, "pvp", now)` / online `net.send("ACT", "pvp")` | akcja przycisku (online wykonuje serwer) |
| `character.pvpMode()` | to samo co `session.pvpMode()` |
| `character.skull()` | `PvpRules.Skull` z ostatniej synchronizacji (sesja co krok / komunikat `PVP` online) |
| `character.skull(now)` | jw., ale `NONE` po uplywie czasu czaszki |
| `character.skullMs(now)` | pozostaly czas czaszki w ms (0 = brak) |
| `character.pzBlockedMs(now)` | pozostaly czas blokady wejscia do PZ w ms (0 = mozna) |
| `character.logoutBlockedMs(now)` | pozostaly czas blokady wylogowania (wieksza z blokad; ma ja tez zaatakowany) |
| `session.pzBlockedMessage(now)` | polski komunikat blokady PZ |
| `session.pvpStatus()` | pelny stan `PvpRules.Status` (skull(now), skullUntil(now), pzBlockedMs(now), logoutBlockedMs(now), unjustKills(now, okno), revengeOn(imie, now), yellowOf(imie, now)) |
| `hud.skullIcon("WHITE"/"YELLOW"/"ORANGE"/"RED"/"BLACK")` | `TextureRegion` ikony 16x16 albo null |
| `Hud.skullColor(nazwa)` / `Hud.skullName(skull)` | kolor imienia i polska nazwa czaszki |

`now` to czas swiata klienta (`GameScreen.now()`, online zgrany z serwerem) - ten sam, ktory dostaje `Hud.render`
jako `serverTimeMs`.
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
Okno domu przy drzwiach (`GameSession.houseDoorTap`, `HouseView`, `client/ui/HouseWindow`): obcy i gosc "tylko
wejscie" dostaja okno (wolny/zajety, wlasciciel, cena, czynsz, przycisk Kup -> `buyHouseAtDoor`), wlasciciel na
zewnatrz i gosc z pelnym dostepem otwieraja drzwi, wlasciciel w srodku dostaje okno: Zapros (pelny dostep / wejscie),
lista gosci z Usun, Otworz/Zamknij drzwi, Opusc dom (potwierdzenie, jak sprzedaz miastu). Online: HOUSE DOOR|BUY|LEAVE|
GUEST imie [entry] -> odpowiedz HOUSEINFO; HACC = cudze domy z dostepem gracza. `/dom wstep Imie` = samo wejscie.
Dostep (`HouseAccess`): ENTRY - wejscie i chodzenie; FULL/OWNER - takze wyrzucanie, podnoszenie, pchanie skrzyn,
schowki, drzwi i okna domu. Zapis gosci: `guests:id=Imie:full,Imie:entry` (stare linie bez poziomu = full).
Czynsz (`HouseRent`): 2% ceny tygodniowo (czas rzeczywisty, `-Dpixel.rentPeriodMs`), kupno oplaca pierwszy tydzien,
pobierany z banku (offline w sesji, stan w zapisie postaci `rent`/`hguests`; serwer: wlasciciel w grze w swojej sesji,
offline `ServerWorld.sweepHouseRent` na zapisie postaci; linie `rent:id=oplacony_do,zalegle`). Brak zlota = zaleglosc
i ostrzezenie; po 2 zaleglosciach dom wraca do miasta, rzeczy ze schowkow i podlogi ida do depozytu (monety na konto).
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

- `WorldClock`: doba gry = 6 h czasu rzeczywistego (`REAL_DAY_MS`): ok. 3 h jasno i 3 h ciemno, godzina gry = 15 min
  (`GAME_HOUR_MS`; do 0.212 doba trwala 2 h). Fazy swit/dzien/zmierzch/noc, plynne przejscia, noc min. 38% jasnosci.
  Czynsz, zaproszenia, odliczania i inne timery licza czas rzeczywisty i nie zaleza od dlugosci doby.
- Podziemia: jasnosc maleje z glebokoscia, min. 16%; postac zawsze ma poswiate 2,5 kafelka.
- Pochodnia z plecaka (POCH) - promien 5; pali sie 30 min czasu gry, potem znika i zapala sie nastepna
  (komunikat, gdy brak kolejnej). Zgaszona nie traci paliwa. Czas widac na karcie postaci.
- Czar Poswiata (LIGHT) daje wiekszy promien na czas; sytosc, swiatlo z czaru i pochodnia sa zapisywane.
- Zrodla swiatla na mapie: latarnie, pochodnie scienne, ogniska, krysztaly (takze lodowe), swiecacy mech,
  magma, swiatlo z otworu nad miejscem na line.
- Pochodnie i latarnie na murach: `world/WallLights` (WorldMap.wallLights()). Na mapie obiekt `wall_torch` /
  `wall_lantern` na polu muru, wiszacy na scianie poludniowej (mur poziomy) albo wschodniej (pionowy):
  `WallLights.face`. Generator (`tools/mapgen/wall_lights.py`, ostatni przebieg generate_continent.py)
  przenosi pochodnie i latarnie z podlogi na najblizszy taki mur w tym samym pomieszczeniu/ulicy (bez okien,
  drzwi i zaslaniajacych obiektow), reszte usuwa. Dotkniecie z pola obok (jak drzwi, akcja `open:x:y:z`)
  zapala albo gasi; nie da sie ich wziac. Zgaszona nie swieci (LightRenderer pomija ja, wersja `version()`).
  Stan: domyslnie wszystko sie pali; odstepstwa offline w postaci (`walllights`), na serwerze w
  `walllights.txt` i komunikat `WLIGHT x y z 1|0` do klientow (takze po zalogowaniu). API dla zadan:
  `isLit(x,y,z)`, `setLit(p, lit)`, `toggle(p)`, `setDefaultLit(p, lit)` (np. zgaszone do czasu oltarza,
  bez zapisu), `lightsIn(z, x0, y0, x1, y1)`, `front(p)`. Grafiki: `tools/art/wall_lights_import.py` (OTSP).
- Renderowanie: FBO o obnizonej rozdzielczosci, mnozenie przez scene; lista zrodel buforowana per pietro.
  Na czas rysowania do FBO wylaczane jest przyciecie (scissor) okna gry - inaczej scena wychodzila biala.
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
- Prawy panel: minimapa, ekwipunek (3x4 albo 5x2), Umiejetnosci (wysoki ekran), lista walki, pasek akcji. U gory
  paski HP/MP/doswiadczenie i ikony stanu, na dole konsola z zakladkami czatu.
- 2026-10-28 (Darek): dawny rzad przyciskow nad konsola (ATAK, UZYJ, KSIEGA, ROZMOWA, PODNIES, LOW, KON, PLECAK
  gdy ekwipunek jest zasloniety) stoi jak w Tibii na prawo od ekwipunku: kolumna akcji i kolumna przelacznikow
  (i, BIEG, TARCZA, POCH). Na niskim ekranie (telefon, zwarty ekwipunek 5x2) przelaczniki sa w kolumnie obok
  minimapy, a akcje w rzedzie pod nia, nad ekwipunkiem. Pojawienie sie przycisku (np. ROZMOWA obok NPC) przelicza
  uklad panelu (`Hud.layoutSide`). Przycisku CZAT nie ma: dotkniecie konsoli (albo Enter) otwiera pisanie.
- Pad w lewym dolnym rogu ekranu (nachodzi na widok gry i dolny pasek); konsola zaczyna sie za nim.
  Prawy panel ma 20% szerokosci (170-260 jednostek UI).
- Dotyk poza widokiem gry nie trafia na mape. Na szerokim ekranie okna (ksiega, NPC, mapa) stoja nad widokiem gry.

- Zakladki czatu jak w Tibii (`chat.ChatTabs`, 2026-10-28): Lokalny, Serwer, Handel, na zadanie Gildia i Druzyna
  (po pierwszej wiadomosci albo dolaczeniu do druzyny) oraz zakladka kazdej rozmowy prywatnej (otwiera ja wiadomosc
  od gracza albo echo wlasnej; krzyzyk na wybranej zakladce ja zamyka, najwyzej 8). Wybrana zakladka jest jasna,
  zakladka z nowa cudza wiadomoscia ma czerwona nazwe do czasu wybrania. Tab / Shift+Tab przelacza zakladki.
- Trasowanie: serwer wysyla `CHAT nadawca tekst kanal` (kanal: local, pm, letter, guild, party, trade, broadcast;
  starszy serwer bez kanalu - decyduje etykieta "[od X]", "[Handel] X"...). Wpisy dziennika `EventLog.Kind.CHAT`
  (NPC, wlasne slowa offline, wiesci gildii) ida do Lokalnego / Gildii, cala reszta dziennika do zakladki Serwer;
  ogloszenia administratora sa wpisem SYSTEM (Serwer i napis na widoku gry). Zdarzenia (lup, poziom, ostrzezenia)
  dalej pokazuja sie tez jako napisy na widoku gry przez 5 s.
- Pisanie: tekst bez "/" w zakladce Handel idzie jako `TCHAT tekst` (serwer: `CHAT "[Handel] imie" tekst trade` do
  wszystkich), w Gildii `GCHAT`, w Druzynie `PCHAT`, w rozmowie `PM imie tekst`; w Lokalnym i Serwerze jak dotad
  (slowa do NPC obok, inkantacje, `CHAT`). Polecenia "/..." dzialaja tak samo w kazdej zakladce.
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
- Zasady lupow (`monster/LootRules`, test `LootRulesTest`): oczekiwane zloto zwyklego potwora = poziom^1,12
  (pas 0,5-2x), boss >= 8x; szansa przedmiotu <= 1000*sqrt(250/wartosc) promili (boss 10x); zwykly potwor nie
  upuszcza nic drozszego niz 400*poziom+1000. Przedmioty tylko z bossow: `data/boss_items.csv` (wszystkie
  legendarne >= 400 000 gp tez), u bossa 1-5% zaleznie od wartosci, bez premii potwora dnia i zaklec; zwykly potwor
  z takim przedmiotem nie wczyta sie. `LootRules.rollBossLootPerPlayer` - osobny rzut na kazdego uczestnika
  (pod wyprawy na bossa w 5 graczy).
- Smierc gracza: -5% exp (nie ponizej poziomu startowego), pelne HP/MP, swiatynia Varden.
- Zdarzenia walki (`CombatEvent`) -> liczby obrazen, leczenia, awansu w kliencie.
- Cios wrecz niesie pozycje atakujacego (`from`, `isMeleeStrike`): klient rysuje wypad atakujacego w strone celu
  (180 ms) i krotkie ciecie na celu. Dziala tez online (EV z 10 polami).

## Druzyny (Z logika, S serwer)

- `party/PartyManager`: zaproszenie przez lidera (wazne 60 s), przyjecie, wyjscie, wyrzucenie przez lidera (`kick`);
  maks. 8 osob; po wyjsciu lidera przywodztwo przechodzi na nastepnego, przy jednej osobie druzyna sie rozpada.
  Wylogowanie = wyjscie z druzyny. Druzyna dziala tylko online (offline nie ma innych graczy).
- `party/PartyRules` - wspolne doswiadczenie dokladnie jak w Tibii. Zrodla: [P1] https://www.tibia.com/support/?subtopic=gethelp&entryid=91
  (tibia.com, "Shared Experience"), [P2] https://tibia.fandom.com/wiki/Party (wzor podzialu, 2 min aktywnosci).
  Dziala tylko, gdy WSZYSCY czlonkowie spelniaja warunki (inaczej kazdy dostaje swoje zabicia sam):
  najnizszy poziom >= 2/3 najwyzszego (P1: "poziom 40 dzieli z 60, a nie z 20"), kazdy najwyzej 30 pol od lidera,
  takze pietro wyzej albo nizej (P1), kazdy aktywny w ostatnich 2 min (P2; P1: "uleczyl czlonka albo zaatakowal
  agresywnego potwora" - u nas atak na potwora, `CombatSystem.lastMonsterAttackMs`, bo nie ma czarow leczacych innych).
  Premia zalezy tylko od liczby roznych profesji (P1):

  | rozne profesje | 1 | 2 | 3 | 4 | 5 (z Monkiem) |
  |---|---|---|---|---|---|
  | premia | +20% | +30% | +60% | +100% | +100% |

  P1 wprost: "party members of all 4 or 5 vocations ... bonus raised to 100%". Premia tylko za potwory dajace
  bazowo >= 20 doswiadczenia (`MIN_BONUS_EXPERIENCE`, P1). Podzial (P2): kazdy z P czlonkow dostaje
  ceil(M x (1 + premia) / P) x C; u nas M x C = doswiadczenie potwora dla wlasnego poziomu czlonka z jego zwojem nauki
  i potworem dnia, wypoczynek (odpowiednik staminy) liczony po podziale. Przyklady (M = 1000): 2 rycerzy 600 kazdy,
  rycerz + druid 650, 3 profesje po 534, 4 profesje w 4 osoby po 500, 5 profesji w 5 osob po 400.
  Roznica: w Tibii dzielenie wlacza lider (menu) i nie moze miec wtedy znaku walki; u nas dziala od razu w kazdej
  druzynie. Zabicie liczy sie do zadan dnia, zlecen i zadan calej druzyny.
- Klient: panel druzyny (imiona, poziom i profesja, HP% gdy widoczni, "daleko" poza zasiegiem; w naglowku premia
  "+100% exp" albo "bez wspolnego exp"), zakladka Druzyna pokazuje `PartyView.bonusLine()`; imiona niebieskie.
- `CombatSystem.setExperienceSharer(type, now)` - punkt podpiecia serwera (`ServerWorld.shareKill`). PvP miedzy czlonkami zablokowane.
- Protokol: `PARTY INVITE|ACCEPT|KICK imie`, `PARTY LEAVE|INFO`, `PCHAT tekst`; serwer odsyla `PARTY imie1,imie2`
  albo `PARTY -`, `PARTYINFO premia dzieli imie;poziom;PROFESJA;online ...` (`party/PartyView`, po kazdej zmianie
  i co 3 s, gdy cos sie zmienilo), `PARTYINV lider` do zaproszonego. `PINFO imie` -> `PINFO imie;poziom;PROFESJA;gildia;online`
  (`social/PlayerInfo`, z gry albo rankingu).
- Czat: `/zaproś Imię`, `/przyjmij [Imię]` (bez imienia - najnowsze zaproszenie), `/opuść`, `/drużyna` (sklad i premia),
  `/drużyna zaproś|przyjmij|wyrzuć|opuść Imię`, to samo po angielsku `/party invite|accept|kick|leave|info`.
- API klienta (`social/SocialClient`, przez `GameSession`; dla okna po dotknieciu gracza): `invitePlayerToParty(name)`,
  `acceptPartyInvite([leader])`, `leaveParty()`, `kickFromParty(name)`, `party()` (`PartyView`: czlonkowie z poziomem,
  profesja i stanem, premia, `bonusLine()`), `playerInfo(name)` (`PlayerInfo`, online odpowiedz serwera przychodzi
  asynchronicznie - `social().version()` rosnie), `addFriend(name)`, `removeFriend(name)`, `friends()` (`FriendsView`
  ze stanem w grze). Online kazda akcja idzie do serwera (`setSocialLink`), offline druzyna odpowiada "dziala online",
  a znajomi zapisuja sie w postaci.

## Stada dla druzyn (etap 9, generator)

- `generate_continent.party_packs` (wlasne ziarno - reszta mapy bez zmian): spawny potworow powyzej poziomu 15, dalej
  niz 60 pol od swiatyn, bez bossow, nocnych i kanalow, dostaja znacznik `stado` w spawns.csv: pietro -2 albo potwor
  od poz. 60 -> 3-4 sztuki (promien >= 4); pietro -3 i nizej albo potwor od poz. 150 -> 4-6 sztuk (promien >= 5) i w 40%
  drugie stado 3-4 sztuk obok (tylko na polu osiagalnym pieszo, w tym samym regionie). Okolice miast i Varden bez zmian.
- Potwory ze stada daja pelne doswiadczenie: dawne `CombatSystem.PACK_EXP_PERCENT` = 85% usuniete przy przejsciu na zasady Tibii, bo w Tibii
  nie ma zadnej obnizki doswiadczenia za gestosc potworow.
- Liczby (0.212 -> etap 9): punkty spawnu 960 -> 1071, potwory 2283 -> 3803 (pietro 0: 1527 -> 2322, -1: 367 -> 622,
  -2: 206 -> 416, -3: 85 -> 213, -4: 46 -> 131, -5: 18 -> 40). `ServerLoadBudgetTest` (200 graczy): 4,7 ms -> 6,7 ms na tik
  (bez optymalizacji ok. 10 ms); pomogly zbior pol graczy w `MonsterManager.canOccupy` (zamiast petli po 200 graczach
  przy kazdym polu sciezki) i tablica pieter w `WorldMap` zamiast TreeMap.

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

## Drzwi poziomu i zadania z oltarzami profesji (Z logika, S serwer, C rysowanie)

- Drzwi poziomu (wielokrotnego uzytku): plik mapy `level_doors.csv` (`x;y;z;minimalny poziom`, pole z obiektem
  `door_level`, ktory jest zwyklymi drzwiami w murze ze zlota tabliczka). `MapLoader` wywoluje
  `WorldMap.addLevelDoor(p, lvl)`: drzwi + straznik poziomu na tym jednym polu (`requiredLevelAt`). API:
  `world.levelDoorAt(p)` (0 = nie drzwi poziomu), `world.levelDoors()`, `WorldMap.levelDoorSign(lvl)`
  ("\"Tylko dla poziomu 20+\"" - proste cudzyslowy, czcionka gry nie ma „ ”). Dotkniecie (`GameSession.useAt`) pokazuje napis; nizszy poziom - drzwi sie nie
  otwieraja, krok w drzwi konczy sie tym samym komunikatem; od poziomu - drzwi sie otwieraja i mozna przejsc.
  Generator: `ctx['level_doors'].append((x, y, z, poziom))` i obiekt `door_level` w przerwie muru.
- Zadania z oltarzami: `world/AltarQuest` z pliku `altar_quests.csv` (wiersz `quest` + wiersze `altar` na kazda
  profesje: oltarz, pole przed nim, pochodnia na murze, przedmiot, cel teleportu, skrzynia). Przedmiot na oltarzu to
  `displays.csv` (tylko grafika, nie do podniesienia ani przesuniecia). Pochodnie zadania sa domyslnie zgaszone
  (`WallLights.setDefaultLit(false)`) i nie zapalaja sie dotknieciem; `AltarQuest.updateTorches` zapala pochodnie
  oltarza, gdy na polu stoi zywa postac wlasciwej profesji. Dzwignia (`lever`, na prawo od pola rycerza) przy
  wszystkich pieciu przenosi kazda postac na jej pole w skarbcu, inaczej mowi, kogo brakuje.
  Skarbiec: skrzynie `TreasureChests.Chest` z `vocation` (identyfikator `q_<id>_<profesja>`), samo zloto, raz na
  postac (`openedChests`), cudzej profesji nie da sie otworzyc. Ze skarbca schody na -2.
- Offline: `GameSession` liczy pochodnie sama (sama postac); `-Dpixel.altarGhosts=PALADIN,DRUID` (albo `ALL`)
  stawia "duchy" profesji na ich polach (test i zrzuty). Online: klient nie zmienia pochodni zadan
  (`setAltarsLocal(false)`), serwer co takt liczy je z pozycji wszystkich graczy (`ServerWorld.updateAltarTorches`,
  komunikaty `WLIGHT`) i podstawia `GameSession.AltarParty` (dzwignia przenosi wszystkich graczy z pol).
- Grobowiec Pieciu Profesji (generator `tools/mapgen/quests.py`): kapliczka na Pustyni Qasirah z drzwiami poziomu 20,
  grobowiec -1..-3 (szkielety, hieny, trolle, golemy, skorpiony; region 20-60), sala oltarzy i skarbiec na -3
  (strefy ochronne), nagroda 20 000 gp dla kazdej profesji.

## Wyprawy na bossa w piec postaci (Z logika, S serwer, C rysowanie)

- Dane: plik mapy `boss_quests.csv` (generator `tools/mapgen/boss_quests.py`), wiersz `quest;id;nazwa;poziom;boss;
  dzwignia;przedsionek x0,y0,x1,y1,z;arena x0,y0,x1,y1,z;boss;portal;cel wyjscia;drzwi poziomu` i piec wierszy
  `square;id;pole;cel na arenie`. `MapLoader` -> `world/BossQuest` (`world.bossQuests()`, `bossQuest(id)`,
  `bossQuestByLever/ByPortal/ByArena/BySquare`, `inBossQuestRooms` - bez losowych skrzyn i pulapek). Nowa wyprawa =
  nowy wpis w `QUESTS` generatora (kraina, styl lochu, boss z monsters.csv, potwory w komorach); kod sie nie zmienia.
- Uklad: na powierzchni kapliczka z drzwiami poziomu (`level_doors.csv`) i schodami, loch na -1 z potworami progu,
  przedsionek (strefa ochronna) z piecioma polami `boss_plate` w rzedzie i dzwignia `lever` nad srodkowym polem,
  obok zamknieta arena (bez wejscia, nie jest strefa ochronna) z polami docelowymi, miejscem bossa i portalem
  `boss_portal` w rogu. Grafiki pol i portalu rysuje `tools/art/boss_quest_art.py`.
- Przebieg: `game/BossQuestRuntime` (wspolny dla gry offline i serwera; gracze przez `BossQuestRuntime.Hero`).
  Dzwignia: arena wolna (`Phase.IDLE`), ciagnacy nie ukonczyl wyprawy, na pieciu polach stoi piec zywych postaci
  (dowolne profesje), zadna nie ukonczyla - wtedy teleport na arene i boss (`MonsterManager.spawnEvent`, bez
  odradzania), inaczej komunikat (ilu brakuje / kto juz ukonczyl / arena zajeta). Smierc bossa: kazdy uczestnik zywy
  i na arenie dostaje wlasny lup `LootRules.rollBossLootPerPlayer` do plecaka (pelny - depozyt, w ostatecznosci
  ziemia), komunikat z lista (przedmioty tylko z bossa w gwiazdkach + osobny wiersz "Rzadki lup tylko z bossa"),
  zapis `PlayerCharacter.completedBossQuests` (klucz `bossq` w zapisie postaci); cialo bossa jest puste
  (`CombatSystem.setLootOwner`). Portal przenosi do przedsionka (`Phase.CLEARED`, po 10 min sam wynosi maruderow);
  arena wolna, gdy nikt z druzyny na niej nie zostal. Reset: w trakcie walki brak zywego uczestnika na arenie
  (smierc, ucieczka, wylogowanie) - boss znika, dzwignia znow dziala. Postac wczytana na arenie (bez trwajacej walki)
  wraca do przedsionka.
- Serwer: jeden `BossQuestRuntime` w `ServerWorld` (pozycje, teleporty, boss, lup, ogloszenie zabicia), sesje dostaja
  go przez `GameSession.setBossQuests`; klient online wysyla tylko `ACT open:` dzwigni. Offline:
  `-Dpixel.bossGhosts=4` stawia duchy na wolnych polach (licza sie tylko przy dzwigni), `-Dpixel.bossWin=true`
  zabija bossa jednym ciosem (podglad lupu).
- Wyprawy (2026-10-28): Leze Grimtootha (las na wschod od Varden, poziom 30, Grimtooth), Lodowy Tron Ysolde
  (lodowa polnoc, 100, Ysolde), Krypta Neferakha (zachodnia pustynia, 200, Faraon Neferakh), Kuznia Ignarotha
  (zbocze wulkanu, 400, Ignaroth).

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

Nowe potwory (2026-10-28, 32 gatunki i 4 bossow jaskin, poziomy 9-930): wlasne projekty i nazwy, grafiki tylko
z OpenTibia Sprite Pack (stroje 4, 20, 24, 27, 28, 30, 38, 42, 43 dotad nieuzywane; reszta to stroje z maska kolorow
albo przebarwienia w `tools/art/otsp_import.py`). Dane Canary (GPL) sluzyly tylko jako punkt odniesienia dla proporcji
statystyk (doswiadczenie/HP, zywioly, szybkosc), statystyki liczone z krzywej naszych potworow. Spawny w krainach
(`ZONES`), wyspach (`ISLAND_TIERS`), tunelach, kanalach, kopalniach, jaskiniach, Otchlani Wulkanu, Lochach Zapomnianych
i na drogach wypraw na bossa. Bossowie: Wroniec (32, Grota Rozbojnikow -2), Szlamiara (95, Leze Krokodyli -2),
Velmira (290, Grota Upiorow -2), Suweren Ostrzy (640, Jaskinia Cienia -3), respawn 1 h; nowe przedmioty tylko z bossow:
kaptur z kruczych pior, laska z Topieli. Nowe materialy (skupuja sklepy z towarami): czarne pioro, sluz czerwia,
plytka pancerza, ektoplazma, odlamek gargulca. Test `MonsterArtAndNamesTest`: kazdy potwor ma grafike z klatkami
kroku, zaden nie nosi nazwy znanego potwora z Tibii.

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
  szansa `min(70%, 10% + 0,6% * umiejetnosc)`. Potrzebna wedka (w plecaku albo w rece) i robaki w gniezdzie
  amunicji (`EquipSlot.AMMO`, obok spodni - robak ma slot AMMO w items.csv); kazde zarzucenie zuzywa 1 robaka,
  pusty stos uzupelnia sie robakami z plecaka. Bez wedki / bez robakow / robaki tylko w plecaku - osobne komunikaty
  (`FishingResult.NO_ROD`, `NO_BAIT`, `BAIT_NOT_IN_SLOT`). Robak 1 gp u kazdego kupca z plecakami (tam tez wedka),
  postac startowa (bez paladyna) ma 10 robakow w gniezdzie. Zlowiona ryba, ktora nie zmiesci sie w plecaku = robak
  zostaje; kafelek po zlowieniu odpoczywa 2 min (stan ulotny); perla 3% od umiejetnosci 30.
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
