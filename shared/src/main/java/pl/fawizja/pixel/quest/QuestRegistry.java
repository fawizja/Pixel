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

    private QuestRegistry(Map<String, QuestType> byKey) { this.byKey = Collections.unmodifiableMap(byKey); }

    public static QuestRegistry loadDefault(ItemRegistry items, MonsterRegistry monsters) {
        try {
            QuestRegistry r = parse(SpellRegistry.readLines("/data/quests.csv"), items, monsters);
            return r.withChains(SpellRegistry.readLines("/data/quest_chains.csv"), items);
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
                else items.byKey(q.target());
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

    public QuestType get(String key) { return byKey.get(key); }
    public Collection<QuestType> all() { return byKey.values(); }

    public List<QuestType> byGiver(String npcKey) {
        List<QuestType> list = new ArrayList<>();
        for (QuestType q : byKey.values()) if (q.giver().equals(npcKey)) list.add(q);
        return list;
    }
}
