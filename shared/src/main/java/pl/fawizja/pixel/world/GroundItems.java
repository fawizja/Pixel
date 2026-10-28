package pl.fawizja.pixel.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemStack;

/**
 * Przedmioty lezace na ziemi i ciala potworow. Podnoszenie i wyrzucanie przenosza dokladnie te same stosy.
 * Cialo trzyma lup, ktorego nie zabral autoloot; po {@link #CORPSE_MS} znika razem z tym, co w nim zostalo.
 * Przedmioty na ziemi znikaja po {@link #GROUND_MS} od ostatniego polozenia na kafelku (serwer nie puchnie).
 */
public final class GroundItems {

    public static final long CORPSE_MS = 180_000L;
    public static final long GROUND_MS = 30 * 60_000L;
    public static final int MAX_CORPSE_STACKS = 20;

    /** Cialo potwora: klucz potwora (wyglad), czas znikniecia i lup do przeszukania. */
    public static final class Corpse {
        private final String monsterKey;
        private long untilMs;
        private final List<ItemStack> loot = new ArrayList<>();
        /** Klient online: serwer podaje tylko, czy cos w nim jest. */
        private final Boolean remoteHasLoot;

        Corpse(String monsterKey, long untilMs, Boolean remoteHasLoot) {
            this.monsterKey = monsterKey;
            this.untilMs = untilMs;
            this.remoteHasLoot = remoteHasLoot;
        }

        public String monsterKey() { return monsterKey; }
        public long untilMs() { return untilMs; }
        public List<ItemStack> loot() { return Collections.unmodifiableList(loot); }
        public boolean hasLoot() { return remoteHasLoot != null ? remoteHasLoot : !loot.isEmpty(); }
    }

    private final Map<Position, List<ItemStack>> items = new HashMap<>();
    private final Map<Position, Long> placedAt = new HashMap<>();
    private final Map<Position, Corpse> corpses = new HashMap<>();
    private long nextDecayMs;
    /** Czas ostatniego decay(); -1 = jeszcze nieznany (polozone wtedy przedmioty licza czas od pierwszego decay). */
    private long lastNow = -1;

    public List<ItemStack> at(Position p) {
        List<ItemStack> l = items.get(p);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    /** Zdejmuje stos z kafelka (np. monety z opuszczanego domu ida na konto); null, gdy nie ma takiego. */
    public ItemStack take(Position p, int index) {
        List<ItemStack> l = items.get(p);
        if (l == null || index < 0 || index >= l.size()) return null;
        ItemStack s = l.remove(index);
        if (l.isEmpty()) {
            items.remove(p);
            placedAt.remove(p);
        }
        return s;
    }

    public void place(Position p, ItemStack stack) {
        items.computeIfAbsent(p, k -> new ArrayList<>()).add(stack);
        placedAt.put(p, lastNow);
    }

    /** Nowe cialo (albo kolejny potwor na tym samym kafelku: lup sie sumuje, ostatni wyglad wygrywa). */
    public Corpse addCorpse(Position p, String monsterKey, List<ItemStack> loot, long now) {
        Corpse old = corpses.get(p);
        Corpse c = new Corpse(monsterKey, now + CORPSE_MS, null);
        if (old != null) c.loot.addAll(old.loot);
        for (ItemStack s : loot) if (c.loot.size() < MAX_CORPSE_STACKS) c.loot.add(s);
        corpses.put(p, c);
        return c;
    }

    /** Klient online: znacznik ciala z klatki serwera. */
    public void placeRemoteCorpse(Position p, String monsterKey, boolean hasLoot) {
        corpses.put(p, new Corpse(monsterKey, Long.MAX_VALUE, hasLoot));
    }

    public Corpse corpseAt(Position p) { return corpses.get(p); }

    public void forEachCorpseNear(Position c, int radius, java.util.function.BiConsumer<Position, Corpse> out) {
        for (Map.Entry<Position, Corpse> e : corpses.entrySet()) {
            Position p = e.getKey();
            if (p.z() == c.z() && p.distanceTo(c) <= radius) out.accept(p, e.getValue());
        }
    }

    /**
     * Przeszukanie ciala: wszystko, co sie miesci, trafia do plecaka (atomowo dla kazdego stosu),
     * reszta zostaje w ciele. Zwraca zabrane stosy.
     */
    public List<ItemStack> lootCorpse(Position p, Container backpack) {
        Corpse c = corpses.get(p);
        List<ItemStack> taken = new ArrayList<>();
        if (c == null) return taken;
        for (java.util.Iterator<ItemStack> it = c.loot.iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (backpack.add(s).ok()) {
                taken.add(s);
                it.remove();
            }
        }
        return taken;
    }

    /** Usuwa przeterminowane ciala i stare przedmioty (najwyzej raz na sekunde). */
    public void decay(long now) {
        lastNow = now;
        if (now < nextDecayMs) return;
        nextDecayMs = now + 1000;
        corpses.values().removeIf(c -> c.untilMs <= now);
        for (java.util.Iterator<Map.Entry<Position, Long>> it = placedAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Position, Long> e = it.next();
            if (e.getValue() < 0) {
                e.setValue(now);
            } else if (now - e.getValue() >= GROUND_MS) {
                items.remove(e.getKey());
                it.remove();
            }
        }
    }

    public int corpseCount() { return corpses.size(); }

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
        if (l.isEmpty()) {
            items.remove(where);
            placedAt.remove(where);
        }
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

    public void clear() {
        items.clear();
        placedAt.clear();
        corpses.clear();
    }

    public int totalStacks() {
        int n = 0;
        for (List<ItemStack> l : items.values()) n += l.size();
        return n;
    }
}
