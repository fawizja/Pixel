package pl.fawizja.pixel.achievement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Karta potwora w bestiariuszu: podstawowe dane od pierwszego pokonania, odpornosci od 10,
 * lupy z szansami od {@link #LOOT_KILLS} pokonanych.
 */
public final class BestiaryCard {
    public static final int RESIST_KILLS = 10;
    public static final int LOOT_KILLS = 25;

    private BestiaryCard() {}

    public static String damageName(DamageType t) {
        switch (t) {
            case PHYSICAL: return "fizyczne";
            case FIRE: return "ogień";
            case ICE: return "lód";
            case EARTH: return "ziemia";
            case ENERGY: return "energia";
            case HOLY: return "święte";
            case DEATH: return "śmierć";
            default: return t.name();
        }
    }

    /** Wiersze okna informacji: {etykieta, szczegol}. */
    public static List<String[]> rows(MonsterType t, int kills, ItemRegistry items) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Pokonano", String.format("%,d", kills) + (t.boss() ? " (boss)" : "")});
        if (kills <= 0) {
            rows.add(new String[] {"Nieznany", "pokonaj go, aby poznać szczegóły"});
            return rows;
        }
        rows.add(new String[] {"Poziom i zdrowie", "poziom " + t.level() + ", " + String.format("%,d", t.maxHealth()) + " HP"});
        rows.add(new String[] {"Doświadczenie", String.format("%,d", t.experience()) + " exp"});
        rows.add(new String[] {"Atak", t.attackMin() + "-" + t.attackMax() + " (" + damageName(t.damageType()) + ")"
                + (t.range() > 1 ? ", z dystansu" : "")});
        if (kills < RESIST_KILLS) {
            rows.add(new String[] {"Odporności", "pokonaj jeszcze " + (RESIST_KILLS - kills) + ", aby je poznać"});
        } else {
            rows.add(new String[] {"Odporności", resistances(t)});
        }
        if (kills < LOOT_KILLS) {
            rows.add(new String[] {"Łupy", "pokonaj jeszcze " + (LOOT_KILLS - kills) + ", aby je poznać"});
        } else {
            for (LootEntry e : t.loot()) {
                String name = items.get(e.itemId()).name();
                String count = e.max() > 1 ? " " + e.min() + "-" + e.max() + " szt." : "";
                rows.add(new String[] {name, percent(e.chancePermille()) + count});
            }
            if (t.loot().isEmpty()) rows.add(new String[] {"Łupy", "brak"});
        }
        return rows;
    }

    static String resistances(MonsterType t) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<DamageType, Integer> e : t.resistances().entrySet()) {
            if (e.getValue() == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(damageName(e.getKey())).append(' ').append(e.getValue() > 0 ? "+" : "").append(e.getValue()).append('%');
        }
        if (t.armor() > 0) sb.append(sb.length() > 0 ? ", " : "").append("pancerz ").append(t.armor());
        return sb.length() == 0 ? "brak" : sb.toString();
    }

    /** Szansa w promilach jako tekst: 0,5% albo 12%. */
    static String percent(int permille) {
        if (permille % 10 == 0) return (permille / 10) + "%";
        return (permille / 10) + "," + (permille % 10) + "%";
    }
}
