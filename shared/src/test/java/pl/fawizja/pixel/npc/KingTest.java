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
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldLayout;
import pl.fawizja.pixel.world.WorldMap;

/** Krol Aurelian w zamku Varden: konto premium (rejsy) i promocja profesji ze znakiem; straznicy przy bramach. */
class KingTest {

    static final WorldMap WORLD = TestData.world();

    static GameSession atTheKing(PlayerCharacter pc) throws Exception {
        ShopRegistry shops = ShopRegistry.loadDefault(TestData.ITEMS);
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, WORLD, shops);
        GameSession s = new GameSession(TestData.ITEMS, WORLD, pc, new MonsterManager(WORLD, null, List.of(), new Random(1)),
                new Random(2), 8);
        s.setTownServices(npcs, shops, null);
        Npc king = npcs.get("varden_king");
        assertEquals(NpcRole.KING, king.role());
        s.teleport(king.position().translate(0, 1), 0);
        assertEquals(king, s.npcNearby());
        return s;
    }

    @Test
    void theKingSellsPremiumAndThenPromotionFromTheBank() throws Exception {
        PlayerCharacter pc = TestData.newCharacter(Vocation.PALADIN);
        pc.addExperience(Balance.experienceForLevel(30) - pc.experience());
        pc.bank().credit(GameSession.PREMIUM_PRICE + GameSession.PROMOTION_PRICE + 5);
        long bank = pc.bank().balance();
        GameSession s = atTheKing(pc);

        assertFalse(s.buyPromotion(0), "promotion needs premium first");
        assertFalse(pc.isPromoted());
        assertTrue(Actions.apply(s, "npc:premium", 1));
        assertTrue(pc.isPremium());
        assertEquals(bank - GameSession.PREMIUM_PRICE, pc.bank().balance());
        assertFalse(s.buyPremium(2), "only once");

        s.askNpc("promocja", 3);
        assertTrue(pc.isPromoted());
        assertEquals("Royal Paladin", pc.vocationTitle());
        assertEquals(5, pc.bank().balance());

        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertTrue(back.isPremium() && back.isPromoted(), "the save keeps premium and promotion");
    }

    @Test
    void promotionNeedsLevelAndMoneyAndSpeedsUpRegeneration() throws Exception {
        PlayerCharacter low = TestData.newCharacter(Vocation.DRUID);
        low.setPremium(true);
        low.bank().credit(GameSession.PROMOTION_PRICE);
        if (low.level() < GameSession.PROMOTION_LEVEL) assertFalse(atTheKing(low).buyPromotion(0), "level too low");

        PlayerCharacter poor = TestData.newCharacter(Vocation.DRUID);
        poor.addExperience(Balance.experienceForLevel(40) - poor.experience());
        poor.setPremium(true);
        poor.bank().debit(poor.bank().balance());
        assertFalse(atTheKing(poor).buyPromotion(0), "no money in the bank");

        PlayerCharacter a = TestData.newCharacter(Vocation.DRUID), b = TestData.newCharacter(Vocation.DRUID);
        for (PlayerCharacter pc : List.of(a, b)) {
            pc.addExperience(Balance.experienceForLevel(40) - pc.experience());
            pc.damage(pc.health() - 1);
        }
        b.setPromoted(true);
        a.tickRegeneration(60_000);
        b.tickRegeneration(60_000);
        assertTrue(b.health() > a.health(), "promoted " + b.health() + " vs " + a.health());
    }

    @Test
    void guardsStandAtTheGatesOfEveryTown() throws Exception {
        NpcRegistry npcs = NpcRegistry.load(WorldLayout.WORLD_MAP, WORLD, ShopRegistry.loadDefault(TestData.ITEMS));
        for (String town : List.of("Varden", "Frostholm", "Qasirah", "Selvaran")) {
            assertTrue(npcs.all().stream().anyMatch(n -> n.role() == NpcRole.GUARD && n.town().equals(town)), town);
        }
        for (Npc g : npcs.all()) {
            if (g.role() == NpcRole.GUARD) assertTrue(WORLD.isProtectionZone(g.position()), g.key());
        }
    }

    @Test
    void oldSavesMoveWithTheirTownOrBackToTheTemple() {
        // Varden przeniesiony w calosci z podziemiami, wyspy tylko powierzchnia, dzicz nie istnieje
        assertEquals(new Position(170, 356, 0), WorldLayout.fromV3(79, 80, 0));
        assertEquals(new Position(170, 356, -2), WorldLayout.fromV3(79, 80, -2));
        assertNull(WorldLayout.fromV3(20, 300, 0));
        assertNull(WorldLayout.fromV3(80, 20, -1));
        assertTrue(WORLD.isWalkable(WorldLayout.fromV3(79, 29, 0)), "Frostholm temple");
    }
}
