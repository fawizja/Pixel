package pl.fawizja.pixel.magic;

/**
 * Rodzaj efektu czaru. RUNE_* to efekty run (rzucane z przedmiotu, bez many).
 * HASTE: baza = premia szybkosci w procentach, zasieg = czas trwania w sekundach (utani hur, utani gran hur).
 */
public enum SpellKind {
    HEAL_SELF, ATTACK_TARGET, ATTACK_AREA, LIGHT, HASTE, CONJURE, RUNE_TARGET, RUNE_AREA, RUNE_HEAL;

    public boolean isRune() { return this == RUNE_TARGET || this == RUNE_AREA || this == RUNE_HEAL; }
    public boolean isHeal() { return this == HEAL_SELF || this == RUNE_HEAL; }
    public boolean needsTarget() { return this == ATTACK_TARGET || this == RUNE_TARGET || this == RUNE_AREA; }
    public boolean isOffensive() { return this == ATTACK_TARGET || this == ATTACK_AREA || this == RUNE_TARGET || this == RUNE_AREA; }
}
