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

    /** Jak nizej, z nazwa z tierem Kuzni ("Miecz +2 (T3)") ogladanego stosu. */
    public static String stats(ItemType t, ItemStack s, ItemType equipped, int eu) {
        String base = stats(t, s.upgrade(), equipped, eu);
        String plain = Upgrade.name(t, s.upgrade());
        return s.tier() > 0 && base.startsWith(plain) ? Forge.name(t, s) + base.substring(plain.length()) : base;
    }

    /**
     * Linia Kuzni i nasycen do okna przedmiotu (jak "Look" w Tibii): "klasa 4, tier 2/10; Natarcie 1,1% (+60% obrażeń)
     * | nasycenia 1/3: Wampiryzm (Potężne): kradzież życia 25%, 19 h 59 min". Pusta, gdy przedmiot nie ma klasy ani miejsc.
     */
    public static String forgeLine(ItemType t, ItemStack s) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String f = Forge.describe(t, s.tier());
        if (!f.isEmpty()) parts.add(f);
        int slots = Imbuement.slots(t);
        if (slots > 0) {
            java.util.List<String> im = s.imbuements().describe();
            parts.add("nasycenia " + s.imbuements().size() + "/" + slots + (im.isEmpty() ? "" : ": " + String.join("; ", im)));
        }
        return String.join(" | ", parts);
    }

    /** Jak wyzej, z poziomami ulepszenia (u - ogladany przedmiot, eu - zalozony). */
    public static String stats(ItemType t, int u, ItemType equipped, int eu) {
        StringBuilder b = new StringBuilder(Upgrade.name(t, u));
        if (t.slot() == EquipSlot.LIGHT) return b.append("  światło ").append(t.lightRadius()).append(", pali się 20 min (pole obok butów)").toString();
        if (equipped == t && u == eu) {
            String bonus = t.bonus().describe();
            return b.append(" (założone)").append(bonus.isEmpty() ? "" : "  " + bonus).toString();
        }
        stat(b, "atak", Upgrade.attack(t, u), equipped == null ? 0 : Upgrade.attack(equipped, eu), equipped != null);
        stat(b, "obrona", Upgrade.defense(t, u), equipped == null ? 0 : Upgrade.defense(equipped, eu), equipped != null);
        stat(b, "pancerz", Upgrade.armor(t, u), equipped == null ? 0 : Upgrade.armor(equipped, eu), equipped != null);
        if (t.range() > 1) b.append("  zasięg ").append(t.range());
        String bonus = t.bonus().describe();
        if (!bonus.isEmpty()) b.append("  ").append(bonus);
        return b.toString();
    }

    /**
     * Krotka roznica do wiersza sklepu: "atak +5, pancerz -1" wzgledem zalozonego (equipped z ulepszeniem eu),
     * "jak założone" przy braku zmian, "nie dla ciebie" / "od poz. 20", gdy postac nie moze tego nosic; pusty tekst
     * dla przedmiotow, ktorych sie nie zaklada.
     */
    public static String shopDelta(ItemType t, ItemType equipped, int eu, PlayerCharacter pc) {
        if (t.slot() == EquipSlot.NONE) return "";
        if (t.slot() == EquipSlot.LIGHT) return "światło " + t.lightRadius() + ", 20 min, pole obok butów";
        if (t.category() == ItemCategory.BAIT) return "przynęta: trzymaj w plecaku, 1 na zarzucenie wędki";
        if (!t.vocations().contains(pc.vocation())) return "nie dla ciebie";
        if (t.slot() == EquipSlot.AMMO && equipped == null) return "amunicja, atak " + t.attack();
        StringBuilder b = new StringBuilder();
        delta(b, "atak", t.attack(), equipped == null ? 0 : Upgrade.attack(equipped, eu));
        delta(b, "obrona", t.defense(), equipped == null ? 0 : Upgrade.defense(equipped, eu));
        delta(b, "pancerz", t.armor(), equipped == null ? 0 : Upgrade.armor(equipped, eu));
        String prot = ItemBonus.describeProtections(t.bonus().protections());
        if (!prot.isEmpty() && equipped != t) b.append(b.length() > 0 ? ", " : "").append("ochrona ").append(prot);
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
