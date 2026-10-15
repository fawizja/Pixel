package pl.fawizja.pixel.movement;

public enum MoveResult {
    MOVED, CHANGED_FLOOR, BLOCKED, ON_COOLDOWN, DEAD, NOTHING_TO_USE, NEEDS_ROPE, LEVEL_TOO_LOW;

    public boolean moved() { return this == MOVED || this == CHANGED_FLOOR; }
}
