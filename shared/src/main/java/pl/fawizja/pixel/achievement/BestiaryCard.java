package pl.fawizja.pixel.achievement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Karta potwora w bestiariuszu jak w Tibii (Canary ProtocolGame::parseBestiarysendMonsterData), wg etapu wpisu
 * ({@link BestiaryEntry#stage}):
 * <ul>
 *   <li>etap 1 (od pierwszego zabicia): nazwa, klasa, trudnosc, licznik zabic; lupy tylko jako "?" z kategoria,</li>
 *   <li>etap 2: zdrowie, doswiadczenie, szybkosc, pancerz, sposob walki, punkty charmow; lupy pospolite i niepospolite,</li>
 *   <li>etap 3: odpornosci, miejsca wystepowania, lupy polrzadkie,</li>
 *   <li>ukonczony: wszystkie lupy, punkty charmow przyznane, mozna zalozyc charm Major.</li>
 * </ul>
 * Lupy pokazuja kategorie rzadkosci ({@link LootRarity}), nie procent. Bossowie (w Tibii Bosstiary) maja krotka karte.
 */
public final class BestiaryCard {

    private BestiaryCard() {}

    public static String damageName(DamageType t) {
        return t.label();
    }

    /** Wiersze okna informacji bez miejsc wystepowania i charmow: {etykieta, szczegol}. */
    public static List<String[]> rows(MonsterType t, int kills, ItemRegistry items) {
        return rows(t, kills, items, null, null);
    }

    /** Co odslania etap (opis nastepnego etapu na karcie). */
    static String reveals(int stage) {
        switch (stage) {
            case 1: return "nazwa i klasa";
            case 2: return "statystyki, łupy pospolite i niepospolite";
            case 3: return "odporności, miejsca, łupy półrzadkie";
            default: return "wszystkie łupy i punkty charmów";
        }
    }

    /**
     * Wiersze karty: where = krainy wystepowania ({@link #habitats}; null/pusty = brak), charms = opis charmow
     * zalozonych na tego potwora (null/pusty = brak).
     */
    public static List<String[]> rows(MonsterType t, int kills, ItemRegistry items, String where, String charms) {
        List<String[]> rows = new ArrayList<>();
        BestiaryEntry e = BestiaryData.of(t);
        if (e == null) {
            rows.add(new String[] {"Pokonano", String.format("%,d", kills) + (t.boss() ? " (boss)" : "")});
            if (kills <= 0) {
                rows.add(new String[] {"Nieznany", "pokonaj go, aby poznać szczegóły"});
                return rows;
            }
            rows.add(new String[] {"Zdrowie", String.format("%,d HP, %,d exp, poziom %d", t.maxHealth(), t.experience(), t.level())});
            rows.add(new String[] {"Bestiariusz", t.boss() ? "bossowie nie mają wpisu ani charmów" : "brak wpisu"});
            if (where != null && !where.isEmpty()) rows.add(new String[] {"Występuje", where});
            return rows;
        }
        int stage = e.stage(kills);
        rows.add(new String[] {"Pokonano", String.format("%,d / %,d", kills, e.complete()) + " (etap " + stage + "/" + BestiaryEntry.STAGES + ")"});
        rows.add(new String[] {"Klasa i trudność", BestiaryClasses.label(t.race()) + ", " + e.difficulty().starsText() + " "
                + e.difficulty().label + ", " + e.occurrenceLabel()});
        if (stage == 0) {
            rows.add(new String[] {"Nieznany", "pokonaj go, aby poznać szczegóły"});
            return rows;
        }
        if (stage < BestiaryEntry.STAGES) {
            int next = e.nextThreshold(kills);
            rows.add(new String[] {"Etap " + (stage + 1), String.format("pokonaj jeszcze %,d: %s", next - kills, reveals(stage + 1))});
        }
        if (stage >= 2) {
            rows.add(new String[] {"Zdrowie", String.format("%,d HP, %,d exp, poziom %d", t.maxHealth(), t.experience(), t.level())});
            rows.add(new String[] {"Walka", (t.range() > 1 ? "z dystansu" : "wręcz") + ", szybkość " + t.speed() + ", pancerz " + t.armor()});
            rows.add(new String[] {"Punkty charmów", e.charmPoints() + (stage >= BestiaryEntry.STAGES ? " (zdobyte)" : " (za ukończenie)")});
        }
        if (stage >= 3) {
            rows.add(new String[] {"Odporności", resistances(t)});
            if (where != null && !where.isEmpty()) rows.add(new String[] {"Występuje", where});
        }
        if (charms != null && !charms.isEmpty()) rows.add(new String[] {"Charmy", charms});
        for (LootEntry le : t.loot()) {
            LootRarity r = LootRarity.of(le.chancePermille());
            if (stage >= r.revealStage()) {
                String name = items.get(le.itemId()).name();
                String count = le.max() > 1 ? ", " + le.min() + "-" + le.max() + " szt." : "";
                rows.add(new String[] {name, r.label + count});
            } else {
                rows.add(new String[] {"?", r.label + " (etap " + r.revealStage() + ")"});
            }
        }
        if (t.loot().isEmpty()) rows.add(new String[] {"Łupy", "brak"});
        return rows;
    }

    /** Najwyzej tyle krain wymienia wiersz "Występuje". */
    public static final int HABITATS_SHOWN = 3;

    /**
     * Krainy, w ktorych potwor sie odradza (nazwy regionow mapy, bez powtorzen, w kolejnosci spawnow);
     * "noca" przy krainie, gdzie pojawia sie tylko po zmroku. Pusty tekst, gdy brak spawnow (np. boss z wydarzenia).
     */
    public static String habitats(String key, List<pl.fawizja.pixel.monster.SpawnPoint> spawns, pl.fawizja.pixel.world.WorldMap world) {
        java.util.LinkedHashMap<String, Boolean> names = new java.util.LinkedHashMap<>(); // nazwa -> tylko noca
        for (pl.fawizja.pixel.monster.SpawnPoint sp : spawns) {
            if (!sp.monsterKey().equals(key)) continue;
            pl.fawizja.pixel.world.Region r = world.regionAt(sp.center());
            String name = r == null ? "dzicz" : r.name();
            names.merge(name, sp.nightOnly(), (a, b) -> a && b);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : names.entrySet()) out.add(e.getKey() + (e.getValue() ? " nocą" : ""));
        if (out.size() > HABITATS_SHOWN) {
            int more = out.size() - HABITATS_SHOWN;
            out = new ArrayList<>(out.subList(0, HABITATS_SHOWN));
            out.add("i " + more + " inne");
        }
        return String.join(", ", out);
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
