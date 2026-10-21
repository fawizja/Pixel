package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreditsTest {
    @Test
    void listsLicenseAndEveryAuthor() {
        List<String[]> rows = Credits.rows();
        assertTrue(rows.get(0)[1].contains("CC BY 4.0"));
        StringBuilder all = new StringBuilder();
        for (String[] r : rows) all.append(r[1]).append(", ");
        for (String name : Credits.AUTHORS.split(", ")) assertTrue(all.toString().contains(name), name);
        assertEquals("Autorzy", rows.get(3)[0]);
        for (String[] r : rows) assertTrue(r[1].length() <= 60, r[1]);
    }
}
