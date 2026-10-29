package pl.fawizja.pixel.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import pl.fawizja.pixel.client.PixelGame;
import pl.fawizja.pixel.client.screen.GameScreen;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Uruchamianie na PC: ./gradlew :desktop:run
 *
 * Opcje deweloperskie (wlasciwosci systemowe, przydatne w CI i do zrzutow ekranu):
 *   -Dpixel.uiScale=2                 uklad HUD jak na telefonie (skala interfejsu)
 *   -Dpixel.autostart=Imie:PALADIN    pomija menu: gra lokalna (tworzy postac, jesli nie ma zapisu)
 *   -Dpixel.server=host:port          z autostart: logowanie na serwer (haslo -Dpixel.password, domyslnie haslo-dev)
 *                                     bez autostart: domyslny adres w ekranie logowania
 *   -Dpixel.teleport=x,y,z            ustawia postac na pozycji (np. 8,23,-1)
 *   -Dpixel.screenshot=plik.png       zapisuje zrzut ekranu po pixel.frames klatkach i konczy
 *   -Dpixel.frames=120
 *   -Dpixel.hour=22                   godzina gry na starcie sesji (np. noc do zrzutow ekranu); wlacza tez staly
 *                                     czas: 50 ms na klatke, staly dzien i czas rzeczywisty - zrzut nie zalezy od maszyny
 *   -Dpixel.frameMs=N                 krok czasu sesji na klatke (0 = zegar rzeczywisty)
 *   -Dpixel.level=N      podglad postaci na wyzszym poziomie (zrzuty)
 *   -Dpixel.npcPage=N    strona listy kupca przy -Dpixel.open=npc
 *   -Dpixel.partySize=N  przy -Dpixel.open=party: wielkosc podgladanej druzyny (2..5, rozne profesje, premia exp)
 *   -Dpixel.button=id    akcja przycisku/wiersza menu (np. journal:goto:varden_guide)
 *   -Dpixel.exportArt=dir zapisuje obecne grafiki jako PNG pod nazwami z docs/GRAPHICS.md (dla grafikow)
 *   -Dpixel.button2=id   druga akcja po pierwszej (np. use, potem cmd:/statystyki)
 *   -Dpixel.tap=x,y      dotkniecie pola swiata (marsz, NPC, cel)
 *   -Dpixel.look=x,y     podglad pola (jak dlugie dotkniecie na telefonie)
 *   -Dpixel.altarGhosts=PALADIN,DRUID|ALL  offline: "duchy" profesji stoja przed oltarzami zadan (test bez 5 graczy)
 *   -Dpixel.bossGhosts=4  offline: "duchy" uzupelniaja druzyne na polach przy dzwigni wyprawy na bossa (test w 1 osobe)
 *   -Dpixel.bossWin=true  offline: boss wyprawy (po dzwigni z -Dpixel.tap) ginie od razu - podglad lupu i portalu
 *   -Dpixel.joy=fx,fy    joystick pod kciukiem w punkcie widoku gry (ulamki 0..1) wychylony w prawo-gore (zrzuty)
 *   -Dpixel.walkto=x,y   marsz do pola (jak dotkniecie duzej mapy)
 *   -Dpixel.open=playerpopup|vip|buffs|book|bag|bagitem|character|npc|depot|friends|market|mount|pet|smith|chest|map|chat|journal|achievements|bestiary|diag|trade|player|pvp|pzblock|skull_red|skull_white|party|ranking|death|bagmess|bagpages|bagkeep|trap|battle|picker|pickerrune   otwiera ksiege, plecak, karte postaci, rozmowe z NPC, diagnostyke albo podglad okna handlu
 *   -Dpixel.mobileHud=1  uklad HUD-u na telefon (wariant B: widok gry na calym ekranie, luk czarow); na Androidzie zawsze
 *   -Dpixel.open=coins|torchsel|torchlit  monety w plecaku, zaznaczona pochodnia, zapalona pochodnia w polu obok butow (z mobile_* po przecinku)
 *   -Dpixel.open=mobile_win_bag|mobile_win_equip|mobile_win_skills|mobile_win_vip|mobile_win_settings|mobile_chat|mobile_picker|mobile_logout|mobile_journal
 *                        stany ukladu na telefon (kilka po przecinku); -Dpixel.pickerButton=N (0..6) przy mobile_picker
 *   -Dpixel.conTab=N     zakladka czatu (0 Lokalny, 1 Serwer, 2 Handel, potem Gildia/Druzyna i rozmowy); -Dpixel.open=chattabs: przykladowe wiadomosci
 *   -Dpixel.seed=1                    stale ziarno potworow (testy regresji GUI)
 *   -Dpixel.fx=true                   tuz przed zrzutem pokazuje efekty (pociski, wybuch ognia, mrozna fala, kamienie, leczenie) i dwa ciala
 */
public final class DesktopLauncher {

    public static void main(String[] args) {
        pl.fawizja.pixel.client.render.TileArt.exportDir = System.getProperty("pixel.exportArt");
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixel");
        config.setWindowedMode(Integer.getInteger("pixel.width", 1280), Integer.getInteger("pixel.height", 720));
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setIdleFPS(10);
        new Lwjgl3Application(new DevPixelGame(), config);
    }

    private static final class DevPixelGame extends PixelGame {
        private final String autostart = System.getProperty("pixel.autostart");
        private final String teleport = System.getProperty("pixel.teleport");
        private final String screenshot = System.getProperty("pixel.screenshot");
        private final int frames = Integer.getInteger("pixel.frames", 120);
        private final Integer hour = Integer.getInteger("pixel.hour");
        /** Czas rzeczywisty przy -Dpixel.hour (regresja GUI): 2026-01-01 12:00 UTC. */
        private static final long FIXED_WALL_MS = 1_767_268_800_000L;
        private boolean clockSet;
        private int frame;
        private boolean teleported;

        @Override
        public void create() {
            super.create();
            if (autostart == null) {
                if ("login".equals(System.getProperty("pixel.open"))) {
                    setScreen(new pl.fawizja.pixel.client.screen.LoginScreen(this));
                }
                return;
            }
            String[] p = autostart.split(":");
            Vocation v = Vocation.valueOf(p.length > 1 ? p[1] : "PALADIN");
            String server = platform().serverAddress();
            if (server != null) {
                OnlineLogin login = connectOnline(server, p[0], v.name(), System.getProperty("pixel.password", "hasło-dev"));
                if (login.problem() == null) {
                    startOnline(login);
                    return;
                }
                Gdx.app.error("Pixel", "Online niedostępne: " + login.problem());
            }
            if (localSaveSummary() != null) playLocal();
            else createCharacter(p[0], v);
        }

        @Override
        public void render() {
            if (!clockSet && hour != null && getScreen() instanceof GameScreen) {
                clockSet = true;
                // staly czas rzeczywisty (czynsz domu: "nastepna platnosc za ...") - zrzut nie zalezy od chwili uruchomienia
                ((GameScreen) getScreen()).session().setWallClock(() -> FIXED_WALL_MS);
                ((GameScreen) getScreen()).session().setClock(
                        new pl.fawizja.pixel.time.WorldClock(-hour * pl.fawizja.pixel.time.WorldClock.GAME_HOUR_MS,
                                pl.fawizja.pixel.time.WorldClock.REAL_DAY_MS, 0.38f));
            }
            if (!teleported && teleport != null && getScreen() instanceof GameScreen) {
                String[] p = teleport.split(",");
                ((GameScreen) getScreen()).session().teleport(
                        new Position(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())), 0L);
                teleported = true;
                int level = Integer.getInteger("pixel.level", 0);
                pl.fawizja.pixel.character.PlayerCharacter me = ((GameScreen) getScreen()).session().character();
                while (me.level() < level) me.addExperience(Math.max(1000L, me.experience()));
                String open = System.getProperty("pixel.open");
                if ("book".equals(open)) ((GameScreen) getScreen()).hud().toggleBook();
                if ("bag".equals(open)) ((GameScreen) getScreen()).hud().toggleBag();
                if ("bagitem".equals(open)) {
                    // podglad opisu przedmiotu: pierwszy przedmiot z miejscem na ciele
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().toggleBag();
                    pl.fawizja.pixel.inventory.Container bp = gs.session().character().backpack();
                    bp.add(new pl.fawizja.pixel.item.ItemStack(gs.session().items().byKey("iron_sword").id(), 1));
                    for (int i = 0; i < bp.size(); i++) {
                        if (bp.get(i) != null && gs.session().items().get(bp.get(i).itemId()).slot() != pl.fawizja.pixel.item.EquipSlot.NONE) {
                            gs.hud().selectSlot(i);
                            break;
                        }
                    }
                }
                if ("bagpages".equals(open)) {
                    // podglad wielu plecakow (etap 14): dwa dodatkowe plecaki, monety i lup na drugiej stronie
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter hero = gs.session().character();
                    while (hero.extraBags() < 2) hero.attachBag();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    String[][] loot = {{"gold_coin", "100", "20"}, {"gold_coin", "100", "21"}, {"platinum_coin", "37", "22"},
                            {"crystal_coin", "3", "23"}, {"coral_helmet", "1", "25"}, {"pearl", "12", "26"}, {"backpack", "1", "27"}};
                    for (String[] m : loot) {
                        if (reg.containsKey(m[0])) hero.backpack().putInSlot(Integer.parseInt(m[2]), new pl.fawizja.pixel.item.ItemStack(reg.byKey(m[0]).id(), Integer.parseInt(m[1])));
                    }
                    gs.hud().toggleBag();
                    gs.hud().showBagPage(Integer.getInteger("pixel.bagPage", 1));
                }
                if ("bagmess".equals(open)) {
                    // podglad porzadkowania: rozrzucone stosy i dziury w plecaku (z -Dpixel.button=bag_sort po sortowaniu)
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().toggleBag();
                    pl.fawizja.pixel.inventory.Container bp = gs.session().character().backpack();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    String[][] mess = {{"health_potion", "3", "13"}, {"iron_sword", "1", "2"}, {"mana_potion", "4", "18"},
                            {"health_potion", "5", "7"}, {"leather_armor", "1", "11"}, {"mana_potion", "2", "4"}};
                    for (String[] m : mess) {
                        if (!reg.containsKey(m[0])) continue;
                        int slot = Integer.parseInt(m[2]);
                        if (bp.get(slot) == null) bp.putInSlot(slot, new pl.fawizja.pixel.item.ItemStack(reg.byKey(m[0]).id(), Integer.parseInt(m[1])));
                    }
                }
                if ("bagkeep".equals(open)) {
                    // podglad "Zachowaj": mikstury zdrowia oznaczone i zaznaczone w plecaku
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().toggleBag();
                    pl.fawizja.pixel.inventory.Container bp = gs.session().character().backpack();
                    int hp = gs.session().items().byKey("health_potion").id();
                    bp.add(new pl.fawizja.pixel.item.ItemStack(hp, 8));
                    bp.sort();
                    for (int i = 0; i < bp.size(); i++) {
                        if (bp.get(i) != null && bp.get(i).itemId() == hp) {
                            gs.session().toggleKeepSlot(i, gs.worldTime());
                            gs.hud().selectSlot(i);
                            break;
                        }
                    }
                }
                if ("death".equals(open)) {
                    // podglad podsumowania smierci: kilka trafien i smierc (okno otworzy sie samo)
                    GameScreen gs = (GameScreen) getScreen();
                    long t = gs.worldTime();
                    pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                    for (int i = 0; i < 4; i++) cs.recordDamage("Wilk", 18 + i, t);
                    for (int i = 0; i < 2; i++) cs.recordDamage("Goblin szaman", 44, t);
                    cs.diedFromPlayer("Kuba", false, t);
                }
                if (open != null && open.startsWith("house_")) {
                    // podglad okna domu przy drzwiach: house_free (wolny, Kup), house_owned (zajety przez Halvara),
                    // house_owner (wlasciciel w srodku, goscie), house_invite (wpisywanie imienia); -Dpixel.houseId=id
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.game.GameSession s = gs.session();
                    pl.fawizja.pixel.character.PlayerCharacter pc = s.character();
                    String hid = System.getProperty("pixel.houseId");
                    pl.fawizja.pixel.house.House h = hid != null ? s.houses().get(hid) : s.houses().inTown("Varden").get(0);
                    for (int bit = 0; bit < 64; bit++) pc.markTip(bit);
                    boolean owner = open.equals("house_owner") || open.equals("house_invite");
                    pl.fawizja.pixel.house.HouseOwnersMap owners = new pl.fawizja.pixel.house.HouseOwnersMap();
                    if (open.equals("house_owned")) owners.claim(h.id(), "Halvar");
                    if (owner) {
                        owners.claim(h.id(), pc.name());
                        pc.setHouse(h.id());
                        owners.setRent(h.id(), (hour != null ? FIXED_WALL_MS : System.currentTimeMillis()) + 5 * 86_400_000L + 3 * 3_600_000L, 0);
                        owners.addGuest(h.id(), "Ula", pl.fawizja.pixel.house.HouseAccess.FULL);
                        owners.addGuest(h.id(), "Stary Wilk", pl.fawizja.pixel.house.HouseAccess.ENTRY);
                    } else {
                        pc.bank().credit(h.price() + 12_500);
                    }
                    s.setHouses(s.houses(), owners);
                    Position stand = null;
                    for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                        Position n = h.door().translate(d.dx, d.dy);
                        if (!d.diagonal() && s.world().isWalkable(n) && h.contains(n) == owner && s.npcs().at(n) == null) {
                            stand = n;
                            break;
                        }
                    }
                    if (stand != null) s.teleport(stand, 0L);
                    gs.tapTile(h.door());
                    if (open.equals("house_invite")) gs.hud().previewHouse("house:invitefull", "Stary Wilk");
                }
                if ("bossloot".equals(open)) {
                    // podglad lupu bossa (-Dpixel.boss=morgath): osobny rzut dla kazdego z 5 uczestnikow wyprawy;
                    // ziarno szukane tak, by ktos trafil przedmiot tylko z bossow (inaczej przy 1-5% zwykle nic)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    pl.fawizja.pixel.monster.MonsterType boss = pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(reg)
                            .get(System.getProperty("pixel.boss", "morgath"));
                    java.util.List<String> party = java.util.List.of(gs.session().character().name(), "Ula", "Kuba", "Mira", "Olaf");
                    java.util.Map<String, java.util.List<pl.fawizja.pixel.item.ItemStack>> rolls = null;
                    for (long seed = 1; seed < 10_000; seed++) {
                        rolls = pl.fawizja.pixel.monster.LootRules.rollBossLootPerPlayer(boss, reg, party, new java.util.Random(seed));
                        boolean rare = rolls.values().stream().flatMap(java.util.List::stream)
                                .anyMatch(s -> pl.fawizja.pixel.monster.LootRules.isBossOnly(reg.get(s.itemId())));
                        if (rare) break;
                    }
                    java.util.List<String[]> rows = new java.util.ArrayList<>();
                    for (java.util.Map.Entry<String, java.util.List<pl.fawizja.pixel.item.ItemStack>> e : rolls.entrySet()) {
                        java.util.Map<Integer, Integer> sum = new java.util.LinkedHashMap<>();
                        for (pl.fawizja.pixel.item.ItemStack s : e.getValue()) sum.merge(s.itemId(), s.count(), Integer::sum);
                        java.util.List<String> parts = new java.util.ArrayList<>();
                        boolean rare = false;
                        for (java.util.Map.Entry<Integer, Integer> s : sum.entrySet()) {
                            pl.fawizja.pixel.item.ItemType it = reg.get(s.getKey());
                            boolean bossOnly = pl.fawizja.pixel.monster.LootRules.isBossOnly(it);
                            rare |= bossOnly;
                            parts.add((s.getValue() > 1 ? s.getValue() + "x " : "") + it.name() + (bossOnly ? " (tylko z bossa!)" : ""));
                        }
                        rows.add(new String[] {e.getKey() + (rare ? " - rzadki łup!" : ""), parts.isEmpty() ? "nic" : String.join(", ", parts)});
                    }
                    gs.hud().openInfo("Łup: " + boss.name() + " (osobny rzut na gracza)", rows);
                }
                if ("character".equals(open)) ((GameScreen) getScreen()).hud().toggleCharacter();
                if ("diag".equals(open)) ((GameScreen) getScreen()).hud().toggleDiagnostics();
                if ("party".equals(open)) {
                    GameScreen gs = (GameScreen) getScreen();
                    // -Dpixel.partySize=N (2..5): ja + czlonkowie innych profesji; premia jak w PartyRules
                    int size = Math.max(2, Math.min(5, Integer.getInteger("pixel.partySize", 3)));
                    pl.fawizja.pixel.character.PlayerCharacter lead = gs.session().character();
                    java.util.List<pl.fawizja.pixel.party.PartyView.Member> rows = new java.util.ArrayList<>();
                    rows.add(new pl.fawizja.pixel.party.PartyView.Member(lead.name(), lead.level(), lead.vocation(), true));
                    String[] names = {"Ula", "Kuba", "Ela", "Ada"};
                    int i = 0;
                    for (pl.fawizja.pixel.vocation.Vocation v : pl.fawizja.pixel.vocation.Vocation.values()) {
                        if (v == lead.vocation() || rows.size() >= size) continue;
                        rows.add(new pl.fawizja.pixel.party.PartyView.Member(names[i++], Math.max(1, lead.level() - i), v, true));
                    }
                    gs.previewParty(pl.fawizja.pixel.party.PartyView.of(rows));
                }
                if ("player".equals(open)) ((GameScreen) getScreen()).hud().openPlayerMenu(2, "Ula", false, false);
                if ("pvp".equals(open)) ((GameScreen) getScreen()).previewPvp();
                if ("playerpopup".equals(open)) ((GameScreen) getScreen()).previewPlayerPopup();
                if ("vip".equals(open)) {
                    // podglad listy VIP (bez serwera): znajomi w grze (zieloni) i poza gra (czerwoni)
                    ((GameScreen) getScreen()).previewVip(new pl.fawizja.pixel.friends.FriendsView(java.util.List.of(
                            new pl.fawizja.pixel.friends.FriendsView.Row("Ula", true, "Druid", 212),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Stary Wilk", false, "Monk", 175, 3 * 60 + 20),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Kuba", true, "Paladin", 98),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Mira", false, "Sorcerer", 160, 5 * 24 * 60),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Olaf", true, "Knight", 142))));
                }
                if ("buffs".equals(open)) {
                    // podglad pola wzmocnien obok butow: przyspieszenie, mikstura sily, jedzenie i biala czaszka
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pc.enchantments().extend(pl.fawizja.pixel.item.Enchantments.Kind.STRENGTH);
                    pc.addHaste(30, 90_000);
                    pc.feed(20 * 60_000);
                    gs.previewPvp();
                }
                if ("pzblock".equals(open)) ((GameScreen) getScreen()).previewPzBlock();
                if ("skull_red".equals(open)) ((GameScreen) getScreen()).previewOwnSkull(pl.fawizja.pixel.pvp.PvpRules.Skull.RED);
                if ("skull_white".equals(open)) ((GameScreen) getScreen()).previewOwnSkull(pl.fawizja.pixel.pvp.PvpRules.Skull.WHITE);
                if ("trade".equals(open)) {
                    // podglad okna handlu (bez serwera): przykladowe oferty
                    ((GameScreen) getScreen()).hud().setTrade(new pl.fawizja.pixel.trade.TradeView(
                            "Ula", "5x Mikstura zdrowia, 1x Lina", "700 gp", false, true));
                }
                if ("ranking".equals(open)) {
                    // podglad okna rankingu (bez serwera): przykladowe miejsca
                    java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
                    String[][] sample = {{"Ula", "Druid", "212"}, {"Kuba", "Paladin", "198"}, {"Stary Wilk", "Monk", "175"},
                            {"Mira", "Sorcerer", "160"}, {"Olaf", "Paladin", "142"}, {"Lena", "Druid", "120"},
                            {"Test", "Paladin", "98"}, {"Kora", "Monk", "77"}, {"Petra", "Sorcerer", "61"}, {"Quin", "Druid", "40"}};
                    for (int i = 0; i < sample.length; i++) {
                        rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i + 1, sample[i][0], sample[i][1],
                                Integer.parseInt(sample[i][2])));
                    }
                    ((GameScreen) getScreen()).hud().openRanking(
                            new pl.fawizja.pixel.ranking.RankingView("Ranking serwera", 7, 23, rows));
                }
                if ("weekly".equals(open)) {
                    // podglad rankingu tygodnia: zdobyte doswiadczenie
                    java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
                    String[][] sample = {{"Kuba", "Paladin", "4820000"}, {"Ula", "Druid", "3915000"}, {"Mira", "Sorcerer", "2210500"},
                            {"Test", "Paladin", "1840000"}, {"Olaf", "Paladin", "960000"}, {"Kora", "Monk", "412300"}};
                    for (int i = 0; i < sample.length; i++) {
                        rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i + 1, sample[i][0], sample[i][1],
                                Integer.parseInt(sample[i][2])));
                    }
                    ((GameScreen) getScreen()).hud().openRanking(
                            new pl.fawizja.pixel.ranking.RankingView(pl.fawizja.pixel.ranking.WeeklyRanking.TITLE, 4, 6, rows));
                }
                if ("achievements".equals(open) || "bestiary".equals(open)) {
                    // podglad: troche pokonanych potworow i jeden boss
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.achievement.Bestiary b = gs.session().character().bestiary();
                    for (int i = 0; i < 120; i++) b.record("wolf", false);
                    for (int i = 0; i < 34; i++) b.record("goblin", false);
                    for (int i = 0; i < 9; i++) b.record("goblin_shaman", false);
                    b.record("grimtooth", true);
                    gs.session().character().achievements().check(gs.session().character());
                    gs.hud().openAchievements("bestiary".equals(open));
                }
                if ("mount".equals(open)) {
                    // podglad: postac na koniu (przycisk KON widoczny)
                    pl.fawizja.pixel.character.PlayerCharacter pc = ((GameScreen) getScreen()).session().character();
                    pc.setHasMount(true);
                    pc.setMounted(true);
                }
                if ("pet".equals(open)) {
                    // podglad: pupil chodzi za postacia (-Dpixel.pet=pet_cat itd., domyslnie szczenie)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    String pet = System.getProperty("pixel.pet", "pet_dog");
                    pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(gs.session().items().byKey(pet).id(), 1));
                    pc.setPet(pet);
                }
                if ("chest".equals(open)) {
                    // podglad: obok skrzyni bez silnych potworow w poblizu, najblizej swiatyni Varden
                    // (z pochodnia, zeby bylo widac); inaczej postac z poziomu 8 ginie przed zrzutem i obraz jest losowy
                    GameScreen gs = (GameScreen) getScreen();
                    Position home = gs.session().world().spawn();
                    pl.fawizja.pixel.treasure.TreasureChests.Chest c = gs.session().chests().all().stream()
                            .min(java.util.Comparator.<pl.fawizja.pixel.treasure.TreasureChests.Chest>comparingInt(
                                    ch -> gs.session().monsters().maxMonsterLevelNear(ch.position(), 20) > 15 ? 1 : 0)
                                    .thenComparingInt(ch -> ch.position().distanceTo(home)))
                            .orElse(gs.session().chests().all().get(0));
                    Gdx.app.log("Pixel", "Podglad skrzyni: " + c.position());
                    // bez kolejnych podpowiedzi: ich pora zalezy od klatek, a zrzut ma byc powtarzalny
                    for (int bit = 0; bit < 64; bit++) gs.session().character().markTip(bit);
                    for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                        Position n = c.position().translate(d.dx, d.dy);
                        if (gs.session().world().isWalkable(n)) {
                            gs.session().teleport(n, 0L);
                            break;
                        }
                    }
                    gs.session().toggleTorch(0L);
                }
                if ("trap".equals(open)) {
                    // podglad: dwa pola od pierwszej pulapki z kolcami na pietrze -2 (z pochodnia)
                    GameScreen gs = (GameScreen) getScreen();
                    Position trap = gs.session().chests().traps().get(0);
                    for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                        Position n = trap.translate(2 * d.dx, 2 * d.dy);
                        if (gs.session().world().isWalkable(n) && !gs.session().chests().trapAt(n)) {
                            gs.session().teleport(n, 0L);
                            break;
                        }
                    }
                    gs.session().toggleTorch(0L);
                }
                if ("map".equals(open)) {
                    // podglad duzej mapy z zaznaczonym celem marszu (bez ruchu - zrzut jest powtarzalny)
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().setWalkTarget(new Position(560, 560, 0));
                    gs.hud().toggleMap();
                }
                if ("journal".equals(open)) {
                    // podglad dziennika: jedno zadanie w toku
                    GameScreen gs = (GameScreen) getScreen();
                    gs.session().character().quests().restore("varden_wolves", false, 3);
                    gs.hud().openJournal();
                }
                if ("picker".equals(open)) ((GameScreen) getScreen()).hud().openPicker(6);
                if (open != null && (open.contains("coins") || open.contains("torch"))) {
                    // monety i pochodnia (kilka po przecinku): coins (stosy zlota, platyny i krysztalu + pochodnie, plecak
                    // otwarty na komputerze), torchsel (zaznaczona pochodnia w plecaku - "Załóż"), torchlit (pochodnia
                    // zalozona w polu obok butow, zapalona, 2 minuty wypalone)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.game.GameSession s = gs.session();
                    pl.fawizja.pixel.character.PlayerCharacter hero = s.character();
                    pl.fawizja.pixel.item.ItemRegistry reg = s.items();
                    int torch = reg.byKey("torch").id();
                    java.util.List<String> opts = java.util.Arrays.asList(open.split(","));
                    if (opts.contains("coins")) {
                        // monety laduja jak lup: same sie lacza i wymieniaja (60+40 zlotych = 1 platynowa itd.)
                        int[][] coins = {{1, 60}, {1, 40}, {1, 47}, {2, 83}, {2, 99}, {1, 99}, {1, 4}, {3, 12}};
                        for (int[] c : coins) hero.backpack().add(new pl.fawizja.pixel.item.ItemStack(c[0], c[1]));
                        hero.backpack().add(new pl.fawizja.pixel.item.ItemStack(torch, 1));
                        hero.backpack().add(new pl.fawizja.pixel.item.ItemStack(torch, 1));
                        if (!opts.contains("mobile_win_bag")) gs.hud().toggleBag();
                    }
                    if (opts.contains("torchlit")) {
                        for (int i = 0; i < hero.backpack().size(); i++) {
                            if (hero.backpack().get(i) != null && hero.backpack().get(i).itemId() == torch) {
                                s.equipFromSlot(i, 0L);
                                break;
                            }
                        }
                        s.toggleTorch(0L);
                        hero.tickTorch(2 * 60_000L + 30_000L, torch);
                    }
                }
                if (open != null && open.contains("mobile_")) {
                    // uklad na telefon (-Dpixel.mobileHud=1): mobile_win_bag|equip|skills|vip|settings, mobile_chat,
                    // mobile_picker (przycisk 3 luku), mobile_logout; kilka po przecinku, np. mobile_win_skills,mobile_chat
                    GameScreen gs = (GameScreen) getScreen();
                    for (String o : open.split(",")) {
                        o = o.trim();
                        if (o.equals("mobile_win_vip")) {
                            gs.previewVip(new pl.fawizja.pixel.friends.FriendsView(java.util.List.of(
                                    new pl.fawizja.pixel.friends.FriendsView.Row("Ula", true, "Knight", 38),
                                    new pl.fawizja.pixel.friends.FriendsView.Row("Mira", true, "Druid", 27),
                                    new pl.fawizja.pixel.friends.FriendsView.Row("Kuba", false, "Paladin", 51, 3 * 60),
                                    new pl.fawizja.pixel.friends.FriendsView.Row("Olek", false, "Sorcerer", 19, 2 * 24 * 60))));
                        }
                        if (o.startsWith("mobile_win_")) gs.hud().openMobileWindow(o.substring("mobile_win_".length()));
                        if (o.equals("mobile_chat")) gs.hud().setMobileChat(true);
                        if (o.equals("mobile_picker")) gs.hud().openMobilePicker(Integer.getInteger("pixel.pickerButton", 2));
                        if (o.equals("mobile_logout")) gs.press("logout");
                        if (o.equals("mobile_journal")) gs.hud().openJournal();
                    }
                }
                if (open != null && java.util.Arrays.asList(open.split(",")).contains("torchsel")) {
                    // po otwarciu okien (otwarcie okna czysci zaznaczenie): zaznaczona pochodnia w plecaku
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.inventory.Container bp = gs.session().character().backpack();
                    int torch = gs.session().items().byKey("torch").id();
                    for (int i = 0; i < bp.size(); i++) {
                        if (bp.get(i) != null && bp.get(i).itemId() == torch) {
                            gs.hud().selectSlot(i);
                            break;
                        }
                    }
                }
                if ("pickerrune".equals(open)) {
                    ((GameScreen) getScreen()).hud().openPicker(6);
                    ((GameScreen) getScreen()).hud().pickCategory("rune");
                }
                if ("chat".equals(open)) {
                    // podglad dymka czatu nad wlasna postacia
                    GameScreen gs = (GameScreen) getScreen();
                    gs.say(gs.session().character().name(), "Witajcie w Varden! Ktoś idzie ze mną do kopalni goblinów?");
                }
                if ("chattabs".equals(open) || open != null && open.startsWith("chattabs,")) {
                    // podglad zakladek czatu: wiadomosci z kilku kanalow (jak z serwera), czesc nieprzeczytana
                    GameScreen gs = (GameScreen) getScreen();
                    long t = gs.session().lastUpdateMs();
                    String self = gs.session().character().name();
                    gs.receiveChat("Ula", "Ktoś idzie ze mną na wilki pod Varden?", "local", t);
                    gs.receiveChat(self, "Ja idę, poczekaj przy świątyni.", "local", t);
                    gs.receiveChat("[Handel] Kuba", "Sprzedam 20 wilczych futer, 300 gp za sztukę.", "trade", t);
                    gs.receiveChat("[Handel] Mira", "Kupię łopatę i linę, płacę od ręki.", "trade", t);
                    gs.receiveChat("[Drużyna] Ola", "Zbieramy się przy wejściu do kopalni.", "party", t);
                    gs.receiveChat("[od Mira]", "Hej, masz jeszcze tę łopatę?", "pm", t);
                }
                Integer conTab = Integer.getInteger("pixel.conTab");
                if (conTab != null) ((GameScreen) getScreen()).hud().showConsoleTab(conTab);
                String button = System.getProperty("pixel.button");
                if (button != null) ((GameScreen) getScreen()).press(button);
                String button2 = System.getProperty("pixel.button2");
                if (button2 != null) ((GameScreen) getScreen()).press(button2);
                String tap = System.getProperty("pixel.tap");
                if (tap != null) {
                    String[] w = tap.split(",");
                    GameScreen gs = (GameScreen) getScreen();
                    gs.tapTile(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), gs.session().character().position().z()));
                }
                String look = System.getProperty("pixel.look");
                if (look != null) {
                    String[] w = look.split(",");
                    GameScreen gs = (GameScreen) getScreen();
                    gs.lookTile(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), gs.session().character().position().z()));
                }
                String joy = System.getProperty("pixel.joy");
                if (joy != null) {
                    String[] f = joy.split(",");
                    GameScreen gs = (GameScreen) getScreen();
                    float[] v = gs.hud().gameView();
                    int sx = (int) (v[0] + v[2] * Float.parseFloat(f[0].trim()));
                    int sy = com.badlogic.gdx.Gdx.graphics.getHeight() - (int) (v[1] + v[3] * Float.parseFloat(f[1].trim()));
                    if (gs.hud().floatJoystick(sx, sy, 7)) gs.hud().touchDragged(sx + 40, sy - 25, 7);
                }
                String walkTo = System.getProperty("pixel.walkto");
                if (walkTo != null) {
                    String[] w = walkTo.split(",");
                    ((GameScreen) getScreen()).walkTo(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), 0));
                }
                if ("market".equals(open)) {
                    // podglad rynku (bez serwera): przykladowe oferty
                    pl.fawizja.pixel.market.MarketView.Row[] r = {
                            new pl.fawizja.pixel.market.MarketView.Row(41, "Mikstura zdrowia", 20, 1_200, "Ula", false),
                            new pl.fawizja.pixel.market.MarketView.Row(40, "Wilcze futro", 15, 300, "Kuba", false),
                            new pl.fawizja.pixel.market.MarketView.Row(37, "Lina", 1, 80, "Stary Wilk", false),
                            new pl.fawizja.pixel.market.MarketView.Row(35, "Wędka", 1, 250, "Mira", false),
                            new pl.fawizja.pixel.market.MarketView.Row(12, "Robak", 50, 500, "Test", true)};
                    ((GameScreen) getScreen()).hud().openMarket(new pl.fawizja.pixel.market.MarketView(64, null, java.util.List.of(r)));
                }
                if ("friends".equals(open)) {
                    // podglad listy znajomych (bez serwera)
                    ((GameScreen) getScreen()).hud().openFriends(new pl.fawizja.pixel.friends.FriendsView(java.util.List.of(
                            new pl.fawizja.pixel.friends.FriendsView.Row("Ula", true, "Druid", 212),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Stary Wilk", false, "Monk", 175, 3 * 60 + 20),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Kuba", true, "Paladin", 98),
                            new pl.fawizja.pixel.friends.FriendsView.Row("Mira", false, "Sorcerer", 160, 5 * 24 * 60))));
                }
                if ("smith".equals(open)) {
                    // podglad kowala: zalozona tarcza juz +2 (obok kupca)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pl.fawizja.pixel.item.ItemStack shield = pc.equipment().get(pl.fawizja.pixel.item.EquipSlot.SHIELD);
                    if (shield != null) pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.SHIELD, shield.withUpgrade(2));
                    gs.hud().openSmith(gs.session());
                }
                if ("depot".equals(open)) {
                    // podglad depozytu: kilka rzeczy juz odlozonych
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    for (String[] it : new String[][] {{"wolf_pelt", "14"}, {"rope", "1"}, {"health_potion", "20"}}) {
                        gs.session().character().depot().add(new pl.fawizja.pixel.item.ItemStack(reg.byKey(it[0]).id(), Integer.parseInt(it[1])));
                    }
                    gs.hud().openDepot(gs.session());
                }
                if ("npc".equals(open)) {
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().openNpc(gs.session());
                    gs.hud().previewNpcPage(Integer.getInteger("pixel.npcPage", 0));
                }
            }
            if (Boolean.getBoolean("pixel.bossWin") && frame == frames - 15 && getScreen() instanceof GameScreen) {
                // offline: boss wyprawy (po dzwigni z -Dpixel.tap) ginie od jednego ciosu - podglad lupu i portalu
                GameScreen gs = (GameScreen) getScreen();
                gs.session().debugDefeatQuestBoss(gs.worldTime());
            }
            if (Boolean.getBoolean("pixel.fx") && frame == frames - 8 && getScreen() instanceof GameScreen) {
                GameScreen gs = (GameScreen) getScreen();
                long now = gs.worldTime();
                Position me = gs.session().character().position();
                pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now - 20, me, me.translate(5, -3), "ENERGY"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now, me, me.translate(-5, -3), "PHYSICAL"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 100, me.translate(-4, 2), 2, "FIRE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 220, me.translate(4, 3), 1, "ICE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 150, me, 0, "HEAL"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now - 40, me, me.translate(2, -4), "DEATH"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now - 60, me, me.translate(-2, -4), "EARTH"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 120, me.translate(0, 3), 1, "PHYSICAL"));
                gs.session().ground().addCorpse(me.translate(1, 1), "wolf",
                        java.util.List.of(new pl.fawizja.pixel.item.ItemStack(1, 12)), now);
                gs.session().ground().addCorpse(me.translate(-1, 1), "goblin", java.util.List.of(), now);
            }
            super.render();
            if (screenshot != null && ++frame == frames) {
                Pixmap pm = Pixmap.createFromFrameBuffer(0, 0,
                        Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                PixmapIO.writePNG(Gdx.files.absolute(screenshot), pm, -1, true);
                pm.dispose();
                Gdx.app.log("Pixel", "Screenshot saved: " + screenshot);
                Gdx.app.exit();
            }
        }
    }
}
