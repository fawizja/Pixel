package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.magic.MonkRole;
import pl.fawizja.pixel.magic.SpellGroup;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Harmony;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Mnich z harmonia wobec rycerza na tych samych polowaniach (poziomy 8, 30, 100, 200): postac z umiejetnosciami
 * typowymi dla poziomu (CharacterFactory, jak HuntBalanceSimulationTest) i najlepszym sprzetem poziomu, walka 1 na 1
 * z leczeniem (mikstury i czar leczacy jak w HuntBalanceSimulationTest).
 * <ul>
 *   <li>rycerz: co 2 s najmocniejszy gotowy czar ataku;</li>
 *   <li>mnich jak w Tibii: czary budujace, a przy 5 punktach (albo gdy zaden builder nie jest gotowy, a sa punkty)
 *       najmocniejszy gotowy czar wydajacy; od poziomu 20 cnota harmonii; mnich gra sam, wiec jest spokojny.
 *       Leczenie z harmonii przy kazdym zbudowaniu i wydaniu.</li>
 * </ul>
 * W Tibii mnich poluje mniej wiecej tak szybko jak rycerz - tu najwyzej 1,5 raza wolniej na kazdym poziomie.
 */
class MonkBalanceSimulationTest {

    static final int FIGHTS = 150;
    static final int[] LEVELS = {8, 30, 100, 200};
    /** Polowania poziomu: potwor poziomu i potwory krainy z HuntBalanceSimulationTest. */
    static final Map<Integer, String[]> HUNTS = Map.of(
            8, new String[] {"wolf", "giant_rat", "forest_spider"},
            30, new String[] {"orc_warrior", "boar", "quarry_brute", "crocodile"},
            100, new String[] {"frost_witch", "jungle_serpent", "bog_wraith", "gilded_beetle"},
            200, new String[] {"stone_sentinel", "sand_gargoyle", "temple_guardian", "blade_warden"});

    record Result(double win, double killS, double takenPerKill) {}

    static PlayerCharacter build(Vocation v, int level) {
        PlayerCharacter pc = HuntBalanceSimulationTest.FACTORY.create("Sim", v, new Position(1, 1, 0), level);
        HuntBalanceSimulationTest.equip(pc);
        pc.heal(Integer.MAX_VALUE);
        if (v == Vocation.MONK && level >= 20) pc.harmony().setVirtue(Harmony.Virtue.HARMONY);
        return pc;
    }

    static List<SpellType> attacks(PlayerCharacter pc) {
        List<SpellType> out = new ArrayList<>();
        for (SpellType s : HuntBalanceSimulationTest.SPELLS.forVocation(pc.vocation())) {
            if ((s.kind() == SpellKind.ATTACK_TARGET || s.kind() == SpellKind.ATTACK_AREA) && pc.level() >= s.minLevel()) out.add(s);
        }
        return out;
    }

    static Result fight(PlayerCharacter pc, MonsterType m, long seed) {
        Random rng = new Random(seed);
        pc.setFightMode(FightMode.BALANCED);
        AttackProfile ap = CombatFormulas.attackProfile(pc, TestData.ITEMS);
        List<SpellType> spells = attacks(pc);
        SpellType heal = HuntBalanceSimulationTest.bestHeal(pc);
        boolean monk = pc.vocation() == Vocation.MONK;
        int wins = 0;
        long totalMs = 0, taken = 0;
        for (int f = 0; f < FIGHTS; f++) {
            int hp = pc.maxHealth(), mhp = m.maxHealth(), mp = pc.maxMana(), harmony = 0;
            Map<String, Long> ready = new HashMap<>();
            long tHit = 0, tMonster = 600, tSpell = 0, tHeal = 0, now = 0;
            while (hp > 0 && mhp > 0 && now < 300_000) {
                now = Math.min(Math.min(tHit, tMonster), Math.min(tSpell, tHeal));
                if (now == tHeal) {
                    if (hp < pc.maxHealth() * 0.6) {
                        int h = HuntBalanceSimulationTest.potionHeal(pc, rng);
                        if (heal != null && hp < pc.maxHealth() * 0.5 && mp >= heal.mana()) {
                            h += (int) Math.round(SpellSystem.baseValue(heal, pc) * (0.85 + rng.nextDouble() * 0.3));
                            mp -= heal.mana();
                        }
                        hp = Math.min(pc.maxHealth(), hp + h);
                    } else if (mp < pc.maxMana() * 0.6) {
                        mp = Math.min(pc.maxMana(), mp + HuntBalanceSimulationTest.potionMana(pc, rng));
                    }
                    tHeal += 1000;
                } else if (now == tSpell) {
                    SpellType pick = monk ? monkPick(spells, pc, m, harmony, ready, now, mp) : bestReady(spells, pc, m, ready, now, mp, null);
                    if (pick != null) {
                        double v = SpellSystem.baseValue(pick, pc);
                        int h0 = harmony;
                        if (pick.monkRole() == MonkRole.SPENDER) v *= Harmony.bonus(harmony, pc.harmony().virtue(), true);
                        double roll = pick.monkFormula() ? 0.9 + rng.nextDouble() * 0.2 : 0.85 + rng.nextDouble() * 0.3;
                        mhp -= CombatFormulas.mitigateOnMonster(m, (int) Math.round(v * roll), pick.damageType(), rng);
                        ready.put(pick.key(), now + pick.cooldownMs());
                        mp -= pick.mana();
                        if (pick.monkRole() == MonkRole.SPENDER) {
                            hp = Math.min(pc.maxHealth(), hp + harmonyHeal(pc, h0, rng));
                            for (SpellType b : spells) {
                                if (b.monkRole() == MonkRole.BUILDER && ready.containsKey(b.key())) {
                                    ready.put(b.key(), ready.get(b.key()) - Harmony.COOLDOWN_CUT_PER_POINT_MS * h0);
                                }
                            }
                            harmony = pc.harmony().virtue() == Harmony.Virtue.HARMONY ? 1 : 0;
                            if (harmony == 1) hp = Math.min(pc.maxHealth(), hp + harmonyHeal(pc, 1, rng));
                        } else if (pick.monkRole() == MonkRole.BUILDER && harmony < Harmony.MAX) {
                            harmony++;
                            hp = Math.min(pc.maxHealth(), hp + harmonyHeal(pc, 1, rng));
                        }
                    }
                    tSpell += SpellGroup.ATTACK.groupCooldownMs;
                } else if (now == tHit) {
                    mhp -= CombatFormulas.mitigateOnMonster(m, CombatFormulas.roll(ap.maxDamage(), rng), ap.type(), rng);
                    tHit += ap.intervalMs();
                } else {
                    int raw = CombatFormulas.rollBetween(m.attackMin(), m.attackMax(), rng);
                    int d = CombatFormulas.mitigateOnPlayer(pc, raw, m.damageType(), true, rng);
                    hp -= d;
                    taken += d;
                    tMonster += m.attackIntervalMs();
                }
            }
            if (mhp <= 0) wins++;
            totalMs += now;
        }
        return new Result(wins / (double) FIGHTS, totalMs / 1000.0 / FIGHTS, taken / (double) FIGHTS);
    }

    static int harmonyHeal(PlayerCharacter pc, int charges, Random rng) {
        return CombatFormulas.rollBetween(Harmony.healMin(pc.level(), charges), Harmony.healMax(pc.level(), charges), rng);
    }

    static SpellType bestReady(List<SpellType> spells, PlayerCharacter pc, MonsterType m, Map<String, Long> ready, long now, int mp,
                               MonkRole role) {
        SpellType pick = null;
        double best = 0;
        for (SpellType s : spells) {
            if (role != null && s.monkRole() != role) continue;
            if (ready.getOrDefault(s.key(), 0L) > now || mp < s.mana()) continue;
            double d = SpellSystem.baseValue(s, pc) * (100 - m.resistance(s.damageType())) / 100.0;
            if (pick == null || d > best) {
                pick = s;
                best = d;
            }
        }
        return pick;
    }

    /** Rotacja mnicha: przy pelnej harmonii spender, inaczej builder; gdy builderow brak - spender z tym, co jest. */
    static SpellType monkPick(List<SpellType> spells, PlayerCharacter pc, MonsterType m, int harmony, Map<String, Long> ready,
                              long now, int mp) {
        if (harmony >= Harmony.MAX) {
            SpellType s = bestReady(spells, pc, m, ready, now, mp, MonkRole.SPENDER);
            if (s != null) return s;
        }
        SpellType b = bestReady(spells, pc, m, ready, now, mp, MonkRole.BUILDER);
        if (b != null) return b;
        return harmony > 0 ? bestReady(spells, pc, m, ready, now, mp, MonkRole.SPENDER) : null;
    }

    @Test
    void monkHuntsRoughlyAsFastAsAKnight() {
        StringBuilder sb = new StringBuilder("\n| poz. | potwor | rycerz: wygr. / zabicie s / obr. na zab. | mnich: wygr. / zabicie s / obr. na zab. | mnich/rycerz czas |\n");
        List<String> fails = new ArrayList<>();
        for (int level : LEVELS) {
            PlayerCharacter knight = build(Vocation.KNIGHT, level), monk = build(Vocation.MONK, level);
            double kSum = 0, mSum = 0;
            for (String key : HUNTS.get(level)) {
                MonsterType m = HuntBalanceSimulationTest.REG.get(key);
                Result k = fight(knight, m, level * 7L + key.hashCode());
                Result mo = fight(monk, m, level * 7L + key.hashCode());
                kSum += k.killS();
                mSum += mo.killS();
                sb.append(String.format("| %d | %s | %.2f / %.1f / %.0f | %.2f / %.1f / %.0f | %.2f |%n", level, key,
                        k.win(), k.killS(), k.takenPerKill(), mo.win(), mo.killS(), mo.takenPerKill(), mo.killS() / k.killS()));
                if (mo.win() < k.win() - 0.05) fails.add("L" + level + " " + key + ": monk wins " + mo.win() + " vs knight " + k.win());
            }
            double ratio = mSum / kSum;
            sb.append(String.format("| %d | srednio | | | %.2f |%n", level, ratio));
            if (ratio > 1.5) fails.add("L" + level + ": monk " + String.format("%.2f", ratio) + "x slower than the knight");
        }
        System.out.println(sb);
        assertTrue(fails.isEmpty(), String.join("\n", fails) + sb);
    }
}
