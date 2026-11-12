package pl.fawizja.pixel.balance;

import pl.fawizja.pixel.i18n.Messages;

import pl.fawizja.pixel.vocation.Vocation;

/**
 * Centralne krzywe balansu. Wszystkie obliczenia sa w long/int z kontrola przepelnienia,
 * a wyniki sa ograniczane do sensownych zakresow.
 */
public final class Balance {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 1000;

    /*
     * Mnozniki serwera rozlozone na etapy ("stages" jak na serwerach OTS, np. RubinOT). Wspolne dla gry offline
     * i serwera online (oba korzystaja z modulu shared). Kazdy wiersz: {od poziomu, mnoznik}; obowiazuje ostatni
     * wiersz, ktorego "od" nie przekracza biezacego poziomu. Zmiana tempa gry = zmiana liczb w tych tabelach.
     */
    /** Doswiadczenie za potwory wg poziomu postaci (1-8 x50, 9-50 x80, ... 1401+ x1.2). */
    public static final double[][] EXP_STAGES = {
            {1, 50}, {9, 80}, {51, 60}, {101, 40}, {151, 30}, {201, 15}, {301, 12}, {401, 10}, {501, 7},
            {601, 6}, {701, 5}, {801, 4}, {901, 3}, {1001, 2}, {1201, 1.5}, {1401, 1.2}};
    /** Proby piesci, broni, dystansu, tarczy i wedkarstwa wg biezacego poziomu tej umiejetnosci. */
    public static final double[][] SKILL_STAGES = {{0, 10}, {81, 7}, {101, 4}, {121, 2}};
    /** Mana liczona do poziomu magii wg biezacego poziomu magii. */
    public static final double[][] MAGIC_STAGES = {{0, 10}, {81, 7}, {101, 4}, {121, 3}, {131, 2}};

    /**
     * Wyrownanie tempa umiejetnosci do tempa poziomow (stala kompensacji, przed mnoznikiem etapu): kazda proba z walki
     * z potworem (cios albo strzal, takze chybiony) i kazdy punkt wydanej many liczy sie jak tyle prob. Potwory
     * gina tu w 1,5-3 s (2-4 ciosy na zabicie zamiast kilkunastu w Tibii), a etapy exp sa jak na RubinOT, wiec bez
     * tego czynnika rycerz 100 mial z samego polowania bron ~51 i tarcze ~45, czarodziej magie ~40. Z nim
     * (HuntBalanceSimulationTest#skillsFollowTheLevel) rycerz 100 ma ok. 82/77, paladyn dystans ok. 83, mag magie
     * ok. 72 - jak typowa postac tego poziomu. Manekin: {@link #DUMMY_TRY_FACTOR}.
     */
    public static final int HUNT_TRY_FACTOR = 30;
    /**
     * Proba tarczy za kazdy atak potwora odparty tarcza (wrecz albo fizyczny pocisk): HUNT_TRY_FACTOR razy 3, bo
     * tarcza ma 2x wyzsza stala A niz bron, a potwor zdazy zaatakowac na zabicie ok. 3x rzadziej niz gracz
     * (gracz bije pierwszy i szybciej, w Tibii rycerz trzyma zwykle kilka potworow naraz).
     */
    public static final int BLOCK_TRY_FACTOR = 3 * HUNT_TRY_FACTOR;

    /**
     * Manekin treningowy (GameSession.updateTraining): cios co {@link #DUMMY_STRIKE_MS} (24/min), kazdy to tyle prob
     * umiejetnosci broni z mnoznikiem etapu: 24 x 15 = 360 prob/min przed etapem (polowanie rycerza 50-100: 21-27
     * atakow/min x HUNT_TRY_FACTOR = ok. 630-820). Wg HuntBalanceSimulationTest#dummyTrainsAboutTwoThirdsAsFastAsHunting
     * miecz, jaki rycerz ma z polowania na poziomie 100 (82), manekin daje w ok. 1,6x dluzszym czasie (tempo ok. 62%),
     * do poziomu 50 (miecz 64) ok. 84% - wolniej niz polowanie, bez doswiadczenia i lupu, ale bez ryzyka i przerw.
     * Rycerz: miecz 10 -> 60 przy manekinie ok. 16 min (do 0.222.0: 1 proba co 3 s, 4,8 h).
     */
    public static final int DUMMY_TRY_FACTOR = HUNT_TRY_FACTOR / 2;
    /**
     * Proby tarczy za kazdy cios w manekin (tylko z tarcza albo mnich): 24 x 20 = 480/min przed etapem (polowanie
     * 50-100: 14-21 odpartych atakow/min x BLOCK_TRY_FACTOR = 1200-1900, ale na niskich poziomach duzo mniej);
     * tarcza z polowania na poziomie 100 (77): tempo manekina ok. 70%, na poziomie 50 (59) ok. 90%.
     */
    public static final int DUMMY_BLOCK_TRY_FACTOR = 2 * HUNT_TRY_FACTOR / 3;
    /** Odstep ciosow w manekin (ms). */
    public static final long DUMMY_STRIKE_MS = 2_500;

    /** Mnoznik z tabeli etapow dla poziomu. */
    public static double stage(double[][] stages, int level) {
        double m = stages[0][1];
        for (double[] row : stages) if (level >= row[0]) m = row[1];
        return m;
    }

    /** Mnoznik doswiadczenia za potwory dla poziomu postaci. */
    public static double expRate(int level) { return stage(EXP_STAGES, level); }
    /** Mnoznik prob umiejetnosci (nie magii) dla biezacego poziomu umiejetnosci. */
    public static double skillRate(int skillLevel) { return stage(SKILL_STAGES, skillLevel); }
    /** Mnoznik many do poziomu magii dla biezacego poziomu magii. */
    public static double magicRate(int magicLevel) { return stage(MAGIC_STAGES, magicLevel); }

    /** Mnoznik zapisany do okna postaci: "x80", "x1.5". */
    public static String rateLabel(double rate) {
        return rate == Math.rint(rate) ? "x" + (long) rate : "x" + rate;
    }

    /** Ilosc pomnozona przez mnoznik etapu (zaokraglona, z nasyceniem). */
    public static long applyRate(long amount, double rate) {
        if (amount <= 0) return 0;
        double v = amount * rate;
        return v >= Long.MAX_VALUE / 8.0 ? Long.MAX_VALUE / 8 : Math.max(1, Math.round(v));
    }
    /** Poziom, od ktorego licza sie przyrosty profesji. */
    public static final int VOCATION_LEVEL = 8;
    public static final int HP_AT_VOCATION_LEVEL = 185;
    public static final int MANA_AT_VOCATION_LEVEL = 90;
    public static final int CAPACITY_AT_VOCATION_LEVEL = 470;
    /** Przyrost ponizej poziomu 8 (przed wyborem profesji). */
    public static final int PRE_VOCATION_GAIN = 5;

    public static final int BASE_SPEED = 220;
    /** Gorny limit predkosci ruchu w kafelkach na sekunde (czytelnosc na telefonie). */
    public static final double MAX_TILES_PER_SECOND = 10.0;
    /** Co ile sekund nastepuje tik regeneracji. */
    public static final int REGEN_TICK_SECONDS = 6;

    private static final long[] EXP_TABLE = buildExpTable();

    private Balance() {}

    private static long[] buildExpTable() {
        long[] t = new long[MAX_LEVEL + 2];
        for (int l = 1; l <= MAX_LEVEL + 1; l++) {
            t[l] = computeExperienceForLevel(l);
        }
        return t;
    }

    /**
     * Calkowite doswiadczenie wymagane do osiagniecia poziomu: 50/3 * (L^3 - 6L^2 + 17L - 12).
     * Krzywa szescienna: koszt kolejnego poziomu rosnie kwadratowo, wiec postep spowalnia
     * w kontrolowany sposob. Poziom 8 = 4200 exp, poziom 1000 ~ 1,66e10 (miesci sie w long).
     */
    static long computeExperienceForLevel(int level) {
        if (level <= 1) return 0L;
        long l = level;
        long poly = Math.addExact(Math.subtractExact(Math.multiplyExact(Math.multiplyExact(l, l), l),
                Math.multiplyExact(6L, Math.multiplyExact(l, l))), Math.subtractExact(Math.multiplyExact(17L, l), 12L));
        return Math.multiplyExact(50L, poly) / 3L;
    }

    /** Postep doswiadczenia do nastepnego poziomu w procentach (0..99; 100 na poziomie maksymalnym). */
    public static int percentToNextLevel(long experience, int level) {
        if (level >= MAX_LEVEL) return 100;
        long cur = experienceForLevel(level), next = experienceForLevel(level + 1);
        if (next <= cur) return 100;
        return (int) Math.max(0, Math.min(99, (experience - cur) * 100 / (next - cur)));
    }

    public static long experienceForLevel(int level) {
        if (level <= MIN_LEVEL) return 0L;
        if (level > MAX_LEVEL + 1) throw new IllegalArgumentException("level > " + (MAX_LEVEL + 1));
        return EXP_TABLE[level];
    }

    /** Maksymalne doswiadczenie, jakie moze miec postac (rowne progowi poziomu maksymalnego). */
    public static long maxExperience() {
        return EXP_TABLE[MAX_LEVEL];
    }

    /** Poziom wynikajacy z doswiadczenia (wyszukiwanie binarne w tablicy). */
    public static int levelForExperience(long experience) {
        if (experience <= 0) return MIN_LEVEL;
        int lo = MIN_LEVEL, hi = MAX_LEVEL;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (EXP_TABLE[mid] <= experience) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    private static int statAt(int level, int atVocationLevel, int perLevel) {
        int l = clampLevel(level);
        long v = l >= VOCATION_LEVEL
                ? atVocationLevel + (long) (l - VOCATION_LEVEL) * perLevel
                : atVocationLevel - (long) (VOCATION_LEVEL - l) * PRE_VOCATION_GAIN;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, v));
    }

    public static int maxHealth(Vocation v, int level) {
        return statAt(level, HP_AT_VOCATION_LEVEL, v.hpPerLevel());
    }

    public static int maxMana(Vocation v, int level) {
        return statAt(level, MANA_AT_VOCATION_LEVEL, v.manaPerLevel());
    }

    public static int capacity(Vocation v, int level) {
        return statAt(level, CAPACITY_AT_VOCATION_LEVEL, v.capacityPerLevel());
    }

    /** Regeneracja HP na tik: baza profesji + skladnik rosnacy wolno z poziomem. */
    public static int healthRegenPerTick(Vocation v, int level) {
        return v.baseHpRegen() + clampLevel(level) * v.hpPerLevel() / 400;
    }

    public static int manaRegenPerTick(Vocation v, int level) {
        return v.baseManaRegen() + clampLevel(level) * v.manaPerLevel() / 400;
    }

    /** Szybkosc postaci: rosnie z pierwiastkiem poziomu, wiec na wysokich poziomach nie eksploduje. */
    public static int speed(int level) {
        int l = clampLevel(level);
        return BASE_SPEED + (int) Math.round(25.0 * Math.sqrt(Math.max(0, l - 7)));
    }

    /** Do tej szybkosci 80 punktow = 1 pole/s; powyzej przyrost jest o polowe wolniejszy. */
    public static final int FAST_SPEED = 400;

    /**
     * Pola na sekunde. Powyzej FAST_SPEED krzywa sie splaszcza, zeby postac 300+ (ok. 6.5 pola/s)
     * miala jeszcze miejsce na przyspieszenie z czarow i wierzchowca przed limitem.
     */
    public static double tilesPerSecond(int speed) {
        double t = speed <= FAST_SPEED ? speed / 80.0 : FAST_SPEED / 80.0 + (speed - FAST_SPEED) / 160.0;
        return Math.min(MAX_TILES_PER_SECOND, Math.max(1.0, t));
    }

    /** Czas jednego kroku w ms; ruch po skosie trwa dluzej. */
    public static long stepDurationMs(int speed, boolean diagonal) {
        double ms = 1000.0 / tilesPerSecond(speed);
        return Math.round(diagonal ? ms * Math.sqrt(2.0) : ms);
    }

    /**
     * Doswiadczenie za zabicie potwora. Postac o wiele silniejsza od potwora dostaje
     * coraz mniej (min. 10%), zeby nie oplacalo sie bic banalnych przeciwnikow.
     */
    public static long experienceReward(long monsterExp, int monsterLevel, int playerLevel) {
        if (monsterExp <= 0) return 0;
        long threshold = 4L * monsterLevel + 20;
        if (playerLevel <= threshold) return monsterExp;
        double factor = Math.max(0.1, (double) threshold / playerLevel);
        return Math.max(1, Math.round(monsterExp * factor));
    }

    /**
     * Ulamek (0..1) doswiadczenia, umiejetnosci i poziomu magii tracony przy smierci - wzor z Canary
     * ({@code Player::getLostPercent}). Strata bazowa od poziomu 24: {@code (L+50)*50*(L^2-5L+8)/doswiadczenie} procent,
     * gdzie L to poziom z ulamkiem; ponizej 24 stale 10%. Kazde z pieciu zwyklych blogoslawienstw zmniejsza ja o 8%
     * (ponizej poziomu 24 piec daje 50%), promocja o dalsze 30%.
     */
    public static double deathLossFraction(int level, long experience, int blessings, boolean promoted) {
        int b = Math.max(0, Math.min(pl.fawizja.pixel.character.Blessing.REGULAR_COUNT, blessings));
        double reduction = b * pl.fawizja.pixel.character.Blessing.REDUCTION_EACH_PERCENT / 100.0;
        double base;
        if (level >= 24 && experience > 0) {
            double cur = experienceForLevel(level), next = experienceForLevel(Math.min(level + 1, MAX_LEVEL + 1));
            double l = level + (next > cur ? Math.max(0, Math.min(99, Math.floor((experience - cur) * 100.0 / (next - cur)))) / 100.0 : 0);
            base = ((l + 50) * 50 * (l * l - 5 * l + 8)) / experience;
        } else {
            reduction = reduction >= 0.40 ? 0.50 : reduction;
            base = 10;
        }
        if (promoted) reduction += 0.30;
        return base * (1 - Math.min(1.0, reduction)) / 100.0;
    }

    /** Doswiadczenie po smierci (bez promocji), nigdy ponizej progu poziomu minLevel. */
    public static long experienceAfterDeath(long experience, int minLevel, int blessings, int level) {
        return experienceAfterDeath(experience, minLevel, deathLossFraction(level, experience, blessings, false));
    }

    /** Doswiadczenie po smierci przy gotowym ulamku straty (zaokraglonym w gore jak w Canary). */
    public static long experienceAfterDeath(long experience, int minLevel, double lossFraction) {
        long lost = (long) Math.ceil(experience * lossFraction);
        long floor = experienceForLevel(minLevel);
        return Math.max(Math.min(experience, floor), experience - lost);
    }

    /** Utrata doswiadczenia przy smierci bez blogoslawienstw, nie ponizej progu poziomu minLevel. */
    public static long experienceAfterDeath(long experience, int minLevel) {
        return experienceAfterDeath(experience, minLevel, 0, Math.max(1, levelForExperience(experience)));
    }

    /** Opis straty doswiadczenia przy smierci, np. "3,1% (bez blogoslawienstw 4,5%)". */
    public static String deathLossLabel(int blessings, int level, long experience, boolean promoted) {
        String pct = Messages.decimal(100 * deathLossFraction(level, experience, blessings, promoted), 1);
        if (blessings <= 0 && !promoted) return Messages.get("bless.loss_zero", pct);
        String base = Messages.decimal(100 * deathLossFraction(level, experience, 0, false), 1);
        return Messages.get("bless.loss_fmt", pct, base);
    }

    /** Cena zwyklego blogoslawienstwa u mnicha (patrz {@link pl.fawizja.pixel.character.Blessing#price}). */
    public static long blessingPrice(int level) {
        return pl.fawizja.pixel.character.Blessing.WISDOM.price(level);
    }

    /** Dodawanie doswiadczenia z nasyceniem: nigdy nie przekracza maksimum ani nie przepelnia long. */
    public static long addExperience(long current, long gained) {
        if (gained <= 0) return current;
        long max = maxExperience();
        if (current >= max) return max;
        long sum = current + gained;
        if (sum < 0 || sum > max) return max;
        return sum;
    }
}
