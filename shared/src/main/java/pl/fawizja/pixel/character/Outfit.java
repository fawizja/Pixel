package pl.fawizja.pixel.character;

/**
 * Kolor stroju postaci: 0 = kolor profesji, pozostale to stale kolory do wyboru (bez oplat).
 * Indeks jest zapisywany w postaci i wysylany innym graczom w klatce stanu.
 */
public final class Outfit {

    public static final String[] NAMES = {"profesji", "czerwony", "niebieski", "zielony", "czarny", "biały", "fioletowy", "złoty"};
    /** Kolor tulowia (#rrggbb); dla 0 null - rysunek bierze kolor profesji. */
    public static final String[] COLORS = {null, "#b8322a", "#2f5fb8", "#3a8a3a", "#2c2a30", "#d8d6cc", "#7a3aa8", "#c8a030"};

    private Outfit() {}

    public static int count() { return NAMES.length; }

    /** Bledny indeks wraca do koloru profesji. */
    public static int clamp(int i) { return i >= 0 && i < NAMES.length ? i : 0; }

    public static String name(int i) { return NAMES[clamp(i)]; }

    /** "czerwony", "bialy", "3" -> indeks; -1, gdy nie ma takiego koloru. */
    public static int parse(String s) {
        if (s == null) return -1;
        String t = fold(s.trim());
        if (t.isEmpty()) return -1;
        try {
            int i = Integer.parseInt(t);
            return i >= 0 && i < NAMES.length ? i : -1;
        } catch (NumberFormatException ignored) {
            // nazwa koloru
        }
        for (int i = 0; i < NAMES.length; i++) if (fold(NAMES[i]).equals(t)) return i;
        return -1;
    }

    private static String fold(String s) {
        String t = s.toLowerCase(java.util.Locale.ROOT);
        return java.text.Normalizer.normalize(t.replace('ł', 'l'), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
