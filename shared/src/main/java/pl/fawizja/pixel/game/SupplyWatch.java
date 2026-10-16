package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;

/**
 * Ostrzezenia o koncu zapasow na polowaniu: mikstury, runy i amunicja w plecaku.
 * Komunikat tylko przy spadku ponizej {@link #LOW} albo do zera (zakupy i depozyt nie ostrzegaja).
 */
public final class SupplyWatch {
    public static final int LOW = 5;

    private final List<ItemType> watched = new ArrayList<>();
    private final Map<Integer, Integer> last = new HashMap<>();

    public SupplyWatch(ItemRegistry items) {
        for (ItemType t : items.all()) {
            ItemCategory c = t.category();
            if (c == ItemCategory.POTION || c == ItemCategory.RUNE || c == ItemCategory.AMMUNITION) watched.add(t);
        }
    }

    /** Sprawdza plecak; hunting = poza strefa ochronna. Zwraca ostrzezenia do dziennika. */
    public List<String> check(Container backpack, boolean hunting) {
        List<String> out = new ArrayList<>();
        for (ItemType t : watched) {
            int now = backpack.count(t.id());
            Integer before = last.put(t.id(), now);
            if (!hunting || before == null || now >= before) continue;
            if (now == 0) out.add("Skończyło się: " + t.name() + ".");
            else if (before > LOW && now <= LOW) out.add("Zostało tylko " + now + ": " + t.name() + ".");
        }
        return out;
    }
}
