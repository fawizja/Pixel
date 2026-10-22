package pl.fawizja.pixel.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Pojemnik o stalej liczbie slotow (plecak ma 20). Kazda operacja jest atomowa:
 * albo wykonuje sie w calosci, albo nie zmienia stanu - to chroni przed utrata
 * i duplikacja przedmiotow.
 */
public final class Container {

    public static final int BACKPACK_SLOTS = 20;

    private final ItemRegistry registry;
    private ItemStack[] slots;

    public Container(ItemRegistry registry, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
        this.registry = Objects.requireNonNull(registry);
        this.slots = new ItemStack[size];
    }

    public static Container backpack(ItemRegistry registry) {
        return new Container(registry, BACKPACK_SLOTS);
    }

    public int size() { return slots.length; }

    /**
     * Zmienia liczbe slotow (dodatkowe plecaki). Powiekszenie zawsze sie udaje; pomniejszenie tylko wtedy,
     * gdy usuwane sloty sa puste - przedmioty nigdy nie znikaja.
     */
    public boolean resize(int size) {
        if (size <= 0) return false;
        for (int i = size; i < slots.length; i++) if (slots[i] != null) return false;
        slots = Arrays.copyOf(slots, size);
        return true;
    }

    public ItemStack get(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    public boolean isEmpty(int slot) { return get(slot) == null; }

    public int freeSlots() {
        int n = 0;
        for (ItemStack s : slots) if (s == null) n++;
        return n;
    }

    public boolean isFull() { return freeSlots() == 0; }

    public int count(int itemId) {
        long n = 0;
        for (ItemStack s : slots) if (s != null && s.itemId() == itemId) n += s.count();
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : slots) if (s != null) list.add(s);
        return list;
    }

    /** Ile sztuk danego przedmiotu zmiesci sie jeszcze w pojemniku. */
    public long spaceFor(int itemId) {
        int max = registry.maxStack(itemId);
        long space = 0;
        for (ItemStack s : slots) {
            if (s == null) space += max;
            else if (max > 1 && s.itemId() == itemId) space += max - s.count();
        }
        return space;
    }

    public boolean canAdd(int itemId, int count) {
        return count > 0 && spaceFor(itemId) >= count;
    }

    /** Dodaje caly stos (dopelniajac istniejace stosy) albo nic, jesli sie nie zmiesci. */
    public InventoryResult add(ItemStack stack) {
        Objects.requireNonNull(stack);
        if (!canAdd(stack.itemId(), stack.count())) return InventoryResult.NO_SPACE;
        int max = registry.maxStack(stack.itemId());
        int remaining = stack.count();
        if (max > 1) {
            for (int i = 0; i < slots.length && remaining > 0; i++) {
                ItemStack s = slots[i];
                if (s != null && s.itemId() == stack.itemId() && s.count() < max) {
                    int put = Math.min(max - s.count(), remaining);
                    slots[i] = s.withCount(s.count() + put);
                    remaining -= put;
                }
            }
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] == null) {
                int put = Math.min(max, remaining);
                slots[i] = stack.withCount(put); // zachowuje ulepszenie
                remaining -= put;
            }
        }
        if (remaining != 0) throw new IllegalStateException("capacity check inconsistent");
        return InventoryResult.OK;
    }

    /**
     * Usuwa podana ilosc przedmiotu z dowolnych stosow (od konca) albo nic, gdy jest za malo.
     * Najpierw zwykle sztuki, ulepszone dopiero na koncu.
     */
    public InventoryResult remove(int itemId, int count) {
        if (count <= 0) return InventoryResult.INVALID_AMOUNT;
        if (count(itemId) < count) return InventoryResult.NOT_ENOUGH_ITEMS;
        int remaining = count;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = slots.length - 1; i >= 0 && remaining > 0; i--) {
                ItemStack s = slots[i];
                if (s != null && s.itemId() == itemId && (s.upgrade() > 0) == (pass == 1)) {
                    int take = Math.min(s.count(), remaining);
                    slots[i] = take == s.count() ? null : s.withCount(s.count() - take);
                    remaining -= take;
                }
            }
        }
        return InventoryResult.OK;
    }

    /** Ilosc sztuk bez ulepszenia (tylko takie mozna sprzedac, oddac w handlu, wystawic albo wyslac). */
    public int countPlain(int itemId) {
        int n = 0;
        for (ItemStack s : slots) if (s != null && s.itemId() == itemId && s.upgrade() == 0) n += s.count();
        return n;
    }

    /** Wyjmuje czesc lub caly stos ze slotu. Zwraca wyjety stos albo null przy blednych danych. */
    public ItemStack takeFromSlot(int slot, int count) {
        checkSlot(slot);
        ItemStack s = slots[slot];
        if (s == null || count <= 0 || count > s.count()) return null;
        slots[slot] = count == s.count() ? null : s.withCount(s.count() - count);
        return s.withCount(count);
    }

    /** Wstawia stos do konkretnego pustego slotu. */
    public InventoryResult putInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slots.length) return InventoryResult.INVALID_SLOT;
        if (slots[slot] != null) return InventoryResult.NO_SPACE;
        if (stack.count() > registry.maxStack(stack.itemId())) return InventoryResult.INVALID_AMOUNT;
        slots[slot] = stack;
        return InventoryResult.OK;
    }

    /**
     * Przenosi przedmioty miedzy slotami: do pustego slotu, laczy takie same stosy
     * albo zamienia miejscami cale stosy.
     */
    public InventoryResult move(int from, int to, int count) {
        if (from < 0 || from >= slots.length || to < 0 || to >= slots.length) return InventoryResult.INVALID_SLOT;
        ItemStack src = slots[from];
        if (src == null) return InventoryResult.EMPTY_SLOT;
        if (count <= 0 || count > src.count()) return InventoryResult.INVALID_AMOUNT;
        if (from == to) return InventoryResult.OK;
        ItemStack dst = slots[to];
        int max = registry.maxStack(src.itemId());
        if (dst == null) {
            slots[to] = src.withCount(count);
            slots[from] = count == src.count() ? null : src.withCount(src.count() - count);
            return InventoryResult.OK;
        }
        if (dst.itemId() == src.itemId() && max > 1) {
            int put = Math.min(count, max - dst.count());
            if (put <= 0) return InventoryResult.NO_SPACE;
            slots[to] = dst.withCount(dst.count() + put);
            slots[from] = put == src.count() ? null : src.withCount(src.count() - put);
            return InventoryResult.OK;
        }
        if (count != src.count()) return InventoryResult.NO_SPACE;
        slots[to] = src;
        slots[from] = dst;
        return InventoryResult.OK;
    }

    /** Laczy wszystkie niepelne stosy tego samego typu (porzadkowanie plecaka). */
    public void compactStacks() {
        for (int i = 0; i < slots.length; i++) {
            ItemStack a = slots[i];
            if (a == null) continue;
            int max = registry.maxStack(a.itemId());
            if (max <= 1) continue;
            for (int j = i + 1; j < slots.length && slots[i].count() < max; j++) {
                ItemStack b = slots[j];
                if (b != null && b.itemId() == a.itemId()) {
                    int put = Math.min(max - slots[i].count(), b.count());
                    slots[i] = slots[i].withCount(slots[i].count() + put);
                    slots[j] = put == b.count() ? null : b.withCount(b.count() - put);
                }
            }
        }
    }

    /**
     * Porzadkuje plecak: laczy stosy, potem ustawia przedmioty wg kategorii (monety, bron, tarcze, zbroje,
     * amunicja, mikstury...), w kategorii wg rodzaju i od najwyzszego ulepszenia. Puste sloty trafiaja na koniec.
     * Zwraca true, gdy cokolwiek sie przesunelo.
     */
    public boolean sort() {
        ItemStack[] before = slots.clone();
        compactStacks();
        List<ItemStack> items = contents();
        items.sort(java.util.Comparator
                .comparingInt((ItemStack s) -> registry.get(s.itemId()).category().ordinal())
                .thenComparingInt(ItemStack::itemId)
                .thenComparing(ItemStack::upgrade, java.util.Comparator.reverseOrder())
                .thenComparing(ItemStack::count, java.util.Comparator.reverseOrder()));
        Arrays.fill(slots, null);
        for (int i = 0; i < items.size(); i++) slots[i] = items.get(i);
        return !Arrays.equals(before, slots);
    }

    /** Kopia do bezpiecznych operacji transakcyjnych (zmien kopie, potem commitFrom). */
    public Container copy() {
        Container c = new Container(registry, slots.length);
        System.arraycopy(slots, 0, c.slots, 0, slots.length);
        return c;
    }

    /** Zatwierdza stan z kopii utworzonej przez {@link #copy()}. */
    public void commitFrom(Container other) {
        if (other.slots.length != slots.length) throw new IllegalArgumentException("size mismatch");
        System.arraycopy(other.slots, 0, slots, 0, slots.length);
    }

    public void clear() { Arrays.fill(slots, null); }

    public ItemRegistry registry() { return registry; }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException("slot " + slot);
    }
}
