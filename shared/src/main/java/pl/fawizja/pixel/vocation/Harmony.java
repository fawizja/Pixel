package pl.fawizja.pixel.vocation;

/**
 * Harmonia mnicha jak w Tibii (liczby z serwera Canary, src/creatures/players/player.cpp):
 * <ul>
 *   <li>0..{@link #MAX} punktow; czar budujacy (builder) daje +1, czar wydajacy (spender) zuzywa wszystkie
 *       i bije mocniej: obrazenia x (1 + B * 2^(h-1)), B = 8% (Canary {@code getHarmonyBonus});</li>
 *   <li>cnota (virtue) - jedna aktywna naraz: Harmonii (B +4 pkt, w spokoju +8 pkt; kazdy spender od razu oddaje 1 punkt),
 *       Sprawiedliwosci (walka piesciami +15%, w spokoju +30%), Wytrwalosci (leczenie czarami mnicha x1,35, w spokoju x1,70);</li>
 *   <li>spokoj (Serene): mnich jest spokojny, chyba ze widzi czlonka druzyny i jednoczesnie na 8 polach wokol stoi
 *       co najmniej 6 stworzen ({@code Player::updateSerenityState}); Skupienie spokoju wymusza spokoj na 7 s.</li>
 * </ul>
 * Smierc zeruje harmonie, wylogowanie nie (Canary zapisuje harmonie i cnote w kv gracza). Stan ma kazda postac,
 * ale zmieniaja go tylko czary mnicha.
 */
public final class Harmony {

    public static final int MAX = 5;
    /** Bazowa premia spendera w punktach procentowych (Canary: harmonyBaseBonus = 8). */
    public static final double BASE_BONUS = 8;
    /** Spokoj traci mnich z druzyna w zasiegu wzroku, gdy wokol stoi tyle stworzen (Canary: ++count >= 6). */
    public static final int CROWD = 6;
    /** Skupienie spokoju: spokoj na 7 s (Canary focus_serenity.lua: setSerene(true, 7 * 1000)). */
    public static final long FOCUS_SERENE_MS = 7_000;
    /** Spender skraca odnowienie czarow budujacych o 2 s za kazdy wydany punkt (Canary Spell::postCastSpell). */
    public static final long COOLDOWN_CUT_PER_POINT_MS = 2_000;

    /** Cnoty mnicha (Canary Virtue_t) - klucz czaru, nazwa i opis po polsku. */
    public enum Virtue {
        NONE(null, "brak", ""),
        HARMONY("virtue_of_harmony", "Cnota harmonii", "premia czarów wydających +50% (w spokoju +100%), każdy oddaje 1 punkt harmonii"),
        JUSTICE("virtue_of_justice", "Cnota sprawiedliwości", "walka pięściami +15% (w spokoju +30%)"),
        SUSTAIN("virtue_of_sustain", "Cnota wytrwałości", "leczenie czarami mnicha +35% (w spokoju +70%)");

        public final String spellKey;
        public final String label;
        public final String effect;

        Virtue(String spellKey, String label, String effect) {
            this.spellKey = spellKey;
            this.label = label;
            this.effect = effect;
        }

        /** Cnota rzucana danym czarem albo null. */
        public static Virtue ofSpell(String key) {
            for (Virtue v : values()) if (v.spellKey != null && v.spellKey.equals(key)) return v;
            return null;
        }

        static Virtue parse(String s) {
            for (Virtue v : values()) if (v.name().equals(s)) return v;
            return NONE;
        }
    }

    private int points;
    private Virtue virtue = Virtue.NONE;
    private boolean serene = true;
    /** Pozostaly czas wymuszonego spokoju (Skupienie spokoju). */
    private long forcedSereneMs;

    public int points() { return points; }
    public boolean full() { return points >= MAX; }
    public Virtue virtue() { return virtue; }
    public boolean serene() { return serene || forcedSereneMs > 0; }
    public long forcedSereneMs() { return forcedSereneMs; }

    public void setVirtue(Virtue v) { virtue = v == null ? Virtue.NONE : v; }

    /** Dodaje punkty (najwyzej do {@link #MAX}); zwraca, ile faktycznie przybylo (od tego zalezy leczenie). */
    public int build(int charges) {
        int add = Math.max(0, Math.min(charges, MAX - points));
        points += add;
        return add;
    }

    /** Zuzywa wszystkie punkty; zwraca ich liczbe. Odbudowe z cnoty harmonii robi wywolujacy (z leczeniem, jak Canary). */
    public int spend() {
        int h = points;
        points = 0;
        return h;
    }

    public void empty() { points = 0; }

    /** Mnoznik obrazen/leczenia czaru wydajacego przy obecnych punktach. */
    public double bonus() { return bonus(points, virtue, serene()); }

    /** Canary getHarmonyBonus: 1 + B * 2^(h-1) / 100, B = 8 (+4 cnota harmonii, +8 w spokoju); h = 0 -> 1. */
    public static double bonus(int points, Virtue virtue, boolean serene) {
        if (points <= 0) return 1;
        double b = BASE_BONUS;
        if (virtue == Virtue.HARMONY) b += serene ? 8 : 4;
        return 1 + b * Math.pow(2, Math.min(MAX, points) - 1) / 100.0;
    }

    /** Cnota sprawiedliwosci: mnoznik umiejetnosci walki piesciami (Canary SKILL_FISTPERCENT 115 / 130 w spokoju). */
    public double fistFactor() {
        if (virtue != Virtue.JUSTICE) return 1;
        return serene() ? 1.30 : 1.15;
    }

    /** Cnota wytrwalosci: mnoznik leczenia czarami mnicha, bez leczenia z harmonii (Canary Combat: 1,35 / 1,70). */
    public double healFactor() {
        if (virtue != Virtue.SUSTAIN) return 1;
        return serene() ? 1.70 : 1.35;
    }

    /** Wymusza spokoj na podany czas (Skupienie spokoju). */
    public void forceSerene(long ms) { forcedSereneMs = Math.max(forcedSereneMs, ms); }

    /**
     * Stan spokoju co krok gry: bez wymuszenia spokoj traci tylko mnich, ktory widzi czlonka druzyny i ma wokol
     * co najmniej {@link #CROWD} stworzen. Zwraca true, gdy stan sie zmienil.
     */
    public boolean update(long dtMs, boolean partyMemberVisible, int creaturesAround) {
        boolean before = serene();
        if (forcedSereneMs > 0) forcedSereneMs = Math.max(0, forcedSereneMs - Math.max(0, dtMs));
        serene = !(partyMemberVisible && creaturesAround >= CROWD);
        return before != serene();
    }

    /** Zapis "punkty,CNOTA,spokoj,wymuszenieMs" (pole "monk" zapisu postaci i komunikatu SELF). */
    public String encode() {
        return points + "," + virtue.name() + "," + (serene ? 1 : 0) + "," + forcedSereneMs;
    }

    public boolean isDefault() { return points == 0 && virtue == Virtue.NONE && serene && forcedSereneMs == 0; }

    /** Odczyt pola zapisu; brak albo bledne pole = stan poczatkowy. */
    public void restore(String s) {
        points = 0;
        virtue = Virtue.NONE;
        serene = true;
        forcedSereneMs = 0;
        if (s == null || s.isBlank()) return;
        String[] f = s.split(",");
        try {
            points = Math.max(0, Math.min(MAX, Integer.parseInt(f[0].trim())));
            if (f.length > 1) virtue = Virtue.parse(f[1].trim());
            if (f.length > 2) serene = !"0".equals(f[2].trim());
            if (f.length > 3) forcedSereneMs = Math.max(0, Long.parseLong(f[3].trim()));
        } catch (NumberFormatException e) {
            points = 0;
        }
    }

    public void copyFrom(Harmony o) {
        points = o.points;
        virtue = o.virtue;
        serene = o.serene;
        forcedSereneMs = o.forcedSereneMs;
    }

    /**
     * Leczenie z harmonii (Canary Combat::harmonyHeal) przy kazdym zbudowaniu i wydaniu: plaska premia poziomu
     * x2..x2,3, +5% za kazdy punkt; co najmniej 10..25.
     */
    public static int healMin(int level, int charges) {
        return Math.max(10, (int) Math.ceil(flatDamageHealing(level) * 2 * (1 + 0.05 * charges)));
    }

    public static int healMax(int level, int charges) {
        return Math.max(25, (int) Math.ceil(flatDamageHealing(level) * 2.3 * (1 + 0.05 * charges)));
    }

    /** Poziom, do ktorego czary ataku mnicha dostaja premie nowicjusza (dodatek Pixela, nie ma go w Canary). */
    public static final int NOVICE_LEVEL = 40;
    /** Premia nowicjusza: tyle obrazen za kazdy poziom ponizej {@link #NOVICE_LEVEL}. */
    public static final double NOVICE_PER_LEVEL = 1.0;

    /**
     * Premia nowicjusza do czarow ataku mnicha: (40 - poziom) x 1, od poziomu 40 zero. Wzor Canary
     * (moc x piesci x atak) przy piesciach 10 daje na 8 poziomie kilka punktow, a rycerz Pixela ma juz od 8 poziomu
     * brutalne uderzenie (w Tibii dopiero od 16) - bez premii mnich polowal na pierwszych potworach kilka razy wolniej.
     */
    public static double noviceBonus(int level) {
        return Math.max(0, NOVICE_LEVEL - level) * NOVICE_PER_LEVEL;
    }

    /**
     * Plaska premia obrazen i leczenia za poziom (Canary Player::calculateFlatDamageHealing): poziom / 5 do 500,
     * dalej coraz wolniej (progi 500, 1100, 1800, ... z dzielnikiem 6, 7, ...).
     */
    public static int flatDamageHealing(int level) {
        double previous = 0;
        long baseline = 0;
        double factor = 1.0 / 5.0;
        long threshold = 500, step = 600;
        int tier = 1;
        while (level >= threshold) {
            baseline = threshold;
            factor = 1.0 / (5.0 + tier);
            previous += threshold * (1.0 / (5.0 + tier - 1));
            tier++;
            threshold += step;
            step += 100;
        }
        return (int) Math.min(65535, Math.ceil(previous + (level - baseline) * factor));
    }
}
