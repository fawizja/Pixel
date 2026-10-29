package pl.fawizja.pixel.pvp;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
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
 * Zasady walki gracz kontra gracz jak w Tibii (swiat Open PvP; zrodla i roznice w docs/GAME_SYSTEMS.md):
 * <ul>
 *   <li>postacie ponizej poziomu {@link #PROTECTION_LEVEL} nie walcza z graczami (odpowiednik Rookgaardu),</li>
 *   <li>w strefie ochronnej (PZ) nie ma walki; czlonkow wlasnej druzyny nie mozna atakowac (serwer),</li>
 *   <li>przelacznik PvP = tryb bezpieczny Tibii odwrotnie: wylaczony (tryb bezpieczny) = nie atakujesz
 *       nieoznaczonych graczy celowanym atakiem, czarem ani runa; czary obszarowe rania ich jednak tak jak
 *       w Tibii ({@link #SECURE_MODE_BLOCKS_AREA}). Gracza z czaszka (dla nas oznaczonego) wolno bic zawsze,</li>
 *   <li>kazda agresja wobec gracza: atakujacy - blokada PZ i wylogowania {@link #PZ_BLOCK_MS} (1 min od ostatniej
 *       agresji), ofiara - blokada wylogowania {@link #LOGOUT_BLOCK_MS},</li>
 *   <li>atak na nieoznaczonego gracza: biala czaszka tak dlugo jak blokada wylogowania (1 min);
 *       atak na oznaczonego jest usprawiedliwiony: bez czaszki, ale ofiara widzi atakujacego z zolta czaszka
 *       ({@link #YELLOW_SKULL_MS}) i moze mu oddac bez kary,</li>
 *   <li>zabicie nieoznaczonego gracza (nieusprawiedliwione): biala czaszka i blokada PZ/wylogowania na
 *       {@link #KILL_BLOCK_MS} (15 min), ofiara widzi zabojce z pomaranczowa czaszka (zemsta) przez
 *       {@link #REVENGE_MS} (7 dni) albo do zemsty,</li>
 *   <li>czerwona czaszka: 3 nieusprawiedliwione zabojstwa w 24 h, 5 w 7 dni albo 10 w 30 dniach; trwa
 *       {@link #RED_SKULL_MS} (30 dni) od zabojstwa ponad limit. Czarna: 6 / 10 / 20, trwa {@link #BLACK_SKULL_MS}
 *       (45 dni). Smierc z czerwona albo czarna czaszka = caly plecak i ekwipunek zostaja na ziemi,</li>
 *   <li>czarna czaszka: nie atakuje nieoznaczonych graczy (nawet w trybie PvP), nie uzywa czarow ani run
 *       obszarowych, dostaje 100% obrazen od graczy, odradza sie z {@link #BLACK_SKULL_RESPAWN_HP} HP i 0 many,</li>
 *   <li>obrazenia miedzy graczami: {@link #PVP_DAMAGE} (50%), na czarna czaszke 100%; PvP nie daje doswiadczenia.</li>
 * </ul>
 */
public final class PvpRules {

    public static final int PROTECTION_LEVEL = 30;
    private static final long MIN = 60_000L;
    private static final long DAY = 24 * 60 * MIN;

    /** Blokada wylogowania po kazdej agresji gracz-gracz (atakujacy i ofiara): 60 s od ostatniej agresji. */
    public static final long LOGOUT_BLOCK_MS = MIN;
    /** Blokada wejscia do PZ atakujacego (trwa tyle co blokada wylogowania). */
    public static final long PZ_BLOCK_MS = LOGOUT_BLOCK_MS;
    /** Biala czaszka po samym ataku na nieoznaczonego gracza: tyle co blokada wylogowania. */
    public static final long WHITE_SKULL_MS = LOGOUT_BLOCK_MS;
    /** Zolta czaszka (widzi ja tylko zaatakowany): tyle co blokada wylogowania. */
    public static final long YELLOW_SKULL_MS = LOGOUT_BLOCK_MS;
    /** Po nieusprawiedliwionym zabojstwie: biala czaszka, blokada PZ i wylogowania na 15 min. */
    public static final long KILL_BLOCK_MS = 15 * MIN;
    /** Pomaranczowa czaszka (zemsta) na zabojcy, widoczna dla ofiary: 7 dni albo do zemsty. */
    public static final long REVENGE_MS = 7 * DAY;
    public static final long RED_SKULL_MS = 30 * DAY;
    public static final long BLACK_SKULL_MS = 45 * DAY;
    /** Okna liczenia nieusprawiedliwionych zabojstw: doba, tydzien, 30 dni. */
    public static final long[] KILL_WINDOWS_MS = {DAY, 7 * DAY, 30 * DAY};
    /** Progi czerwonej czaszki dla okien {@link #KILL_WINDOWS_MS}. */
    public static final int[] RED_SKULL_KILLS = {3, 5, 10};
    /** Progi czarnej czaszki dla okien {@link #KILL_WINDOWS_MS}. */
    public static final int[] BLACK_SKULL_KILLS = {6, 10, 20};
    /** Zabojstwa starsze niz najdluzsze okno sa zapominane. */
    public static final long KILL_MEMORY_MS = 30 * DAY;
    public static final double PVP_DAMAGE = 0.5;
    /** Czarna czaszka dostaje pelne obrazenia od graczy. */
    public static final double BLACK_SKULL_DAMAGE = 1.0;
    public static final int BLACK_SKULL_RESPAWN_HP = 40;
    /**
     * Czy tryb bezpieczny (przelacznik PvP wylaczony) chroni nieoznaczonych graczy przed czarami obszarowymi.
     * W Tibii nie chroni ("doesn't prevent attack with spells like GFB") - false.
     */
    public static final boolean SECURE_MODE_BLOCKS_AREA = false;

    /**
     * Czaszki. NONE/WHITE/RED/BLACK widza wszyscy; YELLOW i ORANGE widzi tylko jeden gracz
     * ({@link #skullSeenBy}). Zielonej (druzyna) nie ma - w Tibii zniknela ze swiatow Open PvP w 2013 r.
     */
    public enum Skull { NONE, WHITE, YELLOW, ORANGE, RED, BLACK }

    public enum Verdict {
        OK(null),
        SELF("Nie możesz atakować samego siebie."),
        DEAD("Cel nie żyje."),
        ATTACKER_PROTECTED("Do poziomu " + PROTECTION_LEVEL + " nie możesz atakować graczy."),
        TARGET_PROTECTED("Ten gracz jest chroniony (poniżej poziomu " + PROTECTION_LEVEL + ")."),
        PROTECTION_ZONE("W strefie ochronnej nie ma walki."),
        BLACK_SKULL("Z czarną czaszką nie możesz atakować graczy bez czaszki."),
        PVP_OFF("Włącz tryb PvP (czaszka obok ekwipunku albo /pvp), aby atakować graczy bez czaszki."),
        OTHER_FLOOR("Cel jest na innym piętrze.");

        public final String message;

        Verdict(String message) { this.message = message; }
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    /** Stan PvP gracza (czaszki, blokady, nieusprawiedliwione zabojstwa, zemsta, odstep atakow). */
    public static final class Status {
        long whiteUntil;
        long redUntil;
        long blackUntil;
        long nextAttackMs;
        /** Blokada wejscia do PZ. */
        long fightUntil;
        /** Blokada wylogowania. */
        long logoutUntil;
        final ArrayDeque<Long> unjustKills = new ArrayDeque<>();
        /** Gracze, ktorych widzimy z zolta czaszka (zaatakowali nas, majac nas za oznaczonych): imie -> do kiedy. */
        final Map<String, Long> yellow = new HashMap<>();
        /** Nasi zabojcy (pomaranczowa czaszka, zemsta): imie -> czasy nieusprawiedliwionych zabojstw. */
        final Map<String, ArrayDeque<Long>> revenge = new HashMap<>();
        /** Pojedynek (tylko w pamieci): imie przeciwnika malymi literami i koniec czasu walki; null = brak. */
        String duelPartner;
        long duelUntil;

        public String duelPartner() { return duelPartner; }
        public long duelUntil() { return duelUntil; }
        public boolean duelling(String name) {
            return duelPartner != null && duelPartner.equals(key(name));
        }

        /** Do kiedy trwa blokada strefy ochronnej po walce z graczem (czas serwera). */
        public long fightUntil() { return fightUntil; }

        /** Do kiedy trwa blokada wylogowania (czas serwera). */
        public long logoutUntil() { return logoutUntil; }

        /** Konczy blokady wylogowania i PZ od razu (polecenie moderatora, testy); czaszki zostaja. */
        public void liftBlocks() {
            logoutUntil = 0;
            fightUntil = 0;
        }

        /** Pozostaly czas blokady wylogowania (0 = mozna wyjsc). */
        public long logoutBlockedMs(long now) { return Math.max(0, logoutUntil - now); }

        /** Czy postac ma czaszke widoczna dla wszystkich - wtedy kazdy moze ja atakowac bez kary. */
        public boolean aggressor(long now) { return skull(now) != Skull.NONE; }

        /** Kiedy znika obecna czaszka (0 = brak). */
        public long skullUntil(long now) {
            if (now < blackUntil) return blackUntil;
            if (now < redUntil) return redUntil;
            return now < whiteUntil ? whiteUntil : 0;
        }

        /** Pozostaly czas blokady wejscia do strefy ochronnej (0 = brak). */
        public long pzBlockedMs(long now) { return Math.max(0, fightUntil - now); }

        /** Czaszka widoczna dla wszystkich: BLACK, RED, WHITE albo NONE. */
        public Skull skull(long now) {
            if (now < blackUntil) return Skull.BLACK;
            if (now < redUntil) return Skull.RED;
            if (now < whiteUntil) return Skull.WHITE;
            return Skull.NONE;
        }

        /** Czy widzimy gracza name z zolta czaszka. */
        public boolean yellowOf(String name, long now) {
            Long until = yellow.get(key(name));
            return until != null && now < until;
        }

        /** Czy mamy zemste (pomaranczowa czaszke) na graczu name. */
        public boolean revengeOn(String name, long now) {
            ArrayDeque<Long> k = revenge.get(key(name));
            if (k == null) return false;
            for (long t : k) if (now - t < REVENGE_MS) return true;
            return false;
        }

        /** Nieusprawiedliwione zabojstwa z ostatnich 30 dni. */
        public int unjustKills(long now) {
            prune(now);
            return unjustKills.size();
        }

        /** Nieusprawiedliwione zabojstwa z ostatnich windowMs. */
        public int unjustKills(long now, long windowMs) {
            prune(now);
            int n = 0;
            for (long k : unjustKills) if (now - k < windowMs) n++;
            return n;
        }

        private void prune(long now) {
            for (Iterator<Long> it = unjustKills.iterator(); it.hasNext(); ) {
                if (now - it.next() >= KILL_MEMORY_MS) it.remove();
            }
            yellow.values().removeIf(u -> u <= now);
            for (Iterator<ArrayDeque<Long>> it = revenge.values().iterator(); it.hasNext(); ) {
                ArrayDeque<Long> k = it.next();
                while (!k.isEmpty() && now - k.peekFirst() >= REVENGE_MS) k.removeFirst();
                if (k.isEmpty()) it.remove();
            }
        }

        /**
         * Zapis na dysk: czasy jako zegar scienny (czas serwera liczy sie od startu procesu), wiec czaszki,
         * blokady, zabojstwa i zemsta przetrwaja restart serwera. Format (ms epoki, 0 = brak):
         * "biala;czerwona;zab1,zab2,...;czarna;blokadaPz;blokadaWylogowania;imie@t1/t2+imie2@t3".
         * Starszy zapis "biala;czerwona;zabojstwa" tez sie wczyta.
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
            sb.append(';').append(toWall(blackUntil, now, wallNow));
            sb.append(';').append(toWall(fightUntil, now, wallNow));
            sb.append(';').append(toWall(logoutUntil, now, wallNow)).append(';');
            first = true;
            for (Map.Entry<String, ArrayDeque<Long>> e : revenge.entrySet()) {
                if (!revengeOn(e.getKey(), now)) continue;
                if (!first) sb.append('+');
                sb.append(e.getKey()).append('@');
                boolean f2 = true;
                for (long k : e.getValue()) {
                    if (!f2) sb.append('/');
                    sb.append(wallNow + (k - now));
                    f2 = false;
                }
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
                if (f.length > 3 && !f[3].isEmpty()) s.blackUntil = fromWall(Long.parseLong(f[3]), now, wallNow);
                if (f.length > 4 && !f[4].isEmpty()) s.fightUntil = fromWall(Long.parseLong(f[4]), now, wallNow);
                if (f.length > 5 && !f[5].isEmpty()) s.logoutUntil = fromWall(Long.parseLong(f[5]), now, wallNow);
                if (f.length > 6 && !f[6].isEmpty()) {
                    for (String r : f[6].split("\\+")) {
                        int at = r.lastIndexOf('@');
                        if (at <= 0) continue;
                        ArrayDeque<Long> times = new ArrayDeque<>();
                        for (String k : r.substring(at + 1).split("/")) times.addLast(now + (Long.parseLong(k) - wallNow));
                        s.revenge.put(key(r.substring(0, at)), times);
                    }
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
    }

    /**
     * Czaszka gracza target (imie targetName, stan ts) w oczach gracza viewer (stan vs): czarna, czerwona,
     * pomaranczowa (nasz zabojca), biala, zolta (zaatakowal nas, gdy mielismy czaszke) albo NONE.
     */
    public static Skull skullSeenBy(Status viewer, String targetName, Status ts, long now) {
        Skull global = ts == null ? Skull.NONE : ts.skull(now);
        if (global == Skull.BLACK || global == Skull.RED) return global;
        if (viewer != null && viewer.revengeOn(targetName, now)) return Skull.ORANGE;
        if (global == Skull.WHITE) return global;
        if (viewer != null && viewer.yellowOf(targetName, now)) return Skull.YELLOW;
        return Skull.NONE;
    }

    /** Czy atak a na b jest usprawiedliwiony (b ma dla a jakakolwiek czaszke). */
    public static boolean marked(Status as, String bName, Status bs, long now) {
        return skullSeenBy(as, bName, bs, now) != Skull.NONE;
    }

    /** Wynik jednego ataku. */
    public enum Outcome { NOT_ALLOWED, NOT_READY, OUT_OF_REACH, HIT, KILLED, DUEL_WON }

    /** Pojedynek trwa najwyzej tyle; potem remis. */
    public static final long DUEL_MS = 5 * 60_000L;

    /** Poczatek pojedynku: bez czaszek, bez ochrony poziomu, do pierwszej krwi (przegrany zostaje z 1 HP). */
    public static void startDuel(String a, Status as, String b, Status bs, long now) {
        as.duelPartner = key(b);
        bs.duelPartner = key(a);
        as.duelUntil = bs.duelUntil = now + DUEL_MS;
    }

    public static void endDuel(Status as, Status bs) {
        as.duelPartner = null;
        bs.duelPartner = null;
    }

    private PvpRules() {}

    /** Czy a moze zaatakowac b (bez stanu a: bez zoltych i pomaranczowych czaszek). */
    public static Verdict check(PlayerCharacter a, PlayerCharacter b, Status bStatus, WorldMap world, long now) {
        return check(a, null, b, bStatus, world, now);
    }

    /** Czy a moze zaatakowac b celowanym atakiem, czarem albo runa (bez sprawdzania zasiegu). */
    public static Verdict check(PlayerCharacter a, Status aStatus, PlayerCharacter b, Status bStatus, WorldMap world,
                                long now) {
        return check(a, aStatus, b, bStatus, world, now, false);
    }

    /** Jak {@link #check}, ale dla czaru obszarowego: tryb bezpieczny nie chroni ({@link #SECURE_MODE_BLOCKS_AREA}). */
    public static Verdict checkArea(PlayerCharacter a, Status aStatus, PlayerCharacter b, Status bStatus,
                                    WorldMap world, long now) {
        return check(a, aStatus, b, bStatus, world, now, !SECURE_MODE_BLOCKS_AREA);
    }

    private static Verdict check(PlayerCharacter a, Status aStatus, PlayerCharacter b, Status bStatus, WorldMap world,
                                 long now, boolean ignoreSecureMode) {
        if (a == b || a.id().equals(b.id())) return Verdict.SELF;
        if (b.isDead() || a.isDead()) return Verdict.DEAD;
        boolean duel = bStatus != null && bStatus.duelling(a.name());
        if (!duel && a.level() < PROTECTION_LEVEL) return Verdict.ATTACKER_PROTECTED;
        if (!duel && b.level() < PROTECTION_LEVEL) return Verdict.TARGET_PROTECTED;
        if (a.position().z() != b.position().z()) return Verdict.OTHER_FLOOR;
        if (world.isProtectionZone(a.position()) || world.isProtectionZone(b.position())) return Verdict.PROTECTION_ZONE;
        if (duel) return Verdict.OK; // pojedynek: poziomy bez znaczenia
        boolean marked = marked(aStatus, b.name(), bStatus, now);
        if (!marked && aStatus != null && aStatus.skull(now) == Skull.BLACK) return Verdict.BLACK_SKULL;
        if (!marked && !a.pvpMode() && !ignoreSecureMode) return Verdict.PVP_OFF; // tryb bezpieczny
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
        if (check(a, as, b, vs, world, now) != Verdict.OK) return Outcome.NOT_ALLOWED;
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
     * Czar albo runa celowana na gracza: te same zasady co atak (bez odstepu atakow - pilnuja go czasy odnowienia
     * czarow). raw = wylosowana wartosc czaru przed modyfikatorem PvP.
     */
    public static Outcome spellHit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                                   DamageType type, WorldMap world, long now, Random rng) {
        if (check(attacker.character(), as, victim.character(), vs, world, now) != Verdict.OK) return Outcome.NOT_ALLOWED;
        return hit(attacker, as, victim, vs, raw, type, now, rng);
    }

    /** Trafienie gracza czarem albo runa obszarowa (tryb bezpieczny nie chroni - jak w Tibii). */
    public static Outcome areaHit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                                  DamageType type, WorldMap world, long now, Random rng) {
        if (checkArea(attacker.character(), as, victim.character(), vs, world, now) != Verdict.OK) {
            return Outcome.NOT_ALLOWED;
        }
        return hit(attacker, as, victim, vs, raw, type, now, rng);
    }

    /** Wspolna czesc trafienia: blokady, czaszki, obrazenia, zdarzenia, smierc, zemsta i czerwona/czarna czaszka. */
    private static Outcome hit(GameSession attacker, Status as, GameSession victim, Status vs, int raw,
                               DamageType type, long now, Random rng) {
        PlayerCharacter a = attacker.character();
        PlayerCharacter b = victim.character();
        if (vs.duelling(a.name())) return duelHit(attacker, as, victim, vs, raw, type, now, rng);
        boolean justified = marked(as, b.name(), vs, now);
        // kazda agresja: blokada PZ i wylogowania atakujacego, blokada wylogowania ofiary (1 min od ostatniej)
        block(attacker, as, now + PZ_BLOCK_MS);
        as.logoutUntil = Math.max(as.logoutUntil, now + LOGOUT_BLOCK_MS);
        vs.logoutUntil = Math.max(vs.logoutUntil, now + LOGOUT_BLOCK_MS);
        if (!justified) {
            if (as.skull(now) == Skull.NONE) {
                attacker.log().add(now, EventLog.Kind.WARNING, "Zaatakowałeś gracza " + b.name()
                        + " - biała czaszka, strefa ochronna i wylogowanie zablokowane na " + WHITE_SKULL_MS / 1000 + " s.");
            }
            as.whiteUntil = Math.max(as.whiteUntil, now + WHITE_SKULL_MS);
        } else if (as.skull(now) == Skull.NONE) {
            // usprawiedliwiony atak bez wlasnej czaszki: ofiara widzi nas z zolta czaszka i moze oddac bez kary
            vs.yellow.put(key(a.name()), now + YELLOW_SKULL_MS);
        }
        Skull victimSkull = vs.skull(now);
        raw = (int) Math.round(raw * (victimSkull == Skull.BLACK ? BLACK_SKULL_DAMAGE : PVP_DAMAGE));
        int dmg = CombatFormulas.mitigateOnPlayer(b, raw, type, b.isDefensive(), rng);
        int dealt = b.damage(dmg);
        victim.combat().recordDamage(a.name() + " (gracz)", dealt, now);
        CombatEvent.Kind kind = dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED;
        victim.combat().addEvent(new CombatEvent(now, b.position(), dealt, kind));
        attacker.combat().addEvent(new CombatEvent(now, b.position(), dealt,
                dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        if (!b.isDead()) return Outcome.HIT;

        boolean dropAll = victimSkull == Skull.RED || victimSkull == Skull.BLACK;
        victim.combat().diedFromPlayer(a.name(), dropAll, now);
        if (victimSkull == Skull.BLACK) {
            // czarna czaszka: odrodzenie z 40 HP i bez many
            b.damage(b.health() - Math.min(b.health(), BLACK_SKULL_RESPAWN_HP));
            b.spendMana(b.mana());
        }
        // smierc konczy blokady ofiary (odradza sie w swiatyni)
        vs.fightUntil = 0;
        vs.logoutUntil = 0;
        vs.yellow.clear();
        b.setPzBlockedUntil(0);
        attacker.log().add(now, EventLog.Kind.WARNING, "Zabiłeś gracza " + b.name() + ".");
        if (justified) {
            // zemsta wykonana: jedna pomaranczowa czaszka mniej
            ArrayDeque<Long> r = as.revenge.get(key(b.name()));
            if (r != null) {
                r.pollFirst();
                if (r.isEmpty()) as.revenge.remove(key(b.name()));
            }
            return Outcome.KILLED;
        }
        unjustifiedKill(attacker, as, b.name(), vs, now);
        return Outcome.KILLED;
    }

    /** Nieusprawiedliwione zabojstwo: 15 min bialej czaszki i blokad, zemsta ofiary, progi czerwonej i czarnej. */
    private static void unjustifiedKill(GameSession attacker, Status as, String victimName, Status vs, long now) {
        as.unjustKills.addLast(now);
        as.whiteUntil = Math.max(as.whiteUntil, now + KILL_BLOCK_MS);
        as.logoutUntil = Math.max(as.logoutUntil, now + KILL_BLOCK_MS);
        block(attacker, as, now + KILL_BLOCK_MS);
        vs.revenge.computeIfAbsent(key(attacker.character().name()), k -> new ArrayDeque<>()).addLast(now);
        Skull before = as.skull(now);
        Skull after = applyThresholds(as, now);
        String msg;
        if (after == Skull.BLACK && before != Skull.BLACK) {
            msg = "Czarna czaszka na 45 dni: nie zaatakujesz graczy bez czaszki ani czarem obszarowym, gracze zadają ci "
                    + "pełne obrażenia, przy śmierci tracisz wszystko i odradzasz się z 40 HP.";
        } else if (after == Skull.RED && before != Skull.RED) {
            msg = "Czerwona czaszka na 30 dni: przy śmierci stracisz cały plecak i ekwipunek.";
        } else {
            msg = "Nieusprawiedliwione zabójstwo: biała czaszka, strefa ochronna i wylogowanie zablokowane na "
                    + KILL_BLOCK_MS / 60_000 + " min.";
        }
        attacker.log().add(now, EventLog.Kind.WARNING, msg + " Zabójstwa: " + as.unjustKills(now, KILL_WINDOWS_MS[0])
                + "/24 h, " + as.unjustKills(now, KILL_WINDOWS_MS[1]) + "/7 dni, "
                + as.unjustKills(now, KILL_WINDOWS_MS[2]) + "/30 dni.");
    }

    /**
     * Po zapisaniu nieusprawiedliwionego zabojstwa: czarna czaszka (45 dni), gdy zabojstwa w ktoryms oknie
     * osiagnely {@link #BLACK_SKULL_KILLS}, inaczej czerwona (30 dni od tego zabojstwa) przy {@link #RED_SKULL_KILLS}.
     * Zwraca czaszke po zmianie.
     */
    static Skull applyThresholds(Status as, long now) {
        if (reached(as, BLACK_SKULL_KILLS, now)) {
            as.blackUntil = Math.max(as.blackUntil, now + BLACK_SKULL_MS);
        } else if (reached(as, RED_SKULL_KILLS, now)) {
            as.redUntil = Math.max(as.redUntil, now + RED_SKULL_MS);
        }
        return as.skull(now);
    }

    /** Czy zabojstwa w ktoryms oknie {@link #KILL_WINDOWS_MS} osiagnely prog. */
    static boolean reached(Status s, int[] limits, long now) {
        for (int i = 0; i < KILL_WINDOWS_MS.length; i++) {
            if (s.unjustKills(now, KILL_WINDOWS_MS[i]) >= limits[i]) return true;
        }
        return false;
    }

    /**
     * Stan jak po trafieniu (WHITE) albo zabiciu gracza (RED, BLACK) - podglad na zrzutach ekranu
     * (-Dpixel.open=pvp|pzblock), bez drugiego gracza.
     */
    public static void markForPreview(Status st, Skull skull, long now) {
        switch (skull) {
            case WHITE -> st.whiteUntil = Math.max(st.whiteUntil, now + WHITE_SKULL_MS);
            case RED -> st.redUntil = Math.max(st.redUntil, now + RED_SKULL_MS);
            case BLACK -> st.blackUntil = Math.max(st.blackUntil, now + BLACK_SKULL_MS);
            default -> { return; }
        }
        long block = skull == Skull.WHITE ? PZ_BLOCK_MS : KILL_BLOCK_MS;
        st.fightUntil = Math.max(st.fightUntil, now + block);
        st.logoutUntil = Math.max(st.logoutUntil, now + block);
    }

    /** Blokada wejscia do PZ do czasu until (tylko wydluza) - w stanie PvP i w sesji gracza. */
    private static void block(GameSession s, Status st, long until) {
        st.fightUntil = Math.max(st.fightUntil, until);
        s.lockProtectionZonesUntil(st.fightUntil);
    }

    /** Trafienie w pojedynku: bez czaszek i blokad; cios, ktory by zabil, konczy pojedynek przy 1 HP. */
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
