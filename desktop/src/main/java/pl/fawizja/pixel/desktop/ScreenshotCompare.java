package pl.fawizja.pixel.desktop;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Regresja GUI (etap 16): porownuje zrzuty ekranu z wzorcami w tools/gui-regression/reference.
 * Oba obrazy sa zmniejszane do polowy (srednia 2x2), a piksel liczy sie jako rozny, gdy ktorys kanal
 * rozni sie o wiecej niz {@link #CHANNEL_TOLERANCE}. Scena przechodzi, gdy roznych pikseli jest
 * mniej niz podany prog (potwory sie ruszaja, a sterowniki graficzne roznie wygladzaja krawedzie).
 *
 * Uzycie: compare &lt;wzorce&gt; &lt;zrzuty&gt; &lt;katalog-roznic&gt; &lt;prog&gt; | update &lt;wzorce&gt; &lt;zrzuty&gt;
 */
public final class ScreenshotCompare {

    static final int CHANNEL_TOLERANCE = 40;

    private ScreenshotCompare() { }

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("update")) {
            update(new File(args[1]), new File(args[2]));
            return;
        }
        if (args.length != 5 || !args[0].equals("compare")) {
            System.err.println("compare <wzorce> <zrzuty> <roznice> <prog> | update <wzorce> <zrzuty>");
            System.exit(2);
        }
        int failed = compare(new File(args[1]), new File(args[2]), new File(args[3]), Double.parseDouble(args[4]));
        System.exit(failed == 0 ? 0 : 1);
    }

    static void update(File refDir, File shotDir) throws IOException {
        refDir.mkdirs();
        for (File shot : pngs(shotDir)) {
            ImageIO.write(halve(ImageIO.read(shot)), "png", new File(refDir, shot.getName()));
            System.out.println("wzorzec: " + shot.getName());
        }
    }

    /** Zwraca liczbe scen, ktore nie przeszly. */
    static int compare(File refDir, File shotDir, File diffDir, double maxRatio) throws IOException {
        int failed = 0;
        File[] refs = pngs(refDir);
        if (refs.length == 0) {
            System.out.println("BLAD: brak wzorcow w " + refDir);
            return 1;
        }
        for (File ref : refs) {
            File shot = new File(shotDir, ref.getName());
            if (!shot.isFile()) {
                System.out.println("BLAD " + ref.getName() + ": brak zrzutu");
                failed++;
                continue;
            }
            BufferedImage expected = ImageIO.read(ref);
            BufferedImage actual = halve(ImageIO.read(shot));
            double ratio = diffRatio(expected, actual);
            boolean ok = ratio <= maxRatio;
            System.out.printf("%s %s: %.2f%% roznych pikseli (prog %.2f%%)%n",
                    ok ? "OK  " : "BLAD", ref.getName(), ratio * 100, maxRatio * 100);
            if (!ok) {
                failed++;
                diffDir.mkdirs();
                ImageIO.write(actual, "png", new File(diffDir, "actual_" + ref.getName()));
                if (sameSize(expected, actual)) ImageIO.write(diffImage(expected, actual), "png", new File(diffDir, "diff_" + ref.getName()));
            }
        }
        return failed;
    }

    /** Odsetek pikseli, ktore roznia sie bardziej niz tolerancja; 1.0 przy innym rozmiarze. */
    static double diffRatio(BufferedImage a, BufferedImage b) {
        if (!sameSize(a, b)) return 1.0;
        long diff = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (differs(a.getRGB(x, y), b.getRGB(x, y))) diff++;
            }
        }
        return diff / (double) ((long) a.getWidth() * a.getHeight());
    }

    static BufferedImage halve(BufferedImage src) {
        int w = Math.max(1, src.getWidth() / 2);
        int h = Math.max(1, src.getHeight() / 2);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, bl = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = src.getRGB(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
                        r += (p >> 16) & 0xff;
                        g += (p >> 8) & 0xff;
                        bl += p & 0xff;
                    }
                }
                out.setRGB(x, y, ((r / 4) << 16) | ((g / 4) << 8) | (bl / 4));
            }
        }
        return out;
    }

    private static BufferedImage diffImage(BufferedImage a, BufferedImage b) {
        BufferedImage out = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                int grey = (((p >> 16) & 0xff) + ((p >> 8) & 0xff) + (p & 0xff)) / 9;
                out.setRGB(x, y, differs(p, b.getRGB(x, y)) ? 0xff2020 : (grey << 16) | (grey << 8) | grey);
            }
        }
        return out;
    }

    private static boolean differs(int p, int q) {
        return Math.abs(((p >> 16) & 0xff) - ((q >> 16) & 0xff)) > CHANNEL_TOLERANCE
                || Math.abs(((p >> 8) & 0xff) - ((q >> 8) & 0xff)) > CHANNEL_TOLERANCE
                || Math.abs((p & 0xff) - (q & 0xff)) > CHANNEL_TOLERANCE;
    }

    private static boolean sameSize(BufferedImage a, BufferedImage b) {
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    }

    private static File[] pngs(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png"));
        if (files == null) return new File[0];
        Arrays.sort(files);
        return files;
    }
}
