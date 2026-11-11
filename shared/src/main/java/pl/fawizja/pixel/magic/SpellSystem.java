package pl.fawizja.pixel.magic;

import pl.fawizja.pixel.i18n.Messages;
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

        /** Jak {@link #strike(int, DamageType, long)}; amount zawiera juz krytyk, critical = oznaczenie trafienia. */
        default boolean strike(int amount, DamageType type, boolean critical, int targets, long now) {
            return strike(amount, type, now);
        }

        /** Jak strikeArea ponizej, z oznaczeniem krytyka i liczba celow rzutu (kradziez zycia/many). */
        default int strikeArea(Position center, int radius, java.util.function.Predicate<Position> reach,
                               java.util.function.IntSupplier amount, DamageType type, boolean critical, int targets, long now) {
            return strikeArea(center, radius, reach, amount, type, now);
        }

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
        /** Imie (exura sio "imie"); domyslnie puste. */
        default String name() { return ""; }
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
        if (aimTile != null) return aimPlayer ? aimTile : null; // celownik: gracz tylko na wskazanym polu
        Monster m = target();
        if (m != null && !m.isDead()) return null;
        return playerTarget == null ? null : playerTarget.position();
    }

    // ------------------------------------------------------------------ celownik run (jak "Use with..." w Tibii)

    /** Pole wskazane celownikiem albo "na siebie" (null = zwykly cel), potwor na nim i czy stoi tam cel-gracz. */
    private Position aimTile;
    private Monster aimMonster;
    private boolean aimPlayer;

    /**
     * Cel czaru albo runy: przy celowniku potwor na wskazanym polu (moze byc null - wtedy runa na cel odmowi),
     * inaczej wybrany cel walki. Uzycie celownika nie zmienia celu walki (jak w Tibii).
     */
    Monster target() { return aimTile != null ? aimMonster : combat.target(); }

    /**
     * Runa z pola plecaka na wskazane pole (celownik) albo na siebie (pole postaci). Jak w Tibii: zasieg runy
     * i linia wzroku do pola; runa na stworzenie (nagla smierc, paraliz...) wymaga stworzenia na tym polu, runa obszarowa
     * moze trafic w puste pole, runa leczaca tylko na siebie. Inne przedmioty: na siebie jak zwykle uzycie.
     */
    public CastResult useItemAt(int slot, Position tile, long now) {
        ItemStack st = pc.backpack().get(slot);
        if (st == null) return report(CastResult.MISSING_ITEM, now);
        if (tile == null) return useItem(slot, now);
        ItemType it = items.get(st.itemId());
        if (it.category() != ItemCategory.RUNE) {
            if (!tile.equals(pc.position())) return report(CastResult.NOT_USABLE, now);
            return useItem(slot, now);
        }
        aimTile = tile;
        Monster on = monsters.monsterAt(tile);
        aimMonster = on != null && !on.isDead() ? on : null;
        aimPlayer = aimMonster == null && playerTarget != null && tile.equals(playerTarget.position());
        try {
            return useRune(slot, it, now);
        } finally {
            aimTile = null;
            aimMonster = null;
            aimPlayer = false;
        }
    }

    /** Pierwsza sztuka przedmiotu z plecaka na pole (celownik) albo na siebie (pole postaci). */
    public CastResult useFirstAt(String itemKey, Position tile, long now) {
        if (!items.containsKey(itemKey)) return report(CastResult.NOT_USABLE, now);
        int id = items.byKey(itemKey).id();
        for (int i = 0; i < pc.backpack().size(); i++) {
            ItemStack st = pc.backpack().get(i);
            if (st != null && st.itemId() == id) return useItemAt(i, tile, now);
        }
        return report(CastResult.MISSING_ITEM, now);
    }

    /** Sprawdzenie celownika (zasieg, pietro, wzrok, rodzaj runy); OK, gdy celownik nie jest uzywany. */
    private CastResult checkAim(SpellType s) {
        if (aimTile == null) return CastResult.OK;
        Position me = pc.position();
        boolean self = aimTile.equals(me);
        if (s.kind() == SpellKind.RUNE_HEAL || s.kind() == SpellKind.RUNE_CURE) return self ? CastResult.OK : CastResult.ONLY_SELF;
        if (aimTile.z() != me.z() || me.distanceTo(aimTile) > Math.max(1, s.range())) return CastResult.OUT_OF_RANGE;
        if (!self && !world.hasLineOfSight(me, aimTile)) return CastResult.NOT_VISIBLE;
        if (s.kind().needsTarget() && s.kind() != SpellKind.RUNE_AREA) {
            if (monsterOnly(s.kind()) ? aimMonster == null : aimMonster == null && !aimPlayer) return CastResult.ONLY_CREATURES;
        }
        return CastResult.OK;
    }

    /** Czy pole jest w zasiegu i polu widzenia runy (podswietlenie celownika na kliencie). */
    public boolean aimReachable(SpellType s, Position tile) {
        Position me = pc.position();
        if (tile == null || tile.z() != me.z()) return false;
        if (s.kind() == SpellKind.RUNE_HEAL || s.kind() == SpellKind.RUNE_CURE) return tile.equals(me);
        return me.distanceTo(tile) <= Math.max(1, s.range()) && (tile.equals(me) || world.hasLineOfSight(me, tile));
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
            if (pc.level() >= s.minLevel() && pc.skills().level(Skill.MAGIC) >= s.minMagic() && pl.fawizja.pixel.wheel.WheelSpells.unlocked(pc, s)) out.add(s);
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
        double v = s.base() + pc.level() * s.levelFactor() + pc.effectiveSkill(Skill.MAGIC) * s.magicFactor()
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
                + pl.fawizja.pixel.vocation.Harmony.flatDamageHealing(pc.level())
                + pl.fawizja.pixel.vocation.Harmony.noviceBonus(pc.level());
    }

    /** Srednia wartosc efektu teraz: wartosc bazowa z premia harmonii (spender) i cnota wytrwalosci (leczenie mnicha). */
    public double expectedValue(SpellType s) {
        double v = baseValue(s, pc);
        if (s.monkRole() == MonkRole.SPENDER) v *= pc.harmony().bonus(pl.fawizja.pixel.wheel.WheelSpells.ascetic(pc));
        if (s.kind().isHeal() && pc.vocation() == Vocation.MONK) v *= pc.harmony().healFactor();
        return v;
    }

    /** Rozrzut czarow mnicha jak w Canary: od -10% do +10%. */
    public static final double MONK_ROLL = 0.10;

    /** Wartosc efektu z losowaniem: czary mnicha +-10% i premia harmonii (spender), leczenie mnicha x cnota wytrwalosci. */
    private int roll(SpellType s) {
        double v = baseValue(s, pc) * (s.monkFormula() ? 1 - MONK_ROLL + rng.nextDouble() * 2 * MONK_ROLL
                : MIN_ROLL + rng.nextDouble() * (MAX_ROLL - MIN_ROLL));
        if (s.monkRole() == MonkRole.SPENDER) v *= pc.harmony().bonus(pl.fawizja.pixel.wheel.WheelSpells.ascetic(pc));
        if (s.kind().isHeal() && pc.vocation() == Vocation.MONK) v *= pc.harmony().healFactor();
        v = v * wheelFactor + pl.fawizja.pixel.wheel.WheelSpells.flatHealing(pc, s); // Kolo Przeznaczenia: ulepszenia i objawienia
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
            pl.fawizja.pixel.wheel.WheelSpells.onHarmonySpent(pc, spent); // Kolo Przeznaczenia: Sanktuarium
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
        int amount = pl.fawizja.pixel.wheel.WheelSpells.harmonyHeal(pc, pl.fawizja.pixel.combat.CombatFormulas.rollBetween(pl.fawizja.pixel.vocation.Harmony.healMin(pc.level(), charges),
                pl.fawizja.pixel.vocation.Harmony.healMax(pc.level(), charges), rng));
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

    /**
     * Kilka czarow z jednego pola paska (combo): rzuca pierwszy z listy, ktory mozna rzucic teraz (profesja, poziom,
     * mana, odnowienie czaru i jego grupy, cel w zasiegu). Jedno nacisniecie = najwyzej jeden czar, wiec odnowienia
     * i grupy z Tibii obowiazuja jak przy osobnych klawiszach. Gdy zaden nie jest gotowy, pierwszy czar podaje powod
     * (bez kosztow). Runy nie wchodza do combo.
     */
    public CastResult castCombo(List<String> keys, long now) {
        SpellType first = null;
        for (String k : keys) {
            SpellType s = spells.find(k);
            if (s == null || s.kind().isRune()) continue;
            if (first == null) first = s;
            if (check(s, now, true) == CastResult.OK) return cast(k, now);
        }
        return first == null ? report(CastResult.NOT_USABLE, now) : cast(first.key(), now);
    }

    /** Czy czar mozna rzucic teraz (bez rzucania i bez komunikatow) - podglad combo na pasku. */
    public boolean ready(String key, long now) {
        SpellType s = spells.find(key);
        return s != null && !s.kind().isRune() && check(s, now, true) == CastResult.OK;
    }

    public CastResult cast(String key, long now) { return cast(key, null, now); }

    /** Dopisek biezacego rzutu (nazwa stworzenia, imie) albo null. */
    private String castParam;

    /**
     * Czar z dopiskiem jak w Tibii: utevo res "wilk", utevo res ina "szkielet", exura sio "imie".
     * Bez dopisku czar wybiera sam (najsilniejszy sluga na ktorego starczy many, wyglad celu, najbardziej ranny).
     */
    public CastResult cast(String key, String param, long now) {
        SpellType s = spells.find(key);
        if (s == null || s.kind().isRune()) return CastResult.NOT_USABLE;
        castParam = param == null || param.isBlank() ? null : param.trim();
        try {
            return castChecked(s, now);
        } finally {
            castParam = null;
        }
    }

    private CastResult castChecked(SpellType s, long now) {
        CastResult r = check(s, now, true);
        if (r != CastResult.OK) return report(r, now);
        // Kolo Przeznaczenia: premie czaru na czas rzutu (mana, odnowienie, wartosc, krytyk, kradziez)
        pl.fawizja.pixel.wheel.SpellAugment aug = pl.fawizja.pixel.wheel.WheelSpells.augment(pc, s);
        pc.wheel().setCastAugment(aug);
        wheelFactor = pl.fawizja.pixel.wheel.WheelSpells.castFactor(pc, s, aug);
        try {
            r = apply(s, now);
        } finally {
            pc.wheel().setCastAugment(null);
            wheelFactor = 1;
        }
        if (r != CastResult.OK) return report(r, now);
        int mana = pl.fawizja.pixel.wheel.WheelSpells.mana(pc, s) + extraMana;
        extraMana = 0;
        pc.spendMana(Math.min(mana, pc.mana()));
        trainMagic(mana, now);
        if (s.monkRole() != MonkRole.NONE) afterMonkCast(s, now);
        long cd = pl.fawizja.pixel.wheel.WheelSpells.cooldownMs(pc, s);
        cooldowns.trigger(s, now, cd);
        for (String k : pl.fawizja.pixel.wheel.WheelSpells.linked(s.key())) if (spells.find(k) != null) cooldowns.trigger(spells.find(k), now, cd);
        if (s.kind().isOffensive() && pc.breakInvisibility()) log.add(now, EventLog.Kind.INFO, Messages.get("combat_system.atakujesz_niewidzialnosc_znika"));
        return CastResult.OK;
    }

    /** Dodatkowa mana rzutu (przywolanie: koszt stworzenia jak w Tibii). */
    private int extraMana;

    /** Nazwa drugiej grupy odnowienia czaru (np. "special", "focus") albo null ({@link SpellGroups}). */
    public static String secondaryGroup(String key) {
        SpellGroups.Secondary sec = SpellGroups.secondary(key);
        return sec == null ? null : sec.group();
    }

    /** Mnoznik wartosci biezacego rzutu z Kola Przeznaczenia (1 poza rzutem). */
    private double wheelFactor = 1;

    /** Promien czaru obszarowego z premia kola. */
    private int radius(SpellType s) { return s.radius() + pc.wheel().castAugment().extraRadius; }

    private CastResult check(SpellType s, long now, boolean spell) {
        if (pc.isDead()) return CastResult.DEAD;
        if (spell && !s.allowedFor(pc.vocation())) return CastResult.WRONG_VOCATION;
        if (pc.level() < s.minLevel()) return CastResult.LEVEL_TOO_LOW;
        if (pc.skills().level(Skill.MAGIC) < s.minMagic()) return CastResult.MAGIC_TOO_LOW;
        if (spell && !pl.fawizja.pixel.wheel.WheelSpells.unlocked(pc, s)) return CastResult.WHEEL_LOCKED;
        if (spell && pc.mana() < pl.fawizja.pixel.wheel.WheelSpells.mana(pc, s)) return CastResult.NOT_ENOUGH_MANA;
        if (!cooldowns.ready(s, now) || pl.fawizja.pixel.wheel.WheelSpells.avatarCoolingDown(pc, s)) return CastResult.COOLDOWN;
        if (s.kind().isOffensive() && world.isProtectionZone(pc.position())) return CastResult.PROTECTION_ZONE;
        if (s.kind().isArea() && playerTarget != null && playerTarget.areaForbidden(now)) return CastResult.BLACK_SKULL;
        CastResult aimed = checkAim(s);
        if (aimed != CastResult.OK) return aimed;
        if (s.kind().needsTarget() && !(aimTile != null && s.kind() == SpellKind.RUNE_AREA)) {
            Monster t = target();
            Position tp = t != null && !t.isDead() ? t.position() : monsterOnly(s.kind()) ? null : playerTargetPosition();
            if (tp == null || tp.z() != pc.position().z()) return CastResult.NO_TARGET;
            if (pc.position().distanceTo(tp) > Math.max(1, s.range())) return CastResult.OUT_OF_RANGE;
            if (!world.hasLineOfSight(pc.position(), tp)) return CastResult.NOT_VISIBLE;
        }
        int extra = pl.fawizja.pixel.wheel.WheelSpells.augment(pc, s).extraRadius;
        if (s.kind() == SpellKind.ATTACK_AREA && SpellAreas.chain(s)) {
            int area = s.radius() + extra;
            if (targetsAround(pc.position(), area).isEmpty() && !playerInArea(pc.position(), area)) return CastResult.NO_TARGET;
        } else if (s.kind() == SpellKind.ATTACK_AREA || s.kind() == SpellKind.ATTACK_WAVE || s.kind() == SpellKind.ATTACK_BEAM) {
            // jak w Tibii czar obszarowy trafia pola swojego ksztaltu; bez nikogo na nich nie marnuje many
            AreaCast c = areaCast(s, pc.position(), extra);
            if (c == null || monstersIn(c).isEmpty() && !playerIn(c)) return CastResult.NO_TARGET;
        }
        if (s.kind().targetsTile() && aimTile == null) {
            Position aim = aim(s);
            if (aim == null) return CastResult.NO_TARGET;
            if (pc.position().distanceTo(aim) > Math.max(1, s.range())) return CastResult.OUT_OF_RANGE;
            if (!aim.equals(pc.position()) && !world.hasLineOfSight(pc.position(), aim)) return CastResult.NOT_VISIBLE;
        }
        if ((s.kind() == SpellKind.CURE || s.kind() == SpellKind.RUNE_CURE)
                && !pc.conditions().has(pl.fawizja.pixel.combat.ConditionType.of(s.damageType()))) return CastResult.NO_CONDITION;
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
                        playerTargetPosition() != null ? playerTargetPosition() : target().position(), s.damageType().name()));
                if (s.kind() == SpellKind.ATTACK_TARGET && s.radius() > 0) {
                    // Boski granat: wybuch wokol celu (Canary AREA_CIRCLE2X2)
                    Position c = playerTargetPosition() != null ? playerTargetPosition() : target().position();
                    AreaCast ac = areaCast(s, c, 0);
                    effect(ac, s.damageType().name(), now);
                    areaStrike(ac, s, monstersIn(ac), now);
                    return CastResult.OK;
                }
                if (playerTargetPosition() != null) {
                    boolean crit = combat.rollCritical();
                    return playerTarget.strike(critAmount(roll(s), crit), s.damageType(), crit, 1, now) ? CastResult.OK : CastResult.NOT_ALLOWED;
                }
                combat.damageMonster(target(), pl.fawizja.pixel.wheel.WheelSpells.vsTarget(pc, s, target(), roll(s)), s.damageType(), now, combat.rollCritical(), 1);
                return CastResult.OK;
            case ATTACK_AREA: {
                AreaCast ac;
                List<Monster> hit;
                if (SpellAreas.chain(s)) {
                    // czar lancuchowy: przeskakuje na najwyzej "ilosc" celow w promieniu
                    ac = new AreaCast(SpellArea.circle(radius(s)), pc.position(), null, pc.position(),
                            SpellArea.circle(radius(s)).visibleTiles(pc.position(), null, pc.position(), world));
                    combat.addEvent(CombatEvent.area(now, pc.position(), radius(s), s.damageType().name()));
                    hit = limitTargets(targetsAround(pc.position(), radius(s)), s.produceCount() + pc.wheel().castAugment().extraTargets);
                } else {
                    ac = areaCast(s, pc.position(), pc.wheel().castAugment().extraRadius);
                    effect(ac, s.damageType().name(), now);
                    hit = monstersIn(ac);
                }
                double recast = pl.fawizja.pixel.wheel.WheelSpells.recast(pc, s);
                areaStrike(ac, s, hit, now);
                if (recast > 0) {
                    // Wybuch ducha z objawieniem: drugie uderzenie z pelnej harmonii (Canary combatRecast, bez 1 s opoznienia)
                    double f = wheelFactor;
                    wheelFactor = f * recast;
                    hit.removeIf(Monster::isDead);
                    areaStrike(ac, s, hit, now);
                    wheelFactor = f;
                }
                return CastResult.OK;
            }
            case ATTACK_BEAM: {
                // promien jak w Tibii (exevo vis lux, exevo gran vis lux, exevo max mort): prosta linia od pola przed
                // postacia w strone patrzenia (albo celu w linii); Wielki promien smierci +1 pole za stopien 2 i 3
                AreaCast ac = areaCast(s, pc.position(), pc.wheel().castAugment().extraRadius);
                effect(ac, s.damageType().name(), now);
                areaStrike(ac, s, monstersIn(ac), now);
                return CastResult.OK;
            }
            case WHEEL_BUFF: {
                String done = pl.fawizja.pixel.wheel.WheelSpells.buff(pc, s);
                if (done == null) return CastResult.WHEEL_LOCKED;
                combat.addEvent(CombatEvent.area(now, pc.position(), s.key().startsWith("avatar_") ? 0 : 1, "HASTE"));
                log.add(now, EventLog.Kind.INFO, done);
                return CastResult.OK;
            }
            case HEAL_PARTY: {
                // exura gran mas res, Zbiorowe uleczenie ducha: rzucajacy i druzyna na polach obszaru (Canary
                // AREA_CIRCLE3X3, AREA_MASS_SPIRIT_MEND), kazdy z osobnym losowaniem
                AreaCast ac = areaCast(s, pc.position(), 0);
                effect(ac, "HEAL", now);
                int healed = pc.heal(roll(s));
                combat.addEvent(new CombatEvent(now, pc.position(), healed, CombatEvent.Kind.HEAL));
                for (Ally a : visibleAllies()) {
                    if (a.health() <= 0 || !ac.contains(a.position())) continue;
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
                log.add(now, EventLog.Kind.INFO, v.label() + ": " + v.effect() + ".");
                return CastResult.OK;
            }
            case FOCUS_SERENITY:
                // Canary focus_serenity.lua: zeruje odnowienia czarow wydajacych, spokoj na 7 s, pelna harmonia
                for (SpellType o : spells.all()) if (o.monkRole() == MonkRole.SPENDER) cooldowns.cut(o, Long.MAX_VALUE, now);
                pc.harmony().forceSerene(pl.fawizja.pixel.vocation.Harmony.FOCUS_SERENE_MS);
                buildHarmony(pl.fawizja.pixel.vocation.Harmony.MAX, now);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + Messages.get("spell_system.pelna_harmonia_i_spokoj"));
                return CastResult.OK;
            case FOCUS_HARMONY:
                buildHarmony(pl.fawizja.pixel.vocation.Harmony.MAX, now);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + Messages.get("spell_system.pelna_harmonia"));
                return CastResult.OK;
            case RUNE_AREA: {
                Position pvp = playerTargetPosition();
                Position center = aimTile != null ? aimTile : pvp != null ? pvp : target().position();
                combat.addEvent(CombatEvent.projectile(now, pc.position(), center, s.damageType().name()));
                // wielka kula ognia i inne: okrag Canary AREA_CIRCLE3X3, eksplozja: krzyz AREA_CIRCLE1X1
                AreaCast ac = areaCast(s, center, 0);
                effect(ac, s.damageType().name(), now);
                areaStrike(ac, s, monstersIn(ac), now);
                return CastResult.OK;
            }
            case LIGHT:
                pc.addLightBuff(s.radius(), s.range() > 0 ? s.range() * 1000L : LIGHT_SPELL_MS);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, Messages.get("spell_system.swiatlo_otacza_postac") + Math.max(1, (s.range() > 0 ? s.range() : LIGHT_SPELL_MS / 1000) / 60) + Messages.get("spell_system.min"));
                return CastResult.OK;
            case HASTE: {
                int before = pc.speed();
                pc.addHaste((int) Math.round(s.base()), s.range() * 1000L);
                combat.addEvent(CombatEvent.area(now, pc.position(), 0, "HASTE"));
                log.add(now, EventLog.Kind.INFO, s.name() + Messages.get("spell_system.szybkosc") + before + " -> " + pc.speed() + " na " + s.range() + " s.");
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
                log.add(now, EventLog.Kind.LOOT, Messages.get("spell_system.stworzono") + s.produceCount() + "x " + made.name());
                return CastResult.OK;
            }
            default:
                return tibia.apply(s, now);
        }
    }

    /** Czy w obszarze czaru stoi gracz, ktorego zasady PvP pozwalaja trafic. */
    private boolean playerInArea(Position center, int radius) {
        return playerTarget != null && playerTarget.anyInArea(center, radius, p -> world.hasLineOfSight(center, p));
    }

    /**
     * Czar albo runa obszarowa: jak w Tibii jeden rzut na krytyk dla calego czaru (wszystkie cele), kradziez
     * zycia/many wg liczby trafionych celow (potwory + gracz w obszarze), osobny rzut obrazen na kazdy cel.
     */
    private void areaStrike(AreaCast c, SpellType s, List<Monster> hit, long now) {
        boolean crit = combat.rollCritical();
        int targets = Math.max(1, hit.size() + (playerIn(c) ? 1 : 0));
        for (Monster m : hit) combat.damageMonster(m, pl.fawizja.pixel.wheel.WheelSpells.vsTarget(pc, s, m, roll(s)), s.damageType(), now, crit, targets);
        if (playerTarget != null) {
            java.util.Set<Position> set = new java.util.HashSet<>(c.tiles());
            playerTarget.strikeArea(c.anchor(), c.area().extent() + 1, set::contains, () -> critAmount(roll(s), crit),
                    s.damageType(), crit, targets, now);
        }
    }

    // ------------------------------------------------------------------ obszary czarow jak w Tibii (SpellArea)

    /**
     * Obszar jednego rzutu: ksztalt z Canary, srodek (kotwica), kierunek (dla obszarow kierunkowych), punkt, z ktorego
     * liczy sie linia wzroku, i trafione pola.
     */
    public record AreaCast(SpellArea area, Position anchor, pl.fawizja.pixel.world.Direction dir, Position origin,
                           List<Position> tiles) {
        public boolean contains(Position p) { return tiles.contains(p); }
    }

    /**
     * Obszar rzutu czaru s. center = srodek obszaru bez kierunku (pole rzucajacego albo cel runy). Fale, promienie
     * i ciosy przed soba zaczynaja sie na polu przed postacia (Canary needDirection) i obracaja sie z kierunkiem;
     * sciany z run ustawiaja sie w poprzek kierunku rzutu (takze po skosie). extra = dodatkowe pola z Kola.
     * null, gdy czar nie ma obszaru.
     */
    public AreaCast areaCast(SpellType s, Position center, int extra) {
        SpellArea a = SpellAreas.of(s, extra);
        if (a == null) return null;
        Position me = pc.position();
        if (SpellAreas.inFront(s, a)) {
            pl.fawizja.pixel.world.Direction d = aimDirection(a);
            Position anchor = me.translate(d.dx, d.dy);
            return new AreaCast(a, anchor, d, me, a.visibleTiles(anchor, d, me, world));
        }
        pl.fawizja.pixel.world.Direction d = a.directional() ? throwDirection(center) : null;
        return new AreaCast(a, center, d, center, a.visibleTiles(center, d, center, world));
    }

    /** Kierunek patrzenia bez skosu (po skosie w Tibii postac patrzy na wschod albo zachod). */
    pl.fawizja.pixel.world.Direction straightFacing() {
        pl.fawizja.pixel.world.Direction d = facing();
        if (d.diagonal()) return d.dx > 0 ? pl.fawizja.pixel.world.Direction.EAST : pl.fawizja.pixel.world.Direction.WEST;
        return d;
    }

    /**
     * Kierunek czaru przed soba: patrzenie postaci jak w Tibii; gdy wybrany cel nie stoi w obszarze w tym kierunku,
     * a stoi w obszarze w innym prostym kierunku - postac obraca sie do niego (wygoda na telefonie).
     */
    pl.fawizja.pixel.world.Direction aimDirection(SpellArea a) {
        Position me = pc.position();
        pl.fawizja.pixel.world.Direction f = straightFacing();
        Position t = null;
        Monster m = target();
        if (m != null && !m.isDead()) t = m.position();
        else if (playerTargetPosition() != null) t = playerTargetPosition();
        if (t == null || t.z() != me.z() || t.equals(me)) return f;
        if (a.tiles(me.translate(f.dx, f.dy), f).contains(t)) return f;
        int dx = t.x() - me.x(), dy = t.y() - me.y();
        pl.fawizja.pixel.world.Direction dom = Math.abs(dx) >= Math.abs(dy)
                ? (dx > 0 ? pl.fawizja.pixel.world.Direction.EAST : pl.fawizja.pixel.world.Direction.WEST)
                : (dy > 0 ? pl.fawizja.pixel.world.Direction.SOUTH : pl.fawizja.pixel.world.Direction.NORTH);
        List<pl.fawizja.pixel.world.Direction> order = new ArrayList<>(List.of(dom, pl.fawizja.pixel.world.Direction.NORTH,
                pl.fawizja.pixel.world.Direction.EAST, pl.fawizja.pixel.world.Direction.SOUTH, pl.fawizja.pixel.world.Direction.WEST));
        for (pl.fawizja.pixel.world.Direction d : order) {
            if (a.tiles(me.translate(d.dx, d.dy), d).contains(t)) return d;
        }
        return f;
    }

    /**
     * Kierunek od postaci do pola celu (jak Canary AreaCombat::getArea): skos, gdy cel lezy po skosie; na wlasnym
     * polu - patrzenie.
     */
    pl.fawizja.pixel.world.Direction throwDirection(Position to) {
        Position me = pc.position();
        int dx = Integer.signum(to.x() - me.x()), dy = Integer.signum(to.y() - me.y());
        if (dx == 0 && dy == 0) return straightFacing();
        return pl.fawizja.pixel.world.Direction.fromDelta(dx, dy);
    }

    /** Potwory (bez slug) na polach obszaru. */
    List<Monster> monstersIn(AreaCast c) {
        List<Monster> out = new ArrayList<>();
        if (c == null) return out;
        java.util.Set<Position> set = new java.util.HashSet<>(c.tiles());
        for (Monster m : monsters.near(c.anchor(), c.area().extent() + 1)) {
            if (!m.isDead() && !m.isSummon() && set.contains(m.position())) out.add(m);
        }
        return out;
    }

    /** Czy na polach obszaru stoi gracz, ktorego zasady PvP pozwalaja trafic. */
    boolean playerIn(AreaCast c) {
        if (playerTarget == null || c == null) return false;
        java.util.Set<Position> set = new java.util.HashSet<>(c.tiles());
        return playerTarget.anyInArea(c.anchor(), c.area().extent() + 1, set::contains);
    }

    /** Efekt na polach obszaru - klient rysuje dokladnie trafione pola. */
    void effect(AreaCast c, String fx, long now) {
        combat.addEvent(CombatEvent.shaped(now, c.anchor(), c.area().encode(c.dir()), c.origin(), fx));
    }

    /** Krytyk dla obrazen w gracza (w potwory dolicza go CombatSystem.damageMonster). */
    private int critAmount(int raw, boolean crit) {
        return pl.fawizja.pixel.combat.CriticalHit.apply(pc, raw, crit);
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

    List<Monster> targetsAround(Position center, int radius) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : monsters.near(center, radius)) {
            if (!m.isDead() && !m.isSummon() && m.position().z() == center.z() && world.hasLineOfSight(center, m.position())) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------ czary jak w Tibii (0.240): TibiaSpells

    private final TibiaSpells tibia = new TibiaSpells(this);

    /** Efekty czarow i run z Tibii: fale, stany, pola, sludzy, lewitacja ({@link TibiaSpells}). */
    public TibiaSpells tibia() { return tibia; }

    PlayerCharacter pc() { return pc; }
    WorldMap world() { return world; }
    ItemRegistry items() { return items; }
    CombatSystem combat() { return combat; }
    MonsterManager monsters() { return monsters; }
    EventLog log() { return log; }
    Random rng() { return rng; }
    String param() { return castParam; }
    void addExtraMana(int m) { extraMana += m; }
    int rollValue(SpellType s) { return roll(s); }
    Position playerTargetPos() { return playerTargetPosition(); }
    PlayerTargeting playerTargeting() { return playerTarget; }
    List<Ally> allies() { return visibleAllies(); }
    void strikeArea(AreaCast c, SpellType s, List<Monster> hit, long now) { areaStrike(c, s, hit, now); }

    private static boolean monsterOnly(SpellKind k) {
        return k == SpellKind.CONDITION_TARGET || k == SpellKind.RUNE_CONDITION || k == SpellKind.RUNE_PARALYSE
                || k == SpellKind.RUNE_CONVINCE;
    }

    /** Kierunek patrzenia postaci (fale, sciany z run, lewitacja); ustawia sesja. */
    private java.util.function.Supplier<pl.fawizja.pixel.world.Direction> facing = () -> pl.fawizja.pixel.world.Direction.SOUTH;

    public void setFacing(java.util.function.Supplier<pl.fawizja.pixel.world.Direction> f) { if (f != null) facing = f; }

    pl.fawizja.pixel.world.Direction facing() { return facing.get(); }

    /** Lewitacja i magiczna lina (exani hur, exani tera) - wykonuje sesja, ktora zna mape przejsc i domy. */
    public interface Mover {
        /** Exani hur: true = wykonano; false = brak miejsca (komunikat wypisuje sesja). */
        boolean levitate(boolean up, long now);
        /** Exani tera: true = wyciagnieto. */
        boolean magicRope(long now);
    }

    private Mover mover;

    public void setMover(Mover m) { mover = m; }

    Mover mover() { return mover; }

    /** Pole celu runy na pole: wybrany cel albo pole przed postacia (bez osobnego celownika). */
    Position aim(SpellType s) {
        if (aimTile != null) return aimTile;
        Monster t = target();
        if (t != null && !t.isDead() && t.position().z() == pc.position().z()) return t.position();
        Position pp = playerTargetPosition();
        if (pp != null) return pp;
        pl.fawizja.pixel.world.Direction d = facing();
        return pc.position().translate(d.dx, d.dy);
    }


    /**
     * Krok efektow czarow w czasie (co klatke gry): stany na potworach (utori, ogien duszy, pola), sludzy (utevo res),
     * pola pod potworami wlasciciela.
     */
    public void updateEffects(long now) { tibia.update(now); }

    private void trainMagic(int mana, long now) {
        if (mana <= 0) return;
        // jak w Tibii: kazdy punkt wydanej many (takze na tworzenie run) to punkt do poziomu magii;
        // profesja decyduje tylko o krzywej (Skills.vocationConstant), mnoznik serwera dolicza Skills.train,
        // a Balance.HUNT_TRY_FACTOR wyrownuje tempo magii do szybkich poziomow
        int gained = pc.skills().train(Skill.MAGIC, (long) mana * pl.fawizja.pixel.balance.Balance.HUNT_TRY_FACTOR);
        if (gained > 0) log.add(now, EventLog.Kind.SYSTEM, Messages.get("spell_system.poziom_magii") + pc.skills().level(Skill.MAGIC));
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
        // jak w Canary runy i mikstury dziela 1 s uzycia przedmiotu (nextPotionAction), niezaleznie od grup czarow
        if (!cooldowns.groupReady(SpellGroup.POTION, now)) return CastResult.COOLDOWN;
        CastResult r = check(effect, now, false);
        if (r != CastResult.OK) return report(r, now);
        r = apply(effect, now);
        if (r != CastResult.OK) {
            extraMana = 0;
            return report(r, now);
        }
        if (extraMana > 0) {
            // runa przekonania: koszt many stworzenia jak w Tibii
            pc.spendMana(Math.min(extraMana, pc.mana()));
            trainMagic(extraMana, now);
            extraMana = 0;
        }
        pc.backpack().takeFromSlot(slot, 1);
        cooldowns.trigger(effect, now);
        cooldowns.triggerGroup(SpellGroup.POTION, now, SpellGroup.POTION.groupCooldownMs);
        if (effect.kind().isOffensive() && pc.breakInvisibility()) log.add(now, EventLog.Kind.INFO, Messages.get("combat_system.atakujesz_niewidzialnosc_znika"));
        return CastResult.OK;
    }

    private CastResult report(CastResult r, long now) {
        if (r != CastResult.OK && r != CastResult.COOLDOWN) log.add(now, EventLog.Kind.WARNING, r.message());
        return r;
    }
}
