package pl.fawizja.pixel.npc;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.inventory.ItemSearch;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemType;

/** /cena: gdzie kupic i komu najlepiej sprzedac przedmiot (ceny kupcow NPC ze wszystkich miast). */
public final class PriceCheck {

    public static final int MAX_ROWS = 12;

    private PriceCheck() {}

    /** Przedmiot po nazwie albo kluczu (bez polskich znakow): najpierw pelna zgodnosc, potem poczatek, potem fragment. */
    public static ItemType find(ItemRegistry items, String query) {
        String q = ItemSearch.fold(query);
        if (q.length() < 2) return null;
        ItemType starts = null, contains = null;
        for (ItemType t : items.all()) {
            String n = ItemSearch.fold(t.name());
            String pn = ItemSearch.fold(t.polishName());
            if (n.equals(q) || pn.equals(q) || t.key().equals(q)) return t;
            if (starts == null && (n.startsWith(q) || pn.startsWith(q))) starts = t;
            if (contains == null && (n.contains(q) || pn.contains(q) || t.key().contains(q))) contains = t;
        }
        return starts != null ? starts : contains;
    }

    /** Wiersze: wartosc, potem kupcy od najwyzszej ceny skupu, na koncu tylko sprzedajacy. */
    public static List<String[]> rows(ItemType t, NpcRegistry npcs, ShopRegistry shops) {
        List<String[]> buyers = new ArrayList<>();
        List<long[]> order = new ArrayList<>();
        for (Npc n : npcs.all()) {
            Shop s = n.shopKey() == null || shops == null ? null : shops.get(n.shopKey());
            ShopOffer o = s == null ? null : s.offer(t.id());
            if (o == null || !o.sold() && !o.bought()) continue;
            String what = (o.bought() ? Messages.get("price_check.skupuje_za") + o.sellPrice() + " gp" : Messages.get("price_check.nie_skupuje"))
                    + (o.sold() ? Messages.get("price_check.sprzedaje_za") + o.buyPrice() + " gp" : "");
            order.add(new long[] {o.bought() ? o.sellPrice() : -1, buyers.size()});
            buyers.add(new String[] {n.name() + " (" + n.town() + ")", what});
        }
        order.sort((a, b) -> Long.compare(b[0], a[0]));
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {Messages.get("price_check.wartosc"), t.value() + Messages.get("price_check.gp_za_sztuke")});
        for (long[] o : order) {
            if (out.size() >= MAX_ROWS) break;
            out.add(buyers.get((int) o[1]));
        }
        if (out.size() == 1) out.add(new String[] {Messages.get("price_check.kupcy"), Messages.get("price_check.zaden_kupiec_nie_handluje")});
        return out;
    }
}
