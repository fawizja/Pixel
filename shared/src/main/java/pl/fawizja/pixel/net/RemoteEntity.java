package pl.fawizja.pixel.net;

import pl.fawizja.pixel.world.Position;

/**
 * Inny gracz albo potwor widziany w klatce stanu z serwera. kind = PL lub MO.
 * mark = czaszka PvP gracza (NONE, WHITE, RED); dla potworow NONE.
 */
public record RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent, String mark) {

    public RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent) {
        this(kind, id, key, name, position, healthPercent, "NONE");
    }
}
