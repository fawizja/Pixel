package pl.fawizja.pixel.monster;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.combat.DamageType;

/** Niezmienna definicja potwora z data/monsters.csv. */
public final class MonsterType {
    private final String key;
    private final String name;
    private final int level;
    private final int maxHealth;
    private final long experience;
    private final int speed;
    private final int attackMin;
    private final int attackMax;
    private final DamageType damageType;
    private final int range;
    private final int attackIntervalMs;
    private final int armor;
    private final EnumMap<DamageType, Integer> resistances;
    private final MonsterBehavior behavior;
    private final int aggroRange;
    private final int fleeHealthPercent;
    private final int healAmount;
    private final boolean boss;
    private final String color;
    private final String shape;
    private final String biome;
    private final boolean nocturnal;
    private final String description;
    private final List<LootEntry> loot;
    /** Klasa potwora jak w bestiariuszu Tibii (Mammal, Undead, Dragon...). */
    private final String race;
    /** Drugi atak (np. wyssanie zycia) albo null. */
    private final ExtraAttack extraAttack;
    /**
     * Stopien Kuzni (Tibia "forge stack"): 0 = zwykly, 1..5 = pod wpływem (Influenced), 15 = diaboliczny (Fiendish).
     * Odmiany maja ten sam klucz co zwykly potwor: grafika, bestiariusz i zadania licza je jak zwyklego.
     */
    private int forgeStack;
    private MonsterType base = this;
    private final MonsterType[] forgeVariants = new MonsterType[FIENDISH_STACK + 1];

    /** Stopien diabolicznego potwora (Canary configureForgeSystem). */
    public static final int FIENDISH_STACK = 15;
    /**
     * Szansa (promile) na potwora pod wpływem przy pojawieniu sie. Tibia: 300 na serwer (forgeInfluencedLimit);
     * u nas ok. 3% z 7352 potworow swiata (~220), limit 300 pilnuje MonsterManager.
     */
    public static final int INFLUENCED_PERMILLE = 30;
    /**
     * Rozklad stopni 1..5 (promile, narastajaco) jak Canary normal_random(1, 5): N(0,5; 0,25) obciety do [0, 1],
     * zaokraglony do 5 stopni - 4,6% / 25,3% / 40,1% / 25,3% / 4,6%.
     */
    static final int[] STAGE_CUMULATIVE = {46, 299, 700, 953, 1000};

    public MonsterType(String key, String name, int level, int maxHealth, long experience, int speed,
                       int attackMin, int attackMax, DamageType damageType, int range, int attackIntervalMs,
                       int armor, Map<DamageType, Integer> resistances, MonsterBehavior behavior, int aggroRange,
                       int fleeHealthPercent, int healAmount, boolean boss, String color, String shape,
                       String biome, boolean nocturnal, String description, List<LootEntry> loot) {
        this(key, name, level, maxHealth, experience, speed, attackMin, attackMax, damageType, range, attackIntervalMs, armor,
                resistances, behavior, aggroRange, fleeHealthPercent, healAmount, boss, color, shape, biome, nocturnal, description,
                loot, "", null);
    }

    public MonsterType(String key, String name, int level, int maxHealth, long experience, int speed,
                       int attackMin, int attackMax, DamageType damageType, int range, int attackIntervalMs,
                       int armor, Map<DamageType, Integer> resistances, MonsterBehavior behavior, int aggroRange,
                       int fleeHealthPercent, int healAmount, boolean boss, String color, String shape,
                       String biome, boolean nocturnal, String description, List<LootEntry> loot, String race,
                       ExtraAttack extraAttack) {
        if (maxHealth <= 0 || level <= 0 || experience < 0) throw new IllegalArgumentException("bad stats: " + key);
        if (attackMin < 0 || attackMax < attackMin) throw new IllegalArgumentException("bad attack: " + key);
        if (range < 1 || attackIntervalMs < 200) throw new IllegalArgumentException("bad range/interval: " + key);
        for (int r : resistances.values()) {
            if (r < -100 || r > 100) throw new IllegalArgumentException("resistance -100..100: " + key);
        }
        this.key = key;
        this.name = name;
        this.level = level;
        this.maxHealth = maxHealth;
        this.experience = experience;
        this.speed = speed;
        this.attackMin = attackMin;
        this.attackMax = attackMax;
        this.damageType = damageType;
        this.range = range;
        this.attackIntervalMs = attackIntervalMs;
        this.armor = armor;
        this.resistances = resistances.isEmpty() ? new EnumMap<>(DamageType.class) : new EnumMap<>(resistances);
        this.behavior = behavior;
        this.aggroRange = aggroRange;
        this.fleeHealthPercent = fleeHealthPercent;
        this.healAmount = healAmount;
        this.boss = boss;
        this.color = color;
        this.shape = shape;
        this.biome = biome;
        this.nocturnal = nocturnal;
        this.description = description;
        this.loot = Collections.unmodifiableList(new java.util.ArrayList<>(loot));
        this.race = race == null ? "" : race;
        this.extraAttack = extraAttack;
    }

    public String key() { return key; }
    public String name() { return name; }
    public int level() { return level; }
    public int maxHealth() { return maxHealth; }
    public long experience() { return experience; }
    public int speed() { return speed; }
    public int attackMin() { return attackMin; }
    public int attackMax() { return attackMax; }
    public DamageType damageType() { return damageType; }
    public int range() { return range; }
    public int attackIntervalMs() { return attackIntervalMs; }
    public int armor() { return armor; }
    /** Odpornosc w procentach (ujemna = slabosc). */
    public int resistance(DamageType t) { return resistances.getOrDefault(t, 0); }
    public Map<DamageType, Integer> resistances() { return Collections.unmodifiableMap(resistances); }
    public MonsterBehavior behavior() { return behavior; }
    public int aggroRange() { return aggroRange; }
    public int fleeHealthPercent() { return fleeHealthPercent; }
    public int healAmount() { return healAmount; }
    public boolean boss() { return boss; }
    public String color() { return color; }
    public String shape() { return shape; }
    public String biome() { return biome; }
    /** Nocny drapieznik: w nocy na powierzchni wieksze aggro i szybsze ataki. */
    public boolean nocturnal() { return nocturnal; }
    public String description() { return description; }
    public List<LootEntry> loot() { return loot; }
    /** Klasa bestiariusza Tibii (np. "Undead"); pusta, gdy nie podano. */
    public String race() { return race; }
    /** Drugi atak potwora albo null. */
    public ExtraAttack extraAttack() { return extraAttack; }

    /** Wylosowany atak: rodzaj obrazen i obrazenia przed obrona postaci. */
    public record Strike(DamageType type, int raw) {}

    /**
     * Jeden atak potwora: w {@link ExtraAttack#percent()}% atakow drugi atak, inaczej zwykly. Potwor bez drugiego
     * ataku losuje tylko obrazenia (ta sama liczba losowan co dawniej - powtarzalne walki w testach).
     */
    public Strike rollStrike(java.util.Random rng) {
        if (extraAttack != null && rng.nextInt(100) < extraAttack.percent()) {
            return new Strike(extraAttack.type(), pl.fawizja.pixel.combat.CombatFormulas.rollBetween(extraAttack.min(), extraAttack.max(), rng));
        }
        return new Strike(damageType, pl.fawizja.pixel.combat.CombatFormulas.rollBetween(attackMin, attackMax, rng));
    }

    /** Stopien Kuzni: 0, 1..5 (pod wpływem) albo 15 (diaboliczny). */
    public int forgeStack() { return forgeStack; }
    public boolean isInfluenced() { return forgeStack >= 1 && forgeStack <= 5; }
    public boolean isFiendish() { return forgeStack == FIENDISH_STACK; }
    /** Potwor pod wpływem albo diaboliczny (dawna "elita": bestiariusz i osiagniecia licza je razem). */
    public boolean isElite() { return forgeStack > 0; }
    /** Zwykla odmiana (dla zwyklego potwora: on sam). */
    public MonsterType base() { return base; }

    /** Mnozniki Canary wg stopnia s (monster.cpp): zdrowie 1 + (15s + 35) / 100. */
    public static double healthFactor(int s) { return s <= 0 ? 1 : 1 + (15.0 * s + 35) / 100.0; }
    /** Obrazenia 1,35 + (s - 1) x 0,1 (getAttackMultiplier). */
    public static double damageFactor(int s) { return s <= 0 ? 1 : 1.35 + (s - 1) * 0.1; }
    /** Obrona 1 + 0,1 x s (getDefenseMultiplier) - u nas pancerz. */
    public static double defenseFactor(int s) { return s <= 0 ? 1 : 1 + 0.1 * s; }
    /**
     * Doswiadczenie (s + 10) / 10 jak zamierza Canary getLostExperience: +10..+50%, diaboliczny x2,5. (Canary liczy to
     * na liczbach calkowitych i daje x1 / x2 - prawdziwa premia w Tibii niezweryfikowana.)
     */
    public static double experienceFactor(int s) { return s <= 0 ? 1 : (s + 10) / 10.0; }

    /** Czy potwor moze zostac pod wpływem albo diaboliczny (jak canBeForgeMonster: nie boss, zostawia lup). */
    public boolean canBeForge() { return forgeStack == 0 && !boss && !loot.isEmpty(); }

    /** Pod wpływem stopnia 1..5. */
    public MonsterType influenced(int stage) { return forgeVariant(Math.max(1, Math.min(5, stage))); }

    public MonsterType fiendish() { return forgeVariant(FIENDISH_STACK); }

    /**
     * Odmiana Kuzni o stopniu stack (1..5 albo 15; 0 = zwykly); bossowie i potwory bez lupu zwracaja siebie.
     * Lup bez zmian (Canary nie ma mnoznika lupu; diaboliczny dodaje odlamki - CombatSystem).
     */
    public synchronized MonsterType forgeVariant(int stack) {
        MonsterType b = base;
        if (stack <= 0) return b;
        if (stack != FIENDISH_STACK) stack = Math.max(1, Math.min(5, stack));
        if (b.boss || b.loot.isEmpty()) return b;
        if (b != this) return b.forgeVariant(stack);
        if (forgeVariants[stack] == null) {
            double d = damageFactor(stack);
            MonsterType t = new MonsterType(key, name, level, (int) Math.ceil(maxHealth * healthFactor(stack)),
                    Math.round(experience * experienceFactor(stack)), speed,
                    (int) Math.round(attackMin * d), (int) Math.round(attackMax * d), damageType, range, attackIntervalMs,
                    (int) Math.round(armor * defenseFactor(stack)), resistances, behavior, aggroRange, fleeHealthPercent,
                    healAmount, false, color, shape, biome, nocturnal, description, loot, race,
                    extraAttack == null ? null : extraAttack.scaled(d));
            t.forgeStack = stack;
            t.base = this;
            forgeVariants[stack] = t;
        }
        return forgeVariants[stack];
    }

    /** Opis stopnia: "pod wpływem (3)" / "diaboliczny" / "". */
    public String forgeLabel() {
        if (isFiendish()) return "diaboliczny";
        return isInfluenced() ? "pod wpływem (" + forgeStack + ")" : "";
    }

    private static long mix(long x) {
        x *= 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return x;
    }

    /**
     * Stopien pod wpływem potwora o danym numerze (0 = zwykly): los z numeru, bez zuzywania generatora
     * (powtarzalne spawny), {@link #INFLUENCED_PERMILLE} promili, stopien wg {@link #STAGE_CUMULATIVE}.
     */
    public static int forgeRoll(int monsterId) {
        if (Math.floorMod(mix(monsterId), 1000L) >= INFLUENCED_PERMILLE) return 0;
        long r = Math.floorMod(mix(monsterId ^ 0x5DEECE66DL), 1000L);
        for (int i = 0; i < STAGE_CUMULATIVE.length; i++) if (r < STAGE_CUMULATIVE[i]) return i + 1;
        return 5;
    }

}
