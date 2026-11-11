package pl.fawizja.pixel.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.client.ui.PolishFont;

/** Czcionka ma glify dla wszystkich znakow z paczek jezykowych (ñ á é í ó ú ü ¿ ¡ i polskie litery). */
class FontCoverageTest {

    private static Set<Integer> glyphs() throws Exception {
        Set<Integer> ids = new HashSet<>();
        try (InputStream in = FontCoverageTest.class.getClassLoader().getResourceAsStream(PolishFont.FNT)) {
            String s = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            Matcher m = Pattern.compile("char id=(\\d+)").matcher(s);
            while (m.find()) ids.add(Integer.parseInt(m.group(1)));
        }
        for (char c : PolishFont.ADDED.toCharArray()) ids.add((int) c);
        return ids;
    }

    private static String read(String path) throws Exception {
        try (InputStream in = FontCoverageTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyCharacterOfEveryBundleHasAGlyph() throws Exception {
        Set<Integer> ids = glyphs();
        for (char c : "ñáéíóúüÑÁÉÍÓÚÜ¿¡".toCharArray()) assertTrue(ids.contains((int) c), "brak glifu: " + c);
        Set<Character> missing = new TreeSet<>();
        for (String f : new String[] {"messages_en.properties", "messages_es.properties", "data_en.csv", "data_es.csv"}) {
            for (char c : read("/i18n/" + f).toCharArray())
                if (c > 126 && c != '\n' && !ids.contains((int) c)) missing.add(c);
        }
        assertTrue(missing.isEmpty(), "znaki bez glifu w czcionce: " + missing);
    }
}
