package pl.fawizja.pixel.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/** Wyszukiwanie przedmiotow postaci (/szukaj): plecak, wyposazenie i depozyt. Wiersze {nazwa, gdzie}. */
public final class ItemSearch {
    public static final int MAX_ROWS = 20;
    public static final int MIN_QUERY = 2;

    private ItemSearch() {}

    public static List<String[]> rows(PlayerCharacter pc, ItemRegistry items, String query) {
        String q = fold(query);
        List<String[]> out = new ArrayList<>();
        if (q.length() < MIN_QUERY) return out;
        // id -> {plecak, zalozone, depozyt}
        Map<Integer, int[]> found = new LinkedHashMap<>();
        count(pc.backpack(), items, q, found, 0);
        for (EquipSlot s : EquipSlot.values()) {
            ItemStack st = pc.equipment().get(s);
            if (st != null && matches(items.get(st.itemId()), q)) found.computeIfAbsent(st.itemId(), k -> new int[3])[1] += st.count();
        }
        count(pc.depot(), items, q, found, 2);
        for (Map.Entry<Integer, int[]> e : found.entrySet()) {
            if (out.size() >= MAX_ROWS) break;
            int[] c = e.getValue();
            List<String> where = new ArrayList<>();
            if (c[0] > 0) where.add("plecak " + c[0]);
            if (c[1] > 0) where.add("założone");
            if (c[2] > 0) where.add("depozyt " + c[2]);
            out.add(new String[] {items.get(e.getKey()).name(), String.join(", ", where)});
        }
        return out;
    }

    private static void count(Container c, ItemRegistry items, String q, Map<Integer, int[]> found, int idx) {
        for (ItemStack s : c.contents()) {
            if (s != null && matches(items.get(s.itemId()), q)) found.computeIfAbsent(s.itemId(), k -> new int[3])[idx] += s.count();
        }
    }

    private static boolean matches(ItemType t, String q) {
        return t != null && (fold(t.name()).contains(q) || fold(t.key()).contains(q));
    }

    public static String fold(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(java.util.Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
