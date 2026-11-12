package pl.fawizja.pixel.desktop;

import pl.fawizja.pixel.i18n.Messages;
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
 *   -Dpixel.autostart=Imie:PALADIN:frostholm  jak wyzej, nowa postac w tym miescie startowym (online: nowe konto)
 *   -Dpixel.open=create               bez autostart: ekran tworzenia postaci (profesja + miasto startowe);
 *                                     -Dpixel.createTown=nerys -Dpixel.createVocation=DRUID zaznaczaja wybor
 *   -Dpixel.server=host:port          z autostart: logowanie na serwer (haslo -Dpixel.password, domyslnie haslo-dev)
 *                                     bez autostart: domyslny adres w ekranie logowania
 *   -Dpixel.teleport=x,y,z            ustawia postac na pozycji (np. 8,23,-1)
 *   -Dpixel.screenshot=plik.png       zapisuje zrzut ekranu po pixel.frames klatkach i konczy
 *   -Dpixel.frames=120
 *   -Dpixel.hour=22                   godzina gry na starcie sesji (np. noc do zrzutow ekranu); wlacza tez staly
 *                                     czas: 50 ms na klatke, staly dzien i czas rzeczywisty - zrzut nie zalezy od maszyny
 *   -Dpixel.frameMs=N                 krok czasu sesji na klatke (0 = zegar rzeczywisty)
 *   -Dpixel.level=N      podglad postaci na wyzszym poziomie (zrzuty)
 *   -Dpixel.harmony=N    mnich: N punktow harmonii (0..5); -Dpixel.virtue=HARMONY|JUSTICE|SUSTAIN aktywna cnota;
 *                        -Dpixel.open=virtues okienko wyboru cnoty; -Dpixel.open=monks inni mnisi z harmonia obok
 *   -Dpixel.skills=AXE:82,SHIELDING:77  nowa postac z tymi poziomami umiejetnosci (np. z symulacji polowania)
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
 *   -Dpixel.controls=dpad|float4|hold|classic  wariant sterowania (bez zapisu); -Dpixel.sens=1..5 czulosc
 *   -Dpixel.ui=classic|new  wyglad interfejsu (bez zapisu; domyslnie z ustawien, czyli nowy)
 *   -Dpixel.dpad=NE      wcisniety kierunek krzyzyka (N, NE, E, SE, S, SW, W, NW)
 *   -Dpixel.hold=fx,fy   palec przytrzymany na widoku gry (ulamki 0..1) w wariancie "Przytrzymanie na mapie"
 *   -Dpixel.walkto=x,y  marsz do pola (jak dotkniecie duzej mapy)
 *   -Dpixel.revealMap=1  cala mapa odkryta (bez mgly; regresja GUI domyslnie, sceny mgly ustawiaja 0)
 *   -Dpixel.exploreRoute=x,y,z;x,y,z;...  z teleport: odkrywa mape wzdluz odcinkow trasy, jakby postac nia przeszla
 *   -Dpixel.taps=x,y@N;x,y@M  dotkniecia pol swiata w klatkach N, M (np. drzwi poziomu, potem pole za nimi)
 *   -Dpixel.spellDemo=field|gfb|poison|poison_cure|exani|summon  podglad czarow, run, pol i stanow (zrzuty 0.240)
 *   -Dpixel.spellDemo=gfb|wave|beam|ultimate|chest  obszary czarow jak w Tibii na tlumie dzikow i nagroda skrzyni zadania
 *                        (-Dpixel.chest=id skrzyni, domyslnie d668_467_-3)
 *   -Dpixel.spellDemo=aim|aimthrow|aimbag|pickermode|combo|pickercombo  paski akcji jak w Tibii: celownik runy (pole 7
 *                        paska: wielka kula ognia z celownikiem; aimthrow rzuca ja na pole -Dpixel.aimHover), "Uzyj z..."
 *                        z plecaka, menu trybu runy (cel / siebie / celownik), pole combo i jego edycja
 *   -Dpixel.aimHover=x,y pole pod kursorem celownika (Xvfb nie ma myszy)
 *   -Dpixel.open=playerpopup|vip|buffs|book|bag|bagitem|character|npc|depot|friends|market|mount|pet(+ -Dpixel.petCard=1)|smith|chest|map|chat|journal|achievements|bestiary|diag|trade|player|pvp|pzblock|skull_red|skull_white|party|ranking|death|bagmess|bagpages|bagkeep|trap|battle|picker|pickerrune   otwiera ksiege, plecak, karte postaci, rozmowe z NPC, diagnostyke albo podglad okna handlu
 *   -Dpixel.open=wheel[=id]  podglad Kola Przeznaczenia (np. wheel=wheel:q:P, wheel=wheel:perks, wheel=wheel:gems:0); -Dpixel.wheelOrder=PRBG,
 *                           -Dpixel.wheelAvatar=true, -Dpixel.wheelGems=true (klejnoty, naczynia i okruchy do podgladu)
 *   -Dpixel.open=bestcard|bestclass|charms[=id]  podglad bestiariusza i charmow (np. charms=charm:sel:WOUND); -Dpixel.charmProc=true liczby charmow
 *   -Dpixel.open=fishing  wedka w rece, robaki w plecaku, 4 zarzucenia nad woda (np. -Dpixel.teleport=470,483,0)
 *   -Dpixel.stamina=M    nowa postac z M minutami staminy (np. 750 = 12:30, pomaranczowa); -Dpixel.premium=1 konto premium
 *   -Dpixel.fightMode=OFFENSIVE|BALANCED|DEFENSIVE  tryb walki nowej postaci (przycisk miecz / miecz z tarcza / tarcza)
 *   -Dpixel.mobileHud=1  uklad HUD-u na telefon (wariant B: widok gry na calym ekranie, luk czarow); na Androidzie zawsze
 *   -Dpixel.open=coins|torchsel|torchlit  monety w plecaku, zaznaczona pochodnia, zapalona pochodnia w polu obok butow (z mobile_* po przecinku)
 *   -Dpixel.open=rarity: ramki rzadkosci (przedmioty kazdej rzadkosci w plecaku i na ciele; z mobile_win_bag/equip na telefonie)
 *   -Dpixel.open=mobile_win_bag|mobile_win_equip|mobile_win_skills|mobile_win_vip|mobile_win_settings|mobile_chat|mobile_picker|mobile_logout|mobile_journal
 *                        stany ukladu na telefon (kilka po przecinku); -Dpixel.pickerButton=N (0..8; 5-7 wewnetrzny luk, 8 rog) przy mobile_picker
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
        private boolean revealed;

        /** Odkrywa widok z kazdego pola odcinkow trasy (linie proste miedzy punktami, na pietrze punktu). */
        private static void exploreRoute(pl.fawizja.pixel.world.ExploredMap map, String route) {
            Position prev = null;
            for (String pt : route.split(";")) {
                String[] c = pt.split(",");
                Position p = new Position(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()));
                if (prev == null || prev.z() != p.z()) {
                    map.revealView(p);
                } else {
                    int n = Math.max(Math.abs(p.x() - prev.x()), Math.abs(p.y() - prev.y()));
                    for (int i = 1; i <= n; i++) {
                        map.revealView(new Position(prev.x() + Math.round((p.x() - prev.x()) * (float) i / n),
                                prev.y() + Math.round((p.y() - prev.y()) * (float) i / n), p.z()));
                    }
                }
                prev = p;
            }
        }

        @Override
        public void create() {
            super.create();
            if (autostart == null) {
                if ("login".equals(System.getProperty("pixel.open"))) {
                    setScreen(new pl.fawizja.pixel.client.screen.LoginScreen(this));
                }
                if ("create".equals(System.getProperty("pixel.open"))) {
                    setScreen(new pl.fawizja.pixel.client.screen.VocationSelectScreen(this));
                }
                return;
            }
            String[] p = autostart.split(":");
            Vocation v = Vocation.valueOf(p.length > 1 ? p[1] : "PALADIN");
            String town = p.length > 2 ? p[2] : null;
            String server = platform().serverAddress();
            if (server != null) {
                OnlineLogin login = connectOnline(server, p[0], v.name(), System.getProperty("pixel.password", Messages.get("desktop_launcher.haslo_dev")), null, town);
                if (login.problem() == null) {
                    startOnline(login);
                    return;
                }
                Gdx.app.error("Pixel", Messages.get("desktop_launcher.online_niedostepne") + login.problem());
            }
            if (localSaveSummary() != null) playLocal();
            else createCharacter(p[0], v, town);
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
            if (!revealed && getScreen() instanceof GameScreen) {
                revealed = true;
                if ("1".equals(System.getProperty("pixel.revealMap"))) {
                    ((GameScreen) getScreen()).session().character().explored().revealAll();
                }
            }
            if (!teleported && teleport != null && getScreen() instanceof GameScreen) {
                String route = System.getProperty("pixel.exploreRoute");
                if (route != null) exploreRoute(((GameScreen) getScreen()).session().character().explored(), route);
                String[] p = teleport.split(",");
                ((GameScreen) getScreen()).session().teleport(
                        new Position(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())), 0L);
                teleported = true;
                int level = Integer.getInteger("pixel.level", 0);
                pl.fawizja.pixel.character.PlayerCharacter me = ((GameScreen) getScreen()).session().character();
                while (me.level() < level) me.addExperience(Math.max(1000L, me.experience()));
                // podglad harmonii mnicha: -Dpixel.harmony=0..5, -Dpixel.virtue=HARMONY|JUSTICE|SUSTAIN
                Integer harmony = Integer.getInteger("pixel.harmony");
                if (harmony != null) me.harmony().build(harmony - me.harmony().points());
                String virtue = System.getProperty("pixel.virtue");
                if (virtue != null) me.harmony().setVirtue(pl.fawizja.pixel.vocation.Harmony.Virtue.valueOf(virtue));
                // -Dpixel.gear=klucz,klucz: te przedmioty zalozone (kazdy na swoje miejsce), -Dpixel.bagItems=klucz,...: do plecaka
                // (podglad zestawow, np. przedmioty Hinasa)
                String gearKeys = System.getProperty("pixel.gear");
                if (gearKeys != null) {
                    pl.fawizja.pixel.item.ItemRegistry reg = ((GameScreen) getScreen()).session().items();
                    for (String k : gearKeys.split(",")) {
                        pl.fawizja.pixel.item.ItemType t = reg.byKeyOrNull(k.trim());
                        if (t != null && t.slot() != pl.fawizja.pixel.item.EquipSlot.NONE) {
                            me.equipment().setDirect(t.slot(), new pl.fawizja.pixel.item.ItemStack(t.id(), 1));
                        }
                    }
                }
                String bagKeys = System.getProperty("pixel.bagItems");
                if (bagKeys != null) {
                    pl.fawizja.pixel.item.ItemRegistry reg = ((GameScreen) getScreen()).session().items();
                    for (String k : bagKeys.split(",")) {
                        pl.fawizja.pixel.item.ItemType t = reg.byKeyOrNull(k.trim());
                        if (t != null) me.backpack().add(new pl.fawizja.pixel.item.ItemStack(t.id(), 1));
                    }
                }
                String open = System.getProperty("pixel.open");
                if ("virtues".equals(open)) ((GameScreen) getScreen()).hud().openVirtues();
                if ("book".equals(open)) ((GameScreen) getScreen()).hud().toggleBook();
                if ("credits".equals(open)) ((GameScreen) getScreen()).hud().openInfo(Messages.get("game_screen.autorzy_grafik"), pl.fawizja.pixel.game.Credits.rows());
                if ("bag".equals(open)) ((GameScreen) getScreen()).hud().toggleBag();
                if ("bagitem".equals(open)) {
                    // podglad opisu przedmiotu: pierwszy przedmiot z miejscem na ciele
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().toggleBag();
                    pl.fawizja.pixel.inventory.Container bp = gs.session().character().backpack();
                    // -Dpixel.lookItem=klucz: ten przedmiot zamiast miecza (np. obsidian_armor - ochrony)
                    String lookItem = System.getProperty("pixel.lookItem");
                    int lookId = gs.session().items().byKey(lookItem != null ? lookItem : "iron_sword").id();
                    // -Dpixel.lookTier=7 -Dpixel.lookImbue=VAMPIRISM3,STRIKE2: tier Kuzni i nasycenia w opisie
                    int lookTier = Integer.getInteger("pixel.lookTier", 0);
                    pl.fawizja.pixel.item.Imbuements lookIm = pl.fawizja.pixel.item.Imbuements.NONE;
                    String imb = System.getProperty("pixel.lookImbue");
                    if (imb != null) {
                        for (String k : imb.split(",")) {
                            k = k.trim();
                            lookIm = lookIm.with(pl.fawizja.pixel.item.Imbuement.valueOf(k.substring(0, k.length() - 1)), k.charAt(k.length() - 1) - '0');
                        }
                    }
                    bp.add(new pl.fawizja.pixel.item.ItemStack(lookId, 1, 0, lookTier, lookIm));
                    for (int i = 0; i < bp.size(); i++) {
                        if (bp.get(i) != null && (lookItem != null ? bp.get(i).itemId() == lookId
                                : gs.session().items().get(bp.get(i).itemId()).slot() != pl.fawizja.pixel.item.EquipSlot.NONE)) {
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
                    // house_home: wlasciciel w srodku przy lozku (wnetrze z lozkami); house_sleep: do tego dotyka lozka
                    // i kladzie sie spac (-Dpixel.sleepMs=60000 trzyma postac w lozku do zrzutu)
                    boolean home = open.equals("house_home") || open.equals("house_sleep");
                    boolean owner = home || open.equals("house_owner") || open.equals("house_invite");
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
                    if (home) {
                        java.util.List<Position> beds = s.bedsIn(h);
                        Position bed = beds.isEmpty() ? null : beds.get(0);
                        stand = null;
                        for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                            Position n = bed == null ? null : bed.translate(d.dx, d.dy);
                            if (n != null && !d.diagonal() && h.contains(n) && s.world().isWalkable(n)) {
                                stand = n;
                                break;
                            }
                        }
                        if (stand != null) s.teleport(stand, 0L);
                        if (open.equals("house_sleep") && bed != null) gs.tapTile(bed);
                    } else {
                        if (stand != null) s.teleport(stand, 0L);
                        gs.tapTile(h.door());
                    }
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
                            parts.add((s.getValue() > 1 ? s.getValue() + "x " : "") + it.name() + (bossOnly ? Messages.get("desktop_launcher.tylko_z_bossa") : ""));
                        }
                        rows.add(new String[] {e.getKey() + (rare ? Messages.get("desktop_launcher.rzadki_lup") : ""), parts.isEmpty() ? "nic" : String.join(", ", parts)});
                    }
                    gs.hud().openInfo(Messages.get("combat_system.lup") + boss.name() + Messages.get("desktop_launcher.osobny_rzut_na_gracza"), rows);
                }
                if ("character".equals(open)) ((GameScreen) getScreen()).hud().toggleCharacter();
                if ("combatgear".equals(open)) {
                    // okno postaci ze statystykami walki: zbroja i nogi z obsydianu, helm z koralu, topor z kradzieza,
                    // zaklecie kowala "krytyk" (krytyk, kradziez, ochrony w oknie Umiejetnosci)
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    String[][] gear = {{"ARMOR", "obsidian_armor"}, {"LEGS", "obsidian_legs"}, {"HEAD", "coral_helmet"}, {"WEAPON", "skull_axe"}};
                    for (String[] g : gear) {
                        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.valueOf(g[0]), new pl.fawizja.pixel.item.ItemStack(reg.byKey(g[1]).id(), 1));
                    }
                    pc.enchantments().extend(pl.fawizja.pixel.item.Enchantments.Kind.CRIT);
                    gs.hud().toggleCharacter();
                }
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
                if ("monks".equals(open)) ((GameScreen) getScreen()).previewMonks();
                if ("playerpopup".equals(open)) ((GameScreen) getScreen()).previewPlayerPopup();
                if ("vip".equals(open)) {
                    // podglad listy VIP (bez serwera): znajomi w grze (zieloni) i poza gra (czerwoni)
                    ((GameScreen) getScreen()).previewVip(new pl.fawizja.pixel.friends.FriendsView(java.util.List.of(
                            new pl.fawizja.pixel.friends.FriendsView.Row("Ula", true, Messages.get("hud.druid"), 212),
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
                    String[][] sample = {{"Ula", Messages.get("hud.druid"), "212"}, {"Kuba", "Paladin", "198"}, {"Stary Wilk", "Monk", "175"},
                            {"Mira", "Sorcerer", "160"}, {"Olaf", "Paladin", "142"}, {"Lena", Messages.get("hud.druid"), "120"},
                            {"Test", "Paladin", "98"}, {"Kora", "Monk", "77"}, {"Petra", "Sorcerer", "61"}, {"Quin", Messages.get("hud.druid"), "40"}};
                    for (int i = 0; i < sample.length; i++) {
                        rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i + 1, sample[i][0], sample[i][1],
                                Integer.parseInt(sample[i][2])));
                    }
                    ((GameScreen) getScreen()).hud().openRanking(
                            new pl.fawizja.pixel.ranking.RankingView(Messages.get("desktop_launcher.ranking_serwera"), 7, 23, rows));
                }
                if ("weekly".equals(open)) {
                    // podglad rankingu tygodnia: zdobyte doswiadczenie
                    java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
                    String[][] sample = {{"Kuba", "Paladin", "4820000"}, {"Ula", Messages.get("hud.druid"), "3915000"}, {"Mira", "Sorcerer", "2210500"},
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
                if (open != null && (open.startsWith("bestcard") || open.startsWith("bestclass") || open.startsWith("charm"))) {
                    // podglad bestiariusza i charmow: wilk 120 zabic (etap 3), goblin ukonczony (Major), charmy kupione
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pl.fawizja.pixel.achievement.Bestiary b = pc.bestiary();
                    for (int i = 0; i < 120; i++) b.record("wolf", false);
                    for (int i = 0; i < 250; i++) b.record("goblin", false);
                    for (int i = 0; i < 12; i++) b.record("bandit", false);
                    for (int i = 0; i < 3; i++) b.record("boar", false);
                    pc.charms().addPoints(2000);
                    gs.session().buyCharm(pl.fawizja.pixel.charm.Charm.WOUND, 0);
                    gs.session().buyCharm(pl.fawizja.pixel.charm.Charm.WOUND, 0);
                    gs.session().buyCharm(pl.fawizja.pixel.charm.Charm.DODGE, 0);
                    gs.session().buyCharm(pl.fawizja.pixel.charm.Charm.CRIPPLE, 0);
                    gs.session().assignCharm(pl.fawizja.pixel.charm.Charm.WOUND, "goblin", 0);
                    gs.session().assignCharm(pl.fawizja.pixel.charm.Charm.CRIPPLE, "wolf", 0);
                    String[] o = open.split("=", 2);
                    String id = o.length > 1 ? o[1] : open.startsWith("bestcard") ? "best:wolf" : open.startsWith("bestclass") ? "bestclass:Mammal" : "charm:open";
                    gs.press(id);
                }
                if (open != null && open.startsWith("wheel")) {
                    // podglad Kola Przeznaczenia: promocja + premium, punkty rozdane wg -Dpixel.wheelOrder (domyslnie PRBG:
                    // najpierw fioletowa cwiartka - objawienie awatara), -Dpixel.wheelAvatar=true wlacza awatara
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pc.setPromoted(true);
                    pc.setPremium(true);
                    fillWheel(pc, System.getProperty("pixel.wheelOrder", "PRBG"));
                    if (Boolean.getBoolean("pixel.wheelGems")) wheelGems(pc, gs.session().items());
                    if (Boolean.getBoolean("pixel.wheelAvatar")) {
                        pc.wheel().startAvatar(pc.wheelBonus().stage(pl.fawizja.pixel.wheel.WheelDomain.PURPLE),
                                pl.fawizja.pixel.wheel.DestinyWheel.AVATAR_COOLDOWN_MS);
                    }
                    pc.heal(pc.maxHealth());
                    pc.restoreMana(pc.maxMana());
                    String[] o = open.split("=", 2);
                    gs.press(o.length > 1 ? o[1] : "wheel");
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
                    int petId = gs.session().items().byKey(pet).id();
                    pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(petId, 1));
                    // przywolanie jak w grze (uzycie przedmiotu): komunikat "+5% doswiadczenia" w dzienniku
                    for (int i = 0; i < pc.backpack().size(); i++) {
                        pl.fawizja.pixel.item.ItemStack st = pc.backpack().get(i);
                        if (st != null && st.itemId() == petId) {
                            gs.session().useSlot(i, gs.worldTime());
                            break;
                        }
                    }
                    if (!pet.equals(pc.pet())) pc.setPet(pet);
                    // -Dpixel.petCard=1: od razu karta postaci (Poziom: "exp xN, pupil +5%")
                    if (Boolean.getBoolean("pixel.petCard")) gs.hud().toggleCharacter();
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
                if (open != null && open.contains("fishing")) {
                    // lowienie jak w Tibii: wedka z plecaka do reki, robaki zostaja w plecaku, kilka zarzucen nad woda
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.game.GameSession s = gs.session();
                    pl.fawizja.pixel.character.PlayerCharacter hero = s.character();
                    int rod = s.items().byKey("fishing_rod").id();
                    for (int i = 0; i < hero.backpack().size(); i++) {
                        if (hero.backpack().get(i) != null && hero.backpack().get(i).itemId() == rod) {
                            s.equipFromSlot(i, 0L);
                            break;
                        }
                    }
                    for (int i = 0; i < 4; i++) s.fish(i * 2_000L);
                    if (!open.contains("mobile_win_bag")) gs.hud().toggleBag();
                }
                if (open != null && java.util.Arrays.asList(open.split(",")).contains("rarity")) {
                    // podglad ramek rzadkosci (wyglad nowy): w plecaku po przedmiocie z kazdej ramki (zwykla..mityczna;
                    // przedmiot tylko z bossow o stopien wyzej), na ciele zestaw roznych rzadkosci; plecak otwarty na komputerze
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter hero = gs.session().character();
                    java.util.List<pl.fawizja.pixel.item.ItemType> gear = new java.util.ArrayList<>();
                    for (pl.fawizja.pixel.item.ItemType t : gs.session().items().all()) {
                        if (t.maxStack() == 1 && t.slot() != pl.fawizja.pixel.item.EquipSlot.NONE && t.slot() != pl.fawizja.pixel.item.EquipSlot.LIGHT) gear.add(t);
                    }
                    gear.sort(java.util.Comparator.comparingInt(pl.fawizja.pixel.item.ItemType::id));
                    for (int tier = 1; tier <= 6; tier++) {
                        for (pl.fawizja.pixel.item.ItemType t : gear) {
                            boolean boss = pl.fawizja.pixel.monster.LootRules.isBossOnly(t);
                            int r = pl.fawizja.pixel.item.Rarity.tier(t);
                            if (r > 0 && Math.min(6, r + (boss ? 1 : 0)) == tier) {
                                hero.backpack().add(new pl.fawizja.pixel.item.ItemStack(t.id(), 1));
                                break;
                            }
                        }
                    }
                    pl.fawizja.pixel.item.EquipSlot[] slots = {pl.fawizja.pixel.item.EquipSlot.AMULET, pl.fawizja.pixel.item.EquipSlot.HEAD,
                            pl.fawizja.pixel.item.EquipSlot.ARMOR, pl.fawizja.pixel.item.EquipSlot.SHIELD, pl.fawizja.pixel.item.EquipSlot.LEGS,
                            pl.fawizja.pixel.item.EquipSlot.FEET, pl.fawizja.pixel.item.EquipSlot.RING};
                    int[] want = {4, 3, 5, 2, 6, 1, 3};
                    for (int i = 0; i < slots.length; i++) {
                        // przedmiot na to miejsce z ramka najblizsza zadanej
                        pl.fawizja.pixel.item.ItemType best = null;
                        int bestDiff = 99;
                        for (pl.fawizja.pixel.item.ItemType t : gear) {
                            int r = pl.fawizja.pixel.item.Rarity.tier(t);
                            if (t.slot() != slots[i] || r == 0) continue;
                            int diff = Math.abs(Math.min(6, r + (pl.fawizja.pixel.monster.LootRules.isBossOnly(t) ? 1 : 0)) - want[i]);
                            if (diff < bestDiff) {
                                best = t;
                                bestDiff = diff;
                            }
                        }
                        if (best != null) hero.equipment().setDirect(slots[i], new pl.fawizja.pixel.item.ItemStack(best.id(), 1));
                    }
                    if (!open.contains("mobile_")) gs.hud().toggleBag();
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
                                    new pl.fawizja.pixel.friends.FriendsView.Row("Mira", true, Messages.get("hud.druid"), 27),
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
                    gs.say(gs.session().character().name(), Messages.get("desktop_launcher.witajcie_w_varden_ktos"));
                }
                if ("chattabs".equals(open) || open != null && open.startsWith("chattabs,")) {
                    // podglad zakladek czatu: wiadomosci z kilku kanalow (jak z serwera), czesc nieprzeczytana
                    GameScreen gs = (GameScreen) getScreen();
                    long t = gs.session().lastUpdateMs();
                    String self = gs.session().character().name();
                    gs.receiveChat("Ula", Messages.get("desktop_launcher.ktos_idzie_ze_mna"), "local", t);
                    gs.receiveChat(self, Messages.get("desktop_launcher.ja_ide_poczekaj_przy"), "local", t);
                    gs.receiveChat("[Handel] Kuba", Messages.get("desktop_launcher.sprzedam_20_wilczych_futer"), "trade", t);
                    gs.receiveChat("[Handel] Mira", Messages.get("desktop_launcher.kupie_lopate_i_line"), "trade", t);
                    gs.receiveChat(Messages.get("desktop_launcher.druzyna_ola"), Messages.get("desktop_launcher.zbieramy_sie_przy_wejsciu"), "party", t);
                    gs.receiveChat("[od Mira]", Messages.get("desktop_launcher.hej_masz_jeszcze_te"), "pm", t);
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
                    // -Dpixel.joyDrag=dx,dy: wychylenie w pikselach ekranu (domyslnie 40,-25: w prawo i w gore)
                    String[] d = System.getProperty("pixel.joyDrag", "40,-25").split(",");
                    int ddx = Integer.parseInt(d[0].trim()), ddy = Integer.parseInt(d[1].trim());
                    if (gs.hud().floatJoystick(sx, sy, 7)) gs.hud().touchDragged(sx + ddx, sy + ddy, 7);
                }
                String dpad = System.getProperty("pixel.dpad");
                if (dpad != null) {
                    // wcisniety kierunek krzyzyka (N, NE, E, ... albo NORTH_EAST)
                    ((GameScreen) getScreen()).hud().dpad().press(direction(dpad));
                }
                String hold = System.getProperty("pixel.hold");
                if (hold != null) {
                    String[] f = hold.split(",");
                    GameScreen gs = (GameScreen) getScreen();
                    float[] v = gs.hud().gameView();
                    int sx = (int) (v[0] + v[2] * Float.parseFloat(f[0].trim()));
                    int sy = com.badlogic.gdx.Gdx.graphics.getHeight() - (int) (v[1] + v[3] * Float.parseFloat(f[1].trim()));
                    gs.simulateHold(sx, sy);
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
                            new pl.fawizja.pixel.market.MarketView.Row(35, Messages.get("desktop_launcher.wedka"), 1, 250, "Mira", false),
                            new pl.fawizja.pixel.market.MarketView.Row(12, "Robak", 50, 500, "Test", true)};
                    ((GameScreen) getScreen()).hud().openMarket(new pl.fawizja.pixel.market.MarketView(64, null, java.util.List.of(r)));
                }
                if ("friends".equals(open)) {
                    // podglad listy znajomych (bez serwera)
                    ((GameScreen) getScreen()).hud().openFriends(new pl.fawizja.pixel.friends.FriendsView(java.util.List.of(
                            new pl.fawizja.pixel.friends.FriendsView.Row("Ula", true, Messages.get("hud.druid"), 212),
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
                if ("forge".equals(open) || "imbue".equals(open)) {
                    // podglad Kuzni / nasycen u kowala (stac przy kowalu, np. Bogdan w Varden 566,498,0):
                    // dwa topory klasy 4 do fuzji, miecz T3 jako dawca transferu, pyl, odlamki, rdzenie i zloto w banku;
                    // -Dpixel.forgeFuse=1 od razu robi jedna fuzje (wiersz "Wynik")
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                    pl.fawizja.pixel.inventory.Container bp = pc.backpack();
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey("skull_axe").id(), 1));
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey("skull_axe").id(), 1));
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey("abyssal_greatsword").id(), 1, 0, 3, null));
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey("umbral_blade").id(), 1));
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey(pl.fawizja.pixel.item.Forge.CORE_KEY).id(), 3));
                    bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey(pl.fawizja.pixel.item.Forge.SLIVER_KEY).id(), 27));
                    pc.restoreForge(125, 140);
                    pc.bank().credit(20_000_000);
                    if ("imbue".equals(open)) {
                        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.WEAPON, new pl.fawizja.pixel.item.ItemStack(
                                reg.byKey("abyssal_greatsword").id(), 1, 0, 3, pl.fawizja.pixel.item.Imbuements.NONE
                                .with(pl.fawizja.pixel.item.Imbuement.VAMPIRISM, 3)));
                        for (pl.fawizja.pixel.item.Imbuement.Ingredient in : pl.fawizja.pixel.item.Imbuement.STRIKE.ingredients(2)) {
                            bp.add(new pl.fawizja.pixel.item.ItemStack(reg.byKey(in.itemKey()).id(), in.count()));
                        }
                    }
                    if (Boolean.getBoolean("pixel.forgeFuse")) {
                        java.util.List<int[]> pairs = gs.session().forge().fusionPairs();
                        if (!pairs.isEmpty()) gs.session().forgeFuse(pairs.get(0)[0], pairs.get(0)[1], true, false, gs.worldTime());
                    }
                    gs.hud().openSmithPage(gs.session(), "imbue".equals(open) ? 2 : 1, pl.fawizja.pixel.item.EquipSlot.WEAPON,
                            Integer.getInteger("pixel.imbueLevel", 2));
                }
                if (Boolean.getBoolean("pixel.forgeMonsters")) {
                    // podglad potworow Kuzni: najblizszy diaboliczny, kolejne pod wpływem 1..5, dwa dalsze diaboliczne (minimapa)
                    GameScreen gs = (GameScreen) getScreen();
                    Position heroAt = gs.session().character().position();
                    pl.fawizja.pixel.monster.MonsterManager mm = gs.session().monsters();
                    mm.spawnEvent("wolf", heroAt.translate(3, 1), 2, 3, gs.worldTime());
                    mm.spawnEvent("boar", heroAt.translate(-3, 1), 2, 3, gs.worldTime());
                    java.util.List<pl.fawizja.pixel.monster.Monster> near = new java.util.ArrayList<>();
                    for (pl.fawizja.pixel.monster.Monster m : mm.monsters()) {
                        if (m.position().z() == heroAt.z() && !m.isDead() && m.type().base().canBeForge()) near.add(m);
                    }
                    near.sort(java.util.Comparator.comparingInt(m -> m.position().distanceTo(heroAt)));
                    for (int i = 0; i < near.size() && i < 6; i++) {
                        if (i == 0) mm.debugMakeFiendish(near.get(i), gs.worldTime());
                        else near.get(i).changeType(near.get(i).type().base().influenced(i));
                    }
                    for (int i = 0, made = 0; i < near.size() && made < 2; i++) {
                        if (near.get(i).position().distanceTo(heroAt) >= 14) {
                            mm.debugMakeFiendish(near.get(i), gs.worldTime());
                            made++;
                            i += 5;
                        }
                    }
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
                if (open != null && open.startsWith("bless")) {
                    // podglad blogoslawienstw (z -Dpixel.startLevel=...): bless_monk = rozmowa z mnichem obok (teleport do jego
                    // kaplicy), bless_status = okno informacyjne, bless_death = smierc bez blogoslawienstw (podsumowanie),
                    // bless_corpse = ta sama smierc i powrot do ciala (przedmioty lezace w ciele; z -Dpixel.noDeathWindow=true bez okna smierci), bless_death5 = smierc z pieciu, bless_talk = rozmowa
                    GameScreen gs = (GameScreen) getScreen();
                    pl.fawizja.pixel.character.PlayerCharacter pc = gs.session().character();
                    pl.fawizja.pixel.character.Blessing[] all = pl.fawizja.pixel.character.Blessing.values();
                    if (open.startsWith("bless_death") || open.equals("bless_corpse")) {
                        pc.addExperience(pl.fawizja.pixel.balance.Balance.experienceForLevel(400) - pc.experience());
                        pl.fawizja.pixel.item.ItemRegistry reg = gs.session().items();
                        for (String[] it : new String[][] {{"health_potion", "12"}, {"rope", "1"}, {"wolf_pelt", "9"}, {"gold_coin", "85"}}) {
                            if (reg.byKey(it[0]) != null) pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(reg.byKey(it[0]).id(), Integer.parseInt(it[1])));
                        }
                        if (open.equals("bless_death5")) {
                            for (pl.fawizja.pixel.character.Blessing b : all) if (b.kind() == pl.fawizja.pixel.character.Blessing.Kind.REGULAR) pc.addBlessing(b);
                        }
                        long t = gs.worldTime();
                        for (int i = 0; i < 3; i++) gs.session().combat().recordDamage("Goblin szaman", 60 + i, t);
                        pl.fawizja.pixel.world.Position at = pc.position();
                        gs.session().combat().diedFromPlayer("Kuba", false, t);
                        if (open.equals("bless_corpse")) {
                            gs.session().teleport(at.translate(0, 3), t);   // 3 pola od ciala: bez automatycznego zbierania
                        }
                    } else if (open.equals("bless_status")) {
                        pc.addBlessing(all[1]); pc.addBlessing(all[3]); pc.addBlessing(all[5]); pc.addBlessing(all[0]);
                        gs.hud().openInfo(pl.fawizja.pixel.i18n.Messages.get("skill_sheet.blogoslawienstwa"),
                                pl.fawizja.pixel.character.BlessingStatus.rows(pc, gs.session().npcs()));
                    } else {
                        // bless_monk: okno rozmowy z mnichem obok, bless_talk: rozmowa slowami (oferta z cena) na czacie
                        pc.setPremium(true);
                        pl.fawizja.pixel.economy.Money.add(pc.backpack(), 300_000);
                        if (open.equals("bless_talk")) {
                            gs.session().askNpc("błogosławieństwo", gs.worldTime());
                            gs.session().askNpc("gdzie", gs.worldTime() + 1);
                        } else {
                            gs.hud().openNpc(gs.session());
                        }
                    }
                }
                if ("npc".equals(open)) {
                    GameScreen gs = (GameScreen) getScreen();
                    gs.hud().openNpc(gs.session());
                    gs.hud().previewNpcPage(Integer.getInteger("pixel.npcPage", 0));
                }
            }
            String taps = System.getProperty("pixel.taps");
            if (taps != null && getScreen() instanceof GameScreen) {
                // kolejne dotkniecia pol w wybranych klatkach: x,y@klatka;x,y@klatka (drzwi, drabina, krzak - zrzuty)
                for (String one : taps.split(";")) {
                    String[] at = one.split("@");
                    if (at.length != 2 || Integer.parseInt(at[1].trim()) != frame) continue;
                    String[] w = at[0].split(",");
                    GameScreen gs = (GameScreen) getScreen();
                    gs.tapTile(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()),
                            gs.session().character().position().z()));
                }
            }
            String presses = System.getProperty("pixel.buttons");
            if (presses != null && getScreen() instanceof GameScreen) {
                // przyciski w wybranych klatkach: id@klatka;id@klatka (np. Kup w oknie domu po dotknieciu drzwi)
                for (String one : presses.split(";")) {
                    int at = one.lastIndexOf('@');
                    if (at > 0 && Integer.parseInt(one.substring(at + 1).trim()) == frame) {
                        ((GameScreen) getScreen()).press(one.substring(0, at).trim());
                    }
                }
            }
            if (Boolean.getBoolean("pixel.bossWin") && frame == frames - 15 && getScreen() instanceof GameScreen) {
                // offline: boss wyprawy (po dzwigni z -Dpixel.tap) ginie od jednego ciosu - podglad lupu i portalu
                GameScreen gs = (GameScreen) getScreen();
                gs.session().debugDefeatQuestBoss(gs.worldTime());
            }
            String facingDemo = System.getProperty("pixel.facingDemo");
            if (facingDemo != null && getScreen() instanceof GameScreen) facingDemo((GameScreen) getScreen(), facingDemo, frame);
            String spellDemo = System.getProperty("pixel.spellDemo");
            if (spellDemo != null && getScreen() instanceof GameScreen) spellDemo((GameScreen) getScreen(), spellDemo, frame, frames);
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
            if (Boolean.getBoolean("pixel.charmProc") && frame == frames - 1 && getScreen() instanceof GameScreen) {
                // podglad charmow w walce: Rana (obrazenia), Okaleczenie (sam napis), Unik na postaci
                GameScreen gs = (GameScreen) getScreen();
                long now = gs.worldTime();
                Position me = gs.session().character().position();
                pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me.translate(2, 1), 96, pl.fawizja.pixel.combat.CombatEvent.Kind.HIT_MONSTER,
                        null, pl.fawizja.pixel.charm.CharmEffects.eventEffect(pl.fawizja.pixel.charm.Charm.WOUND)));
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me.translate(-2, 1), 0, pl.fawizja.pixel.combat.CombatEvent.Kind.BLOCKED,
                        null, pl.fawizja.pixel.charm.CharmEffects.eventEffect(pl.fawizja.pixel.charm.Charm.CRIPPLE)));
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me, 0, pl.fawizja.pixel.combat.CombatEvent.Kind.BLOCKED,
                        null, pl.fawizja.pixel.charm.CharmEffects.eventEffect(pl.fawizja.pixel.charm.Charm.DODGE)));
            }
            if (Boolean.getBoolean("pixel.crit") && frame == frames - 1 && getScreen() instanceof GameScreen) {
                // podglad liczb walki: cios krytyczny (pomaranczowy z "krytyk"), zwykly cios, wyssanie many na postaci
                GameScreen gs = (GameScreen) getScreen();
                long now = gs.worldTime();
                Position me = gs.session().character().position();
                pl.fawizja.pixel.combat.CombatSystem cs = gs.session().combat();
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me.translate(2, 2), 172,
                        pl.fawizja.pixel.combat.CombatEvent.Kind.HIT_MONSTER, null, pl.fawizja.pixel.combat.CriticalHit.EVENT_EFFECT));
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me.translate(-2, 2), 64,
                        pl.fawizja.pixel.combat.CombatEvent.Kind.HIT_MONSTER));
                cs.addEvent(new pl.fawizja.pixel.combat.CombatEvent(now, me, 35,
                        pl.fawizja.pixel.combat.CombatEvent.Kind.HIT_PLAYER, null, pl.fawizja.pixel.combat.CombatSystem.MANA_EFFECT));
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

        /**
         * Podglad czarow i run z 0.240 (-Dpixel.spellDemo=...): field (pole ognia pod paralizowanym potworem, potwor sie pali),
         * gfb (wielka kula ognia w stado), poison (zatrucie), poison_cure (zatrucie i exana pox), exani (exani hur up spod
         * dziury), summon (utevo res "wilk"). Obszary jak w Tibii: gfb (kolo 3x3 w tlum), wave (fala przed postacia),
         * beam (promien), ultimate (hells core / rage of the skies wokol postaci), chest (skrzynia zadania i komunikat nagrody,
         * -Dpixel.chest=id).
         */
        /** Podglad zwrotu potworow (-Dpixel.facingDemo=...): "dirs" - wilk i dzik stoja twarza na N, E, S, W; "walk" - dzik idzie
         *  prosto na zachod do postaci (klatki chodu); "attack" - dzik stoi obok postaci i bije ja, patrzac na nia. */
        private static void facingDemo(GameScreen gs, String demo, int frame) {
            pl.fawizja.pixel.game.GameSession s = gs.session();
            pl.fawizja.pixel.character.PlayerCharacter pc = s.character();
            Position me = pc.position();
            long now = gs.worldTime();
            pl.fawizja.pixel.world.Direction[] dirs = {pl.fawizja.pixel.world.Direction.NORTH, pl.fawizja.pixel.world.Direction.EAST,
                    pl.fawizja.pixel.world.Direction.SOUTH, pl.fawizja.pixel.world.Direction.WEST};
            if (frame == 2) {
                pc.skills().set(pl.fawizja.pixel.character.Skill.MAGIC, 20, 0);
                for (int bit = 0; bit < 64; bit++) pc.markTip(bit);
                if (demo.equals("dirs")) {
                    pc.setInvisible(600_000);
                    String[] keys = {"wolf", "boar", "goblin"};
                    for (int row = 0; row < keys.length; row++) {
                        for (int i = 0; i < 4; i++) {
                            for (pl.fawizja.pixel.monster.Monster m : s.monsters().spawnEvent(keys[row], me.translate(i * 2 - 3, -2 - row), 0, 1, now)) {
                                m.paralyze(now + 600_000);
                            }
                        }
                    }
                } else if (demo.equals("walk")) {
                    s.monsters().spawnEvent("boar", me.translate(6, 0), 0, 1, now);
                } else if (demo.equals("attack")) {
                    s.monsters().spawnEvent("boar", me.translate(1, 0), 0, 1, now);
                    s.monsters().spawnEvent("wolf", me.translate(0, 1), 0, 1, now);
                    s.monsters().spawnEvent("goblin", me.translate(-1, 0), 0, 1, now);
                }
            }
            if (demo.equals("dirs")) {
                for (pl.fawizja.pixel.monster.Monster m : s.monsters().monsters()) {
                    int i = Math.floorMod(m.position().x() - me.x() + 3, 8) / 2;
                    m.lookAt(m.position().translate(dirs[i].dx, dirs[i].dy));
                }
            }
        }

        private static final java.util.Set<String> AREA_DEMOS = java.util.Set.of("gfb", "wave", "beam", "ultimate");

        private static void spellDemo(GameScreen gs, String demo, int frame, int frames) {
            pl.fawizja.pixel.game.GameSession s = gs.session();
            pl.fawizja.pixel.character.PlayerCharacter pc = s.character();
            long now = gs.worldTime();
            Position me = pc.position();
            if (frame == 2) {
                pc.skills().set(pl.fawizja.pixel.character.Skill.MAGIC, Math.max(pc.skills().level(pl.fawizja.pixel.character.Skill.MAGIC), 20), 0);
                pc.restoreMana(pc.maxMana());
                for (int bit = 0; bit < 64; bit++) pc.markTip(bit);
                boolean bar = demo.startsWith("aim") || demo.equals("pickermode");
                if (bar) {
                    // pole 7 paska: wielka kula ognia; tryb wg podgladu (menu trybu - na cel, reszta - celownik)
                    pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(s.items().byKey("great_fireball_rune").id(), 12));
                    gs.hud().spellBar().set(6, demo.equals("pickermode") ? "item:great_fireball_rune" : "item:great_fireball_rune:aim");
                    gs.press("bar_saved");
                }
                if (demo.equals("combo") || demo.equals("pickercombo")) {
                    // pole 1 paska: combo leczenie + atak (pierwszy gotowy czar)
                    java.util.List<String> keys = new java.util.ArrayList<>();
                    for (pl.fawizja.pixel.magic.SpellType sp : s.spells().knownSpells()) {
                        if (sp.kind() == pl.fawizja.pixel.magic.SpellKind.ATTACK_TARGET && sp.range() >= 3) { // najsilniejszy atak na cel
                            keys.clear();
                            keys.add(sp.key());
                        }
                    }
                    for (pl.fawizja.pixel.magic.SpellType sp : s.spells().knownSpells()) {
                        if (sp.kind() == pl.fawizja.pixel.magic.SpellKind.HEAL_SELF) {
                            if (keys.size() > 1) keys.remove(1);
                            keys.add(sp.key());
                        }
                    }
                    String combo = pl.fawizja.pixel.settings.SpellBar.combo(keys);
                    if (combo != null) gs.hud().spellBar().set(1, combo);
                    gs.press("bar_saved");
                }
                boolean crowd = AREA_DEMOS.contains(demo);
                if (demo.equals("field") || bar || demo.equals("combo") || crowd) {
                    pc.setInvisible(600_000); // potwory stoja w miejscu (nie widza postaci); zdjete przed zrzutem
                    String key = demo.equals("field") ? "wolf" : "boar";
                    int[][] spots = demo.equals("field") ? new int[][] {{0, -2}} : new int[][] {{0, -2}, {1, -2}, {-1, -2}, {0, -3}, {1, -3}, {-1, -3}};
                    if (crowd) {
                        // tlum na calym polu przed postacia (i wokol niej przy ultimate): widac, ktore pola trafia ksztalt
                        java.util.List<int[]> l = new java.util.ArrayList<>();
                        int top = demo.equals("beam") ? -9 : -6, bottom = demo.equals("ultimate") ? 6 : -1, side = demo.equals("ultimate") ? 7 : 4;
                        for (int dy = top; dy <= bottom; dy++) {
                            for (int dx = -side; dx <= side; dx++) {
                                if ((dx != 0 || dy != 0) && (dx + dy) % 2 == 0) l.add(new int[] {dx, dy});
                            }
                        }
                        spots = l.toArray(new int[0][]);
                    }
                    for (int[] d : spots) {
                        for (pl.fawizja.pixel.monster.Monster m : s.monsters().spawnEvent(key, me.translate(d[0], d[1]), 0, 1, now)) {
                            m.paralyze(now + 600_000);
                        }
                    }
                }
                if (demo.startsWith("poison")) {
                    pc.conditions().addTotal(pl.fawizja.pixel.combat.ConditionType.POISON, 400, 4000, now, Messages.get("desktop_launcher.pajak_lesny"));
                    pc.damage(pc.maxHealth() / 3);
                }
            }
            if (demo.equals("field") && frame == frames - 6) {
                pl.fawizja.pixel.monster.Monster m = s.monsters().monsterAt(me.translate(0, -2));
                if (m != null) s.combat().setTarget(m);
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(s.items().byKey("fire_field_rune").id(), 3));
                s.spells().useFirst("fire_field_rune", now);
                s.combat().setTarget(null);
            }
            if (demo.equals("gfb") && frame == frames - 4) {
                pl.fawizja.pixel.monster.Monster m = s.monsters().monsterAt(me.translate(0, -2));
                if (m != null) s.combat().setTarget(m);
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(s.items().byKey("great_fireball_rune").id(), 3));
                s.spells().useFirst("great_fireball_rune", now);
            }
            if (frame == frames - 7 && (demo.equals("wave") || demo.equals("beam") || demo.equals("ultimate"))) {
                // postac patrzy na polnoc, cel w ksztalcie przed nia; czar: fala / promien / ultimate najwyzszy znany
                s.applyRemotePosition(me, pl.fawizja.pixel.world.Direction.NORTH, now, 0);
                pl.fawizja.pixel.monster.Monster m = s.monsters().monsterAt(me.translate(0, -2));
                if (m != null) s.combat().setTarget(m);
                String[] order = demo.equals("wave") ? new String[] {"great_fire_wave", "fire_wave", "terra_wave", "ice_wave", "front_sweep"}
                        : demo.equals("beam") ? new String[] {"great_energy_beam", "energy_beam", "great_death_beam"}
                        : new String[] {"hells_core", "rage_of_the_skies", "wrath_of_nature", "eternal_winter", "divine_caldera", "groundshaker"};
                for (String k : order) {
                    if (s.spells().knownSpells().stream().anyMatch(sp -> sp.key().equals(k))) {
                        s.castSpell(k, now);
                        break;
                    }
                }
            }
            if (demo.equals("chest") && frame == 2) {
                // skrzynia zadania (-Dpixel.chest=id, domyslnie twierdza): postac obok niej
                String id = System.getProperty("pixel.chest", "d668_467_-3");
                for (pl.fawizja.pixel.treasure.TreasureChests.Chest c : s.chests().all()) {
                    if (!c.id().equals(id)) continue;
                    for (pl.fawizja.pixel.world.Direction d : pl.fawizja.pixel.world.Direction.values()) {
                        Position n = c.position().translate(d.dx, d.dy);
                        if (s.world().isWalkable(n)) {
                            s.teleport(n, now);
                            break;
                        }
                    }
                }
            }
            if (demo.equals("chest") && frame == frames - 6) s.openChest(now);
            if ((demo.equals("field") || AREA_DEMOS.contains(demo)) && frame == frames - 2) pc.breakInvisibility();
            String hover = System.getProperty("pixel.aimHover");
            if (hover != null && frame == 2) {
                String[] w = hover.split(",");
                gs.setAimHover(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), me.z()));
            }
            if (demo.startsWith("aim") && frame == frames - 8) {
                if (demo.equals("aimbag")) {
                    // "Uzyj z..." z plecaka: zaznaczona runa, UZYJ wlacza celownik
                    gs.hud().toggleBag();
                    int rune = s.items().byKey("great_fireball_rune").id();
                    for (int i = 0; i < pc.backpack().size(); i++) {
                        if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == rune) {
                            gs.hud().selectSlot(i);
                            break;
                        }
                    }
                    gs.press("bag_use");
                } else {
                    gs.press("spell:6");
                }
            }
            if (demo.equals("aimthrow") && frame == frames - 4 && hover != null) {
                String[] w = hover.split(",");
                gs.tapTile(new Position(Integer.parseInt(w[0].trim()), Integer.parseInt(w[1].trim()), me.z()));
            }
            if (demo.equals("pickermode") && frame == frames - 6) gs.hud().openPicker(6);
            if (demo.equals("pickercombo") && frame == frames - 6) {
                gs.hud().openPicker(1);
                gs.hud().pickCategory("combo");
            }
            if (demo.equals("combo") && frame == frames - 6) {
                pl.fawizja.pixel.monster.Monster m = s.monsters().monsterAt(me.translate(0, -2));
                if (m != null) s.combat().setTarget(m);
                gs.press("spell:1");
            }
            if (demo.equals("poison_cure") && frame == frames - 6) {
                gs.say(pc.name(), Messages.get("desktop_launcher.exana_pox"));
                s.castSpell("cure_poison", now);
            }
            if (demo.equals("exani") && frame == frames - 12) {
                gs.say(pc.name(), "exani hur up");
                s.castSpell("levitate_up", now);
            }
            if (demo.equals("summon") && frame == frames - 12) {
                gs.say(pc.name(), "utevo res \"wilk\"");
                s.castSpell("summon_creature", "wilk", now);
            }
        }

        /** Kierunek z -Dpixel.dpad: skrot (N, NE, E, SE, S, SW, W, NW) albo nazwa (NORTH_EAST). */
        private static pl.fawizja.pixel.world.Direction direction(String s) {
            String k = s.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace("N", "NORTH_").replace("S", "SOUTH_").replace("E", "EAST_").replace("W", "WEST_");
            if (s.trim().length() > 2) return pl.fawizja.pixel.world.Direction.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
            k = k.replaceAll("_+$", "");
            return pl.fawizja.pixel.world.Direction.valueOf(k);
        }

        /**
         * Podglad klejnotow (-Dpixel.wheelGems=true): nieodkryte klejnoty i okruchy w plecaku, kilka odkrytych klejnotow
         * (staly generator), wiekszy w naczyniu fioletowej domeny, zwykly w czerwonej, dwa mody na wyzszym stopniu.
         */
        private static void wheelGems(pl.fawizja.pixel.character.PlayerCharacter pc, pl.fawizja.pixel.item.ItemRegistry items) {
            pl.fawizja.pixel.wheel.GemAtelier a = pc.wheel().gems();
            java.util.Random rng = new java.util.Random(7);
            for (pl.fawizja.pixel.wheel.GemQuality q : pl.fawizja.pixel.wheel.GemQuality.values()) {
                pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(items.byKey(pl.fawizja.pixel.wheel.GemAtelier.gemKey(pc.vocation(), q)).id(),
                        q == pl.fawizja.pixel.wheel.GemQuality.LESSER ? 3 : 1));
            }
            pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(items.byKey(pl.fawizja.pixel.wheel.GemAtelier.LESSER_FRAGMENT_KEY).id(), 12));
            pc.backpack().add(new pl.fawizja.pixel.item.ItemStack(items.byKey(pl.fawizja.pixel.wheel.GemAtelier.GREATER_FRAGMENT_KEY).id(), 6));
            pl.fawizja.pixel.wheel.WheelGem lesser = a.reveal(pl.fawizja.pixel.wheel.GemQuality.LESSER, pc.vocation(), rng);
            a.reveal(pl.fawizja.pixel.wheel.GemQuality.LESSER, pc.vocation(), rng);
            pl.fawizja.pixel.wheel.WheelGem regular = a.reveal(pl.fawizja.pixel.wheel.GemQuality.REGULAR, pc.vocation(), rng);
            pl.fawizja.pixel.wheel.WheelGem greater = a.reveal(pl.fawizja.pixel.wheel.GemQuality.GREATER, pc.vocation(), rng);
            while (greater.domain() != pl.fawizja.pixel.wheel.WheelDomain.PURPLE) a.switchDomain(greater.id);
            while (regular.domain() != pl.fawizja.pixel.wheel.WheelDomain.RED) a.switchDomain(regular.id);
            a.socket(greater.id);
            a.socket(regular.id);
            a.toggleLock(lesser.id);
            a.gradeUp(greater.basic1);
            a.gradeUp(greater.basic1);
            a.gradeUp(greater.supreme, pc.vocation());
            pc.refreshWheel();
        }

        /** Podglad kola: zapelnia otwarte pola cwiartek w podanej kolejnosci (np. "PRBG"), az skoncza sie punkty. */
        private static void fillWheel(pl.fawizja.pixel.character.PlayerCharacter pc, String order) {
            int av = pc.wheelPoints();
            boolean progress = true;
            while (progress && pc.wheel().unused(av) > 0) {
                progress = false;
                for (char c : order.toCharArray()) {
                    pl.fawizja.pixel.wheel.WheelDomain d = pl.fawizja.pixel.wheel.WheelDomain.ofCode(c);
                    if (d == null) continue;
                    for (pl.fawizja.pixel.wheel.WheelSlice s : pl.fawizja.pixel.wheel.WheelSlice.of(d)) {
                        int n = Math.min(pc.wheel().unused(av), s.capacity() - pc.wheel().points(s));
                        if (n > 0 && pc.wheel().add(s, n, av) == pl.fawizja.pixel.wheel.DestinyWheel.Result.OK) progress = true;
                    }
                    if (progress) break; // cwiartka po cwiartce: najpierw wypelnij pierwsza
                }
            }
            pc.refreshWheel();
        }
    }
}
