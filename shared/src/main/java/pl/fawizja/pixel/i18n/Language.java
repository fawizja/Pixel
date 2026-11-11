package pl.fawizja.pixel.i18n;

import java.util.Locale;

/** Jezyki gry: angielski (domyslny dla nowych instalacji), polski i hiszpanski. */
public enum Language {
    EN("en", "English", Locale.ENGLISH),
    PL("pl", "Polski", Locale.forLanguageTag("pl")),
    ES("es", "Español", Locale.forLanguageTag("es"));

    /** Jezyk nowej instalacji (ustawienia bez zapisanego jezyka). */
    public static final Language DEFAULT = EN;

    public final String code;
    /** Nazwa w tym samym jezyku (do wyboru jezyka). */
    public final String label;
    public final Locale locale;

    Language(String code, String label, Locale locale) {
        this.code = code;
        this.label = label;
        this.locale = locale;
    }

    /** Jezyk z kodu ("en", "pl", "es", wielkosc liter dowolna); {@code def} dla nieznanego lub pustego. */
    public static Language byCode(String code, Language def) {
        if (code == null) return def;
        String c = code.trim();
        for (Language l : values()) if (l.code.equalsIgnoreCase(c)) return l;
        return def;
    }

    public Language next() { return values()[(ordinal() + 1) % values().length]; }
}
