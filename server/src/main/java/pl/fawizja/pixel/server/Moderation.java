package pl.fawizja.pixel.server;

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
 * Moderacja serwera: administratorzy (plik {@code admins.txt} - imie w linii, edytowany przez operatora),
 * bany i wyciszenia zapisywane w {@code moderation.txt}. Czasy w milisekundach zegara sciennego
 * (bany przetrwaja restart); 0 = bez konca.
 */
final class Moderation {

    static final long FOREVER = 0L;

    private final File adminsFile;
    private final File stateFile;
    private final Set<String> admins = new LinkedHashSet<>();
    private final Map<String, Long> bans = new HashMap<>();
    private final Map<String, Long> mutes = new HashMap<>();

    Moderation(File saveDir) {
        this.adminsFile = new File(saveDir, "admins.txt");
        this.stateFile = new File(saveDir, "moderation.txt");
        load();
    }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }

    private void load() {
        admins.clear();
        bans.clear();
        mutes.clear();
        for (String line : lines(adminsFile)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) admins.add(key(t));
        }
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

    private static java.util.List<String> lines(File f) {
        try {
            return f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : java.util.List.of();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    private void save() throws IOException {
        StringBuilder sb = new StringBuilder("# typ;imie;do (ms, 0 = bez konca)\n");
        for (Map.Entry<String, Long> e : bans.entrySet()) sb.append("ban;").append(e.getKey()).append(';').append(e.getValue()).append('\n');
        for (Map.Entry<String, Long> e : mutes.entrySet()) sb.append("mute;").append(e.getKey()).append(';').append(e.getValue()).append('\n');
        File tmp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
        stateFile.getParentFile().mkdirs();
        Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    boolean isAdmin(String name) { return admins.contains(key(name)); }

    /** Dopisuje administratora do admins.txt (polecenie konsoli serwera). */
    void addAdmin(String name) throws IOException {
        if (!admins.add(key(name))) return;
        adminsFile.getParentFile().mkdirs();
        Files.write(adminsFile.toPath(), (name.trim() + "\n").getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
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
