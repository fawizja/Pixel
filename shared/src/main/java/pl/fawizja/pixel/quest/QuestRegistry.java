package pl.fawizja.pixel.quest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.magic.SpellRegistry;
import pl.fawizja.pixel.monster.MonsterRegistry;

/** Zadania z data/quests.csv (cele sprawdzane wzgledem potworow i przedmiotow). */
public final class QuestRegistry {
    private final Map<String, QuestType> byKey;
    private final Map<String, List<String>> prereqs;

    private QuestRegistry(Map<String, QuestType> byKey) { this(byKey, Map.of()); }

    private QuestRegistry(Map<String, QuestType> byKey, Map<String, List<String>> prereqs) {
        this.byKey = Collections.unmodifiableMap(byKey);
        this.prereqs = Collections.unmodifiableMap(prereqs);
    }

    public static QuestRegistry loadDefault(ItemRegistry items, MonsterRegistry monsters) {
        try {
            QuestRegistry r = parse(SpellRegistry.readLines("/data/quests.csv"), items, monsters);
            return r.withChains(SpellRegistry.readLines("/data/quest_chains.csv"), items)
                    .withRequires(SpellRegistry.readLines("/data/quest_requires.csv"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read quests", e);
        }
    }

    public static QuestRegistry parse(List<String> lines, ItemRegistry items, MonsterRegistry monsters) throws IOException {
        Map<String, QuestType> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 10);
            if (f.length != 10) throw new IOException("quests.csv line " + n + ": expected 10 fields");
            try {
                QuestType q = new QuestType(f[0].trim(), f[1].trim(), f[2].trim(), QuestKind.valueOf(f[3].trim()),
                        f[4].trim(), Integer.parseInt(f[5].trim()), Long.parseLong(f[6].trim()),
                        Long.parseLong(f[7].trim()), Integer.parseInt(f[8].trim()), f[9].trim());
                if (q.kind() == QuestKind.KILL) monsters.get(q.target());
                else if (q.kind() == QuestKind.COLLECT) items.byKey(q.target());
                else if (q.amount() != 1) throw new IllegalArgumentException("VISIT needs amount 1: " + q.key());
                if (map.put(q.key(), q) != null) throw new IllegalArgumentException("duplicate quest " + q.key());
            } catch (IllegalArgumentException e) {
                throw new IOException("quests.csv line " + n + ": " + e.getMessage(), e);
            }
        }
        return new QuestRegistry(map);
    }

    /**
     * Lancuchy i nagrody przedmiotami (data/quest_chains.csv): klucz;wymagane zadanie albo -;przedmiot:ilosc albo -.
     * Wymagane zadanie musi miec tego samego zleceniodawce i nie moze tworzyc petli.
     */
    public QuestRegistry withChains(List<String> lines, ItemRegistry items) throws IOException {
        Map<String, QuestType> map = new LinkedHashMap<>(byKey);
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 3) throw new IOException("quest_chains.csv line " + n + ": expected 3 fields");
            QuestType q = map.get(f[0].trim());
            if (q == null) throw new IOException("quest_chains.csv line " + n + ": unknown quest " + f[0]);
            String req = f[1].trim().equals("-") ? null : f[1].trim();
            if (req != null) {
                QuestType prev = map.get(req);
                if (prev == null) throw new IOException("quest_chains.csv line " + n + ": unknown required quest " + req);
                if (!prev.giver().equals(q.giver())) throw new IOException("quest_chains.csv line " + n + ": chain across givers");
            }
            String item = null;
            int count = 0;
            if (!f[2].trim().equals("-")) {
                String[] ic = f[2].trim().split(":");
                if (ic.length != 2 || items.byKey(ic[0]) == null) throw new IOException("quest_chains.csv line " + n + ": bad reward " + f[2]);
                item = ic[0];
                try {
                    count = Integer.parseInt(ic[1]);
                } catch (NumberFormatException e) {
                    throw new IOException("quest_chains.csv line " + n + ": bad reward count", e);
                }
                if (count < 1) throw new IOException("quest_chains.csv line " + n + ": bad reward count");
            }
            map.put(q.key(), q.withChain(req, item, count));
        }
        for (QuestType q : map.values()) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (QuestType c = q; c != null && c.requires() != null; c = map.get(c.requires())) {
                if (!seen.add(c.key())) throw new IOException("quest_chains.csv: loop at " + q.key());
            }
        }
        return new QuestRegistry(map);
    }

    /**
     * Zaleznosci miedzy liniami zadan (data/quest_requires.csv): {@code misja;wymagana1,wymagana2}. Misja jest odblokowana
     * dopiero po oddaniu wszystkich wymaganych zadan (poza lancuchem {@code requires}, ktory laczy misje jednego zleceniodawcy).
     */
    public QuestRegistry withRequires(List<String> lines) throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        int n = 0;
        for (String line : lines) {
            n++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";");
            if (f.length != 2) throw new IOException("quest_requires.csv line " + n + ": expected 2 fields");
            if (!byKey.containsKey(f[0].trim())) throw new IOException("quest_requires.csv line " + n + ": unknown quest " + f[0]);
            List<String> req = new ArrayList<>();
            for (String r : f[1].split(",")) {
                if (!byKey.containsKey(r.trim())) throw new IOException("quest_requires.csv line " + n + ": unknown required quest " + r);
                req.add(r.trim());
            }
            map.put(f[0].trim(), List.copyOf(req));
        }
        // bez petli miedzy liniami
        for (String k : map.keySet()) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(List.of(k));
            while (!todo.isEmpty()) {
                String c = todo.poll();
                for (QuestType q = byKey.get(c); q != null; q = q.requires() == null ? null : byKey.get(q.requires())) {
                    for (String r : prereqs(map, q.key())) {
                        if (r.equals(k)) throw new IOException("quest_requires.csv: loop at " + k);
                        if (seen.add(r)) todo.add(r);
                    }
                }
            }
        }
        return new QuestRegistry(new LinkedHashMap<>(byKey), map);
    }

    private static List<String> prereqs(Map<String, List<String>> m, String key) { return m.getOrDefault(key, List.of()); }

    /** Zadania, ktore trzeba ukonczyc przed ta misja (poza jej poprzednikiem w lancuchu); pusta lista, gdy brak. */
    public List<String> prereqs(String key) { return prereqs(prereqs, key); }

    public QuestType get(String key) { return byKey.get(key); }
    public Collection<QuestType> all() { return byKey.values(); }

    public List<QuestType> byGiver(String npcKey) {
        List<QuestType> list = new ArrayList<>();
        for (QuestType q : byKey.values()) if (q.giver().equals(npcKey)) list.add(q);
        return list;
    }
}
