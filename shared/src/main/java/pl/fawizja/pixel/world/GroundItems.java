package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;

/** Przedmioty lezace na ziemi. Podnoszenie i wyrzucanie przenosza dokladnie te same stosy. */
public final class GroundItems {

    private final Map<Position, List<ItemStack>> items = new HashMap<>();

    public List<ItemStack> at(Position p) {
        List<ItemStack> l = items.get(p);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    public void place(Position p, ItemStack stack) {
        items.computeIfAbsent(p, k -> new ArrayList<>()).add(stack);
    }

    /** Wyrzuca czesc stosu z plecaka na kafelek. */
    public InventoryResult dropFrom(Container backpack, int slot, int count, Position where, WorldMap world) {
        if (slot < 0 || slot >= backpack.size()) return InventoryResult.INVALID_SLOT;
        if (!world.isWalkable(where)) return InventoryResult.INVALID_SLOT;
        ItemStack taken = backpack.takeFromSlot(slot, count);
        if (taken == null) return backpack.get(slot) == null ? InventoryResult.EMPTY_SLOT : InventoryResult.INVALID_AMOUNT;
        place(where, taken);
        return InventoryResult.OK;
    }

    /** Podnosi stos o indeksie z kafelka do plecaka; przy braku miejsca zostaje na ziemi. */
    public InventoryResult pickUp(Position where, int index, Container backpack) {
        List<ItemStack> l = items.get(where);
        if (l == null || index < 0 || index >= l.size()) return InventoryResult.EMPTY_SLOT;
        ItemStack s = l.get(index);
        InventoryResult r = backpack.add(s);
        if (!r.ok()) return r;
        l.remove(index);
        if (l.isEmpty()) items.remove(where);
        return InventoryResult.OK;
    }

    /** Wszystkie stosy w promieniu (to samo pietro), np. do wyslania klientowi. */
    public void forEachNear(Position c, int radius, java.util.function.BiConsumer<Position, ItemStack> out) {
        for (Map.Entry<Position, List<ItemStack>> e : items.entrySet()) {
            Position p = e.getKey();
            if (p.z() != c.z() || p.distanceTo(c) > radius) continue;
            for (ItemStack s : e.getValue()) out.accept(p, s);
        }
    }

    public void clear() { items.clear(); }

    public int totalStacks() {
        int n = 0;
        for (List<ItemStack> l : items.values()) n += l.size();
        return n;
    }
}
