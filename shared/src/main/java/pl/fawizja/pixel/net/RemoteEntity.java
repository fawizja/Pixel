package pl.fawizja.pixel.net;

import pl.fawizja.pixel.world.Position;

/**
 * Inny gracz albo potwor widziany w klatce stanu z serwera. kind = PL lub MO.
 * mark = czaszka PvP gracza (NONE, WHITE, RED); dla potworow NONE. guild = nazwa gildii gracza albo null.
 * outfit = kolor stroju gracza (Outfit, od 0.99.0; 0 = kolor profesji). pvpMode = gracz ma wlaczony tryb PvP
 * (czerwony przelacznik czaszki, pole "P" wiersza PL). harmony = punkty harmonii mnicha 0..5 (-1 = nie mnich albo
 * starszy serwer bez tego pola), serene = mnich w spokoju (pole harmonii wiersza PL: "-", "N" albo "NS").
 */
public record RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                           String guild, boolean mounted, String pet, String title, int outfit, boolean pvpMode,
                           int harmony, boolean serene, String access) {

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted, String pet, String title, int outfit, boolean pvpMode,
                        int harmony, boolean serene) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, pet, title, outfit, pvpMode, harmony, serene, null);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted, String pet, String title, int outfit, boolean pvpMode) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, pet, title, outfit, pvpMode, -1, false);
    }

    /** Pole harmonii wiersza PL: "-" bez harmonii, inaczej punkty i "S" przy spokoju (np. "3S"). */
    public static String harmonyField(int points, boolean serene) {
        if (points < 0) return "-";
        return Math.min(pl.fawizja.pixel.vocation.Harmony.MAX, points) + (serene ? "S" : "");
    }

    /** Punkty z pola harmonii (-1 = brak albo bledne pole). */
    public static int harmonyPoints(String f) {
        if (f == null || f.isEmpty() || f.equals("-")) return -1;
        String n = f.endsWith("S") ? f.substring(0, f.length() - 1) : f;
        try {
            return Math.max(0, Math.min(pl.fawizja.pixel.vocation.Harmony.MAX, Integer.parseInt(n)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Spokoj z pola harmonii. */
    public static boolean harmonySerene(String f) {
        return harmonyPoints(f) >= 0 && f.endsWith("S");
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted, String pet, String title, int outfit) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, pet, title, outfit, false);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted, String pet, String title) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, pet, title, 0);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted, String pet) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, pet, null);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild, boolean mounted) {
        this(kind, id, key, name, position, healthPercent, mark, guild, mounted, null);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                        String guild) {
        this(kind, id, key, name, position, healthPercent, mark, guild, false);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark) {
        this(kind, id, key, name, position, healthPercent, mark, null);
    }

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent) {
        this(kind, id, key, name, position, healthPercent, "NONE", null);
    }
}
