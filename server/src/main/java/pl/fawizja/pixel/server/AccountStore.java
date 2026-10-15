package pl.fawizja.pixel.server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Konta graczy: haslo nigdy nie jest zapisywane, tylko skrot PBKDF2-HMAC-SHA256 z losowa sola
 * (osobny plik na konto, poza repozytorium - w katalogu zapisow serwera). Pierwsze logowanie
 * nowym imieniem zaklada konto. Po {@link #MAX_FAILURES} blednych probach konto jest blokowane
 * na {@link #LOCK_MS}.
 */
final class AccountStore {

    enum Result { CREATED, OK, WRONG_PASSWORD, LOCKED, BAD_PASSWORD }

    static final int MIN_PASSWORD = 6;
    static final int MAX_PASSWORD = 64;
    static final int ITERATIONS = 60_000;
    static final int MAX_FAILURES = 5;
    static final long LOCK_MS = 60_000L;

    private final File dir;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, int[]> failures = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();

    AccountStore(File saveDir) {
        this.dir = new File(saveDir, "accounts");
    }

    boolean exists(String name) { return file(name).isFile(); }

    /** Logowanie albo zalozenie konta. now - czas serwera (do blokady po blednych probach). */
    Result login(String name, String password, long now) {
        String key = key(name);
        Long lock = lockedUntil.get(key);
        if (lock != null && now < lock) return Result.LOCKED;
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            return Result.BAD_PASSWORD;
        }
        File f = file(name);
        try {
            if (!f.isFile()) {
                create(f, password);
                return Result.CREATED;
            }
            if (matches(f, password)) {
                failures.remove(key);
                lockedUntil.remove(key);
                return Result.OK;
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("konto " + name + ": " + e.getMessage(), e);
        }
        return failed(key, now);
    }

    /** Zmiana hasla po podaniu obecnego (te same blokady co przy logowaniu). */
    Result changePassword(String name, String oldPassword, String newPassword, long now) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD || newPassword.length() > MAX_PASSWORD) {
            return Result.BAD_PASSWORD;
        }
        Result r = verify(name, oldPassword, now);
        if (r != Result.OK) return r;
        try {
            create(file(name), newPassword);
        } catch (IOException e) {
            throw new IllegalStateException("konto " + name + ": " + e.getMessage(), e);
        }
        return Result.OK;
    }

    /** Usuniecie konta po podaniu hasla. Zapis postaci usuwa wywolujacy. */
    Result delete(String name, String password, long now) {
        Result r = verify(name, password, now);
        if (r != Result.OK) return r;
        try {
            Files.deleteIfExists(file(name).toPath());
        } catch (IOException e) {
            throw new IllegalStateException("konto " + name + ": " + e.getMessage(), e);
        }
        return Result.OK;
    }

    /** Sprawdzenie hasla istniejacego konta (bez zakladania nowego). */
    private Result verify(String name, String password, long now) {
        String key = key(name);
        Long lock = lockedUntil.get(key);
        if (lock != null && now < lock) return Result.LOCKED;
        File f = file(name);
        if (!f.isFile() || password == null) return failed(key, now);
        try {
            if (matches(f, password)) {
                failures.remove(key);
                return Result.OK;
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("konto " + name + ": " + e.getMessage(), e);
        }
        return failed(key, now);
    }

    private Result failed(String key, long now) {
        int[] count = failures.computeIfAbsent(key, k -> new int[1]);
        if (++count[0] >= MAX_FAILURES) {
            count[0] = 0;
            lockedUntil.put(key, now + LOCK_MS);
        }
        return Result.WRONG_PASSWORD;
    }

    private static boolean matches(File f, String password) throws IOException {
        String[] parts = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split(";");
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) throw new IOException("bad account file");
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        return MessageDigest.isEqual(expected, hash(password, salt, iterations));
    }

    private void create(File f, String password) throws IOException {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String line = "pbkdf2-sha256;" + ITERATIONS + ";" + Base64.getEncoder().encodeToString(salt) + ";"
                + Base64.getEncoder().encodeToString(hash(password, salt, ITERATIONS)) + "\n";
        Files.createDirectories(dir.toPath());
        File tmp = new File(dir, f.getName() + ".tmp");
        Files.write(tmp.toPath(), line.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static byte[] hash(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private File file(String name) { return new File(dir, key(name).replace(' ', '_').replace('\'', '-') + ".acc"); }

    private static String key(String name) { return name.trim().toLowerCase(Locale.ROOT); }
}
