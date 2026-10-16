package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class WhatsNewTest {

    static final String LOG = "# Changelog\n\n## [0.2.0] - 2026-10-16\n\n### Dodano\n- Nowy `czar` dla druida.\n"
            + "- Bardzo długi punkt, który nie zmieści się w jednym wierszu okna, więc jego dalszy ciąg trafia do opisu,\n"
            + "  a reszta jest ucinana na końcu, żeby nie wyjść poza okno gry na telefonie.\n\n### Naprawiono\n- Błąd mapy.\n"
            + "## [0.1.0] - 2026-10-15\n- Start.\n";

    @Test
    void listsPointsOfTheNewestVersions() {
        assertEquals("0.2.0", WhatsNew.latestVersion(LOG));
        List<String[]> rows = WhatsNew.rows(LOG, 1, 10);
        assertEquals(3, rows.size(), "only the newest version");
        assertArrayEquals(new String[] {"0.2: Nowy czar dla druida.", ""}, rows.get(0));
        assertTrue(rows.get(1)[0].length() <= WhatsNew.MAX_LABEL);
        assertTrue(rows.get(1)[1].endsWith("..."), rows.get(1)[1]);
        assertTrue(rows.get(1)[1].length() <= WhatsNew.MAX_DETAIL);
        assertEquals("0.2: Błąd mapy.", rows.get(2)[0]);
        assertEquals(4, WhatsNew.rows(LOG, 5, 10).size());
        assertEquals(2, WhatsNew.rows(LOG, 5, 2).size());
        assertTrue(WhatsNew.rows(null, 3, 10).isEmpty());
    }

    @Test
    void theGameShipsItsChangelog() {
        String log = WhatsNew.load();
        assertNotNull(log, "CHANGELOG.md packed into the resources");
        assertNotNull(WhatsNew.latestVersion(log));
        assertFalse(WhatsNew.rows(log, 3, 18).isEmpty());
    }
}
