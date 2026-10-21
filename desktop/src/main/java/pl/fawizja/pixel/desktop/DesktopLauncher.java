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
 *   -Dpixel.hour=22                   godzina gry na starcie sesji (np. noc do zrzutow ekranu)
 *   -Dpixel.level=N      podglad postaci na wyzszym poziomie (zrzuty)
 *   -Dpixel.npcPage=N    strona listy kupca przy -Dpixel.open=npc
 *   -Dpixel.button=id    akcja przycisku/wiersza menu (np. journal:goto:varden_guide)
 *   -Dpixel.exportArt=dir zapisuje obecne grafiki jako PNG pod nazwami z docs/GRAPHICS.md (dla grafikow)
 *   -Dpixel.button2=id   druga akcja po pierwszej (np. use, potem cmd:/statystyki)
 *   -Dpixel.tap=x,y      dotkniecie pola swiata (marsz, NPC, cel)
 *   -Dpixel.walkto=x,y   marsz do pola (jak dotkniecie duzej mapy)
 *   -Dpixel.open=book|bag|bagitem|character|npc|depot|friends|market|mount|pet|smith|chest|map|chat|journal|achievements|bestiary|diag|trade|player|party|ranking|death|bagmess|bagkeep|trap   otwiera ksiege, plecak, karte postaci, rozmowe z NPC, diagnostyke albo podglad okna handlu
 *   -Dpixel.seed=1                    stale ziarno potworow (testy regresji GUI)
 *   -Dpixel.fx=true                   tuz przed zrzutem pokazuje efekty (pocisk, wybuch ognia, mrozna fala, leczenie) i dwa ciala
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
                ((GameScreen) getScreen()).session().setClock(
                        new pl.fawizja.pixel.time.WorldClock(-hour * 150_000L, 60L * 60L * 1000L, 0.38f));
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
                if ("character".equals(open)) ((GameScreen) getScreen()).hud().toggleCharacter();
                if ("diag".equals(open)) ((GameScreen) getScreen()).hud().toggleDiagnostics();
                if ("party".equals(open)) {
                    GameScreen gs = (GameScreen) getScreen();
                    gs.previewParty(java.util.List.of(gs.session().character().name(), "Ula", "Kuba"));
                }
                if ("player".equals(open)) ((GameScreen) getScreen()).hud().openPlayerMenu(2, "Ula", false, false);
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
                    // podglad: obok pierwszej skrzyni na pietrze -1 (z pochodnia, zeby bylo widac)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.treasure.TreasureChests.Chest c = gs.session().chests().all().get(0);
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
                    gs.hud().setWalkTarget(new Position(120, 150, 0));
                    gs.hud().toggleMap();
                }
                if ("journal".equals(open)) {
                    // podglad dziennika: jedno zadanie w toku
                    GameScreen gs = (GameScreen) getScreen();
                    gs.session().character().quests().restore("varden_wolves", false, 3);
                    gs.hud().openJournal();
                }
                if ("chat".equals(open)) {
                    // podglad dymka czatu nad wlasna postacia
                    GameScreen gs = (GameScreen) getScreen();
                    gs.say(gs.session().character().name(), "Witajcie w Varden! Ktoś idzie ze mną do kopalni goblinów?");
                }
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
            if (Boolean.getBoolean("pixel.fx") && frame == frames - 8 && getScreen() instanceof GameScreen) {
                GameScreen gs = (GameScreen) getScreen();
                long now = gs.worldTime();
                Position me = gs.session().character().position();
                pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.projectile(now - 120, me, me.translate(5, -3), "ENERGY"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 100, me.translate(-4, 2), 2, "FIRE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 220, me.translate(4, 3), 1, "ICE"));
                cs.addEvent(pl.fawizja.pixel.combat.CombatEvent.area(now - 150, me, 0, "HEAL"));
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
