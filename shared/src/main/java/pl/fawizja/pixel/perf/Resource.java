package pl.fawizja.pixel.perf;

/** Monitorowane zasoby. Wartosc to wykorzystanie 0..1 lub NaN, gdy platforma jej nie udostepnia. */
public enum Resource {
    APP_MEMORY("Pamiec aplikacji"),
    SYSTEM_MEMORY("Pamiec systemu"),
    CPU("CPU"),
    THERMAL("Temperatura"),
    FRAME_TIME("Czas klatki");

    private final String label;

    Resource(String label) { this.label = label; }

    public String label() { return label; }
}
