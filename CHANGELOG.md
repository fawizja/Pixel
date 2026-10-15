# Changelog

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
