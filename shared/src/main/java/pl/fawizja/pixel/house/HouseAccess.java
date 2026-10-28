package pl.fawizja.pixel.house;

import java.util.Locale;

/**
 * Dostep postaci do domu (Darek 2026-10-28: "zapros pelny dostep to osoba moze robic wszystko przenosic otwierac
 * rzucac itemki zas sam dostep to tylko wejscie bez rzucania podnoszenia otwierania drzwi").
 * ENTRY: wejscie i chodzenie; FULL: takze przedmioty, skrzynie i drzwi; OWNER: wlasciciel.
 */
public enum HouseAccess {
    NONE, ENTRY, FULL, OWNER;

    /** Moze wejsc do wnetrza. */
    public boolean mayEnter() { return this != NONE; }

    /** Moze wyrzucac, podnosic i przesuwac przedmioty, otwierac skrzynie i drzwi. */
    public boolean mayHandle() { return this == FULL || this == OWNER; }

    /** Krotki kod do zapisu ("full", "entry"). */
    public String code() { return name().toLowerCase(Locale.ROOT); }

    /** Opis po polsku. */
    public String label() {
        switch (this) {
            case OWNER: return "właściciel";
            case FULL: return "pełny dostęp";
            case ENTRY: return "tylko wejście";
            default: return "brak dostępu";
        }
    }

    /** Poziom goscia z kodu; nieznany albo pusty kod = pelny dostep (stare zapisy nie mialy poziomu). */
    public static HouseAccess guestLevel(String code) {
        return code != null && code.trim().equalsIgnoreCase("entry") ? ENTRY : FULL;
    }

    /** Dowolny poziom z nazwy ("OWNER", "entry"...); nieznany = NONE. */
    public static HouseAccess parse(String code) {
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return NONE;
        }
    }
}
