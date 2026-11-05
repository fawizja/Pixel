package pl.fawizja.pixel.combat;

/**
 * Rodzaje obrazen jak w Tibii (COMBAT_* w Canary). Pancerz redukuje tylko fizyczne; pozostale - odpornosci
 * procentowe potworow i ochrony ze sprzetu gracza. DROWN (utoniecie), LIFE_DRAIN (wyssanie zycia, bez pancerza)
 * i MANA_DRAIN (wyssanie many: zabiera mane zamiast zdrowia) zadaja tylko potwory.
 */
public enum DamageType {
    PHYSICAL, FIRE, ICE, EARTH, ENERGY, HOLY, DEATH, DROWN, LIFE_DRAIN, MANA_DRAIN;

    /** Nazwa po polsku (bestiariusz, opisy przedmiotow, okno postaci). */
    public String label() {
        switch (this) {
            case PHYSICAL: return "fizyczne";
            case FIRE: return "ogień";
            case ICE: return "lód";
            case EARTH: return "ziemia";
            case ENERGY: return "energia";
            case HOLY: return "święte";
            case DEATH: return "śmierć";
            case DROWN: return "utonięcie";
            case LIFE_DRAIN: return "wyssanie życia";
            case MANA_DRAIN: return "wyssanie many";
            default: return name();
        }
    }
}
