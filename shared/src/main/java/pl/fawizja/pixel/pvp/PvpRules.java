package pl.fawizja.pixel.pvp;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.combat.AttackProfile;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatFormulas;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Zasady walki gracz kontra gracz (decyzje opisane w docs/GAME_SYSTEMS.md):
 * <ul>
 *   <li>postacie ponizej poziomu {@link #PROTECTION_LEVEL} nie atakuja i nie sa atakowane przez graczy,</li>
 *   <li>w strefie ochronnej (PZ) nie ma walki - ani atakujacy, ani cel nie moga w niej stac,</li>
 *   <li>tryb PvP (przelacznik czaszki, {@link PlayerCharacter#pvpMode()}): wylaczony = nie atakujesz graczy
 *       i obszarowe czary/runy omijaja graczy; wyjatek - gracz z czaszka (agresor) jest celem dla kazdego,</li>
 *   <li>nie mozna atakowac postaci o ponad polowe slabszej (chyba ze ma czaszke),</li>
 *   <li>trafienie gracza bez czaszki (atak, czar, runa, obszar) daje biala czaszke i blokade wejscia do PZ
 *       na {@link #WHITE_SKULL_MS} (60 s), odnawiane kazdym kolejnym takim trafieniem,</li>
 *   <li>zabicie gracza bez czaszki daje czerwona czaszke i blokade PZ na {@link #KILL_SKULL_MS} (10 min);
 *       {@link #RED_SKULL_KILLS} takie zabojstwa w {@link #KILL_WINDOW_MS} wydluzaja czerwona czaszke do
 *       {@link #RED_SKULL_MS}; smierc z czerwona czaszka = caly plecak zostaje na ziemi,</li>
 *   <li>atak na gracza z czaszka jest "darmowy": bez czaszki i bez blokady PZ dla atakujacego,</li>
 *   <li>PvP nie daje doswiadczenia (brak "farmienia" znajomych), obrazenia graczom sa o polowe mniejsze.</li>
 * </ul>
 */
public final class PvpRules {

    public static final int PROTECTION_LEVEL = 30;
    /** Biala czaszka (i blokada PZ) po trafieniu gracza bez czaszki. */
    public static final long WHITE_SKULL_MS = 60_000L;
    /** Czerwona czaszka (i blokada PZ) po zabiciu gracza bez czaszki. */
    public static final long KILL_SKULL_MS = 10 * 60_000L;
    /** Czerwona czaszka po {@link #RED_SKULL_KILLS} nieusprawiedliwionych zabojstwach w {@link #KILL_WINDOW_MS}. */
    public static final long RED_SKULL_MS = 6 * 3_600_000L;
    public static final long KILL_WINDOW_MS = 24 * 3_600_000L;
    public static final int RED_SKULL_KILLS = 3;
    public static final double PVP_DAMAGE = 0.5;
    /** Po nieusprawiedliwionym trafieniu gracza nie mozna wejsc do strefy ochronnej przez ten czas. */
    public static final long FIGHT_LOCK_MS = WHITE_SKULL_MS;

    public enum Skull { NONE, WHITE, RED }

    public enum Verdict {
        OK(null),
        SELF("Nie możesz atakować samego siebie."),
        DEAD("Cel nie żyje."),
        ATTACKER_PROTECTED("Do poziomu " + PROTECTION_LEVEL + " nie możesz atakować graczy."),
        TARGET_PROTECTED("Ten gracz jest chroniony (poniżej poziomu " + PROTECTION_LEVEL + ")."),
        PROTECTION_ZONE("W strefie ochronnej nie ma walki."),
        LEVEL_GAP("Ten gracz jest o wiele słabszy - nie możesz go zaatakować."),
        PVP_OFF("Włącz tryb PvP (czaszka obok ekwipunku albo /pvp), aby atakować graczy."),
        OTHER_FLOOR("Cel jest na innym piętrze.");

        public final String message;

        Verdict(String message) { this.message = message; }
    }

    /** Stan PvP gracza (czaszki, nieusprawiedliwione zabojstwa, odstep atakow). */
    public static final class Status {
        long whiteUntil;
        long redUntil;
        long nextAttackMs;
        long fightUntil;
        final ArrayDeque<Long> unjustKills = new ArrayDeque<>();
        /** Pojedynek (tylko w pamieci): imie przeciwnika malymi literami i koniec czasu walki; null = brak. */
        String duelPartner;
        long duelUntil;

        public String duelPartner() { return duelPartner; }
        public long duelUntil() { return duelUntil; }
        public boolean duelling(String name) {
            return duelPartner != null && duelPartner.equals(name.toLowerCase(java.util.Locale.ROOT));
        }

        /** Do kiedy trwa blokada strefy ochronnej po walce z graczem (czas serwera). */
        public long fightUntil() { return fightUntil; }

        /** Czy postac ma czaszke (jest agresorem) - wtedy kazdy moze ja atakowac, takze bez trybu PvP. */
        public boolean aggressor(long now) { return skull(now) != Skull.NONE; }

        /** Kiedy znika obecna czaszka (0 = brak). */
        public long skullUntil(long now) {
            if (now < redUntil) return redUntil;
            return now < whiteUntil ? whiteUntil : 0;
        }

        /** Pozostaly czas blokady wejscia do strefy ochronnej (0 = brak). */
        public long pzBlockedMs(long now) { return Math.max(0, fightUntil - now); }

        /**
         * Zapis na dysk: czasy jako zegar scienny (czas serwera liczy sie od startu procesu), wiec czaszki
         * i zabojstwa przetrwaja restart serwera. Format: "white;red;kill1,kill2,..." (ms epoki, 0 = brak).
         */
        public String encode(long now, long wallNow) {
            prune(now);
            StringBuilder sb = new StringBuilder();
            sb.append(toWall(whiteUntil, now, wallNow)).append(';').append(toWall(redUntil, now, wallNow)).append(';');
            boolean first = true;
            for (long k : unjustKills) {
                if (!first) sb.append(',');
                sb.append(wallNow + (k - now));
                first = false;
            }
            return sb.toString();
        }

        public static Status decode(String line, long now, long wallNow) {
            Status s = new Status();
            if (line == null || line.isBlank()) return s;
            String[] f = line.trim().split(";", -1);
            if (f.length < 2) return s;
            try {
                s.whiteUntil = fromWall(Long.parseLong(f[0]), now, wallNow);
                s.redUntil = fromWall(Long.parseLong(f[1]), now, wallNow);
                if (f.length > 2 && !f[2].isEmpty()) {
                    for (String k : f[2].split(",")) s.unjustKills.addLast(now + (Long.parseLong(k) - wallNow));
                }
            } catch (NumberFormatException e) {
                return new Status(); // uszkodzony zapis: bez czaszek (bezpieczniej niz blad logowania)
            }
            s.prune(now);
            return s;
        }

        private static long toWall(long until, long now, long wallNow) {
            return until <= now ? 0 : wallNow + (until - now);
        }

        private static long fromWall(long wall, long now, long wallNow) {
            return wall <= wallNow ? 0 : now + (wall - wallNow);
        }

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
    public enum Outcome { NOT_ALLOWED, NOT_READY, OUT_OF_REACH, HIT, KILLED, DUEL_WON }

    /** Pojedynek trwa najwyzej tyle; potem remis. */
    public static final long DUEL_MS = 5 * 60_000L;

    /** Poczatek pojedynku: bez czaszek, bez ochrony poziomu, do pierwszej krwi (przegrany zostaje z 1 HP). */
    public static void startDuel(String a, Status as, String b, Status bs, long now) {
        as.duelPartner = b.toLowerCase(java.util.Locale.ROOT);
        bs.duelPartner = a.toLowerCase(java.util.Locale.ROOT);
        as.duelUntil = bs.duelUntil = now + DUEL_MS;
    }

    public static void endDuel(Status as, Status bs) {
        as.duelPartner = null;
        bs.duelPartner = null;
    }

    private PvpRules() {}

    /** Czy a moze zaatakowac b (bez sprawdzania zasiegu). */
    public static Verdict check(PlayerCharacter a, PlayerCharacter b, Status bStatus, WorldMap world, long now) {
        if (a == b || a.id().equals(b.id())) return Verdict.SELF;
        if (b.isDead() || a.isDead()) return Verdict.DEAD;
        boolean duel = bStatus != null && bStatus.duelling(a.name());
        if (!duel && a.level() < PROTECTION_LEVEL) return Verdict.ATTACKER_PROTECTED;
        if (!duel && b.level() < PROTECTION_LEVEL) return Verdict.TARGET_PROTECTED;
        if (a.position().z() != b.position().z()) return Verdict.OTHER_FLOOR;
        if (world.isProtectionZone(a.position()) || world.isProtectionZone(b.position())) return Verdict.PROTECTION_ZONE;
        if (duel) return Verdict.OK; // pojedynek: poziomy bez znaczenia
        boolean marked = bStatus != null && bStatus.skull(now) != Skull.NONE;
        if (!marked && !a.pvpMode()) return Verdict.PVP_OFF; // agresora (z czaszka) wolno bic bez trybu PvP
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
        int raw = CombatFormulas.roll(ap.maxDamage(), rng);
        if (a.isDefensive()) raw = (int) Math.round(raw * CombatFormulas.DEFENSIVE_OUTGOING);
        return hit(attacker, as, victim, vs, raw, ap.type(), now, rng);
    }

    /**
     * Czar albo runa na gracza: te same zasady co atak (bez odstepu atakow - pilnuja go czasy odnowienia czarow).
     * raw = wylosowana wartosc czaru przed modyfikatorem PvP.
     */
    public static Outcome spellHit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                                   DamageType type, WorldMap world, long now, Random rng) {
        if (check(attacker.character(), victim.character(), vs, world, now) != Verdict.OK) return Outcome.NOT_ALLOWED;
        return hit(attacker, as, victim, vs, raw, type, now, rng);
    }

    /** Wspolna czesc trafienia: blokada PZ, czaszki, obrazenia x0,5, zdarzenia, smierc i czerwona czaszka. */
    private static Outcome hit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                               DamageType type, long now, Random rng) {
        PlayerCharacter a = attacker.character();
        PlayerCharacter b = victim.character();
        if (vs.duelling(a.name())) return duelHit(attacker, as, victim, vs, raw, type, now, rng);
        boolean justified = vs.skull(now) != Skull.NONE;
        if (!justified) {
            // agresja: biala czaszka i blokada PZ (odnawiane kazdym trafieniem); bicie agresora jest darmowe
            if (as.skull(now) == Skull.NONE) {
                attacker.log().add(now, EventLog.Kind.WARNING, "Zaatakowałeś gracza " + b.name()
                        + " - biała czaszka, strefa ochronna zamknięta na " + WHITE_SKULL_MS / 1000 + " s.");
            }
            as.whiteUntil = Math.max(as.whiteUntil, now + WHITE_SKULL_MS);
            block(attacker, as, now + WHITE_SKULL_MS);
        }
        raw = (int) Math.round(raw * PVP_DAMAGE);
        int dmg = CombatFormulas.mitigateOnPlayer(b, raw, type, b.isDefensive(), rng);
        int dealt = b.damage(dmg);
        victim.combat().recordDamage(a.name() + " (gracz)", dealt, now);
        CombatEvent.Kind kind = dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED;
        victim.combat().addEvent(new CombatEvent(now, b.position(), dealt, kind));
        attacker.combat().addEvent(new CombatEvent(now, b.position(), dealt,
                dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        if (!b.isDead()) return Outcome.HIT;

        boolean dropAll = vs.skull(now) == Skull.RED;
        victim.combat().diedFromPlayer(a.name(), dropAll, now);
        attacker.log().add(now, EventLog.Kind.WARNING, "Zabiłeś gracza " + b.name() + ".");
        if (!justified) {
            as.unjustKills.addLast(now);
            long red = as.unjustKills(now) >= RED_SKULL_KILLS ? RED_SKULL_MS : KILL_SKULL_MS;
            as.redUntil = Math.max(as.redUntil, now + red);
            block(attacker, as, now + KILL_SKULL_MS);
            attacker.log().add(now, EventLog.Kind.WARNING, "Czerwona czaszka na "
                    + (red == RED_SKULL_MS ? "6 h" : KILL_SKULL_MS / 60_000 + " min")
                    + ": strefa ochronna zamknięta na " + KILL_SKULL_MS / 60_000 + " min, przy śmierci stracisz cały plecak.");
        }
        return Outcome.KILLED;
    }

    /**
     * Stan jak po trafieniu (WHITE) albo zabiciu (RED) gracza bez czaszki - podglad na zrzutach ekranu
     * (-Dpixel.open=pvp|pzblock), bez drugiego gracza.
     */
    public static void markForPreview(Status st, Skull skull, long now) {
        long ms = skull == Skull.RED ? KILL_SKULL_MS : WHITE_SKULL_MS;
        if (skull == Skull.RED) st.redUntil = Math.max(st.redUntil, now + ms);
        else if (skull == Skull.WHITE) st.whiteUntil = Math.max(st.whiteUntil, now + ms);
        else return;
        st.fightUntil = Math.max(st.fightUntil, now + ms);
    }

    /** Blokada wejscia do PZ do czasu until (tylko wydluza) - w stanie PvP i w sesji gracza. */
    private static void block(GameSession s, Status st, long until) {
        st.fightUntil = Math.max(st.fightUntil, until);
        s.lockProtectionZonesUntil(st.fightUntil);
    }

    /** Trafienie w pojedynku: bez czaszek i blokady PZ; cios, ktory by zabil, konczy pojedynek przy 1 HP. */
    private static Outcome duelHit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                                   DamageType type, long now, Random rng) {
        PlayerCharacter a = attacker.character();
        PlayerCharacter b = victim.character();
        raw = (int) Math.round(raw * PVP_DAMAGE);
        int dmg = CombatFormulas.mitigateOnPlayer(b, raw, type, b.isDefensive(), rng);
        boolean won = dmg >= b.health();
        if (won) dmg = b.health() - 1;
        int dealt = b.damage(dmg);
        victim.combat().addEvent(new CombatEvent(now, b.position(), dealt,
                dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED));
        attacker.combat().addEvent(new CombatEvent(now, b.position(), dealt,
                dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        if (!won) return Outcome.HIT;
        endDuel(as, vs);
        attacker.log().add(now, EventLog.Kind.SYSTEM, "Wygrywasz pojedynek z " + b.name() + "!");
        victim.log().add(now, EventLog.Kind.SYSTEM, "Przegrywasz pojedynek z " + a.name() + ".");
        return Outcome.DUEL_WON;
    }
}
