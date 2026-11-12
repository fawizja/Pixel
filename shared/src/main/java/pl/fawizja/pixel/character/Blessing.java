package pl.fawizja.pixel.character;

import pl.fawizja.pixel.i18n.Messages;

/**
 * Blogoslawienstwa jak w Tibii (i w Canary): piec zwyklych i Zrzadzenie losu (tylko PvP). Kazde zwykle zmniejsza
 * strate (doswiadczenia, umiejetnosci, poziomu magii) o 8 punktow procentowych od straty bazowej, a w calosci chroni
 * tez przedmioty: z piecioma nic nie wypada z ekwipunku. Zrzadzenie losu chroni zwykle blogoslawienstwa przy smierci
 * z reki gracza. Do poziomu {@link #ADVENTURER_LEVEL} chroni Blogoslawienstwo Poszukiwacza (za darmo).
 *
 * <p>Kazde blogoslawienstwo sprzedaje jeden mnich w kaplicy poza miastem ({@link #sellerKey()}): trzy w kaplicach
 * kontynentu glownego (konto darmowe) i trzy w ziemiach premium (wymagaja konta premium).
 * Kolejnosc stalych to bity zapisu postaci - nie zmieniac.
 */
public enum Blessing {
    TWIST("Zrządzenie losu", Kind.PVP, "nerys_blesser", true),
    WISDOM("Mądrość Samotności", Kind.REGULAR, "selv_blesser", true),
    SPARK("Iskra Feniksa", Kind.REGULAR, "valmir_blesser", true),
    FIRE("Ogień Słońc", Kind.REGULAR, "qas_blesser", false),
    SHIELD("Duchowa Tarcza", Kind.REGULAR, "varden_blesser", false),
    EMBRACE("Objęcia Świata", Kind.REGULAR, "frost_blesser", false);

    public enum Kind { REGULAR, PVP }

    /** Do tego poziomu wlacznie postac ma za darmo wszystkie zwykle blogoslawienstwa i Zrzadzenie losu. */
    public static final int ADVENTURER_LEVEL = 30;
    /** Zmniejszenie straty za jedno blogoslawienstwo (punkty procentowe straty bazowej, jak w Canary). */
    public static final int REDUCTION_EACH_PERCENT = 8;
    /** Ile zwyklych blogoslawienstw chroni caly ekwipunek. */
    public static final int REGULAR_COUNT = 5;

    private final String label;
    private final Kind kind;
    private final String sellerKey;
    private final boolean premium;

    Blessing(String label, Kind kind, String sellerKey, boolean premium) {
        this.label = label;
        this.kind = kind;
        this.sellerKey = sellerKey;
        this.premium = premium;
    }

    public String label() { return Messages.get("bless.n_" + key()); }
    public Kind kind() { return kind; }
    public int bit() { return 1 << ordinal(); }
    public boolean countsForLoss() { return kind != Kind.PVP; }
    /** Klucz NPC (mnicha), ktory sprzedaje to blogoslawienstwo. */
    public String sellerKey() { return sellerKey; }
    /** Czy kupno wymaga konta premium (mnich w ziemiach premium). */
    public boolean premium() { return premium; }

    /** Cena u mnicha (gp): do poz. 30 stala 2000, potem rosnaca jak w Tibii (Zrzadzenie losu do 50 000). */
    public long price(int level) {
        if (kind == Kind.PVP) return level <= 30 ? 2000 : level >= 270 ? 50_000 : 200L * (level - 20);
        return level <= 30 ? 2000 : level >= 120 ? 20_000 + 75L * (level - 120) : 200L * (level - 20);
    }

    /** Krotki klucz do akcji ("npc:bless:wisdom"). */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Blessing byKey(String key) {
        for (Blessing b : values()) if (b.key().equals(key)) return b;
        return null;
    }

    /** Blogoslawienstwo sprzedawane przez mnicha o danym kluczu NPC albo null. */
    public static Blessing soldBy(String npcKey) {
        for (Blessing b : values()) if (b.sellerKey.equals(npcKey)) return b;
        return null;
    }
}
