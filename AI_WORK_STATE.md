# AI_WORK_STATE

Plik stanu dla kolejnych sesji pracy. **Po wznowieniu przeczytaj go razem z TODO.md.**

## Podsumowanie (2026-10-16, wersja 0.56.0)

- Repozytorium `fawizja/Pixel` bylo puste; projekt utworzony od zera i wypchniety na `main`.
- Gra dziala lokalnie i online: modul `server` (TCP) + tryb online klienta desktopowego (`-Dpixel.server`).
  PvP (etap 13), konta z haslem, menu i ekran logowania (takze APK). TLS z przypinaniem certyfikatu.
- Ukonczone: etapy 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15 oraz czesci etapow 2, 9, 10, 11, 14, 16 (szczegoly w TODO.md).
- 0.1.0 = 301ac0a, 0.2.0 = f6142e4, 0.3.0 = f554971, 0.4.0 = f77ddc9, 0.5.0 = 561ad81, 0.6.0 = 9be0eca, 0.7.0 = e6268c5, 0.8.0 = b759340, etap 14 cz.1 = 9b914f5 (wszystkie z zielonym CI i APK). 0.9.0 = 143c40b (klient online, CI zielone). 0.10.0 = 4613838 (PvP, CI zielone). 0.11.0 = 9511573 (konta/logowanie/czat, CI zielone). 0.11.1 = a97ad23 (przewidywanie ruchu, CI zielone). 0.12.0 = 955b3c1 (TLS, CI zielone). 0.12.1 = e6c5175 (limity serwera), kopie zapasowe = b64d06a, endgame cz.1 = 05d40b4 (potwory, sprzet, symulacje; CI zielone). 0.13.0 = d0f3c45 (wschodni kontynent i straznik mostu, CI zielone). 0.13.1 = f3d52d5 (oboz, kuznia, CI zielone). 0.13.2 = 9e8bda2 (blokada PZ po walce, trwale czaszki, KEEP; CI zielone). 0.14.0 = bf72e4c (PvP czarami i runami - etap 13 domkniety, CI zielone). 0.14.1 = aa6d179 (zarzadzanie kontem, limit kont; CI zielone). 0.15.0 = 65f6622 (ustawienia gracza, etap 15 domkniety; CI zielone). 0.15.1 = 9e446f6 (regresja GUI, CI zielone razem z zadaniem gui-regression). 0.15.2 = 4264d68 (ryby biomow, CI zielone). 0.15.3 = f6ade77 (nocne spawny, CI zielone). 0.16.0 = 2785850 (lancuchy zadan, rozmowy slowami kluczowymi; CI zielone). 0.16.1 = 107fa42 (efekty graficzne czarow, CI zielone). 0.16.2 = c75acf3 (wlasny pasek czarow, CI zielone). 0.17.0 = 2cdebc8 (ciala potworow; CI czerwone przez niestabilny test hasla keytool, naprawione w 0.17.1). 0.17.1 = 962fc65 (wyzyny +1..+3 poza wieza Varden, poprawka TLS; CI zielone). 0.18.0 = 1a78aef (CI zielone): dzwieki syntezowane (audio/SoundSynth, SoundCues, client/audio/SoundPlayer), glosnosc w ustawieniach, animacja ciosu wrecz. 0.18.1 = perf/FrameStats (log Klatki: co 30 s), zadanie CI android-smoke (emulator API 30, tools/android-smoke/run.sh), autostart w APK debug; CI zielone (678144f), emulator ok. 0.19.0 = druzyny (party/PartyManager, PartyRules, ServerWorld.shareKill/partyCommand, ChatCommands /zapros /przyjmij /opusc /d; CI zielone 25a5a66). 0.20.0 = handel (trade/PlayerTrade, ServerWorld.tradeCommand, /handel /oferuj /akceptuj /anuluj). CI zielone (1e1a5c6). 0.20.1 = okno handlu w HUD (TradeView, komunikat TS, scena GUI trade). CI zielone (6366c53). 0.21.0 = gildie (guild/GuildRegistry, ServerWorld.guildCommand, guilds.txt, PL pole 10). CI zielone (bc67ee0). 0.21.1 = menu gracza po dotknieciu (Hud.openPlayerMenu, akcje player:*), scena GUI player. CI zielone (5c23e04). 0.21.2 = panel druzyny (GameScreen.drawPartyPanel), scena GUI party (13 scen); CI zielone (504fb2a). 0.22.0 = server/Dockerfile, docs/SERVER.md, zadanie CI server-image; CI zielone. 0.23.0 = PM (/w), moderacja (server/Moderation, ServerWorld.moderate, konsola PixelServer.startConsole, admins.txt, moderation.txt); CI zielone (6b989d5). 0.24.0 = polskie litery (client/ui/PolishFont skladana z lsans-15, ChatCommands.key bez diakrytykow, teksty i dane CSV z polskimi znakami; CI zielone 8acd8df). 0.25.0 = polskie nazwy przedmiotow i potworow (items.csv / monsters.csv kolumna name), PlayerTrade.parse bez diakrytykow; CI zielone 8cb368d. 0.26.0 = ranking (ranking/Ranking, RankingView, komunikat RANK, ServerWorld.loadRanking/sendRanking, Hud.openRanking, scena GUI ranking - 14 scen). 0.27.0 = depozyt przedmiotow (PlayerCharacter.depot 60 slotow, CharacterCodec dp.*, GameSession.depotStore/depotTake, akcje npc:store/npc:take, Hud depotView, scena GUI depot - 15 scen). 0.28.0 = znajomi (friends/FriendList, FriendsView, komunikaty FRIEND/FRIENDS, ServerWorld.friendCommand/tellFriends, Ranking.get, Hud.openFriends, player:friend, scena GUI friends - 16 scen). 0.29.0 = rynek graczy (market/Market + MarketView, market.txt, komunikaty MARKET LIST|SELL|BUY|CANCEL, ServerWorld.marketCommand, BankAccount.credit/debit, PlayerTrade.parse publiczne, Hud.openMarket, npc:market, scena GUI market - 17 scen). 0.30.0 = bestiariusz i osiagniecia (achievement/Bestiary, Achievement, Achievements; PlayerCharacter.bestiary/achievements, kodek kill.* i achievements, GameSession.checkAchievements w killListener i co 1 s w update, Hud.openAchievements, klawisz O, sceny GUI achievements i bestiary - 19 scen). 0.31.0 = inwazje (event/Invasion z planami miast, MonsterManager.spawnEvent/clearEvent bez odradzania, CombatSystem.setDeathListener, ServerWorld.updateInvasion/startInvasion/finishInvasion, ADMIN INVASION, -Dpixel.invasionMinutes). 0.32.0 = zadanie dnia (daily/DailyTask, PlayerCharacter.daily, kodek daily, GameSession.dailyAction/setToday/today, MonsterManager.spawnedTypes, akcja npc:daily, wiersz u przewodnika, scena GUI guide - 20 scen). 0.33.0 = wierzchowiec (przedmiot saddle id 126 w 4 sklepach, PlayerCharacter.hasMount/isMounted/MOUNT_SPEED, flagi H/M, GameSession.useSaddle/toggleMount, akcja mount, przycisk KON / klawisz M, TileArt.horse, PL pole 11 M|-, scena GUI mount - 21 scen). 0.34.0 = poczta (mail/Mailbox, mail.txt, MAIL SEND imie tekst, ServerWorld.mailCommand/deliverMail przy wejsciu, /wyslij). 0.35.0 = skrzynie skarbow (treasure/TreasureChests.generate - BFS osiagalnosci przez przejscia, seed 20261015; PlayerCharacter.openedChests, kodek chests, GameSession.setChests/openChest w useNearby, TileArt.chest, scena GUI chest - 22 sceny). 0.36.0 = minimapa (world/MinimapPalette - kolor piksela na kafelek, client/ui/Minimap - tekstury pieter budowane leniwie, Hud.toggleMap/mapOpen, klawisz J, pixel.open=map, scena GUI map - 23 sceny). 0.37.0 = marsz do celu (movement/Pathfinder.path, GameScreen.walkTo/walkStep/stopWalk - kierunek jak z joysticka, akcja map:goto:x,y z Hud, Hud.setWalkTarget, -Dpixel.walkto). 0.38.0 = dotknij, aby isc (GameScreen.tapTile -> walkTo quiet/nextTo, pathNextTo, walkNpc otwiera rozmowe po dojsciu, -Dpixel.tap). 0.39.0 = pojedynki (PvpRules.Status.duelPartner/duelUntil tylko w pamieci, startDuel/endDuel, duelHit -> Outcome.DUEL_WON; ServerWorld.duelCommand/leftDuel/duelOver/updateDuels, komunikat DUEL REQUEST|SURRENDER, /pojedynek, player:duel). 0.40.0 = nagroda za logowanie (daily/LoginStreak, PlayerCharacter.loginStreak, kodek login, GameSession.checkLoginReward w update co 60 s; testy sieciowe sald dodaja NetworkSyncTest.LOGIN_BONUS). 0.41.0 = dymki czatu (GameScreen.say/drawBubble, mapa bubbles, komunikat CHAT bez nawiasu = czat ogolny; scena GUI chat - 24 sceny). 0.42.0 = dziennik zadan (Hud.openJournal/journalMode/rebuildJournalMenu, akcje journal:goto:npc i journal:guide -> GameScreen walkTo + walkNpc, nearestGuide, klawisz Z, /zadania, book:journal, -Dpixel.button, scena GUI journal - 25 scen). 0.43.0 = automatyczne lupy (GameSettings.autoLoot / settings:loot / klucz loot, GameScreen.autoLoot co 0,8 s przez act("loot"), max 2 proby na cialo). 0.44.0 = licznik bossow (MonsterManager.bossReport/BossStatus/row/describe, komunikat BOSS z wierszami imie;miejsce;stan, Hud.openInfo, npc:bosses u przewodnika, /bossowie, scena GUI bosses - 26 scen). 0.45.0 = porownanie przedmiotow (item/ItemCompare.stats/requirements, ramka nad plecakiem Hud.bagTipX/W, Hud.selectSlot, pixel.open=bagitem, scena GUI bagitem - 27 scen). 0.46.0 = zwoj powrotu (przedmiot 127 return_scroll w 4 sklepach po 300 gp bez skupu, GameSession.startRecall/updateRecall, RECALL_MS 3 s). 0.47.0 = analiza polowania (stats/HuntAnalyzer - migawka exp/zabicia z bestiariusza/zloto w plecaku, GameScreen.openHunt/updateHunt co 1 s, Hud.infoOpen/updateInfo, wiersz info z akcja r[3], book:hunt, hunt:reset, klawisz H, /analiza). 0.48.0 = manekiny treningowe (training/TrainingDummies.generate(world, pozycje NPC) - 2 na miasto w PZ, GameSession.setTownServices liczy dummies, toggleTraining w useNearby, updateTraining co TRAIN_MS 3 s, manekin blokuje ruch w occupancy, TileArt.dummy, GameScreen.drawChests rysuje tez manekiny, tapTile na manekinie). 0.49.0 = pogoda (world/Weather.at(epochMs, region, z) - okna 20 min, 30% opadow, rodzaj z nazwy krainy; intensity, ambientFactor; client/render/WeatherRenderer; GameScreen.weatherAt/forcedWeather/frozenWeather - przy -Dpixel.hour pogodnie i nieruchome krople; -Dpixel.weather; scena GUI rain - 28 scen) + wartosc przedmiotow w HuntAnalyzer (konstruktor z ItemRegistry). 0.50.0 = woznice (NpcRole.CARRIER - tylko w miescie, npc/Travel.price/routes, GameSession.travel - placi z plecaka, odmowa w blokadzie PZ i przy bramie poziomu, cel = wolne pole obok woznicy docelowego; Hud case CARRIER, akcja npc:travel:Miasto; npcs.csv dopisane recznie z polskimi znakami, generate_world.py ma te same wiersze bez znakow - nie nadpisuj npcs.csv generatorem). 0.51.0 = blogoslawienstwo (PlayerCharacter.blessed + kodek bless, applyDeathPenalty zwraca true gdy zuzyte, Balance.experienceAfterDeath(..., blessed) 2,5%, Balance.blessingPrice, GameSession.priestBless, akcja npc:bless) + poprawka wyscigu w NetworkSyncTest (czeka na drugie wyjscie znajomego przed LIST). 0.52.0 = ustawienia settings:heal (GameSettings.autoHeal 0/30/50/70, GameScreen.autoHeal -> act potion_hp co >= 1 s) i settings:weather (GameSettings.weather); test pojedynku zabija potwory w promieniu 25 od pola (ServerWorld.monsters). 0.53.0 = podsumowanie smierci (combat/DeathRecap: okno 30 s, max 64 trafien, build czysci; CombatSystem.recordDamage/lastDeath; PvpRules.hit zapisuje trafienia graczy; komunikaty DEATH -> DEATHLOG; GameScreen.checkDeath lokalnie, online LOG 'Zginąłeś' -> DEATH; /smierc; -Dpixel.open=death). 0.54.0 = potwory elitarne (MonsterType.eliteVariant - ten sam klucz, nazwa + ' (elita)', 2x HP, 1,3x atak, 3x exp, 2x szansa lupow; eliteRoll(id) 4%; MonsterManager.spawnType; MO pole 8 'E'; GameScreen rysuje 1,3x zlociscie) + obrys polskich liter (PolishFont.outline, glif +1 px marginesu). 0.55.0 = 4 osiagniecia (Kind ELITES/SKILL/CHESTS; Bestiary.eliteKills + kodek elites), strony w oknie osiagniec (Hud.achPage, ach:page); CI zielone (e32539a). 0.56.0 = potwor dnia (daily/BoostedCreature.pick(dzien, spawnedTypes) - nie boss, Random z dnia; CombatSystem.setBoostedKey/isBoosted/killExperience, rollLoot z boostChance; ServerWorld.shareKill liczy przez killExperience; GameSession.boostedCreature/checkBoost; GameScreen /potwor, wiersz w huntRows, -Dpixel.day, staly dzien 20000 przy -Dpixel.hour). Uwaga: `spawns.csv` (takze nocne) generuje tools/mapgen - nie edytuj recznie.

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

- 0.14.1: `./gradlew test` - OK, 191 testow (zmiana hasla i usuniecie konta przez TCP).
- 0.14.0: `./gradlew test` - OK, 188 testow.
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
- Grafika jest tymczasowa (proceduralna). Czcionka: lsans-15 z libGDX + polskie litery skladane w `PolishFont`;
  nowe napisy pisz z polskimi znakami (komentarze w kodzie nadal bez). Nazwy regionow i klucze danych zostaja ASCII.
- Efekty czarow sa proste (ksztalty, bez sprite); dzwieki syntezowane w kodzie (0.18.0).
- Symulacje walk to model "stoimy i wymieniamy ciosy" (pesymistyczny dla profesji dystansowych).
- PvP tylko atakiem podstawowym; limity serwera i kopie zapasowe sa. Endgame 230..1000 jest na wschodnim kontynencie z obozem (kupiec, bank, zadania) i kuznia.
- Miasta poza Varden maja tylko swiatynie, plac i domy (bez sklepow do etapu 12).

## Punkt wznowienia

**Stan: wszystkie 16 etapow zrobione poza pomiarem klatek na prawdziwym telefonie (wymaga urzadzenia)
i publicznym serwerem (wymaga VPS wlasciciela, instrukcja w docs/SERVER.md).** Kolejne pomysly (TODO.md):
1. Nastepne funkcje MMO (np. domy graczy, depozyt w banku na przedmioty) albo tresci (nowe zadania).
2. Pomiar na telefonie: `tools/android-smoke/run.sh` przez adb, gdy wlasciciel podlaczy urzadzenie.
Uruchamianie GUI w chmurze: `DISPLAY=:99` (Xvfb :99), najpierw `./gradlew :desktop:installDist`.
Regresja GUI: `tools/gui-regression/run.sh` (sceny w `scenes.txt`); po zamierzonej zmianie wygladu `--update`
i obejrzenie nowych wzorcow przed commitem.
Menu: `MenuScreen` -> `PixelGame.playLocal()` albo `LoginScreen` -> `connectOnline` (watek) -> `startOnline`.
Test GUI bez myszy: Xvfb :99 + python-xlib (XTEST) do klikania i pisania - skrypt w opisie sesji, nie w repo.
Tryb online: `GameScreen(game, pc, NetClient)` - bez symulacji, `syncWithServer` odwzorowuje SELF/FRAME/LOG/EV.
Uruchomienie lokalne: `server/build/install/server/bin/server 7272 <katalog>` i klient z `-Dpixel.server=127.0.0.1:7272`.
Mapy swiata zmieniaj tylko przez `tools/mapgen/generate_world.py`, potem `./gradlew :shared:test`.
Generator: zachod (x < 160) uzywa `rnd`, wschod `rnd_east` - nie mieszaj, bo zmieni sie caly stary swiat.
Nowe symbole legendy wschodu to greckie litery (pliki map w UTF-8). Balans endgame: `EndgameSimulationTest`.
