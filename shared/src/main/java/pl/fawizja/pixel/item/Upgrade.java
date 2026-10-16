package pl.fawizja.pixel.item;

/**
 * Ulepszanie wyposazenia u kupca (kowala): +1..+5. Bron zyskuje 6% ataku na poziom (co najmniej +1),
 * zbroja i tarcza +1 pancerza i obrony na poziom. Nieudana proba zabiera tylko zloto, przedmiot zostaje.
 */
public final class Upgrade {
    public static final int MAX = 5;
    private static final int[] CHANCE = {100, 90, 75, 55, 35};

    private Upgrade() {}

    /** Ulepszyc mozna pojedyncze wyposazenie z atakiem, obrona albo pancerzem. */
    public static boolean upgradable(ItemType t) {
        return t.slot() != EquipSlot.NONE && t.maxStack() == 1 && (t.attack() > 0 || t.defense() > 0 || t.armor() > 0);
    }

    /** Szansa (w procentach) na udane ulepszenie do poziomu next (1..MAX). */
    public static int chancePercent(int next) {
        if (next < 1 || next > MAX) return 0;
        return CHANCE[next - 1];
    }

    /** Cena proby ulepszenia do poziomu next: 2x wartosc przedmiotu razy poziom, co najmniej 200 gp razy poziom do kwadratu. */
    public static long price(ItemType t, int next) {
        return Math.max(200L * next * next, t.value() * next * 2);
    }

    public static int attack(ItemType t, int level) {
        if (t.attack() <= 0 || level <= 0) return t.attack();
        return t.attack() + Math.max(level, (int) Math.round(t.attack() * 0.06 * level));
    }

    public static int defense(ItemType t, int level) {
        return t.defense() > 0 ? t.defense() + level : t.defense();
    }

    public static int armor(ItemType t, int level) {
        return t.armor() > 0 ? t.armor() + level : t.armor();
    }

    /** Nazwa z poziomem ulepszenia, np. "Miecz +2". */
    public static String name(ItemType t, int level) {
        return level > 0 ? t.name() + " +" + level : t.name();
    }
}
