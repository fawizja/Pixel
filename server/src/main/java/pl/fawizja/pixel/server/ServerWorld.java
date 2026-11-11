package pl.fawizja.pixel.server;

import pl.fawizja.pixel.i18n.Messages;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.magic.ConsumableRegistry;
import pl.fawizja.pixel.magic.RecipeRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.net.Protocol;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.pvp.PvpRules;
import pl.fawizja.pixel.trade.PlayerTrade;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.save.SaveStore;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.MapLoader;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.TileRegistry;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Jeden wspolny swiat: potwory, przedmioty na ziemi i sesje wszystkich graczy. Wszystkie metody
 * wywoluje tylko watek petli serwera (bez blokad w logice gry).
 */
final class ServerWorld {
    private static final Logger LOG = Logger.getLogger("pixel.server");
    static final long SAVE_EVERY_MS = 60_000;
    static final long SELF_EVERY_MS = 250;
    /** Kawalki mapy (64 x 64) w jednym komunikacie FOG: najwyzej ~44 KB base64 (limit linii 64 KB). */
    static final int FOG_CHUNKS_PER_LINE = 64;
    /** Czat: najwyzej CHAT_MAX wiadomosci w CHAT_WINDOW_MS. */
    static final int CHAT_MAX = 5;
    static final long CHAT_WINDOW_MS = 10_000;

    final ItemRegistry items = ItemRegistry.loadDefault();
    final WorldMap world;
    final MonsterManager monsters;
    /** Areny wypraw na bossa w piec postaci (wspolne dla wszystkich graczy; serwer rozstrzyga dzwignie i lup). */
    final pl.fawizja.pixel.game.BossQuestRuntime bossQuests;
    final GroundItems ground = new GroundItems();
    /** Stan PvP po imieniu - przetrwa wylogowanie (czaszki nie znikaja po ponownym wejsciu). */
    private final Map<String, PvpRules.Status> pvp = new java.util.HashMap<>();
    /** Limit zakladania kont: tyle nowych kont z jednego adresu na dobe. */
    static final long NEW_ACCOUNT_WINDOW_MS = 24 * 3_600_000L;
    int maxNewAccountsPerAddress = 5;
    private final Map<java.net.InetAddress, java.util.ArrayDeque<Long>> newAccounts = new java.util.HashMap<>();
    private long lastNow;
    private final Random pvpRng = new Random();
    private final StartingConfig config = StartingConfig.loadDefault();
    private final MonsterRegistry monsterTypes;
    private final NpcRegistry npcs;
    private final pl.fawizja.pixel.house.HouseRegistry houses;
    private final File housesFile;
    private final pl.fawizja.pixel.house.HouseOwnersMap houseOwners;
    /** Wlasciciele domow wspolni dla graczy; kazda zmiana od razu na dysk, a na koniec kroku do klientow (HACC). */
    private final pl.fawizja.pixel.house.HouseOwners savedHouseOwners = new pl.fawizja.pixel.house.HouseOwners() {
        @Override
        public String ownerOf(String id) { return houseOwners.ownerOf(id); }

        @Override
        public boolean claim(String id, String owner) {
            boolean ok = houseOwners.claim(id, owner);
            if (ok) housesChanged();
            return ok;
        }

        @Override
        public void release(String id) {
            houseOwners.release(id);
            housesChanged();
        }

        @Override
        public List<String> guests(String id) { return houseOwners.guests(id); }

        @Override
        public java.util.Map<String, pl.fawizja.pixel.house.HouseAccess> guestLevels(String id) { return houseOwners.guestLevels(id); }

        @Override
        public pl.fawizja.pixel.house.HouseAccess guestAccess(String id, String name) { return houseOwners.guestAccess(id, name); }

        @Override
        public boolean addGuest(String id, String name) { return addGuest(id, name, pl.fawizja.pixel.house.HouseAccess.FULL); }

        @Override
        public boolean addGuest(String id, String name, pl.fawizja.pixel.house.HouseAccess level) {
            boolean ok = houseOwners.addGuest(id, name, level);
            if (ok) housesChanged();
            return ok;
        }

        @Override
        public boolean removeGuest(String id, String name) {
            boolean ok = houseOwners.removeGuest(id, name);
            if (ok) housesChanged();
            return ok;
        }

        @Override
        public boolean transfer(String id, String owner) {
            boolean ok = houseOwners.transfer(id, owner);
            if (ok) housesChanged();
            return ok;
        }

        @Override
        public long rentPaidUntil(String id) { return houseOwners.rentPaidUntil(id); }

        @Override
        public int rentMissed(String id) { return houseOwners.rentMissed(id); }

        @Override
        public void setRent(String id, long paidUntil, int missed) {
            houseOwners.setRent(id, paidUntil, missed);
            saveHouses();
        }
    };
    /** Zmienili sie wlasciciele albo goscie: wstep do domow trzeba sprawdzic i rozeslac na koncu kroku. */
    private boolean housesDirty;

    private void housesChanged() {
        saveHouses();
        housesDirty = true;
    }

    /** Oferty sprzedazy domow innym graczom: kupujacy (male litery) -> oferta. */
    private final Map<String, HouseOffer> houseOffers = new java.util.HashMap<>();
    static final long HOUSE_OFFER_MS = 5 * 60_000L;

    record HouseOffer(String houseId, String seller, String buyer, long price, long until) {}
    private final ShopRegistry shops;
    private final QuestRegistry quests;
    private final pl.fawizja.pixel.treasure.TreasureChests chests;
    private final SpellRegistry spells;
    private final ConsumableRegistry consumables;
    private final RecipeRegistry recipes;
    private final CharacterCodec codec = new CharacterCodec(items);
    {
        codec.setWallClock(() -> this.wallClock.getAsLong()); // znacznik wylogowania staminy wg zegara serwera
    }
    /** Ranking wszystkich postaci (takze offline), budowany z zapisow przy starcie. */
    final pl.fawizja.pixel.ranking.Ranking ranking = new pl.fawizja.pixel.ranking.Ranking();
    private final SaveStore saves;
    private final AccountStore accounts;
    private final Random rng = new Random();
    /** Trwajaca inwazja na miasto (null = spokoj) i czas nastepnej (0 = ustalany przy pierwszym kroku). */
    private pl.fawizja.pixel.event.Invasion invasion;
    private long nextInvasionAt;
    private int nextInvasionPlan = -1;
    private final long invasionEveryMs = Math.max(1, Long.getLong("pixel.invasionMinutes", 120L)) * 60_000L;
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
    private final pl.fawizja.pixel.party.PartyManager parties = new pl.fawizja.pixel.party.PartyManager();
    /** Otwarte wymiany (klucz: imie malymi literami, obie strony wskazuja ta sama wymiane). */
    private final Map<String, PlayerTrade> trades = new java.util.HashMap<>();
    private final pl.fawizja.pixel.guild.GuildRegistry guilds;
    private final Moderation moderation;
    /** Zegar scienny dla banow i wyciszen (testy moga go podmienic). */
    volatile java.util.function.LongSupplier wallClock = System::currentTimeMillis;
    private final File guildFile;
    private final File marketFile;
    /** Nagrody za glowy (bounties.txt). */
    private final File bountiesFile;
    private final pl.fawizja.pixel.pvp.Bounties bounties;
    /** Zamowienia kupna (buyorders.txt). */
    private final File ordersFile;
    private final pl.fawizja.pixel.market.BuyOrders orders;
    private final File mailFile;
    /** Listy do graczy offline (letters.txt). */
    private final File lettersFile;
    /** Zgloszenia graczy dla administracji (dopisywane, jedna linia na zgloszenie). */
    private final File reportsFile;
    /** Ranking tygodnia (weekly.txt). */
    private final File weeklyFile;
    private final pl.fawizja.pixel.ranking.WeeklyRanking weekly;
    static final int REPORT_MAX = 200;
    static final long REPORT_EVERY_MS = 60_000;
    private final pl.fawizja.pixel.mail.Letters letters;
    /** Wiadomosc dnia serwera (motd.txt, polecenie MOTD); pusta = brak. */
    private final File motdFile;
    private String motd = "";
    static final int MOTD_MAX = 200;
    final pl.fawizja.pixel.mail.Mailbox mail;
    final pl.fawizja.pixel.market.Market market;
    /** Prosby o handel: adresat -> (proszacy -> waznosc). */
    private final Map<String, Map<String, Long>> tradeRequests = new java.util.HashMap<>();
    static final long TRADE_REQUEST_MS = 60_000L;
    private int nextId = 1;

    ServerWorld(File saveDir) throws IOException {
        world = new MapLoader(TileRegistry.loadDefault()).load(WorldLayout.WORLD_MAP);
        monsterTypes = MonsterRegistry.loadDefault(items);
        monsters = new MonsterManager(world, monsterTypes, SpawnTable.load(WorldLayout.WORLD_MAP, world, monsterTypes), rng);
        monsters.populate(null);
        bossQuests = new pl.fawizja.pixel.game.BossQuestRuntime(world, items, monsters, new Random(rng.nextLong()));
        shops = ShopRegistry.loadDefault(items);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        houses = pl.fawizja.pixel.house.HouseRegistry.load(WorldLayout.WORLD_MAP, world);
        housesFile = new File(saveDir, "houses.txt");
        houseOwners = pl.fawizja.pixel.house.HouseOwnersMap.decode(housesFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(housesFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "");
        quests = QuestRegistry.loadDefault(items, monsterTypes);
        chests = pl.fawizja.pixel.treasure.TreasureChests.generate(world);
        spells = SpellRegistry.loadDefault(items);
        consumables = ConsumableRegistry.loadDefault(items);
        recipes = RecipeRegistry.loadDefault(items);
        saves = new SaveStore(saveDir);
        accounts = new AccountStore(saveDir);
        moderation = new Moderation(saveDir);
        guildFile = new File(saveDir, "guilds.txt");
        guilds = pl.fawizja.pixel.guild.GuildRegistry.decode(guildFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(guildFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        bountiesFile = new File(saveDir, "bounties.txt");
        bounties = pl.fawizja.pixel.pvp.Bounties.decode(bountiesFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(bountiesFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        ordersFile = new File(saveDir, "buyorders.txt");
        orders = pl.fawizja.pixel.market.BuyOrders.decode(ordersFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(ordersFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null,
                System.currentTimeMillis());
        marketFile = new File(saveDir, "market.txt");
        market = pl.fawizja.pixel.market.Market.decode(marketFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(marketFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        lettersFile = new File(saveDir, "letters.txt");
        reportsFile = new File(saveDir, "reports.txt");
        weeklyFile = new File(saveDir, "weekly.txt");
        weekly = pl.fawizja.pixel.ranking.WeeklyRanking.decode(weeklyFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(weeklyFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        letters = pl.fawizja.pixel.mail.Letters.decode(lettersFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(lettersFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        mailFile = new File(saveDir, "mail.txt");
        mail = pl.fawizja.pixel.mail.Mailbox.decode(mailFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(mailFile.toPath()), java.nio.charset.StandardCharsets.UTF_8) : null);
        motdFile = new File(saveDir, "motd.txt");
        if (motdFile.isFile()) {
            motd = cleanMotd(new String(java.nio.file.Files.readAllBytes(motdFile.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        }
        loadRanking(saveDir);
        cratesFile = new File(saveDir, "crates.txt");
        if (cratesFile.isFile()) {
            world.crates().restore(new String(java.nio.file.Files.readAllBytes(cratesFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8), items);
        }
        world.crates().setListener(msg -> {
            broadcast(null, msg);
            saveCrates();
        });
        wallLightsFile = new File(saveDir, "walllights.txt");
        if (wallLightsFile.isFile()) {
            world.wallLights().restore(new String(java.nio.file.Files.readAllBytes(wallLightsFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        world.openings().setListener(msg -> broadcast(null, msg));   // drzwi i okna: stan wspolny dla wszystkich
        world.wallLights().setListener(msg -> {
            broadcast(null, msg);
            saveWallLights();
        });
    }

    private final File wallLightsFile;

    /** Pochodnie i latarnie na murach (zapalone / zgaszone) wspolne dla wszystkich graczy; zapis po kazdej zmianie. */
    private void saveWallLights() {
        try {
            File tmp = new File(wallLightsFile.getParentFile(), "walllights.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), world.wallLights().encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), wallLightsFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("wall lights save failed: " + e.getMessage());
        }
    }

    private final File cratesFile;

    /** Skrzynie (przesuniecia i zawartosc) wspolne dla wszystkich graczy; zapis po kazdej zmianie. */
    private void saveCrates() {
        try {
            File tmp = new File(cratesFile.getParentFile(), "crates.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), world.crates().encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), cratesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("crates save failed: " + e.getMessage());
        }
    }

    /** Nowy klient dostaje obecny stan skrzyn (jego mapa jest swieza z pliku). */
    private void sendCrates(ClientHandler client) {
        for (Position c : world.openings().openPositions()) {
            client.send("DOOR", String.valueOf(c.x()), String.valueOf(c.y()), String.valueOf(c.z()), "1");
        }
        for (java.util.Map.Entry<Position, Boolean> e : world.wallLights().changes().entrySet()) {
            Position c = e.getKey();
            client.send("WLIGHT", String.valueOf(c.x()), String.valueOf(c.y()), String.valueOf(c.z()), e.getValue() ? "1" : "0");
        }
        for (java.util.Map.Entry<Position, Position> e : world.crates().moves().entrySet()) {
            Position o = e.getValue(), c = e.getKey();
            client.send("CRATEMV", String.valueOf(o.x()), String.valueOf(o.y()), String.valueOf(o.z()),
                    String.valueOf(c.x()), String.valueOf(c.y()), String.valueOf(c.z()));
        }
        for (java.util.Map.Entry<Position, String> e : world.crates().allContents().entrySet()) {
            Position c = e.getKey();
            client.send("CRATE", String.valueOf(c.x()), String.valueOf(c.y()), String.valueOf(c.z()), e.getValue());
        }
    }

    static String cleanMotd(String s) {
        String t = s == null ? "" : s.replace('\r', ' ').replace('\n', ' ').trim();
        return t.length() > MOTD_MAX ? t.substring(0, MOTD_MAX) : t;
    }

    String motd() { return motd; }

    pl.fawizja.pixel.house.HouseRegistry houses() { return houses; }

    pl.fawizja.pixel.house.HouseOwners houseOwners() { return savedHouseOwners; }

    /** Wydarzenie premii doswiadczenia (EXPEVENT procent [minuty]); 0 = brak. Tylko w pamieci. */
    private int expEventPercent;
    private long expEventUntil;
    static final int EXP_EVENT_MAX = 200;

    /** EXPEVENT: stan; EXPEVENT 50 [60]: +50% na 60 min (domyslnie 60); EXPEVENT 0: koniec. */
    String expEvent(String[] c, long now) {
        List<String> a = new ArrayList<>();
        for (int i = 1; i < c.length; i++) for (String t : c[i].trim().split("\\s+")) if (!t.isEmpty()) a.add(t);
        if (a.isEmpty()) return expEventPercent > 0 ? expEventLine(now) : Messages.get("server_world.brak_wydarzenia_expevent_proce");
        int pct;
        int minutes = 60;
        try {
            pct = Integer.parseInt(a.get(0));
            if (a.size() > 1) minutes = Integer.parseInt(a.get(1));
        } catch (NumberFormatException e) {
            return Messages.get("server_world.uzycie_expevent_procent_minuty");
        }
        if (pct <= 0) {
            if (expEventPercent == 0) return Messages.get("server_world.brak_wydarzenia");
            endExpEvent(now);
            return Messages.get("server_world.wydarzenie_zakonczone");
        }
        if (pct > EXP_EVENT_MAX || minutes <= 0 || minutes > 24 * 60) return Messages.get("server_world.procent_1") + EXP_EVENT_MAX + Messages.get("server_world.czas_1_1440_minut");
        expEventPercent = pct;
        expEventUntil = now + minutes * 60_000L;
        for (ServerPlayer o : players.values()) o.session.log().add(now, EventLog.Kind.SYSTEM, expEventLine(now));
        LOG.info("exp event +" + pct + Messages.get("server_world.for") + minutes + Messages.get("hud.min"));
        return expEventLine(now);
    }

    private String expEventLine(long now) {
        long min = Math.max(1, (expEventUntil - now + 59_999) / 60_000);
        return Messages.get("server_world.wydarzenie") + expEventPercent + Messages.get("server_world.doswiadczenia_za_potwory_jeszc") + min + Messages.get("server_world.min");
    }

    private void updateExpEvent(long now) {
        if (expEventPercent > 0 && now >= expEventUntil) endExpEvent(now);
        for (java.util.Iterator<Map.Entry<String, Long>> it = guildBanners.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.byName(e.getKey());
            if (g != null) toGuild(g, Messages.get("server_world.sztandar_gildii_opadl_premia"), now);
        }
        for (ServerPlayer p : players.values()) {
            p.session.combat().setBonusExpPercent(expEventPercent + (bannerUntil(p.name(), now) > now ? BANNER_PERCENT : 0));
        }
    }

    /** Sztandar gildii: lider placi ze skarbca, czlonkowie dostaja premie exp na godzine (tylko w pamieci serwera). */
    static final long BANNER_COST = 100_000;
    static final int BANNER_PERCENT = 10;
    static final long BANNER_MS = 60 * 60_000L;
    /** Nazwa gildii (male litery) -> koniec sztandaru (czas serwera). */
    private final Map<String, Long> guildBanners = new java.util.HashMap<>();

    /** Opis premii exp gracza do okna wydarzen: wydarzenie serwera i sztandar gildii ("-" = brak). */
    private String expBonusLine(ServerPlayer p, long now) {
        List<String> parts = new ArrayList<>();
        if (expEventPercent > 0) {
            parts.add("+" + expEventPercent + Messages.get("server_world.za_potwory_jeszcze") + Math.max(1, (expEventUntil - now + 59_999) / 60_000) + Messages.get("hud.min"));
        }
        long banner = bannerUntil(p.name(), now);
        if (banner > now) parts.add(Messages.get("server_world.sztandar_gildii") + BANNER_PERCENT + "%, " + Math.max(1, (banner - now + 59_999) / 60_000) + Messages.get("hud.min"));
        return parts.isEmpty() ? "-" : String.join("; ", parts);
    }

    private long bannerUntil(String member, long now) {
        pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(member);
        return g == null ? 0 : guildBanners.getOrDefault(g.name().toLowerCase(java.util.Locale.ROOT), 0L);
    }

    private void endExpEvent(long now) {
        expEventPercent = 0;
        for (ServerPlayer o : players.values()) o.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.wydarzenie_premii_doswiadczeni"));
        LOG.info("exp event ended");
    }

    /** Nowa wiadomosc dnia ("-" albo pusta czysci); zapis do motd.txt. */
    String setMotd(String text) throws IOException {
        motd = text == null || text.trim().equals("-") ? "" : cleanMotd(text);
        java.nio.file.Files.write(motdFile.toPath(), motd.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return motd.isEmpty() ? Messages.get("server_world.wiadomosc_dnia_usunieta") : Messages.get("server_world.wiadomosc_dnia") + motd;
    }

    /** Ranking z zapisow postaci (player_*.save); uszkodzony zapis jest pomijany. */
    private void loadRanking(File saveDir) {
        File[] files = saveDir.listFiles((d, n) -> n.startsWith("player_") && n.endsWith(".save"));
        if (files == null) return;
        for (File f : files) {
            String slot = f.getName().substring(0, f.getName().length() - ".save".length());
            try {
                PlayerCharacter pc = saves.read(slot, codec::decode).value();
                if (pc != null) ranking.put(rankEntry(pc));
            } catch (IOException | RuntimeException e) {
                LOG.warning("ranking: pomijam " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private static pl.fawizja.pixel.ranking.Ranking.Entry rankEntry(PlayerCharacter pc) {
        pl.fawizja.pixel.character.Skill[] all = pl.fawizja.pixel.character.Skill.values();
        int[] skills = new int[all.length];
        for (int i = 0; i < all.length; i++) skills[i] = pc.skills().level(all[i]);
        return new pl.fawizja.pixel.ranking.Ranking.Entry(pc.name(), pc.vocation(), pc.level(), pc.experience(), skills);
    }

    /** /ranking magia, miecz...: pierwsze miejsca wedlug poziomu umiejetnosci. */
    private void sendSkillRanking(ServerPlayer p, pl.fawizja.pixel.character.Skill skill) {
        for (ServerPlayer o : players.values()) ranking.put(rankEntry(o.session.character()));
        java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
        int i = 1;
        for (pl.fawizja.pixel.ranking.Ranking.Entry e : ranking.topSkill(pl.fawizja.pixel.ranking.Ranking.SHOWN, skill)) {
            rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i++, e.name(), e.vocation().displayName(), e.skill(skill)));
        }
        sendRank(p, new pl.fawizja.pixel.ranking.RankingView(pl.fawizja.pixel.ranking.Ranking.SKILL_TITLE + skill.label(),
                ranking.positionSkill(p.name(), skill), ranking.size(null), rows));
    }

    /** /ranking [profesja]: pierwsze miejsca i miejsce pytajacego. */
    private void sendRanking(ServerPlayer p, String arg, long now) {
        if (pl.fawizja.pixel.ranking.WeeklyRanking.isWeeklyArg(arg)) {
            long wall = wallClock.getAsLong();
            weeklyRollover(now);
            for (ServerPlayer o : players.values()) {
                ranking.put(rankEntry(o.session.character()));
                weekly.observe(o.name(), o.session.character().experience(), wall);
            }
            sendRank(p, weekly.view(ranking, p.name(), pl.fawizja.pixel.ranking.Ranking.SHOWN, wall));
            return;
        }
        if (pl.fawizja.pixel.ranking.GuildRanking.isGuildArg(arg)) {
            for (ServerPlayer o : players.values()) ranking.put(rankEntry(o.session.character()));
            pl.fawizja.pixel.guild.GuildRegistry.Guild mine = guilds.guildOf(p.name());
            sendRank(p, pl.fawizja.pixel.ranking.GuildRanking.view(
                    pl.fawizja.pixel.ranking.GuildRanking.scores(guilds.all(), ranking), mine == null ? null : mine.name()));
            return;
        }
        pl.fawizja.pixel.vocation.Vocation filter = null;
        if (arg != null && !arg.isBlank()) {
            filter = pl.fawizja.pixel.ranking.Ranking.vocationFrom(arg);
            pl.fawizja.pixel.character.Skill skill = filter == null ? pl.fawizja.pixel.ranking.Ranking.skillFrom(arg) : null;
            if (skill != null) {
                sendSkillRanking(p, skill);
                return;
            }
            if (filter == null) {
                warn(p, Messages.get("server_world.nie_znam_profesji_ani") + arg.trim() + Messages.get("server_world.wpisz_ranking_ranking_druid")
                        + Messages.get("server_world.magia_miecz_dystans_piesci"), now);
                return;
            }
        }
        // gracze online: biezacy stan, nie z ostatniego zapisu
        for (ServerPlayer o : players.values()) ranking.put(rankEntry(o.session.character()));
        String title = filter == null ? Messages.get("desktop_launcher.ranking_serwera") : Messages.get("server_world.ranking") + filter.displayName();
        java.util.List<pl.fawizja.pixel.ranking.RankingView.Row> rows = new java.util.ArrayList<>();
        int i = 1;
        for (pl.fawizja.pixel.ranking.Ranking.Entry e : ranking.top(pl.fawizja.pixel.ranking.Ranking.SHOWN, filter)) {
            rows.add(new pl.fawizja.pixel.ranking.RankingView.Row(i++, e.name(), e.vocation().displayName(), e.level()));
        }
        sendRank(p, new pl.fawizja.pixel.ranking.RankingView(title, ranking.position(p.name(), filter), ranking.size(filter), rows));
    }

    private void sendRank(ServerPlayer p, pl.fawizja.pixel.ranking.RankingView view) {
        String[] f = view.encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "RANK";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    /** Ostatnie zgloszenia graczy (najnowsze na koncu) dla REPORTS. */
    String recentReports(int n) throws IOException {
        if (!reportsFile.isFile()) return Messages.get("server_world.brak_zgloszen");
        List<String> lines = java.nio.file.Files.readAllLines(reportsFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (String l : lines.subList(Math.max(0, lines.size() - n), lines.size())) {
            String[] f = l.split(";", 4);
            if (f.length == 4) out.add(f[0].replace('T', ' ').replaceAll("\\.\\d+Z$|Z$", "") + " " + f[1] + " -> " + f[2] + ": " + f[3]);
        }
        return Messages.get("server_world.zgloszenia") + lines.size() + Messages.get("server_world.ostatnie") + out.size() + "): " + String.join(" | ", out);
    }

    /** REPORT imie powod: zapis w reports.txt i ostrzezenie dla administratorow w grze. */
    private void report(ServerPlayer p, String[] f, long now) {
        String who = f.length > 1 ? f[1].trim() : "";
        String why = f.length > 2 ? f[2].replace('\n', ' ').trim() : "";
        if (who.isEmpty() || why.isEmpty()) {
            warn(p, Messages.get("server_world.napisz_zglos_imie_co"), now);
            return;
        }
        if (!saved(who) && find(who) == null) {
            warn(p, Messages.get("server_world.nie_ma_takiej_postaci"), now);
            return;
        }
        if (now - p.lastReportMs < REPORT_EVERY_MS) {
            warn(p, Messages.get("server_world.kolejne_zgloszenie_mozesz_wysl"), now);
            return;
        }
        p.lastReportMs = now;
        if (why.length() > REPORT_MAX) why = why.substring(0, REPORT_MAX);
        String line = java.time.Instant.ofEpochMilli(wallClock.getAsLong()) + ";" + p.name() + ";" + who + ";" + why + "\n";
        try {
            java.nio.file.Files.write(reportsFile.toPath(), line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warning("report not saved: " + e.getMessage());
        }
        LOG.info("report " + p.name() + " -> " + who + ": " + why);
        int admins = 0;
        for (ServerPlayer o : players.values()) {
            if (moderation.isAdmin(o.name())) {
                warn(o, Messages.get("server_world.zgloszenie") + p.name() + Messages.get("server_world.zglasza") + who + ": " + why, now);
                admins++;
            }
        }
        info(p, Messages.get("server_world.zgloszenie_wyslane_do_administ") + (admins > 0 ? Messages.get("server_world.w_grze") + admins + ")." : Messages.get("server_world.przeczytaja_je_po_powrocie")), now);
    }

    /** Opis gracza dla /obejrzyj: profesja, poziom, gildia, tytul i zalozony sprzet z ulepszeniami. */
    List<String> inspectLines(ServerPlayer o) {
        PlayerCharacter c = o.session.character();
        String tag = guildTag(c.name());
        String title = pl.fawizja.pixel.achievement.Achievement.titleOf(c.title());
        List<String> out = new ArrayList<>();
        out.add(c.name() + ": " + c.vocation().displayName() + Messages.get("pixel_game.poziom") + c.level()
                + (tag == null || tag.equals("-") ? "" : Messages.get("server_world.gildia") + tag + ">") + (title == null ? "" : ", " + title) + ".");
        List<String> worn = new ArrayList<>();
        for (pl.fawizja.pixel.item.EquipSlot slot : pl.fawizja.pixel.item.EquipSlot.values()) {
            pl.fawizja.pixel.item.ItemStack st = c.equipment().get(slot);
            if (st == null) continue;
            pl.fawizja.pixel.item.ItemType t = items.get(st.itemId());
            if (t == null) continue;
            worn.add(t.name() + (st.upgrade() > 0 ? " +" + st.upgrade() : "") + (st.count() > 1 ? " x" + st.count() : ""));
        }
        out.add(worn.isEmpty() ? Messages.get("server_world.nic_nie_ma_na") : Messages.get("server_world.zalozone") + String.join(", ", worn) + ".");
        return out;
    }

    /** Czlonkowie gildii: najpierw w grze, potem wg poziomu; przy nieobecnych - kiedy ostatnio grali. Po 6 na linie. */
    List<String> guildMemberLines(pl.fawizja.pixel.guild.GuildRegistry.Guild g) {
        record M(String name, boolean online, int level, long ago) {}
        List<M> all = new ArrayList<>();
        for (String n : g.members()) {
            ServerPlayer o = find(n);
            if (o != null) {
                all.add(new M(o.name(), true, o.session.character().level(), 0));
            } else {
                pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(n);
                long saved = saves.lastModified("player_" + n.toLowerCase(java.util.Locale.ROOT));
                all.add(new M(e == null ? n : e.name(), false, e == null ? 0 : e.level(),
                        saved <= 0 ? -1 : Math.max(0, (wallClock.getAsLong() - saved) / 60_000L)));
            }
        }
        all.sort(java.util.Comparator.comparing((M m) -> !m.online()).thenComparing(M::level, java.util.Comparator.reverseOrder()));
        List<String> out = new ArrayList<>();
        int online = 0;
        for (M m : all) if (m.online()) online++;
        StringBuilder sb = new StringBuilder(Messages.get("server_world.czlonkowie") + g.name() + " (" + all.size() + Messages.get("server_world.w_grze_2") + online + "): ");
        for (int i = 0; i < all.size(); i++) {
            M m = all.get(i);
            String ago = pl.fawizja.pixel.friends.FriendsView.describeAgo(m.ago());
            if (i > 0 && i % 6 == 0) {
                out.add(sb.toString());
                sb = new StringBuilder();
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(m.name()).append(m.name().equalsIgnoreCase(g.leader()) ? " [lider]" : "")
                    .append(" (").append(m.level() > 0 ? "poz. " + m.level() + ", " : "")
                    .append(m.online() ? Messages.get("server_world.w_grze_3") : ago.isEmpty() ? Messages.get("server_world.poza_gra") : ago).append(')');
        }
        out.add(sb.toString());
        return out;
    }

    /** FRIEND LIST | ADD imie | REMOVE imie; po kazdej zmianie klient dostaje liste (FRIENDS). */
    private void friendCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        pl.fawizja.pixel.friends.FriendList list = p.session.character().friends();
        switch (f[1]) {
            case "ADD": {
                if (f.length < 3) return;
                String name = knownName(f[2].trim());
                pl.fawizja.pixel.friends.FriendList.Result r = name == null
                        ? pl.fawizja.pixel.friends.FriendList.Result.UNKNOWN : list.add(p.name(), name);
                if (r != pl.fawizja.pixel.friends.FriendList.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                info(p, name + Messages.get("server_world.dodany_do_znajomych"), now);
                break;
            }
            case "REMOVE": {
                if (f.length < 3) return;
                pl.fawizja.pixel.friends.FriendList.Result r = list.remove(f[2]);
                if (r != pl.fawizja.pixel.friends.FriendList.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                info(p, f[2].trim() + Messages.get("social_client.usuniety_ze_znajomych"), now);
                break;
            }
            case "LIST": break;
            default: return;
        }
        sendFriends(p);
    }

    /** Imie postaci tak, jak je zapisano (online albo w rankingu), null gdy jej nie ma. */
    private String knownName(String name) {
        ServerPlayer o = find(name);
        if (o != null) return o.name();
        pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(name);
        return e == null ? null : e.name();
    }

    private void sendFriends(ServerPlayer p) {
        java.util.List<pl.fawizja.pixel.friends.FriendsView.Row> rows = new java.util.ArrayList<>();
        for (String n : p.session.character().friends().names()) {
            ServerPlayer o = find(n);
            if (o != null) {
                PlayerCharacter c = o.session.character();
                rows.add(new pl.fawizja.pixel.friends.FriendsView.Row(c.name(), true, c.vocation().displayName(), c.level()));
            } else {
                pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(n);
                long saved = saves.lastModified("player_" + n.toLowerCase(java.util.Locale.ROOT));
                rows.add(new pl.fawizja.pixel.friends.FriendsView.Row(n, false,
                        e == null ? "-" : e.vocation().displayName(), e == null ? 0 : e.level(),
                        saved <= 0 ? -1 : Math.max(0, (wallClock.getAsLong() - saved) / 60_000L)));
            }
        }
        String[] f = new pl.fawizja.pixel.friends.FriendsView(rows).encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "FRIENDS";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    /** Powiadomienie znajomych o wejsciu / wyjsciu gracza. */
    private void tellFriends(ServerPlayer p, String text, long now) {
        for (ServerPlayer o : players.values()) {
            if (o != p && o.session.character().friends().contains(p.name())) {
                Messages.runAs(o.language, () -> o.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.znajomy") + p.name() + " " + text));
            }
        }
    }

    /** Wiersz "Ranking tygodnia" okna Wydarzenia: miejsce gracza i ile do konca tygodnia. */
    String weeklyLine(ServerPlayer p, long now) {
        long wall = wallClock.getAsLong();
        weeklyRollover(now);
        for (ServerPlayer o : players.values()) {
            ranking.put(rankEntry(o.session.character()));
            weekly.observe(o.name(), o.session.character().experience(), wall);
        }
        pl.fawizja.pixel.ranking.RankingView v = weekly.view(ranking, p.name(), pl.fawizja.pixel.ranking.Ranking.SHOWN, wall);
        java.time.ZonedDateTime t = java.time.Instant.ofEpochMilli(wall).atZone(java.time.ZoneOffset.UTC);
        long hours = java.time.Duration.between(t, t.toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay(java.time.ZoneOffset.UTC)).toHours();
        String left = hours >= 48 ? hours / 24 + Messages.get("server_world.dni") : Math.max(1, hours) + " h";
        long gained = weekly.gained(p.name(), p.session.character().experience(), wall);
        return (v.mine() > 0 ? v.mine() + Messages.get("hud.miejsce_z") + v.total() + String.format(Messages.get("server_world.d_exp"), gained) : Messages.get("server_world.zdobadz_doswiadczenie_by_wejsc"))
                + Messages.get("server_world.nagrody_za") + left;
    }

    /** Koniec tygodnia: nagrody na konta (offline - przy wejsciu) i ogloszenie podium. */
    private void weeklyRollover(long now) {
        for (ServerPlayer o : players.values()) ranking.put(rankEntry(o.session.character()));
        List<pl.fawizja.pixel.ranking.RankingView.Row> podium = weekly.closeWeek(ranking, wallClock.getAsLong());
        if (podium.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (pl.fawizja.pixel.ranking.RankingView.Row r : podium) {
            long prize = pl.fawizja.pixel.ranking.WeeklyRanking.PRIZES[r.place() - 1];
            ServerPlayer o = find(r.name());
            if (o != null && o.session.character().bank().credit(prize).ok()) {
                o.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.ranking_tygodnia") + r.place() + Messages.get("server_world.miejsce")
                        + String.format("%,d gp", prize) + Messages.get("server_world.na_twoje_konto"));
            } else {
                market.addEarnings(r.name(), prize);
            }
            names.add(r.place() + ". " + r.name() + String.format(Messages.get("server_world.d_exp"), r.level()));
        }
        saveMarket();
        saveWeekly();
        LOG.info("weekly podium: " + names);
        for (ServerPlayer o : players.values()) {
            o.client.send("CHAT", Messages.get("server_world.ogloszenie"), Messages.get("server_world.koniec_tygodnia_najwiecej_dosw") + String.join(", ", names) + Messages.get("server_world.nagrody_trafily_na_konta"), "broadcast");
        }
    }

    private void saveWeekly() {
        try {
            File tmp = new File(weeklyFile.getParentFile(), "weekly.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), weekly.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), weeklyFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("weekly ranking not saved: " + e.getMessage());
        }
    }

    private void saveHouses() {
        try {
            File tmp = new File(housesFile.getParentFile(), "houses.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), houseOwners.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), housesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("houses save failed: " + e.getMessage());
        }
    }

    private void saveLetters() {
        try {
            File tmp = new File(lettersFile.getParentFile(), "letters.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), letters.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), lettersFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("letters save failed: " + e.getMessage());
        }
    }

    /** Listy czekajace na gracza: jako wiadomosci prywatne (klient ukrywa ignorowanych). */
    private void deliverLetters(ServerPlayer p) {
        List<pl.fawizja.pixel.mail.Letters.Letter> got = letters.take(p.name());
        if (got.isEmpty()) return;
        for (pl.fawizja.pixel.mail.Letters.Letter l : got) p.client.send("CHAT", "[list] " + l.from(), l.text(), "letter");
        saveLetters();
    }

    private void saveMail() {
        try {
            File tmp = new File(mailFile.getParentFile(), "mail.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), mail.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), mailFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("mail save failed: " + e.getMessage());
        }
    }

    /** MAIL SEND imie tekst: przesylka z plecaka do depozytu innej postaci (takze offline), tylko przy bankierze. */
    private void mailCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 4 || !f[1].equals("SEND")) return;
        if (!atBanker(p, now)) return;
        String to = knownName(f[2].trim());
        if (to == null) {
            warn(p, Messages.get("server_world.nie_ma_takiej_postaci"), now);
            return;
        }
        if (to.equalsIgnoreCase(p.name())) {
            warn(p, Messages.get("server_world.wlasne_rzeczy_odloz_do"), now);
            return;
        }
        pl.fawizja.pixel.trade.PlayerTrade.Parsed what = pl.fawizja.pixel.trade.PlayerTrade.parse(f[3], items);
        if (what == null || what.itemId() < 0 || pl.fawizja.pixel.economy.Currency.isCoin(what.itemId())) {
            warn(p, Messages.get("server_world.nie_wiem_co_wyslac"), now);
            return;
        }
        PlayerCharacter pc = p.session.character();
        int count = (int) what.count();
        String label = count + "x " + items.get(what.itemId()).name();
        if (pc.backpack().countPlain(what.itemId()) < count) {
            warn(p, Messages.get("server_world.nie_masz_w_plecaku") + label + Messages.get("server_world.ulepszonych_przedmiotow_nie_mo"), now);
            return;
        }
        if (!mail.canAccept(to)) {
            warn(p, to + Messages.get("server_world.ma_za_duzo_nieodebranych"), now);
            return;
        }
        if (!pc.backpack().remove(what.itemId(), count).ok()) return;
        mail.add(new pl.fawizja.pixel.mail.Mailbox.Parcel(to, p.name(), what.itemId(), count));
        ServerPlayer r = find(to);
        if (r != null) deliverMail(r, now);
        saveMail();
        save(p);
        info(p, Messages.get("server_world.wyslano") + label + " do " + to + (r == null ? Messages.get("server_world.odbierze_po_wejsciu_do") : "."), now);
    }

    /** TRANSFER imie kwota: zloto z konta na konto innej postaci (takze offline), tylko przy bankierze. */
    private void transferCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 3) return;
        long amount;
        try {
            amount = Long.parseLong(f[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (amount <= 0) return;
        if (!atBanker(p, now)) return;
        String to = knownName(f[1].trim());
        if (to == null) {
            warn(p, Messages.get("server_world.nie_ma_takiej_postaci"), now);
            return;
        }
        if (to.equalsIgnoreCase(p.name())) {
            warn(p, Messages.get("server_world.nie_mozesz_przelac_zlota"), now);
            return;
        }
        PlayerCharacter pc = p.session.character();
        if (!pc.bank().debit(amount).ok()) {
            warn(p, Messages.get("server_world.na_koncie_masz_tylko") + String.format("%,d gp", pc.bank().balance()) + Messages.get("server_world.wplac_monety_u_bankiera"), now);
            return;
        }
        String label = String.format("%,d gp", amount);
        ServerPlayer r = find(to);
        if (r != null && r.session.character().bank().credit(amount).ok()) {
            Messages.runAs(r.language, () -> r.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.przelew") + p.name() + Messages.get("server_world.przesyla") + label + Messages.get("server_world.na_twoje_konto")));
            save(r);
        } else {
            market.addEarnings(to, amount);
            saveMarket();
        }
        save(p);
        LOG.info("transfer " + p.name() + " -> " + to + " " + amount);
        info(p, Messages.get("server_world.przelano") + label + " do " + to + (r == null ? Messages.get("server_world.wplynie_po_wejsciu_do") : "."), now);
    }

    static final long RENT_SWEEP_MS = 60_000;
    private long rentSweptMs = Long.MIN_VALUE / 2;

    /**
     * Cudze domy, do ktorych gracz ma wstep, i domy z wlascicielem: {"HACC", "id:poziom,id:poziom", "id,id"} (klient
     * od razu wie, gdzie wejdzie, i nad ktorymi drzwiami nie stawiac tabliczki "Na sprzedaż").
     */
    private void sendHouseAccess(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (pl.fawizja.pixel.house.House h : houses.all()) {
            pl.fawizja.pixel.house.HouseAccess a = houseOwners.guestAccess(h.id(), p.name());
            if (a != pl.fawizja.pixel.house.HouseAccess.NONE) out.add(h.id() + ":" + a.code());
        }
        p.client.send("HACC", String.join(",", out), String.join(",", houseOwners.owned().keySet()));
    }

    /**
     * Czynsz domow, ktorych wlasciciele sa offline (wlasciciel w grze placi w swojej sesji): pobranie z konta
     * w zapisie postaci, po {@link pl.fawizja.pixel.house.HouseRent#MAX_MISSED} zaleglosciach dom wraca do miasta,
     * a rzeczy z niego trafiaja do depozytu postaci.
     */
    void sweepHouseRent(long now) {
        long wall = wallClock.getAsLong();
        long period = pl.fawizja.pixel.house.HouseRent.period();
        for (Map.Entry<String, String> e : houseOwners.owned().entrySet()) {
            pl.fawizja.pixel.house.House h = houses.get(e.getKey());
            if (h == null || find(e.getValue()) != null) continue;
            long until = houseOwners.rentPaidUntil(h.id());
            int missed = houseOwners.rentMissed(h.id());
            if (until <= 0) {                                  // dom kupiony przed czynszem: okres rusza teraz
                savedHouseOwners.setRent(h.id(), wall + period, 0);
                continue;
            }
            if (wall < until && missed == 0) continue;
            PlayerCharacter pc;
            try {
                pc = load(e.getValue());
            } catch (IllegalArgumentException ex) {
                continue;                                      // uszkodzony zapis: nie ruszamy domu
            }
            if (pc == null) {                                  // postac usunieta: dom wraca do miasta
                savedHouseOwners.release(h.id());
                continue;
            }
            pl.fawizja.pixel.house.HouseRent.Result r = pl.fawizja.pixel.house.HouseRent.settle(until, missed, wall, period,
                    pl.fawizja.pixel.house.HouseRent.rent(h), pc.bank());
            boolean write = r.charged() > 0;
            if (r.evicted()) {
                pl.fawizja.pixel.house.HouseRent.moveBelongings(h, world.crates(), ground, pc.depot(), pc.bank(), items);
                savedHouseOwners.release(h.id());
                if (h.id().equals(pc.house())) pc.setHouse(null);
                write = true;
                LOG.info("house " + h.id() + " returned to town: rent unpaid by " + e.getValue());
            } else if (r.changed(until, missed)) {
                savedHouseOwners.setRent(h.id(), r.paidUntil(), r.missed());
            }
            if (write) {
                try {
                    saves.write("player_" + e.getValue().toLowerCase(java.util.Locale.ROOT), codec.encode(pc));
                } catch (IOException ex) {
                    LOG.warning("rent: save of " + e.getValue() + " failed: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * HOUSE INFO | GUEST imie | UNGUEST imie | SELL imie cena | ACCEPT | CANCEL: goscie domu i sprzedaz domu
     * innemu graczowi (kupujacy placi z banku, sprzedajacy dostaje zloto na konto, takze gdy jest offline).
     */
    private void houseCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        GameSession s = p.session;
        String own = s.character().house();
        switch (f[1]) {
            case "DOOR": {                                   // okno domu przy drzwiach: stan z serwera
                pl.fawizja.pixel.house.House h = f.length < 3 ? null : houses.get(f[2]);
                if (h != null) p.client.send(s.houseView(h).toMessage());
                break;
            }
            case "BUY": {
                pl.fawizja.pixel.house.House h = f.length < 3 ? null : houses.get(f[2]);
                if (h == null) return;
                if (s.buyHouseAtDoor(h.id(), now)) {
                    save(p);
                    LOG.info("house " + h.id() + " bought by " + p.name());
                }
                p.client.send(s.houseView(h).toMessage());
                break;
            }
            case "LEAVE": {
                pl.fawizja.pixel.house.House h = houses.get(own);
                if (h != null && s.leaveHouseAtDoor(now)) {
                    save(p);
                    LOG.info("house " + h.id() + " left by " + p.name());
                }
                if (h != null) p.client.send(s.houseView(h).toMessage());
                break;
            }
            case "INFO": {
                info(p, s.houseInfo(), now);
                HouseOffer o = houseOffers.get(p.name().toLowerCase(java.util.Locale.ROOT));
                if (o != null && o.until() > now && houses.get(o.houseId()) != null) {
                    info(p, String.format(Messages.get("server_world.s_oferuje_ci_dom"), o.seller(),
                            houses.get(o.houseId()).name(), o.price()), now);
                }
                break;
            }
            case "GUEST": {
                if (f.length < 3) return;
                String name = knownName(f[2].trim());
                if (name == null) {
                    warn(p, Messages.get("server_world.nie_ma_takiej_postaci"), now);
                    return;
                }
                pl.fawizja.pixel.house.HouseAccess level = f.length > 3 && f[3].equalsIgnoreCase("entry")
                        ? pl.fawizja.pixel.house.HouseAccess.ENTRY : pl.fawizja.pixel.house.HouseAccess.FULL;
                if (s.addHouseGuest(name, level, now)) {
                    ServerPlayer g = find(name);
                    if (g != null) g.session.log().add(now, EventLog.Kind.SYSTEM,
                            p.name() + Messages.get("server_world.wpuszcza_cie_do_domu") + houses.get(own).name() + " (" + level.label() + ").");
                }
                if (houses.get(own) != null) p.client.send(s.houseView(houses.get(own)).toMessage());
                break;
            }
            case "UNGUEST": {
                if (f.length < 3) return;
                if (s.removeHouseGuest(f[2], now)) {
                    ServerPlayer g = find(f[2].trim());
                    if (g != null) g.session.checkHouseAccess();
                }
                if (houses.get(own) != null) p.client.send(s.houseView(houses.get(own)).toMessage());
                break;
            }
            case "SELL": {
                if (f.length < 4) return;
                long price;
                try {
                    price = Long.parseLong(f[3]);
                } catch (NumberFormatException e) {
                    return;
                }
                pl.fawizja.pixel.house.House h = houses.get(own);
                ServerPlayer b = find(f[2].trim());
                String msg = null;
                if (h == null || !p.name().equals(houseOwners.ownerOf(h.id()))) msg = Messages.get("server_world.nie_masz_domu");
                else if (price <= 0 || price > 10_000_000_000L) msg = Messages.get("server_world.podaj_cene_np_dom");
                else if (b == null) msg = Messages.get("server_world.kupujacy_musi_byc_w");
                else if (b == p) msg = Messages.get("server_world.nie_sprzedasz_domu_samemu");
                else if (b.session.character().house() != null) msg = b.name() + Messages.get("server_world.ma_juz_dom");
                if (msg != null) {
                    warn(p, msg, now);
                    return;
                }
                houseOffers.put(b.name().toLowerCase(java.util.Locale.ROOT),
                        new HouseOffer(h.id(), p.name(), b.name(), price, now + HOUSE_OFFER_MS));
                String label = String.format("%,d gp", price);
                info(p, Messages.get("server_world.oferta_wyslana") + h.name() + " dla " + b.name() + " za " + label + Messages.get("server_world.wazna_5_minut"), now);
                b.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.oferuje_ci_dom") + h.name() + " (" + h.town()
                        + ", " + h.size() + Messages.get("server_world.pol_za") + label + Messages.get("server_world.z_banku_wpisz_dom"));
                break;
            }
            case "ACCEPT": {
                HouseOffer o = houseOffers.remove(p.name().toLowerCase(java.util.Locale.ROOT));
                pl.fawizja.pixel.house.House h = o == null ? null : houses.get(o.houseId());
                PlayerCharacter pc = s.character();
                String msg = null;
                if (o == null || o.until() < now || h == null) msg = Messages.get("server_world.nikt_nie_oferuje_ci");
                else if (!o.seller().equals(houseOwners.ownerOf(h.id()))) msg = Messages.get("server_world.ten_dom_nie_nalezy") + o.seller() + ".";
                else if (pc.house() != null) msg = Messages.get("server_world.masz_juz_dom_najpierw");
                else if (pc.bank().balance() < o.price()) {
                    msg = String.format(Messages.get("server_world.dom_kosztuje_d_gp"), o.price(), pc.bank().balance());
                }
                if (msg != null) {
                    warn(p, msg, now);
                    return;
                }
                if (!pc.bank().debit(o.price()).ok() || !savedHouseOwners.transfer(h.id(), p.name())) {
                    warn(p, Messages.get("server_world.nie_udalo_sie_kupic"), now);
                    return;
                }
                String label = String.format("%,d gp", o.price());
                ServerPlayer seller = find(o.seller());
                if (seller != null && seller.session.character().bank().credit(o.price()).ok()) {
                    seller.session.setOwnHouse(null);
                    seller.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.kupuje_twoj_dom") + h.name() + ". "
                            + label + Messages.get("server_world.wplywa_na_twoje_konto"));
                    save(seller);
                } else {
                    market.addEarnings(o.seller(), o.price());
                    saveMarket();
                }
                s.setOwnHouse(h.id());
                for (ServerPlayer o2 : players.values()) o2.session.checkHouseAccess();   // dawni goscie wychodza
                save(p);
                LOG.info("house " + h.id() + " sold " + o.seller() + " -> " + p.name() + " " + o.price());
                info(p, Messages.get("server_world.kupiono_dom") + h.name() + " od " + o.seller() + " za " + label + ".", now);
                break;
            }
            case "CANCEL": {
                String me = p.name().toLowerCase(java.util.Locale.ROOT);
                boolean any = houseOffers.remove(me) != null;
                any |= houseOffers.values().removeIf(o -> o.seller().equals(p.name()));
                info(p, any ? Messages.get("server_world.oferta_domu_anulowana") : Messages.get("server_world.nie_masz_ofert_domu"), now);
                break;
            }
            default: return;
        }
    }

    /** BOUNTY: lista nagrod; BOUNTY imie kwota: doplata z konta do nagrody za glowe postaci. */
    /** Szuka druzyny i jeszcze w zadnej nie jest. */
    private boolean seeksParty(ServerPlayer o) {
        return o.lookingForParty && parties.members(o.name()).isEmpty();
    }

    /** /szukam: przelacza ogloszenie; gracze bez druzyny o podobnym poziomie dostaja wiadomosc. */
    private void lookingForParty(ServerPlayer p, long now) {
        if (p.lookingForParty) {
            p.lookingForParty = false;
            info(p, Messages.get("server_world.nie_szukasz_juz_druzyny"), now);
            return;
        }
        if (!parties.members(p.name()).isEmpty()) {
            warn(p, Messages.get("server_world.jestes_juz_w_druzynie"), now);
            return;
        }
        if (!chatAllowed(p, now)) return;
        p.lookingForParty = true;
        PlayerCharacter pc = p.session.character();
        int told = 0;
        for (ServerPlayer o : players.values()) {
            if (o == p || !parties.members(o.name()).isEmpty()) continue;
            int lvl = o.session.character().level();
            if (lvl * 3 < pc.level() * 2 || lvl * 2 > pc.level() * 3) continue; // poziom w granicach 2/3 .. 3/2
            o.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " (" + pc.vocation().displayName() + Messages.get("pixel_game.poziom") + pc.level()
                    + Messages.get("server_world.szuka_druzyny_zapros") + p.name());
            told++;
        }
        info(p, Messages.get("server_world.szukasz_druzyny_widac_to") + told
                + Messages.get("server_world.ponowne_szukam_wylacza"), now);
    }

    /** ", nagroda 5,000 gp" do listy graczy, gdy ktos wyznaczyl nagrode za postac. */
    private String bountyNote(String name) {
        long amount = bounties.amount(name);
        return amount > 0 ? String.format(Messages.get("server_world.nagroda_d_gp"), amount) : "";
    }

    private void bountyCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 3) {
            List<pl.fawizja.pixel.pvp.Bounties.Bounty> top = bounties.top(pl.fawizja.pixel.pvp.Bounties.SHOWN);
            if (top.isEmpty()) {
                info(p, Messages.get("server_world.nie_ma_nagrod_za"), now);
                return;
            }
            StringBuilder sb = new StringBuilder(Messages.get("server_world.nagrody_za_glowy"));
            for (pl.fawizja.pixel.pvp.Bounties.Bounty b : top) sb.append(' ').append(b.name()).append(String.format(" %,d gp;", b.amount()));
            info(p, sb.substring(0, sb.length() - 1), now);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(f[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (amount < pl.fawizja.pixel.pvp.Bounties.MIN) {
            warn(p, String.format(Messages.get("server_world.najmniejsza_nagroda_to_d"), pl.fawizja.pixel.pvp.Bounties.MIN), now);
            return;
        }
        String to = knownName(f[1].trim());
        if (to == null) {
            warn(p, Messages.get("server_world.nie_ma_takiej_postaci"), now);
            return;
        }
        if (to.equalsIgnoreCase(p.name())) {
            warn(p, Messages.get("server_world.nie_mozesz_wyznaczyc_nagrody"), now);
            return;
        }
        PlayerCharacter pc = p.session.character();
        if (!pc.bank().debit(amount).ok()) {
            warn(p, Messages.get("server_world.na_koncie_masz_tylko") + String.format("%,d gp", pc.bank().balance()) + Messages.get("server_world.wplac_monety_u_bankiera"), now);
            return;
        }
        long total = bounties.add(to, amount);
        saveBounties();
        save(p);
        LOG.info("bounty " + p.name() + " -> " + to + " " + amount);
        String msg = Messages.get("server_world.nagroda_za_glowe") + to + String.format(Messages.get("server_world.wynosi_teraz_d_gp"), total) + Messages.get("server_world.dolozyl") + p.name() + ").";
        for (ServerPlayer o : players.values()) o.session.log().add(now, EventLog.Kind.SYSTEM, msg);
    }

    /** Zabojca dostaje nagrode za glowe ofiary (nie ta sama druzyna ani gildia). */
    private void claimBounty(ServerPlayer killer, ServerPlayer victim, long now) {
        if (bounties.amount(victim.name()) <= 0 || sameParty(killer, victim)) return;
        pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(killer.name());
        if (g != null && g == guilds.guildOf(victim.name())) return;
        long amount = bounties.claim(victim.name());
        if (!killer.session.character().bank().credit(amount).ok()) {
            bounties.add(victim.name(), amount);
            return;
        }
        saveBounties();
        save(killer);
        LOG.info("bounty claimed " + killer.name() + " <- " + victim.name() + " " + amount);
        String msg = killer.name() + Messages.get("server_world.odbiera_nagrode_za_glowe") + victim.name() + String.format(Messages.get("server_world.d_gp_na_konto"), amount);
        for (ServerPlayer o : players.values()) o.session.log().add(now, EventLog.Kind.SYSTEM, msg);
    }

    private void saveBounties() {
        try {
            File tmp = new File(bountiesFile.getParentFile(), "bounties.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), bounties.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), bountiesFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("bounties save failed: " + e.getMessage());
        }
    }

    /** Dostarcza czekajace przesylki do depozytu odbiorcy (ile sie zmiesci). */
    private void deliverMail(ServerPlayer r, long now) {
        List<pl.fawizja.pixel.mail.Mailbox.Parcel> parcels = mail.take(r.name());
        if (parcels.isEmpty()) return;
        PlayerCharacter pc = r.session.character();
        int delivered = 0;
        for (pl.fawizja.pixel.mail.Mailbox.Parcel parcel : parcels) {
            if (pc.depot().canAdd(parcel.itemId(), parcel.count())
                    && pc.depot().add(new pl.fawizja.pixel.item.ItemStack(parcel.itemId(), parcel.count())).ok()) {
                delivered++;
                r.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.poczta") + parcel.from() + Messages.get("server_world.przysyla")
                        + parcel.count() + "x " + items.get(parcel.itemId()).name() + Messages.get("server_world.czeka_w_depozycie_u"));
            } else {
                mail.add(parcel);
            }
        }
        if (delivered < parcels.size()) {
            r.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.poczta_depozyt_jest_pelny") + (parcels.size() - delivered)
                    + Messages.get("server_world.przesylek_czeka_zrob_miejsce"));
        }
        if (delivered > 0) save(r);
        saveMail();
    }

    /** Zapis rynku: plik tymczasowy i podmiana. */
    private void saveMarket() {
        try {
            File tmp = new File(marketFile.getParentFile(), "market.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), market.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), marketFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("market save failed: " + e.getMessage());
        }
    }

    private boolean atBanker(ServerPlayer p, long now) {
        pl.fawizja.pixel.npc.Npc n = p.session.npcNearby();
        if (n != null && n.role() == pl.fawizja.pixel.npc.NpcRole.BANKER) return true;
        warn(p, Messages.get("server_world.rynek_obsluguje_bankier_podejd"), now);
        return false;
    }

    /** MARKET LIST [szukaj] | SELL "5 wilcze futro 300" | BUY id | CANCEL id. */
    private void marketCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        PlayerCharacter pc = p.session.character();
        String arg = f.length > 2 ? f[2].trim() : "";
        switch (f[1]) {
            case "LIST":
                sendMarket(p, arg);
                return;
            case "SELL": {
                if (!atBanker(p, now)) return;
                int sp = arg.lastIndexOf(' ');
                String priceText = sp < 0 ? "" : arg.substring(sp + 1).toLowerCase(java.util.Locale.ROOT).replace("gp", "");
                long price;
                try {
                    price = Long.parseLong(priceText);
                } catch (NumberFormatException e) {
                    warn(p, Messages.get("server_world.wpisz_rynek_wystaw_5"), now);
                    return;
                }
                pl.fawizja.pixel.trade.PlayerTrade.Parsed what = pl.fawizja.pixel.trade.PlayerTrade.parse(arg.substring(0, sp), items);
                if (what == null || what.itemId() < 0 || pl.fawizja.pixel.economy.Currency.isCoin(what.itemId())) {
                    warn(p, Messages.get("server_world.nie_wiem_jaki_przedmiot"), now);
                    return;
                }
                int count = (int) what.count();
                // jedna sztuka z ulepszeniem albo tierem Kuzni (najwyzszy tier), gdy brak zwyklych - rynek je zachowuje
                int special = -1;
                if (count == 1 && pc.backpack().countPlain(what.itemId()) < 1) {
                    for (int i = 0; i < pc.backpack().size(); i++) {
                        pl.fawizja.pixel.item.ItemStack st = pc.backpack().get(i);
                        if (st == null || st.itemId() != what.itemId() || st.isPlain() || !st.imbuements().isEmpty()) continue;
                        if (special < 0 || st.tier() > pc.backpack().get(special).tier()) special = i;
                    }
                }
                if (special < 0 && pc.backpack().countPlain(what.itemId()) < count) {
                    warn(p, Messages.get("server_world.nie_masz_w_plecaku") + count + "x " + items.get(what.itemId()).name()
                            + Messages.get("server_world.przedmiotow_z_nasyceniem_nie"), now);
                    return;
                }
                pl.fawizja.pixel.market.Market.Result r = market.canList(p.name(), price);
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                pl.fawizja.pixel.item.ItemStack listed;
                if (special >= 0) {
                    listed = pc.backpack().takeFromSlot(special, 1);
                    if (listed == null) return;
                } else {
                    if (!pc.backpack().remove(what.itemId(), count).ok()) return;
                    listed = new pl.fawizja.pixel.item.ItemStack(what.itemId(), count);
                }
                pl.fawizja.pixel.market.Market.Listing l = market.add(p.name(), listed, price);
                saveMarket();
                save(p);
                info(p, Messages.get("server_world.wystawiono_na_rynku") + count + "x " + listingName(l) + " za "
                        + String.format("%,d gp", price) + Messages.get("server_world.oferta") + l.id() + ").", now);
                break;
            }
            case "BUY": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.Market.Result r = market.checkBuy(id, p.name());
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message(), now);
                    break;
                }
                pl.fawizja.pixel.market.Market.Listing l = market.get(id);
                if (!pc.backpack().canAdd(l.itemId(), l.count())) {
                    warn(p, Messages.get("server_world.brak_miejsca_w_plecaku"), now);
                    return;
                }
                // najpierw konto, potem monety z plecaka
                boolean paid = pc.bank().debit(l.price()).ok()
                        || pl.fawizja.pixel.economy.Money.pay(pc.backpack(), l.price()).ok();
                if (!paid) {
                    warn(p, Messages.get("server_world.za_malo_zlota_oferta") + String.format("%,d gp", l.price()) + Messages.get("server_world.konto_albo_plecak"), now);
                    return;
                }
                market.sold(id);
                if (l.upgrade() == 0 && l.tier() == 0) market.recordSale(l.itemId(), l.count(), l.price());
                pc.backpack().add(l.stack());
                long earned = l.price() - pl.fawizja.pixel.market.Market.fee(l.price());
                String what = l.count() + "x " + listingName(l);
                ServerPlayer seller = find(l.seller());
                if (seller != null && seller.session.character().bank().credit(earned).ok()) {
                    seller.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.rynek") + p.name() + Messages.get("server_world.kupil") + what
                            + Messages.get("server_world.na_konto_wplynelo") + String.format("%,d gp", earned) + ".");
                    save(seller);
                } else {
                    market.addEarnings(l.seller(), earned);
                }
                saveMarket();
                save(p);
                info(p, Messages.get("server_world.kupiono") + what + " za " + String.format("%,d gp", l.price()) + ".", now);
                break;
            }
            case "CANCEL": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.Market.Result r = market.checkCancel(id, p.name());
                if (r != pl.fawizja.pixel.market.Market.Result.OK) {
                    warn(p, r.message(), now);
                    break;
                }
                pl.fawizja.pixel.market.Market.Listing l = market.get(id);
                if (!pc.backpack().canAdd(l.itemId(), l.count())) {
                    warn(p, Messages.get("server_world.brak_miejsca_w_plecaku"), now);
                    return;
                }
                market.cancel(id);
                pc.backpack().add(l.stack());
                saveMarket();
                save(p);
                info(p, Messages.get("server_world.wycofano_oferte") + l.count() + "x " + listingName(l) + Messages.get("server_world.wraca_do_plecaka"), now);
                break;
            }
            default:
                return;
        }
        sendMarket(p, "");
    }

    /** Ceny z rynku graczy dla /cena: ostatnia sprzedaz, najtansza oferta i najwyzsze zamowienie kupna (za sztuke). */
    private void marketPrice(ServerPlayer p, String key, long now) {
        if (!items.containsKey(key)) return;
        pl.fawizja.pixel.item.ItemType t = items.byKey(key);
        long last = market.lastUnitPrice(t.id());
        pl.fawizja.pixel.market.Market.Listing cheap = market.cheapest(t.id());
        long bid = -1;
        for (pl.fawizja.pixel.market.BuyOrders.Order o : orders.newestFirst()) {
            if (o.itemId() == t.id()) bid = Math.max(bid, Math.max(1, o.price() / o.count()));
        }
        info(p, Messages.get("server_world.rynek_graczy") + t.name() + Messages.get("server_world.ostatnia_sprzedaz") + (last < 0 ? Messages.get("server_world.brak") : String.format(Messages.get("server_world.d_gp_szt"), last))
                + Messages.get("server_world.najtansza_oferta") + (cheap == null ? Messages.get("server_world.brak") : String.format(Messages.get("server_world.d_gp_szt_d"), Math.max(1, cheap.price() / cheap.count()), cheap.id()))
                + Messages.get("server_world.kupujacy_placa") + (bid < 0 ? Messages.get("server_world.brak_zamowien") : String.format(Messages.get("server_world.d_gp_szt"), bid)) + ".", now);
    }

    /** Zamowienia kupna: LIST, ADD "ilosc przedmiot cena", FILL id, CANCEL id (trzy ostatnie u bankiera). */
    private void wantCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        PlayerCharacter pc = p.session.character();
        String arg = f.length > 2 ? f[2].trim() : "";
        switch (f[1]) {
            case "LIST":
                break;
            case "ADD": {
                if (!atBanker(p, now)) return;
                int sp = arg.lastIndexOf(' ');
                long price = sp < 0 ? -1 : pl.fawizja.pixel.net.ChatCommands.amount(arg.substring(sp + 1));
                pl.fawizja.pixel.trade.PlayerTrade.Parsed what = price <= 0 ? null
                        : pl.fawizja.pixel.trade.PlayerTrade.parse(arg.substring(0, sp), items);
                if (what == null || what.itemId() < 0 || pl.fawizja.pixel.economy.Currency.isCoin(what.itemId())) {
                    warn(p, Messages.get("server_world.wpisz_kupie_20_wilcze"), now);
                    return;
                }
                int count = (int) Math.min(Integer.MAX_VALUE, what.count());
                pl.fawizja.pixel.market.BuyOrders.Result r = orders.canPlace(p.name(), count, price);
                if (r != pl.fawizja.pixel.market.BuyOrders.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                if (!pc.bank().debit(price).ok()) {
                    warn(p, Messages.get("server_world.na_koncie_brakuje_zlota") + String.format("%,d gp", price) + Messages.get("server_world.do_realizacji"), now);
                    return;
                }
                pl.fawizja.pixel.market.BuyOrders.Order o = orders.place(p.name(), what.itemId(), count, price, wallClock.getAsLong());
                saveOrders();
                save(p);
                info(p, Messages.get("server_world.zamowienie") + o.id() + Messages.get("server_world.kupisz") + count + "x " + items.get(o.itemId()).name() + " za "
                        + String.format("%,d gp", price) + Messages.get("server_world.zloto_czeka_zablokowane_przedm"), now);
                break;
            }
            case "FILL": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.BuyOrders.Result r = orders.checkFill(id, p.name());
                if (r != pl.fawizja.pixel.market.BuyOrders.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                pl.fawizja.pixel.market.BuyOrders.Order o = orders.get(id);
                String what = o.count() + "x " + items.get(o.itemId()).name();
                if (pc.backpack().countPlain(o.itemId()) < o.count()) {
                    warn(p, Messages.get("server_world.zamowienie_wymaga") + what + Messages.get("server_world.w_plecaku_bez_ulepszen"), now);
                    return;
                }
                if (!mail.canAccept(o.buyer())) {
                    warn(p, Messages.get("server_world.skrzynka_zamawiajacego_jest_pe"), now);
                    return;
                }
                if (!pc.backpack().remove(o.itemId(), o.count()).ok() || !pc.bank().credit(o.price()).ok()) return;
                orders.remove(id);
                market.recordSale(o.itemId(), o.count(), o.price());
                saveMarket();
                mail.add(new pl.fawizja.pixel.mail.Mailbox.Parcel(o.buyer(), p.name(), o.itemId(), o.count()));
                saveOrders();
                saveMail();
                save(p);
                info(p, Messages.get("server_world.zrealizowano_zamowienie") + id + ": " + what + " dla " + o.buyer() + Messages.get("server_world.na_konto_wplynelo")
                        + String.format("%,d gp", o.price()) + ".", now);
                ServerPlayer buyer = find(o.buyer());
                if (buyer != null) deliverMail(buyer, now);
                break;
            }
            case "CANCEL": {
                if (!atBanker(p, now)) return;
                int id = parseInt(arg);
                pl.fawizja.pixel.market.BuyOrders.Result r = orders.checkCancel(id, p.name());
                if (r != pl.fawizja.pixel.market.BuyOrders.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                pl.fawizja.pixel.market.BuyOrders.Order o = orders.remove(id);
                pc.bank().credit(o.price());
                saveOrders();
                save(p);
                info(p, Messages.get("server_world.wycofano_zamowienie") + id + ". " + String.format("%,d gp", o.price()) + Messages.get("server_world.wraca_na_konto"), now);
                break;
            }
            default:
                return;
        }
        sendOrders(p);
    }

    /** Okno zamowien: wlasne, potem najnowsze cudze (wiersze "etykieta;opis"). */
    private void sendOrders(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        out.add("WANT");
        for (pl.fawizja.pixel.market.BuyOrders.Order o : orders.byBuyer(p.name())) {
            out.add("#" + o.id() + " " + o.count() + "x " + items.get(o.itemId()).name() + ";"
                    + String.format("%,d gp", o.price()) + Messages.get("server_world.twoje_kupie_anuluj") + o.id() + ")");
        }
        int shown = 0;
        for (pl.fawizja.pixel.market.BuyOrders.Order o : orders.newestFirst()) {
            if (o.buyer().equalsIgnoreCase(p.name())) continue;
            if (shown++ >= pl.fawizja.pixel.market.BuyOrders.SHOWN) break;
            out.add("#" + o.id() + " " + o.count() + "x " + items.get(o.itemId()).name() + ";"
                    + String.format("%,d gp", o.price()) + ", " + o.buyer() + Messages.get("server_world.sprzedaj") + o.id() + ")");
        }
        p.client.send(out.toArray(new String[0]));
    }

    private long ordersCheckedMs = -60_000;

    /** Wygasle zamowienia kupna: zloto wraca na konto (offline - przy nastepnym wejsciu, jak zarobek z rynku). */
    void expireOrders(long now) {
        ordersCheckedMs = now;
        List<pl.fawizja.pixel.market.BuyOrders.Order> gone = orders.expire(wallClock.getAsLong());
        if (gone.isEmpty()) return;
        for (pl.fawizja.pixel.market.BuyOrders.Order o : gone) {
            ServerPlayer b = find(o.buyer());
            if (b != null && b.session.character().bank().credit(o.price()).ok()) {
                b.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.zamowienie") + o.id() + " (" + o.count() + "x "
                        + items.get(o.itemId()).name() + Messages.get("server_world.wygaslo_po_7_dniach") + String.format("%,d gp", o.price()) + Messages.get("server_world.wraca_na_konto"));
                save(b);
            } else {
                market.addEarnings(o.buyer(), o.price());
            }
        }
        saveOrders();
        saveMarket();
    }

    private void saveOrders() {
        try {
            File tmp = new File(ordersFile.getParentFile(), "buyorders.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), orders.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), ordersFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("buy orders save failed: " + e.getMessage());
        }
    }

    /** Okno rynku: najpierw wlasne oferty, potem najnowsze cudze (opcjonalnie tylko pasujace do nazwy). */
    private void sendMarket(ServerPlayer p, String search) {
        String q = search == null ? "" : plainLower(search.trim());
        java.util.List<pl.fawizja.pixel.market.MarketView.Row> rows = new java.util.ArrayList<>();
        for (pl.fawizja.pixel.market.Market.Listing l : market.bySeller(p.name())) rows.add(marketRow(l, true));
        int others = 0;
        for (pl.fawizja.pixel.market.Market.Listing l : market.newestFirst()) {
            if (l.seller().equalsIgnoreCase(p.name())) continue;
            if (!q.isEmpty() && !plainLower(items.get(l.itemId()).name()).contains(q) && !items.get(l.itemId()).key().contains(q.replace(' ', '_'))) continue;
            if (others++ >= 40) break;
            rows.add(marketRow(l, false));
        }
        String[] f = new pl.fawizja.pixel.market.MarketView(market.size(), q.isEmpty() ? null : search.trim(), rows).encode();
        String[] msg = new String[f.length + 1];
        msg[0] = "MARKET";
        System.arraycopy(f, 0, msg, 1, f.length);
        p.client.send(msg);
    }

    private pl.fawizja.pixel.market.MarketView.Row marketRow(pl.fawizja.pixel.market.Market.Listing l, boolean mine) {
        return new pl.fawizja.pixel.market.MarketView.Row(l.id(), listingName(l), l.count(), l.price(), l.seller(), mine);
    }

    /** Nazwa oferty z ulepszeniem i tierem Kuzni: "Miecz +2 (T3)". */
    private String listingName(pl.fawizja.pixel.market.Market.Listing l) {
        return pl.fawizja.pixel.item.Forge.name(items.get(l.itemId()), l.stack());
    }

    private static String plainLower(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String guildTag(String name) {
        pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(name);
        return g == null ? "-" : g.name();
    }

    /** Zapis gildii: plik tymczasowy i podmiana (bez polowicznego zapisu). */
    private void saveGuilds() {
        try {
            File tmp = new File(guildFile.getParentFile(), "guilds.txt.tmp");
            java.nio.file.Files.write(tmp.toPath(), guilds.encode().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(tmp.toPath(), guildFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warning("guild save failed: " + e.getMessage());
        }
    }

    /** Wiadomosc do zalogowanych czlonkow gildii. */
    private void toGuild(pl.fawizja.pixel.guild.GuildRegistry.Guild g, String text, long now) {
        for (String n : g.members()) {
            ServerPlayer m = find(n);
            if (m != null) m.session.log().add(now, EventLog.Kind.CHAT, "[Gildia] " + text);
        }
    }

    /** GUILD CREATE nazwa | INVITE imie | ACCEPT nazwa | LEAVE | KICK imie | INFO. */
    private void guildCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        pl.fawizja.pixel.guild.GuildRegistry.Result r;
        pl.fawizja.pixel.guild.GuildRegistry.Guild mine = guilds.guildOf(p.name());
        switch (f[1]) {
            case "CREATE": {
                if (f.length < 3) return;
                r = guilds.canCreate(p.name(), f[2]);
                if (r == pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    if (!pl.fawizja.pixel.economy.Money.pay(p.session.character().backpack(),
                            pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST).ok()) {
                        warn(p, Messages.get("server_world.zalozenie_gildii_kosztuje") + String.format("%,d gp", pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST)
                                + Messages.get("server_world.monety_w_plecaku"), now);
                        return;
                    }
                    r = guilds.create(p.name(), f[2]);
                }
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                saveGuilds();
                save(p);
                Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.zalozono_gildie") + f[2].trim() + Messages.get("server_world.zapraszaj_gildia_zapros_imie")));
                LOG.info("guild created " + f[2].trim() + " by " + p.name());
                return;
            }
            case "INVITE": {
                if (f.length < 3) return;
                ServerPlayer other = find(f[2]);
                if (other == null) {
                    warn(p, Messages.get("server_world.gracz") + f[2] + Messages.get("server_world.nie_jest_w_grze_2"), now);
                    return;
                }
                r = guilds.invite(p.name(), other.name(), now);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                info(p, Messages.get("server_world.zaproszono") + other.name() + Messages.get("server_world.do_gildii") + mine.name() + ".", now);
                other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.zaprasza_cie_do_gildii") + mine.name()
                        + Messages.get("server_world.gildia_przyjmij") + mine.name());
                return;
            }
            case "ACCEPT": {
                if (f.length < 3) return;
                r = guilds.accept(p.name(), f[2], now);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                saveGuilds();
                toGuild(guilds.guildOf(p.name()), p.name() + Messages.get("server_world.dolacza_do_gildii"), now);
                return;
            }
            case "LEAVE": {
                if (mine == null) {
                    warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message(), now);
                    return;
                }
                guilds.leave(p.name());
                saveGuilds();
                info(p, Messages.get("server_world.opusciles_gildie") + mine.name() + ".", now);
                if (!mine.members().isEmpty()) toGuild(mine, p.name() + Messages.get("server_world.opuszcza_gildie_lider") + mine.leader(), now);
                return;
            }
            case "KICK": {
                if (f.length < 3) return;
                pl.fawizja.pixel.guild.GuildRegistry.Guild g = mine;
                String target = f[2].trim();
                r = guilds.kick(p.name(), target);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                saveGuilds();
                toGuild(g, target + Messages.get("server_world.zostaje_usuniety_z_gildii"), now);
                ServerPlayer t = find(target);
                if (t != null) warn(t, Messages.get("server_world.usunieto_cie_z_gildii") + g.name() + ".", now);
                return;
            }
            case "MOTD": {
                r = guilds.setMotd(p.name(), f.length > 2 ? f[2] : "");
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r.message(), now);
                    return;
                }
                saveGuilds();
                toGuild(mine, mine.motd() == null ? Messages.get("server_world.lider_usunal_wiadomosc_gildii") : Messages.get("server_world.wiadomosc_gildii") + mine.motd(), now);
                return;
            }
            case "DEPOSIT":
            case "WITHDRAW": {
                long amount;
                try {
                    amount = f.length > 2 ? Long.parseLong(f[2].trim()) : 0;
                } catch (NumberFormatException e) {
                    return;
                }
                PlayerCharacter pc = p.session.character();
                boolean in = f[1].equals("DEPOSIT");
                if (in) {
                    if (mine == null) {
                        warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message(), now);
                        return;
                    }
                    if (amount <= 0 || !pc.bank().debit(amount).ok()) {
                        warn(p, Messages.get("server_world.na_koncie_masz_tylko") + String.format("%,d gp", pc.bank().balance()) + Messages.get("server_world.wplac_monety_u_bankiera"), now);
                        return;
                    }
                    r = guilds.deposit(p.name(), amount);
                    if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                        pc.bank().credit(amount);
                        warn(p, r.message(), now);
                        return;
                    }
                } else {
                    r = guilds.withdraw(p.name(), amount);
                    if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                        warn(p, r.message(), now);
                        return;
                    }
                    if (!pc.bank().credit(amount).ok()) {
                        guilds.deposit(p.name(), amount);
                        warn(p, Messages.get("server_world.konto_jest_pelne"), now);
                        return;
                    }
                }
                saveGuilds();
                save(p);
                LOG.info("guild " + (in ? "deposit " : "withdraw ") + p.name() + " " + mine.name() + " " + amount);
                toGuild(mine, p.name() + (in ? Messages.get("server_world.wplaca") : Messages.get("server_world.wyplaca")) + String.format("%,d gp", amount)
                        + (in ? Messages.get("server_world.do_skarbca") : Messages.get("server_world.ze_skarbca")) + String.format(Messages.get("server_world.w_skarbcu_d_gp"), mine.treasury()), now);
                return;
            }
            case "BANNER": {
                if (mine == null) {
                    warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message(), now);
                    return;
                }
                r = guilds.withdraw(p.name(), BANNER_COST);
                if (r != pl.fawizja.pixel.guild.GuildRegistry.Result.OK) {
                    warn(p, r == pl.fawizja.pixel.guild.GuildRegistry.Result.TREASURY_LOW
                            ? Messages.get("server_world.sztandar_kosztuje") + String.format("%,d gp", BANNER_COST) + Messages.get("server_world.ze_skarbca_gildii") : r.message(), now);
                    return;
                }
                String gk = mine.name().toLowerCase(java.util.Locale.ROOT);
                long until = Math.max(now, guildBanners.getOrDefault(gk, now)) + BANNER_MS;
                guildBanners.put(gk, until);
                saveGuilds();
                LOG.info("guild banner " + mine.name() + " by " + p.name());
                toGuild(mine, p.name() + Messages.get("server_world.wznosi_sztandar_gildii") + BANNER_PERCENT + Messages.get("game_session.doswiadczenia_za_potwory_przez")
                        + (until - now) / 60_000 + Messages.get("hud.min") + String.format(Messages.get("server_world.w_skarbcu_d_gp"), mine.treasury()), now);
                updateExpEvent(now);
                return;
            }
            case "MEMBERS": {
                if (mine == null) {
                    warn(p, Messages.get("server_world.nie_nalezysz_do_gildii"), now);
                    return;
                }
                for (String line : guildMemberLines(mine)) info(p, line, now);
                return;
            }
            default: // INFO
                if (mine == null) {
                    info(p, Messages.get("server_world.nie_nalezysz_do_gildii_2") + String.format("%,d gp",
                            pl.fawizja.pixel.guild.GuildRegistry.FOUNDING_COST) + ")", now);
                    return;
                }
                List<String> online = new ArrayList<>();
                for (String n : mine.members()) if (find(n) != null) online.add(n);
                info(p, Messages.get("server_world.gildia_2") + mine.name() + Messages.get("server_world.lider") + mine.leader() + Messages.get("server_world.czlonkow") + mine.members().size()
                        + Messages.get("server_world.w_grze_4") + String.join(", ", online) + String.format(Messages.get("server_world.skarbiec_d_gp"), mine.treasury()), now);
                if (mine.motd() != null) info(p, Messages.get("server_world.wiadomosc_gildii") + mine.motd(), now);
                long banner = bannerUntil(p.name(), now);
                info(p, banner > now ? Messages.get("server_world.sztandar_gildii_2") + BANNER_PERCENT + Messages.get("server_world.exp_jeszcze") + Math.max(1, (banner - now + 59_999) / 60_000) + Messages.get("server_world.min_2")
                        : Messages.get("server_world.sztandar_gildii_lider_gildia") + BANNER_PERCENT + Messages.get("server_world.exp_na_godzine_za")
                        + String.format("%,d gp", BANNER_COST) + Messages.get("server_world.ze_skarbca_2"), now);
        }
    }

    Collection<ServerPlayer> players() { return Collections.unmodifiableCollection(players.values()); }

    /**
     * Druzyna dla harmonii mnicha (Canary hasVisiblePartyMember / healFromHarmony): czlonkowie druzyny na tym samym
     * pietrze w zasiegu ekranu (8 x 6 pol) i inni gracze na 8 polach wokol (tlum odbierajacy spokoj).
     */
    private pl.fawizja.pixel.magic.SpellSystem.Allies monkAllies(ServerPlayer p) {
        return new pl.fawizja.pixel.magic.SpellSystem.Allies() {
            @Override
            public List<pl.fawizja.pixel.magic.SpellSystem.Ally> visible() {
                List<pl.fawizja.pixel.magic.SpellSystem.Ally> out = new ArrayList<>();
                Position me = p.session.character().position();
                for (String name : parties.members(p.name())) {
                    ServerPlayer o = find(name);
                    if (o == null || o == p || o.session.character().isDead()) continue;
                    Position op = o.session.character().position();
                    if (op.z() != me.z() || Math.abs(op.x() - me.x()) > 8 || Math.abs(op.y() - me.y()) > 6) continue;
                    pl.fawizja.pixel.character.PlayerCharacter c = o.session.character();
                    out.add(new pl.fawizja.pixel.magic.SpellSystem.Ally() {
                        @Override public Position position() { return c.position(); }
                        @Override public int health() { return c.health(); }
                        @Override public int heal(int amount) {
                            int h = c.heal(amount);
                            if (h > 0) o.session.combat().addEvent(new pl.fawizja.pixel.combat.CombatEvent(
                                    o.session.lastUpdateMs(), c.position(), h, pl.fawizja.pixel.combat.CombatEvent.Kind.HEAL));
                            return h;
                        }
                    });
                }
                return out;
            }

            @Override
            public int playersAround() {
                Position me = p.session.character().position();
                int n = 0;
                for (ServerPlayer o : players.values()) {
                    if (o == p || o.session.character().isDead()) continue;
                    Position op = o.session.character().position();
                    if (!op.equals(me) && me.distanceTo(op) <= 1) n++;
                }
                return n;
            }
        };
    }

    ServerPlayer find(String name) {
        for (ServerPlayer p : players.values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Logowanie: wczytuje zapis postaci o tym imieniu albo tworzy nowa. Zwraca null z komunikatem bledu w client. */
    ServerPlayer join(ClientHandler client, String name, String vocation, String password, long now) {
        return join(client, name, vocation, password, null, now);
    }

    /**
     * Jak wyzej, z miastem startowym nowej postaci (szoste pole HELLO; null/pusty/"-" = miasto domyslne
     * start.town). Nieznane miasto przy zakladaniu konta = odmowa; dla istniejacej postaci pole jest pomijane.
     */
    ServerPlayer join(ClientHandler client, String name, String vocation, String password, String town, long now) {
        return join(client, name, vocation, password, town, pl.fawizja.pixel.i18n.Language.PL, now);
    }

    /** Jak wyzej, z jezykiem klienta (siodme pole HELLO): wiadomosci dla tego gracza powstaja w jego jezyku. */
    ServerPlayer join(ClientHandler client, String name, String vocation, String password, String town,
            pl.fawizja.pixel.i18n.Language language, long now) {
        if (shutdownDone) {
            client.send("ERR", Messages.get("server_world.serwer_jest_wylaczany_sprobuj"));
            return null;
        }
        try {
            PlayerCharacter.validateName(name);
        } catch (IllegalArgumentException e) {
            client.send("ERR", Messages.get("vocation_select_screen.niepoprawne_imie_3_20"));
            return null;
        }
        boolean newAccount = !saved(name) && !accounts.exists(name);
        if (newAccount) {
            try {
                Vocation.valueOf(vocation);
            } catch (IllegalArgumentException e) {
                client.send("ERR", Messages.get("server_world.nieznana_profesja"));
                return null;
            }
            if (town != null && !town.isBlank() && !town.equals("-")
                    && !pl.fawizja.pixel.character.StartingTowns.isValid(world, town)) {
                client.send("ERR", Messages.get("server_world.nieznane_miasto_startowe") + town + ".");
                return null;
            }
            java.util.ArrayDeque<Long> made = newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>());
            while (!made.isEmpty() && now - made.peekFirst() > NEW_ACCOUNT_WINDOW_MS) made.pollFirst();
            if (made.size() >= maxNewAccountsPerAddress) {
                client.send("ERR", Messages.get("server_world.za_duzo_nowych_kont"));
                return null;
            }
        }
        switch (accounts.login(name, password, now)) {
            case WRONG_PASSWORD:
                client.send("ERR", Messages.get("server_world.zle_haslo"));
                return null;
            case LOCKED:
                client.send("ERR", Messages.get("server_world.za_duzo_blednych_prob"));
                return null;
            case BAD_PASSWORD:
                client.send("ERR", Messages.get("server_world.haslo_musi_miec") + AccountStore.MIN_PASSWORD + "-" + AccountStore.MAX_PASSWORD + Messages.get("server_world.znakow"));
                return null;
            case CREATED:
                LOG.info("new account " + name);
                newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>()).addLast(now);
                break;
            default:
        }
        Long banned = moderation.bannedUntil(name, wallClock.getAsLong());
        if (banned != null) {
            client.send("ERR", Messages.get("server_world.konto_zablokowane") + Moderation.describeUntil(banned) + ".");
            return null;
        }
        ServerPlayer already = find(name);
        if (already != null) {
            if (already.lingering) {
                long left = (already.pvp.logoutBlockedMs(now) + 999) / 1000;
                client.send("ERR", Messages.get("server_world.postac") + name + Messages.get("server_world.jest_jeszcze_w_swiecie")
                        + (left >= 60 ? left / 60 + Messages.get("server_world.min_3") + left % 60 + " s" : left + " s") + ").");
            } else {
                client.send("ERR", Messages.get("server_world.postac") + name + Messages.get("server_world.jest_juz_w_grze"));
            }
            return null;
        }
        PlayerCharacter pc;
        try {
            pc = load(name);
            if (pc == null) {
                pc = new CharacterFactory(config, items).createIn(world, name, Vocation.valueOf(vocation),
                        town, config.startLevel());
            } else {
                pc.stamina().onLogin(wallClock.getAsLong()); // stamina odnowiona za czas offline (zasady Tibii)
            }
        } catch (IllegalArgumentException e) {
            client.send("ERR", Messages.get("server_world.nieprawidlowe_dane_postaci") + e.getMessage());
            return null;
        }
        pc.setPosition(freeSpotNear(pc.position()));
        GameSession s = new GameSession(items, world, pc, monsters, ground, new Random(rng.nextLong()),
                config.startLevel(), spells, consumables, recipes);
        s.setTownServices(npcs, shops, quests);
        s.setWallClock(() -> wallClock.getAsLong());
        s.setHouses(houses, savedHouseOwners);
        s.setChests(chests);
        ServerPlayer p = new ServerPlayer(nextId++, s, client, now,
                pvp.computeIfAbsent(pc.name().toLowerCase(java.util.Locale.ROOT), k -> loadPvp(k, now)));
        p.language = language;
        if (p.pvp.fightUntil() > now) s.lockProtectionZonesUntil(p.pvp.fightUntil());
        s.setOtherPlayers(pos -> {
            for (ServerPlayer o : players.values()) {
                if (o != p && !o.session.character().isDead() && pos.equals(o.session.character().position())) return true;
            }
            return false;
        });
        s.setAltarParty(altarParty(p));
        s.setBossQuests(bossQuests, this::bossHeroes);
        s.combat().setLootOwner(bossQuests::ownsLoot);
        Position evict = bossQuests.evictTarget(s.bossHero());
        if (evict != null) {
            // wylogowanie na arenie: walka sie skonczyla albo to inna druzyna - powrot do przedsionka
            s.teleport(freeSpotNear(evict), now);
            s.log().add(now, EventLog.Kind.INFO, Messages.get("server_world.arena_jest_zamknieta_wracasz"));
        }
        s.setPvpStatus(p.pvp);
        // czary i runy na graczy: cel-gracz i (obszar) wszyscy gracze w zasiegu - zasady w PvpRules.check
        s.spells().setPlayerTargeting(new pl.fawizja.pixel.pvp.PvpTargeting(s,
                () -> {
                    ServerPlayer t = p.pvpTarget < 0 ? null : players.get(p.pvpTarget);
                    return t == null ? null : t.session;
                },
                () -> {
                    List<GameSession> near = new ArrayList<>();
                    for (ServerPlayer o : players.values()) if (o != p) near.add(o.session);
                    return near;
                },
                (me, other) -> {
                    ServerPlayer o = bySession(other);
                    return o != null && sameParty(p, o);
                },
                world, pvpRng,
                (victim, o, t) -> {
                    ServerPlayer v = bySession(victim);
                    if (v == null) return;
                    if (o == PvpRules.Outcome.KILLED) {
                        if (p.pvpTarget == v.id) p.pvpTarget = -1;
                        claimBounty(p, v, t);
                    }
                    if (o == PvpRules.Outcome.DUEL_WON) duelOver(p, v, t);
                }));
        s.spells().setAllies(monkAllies(p));
        s.combat().setExperienceSharer((type, t) -> shareKill(p, type, t));
        s.combat().setDeathListener(m -> {
            invasionKill(p, m);
            bossQuestKill(m);
        });
        s.combat().setRareDropListener((type, item, t) -> {
            String what = items.get(item.itemId()).name() + " (" + type.name() + ")";
            for (ServerPlayer o : players.values()) {
                if (o != p) o.session.log().add(t, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.zdobywa_rzadki_lup") + what + "!");
            }
        });
        players.put(p.id, p);
        client.send("WELCOME", String.valueOf(p.id), String.valueOf(PixelServer.TICK_MS));
        sendCrates(client);
        sendHouseAccess(p);
        String wake = pl.fawizja.pixel.house.BedRest.wakeUp(pc, wallClock.getAsLong()); // sen w lozku: zycie i mana
        if (wake != null) s.log().add(lastNow, EventLog.Kind.SYSTEM, wake);
        broadcastInfo(p, () -> p.name() + Messages.get("server_world.wchodzi_do_gry"));
        tellFriends(p, Messages.get("server_world.jest_teraz_w_grze"), lastNow);
        List<String> on = new ArrayList<>();
        for (String n : pc.friends().names()) if (find(n) != null) on.add(find(n).name());
        if (!on.isEmpty()) s.log().add(lastNow, EventLog.Kind.SYSTEM, Messages.get("server_world.znajomi_w_grze") + String.join(", ", on));
        if (!motd.isEmpty()) s.log().add(lastNow, EventLog.Kind.SYSTEM, Messages.get("server_world.wiadomosc_dnia") + motd);
        if (expEventPercent > 0) s.log().add(lastNow, EventLog.Kind.SYSTEM, expEventLine(lastNow));
        pl.fawizja.pixel.guild.GuildRegistry.Guild guild = guilds.guildOf(pc.name());
        if (guild != null && guild.motd() != null) {
            s.log().add(lastNow, EventLog.Kind.SYSTEM, Messages.get("server_world.wiadomosc_gildii_2") + guild.name() + ": " + guild.motd());
        }
        if (invasion != null) {
            s.log().add(lastNow, EventLog.Kind.SYSTEM, Messages.get("server_world.trwa_inwazja") + invasion.plan().name() + Messages.get("server_world.atakuja")
                    + invasion.plan().town() + Messages.get("server_world.zostalo") + invasion.remaining() + Messages.get("server_world.wrogow"));
        }
        deliverMail(p, lastNow);
        deliverLetters(p);
        long earned = market.takeEarnings(pc.name());
        if (earned > 0) {
            if (pc.bank().credit(earned).ok()) {
                s.log().add(lastNow, EventLog.Kind.SYSTEM, Messages.get("server_world.pod_twoja_nieobecnosc_na")
                        + String.format("%,d gp", earned) + Messages.get("server_world.rynek_i_przelewy"));
                save(p);
            } else {
                market.addEarnings(pc.name(), earned);
            }
            saveMarket();
        }
        LOG.info("join " + pc.name() + " (" + players.size() + " online)");
        return p;
    }

    /**
     * Doswiadczenie za potwora zabitego przez czlonka druzyny, jak w Tibii ({@link pl.fawizja.pixel.party.PartyRules}):
     * dzielone tylko, gdy wszyscy czlonkowie sa do 30 pol od lidera (pietro +-1), aktywni w ostatnich 2 min
     * i maja zgodne poziomy (2/3). Kazdy dostaje ceil(M x (1 + premia) / P) x C. false = brak warunkow
     * (zabojca dostaje doswiadczenie sam).
     */
    boolean shareKill(ServerPlayer killer, pl.fawizja.pixel.monster.MonsterType type, long now) {
        List<String> names = parties.members(killer.name());
        if (names.size() < 2) return false;
        ServerPlayer leader = find(names.get(0));
        if (leader == null) return false;
        Position at = leader.session.character().position();
        List<ServerPlayer> all = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        List<pl.fawizja.pixel.vocation.Vocation> vocations = new ArrayList<>();
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m == null) return false;
            PlayerCharacter c = m.session.character();
            if (!pl.fawizja.pixel.party.PartyRules.inShareRange(at, c.position())) return false;
            if (m != killer && !pl.fawizja.pixel.party.PartyRules.active(m.session.combat().lastMonsterAttackMs(), now)) {
                return false;
            }
            all.add(m);
            levels.add(c.level());
            vocations.add(c.vocation());
        }
        if (!pl.fawizja.pixel.party.PartyRules.levelsCompatible(levels)) return false;
        int bonus = pl.fawizja.pixel.party.PartyRules.bonusPercent(type.experience(), vocations);
        for (ServerPlayer m : all) {
            PlayerCharacter c = m.session.character();
            long exp = m.session.combat().killExperience(type, c.level());
            long share = pl.fawizja.pixel.party.PartyRules.share(exp, all.size(), bonus);
            boolean rested = c.useRested(); // premia postaci (C) po podziale, jak stamina w Tibii
            if (rested) {
                share = share * (100 + PlayerCharacter.RESTED_EXP_PERCENT) / 100;
                c.stats().recordRestedKill();
            }
            String note = pl.fawizja.pixel.combat.CombatSystem.staminaNote(c, m == killer); // strefa sprzed zuzycia
            share = c.stamina().hunt(share, c.isPremium(), now); // stamina jak w Tibii: po podziale, osobno dla kazdego
            m.session.combat().awardForgeDust(type, now); // pyl Kuzni: kazdy z druzyny wlasny rzut (Canary ForgeMonster:onDeath)
            m.session.combat().awardExperience(share, (m == killer ? Messages.get("server_world.pokonano") : Messages.get("server_world.druzyna_pokonala")) + type.name()
                    + (rested ? Messages.get("combat_system.wypoczety") : "") + note, now);
            if (m != killer) m.session.partyKill(type); // zadania dnia, zlecenia i zadania licza sie calej druzynie obok
        }
        return true;
    }

    /** Obsluga polecen druzyny: PARTY INVITE|ACCEPT|KICK imie, PARTY LEAVE|INFO. */
    private void partyCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        String sub = f[1];
        if (sub.equals("INFO")) {
            pl.fawizja.pixel.party.PartyView v = partyView(p.name());
            if (!v.inParty()) {
                info(p, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message(), now);
                return;
            }
            p.lastPartyInfo = "";
            sendPartyInfo(p);
            info(p, Messages.get("server_world.druzyna") + String.join(", ", v.names()) + ". " + v.bonusLine(), now);
            return;
        }
        if (sub.equals("KICK")) {
            if (f.length < 3) return;
            String target = f[2].trim();
            String leader = parties.leaderOf(p.name());
            String exact = target;
            for (String n : parties.members(p.name())) if (n.equalsIgnoreCase(target)) exact = n;
            pl.fawizja.pixel.party.PartyManager.Result r = parties.kick(p.name(), target);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                warn(p, r == pl.fawizja.pixel.party.PartyManager.Result.NOT_LEADER ? Messages.get("social_client.tylko_lider_druzyny_moze") : r.message(), now);
                return;
            }
            ServerPlayer gone = find(exact);
            if (gone != null) {
                Messages.runAs(gone.language, () -> gone.session.log().add(now, EventLog.Kind.WARNING, leader + Messages.get("server_world.usuwa_cie_z_druzyny")));
                gone.lastPartyInfo = "";
                gone.client.send("PARTY", "-");
            }
            List<String> rest = parties.members(p.name());
            if (rest.isEmpty()) rest = List.of(p.name());
            for (String n : rest) {
                ServerPlayer m = find(n);
                if (m != null) m.session.log().add(now, EventLog.Kind.INFO, exact + Messages.get("server_world.usuniety_z_druzyny"));
            }
            sendParty(rest);
            return;
        }
        if (sub.equals("LEAVE")) {
            if (parties.members(p.name()).isEmpty()) {
                Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message()));
                return;
            }
            partyLeft(p, now);
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.INFO, Messages.get("server_world.opusciles_druzyne")));
            return;
        }
        if (f.length < 3) return;
        ServerPlayer other = find(f[2]);
        if (other == null) {
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.gracz") + f[2] + Messages.get("server_world.nie_jest_w_grze_2")));
            return;
        }
        if (sub.equals("INVITE")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.invite(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, r.message()));
                return;
            }
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.INFO, Messages.get("server_world.zaproszono") + other.name() + Messages.get("server_world.do_druzyny")));
            Messages.runAs(other.language, () -> other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.zaprasza_cie_do_druzyny") + p.name()));
            other.client.send("PARTYINV", p.name());
        } else if (sub.equals("ACCEPT")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.accept(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, r.message()));
                return;
            }
            for (String n : parties.members(p.name())) {
                ServerPlayer m = find(n);
                if (m == null) continue;
                Messages.runAs(m.language, () -> m.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.dolacza_do_druzyny")));
            }
            sendParty(parties.members(p.name()));
        }
    }

    private static String lower(String name) { return name.toLowerCase(java.util.Locale.ROOT); }

    /** Wyzwania na pojedynek: wyzywajacy (male litery) -> {wyzwany, czas}. */
    private final Map<String, Object[]> duelInvites = new java.util.HashMap<>();
    static final long DUEL_INVITE_MS = 60_000L;

    /** DUEL REQUEST imie (wyzwanie albo przyjecie wyzwania) | DUEL SURRENDER. */
    private void duelCommand(ServerPlayer p, String[] f, long now) {
        String me = p.name().toLowerCase(java.util.Locale.ROOT);
        if (f.length >= 2 && f[1].equals("SURRENDER")) {
            ServerPlayer o = p.pvp.duelPartner() == null ? null : find(p.pvp.duelPartner());
            if (o == null) {
                warn(p, Messages.get("server_world.nie_walczysz_w_pojedynku"), now);
                return;
            }
            PvpRules.endDuel(p.pvp, o.pvp);
            info(p, Messages.get("server_world.poddajesz_pojedynek_z") + o.name() + ".", now);
            info(o, p.name() + Messages.get("server_world.poddaje_sie_wygrywasz_pojedyne"), now);
            duelOver(o, p, now);
            return;
        }
        if (f.length < 3 || !f[1].equals("REQUEST")) {
            info(p, Messages.get("server_world.pojedynek_pojedynek_imie_wyzwa"), now);
            return;
        }
        ServerPlayer t = find(f[2]);
        if (t == null) {
            warn(p, Messages.get("server_world.nie_ma_w_grze") + f[2] + ".", now);
            return;
        }
        if (t == p) {
            warn(p, Messages.get("server_world.nie_mozesz_walczyc_sam"), now);
            return;
        }
        if (p.pvp.duelPartner() != null || t.pvp.duelPartner() != null) {
            warn(p, (p.pvp.duelPartner() != null ? Messages.get("server_world.juz_walczysz") : t.name() + Messages.get("server_world.juz_walczy")) + Messages.get("server_world.w_pojedynku"), now);
            return;
        }
        if (p.session.character().position().distanceTo(t.session.character().position()) > 8
                || p.session.character().position().z() != t.session.character().position().z()) {
            warn(p, t.name() + Messages.get("server_world.jest_za_daleko_podejdz"), now);
            return;
        }
        String them = t.name().toLowerCase(java.util.Locale.ROOT);
        Object[] theirs = duelInvites.get(them);
        if (theirs != null && theirs[0].equals(me) && now - (Long) theirs[1] <= DUEL_INVITE_MS) {
            duelInvites.remove(them);
            duelInvites.remove(me);
            PvpRules.startDuel(p.name(), p.pvp, t.name(), t.pvp, now);
            String rules = Messages.get("server_world.do_pierwszej_krwi_przegrany");
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.pojedynek_z") + t.name() + "!" + rules));
            Messages.runAs(t.language, () -> t.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.pojedynek_z") + p.name() + "!" + rules));
            return;
        }
        duelInvites.put(me, new Object[] {them, now});
        info(p, Messages.get("server_world.wyzywasz") + t.name() + Messages.get("server_world.na_pojedynek_czeka_minute"), now);
        info(t, p.name() + Messages.get("server_world.wyzywa_cie_na_pojedynek") + p.name(), now);
    }

    /** Wyjscie z gry w trakcie pojedynku: walkower dla przeciwnika. */
    private void leftDuel(ServerPlayer p) {
        duelInvites.remove(p.name().toLowerCase(java.util.Locale.ROOT));
        String partner = p.pvp.duelPartner();
        if (partner == null) return;
        ServerPlayer o = find(partner);
        if (o == null || o == p) {
            PvpRules.endDuel(p.pvp, p.pvp);
            return;
        }
        PvpRules.endDuel(p.pvp, o.pvp);
        info(o, p.name() + Messages.get("server_world.opuscil_gre_wygrywasz_pojedyne"), lastNow);
        duelOver(o, p, lastNow);
    }

    /** Koniec pojedynku: obie strony przestaja sie atakowac (dalsza walka bylaby zwyklym PvP z czaszka). */
    private void duelOver(ServerPlayer winner, ServerPlayer loser, long now) {
        if (winner.pvpTarget == loser.id) winner.pvpTarget = -1;
        if (loser.pvpTarget == winner.id) loser.pvpTarget = -1;
        winner.session.character().stats().recordDuelWin();
        save(winner);
        for (ServerPlayer o : players.values()) {
            if (o != winner && o != loser && o.session.character().position().distanceTo(winner.session.character().position()) <= Protocol.VIEW_RADIUS) {
                info(o, winner.name() + Messages.get("server_world.wygrywa_pojedynek_z") + loser.name() + ".", now);
            }
        }
    }

    /** Remis po czasie albo gdy ktos zginie od potwora w trakcie pojedynku. */
    private void updateDuels(long now) {
        for (ServerPlayer p : players.values()) {
            String partner = p.pvp.duelPartner();
            if (partner == null) continue;
            ServerPlayer o = find(partner);
            if (o == null) {
                PvpRules.endDuel(p.pvp, p.pvp);
                continue;
            }
            boolean dead = p.session.character().isDead() || o.session.character().isDead();
            if (now >= p.pvp.duelUntil() || dead) {
                PvpRules.endDuel(p.pvp, o.pvp);
                String why = dead ? Messages.get("server_world.pojedynek_przerwany") : Messages.get("server_world.czas_minal_pojedynek_konczy");
                info(p, why, now);
                info(o, why, now);
                if (p.pvpTarget == o.id) p.pvpTarget = -1;
                if (o.pvpTarget == p.id) o.pvpTarget = -1;
            }
        }
    }

    private void info(ServerPlayer p, String text, long now) { p.session.log().add(now, EventLog.Kind.INFO, text); }

    private void warn(ServerPlayer p, String text, long now) { p.session.log().add(now, EventLog.Kind.WARNING, text); }

    /** Handel miedzy graczami: TRADE REQUEST imie | OFFER tekst | ACCEPT | CANCEL. */
    private void tradeCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        PlayerTrade open = trades.get(lower(p.name()));
        if (open != null && tradeBroken(open, now)) {
            cancelTrade(open, Messages.get("server_world.wymiana_anulowana_za_daleko"), now);
            open = null;
        }
        switch (f[1]) {
            case "REQUEST": {
                if (f.length < 3) return;
                ServerPlayer other = find(f[2]);
                if (other == null || other == p) {
                    warn(p, other == p ? Messages.get("server_world.nie_mozesz_handlowac_z") : Messages.get("server_world.gracz") + f[2] + Messages.get("server_world.nie_jest_w_grze_2"), now);
                    return;
                }
                if (open != null) {
                    warn(p, Messages.get("server_world.najpierw_zakoncz_obecna_wymian"), now);
                    return;
                }
                PlayerTrade theirs = trades.get(lower(other.name()));
                if (theirs != null && !tradeBroken(theirs, now)) {
                    warn(p, other.name() + Messages.get("server_world.juz_z_kims_handluje"), now);
                    return;
                }
                if (!PlayerTrade.closeEnough(p.session.character().position(), other.session.character().position())) {
                    warn(p, PlayerTrade.Result.TOO_FAR.message(), now);
                    return;
                }
                Map<String, Long> mine = tradeRequests.get(lower(p.name()));
                Long asked = mine == null ? null : mine.get(lower(other.name()));
                if (asked != null && now <= asked) {
                    mine.remove(lower(other.name()));
                    PlayerTrade t = new PlayerTrade(other.name(), p.name(), now);
                    trades.put(lower(p.name()), t);
                    trades.put(lower(other.name()), t);
                    String how = Messages.get("server_world.dodaj_oferte_oferuj_5");
                    info(p, Messages.get("server_world.wymiana_z") + other.name() + " otwarta. " + how, now);
                    info(other, Messages.get("server_world.wymiana_z") + p.name() + " otwarta. " + how, now);
                    sendTradeState(t);
                } else {
                    tradeRequests.computeIfAbsent(lower(other.name()), k -> new java.util.HashMap<>())
                            .put(lower(p.name()), now + TRADE_REQUEST_MS);
                    info(p, Messages.get("server_world.zaproponowano_handel_graczowi") + other.name() + ".", now);
                    Messages.runAs(other.language, () -> other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.chce_handlowac_handel") + p.name()));
                }
                return;
            }
            case "CANCEL":
                if (open == null) {
                    warn(p, Messages.get("server_world.nie_masz_otwartej_wymiany_2"), now);
                    return;
                }
                cancelTrade(open, p.name() + Messages.get("server_world.anuluje_wymiane"), now);
                return;
            default:
        }
        if (open == null) {
            warn(p, Messages.get("server_world.nie_masz_otwartej_wymiany"), now);
            return;
        }
        ServerPlayer partner = find(open.partnerOf(p.name()));
        if (partner == null) {
            cancelTrade(open, Messages.get("server_world.wymiana_anulowana_drugi_gracz"), now);
            return;
        }
        if (f[1].equals("OFFER") && f.length >= 3) {
            PlayerTrade.Result r = open.offer(p.name(), p.session.character().backpack(), items, f[2], now);
            if (r != PlayerTrade.Result.OK) {
                warn(p, r.message(), now);
                return;
            }
            String status = Messages.get("server_world.oferta_2") + p.name() + ": " + open.offerOf(p.name()).describe(items)
                    + Messages.get("server_world.oferta_3") + partner.name() + ": " + open.offerOf(partner.name()).describe(items) + Messages.get("server_world.akceptuj");
            info(p, status, now);
            info(partner, status, now);
            sendTradeState(open);
        } else if (f[1].equals("ACCEPT")) {
            PlayerTrade.Result r = open.accept(p.name());
            if (r == PlayerTrade.Result.WAITING) {
                info(p, r.message(), now);
                sendTradeState(open);
                partner.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.akceptuje_wymiane")
                        + open.offerOf(p.name()).describe(items) + " za " + open.offerOf(partner.name()).describe(items)
                        + Messages.get("server_world.akceptuj_albo_anuluj"));
                return;
            }
            if (r != PlayerTrade.Result.DONE) {
                warn(p, r.message(), now);
                return;
            }
            ServerPlayer first = find(open.first());
            ServerPlayer second = find(open.second());
            PlayerTrade.Result done = open.execute(first.session.character().backpack(), second.session.character().backpack());
            trades.remove(lower(first.name()));
            trades.remove(lower(second.name()));
            first.client.send("TS", "-");
            second.client.send("TS", "-");
            for (ServerPlayer x : new ServerPlayer[] {first, second}) {
                if (done == PlayerTrade.Result.DONE) info(x, Messages.get("server_world.wymiana_zakonczona_oddano") + open.offerOf(x.name()).describe(items)
                        + Messages.get("server_world.otrzymano") + open.offerOf(open.partnerOf(x.name())).describe(items) + ".", now);
                else warn(x, done.message(), now);
            }
            if (done == PlayerTrade.Result.DONE) {
                LOG.info("trade " + first.name() + " <-> " + second.name() + ": " + open.offerOf(first.name()).describe(items)
                        + " / " + open.offerOf(second.name()).describe(items));
                save(first);
                save(second);
            }
        }
    }

    /** Wylogowanie przerywa wymiane i kasuje prosby (przed usunieciem z listy graczy). */
    private void leftTrade(ServerPlayer p) {
        tradeRequests.remove(lower(p.name()));
        PlayerTrade t = trades.get(lower(p.name()));
        if (t != null) cancelTrade(t, Messages.get("server_world.wymiana_anulowana") + p.name() + Messages.get("server_world.wychodzi_z_gry"), lastNow);
    }

    private boolean tradeBroken(PlayerTrade t, long now) {
        ServerPlayer x = find(t.first()), y = find(t.second());
        return x == null || y == null || t.expired(now)
                || !PlayerTrade.closeEnough(x.session.character().position(), y.session.character().position());
    }

    private void cancelTrade(PlayerTrade t, String why, long now) {
        trades.remove(lower(t.first()));
        trades.remove(lower(t.second()));
        for (String n : new String[] {t.first(), t.second()}) {
            ServerPlayer x = find(n);
            if (x == null) continue;
            warn(x, why, now);
            x.client.send("TS", "-");
        }
    }

    /** Stan wymiany dla okna handlu u obu graczy. */
    private void sendTradeState(PlayerTrade t) {
        for (String n : new String[] {t.first(), t.second()}) {
            ServerPlayer x = find(n);
            if (x == null) continue;
            String other = t.partnerOf(n);
            pl.fawizja.pixel.trade.TradeView v = new pl.fawizja.pixel.trade.TradeView(other,
                    t.offerOf(n).describe(items), t.offerOf(other).describe(items), t.accepted(n), t.accepted(other));
            String[] f = v.encode();
            x.client.send("TS", f[0], f[1], f[2], f[3], f[4]);
        }
    }

    /** Wyjscie z druzyny (polecenie, wylogowanie, usuniecie konta) z powiadomieniem pozostalych. */
    private void partyLeft(ServerPlayer p, long now) {
        List<String> rest = parties.leave(p.name());
        if (rest.isEmpty()) return;
        p.client.send("PARTY", "-");
        p.lastPartyInfo = "";
        for (String n : rest) {
            ServerPlayer m = find(n);
            if (m != null) m.session.log().add(now, EventLog.Kind.INFO, p.name() + Messages.get("server_world.opuszcza_druzyne"));
        }
        sendParty(rest);
    }

    /** Lista czlonkow do kazdego z nich ("-" gdy druzyna sie rozpadla). */
    private void sendParty(List<String> names) {
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m == null) continue;
            List<String> now = parties.members(n);
            m.client.send("PARTY", now.isEmpty() ? "-" : String.join(",", now));
            if (now.isEmpty()) m.lastPartyInfo = "";
            else sendPartyInfo(m);
        }
    }

    /** Druzyna gracza z poziomami, profesjami i premia (czlonkowie poza gra: poziom z rankingu). */
    pl.fawizja.pixel.party.PartyView partyView(String name) {
        List<String> names = parties.members(name);
        if (names.size() < 2) return pl.fawizja.pixel.party.PartyView.NONE;
        List<pl.fawizja.pixel.party.PartyView.Member> rows = new ArrayList<>();
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m != null) {
                PlayerCharacter c = m.session.character();
                rows.add(new pl.fawizja.pixel.party.PartyView.Member(c.name(), c.level(), c.vocation(), true));
            } else {
                pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(n);
                rows.add(new pl.fawizja.pixel.party.PartyView.Member(n, e == null ? 0 : e.level(), e == null ? null : e.vocation(), false));
            }
        }
        return pl.fawizja.pixel.party.PartyView.of(rows);
    }

    /** PARTYINFO do gracza, gdy cos sie zmienilo od ostatniego wyslania. */
    private void sendPartyInfo(ServerPlayer m) {
        String[] msg = partyView(m.name()).encode();
        String body = String.join("\t", msg);
        if (body.equals(m.lastPartyInfo)) return;
        m.lastPartyInfo = body;
        m.client.send(msg);
    }

    private long partyInfoSentMs = Long.MIN_VALUE / 2;

    /** Co kilka sekund: poziomy w oknie druzyny (awans, wejscie i wyjscie czlonka). */
    private void refreshPartyInfo(long now) {
        if (now - partyInfoSentMs < 3_000 && now >= partyInfoSentMs) return;
        partyInfoSentMs = now;
        for (ServerPlayer p : players.values()) {
            if (parties.members(p.name()).size() >= 2) sendPartyInfo(p);
        }
    }

    /** Odpowiedz PINFO: poziom, profesja i gildia postaci (w grze albo z rankingu). */
    pl.fawizja.pixel.social.PlayerInfo playerInfo(String name) {
        ServerPlayer o = find(name);
        if (o != null) {
            PlayerCharacter c = o.session.character();
            String g = guildTag(c.name());
            return new pl.fawizja.pixel.social.PlayerInfo(c.name(), c.level(), c.vocation(), g.equals("-") ? null : g, true);
        }
        pl.fawizja.pixel.ranking.Ranking.Entry e = ranking.get(name);
        if (e == null) return pl.fawizja.pixel.social.PlayerInfo.unknown(name);
        String g = guildTag(e.name());
        return new pl.fawizja.pixel.social.PlayerInfo(e.name(), e.level(), e.vocation(), g.equals("-") ? null : g, false);
    }

    boolean sameParty(ServerPlayer a, ServerPlayer b) { return parties.sameParty(a.name(), b.name()); }

    /** Wolne pole najblizej p (bez innego gracza i przejscia), aby gracze nie stali jeden na drugim. */
    Position freeSpotNear(Position p) {
        for (int r = 0; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    Position c = p.translate(dx, dy);
                    if (r > 0 && (!world.isWalkable(c) || world.transitionAt(c) != null)) continue;
                    if (!occupied(c)) return c;
                }
            }
        }
        return p;
    }

    private boolean occupied(Position c) {
        for (ServerPlayer o : players.values()) if (o.session.character().position().equals(c)) return true;
        return false;
    }

    private PlayerCharacter load(String name) {
        String slot = "player_" + name.toLowerCase(java.util.Locale.ROOT);
        if (!saves.exists(slot)) return null;
        try {
            return saves.read(slot, codec::decode).value();
        } catch (IOException e) {
            throw new IllegalArgumentException(Messages.get("server_world.zapis_uszkodzony"));
        }
    }

    void save(ServerPlayer p) {
        String slot = "player_" + p.name().toLowerCase(java.util.Locale.ROOT);
        try {
            saves.write(slot, codec.encode(p.session.character()));
            ranking.put(rankEntry(p.session.character()));
            weeklyRollover(lastNow);
            weekly.observe(p.name(), p.session.character().experience(), wallClock.getAsLong());
            saveWeekly();
            saves.write("pvp_" + p.name().toLowerCase(java.util.Locale.ROOT), p.pvp.encode(lastNow, System.currentTimeMillis()));
        } catch (IOException e) {
            LOG.warning("save failed for " + p.name() + ": " + e.getMessage());
        }
    }

    /** Po usunieciu konta: gracz znika ze swiata bez zapisu, a jego pliki sa kasowane. */
    private void deleteCharacter(ServerPlayer p) {
        String key = p.name().toLowerCase(java.util.Locale.ROOT);
        partyLeft(p, lastNow);
        leftTrade(p);
        leftDuel(p);
        if (guilds.guildOf(p.name()) != null) {
            guilds.forget(p.name());
            saveGuilds();
        }
        market.forget(p.name());
        saveMarket();
        mail.forget(p.name());
        saveMail();
        letters.forget(p.name());
        saveLetters();
        players.remove(p.id);
        pvp.remove(key);
        ranking.remove(p.name());
        weekly.remove(p.name());
        try {
            saves.delete("player_" + key);
            saves.delete("pvp_" + key);
        } catch (IOException e) {
            LOG.warning("delete failed for " + p.name() + ": " + e.getMessage());
        }
        p.client.player = null;
        p.client.send("ERR", Messages.get("server_world.konto_usuniete"));
        p.client.close();
        broadcastInfo(p, () -> p.name() + Messages.get("server_world.opuszcza_gre"));
        LOG.info("account deleted " + p.name());
    }

    boolean saved(String name) { return saves.exists("player_" + name.toLowerCase(java.util.Locale.ROOT)); }

    /**
     * Rozlaczenie klienta (BYE albo zerwane polaczenie). Jak w Tibii: z blokada wylogowania (walka z graczem w
     * ostatniej minucie, 15 min po nieusprawiedliwionym zabojstwie) postac zostaje w swiecie, dopoki blokada
     * nie minie - mozna ja dalej atakowac. Potem {@link #leave}.
     */
    void disconnect(ServerPlayer p, long now) {
        if (!players.containsKey(p.id)) return;
        if (p.pvp.logoutBlockedMs(now) > 0 && !p.session.character().isDead()) {
            p.lingering = true;
            p.move = null;
            LOG.info("logout blocked " + p.name() + " for " + p.pvp.logoutBlockedMs(now) + " ms");
            return;
        }
        leave(p);
    }

    /** Postacie rozlaczone z blokada wylogowania wychodza, gdy blokada minie. */
    private void releaseLingering(long now) {
        List<ServerPlayer> done = new ArrayList<>();
        for (ServerPlayer p : players.values()) if (p.lingering && p.pvp.logoutBlockedMs(now) <= 0) done.add(p);
        for (ServerPlayer p : done) leave(p);
    }

    void leave(ServerPlayer p) {
        if (!players.containsKey(p.id)) return;
        save(p); // najpierw zapis, potem usuniecie z listy graczy
        partyLeft(p, lastNow);
        leftTrade(p);
        leftDuel(p);
        players.remove(p.id);
        // wylogowanie w drzwiach poziomu: drzwi zamykaja sie za postacia (chyba ze stoi w nich ktos inny)
        Position at = p.session.character().position();
        if (world.levelDoorAt(at) > 0 && players.values().stream().noneMatch(o -> o.session.character().position().equals(at))) {
            world.openings().set(at, false);
        }
        tellFriends(p, Messages.get("server_world.wyszedl_z_gry"), lastNow);
        broadcastInfo(p, () -> p.name() + Messages.get("server_world.wychodzi_z_gry"));
        LOG.info("leave " + p.name() + " (" + players.size() + " online)");
    }

    /** Komenda od klienta (juz zalogowanego). */
    void command(ServerPlayer p, String[] f, long now) {
        Messages.runAs(p.language, () -> commandIn(p, f, now));
    }

    private void commandIn(ServerPlayer p, String[] f, long now) {
        switch (f[0]) {
            case "MOVE": p.move = f.length > 1 && !f[1].equals("-") ? parseDir(f[1]) : null; break;
            case "ACT":
                if (f.length > 1) Actions.apply(p.session, f[1], now);
                Position bed = p.session.takeSleep();
                if (bed != null) {
                    // postac polozyla sie w lozku: zapis z chwila zasniecia, klient pokazuje sen i sie rozlacza
                    save(p);
                    p.client.send("SLEEP", String.valueOf(bed.x()), String.valueOf(bed.y()), String.valueOf(bed.z()));
                    LOG.info("sleep " + p.name() + " at " + bed);
                }
                break;
            case "ATTACK":
                if (f.length > 1) attackPlayer(p, parseInt(f[1]), now);
                break;
            case "TARGET":
                p.pvpTarget = -1;
                if (f.length > 1) {
                    int id = parseInt(f[1]);
                    for (Monster m : monsters.monsters()) {
                        if (m.id() == id) p.session.targetAt(m.position());
                    }
                }
                break;
            case "PASSWD":
                if (f.length < 3) break;
                switch (accounts.changePassword(p.name(), f[1], f[2], now)) {
                    case OK: p.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.haslo_zmienione")); break;
                    case LOCKED: p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.za_duzo_blednych_prob")); break;
                    case BAD_PASSWORD:
                        p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.nowe_haslo_musi_miec") + AccountStore.MIN_PASSWORD
                                + "-" + AccountStore.MAX_PASSWORD + Messages.get("server_world.znakow"));
                        break;
                    default: p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.zle_obecne_haslo"));
                }
                break;
            case "DELETE":
                if (f.length < 2) break;
                if (accounts.delete(p.name(), f[1], now) != AccountStore.Result.OK) {
                    Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.konto_nie_zostalo_usuniete")));
                    break;
                }
                deleteCharacter(p);
                break;
            case "PARTY": partyCommand(p, f, now); break;
            case "PINFO":
                if (f.length >= 2 && !f[1].isBlank()) p.client.send(playerInfo(f[1].trim()).encode());
                break;
            case "LFG": lookingForParty(p, now); break;
            case "TRADE": tradeCommand(p, f, now); break;
            case "GUILD": guildCommand(p, f, now); break;
            case "FRIEND": friendCommand(p, f, now); break;
            case "MARKET": marketCommand(p, f, now); break;
            case "WANT": wantCommand(p, f, now); break;
            case "PRICE": marketPrice(p, f.length > 1 ? f[1] : "", now); break;
            case "MAIL": mailCommand(p, f, now); break;
            case "TRANSFER": transferCommand(p, f, now); break;
            case "HOUSE": houseCommand(p, f, now); break;
            case "BOUNTY": bountyCommand(p, f, now); break;
            case "DUEL": duelCommand(p, f, now); break;
            case "PVP": // PVP ON|OFF|TOGGLE - przelacznik trybu PvP (tez ACT pvp)
                if (f.length < 2 || f[1].equals("TOGGLE")) p.session.togglePvpMode(now);
                else if (f[1].equals("ON") != p.session.pvpMode()) p.session.togglePvpMode(now);
                break;
            case "TITLE": p.session.chooseTitle(f.length > 1 ? f[1] : null, now); break;
            case "OUTFIT": p.session.chooseOutfit(f.length > 1 ? f[1] : null, now); break;
            case "DEATH": {
                // podsumowanie ostatniej smierci tego gracza
                pl.fawizja.pixel.combat.DeathRecap.Report r = p.session.combat().lastDeath();
                List<String> out = new ArrayList<>();
                out.add("DEATHLOG");
                if (r != null) java.util.Collections.addAll(out, pl.fawizja.pixel.combat.DeathRecap.encode(pl.fawizja.pixel.combat.DeathRecap.rows(r)));
                p.client.send(out.toArray(new String[0]));
                break;
            }
            case "BOSS":
                List<String> rows = new ArrayList<>();
                rows.add("BOSS");
                for (MonsterManager.BossStatus b : monsters.bossReport(now)) rows.add(String.join(";", MonsterManager.row(b, now)));
                p.client.send(rows.toArray(new String[0]));
                break;
            case "EVENTS":
                p.client.send("EVENTS", invasionStatus(now), expBonusLine(p, now), weeklyLine(p, now));
                break;
            case "LOOTLOG": {
                // ostatnie lupy tego gracza (wiersze jak w podsumowaniu smierci)
                List<String> out = new ArrayList<>();
                out.add("LOOTLOG");
                java.util.Collections.addAll(out, pl.fawizja.pixel.combat.DeathRecap.encode(p.session.combat().lootLog().rows(now)));
                p.client.send(out.toArray(new String[0]));
                break;
            }
            case "INSPECT": {
                ServerPlayer o = f.length < 2 ? null : find(f[1].trim());
                if (o == null) {
                    warn(p, Messages.get("server_world.nie_ma_takiego_gracza"), now);
                    break;
                }
                for (String line : inspectLines(o)) info(p, line, now);
                if (o != p) o.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + Messages.get("server_world.oglada_twoj_ekwipunek"));
                break;
            }
            case "WHO": {
                // lista graczy w grze: najwyzszy poziom na gorze (bez polozenia)
                List<ServerPlayer> online = new ArrayList<>(players.values());
                online.sort((a, b) -> {
                    int c = Integer.compare(b.session.character().level(), a.session.character().level());
                    return c != 0 ? c : a.name().compareToIgnoreCase(b.name());
                });
                List<String> out = new ArrayList<>();
                out.add("WHO");
                for (ServerPlayer o : online) {
                    if (out.size() > 100) break;
                    PlayerCharacter oc = o.session.character();
                    String tag = guildTag(oc.name());
                    String title = pl.fawizja.pixel.achievement.Achievement.titleOf(oc.title());
                    out.add(oc.name() + ";" + oc.vocation().displayName() + Messages.get("pixel_game.poziom") + oc.level()
                            + (tag == null || tag.equals("-") ? "" : " <" + tag + ">") + (title == null ? "" : ", " + title)
                            + bountyNote(oc.name()) + (seeksParty(o) ? Messages.get("server_world.szuka_druzyny") : ""));
                }
                p.client.send(out.toArray(new String[0]));
                break;
            }
            case "RANK":
                if (chatAllowed(p, now)) sendRanking(p, f.length > 1 ? f[1] : null, now);
                break;
            case "PM": {
                if (f.length < 3 || f[2].isBlank()) break;
                ServerPlayer to = find(f[1].trim());
                if (to == null) {
                    // offline: list czeka do wejscia, jesli taka postac istnieje
                    String who = f[1].trim();
                    if (!saved(who)) {
                        warn(p, Messages.get("server_world.nie_ma_postaci") + who + ".", now);
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    if (!letters.canAccept(who)) {
                        warn(p, who + Messages.get("server_world.ma_juz_za_duzo"), now);
                        break;
                    }
                    letters.add(who, p.name(), f[2]);
                    saveLetters();
                    info(p, who + Messages.get("server_world.nie_jest_w_grze"), now);
                    break;
                }
                if (!chatAllowed(p, now)) break;
                String text = f[2].length() > 200 ? f[2].substring(0, 200) : f[2];
                to.client.send("CHAT", "[od " + p.name() + "]", text, "pm");
                p.client.send("CHAT", "[do " + to.name() + "]", text, "pm");
                break;
            }
            case "REPORT":
                report(p, f, now);
                break;
            case "ADMIN":
                if (!moderation.isAdmin(p.name())) {
                    warn(p, Messages.get("server_world.brak_uprawnien_administratora"), now);
                    break;
                }
                String[] cmd = new String[f.length - 1];
                System.arraycopy(f, 1, cmd, 0, cmd.length);
                String answer = moderate(cmd, p.name(), now);
                info(p, answer, now);
                LOG.info("admin " + p.name() + ": " + String.join(" ", cmd) + " -> " + answer);
                break;
            case "GCHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    pl.fawizja.pixel.guild.GuildRegistry.Guild g = guilds.guildOf(p.name());
                    if (g == null) {
                        warn(p, pl.fawizja.pixel.guild.GuildRegistry.Result.NOT_MEMBER.message(), now);
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (String n : g.members()) {
                        ServerPlayer m = find(n);
                        if (m != null) m.client.send("CHAT", "[" + g.name() + "] " + p.name(), text, "guild");
                    }
                }
                break;
            case "PCHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    List<String> team = parties.members(p.name());
                    if (team.isEmpty()) {
                        Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message()));
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (String n : team) {
                        ServerPlayer m = find(n);
                        if (m != null) m.client.send("CHAT", Messages.get("server_world.druzyna_2") + p.name(), text, "party");
                    }
                }
                break;
            case "ROLL": {
                // rzut kostka: wynik losuje serwer i widza go wszyscy (jak czat, z ta sama moderacja)
                if (!chatAllowed(p, now)) break;
                int sides = 6;
                try {
                    if (f.length > 1) sides = Math.max(2, Math.min(100, Integer.parseInt(f[1].trim())));
                } catch (NumberFormatException ignored) {
                    // zostaje k6
                }
                String text = pl.fawizja.pixel.net.Emotes.diceText(sides, 1 + rng.nextInt(sides));
                for (ServerPlayer o : players.values()) o.client.send("CHAT", p.name(), text, "local");
                break;
            }
            case "CHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", p.name(), text, "local");
                }
                break;
            case "TCHAT":
                // kanal Handel (zakladka czatu jak w Tibii): do wszystkich graczy, z ta sama moderacja co czat
                if (f.length > 1 && !f[1].isBlank()) {
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", Messages.get("server_world.handel") + p.name(), text, "trade");
                }
                break;
            default:
        }
    }

    /**
     * Polecenie moderacji (z czatu administratora albo z konsoli serwera): SAY tekst, KICK imie,
     * BAN imie godziny (0 = na stale), UNBAN imie, MUTE imie minuty (0 = zdejmij), ADMIN imie (tylko konsola),
     * WHO. Zwraca odpowiedz dla wydajacego polecenie.
     */
    static final String COMMANDS = "SAY, KICK, BAN, UNBAN, MUTE, WHO, INVASION, STATUS, SAVE, MOTD, EXPEVENT, SHUTDOWN, REPORTS";

    /** Progi ostrzezen przed wylaczeniem (ms do konca), malejaco. */
    static final long[] SHUTDOWN_WARN = {30 * 60_000L, 15 * 60_000L, 10 * 60_000L, 5 * 60_000L, 3 * 60_000L, 2 * 60_000L, 60_000L, 30_000L, 10_000L};
    /** Zaplanowane wylaczenie (czas swiata ms) albo -1. */
    private long shutdownAt = -1;
    /** Ostatni ogloszony prog (ms do konca). */
    private long shutdownAnnounced;
    private boolean shutdownDone;

    /** Po odliczaniu SHUTDOWN: gracze wyrzuceni i zapisani, petla serwera ma sie zakonczyc. */
    boolean shutdownDone() { return shutdownDone; }

    /** SHUTDOWN [minuty | Ns] (domyslnie 5 min), SHUTDOWN - odwoluje. */
    private String shutdown(String[] c, String by, long now) {
        if (!by.equals("konsola")) return Messages.get("server_world.wylaczenie_serwera_planuje_tyl");
        String arg = c.length > 1 ? c[1].trim().toLowerCase(java.util.Locale.ROOT) : "5";
        if (arg.equals("-")) {
            if (shutdownAt < 0) return Messages.get("server_world.nie_zaplanowano_wylaczenia");
            shutdownAt = -1;
            announce(() -> Messages.get("server_world.wylaczenie_serwera_odwolane"));
            return Messages.get("server_world.odwolano_wylaczenie");
        }
        long ms = arg.endsWith("s") ? Long.parseLong(arg.substring(0, arg.length() - 1)) * 1000L : Long.parseLong(arg) * 60_000L;
        if (ms < 0 || ms > 24 * 3600_000L) return Messages.get("server_world.podaj_od_0_do");
        shutdownAt = now + ms;
        shutdownAnnounced = ms;
        announce(() -> Messages.get("server_world.serwer_zostanie_wylaczony_za") + describeLeft(ms) + Messages.get("server_world.zakoncz_walke_w_bezpiecznym"));
        LOG.info("shutdown in " + ms / 1000 + " s");
        return Messages.get("server_world.wylaczenie_za") + describeLeft(ms) + Messages.get("server_world.shutdown_odwoluje");
    }

    private void updateShutdown(long now) {
        if (shutdownAt < 0 || shutdownDone) return;
        long left = shutdownAt - now;
        if (left <= 0) {
            for (ServerPlayer p : new ArrayList<>(players.values())) kick(p, Messages.get("server_world.serwer_jest_wylaczany_postac"));
            saveAll();
            shutdownDone = true;
            LOG.info("shutdown: players saved");
            return;
        }
        for (long w : SHUTDOWN_WARN) {
            if (w < shutdownAnnounced && left <= w) {
                shutdownAnnounced = w;
                announce(() -> Messages.get("server_world.serwer_zostanie_wylaczony_za") + describeLeft(w) + ".");
                break;
            }
        }
    }

    static String describeLeft(long ms) {
        long s = (ms + 999) / 1000;
        return s >= 60 && s % 60 == 0 ? s / 60 + Messages.get("hud.min") : s >= 60 ? s / 60 + Messages.get("server_world.min_3") + s % 60 + " s" : s + " s";
    }

    private void announce(java.util.function.Supplier<String> text) {
        for (ServerPlayer o : players.values()) {
            Messages.runAs(o.language, () -> o.client.send("CHAT", Messages.get("server_world.ogloszenie"), text.get(), "broadcast"));
        }
    }
    /** Czas pierwszego taktu swiata (do czasu dzialania w STATUS); -1 przed pierwszym taktem. */
    private long firstTick = -1;

    /** Stan serwera dla operatora: czas dzialania, gracze, potwory, inwazja, pamiec. */
    String status(long now) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long up = firstTick < 0 ? 0 : Math.max(0, now - firstTick) / 1000;
        return String.format(java.util.Locale.ROOT, Messages.get("server_world.dziala_d_h_02d"),
                up / 3600, up / 60 % 60, players.size(), monsters.monsters().size(),
                invasion == null ? Messages.get("server_world.brak") : invasion.plan().town() + " (" + invasion.remaining() + " z " + invasion.total() + ")", usedMb, rt.maxMemory() / (1024 * 1024),
                shutdownAt < 0 ? "" : Messages.get("server_world.wylaczenie_za_2") + describeLeft(Math.max(0, shutdownAt - now)));
    }

    String moderate(String[] c, String by, long now) {
        if (c.length == 0) return Messages.get("server_world.polecenia") + COMMANDS;
        long wall = wallClock.getAsLong();
        try {
            switch (c[0].toUpperCase(java.util.Locale.ROOT)) {
                case "SAY": {
                    if (c.length < 2 || c[1].isBlank()) return Messages.get("server_world.podaj_tekst_ogloszenia");
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", Messages.get("server_world.ogloszenie"), c[1], "broadcast");
                    return Messages.get("server_world.wyslano_ogloszenie_do") + players.size() + " graczy.";
                }
                case "KICK": {
                    ServerPlayer t = c.length < 2 ? null : find(c[1].trim());
                    if (t == null) return Messages.get("server_world.nie_ma_takiego_gracza");
                    kick(t, Messages.get("server_world.wyrzucono_z_serwera") + by + ").");
                    return Messages.get("server_world.wyrzucono") + t.name() + ".";
                }
                case "BAN": {
                    if (c.length < 2 || !saved(c[1].trim()) && find(c[1].trim()) == null) return Messages.get("server_world.nie_ma_takiej_postaci");
                    long hours = c.length > 2 ? Long.parseLong(c[2].trim()) : 0;
                    moderation.ban(c[1].trim(), hours, wall);
                    ServerPlayer t = find(c[1].trim());
                    String until = Moderation.describeUntil(moderation.bannedUntil(c[1].trim(), wall));
                    if (t != null) kick(t, Messages.get("server_world.konto_zablokowane") + until + ".");
                    return Messages.get("server_world.zbanowano") + c[1].trim() + " " + until + ".";
                }
                case "UNBAN":
                    if (c.length < 2) return Messages.get("server_world.podaj_imie");
                    return moderation.unban(c[1].trim()) ? Messages.get("server_world.zdjeto_bana") + c[1].trim() + "." : Messages.get("server_world.ta_postac_nie_ma");
                case "MUTE": {
                    if (c.length < 2) return Messages.get("server_world.podaj_imie");
                    long minutes = c.length > 2 ? Long.parseLong(c[2].trim()) : 60;
                    moderation.mute(c[1].trim(), minutes, wall);
                    ServerPlayer t = find(c[1].trim());
                    if (t != null) warn(t, minutes > 0 ? Messages.get("server_world.wyciszono_cie_na") + minutes + Messages.get("server_world.min_2") : Messages.get("server_world.wyciszenie_zdjete"), now);
                    return minutes > 0 ? Messages.get("server_world.wyciszono") + c[1].trim() + " na " + minutes + Messages.get("server_world.min_2") : Messages.get("server_world.zdjeto_wyciszenie") + c[1].trim() + ".";
                }
                case "ADMIN":
                    if (!by.equals("konsola")) return Messages.get("server_world.administratorow_dodaje_tylko_k");
                    if (c.length < 2) return Messages.get("server_world.podaj_imie");
                    moderation.addAdmin(c[1].trim());
                    return Messages.get("server_world.dodano_administratora") + c[1].trim() + ".";
                case "INVASION":
                    return startInvasion(c.length > 1 ? c[1] : null, now);
                case "STATUS":
                    return status(now);
                case "MOTD":
                    if (c.length < 2 || c[1].isBlank()) return motd.isEmpty() ? Messages.get("server_world.brak_wiadomosci_dnia_motd") : Messages.get("server_world.wiadomosc_dnia") + motd;
                    return setMotd(c[1]);
                case "EXPEVENT":
                    return expEvent(c, now);
                case "SHUTDOWN":
                    return shutdown(c, by, now);
                case "REPORTS":
                    return recentReports(5);
                case "SAVE":
                    saveAll();
                    return Messages.get("tile_art.zapisano") + players.size() + " postaci.";
                case "WHO": {
                    List<String> names = new ArrayList<>();
                    for (ServerPlayer o : players.values()) names.add(o.name() + " (" + o.session.character().level() + ")");
                    return players.size() + Messages.get("server_world.w_grze_5") + String.join(", ", names);
                }
                default:
                    return Messages.get("server_world.nieznane_polecenie_polecenia") + COMMANDS;
            }
        } catch (NumberFormatException e) {
            return Messages.get("server_world.czas_musi_byc_liczba");
        } catch (IOException e) {
            return Messages.get("server_world.blad_zapisu") + e.getMessage();
        }
    }

    /** Wyrzucenie: zapis postaci, komunikat i rozlaczenie. */
    void kick(ServerPlayer t, String reason) {
        leave(t);
        t.client.player = null;
        t.client.send("ERR", reason);
        t.client.close();
    }

    /** Limit spamu wspolny dla czatu ogolnego i druzyny. */
    private boolean chatAllowed(ServerPlayer p, long now) {
        if (moderation.isMuted(p.name(), wallClock.getAsLong())) {
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.jestes_wyciszony_przez_adminis")));
            return false;
        }
        while (!p.chatTimes.isEmpty() && now - p.chatTimes.peekFirst() > CHAT_WINDOW_MS) p.chatTimes.pollFirst();
        if (p.chatTimes.size() >= CHAT_MAX) {
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.zwolnij_za_duzo_wiadomosci")));
            return false;
        }
        p.chatTimes.addLast(now);
        return true;
    }

    private ServerPlayer bySession(GameSession s) {
        for (ServerPlayer o : players.values()) if (o.session == s) return o;
        return null;
    }

    /** Wybor gracza do ataku: zasady sprawdzane od razu, by gracz dostal czytelna odpowiedz. */
    void attackPlayer(ServerPlayer p, int id, long now) {
        ServerPlayer t = players.get(id);
        if (t == null) {
            p.pvpTarget = -1;
            return;
        }
        if (sameParty(p, t) && !p.pvp.duelling(t.name())) {
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, Messages.get("server_world.nie_atakujesz_czlonka_wlasnej")));
            p.pvpTarget = -1;
            return;
        }
        PvpRules.Verdict v = PvpRules.check(p.session.character(), p.pvp, t.session.character(), t.pvp, world, now);
        if (v != PvpRules.Verdict.OK) {
            Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.WARNING, v.message()));
            p.pvpTarget = -1;
            return;
        }
        p.pvpTarget = t.id;
        p.session.combat().setTarget(null);
    }

    private static Direction parseDir(String s) {
        try {
            return Direction.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Wiersz INFO dla wszystkich poza {@code except}, kazdemu w jego jezyku. */
    private void broadcastInfo(ServerPlayer except, java.util.function.Supplier<String> text) {
        for (ServerPlayer o : players.values()) {
            if (o != except) Messages.runAs(o.language, () -> o.client.send("LOG", "INFO", text.get()));
        }
    }

    private void broadcast(ServerPlayer except, String... msg) {
        for (ServerPlayer o : players.values()) if (o != except) o.client.send(msg);
    }

    /** Krok swiata: sesje graczy, potem potwory raz dla wszystkich, potem walka. */
    /** Czaszki i nieusprawiedliwione zabojstwa z dysku (przetrwaja restart serwera). */
    private PvpRules.Status loadPvp(String key, long now) {
        try {
            if (saves.exists("pvp_" + key)) {
                PvpRules.Status st = saves.read("pvp_" + key,
                        text -> PvpRules.Status.decode(text, now, System.currentTimeMillis())).value();
                if (st != null) return st;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("pvp status of " + key + " unreadable: " + e.getMessage());
        }
        return new PvpRules.Status();
    }

    void tick(long now) {
        lastNow = now;
        if (firstTick < 0) firstTick = now;
        updateExpEvent(now);
        if (now - ordersCheckedMs >= 60_000 || now < ordersCheckedMs) {
            expireOrders(now);
            weeklyRollover(now);
        }
        for (ServerPlayer p : players.values()) Messages.runAs(p.language, () -> p.session.update(now, p.move));
        bossQuests.update(bossHeroes(), now);
        long wall = wallClock.getAsLong();
        if (wall - rentSweptMs >= RENT_SWEEP_MS || wall < rentSweptMs) {
            rentSweptMs = wall;
            sweepHouseRent(now);
        }
        if (housesDirty) {
            housesDirty = false;
            for (ServerPlayer p : players.values()) {
                p.session.checkHouseAccess();          // wyrzuceni goscie i dawni wlasciciele wychodza przez drzwi
                sendHouseAccess(p);
            }
        }
        updateAltarTorches();
        updateInvasion(now);
        updateDuels(now);
        releaseLingering(now);
        refreshPartyInfo(now);
        updateShutdown(now);
        if (players.isEmpty()) return;
        List<MonsterManager.Target> targets = new ArrayList<>();
        Map<MonsterManager.Target, ServerPlayer> byTarget = new LinkedHashMap<>();
        for (ServerPlayer p : players.values()) {
            targets.add(p.session.playerTarget());
            byTarget.put(p.session.playerTarget(), p);
        }
        monsters.update(now, targets, new MonsterManager.AttackHandler() {
            @Override
            public void monsterAttacks(Monster m, long t) {
                ServerPlayer p = byTarget.get(m.target());
                if (p != null) Messages.runAs(p.language, () -> p.session.combat().monsterAttacks(m, t));
            }

            @Override
            public void monsterHeals(Monster healer, Monster healed, int amount, long t) {
                for (ServerPlayer p : players.values()) {
                    if (p.session.character().position().distanceTo(healed.position()) <= Protocol.VIEW_RADIUS) {
                        Messages.runAs(p.language, () -> p.session.combat().monsterHeals(healer, healed, amount, t));
                    }
                }
            }
        });
        sendFiendish(now);
        for (ServerPlayer p : players.values()) {
            if (p.pvpTarget < 0) continue;
            ServerPlayer t = players.get(p.pvpTarget);
            if (t == null || (sameParty(p, t) && !p.pvp.duelling(t.name()))) {
                p.pvpTarget = -1;
                continue;
            }
            PvpRules.Verdict v = PvpRules.check(p.session.character(), p.pvp, t.session.character(), t.pvp, world, now);
            if (v != PvpRules.Verdict.OK) {
                Messages.runAs(p.language, () -> p.session.log().add(now, EventLog.Kind.INFO, Messages.get("server_world.koniec_walki_z") + t.name() + ": " + v.message()));
                p.pvpTarget = -1;
                continue;
            }
            PvpRules.Outcome o = PvpRules.attack(p.session, p.pvp, t.session, t.pvp, world, now, pvpRng);
            if (o == PvpRules.Outcome.KILLED) {
                p.pvpTarget = -1;
                claimBounty(p, t, now);
            }
            if (o == PvpRules.Outcome.DUEL_WON) duelOver(p, t, now);
        }
        for (ServerPlayer p : players.values()) {
            if (now - p.lastSaveMs >= SAVE_EVERY_MS) {
                save(p);
                p.lastSaveMs = now;
            }
        }
    }

    private long fiendishSentMs = Long.MIN_VALUE / 2;

    /**
     * Diaboliczne potwory na minimapie wszystkich graczy: "FIEND x,y,z x,y,z ..." co 5 s, tylko po zmianie
     * (diabolicznych jest najwyzej {@link MonsterManager#FIENDISH_LIMIT}, ruszaja sie wolno).
     */
    private void sendFiendish(long now) {
        if (now >= fiendishSentMs && now - fiendishSentMs < 5_000) return;
        fiendishSentMs = now;
        List<String> f = new ArrayList<>();
        f.add("FIEND");
        for (Monster m : monsters.fiendishMonsters()) {
            Position mp = m.position();
            f.add(mp.x() + "," + mp.y() + "," + mp.z());
        }
        String line = String.join(" ", f);
        for (ServerPlayer p : players.values()) {
            if (line.equals(p.lastFiendish)) continue;
            p.lastFiendish = line;
            p.client.send(f.toArray(new String[0]));
        }
    }

    /** Kto stoi przed oltarzami: wszyscy zywi gracze swiata (serwer jest jedynym zrodlem prawdy). */
    private final pl.fawizja.pixel.world.AltarQuest.Occupants altarOccupants = (pos, v) -> {
        for (ServerPlayer o : players.values()) {
            PlayerCharacter c = o.session.character();
            if (!c.isDead() && c.vocation() == v && pos.equals(c.position())) return true;
        }
        return false;
    };

    /** Dzwignia sali oltarzy przenosi wszystkich graczy stojacych na swoich polach (kazdy dostaje komunikat). */
    private pl.fawizja.pixel.game.GameSession.AltarParty altarParty(ServerPlayer puller) {
        return new pl.fawizja.pixel.game.GameSession.AltarParty() {
            @Override
            public boolean standing(Position pos, Vocation v) { return altarOccupants.standing(pos, v); }

            @Override
            public void teleport(Position from, Vocation v, Position to, long t) {
                for (ServerPlayer o : players.values()) {
                    PlayerCharacter c = o.session.character();
                    if (c.isDead() || c.vocation() != v || !from.equals(c.position())) continue;
                    o.session.teleport(to, t);
                    if (o != puller) {
                        Messages.runAs(o.language, () -> o.session.log().add(t, EventLog.Kind.SYSTEM, puller.name() + Messages.get("server_world.przeciaga_dzwignie_jestescie_w")));
                    }
                    return;
                }
            }
        };
    }

    /** Pochodnie nad oltarzami zadan (zmiany ida do klientow komunikatem WLIGHT). */
    private void updateAltarTorches() {
        for (pl.fawizja.pixel.world.AltarQuest q : world.altarQuests()) q.updateTorches(world.wallLights(), altarOccupants);
    }

    /** Wszyscy gracze w grze widziani przez wyprawy na bossa. */
    private List<pl.fawizja.pixel.game.BossQuestRuntime.Hero> bossHeroes() {
        List<pl.fawizja.pixel.game.BossQuestRuntime.Hero> out = new ArrayList<>();
        for (ServerPlayer o : players.values()) out.add(o.session.bossHero());
        return out;
    }

    /** Smierc potwora: boss wyprawy rozdaje lup uczestnikom (osobno kazdemu), zapis postaci i ogloszenie. */
    private void bossQuestKill(Monster m) {
        long now = lastNow;
        pl.fawizja.pixel.world.BossQuest q = bossQuests.onMonsterDeath(m, bossHeroes(), now);
        if (q == null) return;
        List<String> party = bossQuests.party(q.id());
        for (String n : party) {
            ServerPlayer o = find(n);
            if (o != null) save(o);
        }
        announce(() -> Messages.get("server_world.druzyna_3") + String.join(", ", party) + " pokonuje " + m.type().name() + " (" + q.name() + ")!", now);
        LOG.info("boss quest " + q.id() + Messages.get("server_world.cleared_by") + party);
    }

    /** Ogloszenie wydarzenia dla wszystkich graczy w grze. */
    private void announce(java.util.function.Supplier<String> text, long now) {
        for (ServerPlayer o : players.values()) Messages.runAs(o.language, () -> o.session.log().add(now, EventLog.Kind.SYSTEM, text.get()));
    }

    /** Opis inwazji do okna wydarzen: trwajaca albo czas do nastepnej. */
    String invasionStatus(long now) {
        if (invasion != null) {
            return "trwa: " + invasion.plan().name() + Messages.get("server_world.atakuja") + invasion.plan().town() + Messages.get("server_world.zostalo") + invasion.remaining() + ")";
        }
        long at = nextInvasionAt == 0 ? now + invasionEveryMs : nextInvasionAt;
        long min = Math.max(0, (at - now + 59_999) / 60_000);
        return Messages.get("server_world.nastepna_za") + (min >= 60 ? min / 60 + " h " + String.format("%02d", min % 60) + Messages.get("hud.min") : min + Messages.get("hud.min")) + Messages.get("server_world.gdy_ktos_jest_w");
    }

    /** Harmonogram inwazji: co pixel.invasionMinutes (domyslnie 120) minut, gdy ktos jest w grze. */
    private void updateInvasion(long now) {
        if (invasion != null) {
            if (invasion.over(now)) finishInvasion(now);
            return;
        }
        if (nextInvasionAt == 0) nextInvasionAt = now + invasionEveryMs;
        if (now >= nextInvasionAt && !players.isEmpty()) {
            startInvasion(null, now);
            nextInvasionAt = now + invasionEveryMs;
        }
    }

    /** Start inwazji (town = null: kolejne miasto z listy). Zwraca opis dla administratora. */
    String startInvasion(String town, long now) {
        if (invasion != null) return Messages.get("server_world.trwa_juz_inwazja_na") + invasion.plan().town() + ".";
        List<pl.fawizja.pixel.event.Invasion.Plan> plans = pl.fawizja.pixel.event.Invasion.PLANS;
        pl.fawizja.pixel.event.Invasion.Plan plan = null;
        if (town != null && !town.isBlank()) {
            for (pl.fawizja.pixel.event.Invasion.Plan pl0 : plans) if (pl0.town().equalsIgnoreCase(town.trim())) plan = pl0;
            if (plan == null) return Messages.get("server_world.nie_ma_planu_inwazji") + town.trim() + ".";
        } else {
            if (nextInvasionPlan < 0) nextInvasionPlan = rng.nextInt(plans.size());
            plan = plans.get(nextInvasionPlan);
            nextInvasionPlan = (nextInvasionPlan + 1) % plans.size();
        }
        pl.fawizja.pixel.world.Town t = world.town(plan.town());
        Position center = t == null ? null : pl.fawizja.pixel.event.Invasion.gatherPoint(world, t, rng);
        if (center == null) return Messages.get("server_world.nie_znaleziono_miejsca_na") + plan.town() + ".";
        List<Monster> spawned = new ArrayList<>(monsters.spawnEvent(plan.monster(), center, 6, plan.count(), now));
        List<Monster> leader = monsters.spawnEvent(plan.leader(), center, 2, 1, now);
        spawned.addAll(leader);
        if (spawned.isEmpty()) return Messages.get("server_world.potwory_nie_zmiescily_sie") + plan.town() + ".";
        List<Integer> ids = new ArrayList<>();
        for (Monster m : spawned) ids.add(m.id());
        invasion = new pl.fawizja.pixel.event.Invasion(plan, center, ids, leader.isEmpty() ? -1 : leader.get(0).id(), now,
                pl.fawizja.pixel.event.Invasion.DURATION_MS);
        String leaderName = leader.isEmpty() ? "" : Messages.get("server_world.herszt") + leader.get(0).type().name();
        final pl.fawizja.pixel.event.Invasion.Plan fplan = plan;
        announce(() -> Messages.get("server_world.inwazja") + fplan.name() + Messages.get("server_world.atakuja") + fplan.town() + " " + pl.fawizja.pixel.event.Invasion.direction(t.temple(), center)
                + " (" + spawned.size() + Messages.get("server_world.wrogow_2") + leaderName + Messages.get("server_world.za_kazdego_pokonanego")
                + pl.fawizja.pixel.event.Invasion.REWARD_PER_KILL + Messages.get("server_world.gp_na_konto_po"), now);
        LOG.info("invasion " + plan.town() + " at " + center + " (" + spawned.size() + ")");
        return Messages.get("server_world.inwazja_na") + plan.town() + Messages.get("server_world.rozpoczeta") + spawned.size() + Messages.get("server_world.wrogow_przy") + center.x() + "," + center.y() + ").";
    }

    private void invasionKill(ServerPlayer p, Monster m) {
        if (invasion == null || !invasion.onKill(p.name(), m.id())) return;
        long now = lastNow;
        if (p.name().equals(invasion.leaderSlayer()) && invasion.remaining() > 0 && m.type().key().equals(invasion.plan().leader())) {
            announce(() -> p.name() + Messages.get("server_world.pokonuje_herszta_najezdzcow_zo") + invasion.remaining() + Messages.get("server_world.wrogow_3"), now);
        } else if (invasion.remaining() == invasion.total() / 2) {
            announce(() -> Messages.get("server_world.inwazja_na") + invasion.plan().town() + Messages.get("server_world.polowa_wrogow_pokonana_zostalo") + invasion.remaining() + ".", now);
        }
    }

    private void finishInvasion(long now) {
        pl.fawizja.pixel.event.Invasion inv = invasion;
        invasion = null;
        monsters.clearEvent(inv.aliveIds());
        List<Map.Entry<String, Integer>> top = inv.ranking();
        String best = top.isEmpty() ? "" : Messages.get("server_world.najwiecej_pokonal_a") + top.get(0).getKey() + " (" + top.get(0).getValue() + ").";
        if (inv.defended()) announce(() -> inv.plan().town() + Messages.get("server_world.obronione") + best + Messages.get("server_world.nagrody_trafily_na_konta_2"), now);
        else announce(() -> Messages.get("server_world.najezdzcy_odchodza_od") + inv.plan().town() + Messages.get("server_world.zostalo") + inv.remaining() + ")." + best
                + (top.isEmpty() ? "" : Messages.get("server_world.za_pokonanych_sa_nagrody")), now);
        for (Map.Entry<String, Integer> e : top) {
            ServerPlayer o = find(e.getKey());
            long reward = inv.reward(e.getKey());
            if (o == null || reward <= 0) continue;
            if (o.session.character().bank().credit(reward).ok()) {
                o.session.log().add(now, EventLog.Kind.SYSTEM, Messages.get("server_world.nagroda_za_obrone") + inv.plan().town() + ": "
                        + String.format("%,d gp", reward) + Messages.get("server_world.na_koncie") + e.getValue() + " pokonanych).");
                save(o);
            }
        }
        LOG.info("invasion over " + inv.plan().town() + (inv.defended() ? " defended" : " expired"));
    }

    /** Trwajaca inwazja (testy). */
    pl.fawizja.pixel.event.Invasion invasion() { return invasion; }

    /** Wysyla kazdemu graczowi nowe wpisy dziennika, zapis postaci (gdy sie zmienil) i klatke stanu. */
    void sendState(long now) {
        for (ServerPlayer p : players.values()) {
            EventLog log = p.session.log();
            long fresh = log.total() - p.logSent;
            if (fresh > 0) {
                for (EventLog.Entry e : log.latest((int) Math.min(fresh, log.size()))) {
                    p.client.send("LOG", e.kind().name(), e.text());
                }
                p.logSent = log.total();
            }
            long freshEv = p.session.combat().eventTotal() - p.eventsSent;
            if (freshEv > 0) {
                for (CombatEvent e : p.session.combat().latestEvents((int) Math.min(freshEv, 64))) {
                    Position ep = e.position();
                    if (e.from() != null || e.effect() != null) {
                        // efekt graficzny: pocisk (skad) i rodzaj obrazen
                        Position fp = e.from() != null ? e.from() : ep;
                        p.client.send("EV", e.kind().name(), String.valueOf(ep.x()), String.valueOf(ep.y()),
                                String.valueOf(ep.z()), String.valueOf(e.amount()), String.valueOf(fp.x()),
                                String.valueOf(fp.y()), String.valueOf(fp.z()), e.effect() == null ? "-" : e.effect());
                    } else {
                        p.client.send("EV", e.kind().name(), String.valueOf(ep.x()), String.valueOf(ep.y()),
                                String.valueOf(ep.z()), String.valueOf(e.amount()));
                    }
                }
                p.eventsSent = p.session.combat().eventTotal();
            }
            if (!p.fogSent) {
                // odkryta mapa raz po zalogowaniu (czesci mieszcza sie w linii protokolu); dalej klient odkrywa
                // ja sam z pozycji z klatek, a serwer z wlasnych pozycji i zapisuje ja w zapisie postaci
                for (String part : p.session.character().explored().encodeParts(FOG_CHUNKS_PER_LINE)) p.client.send("FOG", part);
                p.fogSent = true;
            }
            if (now - p.lastSelfMs >= SELF_EVERY_MS) {
                String self = codec.encode(p.session.character(), false);
                if (!self.equals(p.lastSelf)) {
                    p.client.send("SELF", self);
                    p.lastSelf = self;
                }
                p.lastSelfMs = now;
            }
            pl.fawizja.pixel.game.Challenge ch = p.session.challenge();
            String chLine = ch == null ? "-" : ch.line(now);
            if (!chLine.equals(p.lastChallenge)) {
                p.client.send("CHAL", chLine); // stan wyzwania ze zwoju (zmienia sie co sekunde)
                p.lastChallenge = chLine;
            }
            // tryb PvP, czaszka i blokada PZ (czas serwera) - tylko po zmianie
            // PVP tryb czaszka czaszkaDo blokadaPzDo blokadaWylogowaniaDo
            String pvpLine = Protocol.line("PVP", p.session.pvpMode() ? "1" : "0", p.pvp.skull(now).name(),
                    String.valueOf(p.pvp.skullUntil(now)), String.valueOf(p.pvp.fightUntil() > now ? p.pvp.fightUntil() : 0),
                    String.valueOf(p.pvp.logoutUntil() > now ? p.pvp.logoutUntil() : 0));
            if (!pvpLine.equals(p.lastPvp)) {
                p.client.send(Protocol.parse(pvpLine));
                p.lastPvp = pvpLine;
            }
            Position me = p.session.character().position();
            StringBuilder frame = new StringBuilder();
            frame.append(Protocol.line("ME", String.valueOf(me.x()), String.valueOf(me.y()), String.valueOf(me.z()),
                    p.session.facing().name(), p.pvp.skull(now).name())).append('\n');
            frame.append(Protocol.line("TP", p.pvpTarget < 0 ? "-" : String.valueOf(p.pvpTarget))).append('\n');
            Monster tg = p.session.combat().target();
            frame.append(Protocol.line("TG", tg == null || tg.isDead() ? "-" : String.valueOf(tg.id()))).append('\n');
            for (ServerPlayer o : players.values()) {
                Position op = o.session.character().position();
                if (o == p || op.z() != me.z() || op.distanceTo(me) > Protocol.VIEW_RADIUS) continue;
                PlayerCharacter oc = o.session.character();
                frame.append(Protocol.line("PL", String.valueOf(o.id), oc.name(), oc.vocation().name(),
                        String.valueOf(op.x()), String.valueOf(op.y()), String.valueOf(op.z()),
                        String.valueOf(100 * oc.health() / Math.max(1, oc.maxHealth())),
                        PvpRules.skullSeenBy(p.pvp, oc.name(), o.pvp, now).name(), // zolta/pomaranczowa tylko dla p
                        guildTag(oc.name()), oc.isMounted() ? "M" : "-", oc.pet() == null ? "-" : oc.pet(),
                        oc.title() == null ? "-" : oc.title(), String.valueOf(oc.outfit()),
                        o.session.pvpMode() ? "P" : "-",
                        // harmonia mnicha pod paskami nad postacia (jak w Tibii)
                        oc.vocation() == pl.fawizja.pixel.vocation.Vocation.MONK
                                ? pl.fawizja.pixel.net.RemoteEntity.harmonyField(oc.harmony().points(), oc.harmony().serene()) : "-"))
                        .append('\n');
            }
            for (Monster m : monsters.near(me, Protocol.VIEW_RADIUS)) {
                if (m.isDead() || m.position().z() != me.z()) continue;
                Position mp = m.position();
                frame.append(Protocol.line("MO", String.valueOf(m.id()), m.type().key(), String.valueOf(mp.x()),
                        String.valueOf(mp.y()), String.valueOf(mp.z()), String.valueOf(Math.round(100 * m.healthFraction())),
                        MonsterManager.mark(m.type())))
                        .append('\n');
            }
            ground.forEachNear(me, Protocol.VIEW_RADIUS, (gp, st) -> frame.append(Protocol.line("GI",
                    String.valueOf(gp.x()), String.valueOf(gp.y()), String.valueOf(gp.z()),
                    String.valueOf(st.itemId()), String.valueOf(st.count()))).append('\n'));
            ground.forEachCorpseNear(me, Protocol.VIEW_RADIUS, (cp, c) -> frame.append(Protocol.line("CO",
                    String.valueOf(cp.x()), String.valueOf(cp.y()), String.valueOf(cp.z()), c.monsterKey(),
                    c.hasLoot() ? "1" : "0")).append('\n'));
            String body = frame.toString();
            if (body.equals(p.lastFrameBody)) {
                // nic sie nie zmienilo: sam znacznik czasu zamiast calej klatki (oszczednosc transferu na telefonie)
                p.client.send("KEEP", String.valueOf(now));
                p.framesKept++;
            } else {
                p.client.sendRaw(Protocol.line("FRAME", String.valueOf(now)) + "\n" + body + "END");
                p.lastFrameBody = body;
            }
        }
    }

    void saveAll() {
        for (ServerPlayer p : players.values()) save(p);
    }
}
