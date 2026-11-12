package pl.fawizja.pixel.game;

import pl.fawizja.pixel.i18n.Messages;
import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.Enchantments;
import pl.fawizja.pixel.npc.Shop;
import pl.fawizja.pixel.npc.ShopOffer;

/**
 * Sklep z gornej ramki (jak Tibia Store): tylko uslugi i rzeczy, ktore juz sa w grze (konto premium i promocja od
 * krola, zaklecia kowala, siodlo i zwierzaki z kupcow), w tych samych cenach, placone
 * zlotem z konta w banku - bez chodzenia do NPC. Zakup: {@link GameSession#storeBuy(String, long)} (akcja "store:klucz").
 */
public final class Store {

    /** Pozycja sklepu: blocked = powod, dla ktorego teraz nie da sie kupic (null = mozna). icon = klucz przedmiotu. */
    public record Offer(String key, String name, String desc, long price, String blocked, String icon) {
        public boolean available() { return blocked == null; }
    }

    /**
     * Zakladki okna sklepu (2026-11-11, Darek: "dodaj w sklepie zakladke premium" i "pozycje outfity na przyszlosc"):
     * uslugi (zaklecia, wierzchowiec, zwierzaki), premium (konto premium i promocja z opisem, co daja)
     * i stroje (kolory stroju za darmo, nowe stroje z data/store_outfits.csv - na razie "wkrotce").
     */
    public enum Tab {
        SERVICES("uslugi", "Usługi"), PREMIUM("premium", "Premium"), OUTFITS("outfity", "Outfity");

        public final String key;
        private final String label;
        public String label() { return Messages.tr(label); }

        Tab(String key, String label) {
            this.key = key;
            this.label = label;
        }

        /** Zakladka po kluczu ("premium"); nieznany - uslugi. */
        public static Tab byKey(String key) {
            for (Tab t : values()) if (t.key.equals(key)) return t;
            return SERVICES;
        }
    }

    /** Akcja wiersza zakladki ("store:tab:premium"). */
    public static final String TAB_ACTION = "store:tab:";

    /** Pupile od opiekunow zwierzat w miastach (sklepy *_pets w data/shops.csv). */
    static final String[] PETS = {"pet_dog", "pet_cat", "pet_raven", "pet_fennec", "pet_gull", "pet_parrot"};

    private Store() {}

    public static List<Offer> offers(GameSession s) {
        PlayerCharacter pc = s.character();
        List<Offer> out = new ArrayList<>();
        out.add(new Offer("premium", Messages.get("store.konto_premium"), Messages.get("store.rejsy_statkami_miedzy_portami"), GameSession.PREMIUM_PRICE,
                pc.isPremium() ? Messages.get("store.masz_juz_konto_premium") : null, "pearl"));
        String promo = pc.isPromoted() ? Messages.get("store.juz_jestes") + pc.vocationTitle()
                : !pc.isPremium() ? Messages.get("store.najpierw_konto_premium")
                : pc.level() < GameSession.PROMOTION_LEVEL ? Messages.get("hud.od_poziomu") + GameSession.PROMOTION_LEVEL : null;
        out.add(new Offer("promote", Messages.get("hud.promocja") + pc.vocation().promotedName(), Messages.get("store.znak_przy_postaci_regeneracja"),
                GameSession.PROMOTION_PRICE, promo, "grimtooth_crown"));
        for (Enchantments.Kind k : Enchantments.Kind.values()) {
            if (!k.smith()) continue;
            Enchantments e = pc.enchantments();
            out.add(new Offer("enchant_" + k.name().toLowerCase(java.util.Locale.ROOT), k.label() + " (1 h)", k.effect() + Messages.get("store.przez_godzine_polowania"),
                    Enchantments.price(k, pc.level()), e.canExtend(k) ? null : Messages.get("store.zapas_na") + Enchantments.describe(e.remainingMs(k)), null));
        }
        addItem(s, out, "saddle", Messages.get("store.wierzchowiec_kon"), Messages.get("store.kon_na_zawsze_60"), pc.hasMount() ? Messages.get("store.masz_juz_konia") : null);
        for (String pet : PETS) addItem(s, out, pet, null, null, null);
        return out;
    }

    /** Zakladka oferty: konto premium i promocja w Premium, reszta w Uslugach. */
    public static Tab tabOf(Offer o) {
        return o.key().equals("premium") || o.key().equals("promote") ? Tab.PREMIUM : Tab.SERVICES;
    }

    /** Oferty jednej zakladki (stroje nie sa ofertami - patrz {@link #rows}). */
    public static List<Offer> offers(GameSession s, Tab tab) {
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers(s)) if (tabOf(o) == tab) out.add(o);
        return out;
    }

    /**
     * Wiersze okna sklepu {etykieta, opis, stan, akcja[, znacznik]}: najpierw zakladki (znacznik "tab" / "tab_on"),
     * potem tresc wybranej zakladki - oferty (dotkniecie: "store:ask:klucz", niedostepne "off"), w Premium dodatkowo
     * co daje konto premium, w Outfitach kolory stroju (akcja "outfit:i") i stroje "wkrotce" ("store:soon:klucz"); opis premium ze
     * znacznikiem "info" (sam tekst).
     */
    public static List<String[]> rows(GameSession s, Tab tab) {
        List<String[]> rows = new ArrayList<>();
        for (Tab t : Tab.values()) rows.add(new String[] {t.label(), "", "", TAB_ACTION + t.key, t == tab ? "tab_on" : "tab"});
        PlayerCharacter pc = s.character();
        if (tab == Tab.OUTFITS) {
            int now = pc.outfit();
            for (int i = 0; i < pl.fawizja.pixel.character.Outfit.count(); i++) {
                rows.add(new String[] {Messages.get("store.kolor_stroju") + pl.fawizja.pixel.character.Outfit.name(i),
                        i == now ? Messages.get("game_screen.noszony_teraz") : Messages.get("store.za_darmo_dotknij_aby"), "", "outfit:" + i});
            }
            for (StoreOutfits.Entry e : StoreOutfits.all()) {
                String price = e.price() > 0 ? String.format("%,d gp", e.price()) : Messages.get("store.za_darmo");
                // "wkrotce": zwykly (czytelny) wiersz z dopiskiem; dotkniecie mowi tylko, ze stroj bedzie pozniej
                rows.add(new String[] {Messages.get("game_session.stroj") + e.name() + (e.soon() ? Messages.get("store.wkrotce") : ""), (e.soon() ? "" : price + " - ") + e.desc(), "",
                        "store:soon:" + e.key()});
            }
            return rows;
        }
        for (Offer o : offers(s, tab)) {
            String price = String.format("%,d gp", o.price());
            rows.add(o.available() ? new String[] {o.name(), price, o.desc(), "store:ask:" + o.key()}
                    : new String[] {o.name(), price + " - " + o.blocked(), "", "store:ask:" + o.key(), "off"});
        }
        if (tab == Tab.PREMIUM) rows.addAll(premiumInfo(pc));
        return rows;
    }

    /** Co daje konto premium (wiersze tylko z tekstem: akcja "ach:none", znacznik "info"): to, co gra naprawde sprawdza. */
    public static List<String[]> premiumInfo(PlayerCharacter pc) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {Messages.get("store.twoje_konto") + (pc.isPremium() ? "premium" : "darmowe"),
                pc.isPremium() ? Messages.get("store.premium_bez_limitu_czasu") : String.format(Messages.get("store.premium_raz_na_zawsze"), GameSession.PREMIUM_PRICE), "", "ach:none", "info"});
        rows.add(new String[] {Messages.get("store.rejsy_statkami"), Messages.get("store.kapitanowie_zabiora_cie_do"), "", "ach:none", "info"});
        rows.add(new String[] {Messages.get("store.premia_staminy"), Messages.get("store.150_doswiadczenia_przy_zielone"), "", "ach:none", "info"});
        rows.add(new String[] {Messages.get("bestiary_card.charmy"), pl.fawizja.pixel.charm.Charms.PREMIUM_SLOTS + Messages.get("store.miejsc_zamiast")
                + pl.fawizja.pixel.charm.Charms.FREE_SLOTS, "", "ach:none", "info"});
        rows.add(new String[] {Messages.get("store.promocja_profesji"), Messages.get("hud.od_poziomu") + GameSession.PROMOTION_LEVEL + Messages.get("store.szybsza_regeneracja"), "", "ach:none", "info"});
        rows.add(new String[] {Messages.get("store.kolo_przeznaczenia"), Messages.get("hud.od_poziomu") + pl.fawizja.pixel.wheel.DestinyWheel.MIN_LEVEL + Messages.get("store.z_promocja"), "", "ach:none", "info"});
        return rows;
    }

    /** Przedmiot w najnizszej cenie kupcow (tylko gdy ktos go sprzedaje). */
    private static void addItem(GameSession s, List<Offer> out, String key, String name, String desc, String blocked) {
        if (!s.items().containsKey(key)) return;
        long price = itemPrice(s, key);
        if (price <= 0) return;
        pl.fawizja.pixel.item.ItemType t = s.items().byKey(key);
        if (blocked == null && s.character().backpack().count(t.id()) > 0) blocked = Messages.get("store.masz_w_plecaku");
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
