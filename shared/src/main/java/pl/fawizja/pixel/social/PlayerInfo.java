package pl.fawizja.pixel.social;

import pl.fawizja.pixel.vocation.Vocation;

/**
 * Co wiadomo o innej postaci (okno po dotknieciu gracza): poziom, profesja, gildia i czy jest w grze.
 * Komunikat serwera "PINFO imie;poziom;PROFESJA;gildia;online" (gildia "-" = brak, PROFESJA "-" = nie wiadomo);
 * dla nieznanej postaci "PINFO imie;0;-;-;0" ({@link #known()} = false).
 */
public record PlayerInfo(String name, int level, Vocation vocation, String guild, boolean online) {

    public boolean known() { return level > 0; }

    public static PlayerInfo unknown(String name) { return new PlayerInfo(name, 0, null, null, false); }

    /** Pola komunikatu do wyslania, z nazwa "PINFO". */
    public String[] encode() {
        return new String[] {"PINFO", clean(name) + ";" + level + ";" + (vocation == null ? "-" : vocation.name()) + ";"
                + (guild == null || guild.isBlank() ? "-" : clean(guild)) + ";" + (online ? 1 : 0)};
    }

    private static String clean(String s) { return s.replace(";", "").replace("\t", " ").replace("\n", " "); }

    /** Odczyt komunikatu; null dla obcej albo zlej linii. */
    public static PlayerInfo parse(String[] m) {
        if (m == null || m.length < 2 || !"PINFO".equals(m[0])) return null;
        String[] f = m[1].split(";");
        if (f.length != 5) return null;
        try {
            Vocation v = null;
            if (!f[2].equals("-")) {
                try {
                    v = Vocation.valueOf(f[2]);
                } catch (IllegalArgumentException ignored) {
                    // nieznana profesja
                }
            }
            return new PlayerInfo(f[0], Integer.parseInt(f[1]), v, f[3].equals("-") ? null : f[3], "1".equals(f[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Krotki opis do okna, np. "Druid, poziom 42, gildia Wilki, w grze". */
    public String describe() {
        if (!known()) return "Nie znamy tej postaci.";
        return (vocation == null ? "" : vocation.displayName() + ", ") + "poziom " + level
                + (guild == null ? "" : ", gildia " + guild) + (online ? ", w grze" : ", poza grą");
    }
}
