package pl.fawizja.pixel.item;

import java.util.EnumSet;
import java.util.Set;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/** Niezmienna definicja typu przedmiotu wczytana z data/items.csv. */
public final class ItemType {
    private final int id;
    private final String key;
    private final String name;
    private final ItemCategory category;
    private final boolean stackable;
    private final int maxStack;
    private final long value;
    private final EquipSlot slot;
    private final int attack;
    private final int defense;
    private final int armor;
    private final int minLevel;
    private final Set<Vocation> vocations;
    private final int lightRadius;
    private final int range;
    private final DamageType damageType;
    private final String color;
    private final String description;

    public ItemType(int id, String key, String name, ItemCategory category, boolean stackable, int maxStack,
                    long value, EquipSlot slot, int attack, int defense, int armor, int minLevel,
                    Set<Vocation> vocations, int lightRadius, int range, DamageType damageType,
                    String color, String description) {
        if (id <= 0) throw new IllegalArgumentException("item id must be > 0");
        if (stackable && maxStack < 2) throw new IllegalArgumentException("stackable item needs maxStack >= 2: " + key);
        if (!stackable && maxStack != 1) throw new IllegalArgumentException("non-stackable item needs maxStack 1: " + key);
        if (value < 0) throw new IllegalArgumentException("negative value: " + key);
        this.id = id;
        this.key = key;
        this.name = name;
        this.category = category;
        this.stackable = stackable;
        this.maxStack = maxStack;
        this.value = value;
        this.slot = slot;
        this.attack = attack;
        this.defense = defense;
        this.armor = armor;
        this.minLevel = minLevel;
        this.vocations = vocations.isEmpty() ? EnumSet.allOf(Vocation.class) : EnumSet.copyOf(vocations);
        this.lightRadius = lightRadius;
        if (range < 0 || range > 8) throw new IllegalArgumentException("range 0..8: " + key);
        this.range = range;
        this.damageType = damageType;
        this.color = color;
        this.description = description;
    }

    public int id() { return id; }
    public String key() { return key; }
    public String name() { return name; }
    public ItemCategory category() { return category; }
    public boolean stackable() { return stackable; }
    public int maxStack() { return maxStack; }
    public long value() { return value; }
    public EquipSlot slot() { return slot; }
    public int attack() { return attack; }
    public int defense() { return defense; }
    public int armor() { return armor; }
    public int minLevel() { return minLevel; }
    public Set<Vocation> vocations() { return EnumSet.copyOf(vocations); }
    public boolean usableBy(Vocation v) { return vocations.contains(v); }
    public int lightRadius() { return lightRadius; }
    /** Zasieg broni w kafelkach (1 = wrecz, 0 = nie dotyczy). */
    public int range() { return range; }
    public DamageType damageType() { return damageType; }
    public String color() { return color; }
    public String description() { return description; }

    @Override
    public String toString() { return key + "#" + id; }
}
