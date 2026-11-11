package pl.fawizja.pixel.achievement;

import pl.fawizja.pixel.i18n.Messages;
/**
 * Wpis bestiariusza potwora (data/bestiary.csv): odpowiednik w Tibii, trudnosc (gwiazdki) i wystepowanie przepisane
 * z jego pliku Canary. Progi zabic i punkty charmow jak w Tibii: zwykle z {@link BestiaryDifficulty}, a potwory
 * "bardzo rzadkie" (Occurrence 3) maja progi 2 / 3 / 5 i 10 / 30 / 50 punktow dla 1 / 2 / 3+ gwiazdek (Canary).
 *
 * <p>Etapy jak w Tibii (Canary IOBestiary::getKillStatus i ProtocolGame::parseBestiarysendMonsterData):
 * 0 = nieznany (0 zabic), 1 = od pierwszego zabicia, 2 = od {@link #secondStage()}, 3 = od {@link #thirdStage()},
 * 4 = ukonczony ({@link #complete()} zabic).
 */
public record BestiaryEntry(String key, String tibiaName, BestiaryDifficulty difficulty, int occurrence) {

    public static final int VERY_RARE = 3;
    public static final int STAGES = 4;

    public BestiaryEntry {
        if (occurrence < 0 || occurrence > VERY_RARE) throw new IllegalArgumentException(Messages.get("bestiary_entry.occurrence_0_3") + key);
    }

    public boolean veryRare() { return occurrence == VERY_RARE; }

    public int secondStage() { return veryRare() ? 2 : difficulty.secondStage; }
    public int thirdStage() { return veryRare() ? 3 : difficulty.thirdStage; }
    public int complete() { return veryRare() ? 5 : difficulty.complete; }

    public int charmPoints() {
        if (!veryRare()) return difficulty.charmPoints;
        return difficulty.stars <= 1 ? 10 : difficulty.stars == 2 ? 30 : 50;
    }

    /** Etap 0..4 dla liczby zabic. */
    public int stage(int kills) {
        if (kills <= 0) return 0;
        if (kills < secondStage()) return 1;
        if (kills < thirdStage()) return 2;
        if (kills < complete()) return 3;
        return 4;
    }

    /** Prog nastepnego etapu albo 0 po ukonczeniu. */
    public int nextThreshold(int kills) {
        switch (stage(kills)) {
            case 0: return 1;
            case 1: return secondStage();
            case 2: return thirdStage();
            case 3: return complete();
            default: return 0;
        }
    }

    public String occurrenceLabel() {
        switch (occurrence) {
            case 1: return "niepospolity";
            case 2: return "rzadki";
            case 3: return Messages.get("bestiary_entry.bardzo_rzadki");
            default: return "pospolity";
        }
    }
}
