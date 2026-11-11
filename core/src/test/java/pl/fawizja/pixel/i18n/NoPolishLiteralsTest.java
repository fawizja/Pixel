package pl.fawizja.pixel.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Glowne klasy interfejsu klienta nie moga zawierac polskich literalow z diakrytykami: teksty ida przez
 * {@link Messages} (paczki messages_*.properties). Dozwolone sa polecenia czatu ("/..."), znaczniki protokolu i
 * literaly bedace zrodlem przekladu (tekst z polskiej paczki, tlumaczony przez {@code Messages.tr} przy uzyciu).
 */
class NoPolishLiteralsTest {

    private static final String DIACRITICS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ";

    /** Fragmenty literalow, ktore sa znacznikami protokolu albo kluczami danych, a nie tekstem dla gracza. */
    private static final Set<String> ALLOWED = Set.of(
            "[Gildia] ", "[Drużyna] ", "[Handel] ", "Drużyna", "Gildia", "Handel");

    @Test
    void clientUiHasNoPolishStringLiterals() throws IOException {
        Path root = Path.of("src/main/java/pl/fawizja/pixel/client");
        if (!Files.isDirectory(root)) root = Path.of("core").resolve(root);
        List<String> offenders = new ArrayList<>();
        Set<String> sources = new java.util.HashSet<>();
        for (String k : Messages.keys(Language.PL)) sources.add(Messages.get(Language.PL, k));
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String name = f.getFileName().toString();
                if (name.equals("PolishFont.java")) continue; // lista znakow czcionki
                String src = Files.readString(f);
                for (String lit : literals(src)) {
                    if (ALLOWED.contains(lit) || lit.startsWith("/")) continue; // protokol i polecenia czatu
                    if (sources.contains(lit)) continue; // zrodlo przekladu: przechodzi przez Messages.tr
                    if (hasDiacritic(lit)) offenders.add(name + ": " + lit);
                }
            }
        }
        assertTrue(offenders.isEmpty(), offenders.size() + " polskich literalow w UI klienta, np. "
                + offenders.stream().limit(15).toList());
    }

    static boolean hasDiacritic(String s) {
        for (int i = 0; i < s.length(); i++) if (DIACRITICS.indexOf(s.charAt(i)) >= 0) return true;
        return false;
    }

    /** Literaly napisowe z kodu Javy (z pominieciem komentarzy i znakow). */
    static List<String> literals(String s) {
        List<String> out = new ArrayList<>();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 1;
            } else if (c == '\'') {
                i++;
                if (i < n && s.charAt(i) == '\\') i++;
                while (i < n && s.charAt(i) != '\'') i++;
            } else if (c == '"') {
                if (s.startsWith("\"\"\"", i)) {
                    int e = s.indexOf("\"\"\"", i + 3);
                    if (e < 0) break;
                    out.add(s.substring(i + 3, e));
                    i = e + 2;
                } else {
                    StringBuilder b = new StringBuilder();
                    i++;
                    while (i < n && s.charAt(i) != '"') {
                        if (s.charAt(i) == '\\' && i + 1 < n) i++;
                        b.append(s.charAt(i));
                        i++;
                    }
                    out.add(b.toString());
                }
            }
        }
        return out;
    }
}
