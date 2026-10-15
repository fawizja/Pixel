package pl.fawizja.pixel.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.world.Position;

/**
 * Bezpieczna wymiana miedzy dwoma graczami: kazdy wystawia przedmioty z plecaka i zloto, kazda zmiana
 * oferty kasuje akceptacje obu stron, a wymiana nastepuje dopiero po akceptacji obu - atomowo na kopiach
 * plecakow (albo cala, albo wcale). Gracze musza stac obok siebie ({@link #MAX_DISTANCE}).
 */
public final class PlayerTrade {

    public static final int MAX_DISTANCE = 2;
    public static final int MAX_STACKS = 8;
    public static final long TIMEOUT_MS = 180_000L;

    public enum Result {
        OK("OK"),
        DONE("Wymiana zakończona."),
        WAITING("Zaakceptowano - czekam na drugą stronę."),
        NOT_PARTICIPANT("Nie bierzesz udziału w tej wymianie."),
        UNKNOWN_ITEM("Nie ma takiego przedmiotu. Przykład: /oferuj 5 mikstura zdrowia albo /oferuj 1000 gp"),
        NOT_ENOUGH("Nie masz tylu w plecaku."),
        TOO_MANY_STACKS("Za dużo pozycji w ofercie (maks. " + MAX_STACKS + ")."),
        TOO_FAR("Stój obok drugiego gracza (maks. " + MAX_DISTANCE + " pola, to samo piętro)."),
        NO_SPACE("Wymiana anulowana: brak miejsca w plecaku."),
        CHANGED("Wymiana anulowana: przedmiotów z oferty już nie ma w plecaku."),
        EMPTY("Obie oferty są puste - nie ma czego wymieniać.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    /** Oferta jednej strony: stosy przedmiotow i zloto (w gp). */
    public static final class Offer {
        private final List<ItemStack> items = new ArrayList<>();
        private long gold;

        public List<ItemStack> items() { return Collections.unmodifiableList(items); }
        public long gold() { return gold; }
        public boolean isEmpty() { return items.isEmpty() && gold == 0; }

        int countOf(int itemId) {
            int n = 0;
            for (ItemStack s : items) if (s.itemId() == itemId) n += s.count();
            return n;
        }

        public String describe(ItemRegistry reg) {
            List<String> parts = new ArrayList<>();
            for (ItemStack s : items) parts.add(s.count() + "x " + reg.get(s.itemId()).name());
            if (gold > 0) parts.add(gold + " gp");
            return parts.isEmpty() ? "(nic)" : String.join(", ", parts);
        }
    }

    private final String a;
    private final String b;
    private final Offer offerA = new Offer();
    private final Offer offerB = new Offer();
    private boolean acceptA;
    private boolean acceptB;
    private long lastChangeMs;

    public PlayerTrade(String a, String b, long now) {
        this.a = a;
        this.b = b;
        this.lastChangeMs = now;
    }

    public String first() { return a; }
    public String second() { return b; }

    public boolean involves(String name) { return is(a, name) || is(b, name); }

    public String partnerOf(String name) { return is(a, name) ? b : a; }

    public Offer offerOf(String name) { return is(a, name) ? offerA : offerB; }

    public boolean expired(long now) { return now - lastChangeMs > TIMEOUT_MS; }

    public static boolean closeEnough(Position p, Position q) {
        return p.z() == q.z() && p.distanceTo(q) <= MAX_DISTANCE;
    }

    /** Dodaje przedmioty albo zloto do oferty gracza; sprawdza, czy ma je w plecaku. */
    public Result offer(String who, Container backpack, ItemRegistry reg, String text, long now) {
        if (!involves(who)) return Result.NOT_PARTICIPANT;
        Parsed p = parse(text, reg);
        if (p == null) return Result.UNKNOWN_ITEM;
        Offer o = offerOf(who);
        if (p.itemId < 0) {
            if (Money.totalValue(backpack) < o.gold + p.count) return Result.NOT_ENOUGH;
            o.gold += p.count;
        } else {
            if (backpack.count(p.itemId) < o.countOf(p.itemId) + p.count) return Result.NOT_ENOUGH;
            if (o.items.size() >= MAX_STACKS) return Result.TOO_MANY_STACKS;
            o.items.add(new ItemStack(p.itemId, (int) p.count));
        }
        changed(now);
        return Result.OK;
    }

    /** Akceptacja; DONE oznacza, ze obie strony zaakceptowaly i trzeba wywolac {@link #execute}. */
    public Result accept(String who) {
        if (!involves(who)) return Result.NOT_PARTICIPANT;
        if (offerA.isEmpty() && offerB.isEmpty()) return Result.EMPTY;
        if (is(a, who)) acceptA = true;
        else acceptB = true;
        return acceptA && acceptB ? Result.DONE : Result.WAITING;
    }

    public boolean accepted(String who) { return is(a, who) ? acceptA : acceptB; }

    private void changed(long now) {
        acceptA = false;
        acceptB = false;
        lastChangeMs = now;
    }

    /**
     * Wymiana na kopiach plecakow; plecaki zmieniaja sie tylko przy powodzeniu.
     * {@code backpackA} nalezy do {@link #first()}, {@code backpackB} do {@link #second()}.
     */
    public Result execute(Container backpackA, Container backpackB) {
        Container ta = backpackA.copy();
        Container tb = backpackB.copy();
        Result r = take(ta, offerA);
        if (r == Result.OK) r = take(tb, offerB);
        if (r != Result.OK) return r;
        if (!give(tb, offerA) || !give(ta, offerB)) return Result.NO_SPACE;
        backpackA.commitFrom(ta);
        backpackB.commitFrom(tb);
        return Result.DONE;
    }

    private static Result take(Container c, Offer o) {
        for (ItemStack s : o.items) if (!c.remove(s.itemId(), s.count()).ok()) return Result.CHANGED;
        InventoryResult paid = Money.pay(c, o.gold);
        return paid.ok() ? Result.OK : Result.CHANGED;
    }

    private static boolean give(Container c, Offer o) {
        for (ItemStack s : o.items) {
            int left = s.count();
            int max = c.registry().maxStack(s.itemId());
            while (left > 0) {
                int n = Math.min(left, max);
                if (!c.add(new ItemStack(s.itemId(), n)).ok()) return false;
                left -= n;
            }
        }
        return Money.add(c, o.gold).ok();
    }

    /** Ilosc i przedmiot (itemId) albo zloto (itemId = -1). */
    public record Parsed(int itemId, long count) {}

    /** "5 health potion", "health_potion", "1000 gp" (takze "zloto"). Ilosc domyslnie 1. */
    /** Bez polskich znakow: "złota moneta" = "zlota moneta". */
    private static String plain(String s) {
        String t = s.replace('ł', 'l').replace('Ł', 'L');
        return java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public static Parsed parse(String text, ItemRegistry reg) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        long count = 1;
        int sp = t.indexOf(' ');
        if (sp > 0 && t.substring(0, sp).chars().allMatch(Character::isDigit)) {
            try {
                count = Long.parseLong(t.substring(0, sp));
            } catch (NumberFormatException e) {
                return null;
            }
            t = t.substring(sp + 1).trim();
        }
        if (count <= 0 || count > 1_000_000_000L || t.isEmpty()) return null;
        if (t.equals("gp") || t.equals("zloto") || t.equals("złoto") || t.equals("gold")) return new Parsed(-1, count);
        if (count > 10_000) return null;
        String wanted = plain(t).replace(' ', '_');
        ItemType prefix = null;
        int prefixHits = 0;
        for (ItemType it : reg.all()) {
            String key = it.key();
            String name = plain(it.name().toLowerCase(Locale.ROOT)).replace(' ', '_');
            if (key.equals(wanted) || name.equals(wanted)) return new Parsed(it.id(), count);
            if (key.startsWith(wanted) || name.startsWith(wanted)) {
                prefix = it;
                prefixHits++;
            }
        }
        return prefixHits == 1 ? new Parsed(prefix.id(), count) : null;
    }

    private static boolean is(String x, String y) { return x.equalsIgnoreCase(y); }
}
