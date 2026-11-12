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
        assertEquals(200 * 80, Blessing.TWIST.price(100));
        assertEquals(50_000, Blessing.TWIST.price(300));
        assertEquals(Blessing.WISDOM.price(100), Balance.blessingPrice(100));
        assertEquals(6, Blessing.values().length, "five regular ones and Twist of Fate (no mountain blessings)");
    }

    @Test
    void everyBlessingCutsTheBaseLossByEightPercentAndFiveGiveFortyPercent() {
        long exp = Balance.experienceForLevel(100);
        double base = Balance.deathLossFraction(100, exp, 0, false);
        assertEquals(4.5, base * 100, 0.2, "Canary formula: about 4.5% at level 100");
        for (int n = 1; n <= 5; n++) {
            assertEquals(base * (100 - 8 * n) / 100.0, Balance.deathLossFraction(100, exp, n, false), 1e-12, n + " blessings");
        }
        assertEquals(base * 0.6, Balance.deathLossFraction(100, exp, 5, false), 1e-12, "five blessings: -40%");
        assertEquals(base * 0.7, Balance.deathLossFraction(100, exp, 0, true), 1e-12, "promotion: -30%");
        assertEquals(base * 0.3, Balance.deathLossFraction(100, exp, 5, true), 1e-12, "five blessings and promotion: -70%");
        assertEquals(0.10, Balance.deathLossFraction(12, Balance.experienceForLevel(12), 0, false), 1e-12, "below level 24: 10%");
        assertEquals(0.05, Balance.deathLossFraction(12, Balance.experienceForLevel(12), 5, false), 1e-12, "below level 24 five blessings halve it");
        assertTrue(Balance.deathLossFraction(300, Balance.experienceForLevel(300), 0, false) < base, "falls with level");
        long lost0 = exp - Balance.experienceAfterDeath(exp, 8, 0, 100);
        long lost5 = exp - Balance.experienceAfterDeath(exp, 8, 5, 100);
        assertEquals(lost0 * 0.6, lost5, 2.0);
    }

    @Test
    void adventurerBlessingCoversLowLevelsForFreeAndIsNeverUsedUp() {
        PlayerCharacter pc = level(20);
        assertTrue(pc.adventurerBlessing());
        assertEquals(5, pc.blessingCount());
        int held = pc.applyDeathPenalty(8, 0, false, false);
        assertEquals(5, held);
        assertEquals(5, pc.blessingCount(), "still protected after the death");
        assertEquals(0, pc.blessingMask());
    }

    @Test
    void aPveDeathUsesUpAllRegularBlessingsButTwistOfFate() {
        PlayerCharacter pc = level(60);
        for (Blessing b : Blessing.values()) pc.addBlessing(b);
        assertEquals(5, pc.blessingCount());
        long exp = pc.experience();
        double f = Balance.deathLossFraction(60, exp, 5, false);
        assertEquals(5, pc.applyDeathPenalty(8, 0, false, false));
        assertEquals(exp - (long) Math.ceil(exp * f), pc.experience());
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
        double f = Balance.deathLossFraction(pc.level(), before, 5, false);
        cs.diedFromPlayer("Kuba", false, 1000);
        assertEquals(before - (long) Math.ceil(before * f), pc.experience(), "five blessings: 40% less than without");
        assertEquals(5, pc.blessingCount());
        assertFalse(pc.boughtBlessing(Blessing.TWIST));
    }

    private static GameSession atMonk(PlayerCharacter pc, String key) throws Exception {
        WorldMap world = TestData.world();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS)));
        Npc monk = npcs.get(key);
        assertNotNull(monk, key);
        assertEquals(NpcRole.BLESSER, monk.role());
        s.teleport(monk.position().translate(0, 1), 0);
        if (s.npcNearby() != monk) s.teleport(monk.position().translate(1, 0), 0);
        assertEquals(monk, s.npcNearby());
        return s;
    }

    @Test
    void everyMonkSellsExactlyOneBlessingAndTownPriestsSellNone() throws Exception {
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, TestData.world(), ShopRegistry.loadDefault(TestData.ITEMS));
        for (Blessing b : Blessing.values()) {
            Npc monk = npcs.get(b.sellerKey());
            assertNotNull(monk, b.key());
            assertEquals(NpcRole.BLESSER, monk.role());
            assertSame(b, Blessing.soldBy(monk.key()));
        }
        long monks = npcs.all().stream().filter(n -> n.role() == NpcRole.BLESSER).count();
        assertEquals(6, monks);
        assertEquals(3, java.util.Arrays.stream(Blessing.values()).filter(b -> !b.premium()).count(), "three for free accounts");
        assertEquals(3, java.util.Arrays.stream(Blessing.values()).filter(Blessing::premium).count(), "three for premium accounts");
    }

    @Test
    void aMonkSellsHisBlessingForCoinsAndOnlyThatOne() throws Exception {
        PlayerCharacter pc = rich(level(60));
        GameSession s = atMonk(pc, "varden_blesser");
        long gold = s.gold();
        long price = Blessing.SHIELD.price(60);
        assertSame(Blessing.SHIELD, s.blessingOfNearbyMonk());
        Actions.apply(s, "npc:bless:wisdom", 0);
        assertFalse(pc.boughtBlessing(Blessing.WISDOM), "the Varden monk does not sell wisdom");
        assertEquals(gold, s.gold());
        assertTrue(Actions.apply(s, "npc:bless:shield", 0));
        assertTrue(pc.boughtBlessing(Blessing.SHIELD));
        assertEquals(gold - price, s.gold());
        assertFalse(s.blessBuy("shield", 0), "no second copy");
        assertEquals(gold - price, s.gold());
    }

    @Test
    void premiumMonksNeedPremiumAndTwistNeedsAnotherBlessing() throws Exception {
        PlayerCharacter pc = rich(level(60));
        GameSession s = atMonk(pc, "valmir_blesser");
        assertNotNull(s.blessingBlocked(Blessing.SPARK), "free account");
        assertFalse(s.blessBuy("spark", 0));
        pc.setPremium(true);
        assertNull(s.blessingBlocked(Blessing.SPARK));
        assertTrue(s.blessBuy("spark", 0));
        GameSession n = atMonk(pc, "nerys_blesser");
        assertNull(n.blessingBlocked(Blessing.TWIST), "another blessing is already owned");
        PlayerCharacter fresh = rich(level(60));
        fresh.setPremium(true);
        GameSession n2 = atMonk(fresh, "nerys_blesser");
        assertNotNull(n2.blessingBlocked(Blessing.TWIST));
        assertFalse(n2.blessBuy("twist", 0), "first any other blessing");
        assertTrue(n.blessBuy("twist", 0));
        assertTrue(pc.boughtBlessing(Blessing.TWIST));
    }

    @Test
    void theMonkTalksLikeInTibiaBlessingPriceYes() throws Exception {
        PlayerCharacter pc = rich(level(100));
        GameSession s = atMonk(pc, "qas_blesser");
        long gold = s.gold();
        String offer = s.askNpc("błogosławieństwo", 1);
        assertTrue(offer.contains(Blessing.FIRE.label()) && offer.matches(".*16.000 gp.*"), offer);
        assertEquals(gold, s.gold(), "nothing is paid before yes");
        String no = s.askNpc("nie", 2);
        assertNotNull(no);
        assertFalse(pc.boughtBlessing(Blessing.FIRE));
        s.askNpc("tak", 3);
        assertFalse(pc.boughtBlessing(Blessing.FIRE), "yes without an offer buys nothing");
        s.askNpc("blessing", 4);
        String done = s.askNpc("tak", 5);
        assertTrue(pc.boughtBlessing(Blessing.FIRE), done);
        assertEquals(gold - 16_000, s.gold());
        String where = s.askNpc("gdzie", 6);
        for (Blessing b : Blessing.values()) if (b != Blessing.FIRE) assertTrue(where.contains(b.label()), b.label() + ": " + where);
    }

    @Test
    void lowLevelsAreCoveredAndCannotBuyRegularOnes() throws Exception {
        PlayerCharacter pc = rich(level(10));
        GameSession s = atMonk(pc, "varden_blesser");
        long gold = s.gold();
        assertFalse(s.blessBuy("shield", 0));
        assertEquals(gold, s.gold());
        assertTrue(BlessingStatus.summary(pc).startsWith("Poszukiwacza"));
    }

    @Test
    void notEnoughCoinsMeansNoBlessing() throws Exception {
        PlayerCharacter pc = level(200);
        pc.backpack().clear();
        GameSession s = atMonk(pc, "varden_blesser");
        assertFalse(s.blessBuy("shield", 0));
        assertEquals(0, pc.blessingCount());
    }

    @Test
    void theSaveKeepsBlessingsAndIgnoresUnknownBits() throws Exception {
        PlayerCharacter pc = level(60);
        pc.addBlessing(Blessing.FIRE);
        pc.addBlessing(Blessing.TWIST);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(back.boughtBlessing(Blessing.FIRE) && back.boughtBlessing(Blessing.TWIST));
        assertFalse(back.boughtBlessing(Blessing.WISDOM));
        assertEquals(pc.blessingMask(), back.blessingMask());
        assertEquals(0, codec.decode(codec.encode(level(60))).blessingMask());
        back.setBlessingMask(0xFF);
        assertEquals(0x3F, back.blessingMask(), "the two old mountain bits are dropped");
    }

    @Test
    void statusWindowIsInformationalAndSaysWhereToBuyEachBlessing() throws Exception {
        PlayerCharacter pc = level(60);
        pc.addBlessing(Blessing.FIRE);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, TestData.world(), ShopRegistry.loadDefault(TestData.ITEMS));
        List<String[]> rows = BlessingStatus.rows(pc, npcs);
        StringBuilder all = new StringBuilder();
        for (String[] r : rows) all.append(r[0]).append('=').append(r[1]).append('\n');
        for (Blessing b : Blessing.values()) assertTrue(all.toString().contains(b.label()), b.label());
        assertTrue(all.toString().contains("Ogień Słońc (darmowe)=masz"));
        Npc wisdom = npcs.get("selv_blesser");
        assertTrue(all.toString().contains("Mądrość Samotności (premium)=brak"));
        assertTrue(all.toString().contains(wisdom.name()) && all.toString().contains(wisdom.position().x() + ", " + wisdom.position().y()), all.toString());
        assertTrue(all.toString().contains("premium"));
        assertEquals("1 z 5 (-8% straty)", BlessingStatus.summary(pc));
    }
}
