package pl.fawizja.pixel.magic;

/**
 * Rodzaj efektu czaru. RUNE_* to efekty run (rzucane z przedmiotu, bez many).
 * HASTE: baza = premia szybkosci w procentach, zasieg = czas trwania w sekundach (utani hur, utani gran hur).
 * Mnich (harmonia, {@link pl.fawizja.pixel.vocation.Harmony}): HEAL_PARTY leczy mnicha i czlonkow druzyny w promieniu,
 * VIRTUE ustawia cnote (wg klucza czaru), FOCUS_HARMONY napelnia harmonie, FOCUS_SERENITY napelnia harmonie,
 * zeruje odnowienia czarow wydajacych i wymusza spokoj na 7 s.
 * Kolo Przeznaczenia: ATTACK_BEAM - promien w strone celu (promien = dlugosc), WHEEL_BUFF - awatar albo Boskie
 * wzmocnienie ({@link pl.fawizja.pixel.wheel.WheelSpells#buff}). ATTACK_TARGET z promieniem > 0 wybucha wokol celu.
 * Czary jak w Tibii (0.240): ATTACK_WAVE - fala (stozek) w strone celu albo patrzenia, CONDITION_TARGET - stan w cel
 * (utori flam/vis/pox/kor/mort/san), CURE - leczenie stanu (exana pox/flam/vis/kor/mort), HEAL_OTHER - leczenie
 * czlonka druzyny (exura sio), REGENERATION (utura), MAGIC_SHIELD (utamo vita), INVISIBLE (utana vid), ILLUSION
 * (utevo res ina), SUMMON (utevo res), CHALLENGE (exeta res), BUFF (utito tempo, utamo tempo...), LEVITATE (exani hur
 * up/down), MAGIC_ROPE (exani tera). Runy: RUNE_FIELD (pole/bomba), RUNE_WALL, RUNE_BARRIER (magiczna sciana, dziki
 * wzrost), RUNE_DESTROY_FIELD, RUNE_CONDITION (ogien duszy), RUNE_PARALYSE, RUNE_CURE (odtrutka), RUNE_CONVINCE,
 * RUNE_ANIMATE (ozywienie zwlok).
 */
public enum SpellKind {
    HEAL_SELF, ATTACK_TARGET, ATTACK_AREA, LIGHT, HASTE, CONJURE, RUNE_TARGET, RUNE_AREA, RUNE_HEAL,
    HEAL_PARTY, VIRTUE, FOCUS_HARMONY, FOCUS_SERENITY, ATTACK_BEAM, WHEEL_BUFF,
    ATTACK_WAVE, CONDITION_TARGET, CURE, HEAL_OTHER, REGENERATION, MAGIC_SHIELD, INVISIBLE, ILLUSION, SUMMON,
    CHALLENGE, BUFF, LEVITATE, MAGIC_ROPE,
    RUNE_FIELD, RUNE_WALL, RUNE_BARRIER, RUNE_DESTROY_FIELD, RUNE_CONDITION, RUNE_PARALYSE, RUNE_CURE, RUNE_CONVINCE,
    RUNE_ANIMATE;

    public boolean isRune() { return name().startsWith("RUNE_"); }

    public boolean isHeal() { return this == HEAL_SELF || this == RUNE_HEAL || this == HEAL_PARTY || this == HEAL_OTHER; }

    /** Czar/runa wymaga wybranego celu w zasiegu i polu widzenia. */
    public boolean needsTarget() {
        switch (this) {
            case ATTACK_TARGET: case RUNE_TARGET: case RUNE_AREA: case CONDITION_TARGET:
            case RUNE_CONDITION: case RUNE_PARALYSE: case RUNE_CONVINCE:
                return true;
            default:
                return false;
        }
    }

    /** Czar albo runa ofensywna: nie dziala w strefie ochronnej. */
    public boolean isOffensive() {
        switch (this) {
            case ATTACK_TARGET: case ATTACK_AREA: case RUNE_TARGET: case RUNE_AREA: case ATTACK_BEAM: case ATTACK_WAVE:
            case CONDITION_TARGET: case RUNE_FIELD: case RUNE_WALL: case RUNE_BARRIER: case RUNE_CONDITION:
            case RUNE_PARALYSE:
                return true;
            default:
                return false;
        }
    }

    /** Czar/runa obszarowa (czarna czaszka ich zabrania, jak w Tibii). */
    public boolean isArea() {
        return this == ATTACK_AREA || this == RUNE_AREA || this == ATTACK_WAVE || this == ATTACK_BEAM || this == RUNE_FIELD || this == RUNE_WALL;
    }

    /** Runa celujaca w pole (cel albo pole przed postacia), nie w potwora. */
    public boolean targetsTile() {
        return this == RUNE_FIELD || this == RUNE_WALL || this == RUNE_BARRIER || this == RUNE_DESTROY_FIELD || this == RUNE_ANIMATE;
    }
}
