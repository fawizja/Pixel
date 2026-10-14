package pl.fawizja.pixel.client.platform;

import java.io.File;
import java.util.Map;
import pl.fawizja.pixel.perf.Resource;

/** Uslugi zalezne od platformy (Android / desktop). */
public interface PlatformServices {

    /**
     * Wypelnia probke wykorzystania zasobow (0..1). Zasoby, ktorych platforma nie udostepnia
     * wiarygodnie, maja wartosc NaN - nie wolno ich zgadywac.
     */
    void sampleResources(Map<Resource, Float> out);

    /** Opis baterii do diagnostyki albo null, gdy niedostepny. */
    String batteryInfo();

    /** Opis stanu termicznego albo null, gdy niedostepny. */
    String thermalInfo();

    /** Katalog zapisu postaci (lokalny, prywatny dla aplikacji). */
    File saveDirectory();

    /** Czy platforma powinna sama ograniczac FPS (Android nie ma wbudowanego limitu). */
    boolean needsSoftwareFrameLimit();
}
