package pl.fawizja.pixel.magic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.CharacterFactory;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.config.StartingConfig;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.movement.MovementSystem;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;

/** Etap 12: utani hur i utani gran hur przyspieszaja postac 300+ na okreslony czas, a potem wygasaja. */
class HasteTest {

    static PlayerCharacter veteran(Vocation v) {
        return new CharacterFactory(StartingConfig.defaults(), TestData.ITEMS).create("Szybki", v, new Position(31, 16, 0), 300);
    }

    @Test
    void utaniHurSpeedsUpForItsDurationAndThenExpires() {
        PlayerCharacter pc = veteran(Vocation.PALADIN);
        GameSession s = MagicTest.session(pc, null, null);
        int base = pc.speed();
        long baseStep = MovementSystem.stepDurationMs(pc, Direction.EAST);
        assertEquals(CastResult.OK, s.castSpell("haste", 1000));
        assertEquals(base + base * 30 / 100, pc.speed());
        assertTrue(MovementSystem.stepDurationMs(pc, Direction.EAST) < baseStep, "hasted steps are shorter");
        pc.tickRegeneration(32_000);
        assertTrue(pc.hasteMs() > 0, "still hasted before 33 s");
        pc.tickRegeneration(1_000);
        assertEquals(0, pc.hasteMs());
        assertEquals(base, pc.speed(), "speed back to normal");
    }

    @Test
    void utaniGranHurIsStrongerShorterAndOnlyForMages() {
        PlayerCharacter sorc = veteran(Vocation.SORCERER);
        GameSession s = MagicTest.session(sorc, null, null);
        int base = sorc.speed();
        assertEquals(CastResult.OK, s.castSpell("strong_haste", 1000));
        assertEquals(base + base * 70 / 100, sorc.speed());
        assertEquals(22_000, sorc.hasteMs());
        // nowy czar zastepuje poprzedni (nie sumuje sie)
        assertEquals(CastResult.OK, s.castSpell("haste", 4000));
        assertEquals(base + base * 30 / 100, sorc.speed());

        PlayerCharacter pal = veteran(Vocation.PALADIN);
        assertEquals(CastResult.WRONG_VOCATION, MagicTest.session(pal, null, null).castSpell("strong_haste", 1000));
    }

    @Test
    void speedAt300LeavesRoomForHasteUnderTheCap() {
        double walk = Balance.tilesPerSecond(Balance.speed(300));
        double hur = Balance.tilesPerSecond(Balance.speed(300) * 130 / 100);
        double granHur = Balance.tilesPerSecond(Balance.speed(300) * 170 / 100);
        assertTrue(walk > 5.5 && walk < 7.5, "level 300 walks " + walk);
        assertTrue(walk < hur && hur < granHur, "each haste is faster");
        assertTrue(granHur < Balance.MAX_TILES_PER_SECOND, "strong haste still under the cap: " + granHur);
        // niskie poziomy chodza tak samo jak przed zmiana krzywej
        assertEquals(Balance.speed(8) / 80.0, Balance.tilesPerSecond(Balance.speed(8)), 1e-9);
    }

    @Test
    void autoSpellBarOffersHasteToVeterans() {
        PlayerCharacter pc = veteran(Vocation.DRUID);
        GameSession s = MagicTest.session(pc, null, null);
        assertTrue(s.spells().knownSpells().stream().anyMatch(sp -> sp.key().equals("strong_haste")));
    }
}
