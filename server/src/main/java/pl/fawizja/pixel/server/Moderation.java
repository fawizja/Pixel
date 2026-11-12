package pl.fawizja.pixel.server;

import pl.fawizja.pixel.admin.AccessLevel;
import pl.fawizja.pixel.i18n.Messages;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Moderacja serwera: poziomy dostepu (plik {@code admins.txt} - imie w linii = game master, "Imie:tutor" albo
 * "Imie:god" nadaje inny poziom; plik {@code gods.txt} - imie w linii = GOD; oba edytuje operator, nic nie jest
 * wpisane na stale w kodzie),
 * bany i wyciszenia zapisywane w {@code moderation.txt}. Czasy w milisekundach zegara sciennego
 * (bany przetrwaja restart); 0 = bez konca.
 */
final class Moderation {

    static final long FOREVER = 0L;

    private final File adminsFile;
    private final File stateFile;
    private final File godsFile;
    private final Map<String, AccessLevel> staff = new java.util.LinkedHashMap<>();
    private final Map<String, Long> bans = new HashMap<>();
    private final Map<String, Long> mutes = new HashMap<>();

    Moderation(File saveDir) {
        this.adminsFile = new File(saveDir, "admins.txt");
        this.godsFile = new File(saveDir, "gods.txt");
        this.stateFile = new File(saveDir, "moderation.txt");
        load();
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }

    private void load() {
        staff.clear();
        bans.clear();
        mutes.clear();
        for (String line : lines(adminsFile)) addStaff(line, AccessLevel.GAMEMASTER);
        for (String line : lines(godsFile)) addStaff(line, AccessLevel.GOD);
        for (String line : lines(stateFile)) {
            String[] f = line.split(";");
            if (f.length != 3) continue;
            try {
                long until = Long.parseLong(f[2].trim());
                if (f[0].equals("ban")) bans.put(key(f[1]), until);
                else if (f[0].equals("mute")) mutes.put(key(f[1]), until);
            } catch (NumberFormatException ignored) {
                // zla linia - pomijamy
            }
        }
    }

    /** Wiersz pliku poziomow: "Imie", "Imie:poziom" albo komentarz (#); wyzszy poziom wygrywa. */
    private void addStaff(String line, AccessLevel def) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#")) return;
        AccessLevel level = def;
        int colon = t.lastIndexOf(':');
        if (colon > 0) {
            AccessLevel parsed = AccessLevel.parse(t.substring(colon + 1));
            if (parsed != null && parsed != AccessLevel.PLAYER) {
                level = parsed;
                t = t.substring(0, colon).trim();
            }
        }
        if (t.isEmpty()) return;
        staff.merge(key(t), level, (a, b) -> a.atLeast(b) ? a : b);
    }

    /** Wczytuje ponownie admins.txt, gods.txt i moderation.txt (polecenie konsoli RELOAD - bez restartu serwera). */
    void reload() { load(); }

    private static java.util.List<String> lines(File f) {
        try {
            return f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : java.util.List.of();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    private void save() throws IOException {
        StringBuilder sb = new StringBuilder(Messages.get("moderation.typ_imie_do_ms"));
        for (Map.Entry<String, Long> e : bans.entrySet()) sb.append("ban;").append(e.getKey()).append(';').append(e.getValue()).append('\n');
        for (Map.Entry<String, Long> e : mutes.entrySet()) sb.append("mute;").append(e.getKey()).append(';').append(e.getValue()).append('\n');
        File tmp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        stateFile.getParentFile().mkdirs();
        Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Poziom dostepu postaci wg plikow operatora (zwykly gracz = PLAYER). */
    AccessLevel level(String name) { return staff.getOrDefault(key(name), AccessLevel.PLAYER); }

    /** Game master albo wyzej: polecenia moderacji (ADMIN ...). */
    boolean isAdmin(String name) { return level(name).atLeast(AccessLevel.GAMEMASTER); }

    boolean isGod(String name) { return level(name).atLeast(AccessLevel.GOD); }

    /** Dopisuje administratora (game mastera) do admins.txt (polecenie konsoli serwera). */
    void addAdmin(String name) throws IOException {
        if (level(name).atLeast(AccessLevel.GAMEMASTER)) return;
        staff.put(key(name), AccessLevel.GAMEMASTER);
        append(adminsFile, name.trim());
    }

    /** Dopisuje postac do gods.txt: poziom GOD (polecenie konsoli serwera). */
    void addGod(String name) throws IOException {
        if (isGod(name)) return;
        staff.put(key(name), AccessLevel.GOD);
        append(godsFile, name.trim());
    }

    /** Odbiera GOD (usuwa z gods.txt i z wpisow "Imie:god" w admins.txt); true, gdy postac byla GOD-em. */
    boolean removeGod(String name) throws IOException {
        boolean was = isGod(name);
        rewriteWithout(godsFile, name, false);
        rewriteWithout(adminsFile, name, true);
        load();
        return was;
    }

    private static void append(File f, String name) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), (name + "\n").getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /** Przepisuje plik bez linii tej postaci (godOnly: tylko wpisy poziomu god). */
    private void rewriteWithout(File f, String name, boolean godOnly) throws IOException {
        if (!f.isFile()) return;
        StringBuilder sb = new StringBuilder();
        for (String line : lines(f)) {
            String t = line.trim();
            String n = t;
            boolean god = !godOnly;
            int colon = t.lastIndexOf(':');
            if (colon > 0 && AccessLevel.parse(t.substring(colon + 1)) != null) {
                n = t.substring(0, colon).trim();
                god = AccessLevel.parse(t.substring(colon + 1)) == AccessLevel.GOD;
            }
            if (!t.startsWith("#") && key(n).equals(key(name)) && god) continue;
            sb.append(line).append('\n');
        }
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean active(Long until, long wallNow) {
        return until != null && (until == FOREVER || wallNow < until);
    }

    /** Czas konca bana (0 = bez konca) albo null, gdy gracz nie jest zbanowany. */
    Long bannedUntil(String name, long wallNow) {
        Long u = bans.get(key(name));
        return active(u, wallNow) ? u : null;
    }

    boolean isMuted(String name, long wallNow) { return active(mutes.get(key(name)), wallNow); }

    void ban(String name, long hours, long wallNow) throws IOException {
        bans.put(key(name), hours <= 0 ? FOREVER : wallNow + hours * 3_600_000L);
        save();
    }

    boolean unban(String name) throws IOException {
        boolean had = bans.remove(key(name)) != null;
        if (had) save();
        return had;
    }

    void mute(String name, long minutes, long wallNow) throws IOException {
        if (minutes <= 0) mutes.remove(key(name));
        else mutes.put(key(name), wallNow + minutes * 60_000L);
        save();
    }

    static String describeUntil(long until) {
        if (until == FOREVER) return "na stale";
        return "do " + java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
                .format(java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneOffset.UTC));
    }
}
