package pl.fawizja.pixel.quest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.i18n.Messages;
import pl.fawizja.pixel.magic.SpellRegistry;

/**
 * Linie zadan do dziennika (data/quest_lines.csv): linia to lancuch misji ({@code quest_chains.csv}) od pierwszej
 * misji, z poziomem trudnosci (jak w Tibii: latwe, srednie, srednio trudne, trudne, ekstremalne) i znacznikiem premium.
 * Misja bez wiersza w pliku tworzy linie z domyslnym poziomem wg poziomu misji.
 */
public final class QuestLines {

    public enum Tier { EASY, MEDIUM, MEDIUMHARD, HARD, EXTREME }

    /** Linia: pierwsza misja (klucz linii), kolejne misje w kolejnosci lancucha, trudnosc i konto. */
    public record Line(String key, String name, Tier tier, boolean premium, String where, List<QuestType> missions) {
        public QuestType first() { return missions.get(0); }
        public int minLevel() { return first().minLevel(); }
        public String giver() { return first().giver(); }
    }

    private final Map<String, Line> lines;

    private QuestLines(Map<String, Line> lines) { this.lines = Collections.unmodifiableMap(lines); }

    public static QuestLines loadDefault(QuestRegistry registry) {
        try {
            return parse(SpellRegistry.readLines("/data/quest_lines.csv"), registry);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read quest lines", e);
        }
    }

    public static Tier defaultTier(int level) {
        return level < 30 ? Tier.EASY : level < 60 ? Tier.MEDIUM : level < 120 ? Tier.MEDIUMHARD : level < 250 ? Tier.HARD : Tier.EXTREME;
    }

    public static QuestLines parse(List<String> csv, QuestRegistry registry) throws IOException {
        Map<String, String[]> rows = new LinkedHashMap<>();
        int n = 0;
        for (String l : csv) {
            n++;
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", -1);
            if (f.length != 5) throw new IOException("quest_lines.csv line " + n + ": expected 5 fields");
            if (registry.get(f[0].trim()) == null) throw new IOException("quest_lines.csv line " + n + ": unknown quest " + f[0]);
            rows.put(f[0].trim(), f);
        }
        Map<String, Line> out = new LinkedHashMap<>();
        for (QuestType root : registry.all()) {
            if (root.requires() != null) continue;
            List<QuestType> chain = new ArrayList<>();
            QuestType cur = root;
            while (cur != null) {
                chain.add(cur);
                QuestType prev = cur;
                cur = null;
                for (QuestType q : registry.all()) if (prev.key().equals(q.requires())) { cur = q; break; }
            }
            String[] f = rows.get(root.key());
            Tier tier = defaultTier(root.minLevel());
            boolean premium = false;
            String name = root.name(), where = null;
            if (f != null) {
                try {
                    tier = Tier.valueOf(f[1].trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IOException("quest_lines.csv: bad tier for " + root.key(), e);
                }
                premium = f[2].trim().equals("1");
                if (!f[3].trim().isEmpty()) name = Messages.data("quest", root.key(), "line", f[3].trim());
                if (!f[4].trim().isEmpty()) where = Messages.data("quest", root.key(), "where", f[4].trim());
            }
            out.put(root.key(), new Line(root.key(), name, tier, premium, where, List.copyOf(chain)));
        }
        return new QuestLines(out);
    }

    public java.util.Collection<Line> all() { return lines.values(); }

    public Line get(String rootKey) { return lines.get(rootKey); }

    /** Linia, do ktorej nalezy misja. */
    public Line lineOf(String missionKey) {
        for (Line l : lines.values()) for (QuestType q : l.missions()) if (q.key().equals(missionKey)) return l;
        return null;
    }
}
