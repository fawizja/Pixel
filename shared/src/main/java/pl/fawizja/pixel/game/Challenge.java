package pl.fawizja.pixel.game;

import java.util.ArrayList;
import java.util.List;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.world.Position;

/**
 * Wyzwanie ze zwoju: kolejne fale potworow wokol miejsca odczytania. Fala nastepna rusza, gdy padnie
 * poprzednia; po ostatniej nagroda. Za dlugo albo za daleko od miejsca - wyzwanie przepada.
 */
public final class Challenge {

    public static final int[] WAVES = {3, 4, 5};
    public static final int RADIUS = 3;
    public static final long TIME_MS = 5 * 60_000L;
    /** Odejscie dalej niz tyle pol od srodka konczy wyzwanie. */
    public static final int LEAVE = 12;

    final MonsterType type;
    final Position center;
    final long untilMs;
    int wave;
    final List<Integer> ids = new ArrayList<>();

    Challenge(MonsterType type, Position center, long untilMs) {
        this.type = type;
        this.center = center;
        this.untilMs = untilMs;
    }

    public MonsterType type() { return type; }
    public int wave() { return wave; }
    public Position center() { return center; }

    public static int totalMonsters() {
        int n = 0;
        for (int w : WAVES) n += w;
        return n;
    }

    /** Nagroda za ukonczenie: polowa doswiadczenia za wszystkie potwory fal jako premia i zloto na konto. */
    public static long rewardExp(MonsterType t) { return t.experience() * totalMonsters() / 2; }

    public static long rewardGold(MonsterType t) { return Math.max(500, (long) t.level() * 40); }
}
