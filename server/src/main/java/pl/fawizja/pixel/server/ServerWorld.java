package pl.fawizja.pixel.server;

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
    /** Czat: najwyzej CHAT_MAX wiadomosci w CHAT_WINDOW_MS. */
    static final int CHAT_MAX = 5;
    static final long CHAT_WINDOW_MS = 10_000;

    final ItemRegistry items = ItemRegistry.loadDefault();
    final WorldMap world;
    final MonsterManager monsters;
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
    private final ShopRegistry shops;
    private final QuestRegistry quests;
    private final SpellRegistry spells;
    private final ConsumableRegistry consumables;
    private final RecipeRegistry recipes;
    private final CharacterCodec codec = new CharacterCodec(items);
    private final SaveStore saves;
    private final AccountStore accounts;
    private final Random rng = new Random();
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
    private final pl.fawizja.pixel.party.PartyManager parties = new pl.fawizja.pixel.party.PartyManager();
    private int nextId = 1;

    ServerWorld(File saveDir) throws IOException {
        world = new MapLoader(TileRegistry.loadDefault()).load(WorldLayout.WORLD_MAP);
        monsterTypes = MonsterRegistry.loadDefault(items);
        monsters = new MonsterManager(world, monsterTypes, SpawnTable.load(WorldLayout.WORLD_MAP, world, monsterTypes), rng);
        monsters.populate(null);
        shops = ShopRegistry.loadDefault(items);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(items, monsterTypes);
        spells = SpellRegistry.loadDefault(items);
        consumables = ConsumableRegistry.loadDefault(items);
        recipes = RecipeRegistry.loadDefault(items);
        saves = new SaveStore(saveDir);
        accounts = new AccountStore(saveDir);
    }

    Collection<ServerPlayer> players() { return Collections.unmodifiableCollection(players.values()); }

    ServerPlayer find(String name) {
        for (ServerPlayer p : players.values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Logowanie: wczytuje zapis postaci o tym imieniu albo tworzy nowa. Zwraca null z komunikatem bledu w client. */
    ServerPlayer join(ClientHandler client, String name, String vocation, String password, long now) {
        try {
            PlayerCharacter.validateName(name);
        } catch (IllegalArgumentException e) {
            client.send("ERR", "Niepoprawne imie: 3-20 liter, spacje i apostrof.");
            return null;
        }
        boolean newAccount = !saved(name) && !accounts.exists(name);
        if (newAccount) {
            try {
                Vocation.valueOf(vocation);
            } catch (IllegalArgumentException e) {
                client.send("ERR", "Nieznana profesja.");
                return null;
            }
            java.util.ArrayDeque<Long> made = newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>());
            while (!made.isEmpty() && now - made.peekFirst() > NEW_ACCOUNT_WINDOW_MS) made.pollFirst();
            if (made.size() >= maxNewAccountsPerAddress) {
                client.send("ERR", "Za duzo nowych kont z tego adresu - sprobuj jutro.");
                return null;
            }
        }
        switch (accounts.login(name, password, now)) {
            case WRONG_PASSWORD:
                client.send("ERR", "Zle haslo.");
                return null;
            case LOCKED:
                client.send("ERR", "Za duzo blednych prob - sprobuj za minute.");
                return null;
            case BAD_PASSWORD:
                client.send("ERR", "Haslo musi miec " + AccountStore.MIN_PASSWORD + "-" + AccountStore.MAX_PASSWORD + " znakow.");
                return null;
            case CREATED:
                LOG.info("new account " + name);
                newAccounts.computeIfAbsent(client.address(), k -> new java.util.ArrayDeque<>()).addLast(now);
                break;
            default:
        }
        if (find(name) != null) {
            client.send("ERR", "Postac " + name + " jest juz w grze.");
            return null;
        }
        PlayerCharacter pc;
        try {
            pc = load(name);
            if (pc == null) {
                pc = new CharacterFactory(config, items).create(name, Vocation.valueOf(vocation),
                        world.templeOf(config.startTown()));
            }
        } catch (IllegalArgumentException e) {
            client.send("ERR", "Nieprawidlowe dane postaci: " + e.getMessage());
            return null;
        }
        pc.setPosition(freeSpotNear(pc.position()));
        GameSession s = new GameSession(items, world, pc, monsters, ground, new Random(rng.nextLong()),
                config.startLevel(), spells, consumables, recipes);
        s.setTownServices(npcs, shops, quests);
        ServerPlayer p = new ServerPlayer(nextId++, s, client, now,
                pvp.computeIfAbsent(pc.name().toLowerCase(java.util.Locale.ROOT), k -> loadPvp(k, now)));
        if (p.pvp.fightUntil() > now) s.lockProtectionZonesUntil(p.pvp.fightUntil());
        s.spells().setPlayerTargeting(new pl.fawizja.pixel.magic.SpellSystem.PlayerTargeting() {
            @Override
            public Position position() {
                ServerPlayer t = p.pvpTarget < 0 ? null : players.get(p.pvpTarget);
                return t == null ? null : t.session.character().position();
            }

            @Override
            public boolean strike(int amount, pl.fawizja.pixel.combat.DamageType type, long t) {
                ServerPlayer v = p.pvpTarget < 0 ? null : players.get(p.pvpTarget);
                if (v == null) return false;
                PvpRules.Outcome o = PvpRules.spellHit(p.session, p.pvp, v.session, v.pvp, amount, type, world, t, pvpRng);
                if (o == PvpRules.Outcome.KILLED) p.pvpTarget = -1;
                return o != PvpRules.Outcome.NOT_ALLOWED;
            }
        });
        s.combat().setExperienceSharer((type, t) -> shareKill(p, type, t));
        players.put(p.id, p);
        client.send("WELCOME", String.valueOf(p.id), String.valueOf(PixelServer.TICK_MS));
        broadcast(p, "LOG", "INFO", pc.name() + " wchodzi do gry.");
        LOG.info("join " + pc.name() + " (" + players.size() + " online)");
        return p;
    }

    /**
     * Doswiadczenie za potwora zabitego przez p dzielone w druzynie ({@link pl.fawizja.pixel.party.PartyRules}).
     * false = brak warunkow (zabojca dostaje doswiadczenie sam).
     */
    boolean shareKill(ServerPlayer killer, pl.fawizja.pixel.monster.MonsterType type, long now) {
        List<String> names = parties.members(killer.name());
        if (names.size() < 2) return false;
        Position at = killer.session.character().position();
        List<ServerPlayer> eligible = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (String n : names) {
            ServerPlayer m = find(n);
            if (m == null || m.session.character().isDead()) continue;
            if (!pl.fawizja.pixel.party.PartyRules.inShareRange(at, m.session.character().position())) continue;
            eligible.add(m);
            levels.add(m.session.character().level());
        }
        if (eligible.size() < 2 || !pl.fawizja.pixel.party.PartyRules.levelsCompatible(levels)) return false;
        for (ServerPlayer m : eligible) {
            long solo = pl.fawizja.pixel.balance.Balance.experienceReward(type.experience(), type.level(),
                    m.session.character().level());
            long share = pl.fawizja.pixel.party.PartyRules.share(solo, eligible.size());
            m.session.combat().awardExperience(share, (m == killer ? "Pokonano: " : "Druzyna pokonala: ") + type.name(), now);
        }
        return true;
    }

    /** Obsluga polecen druzyny: PARTY INVITE|ACCEPT imie, PARTY LEAVE. */
    private void partyCommand(ServerPlayer p, String[] f, long now) {
        if (f.length < 2) return;
        String sub = f[1];
        if (sub.equals("LEAVE")) {
            if (parties.members(p.name()).isEmpty()) {
                p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message);
                return;
            }
            partyLeft(p, now);
            p.session.log().add(now, EventLog.Kind.INFO, "Opusciles druzyne.");
            return;
        }
        if (f.length < 3) return;
        ServerPlayer other = find(f[2]);
        if (other == null) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Gracz " + f[2] + " nie jest w grze.");
            return;
        }
        if (sub.equals("INVITE")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.invite(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                p.session.log().add(now, EventLog.Kind.WARNING, r.message);
                return;
            }
            p.session.log().add(now, EventLog.Kind.INFO, "Zaproszono " + other.name() + " do druzyny.");
            other.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " zaprasza cie do druzyny: /przyjmij " + p.name());
        } else if (sub.equals("ACCEPT")) {
            pl.fawizja.pixel.party.PartyManager.Result r = parties.accept(p.name(), other.name(), now);
            if (r != pl.fawizja.pixel.party.PartyManager.Result.OK) {
                p.session.log().add(now, EventLog.Kind.WARNING, r.message);
                return;
            }
            for (String n : parties.members(p.name())) {
                ServerPlayer m = find(n);
                if (m == null) continue;
                m.session.log().add(now, EventLog.Kind.SYSTEM, p.name() + " dolacza do druzyny.");
            }
            sendParty(parties.members(p.name()));
        }
    }

    /** Wyjscie z druzyny (polecenie, wylogowanie, usuniecie konta) z powiadomieniem pozostalych. */
    private void partyLeft(ServerPlayer p, long now) {
        List<String> rest = parties.leave(p.name());
        if (rest.isEmpty()) return;
        p.client.send("PARTY", "-");
        for (String n : rest) {
            ServerPlayer m = find(n);
            if (m != null) m.session.log().add(now, EventLog.Kind.INFO, p.name() + " opuszcza druzyne.");
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
        }
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
            throw new IllegalArgumentException("zapis uszkodzony");
        }
    }

    void save(ServerPlayer p) {
        String slot = "player_" + p.name().toLowerCase(java.util.Locale.ROOT);
        try {
            saves.write(slot, codec.encode(p.session.character()));
            saves.write("pvp_" + p.name().toLowerCase(java.util.Locale.ROOT), p.pvp.encode(lastNow, System.currentTimeMillis()));
        } catch (IOException e) {
            LOG.warning("save failed for " + p.name() + ": " + e.getMessage());
        }
    }

    /** Po usunieciu konta: gracz znika ze swiata bez zapisu, a jego pliki sa kasowane. */
    private void deleteCharacter(ServerPlayer p) {
        String key = p.name().toLowerCase(java.util.Locale.ROOT);
        partyLeft(p, lastNow);
        players.remove(p.id);
        pvp.remove(key);
        try {
            saves.delete("player_" + key);
            saves.delete("pvp_" + key);
        } catch (IOException e) {
            LOG.warning("delete failed for " + p.name() + ": " + e.getMessage());
        }
        p.client.player = null;
        p.client.send("ERR", "Konto usuniete.");
        p.client.close();
        broadcast(p, "LOG", "INFO", p.name() + " opuszcza gre.");
        LOG.info("account deleted " + p.name());
    }

    boolean saved(String name) { return saves.exists("player_" + name.toLowerCase(java.util.Locale.ROOT)); }

    void leave(ServerPlayer p) {
        if (!players.containsKey(p.id)) return;
        save(p); // najpierw zapis, potem usuniecie z listy graczy
        partyLeft(p, lastNow);
        players.remove(p.id);
        broadcast(p, "LOG", "INFO", p.name() + " wychodzi z gry.");
        LOG.info("leave " + p.name() + " (" + players.size() + " online)");
    }

    /** Komenda od klienta (juz zalogowanego). */
    void command(ServerPlayer p, String[] f, long now) {
        switch (f[0]) {
            case "MOVE": p.move = f.length > 1 && !f[1].equals("-") ? parseDir(f[1]) : null; break;
            case "ACT": if (f.length > 1) Actions.apply(p.session, f[1], now); break;
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
                    case OK: p.session.log().add(now, EventLog.Kind.SYSTEM, "Haslo zmienione."); break;
                    case LOCKED: p.session.log().add(now, EventLog.Kind.WARNING, "Za duzo blednych prob - sprobuj za minute."); break;
                    case BAD_PASSWORD:
                        p.session.log().add(now, EventLog.Kind.WARNING, "Nowe haslo musi miec " + AccountStore.MIN_PASSWORD
                                + "-" + AccountStore.MAX_PASSWORD + " znakow.");
                        break;
                    default: p.session.log().add(now, EventLog.Kind.WARNING, "Zle obecne haslo.");
                }
                break;
            case "DELETE":
                if (f.length < 2) break;
                if (accounts.delete(p.name(), f[1], now) != AccountStore.Result.OK) {
                    p.session.log().add(now, EventLog.Kind.WARNING, "Konto nie zostalo usuniete - zle haslo.");
                    break;
                }
                deleteCharacter(p);
                break;
            case "PARTY": partyCommand(p, f, now); break;
            case "PCHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    List<String> team = parties.members(p.name());
                    if (team.isEmpty()) {
                        p.session.log().add(now, EventLog.Kind.WARNING, pl.fawizja.pixel.party.PartyManager.Result.NOT_IN_PARTY.message);
                        break;
                    }
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (String n : team) {
                        ServerPlayer m = find(n);
                        if (m != null) m.client.send("CHAT", "[Druzyna] " + p.name(), text);
                    }
                }
                break;
            case "CHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    if (!chatAllowed(p, now)) break;
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", p.name(), text);
                }
                break;
            default:
        }
    }

    /** Limit spamu wspolny dla czatu ogolnego i druzyny. */
    private boolean chatAllowed(ServerPlayer p, long now) {
        while (!p.chatTimes.isEmpty() && now - p.chatTimes.peekFirst() > CHAT_WINDOW_MS) p.chatTimes.pollFirst();
        if (p.chatTimes.size() >= CHAT_MAX) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Zwolnij - za duzo wiadomosci na czacie.");
            return false;
        }
        p.chatTimes.addLast(now);
        return true;
    }

    /** Wybor gracza do ataku: zasady sprawdzane od razu, by gracz dostal czytelna odpowiedz. */
    void attackPlayer(ServerPlayer p, int id, long now) {
        ServerPlayer t = players.get(id);
        if (t == null) {
            p.pvpTarget = -1;
            return;
        }
        if (sameParty(p, t)) {
            p.session.log().add(now, EventLog.Kind.WARNING, "Nie atakujesz czlonka wlasnej druzyny.");
            p.pvpTarget = -1;
            return;
        }
        PvpRules.Verdict v = PvpRules.check(p.session.character(), t.session.character(), t.pvp, world, now);
        if (v != PvpRules.Verdict.OK) {
            p.session.log().add(now, EventLog.Kind.WARNING, v.message);
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
        for (ServerPlayer p : players.values()) p.session.update(now, p.move);
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
                if (p != null) p.session.combat().monsterAttacks(m, t);
            }

            @Override
            public void monsterHeals(Monster healer, Monster healed, int amount, long t) {
                for (ServerPlayer p : players.values()) {
                    if (p.session.character().position().distanceTo(healed.position()) <= Protocol.VIEW_RADIUS) {
                        p.session.combat().monsterHeals(healer, healed, amount, t);
                    }
                }
            }
        });
        for (ServerPlayer p : players.values()) {
            if (p.pvpTarget < 0) continue;
            ServerPlayer t = players.get(p.pvpTarget);
            if (t == null || sameParty(p, t)) {
                p.pvpTarget = -1;
                continue;
            }
            PvpRules.Verdict v = PvpRules.check(p.session.character(), t.session.character(), t.pvp, world, now);
            if (v != PvpRules.Verdict.OK) {
                p.session.log().add(now, EventLog.Kind.INFO, "Koniec walki z " + t.name() + ": " + v.message);
                p.pvpTarget = -1;
                continue;
            }
            if (PvpRules.attack(p.session, p.pvp, t.session, t.pvp, world, now, pvpRng) == PvpRules.Outcome.KILLED) {
                p.pvpTarget = -1;
            }
        }
        for (ServerPlayer p : players.values()) {
            if (now - p.lastSaveMs >= SAVE_EVERY_MS) {
                save(p);
                p.lastSaveMs = now;
            }
        }
    }

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
            if (now - p.lastSelfMs >= SELF_EVERY_MS) {
                String self = codec.encode(p.session.character());
                if (!self.equals(p.lastSelf)) {
                    p.client.send("SELF", self);
                    p.lastSelf = self;
                }
                p.lastSelfMs = now;
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
                        String.valueOf(100 * oc.health() / Math.max(1, oc.maxHealth())), o.pvp.skull(now).name())).append('\n');
            }
            for (Monster m : monsters.near(me, Protocol.VIEW_RADIUS)) {
                if (m.isDead() || m.position().z() != me.z()) continue;
                Position mp = m.position();
                frame.append(Protocol.line("MO", String.valueOf(m.id()), m.type().key(), String.valueOf(mp.x()),
                        String.valueOf(mp.y()), String.valueOf(mp.z()), String.valueOf(Math.round(100 * m.healthFraction()))))
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
