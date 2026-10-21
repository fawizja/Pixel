# TODO

Legenda: [x] zrobione i zweryfikowane, [~] czesciowo, [ ] do zrobienia.

## Etap 1 - Audyt repozytorium i konfiguracja
- [x] Audyt: repozytorium bylo puste (brak commitow), projekt utworzony od zera
- [x] Gradle 8.14.3 (wrapper), moduly shared / core / desktop / android, wersjonowane zaleznosci
- [x] CI GitHub Actions: testy, kompilacja, APK debug jako artefakt

## Etap 2 - Pierwsza uruchamialna wersja libGDX
- [x] Klient libGDX 1.13.5, launcher desktop (uruchomiony i sprawdzony zrzutem ekranu)
- [~] Launcher Android (kod skompilowany wzgledem Android 14 + gdx-backend-android; APK budowany tylko w CI)
- [x] Ekran wyboru profesji i imienia
- [x] Pauza / utrata fokusu / wznowienie z autozapisem

## Etap 3 - Renderowanie 2D, mapa, kamera, ruch, kolizje
- [x] Mapa kafelkowa z warstwami i legenda, generowana grafika pikselowa (atlas, NEAREST)
- [x] Kamera z przyciaganiem do pikseli, ExtendViewport (rozne proporcje ekranu), UI skalowane wg DPI
- [x] Ruch w 8 kierunkach z interpolacja, kolizje, bez scinania rogow
- [x] Joystick, przyciski, klawiatura
- [~] Duzy swiat 160x232 dziala bez chunkow (rysowane tylko widoczne kafelki); chunki przy wiekszym swiecie

## Etap 4 - Postac, profesje, HP, MP, poziomy, doswiadczenie
- [x] Start: poziom 8, 10 000 gp w plecaku (nie w banku), zestaw profesji, swiatynia Varden
- [x] Krzywe 8..1000 bez przepelnien, regeneracja, szybkosc z limitem
- [x] 4 profesje o roznych profilach
- [x] Czary i umiejetnosci profesji (etap 7), zasady PvP (etap 13)

## Etap 5 - Plecak, przedmioty, monety, ekwipunek
- [x] Plecak 20 slotow, atomowe operacje, laczenie stosow, przenoszenie, zamiana
- [x] Ekwipunek z wymaganiami profesji i poziomu
- [x] Wyrzucanie i podnoszenie, lista ignorowanych przy autoloocie
- [x] Gold / Platinum / Crystal, wymiana, platnosci z reszta, bank (logika)
- [x] Uzywanie przedmiotow (mikstury, jedzenie) - etap 7
- [x] Okno banku w UI - etap 12

## Etap 6 - Potwory, AI, walka, lupy
- [x] Rejestr potworow (data/monsters.csv, data/loot.csv): 10 typow + boss, odpornosci, 6 zachowan
- [x] Spawny (maps/varden/spawns.csv), odradzanie, brak pojawiania sie na oczach gracza
- [x] AI: aggro z linia wzroku, poscig (BFS), ucieczka i osaczenie, dystans dla strzelcow, leczenie sojusznikow,
      smycz i powrot do spawnu z leczeniem, regeneracja poza walka, boss (jednorazowe leczenie, szal)
- [x] Strefy ochronne (miasto): brak walki, potwory nie wchodza
- [x] Walka: cel (przycisk, dotkniecie, automatyczna odpowiedz), wrecz / dystans / magia, pancerz, tarcza,
      odpornosci, szybkosc ataku, postawa obronna
- [x] Smierc gracza (5% exp, nigdy ponizej poziomu startowego, odrodzenie w swiatyni)
- [x] Doswiadczenie z malejacym zyskiem za zbyt slabe potwory, lupy z autolootem lub na ziemie
- [x] Symulacje walk profesji (poziom 8 i 30), test "godzina na wilkach", test wysokiego poziomu
- [x] Siatka przestrzenna, symulacja tylko w promieniu 20 kafelkow od gracza
- [x] Klient: sprite potworow, paski HP, obwodka celu, liczby obrazen, panel celu, CEL / TARCZA
- [x] Mikstury i czary leczace (etap 7)
- [x] Cialo potwora z lupem do przeszukania (UZYJ / PODNIES / dotkniecie obok), znika po 3 min
- [x] Dzwiek, animacja ataku (wypad i ciecie przy ciosie wrecz, efekty syntezowane, 0.18.0)

## Etap 7 - Czary, runy, mikstury, crafting
- [x] 20 czarow i 3 efekty run (data/spells.csv): leczenie, ataki na cel i obszarowe, swiatlo, przywolanie wloczni, zaklinanie run
- [x] Mana, poziom, poziom magii, czasy odnowienia czaru i grupy (atak / leczenie / wsparcie / mikstury)
- [x] Trening poziomu magii wydawana mana (magowie szybciej)
- [x] Runy: pusta runa + czar -> runy; uzycie na cel lub na siebie, wymagany poziom magii
- [x] Mikstury (data/consumables.csv) z wymaganiami profesji i poziomu, bez marnowania przy pelnym HP/MP
- [x] Jedzenie: sytosc do 20 min, podwojna regeneracja
- [x] Crafting (data/recipes.csv): 8 receptur, atomowo, koszt w gp
- [x] Klient: automatyczny pasek czarow z odnowieniem, przyciski HP/MP, ksiega czarow i rzemiosla, UZYJ w plecaku
- [x] Symulacje z botem leczacym: 10 min przy wilkach bez smierci; 100 mikstur nie wystarcza na bossa na poziomie 8
- [x] Efekty graficzne czarow (pociski, wybuchy, leczenie, ataki dystansowe)
- [x] Dzwieki czarow wg zywiolu, leczenia i awansu (0.18.0)
- [x] Wlasny uklad paska czarow (ksiega: "Ustaw pasek czarow", zapis per postac)


## Etap 8 - Fishing i umiejetnosci
- [x] Lowienie: wedka + robak (zuzywany tylko przy zlowieniu), woda w zasiegu 3, szansa 16%..70% wg umiejetnosci
- [x] Odpoczynek lowiska 2 min po zlowieniu (brak farmienia jednego kafelka), perly od umiejetnosci 30
- [x] Trening umiejetnosci tylko na potworach, ktore nie sa zbyt slabe (ten sam prog co pelny exp)
- [x] Karta postaci (dotkniecie paskow HP/MP lub P): exp, szybkosc, udzwig, sytosc, umiejetnosci z postepem
- [x] Przycisk LOW pokazywany tylko przy wodzie z wedka w plecaku
- [x] Ryby zalezne od biomu (lod, bagna, oaza): data/fishing.csv

## Etap 9 - Miasta (max 4), biomy, expowiska
- [x] Varden: swiatynia, bank, sklepy, domy, wieza, kopalnia, jezioro, pola
- [x] Swiat 160x232 (generate_world.py): Frostholm (snieg), Varden, Selvaran (dzungla, bagna), Qasirah (pustynia), drogi
- [x] Nowe kafelki biomow: lod, bagno, piaskowiec, zamarzniete i grobowcowe posadzki, sosny, palmy, kaktusy, sciany
- [x] Lochy: Lodowe Jaskinie (-1..-2), Zatopione Ruiny (-1..-2), Grobowce (-1..-3)
- [x] 12 potworow biomow (poz. 40-200) z lupami, 9 przedmiotow na poziom 40-100
- [x] Regiony z zalecanym poziomem i ostrzezeniem, odrodzenie w swiatyni miasta, migracja zapisu v3
- [x] Testy: 4 miasta ze strefami, dostepnosc wszystkich miast i lochow z Varden, kolejnosc biomow, spawny
- [x] Poziomy 230..1000: wschodni kontynent (5 krain, 17 potworow, 4 bossow, 4 progi sprzetu), most ze straznikiem (poz. 200)
- [x] Symulacje endgame z leczeniem: kazdy prog do przejscia, bossowie tylko druzyna, bez przeskakiwania progow
- [x] Oboz za mostem: kupiec ze skupem materialow, bankier, zwiadowczyni z 9 zadaniami endgame
- [x] Kuznia endgame: 28 receptur z obsydianu, lodowych rdzeni, esencji cienia i zaru Otchlani
- [x] Ryby zalezne od biomu
- [x] Selvaran przebudowane (0.182.0): mury z wiezami i tarasami +1, swiatynia z dzwonnica, depozyt, 3 sklepy, domy z pietrem; Skalny Wawoz, Klify +1/+2, Kanaly -1, Krysztalowa Glebia -2
## Etap 10 - Pietra +5..-5, jaskinie, oswietlenie
- [x] Pietra -5..+5, schody, dziury, drabiny, liny; test dostepnosci wszystkich pieter
- [x] Ciemne podziemia, pochodnie na mapie i w plecaku, poswiata postaci
- [x] Paliwo pochodni (30 min, automatycznie nastepna), czar Poswiata, zapis stanu swiatla i sytosci
- [x] 3 lochy biomow do -5 (dziury, drabiny, miejsca na line), swiecacy mech, magma, lodowe krysztaly
- [x] 3 bossowie biomow (150, 200, 230) z unikalnymi lupami; testy drogi powrotnej i swiatla
- [x] Pietra +1..+5 poza wieza Varden: Mrozne Turnie +1..+3, Plaskowyz Qasirah +1..+2 (0.17.1)
## Etap 11 - Dzien, noc, oswietlenie swiata
- [x] Zegar swiata, fazy, plynna jasnosc, czytelna noc
- [x] Zegar w GameSession (wspolny dla logiki i grafiki, wymienny - serwer poda czas), komunikaty o zmroku i switaniu
- [x] Nocne drapiezniki (kolumna activity w monsters.csv): w nocy na powierzchni aggro +3, ataki o 20% szybsze
- [x] Ostrzezenie o pietrze liczy tylko spawny w promieniu 30 kafelkow (duzy swiat)
- [x] Czas gry z serwera (klient online zgrywa zegar)
- [x] Nocne spawny (6 grup na powierzchni, znikaja za dnia poza wzrokiem graczy)
## Etap 12 - NPC, zadania, handel, bank
- [x] 17 NPC w 4 miastach (bankier, kupiec, kaplan, przewodnik; w Varden takze czarodziejka), generowane z mapa
- [x] 5 sklepow (data/shops.csv): kupno z ilosciami, sprzedaz wszystkich sztuk, atomowo, z reszta
- [x] Zabezpieczenia ekonomii: skup ponizej ceny sprzedazy w calym swiecie, brak skupu run/mikstur/monet,
      brak zysku z receptur i zadan na przynoszenie (testy)
- [x] Bank w UI: wplata wszystkiego, wyplaty; kaplan: leczenie i zamieszkanie (miejsce odrodzenia)
- [x] 13 zadan (data/quests.csv): zabij N / przynies N, nagrody exp i gp, zapis postepu
- [x] Klient: sprite i imiona NPC, przycisk ROZMOWA, dotkniecie NPC, okno rozmowy z rolami i stronami
- [x] Dialogi slowami kluczowymi, zadania wieloetapowe, nagrody przedmiotami
## Etap 13 - PvP i reguly serwerowe
- [x] `pvp/PvpRules`: ochrona do poziomu 30, brak walki w PZ, zakaz atakowania postaci o ponad polowe slabszych
- [x] Biala czaszka (15 min) za atak na niewinnego, czerwona (6 h) za 3 nieusprawiedliwione zabojstwa w 24 h
- [x] Smierc z czerwona czaszka = caly plecak na ziemi; PvP nie daje exp; obrazenia graczom x0,5
- [x] Serwer: `ATTACK id`, czaszki w klatkach (PL, ME), stan PvP przetrwa wylogowanie
- [x] Klient: dotkniecie gracza = atak (drugie = koniec), czerwona ramka celu, kolor imienia wg czaszki
- [x] Testy zasad + symulacja pojedynkow 4x4 profesji na poziomie 50 + test przez TCP
- [x] Blokada wejscia do PZ po walce (60 s), zapis czaszek na dysku serwera (przetrwaja restart)
- [x] Czary i runy na graczy (te same zasady co atak, obszar tylko w wybrany cel)
## Etap 14 - Serwer i synchronizacja MMORPG
- [x] Modul `server` (TCP, protokol tekstowy), wspolny swiat, wielu graczy, zapis postaci na serwerze
- [x] Potwory wybieraja najblizszego gracza; atak trafia do sesji tego gracza
- [x] Testy przez prawdziwe polaczenia TCP (widocznosc, ruch, czat, podwojne logowanie, wspolne potwory)
- [x] Tryb online w kliencie libGDX: replika stanu z serwera, inni gracze, potwory, ziemia, obrazenia, czat (odbior)
- [x] Wolne pole przy logowaniu (gracze nie stoja na jednym kafelku)
- [x] Menu startowe (lokalnie / online), ekran logowania (takze Android), zapamietany adres i imie
- [x] Konta z haslem: PBKDF2-HMAC-SHA256 + sol, blokada po 5 bledach, protokol v2
- [x] Czat: przycisk CZAT / Enter; powrot do menu po rozlaczeniu
- [x] TLS: certyfikat serwera tworzony przy pierwszym starcie (poza repo), przypinanie odcisku w kliencie
- [x] Limity polaczen/wiadomosci/czatu, asynchroniczna wysylka do klientow
- [x] Kopie zapasowe katalogu serwera: zip przy starcie i co 6 h, 7 najnowszych
- [x] Zmiana hasla, usuwanie konta (polecenia w czacie), limit 5 nowych kont na dobe z jednego adresu
- [x] Przewidywanie ruchu po stronie klienta z korekta pozycji przez serwer
- [x] Protokol v3: niezmieniona klatka jako `KEEP` (mniej transferu)
- [x] Druzyny: zaproszenia, czat druzyny, wspolne doswiadczenie z premia, bez PvP w druzynie (0.19.0)
- [x] Bezpieczny handel miedzy graczami (oferty, podwojna akceptacja, atomowa wymiana) (0.20.0)
- [x] Okno handlu w HUD (0.20.1)
- [x] Gildie: zalozenie za 50 000 gp, zaproszenia, wyrzucanie, czat gildii, zapis na serwerze (0.21.0)
- [x] Obraz Docker serwera, instrukcja VPS (docs/SERVER.md), test obrazu w CI (0.22.0)
- [x] Prywatne wiadomosci i moderacja (bany, wyciszenia, ogloszenia, konsola serwera) (0.23.0)
- [x] Polskie litery w czcionce i pelne polskie napisy w grze (0.24.0)
- [x] Polskie nazwy przedmiotow i potworow, handel przyjmuje obie nazwy (0.25.0)
- [x] Ranking postaci (/ranking [profesja]), takze graczy offline (0.26.0)
- [x] Depozyt przedmiotow u bankiera, 60 miejsc, wspolny dla miast (0.27.0)
- [x] Lista znajomych z powiadomieniami o wejsciu i wyjsciu (0.28.0)
- [x] Rynek graczy u bankiera, sprzedaz takze offline, prowizja 2% (0.29.0)
- [x] Bestiariusz i 15 osiagniec z oknem postepu (0.30.0)
- [x] Inwazje potworow na miasta z nagrodami (0.31.0)
- [x] Zadanie dnia u przewodnika (0.32.0)
- [x] Wierzchowiec (siodlo, +60 szybkosci, widoczny dla innych) (0.33.0)
- [x] Poczta: przesylki do depozytu, takze dla graczy offline (0.34.0)
- [x] Skrzynie skarbow w lochach i na wyzynach (0.35.0)
- [x] Minimapa i duza mapa pietra (0.36.0)
- [x] Marsz do miejsca wskazanego na mapie (0.37.0)
- [x] Dotknij, aby iść (pole, NPC z rozmowa po dojsciu, cialo) (0.38.0)
- [x] Pojedynki do pierwszej krwi bez czaszek (0.39.0)
- [x] Nagroda za codzienne logowanie (seria 7 dni) (0.40.0)
- [x] Dymki czatu nad postaciami (0.41.0)
- [x] Dziennik zadan z marszem do zleceniodawcy (0.42.0)
- [x] Automatyczne lupy z cial obok postaci (0.43.0)
- [x] Licznik bossow (zyje / odrodzi sie za) (0.44.0)
- [x] Porownanie przedmiotu z zalozonym w plecaku (0.45.0)
- [x] Zwoj powrotu do swiatyni (0.46.0)
- [x] Analiza polowania: exp, zabicia i zloto na godzine (0.47.0)
- [x] Manekiny treningowe w miastach (umiejetnosci bez doswiadczenia) (0.48.0)
- [x] Pogoda: deszcz, snieg, burza piaskowa wg krainy (0.49.0)
- [x] Woznice: platne przejazdy miedzy miastami (0.50.0)
- [x] Blogoslawienstwo kaplana: polowa straty exp przy smierci (0.51.0)
- [x] Ustawienia: automatyczna mikstura zdrowia, wylaczenie pogody (0.52.0)
- [x] Podsumowanie smierci: zabojca, strata, obrazenia wg zrodla (0.53.0)
- [x] Potwory elitarne (ok. 4%, wiecej exp i lupow) (0.54.0)
- [x] 4 nowe osiagniecia, strony w oknie osiagniec (0.55.0)
- [x] Potwor dnia: +50% exp i lupu, /potwor, wiersz w analizie polowania (0.56.0)
- [x] Ogloszenie rzadkiego lupu (<= 1%) dla wszystkich graczy, znacznik potwora dnia (0.57.0)
- [x] Pupile chodzace za postacia, widoczne online (0.58.0)
- [x] Tytuly z osiagniec nad imieniem gracza (0.59.0)
- [x] Karta potwora w bestiariuszu (odpornosci od 10, lupy od 25 pokonanych) (0.60.0)
- [x] Lista graczy online /gracze (0.61.0)
- [x] Ulepszanie wyposazenia do +5 u kupca (0.62.0)
- [x] Okno polecen /pomoc z wykonywaniem po dotknieciu (0.63.0)
- [x] Lancuch zadan karawan w Qasirah, poziomy 45-170 (0.64.0)
- [x] Zadania Selvaran (85, 150) i Frostholm (110) domykajace luki poziomow (0.65.0)
- [x] Zaklecia u kowala: kradziez zycia, many, cios krytyczny (0.66.0)
- [x] Przelewy zlota miedzy kontami /przelew (0.67.0)
- [x] Zwoj nauki: +30% exp za potwory przez godzine polowania (0.68.0)
- [x] Wiadomosc dnia gildii /gildia wiadomosc (0.69.0)
- [x] Nagrody za progi poziomu 10-250 (0.70.0)
- [x] Wlasne znaczniki na mapie /znacznik, /znaczniki (0.71.0)
- [x] Ranking gildii /ranking gildie (0.72.0)
- [x] Sprzedaz calego lupu u kupca jednym dotknieciem (0.73.0)
- [x] Okno wydarzen /wydarzenia (0.74.0)
- [x] Automatyczna mikstura many w ustawieniach (0.75.0)
- [x] Okno statystyk postaci /statystyki (0.76.0)
- [x] Automatyczne leczenie czarem przed miksturą (0.77.0)
- [x] Okno ostatnich łupów /łupy (0.78.0)
- [x] Ostrzeżenia o końcu mikstur, run i amunicji (0.79.0)
- [x] Boss tygodnia z nagrodą w złocie (0.80.0)
- [x] Uzupełnianie mikstur u kupca jednym dotknięciem (0.81.0)
- [x] Wyszukiwanie przedmiotów /szukaj (0.82.0)
- [x] Historia czatu (strzałki, podpowiedź na Androidzie) (0.83.0)
- [x] Alarm niskiego zdrowia (ramka i dźwięk) (0.84.0)
- [x] Automatyczne jedzenie, strony w panelu ustawień (0.85.0)
- [x] Śledzenie zadań na ekranie (0.86.0)
- [x] Ustawienie: cel sam (0.87.0)
- [x] Procent postępu exp i umiejętności na karcie postaci (0.88.0)
- [x] 5 zadań obozu wschodniego dla poziomów 270-780 (0.89.0)
- [x] Bilans polowania z kosztem zapasów (0.90.0)
- [x] Odłożenie całego łupu do depozytu jednym dotknięciem (0.91.0)
- [x] Osiągnięcia: boss tygodnia i 10 godzin gry (0.92.0)
- [x] Znacznik bossa tygodnia na mapie (0.93.0)
- [x] Uzupełnianie run i amunicji u kupca (0.94.0)
- [x] Znacznik miejsca śmierci na mapie (0.95.0)
- [x] Boss tygodnia i miejsce śmierci w oknie /znaczniki (0.96.0)
- [x] Komunikat o odrodzeniu bossa (0.97.0)
- [x] Polecenia serwera status i save (0.98.0)
- [x] Kolor stroju widoczny dla innych graczy (0.99.0)
- [x] Ostrzeżenie o czerwonej czaszce w pobliżu (0.100.0)
- [x] Ignorowanie graczy w czacie (0.101.0)
- [x] Przyciski HP/MP używają najsilniejszych mikstur (0.102.0)
- [x] Uzupełnianie najsilniejszych dozwolonych mikstur (0.103.0)
- [x] Zlecenia łowcy u przewodnika (0.104.0)
- [x] Osiągnięcie za zlecenia łowcy (0.105.0)
- [x] Zabicia drużyny liczą się do zadań (0.106.0)
- [x] Zlecenie łowcy w śledzeniu zadań (0.107.0)
- [x] /cena - ceny kupców NPC (0.108.0)
- [x] Zwój wyzwania z falami potworów (0.109.0)
- [x] Wyzwanie na ekranie (fala, czas) (0.110.0)
- [x] Licznik wyzwań i osiągnięcie Pogromca fal (0.111.0)
- [x] /idź do NPC (0.112.0)
- [x] Podpowiedzi dla nowych postaci (0.113.0)
- [x] Wyłączanie podpowiedzi (0.114.0)
- [x] Trudność celu nad potworem (0.115.0)
- [x] Wyzwanie na ekranie także online (0.116.0)
- [x] Wiadomość dnia serwera /motd (0.117.0)
- [x] Zużycie amunicji przy strzałach (0.118.0)
- [x] Ranking umiejętności /ranking magia (0.119.0)
- [x] Nagrody za głowy /nagroda (0.121.0)
- [x] Skarbiec gildii (0.123.0)
- [x] Wydarzenie premii doświadczenia /bonusexp (0.124.0)
- [x] Premia exp w oknie wydarzeń, miejsca występowania w bestiariuszu, /idź łowisko (0.125.0-0.129.0)
- [x] Czary poziomów 40-60 (0.130.0), okno „Co nowego” (0.131.0, poprawka Androida 0.133.1)
- [x] Odpoczynek w strefie ochronnej (0.133.0) i premia exp za wypoczęcie (0.134.0), podpowiedź o odpoczynku (0.135.0)
- [x] Nagrody na liście /gracze (0.136.0), osiągnięcie „Wypoczęty łowca” (0.137.0), zamówienia kupna /kupię (0.138.0)
## Etap 15 - Ochrona zasobow 90%/75% i diagnostyka
- [x] Logika NORMAL / LIMITED / RECOVERING z testami
- [x] Pomiary desktop i Android (pamiec, termika, bateria), panel diagnostyki
- [x] Ustawienia: oszczedzanie baterii, limit FPS, jakosc swiatla, liczby obrazen (zapis w Preferences)
## Etap 16 - Optymalizacja, regresja, przygotowanie Androida
- [x] Budzet wydajnosci petli serwera (200 graczy, cala mapa) jako test regresji
- [x] Mniej transferu: klatki bez zmian jako `KEEP`
- [x] Grafika w jednym atlasie tekstur (TileArt)
- [x] Niestabilny test NetworkSyncTest.bountiesArePaidToTheKiller: czestsze oslabianie celu i 30 s limitu (0.193.1)
- [x] Pasek akcji 10 pol 5x2 z wyborem czar leczacy / atakujacy / mikstura / runa po przytrzymaniu (0.191.0)
- [x] Uklad Tibii w jednej bryle: widok gry w ramce, panel prawy, paski u gory, konsola na dole (0.192.0)
- [x] Okna Ekwipunek, Umiejętności, Plecak i Konsola w stylu OTClient (0.186.0)
- [x] Ramki okien, przyciski i pad z OTClientV8 (MIT) w assets/ui/otc (0.185.0)
- [x] HUD w stylu klienta mobilnego: joystick, ATAK + 8 akcji na łukach, dolny pasek, minimapa z paskami (0.184.0)
- [x] Widok jak w Tibii: dachy znikajace po wejsciu, wysokie sciany z licem, wyzsze pietra widoczne z dolu (0.183.0)
- [x] Podmiana grafik plikami PNG (assets/art, 16 albo 32 px), 180 grafik z OpenTibia Sprite Pack (CC BY 4.0), /autorzy
- [ ] Brakujace grafiki (wilk, smoki, golemy, schody, sciany lodu i dzungli, kon, skrzynie) - czekaja na grafiki od Darka
- [x] Pomiar klatek w kliencie (logcat co 30 s) i test APK na emulatorze w CI (`android-smoke`)
- [ ] Pomiar klatek na prawdziwym telefonie (wymaga urzadzenia: `tools/android-smoke/run.sh` przez adb)
- [x] Zestaw testow regresji GUI (zrzuty ekranu kluczowych miejsc porownywane automatycznie, zadanie CI)
