# Pixel

2D MMORPG na Androida w stylu klasycznych gier fantasy, budowany etapami w **libGDX (Java 17)**.

> Stan: **wczesna wersja lokalna (offline)**. Jest grywalny swiat z czterema miastami
> (Frostholm, Varden, Selvaran, Qasirah) w pasach biomow snieg - umiarkowany - dzungla - pustynia,
> lochami na pietrach -5..+5 i regionami z zalecanym poziomem, postac, profesje, plecak, monety, bank (logika), zapis oraz
> potwory z AI, walka, smierc, doswiadczenie i lupy, czary, runy, mikstury, jedzenie i crafting.
> Stan: dziala serwer z synchronizacja sieciowa i tryb online klienta desktopowego (wielu graczy w jednym
> swiecie). Dziala PvP z ochrona nowych postaci i czaszkami.
> Brakuje jeszcze kont z haslem, szyfrowania i wyboru serwera na Androidzie.

## Moduly

| Modul | Zawartosc |
|---|---|
| `shared` | Czysta logika gry bez libGDX: balans, profesje, postac, przedmioty, plecak, monety, bank, mapa i kolizje, piętra, ruch, zegar swiata, oswietlenie, ochrona zasobow 90%/75%, zapis. Testy JUnit 5. Ten sam kod bedzie wykorzystany przez serwer. |
| `core` | Klient libGDX: generowana grafika pikselowa, renderowanie mapy, swiatlo (FBO), HUD mobilny, joystick, plecak, diagnostyka. |
| `desktop` | Launcher LWJGL3 do uruchamiania i testow na PC. |
| `android` | Launcher Androida, pomiary zasobow (pamiec, stan termiczny, bateria). Dolaczany tylko, gdy jest Android SDK. |
| `tools/mapgen` | Deterministyczny generator mapy startowej (Python 3). |

Dane gry (latwe do balansowania bez zmian w kodzie) leza w `shared/src/main/resources`:
`data/items.csv`, `data/tiles.csv`, `data/starting.properties`, `data/monsters.csv`, `data/spells.csv`, `maps/world/*` (generowane przez
`tools/mapgen/generate_world.py`, ktory korzysta z `maps/varden/*`).

## Wymagania

- JDK 17 lub nowszy
- Android: Android SDK (API 35) wskazany w `local.properties` (`sdk.dir=...`) albo w `ANDROID_HOME`
- Gradle nie jest wymagany - uzyj dolaczonego `./gradlew`

## Uruchamianie

```bash
./gradlew :shared:test            # testy logiki (bez telefonu i bez GPU)
./gradlew :desktop:run            # gra na PC
./gradlew :android:assembleDebug  # APK (wymaga Android SDK)
./gradlew :android:installDebug   # instalacja na podlaczonym telefonie
```

CI (GitHub Actions, `.github/workflows/build.yml`) uruchamia testy, kompiluje klienta i buduje APK debug
jako artefakt `pixel-debug-apk`.

### Sterowanie

| Akcja | Telefon | PC |
|---|---|---|
| Ruch (8 kierunkow) | joystick w lewym dolnym rogu | strzalki / WASD |
| Uzyj (drabina, lina) | UZYJ | E |
| Bieganie (zielony = wlaczone) | BIEG | R |
| Pochodnia | POCH | T |
| Plecak | PLECAK | B lub I |
| Podnies przedmiot spod nog | PODNIES | G |
| Diagnostyka | `i` w prawym gornym rogu | F3 |
| Nastepny cel (atak automatyczny) | CEL albo dotkniecie potwora | Spacja / C |
| Postawa obronna (zielony = wlaczona) | TARCZA | V |
| Czary z paska (leczenie, atak, obszar, swiatlo) | 4 male przyciski na dole | 1-4 |
| Mikstura zdrowia / many | HP / MP | Q / F |
| Ksiega czarow i rzemiosla | KSIEGA | K |
| Lowienie ryb (przy wodzie, wedka + robaki) | LOW | L |
| Karta postaci i umiejetnosci | dotknij paskow HP/MP | P |
| Rozmowa z NPC (sklep, bank, kaplan, zadania) | ROZMOWA albo dotkniecie NPC | N |
| Uzycie przedmiotu (mikstura, jedzenie, runa na cel) | PLECAK -> zaznacz -> UZYJ | - |

W plecaku: dotknij przedmiotu, aby go zaznaczyc (statystyki), potem dotknij innego slotu, aby
przeniesc / polaczyc / zamienic, albo uzyj ZALOZ / WYRZUC.

### Opcje deweloperskie (desktop)

```bash
./gradlew :desktop:installDist
JAVA_OPTS="-Dpixel.autostart=Darek:MONK -Dpixel.teleport=60,91,-5 -Dpixel.hour=23 -Dpixel.screenshot=shot.png" \
  desktop/build/install/desktop/bin/desktop
```

### Serwer i gra online (etap 14)

```bash
./gradlew :server:installDist
server/build/install/server/bin/server 7171 server-saves   # port i katalog zapisow postaci
```

Serwer jest zrodlem prawdy: klient wysyla tylko zamiary (ruch, akcje), serwer odsyla stan swiata.
Klient desktopowy w trybie online:

```bash
JAVA_OPTS="-Dpixel.server=127.0.0.1:7171 -Dpixel.autostart=Darek:PALADIN" desktop/build/install/desktop/bin/desktop
```

Gdy serwer nie odpowiada, klient gra lokalnie. Na Androidzie brak jeszcze ekranu wyboru serwera
(APK dziala lokalnie), nie ma tez kont z haslem ani szyfrowania - serwer nie jest gotowy do wystawienia publicznie.

## Dokumentacja

- [AI_WORK_STATE.md](AI_WORK_STATE.md) - aktualny stan, architektura, testy, punkt wznowienia
- [TODO.md](TODO.md) - plan etapow i status zadan
- [CHANGELOG.md](CHANGELOG.md) - historia zmian
- [docs/GAME_SYSTEMS.md](docs/GAME_SYSTEMS.md) - mechaniki
- [docs/GAME_BALANCE.md](docs/GAME_BALANCE.md) - zasady balansu
- [docs/WORLD_MAP.md](docs/WORLD_MAP.md) - geografia swiata

## Zasoby i licencje

Cala grafika jest generowana proceduralnie w kodzie (`TileArt`) i jest wlasna. Projekt nie zawiera
grafik, map ani danych z gier komercyjnych. Obecna grafika jest tymczasowa (placeholder) i zostanie
zastapiona docelowymi atlasami.
