package pl.fawizja.pixel.magic;

/**
 * Wspolny czas odnowienia grupy jak w Canary: atak 2 s, leczenie 1 s, wsparcie 2 s (grupy niezalezne od siebie;
 * wlasne odnowienia grup i drugie grupy - {@link SpellGroups}). POTION: uzycie przedmiotu - mikstury i runy
 * dziela 1 s (Canary nextPotionAction, timeBetweenExActions = 1000).
 */
public enum SpellGroup {
    ATTACK(2000), HEALING(1000), SUPPORT(2000), POTION(1000);

    public final int groupCooldownMs;

    SpellGroup(int ms) { this.groupCooldownMs = ms; }
}
