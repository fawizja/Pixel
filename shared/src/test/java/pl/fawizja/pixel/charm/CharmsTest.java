package pl.fawizja.pixel.charm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.achievement.Bestiary;
import pl.fawizja.pixel.character.CharacterCodec;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.vocation.Vocation;

/** Charmy jak w Tibii 2025: Major za punkty, Minor za echa, stopnie, sloty, zapis i migracja. */
class CharmsTest {

    private static Bestiary killed(String key, int n) {
        Bestiary b = new Bestiary();
        for (int i = 0; i < n; i++) b.record(key, false);
        return b;
    }

    @Test
    void allTwentyFiveCharmsWithTibiaValues() {
        assertEquals(25, Charm.values().length);
        int major = 0;
        for (Charm c : Charm.values()) if (c.major()) major++;
        assertEquals(14, major, "14 Major, 11 Minor");
        assertEquals(10, Charm.WOUND.value(2), 1e-9);
        assertEquals(1200, Charm.WOUND.cost(3));
        assertEquals(4000, Charm.LOW_BLOW.cost(3));
        assertEquals(9, Charm.LOW_BLOW.value(3), 1e-9);
        assertEquals(44, Charm.SAVAGE_BLOW.value(3), 1e-9);
        assertEquals(1.6, Charm.VAMPIRIC_EMBRACE.value(1), 1e-9);
        assertEquals(225, Charm.GUT.cost(3));
        assertEquals("1,6", Charm.number(1.6));
    }

    @Test
    void majorCostsPointsAndGivesEchoesMinorCostsEchoes() {
        Charms c = new Charms();
        c.addPoints(300);
        assertFalse(c.buy(Charm.ENFLAME).ok(), "400 points needed");
        assertTrue(c.buy(Charm.WOUND).ok());
        assertEquals(60, c.points());
        assertEquals(50, c.echoes(), "tier I gives 25*0+25*0+50");
        assertEquals(1, c.tier(Charm.WOUND));
        assertFalse(c.buy(Charm.CRIPPLE).ok(), "100 echoes needed");
        c.addPoints(1000);
        assertTrue(c.buy(Charm.WOUND).ok());
        assertEquals(150, c.echoes(), "tier II gives 100");
        assertTrue(c.buy(Charm.CRIPPLE).ok());
        assertEquals(50, c.echoes());
        assertEquals(200, Charms.echoesFor(2));
        c.addPoints(5000);
        assertTrue(c.buy(Charm.WOUND).ok());
        assertFalse(c.buy(Charm.WOUND).ok(), "max tier III");
    }

    @Test
    void assignRulesSlotsAndCategories() {
        Charms c = new Charms();
        c.addPoints(10_000);
        c.buy(Charm.WOUND);
        c.buy(Charm.DODGE);
        c.buy(Charm.ZAP);
        c.buy(Charm.CRIPPLE);
        Bestiary b = killed("wolf", 249);
        assertFalse(c.assign(Charm.WOUND, "wolf", b, false, "Wilk").ok(), "Major needs a completed entry");
        assertTrue(c.assign(Charm.CRIPPLE, "wolf", b, false, "Wilk").ok(), "Minor needs only a known creature");
        b.record("wolf", false);
        assertTrue(c.assign(Charm.WOUND, "wolf", b, false, "Wilk").ok());
        assertFalse(c.assign(Charm.DODGE, "wolf", b, true, "Wilk").ok(), "one Major per creature");
        for (int i = 0; i < 500; i++) b.record("boar", false);
        assertFalse(c.assign(Charm.DODGE, "boar", b, false, "Dzik").ok(), "free account: 2 slots");
        assertTrue(c.assign(Charm.DODGE, "boar", b, true, "Dzik").ok(), "premium: 6 slots");
        assertEquals(3, c.used());
        assertEquals(Charm.WOUND, c.on("wolf", Charm.Category.MAJOR));
        assertEquals(1, c.active(Charm.WOUND, "wolf"));
        assertEquals(0, c.active(Charm.WOUND, "boar"));
        b.record("grimtooth", true);
        assertFalse(c.assign(Charm.ZAP, "grimtooth", b, true, "Grimtooth").ok(), "no charms on bosses");
        assertTrue(c.remove(Charm.WOUND));
        assertEquals(0, c.active(Charm.WOUND, "wolf"));
    }

    @Test
    void resetReturnsAllPoints() {
        Charms c = new Charms();
        c.addPoints(1000);
        c.buy(Charm.WOUND);
        c.resetAll(true);
        assertEquals(1000, c.points());
        assertEquals(100, c.echoes(), "promoted characters keep 100 echoes");
        assertEquals(0, c.tier(Charm.WOUND));
        c.resetAll(false);
        assertEquals(0, c.echoes());
    }

    @Test
    void goldPricesAreTibiaDividedByFour() {
        assertEquals(2_500, CharmPrices.removal(100), "Tibia 10 000");
        assertEquals(25_000, CharmPrices.resetAll(100), "Tibia 100 000");
        assertEquals(25_000 + 2_750 * 300, CharmPrices.resetAll(300), "Tibia 100 000 + 11 000 x level");
    }

    @Test
    void savedWithCharacterAndOldSavesGetPointsForCompletedEntries() throws Exception {
        CharacterCodec codec = new CharacterCodec(TestData.ITEMS);
        PlayerCharacter pc = TestData.newCharacter(Vocation.KNIGHT);
        for (int i = 0; i < 250; i++) pc.bestiary().record("wolf", false);
        for (int i = 0; i < 500; i++) pc.bestiary().record("boar", false);
        pc.charms().addPoints(20);
        pc.charms().buy(Charm.CRIPPLE); // za malo ech - nic
        pc.charms().addPoints(300);
        pc.charms().buy(Charm.WOUND);
        pc.charms().assign(Charm.WOUND, "boar", pc.bestiary(), false, "Dzik");
        PlayerCharacter back = codec.decode(codec.encode(pc));
        assertEquals(pc.charms().encode(), back.charms().encode());
        assertEquals("boar", back.charms().assignedTo(Charm.WOUND));

        // stary zapis bez pola "charms": 5 (wilk) + 15 (dzik) punktow za ukonczone wpisy
        String text = codec.encode(pc);
        String old = stripField(text, "charms");
        PlayerCharacter migrated = codec.decode(old);
        assertEquals(20, migrated.charms().points());
        assertEquals(20, migrated.charms().maxPoints());
        assertEquals(0, migrated.charms().tier(Charm.WOUND));
    }

    /** Usuwa pole z zapisu i przelicza sume kontrolna (zapis sprzed charmow). */
    private static String stripField(String text, String key) {
        String[] lines = text.split("\n");
        StringBuilder body = new StringBuilder();
        String header = null;
        for (String l : lines) {
            if (header == null) { header = l; continue; }
            if (l.startsWith("crc=") || l.startsWith(key + "=")) continue;
            body.append(l).append('\n');
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return header + "\n" + body + "crc=" + Long.toHexString(crc.getValue()) + "\n";
    }
}
