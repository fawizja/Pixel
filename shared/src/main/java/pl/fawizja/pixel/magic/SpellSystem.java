package pl.fawizja.pixel.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.combat.CombatEvent;
import pl.fawizja.pixel.combat.CombatSystem;
import pl.fawizja.pixel.combat.DamageType;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.inventory.Container;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemCategory;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.vocation.Vocation;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Czary, runy, mikstury i jedzenie. Kazda akcja jest najpierw w pelni sprawdzana, a dopiero potem
 * zuzywa mane / przedmiot i uruchamia czasy odnowienia - nieudana proba nic nie kosztuje.
 */
public final class SpellSystem {

    /** Rozrzut wartosci czarow: 85%..115%. */
    public static final double MIN_ROLL = 0.85;
    public static final double MAX_ROLL = 1.15;
    public static final long LIGHT_SPELL_MS = 5 * 60_000L;

    private final PlayerCharacter pc;
    private final WorldMap world;
    private final ItemRegistry items;
    private final SpellRegistry spells;
    private final ConsumableRegistry consumables;
    private final CombatSystem combat;
    private final MonsterManager monsters;
    private final EventLog log;
    private final Random rng;
    private final Cooldowns cooldowns = new Cooldowns();
    private PlayerTargeting playerTarget;

    /**
     * Cel-gracz dla czarow i run (PvP, ustawia serwer). Uzywany, gdy postac nie ma celu-potwora.
     * Zasady PvP (poziomy, PZ, czaszki) sprawdza implementacja w {@link #strike}.
     */
    public interface PlayerTargeting {
        /** Pozycja atakowanego gracza albo null, gdy nie ma celu. */
        Position position();

        /** Zadaje obrazenia czaru; false, gdy zasady PvP na to nie pozwalaja. */
        boolean strike(int amount, DamageType type, long now);

        /**
         * Czar/runa obszarowa: trafia graczy w promieniu radius od center, ktorych zasady PvP pozwalaja zaatakowac
         * (tryb PvP wlaczony albo gracz z czaszka); reach = zasieg wzroku od srodka. Zwraca liczbe trafionych.
         * Domyslnie (bez listy graczy) tylko wybrany cel-gracz.
         */
        default int strikeArea(Position center, int radius, java.util.function.Predicate<Position> reach,
                               java.util.function.IntSupplier amount, DamageType type, long now) {
            Position p = position();
            if (p == null || p.z() != center.z() || p.distanceTo(center) > radius || !reach.test(p)) return 0;
            return strike(amount.getAsInt(), type, now) ? 1 : 0;
        }

        /** Czy w obszarze stoi gracz, ktorego wolno trafic (czar obszarowy bez potworow w zasiegu). */
        default boolean anyInArea(Position center, int radius, java.util.function.Predicate<Position> reach) {
            Position p = position();
            return p != null && p.z() == center.z() && p.distanceTo(center) <= radius && reach.test(p);
        }

        /** Czy rzucajacy nie moze teraz uzywac czarow i run obszarowych (czarna czaszka). */
        default boolean areaForbidden(long now) { return false; }
    }

    public void setPlayerTargeting(PlayerTargeting t) { playerTarget = t; }

    /** Czlonek druzyny widziany przez mnicha (serwer): pozycja, zdrowie i leczenie. */
    public interface Ally {
        Position position();
        int health();
        /** Leczy i zwraca, ile faktycznie przybylo. */
        int heal(int amount);
    }

    /**
     * Druzyna rzucajacego (ustawia serwer; offline nikogo nie ma): do spokoju mnicha, leczenia z harmonii
     * i Zbiorowego uleczenia ducha.
     */
    public interface Allies {
        /** Czlonkowie druzyny (bez rzucajacego) na tym samym pietrze w zasiegu ekranu (|dx| <= 8, |dy| <= 6). */
        List<Ally> visible();

        /** Inni gracze na 8 polach wokol rzucajacego (licza sie do tlumu, ktory odbiera spokoj). */
        default int playersAround() { return 0; }
    }

    private Allies allies;

    public void setAllies(Allies a) { allies = a; }

    private List<Ally> visibleAllies() {
        return allies == null ? java.util.Collections.emptyList() : allies.visible();
    }

    /** Cel-gracz, gdy brak celu-potwora (inaczej null). */
    private Position playerTargetPosition() {
        Monster m = combat.target();
        if (m != null && !m.isDead()) return null;
        return playerTarget == null ? null : playerTarget.position();
    }

    public SpellSystem(PlayerCharacter pc, WorldMap world, ItemRegistry items, SpellRegistry spells,
                       ConsumableRegistry consumables, CombatSystem combat, MonsterManager monsters, EventLog log, Random rng) {
        this.pc = pc;
        this.world = world;
        this.items = items;
        this.spells = spells;
        this.consumables = consumables;
        this.combat = combat;
        this.monsters = monsters;
        this.log = log;
        this.rng = rng;
    }

    public Cooldowns cooldowns() { return cooldowns; }
    public SpellRegistry registry() { return spells; }

    /** Czary profesji odblokowane na obecnym poziomie (pasek szybkiego dostepu). */
    public List<SpellType> knownSpells() {
        List<SpellType> out = new ArrayList<>();
        for (SpellType s : spells.forVocation(pc.vocation())) {
            if (pc.level() >= s.minLevel() && pc.skills().level(Skill.MAGIC) >= s.minMagic()) out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ formuly

    /**
     * Wartosc bazowa efektu bez losowania (do opisow i testow). Czar ataku mnicha z rola harmonii liczy sie
     * wzorem Canary: moc * piesci / 100 * atak broni / 10 + premia poziomu (bez premii harmonii).
     */
    public static double baseValue(SpellType s, PlayerCharacter pc) {
        if (s.monkFormula()) return monkPower(s, pc);
        double v = s.base() + pc.level() * s.levelFactor() + pc.skills().level(Skill.MAGIC) * s.magicFactor()
                + pc.effectiveSkill(weaponSkill(pc)) * s.skillFactor();
        if (s.kind().isHeal()) v *= pc.vocation().healingFactor();
        else if (s.kind().isRune()) v *= pc.vocation().magicFactor();
        return Math.max(0, v);
    }

    /**
     * Umiejetnosc broni do czarow (jak w Tibii exori liczy sie z umiejetnosci trzymanej broni): rycerz z mieczem,
     * toporem albo maczuga - ta umiejetnosc, bez broni wrecz - najwyzsza z trzech.
     */
    public static Skill weaponSkill(PlayerCharacter pc) {
        Skill base = weaponSkill(pc.vocation());
        if (base != Skill.SWORD) return base;
        Skill held = pc.equipment().weaponType().skill();
        if (held.isMeleeWeapon()) return held;
        Skill best = Skill.SWORD;
        for (Skill s : new Skill[] {Skill.AXE, Skill.CLUB}) if (pc.skills().level(s) > pc.skills().level(best)) best = s;
        return best;
    }

    public static Skill weaponSkill(Vocation v) {
        switch (v) {
            case PALADIN: return Skill.DISTANCE;
            case MONK: return Skill.FIST;
            default: return Skill.SWORD;
        }
    }

    /**
     * Canary (np. swift_jab.lua): SPELL_BASE_POWER * (skill / 100) * (attack / 10) + calculateFlatDamageHealing();
     * atak = bron w rece (kastety), bez broni piesci {@link pl.fawizja.pixel.combat.CombatFormulas#FIST_ATTACK}.
     */
    public static double monkPower(SpellType s, PlayerCharacter pc) {
        int attack = pc.equipment().weaponType() != pl.fawizja.pixel.item.WeaponType.NONE
                ? pc.equipment().weaponAttack() : pl.fawizja.pixel.combat.CombatFormulas.FIST_ATTACK;
        return s.base() * pc.effectiveSkill(Skill.FIST) / 100.0 * attack / 10.0
                + pl.fawizja.pixel.vocation.Harmony.flatDamageHealing(pc.level());
    }

    /** Srednia wartosc efektu teraz: wartosc bazowa z premia harmonii (spender) i cnota wytrwalosci (leczenie mnicha). */
    public double expectedValue(SpellType s) {
        double v = baseValue(s, pc);
        if (s.monkRole() == MonkRole.SPENDER) v *= pc.harmony().bonus();
        if (s.kind().isHeal() && pc.vocation() == Vocation.MONK) v *= pc.harmony().healFactor();
        return v;
    }

    /** Rozrzut czarow mnicha jak w Canary: od -10% do +10%. */
    public static final double MONK_ROLL = 0.10;

    /** Wartosc efektu z losowaniem: czary mnicha +-10% i premia harmonii (spender), leczenie mnicha x cnota wytrwalosci. */
    private int roll(SpellType s) {
        double v = baseValue(s, pc) * (s.monkFormula() ? 1 - MONK_ROLL + rng.nextDouble() * 2 * MONK_ROLL
                : MIN_ROLL + rng.nextDouble() * (MAX_ROLL - MIN_ROLL));
        if (s.monkRole() == MonkRole.SPENDER) v *= pc.harmony().bonus();
        if (s.kind().isHeal() && pc.vocation() == Vocation.MONK) v *= pc.harmony().healFactor();
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    // ------------------------------------------------------------------ harmonia mnicha

    /**
     * Po udanym czarze (Canary Spell::postCastSpell): spender leczy za wszystkie punkty, zeruje je, skraca odnowienie
     * czarow budujacych o 2 s za punkt, a z cnota harmonii od razu buduje 1 punkt; builder dodaje 1 punkt.
     */
    private void afterMonkCast(SpellType s, long now) {
        pl.fawizja.pixel.vocation.Harmony h = pc.harmony();
        if (s.monkRole() == MonkRole.SPENDER) {
            int spent = h.points();
            harmonyHeal(spent, now);
            h.spend();
            if (spent > 0) {
                for (SpellType b : spells.all()) {
                    if (b.monkRole() == MonkRole.BUILDER) cooldowns.cut(b, pl.fawizja.pixel.vocation.Harmony.COOLDOWN_CUT_PER_POINT_MS * spent, now);
                }
            }
            if (h.virtue() == pl.fawizja.pixel.vocation.Harmony.Virtue.HARMONY) buildHarmony(1, now);
        } else if (s.monkRole() == MonkRole.BUILDER) {
            buildHarmony(1, now);
        }
    }

    /** Buduje punkty harmonii; kazde zbudowanie leczy (Canary buildHarmony -> healFromHarmony). */
    private void buildHarmony(int charges, long now) {
        int added = pc.harmony().build(charges);
        if (added > 0) harmonyHeal(added, now);
    }

    /**
     * Leczenie z harmonii (Canary Player::healFromHarmony): najbardziej ranny widoczny czlonek druzyny, gdy ma mniej
     * zdrowia niz mnich, inaczej sam mnich. Cnota wytrwalosci go nie wzmacnia (ORIGIN_HARMONY).
     */
    private void harmonyHeal(int charges, long now) {
        int amount = pl.fawizja.pixel.combat.CombatFormulas.rollBetween(pl.fawizja.pixel.vocation.Harmony.healMin(pc.level(), charges),
                pl.fawizja.pixel.vocation.Harmony.healMax(pc.level(), charges), rng);
        Ally best = null;
        int lowest = pc.health();
        for (Ally a : visibleAllies()) {
            if (a.health() > 0 && a.health() < lowest) {
                best = a;
                lowest = a.health();
            }
        }
        if (best == null) {
            int healed = pc.heal(amount);
            if (healed > 0) combat.addEvent(new CombatEvent(now, pc.position(), healed, CombatEvent.Kind.HEAL));
        } else {
            int healed = best.heal(amount);
            if (healed > 0) combat.addEvent(new CombatEvent(now, best.position(), healed, CombatEvent.Kind.HEAL));
        }
    }

    /**
     * Spokoj mnicha co krok gry (Canary Player::updateSerenityState): traci go mnich, ktory widzi czlonka druzyny
     * i ma na 8 polach wokol co najmniej 6 stworzen (potwory i inni gracze). Zwraca true, gdy stan sie zmienil.
     */
    public boolean updateSerenity(long dtMs, long now) {
        if (pc.vocation() != Vocation.MONK) return false;
        Position me = pc.position();
        int around = 0;
        for (Monster m : monsters.near(me, 1)) {
            if (!m.isDead() && m.position().z() == me.z() && !m.position().equals(me) && me.distanceTo(m.position()) <= 1) around++;
        }
        if (allies != null) around += allies.playersAround();
        // bez wpisu w dzienniku (w tlumie stan zmienia sie czesto): spokoj widac przy kulach harmonii w HUD
        return pc.harmony().update(dtMs, !visibleAllies().isEmpty(), around);
    }

    // ------------------------------------------------------------------ czary

    /** Najsilniejszy czar leczacy, ktory postac moze rzucic teraz (profesja, poziom, mana, odnowienie), albo null. */
    public String bestHeal(long now) {
        SpellType best = null;
        for (SpellType s : spells.all()) {
            if (s.kind() != SpellKind.HEAL_SELF || check(s, now, true) != CastResult.OK) continue;
            if (best == null || baseValue(s, pc) > baseValue(best, pc)) best = s;
        }
        return best == null ? null : best.key();
    }

    public CastResult cast(String key, long now) {
        SpellType s = spells.find(key);
        if (s == null || s.kind().isRune()) return CastResult.NOT_USABLE;
        CastResult r = check(s, now, true);
        if (r != CastResult.OK) return report(r, now);
        r = apply(s, now);
        if (r != CastResult.OK) return report(r, now);
        pc.spendMana(s.mana());
        trainMagic(s.mana(), now);
        if (s.monkRole() != MonkRole.NONE) afterMonkCast(s, now);
        cooldowns.trigger(s, now);
        return CastResult.OK;
    }

    private CastResult check(SpellType s, long now, boolean spell) {
        if (pc.isDead()) return CastResult.DEAD;
        if (spell && !s.allowedFor(pc.vocation())) return CastResult.WRONG_VOCATION;
        if (pc.level() < s.minLevel()) return CastResult.LEVEL_TOO_LOW;
        if (pc.skills().level(Skill.MAGIC) < s.minMagic()) return CastResult.MAGIC_TOO_LOW;
        if (spell && pc.mana() < s.mana()) return CastResult.NOT_ENOUGH_MANA;
        if (!cooldowns.ready(s, now)) return CastResult.COOLDOWN;
        if (s.kind().isOffensive() && world.isProtectionZone(pc.position())) return CastResult.PROTECTION_ZONE;
        if ((s.kind() == SpellKind.ATTACK_AREA || s.kind() == SpellKind.RUNE_AREA) && playerTarget != null
                && playerTarget.areaForbidden(now)) return CastResult.BLACK_SKULL;
        if (s.kind().needsTarget()) {
            Monster t = combat.target();
            Position tp = t != null && !t.isDead() ? t.position() : playerTargetPosition();
            if (tp == null || tp.z() != pc.position().z()) return CastResult.NO_TARGET;
            if (pc.position().distanceTo(tp) > Math.max(1, s.range())) return CastResult.OUT_OF_RANGE;
            if (!world.hasLineOfSight(pc.position(), tp)) return CastResult.NOT_VISIBLE;
        }
        if (s.kind() == SpellKind.ATTACK_AREA && targetsAround(pc.position(), s.radius()).isEmpty()
                && !playerInArea(pc.position(), s.radius())) return CastResult.NO_TARGET;
        if (s.kind() == SpellKind.CONJURE && s.consumesItem() != null
                && pc.backpack().count(items.byKey(s.consumesItem()).id()) < 1) return CastResult.MISSING_ITEM;
        return CastResult.OK;
    }

    /** Wykonuje efekt (po sprawdzeniu warunkow). Dla CONJURE moze odmowic (pelny plecak) bez kosztow. */
    private CastResult apply(SpellType s, long now) {
        switch (s.kind()) {
            case HEAL_SELF:
            case RUNE_HEAL: {
                int healed = pc.heal(roll(s));
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HEAL"));
                combat.addEvent(new CombatEvent(now, pc.position(), healed, CombatEvent.Kind.HEAL));
                return CastResult.OK;
            }
            case ATTACK_TARGET:
            case RUNE_TARGET:
                combat.addEvent(CombatEvent.projectile(now, pc.position(),
                        playerTargetPosition() != null ? playerTargetPosition() : combat.target().position(), s.damageType().name()));
                if (playerTargetPosition() != null) {
                    return playerTarget.strike(roll(s), s.damageType(), now) ? CastResult.OK : CastResult.NOT_ALLOWED;
                }
                combat.damageMonster(combat.target(), roll(s), s.damageType(), now);
                return CastResult.OK;
            case ATTACK_AREA:
                combat.addEvent(CombatEvent.area(now, pc.position(), s.radius(), s.damageType().name()));
                for (Monster m : limitTargets(targetsAround(pc.position(), s.radius()), s.produceCount())) {
                    combat.damageMonster(m, roll(s), s.damageType(), now);
                }
                strikePlayers(pc.position(), s, now);
                return CastResult.OK;
            case HEAL_PARTY: {
                // Zbiorowe uleczenie ducha: mnich i druzyna w promieniu, kazdy z osobnym losowaniem (Canary mass_spirit_mend)
                combat.addEvent(CombatEvent.area(now, pc.position(), s.radius(), "HEAL"));
                int healed = pc.heal(roll(s));
                combat.addEvent(new CombatEvent(now, pc.position(), healed, CombatEvent.Kind.HEAL));
                for (Ally a : visibleAllies()) {
                    if (a.health() <= 0 || a.position().z() != pc.position().z() || pc.position().distanceTo(a.position()) > s.radius()) continue;
                    int h = a.heal(roll(s));
                    if (h > 0) combat.addEvent(new CombatEvent(now, a.position(), h, CombatEvent.Kind.HEAL));
                }
                return CastResult.OK;
            }
            case VIRTUE: {
                pl.fawizja.pixel.vocation.Harmony.Virtue v = pl.fawizja.pixel.vocation.Harmony.Virtue.ofSpell(s.key());
                if (v == null) return CastResult.NOT_USABLE;
                pc.harmony().setVirtue(v);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HEAL"));
                log.add(now, EventLog.Kind.INFO, v.label + ": " + v.effect + ".");
                return CastResult.OK;
            }
            case FOCUS_SERENITY:
                // Canary focus_serenity.lua: zeruje odnowienia czarow wydajacych, spokoj na 7 s, pelna harmonia
                for (SpellType o : spells.all()) if (o.monkRole() == MonkRole.SPENDER) cooldowns.cut(o, Long.MAX_VALUE, now);
                pc.harmony().forceSerene(pl.fawizja.pixel.vocation.Harmony.FOCUS_SERENE_MS);
                buildHarmony(pl.fawizja.pixel.vocation.Harmony.MAX, now);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + ": pełna harmonia i spokój na 7 s.");
                return CastResult.OK;
            case FOCUS_HARMONY:
                buildHarmony(pl.fawizja.pixel.vocation.Harmony.MAX, now);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + ": pełna harmonia.");
                return CastResult.OK;
            case RUNE_AREA: {
                Position pvp = playerTargetPosition();
                Position center = pvp != null ? pvp : combat.target().position();
                combat.addEvent(CombatEvent.projectile(now, pc.position(), center, s.damageType().name()));
                combat.addEvent(CombatEvent.area(now, center, s.radius(), s.damageType().name()));
                for (Monster m : targetsAround(center, s.radius())) combat.damageMonster(m, roll(s), s.damageType(), now);
                strikePlayers(center, s, now);
                return CastResult.OK;
            }
            case LIGHT:
                pc.addLightBuff(s.radius(), LIGHT_SPELL_MS);
                log.add(now, EventLog.Kind.INFO, "Światło otacza postać.");
                return CastResult.OK;
            case HASTE: {
                int before = pc.speed();
                pc.addHaste((int) Math.round(s.base()), s.range() * 1000L);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + ": szybkość " + before + " -> " + pc.speed() + " na " + s.range() + " s.");
                return CastResult.OK;
            }
            case CONJURE: {
                Container copy = pc.backpack().copy();
                if (s.consumesItem() != null && copy.remove(items.byKey(s.consumesItem()).id(), 1) != InventoryResult.OK) {
                    return CastResult.MISSING_ITEM;
                }
                ItemType made = items.byKey(s.producesItem());
                if (copy.add(new ItemStack(made.id(), s.produceCount())) != InventoryResult.OK) return CastResult.BACKPACK_FULL;
                pc.backpack().commitFrom(copy);
                log.add(now, EventLog.Kind.LOOT, "Stworzono: " + s.produceCount() + "x " + made.name());
                return CastResult.OK;
            }
            default:
                return CastResult.NOT_USABLE;
        }
    }

    /** Czy w obszarze czaru stoi gracz, ktorego zasady PvP pozwalaja trafic. */
    private boolean playerInArea(Position center, int radius) {
        return playerTarget != null && playerTarget.anyInArea(center, radius, p -> world.hasLineOfSight(center, p));
    }

    /** Obszar trafia graczy (tryb PvP albo agresorzy) - zasady w implementacji PlayerTargeting (PvpTargeting). */
    private void strikePlayers(Position center, SpellType s, long now) {
        if (playerTarget == null) return;
        playerTarget.strikeArea(center, s.radius(), p -> world.hasLineOfSight(center, p), () -> roll(s), s.damageType(), now);
    }

    /**
     * Czar lancuchowy mnicha (Lancuch pokuty, Wybuch ducha): kolumna "ilosc" = najwiecej celow; najpierw wybrany cel,
     * potem najblizsze. 0 = wszystkie w obszarze.
     */
    private List<Monster> limitTargets(List<Monster> all, int max) {
        if (max <= 0 || all.size() <= max) return all;
        Monster t = combat.target();
        Position me = pc.position();
        all.sort((a, b) -> {
            if (a == t) return -1;
            if (b == t) return 1;
            return Integer.compare(me.distanceTo(a.position()), me.distanceTo(b.position()));
        });
        return new ArrayList<>(all.subList(0, max));
    }

    private List<Monster> targetsAround(Position center, int radius) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : monsters.near(center, radius)) {
            if (!m.isDead() && m.position().z() == center.z() && world.hasLineOfSight(center, m.position())) out.add(m);
        }
        return out;
    }

    private void trainMagic(int mana, long now) {
        if (mana <= 0) return;
        // jak w Tibii: kazdy punkt wydanej many (takze na tworzenie run) to punkt do poziomu magii;
        // profesja decyduje tylko o krzywej (Skills.vocationConstant), mnoznik serwera dolicza Skills.train,
        // a Balance.HUNT_TRY_FACTOR wyrownuje tempo magii do szybkich poziomow
        int gained = pc.skills().train(Skill.MAGIC, (long) mana * pl.fawizja.pixel.balance.Balance.HUNT_TRY_FACTOR);
        if (gained > 0) log.add(now, EventLog.Kind.SYSTEM, "Poziom magii: " + pc.skills().level(Skill.MAGIC));
    }

    // ------------------------------------------------------------------ przedmioty

    /** Uzycie przedmiotu z plecaka: mikstura, jedzenie albo runa (na cel / na siebie). */
    public CastResult useItem(int slot, long now) {
        ItemStack st = pc.backpack().get(slot);
        if (st == null) return CastResult.MISSING_ITEM;
        ItemType it = items.get(st.itemId());
        if (it.category() == ItemCategory.RUNE) return useRune(slot, it, now);
        Consumable c = consumables.get(it.id());
        if (c == null) return report(CastResult.NOT_USABLE, now);
        if (pc.isDead()) return CastResult.DEAD;
        if (c.isFood()) {
            if (!pc.feed(c.foodSeconds() * 1000L)) return report(CastResult.FULL, now);
            pc.backpack().takeFromSlot(slot, 1);
            return CastResult.OK;
        }
        if (!it.vocations().contains(pc.vocation())) return report(CastResult.WRONG_VOCATION, now);
        if (pc.level() < it.minLevel()) return report(CastResult.LEVEL_TOO_LOW, now);
        if (!cooldowns.groupReady(SpellGroup.POTION, now)) return CastResult.COOLDOWN;
        boolean hpNeeded = c.restoresHealth() && pc.health() < pc.maxHealth();
        boolean manaNeeded = c.restoresMana() && pc.mana() < pc.maxMana();
        if (!hpNeeded && !manaNeeded) return report(CastResult.FULL, now);
        pc.backpack().takeFromSlot(slot, 1);
        if (c.restoresHealth()) {
            int healed = pc.heal(c.hpMin() + rng.nextInt(c.hpMax() - c.hpMin() + 1));
            combat.addEvent(new CombatEvent(now, pc.position(), healed, CombatEvent.Kind.HEAL));
        }
        if (c.restoresMana()) pc.restoreMana(c.manaMin() + rng.nextInt(c.manaMax() - c.manaMin() + 1));
        cooldowns.triggerGroup(SpellGroup.POTION, now, Math.max(c.cooldownMs(), SpellGroup.POTION.groupCooldownMs));
        return CastResult.OK;
    }

    /** Pierwszy slot z danym przedmiotem (przyciski mikstur w HUD). */
    public CastResult useFirst(String itemKey, long now) {
        int id = items.byKey(itemKey).id();
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == id) return useItem(i, now);
        }
        return report(CastResult.MISSING_ITEM, now);
    }

    private CastResult useRune(int slot, ItemType rune, long now) {
        SpellType effect = spells.find(rune.key());
        if (effect == null || !effect.kind().isRune()) return report(CastResult.NOT_USABLE, now);
        CastResult r = check(effect, now, false);
        if (r != CastResult.OK) return report(r, now);
        r = apply(effect, now);
        if (r != CastResult.OK) return report(r, now);
        pc.backpack().takeFromSlot(slot, 1);
        cooldowns.trigger(effect, now);
        return CastResult.OK;
    }

    private CastResult report(CastResult r, long now) {
        if (r != CastResult.OK && r != CastResult.COOLDOWN) log.add(now, EventLog.Kind.WARNING, r.message);
        return r;
    }
}
