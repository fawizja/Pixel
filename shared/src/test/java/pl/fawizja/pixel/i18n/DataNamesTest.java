package pl.fawizja.pixel.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.item.ItemType;

class DataNamesTest {

    @AfterEach
    void reset() { Messages.setLanguage(Language.PL); }

    private static List<String> keys(String csv, int col) throws Exception {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                DataNamesTest.class.getResourceAsStream("/data/" + csv), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) if (!l.isBlank() && !l.startsWith("#")) out.add(l.split(";", -1)[col]);
        }
        return out;
    }

    @Test
    void itemsMonstersSpellsAndQuestsHaveEnglishAndSpanishNames() throws Exception {
        String[][] kinds = {{"items.csv", "1", "item"}, {"monsters.csv", "0", "monster"}, {"spells.csv", "0", "spell"}, {"quests.csv", "0", "quest"}};
        for (String[] k : kinds)
            for (String key : keys(k[0], Integer.parseInt(k[1])))
                for (Language l : new Language[] {Language.EN, Language.ES})
                    assertTrue(DataNames.has(l, k[2], key, "name"), l + " " + k[2] + " " + key);
    }

    @Test
    void unknownKeyFallsBackToSourceName() {
        Messages.setLanguage(Language.ES);
        assertEquals("Nieznany", Messages.data("item", "no_such_item", "Nieznany"));
    }

    @Test
    void namesFollowTheLanguageAndPolishIsTheSource() {
        ItemType gold = pl.fawizja.pixel.TestData.ITEMS.byKey("gold_coin");
        Messages.setLanguage(Language.PL);
        assertEquals("Złota moneta", gold.name());
        Messages.setLanguage(Language.EN);
        assertEquals("Gold Coin", gold.name());
        Messages.setLanguage(Language.ES);
        assertEquals("Moneda de oro", gold.name());
        assertEquals("Złota moneta", gold.polishName());
        assertEquals("Moneda de oro", Messages.callAs(Language.ES, gold::name));
    }
}
