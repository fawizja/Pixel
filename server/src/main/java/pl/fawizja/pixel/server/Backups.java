package pl.fawizja.pixel.server;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Kopie zapasowe katalogu serwera (postacie, konta, certyfikat) jako archiwa zip w
 * {@code <katalog>/backups/}. Pliki tymczasowe i same kopie sa pomijane; zostaje {@link #KEEP} najnowszych.
 */
final class Backups {
    private static final Logger LOG = Logger.getLogger("pixel.server");
    static final int KEEP = 7;
    static final long EVERY_MS = 6 * 3_600_000L;

    private final File dir;
    private final File target;

    Backups(File saveDir) {
        this.dir = saveDir;
        this.target = new File(saveDir, "backups");
    }

    /** Tworzy archiwum i usuwa najstarsze ponad limit. Zwraca utworzony plik. */
    File create() throws IOException {
        Files.createDirectories(target.toPath());
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File zip = new File(target, "pixel-" + stamp + ".zip");
        for (int i = 1; zip.exists(); i++) zip = new File(target, "pixel-" + stamp + "-" + i + ".zip");
        File tmp = new File(target, zip.getName() + ".tmp");
        Path root = dir.toPath();
        try (OutputStream os = Files.newOutputStream(tmp.toPath()); ZipOutputStream out = new ZipOutputStream(os);
             Stream<Path> files = Files.walk(root)) {
            List<Path> list = new ArrayList<>();
            files.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(target.toPath()))
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .forEach(list::add);
            for (Path p : list) {
                byte[] data;
                try {
                    data = Files.readAllBytes(p);
                } catch (IOException e) {
                    continue; // plik zniknal w trakcie (zapis atomowy) - kolejna kopia go zlapie
                }
                out.putNextEntry(new ZipEntry(root.relativize(p).toString().replace(File.separatorChar, '/')));
                out.write(data);
                out.closeEntry();
            }
        }
        Files.move(tmp.toPath(), zip.toPath(), StandardCopyOption.ATOMIC_MOVE);
        prune();
        return zip;
    }

    private void prune() {
        File[] all = target.listFiles((d, n) -> n.startsWith("pixel-") && n.endsWith(".zip"));
        if (all == null || all.length <= KEEP) return;
        Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < all.length - KEEP; i++) {
            if (!all[i].delete()) LOG.warning("nie można usunąć starej kopii " + all[i]);
        }
    }

    File[] list() {
        File[] all = target.listFiles((d, n) -> n.endsWith(".zip"));
        return all == null ? new File[0] : all;
    }

    /** Watek tworzacy kopie przy starcie i co EVERY_MS. */
    Thread startSchedule() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    File z = create();
                    LOG.info("Kopia zapasowa: " + z.getName());
                    Thread.sleep(EVERY_MS);
                } catch (InterruptedException e) {
                    return;
                } catch (IOException | RuntimeException e) {
                    LOG.warning("Kopia zapasowa nieudana: " + e.getMessage());
                    try {
                        Thread.sleep(600_000L);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }, "pixel-backup");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
