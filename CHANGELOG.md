# Changelog

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
