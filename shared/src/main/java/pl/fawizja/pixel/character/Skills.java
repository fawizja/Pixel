package pl.fawizja.pixel.character;

import java.util.EnumMap;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Umiejetnosci rosnace z uzyciem, liczone jak w Tibii (TibiaWiki "Formulae"):
 * przejscie z poziomu L na L+1 wymaga {@code A * b^(L - c)} prob, gdzie A to stala umiejetnosci
 * ({@link #skillConstant}), b stala profesji dla tej umiejetnosci ({@link #vocationConstant}),
 * a c poziom startowy (0 dla magii, 10 dla pozostalych). Dla magii "proba" to jeden punkt wydanej many.
 *
 * <p>{@link #addTries} dodaje surowe proby; rozgrywka uzywa {@link #train}, ktore mnozy je przez mnoznik
 * etapu serwera dla biezacego poziomu ({@link Balance#skillRate}, {@link Balance#magicRate}).
 *
 * <p>Zapis zawiera poziom i proby w biezacym poziomie. Po zmianie krzywej (stare zapisy z jedna krzywa
 * 50*1.1^n) poziom zostaje jaki byl, a postep jest tylko obcinany do 99% (nastepne proby moga od razu awansowac).
 */
public final class Skills {

    public static final int START_SKILL = 10;
    public static final int MAX_SKILL = 200;
    /** Gorny limit prob na jeden poziom (zapobiega przepelnieniu long dla bardzo stromych krzywych). */
    static final long MAX_TRIES = 1_000_000_000_000_000_000L;

    private final Vocation vocation;
    private final EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private final EnumMap<Skill, Long> tries = new EnumMap<>(Skill.class);

    /** Umiejetnosci bez profesji (stale "None" z Tibii, jak na Rookgaardzie). */
    public Skills() { this(null); }

    /** Umiejetnosci postaci danej profesji (null = bez profesji). */
    public Skills(Vocation vocation) {
        this.vocation = vocation;
        for (Skill s : Skill.values()) {
            levels.put(s, s == Skill.MAGIC ? 0 : START_SKILL);
            tries.put(s, 0L);
        }
    }

    public Vocation vocation() { return vocation; }
    public int level(Skill s) { return levels.get(s); }
    public long tries(Skill s) { return tries.get(s); }

    /** Proby potrzebne tej postaci do nastepnego poziomu umiejetnosci s. */
    public long triesForNext(Skill s) { return triesForNext(vocation, s, levels.get(s)); }

    /** Postep do nastepnego poziomu umiejetnosci w procentach (0..99; 100 na maksimum). */
    public int percentToNext(Skill s) {
        int lvl = levels.get(s);
        if (lvl >= MAX_SKILL) return 100;
        return (int) Math.min(99, tries.get(s) * 100 / Math.max(1, triesForNext(s)));
    }

    /** Postep do nastepnego poziomu jako ulamek 0..1 (pasek w oknie umiejetnosci); obciety ponizej 1. */
    public double progress(Skill s) {
        if (levels.get(s) >= MAX_SKILL) return 1.0;
        return Math.min(0.99, (double) tries.get(s) / Math.max(1, triesForNext(s)));
    }

    /** Stala umiejetnosci A z Tibii: magia 1600, wrecz 50, dystans 25, tarcza 100, wedkarstwo 20. */
    public static int skillConstant(Skill s) {
        switch (s) {
            case MAGIC: return 1600;
            case DISTANCE: return 25;
            case SHIELDING: return 100;
            case FISHING: return 20;
            default: return 50; // piesci, maczuga, miecz, topor
        }
    }

    /**
     * Stala profesji b z Tibii (TibiaWiki "Formulae"; mnich wg vocations.xml serwera Canary 15.x).
     * null = bez profesji.
     */
    public static double vocationConstant(Vocation v, Skill s) {
        if (s == Skill.FISHING) return 1.1;
        if (v == null) {
            switch (s) {
                case MAGIC: return 3.0;
                case FIST: case SHIELDING: return 1.5;
                default: return 2.0;
            }
        }
        switch (v) {
            case KNIGHT:
                switch (s) {
                    case MAGIC: return 3.0;
                    case DISTANCE: return 1.4;
                    default: return 1.1;
                }
            case PALADIN:
                switch (s) {
                    case MAGIC: return 1.4;
                    case DISTANCE: case SHIELDING: return 1.1;
                    default: return 1.2;
                }
            case SORCERER:
                switch (s) {
                    case MAGIC: return 1.1;
                    case FIST: case SHIELDING: return 1.5;
                    default: return 2.0;
                }
            case DRUID:
                switch (s) {
                    case MAGIC: return 1.1;
                    case FIST: case SHIELDING: return 1.5;
                    default: return 1.8;
                }
            case MONK:
                switch (s) {
                    case MAGIC: return 1.3;
                    case FIST: return 1.1;
                    case DISTANCE: return 2.0;
                    case SHIELDING: return 1.2;
                    default: return 1.5;
                }
            default: return 2.0;
        }
    }

    /** Poziom startowy c: 0 dla magii, 10 dla pozostalych umiejetnosci. */
    public static int offset(Skill s) { return s == Skill.MAGIC ? 0 : START_SKILL; }

    /** Liczba prob (dla magii: many) potrzebna profesji v do przejscia z poziomu level na level+1. */
    public static long triesForNext(Vocation v, Skill s, int level) {
        double val = skillConstant(s) * Math.pow(vocationConstant(v, s), Math.max(0, level - offset(s)));
        // jak w serwerach Tibii: czesc calkowita; epsilon chroni przed bledem zaokraglenia (50*1.1^2 = 60.5000..01)
        return (long) Math.max(1, Math.min((double) MAX_TRIES, Math.floor(val + 1e-6)));
    }

    /**
     * Proby z rozgrywki (cios, strzal, blok, rzut wedka, wydana mana) pomnozone przez mnoznik etapu serwera
     * dla biezacego poziomu umiejetnosci. Zwraca liczbe zdobytych poziomow.
     */
    public int train(Skill s, long baseTries) {
        if (baseTries <= 0) return 0;
        return addTries(s, Balance.applyRate(baseTries, rate(s)));
    }

    /** Biezacy mnoznik etapu serwera dla umiejetnosci (zalezy od jej poziomu). */
    public double rate(Skill s) {
        return s == Skill.MAGIC ? Balance.magicRate(levels.get(s)) : Balance.skillRate(levels.get(s));
    }

    /** Dodaje surowe proby (bez mnoznika serwera) i zwraca liczbe zdobytych poziomow. */
    public int addTries(Skill s, long amount) {
        if (amount <= 0) return 0;
        int lvl = levels.get(s);
        long t = Math.min(Long.MAX_VALUE / 4, tries.get(s) + Math.min(amount, Long.MAX_VALUE / 4));
        int gained = 0;
        while (lvl < MAX_SKILL && t >= triesForNext(vocation, s, lvl)) {
            t -= triesForNext(vocation, s, lvl);
            lvl++;
            gained++;
        }
        if (lvl >= MAX_SKILL) t = 0;
        levels.put(s, lvl);
        tries.put(s, t);
        return gained;
    }

    /**
     * Strata przy smierci jak w Canary: odbiera {@code fraction} (0..1) wszystkich prob zebranych od poziomu startowego
     * (dla magii wydanej many), cofajac poziomy, ale nie ponizej poziomu startowego. Zwraca liczbe straconych poziomow.
     */
    public int lose(Skill s, double fraction) {
        if (fraction <= 0) return 0;
        int floor = offset(s);
        int lvl = levels.get(s);
        if (lvl <= floor && tries.get(s) <= 0) return 0;
        long total = tries.get(s);
        for (int l = floor; l < lvl; l++) total += triesForNext(vocation, s, l);
        long lost = (long) Math.floor(total * Math.min(1.0, fraction));
        long t = tries.get(s);
        int start = lvl;
        while (lost > t) {
            lost -= t;
            if (lvl <= floor) {
                lvl = floor;
                t = 0;
                lost = 0;
                break;
            }
            lvl--;
            t = triesForNext(vocation, s, lvl);
        }
        t = Math.max(0, t - lost);
        levels.put(s, lvl);
        tries.put(s, t);
        return start - lvl;
    }

    /** Strata przy smierci dla wszystkich umiejetnosci i magii (patrz {@link #lose}); zwraca sume straconych poziomow. */
    public int loseAll(double fraction) {
        int n = 0;
        for (Skill s : Skill.values()) n += lose(s, fraction);
        return n;
    }

    /**
     * Ustawia poziom i proby (wczytanie zapisu, stan z serwera). Poziom nigdy nie jest obnizany; proby moga
     * przekraczac wymog nowej krzywej (stary zapis) - postep w oknie jest wtedy obcinany do 99%.
     */
    public void set(Skill s, int level, long progress) {
        if (level < 0 || level > MAX_SKILL || progress < 0) throw new IllegalArgumentException("bad skill " + s);
        levels.put(s, level);
        tries.put(s, progress);
    }
}
