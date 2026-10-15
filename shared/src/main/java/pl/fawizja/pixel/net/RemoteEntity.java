package pl.fawizja.pixel.net;

import pl.fawizja.pixel.world.Position;

/**
 * Inny gracz albo potwor widziany w klatce stanu z serwera. kind = PL lub MO.
 * mark = czaszka PvP gracza (NONE, WHITE, RED); dla potworow NONE. guild = nazwa gildii gracza albo null.
 */
public record RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark,
                           String guild, boolean mounted) {

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
