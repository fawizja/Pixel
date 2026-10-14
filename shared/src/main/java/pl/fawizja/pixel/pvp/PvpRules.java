package pl.fawizja.pixel.pvp;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.AttackProfile;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Zasady walki gracz kontra gracz (decyzje opisane w docs/GAME_SYSTEMS.md):
 * <ul>
 *   <li>postacie ponizej poziomu {@link #PROTECTION_LEVEL} nie atakuja i nie sa atakowane przez graczy,</li>
 *   <li>w strefie ochronnej (PZ) nie ma walki - ani atakujacy, ani cel nie moga w niej stac,</li>
 *   <li>nie mozna atakowac postaci o ponad polowe slabszej (chyba ze ma czaszke),</li>
 *   <li>atak na gracza bez czaszki daje biala czaszke na {@link #WHITE_SKULL_MS},</li>
 *   <li>{@link #RED_SKULL_KILLS} nieusprawiedliwione zabojstwa w {@link #KILL_WINDOW_MS} daja czerwona
 *       czaszke na {@link #RED_SKULL_MS}; smierc z czerwona czaszka = caly plecak zostaje na ziemi,</li>
 *   <li>PvP nie daje doswiadczenia (brak "farmienia" znajomych), obrazenia graczom sa o polowe mniejsze.</li>
 * </ul>
 */
public final class PvpRules {

    public static final int PROTECTION_LEVEL = 30;
    public static final long WHITE_SKULL_MS = 15 * 60_000L;
    public static final long RED_SKULL_MS = 6 * 3_600_000L;
    public static final long KILL_WINDOW_MS = 24 * 3_600_000L;
    public static final int RED_SKULL_KILLS = 3;
    public static final double PVP_DAMAGE = 0.5;

    public enum Skull { NONE, WHITE, RED }

    public enum Verdict {
        OK(null),
        SELF("Nie mozesz atakowac samego siebie."),
        DEAD("Cel nie zyje."),
        ATTACKER_PROTECTED("Do poziomu " + PROTECTION_LEVEL + " nie mozesz atakowac graczy."),
        TARGET_PROTECTED("Ten gracz jest chroniony (ponizej poziomu " + PROTECTION_LEVEL + ")."),
        PROTECTION_ZONE("W strefie ochronnej nie ma walki."),
        LEVEL_GAP("Ten gracz jest o wiele slabszy - nie mozesz go zaatakowac."),
        OTHER_FLOOR("Cel jest na innym pietrze.");

        public final String message;

        Verdict(String message) { this.message = message; }
    }

    /** Stan PvP gracza (czaszki, nieusprawiedliwione zabojstwa, odstep atakow). */
    public static final class Status {
        long whiteUntil;
        long redUntil;
        long nextAttackMs;
        final ArrayDeque<Long> unjustKills = new ArrayDeque<>();

        public Skull skull(long now) {
            if (now < redUntil) return Skull.RED;
            if (now < whiteUntil) return Skull.WHITE;
            return Skull.NONE;
        }

        public int unjustKills(long now) {
            prune(now);
            return unjustKills.size();
        }

        private void prune(long now) {
            for (Iterator<Long> it = unjustKills.iterator(); it.hasNext(); ) {
                if (now - it.next() > KILL_WINDOW_MS) it.remove();
            }
        }
    }

    /** Wynik jednego ataku. */
    public enum Outcome { NOT_ALLOWED, NOT_READY, OUT_OF_REACH, HIT, KILLED }

    private PvpRules() {}

    /** Czy a moze zaatakowac b (bez sprawdzania zasiegu). */
    public static Verdict check(PlayerCharacter a, PlayerCharacter b, Status bStatus, WorldMap world, long now) {
        if (a == b || a.id().equals(b.id())) return Verdict.SELF;
        if (b.isDead() || a.isDead()) return Verdict.DEAD;
        if (a.level() < PROTECTION_LEVEL) return Verdict.ATTACKER_PROTECTED;
        if (b.level() < PROTECTION_LEVEL) return Verdict.TARGET_PROTECTED;
        if (a.position().z() != b.position().z()) return Verdict.OTHER_FLOOR;
        if (world.isProtectionZone(a.position()) || world.isProtectionZone(b.position())) return Verdict.PROTECTION_ZONE;
        boolean marked = bStatus != null && bStatus.skull(now) != Skull.NONE;
        if (!marked && b.level() * 2 < a.level()) return Verdict.LEVEL_GAP;
        return Verdict.OK;
    }

    /**
     * Jeden automatyczny atak gracza na gracza (wywolywany co krok serwera): zasady, zasieg,
     * odstep atakow, obrazenia, czaszki i smierc. Zdarzenia trafiaja do dziennikow obu stron.
     */
    public static Outcome attack(GameSession attacker, Status as, GameSession victim, Status vs,
                                 WorldMap world, long now, Random rng) {
        PlayerCharacter a = attacker.character();
        PlayerCharacter b = victim.character();
        if (check(a, b, vs, world, now) != Verdict.OK) return Outcome.NOT_ALLOWED;
        if (now < as.nextAttackMs) return Outcome.NOT_READY;
        AttackProfile ap = CombatFormulas.attackProfile(a, attacker.items());
        if (a.position().distanceTo(b.position()) > ap.range() || !world.hasLineOfSight(a.position(), b.position())) {
            return Outcome.OUT_OF_REACH;
        }
        as.nextAttackMs = now + ap.intervalMs();
        boolean justified = vs.skull(now) != Skull.NONE;
        if (!justified && as.skull(now) == Skull.NONE) {
            attacker.log().add(now, EventLog.Kind.WARNING, "Zaatakowales gracza " + b.name() + " - biala czaszka na 15 min.");
        }
        if (!justified) as.whiteUntil = Math.max(as.whiteUntil, now + WHITE_SKULL_MS);
        int raw = CombatFormulas.roll(ap.maxDamage(), rng);
        if (a.isDefensive()) raw = (int) Math.round(raw * CombatFormulas.DEFENSIVE_OUTGOING);
        raw = (int) Math.round(raw * PVP_DAMAGE);
        int dmg = CombatFormulas.mitigateOnPlayer(b, raw, ap.type(), b.isDefensive(), rng);
        int dealt = b.damage(dmg);
        CombatEvent.Kind kind = dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED;
        victim.combat().addEvent(new CombatEvent(now, b.position(), dealt, kind));
        attacker.combat().addEvent(new CombatEvent(now, b.position(), dealt,
                dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        if (!b.isDead()) return Outcome.HIT;

        boolean dropAll = vs.skull(now) == Skull.RED;
        victim.combat().diedFromPlayer(a.name(), dropAll, now);
        attacker.log().add(now, EventLog.Kind.WARNING, "Zabiles gracza " + b.name() + ".");
        if (!justified) {
            as.unjustKills.addLast(now);
            if (as.unjustKills(now) >= RED_SKULL_KILLS) {
                as.redUntil = now + RED_SKULL_MS;
                attacker.log().add(now, EventLog.Kind.WARNING,
                        "Czerwona czaszka: przy smierci stracisz caly plecak (6 h).");
            }
        }
        return Outcome.KILLED;
    }
}
