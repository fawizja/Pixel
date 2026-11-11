package pl.fawizja.pixel.perf;

import pl.fawizja.pixel.i18n.Messages;
/** Monitorowane zasoby. Wartosc to wykorzystanie 0..1 lub NaN, gdy platforma jej nie udostepnia. */
public enum Resource {
    APP_MEMORY("Pamięć aplikacji"),
    SYSTEM_MEMORY("Pamięć systemu"),
    CPU("CPU"),
    THERMAL("Temperatura"),
    FRAME_TIME("Czas klatki");

    private final String label;

    Resource(String label) { this.label = label; }

    public String label() { return Messages.tr(label); }
}
