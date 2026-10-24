package pl.fawizja.pixel.combat;

import java.util.Random;
import pl.fawizja.pixel.character.PlayerCharacter;
import pl.fawizja.pixel.character.Skill;
import pl.fawizja.pixel.item.EquipSlot;
import pl.fawizja.pixel.item.ItemRegistry;
import pl.fawizja.pixel.item.ItemStack;
import pl.fawizja.pixel.item.ItemType;
import pl.fawizja.pixel.monster.MonsterType;
import pl.fawizja.pixel.vocation.Vocation;

/**
 * Centralne formuly walki. Losowosc pochodzi z przekazanego Random (testy uzywaja
 * stalego ziarna). Wszystkie wyniki sa nieujemne i ograniczone do int.
 */
public final class CombatFormulas {

    /** Atak golymi rekami. */
    public static final int FIST_ATTACK = 7;
    /** Minimalny udzial maksymalnych obrazen w losowaniu (40%..100%). */
    public static final double MIN_ROLL = 0.4;
    /** Postawa obronna: mniej zadawanych i otrzymywanych obrazen. */
    public static final double DEFENSIVE_OUTGOING = 0.8;
    public static final double DEFENSIVE_INCOMING = 0.75;
    /** Postawa obronna z tarcza: mocniejsze zmniejszenie obrazen niz bez tarczy. */
    public static final double DEFENSIVE_INCOMING_SHIELD = 0.6;

    private CombatFormulas() {}

    /** Amunicja w gniezdzie dodaje do ataku broni dystansowej 1/4 swojego ataku (zuzywana po sztuce na strzal). */
    public static final int AMMO_ATTACK_DIVISOR = 4;

    /** Amunicja zalozona do strzalow fizyczna bronia dystansowa; null, gdy brak albo bron jej nie uzywa. */
    public static ItemType ammunition(PlayerCharacter pc, ItemRegistry items) {
        ItemStack w = pc.equipment().get(EquipSlot.WEAPON);
        ItemType weapon = w == null ? null : items.get(w.itemId());
        if (weapon == null || weapon.range() <= 1 || weapon.damageType() != DamageType.PHYSICAL || usesNoAmmo(weapon)) return null;
        ItemStack a = pc.equipment().get(EquipSlot.AMMO);
        if (a == null || a.count() <= 0) return null;
        ItemType t = items.get(a.itemId());
        return t != null && t.category() == pl.fawizja.pixel.item.ItemCategory.AMMUNITION ? t : null;
    }

    /** Luki i kusze strzelaja bez amunicji z gniazda (wlocznie do rzucania do nich nie pasuja). */
    public static boolean usesNoAmmo(ItemType weapon) {
        return weapon.key().endsWith("bow");
    }

    /** Profil ataku postaci na podstawie broni, umiejetnosci i profesji. */
    public static AttackProfile attackProfile(PlayerCharacter pc, ItemRegistry items) {
        Vocation v = pc.vocation();
        ItemStack w = pc.equipment().get(EquipSlot.WEAPON);
        ItemType weapon = w == null ? null : items.get(w.itemId());
        int attack = weapon == null ? FIST_ATTACK : pl.fawizja.pixel.item.Upgrade.attack(weapon, w.upgrade());
        int range = weapon == null ? 1 : Math.max(1, weapon.range());
        DamageType type = weapon == null ? DamageType.PHYSICAL : weapon.damageType();
        double bonus = pc.level() / 5.0;
        double max;
        Skill skill;
        if (type != DamageType.PHYSICAL && range > 1) {
            skill = Skill.MAGIC;
            max = attack * (1.0 + pc.skills().level(Skill.MAGIC) / 25.0) * v.magicFactor() + bonus;
        } else if (range > 1) {
            skill = Skill.DISTANCE;
            ItemType ammo = ammunition(pc, items);
            if (ammo != null) attack += ammo.attack() / AMMO_ATTACK_DIVISOR;
            max = attack * (pc.skills().level(Skill.DISTANCE) + 10) / 20.0 * v.distanceFactor() + bonus;
        } else {
            skill = v == Vocation.MONK ? Skill.FIST : Skill.SWORD;
            max = attack * (pc.skills().level(skill) + 10) / 20.0 * v.meleeFactor() + bonus;
        }
        return new AttackProfile((int) Math.min(Integer.MAX_VALUE, Math.round(max)), range, type, skill, v.attackIntervalMs());
    }

    public static int roll(int max, Random rng) {
        if (max <= 0) return 0;
        int min = (int) Math.floor(max * MIN_ROLL);
        return min + rng.nextInt(max - min + 1);
    }

    public static int rollBetween(int min, int max, Random rng) {
        if (max <= min) return Math.max(0, min);
        return min + rng.nextInt(max - min + 1);
    }

    /** Obrazenia po pancerzu i odpornosciach potwora. */
    public static int mitigateOnMonster(MonsterType m, int damage, DamageType type, Random rng) {
        if (damage <= 0) return 0;
        long d = damage;
        if (type == DamageType.PHYSICAL && m.armor() > 0) {
            d -= rollBetween(m.armor() / 2, m.armor(), rng);
        }
        d = d * (100 - m.resistance(type)) / 100;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, d));
    }

    /** Obrazenia po pancerzu, tarczy i postawie postaci. */
    public static int mitigateOnPlayer(PlayerCharacter pc, int damage, DamageType type, boolean defensive, Random rng) {
        if (damage <= 0) return 0;
        double d = damage;
        if (type == DamageType.PHYSICAL) {
            int armor = pc.equipment().totalArmor();
            d -= rollBetween(0, armor / 2, rng);
            double defense = pc.equipment().totalDefense() * pc.vocation().defenseFactor()
                    * (pc.skills().level(Skill.SHIELDING) + 10) / 20.0;
            d -= rollBetween(0, (int) (defense / 3), rng);
        }
        if (defensive) {
            int shield = pc.equipment().shieldDefense();
            if (shield > 0) {
                // postawa obronna z tarcza: dodatkowe blokowanie ciosow tarcza i mniejsze obrazenia
                if (type == DamageType.PHYSICAL) {
                    double block = shield * pc.vocation().defenseFactor() * (pc.skills().level(Skill.SHIELDING) + 10) / 20.0;
                    d -= rollBetween((int) (block / 6), (int) (block / 2), rng);
                }
                d = Math.max(0, d) * DEFENSIVE_INCOMING_SHIELD;
            } else {
                d *= DEFENSIVE_INCOMING;
            }
        }
        return (int) Math.max(0, Math.round(d));
    }
}
