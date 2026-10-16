package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.vocation.Vocation;

class OutfitTest {

    @Test
    void colorsAreFoundByNameWithOrWithoutPolishLettersOrByNumber() {
        assertEquals(1, Outfit.parse("Czerwony"));
        assertEquals(5, Outfit.parse("bialy"));
        assertEquals(5, Outfit.parse("biały"));
        assertEquals(7, Outfit.parse("ZŁOTY"));
        assertEquals(3, Outfit.parse("3"));
        assertEquals(0, Outfit.parse("profesji"));
        assertEquals(-1, Outfit.parse("tęczowy"));
        assertEquals(-1, Outfit.parse("99"));
        assertEquals(-1, Outfit.parse(null));
        assertEquals(Outfit.NAMES.length, Outfit.COLORS.length);
        assertNull(Outfit.COLORS[0], "0 keeps the vocation color");
    }

    @Test
    void theOutfitIsSavedAndBadValuesFallBackToTheVocationColor() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        assertEquals(0, pc.outfit());
        pc.setOutfit(6);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(6, back.outfit());
        pc.setOutfit(42);
        assertEquals(0, pc.outfit());
    }
}
