package pl.fawizja.pixel.monster;

/** Styl walki potwora - kazdy daje inna taktyke AI. */
public enum MonsterBehavior {
    /** Podchodzi i bije wrecz. */
    MELEE,
    /** Trzyma dystans i atakuje z daleka. */
    RANGED,
    /** Atakuje magia z dystansu, zwykle odporny na zywioly. */
    MAGIC,
    /** Szybki, ucieka przy niskim zdrowiu. */
    SKIRMISHER,
    /** Leczy rannych sojusznikow zamiast atakowac. */
    HEALER,
    /** Wolny, opancerzony, duzo HP. */
    TANK
}
