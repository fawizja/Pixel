package pl.fawizja.pixel.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pl.fawizja.pixel.combat.DamageType;

/**
 * Nasycenia jednego przedmiotu (niezmienne): lista aktywnych nasycen z pozostalym czasem walki. Liczbe miejsc
 * wyznacza {@link Imbuement#slots(ItemType)}; jedno nasycenie z kazdej grupy (ochrony: jedno na zywiol).
 * Zapis: "VAMPIRISM3@72000~STRIKE1@100" (rodzaj, stopien, sekundy pozostale).
 */
public record Imbuements(List<Slot> slots) {

    public static final Imbuements NONE = new Imbuements(List.of());

    /** Jedno nasycenie: rodzaj, stopien 1..3 i pozostaly czas walki (ms). */
    public record Slot(Imbuement kind, int level, long msLeft) {
        public Slot {
            if (kind == null || level < 1 || level > 3) throw new IllegalArgumentException("imbuement level 1..3");
            msLeft = Math.max(0, Math.min(Imbuement.DURATION_MS, msLeft));
        }
    }

    public Imbuements {
        slots = Collections.unmodifiableList(new ArrayList<>(slots == null ? List.of() : slots));
    }

    public boolean isEmpty() { return slots.isEmpty(); }
    public int size() { return slots.size(); }

    public Slot get(Imbuement kind) {
        for (Slot s : slots) if (s.kind() == kind) return s;
        return null;
    }

    /** Czy przedmiot ma juz nasycenie z tej samej grupy (ochrony: tego samego zywiolu). */
    public boolean conflicts(Imbuement kind) {
        for (Slot s : slots) {
            if (s.kind() == kind) return true;
            if (s.kind().category() == kind.category() && kind.category() != Imbuement.Category.PROTECTION) return true;
        }
        return false;
    }

    public Imbuements with(Imbuement kind, int level) {
        List<Slot> out = new ArrayList<>(slots);
        out.add(new Slot(kind, level, Imbuement.DURATION_MS));
        return new Imbuements(out);
    }

    public Imbuements without(int index) {
        List<Slot> out = new ArrayList<>(slots);
        out.remove(index);
        return out.isEmpty() ? NONE : new Imbuements(out);
    }

    /** Uplyw czasu walki; wygasle nasycenia znikaja (zwraca ten sam obiekt, gdy nic sie nie zmienilo). */
    public Imbuements tick(long dtMs) {
        if (slots.isEmpty() || dtMs <= 0) return this;
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots) {
            long left = s.msLeft() - dtMs;
            if (left > 0) out.add(new Slot(s.kind(), s.level(), left));
        }
        return out.isEmpty() ? NONE : new Imbuements(out);
    }

    /** Suma wartosci aktywnych nasycen danej grupy (np. kradziez zycia). */
    public double sum(Imbuement.Category c) {
        double v = 0;
        for (Slot s : slots) if (s.kind().category() == c) v += s.kind().value(s.level());
        return v;
    }

    /** Ochrona przed zywiolem z nasycen (procent). */
    public int protection(DamageType t) {
        int v = 0;
        for (Slot s : slots) {
            if (s.kind().category() == Imbuement.Category.PROTECTION && s.kind().element() == t) v += (int) s.kind().value(s.level());
        }
        return v;
    }

    /** Zamiana obrazen fizycznych: pierwsze nasycenie zywiolowe (na przedmiocie moze byc tylko jedno) albo null. */
    public Slot elementDamage() {
        for (Slot s : slots) if (s.kind().category() == Imbuement.Category.ELEMENT_DAMAGE) return s;
        return null;
    }

    /** Czy jest krytyk (Strike). */
    public boolean hasStrike() { return sum(Imbuement.Category.CRITICAL) > 0; }

    public String encode() {
        StringBuilder b = new StringBuilder();
        for (Slot s : slots) {
            if (b.length() > 0) b.append('~');
            b.append(s.kind().name()).append(s.level()).append('@').append((s.msLeft() + 999) / 1000);
        }
        return b.toString();
    }

    public static Imbuements parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        List<Slot> out = new ArrayList<>();
        for (String part : text.split("~")) {
            int at = part.indexOf('@');
            if (at < 2) continue;
            String head = part.substring(0, at);
            Imbuement k = Imbuement.byName(head.substring(0, head.length() - 1));
            if (k == null) continue; // nieznane nasycenie z nowszej wersji
            int level = head.charAt(head.length() - 1) - '0';
            long sec = Long.parseLong(part.substring(at + 1).trim());
            if (level < 1 || level > 3 || sec <= 0) continue;
            out.add(new Slot(k, level, sec * 1000));
        }
        return out.isEmpty() ? NONE : new Imbuements(out);
    }

    /** Opis do okna przedmiotu: "Wampiryzm (Potężne): kradzież życia 25%, 19 h 59 min". */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Slot s : slots) {
            out.add(s.kind().label() + " (" + Imbuement.LEVELS[s.level()] + "): " + s.kind().effect(s.level()) + ", "
                    + Enchantments.describe(s.msLeft()));
        }
        return out;
    }
}
