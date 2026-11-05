package pl.fawizja.pixel.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.item.WeaponType;
import pl.fawizja.pixel.magic.SpellGroup;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Polowanie jak na serwerze z etapami (Balance.EXP_STAGES / SKILL_STAGES / MAGIC_STAGES, Darek 2026-11-04):
 *
 * <ol>
 *   <li><b>Typowe umiejetnosci dla poziomu</b> nie sa zgadywane, tylko wynikaja z gry: postac od poziomu 1 poluje
 *       na potwora swojego poziomu ({@link #huntTarget}), kazdy cios to {@link Balance#HUNT_TRY_FACTOR} prob broni,
 *       kazdy cios potwora do odparcia {@link Balance#BLOCK_TRY_FACTOR} prob tarczy, kazdy punkt wydanej many
 *       HUNT_TRY_FACTOR prob magii (wszystko z mnoznikiem etapu, jak w grze), a czas na poziomie to brakujace
 *       doswiadczenie / (exp za potwora z mnoznikiem etapu) * (czas zabicia + {@link #OVERHEAD_S} s na dojscie
 *       i lup). Sprzet: najlepszy dostepny dla profesji i poziomu.</li>
 *   <li><b>Polowanie</b>: walka 1 na 1 (Monte Carlo) z leczeniem - mikstura zdrowia poziomu postaci, gdy HP < 60%
 *       (co 1 s), czar leczacy, gdy HP < 50% (co 1 s), czary ataku co 2 s (mana z mikstur, bez limitu).
 *       Mag stoi w miejscu (pesymistycznie: w grze trzyma dystans).</li>
 * </ol>
 * Wynik: tabela rozwoju (rycerz, paladyn, czarodziej, druid) wobec typowych umiejetnosci poziomu
 * ({@link #skillsFollowTheLevel}) i tabela walki (czas zabicia, obrazenia na minute, smiertelnosc) dla rycerza
 * i czarodzieja z umiejetnosciami z tej symulacji na poziomach 8/20/50/100/200 wobec potworow ich krainy
 * i potworow z wyzszych krain.
 */
class HuntBalanceSimulationTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final SpellRegistry SPELLS = SpellRegistry.loadDefault(TestData.ITEMS);
    static final CharacterFactory FACTORY = new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS);
    /** Dojscie do nastepnego potwora, lup, przerwy. */
    static final double OVERHEAD_S = 6.0;
    static final int FIGHTS = 120;

    // ------------------------------------------------------------------ sprzet i postac

    /** Najlepsza bron profesji (wg ataku) i najlepszy pancerz na kazde miejsce, dostepne na tym poziomie. */
    static void equip(PlayerCharacter pc) {
        Vocation v = pc.vocation();
        boolean mage = v == Vocation.SORCERER || v == Vocation.DRUID;
        Map<EquipSlot, ItemType> best = new HashMap<>();
        for (ItemType t : TestData.ITEMS.all()) {
            if (t.minLevel() > pc.level() || !t.usableBy(v)) continue;
            if (t.category() == ItemCategory.WEAPON) {
                boolean fits = switch (v) {
                    case KNIGHT -> t.weaponType() == WeaponType.SWORD || t.weaponType() == WeaponType.AXE || t.weaponType() == WeaponType.CLUB;
                    case SORCERER, DRUID -> t.weaponType() == WeaponType.WAND;
                    case PALADIN -> t.weaponType() == WeaponType.DISTANCE && t.key().endsWith("bow");
                    case MONK -> t.weaponType() == WeaponType.FIST;
                };
                if (!fits) continue;
                ItemType cur = best.get(EquipSlot.WEAPON);
                if (cur == null || t.attack() > cur.attack()) best.put(EquipSlot.WEAPON, t);
            } else if (t.slot() == EquipSlot.SHIELD) {
                if (mage) continue;
                ItemType cur = best.get(EquipSlot.SHIELD);
                if (cur == null || t.defense() > cur.defense()) best.put(EquipSlot.SHIELD, t);
            } else if (t.slot() == EquipSlot.HEAD || t.slot() == EquipSlot.ARMOR || t.slot() == EquipSlot.LEGS || t.slot() == EquipSlot.FEET) {
                if (t.armor() <= 0) continue;
                ItemType cur = best.get(t.slot());
                if (cur == null || t.armor() > cur.armor()) best.put(t.slot(), t);
            }
        }
        for (EquipSlot s : EquipSlot.values()) if (s != EquipSlot.NONE) pc.equipment().setDirect(s, null);
        for (Map.Entry<EquipSlot, ItemType> e : best.entrySet()) pc.equipment().setDirect(e.getKey(), new ItemStack(e.getValue().id(), 1));
    }

    /** Postac na poziomie level z danymi umiejetnosciami i sprzetem poziomu. */
    static PlayerCharacter character(Vocation v, int level, Map<Skill, Integer> skills) {
        PlayerCharacter pc = FACTORY.create("Sim", v, new Position(1, 1, 0), level);
        for (Map.Entry<Skill, Integer> e : skills.entrySet()) pc.skills().set(e.getKey(), e.getValue(), 0);
        equip(pc);
        pc.heal(Integer.MAX_VALUE);
        return pc;
    }

    static Skill mainSkill(PlayerCharacter pc) {
        return CombatFormulas.attackProfile(pc, TestData.ITEMS).skill();
    }

    /** Potwor, na ktorego poluje postac tego poziomu: najsilniejszy zwykly potwor nie silniejszy od postaci. */
    static MonsterType huntTarget(int level) {
        MonsterType best = null;
        for (MonsterType t : REG.all()) {
            if (t.boss() || t.isElite() || t.level() > Math.max(3, level)) continue;
            if (best == null || t.level() > best.level()) best = t;
        }
        return best;
    }

    // ------------------------------------------------------------------ walka

    record Fight(double winRate, double killSeconds, double damagePerMinute, double damagePerKill, double actions, double manaPerMinute) {}

    /**
     * Mag z czarami na 7 pol trzyma wolniejszego potwora wrecz na dystans: potwor trafia tylko w takiej czesci
     * swoich atakow.
     */
    static final double KITE_SHARE = 0.35;

    /** Mikstura many poziomu i profesji (rycerz i mnich tylko zwykla). */
    static int potionMana(PlayerCharacter pc, Random rng) {
        boolean mage = pc.vocation() == Vocation.SORCERER || pc.vocation() == Vocation.DRUID;
        boolean caster = mage || pc.vocation() == Vocation.PALADIN;
        int l = pc.level();
        if (mage && l >= 250) return CombatFormulas.rollBetween(650, 900, rng);
        if (caster && l >= 200) return CombatFormulas.rollBetween(400, 600, rng);
        if (caster && l >= 100) return CombatFormulas.rollBetween(240, 330, rng);
        if (caster && l >= 50) return CombatFormulas.rollBetween(140, 200, rng);
        return CombatFormulas.rollBetween(50, 80, rng);
    }

    static int potionHeal(PlayerCharacter pc, Random rng) {
        String[][] tiers = {{"250", "1100", "1500"}, {"200", "800", "1100"}, {"100", "420", "560"}, {"50", "220", "300"}, {"0", "60", "90"}};
        boolean warrior = pc.vocation() == Vocation.KNIGHT || pc.vocation() == Vocation.MONK || pc.vocation() == Vocation.PALADIN;
        for (String[] t : tiers) {
            int lvl = Integer.parseInt(t[0]);
            if (pc.level() >= lvl && (warrior || lvl == 0)) {
                return CombatFormulas.rollBetween(Integer.parseInt(t[1]), Integer.parseInt(t[2]), rng);
            }
        }
        return 0;
    }

    static List<SpellType> attackSpells(PlayerCharacter pc) {
        List<SpellType> out = new ArrayList<>();
        for (SpellType s : SPELLS.all()) {
            boolean usable = s.kind() == SpellKind.ATTACK_TARGET || s.kind() == SpellKind.ATTACK_AREA
                    ? s.allowedFor(pc.vocation()) : s.kind() == SpellKind.RUNE_TARGET || s.kind() == SpellKind.RUNE_AREA;
            if (!usable || pc.level() < s.minLevel() || pc.skills().level(Skill.MAGIC) < s.minMagic()) continue;
            out.add(s);
        }
        return out;
    }

    /** Mana za jedna rune: czar zaklinajacy runy / liczba run (runa nie kosztuje many przy uzyciu). */
    static int manaCost(SpellType s) {
        if (!s.kind().isRune()) return s.mana();
        for (SpellType c : SPELLS.all()) {
            if (c.kind() == SpellKind.CONJURE && s.key().equals(c.producesItem())) return c.mana() / Math.max(1, c.produceCount());
        }
        return 0;
    }

    static SpellType bestHeal(PlayerCharacter pc) {
        SpellType best = null;
        for (SpellType s : SPELLS.forVocation(pc.vocation())) {
            if (s.kind() != SpellKind.HEAL_SELF || pc.level() < s.minLevel() || pc.skills().level(Skill.MAGIC) < s.minMagic()) continue;
            if (best == null || SpellSystem.baseValue(s, pc) > SpellSystem.baseValue(best, pc)) best = s;
        }
        return best;
    }

    static int spellRoll(SpellType s, PlayerCharacter pc, Random rng) {
        return (int) Math.round(SpellSystem.baseValue(s, pc)
                * (SpellSystem.MIN_ROLL + rng.nextDouble() * (SpellSystem.MAX_ROLL - SpellSystem.MIN_ROLL)));
    }

    /**
     * Pojedynek z leczeniem. Zwraca tez liczby do rozwoju umiejetnosci: ciosy gracza, ciosy fizyczne potwora,
     * wydana mana (out[0..2], suma z walk).
     */
    static Fight fight(PlayerCharacter pc, MonsterType m, long seed, int fights, long[] training) {
        Random rng = new Random(seed);
        AttackProfile ap = CombatFormulas.attackProfile(pc, TestData.ITEMS);
        List<SpellType> spells = attackSpells(pc);
        SpellType heal = bestHeal(pc);
        int wins = 0;
        long totalMs = 0, taken = 0, healed = 0, mana = 0, actions = 0;
        // ucieczka przed potworem wrecz tylko, gdy postac jest wyraznie szybsza (inaczej potwor dogania)
        boolean kites = (pc.vocation() == Vocation.SORCERER || pc.vocation() == Vocation.DRUID) && m.range() <= 1
                && Balance.speed(pc.level()) > m.speed() * 1.15;
        for (int f = 0; f < fights; f++) {
            int hp = pc.maxHealth(), mhp = m.maxHealth(), mp = pc.maxMana();
            boolean bossHealed = false;
            Map<String, Long> ready = new HashMap<>();
            long tHit = 0, tMonster = 600, tSpell = 0, tHeal = 0, now = 0;
            while (hp > 0 && mhp > 0 && now < 300_000) {
                now = Math.min(Math.min(tHit, tMonster), Math.min(tSpell, tHeal));
                if (now == tHeal) {
                    // jedna mikstura na sekunde (wspolna grupa): zdrowie, gdy HP < 60%, inaczej mana, gdy < 60%
                    if (hp < pc.maxHealth() * 0.6) {
                        int h = potionHeal(pc, rng);
                        if (heal != null && hp < pc.maxHealth() * 0.5 && mp >= heal.mana()) {
                            h += spellRoll(heal, pc, rng);
                            mp -= heal.mana();
                            mana += heal.mana();
                        }
                        int before = hp;
                        hp = Math.min(pc.maxHealth(), hp + h);
                        healed += hp - before;
                    } else if (mp < pc.maxMana() * 0.6) {
                        mp = Math.min(pc.maxMana(), mp + potionMana(pc, rng));
                    }
                    tHeal += 1000;
                } else if (now == tSpell) {
                    SpellType pick = null;
                    double pickDmg = 0;
                    for (SpellType s : spells) {
                        if (ready.getOrDefault(s.key(), 0L) > now || mp < s.mana()) continue;
                        double d = SpellSystem.baseValue(s, pc) * (100 - m.resistance(s.damageType())) / 100.0;
                        if (pick == null || d > pickDmg) {
                            pick = s;
                            pickDmg = d;
                        }
                    }
                    if (pick != null) {
                        mhp -= CombatFormulas.mitigateOnMonster(m, spellRoll(pick, pc, rng), pick.damageType(), rng);
                        actions++;
                        ready.put(pick.key(), now + pick.cooldownMs());
                        mp -= pick.mana();
                        mana += manaCost(pick);
                    }
                    tSpell += SpellGroup.ATTACK.groupCooldownMs;
                } else if (now == tHit) {
                    mhp -= CombatFormulas.mitigateOnMonster(m, CombatFormulas.roll(ap.maxDamage(), rng), ap.type(), rng);
                    actions++;
                    if (training != null) training[0]++;
                    tHit += ap.intervalMs();
                } else {
                    int raw = CombatFormulas.rollBetween(m.attackMin(), m.attackMax(), rng);
                    int d = CombatFormulas.mitigateOnPlayer(pc, raw, m.damageType(), false, rng);
                    if (kites && rng.nextDouble() >= KITE_SHARE) d = 0;
                    hp -= d;
                    taken += d;
                    if (training != null && CombatSystem.blockable(m)) training[1]++;
                    tMonster += m.attackIntervalMs();
                }
                if (m.boss() && !bossHealed && mhp > 0 && mhp < m.maxHealth() * 0.3) {
                    mhp += m.healAmount();
                    bossHealed = true;
                }
            }
            if (mhp <= 0) wins++;
            totalMs += now;
        }
        if (training != null) training[2] += mana;
        double minutes = Math.max(1, totalMs) / 60_000.0;
        double kill = totalMs / 1000.0 / fights;
        double perKill = taken / (double) fights;
        // obrazenia na minute polowania: na zabicie, razy zabicia na minute (walka + dojscie do nastepnego)
        return new Fight(wins / (double) fights, kill, perKill * 60 / (kill + OVERHEAD_S), perKill,
                actions / (double) fights, mana / minutes);
    }

    // ------------------------------------------------------------------ rozwoj postaci

    record Progress(int level, Map<Skill, Integer> skills, double hours) {}

    /**
     * Postac od poziomu 1 do maxLevel polujaca na potwory swojego poziomu; umiejetnosci na kazdym poziomie.
     * Proby jak w grze: kazdy atak bronia/strzal/rozdzka = {@link Balance#HUNT_TRY_FACTOR} prob
     * (CombatSystem.update), kazdy atak potwora do odparcia tarcza = {@link Balance#BLOCK_TRY_FACTOR}
     * (CombatSystem.monsterAttacks), kazdy punkt wydanej many = HUNT_TRY_FACTOR (SpellSystem.trainMagic);
     * wszystko przez Skills.train, czyli z mnoznikiem etapu.
     */
    static Map<Integer, Progress> grow(Vocation v, int maxLevel) {
        Map<Integer, Progress> out = new HashMap<>();
        PlayerCharacter pc = FACTORY.create("Sim", v, new Position(1, 1, 0), 1);
        double seconds = 0;
        for (int level = 1; level <= maxLevel; level++) {
            Map<Skill, Integer> now = new HashMap<>();
            for (Skill s : Skill.values()) now.put(s, pc.skills().level(s));
            out.put(level, new Progress(level, now, seconds / 3600));
            if (level == maxLevel) break;
            PlayerCharacter at = character(v, level, now);
            MonsterType m = huntTarget(level);
            long[] tr = new long[3];
            Fight f = fight(at, m, level * 31L + v.ordinal(), 12, tr);
            long expKill = Balance.applyRate(Balance.experienceReward(m.experience(), m.level(), level), Balance.expRate(level));
            long need = Balance.experienceForLevel(level + 1) - Balance.experienceForLevel(level);
            double kills = Math.ceil(need / (double) expKill);
            seconds += kills * (f.killSeconds() + OVERHEAD_S);
            // proby z 12 walk przeskalowane na liczbe zabic na tym poziomie
            double scale = kills / 12.0;
            trainMany(pc, mainSkill(at), Math.round(tr[0] * scale), Balance.HUNT_TRY_FACTOR);
            if (CombatSystem.trainsShielding(at)) trainMany(pc, Skill.SHIELDING, Math.round(tr[1] * scale), Balance.BLOCK_TRY_FACTOR);
            long manaSpent = Math.round(tr[2] * scale);
            while (manaSpent > 0) {
                long chunk = Math.min(manaSpent, 50); // jak pojedyncze czary: mnoznik etapu wg biezacego poziomu
                pc.skills().train(Skill.MAGIC, chunk * Balance.HUNT_TRY_FACTOR);
                manaSpent -= chunk;
            }
        }
        return out;
    }

    private static void trainMany(PlayerCharacter pc, Skill s, long events, int triesPerEvent) {
        for (long i = 0; i < events; i++) pc.skills().train(s, triesPerEvent);
        // rycerz zmienia miecz/topor/maczuge na najlepsza bron poziomu: w tabeli liczy sie jedna umiejetnosc broni
        // (poziom i postep przechodza na pozostale, inaczej kazda zmiana broni gubilaby postep)
        if (s.isMeleeWeapon()) for (Skill o : new Skill[] {Skill.SWORD, Skill.AXE, Skill.CLUB}) {
            if (o != s) pc.skills().set(o, pc.skills().level(s), pc.skills().tries(s));
        }
    }

    // ------------------------------------------------------------------ tabela

    static final int[] LEVELS = {8, 20, 50, 100, 200};

    /**
     * Umiejetnosci typowe dla poziomu w Tibii (postac bez dlugiego treningu offline; orientacyjnie z profili graczy
     * na TibiaWiki/forach; poziomy 50 i 100 wg Darka 2026-11-05): rycerz bron/tarcza (paladyn dystans tak jak bron
     * rycerza), czarodziej i druid magia. Miedzy punktami liniowo.
     */
    static final int[][] TIBIA_KNIGHT = {{1, 10, 10}, {8, 30, 28}, {20, 55, 50}, {50, 70, 65}, {100, 85, 80},
            {200, 98, 92}, {300, 105, 98}, {500, 115, 106}, {1000, 125, 115}};
    static final int[][] TIBIA_SORCERER = {{1, 0}, {8, 8}, {20, 25}, {50, 50}, {100, 68}, {200, 82}, {300, 90},
            {500, 100}, {1000, 112}};

    static int interp(int[][] t, int level, int col) {
        for (int i = 0; i + 1 < t.length; i++) {
            if (level <= t[i + 1][0]) {
                double f = (level - t[i][0]) / (double) (t[i + 1][0] - t[i][0]);
                return (int) Math.round(t[i][col] + f * (t[i + 1][col] - t[i][col]));
            }
        }
        return t[t.length - 1][col];
    }

    /** Potwory krainy (na poziomie postaci) i potwory "za daleko" (wyzsza kraina). */
    static final Map<Integer, String[]> REGION = Map.of(
            8, new String[] {"wolf", "giant_rat", "carrion_crow", "forest_spider"},
            20, new String[] {"orc_warrior", "orc_shaman", "boar", "quarry_brute", "crocodile"},
            50, new String[] {"cave_troll", "crystal_golem", "crocodile", "orc_berserker"},
            100, new String[] {"frost_witch", "jungle_serpent", "bog_wraith", "gilded_beetle"},
            200, new String[] {"stone_sentinel", "sand_gargoyle", "temple_guardian", "blade_warden"});
    /** Potwory "smiertelne" dla poziomu (Difficulty.DEADLY: poziom > 2x postaci + 10) z wyzszych krain. */
    static final Map<Integer, String[]> TOO_STRONG = Map.of(
            8, new String[] {"orc_berserker", "crystal_golem"},
            20, new String[] {"yeti", "frost_witch"},
            50, new String[] {"ice_golem", "stone_sentinel"},
            100, new String[] {"blade_warden", "cursed_treant"},
            200, new String[] {"frost_giant", "storm_harpy"});

    private static Map<Vocation, Map<Integer, Progress>> growthCache;

    static synchronized Map<Vocation, Map<Integer, Progress>> growth() {
        if (growthCache != null) return growthCache;
        Map<Vocation, Map<Integer, Progress>> g = new HashMap<>();
        growthCache = g;
        for (Vocation v : new Vocation[] {Vocation.KNIGHT, Vocation.PALADIN, Vocation.SORCERER, Vocation.DRUID}) g.put(v, grow(v, 200));
        return g;
    }

    static String row(int level, Vocation v, PlayerCharacter pc, String key, Fight f, boolean strong) {
        MonsterType m = REG.get(key);
        String skills = v == Vocation.KNIGHT
                ? String.format("bron %3d tarcza %3d", pc.skills().level(Skill.AXE), pc.skills().level(Skill.SHIELDING))
                : String.format("magia %3d          ", pc.skills().level(Skill.MAGIC));
        return String.format("| %4d | %-8s | %s | %5d | %-17s | %4d | %6d | %5.1f | %4.1f | %6.0f | %7.0f | %5.0f%% | %5.1f%% |%n",
                level, v == Vocation.KNIGHT ? "rycerz" : "czarodz.", skills, pc.maxHealth(), (strong ? "!" : "") + key, m.level(),
                m.maxHealth(), f.killSeconds(), f.actions(), f.damagePerKill(), f.damagePerMinute(),
                f.damagePerMinute() * 100 / pc.maxHealth(), f.winRate() * 100);
    }

    /** Zgodnosc z typowa postacia (Darek 2026-11-05: "podnies umiejetnosci wg tabeli RubinOT"): +- tyle poziomow. */
    static final int SKILL_TOLERANCE = 7;

    /** Glowna umiejetnosc walki profesji w tabeli rozwoju (rycerz: bron wrecz, paladyn: dystans, magowie: magia). */
    static int mainLevel(Vocation v, Map<Skill, Integer> skills) {
        switch (v) {
            case KNIGHT: return skills.get(Skill.AXE);
            case PALADIN: return skills.get(Skill.DISTANCE);
            case MONK: return skills.get(Skill.FIST);
            default: return skills.get(Skill.MAGIC);
        }
    }

    /** Typowa glowna umiejetnosc profesji (paladyn jak bron rycerza, magowie jak magia czarodzieja). */
    static int typicalMain(Vocation v, int level) {
        return v == Vocation.SORCERER || v == Vocation.DRUID ? interp(TIBIA_SORCERER, level, 1) : interp(TIBIA_KNIGHT, level, 1);
    }

    @Test
    void skillsFollowTheLevel() {
        Map<Vocation, Map<Integer, Progress>> g = growth();
        StringBuilder sb = new StringBuilder("\nUmiejetnosci tylko z polowania (etapy serwera, Balance.HUNT_TRY_FACTOR = "
                + Balance.HUNT_TRY_FACTOR + ", bez manekinow) vs typowe w Tibii; czas polowania od poziomu 1:\n"
                + "| poz. | rycerz bron/tarcza (typowo) | h | paladyn dystans (typowo) | h | czarodziej magia (typowo) | h | druid magia (typowo) | h |\n");
        List<String> fails = new ArrayList<>();
        for (int l : new int[] {20, 50, 100, 150, 200}) {
            Progress k = g.get(Vocation.KNIGHT).get(l), p = g.get(Vocation.PALADIN).get(l);
            Progress s = g.get(Vocation.SORCERER).get(l), d = g.get(Vocation.DRUID).get(l);
            sb.append(String.format("| %4d | %3d/%3d (%3d/%3d) | %4.1f | %3d (%3d) | %4.1f | %3d (%3d) | %4.1f | %3d (%3d) | %4.1f |%n", l,
                    k.skills().get(Skill.AXE), k.skills().get(Skill.SHIELDING), interp(TIBIA_KNIGHT, l, 1), interp(TIBIA_KNIGHT, l, 2), k.hours(),
                    p.skills().get(Skill.DISTANCE), typicalMain(Vocation.PALADIN, l), p.hours(),
                    s.skills().get(Skill.MAGIC), typicalMain(Vocation.SORCERER, l), s.hours(),
                    d.skills().get(Skill.MAGIC), typicalMain(Vocation.DRUID, l), d.hours()));
            if (l != 50 && l != 100) continue;
            for (Vocation v : new Vocation[] {Vocation.KNIGHT, Vocation.PALADIN, Vocation.SORCERER, Vocation.DRUID}) {
                int got = mainLevel(v, g.get(v).get(l).skills()), want = typicalMain(v, l);
                if (Math.abs(got - want) > SKILL_TOLERANCE) fails.add("L" + l + " " + v + " main skill " + got + " vs typical " + want);
            }
            int shield = k.skills().get(Skill.SHIELDING), want = interp(TIBIA_KNIGHT, l, 2);
            if (Math.abs(shield - want) > SKILL_TOLERANCE) fails.add("L" + l + " KNIGHT shielding " + shield + " vs typical " + want);
        }
        for (Vocation v : g.keySet()) sb.append("L100 ").append(v).append(": ").append(new java.util.TreeMap<>(g.get(v).get(100).skills())).append('\n');
        System.out.println(sb);
        assertTrue(fails.isEmpty(), String.join("\n", fails));
        Progress k100 = g.get(Vocation.KNIGHT).get(100), s100 = g.get(Vocation.SORCERER).get(100);
        assertTrue(g.get(Vocation.KNIGHT).get(20).skills().get(Skill.AXE) < k100.skills().get(Skill.AXE));
        assertTrue(s100.skills().get(Skill.FIST) < 30, "mage stays weak in melee");
        assertTrue(s100.skills().get(Skill.SHIELDING) < 30, "mage stays weak in shielding");
        assertTrue(s100.skills().get(Skill.MAGIC) > k100.skills().get(Skill.MAGIC) * 3, "mage magic >> knight magic");
        assertTrue(g.get(Vocation.PALADIN).get(100).skills().get(Skill.MAGIC) < s100.skills().get(Skill.MAGIC), "paladin magic below mage");
    }

    /** Postac tworzona od razu na wyzszym poziomie (start offline 300) ma umiejetnosci z tej symulacji. */
    @Test
    void factoryStartSkillsMatchTheSimulation() {
        Map<Vocation, Map<Integer, Progress>> g = growth();
        List<String> fails = new ArrayList<>();
        for (Vocation v : g.keySet()) {
            for (int l : new int[] {20, 50, 100, 150, 200}) {
                PlayerCharacter pc = FACTORY.create("Start", v, new Position(1, 1, 0), l);
                Map<Skill, Integer> sim = g.get(v).get(l).skills();
                for (Skill s : new Skill[] {Skill.AXE, Skill.DISTANCE, Skill.SHIELDING, Skill.MAGIC}) {
                    if (Math.abs(pc.skills().level(s) - Math.max(Skill.MAGIC == s ? 0 : 10, sim.get(s))) > 2) {
                        fails.add("L" + l + " " + v + " " + s + " factory " + pc.skills().level(s) + " vs simulated " + sim.get(s));
                    }
                }
            }
        }
        assertTrue(fails.isEmpty(), String.join("\n", fails));
    }

    @Test
    void charactersHuntTheirRegionAndFearHigherOnes() {
        StringBuilder sb = new StringBuilder("\n| poz. | profesja | umiejetnosci        |    HP | potwor            | poz. | HP pot. | czas s | ciosy | obr./zab. | obr./min | % HP/min | wygrane |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        List<String> fails = new ArrayList<>();
        for (int l : LEVELS) {
            for (Vocation v : new Vocation[] {Vocation.KNIGHT, Vocation.SORCERER}) {
                // umiejetnosci z symulacji rozwoju (skillsFollowTheLevel), nie zgadywane
                PlayerCharacter pc = character(v, l, growth().get(v).get(l).skills());
                for (String key : REGION.get(l)) {
                    Fight f = fight(pc, REG.get(key), key.hashCode() + l, FIGHTS, null);
                    sb.append(row(l, v, pc, key, f, false));
                    if (f.winRate() < 0.97) fails.add("L" + l + " " + v + " loses to region monster " + key + " " + f);
                    if (f.killSeconds() > 25) fails.add("L" + l + " " + v + " too slow on " + key + " " + f);
                    if (v == Vocation.KNIGHT && l >= 20 && f.actions() < 1.8 && REG.get(key).level() >= l * 2 / 3) fails.add("L" + l + " " + v + " one-shots " + key + " " + f);
                    if (f.damagePerMinute() > pc.maxHealth() * 1.5) fails.add("L" + l + " " + v + " bleeds on region monster " + key + " " + f);
                }
                for (String key : TOO_STRONG.get(l)) {
                    Fight f = fight(pc, REG.get(key), key.hashCode() + l, FIGHTS, null);
                    sb.append(row(l, v, pc, key, f, true));
                    // za silny: albo ginie, albo walka jest dluga i kosztowna (duzo wiecej obrazen niz w krainie)
                    // za silny: ginie, walka trwa ponad 30 s, kosztuje ponad 3/4 paska HP na zabicie albo 2 paski na minute,
                    // albo jeden cios potwora zabiera 40% HP
                    MonsterType m = REG.get(key);
                    boolean dangerous = f.winRate() < 0.8 || f.killSeconds() > 30 || f.damagePerMinute() > pc.maxHealth() * 2.0
                            || f.damagePerKill() > pc.maxHealth() * 0.75 || m.attackMax() >= pc.maxHealth() * 0.4;
                    if (!dangerous) fails.add("L" + l + " " + v + " farms too strong " + key + " " + f);
                }
            }
        }
        System.out.println(sb);
        assertTrue(fails.isEmpty(), String.join("\n", fails));
    }
}
