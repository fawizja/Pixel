package pl.fawizja.pixel.charm;

/**
 * Oplaty w zlocie za charmy. Tibia (Canary IOBestiary::sendBuyCharmRune): zdjecie charmu z potwora 100 gp x poziom,
 * pelny reset 100 000 gp + 11 000 gp x poziom (ponad poziomem 100), z "Charm Expansion" 75% ceny.
 * U nas ceny sa 4 razy nizsze, bo gospodarka jest ok. 4-5 razy ubozsza od tibijskiej: blogoslawienstwo kosztuje
 * 200 gp x poziom za calosc, a w Tibii podobna kwote kosztuje jedno z pieciu blogoslawienstw.
 */
public final class CharmPrices {
    /** Dzielnik cen Tibii. */
    public static final int ECONOMY_DIVISOR = 4;

    private CharmPrices() {}

    /** Zdjecie charmu z potwora: Tibia 100 x poziom, u nas 25 x poziom. */
    public static long removal(int level) {
        return 100L * Math.max(1, level) / ECONOMY_DIVISOR;
    }

    /** Reset wszystkich charmow: Tibia 100 000 + 11 000 x poziom ponad 100, u nas cwierc tego. */
    public static long resetAll(int level) {
        long tibia = 100_000L + (level > 100 ? 11_000L * level : 0);
        return tibia / ECONOMY_DIVISOR;
    }
}
