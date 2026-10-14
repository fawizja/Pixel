package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.net.RemoteEntity;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Replika klienta online: stan postaci, pozycja i potwory przychodza z serwera. */
class ReplicaSyncTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    @Test
    void characterStateIsCopiedFromServerSave() throws Exception {
        PlayerCharacter server = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), server);
        s.toggleRun(0);
        s.toggleTorch(0);
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        String save = codec.encode(server);

        PlayerCharacter client = TestData.newCharacter(Vocation.DRUID);
        client.backpack().takeFromSlot(0, 1);
        client.copyStateFrom(codec.decode(save));
        // wszystko poza identyfikatorem, pozycja (przychodzi w klatkach) i suma kontrolna
        assertEquals(strip(codec.encode(server)), strip(codec.encode(client)));
        assertTrue(client.isTorchLit());

        PlayerCharacter other = TestData.newCharacter(Vocation.MONK);
        assertThrows(IllegalArgumentException.class, () -> other.copyStateFrom(codec.decode(save)));
    }

    private static String strip(String save) {
        return save.replaceAll("(?m)^(id|position|crc)=.*$", "");
    }

    @Test
    void remotePositionInterpolatesStepsAndJumpsTeleports() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        Position start = pc.position();
        s.applyRemotePosition(start.translate(0, 1), Direction.SOUTH, 1000, 400);
        assertEquals(start.translate(0, 1), pc.position());
        float mid = s.renderY(1200);
        assertTrue(mid > start.y() && mid < start.y() + 1, "interpolated " + mid);
        Position far = start.translate(5, 3);
        s.applyRemotePosition(far, null, 2000, 400);
        assertEquals(far.y(), s.renderY(2000), 1e-4);
        assertEquals(Direction.SOUTH, s.facing());
    }

    @Test
    void monstersMirrorTheServerFrame() {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, List.of(), new Random(1));
        Position a = new Position(42, 6, 0);
        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a, 100),
                new RemoteEntity("MO", 8, "nonexistent", "x", a, 100)), 0, 300);
        assertEquals(1, mm.monsters().size(), "unknown types are skipped");
        Monster boar = mm.monsters().get(0);
        assertEquals(7, boar.id());

        mm.syncRemote(List.of(new RemoteEntity("MO", 7, "boar", "boar", a.translate(1, 0), 40)), 1000, 300);
        assertSame(boar, mm.monsters().get(0), "same monster object keeps moving");
        assertEquals(a.translate(1, 0), boar.position());
        float mid = boar.renderX(1150);
        assertTrue(mid > a.x() && mid < a.x() + 1, "smooth step " + mid);
        assertEquals(0.4f, boar.healthFraction(), 0.02f);
        assertEquals(1, mm.near(a.translate(1, 0), 0).size(), "spatial grid follows");

        mm.syncRemote(List.of(), 2000, 300);
        assertTrue(mm.monsters().isEmpty(), "out of view = gone");
        assertTrue(mm.near(a.translate(1, 0), 3).isEmpty());
    }
}
