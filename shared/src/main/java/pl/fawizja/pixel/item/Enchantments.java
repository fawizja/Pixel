package pl.fawizja.pixel.item;

import java.util.EnumMap;
import java.util.Map;

/**
 * Zaklecia kowala na czas polowania: kradziez zycia, kradziez many i cios krytyczny.
 * Czas plynie tylko poza strefa ochronna; kazdy zakup dodaje godzine, najwyzej {@link #MAX_MS}.
 */
public final class Enchantments {
    public enum Kind {
        LIFE("Kradzież życia", "6% zadanych obrażeń leczy", true),
        MANA("Kradzież many", "3% zadanych obrażeń wraca jako mana", true),
        CRIT("Cios krytyczny", "10% szans na +50% obrażeń", true),
        /** Zwoj nauki z plecaka (nie u kowala): +30% doswiadczenia za potwory. */
        EXP("Zwój nauki", "+30% doświadczenia za potwory", false);

        private final String label;
        private final String effect;
        private final boolean smith;

        Kind(String label, String effect, boolean smith) {
            this.label = label;
            this.effect = effect;
            this.smith = smith;
        }

        public String label() { return label; }
        public String effect() { return effect; }
        /** Czy zaklecie sprzedaje kowal (EXP pochodzi ze zwoju). */
        public boolean smith() { return smith; }
    }

    public static final long DURATION_MS = 3_600_000L;
    public static final long MAX_MS = 3 * DURATION_MS;
    public static final int LIFE_PERCENT = 6;
    public static final int MANA_PERCENT = 3;
    public static final int CRIT_CHANCE = 10;
    public static final int CRIT_BONUS_PERCENT = 50;
    public static final int EXP_BONUS_PERCENT = 30;

    /** Doswiadczenie ze zwojem nauki. */
    public long experience(long exp) {
        return active(Kind.EXP) ? exp + exp * EXP_BONUS_PERCENT / 100 : exp;
    }

    private final EnumMap<Kind, Long> remaining = new EnumMap<>(Kind.class);

    public static long price(Kind k, int level) {
        return (k == Kind.CRIT ? 400L : 300L) + (k == Kind.CRIT ? 25L : 20L) * Math.max(1, level);
    }

    public boolean active(Kind k) { return remainingMs(k) > 0; }

    public long remainingMs(Kind k) {
        Long v = remaining.get(k);
        return v == null ? 0 : v;
    }

    /** Czy kolejny zakup sie zmiesci (do 3 godzin). */
    public boolean canExtend(Kind k) { return remainingMs(k) + DURATION_MS <= MAX_MS; }

    public void extend(Kind k) {
        remaining.put(k, Math.min(MAX_MS, remainingMs(k) + DURATION_MS));
    }

    /** Uplyw czasu poza strefa ochronna; zwraca zaklecie, ktore wlasnie wygaslo, albo null. */
    public Kind tick(long dtMs) {
        Kind expired = null;
        for (Map.Entry<Kind, Long> e : remaining.entrySet()) {
            if (e.getValue() <= 0) continue;
            long left = Math.max(0, e.getValue() - dtMs);
            e.setValue(left);
            if (left == 0) expired = e.getKey();
        }
        remaining.values().removeIf(v -> v <= 0);
        return expired;
    }

    /** Leczenie z kradziezy (0, gdy zaklecie nieaktywne). */
    public int leech(Kind k, int dealt) {
        if (dealt <= 0 || !active(k)) return 0;
        int pct = k == Kind.LIFE ? LIFE_PERCENT : k == Kind.MANA ? MANA_PERCENT : 0;
        return Math.max(1, Math.round(dealt * pct / 100f));
    }

    /** Opis czasu: "45 min" albo "1 h 05 min". */
    public static String describe(long ms) {
        long min = (ms + 59_999) / 60_000;
        return min >= 60 ? (min / 60) + " h " + String.format("%02d", min % 60) + " min" : min + " min";
    }

    /** Zapis: "LIFE:123,CRIT:456". */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Kind, Long> e : remaining.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public void restore(String text) {
        remaining.clear();
        if (text == null || text.isBlank()) return;
        for (String part : text.split(",")) {
            String[] f = part.split(":");
            if (f.length != 2) continue;
            try {
                long v = Math.min(MAX_MS, Long.parseLong(f[1]));
                if (v > 0) remaining.put(Kind.valueOf(f[0]), v);
            } catch (IllegalArgumentException ignored) {
                // nieznane zaklecie ze starszego lub nowszego zapisu
            }
        }
    }

    public void copyFrom(Enchantments o) {
        remaining.clear();
        remaining.putAll(o.remaining);
    }
}
