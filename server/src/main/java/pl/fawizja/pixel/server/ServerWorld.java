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

    final ItemRegistry items = ItemRegistry.loadDefault();
    final WorldMap world;
    final MonsterManager monsters;
    final GroundItems ground = new GroundItems();
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
    private final Random rng = new Random();
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
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
    }

    Collection<ServerPlayer> players() { return Collections.unmodifiableCollection(players.values()); }

    ServerPlayer find(String name) {
        for (ServerPlayer p : players.values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Logowanie: wczytuje zapis postaci o tym imieniu albo tworzy nowa. Zwraca null z komunikatem bledu w client. */
    ServerPlayer join(ClientHandler client, String name, String vocation, long now) {
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
        ServerPlayer p = new ServerPlayer(nextId++, s, client, now);
        players.put(p.id, p);
        client.send("WELCOME", String.valueOf(p.id), String.valueOf(PixelServer.TICK_MS));
        broadcast(p, "LOG", "INFO", pc.name() + " wchodzi do gry.");
        LOG.info("join " + pc.name() + " (" + players.size() + " online)");
        return p;
    }

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
        } catch (IOException e) {
            LOG.warning("save failed for " + p.name() + ": " + e.getMessage());
        }
    }

    boolean saved(String name) { return saves.exists("player_" + name.toLowerCase(java.util.Locale.ROOT)); }

    void leave(ServerPlayer p) {
        if (players.remove(p.id) == null) return;
        save(p);
        broadcast(p, "LOG", "INFO", p.name() + " wychodzi z gry.");
        LOG.info("leave " + p.name() + " (" + players.size() + " online)");
    }

    /** Komenda od klienta (juz zalogowanego). */
    void command(ServerPlayer p, String[] f, long now) {
        switch (f[0]) {
            case "MOVE": p.move = f.length > 1 && !f[1].equals("-") ? parseDir(f[1]) : null; break;
            case "ACT": if (f.length > 1) Actions.apply(p.session, f[1], now); break;
            case "TARGET":
                if (f.length > 1) {
                    int id = parseInt(f[1]);
                    for (Monster m : monsters.monsters()) {
                        if (m.id() == id) p.session.targetAt(m.position());
                    }
                }
                break;
            case "CHAT":
                if (f.length > 1 && !f[1].isBlank()) {
                    String text = f[1].length() > 200 ? f[1].substring(0, 200) : f[1];
                    for (ServerPlayer o : players.values()) o.client.send("CHAT", p.name(), text);
                }
                break;
            default:
        }
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
    void tick(long now) {
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
                for (CombatEvent e : p.session.combat().latestEvents((int) Math.min(freshEv, 40))) {
                    Position ep = e.position();
                    p.client.send("EV", e.kind().name(), String.valueOf(ep.x()), String.valueOf(ep.y()),
                            String.valueOf(ep.z()), String.valueOf(e.amount()));
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
            frame.append(Protocol.line("FRAME", String.valueOf(now))).append('\n');
            frame.append(Protocol.line("ME", String.valueOf(me.x()), String.valueOf(me.y()), String.valueOf(me.z()),
                    p.session.facing().name())).append('\n');
            Monster tg = p.session.combat().target();
            frame.append(Protocol.line("TG", tg == null || tg.isDead() ? "-" : String.valueOf(tg.id()))).append('\n');
            for (ServerPlayer o : players.values()) {
                Position op = o.session.character().position();
                if (o == p || op.z() != me.z() || op.distanceTo(me) > Protocol.VIEW_RADIUS) continue;
                PlayerCharacter oc = o.session.character();
                frame.append(Protocol.line("PL", String.valueOf(o.id), oc.name(), oc.vocation().name(),
                        String.valueOf(op.x()), String.valueOf(op.y()), String.valueOf(op.z()),
                        String.valueOf(100 * oc.health() / Math.max(1, oc.maxHealth())))).append('\n');
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
            frame.append("END");
            p.client.sendRaw(frame.toString());
        }
    }

    void saveAll() {
        for (ServerPlayer p : players.values()) save(p);
    }
}
