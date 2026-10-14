package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

/** Paliwo pochodni i zapis stanu ulotnego (sytosc, swiatlo z czaru, pochodnia). */
class TorchTest {

    static final int TORCH = TestData.ITEMS.byKey("torch").id();

    @Test
    void litTorchBurnsOutAndTheNextOneIsLit() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.backpack().add(new ItemStack(TORCH, 1)); // 2 pochodnie (1 z zestawu startowego)
        int start = pc.backpack().count(TORCH);
        assertTrue(pc.setTorchLit(true, TORCH));
        long t = 0;
        while (t < PlayerCharacter.TORCH_BURN_MS - 1000) {
            assertEquals(PlayerCharacter.TorchTick.NONE, pc.tickTorch(1000, TORCH));
            t += 1000;
        }
        assertEquals(PlayerCharacter.TorchTick.NEXT_LIT, pc.tickTorch(1000, TORCH));
        assertEquals(start - 1, pc.backpack().count(TORCH));
        assertTrue(pc.isTorchLit());
        PlayerCharacter.TorchTick last = PlayerCharacter.TorchTick.NONE;
        for (int i = 0; i < PlayerCharacter.TORCH_BURN_MS / 1000 && pc.isTorchLit(); i++) last = pc.tickTorch(1000, TORCH);
        if (start - 1 == 1) {
            assertEquals(PlayerCharacter.TorchTick.BURNED_OUT, last);
            assertFalse(pc.isTorchLit());
            assertEquals(0, pc.backpack().count(TORCH));
        }
    }

    @Test
    void extinguishedTorchKeepsItsFuel() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.setTorchLit(true, TORCH);
        pc.tickTorch(10 * 60_000L, TORCH);
        pc.setTorchLit(false, TORCH);
        long left = pc.torchBurnMs();
        assertEquals(PlayerCharacter.TORCH_BURN_MS - 10 * 60_000L, left);
        assertEquals(PlayerCharacter.TorchTick.NONE, pc.tickTorch(60 * 60_000L, TORCH));
        pc.setTorchLit(true, TORCH);
        assertEquals(left, pc.torchBurnMs(), "relighting continues the same torch");
    }

    @Test
    void timersSurviveSaveAndLoad() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.feed(5 * 60_000L);
        pc.addLightBuff(6, 3 * 60_000L);
        pc.setTorchLit(true, TORCH);
        pc.tickTorch(60_000L, TORCH);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.fedMs(), back.fedMs());
        assertEquals(6, back.lightBuffRadius());
        assertEquals(pc.lightBuffMs(), back.lightBuffMs());
        assertTrue(back.isTorchLit());
        assertEquals(pc.torchBurnMs(), back.torchBurnMs());
    }
}
