package pl.fawizja.pixel.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Podsumowanie smierci: kto zadal obrazenia w ostatnich {@link #WINDOW_MS} ms przed smiercia (suma i liczba
 * ciosow na zrodlo, od najwiekszej), zabojca i strata doswiadczenia. Pamieta tylko ostatnie trafienia.
 */
public final class DeathRecap {

    public static final long WINDOW_MS = 30_000;
    static final int MAX_HITS = 64;

    record Hit(long timeMs, String source, int amount) { }

    public record Row(String source, long total, int hits) { }

    public record Report(long timeMs, String killer, long expLost, boolean blessed, List<Row> rows) { }

    private final ArrayDeque<Hit> hits = new ArrayDeque<>();

    /** Trafienie postaci (0 = zablokowane, pomijane). */
    public void record(String source, int amount, long now) {
        if (amount <= 0 || source == null) return;
        hits.addLast(new Hit(now, source, amount));
        while (hits.size() > MAX_HITS) hits.removeFirst();
    }

    /** Raport z trafien z okna przed smiercia; potem lista trafien jest czyszczona. */
    public Report build(String killer, long expLost, boolean blessed, long now) {
        Map<String, long[]> by = new LinkedHashMap<>();
        for (Hit h : hits) {
            if (h.timeMs() < now - WINDOW_MS) continue;
            long[] a = by.computeIfAbsent(h.source(), k -> new long[2]);
            a[0] += h.amount();
            a[1]++;
        }
        hits.clear();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : by.entrySet()) rows.add(new Row(e.getKey(), e.getValue()[0], (int) e.getValue()[1]));
        rows.sort((x, y) -> Long.compare(y.total(), x.total()));
        return new Report(now, killer, expLost, blessed, rows);
    }

    /** Wiersze okna informacyjnego {etykieta, opis}. */
    public static List<String[]> rows(Report r) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"Zabójca", r.killer()});
        out.add(new String[] {"Strata", r.expLost() + " exp" + (r.blessed() ? " (połowa dzięki błogosławieństwu)" : "")});
        for (Row row : r.rows()) {
            out.add(new String[] {row.source(), row.total() + " obrażeń w " + row.hits() + " " + hitsWord(row.hits())
                    + " (ostatnie " + WINDOW_MS / 1000 + " s)"});
        }
        return out;
    }

    static String hitsWord(int n) {
        if (n == 1) return "ciosie";
        return "ciosach";
    }

    /** Wiersze do komunikatu sieciowego: "etykieta;opis" (srednik w tekscie zamieniany na przecinek). */
    public static String[] encode(List<String[]> rows) {
        String[] out = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) out[i] = rows.get(i)[0].replace(';', ',') + ";" + rows.get(i)[1].replace(';', ',');
        return out;
    }

    public static List<String[]> decode(String[] fields, int from) {
        List<String[]> out = new ArrayList<>();
        for (int i = from; i < fields.length; i++) {
            String[] r = fields[i].split(";", 2);
            if (r.length == 2) out.add(r);
        }
        return out;
    }
}
