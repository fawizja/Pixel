package pl.fawizja.pixel.perf;

public enum GuardMode {
    /** Standardowa praca. */
    NORMAL,
    /** Ograniczanie zuzycia po przekroczeniu progu (domyslnie 90%). */
    LIMITED,
    /** Stopniowe przywracanie po spadku ponizej progu wznowienia (domyslnie 75%). */
    RECOVERING
}
