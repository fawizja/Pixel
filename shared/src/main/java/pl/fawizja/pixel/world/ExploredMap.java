package pl.fawizja.pixel.world;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Odkryta mapa postaci (mgla na minimapie i duzej mapie, jak w Tibii): bit na kafelek, osobno dla kazdego pietra.
 * Pola zapisane sa w kawalkach 64 x 64 (64 slowa long = 512 B), tworzonych dopiero przy pierwszym odkryciu -
 * nowa postac zajmuje kilka kawalkow, cale 11 pieter swiata 1000 x 1000 najwyzej ok. 1,4 MB.
 *
 * <p>Postac odkrywa to, co widzi: okno gry 15 x 11 wokol siebie i pas jednego pola dookola
 * ({@link #VIEW_X} x {@link #VIEW_Y} w kazda strone). Zapis: deflate + base64 (pole "fog" zapisu postaci).
 *
 * <p>Rysowanie (minimapa) sledzi zmiany bez kopiowania maski: {@link #revision()} rosnie przy kazdym odkryciu,
 * a ostatnie prostokaty odkryc trzyma maly bufor cykliczny ({@link #dirtyTotal()}, {@link #dirty(long, int[])}).
 */
public final class ExploredMap {

    /** Zasieg odkrywania wokol postaci: 15 x 11 widoku gry plus pas jednego pola (17 x 13). */
    public static final int VIEW_X = 8, VIEW_Y = 6;
    public static final int MIN_Z = -7, MAX_Z = 7;
    static final int CHUNK_SHIFT = 6, CHUNK = 1 << CHUNK_SHIFT, CHUNKS = 16, MAX_XY = CHUNK * CHUNKS;
    private static final int WORDS = CHUNK * CHUNK / 64;
    private static final int FORMAT = 1;

    /** [z - MIN_Z][cy * CHUNKS + cx] -> 64 slowa (wiersz kafelkow = jedno slowo), null = nic nie odkryto. */
    private final long[][][] floors = new long[MAX_Z - MIN_Z + 1][][];
    private boolean all;
    private boolean legacyHome;
    private int revision;
    private int lastX = Integer.MIN_VALUE, lastY, lastZ;
    private String encoded;
    private int encodedRevision = -1;

    private static final int RING = 64;
    /** Bufor ostatnich odkryc: z, x0, y0, x1, y1 (wlacznie); pelny bufor = przebudowa u odbiorcy. */
    private final int[] ring = new int[RING * 5];
    private long dirtyTotal;

    public boolean isExplored(int x, int y, int z) {
        if (all) return true;
        if (x < 0 || y < 0 || x >= MAX_XY || y >= MAX_XY || z < MIN_Z || z > MAX_Z) return false;
        long[][] f = floors[z - MIN_Z];
        if (f == null) return false;
        long[] c = f[(y >> CHUNK_SHIFT) * CHUNKS + (x >> CHUNK_SHIFT)];
        return c != null && (c[y & (CHUNK - 1)] >>> (x & (CHUNK - 1)) & 1L) != 0;
    }

    public boolean isExplored(Position p) { return isExplored(p.x(), p.y(), p.z()); }

    /** Czy pietro ma choc jeden kawalek z odkrytymi polami w kwadracie kawalka (cx, cy) - szybkie pomijanie mgly. */
    public boolean chunkTouched(int cx, int cy, int z) {
        if (all) return true;
        if (cx < 0 || cy < 0 || cx >= CHUNKS || cy >= CHUNKS || z < MIN_Z || z > MAX_Z) return false;
        long[][] f = floors[z - MIN_Z];
        return f != null && f[cy * CHUNKS + cx] != null;
    }

    /** Odkrywa widok wokol pola (bez pracy, gdy postac stoi w tym samym miejscu). true = cos nowego. */
    public boolean revealView(Position p) {
        if (p.x() == lastX && p.y() == lastY && p.z() == lastZ) return false;
        lastX = p.x();
        lastY = p.y();
        lastZ = p.z();
        return reveal(p.x() - VIEW_X, p.y() - VIEW_Y, p.x() + VIEW_X, p.y() + VIEW_Y, p.z());
    }

    /** Odkrywa prostokat (x0, y0) - (x1, y1) wlacznie na pietrze z. true = choc jedno pole bylo nieodkryte. */
    public boolean reveal(int x0, int y0, int x1, int y1, int z) {
        if (z < MIN_Z || z > MAX_Z) return false;
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(MAX_XY - 1, x1);
        y1 = Math.min(MAX_XY - 1, y1);
        if (x1 < x0 || y1 < y0) return false;
        int zi = z - MIN_Z;
        if (floors[zi] == null) floors[zi] = new long[CHUNKS * CHUNKS][];
        long[][] f = floors[zi];
        boolean changed = false;
        for (int y = y0; y <= y1; y++) {
            int cy = y >> CHUNK_SHIFT, row = y & (CHUNK - 1);
            for (int cx = x0 >> CHUNK_SHIFT; cx <= x1 >> CHUNK_SHIFT; cx++) {
                int a = Math.max(x0, cx << CHUNK_SHIFT) & (CHUNK - 1);
                int b = Math.min(x1, (cx << CHUNK_SHIFT) + CHUNK - 1) & (CHUNK - 1);
                long mask = (b == 63 ? -1L : (1L << (b + 1)) - 1) & (-1L << a);
                int ci = cy * CHUNKS + cx;
                long[] c = f[ci];
                if (c == null) f[ci] = c = new long[WORDS];
                if ((c[row] & mask) != mask) {
                    c[row] |= mask;
                    changed = true;
                }
            }
        }
        if (changed) {
            revision++;
            markDirty(z, x0, y0, x1, y1);
        }
        return changed;
    }

    private void markDirty(int z, int x0, int y0, int x1, int y1) {
        int i = (int) (dirtyTotal % RING) * 5;
        ring[i] = z;
        ring[i + 1] = x0;
        ring[i + 2] = y0;
        ring[i + 3] = x1;
        ring[i + 4] = y1;
        dirtyTotal++;
    }

    /** Liczba wszystkich zapisanych prostokatow odkryc (odbiorca trzyma swoj kursor). */
    public long dirtyTotal() { return dirtyTotal; }

    /** Prostokat odkrycia nr n do out {z, x0, y0, x1, y1}; false, gdy wypadl juz z bufora (trzeba przebudowac). */
    public boolean dirty(long n, int[] out) {
        if (n < 0 || n >= dirtyTotal || n < dirtyTotal - RING) return false;
        System.arraycopy(ring, (int) (n % RING) * 5, out, 0, 5);
        return true;
    }

    /** Licznik zmian (odkrycia, wczytanie, odkrycie wszystkiego). */
    public int revision() { return revision; }

    /** Tryb deweloperski (-Dpixel.revealMap=1): cala mapa odkryta, bez zapisu. */
    public void revealAll() {
        all = true;
        revision++;
        dirtyTotal += RING + 1; // odbiorcy przebudowuja wszystko
    }

    public boolean allRevealed() { return all; }

    /** Zapis sprzed mgly (v6 i starsze): sesja odkryje okolice swiatyni miasta postaci. */
    public void markLegacy() { legacyHome = true; }

    public boolean takeLegacy() {
        boolean l = legacyHome;
        legacyHome = false;
        return l;
    }

    /** Liczba odkrytych pol na pietrze (testy, statystyki). */
    public int count(int z) {
        if (z < MIN_Z || z > MAX_Z || floors[z - MIN_Z] == null) return 0;
        int n = 0;
        for (long[] c : floors[z - MIN_Z]) {
            if (c != null) for (long w : c) n += Long.bitCount(w);
        }
        return n;
    }

    /** Pamiec masek w bajtach (zaalokowane kawalki). */
    public int memoryBytes() {
        int n = 0;
        for (long[][] f : floors) {
            if (f == null) continue;
            for (long[] c : f) if (c != null) n += WORDS * 8;
        }
        return n;
    }

    public boolean isEmpty() {
        for (long[][] f : floors) {
            if (f == null) continue;
            for (long[] c : f) {
                if (c == null) continue;
                for (long w : c) if (w != 0) return false;
            }
        }
        return true;
    }

    public void clear() {
        java.util.Arrays.fill(floors, null);
        all = false;
        lastX = Integer.MIN_VALUE;
        revision++;
        dirtyTotal += RING + 1;
    }

    /** Caly zapis (deflate + base64); pusty napis, gdy nic nie odkryto. Wynik zapamietany do nastepnej zmiany. */
    public String encode() {
        if (encodedRevision != revision || encoded == null) {
            List<String> parts = encodeParts(Integer.MAX_VALUE);
            encoded = parts.isEmpty() ? "" : parts.get(0);
            encodedRevision = revision;
        }
        return encoded;
    }

    /**
     * Zapis podzielony na czesci po najwyzej maxChunks kawalkow (komunikaty sieciowe ograniczone dlugoscia linii:
     * 64 kawalki to najwyzej ~44 KB base64). Kazda czesc da sie wczytac osobno przez {@link #merge(String)}.
     */
    public List<String> encodeParts(int maxChunks) {
        List<String> out = new ArrayList<>();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        int inPart = 0;
        raw.write(FORMAT);
        for (int zi = 0; zi < floors.length; zi++) {
            long[][] f = floors[zi];
            if (f == null) continue;
            for (int ci = 0; ci < f.length; ci++) {
                long[] c = f[ci];
                if (c == null || isZero(c)) continue;
                raw.write(zi + MIN_Z);
                raw.write(ci >> 8);
                raw.write(ci);
                for (long w : c) {
                    for (int s = 56; s >= 0; s -= 8) raw.write((int) (w >>> s));
                }
                if (++inPart >= maxChunks) {
                    out.add(pack(raw.toByteArray()));
                    raw.reset();
                    raw.write(FORMAT);
                    inPart = 0;
                }
            }
        }
        if (inPart > 0) out.add(pack(raw.toByteArray()));
        return out;
    }

    private static boolean isZero(long[] c) {
        for (long w : c) if (w != 0) return false;
        return true;
    }

    private static String pack(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 8));
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** Dopisuje (OR) odkrycia z zapisu albo czesci zapisu; pusty napis/null nic nie zmienia. */
    public void merge(String text) {
        if (text == null || text.isBlank()) return;
        byte[] data = inflate(Base64.getDecoder().decode(text.trim()));
        if (data.length < 1 || data[0] != FORMAT) throw new IllegalArgumentException("unknown fog format");
        int rec = 3 + WORDS * 8;
        if ((data.length - 1) % rec != 0) throw new IllegalArgumentException("truncated fog data");
        for (int p = 1; p < data.length; p += rec) {
            int z = data[p];
            int ci = (data[p + 1] & 0xff) << 8 | data[p + 2] & 0xff;
            if (z < MIN_Z || z > MAX_Z || ci >= CHUNKS * CHUNKS) throw new IllegalArgumentException("bad fog chunk");
            int zi = z - MIN_Z;
            if (floors[zi] == null) floors[zi] = new long[CHUNKS * CHUNKS][];
            long[] c = floors[zi][ci];
            if (c == null) floors[zi][ci] = c = new long[WORDS];
            for (int i = 0; i < WORDS; i++) {
                long w = 0;
                for (int b = 0; b < 8; b++) w = w << 8 | data[p + 3 + i * 8 + b] & 0xff;
                c[i] |= w;
            }
        }
        revision++;
        dirtyTotal += RING + 1;
    }

    private static byte[] inflate(byte[] in) {
        Inflater inf = new Inflater();
        inf.setInput(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 4);
        byte[] buf = new byte[8192];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw new IllegalArgumentException("truncated fog data");
                out.write(buf, 0, n);
                if (out.size() > 4 * 1024 * 1024) throw new IllegalArgumentException("fog data too large");
            }
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("bad fog data", e);
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }
}
