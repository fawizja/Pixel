package pl.fawizja.pixel.magic;

/**
 * Rodzaj efektu czaru. RUNE_* to efekty run (rzucane z przedmiotu, bez many).
 * HASTE: baza = premia szybkosci w procentach, zasieg = czas trwania w sekundach (utani hur, utani gran hur).
 * Mnich (harmonia, {@link pl.fawizja.pixel.vocation.Harmony}): HEAL_PARTY leczy mnicha i czlonkow druzyny w promieniu,
 * VIRTUE ustawia cnote (wg klucza czaru), FOCUS_HARMONY napelnia harmonie, FOCUS_SERENITY napelnia harmonie,
 * zeruje odnowienia czarow wydajacych i wymusza spokoj na 7 s.
 * Kolo Przeznaczenia: ATTACK_BEAM - promien w strone celu (promien = dlugosc), WHEEL_BUFF - awatar albo Boskie
 * wzmocnienie ({@link pl.fawizja.pixel.wheel.WheelSpells#buff}). ATTACK_TARGET z promieniem > 0 wybucha wokol celu.
 */
public enum SpellKind {
    HEAL_SELF, ATTACK_TARGET, ATTACK_AREA, LIGHT, HASTE, CONJURE, RUNE_TARGET, RUNE_AREA, RUNE_HEAL,
    HEAL_PARTY, VIRTUE, FOCUS_HARMONY, FOCUS_SERENITY, ATTACK_BEAM, WHEEL_BUFF;

    public boolean isRune() { return this == RUNE_TARGET || this == RUNE_AREA || this == RUNE_HEAL; }
    public boolean isHeal() { return this == HEAL_SELF || this == RUNE_HEAL || this == HEAL_PARTY; }
    public boolean needsTarget() { return this == ATTACK_TARGET || this == RUNE_TARGET || this == RUNE_AREA || this == ATTACK_BEAM; }
    public boolean isOffensive() { return this == ATTACK_TARGET || this == ATTACK_AREA || this == RUNE_TARGET || this == RUNE_AREA || this == ATTACK_BEAM; }
}
