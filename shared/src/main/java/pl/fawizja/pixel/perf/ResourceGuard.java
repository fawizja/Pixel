package pl.fawizja.pixel.perf;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ochrona zasobow urzadzenia: NORMAL -> LIMITED przy >= 90% (po krotkim potwierdzeniu,
 * zeby pojedyncze skoki nie przelaczaly trybu), LIMITED -> RECOVERING po utrzymaniu
 * ponizej 75% przez okres stabilizacji, RECOVERING -> NORMAL po stopniowym przywroceniu.
 * Logika jest czysta (czas podawany z zewnatrz), wiec da sie ja w pelni przetestowac.
 * Nigdy nie blokuje sterowania, zapisu ani transakcji - steruje tylko kosztownymi efektami.
 */
public final class ResourceGuard {

    public static final class Config {
        public final float enterThreshold;
        public final float resumeThreshold;
        public final long enterConfirmMs;
        public final long stabilizationMs;
        public final long recoveryMs;

        public Config(float enterThreshold, float resumeThreshold, long enterConfirmMs,
                      long stabilizationMs, long recoveryMs) {
            if (!(resumeThreshold < enterThreshold) || enterThreshold > 1f || resumeThreshold < 0f) {
                throw new IllegalArgumentException("need 0 <= resume < enter <= 1");
            }
            this.enterThreshold = enterThreshold;
            this.resumeThreshold = resumeThreshold;
            this.enterConfirmMs = enterConfirmMs;
            this.stabilizationMs = stabilizationMs;
            this.recoveryMs = recoveryMs;
        }

        public static Config defaults() {
            return new Config(0.90f, 0.75f, 750, 3000, 4000);
        }
    }

    private final Config config;
    private GuardMode mode = GuardMode.NORMAL;
    private final EnumMap<Resource, Float> last = new EnumMap<>(Resource.class);
    private long overSinceMs = -1;
    private long underSinceMs = -1;
    private long recoveringSinceMs = -1;
    private Resource reason;
    private float reasonValue;
    private float recoveryProgress = 1f;
    private int transitions;

    public ResourceGuard(Config config) {
        this.config = config;
    }

    public ResourceGuard() {
        this(Config.defaults());
    }

    /** Podaje probke wartosci (0..1 albo NaN, gdy niedostepna) i aktualizuje stan. */
    public GuardMode update(long nowMs, Map<Resource, Float> sample) {
        last.clear();
        Resource worst = null;
        float worstValue = -1f;
        for (Map.Entry<Resource, Float> e : sample.entrySet()) {
            Float v = e.getValue();
            if (v == null || v.isNaN()) continue;
            float c = Math.max(0f, Math.min(1f, v));
            last.put(e.getKey(), c);
            if (c > worstValue) {
                worstValue = c;
                worst = e.getKey();
            }
        }
        boolean over = worst != null && worstValue >= config.enterThreshold;
        boolean under = worst == null || worstValue < config.resumeThreshold;

        switch (mode) {
            case NORMAL:
                if (over) {
                    if (overSinceMs < 0) overSinceMs = nowMs;
                    if (nowMs - overSinceMs >= config.enterConfirmMs) enterLimited(worst, worstValue);
                } else {
                    overSinceMs = -1;
                }
                break;
            case LIMITED:
                if (over) {
                    reason = worst;
                    reasonValue = worstValue;
                }
                if (under) {
                    if (underSinceMs < 0) underSinceMs = nowMs;
                    if (nowMs - underSinceMs >= config.stabilizationMs) {
                        mode = GuardMode.RECOVERING;
                        recoveringSinceMs = nowMs;
                        recoveryProgress = 0f;
                        transitions++;
                    }
                } else {
                    underSinceMs = -1;
                }
                break;
            case RECOVERING:
                if (over) {
                    enterLimited(worst, worstValue);
                } else if (under) {
                    recoveryProgress = Math.min(1f, (nowMs - recoveringSinceMs) / (float) Math.max(1, config.recoveryMs));
                    if (recoveryProgress >= 1f) {
                        mode = GuardMode.NORMAL;
                        reason = null;
                        overSinceMs = -1;
                        transitions++;
                    }
                } else {
                    // Miedzy 75% a 90%: wstrzymaj przywracanie (przesun poczatek, zachowaj postep).
                    recoveringSinceMs = nowMs - (long) (recoveryProgress * config.recoveryMs);
                }
                break;
            default:
                throw new IllegalStateException();
        }
        return mode;
    }

    private void enterLimited(Resource worst, float value) {
        mode = GuardMode.LIMITED;
        reason = worst;
        reasonValue = value;
        underSinceMs = -1;
        overSinceMs = -1;
        recoveryProgress = 0f;
        transitions++;
    }

    public GuardMode mode() { return mode; }
    public Resource reason() { return reason; }
    public float reasonValue() { return reasonValue; }
    public float recoveryProgress() { return mode == GuardMode.NORMAL ? 1f : recoveryProgress; }
    public int transitionCount() { return transitions; }
    public Map<Resource, Float> lastSample() { return new EnumMap<>(last); }
    public Config config() { return config; }

    /** Docelowy limit FPS (0 = bez limitu poza ustawieniami gracza). */
    public int fpsCap(int normalCap) {
        int limited = Math.min(30, normalCap <= 0 ? 30 : normalCap);
        switch (mode) {
            case NORMAL: return normalCap;
            case LIMITED: return limited;
            default:
                int target = normalCap <= 0 ? 60 : normalCap;
                return Math.round(limited + (target - limited) * recoveryProgress);
        }
    }

    /** Jakosc kosztownych efektow 0..1 (swiatla, czasteczki). */
    public float effectsQuality() {
        switch (mode) {
            case NORMAL: return 1f;
            case LIMITED: return 0.25f;
            default: return 0.25f + 0.75f * recoveryProgress;
        }
    }

    /** Czy wolno rozpoczynac odkladalne prace (generowanie dekoracji, prefetch map). */
    public boolean allowDeferrableWork() {
        return mode == GuardMode.NORMAL || (mode == GuardMode.RECOVERING && recoveryProgress >= 0.5f);
    }

    /** Odstep miedzy odswiezeniami diagnostyki. */
    public long diagnosticsIntervalMs() {
        return mode == GuardMode.LIMITED ? 2000L : 500L;
    }

    /** Opis do menu diagnostyki. */
    public String describe() {
        if (mode == GuardMode.NORMAL) return "NORMAL";
        String r = reason == null ? "?" : reason.label() + " " + Math.round(reasonValue * 100) + "%";
        return mode == GuardMode.LIMITED ? "LIMITED (" + r + ")"
                : "RECOVERING " + Math.round(recoveryProgress * 100) + "% (" + r + ")";
    }
}
