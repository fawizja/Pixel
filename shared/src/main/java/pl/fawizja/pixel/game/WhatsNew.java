package pl.fawizja.pixel.game;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * "Co nowego": wpisy z CHANGELOG.md dolaczonego do gry (zasob /data/CHANGELOG.md). Okno pokazuje
 * punkty ostatnich wersji; po aktualizacji gry otwiera sie samo raz.
 */
public final class WhatsNew {

    public static final String RESOURCE = "/data/CHANGELOG.md";
    /** Dlugosc etykiety i opisu wiersza okna (dluzszy punkt jest ucinany "..."). */
    public static final int MAX_LABEL = 38;
    public static final int MAX_DETAIL = 56;

    private WhatsNew() {}

    /** Tresc dziennika zmian z zasobow albo null. */
    public static String load() {
        try (InputStream in = WhatsNew.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            // bez InputStream.readAllBytes - nie ma go na Androidzie przed API 33
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Numer najnowszej wersji ("0.131.0") albo null. */
    public static String latestVersion(String changelog) {
        if (changelog == null) return null;
        for (String line : changelog.split("\n")) {
            String v = version(line);
            if (v != null) return v;
        }
        return null;
    }

    /** Wiersze {wersja, punkt} z {@code versions} najnowszych wersji, najwyzej {@code maxRows}. */
    public static List<String[]> rows(String changelog, int versions, int maxRows) {
        List<String[]> out = new ArrayList<>();
        if (changelog == null) return out;
        String current = null;
        int seen = 0;
        StringBuilder item = null;
        for (String raw : changelog.split("\n")) {
            String line = raw.replaceAll("\\s+$", "");
            String v = version(line);
            if (v != null || line.startsWith("- ") || line.startsWith("#") || line.isBlank()) {
                if (item != null) add(out, current, item.toString(), maxRows);
                item = null;
            }
            if (v != null) {
                if (++seen > versions) break;
                current = v;
            } else if (current != null && line.startsWith("- ")) {
                item = new StringBuilder(line.substring(2).trim());
            } else if (item != null && line.startsWith("  ")) {
                item.append(' ').append(line.trim());
            }
        }
        if (item != null && seen <= versions) add(out, current, item.toString(), maxRows);
        return out;
    }

    /** Wiersz okna: poczatek punktu w etykiecie (z wersja), dalszy ciag w opisie, reszta ucieta "...". */
    private static void add(List<String[]> out, String version, String text, int maxRows) {
        if (out.size() >= maxRows) return;
        String t = version.replaceAll("\\.0$", "") + ": " + text.replace("`", "").replace('„', '"').replace('”', '"');
        String label = cut(t, MAX_LABEL);
        String rest = t.substring(label.length()).trim();
        if (rest.length() > MAX_DETAIL) rest = cut(rest, MAX_DETAIL - 3) + "...";
        out.add(new String[] {label, rest});
    }

    /** Poczatek tekstu do max znakow, ciety na spacji. */
    private static String cut(String t, int max) {
        if (t.length() <= max) return t;
        int sp = t.lastIndexOf(' ', max);
        return t.substring(0, sp > max / 2 ? sp : max);
    }

    private static String version(String line) {
        if (!line.startsWith("## [")) return null;
        int end = line.indexOf(']');
        return end > 4 ? line.substring(4, end) : null;
    }
}
