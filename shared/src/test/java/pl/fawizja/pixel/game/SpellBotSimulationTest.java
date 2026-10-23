package pl.fawizja.pixel.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.magic.SpellKind;
import pl.fawizja.pixel.magic.SpellType;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterRegistry;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.monster.SpawnTable;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;

/**
 * Symulacje na prawdziwej sesji gry z prostym "botem" gracza: leczy sie ponizej 50% HP
 * (czar albo mikstura), a w przeciwnym razie rzuca najlepszy czar ataku na cel.
 */
class SpellBotSimulationTest {

    static final MonsterRegistry REG = MonsterRegistry.loadDefault(TestData.ITEMS);

    /** Jeden krok decyzji bota. */
    static void bot(GameSession s, long now) {
        PlayerCharacter pc = s.character();
        // gracz sam zaznacza potwora (gra nie wybiera celu za niego)
        if (s.combat().target() == null) s.combat().cycleTarget();
        List<SpellType> known = s.spells().knownSpells();
        if (pc.health() < pc.maxHealth() / 2) {
            for (int i = known.size() - 1; i >= 0; i--) {
                SpellType sp = known.get(i);
                if (sp.kind() == SpellKind.HEAL_SELF && s.castSpell(sp.key(), now) == CastResult.OK) return;
            }
            s.usePotion("health_potion", now);
            return;
        }
        if (s.combat().target() == null) return;
        for (int i = known.size() - 1; i >= 0; i--) {
            SpellType sp = known.get(i);
            if (sp.kind() == SpellKind.ATTACK_TARGET && pc.mana() >= sp.mana() + 20
                    && s.spells().cooldowns().ready(sp, now)) {
                if (s.castSpell(sp.key(), now) == CastResult.OK) return;
            }
        }
    }

    private static GameSession session(PlayerCharacter pc, List<SpawnPoint> spawns, long seed) {
        MonsterManager mm = new MonsterManager(TestData.varden(), REG, spawns, new Random(seed));
        mm.populate(pc.position());
        return new GameSession(TestData.ITEMS, TestData.varden(), pc, mm, new Random(seed + 1), 8);
    }

    @Test
    void healingLetsEveryStarterHoldTheWolfFieldLonger() throws Exception {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = TestData.newCharacter(v);
            GameSession s = session(pc, SpawnTable.load("varden", TestData.varden(), REG), 21);
            s.teleport(new Position(13, 11, 0), 0);
            int deaths = 0;
            for (long t = 0; t < 600_000; t += 50) {
                Position before = pc.position();
                s.update(t, null);
                if (t % 250 == 0) bot(s, t);
                if (!pc.position().equals(before)) {
                    deaths++;
                    s.teleport(new Position(13, 11, 0), t);
                }
            }
            assertEquals(0, deaths, v + " survives 10 minutes with healing");
            assertTrue(s.combat().kills() >= 5, v + " kills " + s.combat().kills());
        }
    }

    @Test
    void potionsDoNotLetANewCharacterFarmTheBoss() {
        for (Vocation v : Vocation.values()) {
            PlayerCharacter pc = TestData.newCharacter(v);
            pc.backpack().add(new ItemStack(TestData.ITEMS.byKey("health_potion").id(), 100));
            Position bossAt = new Position(12, 27, -5);
            GameSession s = session(pc, List.of(new SpawnPoint("grimtooth", bossAt, 0, 1, 1200)), 7);
            Position me = new Position(13, 27, -5);
            assertTrue(TestData.varden().isWalkable(me));
            s.teleport(me, 0);
            s.combat().setTarget(s.monsters().monsters().get(0));
            long exp = pc.experience();
            boolean died = false;
            for (long t = 0; t < 600_000 && !died; t += 50) {
                s.update(t, null);
                if (t % 250 == 0) bot(s, t);
                died = !pc.position().equals(me);
            }
            assertTrue(died, v + " level 8 with 100 potions must lose to the level 60 boss");
            assertTrue(pc.experience() <= exp, v + " gained nothing");
        }
    }
}
