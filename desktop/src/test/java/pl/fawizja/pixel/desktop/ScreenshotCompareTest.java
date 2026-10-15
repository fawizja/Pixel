package pl.fawizja.pixel.desktop;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ScreenshotCompareTest {

    private static BufferedImage filled(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void smallNoisePassesButAMovedPanelFails() throws Exception {
        BufferedImage ref = filled(40, 40, 0x336633);
        BufferedImage noisy = filled(40, 40, 0x3a6a3a); // drobna roznica odcienia (sterownik)
        assertEquals(0.0, ScreenshotCompare.diffRatio(ref, noisy));

        BufferedImage panel = filled(40, 40, 0x336633);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) panel.setRGB(x, y, 0x101010);
        assertEquals(0.25, ScreenshotCompare.diffRatio(ref, panel), 1e-9);
        assertEquals(1.0, ScreenshotCompare.diffRatio(ref, filled(20, 40, 0x336633)), "different size");
    }

    @Test
    void updateThenCompareRoundTripsAndReportsMissingShots() throws Exception {
        File root = Files.createTempDirectory("gui-reg").toFile();
        File shots = new File(root, "shots");
        File refs = new File(root, "refs");
        shots.mkdirs();
        ImageIO.write(filled(80, 60, 0x204080), "png", new File(shots, "town.png"));
        ScreenshotCompare.update(refs, shots);
        BufferedImage stored = ImageIO.read(new File(refs, "town.png"));
        assertEquals(40, stored.getWidth(), "references are stored at half size");
        assertEquals(0, ScreenshotCompare.compare(refs, shots, new File(root, "diff"), 0.02));

        assertTrue(new File(shots, "town.png").delete());
        assertEquals(1, ScreenshotCompare.compare(refs, shots, new File(root, "diff"), 0.02));
    }
}
