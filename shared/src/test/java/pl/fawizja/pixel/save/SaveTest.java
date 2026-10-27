package pl.fawizja.pixel.save;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;

class SaveTest {

    private final CharacterCodec codec = new CharacterCodec(TestData.ITEMS);

    @Test
    void roundTripPreservesState() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        pc.addExperience(1_000_000);
        pc.damage(37);
        pc.setPosition(new Position(14, 27, -2));
        pc.bank().deposit(pc.backpack(), 1234);
        pc.skills().addTries(Skill.FISHING, 500);
        pc.autoLootFilter().ignore(36);
        pc.toggleKept(30);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.id(), back.id());
        assertEquals(pc.name(), back.name());
        assertEquals(pc.vocation(), back.vocation());
        assertEquals(pc.level(), back.level());
        assertEquals(pc.experience(), back.experience());
        assertEquals(pc.health(), back.health());
        assertEquals(pc.mana(), back.mana());
        assertEquals(pc.position(), back.position());
        assertEquals(1234, back.bank().balance());
        assertEquals(Money.totalValue(pc.backpack()), Money.totalValue(back.backpack()));
        assertEquals(pc.backpack().contents(), back.backpack().contents());
        assertEquals(pc.equipment().get(EquipSlot.WEAPON), back.equipment().get(EquipSlot.WEAPON));
        assertEquals(pc.skills().level(Skill.FISHING), back.skills().level(Skill.FISHING));
        assertTrue(back.autoLootFilter().isIgnored(36));
        assertTrue(back.isKept(30));
        assertFalse(back.isKept(31));
    }

    @Test
    void corruptedSaveIsRejected() {
        String text = codec.encode(TestData.newCharacter(Vocation.DRUID));
        String tampered = text.replace("bank=0", "bank=999999999");
        assertThrows(SaveFormatException.class, () -> codec.decode(tampered));
        assertThrows(SaveFormatException.class, () -> codec.decode(text.substring(0, text.length() / 2)));
        assertThrows(SaveFormatException.class, () -> codec.decode("garbage"));
        assertThrows(SaveFormatException.class, () -> codec.decode(text.replace("PIXELSAVE " + CharacterCodec.CURRENT_VERSION, "PIXELSAVE 99")));
    }

    @Test
    void version1SaveIsMigrated() throws Exception {
        String v2 = codec.encode(TestData.newCharacter(Vocation.SORCERER));
        // zbuduj zapis w starym formacie: pozycja bez pietra, saldo pod "gold.bank"
        StringBuilder body = new StringBuilder();
        for (String line : v2.split("\n")) {
            if (line.startsWith("PIXELSAVE") || line.startsWith("crc=")) continue;
            if (line.startsWith("position=")) line = "position=31,16";
            if (line.startsWith("bank=")) line = "gold.bank=777";
            if (line.startsWith("hometown=")) continue;
            body.append(line).append('\n');
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body.toString().getBytes(StandardCharsets.UTF_8));
        String v1 = "PIXELSAVE 1\n" + body + "crc=" + Long.toHexString(crc.getValue()) + "\n";
        PlayerCharacter pc = codec.decode(v1);
        // zapisy sprzed swiata 1000x1000 (v1-v4): pole spoza mapy, sesja stawia postac w swiatyni jej miasta
        assertEquals(new Position(-1, -1, 0), pc.position());
        assertEquals(777, pc.bank().balance());
        assertEquals("varden", pc.homeTown());
    }

    @Test
    void storeFallsBackToBackupWhenMainIsCorrupted(@TempDir Path dir) throws Exception {
        SaveStore store = new SaveStore(dir.toFile());
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        store.write("hero", codec.encode(pc));
        pc.addExperience(50_000);
        store.write("hero", codec.encode(pc));
        File main = dir.resolve("hero.save").toFile();
        Files.writeString(main.toPath(), "PIXELSAVE 2\nbroken");
        SaveStore.LoadResult<PlayerCharacter> r = store.read("hero", codec::decode);
        assertNotNull(r.value());
        assertTrue(r.fromBackup());
        assertNotNull(r.problem());
        assertEquals(8, r.value().level(), "backup holds the previous good save");
        assertFalse(dir.resolve("hero.save.tmp").toFile().exists());
    }

    @Test
    void storeRejectsPathTraversal(@TempDir Path dir) throws Exception {
        SaveStore store = new SaveStore(dir.toFile());
        store.write("../evil", "x");
        assertTrue(dir.resolve("___evil.save").toFile().exists());
    }
}
