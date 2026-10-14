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
- [ ] Ladowanie fragmentow (chunkow) dla duzego swiata

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

## Etap 7 - Czary, runy, mikstury, crafting  <- NASTEPNY

## Etap 8 - Fishing i umiejetnosci
## Etap 9 - Miasta (max 4), biomy, expowiska
- [x] Varden: swiatynia, bank, sklepy, domy, wieza, kopalnia, jezioro, pola
- [ ] NPC i uslugi Varden, Frostholm, Selvaran, Qasirah, biomy snieg / rownik / pustynia
## Etap 10 - Pietra +5..-5, jaskinie, oswietlenie
- [x] Pietra -5..+5, schody, dziury, drabiny, liny; test dostepnosci wszystkich pieter
- [x] Ciemne podziemia, pochodnie na mapie i w plecaku, poswiata postaci
- [ ] Paliwo pochodni, czary swiatla
## Etap 11 - Dzien, noc, oswietlenie swiata
- [x] Zegar swiata, fazy, plynna jasnosc, czytelna noc
- [ ] Zegar z serwera, wplyw nocy na potwory
## Etap 12 - NPC, zadania, handel, bank
## Etap 13 - PvP i reguly serwerowe
## Etap 14 - Serwer i synchronizacja MMORPG
## Etap 15 - Ochrona zasobow 90%/75% i diagnostyka
- [x] Logika NORMAL / LIMITED / RECOVERING z testami
- [x] Pomiary desktop i Android (pamiec, termika, bateria), panel diagnostyki
- [ ] Osobne ustawienia oszczedzania baterii, menu ustawien
## Etap 16 - Optymalizacja, regresja, przygotowanie Androida
