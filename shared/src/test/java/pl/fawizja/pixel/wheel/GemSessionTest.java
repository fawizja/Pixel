package pl.fawizja.pixel.wheel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/** Pracownia klejnotow w sesji (akcje jak online), lup klejnotow z potworow i karty pol / objawien kola. */
class GemSessionTest {
    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);
    static final Position FIELD = new Position(470, 483, 0);

    @BeforeAll
    static void load() throws Exception { WheelSessionTest.load(); }

    private static int id(String key) { return TestData.ITEMS.byKey(key).id(); }

    private static PlayerCharacter rich(Vocation v) {
        PlayerCharacter pc = WheelFixtures.hero(v, 1000);
        pc.bank().credit(200_000_000L);
        return pc;
    }

    @Test
    void revealTakesTheGemAndGold() {
        PlayerCharacter pc = rich(Vocation.SORCERER);
        String key = GemAtelier.gemKey(Vocation.SORCERER, GemQuality.GREATER);
        GameSession s = WheelSessionTest.session(pc, FIELD);
        assertTrue(Actions.apply(s, "wheel:gem:reveal:G", 1000));
        assertEquals(GemAtelier.Result.NO_GEM.message(), WheelSessionTest.lastLog(s));
        pc.backpack().add(new ItemStack(id(key), 1));
        long bank = pc.bank().balance();
        Actions.apply(s, "wheel:gem:reveal:G", 1000);
        assertEquals(1, pc.wheel().gems().count(), "revealing works anywhere");
        assertEquals(0, pc.backpack().count(id(key)));
        assertEquals(bank - GemQuality.GREATER.revealCost, pc.bank().balance());
        WheelGem g = pc.wheel().gems().gems().get(0);
        assertTrue(g.supreme.allowed(Vocation.SORCERER));
        assertTrue(GemAtelier.gemKey(Vocation.KNIGHT, GemQuality.LESSER).startsWith("lesser_"));
        assertNotEquals(key, GemAtelier.gemKey(Vocation.KNIGHT, GemQuality.GREATER), "gem colour follows the vocation");
    }

    @Test
    void socketOnlyInTempleAndDestroyGivesFragments() {
        PlayerCharacter pc = rich(Vocation.KNIGHT);
        pc.wheel().gems().decode("n=3;g=1,L,G,HEALTH,-,-,0/2,R,B,MANA,FIRE_RES,-,1");
        GameSession field = WheelSessionTest.session(pc, FIELD);
        Actions.apply(field, "wheel:gem:socket:1", 1000);
        assertEquals(GemAtelier.Result.NOT_IN_TEMPLE.message(), WheelSessionTest.lastLog(field));
        assertNull(pc.wheel().gems().socketed(WheelDomain.GREEN));
        GameSession temple = WheelSessionTest.session(pc, WheelSessionTest.TEMPLE);
        Actions.apply(temple, "wheel:gem:socket:1", 1000);
        assertNotNull(pc.wheel().gems().socketed(WheelDomain.GREEN));
        Actions.apply(temple, "wheel:gem:destroy:2", 1000);
        assertEquals(GemAtelier.Result.LOCKED.message(), WheelSessionTest.lastLog(temple), "locked gem is safe");
        Actions.apply(temple, "wheel:gem:destroy:1", 1000);
        assertNull(pc.wheel().gems().gem(1));
        assertNull(pc.wheel().gems().socketed(WheelDomain.GREEN));
        int frags = pc.backpack().count(id(GemAtelier.LESSER_FRAGMENT_KEY));
        assertTrue(frags >= 1 && frags <= 5, "lesser gem: 1-5 lesser fragments, got " + frags);
    }

    @Test
    void gradingConsumesFragmentsAndGold() {
        PlayerCharacter pc = rich(Vocation.DRUID);
        GameSession s = WheelSessionTest.session(pc, FIELD);
        Actions.apply(s, "wheel:grade:b:ICE_RES", 1000);
        assertEquals(GemAtelier.Result.NO_FRAGMENTS.message(), WheelSessionTest.lastLog(s));
        pc.backpack().add(new ItemStack(id(GemAtelier.LESSER_FRAGMENT_KEY), 7));
        long bank = pc.bank().balance();
        Actions.apply(s, "wheel:grade:b:ICE_RES", 1000);
        assertEquals(1, pc.wheel().gems().grade(BasicGemMod.ICE_RES));
        assertEquals(2, pc.backpack().count(id(GemAtelier.LESSER_FRAGMENT_KEY)));
        assertEquals(bank - 500_000, pc.bank().balance());
        Actions.apply(s, "wheel:grade:s:KNIGHT_MASTERY_AVATAR", 1000);
        assertEquals(GemAtelier.Result.WRONG_VOCATION.message(), WheelSessionTest.lastLog(s));
    }

    @Test
    void gemsSurviveTheSave() throws Exception {
        PlayerCharacter pc = rich(Vocation.PALADIN);
        pc.wheel().gems().decode("n=2;g=1,R,P,CAPACITY,HOLY_RES,-,0;a=0,0,1,0;bg=CAPACITY:2");
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.wheel().gems().encode(), back.wheel().gems().encode());
    }

    @Test
    void dropsFollowCreatureCategories() {
        MonsterType wolf = REG.get("wolf");
        assertEquals(-1, GemDrops.category(wolf));
        assertTrue(GemDrops.roll(wolf, new Random(1)).isEmpty(), "ordinary creatures drop no gems");
        assertEquals(9000, GemDrops.chance(wolf.influenced(2), GemQuality.LESSER));
        assertEquals(0, GemDrops.chance(wolf.influenced(2), GemQuality.GREATER));
        assertEquals(9000, GemDrops.chance(wolf.fiendish(), GemQuality.GREATER));
        Random rng = new Random(7);
        int lesser = 0, greater = 0, n = 100_000;
        for (int i = 0; i < n; i++) {
            for (Map.Entry<String, Integer> e : GemDrops.roll(wolf.fiendish(), rng).entrySet()) {
                if (e.getKey().startsWith("lesser_")) lesser += e.getValue();
                else if (e.getKey().startsWith("greater_")) {
                    greater += e.getValue();
                    assertEquals(1, e.getValue(), "at most one greater gem");
                }
            }
        }
        // fiendish: lesser 3% (+3% z drugiego losu), greater 9%
        assertEquals(0.03 * 1.03, lesser / (double) n, 0.004);
        assertEquals(0.09, greater / (double) n, 0.006);
        for (MonsterType t : REG.all()) {
            if (t.boss() && t.level() >= GemDrops.ARCHFOE_MIN_LEVEL) assertEquals(2, GemDrops.category(t), t.key());
        }
    }

    @Test
    void sliceCardsDescribeEffectsFromData() {
        PlayerCharacter pc = WheelFixtures.hero(Vocation.KNIGHT, 1000);
        WheelFixtures.path(pc, WheelDomain.RED);
        // pole czaru: nazwy, stopnie I i II z danych WheelPerks
        WheelPerks.Upgrade u = WheelPerks.upgrade(Vocation.KNIGHT, WheelSlice.RED_200.conviction.spellGroup());
        java.util.List<String[]> card = WheelView.slice(pc, WheelSlice.RED_200, true, k -> k.equals("front_sweep") ? "Cięcie przed sobą" : k);
        String all = join(card);
        assertTrue(all.contains("Cięcie przed sobą (Tibia: Front Sweep)"), all);
        assertTrue(all.contains(u.grade1.describe()));
        assertTrue(all.contains("razem " + u.at(2).describe()));
        assertTrue(card.stream().anyMatch(r -> r[0].equals("Stopień I (aktywny)")), "RED_200 is full");
        assertTrue(card.stream().anyMatch(r -> r[0].equals("Drugie pole") && r[3].equals("wheel:sel:B50")));
        // pole umiejetnosci: oddanie za punkt i przy pelnym polu
        String skill = join(WheelView.slice(pc, WheelSlice.RED_TOP_100, true, k -> k));
        assertTrue(skill.contains("+100 many (pełne pole: +100 many)"), skill);
        assertTrue(skill.contains("+1 " + WheelPerks.skillLabel(Vocation.KNIGHT)));
        // pole pasywki z nazwa z Tibii
        String passive = join(WheelView.slice(pc, WheelSlice.GREEN_200, true, k -> k));
        assertTrue(passive.contains("Instynkt bitewny (Tibia: Battle Instinct)"), passive);
        // naczynie
        String vessel = join(WheelView.slice(pc, WheelSlice.RED_TOP_75, true, k -> k));
        assertTrue(vessel.contains("Rezonans") && vessel.contains("2 z 3 naczyń"), vessel);
        // objawienie: etapy z progami
        java.util.List<String[]> rev = WheelView.revelation(pc, WheelDomain.RED, k -> k);
        assertTrue(join(rev).contains("Executioner's Throw"));
        assertTrue(rev.stream().anyMatch(r -> r[0].equals("Etap 2 (aktywny)")), "575 points: stage 2");
        assertTrue(rev.stream().anyMatch(r -> r[0].equals("Etap 3 (od 1000 pkt)")));
        // cwiartka: krotki efekt i link do karty objawienia
        java.util.List<String[]> q = WheelView.quadrant(pc, WheelDomain.RED, k -> k);
        assertEquals("wheel:rev:R", q.get(0)[3]);
        assertTrue(q.stream().anyMatch(r -> r[1].equals("+3 HP, +1 many/pkt; pełne: czar front_sweep")), join(q));
        assertTrue(q.stream().anyMatch(r -> r[1].equals("+1 many/pkt; pełne: czar chivalrous_challenge")), join(q));
        assertTrue(q.stream().anyMatch(r -> r[0].equals("Zewnętrzne 200: 200/200, pełne")), join(q));
        pl.fawizja.pixel.magic.SpellRegistry spells = pl.fawizja.pixel.magic.SpellRegistry.loadDefault(TestData.ITEMS);
        java.util.function.Function<String, String> names = k -> spells.find(k) == null ? k : spells.find(k).name();
        for (Vocation v : Vocation.values()) {
            PlayerCharacter h = WheelFixtures.hero(v, 1000);
            for (WheelSlice s : WheelSlice.values()) {
                String e = WheelView.shortEffect(s, v, names);
                assertTrue(e.length() <= WheelView.SHORT_MAX && !e.endsWith("..."), "short line fits: " + e);
            }
            for (WheelDomain d : WheelDomain.values()) {
                for (java.util.List<String[]> l : java.util.List.of(WheelView.quadrant(h, d, k -> k), WheelView.revelation(h, d, k -> k))) {
                    for (String[] r : l) assertTrue(r[1].length() <= 70, "fits a row: " + r[1]);
                }
                for (WheelSlice s : WheelSlice.of(d)) {
                    for (String[] r : WheelView.slice(h, s, false, k -> k)) assertTrue(r[1].length() <= 70, "fits a row: " + r[1]);
                }
            }
        }
    }

    private static String join(java.util.List<String[]> rows) {
        StringBuilder b = new StringBuilder();
        for (String[] r : rows) b.append(String.join(" | ", r)).append('\n');
        return b.toString();
    }
}
