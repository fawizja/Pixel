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
        assertTrue(all.toString().contains("OTClientV8 (MIT)"));
        for (String name : Credits.LPC_AUTHORS.split(", ")) assertTrue(all.toString().contains(name), name);
        assertTrue(all.toString().contains("CC BY-SA 3.0"));
    }

    /** Kazdy autor z assets/art/lpc/CREDITS-LPC.csv (warstwy postaci NPC) jest w oknie /autorzy. */
    @Test
    void listsEveryLpcLayerAuthor() throws java.io.IOException {
        java.nio.file.Path csv = null;
        for (String p : new String[] {"../" + Credits.LPC_FILE, Credits.LPC_FILE}) {
            if (java.nio.file.Files.exists(java.nio.file.Paths.get(p))) csv = java.nio.file.Paths.get(p);
        }
        assertTrue(csv != null, "brak " + Credits.LPC_FILE);
        String known = Credits.LPC_AUTHORS.toLowerCase(java.util.Locale.ROOT);
        List<String> lines = java.nio.file.Files.readAllLines(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(lines.size() > 1);
        for (String line : lines.subList(1, lines.size())) {
            String authors = csvFields(line).get(3);   // layer, npc_count, licenses, authors, urls
            for (String a : authors.split(",")) {
                assertTrue(known.contains(a.trim().toLowerCase(java.util.Locale.ROOT)), a.trim() + " (" + line + ")");
            }
        }
    }

    private static List<String> csvFields(String line) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
