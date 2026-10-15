package pl.fawizja.pixel.npc;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import pl.fawizja.pixel.magic.SpellRegistry;

/**
 * Rozmowy z NPC slowami kluczowymi (data/dialogues.csv). Wypowiedz gracza pasuje do linii, gdy zawiera
 * ktores z jej slow (bez wielkosci liter i polskich znakow). Linia konkretnego NPC wygrywa z linia roli,
 * a ta z linia wspolna dla wszystkich.
 */
public final class DialogueRegistry {

    /** Jedna linia: kto (klucz NPC, "@ROLA" albo "*"), slowa kluczowe i odpowiedz. */
    /** {@code topic} - pierwsze slowo kluczowe w oryginalnej pisowni (z polskimi znakami), do pokazania graczowi. */
    public record Line(String who, List<String> keywords, String answer, String topic) { }

    public static final int MAX_TEXT = 60;

    private final List<Line> lines;

    private DialogueRegistry(List<Line> lines) { this.lines = lines; }

    private static DialogueRegistry defaults;

    /** Wspolny egzemplarz z danych gry (wczytany raz, tylko do odczytu). */
    public static synchronized DialogueRegistry defaults() {
        if (defaults == null) defaults = loadDefault();
        return defaults;
    }

    public static DialogueRegistry empty() { return new DialogueRegistry(new ArrayList<>()); }

    public static DialogueRegistry loadDefault() {
        try {
            return parse(SpellRegistry.readLines("/data/dialogues.csv"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read dialogues", e);
        }
    }

    public static DialogueRegistry parse(List<String> raw) throws IOException {
        List<Line> out = new ArrayList<>();
        int n = 0;
        for (String l : raw) {
            n++;
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] f = t.split(";", 3);
            if (f.length != 3 || f[2].isBlank()) throw new IOException("dialogues.csv line " + n + ": expected 3 fields");
            String who = f[0].trim();
            if (who.startsWith("@")) {
                try {
                    NpcRole.valueOf(who.substring(1));
                } catch (IllegalArgumentException e) {
                    throw new IOException("dialogues.csv line " + n + ": unknown role " + who, e);
                }
            }
            List<String> kws = new ArrayList<>();
            for (String k : f[1].split(",")) {
                String nk = normalize(k);
                if (!nk.isEmpty()) kws.add(nk);
            }
            if (kws.isEmpty()) throw new IOException("dialogues.csv line " + n + ": no keywords");
            out.add(new Line(who, kws, f[2].trim(), f[1].split(",")[0].trim().toLowerCase(Locale.ROOT)));
        }
        return new DialogueRegistry(out);
    }

    /** Male litery, bez polskich znakow i interpunkcji, pojedyncze spacje. */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.replace('ł', 'l').replace('Ł', 'L');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").trim().replaceAll(" +", " ");
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    private static int rank(Line line, Npc npc) {
        if (line.who().equals(npc.key())) return 3;
        if (line.who().equals("@" + npc.role().name())) return 2;
        if (line.who().equals("*")) return 1;
        return 0;
    }

    private static boolean matches(Line line, String said) {
        String padded = " " + said + " ";
        String[] words = said.split(" ");
        for (String k : line.keywords()) {
            if (padded.contains(" " + k + " ")) return true;
            if (k.indexOf(' ') < 0 && k.length() >= 5) {
                for (String w : words) if (inflected(w, k)) return true;
            }
        }
        return false;
    }

    /** Odmiana: praca / prace / pracy, kopalnia / kopalni (ten sam temat, inna koncowka o 1-2 litery). */
    static boolean inflected(String word, String keyword) {
        String stem = keyword.substring(0, keyword.length() - 1);
        return word.startsWith(stem) && word.length() <= keyword.length() + 1;
    }

    /** Odpowiedz NPC albo null, gdy nic nie pasuje. */
    public String answer(Npc npc, String text) {
        String said = normalize(text);
        if (said.isEmpty()) return null;
        Line best = null;
        int bestRank = 0;
        for (Line line : lines) {
            int r = rank(line, npc);
            if (r > bestRank && matches(line, said)) {
                best = line;
                bestRank = r;
            }
        }
        if (best == null) return null;
        return best.answer().replace("{imie}", npc.name()).replace("{miasto}", npc.town())
                .replace("{powitanie}", npc.greeting());
    }

    /** Tematy rozmowy (pierwsze slowo kazdej linii), najpierw wlasne tematy NPC. */
    public List<String> topics(Npc npc) {
        Set<String> out = new LinkedHashSet<>();
        for (int r = 3; r >= 1; r--) {
            for (Line line : lines) if (rank(line, npc) == r) out.add(line.topic());
        }
        return new ArrayList<>(out);
    }
}
