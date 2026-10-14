package pl.fawizja.pixel.magic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.vocation.Vocation;

/** Definicja czaru z data/spells.csv. Wartosc efektu = baza + poziom*lf + magia*mf + umiejetnosc*sf. */
public final class SpellType {
    private final String key;
    private final String name;
    private final Set<Vocation> vocations;
    private final int minLevel;
    private final int minMagic;
    private final int mana;
    private final int cooldownMs;
    private final SpellGroup group;
    private final SpellKind kind;
    private final DamageType damageType;
    private final double base;
    private final double levelFactor;
    private final double magicFactor;
    private final double skillFactor;
    private final int range;
    private final int radius;
    private final String producesItem;
    private final String consumesItem;
    private final int produceCount;
    private final String description;

    public SpellType(String key, String name, Set<Vocation> vocations, int minLevel, int minMagic, int mana, int cooldownMs,
                     SpellGroup group, SpellKind kind, DamageType damageType, double base, double levelFactor,
                     double magicFactor, double skillFactor, int range, int radius, String producesItem,
                     String consumesItem, int produceCount, String description) {
        if (mana < 0 || cooldownMs < 0 || minLevel < 1 || range < 0 || radius < 0 || produceCount < 0) {
            throw new IllegalArgumentException("bad spell values: " + key);
        }
        if (kind == SpellKind.CONJURE && (producesItem == null || produceCount <= 0)) {
            throw new IllegalArgumentException("conjure spell without product: " + key);
        }
        this.key = key;
        this.name = name;
        this.vocations = vocations.isEmpty() ? EnumSet.allOf(Vocation.class) : EnumSet.copyOf(vocations);
        this.minLevel = minLevel;
        this.minMagic = minMagic;
        this.mana = mana;
        this.cooldownMs = cooldownMs;
        this.group = group;
        this.kind = kind;
        this.damageType = damageType;
        this.base = base;
        this.levelFactor = levelFactor;
        this.magicFactor = magicFactor;
        this.skillFactor = skillFactor;
        this.range = range;
        this.radius = radius;
        this.producesItem = producesItem;
        this.consumesItem = consumesItem;
        this.produceCount = produceCount;
        this.description = description;
    }

    public String key() { return key; }
    public String name() { return name; }
    public Set<Vocation> vocations() { return Collections.unmodifiableSet(vocations); }
    public boolean allowedFor(Vocation v) { return vocations.contains(v); }
    public int minLevel() { return minLevel; }
    public int minMagic() { return minMagic; }
    public int mana() { return mana; }
    public int cooldownMs() { return cooldownMs; }
    public SpellGroup group() { return group; }
    public SpellKind kind() { return kind; }
    public DamageType damageType() { return damageType; }
    public double base() { return base; }
    public double levelFactor() { return levelFactor; }
    public double magicFactor() { return magicFactor; }
    public double skillFactor() { return skillFactor; }
    public int range() { return range; }
    public int radius() { return radius; }
    public String producesItem() { return producesItem; }
    public String consumesItem() { return consumesItem; }
    public int produceCount() { return produceCount; }
    public String description() { return description; }
}
