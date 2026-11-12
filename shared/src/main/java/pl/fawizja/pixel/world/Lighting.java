package pl.fawizja.pixel.world;

import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemRegistry;

/**
 * Zasady oswietlenia niezalezne od renderera. Podziemia sa ciemniejsze z glebokoscia,
 * prawie czarne poza swiatlami (pochodnie, lawa, kryształy), ale nigdy calkowicie, a postac zawsze ma minimalna poswiate - loch ma byc
 * grywalny bez maksymalnej jasnosci ekranu.
 */
public final class Lighting {

    /** Promien poswiaty postaci bez pochodni (w kafelkach). */
    public static final float BASE_PLAYER_GLOW = 2.5f;
    public static final float MIN_UNDERGROUND_AMBIENT = 0.04f;
    /** Jasnosc otoczenia pierwszego podziemia: prawie ciemno jak w Tibii - widac tylko swiatlo postaci, pochodni i lawy. */
    public static final float UNDERGROUND_AMBIENT = 0.10f;

    private Lighting() {}

    /** Jasnosc otoczenia dla pietra: powierzchnia i wyzej zalezy od pory dnia. */
    public static float ambientForFloor(int z, float surfaceAmbient) {
        if (z >= 0) return surfaceAmbient;
        float a = UNDERGROUND_AMBIENT - 0.012f * (-z - 1);
        return Math.max(MIN_UNDERGROUND_AMBIENT, a);
    }

    /** Promien swiatla postaci: zapalona pochodnia albo podstawowa poswiata. */
    public static float playerLightRadius(PlayerCharacter pc, ItemRegistry items) {
        float r = Math.max(BASE_PLAYER_GLOW, pc.lightBuffRadius());
        int torch = items.byKey("torch").id();
        if (pc.isTorchLit() && pc.hasTorchEquipped(torch)) {
            r = Math.max(r, items.get(torch).lightRadius());
        }
        return r;
    }
}
