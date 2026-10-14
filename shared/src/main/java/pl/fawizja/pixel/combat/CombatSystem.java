package pl.fawizja.pixel.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import pl.fawizja.pixel.balance.Balance;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.game.EventLog;
import pl.fawizja.pixel.inventory.InventoryResult;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.monster.LootEntry;
import pl.fawizja.pixel.monster.Monster;
import pl.fawizja.pixel.monster.MonsterManager;
import pl.fawizja.pixel.monster.MonsterState;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.world.GroundItems;
import pl.fawizja.pixel.world.Position;
import pl.fawizja.pixel.world.WorldMap;

/**
 * Walka PvM: automatyczny atak wybranego celu, ataki potworow, smierc, doswiadczenie
 * i lupy. Kazda akcja zmienia stan gry (HP, exp, umiejetnosci, przedmioty).
 */
public final class CombatSystem implements MonsterManager.AttackHandler {

    private final PlayerCharacter pc;
    private final WorldMap world;
    private final ItemRegistry items;
    private final MonsterManager monsters;
    private final GroundItems ground;
    private final EventLog log;
    private final Random rng;
    private final int minLevel;
    private final ArrayDeque<CombatEvent> events = new ArrayDeque<>();
    private Monster target;
    private long nextPlayerAttackMs;
    private boolean autoLoot = true;
    private int kills;

    public CombatSystem(PlayerCharacter pc, WorldMap world, ItemRegistry items, MonsterManager monsters,
                        GroundItems ground, EventLog log, Random rng, int minLevel) {
        this.pc = pc;
        this.world = world;
        this.items = items;
        this.monsters = monsters;
        this.ground = ground;
        this.log = log;
        this.rng = rng;
        this.minLevel = minLevel;
    }

    public Monster target() { return target; }
    public int kills() { return kills; }
    public boolean autoLoot() { return autoLoot; }
    public void setAutoLoot(boolean v) { autoLoot = v; }

    public void setTarget(Monster m) {
        target = m != null && !m.isDead() && m.position().z() == pc.position().z() ? m : null;
    }

    /** Wybiera nastepny najblizszy widoczny cel (przycisk CEL). */
    public Monster cycleTarget() {
        List<Monster> near = monsters.near(pc.position(), 8);
        near.removeIf(m -> m.isDead() || !world.hasLineOfSight(pc.position(), m.position()));
        near.sort((a, b) -> {
            int c = Integer.compare(a.position().distanceTo(pc.position()), b.position().distanceTo(pc.position()));
            return c != 0 ? c : Integer.compare(a.id(), b.id());
        });
        if (near.isEmpty()) {
            target = null;
            return null;
        }
        int idx = target == null ? -1 : near.indexOf(target);
        target = near.get((idx + 1) % near.size());
        return target;
    }

    /** Krok walki gracza: atak celu, gdy jest w zasiegu, widoczny i minal odstep ataku. */
    public void update(long now) {
        if (pc.isDead()) return;
        if (target != null && (target.isDead() || target.position().z() != pc.position().z()
                || target.position().distanceTo(pc.position()) > 10)) {
            target = null;
        }
        if (target == null || now < nextPlayerAttackMs) return;
        if (world.isProtectionZone(pc.position())) return;
        AttackProfile ap = CombatFormulas.attackProfile(pc, items);
        if (!inReach(target, ap.range())) {
            // cel uciekl poza zasieg, a inny potwor nas atakuje - przelacz (wygoda na ekranie dotykowym)
            Monster attacker = nearestAttackerInReach(ap.range());
            if (attacker == null) return;
            target = attacker;
        }
        nextPlayerAttackMs = now + ap.intervalMs();
        int raw = CombatFormulas.roll(ap.maxDamage(), rng);
        if (pc.isDefensive()) raw = (int) Math.round(raw * CombatFormulas.DEFENSIVE_OUTGOING);
        int dmg = CombatFormulas.mitigateOnMonster(target.type(), raw, ap.type(), rng);
        int dealt = target.damage(dmg);
        monsters.provoke(target);
        if (!isTrivial(target.type())) pc.skills().addTries(ap.skill(), 1);
        push(new CombatEvent(now, target.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        if (target.isDead()) onMonsterDeath(target, now);
    }

    /**
     * Potwor zbyt slaby, by czegokolwiek uczyc (ten sam prog co pelne doswiadczenie):
     * poziom 100 nie wytrenuje umiejetnosci na wilkach.
     */
    public boolean isTrivial(MonsterType t) {
        return pc.level() > 4 * t.level() + 20;
    }

    /** Obrazenia z czaru lub runy: odpornosci, zdarzenie, prowokacja, smierc. Zwraca zadane HP. */
    public int damageMonster(Monster m, int raw, DamageType type, long now) {
        if (m.isDead()) return 0;
        int dmg = CombatFormulas.mitigateOnMonster(m.type(), raw, type, rng);
        int dealt = m.damage(dmg);
        push(new CombatEvent(now, m.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_MONSTER : CombatEvent.Kind.BLOCKED));
        monsters.provoke(m);
        if (target == null) target = m;
        if (m.isDead()) onMonsterDeath(m, now);
        return dealt;
    }

    /** Zdarzenie do wyswietlenia (leczenie z czaru, mikstury). */
    public void addEvent(CombatEvent e) { push(e); }

    private boolean inReach(Monster m, int range) {
        return m.position().z() == pc.position().z() && pc.position().distanceTo(m.position()) <= range
                && world.hasLineOfSight(pc.position(), m.position());
    }

    private Monster nearestAttackerInReach(int range) {
        Monster best = null;
        for (Monster m : monsters.near(pc.position(), range)) {
            if (m.isDead() || m == target || m.state() != MonsterState.CHASE || !inReach(m, range)) continue;
            if (best == null || m.position().distanceTo(pc.position()) < best.position().distanceTo(pc.position())) best = m;
        }
        return best;
    }

    private void onMonsterDeath(Monster m, long now) {
        MonsterType t = m.type();
        kills++;
        long exp = Balance.experienceReward(t.experience(), t.level(), pc.level());
        int levels = pc.addExperience(exp);
        log.add(now, EventLog.Kind.INFO, "Pokonano: " + t.name() + " (+" + exp + " exp)");
        if (levels > 0) {
            log.add(now, EventLog.Kind.SYSTEM, "Awans na poziom " + pc.level() + "!");
            push(new CombatEvent(now, pc.position(), pc.level(), CombatEvent.Kind.LEVEL_UP));
        }
        Position corpse = m.position();
        List<ItemStack> drop = rollLoot(t);
        List<String> looted = new ArrayList<>();
        for (ItemStack s : drop) {
            boolean taken = false;
            if (autoLoot && !pc.autoLootFilter().isIgnored(s.itemId()) && pc.position().distanceTo(corpse) <= 8) {
                taken = pc.backpack().add(s) == InventoryResult.OK;
                if (taken) looted.add(describe(s));
            }
            if (!taken) ground.place(corpse, s);
        }
        if (!looted.isEmpty()) log.add(now, EventLog.Kind.LOOT, "Lup: " + String.join(", ", looted));
        if (drop.size() > looted.size()) {
            log.add(now, EventLog.Kind.LOOT, "Czesc lupu zostala na ziemi.");
        }
        monsters.kill(m, now);
        if (target == m) target = null;
    }

    /** Losuje lup z tabeli potwora. */
    public List<ItemStack> rollLoot(MonsterType t) {
        List<ItemStack> out = new ArrayList<>();
        for (LootEntry e : t.loot()) {
            if (rng.nextInt(1000) < e.chancePermille()) {
                int count = CombatFormulas.rollBetween(e.min(), e.max(), rng);
                int max = items.maxStack(e.itemId());
                while (count > 0) {
                    int c = Math.min(count, max);
                    out.add(new ItemStack(e.itemId(), c));
                    count -= c;
                }
            }
        }
        return out;
    }

    @Override
    public void monsterAttacks(Monster m, long now) {
        if (pc.isDead() || world.isProtectionZone(pc.position())) return;
        MonsterType t = m.type();
        int raw = CombatFormulas.rollBetween(t.attackMin(), t.attackMax(), rng);
        int dmg = CombatFormulas.mitigateOnPlayer(pc, raw, t.damageType(), pc.isDefensive(), rng);
        int dealt = pc.damage(dmg);
        if (t.damageType() == DamageType.PHYSICAL && !isTrivial(t)) pc.skills().addTries(Skill.SHIELDING, 1);
        push(new CombatEvent(now, pc.position(), dealt, dealt > 0 ? CombatEvent.Kind.HIT_PLAYER : CombatEvent.Kind.BLOCKED));
        if (target == null) target = m; // automatyczna odpowiedz na atak
        if (pc.isDead()) onPlayerDeath(m, now);
    }

    @Override
    public void monsterHeals(Monster healer, Monster healed, int amount, long now) {
        if (amount > 0) push(new CombatEvent(now, healed.position(), amount, CombatEvent.Kind.HEAL));
    }

    private void onPlayerDeath(Monster killer, long now) {
        long before = pc.experience();
        pc.applyDeathPenalty(minLevel);
        Position temple = world.templeOf(pc.homeTown());
        pc.revive(temple);
        target = null;
        push(new CombatEvent(now, temple, 0, CombatEvent.Kind.DEATH));
        log.add(now, EventLog.Kind.WARNING, "Zginales (" + killer.type().name() + "). Strata: "
                + (before - pc.experience()) + " exp. Odrodzenie w swiatyni.");
    }

    private void push(CombatEvent e) {
        events.addLast(e);
        while (events.size() > 40) events.removeFirst();
    }

    public List<CombatEvent> recentEvents(long now, long maxAgeMs) {
        List<CombatEvent> out = new ArrayList<>();
        for (CombatEvent e : events) if (now - e.timeMs() <= maxAgeMs) out.add(e);
        return out;
    }

    private String describe(ItemStack s) {
        String n = items.get(s.itemId()).name();
        return s.count() > 1 ? s.count() + "x " + n : n;
    }
}
