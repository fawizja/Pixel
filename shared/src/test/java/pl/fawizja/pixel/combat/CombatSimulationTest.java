package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Symulacje pojedynkow "stoimy i wymieniamy ciosy" na formulach z {@link CombatFormulas}.
 * To pesymistyczny model dla profesji dystansowych (nie uciekaja), wiec jesli wygrywaja tutaj,
 * w grze poradza sobie jeszcze lepiej. Bez regeneracji i mikstur.
 */
class CombatSimulationTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final int FIGHTS = 300;

    record Result(double winRate, double avgSeconds, double dps) {}

    static Result duel(PlayerCharacter template, MonsterType m, long seed) {
        Random rng = new Random(seed);
        AttackProfile ap = CombatFormulas.attackProfile(template, TestData.ITEMS);
        int wins = 0;
        long totalMs = 0;
        long totalDamage = 0;
        long hits = 0;
        for (int f = 0; f < FIGHTS; f++) {
            int php = template.maxHealth();
            int mhp = m.maxHealth();
            long tPlayer = 0, tMonster = 500, now = 0;
            while (php > 0 && mhp > 0 && now < 600_000) {
                if (tPlayer <= tMonster) {
                    now = tPlayer;
                    int d = CombatFormulas.mitigateOnMonster(m, CombatFormulas.roll(ap.maxDamage(), rng), ap.type(), rng);
                    mhp -= d;
                    totalDamage += d;
                    hits++;
                    tPlayer += ap.intervalMs();
                } else {
                    now = tMonster;
                    int raw = CombatFormulas.rollBetween(m.attackMin(), m.attackMax(), rng);
                    php -= CombatFormulas.mitigateOnPlayer(template, raw, m.damageType(), false, rng);
                    tMonster += m.attackIntervalMs();
                }
            }
            if (mhp <= 0) wins++;
            totalMs += now;
        }
        return new Result(wins / (double) FIGHTS, totalMs / 1000.0 / FIGHTS, totalDamage * 1000.0 / Math.max(1, hits * ap.intervalMs()));
    }

    /** Postac na danym poziomie z typowymi umiejetnosciami i sprzetem (ziemia: skills.set). */
    static PlayerCharacter build(Vocation v, int level, int weaponSkill, int magic, int shielding, int weaponId, int... armorIds) {
        PlayerCharacter pc = TestData.newCharacter(v);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        pc.heal(Integer.MAX_VALUE);
        pc.skills().set(Skill.SWORD, weaponSkill, 0);
        pc.skills().set(Skill.FIST, weaponSkill, 0);
        pc.skills().set(Skill.DISTANCE, weaponSkill, 0);
        pc.skills().set(Skill.MAGIC, magic, 0);
        pc.skills().set(Skill.SHIELDING, shielding, 0);
        if (weaponId > 0) pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(weaponId, 1));
        for (int id : armorIds) pc.equipment().setDirect(TestData.ITEMS.get(id).slot(), new ItemStack(id, 1));
        return pc;
    }

    static Map<Vocation, PlayerCharacter> starters() {
        Map<Vocation, PlayerCharacter> out = new EnumMap<>(Vocation.class);
        for (Vocation v : Vocation.values()) out.put(v, TestData.newCharacter(v));
        return out;
    }

    static Map<Vocation, PlayerCharacter> midLevel() {
        Map<Vocation, PlayerCharacter> out = new EnumMap<>(Vocation.class);
        out.put(Vocation.PALADIN, build(Vocation.PALADIN, 30, 45, 8, 35, 58, 51, 52, 56));
        out.put(Vocation.DRUID, build(Vocation.DRUID, 30, 12, 40, 15, 54, 51, 56));
        out.put(Vocation.SORCERER, build(Vocation.SORCERER, 30, 12, 40, 15, 53, 51, 56));
        out.put(Vocation.MONK, build(Vocation.MONK, 30, 45, 8, 35, 55, 51, 52, 56));
        return out;
    }

    @Test
    void everyStarterBeatsBeginnerMonsters() {
        for (Map.Entry<Vocation, PlayerCharacter> e : starters().entrySet()) {
            Result wolf = duel(e.getValue(), REG.get("wolf"), 2);
            assertTrue(wolf.winRate() >= 0.99, e.getKey() + " vs wolf " + wolf);
            assertTrue(wolf.avgSeconds() <= 10, e.getKey() + " kills a wolf quickly " + wolf);
            Result bandit = duel(e.getValue(), REG.get("bandit"), 3);
            assertTrue(bandit.winRate() >= 0.9, e.getKey() + " vs bandit " + bandit);
        }
    }

    @Test
    void starterCannotSoloDeepMonstersOrBoss() {
        for (Map.Entry<Vocation, PlayerCharacter> e : starters().entrySet()) {
            assertTrue(duel(e.getValue(), REG.get("cave_troll"), 4).winRate() <= 0.05, e.getKey() + " vs troll");
            assertEquals(0.0, duel(e.getValue(), REG.get("crystal_golem"), 5).winRate(), e.getKey() + " vs golem");
            assertEquals(0.0, duel(e.getValue(), REG.get("grimtooth"), 6).winRate(), e.getKey() + " vs boss");
        }
    }

    @Test
    void midLevelCharactersHandleMidMonstersButNotTheBossAlone() {
        for (Map.Entry<Vocation, PlayerCharacter> e : midLevel().entrySet()) {
            assertTrue(duel(e.getValue(), REG.get("skeleton"), 7).winRate() >= 0.95, e.getKey() + " vs skeleton");
            assertTrue(duel(e.getValue(), REG.get("cave_troll"), 8).winRate() >= 0.95, e.getKey() + " vs troll");
            assertTrue(duel(e.getValue(), REG.get("grimtooth"), 9).winRate() <= 0.01, e.getKey() + " solo boss at 30");
        }
    }

    @Test
    void vocationDamageStaysWithinBand() {
        // bandyta nie ma odpornosci - porownanie "czystych" obrazen na atak
        double[] starter = dpsRange(starters());
        assertTrue(starter[1] / starter[0] <= 1.6, "starter dps spread " + starter[0] + ".." + starter[1]);
        double[] mid = dpsRange(midLevel());
        assertTrue(mid[1] / mid[0] <= 2.0, "level 30 wand vs weapon spread " + mid[0] + ".." + mid[1]);
    }

    private static double[] dpsRange(Map<Vocation, PlayerCharacter> chars) {
        double min = Double.MAX_VALUE, max = 0;
        for (PlayerCharacter pc : chars.values()) {
            double d = duel(pc, REG.get("bandit"), 10).dps();
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[] {min, max};
    }

    @Test
    void anHourOfWolvesDoesNotCatapultANewCharacter() {
        for (Map.Entry<Vocation, PlayerCharacter> e : starters().entrySet()) {
            Result r = duel(e.getValue(), REG.get("wolf"), 11);
            // 5 s szukania/dojscia na kazde zabicie, zero przerw na leczenie (optymistycznie)
            long kills = (long) (3600 / (r.avgSeconds() + 5));
            PlayerCharacter pc = TestData.newCharacter(e.getKey());
            for (long i = 0; i < kills; i++) pc.addExperience(Balance.experienceReward(20, 8, pc.level()));
            assertTrue(pc.level() <= 20, e.getKey() + " reached " + pc.level() + " after " + kills + " wolves");
        }
    }

    @Test
    void highLevelGainsAlmostNothingFromTrivialMonsters() {
        PlayerCharacter pc = build(Vocation.SORCERER, 300, 12, 90, 20, 53);
        long needed = Balance.experienceForLevel(301) - pc.experience();
        long hour = 0;
        for (int i = 0; i < 3600; i++) hour += Balance.experienceReward(REG.get("wolf").experience(), 8, pc.level());
        assertTrue(hour * 100 < needed, "an hour of wolves is < 1% of a level at 300: " + hour + " / " + needed);
        assertEquals(REG.get("grimtooth").experience(), Balance.experienceReward(4000, 60, 60), "on-level boss pays in full");
    }

    @Test
    void printBalanceTable() {
        StringBuilder sb = new StringBuilder("\n");
        for (String key : new String[] {"wolf", "bandit", "skeleton", "cave_troll", "crystal_golem", "grimtooth"}) {
            for (Map.Entry<Vocation, PlayerCharacter> e : starters().entrySet()) {
                Result r = duel(e.getValue(), REG.get(key), 1);
                sb.append(String.format("L8  %-8s vs %-14s win %5.1f%% t %6.1fs dps %5.1f%n", e.getKey(), key, r.winRate() * 100, r.avgSeconds(), r.dps()));
            }
            for (Map.Entry<Vocation, PlayerCharacter> e : midLevel().entrySet()) {
                Result r = duel(e.getValue(), REG.get(key), 1);
                sb.append(String.format("L30 %-8s vs %-14s win %5.1f%% t %6.1fs dps %5.1f%n", e.getKey(), key, r.winRate() * 100, r.avgSeconds(), r.dps()));
            }
        }
        System.out.println(sb);
    }
}
