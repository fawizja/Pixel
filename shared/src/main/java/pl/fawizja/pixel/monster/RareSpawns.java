package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.world.Position;

/**
 * Rzadki spawn jak w Tibii: na smoczych lowiskach bardzo rzadko zamiast zwyklego smoka odradza sie bialy, perlowy
 * smok. Losowanie przy kazdym odrodzeniu smoka (nie przy starcie swiata), tylko gdy w poblizu lowiska jest gracz;
 * po pojawieniu sie smoka obowiazuje przerwa i zyje najwyzej jeden. Zegar i los liczy {@link MonsterManager}
 * (offline i serwer tak samo), wiec test z ziarnem daje powtarzalny wynik.
 *
 * @param monsterKey     rzadki potwor (pearl_dragon)
 * @param chancePermille szansa (promile) na kazde odrodzenie smoka przy graczu: 15 = 1,5%
 * @param cooldownMs     przerwa miedzy pojawieniami (od chwili pojawienia sie)
 * @param huntRadius     gracz na tym pietrze i najwyzej tyle pol od srodka spawnu = "na lowisku"
 * @param minLevel       odrodzenie smoka o poziomie od tego liczy sie jako smocze lowisko (bez piskleci)
 */
public record RareSpawns(String monsterKey, int chancePermille, long cooldownMs, int huntRadius, int minLevel) {

    public static final String PEARL_DRAGON = "pearl_dragon";

    public RareSpawns {
        if (monsterKey == null || monsterKey.isBlank() || chancePermille < 0 || chancePermille > 1000 || cooldownMs < 0
                || huntRadius < 1 || minLevel < 1) throw new IllegalArgumentException("bad rare spawn");
    }

    /** Ustawienia gry: 1,5% na odrodzenie smoka, przerwa godzina, gracz do 32 pol od srodka spawnu. */
    public static RareSpawns standard() {
        return new RareSpawns(PEARL_DRAGON, 15, 3_600_000L, 32, 100);
    }

    /** Czy odrodzenie tego potwora liczy sie jako smocze lowisko: klasa Dragon, nie pisklak, nie sam rzadki smok. */
    public boolean isDragonGround(MonsterType t) {
        return t != null && "Dragon".equals(t.race()) && t.level() >= minLevel && !t.key().equals(monsterKey) && !t.boss();
    }

    /** Czy gracz stoi na lowisku wokol srodka spawnu. */
    public boolean playerOnGround(Position spawnCenter, Position player) {
        return player.z() == spawnCenter.z() && player.distanceTo(spawnCenter) <= huntRadius;
    }
}
