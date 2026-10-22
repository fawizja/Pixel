# PROJECT_PROGRESS

Plik wznowienia dla rozbudowy "w stylu Tibii" (master prompt Darka z 2026-10-22, etapy 1-20).
Po wznowieniu przeczytaj go razem z AI_WORK_STATE.md i TODO.md. Nie zawiera hasel ani tokenow.

## Cel

Rozbudowac istniejaca gre Pixel (libGDX, Java 17, Android) tak, by klimatem, interfejsem i mechanika
przypominala klasyczna Tibie (OTClient V8 / Canary), ale z autorskim swiatem: cztery duze, rozne miasta
(krolewskie, portowe, gorskie, egzotyczne), wiele expowisk, wielopoziomowe podziemia, statki i mosty.
Zalozenia serwera: kazda profesja startuje na poziomie 300 z 10 000 000 gp w banku.

## Repozytorium

- https://github.com/fawizja/Pixel, branch roboczy: `development` (main = dzialajaca wersja, APK `najnowsza`).
- Punkt wyjscia: main 0ba79e6 (0.196.0). Rownolegle inny watek pracuje na `main` (plecak w ramce ekwipunku,
  ruch po skosie) - `development` regularnie wciaga `main` (merge, bez przepisywania historii).

## Stan etapow

| Etap | Stan | Commit |
|------|------|--------|
| 1. Audyt i zabezpieczenie | ZAKONCZONY | 15af7c2 |
| 2. Grafika, assety, renderowanie | ZAKONCZONY | 55e7e49 |
| 3. System mapy i warstw | w toku | - |
| 4-20 | do zrobienia | - |

Galeria zrzutow po etapach: https://claude.ai/artifact/NQhU2aj1gny6JeFq3Nzekz
APK z development: https://github.com/fawizja/Pixel/releases/tag/development-apk (CI po kazdym pushu)

## Etap 1 - wyniki audytu (2026-10-22)

### Budowa i testy
- `./gradlew test :desktop:installDist :server:installDist` - OK. Na starcie 421 testow (shared 378, server 40, desktop 3), 0 bledow.
- Po zmianie startu: 422 testy (nowy StartingConfigFileTest), 0 bledow.
- APK: Android SDK niedostepny w chmurze; APK buduje CI GitHub Actions (`.github/workflows/build.yml`).
- Gra uruchomiona pod Xvfb (desktop lwjgl3): zrzuty Varden, Frostholm, Selvaran, Qasirah, obozu.

### Architektura (dziala, zachowac)
- `shared` - cala logika (swiat, ruch, walka, potwory, czary, ekwipunek, ekonomia, NPC, zadania, zapis, protokol).
- `core` - klient libGDX (WorldRenderer, LightRenderer, Hud 2.8 tys. linii, GameScreen 2 tys. linii).
- `server` - autorytatywny serwer TCP (ServerWorld uzywa `shared`), konta, TLS, PvP.
- `desktop`/`android` - launchery. Mapy: pliki ASCII `maps/world/floor_<z>.txt` + legend.csv, generowane
  przez `tools/mapgen/generate_world.py` (deterministycznie). Pietra -5..+5, przejscia w transitions.csv.
- Dane: data/*.csv (120 przedmiotow, 42 potwory, 36 czarow/run, 46 zadan, 240 pozycji sklepow).

### Kompletne (zweryfikowane testami lub zrzutem)
- Ruch 8 kierunkow z kolizjami, pathfinding, pietra i schody, swiatlo i pora dnia, pogoda.
- Walka wrecz/dystans/magia, odpornosci, smierc i odrodzenie, exp, lupy z autolootem, ciala potworow.
- Plecak 20 slotow (atomowe operacje), ekwipunek z wymaganiami, monety gp/plat/crystal z wymiana i reszta.
- Bank (konto wspolne miast), depozyt (zapis w postaci), rynek, handel graczy, poczta, przelewy.
- Czary (36), runy z ladunkami, mikstury, jedzenie, crafting, lowienie, pupile, wierzchowiec, blogoslawienstwa.
- NPC (26) z dialogami, zadania i lancuchy zadan, osiagniecia, ranking, gildie, druzyny.
- Serwer TCP z kontami, PvP, czaszkami, kopiami zapasowymi.

### Braki wzgledem master promptu (do kolejnych etapow)
1. **Swiat (etapy 4-9)**: mapa 256x232 to cztery male miasta w poziomych pasach biomow z losowym szumem;
   brak wybrzezy, morza, wysp, gor/kanionow jako ksztaltu terenu. Frostholm i Qasirah to te same,
   male uklady co Varden (identyczna swiatynia) - wymog "kazde miasto inne" niespelniony.
   Brak miasta portowego i gorskiego (sa: Varden krolewskie, Frostholm sniezne, Selvaran dzungla, Qasirah pustynia).
2. **Statki (etap 7)**: brak portow i statkow; podroz miedzy miastami tylko "woznica" (teleport za monety).
3. **Domki (etap 6)**: brak systemu domow (sa tylko budynki bez wlasciciela).
4. **Start 300 (etap 10)**: zachodni kontynent (poziomy 8-230) jest dla postaci 300 za slaby; pierwsze
   expowiska trzeba przestawic na 300+.
5. **Czary (etap 13)**: brak czarow szybkosci (utani hur, utani gran hur) i inkantacji w stylu Tibii
   (nazwy sa tylko polskie). Szybkosc = poziom + wierzchowiec, bez buffow.
6. **Plecaki (etap 14)**: jeden plecak; brak wielu plecakow (pojemnikow w pojemnikach) i przeciagania
   miedzy slotami (w Hud tylko szczatkowe "drag").
7. **Grafika (etap 2)**: sprite postaci z OpenTibia Sprite Pack sa po imporcie pochylone/scięte
   (postacie "leza" po skosie) i stroj mnicha ma czapke mikolaja - do poprawy w tools/art/otsp_import.py.
   Brakujace grafiki (TODO.md): smoki, golemy, schody, sciany lodu i dzungli, kon, skrzynie.
8. **UI (etap 16)**: HUD w stylu OTClient jest, ale bez zakladek czatu i okna wielu plecakow.
9. **Wydajnosc (etap 18)**: brak pomiaru na prawdziwym telefonie (wymaga urzadzenia Darka).

### Wykonane w etapie 1
- Branch `development` utworzony z main 0.196.0 (main nietkniety = zabezpieczona dzialajaca wersja).
- `data/starting.properties`: start.level=300 (takze online), start.gold.bank=10000000 (kieszonkowe 10 000 gp w plecaku zostaje).
- `StartingConfig.loadDefault()` przyjmuje `-Dpixel.start.level`/`-Dpixel.start.gold.bank`; testy mechaniki
  dalej licza od poziomu 8 (build.gradle), a `StartingConfigFileTest` pilnuje 300 i 10 mln w dostarczonej konfiguracji.
- `tools/mapgen/render_overview.py` - podglad calego pietra mapy do PNG (wizualizacja do raportow).

## Etap 2 - grafika (2026-10-22)
- `core/.../render/GroundBorders.java`: obrzeza podloza jak w Tibii (priorytety: woda < lod/lawa < bagno < jaskinia
  < popiol < ziemia < ciemny piasek < piasek < bruk < pole < trawa < trawa dzungli < snieg); 8 kawalkow na podloze
  skladanych przy starcie z tekstury i maski; rysowane w WorldRenderer.drawBorders po podlozu, przed dekoracja.
- `tools/art/otsp_import.py`: TINTED - 19 potworow z przebarwionych strojow OTSP; mnich = stroj 26; schody, drabina,
  lada, oltarz, pochodnia; mury ice_wall i obsidian_wall; cave_wall = przyciemniony kamien (lita skala).
  Paczka OTSP: `git clone https://github.com/peonso/opentibia_sprite_pack` i
  `python3 -I tools/art/otsp_import.py <paczka>/client_files /tmp/art`, potem kopiowac tylko zmienione pliki.
- Poprawka: CharacterFactory.BASE_LEVEL = 8 - umiejetnosci "pod poziom" od 9 w gore (wczesniej porownanie z
  start.level, ktore po zmianie na 300 wylaczalo umiejetnosci postaci serwerowych).
- Uwaga: ukosna sylwetka postaci to styl samej paczki OTSP (perspektywa Tibii), nie blad importu.
- Testy 423 OK, regresja GUI zaktualizowana (east_camp, selvaran zmienione przez obrzeza).
- Brakuje nadal: palma, kaktus, fontanna, latarnia uliczna, plot, dywan, magma/lawa z OTSP (zostaja z kodu).

## Nastepne kroki (dokladnie)
1. Etap 3: system mapy i warstw - sprawdzic limity (rozmiar mapy, pietra, chunki), przygotowac format pod duzy swiat.
2. Etap 4: nowy uklad swiata (kontynent z wybrzezem, morzem, wyspami, gorami) w generate_world.py,
   zachowujac format plikow i istniejace systemy (MapLoader, transitions, spawns, npcs.csv).
3. Etap 5: cztery rozne, duze miasta: Varden (krolewskie), nowe miasto portowe, Frostholm jako gorskie
   (kamienne tarasy, kopalnie), Qasirah/Selvaran jako egzotyczne (pustynia + dzungla).

## Narzedzia
- JDK 21 (kompilacja --release 17), Gradle 8.14.3 wrapper; w chmurze lokalny init.d z lustrem Maven Central
  (poza repo) gdy Maven Central zwraca 429.
- Zrzuty: Xvfb :99 + `desktop/build/install/desktop/bin/desktop` z `-Dpixel.autostart=Imie:PROFESJA
  -Dpixel.teleport=x,y,z -Dpixel.screenshot=plik.png`; regresja GUI `tools/gui-regression/run.sh`.
