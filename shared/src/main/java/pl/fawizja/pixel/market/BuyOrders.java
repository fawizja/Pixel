package pl.fawizja.pixel.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zamowienia kupna na rynku: gracz wplaca zloto z konta i oglasza "kupie 20x wilcze futro za 2 000 gp".
 * Inny gracz u bankiera realizuje zamowienie - oddaje przedmioty, dostaje zloto (bez prowizji rynku),
 * a przedmioty ida poczta do depozytu zamawiajacego. Zapis w buyorders.txt: "N nastepneId" i "O id;przedmiot;ilosc;cena;kupujacy".
 */
public final class BuyOrders {

    public static final int MAX_PER_BUYER = 5;
    public static final int MAX_TOTAL = 1000;
    /** Tyle zamowien pokazuje lista. */
    public static final int SHOWN = 15;

    public record Order(int id, String buyer, int itemId, int count, long price) {}

    public enum Result {
        OK(""),
        TOO_MANY("Masz już " + MAX_PER_BUYER + " zamówień. Wycofaj któreś: /kupię anuluj numer."),
        FULL("Tablica zamówień jest pełna. Spróbuj później."),
        BAD_PRICE("Cena musi być większa od zera, a ilość od 1 do 100."),
        NO_ORDER("Nie ma takiego zamówienia."),
        OWN_ORDER("Nie zrealizujesz własnego zamówienia."),
        NOT_YOURS("To nie Twoje zamówienie.");

        public final String message;

        Result(String message) { this.message = message; }
    }

    private final Map<Integer, Order> orders = new LinkedHashMap<>();
    private int nextId = 1;

    public synchronized Result canPlace(String buyer, int count, long price) {
        if (price <= 0 || price > Market.MAX_PRICE || count < 1 || count > 100) return Result.BAD_PRICE;
        if (orders.size() >= MAX_TOTAL) return Result.FULL;
        if (byBuyer(buyer).size() >= MAX_PER_BUYER) return Result.TOO_MANY;
        return Result.OK;
    }

    public synchronized Order place(String buyer, int itemId, int count, long price) {
        Order o = new Order(nextId++, buyer, itemId, count, price);
        orders.put(o.id(), o);
        return o;
    }

    public synchronized Order get(int id) { return orders.get(id); }

    public synchronized Result checkFill(int id, String seller) {
        Order o = orders.get(id);
        if (o == null) return Result.NO_ORDER;
        if (key(o.buyer()).equals(key(seller))) return Result.OWN_ORDER;
        return Result.OK;
    }

    public synchronized Result checkCancel(int id, String buyer) {
        Order o = orders.get(id);
        if (o == null) return Result.NO_ORDER;
        if (!key(o.buyer()).equals(key(buyer))) return Result.NOT_YOURS;
        return Result.OK;
    }

    /** Usuwa zamowienie (zrealizowane albo wycofane). */
    public synchronized Order remove(int id) { return orders.remove(id); }

    public synchronized List<Order> byBuyer(String buyer) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders.values()) if (key(o.buyer()).equals(key(buyer))) out.add(o);
        return out;
    }

    public synchronized List<Order> newestFirst() {
        List<Order> out = new ArrayList<>(orders.values());
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized int size() { return orders.size(); }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder("N ").append(nextId).append('\n');
        for (Order o : orders.values()) {
            sb.append("O ").append(o.id()).append(';').append(o.itemId()).append(';').append(o.count()).append(';')
                    .append(o.price()).append(';').append(o.buyer()).append('\n');
        }
        return sb.toString();
    }

    /** Odczyt zapisu (null = pusto); zle linie sa pomijane. */
    public static BuyOrders decode(String text) {
        BuyOrders b = new BuyOrders();
        if (text == null) return b;
        for (String line : text.split("\n")) {
            try {
                if (line.startsWith("N ")) {
                    b.nextId = Math.max(b.nextId, Integer.parseInt(line.substring(2).trim()));
                } else if (line.startsWith("O ")) {
                    String[] f = line.substring(2).split(";", 5);
                    Order o = new Order(Integer.parseInt(f[0]), f[4].trim(), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Long.parseLong(f[3]));
                    if (o.count() < 1 || o.price() <= 0 || o.buyer().isEmpty()) continue;
                    b.orders.put(o.id(), o);
                    b.nextId = Math.max(b.nextId, o.id() + 1);
                }
            } catch (RuntimeException e) {
                // pomijamy uszkodzona linie
            }
        }
        return b;
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
}
