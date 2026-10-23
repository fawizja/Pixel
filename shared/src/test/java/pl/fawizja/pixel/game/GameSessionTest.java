package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

class GameSessionTest {

    @Test
    void movementIsTimedAndInterpolated() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        Position start = pc.position();
        s.update(0, Direction.SOUTH);
        assertEquals(start.translate(0, 1), pc.position());
        assertTrue(s.isMoving());
        float mid = s.renderY(100);
        assertTrue(mid > start.y() && mid < start.y() + 1, "interpolated " + mid);
        s.update(50, Direction.SOUTH); // krok w trakcie - drugi nie startuje
        assertEquals(start.translate(0, 1), pc.position());
        s.update(10_000, Direction.SOUTH);
        assertEquals(start.translate(0, 2), pc.position());
    }

    @Test
    void dropAndPickUpThroughSession() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        long gold = s.gold();
        int coinSlot = -1;
        for (int i = 0; i < pc.backpack().size(); i++) {
            if (pc.backpack().get(i) != null && pc.backpack().get(i).itemId() == 3) coinSlot = i;
        }
        s.dropFromSlot(coinSlot, 0);
        assertEquals(0, s.gold());
        assertTrue(s.itemsAtFeet());
        s.pickUpHere(1);
        assertEquals(gold, s.gold());
        assertFalse(s.itemsAtFeet());
        assertTrue(s.log().size() >= 2);
    }

    @Test
    void torchToggleNeedsTorch() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = new GameSession(TestData.ITEMS, TestData.varden(), pc);
        s.toggleTorch(0);
        assertTrue(pc.isTorchLit());
        s.toggleTorch(1);
        assertFalse(pc.isTorchLit());
        pc.backpack().remove(TestData.ITEMS.byKey("torch").id(), 1);
        s.toggleTorch(2);
        assertFalse(pc.isTorchLit());
    }

    @Test
    void invalidSavedPositionFallsBackToSpawn() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.setPosition(new Position(18, 15, 0)); // sciana
        new GameSession(TestData.ITEMS, TestData.varden(), pc);
        assertEquals(TestData.varden().spawn(), pc.position());
    }

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    private static GameSession sessionWithMonsters(PlayerCharacter pc, long seed) throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load("varden", TestData.varden(), REG);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, spawns, new Random(seed));
        mm.populate(pc.position());
        return new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(seed + 1), 8);
    }

    @Test
    void newCharacterSurvivesTheWolvesNextToTown() throws Exception {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = TestData.newCharacter(v);
            GameSession s = sessionWithMonsters(pc, 3);
            s.teleport(new Position(13, 11, 0), 0);
            long exp = pc.experience();
            // stoi w miejscu przez 3 minuty: wilki atakuja, gracz zaznacza najblizszego i postac go bije
            for (long t = 0; t < 180_000; t += 50) {
                if (t % 250 == 0 && s.combat().target() == null) s.combat().cycleTarget();
                s.update(t, null);
            }
            assertEquals(new Position(13, 11, 0), pc.position(), v + " did not die");
            assertTrue(pc.experience() > exp, v + " killed something");
            assertTrue(s.combat().kills() >= 2, v + " kills " + s.combat().kills());
        }
    }

    @Test
    void attackedPlayerDoesNotPickATargetByItself() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = sessionWithMonsters(pc, 3);
        s.teleport(new Position(13, 11, 0), 0);
        int hp = pc.health();
        boolean hit = false;
        for (long t = 0; t < 60_000 && !hit; t += 50) {
            s.update(t, null);
            hit = pc.health() < hp;
        }
        assertTrue(hit, "wolves attacked");
        assertNull(s.combat().target(), "only the player selects a monster to attack");
    }

    @Test
    void enteringDeepFloorWarnsNewCharacter() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.DRUID);
        GameSession s = sessionWithMonsters(pc, 5);
        s.teleport(new Position(13, 22, -5), 1000);
        assertTrue(s.log().latest(5).stream().anyMatch(e -> e.text().startsWith("Uwaga")), "danger warning on -5");
        GameSession calm = sessionWithMonsters(TestData.newCharacter(Vocation.DRUID), 5);
        calm.teleport(new Position(13, 11, 0), 1000);
        assertTrue(calm.log().latest(5).stream().noneMatch(e -> e.text().startsWith("Uwaga")));
    }

    @Test
    void monsterLevelsGrowWithDepthAndSurfaceIsSafe() throws Exception {
        List<SpawnPoint> spawns = SpawnTable.load("varden", TestData.varden(), REG);
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, spawns, new Random(1));
        assertTrue(mm.maxMonsterLevelOnFloor(0) <= 15, "surface around the starting town");
        for (int z = 1; z <= 5; z++) assertEquals(0, mm.maxMonsterLevelOnFloor(z), "no monsters in town buildings");
        int prev = mm.maxMonsterLevelOnFloor(-1);
        for (int z = -2; z >= -5; z--) {
            int lvl = mm.maxMonsterLevelOnFloor(z);
            assertTrue(lvl >= prev, "floor " + z + " is not easier than the one above");
            prev = lvl;
        }
    }

    @Test
    void teleportOntoMonsterFreesTheTile() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = sessionWithMonsters(pc, 9);
        Position occupied = s.monsters().monsters().get(0).position();
        s.teleport(occupied, 0);
        assertNull(s.monsters().monsterAt(occupied));
        assertEquals(occupied, pc.position());
    }
}
