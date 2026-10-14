package pl.fawizja.pixel.magic;

/** Wspolny czas odnowienia grupy (np. po czarze ataku nie mozna od razu rzucic kolejnego). */
public enum SpellGroup {
    ATTACK(2000), HEALING(1000), SUPPORT(2000), POTION(1000);

    public final int groupCooldownMs;

    SpellGroup(int ms) { this.groupCooldownMs = ms; }
}
