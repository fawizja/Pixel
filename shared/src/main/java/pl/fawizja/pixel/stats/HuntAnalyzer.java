package pl.fawizja.pixel.stats;

import java.util.ArrayList;
import java.util.List;

import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/**
 * Analizator polowania: porownuje stan postaci (doswiadczenie, zabicia z bestiariusza, zloto i wartosc
 * przedmiotow w plecaku wg ceny z items.csv) z migawka z poczatku polowania. Dziala tylko na danych postaci, wiec tak samo offline i online.
 */
public final class HuntAnalyzer {

    public record Report(long elapsedMs, long experience, long expPerHour, long kills, long killsPerHour,
                         long gold, long goldPerHour, long loot, long lootPerHour, long expToLevel, long etaMs) { }

    private boolean started;
    private long startMs;
    private long startExp;
    private long startKills;
    private long startGold;
    private long startLoot;
    private final ItemRegistry items;

    public HuntAnalyzer(ItemRegistry items) { this.items = items; }

    /** Nowe polowanie od teraz. */
    public void reset(long nowMs, PlayerCharacter pc) {
        started = true;
        startMs = nowMs;
        startExp = pc.experience();
        startKills = pc.bestiary().total();
        startGold = Money.totalValue(pc.backpack());
        startLoot = itemsValue(pc);
    }

    /** Wartosc przedmiotow w plecaku bez monet (cena z items.csv razy ilosc). */
    long itemsValue(PlayerCharacter pc) {
        long sum = 0;
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st == null) continue;
            ItemType t = items.get(st.itemId());
            if (t != null && t.category() != ItemCategory.CURRENCY) sum += t.value() * st.count();
        }
        return sum;
    }

    public boolean started() { return started; }

    public Report report(long nowMs, PlayerCharacter pc) {
        if (!started) reset(nowMs, pc);
        long elapsed = Math.max(0, nowMs - startMs);
        long exp = pc.experience() - startExp;
        long kills = pc.bestiary().total() - startKills;
        long gold = Money.totalValue(pc.backpack()) - startGold;
        long loot = itemsValue(pc) - startLoot;
        long toLevel = pc.level() >= Balance.MAX_LEVEL ? 0
                : Math.max(0, Balance.experienceForLevel(pc.level() + 1) - pc.experience());
        long expH = perHour(exp, elapsed);
        long eta = toLevel > 0 && expH > 0 ? (long) Math.ceil(toLevel * 3_600_000.0 / expH) : -1;
        return new Report(elapsed, exp, expH, kills, perHour(kills, elapsed), gold, perHour(gold, elapsed), loot, perHour(loot, elapsed), toLevel, eta);
    }

    /** Wartosc na godzine; przez pierwsza minute brak wiarygodnej predkosci (0). */
    static long perHour(long value, long elapsedMs) {
        if (elapsedMs < 60_000) return 0;
        return Math.round(value * 3_600_000.0 / elapsedMs);
    }

    /** Wiersze do okna informacyjnego {etykieta, wartosc}. */
    public static List<String[]> rows(Report r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Czas polowania", duration(r.elapsedMs())});
        rows.add(new String[] {"Doświadczenie", signed(r.experience()) + rate(r.expPerHour(), r.elapsedMs())});
        rows.add(new String[] {"Zabite potwory", r.kills() + rate(r.killsPerHour(), r.elapsedMs())});
        rows.add(new String[] {"Bilans złota", signed(r.gold()) + " gp" + rate(r.goldPerHour(), r.elapsedMs())});
        rows.add(new String[] {"Wartość przedmiotów", signed(r.loot()) + " gp" + rate(r.lootPerHour(), r.elapsedMs())});
        rows.add(new String[] {"Do następnego poziomu", r.expToLevel() == 0 ? "maksymalny poziom" : r.expToLevel() + " exp"});
        rows.add(new String[] {"Następny poziom za", r.etaMs() < 0 ? "brak danych (poluj dalej)" : duration(r.etaMs())});
        return rows;
    }

    private static String rate(long perHour, long elapsedMs) {
        return elapsedMs < 60_000 ? "" : " (" + signed(perHour) + " / h)";
    }

    private static String signed(long v) { return v > 0 ? "+" + v : Long.toString(v); }

    /** "45 s", "12 min", "3 h 5 min". */
    public static String duration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + " s";
        long min = s / 60;
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }
}
