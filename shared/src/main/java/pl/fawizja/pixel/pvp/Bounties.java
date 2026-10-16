package pl.fawizja.pixel.pvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nagrody za glowy graczy: kazdy moze dolozyc zloto z konta do nagrody za postac, a kto ja pokona w walce PvP
 * (nie z tej samej druzyny ani gildii, nie w pojedynku), dostaje calosc na konto. Zapis w bounties.txt
 * jako linie "Imie;kwota".
 */
public final class Bounties {

    /** Najmniejsza wplata - drobne kwoty tylko zasmiecalyby liste. */
    public static final long MIN = 1_000;
    /** Tyle nagrod pokazuje /nagrody. */
    public static final int SHOWN = 10;

    public record Bounty(String name, long amount) {}

    private final Map<String, Bounty> byName = new LinkedHashMap<>();

    /** Dolozenie kwoty do nagrody za postac (imie z wielkoscia liter z zapisu). Zwraca nowa sume. */
    public synchronized long add(String name, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount " + amount);
        Bounty old = byName.get(key(name));
        long total = old == null ? amount : safeAdd(old.amount(), amount);
        byName.put(key(name), new Bounty(old == null ? name : old.name(), total));
        return total;
    }

    /** Suma nagrody za postac (0 = brak). */
    public synchronized long amount(String name) {
        Bounty b = byName.get(key(name));
        return b == null ? 0 : b.amount();
    }

    /** Odbior nagrody: usuwa ja i zwraca kwote (0 = nie bylo). */
    public synchronized long claim(String name) {
        Bounty b = byName.remove(key(name));
        return b == null ? 0 : b.amount();
    }

    /** Najwyzsze nagrody, malejaco. */
    public synchronized List<Bounty> top(int n) {
        List<Bounty> all = new ArrayList<>(byName.values());
        all.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        return new ArrayList<>(all.subList(0, Math.min(n, all.size())));
    }

    public synchronized String encode() {
        StringBuilder sb = new StringBuilder();
        for (Bounty b : byName.values()) sb.append(b.name()).append(';').append(b.amount()).append('\n');
        return sb.toString();
    }

    /** Odczyt pliku; bledne linie sa pomijane. */
    public static Bounties decode(String text) {
        Bounties out = new Bounties();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] f = line.trim().split(";");
            if (f.length != 2 || f[0].isBlank()) continue;
            try {
                long a = Long.parseLong(f[1].trim());
                if (a > 0) out.add(f[0].trim(), a);
            } catch (NumberFormatException e) {
                // pomin
            }
        }
        return out;
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
}
