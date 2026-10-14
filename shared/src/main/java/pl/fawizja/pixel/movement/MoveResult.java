package pl.fawizja.pixel.movement;

public enum MoveResult {
    MOVED, CHANGED_FLOOR, BLOCKED, ON_COOLDOWN, DEAD, NOTHING_TO_USE, NEEDS_ROPE;

    public boolean moved() { return this == MOVED || this == CHANGED_FLOOR; }
}
