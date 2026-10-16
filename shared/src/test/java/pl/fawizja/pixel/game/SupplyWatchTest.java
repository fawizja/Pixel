package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.item.ItemStack;

class SupplyWatchTest {
    @Test
    void warnsOnlyWhenSuppliesRunLowWhileHunting() {
        int hp = TestData.ITEMS.byKey("health_potion").id();
        Container bag = new Container(TestData.ITEMS, 20);
        SupplyWatch w = new SupplyWatch(TestData.ITEMS);
        bag.add(new ItemStack(hp, 7));
        assertEquals(List.of(), w.check(bag, true), "first look only remembers");
        bag.remove(hp, 1);
        assertEquals(List.of(), w.check(bag, true), "6 left - still fine");
        bag.remove(hp, 1);
        assertEquals(List.of("Zostało tylko 5: Mikstura zdrowia."), w.check(bag, true));
        bag.remove(hp, 1);
        assertEquals(List.of(), w.check(bag, true), "warned once");
        bag.remove(hp, 4);
        assertEquals(List.of("Skończyło się: Mikstura zdrowia."), w.check(bag, true));

        bag.add(new ItemStack(hp, 10));
        assertEquals(List.of(), w.check(bag, true), "buying is quiet");
        bag.remove(hp, 10);
        assertEquals(List.of(), w.check(bag, false), "depot in town is quiet");
    }
}
