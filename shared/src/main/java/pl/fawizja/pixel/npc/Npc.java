package pl.fawizja.pixel.npc;

import java.util.Objects;
import pl.fawizja.pixel.world.Position;

/** Postac niezalezna stojaca w miescie. shopKey tylko dla kupcow. */
public record Npc(String key, String name, NpcRole role, String town, Position position, String shopKey,
                  String greeting, String color) {

    /** Maksymalna odleglosc rozmowy (w kafelkach, przez lade). */
    public static final int TALK_RANGE = 2;

    public Npc {
        Objects.requireNonNull(key);
        Objects.requireNonNull(role);
        Objects.requireNonNull(position);
        if (role == NpcRole.MERCHANT && (shopKey == null || shopKey.isEmpty())) {
            throw new IllegalArgumentException("merchant without shop: " + key);
        }
    }
}
