package pl.fawizja.pixel.wheel;

import java.util.Arrays;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Kolo Przeznaczenia postaci: punkty w 36 polach, zestawy (presety), czasy Daru Zycia i awatara.
 * <p>Zasady jak w Tibii (TibiaWiki Wheel_of_Destiny, Canary player_wheel.cpp):
 * <ul>
 *   <li>punkty promocji: 1 za kazdy poziom od 51 ({@code poziom - 50}), tylko z promocja i kontem premium,</li>
 *   <li>pole 50 zawsze dostepne, kazde dalsze - gdy pelne jest jedno z jego pol poprzedzajacych
 *       ({@link WheelSlice#prereqs()}) i postac ma dosc punktow ogolem ({@link WheelSlice.Pos#threshold}),</li>
 *   <li>punkty mozna przestawiac za darmo, ale tylko w swiatyni (sprawdza {@code GameSession});
 *       zdjecie punktow, od ktorych zalezy dalsze pole, jest odrzucane - najpierw trzeba oproznic dalsze pole,</li>
 *   <li>po utracie poziomu (smierc) nadmiar punktow schodzi od zewnatrz kola ({@link #trim(int)}).</li>
 * </ul>
 * Czasy Daru Zycia i odnowienia awatara plyna tylko w grze (jak storage w Canary) i sa zapisywane; pozostale stany
 * (pole Boskiego wzmocnienia, Sanktuarium, Mistrzostwo skupienia) sa ulotne.
 */
public final class DestinyWheel {
    public static final int MIN_LEVEL = 51;
    /** Liczba zestawow (presetow) do zapamietania ulozenia. W Tibii wiecej - liczba niezweryfikowana. */
    public static final int PRESETS = 3;
    public static final long AVATAR_MS = 15_000;
    /** Awatar: 2 h odnowienia, kazdy stopien od 2 skraca o 30 min (Canary registerPlayerBonusData). */
    public static final long AVATAR_COOLDOWN_MS = 2 * 3_600_000L;
    public static final long AVATAR_COOLDOWN_CUT_MS = 30 * 60_000L;

    public enum Result {
        OK(""),
        NOT_ELIGIBLE("Koło Przeznaczenia wymaga promocji, konta premium i poziomu 51."),
        NOT_IN_TEMPLE("Punkty koła przestawisz tylko w świątyni (przy kapłanie)."),
        NO_POINTS("Nie masz wolnych punktów promocji."),
        FULL("To pole jest już pełne."),
        LOCKED("To pole jest zamknięte: najpierw zapełnij sąsiednie pole bliżej środka."),
        THRESHOLD("Za mało punktów promocji, żeby sięgnąć tak daleko."),
        DEPENDENT("Najpierw opróżnij dalsze pola, które zależą od tego pola."),
        EMPTY("To pole jest puste."),
        NO_PRESET("Ten zestaw jest pusty."),
        PRESET_TOO_BIG("Zestaw wymaga więcej punktów, niż masz.");

        public final String message;

        Result(String m) { message = m; }
    }

    private final int[] points = new int[WheelSlice.values().length];
    private final int[][] presets = new int[PRESETS][];
    private long giftCooldownMs;
    private long avatarMs;
    private long avatarCooldownMs;
    // ulotne
    private long empowerMs;
    private Position empowerAt;
    private int empowerPercent;
    private long focusMs;
    private long sanctuaryMs;
    private int sanctuaryPercent;
    private long battleHealCdMs;
    private int adjacentMonsters;
    private boolean giftTriggered;
    private SpellAugment castAugment = SpellAugment.NONE;
    private int avatarStage;
    // pamiec podreczna premii
    private int version;
    private int cacheVersion = -1;
    private Vocation cacheVocation;
    private boolean cacheEligible;
    private WheelBonus cache = WheelBonus.NONE;

    /** Punkty promocji: poziom - 50 od poziomu 51, z promocja i premium (jak w Tibii). */
    public static int available(int level, boolean promoted, boolean premium) {
        return promoted && premium && level >= MIN_LEVEL ? level - (MIN_LEVEL - 1) : 0;
    }

    public static boolean eligible(int level, boolean promoted, boolean premium) { return available(level, promoted, premium) > 0; }

    public int points(WheelSlice s) { return points[s.ordinal()]; }

    public int spent() {
        int t = 0;
        for (int p : points) t += p;
        return t;
    }

    public int domainPoints(WheelDomain d) {
        int t = 0;
        for (WheelSlice s : WheelSlice.of(d)) t += points[s.ordinal()];
        return t;
    }

    public int unused(int available) { return Math.max(0, available - spent()); }

    public boolean isEmpty() { return spent() == 0; }

    /** Premie dla profesji; eligible = false (brak promocji/premium/poziomu) daje {@link WheelBonus#NONE}. */
    public WheelBonus bonus(Vocation v, boolean eligible) {
        if (cacheVersion != version || cacheVocation != v || cacheEligible != eligible) {
            cache = eligible ? new WheelBonus(v, points) : WheelBonus.NONE;
            cacheVersion = version;
            cacheVocation = v;
            cacheEligible = eligible;
        }
        return cache;
    }

    // ------------------------------------------------------------------ reguly pol

    /** Czy pole mozna teraz zasilic (otwarte i w zasiegu punktow), niezaleznie od wolnych punktow. */
    public boolean open(WheelSlice s, int available) {
        return open(points, s, available);
    }

    private static boolean open(int[] pts, WheelSlice s, int available) {
        if (available < s.pos.threshold) return false;
        if (s.pos == WheelSlice.Pos.S50) return true;
        boolean[] legit = legit(pts);
        for (WheelSlice p : s.prereqs()) if (legit[p.ordinal()] && pts[p.ordinal()] >= p.capacity()) return true;
        return false;
    }

    /** Pola polaczone ze srodkiem lancuchem pelnych pol (punkt staly). */
    private static boolean[] legit(int[] pts) {
        boolean[] ok = new boolean[pts.length];
        boolean changed = true;
        while (changed) {
            changed = false;
            for (WheelSlice s : WheelSlice.values()) {
                int i = s.ordinal();
                if (ok[i] || pts[i] <= 0) continue;
                boolean reach = s.pos == WheelSlice.Pos.S50;
                for (WheelSlice p : s.prereqs()) {
                    if (reach) break;
                    reach = ok[p.ordinal()] && pts[p.ordinal()] >= p.capacity();
                }
                if (reach) {
                    ok[i] = true;
                    changed = true;
                }
            }
        }
        return ok;
    }

    /** Czy ulozenie jest poprawne: pojemnosci, suma, progi i polaczenie kazdego niepustego pola ze srodkiem. */
    public static boolean valid(int[] pts, int available) {
        if (pts.length != WheelSlice.values().length) return false;
        int sum = 0;
        for (WheelSlice s : WheelSlice.values()) {
            int p = pts[s.ordinal()];
            if (p < 0 || p > s.capacity()) return false;
            if (p > 0 && available < s.pos.threshold) return false;
            sum += p;
        }
        if (sum > available) return false;
        boolean[] ok = legit(pts);
        for (int i = 0; i < pts.length; i++) if (pts[i] > 0 && !ok[i]) return false;
        return true;
    }

    public boolean valid(int available) { return valid(points, available); }

    // ------------------------------------------------------------------ zmiany (GameSession sprawdza swiatynie)

    /** Dodaje do n punktow (najwyzej do pelna i do wolnych punktow). */
    public Result add(WheelSlice s, int n, int available) {
        if (available <= 0) return Result.NOT_ELIGIBLE;
        int free = unused(available);
        if (free <= 0) return Result.NO_POINTS;
        int cur = points[s.ordinal()];
        if (cur >= s.capacity()) return Result.FULL;
        if (available < s.pos.threshold) return Result.THRESHOLD;
        if (cur == 0 && !open(s, available)) return Result.LOCKED;
        points[s.ordinal()] = cur + Math.min(Math.max(1, n), Math.min(free, s.capacity() - cur));
        version++;
        return Result.OK;
    }

    /** Zdejmuje do n punktow; odmawia, gdy od pola zalezy inne niepuste pole. */
    public Result remove(WheelSlice s, int n, int available) {
        int cur = points[s.ordinal()];
        if (cur <= 0) return Result.EMPTY;
        int[] copy = points.clone();
        copy[s.ordinal()] = Math.max(0, cur - Math.max(1, n));
        if (!valid(copy, Math.max(available, spentOf(copy)))) return Result.DEPENDENT;
        System.arraycopy(copy, 0, points, 0, points.length);
        version++;
        return Result.OK;
    }

    /** Zeruje kolo (wszystkie punkty wracaja), jak przycisk resetu w Tibii. */
    public Result reset() {
        Arrays.fill(points, 0);
        version++;
        return Result.OK;
    }

    /** Zapamietuje biezace ulozenie w zestawie i (0..PRESETS-1). */
    public Result savePreset(int i) {
        presets[i] = points.clone();
        return Result.OK;
    }

    public boolean hasPreset(int i) { return presets[i] != null; }

    /** Punkty zestawu i (0 = pusty). */
    public int presetPoints(int i) { return presets[i] == null ? 0 : spentOf(presets[i]); }

    /** Wczytuje zestaw i, gdy pasuje do obecnych punktow. */
    public Result loadPreset(int i, int available) {
        if (presets[i] == null) return Result.NO_PRESET;
        if (!valid(presets[i], available)) return Result.PRESET_TOO_BIG;
        System.arraycopy(presets[i], 0, points, 0, points.length);
        version++;
        return Result.OK;
    }

    public void clearPreset(int i) { presets[i] = null; }

    private static int spentOf(int[] pts) {
        int t = 0;
        for (int p : pts) t += p;
        return t;
    }

    /**
     * Po utracie poziomu: zdejmuje nadmiar punktow od zewnatrz kola (pola 200, 150, ... jak kolejnosc priorytetu
     * Canary getSlotPrioritaryOrder, tylko odwrotnie), potem usuwa pola odciete od srodka. Zwraca, czy cos zmieniono.
     */
    public boolean trim(int available) {
        if (valid(available)) return false;
        WheelSlice.Pos[] order = WheelSlice.Pos.values();
        int excess = spent() - Math.max(0, available);
        for (int pi = order.length - 1; pi >= 0 && excess > 0; pi--) {
            for (int di = WheelDomain.values().length - 1; di >= 0 && excess > 0; di--) {
                WheelSlice s = WheelSlice.of(WheelDomain.values()[di], order[pi]);
                int take = Math.min(excess, points[s.ordinal()]);
                points[s.ordinal()] -= take;
                excess -= take;
            }
        }
        for (WheelSlice s : WheelSlice.values()) if (available < s.pos.threshold) points[s.ordinal()] = 0;
        boolean[] ok = legit(points);
        for (int i = 0; i < points.length; i++) if (!ok[i]) points[i] = 0;
        version++;
        return true;
    }

    // ------------------------------------------------------------------ czasy (tylko w grze)

    /** Krok czasu gry: Dar Zycia, awatar i stany ulotne. */
    public void tick(long dtMs) {
        if (dtMs <= 0) return;
        giftCooldownMs = Math.max(0, giftCooldownMs - dtMs);
        avatarMs = Math.max(0, avatarMs - dtMs);
        avatarCooldownMs = Math.max(0, avatarCooldownMs - dtMs);
        empowerMs = Math.max(0, empowerMs - dtMs);
        if (empowerMs == 0) empowerAt = null;
        focusMs = Math.max(0, focusMs - dtMs);
        sanctuaryMs = Math.max(0, sanctuaryMs - dtMs);
        battleHealCdMs = Math.max(0, battleHealCdMs - dtMs);
    }

    public long giftCooldownMs() { return giftCooldownMs; }
    public void setGiftCooldown(long ms) { giftCooldownMs = Math.max(0, ms); }

    /** Zgloszenie uzycia Daru Zycia (odbiera GameSession: dziennik, efekt, -60 s odnowien). */
    public void markGift() { giftTriggered = true; }

    public boolean consumeGift() {
        boolean g = giftTriggered;
        giftTriggered = false;
        return g;
    }

    public boolean avatarActive() { return avatarMs > 0; }
    public long avatarMs() { return avatarMs; }
    public long avatarCooldownMs() { return avatarCooldownMs; }
    /** Stopien awatara z chwili rzucenia (premie trwaja wg niego). */
    public int avatarStage() { return avatarMs > 0 ? avatarStage : 0; }

    public void startAvatar(int stage, long cooldownMs) {
        avatarStage = Math.max(1, Math.min(3, stage));
        avatarMs = AVATAR_MS;
        avatarCooldownMs = cooldownMs;
    }

    public void startEmpowerment(Position at, int percent, long ms) {
        empowerAt = at;
        empowerPercent = percent;
        empowerMs = ms;
    }

    /** Premia Boskiego wzmocnienia, gdy postac stoi na polu 3x3 (procent, 0 = brak). */
    public int empowerment(Position me) {
        if (empowerMs <= 0 || empowerAt == null || me == null || me.z() != empowerAt.z()) return 0;
        return Math.abs(me.x() - empowerAt.x()) <= 1 && Math.abs(me.y() - empowerAt.y()) <= 1 ? empowerPercent : 0;
    }

    public Position empowerAt() { return empowerMs > 0 ? empowerAt : null; }

    public void startFocus(long ms) { focusMs = ms; }
    /** Zuzywa premie Mistrzostwa skupienia (true = byla aktywna). */
    public boolean useFocus() {
        boolean f = focusMs > 0;
        focusMs = 0;
        return f;
    }
    public boolean focusActive() { return focusMs > 0; }

    public void startSanctuary(int percent, long ms) {
        if (sanctuaryMs > 0) return; // Canary: nie odnawia, gdy trwa
        sanctuaryPercent = percent;
        sanctuaryMs = ms;
    }
    public int sanctuaryPercent() { return sanctuaryMs > 0 ? sanctuaryPercent : 0; }

    /** Mistrzostwo run: 25% szansy na rzut runa (staly generator - wynik powtarzalny w testach). */
    private final java.util.Random runic = new java.util.Random(25);

    public boolean rollRunic() { return runic.nextInt(100) < 25; }

    public boolean battleHealReady() { return battleHealCdMs <= 0; }
    public void startBattleHealCooldown(long ms) { battleHealCdMs = ms; }

    /** Potwory na sasiednich polach (ustawia GameSession co krok) - Instynkt bitewny i Taktyka pozycyjna. */
    public int adjacentMonsters() { return adjacentMonsters; }
    public void setAdjacentMonsters(int n) { adjacentMonsters = Math.max(0, n); }

    /** Premia czaru rzucanego w tej chwili (SpellSystem ustawia na czas rzutu; krytyk i kradziez ja czytaja). */
    public SpellAugment castAugment() { return castAugment; }
    public void setCastAugment(SpellAugment a) { castAugment = a == null ? SpellAugment.NONE : a; }

    // ------------------------------------------------------------------ zapis

    /**
     * Tekst do zapisu: "p=G50:50,GT75:75;s0=...;g=ms;a=ms;ac=ms;as=1". Puste kolo bez zestawow i czasow = "".
     */
    public String encode() {
        StringBuilder b = new StringBuilder();
        String p = pointsText(points);
        if (spent() > 0) b.append("p=").append(p);
        for (int i = 0; i < PRESETS; i++) {
            if (presets[i] == null) continue;
            if (b.length() > 0) b.append(';');
            b.append('s').append(i).append('=').append(pointsText(presets[i]));
        }
        if (giftCooldownMs > 0) sep(b).append("g=").append(giftCooldownMs);
        if (avatarMs > 0) sep(b).append("a=").append(avatarMs).append(";as=").append(avatarStage);
        if (avatarCooldownMs > 0) sep(b).append("ac=").append(avatarCooldownMs);
        return b.toString();
    }

    private static StringBuilder sep(StringBuilder b) {
        if (b.length() > 0) b.append(';');
        return b;
    }

    private static String pointsText(int[] pts) {
        StringBuilder b = new StringBuilder();
        for (WheelSlice s : WheelSlice.values()) {
            int v = pts[s.ordinal()];
            if (v <= 0) continue;
            if (b.length() > 0) b.append(',');
            b.append(s.code()).append(':').append(v);
        }
        return b.length() == 0 ? "-" : b.toString();
    }

    /** Odczyt; null albo "" = puste kolo (zapis sprzed Kola Przeznaczenia). Bledne wpisy sa pomijane. */
    public void decode(String text) {
        Arrays.fill(points, 0);
        Arrays.fill(presets, null);
        giftCooldownMs = avatarMs = avatarCooldownMs = 0;
        avatarStage = 0;
        version++;
        if (text == null || text.isEmpty()) return;
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq);
            String v = part.substring(eq + 1);
            try {
                if (k.equals("p")) parsePoints(v, points);
                else if (k.length() == 2 && k.charAt(0) == 's') {
                    int i = k.charAt(1) - '0';
                    if (i >= 0 && i < PRESETS) {
                        presets[i] = new int[points.length];
                        parsePoints(v, presets[i]);
                    }
                } else if (k.equals("g")) giftCooldownMs = Math.max(0, Long.parseLong(v));
                else if (k.equals("a")) avatarMs = Math.max(0, Math.min(AVATAR_MS, Long.parseLong(v)));
                else if (k.equals("as")) avatarStage = Math.max(0, Math.min(3, Integer.parseInt(v)));
                else if (k.equals("ac")) avatarCooldownMs = Math.max(0, Math.min(AVATAR_COOLDOWN_MS, Long.parseLong(v)));
            } catch (NumberFormatException e) {
                // pomijamy uszkodzony wpis
            }
        }
    }

    private static void parsePoints(String v, int[] into) {
        if (v.equals("-")) return;
        for (String e : v.split(",")) {
            int c = e.indexOf(':');
            if (c <= 0) continue;
            WheelSlice s = WheelSlice.ofCode(e.substring(0, c));
            if (s == null) continue;
            into[s.ordinal()] = Math.max(0, Math.min(s.capacity(), Integer.parseInt(e.substring(c + 1))));
        }
    }

    /** Klient online: stan z serwera (SELF). */
    public void copyFrom(DestinyWheel o) {
        System.arraycopy(o.points, 0, points, 0, points.length);
        for (int i = 0; i < PRESETS; i++) presets[i] = o.presets[i] == null ? null : o.presets[i].clone();
        giftCooldownMs = o.giftCooldownMs;
        avatarMs = o.avatarMs;
        avatarStage = o.avatarStage;
        avatarCooldownMs = o.avatarCooldownMs;
        version++;
    }

    /** Ustawia punkty bez sprawdzania (testy, podglad). */
    public void setPointsUnchecked(WheelSlice s, int v) {
        points[s.ordinal()] = Math.max(0, Math.min(s.capacity(), v));
        version++;
    }
}
