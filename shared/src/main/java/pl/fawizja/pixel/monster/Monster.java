package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Instancja potwora w swiecie. */
public final class Monster {
    private final int id;
    /** Rodzaj; zmienia sie tylko przy staniu sie diabolicznym i powrocie (Kuznia, {@link #changeType}). */
    private MonsterType type;
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
    private MonsterManager.Target target;
    /** Ostatnie miejsce, gdzie potwor widzial gracza, i kiedy (poscig za znikajacym z oczu). */
    private Position lastSeen;
    private long lastSeenMs;
    /** Charmy (Okaleczenie, Odretwienie, Smiertelny chwyt): paraliz i zakaz ucieczki do tego czasu. */
    private long paralyzedUntilMs = Long.MIN_VALUE;
    private long fleeBlockedUntilMs = Long.MIN_VALUE;

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

    /** Paraliz (charm Okaleczenie / Odretwienie): potwor chodzi wolniej do podanego czasu. */
    public void paralyze(long untilMs) { paralyzedUntilMs = Math.max(paralyzedUntilMs, untilMs); }
    public boolean paralyzed(long now) { return now < paralyzedUntilMs; }
    /** Smiertelny chwyt: potwor nie ucieka (niskie zdrowie) do podanego czasu. */
    public void holdFromFleeing(long untilMs) { fleeBlockedUntilMs = Math.max(fleeBlockedUntilMs, untilMs); }
    public boolean fleeBlocked(long now) { return now < fleeBlockedUntilMs; }

    /** Gracz, na ktorego potwor teraz poluje (wielu graczy: serwer kieruje atak do jego sesji). */
    public MonsterManager.Target target() { return target; }
    void setTarget(MonsterManager.Target t) { target = t; }
    void setCornered(boolean c) { cornered = c; }

    void setState(MonsterState s) { state = s; }
    Position lastSeen() { return lastSeen; }
    long lastSeenMs() { return lastSeenMs; }
    void saw(Position p, long now) {
        lastSeen = p;
        lastSeenMs = now;
    }
    /** Rozglada sie: zwrot bez kroku (bezczynny potwor patrzy w rozne strony). */
    void face(Direction d) { facing = d; }
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

    /**
     * Zmiana odmiany (diaboliczny i z powrotem): zdrowie w tej samej proporcji, potwor zostaje na miejscu.
     */
    public void changeType(MonsterType t) {
        if (t == null || t == type) return;
        float f = healthFraction();
        type = t;
        health = Math.max(isDead() ? 0 : 1, Math.round(t.maxHealth() * f));
    }

    /** Klient online: zdrowie z klatki stanu serwera (w procentach). */
    void setHealthPercent(int percent) {
        health = Math.max(1, Math.min(type.maxHealth(), (int) Math.round(type.maxHealth() * percent / 100.0)));
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
