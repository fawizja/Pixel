package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.economy.Money;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.npc.NpcRegistry;
import pl.fawizja.pixel.npc.ShopRegistry;
import pl.fawizja.pixel.npc.Travel;
import pl.fawizja.pixel.treasure.TreasureChests;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.AltarQuest;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Layer;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.Reachability;
import pl.fawizja.pixel.world.Region;
import pl.fawizja.pixel.world.Transition;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Grobowiec Pieciu Profesji pod pustynia (Darek 2026-10-28): drzwi poziomu 20 przed zadaniem, piec oltarzy profesji
 * z pochodniami, dzwignia na prawo od pola rycerza, skarbiec z nagroda 20 000 gp raz na postac.
 */
class AltarQuestTest {

    private final WorldMap world = TestData.world();
    private final AltarQuest quest = world.altarQuests().stream().filter(q -> q.id().equals("desert")).findFirst()
            .orElseThrow(() -> new AssertionError("desert quest missing"));

    @BeforeEach
    @AfterEach
    void cleanUp() {
        world.wallLights().setListener(null);
        world.wallLights().reset();
        for (Position d : world.levelDoors().keySet()) world.openings().set(d, false);
    }

    private static PlayerCharacter hero(Vocation v, int level, Position at) {
        PlayerCharacter pc = TestData.newCharacter(v);
        if (level > pc.level()) pc.addExperience(Balance.experienceForLevel(level) - pc.experience());
        assertEquals(level, pc.level());
        pc.setPosition(at);
        return pc;
    }

    private Position levelDoor() {
        return world.levelDoors().keySet().stream().filter(p -> p.z() == 0).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ mapa

    @Test
    void theMapHasTheQuestLayout() {
        assertEquals(20000, quest.gold());
        assertEquals(EnumSet.allOf(Vocation.class).size(), quest.slots().size(), "one altar per vocation");
        int altarId = world.tiles().byKey("altar").id();
        TreasureChests chests = TreasureChests.generate(world);
        for (Vocation v : Vocation.values()) {
            AltarQuest.Slot s = quest.slot(v);
            assertNotNull(s, v.name());
            assertEquals(-3, s.altar().z(), "the quest is on floor -3");
            assertEquals(altarId, world.floor(-3).get(Layer.OBJECT, s.altar().x(), s.altar().y()));
            assertEquals(s.itemKey(), world.displayAt(s.altar()), "vocation item lies on the altar");
            assertTrue(TestData.ITEMS.byKey(s.itemKey()).vocations().contains(v), s.itemKey() + " belongs to " + v);
            assertEquals(s.altar().translate(0, 1), s.stand(), "square in front of the altar");
            assertTrue(world.isWalkable(s.stand()));
            assertTrue(world.wallLights().isTorch(s.torch()), "torch on the wall next to the altar " + s.torch());
            assertTrue(world.isQuestTorch(s.torch()));
            assertFalse(world.wallLights().isLit(s.torch()), "quest torches are unlit by default");
            assertEquals(1, s.torch().distanceTo(s.altar()), "torch next to its altar");
            assertTrue(quest.inHall(s.stand()) && world.isProtectionZone(s.stand()));
            assertTrue(quest.inVault(s.target()) && world.isWalkable(s.target()));
            TreasureChests.Chest c = chests.at(s.chest());
            assertNotNull(c, "reward chest of " + v);
            assertEquals(v, c.vocation());
            assertEquals(20000, c.gold());
            assertEquals(1, c.position().distanceTo(s.target()), "the chest is next to the teleport target");
        }
        // dzwignia: bezposrednio na prawo (wschod) od pola rycerza
        assertEquals(quest.slot(Vocation.KNIGHT).stand().translate(1, 0), quest.lever());
        assertEquals("lever", world.tiles().get(world.floor(-3).get(Layer.OBJECT, quest.lever().x(), quest.lever().y())).key());
        assertSame(quest, world.altarQuestByLever(quest.lever()));
        // w sali i skarbcu nie ma losowych skrzyn ani pulapek
        for (TreasureChests.Chest c : chests.all()) {
            if (c.vocation() == null) assertFalse(world.inAltarQuestRooms(c.position()), "random chest in the quest rooms");
        }
        for (Position t : chests.traps()) assertFalse(world.inAltarQuestRooms(t));
        // drzwi poziomu 20 na pustyni
        Position door = levelDoor();
        assertEquals(20, world.levelDoorAt(door));
        assertEquals(20, world.requiredLevelAt(door));
        assertTrue(world.openings().isDoor(door));
        Region surface = world.regionAt(door);
        assertNotNull(surface);
        assertTrue(surface.name().contains("Pustynia"), "entrance in the desert: " + surface.name());
        assertEquals("Grobowiec Pięciu Profesji", world.regionAt(quest.lever()).name());
    }

    @Test
    void entranceIsReachableFromTheDesertAndTheVaultLeadsBack() throws Exception {
        NpcRegistry npcs = NpcRegistry.load("world", world, ShopRegistry.loadDefault(TestData.ITEMS));
        List<Reachability.Link> links = new ArrayList<>(Travel.links(npcs));
        Reachability walk = Reachability.from(world, world.spawn(), links);
        Position door = levelDoor();
        assertTrue(walk.reached(door.translate(0, 1)), "in front of the level door");
        assertTrue(walk.reached(door));
        // schody kapliczki prowadza w dol, az do sali oltarzy na -3
        for (Vocation v : Vocation.values()) assertTrue(walk.reached(quest.slot(v).stand()), "altar square " + v);
        assertTrue(walk.reachedNear(quest.lever()));
        // skarbiec tylko dzwignia
        for (Vocation v : Vocation.values()) assertFalse(walk.reached(quest.slot(v).target()), "vault only by the lever");
        // ze skarbca schody na -2, stamtad do swiatyni
        Position target = quest.slot(Vocation.KNIGHT).target();
        Reachability back = Reachability.from(world, target, links);
        assertTrue(back.reached(world.spawn()), "way back from the vault");
        boolean exit = false;
        for (Transition t : world.floor(-3).transitions().values()) {
            if (quest.inVault(t.from())) {
                exit = true;
                assertEquals(-2, t.to().z());
                assertTrue(walk.reached(t.to()), "vault exit lands in the dungeon");
            }
        }
        assertTrue(exit, "vault has an exit");
    }

    @Test
    void monstersFitAPartyOfFiveFromLevel20() throws Exception {
        Region r = world.regionAt(quest.lever());
        MonsterRegistry reg = MonsterRegistry.loadDefault(TestData.ITEMS);
        int count = 0;
        for (SpawnPoint s : SpawnTable.load("world", world, reg)) {
            if (world.regionAt(s.center()) != r) continue;
            int lvl = reg.get(s.monsterKey()).level();
            assertTrue(lvl >= 20 && lvl <= 60, s.monsterKey() + " level " + lvl);
            assertFalse(world.inAltarQuestRooms(s.center()));
            count++;
        }
        assertTrue(count >= 12, "spawns on -1..-3: " + count);
    }

    // ------------------------------------------------------------------ drzwi poziomu

    private GameSession walkNorthThroughDoor(int level) {
        Position door = levelDoor();
        PlayerCharacter pc = hero(Vocation.KNIGHT, level, door.translate(0, 1));
        GameSession s = new GameSession(TestData.ITEMS, world, pc);
        for (long t = 0; t < 3_000; t += 50) s.update(t, Direction.NORTH);
        return s;
    }

    @Test
    void levelDoorStopsCharactersBelowLevel20() {
        Position door = levelDoor();
        GameSession s = walkNorthThroughDoor(19);
        assertEquals(door.translate(0, 1), s.character().position(), "stays in front of the door");
        assertTrue(s.log().latest(10).stream().anyMatch(e -> e.text().contains("Tylko dla poziomu 20+")));
        // dotkniecie: napis, drzwi sie nie otwieraja
        assertTrue(s.useAt(door, 5_000));
        assertTrue(s.log().latest(1).get(0).text().contains("Tylko dla poziomu 20+"));
        assertFalse(world.openings().isOpen(door));
        assertTrue(s.lookAt(door, 6_000).contains("Tylko dla poziomu 20+"));
    }

    @Test
    void levelDoorLetsLevel20ThroughAndClosesBehind() {
        Position door = levelDoor();
        // wejscie w zamkniete drzwi poziomu ich nie otwiera (jak inne drzwi) - otwiera je uzycie
        GameSession walked = walkNorthThroughDoor(20);
        assertEquals(door.translate(0, 1), walked.character().position());
        assertFalse(world.openings().isOpen(door));

        PlayerCharacter pc = hero(Vocation.DRUID, 20, door.translate(0, 1));
        GameSession s = new GameSession(TestData.ITEMS, world, pc);
        assertTrue(s.useAt(door, 0));
        assertTrue(s.log().latest(1).get(0).text().contains("drzwi się otwierają"));
        assertEquals(door, pc.position(), "using the level door steps into the doorway (like Tibia)");
        assertTrue(world.openings().isOpen(door), "open while the character stands in it");
        long t = 0;
        for (; t < 3_000 && pc.position().equals(door); t += 50) s.update(t, Direction.NORTH);
        Position p = pc.position();
        assertTrue(p.z() == -1 || p.y() < door.y(), "went through the door: " + p);
        s.update(t + 50, null);
        assertFalse(world.openings().isOpen(door), "the door closes behind the character");

        // za nia postac z poziomem 19 nie przejdzie: drzwi zamkniete, wejscie i dotkniecie odmawiaja
        GameSession low = walkNorthThroughDoor(19);
        assertEquals(door.translate(0, 1), low.character().position());
        assertFalse(low.useAt(door, 10_000) && world.openings().isOpen(door));
        assertNotEquals(door, low.character().position());
        assertTrue(low.log().latest(1).get(0).text().contains("Tylko dla poziomu 20+"));
    }

    @Test
    void levelDoorStaysOpenWhileAnotherQualifiedCharacterStandsInIt() {
        Position door = levelDoor();
        PlayerCharacter a = hero(Vocation.DRUID, 20, door.translate(0, 1));
        GameSession sa = new GameSession(TestData.ITEMS, world, a);
        assertTrue(sa.useAt(door, 0));
        assertEquals(door, a.position());
        // druga sesja (serwer: inny gracz) widzi postac A w drzwiach: nie wejdzie i drzwi sie nie zamkna
        PlayerCharacter b = hero(Vocation.KNIGHT, 30, door.translate(0, 1));
        GameSession sb = new GameSession(TestData.ITEMS, world, b);
        sb.setOtherPlayers(q -> q.equals(a.position()));
        assertTrue(sb.useAt(door, 100));
        assertNotEquals(door, b.position());
        assertTrue(sb.log().latest(1).get(0).text().contains("Ktoś stoi"));
        assertTrue(world.openings().isOpen(door));
    }

    // ------------------------------------------------------------------ pochodnie i dzwignia

    /** Druzyna testowa: sesje offline na wspolnym swiecie, dzwignia przenosi kazdego (jak serwer). */
    private static final class Party implements GameSession.AltarParty {
        final List<GameSession> sessions = new ArrayList<>();

        @Override
        public boolean standing(Position p, Vocation v) {
            for (GameSession s : sessions) {
                if (s.character().vocation() == v && s.character().position().equals(p)) return true;
            }
            return false;
        }

        @Override
        public void teleport(Position from, Vocation v, Position to, long now) {
            for (GameSession s : sessions) {
                if (s.character().vocation() == v && s.character().position().equals(from)) {
                    s.teleport(to, now);
                    return;
                }
            }
        }
    }

    private Party party(Vocation... vocations) {
        Party party = new Party();
        for (Vocation v : vocations) {
            GameSession s = new GameSession(TestData.ITEMS, world, hero(v, 25, quest.slot(v).stand()));
            party.sessions.add(s);
        }
        for (GameSession s : party.sessions) s.setAltarParty(party);
        return party;
    }

    @Test
    void torchLightsOnlyForTheRightVocationOnTheRightSquare() {
        PlayerCharacter knight = hero(Vocation.KNIGHT, 25, quest.slot(Vocation.DRUID).stand());
        GameSession s = new GameSession(TestData.ITEMS, world, knight);
        s.update(0, null);
        for (Vocation v : Vocation.values()) assertFalse(world.wallLights().isLit(quest.slot(v).torch()), "knight on druid square: " + v);
        s.teleport(quest.slot(Vocation.KNIGHT).stand(), 100);
        s.update(200, null);
        assertTrue(world.wallLights().isLit(quest.slot(Vocation.KNIGHT).torch()), "knight on his square");
        assertFalse(world.wallLights().isLit(quest.slot(Vocation.DRUID).torch()));
        s.teleport(quest.slot(Vocation.KNIGHT).stand().translate(0, 2), 400);
        s.update(500, null);
        assertFalse(world.wallLights().isLit(quest.slot(Vocation.KNIGHT).torch()), "leaves - torch goes out");
        // tapniecie pochodni zadania (z pola obok) jej nie zapala ani nie gasi
        Position torch = quest.slot(Vocation.KNIGHT).torch();
        s.teleport(torch.translate(0, 1), 600);
        s.update(700, null);
        assertTrue(s.useAt(torch, 800));
        assertFalse(world.wallLights().isLit(torch));
        assertTrue(s.log().latest(1).get(0).text().contains("zapali się sama"));
    }

    @Test
    void leverNeedsAllFiveVocations() {
        Party p = party(Vocation.KNIGHT, Vocation.PALADIN, Vocation.SORCERER, Vocation.DRUID);
        GameSession knight = p.sessions.get(0);
        p.sessions.get(0).updateAltarTorches();
        assertEquals(4, quest.slots().stream().filter(s -> world.wallLights().isLit(s.torch())).count(), "4 torches lit");
        assertTrue(knight.useAt(quest.lever(), 1_000));
        String msg = knight.log().latest(1).get(0).text();
        assertTrue(msg.contains("Monk") && msg.contains("ani drgnie"), msg);
        for (GameSession s : p.sessions) assertTrue(quest.inHall(s.character().position()), "nobody moved");
    }

    @Test
    void leverTeleportsAllFiveToTheVault() {
        Party p = party(Vocation.values());
        GameSession knight = p.sessions.stream().filter(s -> s.character().vocation() == Vocation.KNIGHT).findFirst().orElseThrow();
        knight.updateAltarTorches();
        for (Vocation v : Vocation.values()) assertTrue(world.wallLights().isLit(quest.slot(v).torch()), "lit " + v);
        assertEquals(1, knight.character().position().distanceTo(quest.lever()), "lever is next to the knight");
        assertTrue(knight.useAt(quest.lever(), 1_000));
        for (GameSession s : p.sessions) {
            assertEquals(quest.slot(s.character().vocation()).target(), s.character().position(), s.character().vocation().name());
        }
        knight.updateAltarTorches();
        for (Vocation v : Vocation.values()) assertFalse(world.wallLights().isLit(quest.slot(v).torch()), "squares empty again");
    }

    @Test
    void offlineGhostsTestPath() {
        GameSession s = new GameSession(TestData.ITEMS, world, hero(Vocation.KNIGHT, 25, quest.slot(Vocation.KNIGHT).stand()));
        assertTrue(s.useAt(quest.lever(), 0));
        assertTrue(quest.inHall(s.character().position()), "alone the lever does not move");
        s.setAltarGhosts(EnumSet.of(Vocation.PALADIN, Vocation.SORCERER, Vocation.DRUID, Vocation.MONK));
        s.update(100, null);
        for (Vocation v : Vocation.values()) assertTrue(world.wallLights().isLit(quest.slot(v).torch()), "lit " + v);
        assertTrue(s.useAt(quest.lever(), 200));
        assertEquals(quest.slot(Vocation.KNIGHT).target(), s.character().position());
    }

    // ------------------------------------------------------------------ nagroda

    @Test
    void rewardOncePerCharacterAndOnlyFromTheOwnChest() throws Exception {
        TreasureChests chests = TreasureChests.generate(world);
        PlayerCharacter pc = hero(Vocation.MONK, 25, quest.slot(Vocation.MONK).target());
        GameSession s = new GameSession(TestData.ITEMS, world, pc);
        s.setChests(chests);
        long before = Money.totalValue(pc.backpack());
        assertTrue(s.useAt(quest.slot(Vocation.MONK).chest(), 0));
        assertEquals(before + 20000, Money.totalValue(pc.backpack()));
        assertTrue(pc.openedChests().contains(quest.chestId(Vocation.MONK)));
        assertTrue(s.useAt(quest.slot(Vocation.MONK).chest(), 100));
        assertTrue(s.log().latest(1).get(0).text().contains("Ta skrzynia jest pusta"));
        assertEquals(before + 20000, Money.totalValue(pc.backpack()));
        // cudza skrzynia (druid obok)
        s.teleport(quest.slot(Vocation.DRUID).target(), 200);
        assertTrue(s.useAt(quest.slot(Vocation.DRUID).chest(), 300));
        assertTrue(s.log().latest(1).get(0).text().contains("należy do profesji Druid"));
        assertEquals(before + 20000, Money.totalValue(pc.backpack()));
        // po zapisie i wczytaniu (offline i serwer zapisuja postac tym samym kodekiem) nagrody nie ma
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter loaded = codec.decode(codec.encode(pc));
        loaded.setPosition(quest.slot(Vocation.MONK).target());
        GameSession again = new GameSession(TestData.ITEMS, world, loaded);
        again.setChests(chests);
        long had = Money.totalValue(loaded.backpack());
        assertTrue(again.useAt(quest.slot(Vocation.MONK).chest(), 0));
        assertEquals(had, Money.totalValue(loaded.backpack()), "quest can be done once per character");
    }

    @Test
    void altarItemsCannotBePickedUpOrMoved() {
        for (Vocation v : Vocation.values()) {
            AltarQuest.Slot slot = quest.slot(v);
            PlayerCharacter pc = hero(v, 25, slot.stand());
            int item = TestData.ITEMS.byKey(slot.itemKey()).id();
            int had = pc.backpack().count(item) + (pc.equipment().all().values().stream().filter(st -> st.itemId() == item).mapToInt(st -> 1).sum());
            GameSession s = new GameSession(TestData.ITEMS, world, pc);
            assertTrue(s.ground().at(slot.altar()).isEmpty(), "nothing lies on the ground there");
            assertTrue(s.useAt(slot.altar(), 0));
            assertTrue(s.log().latest(1).get(0).text().contains("nie da się go podnieść"));
            s.pickUpHere(10);
            assertFalse(world.crates().isMovable(slot.altar()), "altar cannot be pushed");
            assertTrue(s.lookAt(slot.altar(), 20).contains("ołtarz profesji " + v.displayName()));
            int now = pc.backpack().count(item) + (pc.equipment().all().values().stream().filter(st -> st.itemId() == item).mapToInt(st -> 1).sum());
            assertEquals(had, now, "item stays on the altar");
            assertEquals(slot.itemKey(), world.displayAt(slot.altar()));
        }
    }

    @Test
    void questDataApi() {
        Map<Position, Integer> doors = world.levelDoors();
        assertFalse(doors.isEmpty());
        assertEquals("\"Tylko dla poziomu 20+\"", WorldMap.levelDoorSign(20));
        assertEquals("Knight, Monk", AltarQuest.names(List.of(quest.slot(Vocation.KNIGHT), quest.slot(Vocation.MONK))));
        assertEquals("q_desert_knight", quest.chestId(Vocation.KNIGHT));
    }
}
