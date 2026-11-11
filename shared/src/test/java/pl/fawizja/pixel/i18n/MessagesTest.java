package pl.fawizja.pixel.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @AfterEach
    void reset() { Messages.setLanguage(Language.PL); }

    @Test
    void everyLanguageHasTheSameKeys() {
        Set<String> pl = new TreeSet<>(Messages.keys(Language.PL));
        assertFalse(pl.isEmpty());
        for (Language l : Language.values()) {
            Set<String> other = new TreeSet<>(Messages.keys(l));
            Set<String> missing = new TreeSet<>(pl); missing.removeAll(other);
            Set<String> extra = new TreeSet<>(other); extra.removeAll(pl);
            assertTrue(missing.isEmpty(), l + " lacks keys: " + first(missing));
            assertTrue(extra.isEmpty(), l + " has unknown keys: " + first(extra));
        }
    }

    @Test
    void placeholdersMatchAcrossLanguages() {
        for (String k : Messages.keys(Language.PL)) {
            String pl = Messages.get(Language.PL, k);
            for (Language l : new Language[] {Language.EN, Language.ES}) {
                assertEquals(placeholders(pl), placeholders(Messages.get(l, k)), l + " " + k);
            }
        }
    }

    @Test
    void noEmptyTranslations() {
        for (Language l : Language.values())
            for (String k : Messages.keys(l))
                if (!Messages.get(Language.PL, k).isBlank()) assertFalse(Messages.get(l, k).isBlank(), l + " " + k);
    }

    @Test
    void fallbackToPolishForUnknownKey() {
        Messages.setLanguage(Language.ES);
        assertEquals("no.such.key", Messages.get("no.such.key"));
    }

    @Test
    void argumentsAreSubstitutedAndNumbersFormatted() {
        assertEquals("1,234", Messages.number(Language.EN, 1234));
        assertEquals("1 234", Messages.number(Language.PL, 1234));
        assertEquals("1.234.567", Messages.number(Language.ES, 1234567));
        assertEquals("-1,000", Messages.number(Language.EN, -1000));
        assertEquals("12", Messages.number(Language.EN, 12));
        Messages.setLanguage(Language.EN);
        assertEquals("1.5", Messages.decimal(1.5, 1));
        Messages.setLanguage(Language.ES);
        assertEquals("1,5", Messages.decimal(1.5, 1));
        assertEquals("a 1.000 b", Messages.format("a {0} b", new Object[] {1000L}));
    }

    @Test
    void trTranslatesPolishSourceTextPerLanguage() {
        String any = Messages.get(Language.PL, firstKeyWithDifferentTranslation());
        Messages.setLanguage(Language.PL);
        assertEquals(any, Messages.tr(any));
        Messages.setLanguage(Language.EN);
        assertNotEquals(any, Messages.tr(any));
        assertTrue(Messages.is(any, any));
    }

    @Test
    void runAsOverridesLanguageOnlyForTheCaller() {
        Messages.setLanguage(Language.PL);
        String k = firstKeyWithDifferentTranslation();
        String en = Messages.callAs(Language.EN, () -> Messages.get(k));
        assertEquals(Messages.get(Language.EN, k), en);
        assertEquals(Messages.get(Language.PL, k), Messages.get(k));
    }

    @Test
    void languageByCode() {
        assertEquals(Language.ES, Language.byCode("ES", Language.EN));
        assertEquals(Language.EN, Language.byCode("xx", Language.EN));
        assertEquals(Language.PL, Language.byCode(null, Language.PL));
        assertEquals(Language.EN, Language.DEFAULT);
    }

    @Test
    void mostKeysAreActuallyTranslated() {
        for (Language l : new Language[] {Language.EN, Language.ES}) {
            int same = 0, total = 0;
            for (String k : Messages.keys(Language.PL)) {
                total++;
                if (Messages.get(Language.PL, k).equals(Messages.get(l, k))) same++;
            }
            assertTrue(same < total * 0.5, l + ": " + same + " of " + total + " untranslated");
        }
    }

    private static String firstKeyWithDifferentTranslation() {
        for (String k : new TreeSet<>(Messages.keys(Language.PL))) {
            String pl = Messages.get(Language.PL, k);
            if (pl.length() > 12 && !pl.equals(Messages.get(Language.EN, k)) && Messages.tr(Language.PL, pl).equals(pl)) return k;
        }
        throw new AssertionError("no translated key");
    }

    private static String placeholders(String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(s);
        while (m.find()) out.add(m.group());
        return out.toString();
    }

    private static String first(Set<String> s) { return s.stream().limit(5).toList() + " (" + s.size() + ")"; }
}
