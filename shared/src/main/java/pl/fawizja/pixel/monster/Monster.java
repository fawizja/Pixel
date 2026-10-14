package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Instancja potwora w swiecie. */
public final class Monster {
    private final int id;
    private final MonsterType type;
    private final SpawnPoint spawn;
    private int health;
    private Position position;
    private Position previous;
    private long stepStartMs;
    private long stepDurationMs;
    private MonsterState state = MonsterState.IDLE;
    private long nextAttackMs;
    private long nextActionMs;
    private boolean healedOnce;
    private boolean cornered;
    private Direction facing = Direction.SOUTH;

    public Monster(int id, MonsterType type, SpawnPoint spawn, Position position) {
        this.id = id;
        this.type = type;
        this.spawn = spawn;
        this.position = position;
        this.previous = position;
        this.health = type.maxHealth();
    }

    public int id() { return id; }
    public MonsterType type() { return type; }
    public SpawnPoint spawn() { return spawn; }
    public int health() { return health; }
    public boolean isDead() { return health <= 0; }
    public Position position() { return position; }
    public MonsterState state() { return state; }
    public Direction facing() { return facing; }
    public float healthFraction() { return health / (float) type.maxHealth(); }
    public boolean healedOnce() { return healedOnce; }
    void markHealed() { healedOnce = true; }
    /** Osaczony potwor przestaje uciekac i walczy do konca (do powrotu na spawn). */
    public boolean cornered() { return cornered; }
    void setCornered(boolean c) { cornered = c; }

    void setState(MonsterState s) { state = s; }
    long nextAttackMs() { return nextAttackMs; }
    void setNextAttackMs(long t) { nextAttackMs = t; }
    long nextActionMs() { return nextActionMs; }
    void setNextActionMs(long t) { nextActionMs = t; }

    public boolean isMoving(long now) { return now - stepStartMs < stepDurationMs; }

    void moveTo(Position p, Direction d, long now, long durationMs) {
        previous = position;
        position = p;
        facing = d;
        stepStartMs = now;
        stepDurationMs = durationMs;
    }

    /** Teleport bez interpolacji (spawn). */
    void placeAt(Position p) {
        position = p;
        previous = p;
        stepDurationMs = 0;
    }

    /** Zadaje obrazenia, zwraca faktycznie odjete HP. */
    public int damage(int amount) {
        if (amount <= 0 || isDead()) return 0;
        int d = Math.min(health, amount);
        health -= d;
        return d;
    }

    public int heal(int amount) {
        if (amount <= 0 || isDead()) return 0;
        int h = Math.min(type.maxHealth() - health, amount);
        health += h;
        return h;
    }

    public float renderX(long now) { return lerp(previous.x(), position.x(), progress(now)); }
    public float renderY(long now) { return lerp(previous.y(), position.y(), progress(now)); }

    private float progress(long now) {
        if (stepDurationMs <= 0) return 1f;
        return Math.min(1f, (now - stepStartMs) / (float) stepDurationMs);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
