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
        for (ItemStack s : worn.values()) sum += pl.fawizja.pixel.item.Upgrade.armor(registry.get(s.itemId()), s.upgrade());
        return sum;
    }

    public int totalDefense() {
        int sum = 0;
        for (ItemStack s : worn.values()) sum += pl.fawizja.pixel.item.Upgrade.defense(registry.get(s.itemId()), s.upgrade());
        return sum;
    }

    /**
     * Suma ochron przed rodzajem obrazen ze wszystkich zalozonych przedmiotow (jak w Tibii: ochrony sie sumuja,
     * ujemne oslabiaja), ograniczona do -100..100 (100 = calkowita odpornosc, np. helm glebin na utoniecie).
     */
    public int protection(pl.fawizja.pixel.combat.DamageType type) {
        int sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).bonus().protection(type);
        return Math.max(-100, Math.min(100, sum));
    }

    /** Wszystkie niezerowe ochrony ze sprzetu (okno postaci). */
    public Map<pl.fawizja.pixel.combat.DamageType, Integer> protections() {
        EnumMap<pl.fawizja.pixel.combat.DamageType, Integer> out = new EnumMap<>(pl.fawizja.pixel.combat.DamageType.class);
        for (pl.fawizja.pixel.combat.DamageType t : pl.fawizja.pixel.combat.DamageType.values()) {
            int p = protection(t);
            if (p != 0) out.put(t, p);
        }
        return out;
    }

    /** Suma premii przedmiotow do szansy na krytyk (punkty procentowe). */
    public double critChanceBonus() {
        double sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).bonus().critChance();
        return sum;
    }

    /** Suma premii przedmiotow do obrazen krytycznych (procent). */
    public double critDamageBonus() {
        double sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).bonus().critDamage();
        return sum;
    }

    /** Suma kradziezy zycia z przedmiotow (procent). */
    public double lifeLeech() {
        double sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).bonus().lifeLeech();
        return sum;
    }

    /** Suma kradziezy many z przedmiotow (procent). */
    public double manaLeech() {
        double sum = 0;
        for (ItemStack s : worn.values()) sum += registry.get(s.itemId()).bonus().manaLeech();
        return sum;
    }

    /** Obrona samej tarczy (0, gdy postac nie nosi tarczy). */
    public int shieldDefense() {
        ItemStack s = worn.get(EquipSlot.SHIELD);
        if (s == null) return 0;
        ItemType def = registry.get(s.itemId());
        if (def == null || def.category() != pl.fawizja.pixel.item.ItemCategory.SHIELD) return 0;
        return pl.fawizja.pixel.item.Upgrade.defense(def, s.upgrade());
    }

    /** Rodzaj broni w rece (NONE: pusta reka albo nie bron, np. wedka). */
    public pl.fawizja.pixel.item.WeaponType weaponType() {
        ItemStack w = worn.get(EquipSlot.WEAPON);
        ItemType t = w == null ? null : registry.get(w.itemId());
        if (t == null || t.category() != pl.fawizja.pixel.item.ItemCategory.WEAPON) return pl.fawizja.pixel.item.WeaponType.NONE;
        return t.weaponType();
    }

    /** Klucz broni w rece (np. "crossbow") albo null. */
    public String weaponKey() {
        ItemStack w = worn.get(EquipSlot.WEAPON);
        ItemType t = w == null ? null : registry.get(w.itemId());
        return t == null ? null : t.key();
    }

    public int weaponAttack() {
        ItemStack w = worn.get(EquipSlot.WEAPON);
        return w == null ? 0 : pl.fawizja.pixel.item.Upgrade.attack(registry.get(w.itemId()), w.upgrade());
    }
}
