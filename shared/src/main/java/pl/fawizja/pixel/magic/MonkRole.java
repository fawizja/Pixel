package pl.fawizja.pixel.magic;

/**
 * Rola czaru mnicha wobec harmonii (Canary MonkSpell_t, kolumna "mnich" w spells.csv): B buduje (+1 punkt),
 * S wydaje (zuzywa wszystkie punkty i mnozy efekt przez premie harmonii), puste pole = bez wplywu.
 */
public enum MonkRole {
    NONE, BUILDER, SPENDER;

    public static MonkRole parse(String s) {
        switch (s == null ? "" : s.trim()) {
            case "B": return BUILDER;
            case "S": return SPENDER;
            case "": case "-": return NONE;
            default: throw new IllegalArgumentException("bad monk role " + s);
        }
    }

    /** Polska nazwa roli do ksiegi czarow. */
    public String label() {
        switch (this) {
            case BUILDER: return "buduje harmonię";
            case SPENDER: return "wydaje harmonię";
            default: return "";
        }
    }
}
