package pl.fawizja.pixel.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Stany z obrazeniami co tik jednej postaci albo potwora (Canary ConditionDamage): lista obrazen kolejnych tikow,
 * odstep miedzy tikami i zrodlo (do podsumowania smierci). Nowy stan tego samego rodzaju zastepuje stary, gdy zada
 * wiecej obrazen niz to, co zostalo (jak w Tibii: slabsze zatrucie nie skraca silniejszego). Stan ulotny.
 */
public final class Conditions {

    /** Jeden tik obrazen do zadania teraz. */
    public record Tick(ConditionType type, int amount, String source) {}

    private static final class Active {
        final ArrayDeque<Integer> ticks;
        final long intervalMs;
        long nextMs;
        final String source;

        Active(List<Integer> ticks, long intervalMs, long nextMs, String source) {
            this.ticks = new ArrayDeque<>(ticks);
            this.intervalMs = intervalMs;
            this.nextMs = nextMs;
            this.source = source;
        }

        int remaining() {
            int s = 0;
            for (int t : ticks) s += t;
            return s;
        }
    }

    private final Map<ConditionType, Active> active = new EnumMap<>(ConditionType.class);

    /**
     * Dodaje stan. delayed = pierwszy tik po odstepie (ataki potworow i czary utori), inaczej od razu (pola).
     * Zwraca false, gdy obecny stan jest silniejszy i zostal.
     */
    public boolean add(ConditionType type, List<Integer> ticks, long intervalMs, boolean delayed, long now, String source) {
        if (type == null || ticks == null || ticks.isEmpty() || intervalMs <= 0) return false;
        int total = 0;
        for (int t : ticks) total += Math.max(0, t);
        if (total <= 0) return false;
        Active old = active.get(type);
        if (old != null && old.remaining() > total) return false;
        active.put(type, new Active(ticks, intervalMs, delayed ? now + intervalMs : now, source));
        return true;
    }

    /** Stan z laczna suma obrazen rozlozona jak w Tibii (pierwszy tik = ceil(suma/20), potem malejaco). */
    public boolean addTotal(ConditionType type, int total, long intervalMs, long now, String source) {
        return add(type, damageList(total, Math.max(1, (int) Math.ceil(total / 20.0))), intervalMs, true, now, source);
    }

    /** Tiki, ktore wypadly do chwili now (najstarsze pierwsze); wygasle stany znikaja. */
    public List<Tick> tick(long now) {
        if (active.isEmpty()) return Collections.emptyList();
        List<Tick> out = new ArrayList<>();
        java.util.Iterator<Map.Entry<ConditionType, Active>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ConditionType, Active> e = it.next();
            Active a = e.getValue();
            int guard = 0;
            while (!a.ticks.isEmpty() && now >= a.nextMs && guard++ < 50) {
                out.add(new Tick(e.getKey(), a.ticks.pollFirst(), a.source));
                a.nextMs += a.intervalMs;
            }
            if (a.ticks.isEmpty()) it.remove();
        }
        return out;
    }

    public boolean has(ConditionType t) { return active.containsKey(t); }

    public boolean any() { return !active.isEmpty(); }

    /** Leczenie (exana pox...): true, gdy stan byl. */
    public boolean remove(ConditionType t) { return active.remove(t) != null; }

    public void clear() { active.clear(); }

    /** Aktywne stany (do ikon HUD). */
    public List<ConditionType> types() { return new ArrayList<>(active.keySet()); }

    /** Ile obrazen jeszcze zada stan (0 = brak). */
    public int remainingDamage(ConditionType t) {
        Active a = active.get(t);
        return a == null ? 0 : a.remaining();
    }

    /** Ile tikow zostalo. */
    public int remainingTicks(ConditionType t) {
        Active a = active.get(t);
        return a == null ? 0 : a.ticks.size();
    }

    /** Czas do konca stanu (ms). */
    public long remainingMs(ConditionType t, long now) {
        Active a = active.get(t);
        if (a == null) return 0;
        return Math.max(0, a.nextMs - now) + (long) Math.max(0, a.ticks.size() - 1) * a.intervalMs;
    }

    /** Stale tiki: count razy po amount. */
    public static List<Integer> constant(int count, int amount) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(amount);
        return out;
    }

    /**
     * Lista obrazen tikow jak Canary ConditionDamage::generateDamageList: od start w dol do 1, kazda wartosc
     * powtarzana tak, by suma zblizala sie do amount.
     */
    public static List<Integer> damageList(int amount, int start) {
        amount = Math.abs(amount);
        List<Integer> list = new ArrayList<>();
        if (amount == 0 || start <= 0) return list;
        int sum = 0;
        for (int i = start; i > 0; --i) {
            int n = start + 1 - i;
            int med = (n * amount) / start;
            double x1, x2;
            do {
                sum += i;
                list.add(i);
                x1 = Math.abs(1.0 - ((float) sum + i) / med);
                x2 = Math.abs(1.0 - ((float) sum / med));
            } while (x1 < x2);
        }
        return list;
    }
}
