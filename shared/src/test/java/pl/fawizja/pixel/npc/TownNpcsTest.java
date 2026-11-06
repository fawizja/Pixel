package pl.fawizja.pixel.npc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.Imbuement;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Town;

/** Opiekun zwierzat, wladca i zaklinacz w kazdym miescie (tools/mapgen/town_npcs.py). */
class TownNpcsTest {

    @BeforeAll
    static void load() throws Exception { TownServicesTest.load(); }

    static final String[][] TOWNS = {{"Varden", "varden"}, {"Frostholm", "frost"}, {"Selvaran", "selv"}, {"Qasirah", "qas"},
            {"Valmir", "valmir"}, {"Nerys", "nerys"}};

    GameSession at(PlayerCharacter pc, String npcKey) {
        GameSession s = new TownServicesTest().session(pc, List.of());
        Npc n = TownServicesTest.npcs.get(npcKey);
        s.teleport(TownServicesTest.talkSpot(n), 1000);
        assertEquals(n, s.npcNearby(), npcKey);
        return s;
    }

    @Test
    void everyTownHasAPetKeeperARulerAndAnImbuer() {
        assertEquals(TOWNS.length, TownServicesTest.world.towns().size(), "all towns of the world");
        for (String[] t : TOWNS) {
            Town town = TownServicesTest.world.town(t[0]);
            assertNotNull(town, t[0]);
            Npc keeper = TownServicesTest.npcs.get(t[1] + "_petkeeper");
            assertTrue(keeper.isPetKeeper() && keeper.name().startsWith("Opiekun"), t[0]);
            assertTrue(keeper.shopKey().endsWith("_pets"));
            Npc ruler = TownServicesTest.npcs.get(t[0].equals("Varden") ? "varden_king" : t[1] + "_ruler");
            assertEquals(NpcRole.KING, ruler.role(), t[0]);
            assertTrue(ruler.name().matches("(Król|Królowa|Lord) .+"), ruler.name());
            Npc imbuer = TownServicesTest.npcs.get(t[1] + "_imbuer");
            assertEquals(NpcRole.IMBUER, imbuer.role());
            assertTrue(imbuer.imbues() && !imbuer.isSmith(), "imbuer is not a smith (no forge)");
            for (Npc n : new Npc[] {keeper, ruler, imbuer}) {
                assertEquals(t[0], n.town());
                assertNotNull(TownServicesTest.talkSpot(n), "reachable " + n.key());
                // nowi NPC nie stoja przy innych: gracz przy kazdym innym NPC rozmawia z nim, nie z nowym
                for (Npc o : TownServicesTest.npcs.all()) {
                    if (o != n && o.position().z() == 0 && !n.key().equals("varden_king")) {
                        assertTrue(o.position().distanceTo(n.position()) >= 4, n.key() + " too close to " + o.key());
                    }
                }
            }
        }
    }

    @Test
    void aRulerOutsideVardenSellsPremiumAndPromotionLikeTheKing() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.SORCERER);
        pc.addExperience(Balance.experienceForLevel(30) - pc.experience());
        pc.bank().credit(GameSession.PREMIUM_PRICE + GameSession.PROMOTION_PRICE);
        GameSession s = at(pc, "frost_ruler");
        assertTrue(s.premiumHint("Frostholm").contains("Królowa Świętosława"));
        assertTrue(Actions.apply(s, "npc:premium", 1100));
        assertTrue(pc.isPremium());
        s.askNpc("promocja", 1200);
        assertTrue(pc.isPromoted());
        assertEquals(0, pc.bank().balance());
    }

    @Test
    void theImbuerImbuesButHasNoForgeOrShop() {
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        GameSession s = at(pc, "nerys_imbuer");
        ItemType w = ForgeServiceTest.firstOfClass(4, EquipSlot.WEAPON);
        pc.equipment().setDirect(EquipSlot.WEAPON, new ItemStack(w.id(), 1));
        for (Imbuement.Ingredient in : Imbuement.STRIKE.ingredients(1)) pc.backpack().add(new ItemStack(ForgeServiceTest.id(in.itemKey()), in.count()));
        assertTrue(Money.add(pc.backpack(), 20_000 + Imbuement.CLEAR_COST).ok());
        assertTrue(Actions.apply(s, "npc:imbue:WEAPON:STRIKE:1", 1100), "crit (Strike) at the imbuer");
        assertEquals(1, pc.equipment().get(EquipSlot.WEAPON).imbuements().size());
        assertTrue(s.clearImbuement(EquipSlot.WEAPON, 0, 1200), "and removes imbuements");
        assertEquals(0, pc.equipment().get(EquipSlot.WEAPON).imbuements().size());
        assertFalse(s.forgeRaiseLimit(1300), "the forge stays at the smith");
        assertNull(s.shopOf(s.npcNearby()));
    }

    @Test
    void finderAndCaptainPointToTheRulers() {
        Npc found = NpcFinder.find(TownServicesTest.npcs, "królowa", TownServicesTest.npcs.get("frost_banker").position());
        assertEquals("frost_ruler", found.key());
        assertEquals("nerys_petkeeper", NpcFinder.find(TownServicesTest.npcs, "pupil",
                TownServicesTest.npcs.get("nerys_banker").position()).key());
        assertEquals("qas_imbuer", NpcFinder.find(TownServicesTest.npcs, "zaklinacz",
                TownServicesTest.npcs.get("qas_banker").position()).key());
    }
}
