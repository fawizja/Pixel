package pl.fawizja.pixel.combat;

/**
 * Stany zadajace obrazenia co tik, jak w Tibii: zatrucie (ziemia), podpalenie (ogien), porazenie (energia),
 * krwawienie (fizyczne), zamrozenie (lod), oslepienie (swietosc), klatwa (smierc) i tonięcie (woda).
 * Obrazenia tikow zmniejszaja odpornosci na zywiol (bez pancerza), jak w Tibii.
 */
public enum ConditionType {
    POISON(DamageType.EARTH, "Zatrucie", "zatruty", "exana pox"),
    BURNING(DamageType.FIRE, "Podpalenie", "płonie", "exana flam"),
    ELECTRIFIED(DamageType.ENERGY, "Porażenie", "porażony prądem", "exana vis"),
    BLEEDING(DamageType.PHYSICAL, "Krwawienie", "krwawi", "exana kor"),
    FREEZING(DamageType.ICE, "Zamrożenie", "zamarza", null),
    DAZZLED(DamageType.HOLY, "Oślepienie", "oślepiony", null),
    CURSED(DamageType.DEATH, "Klątwa", "przeklęty", "exana mort"),
    DROWNING(DamageType.DROWN, "Tonięcie", "tonie", null);

    public final DamageType damage;
    public final String label;
    public final String state;
    /** Slowa czaru leczacego stan albo null (jak w Tibii zamrozenia, oslepienia i toniecia nie leczy zaden czar). */
    public final String cure;

    ConditionType(DamageType damage, String label, String state, String cure) {
        this.damage = damage;
        this.label = label;
        this.state = state;
        this.cure = cure;
    }

    /** Stan zwiazany z typem obrazen (pola, runy, czary utori, leczenie exana) albo null. */
    public static ConditionType of(DamageType t) {
        if (t == null) return null;
        for (ConditionType c : values()) if (c.damage == t) return c;
        return null;
    }

    /** Opis stanu na ikonie HUD. */
    public String describe() {
        return label + ": tracisz życie co kilka sekund" + (cure != null ? " (leczy " + cure + ")" : "") + ".";
    }
}
