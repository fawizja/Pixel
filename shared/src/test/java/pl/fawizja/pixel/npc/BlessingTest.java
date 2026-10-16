package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.quest.QuestRegistry;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

class BlessingTest {

    @Test
    void aBlessingHalvesTheNextDeathPenaltyOnce() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.addExperience(Balance.experienceForLevel(40) - pc.experience());
        long exp = pc.experience();
        pc.setBlessed(true);
        assertTrue(pc.applyDeathPenalty(8));
        assertEquals(exp - exp / 40, pc.experience());
        assertFalse(pc.isBlessed(), "used up");
        long exp2 = pc.experience();
        assertFalse(pc.applyDeathPenalty(8));
        assertEquals(exp2 - exp2 / 20, pc.experience());
        assertEquals(1000, Balance.blessingPrice(1));
        assertEquals(20_000, Balance.blessingPrice(100));
    }

    @Test
    void thePriestSellsItForBackpackCoinsAndTheSaveKeepsIt() throws Exception {
        WorldMap world = TestData.world();
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, world, shops);
        PlayerCharacter pc = TestData.newCharacter(Vocation.MONK);
        GameSession s = new GameSession(TestData.ITEMS, world, pc,
                new MonsterManager(world, null, List.of(), new Random(1)), new Random(2), 8);
        s.setTownServices(npcs, shops, QuestRegistry.loadDefault(TestData.ITEMS, MonsterRegistry.loadDefault(TestData.ITEMS)));
        Npc priest = npcs.get("varden_priest");
        s.teleport(priest.position().translate(0, 1), 0);
        assertEquals(priest, s.npcNearby());
        long gold = s.gold();
        assertTrue(Actions.apply(s, "npc:bless", 0));
        assertTrue(pc.isBlessed());
        assertEquals(gold - Balance.blessingPrice(pc.level()), s.gold());
        assertFalse(s.priestBless(0), "only one at a time");
        assertEquals(gold - Balance.blessingPrice(pc.level()), s.gold());
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        assertTrue(codec.decode(codec.encode(pc)).isBlessed());
        pc.setBlessed(false);
        assertFalse(codec.decode(codec.encode(pc)).isBlessed());
    }
}
