package pl.fawizja.pixel.item;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Opis przedmiotu do okna plecaka: statystyki z roznica wzgledem zalozonego przedmiotu w tym samym miejscu
 * (np. "atak 30 (+5)") i wymagania (poziom, profesje) oraz wartosc.
 */
public final class ItemCompare {

    private ItemCompare() {}

    /** Pierwsza linia: nazwa i statystyki z roznicami; equipped = przedmiot w tym samym miejscu albo null. */
    public static String stats(ItemType t, ItemType equipped) {
        return stats(t, 0, equipped, 0);
    }

    /** Jak wyzej, z poziomami ulepszenia (u - ogladany przedmiot, eu - zalozony). */
    public static String stats(ItemType t, int u, ItemType equipped, int eu) {
        StringBuilder b = new StringBuilder(Upgrade.name(t, u));
        if (equipped == t && u == eu) return b.append(" (założone)").toString();
        stat(b, "atak", Upgrade.attack(t, u), equipped == null ? 0 : Upgrade.attack(equipped, eu), equipped != null);
        stat(b, "obrona", Upgrade.defense(t, u), equipped == null ? 0 : Upgrade.defense(equipped, eu), equipped != null);
        stat(b, "pancerz", Upgrade.armor(t, u), equipped == null ? 0 : Upgrade.armor(equipped, eu), equipped != null);
        if (t.range() > 1) b.append("  zasięg ").append(t.range());
        return b.toString();
    }

    /**
     * Krotka roznica do wiersza sklepu: "atak +5, pancerz -1" wzgledem zalozonego (equipped z ulepszeniem eu),
     * "jak założone" przy braku zmian, "nie dla ciebie" / "od poz. 20", gdy postac nie moze tego nosic; pusty tekst
     * dla przedmiotow, ktorych sie nie zaklada.
     */
    public static String shopDelta(ItemType t, ItemType equipped, int eu, PlayerCharacter pc) {
        if (t.slot() == EquipSlot.NONE) return "";
        if (t.category() == ItemCategory.BAIT) return "przynęta: do gniazda amunicji, 1 na zarzucenie";
        if (!t.vocations().contains(pc.vocation())) return "nie dla ciebie";
        if (t.slot() == EquipSlot.AMMO && equipped == null) return "amunicja, atak " + t.attack();
        StringBuilder b = new StringBuilder();
        delta(b, "atak", t.attack(), equipped == null ? 0 : Upgrade.attack(equipped, eu));
        delta(b, "obrona", t.defense(), equipped == null ? 0 : Upgrade.defense(equipped, eu));
        delta(b, "pancerz", t.armor(), equipped == null ? 0 : Upgrade.armor(equipped, eu));
        String out = b.length() == 0 ? "jak założone" : b.toString();
        return pc.level() < t.minLevel() ? out + ", od poz. " + t.minLevel() : out;
    }

    private static void delta(StringBuilder b, String label, int value, int current) {
        if (value == current) return;
        if (b.length() > 0) b.append(", ");
        b.append(label).append(' ').append(value > current ? "+" : "").append(value - current);
    }

    private static void stat(StringBuilder b, String label, int value, int current, boolean compare) {
        if (value <= 0 && current <= 0) return;
        b.append("  ").append(label).append(' ').append(value);
        if (compare && value != current) b.append(" (").append(value > current ? "+" : "").append(value - current).append(')');
    }

    /** Druga linia: wymagania (z oznaczeniem, czego postac nie spelnia) i wartosc; pusta, gdy nic do powiedzenia. */
    public static String requirements(ItemType t, PlayerCharacter pc) {
        StringBuilder b = new StringBuilder();
        if (t.minLevel() > 1) {
            b.append("poziom ").append(t.minLevel());
            if (pc.level() < t.minLevel()) b.append(" (za niski)");
        }
        if (t.slot() != EquipSlot.NONE && t.vocations().size() < Vocation.values().length) {
            if (b.length() > 0) b.append(", ");
            b.append("tylko: ");
            boolean first = true;
            for (Vocation v : t.vocations()) {
                if (!first) b.append('/');
                b.append(v.displayName());
                first = false;
            }
            if (!t.usableBy(pc.vocation())) b.append(" (nie dla ciebie)");
        }
        if (t.value() > 0) {
            if (b.length() > 0) b.append(", ");
            b.append(String.format("wartość %,d gp", t.value()));
        }
        return b.toString();
    }
}
