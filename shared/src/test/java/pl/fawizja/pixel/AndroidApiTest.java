package pl.fawizja.pixel;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Kod klienta dziala na Androidzie od API 30: metody Javy 9+ bez odpowiednika tam (D8 ich nie przenosi)
 * koncza sie NoSuchMethodError dopiero na telefonie. Ten test pilnuje znanych pulapek w shared i core.
 */
class AndroidApiTest {

    /** Wywolania nieobecne na Androidzie 11 (API 30). */
    static final String[] FORBIDDEN = {".readAllBytes()", ".readNBytes(", ".transferTo(System", "InputStream.nullInputStream"};

    @Test
    void clientCodeAvoidsMethodsMissingOnAndroid30() throws IOException {
        List<String> hits = new ArrayList<>();
        for (String root : new String[] {"src/main/java", "../core/src/main/java"}) {
            Path dir = Path.of(root);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String l = lines.get(i);
                        if (l.trim().startsWith("//") || l.trim().startsWith("*")) continue;
                        for (String bad : FORBIDDEN) if (l.contains(bad)) hits.add(f + ":" + (i + 1) + " " + bad);
                    }
                }
            }
        }
        assertEquals(List.of(), hits);
    }
}
