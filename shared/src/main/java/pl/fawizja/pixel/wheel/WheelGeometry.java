package pl.fawizja.pixel.wheel;

import java.util.List;

/**
 * Geometria rysunku Kola Przeznaczenia jak w oknie Tibii (czysta matematyka, bez libGDX; testy: WheelGeometryTest).
 * Kolo o promieniu 1: piasta (wolne punkty) do {@link #HUB}, potem 5 pierscieni od srodka: pola 50 (1 na cwiartke),
 * 75 (2), 100 (3), 150 (2) i zewnetrzne 200 (1). Cwiartki: czerwona prawa gora (0-90 stopni), zielona lewa gora
 * (90-180), niebieska lewy dol (180-270), fioletowa prawy dol (270-360); katy przeciwnie do wskazowek zegara od
 * prawej, os y w gore. Pola "gorne" (T) leza przy granicy, przez ktora siegaja do sasiedniej cwiartki
 * ({@link WheelSlice#prereqs()}): zielone i czerwone gorne przy osi pionowej u gory, niebieskie gorne przy zielonej,
 * fioletowe gorne przy czerwonej - kazde pole dotyka na rysunku wszystkich pol, ktore je otwieraja.
 */
public final class WheelGeometry {
    /** Promien piasty (srodek z wolnymi punktami) jako ulamek promienia kola. */
    public static final float HUB = 0.22f;
    /** Zewnetrzne krawedzie pierscieni 0..4 (ulamek promienia). */
    public static final float[] RING_OUTER = {0.38f, 0.535f, 0.69f, 0.845f, 1f};

    private static final WheelSlice.Pos[][] RING_SLOTS = {
        {WheelSlice.Pos.S50},
        {WheelSlice.Pos.T75, WheelSlice.Pos.B75},
        {WheelSlice.Pos.T100, WheelSlice.Pos.M100, WheelSlice.Pos.B100},
        {WheelSlice.Pos.T150, WheelSlice.Pos.B150},
        {WheelSlice.Pos.S200},
    };

    private WheelGeometry() {}

    /** Pierscien pola od srodka (0: pole 50 ... 4: pole 200). */
    public static int ring(WheelSlice.Pos p) {
        for (int r = 0; r < RING_SLOTS.length; r++) for (WheelSlice.Pos q : RING_SLOTS[r]) if (q == p) return r;
        throw new IllegalArgumentException(String.valueOf(p));
    }

    public static int ring(WheelSlice s) { return ring(s.pos); }

    /** Ile pol jednej cwiartki lezy w pierscieniu. */
    public static int slotsInRing(int ring) { return RING_SLOTS[ring].length; }

    public static float innerRadius(int ring) { return ring == 0 ? HUB : RING_OUTER[ring - 1]; }

    public static float outerRadius(int ring) { return RING_OUTER[ring]; }

    /** Kat poczatku cwiartki (stopnie). */
    public static float quadrantStart(WheelDomain d) {
        switch (d) {
            case RED: return 0;
            case GREEN: return 90;
            case BLUE: return 180;
            default: return 270;
        }
    }

    /** Cwiartka pod katem (stopnie, dowolny zakres). */
    public static WheelDomain quadrantAt(double deg) {
        int q = (int) Math.floor(norm(deg) / 90.0) % 4;
        switch (q) {
            case 0: return WheelDomain.RED;
            case 1: return WheelDomain.GREEN;
            case 2: return WheelDomain.BLUE;
            default: return WheelDomain.PURPLE;
        }
    }

    /** Pola "gorne" leza przy poczatku cwiartki (zielona, niebieska) albo przy jej koncu (czerwona, fioletowa). */
    private static boolean topAtStart(WheelDomain d) { return d == WheelDomain.GREEN || d == WheelDomain.BLUE; }

    /** Numer wycinka pola w pierscieniu liczony od poczatku cwiartki (rosnacy kat). */
    public static int angularIndex(WheelSlice s) {
        WheelSlice.Pos[] slots = RING_SLOTS[ring(s.pos)];
        int i = 0;
        while (slots[i] != s.pos) i++;
        return topAtStart(s.domain) ? i : slots.length - 1 - i;
    }

    /** Szerokosc katowa pola (stopnie). */
    public static float sweepDeg(WheelSlice s) { return 90f / slotsInRing(ring(s.pos)); }

    /** Poczatek katowy pola (stopnie, 0..360). */
    public static float startDeg(WheelSlice s) { return quadrantStart(s.domain) + angularIndex(s) * sweepDeg(s); }

    /** Srodek katowy pola (stopnie). */
    public static float midDeg(WheelSlice s) { return startDeg(s) + sweepDeg(s) / 2f; }

    /** Promien srodka pola (ulamek promienia kola). */
    public static float midRadius(WheelSlice s) {
        int r = ring(s.pos);
        return (innerRadius(r) + outerRadius(r)) / 2f;
    }

    /** Punkt srodka pola: {x, y} dla kola o promieniu 1 i srodku (0, 0). */
    public static float[] center(WheelSlice s) {
        double a = Math.toRadians(midDeg(s));
        float m = midRadius(s);
        return new float[] {(float) Math.cos(a) * m, (float) Math.sin(a) * m};
    }

    /** Piasta: odleglosc od srodka mniejsza niz {@link #HUB} promienia. */
    public static boolean inHub(float dx, float dy, float radius) {
        return radius > 0 && Math.hypot(dx, dy) < HUB * radius;
    }

    /**
     * Pole pod punktem (dx, dy) wzgledem srodka kola o promieniu radius (y w gore) albo null (piasta, poza kolem).
     * Szczeliny rysunku miedzy polami naleza do najblizszego pola (dotkniecie zawsze cos trafia).
     */
    public static WheelSlice sliceAt(float dx, float dy, float radius) {
        if (radius <= 0) return null;
        double d = Math.hypot(dx, dy) / radius;
        if (d < HUB || d > 1.0) return null;
        int ring = 0;
        while (ring < RING_OUTER.length - 1 && d >= RING_OUTER[ring]) ring++;
        double deg = norm(Math.toDegrees(Math.atan2(dy, dx)));
        WheelDomain dom = quadrantAt(deg);
        double local = deg - quadrantStart(dom);
        int n = slotsInRing(ring);
        int k = Math.min(n - 1, Math.max(0, (int) Math.floor(local / (90.0 / n))));
        int slot = topAtStart(dom) ? k : n - 1 - k;
        return WheelSlice.of(dom, RING_SLOTS[ring][slot]);
    }

    /**
     * Pola sa sasiadami na rysunku: ten sam pierscien i wspolna krawedz katowa albo sasiednie pierscienie
     * i wspolny odcinek katow (dlugosci wiekszej od zera).
     */
    public static boolean touching(WheelSlice a, WheelSlice b) {
        if (a == b) return false;
        int ra = ring(a), rb = ring(b);
        double a0 = startDeg(a), a1 = a0 + sweepDeg(a), b0 = startDeg(b), b1 = b0 + sweepDeg(b);
        if (ra == rb) return near(a1, b0) || near(b1, a0);
        if (Math.abs(ra - rb) != 1) return false;
        return overlap(a0, a1, b0, b1) > 1e-6;
    }

    /** Wszystkie pola w kolejnosci rysowania (od srodka). */
    public static List<WheelSlice> all() { return List.of(WheelSlice.values()); }

    private static boolean near(double x, double y) { return Math.abs(norm(x) - norm(y)) < 1e-6 || Math.abs(Math.abs(norm(x) - norm(y)) - 360) < 1e-6; }

    private static double overlap(double a0, double a1, double b0, double b1) {
        double best = 0;
        for (int k = -1; k <= 1; k++) {
            double s = Math.max(a0, b0 + 360 * k), e = Math.min(a1, b1 + 360 * k);
            best = Math.max(best, e - s);
        }
        return best;
    }

    private static double norm(double deg) {
        double r = deg % 360.0;
        return r < 0 ? r + 360.0 : r;
    }
}
