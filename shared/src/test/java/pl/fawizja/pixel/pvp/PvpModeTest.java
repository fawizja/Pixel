package pl.fawizja.pixel.pvp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import pl.fawizja.pixel.TestData;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.Actions;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.magic.CastResult;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.SpawnPoint;
import pl.fawizja.pixel.pvp.PvpRules.Outcome;
import pl.fawizja.pixel.pvp.PvpRules.Skull;
import pl.fawizja.pixel.pvp.PvpRules.Verdict;
import pl.fawizja.pixel.world.Direction;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Tryb PvP (przelacznik czaszki) i czaszki: offline, z "udawanym" drugim graczem - druga GameSession
 * na tej samej mapie, czary obszarowe przez {@link PvpTargeting} jak na serwerze.
 */
class PvpModeTest {

    static final WorldMap W = TestData.varden();
    static final Position FIELD = new Position(48, 6, 0);

    private static GameSession player(String name, Position p) {
        PlayerCharacter pc = TestData.newCharacterNamed(name);
        pc.addExperience(Balance.experienceForLevel(40) - pc.experience());
        pc.setPosition(p);
        pc.heal(pc.maxHealth());
        return new GameSession(TestData.ITEMS, W, pc);
    }

    private static Outcome fightToDeath(GameSession a, GameSession b, long from, Random rng, long[] when) {
        Outcome o = Outcome.HIT;
        long t = from;
        for (; t < from + 900_000 && (o == Outcome.HIT || o == Outcome.NOT_READY); t += 2000) {
            o = PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, t, rng);
        }
        when[0] = t - 2000;
        return o;
    }

    @Test
    void pvpModeIsOffByDefaultTogglesAndIsNotSaved() throws Exception {
        GameSession s = player("Toggler", FIELD);
        assertFalse(s.pvpMode());
        assertFalse(s.isPvpMode());
        assertTrue(s.togglePvpMode());
        assertTrue(s.character().pvpMode());
        assertTrue(Actions.apply(s, "pvp", 0));
        assertFalse(s.pvpMode(), "the HUD toggle action switches it back off");
        s.setPvpMode(true);
        pl.fawizja.pixel.character.CharacterCodec codec = new pl.fawizja.pixel.character.CharacterCodec(TestData.ITEMS);
        assertFalse(codec.decode(codec.encode(s.character())).pvpMode(), "not saved across logins");
        assertEquals(Skull.NONE, s.character().skull());
        assertEquals(0, s.character().pzBlockedMs(0));
    }

    @Test
    void cannotAttackAPlayerWithPvpOffButCanWithItOn() {
        GameSession a = player("Atakuje", FIELD);
        GameSession b = player("Broni", FIELD.translate(1, 0));
        int hp = b.character().health();
        assertEquals(Verdict.PVP_OFF, PvpRules.check(a.character(), b.character(), b.pvpStatus(), W, 0));
        assertEquals(Outcome.NOT_ALLOWED, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 0, new Random(1)));
        assertEquals(hp, b.character().health());
        assertEquals(Skull.NONE, a.pvpStatus().skull(0));

        a.togglePvpMode();
        assertEquals(Verdict.OK, PvpRules.check(a.character(), b.character(), b.pvpStatus(), W, 0));
        assertEquals(Outcome.HIT, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 1000, new Random(1)));
        assertEquals(Skull.WHITE, a.pvpStatus().skull(1000));
        assertEquals(PvpRules.WHITE_SKULL_MS, a.character().pzBlockedMs(1000), "60 s protection zone block");
        a.update(1000, null);
        assertEquals(Skull.WHITE, a.character().skull(), "skull mirrored into the character for the HUD");
        assertEquals(Skull.NONE, b.pvpStatus().skull(1000), "the defender stays clean");
        assertEquals(0, b.character().pzBlockedMs(1000), "the defender may flee into a protection zone");

        // kolejna agresja odnawia licznik
        assertEquals(Outcome.HIT, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 31_000, new Random(1)));
        assertEquals(31_000 + PvpRules.WHITE_SKULL_MS, a.pvpStatus().fightUntil());
        assertEquals(Skull.WHITE, a.pvpStatus().skull(31_000 + PvpRules.WHITE_SKULL_MS - 1));
        assertEquals(Skull.NONE, a.pvpStatus().skull(31_000 + PvpRules.WHITE_SKULL_MS), "the skull expires with its timer");

        // w strefie ochronnej nikt nikogo nie atakuje, nawet w trybie PvP
        Position temple = new Position(31, 16, 0);
        assertTrue(W.isProtectionZone(temple));
        a.character().setPosition(temple);
        b.character().setPosition(temple.translate(1, 0));
        assertEquals(Verdict.PROTECTION_ZONE, PvpRules.check(a.character(), b.character(), b.pvpStatus(), W, 200_000));
    }

    @Test
    void aSkulledPlayerCanBeAttackedByAnyoneWithPvpOffForFree() {
        GameSession aggressor = player("Agresor", FIELD);
        GameSession victim = player("Ofiara", FIELD.translate(1, 0));
        GameSession helper = player("Pomocnik", FIELD.translate(-1, 0));
        aggressor.setPvpMode(true);
        assertEquals(Outcome.HIT, PvpRules.attack(aggressor, aggressor.pvpStatus(), victim, victim.pvpStatus(), W, 0, new Random(1)));
        assertTrue(aggressor.pvpStatus().aggressor(0));

        assertFalse(victim.pvpMode());
        assertFalse(helper.pvpMode());
        assertEquals(Verdict.OK, PvpRules.check(victim.character(), aggressor.character(), aggressor.pvpStatus(), W, 0));
        int hp = aggressor.character().health();
        Outcome o = Outcome.NOT_READY;
        for (long t = 0; t < 20_000 && o != Outcome.HIT; t += 2000) {
            o = PvpRules.attack(helper, helper.pvpStatus(), aggressor, aggressor.pvpStatus(), W, t, new Random(t));
        }
        assertEquals(Outcome.HIT, o);
        assertTrue(aggressor.character().health() <= hp);
        assertEquals(Skull.NONE, helper.pvpStatus().skull(20_000), "hitting an aggressor gives no skull");
        assertTrue(helper.character().pzBlockedMs(0) > 0, "but, as in Tibia, any attack on a player blocks the PZ for 1 min");
        assertEquals(Skull.YELLOW, PvpRules.skullSeenBy(aggressor.pvpStatus(), "Pomocnik", helper.pvpStatus(), 0),
                "the aggressor sees the helper with a yellow skull");
        assertEquals(Skull.NONE, PvpRules.skullSeenBy(victim.pvpStatus(), "Pomocnik", helper.pvpStatus(), 0),
                "nobody else does");
        assertEquals(Verdict.PVP_OFF, PvpRules.check(helper.character(), victim.character(), victim.pvpStatus(), W, 0),
                "the clean victim is still protected from players with PvP off");
    }

    @Test
    void killingAnUnskulledPlayerGivesAFifteenMinuteWhiteSkullAndBlocks() {
        GameSession killer = player("Zabojca", FIELD);
        GameSession victim = player("Zabity", FIELD.translate(1, 0));
        killer.setPvpMode(true);
        long[] when = new long[1];
        assertEquals(Outcome.KILLED, fightToDeath(killer, victim, 0, new Random(5), when));
        long t = when[0];
        assertEquals(Skull.WHITE, killer.pvpStatus().skull(t), "one unjustified kill is not a red skull in Tibia");
        assertEquals(PvpRules.KILL_BLOCK_MS, killer.character().pzBlockedMs(t), "15 min protection zone block");
        assertEquals(PvpRules.KILL_BLOCK_MS, killer.pvpStatus().logoutBlockedMs(t), "15 min logout block");
        assertEquals(Skull.WHITE, killer.pvpStatus().skull(t + PvpRules.KILL_BLOCK_MS - 1));
        assertEquals(Skull.NONE, killer.pvpStatus().skull(t + PvpRules.KILL_BLOCK_MS), "white skull for 15 min");
        assertEquals(0, victim.pvpStatus().logoutBlockedMs(t), "death ends the victim's blocks");
        killer.update(t + 1, null);
        assertEquals(Skull.WHITE, killer.character().skull());
        assertTrue(killer.log().latest(10).stream().anyMatch(e -> e.text().contains("Nieusprawiedliwione zabójstwo")));
    }

    /** Czarodziej z runa obszarowa celuje w dzika; obok dzika stoi drugi gracz. */
    @Test
    void areaRunesHitUnmarkedPlayersEvenInSecureModeAndSkullTheCaster() {
        PlayerCharacter pc = TestData.newCharacterNamed("Runiarz");
        pc.addExperience(Balance.experienceForLevel(40) - pc.experience());
        pc.skills().set(Skill.MAGIC, 10, 0);
        pc.backpack().add(new ItemStack(TestData.ITEMS.byKey("fireburst_rune").id(), 5));
        MonsterManager mm = new MonsterManager(W, pl.fawizja.pixel.monster.MonsterRegistry.loadDefault(TestData.ITEMS),
                List.of(new SpawnPoint("boar", new Position(12, 12, 0), 0, 1, 600)), new Random(3));
        mm.populate(null);
        GameSession caster = new GameSession(TestData.ITEMS, W, pc, mm, new Random(5), 8,
                pl.fawizja.pixel.magic.SpellRegistry.loadDefault(TestData.ITEMS),
                pl.fawizja.pixel.magic.ConsumableRegistry.loadDefault(TestData.ITEMS),
                pl.fawizja.pixel.magic.RecipeRegistry.loadDefault(TestData.ITEMS));
        caster.teleport(new Position(10, 12, 0), 0);
        Monster boar = mm.monsters().get(0);
        GameSession bystander = player("Gapiowicz", boar.position().translate(0, 1));
        assertFalse(W.isProtectionZone(bystander.character().position()));
        caster.spells().setPlayerTargeting(new PvpTargeting(caster, () -> null, () -> List.of(caster, bystander),
                null, W, new Random(7), null));

        // tryb PvP wylaczony (tryb bezpieczny): jak w Tibii runa obszarowa i tak rani gracza obok - biala czaszka
        caster.combat().setTarget(boar);
        int hp = bystander.character().health();
        assertFalse(PvpRules.SECURE_MODE_BLOCKS_AREA);
        assertEquals(CastResult.OK, caster.spells().useFirst("fireburst_rune", 1000));
        assertTrue(boar.health() < boar.type().maxHealth());
        assertTrue(bystander.character().health() < hp, "secure mode does not protect from area damage (Tibia)");
        assertEquals(Skull.WHITE, caster.pvpStatus().skull(1000));
        assertEquals(PvpRules.PZ_BLOCK_MS, caster.character().pzBlockedMs(1000));
        assertEquals(Skull.NONE, bystander.pvpStatus().skull(1000));
        // ale celowany atak na nieoznaczonego gracza w trybie bezpiecznym jest zablokowany
        assertEquals(Verdict.PVP_OFF, PvpRules.check(caster.character(), caster.pvpStatus(), bystander.character(),
                bystander.pvpStatus(), W, 1000));

        // obszar gracza bez czaszki trafia oznaczonego rzucajacego bez kary, a nieoznaczonego - z biala czaszka
        GameSession clean = player("Czysty", new Position(10, 12, 0));
        int casterHp = caster.character().health();
        PvpTargeting t = new PvpTargeting(clean, () -> null, () -> List.of(caster), null, W, new Random(9), null);
        int hits = t.strikeArea(clean.character().position(), 3, p -> true, () -> 50,
                pl.fawizja.pixel.combat.DamageType.FIRE, 6000);
        assertEquals(1, hits);
        assertTrue(caster.character().health() < casterHp);
        assertEquals(Skull.NONE, clean.pvpStatus().skull(6000), "the skulled caster is fair game");
        // druzyna: obszar omija czlonkow wlasnej druzyny
        PvpTargeting party = new PvpTargeting(clean, () -> null, () -> List.of(caster), (x, y) -> true, W,
                new Random(9), null);
        assertEquals(0, party.strikeArea(clean.character().position(), 3, p -> true, () -> 50,
                pl.fawizja.pixel.combat.DamageType.FIRE, 7000));
    }

    @Test
    void protectionZoneEntryIsBlockedWhileSkulledAndAllowedAfterExpiry() {
        // pole tuz przed strefa ochronna
        Position edge = null;
        Direction into = null;
        search:
        for (int y = 1; y < W.height() - 1; y++) {
            for (int x = 1; x < W.width() - 1; x++) {
                Position q = new Position(x, y, 0);
                if (!W.isWalkable(q) || W.isProtectionZone(q) || W.transitionAt(q) != null) continue;
                for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    Position n = q.translate(d.dx, d.dy);
                    if (W.isWalkable(n) && W.isProtectionZone(n) && W.transitionAt(n) == null) {
                        edge = q;
                        into = d;
                        break search;
                    }
                }
            }
        }
        assertNotNull(edge);
        GameSession a = player("Wchodzi", FIELD);
        GameSession b = player("Stoi", FIELD.translate(1, 0));
        a.setPvpMode(true);
        assertEquals(Outcome.HIT, PvpRules.attack(a, a.pvpStatus(), b, b.pvpStatus(), W, 10_000, new Random(1)));
        a.character().setPosition(edge);
        a.update(20_000, into);
        assertEquals(edge, a.character().position(), "skulled: locked out of the protection zone");
        assertEquals(Skull.WHITE, a.character().skull());
        assertTrue(a.log().latest(5).stream().anyMatch(e -> e.text().contains("nie wejdziesz do strefy ochronnej")));
        a.update(10_000 + PvpRules.WHITE_SKULL_MS, into);
        assertTrue(W.isProtectionZone(a.character().position()), "allowed in again after the skull expired");
        assertEquals(Skull.NONE, a.character().skull());
    }
}
