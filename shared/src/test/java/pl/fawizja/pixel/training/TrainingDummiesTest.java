package pl.fawizja.pixel.training;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
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

class TrainingDummiesTest {

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
        long fist = pc.skills().tries(Skill.FIST);
        long shield = pc.skills().tries(Skill.SHIELDING);
        s.useNearby(0);
        assertTrue(s.training());
        s.update(GameSession.TRAIN_MS * 4, null);
        assertEquals(fist + 4, pc.skills().tries(Skill.FIST), "monk trains fist");
        assertEquals(shield + 2, pc.skills().tries(Skill.SHIELDING));
        assertEquals(exp, pc.experience(), "no experience from a dummy");
        assertFalse(s.dummies().at(spot));
        assertTrue(s.dummies().at(dummy));
        // ruch konczy trening
        s.teleport(world.templeOf(pc.homeTown()), GameSession.TRAIN_MS * 4);
        s.update(GameSession.TRAIN_MS * 5, null);
        assertFalse(s.training());
        assertEquals(fist + 4, pc.skills().tries(Skill.FIST));
    }
}
