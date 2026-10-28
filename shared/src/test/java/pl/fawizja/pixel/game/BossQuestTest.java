package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.LootRules;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.BossQuest;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Reachability;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Wyprawy na bossa w piec postaci (Darek 2026-10-28): cztery lochy w roznych krainach (poziom 30, 100, 200, 400),
 * drzwi poziomu, przedsionek z piecioma polami i dzwignia, zamknieta arena, osobny lup kazdego uczestnika, raz na postac.
 */
class BossQuestTest {

    private static final MonsterRegistry MONSTERS;

    static {
        try {
            MONSTERS = MonsterRegistry.loadDefault(TestData.ITEMS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String[] NAMES = {"Ala", "Bartek", "Celina", "Darek", "Ela"};
    private final WorldMap world = TestData.world();
    private final BossQuest quest = world.bossQuest("grimtooth");
    private MonsterManager monsters;
    private BossQuestRuntime runtime;
    private final List<GameSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        monsters = new MonsterManager(world, MONSTERS, List.of(), new Random(3));
        runtime = new BossQuestRuntime(world, TestData.ITEMS, monsters, new Random(5));
        sessions.clear();
    }

    @AfterEach
    void cleanUp() {
        for (Position d : world.levelDoors().keySet()) world.openings().set(d, false);
    }

    private static PlayerCharacter hero(String name, Vocation v, int level, Position at) {
        PlayerCharacter pc = new pl.fawizja.pixel.character.CharacterFactory(pl.fawizja.pixel.config.StartingConfig.loadDefault(),
                TestData.ITEMS).create(name, v, at);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.setPosition(at);
        return pc;
    }

    /** Sesja "jak na serwerze": wspolne potwory i wspolne areny, wszyscy gracze widoczni dla wyprawy. */
    private GameSession join(PlayerCharacter pc) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, monsters, new Random(sessions.size() + 11), 1);
        s.setBossQuests(runtime, this::heroes);
        sessions.add(s);
        return s;
    }

    private List<BossQuestRuntime.Hero> heroes() {
        List<BossQuestRuntime.Hero> out = new ArrayList<>();
        for (GameSession s : sessions) out.add(s.bossHero());
        return out;
    }

    /** n postaci na pierwszych n polach przedsionka; pierwsza na srodkowym polu (obok dzwigni). */
    private List<GameSession> party(int n) {
        List<BossQuest.Square> sq = new ArrayList<>(quest.squares());
        BossQuest.Square middle = sq.remove(2);
        sq.add(0, middle);
        Vocation[] v = Vocation.values();
        List<GameSession> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(join(hero(NAMES[i], v[i % v.length], 35, sq.get(i).stand())));
        return out;
    }

    private Monster boss() {
        int id = runtime.bossId(quest.id());
        for (Monster m : monsters.monsters()) if (m.id() == id) return m;
        return null;
    }

    private static String last(GameSession s) { return s.log().latest(1).get(0).text(); }

    private static boolean logged(GameSession s, String part) {
        return s.log().latest(30).stream().anyMatch(e -> e.text().contains(part));
    }

    // ------------------------------------------------------------------ mapa

    @Test
    void fourQuestsInDifferentRegionsAndTiers() {
        List<BossQuest> all = world.bossQuests();
        assertEquals(4, all.size());
        assertEquals(List.of(30, 100, 200, 400), all.stream().map(BossQuest::minLevel).toList());
        assertEquals(List.of("grimtooth", "frost_queen", "pharaoh", "ignaroth"), all.stream().map(BossQuest::bossKey).toList());
        Set<String> grounds = new HashSet<>();
        for (BossQuest q : all) {
            MonsterType boss = MONSTERS.get(q.bossKey());
            assertTrue(boss.boss(), q.bossKey() + " is a boss");
            assertTrue(boss.level() >= q.minLevel() && boss.level() <= 2 * q.minLevel() + 10,
                    q.id() + ": boss level " + boss.level() + " fits tier " + q.minLevel());
            Position outside = q.levelDoor().translate(0, 2);
            grounds.add(world.tiles().get(world.floor(0).get(Layer.GROUND, outside.x(), outside.y())).key());
            for (BossQuest o : all) {
                if (o != q) assertTrue(o.levelDoor().distanceTo(q.levelDoor()) > 100, "far apart: " + q.id() + " " + o.id());
            }
        }
        assertEquals(Set.of("grass", "snow", "sand", "ash"), grounds, "forest, ice, desert crypt and volcano");
    }

    @Test
    void eachQuestHasLevelDoorLeverFiveSquaresAndAClosedArena() throws Exception {
        NpcRegistry npcs = NpcRegistry.load("world", world, ShopRegistry.loadDefault(TestData.ITEMS));
        List<Reachability.Link> links = new ArrayList<>(Travel.links(npcs));
        Reachability walk = Reachability.from(world, world.spawn(), links);
        TreasureChests chests = TreasureChests.generate(world);
        for (BossQuest q : world.bossQuests()) {
            String id = q.id();
            Position door = q.levelDoor();
            assertEquals(q.minLevel(), world.levelDoorAt(door), id);
            assertEquals(q.minLevel(), world.requiredLevelAt(door), id);
            assertTrue(world.openings().isDoor(door), id + ": door at " + door);
            assertTrue(walk.reached(door.translate(0, 1)), id + ": in front of the level door");
            assertTrue(walk.reached(door), id + ": through the level door");
            assertEquals("lever", world.tiles().get(world.floor(q.lever().z()).get(Layer.OBJECT, q.lever().x(), q.lever().y())).key());
            assertSame(q, world.bossQuestByLever(q.lever()));
            assertTrue(walk.reachedNear(q.lever()), id + ": lever reachable");
            assertEquals(BossQuest.PARTY, q.squares().size());
            Set<Position> targets = new HashSet<>();
            for (BossQuest.Square s : q.squares()) {
                assertEquals("boss_plate", world.tiles().get(world.floor(s.stand().z()).get(Layer.DECORATION, s.stand().x(), s.stand().y())).key());
                assertTrue(walk.reached(s.stand()), id + ": square " + s.stand());
                assertTrue(q.inLobby(s.stand()) && world.isProtectionZone(s.stand()), id + ": squares in the safe lobby");
                assertTrue(q.inArena(s.target()) && world.isWalkable(s.target()));
                assertFalse(walk.reached(s.target()), id + ": arena only through the lever");
                assertFalse(world.isProtectionZone(s.target()), id + ": the arena is no protection zone");
                targets.add(s.target());
            }
            assertEquals(BossQuest.PARTY, targets.size());
            assertEquals(1, q.squares().get(2).stand().distanceTo(q.lever()), id + ": lever next to the middle square");
            assertTrue(q.inArena(q.bossSpawn()) && world.isWalkable(q.bossSpawn()));
            assertFalse(walk.reached(q.bossSpawn()));
            assertEquals("boss_portal", world.tiles().get(world.floor(q.exitPortal().z()).get(Layer.OBJECT, q.exitPortal().x(), q.exitPortal().y())).key());
            assertFalse(walk.reached(q.exitPortal()));
            assertTrue(q.inLobby(q.exitTarget()) && walk.reached(q.exitTarget()), id + ": portal leads to the lobby");
            assertTrue(Reachability.from(world, q.exitTarget(), links).reached(world.spawn()), id + ": way back to town");
            for (TreasureChests.Chest c : chests.all()) assertFalse(world.inBossQuestRooms(c.position()), id + ": random chest in quest rooms");
            for (Position t : chests.traps()) assertFalse(world.inBossQuestRooms(t), id + ": trap in quest rooms");
        }
    }

    @Test
    void dungeonMonstersFitTheTier() throws Exception {
        for (BossQuest q : world.bossQuests()) {
            var region = world.regionAt(q.lever());
            assertNotNull(region, q.id());
            assertEquals(q.name(), region.name());
            int count = 0;
            for (SpawnPoint s : SpawnTable.load("world", world, MONSTERS)) {
                if (world.regionAt(s.center()) != region) continue;
                int lvl = MONSTERS.get(s.monsterKey()).level();
                assertTrue(lvl >= q.minLevel() / 2 && lvl <= q.minLevel() * 2, q.id() + ": " + s.monsterKey() + " level " + lvl);
                assertFalse(MONSTERS.get(s.monsterKey()).boss(), "no bosses on the way");
                assertFalse(world.inBossQuestRooms(s.center()), q.id() + ": spawn in the quest rooms");
                count++;
            }
            assertTrue(count >= 5, q.id() + " spawns: " + count);
        }
    }

    // ------------------------------------------------------------------ dzwignia

    @Test
    void leverNeedsFivePlayers() {
        List<GameSession> p = party(4);
        GameSession puller = p.get(0);
        assertEquals(1, puller.character().position().distanceTo(quest.lever()));
        assertTrue(puller.useAt(quest.lever(), 1_000));
        assertTrue(last(puller).contains("stoi 4 z 5") && last(puller).contains("brakuje jeszcze 1"), last(puller));
        for (GameSession s : p) assertTrue(quest.inLobby(s.character().position()), "nobody moved");
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        assertTrue(monsters.monsters().isEmpty(), "no boss yet");
        // piaty dochodzi: dzwignia dziala
        GameSession fifth = join(hero("Ela", Vocation.MONK, 35, quest.squares().get(4).stand()));
        p.add(fifth);
        assertTrue(puller.useAt(quest.lever(), 2_000));
        assertEquals(BossQuestRuntime.Phase.FIGHT, runtime.phase(quest.id()));
        Set<Position> at = new HashSet<>();
        for (GameSession s : p) {
            assertTrue(quest.inArena(s.character().position()), s.character().name() + " in the arena");
            at.add(s.character().position());
            assertTrue(logged(s, "Arena Leże Grimtootha zamyka się"), s.character().name());
        }
        assertEquals(5, at.size(), "each on its own target square");
        assertEquals(5, runtime.party(quest.id()).size());
    }

    @Test
    void bossSpawnsInTheArena() {
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        Monster b = boss();
        assertNotNull(b, "boss spawned");
        assertEquals(quest.bossKey(), b.type().key());
        assertTrue(b.type().boss());
        assertTrue(quest.inArena(b.position()));
        assertTrue(b.position().distanceTo(quest.bossSpawn()) <= 1);
        assertEquals(1, monsters.monsters().size(), "only the boss");
        // druga dzwignia w trakcie walki: arena zajeta
        GameSession other = join(hero("Spozniony", Vocation.KNIGHT, 40, quest.squares().get(2).stand()));
        assertTrue(other.useAt(quest.lever(), 2_000));
        assertTrue(last(other).contains("trwa walka"), last(other));
        assertEquals(1, monsters.monsters().size());
    }

    @Test
    void offlineGhostsLetOnePlayerTestTheQuest() {
        PlayerCharacter pc = hero("Solo", Vocation.KNIGHT, 35, quest.squares().get(2).stand());
        GameSession s = new GameSession(TestData.ITEMS, world, pc, monsters, new Random(1), 1);
        assertTrue(s.useAt(quest.lever(), 0));
        assertTrue(last(s).contains("brakuje jeszcze 4"), last(s));
        s.setBossGhosts(4);
        assertTrue(s.useAt(quest.lever(), 100));
        assertTrue(quest.inArena(pc.position()), "the player is in the arena");
        assertEquals(1, monsters.monsters().size(), "boss");
        assertEquals(quest.bossKey(), monsters.monsters().get(0).type().key());
        assertTrue(s.debugDefeatQuestBoss(200));
        assertTrue(pc.completedBossQuests().contains(quest.id()));
        assertTrue(logged(s, "Twój łup z bossa"));
        // portal po walce wraca do przedsionka i zwalnia arene
        s.teleport(quest.exitPortal(), 300);
        s.update(400, null);
        assertEquals(quest.exitTarget(), pc.position());
        assertEquals(BossQuestRuntime.Phase.IDLE, s.bossQuests().phase(quest.id()));
    }

    // ------------------------------------------------------------------ lup

    @Test
    void eachSurvivorGetsOwnLootAndTheExitOpens() {
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        Monster b = boss();
        // jeden ucieka z areny (np. runa teleportu) - nie dostanie lupu ani zaliczenia
        GameSession runaway = p.get(4);
        runaway.teleport(quest.exitTarget(), 1_500);
        List<Integer> before = new ArrayList<>();
        for (GameSession s : p) before.add(s.character().backpack().size());
        p.get(1).combat().damageMonster(b, b.type().maxHealth() * 20, DamageType.HOLY, 2_000);
        assertTrue(b.isDead());
        assertEquals(BossQuestRuntime.Phase.CLEARED, runtime.phase(quest.id()));
        for (int i = 0; i < 4; i++) {
            GameSession s = p.get(i);
            assertTrue(s.character().completedBossQuests().contains(quest.id()), s.character().name());
            assertTrue(logged(s, "Twój łup z bossa Grimtooth"), s.character().name());
            assertTrue(logged(s, "W rogu areny otworzył się portal"));
        }
        assertFalse(runaway.character().completedBossQuests().contains(quest.id()), "left the arena before the kill");
        assertFalse(logged(runaway, "Twój łup z bossa"));
        // cialo bossa jest puste (lup rozdany osobno)
        var corpse = p.get(1).ground().corpseAt(b.position());
        assertTrue(corpse == null || corpse.loot().isEmpty(), "boss corpse holds no loot");
        // wyjscie: portal przenosi do przedsionka, po wyjsciu wszystkich arena jest wolna
        for (int i = 0; i < 4; i++) {
            p.get(i).teleport(quest.exitPortal(), 3_000 + i);
            runtime.update(heroes(), 3_100 + i);
            assertEquals(quest.exitTarget(), p.get(i).character().position());
        }
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
    }

    @Test
    void lootPerPlayerIsIndependentAndHighlightsBossOnlyItems() {
        MonsterType grim = MONSTERS.get("grimtooth");
        List<String> five = List.of("A", "B", "C", "D", "E");
        // wynik jednego gracza nie zalezy od tego, kto jeszcze bral udzial
        for (long seed = 1; seed <= 40; seed++) {
            Map<String, List<ItemStack>> all = LootRules.rollBossLootPerPlayer(grim, TestData.ITEMS, five, new Random(seed));
            Map<String, List<ItemStack>> alone = LootRules.rollBossLootPerPlayer(grim, TestData.ITEMS, List.of("C"), new Random(seed));
            assertEquals(alone.get("C"), all.get("C"), "seed " + seed);
        }
        // gracze nie dostaja tego samego (osobne losowania)
        int differ = 0;
        for (long seed = 1; seed <= 40; seed++) {
            Map<String, List<ItemStack>> all = LootRules.rollBossLootPerPlayer(grim, TestData.ITEMS, five, new Random(seed));
            if (new HashSet<>(all.values()).size() > 1) differ++;
        }
        assertTrue(differ > 30, "independent rolls differ: " + differ);
        // przedmioty zwyklych potworow nigdy nie sa "tylko z bossa"
        for (MonsterType t : MONSTERS.all()) {
            if (t.boss()) continue;
            for (LootEntry e : t.loot()) assertFalse(LootRules.isBossOnly(TestData.ITEMS.get(e.itemId())), t.key());
        }
        // rzadkie przedmioty spadaja rzadko: najlepsze z najmniejsza szansa
        for (BossQuest q : world.bossQuests()) {
            for (LootEntry e : MONSTERS.get(q.bossKey()).loot()) {
                var it = TestData.ITEMS.get(e.itemId());
                if (LootRules.isBossOnly(it)) assertTrue(e.chancePermille() <= LootRules.bossOnlyMaxPermille(it.value()), it.key());
            }
            assertTrue(LootRules.audit(MONSTERS.get(q.bossKey()), TestData.ITEMS).isEmpty(), q.bossKey());
        }
        // ziarno, przy ktorym ktos dostaje przedmiot tylko z bossa: komunikat go wyroznia, zwykle przedmioty nie
        long seed = -1;
        for (long sd = 1; sd < 5_000 && seed < 0; sd++) {
            Map<String, List<ItemStack>> all = LootRules.rollBossLootPerPlayer(grim, TestData.ITEMS,
                    List.of(NAMES), new Random(sd));
            for (List<ItemStack> l : all.values()) for (ItemStack st : l) if (LootRules.isBossOnly(TestData.ITEMS.get(st.itemId()))) seed = sd;
        }
        assertTrue(seed > 0, "some seed drops a boss-only item");
        runtime = new BossQuestRuntime(world, TestData.ITEMS, monsters, new Random(seed));
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        Monster b = boss();
        p.get(0).combat().damageMonster(b, b.type().maxHealth() * 20, DamageType.HOLY, 2_000);
        int highlighted = 0;
        Map<String, pl.fawizja.pixel.item.ItemType> byName = new java.util.HashMap<>();
        for (var t : TestData.ITEMS.all()) byName.put(t.name(), t);
        for (GameSession s : p) {
            String msg = s.log().latest(30).stream().map(e -> e.text()).filter(t -> t.startsWith("Twój łup z bossa")).findFirst().orElseThrow();
            for (ItemStack st : s.character().backpack().contents()) {
                var it = TestData.ITEMS.get(st.itemId());
                if (!msg.contains(it.name())) continue;
                if (LootRules.isBossOnly(it)) {
                    assertTrue(msg.contains("*" + it.name() + "* (tylko z bossa!)") || msg.contains("x " + it.name() + "* (tylko z bossa!)"), msg);
                    assertTrue(logged(s, "Rzadki łup tylko z bossa: *" + it.name() + "*"), msg);
                    highlighted++;
                }
            }
            for (String part : msg.substring(msg.indexOf(':') + 1).split(", ")) {
                if (part.contains("(tylko z bossa!)")) continue;
                String name = part.replaceAll("^\\s*\\d+x ", "").replaceAll("\\.$", "").replaceAll(" \\(.*\\)$", "").trim();
                var it = byName.get(name);
                if (it != null) assertFalse(LootRules.isBossOnly(it), "not highlighted but boss-only: " + name);
            }
        }
        assertTrue(highlighted > 0, "a boss-only item was highlighted");
    }

    @Test
    void fullBackpackSendsLootToTheDepot() {
        List<GameSession> p = party(5);
        PlayerCharacter full = p.get(3).character();
        int filler = TestData.ITEMS.byKey("rope").id();
        while (full.backpack().add(new ItemStack(filler, 1)).ok()) {
            // plecak do pelna
        }
        int depotBefore = full.depot().contents().size();
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        Monster b = boss();
        p.get(0).combat().damageMonster(b, b.type().maxHealth() * 20, DamageType.HOLY, 2_000);
        String msg = p.get(3).log().latest(30).stream().map(e -> e.text()).filter(t -> t.startsWith("Twój łup z bossa")).findFirst().orElseThrow();
        assertTrue(msg.contains("do depozytu"), msg);
        assertTrue(full.depot().contents().size() > depotBefore);
    }

    // ------------------------------------------------------------------ raz na postac

    @Test
    void oncePerCharacterAcrossSaveAndReload() throws Exception {
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        Monster b = boss();
        p.get(0).combat().damageMonster(b, b.type().maxHealth() * 20, DamageType.HOLY, 2_000);
        for (int i = 0; i < 5; i++) {
            p.get(i).teleport(quest.exitPortal(), 3_000);
            runtime.update(heroes(), 3_100);
        }
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        // zapis i wczytanie postaci (offline i serwer uzywaja tego samego kodeka)
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter veteran = codec.decode(codec.encode(p.get(0).character()));
        assertTrue(veteran.completedBossQuests().contains(quest.id()));
        sessions.clear();
        veteran.setPosition(quest.squares().get(2).stand());
        GameSession v = join(veteran);
        // nowa druzyna z weteranem: on sam nie pociagnie dzwigni
        List<BossQuest.Square> sq = quest.squares();
        for (int i : new int[] {0, 1, 3, 4}) join(hero("Nowy" + "abcde".charAt(i), Vocation.DRUID, 35, sq.get(i).stand()));
        assertTrue(v.useAt(quest.lever(), 5_000));
        assertTrue(last(v).contains("już ukończyła"), last(v));
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        // nowy ciagnie, ale weteran stoi na polu: tez odmowa z imieniem
        v.teleport(quest.exitTarget(), 5_100);
        GameSession puller = join(hero("Nowyc", Vocation.MONK, 35, sq.get(2).stand()));
        GameSession side = sessions.get(1);
        side.teleport(quest.lever().translate(1, 0), 5_150);
        veteran.setPosition(sq.get(0).stand());
        assertTrue(puller.useAt(quest.lever(), 5_200));
        assertTrue(last(puller).contains("już ukończył(a): " + veteran.name()), last(puller));
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        // drugi zapis nie gubi znacznika i nie dubluje go
        PlayerCharacter again = codec.decode(codec.encode(veteran));
        assertEquals(Set.of(quest.id()), again.completedBossQuests());
    }

    // ------------------------------------------------------------------ reset areny

    @Test
    void arenaResetsWhenThePartyDiesOrLeaves() {
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        assertNotNull(boss());
        // czterech ginie, piaty jeszcze walczy: arena trwa
        for (int i = 0; i < 4; i++) p.get(i).character().damage(1_000_000);
        runtime.update(heroes(), 1_500);
        assertEquals(BossQuestRuntime.Phase.FIGHT, runtime.phase(quest.id()));
        // ostatni ginie: boss znika, dzwignia znow dziala
        p.get(4).character().damage(1_000_000);
        runtime.update(heroes(), 2_000);
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        assertTrue(monsters.monsters().isEmpty(), "the boss despawned");
        assertTrue(logged(p.get(0), "Arena Leże Grimtootha się zamyka"));
        for (GameSession s : p) assertFalse(s.character().completedBossQuests().contains(quest.id()));
        // nowa proba (te same postacie po odrodzeniu wracaja na pola)
        sessions.clear();
        List<GameSession> retry = party(5);
        assertTrue(retry.get(0).useAt(quest.lever(), 3_000));
        assertEquals(BossQuestRuntime.Phase.FIGHT, runtime.phase(quest.id()));
        assertNotNull(boss());
        // wszyscy wychodza z areny (bez smierci): tez reset
        for (GameSession s : retry) s.teleport(quest.exitTarget(), 3_500);
        runtime.update(heroes(), 3_600);
        assertEquals(BossQuestRuntime.Phase.IDLE, runtime.phase(quest.id()));
        assertTrue(monsters.monsters().isEmpty());
    }

    @Test
    void characterLoadedInsideTheArenaIsMovedToTheLobby() {
        // offline: po wczytaniu arena jest pusta - postac wraca do przedsionka
        PlayerCharacter pc = hero("Powrot", Vocation.PALADIN, 35, quest.squares().get(0).target());
        GameSession s = new GameSession(TestData.ITEMS, world, pc);
        assertEquals(quest.exitTarget(), s.character().position());
        // serwer: obcy na arenie wraca do przedsionka, uczestnik trwajacej walki zostaje
        List<GameSession> p = party(5);
        assertTrue(p.get(0).useAt(quest.lever(), 1_000));
        assertNull(runtime.evictTarget(p.get(1).bossHero()), "fighter stays");
        GameSession stranger = join(hero("Obcy", Vocation.DRUID, 35, quest.exitTarget()));
        stranger.character().setPosition(quest.bossSpawn().translate(0, 2));
        assertEquals(quest.exitTarget(), runtime.evictTarget(stranger.bossHero()));
        assertNull(runtime.evictTarget(join(hero("Daleko", Vocation.DRUID, 35, quest.exitTarget())).bossHero()));
    }
}
