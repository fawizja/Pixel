package pl.fawizja.pixel.character;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.economy.Currency;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.vocation.Vocation;

/** Pochodnia w polu obok butow: zapalanie, wypalanie (tylko gdy plonie), zapis i migracja starych zapisow. */
class TorchTest {

    static final int TORCH = TestData.ITEMS.byKey("torch").id();

    /** Postac z pochodnia zalozona w polu LIGHT. */
    static PlayerCharacter withTorch(Vocation v) {
        PlayerCharacter pc = TestData.newCharacter(v);
        pc.backpack().add(ItemStack.of(TORCH));
        int slot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == TORCH) slot = i;
        }
        assertTrue(pc.equipment().equipFromBackpack(pc.backpack(), slot, pc.vocation(), pc.level()).ok());
        assertTrue(pc.hasTorchEquipped(TORCH));
        return pc;
    }

    @Test
    void torchGoesIntoItsOwnSlot() {
        assertEquals(EquipSlot.LIGHT, TestData.ITEMS.get(TORCH).slot());
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        assertFalse(pc.setTorchLit(true, TORCH), "a torch in the backpack cannot be lit");
        assertEquals(0, pc.torchRemainingMs(TORCH));
        PlayerCharacter eq = withTorch(Vocation.KNIGHT);
        assertEquals(PlayerCharacter.TORCH_BURN_MS, eq.torchRemainingMs(TORCH));
        assertEquals(20 * 60_000L, PlayerCharacter.TORCH_BURN_MS);
    }

    @Test
    void litTorchBurnsOutAfterTwentyMinutesAndDisappears() {
        PlayerCharacter pc = withTorch(Vocation.PALADIN);
        int inBackpack = pc.backpack().count(TORCH);
        assertTrue(pc.setTorchLit(true, TORCH));
        for (long t = 0; t < PlayerCharacter.TORCH_BURN_MS - 1000; t += 1000) {
            assertEquals(PlayerCharacter.TorchTick.NONE, pc.tickTorch(1000, TORCH));
        }
        assertEquals(1000, pc.torchRemainingMs(TORCH));
        assertEquals(PlayerCharacter.TorchTick.BURNED_OUT, pc.tickTorch(1000, TORCH));
        assertFalse(pc.isTorchLit());
        assertNull(pc.equipment().get(EquipSlot.LIGHT), "burned-out torch is gone");
        assertEquals(inBackpack, pc.backpack().count(TORCH), "no torch from the backpack is lit automatically");
        assertFalse(pc.setTorchLit(true, TORCH));
    }

    @Test
    void extinguishedTorchKeepsItsFuel() {
        PlayerCharacter pc = withTorch(Vocation.DRUID);
        pc.setTorchLit(true, TORCH);
        pc.tickTorch(5 * 60_000L, TORCH);
        pc.setTorchLit(false, TORCH);
        long left = pc.torchRemainingMs(TORCH);
        assertEquals(15 * 60_000L, left);
        // zgaszona przez godzine: paliwo stoi
        for (int i = 0; i < 60; i++) assertEquals(PlayerCharacter.TorchTick.NONE, pc.tickTorch(60_000L, TORCH));
        assertEquals(left, pc.torchRemainingMs(TORCH));
        assertNotNull(pc.equipment().get(EquipSlot.LIGHT));
        pc.setTorchLit(true, TORCH);
        assertEquals(left, pc.torchRemainingMs(TORCH), "relighting continues the same torch");
        pc.tickTorch(15 * 60_000L - 1, TORCH);
        assertTrue(pc.isTorchLit());
        assertEquals(PlayerCharacter.TorchTick.BURNED_OUT, pc.tickTorch(1, TORCH));
    }

    @Test
    void removedTorchGoesOut() {
        PlayerCharacter pc = withTorch(Vocation.SORCERER);
        pc.setTorchLit(true, TORCH);
        pc.equipment().unequipToBackpack(EquipSlot.LIGHT, pc.backpack());
        pc.tickTorch(1000, TORCH);
        assertFalse(pc.isTorchLit());
    }

    @Test
    void timersSurviveSaveAndLoad() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = withTorch(Vocation.SORCERER);
        pc.feed(5 * 60_000L);
        pc.addLightBuff(6, 3 * 60_000L);
        pc.setTorchLit(true, TORCH);
        pc.tickTorch(60_000L, TORCH);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.fedMs(), back.fedMs());
        assertEquals(6, back.lightBuffRadius());
        assertEquals(pc.lightBuffMs(), back.lightBuffMs());
        assertTrue(back.isTorchLit());
        assertTrue(back.hasTorchEquipped(TORCH));
        assertEquals(19 * 60_000L, back.torchRemainingMs(TORCH));
        // zgaszona pochodnia z czesciowym paliwem
        pc.setTorchLit(false, TORCH);
        PlayerCharacter off = codec.decode(codec.encode(pc));
        assertFalse(off.isTorchLit());
        assertEquals(19 * 60_000L, off.torchRemainingMs(TORCH));
    }

    // ------------------------------------------------------------------ migracja starych zapisow (v5)

    /** Zapis w formacie v5 z dopisanymi liniami (suma kontrolna przeliczona). */
    static String legacySave(PlayerCharacter pc, java.util.function.UnaryOperator<String> body) {
        String text = new CharacterCodec(TestData.ITEMS).encode(pc);
        int nl = text.indexOf('\n');
        int crcAt = text.lastIndexOf("crc=");
        String b = body.apply(text.substring(nl + 1, crcAt));
        CRC32 c = new CRC32();
        c.update(b.getBytes(StandardCharsets.UTF_8));
        return CharacterCodec.MAGIC + " 5\n" + b + "crc=" + Long.toHexString(c.getValue()) + "\n";
    }

    @Test
    void legacyLitBackpackTorchMovesIntoTheTorchSlot() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        pc.backpack().add(ItemStack.of(TORCH));
        int torches = pc.backpack().count(TORCH);
        String save = legacySave(pc, b -> b.replaceAll("timers=([^,]*),([^,]*),([^,]*),0,0", "timers=$1,$2,$3,1,1500000"));
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(save);
        assertTrue(back.hasTorchEquipped(TORCH));
        assertEquals(torches - 1, back.backpack().count(TORCH));
        assertTrue(back.isTorchLit());
        assertEquals(PlayerCharacter.TORCH_BURN_MS, back.torchRemainingMs(TORCH), "old 25 min fuel capped at 20 min");
    }

    @Test
    void legacyUnlitTorchStaysInTheBackpack() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        int torches = pc.backpack().count(TORCH);
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(legacySave(pc, b -> b));
        assertNull(back.equipment().get(EquipSlot.LIGHT));
        assertEquals(torches, back.backpack().count(TORCH));
        assertFalse(back.isTorchLit());
    }

    @Test
    void legacyTorchInTheAmmoSlotMovesToTheTorchSlot() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.equipment().setDirect(EquipSlot.AMMO, null);
        int torches = pc.backpack().count(TORCH);
        String save = legacySave(pc, b -> b + "eq.AMMO=" + TORCH + "x1\n");
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(save);
        assertTrue(back.hasTorchEquipped(TORCH));
        assertNull(back.equipment().get(EquipSlot.AMMO));
        assertEquals(torches, back.backpack().count(TORCH));
    }

    @Test
    void legacyFullCoinStacksAreConvertedOnLoad() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        long before = Money.totalValue(pc.backpack());
        int g = Currency.GOLD.itemId(), p = Currency.PLATINUM.itemId();
        // wolne pola na koncu plecaka: stare stosy po 100 zlotych i 100 platynowych
        String save = legacySave(pc, b -> b + "bp.17=" + g + "x100\nbp.18=" + g + "x100\nbp.19=" + p + "x100\n");
        PlayerCharacter back = new CharacterCodec(TestData.ITEMS).decode(save);
        assertEquals(before + 200 + 10_000, Money.totalValue(back.backpack()));
        assertTrue(back.backpack().count(g) < 100);
        assertTrue(back.backpack().count(p) < 100);
        assertTrue(back.backpack().count(Currency.CRYSTAL.itemId()) >= 1);
        assertEquals(pc.bank().balance(), back.bank().balance(), "bank balance (plain gp number) unchanged");
    }
}
