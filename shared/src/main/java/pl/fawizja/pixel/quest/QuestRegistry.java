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
            return parse(SpellRegistry.readLines("/data/quests.csv"), items, monsters);
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

    public QuestType get(String key) { return byKey.get(key); }
    public Collection<QuestType> all() { return byKey.values(); }

    public List<QuestType> byGiver(String npcKey) {
        List<QuestType> list = new ArrayList<>();
        for (QuestType q : byKey.values()) if (q.giver().equals(npcKey)) list.add(q);
        return list;
    }
}
