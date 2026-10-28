package pl.fawizja.pixel.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Inwazja na miasto (wydarzenie serwera): grupa potworow z hersztem pojawia sie za murami miasta.
 * Kazdy pokonany najezdzca jest liczony graczowi; po obronie gracze dostaja zloto na konto.
 * Stan jednej trwajacej inwazji; harmonogram i potwory prowadzi serwer.
 */
public final class Invasion {

    /** Plan inwazji dla miasta: potwor, ilu, herszt (jeden, silniejszy) i nazwa w ogloszeniu. */
    public record Plan(String town, String monster, int count, String leader, String name) {}

    public static final List<Plan> PLANS = List.of(
            new Plan("Varden", "bandit", 24, "cave_troll", "Bandyci"),
            new Plan("Frostholm", "snow_wolf", 24, "yeti", "Śnieżne wilki"),
            new Plan("Qasirah", "hyena", 24, "scorpion", "Hieny pustyni"),
            new Plan("Selvaran", "crocodile", 20, "jungle_serpent", "Krokodyle z bagien"));

    /** Zloto na konto za kazdego pokonanego najezdzce (po udanej obronie albo po czasie). */
    public static final long REWARD_PER_KILL = 100;
    public static final long LEADER_BONUS = 1_000;
    public static final long DURATION_MS = 20 * 60_000L;

    private final Plan plan;
    private final Position center;
    private final Set<Integer> alive = new HashSet<>();
    private final int leaderId;
    private final int total;
    private final long endsAt;
    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private String leaderSlayer;

    public Invasion(Plan plan, Position center, Collection<Integer> ids, int leaderId, long now, long durationMs) {
        this.plan = plan;
        this.center = center;
        this.alive.addAll(ids);
        this.leaderId = leaderId;
        this.total = alive.size();
        this.endsAt = now + durationMs;
    }

    public Plan plan() { return plan; }
    public Position center() { return center; }
    public int total() { return total; }
    public int remaining() { return alive.size(); }
    public Set<Integer> aliveIds() { return java.util.Collections.unmodifiableSet(alive); }
    public long endsAt() { return endsAt; }
    public String leaderSlayer() { return leaderSlayer; }

    /** Zabicie potwora przez gracza; true, gdy to byl najezdzca. */
    public boolean onKill(String player, int monsterId) {
        if (!alive.remove(monsterId)) return false;
        kills.merge(player, 1, Integer::sum);
        if (monsterId == leaderId) leaderSlayer = player;
        return true;
    }

    public boolean defended() { return alive.isEmpty(); }

    public boolean over(long now) { return defended() || now >= endsAt; }

    /** Gracze od najwiekszej liczby pokonanych. */
    public List<Map.Entry<String, Integer>> ranking() {
        List<Map.Entry<String, Integer>> out = new ArrayList<>(kills.entrySet());
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }

    /** Nagroda gracza w gp (0 gdy nie walczyl). */
    public long reward(String player) {
        long r = kills.getOrDefault(player, 0) * REWARD_PER_KILL;
        if (player.equals(leaderSlayer)) r += LEADER_BONUS;
        return r;
    }

    /**
     * Miejsce zbiorki za murami: kafelek na powierzchni 14-40 pol od swiatyni (duze miasta maja mury dalej), do
     * przejscia i poza strefa ochronna. null, gdy nic sie nie nadaje.
     */
    public static Position gatherPoint(WorldMap world, Town town, Random rng) {
        Position t = town.temple();
        for (int attempt = 0; attempt < 4000; attempt++) {
            int dx = rng.nextInt(81) - 40, dy = rng.nextInt(81) - 40;
            int d = Math.max(Math.abs(dx), Math.abs(dy));
            if (d < 14) continue;
            Position p = new Position(t.x() + dx, t.y() + dy, t.z());
            if (p.x() < 0 || p.y() < 0 || p.x() >= world.width() || p.y() >= world.height()) continue;
            if (!world.isWalkable(p) || world.isProtectionZone(p) || world.requiredLevelAt(p) > 0) continue;
            int free = 0;
            for (int ox = -2; ox <= 2; ox++) {
                for (int oy = -2; oy <= 2; oy++) {
                    Position q = p.translate(ox, oy);
                    if (world.isWalkable(q) && !world.isProtectionZone(q)) free++;
                }
            }
            if (free >= 15) return p;
        }
        return null;
    }

    /** "od północy", "od wschodu"... wzgledem swiatyni miasta. */
    public static String direction(Position temple, Position p) {
        int dx = p.x() - temple.x(), dy = p.y() - temple.y();
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? "od wschodu" : "od zachodu";
        return dy >= 0 ? "od południa" : "od północy";
    }
}
