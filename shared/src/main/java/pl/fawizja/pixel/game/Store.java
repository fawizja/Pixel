package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;

/**
 * Sklep z gornej ramki (jak Tibia Store): tylko uslugi i rzeczy, ktore juz sa w grze (konto premium i promocja od
 * krola, blogoslawienstwo kaplana, zaklecia kowala, siodlo i zwierzaki z kupcow), w tych samych cenach, placone
 * zlotem z konta w banku - bez chodzenia do NPC. Zakup: {@link GameSession#storeBuy(String, long)} (akcja "store:klucz").
 */
public final class Store {

    /** Pozycja sklepu: blocked = powod, dla ktorego teraz nie da sie kupic (null = mozna). icon = klucz przedmiotu. */
    public record Offer(String key, String name, String desc, long price, String blocked, String icon) {
        public boolean available() { return blocked == null; }
    }

    /** Zwierzaki od kupcow (data/shops.csv). */
    static final String[] PETS = {"pet_dog", "pet_cat", "pet_raven", "pet_fennec"};

    private Store() {}

    public static List<Offer> offers(GameSession s) {
        PlayerCharacter pc = s.character();
        List<Offer> out = new ArrayList<>();
        out.add(new Offer("premium", "Konto premium", "rejsy statkami między portami", GameSession.PREMIUM_PRICE,
                pc.isPremium() ? "masz już konto premium" : null, "pearl"));
        String promo = pc.isPromoted() ? "już jesteś " + pc.vocationTitle()
                : !pc.isPremium() ? "najpierw konto premium"
                : pc.level() < GameSession.PROMOTION_LEVEL ? "od poziomu " + GameSession.PROMOTION_LEVEL : null;
        out.add(new Offer("promote", "Promocja: " + pc.vocation().promotedName(), "znak przy postaci, regeneracja o połowę szybsza",
                GameSession.PROMOTION_PRICE, promo, "grimtooth_crown"));
        out.add(new Offer("bless", "Błogosławieństwo", "najbliższa śmierć: połowa straty doświadczenia",
                pl.fawizja.pixel.balance.Balance.blessingPrice(pc.level()), pc.isBlessed() ? "już jesteś pobłogosławiony" : null, "healing_herb"));
        for (Enchantments.Kind k : Enchantments.Kind.values()) {
            if (!k.smith()) continue;
            Enchantments e = pc.enchantments();
            out.add(new Offer("enchant_" + k.name().toLowerCase(java.util.Locale.ROOT), k.label() + " (1 h)", k.effect() + " przez godzinę polowania",
                    Enchantments.price(k, pc.level()), e.canExtend(k) ? null : "zapas na " + Enchantments.describe(e.remainingMs(k)), null));
        }
        addItem(s, out, "saddle", "Wierzchowiec: koń", "koń na zawsze, +60 szybkości (użyj siodła)", pc.hasMount() ? "masz już konia" : null);
        for (String pet : PETS) addItem(s, out, pet, null, null, null);
        return out;
    }

    /** Przedmiot w najnizszej cenie kupcow (tylko gdy ktos go sprzedaje). */
    private static void addItem(GameSession s, List<Offer> out, String key, String name, String desc, String blocked) {
        if (!s.items().containsKey(key)) return;
        long price = itemPrice(s, key);
        if (price <= 0) return;
        pl.fawizja.pixel.item.ItemType t = s.items().byKey(key);
        if (blocked == null && s.character().backpack().count(t.id()) > 0) blocked = "masz w plecaku";
        if (desc == null) {
            // pierwsze zdanie opisu przedmiotu ("Wierny piesek z Varden")
            desc = t.description() == null ? "" : t.description();
            int dot = desc.indexOf('.');
            if (dot > 0) desc = desc.substring(0, dot);
            if (!desc.isEmpty()) desc = "zwierzak: " + desc.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + desc.substring(1);
        }
        out.add(new Offer(key, name != null ? name : t.name(), desc, price, blocked, key));
    }

    /** Najnizsza cena kupna przedmiotu u kupcow albo 0, gdy nikt go nie sprzedaje. */
    static long itemPrice(GameSession s, String key) {
        if (s.shops() == null || !s.items().containsKey(key)) return 0;
        int id = s.items().byKey(key).id();
        long best = 0;
        for (Shop shop : s.shops().all()) {
            ShopOffer o = shop.offer(id);
            if (o != null && o.sold() && (best == 0 || o.buyPrice() < best)) best = o.buyPrice();
        }
        return best;
    }

    public static Offer find(GameSession s, String key) {
        for (Offer o : offers(s)) if (o.key().equals(key)) return o;
        return null;
    }
}
