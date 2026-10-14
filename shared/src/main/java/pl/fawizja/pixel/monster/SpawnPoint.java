package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.world.Position;

/** Miejsce odradzania: potwory pojawiaja sie w promieniu od srodka i wracaja tu po poscigu. */
public record SpawnPoint(String monsterKey, Position center, int radius, int count, int respawnSeconds) {
    public SpawnPoint {
        if (radius < 0 || count < 1 || respawnSeconds < 1) throw new IllegalArgumentException("bad spawn " + monsterKey);
    }
}
