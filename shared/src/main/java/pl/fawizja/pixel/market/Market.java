package pl.fawizja.pixel.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rynek graczy (serwer): oferty wystawione u bankiera, kupowane takze wtedy, gdy sprzedawca jest offline.
 * Zaplata (cena minus prowizja) czeka w {@link #takeEarnings} do nastepnego wejscia sprzedawcy, jesli nie ma go w grze.
 * Zapis tekstowy: "N nastepneId", "L id;itemId;ilosc;cena;sprzedawca", "E kwota;sprzedawca".
 */
public final class Market {

    public static final int MAX_PER_SELLER = 10;
    public static final int MAX_TOTAL = 2000;
    public static final long MAX_PRICE = 1_000_000_000L;
    /** Prowizja rynku w procentach (znika z gry - hamuje inflacje). */
    public static final int FEE_PERCENT = 2;

    public record Listing(int id, String seller, int itemId, int count, long price) {}

    public enum Result {
        OK(null),
        TOO_MANY("Masz już " + MAX_PER_SELLER + " ofert na rynku. Wycofaj którąś albo poczekaj na sprzedaż."),
        FULL("Rynek jest pełny, spróbuj później."),
        BAD_PRICE("Cena musi wynosić od 1 do " + String.format("%,d", MAX_PRICE) + " gp."),
        NOT_FOUND("Tej oferty już nie ma."),
        OWN("To Twoja oferta - możesz ją wycofać."),
        NOT_OWNER("To nie jest Twoja oferta.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final Map<String, Long> earnings = new HashMap<>();
    private final Map<String, String> earningNames = new HashMap<>();
    private int nextId = 1;

    public static long fee(long price) { return price * FEE_PERCENT / 100; }

    public synchronized Result canList(String seller, long price) {
        if (price < 1 || price > MAX_PRICE) return Result.BAD_PRICE;
        if (listings.size() >= MAX_TOTAL) return Result.FULL;
        if (bySeller(seller).size() >= MAX_PER_SELLER) return Result.TOO_MANY;
        return Result.OK;
    }

    /** Nowa oferta; wywolujacy sprawdza najpierw {@link #canList} i zabiera przedmioty z plecaka. */
    public synchronized Listing add(String seller, int itemId, int count, long price) {
        Listing l = new Listing(nextId++, seller, itemId, count, price);
        listings.put(l.id(), l);
        return l;
    }

    public synchronized Listing get(int id) { return listings.get(id); }

    public synchronized Result checkBuy(int id, String buyer) {
        Listing l = listings.get(id);
        if (l == null) return Result.NOT_FOUND;
        if (l.seller().equalsIgnoreCase(buyer)) return Result.OWN;
        return Result.OK;
    }

    /** Sprzedaz: zdejmuje oferte; zwraca ja albo null. Zaplate dopisuje {@link #addEarnings}. */
    public synchronized Listing sold(int id) { return listings.remove(id); }

    public synchronized Result checkCancel(int id, String seller) {
        Listing l = listings.get(id);
        if (l == null) return Result.NOT_FOUND;
        if (!l.seller().equalsIgnoreCase(seller)) return Result.NOT_OWNER;
        return Result.OK;
    }

    public synchronized Listing cancel(int id) { return listings.remove(id); }

    public synchronized void addEarnings(String seller, long amount) {
        if (amount <= 0) return;
        earnings.merge(key(seller), amount, Long::sum);
        earningNames.put(key(seller), seller);
    }

    /** Zaplata czekajaca na sprzedawce (zerowana przy odbiorze). */
    public synchronized long takeEarnings(String seller) {
        earningNames.remove(key(seller));
        Long v = earnings.remove(key(seller));
        return v == null ? 0 : v;
    }

    public synchronized List<Listing> bySeller(String seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller().equalsIgnoreCase(seller)) out.add(l);
        return out;
    }

    /** Wszystkie oferty, najnowsze najpierw. */
    public synchronized List<Listing> newestFirst() {
        List<Listing> out = new ArrayList<>(listings.values());
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized int size() { return listings.size(); }

    /** Usuniecie postaci: jej oferty i zarobki przepadaja. */
    public synchronized void forget(String seller) {
        listings.values().removeIf(l -> l.seller().equalsIgnoreCase(seller));
        takeEarnings(seller);
    }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder("N ").append(nextId).append('\n');
        for (Listing l : listings.values()) {
            sb.append("L ").append(l.id()).append(';').append(l.itemId()).append(';').append(l.count()).append(';')
                    .append(l.price()).append(';').append(l.seller()).append('\n');
        }
        for (Map.Entry<String, Long> e : earnings.entrySet()) {
            sb.append("E ").append(e.getValue()).append(';').append(earningNames.getOrDefault(e.getKey(), e.getKey())).append('\n');
        }
        return sb.toString();
    }

    /** Odczyt zapisu (null albo pusty tekst = pusty rynek); zle linie sa pomijane. */
    public static Market decode(String text) {
        Market m = new Market();
        if (text == null) return m;
        for (String line : text.split("\n")) {
            try {
                if (line.startsWith("N ")) {
                    m.nextId = Math.max(m.nextId, Integer.parseInt(line.substring(2).trim()));
                } else if (line.startsWith("L ")) {
                    String[] f = line.substring(2).split(";", 5);
                    Listing l = new Listing(Integer.parseInt(f[0]), f[4], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Long.parseLong(f[3]));
                    m.listings.put(l.id(), l);
                    m.nextId = Math.max(m.nextId, l.id() + 1);
                } else if (line.startsWith("E ")) {
                    String[] f = line.substring(2).split(";", 2);
                    m.addEarnings(f[1], Long.parseLong(f[0]));
                }
            } catch (RuntimeException e) {
                // pomijamy uszkodzona linie
            }
        }
        return m;
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
