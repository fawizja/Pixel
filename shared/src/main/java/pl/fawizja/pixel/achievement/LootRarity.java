package pl.fawizja.pixel.achievement;

/**
 * Rzadkosc lupu w bestiariuszu jak w Tibii: zamiast szansy w procentach kategoria (Canary
 * IOBestiary::calculateDifficult; progi z Tibii: pospolite od 25%, niepospolite 5-25%, polrzadkie 1-5%,
 * rzadkie 0,2-1%, bardzo rzadkie ponizej 0,2%). Etap 2 pokazuje pospolite i niepospolite, etap 3 takze
 * polrzadkie, ukonczony wpis wszystkie.
 */
public enum LootRarity {
    COMMON("pospolity", 250),
    UNCOMMON("niepospolity", 50),
    SEMI_RARE("półrzadki", 10),
    RARE("rzadki", 2),
    VERY_RARE("bardzo rzadki", 0);

    public final String label;
    /** Najmniejsza szansa w promilach dla tej kategorii. */
    public final int minPermille;

    LootRarity(String label, int minPermille) {
        this.label = label;
        this.minPermille = minPermille;
    }

    public static LootRarity of(int chancePermille) {
        for (LootRarity r : values()) if (chancePermille >= r.minPermille) return r;
        return VERY_RARE;
    }

    /** Najnizszy etap bestiariusza, od ktorego widac lup tej kategorii. */
    public int revealStage() {
        switch (this) {
            case COMMON:
            case UNCOMMON: return 2;
            case SEMI_RARE: return 3;
            default: return 4;
        }
    }
}
