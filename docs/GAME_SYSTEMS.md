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

## Trening offline przy posagu (Z logika, S serwer, C okno, generator `tools/mapgen/trainers.py`) - 2026-11-12

Wzorowane na Canary (`offline_training` w `player.cpp`, `skill_trainer.lua`), z uproszczeniami opisanymi nizej.
- Posag treningowy (`training_statue`, grafika z brazowym odcieniem) stoi w swiatyni kazdego miasta (generator wstawia go przy scianie
  pokoju swiatyni, nie na dywanie ani przy drzwiach/ oltarzu/ NPC, bez zamykania przejsc). Dotkniecie posagu otwiera okno wyboru:
  Miecz, Topor, Maczuga, Piesci, Dystans, Poziom magii (`OfflineTraining.Choice`) albo "Zostan w grze".
- Tylko konto premium (jak w Canary `offlineTrainingSkill`: tylko premium); bez premium komunikat odmowy, brak wylogowania. Odmowa tez w
  trakcie walki albo blokady strefy ochronnej (jak zwykle wylogowanie) i dla martwej postaci.
- Wybor zapisuje sie w postaci (`offtrain=sekundy,wybor,czas_scienny`), postac sie wylogowuje (offline: powrot do menu, online: serwer
  zapisuje i wysyla `TRAINLOGOUT`, klient odlacza sie). Przy nastepnym wejsciu `OfflineTraining.onLogin` rozlicza trening:
  czas offline krotszy niz 10 min = bez treningu i komunikat; dluzszy niz 21 dni = liczy sie 21; trening trwa tyle, ile zostalo na pasku
  (maks. 12 godz.), reszta czasu odnawia pasek.
- Wzory (Canary): proby bronia `(sekundy / szybkosc_ataku_profesji) / 2`, dystans `/ 4`; magia `sekundy * mana_na_sekunde` (regeneracja
  many profesji z `Balance`); tarcza `sekundy / 4`, ale tylko gdy wybrana umiejetnosc sie zmienila (jak w Canary). Proby przechodza przez
  `Skills.train` czyli mnoznik serwera dla etapu umiejetnosci. Raport trafia do dziennika ("Podczas nieobecnosci trenowales przez ...",
  "Twoja umiejetnosc ... zmienila sie z poziomu ... na ...").
- Pasek "Trening offline" w panelu umiejetnosci (komputer) i oknie Umiejetnosci (telefon) pod Stamina: 12:00 po pelnym naladowaniu,
  odnawia sie 1:1 podczas gry (`SkillSheet.offlineTraining`), niebieski.
- Uproszczenia wzgledem Canary: brak osobnego "czasu treningu" w sekundach liczonych z `offlineTrainingTime` w bazie (u nas pole `offtrain`
  w zapisie postaci), brak przyspieszenia przez dummy w domu (to osobny system), wszystkie umiejetnosci walki liczone mnoznikiem serwera.
  Gra lokalna: wylogowanie to powrot do menu; rozliczenie przy "Graj lokalnie" (zegar scienny) i w komunikacie logowania.

## Postac (Z)

- Tworzenie: poziom 8, 10 000 gp w plecaku, 0 gp w banku, zestaw startowy profesji, start w swiatyni Varden.
- Miasto startowe (jak w Tibii, 2026-11-05): ekran tworzenia postaci (`VocationSelectScreen`, karta z wycinkiem
  minimapy, klimatem i opisem kazdego miasta - `StartingTowns`) i logowanie online (nowe konto) pozwalaja wybrac
  dowolne miasto swiata (`world.towns()`: Varden, Frostholm, Selvaran, Qasirah, Valmir, Nerys; zadne nie wymaga
  premium - na wyspy Nerys plywa prom woznicy). Postac tam mieszka (`homeTown`) i zaczyna w swiatyni. Domyslnie
  `start.town` (Varden). Online: szoste, opcjonalne pole `HELLO` (brak/`-` = domyslne, nieznane miasto = odmowa
  bez zakladania konta; dla istniejacej postaci pomijane). Offline nieznane miasto = domyslne. Zmiana miasta
  pozniej: kaplan w swiatyni, wiersz "Zamieszkaj w ..." (`GameSession.settleHere`, darmowe). Sceny GUI: `create_town_mobile`, `create_town_desktop`, `start_frostholm`.
- Poziomy 1..1000, doswiadczenie w `long` z nasyceniem; HP i mana rosna przy awansie.
- Obrazenia, leczenie, mana, regeneracja co 6 s czasu gry.
- Umiejetnosci (`Skills`): piesci, bron, dystans, tarcza, magia, wedkarstwo; rosna z uzyciem wg krzywych Tibii dla profesji (sekcja umiejetnosci nizej).
- Walidacja imienia (3-20 liter, spacje, apostrof).

## Jezyki: angielski, polski, hiszpanski (Z, S serwer, C UI)

- `Language` (EN domyslny dla nowych instalacji, PL, ES) i `Messages` (shared, `pl.fawizja.pixel.i18n`): teksty w
  `shared/src/main/resources/i18n/messages_{en,pl,es}.properties`, klucz `obszar.hasz`, argumenty `{0}`, `{1}`;
  brak klucza w jezyku = polski. Liczby: `Messages.number` (en `1,234`, pl `1 234`, es `1.234`). Teksty zrodlowe w
  enumach i stalych zostaja polskie i sa tlumaczone przy odczycie (`Messages.tr`), a porownania niezalezne od jezyka
  robia `Messages.is / startsWithAny / containsAny`. Znaczniki protokolu (`[Gildia] `, `[Drużyna] `, `[od X]`) i polecenia
  czatu (`/dom wpuść`...) pozostaja polskie we wszystkich jezykach (aliasy `/idz`: role w `NpcFinder` maja tez en/es).
- Nazwy danych (przedmioty, potwory, czary, zadania, NPC z powitaniami): `i18n/data_{en,es}.csv`, wiersz
  `rodzaj;klucz;pole;tekst` (`DataNames`, `Messages.data`), brak wiersza = polski z CSV z danymi. Zaklecia (slowa
  `exura`...) zostaja po tibijsku. Nie tlumaczone: opisy przedmiotow i potworow, dialogi `dialogues.csv`, nazwy miast i miejsc
  (nazwy wlasne), nazwy na mapie.
- Wybor: pasek 3 przyciskow (`LanguageBar`) w menu glownym, logowaniu i tworzeniu postaci oraz pierwszy wiersz ustawien
  (`settings:lang`); zapis w `GameSettings` (klucz `lang`), dziala od razu. `-Dpixel.lang=en|pl|es` wymusza jezyk (zrzuty).
- Online: siodme, opcjonalne pole `HELLO` to jezyk (`en|pl|es`, brak = `pl`, jak starzy klienci). Serwer trzyma jezyk w
  `ServerPlayer.language` i formatuje kazdy komunikat per odbiorca (`Messages.runAs`), wiec gracze w roznych jezykach widza
  to samo zdarzenie w swoim jezyku; wiadomosci rozgloszeniowe sa tlumaczone osobno dla kazdego odbiorcy.
- Testy: komplet kluczy i argumentow w 3 paczkach (`MessagesTest`), nazwy danych i NPC (`DataNamesTest`), trwalosc
  jezyka (`GameSettingsTest`), brak polskich literalow w UI klienta (`NoPolishLiteralsTest`), glify czcionki dla
  ñ á é í ó ú ü ¿ ¡ i polskich liter (`FontCoverageTest`).

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
- Plecak jak w Tibii (2026-11-12, `inventory/ContainerGrid`, `inventory/ContainerType`, `client/ui/ContainerView`): domyslnie
  20 pol na jednej stronie (komputer 5 x 4, telefon 10 x 2), bez zakladek i stron. Wieksze kontenery -
  `backpack_traveler` (24 pola) i `backpack_explorer` (36 pol), przewidziane jako nagrody za zadania - zastepuja biezacy plecak po uzyciu
  z plecaka (poprzedni wraca do torby, mniejszy lub rowny nic nie zmienia), a okno przewija je w pionie: kolko myszy, pasek przewijania
  przy prawej krawedzi (przeciaganie suwaka, strzalki) i przyciski `<` `>` na telefonie. Okno ma zawsze te sama wysokosc.
  Logika widzi jeden `Container` (`PlayerCharacter.setBackpackSlots`, 20..80), wiec lup, sklepy, handel i mikstury dzialaja bez zmian.
  Zapis: `bagslots=<n>` (gdy inne niz 20); stary zapis z `bags=<n>` (strony po 20) wczytuje sie jako jeden przewijany plecak
  `20 * (1 + n)` pol - nic nie ginie. Weteran (poziom 250+) startuje z plecakiem podroznika (24 pola). Akcja `bag_detach` zniknela.
- Przeciaganie: przytrzymanie i przesuniecie przedmiotu przenosi go na inne pole albo na panel ekwipunku (zalozenie), a przy otwartym
  depozycie na jego okno. Krotkie dotkniecie dziala jak dotad.
- Wskaznik pingu (`net/PingMeter`, `NetClient.ping`): pod ramka widoku, obok FPS, kropka i wartosc w ms - zielona < 100 ms, pomaranczowa
  100-250, czerwona > 250 (albo brak odpowiedzi > 6 s). Prawdziwy czas obiegu: klient wysyla `PING <nanoTime>` co 2 s, serwer odpowiada
  `PONG` od razu w watku polaczenia (poza kolejka swiata), pokazywana jest mediana 5 ostatnich pomiarow. Gra lokalna (bez sieci) kropki
  nie pokazuje; do zrzutow `-Dpixel.fakePing=MS`.

### Zestawy Hinasa (dane, 2026-11-10)
- 11 zestawow z grafik Hinasa (OTLand, docs/CREDITS.md) i 8 pojedynczych przedmiotow, `items.csv` id 256..392
  (`TileArt`: przedmioty od id 256 w komorkach od 2048, limit id 767; atlas grafik 32 px powiekszony do 2048 komorek).
- Zestaw = helm, zbroja, nagolenniki, buty, tarcza, bron rycerza (miecz/topor/maczuga), luk, rozdzka (czarodziej),
  laska (druid), ksiega czarow (tarcza magow: SHIELD z profesjami SORCERER,DRUID, mala obrona + ochrony) i amulet
  (slot AMULET: ochrony, pancerz 0-2). Statystyki z krzywej istniejacych przedmiotow (interpolacja ataku / pancerza /
  obrony miedzy progami), wartosc z poziomu, wiec rzadkosc, klasa Kuzni i miejsca nasycen wynikaja same.
- Poziomy i zrodla: Kogucie 20 (kowale i czarodzieje miast, Iskrzyk, Akolita), Cukrowe 35 (Paser z tuneli, Latarnik),
  Zarazy 80 (bagna, Szlamiara), Zamieci 120 (lodowe wije i golemy, Ysolde), Smocze 170 (Straznik swiatyni, dzungla),
  Bialego Slonca 210 (mumie, gargulce, Faraon), Feniksa 330 (wulkan, Ignaroth), Odmetow 420 (topielcy, piraci, Krwawa
  Broda), Ksiezycowe 600 (upiory mgly, Zmierzchnik, Morvena), Anielskie 720 (Otchlan, bronie z Kha'zuula), Piekielne 850
  (Otchlan; zbroja i bronie legendarne tylko z bossow Pyrrax i Mor'gath, `boss_items.csv`).
- Ochrony jak w zestawach Tibii (`premie`): kazdy zestaw chroni przed swoim zywiolem, czesc ma slabosc (np. Feniks: lod).

## Waluty i bank (Z, UI banku - C)

Gold / Platinum / Crystal (100:1). Wplata, wyplata, przelew, placenie z reszta (wszystkimi nominalami).
Monety lacza sie same (`Container.normalizeCoins`): kazde dodanie monet (lup, podniesienie, sprzedaz u NPC, wyplata,
reszta) i polaczenie stosow przy przenoszeniu wymienia pelne 100 zlotych na 1 platynowa i 100 platynowych na
1 krysztalowa; niepelne stosy tej samej monety sie lacza. Pelny plecak przyjmie monety, jesli po wymianie sie zmieszcza.
Stare zapisy ze stosami po 100 sztuk sa wymieniane przy odczycie (wartosc i saldo konta bez zmian). Uzycie monety
tylko podaje wartosc; w plecaku monety wygladaja jak kupka (wiecej sztuk - wyzsza kupka).
Wszystko transakcyjne, z ochrona przed przepelnieniem. Bankier w kazdym miescie (jedno konto).
Depozyt przedmiotow (0.27.0): `PlayerCharacter.depot()` - 100 miejsc (`DEPOT_SLOTS`), wspolny dla miast, tylko przy bankierze albo skrzyni
(`GameSession.depotStore/depotTake`, przenosi wszystkie sztuki ile sie zmiesci, monety odrzuca). Zapis: `dp.<slot>`.
Szafki depozytu (`depot_locker`) w budynkach depozytu kazdego miasta otwieraja ten sam depozyt.
Okno depozytu jak w Tibii (2026-11-12): dotkniecie skrzyni na mapie (`GameSession.useAt` -> `takeDepotOpen`) otwiera na komputerze siatke
depozytu obok plecaka (5 x 4 z przewijaniem), przycisk "Odloz lup" i przeciaganie albo dotkniecie pola miedzy plecakiem a depozytem
(`GameSession.depotTransfer`, akcja `depot_move:B|D:pole:B|D:pole`, `-1` = pierwsze wolne pole, stosy sie lacza). Okno zamyka sie po
odejsciu od skrzyni. Dziala tak samo online (akcja `ACT`, serwer sprawdza bliskosc skrzyni) i offline; zawartosc jest w zapisie postaci.
Na telefonie depozyt zostaje lista przy skrzyni (jak u bankiera).
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
Domy na sprzedaz i lozka (2026-10-29): cena 1000 gp za pole wnetrza (dzielnica krolewska Varden 2000, przedmiescia
i chaty w dziczy 800, wioski 600); kupno najpierw z konta, reszta monetami z plecaka (`GameSession.claimHouse`,
`houseFunds`). Mapgen `tools/mapgen/homes.py`: kazdy dom 1-4 lozka (`beds_for`: <=12 pol 1, <=20 2, <=30 3, wiecej 4;
osobne ziarno z id domu), po 2 domy w zywych wioskach (`wies_*`), po 2 przy bramach miast (`<miasto>_pN`) i 6 chat
w dziczy (`wild_N`) z drozka do drogi i PZ nad domem; `check_houses` sprawdza drzwi, lozka i przejscie. Pole `miasto`
domu poza murami = najblizsze miasto. Wolne domy maja tabliczke nad drzwiami i napis "Na sprzedaż: cena"
(`GameScreen.drawSaleSigns`; online HACC ma 3. pole: id domow z wlascicielem -> `GameSession.setTakenHouses`).
Lozko (obiekt `bed`, uzycie dotknieciem): w domu z wstepem (wlasciciel/gosc) postac kladzie sie i wylogowuje
(`GameSession.useBed` -> `takeSleep`, klient pokazuje sen `-Dpixel.sleepMs`, online serwer wysyla SLEEP x y z);
w cudzym domu "To nie twoje łóżko.", w walce odmowa. Zapis `slept` = chwila zasniecia; po zalogowaniu
`house/BedRest.wakeUp`: 2% zycia i many na minute snu (pelnia po 50 min), stamina jak po kazdym wylogowaniu
(`Stamina.onLogin`, to samo tempo odnowienia).
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
- Kaplan: pelne leczenie i zamieszkanie w miescie (`homeTown`). Blogoslawienstwa sprzedaje szesciu mnichow (`NpcRole.BLESSER`,
  `Blessing`, `BlessingStatus`, `DeathLoss`) w malych kaplicach przy miastach (`tools/mapgen/chapels.py`), po jednym na NPC:
  darmowe konto - Varden (Duchowa Tarcza), Frostholm (Objecia Swiata), Qasirah (Ogien Slonc); premium - Valmir (Iskra Feniksa),
  Selvaran (Madrosc Samotnosci), Nerys (Zrzadzenie losu, wymaga innego blogoslawienstwa). Cena zalezy od poziomu, rozmowa slowami
  (blogoslawienstwo / cena / tak / gdzie) albo okno NPC; sklep ich nie sprzedaje. Kazde z pieciu zwyklych zmniejsza strate o 8%
  (piec = 40%, ponizej poz. 24 = 50%, promocja +30%); Zrzadzenie losu chroni w PvP. Do poz. 30 darmowe Blogoslawienstwo Poszukiwacza.
  Okno `/blogoslawienstwa` jest informacyjne (co masz i gdzie kupic).
- Smierc (`PlayerCharacter.applyDeath`): exp, umiejetnosci i poziom magiczny tracone tym samym ulamkiem co w Canary
  (`Balance.deathLossFraction`); plecak wypada z szansa 100/70/45/25/10/0%, kazdy zalozony przedmiot 10/7/4,5/2,5/1/0% (0-5 blogoslawienstw),
  do ciala gracza (`GroundItems.addPlayerCorpse`, 10 min, widoczne dla wszystkich). Amulet Straty chroni caly ekwipunek i niszczy sie;
  czerwona i czarna czaszka traca wszystko. Blogoslawienstwa znikaja przy smierci (w PvP bez czaszki tylko Zrzadzenie losu).
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
  online liczy ja serwer (akcja `npc:ask:<tekst>`). Bez pogawedek: tylko linie potrzebne do gry (gdzie szukac
  celu zadania, brama poziomu za mostem, receptury u kwatermistrza obozu); uslugi sa przyciskami okna, a na inne
  slowa (np. "czesc") NPC odpowiada swoim jednym powitaniem z npcs.csv i lista tematow.

## Uslugi w kazdym miescie: pupile, wladcy, zaklinacze (Z logika, S serwer, C okna, generator) - 2026-11-06

Darek: "w kazdym miescie npc odpowiedni za kupno danego pupila, premium u krola, krolowej lub lorda, kradziez zycia,
krytyk itp. u NPC w miastach". Kazde z 6 miast na powierzchni (map.properties `towns=`) ma trzech nowych NPC; stawia ich generator
(`tools/mapgen/town_npcs.py`, wywolany na poczatku `generate_continent.finish`, wspolrzedne stale przy kazdym uruchomieniu):
wewnatrz zamknietego budynku miasta (nie w domu na sprzedaz), na wolnym polu podlogi przy scianie, nie przy drzwiach
ani schodach, bez zamykania przejscia, osiagalni ze swiatyni i co najmniej 5 pol (Chebyshev) od innych NPC - rozmowa
wybiera najblizszego NPC w zasiegu 2, wiec gracz przy starym NPC nigdy nie trafi na nowego. Najpierw budynek
"kotwicy" (opiekun u kupca/karczmarza, wladca u zarzadcy domow/bankiera/kaplana, zaklinacz u kowala/maga), potem
najblizszy inny budynek miasta. Grafiki: stroje OTSP z maska kolorow (`_ROLE` petkeeper/ruler/imbuer i
`RULER_COLORS` w `tools/art/otsp_import.py`).

- **Pupile** (`pet_*`, `GameSession.togglePet`): pupil chodzacy za postacia daje +`PlayerCharacter.PET_EXP_PERCENT` (5)%
  doswiadczenia z potworow. Liczy to `CombatSystem.killExperience` na koncu (po etapach serwera, zwoju nauki, potworze dnia
  i wydarzeniu serwera; przed wypoczeciem i stamina), wiec tak samo solo, w druzynie (`ServerWorld.shareKill`) i online
  (serwer trzyma `pet` w swojej sesji). Widac to w opisie przedmiotu, w komunikacie "... chodzi teraz za tobą: +5%
  doświadczenia z potworów." i w karcie postaci (`SkillSheet.expRateLabel`: "exp x80, pupil +5%").
- **Opiekun zwierzat** (`<miasto>_petkeeper`, rola MERCHANT, `Npc.isPetKeeper()`, sklep `<miasto>_pets`): sprzedaje
  tylko pupila swojego miasta za 5000 gp, niczego nie skupuje (okno bez skupu i zapasow, z opisem premii). Pupile
  zniknely ze sklepow ogolnych. Varden - szczenie, Frostholm - kruk, Selvaran - kocie, Qasirah - fenek, Valmir - mewka
  (`pet_gull`, nowa), Nerys - papuzka (`pet_parrot`, nowa; obie to przebarwiony kruk OTSP, ta sama mechanika).
- **Wladca** (rola KING, `<miasto>_ruler`; w Varden zostaje krol Aurelian `varden_king`): konto premium i promocja
  dokladnie jak u krola (`buyPremium`, `buyPromotion`, slowa "premium"/"promocja") - promocja tez, bo to jedna rola i
  jedno okno; w kazdym miescie te same ceny i warunki. Kapitan bez premium podpowiada wladce swojego miasta
  (`GameSession.premiumHint`), `/idz krol|krolowa|lord|wladca|premium` prowadzi do najblizszego.
- **Zaklinacz** (rola IMBUER, `<miasto>_imbuer`): okno nasycen (`imbueRows`) jak strona "Nasycenia" u kowala -
  Wampiryzm (kradziez zycia), Pustka (many), Cios (krytyk), zywioly i ochrony. Kowal zostawia swoja strone nasycen:
  `GameSession.imbue` / `clearImbuement` dzialaja przy kowalu albo zaklinaczu (`imbuerNearby`, `Npc.imbues()`);
  Kuznia, ulepszenia i zaklecia zostaja tylko u kowala. `/idz zaklinacz|nasycenia`, opiekun: `/idz pupil|opiekun`.

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

## Kuznia Wyniesienia, tiery i nasycenia (Z logika, S serwer, C okno kowala) - 2026-11-06

Wzorowane na Tibii (zrodla Canary: `config.lua.dist`, `forge` w `game.cpp`/`player.cpp`, `item_tiers.lua`,
`imbuements.xml`). Wartosci oznaczone **(niezweryfikowane)** nie sa potwierdzone w Tibii albo sa nasza skala.

### Klasa i tier przedmiotu (`item/Forge`)
- Kuznia obejmuje pojedyncze bronie (pole broni) oraz helmy, zbroje, spodnie i buty. Klasa wynika z rzadkosci
  (`Rarity.tier` z wartosci): niepospolity 1, rzadki 2, epicki 3, legendarny 4; przedmiot tylko z bossow +1
  (najwyzej 4). Zwykle przedmioty, tarcze, pierscienie i amulety nie maja klasy.
- Najwyzszy tier wg klasy jak w Tibii: 1 / 2 / 3 / 10 (`MAX_TIER_BY_CLASS`).
- Tier jest polem `ItemStack.tier` (przedmiot z tierem jest pojedynczy, nie laczy sie w stosy, nie sprzedaje kupcowi;
  `isPlain()`). Nazwa "Miecz +2 (T3)", znaczek "T3" na polach ekwipunku i plecaka, opis w plecaku (klasa, tier,
  efekt z szansa) - `ItemCompare.forgeLine` i wiersze opisu w `Hud`.
- Zapis postaci v8: stos "idxilosc[+ulepszenie][*tier][#nasycenia]" (`ItemStack.encode/parse`), klucz
  `forge=pyl,limit`. Zapis v7 i starsze czyta sie bez zmian (tier 0, bez nasycen, pyl 0, limit 100). Tier z pliku
  jest ograniczany do limitu klasy. `Protocol.VERSION` = 4 (nowe znaczniki MO, wiadomosc FIEND).
- Rynek: oferta pamieta ulepszenie i tier (linie "L2" w `market.txt`, stare "L" dalej sie czytaja); wystawic mozna
  jeden przedmiot z tierem albo ulepszeniem, ale nie nasycony.

### Pyl, odlamki, rdzenie
- Pyl (`PlayerCharacter.forgeDust`) za zabicie potwora pod wpływem stopnia s albo diabolicznego (s = 15): s..3s
  (`Forge.rollDust`); w druzynie kazdy czlonek dostaje wlasny rzut (`ServerWorld.shareKill`). Ponad limit przepada
  z komunikatem.
- Limit pylu 100 -> 225, podniesienie o 1 kosztuje (limit - 75) pylu (Canary).
- Wymiana u kowala: 60 pylu -> 3 odlamki (`forge_sliver`), 50 odlamkow -> 1 wzniosly rdzen (`exalted_core`).
  Diaboliczny potwor zostawia dodatkowo 3..7 odlamkow w ciele.

### Fuzja i transfer (`game/ForgeService`, okno kowala "Kuźnia Wyniesienia")
- Fuzja dwoch takich samych przedmiotow o tym samym tierze z plecaka: 100 pylu + zloto, szansa 50% (+15% za
  rdzen "na szanse"). Porazka: pierwszy przedmiot zostaje, drugi traci tier (na tierze 0 przepada); rdzen "na
  ochrone" zmniejsza szanse utraty do 50%. Porazka zawsze zuzywa pyl, zloto i rdzenie.
- Premie sukcesu (rzut 0..10000, progi Canary 7400/9000/9500/9525/9550/9950/9975): brak, pyl wraca, rdzenie wracaja,
  zloto wraca, drugi przedmiot ocalal z tierem -1, ocalal bez zmian, zyskal tier, pierwszy zyskal dwa tiery (tylko
  ponizej limitu klasy).
- Transfer: dawca z tierem >= 2 przepada, biorca z tierem 0 tej samej klasy i tego samego rodzaju (pole) dostaje
  tier dawcy - 1. Koszt: 100 pylu, rdzenie wg tabeli Canary (klasa, tier wyniku), zloto = cena fuzji tieru dawcy.
  W Tibii transfer dziala tez miedzy roznymi przedmiotami tej samej klasy i typu - u nas warunek "to samo pole".
- **Zloto (wzor, niezweryfikowana skala):** `max(1000, wartosc_przedmiotu x 0,5 x R(klasa, tier))`, gdzie
  `R = cena_Canary(klasa, tier) / cena_Canary(klasa, 1)` z `item_tiers.lua`. Tibia liczy stale ceny dla klasy;
  u nas ceny przedmiotow sa duzo nizsze, wiec bierzemy proporcje miedzy tierami Canary i polowe wartosci przedmiotu
  jako cene tieru 1.
- Platnosc jak w Tibii: najpierw monety z plecaka, reszta z konta. Wszystko atomowe (kopia plecaka, potem
  `commitFrom`).
- Akcje: `npc:forge:fuse:poleA:poleB:rdzenSzansy:rdzenOchrony`, `npc:forge:transfer:dawca:biorca`,
  `npc:forge:slivers`, `npc:forge:core`, `npc:forge:limit` - offline i online (ACT na serwerze), tylko przy kowalu.

### Kowal tylko u kowali
- `Npc.isSmith()`: kupiec z kluczem `*_smith` (6 kowali: Varden, Frostholm, Selvaran, Qasirah, Nerys, Valmir).
  Wiersz "Kowal: ulepszenia, Kuźnia, nasycenia" pojawia sie tylko u nich (wczesniej u kazdego kupca), a ulepszenia
  i zaklecia kowala (`upgradeEquipped`, `buyEnchantment`) tez wymagaja kowala (`GameSession.smithNearby`).
- Okno kowala ma trzy strony: ulepszenia +1..+5 i zaklecia (jak dotad), Kuznia (fuzja z przelacznikami rdzeni,
  wymiany, limit pylu, transfer w dwoch krokach: dawca, potem biorca) i nasycenia. Ostatni wynik ("Kuźnia: ...")
  z dziennika jest wierszem "Wynik".

### Efekty tierow (`ForgeEffects`, wzory Canary `a*t^2 + b*t + c`)
- Natarcie (bron): 0,05/0,4/0,05 - szansa na +60% obrazen ciosu (efekt "FATAL", napis "natarcie"); rzut na kazdy
  cel, bez PvP **(niezweryfikowane: Tibia liczy go raz na atak)**.
- Unik (zbroja): 0,0307576/0,440697/0,026 - cios potwora chybia (zdarzenie BLOCKED "DODGE", napis "unik"); tylko
  przeciw potworom.
- Rozped (helm): 0,05/1,9/0,05 - co 2 s walki skraca odnowienia czarow o 2 s (`Cooldowns.cutAll`).
- Transcendencja (spodnie): 0,0127/0,1070/0,0073 - co 2 s, gdy w ostatnich 2 s byl atak gracza: awatar na 7 s
  (100% krytyka, +15% obrazen krytycznych, -15% obrazen przyjetych), jak awatar 3. stopnia w Tibii.
- Wzmocnienie (buty): 0,4/1,7/0,4 - mnozy szanse pozostalych efektow x(1 + a/100).
- Nowe buty klas 2-4 (dla Wzmocnienia): obsidian_boots, coral_boots, glacial_boots, shadow_boots, abyssal_treads
  (tylko z Morgatha).

### Potwory pod wpływem i diaboliczne (`MonsterType.forgeStack`, `MonsterManager`)
- Zastepuja dawne elity (`isElite()` znaczy teraz "pod wpływem albo diaboliczny" - bestiariusz i osiagniecia
  licza je jak dotad). Mnozniki Canary dla stopnia s (1..5, diaboliczny 15): zdrowie `1 + (15s + 35)/100`, obrazenia
  `1,35 + (s - 1) x 0,1`, pancerz `1 + 0,1s`, doswiadczenie `(s + 10)/10` **(niezweryfikowane: Canary liczy to na
  liczbach calkowitych)**. Lup bez zmian (Canary nie ma mnoznika lupu).
- Pod wpływem: los z numeru potwora (jak dawne elity, bez zuzywania generatora) 3% spawnow **(nasza skala: ok. 220
  z 7352 potworow)**, limit 300 naraz (Canary). Stopien wg rozkladu Canary `normal_random(1,5)`: 4,6% / 25,3% /
  40,1% / 25,3% / 4,6%.
- Diaboliczne: 4 naraz (Canary `forgeFiendishLimit`, nieskalowane), kazdy przez godzine, potem wraca do zwyklego
  i zastepuje go inny; po zabiciu nowy po 270 s. Tylko w swiatach z >= 50 spawnami (bez testow i aren).
  `Monster.changeType` zachowuje proporcje zdrowia.
- Siec: znacznik MO "I1".."I5" / "F" / "-"; klient podmienia odmiane przy zmianie. Serwer co 5 s wysyla
  "FIEND x,y,z ..." (tylko po zmianie); minimapa rysuje fioletowe znaczniki diabolicznych (offline z wlasnych
  potworow). Na widoku: fioletowy odcien i kropki stopnia przy pasku zdrowia, diaboliczny wiekszy z korona.

### Nasycenia (`item/Imbuement`, `item/Imbuements`)
- 14 rodzajow, trzy stopnie (Podstawowe / Zlozone / Potezne = Basic / Intricate / Powerful), wartosci Tibii:
  Wampiryzm 5/10/25% kradziezy zycia, Pustka 3/5/8% many, Cios +10% szansy krytyka i 15/25/50% obrazen
  krytycznych, Zar/Jad/Mroz/Elektryzacja/Zniwo 10/25/50% obrazen fizycznych broni jako zywiol, ochrony Smocza
  skora (ogien), Wezowa skora (ziemia), Luska quary (lod), Tkanina chmur (energia), Obecnosc demona (swiete)
  3/8/15%, Calun licza (smierc) 2/5/10%.
- Na co: kradziez i krytyk na bron (Wampiryzm tez zbroja, Pustka tez helm), zywioly na bron fizyczna, ochrony na
  zbroje i tarcze. Na przedmiocie jedno nasycenie z grupy (ochrony: jedna na zywiol).
- Miejsca: bron niepospolita 1, rzadka 2, epicka i legendarna 3; zbroja i helm 1 (do rzadkich) albo 2; tarcza 1;
  reszta 0 **(niezweryfikowane: w Tibii zalezy od konkretnego przedmiotu)**.
- Czas 20 h walki: plynie tylko na zalozonych przedmiotach, w walce (60 s od ataku albo ciosu) i poza strefa
  ochronna (`Equipment.tickImbuements`).
- Koszt: skladniki z naszego lupu (stopien n = n pierwszych pozycji, liczby jak w Tibii) + zloto 1250 / 7500 /
  50 000 (Canary 5000 / 30 000 / 200 000 x 0,25 **(niezweryfikowana skala)**), usuniecie 3750. Nasycenie zawsze
  sie udaje **(Canary ma procent powodzenia - pominiety)**.
- Haki: `Leech` (suma LIFE_LEECH / MANA_LEECH), `CriticalHit` (Cios: +10% szansy za kazdy przedmiot, obrazenia z
  sumy), `Equipment.protection` (ochrony), `CombatSystem.mitigateWeaponHit` (zamiana czesci ciosu na zywiol).
- Akcje: `npc:imbue:POLE:RODZAJ:STOPIEN`, `npc:unimbue:POLE:indeks`.

## Mapa, kolizje, pietra (Z)

- Warstwy: podloze, dekoracje, obiekty (przeszkody), sciany, swiatlo, przejscia.
- Pietra -7..+5 o wspolnym ukladzie wspolrzednych, mapy z plikow tekstowych + legenda (-6 i -7 tylko pod kopalniami
  krasnoludow).
- Kolizje z cache (`boolean[]`), ruch w 8 kierunkach. Skos nie scina rogu muru ani wody, ale miedzy drzewami,
  krzakami, palmami, kaktusami i glazami mozna sie przecisnac po skosie jak w Tibii (`TileType.squeezable`,
  `WorldMap.diagonalCornersOpen`; gracz i potwory).
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
8 kierunkow (2026-11-12, decyzja wlasciciela: "jak w Tibii, skos x3"): gracz i potwory chodza w 8 kierunkach, krok po skosie
trwa `MovementSystem.DIAGONAL_STEP_FACTOR` = 3 zwykle kroki, rogi wg `WorldMap.diagonalCornersOpen`; ta sama logika
(`GameSession.update`) dziala offline i na serwerze (serwer przyjmuje wszystkie 8 nazw `Direction`).
Joystick (`StickFilter`, wariant "Joystick (8 kierunkow)", staly albo pod kciukiem wg ustawien): rusza przy wychyleniu 45%,
staje ponizej 30% (martwa strefa jak dotad), 8 rownych sektorow po 45 stopni bez zadnego opoznienia, histereza kata 10 stopni
za granica sektora (drgajacy kciuk nie przeskakuje). Krzyzyk (`DpadInput`) i przytrzymanie na mapie (`HoldToWalk`) tez daja 8 kierunkow
od razu; "Plywajacy, 4 kierunki" (`FourWayStick`, skos po chwili trzymania w klinie) zostaje do wyboru w ustawieniach.
Klawiatura (`KeyboardDirection`): dwa klawisze naraz (strzalki / WASD) = skos, klawisze przeciwne sie znosza; blok numeryczny
1/3/7/9 = skosy (Q/E/Z/C maja inne funkcje: cel, uzycie, mikstura, dziennik). Domyslny wariant (krzyzyk) byl i jest 8-kierunkowy.
Marsz po dotknieciu mapy (`Pathfinder.path`, A*): krok prosty 100, skos 350 (trwa jak 3 proste + zapas), drobna kara
za zakret - trasa prosta / w ksztalcie L, skos tylko przy przecisnieciu sie miedzy przeszkodami (marsz po dotknieciu mapy zostaje 4-kierunkowy).
Sterowanie dotykiem (etap 17, `GameScreen` + `Hud.floatJoystick`): dotkniecie mapy rozstrzyga sie przy puszczeniu
palca. Krotkie dotkniecie = pole (cel, marsz, rozmowa, lup), przytrzymanie 450 ms = podglad pola
(`GameSession.lookAt`: potwor z poziomem i zdrowiem, NPC z rola, cialo, przedmioty, przejscia, strefa ochronna,
odleglosc; na PC prawy przycisk myszy), przeciagniecie po lewej czesci widoku gry = joystick pod kciukiem
(podstawa tam, gdzie palec dotknal; po puszczeniu wraca w rog). Ustawienia: joystick pod kciukiem / tylko w rogu,
rozmiar 80-150%. Na Androidzie ekran nie gasnie w trakcie gry (`useWakelock`).

## Uzycie dotknieciem, drzwi, dachy (Z logika, S serwer, C UI)

Na telefonie nie ma przycisku "Uzyj" - dotkniecie obiektu na mapie dziala jak "Use" w Tibii
(`GameSession.usableAt` / `useAt`, akcja `open:x:y:z`, klient `GameScreen.tapTile`; ta sama sciezka dla klikniecia na PC).
Obiekt obok postaci (albo pod nia) - uzycie od razu; dalej - marsz na pole obok i uzycie po dojsciu.
- Uzywalne: skrzynie, schowki, drzwi i okna, dzwignie, pochodnie na murach, drabiny i miejsca na line (wejscie pietro
  wyzej, lina potrzebna), krzaki z owocami i ziolami, drogowskazy (kraina i droga do najblizszego miasta), studnie,
  lozka, pulpity. Schody i dziury jak dotad - wejscie na pole. Przedmioty na ziemi: podglad/podnoszenie bez zmian.
- Kratki sciekowe w miastach (0.240, `TransitionType.GRATE`): jak w Tibii schodzi sie do kanalu dotknieciem kratki,
  nie wejsciem na nia (wejscie na kratke nic nie robi, mozna po niej chodzic). Uzycie idzie ta sama droga co drabina:
  `GameSession.usableAt` / `useWorldObject` -> `MovementSystem.use`. W `maps/world/transitions.csv` 53 wejscia STAIRS
  na polach kratek (z=0) zmienione na GRATE; generator (`tools/mapgen/generate_continent.py`) zapisuje je juz jako
  GRATE. Opis po spojrzeniu: "kratka sciekowa (dotknij, by zejsc do kanalu)".
- Drzwi jak w Tibii: wejscie w zamkniete drzwi tylko zatrzymuje (`MoveResult.DOOR_CLOSED`, podpowiedz
  "Dotknij drzwi, aby je otworzyć." najwyzej raz na 30 s); otwiera i zamyka je dotkniecie (zamkniecie nie, gdy ktos
  stoi w drzwiach). Marsz po dotknieciu mapy omija zamkniete drzwi, a gdy trasa przez nie prowadzi - staje przed
  nimi. Zasady domow (`HouseAccess`) bez zmian. Potwory przez zamkniete drzwi nie przechodza.
- Online: stan drzwi i okien jest wspolny - serwer rozsyla zmiany komunikatem `DOOR x y z 1|0` (`Openings.setListener`)
  i wysyla otwarte drzwi nowo zalogowanemu; klient nie przewiduje juz drzwi lokalnie.
- Dachy (`client/render/Roofs`): pod dachem nie widac zadnych dachow (jak dotad); dodatkowo dach (i wyzsze pietra)
  jednego budynku znika, gdy postac stoi najwyzej 2 pola od jego drzwi albo tuz przy jego murze (1 pole) - widac
  mury i drzwi do dotkniecia. Dach znika i wraca plynnie w 250 ms (`Roofs.stepFade`; przy -Dpixel.hour od razu).

## Krzaki z owocami i jedzenie (Z logika, C grafika)

- Co piaty (srednio) zwykly krzak `bush` na powierzchni nosi owoce (`gathering/BerryBushes`, wybor wg pozycji pola -
  bez zmian mapy, ten sam u klienta i na serwerze): 2/3 jagody (`blueberry`), 1/3 maliny (`raspberry`).
- Dotkniecie krzaka obok: 3 owoce, po 3 zbiorach krzak jest pusty i odrasta po godzinie (komunikat "odrosną za N min").
  Stan liczy sie osobno dla kazdej postaci (jak ziola). Przycisk UZYJ / klawisz E tez zrywa owoce z krzaka obok.
- Zwykle krzaki bez owocow daja dalej ziela lecznicze (dotkniecie albo UZYJ, odrost 10 min).
- Grafika: warianty krzakow z paczki OTSP - z owocami 1539/1542/1548 (jagody), 1540/1546 (maliny); obrany krzak to
  ten sam krzak bez owocow (1538/1541/1544). Zwykle krzaki nie dostaja juz wariantow z owocami.
- Sytosc (consumables.csv, sekundy; maks. 1200 s jak w Tibii). Porownanie z Tibia (TibiaWiki / food.lua serwerow OT,
  wartosci podane z pamieci, bez sprawdzenia online w tej sesji):

  | Przedmiot | Pixel | Tibia |
  |---|---|---|
  | Jagody / maliny (sztuka) | 12 s | blueberry / raspberry 12 s |
  | Chleb | 120 s (bylo 300) | bread 120 s |
  | Ryba | 144 s (bylo 240) | fish 144 s |
  | Mieso | 180 s | meat 180 s |
  | Bagienny wegorz, karp z oazy | 240 s (bylo 360) | rzadkie ryby, miedzy miesem a szynka |
  | Lodowy dorsz | 300 s (bylo 420) | j.w. |
  | (szynka - brak w grze) | - | ham 360 s |

  Garsc owocow z jednego zbioru (3 x 12 s) syci krocej niz kawalek miesa.

## Swiatlo, dzien i noc (C)

- `WorldClock`: doba gry = 6 h czasu rzeczywistego (`REAL_DAY_MS`): ok. 3 h jasno i 3 h ciemno, godzina gry = 15 min
  (`GAME_HOUR_MS`; do 0.212 doba trwala 2 h). Fazy swit/dzien/zmierzch/noc, plynne przejscia, noc min. 38% jasnosci.
  Czynsz, zaproszenia, odliczania i inne timery licza czas rzeczywisty i nie zaleza od dlugosci doby.
- Podziemia: jasnosc maleje z glebokoscia, min. 16%; postac zawsze ma poswiate 2,5 kafelka.
- Pochodnia (kupiona w sklepie ogolnym albo z lupu goblinow, bandytow, szkieletow, stonog) - w plecaku "Zaloz" kladzie ja
  w pole na prawo od butow (EquipSlot.LIGHT). Dotkniecie pola (albo przycisk POCH., klawisz T) zapala i gasi; promien 5.
  Pali sie 20 min czasu palenia (zgaszona nie traci paliwa), w polu widac pozostaly czas ("18m"); wypalona znika
  z komunikatem "Pochodnia się wypaliła.". Zdjeta pochodnia gasnie. Zapis v6: stan w `timers`; starsze zapisy - zapalona
  pochodnia z plecaka trafia do pola, pochodnia z pola amunicji tez.
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
- Telefon: luk 5 czarow (pola 1, 2, 6, 0, 3), wewnetrzny luk 3 pol (4, 7, 5 - stare mikstury na koncach, nowe pole 7
  w srodku) i pole 8 w rogu ekranu (srodek obu lukow); kazde przyjmie czar albo miksture (przytrzymanie = wybor).
- Paski akcji jak w Tibii 12+ (`settings/SpellBar`, `SpellSystem.useItemAt/castCombo`):
  - Runa ma tryb: `item:klucz` na cel (domyslnie), `item:klucz:self` na siebie, `item:klucz:aim` celownik. Tryb
    w menu pola (wiersze "Uzyj na cel / na siebie / z celownikiem", wybrany zolty), znaczek w lewym gornym rogu pola.
  - Celownik (`GameScreen.startAim`): nastepne dotkniecie pola mapy wysyla `item:klucz:at:x:y:z`; z plecaka (UZYJ na
    runie = "Use with...") `slot_at:pole:x:y:z`. Anulowanie: Esc, Wstecz, prawy przycisk, to samo pole paska.
    Podglad: pola w zasiegu i wzroku runy, kursor zielony/czerwony, obszar runy obszarowej (odleglosc Czebyszewa jak
    w obrazeniach).
  - Zasady (jak "Use with" w Tibii): zasieg runy (7), to samo pietro, linia wzroku (poza wlasnym polem); runa na
    stworzenie wymaga stworzenia na polu (`ONLY_CREATURES`), runa obszarowa trafia tez puste pole, runa lecznicza
    i odtrutka tylko na siebie (`ONLY_SELF`). Celownik nie zmienia celu walki. PvP: gracz na polu jest trafiany tylko,
    gdy jest wybranym celem-graczem.
  - Combo `combo:a+b+c` (do 4 czarow): kazde nacisniecie rzuca pierwszy czar, ktory przechodzi pelne sprawdzenie
    (mana, odnowienie czaru i grupy, drugie grupy, cel); nic gotowego = komunikat pierwszego czaru bez kosztu.
    Tibia nie ma makr - jedno nacisniecie to najwyzej jeden czar. Edycja: rodzaj "Combo" w menu pola.
  - Zapis: klucz `bar` w zapisie postaci v9 (`PlayerCharacter.actionBar`); online akcja `bar:<uklad>` do serwera,
    pasek wraca w SELF. Dawny uklad z Preferences `pixel-bar` przenoszony raz do postaci (klucz usuwany).

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
zapis atomowy (tmp + fsync + kopia .bak), odczyt z kopii przy uszkodzeniu. v8: tier i nasycenia stosow oraz pyl
Kuzni (patrz "Kuznia Wyniesienia"). v9: uklad paska akcji (`bar`, patrz "Pasek akcji"). Autozapis co 60 s,
przy pauzie, utracie fokusu i zamknieciu.

## Potwory i AI (Z logika, C grafika)

- `MonsterRegistry` (monsters.csv + loot.csv), `SpawnTable` (spawns.csv), `MonsterManager`.
- Stany: IDLE (wedrowka w promieniu spawnu, regeneracja 5%), CHASE, FLEE,
  RETURN (smycz: promien spawnu + `LEASH` 24 lub inne pietro; po powrocie pelne HP).
- Poscig jak w Tibii (0.217): potwor zauwaza gracza w zasiegu wzroku (`sightRange` 7..9, przed soba pelny, za
  plecami mniej) bez linii wzroku - takze zza drzew i rogow; gonionego trzyma do zasiegu + 4 i jeszcze
  `MEMORY_MS` (5 s) idzie tam, gdzie go ostatnio widzial, potem wraca. Droga: `Pathfinder.nextStepTimed` (A* po
  czasie marszu, skos = 3 proste jak u gracza, przecisniecie po skosie miedzy drzewami/krzakami/glazami wg
  `WorldMap.diagonalCornersOpen`), najwyzej `PATH_NODES` 700 pol; na krok symulacji najwyzej
  `MAX_PATH_SEARCHES_PER_UPDATE` 40 wyszukiwan, bez drogi potwor czeka ~0,8-1,2 s. Krok po skosie trwa 3x dluzej.
  Linia wzroku liczy sie tylko do atakow z dystansu.
- Zachowania: MELEE, TANK, SKIRMISHER (ucieka ponizej progu HP; osaczony walczy do konca),
  RANGED / MAGIC / HEALER z zasiegiem > 1 trzymaja dystans rowny zasiegowi ataku (za blisko - odchodza, za daleko
  albo bez linii strzalu - podchodza), strzelaja z linii wzroku; HEALER (leczy najslabszego
  sojusznika < 70% w promieniu 4), boss (leczenie raz ponizej 30%, atak 30% szybszy ponizej 50%).
- 8 kierunkow (2026-11-12): wedrowka (IDLE) losuje 8 kierunkow (skos x3 czasu kroku jak u gracza), ucieczka i trzymanie dystansu
  wybieraja kierunek z 8 wg przyrostu odleglosci na jednostke czasu (skos tylko, gdy prosto nie da sie uciec), poscig to A* z skosami.
  Zwrot (`Monster.facing`) ma 8 wartosci; grafika: `CreatureFrames.pick` bierze `klucz_ne|nw|se|sw` (+ `_walk1/_walk2`), gdy
  sa komplety, inaczej jak w Tibii skos patrzy w prawo (NE, SE) albo w lewo (NW, SW); konwencja plikow w docs/GRAPHICS.md.
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
- Tryb walki (`combat/FightMode`, przycisk w HUD i klawisz V, zamiast dawnej "Tarczy"): ofensywny (miecz) ->
  zbalansowany (miecz i tarcza, domyslny) -> defensywny (tarcza). Wartosci z TFS (`Player::getAttackFactor` 1,0 / 1,2 /
  2,0 jako dzielnik ataku, `Player::getDefenseFactor` 0,5 / 0,75 / 1,0 przy ataku w ostatnim odstepie, inaczej 1,0),
  przeliczone wzgledem zbalansowanego (nasze formuly byly strojone dla zwyklej walki): obrazenia broni (bez premii
  poziom/5, bez rozdzek i czarow) x1,2 / x1,0 / x0,6; obrona (tarcza, bron; nie pancerz) x0,67 / x1,0 / x1,33 gdy
  postac atakowala bronia w ostatnim odstepie, inaczej x1,33 w kazdym trybie; defensywny z tarcza dodatkowo blokuje
  tarcza jak dawna "Tarcza". Zywioly bez zmian (jak w TFS). Zapis w flagach postaci ("O" / brak / "D" - stary zapis
  "D" = defensywny); akcja `fightmode:NAZWA` (klient wysyla tryb docelowy), `fightmode` i stare `defensive` przelaczaja.

### Walka jak w Tibii: odpornosci, ochrony, krytyki, kradziez (Z logika, S serwer, C liczby i okno postaci)

- Rodzaje obrazen (`DamageType`): fizyczne, ogien, lod, ziemia, energia, swiete, smierc oraz z Tibii utoniecie
  (DROWN), wyssanie zycia (LIFE_DRAIN, omija pancerz jak w Canary) i wyssanie many (MANA_DRAIN - zabiera mane zamiast
  zycia, liczba niebieska, efekt zdarzenia "MANA"). `DamageType.label()` - polskie nazwy (bestiariusz, opisy).
- Odpornosci potworow (monsters.csv, kolumna `resistances`) przepisane z odpowiednikow w Tibii (pliki potworow Canary,
  `elements` / `immunities`). Nowa kolumna `klasa(Tibia)` (Mammal, Undead, Demon, Dragon, Elemental, Human, Magical, ...).
  Wzorce klas (test `TibiaCombatTest`): nieumarli odporni w 100% na smierc, utoniecie i wyssanie zycia, slabi na swiete;
  zywiolaki odporne na wlasny zywiol; stwory lodu NIE sa automatycznie slabe na ogien (Ice Golem 100%, Ice Witch 50%,
  Winter Wolf +10%); slabosci najwyzej -30%. Odchylenia od Tibii dla balansu (symulacje endgame, gdzie magowie
  walcza jedna rozdzka): lava_golem HP 8000, ice_dragon ziemia 5 (Tibia 50 bylaby odpornoscia na jedyna rozdzke
  druida tego poziomu), khazuul atak 950-2700, abyss_lord HP 26000 i fizyczne 25, bone_dragon lod 20, abyssal_demon
  energia 40, sand_wraith fizyczne 40.
- Ataki dodatkowe potworow (kolumna `atak dodatkowy(TYP:min-max:%)`, `monster/ExtraAttack`): przy kazdym ataku z
  szansa % potwor zamiast zwyklego ciosu uzywa ataku tego typu (tani zamiennik listy atakow Tibii - wyssanie zycia
  szkieletow i upiorow, wyssanie many kaplanow pustki itd.). `MonsterType.rollStrike` - jeden rzut; pod wpływem i diaboliczne x1,35..x1,75 (Kuznia).
  Pelne listy atakow zywiolowych z Tibii (fale, kule, kilka czarow) nie sa przeniesione.
- Ochrona gracza: kolumna `premie` w items.csv (`ItemBonus`): `FIRE:8,ICE:-8,...` w procentach, wartosci z
  najblizszych przedmiotow Tibii (Magma Coat, Glacier Robe, Helmet of the Deep - utoniecie 100%, Depth Ocrea - wyssanie
  many 15%, Fur Armor). `Equipment.protection(typ)` sumuje zalozone przedmioty (-100..100). W `mitigateOnPlayer`
  (potwory i gracze, takze czary PvP) obrazenia: `d -= round(d * ochrona / 100)` przed pancerzem, jak absorb w Canary;
  ujemna ochrona zwieksza obrazenia.
- Cios krytyczny (`CriticalHit`, jak obecna Tibia): kazdy ma 5% szansy i +10% obrazen; przedmioty `CRIT:x`
  (szansa) i `CRITDMG:x` (dodatkowe obrazenia, bronie Soul War / Sanguine); zaklecie kowala "krytyk" dziala jak
  imbuement Powerful Strike: +10% szansy, +50% obrazen. Wszystko sie sumuje; obrazenia = surowe x (1 + suma/100).
  Jeden rzut na cios bronia lub na czar (czar obszarowy: jeden rzut dla wszystkich celow). Dziala w PvP
  (`PvpRules.attack/spellHit/areaHit`, przed czynnikiem PvP). Zdarzenie trafienia z efektem "CRIT" - klient rysuje
  wieksza pomaranczowa liczbe z "!" i napis "krytyk"; online EV z 10 polami ("skad" = cel, stary klient ignoruje).
- Kradziez zycia/many (`Leech`, wzor Canary): `zadane * % * (0,1n + 0,9) / n` (n = liczba trafionych celow; 1 cel =
  pelny %, czar w 2 cele po 55% itd.), zaokraglone. Przedmioty `LIFELEECH:x`, `MANALEECH:x`; zaklecia kowala zycie 6% i
  mana 3% (wczesniej wlasne procenty) przechodza przez ten sam wzor. Przyszle imbuementy Vampirism/Void dodadza swoje
  procenty w `Leech.lifePercent/manaPercent` (jedno miejsce sumowania).
- Okno postaci (komputer: okno "Umiejetnosci", telefon: Ekwipunek) pokazuje "Krytyk", "Kradziez" i "Ochrona"
  (`SkillSheet.combatRows`); opis przedmiotu (`ItemCompare.stats`) dopisuje ochrony, krytyk i kradziez.
- Zapis: bez zmian (premie wynikaja z przedmiotow), `CharacterCodec` zostaje v7.

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

Skrzynie lupu z mapy (2026-11-03): `maps/world/chests.csv` (x;y;z;zloto;przedmiot;ilosc) pisze generator
(`tools/mapgen/wilds.py`) w ostatnich komorach lochow dziczy; `MapLoader` -> `WorldMap.lootChests()`,
`TreasureChests.generate` dokleja je z identyfikatorem `d<x>_<y>_<z>` (`LOOT_PREFIX`), raz na postac jak inne skrzynie.

Nagrody wg trudnosci (0.249, `treasure/ChestRewards`, prosba Darka): zawartosc z mapy jest minimum. Trudnosc
(poziom) = najwyzsza z: poziomu drzwi poziomu na drodze do skrzyni (`TreasureChests.explore` - przeszukiwanie
progami drzwi), srodka przedzialu poziomow regionu, poziomu najsilniejszych potworow w 12 polach (z bossem) i
8 x glebokosc. Rodzaje:
- **skrzynia zadania** (za drzwiami poziomu, przy bossie, w pokojach zadan, nagrody zadan z oltarzami, przedmiot
  wpisany na mapie): zloto `100 x T + T^2/2` (T = trudnosc; np. 40 -> 4 800 gp, 75 -> 10 300 gp, 200 -> 40 000 gp),
  jeden wybrany dobry przedmiot dla profesji otwierajacego (wpisany na mapie, jesli pasuje, inaczej z najwyzszej
  polki do poziomu T, bez przedmiotow tylko z bossow) i 5 + T/20 mikstur (do 20);
- **skrzynia lupu** w slepej komorze: `40 + 12 x T` gp (np. 30 -> 400, 125 -> 1 540), 2 + T/25 mikstur dla
  profesji, co trzecia - slabszy przedmiot (do 60% T); losowe skrzynie skarbow ("c") podobnie, przedmiot co druga.
Komunikat podaje wszystko w dwoch krotkich wierszach (mieszcza sie na telefonie), np. "Nagroda za zadanie
(poziom ~75): 10 300 gp!" i "Otrzymujesz: Hełm Pana Twierdzy, 8x Silna mikstura zdrowia.". Losowanie zalezy tylko od skrzyni i profesji (klient i serwer licza to samo). Test: `ChestRewardsTest`.

## Lowiska w dziczy (generator, 2026-11-03)

`tools/mapgen/wilds.py` (wlasne ziarna, po zadaniach w `generate_continent.build`): 66 nowych wejsc do podziemi
(jaskinia w skalnym wystepie, kopalnia w drewnianej ramie z placem, zapadlisko - dziura wsrod ruin z kanalem pod
spodem i drabina) rozstawionych tak, by kazdy punkt drogi mial wejscie w zasiegu ~45 pol; 1-3 pietra po 5-8 komor,
potwory wg poziomu krainy i odleglosci od miast, ciala poleglych, skrzynie lupu. `surface_spawns`: dodatkowe spawny
niskiego (1-15) i sredniego (16-40) poziomu w lasach i na lakach obok drog (nie na nich), pasujace do regionu.
Zadania w piec postaci maja dlugie podejscia: Grobowiec Pieciu Profesji ok. 1500 pol (3 pietra 150 x 96),
wyprawy na bossa ok. 450 pol (17 komor 150 x 82).

Od 0.248 (`wilds.floors_for/layout/themes`): kazdy loch ma 2-7 pieter (glebiej przy wyzszym poziomie okolicy),
pietro 56-74 x 40-52 z pnia 6-9 komor, 2-4 slepymi komorami (skrzynia, spawn albo ciala) i petlami; style pieter
mieszane (`features.CAVE_STYLES` + `crystal`, `flooded`, `ruin`; `carve_rooms` wybiera krypte albo jaskinie,
w `WATER_STYLES` lawa staje sie woda), `ensure_lights` doklada swiatla (DeepDungeonTest). Poziom spawnow rosnie
z glebokoscia; `route_levels` dalej obniza podejscia (RouteLevelsTest). Recznie budowane miejsca pod ziemia
(twierdza orkow, Elandor, Durnkar i kopalnie, wulkan) sa przypiete (`UNDER_PIN`, `UNDER_RESERVED`
w `generate_continent`, `structures.stamp` czyta `ctx['under_pin']`), wiec nowe lochy ich nie przesuwaja.

## Wyspa Bursztynowa, Oboz Lowczyn, Cyklopolis (generator `tools/mapgen/isles.py`, 0.248)

- `grow_island`: wyspa najsilniejszych potworow (ziarno `ISLE_SEED` 790,880) powiekszona na poludnie o ~3900 pol
  ladu. Bursztynowa Skala (srodek ~784,928): wieza u stop (drzwi 784,952), schody na +1..+3 (ruiny, urwiska),
  nisza na zachodzie ze schodami w dol do Bursztynowych Czelusci -1..-6 (grota morska, zalane, krypta, krysztaly,
  grobowce w piasku, rdzen). Na kazdym pietrze 36-102 spawny silnych stad (poziom >= 260, w dol rosnie), skrzynie
  w slepych komorach. Prom zostaje (przystan 808,842); kolejnosc nazw promow liczy stary rozmiar wyspy.
- `amazon_camp`: Oboz Lowczyn w dzungli na wschod od Varden (palisada, jedyne wejscie to drzwi poziomu 20 w bramie
  757,627; chaty, chata matrony), Piwnice Obozu Lowczyn -1..-2, w grocie Matrona i skrzynia zadania (763,621,-2;
  2500 gp + elficki luk). Potwory: Lowczyni Gaju 18, Guslarka Gaju 22, Tarczowniczka Gaju 24, Matrona Gaju 40
  (`grove_*`, OTSP). W obozie i piwnicach sa tylko lowczynie, a brama jest drzwiami poziomu: inaczej `route_levels`
  traktuje slabsze cele (zwierzyna, okoliczna dzungla) jako drogi przez oboz i obniza lowczynie.
- `cyclopolis`: ruiny miasta olbrzymow na gorzystej wyspie (562,883; prom 579,878 - przystan nazywa sie teraz
  Cyklopolis), Wielka Kuznia i kuznia kowala z paleniskami i kowadlami, cztery ruiny; Kuznie Cyklopolis -1..-6 pod
  miastem (dwa zejscia: 576,891 i 588,889). Jednookie olbrzymy (`oneeye_brute` 30, `oneeye_hauler` 36,
  `oneeye_smith` 42) i slabsza fauna jaskin, poziomy do 150.
- Test: `IslandsCampCyclopolisTest` (dojscie do kazdego pietra, liczby i poziomy spawnow, lochy dziczy).

## Drzwi poziomu i zadania z oltarzami profesji (Z logika, S serwer, C rysowanie)

- Drzwi poziomu (wielokrotnego uzytku): plik mapy `level_doors.csv` (`x;y;z;minimalny poziom`, pole z obiektem
  `door_level`, ktory jest zwyklymi drzwiami w murze ze zlota tabliczka). `MapLoader` wywoluje
  `WorldMap.addLevelDoor(p, lvl)`: drzwi + straznik poziomu na tym jednym polu (`requiredLevelAt`). API:
  `world.levelDoorAt(p)` (0 = nie drzwi poziomu), `world.levelDoors()`, `WorldMap.levelDoorSign(lvl)`
  ("\"Tylko dla poziomu 20+\"" - proste cudzyslowy, czcionka gry nie ma „ ”). Dotkniecie (`GameSession.useAt`) pokazuje napis; nizszy poziom - drzwi sie nie
  otwieraja, krok w drzwi konczy sie tym samym komunikatem. Od poziomu (jak w Tibii) dotkniecie otwiera drzwi i
  wprowadza postac w drzwi; drzwi sa otwarte, dopoki ktos w nich stoi, i zamykaja sie za postacia, gdy z nich zejdzie
  (`GameSession.closeLevelDoorBehind`, takze po schodach, teleporcie i smierci; nie, gdy w drzwiach stoi inny gracz).
  Kazda sesja pilnuje swoich drzwi, wiec dziala osobno dla kazdej postaci, na serwerze tez (komunikat `DOOR`).
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

## Twierdza orkow i osada elfow (generator `tools/mapgen/strongholds.py`, klocki `structures.py`)

- `structures.Plan`: wielopietrowy plan w ukladzie lokalnym - `box` (budynek z drzwiami), `platform`/`deck` (pomost,
  most linowy), `stairs`/`ladder` (schody i drabina z dziura), `level_door`, `corridor`, `cave` (komory i krypty),
  `spawn`, `trail` (sciezka przez las). `check()` sprawdza, ze kazdy spawn, przejscie, skrzynia i NPC sa osiagalne
  z wejsc, a pola `behind` tylko przez drzwi poziomu. `find_site` szuka miejsca (min. 70 pol od swiatyn, bez miast,
  drog i przejsc), `stamp` kladzie plan (podziemia najblizej, w prostokacie bez cudzych spawnow) i dopisuje
  przejscia, spawny, skrzynie, drzwi, strefy ochronne, NPC i regiony. Potwory z `structures.HAND_PLACED` nie trafiaja
  do losowych lowisk, a `route_levels` zamienia je tylko na slabszych z tej samej rodziny.
- Twierdza Krwawego Kla (na wschod od Varden, ok. 652..718 x 505..563): rozpadlina z mostami, mur, cztery wieze,
  brama z dwiema wiezami, wewnetrzna rozpadlina z dwoma mostami, cytadela, koszary, kuznia, zagroda wargow, oboz.
  Pietra +1 (chodnik na murach, wieze, brama, koszary), +2 (wieze, taras cytadeli, mosty linowe), +3 (wieza
  strazy); podziemia -1 (jaskinie, tajny tunel z wychodni za murem), -2 (krypty), -3 (najsilniejsze orki). Na -3
  drzwi poziomu 50 do skrzydla z sala Urgotha (boss 75) i skarbcem: skrzynia z Helmem Pana Twierdzy i 4000 gp,
  raz na postac. Orki od Orka (12) do Fanatyka orczego kultu (60); im glebiej, tym silniejsze i liczniejsze.
  Regiony "Twierdza Krwawego Kla" (0..+3) i "Podziemia Twierdzy Krwawego Kla" (-3..-1).
- Elandor, osada elfow (las na poludniowy zachod od Varden, ok. 352..412 x 507..557): polana z placem i fontanna,
  Wielkie Drzewo, swiatynia, depozyt, luczarz, zielarka, gospoda. Pietra +1 (pomosty na drzewach i mosty linowe),
  +2 (korona Wielkiego Drzewa, gniazdo zwiadowcow), +3 (szczyt drzewa ze Starszym), -1 (krypta, skarbiec, piwnica
  gospody), -2 (grota ze zrodlem). Cala osada (-2..+3) jest strefa ochronna; elfy-wygnancy (Elf, Zwiadowca elfow,
  Elfi arkanista) w lesie dookola i na czatowniach +1. Las w promieniu 30 pol ma limit poziomu 45
  (`ctx['area_caps']` -> `route_levels`).
- Test: `world/StrongholdsTest` (wszystkie pietra osiagalne pieszo z Varden, skarbiec dopiero od poziomu 50).

## Durnkar, krasnoludzkie miasto pod gorami, i kopalnie do -7 (generator `tools/mapgen/dwarves.py`, `dwarf_city.py`) - 2026-11-11

- Masyw gorski na polnoc od Varden (najglebsze pole gory ok. 483,299). Ukryte wejscie: waski kretty wawoz od
  zbocza (ok. 483,263) w glab gory, bez drogi i bez znaku, konczy sie grota ze schodami w dol (478,275,0).
  "Sztolnie do Durnkaru" (-1..-4) to krete tunele ze slepymi odnogami, bez potworow, do przedsionka miasta na -5.
- Durnkar (-5, ok. 472..560 x 299..354, swiatynia 496,308,-5): jaskinia z kanalem lawy i mostami, aleja,
  swiatynia z kaplanem, bank z depozytem (5 skrzynek) i zarzadca domow, sala tronowa krola, karczma (zaopatrzenie,
  robaki i wedka), kowal z kuznia (bron), platnerz, zaklinacz, runiarz (runy i mikstury), narzedziownik, kupiec,
  opiekun zwierzat, przewodnik, dwoch straznikow bram i wozkarz (podroze do innych miast). 12 domow na sprzedaz
  (systemem domow, `houses.csv`, pietro -5). Cale miasto i przedsionek sa strefa ochronna. NPC to krepi, brodaci
  krasnoludowie w strojach OTSP (`tools/art/otsp_import.py`, `DWARF_NPCS`).
- Miasto tylko do zamieszkania: `map.properties` `residence_only=Durnkar` -> `Town.starting() == false`. Nie ma go
  w wyborze miasta startowego (`StartingTowns.list/isValid`, takze na serwerze), ale kaplan pozwala tu zamieszkac
  (`homeTown`, odrodzenie w swiatyni na -5). `Town.MAX_TOWNS` = 8.
  "Sztolnie do Durnkaru" (-1..-3) to krete tunele ze slepymi odnogami, bez potworow, do Wysokich Hal na -4.
- Durnkar (0.250) to organiczne, trzypietrowe miasto (`dwarf_city.py`, ok. 462..566 x 290..355): Wysokie Hale (-4:
  swiatynia z kaplanem, bank z depozytem - 5 skrzynek, zarzadca domow, sala tronowa krola), Rynek (-5: karczma, kowal
  z kuznia, platnerz, zaklinacz, runiarz, narzedziownik, kupiec, opiekun zwierzat, przewodnik, wozkarz) i Glebiny (-6:
  domy, kanal lawy z mostami). Ulice to krzywe Catmulla-Roma, jaskinie z szumu, budynki stoja wzdluz ulic i maja
  chodniki do ulicy; pietra laczy kilka kamiennych schodow. Dwie bramy: zachodnia (wejscie) i wschodnia z drzwiami
  poziomu 25 do Kopalni Zelaznej Brody (-5). 16 domow na sprzedaz (systemem domow, `houses.csv`, pietra -4..-6). Cale
  miasto i przedsionek sa strefa ochronna. NPC to krepi, brodaci krasnoludowie z LPC (`tools/art/lpc_compose.py`,
  `DWARF_TOWNS`, autorzy w `CREDITS-LPC.csv`).
- Miasto tylko do zamieszkania: `map.properties` `residence_only=` -> `Town.starting() == false`. Nie ma go w wyborze
  miasta startowego, ale kaplan pozwala tu zamieszkac (`homeTown`, odrodzenie w swiatyni na -4). Durnkar stoi na
  kontynencie glownym (osiagalny pieszo z Varden; wozkarze jezdza do miast kontynentu glownego). `Town.MAX_TOWNS` = 8.
- Trzy kopalnie, kazda z wlasna sztolnia w zboczu (wschod 520,299; zachod 464,299; poludnie 483,318) i sciezka do
  najblizszej drogi: Kopalnia Zelaznej Brody (pod miastem na wschodzie), Kopalnia Glebokiego Kilofa i Stara Sztolnia
  Granitowa. Kazda ma pietra -1..-7, kazde wieksze od poprzedniego, z komorami polaczonymi korytarzami i slepymi
  odnogami. Glebiej silniejsze i liczniejsze potwory, a na kazdym pietrze komory od schodow od slabszych do
  silniejszych (`route_levels`): -1 czerw gnilny i krasnolud, -2 krasnolud gornik, -3 krasnolud zolnierz i czerw
  padlinozerny, -4 krasnolud straznik, -5 krasnolud geomanta i kamienny golem, -6 zywiolak ziemi i krysztalowy
  golem, -7 Behemot i kamienny wartownik (z lawa).
- Kopalnia Zelaznej Brody laczy sie z miastem: korytarz od wschodniej bramy na -5 z drzwiami poziomu 25
  (570,321,-5).
- Nowe potwory (`monsters.csv`, lup, bestiariusz): Czerw gnilny 8, Krasnolud 10, Krasnolud gornik 12, Krasnolud
  zolnierz 16 (kusza), Czerw padlinozerny 20, Krasnolud straznik 26, Kamienny golem 32, Krasnolud geomanta 34
  (ziemia, leczy), Zywiolak ziemi 55, Behemot 120. Klucze nie sa nazwami z Tibii (`mine_grub`, `deep_worm`,
  `dwarf_warden`, `rock_golem`, `dwarf_earthcaller`, `earth_spirit`, `deep_behemot`). Grafika z OTSP
  (`tools/art/otsp_import.py`, `STOCKY` - krepe, przysadziste sylwetki).
- `structures.Plan.cave(..., lava=)`, `free_under/stamp(per_floor=True)` (kazde pietro szuka miejsca osobno),
  `check()` zglasza przejscie bez schodow. Test: `world/DwarvesTest` (ukryte wejscie, miasto, kopalnie, drzwi
  poziomu, potwory).

## Kontynent glowny i ziemie premium (generator `tools/mapgen/realms.py`, 0.250)

- Przy tworzeniu postaci mozna wybrac tylko 3 miasta kontynentu glownego: Varden, Frostholm i Qasirah
  (`StartingTowns.list`, walidacja takze na serwerze, `ServerWorld.isValid`). Valmir, Selvaran i Nerys to ziemie
  premium (`Town.landmass() > 0`, `map.properties` `premium_towns=Miasto:numer`, tez `residence_only`): nie sa do
  wyboru, a istniejace postacie z domem tam zachowuja dom (odrodzenie w ich swiatyni). Durnkar: kontynent glowny,
  tylko zamieszkanie.
- Ziemie premium: 1 - poludnie (Valmir, Selvaran), 2 - Szmaragdowe Wyspy z Nerys. Od kontynentu glownego dzieli je
  woda: `realms.split` kopie kanaly (Zielona Odnoga od Srebrnej Rzeki na wschod i krotkie odnogi, miejsca wyznaczone
  offline minimalnym przekrojem grafu ladu), a petla naprawcza tak dlugo przecina najkrotsza droga pieszo z Varden do
  swiatyni premium (most znika w calosci, schody sa zamurowane, korytarz podziemny dostaje mur), az nie ma zadnej.
  Do ziem premium plyna tylko statki (kapitanowie, `ships.csv`): konto premium albo promocja (`canSail`).
- Wozkarze (`Travel.routes`, `NpcRegistry.landmassOf`) jezdza tylko w obrebie jednego ladu: nigdy na ziemie premium.
- Testy: `world/RealmsTest` (brak drogi pieszo, 3 miasta startowe, wozy, statek i premium), `StartingTownTest`,
  `StartingTownNetworkTest`.

## Warownia Gorvalda (generator `tools/mapgen/castle.py`, 0.250)

- Zamek dla poziomow 20-70 na kontynencie glownym (ok. 750..814 x 336..390, daleko od miast), w fosie, z zamknietym
  murem bez bram, czterema wiezami narozniami, dziedzincem (koszary, psiarnia, kuznia, kaplica, stajnia), twierdza
  (sala glowna, zbrojownia, kuchnia), koroną murow (+1), komnatami kasztelana (+2) i sala tronowa (+3).
- Wejscie tylko przez podziemia: jaskinia w skale przed fosa (740,397,0) -> -1 posterunek orkow -> -2 orcze chodniki i
  rogacze -> -3 glebokie tunele rogaczy -> krotki chodnik w gore (-2) -> piwnica pod twierdza (-1, straz zamku) ->
  schody do sali schodow zamku. Z powierzchni nie ma innego wejscia (`CastleTest`).
- Drzwi poziomu 55 (780,360,+2) do sali tronowej: boss Gorvald, Kasztelan Warowni (poziom 70) i skrzynia zadania
  (783,349,+3, nagroda skalowana trudnoscia `ChestRewards`, raz na postac).
- Nowe potwory (oryginalne nazwy, grafika z OTSP): Rogaty rozbojnik/lucznik/straznik/mistyk (24-38), Ogar/Kusznik/
  Wartownik/Kapelan warowni (40-56) i Gorvald (70); orki i trolle jaskiniowe z istniejacych danych.
- Klocki: `structures.find_site(..., bounds=)` ogranicza szukanie miejsca (zamek tylko na polnocy kontynentu).


## Srebrna Rzeka z delta i Wulkan Pozogi (generator `tools/mapgen/delta_volcano.py`) - 2026-11-11

Darek: "obok miasta Garden [Varden] niech plynie szeroka rzeka z delta ujscia do morza; miejsce polowania wulkan
odgrodz lawa, zeby nie mozna bylo przejsc, i daj tam statek; plywac moga tylko premium i promocja; pod wulkanem az -7
pieter i bardzo duzo silnych potworow i +7 bardzo duzo potworow mieszanych - exp dla party, nie solo".
- Srebrna Rzeka: rzeka z jeziora pod Varden na poludniowy wschod jest poszerzona o 3 pola z kazdej strony (koryto
  14-25 pol) i przy ujsciu (rozwidlenie ok. 601,698) rozdziela sie na cztery koryta do zatoki pod Selvaran i do
  laguny za mierzeja: wachlarz delty z wyspami, lachami piasku, piaszczystymi brzegami i trzcinami. Drogi (Valmir -
  Selvaran) przechodza po mostach (`features.connect`, koszt wody rzeki). Trzy pomosty rybackie z sieciami i lodka;
  region "Delta Srebrnej Rzeki" (560..662 x 680..818) - w `data/fishing.csv` wegorze. Teren zmieniany w `build` po
  stawach (`nature.ponds` - ich losowanie bez zmian), przed drogami.
- Wulkan Pozogi (srodek krateru 652,850): wulkaniczny cypel na poludnie od delty. Lawa splywa do morza: pas lawy na
  calym brzegu cypla (od 31 pol od srodka), od strony ladu szeroki do 42 pol, odcina go od reszty ladu - pieszo nie da sie wejsc, drogi omijaja strefe (`footprint`).
  Popiol z obsydianem, zweglone drzewa, strugi i oczka lawy, stozek (gora, promien 24). Przystan z duzym statkiem
  na wschodnim brzegu: Kapitan Ignacy Smolarz (`pozoga_captain`, 692,851, miasto podrozy "Wulkan Pożogi", strefa
  ochronna). Rejsy w obie strony z Varden, Valmir, Selvaran, Qasirah, Frostholm, Nerys i Przystani Wschodniej.
- Stozek Pozogi +1..+6: lawowe korytarze z komorami we wnetrzu gory, w kole coraz mniejszym (promien 23..13), +7
  krater z jeziorem lawy i krawedzia. Wejscie: wneka w poludniowym zboczu (schody 652,871). Bardzo duzo potworow
  mieszanych (ogien, powietrze, mroz, cienie: wiwerny, harpie, mrozne olbrzymy, gargulce, upiory, smoki), wyzej
  silniejsze: ok. 50 potworow na +1 do ok. 30 na +6/+7.
- Trzewia Pozogi -1..-7: wejscie we wnece zachodniego zbocza (631,850). Kazde pietro wieksze (do 72 x 55), pien
  komor od schodow do schodow i boczne slepe komory (najdalsze ze skrzynia lupu). Bardzo duzo silnych potworow
  ognia, glebiej silniejsze i liczniejsze (ok. 50 na -1, ok. 95 na -7): zywiolaki ognia, chrzaszcze, magowie
  popiolu, lawowe golemy, salamandry, ogary, kolosy, smoki magmy, czerwie i demony otchlani, skrzydlate pomioty.
- Wszystkie spawny wulkanu sa stadami (`stado`, zwykle 5-6 potworow, poziomy 300-1000) - lowisko dla druzyn (premia
  druzyny). Regiony: "Wulkan Pożogi" (0, pasy wewnatrz pierscienia), "Stożek Pożogi" (+1..+7), "Trzewia Pożogi"
  (-1..-7), po jednym na pietro. `route_levels` nie zmienia wulkanu (osiagalny tylko statkiem).
- Nowe potwory (OTSP przebarwione, `TINTED`): Popielna salamandra 420, Ogar Pozogi 520, Magmowy kolos 620 (lup,
  bestiariusz).
- Pietra do +7: `Position.MAX_FLOOR` = 7, `WorldRenderer` rysuje wyzsze pietra do MAX_FLOOR, generator i
  `route_levels` licza pietra -7..+7 (`floor_+6.txt`, `floor_+7.txt`).
- Rejsy (wszyscy kapitanowie, takze przewoznicy na wyspy): wczesniej tylko konto premium, teraz premium ALBO
  promocja (`PlayerCharacter.canSail`, `GameSession.travel`, menu kapitana w `Hud`). Odmowa: "rejsy tylko dla kont
  premium albo postaci z promocją" i podpowiedz wladcy miasta. Woznice (CARRIER) bez zmian - bez wymagan.
- Generator: `strongholds.RESERVED` - lochy dziczy (`wilds`) nie zajmuja okolicy, w ktorej pozniej staje twierdza
  orkow i Elandor (po zmianach terenu losowe zejscie do lochu potrafilo zablokowac miejsce osady).
- Test: `world/VolcanoDeltaTest` (odciecie lawa, rejsy, kazde pietro -7..+7 od przystani, liczba i sila potworow,
  premium/promocja, szerokosc rzeki, koryta delty, mosty, droga Valmir - Selvaran).

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

## Pieczec Gasnacej Gwiazdy (Z logika, S serwer, C okno Herolda, generator `tools/mapgen/citadel.py`) - 2026-11-12

Bardzo dlugi quest endgame (poziom 300+) na wyspie premium "Wyspa Gasnacej Gwiazdy" i nagroda w swiatyni Varden.

- Wyspa: powierzchnia od (80,770), osiagalna tylko statkiem (przewoznik `star_captain` Captain Brannoc przy przystani (88,796), trasy
  do Varden, Qasirah i Valmir; statki wymagaja konta premium). Pieszo z Varden nie ma drogi (generator sprawdza `realms.Graph`, test
  `StarQuestTest`). Dziedziniec ze Straznikiem Pieczeci (`star_warden`, rola GUIDE, zadania z `quests.csv`), Swiatynia Pieczeci za drzwiami
  poziomu 300 (126,792), siedem portali (TUNNEL, obiekt `boss_portal`) w rzedzie y=778 i schody (140,784) do sali nagrod pod swiatynia (-1, brama zadania (142,784,-1), skrzynie od (144,784,-1)).
- Sekcje: siedem planow po trzy pietra (-1..-3), kladzionych przez `structures.stamp` pod morzem (x 12-318, y 850-975): F1 komora z
  portalem powrotnym, jaskinie (`isles.rooms_floor`), budka bramna (drzwi poziomu 300 + brama `quest:star_mK`) i schody; F2 jaskinie;
  F3 jaskinie, sala bossa, drzwi za bossem (brama `boss:<klucz>`) i alkowa z portalem do nastepnej sekcji. Sekcja 7 nie ma budki:
  Noctharion zamyka Pieczec, portal wraca do swiatyni. Kazdy start sekcji ma portal powrotny do swiatyni.
- Bossowie: Vorgrim (360), Ysmera (420), Kharzul (470), Vessara (520), Ilmora (575), Ugrath (640), Noctharion (700); lupy w `loot.csv`,
  przedmioty tylko z bossow w `boss_items.csv`. Potwory sekcji: `ash_penitent`, `cinder_revenant`, `rimebound_monk`, `hoarfrost_warden`,
  `slag_forgehand`, `molten_cultist`, `thorn_maiden`, `spore_stalker`, `mirror_acolyte`, `glass_phantom`, `maw_crawler`,
  `gore_harbinger`, `dimmed_seraph` (poziomy 310-600, w `HAND_PLACED`).
- Bramy: plik mapy `star_quest.csv` (`MapLoader.readStarQuest`, `world/QuestGate`): `gate;x;y;z;wymog` z wymogiem `quest:<klucz>` (zadanie
  oddane), `boss:<potwor>` (znacznik `boss.<potwor>` w postaci ustawia `GameSession.taskKill`, takze `partyKill`) albo `flag:<znacznik>`.
  `MovementSystem.step` zwraca `MoveResult.GATE_SEALED`, `GameSession.useAt` wypisuje, czego brakuje (`gateRefusal`). Drzwi bram sa tez
  w `level_doors.csv` (300). Siatka dostepu (`Reachability`, `TreasureChests`) bram nie widzi.
- Misje (`quests.csv`, `quest_chains.csv`; stan w `QuestLog`, zapisywany): `star_m1`..`star_m6` (50-70 zabic potworow sekcji 1-6) i
  `star_final` (Noctharion), kazda wymaga poprzedniej i daje mikstury. Oddanie `star_final` (`QuestSystem.turnIn` -> `StarQuest.onCompleted`)
  ustawia znacznik postaci `star_sealed` (`PlayerCharacter.questFlags()`, zapis `qflags` w `CharacterCodec`).
- Sala nagrod: `choice;grupa;x;y;z;przedmiot` w `star_quest.csv` + zwykle skrzynie lupu w `chests.csv` (id `d<x>_<y>_<z>`). Z grupy
  `star_reward` mozna otworzyc jedna (`GameSession.openRewardChoice`, znacznik `rcg_star_reward` w `openedChests`); przedmiot innej
  profesji nie zuzywa wyboru, przed `star_sealed` skrzynie sa zamkniete.
- Herold (`varden_herald`, Herald Matthias, rola `HERALD`, swiatynia Varden (500,497)): `GameSession.blessAll` (akcja `npc:blessall`,
  rozmowa "blogoslawienstwa" / "tak") sprzedaje wszystkie brakujace blogoslawienstwa za `StarQuest.bundlePrice` (80% sumy cen u mnichow,
  w setkach gp), tylko z `star_sealed`; bez niego odmawia (`star.herald_locked`). Szesciu mnichow z kaplic (`BLESSER`) bez zmian.
  Offline i serwer uzywaja tej samej sesji, wiec zachowuja sie tak samo. Napisy: `Messages` (`star.*`, pl/en/es).
- Testy: `StarQuestTest` (lancuch misji, kodek, bramy, dostep od przystani, brak drogi z Varden, wybor jednej nagrody, Herold z flaga i bez).
  Podglad: `-Dpixel.flags=star_sealed,boss.vorgrim` (regresja GUI, sceny `star_*`).

## Minimapa (Z kolory, C rysowanie)

`world/MinimapPalette.color/paint`: piksel RGBA8888 na kafelek - przejscie zolte, sciana ciemna, podloze w kolorze
kafelka x0.8, blokujacy obiekt x0.5, puste pole przezroczyste. `client/ui/Minimap` buduje teksture pietra raz (Nearest)
i rysuje wycinek 60x60 pol wokol gracza albo cale pietro (`Hud.toggleMap`, klawisz J) z nazwami miast.
Dotkniecie duzej mapy -> akcja `map:goto:x,y` -> `GameScreen.walkTo`: `Pathfinder.path` (BFS na tablicach, bez przejsc
na inne pietra) i co klatke kierunek do nastepnego pola, podawany jak z joysticka (dziala tez online).
Dotkniecie swiata (`GameScreen.tapTile`): gracz online -> menu gracza, NPC blisko -> rozmowa, NPC daleko -> marsz na
pole obok (`pathNextTo`) i rozmowa po dojsciu, potwor -> cel, cialo obok -> lup, wolne pole -> marsz bez komunikatu.

Mgla mapy (2026-11-05, jak w Tibii): `world/ExploredMap` w `PlayerCharacter.explored()` - bit na kafelek na pietrze
(z -7..7), kawalki 64x64 (512 B) tworzone przy pierwszym odkryciu (11 pieter 1000x1000 najwyzej ~1,4 MB).
`GameSession.update` (offline i serwer) oraz `GameScreen` online odkrywaja widok 17x13 wokol postaci
(`revealView`, okno 15x11 + pas 1 pola; nic, gdy postac stoi). Zapis: pole `fog` (deflate + base64), format v7;
starsze zapisy (`markLegacy`) dostaja odkryty prostokat +-24 x +-18 wokol swiatyni miasta postaci (raz, w konstruktorze
sesji). Online: serwer liczy mgle ze swoich pozycji i zapisuje ja; po zalogowaniu wysyla ja raz komunikatami
`FOG` (po 64 kawalki, < 48 KB na linie), SELF jej nie zawiera (`encode(pc, false)`); klient OR-uje FOG i dalej
odkrywa sam. `Minimap`: nieodkryte pola - ciemna mgla z lekkim szumem, 2 pola miekkiej krawedzi; nowe odkrycia
dosylane do tekstury blokami 32x32 (`Texture.draw` z gotowych Pixmap, bufor cykliczny prostokatow w `ExploredMap`).
Znaczniki i nazwy miast tylko na odkrytych polach (swoje miasto zawsze); dotkniecie nieodkrytego pola duzej mapy:
"Nie znasz tej okolicy.". `-Dpixel.revealMap=1` odkrywa wszystko (regresja GUI domyslnie), `-Dpixel.exploreRoute`
odkrywa trase do zrzutow.

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
`GameSession.useNearby` -> `toggleTraining` (manekin obok); `updateTraining`: co `TRAIN_MS` = `Balance.DUMMY_STRIKE_MS`
(2,5 s, 24 ciosy/min) `Balance.DUMMY_TRY_FACTOR` = `HUNT_TRY_FACTOR / 2` = 15 prob umiejetnosci z
`CombatFormulas.attackProfile` i (tylko z tarcza w rece albo mnich) `DUMMY_BLOCK_TRY_FACTOR` = `2 * HUNT_TRY_FACTOR / 3` = 20
prob `SHIELDING`, wszystko x etap serwera; zmiana pola albo smierc konczy. Komunikat na start podaje tempo
("Miecz 360 prób/min, Tarcza 480 prób/min (x etap serwera; ok. 2/3 tempa polowania ...)").
Rownowaga (`HuntBalanceSimulationTest#dummyTrainsAboutTwoThirdsAsFastAsHunting`): proby/min przed etapem - manekin bron 360,
tarcza 480; polowanie rycerza 50-100 bron 630-820 (21-27 atakow/min x 30), tarcza 1200-1900 (14-21 odpartych ciosow x 90).
Do umiejetnosci rycerza 100 z polowania (miecz 82 / tarcza 77) manekin potrzebuje 2,3 / 2,1 h zamiast 1,4 h (tempo ok.
62% / 69%), do poziomu 50 (64 / 59) ok. 84% / 90%. Rycerz miecz 10 -> 60: manekin ok. 16 min (do 0.222.0: 1 proba co 3 s,
4,8 h), polowanie ok. 14 min. Bez doswiadczenia i lupu. Rozdzka trenuje przy manekinie magie tylko 15 "many" na cios;
magie trenuje sie wydawaniem many (kazdy czar, takze leczenie i runy w miescie, `SpellSystem.trainMagic` x HUNT_TRY_FACTOR),
o czym przypomina komunikat. Ataku czarami w manekin nie ma (czary ofensywne sa zablokowane w strefie ochronnej).
Wyglad: `objects/training_dummy.png` (OTSP, przebarwiony na worek ze sloma i drewniana podstawe), rysowany w naturalnym
rozmiarze 68 px od prawego dolnego rogu pola (`GameScreen.drawChests`, `TileArt.dummyNative`), po kazdym ciosie
kolysze sie przez 0,45 s (`GameSession.lastTrainingStrikeMs`); postac obraca sie do manekina.
Miejsca (`TrainingDummiesTest#listsWhereTheDummiesAre`): Varden 498,504 i 501,504 (plac 5 pol na poludnie od swiatyni),
Frostholm 744,205 i 747,204, Selvaran 685,685 i 686,689, Qasirah 217,485 i 216,492, Valmir 492,757 i 495,757,
Nerys 867,730 i 870,732 (wszystkie z = 0). Sceny GUI: `dummy_varden_mobile`, `dummy_training_mobile`, `dummy_training_sorcerer`.

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

### Bestiariusz jak w Tibii (achievement/Bestiary*, 2026-11-06)

- `data/bestiary.csv` (`klucz;odpowiednik z Tibii;gwiazdki;occurrence`) - kazdy zwykly potwor ma odpowiednik z Tibii
  (gwiazdki i occurrence z Canary `data/monster/**`, np. wilk = Wolf 1*, dzik = Boar 2*, magma_drake = Dragon Lord 3*,
  abyssal_demon = Demon 4*, abyss_lord = Brachiodemon 5*). Bossowie nie maja wpisu (test `BestiaryDataTest`).
  Dobor odpowiednikow jest reczny (najblizszy potwor Tibii wg roli/poziomu). Yeti: occurrence 0 zamiast 3.
- `BestiaryDifficulty` (progi etapow i punkty charmow jak w Canary): Harmless 5/10/25 (1 pkt), Trivial 10/100/250 (5),
  Easy 25/250/500 (15), Medium 50/500/1000 (25), Hard 100/1000/2500 (50), Challenging 200/2000/5000 (100);
  bardzo rzadkie (occurrence 3) 2/3/5 zabic i 10/30/50 pkt wg gwiazdek.
- `BestiaryEntry.stage(zabicia)` 0..4: 1 - podstawy (nazwa, klasa, gwiazdki); 2 - zdrowie, exp, walka, lup pospolity
  i niepospolity; 3 - odpornosci, wystepowanie, lup polrzadki; 4 (ukonczony) - caly lup i punkty charmow
  (`GameSession.bestiaryProgress` wypisuje etap i dodaje punkty przy ukonczeniu).
- `LootRarity` - lup jako kategorie Tibii zamiast %: pospolity >= 25%, niepospolity 5-25%, polrzadki 1-5%,
  rzadki 0,2-1%, bardzo rzadki < 0,2% (progi zamierzone przez Tibie; Canary ma blad dzielenia calkowitego).
- `BestiaryClasses` - 20 klas Tibii (rasa z monsters.csv) z polskimi nazwami; `BestiaryView` - lista klas
  (wiersz Charmy, klasy `bestclass:<Klasa>`, Bossowie), lista gatunkow (nieznane jako "?"), stronicowanie.
  Okna w `core/client/ui/BestiaryWindows` (desktop i mobile).

### Charmy (charm/*, Tibia 2025)

- `Charm` - 25 charmow w kolejnosci Canary: 14 Major (kupowane za punkty charmow), 11 Minor (za echa).
  Wartosci i koszty stopni I/II/III z Canary `bestiary_charms.lua`. Kupno stopnia Major daje echa
  25t^2+25t+50 (t = stopien przed kupnem).
- `Charms` (stan postaci): sloty 2 (darmowe konto) / 6 (premium), na stwora 1 Major + 1 Minor, brak na bossach.
  Major wymaga ukonczonego wpisu, Minor wystarcza znany stwor (1 zabicie - uproszczenie Pixel). Przed
  przeniesieniem charm trzeba zdjac. `resetAll` oddaje wszystkie punkty (awansowani zachowuja 100 ech jak w Canary).
- Ceny w zlocie (`CharmPrices`, ceny Tibii / 4 jak reszta ekonomii): zdjecie 25 x poziom, reset wszystkich
  25 000 + 2 750 x poziom powyzej 100. Platnosc z plecaka, potem z banku (`GameSession.payGold`).
- Dzialanie (`CharmEffects`, haki w `CombatSystem`, rzut `nextInt(10000)`):
  - Rana/Podpalenie/Trucizna/Zamrozenie/Porazenie/Klatwa/Boski gniew: po trafieniu min(ceil(5% max HP potwora),
    ceil(2 x poziom)) obrazen danego zywiolu (z odpornosciami potwora).
  - Rzez: po zabiciu min(ceil(15% HP zabitego), 6 x poziom) w 4 sasiednie pola; Przytloczenie: min(8% HP celu,
    5% max HP gracza); Przeplyw many: min(8% HP celu, 2,5% max many) - oba obrazenia neutralne.
  - Niski cios: szansa krytyka p + wartosc (rzut uzupelniajacy l / (100 - p); Canary rzuca drugi raz).
    Dziki cios: +% obrazen krytyka (`CriticalHit.apply`). Wampirzy uscisk/Zew pustki: dodatek do kradziezy
    zycia/many (`Leech`), dziala tylko gdy postac ma juz kradziez z przedmiotow/zaklec (opis Tibii).
  - Unik (cios potwora chybia, zdarzenie BLOCKED), Riposta (odbija przyjete obrazenia, neutralne, bez pancerza).
    Kolejnosc obronna jak w Canary: najpierw Minor, potem Major.
  - Blogoslawienstwo: -6/9/12% utraty exp przy smierci od tego stwora (`applyDeathPenalty(min, procent)`).
  - Patroszenie (Gut): szansa przedmiotow MATERIAL + ceil(szansa x %) (Canary: produkty stworow);
    Skorowanie (Scavenge): szansa "trofeum" (najrzadszy MATERIAL potwora) x (1 + %) - odpowiednik Pixel.
  - Okaleczenie i Odretwienie: paraliz 10 s (krok potwora x3 - Pixel nie ma zwyklego spowolnienia).
    Smiertelny chwyt: potwor nie ucieka przez 30 s. Zastrzyk adrenaliny: +250% szybkosci przez 10 s
    (wzor Canary; nadpisuje biezace przyspieszenie).
  - Oczyszczenie (odpowiednik Pixel - brak stanow typu trucizna/podpalenie): oczyszcza zywiolowy cios potwora
    (brak obrazen). Odwrocenie pustki: wyssanie many przez potwora daje mane zamiast ja zabierac.
  - Zdarzenia walki `CHARM:<NAZWA>` (klient rysuje fioletowa liczbe i nazwe charmu), log "Charm X: ...".
  - Brak: Charm Expansion (sloty stale), Animus Mastery, tracker bestiariusza, bosstiariusz.
- Akcje (`Actions`, takze ACT na serwerze): `charm:buy:X`, `charm:assign:X:klucz`, `charm:remove:X`, `charm:reset`.
- Zapis: pole `charms` = `pkt,maxPkt,echa,maxEcha|NAZWA:stopien:klucz|...` (bez zmiany wersji zapisu);
  zapis bez pola dostaje punkty za juz ukonczone wpisy (`Charms.migrateFrom`). Klient online dostaje stan w SELF.

## Kolo Przeznaczenia (Z logika `wheel/*`, S serwer, C okno, 2026-11-06)

Liczby z Canary (`io_wheel.cpp`, `player_wheel.cpp`, skrypty czarow objawien); co jest przeskalowane albo zastapione,
opisuje "Uproszczenia" nizej.

- Dostep: poziom 51+, promocja (`isPromoted`) i premium; 1 punkt promocji za kazdy poziom od 51
  (`DestinyWheel.available` = poziom - 50). Bez premium lub promocji punkty zostaja, ale premie sa uspione
  (`PlayerCharacter.wheelBonus()` = `WheelBonus.NONE`). Po utracie poziomow `trim` zdejmuje nadmiar od zewnatrz
  kola (pola 200, 150, ...) i usuwa pola odciete od srodka.
- 36 pol (`WheelSlice`): 4 cwiartki (`WheelDomain`: zielona, czerwona, fioletowa, niebieska) x 9 pol o pojemnosci
  50 / 75 x2 / 100 x3 / 150 x2 / 200. Pole mozna zasilic, gdy jedno z pol-poprzednikow (listy z Canary
  `canPlayerSelectPointOnSlot`, z polaczeniami miedzy cwiartkami G_T-R_T, R_B-P_T, P_B-B_B, B_T-G_B) jest pelne
  i postac ma lacznie co najmniej 50 / 125 / 225 / 375 punktow dla pol 75 / 100 / 150 / 200. Zdjecie punktu,
  od ktorego zalezy inne niepuste pole, jest odrzucane.
- Oddanie (kazdy punkt pola): HP rycerz 3 / paladyn 2 / mag 1 / mnich 2, mana 1 / 3 / 6 / 2, udzwig 5 / 4 / 2 / 5,
  mitygacja 0,03% (mnoznik pochlaniania pancerza i obrony, jak Canary `calculateMitigation`).
- Przekonanie (pelne pole): +1 umiejetnosci profesji (wrecz / dystans / magia / piesci), kradziez zycia 0,75%,
  many 0,25%, ulepszenie czaru (`WheelPerks`, 5 grup na profesje; stopien = liczba pelnych pol grupy, 1-2),
  pasywki pol 200 (`WheelPassive`: zielone 200 Instynkt bitewny / Taktyka pozycyjna / Mistrzostwo run / Wiez
  lecznicza / Przewodnia obecnosc, fioletowe 200 Leczenie w boju / Mistrzostwo balistyki / Mistrzostwo skupienia /
  Mistrzostwo run / Sanktuarium), naczynia klejnotow (+1 rezonansu naczynia domeny, ponizej).
- Objawienia: etap 1/2/3 przy 250/500/1000 punktach w cwiartce; kazdy etap kazdej cwiartki daje +4/+9/+20
  obrazen (tylko w potwory) i leczenia. Zielona: Dar Zycia (smiertelny cios, ktorego nadwyzka nie przekracza
  20/25/30% maks. HP, leczy tyle procent i postac przezywa; odnowienie 30/20/10 h gry; skraca odnowienia czarow
  o minute). Fioletowa: awatar profesji (czar `avatar_of_*`, 15 s: -5/10/15% obrazen otrzymanych, kazdy cios
  krytyczny, +5/10/15% obrazen krytycznych; odnowienie 2 h - 30 min za etap 2 i 3). Czerwona: Rzut kata,
  Boski granat, Mistrzostwo promienia (Wielki promien smierci), Blogoslawienstwo gaju, Wybuch ducha.
  Niebieska: Mistrzostwo walki, Boskie wzmocnienie, Wyssanie ciala, Blizniacze wybuchy, Asceta.
- Warstwa modyfikatorow czarow (`WheelSpells`, wolana z `SpellSystem` jako male haki): mana, odnowienie,
  % obrazen/leczenia, kradziez, krytyk, cele i promien (`SpellAugment`); czary objawien sa zablokowane
  (`CastResult.WHEEL_LOCKED`, niewidoczne w ksiedze) bez etapu 1. Nowe rodzaje czarow: `ATTACK_BEAM` (promien
  na linii do celu) i `WHEEL_BUFF` (awatar, Boskie wzmocnienie). Stan awatara jest czasowy (`DestinyWheel.tick`).
- Haki walki (`WheelEffects`): `CombatSystem.critical` (premie procentowe i plaska), `CriticalHit` (szansa
  i obrazenia krytyczne), `Leech`, `CombatFormulas.mitigateOnPlayer` (mitygacja, obrona tarczy z Mistrzostwa
  walki, awatar), `PlayerCharacter.damage` (Dar Zycia), `effectiveSkill` (umiejetnosci z kola).
- Zmiany tylko w swiatyni: strefa ochronna z kaplanem najwyzej 8 pol dalej na tym samym pietrze
  (`GameSession.inTemple`). Tibia pozwala zmieniac kolo w kazdej strefie ochronnej; Pixel wiaze to ze swiatynia,
  zeby przebudowa byla decyzja "w miescie", a nie w depozycie obok expowiska. Zapis zestawu dziala wszedzie,
  wczytanie zestawu i reset (bez oplaty jak w Tibii) tylko w swiatyni. 3 zestawy.
- Akcje (`Actions`, online ACT na serwerze): `wheel:add:KOD:n`, `wheel:remove:KOD:n`, `wheel:reset`,
  `wheel:save:i`, `wheel:load:i` (KOD jak `GT75`, `R50`, `P200`). Stan wraca do klienta w SELF.
- Okno (`WheelView` + klient `WheelWindows`, wspolne okno informacyjne HUD): przeglad (punkty i swiatynia w jednym
  wierszu, 4 kafle cwiartek z postepem objawienia, aktywne premie, klejnoty i naczynia, zestawy, czyszczenie),
  cwiartka (objawienie + 9 pol z krotkim efektem `WheelView.shortEffect`, np. "+3 HP/pkt; pełne: +1 walka wręcz",
  przy czarze "Wstrząs ziemi I: +12,5% obrażeń", stan w etykiecie), karta pola jak dymek z Tibii: oddanie za punkt /
  teraz / przy pelnym polu, nastepny punkt, przekonanie (czar po polsku z nazwa z Tibii, stopien I i II z
  `WheelPerks.Upgrade`, drugie pole grupy, uwaga o zamienniku; pasywka z nazwa z Tibii i dzialaniem; naczynie z
  rezonansem i klejnotem), objawienie domeny, wymagania, przyciski +1 / +10 / do pelna / -1 / oproznij. Karta
  objawienia (`wheel:rev:G|R|P|B`): nazwa (i z Tibii), czar, etapy 1-3 z progami i stanem, moc objawien
  (+4/+9/+20), Mistrzostwo objawienia z klejnotu. Wszystkie opisy sa skladane z danych (`WheelPerks`,
  `SpellAugment.describe`, `WheelPassive`, `WheelRevelation` - teksty Daru Zycia i awatara z `WheelEffects` /
  `DestinyWheel`), wiec tekst nie rozjedzie sie z kodem. Na komputerze w pustej kolumnie rysowane jest kolo (36 pol,
  kolor wg zapelnienia, romby na polach naczyn: jasny = klejnot w naczyniu i pole pelne). Wejscie: klawisz U,
  `/koło`, Opcje, kaplan w rozmowie.
- Zapis: osobne pole `wheel` = `p=G50:50,...;s0=...;g=ms;a=ms;as=etap;ac=ms` bez zmiany wersji zapisu
  (rownolegle prace nad Kuznia podnosza wersje); zapis bez pola = puste kolo.
- Uproszczenia i zamienniki:
  - Czary bez odpowiednika w Pixel zastapione najblizszymi (np. Front Sweep -> Szal, Chivalrous Challenge ->
    Unicestwienie, Sharpshooter -> Boski sad, Magic Shield -> Wielkie uzdrowienie); opis w karcie pola.
  - Strong Ethereal Spear st. 2: Canary daje +380% obrazen; w Pixel czar jest juz pelnej mocy, wiec +38%.
  - Odnowienia czarow objawien przeskalowane do odnowien Pixel (np. Rzut kata -2 s za etap 2 i 3).
  - Boski granat wybucha od razu (Tibia: po 3 s), Wybuch ducha jest zwyklym czarem 300 poziomu (objawienie daje
    drugie uderzenie przy pelnej harmonii i krotsze odnowienie).
  - Wyssanie ciala dziala bez oslabien, Mistrzostwo run = 25% szansy na x1,2 wartosci runy, Wiez lecznicza
    i Przewodnia obecnosc = +10% leczenia (brak leczenia innych / mantry).
  - Plaskie premie objawien tylko w potwory; w PvP dziala jedynie zmniejszenie obrazen awatara.
  - Awatar kola i Transcendencja z Kuzni to jeden stan awatara jak w Canary: gdy dzialaja oba, liczy sie
    wiekszy efekt (Transcendencja -15% / +15% krytyka = etap 3), bez sumowania. Dar Zycia skraca odnowienia
    tym samym `Cooldowns.cutAll` co Rozped z Kuzni.
- Balans (`WheelBalanceTest`): 300 poziom, 250 pkt - srednio x1,02-1,15 "sily" (obrazenia x HP / otrzymane
  x kradziez) zaleznie od kolejnosci cwiartek; pelne kolo na 1000 poziomie ok. x1,08, z awatarem ok. x1,44
  na 15 s. Symulacja nie liczy ulepszen pojedynczych czarow (te ogranicza osobny test: najwyzej +50% obrazen).

### Klejnoty Kola (Wheel gems, `GemAtelier`, `WheelGem`, `GemDrops`, `GemView`)

- Lup (`GemDrops`, Canary `wheel_gems.cpp` / `config.lua`): klejnot mniejszy / zwykly / wiekszy (`GemQuality`)
  losowej rodziny profesji (straznik - rycerz, strzelec - paladyn, medrzec - czarodziej, mistyk - druid,
  duchowy - mnich; przedmioty 220-234) z potworow pod wplywem 9% / 0% / 0%, diabolicznych 3% / 3% / 9%,
  "archfoe" 0% / 9% / 3%; drugi los tylko po udanym pierwszym, najwyzej 2 / 2 / 1 sztuki. Archfoe w Pixelu =
  boss od poziomu 200 (`GemDrops.ARCHFOE_MIN_LEVEL`). Hak: `CombatSystem.onMonsterDeath` po odlamkach Kuzni.
- Pracownia (okno "Klejnoty i naczynia", `wheel:gems:0`): odkrycie klejnotu wlasnej profesji z plecaka za
  31 250 / 250 000 / 1 500 000 gp (ceny Tibii / 4) (plecak albo bank) daje losowa domene i mody: mniejszy 1 mod podstawowy
  (lista slotu 1 z Canary), zwykly 2 (drugi z listy slotu 2, inny niz pierwszy), wiekszy 2 + mod najwyzszy
  profesji (`SupremeGemMod`). Do 225 klejnotow. Blokada chroni przed rozbiciem i zmiana domeny. Zmiana domeny
  na nastepna (zielona -> czerwona -> fioletowa -> niebieska) za 31 250 / 62 500 / 125 000 gp. Rozbicie daje
  1-5 / 2-10 mniejszych albo 1-5 wiekszych okruchow (srodek przedzialu najczestszy jak `normal_random`).
- Naczynia: na cwiartke 3 pola z przekonaniem "naczynie"; rezonans = liczba pelnych pol naczyn domeny. Klejnot
  domeny wlozony do naczynia (tylko w swiatyni) daje przy rezonansie 1 mod 1, przy 2 mod 2, przy 3 mod najwyzszy.
- Mody podstawowe (`BasicGemMod`, wartosci z Canary `WheelModifierContext`): odpornosci (1-3%, pary, odpornosc
  ze slaboscia), mitygacja, HP / mana / udzwig zalezne od profesji (pelny mod albo polowa z odpornoscia).
  Mody najwyzsze: unik 0,28%, obrazenia krytyczne 2%, kradziez zycia 2% / many 0,8%, Mistrzostwo objawienia
  (+150 punktow do etapu objawienia domeny), ulepszenia czarow profesji (obrazenia, krytyk, leczenie, odnowienie).
- Warsztat okruchow: stopien modu I-IV wspolny dla wszystkich klejnotow postaci (mnoznik x1 / x1,1 / x1,2 / x1,5,
  slabosci i odnowienia bez mnoznika). Podstawowy: 500 tys. + 5, 1,25 mln + 15, 7,5 mln + 30 mniejszych okruchow;
  najwyzszy: 1,25 mln + 5, 3 mln + 15, 18,75 mln + 30 wiekszych. Kazdy mod na stopniu IV daje +1 punkt promocji.
- Haki: `WheelBonus` (HP, mana, udzwig, mitygacja, kradziez, etap objawienia), `WheelEffects.critDamage`,
  `WheelEffects.gemResist` w `CombatFormulas.mitigateOnPlayer`, `gemDodge` w `ForgeEffects.rollRuse`,
  `WheelSpells.augment` (mody czarow). Akcje: `wheel:gem:reveal:L|R|G`, `wheel:gem:lock|destroy|switch|socket:ID`,
  `wheel:gem:unsocket:G|R|P|B`, `wheel:grade:b|s:MOD` (online ACT, stan w SELF). Zapis: osobne pole `gems`
  (bez zmiany wersji), pusta pracownia nie jest zapisywana.
- Niezweryfikowane / dopasowania: udzwig mnicha z modow jak rycerza (brak w Canary); slabosci z modow
  zwiekszaja obrazenia (Canary liczy tylko dodatnie odpornosci); premia Rozpedu z modow odnowienia pominieta;
  koszty w zlocie jak w Tibii (ekonomia Pixela moze wymagac skalowania); mitygacja 500 = 5% (interpretacja
  jednostki); rozklad okruchow przyblizony; czary Tibii bez odpowiednika zastapione (np. Front Sweep -> Burza
  ostrzy, Divine Missile -> Swiety pocisk).

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

## Czary jak w Tibii (0.240, Z logika `magic/TibiaSpells`, C rysowanie i HUD)

Dane czarow z Canary (Tibia 15.x): slowa, profesje, poziom, mana i odnowienie sa jak w Tibii (`spells.csv`,
`incantations.csv`); obrazenia przeliczone na formuly gry (srednia z Tibii: poziom/5 + magia*a + b, rozrzut 15%).
Analiza luk wzgledem Tibii: `/mnt/project-files/analizy/czary-tibia.md`.

- Profesje: druid 64 czary (z runami), czarodziej 57, mnich 27, paladyn 24, rycerz 23 oraz 38 wspolnych
  (swiatla, lina, lewitacja, exana pox, wszystkie runy). Ksiega pokazuje slowa, mane i poziom.
- Odnowienia (0.249, sprawdzone z Canary `spells.cpp`/`actions.cpp`, `magic/Cooldowns`, `SpellGroups`):
  - kazdy czar ma wlasne odnowienie (`spells.csv`, wartosci z Canary `spell:cooldown`);
  - grupa glowna: atak 2 s, leczenie 1 s, wsparcie 2 s - **niezalezne od siebie**: czar ataku i czar leczenia
    mozna rzucic w tej samej chwili (dwa nacisniecia), dwa czary ataku - co 2 s. Czary skupienia (exevo gran mas
    flam/frigo/vis/tera) blokuja grupe ataku na 4 s (`spell_groups.csv`, Canary `groupCooldown(4 * 1000, 40 * 1000)`);
  - druga grupa (`data/spell_groups.csv`): "special" - silne uderzenia i blyskawica 8 s; "ultimatestrikes" -
    ostateczne uderzenia 30 s; "focus" - czary skupienia 40 s oraz utito tempo / utamo tempo 2 s, utito tempo san /
    utamo tempo san 10 s (jedna grupa dla czarow ataku i wsparcia jak w Canary); "greatbeams" 6 s; "burstsofnature"
    22 s; "virtue" - cnoty mnicha 10 s (grupa glowna cnot to teraz wsparcie 2 s);
  - runy: wlasne odnowienie runy i grupa jej czaru (runy ataku - grupa ataku 2 s, runy leczace - leczenie 1 s,
    paraliz - wsparcie) jak w Canary `rune:group/groupCooldown`; do tego runy i mikstury dziela 1 s "uzycia
    przedmiotu" (`SpellGroup.POTION`, Canary `nextPotionAction`, `timeBetweenExActions = 1000`): po miksturze runa
    czeka 1 s i odwrotnie, ale runa ataku nie blokuje czaru leczenia (inne grupy);
  - mikstury: 1 s (`SpellGroup.POTION`), bez wplywu na grupy czarow.
- Combo z paska (`combo:a+b+c`, `SpellSystem.castCombo`): jedno nacisniecie = najwyzej jeden czar - pierwszy, ktory
  mozna rzucic teraz (wszystkie odnowienia powyzej obowiazuja). Combo leczenie+atak przy jednym nacisnieciu rzuca
  tylko leczenie; atak poleci przy nastepnym nacisnieciu (grupy sa niezalezne, wiec od razu) - jak dwa klawisze
  w Tibii. Test: `TibiaCooldownsTest.comboFiresOneSpellPerPress`.
- Obszary czarow i run (0.249, `magic/SpellArea`, `data/spell_areas.csv`): macierze jak w Canary
  (`data/scripts/lib/register_spells.lua`), macierz dla kierunku polnocnego, obroty jak `AreaCombat::setupArea`
  (wariant ukosny dla scian z run jak `setupExtArea`). Srodek: cel runy, postac (exori, exevo gran mas...) albo w
  czarach kierunkowych pole przed postacia (Canary `needDirection` -> `getCasterPosition`). Trafione sa pola widoczne
  ze srodka (fale i promienie - z postaci). Kierunek fali/promienia = patrzenie postaci; gdy wybrany cel stoi
  w obszarze w innym prostym kierunku, czar idzie w jego strone (wygoda na telefonie). Bez nikogo na polach czar
  obszarowy nie rusza (bez straty many).

  | Czar / runa | Obszar Canary |
  |---|---|
  | wielka kula ognia, lawina, burza, deszcz kamieni | CIRCLE3X3 (37 pol, okrag, nie kwadrat 7x7) |
  | eksplozja | CIRCLE1X1 (krzyz) |
  | bomby ognia/trucizny/energii, exori, exori gran, exeta res | SQUARE1X1 (3x3) |
  | pola z run | jedno pole |
  | sciany ognia/trucizny | WALLFIELD (5 w poprzek rzutu, po skosie ukosna) |
  | sciana energii | WALLFIELD_ENERGY (7) |
  | exori mas, exevo mas san, exura gran mas res | CIRCLE3X3 |
  | exevo gran mas flam / frigo | CIRCLE5X5 (61 pol) |
  | exevo gran mas vis / tera | CIRCLE6X6 (85 pol) |
  | wybuch ziemi/lodu (Kolo) | RING1_BURST3 |
  | Boski granat | CIRCLE2X2 wokol celu |
  | exevo flam hur, exevo frigo hur, scorch, chill out | WAVE4 (1, 3, 3, 5 szer.) |
  | exevo gran flam hur | WAVE7 |
  | exevo vis hur, exevo tera hur | SQUAREWAVE5 |
  | exevo gran frigo hur | SHORTWAVE3 |
  | exori min, exori min flam | WAVE6 (3 pola przed rycerzem) |
  | exevo vis lux | BEAM5, exevo gran vis lux - BEAM8, exevo max mort - BEAM6/7/8 (stopien objawienia) |
  | grad ciosow / wielki grad / zamaszyste powalenie (mnich) | FLURRY_OF_BLOWS / GREATER_FLURRY_OF_BLOWS / suma SWEEPING |
  | Zbiorowe uleczenie ducha | MASS_SPIRIT_MEND |

  Czary lancuchowe (Lancuch pokuty, Wybuch ducha, Boskie oslepienie, Rycerskie wyzwanie) przeskakuja miedzy celami w
  promieniu (bez obszaru). Efekt: jedno zdarzenie `CombatEvent.shaped` (kod ksztaltu i kierunku w `amount`, pole
  postaci w `from`), klient rysuje dokladnie te pola (`SpellArea.eventTiles`, `GameScreen`), podglad celownika runy
  pokazuje ten sam obszar.
- Czary z parametrem: `utevo res "wilk"` (przywolanie), `exura sio "imie"` (leczenie innego; bez imienia -
  najslabszy sojusznik albo ty), iluzja (`utevo res ina "szkielet"` albo cel). Akcja `cast:klucz:parametr`.
- Slugi (`summons.csv`, `SummonTable`): najwyzej 2, mana z Tibii; ida za wlascicielem (teleport z innego pietra albo
  dalej niz 10 pol), atakuja jego cel i bronia sie (`CombatSystem.creatureHit`), znikaja po smierci postaci.
  Przekonanie stworzenia (runa) i ozywienie zwlok (runa na cialo -> szkielet) tez daja slugi.
- Inne: tarcza many (utamo vita, obrazenia z many), niewidzialnosc (potwory nie widza postaci; atak ja zdejmuje),
  regeneracja, wyzwanie (prowokacja do 5 potworow, nie uciekaja), lewitacja (exani hur up/down), magiczna lina.
- Exani hur up/down tylko tam, gdzie w Tibii: up - nad postacia otwarte powietrze (brak podloza albo dziura), cel to pole
  przed postacia pietro wyzej; down - pole przed postacia jest otwarte (dziura, krawedz), cel pod nim. Cel musi byc
  wolny i dostepny (bez przejsc, drzwi poziomu, domow, potworow, NPC, pokojow zadan); blokada PZ jak w Tibii
  (`GameSession.levitationDestination`).
- Stany (`combat/Conditions`, `ConditionType`): zatrucie, podpalenie, porazenie, krwawienie, zamrozenie, oslepienie,
  klatwa, toniecie. Tiki jak w Canary (`damageList` - malejace od ceil(suma/20) do 1; pola - stale tiki). Pasek stanu
  pokazuje ikone, opis podaje czar leczacy. Liczby tikow w kolorze zywiolu.
- Leczenie stanow wg profesji (jak w Tibii): exana pox - wszyscy (runa odtrutki tez), exana vis i exana flam - druid,
  exana kor - druid i rycerz, exana mort - paladyn. Zly czar dla profesji = "nie twoja profesja".
- Potwory nakladaja stany (`monster_conditions.csv`, 61 potworow, wartosci jak w Canary condition totalDamage/interval):
  pajaki i skorpiony zatruwaja, zywiolaki ognia podpalaja, nieumarli przeklinaja, wilki i bestie powoduja krwawienie...
- Pola (`world/Fields`): ogien 7x20 co 10 s przez 200 s (potem slabszy 7x10), trucizna jak `damageList(100, 5)` co 5 s
  przez 248 s, energia 25 raz, 98 s; magiczna sciana i dziki wzrost 20 s, blokuja ruch (a sciana takze wzrok).
  Pola nie powstaja w strefie ochronnej ani na polach zablokowanych; wejscie w pole naklada stan od razu (gracz -
  ostrzezenie z nazwa czaru leczacego; potwor - tylko pola wlasciciela). Runa niszczenia pol usuwa pole.
- Runy (id 400-423): pola (ognia, trucizny, energii), sciany (5 pol w poprzek kierunku), bomby (3x3), lawina i wielka kula
  ognia (promien 3), kula ognia, sopel, stalagmit, burza, deszcz kamieni, ogien duszy, pociski, swiety pocisk,
  odtrutka, paraliz, przekonanie, ozywienie zwlok, magiczna sciana, dziki wzrost. Zaklinane czarami jak w Tibii
  (poziom, mana, ilosc) i sprzedawane u magow wszystkich miast i w obozie (`shops.csv`). Runa celuje w cel, a bez celu -
  w pole przed postacia.
- Rysowanie (`GameScreen.drawFields`): pola to petla klatek efektow OTSP (plomien, energia, kaluza szlamu, magia, ziemia).
- Online: serwer liczy stany i pola w `GameSession.update` kazdego gracza; klient ich nie dostaje (brak synchronizacji
  pol i stanow do innych graczy - do zrobienia).

## Harmonia mnicha (Z logika, S serwer, C HUD)

Jak w Tibii; liczby z serwera Canary (`player.cpp` buildHarmony/spendHarmony/getHarmonyBonus/updateSerenityState,
`spells.cpp` postCastSpell, `combat.cpp` harmonyHeal, skrypty czarow mnicha). Stan: `vocation/Harmony`
(`PlayerCharacter.harmony()`), logika w `SpellSystem` (`afterMonkCast`, `updateSerenity`).

- **Harmonia 0-5.** Czar budujacy (kolumna "mnich" = `B` na koncu wiersza spells.csv, `MonkRole.BUILDER`) daje
  +1 punkt; wydajacy (`S`) zuzywa wszystkie, mnozy obrazenia (i leczenie Zbiorowego uleczenia ducha) przez
  `1 + B * 2^(h-1) / 100`, B = 8 (x2,28 przy 5 punktach), i skraca odnowienie czarow budujacych o 2 s za punkt.
  Kazde zbudowanie i wydanie leczy (poziom/5 x2..x2,3, +5% za punkt, min. 10..25) mnicha albo najbardziej
  rannego widocznego czlonka druzyny, jesli ma mniej zdrowia niz mnich.
- **Cnoty** (poz. 20, 210 many, 10 s miedzy zmianami - druga grupa "virtue" jak w Canary, grupa glowna wsparcie 2 s, jedna aktywna): harmonii
  (B +4, w spokoju +8, kazdy spender od razu oddaje 1 punkt), sprawiedliwosci (umiejetnosc walki piesciami
  x1,15 / x1,30 w spokoju, `PlayerCharacter.effectiveSkill` - takze ciosy wrecz w `CombatFormulas`),
  wytrwalosci (leczenie czarami mnicha x1,35 / x1,70, bez leczenia z harmonii - jak ORIGIN_HARMONY w Canary).
  Premie dla druzyny z Tibii nie sa zrobione.
- **Spokoj (Serene).** Mnich jest spokojny, chyba ze widzi czlonka druzyny (to samo pietro, 8 x 6 pol) i
  jednoczesnie na 8 polach wokol stoi co najmniej 6 stworzen (potwory i inni gracze). Offline zawsze spokojny.
  Liczone w `GameSession.update`; na serwerze druzyne i graczy obok daje `ServerWorld.monkAllies`.
- **Skupienie spokoju** (`utamo tio`, poz. 150, 500 many, 10 min): pelna harmonia, odnowienia spenderow od
  zera, spokoj wymuszony na 7 s. **Skupienie harmonii** (`utevo nia`, poz. 275, 500 many, 2 min): pelna harmonia.
- **Smierc** zeruje harmonie (cnota zostaje), **wylogowanie** nie: pole `monk=punkty,CNOTA,spokoj,wymuszenieMs`
  w zapisie postaci (bez podbijania wersji CharacterCodec; brak pola = stan poczatkowy). To samo pole idzie
  w komunikacie SELF, wiec klient online widzi harmonie, cnote i spokoj serwera.
- **Harmonia nad postacia** (jak w Tibii, `HarmonyPips`): pod paskami zycia/many mnicha rzadek 5 malych
  kwadratowych kropek (pelne zlote, puste ciemne), przy spokoju obok niebieski rombik; czaszka PvP schodzi pod
  kropki. Inni gracze widza to z serwera: ostatnie pole wiersza `PL` (`-` = nie mnich, `3` = 3 punkty, `3S` =
  3 punkty i spokoj; starsi klienci pomijaja pole, wersja protokolu bez zmian). Kule harmonii w HUD zostaja -
  tylko tam widac cnote i tylko one otwieraja wybor cnoty/opis po dotknieciu. Podglad: `-Dpixel.open=monks`.
- **Czary mnicha** (role i liczby z Canary; ataki fizyczne, wzor `moc * piesci/100 * atak broni/10 + poziom/5`
  (`SpellSystem.monkPower`, bez broni atak 7), rozrzut +-10%). Dodatek Pixela: premia nowicjusza
  `max(0, 40 - poziom) * 1` (`Harmony.noviceBonus`) - przy piesciach 10 sam wzor Canary daje na 8 poziomie ~4
  obrazen, a rycerz Pixela ma brutalne uderzenie juz od 8 poziomu; od 40 poziomu premii nie ma.
  `MonkBalanceSimulationTest` porownuje mnicha z rycerzem na tych samych polowaniach (8, 30, 100, 200;
  rotacja: buildery, przy 5 punktach spender) i pilnuje, by mnich nie byl wolniejszy niz 1,5 raza:

  | Czar (Tibia) | Klucz | Poz. | Mana | Odn. | Moc | Rola |
  |---|---|---|---|---|---|---|
  | Szybki cios (Swift Jab) | swift_jab | 1 | 3 | 2 s | 12 | B |
  | Starcie tygrysa (Tiger Clash) | tiger_strike | 1 | 3 | 8 s | 15 | S |
  | Podwojny cios (Double Jab) | double_jab | 14 | 30 | 4 s | 50 | B |
  | Wielkie starcie tygrysa | greater_tiger_clash | 18 | 50 | 8 s | 44 | S |
  | Mistyczne odepchniecie (zasieg 7) | mystic_repulse | 30 | 150 | 20 s | 72 | B |
  | Grad ciosow (obszar 1) | flurry_of_blows | 35 | 110 | 4 s | 65 | B |
  | Zamaszyste powalenie (obszar 1) | sweeping_takedown | 60 | 210 | 8 s | 72 (48 x1,5) | S |
  | Lancuch pokuty (3 cele, promien 3) | chained_penance | 70 | 180 | 4 s | 74 | B |
  | Wielki grad ciosow (obszar 2) | greater_flurry_of_blows | 90 | 300 | 16 s | 100 | B |
  | Potezny podbrodkowy | forceful_uppercut | 110 | 325 | 60 s | 130 | B |
  | Druzgocacy nokaut | devastating_knockout | 125 | 210 | 24 s | 62 | S |
  | Wybuch ducha (6 celow, promien 3) | spiritual_outburst | 300 | 425 | 24 s | 42 | S |
  | Uleczenie ducha (Spirit Mend) | spirit_mend | 80 | 210 | 1 s | 0,2 poz + 10 mag + 61 | - |
  | Zbiorowe uleczenie ducha (promien 5) | mass_spirit_mend | 150 | 250 | 8 s | 0,2 poz + 8 mag + 44 | S |

  Zostaly czary Pixela Wewnetrzny spokoj (12) i Gleboka medytacja (40). Obszary kierunkowe Tibii sa tu kolem
  wokol mnicha, lancuchy - najblizsze cele w promieniu (kolumna "ilosc" = liczba celow). Uproszczenia: brak
  odepchniecia (Mystic Repulse), premii za umiejetnosc >110 (Sweeping Takedown), drugiego uderzenia Wybuchu ducha
  i progu 5-10 obrazen Starcia tygrysa. Inkantacje jak w Tibii (`exori infir pug`, `exori gran nia`, `utori virtu`...).
- **HUD:** 5 kul (rysowane ksztaltami, `client/ui/HarmonyHud`), za nimi znak cnoty, niebieska poswiata = spokoj.
  Desktop: drugi wiersz gornego paska, na lewo od ikon stanu; telefon: pod ramka za ikonami stanu. Dotkniecie
  kul: opis w dzienniku i okienko wyboru cnoty (rzuca czar cnoty). Pasek automatyczny mnicha: leczenie, czar
  budujacy, wydajacy, obszar. Podglad: `-Dpixel.harmony=N -Dpixel.virtue=HARMONY -Dpixel.open=virtues`.

## Lowienie i umiejetnosci (Z logika, C UI)

- `gathering/FishingSystem`: najblizszy kafelek wody w zasiegu 3 z linia wzroku; proba co 1,5 s;
  szansa `min(70%, 10% + 0,6% * umiejetnosc)`. Jak w Tibii (0.217): wedka w rece (pole broni, slot WEAPON
  w items.csv; w rece walczy sie piesciami) albo w plecaku i robaki w plecaku - robakow sie nie zaklada (slot NONE);
  kazde zarzucenie zuzywa 1 robaka z plecaka. Bez wedki / bez robakow - `FishingResult.NO_ROD`, `NO_BAIT`.
  Stare zapisy: robaki z pola amunicji trafiaja przy wczytaniu do plecaka (pelny - do depozytu).
  Robak 1 gp u kazdego kupca z plecakami (tam tez wedka), postac startowa ma 10 robakow w plecaku. Zlowiona
  ryba, ktora nie zmiesci sie w plecaku = robak
  zostaje; kafelek po zlowieniu odpoczywa 2 min (stan ulotny); perla 3% od umiejetnosci 30.
- Ryby biomow (`data/fishing.csv`): Mrozne Pustkowia - lodowy dorsz (35%, od umiejetnosci 15),
  Dzungla Selvaran - wegorz bagienny (35%, od 20; lowiskiem sa tam bagna), Pustynia Qasirah - karp z oazy
  (30%, od 25). Pozostale zlowienia to zwykla ryba. Ryby biomow dluzej syca i maja wyzszy skup w lokalnym sklepie.
- Umiejetnosci (kolejnosc okna Skills z Tibii, `Skill.values()`): Piesci, Maczuga, Miecz, Topor, Dystans,
  Tarcza, Wedkarstwo, Magia. Bron ma w items.csv kolumne `weaponType` (SWORD/AXE/CLUB/FIST/DISTANCE/WAND,
  "-" = nie bron): obrazenia liczy i trenuje umiejetnosc tego rodzaju (`CombatFormulas.weaponSkill`), czary
  broni rycerza - umiejetnosc trzymanej broni. Rycerz zaczyna z mieczem, toporem i maczuga na tym samym poziomie.
  Stare zapisy bez `skill.AXE` / `skill.CLUB` dostaja poziom i postep miecza (hojnie; po kluczach, bez wersji).
- Stamina (`character/Stamina`, TibiaWiki): 42:00, polowanie (doswiadczenie z potworow) zuzywa 1 minute na
  minute; powyzej 39:00 premium 150% exp, od 14:00 w dol 50%, przy 0 (strefa EMPTY) brak exp i brak lupu jak
  w Tibii: cialo zabitego potwora puste (bez zlota i przedmiotow), wpis "brak staminy - bez expa i łupu"
  (`CombatSystem.onMonsterDeath`, strefa sprzed zuzycia minuty - ostatnia minuta jeszcze daje exp i lup). Lup
  lezy w ciele zabojcy, wiec liczy sie stamina zabojcy; czlonek druzyny bez staminy traci tylko swoj udzial exp
  ("bez expa"). Wyprawa na bossa: uczestnik bez staminy ma wyprawe zaliczona, ale nie dostaje osobistego lupu
  (`BossQuestRuntime.Hero.noStamina`). Offline odnowienie o 1/3 szybsze niz w Tibii: po 10 min 1 minuta na
  2 min 15 s do 39:00, powyzej 1 na 4 min 30 s (39:00 -> 42:00: 13 h 40 min, 0:00 -> 42:00: 101 h 25 min;
  sen w lozku tak samo). Zapis w osobnym polu `stamina=minuty,czasZapisu`
  (bez podbijania wersji; brak pola = 42:00), odnowienie przy logowaniu (`Stamina.onLogin`: serwer przy
  wczytaniu postaci, gra lokalna w `PixelGame.playLocal`). Na serwerze stamina liczy sie w sesji gracza
  (`CombatSystem` i podzial exp druzyny w `ServerWorld`), klient dostaje ja w zapisie SELF. Okno umiejetnosci
  (`SkillSheet`): wiersz Stamina hh:mm z paskiem (zielony premia, zolty, pomaranczowy < 14:00, czerwony 0).
- Krzywe umiejetnosci jak w Tibii (TibiaWiki "Formulae"; `Skills.triesForNext(profesja, skill, poziom)`):
  przejscie L -> L+1 kosztuje `A * b^(L - c)` prob (czesc calkowita), c = 10 (magia 0), maks. 200.
  A: piesci/maczuga/miecz/topor 50, dystans 25, tarcza 100, wedkarstwo 20, magia 1600 (punktow many).
  b (magia / bron wrecz / piesci / dystans / tarcza / wedkarstwo): Knight 3.0/1.1/1.1/1.4/1.1/1.1,
  Paladin 1.4/1.2/1.2/1.1/1.1/1.1, Sorcerer 1.1/2.0/1.5/2.0/1.5/1.1, Druid 1.1/1.8/1.5/1.8/1.5/1.1,
  Monk (vocations.xml Canary 15.x) 1.3/1.5/1.1/2.0/1.2/1.1. `Skills` zna profesje (tworzy je `PlayerCharacter`);
  `new Skills()` = bez profesji (Rookgaard: 3.0/2.0/1.5/2.0/1.5/1.1).
- Proby (`Skills.train`, mnozone przez etap serwera; `addTries` = surowe): kazdy atak bronia/piescia/rozdzka
  i strzal w potwora, takze chybiony, +`Balance.HUNT_TRY_FACTOR` (30) (`CombatSystem.update`), kazdy atak potwora,
  ktory tarcza moze odeprzec (wrecz dowolnego zywiolu albo fizyczny pocisk, `CombatSystem.blockable`),
  +`Balance.BLOCK_TRY_FACTOR` (90) tarczy - tylko z tarcza w rece (mnich bez tarczy), rzut wedka +1 (udany czy nie),
  kazdy punkt wydanej many na czar (takze tworzenie run) +HUNT_TRY_FACTOR magii (`SpellSystem.trainMagic`, bez
  wspolczynnika profesji - profesja decyduje tylko o krzywej). HUNT_TRY_FACTOR to stala kompensacji: potwory
  gina w 1,5-3 s (2-4 ciosy na zabicie), a exp rosnie z etapami jak na RubinOT, wiec przy 1 probie na atak rycerz 100
  mial z polowania bron ~51 / tarcze ~45, a czarodziej magie ~40. Z czynnikiem (`HuntBalanceSimulationTest#skillsFollowTheLevel`,
  polowanie od poziomu 1): rycerz 50/100/200 bron 64/82/97, tarcza 59/77/96; paladyn dystans 68/83/101; czarodziej
  i druid magia 54/72/94 - typowo dla poziomu (rycerz 70/65 i 85/80, magia 50 i 68, tolerancja 7). Postac tworzona
  od razu wyzej (start offline 300, `-Dpixel.startLevel`) dostaje te same umiejetnosci z tabel
  `CharacterFactory.KNIGHT_SKILLS/PALADIN_SKILLS/MONK_SKILLS/MAGE_SKILLS` (wyniki symulacji do poziomu 600, dalej
  ekstrapolacja; poziom 300: rycerz 106/105, paladyn dystans 110 tarcza 105, mag magia 114, mnich piesci 109). Manekin
  (`GameSession.updateTraining`): co 2,5 s 15 prob broni i 20 tarczy (z tarcza), ok. 2/3 tempa polowania (sekcja Manekiny). Brak treningu na
  potworach ponizej progu `poziom > 4 * poziom potwora + 20` (`CombatSystem.isTrivial`).
- Etapy serwera (`Balance.EXP_STAGES` / `SKILL_STAGES` / `MAGIC_STAGES`, jak "stages" na OTS, np. RubinOT;
  wspolne dla gry offline i serwera): exp za potwory wg poziomu postaci 1-8 x50, 9-50 x80, 51-100 x60,
  101-150 x40, 151-200 x30, 201-300 x15, 301-400 x12, 401-500 x10, 501-600 x7, 601-700 x6, 701-800 x5,
  801-900 x4, 901-1000 x3 (dalej x2/x1.5/x1.2 - poza limitem 1000); umiejetnosci wg biezacego poziomu
  umiejetnosci 1-80 x10, 81-100 x7, 101-120 x4, 121+ x2; magia 0-80 x10, 81-100 x7, 101-120 x4, 121-130 x3,
  131+ x2. Mnoznik exp dziala w `CombatSystem.killExperience` (zabicie solo i podzial w druzynie - kazdy wg
  swojego poziomu, przed stamina i premia wypoczecia) oraz na nagrodach liczonych z exp potwora (zadanie dnia,
  zlecenie lowcy, wyzwanie). Stale nagrody questow (`quests.csv`), kara smierci (procent posiadanego exp)
  i progi poziomow nie sa mnozone. Okno umiejetnosci pokazuje "Poziom 20 (exp x80)", okno postaci takze
  mnoznik kazdej umiejetnosci.
- Zgodnosc zapisow: zapis trzyma poziom i proby w biezacym poziomie; po zmianie krzywej poziomy zostaja,
  a pasek postepu jest obcinany do 99% (nadmiar prob awansuje przy nastepnej probie).

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

## Wzgorza goblinow, smokow i hydr (Z dane, generator `tools/mapgen/hills.py`, 2026-11-12)

- Trzy krainy: **Wzgorza Goblinow** (kontynent glowny, ziemia darmowa; Nory Goblinow -1..-3, poziomy 8-50), **Wzgorza
  Smokow** (pustynia przy Qasirah; Smocze Pieczary -1..-4, poziomy 50-900, najsilniejsze smoki na dnie) i **Wzgorza
  Hydr** (wyspa wschodnia, tylko statkiem - rejs wymaga premium albo promocji; Legowiska Hydr -1..-5, poziomy 22-505).
- Prostokaty powierzchni: goblinow (573,406)-(632,489), smokow (79,247)-(156,331), hydr (886,376)-(965,455); podziemia
  w prostokatach pieter (`regions=` w map.properties, nazwy `Nory Goblinow`, `Smocze Pieczary`, `Legowiska Hydr`).
- `build_one`: ksztalt (suma kol wzdluz kregoslupa + szum), miejsce (`find_origin`, bez miast, drog, twierdz, zamku
  Warownia Gorvalda x 750-814, y 336-390), grzbiety gorskie z dolinami i 3 przeleczami, stawy, wejscia (jaskinie u stop
  grzbietow, 3-4 na krainę), spawny powierzchni wg drogi od swiatyni (najslabsze przy przelecz), podziemia (`floor_net`:
  drzewo rozpinajace pokoi, petle, krete waypointy, slepe liczki, 2 schody w dol), skrzynie, regiony, `clear_surface`, stamp.
- Nowe style jaskin w `features.py`: `goblin_den/mine/hold`, `fen_cave`, `flooded`, `moss_grot`, `venom_pit`,
  `serpent_lair`, `dragon_den`, `ember_den`, `dragon_hoard` (`WATER_STYLES`, nowy `LAVA_STYLES`).
- Potwory: `goblin_scrounger` 14, `goblin_knifer` 24, `goblin_warchief` 36, `marsh_lizard` 88, `lizard_hexer` 120,
  `bog_hydra` 190, `bog_naga` 250, `venom_hydra` 310, `stone_gorgon` 400, `dragon_whelp` 50, `scale_dragon` 140,
  `crag_dragon` 260 (nowe, w `HAND_PLACED`, wiec `route_levels` ich nie podmienia) oraz istniejace goblin, goblin_shaman,
  cave_troll, dune_drake, magma_drake, bone_dragon i inne. Grafika z OTSP (`otsp_import.py`: TINTED i STOCKY).
- `route_levels`: zasada luzu trasy (spawn w 8 polach od drogi do slabszego spawnu dostaje najwyzej slabszy + 25%) i
  limity pasm drogowych dzialaja tez tu; spawny przydzielane wg `route_dist`, zeby slabsze lezaly blizej wejsc.
- Wyspa hydr: tunel pod morzem (`generate_continent.tunnel`) konczy sie na sasiednim kawalku ladu, ktory z wyspa
  laczy po skosie las (gra pozwala przejsc miedzy drzewami po skosie). `hills.sever_diagonals` zasypuje najwezsza
  warstwe BFS (8 kierunkow) glazami, zeby wyspa byla dostepna tylko statkiem.
- Nazwy miejsc: `Region.displayName()` (`Messages.data("place", nazwa, nazwa)`); wiersze `place;<nazwa PL>;name;<tekst>`
  w `i18n/data_en.csv` i `data_es.csv`. Uzywane w `GameSession.checkRegion/signText`, `BestiaryCard.habitats`, `MonsterManager`.
- Testy: `HillsTest`; sceny gui-regression `hills_goblin_*`, `hills_dragon_*`, `hills_hydra_*` (wzorce dopisze koordynator).

## Obozy potworow i Miasto Rogatych (Z dane, generatory `camps.py` i `minotaur.py`, 2026-11-12)
- Obozy dziczy (`camps.py`): nieregularna plama z palisada/kamiennym pierscieniem/ruinami, 2-3 bramy, wnetrze z
  ogniskiem, chatami i namiotami. Odstepy: `MIN_ANY`=50, `MIN_FAMILY`=70, od znanych wejsc `MIN_FIXED`=55; omijaja
  prostokaty `HOLD` (zamek, wzgorza, twierdze). Test: `CampsTest`.
- Miasto Rogatych (-7, region `Miasto Rogatych`, poziomy 20-100): arena i sala tronowa w centrum, ulice, domy,
  zaulki, jaskinie boczne. Zejscia: dwa 6-pietrowe kreta jaskinie `Labirynt Rogatych` (-1..-6) z gor.
  Potwory: horn_scout/raider/bowman/guard/mystic/sapper/gladiator/oracle/champion, boss horn_overlord (poziom 100).
  Test: `MinotaurCityTest`. Nic nie tworzy drogi pieszej do ziem premium.

## Poziomy dostepu i polecenia GOD (Z logika `admin/*`, S serwer, C znacznik i okno)

- **Poziomy** (`AccessLevel`): `PLAYER`, `TUTOR`, `GAMEMASTER`, `GOD`. Serwer liczy poziom z nazwy postaci (`Moderation.level`): `admins.txt`
  (`Imie` = game master, `Imie:tutor`, `Imie:god`) i `gods.txt` (`Imie` = GOD), wyzszy wygrywa. Konto = imie postaci, wiec operator nadaje GOD-a
  imieniem po zwyklej rejestracji; w repozytorium nie ma zadnego konta, hasla ani tokenu. Konsola serwera: `god Imie`, `ungod Imie`, `admin Imie`
  (game master), `reload` (ponowne wczytanie plikow, bez restartu). `PlayerCharacter.accessLevel()` jest tylko w pamieci (nie ma go w zapisie
  postaci); serwer odswieza je co takt.
- **Polecenia** (`GodCommands.COMMANDS`: tabela z poziomem, aliasami, skladnia i kluczem opisu): parser czatu `ChatCommands` -> `GOD SUB [arg]`
  (albo `ADMIN SUB` dla aliasow `/broadcast`, `/raid`, `/save`, `/shutdown`). `GodCommands.execute(Context, pola)` sprawdza poziom z `Context.level()`
  (hosta, nie klienta), wykonuje polecenie na `GameSession` i zapisuje wynik w `Context.audit` (serwer: `admin.log` z czasem, imieniem i poziomem;
  odmowy tez). Host serwera: `ServerWorld.GodHost`, gra lokalna: `GameScreen.LocalGodHost`. Teksty w `Messages` (`god.*`, PL/EN/ES).
- **Ghost / godmode**: `PlayerCharacter.isGhost()` (niewidzialnosc dla potworow przez `isInvisible`, wiersz `PL` pomija ducha dla nie-GOD-ow,
  tez ATTACK/INSPECT/lista znajomych) i `isGodMode()`; `damage()` zwraca 0. Oba tylko dla GOD-a (utrata poziomu zdejmuje je).
- **Klient**: komunikat `ACCESS poziom duch tryb_boga` (tylko wyswietlanie), znacznik `[GOD]` nad imieniem (zloty; GM niebieski, tutor zielony),
  pole 17 wiersza `PL` ze znacznikiem innych graczy, okno `/komendy [strona]`.
- **Gra lokalna**: `LocalGod` (`-Dpixel.god=true`, `PIXEL_GOD=1`, plik `god.enabled`); domyslnie wylaczone, nigdy online.
- Opis dla wlasciciela: `analizy/komendy-god.md`. Testy: `GodCommandsTest`, `GodNetworkTest`, `ChatCommandsTest`.
## Ród pięciu smoków i czary potworów (Z dane, 2026-11-12)
- Smoki od najsłabszego: `emerald_dragon` (zielony, poz. 205, 3000 HP), `hoarfrost_dragon` (lodowy, 240, 4500), `coal_dragon`
  (czarny, 320, 6600), `dune_drake` (złoty, 410, 8900) i `pearl_dragon` (biały, 600, 21000, nie boss: ma wpis w bestiariuszu).
  Wszystkie: zachowanie MELEE (podchodzą i gryzą fizycznie), zasięg = najdalszy czar. Test: `DragonFamilyTest`.
- `data/monster_spells.csv`: `potwor;czar;tryb;obszar;typ;min;max;szansa;zasieg;dodatek`. Tryb WAVE (przed potworem, obrót do celu
  jak `SpellSystem.aimDirection`), BALL (pocisk i obszar wokół celu), SELF (wokół potwora). Dodatek: stan (`BURNING:60-110:4000:60`)
  albo `SLOW:procent:ms`. Rzut: `MonsterSpells.plan` (kształt `SpellArea`, linia wzroku), `CombatSystem.planSpell/showSpell/
  applySpellEffects`; odmiany Kuźni mnożą obrażenia jak ciosy. `castMonsterSpell` rzuca czar bez losowania (testy, podglądy).
- Rzadki spawn: `RareSpawns.standard()` = 15 promili na odrodzenie smoka (klasa Dragon, poz. >= 100) przy graczu do 32 pól,
  przerwa 1 h, jeden żywy; ziarno testów: `MonsterManager.setRareRandom`. Bez rejestru z `pearl_dragon` albo przy < 50 spawnach wyłączony.
- Spawny stałe: `tools/mapgen/dragon_spawns.py` (na gotowym świecie: `python3 tools/mapgen/dragon_spawns.py`; idempotentny).
- Pasek życia dużych potworów: `OverheadLayout`. Światło: `Lighting.BASE_PLAYER_GLOW` 2, pochodnia 7.

## Planowane

 fishing, NPC, zadania, handel, bank w UI, PvP,
serwer i synchronizacja - patrz [TODO.md](../TODO.md).

## Style ataku potworów (Z logika, C grafika, 2026-11-12)

Wzorzec: Tibia/Canary (`attacks`, `defenses`, `summons`, `runonhealth`, `targetdistance`). Analiza i tabela wszystkich potworów:
`analizy/potwory-ataki.md`.

* **Profil** (`MonsterProfiles`, `data/monster_behaviour.csv`): `potwor;archetyp;dystans;pocisk;ucieczka`. Archetypy: `MELEE`, `DISTANCE`,
  `CASTER` (czary w cel), `AREA`, `SUMMONER`, `MIXED`, `SUPPORT`. Dystans 0 = podchodzi i bije wręcz; N >= 2 = trzyma N..N+1 pól,
  cofa się przy bliższym graczu, atak podstawowy to pocisk (`pocisk` = grafika z `assets/art/missiles`). Ucieczka nadpisuje
  `fleeHpPct` z `monsters.csv`; po wyleczeniu o 25 pp ponad progiem potwór wraca do walki.
* **Czary** (`MonsterSpells`, `data/monster_spells.csv`, 11 kolumn: `potwor;czar;tryb;obszar;typ;min;max;szansa;zasieg;dodatek;pocisk`):
  `WAVE`/`BALL`/`SELF` z kształtami `SpellArea`, `BOLT` (pocisk w jeden cel, obszar `POINT`), `HEAL` (siebie, gdy HP < 70%),
  `HASTE` (`HASTE:procent:ms`), `SUMMON` (`SUMMON:potwor:limit`). Dodatek `SLOW:procent:ms` = paraliż, `STAN:min-max:odstep:szansa`.
  Zasięg ataku potwora to większy z `range` i zasięgu jego czarów (`MonsterManager.reach`).
* **Przywołańcy** (`MonsterManager.summonMinion`): limit naraz na pana, przerwa 6 s, pomocnik poluje na gracza, znika ze śmiercią pana lub
  gdy traci cel, nie odradza się, nie daje łupu ani doświadczenia (`Monster.isMinion`).
* **Walka** (`CombatSystem.monsterAttacks`) jest wspólna dla offline i serwera. Pociski mają w zdarzeniu efekt `TYP/pocisk`
  (`CombatEvent.withMissile`), klient dobiera grafikę z nazwy.
* **Online** (`ServerWorld.monsterAttack`): czar obszarowy trafia każdego gracza w obszarze (`CombatSystem.monsterSpellHit`), a efekty
  (pociski, obszary, blask przywołania i przyspieszenia) trafiają do zdarzeń wszystkich graczy w zasięgu widoku (`EV`).
* Strojenie: obrażenia czarów liczone z `attackMax` potwora (cel ≈ 1,15× przy 25%, obszar 0,7-0,9×); poziomy i spawny bez zmian.
* Podgląd: `-Dpixel.mobDemo=spear|caster|area|summon|flee`; testy `MonsterStyleTest`, `MonsterAttackNetworkTest`.

## Zwrot i chód potworów (`Monster.facing4`, `CreatureFrames`, `TileArt.monster`)

- Kierunek: `Monster.facing()` ustawia ruch (`moveTo`), rozglądanie się bezczynnego potwora i atak
  (`MonsterManager` wywołuje `Monster.lookAt(cel)` w chwili ciosu lub czaru, o ile potwór nie jest w połowie kroku).
  Na ekranie liczą się 4 strony: `Direction.cardinal()` (skos patrzy na wschód albo zachód), a `Direction.toward`
  wybiera oś o większej odległości (przy remisie poziomo).
- Online serwer wysyła tylko pozycję, typ i % zdrowia (`RemoteEntity`), więc klient wylicza kierunek z różnicy pozycji
  w `MonsterManager.syncRemote` (skok dalej niż 1 pole to teleport, bez zmiany kierunku). Atak nie jest widoczny w klatce,
  dlatego `MonsterManager.faceAdjacent` obraca stojącego potwora (nie w kroku) twarzą do gracza, gdy stoi obok niego.
- Klatka: `TileArt.walkFrame` daje 0 (postój) gdy potwór stoi, a w marszu 1 albo 2 (zmiana w połowie pola i na kolejnym
  polu). `CreatureFrames.pick` wybiera stronę plików (dół, góra, lewo, prawo); bez grafiki strony: lewo/prawo odbija
  drugą z tych stron, a bez żadnej odbity przód dla zachodu (tył zostaje przodem). `TileArt.monster(klucz, kierunek,
  klatka, odbicie)` zwraca region i znacznik odbicia.
- Grafiki: `assets/art/monsters/klucz.png` (przód, postój), `klucz_walk1|2.png`, a dla pozostałych stron
  `klucz_up|left|right.png` i `..._walk1|2.png` (32 px, ta sama skala co przód, osobny atlas kierunków w `TileArt`).
  Wszystkie 136 potworów są z OTSP i mają komplet (test `MonsterArtAndNamesTest`). NPC mają jedną klatkę (stoją w miejscu),
  więc nie obracają się; pupile i postacie graczy mają własną ścieżkę (`TileArt.character`).

## Questy fabularne, tabliczki i dziennik (Z logika, C UI, G generator `tools/mapgen/story_quests.py`)

- Misje: `quests.csv` ma teraz typ `VISIT` (cel = nazwa regionu, ilosc 1); `QuestSystem.onEnter` zalicza go po wejsciu do regionu.
- 13 questow (`QUESTS` w `story_quests.py`): kazdy to `<klucz>_m1` (VISIT), `_m2`, `_m3` (KILL) i skrzynie z wyborem nagrody otwierane po `_m3`
  (`reward;grupa;quest:<klucz>_m3` w `star_quest.csv`, znacznik `rcg_<grupa>`). Dane CSV/i18n generuje `python3 tools/mapgen/story_quests.py data`
  miedzy znacznikami `# BEGIN story_quests`; `quest_lines.csv` opisuje linie dla dziennika.
- Bramy: `QuestGate` ma wymagania `quest:`, `boss:`, `flag:` i `party:N` (liczba graczy online w druzynie, `PlayerCharacter.partyOnline` odswiezane
  przez `GameSession`). Pole `gate` w tabeli `QUESTS` naklada wymaganie na wejscie questu; ekstremalny `crown` wymaga 3 graczy.
- Tabliczki: `ChestSigns` liczy dla kazdej skrzyni questowej jedno pole obok (bez przejsc, drzwi, bram, obiektow, pulapek); `GameSession.useAt` czyta tekst
  (`chest_sign.*`). Rysuje je `GameScreen.drawChests`.
- Dziennik: `QuestJournal.build` zwraca wpisy (w toku, nowe, ukonczone), `Hud.rebuildJournalMenu` rysuje liste i szczegoly. `-Dpixel.quests` ustawia stan do zrzutow.
