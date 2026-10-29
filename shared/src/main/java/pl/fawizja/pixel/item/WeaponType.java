package pl.fawizja.pixel.item;

import pl.fawizja.pixel.character.Skill;

/**
 * Rodzaj broni z kolumny weaponType w data/items.csv (jak w Tibii): decyduje, ktora umiejetnosc liczy obrazenia
 * i ktora trenuje sie walka. NONE - nie bron (np. wedka w rece: walka piesciami).
 */
public enum WeaponType {
    NONE(Skill.FIST), FIST(Skill.FIST), SWORD(Skill.SWORD), AXE(Skill.AXE), CLUB(Skill.CLUB),
    DISTANCE(Skill.DISTANCE), WAND(Skill.MAGIC);

    private final Skill skill;

    WeaponType(Skill skill) { this.skill = skill; }

    /** Umiejetnosc, ktora liczy obrazenia tej broni i ktora trenuje jej uzycie. */
    public Skill skill() { return skill; }

    /** "-" lub puste pole w CSV = NONE. */
    public static WeaponType parse(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() || t.equals("-") ? NONE : valueOf(t);
    }
}
