package pl.fawizja.pixel.monster;

import pl.fawizja.pixel.world.Position;

/**
 * Miejsce odradzania: potwory pojawiaja sie w promieniu od srodka i wracaja tu po poscigu.
 * nightOnly - nocny spawn na powierzchni: potwory pojawiaja sie o zmroku i znikaja za dnia
 * (dopiero gdy nie walcza i zaden gracz ich nie widzi).
 * pack - stado w lepszym lowisku dla druzyn (znacznik "stado" w spawns.csv): wieksza grupa, a za kazdego potwora
 * {@link pl.fawizja.pixel.combat.CombatSystem#PACK_EXP_PERCENT}% doswiadczenia.
 */
public record SpawnPoint(String monsterKey, Position center, int radius, int count, int respawnSeconds, boolean nightOnly,
                         boolean pack) {
    public SpawnPoint {
        if (radius < 0 || count < 1 || respawnSeconds < 1) throw new IllegalArgumentException("bad spawn " + monsterKey);
        if (nightOnly && center.z() < 0) throw new IllegalArgumentException("night spawn underground " + monsterKey);
    }

    public SpawnPoint(String monsterKey, Position center, int radius, int count, int respawnSeconds, boolean nightOnly) {
        this(monsterKey, center, radius, count, respawnSeconds, nightOnly, false);
    }

    public SpawnPoint(String monsterKey, Position center, int radius, int count, int respawnSeconds) {
        this(monsterKey, center, radius, count, respawnSeconds, false, false);
    }
}
