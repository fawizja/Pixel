package pl.fawizja.pixel.combat;

import pl.fawizja.pixel.i18n.Messages;
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
            case FIRE: return Messages.get("damage_type.ogien");
            case ICE: return Messages.get("damage_type.lod");
            case EARTH: return "ziemia";
            case ENERGY: return "energia";
            case HOLY: return Messages.get("damage_type.swiete");
            case DEATH: return Messages.get("damage_type.smierc");
            case DROWN: return Messages.get("damage_type.utoniecie");
            case LIFE_DRAIN: return Messages.get("damage_type.wyssanie_zycia");
            case MANA_DRAIN: return Messages.get("damage_type.wyssanie_many");
            default: return name();
        }
    }
}
