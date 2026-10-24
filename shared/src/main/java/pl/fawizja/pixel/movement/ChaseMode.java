package pl.fawizja.pixel.movement;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.Monster;

/** Bieg wlaczony: postac sama podchodzi do zaznaczonego potwora i idzie za nim, az stanie obok. */
public final class ChaseMode {

    /** Dalej niz tyle pol postac nie goni celu (potwor poza ekranem). */
    public static final int RANGE = 12;

    private ChaseMode() {}

    /** true, gdy postac powinna isc do celu: bieg wlaczony, cel zywy, na tym pietrze, w zasiegu i jeszcze nie obok. */
    public static boolean wants(PlayerCharacter pc, Monster target) {
        if (pc == null || !pc.isRunning() || target == null || target.isDead()) return false;
        int d = pc.position().distanceTo(target.position());
        return d > 1 && d <= RANGE;
    }
}
