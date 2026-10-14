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

    public MonsterType(String key, String name, int level, int maxHealth, long experience, int speed,
                       int attackMin, int attackMax, DamageType damageType, int range, int attackIntervalMs,
                       int armor, Map<DamageType, Integer> resistances, MonsterBehavior behavior, int aggroRange,
                       int fleeHealthPercent, int healAmount, boolean boss, String color, String shape,
                       String biome, boolean nocturnal, String description, List<LootEntry> loot) {
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
}
