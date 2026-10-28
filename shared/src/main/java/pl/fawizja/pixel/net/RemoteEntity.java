package pl.fawizja.pixel.net;

import pl.fawizja.pixel.world.Position;

/**
 * Inny gracz albo potwor widziany w klatce stanu z serwera. kind = PL lub MO.
 * mark = czaszka PvP gracza (NONE, WHITE, RED); dla potworow NONE. guild = nazwa gildii gracza albo null.
 * outfit = kolor stroju gracza (Outfit, od 0.99.0; 0 = kolor profesji). pvpMode = gracz ma wlaczony tryb PvP
 * (czerwony przelacznik czaszki, pole "P" wiersza PL).
 */
public record RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                           String guild, boolean mounted, String pet, String title, int outfit, boolean pvpMode) {

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
