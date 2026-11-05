package pl.fawizja.pixel.training;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.npc.Npc;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Town;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

public class TrainingDummiesTest {

    static WorldMap world;
    static NpcRegistry npcs;
    static ShopRegistry shops;
    static QuestRegistry quests;

    @BeforeAll
    static void load() throws Exception {
        world = TestData.world();
        shops = ShopRegistry.loadDefault(TestData.ITEMS);
        npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        quests = QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS));
    }

    static List<Position> npcSpots() {
        List<Position> l = new ArrayList<>();
        for (Npc n : npcs.all()) l.add(n.position());
        return l;
    }

    @Test
    void everyTownGetsTwoDummiesInItsSafeZoneAwayFromTheTempleAndNpcs() {
        TrainingDummies d = TrainingDummies.generate(world, npcSpots());
        assertEquals(world.towns().size() * TrainingDummies.PER_TOWN, d.all().size());
        for (Position p : d.all()) {
            assertTrue(world.isWalkable(p), p.toString());
            assertTrue(world.isProtectionZone(p), p.toString());
            for (Town t : world.towns()) {
                if (t.temple().z() == p.z()) assertTrue(Math.max(Math.abs(t.temple().x() - p.x()), Math.abs(t.temple().y() - p.y())) >= 3);
            }
            for (Npc n : npcs.all()) assertFalse(n.position().equals(p) || n.position().distanceTo(p) < 2, n.key());
        }
        assertEquals(d.all(), TrainingDummies.generate(world, npcSpots()).all(), "the same on client and server");
    }

    @Test
    void trainingNextToADummyRaisesTheWeaponSkillWithoutExperienceUntilYouMove() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, quests);
        Position dummy = s.dummies().all().get(0);
        Position spot = null;
        for (Direction dir : Direction.values()) {
            Position q = dummy.translate(dir.dx, dir.dy);
            if (!dir.diagonal() && world.isWalkable(q) && npcs.at(q) == null) { spot = q; break; }
        }
        assertNotNull(spot);
        s.teleport(spot, 0);
        long exp = pc.experience();
        // to samo co 4 ciosy w manekin: DUMMY_TRY_FACTOR prob piesci i DUMMY_BLOCK_TRY_FACTOR tarczy na cios, z etapem
        pl.fawizja.pixel.character.Skills ref = new pl.fawizja.pixel.character.Skills(Vocation.MONK);
        for (Skill sk : Skill.values()) ref.set(sk, pc.skills().level(sk), pc.skills().tries(sk));
        for (int i = 0; i < 4; i++) {
            ref.train(Skill.FIST, Balance.DUMMY_TRY_FACTOR);
            ref.train(Skill.SHIELDING, Balance.DUMMY_BLOCK_TRY_FACTOR);
        }
        s.useNearby(0);
        assertTrue(s.training());
        assertTrue(s.log().latest(1).get(0).text().contains("360 prób/min"),
                "start message shows the speed");
        s.update(GameSession.TRAIN_MS * 4, null);
        assertEquals(ref.level(Skill.FIST), pc.skills().level(Skill.FIST), "monk trains fist (x stage multiplier)");
        assertEquals(ref.tries(Skill.FIST), pc.skills().tries(Skill.FIST));
        assertEquals(ref.tries(Skill.SHIELDING), pc.skills().tries(Skill.SHIELDING), "monk trains shielding without a shield");
        assertEquals(ref.level(Skill.SHIELDING), pc.skills().level(Skill.SHIELDING));
        assertEquals(exp, pc.experience(), "no experience from a dummy");
        assertFalse(s.dummies().at(spot));
        assertTrue(s.dummies().at(dummy));
        // ruch konczy trening
        long fistTries = pc.skills().tries(Skill.FIST);
        int fistLevel = pc.skills().level(Skill.FIST);
        s.teleport(world.templeOf(pc.homeTown()), GameSession.TRAIN_MS * 4);
        s.update(GameSession.TRAIN_MS * 5, null);
        assertFalse(s.training());
        assertEquals(fistTries, pc.skills().tries(Skill.FIST));
        assertEquals(fistLevel, pc.skills().level(Skill.FIST));
    }

    /** Na telefonie nie ma przycisku Uzyj: dotkniecie manekina zaczyna i konczy trening. */
    @Test
    void tappingTheDummyStartsAndStopsTraining() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, quests);
        Position dummy = s.dummies().all().get(0);
        Position spot = null;
        for (Direction dir : Direction.values()) {
            Position q = dummy.translate(dir.dx, dir.dy);
            if (!dir.diagonal() && world.isWalkable(q) && npcs.at(q) == null) { spot = q; break; }
        }
        assertNotNull(spot);
        s.teleport(spot, 0);
        assertTrue(s.usableAt(dummy), "dummy is usable by tap");
        assertTrue(s.useAt(dummy, 0));
        assertTrue(s.training());
        assertTrue(s.useAt(dummy, 100));
        assertFalse(s.training());
    }

    /** Rycerz: miecz 10 -> 60 przy manekinie (porownanie z polowaniem: HuntBalanceSimulationTest#dummyTrainsAboutTwoThirdsAsFastAsHunting). */
    @Test
    void aKnightTrainsSword10To60AtTheDummyInAQuarterOfAnHour() {
        long perMin = 60_000 / GameSession.TRAIN_MS;
        assertEquals(360, perMin * Balance.DUMMY_TRY_FACTOR, "weapon tries/min before the stage multiplier");
        assertEquals(480, perMin * Balance.DUMMY_BLOCK_TRY_FACTOR, "shielding tries/min before the stage multiplier");
        double hours = hoursAtDummy(Vocation.KNIGHT, Skill.SWORD, 10, 60, Balance.DUMMY_TRY_FACTOR, GameSession.TRAIN_MS);
        double before = hoursAtDummy(Vocation.KNIGHT, Skill.SWORD, 10, 60, 1, 3_000); // do 0.222.0: 1 proba co 3 s
        System.out.printf("Manekin: rycerz miecz 10->60 w %.2f h (do 0.222.0: %.1f h)%n", hours, before);
        assertTrue(hours > 0.15 && hours < 0.5, "sword 10->60 in " + hours + " h");
        assertTrue(before > 10 * hours);
    }

    /** Czas treningu umiejetnosci od..do przy manekinie (tries prob z etapem co strikeMs). */
    public static double hoursAtDummy(Vocation v, Skill s, int from, int to, int tries, long strikeMs) {
        pl.fawizja.pixel.character.Skills k = new pl.fawizja.pixel.character.Skills(v);
        k.set(s, from, 0);
        long strikes = 0;
        while (k.level(s) < to) {
            k.train(s, tries);
            strikes++;
        }
        return strikes * strikeMs / 3_600_000.0;
    }

    /** Spis manekinow (dla dokumentacji i wlasciciela): miasto, pole, przesuniecie od swiatyni, podloze, najblizszy NPC. */
    @Test
    void listsWhereTheDummiesAre() {
        TrainingDummies d = TrainingDummies.generate(world, npcSpots());
        StringBuilder sb = new StringBuilder("\nManekiny treningowe:\n");
        for (Town t : world.towns()) {
            for (Position p : TrainingDummies.forTown(world, t.temple(), npcSpots())) {
                assertTrue(d.at(p));
                Npc near = null;
                for (Npc n : npcs.all()) {
                    if (n.position().z() == p.z() && (near == null || n.position().distanceTo(p) < near.position().distanceTo(p))) near = n;
                }
                pl.fawizja.pixel.world.TileType g = world.tiles().get(world.floor(p.z()).get(pl.fawizja.pixel.world.Layer.GROUND, p.x(), p.y()));
                sb.append(String.format("  %-10s %d,%d,%d  swiatynia %d,%d  (%+d,%+d)  %s  NPC: %s (%d)%n", t.name(), p.x(), p.y(), p.z(),
                        t.temple().x(), t.temple().y(), p.x() - t.temple().x(), p.y() - t.temple().y(), g == null ? "?" : g.key(),
                        near == null ? "-" : near.name(), near == null ? -1 : near.position().distanceTo(p)));
            }
        }
        System.out.println(sb);
    }
}
