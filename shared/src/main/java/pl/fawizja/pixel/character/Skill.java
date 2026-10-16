package pl.fawizja.pixel.character;

public enum Skill {
    FIST("Pięści"), SWORD("Miecz"), DISTANCE("Dystans"), SHIELDING("Tarcza"), MAGIC("Magia"), FISHING("Wędkarstwo");

    private final String label;

    Skill(String label) { this.label = label; }

    /** Nazwa w grze. */
    public String label() { return label; }
}
