# Changelog

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
