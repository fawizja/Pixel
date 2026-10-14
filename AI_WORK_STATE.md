# AI_WORK_STATE

Plik stanu dla kolejnych sesji pracy. **Po wznowieniu przeczytaj go razem z TODO.md.**

## Podsumowanie (2026-10-14, wersja 0.4.0)

- Repozytorium `fawizja/Pixel` bylo puste; projekt utworzony od zera i wypchniety na `main`.
- Gra dziala lokalnie (offline) na desktopie; to jeszcze nie MMORPG (brak serwera i synchronizacji).
- Ukonczone: etapy 1, 3, 4, 5, 6, 7, 8 oraz czesci etapow 2, 9, 10, 11, 15 (szczegoly w TODO.md).
- 0.1.0 = 301ac0a, 0.2.0 = f6142e4, 0.3.0 = f554971 (wszystkie z zielonym CI i APK). 0.4.0 = etap 8.

## Architektura

```
shared/   pl.fawizja.pixel.*         czysta Java 17, bez libGDX, testy JUnit 5
  balance/    Balance (krzywe 1..1000), LevelBracket
  vocation/   Vocation (4 profesje - wszystkie wspolczynniki)
  character/  PlayerCharacter, CharacterFactory (+StarterKits), Skills, CharacterCodec (zapis)
  item/       ItemType, ItemRegistry (data/items.csv), ItemStack (niezmienny), EquipSlot
  inventory/  Container (atomowy, 20 slotow), Equipment, AutoLootFilter, InventoryResult
  economy/    Currency, Money (platnosci z reszta, wymiana), BankAccount
  world/      Position (z -5..+5), FloorMap (warstwy short[] + cache kolizji + swiatlo),
              WorldMap, MapLoader (maps/<nazwa>/), TileRegistry (data/tiles.csv), Transition,
              GroundItems, Lighting, Direction
  movement/   MovementSystem (krok, uzycie, czas kroku)
  time/       WorldClock (doba 60 min, plynna jasnosc)
  perf/       ResourceGuard (90%/75%, NORMAL/LIMITED/RECOVERING)
  save/       SaveStore (zapis atomowy + .bak), SaveFormatException
  monster/    MonsterType, MonsterRegistry (monsters.csv + loot.csv), SpawnTable, SpawnPoint,
              Monster, MonsterManager (AI + spawny + odradzanie), SpatialGrid, MonsterBehavior/State
  combat/     CombatFormulas, CombatSystem (gracz <-> potwory, smierc, lupy), CombatEvent, DamageType
  magic/      SpellType/Registry (spells.csv), SpellSystem (czary, runy, mikstury, jedzenie), Cooldowns,
              ConsumableRegistry, RecipeRegistry, CraftingSystem, CastResult (komunikaty)
  gathering/  FishingSystem, FishingResult
  movement/   + Pathfinder (BFS z limitem wezlow)
  game/       GameSession (lokalna petla gry: ruch -> potwory -> walka, teleport), EventLog
core/     pl.fawizja.pixel.client.*   libGDX
  PixelGame, screen/VocationSelectScreen, screen/GameScreen,
  render/TileArt (grafika proceduralna), render/WorldRenderer, render/LightRenderer (FBO),
  ui/Hud, ui/VirtualJoystick, ui/HudButton, platform/PlatformServices (+Default)
desktop/  DesktopLauncher (+ opcje -Dpixel.autostart/teleport/screenshot)
android/  AndroidLauncher, AndroidPlatformServices (pamiec, termika API 29+, bateria)
tools/mapgen/generate_varden.py  generator mapy (deterministyczny)
```

Zasada: reguly gry tylko w `shared`; klient tylko wyswietla i wysyla akcje. Serwer (etap 14) ma uzyc `shared`.

## Testy i weryfikacja (co faktycznie wykonano)

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
- Sytosc i swiatlo z czaru nie sa zapisywane.
- Symulacje walk to model "stoimy i wymieniamy ciosy" (pesymistyczny dla profesji dystansowych).
- Brak NPC, czarow, PvP, serwera.

## Punkt wznowienia

**Nastepne zadanie: Etap 9 - miasta (max 4), biomy, expowiska** (lista w TODO.md, plan w docs/WORLD_MAP.md):
1. Swiat wieloregionowy: obecnie jedna mapa `varden` 64x48. Zaprojektowac format swiata z regionami
   (osobne pliki map + przejscia miedzy regionami na krawedziach / drogach) albo jedna wieksza mape
   ladowana fragmentami (etap 3: chunki). Rekomendacja: regiony = osobne mapy, przejscie "travel" na krawedzi.
2. Generator (tools/mapgen) dla Frostholm (snieg, polnoc), Selvaran (rownik), Qasirah (pustynia):
   swiatynia, bank, sklepy, strefa ochronna, okolice z expowiskami; nowe kafelki biomow (snieg, lod, piasek, bagno).
3. Nowe potwory biomow (zgodnie z tabela w WORLD_MAP.md) i ich spawny; ryby zalezne od biomu.
4. Testy: dostepnosc wszystkich miast z Varden, spawn w strefie ochronnej, poziomy potworow rosna z odlegloscia.
