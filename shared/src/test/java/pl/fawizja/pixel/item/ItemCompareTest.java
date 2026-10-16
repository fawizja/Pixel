package pl.fawizja.pixel.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class ItemCompareTest {

    private final ItemRegistry items = TestData.ITEMS;

    @Test
    void statsShowTheDifferenceToTheWornItem() {
        ItemType sword = items.byKey("iron_sword");
        ItemType training = items.byKey("training_sword");
        String s = ItemCompare.stats(sword, training);
        assertTrue(s.startsWith(sword.name()), s);
        assertTrue(s.contains("atak " + sword.attack() + " (+" + (sword.attack() - training.attack()) + ")"), s);
        assertFalse(ItemCompare.stats(sword, null).contains("("), "nothing worn - no difference");
        assertTrue(ItemCompare.stats(training, sword).contains("(-" + (sword.attack() - training.attack()) + ")"));
        assertTrue(ItemCompare.stats(sword, sword).endsWith("(założone)"));
    }

    @Test
    void requirementsMarkWhatTheCharacterLacks() {
        PlayerCharacter druid = TestData.newCharacter(Vocation.DRUID);
        String r = ItemCompare.requirements(items.byKey("iron_sword"), druid);
        assertTrue(r.contains("poziom 15 (za niski)"), r);
        assertTrue(r.contains("(nie dla ciebie)"), r);
        assertTrue(r.contains("wartość"), r);
        PlayerCharacter paladin = TestData.newCharacter(Vocation.PALADIN);
        assertFalse(ItemCompare.requirements(items.byKey("training_sword"), paladin).contains("nie dla ciebie"));
    }

    @Test
    void shopRowsShowWhatChangesAgainstTheWornItem() {
        PlayerCharacter knight = TestData.newCharacter(Vocation.PALADIN);
        ItemType sword = items.byKey("iron_sword");
        ItemType training = items.byKey("training_sword");
        String d = ItemCompare.shopDelta(sword, training, 0, knight);
        assertTrue(d.startsWith("atak +" + (sword.attack() - training.attack())), d);
        assertTrue(d.endsWith("od poz. 15"), d);
        assertEquals("jak założone", ItemCompare.shopDelta(training, training, 0, knight));
        assertEquals("", ItemCompare.shopDelta(items.byKey("health_potion"), null, 0, knight));
        assertEquals("nie dla ciebie", ItemCompare.shopDelta(sword, null, 0, TestData.newCharacter(Vocation.DRUID)));
    }
}
