package pl.fawizja.pixel.inventory;

import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;

/**
 * Waga przedmiotow do udzwigu (cap) postaci. Jednostka to setna uncji (oz): udzwig postaci
 * ({@code PlayerCharacter#capacity()}) jest w uncjach, wiec limit to capacity * {@link #PER_OZ}.
 * Wagi wynikaja z kategorii przedmiotu (plik items.csv nie ma osobnej kolumny); plecak waży tyle co jego zawartosc
 * plus wlasna waga, wiec plecak w plecaku liczy sie rekurencyjnie.
 */
public final class ItemWeights {

    public static final int PER_OZ = 100;

    private ItemWeights() {}

    /** Waga jednej sztuki (bez zawartosci) w setnych uncji. */
    public static int unit(ItemRegistry registry, int itemId) {
        ItemType t = registry.get(itemId);
        if (ContainerType.ofItem(t.key()) != null) {
            return switch (ContainerType.ofItem(t.key())) {
                case BACKPACK -> 8 * PER_OZ;
                case TRAVELER -> 9 * PER_OZ;
                case EXPLORER -> 10 * PER_OZ;
            };
        }
        return switch (t.category()) {
            case CURRENCY -> PER_OZ / 100 + 1;        // 0,02 oz: 100 monet = 2 oz
            case WEAPON -> 12 * PER_OZ;
            case SHIELD -> 10 * PER_OZ;
            case ARMOR -> 12 * PER_OZ;
            case AMMUNITION -> PER_OZ / 10;           // 0,1 oz
            case POTION -> PER_OZ / 2;                // 0,5 oz
            case RUNE -> PER_OZ / 5;                  // 0,2 oz
            case TOOL -> 3 * PER_OZ;
            case LIGHT -> 2 * PER_OZ;
            case FOOD -> PER_OZ;
            case BAIT -> PER_OZ / 20;
            case MATERIAL -> PER_OZ / 2;
        };
    }

    /** Waga stosu razem z zawartoscia (plecak z przedmiotami). */
    public static long of(ItemRegistry registry, ItemStack s) {
        long w = (long) unit(registry, s.itemId()) * s.count();
        if (s.contents() != null) w += s.contents().weight();
        return w;
    }
}
