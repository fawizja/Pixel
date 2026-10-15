package pl.fawizja.pixel.save;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bezpieczny zapis plikow: zapis do pliku tymczasowego, fsync, kopia zapasowa
 * poprzedniej wersji i podmiana. Przy uszkodzonym pliku glownym odczyt korzysta z kopii.
 */
public final class SaveStore {

    public interface Decoder<T> {
        T decode(String text) throws SaveFormatException;
    }

    public record LoadResult<T>(T value, boolean fromBackup, String problem) {}

    private final File dir;

    public SaveStore(File dir) {
        this.dir = dir;
    }

    private File main(String slot) { return new File(dir, slot + ".save"); }
    private File backup(String slot) { return new File(dir, slot + ".save.bak"); }
    private File temp(String slot) { return new File(dir, slot + ".save.tmp"); }

    public static String sanitize(String slot) {
        String s = slot.replaceAll("[^A-Za-z0-9_-]", "_");
        if (s.isEmpty()) throw new IllegalArgumentException("empty slot");
        return s;
    }

    public synchronized void write(String slot, String content) throws IOException {
        String s = sanitize(slot);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = temp(s);
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        File main = main(s);
        File bak = backup(s);
        if (main.exists()) {
            if (bak.exists() && !bak.delete()) throw new IOException("cannot delete " + bak);
            if (!main.renameTo(bak)) throw new IOException("cannot back up " + main);
        }
        if (!tmp.renameTo(main)) {
            // przywroc poprzednia wersje, zeby nie zostac bez zapisu
            if (bak.exists()) bak.renameTo(main);
            throw new IOException("cannot move " + tmp + " to " + main);
        }
    }

    public synchronized <T> LoadResult<T> read(String slot, Decoder<T> decoder) throws IOException {
        String s = sanitize(slot);
        String problem = null;
        File main = main(s);
        if (main.exists()) {
            try {
                return new LoadResult<>(decoder.decode(readText(main)), false, null);
            } catch (SaveFormatException e) {
                problem = "main save rejected: " + e.getMessage();
            }
        }
        File bak = backup(s);
        if (bak.exists()) {
            try {
                return new LoadResult<>(decoder.decode(readText(bak)), true, problem);
            } catch (SaveFormatException e) {
                problem = (problem == null ? "" : problem + "; ") + "backup rejected: " + e.getMessage();
            }
        }
        return new LoadResult<>(null, false, problem);
    }

    /** Usuwa zapis i jego kopie (np. przy usunieciu konta). */
    public synchronized void delete(String slot) throws IOException {
        String s = sanitize(slot);
        java.nio.file.Files.deleteIfExists(main(s).toPath());
        java.nio.file.Files.deleteIfExists(backup(s).toPath());
    }

    public boolean exists(String slot) {
        String s = sanitize(slot);
        return main(s).exists() || backup(s).exists();
    }

    private static String readText(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
