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
    }

    public void setPlayerTargeting(PlayerTargeting t) { playerTarget = t; }

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

    /** Wartosc bazowa efektu bez losowania (do opisow i testow). */
    public static double baseValue(SpellType s, PlayerCharacter pc) {
        double v = s.base() + pc.level() * s.levelFactor() + pc.skills().level(Skill.MAGIC) * s.magicFactor()
                + pc.skills().level(weaponSkill(pc.vocation())) * s.skillFactor();
        if (s.kind().isHeal()) v *= pc.vocation().healingFactor();
        else if (s.kind().isRune()) v *= pc.vocation().magicFactor();
        return Math.max(0, v);
    }

    public static Skill weaponSkill(Vocation v) {
        switch (v) {
            case PALADIN: return Skill.DISTANCE;
            case MONK: return Skill.FIST;
            default: return Skill.SWORD;
        }
    }

    private int roll(SpellType s) {
        double v = baseValue(s, pc) * (MIN_ROLL + rng.nextDouble() * (MAX_ROLL - MIN_ROLL));
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    // ------------------------------------------------------------------ czary

    public CastResult cast(String key, long now) {
        SpellType s = spells.find(key);
        if (s == null || s.kind().isRune()) return CastResult.NOT_USABLE;
        CastResult r = check(s, now, true);
        if (r != CastResult.OK) return report(r, now);
        r = apply(s, now);
        if (r != CastResult.OK) return report(r, now);
        pc.spendMana(s.mana());
        trainMagic(s.mana(), now);
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
                for (Monster m : targetsAround(pc.position(), s.radius())) combat.damageMonster(m, roll(s), s.damageType(), now);
                if (playerInArea(pc.position(), s.radius())) playerTarget.strike(roll(s), s.damageType(), now);
                return CastResult.OK;
            case RUNE_AREA: {
                Position pvp = playerTargetPosition();
                Position center = pvp != null ? pvp : combat.target().position();
                combat.addEvent(CombatEvent.projectile(now, pc.position(), center, s.damageType().name()));
                combat.addEvent(CombatEvent.area(now, center, s.radius(), s.damageType().name()));
                for (Monster m : targetsAround(center, s.radius())) combat.damageMonster(m, roll(s), s.damageType(), now);
                if (playerInArea(center, s.radius())) playerTarget.strike(roll(s), s.damageType(), now);
                return CastResult.OK;
            }
            case LIGHT:
                pc.addLightBuff(s.radius(), LIGHT_SPELL_MS);
                log.add(now, EventLog.Kind.INFO, "Swiatlo otacza postac.");
                return CastResult.OK;
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

    /** Czy cel-gracz stoi w obszarze czaru (obszar trafia tylko w wybrany cel, nie w przypadkowych graczy). */
    private boolean playerInArea(Position center, int radius) {
        Position p = playerTarget == null ? null : playerTarget.position();
        return p != null && p.z() == center.z() && p.distanceTo(center) <= radius && world.hasLineOfSight(center, p);
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
        long tries = Math.round(mana * Math.min(1.0, pc.vocation().magicFactor()));
        int gained = pc.skills().addTries(Skill.MAGIC, Math.max(1, tries));
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
