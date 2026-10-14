package pl.fawizja.pixel.world;

/** Sposoby przechodzenia miedzy pietrami. */
public enum TransitionType {
    /** Schody: wejscie na kafelek przenosi na inne pietro. */
    STAIRS(true, false),
    /** Dziura / szyb: wejscie powoduje zejscie (spadniecie) nizej. */
    HOLE(true, false),
    /** Tunel / przejscie: wejscie przenosi do polaczonego miejsca. */
    TUNNEL(true, false),
    /** Drabina: trzeba jej uzyc stojac na niej lub obok. */
    LADDER(false, false),
    /** Miejsce na line: wyjscie z dziury wymaga liny w plecaku. */
    ROPE_SPOT(false, true);

    private final boolean triggeredByWalking;
    private final boolean requiresRope;

    TransitionType(boolean triggeredByWalking, boolean requiresRope) {
        this.triggeredByWalking = triggeredByWalking;
        this.requiresRope = requiresRope;
    }

    public boolean triggeredByWalking() { return triggeredByWalking; }
    public boolean requiresRope() { return requiresRope; }
}
