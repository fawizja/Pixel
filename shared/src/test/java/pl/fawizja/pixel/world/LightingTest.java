package pl.fawizja.pixel.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

class LightingTest {

    @Test
    void undergroundGetsDarkerButNeverBlack() {
        float prev = Lighting.ambientForFloor(0, 1f);
        for (int z = -1; z >= -5; z--) {
            float a = Lighting.ambientForFloor(z, 1f);
            assertTrue(a < prev);
            assertTrue(a >= Lighting.MIN_UNDERGROUND_AMBIENT);
            prev = a;
        }
        assertEquals(0.5f, Lighting.ambientForFloor(3, 0.5f), 1e-6);
    }

    @Test
    void torchRequiresItemAndExtendsLight() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        int torch = TestData.ITEMS.byKey("torch").id();
        assertEquals(Lighting.BASE_PLAYER_GLOW, Lighting.playerLightRadius(pc, TestData.ITEMS));
        assertFalse(pc.setTorchLit(true, torch), "torch only in the backpack");
        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.LIGHT, pl.fawizja.pixel.item.ItemStack.of(torch));
        assertTrue(pc.setTorchLit(true, torch));
        assertEquals(5f, Lighting.playerLightRadius(pc, TestData.ITEMS));
        pc.equipment().setDirect(pl.fawizja.pixel.item.EquipSlot.LIGHT, null);
        assertEquals(Lighting.BASE_PLAYER_GLOW, Lighting.playerLightRadius(pc, TestData.ITEMS), "torch gone");
        assertFalse(pc.setTorchLit(true, torch));
        assertTrue(pc.setTorchLit(false, torch));
    }
}
