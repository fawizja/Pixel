package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.Blessing;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.movement.MoveResult;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.QuestGate;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Pieczec Gasnacej Gwiazdy (tools/mapgen/citadel.py): lancuch misji Straznika, bramy bossow i misji, wybor jednej
 * nagrody, znacznik wyprawy zapisywany w postaci, Herold w Varden i dostep do wyspy tylko statkiem.
 */
class StarQuestTest {

    static final String ISLAND = "Wyspa Gasnącej Gwiazdy";
    static final String[] MISSIONS = {"star_m1", "star_m2", "star_m3", "star_m4", "star_m5", "star_m6", "star_final"};
    static final String[] BOSSES = {"vorgrim", "ysmera", "kharzul", "vessara", "ilmora", "ugrath", "noctharion"};
    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;
    static MonsterRegistry monsters;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, monsters);
    }

    private static PlayerCharacter hero(Vocation v, int level) {
        PlayerCharacter pc = TestData.newCharacter(v);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        return pc;
    }

    private static GameSession session(PlayerCharacter pc, String npcKey) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, new MonsterManager(world, null, List.of(), new Random(1)),
                new Random(2), 8);
        s.setTownServices(npcs, shops, quests);
        s.setChests(TreasureChests.generate(world));
        if (npcKey != null) {
            Npc n = npcs.get(npcKey);
            assertNotNull(n, npcKey);
            s.teleport(n.position().translate(0, 1), 0);
            if (s.npcNearby() != n) s.teleport(n.position().translate(1, 0), 0);
            if (s.npcNearby() != n) s.teleport(n.position().translate(-1, 0), 0);
            assertEquals(n, s.npcNearby(), npcKey);
        }
        return s;
    }

    /** Pola osiagalne pieszo (kroki, schody, portale, drzwi poziomu i bramy zadan wg postaci) od start. */
    private static Set<Position> explore(PlayerCharacter pc, Position start) {
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            Position p = q.poll();
            for (Direction d : Direction.values()) {
                pc.setPosition(p);
                if (TestData.stepOpeningDoors(world, ms, pc, d).moved() && seen.add(pc.position())) q.add(pc.position());
            }
            pc.setPosition(p);
            if (ms.use(pc) == MoveResult.CHANGED_FLOOR && seen.add(pc.position())) q.add(pc.position());
        }
        return seen;
    }

    private static boolean near(Set<Position> set, Position p) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (set.contains(p.translate(dx, dy))) return true;
        return false;
    }

    private static Position shipStop() {
        Npc c = npcs.get("star_captain");
        assertNotNull(c);
        for (int[] d : new int[][] {{0, 1}, {1, 0}, {-1, 0}, {0, -1}}) {
            Position q = c.position().translate(d[0], d[1]);
            if (world.isWalkable(q)) return q;
        }
        throw new AssertionError("no standing place at the island dock");
    }

    private static void finishMissions(PlayerCharacter pc, int n) {
        for (int i = 0; i < n; i++) pc.quests().finish(MISSIONS[i]);
    }

    // ------------------------------------------------------------------ misje

    @Test
    void missionsFormAChainWithKillCountsAndTheFinalOneSetsTheFlagThatTheCodecSaves() throws Exception {
        assertEquals(7, quests.byGiver(StarQuest.GIVER).size());
        PlayerCharacter pc = hero(Vocation.KNIGHT, 320);
        GameSession s = session(pc, StarQuest.GIVER);
        assertEquals(NpcRole.GUIDE, npcs.get(StarQuest.GIVER).role());
        assertEquals(QuestResult.LOCKED, s.questAction("star_m2", 0), "the second mission needs the first");
        for (int i = 0; i < MISSIONS.length; i++) {
            QuestType q = quests.get(MISSIONS[i]);
            assertEquals(300, q.minLevel(), "endgame: level 300");
            assertEquals(QuestResult.ACCEPTED, s.questAction(q.key(), 0), q.key());
            assertEquals(QuestResult.NOT_FINISHED, s.questAction(q.key(), 0), "kills first");
            for (int k = 0; k < q.amount() - 1; k++) s.partyKill(monsters.get(q.target()));
            assertEquals(QuestResult.NOT_FINISHED, s.questAction(q.key(), 0), "one kill short");
            s.partyKill(monsters.get(q.target()));
            assertFalse(pc.hasQuestFlag(StarQuest.FLAG_SEALED), "not before the last mission is handed in");
            assertEquals(QuestResult.COMPLETED, s.questAction(q.key(), 0), q.key());
            assertEquals(QuestResult.ALREADY_DONE, s.questAction(q.key(), 0));
        }
        assertTrue(pc.hasQuestFlag(StarQuest.FLAG_SEALED), "the Seal is closed");
        assertTrue(StarQuest.completed(pc));
        // flaga i misje zapisuje kodek postaci (offline i na serwerze to ten sam zapis)
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(new CharacterCodec(TestData.ITEMS).encode(pc));
        assertTrue(back.hasQuestFlag(StarQuest.FLAG_SEALED));
        assertTrue(back.hasQuestFlag(QuestGate.bossFlag("noctharion")), "the boss kill flag was saved too");
        for (String m : MISSIONS) assertEquals(QuestLog.State.DONE, back.quests().state(m), m);
    }

    @Test
    void killingASealGuardianSetsItsFlagOnlyOnce() {
        PlayerCharacter pc = hero(Vocation.KNIGHT, 400);
        GameSession s = session(pc, null);
        assertFalse(pc.hasQuestFlag(QuestGate.bossFlag("vorgrim")));
        s.partyKill(monsters.get("vorgrim"));
        assertTrue(pc.hasQuestFlag(QuestGate.bossFlag("vorgrim")));
        assertFalse(pc.hasQuestFlag(QuestGate.bossFlag("ysmera")), "only the guardian that fell");
        assertFalse(pc.hasQuestFlag(StarQuest.FLAG_SEALED), "bosses alone do not close the Seal");
        assertTrue(world.gateBosses().containsAll(List.of(BOSSES)), "every boss guards a gate: " + world.gateBosses());
    }

    // ------------------------------------------------------------------ bramy i mapa

    @Test
    void gatesRefuseWithoutTheMissionOrBossAndOpenWithIt() {
        PlayerCharacter pc = hero(Vocation.KNIGHT, 400);
        MovementSystem ms = new MovementSystem(world, TestData.ITEMS.byKey("rope").id());
        QuestGate quest = null, boss = null;
        for (QuestGate g : world.questGates().values()) {
            if (g.kind() == QuestGate.Kind.QUEST && g.target().equals("star_m1")) quest = g;
            if (g.kind() == QuestGate.Kind.BOSS && g.target().equals("vorgrim") && world.levelDoorAt(g.at()) > 0) boss = g;
        }
        assertNotNull(quest);
        assertNotNull(boss);
        for (QuestGate g : new QuestGate[] {quest, boss}) {
            assertEquals(300, world.levelDoorAt(g.at()), "gate doors are level doors of 300 too: " + g);
            boolean opened = false;
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                Position from = g.at().translate(-d.dx, -d.dy);
                if (!world.isWalkable(from)) continue;
                PlayerCharacter p = hero(Vocation.KNIGHT, 400);
                p.setPosition(from);
                if (ms.step(p, d) == MoveResult.GATE_SEALED) {
                    opened = true;
                    if (g == quest) p.quests().finish("star_m1"); else p.setQuestFlag(QuestGate.bossFlag("vorgrim"));
                    p.setPosition(from);
                    assertNotEquals(MoveResult.GATE_SEALED, ms.step(p, d), "open after the requirement: " + g);
                    break;
                }
            }
            assertTrue(opened, "a sealed approach exists: " + g);
        }
        GameSession s = session(pc, null);
        s.teleport(quest.at().translate(0, 1), 0);
        s.useAt(quest.at(), 0);
        assertTrue(s.log().latest(5).stream().anyMatch(l -> l.text().contains(quests.get("star_m1").name())), "the refusal names the mission");
    }

    @Test
    void theIslandIsReachedByShipAndTheGatesCutTheCitadelIntoStages() throws Exception {
        Position dock = shipStop();
        // bez zadan i bossow: dziedziniec, swiatynia (poziom 300) i pierwsza polowa sekcji 1
        PlayerCharacter pc = hero(Vocation.KNIGHT, 1000);
        Set<Position> a = explore(pc, dock);
        assertTrue(near(a, npcs.get(StarQuest.GIVER).position()), "the Warden stands in the yard");
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters);
        int firstHalf = 0;
        for (SpawnPoint sp : spawns) {
            if (sp.monsterKey().equals("ash_penitent") && near(a, sp.center())) firstHalf++;
            assertFalse(sp.monsterKey().equals("vorgrim") && near(a, sp.center()), "no boss without the missions");
            assertFalse(sp.monsterKey().equals("ysmera") && near(a, sp.center()));
            assertFalse(sp.monsterKey().equals("rimebound_monk") && near(a, sp.center()), "section 2 is sealed");
        }
        assertTrue(firstHalf >= 10, "enough penitents to hunt before the gate: " + firstHalf);
        for (TreasureChests.Chest c : TreasureChests.generate(world).all()) {
            if (world.rewardChoiceAt(c.position()) != null) assertFalse(near(a, c.position()), "reward room is sealed: " + c.id());
        }
        // wszystkie misje, ale bez bossow: sala bossa 1 jest dostepna, sekcja 2 jeszcze nie
        PlayerCharacter m = hero(Vocation.KNIGHT, 1000);
        finishMissions(m, 6);
        Set<Position> b = explore(m, dock);
        boolean vorgrim = false;
        for (SpawnPoint sp : spawns) {
            if (sp.monsterKey().equals("vorgrim")) vorgrim = near(b, sp.center());
            assertFalse(sp.monsterKey().equals("rimebound_monk") && near(b, sp.center()), "the guardian must fall first");
        }
        assertTrue(vorgrim, "boss hall of section 1 after the mission");
        // wszystko zrobione: kazdy spawn, kazdy boss, kazda skrzynia nagrod
        PlayerCharacter all = hero(Vocation.KNIGHT, 1000);
        finishMissions(all, 7);
        for (String boss : BOSSES) all.setQuestFlag(QuestGate.bossFlag(boss));
        Set<Position> c = explore(all, dock);
        List<String> missing = new java.util.ArrayList<>();
        Set<String> keys = Set.of("ash_penitent", "cinder_revenant", "rimebound_monk", "hoarfrost_warden", "slag_forgehand",
                "molten_cultist", "thorn_maiden", "spore_stalker", "mirror_acolyte", "glass_phantom", "maw_crawler",
                "gore_harbinger", "dimmed_seraph", "vorgrim", "ysmera", "kharzul", "vessara", "ilmora", "ugrath", "noctharion");
        int island = 0;
        for (SpawnPoint sp : spawns) {
            if (!keys.contains(sp.monsterKey())) continue;
            island++;
            if (!near(c, sp.center())) missing.add(sp.monsterKey() + sp.center());
        }
        assertTrue(island >= 200, "a long dungeon: " + island + " spawn points");
        assertTrue(missing.isEmpty(), "unreachable after the whole quest: " + missing);
        assertEquals(11, world.rewardChoices().stream().filter(r -> r.group().equals(StarQuest.REWARD_GROUP)).count());
        for (WorldMap.RewardChoice rc : world.rewardChoices()) if (rc.group().equals(StarQuest.REWARD_GROUP)) assertTrue(near(c, rc.position()), rc.itemKey());
    }

    @Test
    void thereIsNoWalkingRouteFromVardenAndShipsNeedPremium() throws Exception {
        PlayerCharacter pc = hero(Vocation.KNIGHT, 1000);
        finishMissions(pc, 7);
        for (String boss : BOSSES) pc.setQuestFlag(QuestGate.bossFlag(boss));
        Set<Position> fromVarden = explore(pc, world.town("Varden").temple());
        assertFalse(near(fromVarden, shipStop()), "the island dock is not reachable on foot");
        assertFalse(near(fromVarden, npcs.get(StarQuest.GIVER).position()));
        for (String boss : BOSSES) {
            for (SpawnPoint sp : SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters)) {
                if (sp.monsterKey().equals(boss)) assertFalse(near(fromVarden, sp.center()), boss + " is not on a walking route");
            }
        }
        // statkiem: konto premium z Varden na wyspe i z powrotem; konto darmowe nie plynie
        PlayerCharacter free = hero(Vocation.KNIGHT, 400);
        assertTrue(pl.fawizja.pixel.economy.Money.add(free.backpack(), 100_000).ok());
        GameSession fs = session(free, "varden_captain");
        assertFalse(fs.travel(ISLAND, 0), "a free account cannot sail to the island");
        free.setPremium(true);
        GameSession ps = session(free, "varden_captain");
        assertTrue(ps.travel(ISLAND, 0), "premium sails to the island");
        assertTrue(near(Set.of(free.position()), npcs.get("star_captain").position()), "arrives at the island dock");
    }

    // ------------------------------------------------------------------ nagroda

    @Test
    void theRewardRoomLetsEachCharacterChooseExactlyOneItem() throws Exception {
        List<WorldMap.RewardChoice> choices = world.rewardChoices().stream().filter(r -> r.group().equals(StarQuest.REWARD_GROUP)).toList();
        assertEquals(11, choices.size());
        for (Vocation v : Vocation.values()) {
            long usable = choices.stream().filter(c -> TestData.ITEMS.byKey(c.itemKey()).usableBy(v)).count();
            assertTrue(usable >= 3, v + " has a choice of equipment: " + usable);
        }
        PlayerCharacter pc = hero(Vocation.KNIGHT, 400);
        GameSession s = session(pc, null);
        WorldMap.RewardChoice blade = choices.stream().filter(c -> c.itemKey().equals("star_blade")).findFirst().orElseThrow();
        WorldMap.RewardChoice wand = choices.stream().filter(c -> c.itemKey().equals("star_wand")).findFirst().orElseThrow();
        WorldMap.RewardChoice axe = choices.stream().filter(c -> c.itemKey().equals("star_axe")).findFirst().orElseThrow();
        s.teleport(blade.position().translate(0, 1), 0);
        assertTrue(s.useAt(blade.position(), 0));
        assertFalse(pc.backpack().count(TestData.ITEMS.byKey("star_blade").id()) > 0, "sealed until the quest is done");
        finishMissions(pc, 7);
        pc.setQuestFlag(StarQuest.FLAG_SEALED);
        // przedmiot cudzej profesji nie zuzywa wyboru
        s.teleport(wand.position().translate(0, 1), 0);
        s.useAt(wand.position(), 0);
        assertFalse(pc.backpack().count(TestData.ITEMS.byKey("star_wand").id()) > 0);
        assertFalse(pc.openedChests().contains(StarQuest.REWARD_TAKEN), "the wrong vocation does not use up the choice");
        s.teleport(blade.position().translate(0, 1), 0);
        s.useAt(blade.position(), 0);
        assertTrue(pc.backpack().count(TestData.ITEMS.byKey("star_blade").id()) > 0, "the chosen sword");
        assertTrue(pc.openedChests().contains(StarQuest.REWARD_TAKEN));
        // drugiej nagrody nie ma: ani tej samej skrzyni, ani innej
        s.useAt(blade.position(), 0);
        s.teleport(axe.position().translate(0, 1), 0);
        s.useAt(axe.position(), 0);
        assertFalse(pc.backpack().count(TestData.ITEMS.byKey("star_axe").id()) > 0, "only one reward per character");
        assertEquals(1, pc.backpack().count(TestData.ITEMS.byKey("star_blade").id()));
        // wybor zostaje po zapisie
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(new CharacterCodec(TestData.ITEMS).encode(pc));
        GameSession s2 = session(back, null);
        s2.teleport(axe.position().translate(0, 1), 0);
        s2.useAt(axe.position(), 0);
        assertFalse(back.backpack().count(TestData.ITEMS.byKey("star_axe").id()) > 0, "the choice survives a save");
    }

    // ------------------------------------------------------------------ Herold w Varden

    @Test
    void theVardenHeraldStandsApartFromEveryOtherNpcAndIsReachable() throws Exception {
        Npc herald = npcs.get(StarQuest.HERALD);
        assertNotNull(herald);
        for (Npc n : npcs.all()) {
            if (n == herald || n.position().z() != 0) continue;
            int cheb = Math.max(Math.abs(n.position().x() - herald.position().x()), Math.abs(n.position().y() - herald.position().y()));
            assertTrue(cheb >= 4, "the talk button must not pick " + n.key() + " instead of the herald (distance " + cheb + ")");
        }
        // stoi w swiatyni Varden i da sie do niego dojsc z miejsca odrodzenia
        Position temple = world.town("Varden").temple();
        assertTrue(Math.abs(herald.position().x() - temple.x()) <= 8 && Math.abs(herald.position().y() - temple.y()) <= 8, "inside the temple");
        Set<Position> reach = explore(hero(Vocation.KNIGHT, 1), temple);
        assertTrue(near(reach, herald.position()), "a player can walk up to the herald");
        // przycisk "Mow" (najblizszy NPC) z pola obok herolda wskazuje herolda
        Position beside = herald.position().translate(0, 1);
        Npc nearest = null;
        for (Npc n : npcs.all()) {
            if (n.position().z() != 0) continue;
            if (nearest == null || n.position().distanceTo(beside) < nearest.position().distanceTo(beside)) nearest = n;
        }
        assertEquals(StarQuest.HERALD, nearest.key());
    }

    @Test
    void theVardenHeraldSellsEveryBlessingAtOnceOnlyAfterTheSeal() {
        Npc herald = npcs.get(StarQuest.HERALD);
        assertNotNull(herald);
        assertEquals(NpcRole.HERALD, herald.role());
        assertEquals("Varden", herald.town());
        long chapelMonks = npcs.all().stream().filter(n -> n.role() == NpcRole.BLESSER).count();
        assertEquals(6, chapelMonks, "the six chapel monks are unchanged");
        // bez znacznika: odmowa, nic nie kupione, zloto nietkniete
        PlayerCharacter pc = hero(Vocation.SORCERER, 300);
        assertTrue(pl.fawizja.pixel.economy.Money.add(pc.backpack(), 2_000_000).ok());
        GameSession s = session(pc, StarQuest.HERALD);
        long gold = s.gold();
        assertNotNull(s.heraldBlocked(), "explains why");
        assertFalse(s.blessAll(0));
        for (Blessing b : Blessing.values()) assertFalse(pc.boughtBlessing(b), b.key());
        assertEquals(gold, s.gold());
        String no = s.askNpc("blogoslawienstwa", 0);
        assertNotNull(no);
        assertTrue(no.contains(pl.fawizja.pixel.i18n.Messages.get("star.herald_locked")), no);
        assertFalse(s.blessAll(0), "still no after asking");
        // z znacznikiem: wszystkie szesc za jedna cene
        pc.setQuestFlag(StarQuest.FLAG_SEALED);
        assertNull(s.heraldBlocked());
        long sum = 0;
        for (Blessing b : Blessing.values()) sum += b.price(pc.level());
        long price = s.heraldPrice();
        assertEquals(Math.round(sum * StarQuest.BUNDLE_FACTOR / 100.0) * 100, price);
        assertTrue(price < sum && price > 0, "one price below the six single prices");
        String offer = s.askNpc("blogoslawienstwa", 0);
        assertTrue(offer.replace(" ", "").replace('\u00a0', ' ').replace(" ", "").contains(String.valueOf(price)), offer);
        assertNotNull(s.askNpc("tak", 0));
        for (Blessing b : Blessing.values()) assertTrue(pc.boughtBlessing(b), "bought: " + b.key());
        assertEquals(5, pc.blessingCount(), "five regular blessings (Twist of Fate is the sixth)");
        assertEquals(gold - price, s.gold());
        assertFalse(s.blessAll(0), "nothing left to buy");
        assertEquals(gold - price, s.gold());
    }

    @Test
    void theHeraldWorksWithoutPremiumAndChargesOnlyForTheMissingOnes() {
        PlayerCharacter pc = hero(Vocation.DRUID, 300);
        pc.setPremium(false);
        pc.setQuestFlag(StarQuest.FLAG_SEALED);
        assertTrue(pl.fawizja.pixel.economy.Money.add(pc.backpack(), 1_000_000).ok());
        pc.addBlessing(Blessing.WISDOM);
        GameSession s = session(pc, StarQuest.HERALD);
        long full = Math.round(java.util.Arrays.stream(Blessing.values()).mapToLong(b -> b.price(300)).sum() * 0.8 / 100.0) * 100;
        assertTrue(s.heraldPrice() < full, "the one already owned is not charged");
        long gold = s.gold();
        long price = s.heraldPrice();
        assertTrue(s.blessAll(0));
        assertEquals(gold - price, s.gold());
        for (Blessing b : Blessing.values()) assertTrue(pc.boughtBlessing(b));
        // za mało zlota: odmowa, nic nie kupione
        PlayerCharacter poor = hero(Vocation.DRUID, 300);
        poor.setQuestFlag(StarQuest.FLAG_SEALED);
        GameSession ps = session(poor, StarQuest.HERALD);
        assertFalse(ps.blessAll(0));
        for (Blessing b : Blessing.values()) assertFalse(poor.boughtBlessing(b));
    }
}
