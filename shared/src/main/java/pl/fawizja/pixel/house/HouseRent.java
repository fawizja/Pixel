package pl.fawizja.pixel.house;

import java.util.List;
import pl.fawizja.pixel.economy.BankAccount;
import pl.fawizja.pixel.economy.Currency;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.world.Crates;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;

/**
 * Czynsz za dom jak w Tibii (Darek 2026-10-28: "jaki jest czynsz tygodniowy, czynsz ma byc automatycznie pobierany
 * z konta gracza"): co tydzien czasu rzeczywistego z konta w banku. Kupno oplaca pierwszy tydzien. Gdy na koncie
 * brakuje, platnosc jest zalegla; zaleglosc splaca sie sama, gdy pieniadze wroca na konto. Po {@link #MAX_MISSED}
 * zaleglych platnosciach dom wraca do miasta, a rzeczy ze skrzyn i z podlogi trafiaja do depozytu wlasciciela.
 * Okres mozna zmienic wlasciwoscia -Dpixel.rentPeriodMs (testy, serwer testowy).
 */
public final class HouseRent {

    public static final long WEEK_MS = 7L * 24 * 3_600_000L;
    /** Tyle zaleglych platnosci i dom wraca do miasta. */
    public static final int MAX_MISSED = 2;
    /** Czynsz tygodniowy: tyle procent ceny domu. */
    public static final int PERCENT_PER_WEEK = 2;

    private HouseRent() {}

    /** Czynsz tygodniowy domu (2% ceny, zaokraglony do 10 gp). */
    public static long rent(House h) {
        long r = h.price() * PERCENT_PER_WEEK / 100;
        return Math.max(100, (r + 5) / 10 * 10);
    }

    /** Okres czynszu w ms (tydzien, chyba ze -Dpixel.rentPeriodMs mowi inaczej). */
    public static long period() {
        String v = System.getProperty("pixel.rentPeriodMs");
        if (v != null) {
            try {
                long p = Long.parseLong(v.trim());
                if (p > 0) return p;
            } catch (NumberFormatException e) {
                // zla wartosc: tydzien
            }
        }
        return WEEK_MS;
    }

    /**
     * Wynik rozliczenia: nowy stan (oplacony do, zalegle), ile pobrano, ile platnosci przybylo zaleglych w tym
     * rozliczeniu i czy dom wraca do miasta.
     */
    public record Result(long paidUntil, int missed, long charged, int newlyMissed, boolean evicted) {
        public boolean changed(long oldPaidUntil, int oldMissed) { return paidUntil != oldPaidUntil || missed != oldMissed; }
    }

    /**
     * Rozlicza czynsz do chwili now. paidUntil = 0 (dom kupiony przed wprowadzeniem czynszu) zaczyna okres od teraz,
     * bez oplaty. Najpierw splata zaleglosci, gdy na koncie juz jest na nie zloto, potem kazdy minienty termin:
     * czynsz plus zaleglosci albo kolejna zaleglosc.
     */
    public static Result settle(long paidUntil, int missed, long now, long period, long rent, BankAccount bank) {
        if (paidUntil <= 0) return new Result(now + period, 0, 0, 0, false);
        long charged = 0;
        int newly = 0;
        if (missed > 0 && bank.balance() >= missed * rent && bank.debit(missed * rent).ok()) {
            charged += missed * rent;
            missed = 0;
        }
        while (now >= paidUntil) {
            paidUntil += period;
            long due = rent * (1 + missed);
            if (bank.balance() >= due && bank.debit(due).ok()) {
                charged += due;
                missed = 0;
            } else {
                missed++;
                newly++;
                if (missed >= MAX_MISSED) return new Result(paidUntil, missed, charged, newly, true);
            }
        }
        return new Result(paidUntil, missed, charged, newly, false);
    }

    /** "6 dni 23 godz.", "5 godz. 10 min", "3 min" - czas do terminu. */
    public static String describeIn(long ms) {
        long m = Math.max(0, ms) / 60_000L;
        long d = m / (24 * 60), h = m / 60 % 24, min = m % 60;
        if (d > 0) return d + (d == 1 ? " dzień " : " dni ") + h + " godz.";
        if (h > 0) return h + " godz. " + min + " min";
        return Math.max(1, min) + " min";
    }

    /**
     * Opuszczenie domu: rzeczy ze skrzyn we wnetrzu i z podlogi do depozytu (monety na konto). Co sie nie zmiesci,
     * zostaje na miejscu. Zwraca liczbe przeniesionych stosow.
     */
    public static int moveBelongings(House h, Crates crates, GroundItems ground, Container depot, BankAccount bank,
                                     ItemRegistry items) {
        int moved = 0;
        for (Position p : crates.allContents().keySet()) {
            if (!h.contains(p)) continue;
            Container c = crates.open(p, items);
            if (c == null) continue;
            boolean changed = false;
            for (int i = 0; i < c.size(); i++) {
                ItemStack st = c.get(i);
                if (st == null || !store(st, depot, bank)) continue;
                c.takeFromSlot(i, st.count());
                changed = true;
                moved++;
            }
            if (changed) crates.changed(p);
        }
        if (ground != null) {
            int[] box = bounds(h);
            for (int y = box[1]; y <= box[3]; y++) {
                for (int x = box[0]; x <= box[2]; x++) {
                    Position p = new Position(x, y, h.z());
                    if (!h.contains(p)) continue;
                    List<ItemStack> lying = ground.at(p);
                    for (int i = lying.size() - 1; i >= 0; i--) {
                        ItemStack st = lying.get(i);
                        Currency coin = Currency.byItemId(st.itemId());
                        if (coin != null) {
                            if (!bank.credit(coin.value() * st.count()).ok()) continue;
                            ground.take(p, i);
                            moved++;
                        } else if (ground.pickUp(p, i, depot).ok()) {
                            moved++;
                        }
                    }
                }
            }
        }
        return moved;
    }

    private static boolean store(ItemStack st, Container depot, BankAccount bank) {
        Currency coin = Currency.byItemId(st.itemId());
        if (coin != null) return bank.credit(coin.value() * st.count()).ok();
        return depot.add(st).ok();
    }

    /** Prostokat obejmujacy wnetrze ze skrzydlami: x0, y0, x1, y1. */
    static int[] bounds(House h) {
        int[] b = {h.x0(), h.y0(), h.x1(), h.y1()};
        int[] w = h.wings();
        for (int i = 0; i < w.length; i += 4) {
            b[0] = Math.min(b[0], w[i]);
            b[1] = Math.min(b[1], w[i + 1]);
            b[2] = Math.max(b[2], w[i + 2]);
            b[3] = Math.max(b[3], w[i + 3]);
        }
        return b;
    }
}
