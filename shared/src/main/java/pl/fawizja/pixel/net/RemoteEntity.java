package pl.fawizja.pixel.net;

import pl.fawizja.pixel.world.Position;

/** Inny gracz albo potwor widziany w klatce stanu z serwera. kind = PL lub MO. */
public record RemoteEntity(String kind, int id, String key, String name, Position position, int healthPercent) {}
