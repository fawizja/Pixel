package pl.fawizja.pixel.character;

/**
 * Blogoslawienstwa jak w Tibii (i w Canary): piec zwyklych, Los Przeznaczenia (tylko PvP) i dwa gorskie.
 * Kazde zwykle i gorskie zmniejsza strate doswiadczenia o 8%; Zrzadzenie losu chroni inne blogoslawienstwa przy
 * smierci z reki gracza. Do poziomu {@link #ADVENTURER_LEVEL} chroni Blogoslawienstwo Poszukiwacza (za darmo).
 */
public enum Blessing {
    TWIST("Zrządzenie losu", Kind.PVP),
    WISDOM("Mądrość Samotności", Kind.REGULAR),
    SPARK("Iskra Feniksa", Kind.REGULAR),
    FIRE("Ogień Słońc", Kind.REGULAR),
    SHIELD("Duchowa Tarcza", Kind.REGULAR),
    EMBRACE("Objęcia Świata", Kind.REGULAR),
    HEART("Serce Góry", Kind.ENHANCED),
    BLOOD("Krew Góry", Kind.ENHANCED);

    public enum Kind { REGULAR, PVP, ENHANCED }

    /** Do tego poziomu wlacznie postac ma za darmo wszystkie zwykle blogoslawienstwa i Zrzadzenie losu. */
    public static final int ADVENTURER_LEVEL = 30;
    /** Zmniejszenie straty doswiadczenia za jedno blogoslawienstwo. */
    public static final int REDUCTION_EACH_PERCENT = 8;
    /** Miasto, w ktorym kaplan sprzedaje gorskie blogoslawienstwa. */
    public static final String MOUNTAIN_TOWN = "Durnkar";
    /** Hurtowy zakup wszystkich brakujacych zwyklych u kaplana: +10% (Henricus). */
    public static final double BULK_MULTIPLIER = 1.1;

    private final String label;
    private final Kind kind;

    Blessing(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    public String label() { return label; }
    public Kind kind() { return kind; }
    public int bit() { return 1 << ordinal(); }
    public boolean countsForLoss() { return kind != Kind.PVP; }

    /** Cena u kaplana (gp): do poz. 30 stala 2000, potem rosnaca jak w Tibii (gorskie drozsze, Zrzadzenie losu do 50 000). */
    public long price(int level) {
        switch (kind) {
            case PVP:
                return level <= 30 ? 2000 : level >= 270 ? 50_000 : 200L * (level - 20);
            case ENHANCED:
                return level <= 30 ? 2000 : level >= 120 ? 26_000 + 100L * (level - 120) : 260L * (level - 20);
            default:
                return level <= 30 ? 2000 : level >= 120 ? 20_000 + 75L * (level - 120) : 200L * (level - 20);
        }
    }

    /** Krotki klucz do akcji ("npc:bless:wisdom"). */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Blessing byKey(String key) {
        for (Blessing b : values()) if (b.key().equals(key)) return b;
        return null;
    }
}
