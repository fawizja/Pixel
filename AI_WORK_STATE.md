# AI_WORK_STATE

Plik stanu dla kolejnych sesji pracy. **Po wznowieniu przeczytaj go razem z TODO.md.**

## Podsumowanie (2026-10-15, wersja 0.13.2)

- Repozytorium `fawizja/Pixel` bylo puste; projekt utworzony od zera i wypchniety na `main`.
- Gra dziala lokalnie i online: modul `server` (TCP) + tryb online klienta desktopowego (`-Dpixel.server`).
  PvP (etap 13), konta z haslem, menu i ekran logowania (takze APK). TLS z przypinaniem certyfikatu.
- Ukonczone: etapy 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 oraz czesci etapow 2, 9, 10, 11, 14, 15 (szczegoly w TODO.md).
- 0.1.0 = 301ac0a, 0.2.0 = f6142e4, 0.3.0 = f554971, 0.4.0 = f77ddc9, 0.5.0 = 561ad81, 0.6.0 = 9be0eca, 0.7.0 = e6268c5, 0.8.0 = b759340, etap 14 cz.1 = 9b914f5 (wszystkie z zielonym CI i APK). 0.9.0 = 143c40b (klient online, CI zielone). 0.10.0 = 4613838 (PvP, CI zielone). 0.11.0 = 9511573 (konta/logowanie/czat, CI zielone). 0.11.1 = a97ad23 (przewidywanie ruchu, CI zielone). 0.12.0 = 955b3c1 (TLS, CI zielone). 0.12.1 = e6c5175 (limity serwera), kopie zapasowe = b64d06a, endgame cz.1 = 05d40b4 (potwory, sprzet, symulacje; CI zielone). 0.13.0 = d0f3c45 (wschodni kontynent i straznik mostu, CI zielone). 0.13.1 = f3d52d5 (oboz, kuznia, CI zielone). 0.13.2 = blokada PZ po walce, trwale czaszki, KEEP.

## Architektura

```
shared/   pl.fawizja.pixel.*         czysta Java 17, bez libGDX, testy JUnit 5
  balance/    Balance (krzywe 1..1000), LevelBracket
  vocation/   Vocation (4 profesje - wszystkie wspolczynniki)
  character/  PlayerCharacter, CharacterFactory (+StarterKits), Skills, CharacterCodec (zapis)
  item/       ItemType, ItemRegistry (data/items.csv), ItemStack (niezmienny), EquipSlot
  inventory/  Container (atomowy, 20 slotow), Equipment, AutoLootFilter, InventoryResult
  economy/    Currency, Money (platnosci z reszta, wymiana), BankAccount
  world/      WorldLayout (mapa "world"), Town, Region, Position (z -5..+5), FloorMap (warstwy short[] + cache kolizji + swiatlo),
              WorldMap, MapLoader (maps/<nazwa>/), TileRegistry (data/tiles.csv), Transition,
              GroundItems, Lighting, Direction
  movement/   MovementSystem (krok, uzycie, czas kroku)
  time/       WorldClock (doba 60 min, plynna jasnosc)
  perf/       ResourceGuard (90%/75%, NORMAL/LIMITED/RECOVERING)
  pvp/        PvpRules (ochrona, PZ, roznica poziomow, czaszki, atak gracz-gracz)
  net/        Protocol (linie TCP), NetClient (watek odbiorczy, klatki), RemoteEntity
  game/       GameSession, Actions (nazwy akcji wspolne dla HUD i serwera), EventLog
  save/       SaveStore (zapis atomowy + .bak), SaveFormatException
  monster/    MonsterType, MonsterRegistry (monsters.csv + loot.csv), SpawnTable, SpawnPoint,
              Monster, MonsterManager (AI + spawny + odradzanie), SpatialGrid, MonsterBehavior/State
  combat/     CombatFormulas, CombatSystem (gracz <-> potwory, smierc, lupy), CombatEvent, DamageType
  magic/      SpellType/Registry (spells.csv), SpellSystem (czary, runy, mikstury, jedzenie), Cooldowns,
              ConsumableRegistry, RecipeRegistry, CraftingSystem, CastResult (komunikaty)
  gathering/  FishingSystem, FishingResult
  movement/   + Pathfinder (BFS z limitem wezlow)
  npc/        Npc, NpcRegistry (maps/world/npcs.csv), Shop, ShopRegistry (data/shops.csv), TradeSystem
  quest/      QuestType, QuestRegistry (data/quests.csv), QuestLog (w postaci), QuestSystem
  game/       GameSession (lokalna petla gry: ruch -> potwory -> walka, teleport), EventLog
core/     pl.fawizja.pixel.client.*   libGDX
  PixelGame, screen/VocationSelectScreen, screen/GameScreen,
  render/TileArt (grafika proceduralna), render/WorldRenderer, render/LightRenderer (FBO),
  ui/Hud, ui/VirtualJoystick, ui/HudButton, platform/PlatformServices (+Default)
desktop/  DesktopLauncher (+ opcje -Dpixel.autostart/teleport/screenshot)
android/  AndroidLauncher, AndroidPlatformServices (pamiec, termika API 29+, bateria)
tools/mapgen/generate_varden.py, generate_world.py  generatory map (deterministyczne; world wkleja varden)
```
server/   pl.fawizja.pixel.server: PixelServer (petla 20/s), ServerWorld (sesje graczy, wspolne potwory i ziemia), ClientHandler
```

Zasada: reguly gry tylko w `shared`; klient tylko wyswietla i wysyla akcje. Serwer (etap 14) ma uzyc `shared`.

## Testy i weryfikacja (co faktycznie wykonano)

- 0.13.2: `./gradlew test` - OK, 187 testow (w tym restart serwera z zachowana czaszka, budzet tiku 0,45 ms).
- 0.13.1: `./gradlew test` - OK, 183 testy (3 przebiegi pod rzad bez bledu); zrzut obozu z 3 NPC.
- 0.13.0: `./gradlew test` - OK, 181 testow; zrzuty pod Xvfb: oboz za mostem, krater, ruiny Kha'zuula,
  Otchlan -1 (renderowanie nowych kafelkow OK). Kontrola: zachodnia czesc mapy identyczna jak przed
  rozszerzeniem poza trasa mostu (porownanie pieter skryptem).

- 0.8.0: `./gradlew build` - OK, 153 testy; zrzuty okna kupca i listy zadan w Varden.
- 0.9.0: `./gradlew test` - OK, 161 testow; recznie: serwer na 7272 + 2 klienty desktop (Xvfb) + bot TCP -
  wszyscy widza sie nawzajem, czat dociera (zrzut online_three_players.png).
- 0.7.0: `./gradlew build` - OK, 147 testow; zrzut o 23:00 przy wilkach Varden (noc, wilk atakuje).
- 0.6.0: `./gradlew build` - OK, 142 testy; zrzuty: krolowa lodu na -5 (krysztaly swieca), w ruinach
  postac 8 poziomu ginie od kolosa i odradza sie w swiatyni.
- 0.5.0: `./gradlew build` - wynik ponizej w sekcji punktu wznowienia; desktop pod Xvfb na nowym swiecie:
  Frostholm, Qasirah, Selvaran, wilki sniezne (atakuja postac 8 poziomu), bagna przy krokodylach - renderowanie OK.
- 0.4.0: `./gradlew build` - **OK, 129 testow**; zrzut przy rzece (przycisk LOW, karta postaci) OK.
- 0.3.0: `./gradlew build` - **OK, 122 testy, 0 bledow**; desktop uruchomiony, zrzuty paska czarow i ksiegi OK.
- 0.2.0: `./gradlew :shared:test` - **OK, 106 testow, 0 bledow**; `:desktop:installDist` OK.
  Gra uruchomiona pod Xvfb z teleportem do wilkow (0), goblinow (-1) i trolli (-4): potwory
  atakuja, liczby obrazen, paski HP, panel celu, autoloot i exp dzialaja (zrzuty w /mnt/project-files/pixel/screenshots).
- 0.1.0: `./gradlew :shared:test :core:build :desktop:build` - OK, 76 testow (JDK 21, `--release 17`).
- Gra uruchomiona na desktopie pod Xvfb (Mesa, renderowanie programowe): zrzuty ekranu miasta,
  kopalni (-1) i wiezy (+5) wygladaly poprawnie; zapis postaci utworzony (`character.save` + `.bak`).
- Kod modulu `android` skompilowany `javac` wzgledem klas Android 14 (robolectric android-all) i
  `gdx-backend-android` 1.13.5 - bez bledow.
- **Niezweryfikowane lokalnie**: budowa APK (brak Android SDK w srodowisku - dl.google.com zablokowany),
  dzialanie na telefonie, ekrany 90/120/144 Hz. APK buduje CI (`.github/workflows/build.yml`) -
  sprawdz wynik pierwszego przebiegu.

## Znane problemy / uwagi

- Maven Central (repo.maven.apache.org) zwracal 429 w srodowisku chmurowym; lokalnie uzywano
  lustra Google przez `~/.gradle/init.d/mirror.gradle` (poza repozytorium). Repo uzywa standardowego mavenCentral().
- W trybie programowego renderowania (Xvfb) czas klatki bywa wysoki, co moze wlaczyc LIMITED - zgodnie z zalozeniem.
- Grafika jest tymczasowa (proceduralna), czcionka domyslna libGDX nie ma polskich znakow - teksty UI bez diakrytykow.
- Brak efektow graficznych czarow (tylko liczby obrazen/leczenia); pasek czarow jest automatyczny.
- Symulacje walk to model "stoimy i wymieniamy ciosy" (pesymistyczny dla profesji dystansowych).
- PvP tylko atakiem podstawowym; limity serwera i kopie zapasowe sa. Endgame 230..1000 jest na wschodnim kontynencie z obozem (kupiec, bank, zadania) i kuznia.
- Miasta poza Varden maja tylko swiatynie, plac i domy (bez sklepow do etapu 12).

## Punkt wznowienia

**Nastepne zadanie: etap 16 (optymalizacja, regresja, Android) i domkniecie etapu 14** (TODO.md). Plan:
1. PvP: czary i runy na graczy (blokada PZ i trwale czaszki juz sa).
2. Etap 16: wydajnosc (profil klatki na duzej mapie 256x232), regresja, Android.
Menu: `MenuScreen` -> `PixelGame.playLocal()` albo `LoginScreen` -> `connectOnline` (watek) -> `startOnline`.
Test GUI bez myszy: Xvfb :99 + python-xlib (XTEST) do klikania i pisania - skrypt w opisie sesji, nie w repo.
Tryb online: `GameScreen(game, pc, NetClient)` - bez symulacji, `syncWithServer` odwzorowuje SELF/FRAME/LOG/EV.
Uruchomienie lokalne: `server/build/install/server/bin/server 7272 <katalog>` i klient z `-Dpixel.server=127.0.0.1:7272`.
Mapy swiata zmieniaj tylko przez `tools/mapgen/generate_world.py`, potem `./gradlew :shared:test`.
Generator: zachod (x < 160) uzywa `rnd`, wschod `rnd_east` - nie mieszaj, bo zmieni sie caly stary swiat.
Nowe symbole legendy wschodu to greckie litery (pliki map w UTF-8). Balans endgame: `EndgameSimulationTest`.
