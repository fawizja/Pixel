package pl.fawizja.pixel.item;

import java.util.List;
import pl.fawizja.pixel.combat.DamageType;

/**
 * Nasycenia (imbuementy) jak w Tibii, u kowala zamiast kapliczki. Wartosci z Canary (data/XML/imbuements.xml):
 * <ul>
 *   <li>Vampirism (kradziez zycia) 5/10/25%, Void (kradziez many) 3/5/8%, Strike (krytyk) +10% szansy i
 *       +15/25/50% obrazen krytycznych,</li>
 *   <li>Scorch / Venom / Frost / Electrify / Reap: zamiana 10/25/50% obrazen fizycznych broni na ogien / ziemie /
 *       lod / energie / smierc,</li>
 *   <li>ochrony Dragon Hide (ogien), Snake Skin (ziemia), Quara Scale (lod), Cloud Fabric (energia), Demon Presence
 *       (swiete) 3/8/15%, Lich Shroud (smierc) 2/5/10%,</li>
 *   <li>trzy stopnie: Basic, Intricate, Powerful; czas 20 godzin walki (72 000 s, "duration" w Canary).</li>
 * </ul>
 * Skladniki to tibijskie produkty z potworow zamienione na nasze lupy (te same ilosci co w Canary), zloto
 * przeskalowane do naszej ekonomii: {@link #GOLD} = cena bazy Canary (5 000 / 30 000 / 200 000) x 0,25.
 */
public enum Imbuement {
    VAMPIRISM("Wampiryzm", "Vampirism", Category.LIFE_LEECH, null, new double[] {5, 10, 25},
            Target.WEAPON_ARMOR, ing("hyena_fang", 25), ing("crab_pincer", 15), ing("shadow_essence", 5)),
    VOID("Pustka", "Void", Category.MANA_LEECH, null, new double[] {3, 5, 8},
            Target.WEAPON_HELMET, ing("spider_silk", 25), ing("chitin_plate", 25), ing("mist_essence", 5)),
    STRIKE("Cios", "Strike", Category.CRITICAL, null, new double[] {15, 25, 50},
            Target.WEAPON, ing("goblin_ear", 20), ing("boar_tusk", 25), ing("drake_horn", 5)),
    SCORCH("Żar", "Scorch", Category.ELEMENT_DAMAGE, DamageType.FIRE, new double[] {10, 25, 50},
            Target.PHYSICAL_WEAPON, ing("obsidian_shard", 25), ing("wyvern_scale", 5), ing("abyssal_ember", 5)),
    VENOM("Jad", "Venom", Category.ELEMENT_DAMAGE, DamageType.EARTH, new double[] {10, 25, 50},
            Target.PHYSICAL_WEAPON, ing("worm_slime", 25), ing("snake_skin", 20), ing("jungle_idol", 2)),
    FROST("Mróz", "Frost", Category.ELEMENT_DAMAGE, DamageType.ICE, new double[] {10, 25, 50},
            Target.PHYSICAL_WEAPON, ing("frost_pelt", 25), ing("yeti_fur", 10), ing("frozen_heart", 5)),
    ELECTRIFY("Elektryzacja", "Electrify", Category.ELEMENT_DAMAGE, DamageType.ENERGY, new double[] {10, 25, 50},
            Target.PHYSICAL_WEAPON, ing("black_feather", 25), ing("crystal_shard", 5), ing("colossus_core", 1)),
    REAP("Żniwo", "Reap", Category.ELEMENT_DAMAGE, DamageType.DEATH, new double[] {10, 25, 50},
            Target.PHYSICAL_WEAPON, ing("bone", 25), ing("mummy_wrap", 20), ing("ectoplasm", 5)),
    DRAGON_HIDE("Smocza skóra", "Dragon Hide", Category.PROTECTION, DamageType.FIRE, new double[] {3, 8, 15},
            Target.ARMOR_SHIELD, ing("croc_leather", 20), ing("obsidian_shard", 10), ing("drake_horn", 5)),
    SNAKE_SKIN("Wężowa skóra", "Snake Skin", Category.PROTECTION, DamageType.EARTH, new double[] {3, 8, 15},
            Target.ARMOR_SHIELD, ing("troll_hide", 25), ing("snake_skin", 20), ing("scorpion_tail", 10)),
    QUARA_SCALE("Łuska quary", "Quara Scale", Category.PROTECTION, DamageType.ICE, new double[] {3, 8, 15},
            Target.ARMOR_SHIELD, ing("wolf_pelt", 25), ing("frost_pelt", 15), ing("pearl", 10)),
    CLOUD_FABRIC("Tkanina chmur", "Cloud Fabric", Category.PROTECTION, DamageType.ENERGY, new double[] {3, 8, 15},
            Target.ARMOR_SHIELD, ing("wyvern_scale", 20), ing("chitin_plate", 15), ing("gargoyle_shard", 10)),
    DEMON_PRESENCE("Obecność demona", "Demon Presence", Category.PROTECTION, DamageType.HOLY, new double[] {3, 8, 15},
            Target.ARMOR_SHIELD, ing("black_feather", 25), ing("mummy_wrap", 25), ing("gargoyle_shard", 20)),
    LICH_SHROUD("Całun licza", "Lich Shroud", Category.PROTECTION, DamageType.DEATH, new double[] {2, 5, 10},
            Target.ARMOR_SHIELD, ing("mummy_wrap", 25), ing("wolf_pelt", 20), ing("drowned_ring", 5));

    /** Grupa wykluczajaca: na jednym przedmiocie najwyzej jedno nasycenie z grupy (ochrony - jedno na zywiol). */
    public enum Category { LIFE_LEECH, MANA_LEECH, CRITICAL, ELEMENT_DAMAGE, PROTECTION }

    /** Na jakie przedmioty (jak w Tibii: leech i krytyk na bron, ochrony na zbroje i tarcze). */
    public enum Target { WEAPON, PHYSICAL_WEAPON, WEAPON_ARMOR, WEAPON_HELMET, ARMOR_SHIELD }

    public record Ingredient(String itemKey, int count) {}

    /** Szansa na krytyk z Strike (kazdy stopien). */
    public static final double STRIKE_CHANCE = 10;
    /** Czas dzialania: 20 godzin walki. */
    public static final long DURATION_MS = 20L * 3_600_000L;
    public static final String[] LEVELS = {"", "Podstawowe", "Złożone", "Potężne"};
    public static final String[] LEVELS_TIBIA = {"", "Basic", "Intricate", "Powerful"};
    /** Zloto za nasycenie wg stopnia: Canary 5 000 / 30 000 / 200 000 x 0,25 (niezweryfikowana skala). */
    public static final long[] GOLD = {0, 1_250, 7_500, 50_000};
    /** Usuniecie nasycenia u kowala (Canary removecost 15 000 x 0,25). */
    public static final long CLEAR_COST = 3_750;

    private final String label;
    private final String tibiaName;
    private final Category category;
    private final DamageType element;
    private final double[] values;
    private final Target target;
    private final List<Ingredient> ingredients;

    Imbuement(String label, String tibiaName, Category category, DamageType element, double[] values, Target target,
              Ingredient... ingredients) {
        this.label = label;
        this.tibiaName = tibiaName;
        this.category = category;
        this.element = element;
        this.values = values;
        this.target = target;
        this.ingredients = List.of(ingredients);
    }

    private static Ingredient ing(String key, int count) { return new Ingredient(key, count); }

    public String label() { return label; }
    public String tibiaName() { return tibiaName; }
    public Category category() { return category; }
    /** Zywiol zamiany obrazen albo ochrony (null dla kradziezy i krytyka). */
    public DamageType element() { return element; }
    public Target target() { return target; }

    /** Wartosc stopnia 1..3 (procent kradziezy, zamiany, ochrony albo obrazen krytycznych). */
    public double value(int level) { return values[Math.max(1, Math.min(3, level)) - 1]; }

    /** Skladniki stopnia 1..3: stopien n wymaga n pierwszych pozycji (jak w Canary). */
    public List<Ingredient> ingredients(int level) {
        return ingredients.subList(0, Math.max(1, Math.min(3, level)));
    }

    /** Czy to nasycenie wchodzi na ten przedmiot (rodzaj i obrazenia broni). */
    public boolean fits(ItemType t) {
        boolean weapon = t.category() == ItemCategory.WEAPON && t.slot() == EquipSlot.WEAPON;
        switch (target) {
            case WEAPON: return weapon;
            case PHYSICAL_WEAPON: return weapon && t.damageType() == DamageType.PHYSICAL;
            case WEAPON_ARMOR: return weapon || t.slot() == EquipSlot.ARMOR;
            case WEAPON_HELMET: return weapon || t.slot() == EquipSlot.HEAD;
            case ARMOR_SHIELD: return t.slot() == EquipSlot.ARMOR || t.category() == ItemCategory.SHIELD;
            default: return false;
        }
    }

    /** Krotki opis efektu: "kradzież życia 25%". */
    public String effect(int level) {
        String v = ItemBonus.number(value(level));
        switch (category) {
            case LIFE_LEECH: return "kradzież życia " + v + "%";
            case MANA_LEECH: return "kradzież many " + v + "%";
            case CRITICAL: return "krytyk +" + ItemBonus.number(STRIKE_CHANCE) + "% szansy, +" + v + "% obrażeń";
            case ELEMENT_DAMAGE: return v + "% obrażeń fizycznych jako " + element.label();
            case PROTECTION: return "ochrona " + element.label() + " +" + v + "%";
            default: return "";
        }
    }

    /**
     * Liczba miejsc na nasycenia przedmiotu (w Tibii zalezy od przedmiotu; u nas od rodzaju i rzadkosci
     * {@link Rarity}): bron niepospolita 1, rzadka 2, epicka i legendarna 3; zbroja i helm niepospolite i rzadkie 1,
     * epickie i legendarne 2; tarcza od niepospolitej 1; spodnie, buty, pierscienie, amulety i zwykle przedmioty 0.
     */
    public static int slots(ItemType t) {
        if (t == null || t.maxStack() != 1) return 0;
        int r = Rarity.tier(t);
        if (r < 2) return 0;
        if (t.category() == ItemCategory.WEAPON && t.slot() == EquipSlot.WEAPON) return r == 2 ? 1 : r == 3 ? 2 : 3;
        if (t.category() == ItemCategory.SHIELD) return 1;
        if (t.slot() == EquipSlot.ARMOR || t.slot() == EquipSlot.HEAD) return r <= 3 ? 1 : 2;
        return 0;
    }

    public static Imbuement byName(String name) {
        for (Imbuement i : values()) if (i.name().equals(name)) return i;
        return null;
    }
}
