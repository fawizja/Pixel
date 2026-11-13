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
    private java.util.function.LongSupplier weightLimit;

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

    /** Ile sztuk w tym pojemniku (bez zagniezdzonych plecakow). */
    private int countLocal(int itemId) {
        long n = 0;
        for (ItemStack s : slots) if (s != null && s.itemId() == itemId) n += s.count();
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /** Ile sztuk w pojemniku i wszystkich plecakach w nim (dowolnie gleboko). */
    public int count(int itemId) {
        long n = countLocal(itemId);
        for (ItemStack s : slots) if (s != null && s.contents() != null) n += s.contents().count(itemId);
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : slots) if (s != null) list.add(s);
        return list;
    }

    /** Ile sztuk danego przedmiotu zmiesci sie jeszcze w tym pojemniku, bez zagniezdzonych plecakow. */
    private long spaceLocal(int itemId) {
        int max = registry.maxStack(itemId);
        long space = 0;
        for (ItemStack s : slots) {
            if (s == null) space += max;
            else if (max > 1 && s.itemId() == itemId) space += max - s.count();
        }
        return space;
    }

    /**
     * Ile sztuk danego przedmiotu zmiesci sie jeszcze w pojemniku - razem z plecakami w plecaku, takze tymi,
     * ktorych jeszcze nie otwarto (pusty plecak ma wszystkie pola wolne).
     */
    public long spaceFor(int itemId) {
        long space = spaceLocal(itemId);
        int max = registry.maxStack(itemId);
        for (ItemStack s : slots) {
            if (s == null) continue;
            if (s.contents() != null) space += s.contents().spaceFor(itemId);
            else if (bagType(s) != null) space += (long) bagType(s).slots() * max;
        }
        return space;
    }

    public boolean canAdd(int itemId, int count) {
        if (count <= 0) return false;
        if (overweight((long) ItemWeights.unit(registry, itemId) * count)) return false;
        if (spaceFor(itemId) >= count) return true;
        return isCoin(itemId) && copy().addCoinsByValue(itemId, count);
    }

    /**
     * Dodaje caly stos (dopelniajac istniejace stosy) albo nic, jesli sie nie zmiesci.
     * Monety lacza sie same jak w Tibii: 100 zlotych zamienia sie w 1 platynowa, 100 platynowych w 1 krysztalowa.
     */
    public InventoryResult add(ItemStack stack) {
        Objects.requireNonNull(stack);
        if (overweight(ItemWeights.of(registry, stack))) return InventoryResult.TOO_HEAVY;
        if (isCoin(stack.itemId())) {
            if (spaceLocal(stack.itemId()) >= stack.count()) {
                addPlain(stack);
                normalizeCoins();
                return InventoryResult.OK;
            }
            // plecak pelny, ale po wymianie na wyzsze nominaly monety sie zmieszcza
            Container tx = copy();
            if (tx.addCoinsByValue(stack.itemId(), stack.count())) {
                commitFrom(tx);
                return InventoryResult.OK;
            }
            if (spaceFor(stack.itemId()) < stack.count()) return InventoryResult.NO_SPACE;
            addDeep(stack);
            return InventoryResult.OK;
        }
        if (spaceFor(stack.itemId()) < stack.count()) return InventoryResult.NO_SPACE;
        addDeep(stack);
        return InventoryResult.OK;
    }

    /**
     * Dodaje stos jak w Tibii: najpierw dopelnia istniejace stosy (w tym pojemniku, potem w plecakach w glab),
     * potem zajmuje wolne pola - najpierw tego pojemnika, a gdy pelny, pierwszego plecaka z miejscem (w glab).
     * Miejsce sprawdza wolajacy ({@link #spaceFor}).
     */
    private void addDeep(ItemStack stack) {
        int remaining = stack.count();
        if (registry.maxStack(stack.itemId()) > 1) remaining = mergeDeep(stack, remaining);
        if (remaining > 0) remaining = fillDeep(stack, remaining);
        if (remaining != 0) throw new IllegalStateException("capacity check inconsistent");
    }

    private int mergeDeep(ItemStack stack, int remaining) {
        int max = registry.maxStack(stack.itemId());
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack s = slots[i];
            if (s != null && s.itemId() == stack.itemId() && s.isPlain() == stack.isPlain() && s.upgrade() == stack.upgrade()
                    && s.tier() == stack.tier() && s.count() < max) {
                int put = Math.min(max - s.count(), remaining);
                slots[i] = s.withCount(s.count() + put);
                remaining -= put;
            }
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] != null && slots[i].contents() != null) remaining = slots[i].contents().mergeDeep(stack, remaining);
        }
        return remaining;
    }

    private int fillDeep(ItemStack stack, int remaining) {
        int max = registry.maxStack(stack.itemId());
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] == null) {
                int put = Math.min(max, remaining);
                slots[i] = stack.withCount(put); // zachowuje ulepszenie i zawartosc plecaka
                remaining -= put;
            }
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            Container child = child(i, true);
            if (child != null) remaining = child.fillDeep(stack, remaining);
        }
        return remaining;
    }

    private void addPlain(ItemStack stack) {
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
    }

    // ------------------------------------------------------------------ monety

    /** Monety (zlota, platynowa, krysztalowa) - lacza sie i wymieniaja same. */
    private boolean isCoin(int itemId) {
        return pl.fawizja.pixel.economy.Currency.isCoin(itemId) && registry.contains(itemId)
                && registry.get(itemId).category() == pl.fawizja.pixel.item.ItemCategory.CURRENCY;
    }

    private long coinValue() {
        long v = 0;
        for (pl.fawizja.pixel.economy.Currency c : pl.fawizja.pixel.economy.Currency.values()) {
            if (isCoin(c.itemId())) v += (long) countLocal(c.itemId()) * c.value();
        }
        return v;
    }

    /**
     * Wymienia kazde pelne 100 monet na monete wyzszego nominalu i laczy niepelne stosy monet.
     * Wartosc pieniedzy sie nie zmienia, a miejsca w pojemniku nigdy nie ubywa (zdjecie 100 monet
     * zawsze zwalnia co najmniej jedno pole). Zwraca true, gdy cos sie zmienilo.
     */
    public boolean normalizeCoins() {
        boolean changed = false;
        pl.fawizja.pixel.economy.Currency[] cs = pl.fawizja.pixel.economy.Currency.values();
        for (int i = 0; i + 1 < cs.length; i++) {
            if (!isCoin(cs[i].itemId()) || !isCoin(cs[i + 1].itemId())) continue;
            int rate = (int) (cs[i + 1].value() / cs[i].value());
            while (countLocal(cs[i].itemId()) >= rate) {
                removeLocal(cs[i].itemId(), rate);
                addPlain(ItemStack.of(cs[i + 1].itemId()));
                changed = true;
            }
        }
        // niepelne stosy tej samej monety w jeden (do pierwszego pola z ta moneta)
        for (int i = 0; i < slots.length; i++) {
            ItemStack a = slots[i];
            if (a == null || !isCoin(a.itemId())) continue;
            int max = registry.maxStack(a.itemId());
            for (int j = i + 1; j < slots.length && slots[i].count() < max; j++) {
                ItemStack b = slots[j];
                if (b != null && b.itemId() == a.itemId()) {
                    int put = Math.min(max - slots[i].count(), b.count());
                    slots[i] = slots[i].withCount(slots[i].count() + put);
                    slots[j] = put == b.count() ? null : b.withCount(b.count() - put);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Dodaje monety wg wartosci, gdy dosypanie stosu sie nie miesci: wszystkie monety zostaja przeliczone
     * na najmniejsza liczbe sztuk (krysztalowe, platynowe, zlote). false, gdy i tak brak miejsca (pojemnik
     * jest wtedy w nieokreslonym stanie - wolac tylko na kopii).
     */
    private boolean addCoinsByValue(int itemId, int count) {
        pl.fawizja.pixel.economy.Currency cur = pl.fawizja.pixel.economy.Currency.byItemId(itemId);
        long total = coinValue() + (long) count * cur.value();
        for (pl.fawizja.pixel.economy.Currency c : pl.fawizja.pixel.economy.Currency.values()) {
            if (!isCoin(c.itemId())) continue;
            int n = countLocal(c.itemId());
            if (n > 0) removeLocal(c.itemId(), n);
        }
        pl.fawizja.pixel.economy.Currency[] cs = pl.fawizja.pixel.economy.Currency.values();
        for (int i = cs.length - 1; i >= 0 && total > 0; i--) {
            if (!isCoin(cs[i].itemId())) continue;
            long n = total / cs[i].value();
            total -= n * cs[i].value();
            int max = registry.maxStack(cs[i].itemId());
            while (n > 0) {
                int chunk = (int) Math.min(n, max);
                if (spaceLocal(cs[i].itemId()) < chunk) return false;
                addPlain(new ItemStack(cs[i].itemId(), chunk));
                n -= chunk;
            }
        }
        return total == 0;
    }

    /**
     * Usuwa podana ilosc przedmiotu z dowolnych stosow (od konca, takze z plecakow w plecaku) albo nic, gdy jest
     * za malo. Najpierw zwykle sztuki, ulepszone dopiero na koncu.
     */
    public InventoryResult remove(int itemId, int count) {
        if (count <= 0) return InventoryResult.INVALID_AMOUNT;
        if (count(itemId) < count) return InventoryResult.NOT_ENOUGH_ITEMS;
        int remaining = count;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) remaining = removeDeep(itemId, remaining, pass);
        return InventoryResult.OK;
    }

    private int removeDeep(int itemId, int remaining, int pass) {
        for (int i = slots.length - 1; i >= 0 && remaining > 0; i--) {
            ItemStack s = slots[i];
            if (s != null && s.itemId() == itemId && !s.isPlain() == (pass == 1)) {
                int take = Math.min(s.count(), remaining);
                slots[i] = take == s.count() ? null : s.withCount(s.count() - take);
                remaining -= take;
            }
        }
        for (int i = slots.length - 1; i >= 0 && remaining > 0; i--) {
            if (slots[i] != null && slots[i].contents() != null) remaining = slots[i].contents().removeDeep(itemId, remaining, pass);
        }
        return remaining;
    }

    /** Jak {@link #remove}, ale tylko z tego pojemnika i bez sprawdzania ilosci (wywolujacy ja zna). */
    private void removeLocal(int itemId, int count) {
        int remaining = count;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int i = slots.length - 1; i >= 0 && remaining > 0; i--) {
                ItemStack s = slots[i];
                if (s != null && s.itemId() == itemId && !s.isPlain() == (pass == 1)) {
                    int take = Math.min(s.count(), remaining);
                    slots[i] = take == s.count() ? null : s.withCount(s.count() - take);
                    remaining -= take;
                }
            }
        }
    }

    /** Ilosc sztuk bez ulepszenia, tieru i nasycen (tylko takie mozna sprzedac, oddac w handlu, wystawic albo wyslac). */
    public int countPlain(int itemId) {
        int n = 0;
        for (ItemStack s : slots) {
            if (s == null) continue;
            if (s.itemId() == itemId && s.isPlain()) n += s.count();
            if (s.contents() != null) n += s.contents().countPlain(itemId);
        }
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
            if (isCoin(src.itemId())) normalizeCoins();
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
                .thenComparing(ItemStack::tier, java.util.Comparator.reverseOrder())
                .thenComparing(ItemStack::upgrade, java.util.Comparator.reverseOrder())
                .thenComparing(ItemStack::count, java.util.Comparator.reverseOrder()));
        Arrays.fill(slots, null);
        for (int i = 0; i < items.size(); i++) slots[i] = items.get(i);
        return !Arrays.equals(before, slots);
    }

    /** Kopia do bezpiecznych operacji transakcyjnych (zmien kopie, potem commitFrom). */
    public Container copy() {
        Container c = new Container(registry, slots.length);
        for (int i = 0; i < slots.length; i++) c.slots[i] = copyStack(slots[i]);
        c.weightLimit = weightLimit;
        return c;
    }

    /** Gleboka kopia stosu: plecak z zawartoscia dostaje wlasna kopie pojemnika, wiec transakcja niczego nie zmieni w oryginale. */
    private static ItemStack copyStack(ItemStack s) {
        return s == null || s.contents() == null ? s : s.withContents(s.contents().copy());
    }

    /** Zatwierdza stan z kopii utworzonej przez {@link #copy()}. */
    public void commitFrom(Container other) {
        if (other.slots.length != slots.length) throw new IllegalArgumentException("size mismatch");
        System.arraycopy(other.slots, 0, slots, 0, slots.length);
    }

    public void clear() { Arrays.fill(slots, null); }

    // ------------------------------------------------------------------ plecaki w plecaku

    /** Rodzaj plecaka dla stosu albo null (to nie jest pojemnik). */
    private ContainerType bagType(ItemStack s) {
        return s == null || !registry.contains(s.itemId()) ? null : ContainerType.ofItem(registry.get(s.itemId()).key());
    }

    /** Czy przedmiot w polu jest plecakiem, ktory mozna otworzyc. */
    public boolean isBag(int slot) { return bagType(get(slot)) != null; }

    private Container child(int slot, boolean create) {
        ItemStack s = slots[slot];
        if (s == null) return null;
        if (s.contents() != null) return s.contents();
        ContainerType t = bagType(s);
        if (t == null || !create) return null;
        Container c = new Container(registry, t.slots());
        slots[slot] = s.withContents(c);
        return c;
    }

    /** Pojemnik plecaka lezacego w polu (powstaje pusty przy pierwszym otwarciu) albo null, gdy to nie plecak. */
    public Container bagAt(int slot) {
        checkSlot(slot);
        return child(slot, true);
    }

    /** Pojemnik wskazany sciezka numerow pol plecakow od tego pojemnika (pusta sciezka = ten pojemnik); null przy blednej sciezce. */
    public Container resolve(int[] path) {
        Container c = this;
        for (int slot : path) {
            if (slot < 0 || slot >= c.slots.length) return null;
            c = c.child(slot, true);
            if (c == null) return null;
        }
        return c;
    }

    /** Sciezka z tekstu "3.0.2" (puste albo "-" = glowny plecak); null, gdy tekst jest niepoprawny. */
    public static int[] parsePath(String text) {
        if (text == null || text.isEmpty() || text.equals("-")) return new int[0];
        String[] f = text.split("\\.");
        int[] p = new int[f.length];
        try {
            for (int i = 0; i < f.length; i++) p[i] = Integer.parseInt(f[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return p;
    }

    public static String pathText(int[] path) {
        if (path.length == 0) return "-";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < path.length; i++) b.append(i > 0 ? "." : "").append(path[i]);
        return b.toString();
    }

    /** Czy pojemnik c jest tym pojemnikiem albo lezy gdziekolwiek w jego plecakach. */
    public boolean containsDeep(Container c) {
        if (c == this) return true;
        for (ItemStack s : slots) if (s != null && s.contents() != null && s.contents().containsDeep(c)) return true;
        return false;
    }

    /** Wszystkie stosy z plecakow w plecaku (glebokosc najpierw), z pominieciem samych plecakow nie - one tez sa na liscie. */
    public List<ItemStack> allContents() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : slots) {
            if (s == null) continue;
            list.add(s);
            if (s.contents() != null) list.addAll(s.contents().allContents());
        }
        return list;
    }

    public boolean isEmptyDeep() {
        for (ItemStack s : slots) if (s != null) return false;
        return true;
    }

    /** Waga zawartosci w setnych uncji ({@link ItemWeights}), razem z zagniezdzonymi plecakami. */
    public long weight() {
        long w = 0;
        for (ItemStack s : slots) if (s != null) w += ItemWeights.of(registry, s);
        return w;
    }

    /** Limit wagi zawartosci w setnych uncji (udzwig postaci); null = bez limitu (depozyt, skrzynie). */
    public void setWeightLimit(java.util.function.LongSupplier limit) { this.weightLimit = limit; }

    public long weightLimit() { return weightLimit == null ? Long.MAX_VALUE : weightLimit.getAsLong(); }

    /** Ile jeszcze mozna dolozyc, nie przekraczajac udzwigu. */
    public long freeWeight() { return weightLimit == null ? Long.MAX_VALUE : Math.max(0, weightLimit.getAsLong() - weight()); }

    private boolean overweight(long extra) {
        return weightLimit != null && weight() + extra > weightLimit.getAsLong();
    }

    /**
     * Przenosi przedmioty miedzy pojemnikami tego samego drzewa (glowny plecak i plecaki w nim): do pustego pola,
     * dolaczajac do takiego samego stosu albo zamieniajac miejscami cale stosy. Plecak nie wejdzie do samego siebie
     * ani do wlasnej zawartosci ({@link InventoryResult#CYCLE}). Waga drzewa sie nie zmienia, wiec udzwig nie blokuje.
     */
    public static InventoryResult moveBetween(Container from, int fromSlot, Container to, int toSlot, int count) {
        if (from == to) return from.move(fromSlot, toSlot, count);
        if (fromSlot < 0 || fromSlot >= from.slots.length || toSlot < 0 || toSlot >= to.slots.length) return InventoryResult.INVALID_SLOT;
        ItemStack src = from.slots[fromSlot];
        if (src == null) return InventoryResult.EMPTY_SLOT;
        if (count <= 0 || count > src.count()) return InventoryResult.INVALID_AMOUNT;
        ItemStack dst = to.slots[toSlot];
        // plecak nie moze trafic do siebie ani do wlasnego potomka
        if (src.contents() != null && src.contents().containsDeep(to)) return InventoryResult.CYCLE;
        if (dst == null) {
            to.slots[toSlot] = src.withCount(count);
            from.slots[fromSlot] = count == src.count() ? null : src.withCount(src.count() - count);
            return InventoryResult.OK;
        }
        int max = from.registry.maxStack(src.itemId());
        if (dst.itemId() == src.itemId() && max > 1 && dst.isPlain() && src.isPlain()) {
            int put = Math.min(count, max - dst.count());
            if (put <= 0) return InventoryResult.NO_SPACE;
            to.slots[toSlot] = dst.withCount(dst.count() + put);
            from.slots[fromSlot] = put == src.count() ? null : src.withCount(src.count() - put);
            return InventoryResult.OK;
        }
        if (count != src.count()) return InventoryResult.NO_SPACE;
        if (dst.contents() != null && dst.contents().containsDeep(from)) return InventoryResult.CYCLE;
        to.slots[toSlot] = src;
        from.slots[fromSlot] = dst;
        return InventoryResult.OK;
    }

    /**
     * Wklada stos z pola do plecaka lezacego w innym polu tego samego pojemnika (przeciagniecie przedmiotu na
     * plecak): pierwszy plecak z miejscem w glab. Plecaka nie wlozy do niego samego.
     */
    /** Wstawia stos do tego pojemnika albo jego plecakow bez sprawdzania udzwigu (przeniesienie w obrebie drzewa). */
    public InventoryResult insert(ItemStack stack) {
        if (spaceFor(stack.itemId()) < stack.count()) return InventoryResult.NO_SPACE;
        addDeep(stack);
        return InventoryResult.OK;
    }

    public InventoryResult putIntoBag(int fromSlot, int bagSlot) {
        if (fromSlot < 0 || fromSlot >= slots.length || bagSlot < 0 || bagSlot >= slots.length) return InventoryResult.INVALID_SLOT;
        if (fromSlot == bagSlot) return InventoryResult.CYCLE;
        ItemStack src = slots[fromSlot];
        if (src == null) return InventoryResult.EMPTY_SLOT;
        Container bag = child(bagSlot, true);
        if (bag == null) return InventoryResult.WRONG_SLOT_TYPE;
        if (src.contents() != null && src.contents().containsDeep(bag)) return InventoryResult.CYCLE;
        if (bag.spaceFor(src.itemId()) < src.count()) return InventoryResult.NO_SPACE;
        slots[fromSlot] = null;
        bag.addDeep(src);
        return InventoryResult.OK;
    }

    // ------------------------------------------------------------------ zapis zawartosci

    /**
     * Zawartosc plecaka jako "{rozmiar|pole=stos|pole=stos}" (stos jak {@link ItemStack#encode()}, zagniezdzone plecaki
     * w tych samych nawiasach). Bez znakow '/', ':', ';', ',' - mieści się w zapisie postaci i skrzyn.
     */
    public String encodeContents() {
        StringBuilder b = new StringBuilder("{").append(slots.length);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) b.append('|').append(i).append('=').append(slots[i].encode());
        }
        return b.append('}').toString();
    }

    /** Odczyt {@link #encodeContents()} bez zewnetrznych nawiasow. */
    public static Container decodeContents(ItemRegistry registry, String inner) {
        java.util.List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '|' && depth == 0) {
                parts.add(inner.substring(start, i));
                start = i + 1;
            }
            if (depth < 0) throw new IllegalArgumentException("unbalanced container contents");
        }
        if (depth != 0) throw new IllegalArgumentException("unbalanced container contents");
        parts.add(inner.substring(start));
        Container c = new Container(registry, Integer.parseInt(parts.get(0).trim()));
        for (int i = 1; i < parts.size(); i++) {
            int eq = parts.get(i).indexOf('=');
            int slot = Integer.parseInt(parts.get(i).substring(0, eq));
            ItemStack st = ItemStack.parse(parts.get(i).substring(eq + 1), registry);
            if (!c.putInSlot(slot, st).ok()) throw new IllegalArgumentException("bad bag slot " + slot);
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Container c && Arrays.equals(slots, c.slots);
    }

    @Override
    public int hashCode() { return slots.length; }

    public ItemRegistry registry() { return registry; }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException("slot " + slot);
    }
}
