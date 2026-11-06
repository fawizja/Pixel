package pl.fawizja.pixel.achievement;

/**
 * Trudnosc wpisu bestiariusza jak w Tibii (gwiazdki 0-5). Progi zabic etapow 2, 3 i ukonczenia oraz punkty charmow
 * za ukonczenie - wartosci z plikow potworow Canary (monster.Bestiary: FirstUnlock / SecondUnlock / toKill /
 * CharmsPoints), te same na calym zbiorze ok. 1000 potworow Tibii.
 */
public enum BestiaryDifficulty {
    HARMLESS(0, "nieszkodliwy", 5, 10, 25, 1),
    TRIVIAL(1, "banalny", 10, 100, 250, 5),
    EASY(2, "łatwy", 25, 250, 500, 15),
    MEDIUM(3, "średni", 50, 500, 1000, 25),
    HARD(4, "trudny", 100, 1000, 2500, 50),
    CHALLENGING(5, "wymagający", 200, 2000, 5000, 100);

    public final int stars;
    public final String label;
    public final int secondStage;
    public final int thirdStage;
    public final int complete;
    public final int charmPoints;

    BestiaryDifficulty(int stars, String label, int secondStage, int thirdStage, int complete, int charmPoints) {
        this.stars = stars;
        this.label = label;
        this.secondStage = secondStage;
        this.thirdStage = thirdStage;
        this.complete = complete;
        this.charmPoints = charmPoints;
    }

    public static BestiaryDifficulty ofStars(int stars) {
        for (BestiaryDifficulty d : values()) if (d.stars == stars) return d;
        throw new IllegalArgumentException("stars 0..5: " + stars);
    }

    /** Gwiazdki jako tekst ("**---"; czcionka gry nie ma znaku gwiazdki). */
    public String starsText() {
        return "*".repeat(stars) + "-".repeat(5 - stars);
    }
}
