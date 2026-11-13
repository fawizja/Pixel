package pl.fawizja.pixel.npc;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.magic.SpellRegistry;

/**
 * Zasady rejsow na nowe wyspy (data/ship_access.csv): kapitan celu; znacznik postaci potrzebny, by tam doplynac (ustawia
 * go oddanie wskazanego zadania, {@code quest/StarQuest.onCompleted}); czy rejs wymaga konta premium (0 = darmowy prom).
 * Rejs jest darmowy tylko wtedy, gdy oba konce to kapitanowie z wierszem "0"; w pozostalych przypadkach wymaga
 * {@link PlayerCharacter#canSail()}. Znacznik sprawdza sie po stronie celu (powrot z wyspy jest zawsze mozliwy).
 */
public final class ShipAccess {
    private ShipAccess() {}

    public record Rule(String captain, String flag, boolean premium, String grantedBy, String hint) {}

    public enum Block { NONE, PREMIUM, FLAG }

    private static volatile Map<String, Rule> rules;

    public static Map<String, Rule> rules() {
        Map<String, Rule> r = rules;
        if (r == null) {
            synchronized (ShipAccess.class) {
                if (rules == null) rules = load();
                r = rules;
            }
        }
        return r;
    }

    private static Map<String, Rule> load() {
        try {
            Map<String, Rule> out = new LinkedHashMap<>();
            List<String> lines = SpellRegistry.readLines("/data/ship_access.csv");
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String[] f = t.split(";", -1);
                if (f.length < 4) throw new IOException("ship_access.csv: expected 4-5 fields: " + line);
                out.put(f[0].trim(), new Rule(f[0].trim(), "-".equals(f[1].trim()) ? null : f[1].trim(), "1".equals(f[2].trim()),
                        "-".equals(f[3].trim()) ? null : f[3].trim(), f.length > 4 ? f[4].trim() : ""));
            }
            return java.util.Collections.unmodifiableMap(out);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read ship access rules", e);
        }
    }

    public static Rule rule(String captainKey) { return rules().get(captainKey); }

    /** Darmowy prom: kapitan z wierszem bez wymogu premium. */
    public static boolean freeFerry(String captainKey) {
        Rule r = rule(captainKey);
        return r != null && !r.premium();
    }

    /** Co blokuje rejs z kapitana {@code from} do kapitana {@code to} dla postaci (NONE = mozna plynac). */
    public static Block check(PlayerCharacter pc, String from, String to) {
        boolean free = freeFerry(from) && freeFerry(to);
        if (!free && !pc.canSail()) return Block.PREMIUM;
        Rule r = rule(to);
        if (r != null && r.flag() != null && !pc.hasQuestFlag(r.flag())) return Block.FLAG;
        return Block.NONE;
    }

    /** Znacznik rejsu ustawiany po oddaniu zadania albo null. */
    public static String grantedFlag(String questKey) {
        for (Rule r : rules().values()) if (questKey.equals(r.grantedBy())) return r.flag();
        return null;
    }

    /** Podpowiedz kapitana celu, gdy brakuje znacznika (w jezyku gry). */
    public static String hint(String to) {
        Rule r = rule(to);
        return Messages.data("npc", to, "shiphint", r == null || r.hint().isEmpty() ? Messages.get("ship.locked") : r.hint());
    }
}
