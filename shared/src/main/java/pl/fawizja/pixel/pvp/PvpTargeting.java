package pl.fawizja.pixel.pvp;

import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.GameSession;
import pl.fawizja.pixel.magic.SpellSystem;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Czary i runy na graczy (wspolne dla serwera i testow z "udawanym" drugim graczem): cel-gracz dla czarow
 * celowanych oraz wszyscy gracze w obszarze dla czarow i run obszarowych. O tym, kogo wolno trafic, decyduje
 * {@link PvpRules#check} (celowane: tryb PvP/bezpieczny, czaszka ofiary, PZ, ochrona poziomu) i
 * {@link PvpRules#checkArea} (obszar: jak w Tibii tryb bezpieczny nie chroni). Druzyny obszar omija.
 * Stan PvP kazdego gracza to {@link GameSession#pvpStatus()}.
 */
public final class PvpTargeting implements SpellSystem.PlayerTargeting {

    /** Po kazdym trafieniu gracza (np. serwer: nagroda za glowe po zabiciu, koniec pojedynku). */
    public interface Listener {
        void hit(GameSession victim, PvpRules.Outcome outcome, long now);
    }

    private final GameSession me;
    private final Supplier<GameSession> target;
    private final Supplier<? extends Iterable<GameSession>> others;
    private final BiPredicate<GameSession, GameSession> friendly;
    private final WorldMap world;
    private final Random rng;
    private final Listener listener;

    /**
     * @param me       rzucajacy
     * @param target   wybrany cel-gracz (albo null)
     * @param others   gracze w poblizu (moze zawierac rzucajacego - jest pomijany)
     * @param friendly sojusznicy (druzyna) - obszar ich omija, chyba ze to pojedynek
     */
    public PvpTargeting(GameSession me, Supplier<GameSession> target, Supplier<? extends Iterable<GameSession>> others,
                        BiPredicate<GameSession, GameSession> friendly, WorldMap world, Random rng, Listener listener) {
        this.me = me;
        this.target = target;
        this.others = others;
        this.friendly = friendly == null ? (a, b) -> false : friendly;
        this.world = world;
        this.rng = rng;
        this.listener = listener == null ? (v, o, t) -> { } : listener;
    }

    @Override
    public Position position() {
        GameSession t = target.get();
        return t == null ? null : t.character().position();
    }

    @Override
    public boolean strike(int amount, DamageType type, long now) {
        GameSession t = target.get();
        if (t == null) return false;
        PvpRules.Outcome o = PvpRules.spellHit(me, me.pvpStatus(), t, t.pvpStatus(), amount, type, world, now, rng);
        if (o != PvpRules.Outcome.NOT_ALLOWED) listener.hit(t, o, now);
        return o != PvpRules.Outcome.NOT_ALLOWED;
    }

    @Override
    public int strikeArea(Position center, int radius, Predicate<Position> reach, IntSupplier amount, DamageType type,
                          long now) {
        int hits = 0;
        for (GameSession o : others.get()) {
            if (!hittable(o, center, radius, reach, now)) continue;
            PvpRules.Outcome out = PvpRules.areaHit(me, me.pvpStatus(), o, o.pvpStatus(), amount.getAsInt(), type,
                    world, now, rng);
            if (out == PvpRules.Outcome.NOT_ALLOWED) continue;
            hits++;
            listener.hit(o, out, now);
        }
        return hits;
    }

    @Override
    public boolean anyInArea(Position center, int radius, Predicate<Position> reach) {
        long now = me.lastUpdateMs() < 0 ? 0 : me.lastUpdateMs();
        for (GameSession o : others.get()) if (hittable(o, center, radius, reach, now)) return true;
        return false;
    }

    private boolean hittable(GameSession o, Position center, int radius, Predicate<Position> reach, long now) {
        if (o == null || o == me || o.character().isDead()) return false;
        if (friendly.test(me, o) && !o.pvpStatus().duelling(me.character().name())) return false;
        Position p = o.character().position();
        if (p.z() != center.z() || p.distanceTo(center) > radius || !reach.test(p)) return false;
        return PvpRules.checkArea(me.character(), me.pvpStatus(), o.character(), o.pvpStatus(), world, now)
                == PvpRules.Verdict.OK;
    }

    /** Czarna czaszka nie uzywa czarow ani run obszarowych (jak w Tibii). */
    @Override
    public boolean areaForbidden(long now) {
        return me.pvpStatus().skull(now) == PvpRules.Skull.BLACK;
    }
}
