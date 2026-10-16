# Pixel

2D MMORPG na Androida w stylu klasycznych gier fantasy, budowany etapami w **libGDX (Java 17)**.

> Stan: **wczesna wersja lokalna (offline)**. Jest grywalny swiat z czterema miastami
> (Frostholm, Varden, Selvaran, Qasirah) w pasach biomow snieg - umiarkowany - dzungla - pustynia,
> lochami na pietrach -5..+5 i regionami z zalecanym poziomem, postac, profesje, plecak, monety, bank (logika), zapis oraz
> potwory z AI, walka, smierc, doswiadczenie i lupy, czary, runy, mikstury, jedzenie i crafting.
> Stan: dziala serwer z synchronizacja sieciowa i tryb online klienta desktopowego (wielu graczy w jednym
> swiecie). Dziala PvP z ochrona nowych postaci i czaszkami.
> Logowanie z haslem dziala takze w APK, polaczenie jest szyfrowane (TLS z przypietym certyfikatem).
> Poziomy 230-1000: wschodni kontynent za mostem (wpuszcza od poziomu 200) z piecioma krainami i czterema bossami dla druzyn.

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
tools/gui-regression/run.sh       # regresja GUI: 14 scen porownanych z wzorcami (wymaga ekranu, np. Xvfb)
tools/gui-regression/run.sh --update  # nowe wzorce po zamierzonej zmianie wygladu
tools/android-smoke/run.sh android/build/outputs/apk/debug  # APK na telefonie/emulatorze przez adb: start, 75 s gry, pomiar klatek
```

CI (GitHub Actions, `.github/workflows/build.yml`) uruchamia testy, kompiluje klienta i buduje APK debug
jako artefakt `pixel-debug-apk`. Osobne zadanie `gui-regression` robi zrzuty ekranu pod Xvfb i porownuje je
z wzorcami w `tools/gui-regression/reference` (roznice jako artefakt `gui-regression`). Zadanie `android-smoke`
uruchamia APK na emulatorze i zapisuje zrzut ekranu, logcat i pomiar klatek (artefakt `android-smoke`).

### Sterowanie

| Akcja | Telefon | PC |
|---|---|---|
| Ruch (8 kierunkow) | joystick w lewym dolnym rogu | strzalki / WASD |
| Uzyj (drabina, lina) | UZYJ | E |
| Bieganie (zielony = wlaczone) | BIEG | R |
| Pochodnia | POCH | T |
| Plecak | PLECAK | B lub I |
| Podnies przedmiot spod nog | PODNIES | G |
| Diagnostyka i ustawienia | `i` w prawym gornym rogu, wiersze ustawien pod panelem | F3, klikniecie wiersza |
| Nastepny cel (atak automatyczny) | CEL albo dotkniecie potwora | Spacja / C |
| Postawa obronna (zielony = wlaczona) | TARCZA | V |
| Czary z paska (leczenie, atak, obszar, swiatlo) | 4 male przyciski na dole | 1-4 |
| Mikstura zdrowia / many | HP / MP | Q / F |
| Ksiega czarow i rzemiosla | KSIEGA | K |
| Lowienie ryb (przy wodzie, wedka + robaki) | LOW | L |
| Karta postaci i umiejetnosci | dotknij paskow HP/MP | P |
| Rozmowa z NPC (sklep, bank z depozytem przedmiotow, kaplan, zadania) | ROZMOWA albo dotkniecie NPC | N |
| Menu gracza online (handel, druzyna, gildia, atak) | dotkniecie gracza | klikniecie gracza |
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
JAVA_OPTS="-Dpixel.server=127.0.0.1:7171 -Dpixel.autostart=Darek:PALADIN -Dpixel.password=twoje-haslo" desktop/build/install/desktop/bin/desktop
```

Bez `-Dpixel.autostart` klient pokazuje menu: "Graj lokalnie" albo "Graj online" (ekran logowania:
adres serwera, imie, haslo). Pierwsze logowanie nowym imieniem zaklada konto; haslo serwer przechowuje
tylko jako skrot PBKDF2 w `<katalog zapisow>/accounts/`. W grze haslo zmienia polecenie czatu `/haslo stare nowe`,
a konto usuwa `/usun-konto haslo`. Druzyny: `/zapros Imie`, `/przyjmij Imie`, `/opusc`, czat druzyny `/d tekst`. Prywatnie: `/w Imie, tekst`. Ranking: `/ranking` albo `/ranking druid` (paladin, mag, mnich), gildie: `/ranking gildie`. Pojedynek: `/pojedynek Imię`, `/pojedynek poddaj`. Znajomi: `/znajomi`, `/znajomi dodaj Imię`, `/znajomi usuń Imię`. Skrzynie skarbów w lochach. Mapa: dotknij minimapy albo klawisz J; własne znaczniki `/znacznik nazwa`, lista `/znaczniki`; dotknięcie miejsca na mapie albo pola na ekranie prowadzi tam postać (do dalekiego NPC - z rozmową po dojściu). Zadanie dnia u przewodnika. Dziennik zadań: klawisz Z albo `/zadania`. Analiza polowania (exp, zabicia i złoto na godzinę): klawisz H albo `/analiza`. Potwór dnia (+50% exp i łupu, codziennie inny, złoty romb nad głową): `/potwór`. Rzadki łup (szansa do 1%) ogłaszany wszystkim graczom. Manekiny treningowe w miastach: UŻYJ obok manekina. Pogoda (deszcz, śnieg, burza piaskowa) zależy od krainy. Woźnice przy fontannach wożą między miastami za monety. Po śmierci okno podsumowania (`/śmierć`). Bossowie: `/bossowie` albo u przewodnika. Kto jest w grze: `/gracze`. Co dziś: `/wydarzenia` (inwazja, potwór dnia, zadanie dnia, nagrody). Statystyki postaci (czas gry, zabicia, śmierci): `/statystyki`. Wszystkie polecenia: `/pomoc` (dotknięcie wykonuje polecenie). Codzienna nagroda za wejście (seria 7 dni). Nagrody za progi poziomu 10-250 (złoto i zwoje nauki). Koń: siodło od kupca, przycisk KOŃ (M). Zwój nauki u kupców: +30% exp za potwory przez godzinę polowania. Pupile (pies, kot, kruk, fenek) u kupców: UŻYJ w plecaku, chodzą za postacią. Kowal u każdego kupca: ulepszanie założonej broni, tarczy i zbroi do +5 oraz zaklęcia na godzinę polowania (kradzież życia, kradzież many, cios krytyczny). Inwazje na miasta co 2 godziny (serwer). Osiągnięcia: klawisz O albo `/osiągnięcia`, `/bestiariusz`. Dotknięcie zdobytego osiągnięcia ustawia tytuł widoczny nad imieniem. Dotknięcie potwora w bestiariuszu pokazuje jego kartę (łupy od 25 pokonanych). Poczta (u bankiera): `/wyślij Imię, 5 wilcze futro`. Przelew złota (u bankiera, z konta): `/przelew Imię, 5000`. Rynek (u bankiera): `/rynek`, `/rynek wystaw 5 wilcze futro 300`, `/rynek kup 12`, `/rynek wycofaj 12`. Moderacja: patrz [docs/SERVER.md](docs/SERVER.md). Gildie: `/gildia zaloz Nazwa` (50 000 gp), `/gildia zapros Imie`, `/gildia przyjmij Nazwa`, `/gildia opusc`, wiadomość dnia lidera `/gildia wiadomość tekst`, czat `/g tekst`. Handel twarza w twarz: `/handel Imie`, `/oferuj 5 mikstura zdrowia` (albo angielska nazwa, np. `health potion`), `/oferuj 1000 gp`, `/akceptuj`, `/anuluj`.

Polaczenie jest szyfrowane TLS. Przy pierwszym starcie serwer tworzy wlasny certyfikat w
`<katalog zapisow>/tls/` (klucz i haslo magazynu nigdy nie trafiaja do repozytorium) i wypisuje jego
odcisk SHA-256. Klient zapamietuje odcisk przy pierwszym polaczeniu i odmawia, gdy sie zmieni
(haslo nie jest wtedy wysylane). Serwer robi kopie zapasowe katalogu zapisow (`backups/*.zip`, przy
starcie i co 6 h, 7 najnowszych) - zawieraja tez klucz TLS, wiec trzeba je chronic jak sam katalog. Serwer ma
limity polaczen, wiadomosci, czatu i zakladania kont z jednego adresu.

## Dokumentacja

- [docs/SERVER.md](docs/SERVER.md) - uruchomienie serwera na VPS (Docker, systemd, kopie, bezpieczenstwo)
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
