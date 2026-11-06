package pl.fawizja.pixel.achievement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.charm.Charm;
import pl.fawizja.pixel.charm.Charms;
import pl.fawizja.pixel.monster.MonsterType;

/**
 * Wiersze okien bestiariusza (komputer i telefon, wspolne okna HUD): lista klas jak w Tibii, lista potworow klasy
 * z gwiazdkami i etapem. Wiersz = {etykieta, opis, stan, akcja[, "off"]} jak w Hud.openInfo.
 */
public final class BestiaryView {
    /** Akcja otwarcia klasy: "bestclass:Klasa[:strona]"; potwora: "best:klucz"; bossowie: klasa {@link #BOSSES}. */
    public static final String CLASS_ACTION = "bestclass:";
    public static final String BOSSES = "Boss";
    public static final String TITLE = "Bestiariusz";

    private BestiaryView() {}

    /** Potwory bestiariusza (bez bossow i bez wpisu) wg klasy Tibii, w kolejnosci klas Tibii, w klasie wg poziomu. */
    public static Map<String, List<MonsterType>> byClass(Collection<MonsterType> types) {
        Map<String, List<MonsterType>> tmp = new LinkedHashMap<>();
        for (MonsterType t : types) {
            if (BestiaryData.of(t) == null) continue;
            tmp.computeIfAbsent(t.race(), k -> new ArrayList<>()).add(t);
        }
        Map<String, List<MonsterType>> out = new LinkedHashMap<>();
        for (String c : BestiaryClasses.all()) if (tmp.containsKey(c)) out.put(c, tmp.remove(c));
        out.putAll(tmp); // klasy spoza listy Tibii (nie powinno ich byc)
        for (List<MonsterType> l : out.values()) l.sort(Comparator.comparingInt(MonsterType::level).thenComparing(MonsterType::key));
        return out;
    }

    /** Wiersz charmow (punkty, echa, sloty) na gorze bestiariusza i okna charmow. */
    public static String[] charmsRow(PlayerCharacter pc) {
        Charms c = pc.charms();
        return new String[] {"Charmy", String.format("%,d pkt charmów, %,d ech, sloty %d/%d", c.points(), c.echoes(), c.used(),
                Charms.slots(pc.isPremium())), "", "charm:open"};
    }

    /** Lista klas: poznane / wszystkie i ukonczone; na koncu bossowie (gdy jakiegos pokonano). */
    public static List<String[]> classRows(PlayerCharacter pc, Collection<MonsterType> types) {
        List<String[]> rows = new ArrayList<>();
        Bestiary b = pc.bestiary();
        for (Map.Entry<String, List<MonsterType>> e : byClass(types).entrySet()) {
            int known = 0, done = 0;
            for (MonsterType t : e.getValue()) {
                int k = b.kills(t.key());
                if (k > 0) known++;
                if (k >= BestiaryData.of(t).complete()) done++;
            }
            rows.add(new String[] {BestiaryClasses.label(e.getKey()), String.format("poznane %d/%d, ukończone %d", known,
                    e.getValue().size(), done), "", CLASS_ACTION + e.getKey()});
        }
        if (b.bossesDefeated() > 0) {
            rows.add(new String[] {"Bossowie", "pokonani: " + b.bossesDefeated() + " (bez wpisu i charmów)", "", CLASS_ACTION + BOSSES});
        }
        return rows;
    }

    /** Potwory klasy: gwiazdki, etap, zabicia; nieznane jako "?". Bossowie: pokonani bossowie. */
    public static List<String[]> speciesRows(PlayerCharacter pc, Collection<MonsterType> types, String tibiaClass) {
        List<String[]> rows = new ArrayList<>();
        Bestiary b = pc.bestiary();
        if (BOSSES.equals(tibiaClass)) {
            for (MonsterType t : types) {
                if (!t.boss() || b.kills(t.key()) <= 0) continue;
                rows.add(new String[] {t.name(), String.format("pokonano %,d", b.kills(t.key())), "", "best:" + t.key()});
            }
            return rows;
        }
        List<MonsterType> list = byClass(types).get(tibiaClass);
        if (list == null) return rows;
        for (MonsterType t : list) {
            BestiaryEntry e = BestiaryData.of(t);
            int k = b.kills(t.key());
            String stars = e.difficulty().starsText();
            if (k <= 0) {
                rows.add(new String[] {"?", stars + " nie spotkany", "", "ach:none", "off"});
                continue;
            }
            int stage = e.stage(k);
            StringBuilder charms = new StringBuilder();
            for (Charm c : pc.charms().charmsOn(t.key())) charms.append(charms.length() == 0 ? " [" : ", ").append(c.label());
            if (charms.length() > 0) charms.append(']');
            String progress = stage >= BestiaryEntry.STAGES ? "ukończony" : String.format("%,d/%,d", k, e.complete());
            rows.add(new String[] {t.name() + charms, stars + " etap " + stage + "/" + BestiaryEntry.STAGES + ", " + progress, "",
                    "best:" + t.key()});
        }
        return rows;
    }

    /** Opis charmow zalozonych na potwora (karta): "Rana II, Zew pustki I"; pusty, gdy brak. */
    public static String charmsOn(PlayerCharacter pc, String monsterKey) {
        StringBuilder sb = new StringBuilder();
        for (Charm c : pc.charms().charmsOn(monsterKey)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.labelWithTier(pc.charms().tier(c)));
        }
        return sb.toString();
    }

    /**
     * Strona listy: wiersze [od, od+na_strone) i wiersz "Strona x/y" z akcja pageAction + nastepna strona (po
     * ostatniej - pierwsza). capacity = ile wierszy miesci okno (razem z wierszem strony).
     */
    public static List<String[]> page(List<String[]> all, int page, int capacity, String pageAction) {
        int cap = Math.max(2, capacity);
        if (all.size() <= cap) return all;
        int per = cap - 1;
        int pages = (all.size() + per - 1) / per;
        int p = Math.floorMod(page, pages);
        List<String[]> out = new ArrayList<>(all.subList(p * per, Math.min(all.size(), (p + 1) * per)));
        out.add(new String[] {"Strona " + (p + 1) + " / " + pages, "dotknij, aby zobaczyć następną", "", pageAction + ((p + 1) % pages)});
        return out;
    }
}
