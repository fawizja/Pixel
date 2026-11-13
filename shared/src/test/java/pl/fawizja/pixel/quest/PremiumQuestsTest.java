package pl.fawizja.pixel.quest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.QuestGate;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Siedemnascie nowych linii premium (tools/mapgen/premium_quests.py): liczby, bramy, dzwignie, wykluczenia, nagrody. */
class PremiumQuestsTest {

    static final List<String> KEYS = List.of("drown", "pit", "oakgrove", "fleet", "toll", "pendulum", "cells", "sunorder", "leaforder",
            "seals", "sandrite", "throne", "quarters", "ember", "trials", "storm", "mirror");

    static WorldMap world;
    static NpcRegistry npcs;
    static QuestRegistry quests;
    static QuestLines lines;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        MonsterRegistry monsters = MonsterRegistry.loadDefault(TestData.ITEMS);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, monsters);
        lines = QuestLines.loadDefault(quests);
    }

    private static PlayerCharacter hero(Vocation v, int level) {
        PlayerCharacter pc = TestData.newCharacter(v);
        pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        return pc;
    }

    @Test
    void thereAreAtLeastFiftyPremiumAndFiftyFreeLines() {
        long premium = lines.all().stream().filter(QuestLines.Line::premium).count();
        long free = lines.all().stream().filter(l -> !l.premium()).count();
        assertTrue(premium >= 50, "premium lines: " + premium);
        assertTrue(free >= 50, "free lines: " + free);
        for (String k : KEYS) {
            QuestLines.Line l = lines.get(k + "_m1");
            assertNotNull(l, k);
            assertTrue(l.premium(), k);
            assertTrue(l.missions().size() >= 3 && l.missions().size() <= 6, k + " missions " + l.missions().size());
            assertTrue(l.minLevel() >= 68, k);
        }
    }

    @Test
    void giversAreEnglishNamedGuidesInSafeYardsAndLinesAreChained() {
        for (String k : KEYS) {
            QuestType m1 = quests.get(k + "_m1");
            Npc g = npcs.get(m1.giver());
            assertNotNull(g, k);
            assertTrue(g.name().matches("[A-Z][A-Za-z'-]+( [A-Z][A-Za-z'-]+)*"), g.name());
            assertTrue(world.isProtectionZone(g.position()), k + " giver in a protected yard");
            assertFalse(g.greeting().isBlank());
            for (QuestType q : lines.get(k + "_m1").missions()) assertEquals(m1.giver(), q.giver(), q.key());
        }
        // powiazane linie: kazda poza pierwszymi ma poprzednika w innej linii
        int linked = 0;
        for (String k : KEYS) if (!quests.prereqs(k + "_m1").isEmpty()) linked++;
        assertEquals(KEYS.size(), linked);
    }

    @Test
    void factionOrdersExcludeEachOther() {
        QuestSystem qs = new QuestSystem(quests, TestData.ITEMS);
        PlayerCharacter pc = hero(Vocation.KNIGHT, 400);
        for (String pre : quests.prereqs("sunorder_m1")) if (!pre.startsWith("!")) pc.quests().restore(pre, true, 0);
        for (String pre : quests.prereqs("leaforder_m1")) if (!pre.startsWith("!")) pc.quests().restore(pre, true, 0);
        QuestType a = quests.get("sunorder_m1"), b = quests.get("leaforder_m1");
        assertTrue(qs.unlocked(pc, a));
        assertTrue(qs.unlocked(pc, b));
        assertEquals(QuestResult.ACCEPTED, qs.interact(pc, a));
        assertFalse(qs.unlocked(pc, b), "after joining the Sun Order the Leaf Order is closed");
    }

    @Test
    void gatesAreCompoundLeversArePullableAndPartyGatesHaveTwoToFivePlayers() {
        int party = 0, levers = 0, boss = 0, quest = 0;
        Set<Integer> sizes = new HashSet<>();
        for (QuestGate g : world.questGates().values()) {
            for (QuestGate part : g.parts()) {
                switch (part.kind()) {
                    case PARTY -> { party++; sizes.add(part.partySize()); assertTrue(part.partySize() >= 2 && part.partySize() <= 5); }
                    case BOSS -> boss++;
                    case QUEST -> quest++;
                    case FLAG -> { levers++; assertTrue(world.leverFlags().containsValue(part.target()), "lever for " + part.target()); }
                }
            }
        }
        assertTrue(party >= 8, "party gates " + party);
        assertTrue(sizes.containsAll(Set.of(2, 3, 4, 5)), "party sizes " + sizes);
        assertTrue(levers >= 10 && boss >= 20 && quest >= 5, levers + "/" + boss + "/" + quest);
        // zlozona brama: wymog bossa i dzwigni naraz
        PlayerCharacter pc = hero(Vocation.KNIGHT, 100);
        QuestGate compound = new QuestGate(new Position(1, 1, 0), "boss:wolf+flag:lv_test");
        assertFalse(compound.passes(pc));
        pc.setQuestFlag(QuestGate.bossFlag("wolf"));
        assertFalse(compound.passes(pc), "still needs the lever");
        pc.setQuestFlag("lv_test");
        assertTrue(compound.passes(pc));
        PlayerCharacter fresh = hero(Vocation.KNIGHT, 100);
        assertEquals("boss:" + "wolf", "boss:" + compound.firstUnmet(fresh).target());
    }

    @Test
    void everyLineHasAChoiceVaultForEveryVocationReachableFromItsTown() {
        Map<Integer, BitSet> reach = TreasureChests.reachable(world, world.towns().stream().map(t -> t.temple()).toList());
        for (String k : KEYS) {
            List<WorldMap.RewardChoice> chests = world.rewardChoices().stream().filter(r -> r.group().equals(k)).toList();
            assertTrue(chests.size() >= 8, k + " chests " + chests.size());
            for (WorldMap.RewardChoice r : chests) {
                BitSet b = reach.get(r.position().z());
                assertTrue(b != null && b.get(r.position().y() * world.width() + r.position().x()) || neighbourReached(reach, r.position()),
                        k + " chest reachable " + r.position());
            }
            for (Vocation v : Vocation.values()) {
                boolean any = chests.stream().anyMatch(r -> TestData.ITEMS.byKey(r.itemKey()).usableBy(v));
                assertTrue(any, k + " has a chest for " + v);
            }
        }
    }

    private static boolean neighbourReached(Map<Integer, BitSet> r, Position p) {
        BitSet b = r.get(p.z());
        if (b == null) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (b.get((p.y() + dy) * world.width() + p.x() + dx)) return true;
        return false;
    }
}
