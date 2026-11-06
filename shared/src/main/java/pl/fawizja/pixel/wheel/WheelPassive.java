package pl.fawizja.pixel.wheel;

import pl.fawizja.pixel.vocation.Vocation;

/**
 * Pasywki profesji z pol 200 (Canary instant: GREEN_200 i PURPLE_200 po zapelnieniu). Liczby z player_wheel.cpp
 * (checkBattleInstinct, checkPositionalTactics, checkBallisticMastery, checkBattleHealingAmount, checkFocusMasteryDamage)
 * i spells.cpp (applySanctuaryEffect). Dopasowania do Pixela opisane przy kazdej pozycji.
 */
public enum WheelPassive {
    /** Rycerz, zielone 200: co najmniej 5 potworow na sasiednich polach - +1 walki wrecz i +6 tarczy za kazdego ponad 4. */
    BATTLE_INSTINCT("Instynkt bitewny", "5+ potworów obok: +1 walki wręcz i +6 tarczy za każdego ponad 4"),
    /**
     * Rycerz, fioletowe 200: gdy potwor zaczyna atakowac, leczenie 0,2 x tarcza (x2 przy HP do 60%, x3 do 30%).
     * U nas: przy ciosie potwora, najwyzej raz na 2 s (brak zdarzenia "potwor wybral cel").
     */
    BATTLE_HEALING("Leczenie w boju", "cios potwora leczy 0,2 x tarcza (x2 przy HP do 60%, x3 do 30%), co 2 s"),
    /** Paladyn, zielone 200: bez potwora obok +3 dystansu, z potworem obok +3 poziomu magii (swieta magia i leczenie). */
    POSITIONAL_TACTICS("Taktyka pozycyjna", "bez potwora obok +3 dystansu, z potworem obok +3 magii"),
    /**
     * Paladyn, fioletowe 200: z beltami +10% obrazen krytycznych, ze strzalami +2% obrazen fizycznych i swietych.
     * U nas: kusza = belty, inny luk = strzaly.
     */
    BALLISTIC_MASTERY("Mistrzostwo balistyki", "kusza: +10% obrażeń krytycznych; łuk: +2% obrażeń"),
    /**
     * Druid, zielone 200: Heal Friend i Nature's Embrace lecza tez rzucajacego (10%). U nas brak leczenia innych przez
     * druida - zamiennik: czary leczace +10%.
     */
    HEALING_LINK("Więź lecznicza", "czary leczące +10% (zamiennik: brak leczenia innych)"),
    /** Sorcerer zielone 200, druid fioletowe 200: runy - 25% szansy na +20% poziomu magii w obrazeniach runy. */
    RUNIC_MASTERY("Mistrzostwo run", "runy: 25% szansy na +20% poziomu magii"),
    /** Sorcerer, fioletowe 200: po czarze skupienia (Piekielny rdzeń, Gniew niebios) nastepny czar ataku w 12 s +35%. */
    FOCUS_MASTERY("Mistrzostwo skupienia", "po czarze skupienia następny atak czarem w 12 s +35% obrażeń"),
    /**
     * Mnich, zielone 200: w Tibii mantra dla druzyny (lider mnich). U nas mantry nie ma - zamiennik: leczenie z harmonii
     * +10%.
     */
    GUIDING_PRESENCE("Przewodnia obecność", "leczenie z harmonii +10% (zamiennik mantry)"),
    /** Mnich, fioletowe 200: wydanie harmonii daje na 5 s +2% obrazen i leczenia za kazdy wydany punkt. */
    SANCTUARY("Sanktuarium", "wydanie harmonii: +2% obrażeń i leczenia za punkt na 5 s");

    public final String label;
    public final String description;

    WheelPassive(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Nazwa w Tibii, np. "Battle Instinct". */
    public String tibia() { return WheelPerks.tibiaName(name()); }

    /** Pasywka pola GREEN_200 (a = true) albo PURPLE_200 dla profesji. */
    public static WheelPassive of(Vocation v, boolean a) {
        switch (v) {
            case KNIGHT: return a ? BATTLE_INSTINCT : BATTLE_HEALING;
            case PALADIN: return a ? POSITIONAL_TACTICS : BALLISTIC_MASTERY;
            case SORCERER: return a ? RUNIC_MASTERY : FOCUS_MASTERY;
            case DRUID: return a ? HEALING_LINK : RUNIC_MASTERY;
            default: return a ? GUIDING_PRESENCE : SANCTUARY;
        }
    }
}
