package pl.fawizja.pixel.magic;

import java.util.Map;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Crafting atomowy: wszystko liczone na kopii plecaka i zatwierdzane tylko w calosci,
 * wiec przy braku miejsca nic nie znika.
 */
public final class CraftingSystem {

    private CraftingSystem() {}

    public static CastResult canCraft(PlayerCharacter pc, Recipe r) {
        if (pc.isDead()) return CastResult.DEAD;
        if (pc.level() < r.minLevel()) return CastResult.LEVEL_TOO_LOW;
        for (Map.Entry<Integer, Integer> e : r.ingredients().entrySet()) {
            if (pc.backpack().count(e.getKey()) < e.getValue()) return CastResult.MISSING_ITEM;
        }
        if (Money.totalValue(pc.backpack()) < r.goldCost()) return CastResult.NOT_ENOUGH_GOLD;
        return CastResult.OK;
    }

    public static CastResult craft(PlayerCharacter pc, Recipe r) {
        CastResult check = canCraft(pc, r);
        if (check != CastResult.OK) return check;
        Container copy = pc.backpack().copy();
        for (Map.Entry<Integer, Integer> e : r.ingredients().entrySet()) {
            if (copy.remove(e.getKey(), e.getValue()) != InventoryResult.OK) return CastResult.MISSING_ITEM;
        }
        if (r.goldCost() > 0) {
            InventoryResult paid = Money.pay(copy, r.goldCost());
            if (paid != InventoryResult.OK) return paid == InventoryResult.NO_SPACE ? CastResult.BACKPACK_FULL : CastResult.NOT_ENOUGH_GOLD;
        }
        if (copy.add(new ItemStack(r.resultItemId(), r.resultCount())) != InventoryResult.OK) return CastResult.BACKPACK_FULL;
        pc.backpack().commitFrom(copy);
        return CastResult.OK;
    }
}
