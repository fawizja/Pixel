package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.Blessing;
import pl.fawizja.pixel.character.BlessingStatus;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

class BlessingTest {

    private static PlayerCharacter level(int lvl) {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.addExperience(Balance.experienceForLevel(lvl) - pc.experience());
        return pc;
    }

    private static PlayerCharacter rich(PlayerCharacter pc) {
        assertTrue(pl.fawizja.pixel.economy.Money.add(pc.backpack(), 300_000).ok());
        return pc;
    }

    private static void giveRegular(PlayerCharacter pc) {
        for (Blessing b : Blessing.values()) if (b.kind() == Blessing.Kind.REGULAR) pc.addBlessing(b);
    }

    @Test
    void pricesFollowTibia() {
        assertEquals(2000, Blessing.WISDOM.price(1));
        assertEquals(2000, Blessing.WISDOM.price(30));
        assertEquals(2200, Blessing.WISDOM.price(31));
        assertEquals(16_000, Blessing.WISDOM.price(100));
        assertEquals(20_000, Blessing.WISDOM.price(120));
        assertEquals(20_000 + 75 * 80, Blessing.WISDOM.price(200));
        assertEquals(260 * 80, Blessing.HEART.price(100));
        assertEquals(26_000 + 100 * 80, Blessing.BLOOD.price(200));
        assertEquals(200 * 80, Blessing.TWIST.price(100));
        assertEquals(50_000, Blessing.TWIST.price(300));
        assertEquals(Blessing.WISDOM.price(100), Balance.blessingPrice(100));
    }

    @Test
    void everyBlessingCutsTheLossByEightPercent() {
        long exp = Balance.experienceForLevel(100);
        assertEquals(exp - exp * 5 / 100, Balance.experienceAfterDeath(exp, 8, 0, 100));
        long lost0 = exp - Balance.experienceAfterDeath(exp, 8, 0, 100);
        for (int n = 1; n <= 7; n++) {
            long lost = exp - Balance.experienceAfterDeath(exp, 8, n, 100);
            assertEquals(lost0 * (100 - 8 * n) / 100.0, lost, 1.0, n + " blessings");
        }
        long lowExp = Balance.experienceForLevel(20);
        assertEquals((lowExp - Balance.experienceAfterDeath(lowExp, 8, 0, 20)) / 2.0,
                lowExp - Balance.experienceAfterDeath(lowExp, 8, 5, 20), 1.0, "below level 24 five blessings halve it");
    }

    @Test
    void adventurerBlessingCoversLowLevelsForFreeAndIsNeverUsedUp() {
        PlayerCharacter pc = level(20);
        assertTrue(pc.adventurerBlessing());
        assertEquals(5, pc.blessingCount());
        assertFalse(pc.hasBlessing(Blessing.HEART), "the mountain ones are not covered");
        int held = pc.applyDeathPenalty(8, 0, false, false);
        assertEquals(5, held);
        assertEquals(5, pc.blessingCount(), "still protected after the death");
        assertEquals(0, pc.blessingMask());
    }

    @Test
    void aPveDeathUsesUpAllBlessingsButTwistOfFate() {
        PlayerCharacter pc = level(60);
        for (Blessing b : Blessing.values()) pc.addBlessing(b);
        assertEquals(7, pc.blessingCount());
        long exp = pc.experience();
        assertEquals(7, pc.applyDeathPenalty(8, 0, false, false));
        assertEquals(exp - exp * 5 * 44 / 10_000, pc.experience());
        assertEquals(0, pc.blessingCount());
        assertTrue(pc.boughtBlessing(Blessing.TWIST), "twist of fate is only spent in PvP");
    }

    @Test
    void aPvpDeathSpendsOnlyTwistOfFateAndKeepsTheRest() {
        PlayerCharacter pc = level(60);
        giveRegular(pc);
        pc.addBlessing(Blessing.TWIST);
        pc.applyDeathPenalty(8, 0, true, false);
        assertFalse(pc.boughtBlessing(Blessing.TWIST));
        assertEquals(5, pc.blessingCount(), "regular blessings survive");
        pc.applyDeathPenalty(8, 0, true, false);
        assertEquals(0, pc.blessingCount(), "without twist the next PvP death takes them");
    }

    @Test
    void aSkulledPvpDeathIgnoresTwistOfFate() {
        PlayerCharacter pc = level(60);
        giveRegular(pc);
        pc.addBlessing(Blessing.TWIST);
        pc.applyDeathPenalty(8, 0, true, true);
        assertEquals(0, pc.blessingCount());
        assertTrue(pc.boughtBlessing(Blessing.TWIST), "red skull: the regular ones go, twist stays");
    }

    @Test
    void endToEndMonsterDeathLosesLessWhenBlessedAndConsumesTheBlessings() {
        long[] lost = new long[2];
        for (int blessed = 0; blessed < 2; blessed++) {
            PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
            pc.addExperience(Balance.experienceForLevel(80));
            if (blessed == 1) giveRegular(pc);
            pc.setPosition(new Position(12, 27, -5));
            SpawnPoint sp = new SpawnPoint("grimtooth", new Position(12, 28, -5), 0, 1, 1200);
            MonsterManager mm = new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS), List.of(sp), new Random(9));
            mm.populate(null);
            Monster boss = mm.monsters().get(0);
            EventLog log = new EventLog(50);
            CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS, mm, new GroundItems(), log, new Random(2), 8);
            long before = pc.experience();
            for (int i = 0; i < 200 && pc.position().z() == -5; i++) cs.monsterAttacks(boss, i * 1000L);
            assertNotEquals(-5, pc.position().z(), "died");
            lost[blessed] = before - pc.experience();
            assertEquals(blessed == 1 ? 5 : 0, cs.lastDeath().blessings());
            assertEquals(0, pc.blessingCount(), "used up");
            boolean said = false;
            for (EventLog.Entry e : log.latest(50)) said |= e.text().contains("Zginąłeś") && (blessed == 0 || e.text().contains("5 błogosławieństw"));
            assertTrue(said, "death message names the blessings: " + log.latest(50));
        }
        assertTrue(lost[1] > 0 && lost[1] < lost[0] * 0.65, "40% less with five blessings: " + lost[0] + " vs " + lost[1]);
    }

    @Test
    void endToEndPvpDeathKeepsRegularBlessingsWithTwist() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(Balance.experienceForLevel(80));
        giveRegular(pc);
        pc.addBlessing(Blessing.TWIST);
        CombatSystem cs = new CombatSystem(pc, TestData.varden(), TestData.ITEMS,
                new MonsterManager(TestData.varden(), MonsterRegistry.loadDefault(TestData.ITEMS), Collections.emptyList(), new Random(1)),
                new GroundItems(), new EventLog(50), new Random(1), 8);
        long before = pc.experience();
        cs.diedFromPlayer("Kuba", false, 1000);
        assertEquals(before - before * 5 * 60 / 10_000, pc.experience(), "five blessings: 3% instead of 5%");
        assertEquals(5, pc.blessingCount());
        assertFalse(pc.boughtBlessing(Blessing.TWIST));
    }

    private static GameSession atPriest(PlayerCharacter pc, String key) throws Exception {
        WorldMap world = TestData.world();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS)));
        Npc priest = npcs.get(key);
        s.teleport(priest.position().translate(0, 1), 0);
        assertEquals(priest, s.npcNearby());
        return s;
    }

    @Test
    void thePriestSellsBlessingsOneByOneOrAllAtOnce() throws Exception {
        PlayerCharacter pc = rich(level(60));
        GameSession s = atPriest(pc, "varden_priest");
        long gold = s.gold();
        long one = Blessing.SPARK.price(60);
        assertTrue(Actions.apply(s, "npc:bless:spark", 0));
        assertTrue(pc.boughtBlessing(Blessing.SPARK));
        assertEquals(gold - one, s.gold());
        assertFalse(s.priestBless("spark", 0), "no second copy");
        assertEquals(gold - one, s.gold());
        long bulk = s.bulkBlessingPrice();
        assertEquals(Math.round(4 * Blessing.WISDOM.price(60) * 1.1), bulk, "four missing, +10%");
        assertTrue(Actions.apply(s, "npc:bless", 0));
        assertEquals(5, pc.blessingCount());
        assertEquals(gold - one - bulk, s.gold());
        assertFalse(s.priestBless(0), "nothing left to buy");
    }

    @Test
    void twistOfFateNeedsAnotherBlessingAndMountainOnesOnlyInDurnkar() throws Exception {
        PlayerCharacter pc = rich(level(60));
        GameSession s = atPriest(pc, "varden_priest");
        assertFalse(s.priestBless("twist", 0), "first any other blessing");
        assertNotNull(s.blessingBlocked(Blessing.TWIST, "Varden"));
        assertTrue(s.priestBless("wisdom", 0));
        assertTrue(s.priestBless("twist", 0));
        assertTrue(pc.boughtBlessing(Blessing.TWIST));
        assertFalse(s.priestBless("heart", 0), "not sold in Varden");
        assertFalse(pc.boughtBlessing(Blessing.HEART));
        GameSession d = atPriest(pc, "durn_priest");
        assertTrue(d.priestBless("heart", 0));
        assertTrue(d.priestBless("blood", 0));
        assertEquals(3, pc.blessingCount());
    }

    @Test
    void lowLevelsAreCoveredAndCannotBuyRegularOnes() throws Exception {
        PlayerCharacter pc = rich(level(10));
        GameSession s = atPriest(pc, "varden_priest");
        long gold = s.gold();
        assertFalse(s.priestBless("fire", 0));
        assertFalse(s.priestBless(0));
        assertEquals(gold, s.gold());
        assertTrue(BlessingStatus.summary(pc).startsWith("Poszukiwacza"));
    }

    @Test
    void notEnoughCoinsMeansNoBlessing() throws Exception {
        PlayerCharacter pc = level(200);
        pc.backpack().clear();
        GameSession s = atPriest(pc, "varden_priest");
        assertFalse(s.priestBless("wisdom", 0));
        assertEquals(0, pc.blessingCount());
    }

    @Test
    void theSaveKeepsBlessingsAndOldSavesGetTheFiveRegularOnes() throws Exception {
        PlayerCharacter pc = level(60);
        pc.addBlessing(Blessing.HEART);
        pc.addBlessing(Blessing.TWIST);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(back.boughtBlessing(Blessing.HEART) && back.boughtBlessing(Blessing.TWIST));
        assertFalse(back.boughtBlessing(Blessing.WISDOM));
        assertEquals(pc.blessingMask(), back.blessingMask());
        String enc = codec.encode(level(60));
        assertEquals(0, codec.decode(enc).blessingMask());
    }

    @Test
    void statusRowsListEveryBlessing() {
        PlayerCharacter pc = level(60);
        pc.addBlessing(Blessing.FIRE);
        List<String[]> rows = BlessingStatus.rows(pc);
        StringBuilder all = new StringBuilder();
        for (String[] r : rows) all.append(r[0]).append('=').append(r[1]).append('\n');
        for (Blessing b : Blessing.values()) assertTrue(all.toString().contains(b.label()), b.label());
        assertTrue(all.toString().contains("Ogień Słońc=masz"));
        assertTrue(all.toString().contains("Mądrość Samotności=brak"));
        assertEquals("1 z 7 (-8% straty)", BlessingStatus.summary(pc));
    }
}
