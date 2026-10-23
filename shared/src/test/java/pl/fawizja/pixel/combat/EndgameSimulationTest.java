package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Poziomy 230..1000: symulacja walki z leczeniem (czar leczacy co 1 s, gdy HP < 55%, tylko z many
 * startowej - bez regeneracji i mikstur, czyli pesymistycznie). Dla kazdego progu i kazdej profesji:
 * zwykle potwory progu sa do pokonania w rozsadnym czasie, boss wymaga druzyny, a postac z progu
 * nizej nie "przeskoczy" do nastepnego.
 */
class EndgameSimulationTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final SpellRegistry SPELLS = SpellRegistry.loadDefault(TestData.ITEMS);
    static final int FIGHTS = 200;

    /** Prog: poziom, typowe umiejetnosci, sprzet (bron dla P, D, S, M, K), potwory, boss. */
    record Tier(String name, int level, int skill, int magic, int shielding, int[] weapons, int[] armor, int shield,
                String[] monsters, String boss) {}

    static final Tier[] TIERS = {
        new Tier("Knieje i Wyzyny", 320, 95, 75, 85, new int[] {92, 94, 93, 95, 169}, new int[] {104, 105, 106}, 107,
                new String[] {"cursed_treant", "wraith_knight", "fire_elemental", "lava_golem"}, "ignaroth"),
        new Tier("Lodowe Szczyty", 500, 110, 90, 100, new int[] {96, 98, 97, 99, 172}, new int[] {108, 109, 110}, 111,
                new String[] {"frost_giant", "storm_harpy", "ice_dragon"}, "hrimgar"),
        new Tier("Pustkowia Cienia", 680, 120, 100, 110, new int[] {100, 102, 101, 103, 174}, new int[] {112, 113, 114}, 115,
                new String[] {"shadow_stalker", "sand_titan", "void_priest"}, "khazuul"),
        new Tier("Otchlan", 900, 128, 112, 118, new int[] {117, 119, 118, 120, 176}, new int[] {112, 116, 114}, 115,
                new String[] {"abyssal_demon", "bone_dragon", "abyss_lord"}, "morgath"),
    };

    record Result(double winRate, double avgSeconds, double manaShare) {}

    static PlayerCharacter build(Vocation v, Tier t) {
        int w = t.weapons()[v.ordinal()];
        int[] gear = (v == Vocation.PALADIN || v == Vocation.MONK || v == Vocation.KNIGHT)
                ? new int[] {t.armor()[0], t.armor()[1], t.armor()[2], t.shield()} : t.armor();
        int weaponSkill = (v == Vocation.DRUID || v == Vocation.SORCERER) ? 30 : t.skill();
        int magic = (v == Vocation.DRUID || v == Vocation.SORCERER) ? t.magic() : t.magic() / 4;
        return CombatSimulationTest.build(v, t.level(), weaponSkill, magic, t.shielding(), w, gear);
    }

    static SpellType bestHeal(PlayerCharacter pc) {
        SpellType best = null;
        for (SpellType s : SPELLS.forVocation(pc.vocation())) {
            if (s.kind() != SpellKind.HEAL_SELF || pc.level() < s.minLevel()) continue;
            if (best == null || SpellSystem.baseValue(s, pc) > SpellSystem.baseValue(best, pc)) best = s;
        }
        return best;
    }

    static Result fight(PlayerCharacter pc, MonsterType m, long seed) {
        Random rng = new Random(seed);
        AttackProfile ap = CombatFormulas.attackProfile(pc, TestData.ITEMS);
        SpellType heal = bestHeal(pc);
        double healBase = heal == null ? 0 : SpellSystem.baseValue(heal, pc);
        int wins = 0;
        long totalMs = 0;
        long manaUsed = 0;
        for (int f = 0; f < FIGHTS; f++) {
            int hp = pc.maxHealth(), mana = pc.maxMana(), mhp = m.maxHealth();
            boolean bossHealed = false;
            long tPlayer = 0, tMonster = 500, tHeal = 0, now = 0;
            while (hp > 0 && mhp > 0 && now < 600_000) {
                long next = Math.min(tPlayer, Math.min(tMonster, tHeal));
                now = next;
                if (next == tHeal) {
                    if (heal != null && hp < pc.maxHealth() * 0.55 && mana >= heal.mana()) {
                        hp = Math.min(pc.maxHealth(), hp + (int) (healBase * (SpellSystem.MIN_ROLL
                                + rng.nextDouble() * (SpellSystem.MAX_ROLL - SpellSystem.MIN_ROLL))));
                        mana -= heal.mana();
                        manaUsed += heal.mana();
                    }
                    tHeal += heal == null ? 600_000 : heal.cooldownMs();
                } else if (next == tPlayer) {
                    mhp -= CombatFormulas.mitigateOnMonster(m, CombatFormulas.roll(ap.maxDamage(), rng), ap.type(), rng);
                    if (m.boss() && !bossHealed && mhp > 0 && mhp < m.maxHealth() * 0.3) {
                        mhp += m.healAmount();
                        bossHealed = true;
                    }
                    tPlayer += ap.intervalMs();
                } else {
                    int raw = CombatFormulas.rollBetween(m.attackMin(), m.attackMax(), rng);
                    hp -= CombatFormulas.mitigateOnPlayer(pc, raw, m.damageType(), false, rng);
                    tMonster += m.attackIntervalMs();
                }
            }
            if (mhp <= 0) wins++;
            totalMs += now;
        }
        return new Result(wins / (double) FIGHTS, totalMs / 1000.0 / FIGHTS,
                manaUsed / (double) FIGHTS / Math.max(1, pc.maxMana()));
    }

    @Test
    void everyVocationClearsItsTierInReasonableTime() {
        StringBuilder report = new StringBuilder("\n");
        for (Tier t : TIERS) {
            for (Vocation v : Vocation.values()) {
                PlayerCharacter pc = build(v, t);
                for (String key : t.monsters()) {
                    MonsterType m = REG.get(key);
                    Result r = fight(pc, m, key.hashCode() + v.ordinal());
                    report.append(String.format("L%-4d %-8s vs %-15s win %5.1f%% t %5.1fs mana %4.0f%%%n",
                            t.level(), v, key, r.winRate() * 100, r.avgSeconds(), r.manaShare() * 100));
                    assertTrue(r.winRate() >= 0.95, t.name() + ": " + v + " vs " + key + " " + r);
                    assertTrue(r.avgSeconds() <= 90, t.name() + ": " + v + " vs " + key + " too slow " + r);
                    assertTrue(r.manaShare() <= 0.6, t.name() + ": " + v + " vs " + key + " mana " + r);
                }
            }
        }
        System.out.println(report);
    }

    @Test
    void bossesNeedATeam() {
        for (Tier t : TIERS) {
            for (Vocation v : Vocation.values()) {
                Result r = fight(build(v, t), REG.get(t.boss()), 99);
                assertTrue(r.winRate() <= 0.10, t.name() + ": " + v + " solos " + t.boss() + " " + r);
            }
        }
    }

    @Test
    void youCannotSkipATier() {
        for (int i = 0; i + 1 < TIERS.length; i++) {
            Tier lower = TIERS[i], upper = TIERS[i + 1];
            MonsterType hardest = null;
            for (String k : upper.monsters()) if (hardest == null || REG.get(k).level() > hardest.level()) hardest = REG.get(k);
            for (Vocation v : Vocation.values()) {
                Result r = fight(build(v, lower), hardest, 7);
                assertTrue(r.winRate() <= 0.5 || r.avgSeconds() > 120,
                        lower.name() + " " + v + " already farms " + hardest.key() + " " + r);
            }
        }
    }

    @Test
    void aNewCharacterDiesInstantlyInTheEndgame() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = TestData.newCharacter(v);
            for (Tier t : TIERS) {
                for (String k : t.monsters()) assertEquals(0.0, fight(pc, REG.get(k), 3).winRate(), v + " vs " + k);
            }
        }
    }
}
