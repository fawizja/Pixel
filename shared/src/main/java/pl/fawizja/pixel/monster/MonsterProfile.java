package pl.fawizja.pixel.monster;

/**
 * Styl ataku potwora z data/monster_behaviour.csv, jak profile potworow w Tibii (Canary data-canary/monster):
 * zwykly cios wrecz, rzucanie i strzelanie z dystansu, czary w jeden cel, czary obszarowe, przywolywanie pomocnikow.
 *
 * @param archetype    rodzaj stylu walki ({@link Archetype})
 * @param keepDistance 0 - potwor podchodzi i bije wrecz; N &gt; 1 - potwor trzyma co najmniej N pol od celu i strzela
 *                     (cofa sie, gdy gracz podejdzie blizej; atak podstawowy jest wtedy pociskiem)
 * @param missile      grafika pocisku ataku podstawowego (assets/art/missiles: spear, arrow, bolt, stone, knife...)
 *                     albo null - wg rodzaju obrazen
 * @param runPercent   ucieczka ponizej tylu procent zdrowia (Canary runonhealth); -1 = wartosc z monsters.csv
 */
public record MonsterProfile(String key, Archetype archetype, int keepDistance, String missile, int runPercent) {

    /** Rodzaje stylu walki: jak podzial potworow Tibii wg sposobu ataku. */
    public enum Archetype {
        /** Tylko cios wrecz (szczur, troll, minotaur). */
        MELEE,
        /** Rzuca i strzela z dystansu, trzyma odleglosc (ork wlocznik, amazonka, lowca). */
        DISTANCE,
        /** Czary w jeden cel: uderzenia energii i ognia, wyssanie zycia i many, czasem leczy siebie (szaman, ciemny mag). */
        CASTER,
        /** Czary obszarowe: fale, kule, promienie, wybuchy (smoki, pajaki z jadowita fala, czarownicy). */
        AREA,
        /** Przywoluje pomocnikow (nekromanta - ghoule, szaman - zwierzeta). */
        SUMMONER,
        /** Cios wrecz i czary albo kilka rodzajow ataku naraz (demon, wiwerna). */
        MIXED,
        /** Leczy sojusznikow (kaplani, szamani), do tego razi z dystansu. */
        SUPPORT
    }

    public MonsterProfile {
        if (key == null || key.isBlank() || archetype == null) throw new IllegalArgumentException("bad monster profile");
        if (keepDistance < 0 || keepDistance == 1 || keepDistance > 8) throw new IllegalArgumentException("bad keep distance " + key);
        if (runPercent < -1 || runPercent > 60) throw new IllegalArgumentException("bad run percent " + key);
    }

    /** Czy potwor trzyma dystans (strzelec, mag). */
    public boolean keepsDistance() { return keepDistance > 1; }

    /** Profil zastepczy dla rodzaju bez wpisu (potwory testowe): wg zachowania z monsters.csv. */
    static MonsterProfile fallback(MonsterType t) {
        boolean far = t.range() > 1 && (t.behavior() == MonsterBehavior.RANGED || t.behavior() == MonsterBehavior.MAGIC
                || t.behavior() == MonsterBehavior.HEALER);
        return new MonsterProfile(t.key(), far ? Archetype.DISTANCE : Archetype.MELEE, far ? t.range() : 0, null, -1);
    }
}
