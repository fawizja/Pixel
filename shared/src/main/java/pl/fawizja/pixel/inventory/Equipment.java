package pl.fawizja.pixel.inventory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;

/** Zalozone przedmioty postaci. Zakladanie i zdejmowanie jest atomowe wzgledem plecaka. */
public final class Equipment {

    private final ItemRegistry registry;
    private final EnumMap<EquipSlot, ItemStack> worn = new EnumMap<>(EquipSlot.class);

    public Equipment(ItemRegistry registry) {
        this.registry = registry;
    }

    public ItemStack get(EquipSlot slot) { return worn.get(slot); }

    public Map<EquipSlot, ItemStack> all() { return Collections.unmodifiableMap(worn); }

    /** Sprawdza, czy dany przedmiot moze zostac zalozony przez postac. */
    public InventoryResult canWear(int itemId, Vocation vocation, int level) {
        ItemType t = registry.get(itemId);
        if (t.slot() == EquipSlot.NONE) return InventoryResult.WRONG_SLOT_TYPE;
        if (!t.usableBy(vocation)) return InventoryResult.WRONG_VOCATION;
        if (level < t.minLevel()) return InventoryResult.LEVEL_TOO_LOW;
        return InventoryResult.OK;
    }

    /**
     * Zaklada caly stos ze slotu plecaka. Jesli slot ekwipunku jest zajety, poprzedni
     * przedmiot trafia w zwolnione miejsce plecaka (zamiana), wiec nic nie ginie.
     */
    public InventoryResult equipFromBackpack(Container backpack, int backpackSlot, Vocation vocation, int level) {
        if (backpackSlot < 0 || backpackSlot >= backpack.size()) return InventoryResult.INVALID_SLOT;
        ItemStack stack = backpack.get(backpackSlot);
        if (stack == null) return InventoryResult.EMPTY_SLOT;
        InventoryResult check = canWear(stack.itemId(), vocation, level);
        if (!check.ok()) return check;
        EquipSlot slot = registry.get(stack.itemId()).slot();
        Container tx = backpack.copy();
        tx.takeFromSlot(backpackSlot, stack.count());
        ItemStack previous = worn.get(slot);
        if (previous != null) {
            InventoryResult r = tx.putInSlot(backpackSlot, previous);
            if (!r.ok()) return r;
        }
        backpack.commitFrom(tx);
        worn.put(slot, stack);
        return InventoryResult.OK;
    }

    /** Zdejmuje przedmiot do plecaka; przy pelnym plecaku nic sie nie zmienia. */
    public InventoryResult unequipToBackpack(EquipSlot slot, Container backpack) {
        ItemStack stack = worn.get(slot);
        if (stack == null) return InventoryResult.EMPTY_SLOT;
        InventoryResult r = backpack.add(stack);
        if (!r.ok()) return r;
        worn.remove(slot);
        return InventoryResult.OK;
    }

    /** Ustawienie bez walidacji - tylko dla fabryki postaci i odczytu zapisu. */
    public void setDirect(EquipSlot slot, ItemStack stack) {
        if (stack == null) worn.remove(slot); else worn.put(slot, stack);
    }

    public int totalArmor() {
        int sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).armor();
        return sum;
    }

    public int totalDefense() {
        int sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).defense();
        return sum;
    }

    public int weaponAttack() {
        ItemStack w = worn.get(EquipSlot.WEAPON);
        return w == null ? 0 : registry.get(w.itemId()).attack();
    }
}
