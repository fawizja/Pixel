package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.Forge;
import pl.fawizja.pixel.item.Imbuement;
import pl.fawizja.pixel.item.Imbuements;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.market.Market;
import pl.fawizja.pixel.vocation.Vocation;

/** Kuznia i nasycenia u kowala (ForgeService przez GameSession i Actions). */
class ForgeServiceTest {

    @BeforeAll
    static void load() throws Exception { TownServicesTest.load(); }

    static ItemType firstOfClass(int c, EquipSlot slot) {
        for (ItemType t : TestData.ITEMS.all()) if (Forge.classOf(t) == c && t.slot() == slot) return t;
        throw new AssertionError("no item of class " + c + " in " + slot);
    }

    static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    GameSession atSmith(PlayerCharacter pc) {
        GameSession s = new TownServicesTest().session(pc, List.of());
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_smith")), 1000);
        return s;
    }

    static int slotOf(PlayerCharacter pc, int itemId, int tier) {
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == itemId && st.tier() == tier) return i;
        }
        return -1;
    }

    @Test
    void onlySmithsAreSmiths() {
        assertTrue(TownServicesTest.npcs.get("varden_smith").isSmith());
        assertFalse(TownServicesTest.npcs.get("varden_merchant").isSmith());
        int smiths = 0;
        for (Npc n : TownServicesTest.npcs.all()) if (n.isSmith()) smiths++;
        assertEquals(6, smiths, "the forge is at all six smiths");

        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = new TownServicesTest().session(pc, List.of());
        s.teleport(TownServicesTest.talkSpot(TownServicesTest.npcs.get("varden_merchant")), 1000);
        pc.restoreForge(100, 100);
        assertFalse(s.forgeConvert("slivers", 1100), "a general merchant has no forge");
        assertEquals(100, pc.forgeDust());
        assertTrue(s.log().latest(3).stream().anyMatch(e -> e.text().contains("kowal")));
    }

    @Test
    void fusionIsPaidAndAtomic() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = atSmith(pc);
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        pc.backpack().add(new ItemStack(w.id(), 1));
        pc.backpack().add(new ItemStack(w.id(), 1));
        int a = slotOf(pc, w.id(), 0);
        int b = a + 1;
        while (pc.backpack().get(b) == null || pc.backpack().get(b).itemId() != w.id()) b++;
        assertEquals(1, s.forge().fusionPairs().size());

        // bez pylu i zlota nic sie nie zmienia
        assertFalse(s.forgeFuse(a, b, false, false, 1100));
        assertEquals(2, pc.backpack().count(w.id()));

        assertTrue(pc.bank().credit(100_000_000).ok());
        long gold = s.forge().funds();
        int successes = 0, failures = 0;
        for (int round = 0; round < 24; round++) {
            for (int k = 0; k < pc.backpack().size(); k++) {
                ItemStack st = pc.backpack().get(k);
                if (st != null && st.itemId() == w.id()) pc.backpack().takeFromSlot(k, 1);
            }
            pc.backpack().remove(id(Forge.CORE_KEY), pc.backpack().count(id(Forge.CORE_KEY)));
            pc.backpack().add(new ItemStack(w.id(), 1));
            pc.backpack().add(new ItemStack(w.id(), 1));
            pc.backpack().add(new ItemStack(id(Forge.CORE_KEY), 2));
            pc.restoreForge(225, 225);
            int[] pr = s.forge().fusionPairs().get(0);
            long before = s.forge().funds();
            boolean ok = Actions.apply(s, "npc:forge:fuse:" + pr[0] + ":" + pr[1] + ":1:1", 2000 + round * 100L);
            assertTrue(ok);
            if (hasTierAbove(pc, w.id(), 0)) {
                successes++;
                int count = pc.backpack().count(w.id());
                assertTrue(count == 1 || count == 2, "the second item is consumed unless a bonus kept it");
            } else {
                failures++;
                assertEquals(225 - Forge.FUSION_DUST, pc.forgeDust(), "a failure always costs the dust");
                assertEquals(0, pc.backpack().count(id(Forge.CORE_KEY)), "and the cores");
                assertEquals(before - Forge.fusionGold(w, 1), s.forge().funds(), "and the gold");
                assertTrue(pc.backpack().count(w.id()) >= 1, "the first item survives");
            }
        }
        assertTrue(successes > 0 && failures > 0, successes + " / " + failures);
        assertTrue(s.forge().funds() < gold, "fusion costs gold");
        assertTrue(s.log().latest(60).stream().anyMatch(e -> e.text().startsWith("Kuźnia: fuzja udana")));
    }

    static boolean hasTierAbove(PlayerCharacter pc, int itemId, int tier) {
        for (ItemStack st : pc.backpack().contents()) if (st != null && st.itemId() == itemId && st.tier() > tier) return true;
        return false;
    }

    @Test
    void fusionStopsAtTheClassCap() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = atSmith(pc);
        ItemType w = firstOfClass(1, EquipSlot.WEAPON);
        pc.backpack().add(new ItemStack(w.id(), 1, 0, 1, Imbuements.NONE));
        pc.backpack().add(new ItemStack(w.id(), 1, 0, 1, Imbuements.NONE));
        pc.restoreForge(225, 225);
        assertTrue(pc.bank().credit(10_000_000).ok());
        assertTrue(s.forge().fusionPairs().isEmpty(), "class 1 stops at tier 1");
        int a = slotOf(pc, w.id(), 1);
        int b = a + 1;
        while (pc.backpack().get(b) == null) b++;
        assertFalse(s.forgeFuse(a, b, false, false, 2000));
        assertEquals(225, pc.forgeDust());
    }

    @Test
    void transferConversionsAndTheDustLimit() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = atSmith(pc);
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        ItemType other = null;
        for (ItemType t : TestData.ITEMS.all()) if (t != w && Forge.classOf(t) == 4 && t.slot() == EquipSlot.WEAPON) { other = t; break; }
        assertNotNull(other);
        pc.backpack().add(new ItemStack(w.id(), 1, 0, 5, Imbuements.NONE));
        pc.backpack().add(new ItemStack(other.id(), 1));
        int donor = slotOf(pc, w.id(), 5);
        int receiver = slotOf(pc, other.id(), 0);
        pc.restoreForge(150, 225);
        assertTrue(pc.bank().credit(100_000_000).ok());
        int cores = Forge.transferCores(w, 4);
        assertTrue(cores > 0);
        assertFalse(s.forgeTransfer(donor, receiver, 1100), "cores missing");
        pc.backpack().add(new ItemStack(id(Forge.CORE_KEY), cores));
        long gold = s.forge().funds();
        assertTrue(Actions.apply(s, "npc:forge:transfer:" + donor + ":" + receiver, 1200));
        assertEquals(0, pc.backpack().count(w.id()), "the donor is consumed");
        assertEquals(4, pc.backpack().get(receiver).tier(), "one tier is lost on transfer");
        assertEquals(50, pc.forgeDust());
        assertEquals(0, pc.backpack().count(id(Forge.CORE_KEY)));
        assertEquals(gold - Forge.transferGold(w, 5), s.forge().funds());

        // 60 pylu -> 3 odlamki, 50 odlamkow -> rdzen
        pc.restoreForge(120, 225);
        assertTrue(Actions.apply(s, "npc:forge:slivers", 1300));
        assertEquals(60, pc.forgeDust());
        assertEquals(3, pc.backpack().count(id(Forge.SLIVER_KEY)));
        assertTrue(s.forgeConvert("slivers", 1400));
        assertFalse(s.forgeConvert("slivers", 1500), "no dust left");
        assertFalse(s.forgeConvert("core", 1600));
        pc.backpack().add(new ItemStack(id(Forge.SLIVER_KEY), 44));
        assertTrue(Actions.apply(s, "npc:forge:core", 1700));
        assertEquals(0, pc.backpack().count(id(Forge.SLIVER_KEY)));
        assertEquals(1, pc.backpack().count(id(Forge.CORE_KEY)));

        // limit: 100 -> 101 za 25 pylu
        pc.restoreForge(30, 100);
        assertTrue(Actions.apply(s, "npc:forge:limit", 1800));
        assertEquals(101, pc.dustLimit());
        assertEquals(5, pc.forgeDust());
        assertFalse(s.forgeRaiseLimit(1900));
    }

    @Test
    void imbuingTakesIngredientsGoldAndDrainsOnlyInBattle() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = atSmith(pc);
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1));
        assertFalse(s.imbue(EquipSlot.WEAPON, Imbuement.VAMPIRISM, 2, 1100), "ingredients missing");
        for (Imbuement.Ingredient in : Imbuement.VAMPIRISM.ingredients(2)) pc.backpack().add(new ItemStack(id(in.itemKey()), in.count()));
        assertTrue(Money.add(pc.backpack(), 20_000).ok());
        long gold = s.forge().funds();
        assertTrue(Actions.apply(s, "npc:imbue:WEAPON:VAMPIRISM:2", 1200));
        assertEquals(gold - Imbuement.GOLD[2], s.forge().funds());
        for (Imbuement.Ingredient in : Imbuement.VAMPIRISM.ingredients(2)) assertEquals(0, pc.backpack().count(id(in.itemKey())));
        Imbuements im = pc.equipment().get(EquipSlot.WEAPON).imbuements();
        assertEquals(1, im.size());
        assertEquals(Imbuement.DURATION_MS, im.get(Imbuement.VAMPIRISM).msLeft());
        assertFalse(s.imbue(EquipSlot.WEAPON, Imbuement.VAMPIRISM, 1, 1300), "one per kind");
        assertFalse(s.imbue(EquipSlot.WEAPON, Imbuement.DRAGON_HIDE, 1, 1300), "protections do not fit weapons");

        // poza walka czas stoi
        for (long t = 2000; t <= 20_000; t += 500) s.update(t, null);
        assertEquals(Imbuement.DURATION_MS, pc.equipment().get(EquipSlot.WEAPON).imbuements().get(Imbuement.VAMPIRISM).msLeft());

        // zapis v8
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        pc.restoreForge(77, 130);
        pc.backpack().add(new ItemStack(w.id(), 1, 2, 6, Imbuements.NONE.with(Imbuement.STRIKE, 3)));
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(77, back.forgeDust());
        assertEquals(130, back.dustLimit());
        assertEquals(Imbuement.DURATION_MS, back.equipment().get(EquipSlot.WEAPON).imbuements().get(Imbuement.VAMPIRISM).msLeft());
        int slot = slotOf(back, w.id(), 6);
        assertTrue(slot >= 0);
        assertEquals(2, back.backpack().get(slot).upgrade());
        assertEquals(3, back.backpack().get(slot).imbuements().get(Imbuement.STRIKE).level());

        // usuniecie
        assertTrue(Money.add(pc.backpack(), 10_000).ok());
        assertTrue(Actions.apply(s, "npc:unimbue:WEAPON:0", 21_000));
        assertTrue(pc.equipment().get(EquipSlot.WEAPON).imbuements().isEmpty());

        // w walce poza strefa ochronna czas nasycen plynie
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1, 0, 0, Imbuements.NONE.with(Imbuement.VOID, 1)));
        pl.fawizja.pixel.world.Position wild = new pl.fawizja.pixel.world.Position(479, 500, 0);
        assertFalse(TownServicesTest.world.isProtectionZone(wild));
        s.teleport(wild, 22_000);
        for (long t = 22_500; t <= 32_000; t += 500) {
            s.combat().noteMonsterAttack(t);
            s.update(t, null);
        }
        long left = pc.equipment().get(EquipSlot.WEAPON).imbuements().get(Imbuement.VOID).msLeft();
        assertTrue(left < Imbuement.DURATION_MS && left >= Imbuement.DURATION_MS - 15_000, "drained " + left);
    }

    @Test
    void version7SavesLoadWithoutForgeState() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        pc.backpack().add(new ItemStack(id("iron_sword"), 1, 3));
        String v8 = codec.encode(pc); // biezacy zapis (v9: pasek akcji)
        assertTrue(v8.startsWith("PIXELSAVE " + CharacterCodec.CURRENT_VERSION + "\n"));
        String body = v8.substring(v8.indexOf('\n') + 1, v8.lastIndexOf("crc="));
        StringBuilder old = new StringBuilder();
        for (String line : body.split("\n", -1)) if (!line.startsWith("forge=")) old.append(line).append('\n');
        String b7 = old.substring(0, old.length() - 1);
        CRC32 c = new CRC32();
        c.update(b7.getBytes(StandardCharsets.UTF_8));
        PlayerCharacter back = codec.decode("PIXELSAVE 7\n" + b7 + "crc=" + Long.toHexString(c.getValue()) + "\n");
        assertEquals(0, back.forgeDust());
        assertEquals(Forge.DUST_START_LIMIT, back.dustLimit());
        int slot = slotOf(back, id("iron_sword"), 0);
        assertEquals(3, back.backpack().get(slot).upgrade());
    }

    @Test
    void theMarketKeepsTheTier() {
        Market m = new Market();
        ItemType w = firstOfClass(4, EquipSlot.WEAPON);
        Market.Listing l = m.add("Ola", new ItemStack(w.id(), 1, 2, 4, Imbuements.NONE), 5000);
        assertEquals(4, l.tier());
        assertEquals(2, l.upgrade());
        Market back = Market.decode(m.encode());
        Market.Listing b = back.get(l.id());
        assertEquals(4, b.tier());
        assertEquals(2, b.upgrade());
        assertEquals(new ItemStack(w.id(), 1, 2, 4, Imbuements.NONE), b.stack());
        assertEquals(0, back.get(back.add("Ula", 10, 5, 100).id()).tier());
    }
}
