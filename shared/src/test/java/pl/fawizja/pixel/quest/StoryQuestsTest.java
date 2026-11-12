package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.i18n.Language;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.NpcRole;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.treasure.ChestSigns;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Trzynascie wypraw fabularnych w stylu Tibii (tools/mapgen/story_quests.py): poziomy i konta, lancuchy misji,
 * zleceniodawcy, miejsca na mapie (darmowe na kontynencie, premium na ladach premium), nagrody, tabliczki przy skrzyniach
 * i dziennik zadan.
 */
class StoryQuestsTest {

    static final List<String> FREE_EASY = List.of("mill", "crypt", "poacher", "oasis");
    static final List<String> FREE_MEDIUM = List.of("barrow", "watch", "zig");
    static final List<String> PREMIUM_MH = List.of("bog", "coil", "lantern");
    static final List<String> PREMIUM_HARD = List.of("stormgate", "wyrm");
    static final List<String> PREMIUM_EXTREME = List.of("crown");
    static final Map<String, String> TOWN = Map.ofEntries(Map.entry("mill", "Varden"), Map.entry("crypt", "Varden"),
            Map.entry("barrow", "Varden"), Map.entry("poacher", "Frostholm"), Map.entry("watch", "Frostholm"),
            Map.entry("oasis", "Qasirah"), Map.entry("zig", "Qasirah"), Map.entry("bog", "Valmir"), Map.entry("crown", "Valmir"),
            Map.entry("coil", "Selvaran"), Map.entry("stormgate", "Selvaran"), Map.entry("lantern", "Nerys"), Map.entry("wyrm", "Nerys"));
    static final List<String> ALL = new ArrayList<>();

    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;
    static QuestLines lines;
    static MonsterRegistry monsters;
    static TreasureChests chests;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, monsters);
        lines = QuestLines.loadDefault(quests);
        chests = TreasureChests.generate(world);
        for (List<String> l : List.of(FREE_EASY, FREE_MEDIUM, PREMIUM_MH, PREMIUM_HARD, PREMIUM_EXTREME)) ALL.addAll(l);
    }

    @AfterEach
    void reset() { Messages.setLanguage(Language.PL); }

    private static PlayerCharacter hero(Vocation v, int level) {
        PlayerCharacter pc = TestData.newCharacter(v);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        return pc;
    }

    private static GameSession session(PlayerCharacter pc) {
        GameSession s = new GameSession(TestData.ITEMS, world, pc, new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, quests);
        s.setChests(chests);
        return s;
    }

    private static List<WorldMap.RewardChoice> group(String key) {
        return world.rewardChoices().stream().filter(r -> r.group().equals(key)).toList();
    }

    private static boolean reached(Map<Integer, BitSet> r, Position p) {
        BitSet b = r.get(p.z());
        if (b == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (b.get((p.y() + dy) * world.width() + p.x() + dx)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ poziomy, konta i lancuchy

    @Test
    void thirteenQuestsInFiveTiersWithFreeAndPremiumSplit() {
        assertEquals(13, ALL.size());
        record Band(List<String> keys, QuestLines.Tier tier, boolean premium, int lo, int hi) {}
        for (Band b : List.of(new Band(FREE_EASY, QuestLines.Tier.EASY, false, 8, 30), new Band(FREE_MEDIUM, QuestLines.Tier.MEDIUM, false, 30, 60),
                new Band(PREMIUM_MH, QuestLines.Tier.MEDIUMHARD, true, 60, 120), new Band(PREMIUM_HARD, QuestLines.Tier.HARD, true, 120, 250),
                new Band(PREMIUM_EXTREME, QuestLines.Tier.EXTREME, true, 250, 1000))) {
            for (String k : b.keys()) {
                QuestLines.Line l = lines.get(k + "_m1");
                assertNotNull(l, k);
                assertEquals(b.tier(), l.tier(), k);
                assertEquals(b.premium(), l.premium(), k);
                assertTrue(l.minLevel() >= b.lo() && l.minLevel() <= b.hi(), k + " level " + l.minLevel());
                assertEquals(3, l.missions().size(), k);
            }
        }
        assertEquals(4, FREE_EASY.size());
        assertEquals(3, FREE_MEDIUM.size());
        assertEquals(3, PREMIUM_MH.size());
        assertEquals(2, PREMIUM_HARD.size());
    }

    @Test
    void everyQuestIsAChainOfThreeMissionsWithAnEnglishNamedGiver() {
        for (String k : ALL) {
            QuestType m1 = quests.get(k + "_m1"), m2 = quests.get(k + "_m2"), m3 = quests.get(k + "_m3");
            assertEquals(QuestKind.VISIT, m1.kind(), k);
            assertNull(m1.requires(), k);
            assertEquals(k + "_m1", m2.requires());
            assertEquals(k + "_m2", m3.requires());
            assertEquals(QuestKind.KILL, m2.kind());
            assertEquals(QuestKind.KILL, m3.kind());
            assertTrue(m2.amount() >= 5 && m3.amount() >= 5, k);
            assertEquals(m1.giver(), m2.giver());
            assertEquals(m1.giver(), m3.giver());
            assertTrue(m3.rewardExp() > m2.rewardExp() && m2.rewardExp() > m1.rewardExp(), k + " rewards grow along the chain");
            Npc g = npcs.get(m1.giver());
            assertNotNull(g, k);
            assertEquals(NpcRole.GUIDE, g.role());
            assertTrue(g.name().matches("[A-Z][A-Za-z'-]+( [A-Z][A-Za-z'-]+)*"), "English NPC name: " + g.name());
            assertTrue(world.isProtectionZone(g.position()), k + " giver stands in a safe yard");
            assertFalse(g.greeting().isBlank());
            assertTrue(monsters.contains(m2.target()) && monsters.contains(m3.target()));
            // zleceniodawca pokazuje wszystkie trzy misje, a ostatni etap wymaga poprzedniego
            assertEquals(3, quests.byGiver(g.key()).size());
        }
        // czesc wspolna nazw: kazda misja ma angielska i hiszpanska nazwe i opis
        for (Language l : List.of(Language.EN, Language.ES)) {
            Messages.setLanguage(l);
            for (String k : ALL) {
                for (String m : List.of("_m1", "_m2", "_m3")) {
                    QuestType q = quests.get(k + m);
                    assertFalse(q.name().isBlank());
                    assertFalse(q.description().isBlank(), k);
                }
            }
        }
        Messages.setLanguage(Language.PL);
        String pl = quests.get("mill_m2").description();
        Messages.setLanguage(Language.EN);
        assertNotEquals(pl, quests.get("mill_m2").description(), "the description is translated");
    }

    private static void turnInChain(PlayerCharacter pc, String k, QuestSystem qs) {
        QuestType m1 = quests.get(k + "_m1");
        assertEquals(QuestResult.ACCEPTED, qs.interact(pc, m1), k);
        // dopoki nie weszlismy do srodka, misji nie da sie oddac
        assertEquals(QuestResult.NOT_FINISHED, qs.interact(pc, m1), k);
        Region inner = world.regions().stream().filter(r -> r.name().equals(m1.target())).findFirst().orElseThrow();
        assertNotNull(inner);
        assertNull(qs.onEnter(pc, "Gdzies indziej"));
        assertEquals(m1, qs.onEnter(pc, m1.target()));
        assertEquals(QuestResult.COMPLETED, qs.interact(pc, m1));
        for (String m : List.of("_m2", "_m3")) {
            QuestType q = quests.get(k + m);
            assertEquals(QuestResult.ACCEPTED, qs.interact(pc, q), k + m);
            for (int i = 0; i < q.amount(); i++) assertEquals(i == q.amount() - 1 ? q : null, qs.onKill(pc, q.target()));
            assertEquals(QuestResult.COMPLETED, qs.interact(pc, q), k + m);
        }
        assertEquals(QuestLog.State.DONE, pc.quests().state(k + "_m3"));
    }

    @Test
    void theMissionChainRunsFromLevelDoorToTheLastTurnIn() {
        QuestSystem qs = new QuestSystem(quests, TestData.ITEMS);
        for (String k : ALL) {
            QuestType m1 = quests.get(k + "_m1");
            PlayerCharacter low = hero(Vocation.KNIGHT, Math.max(1, m1.minLevel() - 1));
            if (m1.minLevel() > 8) assertEquals(QuestResult.TOO_LOW_LEVEL, qs.interact(low, m1), k);
            // chain order: nie da sie przyjac misji 3 przed 2
            PlayerCharacter pc = hero(Vocation.KNIGHT, m1.minLevel());
            assertEquals(QuestResult.LOCKED, qs.interact(pc, quests.get(k + "_m3")), k);
            long exp0 = pc.experience();
            turnInChain(pc, k, qs);
            assertTrue(pc.experience() > exp0, k + " pays experience");
        }
    }

    // ------------------------------------------------------------------ miejsca na mapie

    @Test
    void freeQuestsLieOnTheMainContinentAndPremiumOnesOnPremiumLandReachableByShip() {
        Map<Integer, BitSet> fromVarden = TreasureChests.reachable(world);
        for (String k : ALL) {
            QuestType m1 = quests.get(k + "_m1");
            Npc g = npcs.get(m1.giver());
            boolean premium = lines.get(k + "_m1").premium();
            String town = TOWN.get(k);
            assertEquals(premium, world.town(town).premium(), k + " belongs to " + town);
            List<WorldMap.RewardChoice> rewards = group(k);
            assertTrue(rewards.size() >= 5 && rewards.size() <= 8, k + " reward chests: " + rewards.size());
            Map<Position, Integer> need = new HashMap<>();
            List<Position> targets = new ArrayList<>();
            targets.add(g.position());
            for (WorldMap.RewardChoice r : rewards) targets.add(r.position());
            Map<Integer, BitSet> byFoot = TreasureChests.explore(world, List.of(), targets, need);
            if (!premium) {
                assertTrue(reached(fromVarden, g.position()), k + " giver reachable on foot from Varden");
                for (WorldMap.RewardChoice r : rewards) {
                    assertTrue(reached(fromVarden, r.position()), k + " chest " + r.itemKey() + " reachable");
                }
            } else {
                assertFalse(reached(byFoot, g.position()), k + " is not reachable on foot from Varden");
                for (WorldMap.RewardChoice r : rewards) assertFalse(reached(byFoot, r.position()), k + " chest not on the main continent");
                Position temple = world.town(town).temple();
                Map<Integer, BitSet> fromTown = TreasureChests.reachable(world, List.of(temple));
                assertTrue(reached(fromTown, g.position()), k + " giver reachable from " + town);
                for (WorldMap.RewardChoice r : rewards) assertTrue(reached(fromTown, r.position()), k + " chest reachable from " + town);
                assertTrue(g.position().distanceTo(temple) < 260, k + " lies near " + town);
            }
            // drzwi poziomu wejscia (poza latwymi): dokladnie poziom misji
            int door = 0;
            for (Map.Entry<Position, Integer> e : world.levelDoors().entrySet()) {
                if (e.getKey().z() == g.position().z() && Math.abs(e.getKey().x() - g.position().x()) <= 8 && Math.abs(e.getKey().y() - g.position().y()) <= 4) {
                    door = Math.max(door, e.getValue());
                }
            }
            if (FREE_EASY.contains(k)) assertEquals(0, door, k + " easy quests have an open entrance");
            else assertEquals(m1.minLevel(), door, k + " level door");
            for (WorldMap.RewardChoice r : rewards) {
                assertEquals("quest:" + k + "_m3", world.rewardRequirement(k), k);
                assertTrue(need.getOrDefault(r.position(), 0) <= Math.max(door, 1) || door == 0, k + " chests need at most the door level");
            }
        }
    }

    @Test
    void everyQuestSiteHasItsMonstersInsideAndOnlyOpensAtTheDoorLevel() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load(WorldLayout.WORLD_MAP, world, monsters);
        for (String k : ALL) {
            QuestType m1 = quests.get(k + "_m1"), m2 = quests.get(k + "_m2"), m3 = quests.get(k + "_m3");
            Region inner = world.regions().stream().filter(r -> r.name().equals(m1.target())).findFirst().orElseThrow();
            Map<String, Integer> found = new HashMap<>();
            for (SpawnPoint sp : spawns) {
                Position c = sp.center();
                if (c.z() >= inner.zMin() && c.z() <= inner.zMax() && c.x() >= inner.x0() && c.x() <= inner.x1() && c.y() >= inner.y0() && c.y() <= inner.y1()) {
                    found.merge(sp.monsterKey(), sp.count(), Integer::sum);
                }
            }
            assertTrue(found.getOrDefault(m2.target(), 0) >= m2.amount(), k + " enough " + m2.target() + ": " + found);
            assertTrue(found.getOrDefault(m3.target(), 0) >= m3.amount(), k + " enough " + m3.target() + ": " + found);
        }
    }

    // ------------------------------------------------------------------ nagrody

    @Test
    void rewardsGrowWithTheTierAndEveryVocationHasSomethingToChoose() {
        long prevBest = 0;
        long prevExp = 0, prevGold = 0;
        for (String k : ALL) {
            QuestType m1 = quests.get(k + "_m1");
            long best = 0;
            for (Vocation v : Vocation.values()) {
                long usable = group(k).stream().filter(r -> TestData.ITEMS.byKey(r.itemKey()).usableBy(v)).count();
                assertTrue(usable >= 1, k + ": " + v + " can choose something");
            }
            for (WorldMap.RewardChoice r : group(k)) {
                ItemType it = TestData.ITEMS.byKey(r.itemKey());
                assertFalse(pl.fawizja.pixel.monster.LootRules.isBossOnly(it), k + " " + it.key() + " is not a boss-only item");
                assertTrue(it.minLevel() <= m1.minLevel() * 1.5 + 5, k + ": " + it.key() + " needs level " + it.minLevel() + " but the quest is for " + m1.minLevel());
                best = Math.max(best, it.value());
            }
            assertTrue(best >= prevBest, k + " best reward " + best + " >= " + prevBest);
            prevBest = best;
            long exp = 0, gold = 0;
            for (String m : List.of("_m1", "_m2", "_m3")) {
                exp += quests.get(k + m).rewardExp();
                gold += quests.get(k + m).rewardGold();
            }
            assertTrue(exp > prevExp && gold > prevGold, k + " exp/gold grow: " + exp + "/" + gold);
            prevExp = exp;
            prevGold = gold;
            // jeden poziom doswiadczenia za wyprawe, ekstremalna dwa
            long cost = Balance.experienceForLevel(m1.minLevel() + 1) - Balance.experienceForLevel(m1.minLevel());
            assertTrue(exp >= cost * 0.9 && exp <= cost * 2.2, k + ": " + exp + " exp vs level cost " + cost);
        }
        // wyprawy ekstremalne i trudne placa mniej niz Pieczec Gasnacej Gwiazdy (dodatkowa wyprawa 300+)
        long crownGold = 0, crownExp = 0, sealExp = 0;
        for (String m : List.of("_m1", "_m2", "_m3")) {
            crownGold += quests.get("crown" + m).rewardGold();
            crownExp += quests.get("crown" + m).rewardExp();
        }
        for (QuestType q : quests.all()) if (q.key().startsWith("star_")) sealExp += q.rewardExp();
        assertTrue(crownExp < sealExp / 2 && crownGold < 1_000_000, "the Seal stays the biggest expedition");
    }

    @Test
    void rewardsOfOlderQuestsFollowOneScale() {
        // doswiadczenie wzgledem kosztu poziomu: boss >= 0,45, kilkadziesiat zabic >= 0,09 (tools/mapgen/story_quests.py rebalance)
        for (QuestType q : quests.all()) {
            if (q.key().startsWith("star_") || q.key().matches(".*_m[123]") && ALL.contains(q.key().substring(0, q.key().lastIndexOf('_')))) continue;
            long cost = Balance.experienceForLevel(q.minLevel() + 1) - Balance.experienceForLevel(q.minLevel());
            double ratio = q.rewardExp() / (double) cost;
            boolean boss = q.kind() == QuestKind.KILL && q.amount() == 1;
            double floor = boss ? 0.45 : q.amount() <= 25 ? 0.09 : 0.108;
            assertTrue(ratio >= floor, q.key() + " pays " + ratio + " levels (floor " + floor + ")");
            assertTrue(ratio <= 1.25, q.key() + " is not overpaid: " + ratio);
        }
    }

    @Test
    void chestRewardsAreChosenOncePerCharacterAfterTheLastMission() {
        String k = "mill";
        PlayerCharacter pc = hero(Vocation.KNIGHT, 8);
        GameSession s = session(pc);
        List<WorldMap.RewardChoice> g = group(k);
        WorldMap.RewardChoice axe = g.stream().filter(r -> r.itemKey().equals("hand_axe")).findFirst().orElseThrow();
        WorldMap.RewardChoice armor = g.stream().filter(r -> r.itemKey().equals("studded_armor")).findFirst().orElseThrow();
        WorldMap.RewardChoice wand = g.stream().filter(r -> r.itemKey().equals("wand_of_embers")).findFirst().orElseThrow();
        int axes = pc.backpack().count(TestData.ITEMS.byKey("hand_axe").id());
        s.teleport(axe.position().translate(0, 1), 0);
        assertTrue(s.useAt(axe.position(), 0));
        assertEquals(axes, pc.backpack().count(TestData.ITEMS.byKey("hand_axe").id()), "sealed before the last mission");
        QuestSystem qs = s.quests();
        turnInChain(pc, k, qs);
        assertTrue(s.useAt(axe.position(), 0));
        assertEquals(axes + 1, pc.backpack().count(TestData.ITEMS.byKey("hand_axe").id()));
        s.teleport(armor.position().translate(0, 1), 0);
        s.useAt(armor.position(), 0);
        assertEquals(0, pc.backpack().count(TestData.ITEMS.byKey("studded_armor").id()), "only one chest of the group");
        // przedmiot innej profesji nie zuzywa wyboru
        PlayerCharacter other = hero(Vocation.KNIGHT, 8);
        GameSession s2 = session(other);
        turnInChain(other, k, s2.quests());
        s2.teleport(wand.position().translate(0, 1), 0);
        s2.useAt(wand.position(), 0);
        assertEquals(0, other.backpack().count(TestData.ITEMS.byKey("wand_of_embers").id()));
        s2.teleport(armor.position().translate(0, 1), 0);
        s2.useAt(armor.position(), 0);
        assertEquals(1, other.backpack().count(TestData.ITEMS.byKey("studded_armor").id()));
    }

    // ------------------------------------------------------------------ tabliczki

    @Test
    void everyQuestChestHasAReadableSignNextToItWithTheRightText() {
        PlayerCharacter pc = hero(Vocation.KNIGHT, 300);
        GameSession s = session(pc);
        ChestSigns signs = s.chestSigns();
        int needing = 0;
        for (TreasureChests.Chest c : chests.all()) {
            if (!ChestSigns.needsSign(c, world, s.monsters(), TestData.ITEMS)) continue;
            needing++;
            ChestSigns.Sign sg = signs.forChest(c.id());
            assertNotNull(sg, "sign for quest chest " + c.id() + " at " + c.position());
            assertEquals(c.position().z(), sg.at().z());
            assertTrue(sg.at().distanceTo(c.position()) <= 2, c.id() + " sign is next to the chest");
            assertTrue(world.isWalkable(sg.at()), "the sign does not block: " + c.id());
            assertNull(chests.at(sg.at()), "not on another chest");
            assertEquals(sg, signs.at(sg.at()));
            assertTrue(s.chestSignAt(sg.at()) && s.usableAt(sg.at()), "tapping the sign reads it");
            // brak blokowania dostepu: skrzynia ma co najmniej dwa wolne sasiednie pola poza tabliczka
            int free = 0;
            for (int[] d : new int[][] {{0, 1}, {1, 0}, {-1, 0}, {0, -1}}) {
                Position q = c.position().translate(d[0], d[1]);
                if (world.isWalkable(q) && chests.at(q) == null) free++;
            }
            assertTrue(free >= 1, c.id() + " is reachable");
        }
        assertTrue(needing >= 60, "many quest chests: " + needing);
        assertEquals(needing, signs.all().size(), "one sign per quest chest");
        // tekst: wybor nagrody wymienia przedmiot w trzech jezykach (nazwy z danych), liczbe skrzyn i profesje
        for (WorldMap.RewardChoice rc : group("mill")) {
            ChestSigns.Sign sg = signs.forChest(chests.at(rc.position()).id());
            Map<Language, String> text = new HashMap<>();
            for (Language l : Language.values()) {
                Messages.setLanguage(l);
                String t = s.chestSignText(sg);
                text.put(l, t);
                assertTrue(t.contains(TestData.ITEMS.byKey(rc.itemKey()).name()), l + ": " + t);
                assertFalse(t.contains("chest_sign."), "message exists in " + l + ": " + t);
            }
            assertNotEquals(text.get(Language.PL), text.get(Language.EN));
            assertNotEquals(text.get(Language.EN), text.get(Language.ES));
            assertTrue(text.get(Language.PL).contains("Wybierz jedną"), text.get(Language.PL));
            assertTrue(text.get(Language.EN).contains("Choose one"), text.get(Language.EN));
            assertTrue(text.get(Language.ES).contains("Elige uno"), text.get(Language.ES));
        }
        // zwykla skrzynia zadania (nie z grupy): zloto, przedmioty i poziom
        Messages.setLanguage(Language.PL);
        TreasureChests.Chest castle = chests.all().stream().filter(c -> c.doorLevel() == 55 && c.position().z() == 3).findFirst().orElseThrow();
        String t = s.chestSignText(signs.forChest(castle.id()));
        assertTrue(t.contains("gp") && t.contains("Skrzynia zadania"), t);
        // tabliczka pokazuje sie w dzienniku po dotknieciu
        s.teleport(signs.forChest(castle.id()).at().translate(0, 0), 0);
        assertTrue(s.useAt(signs.forChest(castle.id()).at(), 0));
        assertTrue(s.log().latest(3).stream().anyMatch(l -> l.text().contains("gp")));
    }

    @Test
    void signsAreIdenticalOnEveryComputation() {
        GameSession a = session(hero(Vocation.DRUID, 50)), b = session(hero(Vocation.PALADIN, 400));
        assertEquals(a.chestSigns().all(), b.chestSigns().all());
    }

    // ------------------------------------------------------------------ dziennik zadan

    @Test
    void theQuestLogListsLinesWithStepsHintsAndWhereToGo() {
        PlayerCharacter pc = hero(Vocation.KNIGHT, 12);
        GameSession s = session(pc);
        List<QuestJournal.Entry> log = s.questJournal();
        assertTrue(log.stream().anyMatch(e -> e.line().key().equals("mill_m1") && e.status() == QuestJournal.Status.NEW), "new line at its level");
        assertTrue(log.stream().noneMatch(e -> e.line().key().equals("crown_m1")), "lines far above the level stay hidden");
        QuestSystem qs = s.quests();
        assertEquals(QuestResult.ACCEPTED, qs.interact(pc, quests.get("mill_m1")));
        QuestJournal.Entry e = s.questJournal().stream().filter(x -> x.line().key().equals("mill_m1")).findFirst().orElseThrow();
        assertEquals(QuestJournal.Status.ACTIVE, e.status());
        assertEquals(0, e.current());
        assertEquals(3, e.steps().size());
        assertEquals(QuestJournal.StepState.CURRENT, e.steps().get(0).state());
        assertEquals(QuestJournal.StepState.LOCKED, e.steps().get(1).state());
        assertTrue(e.giverName().contains("Miller Bram") && e.giverName().contains("Opuszczony Młyn"), e.giverName());
        assertTrue(s.questHint(e).contains("Opuszczony Młyn (wnętrze)"), s.questHint(e));
        assertEquals(QuestLines.Tier.EASY, e.line().tier());
        assertFalse(e.line().premium());
        assertEquals(e, s.questJournal().get(0), "active quests come first");
        qs.onEnter(pc, quests.get("mill_m1").target());
        e = s.questJournal().stream().filter(x -> x.line().key().equals("mill_m1")).findFirst().orElseThrow();
        assertTrue(e.currentStep().ready());
        assertTrue(s.questHint(e).contains("Miller Bram"), "turn in hint names the giver");
        assertEquals(QuestResult.COMPLETED, qs.interact(pc, quests.get("mill_m1")));
        e = s.questJournal().stream().filter(x -> x.line().key().equals("mill_m1")).findFirst().orElseThrow();
        assertEquals(QuestJournal.StepState.DONE, e.steps().get(0).state());
        assertEquals(1, e.current());
        assertEquals(1, e.doneCount());
        assertEquals(QuestResult.ACCEPTED, qs.interact(pc, quests.get("mill_m2")));
        qs.onKill(pc, "forest_spider");
        e = s.questJournal().stream().filter(x -> x.line().key().equals("mill_m1")).findFirst().orElseThrow();
        assertEquals(1, e.currentStep().progress());
        assertTrue(s.questHint(e).contains("1/8"), s.questHint(e));
        // hiszpanski i angielski: podpowiedz bez surowych kluczy
        for (Language l : List.of(Language.EN, Language.ES)) {
            Messages.setLanguage(l);
            String h = s.questHint(e);
            assertFalse(h.isBlank() || h.contains("questlog."), l + ": " + h);
            assertFalse(e.name().isBlank());
            assertFalse(Messages.get("questlog.tier_" + e.line().tier().name().toLowerCase()).contains("questlog."));
        }
    }

    @Test
    void everyQuestLineHasAValidRowAndCompletedLinesMoveToTheEnd() {
        long roots = quests.all().stream().filter(q -> q.requires() == null).count();
        assertEquals(roots, lines.all().size(), "one line per chain");
        for (QuestLines.Line l : lines.all()) {
            assertNotNull(l.tier());
            assertFalse(l.name().isBlank());
            assertTrue(l.missions().size() >= 1);
        }
        assertTrue(lines.get("selv_exp1").premium());
        assertTrue(lines.get("isle_wyverns").premium());
        assertFalse(lines.get("varden_smug1").premium());
        PlayerCharacter pc = hero(Vocation.KNIGHT, 500);
        GameSession s = session(pc);
        QuestSystem qs = s.quests();
        turnInChain(pc, "mill", qs);
        List<QuestJournal.Entry> log = s.questJournal();
        assertEquals(QuestJournal.Status.DONE, log.get(log.size() - 1).status(), "completed lines are last");
        assertTrue(log.stream().filter(e -> e.line().key().equals("mill_m1")).allMatch(e -> e.status() == QuestJournal.Status.DONE && e.current() < 0));
    }

    @Test
    void extremeQuestDoorNeedsAParty() {
        Npc npc = npcs.get("crown_isolde");
        pl.fawizja.pixel.world.QuestGate gate = null;
        for (int dx = 1; dx <= 12 && gate == null; dx++) {
            gate = world.questGateAt(new Position(npc.position().x() + dx, npc.position().y(), 0));
        }
        assertNotNull(gate, "crown entrance has a party gate");
        assertEquals(pl.fawizja.pixel.world.QuestGate.Kind.PARTY, gate.kind());
        assertEquals(3, gate.partySize());
        assertTrue(world.levelDoorAt(gate.at()) >= 270, "the party door is also a level door");
        PlayerCharacter pc = hero(Vocation.KNIGHT, 300);
        assertFalse(gate.passes(pc), "alone the gate stays shut");
        pc.setPartyOnline(2);
        assertFalse(gate.passes(pc));
        pc.setPartyOnline(3);
        assertTrue(gate.passes(pc));
        assertThrows(IllegalArgumentException.class, () -> new pl.fawizja.pixel.world.QuestGate(new Position(1, 1, 0), "party:1"));
        for (Language l : Language.values()) {
            Messages.setLanguage(l);
            assertFalse(Messages.get("star.gate_party", "3").startsWith("star.gate_party"), "gate text in " + l);
        }
    }
}
