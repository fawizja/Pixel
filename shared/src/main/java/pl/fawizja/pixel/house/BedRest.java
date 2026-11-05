package pl.fawizja.pixel.house;

import pl.fawizja.pixel.character.PlayerCharacter;

/**
 * Sen w lozku jak w Tibii: postac, ktora wylogowala sie, kladac sie do lozka we wlasnym domu (albo w domu, do
 * ktorego ma wstep), po powrocie jest wypoczeta - odzyskuje zycie i mane za czas snu: {@link #PERCENT_PER_MINUTE}
 * procent na minute, pelnie po {@link #FULL_AFTER_MINUTES} minutach. Stamina odnawia sie po kazdym wylogowaniu
 * wg {@link pl.fawizja.pixel.character.Stamina#onLogin} (pierwsze 10 minut nic, potem minuta na 2 min 15 s snu,
 * powyzej 39:00 na 4 min 30 s) - sen w lozku nie zmienia tempa.
 */
public final class BedRest {

    /** Procent pelnego zycia i many na minute snu. */
    public static final int PERCENT_PER_MINUTE = 2;
    /** Po tylu minutach snu postac jest w pelni wypoczeta. */
    public static final int FULL_AFTER_MINUTES = 100 / PERCENT_PER_MINUTE;

    private BedRest() {}

    /**
     * Pobudka po zalogowaniu: odnowienie zycia i many za czas od zasniecia (zegar scienny) i wyzerowanie znacznika.
     * Zwraca komunikat po polsku albo null, gdy postac nie spala w lozku.
     */
    public static String wakeUp(PlayerCharacter pc, long nowWallMs) {
        long slept = pc.sleptAt();
        if (slept <= 0) return null;
        pc.setSleptAt(0);
        if (pc.isDead()) return null;
        long minutes = Math.max(0, nowWallMs - slept) / 60_000L;
        long percent = Math.min(100, minutes * PERCENT_PER_MINUTE);
        int hp = pc.heal((int) ((long) pc.maxHealth() * percent / 100));
        int mana = pc.restoreMana((int) ((long) pc.maxMana() * percent / 100));
        if (hp == 0 && mana == 0) {
            return minutes < 1 ? "Budzisz się w łóżku po krótkiej drzemce." : "Budzisz się w łóżku po " + minutes + " min snu.";
        }
        return "Budzisz się wypoczęty po " + Math.max(1, minutes) + " min snu w łóżku: +" + hp + " życia, +" + mana + " many.";
    }
}
