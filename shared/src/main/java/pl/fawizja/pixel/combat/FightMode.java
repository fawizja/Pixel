package pl.fawizja.pixel.combat;

/**
 * Tryb walki jak w starej Tibii (przycisk miecz / miecz z tarcza / tarcza), wartosci wg The Forgotten Server
 * (src/player.cpp, Player::getAttackFactor i Player::getDefenseFactor):
 * <ul>
 *   <li>dzielnik ataku broni: ofensywny 1.0, zbalansowany 1.2, defensywny 2.0 (TFS dzieli przez niego czesc obrazen
 *       zalezna od broni i umiejetnosci, bez premii za poziom; rozdzki i czary - bez zmian),</li>
 *   <li>mnoznik obrony (tarcza / bron): ofensywny 0.5, zbalansowany 0.75, defensywny 1.0 - tylko gdy postac atakowala
 *       w ciagu ostatniego odstepu ataku; bez ataku kazdy tryb broni sie w pelni (1.0). Pancerz bez zmian.</li>
 * </ul>
 * Nasze formuly byly strojone dla zwyklej walki, wiec tryb zbalansowany (domyslny) zostaje punktem odniesienia 1.0,
 * a pozostale tryby sa przeliczone w tych samych proporcjach co w TFS (patrz {@link #damageFactor()} i
 * {@link #defenseFactor(boolean)}).
 */
public enum FightMode {
    OFFENSIVE("Ofensywny", "O", 1.0, 0.5),
    BALANCED("Zbalansowany", "", 1.2, 0.75),
    DEFENSIVE("Defensywny", "D", 2.0, 1.0);

    private final String label;
    private final String flag;
    private final double tfsAttackFactor;
    private final double tfsDefenseFactor;

    FightMode(String label, String flag, double tfsAttackFactor, double tfsDefenseFactor) {
        this.label = label;
        this.flag = flag;
        this.tfsAttackFactor = tfsAttackFactor;
        this.tfsDefenseFactor = tfsDefenseFactor;
    }

    /** Nazwa po polsku (etykieta przycisku). */
    public String label() { return label; }

    /** Litera w polu "flags" zapisu postaci ("" = zbalansowany, stary zapis bez "D"). */
    public String flag() { return flag; }

    /** Dzielnik ataku z TFS (getAttackFactor). */
    public double tfsAttackFactor() { return tfsAttackFactor; }

    /** Mnoznik obrony z TFS przy ataku (getDefenseFactor). */
    public double tfsDefenseFactor() { return tfsDefenseFactor; }

    /** Mnoznik czesci obrazen broni wzgledem trybu zbalansowanego: 1.2 / 1.0 / 0.6. */
    public double damageFactor() { return BALANCED.tfsAttackFactor / tfsAttackFactor; }

    /**
     * Mnoznik obrony (tarcza / bron) wzgledem zbalansowanego w walce: 0.67 / 1.0 / 1.33; bez wlasnego ataku w ostatnim
     * odstepie (TFS: 1.0 w kazdym trybie) - 1.33 w kazdym trybie.
     */
    public double defenseFactor(boolean attackedRecently) {
        return (attackedRecently ? tfsDefenseFactor : 1.0) / BALANCED.tfsDefenseFactor;
    }

    /** Nastepny tryb przycisku: ofensywny -> zbalansowany -> defensywny -> ofensywny. */
    public FightMode next() { return values()[(ordinal() + 1) % values().length]; }

    /** Tryb z flag zapisu: "O" ofensywny, "D" defensywny (stara "Tarcza"), inaczej zbalansowany. */
    public static FightMode fromFlags(String flags) {
        if (flags == null) return BALANCED;
        if (flags.contains("D")) return DEFENSIVE;
        if (flags.contains("O")) return OFFENSIVE;
        return BALANCED;
    }

    /** Tryb z nazwy (akcja "fightmode:OFFENSIVE"), null dla nieznanej. */
    public static FightMode parse(String s) {
        for (FightMode m : values()) if (m.name().equalsIgnoreCase(s)) return m;
        return null;
    }
}
