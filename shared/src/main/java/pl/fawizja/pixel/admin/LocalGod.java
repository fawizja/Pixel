package pl.fawizja.pixel.admin;

import java.io.File;

/**
 * Gra jako GOD w trybie lokalnym (bez serwera). Domyslnie wylaczone; wlacza je wlasciciel urzadzenia:
 * <ul>
 * <li>opcja uruchomienia {@code -Dpixel.god=true} (np. {@code JAVA_OPTS="-Dpixel.god=true"} dla wersji desktop),</li>
 * <li>zmienna srodowiskowa {@code PIXEL_GOD=1},</li>
 * <li>pusty plik {@code god.enabled} w katalogu zapisow gry (na Androidzie to katalog prywatny aplikacji, niedostepny
 * bez roota ani adb - zwykly gracz tego nie wlaczy).</li>
 * </ul>
 * Poziom nie jest zapisywany w postaci i nigdy nie dziala online: tam o GOD-zie decyduje wylacznie serwer.
 */
public final class LocalGod {

    public static final String PROPERTY = "pixel.god";
    public static final String ENV = "PIXEL_GOD";
    public static final String FILE = "god.enabled";

    private LocalGod() {}

    public static boolean enabled(File saveDir) {
        String p = System.getProperty(PROPERTY);
        if (p != null) return p.equalsIgnoreCase("true") || p.equals("1");
        String e = System.getenv(ENV);
        if (e != null && (e.equalsIgnoreCase("true") || e.equals("1"))) return true;
        return saveDir != null && new File(saveDir, FILE).isFile();
    }

    /** Poziom dla gry lokalnej: GOD, gdy wlaczony, inaczej zwykly gracz. */
    public static AccessLevel level(File saveDir) {
        return enabled(saveDir) ? AccessLevel.GOD : AccessLevel.PLAYER;
    }
}
