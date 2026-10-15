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
- [ ] Czary i umiejetnosci profesji (etap 7), zasady PvP (etap 13)

## Etap 5 - Plecak, przedmioty, monety, ekwipunek
- [x] Plecak 20 slotow, atomowe operacje, laczenie stosow, przenoszenie, zamiana
- [x] Ekwipunek z wymaganiami profesji i poziomu
- [x] Wyrzucanie i podnoszenie, lista ignorowanych przy autoloocie
- [x] Gold / Platinum / Crystal, wymiana, platnosci z reszta, bank (logika)
- [ ] Uzywanie przedmiotow (mikstury, jedzenie) - razem z etapem 7
- [ ] Okno banku w UI - etap 12

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
- [ ] Mikstury i czary leczace (etap 7) - teraz jedyne leczenie to regeneracja
- [ ] Dzwiek, animacja ataku, cialo potwora z lupem do przeszukania

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
- [ ] Efekty graficzne czarow (pociski, wybuchy), dzwieki
- [ ] Wlasny uklad paska czarow (teraz automatyczny)


## Etap 8 - Fishing i umiejetnosci
- [x] Lowienie: wedka + robak (zuzywany tylko przy zlowieniu), woda w zasiegu 3, szansa 16%..70% wg umiejetnosci
- [x] Odpoczynek lowiska 2 min po zlowieniu (brak farmienia jednego kafelka), perly od umiejetnosci 30
- [x] Trening umiejetnosci tylko na potworach, ktore nie sa zbyt slabe (ten sam prog co pelny exp)
- [x] Karta postaci (dotkniecie paskow HP/MP lub P): exp, szybkosc, udzwig, sytosc, umiejetnosci z postepem
- [x] Przycisk LOW pokazywany tylko przy wodzie z wedka w plecaku
- [ ] Ryby zalezne od biomu (lod, bagna, oaza) - razem z nowymi miastami

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
- [ ] Ryby zalezne od biomu
## Etap 10 - Pietra +5..-5, jaskinie, oswietlenie
- [x] Pietra -5..+5, schody, dziury, drabiny, liny; test dostepnosci wszystkich pieter
- [x] Ciemne podziemia, pochodnie na mapie i w plecaku, poswiata postaci
- [x] Paliwo pochodni (30 min, automatycznie nastepna), czar Poswiata, zapis stanu swiatla i sytosci
- [x] 3 lochy biomow do -5 (dziury, drabiny, miejsca na line), swiecacy mech, magma, lodowe krysztaly
- [x] 3 bossowie biomow (150, 200, 230) z unikalnymi lupami; testy drogi powrotnej i swiatla
- [ ] Pietra +1..+5 poza wieza Varden (gory, przelecze)
## Etap 11 - Dzien, noc, oswietlenie swiata
- [x] Zegar swiata, fazy, plynna jasnosc, czytelna noc
- [x] Zegar w GameSession (wspolny dla logiki i grafiki, wymienny - serwer poda czas), komunikaty o zmroku i switaniu
- [x] Nocne drapiezniki (kolumna activity w monsters.csv): w nocy na powierzchni aggro +3, ataki o 20% szybsze
- [x] Ostrzezenie o pietrze liczy tylko spawny w promieniu 30 kafelkow (duzy swiat)
- [x] Czas gry z serwera (klient online zgrywa zegar)
- [ ] Nocne spawny
## Etap 12 - NPC, zadania, handel, bank
- [x] 17 NPC w 4 miastach (bankier, kupiec, kaplan, przewodnik; w Varden takze czarodziejka), generowane z mapa
- [x] 5 sklepow (data/shops.csv): kupno z ilosciami, sprzedaz wszystkich sztuk, atomowo, z reszta
- [x] Zabezpieczenia ekonomii: skup ponizej ceny sprzedazy w calym swiecie, brak skupu run/mikstur/monet,
      brak zysku z receptur i zadan na przynoszenie (testy)
- [x] Bank w UI: wplata wszystkiego, wyplaty; kaplan: leczenie i zamieszkanie (miejsce odrodzenia)
- [x] 13 zadan (data/quests.csv): zabij N / przynies N, nagrody exp i gp, zapis postepu
- [x] Klient: sprite i imiona NPC, przycisk ROZMOWA, dotkniecie NPC, okno rozmowy z rolami i stronami
- [ ] Dialogi slowami kluczowymi, zadania wieloetapowe, nagrody przedmiotami
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
- [ ] Zmiana hasla, usuwanie konta, limit kont z jednego adresu
- [x] Przewidywanie ruchu po stronie klienta z korekta pozycji przez serwer
- [x] Protokol v3: niezmieniona klatka jako `KEEP` (mniej transferu)
## Etap 15 - Ochrona zasobow 90%/75% i diagnostyka
- [x] Logika NORMAL / LIMITED / RECOVERING z testami
- [x] Pomiary desktop i Android (pamiec, termika, bateria), panel diagnostyki
- [ ] Osobne ustawienia oszczedzania baterii, menu ustawien
## Etap 16 - Optymalizacja, regresja, przygotowanie Androida
- [x] Budzet wydajnosci petli serwera (200 graczy, cala mapa) jako test regresji
- [x] Mniej transferu: klatki bez zmian jako `KEEP`
- [ ] Pomiar klatek na prawdziwym telefonie (wymaga urzadzenia), docelowa grafika w atlasie tekstur
- [ ] Zestaw testow regresji GUI (zrzuty ekranu kluczowych miejsc porownywane automatycznie)
